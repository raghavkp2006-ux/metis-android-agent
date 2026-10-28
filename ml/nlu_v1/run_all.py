from __future__ import annotations

import csv
import json
import math
import random
import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Any

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import accuracy_score, f1_score, precision_recall_fscore_support
from sklearn.model_selection import GroupKFold
from sklearn.linear_model import LogisticRegression

from predict import predict_probabilities, predicted_label


BASE_DIR = Path(__file__).resolve().parent
CONFIG_PATH = BASE_DIR / "config.yaml"
REPORT_PATH = BASE_DIR / "REPORT.md"
MODEL_PATH = BASE_DIR / "model.json"
REFERENCE_PATH = BASE_DIR / "reference_tests.json"
FROZEN_TEST_PATH = BASE_DIR / "test.csv"
STATUS = "single frozen split, 1 test author, indicative only"

TIER_1_INTENTS = {
    "COMPLETE_TASK",
    "DELETE_TASK",
    "POSTPONE_TASK",
    "PRIORITIZE_TASK",
    "RENAME_TASK",
}

TIER_2_INTENTS = {
    "CREATE_TASK",
    "CREATE_REMINDER",
    "CREATE_PROMISE",
    "REFLECTION",
}

READ_INTENTS = {
    "CHECK_CALENDAR",
    "CHECK_TASKS",
    "PERSON_COMMITMENTS",
    "PLAN_DAY",
    "PROJECT_STATUS",
    "WHAT_SHOULD_I_DO",
}

WRITE_INTENTS = {
    "COMPLETE_TASK",
    "CREATE_TASK",
    "DELETE_TASK",
    "RENAME_TASK",
    "PRIORITIZE_TASK",
    "POSTPONE_TASK",
    "CREATE_REMINDER",
    "CREATE_PROMISE",
}


def emit(message: str) -> None:
    print(f"{STATUS}: {message}")


def load_config() -> dict[str, Any]:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        required = {"text", "intent", "author", "supported_now"}
        missing = required.difference(reader.fieldnames or [])
        if missing:
            raise ValueError(f"CSV is missing required columns: {', '.join(sorted(missing))}")
        has_rules_predictions = "rules_pred" in (reader.fieldnames or [])
        has_style = "style" in (reader.fieldnames or [])
        rows = []
        for row in reader:
            if (row.get("supported_now") or "").strip().casefold() != "yes":
                continue
            text = (row.get("text") or "").strip()
            intent = (row.get("intent") or "").strip()
            author = (row.get("author") or "").strip()
            if not text or not intent or not author:
                raise ValueError("Supported rows must have non-empty text, intent, and author.")
            item = {"text": text, "intent": intent, "author": author}
            if has_style:
                item["style"] = (row.get("style") or "").strip()
            if has_rules_predictions:
                item["rules_pred"] = (row.get("rules_pred") or "").strip()
            rows.append(item)
    if not rows:
        raise ValueError(f"No supported rows found in {path}.")
    return rows


def class_rows(
    rows: list[dict[str, str]], config: dict[str, Any], threshold: int
) -> tuple[list[dict[str, str]], list[str], dict[str, int], list[str]]:
    counts = Counter(row["intent"] for row in rows)
    allowed = config["intent_set"]
    excluded = set(config.get("excluded_intents", []))
    unexpected = set(counts).difference(allowed).difference(excluded)
    if unexpected:
        raise ValueError(
            "CSV has labels not in config intent_set or excluded_intents: "
            + ", ".join(sorted(unexpected))
        )
    thin = [label for label in allowed if 0 < counts.get(label, 0) < threshold]
    labels = [label for label in allowed if counts.get(label, 0) >= threshold]
    if len(labels) < 2:
        raise ValueError("At least two configured intents must meet the thin-class threshold.")
    selected = [row for row in rows if row["intent"] in labels]
    absent = set(labels).difference(row["intent"] for row in selected)
    if absent:
        raise ValueError(f"Configured labels have no supported rows: {sorted(absent)}")
    return selected, labels, dict(counts), thin


def make_vectorizers() -> tuple[TfidfVectorizer, TfidfVectorizer]:
    word = TfidfVectorizer(
        analyzer="word",
        lowercase=True,
        ngram_range=(1, 2),
        token_pattern=r"(?u)\b\w\w+\b",
        dtype=np.float64,
    )
    char = TfidfVectorizer(
        analyzer="char",
        lowercase=True,
        ngram_range=(2, 5),
        dtype=np.float64,
    )
    return word, char


def fit_vectorizer_model(
    rows: list[dict[str, str]], classes: list[str], config: dict[str, Any]
) -> tuple[TfidfVectorizer, TfidfVectorizer, LogisticRegression]:
    texts = [row["text"] for row in rows]
    labels = [row["intent"] for row in rows]
    word, char = make_vectorizers()
    word_matrix = word.fit_transform(texts)
    char_matrix = char.fit_transform(texts)
    from scipy.sparse import hstack

    matrix = hstack((word_matrix, char_matrix), format="csr")
    classifier = LogisticRegression(
        C=float(config["model"]["c"]),
        class_weight="balanced",
        max_iter=int(config["model"]["max_iter"]),
        random_state=int(config["seed"]),
    )
    classifier.fit(matrix, labels)
    if list(classifier.classes_) != sorted(classes):
        raise ValueError(
            "Training split does not contain every active class. "
            "Check author distribution or the configured threshold."
        )
    return word, char, classifier


def transform(
    texts: list[str], word: TfidfVectorizer, char: TfidfVectorizer
) -> Any:
    from scipy.sparse import hstack

    return hstack((word.transform(texts), char.transform(texts)), format="csr")


def false_accepts_count(
    truth: list[str], predictions: list[str], target_intents: set[str]
) -> int:
    return sum(
        actual != predicted and predicted in target_intents
        for actual, predicted in zip(truth, predictions)
    )


def evaluate(
    truth: list[str],
    predictions: list[str],
    classes: list[str],
    tier_1: set[str] = TIER_1_INTENTS,
    tier_2: set[str] = TIER_2_INTENTS,
    write_intents: set[str] = WRITE_INTENTS,
) -> dict[str, float | int]:
    covered = [index for index, label in enumerate(predictions) if label != "UNKNOWN"]
    write_rows = [index for index, label in enumerate(truth) if label in write_intents]
    false_actions = false_accepts_count(truth, predictions, write_intents)
    t1_false = false_accepts_count(truth, predictions, tier_1)
    t2_false = false_accepts_count(truth, predictions, tier_2)
    return {
        "macro_f1": float(
            f1_score(truth, predictions, labels=classes, average="macro", zero_division=0)
        ),
        "accuracy": float(accuracy_score(truth, predictions)),
        "selective_accuracy": (
            float(accuracy_score([truth[i] for i in covered], [predictions[i] for i in covered]))
            if covered
            else 0.0
        ),
        "coverage": len(covered) / len(truth) if truth else 0.0,
        "write_coverage": (
            sum(predictions[i] != "UNKNOWN" for i in write_rows) / len(write_rows)
            if write_rows
            else 0.0
        ),
        "tier_1_false_accepts": t1_false,
        "tier_2_false_accepts": t2_false,
        "forbidden_write_false_accepts": t1_false,  # kept for backwards compat
        "false_actions": false_actions,
        "false_action_rate": false_actions / len(truth) if truth else 0.0,
    }


def per_intent_metrics(
    truth: list[str],
    predictions: list[str],
    classes: list[str],
) -> list[dict[str, Any]]:
    precision, recall, f1, support = precision_recall_fscore_support(
        truth, predictions, labels=classes, zero_division=0
    )
    return [
        {
            "intent": label,
            "precision": float(precision[i]),
            "recall": float(recall[i]),
            "f1": float(f1[i]),
            "support": int(support[i]),
        }
        for i, label in enumerate(classes)
    ]


def threshold_grid_values(config: dict[str, Any]) -> list[float]:
    settings = config.get("validation_threshold_grid", {"start": 0.05, "stop": 0.95, "step": 0.05})
    start = float(settings["start"])
    stop = float(settings["stop"])
    step = float(settings["step"])
    count = int(round((stop - start) / step)) + 1
    return [round(start + index * step, 4) for index in range(count)]


def score_predictions(
    truth: list[str],
    probabilities: list[dict[str, float]],
    classes: list[str],
    threshold: float,
    config: dict[str, Any],
) -> tuple[dict[str, float | int], list[str]]:
    predictions = [
        predicted_label(p, classes, threshold, config["unknown_intent"])
        for p in probabilities
    ]
    return (
        evaluate(truth, predictions, classes, TIER_1_INTENTS, TIER_2_INTENTS, WRITE_INTENTS),
        predictions,
    )


def compute_threshold_grid(
    truth: list[str],
    probabilities: list[dict[str, float]],
    classes: list[str],
    config: dict[str, Any],
) -> list[dict[str, float | int]]:
    rows = []
    for threshold in threshold_grid_values(config):
        metrics, _ = score_predictions(truth, probabilities, classes, threshold, config)
        rows.append({"threshold": threshold, **metrics})
    return rows


def fixed_author_split(
    rows: list[dict[str, str]], config: dict[str, Any]
) -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    split_config = config["group_split"]
    test_authors = set(split_config["test_authors"])
    val_authors = set(split_config["validation_authors"])
    train_authors = set(split_config["train_authors"])

    train_rows = [row for row in rows if row["author"] in train_authors]
    val_rows = [row for row in rows if row["author"] in val_authors]
    test_rows = [row for row in rows if row["author"] in test_authors]

    # Note: test.csv is preserved and stays completely unopened in Phase A.
    return train_rows, val_rows, test_rows


def grouped_cv_validation(
    train_rows: list[dict[str, str]],
    classes: list[str],
    config: dict[str, Any],
) -> tuple[list[str], list[dict[str, float]]]:
    authors = [row["author"] for row in train_rows]
    unique_authors = sorted(set(authors))
    n_groups = len(unique_authors)
    emit(f"Grouped CV: {n_groups} author groups: {unique_authors}")

    splitter = GroupKFold(n_splits=n_groups)
    texts = [row["text"] for row in train_rows]
    labels = [row["intent"] for row in train_rows]
    groups = authors

    oof_truth: list[str] = [""] * len(train_rows)
    oof_probabilities: list[dict[str, float] | None] = [None] * len(train_rows)

    for fold_idx, (tr_ids, v_ids) in enumerate(splitter.split(texts, labels, groups)):
        fold_train = [train_rows[i] for i in tr_ids]
        fold_val = [train_rows[i] for i in v_ids]
        fold_author = {row["author"] for row in fold_val}
        emit(f"  CV fold {fold_idx}: held-out author(s)={sorted(fold_author)}, "
             f"train={len(fold_train)}, val={len(fold_val)}")

        word, char, clf = fit_vectorizer_model(fold_train, classes, config)
        matrix = transform([row["text"] for row in fold_val], word, char)
        raw = clf.predict_proba(matrix)
        for i, val_idx in enumerate(v_ids):
            oof_truth[val_idx] = fold_val[i]["intent"]
            oof_probabilities[val_idx] = {
                label: float(raw[i, j]) for j, label in enumerate(clf.classes_)
            }

    assert all(t != "" for t in oof_truth)
    assert all(p is not None for p in oof_probabilities)
    return oof_truth, oof_probabilities  # type: ignore


def out_of_scope_analysis(
    truth: list[str],
    probabilities: list[dict[str, float]],
    classes: list[str],
    threshold: float,
    unknown_intent: str = "UNKNOWN",
) -> dict[str, Any]:
    unknown_indices = [i for i, label in enumerate(truth) if label == unknown_intent]
    total_unknown = len(unknown_indices)
    if total_unknown == 0:
        return {
            "total_unknown": 0,
            "accepted": 0,
            "accepted_rate": 0.0,
            "tier_1": 0,
            "tier_1_rate": 0.0,
            "tier_2": 0,
            "tier_2_rate": 0.0,
            "read": 0,
            "read_rate": 0.0,
        }

    accepted_t1 = 0
    accepted_t2 = 0
    accepted_read = 0
    total_accepted = 0

    for idx in unknown_indices:
        p = probabilities[idx]
        best_cls = max(classes, key=p.__getitem__)
        if p[best_cls] >= threshold and best_cls != unknown_intent:
            total_accepted += 1
            if best_cls in TIER_1_INTENTS:
                accepted_t1 += 1
            elif best_cls in TIER_2_INTENTS:
                accepted_t2 += 1
            elif best_cls in READ_INTENTS:
                accepted_read += 1

    return {
        "total_unknown": total_unknown,
        "accepted": total_accepted,
        "accepted_rate": total_accepted / total_unknown,
        "tier_1": accepted_t1,
        "tier_1_rate": accepted_t1 / total_unknown,
        "tier_2": accepted_t2,
        "tier_2_rate": accepted_t2 / total_unknown,
        "read": accepted_read,
        "read_rate": accepted_read / total_unknown,
    }


def style_subset_metrics(
    rows: list[dict[str, str]],
    predictions: list[str],
    classes: list[str],
    style: str,
    all_truth: list[str],
) -> dict[str, Any] | None:
    indices = [i for i, r in enumerate(rows) if r.get("style", "").lower() == style.lower()]
    if not indices:
        return None
    subset_truth = [all_truth[i] for i in indices]
    subset_predictions = [predictions[i] for i in indices]
    metrics = evaluate(subset_truth, subset_predictions, classes, TIER_1_INTENTS, TIER_2_INTENTS, WRITE_INTENTS)
    metrics["count"] = len(indices)
    return metrics


def check_export_parity(
    rows: list[dict[str, str]],
    word: TfidfVectorizer,
    char: TfidfVectorizer,
    classifier: LogisticRegression,
    model: dict[str, Any],
    seed: int,
) -> None:
    probes = reference_texts(rows, seed)
    sklearn_probabilities = classifier.predict_proba(transform(probes, word, char))
    worst = (0.0, "", 0.0, 0.0)
    for row_index, text in enumerate(probes):
        exported = predict_probabilities(text, model)
        for class_index, label in enumerate(classifier.classes_):
            difference = abs(exported[label] - float(sklearn_probabilities[row_index, class_index]))
            if difference > worst[0]:
                worst = (
                    difference,
                    label,
                    exported[label],
                    float(sklearn_probabilities[row_index, class_index]),
                )
    if worst[0] > 1e-6:
        raise RuntimeError(
            "Dependency-free predictor differs from scikit-learn by "
            f"{worst[0]:.9g} for class {worst[1]} "
            f"(exported={worst[2]:.9g}, sklearn={worst[3]:.9g})."
        )


def export_model(
    word: TfidfVectorizer,
    char: TfidfVectorizer,
    classifier: LogisticRegression,
    classes: list[str],
    threshold: float,
    config: dict[str, Any],
) -> dict[str, Any]:
    classes_sorted = list(classifier.classes_)
    coefficient_matrix = classifier.coef_
    word_size = len(word.vocabulary_)

    def vectorizer_json(
        vocabulary: dict[str, int], idf: np.ndarray, start: int, size: int
    ) -> dict[str, Any]:
        return {
            "vocabulary": vocabulary,
            "idf": [float(value) for value in idf],
            "weights": [
                [float(coefficient_matrix[class_id, start + feature_id]) for class_id in range(len(classes_sorted))]
                for feature_id in range(size)
            ],
        }

    return {
        "status": STATUS,
        "classes": classes_sorted,
        "intercepts": [float(value) for value in classifier.intercept_],
        "threshold": float(threshold),
        "unknown_intent": config["unknown_intent"],
        "forbidden_write_intents": list(sorted(TIER_1_INTENTS)),
        "tier_1_intents": list(sorted(TIER_1_INTENTS)),
        "tier_2_intents": list(sorted(TIER_2_INTENTS)),
        "read_intents": list(sorted(READ_INTENTS)),
        "tokenizer_spec": {
            "lowercase": True,
            "word_analyzer": "word",
            "word_token_pattern": r"(?u)\b\w\w+\b",
            "word_ngram_range": [1, 2],
            "char_analyzer": "char",
            "char_padding": "none",
            "char_ngram_range": [2, 5],
            "tf": "raw count",
            "idf": "smooth idf: log((1 + n_documents) / (1 + document_frequency)) + 1",
            "normalization": "L2 independently per analyzer",
            "probability": "multiclass softmax over linear scores",
        },
        "features": {
            "word": vectorizer_json(word.vocabulary_, word.idf_, 0, word_size),
            "char": vectorizer_json(
                char.vocabulary_, char.idf_, word_size, len(char.vocabulary_)
            ),
        },
    }


def reference_texts(rows: list[dict[str, str]], seed: int) -> list[str]:
    choices = list(dict.fromkeys(row["text"] for row in rows))
    if len(choices) < 50:
        raise ValueError(f"Reference verification needs 50 unique texts; found {len(choices)}.")
    return random.Random(seed).sample(choices, 50)


def write_phase_a_report(
    eval_data: dict[str, Any], config: dict[str, Any]
) -> None:
    ml = eval_data["ml_vn"]
    sensitivity_ml = eval_data["sensitivity_ml_vn"]
    warning = (
        "\n> WARNING: zero false accepts is vacuous at this coverage\n"
        if ml["coverage"] < 0.10
        else ""
    )

    # Main metrics table
    metrics_row = "| ML-only validation (VN, operating t={:.2f}) | {:.6f} | {:.2%} | {:.6f} | {:.2%} | {:.2%} | {} | {} | {} ({:.2%}) |".format(
        eval_data["operating_threshold"],
        ml["accuracy"],
        ml["selective_accuracy"],
        ml["macro_f1"],
        ml["coverage"],
        ml["write_coverage"],
        int(ml["tier_1_false_accepts"]),
        int(ml["tier_2_false_accepts"]),
        int(ml["false_actions"]),
        ml["false_action_rate"],
    )

    sensitivity_row = "| ML-only validation (VN, sensitivity t={:.2f}) | {:.6f} | {:.2%} | {:.6f} | {:.2%} | {:.2%} | {} | {} | {} ({:.2%}) |".format(
        eval_data["vn_sensitivity_threshold"],
        sensitivity_ml["accuracy"],
        sensitivity_ml["selective_accuracy"],
        sensitivity_ml["macro_f1"],
        sensitivity_ml["coverage"],
        sensitivity_ml["write_coverage"],
        int(sensitivity_ml["tier_1_false_accepts"]),
        int(sensitivity_ml["tier_2_false_accepts"]),
        int(sensitivity_ml["false_actions"]),
        sensitivity_ml["false_action_rate"],
    )

    # Per-intent table
    per_intent_rows = []
    for item in eval_data["per_intent"]:
        per_intent_rows.append(
            "| {} | {:.4f} | {:.4f} | {:.4f} | {} |".format(
                item["intent"],
                item["precision"],
                item["recall"],
                item["f1"],
                item["support"],
            )
        )

    # Threshold grid
    grid_rows = []
    for row in eval_data["validation_grid"]:
        marker = " **[Operating Point]**" if abs(row["threshold"] - eval_data["operating_threshold"]) < 1e-4 else (
            " *[VN Sensitivity]*" if abs(row["threshold"] - eval_data["vn_sensitivity_threshold"]) < 1e-4 else ""
        )
        grid_rows.append(
            "| {:.2f}{} | {:.2%} | {:.2%} | {:.2%} | {} | {} | {} |".format(
                float(row["threshold"]),
                marker,
                float(row["coverage"]),
                float(row["write_coverage"]),
                float(row["selective_accuracy"]),
                int(row["tier_1_false_accepts"]),
                int(row["tier_2_false_accepts"]),
                int(row["false_actions"]),
            )
        )

    # Style subsets
    style_rows = []
    for style, m in eval_data["style_metrics"].items():
        style_rows.append(
            "| {} | {} | {:.6f} | {:.2%} | {:.6f} | {:.2%} | {:.2%} | {} | {} | {} ({:.2%}) |".format(
                style,
                m["count"],
                m["accuracy"],
                m["selective_accuracy"],
                m["macro_f1"],
                m["coverage"],
                m["write_coverage"],
                int(m["tier_1_false_accepts"]),
                int(m["tier_2_false_accepts"]),
                int(m["false_actions"]),
                m["false_action_rate"],
            )
        )

    # Out-of-scope check
    oos_vn = eval_data["oos_vn"]
    oos_oof = eval_data["oos_oof"]
    oos_pooled = eval_data["oos_pooled"]

    content = f"""# {STATUS}

validation only, test not run

Split: train = AK, PR, DJ, Raghav ({eval_data['train_rows']} rows); validation = VN ({eval_data['val_rows']} rows); test = SM ({eval_data['test_rows']} rows, frozen, unopened, not scored).
{warning}
Evaluation: author-held-out validation on VN; reject threshold chosen from pooled train grouped-CV OOF and VN predictions on a 0.05-step grid (lowest with zero Tier 1 false accepts + 0.05 margin). Test author SM is frozen and will not be scored until Phase B.

## Validation metrics (VN, author-held-out)

| System | Accuracy | Selective accuracy | Macro-F1 | Coverage | WRITE coverage | Tier 1 false accepts | Tier 2 false accepts | False actions (count; rate) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
{metrics_row}
{sensitivity_row}
| Rules-only | - | - | - | - | - | rules_pred empty | rules_pred empty | rules_pred empty |
| Hybrid (rules first) | - | - | - | - | - | rules_pred empty | rules_pred empty | rules_pred empty |

Operating reject threshold: **{eval_data['operating_threshold']:.2f}** (selected by pooling train OOF and VN predictions; lowest zero Tier 1 threshold was {eval_data['pooled_lowest_zero_t1']:.2f} + 0.05 margin).
VN-only sensitivity threshold: **{eval_data['vn_sensitivity_threshold']:.2f}** (lowest zero Tier 1 threshold on VN alone was {eval_data['vn_lowest_zero_t1']:.2f} + 0.05 margin; shown for sensitivity only, not used as operating point).

- **Tier 1 (zero tolerance):** COMPLETE_TASK, DELETE_TASK, POSTPONE_TASK, PRIORITIZE_TASK, RENAME_TASK
- **Tier 2:** CREATE_TASK, CREATE_REMINDER, CREATE_PROMISE, REFLECTION
- **False actions:** incorrect predictions into any of the 8 WRITE intents (Tier 1 + CREATE_TASK, CREATE_REMINDER, CREATE_PROMISE).
- Coverage is fraction not rejected as UNKNOWN. WRITE coverage is measured over ground-truth WRITE intents. Selective accuracy is measured on covered predictions.

rules_pred empty — rules predictions have not been filled yet. Rules-only and hybrid evaluation deferred to Phase B.

## Out-of-Scope (True UNKNOWN) Evaluation at Operating Threshold ({eval_data['operating_threshold']:.2f})

| Subset | True UNKNOWN Total | Accepted as non-UNKNOWN | Tier 1 False Accepts | Tier 2 False Accepts | READ False Accepts |
|---|---:|---:|---:|---:|---:|
| **Validation (VN)** | {oos_vn['total_unknown']} | {oos_vn['accepted']} ({oos_vn['accepted_rate']:.2%}) | {oos_vn['tier_1']} ({oos_vn['tier_1_rate']:.2%}) | {oos_vn['tier_2']} ({oos_vn['tier_2_rate']:.2%}) | {oos_vn['read']} ({oos_vn['read_rate']:.2%}) |
| **Train Grouped-CV OOF** | {oos_oof['total_unknown']} | {oos_oof['accepted']} ({oos_oof['accepted_rate']:.2%}) | {oos_oof['tier_1']} ({oos_oof['tier_1_rate']:.2%}) | {oos_oof['tier_2']} ({oos_oof['tier_2_rate']:.2%}) | {oos_oof['read']} ({oos_oof['read_rate']:.2%}) |
| **Pooled (OOF + VN)** | {oos_pooled['total_unknown']} | {oos_pooled['accepted']} ({oos_pooled['accepted_rate']:.2%}) | {oos_pooled['tier_1']} ({oos_pooled['tier_1_rate']:.2%}) | {oos_pooled['tier_2']} ({oos_pooled['tier_2_rate']:.2%}) | {oos_pooled['read']} ({oos_pooled['read_rate']:.2%}) |

## Per-intent precision / recall (VN at Operating Threshold {eval_data['operating_threshold']:.2f})

| Intent | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
{chr(10).join(per_intent_rows)}

## Validation coverage versus threshold grid (VN)

| Threshold | Coverage | WRITE coverage | Selective accuracy | Tier 1 false accepts | Tier 2 false accepts | False actions |
|---:|---:|---:|---:|---:|---:|---:|
{chr(10).join(grid_rows)}

## Style-subset metrics (VN at Operating Threshold {eval_data['operating_threshold']:.2f})

| Style | Count | Accuracy | Selective accuracy | Macro-F1 | Coverage | WRITE coverage | Tier 1 false accepts | Tier 2 false accepts | False actions (count; rate) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
{chr(10).join(style_rows)}

## Grouped CV (leave-one-author-out) within train authors (477 rows)

| Metric | Value |
|---|---:|
| Raw OOF Accuracy (argmax) | {eval_data['oof_raw_accuracy']:.4f} |
| Raw OOF Macro-F1 (argmax) | {eval_data['oof_raw_macro_f1']:.4f} |
| OOF Accuracy at Operating Threshold ({eval_data['operating_threshold']:.2f}) | {eval_data['oof_metrics']['accuracy']:.6f} |
| OOF Selective Accuracy | {eval_data['oof_metrics']['selective_accuracy']:.2%} |
| OOF Macro-F1 | {eval_data['oof_metrics']['macro_f1']:.6f} |
| OOF Coverage | {eval_data['oof_metrics']['coverage']:.2%} |
| OOF WRITE Coverage | {eval_data['oof_metrics']['write_coverage']:.2%} |
| OOF Tier 1 False Accepts | {int(eval_data['oof_metrics']['tier_1_false_accepts'])} |
| OOF Tier 2 False Accepts | {int(eval_data['oof_metrics']['tier_2_false_accepts'])} |
| OOF False Actions | {int(eval_data['oof_metrics']['false_actions'])} ({eval_data['oof_metrics']['false_action_rate']:.2%}) |

## Limitations

- {STATUS}: single frozen split with 1 test author; results are indicative, not definitive.
- The reject threshold was determined by pooling train grouped-CV OOF and VN predictions on a 0.05 grid; zero Tier 1 false accepts is a calibration constraint, not an absolute guarantee.
- The test set (SM, 128 rows) is frozen in `test.csv` and remains completely unopened and unscored during Phase A.
- The fixed logistic-regression configuration is not authorization. Any action requires the existing rules and policy engine.
- Phase B (frozen test on SM) is deferred until rules_pred is filled and explicit instruction is given.
"""
    REPORT_PATH.write_text(content, encoding="utf-8")


def main() -> None:
    config = load_config()
    data_path = (BASE_DIR / config["data_path"]).resolve()
    source_rows = read_rows(data_path)
    author_count = len({row["author"] for row in source_rows})
    threshold = int(config["thin_class_thresholds_by_split"]["multi_author_group_split"])
    rows, classes, all_counts, thin_intents = class_rows(source_rows, config, threshold)

    emit(f"authors={author_count}; supported_rows={len(source_rows)}")
    emit(f"thin_class_threshold={threshold}; included_intents={len(classes)}")
    if thin_intents:
        emit(f"THIN intents excluded (< {threshold} rows): {', '.join(thin_intents)}")
    else:
        emit(f"No intents excluded as THIN (all >= {threshold} rows)")
    for label in classes:
        emit(f"intent={label}; supported_rows={all_counts.get(label, 0)}; included")

    train_rows, val_rows, test_rows = fixed_author_split(rows, config)
    emit(f"Train: {len(train_rows)} rows, authors={sorted(set(r['author'] for r in train_rows))}")
    emit(f"Validation: {len(val_rows)} rows, authors={sorted(set(r['author'] for r in val_rows))}")
    emit(f"Test: {len(test_rows)} rows (frozen, unopened, NOT scored in Phase A)")

    # 1. Grouped CV within train authors
    emit("--- Running grouped CV within train authors ---")
    oof_truth, oof_probabilities = grouped_cv_validation(train_rows, classes, config)
    raw_oof_preds = [max(classes, key=p.__getitem__) for p in oof_probabilities]
    oof_raw_acc = float(accuracy_score(oof_truth, raw_oof_preds))
    oof_raw_f1 = float(f1_score(oof_truth, raw_oof_preds, labels=classes, average="macro", zero_division=0))
    emit(f"Train Grouped CV Raw OOF: accuracy={oof_raw_acc:.4f}, macro_f1={oof_raw_f1:.4f}")

    # 2. Fit model on all train authors, evaluate on VN
    emit("--- Fitting model on all train authors ---")
    word, char, classifier = fit_vectorizer_model(train_rows, classes, config)
    val_matrix = transform([r["text"] for r in val_rows], word, char)
    val_raw = classifier.predict_proba(val_matrix)
    val_probabilities = [
        {label: float(val_raw[i, j]) for j, label in enumerate(classifier.classes_)}
        for i in range(len(val_rows))
    ]
    val_truth = [r["intent"] for r in val_rows]

    # 3. Pooled threshold selection (train OOF + VN) on 0.05 grid
    pooled_truth = oof_truth + val_truth
    pooled_probabilities = oof_probabilities + val_probabilities
    grid_steps = threshold_grid_values(config)

    pooled_lowest_zero_t1 = None
    for t in grid_steps:
        m, _ = score_predictions(pooled_truth, pooled_probabilities, classes, t, config)
        if m["tier_1_false_accepts"] == 0 and pooled_lowest_zero_t1 is None:
            pooled_lowest_zero_t1 = t

    if pooled_lowest_zero_t1 is None:
        raise ValueError("Could not achieve 0 Tier 1 false accepts on pooled set across grid.")

    operating_threshold = round(pooled_lowest_zero_t1 + 0.05, 4)
    emit(f"Pooled grid: lowest threshold with zero Tier 1 false accepts = {pooled_lowest_zero_t1:.2f}")
    emit(f"Operating reject threshold (lowest + 0.05 margin) = {operating_threshold:.2f}")

    # VN-only sensitivity threshold
    vn_lowest_zero_t1 = None
    for t in grid_steps:
        m, _ = score_predictions(val_truth, val_probabilities, classes, t, config)
        if m["tier_1_false_accepts"] == 0 and vn_lowest_zero_t1 is None:
            vn_lowest_zero_t1 = t
    vn_sensitivity_threshold = round(vn_lowest_zero_t1 + 0.05, 4)
    emit(f"VN-only sensitivity threshold = {vn_sensitivity_threshold:.2f} (lowest was {vn_lowest_zero_t1:.2f})")

    # Score VN at operating threshold
    ml_vn, val_predictions = score_predictions(
        val_truth, val_probabilities, classes, operating_threshold, config
    )
    # Score VN at sensitivity threshold
    sensitivity_ml_vn, _ = score_predictions(
        val_truth, val_probabilities, classes, vn_sensitivity_threshold, config
    )

    # Score OOF at operating threshold
    oof_metrics, _ = score_predictions(
        oof_truth, oof_probabilities, classes, operating_threshold, config
    )

    # Out-of-scope analysis at operating threshold
    oos_vn = out_of_scope_analysis(val_truth, val_probabilities, classes, operating_threshold, config["unknown_intent"])
    oos_oof = out_of_scope_analysis(oof_truth, oof_probabilities, classes, operating_threshold, config["unknown_intent"])
    oos_pooled = out_of_scope_analysis(pooled_truth, pooled_probabilities, classes, operating_threshold, config["unknown_intent"])

    # Per-intent metrics on VN
    per_intent = per_intent_metrics(val_truth, val_predictions, classes)

    # Validation threshold grid
    val_grid = compute_threshold_grid(val_truth, val_probabilities, classes, config)

    # Style subsets on VN
    style_metrics = {}
    for style in ("hinglish", "typo", "voice"):
        sm = style_subset_metrics(val_rows, val_predictions, classes, style, val_truth)
        if sm is not None:
            style_metrics[style] = sm

    if ml_vn["coverage"] < 0.10:
        emit("WARNING: zero false accepts is vacuous at this coverage")
    if ml_vn["coverage"] < 0.05:
        emit("WARNING: automatically selected threshold leaves ML coverage below 5%")

    # Export model.json
    deployment = export_model(word, char, classifier, classes, operating_threshold, config)
    check_export_parity(train_rows, word, char, classifier, deployment, int(config["seed"]))

    deployment["mode"] = "multi-author-frozen-author-split"
    deployment["seed"] = int(config["seed"])
    deployment["intent_set"] = classes
    deployment["thin_class_threshold"] = threshold
    MODEL_PATH.write_text(
        json.dumps(deployment, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    # Reference pairs
    reference = [
        {
            "input": text,
            "probabilities": predict_probabilities(text, deployment),
        }
        for text in reference_texts(rows, int(config["seed"]))
    ]
    reference_file = {"status": STATUS, "pairs": reference}
    REFERENCE_PATH.write_text(
        json.dumps(reference_file, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    # Verify predict.py against reference
    verify = subprocess.run(
        [
            sys.executable,
            str(BASE_DIR / "predict.py"),
            "--model",
            str(MODEL_PATH),
            "--reference",
            str(REFERENCE_PATH),
            "--check-reference",
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    if verify.returncode != 0:
        raise RuntimeError(
            "predict.py did not reproduce reference_tests.json: "
            + (verify.stderr.strip() or verify.stdout.strip())
        )

    eval_data = {
        "operating_threshold": operating_threshold,
        "pooled_lowest_zero_t1": pooled_lowest_zero_t1,
        "vn_lowest_zero_t1": vn_lowest_zero_t1,
        "vn_sensitivity_threshold": vn_sensitivity_threshold,
        "ml_vn": ml_vn,
        "sensitivity_ml_vn": sensitivity_ml_vn,
        "oof_metrics": oof_metrics,
        "oof_raw_accuracy": oof_raw_acc,
        "oof_raw_macro_f1": oof_raw_f1,
        "oos_vn": oos_vn,
        "oos_oof": oos_oof,
        "oos_pooled": oos_pooled,
        "per_intent": per_intent,
        "validation_grid": val_grid,
        "style_metrics": style_metrics,
        "train_rows": len(train_rows),
        "val_rows": len(val_rows),
        "test_rows": len(test_rows),
    }

    write_phase_a_report(eval_data, config)

    emit(
        "VN Operating Metrics: accuracy={:.6f}; selective accuracy={:.6f}; macro-F1={:.6f}; "
        "coverage={:.2%}; WRITE coverage={:.2%}; Tier 1 false accepts={}; Tier 2 false accepts={}; "
        "false actions={} ({:.2%})".format(
            ml_vn["accuracy"],
            ml_vn["selective_accuracy"],
            ml_vn["macro_f1"],
            ml_vn["coverage"],
            ml_vn["write_coverage"],
            ml_vn["tier_1_false_accepts"],
            ml_vn["tier_2_false_accepts"],
            ml_vn["false_actions"],
            ml_vn["false_action_rate"],
        )
    )
    emit(f"reference_pairs={len(reference)} verified exactly with predict.py")
    emit("wrote model.json, predict.py, reference_tests.json, REPORT.md")
    emit("Phase A complete. test.csv (SM) stays unopened. Awaiting Phase B instruction.")


if __name__ == "__main__":
    main()
