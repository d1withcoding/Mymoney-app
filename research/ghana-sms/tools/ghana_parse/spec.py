"""Loads and compiles the declarative extraction specification.

The spec stores regexes as *token templates* so that one definition can be expanded
identically in Python (``re``) and Kotlin (``java.util.regex``). Only POSIX-style
character classes are used inside tokens, which keeps the two engines equivalent.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field as dc_field
from pathlib import Path
from typing import Any, Dict, List, Optional

SPEC_PATH = Path(__file__).resolve().parents[2] / "spec" / "ghana_patterns.json"

# Longest alternative first: the substitution is anchored on the closing brace, so
# {ID16} can never be mistaken for {ID}.
_TOKEN_ORDER = ("NAME_LAX", "NDELIM_HARD", "NDELIM", "CUR", "PHONE", "NAME", "ID16", "DATE", "TIME", "REF", "ID", "N")
TOKEN_RE = re.compile(r"\{(" + "|".join(_TOKEN_ORDER) + r")\}")


def expand_tokens(pattern: str, tokens: Dict[str, str]) -> str:
    """Replace every ``{TOKEN}`` with its expansion. Unknown tokens are a hard error."""

    def sub(match: "re.Match[str]") -> str:
        name = match.group(1)
        # A brace group that is not a known token is a *quantifier* (e.g. {0,12});
        # TOKEN_RE only fires on names listed in _TOKEN_ORDER, so this is a no-op.
        if name not in tokens:
            raise KeyError(f"Unknown pattern token: {{{name}}}")
        return tokens[name]

    return TOKEN_RE.sub(sub, pattern)


def verify_pattern(source: str, flags: int) -> None:
    """Reject a pattern that Python cannot compile - the Kotlin emitter uses the same string."""
    re.compile(source, flags)


@dataclass
class FieldPattern:
    id: str
    source: str  # token-expanded regex source
    group: int = 1
    whole_match: bool = False
    value: Optional[str] = None  # constant value when the match only *detects* a feature
    note: str = ""
    regex: "re.Pattern[str]" = dc_field(default=None, repr=False)  # type: ignore[assignment]

    def __post_init__(self) -> None:
        self.regex = re.compile(self.source, re.IGNORECASE)

    def match(self, text: str) -> Optional[str]:
        found = self.regex.search(text)
        if not found:
            return None
        if self.value is not None:
            return self.value
        if self.whole_match or self.group == 0:
            return found.group(0)
        return found.group(self.group)


@dataclass
class Template:
    id: str
    kind: str
    priority: int
    source: str
    field_by_group: Dict[int, str]
    expected_fields: List[str]
    regex: "re.Pattern[str]" = dc_field(default=None, repr=False)  # type: ignore[assignment]

    def __post_init__(self) -> None:
        self.regex = re.compile(self.source, re.IGNORECASE)

    def match(self, text: str) -> Optional[Dict[str, str]]:
        found = self.regex.match(text)
        if found is None:
            return None
        out: Dict[str, str] = {}
        for idx, name in self.field_by_group.items():
            try:
                raw = found.group(idx)
            except (IndexError, re.error):  # pragma: no cover - defensive
                raw = None
            if raw is not None and raw.strip():
                out[name] = raw.strip()
        return out


@dataclass
class Operator:
    id: str
    display_name: str
    sender_patterns: List[str]
    body_markers: List[str]
    templates: List[Template]
    fields: Dict[str, List[FieldPattern]]
    sender_regexes: List["re.Pattern[str]"] = dc_field(default_factory=list)
    marker_regexes: List["re.Pattern[str]"] = dc_field(default_factory=list)

    def __post_init__(self) -> None:
        self.sender_regexes = [re.compile(p, re.IGNORECASE) for p in self.sender_patterns]
        self.marker_regexes = [re.compile(p, re.IGNORECASE) for p in self.body_markers]

    def matches_sender(self, sender: str) -> bool:
        sender = (sender or "").strip()
        return any(rx.match(sender) for rx in self.sender_regexes)

    def marker_hits(self, text: str) -> int:
        return sum(1 for rx in self.marker_regexes if rx.search(text))


class Spec:
    """Compiled view over ``ghana_patterns.json``."""

    def __init__(self, raw: Dict[str, Any]) -> None:
        self.raw = raw
        self.version: str = raw.get("spec_version", "0")
        self.tokens: Dict[str, str] = raw["tokens"]
        self.fields: List[str] = list(raw["fields"])
        self.currency: str = raw["currency"]["code"]
        self.kind_to_type: Dict[str, str] = raw["kind_to_transaction_type"]
        self.kind_to_direction: Dict[str, str] = raw.get("kind_to_direction", {})
        self.skip_rules = [
            (r["id"], re.compile(expand_tokens(r["pattern"], self.tokens), re.IGNORECASE), r.get("reason", ""))
            for r in raw.get("skip_rules", [])
        ]
        self.status_rules = [
            (r["id"], re.compile(expand_tokens(r["pattern"], self.tokens), re.IGNORECASE), r["status"])
            for r in raw.get("status_rules", [])
        ]
        self.direction_rules = [
            (r["id"], re.compile(expand_tokens(r["pattern"], self.tokens), re.IGNORECASE), r["direction"])
            for r in raw.get("direction_rules", [])
        ]
        self.shared_fields: Dict[str, List[FieldPattern]] = {
            name: [_field_pattern(name, item, self.tokens) for item in items]
            for name, items in raw.get("shared_fields", {}).items()
        }
        self.operators: List[Operator] = [_operator(o, self.tokens) for o in raw["operators"]]
        self.by_id: Dict[str, Operator] = {o.id: o for o in self.operators}

    # ---- convenience ---------------------------------------------------------
    def field_patterns(self, operator: Optional[Operator], name: str) -> List[FieldPattern]:
        """Operator-specific patterns take precedence, shared patterns are the fallback net."""
        out: List[FieldPattern] = []
        if operator is not None:
            out.extend(operator.fields.get(name, []))
        out.extend(self.shared_fields.get(name, []))
        return out

    def all_pattern_ids(self) -> List[str]:
        ids = [pid for patterns in self.shared_fields.values() for pid in (p.id for p in patterns)]
        for op in self.operators:
            for patterns in op.fields.values():
                ids.extend(p.id for p in patterns)
        return ids

    def all_templates(self) -> List[Template]:
        return [t for op in self.operators for t in op.templates]


def _field_pattern(field_name: str, item: Dict[str, Any], tokens: Dict[str, str]) -> FieldPattern:
    source = expand_tokens(item["regex"], tokens)
    verify_pattern(source, re.IGNORECASE)
    return FieldPattern(
        id=item.get("id", f"{field_name}.{abs(hash(source)) % 10**6}"),
        source=source,
        group=int(item.get("group", 1)),
        whole_match=bool(item.get("whole_match", False)),
        value=item.get("value"),
        note=item.get("note", ""),
    )


def _operator(raw: Dict[str, Any], tokens: Dict[str, str]) -> Operator:
    templates: List[Template] = []
    for item in raw.get("templates", []):
        source = expand_tokens(item["regex"], tokens)
        verify_pattern(source, re.IGNORECASE)
        groups = {int(g["idx"]): g["field"] for g in item.get("groups", [])}
        templates.append(
            Template(
                id=item["id"],
                kind=item.get("kind", "unknown"),
                priority=int(item.get("priority", 50)),
                source=source,
                field_by_group=groups,
                expected_fields=[g["field"] for g in item.get("groups", [])],
            )
        )
    templates.sort(key=lambda t: t.priority)

    fields: Dict[str, List[FieldPattern]] = {}
    for name, items in raw.get("fields", {}).items():
        fields[name] = [_field_pattern(name, item, tokens) for item in items]

    return Operator(
        id=raw["id"],
        display_name=raw["display_name"],
        sender_patterns=list(raw.get("sender_patterns", [])),
        body_markers=list(raw.get("body_markers", [])),
        templates=templates,
        fields=fields,
    )


def load_spec(path: Optional[Path] = None) -> Spec:
    with open(path or SPEC_PATH, "r", encoding="utf-8") as handle:
        return Spec(json.load(handle))
