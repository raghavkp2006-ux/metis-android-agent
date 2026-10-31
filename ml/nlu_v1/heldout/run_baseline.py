"""
Baseline evaluation: frozen model against two held-out test sets.

1. test.csv       — 128 rows, author SM (human-written), 16 intents
2. raghav_test_100.csv — 100 rows, author AI_CLAUDE_TEST (AI-written), 5 new intents

The model was trained on the original 16 labels. The 5 new labels
(CREATE_TIMER, CREATE_ALARM, CALL_PERSON, SEND_MESSAGE, CHECK_MEMORY) have
zero training rows, so the model cannot predict them.  For the AI-written
smoke test the best-case outcome is that the model rejects (UNKNOWN) all 100
utterances — any non-UNKNOWN prediction is a false accept into the wrong class.

Protected-file rules: this script reads model.json and the CSV files but never
writes to them.  Only BASELINE_REPORT.md and sha256_manifest.txt are written.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
import sys
from collections import Counter
from pathlib import Path
from typing import Any

# ── paths ────────────────────────────────────────────────────────────────
BASE_DIR = Path(__file__).resolve().parent.parent          # ml/nlu_v1
HELDOUT_DIR = Path(__file__).resolve().parent              # ml/nlu_v1/heldout
MODEL_PATH = BASE_DIR / "model.json"
TEST_CSV = BASE_DIR / "test.csv"
SMOKE_CSV = HELDOUT_DIR / "raghav_test_100.csv"
REPORT_PATH = HELDOUT_DIR / "BASELINE_REPORT.md"
MANIFEST_PATH = HELDOUT_DIR / "sha256_manifest.txt"
CONFIG_PATH = BASE_DIR / "config.yaml"                     # actually JSON

# ── import predict.py from parent ────────────────────────────────────────
sys.path.insert(0, str(BASE_DIR))
from predict import load_model, predict_probabilities, predicted_label  # noqa: E402


# ── helpers ──────────────────────────────────────────────────────────────

def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def read_test_csv(path: Path) -> list[dict[str, str]]:
    """Read test.csv (columns: text, intent, author, style)."""
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        return [
            {
                "text": row["text"].strip(),
                "intent": row["intent"].strip(),
                "author": row["author"].strip(),
                "style": row.get("style", "").strip(),
            }
            for row in reader
            if row["text"].strip()
        ]


def read_smoke_csv(path: Path) -> list[dict[str, str]]:
    """Read raghav_test_100.csv (columns: author_id, utterance, intended_intent,
    style_tags, contains_real_pii, notes)."""
    with path.open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        return [
            {
                "text": row["utterance"].strip(),
                "intent": row["intended_intent"].strip(),
                "author": row["author_id"].strip(),
                "style": row.get("style_tags", "").strip(),
            }
            for row in reader
            if row["utterance"].strip()
        ]


def run_inference(
    rows: list[dict[str, str]],
    model: dict[str, Any],
    threshold: float,
    unknown_intent: str,
) -> list[dict[str, Any]]:
    results = []
    for row in rows:
        probs = predict_probabilities(row["text"], model)
        pred = predicted_label(probs, model["classes"], threshold, unknown_intent)
        best_class = max(model["classes"], key=probs.__getitem__)
        results.append({
            "text": row["text"],
            "truth": row["intent"],
            "predicted": pred,
            "best_class": best_class,
            "best_prob": probs[best_class],
            "style": row["style"],
        })
    return results


def compute_metrics(
    results: list[dict[str, Any]],
    classes: list[str],
    label_set: list[str] | None = None,
) -> dict[str, Any]:
    """Compute accuracy, per-intent P/R/F1, confusion, false-accept analysis.

    `classes` = model output classes (for predictions).
    `label_set` = ground-truth label set (may include labels not in `classes`).
    """
    if label_set is None:
        label_set = classes

    all_labels = sorted(set(label_set) | set(classes))
    truth = [r["truth"] for r in results]
    preds = [r["predicted"] for r in results]
    n = len(results)

    # Overall accuracy (strict: truth must match pred exactly)
    correct = sum(t == p for t, p in zip(truth, preds))
    accuracy = correct / n if n else 0.0

    # Coverage (fraction not rejected as UNKNOWN)
    covered = [i for i, p in enumerate(preds) if p != "UNKNOWN"]
    coverage = len(covered) / n if n else 0.0
    sel_acc = (
        sum(truth[i] == preds[i] for i in covered) / len(covered)
        if covered else 0.0
    )

    # Per-intent
    per_intent = {}
    for label in all_labels:
        tp = sum(t == label and p == label for t, p in zip(truth, preds))
        fp = sum(t != label and p == label for t, p in zip(truth, preds))
        fn = sum(t == label and p != label for t, p in zip(truth, preds))
        support = sum(t == label for t in truth)
        precision = tp / (tp + fp) if (tp + fp) else 0.0
        recall = tp / (tp + fn) if (tp + fn) else 0.0
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0
        per_intent[label] = {
            "precision": precision,
            "recall": recall,
            "f1": f1,
            "support": support,
            "tp": tp,
            "fp": fp,
            "fn": fn,
        }

    # Macro F1 over labels with support > 0
    labels_with_support = [l for l in all_labels if per_intent[l]["support"] > 0]
    macro_f1 = (
        sum(per_intent[l]["f1"] for l in labels_with_support) / len(labels_with_support)
        if labels_with_support else 0.0
    )

    # False accept analysis: for each utterance whose true intent is NOT in
    # model classes, what did the model predict?
    out_of_vocab_results = [r for r in results if r["truth"] not in classes]
    oov_total = len(out_of_vocab_results)
    oov_rejected = sum(r["predicted"] == "UNKNOWN" for r in out_of_vocab_results)
    oov_false_accepts = oov_total - oov_rejected
    oov_false_accept_targets = Counter(
        r["predicted"] for r in out_of_vocab_results if r["predicted"] != "UNKNOWN"
    )

    return {
        "n": n,
        "accuracy": accuracy,
        "coverage": coverage,
        "selective_accuracy": sel_acc,
        "macro_f1": macro_f1,
        "per_intent": per_intent,
        "oov_total": oov_total,
        "oov_rejected": oov_rejected,
        "oov_false_accepts": oov_false_accepts,
        "oov_false_accept_targets": dict(oov_false_accept_targets),
    }


def style_breakdown(
    results: list[dict[str, Any]],
    classes: list[str],
    label_set: list[str] | None = None,
) -> dict[str, dict[str, Any]]:
    styles = sorted(set(r["style"] for r in results if r["style"]))
    out = {}
    for style in styles:
        subset = [r for r in results if r["style"] == style]
        out[style] = compute_metrics(subset, classes, label_set)
    return out


def intent_breakdown(
    results: list[dict[str, Any]],
    classes: list[str],
) -> dict[str, dict[str, Any]]:
    intents = sorted(set(r["truth"] for r in results))
    out = {}
    for intent in intents:
        subset = [r for r in results if r["truth"] == intent]
        out[intent] = compute_metrics(subset, classes, [intent])
    return out


def format_percent(v: float) -> str:
    return f"{v:.2%}"


def format_float(v: float, decimals: int = 4) -> str:
    return f"{v:.{decimals}f}"


# ── main ─────────────────────────────────────────────────────────────────

def main() -> int:
    # Load frozen model (single read)
    model = load_model(MODEL_PATH)
    threshold = float(model["threshold"])
    unknown_intent = model["unknown_intent"]
    classes = model["classes"]
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))

    print(f"Model classes ({len(classes)}): {classes}")
    print(f"Operating threshold: {threshold}")
    print()

    # ── SHA-256 manifest of all protected files ──────────────────────────
    protected_files = [
        MODEL_PATH,
        BASE_DIR / "predict.py",
        BASE_DIR / "reference_tests.json",
        TEST_CSV,
        SMOKE_CSV,
        CONFIG_PATH,
        BASE_DIR / "run_all.py",
    ]
    manifest_lines = []
    for p in protected_files:
        if p.exists():
            digest = sha256_file(p)
            manifest_lines.append(f"{digest}  {p.relative_to(BASE_DIR)}")
            print(f"SHA-256 {p.name}: {digest[:16]}...")
    manifest_text = "\n".join(manifest_lines) + "\n"
    MANIFEST_PATH.write_text(manifest_text, encoding="utf-8")
    print(f"Wrote {MANIFEST_PATH.name} ({len(manifest_lines)} files)\n")

    # ── 1. Evaluate test.csv (SM, human-written) ────────────────────────
    print("=" * 70)
    print("SECTION A: test.csv — SM (human-written), 128 rows")
    print("=" * 70)
    test_rows = read_test_csv(TEST_CSV)
    print(f"Loaded {len(test_rows)} rows from test.csv")
    test_intents = sorted(set(r["intent"] for r in test_rows))
    print(f"Ground-truth labels ({len(test_intents)}): {test_intents}")

    test_results = run_inference(test_rows, model, threshold, unknown_intent)
    test_metrics = compute_metrics(test_results, classes, test_intents)
    test_style = style_breakdown(test_results, classes, test_intents)

    print(f"  Accuracy:           {format_percent(test_metrics['accuracy'])}")
    print(f"  Coverage:           {format_percent(test_metrics['coverage'])}")
    print(f"  Selective accuracy: {format_percent(test_metrics['selective_accuracy'])}")
    print(f"  Macro-F1:           {format_float(test_metrics['macro_f1'])}")
    print()

    # ── 2. Evaluate raghav_test_100.csv (AI-written smoke test) ─────────
    print("=" * 70)
    print("SECTION B: raghav_test_100.csv — AI-written smoke test, 100 rows")
    print("=" * 70)
    smoke_rows = read_smoke_csv(SMOKE_CSV)
    print(f"Loaded {len(smoke_rows)} rows from raghav_test_100.csv")
    smoke_intents = sorted(set(r["intent"] for r in smoke_rows))
    print(f"Ground-truth labels ({len(smoke_intents)}): {smoke_intents}")

    smoke_results = run_inference(smoke_rows, model, threshold, unknown_intent)
    smoke_metrics = compute_metrics(smoke_results, classes, smoke_intents)
    smoke_style = style_breakdown(smoke_results, classes, smoke_intents)
    smoke_by_intent = intent_breakdown(smoke_results, classes)

    print(f"  OOV total (truth not in model classes): {smoke_metrics['oov_total']}")
    print(f"  OOV rejected as UNKNOWN:                {smoke_metrics['oov_rejected']}")
    print(f"  OOV false accepts:                      {smoke_metrics['oov_false_accepts']}")
    if smoke_metrics['oov_false_accept_targets']:
        print(f"  False-accept targets: {smoke_metrics['oov_false_accept_targets']}")
    print()

    # ── Mis-prediction detail for smoke test ─────────────────────────────
    misclassified = [r for r in smoke_results if r["predicted"] != "UNKNOWN"]
    print(f"  Non-UNKNOWN predictions: {len(misclassified)} / {len(smoke_results)}")
    for r in misclassified:
        print(f"    [{r['truth']}] → {r['predicted']} ({r['best_prob']:.3f}): {r['text'][:80]}")
    print()

    # ── 3. Write BASELINE_REPORT.md ──────────────────────────────────────
    report = build_report(
        model, config, threshold, classes,
        test_rows, test_results, test_metrics, test_style, test_intents,
        smoke_rows, smoke_results, smoke_metrics, smoke_style, smoke_intents,
        smoke_by_intent,
        manifest_text,
    )
    REPORT_PATH.write_text(report, encoding="utf-8")
    print(f"Wrote {REPORT_PATH.name}")
    return 0


def build_report(
    model, config, threshold, classes,
    test_rows, test_results, test_metrics, test_style, test_intents,
    smoke_rows, smoke_results, smoke_metrics, smoke_style, smoke_intents,
    smoke_by_intent,
    manifest_text,
) -> str:
    STATUS = model["status"]
    lines = []
    a = lines.append

    a(f"# Baseline Evaluation Report — {STATUS}")
    a("")
    a("> **Single evaluation pass. No hyperparameters, thresholds, or settings were")
    a("> tuned against any test data. The model and operating threshold are frozen")
    a("> from Phase A (REPORT.md).**")
    a("")
    a("## Scope")
    a("")
    a("This report evaluates the frozen logistic-regression NLU model against two")
    a("separate held-out test sets. Results are reported **separately** and are")
    a("never merged into a single headline number.")
    a("")
    a("| Test Set | File | Author | Origin | Rows | Labels | Purpose |")
    a("|---|---|---|---|---:|---|---|")
    a(f"| **A. Human test** | `test.csv` | SM | Human-written | {len(test_rows)} | {len(test_intents)} original intents | Frozen human evaluation (Phase B) |")
    a(f"| **B. AI smoke test** | `heldout/raghav_test_100.csv` | AI_CLAUDE_TEST | AI-written (Claude) | {len(smoke_rows)} | {len(smoke_intents)} new intents | Smoke test for 5 new zero-row labels |")
    a("")
    a("> [!IMPORTANT]")
    a("> **Section B scores are optimistic.** The AI-written smoke test was generated")
    a("> by Claude, not collected from real users. AI-generated utterances tend to be")
    a("> cleaner, more formulaic, and less ambiguous than real user input. Scores on")
    a("> this set should be treated as an upper bound, not a production estimate.")
    a("")
    a(f"**Frozen model settings:** threshold = {threshold}, unknown_intent = UNKNOWN,")
    a(f"model classes = {len(classes)} ({', '.join(classes)})")
    a("")
    a("---")
    a("")

    # ═══════════════════════════════════════════════════════════════════════
    # SECTION A: test.csv
    # ═══════════════════════════════════════════════════════════════════════
    a("## Section A: Human Test — `test.csv` (SM, 128 rows)")
    a("")
    a("Author SM, human-written, 16 intents. This is the frozen Phase B evaluation.")
    a("")
    a("### A.1 Headline Metrics")
    a("")
    a("| Metric | Value |")
    a("|---|---:|")
    a(f"| Accuracy | {format_percent(test_metrics['accuracy'])} |")
    a(f"| Selective accuracy (covered only) | {format_percent(test_metrics['selective_accuracy'])} |")
    a(f"| Macro-F1 | {format_float(test_metrics['macro_f1'])} |")
    a(f"| Coverage (non-UNKNOWN) | {format_percent(test_metrics['coverage'])} |")
    a(f"| Total rows | {test_metrics['n']} |")
    a("")

    # Per-intent table
    a("### A.2 Per-Intent Precision / Recall / F1")
    a("")
    a("| Intent | P | R | F1 | Support | TP | FP | FN |")
    a("|---|---:|---:|---:|---:|---:|---:|---:|")
    for label in sorted(test_metrics["per_intent"].keys()):
        m = test_metrics["per_intent"][label]
        if m["support"] > 0 or m["fp"] > 0:
            a(f"| {label} | {format_float(m['precision'])} | {format_float(m['recall'])} | {format_float(m['f1'])} | {m['support']} | {m['tp']} | {m['fp']} | {m['fn']} |")
    a("")

    # Style breakdown
    if test_style:
        a("### A.3 Style-Subset Metrics")
        a("")
        a("| Style | Count | Accuracy | Selective Acc | Macro-F1 | Coverage |")
        a("|---|---:|---:|---:|---:|---:|")
        for style in sorted(test_style):
            m = test_style[style]
            a(f"| {style} | {m['n']} | {format_percent(m['accuracy'])} | {format_percent(m['selective_accuracy'])} | {format_float(m['macro_f1'])} | {format_percent(m['coverage'])} |")
        a("")

    # Confusion highlights for test.csv
    a("### A.4 Notable Misclassifications")
    a("")
    test_wrong = [r for r in test_results if r["truth"] != r["predicted"]]
    if not test_wrong:
        a("No misclassifications on the covered predictions.")
    else:
        # Group by (truth → predicted)
        confusion_pairs = Counter((r["truth"], r["predicted"]) for r in test_wrong)
        a("| True Intent | Predicted | Count |")
        a("|---|---|---:|")
        for (truth, pred), count in confusion_pairs.most_common(20):
            a(f"| {truth} | {pred} | {count} |")
    a("")

    a("---")
    a("")

    # ═══════════════════════════════════════════════════════════════════════
    # SECTION B: raghav_test_100.csv
    # ═══════════════════════════════════════════════════════════════════════
    a("## Section B: AI-Written Smoke Test — `heldout/raghav_test_100.csv` (100 rows)")
    a("")
    a("> [!CAUTION]")
    a("> **These scores are optimistic.** This file was AI-generated by Claude")
    a("> (author_id = AI_CLAUDE_TEST). It is NOT a substitute for human-collected")
    a("> test data. Results here represent an upper bound on expected real-world")
    a("> performance for these 5 intents.")
    a("")
    a("### Context")
    a("")
    a("The 5 labels tested here (CREATE_TIMER, CREATE_ALARM, CALL_PERSON,")
    a("SEND_MESSAGE, CHECK_MEMORY) have **zero training rows** in the current model.")
    a("They are not in the model's output class set. The model can only predict one")
    a("of the 16 trained classes or reject as UNKNOWN (via the threshold).")
    a("")
    a("**For this evaluation, the ideal outcome is 100% rejection as UNKNOWN** — any")
    a("non-UNKNOWN prediction is a false accept into the wrong class.")
    a("")

    a("### B.1 Headline Metrics")
    a("")
    a("| Metric | Value |")
    a("|---|---:|")
    a(f"| Total utterances | {smoke_metrics['n']} |")
    a(f"| All are out-of-vocabulary (OOV) | {smoke_metrics['oov_total']} |")
    a(f"| Correctly rejected as UNKNOWN | {smoke_metrics['oov_rejected']} ({format_percent(smoke_metrics['oov_rejected'] / smoke_metrics['n'] if smoke_metrics['n'] else 0)}) |")
    a(f"| **False accepts (wrong class)** | **{smoke_metrics['oov_false_accepts']}** ({format_percent(smoke_metrics['oov_false_accepts'] / smoke_metrics['n'] if smoke_metrics['n'] else 0)}) |")
    a("")

    if smoke_metrics['oov_false_accept_targets']:
        a("#### False-Accept Breakdown by Predicted Class")
        a("")
        a("| Predicted (wrong) Class | Count |")
        a("|---|---:|")
        for cls, count in sorted(smoke_metrics['oov_false_accept_targets'].items(), key=lambda x: -x[1]):
            a(f"| {cls} | {count} |")
        a("")

    # Per-intent (true intent) rejection analysis
    a("### B.2 Per-Intent Rejection Analysis")
    a("")
    a("| True Intent | Rows | Rejected (UNKNOWN) | False Accepts | Rejection Rate |")
    a("|---|---:|---:|---:|---:|")
    for intent in smoke_intents:
        subset = [r for r in smoke_results if r["truth"] == intent]
        rejected = sum(r["predicted"] == "UNKNOWN" for r in subset)
        false_acc = len(subset) - rejected
        rate = rejected / len(subset) if subset else 0.0
        a(f"| {intent} | {len(subset)} | {rejected} | {false_acc} | {format_percent(rate)} |")
    a("")

    # Style breakdown
    if smoke_style:
        a("### B.3 Style-Subset Analysis")
        a("")
        a("| Style | Count | Rejected (UNKNOWN) | False Accepts | Rejection Rate |")
        a("|---|---:|---:|---:|---:|")
        for style in sorted(smoke_style):
            m = smoke_style[style]
            subset = [r for r in smoke_results if r["style"] == style]
            rejected = sum(r["predicted"] == "UNKNOWN" for r in subset)
            false_acc = len(subset) - rejected
            rate = rejected / len(subset) if subset else 0.0
            a(f"| {style} | {len(subset)} | {rejected} | {false_acc} | {format_percent(rate)} |")
        a("")

    # Detailed false accepts
    false_accepts = [r for r in smoke_results if r["predicted"] != "UNKNOWN"]
    if false_accepts:
        a("### B.4 False Accept Details")
        a("")
        a("These utterances were incorrectly classified into a trained class instead of being rejected:")
        a("")
        a("| True Intent | Predicted | Confidence | Style | Utterance |")
        a("|---|---|---:|---|---|")
        for r in false_accepts:
            text = r["text"][:70] + ("…" if len(r["text"]) > 70 else "")
            a(f"| {r['truth']} | {r['predicted']} | {r['best_prob']:.3f} | {r['style']} | {text} |")
        a("")
    else:
        a("### B.4 False Accept Details")
        a("")
        a("✅ No false accepts — all 100 AI-written smoke test utterances were correctly")
        a("rejected as UNKNOWN.")
        a("")

    a("---")
    a("")

    # ═══════════════════════════════════════════════════════════════════════
    # SHA-256 Manifest
    # ═══════════════════════════════════════════════════════════════════════
    a("## SHA-256 Manifest (Protected Files)")
    a("")
    a("Computed at evaluation time. No protected file was modified during this run.")
    a("")
    a("```")
    a(manifest_text.rstrip())
    a("```")
    a("")

    # ═══════════════════════════════════════════════════════════════════════
    # Frozen Settings
    # ═══════════════════════════════════════════════════════════════════════
    a("## Frozen Settings")
    a("")
    a("| Setting | Value |")
    a("|---|---|")
    a(f"| Model status | `{model['status']}` |")
    a(f"| Operating threshold | {threshold} |")
    a(f"| Unknown intent | {model['unknown_intent']} |")
    a(f"| Model classes | {len(classes)} |")
    a(f"| Seed | {model.get('seed', 'N/A')} |")
    a(f"| Mode | {model.get('mode', 'N/A')} |")
    a(f"| config.yaml hash | see manifest above |")
    a("")

    # ═══════════════════════════════════════════════════════════════════════
    # Limitations
    # ═══════════════════════════════════════════════════════════════════════
    a("## Limitations")
    a("")
    a(f"1. **{STATUS}**: single frozen split with 1 human test author (SM);")
    a("   results are indicative, not definitive.")
    a("2. **Section B scores are optimistic**: the AI-written smoke test was")
    a("   generated by Claude and is cleaner/more formulaic than real user input.")
    a("3. The 5 new intents have zero training rows — the model has never seen")
    a("   these labels. Evaluation here is purely about rejection quality.")
    a("4. No threshold or hyperparameter was tuned against either test set.")
    a("5. Rules-only and hybrid evaluation are not included (rules_pred not filled).")
    a("6. The fixed logistic-regression configuration is not authorization.")
    a("   Any action requires the existing rules and policy engine.")
    a("")

    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    sys.exit(main())
