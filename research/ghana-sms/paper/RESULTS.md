# Results

> **Provenance of every number in this file.** They are the output of
> `bash research/ghana-sms/scripts/run_all.sh` run against `corpus/gold/seed-corpus.jsonl`,
> the *synthetic placeholder* corpus (schema and rationale: `corpus/CORPUS_SCHEMA.md`). Until
> the study's own captures replace it, these measure the specification's internal consistency
> and generator-vs-engine agreement - **not** accuracy on real Ghanaian traffic. Every table is
> copied verbatim out of `results/*.md` by `tools/make_results_doc.py`; regenerate it with
> `python3 tools/make_results_doc.py` after any corpus or spec change rather than editing here.

---

## 1. Corpus structure (Objective 1)

Measured by `tools/taxonomy.py`: records: 1290 (transactions 1200, non-transactions 90) - 40 message shapes per operator
across four rendering variants, plus non-transaction controls.

### 1.1 Format families

Reported at two granularities, because the gap between them *is* the argument for a
field-oriented parser: a whole-message-template parser needs one template per skeleton while
per-field patterns absorb the variation between skeletons.

| operator | n | format families | length profile |
|---|---|---|---|
| airtel_tigo_money | 400 | **274** coarse / 282 fine; top-12 coarse cover **14.2%** | median 137.0 chars, max 239; over 160 chars in 89 messages (22.2%) |
| mtn_momo | 400 | **327** coarse / 333 fine; top-12 coarse cover **12.8%** | median 141.0 chars, max 244; over 160 chars in 101 messages (25.2%) |
| telecel_cash | 400 | **288** coarse / 302 fine; top-12 coarse cover **15.5%** | median 143.0 chars, max 260; over 160 chars in 116 messages (29.0%) |

Two readings matter for the paper. First, ~275-330 coarse families for 400 messages per
operator means the message population is *not* a small template set, even in synthetic data
rendered from only 40 shapes: the multiplication comes from label wording, sentence order and
extra clauses, which is exactly what a per-field parser is for. Second, 22-29 % of messages
exceed 160 characters, i.e. are candidates for concatenated-multipart delivery on a real
device - the reason the normalizer treats newline rejoins and split-message fragments as
first-class input rather than as noise to be discarded. Nothing in `tools/ingest.py` or the
engine truncates a body - only the failure report slices for display - because a truncated
long message loses exactly the trailing balance and fee clauses Objective 2 measures.

### 1.2 Sender IDs (the routing input)

| operator | sender | messages |
|---|---|---|
| mtn_momo | `MTN-MOMO` | 400 |
| telecel_cash | `TelecelCash` | 400 |
| airtel_tigo_money | `ATMoney` | 400 |
| unknown | `MTN` | 90 |

### 1.3 Counterparty MSISDN prefix x operator

| prefix | airtel_tigo_money | mtn_momo | telecel_cash |
|---|---|---|---|
| 020 | 24 | 18 | 14 |
| 024 | 21 | 21 | 12 |
| 025 | 17 | 15 | 13 |
| 027 | 17 | 10 | 12 |
| 050 | 14 | 11 | 8 |
| 054 | 11 | 12 | 10 |
| 055 | 14 | 13 | 16 |
| 056 | 22 | 15 | 17 |
| 057 | 10 | 14 | 18 |

### 1.4 Non-transaction traffic a parser must refuse

| class | messages |
|---|---|
| credential / security | 18 |
| promotion / marketing | 18 |
| payment request | 18 |
| account lifecycle | 18 |
| other non-transaction | 18 |

Per-skeleton inventories, money and date-shape histograms and transaction-id geometry:
`results/taxonomy.md`.

---

## 2. Regex extraction (Objective 2)

**template_only** = precision-biased ablation, **fallback_only** = coverage-biased ablation.

### 2.1 Headline (hybrid = the shipped configuration)

- accepted parses: **1200** / 1200 = coverage **1.000**
- false positives on non-transaction controls: 0 / 90 = **0.000**
- records where *every* scored field is exact: 781 (0.651 of accepted)
- messages with a day/month-ambiguous date string: 60

### 2.2 Per field

| field | P | R | F1 | TP | FP | FN | TN | unverifiable |
|---|---|---|---|---|---|---|---|---|
| **amount** | 1.000 | 1.000 | 1.000 | 1200 | 0 | 0 | 0 | 0 |
| **date** | 1.000 | 1.000 | 1.000 | 311 | 0 | 0 | 889 | 0 |
| **direction** | 0.999 | 0.999 | 0.999 | 1199 | 1 | 1 | 0 | 0 |
| **merchant** | 1.000 | 0.977 | 0.988 | 469 | 0 | 11 | 0 | 14 |
| **balance** | 1.000 | 1.000 | 1.000 | 1044 | 0 | 0 | 156 | 0 |
| fee | 1.000 | 1.000 | 1.000 | 254 | 0 | 0 | 946 | 0 |
| levy | 1.000 | 1.000 | 1.000 | 95 | 0 | 0 | 1105 | 0 |
| trx_id | 0.993 | 1.000 | 0.997 | 573 | 4 | 0 | 623 | 0 |
| reference | 0.775 | 0.775 | 0.775 | 31 | 9 | 9 | 1160 | 0 |
| counterparty_name | 0.940 | 0.979 | 0.959 | 235 | 15 | 5 | 945 | 0 |
| counterparty_phone | 1.000 | 0.925 | 0.961 | 369 | 0 | 30 | 801 | 0 |
| paybill_name | 1.000 | 0.971 | 0.985 | 233 | 0 | 7 | 960 | 0 |
| paybill_account | 1.000 | 1.000 | 1.000 | 196 | 0 | 0 | 1004 | 0 |
| time | 1.000 | 1.000 | 1.000 | 311 | 0 | 0 | 889 | 0 |
| status | 0.998 | 0.912 | 0.953 | 1095 | 2 | 105 | 0 | 0 |
| channel | 0.744 | 0.652 | 0.695 | 58 | 20 | 31 | 1091 | 0 |
| tx_kind | 0.883 | 0.770 | 0.823 | 924 | 122 | 276 | 0 | 0 |
| transaction_type | 0.999 | 0.871 | 0.930 | 1045 | 1 | 155 | 0 | 0 |

`merchant` is one logical field: the counterparty name or the paybill name, whichever the
message names. The `unverifiable` column counts predictions against records where that field
was never annotated (excluded from precision *and* recall), which is why merchant precision is
exactly 1.000 while 14 name extractions remain unconfirmed.

### 2.3 Per operator (headline fields)

| operator | field | P | R | F1 | support |
|---|---|---|---|---|---|
| airtel_tigo_money | amount | 1.000 | 1.000 | 1.000 | 400 |
| airtel_tigo_money | balance | 1.000 | 1.000 | 1.000 | 342 |
| airtel_tigo_money | date | 1.000 | 1.000 | 1.000 | 55 |
| airtel_tigo_money | direction | 1.000 | 1.000 | 1.000 | 400 |
| airtel_tigo_money | merchant | 1.000 | 0.994 | 0.997 | 160 |
| mtn_momo | amount | 1.000 | 1.000 | 1.000 | 400 |
| mtn_momo | balance | 1.000 | 1.000 | 1.000 | 348 |
| mtn_momo | date | 1.000 | 1.000 | 1.000 | 145 |
| mtn_momo | direction | 0.998 | 0.998 | 0.998 | 400 |
| mtn_momo | merchant | 1.000 | 0.994 | 0.997 | 160 |
| telecel_cash | amount | 1.000 | 1.000 | 1.000 | 400 |
| telecel_cash | balance | 1.000 | 1.000 | 1.000 | 354 |
| telecel_cash | date | 1.000 | 1.000 | 1.000 | 111 |
| telecel_cash | direction | 1.000 | 1.000 | 1.000 | 400 |
| telecel_cash | merchant | 1.000 | 0.944 | 0.971 | 160 |

### 2.4 Confidence, and what the app would auto-accept


n = 1200, min 0.471, median 0.55, mean 0.728, max 0.95; accepted-but-below-the-0.45 floor: **0** (the app would refuse these even though the engine produced all required fields)
clearing the 0.75 auto-accept bar: **589** (0.491); everything below it is a one-tap review, not a correction, because every field the engine did extract is already filled in.

| confidence band | accepted parses |
|---|---|
| 0.45 - 0.50 | 12 |
| 0.50 - 0.55 | 157 |
| 0.55 - 0.60 | 442 |
| 0.75 - 0.80 | 1 |
| 0.80 - 0.85 | 7 |
| 0.85 - 0.90 | 83 |
| 0.90 - 0.95 | 498 |

The auto-accept share equals the template-covered share *by construction*: the fallback tier
is capped at 0.55 and the bar is 0.75. So the pipeline writes a transaction with no review for
**0.491 of accepted parses** and shows the rest as a one-tap confirmation with
every extracted field pre-filled. Note also `accepted-but-below-the-0.45 floor: 0` - on this
corpus the floor never rejects anything, so coverage 1.000 is not an artefact of a lenient
threshold. The placeholder corpus is deliberately sized so that about half of records match no
whole-message template, which makes the 0.491 figure a design parameter here rather than a
prediction about real traffic.

### 2.5 Template coverage (top 10 of 30)

| template | accepted parses |
|---|---|
| `(no template)` | 611 |
| `mtn.paybill.payment` | 78 |
| `at.merchant.payment` | 78 |
| `tc.paybill.payment` | 75 |
| `mtn.withdrawal.agent` | 40 |
| `mtn.airtime.purchase` | 40 |
| `mtn.data_bundle.purchase` | 40 |
| `tc.airtime.purchase` | 40 |
| `at.airtime.purchase` | 40 |
| `at.withdrawal.cash_out` | 38 |

### 2.6 Ablations: why extraction is two stages

| arm | accepted / coverage | exact on every scored field | reading |
|---|---|---|---|
| hybrid (shipped) | 1200 / 1.000 | 781 (0.651) | templates supply the structure, per-field patterns fill what the template missed |
| template_only | 589 / 0.491 | 136 (0.231) | precision-biased - what the specification alone covers. Fields absent from the matched template collapse: fee recall 0.076, date 0.105, time and channel 0.000 |
| fallback_only | 1200 / 1.000 | 685 (0.571) | coverage-biased - same coverage, 8 points lower exactness; transaction_type F1 0.843 and `reference` precision 0.775, because the net has no template context to reject lookalikes |

The ablations are the substantive extraction result: a template parser covers under half of
the message population, and a pure per-field net matches coverage but loses precision on the
fields whose identity depends on what the message as a whole is about. The hybrid is what both
arms need, and it is 1.000 coverage *with* 0.000 false positives on the 90 controls.

Per-field rows for both ablations: `results/extraction_metrics.md`. Individual misses, with the
message that produced them: `results/extraction_failures.md`.

### 2.7 Where it is weakest (report-worthy, not a bug list)

| field | F1 | diagnosis |
|---|---|---|
| channel | 0.695 | the corpus states the channel in prose ('via MoMo', 'from wallet'); the spec recognises a subset of the phrasings - 20 FP / 31 FN |
| tx_kind | 0.823 | kinds are inferred, so an ambiguous verb chain yields a neighbouring kind (122 FP / 276 FN); the type mapping absorbs most of it (transaction_type 0.930) |
| status | 0.953 | recall 0.912 (105 FN): absence of status wording defaults to SUCCESS, correct on success traffic, untested against a failure corpus |
| reference | 0.775 | support 40 only; free-text reference strings are unbounded, so this is the field most likely to move with real data |

These four are not defects to chase before submission; they are the fields where the
*specification* is thinner than the message population, and each has a different remedy
(more channel phrasings, a kind disambiguation pass, failure-traffic sampling, free-text
reference boundaries). Tuning against placeholder data would only overfit the generator.

---

## 3. On-device categorisation (Objective 3)

> Split: train 840 / dev 180 / test 180, deterministic, stratified by operator x kind. All four
> arms see the same records, so the arm-to-arm deltas are the reportable result.

### 3.1 Arms

| arm | test accuracy | test macro-F1 | dev accuracy | train accuracy |
|---|---|---|---|---|
| Tier 0 only (deterministic rules) | 0.850 | 0.868 | 0.828 | 0.841 |
| Tier 1 only (linear TF-IDF) | 0.967 | 0.935 | 0.972 | 1.000 |
| Cascade, rules always win (naive priority) | 0.906 | 0.908 | 0.867 | 0.882 |
| Cascade with arbitration (rules win on facts, model wins on names) - shipped | 0.978 | 0.968 | 0.989 | 0.995 |

### 3.2 Ablations

| arm | dev acc | test acc | test macro-F1 | test coverage | abstentions | Δ accuracy vs cascade | notes |
|---|---|---|---|---|---|---|---|
| Tier 0 only (deterministic rules) | 0.828 | 0.850 | 0.868 | 0.944 | 10 | -0.128 | Deterministic rules only - the no-learning baseline, and the layer that stays authoritative for structural facts. |
| Tier 1 only (linear TF-IDF) | 0.972 | 0.967 | 0.935 | 1.000 | 0 | -0.011 | Linear model only - what pure lexical context buys. Throws away facts the message states outright (a paybill prefix, 'for ELECTRICITY BILL', top-up vs spend). |
| Cascade, rules always win (naive priority) | 0.867 | 0.906 | 0.908 | 1.000 | 0 | -0.072 | Naive rule priority: a rule always wins, so a keyword inside a merchant name ('AIRTIME SOLUTIONS LTD') overrules the context. The cost of ignoring the model. |
| Cascade with arbitration (rules win on facts, model wins on names) - shipped | 0.989 | 0.978 | 0.968 | 1.000 | 0 | +0.000 | Shipped policy: rules win on structure (short-circuit, no model consulted); the model may overrule a keyword-only rule when it is confident and disagrees. |

- model artifact: `ghana_category_model_v1.tsv`, **188,983 bytes**, 6,000 weight entries, vocab 3,461 (kept 45,915 dropped by truncation)
- host-side scoring cost: **0.0704 ms/record** (single thread, CPython; *not* a device number - measure the Kotlin path with `./gradlew :parser-core:test --tests '*GhanaCategoryClassifierTest*'` on device)

### 3.3 Per-class detail (cascade, test split)

| label | P | R | F1 | support | predicted |
|---|---|---|---|---|---|
| food_dining | 1.000 | 1.000 | 1.000 | 6 | 6 |
| groceries | 1.000 | 1.000 | 1.000 | 3 | 3 |
| transport | 0.667 | 1.000 | 0.800 | 4 | 6 |
| utilities | 0.833 | 1.000 | 0.909 | 5 | 6 |
| airtime_data | 1.000 | 1.000 | 1.000 | 36 | 36 |
| shopping | - | - | - | 0 | 0 |
| health | 1.000 | 1.000 | 1.000 | 1 | 1 |
| education | - | - | - | 0 | 0 |
| rent_housing | - | - | - | 0 | 0 |
| entertainment | 1.000 | 1.000 | 1.000 | 2 | 2 |
| banking | 1.000 | 0.950 | 0.974 | 20 | 19 |
| cash_agent | 1.000 | 1.000 | 1.000 | 36 | 36 |
| p2p_transfer | 1.000 | 1.000 | 1.000 | 18 | 18 |
| income | 1.000 | 1.000 | 1.000 | 18 | 18 |
| other | 0.966 | 0.903 | 0.933 | 31 | 29 |

Supports are the honest part of that table: `shopping`, `education` and `rent_housing` have
**zero** test records, `health` and `entertainment` have 1 and 2. Per-class figures at this
corpus size are indicative only; the comparison between arms is what carries.

### 3.4 Cost of the shipped model

- artifact: `model/ghana_category_model_v1.tsv`, **188,983 B**, plain text, no runtime
  dependency (no TensorFlow, no ONNX runtime); it is a weight table read once at startup
- host-side scoring: **0.0704 ms/record** in CPython on the build sandbox,
  which is an upper bound on what a linear model over ~200 nonzero features costs on a
  handset, not a device measurement
- the Tier-0 rule layer is the part that decides most records and it costs a few substring
  tests; the model is only consulted when rules are ambiguous or silent

The finding to foreground: **naive rule priority loses about 7 accuracy points to the
arbitrated cascade** (0.906 vs 0.978) and 8 points to the model alone (0.967), because the
rule errors are keyword-on-merchant-name errors ('AIRTIME SOLUTIONS LTD' is not airtime),
while the rules that state structural facts (top-up, cash-out, paybill prefix) are worth more
than anything the linear model learns. Gating rules on extraction confidence was tried and
changed nothing (0.906 -> 0.906): the failures are lexical, not fallback artefacts.

---

## 4. Cross-engine parity

The evaluation numbers only reach the app if the Kotlin implements the same policy, so the
harness exports its own inputs as test fixtures:

| fixture | rows | asserted by |
|---|---|---|
| `ghana_corpus_expectations.tsv` | whole corpus | `TestGhanaSeedCorpus.kt`: parse-or-reject, amount, every annotated field |
| `ghana_python_predictions.tsv` | whole corpus | `TestGhanaSeedCorpus.kt`: classifier label + tier string per record |
| `parity_fixtures.tsv` | 37, branch-balanced | `TestGhanaCategoryClassifier.kt`: feature vector, all class scores, argmax, `rule_why`, `rule_strength`, cascade label, tier string, strict label |
| `ghana_category_model_v1.tsv` | 188,983 B | the shipped artifact, loaded from test resources by both tests |

| branch | fixtures | what it proves |
|---|---|---|
| `merchant_lexicon` | 8 | a keyword that matched inside a merchant name loses to a confident model |
| `model_only` | 8 | no rule fired, so the model alone decides |
| `model_overrules_rule` | 8 | the overrule path fired - model label + probability in the tier string |
| `structural_rule` | 8 | a structural fact wins and the model is never consulted |
| `unique_lexicon` | 5 | exactly one vocabulary matched; the model may still overrule at >= 0.50 |

**Status of these tests:** written and structurally checked, but *not executed* - the sandbox
this kit was built in has no JVM, so `./gradlew :parser-core:jvmTest` (or `./init.sh parser`)
still has to run once on a machine with JDK 21. `python3 tools/sync_kotlin.py --check`, which
does run here, passes: the generated Kotlin, the model artifact and the fixtures are in sync
with the specification.

---

## 5. What is expected to move when real captures land

| number | why |
|---|---|
| coverage, exact-every-field, `tx_kind`, `channel`, `status` | the placeholder corpus is generated *from* the specification, so agreement is partly by construction; real traffic brings label vocabulary the 46 pattern groups have never seen |
| template-vs-fallback split (template arm covers 0.491 today) | real traffic concentrates in a few shapes per operator, so template coverage - and the auto-accept share with it - should rise; if it does not, the templates are wrong, not the data |
| merchant-name F1 (1.000 / 0.977) | real names carry apostrophes, `&`, parentheses and mixed case; the normalizer was written for that but has only been exercised on generated names |
| per-class category numbers | zero support for three labels today; they become measurable only with the study corpus |
| classifier accuracy overall | the Tier-1 vocabulary and idf are refit. The *policy* result - naive rule priority loses ~7 points to arbitration - is the claim expected to survive, because it comes from how names differ from facts, not from this corpus |

Report `results/ingest-report/` next to these tables: it records how many rows were read,
mapped, redacted and refused, which is what lets a reviewer see that the corpus was not curated
to fit the parser.
