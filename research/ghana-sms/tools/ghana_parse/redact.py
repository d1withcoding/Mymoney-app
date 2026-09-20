"""PII redaction applied *before* anything is written into the repository.

Two tiers, because a privacy paper needs both:

``--tier publish``   everything identifying is removed. Safe to commit, publish, put in
                     an appendix. Merchant names are also replaced by an invented-name
                     pool, so a reader cannot trace a message back to a trader.
``--tier analysis``  (default) personal counterparty names are pseudonymised with a
                     deterministic, non-reversible mapping; *business* names are kept,
                     because they are public and they are the signal the classifier in
                     Objective 3 needs. MSISDNs keep their 3-digit network prefix only
                     (the prefix is what Objective 1 studies, the subscriber is nobody's
                     business). Transaction IDs keep 4 characters (dedup still works,
                     linkage to the operator's ledger does not).

Every transform is deterministic so the same export always yields the same corpus,
which is what makes the reported metrics reproducible.
"""

from __future__ import annotations

import hashlib
import re
from typing import Iterable, List, Optional, Tuple

# Invented, deliberately non-Ghanaian-adjacent names: if pseudonymisation ever leaks,
# it leaks a name that belongs to nobody.
PSEUDO_NAMES = [
    "AVA QUILL", "BEN TORCH", "CIRA VELLO", "DANE MERWICK", "EILA BRANNON",
    "FENN ALDRAY", "GUS HEMLIN", "HADA SPEIRN", "IVOR KELSEY", "JONA PELHAM",
    "KIRA WOLDRY", "LUEN MARSHAW", "MIDA CARROW", "NOLL TIRAY", "ODIN FELTHAM",
    "PERRIN VASLOW", "QUINN HARKELL", "ROSALD MEYRICK", "SABLE DYNELEY", "TARN EDGARLY",
    "URSAL KEATING", "VADO STRINER", "WREN OAKLEY", "XANDOR PILE", "YORIK DALNE",
    "ZELA BRAMLEY", "ADIN COLEWAR", "BRANN ISELY", "CORWIN DEALEY", "DORSET HYNES",
]

# A Ghanaian MSISDN is 10 digits nationally (0 + 2-digit network code + 7 subscriber
# digits) or +233 + 9. Grouped as 0XX XXX XXXX in most operator messages. The lookahead
# stops a 16-digit transaction id from being harvested as a phone number.
MSISDN_RE = re.compile(
    r"(?:(?<=\D)|^)(?:\+?233[\s-]?|0)([2-5][0-9])[ -]?([0-9]{3})[ -]?([0-9]{4})(?![0-9])"
)
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
URL_RE = re.compile(r"(?:https?://|www\.)\S+", re.IGNORECASE)
TRX_ID_RE = re.compile(
    r"\b(transaction\s*(?:id|ref(?:erence)?)?|txn\s*id|trx\s*id|trans\s*id|tid|txn|ref)\b\s*[:#]?\s*([A-Z0-9]{6,20})",
    re.IGNORECASE,
)
ACCOUNT_RE = re.compile(
    r"\b(paybill|business|merchant|till|bill|meter|account)\s*(?:no|number|id|code|ref)?\s*[:#]?\s*([0-9][0-9 -]{3,19})",
    re.IGNORECASE,
)
# An upper-case multi-word run that is not a known business marker is treated as a
# person's name. This is a heuristic, not a guarantee: the annotation UI shows every
# kept name so the author can downgrade it to a pseudonym by hand.
UPPER_RUN_RE = re.compile(r"\b((?:[A-Z][A-Z'.\-]{1,20}[\s\-]?){1,4}[A-Z][A-Z'.\-]{1,20})\b")

BUSINESS_HINTS = {
    "LIMITED", "LTD", "GHANA", "COMPANY", "CO", "PLC", "ECG", "GRID", "GWCL", "GIPA",
    "GRA", "DWLS", "PST", "DSTV", "STARTIMES", "CABLEKITCH", "GOIL", "SHOPRITE",
    "LIBERTY", "MELCOM", "JUMIA", "ZAGGA", "UBER", "BOLT", "HEYGO", "YANGO", "OGT",
    "SHELL", "TOTAL", "ESSO", "GOIL", "STC", "VIP", "AfIA", "RENT", "LANDLORD",
    "SCHOOL", "COLLEGE", "UNIVERSITY", "CAMPUS", "HOSPITAL", "CLINIC", "PHARMACY",
    "CINEMATRIX", "SHOWMAX", "MTN", "TELECEL", "AIRTELTIGO", "TIGO", "GCB", "ECOBANK",
    "STANBIC", "FIDELITY", "CALIBER", "WARI", "KOTOKO", "SCU", "NATECOM", "RDC",
    "COURT", "CHAMBER", "MARKET", "MOTORING", "AUTOS", "GARAGE", "HOTEL", "LOUNGE",
    "RESTAURANT", "BISTRO", "CHOP", "BAR", "GRILL", "PIZZA", "BURGER", "BAKERY",
    "PROVISIONS", "STORE", "SUPERMARKET", "MALL", "SALON", "SPABAR", "CLEANERS",
}


def _slot(*parts: str, width: int = 8) -> str:
    digest = hashlib.sha256("|".join(parts).encode("utf-8")).hexdigest()
    return digest[:width]


def mask_msisdn(text: str, keep: str = "prefix") -> str:
    """'0241234567' -> '024XXXXXXX' (analysis) or '0XXNNNNNNNN' (publish)."""

    def repl(match: "re.Match[str]") -> str:
        pre, mid, last = match.group(1), match.group(2), match.group(3)
        hidden = len(mid) + len(last)
        if keep == "none":
            return "0" + "X" * (len(pre) + hidden)
        return "0" + pre + "X" * hidden

    return MSISDN_RE.sub(repl, text)


def mask_trx_ids(text: str, keep: str = "four") -> str:
    def repl(match: "re.Match[str]") -> str:
        label, value = match.group(1), match.group(2)
        if keep == "none":
            return f"{label}: TXNREDACTED"
        head = value[:4] if keep == "four" else value[:2]
        return f"{label}: {head}{'X' * max(0, len(value) - len(head))}"

    return TRX_ID_RE.sub(repl, text)


def mask_accounts(text: str, keep_last: int = 3) -> str:
    def repl(match: "re.Match[str]") -> str:
        label, value = match.group(1), match.group(2)
        digits = re.sub(r"[^0-9]", "", value)
        if len(digits) <= keep_last:
            return match.group(0)
        return f"{label} {'X' * (len(digits) - keep_last)}{digits[-keep_last:]}"

    return ACCOUNT_RE.sub(repl, text)


def pseudonym_for(name: str) -> str:
    idx = int(_slot("ghana-pseudonym-v1", name.upper(), width=6), 16) % len(PSEUDO_NAMES)
    pseudo = PSEUDO_NAMES[idx]
    if name.isupper():
        return pseudo.upper()
    if name.istitle() or " " not in name.strip():
        return pseudo.title()
    return pseudo


# Message vocabulary that an operator renders in capitals and that a naive
# "upper-case run == person" heuristic would happily pseudonymise. Losing "ELECTRICITY
# BILL" to a fake name would destroy the field Objective 3 needs most, so these words are
# treated as non-personal, exactly like business names.
MESSAGE_LABEL_HINTS = {
    "ELECTRICITY", "WATER", "BILL", "BILLS", "BILLING", "AIRTIME", "DATA", "BUNDLE",
    "TOPUP", "TOP", "PAYBILL", "CASH", "IN", "OUT", "PAYMENT", "PURCHASE", "REFUND",
    "REVERSAL", "TRANSFER", "DEPOSIT", "WITHDRAWAL", "WITHDRAW", "BALANCE", "STATEMENT",
    "PREPAID", "POSTPAID", "TARIFF", "PROMO", "PROMOTION", "FEE", "LEVY", "TXN", "TRX",
    "TRANSACTION", "REFERENCE", "MOBILE", "MONEY", "WALLET", "SERVICE", "MESSAGE",
    "CONFIRMED", "SUCCESSFUL", "FAILED", "PENDING", "TEMPORARY", "PIN", "PASSWORD",
    "CUS", "TODAY", "TOMORROW", "EXPIRED", "VALID", "THANK", "YOU", "FOR", "USING",
}

_NON_PERSONAL = {b.upper() for b in BUSINESS_HINTS} | {w.upper() for w in MESSAGE_LABEL_HINTS}


def is_business_like(name: str) -> bool:
    """True when an upper-case run should be *kept*: a business, or message vocabulary."""
    tokens = {t.strip(".").upper() for t in re.split(r"[\s\-]+", name) if t.strip(".")}
    return bool(tokens & _NON_PERSONAL)


def pseudonymize_personal_names(text: str, mode: str = "personal-only") -> Tuple[str, List[Tuple[str, str]]]:
    """Replace personal names with the pool. Returns (text, [(original, replacement)]).

    mode: ``none`` | ``personal-only`` (default) | ``all``
    The mapping list is returned for the *private* audit log only and is never written
    to the repository by ingest.py.
    """
    if mode == "none":
        return text, []
    mapping: List[Tuple[str, str]] = []

    def repl(match: "re.Match[str]") -> str:
        run = match.group(1)
        if mode != "all" and is_business_like(run):
            return run
        replacement = pseudonym_for(run)
        mapping.append((run, replacement))
        return replacement

    return UPPER_RUN_RE.sub(repl, text), mapping


def redact_body(
    body: str,
    tier: str = "analysis",
    names_mode: str = "personal-only",
) -> Tuple[str, List[Tuple[str, str]]]:
    """Full pipeline: emails/URLs, MSISDNs, transaction IDs, accounts, personal names."""
    text = body or ""
    text = EMAIL_RE.sub("<email-redacted>", text)
    text = URL_RE.sub("<url-redacted>", text)
    if tier == "publish":
        text = mask_msisdn(text, keep="none")
        text = mask_trx_ids(text, keep="none")
        text = mask_accounts(text, keep_last=0)
        text, mapping = pseudonymize_personal_names(text, mode="all")
        return text, mapping
    # Order matters: a labelled bill account is stronger evidence than a bare digit run, and
    # Ghanaian account numbers are frequently 10 digits, i.e. indistinguishable from an MSISDN.
    # Masking accounts first keeps their documented "last three" context instead of losing it to
    # the phone pattern (tools/test_redaction.py pins this).
    text = mask_accounts(text, keep_last=3)
    text = mask_trx_ids(text, keep="four")
    text = mask_msisdn(text, keep="prefix")
    text, mapping = pseudonymize_personal_names(text, mode=names_mode)
    return text, mapping


def redact_value(field_name: str, value: Optional[str], tier: str = "analysis") -> Optional[str]:
    """Apply the same transform to a gold-annotation value so gold and text stay aligned."""
    if value in (None, ""):
        return value
    text = str(value)
    if field_name in ("counterparty_phone",):
        return None if tier == "publish" else _first_masked_msisdn(text, tier)
    if field_name == "trx_id":
        digits = re.sub(r"[^A-Z0-9]", "", text.upper())
        if tier == "publish":
            return "TXNREDACTED"
        return (digits[:4] + "X" * max(0, len(digits) - 4)) or None
    if field_name == "paybill_account":
        digits = re.sub(r"[^0-9]", "", text)
        if tier == "publish":
            return None
        keep = 3
        return ("X" * max(0, len(digits) - keep) + digits[-keep:]) if digits else None
    if field_name in ("counterparty_name", "paybill_name"):
        if tier == "publish":
            return pseudonym_for(text)
        if names_mode_state.get("mode") == "none":
            return text
        if is_business_like(text):
            return text
        return pseudonym_for(text)
    return text


def _first_masked_msisdn(text: str, tier: str) -> Optional[str]:
    masked = mask_msisdn(text, keep="none" if tier == "publish" else "prefix")
    found = re.search(r"0[2-5][0-9X]{8}", masked)
    return found.group(0) if found else None


# module-level knob so redact_value can honour ingest.py's --names mode without a
# parameter that would have to be threaded through every caller.
names_mode_state = {"mode": "personal-only"}


def set_names_mode(mode: str) -> None:
    names_mode_state["mode"] = mode
