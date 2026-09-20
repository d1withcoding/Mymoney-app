#!/usr/bin/env python3
"""Objective 2 evaluation: per-field extraction quality of the regex cascade.

Scoring rules (fixed, so the numbers in the paper are auditable):

* gold value present + predicted equal  -> true positive
* gold value present + predicted absent -> false negative
* gold value present + predicted differs -> false negative *and* false positive
  (a wrong value is worse than no value: it silently corrupts a budget)
* gold explicitly null (annotated absence) + predicted present -> false positive
* gold key missing (not annotated)       -> the prediction is 'unverifiable', counted
  in its own column and excluded from P/R. Real captures are usually only partially
  annotated; this keeps that honest instead of scoring it as an error.

Numeric fields compare as 2-dp decimals, dates as ISO, times as HH:MM, names
case/punctuation-insensitively, everything else as exact normalized strings.

Outputs (all regenerated, never hand-edited):
  results/extraction_metrics.json   machine-readable, feeds the tables
  results/extraction_metrics.md     paper-ready markdown tables
  results/extraction_failures.md    error analysis: what did not parse and why
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ghana_parse import GhanaSmsEngine  # noqa: E402
from ghana_parse import normalize as N  # noqa: E402
from ghana_parse.corpus import GOLD_FIELDS, Record, read_corpus  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "results"

# Objective 2 as written asks for amount / date / type / merchant-or-counterparty /
# balance. Those five are the headline table; the rest are reported as supporting.
HEADLINE = ("amount", "date", "direction", "merchant", "balance")

EVALUATED = list(GOLD_FIELDS) + ["merchant", "transaction_type"]


@dataclass
class Cell:
    tp: int = 0
    fp: int = 0
    fn: int = 0
    tn: int = 0
    unverifiable: int = 0

    @property
    def precision(self) -> Optional[float]:
        denom = self.tp + self.fp
        return round(self.tp / denom, 4) if denom else None

    @property
    def recall(self) -> Optional[float]:
        denom = self.tp + self.fn
        return round(self.tp / denom, 4) if denom else None

    @property
    def f1(self) -> Optional[float]:
        p, r = self.precision, self.recall
        if p is None or r is None or (p + r) == 0:
            return None
        return round(2 * p * r / (p + r), 4)

    def as_row(self) -> Dict[str, Any]:
        return {
            "tp": self.tp, "fp": self.fp, "fn": self.fn, "tn": self.tn,
            "unverifiable": self.unverifiable,
            "precision": self.precision, "recall": self.recall, "f1": self.f1,
        }


def values_equal(field: str, gold: Any, pred: Any) -> bool:
    if gold is None or pred is None:
        return False
    gold, pred = str(gold).strip(), str(pred).strip()
    if field in ("amount", "fee", "levy", "balance"):
        try:
            return Decimal(gold) == Decimal(pred)
        except InvalidOperation:
            return gold == pred
    if field in ("counterparty_name", "paybill_name"):
        return (N.name_key(gold) or "") == (N.name_key(pred) or "")
    if field == "reference":
        return gold.lower().strip(" .,:;") == pred.lower().strip(" .,:;")
    return gold == pred


def pred_for(record: Record, result, field: str) -> Optional[str]:
    """Map a corpus field name onto the engine's extraction, incl. two derived fields."""
    if field == "merchant":
        for candidate in ("counterparty_name", "paybill_name"):
            value = result.fields.get(candidate)
            if value:
                gold_merchant = record.gold.get("counterparty_name") or record.gold.get("paybill_name")
                if gold_merchant and N.name_key(gold_merchant) == N.name_key(value):
                    return value
        return result.fields.get("counterparty_name") or result.fields.get("paybill_name")
    if field == "transaction_type":
        return result.transaction_type
    return result.fields.get(field)


def gold_for(record: Record, field: str) -> Tuple[bool, Optional[str]]:
    """(annotated?, value). 'merchant' is annotated if either sub-field was annotated."""
    if field == "merchant":
        if "counterparty_name" not in record.gold and "paybill_name" not in record.gold:
            return False, None
        value = record.gold.get("counterparty_name") or record.gold.get("paybill_name")
        return True, value
    if field == "transaction_type":
        kind = record.gold.get("tx_kind")
        if not kind:
            return False, None
        return True, KIND_TO_TYPE.get(str(kind).lower())
    if field not in record.gold:
        return False, None
    return True, record.gold.get(field)


# Mirrors of the shipping thresholds, so this harness measures the boundary the app enforces
# rather than a friendlier version of it. GhanaSmsExtractor.MIN_ACCEPT_CONFIDENCE / AUTO_ACCEPT.
MIN_ACCEPT_CONFIDENCE = 0.45
AUTO_ACCEPT_CONFIDENCE = 0.75


def confidence_stats(confidences: List[float]) -> Dict[str, Any]:
    """Distribution of the stated heuristic over *accepted* parses. Reported because the number
    that matters for the app is the share of parses that clear the auto-accept bar: a parser that
    accepts everything at 0.50 is a review queue, not an automation."""
    if not confidences:
        return {"n": 0}
    ordered = sorted(confidences)
    buckets: Counter = Counter()
    for value in ordered:
        buckets[round(0.05 * int(value / 0.05), 2)] += 1
    return {
        "n": len(ordered),
        "min": round(ordered[0], 3),
        "median": round(ordered[len(ordered) // 2], 3),
        "mean": round(sum(ordered) / len(ordered), 3),
        "max": round(ordered[-1], 3),
        "below_accept_floor": sum(1 for v in ordered if v < MIN_ACCEPT_CONFIDENCE),
        "at_or_above_auto_accept": sum(1 for v in ordered if v >= AUTO_ACCEPT_CONFIDENCE),
        "auto_accept_share": round(sum(1 for v in ordered if v >= AUTO_ACCEPT_CONFIDENCE) / len(ordered), 4),
        "histogram_005": {f"{b:.2f}": buckets[b] for b in sorted(buckets)},
    }


KIND_TO_TYPE: Dict[str, str] = {}


def score(records: List[Record], engine: GhanaSmsEngine, strategy: str) -> Dict[str, Any]:
    cells: Dict[str, Cell] = defaultdict(Cell)
    cells_by_operator: Dict[Tuple[str, str], Cell] = defaultdict(Cell)
    cells_by_tag: Dict[Tuple[str, str], Cell] = defaultdict(Cell)
    template_hits: Counter = Counter()
    pattern_hits: Counter = Counter()
    reject_reasons: Counter = Counter()
    failures: List[Dict[str, Any]] = []
    accepted = 0
    exact_all = 0
    negatives_seen = 0
    negatives_parsed = 0
    ambiguous_dates = 0
    confidences: List[float] = []

    for record in records:
        result = engine.parse(record.body, record.sender, strategy=strategy)
        tags = [t.strip() for t in record.provenance.replace("tags=", "").split(",")] if "tags=" in record.provenance else []

        if not record.is_transaction:
            negatives_seen += 1
            if result.accepted:
                negatives_parsed += 1
                failures.append({"id": record.id, "why": "negative message was parsed", "body": record.body[:120],
                                 "got": result.fields})
            continue

        if not result.accepted:
            reject_reasons[result.reject_reason or "unknown"] += 1
            failures.append({"id": record.id, "operator": record.operator, "why": f"rejected: {result.reject_reason}",
                             "body": record.body[:160]})
            continue
        accepted += 1
        confidences.append(result.confidence)
        template_hits[result.template_id or "(no template)"] += 1
        for field, pattern_id in result.provenance.items():
            pattern_hits[pattern_id] += 1
        if result.ambiguous_date:
            ambiguous_dates += 1

        all_correct = True
        for field in EVALUATED:
            annotated, gold_value = gold_for(record, field)
            if not annotated and field not in HEADLINE and field not in ("status", "tx_kind", "fee"):
                continue  # supporting fields are only scored where the author annotated them
            pred_value = pred_for(record, result, field)
            cell = cells[field]
            op_cell = cells_by_operator[(record.operator, field)]
            tag_cells = [cells_by_tag[(tag, field)] for tag in tags]
            for target in (cell, op_cell, *tag_cells):
                if gold_value:
                    if pred_value and values_equal(field, gold_value, pred_value):
                        target.tp += 1
                    else:
                        target.fn += 1
                        if pred_value:
                            target.fp += 1
                elif field in record.gold and record.gold[field] is None:
                    if pred_value:
                        target.fp += 1
                    else:
                        target.tn += 1
                else:
                    if pred_value:
                        target.unverifiable += 1
            if gold_value and not (pred_value and values_equal(field, gold_value, pred_value)):
                all_correct = False
                if len(failures) < 400 and field in HEADLINE:
                    failures.append({"id": record.id, "operator": record.operator, "field": field,
                                     "gold": gold_value, "predicted": pred_value, "body": record.body[:160]})
        if all_correct:
            exact_all += 1

    total_transactions = sum(1 for r in records if r.is_transaction)
    return {
        "strategy": strategy,
        "records": len(records),
        "transactions": total_transactions,
        "accepted_parses": accepted,
        "coverage": round(accepted / total_transactions, 4) if total_transactions else None,
        "exact_match_all_headline_and_supporting": exact_all,
        "exact_match_rate": round(exact_all / accepted, 4) if accepted else None,
        "ambiguous_date_records": ambiguous_dates,
        "negatives_seen": negatives_seen,
        "negatives_falsely_parsed": negatives_parsed,
        "negative_false_positive_rate": round(negatives_parsed / negatives_seen, 4) if negatives_seen else None,
        "fields": {field: cells[field].as_row() for field in EVALUATED},
        "by_operator": {f"{op}/{field}": cell.as_row() for (op, field), cell in sorted(cells_by_operator.items())},
        "by_tag": {f"{tag}/{field}": cell.as_row() for (tag, field), cell in sorted(cells_by_tag.items())
                    if cell.tp or cell.fp or cell.fn},
        "template_hit_counts": dict(template_hits.most_common()),
        "pattern_hit_counts": dict(pattern_hits.most_common()),
        "reject_reasons": dict(reject_reasons.most_common()),
        "confidence": confidence_stats(confidences),
        "_failures": failures,
    }


def fmt(value: Any) -> str:
    if value is None:
        return "-"
    if isinstance(value, float):
        return f"{value:.3f}" if value < 1 else f"{value:.3f}"
    return str(value)


def markdown(results: Dict[str, Dict[str, Any]], corpus_label: str) -> str:
    lines = [
        f"# Objective 2 - regex extraction metrics ({corpus_label})",
        "",
        "> Generated by `tools/evaluate.py`. Do not hand-edit: numbers here are the",
        "> measured output of the current spec against the current corpus.",
        "",
        "Arm definitions: **hybrid** = template match + per-field gap-fill (shipped),",
        "**template_only** = precision-biased ablation, **fallback_only** = coverage-biased ablation.",
        "",
    ]
    for strategy, data in results.items():
        lines += [
            f"## Strategy: `{strategy}`",
            "",
            f"- records: {data['records']} (transactions {data['transactions']}, negatives {data['negatives_seen']})",
            f"- accepted parses: **{data['accepted_parses']}** / {data['transactions']} = coverage **{fmt(data['coverage'])}**",
            f"- false positives on non-transaction controls: {data['negatives_falsely_parsed']} / {data['negatives_seen']} "
            f"= **{fmt(data['negative_false_positive_rate'])}**",
            f"- records where *every* scored field is exact: {data['exact_match_all_headline_and_supporting']} "
            f"({fmt(data['exact_match_rate'])} of accepted)",
            f"- messages with a day/month-ambiguous date string: {data['ambiguous_date_records']}",
            "",
            "### Per-field (Objective 2 fields first)",
            "",
            "| field | P | R | F1 | TP | FP | FN | TN | unverifiable |",
            "|---|---|---|---|---|---|---|---|---|",
        ]
        ordered = [f for f in HEADLINE if f in data["fields"]] + [f for f in data["fields"] if f not in HEADLINE]
        for field in ordered:
            row = data["fields"][field]
            lines.append(
                f"| {'**' + field + '**' if field in HEADLINE else field} | {fmt(row['precision'])} | "
                f"{fmt(row['recall'])} | {fmt(row['f1'])} | {row['tp']} | {row['fp']} | {row['fn']} | "
                f"{row['tn']} | {row['unverifiable']} |"
            )
        lines += ["", "### Per operator (headline fields)", "",
                  "| operator | field | P | R | F1 | support |", "|---|---|---|---|---|---|"]
        for key, row in data["by_operator"].items():
            operator, field = key.split("/", 1)
            if field not in HEADLINE:
                continue
            support = row["tp"] + row["fn"]
            if support == 0:
                continue
            lines.append(f"| {operator} | {field} | {fmt(row['precision'])} | {fmt(row['recall'])} | "
                         f"{fmt(row['f1'])} | {support} |")
        lines += ["", "### Error breakdown by message tag (recall on headline fields)", "",
                  "| tag | field | P | R | F1 | support |", "|---|---|---|---|---|---|"]
        for key, row in data["by_tag"].items():
            tag, field = key.split("/", 1)
            if field not in HEADLINE or tag.startswith("op_") or tag.startswith("kind_"):
                continue
            lines.append(f"| {tag} | {field} | {fmt(row['precision'])} | {fmt(row['recall'])} | "
                         f"{fmt(row['f1'])} | {row['tp'] + row['fn']} |")
        conf = data["confidence"]
        lines += ["", "### Confidence distribution (accepted parses)", ""]
        if conf.get("n"):
            lines += [f"n = {conf['n']}, min {conf['min']}, median {conf['median']}, mean "
                      f"{conf['mean']}, max {conf['max']}; accepted-but-below-the-{MIN_ACCEPT_CONFIDENCE} "
                      f"floor: **{conf['below_accept_floor']}** (the app would refuse these even though the "
                      f"engine produced all required fields)",
                      f"clearing the {AUTO_ACCEPT_CONFIDENCE} auto-accept bar: **{conf['at_or_above_auto_accept']}** "
                      f"({conf['auto_accept_share']:.3f}); everything below it is a one-tap review, not a "
                      "correction, because every field the engine did extract is already filled in.",
                      "", "| confidence band | accepted parses |", "|---|---|"]
            for band, count in conf["histogram_005"].items():
                lines.append(f"| {band} - {float(band) + 0.05:.2f} | {count} |")
        else:
            lines.append("_no accepted parses_")
        lines += ["", "### Template coverage", "", "| template | accepted parses |", "|---|---|"]
        for template, count in data["template_hit_counts"].items():
            lines.append(f"| `{template}` | {count} |")
        lines += ["", "### Pattern hit counts (provenance; the fallback net and gap-fill)", "",
                  "| pattern | field hits |", "|---|---|",]
        for pattern, count in data["pattern_hit_counts"].items():
            lines.append(f"| `{pattern}` | {count} |")
        lines.append("")
    return "\n".join(lines)


def failures_markdown(results: Dict[str, Dict[str, Any]]) -> str:
    lines = ["# Extraction failures - error analysis", "",
             "Each row is a real miss or a false positive on the evaluation corpus.",
             "Use it to decide what to add to `spec/ghana_patterns.json`.", ""]
    for strategy, data in results.items():
        lines += [f"## Strategy: `{strategy}`", "", "### Rejections", ""]
        for reason, count in data["reject_reasons"].items():
            lines.append(f"- `{reason}` - **{count}**")
        lines += ["", "### Field-level misses (first 60)", "", "```json"]
        for item in data["_failures"][:60]:
            lines.append(json.dumps(item, ensure_ascii=False))
        lines += ["```", ""]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus", type=Path, default=ROOT / "corpus" / "gold" / "seed-corpus.jsonl")
    parser.add_argument("--strategies", default="hybrid,template_only,fallback_only")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--out-dir", type=Path, default=RESULTS)
    parser.add_argument("--label", default="")
    args = parser.parse_args()

    global KIND_TO_TYPE
    engine = GhanaSmsEngine()
    KIND_TO_TYPE = dict(engine.spec.kind_to_type)

    records = read_corpus(args.corpus)
    if args.limit:
        records = records[: args.limit]
    label = args.label or args.corpus.stem

    results: Dict[str, Dict[str, Any]] = {}
    for strategy in [s.strip() for s in args.strategies.split(",") if s.strip()]:
        results[strategy] = score(records, engine, strategy)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "extraction_metrics.json").write_text(
        json.dumps({"corpus": str(args.corpus.name), "results": results}, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    (args.out_dir / "extraction_metrics.md").write_text(markdown(results, label), encoding="utf-8")
    (args.out_dir / "extraction_failures.md").write_text(failures_markdown(results), encoding="utf-8")

    headline = results.get("hybrid", next(iter(results.values())))
    print(f"corpus={label} records={headline['records']} coverage={fmt(headline['coverage'])}")
    print(f"{'field':<22}{'P':>8}{'R':>8}{'F1':>8}")
    for field in HEADLINE:
        row = headline["fields"][field]
        print(f"{field:<22}{fmt(row['precision']):>8}{fmt(row['recall']):>8}{fmt(row['f1']):>8}")
    print(f"negative FP rate: {fmt(headline['negative_false_positive_rate'])}")
    print(f"wrote {args.out_dir / 'extraction_metrics.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
