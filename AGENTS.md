# Ghana Mobile Money App Guide

This repository is being adapted into a Ghana-only personal finance app. Treat
the thesis and the user's requirements in this repository's active task as the
product specification when they differ from the inherited PennyWise feature
set.

## Product scope

- Support Ghana and GHS only. The current supported SMS providers are MTN MoMo
  and Telecel Cash. Add AirtelTigo only when a supplied Ghana corpus sample
  demonstrates its message format. Do not retain or add rules for other
  countries, banks, currencies, or mobile-money services.
- Keep the Home, Transactions, Analytics, and Settings screens. Remove unrelated
  inherited features when they conflict with the Ghana-only thesis or the
  user's instructions.
- Keep SMS parsing, transaction storage, and analytics on-device.
- The source and fork are committed to AGPL-3.0. Preserve the license and
  attribution obligations in source files and distribution materials.

## Network and privacy requirements

- The app must not request Android `INTERNET`, `ACCESS_NETWORK_STATE`, or
  `CHANGE_NETWORK_STATE` permissions. Keep manifest merge-removal declarations
  when dependencies contribute these permissions, and inspect the merged
  manifest whenever an Android SDK is available.
- Do not add outbound clients, network workers, or SDKs for exchange rates,
  model downloads, SMS/problem reports, billing, webhooks, AI relays, analytics,
  or telemetry. Remove dormant clients and their UI, not just their permission
  or entry point.
- External links opened in the system browser are not app network clients, but
  do not use them to submit SMS content or financial data.
- When auditing, search Android and iOS source for HTTP clients, URLs, URL
  sessions, workers, and feature flags; also inspect manifest-merger output and
  dependencies for network-capable components. A source manifest removal rule
  alone does not prove the final APK has no network permission.

## SMS parsing rules

- Parser implementations live in `parser-core` and must remain pure Kotlin with
  no Android dependencies. Register supported Ghana services only in
  `BankParserFactory` and the server parser registry.
- Write provider rules from the supplied Ghana corpus. Add one unit test per
  configured template, using synthetic or anonymized examples. Assert amount,
  `GHS`, transaction direction, and the available balance, counterparty, and
  reference fields. Include negative/non-transaction cases where applicable.
- Use shared parser test helpers and conventions in
  `docs/parser-test-standards.md`.
- `momo-parse` 0.1.0 is an unlicensed, broken PyPI package: its wheel omits its
  template configuration, and its README example produced `unknown` with zero
  confidence. Treat it as a black-box baseline only. Do not copy or re-express
  its rules or code as an implementation source.
- `cedikit` 1.3.0 is MIT-licensed and covers MTN and Telecel only. If its
  templates are re-expressed, retain clear attribution in the parser source.
  One failed sample is only one observation; do not make broader performance
  claims from it. It provides no AirtelTigo rules.
- Derive AirtelTigo rules only from the study corpus. Do not infer its formats
  from another provider.
- Do not fold E-Levy into the transaction amount. Parse only explicit amounts
  and fees represented by the message template and thesis.
- Keep `docs/BANK_SUPPORT.md`, `docs/supported-banks.json`, generated README
  support text, and server parser registry aligned with the implemented Ghana
  parsers. Regenerate generated catalogs with their documented script.

## Classifier and AI requirements

- The thesis specifies a TFLite classifier, not a Qwen 2.5 conversational
  assistant or a multi-gigabyte model download. Do not add or restore Qwen chat,
  model-download UI, download workers, or LLM-based classification.
- The inherited codebase still contains Qwen/model-related implementation in
  places. Consider the classifier migration and complete removal of those
  remnants unfinished until they are replaced or removed and verified.
- Do not claim that the classifier requirement is met by the existing rule
  parser or SMS scan heuristics. Keep classifier decisions local and testable.
- SQLCipher support/checking is also a thesis requirement that must be
  implemented and verified before claiming completion. Do not claim Room data
  is encrypted by SQLCipher unless the database open path actually uses it and
  a test/check verifies that configuration.

## Persistence and currency constraints

- Keep all money currency-tagged. In this product, default and supported
  transaction currency is `GHS`; do not silently interpret foreign-currency
  input as cedis.
- Never sum amounts with different currencies. If legacy currency support
  remains in persistence, preserve currency-aware totals and ensure the user
  interface does not offer unsupported currencies.
- Every backup-serialized field needs a Kotlin default. Use `@Contextual` for
  `BigDecimal`, `LocalDate`, and `LocalDateTime`, and `@Serializable` for enums.
  Read `docs/backup-format.md` before changing entities or backup models.
- Do not put real SMS content, phone numbers, names, account identifiers, or
  other PII in code, comments, fixtures, or logs. Use synthetic examples.
- Database migrations must work on the oldest supported Android SQLite. Follow
  `docs/database-migrations.md` for migration review and device checks.

## Working and verification conventions

- Read the relevant topic docs before changing app architecture, UI, parser
  rules, backups, or migrations. Keep changes focused and review generated
  output after regeneration.
- Use Java 21 and the installed Gradle distribution. The Gradle wrapper may be
  pinned to a different version; use the installed `gradle` command when
  wrapper downloads are unavailable.
- Parser changes: run `gradle :parser-core:test --console=plain` (or the
  documented parser gate). App changes: run the app compile gate and inspect
  the merged manifest. Run tests that cover changed behavior and report any
  verification that the local environment prevented.
- The Android compile currently requires SDK Build Tools 36.0.0 and platform
  37.0 in this checkout. SDK packages and licenses must be available and
  writable before Gradle can complete Android compilation.
- Do not hand-edit version numbers or release changelogs; use the repository's
  release tooling.
