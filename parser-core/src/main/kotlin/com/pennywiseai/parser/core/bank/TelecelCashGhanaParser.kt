package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Telecel Cash (Ghana) SMS messages.
 *
 * Telecel Cash (formerly Vodafone Cash / V-Cash) is Ghana's second-largest
 * mobile money provider. SMS messages use GHS (Ghana Cedi) currency and
 * come from senders like:
 *   T-CASH, T Cash, Telecel, TelecelCash, Vodafone, V-Cash, Cash
 *
 * Supported patterns:
 * - "You have received GHS 100.00 from JOHN DOE. Your new balance is GHS 500.00"
 * - "Cash-out GHS 50.00 to Agent. Fee charged was GHS 1.00"
 * - "Payment made for GHS 20.00 to Merchant"
 * - "GHS 200.00 transferred to 0502345678 - KOFI MENSAH"
 * - "Your account was credited with GHS 500.00"
 *
 * Currency: GHS (Ghana Cedi)
 * Country: Ghana
 */
class TelecelCashGhanaParser : BankParser() {

    override fun getBankName() = "Telecel Cash"

    override fun getCurrency() = "GHS"

    /**
     * Telecel Cash is a phone-number-identified mobile-money wallet: SMS carry no
     * stable per-account number. Return null so all wallet activity consolidates
     * into one account.
     */
    override fun extractAccountLast4(message: String): String? = null

    /**
     * Flag as mobile wallet so balance is tracked against the single consolidated
     * wallet account.
     */
    override fun isMobileWallet(): Boolean = true

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase().replace(" ", "")
        return s == "T-CASH" || s == "TCASH" || s == "TELECEL" ||
                s == "TELECELCASH" || s == "VODAFONE" || s == "V-CASH" ||
                s == "VCASH" || s == "CASH"
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Must contain GHS currency marker
        if (!GHS_MARKER.containsMatchIn(message)) return false

        // Reject OTPs
        if (lower.contains("otp") || lower.contains("verification code")) return false

        // Reject SOS/airtime advance notices
        if (SOS_ADVANCE_PATTERN.containsMatchIn(message)) return false

        // Reject promos without transaction confirmation
        if (PROMO_PATTERN.containsMatchIn(message) && !CONFIRMATION_PATTERN.containsMatchIn(message)) return false

        // Reject data bundle confirmations (telco layer)
        if (DATA_BUNDLE_CONFIRM_PATTERN.containsMatchIn(message)) return false

        // Reject loan reminders
        if (LOAN_REMINDER_PATTERN.containsMatchIn(message) && !COMPLETED_PATTERN.containsMatchIn(message)) return false

        // Must contain a transaction keyword
        return TRANSACTION_KEYWORD_PATTERN.containsMatchIn(message)
    }

    override fun extractAmount(message: String): BigDecimal? {
        // Isolate primary statement before reference
        val refIdx = message.indexOf("Reference:", ignoreCase = true)
            .takeIf { it >= 0 } ?: message.indexOf("Ref:", ignoreCase = true)
            .takeIf { it >= 0 } ?: message.length
        val statement = message.substring(0, refIdx)

        // Priority 1: Verb-anchored amount
        VERB_AMOUNT_PATTERN.find(statement)?.let { match ->
            val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (raw.isNotEmpty()) return parseGhsAmount(raw)
        }

        // Priority 2: Any GHS amount in the statement, excluding fee/tax
        val candidates = GHS_AMOUNT_PATTERN.findAll(statement).mapNotNull { match ->
            val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (raw.isEmpty()) return@mapNotNull null
            val start = match.range.first
            val prefix = statement.substring(maxOf(0, start - 25), start).lowercase()
            val isFeeOrTax = prefix.contains("fee") || prefix.contains("tax") || prefix.contains("levy")
            val isPhone = !raw.contains('.') && !raw.contains(',') && raw.length >= 7
            if (isFeeOrTax || isPhone) null else parseGhsAmount(raw)
        }.toList()

        return candidates.firstOrNull()
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val refIdx = message.indexOf("Reference:", ignoreCase = true)
            .takeIf { it >= 0 } ?: message.indexOf("Ref:", ignoreCase = true)
            .takeIf { it >= 0 } ?: message.length
        val statement = message.substring(0, refIdx)

        val isCredit = EXPLICIT_CREDIT_PATTERNS.any { it.containsMatchIn(statement) }
        val isDebit = EXPLICIT_DEBIT_PATTERNS.any { it.containsMatchIn(statement) }

        return when {
            isCredit && !isDebit -> TransactionType.INCOME
            isDebit && !isCredit -> TransactionType.EXPENSE
            isCredit -> TransactionType.INCOME
            GENERIC_CREDIT_PATTERN.containsMatchIn(statement) &&
                    !GENERIC_DEBIT_PATTERN.containsMatchIn(statement) -> TransactionType.INCOME
            GENERIC_DEBIT_PATTERN.containsMatchIn(statement) &&
                    !GENERIC_CREDIT_PATTERN.containsMatchIn(statement) -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // 1. Inter-network transfer from
        TRANSFER_FROM_PATTERN.find(message)?.let { match ->
            val name = cleanCounterparty(match.groupValues[1])
            if (name.isNotEmpty()) return name
        }

        // 2. Inter-network sent
        INTER_NETWORK_SENT_PATTERN.find(message)?.let { match ->
            val name = cleanCounterparty(match.groupValues[1])
            if (name.isNotEmpty()) return name
        }

        // 3. Standard "to/from"
        TO_FROM_COUNTERPARTY_PATTERN.findAll(message).forEach { match ->
            val raw = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            val name = cleanCounterparty(raw)
            if (name.isNotEmpty()) return name
        }

        // 4. "for" clause
        FOR_COUNTERPARTY_PATTERN.find(message)?.let { match ->
            val name = cleanCounterparty(match.groupValues[1])
            if (name.isNotEmpty()) return name
        }

        return null
    }

    override fun extractBalance(message: String): BigDecimal? {
        val matches = BALANCE_PATTERN.findAll(message).toList()
        val match = if (matches.size > 1) {
            matches.firstOrNull { m ->
                val text = m.value.lowercase()
                text.contains("new") || text.contains("available")
            } ?: matches.last()
        } else {
            matches.firstOrNull()
        } ?: return null

        val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return parseGhsAmount(raw)
    }

    override fun extractReference(message: String): String? {
        REFERENCE_PATTERN.findAll(message).mapNotNull { m ->
            val raw = m.groupValues.getOrNull(1)?.trim()?.trimEnd('.', ',', ';', '-')
            if (raw.isNullOrEmpty() || raw.lowercase() in JUNK_REFS) null else raw
        }.toList().let { refs ->
            return refs.lastOrNull { !it.startsWith("Transfer From", ignoreCase = true) }
                ?: refs.firstOrNull()
        }
    }

    // ---- Helpers ----

    private fun parseGhsAmount(raw: String): BigDecimal? {
        val normalized = raw.replace(",", "")
        return try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun cleanCounterparty(raw: String?): String {
        if (raw == null) return ""
        var cp = raw.trim().trimEnd('.', ',', '-', ' ', ':')
        if (cp.isEmpty()) return ""

        // Strip "has been completed"
        val completedIdx = cp.indexOf(" has been completed", ignoreCase = true)
        if (completedIdx >= 0) cp = cp.substring(0, completedIdx).trim()

        // Strip leading agent codes
        cp = cp.replace(Regex("""^(?:[A-Za-z]{1,2})?\d{3,}\s*-\s*"""), "").trim()

        // "NAME - phone" → keep name only
        val namePhone = Regex("""^([A-Za-z\s.'&]+?)(?:\s*[-–(/]\s*|\s+)(?:\+?233|0)[0-9]{8,12}\)?$""").find(cp)
        if (namePhone != null) {
            val n = namePhone.groupValues[1].trim()
            if (n.isNotEmpty()) cp = n
        }

        // "phone - NAME" → keep name only
        val phoneName = Regex("""^(?:\+?233|0)[0-9]{8,12}(?:\s*[-–]\s*|\s+)(.+)$""").find(cp)
        if (phoneName != null) {
            val n = phoneName.groupValues[1].trim()
            if (n.isNotEmpty()) cp = n
        }

        // Strip trailing balance-related words
        cp = cp.replace(Regex("""[.\s]+(?:current|available|new)$""", RegexOption.IGNORE_CASE), "").trim()

        val lower = cp.lowercase()
        if (lower.contains("your telecel cash") || lower.contains("your vodafone cash") ||
            lower.contains("your wallet") || lower.contains("your account") ||
            lower.contains("your mobile money") || lower == "the agent" || lower == "agent" ||
            lower.matches(Regex("""^\d{1,2}:\d{2}(?::\d{2})?$"""))
        ) return ""

        // Format standalone Ghana phone numbers for display
        if (cp.matches(Regex("""^\+?233\d{9,}$""")) || cp.matches(Regex("""^0\d{9}$"""))) {
            return formatGhanaPhone(cp)
        }

        return cp
    }

    private fun formatGhanaPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        val local = when {
            digits.startsWith("233") && digits.length >= 12 -> "0" + digits.substring(3)
            digits.startsWith("0") && digits.length >= 10 -> digits
            else -> return phone
        }
        return if (local.length == 10) {
            "${local.substring(0, 3)} ${local.substring(3, 6)} ${local.substring(6)}"
        } else phone
    }

    companion object {
        private val GHS_MARKER = Regex("""GHS|GH¢|GH₵""", RegexOption.IGNORE_CASE)

        private val GHS_AMOUNT_PATTERN = Regex(
            """(?:(?:GHS|GH¢|GH₵)\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)|(?<!\d)([0-9]{1,6}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s+(?:GHS|GH¢|GH₵))""",
            RegexOption.IGNORE_CASE
        )

        private val VERB_AMOUNT_PATTERN = Regex(
            """\b(?:payment\s+(?:made\s+)?(?:for\s+(?:\d{7,}\s+)?)?|payment\s+of|credited\s+with|received\s+(?:for\s+)?|cash[-\s]?in\s+(?:received\s+)?(?:for\s+)?|cash[-\s]?out(?:\s+made)?\s+(?:for\s+)?|transferred|withdrawn?|purchased?|bought|paid|sent|recharge(?:\s+request)?(?:\s+of)?)\s*(?:(?:GHS|GH¢|GH₵)\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)|(?<!\d)([0-9]{1,6}(?:,[0-9]{3})*(?:\.[0-9]{1,2}))\s+(?:GHS|GH¢|GH₵))""",
            RegexOption.IGNORE_CASE
        )

        private val BALANCE_PATTERN = Regex(
            """\b(?:new\s+|current\s+|available\s+)?(?:balance|bal(?:ance)?)\b.{0,28}?(?:(?:GHS|GH¢|GH₵)\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)|([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s+(?:GHS|GH¢|GH₵))""",
            RegexOption.IGNORE_CASE
        )

        private val REFERENCE_PATTERN = Regex(
            """\b(?:ref(?:erence)?|message\s+from\s+sender)\b\s*[:\-]?\s*([^.\n]+)""",
            RegexOption.IGNORE_CASE
        )

        private val TO_FROM_COUNTERPARTY_PATTERN = Regex(
            """\b(?:to|from)\s+(?:Agent\s+)?(.+?)(?=\s+(?:new\s+|current\s+)?(?:balance|bal(?:ance)?|ref(?:erence)?|on|with\b|fee|tax|transaction)\b|\.\s+|\.$|[!]|\s{2,}|$)""",
            RegexOption.IGNORE_CASE
        )

        private val FOR_COUNTERPARTY_PATTERN = Regex(
            """\bfor\s+(?!(?:GHS|GH¢|GH₵|\d+\.\d+))(.+?)(?=\s+(?:new\s+|current\s+|available\s+)?(?:balance|bal(?:ance)?|ref(?:erence)?|on|with\b)\b|\.\s+|\.$|[!]|\s{2,}|$)""",
            RegexOption.IGNORE_CASE
        )

        private val TRANSFER_FROM_PATTERN = Regex(
            """\bTransfer\s+From:\s*(?:[0-9+]+-)?([^\n.]+?)(?=\s+on\b|\s+at\b|\.\s*|$)""",
            RegexOption.IGNORE_CASE
        )

        private val INTER_NETWORK_SENT_PATTERN = Regex(
            """\bsent\s+to\s+(?:[0-9+]+(?:\s*-\s*|\s+))?([A-Za-z\s]+?)(?=\s+on\s+(?:MTN|Telecel|T-CASH|Vodafone|MobileMoney)|\.\s*|$)""",
            RegexOption.IGNORE_CASE
        )

        private val EXPLICIT_CREDIT_PATTERNS = listOf(
            Regex("""\bhas\s+sent\s+(?:(?:GHS|GH¢|GH₵)\s*)?[0-9,.]+(?:\s*(?:GHS|GH¢|GH₵))?\s+to\s+your\s+(?:account|wallet)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bcredited\s+(?:with\s+)?(?:GHS|GH¢|GH₵)?\s*[0-9,.]+\s+(?:GHS|GH¢|GH₵)?\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:received|receive)\s+(?:GHS|GH¢|GH₵)?\s*[0-9,.]+\s+(?:GHS|GH¢|GH₵)?\b""", RegexOption.IGNORE_CASE),
            Regex("""\baccount\s+was\s+credited\b""", RegexOption.IGNORE_CASE),
            Regex("""\bcredited\s+with\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:refund(?:ed)?|revers(?:al|ed))\b""", RegexOption.IGNORE_CASE),
            Regex("""\bpayment\s+received\b""", RegexOption.IGNORE_CASE),
            Regex("""\btransfer\s+from\s*:""", RegexOption.IGNORE_CASE),
            Regex("""\bcash[-\s]?in\s+received\b""", RegexOption.IGNORE_CASE),
        )

        private val EXPLICIT_DEBIT_PATTERNS = listOf(
            Regex("""\b(?:payment|payment made|payment for)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bsent\s+to\s+(?!your\s+(?:account|wallet)\b)""", RegexOption.IGNORE_CASE),
            Regex("""\bpaid\s+to\b""", RegexOption.IGNORE_CASE),
            Regex("""\bcash[-\s]?out\b""", RegexOption.IGNORE_CASE),
            Regex("""\bwithdrawn?\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:debited|deducted|deduction)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:purchased|bought|repaid|repayment)\b""", RegexOption.IGNORE_CASE),
            Regex("""\bhas\s+been\s+used\s+on\b""", RegexOption.IGNORE_CASE),
        )

        private val GENERIC_CREDIT_PATTERN = Regex(
            """\b(credited|received|deposit|cash[-\s]?in|cashed[-\s]?in|refund|reversal|reversed)\b""",
            RegexOption.IGNORE_CASE
        )
        private val GENERIC_DEBIT_PATTERN = Regex(
            """\b(debited|deducted|deduction|sent|paid|withdrawn?|purchased?|cash[-\s]?out|transferred|bought|repaid|repayment|used)\b""",
            RegexOption.IGNORE_CASE
        )

        private val SOS_ADVANCE_PATTERN = Regex(
            """has\s+been\s+added\s+to\s+your\s+account\s+and\s+will\s+be\s+deducted""",
            RegexOption.IGNORE_CASE
        )
        private val PROMO_PATTERN = Regex(
            """\b(?:promo|promotion|offer|bonus|win|winner|advert|advertisement|enjoy)\b|\bfree\s+(?:\d+\s*(?:mb|gb)|data|airtime|sms|bundle)\b|\bdial\s*\*""",
            RegexOption.IGNORE_CASE
        )
        private val CONFIRMATION_PATTERN = Regex(
            """\b(?:confirmed|successful(?:ly)?|payment\s+(?:made|received)|cash[-\s]?in\s+received|you\s+have\s+(?:received|withdrawn|bought|purchased|paid|sent)|credited|debited|has\s+been\s+deducted)\b""",
            RegexOption.IGNORE_CASE
        )
        private val DATA_BUNDLE_CONFIRM_PATTERN = Regex(
            """\b(?:bought|purchased)\b.*\d+(?:\.\d+)?\s*(?:GB|MB)\b.*\bvalid\s+for\b""",
            RegexOption.IGNORE_CASE
        )
        private val LOAN_REMINDER_PATTERN = Regex(
            """\b(?:loan\s+is\s+not\s+settled|payment\s+guide|no\s+repayment\s+was\s+received)\b|\bto\s+avoid\s+(?:a\s+|the\s+)?[0-9.]+%""",
            RegexOption.IGNORE_CASE
        )
        private val COMPLETED_PATTERN = Regex(
            """\b(?:confirmed|successful(?:ly)?|credited\s+with|debited|has\s+been\s+deducted|has\s+sent)\b""",
            RegexOption.IGNORE_CASE
        )
        private val TRANSACTION_KEYWORD_PATTERN = Regex(
            """\b(?:received|credited|sent|paid|payment|transferred|cash[-\s]?out|cash[-\s]?in|withdrawn?|debited|deducted|purchased|bought|repaid|deposit|refund|reversal)\b""",
            RegexOption.IGNORE_CASE
        )

        private val JUNK_REFS = setOf("-", "--", ".", "..", "none", "null", "n/a", "no ref", "no reference", "0", "1", "2")
    }
}
