#!/usr/bin/env python3
"""Redaction invariants. Run: ``python3 tools/test_redaction.py``.

Every number and name below is invented (sequential dummy MSISDNs, fake surnames). The tests exist
because the whole corpus contract rests on one sentence - *no PII leaves ``corpus/raw/``* - and that
sentence was false for a while without anything noticing: the MSISDN pattern asked for ten subscriber
digits where Ghana has nine, so real numbers passed through unredacted. A privacy claim needs a
test, not a comment.

These also pin the *tiers*: the analysis tier keeps business names on purpose (they are Objective 3
signal), the publish tier must not.
"""

from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from ghana_parse import redact as R  # noqa: E402

FAILURES = []


def check(condition: bool, what: str) -> None:
    if not condition:
        FAILURES.append(what)


def main() -> int:
    # --- MSISDNs ---------------------------------------------------------------------------
    for raw, masked in [
        ("sent to 0241234567 -", "024XXXXXXX"),
        ("to 0271234567 (name)", "027XXXXXXX"),
        ("paid 0549876543 now", "054XXXXXXX"),
        ("call +233 24 123 4567", "024XXXXXXX"),
        ("tel 020-987-6543", "020XXXXXXX"),
    ]:
        out = R.redact_body(raw)[0]
        check(masked in out, f"analysis tier must mask to {masked}: {out!r}")
        check("1234567" not in out and "9876543" not in out, f"no digit run survives: {out!r}")
    publish = R.redact_body("sent to 0241234567", tier="publish")[0]
    # nothing but the national leading zero survives; a lone '0' identifies nobody, a run of
    # three or more digits means something was not masked
    check(re.search(r"\d{3}", publish) is None,
          f"publish tier must leave no digit run: {publish!r}")

    # --- a 16-digit transaction id must not be harvested as a phone, and vice versa --------
    trx = "Transaction ID: 0000015061132227 Confirmed"
    out = R.redact_body(trx)[0]
    check("0000XXXXXXXXXXXX" in out, f"trx id keeps 4 chars: {out!r}")

    # --- names vs businesses vs message vocabulary ----------------------------------------
    text = "Paid GHS 210.75 to ECG for ELECTRICITY BILL. Refund to SAMPLE COUNTERPARTY after REVERSAL."
    out, mapping = R.redact_body(text)
    check("ECG" in out, f"business name kept in the analysis tier: {out!r}")
    check("ELECTRICITY BILL" in out, f"message vocabulary is not a person: {out!r}")
    check("SAMPLE COUNTERPARTY" not in out, f"personal name pseudonymised: {out!r}")
    check(any(orig == "SAMPLE COUNTERPARTY" for orig, _ in mapping), f"audit map records the swap: {mapping}")
    again, second = R.redact_body(text)
    check(out == again, "redaction is deterministic (same corpus from the same export)")
    check([p for p, _ in mapping] == [p for p, _ in second], "pseudonyms are stable")
    other, _ = R.redact_body("Paid to SAMPLE COUNTERPARTY separately")
    pseudo = [repl for orig, repl in mapping if orig == "SAMPLE COUNTERPARTY"][0]
    check(pseudo in other, "the same person keeps the same pseudonym across messages")
    check(all(n == n.upper() for n in R.PSEUDO_NAMES), "pseudonym pool is uniformly cased")
    check(R.redact_body("Payment made by ANOTHER PARTY", names_mode="none")[0].count("ANOTHER PARTY") == 1,
          "names_mode=none is a no-op on names")

    # --- accounts and paybills -------------------------------------------------------------
    acct = R.redact_body("paid GHS 5.00 to MECE Account: 0301123456")[0]
    check("0301123456" not in acct and acct.endswith("456"), f"account keeps last 3 only: {acct!r}")

    # --- gold values go through the same transform -----------------------------------------
    check(R.redact_value("counterparty_name", "SAMPLE COUNTERPARTY") != "SAMPLE COUNTERPARTY", "gold names are redacted too")
    check(R.redact_value("counterparty_phone", "0271234567", "analysis") == "027XXXXXXX",
          f"gold phone masked the same way as the body: {R.redact_value('counterparty_phone', '0271234567')!r}")
    check(R.redact_value("counterparty_phone", "0271234567", "publish") is None,
          "publish tier drops the field rather than lying about it")

    if FAILURES:
        print("redaction invariant failures:")
        for line in FAILURES:
            print("  -", line)
        return 1
    print("redaction invariants hold")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
