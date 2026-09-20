package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.ghana.GhanaSmsExtractor
import java.math.BigDecimal

/**
 * Base class for the three Ghana mobile-money wallet parsers (MTN MoMo, Telecel Cash,
 * AirtelTigo Money).
 *
 * All three operators are the same *kind* of message source - a phone-number-identified wallet
 * that texts a running balance for every debit and credit - so they share one extraction engine
 * ([GhanaSmsExtractor]) driven by a per-operator slice of the specification. Only routing,
 * display name and the sample messages differ per subclass.
 *
 * Deliberate deviations from the generic [BankParser] path, all of them documented in
 * docs/adding-bank-parsers.md:
 *  - no `accountLast4`: the wallet *is* the account (the #682 lesson from TigoPesaParser -
 *    a per-SMS number fragments one wallet into thousands of accounts);
 *  - `isMobileWallet() = true` so the app still records the running balance against the single
 *    consolidated wallet account;
 *  - currency is pinned to GHS; amounts are never compared or summed across currencies.
 */
abstract class GhanaMobileMoneyParser internal constructor(
    /** Key into `GhanaPatterns.OPERATORS`, e.g. `mtn_momo`. */
    protected val operatorId: String,
    protected val bankDisplayName: String
) : BankParser() {

    override fun getBankName(): String = bankDisplayName

    override fun getCurrency(): String = "GHS"

    override fun isMobileWallet(): Boolean = true

    override fun canHandle(sender: String): Boolean = GhanaSmsExtractor.matchesSender(operatorId, sender)

    override fun extractAccountLast4(message: String): String? = null

    /** Fast content gate; the authoritative decision is [parse]. */
    override fun isTransactionMessage(message: String): Boolean = GhanaSmsExtractor.looksLikeTransaction(message)

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? =
        GhanaSmsExtractor.toParsedTransaction(operatorId, bankDisplayName, smsBody, sender, timestamp)

    // The granular hooks delegate to the same cascade, so a caller that uses extractAmount()
    // directly can never disagree with parse(). Each call re-runs extraction: these messages are
    // < 500 chars and the whole cascade is microseconds, and doing it here keeps the parser
    // stateless (and therefore safe to share across the SMS receiver and the test suite).
    override fun extractAmount(message: String): BigDecimal? = accepted(message)?.amountDecimal

    override fun extractTransactionType(message: String): TransactionType? = accepted(message)?.transactionType

    override fun extractMerchant(message: String, sender: String): String? = accepted(message, sender)?.merchant

    override fun extractBalance(message: String): BigDecimal? = accepted(message)?.balanceDecimal

    override fun extractReference(message: String): String? =
        accepted(message)?.let { it.userReference ?: it.transactionId }

    private fun accepted(message: String, sender: String = ""): GhanaSmsExtractor.Result? =
        GhanaSmsExtractor.extract(operatorId, message, sender).takeIf { it.accepted }
}
