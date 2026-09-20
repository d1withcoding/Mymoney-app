package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.ghana.GhanaCategories
import com.pennywiseai.parser.core.ghana.GhanaCategoryClassifier
import com.pennywiseai.parser.core.ghana.GhanaCategoryFeatures
import com.pennywiseai.parser.core.ghana.GhanaCategoryLabels
import com.pennywiseai.parser.core.ghana.GhanaCategoryModel
import com.pennywiseai.parser.core.ghana.GhanaCategoryRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Objective 3 - the on-device spending classifier: the shipped artifact must be small, and it
 * must be *the same function* the Python trainer measured.
 *
 * The fixtures are exported by `research/ghana-sms/tools/train.py` and copied by
 * `sync_kotlin.py`. Two details make this test worth having:
 *
 *  - they were produced from the **quantised** TSV, not from the trainer's float weights, so
 *    four-decimal rounding in the artifact cannot masquerade as a divergence (tolerances below
 *    are small but non-zero, and the decisions themselves are asserted exactly);
 *  - they are sampled **per policy branch** (`structural_rule`, `merchant_lexicon`,
 *    `unique_lexicon`, `model_overrules_rule`, `model_only`), because the interesting behaviour of
 *    a hybrid cascade is the arbitration, and a fixture set of airtime messages would never
 *    exercise it.
 */
class GhanaCategoryClassifierTest {

    private val modelText: String by lazy { resource(MODEL) }
    private val model: GhanaCategoryModel by lazy { GhanaCategoryModel.load(modelText) }
    private val fixtures: List<Fixture> by lazy {
        resource(FIXTURES).lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { Fixture(it.split('\t')) }
            .toList()
    }

    @Test
    fun `the shipped artifact keeps the taxonomy label order`() {
        assertEquals(GhanaCategories.LABELS, model.labels, "model rows and the Kotlin label list disagree")
        assertTrue(model.featureCount > 0, "no vocabulary in the exported model")
        val bytes = modelText.toByteArray().size
        assertTrue(bytes < 400_000, "model artifact grew past 400 KB ($bytes B); re-tune --max-per-label")
    }

    @Test
    fun `rules layer is exact on the cases a user can audit`() {
        // (label, why) pairs are asserted together: 'which rule fired' is what the app shows when
        // someone challenges an automatic categorisation.
        assertEquals(
            GhanaCategoryRules.Hit("airtime_data", "kind=airtime/data", GhanaCategoryRules.STRUCTURAL),
            GhanaCategoryRules.predict(
                "You have purchased GHS 20.00 worth of airtime for 0201111111",
                mapOf("tx_kind" to "airtime_purchase")
            )
        )
        assertEquals(
            GhanaCategoryRules.Hit("cash_agent", "kind=deposit/withdrawal", GhanaCategoryRules.STRUCTURAL),
            GhanaCategoryRules.predict("Cash out of GHS 200.00 at AGENT KOFI", emptyMap())
        )
        assertEquals(
            GhanaCategoryRules.Hit("utilities", "lexicon:ecg", GhanaCategoryRules.MERCHANT_LEXICON),
            GhanaCategoryRules.predict("Payment of GHS 100.00 to ECG successful", emptyMap())
        )
        assertNull(
            GhanaCategoryRules.predict("Payment of GHS 100.00 to XYZ LTD successful", emptyMap())
        ) // a paybill the lexicon does not know must go to the model, never be guessed
    }

    @TestFactory
    fun `features vector scores and cascade decisions match the python trainer`(): List<DynamicTest> {
        assertTrue(fixtures.isNotEmpty(), "parity fixture is empty - run tools/sync_kotlin.py")
        val classifier = GhanaCategoryClassifier(model)
        return fixtures.mapIndexed { index, fixture ->
            DynamicTest.dynamicTest("${fixture.branch} #${index + 1}") {
                val counts = GhanaCategoryFeatures.extract(fixture.text, fixture.meta)
                val vector = model.vectorize(counts)
                assertEquals(fixture.vector.keys, vector.keys, "which features fired differs from Python")
                for ((feature, expected) in fixture.vector) {
                    assertEquals(expected, vector[feature] ?: 0.0, 1e-5, "tf-idf weight drift on $feature")
                }

                val scores = model.scores(vector)
                assertEquals(fixture.scores.size, scores.size, "score vector length")
                fixture.scores.forEachIndexed { label, expected ->
                    assertEquals(expected, scores[label], 5e-4, "logit drift on ${model.labels[label]}")
                }
                assertEquals(fixture.prediction, model.predictProbs(vector).first, "model argmax drift")

                val hit = GhanaCategoryRules.predict(fixture.text, fixture.meta)
                assertEquals(fixture.ruleLabel, hit?.label ?: "", "rule label drift")
                assertEquals(fixture.ruleStrength, hit?.strength ?: "", "rule strength drift")
                assertEquals(fixture.ruleWhy, hit?.why ?: "", "rule explanation drift")

                val decision = classifier.classify(fixture.text, fixture.meta)
                // Python emits an empty cell when both tiers abstain; the app contract needs a
                // label, so Kotlin reports the fall-through 'other' with abstained = true.
                assertEquals(
                    fixture.cascadeLabel.ifEmpty { GhanaCategories.FALLBACK_LABEL },
                    decision.label,
                    "cascade decision drift"
                )
                assertEquals(fixture.cascadeTier, decision.tier, "which tier decided, and why, changed")
                assertEquals(
                    fixture.cascadeStrictLabel,
                    classifier.classify(fixture.text, fixture.meta, strict = true).label,
                    "the naive rules-first arm must reproduce what the paper reports for it"
                )
                assertEquals(
                    GhanaCategoryLabels.resolve(decision.label),
                    decision.categoryName,
                    "label -> app category mapping is the call site's contract"
                )
            }
        }
    }

    @Test
    fun `every arbitration branch is actually covered`() {
        val branches = fixtures.map { it.branch }.toSet()
        listOf("structural_rule", "merchant_lexicon", "unique_lexicon", "model_overrules_rule", "model_only")
            .forEach { assertTrue(it in branches, "fixture set lost the '$it' branch; regenerate with tools/train.py") }
        assertTrue(
            fixtures.any { it.cascadeLabel != it.cascadeStrictLabel },
            "no record where arbitration changes the answer - the test would be vacuous"
        )
    }

    @Test
    fun `host-side cost is bounded (device timing needs an on-device run)`() {
        val iterations = 400
        val classifier = GhanaCategoryClassifier(model)
        val started = System.nanoTime()
        repeat(iterations) { i ->
            val fixture = fixtures[i % fixtures.size]
            classifier.classify(fixture.text, fixture.meta)
        }
        val millis = (System.nanoTime() - started) / 1_000_000
        println("Ghana classifier: $iterations classifications in ${millis} ms on the JVM test host")
        // Generous on purpose: this catches an accidental quadratic blow-up, not device speed.
        assertTrue(millis < 4_000, "classification took ${millis} ms for $iterations records - investigate")
    }

    private fun resource(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: throw IllegalStateException(
                "Missing Ghana classifier resource '$path'. Run: python3 research/ghana-sms/tools/sync_kotlin.py"
            )
        return stream.bufferedReader().use { it.readText() }
    }

    /** text, meta, vector, scores, prediction, rule_label, gold, rule_why, rule_strength, model_prob, cascade_label, cascade_tier, cascade_strict_label, branch */
    private data class Fixture(
        val text: String,
        val meta: Map<String, String>,
        val vector: Map<String, Double>,
        val scores: List<Double>,
        val prediction: String,
        val ruleLabel: String,
        val gold: String,
        val ruleWhy: String,
        val ruleStrength: String,
        val cascadeLabel: String,
        val cascadeTier: String,
        val cascadeStrictLabel: String,
        val branch: String
    ) {
        constructor(cells: List<String>) : this(
            text = cells.getOrElse(0) { "" },
            meta = pairs(cells.getOrElse(1) { "" }, ';'),
            vector = pairs(cells.getOrElse(2) { "" }, ' ').mapValues { (_, value) -> value.toDoubleOrNull() ?: 0.0 },
            scores = cells.getOrElse(3) { "" }.split(' ').mapNotNull { it.toDoubleOrNull() },
            prediction = cells.getOrElse(4) { "" },
            ruleLabel = cells.getOrElse(5) { "" },
            gold = cells.getOrElse(6) { "" },
            ruleWhy = cells.getOrElse(7) { "" },
            ruleStrength = cells.getOrElse(8) { "" },
            cascadeLabel = cells.getOrElse(10) { "" },
            cascadeTier = cells.getOrElse(11) { "" },
            cascadeStrictLabel = cells.getOrElse(12) { "" },
            branch = cells.getOrElse(13) { "" }
        )

        companion object {
            private fun pairs(raw: String, delimiter: Char): Map<String, String> = raw.split(delimiter)
                .filter { it.contains('=') }
                .associate { it.substringBeforeLast('=') to it.substringAfterLast('=') }
        }
    }

    companion object {
        private const val MODEL = "ghana/ghana_category_model_v1.tsv"
        private const val FIXTURES = "ghana/parity_fixtures.tsv"
    }
}
