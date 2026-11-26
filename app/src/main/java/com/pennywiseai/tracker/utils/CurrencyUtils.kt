package com.pennywiseai.tracker.utils

/** Ghana-only currency choices for manual entry and display preferences. */
object CurrencyUtils {
    fun sortCurrencies(currencies: List<String>): List<String> =
        currencies.filter { it.equals("GHS", ignoreCase = true) }.distinctBy { it.uppercase() }

    fun getAllSupportedCurrencies(): List<String> = listOf("GHS")
}
