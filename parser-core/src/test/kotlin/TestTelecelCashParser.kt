package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

/**
 * Tests for TelecelCash (Ghana mobile money).
 *
 * These cases are parse *decisions*, not fixtures written to match the code by eye: the messages
 * and the expected values were produced by the Python reference implementation of the same
 * specification (research/ghana-sms) and copied here, so a divergence between the two languages
 * fails a test instead of hiding in a footnote. TestGhanaSeedCorpus extends the idea to the whole
 * corpus, and docs/parser-test-standards.md explains why every Ghana case is a dynamic test.
 *
 * PII: samples are synthetic or already-published template examples. Never paste a real message
 * from a handset into this file (CLAUDE.md constraint #4).
 */
class TelecelCashParserTest {

    @TestFactory
    fun `telecel cash parser handles wallet transfers and payments`(): List<DynamicTest> {
        val parser = TelecelCashParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Public template sample (dev.to write-up, verbatim)",
                message = "0000015061132227 Confirmed. You have sent GHS50.00 to 0241234567 - JOHN MENSAH on 2025-09-10 at 13:51:07. Your Telecel Cash balance is GHS654.03.",
                sender = "TELECEL CASH",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN MENSAH",
                    reference = "0000015061132227",
                    balance = BigDecimal("654.03"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "tc.airtime.purchase  [airtime_purchase]",
                message = "You have purchased GH₵ 861.66 worth of airtime for 0555326568 for MTN customer New balance is GH₵ 3,907.48.",
                sender = "TelecelCash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("861.66"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("3907.48"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "tc.p2p_in.short  [p2p_transfer_in]",
                message = "You have received GH₵ 15.28 from EBO AGYEMAN. New balance is GH₵ 4036.25. Fee charged: GH₵ 0.65.",
                sender = "TelecelCash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.28"),
                    currency = "GHS",
                    type = TransactionType.INCOME,
                    merchant = "EBO AGYEMAN",
                    balance = BigDecimal("4036.25"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "tc.paybill.payment  [paybill_payment]",
                message = "Payment of GH¢ 20.00 to WESTERN UNION on 2025-10-26 at 13:17:41 successful. Balance: GH¢ 3,732.64. Transaction ID: 0000013984589768 Thank you for using our services.",
                sender = "TelecelCash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "WESTERN UNION",
                    reference = "0000013984589768",
                    balance = BigDecimal("3732.64"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "Pay-request: nothing has moved yet",
                message = "KOFI MENSAH (0201111111) has requested GHS 150.00 from you. Approve the payment by dialling *110#.",
                sender = "TelecelCash",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Promotion",
                message = "Congratulations! You have won GHS 5,000 in our Telecel Cash promo. Register now on *110#.",
                sender = "TELECEL",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "TELECEL" to true,
            "Telecel Cash" to true,
            "TC CASH" to true,
            "VODAFONE CASH" to true,
            "VCASH" to true,
            "MTN" to false,
            "TIGOPESA" to false,
            "ATMONEY" to false,
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Telecel Cash"
        )
    }
}
