package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction

/**
 * Factory for creating bank-specific parsers based on SMS sender.
 */
object BankParserFactory {

    private val parsers = listOf(
        MtnMomoParser(),  // MTN Mobile Money (Ghana)
        TelecelCashParser()  // Telecel Cash (Ghana)
    )

    /**
     * Returns the appropriate bank parser for the given sender.
     * Returns null if no specific parser is found.
     */
    fun getParser(sender: String): BankParser? {
        return parsers.firstOrNull { it.canHandle(sender) }
    }

    /**
     * Returns every parser whose canHandle matches the sender.
     * Multiple parsers can share a sender (e.g. M-Pesa Kenya/Tanzania/Mozambique);
     * content-aware dispatch in [parse] picks the right one.
     */
    fun getParsers(sender: String): List<BankParser> = parsers.filter { it.canHandle(sender) }

    /**
     * Content-aware dispatch: tries every canHandle-matching parser and returns the
     * first non-null parse(). This un-shadows parsers that share a sender ID — each
     * parser gates its own parse() by message content and returns null otherwise.
     */
    fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? =
        getParsers(sender).firstNotNullOfOrNull { it.parse(smsBody, sender, timestamp) }

    /**
     * Returns the bank parser for the given bank name.
     * Returns null if no specific parser is found.
     */
    fun getParserByName(bankName: String): BankParser? {
        return parsers.firstOrNull { it.getBankName() == bankName }
    }

    /**
     * Returns all available bank parsers.
     */
    fun getAllParsers(): List<BankParser> = parsers

    /**
     * Checks if the sender belongs to any known bank.
     */
    fun isKnownBankSender(sender: String): Boolean {
        return parsers.any { it.canHandle(sender) }
    }
}
