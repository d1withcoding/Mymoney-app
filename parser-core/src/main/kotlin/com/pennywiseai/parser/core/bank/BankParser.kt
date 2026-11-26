package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction

/** Shared interface for the Ghana mobile-money parsers. */
abstract class BankParser {
    abstract fun getBankName(): String

    abstract fun canHandle(sender: String): Boolean

    open fun getCurrency(): String = "GHS"

    open fun isMobileWallet(): Boolean = false

    /** Returns null unless this provider parser recognizes the complete message template. */
    open fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? = null
}
