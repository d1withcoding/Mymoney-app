package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for ECG Prepaid (Ghana) electricity meter recharge SMS messages.
 *
 * ECG (Electricity Company of Ghana) sends recharge confirmation SMS when
 * a customer tops up their prepaid electricity meter via mobile money.
 * These are always EXPENSE transactions.
 *
 * Senders: ECG, ECG Prepaid
 *
 * Supported patterns:
 * - "Recharge request of GHs 50.00 for meter 12345678 (JOHN DOE) has been processed.
 *    Txn ID: ECQ01988150. Token: 1234 5678 9012 3456 7890. Units: 38.5 kWh"
 *
 * Notes:
 * - ECG uses "GHs" (lowercase 's') in addition to standard GHS/GH¢/GH₵
 * - ECG recharges are always expenses (never credits)
 * - ECG messages don't report wallet balances
 * - The meter holder's name (in parentheses) is extracted as the merchant
 *
 * Currency: GHS (Ghana Cedi)
 * Country: Ghana
 */
class ECGPrepaidGhanaParser : BankParser() {

    override fun getBankName() = "ECG Prepaid"

    override fun getCurrency() = "GHS"

    /**
     * ECG prepaid is not a mobile wallet — it's a utility payment.
     * No account number to extract.
     */
    override fun extractAccountLast4(message: String): String? = null

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase().replace(" ", "")
        return s == "ECG" || s == "ECGPREPAID"
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()

        // Must contain GHS currency marker (including ECG's "GHs" variant)
        if (!GHS_MARKER.containsMatchIn(message)) return false

        // Must look like a meter recharge or payment
        return lower.contains("recharge") || lower.contains("processed") ||
                lower.contains("request") || lower.contains("purchase") ||
                lower.contains("payment") || lower.contains("meter")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // ECG-specific: "Recharge request of GHs 50.00" or "GHS 50.00"
        AMOUNT_PATTERN.find(message)?.let { match ->
            val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
            if (raw.isNotEmpty()) return parseGhsAmount(raw)
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        // ECG recharges are always expenses
        return TransactionType.EXPENSE
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Extract meter holder name: "meter 12345678 (JOHN DOE)"
        METER_NAME_PATTERN.find(message)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotEmpty()) return name
        }

        // Fall back to "ECG Prepaid"
        return "ECG Prepaid"
    }

    override fun extractBalance(message: String): BigDecimal? {
        // ECG messages don't report wallet balances
        return null
    }

    override fun extractReference(message: String): String? {
        // "Txn ID: ECQ01988150" or "ref: ..."
        TXN_ID_PATTERN.find(message)?.let { match ->
            val ref = match.groupValues[1].trim().trimEnd('.', ',', ';')
            if (ref.isNotEmpty()) return ref
        }
        return null
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

    companion object {
        // GHS currency markers — includes ECG's non-standard "GHs"
        private val GHS_MARKER = Regex("""GHs|GHS|GH¢|GH₵""", RegexOption.IGNORE_CASE)

        // Amount: "GHs 50.00" or "GHS 50.00" or "GH₵ 50.00"
        private val AMOUNT_PATTERN = Regex(
            """(?:(?:GHs|GHS|GH¢|GH₵)\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)|(?<!\d)([0-9]{1,6}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s+(?:GHs|GHS|GH¢|GH₵))""",
            RegexOption.IGNORE_CASE
        )

        // Meter holder name in parentheses: "meter 12345678 (JOHN DOE)"
        private val METER_NAME_PATTERN = Regex(
            """\bmeter\s+(?:\d+\s*)?\(([^)]+)\)""",
            RegexOption.IGNORE_CASE
        )

        // Transaction ID: "Txn ID: ECQ01988150" or "ref: ..."
        private val TXN_ID_PATTERN = Regex(
            """\b(?:Txn\s+ID|ref(?:erence)?)\s*[:\-]?\s*([^.,\n]+)""",
            RegexOption.IGNORE_CASE
        )
    }
}
