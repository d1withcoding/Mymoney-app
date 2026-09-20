"""Canonical annotated-corpus record: JSONL, one record per SMS.

Design notes for the paper (see corpus/CORPUS_SCHEMA.md):
* ``gold`` may be *partial*. A field that is absent from ``gold`` is 'not annotated'
  and is excluded from that field's precision/recall; a field present as ``null``
  means 'the message does not contain it', which makes a false positive countable.
* ``provenance`` distinguishes real device captures from template-derived synthetic
  records so no reviewer can accuse the evaluation of mixing them silently.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

GOLD_FIELDS = (
    "amount",
    "fee",
    "levy",
    "balance",
    "trx_id",
    "reference",
    "counterparty_name",
    "counterparty_phone",
    "paybill_name",
    "paybill_account",
    "date",
    "time",
    "status",
    "channel",
    "tx_kind",
    "direction",
)

OPERATORS = ("mtn_momo", "telecel_cash", "airtel_tigo_money", "unknown")


@dataclass
class Record:
    id: str
    body: str
    operator: str = "unknown"
    sender: str = ""
    received_at: Optional[str] = None
    source: str = "device_export"          # device_export | manual_capture | template | synthetic
    provenance: str = "unspecified"         # e.g. 'TEST_SUBJECT2.xml', 'author_own'
    consent: str = "unrecorded"             # participant_consent | author_own | synthetic | unrecorded
    is_transaction: bool = True
    category: Optional[str] = None          # Objective 3 gold label
    # Where the gold block came from: 'human_verified' | 'engine_draft_needs_review' | 'absent'.
    # Reported numbers should state this per record - an engine draft is not an annotation, and
    # scoring the engine against its own draft measures nothing. Default keeps older files valid
    # (every backup-serialised field needs a default).
    gold_source: str = "absent"
    gold: Dict[str, Any] = field(default_factory=dict)
    notes: str = ""

    def to_json(self) -> str:
        return json.dumps(asdict(self), ensure_ascii=False, sort_keys=True)

    @staticmethod
    def from_json(line: str) -> "Record":
        data = json.loads(line)
        known = {k: data.pop(k) for k in list(data) if k in Record.__dataclass_fields__}
        # 'gold' and 'notes' are dataclass fields, so they are already in `known`;
        # do NOT re-read them from `data` (they were popped). Explicit nulls inside
        # gold are meaningful: gold[field] = null says 'this message contains no such
        # field', which is what makes a false positive countable at all.
        return Record(**known)

    def annotated_gold(self) -> Dict[str, Any]:
        return {k: v for k, v in self.gold.items() if k in GOLD_FIELDS and v not in (None, "")}

    def explicit_absences(self) -> List[str]:
        """Fields annotated as 'this message contains no such field'."""
        return [k for k, v in self.gold.items() if k in GOLD_FIELDS and v is None]


def read_corpus(path: Path) -> List[Record]:
    records: List[Record] = []
    with open(path, "r", encoding="utf-8") as handle:
        for lineno, line in enumerate(handle, 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            try:
                records.append(Record.from_json(line))
            except Exception as exc:  # noqa: BLE001 - a malformed line must name itself
                raise ValueError(f"{path}:{lineno}: {exc}") from exc
    return records


def write_corpus(path: Path, records: Iterable[Record]) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with open(path, "w", encoding="utf-8") as handle:
        for record in records:
            handle.write(record.to_json() + "\n")
            count += 1
    return count


def dedupe_key(body: str) -> str:
    """Normalized body hash - operators resend the same alert to both parts of a
    transaction, and a device export can contain a message twice (backup + restore)."""
    import hashlib
    import re

    normalized = re.sub(r"\s+", " ", body or "").strip().lower()
    return hashlib.sha1(normalized.encode("utf-8")).hexdigest()[:16]


def split_records(records: List[Record], ratios=(0.7, 0.15, 0.15), seed: int = 7):
    """Deterministic stratified split by (operator, tx_kind) so every split sees every
    operator; stratification matters because AirtelTigo is the smallest stratum."""
    import random
    from collections import defaultdict

    random.seed(seed)
    buckets: Dict[tuple, List[Record]] = defaultdict(list)
    for rec in records:
        key = (rec.operator, str(rec.gold.get("tx_kind") or rec.category or "na"))
        buckets[key].append(rec)
    train: List[Record] = []
    dev: List[Record] = []
    test: List[Record] = []
    r_train, r_dev, _ = ratios
    for _key, group in sorted(buckets.items()):
        random.shuffle(group)
        n = len(group)
        n_train = int(round(n * r_train))
        n_dev = int(round(n * r_dev))
        if n >= 3 and n_train == n:
            n_train = n - 2
            n_dev = 1
        train.extend(group[:n_train])
        dev.extend(group[n_train:n_train + n_dev])
        test.extend(group[n_train + n_dev:])
    return train, dev, test
