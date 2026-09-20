# Related work (positioning, and what still has to be cited)

**Read this file as a scaffold, not a literature review.** Everything under "The reference system"
is stated from the code in this repository and is verifiable. Everything under "Claims to position
against" names the *kind* of source each argument needs and leaves the citation keys as
placeholders - I did not fabricate references, and a paper must not contain one that a reviewer
cannot open. Fill the placeholders from your own search, then delete this paragraph.

## The reference system: PennyWise AI (this repository)

PennyWise (AGPL-3.0) is a privacy-first Android expense tracker: Kotlin, Jetpack Compose, Hilt,
Room, WorkManager, no account and no cloud sync. Its SMS pipeline is the closest published-in-code
example of what this study extends, so its properties are worth stating precisely.

| aspect | what the code does | why this study does it the same / differently |
|---|---|---|
| parser set | 168 parser files covering 157 banks in 24 countries, each a subclass of `core/bank/BankParser` registered in `BankParserFactory`; **no Ghana mobile-money parser existed** | Objective 2 adds three (`MtnMoMoParser`, `TelecelCashParser`, `AirtelTigoMoneyParser`) sharing one base (`GhanaMobileMoneyParser`) because the three wallets differ in wording but not in task |
| extraction method | hand-written regex per bank, mostly whole-message patterns with a few named groups | same idea, but as a *declarative spec* (`spec/ghana_patterns.json`) compiled by both the evaluation harness and the app, so the paper's numbers and the app's code cannot drift |
| confidence | a constant: `Constants.Parsing.CONFIDENCE_PATTERN_BASED = 0.7f` (0.9 for AI-assisted parses) | a per-parse value from a documented formula (METHODS 4), because the study needs an accept/review/auto-accept boundary rather than a provenance tag |
| categorisation | keyword `RULES` in `shared/.../domain/mapping/SharedCategoryMapping.kt`, falling back to "Others", plus a user-defined `TransactionRule` engine | Objective 3 keeps the keyword layer (Tier 0) and adds a 189 KB linear model (Tier 1) with an arbitration policy, evaluated on a 15-label taxonomy; three study labels intentionally have no in-app equivalent and collapse to "Others" instead of silently widening the app's taxonomy |
| on-device AI | LiteRT-LM running Qwen 2.5 behind `LlmService` | Tier 2 hook only. The study does not report an LLM number it did not measure |
| retention of unparsed messages | `UnrecognizedSmsDao` keeps messages that no parser handled; `ParsedTransaction.smsBody` carries the raw text forward | flagged as a privacy gap in `PRIVACY-ETHICS.md`: on-device parsing is not by itself privacy-preserving if the raw text is stored |
| dedup | md5 over (sender, amount, hash of body) | a hash is enough for dedup, which is what makes a "store no raw body" variant feasible |

Sources for these statements: `CLAUDE.md`, `parser-core/src/main/kotlin/...`,
`shared/src/.../SharedCategoryMapping.kt`. All counts are from `git ls-files` / `wc -l` at
commit `c512c7c` and should be restated at submission time.

## Ghana mobile-money context (motivation numbers)

Bank of Ghana payment statistics are the right citation for scale and for the trend that makes this
topic worth a paper. The figures this kit was assembled around (verify against the BoG
publication before submission, and prefer the primary source over any secondary blog):

- transaction *value* in mobile money around GH¢3.01 trillion for 2024, roughly +56.8 % year on
  year; a record month in late 2025 above GH¢518 billion and ~982 million transactions with
  ~26.7 million active wallets;
- agent-to-agent transfers a large share of value (about a third), which is why `cash_agent` is a
  first-class category in the study taxonomy even though it is absent from the app's;
- fee/levy structure: P2P under GHS 100 free, a 1.5 % levy above the daily cumulative threshold
  with a cap, bill payments fee-free but still levy-bearing - which is why the parser keeps
  `amount`, `fee` and `levy` as *separate* fields rather than folding them into one total.

Secondary sources that mentioned senders, spoof variants and USSD menus during scoping (low
authority; use only for background, and cite the operators or the regulator instead in the paper):
`https://dev.to/theboylexis/-how-i-parse-15-momo-sms-formats-with-95-accuracy-47hg` (a practitioner
write-up of a 3-stage MoMo parser with per-(telco,type) template scoring and a 0.6 downstream
confidence filter - notably converging with this study's two-stage design, and independently
reporting that regex beat a small BERT on rare types), `https://www.jbklutse.com/fake-bank-alert-sms-ghana/`
(spoofed alert wording, sender variants, missing transaction ids),
`https://www.jbklutse.com/mtn-momo-fees-2026/` (fee and levy tables),
`https://support.telecel.com.gh/help/telecel-cash-services-terms-and-conditions/` (every credit or
debit triggers an SMS containing the new balance, which is what makes `balance` a first-class
field here and not in a card-based system).

## Claims to position against (fill in the citations)

Each row is a claim the discussion section will need to defend, and the type of source that makes
it defensible.

1. *"Regex/grammar extraction is still competitive with neural extraction for structured
   notification text."* Needs: a neural RE (relation extraction) baseline on transaction SMS, or an
   RE-on-receipts paper; plus the practitioner data point above. This study can only claim parity on
   its own corpus, and cannot claim SOTA.
2. *"On-device categorisation avoids the cloud round-trip and its privacy cost."* Needs: a mobile
   expense app that classifies server-side (the mainstream case), and any published comparison of
   on-device vs server text classification for financial messages.
3. *"Hybrid rule + model beats either alone; naive rule priority does not."* Needs: hybrid
   neuro-symbolic / rule-then-learn work, and learning-under-prior-knowledge. The 0.850 / 0.967 /
   0.906 / 0.978 arm ladder is this study's contribution to this literature, on this corpus.
4. *"SMS is a legitimate personal-finance signal in markets where card data is thin."* Needs:
   financial-inclusion and mobile-money usage studies (BoG, GSMA).
5. *"Parsing SMS is under regulatory constraint."* Needs: Play Store permission policy text and
   date, plus any published alternative (SMS Retriever / User Consent API) - these change, so quote
   them with an access date, not from memory.
