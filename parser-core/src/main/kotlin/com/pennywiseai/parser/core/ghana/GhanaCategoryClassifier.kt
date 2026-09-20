package com.pennywiseai.parser.core.ghana

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Objective 3: spending-category classification for a Ghana mobile-money SMS, on-device.
 *
 * Cascade (identical to the evaluated Python implementation in
 * `research/ghana-sms/tools/train.py`):
 *
 *  - [GhanaCategoryRules] (Tier 0): the transaction kind produced by [GhanaSmsExtractor] plus
 *    the shared merchant/keyword lexicon. Deterministic, instant, auditable - and it is what
 *    makes the easy cases (airtime, ECG paybill, agent cash-in) unbreakable.
 *  - [GhanaCategoryModel] (Tier 1): TF-IDF features scored by a multinomial logistic
 *    regression. A single 150 KB TSV, no tokenizer dependency, no native library, no network.
 *  - Tier 2 (an on-device LLM behind `LlmService`) is *not* implemented here: the paper
 *    evaluates the two tiers above and reports the residual size an LLM tier would have to
 *    resolve. Wiring one in is a call-site decision, not a change to this code.
 *
 * Integration note: this module returns taxonomy labels (`food_dining`) and, via
 * [GhanaCategoryLabels], the PennyWise category name to file the transaction under. Labels whose
 * name is not in `DefaultCategoryData.ALL` (`rent_housing`, `cash_agent`, `p2p_transfer`)
 * require the user to create that category once; until then they map to "Others" and
 * [GhanaCategoryLabels.isExact] reports false so the UI can offer the choice instead of
 * silently filing it away.
 */
object GhanaCategoryFeatures {

    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

    /**
     * Bag-of-words counts for a message. [meta] is the extraction result
     * (operator, tx_kind, direction, channel, status, amount, paybill_name, counterparty_name)
     * so structural signals join the lexical ones. Keep in step with `train.py:extract_features`;
     * `GhanaCategoryClassifierParityTest` pins the two against each other.
     */
    fun extract(text: String, meta: Map<String, String>): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        val core = GhanaNormalizer.stripBoilerplate(text.lowercase())
        val words = NON_ALPHANUMERIC.split(core).filter { it.length >= 2 }
        for (word in words) {
            bump(counts, "u:$word")
            if (word.any { it.isDigit() }) {
                val letters = word.filter { !it.isDigit() }
                bump(counts, "n:" + if (letters.isEmpty()) "num" else letters)
            }
        }
        for (i in 0 until words.size - 1) bump(counts, "b:${words[i]}_${words[i + 1]}")
        for (word in words) {
            var i = 0
            while (i + 3 <= word.length) {
                bump(counts, "c3:" + word.substring(i, i + 3))
                i++
            }
        }
        for ((key, prefix) in STRUCTURAL_KEYS) {
            val value = meta[key]
            if (!value.isNullOrEmpty()) bump(counts, "$prefix:${value.lowercase()}")
        }
        bump(counts, "amt:" + amountBucket(meta["amount"]))
        bump(counts, if (meta["paybill_name"].isNullOrEmpty()) "pb:no" else "pb:yes")
        bump(counts, if (meta["counterparty_name"].isNullOrEmpty()) "cp:no" else "cp:yes")
        val merchant = (meta["paybill_name"] ?: meta["counterparty_name"] ?: "").lowercase().trim()
        if (merchant.isNotEmpty()) {
            for (token in NON_ALPHANUMERIC.split(merchant).filter { it.length >= 3 }) bump(counts, "m:$token")
            bump(counts, "M:" + merchant.replace(' ', '_'))
        }
        return counts
    }

    private val STRUCTURAL_KEYS = listOf(
        "operator" to "op", "tx_kind" to "k", "direction" to "d",
        "channel" to "ch", "status" to "st"
    )

    /** Buckets the trainer uses verbatim; `na` when the amount is missing or unparseable. */
    fun amountBucket(amount: String?): String {
        val value = amount?.replace(",", "")?.toDoubleOrNull() ?: return "na"
        return when {
            value <= 10.0 -> "tiny"
            value <= 50.0 -> "small"
            value <= 200.0 -> "medium"
            value <= 1000.0 -> "large"
            else -> "xlarge"
        }
    }

    private fun bump(counts: MutableMap<String, Int>, key: String) {
        counts[key] = (counts[key] ?: 0) + 1
    }
}

/**
 * The exported linear model: `#ghana-cls` header, `L` label+bias lines, `I` inverse-document
 * frequency lines (needed to reproduce training-time normalisation on device), and `W` sparse
 * weight lines. Parsed once at construction; lookups are a hash map to a per-label row.
 */
class GhanaCategoryModel private constructor(
    val labels: List<String>,
    private val bias: DoubleArray,
    private val idf: Map<String, Double>,
    private val weights: Map<String, DoubleArray>
) {
    val featureCount: Int get() = idf.size

    /**
     * Sublinear tf x idf, L2-normalised over the vocabulary the model knows. Features outside
     * the vocabulary are ignored - exactly as in the trainer, which is why the parity fixture
     * vectors match to six decimals.
     */
    fun vectorize(counts: Map<String, Int>): Map<String, Double> {
        val vector = LinkedHashMap<String, Double>()
        for ((feature, count) in counts) {
            val weight = idf[feature] ?: continue
            vector[feature] = (1.0 + ln(count.toDouble())) * weight
        }
        var norm = 0.0
        for (value in vector.values) norm += value * value
        norm = sqrt(norm)
        if (norm > 0.0) {
            for (key in vector.keys.toList()) vector[key] = vector[key]!! / norm
        }
        return vector
    }

    fun scores(vector: Map<String, Double>): DoubleArray {
        val out = bias.copyOf()
        for ((feature, value) in vector) {
            val row = weights[feature] ?: continue
            for (j in out.indices) out[j] += row[j] * value
        }
        return out
    }

    /** Softmax over the raw scores; only used for a confidence read-out, argmax is the label. */
    fun probabilities(scores: DoubleArray): DoubleArray {
        val top = scores.max() ?: 0.0
        val exps = DoubleArray(scores.size) { exp(scores[it] - top) }
        val total = exps.sum().let { if (it == 0.0) 1.0 else it }
        for (i in exps.indices) exps[i] /= total
        return exps
    }

    fun predictProbs(vector: Map<String, Double>): Pair<String, DoubleArray> {
        val scores = scores(vector)
        var best = 0
        for (i in scores.indices) if (scores[i] > scores[best]) best = i
        return labels[best] to probabilities(scores)
    }

    companion object {
        const val MAGIC = "#ghana-cls"

        /** Test/CI entry point: read the shipped TSV as text (the app reads it from assets). */
        fun load(modelText: String): GhanaCategoryModel {
            val labels = ArrayList<String>()
            val bias = ArrayList<Double>()
            val idf = HashMap<String, Double>()
            val rows = HashMap<String, DoubleArray>()
            modelText.lineSequence().forEach { line ->
                if (line.isEmpty() || line.startsWith(MAGIC)) return@forEach
                val parts = line.split('\t')
                when (parts.firstOrNull()) {
                    "L" -> if (parts.size >= 3) {
                        labels += parts[1]
                        bias += parts[2].toDoubleOrNull() ?: 0.0
                    }

                    "I" -> if (parts.size >= 3) {
                        parts[2].toDoubleOrNull()?.let { idf[parts[1]] = it }
                    }

                    "W" -> if (parts.size >= 4) {
                        val index = parts[2].toIntOrNull() ?: return@forEach
                        val weight = parts[3].toDoubleOrNull() ?: return@forEach
                        val row = rows.getOrPut(parts[1]) { DoubleArray(labels.size) }
                        if (index < row.size) row[index] = weight
                    }
                }
            }
            require(labels.isNotEmpty()) { "Ghana category model has no labels" }
            return GhanaCategoryModel(labels, bias.toDoubleArray(), idf, rows)
        }
    }
}

/**
 * Tier 0 - the deterministic layer, and the policy that decides when it is allowed to win.
 *
 * Mirrors `train.py:RuleTier` + `train.py:arbitrate_with` exactly: the same lexicon (generated
 * from the same JSON), the same first-match-wins tie-break, the same arbitration. Two
 * implementations of one policy is only defensible because `GhanaCategoryClassifierTest` runs
 * every exported branch through both and compares the answers.
 */
object GhanaCategoryRules {

    const val STRUCTURAL = "structural"
    const val MERCHANT_LEXICON = "merchant_lexicon"
    const val UNIQUE_LEXICON = "unique_lexicon"
    const val WEAK = "weak"

    /** How sure the model must be to overrule a lexicon hit, and a weak direction prior. */
    const val LEXICON_OVERTURE_FLOOR: Double = 0.50
    const val MODEL_PROB_FLOOR: Double = 0.45

    data class Hit(val label: String, val why: String, val strength: String)

    /** Null when the rules abstain (no kind evidence and no lexicon match). */
    fun predict(text: String, meta: Map<String, String>): Hit? {
        val kind = (meta["tx_kind"] ?: "").lowercase()
        val lowered = text.lowercase()
        fun structural(label: String, why: String) = Hit(label, why, STRUCTURAL)
        if (kind == "deposit" || kind == "withdrawal" || lowered.contains("cash in") || lowered.contains("cash out")) {
            return structural("cash_agent", "kind=deposit/withdrawal")
        }
        if (kind == "airtime_purchase" || kind == "data_bundle") return structural("airtime_data", "kind=airtime/data")
        if (kind == "loan_disbursement" || kind == "loan_repayment") return structural("banking", "kind=loan")
        if (kind == "reversal") return structural("other", "kind=reversal")
        if (kind == "p2p_transfer_in") return structural("income", "kind=p2p_in")

        val merchant = (meta["paybill_name"] ?: meta["counterparty_name"] ?: "").lowercase()
        val haystack = "$merchant $lowered"
        if (kind == "p2p_transfer_out" && merchant.isEmpty()) return structural("p2p_transfer", "kind=p2p_out")

        // Collect every matching label first: 'uniqueness' is about how many labels matched in
        // total, not about how many had been seen when the best one was found.
        val matches = ArrayList<Triple<Int, Hit, Boolean>>()
        for ((label, keywords) in GhanaCategories.LEXICON) {
            var hits = 0
            var firstHit: String? = null
            for (keyword in keywords) {
                if (haystack.contains(keyword)) {
                    hits++
                    if (firstHit == null) firstHit = keyword
                }
            }
            if (hits == 0) continue
            val onMerchant = merchant.isNotEmpty() && keywords.any { merchant.contains(it) }
            val why = "lexicon:" + (firstHit ?: "")
            matches.add(Triple(hits + if (onMerchant) 2 else 0, Hit(label, why, ""), onMerchant))
        }
        if (matches.isNotEmpty()) {
            var best = matches[0]
            for (candidate in matches) if (candidate.first > best.first) best = candidate
            val strength = if (best.third) MERCHANT_LEXICON
            else if (matches.size == 1) UNIQUE_LEXICON else WEAK
            return best.second.copy(strength = strength)
        }
        if ((meta["direction"] ?: "").uppercase() == "IN") {
            // A credit with nothing else to go on is *usually* income. 'Usually' is precisely
            // the case the model should get a vote on, so this hit is marked WEAK.
            return Hit("income", "direction=IN", WEAK)
        }
        return null
    }

    /**
     * The arbitration policy. [strict] reproduces the naive 'rules have priority' cascade that
     * the paper reports as an ablation arm; the shipped cascade is the default.
     *
     * - a structural hit states what the transaction IS (from the parsed kind); the model never
     *   overrides a fact;
     * - a lexicon hit is a prior about *wording*, and merchant names are where keyword matching
     *   is weakest (a paybill called AIRTIME SOLUTIONS LTD is not an airtime purchase), so a
     *   confidently-agreeing model wins;
     * - a weak hit survives only while the model is unsure.
     */
    fun arbitrate(
        hit: Hit?,
        modelLabel: String?,
        modelProbability: Double,
        strict: Boolean = false
    ): Pair<String?, String> {
        if (hit != null && strict) return hit.label to "rule:${hit.why}"
        if (hit != null && hit.strength == STRUCTURAL) return hit.label to "rule:${hit.why}"
        if (modelLabel == null) {
            return if (hit != null) hit.label to "rule:${hit.why}" else null to "abstained:no features"
        }
        if (hit != null && (hit.strength == MERCHANT_LEXICON || hit.strength == UNIQUE_LEXICON)) {
            if (modelProbability >= LEXICON_OVERTURE_FLOOR && modelLabel != hit.label) {
                return modelLabel to
                    "model:" + "%.2f".format(modelProbability) + " overruled lexicon '${hit.why}'"
            }
            return hit.label to "rule:${hit.why}"
        }
        if (hit != null && hit.strength == WEAK) {
            if (modelProbability >= MODEL_PROB_FLOOR) {
                return modelLabel to
                    "model:" + "%.2f".format(modelProbability) + " overruled weak rule '${hit.why}'"
            }
            return hit.label to "rule:${hit.why}"
        }
        if (hit != null) return modelLabel to "model:" + "%.2f".format(modelProbability) + " (rules ambiguous)"
        return modelLabel to "model:" + "%.2f".format(modelProbability)
    }
}

/**
 * Result of the full cascade, carrying the tier that decided it. [tier] is the same string the
 * Python trainer emits (`rule:...`, `model:0.87 overruled lexicon '...'`), which is what lets the
 * parity test compare explanations and not just labels.
 */
data class GhanaCategoryPrediction(
    val label: String,
    val tier: String,
    val ruleEvidence: String?,
    val probability: Double,
    /** True when neither tier could say anything: the app should leave the field empty, not guess. */
    val abstained: Boolean = false
) {
    /** PennyWise category name to file the transaction under. */
    val categoryName: String get() = GhanaCategoryLabels.resolve(label)
    val isExact: Boolean get() = GhanaCategoryLabels.isExact(label)
}

/** Label -> app category, and which labels need a user-created category to be exact. */
object GhanaCategoryLabels {
    fun resolve(label: String): String =
        GhanaCategories.APP_CATEGORY[label] ?: GhanaCategories.FALLBACK_APP_CATEGORY

    /** False for labels whose category is not shipped, i.e. it was mapped to "Others". */
    fun isExact(label: String): Boolean = label !in GhanaCategories.USER_CREATED_LABELS

    fun allLabels(): List<String> = GhanaCategories.LABELS
}

/**
 * Cascade entry point. Constructed once with the loaded [GhanaCategoryModel] (the app can keep it
 * in a singleton; loading is a single pass over a ~190 KB text artifact).
 *
 * Integration sketch (app layer, not parser-core's business): call [classify] with the extraction
 * result of [GhanaSmsExtractor] as `meta` (operator, tx_kind, direction, channel, status, amount,
 * paybill_name, counterparty_name), then file the transaction under [GhanaCategoryPrediction.categoryName].
 * When [GhanaCategoryPrediction.isExact] is false the label mapped to "Others" because the user has
 * not created that category yet - surface a one-tap "create Cash Agent category" affordance rather
 * than filing it away silently.
 */
class GhanaCategoryClassifier(private val model: GhanaCategoryModel) {

    /** @param strict reproduces the naive 'rules always win' cascade, for comparison only. */
    fun classify(text: String, meta: Map<String, String>, strict: Boolean = false): GhanaCategoryPrediction {
        val hit = GhanaCategoryRules.predict(text, meta)
        val vector = model.vectorize(GhanaCategoryFeatures.extract(text, meta))
        var modelLabel: String? = null
        var probability = 0.0
        if (vector.isNotEmpty()) {
            val probabilities = model.probabilities(model.scores(vector))
            var best = 0
            for (i in probabilities.indices) if (probabilities[i] > probabilities[best]) best = i
            modelLabel = model.labels[best]
            probability = max(0.0, probabilities[best])
        }
        val decision = GhanaCategoryRules.arbitrate(hit, modelLabel, probability, strict)
        val label = decision.first
        if (label == null) {
            return GhanaCategoryPrediction(
                label = GhanaCategories.FALLBACK_LABEL,
                tier = decision.second,
                ruleEvidence = hit?.why,
                probability = 0.0,
                abstained = true
            )
        }
        return GhanaCategoryPrediction(label, decision.second, hit?.why, probability)
    }

    companion object {
        /** Path of the shipped artifact inside parser-core's resources (app: copy to assets). */
        const val MODEL_ASSET_PATH = "ghana/ghana_category_model_v1.tsv"
    }
}
