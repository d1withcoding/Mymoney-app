package com.pennywiseai.tracker.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankNotificationConfigTest {

    @Test
    fun `no non-Ghana banking apps are allowlisted for auto import`() {
        assertTrue(BankNotificationConfig.allowedPackages.isEmpty())
        assertFalse(BankNotificationConfig.isAllowed("com.huntington.m"))
    }

    @Test
    fun `title is kept in front of the body`() {
        assertEquals("Card Transaction Refund\nAmount: AED 5",
            BankNotificationConfig.withTitle("Card Transaction Refund", "Amount: AED 5"))
        // Not duplicated when the body already carries it, and no stray newline when absent.
        assertEquals("Card Transaction\nAmount: AED 5",
            BankNotificationConfig.withTitle("Card Transaction", "Card Transaction\nAmount: AED 5"))
        assertEquals("Amount: AED 5", BankNotificationConfig.withTitle(null, "Amount: AED 5"))
        assertEquals("Title only", BankNotificationConfig.withTitle("Title only", ""))
    }

    @Test
    fun `unlisted packages are not read`() {
        assertFalse(BankNotificationConfig.isAllowed("com.whatsapp"))
    }
}
