package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/**
 * Test suite for Ghanaian mobile money and utility parsers:
 * - MTN Mobile Money (MoMo)
 * - Telecel Cash (formerly Vodafone Cash)
 * - ECG Prepaid (Electricity Company of Ghana)
 */
class GhanaParserTest {

    // ========================================================================
    // MTN Mobile Money Tests
    // ========================================================================

    @TestFactory
    fun `MTN MoMo Ghana parser handles transactions`(): List<DynamicTest> {
        val parser = MTNMoMoGhanaParser()

        val cases = listOf(
            // --- Credit: received money ---
            ParserTestCase(
                name = "MTN MoMo - received money from person",
                message = "You have received GHS 100.00 from JOHN DOE. Your new balance is GHS 500.00. Reference: lunch money",
                sender = "MobileMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "GHS",
                    type = TransactionType.INCOME,
                    merchant = "JOHN DOE",
                    balance = BigDecimal("500.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Debit: payment to merchant ---
            ParserTestCase(
                name = "MTN MoMo - payment made",
                message = "Payment made for GHS 20.00 to ECG Prepaid. Fee charged was GHS 0.50. Your new balance is GHS 480.00",
                sender = "MobileMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("480.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Debit: cash out ---
            ParserTestCase(
                name = "MTN MoMo - cash out to agent",
                message = "Cash out GHS 200.00 to Agent 173570 - Central Book. Fee charged was GHS 1.50. Your new balance is GHS 300.00",
                sender = "MTN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "Central Book",
                    balance = BigDecimal("300.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Debit: transferred to phone number ---
            ParserTestCase(
                name = "MTN MoMo - transferred to phone number",
                message = "GHS 50.00 transferred to 0503527735. Your new balance is GHS 250.00",
                sender = "MTNGH",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "050 352 7735",
                    balance = BigDecimal("250.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Credit: credited with ---
            ParserTestCase(
                name = "MTN MoMo - credited with",
                message = "Your account was credited with GHS 1,500.00. Your new balance is GHS 2,000.00",
                sender = "MobileMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "GHS",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("2000.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Debit: deducted ---
            ParserTestCase(
                name = "MTN MoMo - deducted from account",
                message = "GHS 5.00 has been deducted from your account. Your new balance is GHS 495.00",
                sender = "MobileMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("495.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Debit: GH₵ symbol variant ---
            ParserTestCase(
                name = "MTN MoMo - GH₵ cedi symbol",
                message = "Payment made for GH₵ 35.00 to Melcom Shopping. Your new balance is GH₵ 465.00",
                sender = "MobileMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("35.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("465.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Rejection: promotional message ---
            ParserTestCase(
                name = "MTN MoMo - reject promotional message",
                message = "Enjoy free 2GB data with every GHS 10.00 airtime top-up! Dial *170# now. Promo ends 30th Nov.",
                sender = "MobileMoney",
                shouldParse = false,
            ),
            // --- Rejection: OTP ---
            ParserTestCase(
                name = "MTN MoMo - reject OTP",
                message = "Your OTP is 123456. Valid for 5 minutes. GHS amount verification.",
                sender = "MobileMoney",
                shouldParse = false,
            ),
            // --- Rejection: no GHS marker ---
            ParserTestCase(
                name = "MTN MoMo - reject message without GHS",
                message = "Your data bundle of 1.5GB is active. Valid until 30/10/2026.",
                sender = "MobileMoney",
                shouldParse = false,
            ),
            // --- Rejection: data bundle confirmation ---
            ParserTestCase(
                name = "MTN MoMo - reject data bundle purchase confirmation",
                message = "You have successfully bought 2GB data bundle for GHS 15.00. Valid for 30 days.",
                sender = "MobileMoney",
                shouldParse = false,
            ),
        )

        val handleCases = listOf(
            "MobileMoney" to true,
            "MTN" to true,
            "MTNMoMo" to true,
            "MTNGH" to true,
            "172001" to true,
            "1700" to true,
            "CO8813" to true,
            "Telecel" to false,
            "T-CASH" to false,
            "ECG" to false,
            "MPESA" to false,
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases, "MTN Mobile Money Ghana Parser")
    }

    // ========================================================================
    // Telecel Cash Tests
    // ========================================================================

    @TestFactory
    fun `Telecel Cash Ghana parser handles transactions`(): List<DynamicTest> {
        val parser = TelecelCashGhanaParser()

        val cases = listOf(
            // --- Credit: received money ---
            ParserTestCase(
                name = "Telecel Cash - received money",
                message = "You have received GHS 250.00 from AMA SERWAA. Your new balance is GHS 750.00",
                sender = "T-CASH",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "GHS",
                    type = TransactionType.INCOME,
                    merchant = "AMA SERWAA",
                    balance = BigDecimal("750.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Debit: sent to person ---
            ParserTestCase(
                name = "Telecel Cash - sent to person",
                message = "GHS 100.00 sent to 0502345678 - KOFI MENSAH. Fee charged was GHS 0.50. Your new balance is GHS 400.00",
                sender = "Telecel",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("400.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Debit: payment ---
            ParserTestCase(
                name = "Telecel Cash - payment made",
                message = "Payment made for GHS 45.00 to GOIL Petrol Station. Your new balance is GHS 355.00",
                sender = "TelecelCash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("355.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Debit: cash out ---
            ParserTestCase(
                name = "Telecel Cash - cash out",
                message = "Cash-out GHS 300.00 to Agent. Fee charged was GHS 2.00. Your new balance is GHS 198.00",
                sender = "Vodafone",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("198.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Credit: cash-in ---
            ParserTestCase(
                name = "Telecel Cash - cash-in received",
                message = "Cash-in received for GHS 500.00 from Agent. Your new balance is GHS 500.00",
                sender = "T-CASH",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "GHS",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("500.00"),
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                )
            ),
            // --- Rejection: promotional ---
            ParserTestCase(
                name = "Telecel Cash - reject promo",
                message = "Win a brand new car! Dial *700# to enjoy free GHS 5 airtime bonus.",
                sender = "Telecel",
                shouldParse = false,
            ),
        )

        val handleCases = listOf(
            "T-CASH" to true,
            "Telecel" to true,
            "TelecelCash" to true,
            "Vodafone" to true,
            "V-Cash" to true,
            "Cash" to true,
            "MobileMoney" to false,
            "MTN" to false,
            "ECG" to false,
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases, "Telecel Cash Ghana Parser")
    }

    // ========================================================================
    // ECG Prepaid Tests
    // ========================================================================

    @TestFactory
    fun `ECG Prepaid Ghana parser handles transactions`(): List<DynamicTest> {
        val parser = ECGPrepaidGhanaParser()

        val cases = listOf(
            // --- ECG recharge with meter name ---
            ParserTestCase(
                name = "ECG Prepaid - meter recharge with holder name",
                message = "Recharge request of GHs 50.00 for meter 12345678 (JOHN DOE) has been processed. Txn ID: ECQ01988150. Token: 1234 5678 9012 3456 7890. Units: 38.5 kWh",
                sender = "ECG",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN DOE",
                    reference = "ECQ01988150",
                )
            ),
            // --- ECG recharge with standard GHS ---
            ParserTestCase(
                name = "ECG Prepaid - recharge with GHS",
                message = "Recharge request of GHS 100.00 for meter 87654321 (AMA MENSAH) has been processed. Txn ID: ECQ02345678",
                sender = "ECG Prepaid",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "AMA MENSAH",
                    reference = "ECQ02345678",
                )
            ),
            // --- ECG with GH₵ variant ---
            ParserTestCase(
                name = "ECG Prepaid - GH₵ variant",
                message = "Recharge request of GH₵ 75.00 for meter 11111111 (KWAME ASANTE) has been processed. Txn ID: ECQ99887766",
                sender = "ECG",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "KWAME ASANTE",
                    reference = "ECQ99887766",
                )
            ),
        )

        val handleCases = listOf(
            "ECG" to true,
            "ECG Prepaid" to true,
            "MobileMoney" to false,
            "MTN" to false,
            "Telecel" to false,
        )

        return ParserTestUtils.runTestSuite(parser, cases, handleCases, "ECG Prepaid Ghana Parser")
    }

    // ========================================================================
    // Factory Dispatch Tests
    // ========================================================================

    @TestFactory
    fun `Ghana parsers register correctly in BankParserFactory`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "MTN Mobile Money",
                sender = "MobileMoney",
                currency = "GHS",
                message = "You have received GHS 100.00 from JOHN DOE. Your new balance is GHS 500.00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "GHS",
                    type = TransactionType.INCOME,
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                ),
                description = "MTN MoMo routes via factory (MobileMoney sender)"
            ),
            SimpleTestCase(
                bankName = "Telecel Cash",
                sender = "T-CASH",
                currency = "GHS",
                message = "Payment made for GHS 45.00 to Melcom. Your new balance is GHS 455.00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    isMobileWallet = true,
                    expectNullAccountLast4 = true,
                ),
                description = "Telecel Cash routes via factory (T-CASH sender)"
            ),
            SimpleTestCase(
                bankName = "ECG Prepaid",
                sender = "ECG",
                currency = "GHS",
                message = "Recharge request of GHs 50.00 for meter 12345678 (JOHN DOE) has been processed. Txn ID: ECQ01988150",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "GHS",
                    type = TransactionType.EXPENSE,
                    merchant = "JOHN DOE",
                    reference = "ECQ01988150",
                ),
                description = "ECG Prepaid routes via factory (ECG sender)"
            ),
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Ghana Mobile Money Factory Tests")
    }
}
