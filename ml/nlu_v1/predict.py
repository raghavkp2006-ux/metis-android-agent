from __future__ import annotations

import argparse
import json
import math
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any


def load_model(path: str | Path) -> dict[str, Any]:
    with Path(path).open("r", encoding="utf-8") as source:
        model = json.load(source)
    valid_statuses = {
        "SMOKE TEST - NOT VALID FOR ADOPTION",
        "single frozen split, 1 test author, indicative only",
    }
    if model.get("status") not in valid_statuses:
        raise ValueError(
            "Model status is missing or does not match a recognized artifact status."
        )
    return model


def _word_tokens(text: str) -> list[str]:
    return re.findall(r"(?u)\b\w\w+\b", text.lower())


def _feature_counts(text: str, analyzer: str) -> Counter[str]:
    lowered = text.lower()
    if analyzer == "word":
        tokens = _word_tokens(lowered)
        features = list(tokens)
        features.extend(f"{tokens[i]} {tokens[i + 1]}" for i in range(len(tokens) - 1))
        return Counter(features)
    if analyzer == "char":
        padded = lowered
        return Counter(
            padded[i : i + size]
            for size in range(2, 6)
            for i in range(len(padded) - size + 1)
        )
    raise ValueError(f"Unknown analyzer: {analyzer}")


def _normalized_features(text: str, feature_data: dict[str, Any], analyzer: str) -> list[tuple[int, float]]:
    counts = _feature_counts(text, analyzer)
    vocabulary = feature_data["vocabulary"]
    idf = feature_data["idf"]
    values = [
        (vocabulary[term], float(count) * float(idf[vocabulary[term]]))
        for term, count in counts.items()
        if term in vocabulary
    ]
    values.sort()
    norm = math.sqrt(sum(value * value for _, value in values))
    if norm:
        values = [(index, value / norm) for index, value in values]
    return values


def predict_probabilities(text: str, model: dict[str, Any]) -> dict[str, float]:
    classes = model["classes"]
    scores = list(model["intercepts"])
    for analyzer in ("word", "char"):
        feature_data = model["features"][analyzer]
        for feature_index, value in _normalized_features(text, feature_data, analyzer):
            feature_weights = feature_data["weights"][feature_index]
            for class_index in range(len(classes)):
                scores[class_index] += value * feature_weights[class_index]
    maximum = max(scores)
    exponentials = [math.exp(score - maximum) for score in scores]
    denominator = sum(exponentials)
    return {
        label: probability / denominator
        for label, probability in zip(classes, exponentials)
    }


def predicted_label(
    probabilities: dict[str, float],
    classes: list[str],
    threshold: float,
    unknown_intent: str,
) -> str:
    best = max(classes, key=probabilities.__getitem__)
    return unknown_intent if probabilities[best] < threshold else best


def main() -> int:
    parser = argparse.ArgumentParser(description="Dependency-free METIS intent inference.")
    parser.add_argument("--model", default=str(Path(__file__).with_name("model.json")))
    parser.add_argument("--text", help="One input utterance.")
    parser.add_argument("--reference", help="Reference JSON to verify or evaluate.")
    parser.add_argument("--check-reference", action="store_true")
    args = parser.parse_args()
    model = load_model(args.model)
    if args.check_reference:
        if not args.reference:
            parser.error("--check-reference requires --reference")
        with Path(args.reference).open("r", encoding="utf-8") as source:
            reference = json.load(source)
        if reference.get("status") != model["status"]:
            raise ValueError("Reference and model status fields differ.")
        for index, pair in enumerate(reference["pairs"]):
            actual = predict_probabilities(pair["input"], model)
            if actual != pair["probabilities"]:
                raise ValueError(f"Reference probability mismatch at pair {index}.")
        print(f"{model['status']}: exact match for {len(reference['pairs'])} reference pairs")
        return 0
    if args.reference:
        with Path(args.reference).open("r", encoding="utf-8") as source:
            reference = json.load(source)
        output = [
            {
                "input": pair["input"],
                "probabilities": predict_probabilities(pair["input"], model),
            }
            for pair in reference["pairs"]
        ]
        print(json.dumps(output, ensure_ascii=False))
        return 0
    if args.text is None:
        parser.error("provide --text or --reference")
    probabilities = predict_probabilities(args.text, model)
    label = predicted_label(
        probabilities,
        model["classes"],
        float(model["threshold"]),
        model["unknown_intent"],
    )
    print(
        json.dumps(
            {
                "status": model["status"],
                "intent": label,
                "probabilities": probabilities,
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
