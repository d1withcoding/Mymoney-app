package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.parser.core.MessageTemplate
import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.TeachSelection
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SmsScanClassifierTest {

    private fun classifier(
        templates: Map<String, List<MessageTemplate>> = emptyMap(),
        recentSince: Long = 0L,
    ) = SmsScanClassifier(
        recentSinceMillis = recentSince,
        taughtTemplates = lazyOf(templates),
    )

    @Test
    fun `promotional and government senders are dropped`() {
        val body = "Rs.500.00 debited from A/c XX1234"

        assertTrue(classifier().classify(scannedSms("VM-HDFCBK-P", body)) is ParseResult.Discard)
        assertTrue(classifier().classify(scannedSms("VM-HDFCBK-G", body)) is ParseResult.Discard)
    }

    @Test
    fun `an unknown Ghana MoMo format is kept for review`() {
        val result = classifier().classify(scannedSms("GhanaWallet", "Your GHS 12.00 statement is ready"))

        assertTrue(result is ParseResult.StoreUnrecognized)
    }

    @Test
    fun `a person's message is dropped even with an amount`() {
        val result = classifier().classify(scannedSms("+10000000000", "I paid 12.50 EUR for lunch"))

        assertTrue(result is ParseResult.Discard)
    }

    @Test
    fun `an MTN MoMo debit is parsed in Ghana cedis`() {
        val result = classifier().classify(
            scannedSms("MobileMoney", "Payment made for GHS 20.00 to LOUISA BOATENG Current Balance: GHS 1037.64 . Available Balance: GHS 1037.64 Reference: n. Transaction ID: 76664093335. Fee charged: GHS0.00 Tax charged: 0.")
        )

        val parsed = (result as ParseResult.Transaction).parsed
        assertEquals(0, BigDecimal("20.00").compareTo(parsed.amount))
        assertEquals("GHS", parsed.currency)
    }

    @Test
    fun `Telecel wallet balance is saved as a balance update`() {
        val result = classifier().classify(
            scannedSms(
                "TelecelCash",
                "0000013325442259 Confirmed. Your Telecel Cash wallet balance is GHS510.56 . Sending money from Telecel Cash to Telecel Cash remains FREE on the Telecel Play App."
            )
        )

        assertTrue(result is ParseResult.BalanceUpdate)
        assertEquals("GHS", (result as ParseResult.BalanceUpdate).parsed.currency)
        assertEquals(BigDecimal("510.56"), result.parsed.balance)
    }

    @Test
    fun `a known bank's non-transaction SMS is dropped, not filed`() {
        val result = classifier().classify(scannedSms("MobileMoney", "123456 is your OTP for login. Do not share it with anyone."))

        assertTrue(result is ParseResult.Discard)
    }

    @Test
    fun `a taught sender's message is kept when the parser passes on it`() {
        val sender = "MobileMoney"
        val body = "Juice Bar bill of SAR 75.00 settled"
        val template = MessageTemplates.build(
            id = "t",
            senderKey = LearnedSendersStore.normalize(sender),
            body = "Juice Bar bill of SAR 50.00 settled",
            selection = TeachSelection(amount = 22..26),
            type = TransactionType.EXPENSE,
            currency = "SAR",
            bankName = "Zqt Bank",
        )!!
        val taught = classifier(templates = mapOf(template.senderKey to listOf(template)))
        val sms = scannedSms(sender, body)

        assertTrue(classifier().classify(sms) is ParseResult.Discard)
        assertTrue(taught.classify(sms) is ParseResult.StoreUnrecognized)
        assertTrue(taught.matchesTaughtTemplate(sms))
        assertFalse(taught.matchesTaughtTemplate(scannedSms(sender, "Your parcel has arrived")))
    }

}
