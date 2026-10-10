"""Score a contributed challenge without approving the full benchmark or tuning v3."""

import argparse
import json
from collections import Counter
from pathlib import Path

from dataset import HERE, LABELS, ROOT, source_hashes
from corpus import atomic_text, baseline_source_hash, normalized_text, read_csv, sha256
from candidate import hybrid, predict
from evaluate import metrics, project_read_labels, select_candidate, v2


def evaluate(challenge, baseline_path, output):
    manifest = json.loads((HERE / "manifest-v3.json").read_text(encoding="utf-8"))
    if source_hashes() != manifest["sources"]:
        raise ValueError("Candidate changed since v3 was frozen")
    for name, digest in manifest["hashes"].items():
        if sha256(HERE / name) != digest:
            raise ValueError("Development dataset changed")
    development = read_csv(HERE / "intent-v3.csv")
    parts = {part: [row for row in development if row["split"] == part]
             for part in ("train", "calibration", "threshold", "test")}
    # Reconstruct the already selected candidate before loading the challenge.
    best, _ = select_candidate(parts)
    rows = read_csv(challenge)
    input_path = challenge.with_suffix(".tsv")
    contribution = json.loads((challenge.parent / "user-challenge-manifest.json").read_text(encoding="utf-8"))
    if contribution["challenge_sha256"] != sha256(challenge) or contribution["input_sha256"] != sha256(input_path) or \
            contribution["candidate_manifest_sha256"] != sha256(HERE / "manifest-v3.json"):
        raise ValueError("Contributed challenge or frozen candidate changed")
    if Counter(row["intent"] for row in rows) != Counter({label: 5 for label in LABELS}):
        raise ValueError("Expected five contributed prompts per label")
    texts = {normalized_text(row["utterance"]) for row in rows}
    if len(texts) != 25 or texts & {normalized_text(row["utterance"]) for row in development}:
        raise ValueError("Duplicate or development-exposed challenge prompt")
    baseline = v2.load_baseline(baseline_path, rows, sha256(input_path), baseline_source_hash(ROOT))
    labels, probabilities, reasons = predict(best["model"], rows, best["temperature"], best["threshold"])
    rules = project_read_labels([baseline[row["id"]]["intent"] for row in rows])
    combined = project_read_labels(hybrid(rows, labels, baseline))
    report = {
        "scope": "25 user-contributed prompts; intent-only diagnostic, not full reviewed benchmark or adoption decision",
        "provenance": {"challenge_sha256": sha256(challenge), "native_baseline_sha256": sha256(baseline_path),
                       "native_source_sha256": baseline_source_hash(ROOT), "candidate_sources": source_hashes(),
                       "evaluator_sha256": sha256(Path(__file__))},
        "candidate": {"C": best["C"], "temperature": best["temperature"], "threshold": best["threshold"]},
        "rules": metrics(rows, rules), "learned": metrics(rows, labels), "rules_first": metrics(rows, combined),
        "rows": [{"id": row["id"], "utterance": row["utterance"], "expected_intent": row["intent"],
                  "expected_outcome": row["expected_outcome"], "native_intent": baseline[row["id"]]["intent"],
                  "native_status": baseline[row["id"]]["status"], "learned_intent": label, "rules_first_intent": joined,
                  "confidence": float(probability.max()), "reason": reason,
                  "correct_intent": joined == row["intent"]}
                 for row, label, joined, probability, reason in zip(rows, labels, combined, probabilities, reasons)],
        "full_review_complete": False, "adoption_eligible": False, "rules_retained": True,
        "limitations": "User supplied categories; entity/outcome annotations are assistant drafts. No entity/outcome adapter scored. Small exposed sample must not be reused for a blind claim after tuning.",
    }
    atomic_text(output, json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps({key: report[key] for key in ("rules", "learned", "rules_first", "full_review_complete")}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--challenge", type=Path, default=HERE / "user-challenge-2026-10-10.csv")
    parser.add_argument("--baseline", type=Path, default=ROOT / "app/build/reports/phase15/rules-user-challenge.tsv")
    parser.add_argument("--output", type=Path, default=ROOT / "app/build/reports/phase15/results-user-challenge.json")
    args = parser.parse_args()
    evaluate(args.challenge, args.baseline, args.output)
