# Methods

Everything below is implemented; nothing here is aspirational. Each subsection names the file that
is the authority for it, because the paper's claims are only as good as the mapping from claim to
code.

## 0. Study design

Three objectives, three artifacts, one loop:

| objective | artifact | measured by |
|---|---|---|
| O1 - structure and content of Ghana MoMo SMS | `results/taxonomy.md` | counts on the corpus itself |
| O2 - regex extraction module | `spec/ghana_patterns.json` + `parser-core/.../ghana/` + `bank/{MtnMoMo,TelecelCash,AirtelTigoMoney}Parser.kt` | `tools/evaluate.py` (three ablation arms) |
| O3 - on-device category classifier | `tools/train.py` + `model/ghana_category_model_v1.tsv` + `GhanaCategoryClassifier.kt` | held-out test split, four arms |

The parser is *specified once* in JSON and compiled twice: by Python (evaluation) and by
`tools/sync_kotlin.py` (the shipping Kotlin + test fixtures). The published numbers and the code in
the app are therefore the same program, and `python3 tools/sync_kotlin.py --check` fails the build
if they diverge. Corpus records that the harness reads also become Kotlin test fixtures, so
`:parser-core:jvmTest` re-runs the evaluation on every build.

## 1. Corpus construction (O1 input)

- **Unit of analysis**: one SMS message (`Record` in `tools/ghana_parse/corpus.py`), never a
  "transaction", because a transfer produces two messages and the app must decide what to import.
- **Acquisition**: `tools/ingest.py` accepts CSV/TSV (one header row), Android SMS-backup XML, and
  plain text. Column mapping is explicit (`--map 'body=text,sender=address,...'`) and consent is a
  required column name (`--consent`), so an unlabelled corpus cannot be silently scored.
- **Redaction at ingest** (`tools/ghana_parse/redact.py`, two tiers, `analysis` by default):
  personal name runs become deterministic pseudonyms (same person -> same pseudonym within a
  corpus, so coreference survives); MSISDNs become `0` + network prefix + `X`s (the prefix is
  structural evidence for O1); transaction ids keep their first 4 characters; bill accounts keep
  their last 3; business names, paybill numbers, amounts, fees, levies and balances are kept,
  because they are the subject of measurement and are not identifying on their own. Gold labels
  pass through the *same* function, which is what keeps a redacted corpus internally consistent.
- **Placeholder corpus**: `tools/make_seed_corpus.py` renders 40 message shapes per operator across
  four rendering variants plus 90 non-transaction controls. Shapes are not just the 30 templates:
  half the shapes (`no_template_*`) deliberately express the same event with different label words,
  extra sentences and shuffled order. Without them the study would measure template recall rather
  than extraction.
- **Annotation**: `gold` per field with three-way semantics - absent key = not annotated (excluded
  from P and R), explicit null = "the message contains no such value" (a prediction is a false
  positive), string = expected normalised form. `gold_source` records whether a block is
  `human_verified` or an `engine_draft_needs_review` produced by `--auto-gold`; draft blocks must
  be excluded from reported metrics, since scoring the engine against its own output measures
  nothing. See `corpus/CORPUS_SCHEMA.md`.

## 2. Normalisation

`GhanaNormalizer` (Kotlin) and `tools/ghana_parse/normalize.py` implement the same eleven steps,
byte-comparable because the corpus fixtures test both: currency tokens (`GHS`, `GHc`, `GH¢`, `c/`,
`M`/`GH`), thousands separators, whitespace (Unicode, explicitly enumerated because Java's `\s` is
ASCII-only), typographic punctuation, fullwidth digits, zero-width characters, emoji delimiters,
`&` -> `AND`, sentence compression, and *reversible* truncation of boilerplate. Amounts are
scale-2 decimals; dates are day-first unless a month name or ISO form disambiguates; MSISDNs
normalise to local `0XXXXXXXXX`.

## 3. Specification (`spec/ghana_patterns.json`, v1.1.0)

The specification is data, not code: 16 fields with normalisation hints, a 14-entry kind ->
transaction_type map and kind -> direction map, 5 skip rules, 4 status rules, 2 direction rules, 9
boilerplate patterns, 46 per-field fallback pattern groups (31 shared across operators + 15
operator-specific: 6 MTN, 5 Telecel, 4 AirtelTigo) each with a priority, and 30 whole-message
templates (12 MTN, 9 Telecel Cash, 9 AirtelTigo). The file carries its own honesty marker -
`"status": "DRAFT - regexes ... MUST be re-fitted against the author's collected corpus"` - and a
spec version (`1.1.0`) that the generated Kotlin header repeats.
Pattern bodies are written with named tokens (`{AMT}`, `{NAME}`, `{NDELIM_HARD}`, `{TRX_ID}` ...)
that `spec.py` substitutes *identically* in both languages; `{NDELIM_HARD}` exists because a lazy
name pattern before optional groups truncates, and an apostrophe-insensitive variant exists because
name-continuation words must never be swallowed by a name.

Operator routing is sender-based (`MTN-MOMO`, `MTNSMS`, `Mobile Money`, `ATM` ...), and the *bare*
sender `ATM` is intentionally **not** routed: it is AirtelTigo's short code, but `ATM` is also an
abbreviation for automated teller machine and cash machines use the same wording. A mis-routed
message loses its routing benefit but cannot gain a wrong amount, because amount extraction is
sender-independent.

## 4. Extraction cascade (O2)

```
tidy -> skip rules -> boilerplate strip
  -> per template of the routed operator: fullmatch on the stripped body, score
       4*amount + 3*direction + 3*tx_kind + 2*(balance,date,counterparty_name,paybill_name)
       + 1*(each other field present); highest score wins, ties by lower template priority
  -> accepted = best template produced an amount AND a direction
  -> otherwise per-field fallback patterns (priority = specificity), with
       label-context gating, "unlabelled pattern must start in the first 40% of the message",
       paybill/counterparty mutual exclusion, id digit/letter + sender hints,
       balance requiring a label, date-conflict flagging
  -> normalise every field -> tx_kind -> (transaction_type, direction) -> status
  -> confidence, then accept iff confidence >= 0.45
```

Confidence is a *stated* heuristic, not a learned probability, and the paper must present it that
way. Field yield is `min(1, present_fields / 7)` over the 16 scored fields:

```
template arm : 0.55 + 0.30 * yield + 0.05 * (routed by sender) + 0.05 * (status SUCCESS)   cap 0.95
fallback arm : 0.25 + 0.30 * yield + 0.05 * (routed by sender)                              cap 0.55
```

Capping the fallback tier below 0.6 is what makes a review threshold meaningful rather than a
second guessing layer: an app can auto-accept above 0.75 (template-shaped evidence) and queue the
rest, because the tiers are separated by construction, not by tuning. `MIN_ACCEPT_CONFIDENCE =
0.45` is applied at the boundary that creates a `ParsedTransaction`. Template selection itself
scores fields at 4 (amount), 3 (direction, tx_kind), 2 (balance, date, counterparty_name,
paybill_name) and 1 (everything else), so an almost-full template loses to one that captured the
amount. The repo's existing `Constants.Parsing.CONFIDENCE_PATTERN_BASED = 0.7f` stays a constant
for bank parsers; the Ghana parsers report their computed value instead.

**Refusal is a first-class output.** A non-transaction control set (OTP, promo, E-levy reminder,
debt request, statement, agent commission, fraud alert, tariff notice, network notice) is scored for
false positives, and the rejection reason is kept (`no_amount_found`, `below_confidence_floor`,
`skip:<rule>`, `not_a_transaction`). On a wallet app a spurious parse is worse than a miss: it
writes a transaction the user never made.

## 5. Category cascade (O3)

- **Tier 0 - rules** (`spec/ghana_categories.json`): paybill-number prefixes, merchant-name
  fragments, and keyword lists, each with a *strength*: `structural` (the message states a fact -
  top-up, cash-out, transfer, a biller label, `for ELECTRICITY BILL`) or `lexicon` (a word match),
  with `unique_lexicon` when exactly one label's vocabulary matched. The taxonomy is deliberately
  wider than the app's shipped categories: `cash_agent`, `rent_housing`, `p2p_transfer` exist in
  the study because Ghanaian MoMo traffic needs them, and the Kotlin classifier maps them to
  in-app categories (`isExact = false` -> "Others") rather than dropping them.
- **Tier 1 - a 189 KB linear model**: TF-IDF over unigrams, bigrams, within-word trigrams,
  digit-stripped words, merchant tokens, operator/kind/date-bucket/channel/status symbols, paybill
  and counterparty-name tokens, and the amount bucket; `(1 + ln tf) * idf`, L2-normalised over the
  model vocabulary; one-vs-rest logistic regression trained by hand-written SGD (the harness had
  no third-party packages, which is also why the artifact is auditable line by line). 3 461 vocab
  entries, 6 000 weight rows, `I` rows carrying idf so that a truncated vocabulary still produces
  identical scores in both languages.
- **Arbitration** (`arbitrate_with` in `tools/train.py`, `GhanaCategoryRules.arbitrate` in Kotlin,
  one policy in two languages):

  | rule evidence | model | decision |
  |---|---|---|
  | any rule, `strict` mode | yes | rule wins (the ablation arm; it shows the cost of the policy) |
  | structural fact (derived from the parsed kind) | not consulted | rule wins |
  | lexicon hit that also matched merchant tokens | yes | model wins iff prob >= 0.50 and it disagrees |
  | exactly one label vocabulary matched | yes | model wins iff prob >= 0.50 and it disagrees |
  | weak rule (the direction prior) | yes | model wins iff prob >= 0.45 and it disagrees |
  | ambiguous lexicon (several labels matched, none unique) | yes | model decides; reason says `rules ambiguous` |
  | no rule | yes | model label, with its probability as the reason |
  | no rule | no features fired | abstain -> `other` in-app |

  Tier strings are output as `rule:<why>` or `model:0.87 overruled lexicon '<why>'` so a user can
  see which layer decided, and both languages must produce the identical string.
- **Tier 2 (optional)**: the repo's LiteRT-LM Qwen 2.5 path (`LlmService`) is the intended third
  tier for the residual ~2% and for paraphrased messages. This study deliberately does **not**
  report an LLM number it did not measure; `classifier_metrics.md` states the upper bound
  argument only.

## 6. Metrics

Precision and recall per field under the three-way gold semantics of section 1 (an
unannotated field also produces an `unverifiable` count, which is reported: a name extracted from
an unlabelled message may be right and simply unverifiable, and that must not be counted as
correct). Coverage = accepted parses / transaction records; exact-every-field rate = records where
every *scored* field matches (a strict lower bound on full-record correctness); FP rate on
non-transaction controls; macro-F1 and per-class support for categories. No metric is averaged
across currencies (all amounts are GHS-tagged) and no metric mixes the three arms.

## 7. Reproduction

```bash
bash scripts/run_all.sh                      # corpus -> ingest report -> O1 -> O2 -> O3 -> fixtures
python3 tools/sync_kotlin.py && python3 tools/sync_kotlin.py --check
./gradlew :parser-core:jvmTest                # needs a JDK 21 + Android SDK; not run in this sandbox
./scripts/update-supported-banks.sh           # regenerates docs/supported-banks.json and README badges
```

`run_all.sh` is the unit of reproduction: one command regenerates every number in `RESULTS.md` from
the corpus that is in `corpus/gold/` at that moment.
