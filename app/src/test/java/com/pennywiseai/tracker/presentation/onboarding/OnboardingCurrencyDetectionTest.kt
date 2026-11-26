package com.pennywiseai.tracker.presentation.onboarding

import com.pennywiseai.tracker.utils.CurrencyUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/** Candidates are SIM country, network country, device locale country — in that order. */
class OnboardingCurrencyDetectionTest {

    private val supported = CurrencyUtils.getAllSupportedCurrencies()

    @Test
    fun `Ghana SIM country currency is picked`() {
        assertEquals("GHS", detectCurrency(listOf("gh", "gh", "US"), supported))
    }

    @Test
    fun `unsupported SIM currency falls back to the device locale`() {
        // Swiss SIM (CHF isn't supported), no network country, en_US locale.
        assertEquals("GHS", detectCurrency(listOf("us", "", "US"), supported))
    }

    @Test
    fun `network country is tried before the device locale`() {
        assertEquals("GHS", detectCurrency(listOf(null, "gh", "US"), supported))
    }

    @Test
    fun `nothing usable falls back to the default base currency`() {
        assertEquals("GHS", detectCurrency(listOf(null, "", "US"), supported))
    }
}
