# ingest-smoke - a distributable example of every input shape

Six invented messages, in the three formats `tools/ingest.py` reads. Nothing here is a real
person, number or transaction, so the folder can be committed: it exists to (a) be the runnable
example in the docs, (b) let `python3 tools/ingest.py --in corpus/public/ingest-smoke/sample.xml
--dry-run` be copy-pasted, and (c) show what the ingest path is *supposed* to do to a capture.

| file | input shape | what it exercises |
|---|---|---|
| `sample.csv` | arbitrary header names (`ref,from,message,when,label,cat`) | alias mapping, `--map` overrides, dedup on the repeated `r1`/`r5` pair, a non-transaction control (`r4`), and a `label` column whose values are *not* categories -> reported as unmapped instead of silently poisoning Objective 3 |
| `sample.xml` | Android "SMS Backup & Restore" `<sms address= date= type= body=>` | epoch-millis to ISO-8601, the `type="2"` copy of an alert, a bare short code as sender (`3640`) |
| `sample.txt` | pasted messages, `SENDER: ` prefix | two-line and one-line sender forms, and a message the parser must route from body markers because the sender is not on the allow-list |

Expected output of the dry run (row counts, `dropped 1 duplicate(s)`, and the
`WARNING: category column values not in the taxonomy` line) is the point: it is the report an author
reads *before* trusting an ingest.

What redaction does to these six messages - and the invariants worth checking on any export:

```
0271234567            -> 027XXXXXXX        (personal MSISDNs never survive; the prefix does)
+233 24 123 4567      -> 024XXXXXXX
ANOTHER PARTY             -> a pseudonym         (deterministic: same name -> same pseudonym)
ECG / ELECTRICITY BILL -> kept              (businesses and message vocabulary are signal)
Transaction ID: 76712833868 -> Transaction ID: 7671XXXXXXX
Account: 0301123456   -> Account XXXXXXX456        (last three kept for context; account
                                                        masking runs *before* MSISDN masking,
                                                        because a 10-digit bill account is
                                                        indistinguishable from a phone number)
```

If a real export ever prints a full 10-digit number or a recognisable surname in a dry run, that is
a redaction bug: stop, fix `tools/ghana_parse/redact.py`, and re-run before writing anything.

Reproduce:

```bash
python3 tools/ingest.py --in research/ghana-sms/corpus/public/ingest-smoke/sample.xml \
    --name smoke --consent author_own --auto-gold --dry-run
```
