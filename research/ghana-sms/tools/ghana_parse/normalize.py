"""Field normalizers shared by the Python reference implementation and (by mirror)
the Kotlin on-device extractor.

Every normalizer is total: given junk it returns ``None`` rather than raising, so a
malformed SMS can never crash the parsing loop.
"""

from __future__ import annotations

import json
import re
from datetime import date, datetime
from pathlib import Path
from typing import Optional
from decimal import Decimal, InvalidOperation

_WS_RE = re.compile(r"\s+")
_MONTHS = {
    "jan": 1, "feb": 2, "mar": 3, "apr": 4, "may": 5, "jun": 6,
    "jul": 7, "aug": 8, "sep": 9, "sept": 9, "oct": 10, "nov": 11, "dec": 12,
}
# Words that operators use right after a counterparty name and that must not be
# swallowed by a greedy name capture.
_NAME_STOPWORDS = {
    "ON", "AT", "IN", "TO", "FROM", "BY", "FOR", "VIA", "REF", "TXN", "TRX",
    "THE", "YOUR", "NEW", "BALANCE", "TRANSACTION", "FEE", "LEVY", "CONFIRMED",
    "THANK", "YOU", "USE", "USING", "PAID", "SENT", "RECEIVED", "DEBIT", "CREDIT",
}

# Weak prior only: number portability makes prefix -> network unreliable. taxonomy.py
# cross-tabs observed prefixes against senders so the real distribution is measured,
# not assumed. TODO(verify): reconcile against the NCA national numbering plan.
MSISDN_PREFIX_HINTS = {
    "24": "MTN", "53": "MTN", "54": "MTN", "55": "MTN", "59": "MTN", "82": "MTN", "83": "MTN",
    "20": "TELECEL", "50": "TELECEL", "70": "TELECEL", "79": "TELECEL",
    "27": "AIRTILTIGO", "57": "AIRTILTIGO", "28": "AIRTILTIGO", "56": "AIRTILTIGO",
}


def tidy_text(text: str) -> str:
    """Whitespace + unicode hygiene applied before any regex runs.

    Multi-part SMS arrive with newlines and double spaces; iOS/Android smart quotes
    and non-breaking spaces break naive character classes.
    """
    if not text:
        return ""
    out = text.replace("\u00a0", " ").replace("\u2019", "'").replace("\u2018", "'")
    out = out.replace("\u201c", '"').replace("\u201d", '"').replace("\u2013", "-").replace("\u2014", "-")
    out = out.replace("\r", "\n")
    out = _WS_RE.sub(" ", out)
    return out.strip()


def normalize_amount(value: Optional[str]) -> Optional[str]:
    """'1,299.5' -> '1299.50'. Rejects non-monetary numbers and implausible magnitudes."""
    if value is None:
        return None
    cleaned = re.sub(r"[^0-9.]", "", str(value))
    if cleaned.count(".") > 1:
        cleaned = cleaned.replace(".", "", cleaned.count(".") - 1)
    cleaned = cleaned.strip(".")
    if not cleaned or not re.fullmatch(r"[0-9]{1,13}(\.[0-9]{1,2})?", cleaned):
        return None
    try:
        amount = Decimal(cleaned)
    except InvalidOperation:
        return None
    if amount < Decimal("0") or amount > Decimal("9999999999"):
        return None
    return f"{amount.quantize(Decimal('0.01'))}"


def normalize_date(value: Optional[str], *, default_year: Optional[int] = None) -> Optional[str]:
    """Any operator date shape -> ISO ``YYYY-MM-DD``.

    Ghana writes day-first (31/12/2025). A d/m/y reading with day <= 12 is *ambiguous*
    against a US-style m/d/y; ``date_is_ambiguous`` exposes that count for the paper.
    """
    if value is None:
        return None
    text = str(value).strip().rstrip(".,;")
    if not text:
        return None

    iso = re.fullmatch(r"(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})", text)
    if iso:
        year, month, day = (int(x) for x in iso.groups())
    else:
        dmy = re.fullmatch(r"(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})", text)
        if dmy:
            day, month, year = (int(x) for x in dmy.groups())
            if year < 100:
                year += 2000 if year < 70 else 1900
        else:
            month_name = re.fullmatch(
                r"(\d{1,2})[\s-]([A-Za-z]{3,9})[\s-](\d{4})", text
            ) or re.fullmatch(r"([A-Za-z]{3,9})\s+(\d{1,2}),?\s+(\d{4})", text)
            if month_name:
                a, b, c = month_name.groups()
                if a.isdigit():
                    day, month_txt, year = int(a), b, int(c)
                else:
                    month_txt, day, year = a, int(b), int(c)
                month = _MONTHS.get(month_txt[:4].lower()[:3]) or _MONTHS.get(month_txt[:3].lower())
                if month is None:
                    return None
            else:
                year_only = re.fullmatch(r"(\d{4})", text)
                if year_only and default_year:
                    return None
                return None
    try:
        return date(year, month, day).isoformat()
    except ValueError:
        return None


def date_is_ambiguous(value: Optional[str]) -> bool:
    """True when a numeric date could be read as either d/m/y or m/d/y."""
    if not value:
        return False
    m = re.fullmatch(r"(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})", str(value).strip())
    if not m:
        return False
    a, b = int(m.group(1)), int(m.group(2))
    return a <= 12 and b <= 12


def normalize_time(value: Optional[str]) -> Optional[str]:
    """'13:51:07' / '1:51 pm' -> '13:51'."""
    if value is None:
        return None
    text = str(value).strip().lower().replace(".", "")
    m = re.fullmatch(r"(\d{1,2}):(\d{2})(?::\d{2})?\s*(am|pm)?", text)
    if not m:
        return None
    hour, minute, meridiem = int(m.group(1)), int(m.group(2)), m.group(3)
    if hour > 23 or minute > 59:
        return None
    if meridiem == "pm" and hour < 12:
        hour += 12
    elif meridiem == "am" and hour == 12:
        hour = 0
    return f"{hour:02d}:{minute:02d}"


def normalize_phone(value: Optional[str]) -> Optional[str]:
    """Any Ghana MSISDN shape -> local '0XXXXXXXXX'. Returns None for foreign numbers."""
    if value is None:
        return None
    digits = re.sub(r"[^0-9]", "", str(value))
    if len(digits) == 9 and not digits.startswith("0"):
        digits = "0" + digits
    elif len(digits) == 12 and digits.startswith("233"):
        digits = "0" + digits[3:]
    elif len(digits) == 11 and digits.startswith("0"):
        pass
    elif len(digits) == 11 and digits.startswith("233"):
        digits = "0" + digits[3:]
    if not re.fullmatch(r"0[2-5][0-9]{8}", digits):
        return None
    return digits


def network_hint(local_phone: Optional[str]) -> Optional[str]:
    if not local_phone:
        return None
    return MSISDN_PREFIX_HINTS.get(local_phone[1:3])


def normalize_name(value: Optional[str]) -> Optional[str]:
    """Counterparty / merchant name: strip delimiters and trailing clause fragments."""
    if value is None:
        return None
    text = _WS_RE.sub(" ", str(value)).strip()
    # A trailing parenthetical is a qualifier the ledger does not want ("ECG (POWER)",
    # "SHOPRITE (ACCRA)"). Drop the whole balanced group before trimming stray brackets,
    # otherwise 'ECG (POWER)' would come back as 'ECG (POWER'.
    text = re.sub(r"\s*\([^)]*\)\s*$", "", text)
    text = text.strip(" .,:;-–—()[]/\\")
    # Drop a trailing ' on 2025-09-10' style tail a lazy capture may have kept.
    text = re.split(r"\s+(?:on|at|in)\s+\d", text)[0].strip()
    words = text.split(" ")
    while words and words[-1].upper().rstrip(".") in _NAME_STOPWORDS:
        words.pop()
    text = " ".join(words).strip(" .,:;-")
    text = re.sub(r"\s*-\s*$", "", text)
    return text or None


def name_key(value: Optional[str]) -> Optional[str]:
    """Canonical comparison key for names (case and punctuation insensitive)."""
    if not value:
        return None
    cleaned = re.sub(r"[^A-Z0-9 ]", "", normalize_name(value).upper() if normalize_name(value) else "")
    cleaned = _WS_RE.sub(" ", cleaned).strip()
    return cleaned or None


def normalize_trx_id(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    text = re.sub(r"[^A-Za-z0-9]", "", str(value)).upper()
    return text or None


def normalize_channel(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    text = str(value).lower()
    if "agent" in text:
        return "agent"
    if "atm" in text or "self-service" in text or "self service" in text:
        return "atm"
    if "pos" in text or "mopay" in text or "mo mopay" in text or "card" in text:
        return "pos"
    if "#" in text:
        return "ussd"
    if "app" in text or "online" in text or "web" in text:
        return "app"
    return "other"


def normalize_account(value: Optional[str]) -> Optional[str]:
    """Paybill / bill / meter account: digits and separators only, spaces removed."""
    if value is None:
        return None
    text = re.sub(r"[^0-9A-Za-z/-]", "", str(value)).strip()
    return text or None


def to_decimal(value: Optional[str]) -> Optional[Decimal]:
    norm = normalize_amount(value)
    if norm is None:
        return None
    try:
        return Decimal(norm)
    except InvalidOperation:
        return None


def parse_timestamp(value: Optional[str]) -> Optional[int]:
    """Epoch millis for a device/SMS timestamp string, or None if unparseable."""
    if value is None:
        return None
    text = str(value).strip()
    if re.fullmatch(r"\d{13}", text):
        return int(text)
    if re.fullmatch(r"\d{10}", text):
        return int(text) * 1000
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M", "%d/%m/%Y %H:%M:%S", "%d/%m/%Y %H:%M"):
        try:
            return int(datetime.strptime(text[: len(fmt) + 4].replace("Z", ""), fmt).timestamp() * 1000)
        except ValueError:
            continue
    return None


# Operator boilerplate. Left in place it poisons field extraction: 'Dial *170# to manage
# your wallet' reads as a USSD channel and 'Top up more at any agent' as an agent
# transaction, on messages that say neither. Templates are ^-anchored and unaffected, so
# the boilerplate is only stripped for the per-field pass (see engine.parse).
_SPEC_PATH = Path(__file__).resolve().parents[2] / "spec" / "ghana_patterns.json"


def _spec_boilerplate() -> list:
    """The boilerplate list lives in the spec so Kotlin and Python share one definition."""
    try:
        with open(_SPEC_PATH, "r", encoding="utf-8") as handle:
            return list(json.load(handle).get("boilerplate_patterns", []))
    except (OSError, ValueError):  # pragma: no cover - only if the repo is half-checked-out
        return []


BOILERPLATE = _spec_boilerplate() or [
    r"dial\s+\*?[0-9#*]+\s*#?[^.]{0,40}(?:to manage|to stay connected|to buy|to apply|for more)[^.]*\.",
    r"keep your pin secret[^.]*\.",
    r"never share (?:it|your pin)[^.]*\.",
    r"do not reply to this message[^.]*\.",
    r"not you\?[^.]{0,40}\.",
    r"thank you for using[^.]*\.",
    r"top up more at any agent[^.]*\.",
    r"visit any agent[^.]*\.",
    r"download the [a-z0-9 ]+app[^.]*\.",
]
_BOILERPLATE_RE = re.compile("(?:^|(?<=\s))(?:" + "|".join(BOILERPLATE) + ")", re.IGNORECASE)


def strip_boilerplate(text: str) -> str:
    """Remove operator boilerplate sentences. Returns the transactional core."""
    if not text:
        return text
    cleaned = _BOILERPLATE_RE.sub(" ", text)
    cleaned = _WS_RE.sub(" ", cleaned).strip(" .,;")
    return cleaned or text
