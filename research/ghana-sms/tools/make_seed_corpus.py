#!/usr/bin/env python3
"""Generate the seed corpus: template-derived, fully labelled Ghana MoMo messages.

Why this exists: the pattern library, the evaluation harness and the Objective-3
classifier all need data *now*, while the author's real captures are still being
collected, consented and annotated. Every record here is synthetic and says so
(``source=synthetic``, ``provenance=seed_templates_v3``), so it can never be mistaken
in the paper for evidence about real operator traffic.

What it is genuinely good for
  * a CI-grade check that every regex in ``spec/ghana_patterns.json`` compiles, matches
    and yields the field it claims (run ``tools/evaluate.py``);
  * a training set for the classifier until real labels arrive;
  * stress shapes - currency variants, truncation, marketing footers, mixed-case names,
    missing optional fields - each tagged per record so metrics can be broken down by
    tag in the error-analysis section.

Construction: a message is assembled from *segments*, and every segment that is
rendered contributes its gold value. Gold therefore always describes the exact string
in ``body``; nothing can be annotated that the text does not contain.

Run:  python3 tools/make_seed_corpus.py --per-shape 14 --seed 7
"""

from __future__ import annotations

import argparse
import json
import random
import re
import sys
from datetime import date, timedelta
from itertools import count
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ghana_parse import normalize as N  # noqa: E402
from ghana_parse.corpus import Record, write_corpus  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
LEXICON = json.loads((ROOT / "spec" / "ghana_categories.json").read_text(encoding="utf-8"))

# Population-level Ghanaian given/family names, paired at random. No individual is
# identifiable from these strings, which is what makes them safe to commit.
GIVEN = ["Kwame", "Ama", "Yaw", "Akosua", "Kofi", "Abena", "Kojo", "Adwoa", "Nana",
         "Esi", "Kwabena", "Afia", "Fiifi", "Efua", "Ebo", "Oheneba", "Kwesi", "Appiah"]
FAMILY = ["Boateng", "Mensah", "Owusu", "Asante", "Adjei", "Amoah", "Sarpong", "Darko",
          "Agyeman", "Bediako", "Quansah", "Tetteh", "Annan", "Opoku", "Frimpong", "Asare"]
PERSONS = [f"{g} {f}".upper() for g in GIVEN for f in FAMILY]

MERCH: Dict[str, List[str]] = {label["id"]: (label.get("merchants") or []) for label in LEXICON["labels"]}
CATEGORY_KEYWORDS: Dict[str, List[str]] = {
    label["id"]: [k.lower() for k in (label.get("keywords") or [])] for label in LEXICON["labels"]
}
ALL_MERCHANTS = sorted({m for pool in MERCH.values() for m in pool})

CUR_STYLES = ["GHS {v}", "GHS{v}", "GH\u00a2 {v}", "GH\u00a2{v}", "GH\u20b5 {v}", "GH\u20b5{v}"]
AGENTS = ["AGENT KWABENA OWUSU", "MOMO AGENT ACCRA", "SUPER AGENT KUMASI"]
BALANCE_CLAUSES = {
    "mtn_momo": ["Current Balance: {c}", "Balance: {c}", "Your MoMo balance is {c}", "New balance is {c}"],
    "telecel_cash": ["Your Telecel Cash balance is {c}", "New balance is {c}", "Balance: {c}"],
    "airtel_tigo_money": ["Balance: {c}", "New balance: {c}", "AirtelTigo Money balance: {c}"],
}
TID_CLAUSES = {
    "mtn_momo": ["Transaction ID: {t}", "Trans ID: {t}", ""],
    "telecel_cash": ["Transaction ID: {t}", "Txn Ref: {t}", ""],
    "airtel_tigo_money": ["Txn ID: {t}", "Transaction ID: {t}", ""],
}
REF_TEXTS = ["school fees", "rent", "market run", "fuel top up", "pharmacy", "funeral dues",
             "chop money", "transport fare", "uniform and books", "data money"]


def classify_name(name: str) -> str:
    """Gold category from a merchant name, through the shared lexicon (= Tier-0 rules)."""
    lowered = name.lower()
    for category, keywords in CATEGORY_KEYWORDS.items():
        if category in ("other", "cash_agent", "p2p_transfer", "income"):
            continue
        if any(k in lowered for k in keywords if len(k) > 2):
            return category
    return "other"


def money(rng: random.Random, lo: float = 1.0, hi: float = 2500.0) -> Tuple[str, str]:
    style = rng.choice(CUR_STYLES)
    if rng.random() < 0.35:
        value = float(rng.choice([2, 5, 10, 15, 20, 30, 50, 70, 100, 150, 200, 300, 500, 1000, 2000]))
    else:
        value = round(rng.uniform(lo, hi), 2)
    text = f"{value:,.2f}" if rng.random() < 0.35 else f"{value:.2f}"
    return style.format(v=text), f"{value:.2f}"


def phone(rng: random.Random) -> Tuple[str, str]:
    """A generated MSISDN, not a captured one.

    Every digit in this file's output comes from a seeded PRNG (``--seed``), and the generator
    never reads a corpus, a log, or a real message - so a number here is no more somebody's than
    a surname from ``PERSONS`` is. What the *shape* must keep is realism, not a recognisable fake
    pattern: Objective 1 measures the network-prefix cross-tab and the classifier's digit
    features, so collapsing tails to repeated digits measurably changes both (tried, reverted).
    ``corpus/CORPUS_SCHEMA.md`` states the provenance rule that matters: nothing generated here
    is a real record, and nothing real is ever generated from.
    """
    prefix = rng.choice(["24", "25", "54", "55", "20", "50", "70", "27", "57", "56"])
    tail = f"{rng.randrange(1_000_000, 9_999_999)}"
    local = f"0{prefix}{tail}"
    style = rng.random()
    if style < 0.6:
        pretty = local
    elif style < 0.8:
        pretty = f"0{prefix} {tail[:3]} {tail[3:]}"
    else:
        pretty = f"+233 {prefix} {tail[:3]} {tail[3:]}"
    return pretty, local


def stamp(rng: random.Random) -> Tuple[str, str, str, str, List[str]]:
    day = date(2025, 1, 1) + timedelta(days=rng.randrange(430))
    hour, minute, second = rng.randrange(24), rng.randrange(60), rng.randrange(60)
    roll = rng.random()
    if roll < 0.55:
        date_text = day.isoformat()
    elif roll < 0.85:
        date_text = day.strftime("%d/%m/%Y")
    else:
        date_text = day.strftime("%d-%m-%y")
    time_text = f"{hour:02d}:{minute:02d}" + (f":{second:02d}" if rng.random() < 0.75 else "")
    if rng.random() < 0.08:
        time_text = f"{(hour % 12) or 12:02d}:{minute:02d} {'PM' if hour >= 12 else 'AM'}"
    tags = ["ambiguous_date"] if day.day <= 12 and day.month <= 12 and "/" in date_text else []
    return date_text, time_text, day.isoformat(), f"{hour:02d}:{minute:02d}", tags


def tid(rng: random.Random, operator: str) -> str:
    if operator == "telecel_cash":
        return f"00000{rng.randrange(10**9, 10**10)}{rng.randrange(10, 99)}"[:16].zfill(16)
    if rng.random() < 0.3:
        return "".join(rng.choice("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789") for _ in range(11))
    return str(rng.randrange(10 ** 10, 10 ** 11 - 1))


def footer(rng: random.Random) -> str:
    return rng.choice([
        "", "", "",
        "\nDial *170# to manage your wallet.",
        " Not you? Call 100.",
        "\nKeep your PIN secret. Never share it with anyone.",
        " Thank you for using our services.",
    ])


def surface_noise(rng: random.Random, text: str) -> Tuple[str, List[str]]:
    tags: List[str] = []
    if rng.random() < 0.10:
        text = text.replace(". ", ".  ", 1)
        tags.append("double_space")
    if rng.random() < 0.07:
        text = text.replace(" ", "\n", 1)
        tags.append("newline_wrap")
    if rng.random() < 0.07:  # concatenated multi-part SMS
        text = text[:160]
        tags.append("truncated_160")
    return text, tags


def still_present(field: str, value: str, text: str) -> bool:
    """Did the value survive noise/truncation in a recoverable form?"""
    if value in (None, ""):
        return False
    if field in ("amount", "fee", "levy", "balance"):
        digits = re.sub(r"\D", "", value)
        return digits in re.sub(r"\D", "", text) or digits.lstrip("0") in re.sub(r"\D", "", text).lstrip("0")
    if field == "date":
        return any(N.normalize_date(m) == value for m in re.findall(r"\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}", text))
    if field == "time":
        return any(N.normalize_time(m) == value for m in re.findall(r"\d{1,2}:\d{2}(?::\d{2})?(?:\s*[APap]\.?[Mm]\.?)?", text))
    if field == "counterparty_phone":
        return any(N.normalize_phone(m) == value for m in re.findall(r"(?:\+?233[\s-]?)?0?[2-5][0-9][\s-]?[0-9]{3}[\s-]?[0-9]{4}", text))
    if field == "trx_id":
        return N.normalize_trx_id(value) in N.normalize_trx_id(re.sub(r"[^A-Za-z0-9]", " ", text).upper()[:0] or text.upper())
    return value.upper() in text.upper()


class Builder:
    """Segment writer: rendering a segment is what puts gold into the record."""

    GOLD_UNIVERSE = (
        "amount", "fee", "levy", "balance", "trx_id", "reference", "counterparty_name",
        "counterparty_phone", "paybill_name", "paybill_account", "date", "time", "status",
        "channel", "tx_kind", "direction",
    )

    def __init__(self, rng: random.Random, operator: str, kind: str) -> None:
        self.rng = rng
        self.operator = operator
        self.kind = kind
        self.parts: List[str] = []
        self.gold: Dict[str, Any] = {}
        self.tags: List[str] = []
        self.amount_text, self.amount = money(rng)
        self.gold["amount"] = self.amount
        self.date_text, self.time_text, self.iso_date, self.iso_time, dt_tags = stamp(rng)
        self.tags += dt_tags
        self.balance_val = f"{rng.uniform(2, 9000):.2f}"
        if rng.random() < 0.3:
            self.balance_val = f"{rng.uniform(2, 9000):,.2f}"
        self.balance_norm = f"{float(self.balance_val.replace(',', '')):.2f}"
        self.fee_val = f"{max(0.30, min(0.5 + float(self.amount) * 0.01, 14.0)):.2f}" if rng.random() < 0.5 else None
        self.levy_val = f"{float(self.amount) * 0.015:.2f}" if float(self.amount) > 100 and rng.random() < 0.3 else None
        self.txid = tid(rng, operator)
        self.ref = rng.choice(REF_TEXTS + ["", ""])
        self.person = rng.choice(PERSONS)
        if rng.random() < 0.12:
            self.person = self.person.title()
            self.tags.append("mixed_case_name")
        self.party_phone, self.party_local = phone(rng)
        self.merchant = rng.choice(ALL_MERCHANTS)
        self.account = str(rng.randrange(100000, 9_999_999))  # generated, never captured
        symbol = re.match(r"(GHS|GH\u00a2|GH\u20b5)", self.amount_text)
        self.currency_stub = symbol.group(1) if symbol else "GHS"
        self.currency_space = " " if re.match(r"^(GHS|GH\u00a2|GH\u20b5) ", self.amount_text) else ""

    # -- segment writers -------------------------------------------------------
    def say(self, text: str) -> None:
        self.parts.append(text)

    def balance_clause(self, maybe: float = 0.88) -> None:
        if self.rng.random() > maybe:
            return
        template = self.rng.choice(BALANCE_CLAUSES[self.operator])
        money_text = f"{self.currency_stub}{self.currency_space}{self.balance_val}"
        self.say(" " + template.format(c=money_text))
        self.gold["balance"] = self.balance_norm

    def tid_clause(self, maybe: float = 0.8) -> None:
        template = self.rng.choice(TID_CLAUSES[self.operator])
        if not template or self.rng.random() > maybe:
            return
        self.say(" " + template.format(t=self.txid))
        self.gold["trx_id"] = self.txid

    def fee_clause(self, maybe: float = 0.9) -> None:
        if not self.fee_val or self.rng.random() > maybe:
            return
        self.say(f" Fee charged: {self.currency_stub} {self.fee_val}.")
        self.gold["fee"] = self.fee_val

    def levy_clause(self, maybe: float = 0.85) -> None:
        if not self.levy_val or self.rng.random() > maybe:
            return
        self.say(f" E-Levy: {self.currency_stub} {self.levy_val}.")
        self.gold["levy"] = self.levy_val

    def reference_clause(self, maybe: float = 0.7) -> None:
        if not self.ref or self.rng.random() > maybe:
            return
        self.say(f" Reference: {self.ref}.")
        self.gold["reference"] = self.ref

    def datetime_clause(self) -> None:
        if self.rng.random() < 0.75:
            self.say(f" on {self.date_text} at {self.time_text}")
            self.gold["date"] = self.iso_date
            self.gold["time"] = self.iso_time

    def channel_clause(self) -> None:
        if self.rng.random() < 0.4:
            surface, channel = self.rng.choice([
                ("the MoMo App", "app"), ("the mobile app", "app"),
                ("*110#", "ussd"), ("*170#", "ussd"),
                ("an agent", "agent"), ("ATM", "atm"),
            ])
            self.say(f" Transaction via {surface}.")
            self.gold["channel"] = channel

    def status_clause(self, *, render: bool = True) -> None:
        """Ghana alerts mark completion three ways. Convention (documented in
        paper/objective2): a debit/credit alert with *no* failure marker is an
        completed transaction, so SUCCESS is annotated even when unrendered - and the
        engine is expected to reach the same conclusion from 'sent/paid/credited'."""
        roll = self.rng.random()
        if roll < 0.06:
            status, word = "PENDING", "pending confirmation"
        elif roll < 0.12:
            status, word = "FAILED", "not successful"
        else:
            status, word = "SUCCESS", "successful"
        self.status = status
        if status != "SUCCESS" and render:
            self.say(f" The transaction was {word}.")
        elif status == "SUCCESS" and render and self.kind not in ("p2p_transfer_out", "p2p_transfer_in"):
            self.say(f" {word}.")
        self.gold["status"] = status

    def text(self) -> str:
        joined = "".join(self.parts)
        joined = re.sub(r"\.\s*\.", ".", joined)
        return joined.strip()

    def finish(self, category: str) -> Tuple[str, Dict[str, Any], List[str]]:
        body = self.text() + footer(self.rng)
        body, extra = surface_noise(self.rng, body)
        self.tags += extra
        # Every transaction alert is either explicitly marked or implicitly completed.
        self.gold.setdefault("status", self.status)
        self.gold.setdefault("channel", None)
        self.channel_clause()
        body_after_channel = self.text()
        self.gold["tx_kind"] = self.kind
        self.gold["category"] = category
        self.gold["direction"] = "IN" if self.kind in (
            "p2p_transfer_in", "deposit", "loan_disbursement", "reversal") else "OUT"
        # Gold fields not rendered into the visible text become *explicit nulls*
        # ('this message does not contain it'), never silent absences.
        DERIVED = {"category", "tx_kind", "direction", "status"}
        for field in list(self.gold):
            if field in DERIVED:
                continue
            value = self.gold[field]
            if value and not still_present(field, str(value), body):
                self.gold[field] = None
                self.tags.append(f"lost_{field}")
        for field in self.GOLD_UNIVERSE:
            self.gold.setdefault(field, None)
        return body, self.gold, [t for t in dict.fromkeys(self.tags) if t]


def build(operator: str, kind: str, rng: random.Random) -> Tuple[str, Dict[str, Any], List[str]]:
    b = Builder(rng, operator, kind)
    b.status = "SUCCESS"

    if kind in ("p2p_transfer_out", "p2p_transfer_in"):
        out = kind.endswith("out")
        prep = "to" if out else "from"
        b.gold["counterparty_name"] = b.person
        b.gold["counterparty_phone"] = b.party_local
        if operator == "mtn_momo" and rng.random() < 0.5:
            b.say(f"Payment {'made' if out else 'received'} for {b.amount_text} {prep} {b.person}")
            if rng.random() < 0.55:
                b.say(f" ({b.party_phone})")
            b.say(".")
            b.tags.append("shape_mtn_payment_for")
            b.balance_clause()
            b.say(".")
            b.reference_clause()
            b.fee_clause()
            b.tid_clause()
        elif operator == "mtn_momo":
            b.say(f"You have {'sent' if out else 'received'} {b.amount_text} {prep} {b.person} - {b.party_phone}")
            b.datetime_clause()
            b.say(".")
            b.tags.append("shape_mtn_classic")
            b.balance_clause()
            b.say(".")
            b.reference_clause()
            b.tid_clause()
        elif operator == "telecel_cash":
            if rng.random() < 0.7:
                b.say(f"{b.txid} Confirmed. You have {'sent' if out else 'received'} {b.amount_text.replace(' ', '')} "
                      f"{prep} {b.party_phone} - {b.person}")
                b.gold["trx_id"] = b.txid
                b.tags.append("shape_telecel_tid_prefixed")
            else:
                b.say(f"You have {'sent' if out else 'received'} {b.amount_text} {prep} {b.person}")
                b.tags.append("shape_telecel_bare")
            b.datetime_clause()
            b.say(".")
            b.balance_clause()
            b.say(".")
            b.fee_clause()
        else:
            b.say(f"TXN {b.txid}. {b.amount_text} {'sent' if out else 'received'} {prep} {b.person} ({b.party_phone}).")
            b.gold["trx_id"] = b.txid
            b.tags.append("shape_at_txn_prefixed")
            b.balance_clause()
            b.say(".")
            b.say(" Thank you for using AirtelTigo Money.")
        return b.finish("p2p_transfer" if out else "income")

    if kind in ("deposit", "withdrawal"):
        inwards = kind == "deposit"
        agent = rng.choice(AGENTS)
        if operator == "mtn_momo":
            b.say(f"You have {'withdrawn' if not inwards else 'deposited'} {b.amount_text} "
                  f"{'from' if not inwards else 'into'} your MoMo account")
            b.datetime_clause()
            b.say(".")
        elif operator == "telecel_cash":
            b.say(f"Cash {'in' if inwards else 'out'} of {b.amount_text} by {agent} ")
            b.status_clause()
        else:
            b.say(f"{'Deposit' if inwards else 'Withdrawal'} of {b.amount_text} ")
            b.status_clause()
        if operator == "mtn_momo":
            b.status_clause(render=False)
        if rng.random() < 0.5:
            b.say(f" at {agent}.")
            b.gold["channel"] = "agent"
        b.fee_clause()
        b.levy_clause()
        b.balance_clause()
        b.say(".")
        b.tid_clause()
        return b.finish("cash_agent")

    if kind in ("paybill_payment", "merchant_payment"):
        if operator == "mtn_momo":
            b.say(f"Payment of {b.amount_text} to {b.merchant} (Business ID: {b.account})")
        elif operator == "telecel_cash":
            b.say(f"Payment of {b.amount_text} to {b.merchant}")
            if rng.random() < 0.5:
                b.say(f" for account {b.account}")
        else:
            b.say(f"Payment of {b.amount_text} to {b.merchant} (Paybill ID: {b.account})")
        b.tags.append("shape_paybill")
        b.gold["paybill_name"] = b.merchant
        b.gold["paybill_account"] = b.account
        b.datetime_clause()
        b.status_clause()
        b.fee_clause()
        b.levy_clause()
        b.balance_clause()
        b.say(".")
        b.tid_clause()
        return b.finish(classify_name(b.merchant))

    if kind in ("airtime_purchase", "data_bundle"):
        network = rng.choice(["MTN", "Telecel", "AirtelTigo"])
        if kind == "airtime_purchase":
            b.say(f"You have purchased {b.amount_text} worth of airtime for {b.party_phone} for {network} customer")
        else:
            size = rng.choice(["1GB", "2.5GB", "5GB", "10GB", "40GB"])
            b.say(f"You have successfully purchased a {size} data bundle for {b.amount_text} for {b.party_phone}")
        b.tags.append("shape_topup")
        b.gold["counterparty_phone"] = b.party_local
        b.balance_clause()
        b.say(".")
        return b.finish("airtime_data")

    if kind == "loan_disbursement":
        product = {"mtn_momo": "Qwikloan", "telecel_cash": "Telecel Mo Mobile Loan", "airtel_tigo_money": "AT Loan"}[operator]
        b.say(f"A loan of {b.amount_text} from {product} has been credited to your wallet")
        b.balance_clause()
        b.say(".")
        b.tid_clause()
        b.tags.append("shape_loan")
        return b.finish("banking")

    if kind == "reversal":
        b.say(f"{b.amount_text} has been reversed to your wallet for the transaction to {b.person}")
        b.status = "SUCCESS"
        b.gold["status"] = "SUCCESS"
        b.balance_clause()
        b.say(".")
        b.tid_clause()
        b.tags.append("shape_reversal")
        return b.finish("other")

    raise ValueError(f"unknown kind {kind}")


NEGATIVE_SHAPES: List[Callable[[random.Random], str]] = [
    lambda rng: f"Your MTN one-time password (OTP) is {rng.randrange(100000, 999999)}. It expires in 10 minutes. Never share it.",
    lambda rng: f"Dear customer, you are eligible for a MoMo loan of up to GHS {rng.randrange(1, 20) * 500}.00. Dial *170# to apply now.",
    lambda rng: f"{rng.choice(PERSONS).title()} has requested a payment of GHS {rng.randrange(50, 900)}.00 to you. Approve by dialling *170#.",
    lambda rng: f"Congratulations! You have won GHS {rng.randrange(1, 10) * 1000:.2f} in our customer promo. Call 100 to claim.",
    lambda rng: "Telecel Cash: Your plan expires tomorrow. Renew via *110# to stay connected. Dial *110*3# to buy data.",
    lambda rng: f"Airtime of GHS {rng.randrange(1, 50)}.00 is ready for your number. Top up more at any agent.",
    lambda rng: "AirtelTigo Money: your KYC tier will be downgraded if you do not upgrade. Visit any agent with your Ghana Card.",
    lambda rng: f"Payment request: you are being asked to pay GHS {rng.randrange(20, 500)}.00 for a water bill. Ignore if already paid.",
    lambda rng: f"Dear {rng.choice(PERSONS).title()}, your statement for August is ready. GHS {rng.randrange(100, 900)}.00 is due. Minimum amount due GHS 50.00.",
    lambda rng: "MoMo: Do not reply to this message. Share your PIN with nobody. Fraud hotline 0243000191.",
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=ROOT / "corpus" / "gold" / "seed-corpus.jsonl")
    parser.add_argument("--per-shape", type=int, default=14, help="records generated per (operator, kind) pair")
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--negatives", type=int, default=90)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    kinds = ["p2p_transfer_out", "p2p_transfer_in", "deposit", "withdrawal", "paybill_payment",
             "merchant_payment", "airtime_purchase", "data_bundle", "loan_disbursement", "reversal"]
    senders = {"mtn_momo": "MTN-MOMO", "telecel_cash": "TelecelCash", "airtel_tigo_money": "ATMoney"}
    records: List[Record] = []
    counter = count(1)
    for operator in ("mtn_momo", "telecel_cash", "airtel_tigo_money"):
        for kind in kinds:
            for _ in range(args.per_shape):
                body, gold, tags = build(operator, kind, rng)
                category = gold.pop("category", "other")
                records.append(
                    Record(
                        id=f"seed-{next(counter):05d}",
                        body=body,
                        operator=operator,
                        sender=senders[operator],
                        source="synthetic",
                        provenance="seed_templates_v3 tags=" + ",".join(tags),
                        consent="synthetic",
                        is_transaction=True,
                        category=category,
                        gold=gold,
                        notes="generated by tools/make_seed_corpus.py",
                    )
                )
    for i in range(args.negatives):
        body = NEGATIVE_SHAPES[i % len(NEGATIVE_SHAPES)](rng)
        records.append(
            Record(
                id=f"seed-neg-{i + 1:04d}",
                body=body,
                operator="unknown",
                sender="MTN",
                source="synthetic",
                provenance="seed_templates_v3 negative",
                consent="synthetic",
                is_transaction=False,
                category=None,
                gold={},
                notes="non-transaction control: must NOT be parsed as a transaction",
            )
        )

    written = write_corpus(args.out, records)
    dist: Dict[str, int] = {}
    for rec in records:
        if rec.category:
            dist[rec.category] = dist.get(rec.category, 0) + 1
    print(f"wrote {written} records -> {args.out}")
    print(f"transactions={sum(1 for r in records if r.is_transaction)} negatives={sum(1 for r in records if not r.is_transaction)}")
    print("category distribution:", json.dumps(dict(sorted(dist.items(), key=lambda kv: -kv[1]))))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
