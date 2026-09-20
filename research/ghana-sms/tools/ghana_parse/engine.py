"""The extraction engine: operator routing -> template scoring -> field fallback.

This is the reference implementation the paper evaluates. The Kotlin production path
(parser-core ``GhanaSmsParser``) consumes the same compiled specification and follows
the same three-stage cascade, with the deliberate difference that Kotlin returns the
subset the app's ``ParsedTransaction`` can carry.

Cascade, per message:
  0. tidy_text            whitespace/unicode hygiene (multi-part SMS, smart quotes)
  1. skip_rules           OTP / promo / pay-request / loan-marketing -> rejected
  2. operator routing     sender pattern, else body-marker vote
  3. template matching    every template is tried; best weighted field yield wins
  4. field fallback       operator-specific then shared per-field patterns
  5. normalize + type     amounts, dates, phones, names; tx_kind -> TransactionType
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional

from . import normalize as N
from .spec import FieldPattern, Operator, Spec, load_spec

# Weighted field yield: an extraction that nails amount+balance is worth more than one
# that finds a reference string. Weights are reported in the paper's methods section.
FIELD_WEIGHTS = {
    "amount": 4,
    "direction": 3,
    "tx_kind": 3,
    "balance": 2,
    "date": 2,
    "counterparty_name": 2,
    "paybill_name": 2,
    "fee": 1,
    "levy": 1,
    "trx_id": 1,
    "reference": 1,
    "counterparty_phone": 1,
    "paybill_account": 1,
    "time": 1,
    "status": 1,
    "channel": 1,
}
REQUIRED_FIELDS = ("amount", "direction")


@dataclass
class ExtractionResult:
    text: str
    sender: str
    accepted: bool = False
    reject_reason: Optional[str] = None
    operator_id: Optional[str] = None
    operator_display: Optional[str] = None
    route: Optional[str] = None  # sender | markers | none
    template_id: Optional[str] = None
    mode: Optional[str] = None  # template | field_fallback | none
    fields: Dict[str, str] = field(default_factory=dict)
    raw_fields: Dict[str, str] = field(default_factory=dict)
    provenance: Dict[str, str] = field(default_factory=dict)  # field -> pattern/template id
    confidence: float = 0.0
    score: int = 0
    ambiguous_date: bool = False
    strategy: str = "hybrid"
    boilerplate_stripped: bool = False

    @property
    def effective_status(self) -> str:
        """What the app should record when no status token exists (treat as completed)."""
        return self.fields.get("status") or "SUCCESS"

    @property
    def transaction_type(self) -> Optional[str]:
        kind = self.fields.get("tx_kind")
        return self.type_for_kind(kind)

    @staticmethod
    def type_for_kind(kind: Optional[str]) -> Optional[str]:
        return KIND_TO_TYPE.get((kind or "").strip().lower())

    def present_fields(self) -> List[str]:
        return sorted(k for k, v in self.fields.items() if v)


KIND_TO_TYPE: Dict[str, str] = {}


class GhanaSmsEngine:
    def __init__(self, spec: Optional[Spec] = None) -> None:
        self.spec = spec or load_spec()
        KIND_TO_TYPE.clear()
        KIND_TO_TYPE.update(self.spec.kind_to_type)

    # ---- routing -------------------------------------------------------------
    def route(self, text: str, sender: str) -> tuple[Optional[Operator], str]:
        """Sender ID is the primary route, body markers the fallback.

        Sender IDs are spoofable, so routing is treated as a performance hint only:
        a sender match still has to produce a parse, and marker votes can rescue a
        message whose sender is unknown ('UNKNOWN' address on some handsets).
        """
        for operator in self.spec.operators:
            if operator.matches_sender(sender):
                return operator, "sender"
        best: Optional[Operator] = None
        best_hits = 0
        for operator in self.spec.operators:
            hits = operator.marker_hits(text)
            if hits > best_hits:
                best, best_hits = operator, hits
        if best is not None:
            return best, f"markers:{best_hits}"
        return None, "none"

    def skip_reason(self, text: str) -> Optional[str]:
        for _rule_id, regex, reason in self.spec.skip_rules:
            if regex.search(text):
                return reason
        return None

    # ---- one message ---------------------------------------------------------
    def parse(self, body: str, sender: str = "", *, strategy: str = "hybrid") -> ExtractionResult:
        """``strategy``: 'hybrid' (templates + gap-fill, the shipped behaviour),
        'template_only' (precision-biased ablation), 'fallback_only' (coverage-biased ablation)."""
        text = N.tidy_text(body or "")
        result = ExtractionResult(text=text, sender=sender, strategy=strategy)
        if not text:
            result.reject_reason = "empty message"
            return result

        reason = self.skip_reason(text)
        if reason:
            result.reject_reason = f"skip rule: {reason}"
            return result

        # Field extraction sees the message without operator boilerplate; templates and
        # skip rules still see the whole thing (a promo footer IS evidence).
        core = N.strip_boilerplate(text)
        result.boilerplate_stripped = core != text

        operator, route = self.route(text, sender)
        result.route = route
        if operator is None:
            result.reject_reason = "no operator matched"
            return result
        result.operator_id = operator.id
        result.operator_display = operator.display_name

        template_result = None if strategy == "fallback_only" else self._best_template(text, operator)
        field_source = core
        if template_result is not None:
            template_id, kind, fields, score = template_result
            result.mode = "template"
            result.template_id = template_id
            result.provenance = {name: template_id for name in fields}
            result.score = score
            if kind:
                fields["tx_kind"] = kind
                result.provenance["tx_kind"] = template_id
            if strategy == "hybrid":
                # Gap-fill: anything the template did not capture is still worth
                # recovering with the per-field patterns. tools/evaluate.py measures
                # exactly what this step adds (the 'template_only' ablation arm).
                filled, provenance = self._field_fallback(field_source, operator, skip=set(fields))
                fields.update(filled)
                result.provenance.update(provenance)
                result.score = score + sum(FIELD_WEIGHTS.get(k, 1) for k in filled)
        else:
            if strategy == "template_only":
                result.mode = "none"
                result.reject_reason = "template_only strategy: no template matched"
                return result
            fields, provenance = self._field_fallback(field_source, operator)
            if not fields:
                result.mode = "none"
                result.reject_reason = "no template matched and no field pattern hit"
                return result
            result.mode = "field_fallback"
            result.provenance = provenance
            result.score = sum(FIELD_WEIGHTS.get(k, 1) for k in fields)

        # Mutual exclusion: a paybill/merchant payment has ONE counterparty. If the
        # template already bound the merchant, a fallback name capture is the same
        # string seen twice - keeping it would register as a false positive.
        # Mutual exclusion: a paybill/merchant payment has one counterparty. When the
        # template bound the merchant, a fallback capture of the same span is not new
        # information and would otherwise be scored as a false positive.
        if fields.get("paybill_name") and fields.get("counterparty_name"):
            if N.name_key(fields["paybill_name"]) == N.name_key(fields["counterparty_name"]):
                fields.pop("counterparty_name")
                result.provenance.pop("counterparty_name", None)
        raw: Dict[str, str] = {k: v for k, v in fields.items()}
        norm: Dict[str, str] = {}
        for name, value in fields.items():
            cleaned = self._normalize(name, value)
            if cleaned:
                norm[name] = cleaned

        if not norm.get("amount"):
            result.mode = "none"
            result.raw_fields, result.fields = raw, norm
            result.reject_reason = "amount missing after normalization"
            return result
        if not norm.get("tx_kind"):
            kind = self._infer_kind(text, norm)
            if kind:
                norm["tx_kind"] = kind
                result.provenance.setdefault("tx_kind", "kind_inference")
        if not norm.get("direction"):
            # Kind first: it is derived from the matched template and is more reliable
            # than a keyword sweep over the whole message.
            direction = self.spec.kind_to_direction.get(norm.get("tx_kind", "")) or self._infer_direction(text)
            if direction:
                norm["direction"] = direction
                result.provenance.setdefault("direction", "kind_inference" if self.spec.kind_to_direction.get(norm.get("tx_kind", "")) else "direction_rules")
        if not norm.get("status"):
            # Absence of a marker is reported as 'unknown', NOT silently as success:
            # status is measured against what the message actually says. Callers that
            # need a decision use `effective_status`.
            status = self._infer_status(text)
            if status:
                norm["status"] = status
                result.provenance.setdefault("status", "status_rules")

        result.ambiguous_date = N.date_is_ambiguous(raw.get("date"))
        result.raw_fields = raw
        result.fields = norm
        result.confidence = self._confidence(result, norm)
        result.accepted = all(norm.get(f) for f in REQUIRED_FIELDS)
        if not result.accepted:
            result.reject_reason = "required fields missing: " + ",".join(
                f for f in REQUIRED_FIELDS if not norm.get(f)
            )
        return result

    # ---- stage 3: templates --------------------------------------------------
    def _best_template(self, text: str, operator: Operator):
        """Try every template; keep the highest weighted yield (priority breaks ties)."""
        best = None
        for template in operator.templates:
            captured = template.match(text)
            if not captured:
                continue
            if not N.normalize_amount(captured.get("amount")):
                continue  # a template that cannot yield a sane amount is not a match
            yield_set = {k for k, v in captured.items() if v and v.strip()}
            score = sum(FIELD_WEIGHTS.get(k, 1) for k in yield_set)
            candidate = (score, -template.priority, template.id, captured, template.kind)
            if best is None or (candidate[0], candidate[1]) > (best[0], best[1]):
                best = candidate
        if best is None:
            return None
        score, _neg_priority, template_id, captured, kind = best
        return template_id, kind, dict(captured), score

    # ---- stage 4: field fallback --------------------------------------------
    def _field_fallback(
        self, text: str, operator: Operator, *, skip: Optional[set] = None
    ) -> tuple[Dict[str, str], Dict[str, str]]:
        skip = skip or set()
        fields: Dict[str, str] = {}
        provenance: Dict[str, str] = {}
        for name in self.spec.fields:
            if name in ("status", "tx_kind", "direction"):
                continue  # inferred from rule lists, not from capture groups
            if name in skip:
                continue
            for pattern in self.spec.field_patterns(operator, name):
                value = pattern.match(text)
                if value and value.strip():
                    fields[name] = value.strip()
                    provenance[name] = pattern.id
                    break
        return fields, provenance

    # ---- inference helpers ---------------------------------------------------
    def _normalize(self, name: str, value: str) -> Optional[str]:
        if value is None:
            return None
        if name in ("amount", "fee", "levy", "balance"):
            return N.normalize_amount(value)
        if name == "date":
            return N.normalize_date(value)
        if name == "time":
            return N.normalize_time(value)
        if name in ("counterparty_phone",):
            return N.normalize_phone(value)
        if name in ("counterparty_name", "paybill_name"):
            return N.normalize_name(value)
        if name == "paybill_account":
            return N.normalize_account(value)
        if name == "trx_id":
            return N.normalize_trx_id(value)
        if name == "channel":
            # A constant-valued pattern already produced the canonical label.
            return value if value in ("agent", "app", "ussd", "atm", "pos", "other") else N.normalize_channel(value)
        if name in ("status", "direction"):
            return str(value).strip().upper() or None
        if name == "tx_kind":
            return str(value).strip().lower().replace(" ", "_") or None
        return str(value).strip() or None

    def _infer_direction(self, text: str) -> Optional[str]:
        for _rule_id, regex, direction in self.spec.direction_rules:
            if regex.search(text):
                return direction
        return None

    def _infer_status(self, text: str) -> Optional[str]:
        for _rule_id, regex, status in self.spec.status_rules:
            if regex.search(text):
                return status
        return None

    def _infer_kind(self, text: str, fields: Dict[str, str]) -> Optional[str]:
        lowered = text.lower()
        direction = fields.get("direction")
        if "airtime" in lowered or "recharge" in lowered or "top up" in lowered or "top-up" in lowered:
            return "airtime_purchase"
        if "data bundle" in lowered or "data plan" in lowered or "bundle" in lowered:
            return "data_bundle"
        if "loan" in lowered or "advance" in lowered:
            # Decide from the wording, not from an already-guessed direction: that was
            # circular and turned every disbursement into a repayment.
            if any(k in lowered for k in ("credited", "disbursed", "sent to your", "received")):
                return "loan_disbursement"
            if any(k in lowered for k in ("repay", "repaid", "deduct", "recovery")):
                return "loan_repayment"
            return "loan_disbursement" if direction == "IN" else "loan_repayment"
        if "reversal" in lowered or "reversed" in lowered:
            return "reversal"
        if "withdraw" in lowered or "cash out" in lowered:
            return "withdrawal"
        if "deposit" in lowered or "cash in" in lowered:
            return "deposit"
        if fields.get("paybill_name") or "paybill" in lowered or "business id" in lowered:
            return "paybill_payment"
        if any(k in lowered for k in ("merchant", "momo pay", "momopay", "till", "pos payment")):
            return "merchant_payment"
        if direction == "IN":
            return "p2p_transfer_in"
        if direction == "OUT":
            return "p2p_transfer_out"
        return None

    # ---- confidence -----------------------------------------------------------
    def _confidence(self, result: ExtractionResult, fields: Dict[str, str]) -> float:
        """Deliberately conservative and *documented*: capped so that no regex-only parse
        ever claims certainty, and the gap between tiers is what makes a review threshold
        (e.g. auto-accept >= 0.75) meaningful.

        template hit : 0.55 + 0.30 * field-yield + 0.05 sender-route + 0.05 clean-status
        field fallback: 0.25 + 0.30 * field-yield + 0.05 sender-route, capped at 0.55
        """
        present = sum(1 for name in self.spec.fields if fields.get(name))
        yield_ratio = min(1.0, present / 7.0)
        route_bonus = 0.05 if result.route == "sender" else 0.0
        if result.mode == "template":
            clean = 0.05 if fields.get("status", "SUCCESS") == "SUCCESS" else 0.0
            return round(min(0.95, 0.55 + 0.30 * yield_ratio + route_bonus + clean), 3)
        return round(min(0.55, 0.25 + 0.30 * yield_ratio + route_bonus), 3)
