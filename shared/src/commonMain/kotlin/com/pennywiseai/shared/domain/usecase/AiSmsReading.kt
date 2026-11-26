package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.domain.money.MinorUnits

/**
 * What the on-device model (iOS Foundation Models) read from a bank SMS that no
 * parser knows. Untrusted: [AiSmsValidator] decides whether it's saved.
 */
data class AiSmsReading(
    val isBankTransaction: Boolean,
    val amount: String,
    val currency: String,
    /** expense, income, credit (a credit-card spend) or transfer. */
    val type: String,
    val merchant: String?,
    val accountLast4: String?,
    val bankName: String?
)

/**
 * Guards the model's reading of an SMS before it becomes a transaction. A small
 * model can invent an amount or read an OTP as a spend, so the amount must be
 * printed in the SMS itself, and anything doubtful is dropped rather than saved.
 */
object AiSmsValidator {
    /** Marks every transaction the model read, so the user knows to check it. */
    const val AI_NOTE = "Read by AI – check it"

    /** [reading] as a transaction to save, or null when it isn't safe to save. */
    internal fun validate(body: String, reading: AiSmsReading, knownBank: String?): ParsedBankSms? {
        if (!reading.isBankTransaction || NOT_A_SPEND.containsMatchIn(body)) return null
        val currency = normalizeCurrency(reading.currency) ?: return null
        // The amount must be printed in the SMS next to THIS currency: "INR 318" never
        // passes as USD 318.
        val amount = canonicalAmount(reading.amount)
            ?.takeIf { a -> a != "0" && amountsIn(body).any { it.amount == a && currency in it.currencies } }
            ?: return null
        val type = TYPES[reading.type.trim().lowercase()] ?: return null
        val (whole, fraction) = amount.split('.').let { it[0] to it.getOrElse(1) { "" } }
        // In the currency's own precision (USD has no third decimal, JOD does), and small enough
        // that value x 10^digits fits a Long (BankParserBridge's cap).
        val digits = MinorUnits.digits(currency)
        if (fraction.length > digits || whole.length > 18 - digits) return null
        return ParsedBankSms(
            amount = "$whole.${fraction.padEnd(digits, '0')}",
            type = type,
            merchant = reading.merchant?.trim()?.takeIf { it.isNotEmpty() && it.length <= 60 },
            reference = null,
            // Only digits the SMS really shows, never a guess.
            accountLast4 = reading.accountLast4?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 && it in body },
            balance = null,
            bankName = knownBank ?: reading.bankName?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 } ?: "Unknown bank",
            transactionHash = aiTransactionHash(body),
            isFromCard = false,
            currency = currency,
            accountCurrency = null,
            isMobileWallet = false
        )
    }

    /** Ghana cedi codes and symbols are accepted; other currencies are outside this app's scope. */
    internal fun normalizeCurrency(raw: String): String? {
        val code = raw.trim().uppercase().removeSuffix(".")
        if (code == "₵" || code == "GH₵") return "GHS"
        return "GHS".takeIf { code == it }
    }

    /**
     * "Rs. 1,500.00" → "1500", "318.50" → "318.5": digits only, no grouping, no
     * trailing fraction zeros, so the model's amount and the SMS's compare equal.
     * Null for anything that isn't one plain amount (more than 3 decimals, too big);
     * [validate] then holds it to its currency's decimals.
     */
    internal fun canonicalAmount(raw: String): String? {
        val digits = raw.filter { it.isDigit() || it == '.' }.trim('.')
        val match = AMOUNT.matchEntire(digits) ?: return null
        val whole = match.groupValues[1].trimStart('0').ifEmpty { "0" }
        val fraction = match.groupValues[2].trimEnd('0')
        if (whole.length > 16) return null // the cap parser amounts have (see BankParserBridge)
        return if (fraction.isEmpty()) whole else "$whole.$fraction"
    }

    /** An amount printed in an SMS and the currencies its symbol or code can mean. */
    internal data class PrintedAmount(val amount: String, val currencies: Set<String>)

    /** Finds currency-tagged cedi amounts; a bare number isn't treated as money. */
    internal fun amountsIn(body: String): List<PrintedAmount> =
        MONEY.findAll(body).mapNotNull { m ->
            val number = (m.groups[3] ?: m.groups[4])?.value ?: return@mapNotNull null
            val amount = canonicalAmount(number.replace(",", "")) ?: return@mapNotNull null
            val code = (m.groups[1] ?: m.groups[5])?.value
            val currencies = when {
                code != null -> setOfNotNull(normalizeCurrency(code))
                else -> SYMBOLS[m.groups[2]?.value].orEmpty()
            }
            PrintedAmount(amount, currencies).takeIf { currencies.isNotEmpty() }
        }.toList()

    /** Same SMS, same hash: a second automation run is a duplicate, as for parsed SMS. */
    internal fun aiTransactionHash(body: String): String {
        var hash = 0xcbf29ce484222325uL // FNV-1a, 64-bit
        for (c in body.trim().lowercase()) {
            hash = (hash xor c.code.toULong()) * 0x100000001b3uL
        }
        return "ai_" + hash.toString(16)
    }

    private val AMOUNT = Regex("""(\d+)(?:\.(\d{1,3}))?""")
    // An OTP or a payment request quotes an amount that hasn't moved; the debit SMS follows.
    private val NOT_A_SPEND = Regex("""\b(OTP|one[- ]time password|verification code)\b""", RegexOption.IGNORE_CASE)
    private val TYPES = mapOf("expense" to "EXPENSE", "income" to "INCOME", "credit" to "CREDIT", "transfer" to "TRANSFER")

    private val SYMBOLS = mapOf("₵" to setOf("GHS"), "GH₵" to setOf("GHS"))

    // A number with a currency code (1) or symbol (2) right before it (3), or a code (5) right after it (4).
    private val MONEY = Regex(
        """(?:\b(GHS)\.?|(GH₵|₵))\s*(\d[\d,]*(?:\.\d+)?)|(?<![\w.,])(\d[\d,]*(?:\.\d+)?)\s*(GHS)\b""",
        RegexOption.IGNORE_CASE
    )
}
