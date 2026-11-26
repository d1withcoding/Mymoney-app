# Adding Ghana Mobile Money SMS Rules

The parser module is scoped to Ghana mobile money. Add templates only for MTN MoMo, Telecel Cash, or AirtelTigo and register supported providers in `BankParserFactory`.

## Requirements

- Use the Ghana-specific sample corpus or a synthetic example for each observed message shape. Do not infer templates from provider names or other regions.
- Keep provider rules in `GhanaMobileMoneyParsers.kt`; preserve provenance in comments when re-expressing another project’s rules.
- Add one `DynamicTest` per template in `GhanaMobileMoneyParserTest.kt`. Assert amount, GHS currency, direction, and each available counterparty, reference, and balance field.
- Include OTP and promotional examples as rejected messages when that sender delivers them.
- Keep E-Levy separate from the transfer amount. Capture only the amount and balance explicitly reported in the SMS.
- Use synthetic names, numbers, and references in committed fixtures.

Run `./init.sh parser` after parser changes. The gate runs parser tests and checks generated support documentation. If the build environment lacks JDK 21, install it and rerun the command before treating the change as verified.
