# Corpus schema (v1) and annotation protocol

The corpus is JSON Lines - one record per line - so a real capture set can be appended to,
audited and re-redacted without any tooling beyond the standard library. Canonical reader/writer:
`tools/ghana_parse/corpus.py`.

```jsonc
{
  "id": "p01-00042",                    // stable; prefix keeps participants separable
  "operator": "mtn_momo",               // mtn_momo | telecel_cash | airtel_tigo_money | unknown
  "sender": "MTN-MOMO",                 // as received; short codes are not personal data
  "body": "Payment made for GHS 50.00 to ADAKYEA ... ",   // REDACTED text, see tiers below
  "received_at": "2026-03-14T19:04:22+00:00",              // or null
  "source": "device_export",            // device_export | manual_capture | template | synthetic
  "provenance": "TEST_SUBJECT2.xml",    // which export this row came from
  "consent": "participant_consent",     // participant_consent | author_own | synthetic | unrecorded
  "is_transaction": true,               // false = non-transaction control (OTP, promo, request...)
  "category": "utilities",              // Objective-3 gold label, null when unlabelled
  "gold": { },                          // Objective-2 gold fields, see semantics below
  "notes": "masked number; fee absent"  // free text for the annotator
}
```

## `gold` semantics - the part that decides whether your metrics mean anything

Sixteen fields: `amount, fee, levy, balance, trx_id, reference, counterparty_name,
counterparty_phone, paybill_name, paybill_account, date, time, status, channel, tx_kind,
direction`.

* **key absent** -> "not annotated": excluded from precision *and* recall for that field.
* **key present, `null`** -> "this message contains no such value": a prediction here counts as a
  false positive. This is the only way to measure spurious extraction, and it is why
  `engine_draft_needs_review` records must not be reported as gold.
* **string value** -> the exact normalised form expected (`amount` scale-2 decimal string, `date`
  ISO `YYYY-MM-DD`, `time` `HH:MM`, `counterparty_phone` local `0XXXXXXXXX`, names normalised by
  `GhanaNormalizer`, `direction` `IN`/`OUT`, `status` `SUCCESS`/`FAILED`/`PENDING`).

Store normalised strings, not prose: the evaluator compares canonically (numbers by value, names
by `name_key`), so annotators do not have to know that `GH¢1,050.00` and `1050.00` are the same
amount - but they do have to write it the same way as everyone else.

`transaction_type` is not a gold field: it is derived from `tx_kind` through
`kind_to_transaction_type`, so an annotator labels what the message says, and the app's enum
mapping stays reviewable as data.

## Provenance rule for the placeholder corpus

`seed-corpus.jsonl` is drawn from a seeded PRNG (`--seed 11`) by `tools/make_seed_corpus.py`, which
reads no corpus, no log and no real message. Its MSISDNs, bill accounts and transaction ids are
therefore as invented as its surnames - and its numbers keep realistic *variation* on purpose: the
classifier's digit features and Objective 1's prefix cross-tab are measured on them, so collapsing
them to repeated digits (an intuitive privacy reflex) measurably moves both. What the rule actually
requires is one-directional: **nothing real is ever generated from, and nothing generated is a real
record.** If you point the generator at a capture set, you have left this file's contract.

## Redaction, applied on the way in - not afterwards

`tools/ghana_parse/redact.py`, run by `tools/ingest.py`:

| thing | analysis tier (default) | publish tier |
|---|---|---|
| personal name runs | replaced by a deterministic invented name (`sha256`-keyed, so the same person keeps the same pseudonym across messages) | replaced as well |
| business / merchant / paybill names | **kept** - they are the vocabulary Objective 3 needs, and they are public | masked |
| MSISDN | `0` + network prefix + `X`s (e.g. `024XXXXXXX`) | `0XXXXXXXXXX` |
| transaction id | first 4 characters kept (length/charset are structural evidence) | fully masked |
| paybill / bill account | last 3 digits kept | fully masked |
| amount, fee, levy, balance, dates | kept (they are the study's subject, and not identifying alone) | kept |

Two properties worth defending in the paper:

1. Gold labels are transformed by the *same* redaction function as the body, so a corpus stays
   self-consistent. `set_names_mode()` is how the two agree on what counts as a personal name.
2. `redact_body()` returns an audit map (original -> replacement per span). That map is a
   re-identification key: it is written to a private path only if you ask for it, and it is not
   part of the corpus. If a participant withdraws, you delete `corpus/raw/` and re-derive.

## Annotation protocol

1. **Ingest** with `--auto-gold`: the engine drafts the fields nobody supplied, and the record says
   so - `gold_source` is `absent` (no field gold), `engine_draft_needs_review` (drafted only),
   `mixed` (some human fields, some drafted) or `human_verified` (every field gold came from the
   author). The drafted field list is also appended to `notes` as `[auto_gold=amount,fee]`, so a
   reviewer can see provenance per field without parsing the gold block. `gold_source` describes
   *field* gold only: a category-only corpus is human-verified for Objective 3 and still has no
   Objective-2 labels, which is a normal thing to publish.
2. **Human review** of every record used for reported numbers - disagreements are exactly what
   Objective 2's error table is made of. Flip `gold_source` to `human_verified` per record; report
   how many records were verified and by whom (single annotator? double-coded? disagreement rate?).
3. **Categories** (`category`) are labelled against `spec/ghana_categories.json`. If a message
   belongs to none of the 15, use `other`; do not invent labels silently - the label set is part
   of the artifact.
4. **Controls**: keep the non-transaction messages you rejected (`is_transaction: false`) in the
   same file. A corpus of only successful transfers measures nothing about refusal behaviour,
   which is where a wallet app gets hurt by spoofed alerts.
5. **Deduplicate** on `dedupe_key` (the whitespace-normalised, lowercased body) before reporting:
   operators resend the same alert to both parties and a backup/restore duplicates messages.
6. **Split deterministically** with `split_records(..., seed=...)`, stratified by operator x kind,
   and report the sizes. The classifier's vocab/idf must come from the train split only (the
   trainer does this; a hand-rolled split that leaks will show up as suspiciously perfect recall).

## Consent and provenance fields

`consent` and `provenance` exist so the paper's ethics section can be *checked* rather than
asserted: every reported number can be filtered to `consent in (participant_consent, author_own)`
and the counts per provenance file are reproducible. `unrecorded` rows should be reported as
excluded, and the exclusion count stated.
