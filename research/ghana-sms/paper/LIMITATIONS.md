# Limitations

Written to be pasted into the discussion section, then trimmed. Every item is a real limitation of
what was built here, not a hedge.

## 1. The corpus is a placeholder, so the headline numbers are self-consistency

`corpus/gold/seed-corpus.jsonl` is generated from the same specification the parser reads. Coverage
1.000 and amount F1 1.000 therefore demonstrate that the pipeline works end to end - they do **not**
demonstrate accuracy on real traffic. The information-bearing measurements are the ones where the
corpus and the engine disagree by construction: the two ablation arms (template-only coverage
0.491), the weak fields (`channel` 0.695, `tx_kind` 0.823, `reference` 0.775), the 105 `status`
false negatives, and the 14 unverifiable name extractions. A reviewer must be told this before they
are told any accuracy number.

**What fixes it**: `tools/ingest.py` on the study's own captures, human verification of the gold
blocks (`gold_source = human_verified`), and re-running `scripts/run_all.sh`. Nothing else in the
kit changes; the tables do.

## 2. The specification is marked DRAFT in its own file

`spec/ghana_patterns.json` carries `"status": "DRAFT - regexes are grounded in published message
samples and MUST be re-fitted against the author's collected corpus"`. Operators reword
notifications, change short codes, and alter fee presentation without notice; a parser that
measures well today can decay silently. Two mitigations are implemented and should be described in
the paper: `(no template)` coverage and per-field provenance counters are printed by every
evaluation run (a rising fallback share is the decay signal), and `scripts/run_all.sh` is cheap
enough (~14 s) to run in CI on a retained sample, which is the same "corpus-in-CI" lesson the
practitioner write-up in `RELATED-WORK.md` reports independently.

## 3. Small evaluation slices, and three labels with zero support

1 200 transaction records and 90 controls, split 840/180/180 for the classifier. Per-class precision
and recall for `shopping`, `education` and `rent_housing` are undefined on the test split; `health`
(1) and `entertainment` (2) are single-digit. Macro-F1 over 15 labels with those supports is a
weaker statistic than it looks, which is why the arm-to-arm comparison on identical records is the
claim, not the absolute value. There is also a generator artefact worth naming: 274-327 coarse
"format families" per operator is not the number of families in real traffic, it is what 40 shapes
times four rendering variants looks like under a skeleton hash.

## 4. Thresholds were chosen, not tuned

`MIN_ACCEPT_CONFIDENCE = 0.45`, `AUTO_ACCEPT_CONFIDENCE = 0.75`, and the arbitration floors
(`LEXICON_OVERTURE_FLOOR = 0.50`, `MODEL_PROB_FLOOR = 0.45`) are design choices written down in code
and reasoned about in `METHODS.md`, not points on a tuned curve. The fallback tier is capped at 0.55
so that the 0.75 bar means "template-shaped evidence", which is why the auto-accept share equals the
template-covered share; that is a property of the design, not of the data. A sensitivity sweep
(reliability diagram for the confidence score; accuracy-vs-threshold curves for the floors) is the
obvious next experiment and is not done here.

## 5. Confidence is a heuristic and uncalibrated

It is a weighted count of populated fields, not a probability. Nothing in this study establishes
that a parse at 0.8 is right 80 % of the time, and the paper must not imply it. What the score
*does* buy is an ordering that separates two evidence tiers with a gap by construction.

## 6. No device measurements at all

The sandbox this was built in has no JDK, no Android SDK and no handset: `./gradlew
:parser-core:jvmTest`, `./init.sh parser` and any on-device latency, memory, battery or cold-start
measurement remain to be run. `0.1 ms/record` in `RESULTS.md` 3.4 is CPython on the build host,
offered as an upper bound on a linear model's cost, not as a device number. The privacy argument
("nothing leaves the device") is architectural, and is defensible from the code; the practical
argument ("it is fast enough and cheap enough to run on a mid-range Ghanaian handset") is not yet
supported by a measurement and should not be claimed until one exists.

## 7. The Tier-1 model is deliberately simple, and has no neural baseline

A hand-written logistic regression over TF-IDF, because the environment had no package installs and
because an auditable 189 KB weight table is a feature for an on-device financial app. It means the
study cannot say "a small linear model is enough" in the comparative sense - only that on this
corpus a linear model reaches 0.967 test accuracy and improves the cascade, without a transformer,
a tokenizer vocabulary, or a download. The comparison to a fine-tuned small LM or to embedding-based
categorisation is missing and is the natural follow-up.

## 8. Tier 2 (on-device LLM) is not measured

`classifier_metrics.md` reports the upper-bound argument for a third tier and nothing else. Claiming
an LLM improvement would require running Qwen 2.5 through `LlmService` on target hardware, with
prompts, latency, energy cost and a failure mode for when it disagrees with a structural fact.

## 9. Parsing cannot authenticate a message

The single most consequential limitation for a financial app. A spoofed SMS that follows the real
wording parses successfully, with a plausible balance and a plausible transaction id: nothing in a
text message proves it came from the operator. `canHandle()` sender allow-lists and the status/id
heuristics reduce accidental false positives, and they are measured here (0.000 on the 90
controls), but they are not fraud detection and the paper must not present them as such. See
`PRIVACY-ETHICS.md` section 4 for the user-facing consequences.

## 10. Coverage boundaries of the corpus design

Three operators, one currency, English message bodies. Not covered: Twi or Ewe bodies; bank SMS and
card alerts in Ghana (the app has those for other countries, and the same cascade would need
re-fitting); USSD-only confirmations and push-notification-only events, which are increasingly how
wallets communicate; and the case where a user has disabled SMS backup or the app lacks the
permission, where the whole approach has no input. The study taxonomy also has three labels with no
in-app category (`cash_agent`, `rent_housing`, `p2p_transfer`), which the classifier maps to
"Others" rather than silently extending the app's category list.

## 11. Resolved ambiguities are assumed, not decided

60 records contain a day/month-ambiguous date string (`03-04-2025`), and the engine resolves them
day-first, which is the Ghanaian convention but not a universal one; a wrong day is a wrong
transaction. The evaluator counts an ISO-vs-day-first conflict as a flag, not as an error, so the
reported date F1 of 1.000 does not include that class of real-world risk.

## 12. Single-annotator risk

Until a second annotator labels a sample and a disagreement rate is reported, the gold labels are
one person's reading of the spec, and both precision and recall inherit that. `CORPUS_SCHEMA.md`
asks for double-coding on a subsample for exactly this reason; it has not happened.
