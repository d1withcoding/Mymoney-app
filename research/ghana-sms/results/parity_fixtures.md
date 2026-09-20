# Parity fixtures (Python -> Kotlin)

37 records selected to cover every branch of the arbitration policy, written by `tools/train.py` and copied to `parser-core/src/test/resources/ghana/parity_fixtures.tsv` by `tools/sync_kotlin.py`. `TestGhanaCategoryClassifier.kt` asserts the feature vector, every class score, the argmax, `rule_why`, `rule_strength`, the cascade label, the **tier string** and the strict label per fixture, so a Kotlin-side drift in the policy fails the build.

| branch | fixtures | what it proves |
|---|---|---|
| `merchant_lexicon` | 8 | a keyword that matched inside a merchant name loses to a confident model |
| `model_only` | 8 | no rule fired, so the model alone decides |
| `model_overrules_rule` | 8 | the overrule path fired - model label + probability in the tier string |
| `structural_rule` | 8 | a structural fact wins and the model is never consulted |
| `unique_lexicon` | 5 | exactly one vocabulary matched; the model may still overrule at >= 0.50 |

| # | operator | branch | cascade label | tier | strict label |
|---|---|---|---|---|---|
| 1 | airtel_tigo_money | `merchant_lexicon` | food_dining | `rule:lexicon:mcdonald` | food_dining |
| 2 | airtel_tigo_money | `merchant_lexicon` | utilities | `rule:lexicon:bill` | utilities |
| 3 | airtel_tigo_money | `merchant_lexicon` | utilities | `rule:lexicon:bill` | utilities |
| 4 | airtel_tigo_money | `merchant_lexicon` | transport | `rule:lexicon:stc` | transport |
| 5 | airtel_tigo_money | `merchant_lexicon` | transport | `rule:lexicon:akwaaba parking` | transport |
| 6 | airtel_tigo_money | `merchant_lexicon` | utilities | `rule:lexicon:bill` | utilities |
| 7 | airtel_tigo_money | `merchant_lexicon` | transport | `rule:lexicon:vip travel and tours` | transport |
| 8 | airtel_tigo_money | `merchant_lexicon` | food_dining | `rule:lexicon:bistro` | food_dining |
| 9 | mtn_momo | `model_only` | p2p_transfer | `model:0.89` | p2p_transfer |
| 10 | mtn_momo | `model_only` | p2p_transfer | `model:0.98` | p2p_transfer |
| 11 | mtn_momo | `model_only` | p2p_transfer | `model:0.86` | p2p_transfer |
| 12 | mtn_momo | `model_only` | p2p_transfer | `model:0.89` | p2p_transfer |
| 13 | mtn_momo | `model_only` | p2p_transfer | `model:0.88` | p2p_transfer |
| 14 | telecel_cash | `model_only` | p2p_transfer | `model:0.94` | p2p_transfer |
| 15 | telecel_cash | `model_only` | p2p_transfer | `model:0.92` | p2p_transfer |
| 16 | telecel_cash | `model_only` | p2p_transfer | `model:0.93` | p2p_transfer |
| 17 | airtel_tigo_money | `model_overrules_rule` | groceries | `model:0.96 overruled lexicon 'lexicon:goil'` | transport |
| 18 | airtel_tigo_money | `model_overrules_rule` | other | `model:0.83 overruled lexicon 'lexicon:ecobank ghana'` | banking |
| 19 | airtel_tigo_money | `model_overrules_rule` | p2p_transfer | `model:0.91 overruled weak rule 'lexicon:pin'` | airtime_data |
| 20 | airtel_tigo_money | `model_overrules_rule` | other | `model:0.86 overruled lexicon 'lexicon:reversal'` | income |
| 21 | airtel_tigo_money | `model_overrules_rule` | other | `model:0.92 overruled lexicon 'lexicon:ecobank ghana'` | banking |
| 22 | mtn_momo | `model_overrules_rule` | other | `model:0.72 overruled lexicon 'lexicon:agent'` | cash_agent |
| 23 | mtn_momo | `model_overrules_rule` | other | `model:0.55 overruled lexicon 'lexicon:ghana education service'` | education |
| 24 | mtn_momo | `model_overrules_rule` | p2p_transfer | `model:0.98 overruled lexicon 'lexicon:rent'` | rent_housing |
| 25 | airtel_tigo_money | `structural_rule` | airtime_data | `rule:kind=airtime/data` | airtime_data |
| 26 | airtel_tigo_money | `structural_rule` | airtime_data | `rule:kind=airtime/data` | airtime_data |
| 27 | airtel_tigo_money | `structural_rule` | airtime_data | `rule:kind=airtime/data` | airtime_data |
| 28 | airtel_tigo_money | `structural_rule` | airtime_data | `rule:kind=airtime/data` | airtime_data |
| 29 | airtel_tigo_money | `structural_rule` | airtime_data | `rule:kind=airtime/data` | airtime_data |
| 30 | airtel_tigo_money | `structural_rule` | airtime_data | `rule:kind=airtime/data` | airtime_data |
| 31 | airtel_tigo_money | `structural_rule` | airtime_data | `rule:kind=airtime/data` | airtime_data |
| 32 | airtel_tigo_money | `structural_rule` | airtime_data | `rule:kind=airtime/data` | airtime_data |
| 33 | airtel_tigo_money | `unique_lexicon` | p2p_transfer | `rule:lexicon:sent to` | p2p_transfer |
| 34 | airtel_tigo_money | `unique_lexicon` | p2p_transfer | `rule:lexicon:sent to` | p2p_transfer |
| 35 | airtel_tigo_money | `unique_lexicon` | p2p_transfer | `rule:lexicon:sent to` | p2p_transfer |
| 36 | airtel_tigo_money | `unique_lexicon` | p2p_transfer | `rule:lexicon:sent to` | p2p_transfer |
| 37 | airtel_tigo_money | `unique_lexicon` | p2p_transfer | `rule:lexicon:sent to` | p2p_transfer |
