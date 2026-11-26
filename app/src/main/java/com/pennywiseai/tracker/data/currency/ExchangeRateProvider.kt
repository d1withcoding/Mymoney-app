package com.pennywiseai.tracker.data.currency

import java.math.BigDecimal

/**
 * Interface for exchange rate providers
 */
interface ExchangeRateProvider {
    suspend fun fetchExchangeRate(fromCurrency: String, toCurrency: String): BigDecimal?
    suspend fun fetchAllExchangeRates(baseCurrency: String): Map<String, BigDecimal>?
    suspend fun fetchAllExchangeRatesWithMetadata(baseCurrency: String): ExchangeRateResponseWithMetadata?
    fun getProviderName(): String
    suspend fun getSupportedCurrencies(): List<String>
}

/**
 * Extended response data class that includes the full API response with timestamps
 */
data class ExchangeRateResponseWithMetadata(
    val rates: Map<String, BigDecimal>,
    val nextUpdateTimeUnix: Long,
    val lastUpdateTimeUnix: Long,
    val provider: String,
    val baseCurrency: String
)

/**
 * Factory for creating exchange rate providers
 */
object ExchangeRateProviderFactory {
    fun createProvider(): ExchangeRateProvider {
        return OfflineExchangeRateProvider
    }
}

/** Network-free provider used by the offline fork. Cached local rates remain readable. */
private object OfflineExchangeRateProvider : ExchangeRateProvider {
    override suspend fun fetchExchangeRate(fromCurrency: String, toCurrency: String): BigDecimal? =
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) BigDecimal.ONE else null

    override suspend fun fetchAllExchangeRates(baseCurrency: String): Map<String, BigDecimal>? = null

    override suspend fun fetchAllExchangeRatesWithMetadata(baseCurrency: String): ExchangeRateResponseWithMetadata? = null

    override fun getProviderName(): String = "Offline"

    override suspend fun getSupportedCurrencies(): List<String> = emptyList()
}
