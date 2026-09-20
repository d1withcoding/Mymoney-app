package com.pennywiseai.parser.core.ghana

/**
 * Data types for the declarative Ghana mobile-money extraction spec.
 *
 * The instances themselves are generated from `research/ghana-sms/spec/ghana_patterns.json`
 * into [GhanaPatterns] by `research/ghana-sms/tools/sync_kotlin.py`; nothing in this file is
 * generated, and nothing here encodes a bank format. The Python reference implementation
 * (`research/ghana-sms/tools/ghana_parse/spec.py`) defines the same four types with the same
 * matching semantics, which is what makes the evaluated numbers transferable to this code.
 */

/**
 * A single per-field capture pattern.
 *
 * @param group capture-group index used when [value] is null and [wholeMatch] is false
 * @param wholeMatch take the whole match instead of a group
 * @param value constant returned whenever the pattern matches (used for channel/kind markers
 *   where the *presence* of the phrase is the information, not the phrase itself)
 */
data class GhanaFieldPattern(
    val id: String,
    val regex: Regex,
    val group: Int = 1,
    val wholeMatch: Boolean = false,
    val value: String? = null
) {
    /**
     * Returns the captured text for the first match in [text], or null. Blank and unmatched
     * groups are returned as-is: callers decide whether an empty capture counts, exactly as
     * the Python engine does, so that both languages reject the same candidates.
     */
    fun match(text: String): String? {
        val result = regex.find(text) ?: return null
        if (value != null) return value
        if (wholeMatch || group == 0) return result.value
        val groups = result.groupValues
        if (group >= groups.size) return null
        return groups[group]
    }
}

/**
 * A whole-message template: one regex that captures several fields at once.
 *
 * Templates are tried in [priority] order and scored by weighted field yield (see
 * [GhanaSmsExtractor]); a template that matches but yields no sane amount is discarded, so a
 * partial match can never book a wrong transaction.
 */
data class GhanaTemplate(
    val id: String,
    val kind: String,
    val priority: Int,
    val regex: Regex,
    val groups: Map<Int, String>
) {
    /** Like Python's `re.match`: anchored at the start of the message, not full-match. */
    fun match(text: String): Map<String, String>? {
        val result = regex.matchAt(text, 0) ?: return null
        val groups = result.groupValues
        val out = LinkedHashMap<String, String>(this.groups.size)
        for ((index, field) in this.groups) {
            if (index in groups.indices) {
                val raw = groups[index]
                if (raw.isNotBlank()) out[field] = raw.trim()
            }
        }
        return out.ifEmpty { null }
    }
}

/** Rules that mark a message as NOT a transaction (OTP, promos, pay requests). */
data class GhanaSkipRule(val id: String, val regex: Regex, val reason: String)

/** Rules that map wording to SUCCESS / FAILED / PENDING, evaluated in order. */
data class GhanaStatusRule(val id: String, val regex: Regex, val status: String)

/** Rules that map wording to IN / OUT when no template or transaction kind decided it. */
data class GhanaDirectionRule(val id: String, val regex: Regex, val direction: String)

/** One operator (MTN MoMo, Telecel Cash, AirtelTigo Money) and its message formats. */
data class GhanaOperatorSpec(
    val id: String,
    val displayName: String,
    val senderPatterns: List<Regex>,
    val bodyMarkers: List<Regex>,
    val templates: List<GhanaTemplate>,
    val fields: Map<String, List<GhanaFieldPattern>>
) {
    /** Sender-ID match, anchored at the start like Python's `re.match`. */
    fun matchesSender(sender: String): Boolean {
        val trimmed = sender.trim()
        if (trimmed.isEmpty()) return false
        return senderPatterns.any { it.matchAt(trimmed, 0) != null }
    }

    fun markerHits(text: String): Int = bodyMarkers.count { it.containsMatchIn(text) }
}
