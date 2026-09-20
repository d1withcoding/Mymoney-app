package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.ghana.GhanaNormalizer
import com.pennywiseai.parser.core.ghana.GhanaSmsExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

/**
 * Runs the Kotlin Ghana engine over the whole evaluation corpus, twice, for two different reasons.
 *
 *  1. **Parity** ([ghanaEngineReproducesThePythonReference]) - the rows in
 *     `src/test/resources/ghana/ghana_python_predictions.tsv` are the *Python reference
 *     implementation's* decisions on the same messages, exported by
 *     `research/ghana-sms/tools/sync_kotlin.py`. The Kotlin engine has to reproduce them field for
 *     field, including which template won and what confidence was assigned. That is the test that
 *     backs the paper's claim that the evaluated prototype and the shipped parser are one system:
 *     two regex dialects and two hand-written normalisers drift easily, and "we wrote the same
 *     rules twice" is not evidence unless something checks it on every message.
 *  2. **Floors** ([kotlinEngineHoldsTheCorpusFloors]) - measured against the corpus *gold* labels
 *     instead of against Python, with regression budgets. Coverage and money fields must stay
 *     exact; softer fields carry a floor below the current measurement, so a spec edit that loses
 *     recall fails here even when both languages agree with each other (agreement is not
 *     correctness - the whole point of keeping gold labels at all).
 *
 * Regenerate the fixtures after any spec, corpus or trainer change:
 *
 *     python3 research/ghana-sms/tools/evaluate.py       # metrics for the paper
 *     python3 research/ghana-sms/tools/train.py          # classifier artifact + parity fixtures
 *     python3 research/ghana-sms/tools/sync_kotlin.py     # Kotlin patterns + these TSVs
 *
 * The corpus is synthetic (see research/ghana-sms/corpus/gold/README.md), so these numbers
 * describe engine behaviour on template-derived messages, not operator traffic. Real captures run
 * through the same two tests unchanged - that is the point of the fixture pipeline.
 */
class GhanaSeedCorpusTest {

    private val predictions: List<Prediction> by lazy {
        load(PREDICTIONS).map { Prediction.from(it) }
    }

    private val expectations: List<Gold> by lazy {
        load(EXPECTATIONS).map { Gold.from(it) }
    }

    @TestFactory
    fun `ghana engine reproduces the python reference on every corpus message`(): List<DynamicTest> {
        assertTrue(predictions.isNotEmpty(), "no predictions fixture - run tools/sync_kotlin.py")
        return predictions.map { row ->
            DynamicTest.dynamicTest("parity ${row.id}") {
                val result = GhanaSmsExtractor.extract(row.operator, row.body, row.sender)
                val shouldParse = row.accepted && row.confidence >= GhanaSmsExtractor.MIN_ACCEPT_CONFIDENCE
                if (!shouldParse) {
                    assertTrue(
                        !result.accepted || result.confidence < GhanaSmsExtractor.MIN_ACCEPT_CONFIDENCE,
                        "the reference did not book this message but Kotlin did (${row.rejectReason}); " +
                            "a spec change needs the fixtures regenerated, not this assertion relaxed"
                    )
                    return@dynamicTest
                }
                assertTrue(result.accepted, "Kotlin rejected a message the reference accepted: ${row.rejectReason}")
                assertEquals(row.confidenceText, String.format("%.3f", result.confidence), "confidence drift")
                assertEquals(row.mode, orEmpty(result.mode), "which tier answered changed")
                assertEquals(row.templateId, orEmpty(result.templateId), "template selection changed")
                assertEquals(money(row.amount), money(result.fields["amount"]), "amount drift")
                assertEquals(money(row.balance), money(result.fields["balance"]), "balance drift")
                assertEquals(row.date, orEmpty(result.fields["date"]), "date drift")
                assertEquals(row.direction, orEmpty(result.fields["direction"]), "direction drift")
                assertEquals(row.merchant, orEmpty(result.merchant), "merchant drift")
                assertEquals(row.txKind, orEmpty(result.fields["tx_kind"]), "tx_kind drift")
            }
        }
    }

    @Test
    fun `kotlin engine holds the corpus floors`() {
        var transactions = 0
        var booked = 0
        var amountExact = 0
        var balanceKnown = 0
        var balanceExact = 0
        var dateKnown = 0
        var dateExact = 0
        var merchantKnown = 0
        var merchantExact = 0
        var typeExact = 0
        var negatives = 0
        var negativesRefused = 0

        for (gold in expectations) {
            val result = GhanaSmsExtractor.extract(gold.operator, gold.body, gold.sender)
            if (!gold.isTransaction) {
                negatives++
                if (!result.accepted) negativesRefused++
                continue
            }
            transactions++
            val parsed = GhanaSmsExtractor.toParsedTransaction(
                operatorId = gold.operator,
                bankName = gold.operator,
                smsBody = gold.body,
                sender = gold.sender,
                timestamp = 0L
            )
            if (result.accepted && parsed != null) booked++
            if (money(gold.amount) == money(result.fields["amount"])) amountExact++
            if (gold.balance.isNotEmpty()) {
                balanceKnown++
                if (money(gold.balance) == money(result.fields["balance"])) balanceExact++
            }
            if (gold.date.isNotEmpty()) {
                dateKnown++
                if (gold.date == result.fields["date"]) dateExact++
            }
            if (gold.merchant.isNotEmpty()) {
                merchantKnown++
                val key = GhanaNormalizer.nameKey(result.merchant)
                if (key != null && key == GhanaNormalizer.nameKey(gold.merchant)) merchantExact++
            }
            val expectedType = runCatching { TransactionType.valueOf(gold.transactionType) }.getOrNull()
            if (expectedType != null && expectedType == result.transactionType) typeExact++
        }

        println(
            "Ghana corpus (Kotlin vs gold): booked=${pct(booked, transactions)} " +
                "amount=${pct(amountExact, transactions)} balance=${pct(balanceExact, balanceKnown)} " +
                "date=${pct(dateExact, dateKnown)} merchant=${pct(merchantExact, merchantKnown)} " +
                "type=${pct(typeExact, transactions)} negativesRefused=${pct(negativesRefused, negatives)}"
        )

        assertTrue(transactions > 0, "corpus fixture is empty - run tools/sync_kotlin.py")
        assertTrue(
            booked.toDouble() / transactions >= 0.98,
            "coverage fell below 0.98 (${pct(booked, transactions)}): a spec edit lost messages"
        )
        assertTrue(amountExact.toDouble() / transactions >= 0.98, "amount exactness fell below 0.98")
        if (balanceKnown > 0) {
            assertTrue(balanceExact.toDouble() / balanceKnown >= 0.98, "balance exactness fell below 0.98")
        }
        if (dateKnown > 0) {
            assertTrue(dateExact.toDouble() / dateKnown >= 0.90, "date exactness fell below 0.90")
        }
        if (merchantKnown > 0) {
            assertTrue(merchantExact.toDouble() / merchantKnown >= 0.85, "merchant exactness fell below 0.85")
        }
        assertTrue(typeExact.toDouble() / transactions >= 0.85, "transaction-type exactness fell below 0.85")
        if (negatives > 0) {
            assertTrue(
                negativesRefused.toDouble() / negatives >= 0.90,
                "more than 10% of non-transaction controls were booked as transactions"
            )
        }
    }

    private fun load(resource: String): List<List<String>> {
        val stream = javaClass.classLoader.getResourceAsStream(resource)
            ?: throw IllegalStateException(
                "Missing test fixture '$resource'. Generate it with " +
                    "python3 research/ghana-sms/tools/sync_kotlin.py"
            )
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line -> line.split('\t').map { it.trim() } }
                .toList()
        }
    }

    private fun money(value: String?): BigDecimal? =
        value?.takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() }

    private fun orEmpty(value: String?): String = value ?: ""

    private fun pct(part: Int, whole: Int): String =
        if (whole == 0) "n/a" else String.format("%.3f", part.toDouble() / whole) + " ($part/$whole)"

    /** id, operator, sender, body, accepted, reason, confidence, mode, template, amount, date, direction, merchant, balance, kind */
    private data class Prediction(
        val id: String,
        val operator: String,
        val sender: String,
        val body: String,
        val accepted: Boolean,
        val rejectReason: String,
        val confidence: Double,
        val confidenceText: String,
        val mode: String,
        val templateId: String,
        val amount: String,
        val date: String,
        val direction: String,
        val merchant: String,
        val balance: String,
        val txKind: String
    ) {
        companion object {
            fun from(cells: List<String>) = Prediction(
                id = cells[0], operator = cells[1], sender = cells.getOrNull(2) ?: "",
                body = cells.getOrNull(3) ?: "", accepted = cells.getOrNull(4) == "1",
                rejectReason = cells.getOrNull(5) ?: "",
                confidence = cells.getOrNull(6)?.toDoubleOrNull() ?: 0.0,
                confidenceText = cells.getOrNull(6) ?: "0.000",
                mode = cells.getOrNull(7) ?: "", templateId = cells.getOrNull(8) ?: "",
                amount = cells.getOrNull(9) ?: "", date = cells.getOrNull(10) ?: "",
                direction = cells.getOrNull(11) ?: "", merchant = cells.getOrNull(12) ?: "",
                balance = cells.getOrNull(13) ?: "", txKind = cells.getOrNull(14) ?: ""
            )
        }
    }

    /** id, is_transaction, operator, sender, body, tags, amount, date, direction, merchant, balance, transaction_type */
    private data class Gold(
        val id: String,
        val isTransaction: Boolean,
        val operator: String,
        val sender: String,
        val body: String,
        val tags: String,
        val amount: String,
        val date: String,
        val direction: String,
        val merchant: String,
        val balance: String,
        val transactionType: String
    ) {
        companion object {
            fun from(cells: List<String>) = Gold(
                id = cells[0], isTransaction = cells.getOrNull(1) == "1",
                operator = cells.getOrNull(2) ?: "", sender = cells.getOrNull(3) ?: "",
                body = cells.getOrNull(4) ?: "", tags = cells.getOrNull(5) ?: "",
                amount = cells.getOrNull(6) ?: "", date = cells.getOrNull(7) ?: "",
                direction = cells.getOrNull(8) ?: "", merchant = cells.getOrNull(9) ?: "",
                balance = cells.getOrNull(10) ?: "", transactionType = cells.getOrNull(11) ?: ""
            )
        }
    }

    companion object {
        private const val PREDICTIONS = "ghana/ghana_python_predictions.tsv"
        private const val EXPECTATIONS = "ghana/ghana_corpus_expectations.tsv"
    }
}
