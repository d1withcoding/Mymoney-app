# Privacy and research-ethics notes

Two documents in one, because in this study they are the same artefact: the corpus is built from
people's message inbox, and the app under study exists *because* people do not want that inbox sent
to a server. Code references are to this repository at commit `c512c7c`; re-verify them before
submission, since they are the part a reviewer can actually check.

## 1. What the app guarantees architecturally (and what it does not)

**Guaranteed by construction** (readable in the code):
- transaction parsing runs on the device (`OptimizedSmsReaderWorker`); no parse result is sent to a
  server, and there is no account or user id;
- a parsed transaction persists only extracted fields: `SharedTransactionEntity` / the app's
  transaction entity have no raw-text column, and `ParsedTransaction.smsBody` is consumed on the
  device to compute `generateTransactionId()` = `md5(sender | amount(scale 2) | md5(smsBody)[:16])`
  - a hash, so dedup across Broadcast/ContentProvider timestamp sources works without storing text;
- the on-device classifier used in this study is a 189 KB text weight table loaded locally
  (`model/ghana_category_model_v1.tsv`), with no network call and no download.

**Not guaranteed, and the paper must not claim it**:
- *message authenticity* (section 4);
- *confidentiality of backups*: `BackupExporter` puts `unrecognizedSms` in `BackupData` as
  `List<UnrecognizedSmsEntity>`, and that entity is `@Serializable` with `sms_body` - so wherever raw
  text is retained, it is retained in backups too. There is no encryption code in
  `data/backup/` (no `Cipher`, `AES` or `PBKDF2` in that package); whatever protects a backup file is
  the destination's own protection. State that as a design risk, verify it against the current
  release before publishing it as a criticism, and note that this study's Ghana parsers do not
  change it (section 3 explains why Ghana traffic is in fact *less* exposed);
- *at-rest privacy of the whole inbox*: the app needs `READ_SMS` to work, so the permission it
  consumes is broader than the messages it uses; a personal-finance app that reads all SMS is a
  more attractive target than one that reads none.

## 2. Retention: the part that is easy to miss

`parseSms()` in `OptimizedSmsReaderWorker` decides what happens to a message no parser handled:

```
sender ends with "-P" or "-G"                 -> discard (promotional / service DLT senders)
no parser for the sender, ends with "-T"/"-S"  -> store raw body in unrecognized_sms
no parser for the sender, other naming         -> discard
parser present, parse() returns null           -> discard
```

Two consequences that belong in the paper:

1. **The DLT suffix rule is India-shaped.** `unrecognized_sms` exists to collect transactional
   messages from senders registered under India's DLT regime, whose short codes end in `-T`/`-S`.
   Ghanaian wallets do not use that convention (`MTN-MOMO`, `Telecel Cash`, `ATM`, plain short
   codes), so for Ghana the raw-text retention path is *not taken*. That is a privacy win - and a
   capability gap: a Telecel message the parser fails on is discarded, so there is no review queue
   and no way to recover the transaction, which is precisely the case where a user needs the app
   most. The Ghana parsers therefore leave `accountLast4` null and set `isMobileWallet` (the wallet
   *is* the account), but the app-level gap above is a recommendation, not something this study
   changed.
2. **If a review queue for wallet messages is ever added, it should store a redacted body.** The
   `redact_body()` function in this kit already produces the pseudonymised text plus an audit map;
   storing the redacted form and keeping the audit map out of the database would give the same
   diagnostic value with a much smaller exposure. `UnrecognizedSmsEntity` has `is_deleted` but no
   expiry, and no pruning job was found for it - so any retained body is retained indefinitely and
   is currently backed up (section 1).

## 3. What this study's own data handling does

Implemented, not promised:

- **Redaction at ingest, never after.** `tools/ingest.py` runs `redact_body()` on the way in
  (`tools/ghana_parse/redact.py`); the analysis tier keeps business and paybill names (they are the
  study's subject and are public) while personal names become deterministic pseudonyms, MSISDNs
  become `0` + network prefix + `X`s, transaction ids keep 4 characters and bill accounts keep their
  last 3. Gold labels pass through the same function, so the corpus stays self-consistent.
- **Raw captures stay out of the repository.** `corpus/raw/**` is git-ignored (verified with
  `git check-ignore`), and `tools/sync_kotlin.py` refuses to copy any file named `*corpus.jsonl` or
  `*labeled.csv` into `parser-core/src/test/resources/`, because test resources ship with the source.
  The Kotlin fixtures that *do* get generated come from the synthetic placeholder corpus, and the
  hand-written parser tests use placeholder names with obviously dummy sequential MSISDNs plus an
  inline instruction never to paste a real message into a test (repo constraint #4).
- **The audit map is a re-identification key** and is written only if `--audit-map` is passed to an
  explicit private path; it is not part of the corpus and never committed.
- **Two publication tiers.** `--tier publish` additionally masks names and ids and nulls
  `counterparty_phone`, and the paper must then report recall per tier, because the publish tier
  changes what a field can be (a documented consequence, not a defect).
- **Every reported metric can be filtered to consented rows.** Each record carries `consent`
  (`participant_consent` | `author_own` | `synthetic` | `unrecorded`) and `provenance`;
  `results/ingest-report/` counts rows read, mapped, redacted and refused, so an exclusion has to be
  a decision, not an accident.

Pseudonymisation is not anonymity: a `(amount, timestamp, operator)` triple plus a balance line is
close to unique inside a small population, which is exactly why the analysis tier of the corpus is
also treated as sensitive material and kept out of Git.

## 4. Threat model the paper should state explicitly

- **Spoofed alerts.** Published Ghanaian fraud write-ups document fake alerts whose wording and
  sender differ only slightly from the operator's (missing or placeholder transaction ids, implausible
  balance lines). A parser improves on the human case - it can require a plausible id and a status -
  but it cannot authenticate a text message. This kit therefore measures refusals on 90
  non-transaction controls (0.000 false-positive rate on the placeholder corpus) and reports the
  status/id heuristics, and it does **not** claim fraud detection.
- **Permission scope.** Anything the app can read it can leak; the design argument is not that the
  app reads little, but that it *keeps* little and *sends* nothing.
- **Device loss and backups** are the realistic disclosure paths for a privacy-first app, which is
  why section 1 flags the backup surface instead of resting on "on-device = private".
- **Regulatory.** Ghana's Data Protection Act 843 and the NCA's consumer warnings are the right
  national frame; the Play Store's `READ_SMS` policy governs distribution. Quote each with an access
  date and have the institution's ethics procedure confirm the consent wording - see next section.

## 5. For the human-subjects part of the study (to be completed by the author)

The kit can enforce data handling; it cannot obtain approval. Before real captures are used as the
evaluation corpus, the paper needs:

1. **Review**: approval or exemption from the relevant institutional review body (university IRB or
   the national ethics committee that covers the institution - confirm which applies; do not name one
   in the paper until it is on file).
2. **Participant information + consent**: what is collected (transactional SMS only, plus the
   senders/timeframes needed to identify them), that redaction happens before any human reads a
   message, that withdrawal means deleting the raw export and re-deriving, and that the app's own
   "own messages" path (author's device) is described separately from third-party data.
3. **Recording the consent in the data**: `--consent <column>` is required by `tools/ingest.py`
   precisely so an unrecorded row cannot enter the numbers unnoticed. Rows marked `unrecorded`
   should be excluded and the exclusion count reported.
4. **Second annotator** for a subsample, with a disagreement rate, so the gold labels are not one
   person's reading of the specification (see `LIMITATIONS.md` 12).
5. **Deletion after analysis**, with the date stated in the paper's data-availability section, and
   a statement of what is released instead: the schema, the harness, the generated reports, and -
   if the ethics approval allows it - a synthetic corpus in the style of
   `tools/make_seed_corpus.py`, which is distributable precisely because no real message shaped it.

## 6. The one-sentence version for the abstract

On-device parsing reduces the privacy cost of automated personal-finance tracking to a retention
question rather than a transmission question - and this study shows both halves: the extraction and
classification run with no network path and persist only derived fields, while the residual risks
move to the places engineers stop looking: raw text kept for a review queue, backups that inherit it,
and a message the parser cannot authenticate.
