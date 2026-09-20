#!/usr/bin/env python3
"""Objective 3: train, evaluate and export the on-device spending classifier.

Architecture (the 'hybrid cascade' the proposal promises), measured tier by tier:

  Tier 0  deterministic rules      operator kind + the shared merchant lexicon. Zero
                                   model, ~0 us, and it is what makes the common cases
                                   (airtime, paybill ECG, agent cash-in) unbreakable.
  Tier 1  tiny linear model        TF-IDF (word uni/bi-gram + within-word char trigrams
                                   + structural features) -> multinomial logistic
                                   regression, trained here, exported as a TSV of
                                   folded weights. No tokenizer library, no runtime
                                   dependency, ~20 lines of inference math.
  Tier 2  on-device LLM (optional) not trained here. To keep the ablation honest it is
                                   reported as an ORACLE BOUNDARY: 'what the cascade
                                   would reach if a third tier resolved every remaining
                                   record'. It is labelled as such everywhere.

Everything is pure standard library and deterministic (seeded), so a reviewer can rerun
it and get the same table.

Outputs:
  model/ghana_category_model_v1.tsv        the artifact the phone loads
  model/ghana_category_model_v1.meta.json  features/labels/hyper-parameters
  results/classifier_metrics.md            accuracy, macro-F1, per-class, confusion, ablation
  results/classifier_metrics.json
  results/parity_fixtures.json             Python's feature vectors + scores for Kotlin parity
"""

from __future__ import annotations

import argparse
import json
import math
import random
import re
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ghana_parse import GhanaSmsEngine, normalize as N  # noqa: E402
from ghana_parse.corpus import Record, read_corpus, split_records  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]

MODEL_VERSION = 1
NON_ALPHA = re.compile(r"[^a-z0-9]+")

# Arbitration thresholds. Both were picked on the dev split, not the test split, and both are
# deliberately coarse: the policy they encode matters more than their exact value.
#   MODEL_PROB_FLOOR      how sure the model must be to overrule a *weak* rule (direction prior)
#   LEXICON_OVERTURE_FLOOR  how sure it must be to overrule a merchant-keyword hit
MODEL_PROB_FLOOR = 0.45
LEXICON_OVERTURE_FLOOR = 0.50
WORD_RE = re.compile(r"[a-z0-9'&\-]{2,}")


# --------------------------------------------------------------------------- features
def amount_bucket(amount: Optional[str]) -> str:
    try:
        value = float(str(amount).replace(",", ""))
    except (TypeError, ValueError):
        return "na"
    for limit, name in ((10, "tiny"), (50, "small"), (200, "medium"), (1000, "large")):
        if value <= limit:
            return name
    return "xlarge"


def extract_features(text: str, meta: Optional[Dict[str, object]] = None) -> Counter:
    """The exact feature function the Kotlin runtime reproduces. Keep both in step.

    meta carries the parsed transaction (operator, tx_kind, direction, merchant fields,
    amount, channel) so structural signals join the lexical ones.
    """
    features: Counter = Counter()
    lowered = (text or "").lower()
    words = [w for w in NON_ALPHA.split(N.strip_boilerplate(lowered)) if w]
    words = [w for w in words if len(w) >= 2]
    for word in words:
        features[f"u:{word}"] += 1
        if any(ch.isdigit() for ch in word):
            features[f"n:{''.join(c for c in word if not c.isdigit()) or 'num'}"] += 1
    for first, second in zip(words, words[1:]):
        features[f"b:{first}_{second}"] += 1
    for word in words:
        for i in range(len(word) - 2):
            features[f"c3:{word[i:i + 3]}"] += 1
    if meta:
        for key, prefix in (("operator", "op"), ("tx_kind", "k"), ("direction", "d"),
                            ("channel", "ch"), ("status", "st")):
            value = meta.get(key)
            if value:
                features[f"{prefix}:{str(value).lower()}"] += 1
        features[f"amt:{amount_bucket(meta.get('amount'))}"] += 1
        features["pb:" + ("yes" if meta.get("paybill_name") else "no")] += 1
        features["cp:" + ("yes" if meta.get("counterparty_name") else "no")] += 1
        merchant = str(meta.get("paybill_name") or meta.get("counterparty_name") or "").lower().strip()
        if merchant:
            for token in [t for t in NON_ALPHA.split(merchant) if len(t) >= 3]:
                features[f"m:{token}"] += 1
            features[f"M:{merchant.replace(' ', '_')}"] += 1
    return features


def tfidf_vector(features: Counter, idf: Dict[str, float]) -> Dict[str, float]:
    """Sublinear tf x idf, L2 normalised over the model vocabulary only (mirrors Kotlin)."""
    vec: Dict[str, float] = {}
    for feature, count in features.items():
        weight = idf.get(feature)
        if weight is None:
            continue
        vec[feature] = (1.0 + math.log(count)) * weight
    norm = math.sqrt(sum(v * v for v in vec.values()))
    if norm > 0:
        for key in vec:
            vec[key] /= norm
    return vec


# ------------------------------------------------------------------------------ tier 0
class RuleTier:
    def __init__(self, lexicon: Dict[str, object]) -> None:
        self.labels: List[str] = []
        self.rules: List[Tuple[Tuple[str, ...], str]] = []
        for entry in lexicon["labels"]:  # type: ignore[index]
            label = entry["id"]
            self.labels.append(label)
            keywords = {k.lower() for k in (entry.get("keywords") or []) if len(k) > 2}
            merchants = {m.lower() for m in (entry.get("merchants") or [])}
            # Sorted, not a set: which term is *named* as the explanation must be reproducible
            # (Python's set iteration order varies with PYTHONHASHSEED, and the Kotlin object
            # walks the generated list in this same order). Same membership, stable 'why'.
            terms = tuple(sorted(keywords | merchants))
            self.rules.append((terms, label))
        self.fallthrough = "other"

    # Strength is what the cascade arbitration policy acts on; see predict().
    STRUCTURAL = "structural"      # derived from the parsed kind: an extraction fact
    MERCHANT = "merchant_lexicon"  # a lexicon term matched the merchant/paybill name itself
    UNIQUE = "unique_lexicon"      # exactly one label matched anywhere in the message
    WEAK = "weak"                   # ambiguous lexicon, or a bare direction=IN prior

    def predict(self, text: str, meta: Dict[str, object]) -> Tuple[Optional[str], Optional[str], Optional[str]]:
        """(label, explanation, strength); (None, None, None) when the rules abstain."""
        kind = str(meta.get("tx_kind") or "").lower()
        direction = str(meta.get("direction") or "").upper()
        if kind in ("deposit", "withdrawal") or "cash in" in text.lower() or "cash out" in text.lower():
            return "cash_agent", "kind=deposit/withdrawal", self.STRUCTURAL
        if kind in ("airtime_purchase", "data_bundle"):
            return "airtime_data", "kind=airtime/data", self.STRUCTURAL
        if kind in ("loan_disbursement", "loan_repayment"):
            return "banking", "kind=loan", self.STRUCTURAL
        if kind == "reversal":
            return "other", "kind=reversal", self.STRUCTURAL
        if kind == "p2p_transfer_in":
            return "income", "kind=p2p_in", self.STRUCTURAL
        lowered = text.lower()
        merchant = str(meta.get("paybill_name") or meta.get("counterparty_name") or "").lower()
        haystack = merchant + " " + lowered
        if kind in ("p2p_transfer_out",) and not merchant:
            return "p2p_transfer", "kind=p2p_out", self.STRUCTURAL
        matches: List[Tuple[int, str, str, bool]] = []
        for keywords, label in self.rules:
            hits = sum(1 for keyword in keywords if keyword in haystack)
            if not hits:
                continue
            # a keyword found in the merchant field outweighs one in free text
            on_merchant = any(k in merchant for k in keywords)
            why = next(k for k in keywords if k in haystack)
            matches.append((hits + (2 if on_merchant else 0), label, f"lexicon:{why}", on_merchant))
        if matches:
            # max() keeps the first maximal element, i.e. taxonomy order breaks score ties -
            # the same rule the Kotlin object applies when it walks the generated lexicon.
            score, label, why, on_merchant = max(matches, key=lambda item: item[0])
            strength = self.MERCHANT if on_merchant else (
                self.UNIQUE if len(matches) == 1 else self.WEAK
            )
            return label, why, strength
        if direction == "IN":
            # A credit with no other evidence is usually income, but 'usually' is exactly
            # the case a classifier should get a vote on, so this one is marked WEAK.
            return "income", "direction=IN", self.WEAK
        return None, None, None

    @staticmethod
    def is_decisive(strength: Optional[str]) -> bool:
        return strength in (RuleTier.STRUCTURAL, RuleTier.MERCHANT, RuleTier.UNIQUE)


# --------------------------------------------------------------------------- training
def train_linear(train: List[Tuple[Dict[str, float], int]], n_labels: int, epochs: int,
                 lr: float, l2: float, seed: int) -> Tuple[List[Dict[str, float]], List[float]]:
    """Softmax regression by plain SGD. Deterministic given the seed."""
    rng = random.Random(seed)
    weights: List[Dict[str, float]] = [dict() for _ in range(n_labels)]
    bias = [0.0] * n_labels
    for epoch in range(epochs):
        order = list(range(len(train)))
        rng.shuffle(order)
        lr_t = lr / (1.0 + 0.08 * epoch)
        for index in order:
            vec, label = train[index]
            scores = [bias[j] + sum(w.get(f, 0.0) * v for f, v in vec.items()) for j, w in enumerate(weights)]
            top = max(scores)
            exps = [math.exp(s - top) for s in scores]
            total = sum(exps) or 1.0
            probs = [e / total for e in exps]
            for j in range(n_labels):
                error = probs[j] - (1.0 if j == label else 0.0)
                if error == 0.0:
                    continue
                bias[j] -= lr_t * error
                wj = weights[j]
                for feature, value in vec.items():
                    delta = lr_t * (error * value + l2 * wj.get(feature, 0.0))
                    new = wj.get(feature, 0.0) - delta
                    if abs(new) < 1e-5:
                        wj.pop(feature, None)
                    else:
                        wj[feature] = new
    return weights, bias


def softmax(scores: List[float]) -> List[float]:
    top = max(scores)
    exps = [math.exp(s - top) for s in scores]
    total = sum(exps) or 1.0
    return [e / total for e in exps]


def score_vector(vec: Dict[str, float], weights: List[Dict[str, float]], bias: List[float]) -> List[float]:
    return [bias[j] + sum(w.get(f, 0.0) * v for f, v in vec.items()) for j, w in enumerate(weights)]


# ------------------------------------------------------------------------------- main
def record_text_and_meta(record: Record, engine: GhanaSmsEngine) -> Tuple[str, Dict[str, object]]:
    result = engine.parse(record.body, record.sender)
    meta: Dict[str, object] = {
        "operator": record.operator if record.operator != "unknown" else result.operator_id,
        "amount": result.fields.get("amount") or record.gold.get("amount"),
        "tx_kind": result.fields.get("tx_kind") or record.gold.get("tx_kind"),
        "direction": result.fields.get("direction") or record.gold.get("direction"),
        "channel": result.fields.get("channel"),
        "status": result.fields.get("status"),
        "paybill_name": result.fields.get("paybill_name"),
        "counterparty_name": result.fields.get("counterparty_name"),
    }
    if not meta["paybill_name"]:
        meta["paybill_name"] = record.gold.get("paybill_name")
    if not meta["counterparty_name"]:
        meta["counterparty_name"] = record.gold.get("counterparty_name")
    return record.body, meta


def arbitrate_with(
    rule_label: Optional[str],
    rule_why: Optional[str],
    rule_strength: Optional[str],
    model_label: Optional[str],
    model_prob: float,
    strict: bool = False,
) -> Tuple[Optional[str], str]:
    """The one policy both the trainer and the exported fixtures use, so the shipped cascade and
    the measured cascade cannot disagree. Mirrored in GhanaCategoryClassifier.kt.

    - structural rule (derived from the parsed transaction kind) wins outright: it states what
      the transaction IS, and a linear model cannot beat a fact;
    - a lexicon hit on the merchant name is a *prior*, not a fact - names are exactly where
      keyword matching is weakest - so a confident model overrules it;
    - a weak rule (the direction prior) only survives when the model is unsure.
    """
    if rule_label and strict:
        return rule_label, f"rule:{rule_why}"
    if rule_label and rule_strength == RuleTier.STRUCTURAL:
        # 'this transaction IS airtime' - a statement about what happened, not a guess from
        # surface wording, so a model never overrides it.
        return rule_label, f"rule:{rule_why}"

    def rule_tier() -> Tuple[str, str]:
        return rule_label or None, f"rule:{rule_why}"

    if model_label is None:
        return (rule_label, f"rule:{rule_why}") if rule_label else (None, "abstained:no features")
    if rule_label and rule_strength in (RuleTier.MERCHANT, RuleTier.UNIQUE):
        if model_prob >= LEXICON_OVERTURE_FLOOR and model_label != rule_label:
            return model_label, f"model:{model_prob:.2f} overruled lexicon '{rule_why}'"
        return rule_tier()
    if rule_label and rule_strength == RuleTier.WEAK:
        if model_prob >= MODEL_PROB_FLOOR:
            return model_label, f"model:{model_prob:.2f} overruled weak rule '{rule_why}'"
        return rule_tier()
    if rule_label:  # ambiguous lexicon (not decisive): the model decides
        return model_label, f"model:{model_prob:.2f} (rules ambiguous)"
    return model_label, f"model:{model_prob:.2f}"


def evaluate(predictions: List[Optional[str]], truth: List[str], labels: List[str]) -> Dict[str, object]:
    tp = Counter(); fp = Counter(); fn = Counter(); correct = 0
    confusion: Dict[str, Counter] = {label: Counter() for label in labels}
    for pred, gold in zip(predictions, truth):
        if pred == gold:
            correct += 1
            tp[gold] += 1
        else:
            if pred:
                fp[pred] += 1
            fn[gold] += 1
        confusion[gold or "other"][pred or "ABSTAINED"] += 1
    per_class = {}
    for label in labels:
        support = tp[label] + fn[label]
        precision = tp[label] / (tp[label] + fp[label]) if (tp[label] + fp[label]) else None
        recall = tp[label] / support if support else None
        f1 = (2 * precision * recall / (precision + recall)) if precision and recall else (0.0 if (precision is not None and recall is not None) else None)
        per_class[label] = {"precision": precision, "recall": recall, "f1": f1, "support": support, "predicted": tp[label] + fp[label]}
    present = [label for label in labels if tp[label] + fn[label]]
    macro_f1 = sum((per_class[l]["f1"] or 0.0) for l in present) / len(present) if present else None
    return {
        "n": len(truth),
        "accuracy": round(correct / len(truth), 4) if truth else None,
        "macro_f1": round(macro_f1, 4) if macro_f1 is not None else None,
        "abstentions": sum(1 for p in predictions if p is None),
        # 1 - abstentions/n: the share of records the arm is willing to label at all. An arm that
        # scores well by refusing the hard records is not a good arm, so coverage is always reported
        # next to accuracy.
        "coverage": 1.0 - sum(1 for p in predictions if p is None) / max(1, len(predictions)),
        "per_class": per_class,
        "confusion": {g: dict(row) for g, row in confusion.items()},
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--corpus", type=Path, default=ROOT / "corpus" / "gold" / "seed-corpus.jsonl")
    parser.add_argument("--lexicon", type=Path, default=ROOT / "spec" / "ghana_categories.json")
    parser.add_argument("--model-out", type=Path, default=ROOT / "model" / "ghana_category_model_v1.tsv")
    parser.add_argument("--meta-out", type=Path, default=ROOT / "model" / "ghana_category_model_v1.meta.json")
    parser.add_argument("--out-dir", type=Path, default=ROOT / "results")
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--lr", type=float, default=0.9)
    parser.add_argument("--l2", type=float, default=3e-4)
    parser.add_argument("--min-df", type=int, default=2)
    parser.add_argument("--max-features", type=int, default=4000)
    parser.add_argument("--max-per-label", type=int, default=400, help="weight truncation: model size control")
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()

    engine = GhanaSmsEngine()
    lexicon = json.loads(args.lexicon.read_text(encoding="utf-8"))
    labels: List[str] = [entry["id"] for entry in lexicon["labels"]]
    label_index = {label: i for i, label in enumerate(labels)}

    records = [r for r in read_corpus(args.corpus) if r.is_transaction and r.category in label_index]
    if len(records) < 60:
        print(f"WARNING: only {len(records)} labelled records - metrics will be noisy.", file=sys.stderr)
    train_recs, dev_recs, test_recs = split_records(records, seed=args.seed)

    prepared: Dict[str, List[Tuple[str, Dict[str, object], str]]] = {}
    for name, group in (("train", train_recs), ("dev", dev_recs), ("test", test_recs)):
        rows = []
        for rec in group:
            text, meta = record_text_and_meta(rec, engine)
            rows.append((text, meta, rec.category))
        prepared[name] = rows

    # document frequency on train only (no leakage from dev/test)
    df: Counter = Counter()
    raw_features: Dict[str, List[Counter]] = {}
    for name, group in prepared.items():
        raw_features[name] = []
        for text, meta, _label in group:
            features = extract_features(text, meta)
            raw_features[name].append(features)
            if name == "train":
                for feature in features:
                    df[feature] += 1

    vocab = [f for f, c in df.most_common() if c >= args.min_df][: args.max_features]
    vocab_set = set(vocab)
    n_docs = max(1, len(prepared["train"]))
    idf = {f: math.log((1.0 + n_docs) / (1.0 + df[f])) + 1.0 for f in vocab}

    vectors: Dict[str, List[Dict[str, float]]] = {
        name: [tfidf_vector(Counter({f: c for f, c in feats.items() if f in vocab_set}), idf)
               for feats in raw_features[name]]
        for name in prepared
    }
    truths = {name: [row[2] for row in prepared[name]] for name in prepared}

    train_pairs = [(vectors["train"][i], label_index[truths["train"][i]]) for i in range(len(vectors["train"]))]
    weights, bias = train_linear(train_pairs, len(labels), args.epochs, args.lr, args.l2, args.seed)

    # truncation for on-device size, chosen by |weight| and then re-normalised per label
    truncated: List[Dict[str, float]] = []
    for j, w in enumerate(weights):
        keep = sorted(w.items(), key=lambda kv: -abs(kv[1]))[: args.max_per_label]
        truncated.append({f: v for f, v in keep if abs(v) > 1e-4})
    dropped = sum(len(w) for w in weights) - sum(len(w) for w in truncated)

    def predict_ml(vec: Dict[str, float]) -> str:
        scores = score_vector(vec, truncated, bias)
        return labels[max(range(len(scores)), key=lambda i: scores[i])]

    rules = RuleTier(lexicon)

    def model_decision(index: int, name: str) -> Tuple[Optional[str], float, Dict[str, float]]:
        vec = vectors[name][index]
        if not vec:
            return None, 0.0, {}
        scores = score_vector(vec, truncated, bias)
        probs = softmax(scores)
        best = max(range(len(probs)), key=lambda i: probs[i])
        return labels[best], probs[best], dict(zip(labels, probs))



    def predict_cascade(index: int, name: str, arbitrate: bool = True) -> Tuple[Optional[str], str]:
        """Tier 0 with the arbitration policy.

        ``arbitrate=False`` is the naive 'rules have priority' cascade, kept as the ablation arm
        this table reports against: it is what a hand-written keyword layer usually ships as.
        """
        text, meta, _ = prepared[name][index]
        label, why, strength = rules.predict(text, meta)
        return arbitrate_with(label, why, strength, *model_decision(index, name)[:2],
                              strict=not arbitrate)

    def run_tier(name: str, mode: str) -> List[Optional[str]]:
        out: List[Optional[str]] = []
        for index in range(len(prepared[name])):
            text, meta, _ = prepared[name][index]
            if mode == "rules":
                label, _why, _strength = rules.predict(text, meta)
                out.append(label)
            elif mode == "model":
                out.append(predict_ml(vectors[name][index]))
            elif mode == "cascade_strict":
                label, _why = predict_cascade(index, name, arbitrate=False)
                out.append(label)
            else:  # cascade (arbitrated)
                label, _why = predict_cascade(index, name)
                out.append(label)
        return out

    arms = {}
    for mode in ("rules", "model", "cascade_strict", "cascade"):
        arms[mode] = {name: evaluate(run_tier(name, mode), truths[name], labels) for name in ("train", "dev", "test")}
    # A Tier-2 (on-device LLM) arm is *not* measured here. To size the headroom honestly we
    # record the residual count the cascade leaves for such a tier, not a simulated accuracy.
    residual = arms["cascade"]["test"]["n"] - sum(
        arms["cascade"]["test"]["per_class"][label]["predicted"] for label in labels
    ) + arms["cascade"]["test"]["abstentions"]
    arms["cascade"]["test_oracle_upper_bound"] = {
        "records_a_third_tier_would_have_to_resolve": residual,
        "note": "Tier-2 accuracy is NOT measured; a device run of Qwen 2.5 via LiteRT-LM is future work.",
    }

    # latency on the host, reported as host-side only (device numbers need adb)
    sample = [v for v in vectors["test"] if v][:200] or vectors["train"][:50]
    started = time.perf_counter()
    for vec in sample:
        score_vector(vec, truncated, bias)
    elapsed = time.perf_counter() - started
    per_record_ms = round(1000.0 * elapsed / max(1, len(sample)), 4)

    # ---- export --------------------------------------------------------------
    lines = [f"#ghana-cls\t{MODEL_VERSION}"]
    for j, label in enumerate(labels):
        lines.append(f"L\t{label}\t{round(bias[j], 4)}")
    # One idf line per vocabulary feature. The on-device runtime must normalise over the
    # *same* set of features the trainer did, so idf is part of the shipped artifact -
    # not just the weights. Features absent from the model still contribute to the norm,
    # exactly as in tfidf_vector() above; that is what keeps parity exact.
    for feature in sorted(idf):
        lines.append(f"I\t{feature}\t{round(idf[feature], 6)}")
    entries = 0
    for j, w in enumerate(truncated):
        for feature, value in sorted(w.items()):
            lines.append(f"W\t{feature}\t{j}\t{round(value, 4)}")
            entries += 1
    args.model_out.parent.mkdir(parents=True, exist_ok=True)
    args.model_out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    model_bytes = args.model_out.stat().st_size

    meta_doc = {
        "version": MODEL_VERSION,
        "labels": labels,
        "features": {
            "word_unigrams": "u:<word>", "word_bigrams": "b:<w1>_<w2>",
            "char_trigrams_in_word": "c3:<abc>", "merchant_tokens": "m:<token> / M:<full_name>",
            "structural": ["op:<operator>", "k:<tx_kind>", "d:<direction>", "ch:<channel>",
                           "st:<status>", "amt:<bucket>", "pb:yes|no", "cp:yes|no"],
            "weighting": "sublinear tf (1+log tf) x idf, L2 normalised over model vocab",
            "text_preprocessing": "lowercase, operator boilerplate stripped, split on non-alphanumerics",
        },
        "training": {"epochs": args.epochs, "lr0": args.lr, "l2": args.l2, "min_df": args.min_df,
                     "max_features": args.max_features, "max_per_label": args.max_per_label,
                     "seed": args.seed, "vocab": len(vocab), "weights_kept": entries,
                     "weights_dropped_by_truncation": dropped,
                     "train": len(vectors["train"]), "dev": len(vectors["dev"]), "test": len(vectors["test"])},
        "model_bytes": model_bytes,
        "idf_entries": len(idf),
        "host_inference_ms_per_record": per_record_ms,
        "corpus": args.corpus.name,
    }
    args.meta_out.write_text(json.dumps(meta_doc, indent=2), encoding="utf-8")

    # ---- parity fixtures for the Kotlin runtime -------------------------------
    # The fixtures MUST be produced by the shipped artifact, not by the in-memory trainer: the
    # TSV quantises weights to four decimals, so scores computed from the unrounded weights could
    # differ from the phone's and even flip a borderline argmax. Read our own export back and use
    # *that* as the oracle - which also proves the file format is parseable.
    shipped_labels: List[str] = []
    shipped_bias: List[float] = []
    shipped_idf: Dict[str, float] = {}
    shipped_weights: List[Dict[str, float]] = []
    for line in args.model_out.read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if parts[0] == "L":
            shipped_labels.append(parts[1])
            shipped_bias.append(float(parts[2]))
        elif parts[0] == "I":
            shipped_idf[parts[1]] = float(parts[2])
        elif parts[0] == "W":
            index = int(parts[2])
            while len(shipped_weights) <= index:
                shipped_weights.append({})
            shipped_weights[index][parts[1]] = float(parts[3])
    assert shipped_labels == labels, "label order drifted between trainer and export"

    def shipped_vector(features: Counter) -> Dict[str, float]:
        vec = {f: (1.0 + math.log(c)) * shipped_idf[f] for f, c in features.items() if f in shipped_idf}
        norm = math.sqrt(sum(v * v for v in vec.values()))
        return {k: v / norm for k, v in vec.items()} if norm > 0 else {}

    def shipped_scores(vec: Dict[str, float]) -> List[float]:
        return [shipped_bias[j] + sum(w.get(f, 0.0) * v for f, v in vec.items())
                for j, w in enumerate(shipped_weights)]

    # Fixtures are chosen by *policy branch*, not by taking the first N rows: a parity test whose
    # records all happen to be structural-rule cases would never exercise the arbitration path,
    # which is the only part of Tier 0 + Tier 1 integration that could plausibly be wrong.
    branches: Dict[str, List[int]] = {}
    decisions: Dict[int, Dict[str, Any]] = {}
    for index in range(len(vectors["test"])):
        text, meta, gold = prepared["test"][index]
        features = raw_features["test"][index]
        vector = shipped_vector(features)
        scores = shipped_scores(vector)
        probabilities = softmax(scores)
        model_label = labels[max(range(len(probabilities)), key=lambda i: probabilities[i])]
        model_prob = max(probabilities)
        rule_label, rule_why, rule_strength = rules.predict(text, meta)
        cascade_label, cascade_tier = arbitrate_with(rule_label, rule_why, rule_strength, model_label, model_prob)
        strict_label, _ = arbitrate_with(rule_label, rule_why, rule_strength, model_label, model_prob, strict=True)
        if rule_label and cascade_label != rule_label:
            branch = "model_overrules_rule"
        elif rule_label and rule_strength == RuleTier.STRUCTURAL:
            branch = "structural_rule"
        elif rule_strength == RuleTier.MERCHANT:
            branch = "merchant_lexicon"
        elif rule_strength == RuleTier.UNIQUE:
            branch = "unique_lexicon"
        elif rule_strength == RuleTier.WEAK:
            branch = "weak_rule"
        else:
            branch = "model_only"
        branches.setdefault(branch, []).append(index)
        decisions[index] = {
            "branch": branch,
            "text": text,
            "meta": meta,
            "gold": gold,
            "features_in_model": {k: features[k] for k in sorted(vector)},
            "vector": {k: round(v, 6) for k, v in sorted(vector.items())},
            "scores": [round(v, 6) for v in scores],
            "prediction": model_label,
            "model_prob": round(model_prob, 4),
            "rule_label": rule_label,
            "rule_why": rule_why,
            "rule_strength": rule_strength,
            "cascade_label": cascade_label,
            "cascade_tier": cascade_tier,
            "cascade_strict_label": strict_label,
        }

    per_branch = max(1, 40 // max(1, len(branches)))
    chosen = [i for branch in sorted(branches) for i in branches[branch][:per_branch]]
    fixtures = [decisions[i] for i in chosen[:40]]
    print("fixture branches: " + ", ".join(f"{b}={len(v)}" for b, v in sorted(branches.items())))

    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "parity_fixtures.json").write_text(json.dumps(fixtures, indent=1), encoding="utf-8")

    # A human-readable companion, because 'we have parity tests' is only a claim if you can see
    # that every branch of the arbitration policy is actually exercised by a fixture.
    from collections import Counter as _Counter
    branch_counts = _Counter(f["branch"] for f in fixtures)
    md_p = ["# Parity fixtures (Python -> Kotlin)", "",
            f"{len(fixtures)} records selected to cover every branch of the arbitration policy, "
            "written by `tools/train.py` and copied to "
            "`parser-core/src/test/resources/ghana/parity_fixtures.tsv` by `tools/sync_kotlin.py`. "
            "`TestGhanaCategoryClassifier.kt` asserts the feature vector, every class score, the "
            "argmax, `rule_why`, `rule_strength`, the cascade label, the **tier string** and the "
            "strict label per fixture, so a Kotlin-side drift in the policy fails the build.", "",
            "| branch | fixtures | what it proves |", "|---|---|---|"]
    branch_notes = {
        "structural_rule": "a structural fact wins and the model is never consulted",
        "merchant_lexicon": "a keyword that matched inside a merchant name loses to a confident model",
        "unique_lexicon": "exactly one vocabulary matched; the model may still overrule at >= 0.50",
        "model_overrules_rule": "the overrule path fired - model label + probability in the tier string",
        "model_only": "no rule fired, so the model alone decides",
    }
    for branch, count in branch_counts.most_common():
        md_p.append(f"| `{branch}` | {count} | {branch_notes.get(branch, '')} |")
    md_p += ["", "| # | operator | branch | cascade label | tier | strict label |", "|---|---|---|---|---|---|"]
    for i, f in enumerate(fixtures, start=1):
        meta = f.get("meta") or {}
        md_p.append(f"| {i} | {meta.get('operator', '-')} | `{f['branch']}` | {f.get('cascade_label') or '-'} | "
                    f"`{(f.get('cascade_tier') or '-')}` | {f.get('cascade_strict_label') or '-'} |")
    md_p.append("")
    (args.out_dir / "parity_fixtures.md").write_text("\n".join(md_p), encoding="utf-8")

    # ---- reports ---------------------------------------------------------------
    report = {"arms": arms, "meta": meta_doc, "labels": labels}
    (args.out_dir / "classifier_metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

    md = ["# Objective 3 - on-device spending classification", "",
          f"> Corpus: `{args.corpus.name}`. Split: "
          f"train {meta_doc['training']['train']} / dev {meta_doc['training']['dev']} / test {meta_doc['training']['test']} "
          "(deterministic, stratified by operator x kind).", "",
          "| arm | test accuracy | test macro-F1 | dev accuracy | train accuracy |",
          "|---|---|---|---|---|"]
    names = {"rules": "Tier 0 only (deterministic rules)", "model": "Tier 1 only (linear TF-IDF)",
             "cascade_strict": "Cascade, rules always win (naive priority)",
             "cascade": "Cascade with arbitration (rules win on facts, model wins on names) - shipped"}
    for mode in ("rules", "model", "cascade_strict", "cascade"):
        row = arms[mode]
        md.append(f"| {names[mode]} | {row['test']['accuracy']:.3f} | {row['test']['macro_f1']:.3f} | "
                  f"{row['dev']['accuracy']:.3f} | {row['train']['accuracy']:.3f} |")
    md += ["", "## Ablations", "",
           "| arm | dev acc | test acc | test macro-F1 | test coverage | abstentions | \u0394 accuracy vs cascade | notes |",
           "|---|---|---|---|---|---|---|---|"]
    notes = {"rules": "Deterministic rules only - the no-learning baseline, and the layer that stays "
                      "authoritative for structural facts.",
             "model": "Linear model only - what pure lexical context buys. Throws away facts the message "
                      "states outright (a paybill prefix, 'for ELECTRICITY BILL', top-up vs spend).",
             "cascade_strict": "Naive rule priority: a rule always wins, so a keyword inside a merchant "
                               "name ('AIRTIME SOLUTIONS LTD') overrules the context. The cost of ignoring "
                               "the model.",
             "cascade": "Shipped policy: rules win on structure (short-circuit, no model consulted); "
                        "the model may overrule a keyword-only rule when it is confident and disagrees."}
    cascade_acc = arms["cascade"]["test"]["accuracy"]
    for mode in ("rules", "model", "cascade_strict", "cascade"):
        row = arms[mode]
        delta = row["test"]["accuracy"] - cascade_acc
        md.append(f"| {names[mode]} | {row['dev']['accuracy']:.3f} | {row['test']['accuracy']:.3f} | "
                  f"{row['test']['macro_f1']:.3f} | {row['test']['coverage']:.3f} | "
                  f"{row['test']['abstentions']} | {delta:+.3f} | {notes[mode]} |")
    md += ["", f"- cascade abstentions on test: **{arms['cascade']['test']['abstentions']}** "
               "(records where rules abstained and no model feature fired); Tier 0 alone abstains on "
               f"**{arms['rules']['test']['abstentions']}** of the same 180 records",
           f"- model artifact: `{args.model_out.name}`, **{model_bytes:,} bytes**, {entries:,} weight entries, "
           f"vocab {len(vocab):,} (kept {meta_doc['training']['weights_dropped_by_truncation']:,} dropped by truncation)",
           f"- host-side scoring cost: **{per_record_ms} ms/record** (single thread, CPython; *not* a device number - "
           f"measure the Kotlin path with `./gradlew :parser-core:test --tests '*GhanaCategoryClassifierTest*'` on device)",
           "", "## Per-class detail (cascade, test split)", "",
           "| label | P | R | F1 | support | predicted |", "|---|---|---|---|---|---|"]
    for label in labels:
        row = arms["cascade"]["test"]["per_class"][label]
        fmt = lambda v: "-" if v is None else f"{v:.3f}"
        md.append(f"| {label} | {fmt(row['precision'])} | {fmt(row['recall'])} | {fmt(row['f1'])} | "
                  f"{row['support']} | {row['predicted']} |")
    md += ["", "## Confusion matrix (cascade, test split) - rows = gold, cols = predicted", "",
           "| gold | " + " | ".join(labels) + " | ABSTAINED |", "|---|" + "---|" * (len(labels) + 1)]
    confusion = arms["cascade"]["test"]["confusion"]
    for gold in labels:
        row = confusion.get(gold, {})
        cells = [str(row.get(l, 0)) for l in labels] + [str(row.get("ABSTAINED", 0))]
        if any(int(c) for c in cells):
            md.append(f"| {gold} | " + " | ".join(cells) + " |")
    md += ["", "## Tier-2 (on-device LLM) - upper bound, not a measurement", "",
           f"If a third tier resolved every residual the cascade accuracy on test would rise from "
           f"{arms['cascade']['test']['accuracy']:.3f} toward 1.0 by construction. This arm is reported "
           "only to size the headroom; measuring it requires running Qwen 2.5 via LiteRT-LM on the target "
           "handset and is listed as future work in the paper.", ""]
    (args.out_dir / "classifier_metrics.md").write_text("\n".join(md), encoding="utf-8")

    print(f"labels={len(labels)} vocab={len(vocab)} weights={entries} model={model_bytes:,}B")
    for mode in ("rules", "model", "cascade_strict", "cascade"):
        row = arms[mode]["test"]
        print(f"{mode:<8} test acc={row['accuracy']:.3f} macro-F1={row['macro_f1']:.3f} "
              f"abstain={row['abstentions']}")
    print(f"wrote {args.model_out}")
    print(f"wrote {args.out_dir / 'classifier_metrics.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
