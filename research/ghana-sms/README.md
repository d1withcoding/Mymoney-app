# Ghana mobile-money SMS: corpus, parsers, classifier, evaluation

Three things live here, and they are kept in one loop so they cannot drift:

```
spec/*.json  ──►  tools/ghana_parse (Python reference)  ──►  results/*.md   (the paper's numbers)
     │                                                              │
     └──────────────►  tools/sync_kotlin.py  ◄──────────────────────┘
                              │
                              ▼
              parser-core/.../ghana/*.kt  +  test fixtures (TSV)
```

The extraction specification (`spec/ghana_patterns.json`) is the only place a message format is
written down. Python expands its tokens for evaluation; the generator expands the same tokens for
Kotlin and also exports the corpus as test fixtures, so `:parser-core:jvmTest` re-runs the study's
own evaluation on every build.

## Layout

| path | what it is |
|---|---|
| `spec/ghana_patterns.json` | v1.1.0: tokens, 16 fields, 46 per-field pattern groups, 30 whole-message templates (12 MTN / 9 Telecel / 9 AirtelTigo), skip/status/direction rules, boilerplate sentences, kind -> type/direction maps. |
| `spec/ghana_categories.json` | Objective-3 taxonomy: 15 spending labels, each with keyword and merchant lists, plus which shipped PennyWise category it maps to. |
| `tools/ghana_parse/spec.py` | token expansion + compilation + validation (`{TOKEN}` substitution must be identical in both languages; unknown tokens are a hard error). |
| `tools/ghana_parse/normalize.py` | field normalisers: amounts, dates (day-first), times, MSISDNs, names, ids, channels, boilerplate stripping. |
| `tools/ghana_parse/engine.py` | the cascade: tidy -> skip rules -> operator routing -> template scoring -> per-field fallback -> normalise -> kind/direction/status inference -> confidence. |
| `tools/ghana_parse/corpus.py` | corpus record schema, JSONL I/O, dedup, deterministic stratified splits. |
| `tools/ghana_parse/redact.py` | two-tier PII redaction applied at ingest (see `corpus/CORPUS_SCHEMA.md`). |
| `tools/ingest.py` | CSV/TSV/XML/TXT -> canonical JSONL with column mapping, redaction, and an engine-draft gold pass for review. |
| `tools/make_seed_corpus.py` | synthetic placeholder corpus (60 shapes x operators x render variants), with hard cases deliberately included. |
| `tools/evaluate.py` | Objective-2 metrics: per-field P/R/F1, per-operator and per-tag breakdowns, three ablation arms, template hit counts, reject histogram, negative-control FP rate. |
| `tools/taxonomy.py` | Objective-1 measurements: format families, field-label vocabulary, money/date/time shapes, id geometry, counterparty rendering, MSISDN-prefix x operator cross-tab, multipart lengths. |
| `tools/train.py` | Objective-3: Tier-0 rules + TF-IDF/logistic-regression Tier-1, ablation arms, artifact export, Kotlin parity fixtures. |
| `tools/sync_kotlin.py` | generates `GhanaPatterns.kt` + `GhanaCategories.kt`, copies the model and TSV fixtures into `parser-core/src/test/resources/ghana/`; `--check` is the CI drift gate. |
| `tools/make_results_doc.py` | assembles `paper/RESULTS.md` by slicing tables out of `results/*.md`, so the paper's numbers cannot drift from the harness's. `--check` fails on staleness. |
| `corpus/` | `CORPUS_SCHEMA.md` (schema + annotation protocol + redaction tiers), `raw/` (git-ignored drop zone for unredacted captures), `gold/` (JSONL corpora), `public/` (nothing distributable yet). |
| `results/`, `model/` | generated reports (`extraction_metrics`, `classifier_metrics`, `taxonomy`, `extraction_failures`, `parity_fixtures`) and the exported classifier artifact. |
| `paper/`, `scripts/` | the write-up kit (methods, results, related work, limitations, privacy-ethics) and `run_all.sh`. |

## Kotlin side (already generated, do not hand-edit)

- `parser-core/src/main/kotlin/com/pennywiseai/parser/core/ghana/GhanaPatterns.kt` - generated tables.
- `parser-core/src/main/kotlin/com/pennywiseai/parser/core/ghana/GhanaCategories.kt` - generated taxonomy.
- `.../ghana/GhanaPatternTypes.kt`, `GhanaNormalizer.kt`, `GhanaSmsExtractor.kt`, `GhanaCategoryClassifier.kt` - hand-written, mirror the Python semantics.
- `parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/{GhanaMobileMoneyParser,MtnMoMoParser,TelecelCashParser,AirtelTigoMoneyParser}.kt` - the three wallets, registered in `BankParserFactory`.
- Tests: `TestMtnMoMoParser.kt`, `TestTelecelCashParser.kt`, `TestAirtelTigoMoneyParser.kt`, `TestGhanaNormalizer.kt`, `TestGhanaSeedCorpus.kt` (parity + floors over the whole corpus), `TestGhanaCategoryClassifier.kt` (feature/vector/score/decision parity per policy branch).

## Commands

```bash
python3 tools/make_seed_corpus.py --per-shape 40 --seed 11     # placeholder corpus
python3 tools/ingest.py --in corpus/raw/sms.csv --format csv \
    --map 'body=text,sender=address,category=cat' --consent participant_consent \
    --out corpus/gold/corpus.jsonl --report-dir results/ingest-report     # real captures (dry-run first!)
python3 tools/evaluate.py --corpus corpus/gold/corpus.jsonl    # Objective 2
python3 tools/taxonomy.py --corpus corpus/gold/corpus.jsonl    # Objective 1
python3 tools/train.py --corpus corpus/gold/corpus.jsonl       # Objective 3
python3 tools/sync_kotlin.py && python3 tools/sync_kotlin.py --check
```

Python 3.11+, standard library only (the study environment had no package installs; keeping the
harness dependency-free is what makes it reviewable).

## Measured, on the seed corpus (1 290 records: 1 200 transactions + 90 non-transaction controls)

| headline field | P | R | F1 |
|---|---|---|---|
| amount | 1.000 | 1.000 | 1.000 |
| date | 1.000 | 1.000 | 1.000 |
| direction | 0.999 | 0.999 | 0.999 |
| merchant (counterparty or paybill) | 1.000 | 0.977 | 0.988 |
| balance | 1.000 | 1.000 | 1.000 |

Coverage 1.000, false positives on controls 0.000, 0.651 of accepted parses exact on *every* scored
field. Classifier: cascade test accuracy 0.978 / macro-F1 0.968 vs 0.850 rules-only and 0.967
model-only; artifact 189 KB.

**Read that as "the engine reproduces the specification it was written from", not as real-world
accuracy.** The measurements that carry information today are the ones where the corpus and the
engine disagree by construction: `channel` F1 0.695, `tx_kind` 0.823, `reference` 0.775,
`status` recall 0.912, and the template-vs-fallback ablation (templates alone cover 0.491 of the
corpus). Everything becomes an empirical claim only when the study's own captures replace
`corpus/gold/seed-corpus.jsonl`.

## Adding your real corpus

1. Drop the export in `corpus/raw/` (git-ignored; never committed).
2. `python3 tools/ingest.py --dry-run ...` first: it prints how many rows mapped, how many were
   redacted, and which senders were seen - without writing anything.
3. Ingest for real, then annotate. `--auto-gold` gives you engine drafts marked
   `gold_source = engine_draft_needs_review`; a human has to flip them to `human_verified`
   before the numbers mean anything. Report both counts in the paper.
4. `bash scripts/run_all.sh` and note that `results/` now describes your corpus, not the placeholder.
5. Re-run `tools/sync_kotlin.py` so the Kotlin fixtures follow the new corpus, then
   `./gradlew :parser-core:jvmTest`.

## Hard rules (from the repo's CLAUDE.md, which apply to this directory too)

- No real PII in anything committed: not in corpora, not in tests, not in comments. `corpus/raw/`
  is git-ignored for that reason, and the redaction audit map is never written next to a corpus.
- Money is always currency-tagged; nothing here ever sums across currencies.
- Every field that reaches the database has a Kotlin default, and the parsers leave
  `accountLast4` null on purpose (the wallet *is* the account; see `GhanaMobileMoneyParser`).
