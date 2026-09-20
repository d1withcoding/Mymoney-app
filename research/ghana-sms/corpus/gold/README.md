# corpus/gold - labelled records consumed by the harness

One JSONL record per message (schema: `../CORPUS_SCHEMA.md`). The harness prefers `corpus.jsonl`
and falls back to `seed-corpus.jsonl`.

| file | status |
|---|---|
| `seed-corpus.jsonl` | **synthetic placeholder**, from `tools/make_seed_corpus.py`: 40 message shapes x 3 operators x rendering variants (1 200 transactions) + 90 non-transaction controls. No real person, number or merchant: every name and MSISDN is generated. It exists so the pipeline is runnable and the Kotlin fixtures are non-empty before the study corpus does. |
| `corpus.jsonl` | the real thing: `tools/ingest.py` output, redacted at ingest, human-annotated. Absent until the study captures land. |

Do not commit `corpus.jsonl` unless every record is `consent: synthetic` or a public example the
participant agreed to publish. The safe default is to commit the *reports* (counts, shapes, label
distributions) and keep the corpus out of the repository.

Regenerate the placeholder: `python3 tools/make_seed_corpus.py --per-shape 40 --seed 11`
