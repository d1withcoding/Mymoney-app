#!/usr/bin/env python3
"""Objective 1: what do Ghana mobile-money SMS actually look like?

The study design this implements (and the paper should describe): instead of eyeballing
a handful of messages, the corpus is reduced to *skeletons* - digits collapsed, names and
money masked - and the recurring skeletons are the format families. Everything reported is
measured from the corpus, not asserted from documentation:

  * sender-ID inventory per operator (what your handset actually received alerts from)
  * format families (skeleton, count, share) and how many families a parser must cover
  * field-label vocabulary ('Current Balance:', 'Txn Ref:', 'Fee charged:' ...) with counts
  * money rendering (symbol variants, thousand separators, decimals present/absent)
  * date and time shapes, and the day/month-ambiguous share
  * transaction-ID geometry (length, charset, whether it is a prefix)
  * counterparty rendering (name only / name + MSISDN / masked / absent)
  * MSISDN prefix x operator cross-tab (does number-portability break prefix-based routing?)
  * message length distribution and the 160-char segmentation problem

Usage:
  python3 tools/taxonomy.py --corpus corpus/gold/corpus.jsonl      # your real captures
  python3 tools/taxonomy.py                                       # the seed corpus
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ghana_parse import GhanaSmsEngine, normalize as N  # noqa: E402
from ghana_parse.corpus import GOLD_FIELDS, Record, read_corpus  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]

MONEY_RE = re.compile(r"(?:GHS|GH\u00a2|GH\u20b5|\u20b5)\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?", re.IGNORECASE)
PHONE_RE = re.compile(r"(?<![0-9])(?:\+?233[\s-]?)?0[2-5][0-9][\s-]?[0-9]{3}[\s-]?[0-9]{4}(?![0-9])")
UPPER_RUN_RE = re.compile(r"\b[A-Z][A-Z'&\-]{1,20}(?:\s+[A-Z][A-Z'&\-]{1,20}){0,4}\b")
MIXED_RUN_RE = re.compile(r"\b[A-Z][a-z'&\-]{2,20}(?:\s+[A-Z][a-z'&\-]{1,20}){0,4}\b")
LONG_ID_RE = re.compile(r"\b[A-Z0-9]{9,20}\b")
SHORTCODE_RE = re.compile(r"\*\d{2,4}(?:\*\d{1,3}#|\d*\d#)?#")
NUMERIC_RE = re.compile(r"\d+")

DATE_SHAPES = [
    ("ISO yyyy-mm-dd", re.compile(r"\b\d{4}-\d{2}-\d{2}\b")),
    ("dd/mm/yyyy", re.compile(r"\b\d{2}/\d{2}/\d{4}\b")),
    ("dd-mm-yy", re.compile(r"\b\d{2}-\d{2}-\d{2}\b")),
    ("dd.mm.yyyy", re.compile(r"\b\d{2}\.\d{2}\.\d{4}\b")),
    ("d M yyyy (month name)", re.compile(r"\b\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{4}\b", re.I)),
]
TIME_SHAPES = [
    ("24h hh:mm:ss", re.compile(r"\b\d{2}:\d{2}:\d{2}\b")),
    ("24h hh:mm", re.compile(r"\b\d{2}:\d{2}(?!:)\b")),
    ("12h am/pm", re.compile(r"\b\d{1,2}:\d{2}\s*[APap]\.?[Mm]\.?\b")),
]
LABEL_RE = re.compile(r"\b([A-Z][A-Za-z][A-Za-z .'\-]{1,26}?)\s*:")
VERBS = ["sent", "received", "paid", "withdraw", "deposit", "credited", "debited", "transferred",
         "reversed", "purchased", "bought", "recharged", "topped up", "deducted", "applied"]


def skeleton(text: str, frequent: Optional[frozenset] = None) -> str:
    """Collapse the variable parts, keep the structure that identifies a format family.

    With ``frequent`` (corpus tokens above a share threshold) this becomes the *coarse*
    skeleton: rare content words also collapse, so a family is a layout rather than a
    message. Both granularities are reported: fine shows how many distinct strings there
    really are, coarse shows how few layouts a parser must model.
    """
    out = N.tidy_text(text)
    out = MONEY_RE.sub("<MONEY>", out)
    out = PHONE_RE.sub("<MSISDN>", out)
    out = SHORTCODE_RE.sub("<USSD>", out)
    out = UPPER_RUN_RE.sub("<NAME>", out)
    out = MIXED_RUN_RE.sub("<NAME>", out)
    out = LONG_ID_RE.sub("<ID>", out)
    out = re.sub(r"\b\d+\b", "<N>", out)
    out = re.sub(r"<NAME>(\s*<NAME>)+", "<NAME>", out)
    out = re.sub(r"\s+", " ", out).strip()
    if frequent is not None:
        kept = []
        for token in out.split(" "):
            bare = token.strip(".,:;()[]'\"").lower()
            if token.startswith("<") or (len(bare) > 2 and bare in frequent):
                kept.append(token)
            else:
                kept.append("*")
        out = " ".join(kept)
        out = re.sub(r"(\*(?: \*)+)", " *", out)
        out = out.replace(" * *", " *")
    return out


def pct(part: int, whole: int) -> str:
    return f"{100.0 * part / whole:.1f}%" if whole else "-"


def analyse(records: List[Record], engine: GhanaSmsEngine) -> Dict[str, object]:
    transactions = [r for r in records if r.is_transaction]
    total = len(transactions) or 1
    by_operator: Dict[str, List[Record]] = defaultdict(list)
    for record in transactions:
        by_operator[record.operator].append(record)

    # corpus-frequent tokens for the coarse skeleton
    token_counts: Counter = Counter()
    for record in transactions:
        for token in re.findall(r"[a-z][a-z'\-]{2,}", N.tidy_text(record.body).lower()):
            token_counts[token] += 1
    floor = max(2, int(0.05 * (len(transactions) or 1)))
    frequent = frozenset(t for t, c in token_counts.items() if c >= floor)

    out: Dict[str, object] = {
        "records": len(records),
        "transactions": len(transactions),
        "non_transactions": len(records) - len(transactions),
        "operators": {},
    }

    # --- sender inventory -----------------------------------------------------
    senders: Counter = Counter()
    for record in records:
        if record.sender:
            senders[(record.operator, record.sender)] += 1
    out["senders"] = [{"operator": op, "sender": s, "count": c} for (op, s), c in senders.most_common()]

    # --- per operator ---------------------------------------------------------
    for operator, group in sorted(by_operator.items()):
        if operator == "unknown" or not group:
            continue
        n = len(group)
        skels = Counter(skeleton(r.body, frequent) for r in group)
        fine_skels = Counter(skeleton(r.body) for r in group)
        label_counts: Counter = Counter()
        verb_counts: Counter = Counter()
        money_styles: Counter = Counter()
        date_shapes: Counter = Counter()
        time_shapes: Counter = Counter()
        id_lengths: Counter = Counter()
        id_leading: Counter = Counter()
        counterparty_styles: Counter = Counter()
        lengths = []
        segmented = 0
        ambiguous_dates = 0
        for record in group:
            body = record.body
            lengths.append(len(body))
            if len(body) > 160:
                segmented += 1
            for match in LABEL_RE.finditer(body):
                label = re.sub(r"\s+", " ", match.group(1)).strip()
                if 3 <= len(label) <= 28:
                    label_counts[label] += 1
            lowered = body.lower()
            for verb in VERBS:
                if verb in lowered:
                    verb_counts[verb] += 1
            for money in MONEY_RE.findall(body):
                symbol = re.match(r"[A-Za-z\u20b5\u00a2]+", money.strip())
                token = symbol.group(0).upper() if symbol else "?"
                has_sep = "," in money
                has_dec = bool(re.search(r"\.[0-9]{1,2}$", money.strip()))
                space = " " if re.match(r"^[A-Z\u20b5\u00a2¢]+\s", money) else ""
                money_styles[f"{token}{space}{',' if has_sep else 'nosep'}{'.2dp' if has_dec else 'nodec'}"] += 1
            for name, rx in DATE_SHAPES:
                if rx.search(body):
                    date_shapes[name] += 1
            for name, rx in TIME_SHAPES:
                if rx.search(body):
                    time_shapes[name] += 1
            tid = record.gold.get("trx_id")
            if tid:
                id_lengths[len(str(tid))] += 1
                id_leading["leading digits" if str(tid)[:1].isdigit() else "starts alpha"] += 1
                if re.match(r"^\s*[0-9]{12,20}\s", body):
                    id_counts_key = "ID as message prefix"
                    id_leading[id_counts_key] += 1
            if record.gold.get("counterparty_name") and record.gold.get("counterparty_phone"):
                counterparty_styles["name + msisdn"] += 1
            elif record.gold.get("counterparty_name"):
                counterparty_styles["name only"] += 1
            elif record.gold.get("counterparty_phone"):
                counterparty_styles["msisdn only"] += 1
            elif record.gold.get("paybill_name"):
                counterparty_styles["paybill/merchant"] += 1
            else:
                counterparty_styles["no counterparty"] += 1
            result = engine.parse(body, record.sender)
            if result.ambiguous_date:
                ambiguous_dates += 1

        # field presence, measured the only honest way: from the annotations
        presence = Counter()
        for record in group:
            for field in GOLD_FIELDS:
                if record.gold.get(field):
                    presence[field] += 1

        out["operators"][operator] = {
            "n": n,
            "families": len(skels),
            "families_fine": len(fine_skels),
            "top_skeletons": [{"skeleton": s, "count": c, "share": round(c / n, 4)} for s, c in skels.most_common(12)],
            "top_12_coverage": round(sum(c for _s, c in skels.most_common(12)) / n, 4),
            "field_labels": [{"label": l, "count": c} for l, c in label_counts.most_common(30)],
            "verbs": dict(verb_counts.most_common()),
            "money_styles": dict(money_styles.most_common()),
            "date_shapes": dict(date_shapes.most_common()),
            "time_shapes": dict(time_shapes.most_common()),
            "trx_id_lengths": dict(sorted(id_lengths.items())),
            "trx_id_shape": dict(id_leading.most_common()),
            "counterparty_styles": dict(counterparty_styles.most_common()),
            "field_presence": {f: {"n": presence[f], "rate": round(presence[f] / n, 4)} for f in GOLD_FIELDS},
            "length_median": statistics.median(lengths) if lengths else None,
            "length_max": max(lengths) if lengths else None,
            "over_160_chars": segmented,
            "over_160_rate": round(segmented / n, 4),
            "ambiguous_date_rate": round(ambiguous_dates / n, 4),
        }

    # --- MSISDN prefix x operator --------------------------------------------
    cross: Dict[str, Counter] = defaultdict(Counter)
    for record in transactions:
        phone = record.gold.get("counterparty_phone")
        if phone:
            digits = re.sub(r"\D", "", str(phone))
            if len(digits) == 10:
                cross[digits[1:3]][record.operator] += 1
    out["msisdn_prefix_by_operator"] = {prefix: dict(counter) for prefix, counter in sorted(cross.items())}

    # --- negative classes ------------------------------------------------------
    neg = [r for r in records if not r.is_transaction]
    neg_kinds: Counter = Counter()
    for record in neg:
        lowered = record.body.lower()
        if "otp" in lowered or "password" in lowered or "pin" in lowered:
            neg_kinds["credential / security"] += 1
        elif "requested" in lowered or "request" in lowered or "approve" in lowered:
            neg_kinds["payment request"] += 1
        elif "won" in lowered or "promo" in lowered or "offer" in lowered or "eligible" in lowered:
            neg_kinds["promotion / marketing"] += 1
        elif "expire" in lowered or "renew" in lowered or "kyc" in lowered:
            neg_kinds["account lifecycle"] += 1
        else:
            neg_kinds["other non-transaction"] += 1
    out["non_transaction_classes"] = dict(neg_kinds.most_common())
    return out


def render_markdown(data: Dict[str, object], label: str) -> str:
    lines = [
        f"# Objective 1 - structure and content of Ghana mobile-money SMS ({label})",
        "",
        "> Generated by `tools/taxonomy.py` against the corpus named in the command line.",
        "> Nothing here is taken from vendor documentation: counts are measured on the",
        "> messages themselves. **Read the provenance caveat** - if the corpus is the seed",
        "> corpus, these are properties of the *templates*, not of MTN/Telecel/AirtelTigo traffic.",
        "",
        f"- records: {data['records']} (transactions {data['transactions']}, non-transactions {data['non_transactions']})",
        "",
        "## Sender IDs observed",
        "",
        "| operator | sender | messages |",
        "|---|---|---|",
    ]
    for row in data["senders"]:  # type: ignore[index]
        lines.append(f"| {row['operator']} | `{row['sender']}` | {row['count']} |")
    lines += ["", "## Format families per operator", ""]
    for operator, stats in data["operators"].items():  # type: ignore[union-attr]
        lines += [
            f"### {operator}  (n = {stats['n']})",
            "",
            f"- format families (coarse skeletons): **{stats['families']}**; the 12 most "
            f"common cover **{stats['top_12_coverage'] * 100:.1f}%** of messages. At fine "
            f"granularity the same messages form {stats['families_fine']} distinct skeletons - "
            "the gap is what a *field*-oriented parser absorbs and a *whole-message-template* "
            "parser does not.",
            f"- length: median {stats['length_median']} chars, max {stats['length_max']}; "
            f"over 160 chars in {stats['over_160_chars']} messages ({stats['over_160_rate'] * 100:.1f}%) "
            "- i.e. concatenated multipart SMS",
            f"- day/month-ambiguous date strings: {stats['ambiguous_date_rate'] * 100:.1f}%",
            "",
            "Field-label vocabulary (what precedes a colon):",
            "",
            "| label | count |",
            "|---|---|",
        ]
        for row in stats["field_labels"][:14]:
            lines.append(f"| `{row['label']}:` | {row['count']} |")
        lines += ["", "Money rendering:", "", "| style | count |", "|---|---|"]
        for style, count in stats["money_styles"].items():
            lines.append(f"| `{style}` | {count} |")
        lines += ["", "Date / time shapes:", "", "| shape | messages |", "|---|---|"]
        for shape, count in {**{k: v for k, v in stats["date_shapes"].items()},
                             **{k: v for k, v in stats["time_shapes"].items()}}.items():
            lines.append(f"| {shape} | {count} |")
        lines += ["", "Counterparty rendering:", "", "| style | messages |", "|---|---|"]
        for style, count in stats["counterparty_styles"].items():
            lines.append(f"| {style} | {count} |")
        lines += ["", "Transaction-ID geometry:", "", "| property | value |", "|---|---|"]
        for length, count in stats["trx_id_lengths"].items():
            lines.append(f"| length {length} | {count} |")
        for shape, count in stats["trx_id_shape"].items():
            lines.append(f"| {shape} | {count} |")
        lines += ["", "Verb markers (what states the transaction type): " +
                  ", ".join(f"`{k}` x{v}" for k, v in stats["verbs"].items()), "",
                  "Field presence in this corpus:", "", "| field | present | rate |", "|---|---|---|"]
        for field, row in stats["field_presence"].items():
            if row["n"]:
                lines.append(f"| {field} | {row['n']} | {pct(row['n'], stats['n'])} |")
        lines += ["", "Top skeletons (format families):", "", "```text"]
        for row in stats["top_skeletons"]:
            lines.append(f"{row['count']:>4}  {row['skeleton'][:170]}")
        lines.append("```")
        lines.append("")
    cross = data.get("msisdn_prefix_by_operator") or {}
    if cross:
        lines += ["", "## Counterparty MSISDN prefix x operator", "",
                  "| prefix | " + " | ".join(sorted(data["operators"].keys())) + " |",  # type: ignore[index]
                  "|---|" + "---|" * len(data["operators"])]  # type: ignore[index]
        for prefix, per_op in cross.items():
            row = [str(per_op.get(op, 0)) for op in sorted(data["operators"].keys())]  # type: ignore[index]
            lines.append(f"| 0{prefix} | " + " | ".join(row) + " |")
        lines += ["", "> If the diagonal is not dominant, prefix-based operator inference is",
                  "> unreliable (number portability) and routing must stay sender/content based.", ""]
    neg = data.get("non_transaction_classes") or {}
    if neg:
        lines += ["", "## Non-transaction messages (what a parser must refuse)", "",
                  "| class | messages |", "|---|---|"]
        for cls, count in neg.items():
            lines.append(f"| {cls} | {count} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus", type=Path, default=ROOT / "corpus" / "gold" / "seed-corpus.jsonl")
    parser.add_argument("--out-dir", type=Path, default=ROOT / "results")
    parser.add_argument("--label", default="")
    args = parser.parse_args()

    records = read_corpus(args.corpus)
    data = analyse(records, GhanaSmsEngine())
    label = args.label or args.corpus.stem
    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "taxonomy.json").write_text(json.dumps({"corpus": args.corpus.name, "data": data}, indent=2), encoding="utf-8")
    (args.out_dir / "taxonomy.md").write_text(render_markdown(data, label), encoding="utf-8")

    print(f"corpus={args.corpus.name}  transactions={data['transactions']}")
    for operator, stats in data["operators"].items():  # type: ignore[union-attr]
        print(f"  {operator:<20} n={stats['n']:<4} families={stats['families']:<4} "
              f"top12={stats['top_12_coverage'] * 100:5.1f}%  >160chars={stats['over_160_rate'] * 100:4.1f}%")
    print(f"wrote {args.out_dir / 'taxonomy.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
