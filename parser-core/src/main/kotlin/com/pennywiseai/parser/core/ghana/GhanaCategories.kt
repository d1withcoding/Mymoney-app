// GENERATED FILE - do not edit by hand.
//
// Source of truth: research/ghana-sms/spec/ghana_categories.json
// Regenerate:      python3 research/ghana-sms/tools/sync_kotlin.py
//
// Tier 0 of the Objective-3 cascade: the deterministic keyword/merchant layer. The
// Python trainer (tools/train.py, RuleTier) reads the same JSON, so the rules the paper
// reports and the rules the phone runs are one artifact. Categories whose 'requires_user_creation'
// is true are not in PennyWise's default category set; the app must create them once
// before they can be assigned (see the integration note in GhanaCategoryClassifier.kt).

package com.pennywiseai.parser.core.ghana

/** Spending taxonomy for Ghana mobile-money messages (spec v1.0.0). */
object GhanaCategories {
    const val SPEC_VERSION: String = "1.0.0"
    const val FALLBACK_LABEL: String = "other"

    /** Stable label order - index i is also the model's output neuron i. */
    val LABELS: List<String> = listOf(
        "food_dining",
        "groceries",
        "transport",
        "utilities",
        "airtime_data",
        "shopping",
        "health",
        "education",
        "rent_housing",
        "entertainment",
        "banking",
        "cash_agent",
        "p2p_transfer",
        "income",
        "other",
    )

    /** Labels that do not exist in DefaultCategoryData and must be created by the user. */
    val USER_CREATED_LABELS: Set<String> = setOf(

    )

    /** label -> (matchToken, keyword-or-merchant). Lowercased; len > 2 only, as in train.py. */
    val LEXICON: Map<String, List<String>> = mapOf(
        "food_dining" to listOf("bakery", "banku", "bistro", "bread", "burger", "cafe", "catering", "chop bar", "chop shop", "chopbar", "coffee", "dining", "eat", "grill", "indomie", "jollof", "juice", "kenkey", "kfc", "kfc ghana", "liberty", "liberty prime", "mcdonald", "mcdonalds accra", "mocktails", "moyo", "moyo restaurant", "nungua bistro", "pizza", "pizza hut ghana", "restaurant", "snack", "starbucks ghana", "tea", "waakye"),
        "groceries" to listOf("adonko market", "akwalem market", "evian", "fish", "grocery", "library shopping", "makola provisions", "mall", "market", "melcom", "melcom ghana ltd", "mini mart", "minimart", "oil", "prime fresh mart", "provisions", "rice", "sachet water", "shoprite", "shoprite ghana ltd", "store", "supermarket", "vegetables", "voltic"),
        "transport" to listOf(" transport", "abossey okai", "akwaaba parking", "bolt", "bolt africa", "cab", "diesel", "esso", "fuel", "fuel card", "goil", "goil petrol station", "heygo", "mechanic", "odofo", "ogg", "ogt", "ogt limited", "parking", "petrobon", "petrol", "shell", "shell ghana", "spare parts", "star oil", "stc", "stc ghana", "stp", "taxi", "toll", "total", "totalenergies", "totalenergies ghana", "trotro", "tyre", "uber", "uber ghana", "viage", "vip bus", "vip travel and tours", "voyager", "yango"),
        "utilities" to listOf(" refuse", "aksa spam", "bill", "cablekit", "dstv", "dstv ghana", "dwls", "ecg", "ecg ghana", "ecg paybill", "electricity", "geco", "ghana water gwcl", "gotv", "grid", "gt electric", "gwcl", "internet", "levy rate", "multichoice ghana", "property rate", "pst", "spam", "startimes", "tema water", "waste", "water", "wifi"),
        "airtime_data" to listOf("10gb", "1gb", "2gb", "5gb", "airtiltigo ghana", "airtime", "bundle", "data bundle", "glo ghana", "momo data", "mtn ghana", "pin", "recharge", "roaming", "scratch card", "subscribed", "telecel ghana", "top up", "topup", "voice bundle", "vtu"),
        "shopping" to listOf("bag", "barber", "charger", "clothing", "cosmetics", "decathlon", "decathlon accra", "electronics", "fabric", "furniture", "home depot", "jumia", "jumia ghana", "kente", "laptop", "makeup", "melcom electronics", "mercadona", "nadir furniture", "perfume", "phone", "salon", "sandal", "screen", "shoe", "sport", "suzanna fashion"),
        "health" to listOf("akai house clinic", "chemist", "clinic", "dental", "doctor", "drug", "ernes pharmacy", "glasses", "health insurance", "hospital", "korle bu teaching hospital", "lab", "lancet clinic", "malaria", "maternity", "medi-lab ghana", "medicine", "nhis", "optician", "pharmacare", "pharmacy", "polyclinic", "test", "vaccine"),
        "education" to listOf("accreditation", "ashanti cam tutorial", "books", "campus", "college", "department of", "exam", "ghana education service", "jamb", "knust", "library", "registration fee", "school", "school fees", "stationery", "tuition", "tutorial", "university", "university of ghana", "waec", "wassce", "wassce exam fees"),
        "rent_housing" to listOf("advance rent", "agent fee", "caretaker", "estate", "estate manager", "honey moon grove rent", "housing", "landlord", "landlord rent", "plot", "rent", "rooms rent", "tenancy", "utilities service charge"),
        "entertainment" to listOf("airtel betting", "bet", "betway", "betway ghana", "bowling", "cinema", "cinematrix", "cinematrix silverbird", "club", "concert", "event", "game", "karaoke", "lounge", "movie", "netflix", "showmax", "showmax since 1997", "silverbird", "sportybet", "spotify", "spotify ab", "ticket", "youtube premium"),
        "banking" to listOf("advance", "bank transfer", "caliber bank", "cocoa", "ecobank ghana", "gcb bank", "ghipss", "giip", "insurance", "interbank", "invest", "loan", "microfinance", "momo to bank", "mtn qwikeloan", "pension", "premium", "qwikloan", "repayment", "savings", "ssnit", "stanbic ghana", "treasury bill", "trustfund microfinance"),
        "cash_agent" to listOf("agent", "at money agent", "atm", "cash in", "cash out", "deposit", "float", "mobile money agent", "momo agent", "super agent", "telecel cash agent", "withdraw"),
        "p2p_transfer" to listOf("cross network", "interoperab", "other network", "p2p", "sent money", "sent to", "transfer to", "wallet to wallet"),
        "income" to listOf("allowance", "beneficiary", "cashback", "credit", "dividend", "interest", "momo interest", "moneygram", "payroll", "received", "refund", "remittance", "reversal", "salary", "sponsor", "stipend", "wages", "western union"),
        "other" to listOf(),
    )

    /** label -> PennyWise category name (shared/.../DefaultCategoryData.kt). */
    val APP_CATEGORY: Map<String, String> = mapOf(
        "food_dining" to "Food & Dining",
        "groceries" to "Groceries",
        "transport" to "Transportation",
        "utilities" to "Bills & Utilities",
        "airtime_data" to "Mobile",
        "shopping" to "Shopping",
        "health" to "Healthcare",
        "education" to "Education",
        "rent_housing" to "Rent & Housing",
        "entertainment" to "Entertainment",
        "banking" to "Banking",
        "cash_agent" to "Cash Agent",
        "p2p_transfer" to "P2P Transfer",
        "income" to "Income",
        "other" to "Others",
    )

    /** Labels whose app category is not shipped, i.e. mapping falls back to [FALLBACK_APP_CATEGORY]. */
    const val FALLBACK_APP_CATEGORY: String = "Others"
}
