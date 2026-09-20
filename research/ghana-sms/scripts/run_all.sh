#!/usr/bin/env bash
# The whole research pipeline, in the order that has to be respected.
#
#   spec/ghana_patterns.json  (single source of truth for extraction)
#     -> make_seed_corpus.py  (placeholder corpus, only if you have no real captures yet)
#     -> evaluate.py          (Objective 2 metrics + failure dump)
#     -> taxonomy.py          (Objective 1 structural measurements)
#     -> train.py             (Objective 3 classifier + parity fixtures)
#     -> sync_kotlin.py       (Kotlin patterns, model artifact, test fixtures)
#     -> sync_kotlin.py --check (drift gate: the repo must be regenerable from the spec)
#
# Then, with a JDK present (this sandbox has none):
#   ./init.sh parser          # or: ./gradlew :parser-core:jvmTest
#   ./scripts/update-supported-banks.sh   # regenerates docs/README/web catalogues
#
set -euo pipefail
cd "$(dirname "$0")/.."
TOOLS=tools

if [ "${1:-}" = "--with-seed-corpus" ]; then
  # 40 per shape is what the committed results were produced with (1 200 transactions + 90
  # controls); keep the two in step or run_all.sh quietly re-bases every table on a smaller corpus.
  python3 "$TOOLS/make_seed_corpus.py" --per-shape 40 --seed 11
fi

# Prefer the user's real corpus when it exists; everything below runs on whichever file is there.
CORPUS="corpus/gold/corpus.jsonl"
if [ ! -f "$CORPUS" ]; then
  CORPUS="corpus/gold/seed-corpus.jsonl"
  echo "NOTE: using the synthetic seed corpus. Real captures go to corpus/gold/corpus.jsonl." >&2
fi

# Privacy invariants first: if redaction regressed, nothing below this line should be trusted
# enough to spend time on.
python3 "$TOOLS/test_redaction.py"

python3 "$TOOLS/evaluate.py" --corpus "$CORPUS"
python3 "$TOOLS/taxonomy.py" --corpus "$CORPUS"
python3 "$TOOLS/train.py" --corpus "$CORPUS"
python3 "$TOOLS/sync_kotlin.py"
python3 "$TOOLS/sync_kotlin.py" --check
# The paper's tables are assembled from the reports above, never retyped.
python3 "$TOOLS/make_results_doc.py"
echo
echo "Done. Results in results/, Kotlin assets regenerated in parser-core/."
echo "After a spec change also run: ./gradlew :parser-core:jvmTest"
