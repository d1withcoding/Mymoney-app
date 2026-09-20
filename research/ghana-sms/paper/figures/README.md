# paper/figures

No figures yet, on purpose: every result today is a table, and a chart drawn from the *seed* corpus
would look like a measurement of real traffic.

When the study corpus is in place, draw these, in this order:

1. **Extraction coverage by field** - horizontal bars, hybrid vs the two ablation arms. Source:
   `results/extraction_metrics.md` (per-field tables of each `## Strategy:` section).
2. **Format-family accumulation curve** - x = number of whole-message templates, y = share of
   messages matched, one line per operator. This is the figure that justifies a field-oriented
   parser instead of a template parser. Source: `results/taxonomy.md` plus `taxonomy.py --json`.
3. **Confidence histogram with the accept threshold marked at 0.45.** Source:
   `extraction_metrics.md`, `### Confidence distribution`.
4. **Category arms** (rules / model / naive cascade / cascade with arbitration: test accuracy and
   macro-F1). Source: `results/classifier_metrics.md`, `## Ablations`.
5. **Per-class recall with support counts printed under each bar** - at this corpus size the
   supports matter more than the averages.

Keep the plotting code in `scripts/figures/` so `scripts/run_all.sh` regenerates the figures; a
hand-drawn figure drifts from the table on the facing page within one corpus update.
