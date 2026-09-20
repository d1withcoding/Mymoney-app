// GENERATED FILE - do not edit by hand.
//
// Source of truth: research/ghana-sms/spec/ghana_patterns.json
// Regenerate:     python3 research/ghana-sms/tools/sync_kotlin.py
//
// The regex strings below are produced by the same token expansion the Python
// reference implementation uses (research/ghana-sms/tools/ghana_parse/spec.py), so
// the evaluation harness and this parser cannot drift apart. Field-extraction
// behaviour lives in GhanaSmsExtractor.kt; this file is tables only.

package com.pennywiseai.parser.core.ghana

/** Compiled extraction specification for Ghana mobile-money SMS (spec v1.1.0). */
object GhanaPatterns {
    const val SPEC_VERSION: String = "1.1.0"
    const val CURRENCY: String = "GHS"

    val KIND_TO_TRANSACTION_TYPE: Map<String, String> = mapOf(
        "p2p_transfer_out" to "EXPENSE",
        "p2p_transfer_in" to "INCOME",
        "deposit" to "INCOME",
        "withdrawal" to "EXPENSE",
        "merchant_payment" to "EXPENSE",
        "paybill_payment" to "EXPENSE",
        "airtime_purchase" to "EXPENSE",
        "data_bundle" to "EXPENSE",
        "loan_disbursement" to "INCOME",
        "loan_repayment" to "EXPENSE",
        "reversal" to "INCOME",
        "balance_transfer_out" to "TRANSFER",
        "unknown_out" to "EXPENSE",
        "unknown_in" to "INCOME",
    )

    val KIND_TO_DIRECTION: Map<String, String> = mapOf(
        "p2p_transfer_in" to "IN",
        "p2p_transfer_out" to "OUT",
        "deposit" to "IN",
        "withdrawal" to "OUT",
        "merchant_payment" to "OUT",
        "paybill_payment" to "OUT",
        "airtime_purchase" to "OUT",
        "data_bundle" to "OUT",
        "loan_disbursement" to "IN",
        "loan_repayment" to "OUT",
        "reversal" to "IN",
        "balance_transfer_out" to "OUT",
        "unknown_in" to "IN",
        "unknown_out" to "OUT",
    )

    val SKIP_RULES: List<GhanaSkipRule> = listOf(
        GhanaSkipRule("otp", Regex("\\b(otp|one time password|pin is|verification code|password)\\b", RegexOption.IGNORE_CASE), "credential message, not a transaction"),
        GhanaSkipRule("promo", Regex("\\b(congratulations|you have won|bonus offer|register now|promo|lottery)\\b", RegexOption.IGNORE_CASE), "promotional message"),
        GhanaSkipRule("request_to_pay", Regex("(has requested|requests you to pay|pay .* on behalf|approve the payment by)", RegexOption.IGNORE_CASE), "payment request / approval prompt; no money moved yet"),
        GhanaSkipRule("loan_marketing", Regex("(apply now|you are eligible for a loan|loan offer of)", RegexOption.IGNORE_CASE), "credit marketing, no disbursement"),
        GhanaSkipRule("third_party_topup", Regex("\\b(airtime|recharge|top[- ]?up)\\b.{0,40}?\\bis ready for (?:your|the)\\b", RegexOption.IGNORE_CASE), "third-party top-up notice: no wallet debit is described"),
    )

    val STATUS_RULES: List<GhanaStatusRule> = listOf(
        GhanaStatusRule("status.reversal", Regex("\\b(reversed|reversal|refunded|credited back)\\b", RegexOption.IGNORE_CASE), "SUCCESS"),
        GhanaStatusRule("status.failed", Regex("\\b(failed|not successful|unsuccessful|declined|cancelled|canceled|reversed|could not be processed)\\b", RegexOption.IGNORE_CASE), "FAILED"),
        GhanaStatusRule("status.pending", Regex("\\b(pending|processing|awaiting|in progress|to be confirmed|authori[sz]e)\\b", RegexOption.IGNORE_CASE), "PENDING"),
        GhanaStatusRule("status.success", Regex("\\b(successful(?:ly)?|confirmed|credited|debited|paid|received|sent|purchased|bought|recharged|topped up|applied|deducted)\\b", RegexOption.IGNORE_CASE), "SUCCESS"),
    )

    val DIRECTION_RULES: List<GhanaDirectionRule> = listOf(
        GhanaDirectionRule("dir.in", Regex("(you have received|payment received|received (?:from|a deposit)|credited (?:you|your|to your)|deposit (?:of|received)|you have deposited|money in|you received)", RegexOption.IGNORE_CASE), "IN"),
        GhanaDirectionRule("dir.out", Regex("(you have sent|payment made|payment of|sent to|sent money|debited|debit of|withdraw|you (?:have )?(?:paid|purchased|bought|deposited)|made a payment|cash out|you have successfully purchased)", RegexOption.IGNORE_CASE), "OUT"),
    )

    val SHARED_FIELDS: Map<String, List<GhanaFieldPattern>> = mapOf(
        "amount" to listOf(
            GhanaFieldPattern("amt.currency_first", Regex("(?:GHS|GH[¢₵]|₵)\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("amt.after_verb", Regex("\\b(?:sent|paid|received|withdrawn|deposited|transferred|credited|debited|reversed)\\s+(?:GHS|GH[¢₵]|₵)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("amt.before_verb", Regex("([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+(?:was|has been)?\\s*(?:sent|paid|received|withdrawn|deposited|credited|debited)", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "balance" to listOf(
            GhanaFieldPattern("bal.current_labelled", Regex("\\b(?:current|new|updated|available|remaining|final)\\s+(?:momo|wallet|cash)?\\s*balance\\b[^0-9]{0,12}([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("bal.possessive", Regex("\\b(?:your )?(?:momo|telecel cash|aireltigo money|wallet) balance (?:is|was|of)\\b[^0-9]{0,12}([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("bal.bare", Regex("\\bbalance\\s*[:=]?\\s*(?:GHS|GH[¢₵]|₵)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "fee" to listOf(
            GhanaFieldPattern("fee.labeled", Regex("\\b(?:fee(?:\\s+charged)?|service charge|commission|handling fee)\\s*[:=]?\\s*(?:GHS|GH[¢₵]|₵)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "levy" to listOf(
            GhanaFieldPattern("levy.elevy", Regex("\\b(?:e[- ]?levy|electronic transfer levy|levy(?:\\s+charged)?)\\s*[:=]?\\s*(?:GHS|GH[¢₵]|₵)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "trx_id" to listOf(
            GhanaFieldPattern("trx.labeled", Regex("\\b(?:transaction\\s*(?:id|ref(?:erence)?)|txn\\s*id|trx\\s*id|trans\\s*id|tid|txn)\\b\\s*[:#]?\\s*([A-Z0-9]{6,20})", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("trx.paren", Regex("\\b(?:transaction\\s*(?:id|ref(?:erence)?)|txn\\s*id|trx\\s*id|ref)\\b\\s*[:#]?\\s*[(\\[]?([A-Z0-9]{6,20})[)\\]]?", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "reference" to listOf(
            GhanaFieldPattern("ref.labeled", Regex("\\b(?:reference|narration|remark|note|description)\\s*[:=]\\s*([^.\\n]{2,40}?)[.]?(?:\\s|\$)", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "counterparty_name" to listOf(
            GhanaFieldPattern("cp.to_upper", Regex("\\b(?:sent to|paid to|made payment to|to)\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?=(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s*\$))", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("cp.from_upper", Regex("\\b(?:received from|received a deposit from|from)\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?=(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s*\$))", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("cp.dash_name", Regex("\\b(?:to|from)\\s+(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])\\s*-\\s*(?-i:([A-Za-z][A-Za-z '\\-&]{1,40}?))(?=(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s*\$))", RegexOption.IGNORE_CASE), 2, false, null),
            GhanaFieldPattern("cp.to_title", Regex("\\b(?:sent to|paid to|to|from)\\s+(?-i:([A-Z][A-Za-z'&\\-]{1,20}(?: [A-Z][A-Za-z'&\\-]{1,20}){0,3}))(?=(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s*\$))", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "counterparty_phone" to listOf(
            GhanaFieldPattern("ph.parens", Regex("[(\\[]\\s*(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])\\s*[)\\]]", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("ph.generic", Regex("\\b(?:to|from|for|number)\\s+(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])\\b", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("ph.loose", Regex("\\b(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])\\b", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "paybill_name" to listOf(
            GhanaFieldPattern("pb.name_before_id", Regex("\\b(?:to|for)\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?=\\s*(?:paybill|business|merchant|till|shop|store))", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("pb.to_delimited", Regex("\\b(?:to|for)\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?=\\s*[(\\[](?=[^)]{0,40}(?:paybill|business|merchant|till|\\bid\\b|\\bno\\b)))", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("pb.to_title", Regex("\\b(?:to|for)\\s+(?-i:([A-Z][A-Za-z'&\\-]{1,20}(?: [A-Z][A-Za-z'&\\-]{1,20}){1,3}))(?=\\s*(?:[(\\[](?=[^)]{0,40}(?:paybill|business|merchant|till|\\bid\\b|\\bno\\b))|paybill|business|merchant|till))", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "paybill_account" to listOf(
            GhanaFieldPattern("pb.account", Regex("\\b(?:paybill|business|merchant|till|bill|meter|account)\\s*(?:no|number|id|code|ref)?\\s*[:#]?\\s*([0-9][0-9 /-]{2,19})", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "date" to listOf(
            GhanaFieldPattern("dt.iso", Regex("\\bon\\s+([0-9]{4}-[0-9]{2}-[0-9]{2})", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("dt.any", Regex("([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4})", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "time" to listOf(
            GhanaFieldPattern("tm.at", Regex("\\bat\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*[APap]\\.?[Mm]\\.?)?)", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("tm.any", Regex("\\b([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*[APap]\\.?[Mm]\\.?)?)", RegexOption.IGNORE_CASE), 1, false, null),
        ),
        "channel" to listOf(
            GhanaFieldPattern("ch.agent", Regex("\\b(?:via|through|at|from)\\s+((?:momo |telecel |at )?agent(?:\\s+[A-Z][A-Z .\\-]{0,25})?)", RegexOption.IGNORE_CASE), 1, false, "agent"),
            GhanaFieldPattern("ch.app", Regex("\\b(?:via|through|using|on)\\s+(?:the )?(momo app|mobile app|telecel cash app|super app|at money app|my telecel app|momo\\s*app)", RegexOption.IGNORE_CASE), 1, false, "app"),
            GhanaFieldPattern("ch.ussd", Regex("\\b(?:via|through|using|dialled|dial)\\s+(\\*[0-9]{2,3}#(?:\\*[0-9]{1,3}#)*)", RegexOption.IGNORE_CASE), 1, false, "ussd"),
            GhanaFieldPattern("ch.atm", Regex("\\b(?:via|at|from)\\s+(atm|cash self[- ]service|self[- ]service machine)", RegexOption.IGNORE_CASE), 1, false, "atm"),
            GhanaFieldPattern("ch.pos", Regex("\\b(?:via|using|at)\\s+(pos(?:\\s+(?:terminal|payment))?|momo\\s*pay|merchant terminal)", RegexOption.IGNORE_CASE), 1, false, "pos"),
        ),
    )

    val OPERATORS: Map<String, GhanaOperatorSpec> = mapOf(
        "mtn_momo" to GhanaOperatorSpec(
            "mtn_momo",
            "MTN Mobile Money (MoMo)",
            listOf(Regex("^MTN([ -]?(MOMO|MOBI|MOMO\\s*PSB|PSB\\s*LTD))?\$", RegexOption.IGNORE_CASE), Regex("^MOMO(PSB)?\$", RegexOption.IGNORE_CASE), Regex("^MTN\\s*MO\\s*MO\$", RegexOption.IGNORE_CASE), Regex("^MO\\s*MONEY\$", RegexOption.IGNORE_CASE)),
            listOf(Regex("\\bmtn\\b", RegexOption.IGNORE_CASE), Regex("\\bmomo\\b", RegexOption.IGNORE_CASE), Regex("\\bmomo psb\\b", RegexOption.IGNORE_CASE), Regex("current balance:", RegexOption.IGNORE_CASE), Regex("payment made for", RegexOption.IGNORE_CASE), Regex("payment received for", RegexOption.IGNORE_CASE)),
            listOf(
            GhanaTemplate(
                "mtn.p2p_out.make_payment",
                "p2p_transfer_out",
                10,
                Regex("^Payment made for\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+to\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))\\.\\s*(?:.*?Current Balance:\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Reference:\\s*([^.\\n]{2,40}?)\\.)?\\s*(?:.*?Transaction ID:\\s*([A-Z0-9]{6,20}))?\\s*(?:.*?Fee charged:\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_name", 3 to "balance", 4 to "reference", 5 to "trx_id", 6 to "fee")
            ),
            GhanaTemplate(
                "mtn.p2p_in.make_payment",
                "p2p_transfer_in",
                10,
                Regex("^Payment received for\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+from\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$)(?:\\s*[-(]\\s*(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9]))?\\.\\s*(?:.*?Current Balance:\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Reference:\\s*([^.\\n]{2,40}?)\\.)?\\s*(?:.*?Transaction ID:\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_name", 3 to "counterparty_phone", 4 to "balance", 5 to "reference", 6 to "trx_id")
            ),
            GhanaTemplate(
                "mtn.airtime.purchase",
                "airtime_purchase",
                12,
                Regex("^You have (?:bought|purchased|recharged)\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+(?:worth of )?(?:airtime|credit|recharge)(?:\\s+for\\s+(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9]))?.*?(?:.*?balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_phone", 3 to "balance")
            ),
            GhanaTemplate(
                "mtn.data_bundle.purchase",
                "data_bundle",
                12,
                Regex("^You have (?:successfully )?(?:bought|purchased|subscribed to)\\s+(?:a\\s+)?(?:[0-9.]+\\s*[KMGT]?B\\s+)?data (?:bundle|plan|package)(?:\\s+(?:of|for)\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?.*?(?:.*?(?:for|to)\\s+(?:\\+?233\\s?)?0?[2-5][0-9]{8})?(?:.*?balance\\s*(?:is|of)?\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_phone", 3 to "balance")
            ),
            GhanaTemplate(
                "mtn.paybill.payment",
                "paybill_payment",
                15,
                Regex("^Payment of\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+to\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))\\s*[(\\[]?\\s*(?:(?:paybill|business|merchant|till|account)\\s*(?:no|id|number|code)?\\s*[:#]?\\s*([A-Z0-9 -]{3,20}))\\s*[)\\]]?[^.]*\\.?\\s*(?:.*?balance\\s*(?:is|of|after transaction)?\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Transaction ID:\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "paybill_name", 3 to "paybill_account", 4 to "balance", 5 to "trx_id")
            ),
            GhanaTemplate(
                "mtn.momo_pay.merchant",
                "merchant_payment",
                15,
                Regex("^You have paid\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+to\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$).*?(?:.*?balance\\s*(?:is|of|after transaction)?:?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?(?:Transaction|Merchant|Terminal)\\s*ID:\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "paybill_name", 3 to "balance", 4 to "trx_id")
            ),
            GhanaTemplate(
                "mtn.loan.disbursement",
                "loan_disbursement",
                18,
                Regex("^A (?:loan|advance) (?:of|amounting to)\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+has been (?:credited|disbursed).*?(?:.*?balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?(?:Loan|Transaction)\\s*ID:\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "balance", 3 to "trx_id")
            ),
            GhanaTemplate(
                "mtn.reversal.credit",
                "reversal",
                18,
                Regex("([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+has been reversed (?:to|into) your (?:momo )?wallet.*?(?:.*?balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Transaction ID:\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "balance", 3 to "trx_id")
            ),
            GhanaTemplate(
                "mtn.p2p_out.classic",
                "p2p_transfer_out",
                20,
                Regex("^You have sent\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+to\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$)(?:\\s*[-(]\\s*(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9]))?\\s+on\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4})(?:\\s+at\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*[APap]\\.?[Mm]\\.?)?))?\\.?\\s*(?:.*?balance (?:is|of|after transaction)\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Transaction ID:\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_name", 3 to "counterparty_phone", 4 to "date", 5 to "time", 6 to "balance", 7 to "trx_id")
            ),
            GhanaTemplate(
                "mtn.p2p_in.classic",
                "p2p_transfer_in",
                20,
                Regex("^You have received\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+from\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$)(?:\\s*[-(]\\s*(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9]))?\\s+on\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4})(?:\\s+at\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*[APap]\\.?[Mm]\\.?)?))?\\.?\\s*(?:.*?balance (?:is|of|after transaction)\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Transaction ID:\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_name", 3 to "counterparty_phone", 4 to "date", 5 to "time", 6 to "balance", 7 to "trx_id")
            ),
            GhanaTemplate(
                "mtn.deposit.agent",
                "deposit",
                20,
                Regex("^(?:Deposit of\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)(?:\\s+from\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$))?|You have deposited\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+into your\\s+(?:momo\\s+)?(?:wallet|account))(?:\\s+\\w+\\s+\\S+)?(?:\\s+at\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$))?\\s*(?:.{0,40}?successful|\\.\\s*)?(?:.*?(?:new|current|your) balance (?:is|of|after transaction)?:?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Transaction ID:\\s*([A-Z0-9]{6,20}))?\\s*(?:.*?Fee(?:\\s+charged)?:?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_name", 3 to "balance", 4 to "trx_id", 5 to "fee")
            ),
            GhanaTemplate(
                "mtn.withdrawal.agent",
                "withdrawal",
                20,
                Regex("^You have (?:withdrawn|made a cash out of)\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?).*?(?:.*?(?:new|current) balance (?:is|of)?:?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Transaction ID:\\s*([A-Z0-9]{6,20}))?\\s*(?:.*?Fee(?:\\s+charged)?:?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "balance", 3 to "trx_id", 4 to "fee")
            ),
            ),
            mapOf(
                "balance" to listOf(
            GhanaFieldPattern("mtn.bal.current_balance", Regex("Current Balance:\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("mtn.bal.momo_balance", Regex("Your MoMo balance is\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
                ),
                "amount" to listOf(
            GhanaFieldPattern("mtn.amt.payment_for", Regex("^Payment (?:made|received) for\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
                ),
                "fee" to listOf(
            GhanaFieldPattern("mtn.fee.charged", Regex("Fee charged:\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
                ),
                "trx_id" to listOf(
            GhanaFieldPattern("mtn.trx.id", Regex("Transaction ID:\\s*([A-Z0-9]{6,20})", RegexOption.IGNORE_CASE), 1, false, null),
                ),
                "reference" to listOf(
            GhanaFieldPattern("mtn.ref.text", Regex("Reference:\\s*([^.\\n]{2,40}?)[.]", RegexOption.IGNORE_CASE), 1, false, null),
                ),
            )
        ),
        "telecel_cash" to GhanaOperatorSpec(
            "telecel_cash",
            "Telecel Cash",
            listOf(Regex("^TELECEL( ?CASH)?\$", RegexOption.IGNORE_CASE), Regex("^TC ?CASH\$", RegexOption.IGNORE_CASE), Regex("^TELECEL ?MOBI ?MONEY\$", RegexOption.IGNORE_CASE), Regex("^VODAFONE( ?CASH)?\$", RegexOption.IGNORE_CASE), Regex("^VCASH\$", RegexOption.IGNORE_CASE)),
            listOf(Regex("\\btelecel cash\\b", RegexOption.IGNORE_CASE), Regex("\\bvodafone cash\\b", RegexOption.IGNORE_CASE), Regex("your telecel cash balance", RegexOption.IGNORE_CASE), Regex("^[0-9]{16} confirmed", RegexOption.IGNORE_CASE)),
            listOf(
            GhanaTemplate(
                "tc.p2p_out.tid_prefixed",
                "p2p_transfer_out",
                5,
                Regex("^([0-9]{16})\\s+Confirmed\\.\\s*You have sent\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+to\\s+(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])\\s*-\\s*(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$)\\s+on\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4})\\s+at\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*[APap]\\.?[Mm]\\.?)?)\\.?\\s*(?:Your Telecel Cash balance is\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "trx_id", 2 to "amount", 3 to "counterparty_phone", 4 to "counterparty_name", 5 to "date", 6 to "time", 7 to "balance")
            ),
            GhanaTemplate(
                "tc.p2p_in.tid_prefixed",
                "p2p_transfer_in",
                5,
                Regex("^([0-9]{16})\\s+Confirmed\\.\\s*You have received\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+from\\s+(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])\\s*-\\s*(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$)\\s+on\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4})\\s+at\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*[APap]\\.?[Mm]\\.?)?)\\.?\\s*(?:Your Telecel Cash balance is\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "trx_id", 2 to "amount", 3 to "counterparty_phone", 4 to "counterparty_name", 5 to "date", 6 to "time", 7 to "balance")
            ),
            GhanaTemplate(
                "tc.airtime.purchase",
                "airtime_purchase",
                12,
                Regex("^You have (?:successfully )?(?:topped up|recharged|bought|purchased)\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)(?:\\s+(?:worth of |of )?(?:airtime|credit))?(?:\\s+for\\s+(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9]))?(?:\\s+for\\s+(?:an? )?(?:MTN|Telecel|AirtelTigo|Glo)\\s+customer)?.*?(?:.*?balance\\s*(?:is|of)?\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_phone", 3 to "balance")
            ),
            GhanaTemplate(
                "tc.paybill.payment",
                "paybill_payment",
                15,
                Regex("^Payment of\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+to\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$)\\s*(?:\\(\\s*(?:paybill|business|merchant|till|account)\\s*(?:no|id|number)?\\s*[:#]?\\s*([A-Z0-9 -]{2,20})\\s*\\)|for\\s+account\\s*[:#]?\\s*([0-9][0-9 -]{2,19}))?\\s*(?:.*?(?:was|is)\\s+successful)?.*?(?:on\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4}))?(?:.*?balance\\s*(?:is|of|after transaction)?\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "paybill_name", 3 to "paybill_account", 4 to "paybill_account", 5 to "date", 6 to "balance")
            ),
            GhanaTemplate(
                "tc.reversal.credit",
                "reversal",
                18,
                Regex("([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+has been (?:reversed|refunded) (?:to|into) your (?:telecel cash )?wallet.*?(?:.*?balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "balance")
            ),
            GhanaTemplate(
                "tc.deposit.cash_in",
                "deposit",
                20,
                Regex("^Cash (?:in|deposit) of\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+(?:by|from)\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$).*?(?:was|is) successful.*?(?:.*?balance (?:is|of|after transaction)?:?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Transaction (?:ID|no)\\s*[:#]?\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_name", 3 to "balance", 4 to "trx_id")
            ),
            GhanaTemplate(
                "tc.withdrawal.cash_out",
                "withdrawal",
                20,
                Regex("^You have withdrawn\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+from your (?:telecel cash|wallet) account.*?(?:.*?balance (?:is|of|after transaction)?:?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Transaction (?:ID|no)\\s*[:#]?\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "balance", 3 to "trx_id")
            ),
            GhanaTemplate(
                "tc.p2p_out.short",
                "p2p_transfer_out",
                25,
                Regex("^You have sent\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+to\\s+(?:(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])\\s*-\\s*)?(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$).*?(?:on\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4}))?(?:\\s+at\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*[APap]\\.?[Mm]\\.?)?))?\\.?\\s*(?:.*?Telecel Cash balance is\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_phone", 3 to "counterparty_name", 4 to "date", 5 to "time", 6 to "balance")
            ),
            GhanaTemplate(
                "tc.p2p_in.short",
                "p2p_transfer_in",
                25,
                Regex("^You have received\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+from\\s+(?:(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])\\s*-\\s*)?(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$).*?(?:on\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4}))?(?:\\s+at\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*[APap]\\.?[Mm]\\.?)?))?\\.?\\s*(?:.*?Telecel Cash balance is\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_phone", 3 to "counterparty_name", 4 to "date", 5 to "time", 6 to "balance")
            ),
            ),
            mapOf(
                "balance" to listOf(
            GhanaFieldPattern("tc.bal.telecel_cash_is", Regex("Your (?:Telecel Cash|Vodafone Cash) balance is\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
                ),
                "trx_id" to listOf(
            GhanaFieldPattern("tc.trx.leading_tid", Regex("^[0-9]{16}(?= confirmed)", RegexOption.IGNORE_CASE), 0, true, null),
                ),
                "amount" to listOf(
            GhanaFieldPattern("tc.amt.sent_received", Regex("You have (?:sent|received|withdrawn|paid)\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
                ),
                "date" to listOf(
            GhanaFieldPattern("tc.dt.on", Regex("on\\s+([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4})", RegexOption.IGNORE_CASE), 1, false, null),
                ),
                "time" to listOf(
            GhanaFieldPattern("tc.tm.at", Regex("at\\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?(?:\\s*[APap]\\.?[Mm]\\.?)?)", RegexOption.IGNORE_CASE), 1, false, null),
                ),
            )
        ),
        "airtel_tigo_money" to GhanaOperatorSpec(
            "airtel_tigo_money",
            "AirtelTigo Money",
            listOf(Regex("^AIRTELTIGO( ?MONEY)?\$", RegexOption.IGNORE_CASE), Regex("^AT( ?MONEY)?\$", RegexOption.IGNORE_CASE), Regex("^AT\\s*TIGO(\\s*MONEY)?\$", RegexOption.IGNORE_CASE), Regex("^AIRTEL ?TIGO ?MONEY\$", RegexOption.IGNORE_CASE), Regex("^ATMONEY\$", RegexOption.IGNORE_CASE), Regex("^AT ?PSB ?LTD\$", RegexOption.IGNORE_CASE)),
            listOf(Regex("\\baireltigo\\b", RegexOption.IGNORE_CASE), Regex("\\bat money\\b", RegexOption.IGNORE_CASE), Regex("thank you for using airteltigo", RegexOption.IGNORE_CASE), Regex("^txn [0-9]{6,20}\\.", RegexOption.IGNORE_CASE)),
            listOf(
            GhanaTemplate(
                "at.p2p_out.txn_prefixed",
                "p2p_transfer_out",
                5,
                Regex("^TXN\\s*([A-Z0-9]{6,20})\\.\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+sent to\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))\\s*[(\\[](?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])[)\\]]?\\.?\\s*(?:Balance:\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?.*?(?:Thank you for using AirtelTigo Money\\.?)?\$", RegexOption.IGNORE_CASE),
                mapOf(1 to "trx_id", 2 to "amount", 3 to "counterparty_name", 4 to "counterparty_phone", 5 to "balance")
            ),
            GhanaTemplate(
                "at.p2p_in.txn_prefixed",
                "p2p_transfer_in",
                5,
                Regex("^TXN\\s*([A-Z0-9]{6,20})\\.\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+(?:received|credited)(?: to you)? from\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))\\s*[(\\[](?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])[)\\]]?\\.?\\s*(?:Balance:\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?.*\$", RegexOption.IGNORE_CASE),
                mapOf(1 to "trx_id", 2 to "amount", 3 to "counterparty_name", 4 to "counterparty_phone", 5 to "balance")
            ),
            GhanaTemplate(
                "at.airtime.purchase",
                "airtime_purchase",
                12,
                Regex("^You have (?:successfully )?(?:topped up|recharged|bought|purchased)\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)(?:\\s+(?:worth of |of )?(?:airtime|credit))?(?:\\s+for\\s+(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9]))?(?:\\s+for\\s+(?:an? )?(?:MTN|Telecel|AirtelTigo|Glo)\\s+customer)?.*?(?:.*?balance\\s*(?:is|of)?\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_phone", 3 to "balance")
            ),
            GhanaTemplate(
                "at.merchant.payment",
                "merchant_payment",
                15,
                Regex("^Payment of\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+to\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))\\s*[(\\[]?(?:Paybill|Merchant|Till)\\s*(?:ID|No|Number)?\\s*[:#]?\\s*([A-Z0-9 -]{3,20})[)\\]]?\\s*(?:.*?successful)?(?:.*)?(?:.*?Balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?(?:Txn|Transaction)\\s*(?:ID)?\\s*[:#]?\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "paybill_name", 3 to "paybill_account", 4 to "balance", 5 to "trx_id")
            ),
            GhanaTemplate(
                "at.deposit.cash_in",
                "deposit",
                18,
                Regex("^Deposit of\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+successful\\.?\\s*(?:Fee\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:New balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:Ref\\s*[:#]?\\s*([A-Z0-9]{6,20}))?\\s*(?:.*?for account holder (?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "fee", 3 to "balance", 4 to "trx_id", 5 to "counterparty_name")
            ),
            GhanaTemplate(
                "at.withdrawal.cash_out",
                "withdrawal",
                18,
                Regex("^Withdrawal of\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+(?:at (?:agent|ATM)\\s*(?-i:([A-Z][A-Z '\\-&]{1,40}?))(?:\\s*[.](?:\\s|\$)|\\s+on\\b|\\s+at\\b|\\s*[(\\[]|\\s+-\\s|\\s*[,;]|\\s+(?=(?-i:[a-z]))|\\s*\$) )?successful\\.?\\s*(?:.*?New balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?Ref\\s*[:#]?\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_name", 3 to "balance", 4 to "trx_id")
            ),
            GhanaTemplate(
                "at.loan.disbursement",
                "loan_disbursement",
                18,
                Regex("(?:Your )?(?:AT )?(?:loan|cash advance) (?:of|for)\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+has been (?:credited|disbursed).*?(?:.*?balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "balance")
            ),
            GhanaTemplate(
                "at.p2p_in.classic",
                "p2p_transfer_in",
                20,
                Regex("^You have received\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+from\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))\\s*[(\\[]?(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])[)\\]]?\\.?\\s*(?:.*?AirtelTigo Money balance:?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?(?:Txn|Transaction)\\s*(?:ID|Ref)\\s*[:#]?\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_name", 3 to "counterparty_phone", 4 to "balance", 5 to "trx_id")
            ),
            GhanaTemplate(
                "at.p2p_out.classic",
                "p2p_transfer_out",
                20,
                Regex("^You have sent\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)\\s+to\\s+(?-i:([A-Z][A-Z '\\-&]{1,40}?))\\s*[(\\[]?(?<![0-9])(?:\\+?233[\\s-]?)?0?([2-5][0-9][\\s-]?[0-9]{3}[\\s-]?[0-9]{4})(?![0-9])[)\\]]?\\.?\\s*(?:.*?AirtelTigo Money balance:?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?))?\\s*(?:.*?(?:Txn|Transaction)\\s*(?:ID|Ref)\\s*[:#]?\\s*([A-Z0-9]{6,20}))?", RegexOption.IGNORE_CASE),
                mapOf(1 to "amount", 2 to "counterparty_name", 3 to "counterparty_phone", 4 to "balance", 5 to "trx_id")
            ),
            ),
            mapOf(
                "balance" to listOf(
            GhanaFieldPattern("at.bal.labeled", Regex("\\b(?:AirtelTigo Money|AT Money|Wallet) balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
            GhanaFieldPattern("at.bal.new", Regex("New balance\\s*[:=]?\\s*(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
                ),
                "trx_id" to listOf(
            GhanaFieldPattern("at.trx.txn_prefix", Regex("^TXN\\s*([0-9A-Z]{6,20})\\.", RegexOption.IGNORE_CASE), 1, false, null),
                ),
                "amount" to listOf(
            GhanaFieldPattern("at.amt.sent_received", Regex("\\b(?:sent|received|Deposit|Withdrawal|Payment) of\\s+(?:GHS|GH[¢₵¢]|₵|GHC|GH|cedis?|sewapes)?\\s*([0-9][0-9,]*(?:[.][0-9]{1,2})?)", RegexOption.IGNORE_CASE), 1, false, null),
                ),
            )
        ),
    )

    /** Operator boilerplate that must not reach field extraction (see GhanaNormalizer). */
    val BOILERPLATE: List<Regex> = listOf(
        Regex("dial\\s+\\*?[0-9#*]+\\s*#?[^.]{0,40}(?:to manage|to stay connected|to buy|to apply|for more)[^.]*\\.", RegexOption.IGNORE_CASE),
        Regex("keep your pin secret[^.]*\\.", RegexOption.IGNORE_CASE),
        Regex("never share (?:it|your pin)[^.]*\\.", RegexOption.IGNORE_CASE),
        Regex("do not reply to this message[^.]*\\.", RegexOption.IGNORE_CASE),
        Regex("not you\\?[^.]{0,40}\\.", RegexOption.IGNORE_CASE),
        Regex("thank you for using[^.]*\\.", RegexOption.IGNORE_CASE),
        Regex("top up more at any agent[^.]*\\.", RegexOption.IGNORE_CASE),
        Regex("visit any agent[^.]*\\.", RegexOption.IGNORE_CASE),
        Regex("download the [a-z0-9 ]+app[^.]*\\.", RegexOption.IGNORE_CASE),
    )
}
