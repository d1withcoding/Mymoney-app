#!/usr/bin/env python3
"""Generate the Kotlin extraction tables from ``spec/ghana_patterns.json``.

The specification is the single source of truth for the Ghana parsers: this script
expands the token templates exactly like ``ghana_parse/spec.py`` does and writes
``parser-core/src/main/kotlin/com/pennywiseai/parser/core/ghana/GhanaPatterns.kt``.
The regex *strings* are therefore identical in both languages, which is what lets the
paper claim that the evaluated prototype and the shipped parser do the same matching.

    python3 tools/sync_kotlin.py            # regenerate + copy model assets
    python3 tools/sync_kotlin.py --check    # CI: fail if the Kotlin file is stale

``--check`` is what you run before committing a spec edit (see ../../CLAUDE.md: the
real build gate is ./init.sh parser, which needs a JDK this sandbox does not have).
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from pathlib import Path
from typing import Any, Dict, List

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
SPEC = ROOT / "spec" / "ghana_patterns.json"
KOTLIN_DIR = REPO / "parser-core" / "src" / "main" / "kotlin" / "com" / "pennywiseai" / "parser" / "core" / "ghana"
KOTLIN_FILE = KOTLIN_DIR / "GhanaPatterns.kt"
TEST_RESOURCES = REPO / "parser-core" / "src" / "test" / "resources" / "ghana"

sys.path.insert(0, str(ROOT / "tools"))
from ghana_parse.spec import expand_tokens  # noqa: E402

HEADER = """// GENERATED FILE - do not edit by hand.
//
// Source of truth: research/ghana-sms/spec/ghana_patterns.json
// Regenerate:     python3 research/ghana-sms/tools/sync_kotlin.py
//
// The regex strings below are produced by the same token expansion the Python
// reference implementation uses (research/ghana-sms/tools/ghana_parse/spec.py), so
// the evaluation harness and this parser cannot drift apart. Field-extraction
// behaviour lives in GhanaSmsExtractor.kt; this file is tables only.

package com.pennywiseai.parser.core.ghana

/** Compiled extraction specification for Ghana mobile-money SMS (spec v{version}). */
object GhanaPatterns {{
    const val SPEC_VERSION: String = "{version}"
    const val CURRENCY: String = "{currency}"

    val KIND_TO_TRANSACTION_TYPE: Map<String, String> = mapOf(
{kind_type}
    )

    val KIND_TO_DIRECTION: Map<String, String> = mapOf(
{kind_dir}
    )

    val SKIP_RULES: List<GhanaSkipRule> = listOf(
{skip}
    )

    val STATUS_RULES: List<GhanaStatusRule> = listOf(
{status}
    )

    val DIRECTION_RULES: List<GhanaDirectionRule> = listOf(
{direction}
    )

    val SHARED_FIELDS: Map<String, List<GhanaFieldPattern>> = mapOf(
{shared}
    )

    val OPERATORS: Map<String, GhanaOperatorSpec> = mapOf(
{operators}
    )

    /** Operator boilerplate that must not reach field extraction (see GhanaNormalizer). */
    val BOILERPLATE: List<Regex> = listOf(
{boilerplate}
    )
}}
"""

CATEGORIES_HEADER = """// GENERATED FILE - do not edit by hand.
//
// Source of truth: research/ghana-sms/spec/ghana_categories.json
// Regenerate:      python3 research/ghana-sms/tools/sync_kotlin.py
//
// Tier 0 of the Objective-3 cascade: the deterministic keyword/merchant layer. The
// Python trainer (tools/train.py, RuleTier) reads the same JSON, so the rules the paper
// reports and the rules the phone runs are one artifact. Categories whose 'requires_user_creation'
// is true are not in PennyWise's default category set; the app must create them once
// before they can be assigned (see the integration note in GhanaCategoryClassifier.kt).

package com.pennywiseai.parser.core.ghana

/** Spending taxonomy for Ghana mobile-money messages (spec v{version}). */
object GhanaCategories {{
    const val SPEC_VERSION: String = "{version}"
    const val FALLBACK_LABEL: String = "other"

    /** Stable label order - index i is also the model's output neuron i. */
    val LABELS: List<String> = listOf(
{labels}
    )

    /** Labels that do not exist in DefaultCategoryData and must be created by the user. */
    val USER_CREATED_LABELS: Set<String> = setOf(
{user_created}
    )

    /** label -> (matchToken, keyword-or-merchant). Lowercased; len > 2 only, as in train.py. */
    val LEXICON: Map<String, List<String>> = mapOf(
{lexicon}
    )

    /** label -> PennyWise category name (shared/.../DefaultCategoryData.kt). */
    val APP_CATEGORY: Map<String, String> = mapOf(
{app_category}
    )

    /** Labels whose app category is not shipped, i.e. mapping falls back to [FALLBACK_APP_CATEGORY]. */
    const val FALLBACK_APP_CATEGORY: String = "Others"
}}
"""


def kt_string(value: str) -> str:
    """A Kotlin string literal (non-raw: patterns contain '$' and '"' freely)."""
    escaped = (value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$"))
    return f'"{escaped}"'


def regex_ctor(source: str) -> str:
    return f"Regex({kt_string(source)}, RegexOption.IGNORE_CASE)"


def field_patterns_json(items: List[Dict[str, Any]], tokens: Dict[str, str]) -> str:
    lines = []
    for item in items:
        source = expand_tokens(item["regex"], tokens)
        re.compile(source, re.IGNORECASE)  # fail loudly here, not in a Gradle build
        constant = item.get("value")
        whole = "true" if item.get("whole_match") else "false"
        lines.append(
            "            GhanaFieldPattern("
            f"{kt_string(item.get('id', ''))}, {regex_ctor(source)}, "
            f"{int(item.get('group', 1))}, {whole}, "
            + (kt_string(constant) if constant is not None else "null")
            + "),"
        )
    return "\n".join(lines)


def templates_json(items: List[Dict[str, Any]], tokens: Dict[str, str]) -> str:
    lines = []
    for item in sorted(items, key=lambda t: int(t.get("priority", 50))):
        source = expand_tokens(item["regex"], tokens)
        re.compile(source, re.IGNORECASE)
        groups = ", ".join(
            f"{int(g['idx'])} to {kt_string(g['field'])}" for g in item.get("groups", [])
        )
        lines.append(
            "            GhanaTemplate(\n"
            f"                {kt_string(item['id'])},\n"
            f"                {kt_string(item.get('kind', 'unknown'))},\n"
            f"                {int(item.get('priority', 50))},\n"
            f"                {regex_ctor(source)},\n"
            f"                mapOf({groups})\n"
            "            ),"
        )
    return "\n".join(lines)


def emit(spec: Dict[str, Any]) -> str:
    tokens = spec["tokens"]
    kind_type = "\n".join(
        f'        {kt_string(k)} to {kt_string(v)},' for k, v in spec["kind_to_transaction_type"].items()
    )
    kind_dir = "\n".join(
        f'        {kt_string(k)} to {kt_string(v)},' for k, v in spec.get("kind_to_direction", {}).items()
    )
    skip = "\n".join(
        f"        GhanaSkipRule({kt_string(r['id'])}, "
        f"{regex_ctor(expand_tokens(r['pattern'], tokens))}, {kt_string(r.get('reason', ''))}),"
        for r in spec.get("skip_rules", [])
    )
    status = "\n".join(
        f"        GhanaStatusRule({kt_string(r['id'])}, "
        f"{regex_ctor(expand_tokens(r['pattern'], tokens))}, {kt_string(r['status'])}),"
        for r in spec.get("status_rules", [])
    )
    direction = "\n".join(
        f"        GhanaDirectionRule({kt_string(r['id'])}, "
        f"{regex_ctor(expand_tokens(r['pattern'], tokens))}, {kt_string(r['direction'])}),"
        for r in spec.get("direction_rules", [])
    )
    shared_blocks = []
    for name, items in spec.get("shared_fields", {}).items():
        body = field_patterns_json(items, tokens)
        if body:
            shared_blocks.append(f"        {kt_string(name)} to listOf(\n{body}\n        ),")
    shared = "\n".join(shared_blocks)
    boilerplate = "\n".join(
        f"        {regex_ctor(pattern)}," for pattern in spec.get("boilerplate_patterns", [])
    )

    operator_blocks = []
    for op in spec["operators"]:
        senders = ", ".join(regex_ctor(s) for s in op.get("sender_patterns", []))
        markers = ", ".join(regex_ctor(m) for m in op.get("body_markers", []))
        fields_blocks = []
        for name, items in (op.get("fields") or {}).items():
            body = field_patterns_json(items, tokens)
            if body:
                fields_blocks.append(f"                {kt_string(name)} to listOf(\n{body}\n                ),")
        fields = "mapOf(\n" + "\n".join(fields_blocks) + "\n            )" if fields_blocks else "emptyMap()"
        operator_blocks.append(
            f"        {kt_string(op['id'])} to GhanaOperatorSpec(\n"
            f"            {kt_string(op['id'])},\n"
            f"            {kt_string(op['display_name'])},\n"
            f"            listOf({senders}),\n"
            f"            listOf({markers}),\n"
            "            listOf(\n" + templates_json(op.get("templates", []), tokens) + "\n            ),\n"
            f"            {fields}\n"
            "        ),"
        )
    operators = "\n".join(operator_blocks)

    return HEADER.format(
        version=spec.get("spec_version", "0"),
        currency=spec["currency"]["code"],
        kind_type=kind_type,
        kind_dir=kind_dir,
        skip=skip,
        status=status,
        direction=direction,
        shared=shared,
        operators=operators,
        boilerplate=boilerplate,
    )


def emit_categories(categories: Dict[str, Any]) -> str:
    labels = [entry["id"] for entry in categories["labels"]]
    user_created = [e["id"] for e in categories["labels"] if e.get("requires_user_creation")]
    lexicon_lines = []
    for entry in categories["labels"]:
        # Same filter as train.py:RuleTier - keywords only if they are long enough to be
        # safe substrings, merchant names verbatim (they are matched against the merchant field).
        terms = {kw.lower() for kw in (entry.get("keywords") or []) if len(kw) > 2}
        terms |= {m.lower() for m in (entry.get("merchants") or [])}
        rendered = ", ".join(kt_string(t) for t in sorted(terms))
        lexicon_lines.append(f"        {kt_string(entry['id'])} to listOf({rendered}),")
    return CATEGORIES_HEADER.format(
        version=categories.get("spec_version", "1"),
        labels="\n".join(f"        {kt_string(label)}," for label in labels),
        user_created="\n".join(f"        {kt_string(label)}," for label in user_created),
        lexicon="\n".join(lexicon_lines),
        app_category="\n".join(
            f"        {kt_string(entry['id'])} to {kt_string(entry.get('app_category') or 'Others')},"
            for entry in categories["labels"]
        ),
    )


def write_tsv_fixtures() -> List[str]:
    """Kotlin tests read plain TSV rather than pulling a JSON dependency into parser-core."""
    written: List[str] = []
    TEST_RESOURCES.mkdir(parents=True, exist_ok=True)

    fixtures = json.loads((ROOT / "results" / "parity_fixtures.json").read_text(encoding="utf-8"))
    rows = ["#text\tmeta\tvector\tscores\tprediction\trule_label\tgold\trule_why"
            "\trule_strength\tmodel_prob\tcascade_label\tcascade_tier\tcascade_strict_label\tbranch"]

    for row in fixtures:
        # ';' because values (merchant names) contain spaces; the Kotlin test splits on ';'.
        meta = ";".join(f"{k}={v}" for k, v in sorted(row["meta"].items()) if v not in (None, "", "na"))
        vector = " ".join(f"{k}={v:.6f}" for k, v in sorted(row["vector"].items()))
        scores = " ".join(f"{v:.6f}" for v in row["scores"])
        rows.append("\t".join(
            [row["text"].replace("\t", " ").replace("\n", " "), meta, vector, scores,
             row["prediction"], row["rule_label"] or "", row["gold"],
             (row.get("rule_why") or "").replace("\t", " "), row.get("rule_strength") or "",
             f"{row.get('model_prob', 0.0):.4f}", row.get("cascade_label") or "",
             (row.get("cascade_tier") or "").replace("\t", " "),
             row.get("cascade_strict_label") or "", row.get("branch") or ""]))
    path = TEST_RESOURCES / "parity_fixtures.tsv"
    path.write_text("\n".join(rows) + "\n", encoding="utf-8")
    written.append(str(path.relative_to(REPO)))

    # Two fixtures, two jobs:
    #   ghana_python_predictions.tsv  = the Python reference engine's *predictions*, which the
    #       Kotlin runtime must reproduce field for field (parity: the generated patterns and the
    #       hand-written mirror really do behave the same).
    #   ghana_corpus_expectations.tsv = the *gold* labels, used for the absolute floors
    #       (coverage, exact-amount rate, negative-rejection rate) so a spec edit that silently
    #       degrades extraction fails CI even if Python and Kotlin stay in agreement.
    sys.path.insert(0, str(ROOT / "tools"))
    from ghana_parse import GhanaSmsEngine  # local import: only needed for fixture emission
    from ghana_parse.corpus import read_corpus

    def tags_of(record) -> str:
        # make_seed_corpus.py stores '... tags=a,b' inside provenance; the corpus test uses it
        # to report which rows are hard cases (mixed-case names, ambiguous dates, truncation).
        _head, sep, tail = record.provenance.partition(" tags=")
        if not sep:
            return ""
        return tail.split()[0].replace(",", "+") if tail.split() else ""

    engine = GhanaSmsEngine()
    records = read_corpus(ROOT / "corpus" / "gold" / "seed-corpus.jsonl")
    pred_rows = ["#id\toperator\tsender\tbody\taccepted\treject_reason\tconfidence\tmode"
                 "\ttemplate_id\tamount\tdate\tdirection\tmerchant\tbalance\ttx_kind"]
    exp_rows = ["#id\tis_transaction\toperator\tsender\tbody\ttags\tamount\tdate\tdirection"
                "\tmerchant\tbalance\ttransaction_type"]
    kind_to_type = json.loads(SPEC.read_text(encoding="utf-8"))["kind_to_transaction_type"]
    for record in records:
        result = engine.parse(record.body, record.sender)
        gold_merchant = record.gold.get("paybill_name") or record.gold.get("counterparty_name") or ""
        body = (record.body or "").replace("\t", " ").replace("\n", " ")
        pred_rows.append("\t".join([
            record.id, record.operator, record.sender or "", body,
            "1" if result.accepted else "0", (result.reject_reason or "").replace("\t", " "),
            f"{result.confidence:.3f}", result.mode or "", result.template_id or "",
            result.fields.get("amount", ""), result.fields.get("date", ""),
            result.fields.get("direction", ""), result.fields.get("paybill_name")
            or result.fields.get("counterparty_name", ""), result.fields.get("balance", ""),
            result.fields.get("tx_kind", ""),
        ]))
        exp_rows.append("\t".join([
            record.id, "1" if record.is_transaction else "0", record.operator, record.sender or "", body,
            tags_of(record),
            record.gold.get("amount") or "", record.gold.get("date") or "",
            gold_merchant or "", record.gold.get("balance") or "",
            kind_to_type.get(record.gold.get("tx_kind") or "", ""),
        ]))
    for name, rows in (("ghana_python_predictions.tsv", pred_rows), ("ghana_corpus_expectations.tsv", exp_rows)):
        path = TEST_RESOURCES / name
        path.write_text("\n".join(rows) + "\n", encoding="utf-8")
        written.append(str(path.relative_to(REPO)))
    return written


def copy_model_assets() -> List[str]:
    """The classifier artifact + parity fixtures become JVM test resources."""
    copied: List[str] = []
    TEST_RESOURCES.mkdir(parents=True, exist_ok=True)
    for src in (ROOT / "model" / "ghana_category_model_v1.tsv",):
        if src.exists():
            shutil.copyfile(src, TEST_RESOURCES / src.name)
            copied.append(str((TEST_RESOURCES / src.name).relative_to(REPO)))
    return copied


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="fail if the committed Kotlin file is stale")
    parser.add_argument("--no-assets", action="store_true", help="skip copying model artifacts into test resources")
    args = parser.parse_args()

    spec = json.loads(SPEC.read_text(encoding="utf-8"))
    generated = emit(spec)
    header_only_ok = True
    if args.check:
        expected = {KOTLIN_FILE: generated}
        cat_path = ROOT / "spec" / "ghana_categories.json"
        if cat_path.exists():
            expected[KOTLIN_DIR / "GhanaCategories.kt"] = emit_categories(json.loads(cat_path.read_text(encoding="utf-8")))
        current = "".join(
            (path.read_text(encoding="utf-8") if path.exists() else "") + ("\x00" if path != KOTLIN_FILE else "")
            for path in ()
        )
        if any(expected[path] != (path.read_text(encoding="utf-8") if path.exists() else "") for path in expected):
            stale = [str(p.relative_to(REPO)) for p, text in expected.items()
                     if text != (p.read_text(encoding="utf-8") if p.exists() else "")]
            print(f"STALE: {', '.join(stale)} do\n                  not match the spec.\n"
                  f"Run: python3 research/ghana-sms/tools/sync_kotlin.py", file=sys.stderr)
            return 1
        print(f"Kotlin patterns are in sync with {SPEC.name}")
        return 0

    KOTLIN_DIR.mkdir(parents=True, exist_ok=True)
    KOTLIN_FILE.write_text(generated, encoding="utf-8")
    categories_path = ROOT / "spec" / "ghana_categories.json"
    if categories_path.exists():
        (KOTLIN_DIR / "GhanaCategories.kt").write_text(
            emit_categories(json.loads(categories_path.read_text(encoding="utf-8"))), encoding="utf-8"
        )
    n_templates = sum(len(op.get("templates", [])) for op in spec["operators"])
    n_patterns = sum(len(v) for v in spec.get("shared_fields", {}).values()) + sum(
        len(items) for op in spec["operators"] for items in (op.get("fields") or {}).values()
    )
    print(f"wrote {KOTLIN_FILE.relative_to(REPO)} "
          f"({len(generated.splitlines())} lines, {n_templates} templates, {n_patterns} field patterns)")
    if not args.no_assets:
        for path in copy_model_assets() + write_tsv_fixtures():
            print(f"copied {path}")
    return 0 if header_only_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
