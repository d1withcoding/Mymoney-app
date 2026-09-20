package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

/**
 * Tests for MtnMoMo (Ghana mobile money).
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
class MtnMoMoParserTest {

    @TestFactory
    fun `mtn momo parser handles wallet transfers and payments`(): List<DynamicTest> {
        val parser = MtnMoMoParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Public template sample (dev.to write-up, verbatim)",
                message = "Payment made for GHS 50.00 to DAVID BOATENG. Current Balance: GHS 450.00. Reference: school fees. Transaction ID: 76712833868. Fee charged: GHS 0.50",
                sender = "MTN-MOMO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "DAVID BOATENG",
                    reference = "school fees",
                    balance = BigDecimal("450.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "mtn.airtime.purchase  [airtime_purchase]",
                message = "You have purchased GHS200.00 worth of airtime for 0705499286 for Telecel customer New balance is GHS2366.74. Not you? Call 100.",
                sender = "MTN-MOMO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("2366.74"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "mtn.data_bundle.purchase  [data_bundle]",
                message = "You have successfully purchased a 1GB data bundle for GH₵ 15.00 for 0247044411 New balance is GH₵ 4847.36. Thank you for using our services.",
                sender = "MTN-MOMO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("4847.36"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "mtn.p2p_out.make_payment  [p2p_transfer_out]",
                message = "Payment made for GH₵20.00 to AKOSUA BEDIAKO. Balance: GH₵6,752.39. Fee charged: GH₵ 0.70. Trans ID: 12065693802",
                sender = "MTN-MOMO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "AKOSUA BEDIAKO",
                    reference = "12065693802",
                    balance = BigDecimal("6752.39"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "mtn.paybill.payment  [paybill_payment]",
                message = "Payment of GH¢ 1,595.50 to TELECEL GHANA (Business ID: 6835015) The transaction was not successful. Fee charged: GH¢ 14.00. E-Levy: GH¢ 23.93. New balance is GH¢ 306.89. Trans ID: 1N21UNB0TF3",
                sender = "MTN-MOMO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1595.50"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "TELECEL GHANA",
                    reference = "1N21UNB0TF3",
                    balance = BigDecimal("306.89"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "mtn.withdrawal.agent  [withdrawal]",
                message = "You have withdrawn GH¢200.00 from your MoMo account on 24/02/2025 at 05:28 PM. E-Levy: GH¢ 3.00. Current Balance: GH¢7,829.71.",
                sender = "MTN-MOMO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("7829.71"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "OTP is never a transaction",
                message = "Your MTN MoMo OTP is 448120. It expires in 5 minutes. Never share it with anyone.",
                sender = "MTN-MOMO",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Third-party top-up credit notice moves no money out of my wallet",
                message = "Airtime of GHS 29.00 is ready for your number. Top up more at any agent.",
                sender = "MTN-MOMO",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "MTN" to true,
            "MTN-MOMO" to true,
            "MOMO" to true,
            "MTN MoMo PSB" to true,
            "MO MONEY" to true,
            "TELECEL" to false,
            "ATMONEY" to false,
            "AIRBNK" to false,
            "HDFCBK" to false,
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "MTN Mobile Money"
        )
    }
}
