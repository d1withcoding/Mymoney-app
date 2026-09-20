package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

/**
 * Tests for AirtelTigoMoney (Ghana mobile money).
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
class AirtelTigoMoneyParserTest {

    @TestFactory
    fun `airteltigo money parser handles wallet transfers and payments`(): List<DynamicTest> {
        val parser = AirtelTigoMoneyParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Public template sample (dev.to write-up, verbatim)",
                message = "TXN 987654321. GHS 30.00 sent to KWAME ASANTE (0271234567). Balance: GHS 120.50. Thank you for using AirtelTigo Money.",
                sender = "AIRTELTIGO",
                expected = ExpectedTransaction(
                    amount = BigDecimal("30.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "KWAME ASANTE",
                    reference = "987654321",
                    balance = BigDecimal("120.50"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "at.airtime.purchase  [airtime_purchase]",
                message = "You have purchased GH¢ 520.40 worth of airtime for 0702989994 for Telecel customer AirtelTigo Money balance: GH¢ 721.81.",
                sender = "ATMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("520.40"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("721.81"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "at.deposit.cash_in  [deposit]",
                message = "Deposit of GH₵ 1,174.02  successful. New balance: GH₵ 2,531.15. Thank you for using our services.",
                sender = "ATMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1174.02"),
                    currency = "GHS",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("2531.15"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "at.merchant.payment  [merchant_payment]",
                message = "Payment of GHS432.51 to TELECEL CASH AGENT (Paybill ID: 5701651) on 2025-01-25 at 20:33:37 successful. Fee charged: GHS 4.83. New balance: GHS5,071.67.",
                sender = "ATMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("432.51"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "TELECEL CASH AGENT",
                    balance = BigDecimal("5071.67"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "at.p2p_in.txn_prefixed  [p2p_transfer_in]",
                message = "TXN 12006366586. GH₵ 716.54 received from KWESI QUANSAH (056 266 6933). Balance: GH₵ 3,926.37. Thank you for using AirtelTigo Money.",
                sender = "ATMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("716.54"),
                    currency = "GHS",
                    type = TransactionType.INCOME,
                    merchant = "KWESI QUANSAH",
                    reference = "12006366586",
                    balance = BigDecimal("3926.37"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "at.p2p_out.txn_prefixed  [p2p_transfer_out]",
                message = "TXN 73733796681. GHS 23.14 sent to FIIFI ANNAN (0506602393). Thank you for using AirtelTigo Money.",
                sender = "ATMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.14"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "FIIFI ANNAN",
                    reference = "73733796681",
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "at.withdrawal.cash_out  [withdrawal]",
                message = "Withdrawal of GH₵ 2.00  successful. at AGENT KWABENA OWUSU. AirtelTigo Money balance: GH₵ 288.10. Txn ID: 12787805804 Not you? Call 100.",
                sender = "ATMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    reference = "12787805804",
                    balance = BigDecimal("288.10"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true
                )
            ),
            ParserTestCase(
                name = "Balance-query prompt",
                message = "Dial *800# to check your AirtelTigo Money balance. Keep your PIN secret. Never share it with anyone.",
                sender = "ATMONEY",
                shouldParse = false
            ),
            ParserTestCase(
                name = "KYC reminder",
                message = "Dear customer, your AT Money account requires KYC update. Visit any agent before 30/09/2025.",
                sender = "AIRTELTIGO",
                shouldParse = false
            )
        )

        val handleChecks = listOf(
            "ATMONEY" to true,
            "AT Money" to true,
            "AIRTELTIGO" to true,
            "AT TIGO MONEY" to true,
            "AT" to true,
            "MTN" to false,
            "TELECEL" to false,
            "AIRBNK" to false,
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "AirtelTigo Money"
        )
    }
}
