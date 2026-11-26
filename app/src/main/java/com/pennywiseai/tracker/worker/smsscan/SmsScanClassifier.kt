package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.parser.core.MessageTemplate
import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.bank.BankParser
import com.pennywiseai.parser.core.bank.BankParserFactory
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore

private const val TAG = "OptimizedSmsReaderWorker"

/**
 * Sealed result from the parse stage — each outcome is a distinct type,
 * impossible states are unrepresentable.
 */
internal sealed class ParseResult {
    abstract val sms: ScannedSms

    /** Promo/gov/unknown sender — discard silently. */
    data class Discard(override val sms: ScannedSms) : ParseResult()

    /** Unknown -T/-S sender — save to unrecognized table for review. */
    data class StoreUnrecognized(override val sms: ScannedSms) : ParseResult()

    /** Ghana mobile-wallet balance notification (not a transaction row). */
    data class BalanceUpdate(override val sms: ScannedSms, val parsed: ParsedTransaction) : ParseResult()

    /** Normal debit/credit — ready to be written to the transactions table. */
    data class Transaction(
        override val sms: ScannedSms,
        val parsed: ParsedTransaction
    ) : ParseResult()
}

/**
 * The scan's parse stage. Runs on several parser coroutines at once, so it must stay
 * free of DB access and hold no mutable state beyond the concurrent parser cache and
 * the synchronized [taughtTemplates] lazy.
 */
internal class SmsScanClassifier(
    private val recentSinceMillis: Long,
    taughtTemplates: Lazy<Map<String, List<MessageTemplate>>>,
) {
    private val taughtTemplates by taughtTemplates

    companion object {
        private val GHANA_AMOUNT = Regex("""(?:GHS|GH₵|₵)\s?\.?\d|\d[\d.,]*\s?(?:GHS|GH₵|₵)\b""", RegexOption.IGNORE_CASE)

        /**
         * Whether an SMS no parser claims belongs in Unrecognized SMS (the user's way to report a bank).
         * Keep Ghana mobile-money messages whose sender isn't recognized by the parser, while
         * excluding unrelated currency notifications and ordinary person-to-person SMS.
         */
        internal fun isUnrecognizedCandidate(sender: String, body: String): Boolean {
            val upper = sender.trim().uppercase()
            if (upper in setOf("MOBILEMONEY", "MTNMOMO", "T-CASH", "TELECELCASH", "TCASH", "555")) return true
            return upper.any { it.isLetter() } && GHANA_AMOUNT.containsMatchIn(body)
        }

        /**
         * A taught sender's message that nothing else keeps (#279): its taught shape, or recent
         * money wording that may be a new format, as the live receiver keeps it. An older one is
         * history rather than a change, and the scan's own cleanup would drop it anyway.
         */
        internal fun taughtSenderKeeps(
            templates: List<MessageTemplate>,
            sender: String,
            body: String,
            timestamp: Long,
            recentSince: Long
        ): Boolean {
            val supported = templates.filter { it.currency.equals("GHS", ignoreCase = true) }
            return MessageTemplates.matchAny(supported, body, sender, timestamp) != null ||
                (supported.isNotEmpty() && timestamp > recentSince && MessageTemplates.looksLikeMoney(sender, body))
        }
    }

    /** O(1) sender-to-parser lookup. Factory is only called once per unique sender string. */
    private class ParserHolder(val parsers: List<BankParser>)
    private val parserCache = java.util.concurrent.ConcurrentHashMap<String, ParserHolder>(256)
    private fun cachedParsers(sender: String): List<BankParser> =
        parserCache.computeIfAbsent(sender) { ParserHolder(BankParserFactory.getParsers(sender)) }.parsers

    /** Plain fun — no suspend overhead on the hot path. */
    fun classify(sms: ScannedSms): ParseResult {
        val upper = sms.sender.uppercase()

        if (upper.endsWith("-P") || upper.endsWith("-G"))
            return ParseResult.Discard(sms)

        val parsers = cachedParsers(sms.sender)
        val parser = parsers.firstOrNull()
            ?: return if (isUnrecognizedCandidate(sms.sender, sms.body) || keptForTaughtSender(sms))
                ParseResult.StoreUnrecognized(sms)
            else
                ParseResult.Discard(sms)

        // Shared senders (M-Pesa KE/TZ/MZ): first candidate whose content parses.
        val parsed = parsers.firstNotNullOfOrNull { it.parse(sms.body, sms.sender, sms.timestamp) }
            ?: return if (keptForTaughtSender(sms)) ParseResult.StoreUnrecognized(sms) else ParseResult.Discard(sms)

        if (parsed.type == com.pennywiseai.parser.core.TransactionType.BALANCE_UPDATE) {
            return ParseResult.BalanceUpdate(sms, parsed)
        }

        return ParseResult.Transaction(sms, parsed)
    }

    private fun keptForTaughtSender(sms: ScannedSms): Boolean {
        val templates = taughtTemplates[LearnedSendersStore.normalize(sms.sender)]
            ?.filter { it.currency.equals("GHS", ignoreCase = true) }
            ?: return false
        return taughtSenderKeeps(templates, sms.sender, sms.body, sms.timestamp, recentSinceMillis)
    }

    /** The user taught this shape (#279), so it's offered for confirming whatever else would have dropped it. */
    fun matchesTaughtTemplate(sms: ScannedSms): Boolean {
        val templates = taughtTemplates[LearnedSendersStore.normalize(sms.sender)]
            ?.filter { it.currency.equals("GHS", ignoreCase = true) }
            ?: return false
        return MessageTemplates.matchAny(templates, sms.body, sms.sender, sms.timestamp) != null
    }

}
