package com.pennywiseai.tracker.presentation.add

import java.math.BigDecimal

/**
 * Best guess at the amount in text shared into the app ("Share to PennyWise"):
 * a UPI receipt, a forwarded bank SMS, or a quick note like "450 swiggy".
 *
 * A Ghana cedi figure ("GH₵450", "₵450", "GHS 450") wins and names its currency.
 * A bare number is only trusted in a short note, and
 * only when it looks like an amount (≤ 6 digits, not glued to letters like an
 * account "XX1234"), because messages are full of dates, references and
 * account numbers. Anything unclear returns null — the user fills it in.
 */
object SharedTextAmount {
    data class Guess(val amount: BigDecimal, val currency: String?)

    // An amount ends cleanly: never cut "12.345" down to 12.34.
    private const val END = """(?![\d,]|\.\d)"""
    private const val NUMBER = """(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)$END"""
    private const val SMALL_NUMBER = """(\d{1,6}(?:\.\d{1,2})?)$END"""

    // \b keeps "rs" from matching inside words ("users 50").
    private val TAGGED = Regex("""(GH₵|₵|GHS|₹|\$|€|£|\b(?:INR|USD|AED|SAR|EUR|GBP|CAD|AUD|JOD|NPR|ETB)(?![a-z]))\s*$NUMBER""", RegexOption.IGNORE_CASE)
    private val BARE = Regex("""(?<![\w/.,-])$SMALL_NUMBER(?![/-])""")
    private const val SHORT_NOTE_CHARS = 40

    private fun currencyOf(tag: String): String? = when (tag.trim().uppercase()) {
        "GH₵", "₵", "GHS" -> "GHS"
        else -> null
    }

    fun extract(text: String): Guess? {
        TAGGED.find(text)?.let { m ->
            val amount = m.groupValues[2].replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            val currency = currencyOf(m.groupValues[1]) ?: return null
            return amount?.let { Guess(it, currency) }
        }
        if (text.length > SHORT_NOTE_CHARS) return null
        val bare = BARE.find(text)?.groupValues?.get(1)?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        return bare?.let { Guess(it, null) }
    }
}
