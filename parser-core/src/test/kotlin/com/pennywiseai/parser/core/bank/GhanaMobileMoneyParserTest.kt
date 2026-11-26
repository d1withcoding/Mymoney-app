package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import java.math.BigDecimal

class GhanaMobileMoneyParserTest {
    @TestFactory
    fun `Ghana sample messages parse through the factory for documentation`() = ParserTestUtils.runTestSuite(
        MtnMomoParser(),
        listOf(
            ParserTestCase(
                name = "MTN MoMo transfer sent",
                message = "Payment made for GHS 20.00 to LOUISA BOATENG Current Balance: GHS 1037.64 . Available Balance: GHS 1037.64 Reference: n. Transaction ID: 76664093335. Fee charged: GHS0.00 Tax charged: 0.",
                sender = "MobileMoney",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20.00"), currency = "GHS", type = TransactionType.EXPENSE,
                    merchant = "LOUISA BOATENG", reference = "n", balance = BigDecimal("1037.64"), isMobileWallet = true
                )
            )
        )
    ) + ParserTestUtils.runTestSuite(
        TelecelCashParser(),
        listOf(
            ParserTestCase(
                name = "Telecel Cash bank transfer",
                message = "0000017141657970 Confirmed. GHS1957.00 transferred to PETER BAAH GYIMAH (ABSA ACCOUNT - 9050045678123) on 2025-12-25 at 09:20:41. Fee GHS8.57. New Telecel Cash balance: GHS921.45. Ref: indomie",
                sender = "TelecelCash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1957.00"), currency = "GHS", type = TransactionType.EXPENSE,
                    merchant = "PETER BAAH GYIMAH", reference = "indomie", balance = BigDecimal("921.45"), isMobileWallet = true
                )
            )
        )
    )

    @TestFactory
    fun `mtn templates each parse their reference example`(): List<DynamicTest> {
        val parser = MtnMomoParser()
        return listOf(
            DynamicTest.dynamicTest("mtn_transfer_sent_v1") {
                val parsed = parser.parseUsingRule("mtn_transfer_sent_v1", "Payment made for GHS 20.00 to LOUISA BOATENG Current Balance: GHS 1037.64 . Available Balance: GHS 1037.64 Reference: n. Transaction ID: 76664093335. Fee charged: GHS0.00 Tax charged: 0.", "MobileMoney")
                assertNotNull(parsed, "template mtn_transfer_sent_v1 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("20.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "LOUISA BOATENG", reference = "n", balance = BigDecimal("1037.64")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_transfer_sent_with_phone") {
                val parsed = parser.parseUsingRule("mtn_transfer_sent_with_phone", "Payment made for GHS 62.50 to ROYAL ROSANT VENTURES 233241880380. Current Balance: GHS 7,411.57. Available Balance: GHS 7,411.57. Reference: 2. Transaction ID: 76919434871. Fee charged: GHS 0.62 TAX charged: GHS 0.00.", "MobileMoney")
                assertNotNull(parsed, "template mtn_transfer_sent_with_phone did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("62.50"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "ROYAL ROSANT VENTURES", reference = "2", balance = BigDecimal("7411.57")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_transfer_sent_v2") {
                val parsed = parser.parseUsingRule("mtn_transfer_sent_v2", "Payment made for GHS 35.00 to ERNESTINA ANDOH. Current Balance: GHS 1,037.64. Available Balance: GHS 1,037.64. Reference: 1. Transaction ID: 76289975115. Fee charged: GHS 0.00 TAX charged: GHS 0.00.", "MobileMoney")
                assertNotNull(parsed, "template mtn_transfer_sent_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("35.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "ERNESTINA ANDOH", reference = "1", balance = BigDecimal("1037.64")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_transfer_received") {
                val parsed = parser.parseUsingRule("mtn_transfer_received", "Payment received for GHS 41.00 from SAMUEL NANA AGYEI ASANTE Current Balance: GHS 7445.59 . Available Balance: GHS 7445.59. Reference: 1. Transaction ID: 76982067554. TRANSACTION FEE: 0.00", "MobileMoney")
                assertNotNull(parsed, "template mtn_transfer_received did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("41.00"), currency = "GHS", type = TransactionType.INCOME, merchant = "SAMUEL NANA AGYEI ASANTE", reference = "1", balance = BigDecimal("7445.59")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_money_transfer_deposit") {
                val parsed = parser.parseUsingRule("mtn_money_transfer_deposit", "Money Transfer Deposit received for GHS 66.00 from NANA YAA AFERWAAH AFRAM BUAH Token: 998395013143 Transaction ID: 52070900912. TRANSACTION FEE IS 0", "MobileMoney")
                assertNotNull(parsed, "template mtn_money_transfer_deposit did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("66.00"), currency = "GHS", type = TransactionType.INCOME, merchant = "NANA YAA AFERWAAH AFRAM BUAH", reference = "998395013143"))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_cash_out") {
                val parsed = parser.parseUsingRule("mtn_cash_out", "Cash Out made for GHS200.00 to BOAFO MICROFINANCE LIMITED. Current Balance: GHS572.58 Financial Transaction Id: 76785458115. Cash-out fee is charged automatically from your MTN MoMo wallet. Please do not pay any fees to the Agent. Thank you for using MTN MobileMoney. Fee charged: GHS2.00.", "MobileMoney")
                assertNotNull(parsed, "template mtn_cash_out did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("200.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "BOAFO MICROFINANCE LIMITED", balance = BigDecimal("572.58")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_airtime_purchase") {
                val parsed = parser.parseUsingRule("mtn_airtime_purchase", "Your payment of GHS 6.00 to MTN AIRTIME has been completed at 2026-03-02 12:20:20. Your new balance: GHS 1031.64. Fee was GHS 0.00 Tax was GHS -. Reference: -. Financial Transaction Id: 76322126636. External Transaction Id: 76322126636.", "MobileMoney")
                assertNotNull(parsed, "template mtn_airtime_purchase did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("6.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "MTN AIRTIME", balance = BigDecimal("1031.64")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_merchant_payment") {
                val parsed = parser.parseUsingRule("mtn_merchant_payment", "Your payment of GHS 5.00 to FIT N FINE GYM CENTER LIMITED has been completed at 2026-03-09 07:36:18. Reference: . Your new balance: GHS 47.95. Fee was GHS 0.50 Tax charged: GHS0. Financial Transaction Id: 76807586590. External Transaction Id: -.", "MobileMoney")
                assertNotNull(parsed, "template mtn_merchant_payment did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("5.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "FIT N FINE GYM CENTER LIMITED", balance = BigDecimal("47.95")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_service_payment") {
                val parsed = parser.parseUsingRule("mtn_service_payment", "Payment for GHS2700.00 to CAL Bank Push.Current Balance: GHS 4745.59. Transaction Id: 76986046852. Fee charged: GHS0.00,Tax Charged 0.", "MobileMoney")
                assertNotNull(parsed, "template mtn_service_payment did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("2700.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "CAL Bank Push", balance = BigDecimal("4745.59")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_cash_in") {
                val parsed = parser.parseUsingRule("mtn_cash_in", "Cash In received for GHS 90.00 from APPLE CARE PLUS . Current Balance GHS 1025.14 Available Balance GHS 1025.14. Transaction ID: 76999661422. Fee charged: GHS 0. Cash in (Deposit) is a free transaction on MTN Mobile Money. Please do not pay any fees for it.", "MobileMoney")
                assertNotNull(parsed, "template mtn_cash_in did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("90.00"), currency = "GHS", type = TransactionType.INCOME, merchant = "APPLE CARE PLUS", balance = BigDecimal("1025.14")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_cash_out_v2") {
                val parsed = parser.parseUsingRule("mtn_cash_out_v2", "You have cashed out GHS 186.69 at APOA CONSTRUCTION (0544319278). Available Balance: GHS 813.02. Transaction ID: 78639541234. Fee charged: GHS 1.50. Thank you for using MTN MobileMoney.", "MobileMoney")
                assertNotNull(parsed, "template mtn_cash_out_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("186.69"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "APOA CONSTRUCTION", balance = BigDecimal("813.02")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("mtn_bundle_purchase_v2") {
                val parsed = parser.parseUsingRule("mtn_bundle_purchase_v2", "Your payment of GHS 20.00 to MTN BUNDLE has been completed at 2025-08-19 19:40:12. Your new balance: GHS 1,895.88. Fee was GHS 0.00 Tax was GHS 0.00. Financial Transaction Id: 76990123456.", "MobileMoney")
                assertNotNull(parsed, "template mtn_bundle_purchase_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("20.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "MTN BUNDLE", balance = BigDecimal("1895.88")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
        )
    }

    @TestFactory
    fun `telecel templates each parse their reference example`(): List<DynamicTest> {
        val parser = TelecelCashParser()
        return listOf(
            DynamicTest.dynamicTest("telecel_transfer_sent") {
                val parsed = parser.parseUsingRule("telecel_transfer_sent", "0000012289051596 Confirmed. GHS33.50 sent to 0594372553 - ISAAC AKANCHALA on MTN MOBILE MONEY on 2026-03-07 at 09:52:16. Your Telecel Cash balance is GHS259.18. You were charged GHS0.00. Your E-levy charge is GHS0.00. Sending money from Telecel Cash to Telecel Cash remains FREE on the Telecel Play App. Download the App https://bit.ly/TelecelPlayGhana and continue to enjoy the convenience. Reference: 1. Sendi k3k3!", "TelecelCash")
                assertNotNull(parsed, "template telecel_transfer_sent did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("33.50"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "ISAAC AKANCHALA", reference = "1", balance = BigDecimal("259.18")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_transfer_received_mtn") {
                val parsed = parser.parseUsingRule("telecel_transfer_received_mtn", "0000012280187846 Confirmed. You have received GHS252.00 from MTN MOBILE MONEY with transaction reference: Transfer From: 233240590913-CECILIA OWUSU on 2026-03-05 at 15:04:34. Your Telecel Cash balance is GHS293.68. Ref: support money", "TelecelCash")
                assertNotNull(parsed, "template telecel_transfer_received_mtn did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("252.00"), currency = "GHS", type = TransactionType.INCOME, merchant = "CECILIA OWUSU", reference = "support money", balance = BigDecimal("293.68")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_transfer_received_telecel") {
                val parsed = parser.parseUsingRule("telecel_transfer_received_telecel", "0000014271426460 Confirmed. You have received GHS68.95 from BOAKYE APPIAH ABDUL-MUMIN on TELECEL CASH on 2025-08-29 at 08:47:14. Your Telecel Cash balance is GHS1001.02. Ref: electricity", "TelecelCash")
                assertNotNull(parsed, "template telecel_transfer_received_telecel did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("68.95"), currency = "GHS", type = TransactionType.INCOME, merchant = "BOAKYE APPIAH ABDUL-MUMIN", reference = "electricity", balance = BigDecimal("1001.02")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_merchant_payment") {
                val parsed = parser.parseUsingRule("telecel_merchant_payment", "0000012255152871 Confirmed. GHS5.05 paid to 735000 - EXPRESSPAY on 2026-03-03 at 10:41:30. Your new Telecel Cash balance is GHS456.68. You were charged GHS0.00. Your E-levy charge is GHS0.00. Reference: 90e6d0cdx2f1a.. Sendi k3k3!", "TelecelCash")
                assertNotNull(parsed, "template telecel_merchant_payment did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("5.05"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "EXPRESSPAY", reference = "90e6d0cdx2f1a", balance = BigDecimal("456.68")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_airtime_purchase") {
                val parsed = parser.parseUsingRule("telecel_airtime_purchase", "0000011919258876 Confirmed. You bought GHS41.00 of airtime for 233507995144 on 2026-01-31 at 08:37:00. Your Telecel Cash balance is GHS87.05.", "TelecelCash")
                assertNotNull(parsed, "template telecel_airtime_purchase did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("41.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "TELECEL AIRTIME", balance = BigDecimal("87.05")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_bundle_purchase") {
                val parsed = parser.parseUsingRule("telecel_bundle_purchase", "0000012419176900 confirmed. Your bundle purchase request of GHS15.00 on 2026-03-18 has been received at 13:12:36. Kindly wait for confirmation. Your new Telecel Cash balance is GHS205.11.", "TelecelCash")
                assertNotNull(parsed, "template telecel_bundle_purchase did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("15.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "TELECEL BUNDLE", balance = BigDecimal("205.11")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_airtime_received") {
                val parsed = parser.parseUsingRule("telecel_airtime_received", "Transaction ID: 0000012289051596 confirmed from 555. You have received airtime of GHS1.00 from 233507995144 - ALEX MARFO APPIAH on 2026-03-06 at 10:40:54.", "TelecelCash")
                assertNotNull(parsed, "template telecel_airtime_received did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("1.00"), currency = "GHS", type = TransactionType.INCOME, merchant = "ALEX MARFO APPIAH"))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_cash_withdrawal") {
                val parsed = parser.parseUsingRule("telecel_cash_withdrawal", "0000012028321647 Confirmed. You have withdrawn GHS40.00 from A25736 - FOEJOE ENTERPRISE on 2026-02-10 at 11:26:46. Your Telecel Cash balance is GHS42.03.", "TelecelCash")
                assertNotNull(parsed, "template telecel_cash_withdrawal did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("40.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "FOEJOE ENTERPRISE", balance = BigDecimal("42.03")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_bank_transfer") {
                val parsed = parser.parseUsingRule("telecel_bank_transfer", "0000012039324241 Confirmed. You have transferred GHS40.00 to STANBIC BANK ACCOUNT - 9040013268597 - ALEX MARFO APPIAH on 2026-02-11 at 12:27:52. Your Telecel Cash balance is GHS1.53. You were charged GHS0.50 (Telecel Cash fee GHS0.50 + E-levy GHS0.00). Reference: Apple Music. Sendi k3k3!", "TelecelCash")
                assertNotNull(parsed, "template telecel_bank_transfer did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("40.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "ALEX MARFO APPIAH", reference = "Apple Music", balance = BigDecimal("1.53")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_deposit_received") {
                val parsed = parser.parseUsingRule("telecel_deposit_received", "0000012255758052 Confirmed. On 2026-03-03 at 11:44:15, a deposit of GHS100.00 was made to your account from FOEJOE ENTERPRISE . Your balance is GHS556.68. Sending money from Telecel Cash...", "TelecelCash")
                assertNotNull(parsed, "template telecel_deposit_received did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("100.00"), currency = "GHS", type = TransactionType.INCOME, merchant = "FOEJOE ENTERPRISE", balance = BigDecimal("556.68")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_payment_received") {
                val parsed = parser.parseUsingRule("telecel_payment_received", "0000012269962269 confirmed. You have received GHS387.72 as payment from EXPRESSPAY on 2026-03-04 at 16:47:33. Your new Telecel Cash balance is GHS944.40.", "TelecelCash")
                assertNotNull(parsed, "template telecel_payment_received did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("387.72"), currency = "GHS", type = TransactionType.INCOME, merchant = "EXPRESSPAY", balance = BigDecimal("944.40")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_loan_repayment") {
                val parsed = parser.parseUsingRule("telecel_loan_repayment", "0000012220022826 confirmed. You have paid your GHS6.60 Ready Loan default charge on 2026-02-28 at 10:40:29. Your new Telecel Cash balance is GHS46.73.", "TelecelCash")
                assertNotNull(parsed, "template telecel_loan_repayment did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("6.60"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "TELECEL READY LOAN", balance = BigDecimal("46.73")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_interest_received") {
                val parsed = parser.parseUsingRule("telecel_interest_received", "Dear customer, you have received GHS0.86 from Telecel Cash as interest earned on your mobile money wallet for the period October 2025 to December 2025. Your new balance is GHS2.39.", "TelecelCash")
                assertNotNull(parsed, "template telecel_interest_received did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("0.86"), currency = "GHS", type = TransactionType.INCOME, merchant = "TELECEL CASH", balance = BigDecimal("2.39")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_wallet_balance") {
                val parsed = parser.parseUsingRule("telecel_wallet_balance", "0000012241711788 Confirmed. Your Telecel Cash wallet balance is GHS461.73 . Sending money from Telecel Cash to Telecel Cash remains FREE on the Telecel Play App.", "TelecelCash")
                assertNotNull(parsed, "template telecel_wallet_balance did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal.ZERO, currency = "GHS", type = TransactionType.BALANCE_UPDATE, balance = BigDecimal("461.73")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_airtime_received_v2") {
                val parsed = parser.parseUsingRule("telecel_airtime_received_v2", "0000014091720586 Confirmed. Airtime of GHS56.00 received from 0505864434 - ASAMOAH EFFAH on 2025-08-04 at 08:25:19. Thank you for using Telecel Cash.", "TelecelCash")
                assertNotNull(parsed, "template telecel_airtime_received_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("56.00"), currency = "GHS", type = TransactionType.INCOME, merchant = "ASAMOAH EFFAH"))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_cash_withdrawal_v2") {
                val parsed = parser.parseUsingRule("telecel_cash_withdrawal_v2", "0000013632744514 Confirmed. Cash withdrawal of GHS217.36 at agent A44678 (TEN ELEVEN MART) on 2025-08-30 06:25:37. Your new Telecel Cash balance is GHS812.00. You were charged GHS0.00.", "TelecelCash")
                assertNotNull(parsed, "template telecel_cash_withdrawal_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("217.36"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "TEN ELEVEN MART", balance = BigDecimal("812.00")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_bank_transfer_v2") {
                val parsed = parser.parseUsingRule("telecel_bank_transfer_v2", "0000017141657970 Confirmed. GHS1957.00 transferred to PETER BAAH GYIMAH (ABSA ACCOUNT - 9050045678123) on 2025-12-25 at 09:20:41. Fee GHS8.57. New Telecel Cash balance: GHS921.45. Ref: indomie", "TelecelCash")
                assertNotNull(parsed, "template telecel_bank_transfer_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("1957.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "PETER BAAH GYIMAH", reference = "indomie", balance = BigDecimal("921.45")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_deposit_received_v2") {
                val parsed = parser.parseUsingRule("telecel_deposit_received_v2", "0000014746327887 Confirmed. A deposit of GHS112.73 was received from 0507076617 - ADJOA ABDUL-MUMIN on 2025-12-02 at 00:21:45. Your new Telecel Cash balance is GHS456.78.", "TelecelCash")
                assertNotNull(parsed, "template telecel_deposit_received_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("112.73"), currency = "GHS", type = TransactionType.INCOME, merchant = "ADJOA ABDUL-MUMIN", balance = BigDecimal("456.78")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_payment_received_v2") {
                val parsed = parser.parseUsingRule("telecel_payment_received_v2", "0000011704510927 Confirmed. Payment of GHS423.18 received from EXPRESSPAY - 735000 on 2025-08-30 at 14:50:27. Your Telecel Cash balance is GHS812.00. Ref: INV-1234", "TelecelCash")
                assertNotNull(parsed, "template telecel_payment_received_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("423.18"), currency = "GHS", type = TransactionType.INCOME, merchant = "EXPRESSPAY", reference = "INV-1234", balance = BigDecimal("812.00")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_loan_repayment_v2") {
                val parsed = parser.parseUsingRule("telecel_loan_repayment_v2", "0000012841311402 Confirmed. GHS45.00 debited for CBG Ready Loan repayment on 2025-09-20 at 14:30:00. New Telecel Cash balance: GHS123.45. Thank you for being a valued customer.", "TelecelCash")
                assertNotNull(parsed, "template telecel_loan_repayment_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("45.00"), currency = "GHS", type = TransactionType.EXPENSE, merchant = "CBG READY LOAN", balance = BigDecimal("123.45")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_interest_received_v2") {
                val parsed = parser.parseUsingRule("telecel_interest_received_v2", "0000017757623385 Confirmed. Interest of GHS2.27 credited to your Telecel Cash wallet on 2025-11-24 at 07:22:13. Your new balance is GHS500.00.", "TelecelCash")
                assertNotNull(parsed, "template telecel_interest_received_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal("2.27"), currency = "GHS", type = TransactionType.INCOME, merchant = "TELECEL CASH", balance = BigDecimal("500.00")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
            DynamicTest.dynamicTest("telecel_wallet_balance_v2") {
                val parsed = parser.parseUsingRule("telecel_wallet_balance_v2", "0000013161496147 Confirmed. Your Telecel Cash balance as of 2025-09-13 08:14:22 is GHS1867.76.", "TelecelCash")
                assertNotNull(parsed, "template telecel_wallet_balance_v2 did not parse")
                val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(amount = BigDecimal.ZERO, currency = "GHS", type = TransactionType.BALANCE_UPDATE, balance = BigDecimal("1867.76")))
                org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
                org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
            },
        )
    }

    @TestFactory
    fun `Telecel corpus bank transfer fee breakdown is parsed without folding levy into amount`(): List<DynamicTest> {
        val parser = TelecelCashParser()
        val sms = "0000013342650186 Confirmed. You have transferred GHS1521.00 to CALBANK ACCOUNT - 4050234567890 - EFUA DONKOR on 2026-02-15 at 23:11:25. Your Telecel Cash balance is GHS380.68. You were charged GHS3.35 (Telecel Cash fee GHS3.35 + E-levy GHS0.00).\nReference: birthday.\nSendi k3k3!"
        return listOf(DynamicTest.dynamicTest("Telecel bank transfer with fee split") {
            val parsed = parser.parseUsingRule("telecel_bank_transfer_fee_breakdown", sms, "TelecelCash")
            assertNotNull(parsed)
            val failures = ParserTestUtils.validateResult(parsed!!, ExpectedTransaction(
                amount = BigDecimal("1521.00"),
                currency = "GHS",
                type = TransactionType.EXPENSE,
                merchant = "EFUA DONKOR",
                reference = "birthday",
                balance = BigDecimal("380.68")
            ))
            org.junit.jupiter.api.Assertions.assertTrue(failures.isEmpty(), failures.joinToString())
            org.junit.jupiter.api.Assertions.assertTrue(parsed.isMobileWallet)
        })
    }

}
