# Objective 3 - on-device spending classification

> Corpus: `seed-corpus.jsonl`. Split: train 840 / dev 180 / test 180 (deterministic, stratified by operator x kind).

| arm | test accuracy | test macro-F1 | dev accuracy | train accuracy |
|---|---|---|---|---|
| Tier 0 only (deterministic rules) | 0.850 | 0.868 | 0.828 | 0.841 |
| Tier 1 only (linear TF-IDF) | 0.967 | 0.935 | 0.972 | 1.000 |
| Cascade, rules always win (naive priority) | 0.906 | 0.908 | 0.867 | 0.882 |
| Cascade with arbitration (rules win on facts, model wins on names) - shipped | 0.978 | 0.968 | 0.989 | 0.995 |

## Ablations

| arm | dev acc | test acc | test macro-F1 | test coverage | abstentions | Δ accuracy vs cascade | notes |
|---|---|---|---|---|---|---|---|
| Tier 0 only (deterministic rules) | 0.828 | 0.850 | 0.868 | 0.944 | 10 | -0.128 | Deterministic rules only - the no-learning baseline, and the layer that stays authoritative for structural facts. |
| Tier 1 only (linear TF-IDF) | 0.972 | 0.967 | 0.935 | 1.000 | 0 | -0.011 | Linear model only - what pure lexical context buys. Throws away facts the message states outright (a paybill prefix, 'for ELECTRICITY BILL', top-up vs spend). |
| Cascade, rules always win (naive priority) | 0.867 | 0.906 | 0.908 | 1.000 | 0 | -0.072 | Naive rule priority: a rule always wins, so a keyword inside a merchant name ('AIRTIME SOLUTIONS LTD') overrules the context. The cost of ignoring the model. |
| Cascade with arbitration (rules win on facts, model wins on names) - shipped | 0.989 | 0.978 | 0.968 | 1.000 | 0 | +0.000 | Shipped policy: rules win on structure (short-circuit, no model consulted); the model may overrule a keyword-only rule when it is confident and disagrees. |

- cascade abstentions on test: **0** (records where rules abstained and no model feature fired); Tier 0 alone abstains on **10** of the same 180 records
- model artifact: `ghana_category_model_v1.tsv`, **188,983 bytes**, 6,000 weight entries, vocab 3,461 (kept 45,915 dropped by truncation)
- host-side scoring cost: **0.0704 ms/record** (single thread, CPython; *not* a device number - measure the Kotlin path with `./gradlew :parser-core:test --tests '*GhanaCategoryClassifierTest*'` on device)

## Per-class detail (cascade, test split)

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

## Confusion matrix (cascade, test split) - rows = gold, cols = predicted

| gold | food_dining | groceries | transport | utilities | airtime_data | shopping | health | education | rent_housing | entertainment | banking | cash_agent | p2p_transfer | income | other | ABSTAINED |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| food_dining | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| groceries | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| transport | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| utilities | 0 | 0 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| airtime_data | 0 | 0 | 0 | 0 | 36 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| health | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| entertainment | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 |
| banking | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 19 | 0 | 0 | 0 | 1 | 0 |
| cash_agent | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 36 | 0 | 0 | 0 | 0 |
| p2p_transfer | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0 | 0 | 0 |
| income | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 18 | 0 | 0 |
| other | 0 | 0 | 2 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 28 | 0 |

## Tier-2 (on-device LLM) - upper bound, not a measurement

If a third tier resolved every residual the cascade accuracy on test would rise from 0.978 toward 1.0 by construction. This arm is reported only to size the headroom; measuring it requires running Qwen 2.5 via LiteRT-LM on the target handset and is listed as future work in the paper.
