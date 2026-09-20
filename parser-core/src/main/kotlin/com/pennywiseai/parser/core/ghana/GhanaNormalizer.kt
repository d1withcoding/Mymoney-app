package com.pennywiseai.parser.core.ghana

import com.pennywiseai.parser.core.Constants
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Field normalisers for Ghana mobile-money SMS.
 *
 * Every function is *total*: junk in, `null` out, never an exception - a malformed alert must
 * not be able to crash the parsing loop. This is a line-by-line mirror of
 * `research/ghana-sms/tools/ghana_parse/normalize.py`; the Python copy is the one the paper's
 * metrics were measured with, and `GhanaNormalizerTest` pins the shared behaviour.
 */
object GhanaNormalizer {

    /**
     * Unicode whitespace, spelled out rather than `\\s`: Java's `\\s` is ASCII-only unless
     * Pattern.UNICODE_CHARACTER_CLASS is set (Kotlin's RegexOption has no equivalent), while
     * Python's `\\s` is Unicode-aware - and the reference implementation collapses non-breaking and
     * thin spaces before every regex runs. Normalising here means the generated patterns, which all
     * use `\\s`, then behave identically in both engines. This is not cosmetic: multi-part SMS are
     * concatenated with NBSP/thin spaces by some handsets, and a message that differs only in
     * invisible whitespace must not parse differently by platform.
     */
    private val WHITESPACE = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000\\u2028\\u2029]+")

    /** Amounts with more digits than this are not money, they are transaction ids. */
    private const val MAX_AMOUNT = "9999999999"

    private val AMOUNT_SHAPE = Regex("[0-9]{1,13}(\\.[0-9]{1,2})?")

    private val ISO_DATE = Regex("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})")
    private val DMY_DATE = Regex("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})")
    private val DAY_MONTH_YEAR = Regex("(\\d{1,2})[\\s-]([A-Za-z]{3,9})[\\s-](\\d{4})")
    private val MONTH_DAY_YEAR = Regex("([A-Za-z]{3,9})\\s+(\\d{1,2}),?\\s+(\\d{4})")

    private val TIME = Regex("(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*(am|pm)?")

    private val GHANA_PHONE = Regex("0[2-5][0-9]{8}")
    private val NOT_DIGIT = Regex("[^0-9]")
    private val ID_JUNK = Regex("[^A-Za-z0-9]")
    private val ACCOUNT_JUNK = Regex("[^0-9A-Za-z/-]")

    /** Words operators put right after a counterparty name; a greedy capture must not keep them. */
    private val NAME_STOPWORDS = setOf(
        "ON", "AT", "IN", "TO", "FROM", "BY", "FOR", "VIA", "REF", "TXN", "TRX",
        "THE", "YOUR", "NEW", "BALANCE", "TRANSACTION", "FEE", "LEVY", "CONFIRMED",
        "THANK", "YOU", "USE", "USING", "PAID", "SENT", "RECEIVED", "DEBIT", "CREDIT"
    )

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    private val NAME_TRIM_CHARS = charArrayOf(
        ' ', '.', ',', ':', ';', '-', '\u2013', '\u2014', '(', ')', '[', ']', '/', '\\'
    )

    // Weak prior only - number portability makes prefix -> network unreliable, and
    // tools/taxonomy.py cross-tabs the observed prefixes against senders so the real
    // distribution is measured rather than assumed. TODO(verify) against the NCA plan.
    private val MSISDN_PREFIX_HINTS = mapOf(
        "24" to "MTN", "53" to "MTN", "54" to "MTN", "55" to "MTN", "59" to "MTN", "82" to "MTN", "83" to "MTN",
        "20" to "TELECEL", "50" to "TELECEL", "70" to "TELECEL", "79" to "TELECEL",
        "27" to "AIRTILTIGO", "57" to "AIRTILTIGO", "28" to "AIRTILTIGO", "56" to "AIRTILTIGO"
    )

    private val BOILERPLATE_COMBINED by lazy {
        // (?:^|(?<=\s)) keeps a boilerplate sentence anchored to a clause boundary; Java has
        // no variable-width lookbehind problem here because the alternative is zero-width.
        val alternatives = GhanaPatterns.BOILERPLATE.joinToString("|") { it.pattern }
        Regex("(?:^|(?<=\\s))(?:" + alternatives + ")", RegexOption.IGNORE_CASE)
    }

    /**
     * Whitespace and unicode hygiene applied before any regex runs. Multi-part SMS arrive with
     * newlines and double spaces; smart quotes and non-breaking spaces break naive classes.
     */
    fun tidyText(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        // First: every Unicode space becomes an ASCII space, so `\s` in the generated patterns
        // matches the same text it does in Python (see WHITESPACE above for why that matters).
        val flattened = WHITESPACE.replace(text, " ")
        val replaced = flattened
            .replace('\u00A0', ' ')      // no-break space, common in concatenated multi-part SMS
            .replace('\u2019', '\'')     // right single quotation mark
            .replace('\u2018', '\'')     // left single quotation mark
            .replace('\u201C', '"')      // left double quotation mark (smart quotes from the dialler)
            .replace('\u201D', '"')      // right double quotation mark
            .replace('\u2013', '-')      // en dash
            .replace('\u2014', '-')      // em dash
            .replace('\r', '\n')
        return WHITESPACE.replace(replaced, " ").trim()
    }

    /**
     * Removes operator boilerplate ("Dial *170# to manage your wallet", "Thank you for using...",
     * "Top up more at any agent"). Left in place it invents channels and fees that the message
     * never states. Templates are `^`-anchored and unaffected, so only the per-field pass sees
     * the stripped text - see [GhanaSmsExtractor.extract].
     */
    fun stripBoilerplate(text: String): String {
        if (text.isEmpty()) return text
        val cleaned = WHITESPACE.replace(BOILERPLATE_COMBINED.replace(text, " "), " ")
            .trim(' ', '.', ',', ';')
        return cleaned.ifEmpty { text }
    }

    /** `'1,299.5'` -> `'1299.50'`; rejects non-monetary numbers and implausible magnitudes. */
    fun normalizeAmount(value: String?): String? {
        if (value == null) return null
        var cleaned = value.replace(Regex("[^0-9.]"), "")
        val dots = cleaned.count { it == '.' }
        if (dots > 1) {
            // Keep the LAST dot as the decimal separator (1.299,50 European style does appear
            // in some operator locales); drop the rest, which are thousand separators.
            val lastDot = cleaned.lastIndexOf('.')
            cleaned = cleaned.substring(0, lastDot).replace(".", "") + cleaned.substring(lastDot)
        }
        cleaned = cleaned.trim('.')
        if (cleaned.isEmpty() || !AMOUNT_SHAPE.matches(cleaned)) return null
        val amount = try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            return null
        }
        if (amount.signum() < 0 || amount > BigDecimal(MAX_AMOUNT)) return null
        return amount.setScale(Constants.Parsing.AMOUNT_SCALE, RoundingMode.HALF_UP).toPlainString()
    }

    /** Any operator date shape -> ISO `YYYY-MM-DD`, or null when it is not a real date. */
    fun normalizeDate(value: String?): String? {
        if (value == null) return null
        val text = value.trim().trimEnd('.', ',', ';')
        if (text.isEmpty()) return null

        val iso = ISO_DATE.matchEntire(text)
        if (iso != null) {
            return isoOrNull(
                iso.groupValues[1].toInt(), iso.groupValues[2].toInt(), iso.groupValues[3].toInt()
            )
        }
        val dmy = DMY_DATE.matchEntire(text)
        if (dmy != null) {
            var year = dmy.groupValues[3].toInt()
            if (dmy.groupValues[3].length < 4) year += if (year < 70) 2000 else 1900
            return isoOrNull(year, dmy.groupValues[2].toInt(), dmy.groupValues[1].toInt())
        }
        val monthName = DAY_MONTH_YEAR.matchEntire(text) ?: MONTH_DAY_YEAR.matchEntire(text)
        if (monthName != null) {
            val a = monthName.groupValues[1]
            val b = monthName.groupValues[2]
            val c = monthName.groupValues[3]
            val day: Int
            val monthText: String
            val year: Int
            if (a[0].isDigit()) {
                day = a.toInt(); monthText = b; year = c.toInt()
            } else {
                monthText = a; day = b.toInt(); year = c.toInt()
            }
            val month = MONTHS[monthText.lowercase().take(3)] ?: return null
            return isoOrNull(year, month, day)
        }
        return null
    }

    /** True when a numeric date could be read as either d/m/y or m/d/y (the Ghana/US collision). */
    fun dateIsAmbiguous(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        val match = DMY_DATE.matchEntire(value.trim()) ?: return false
        return match.groupValues[1].toIntOrNull() != null &&
            match.groupValues[2].toIntOrNull() != null &&
            match.groupValues[1].toInt() <= 12 && match.groupValues[2].toInt() <= 12
    }

    /** `'13:51:07'` / `'1:51 pm'` -> `'13:51'`. */
    fun normalizeTime(value: String?): String? {
        if (value == null) return null
        val text = value.trim().lowercase().replace(".", "")
        val match = TIME.matchEntire(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val meridiem = match.groupValues[3]
        if (hour > 23 || minute > 59) return null
        if (meridiem == "pm" && hour < 12) hour += 12 else if (meridiem == "am" && hour == 12) hour = 0
        return "%02d:%02d".format(hour, minute)
    }

    /** Any Ghana MSISDN shape -> local `0XXXXXXXXX`; foreign numbers are rejected. */
    fun normalizePhone(value: String?): String? {
        if (value == null) return null
        var digits = NOT_DIGIT.replace(value, "")
        digits = when {
            digits.length == 9 && !digits.startsWith("0") -> "0$digits"
            digits.length == 12 && digits.startsWith("233") -> "0" + digits.substring(3)
            digits.length == 11 && digits.startsWith("233") -> "0" + digits.substring(3)
            else -> digits
        }
        return if (GHANA_PHONE.matches(digits)) digits else null
    }

    /** Prefix-based network guess. Weak prior only - never used for routing. */
    fun networkHint(localPhone: String?): String? {
        if (localPhone == null || localPhone.length < 3) return null
        return MSISDN_PREFIX_HINTS[localPhone.substring(1, 3)]
    }

    /** Counterparty / merchant name: strip delimiters and trailing clause fragments. */
    fun normalizeName(value: String?): String? {
        if (value == null) return null
        // A trailing parenthetical is a qualifier the ledger does not want ("ECG (POWER)");
        // drop the whole balanced group before trimming stray brackets, mirroring normalize.py.
        var text = Regex("\\s*\\([^)]*\\)\\s*$$").replace(value.trim(), "")
        text = WHITESPACE.replace(text, " ").trim().trim(*NAME_TRIM_CHARS)
        // Drop a trailing " on 2025-09-10"-style tail a lazy capture may have kept.
        val tail = Regex("\\s+(?:on|at|in)\\s+\\d").find(text)
        if (tail != null) text = text.substring(0, tail.range.first).trim()
        val words = text.split(" ").toMutableList()
        while (words.isNotEmpty() &&
            words.last().uppercase().trimEnd('.').let { it in NAME_STOPWORDS }
        ) {
            words.removeAt(words.size - 1)
        }
        text = words.joinToString(" ").trim(' ', '.', ',', ':', ';', '-')
        text = Regex("\\s*-\\s*\$").replace(text, "")
        return text.ifEmpty { null }
    }

    /** Canonical comparison key: case- and punctuation-insensitive, for name matching. */
    fun nameKey(value: String?): String? {
        val normalized = normalizeName(value) ?: return null
        val cleaned = WHITESPACE.replace(
            normalized.uppercase().replace(Regex("[^A-Z0-9 ]"), " "), " "
        ).trim()
        return cleaned.ifEmpty { null }
    }

    fun normalizeTrxId(value: String?): String? {
        if (value == null) return null
        return ID_JUNK.replace(value, "").uppercase().ifEmpty { null }
    }

    fun normalizeChannel(value: String?): String? {
        if (value == null) return null
        val text = value.lowercase()
        return when {
            "agent" in text -> "agent"
            "atm" in text || "self-service" in text || "self service" in text -> "atm"
            "pos" in text || "mopay" in text || "mo mopay" in text || "card" in text -> "pos"
            "#" in text -> "ussd"
            "app" in text || "online" in text || "web" in text -> "app"
            else -> "other"
        }
    }

    /** Paybill / bill / meter account: alphanumerics, slashes and dashes only. */
    fun normalizeAccount(value: String?): String? {
        if (value == null) return null
        return ACCOUNT_JUNK.replace(value, "").trim().ifEmpty { null }
    }

    /** Manual calendar validation: keeps parser-core free of a java.time desugaring dependency. */
    private fun isoOrNull(year: Int, month: Int, day: Int): String? {
        if (month < 1 || month > 12 || day < 1) return null
        val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        val daysInMonth = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (day > daysInMonth[month - 1]) return null
        return "%04d-%02d-%02d".format(year, month, day)
    }
}
