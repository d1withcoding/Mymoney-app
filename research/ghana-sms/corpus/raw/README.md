# corpus/raw - drop zone for unredacted captures (git-ignored)

Put the SMS export here (`.csv`, `.tsv`, `.xml` from SMS Backup & Restore, or one message per line
in `.txt`) and run `tools/ingest.py`. Redaction happens on the way in, so after ingest this
directory holds the only copy of the unredacted text - which is why it is outside the repository:

- `corpus/raw/**` is git-ignored; nothing here can be staged by `git add -A`.
- `tools/sync_kotlin.py` refuses to copy any file named `*corpus.jsonl` or `*labeled.csv` into
  `parser-core/src/test/resources/`, because a test resource ships with the source.
- the redaction audit map (original -> replacement per span) is not written here. Pass
  `--audit-map` only if you have somewhere private for it, and never commit it: it is a
  re-identification key.

Study hygiene: keep the participant export out of cloud-synced folders, delete it once
`results/ingest-report/` reconciles with the expected row counts, and note the deletion date in
`../../paper/PRIVACY-ETHICS.md`.
