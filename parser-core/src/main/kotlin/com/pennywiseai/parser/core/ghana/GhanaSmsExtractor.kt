package com.pennywiseai.parser.core.ghana

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * The Ghana mobile-money extraction cascade (Objective 2 of the study).
 *
 * Three stages, in order, mirroring `research/ghana-sms/tools/ghana_parse/engine.py`:
 *
 *  0. [GhanaNormalizer.tidyText]   whitespace / unicode hygiene for concatenated multi-part SMS
 *  1. skip rules                    OTP / promo / pay-request / top-up notice -> rejected
 *  2. operator routing              sender ID first, body-marker vote as fallback
 *  3. whole-message templates       every template is tried; the best weighted field yield wins
 *  4. per-field fallback            operator-specific patterns, then the shared ones, to fill
 *                                   the gaps the template left (the "hybrid" in hybrid cascade)
 *  5. normalise + type inference    amounts, dates, phones, names; tx_kind -> [TransactionType]
 *
 * Field extraction in stage 4 runs on the message *without* operator boilerplate, while the
 * skip rules and templates still see the whole message: a promo footer is evidence for what a
 * message IS but noise for what it SAYS.
 *
 * Confidence is deliberately conservative: capped at 0.95 because nothing here is a language
 * model, and tiered (template hit >= 0.55, field-fallback <= 0.55) so that a review threshold
 * such as "auto-accept at 0.75, otherwise show the user what was guessed" has meaning. The
 * numbers are documented in the paper's methods section, not invented here.
 */
object GhanaSmsExtractor {

    /** Below this, the parser declines the message rather than writing a guess to the ledger. */
    const val MIN_ACCEPT_CONFIDENCE: Double = 0.45

    /** Weighted field yield: amount+balance is worth more than a reference string. */
    private val FIELD_WEIGHTS: Map<String, Int> = mapOf(
        "amount" to 4, "direction" to 3, "tx_kind" to 3, "balance" to 2, "date" to 2,
        "counterparty_name" to 2, "paybill_name" to 2, "fee" to 1, "levy" to 1, "trx_id" to 1,
        "reference" to 1, "counterparty_phone" to 1, "paybill_account" to 1, "time" to 1,
        "status" to 1, "channel" to 1
    )

    private val REQUIRED_FIELDS = listOf("amount", "direction")

    /** Spec field order - the fallback pass iterates this, so both languages try patterns in the same sequence. */
    private val SPEC_FIELDS = listOf(
        "amount", "fee", "levy", "balance", "trx_id", "reference", "counterparty_name",
        "counterparty_phone", "paybill_name", "paybill_account", "date", "time", "status",
        "channel", "tx_kind", "direction"
    )

    private val MONEY_HINT = Regex("(?:GHS|GH[¢₵]|₵)\\s*[0-9]", RegexOption.IGNORE_CASE)

    data class Result(
        val text: String,
        val sender: String,
        val accepted: Boolean = false,
        val rejectReason: String? = null,
        val operatorId: String? = null,
        val operatorDisplayName: String? = null,
        val route: String? = null,
        val templateId: String? = null,
        val mode: String? = null,
        val fields: Map<String, String> = emptyMap(),
        val rawFields: Map<String, String> = emptyMap(),
        val provenance: Map<String, String> = emptyMap(),
        val confidence: Double = 0.0,
        val score: Int = 0,
        val ambiguousDate: Boolean = false
    ) {
        /** What the app should record when the message states no status token at all. */
        val effectiveStatus: String get() = fields["status"] ?: "SUCCESS"

        /**
         * The app-facing type. Derived from the transaction *kind* when one was identified; a
         * wallet transfer with no recognisable kind still has to be booked, so the money
         * direction decides (IN -> INCOME, OUT -> EXPENSE). This is the one place where the
         * Kotlin record carries more than the Python result: the harness reports a null
         * transaction_type and counts it as a miss, while the ledger must have a value.
         */
        val transactionType: TransactionType?
            get() {
                val kind = fields["tx_kind"]?.lowercase()
                val name = if (kind == null) null else GhanaPatterns.KIND_TO_TRANSACTION_TYPE[kind]
                val mapped = name?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
                return mapped ?: when (fields["direction"]) {
                    "IN" -> TransactionType.INCOME
                    "OUT" -> TransactionType.EXPENSE
                    else -> null
                }
            }

        val merchant: String?
            get() = fields["paybill_name"] ?: fields["counterparty_name"]

        /** Counterparty MSISDN, already normalised to the local 0XXXXXXXXX shape. */
        val counterpartyPhone: String? get() = fields["counterparty_phone"]
        val paybillAccount: String? get() = fields["paybill_account"]
        val transactionId: String? get() = fields["trx_id"]
        val userReference: String? get() = fields["reference"]
        val amountDecimal: BigDecimal? get() = fields["amount"]?.let { runCatching { BigDecimal(it) }.getOrNull() }
        val balanceDecimal: BigDecimal? get() = fields["balance"]?.let { runCatching { BigDecimal(it) }.getOrNull() }
    }

    // -------------------------------------------------------------------------- public API

    /**
     * Cheap content gate for [com.pennywiseai.parser.core.bank.BankParser.isTransactionMessage].
     * Not the decision - [extract] is - just the fast "this cannot be a transaction" filter that
     * keeps obviously irrelevant traffic out of the template sweep.
     */
    fun looksLikeTransaction(message: String?): Boolean {
        val text = GhanaNormalizer.tidyText(message)
        if (text.isEmpty()) return false
        if (skipReason(text) != null) return false
        return MONEY_HINT.containsMatchIn(text)
    }

    /** True when this sender ID is one of the configured Ghana mobile-money short codes. */
    fun matchesSender(operatorId: String, sender: String): Boolean =
        GhanaPatterns.OPERATORS[operatorId]?.matchesSender(sender) == true

    /** Full cascade. [operatorHint] is only consulted when sender AND marker routing fail. */
    fun extract(operatorHint: String?, body: String, sender: String = ""): Result {
        val text = GhanaNormalizer.tidyText(body)
        if (text.isEmpty()) return Result(text = text, sender = sender, rejectReason = "empty message")

        skipReason(text)?.let {
            return Result(text = text, sender = sender, rejectReason = "skip rule: $it")
        }

        val (operator, route) = routeOperator(text, sender, operatorHint)
        if (operator == null) {
            return Result(text = text, sender = sender, route = route, rejectReason = "no operator matched")
        }

        val core = GhanaNormalizer.stripBoilerplate(text)
        val best = bestTemplate(text, operator)

        var mode: String
        var templateId: String? = null
        var score = 0
        val fields = LinkedHashMap<String, String>()
        val provenance = LinkedHashMap<String, String>()

        if (best != null) {
            mode = "template"
            templateId = best.template.id
            fields.putAll(best.fields)
            best.fields.keys.forEach { provenance[it] = best.template.id }
            score = best.score
            if (best.template.kind.isNotEmpty()) {
                fields["tx_kind"] = best.template.kind
                provenance["tx_kind"] = best.template.id
            }
            // Gap-fill: whatever the template did not capture is still worth recovering.
            val (filled, filledProvenance) = fieldFallback(core, operator, fields.keys.toSet())
            fields.putAll(filled)
            provenance.putAll(filledProvenance)
            score += filled.keys.sumOf { FIELD_WEIGHTS[it] ?: 1 }
        } else {
            val (fallback, fallbackProvenance) = fieldFallback(core, operator, emptySet())
            if (fallback.isEmpty()) {
                return Result(
                    text = text, sender = sender, operatorId = operator.id,
                    operatorDisplayName = operator.displayName, route = route,
                    mode = "none", rejectReason = "no template matched and no field pattern hit"
                )
            }
            mode = "field_fallback"
            fields.putAll(fallback)
            provenance.putAll(fallbackProvenance)
            score = fallback.keys.sumOf { FIELD_WEIGHTS[it] ?: 1 }
        }

        // A paybill/merchant payment has ONE counterparty. If the fallback re-captured the
        // merchant the template already bound, keeping both would double-count one string.
        if (fields["paybill_name"] != null && fields["counterparty_name"] != null &&
            GhanaNormalizer.nameKey(fields["paybill_name"]) == GhanaNormalizer.nameKey(fields["counterparty_name"])
        ) {
            fields.remove("counterparty_name")
            provenance.remove("counterparty_name")
        }

        val raw: Map<String, String> = LinkedHashMap(fields)
        val normalized = LinkedHashMap<String, String>()
        for ((name, value) in fields) {
            normalizeField(name, value)?.let { if (it.isNotEmpty()) normalized[name] = it }
        }

        if (normalized["amount"].isNullOrEmpty()) {
            return Result(
                text = text, sender = sender, operatorId = operator.id, operatorDisplayName = operator.displayName,
                route = route, mode = "none", templateId = templateId, rawFields = raw, fields = normalized,
                provenance = provenance, score = score, rejectReason = "amount missing after normalization"
            )
        }
        if (normalized["tx_kind"].isNullOrEmpty()) {
            inferKind(text, normalized)?.let {
                normalized["tx_kind"] = it
                if (!provenance.containsKey("tx_kind")) provenance["tx_kind"] = "kind_inference"
            }
        }
        if (normalized["direction"].isNullOrEmpty()) {
            // Kind first: it comes from the matched template and beats a keyword sweep.
            val fromKind = normalized["tx_kind"]?.let { GhanaPatterns.KIND_TO_DIRECTION[it] }
            val direction = fromKind ?: inferDirection(text)
            if (direction != null) {
                normalized["direction"] = direction
                if (!provenance.containsKey("direction")) {
                    provenance["direction"] = if (fromKind != null) "kind_inference" else "direction_rules"
                }
            }
        }
        if (normalized["status"].isNullOrEmpty()) {
            // Absence of a marker stays 'unknown' here rather than being silently written as
            // success: status is measured against what the message actually says.
            inferStatus(text)?.let {
                normalized["status"] = it
                if (!provenance.containsKey("status")) provenance["status"] = "status_rules"
            }
        }

        val confidence = confidence(mode, route, normalized)
        val accepted = REQUIRED_FIELDS.all { !normalized[it].isNullOrEmpty() }
        val result = Result(
            text = text,
            sender = sender,
            accepted = accepted,
            rejectReason = if (accepted) null else "required fields missing: " +
                REQUIRED_FIELDS.filter { normalized[it].isNullOrEmpty() }.joinToString(","),
            operatorId = operator.id,
            operatorDisplayName = operator.displayName,
            route = route,
            templateId = templateId,
            mode = mode,
            fields = normalized,
            rawFields = raw,
            provenance = provenance,
            confidence = confidence,
            score = score,
            ambiguousDate = GhanaNormalizer.dateIsAmbiguous(raw["date"])
        )
        return result
    }

    /**
     * The app-facing conversion. Returns null whenever the message should not be booked, which
     * keeps the same `parse(...) -> null` contract the other wallet parsers use.
     *
     * `ParsedTransaction` has no slot for fee, levy, channel, status or transaction id, so this
     * path intentionally drops them; the study's harness keeps all sixteen fields
     * (`research/ghana-sms/tools/ghana_parse/engine.py`). `accountLast4` stays null on purpose:
     * a mobile-money wallet IS the account, and minting a per-SMS last4 would fragment the
     * wallet into one account per message (see the #682 note on TigoPesaParser).
     */
    fun toParsedTransaction(
        operatorId: String,
        bankName: String,
        smsBody: String,
        sender: String,
        timestamp: Long
    ): ParsedTransaction? {
        val result = extract(operatorId, smsBody, sender)
        if (!result.accepted || result.confidence < MIN_ACCEPT_CONFIDENCE) return null
        val amount = result.amountDecimal ?: return null
        val type = result.transactionType ?: return null
        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = result.merchant,
            // The user's own reference ("school fees") is the more useful label than an opaque
            // transaction id; the id remains available in the raw fields for dedup/debug.
            reference = result.userReference ?: result.transactionId,
            accountLast4 = null,
            balance = result.balanceDecimal,
            creditLimit = null,
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = bankName,
            isFromCard = false,
            currency = GhanaPatterns.CURRENCY,
            fromAccount = null,
            toAccount = null,
            isMobileWallet = true
        )
    }

    // ------------------------------------------------------------------------------ internals

    private data class TemplateHit(val template: GhanaTemplate, val fields: Map<String, String>, val score: Int)

    private fun routeOperator(
        text: String,
        sender: String,
        hint: String?
    ): Pair<GhanaOperatorSpec?, String> {
        for (operator in GhanaPatterns.OPERATORS.values) {
            if (operator.matchesSender(sender)) return operator to "sender"
        }
        var best: GhanaOperatorSpec? = null
        var bestHits = 0
        for (operator in GhanaPatterns.OPERATORS.values) {
            val hits = operator.markerHits(text)
            if (hits > bestHits) {
                best = operator
                bestHits = hits
            }
        }
        if (best != null) return best to "markers:$bestHits"
        if (hint != null) {
            GhanaPatterns.OPERATORS[hint]?.let { return it to "parser-hint" }
        }
        return null to "none"
    }

    private fun skipReason(text: String): String? {
        for (rule in GhanaPatterns.SKIP_RULES) {
            if (rule.regex.containsMatchIn(text)) return rule.reason
        }
        return null
    }

    /** Try every template; keep the highest weighted yield, priority breaks ties. */
    private fun bestTemplate(text: String, operator: GhanaOperatorSpec): TemplateHit? {
        var best: TemplateHit? = null
        var bestPriority = Int.MAX_VALUE
        for (template in operator.templates) {
            val captured = template.match(text) ?: continue
            if (GhanaNormalizer.normalizeAmount(captured["amount"]) == null) continue
            var score = 0
            for (field in captured.keys) score += FIELD_WEIGHTS[field] ?: 1
            val hit = TemplateHit(template, captured, score)
            val better = best == null || hit.score > best!!.score ||
                (hit.score == best!!.score && template.priority < bestPriority)
            if (better) {
                best = hit
                bestPriority = template.priority
            }
        }
        return best
    }

    private fun fieldFallback(
        text: String,
        operator: GhanaOperatorSpec,
        skip: Set<String>
    ): Pair<Map<String, String>, Map<String, String>> {
        val fields = LinkedHashMap<String, String>()
        val provenance = LinkedHashMap<String, String>()
        for (name in SPEC_FIELDS) {
            if (name == "status" || name == "tx_kind" || name == "direction") continue
            if (name in skip) continue
            val patterns = (operator.fields[name] ?: emptyList()) + (GhanaPatterns.SHARED_FIELDS[name] ?: emptyList())
            for (pattern in patterns) {
                val value = pattern.match(text)
                if (value != null && value.isNotBlank()) {
                    fields[name] = value.trim()
                    provenance[name] = pattern.id
                    break
                }
            }
        }
        return fields to provenance
    }

    private fun normalizeField(name: String, value: String?): String? {
        if (value == null) return null
        return when (name) {
            "amount", "fee", "levy", "balance" -> GhanaNormalizer.normalizeAmount(value)
            "date" -> GhanaNormalizer.normalizeDate(value)
            "time" -> GhanaNormalizer.normalizeTime(value)
            "counterparty_phone" -> GhanaNormalizer.normalizePhone(value)
            "counterparty_name", "paybill_name" -> GhanaNormalizer.normalizeName(value)
            "paybill_account" -> GhanaNormalizer.normalizeAccount(value)
            "trx_id" -> GhanaNormalizer.normalizeTrxId(value)
            "channel" -> if (value in CHANNEL_LABELS) value else GhanaNormalizer.normalizeChannel(value)
            "status", "direction" -> value.trim().uppercase().ifEmpty { null }
            "tx_kind" -> value.trim().lowercase().replace(' ', '_').ifEmpty { null }
            else -> value.trim().ifEmpty { null }
        }
    }

    private fun inferDirection(text: String): String? {
        for (rule in GhanaPatterns.DIRECTION_RULES) {
            if (rule.regex.containsMatchIn(text)) return rule.direction
        }
        return null
    }

    private fun inferStatus(text: String): String? {
        for (rule in GhanaPatterns.STATUS_RULES) {
            if (rule.regex.containsMatchIn(text)) return rule.status
        }
        return null
    }

    /** Last-resort kind inference when neither a template nor a marker produced one. */
    private fun inferKind(text: String, fields: Map<String, String>): String? {
        val lowered = text.lowercase()
        val direction = fields["direction"]
        when {
            "airtime" in lowered || "recharge" in lowered || "top up" in lowered || "top-up" in lowered ->
                return "airtime_purchase"

            "data bundle" in lowered || "data plan" in lowered || "bundle" in lowered -> return "data_bundle"

            "loan" in lowered || "advance" in lowered -> return when {
                // Decide from the wording, not from an already-guessed direction: that was
                // circular and turned every disbursement into a repayment.
                listOf("credited", "disbursed", "sent to your", "received").any { it in lowered } -> "loan_disbursement"
                listOf("repay", "repaid", "deduct", "recovery").any { it in lowered } -> "loan_repayment"
                direction == "IN" -> "loan_disbursement"
                else -> "loan_repayment"
            }

            "reversal" in lowered || "reversed" in lowered -> return "reversal"
            "withdraw" in lowered || "cash out" in lowered -> return "withdrawal"
            "deposit" in lowered || "cash in" in lowered -> return "deposit"
            !fields["paybill_name"].isNullOrEmpty() || "paybill" in lowered || "business id" in lowered ->
                return "paybill_payment"

            listOf("merchant", "momo pay", "momopay", "till", "pos payment").any { it in lowered } ->
                return "merchant_payment"

            direction == "IN" -> return "p2p_transfer_in"
            direction == "OUT" -> return "p2p_transfer_out"
        }
        return null
    }

    private fun confidence(mode: String, route: String?, fields: Map<String, String>): Double {
        val present = SPEC_FIELDS.count { !fields[it].isNullOrEmpty() }
        val yieldRatio = minOf(1.0, present / 7.0)
        val routeBonus = if (route == "sender") 0.05 else 0.0
        val raw = if (mode == "template") {
            val clean = if ((fields["status"] ?: "SUCCESS") == "SUCCESS") 0.05 else 0.0
            0.55 + 0.30 * yieldRatio + routeBonus + clean
        } else {
            0.25 + 0.30 * yieldRatio + routeBonus
        }
        val capped = if (mode == "template") minOf(0.95, raw) else minOf(0.55, raw)
        return Math.round(capped * 1000.0) / 1000.0
    }

    private val CHANNEL_LABELS = setOf("agent", "app", "ussd", "atm", "pos", "other")
}
