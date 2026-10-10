#!/usr/bin/env python3
"""Reproducible intent experiment; evaluation success never implies ML adoption."""

from __future__ import annotations

import argparse
import base64
import csv
import json
import platform
import hashlib
from collections import Counter
from pathlib import Path

import numpy as np
import sklearn
from scipy.optimize import minimize_scalar
from scipy.special import logsumexp, softmax
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from sklearn.pipeline import Pipeline

from corpus import LABELS, atomic_text, baseline_source_hash, read_csv, sha256, validate

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
THRESHOLDS = tuple(value / 100 for value in range(90, 100))
REQUIRED_GATES = {"macro_f1_0_90", "held_out_gain", "sample_unknown_rejection", "independent_evaluation",
                  "confidence_calibration", "candidate_entities_outcomes_policy", "android_parity_latency_ram_battery"}


def adoption_decision(gates: dict) -> str:
    if set(gates) != REQUIRED_GATES:
        raise ValueError("Missing or unexpected adoption gates")
    return "GO" if all(gate["met"] is True for gate in gates.values()) else "NO_GO"


def load_baseline(path: Path, rows: list[dict[str, str]], input_hash: str, source_hash: str) -> dict:
    lines = path.read_text(encoding="utf-8").splitlines()
    metadata = dict(line[2:].split("=", 1) for line in lines if line.startswith("# "))
    if metadata.get("input_sha256") != input_hash or metadata.get("source_sha256") != source_hash:
        raise ValueError("Stale native baseline; rerun :app:phase15RulesSnapshot")
    reader = csv.DictReader((line for line in lines if not line.startswith("#")), delimiter="\t")
    baseline = {}
    for row in reader:
        if row["id"] in baseline:
            raise ValueError("Duplicate native baseline ID")
        baseline[row["id"]] = row
    if set(baseline) != {row["id"] for row in rows}:
        raise ValueError("Native baseline has missing or unexpected requests")
    return baseline


def native_entities(row: dict[str, str]) -> list[dict]:
    entities = []
    for field in filter(None, row["entities"].split(";")):
        kind, start, end, raw, normalized = field.split(":")
        decode = lambda value: base64.b64decode(value, validate=True).decode("utf-8")
        entities.append({"type": kind, "startOffset": int(start), "endOffset": int(end),
                         "rawValue": decode(raw), "normalizedValue": decode(normalized) or None})
    return entities


def safety_metrics(rows: list[dict[str, str]], baseline: dict) -> dict:
    intent_matches = outcome_matches = entity_matches = normalized_matches = normalized_count = 0
    failures = []
    expected_entities_count = produced_entities_count = matched_entities_count = 0
    for row in rows:
        actual = baseline[row["id"]]
        expected_entities, entities = json.loads(row["entities"]), native_entities(actual)
        intent_ok = actual["intent"] == row["intent"]
        status_ok = actual["status"] == row["expected_status"]
        entity_ok = entities == expected_entities
        intent_matches += intent_ok
        outcome_matches += status_ok
        entity_matches += entity_ok
        expected_entities_count += len(expected_entities)
        produced_entities_count += len(entities)
        span_key = lambda value: (value["type"], value["startOffset"], value["endOffset"], value["rawValue"])
        matched_entities_count += len({span_key(value) for value in expected_entities} &
                                      {span_key(value) for value in entities})
        for entity in expected_entities:
            if entity["type"] in {"TIME", "DURATION"}:
                normalized_count += 1
                normalized_matches += entity in entities
        no_effects = actual["proposals"] == "0" and actual["completed"] == "0"
        no_negated_reads = row["case_kind"] != "negation" or actual["reads"] == "0"
        if not all((intent_ok, status_ok, entity_ok, no_effects, no_negated_reads)):
            failures.append({"id": row["id"], "intent": intent_ok, "status": status_ok,
                             "entities": entity_ok, "no_effects": no_effects,
                             "negation_before_reads": no_negated_reads})
    return {"rows": len(rows), "categories": dict(sorted(Counter(row["case_kind"] for row in rows).items())),
            "intent_matches": intent_matches, "outcome_matches": outcome_matches,
            "entity_exact_matches": entity_matches,
            "entity_precision": matched_entities_count / produced_entities_count if produced_entities_count else 0.0,
            "entity_recall": matched_entities_count / expected_entities_count if expected_entities_count else 0.0,
            "time_duration_normalization_matches": normalized_matches,
            "time_duration_normalization_count": normalized_count,
            "failures": failures,
            "scope": "Production EnglishRules and the read-only languageAgentOrchestrator with fictional reads; not the action executor"}


def false_accepts(expected, predicted) -> int:
    return sum(actual == "UNKNOWN" and guess != "UNKNOWN" for actual, guess in zip(expected, predicted))


def score(expected, predicted) -> dict:
    known = [index for index, label in enumerate(expected) if label != "UNKNOWN"]
    report = classification_report(expected, predicted, labels=LABELS, output_dict=True, zero_division=0)
    return {"macro_f1": float(f1_score(expected, predicted, labels=LABELS, average="macro", zero_division=0)),
            "accuracy": float(accuracy_score(expected, predicted)),
            "unknown_false_accepts": false_accepts(expected, predicted),
            "unknown_count": sum(label == "UNKNOWN" for label in expected),
            "known_follow_up_rate": sum(predicted[index] == "UNKNOWN" for index in known) / len(known),
            "per_class": {label: report[label] for label in LABELS},
            "confusion": confusion_matrix(expected, predicted, labels=LABELS).tolist()}


def calibration_metrics(expected, classes, probabilities) -> dict:
    indices = probabilities.argmax(axis=1)
    confidence = probabilities.max(axis=1)
    correctness = np.array([classes[index] == label for index, label in zip(indices, expected)], dtype=float)
    buckets = np.minimum((confidence * 10).astype(int), 9)
    ece = sum(float(np.mean(buckets == bucket)) *
              abs(float(confidence[buckets == bucket].mean() - correctness[buckets == bucket].mean()))
              for bucket in range(10) if (buckets == bucket).any())
    targets = np.array([[float(label == candidate) for candidate in classes] for label in expected])
    return {"top_label_ece_10_bins": ece,
            "top_label_brier": float(np.mean((confidence - correctness) ** 2)),
            "multiclass_brier": float(np.mean(np.sum((probabilities - targets) ** 2, axis=1)))}


def predicted_labels(model, rows, threshold=None, temperature=1.0):
    logits = model.decision_function([row["utterance"] for row in rows])
    probabilities = softmax(logits / temperature, axis=1)
    labels = [str(model.classes_[index]) for index in probabilities.argmax(axis=1)]
    if threshold is not None:
        labels = [label if confidence >= threshold else "UNKNOWN"
                  for label, confidence in zip(labels, probabilities.max(axis=1))]
    return labels, probabilities


def partition_validation(rows):
    calibration_families = set()
    for label in LABELS:
        families = {row["family_id"] for row in rows if row["intent"] == label}
        if len(families) < 2:
            raise ValueError("Validation needs at least two families per label for calibration and threshold selection")
        ordered = sorted(families, key=lambda value: hashlib.sha256(f"calibration:{value}".encode()).hexdigest())
        calibration_families.add(ordered[0])
    calibration = [row for row in rows if row["family_id"] in calibration_families]
    threshold = [row for row in rows if row["family_id"] not in calibration_families]
    return calibration, threshold


def fit_temperature(model, rows):
    logits = model.decision_function([row["utterance"] for row in rows])
    targets = np.array([list(model.classes_).index(row["intent"]) for row in rows])
    def loss(log_temperature):
        scaled = logits / np.exp(log_temperature)
        return float(np.mean(logsumexp(scaled, axis=1) - scaled[np.arange(len(targets)), targets]))
    result = minimize_scalar(loss, bounds=(-4.0, 4.0), method="bounded")
    if not result.success:
        raise ValueError("Validation temperature calibration did not converge")
    return float(np.exp(result.x)), {"method": "scalar temperature fitted to validation negative log likelihood",
                                     "raw_nll": loss(0.0), "calibrated_nll": float(result.fun),
                                     "log_temperature_bounds": [-4.0, 4.0]}


def hybrid_labels(rows, labels, baseline):
    return [baseline[row["id"]]["intent"]
            if baseline[row["id"]]["negated"] == "true" or baseline[row["id"]]["intent"] != "UNKNOWN"
            else label for row, label in zip(rows, labels)]


def evaluate(baseline_path: Path, output_path: Path) -> dict:
    manifest_path = HERE / "manifest-v2.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for name, digest in manifest["hashes"].items():
        if sha256(HERE / name) != digest:
            raise ValueError(f"Corpus differs from its frozen manifest: {name}")
    rows, safety = read_csv(HERE / "intent-v2.csv"), read_csv(HERE / "safety-v2.csv")
    validate(rows)
    validate(safety, require_splits=False)
    source_hash = baseline_source_hash(ROOT)
    baseline = load_baseline(baseline_path, rows + safety, manifest["hashes"]["rules-input-v2.tsv"], source_hash)
    native_safety = safety_metrics(safety, baseline)
    if native_safety["failures"]:
        raise ValueError(f"Native safety/resolution fixture mismatch: {native_safety['failures']}")
    parts = {split: [row for row in rows if row["split"] == split] for split in ("train", "validation", "test")}
    model = Pipeline([
        ("tfidf", TfidfVectorizer(ngram_range=(1, 2), lowercase=True, strip_accents="unicode", sublinear_tf=True)),
        ("logreg", LogisticRegression(C=1.0, max_iter=2_000, random_state=1501)),
    ])
    model.fit([row["utterance"] for row in parts["train"]], [row["intent"] for row in parts["train"]])
    calibration_rows, threshold_rows = partition_validation(parts["validation"])
    temperature, calibration_fit = fit_temperature(model, calibration_rows)
    validation_expected = [row["intent"] for row in threshold_rows]
    threshold = None
    for candidate in THRESHOLDS:
        labels, _ = predicted_labels(model, threshold_rows, candidate, temperature)
        if false_accepts(validation_expected, labels) == 0:
            threshold = candidate
            break
    expected = [row["intent"] for row in parts["test"]]
    raw, probabilities = predicted_labels(model, parts["test"])
    accepted, calibrated_probabilities = predicted_labels(model, parts["test"], threshold, temperature) if threshold is not None else (
        ["UNKNOWN"] * len(expected), probabilities)
    hybrid = hybrid_labels(parts["test"], accepted, baseline)
    safety_raw, _ = predicted_labels(model, safety)
    safety_accepted, safety_probabilities = predicted_labels(model, safety, threshold, temperature) if threshold is not None else (
        ["UNKNOWN"] * len(safety), None)
    safety_hybrid = hybrid_labels(safety, safety_accepted, baseline)
    # Typo recognition is a possible language improvement, not an unsafe acceptance.
    # Those rows check the current rules regression and are reported separately.
    rejection_indices = [index for index, row in enumerate(safety)
                         if row["intent"] == "UNKNOWN" and row["case_kind"] != "typo"]
    rejection_false_accepts = sum(safety_hybrid[index] != "UNKNOWN" for index in rejection_indices)

    rules_score = score(expected, [baseline[row["id"]]["intent"] for row in parts["test"]])
    operational_score = score(expected, hybrid)
    report = {
        "dataset_version": manifest["version"], "seed": 1501,
        "provenance": {"manifest_sha256": sha256(manifest_path), "native_baseline_sha256": sha256(baseline_path),
                       "native_source_sha256": source_hash, "evaluator_sha256": sha256(Path(__file__)),
                       "python": platform.python_version(), "scikit_learn": sklearn.__version__,
                       "numpy": np.__version__},
        "rows": {split: len(values) for split, values in parts.items()},
        "families": {split: len({row["family_id"] for row in values}) for split, values in parts.items()},
        "per_class_rows": {split: dict(sorted(Counter(row["intent"] for row in values).items()))
                           for split, values in parts.items()},
        "candidate": {"name": "word TF-IDF (1-2 grams) + logistic regression", "C": 1.0, "max_iter": 2000,
                      "features": len(model.named_steps["tfidf"].vocabulary_), "threshold": threshold, "temperature": temperature,
                      "threshold_policy": "Validation only, minimum 0.90 required by LocalAgentOrchestrator; rules-first and negation guard",
                      "trained_on": "train only", "vocabulary_idf_on": "train only", "model_packaged": False},
        "labels": LABELS, "test": {"rules": rules_score, "ml_raw_argmax": score(expected, raw),
                                   "ml_at_threshold": score(expected, accepted), "rules_first_ml": operational_score},
        "validation_calibration": {**calibration_fit, "calibration_rows": len(calibration_rows),
                                   "threshold_rows": len(threshold_rows), "family_disjoint": True},
        "raw_probability_calibration": calibration_metrics(expected, model.classes_, probabilities),
        "calibrated_probability_metrics": calibration_metrics(expected, model.classes_, calibrated_probabilities),
        "native_safety_resolution": native_safety,
        "candidate_safety": {"rejection_cases": len(rejection_indices), "rules_first_false_accepts": rejection_false_accepts,
                             "raw_argmax_false_accepts": sum(safety_raw[index] != "UNKNOWN" for index in rejection_indices),
                             "false_accept_details": [{"id": safety[index]["id"], "kind": safety[index]["case_kind"],
                                                       "predicted": safety_hybrid[index],
                                                       "confidence": float(safety_probabilities[index].max())}
                                                      for index in rejection_indices if safety_hybrid[index] != "UNKNOWN"],
                             "noise_probes": [{"id": row["id"], "predicted": safety_hybrid[index]}
                                              for index, row in enumerate(safety) if row["case_kind"] == "typo"],
                             "rules_first_preserves_native_known_intents": all(
                                 guess == baseline[row["id"]]["intent"] for row, guess in zip(safety, safety_hybrid)
                                 if baseline[row["id"]]["intent"] != "UNKNOWN"),
                             "end_to_end_candidate_pipeline": "NOT_EVALUATED; no model-compatible entity extractor or Android adapter"},
        "gates": {
            "macro_f1_0_90": {"met": operational_score["macro_f1"] >= 0.90, "observed": operational_score["macro_f1"]},
            "held_out_gain": {"met": operational_score["macro_f1"] > rules_score["macro_f1"],
                              "observed": operational_score["macro_f1"] - rules_score["macro_f1"]},
            "sample_unknown_rejection": {"met": operational_score["unknown_false_accepts"] == 0 and rejection_false_accepts == 0,
                                         "scope": "Only this synthetic sample"},
            "independent_evaluation": {"met": False, "reason": manifest["evaluation_limit"]},
            "confidence_calibration": {"met": False, "reason": "Temperature fitted on synthetic validation only; deployment calibration remains unverified"},
            "candidate_entities_outcomes_policy": {"met": False, "reason": "No candidate adapter; native safety checks do not validate ML outcomes"},
            "android_parity_latency_ram_battery": {"met": False, "reason": "Not measured; candidate rejected before packaging"},
        },
        "scope_limit": "Corrected single-author synthetic experiment; no independent natural-language, device-cost, or model-policy passing claim",
    }
    report["decision"] = adoption_decision(report["gates"])
    report["adoption_eligible"] = report["decision"] == "GO"
    report["rules_retained"] = not report["adoption_eligible"]
    atomic_text(output_path, json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=ROOT / "app/build/reports/phase15/rules-v2.tsv")
    parser.add_argument("--output", type=Path, default=ROOT / "app/build/reports/phase15/results-v2.json")
    args = parser.parse_args()
    report = evaluate(args.baseline, args.output)
    print(json.dumps({"rows": report["rows"], "families": report["families"],
                      "threshold": report["candidate"]["threshold"],
                      "macro_f1": {name: values["macro_f1"] for name, values in report["test"].items()},
                      "native_safety": report["native_safety_resolution"], "candidate_safety": report["candidate_safety"],
                      "decision": report["decision"], "gates": report["gates"]}, indent=2))


if __name__ == "__main__":
    main()
