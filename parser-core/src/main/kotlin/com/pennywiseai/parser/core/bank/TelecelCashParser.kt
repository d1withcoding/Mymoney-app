package com.pennywiseai.parser.core.bank

/**
 * Parser for Telecel Cash SMS messages (Ghana).
 *
 * Handles formats like (synthetic samples - never paste a real message into the repo):
 * - "0000015061132227 Confirmed. You have sent GHS50.00 to 0241234567 - JOHN PLACEHOLDER
 *    on 2025-09-10 at 13:51:07. Your Telecel Cash balance is GHS654.03."
 * - "You have received GHS 75.50 from 0201111111 - AMA MENSAH on 10/09/2025 at 08:12.
 *    New balance: GHS 830.10."
 * - "Cash out of GHS 200.00 at AGENT KWAME is successful. Charge: GHS 2.00.
 *    Balance: GHS 431.00. Ref: 9932015544"
 *
 * Key patterns:
 * - Sender: TELECEL, TELECEL CASH, TC CASH, TELECEL MOBI MONEY, plus the pre-rebrand
 *   VODAFONE / VODAFONE CASH / VCASH codes. The wallet was renamed in January 2024, and old
 *   senders keep appearing on handsets that were never updated, so both sets are routed.
 * - The 16-digit transaction id is a *message prefix*, not a label: `0000015061132227
 *   Confirmed.` The spec has a dedicated pattern for that shape, and it is the single most
 *   useful discriminator against spoofed alerts, which omit or placeholder it.
 * - Names follow the counterparty MSISDN after a dash ("to 0241234567 - JOHN PLACEHOLDER")
 * - Dates are ISO (`2025-09-10`) here but day-first (`10/09/2025`) in the same operator's
 *   other templates, so the parser reports ambiguous d/m/y readings to the harness
 *
 * Country: Ghana (GH₵, cedi).
 *
 * @see MtnMoMoParser
 * @see AirtelTigoMoneyParser
 */
class TelecelCashParser : GhanaMobileMoneyParser(operatorId = "telecel_cash", bankDisplayName = "Telecel Cash")
