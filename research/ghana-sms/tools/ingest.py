#!/usr/bin/env python3
"""Ingest raw exports into the canonical annotated corpus (redacted, deduplicated).

Supported inputs (auto-detected by extension, overridable with --format):
  *.csv / *.tsv     any column names; see the alias table below, override with
                    --map 'their_column=our_field' (repeatable)
  *.xml             Android 'SMS Backup & Restore' exports (<sms address= body= date= type=>)
  *.jsonl / *.json  one object per record (already-annotated corpus round-trip)
  *.txt             pasted messages, one per line or blank-line separated blocks;
                    each block may start with 'SENDER: ' or '[YYYY-MM-DD HH:MM] '

Examples
  python3 tools/ingest.py --in ~/sms_labeled.csv --name pilot_v1 --auto-gold
  python3 tools/ingest.py --in ~/TEST_SUBJECT2.xml --name subject2 --consent participant_consent
  python3 tools/ingest.py --in notes.txt --name spot_checks --tier publish

The raw input file is never copied into the repository and the redaction tier is
recorded in every record, so an appendix table can state exactly what was removed.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ghana_parse import GhanaSmsEngine, normalize as N  # noqa: E402
from ghana_parse import redact as R  # noqa: E402
from ghana_parse.corpus import GOLD_FIELDS, Record, dedupe_key, write_corpus  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "corpus" / "gold" / "corpus.jsonl"

# Column aliases, in priority order. 'body' is mandatory; everything else is optional.
ALIASES: Dict[str, List[str]] = {
    "body": ["body", "sms_body", "message", "text", "content", "sms", "msg", "message_body", "snippet"],
    "sender": ["sender", "address", "phone_number", "number", "from", "originator", "contact", "src", "name"],
    "received_at": ["date", "datetime", "date_time", "timestamp", "received_at", "received",
                    "time", "when", "sent_date", "sent_at", "sms_date", "date_received",
                    "read", "created", "v1"],
    "operator": ["operator", "network", "provider", "telco", "service"],
    "category": ["category", "label", "spending_category", "class", "cat", "gold_category"],
    "amount": ["amount", "value", "txn_amount", "transaction_amount"],
    "balance": ["balance", "new_balance", "available_balance"],
    "tx_kind": ["tx_kind", "type", "transaction_type", "txn_type", "kind"],
    "direction": ["direction", "dir", "credit_debit", "in_out"],
    "trx_id": ["trx_id", "transaction_id", "txn_id", "tid", "reference_id"],
    "counterparty_name": ["counterparty_name", "counterparty", "merchant", "vendor", "name_of_sender", "party"],
    "counterparty_phone": ["counterparty_phone", "party_number", "other_number"],
    "date": ["tx_date", "transaction_date", "on_date"],
    "time": ["tx_time", "transaction_time"],
    "status": ["status", "state"],
    "notes": ["notes", "comment", "remarks", "note"],
    "consent": ["consent", "consent_status"],
}
# A gold value that the corpus author typed verbatim; 'needs_review' marks engine drafts.
GOLD_KEYS = [k for k in GOLD_FIELDS] + ["category"]


_CATEGORY_LABELS: Optional[frozenset] = None


def known_categories() -> frozenset:
    """The label set from ``spec/ghana_categories.json``. A corpus column whose values are
    not labels (a 'label' column holding 'sent'/'received', say) is *not* silently treated
    as a category - that would poison Objective 3 - it is reported and left unlabelled."""
    global _CATEGORY_LABELS
    if _CATEGORY_LABELS is None:
        path = ROOT / "spec" / "ghana_categories.json"
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return frozenset()
        labels = data.get("labels") or []
        _CATEGORY_LABELS = frozenset(
            str(item.get("id") if isinstance(item, dict) else item).strip().lower()
            for item in labels
        )
    return _CATEGORY_LABELS


def normalize_timestamp(value: str) -> str:
    """Epoch numbers become ISO-8601; text timestamps are tidied but keep whatever offset
    (or none) the export carried. An export that stores '1773515040000' is not asking us to
    keep a 13-digit integer in a date field, and an ISO string is what the corpus schema
    promises, so this is the single place where that conversion happens."""
    import datetime as dt

    text = (value or "").strip()
    if text in ("", "null", "NULL", "0", "-1"):
        return ""
    if text.isdigit() and len(text) in (10, 13, 16, 19):
        seconds = int(text) / float(10 ** (len(text) - 10))
        try:
            stamp = dt.datetime.fromtimestamp(seconds, tz=dt.timezone.utc)
        except (OverflowError, OSError, ValueError):
            return text
        return stamp.isoformat(timespec="seconds")
    return text.replace("  ", " ").replace(" ", "T", 1) if re.match(r"^\d{4}-\d{2}-\d{2} \d", text) else text


def detect_format(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix in (".csv", ".tsv"):
        return "csv"
    if suffix == ".xml":
        return "xml"
    if suffix in (".jsonl", ".ndjson", ".json"):
        return "jsonl"
    return "txt"


def parse_mappings(items: List[str]) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for item in items or []:
        if "=" not in item:
            raise SystemExit(f"--map expects 'their_column=our_field', got: {item}")
        their, ours = item.split("=", 1)
        out[their.strip().lower()] = ours.strip()
    return out


def map_columns(fieldnames: List[str], overrides: Dict[str, str]) -> Tuple[Dict[str, str], List[str]]:
    """their_column -> our_field, preferring explicit --map, then aliases, then fuzzy."""
    lower = {name.lower().strip(): name for name in fieldnames if name}
    mapping: Dict[str, str] = {}
    used: set = set()
    for their, ours in overrides.items():
        if their in lower:
            mapping[lower[their]] = ours
            used.add(lower[their])
    for our_field, candidates in ALIASES.items():
        if our_field in mapping.values():
            continue
        for cand in candidates:
            hit = next((lower[k] for k in lower if k == cand and lower[k] not in used), None)
            if hit:
                mapping[hit] = our_field
                used.add(hit)
                break
    if not any(v == "body" for v in mapping.values()):
        fuzzy = next((lower[k] for k in lower if "body" in k or "msg" in k or "text" in k), None)
        if fuzzy:
            mapping[fuzzy] = "body"
    unmapped = [name for name in fieldnames if name not in mapping]
    return mapping, unmapped


def rows_from_csv(path: Path, dialect_hint: str = "") -> Iterable[Dict[str, str]]:
    with open(path, "r", encoding="utf-8-sig", newline="") as handle:
        sample = handle.read(64 * 1024)
        handle.seek(0)
        delimiter = dialect_hint or ("," if sample.count(",") >= sample.count(";") else ";")
        try:
            dialect = csv.Sniffer().sniff(sample[:8000], delimiters=",;\t|")
            delimiter = dialect.delimiter
        except csv.Error:
            pass
        reader = csv.DictReader(handle, delimiter=delimiter)
        if not reader.fieldnames:
            raise SystemExit(f"{path}: no CSV header found")
        mapping, unmapped = map_columns(list(reader.fieldnames), ROW_OVERRIDES)
        INGEST_STATE["unmapped_columns"] = unmapped
        INGEST_STATE["column_mapping"] = mapping
        for raw in reader:
            record: Dict[str, str] = {}
            for col, value in raw.items():
                if col is None:
                    continue
                field = mapping.get(col)
                if field and value is not None:
                    record[field] = str(value).strip()
                elif value and re.fullmatch(r"__[^_]+__", str(value).strip()):
                    record.setdefault("_extras", "")
                    record["_extras"] += str(value).strip()
            if record.get("body"):
                yield record


INGEST_STATE: Dict[str, Any] = {}


def rows_from_xml(path: Path) -> Iterable[Dict[str, str]]:
    """Android 'SMS Backup & Restore' (and QKSMS-derived) exports.

    <sms protocol="0" address="+23324xxxxxxx" date="1757500000000" type="1"
         subject="null" body="..." to="null" ... />
    type 1 = inbox (received), 2 = sent. We only ever read *received* alerts, but a
    'sent' row is kept when its body looks like an operator alert, because some handsets
    log the operator's copy as sent.
    """
    tree = ET.parse(path)
    root = tree.getroot()
    for element in root.iter():
        tag = element.tag.lower().rsplit("}", 1)[-1]
        if tag not in ("sms", "mms"):
            continue
        attrs = {k.lower(): (v or "").strip() for k, v in element.attrib.items()}
        body = attrs.get("body") or ""
        if not body:
            continue
        if tag == "mms":
            subj = attrs.get("subject") or ""
            body = f"{subj} {body}".strip()
        record = {
            "body": body,
            "sender": attrs.get("address") or attrs.get("phone_number") or attrs.get("sender") or "",
            "received_at": normalize_timestamp(
                attrs.get("date") or attrs.get("sent-date") or attrs.get("received") or ""
            ),
            "msg_type": attrs.get("type") or "",
        }
        yield record


def rows_from_jsonl(path: Path) -> Iterable[Dict[str, str]]:
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            data = json.loads(line)
            if isinstance(data, dict):
                gold = data.get("gold") or {}
                flat = {k: v for k, v in data.items() if k != "gold"}
                flat.update({k: v for k, v in gold.items() if v is not None})
                yield {k: ("" if v is None else str(v)) for k, v in flat.items()}


def rows_from_txt(path: Path) -> Iterable[Dict[str, str]]:
    """Pasted messages: one per line, or blank-line-separated blocks with an optional
    'SENDER: ' / '[2025-09-10 13:51] ' prefix."""
    text = path.read_text(encoding="utf-8", errors="replace")
    blocks = [b.strip() for b in re.split(r"\n\s*\n", text) if b.strip()]
    if len(blocks) <= 1:
        blocks = [line.strip() for line in text.splitlines() if line.strip()]
    for block in blocks:
        if block.lower().startswith(("---", "+++", "diff ", "@@")):
            continue
        sender = ""
        stamp = ""
        m = re.match(r"^\[([^\]]{10,25})\]\s*(.*)$", block, re.DOTALL)
        if m:
            stamp, block = m.group(1), m.group(2).strip()
        marker = re.match(r"^(?:SENDER|FROM|ADDRESS)[ \t]*:[ \t]*([^\n]*)", block, re.IGNORECASE)
        if marker:
            head = marker.group(1).strip()
            lines = block.split("\n", 1)
            tail = lines[1].strip() if len(lines) > 1 else ""
            first, _, rest = head.partition(" ")
            if tail:
                # 'SENDER: MTN-MOMO' on its own line, message below
                sender, block = first.strip(), tail
            elif len(first) <= 24 and len(rest) > 20:
                # 'SENDER: MTN-MOMO Payment made for ...' all on one line
                sender, block = first.strip(), rest.strip()
            else:
                sender, block = head, ""
        m = re.match(r"^([A-Za-z0-9 ._-]{1,24}):\s+(.+)$", block, re.DOTALL)
        if not sender and m and not re.match(r"^(You|Your|Payment|Deposit|Cash|Withdrawal|TXN|GHS|GH)", m.group(1), re.IGNORECASE):
            sender, block = m.group(1), m.group(2).strip()
        block = re.sub(r"\s+", " ", block).strip()
        if block:
            yield {"body": block, "sender": sender, "received_at": stamp}


OPERATOR_BY_NAME = {
    "mtn": "mtn_momo", "momo": "mtn_momo", "mobile money": "mtn_momo",
    "telecel": "telecel_cash", "vodafone": "telecel_cash", "vcash": "telecel_cash",
    "airteltigo": "airtel_tigo_money", "at money": "airtel_tigo_money", "atm": "airtel_tigo_money",
}


def infer_operator(sender: str, body: str, engine: GhanaSmsEngine, explicit: str = "") -> Tuple[str, str]:
    explicit_key = (explicit or "").strip().lower()
    for token, operator_id in OPERATOR_BY_NAME.items():
        if token in explicit_key:
            return operator_id, "annotated"
    result = engine.parse(body, sender)
    if result.operator_id:
        return result.operator_id, f"engine:{result.route}"
    for token, operator_id in OPERATOR_BY_NAME.items():
        if token in sender.lower():
            return operator_id, "sender_substring"
    return "unknown", "unmatched"


def auto_gold(body: str, engine: GhanaSmsEngine) -> Tuple[Dict[str, Any], Optional[str]]:
    """Draft gold labels from the engine. Marked needs_review by the caller: a model may
    never silently author its own exam."""
    result = engine.parse(body, "")
    gold: Dict[str, Any] = {}
    if not result.accepted:
        return {"_draft_status": "engine_rejected"}, None
    for name, value in result.fields.items():
        gold[name] = value
    return gold, None


def build_record(
    raw: Dict[str, str],
    index: int,
    name: str,
    args: argparse.Namespace,
    engine: GhanaSmsEngine,
    seen: set,
) -> Tuple[Optional[Record], str]:
    body = N.tidy_text(raw.get("body", ""))
    if not body:
        return None, "empty_body"
    key = dedupe_key(body)
    if key in seen:
        return None, "duplicate"
    seen.add(key)

    sender = (raw.get("sender") or "").strip()
    # Sender IDs are alphanumeric short codes in Ghana; if the export stored the phone
    # number instead, keep it masked and route on content only.
    operator_id, operator_route = infer_operator(sender, body, engine, raw.get("operator", ""))

    is_transaction = True
    reject_reason = engine.skip_reason(body)
    if raw.get("msg_type") == "2":
        is_transaction = True  # operator copy of an alert; kept, flagged in notes
    if reject_reason:
        is_transaction = False

    redacted_body, mapping = R.redact_body(body, tier=args.tier, names_mode=args.names)
    audit_pairs: List[Tuple[str, str]] = list(mapping)

    gold: Dict[str, Any] = {}
    notes_unmapped = ""
    for field_name in GOLD_KEYS:
        value = (raw.get(field_name) or "").strip()
        if value:
            if field_name in ("amount", "fee", "levy", "balance"):
                value = N.normalize_amount(value) or value
            elif field_name == "date":
                value = N.normalize_date(value) or value
            elif field_name == "time":
                value = N.normalize_time(value) or value
            elif field_name == "counterparty_phone":
                value = N.normalize_phone(value) or value
            elif field_name == "trx_id":
                value = N.normalize_trx_id(value) or value
            elif field_name == "category":
                known = known_categories()
                norm = value.lower().replace(" ", "_").replace("-", "_")
                if known and norm not in known:
                    # Do not invent a label: keep the raw value in notes for the author to
                    # re-map, and leave the record unlabelled for Objective 3.
                    INGEST_STATE.setdefault("unmapped_categories", Counter())
                    INGEST_STATE["unmapped_categories"][value] += 1
                    notes_unmapped = value
                    continue
                value = norm
            elif field_name in ("counterparty_name", "paybill_name"):
                value = R.redact_value(field_name, value, tier=args.tier) or value
            elif field_name in ("direction", "status", "tx_kind", "category"):
                value = value.upper().replace(" ", "_") if field_name != "category" else value.lower()
            gold[field_name] = value

    # 'gold_source' describes the *field* gold, not the category: a corpus annotated only for
    # Objective 3 is legitimately human-verified for categories and still has no field labels.
    human_fields = [k for k in gold if k != "category"]
    gold_source = "human_verified" if human_fields else "absent"
    if args.auto_gold:
        # Draft every field the author did not supply, so a category-only corpus still gets a
        # reviewable starting point. Which fields were drafted is recorded in notes - the
        # gold block stays pure data, and a reviewer can grep for the list.
        draft, _ = auto_gold(redacted_body, engine)
        status = draft.get("_draft_status")
        # '_draft_status' is metadata, never a field: the engine refusing the message is itself a
        # fact the reviewer needs, but a key like this in the gold block would be read as a field.
        added = {k: v for k, v in draft.items() if k not in gold and not k.startswith("_")}
        if added or status:
            gold.update(added)
            gold_source = ("mixed" if human_fields else "engine_draft_needs_review")
            mark = ",".join(sorted(added)) if added else f"none:{status}"
            raw["notes"] = f"{raw.get('notes') or ''} [auto_gold={mark}]".strip()

    notes = (raw.get("notes") or "").strip()
    if notes_unmapped:
        notes = (notes + f" [unmapped_category={notes_unmapped}]").strip()
    if raw.get("msg_type") == "2":
        notes = (notes + " [export row type=sent]").strip()
    notes = (notes + f" [op_route={operator_route}]").strip()

    record = Record(
        id=f"{args.prefix or hashlib.sha1(name.encode()).hexdigest()[:6]}-{index:06d}",
        body=redacted_body,
        operator=operator_id,
        sender=sender if len(sender) <= 24 else R.mask_msisdn(sender, "prefix"),
        received_at=normalize_timestamp(raw.get("received_at") or "") or None,
        source=args.source,
        provenance=name,
        consent=args.consent,
        is_transaction=is_transaction,
        category=gold.get("category"),
        gold_source=gold_source,
        gold={k: v for k, v in gold.items() if k != "_draft_status"},
        notes=notes,
    )
    if reject_reason:
        record.gold["__reject__"] = reject_reason
    return record, "ok" if is_transaction else "non_transaction_kept"


def main(argv: Optional[List[str]] = None) -> int:
    global ROW_OVERRIDES
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--in", dest="infile", required=True, type=Path)
    parser.add_argument("--format", choices=("auto", "csv", "xml", "jsonl", "txt"), default="auto")
    parser.add_argument("--map", dest="maps", action="append", default=[], help="their_column=our_field")
    parser.add_argument("--name", default="", help="provenance label, e.g. pilot_v1 or subject2")
    parser.add_argument("--prefix", default="", help="record-id prefix (default: hash of --name)")
    parser.add_argument("--consent", default="unrecorded", choices=("participant_consent", "author_own", "synthetic", "unrecorded"))
    parser.add_argument("--source", default="device_export", choices=("device_export", "manual_capture", "template", "synthetic"))
    parser.add_argument("--tier", default="analysis", choices=("analysis", "publish"), help="redaction tier (see ghana_parse/redact.py)")
    parser.add_argument("--names", default="personal-only", choices=("personal-only", "all", "none"))
    parser.add_argument("--auto-gold", action="store_true", help="draft missing gold labels with the engine (flagged for review)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--report-dir", type=Path, default=ROOT / "corpus" / "reports")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)

    R.set_names_mode(args.names if args.tier == "analysis" else "all")
    ROW_OVERRIDES = parse_mappings(args.maps)

    if not args.infile.exists():
        print(f"input not found: {args.infile}", file=sys.stderr)
        print("NOTE: attachments must land inside the sandbox. If this was a chat upload, the\n"
              "file bridge may have dropped it - paste the rows into a file instead.", file=sys.stderr)
        return 2

    fmt = detect_format(args.infile) if args.format == "auto" else args.format
    readers = {"csv": rows_from_csv, "xml": rows_from_xml, "jsonl": rows_from_jsonl, "txt": rows_from_txt}
    name = args.name or args.infile.stem

    engine = GhanaSmsEngine()
    records: List[Record] = []
    seen: set = set()
    tally: Counter = Counter()
    raw_stream = readers[fmt](args.infile)
    for index, raw in enumerate(raw_stream, 1):
        if args.limit and index > args.limit:
            break
        record, status = build_record(dict(raw), index, name, args, engine, seen)
        tally[status] += 1
        if record is not None:
            records.append(record)

    operators = Counter(r.operator for r in records)
    transactions = sum(1 for r in records if r.is_transaction)
    needs_review = sum(1 for r in records if r.gold.get("_draft_status") or "engine_draft" in r.notes)
    gold_coverage = Counter()
    for record in records:
        for field_name in GOLD_FIELDS:
            if record.gold.get(field_name):
                gold_coverage[field_name] += 1

    print(f"format={fmt} rows={sum(tally.values())} kept={len(records)} transactions={transactions}")
    print(f"operators={dict(operators)}")
    if tally["duplicate"]:
        print(f"dropped {tally['duplicate']} duplicate(s) by normalized-body hash")
    if tally["non_transaction_kept"]:
        print(f"kept {tally['non_transaction_kept']} non-transaction(s) (is_transaction=false) for false-positive testing")
    if INGEST_STATE.get("unmapped_columns"):
        print(f"ignored columns: {INGEST_STATE['unmapped_columns']}")
    print(f"gold coverage: {dict(gold_coverage)}")
    unmapped_cats = INGEST_STATE.get("unmapped_categories")
    if unmapped_cats:
        print(f"WARNING: category column values not in the taxonomy: {dict(unmapped_cats)}")
        print("         those records are left unlabelled for Objective 3; map the real column "
              "with --map 'theircolumn=category' (labels: "
              f"{', '.join(sorted(known_categories()))[:120]}...)")
    if needs_review:
        print(f"WARNING: {needs_review} record(s) carry engine-drafted gold and MUST be human-checked "
              f"before they are used for scoring (tools/review.py).")

    if args.dry_run:
        for record in records[:5]:
            print("\n--- dry run sample ---")
            print(record.to_json()[:900])
        print("\n(dry run: nothing written)")
        return 0

    written = write_corpus(args.out, records)
    print(f"wrote {written} record(s) -> {args.out.relative_to(ROOT.parent.parent) if args.out.is_absolute() else args.out}")

    args.report_dir.mkdir(parents=True, exist_ok=True)
    report = args.report_dir / f"ingest-{name}.md"
    lines = [
        f"# Ingest report - {name}",
        "",
        f"- input: `{args.infile.name}` (format: {fmt})",
        f"- redaction tier: **{args.tier}**, personal-name mode: {args.names if args.tier == 'analysis' else 'all (publish tier)'}",
        f"- consent: `{args.consent}`, source: `{args.source}`",
        f"- rows seen: {sum(tally.values())}; kept: {written}; duplicates dropped: {tally['duplicate']}",
        f"- transactions: {transactions}; non-transactions retained as negatives: {tally['non_transaction_kept']}",
        "",
        "## Operator routing",
        "",
        "| operator | records |",
        "|---|---|",
    ]
    for operator_id, count in operators.most_common():
        lines.append(f"| {operator_id} | {count} |")
    lines += ["", "## Gold-field coverage", "", "| field | annotated records |", "|---|---|"]
    for field_name in GOLD_FIELDS:
        lines.append(f"| {field_name} | {gold_coverage.get(field_name, 0)} |")
    lines += [
        "",
        "## Column mapping applied",
        "",
        "```",
        json.dumps(INGEST_STATE.get("column_mapping", {}), indent=2, sort_keys=True),
        "```",
        "",
        ("**Engine-drafted gold present - run the review pass before scoring.**" if needs_review else ""),
        "",
        "Generated by `tools/ingest.py`; re-run to refresh.",
    ]
    report.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote report -> {report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
