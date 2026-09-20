package com.pennywiseai.parser.core.bank

/**
 * Parser for AirtelTigo Money (AT Money) SMS messages (Ghana).
 *
 * Handles formats like (synthetic samples - never paste a real message into the repo):
 * - "TXN 987654321. GHS 30.00 sent to KOFI PLACEHOLDER (0271234567). Balance: GHS 120.50.
 *    Thank you for using AirtelTigo Money."
 * - "You have received GHS 400.00 from AMA OWUSU (0201111111) on 10/09/25 at 18:02.
 *    New Balance: GH¢400.50. Dial *800# to check your balance."
 * - "Cash In of GHS 1,000.00 from AGENT YAW successful. Balance: GHS 1,000.00. TrxID: 55214487963"
 * - "Payment of GHS 85.00 to SHOPRITE GHANA (PAYBILL 57123) successful. Fee: GHS 1.00.
 *    Balance: GHS 1,415.00."
 *
 * Key patterns:
 * - Sender: AIRTELTIGO, AIRTELTIGO MONEY, AT MONEY, ATMONEY, AT, AT TIGO MONEY, ATPSB LTD
 *   (the bare "AT" code is short but exact-anchored, so it cannot shadow a longer sender)
 * - The "TXN <id>." prefix and the "Thank you for using AirtelTigo Money." footer are the two
 *   most reliable discriminators; the footer is stripped before field extraction so it cannot
 *   be mistaken for a merchant name
 * - Glo's customer base moved to AirtelTigo in 2020 and the AT/Glo rebrand churn means
 *   messages from this wallet show up with several historical sender IDs
 *
 * Country: Ghana (GH₵, cedi).
 *
 * @see MtnMoMoParser
 * @see TelecelCashParser
 */
class AirtelTigoMoneyParser :
    GhanaMobileMoneyParser(operatorId = "airtel_tigo_money", bankDisplayName = "AirtelTigo Money")
