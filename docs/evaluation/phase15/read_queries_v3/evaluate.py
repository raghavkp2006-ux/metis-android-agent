#!/usr/bin/env python3
"""Evaluate v3 without conflating development metrics with adoption evidence."""

from __future__ import annotations

import argparse
import json
import platform
import sys
from collections import Counter
from pathlib import Path

import numpy as np
import sklearn
from sklearn.metrics import classification_report, confusion_matrix, f1_score

from dataset import HERE, LABELS, PARENT, PARTITIONS, READ_LABELS, ROOT, SEED, source_hashes, validate
from corpus import atomic_text, baseline_source_hash, read_csv, sha256
from candidate import hybrid, predict, train

# Import the immutable v2 utilities under an unambiguous name: both experiment
# directories have an evaluate.py entry point.
import importlib.util
spec = importlib.util.spec_from_file_location("phase15_v2_evaluator", PARENT / "evaluate.py")
v2 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v2)


def metrics(rows, predictions):
    expected = [row["intent"] for row in rows]
    report = classification_report(expected, predictions, labels=LABELS, output_dict=True, zero_division=0)
    known = [index for index, label in enumerate(expected) if label != "UNKNOWN"]
    accepted = [index for index, label in enumerate(predictions) if label in READ_LABELS]
    return {"macro_f1": float(f1_score(expected, predictions, labels=LABELS, average="macro", zero_division=0)),
            "read_coverage": sum(predictions[index] in READ_LABELS for index in known) / len(known) if known else 0.0,
            "accepted_count": len(accepted),
            "accepted_precision": sum(expected[index] == predictions[index] for index in accepted) / len(accepted) if accepted else None,
            "unknown_false_accepts": sum(actual == "UNKNOWN" and guess != "UNKNOWN" for actual, guess in zip(expected, predictions)),
            "unknown_count": expected.count("UNKNOWN"),
            "per_class": {label: report[label] for label in LABELS},
            "confusion": confusion_matrix(expected, predictions, labels=LABELS).tolist()}


def project_read_labels(predictions):
    return [label if label in READ_LABELS else "UNKNOWN" for label in predictions]


def select_threshold(model, rows, temperature):
    if any(row["split"] != "threshold" for row in rows):
        raise ValueError("Thresholds may use only the threshold partition")
    candidates = []
    for threshold in v2.THRESHOLDS:
        labels, _, _ = predict(model, rows, temperature, threshold)
        score = metrics(rows, labels)
        if score["unknown_false_accepts"] == 0 and score["accepted_count"] > 0:
            candidates.append((score["macro_f1"], score["read_coverage"], -threshold, threshold))
    return max(candidates)[-1] if candidates else None


def select_candidate(parts):
    """Fit on train, calibrate on calibration, select on threshold families only."""
    if set(parts) != set(PARTITIONS) or any(row["split"] != part for part, rows in parts.items() for row in rows):
        raise ValueError("Candidate selection requires correctly separated partitions")
    candidates = []
    for regularization in (1.0, 4.0, 16.0, 64.0):
        model = train(parts["train"], regularization)
        temperature, calibration = v2.fit_temperature(model, parts["calibration"])
        threshold = select_threshold(model, parts["threshold"], temperature)
        labels, _, _ = predict(model, parts["threshold"], temperature, threshold)
        score = metrics(parts["threshold"], labels)
        candidates.append({"C": regularization, "model": model, "temperature": temperature,
                           "calibration": calibration, "threshold": threshold, "validation": score})
    best = max(candidates, key=lambda value: (value["validation"]["macro_f1"], value["validation"]["read_coverage"], -value["C"]))
    selection = [{key: value for key, value in candidate.items() if key not in {"model", "calibration"}}
                 for candidate in candidates]
    return best, selection


def score_review(path, model, temperature, threshold, baseline, output):
    from review import load_frozen
    frozen = load_frozen(path)
    manifest, annotations = frozen["manifest"], frozen["rows"]
    # Require a matching native snapshot including these reviewed requests. A
    # Python transcription is never a substitute for the actual production rules.
    if not {row["id"] for row in annotations} <= baseline.keys():
        raise ValueError("Reviewed benchmark needs its own native snapshot; see review instructions")
    rows = []
    for row in annotations:
        rows.append({**row, "intent": row["reviewed_intent"]})
    receipt = output.parent / f"review-scored-{sha256(path)}.json"
    if receipt.exists():
        raise ValueError("Reviewed benchmark already scored; use a fresh benchmark for another adoption attempt")
    labels, _, _ = predict(model, rows, temperature, threshold)
    rules = project_read_labels([baseline[row["id"]]["intent"] for row in rows])
    combined = project_read_labels(hybrid(rows, labels, baseline))
    report = {"benchmark_sha256": sha256(path), "reviewer": manifest["reviewer"],
              "rules": metrics(rows, rules), "candidate": metrics(rows, labels), "rules_first": metrics(rows, combined),
              "scope": "Reviewed intent evaluation only; entity/outcome integration remains unmeasured"}
    human_indices = [index for index, row in enumerate(rows) if row["human_authored"] == "yes"]
    human_rows = [rows[index] for index in human_indices]
    report["human_authored_challenges"] = {
        "rules": metrics(human_rows, [rules[index] for index in human_indices]),
        "candidate": metrics(human_rows, [labels[index] for index in human_indices]),
        "rules_first": metrics(human_rows, [combined[index] for index in human_indices]),
    }
    atomic_text(receipt, json.dumps(report, indent=2) + "\n")
    return report


def evaluate(baseline_path, output, reviewed_benchmark=None):
    manifest = json.loads((HERE / "manifest-v3.json").read_text(encoding="utf-8"))
    if manifest["sources"] != source_hashes():
        raise ValueError("Changed v3 source; intentionally rebuild and inspect the manifest")
    for name, digest in manifest["hashes"].items():
        if sha256(HERE / name) != digest:
            raise ValueError(f"Frozen v3 dataset differs: {name}")
    if manifest["historical_safety_sha256"] != sha256(PARENT / "safety-v2.csv"):
        raise ValueError("Historical v2 safety corpus changed")
    rows = read_csv(HERE / "intent-v3.csv")
    validate(rows)
    safety = read_csv(PARENT / "safety-v2.csv")
    native_rows = rows + safety
    input_path = HERE / "rules-input-v3.tsv"
    if reviewed_benchmark:
        from corpus import write_native_input, FIXED_TIME, FIXED_ZONE
        annotations = json.loads(reviewed_benchmark.read_text(encoding="utf-8"))["rows"]
        native_rows += [{**row, "timestamp": FIXED_TIME, "zone": FIXED_ZONE} for row in annotations]
        input_path = output.parent / "rules-input-reviewed-v3.tsv"
        # The reviewer workflow prepares this before the native task. Never
        # silently replace a native input while validating a previous snapshot.
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            expected = Path(directory) / "expected.tsv"
            write_native_input(expected, native_rows)
            if not input_path.exists() or expected.read_bytes() != input_path.read_bytes():
                raise ValueError("Missing/stale reviewed native input; run prepare-reviewed-input first")
    baseline = v2.load_baseline(baseline_path, native_rows, sha256(input_path), baseline_source_hash(ROOT))
    native_safety = v2.safety_metrics(safety, baseline)
    if native_safety["failures"]:
        raise ValueError("Production rule regression mismatch")
    parts = {part: [row for row in rows if row["split"] == part] for part in PARTITIONS}
    best, selection = select_candidate(parts)
    model, temperature, threshold, calibration = (best[key] for key in ("model", "temperature", "threshold", "calibration"))
    test = parts["test"]
    labels, probabilities, reasons = predict(model, test, temperature, threshold)
    rules = project_read_labels([baseline[row["id"]]["intent"] for row in test])
    combined = project_read_labels(hybrid(test, labels, baseline))
    rules_score, candidate_score, combined_score = metrics(test, rules), metrics(test, labels), metrics(test, combined)
    safety_labels, _, _ = predict(model, safety, temperature, threshold)
    safety_indices = [index for index, row in enumerate(safety) if row["intent"] == "UNKNOWN" and row["case_kind"] != "typo"]
    safety_false_accepts = sum(safety_labels[index] != "UNKNOWN" for index in safety_indices)
    benchmark = score_review(reviewed_benchmark, model, temperature, threshold, baseline, output) if reviewed_benchmark else None
    adoption_score = benchmark["human_authored_challenges"]["rules_first"] if benchmark else combined_score
    comparison = benchmark["human_authored_challenges"]["rules"] if benchmark else rules_score
    gates = {
        "macro_f1_0_90": {"met": adoption_score["macro_f1"] >= .90 and (not benchmark or benchmark["rules_first"]["macro_f1"] >= .90),
                         "scope": "reviewed full set AND human challenges" if benchmark else "development only"},
        "held_out_gain": {"met": adoption_score["macro_f1"] > comparison["macro_f1"], "scope": "reviewed" if benchmark else "development only"},
        "sample_unknown_rejection": {"met": combined_score["unknown_false_accepts"] == 0 and safety_false_accepts == 0 and
                                     (not benchmark or benchmark["rules_first"]["unknown_false_accepts"] == 0)},
        "independent_evaluation": {"met": benchmark is not None, "reason": "User-reviewed plus human-authored challenge sample" if benchmark else "User annotation and independent challenge prompts pending"},
        "confidence_calibration": {"met": False, "reason": "Synthetic validation fit only; deployment calibration unverified"},
        "candidate_entities_outcomes_policy": {"met": False, "reason": "Intent-only development candidate; entity/outcome adapter intentionally awaits reviewed gates"},
        "android_parity_latency_ram_battery": {"met": False, "reason": "No Android adapter or representative-device measurements"},
    }
    report = {
        "dataset_version": manifest["version"], "seed": SEED, "data": manifest["data"], "labels": LABELS,
        "provenance": {"manifest_sha256": sha256(HERE / "manifest-v3.json"), "native_baseline_sha256": sha256(baseline_path),
                       "native_source_sha256": baseline_source_hash(ROOT), "python": platform.python_version(),
                       "scikit_learn": sklearn.__version__, "numpy": np.__version__, "source_hashes": source_hashes()},
        "candidate": {"name": "word TF-IDF 1-2 grams + logistic regression + conservative read eligibility",
                      "C": best["C"], "class_weight": "balanced", "selection_partition": "threshold validation only",
                      "threshold": threshold, "temperature": temperature, "features": len(model.named_steps["tfidf"].vocabulary_),
                      "packaged": False, "enabled": False, "entity_adapter": False},
        "validation_calibration": calibration,
        "validation_model_selection": selection,
        "development": {"rules": rules_score, "candidate": candidate_score, "rules_first": combined_score,
                        "eligibility_reasons": dict(Counter(reasons)),
                        "calibration_metrics": v2.calibration_metrics([row["intent"] for row in test], model.classes_, probabilities)},
        "candidate_safety": {"historical_strict_cases": len(safety_indices), "historical_false_accepts": safety_false_accepts,
                             "fallback_test_false_accepts": candidate_score["unknown_false_accepts"],
                             "rules_first_test_false_accepts": combined_score["unknown_false_accepts"],
                             "rules_preserved": all(guess == baseline[row["id"]]["intent"] for row, guess in zip(test, hybrid(test, labels, baseline))
                                                    if baseline[row["id"]]["intent"] != "UNKNOWN"),
                             "failures": [{"id": row["id"], "kind": row["case_kind"], "rule_intent": baseline[row["id"]]["intent"],
                                           "candidate": guess, "rules_first": joined}
                                          for row, guess, joined in zip(test, labels, combined) if row["intent"] == "UNKNOWN" and joined != "UNKNOWN"]},
        "native_safety_resolution": native_safety, "reviewed_benchmark": benchmark, "gates": gates,
        "decision": v2.adoption_decision(gates), "rules_retained": True,
        "scope_limit": manifest["evaluation_limit"],
    }
    report["adoption_eligible"] = report["decision"] == "GO"
    atomic_text(output, json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=ROOT / "app/build/reports/phase15/rules-v3.tsv")
    parser.add_argument("--output", type=Path, default=ROOT / "app/build/reports/phase15/results-v3.json")
    parser.add_argument("--reviewed-benchmark", type=Path)
    args = parser.parse_args()
    report = evaluate(args.baseline, args.output, args.reviewed_benchmark)
    print(json.dumps({"decision": report["decision"], "candidate": report["candidate"],
                      "development": {key: {metric: values[metric] for metric in ("macro_f1", "read_coverage", "unknown_false_accepts")}
                                      for key, values in report["development"].items() if key in {"rules", "candidate", "rules_first"}},
                      "candidate_safety": {key: value for key, value in report["candidate_safety"].items() if key != "failures"},
                      "gates": report["gates"]}, indent=2))


if __name__ == "__main__":
    main()
