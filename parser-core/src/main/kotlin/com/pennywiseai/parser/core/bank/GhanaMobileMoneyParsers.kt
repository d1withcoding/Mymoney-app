package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Shared Ghana mobile-money template parser. The MTN and Telecel templates are
 * Kotlin re-expressions of the user-selected MomoParse 0.1.0 rule definitions
 * (configs/mtn_templates.json and configs/telecel_templates.json), checked
 * against the supplied synthetic corpus. They are not reused Python code.
 * E-Levy is deliberately left out of transaction amounts; only explicit wallet
 * amounts and balances are parsed.
 */
abstract class GhanaMobileMoneyParser : BankParser() {
    protected data class Rule(
        val id: String,
        val type: String,
        val pattern: Regex,
        val amount: Int,
        val balance: Int,
        val merchant: Int,
        val reference: Int,
        val merchantLiteral: String?,
        val referenceLiteral: String?
    )

    protected abstract val rules: List<Rule>

    protected fun rule(id: String, type: String, pattern: Regex, amount: Int, balance: Int, merchant: Int, reference: Int, merchantLiteral: String?, referenceLiteral: String?) =
        Rule(id, type, pattern, amount, balance, merchant, reference, merchantLiteral, referenceLiteral)

    override fun getCurrency() = "GHS"
    override fun isMobileWallet() = true

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? =
        parseMatchingRule(smsBody, sender, timestamp, null)

    internal fun parseUsingRule(id: String, smsBody: String, sender: String, timestamp: Long = 0L): ParsedTransaction? =
        parseMatchingRule(smsBody, sender, timestamp, id)

    private fun parseMatchingRule(smsBody: String, sender: String, timestamp: Long, onlyId: String?): ParsedTransaction? {
        val selected = rules.asSequence().filter { onlyId == null || it.id == onlyId }
            .mapNotNull { rule -> rule.pattern.find(smsBody)?.let { rule to it } }.firstOrNull() ?: return null
        val (rule, match) = selected
        fun group(index: Int): String? = if (index > 0) match.groupValues.getOrNull(index)?.takeIf { it.isNotBlank() }?.trim() else null
        fun money(value: String?): BigDecimal? = value?.replace(",", "")?.toBigDecimalOrNull()
        val amount = if (rule.type == "wallet_balance") BigDecimal.ZERO else money(group(rule.amount)) ?: return null
        val type = when (rule.type) {
            "transfer_received", "deposit_received", "cash_in", "airtime_received", "payment_received", "interest_received" -> TransactionType.INCOME
            "wallet_balance" -> TransactionType.BALANCE_UPDATE
            else -> TransactionType.EXPENSE
        }
        val merchant = group(rule.merchant) ?: rule.merchantLiteral
        val reference = group(rule.reference) ?: rule.referenceLiteral
        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = merchant?.trim()?.trimEnd('.', ' '),
            reference = reference?.trim()?.trimEnd('.', ' '),
            accountLast4 = null,
            balance = money(group(rule.balance)),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            currency = getCurrency(),
            isMobileWallet = true
        )
    }
}

class MtnMomoParser : GhanaMobileMoneyParser() {
    override fun getBankName() = "MTN MoMo"
    override fun canHandle(sender: String): Boolean = sender.trim().equals("MobileMoney", true) || sender.trim().equals("MTNMoMo", true)

    override val rules = listOf(
        rule(id = "mtn_transfer_sent_v1", type = "transfer_sent", pattern = Regex("""^Payment made for GHS ([\d,]+\.?\d*) to ([A-Za-z][^.]+?) Current Balance: GHS ([\d,]+\.?\d*)[\s.]+Available Balance:.*?Reference:\s*(.*?)\.\s*Transaction ID:\s*(\d+)\.\s*Fee charged:\s*GHS\s*([\d.]+)"""), amount = 1, balance = 3, merchant = 2, reference = 4, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_transfer_sent_with_phone", type = "transfer_sent", pattern = Regex("""^Payment made for GHS ([\d,]+\.?\d*) to ([A-Za-z][A-Za-z\s'.\-&]+?) (233\d{9}|0\d{9})\. Current Balance: GHS ([\d,]+\.?\d*)\.\s*Available Balance:.*?Reference:\s*(.*?)\. Transaction ID:\s*(\d+)\. Fee charged:\s*GHS\s*([\d.]+)"""), amount = 1, balance = 4, merchant = 2, reference = 5, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_transfer_sent_v2", type = "transfer_sent", pattern = Regex("""^Payment made for GHS ([\d,]+\.?\d*) to ([A-Za-z][^.]+?)\. Current Balance: GHS ([\d,]+\.?\d*)\.\s*Available Balance:.*?Reference:\s*(.*?)\. Transaction ID:\s*(\d+)\. Fee charged:\s*GHS\s*([\d.]+)"""), amount = 1, balance = 3, merchant = 2, reference = 4, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_transfer_received", type = "transfer_received", pattern = Regex("""^Payment received for GHS ([\d,]+\.?\d*) from ([A-Za-z][A-Za-z\s'.\-&]+?)\s+Current Balance: GHS ([\d,]+\.?\d*)[\s.]+Available Balance:.*?Reference:\s*(.*?)\. Transaction ID:\s*(\d+)\.\s*(?:TRANSACTION FEE|Fee charged):\s*([\d.]+)"""), amount = 1, balance = 3, merchant = 2, reference = 4, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_money_transfer_deposit", type = "transfer_received", pattern = Regex("""^Money Transfer Deposit received for GHS\s*([\d,]+\.?\d*) from ([A-Za-z][A-Za-z\s'.\-&]+?)\s+Token:\s*(\d+)\s+Transaction ID:\s*(\d+)\.?\s*(?:TRANSACTION FEE(?:\s+IS)?|Fee charged:)\s*([\d.]+)"""), amount = 1, balance = 0, merchant = 2, reference = 3, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_cash_out", type = "cash_out", pattern = Regex("""^Cash Out made for GHS\s*([\d,]+\.?\d*) to (.+?)\. Current Balance: GHS\s*([\d,]+\.?\d*)[\s.]*Financial Transaction Id:\s*(\d+).*?Fee charged:\s*GHS\s*([\d.]+)"""), amount = 1, balance = 3, merchant = 2, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_airtime_purchase", type = "airtime_purchase", pattern = Regex("""^Your payment of GHS ([\d,]+\.?\d*) to MTN AIRTIME has been completed at (\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})\. Your new balance: GHS ([\d,]+\.?\d*)\. Fee was GHS ([\d.]+).*?Financial Transaction Id:\s*(\d+)"""), amount = 1, balance = 4, merchant = 0, reference = 0, merchantLiteral = "MTN AIRTIME", referenceLiteral = null),
        rule(id = "mtn_merchant_payment", type = "merchant_payment", pattern = Regex("""^Your payment of GHS ([\d,]+\.?\d*) to ((?!MTN AIRTIME|MTN BUNDLE)[A-Z].+?) has been completed at (\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})\. Reference:\s*(.*?)\. Your new balance: GHS ([\d,]+\.?\d*)\. Fee was GHS ([\d.]+).*?Financial Transaction Id:\s*(\d+)"""), amount = 1, balance = 6, merchant = 2, reference = 5, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_service_payment", type = "bill_payment", pattern = Regex("""^Payment for GHS\s*([\d,]+\.?\d*) to (.+?)[.\s]+Current Balance: GHS\s*([\d,]+\.?\d*)[.\s]+Transaction Id[:\s]*(\d+)[.\s]+Fee charged:\s*GHS\s*([\d.]+)"""), amount = 1, balance = 3, merchant = 2, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_cash_in", type = "cash_in", pattern = Regex("""^Cash In received for GHS ([\d,]+\.?\d*) from (.+?)\s*\.\s*Current Balance GHS ([\d,]+\.?\d*)\s+Available Balance GHS [\d,]+\.?\d*\.\s*Transaction ID:\s*(\d+)\.\s*Fee charged:\s*GHS\s*([\d.]+)"""), amount = 1, balance = 3, merchant = 2, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_cash_out_v2", type = "cash_out", pattern = Regex("""^You have cashed out GHS\s*([\d,]+\.?\d*) at (.+?) \(([\d]+)\)\. Available Balance: GHS\s*([\d,]+\.?\d*)\. Transaction ID:\s*(\d+)\. Fee charged:\s*GHS\s*([\d.]+)"""), amount = 1, balance = 4, merchant = 2, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "mtn_bundle_purchase_v2", type = "bill_payment", pattern = Regex("""^Your payment of GHS ([\d,]+\.?\d*) to MTN BUNDLE has been completed at (\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})\. Your new balance: GHS ([\d,]+\.?\d*)\. Fee was GHS ([\d.]+).*?Financial Transaction Id:\s*(\d+)"""), amount = 1, balance = 4, merchant = 0, reference = 0, merchantLiteral = "MTN BUNDLE", referenceLiteral = null),
    )
}

class TelecelCashParser : GhanaMobileMoneyParser() {
    override fun getBankName() = "Telecel Cash"
    override fun canHandle(sender: String): Boolean = listOf("T-CASH", "TelecelCash", "TCASH", "555").any { sender.trim().equals(it, true) }

    override val rules = listOf(
        rule(id = "telecel_transfer_sent", type = "transfer_sent", pattern = Regex("""^(\d{16}) Confirmed\. GHS([\d.]+) sent to ([\d]+) - ([A-Za-z][A-Za-z\s'.\-&]+?)(?: on (MTN MOBILE MONEY|TELECEL CASH))? on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your Telecel Cash balance is GHS([\d.]+)\. You were charged GHS([\d.]+)\. Your E-levy charge is GHS([\d.]+)\.(?:[\s\S]*?Reference:\s*([^.\n]*?)\s*\.)?[\s\S]*?Sendi k3k3!$"""), amount = 2, balance = 8, merchant = 4, reference = 11, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_transfer_received_mtn", type = "transfer_received", pattern = Regex("""^(\d{16}) Confirmed\. You have received GHS([\d.]+) from MTN MOBILE MONEY with transaction reference: Transfer From: ([\d]+)-([A-Za-z][A-Za-z\s'.\-&]+?)\s+on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your Telecel Cash balance is GHS([\d.]+)\.\s+Ref:\s*([^.\n]+)"""), amount = 2, balance = 7, merchant = 4, reference = 8, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_transfer_received_telecel", type = "transfer_received", pattern = Regex("""^(\d{16}) Confirmed\. You have received GHS([\d.]+) from ([A-Z][A-Za-z\s\.\-]+?) on TELECEL CASH on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your Telecel Cash balance is GHS([\d.]+)\.(?:\s*Ref:\s*(.+))?"""), amount = 2, balance = 6, merchant = 3, reference = 7, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_merchant_payment", type = "merchant_payment", pattern = Regex("""^(\d{16}) Confirmed\. GHS([\d.]+) paid to (\d+) - ([A-Za-z][A-Za-z\s'.\-&]+?) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your new Telecel Cash balance is GHS([\d.]+)\. You were charged GHS([\d.]+)\. Your E-levy charge is GHS([\d.]+)\.(?:\s*Reference:\s*(.*?)\.)?"""), amount = 2, balance = 7, merchant = 4, reference = 10, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_airtime_purchase", type = "airtime_purchase", pattern = Regex("""^(\d{16}) Confirmed\. You bought GHS([\d.]+) of airtime for ([\d]+) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your Telecel Cash balance is GHS([\d.]+)\."""), amount = 2, balance = 6, merchant = 0, reference = 0, merchantLiteral = "TELECEL AIRTIME", referenceLiteral = null),
        rule(id = "telecel_bundle_purchase", type = "bundle_purchase", pattern = Regex("""^(\d{16}) confirmed\. Your bundle purchase request of GHS([\d.]+) on (\d{4}-\d{2}-\d{2}) has been received at (\d{2}:\d{2}:\d{2})\. Kindly wait for confirmation\. Your new Telecel Cash balance is GHS([\d.]+)\."""), amount = 2, balance = 5, merchant = 0, reference = 0, merchantLiteral = "TELECEL BUNDLE", referenceLiteral = null),
        rule(id = "telecel_airtime_received", type = "airtime_received", pattern = Regex("""^Transaction ID: (\d{16}) confirmed from 555\. You have received airtime of GHS([\d.]+) from ([\d]+) - ([A-Za-z][A-Za-z\s'.\-&]+?) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\."""), amount = 2, balance = 0, merchant = 4, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_cash_withdrawal", type = "cash_withdrawal", pattern = Regex("""^(\d{16}) Confirmed\. You have withdrawn GHS([\d.]+) from ([A-Z0-9]+) - (.+?) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your Telecel Cash balance is GHS([\d.]+)\."""), amount = 2, balance = 7, merchant = 4, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_bank_transfer", type = "bank_transfer", pattern = Regex("""^(\d{16}) Confirmed\. You have transferred GHS([\d.]+) to ([A-Za-z][A-Za-z\s]+ACCOUNT) - (\d+) - ([A-Za-z][A-Za-z\s'.\-&]+?) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your Telecel Cash balance is GHS([\d.]+)\. You were charged GHS([\d.]+).*?Reference:\s*(.*?)\."""), amount = 2, balance = 8, merchant = 5, reference = 10, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_deposit_received", type = "deposit_received", pattern = Regex("""^(\d{16}) Confirmed\. On (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2}), a deposit of GHS([\d.]+) was made to your account from (.+?)\s*\.\s*Your balance is GHS([\d.]+)\."""), amount = 4, balance = 6, merchant = 5, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_payment_received", type = "payment_received", pattern = Regex("""^(\d{16}) confirmed\. You have received GHS([\d.]+) as payment from ([A-Za-z][A-Za-z\s'.\-&]+?) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your new Telecel Cash balance is GHS([\d.]+)\."""), amount = 2, balance = 6, merchant = 3, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_loan_repayment", type = "loan_repayment", pattern = Regex("""^(\d{16}) confirmed\. You have paid (?:your|off) GHS([\d.]+) Ready Loan (.+?) (?:from CBG )?on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your new Telecel Cash balance is GHS([\d.]+)\."""), amount = 2, balance = 6, merchant = 0, reference = 0, merchantLiteral = "TELECEL READY LOAN", referenceLiteral = null),
        rule(id = "telecel_interest_received", type = "interest_received", pattern = Regex("""^Dear customer, you have received GHS([\d.]+) from Telecel Cash as interest earned.*?Your new (?:Telecel Cash )?balance is GHS([\d.]+)\."""), amount = 1, balance = 2, merchant = 0, reference = 0, merchantLiteral = "TELECEL CASH", referenceLiteral = null),
        rule(id = "telecel_wallet_balance", type = "wallet_balance", pattern = Regex("""^(\d{16}) Confirmed\. Your Telecel Cash wallet balance is GHS([\d.]+)\s*\."""), amount = 0, balance = 2, merchant = 0, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_airtime_received_v2", type = "airtime_received", pattern = Regex("""^(\d{16}) Confirmed\. Airtime of GHS([\d.]+) received from ([\d]+) - ([A-Za-z][A-Za-z\s'.\-&]+?) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Thank you for using Telecel Cash"""), amount = 2, balance = 0, merchant = 4, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_cash_withdrawal_v2", type = "cash_withdrawal", pattern = Regex("""^(\d{16}) Confirmed\. Cash withdrawal of GHS([\d.]+) at agent ([A-Z0-9]+) \(([^)]+)\) on (\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})\. Your new Telecel Cash balance is GHS([\d.]+)\. You were charged GHS([\d.]+)"""), amount = 2, balance = 7, merchant = 4, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_bank_transfer_v2", type = "bank_transfer", pattern = Regex("""^(\d{16}) Confirmed\. GHS([\d.]+) transferred to ([A-Za-z][A-Za-z\s'.\-&]+?) \(([^-]+?) - (\d+)\) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Fee GHS([\d.]+)\. New Telecel Cash balance: GHS([\d.]+)\. Ref:\s*(.+)"""), amount = 2, balance = 9, merchant = 3, reference = 10, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_deposit_received_v2", type = "deposit_received", pattern = Regex("""^(\d{16}) Confirmed\. A deposit of GHS([\d.]+) was received from ([\d]+) - ([A-Za-z][A-Za-z\s'.\-&]+?) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your new Telecel Cash balance is GHS([\d.]+)\."""), amount = 2, balance = 7, merchant = 4, reference = 0, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_payment_received_v2", type = "payment_received", pattern = Regex("""^(\d{16}) Confirmed\. Payment of GHS([\d.]+) received from ([A-Za-z][A-Za-z0-9\s'.\-&]+?) - (\d+) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your Telecel Cash balance is GHS([\d.]+)\. Ref:\s*(.+)"""), amount = 2, balance = 7, merchant = 3, reference = 8, merchantLiteral = null, referenceLiteral = null),
        rule(id = "telecel_loan_repayment_v2", type = "loan_repayment", pattern = Regex("""^(\d{16}) Confirmed\. GHS([\d.]+) debited for CBG Ready Loan repayment on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. New Telecel Cash balance: GHS([\d.]+)\."""), amount = 2, balance = 5, merchant = 0, reference = 0, merchantLiteral = "CBG READY LOAN", referenceLiteral = null),
        rule(id = "telecel_interest_received_v2", type = "interest_received", pattern = Regex("""^(\d{16}) Confirmed\. Interest of GHS([\d.]+) credited to your Telecel Cash wallet on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your new balance is GHS([\d.]+)\."""), amount = 2, balance = 5, merchant = 0, reference = 0, merchantLiteral = "TELECEL CASH", referenceLiteral = null),
        rule(id = "telecel_wallet_balance_v2", type = "wallet_balance", pattern = Regex("""^(\d{16}) Confirmed\. Your Telecel Cash balance as of (\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}) is GHS([\d.]+)\."""), amount = 0, balance = 4, merchant = 0, reference = 0, merchantLiteral = null, referenceLiteral = null),
        // Corpus variant: bank account transfer with a parenthetical fee split and E-Levy disclosure.
        // E-Levy remains metadata in the SMS; it is not folded into the transaction amount.
        rule(id = "telecel_bank_transfer_fee_breakdown", type = "bank_transfer", pattern = Regex("""^(\d{16}) Confirmed\. You have transferred GHS([\d.]+) to ([A-Za-z][A-Za-z\s]+ACCOUNT) - (\d+) - (.+?) on (\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})\. Your Telecel Cash balance is GHS([\d.]+)\. You were charged GHS([\d.]+) \(Telecel Cash fee GHS[\d.]+ \+ E-levy GHS[\d.]+\)\.\s*Reference:\s*(.*?)\.\s*Sendi k3k3!"""), amount = 2, balance = 8, merchant = 5, reference = 10, merchantLiteral = null, referenceLiteral = null),
    )
}
