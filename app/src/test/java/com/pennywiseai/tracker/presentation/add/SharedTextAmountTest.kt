package com.pennywiseai.tracker.presentation.add

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SharedTextAmountTest {

    private fun amount(text: String) = SharedTextAmount.extract(text)?.amount
    private fun currency(text: String) = SharedTextAmount.extract(text)?.currency

    @Test
    fun `currency-tagged amount wins over other numbers`() {
        assertEquals(BigDecimal("1200.50"), amount("Paid GHS 1,200.50 to Cafe on 03-10-26."))
        assertEquals(BigDecimal("450"), amount("You paid GH₵450 to Swiggy"))
        assertEquals(BigDecimal("99"), amount("GHS 99 debited from A/c XX1234"))
    }

    @Test
    fun `tag names the currency`() {
        assertNull(currency("Paid $12 to the app store"))
        assertEquals("GHS", currency("You paid ₵450 to Swiggy"))
        assertNull(currency("AED 75 at the mall"))
        assertNull(currency("450 swiggy"))
    }

    @Test
    fun `bare number is used only in a short note`() {
        assertEquals(BigDecimal("450"), amount("450 swiggy"))
        assertEquals(BigDecimal("80.5"), amount("chai 80.5"))
        assertNull(amount("Your order 12345 from the store was delivered to the address on file today"))
    }

    @Test
    fun `references, account numbers and dates are not amounts`() {
        assertNull(amount("UPI Ref 412345678901"))
        assertNull(amount("A/c XX1234"))
        assertNull(amount("lunch 03/10"))
        assertNull(amount("Rs 0 cashback"))
        assertNull(amount("hello"))
        assertNull(amount("Thanks to all our users 50 times over, see you at the next meetup"))
    }

    @Test
    fun `an amount is never truncated`() {
        assertNull(amount("GHS 12.345"))
        assertEquals(BigDecimal("450"), amount("Paid GHS 450."))
    }
}
