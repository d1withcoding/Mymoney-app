package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.ghana.GhanaNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Field normalisation for Ghana mobile-money SMS. Every expectation here is the Python reference
 * implementation's output on the same input (research/ghana-sms/tools/ghana_parse/normalize.py),
 * which is what makes these numbers usable in the paper: the normalisers are half of the
 * extraction quality, and a phone that reads '05-09-25' as 5 September while the evaluation reads
 * it as 9 May is a bug nobody would see in the regex table.
 */
class GhanaNormalizerTest {

    @Test
    fun `amounts are cleaned, scaled and sanity-checked`() {
        assertEquals("1299.50", GhanaNormalizer.normalizeAmount("1,299.5"))
        assertEquals("29.00", GhanaNormalizer.normalizeAmount("GH\u00a2 29.00"))
        assertEquals("1200.00", GhanaNormalizer.normalizeAmount("GHS1,200"))
        assertEquals("0.00", GhanaNormalizer.normalizeAmount("0.00"))
        // three decimals is not money, and a 14-digit run is a transaction id, not an amount
        assertNull(GhanaNormalizer.normalizeAmount("12.3456"))
        assertNull(GhanaNormalizer.normalizeAmount("12345678901234"))
    }

    @Test
    fun `date shapes normalise to ISO and impossible dates are refused`() {
        assertEquals("2025-09-10", GhanaNormalizer.normalizeDate("2025-09-10"))
        assertEquals("2025-12-31", GhanaNormalizer.normalizeDate("31/12/2025"))
        assertEquals("2025-09-05", GhanaNormalizer.normalizeDate("05-09-25"))
        assertEquals("2025-09-10", GhanaNormalizer.normalizeDate("10 Sep 2025"))
        assertEquals("2025-09-09", GhanaNormalizer.normalizeDate("Sept 9, 2025"))
        assertNull(GhanaNormalizer.normalizeDate("31/02/2025"))
        // day-first reading of a Ghana message; the ambiguity itself is reported to the harness
        assertTrue(GhanaNormalizer.dateIsAmbiguous("05/09/25"))
        assertFalse(GhanaNormalizer.dateIsAmbiguous("25/12/2025"))
    }

    @Test
    fun `times collapse to 24h hh-mm`() {
        assertEquals("13:51", GhanaNormalizer.normalizeTime("13:51:07"))
        assertEquals("13:51", GhanaNormalizer.normalizeTime("1:51 pm"))
        assertNull(GhanaNormalizer.normalizeTime("24:00"))
    }

    @Test
    fun `msisdns are localised and foreign or wrong-length numbers are rejected`() {
        assertEquals("0246834693", GhanaNormalizer.normalizePhone("+233 24 683 4693"))
        assertEquals("0241234567", GhanaNormalizer.normalizePhone("0241234567"))
        assertEquals("0241234567", GhanaNormalizer.normalizePhone("233241234567"))
        // an 11-digit South-Asian-shaped number is not a Ghana MSISDN
        assertNull(GhanaNormalizer.normalizePhone("01700000000"))
    }

    @Test
    fun `names lose delimiters, trailing clauses and parenthetical qualifiers`() {
        assertEquals("AMA TETTEH", GhanaNormalizer.normalizeName("AMA TETTEH"))
        assertEquals("JOHN MENSAH", GhanaNormalizer.normalizeName("JOHN MENSAH on 2025-09-10"))
        assertEquals("ECG", GhanaNormalizer.normalizeName("ECG (POWER)"))
        assertEquals("AMA TETTEH", GhanaNormalizer.normalizeName("AMA TETTEH -"))
        // a trailing label word the capture should not have kept
        assertEquals("KOFI", GhanaNormalizer.normalizeName("KOFI REF"))
        assertEquals("NADIR FURNITURE", GhanaNormalizer.nameKey("NADIR  FURNITURE!"))
    }

    @Test
    fun `ids accounts and channels are canonicalised`() {
        assertEquals("ANND4YZ0EMR", GhanaNormalizer.normalizeTrxId("annd4yz0emr"))
        assertNull(GhanaNormalizer.normalizeTrxId("!!"))
        assertEquals("4784906", GhanaNormalizer.normalizeAccount("47 84 906"))
        assertNull(GhanaNormalizer.normalizeAccount("  "))
        assertEquals("ussd", GhanaNormalizer.normalizeChannel("via *170#"))
        assertEquals("agent", GhanaNormalizer.normalizeChannel("at AGENT KWABENA OWUSU"))
        assertEquals("pos", GhanaNormalizer.normalizeChannel("via POS terminal"))
        assertEquals("pos", GhanaNormalizer.normalizeChannel("MoMoPay"))
        // 'MoMo Pay' with a space is the *service name*, not a channel statement; the reference
        // implementation deliberately does not guess here, so neither does the phone.
        assertEquals("other", GhanaNormalizer.normalizeChannel("MoMo Pay"))
        assertEquals("other", GhanaNormalizer.normalizeChannel("random"))
    }

    @Test
    fun `boilerplate never reaches field extraction but the core survives`() {
        val stripped = GhanaNormalizer.stripBoilerplate(
            "You have sent GHS 20.00 to KOJO. Thank you for using MTN MoMo. Dial *170# to manage your wallet."
        )
        assertEquals("You have sent GHS 20.00 to KOJO", stripped)
        // a message that is *only* boilerplate comes back unchanged rather than empty
        assertEquals("Dial *170# to manage your wallet.", GhanaNormalizer.stripBoilerplate("Dial *170# to manage your wallet."))
    }

    @Test
    fun `unicode whitespace is flattened so both regex engines see the same text`() {
        assertEquals(
            "You have purchased GHS 5.00's balance.",
            GhanaNormalizer.tidyText("You\nhave  purchased\u00a0GHS\u20095.00\u2019s  balance. ")
        )
        assertEquals("", GhanaNormalizer.tidyText(null))
    }
}
