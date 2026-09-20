package com.pennywiseai.parser.core.bank

/**
 * Parser for MTN Mobile Money (MoMo) SMS messages (Ghana).
 *
 * Handles formats like (synthetic samples - never paste a real message into the repo):
 * - "Payment made for GHS 50.00 to DAVID PLACEHOLDER. Current Balance: GHS 450.00.
 *    Reference: school fees. Transaction ID: 76712833868. Fee charged: GHS 0.50"
 * - "You have received GHS 120.00 from AMA OWUSU (0540000000) on 10/09/25 at 13:51.
 *    New Balance: GH¢570.00. MoMo Team."
 * - "Payment of GH¢1,050.00 to ECG PAYBILL (8811) was successful. Levy: GH¢15.75.
 *    Fee: GH¢0.50. Balance: GH¢2,301.20. Txn ID: 230910135144AB"
 * - "You bought GHS 20.00 airtime for 0201111111. Balance: GHS 180.00. Ref: 51233445566"
 *
 * Key patterns:
 * - Sender: MTN, MTN-MOMO, MOMO, MTN MoMo PSB, MTN GHANA
 * - Currency: GHS / GH¢ / GH₵
 * - Outbound transfers say "Payment made for GHS x to NAME"; inbound says
 *   "You have received GHS x from NAME"; balances arrive as "Current Balance:"
 * - Fees and the E-Levy arrive as separate tokens ("Fee charged:", "Levy:"), never folded
 *   into the amount - which is why the spec keeps fee/levy as first-class fields even though
 *   ParsedTransaction only carries the amount.
 *
 * Country: Ghana (GH₵, cedi). Operator market share in 2024: MTN was the largest MoMo
 * operator, so this parser carries most of the traffic a Ghanaian user receives.
 *
 * @see TelecelCashParser
 * @see AirtelTigoMoneyParser
 * @see GhanaMobileMoneyParser for the shared cascade and the wallet-account rationale
 */
class MtnMoMoParser : GhanaMobileMoneyParser(operatorId = "mtn_momo", bankDisplayName = "MTN Mobile Money")
