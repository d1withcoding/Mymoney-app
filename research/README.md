# Ghana SMS parsing research - deliverables for the paper

Companion material for *"Privacy-preserving mobile financial tracking application using SMS
transaction parsing and on-device artificial intelligence in Ghana"*. Everything here is
research scaffolding: it is deliberately **outside** the app's production source set except for
the parser and classifier code that the study's second and third objectives actually ship
(`parser-core/src/main/kotlin/com/pennywiseai/parser/core/ghana/` and the three wallet parsers).

| directory | contents |
|---|---|
| `ghana-sms/spec/` | the two specification files: extraction patterns and the spending taxonomy. Single source of truth for both languages. |
| `ghana-sms/tools/` | the Python reference implementation and the whole evaluation pipeline (ingest, corpus generation, metrics, taxonomy, trainer, Kotlin code generation). |
| `ghana-sms/corpus/` | `raw/` (git-ignored, unredacted captures) and `gold/` (redacted, annotated JSONL). |
| `ghana-sms/results/` | generated metric tables and failure dumps - paste-ready for the paper, never hand-edited. |
| `ghana-sms/model/` | the exported on-device classifier artifact (`ghana_category_model_v1.tsv`) plus its metadata. |
| `ghana-sms/paper/` | methods, results, related work, limitations and privacy write-ups. |

## One-command reproduction

```bash
cd research/ghana-sms
bash scripts/run_all.sh                  # evaluate + taxonomy + train + sync + drift check
bash scripts/run_all.sh --with-seed-corpus   # regenerate the placeholder corpus first
```

With a JDK available (not in the study's sandbox):

```bash
./init.sh parser                         # or: ./gradlew :parser-core:jvmTest
./scripts/update-supported-banks.sh      # after adding parsers, regenerate docs catalogues
```

## Status

- **Objective 1 (message taxonomy)** - method implemented (`tools/taxonomy.py`), numbers are
  placeholders until real captures are ingested. See `ghana-sms/paper/RESULTS.md`.
- **Objective 2 (regex extraction module)** - implemented twice (Python reference + generated
  Kotlin patterns), measured with per-field P/R/F1 and a three-arm ablation.
- **Objective 3 (on-device classifier)** - implemented: rule tier + tiny linear model with an
  arbitration policy, 189 KB artifact, no runtime dependencies, per-class and ablation tables
  generated. Tier 2 (on-device LLM) is specified but not measured.
- **Data** - `tools/ingest.py` is ready for the study's own exports; the corpus currently in the
  repo is synthetic and labelled as such everywhere it appears.

Do not quote a number from `results/` in the paper without its caveat: on a synthetic corpus,
per-field F1 of 1.000 measures generator-engine agreement, not real-world accuracy.
