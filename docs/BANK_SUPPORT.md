# Ghana Mobile Money SMS Support

The app currently supports MTN MoMo and Telecel Cash in Ghana. Their parsers live in `parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/GhanaMobileMoneyParsers.kt` and are the only provider parsers registered by `BankParserFactory` and the web parser.

The MTN and Telecel rules are Kotlin re-expressions of the user-selected MomoParse 0.1.0 template definitions, with attribution in the parser source. The supplied `synthetic_sms_corpus.csv` is used to check coverage. Telecel's fee-breakdown bank-transfer format is an additional corpus-derived variant. E-Levy is not inferred or added to a transaction amount.

## Current coverage

`docs/supported-banks.json` is generated from `BankParserFactory` and guarded by `SupportedBanksDocTest`. It lists only MTN MoMo and Telecel Cash. Run `./scripts/update-supported-banks.sh` after changing the registered Ghana services to refresh the catalogue, README block, Play listing copy, and web resources.

Each configured template has a dedicated test in `GhanaMobileMoneyParserTest.kt`. Tests use synthetic examples and assert the parsed amount, GHS currency, transaction direction, and available merchant, reference, and balance fields.

## Adding Ghana templates

Add a rule only when an anonymized or synthetic Ghana sample demonstrates its message shape. Add one test for each template and test non-transaction messages such as OTPs and promotional notices. AirtelTigo rules must come from the study corpus; no AirtelTigo samples are currently included in the supplied CSV.
