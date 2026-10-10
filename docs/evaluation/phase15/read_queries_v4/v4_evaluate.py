"""Evaluate v4 on development families, exposed regressions, and actual JVM rules."""

import argparse
import importlib.util
import json
import math
import re
from collections import Counter
from pathlib import Path

import numpy as np
from threadpoolctl import threadpool_limits
from sklearn.metrics import f1_score

from v4_data import HERE, V3, ROOT, LABELS, PARTITIONS, READ_LABELS, sources, validate
from corpus import atomic_text, baseline_source_hash, normalized_text, read_csv, sha256
from v4_language import eligibility, extract, feature_text, hybrid, predict, resolve, train

spec = importlib.util.spec_from_file_location("phase15_v3_metrics", V3/"evaluate.py")
v3 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v3)
v2 = v3.v2


def select(parts):
    if set(parts) != set(PARTITIONS) or any(row["split"] != part for part, values in parts.items() for row in values):
        raise ValueError("Invalid training/calibration/selection separation")
    candidates = []
    for characters in (False, True):
        for regularization in (1., 4., 16.):
            with threadpool_limits(limits=1):
                model = train(parts["train"], regularization, characters)
            eligible_calibration = [row for row in parts["calibration"] if eligibility(row["utterance"],row["input_source"]) is None]
            if {row["intent"] for row in eligible_calibration} != set(LABELS):
                raise ValueError("Eligible calibration must cover all labels")
            temperature, calibration = v2.fit_temperature(model, eligible_calibration)
            calibration["eligible_rows"] = len(eligible_calibration)
            cutoffs = []
            for threshold in v2.THRESHOLDS:
                labels, _, _ = predict(model, parts["threshold"], temperature, threshold)
                score = v3.metrics(parts["threshold"], labels)
                if score["unknown_false_accepts"] == 0 and score["accepted_count"]:
                    cutoffs.append((score["macro_f1"], score["read_coverage"], -threshold, threshold))
            threshold = max(cutoffs)[-1] if cutoffs else None
            labels, _, _ = predict(model, parts["threshold"], temperature, threshold)
            candidates.append(dict(model=model, characters=characters, C=regularization, temperature=temperature,
                                   calibration=calibration, threshold=threshold, validation=v3.metrics(parts["threshold"], labels)))
            print(json.dumps({"selection": "validation only", "characters": characters, "C": regularization,
                              "threshold": threshold, "f1": candidates[-1]["validation"]["macro_f1"]}), flush=True)
    best = max(candidates, key=lambda value: (value["validation"]["macro_f1"], value["validation"]["read_coverage"],
                                             not value["characters"], -value["C"]))
    return best, [{key: value for key, value in item.items() if key not in {"model", "calibration"}} for item in candidates]


def score(rows, candidate, baseline):
    labels, probabilities, reasons = predict(candidate["model"], rows, candidate["temperature"], candidate["threshold"])
    combined = hybrid(rows, labels, baseline)
    projected = v3.project_read_labels(combined)
    rules = v3.project_read_labels([baseline[row["id"]]["intent"] for row in rows])
    outcomes = [resolve(row["utterance"], label, row["input_source"]) for row, label in zip(rows, projected)]
    details = []
    annotated, exact, negative_reads, corrected_known_reads, preserved_mutations = 0, 0, 0, 0, 0
    for row, label, full, joined, probability, reason, outcome in zip(rows, labels, combined, projected, probabilities, reasons, outcomes):
        expected_entities = json.loads(row["entities"])
        if row["intent"] in {"CHECK_MEMORY", "WHAT_DID_I_PROMISE"} and expected_entities:
            annotated += 1
            exact += outcome["entities"] == expected_entities
        negative_reads += int(row["intent"] == "UNKNOWN" and outcome["reads"] != 0)
        corrected_known_reads += int(row["intent"] == "UNKNOWN" and baseline[row["id"]]["intent"] in READ_LABELS and joined == "UNKNOWN")
        preserved_mutations += int(baseline[row["id"]]["intent"] not in {*READ_LABELS, "UNKNOWN"} and
                                  full == baseline[row["id"]]["intent"])
        details.append(dict(id=row["id"], utterance=row["utterance"], expected=row["intent"], native=baseline[row["id"]]["intent"],
                            learned=label, hybrid=joined, confidence=float(probability.max()), reason=reason,
                            simulated=outcome, correct_intent=joined == row["intent"]))
    return {"rules": v3.metrics(rows, rules), "candidate": v3.metrics(rows, labels), "hybrid": v3.metrics(rows, projected),
            "simulated_outcomes": dict(Counter(outcome["status"] for outcome in outcomes)),
            "annotated_entity_rows": annotated, "exact_entity_rows": exact, "negative_simulated_reads": negative_reads,
            "unsafe_known_read_matches_guarded": corrected_known_reads, "preserved_nonread_rule_matches": preserved_mutations,
            "details": details}


def export(candidate, output):
    model = candidate["model"]
    features = model.named_steps["features"]
    blocks = []
    for name, vectorizer in features.transformer_list:
        blocks.append(dict(name=name, weight=features.transformer_weights[name], vocabulary={key: int(value) for key,value in vectorizer.vocabulary_.items()},
                           idf=vectorizer.idf_.tolist(), analyzer=vectorizer.analyzer, ngram_range=vectorizer.ngram_range,
                           sublinear_tf=True, norm="l2", token_pattern=vectorizer.token_pattern))
    payload = dict(format="METIS_DEVELOPMENT_LINEAR_INTENT_V1", version="v4", labels=model.classes_.tolist(),
                   features=blocks, coefficients=model.named_steps["classifier"].coef_.tolist(),
                   intercept=model.named_steps["classifier"].intercept_.tolist(), temperature=candidate["temperature"],
                   threshold=candidate["threshold"], preprocessing_source_sha256=sha256(HERE/"v4_language.py"),
                   dataset_manifest_sha256=sha256(HERE/"manifest-v4.json"), enabled=False,
                   limit="Development export only; Android tokenizer/numeric parity and device costs unverified")
    atomic_text(output, json.dumps(payload, separators=(",", ":"))+"\n")
    return {"path": output.name, "bytes": output.stat().st_size, "sha256": sha256(output), "packaged": False}


def export_parity(candidate, path, rows):
    """Score serialized weights without sklearn vectorizers; Python numeric parity only."""
    payload = json.loads(path.read_text(encoding="utf-8"))
    coefficients = np.asarray(payload["coefficients"])
    expected = candidate["model"].decision_function([row["utterance"] for row in rows])
    actual = []
    for row in rows:
        value = feature_text(row["utterance"])
        sparse, offset = {}, 0
        for block in payload["features"]:
            low, high = block["ngram_range"]
            tokens = []
            if block["analyzer"] == "word":
                words = re.findall(block["token_pattern"], value)
                for width in range(low, high + 1):
                    tokens.extend(" ".join(words[i:i+width]) for i in range(len(words)-width+1))
            else:
                for word in re.sub(r"\s\s+", " ", value).split():
                    padded = " " + word + " "
                    for width in range(low, high + 1):
                        start = 0
                        tokens.append(padded[:width])
                        while start + width < len(padded):
                            start += 1
                            tokens.append(padded[start:start+width])
                        if start == 0:
                            break
            counts = Counter(token for token in tokens if token in block["vocabulary"])
            values = {block["vocabulary"][token]: (1 + math.log(count))*block["idf"][block["vocabulary"][token]]
                      for token, count in counts.items()}
            norm = math.sqrt(sum(weight*weight for weight in values.values()))
            if norm:
                sparse.update({offset+index: weight/norm*block["weight"] for index, weight in values.items()})
            offset += len(block["vocabulary"])
        logits = np.asarray(payload["intercept"], dtype=float)
        for index, value in sparse.items():
            logits += coefficients[:, index]*value
        actual.append(logits)
    actual = np.asarray(actual)
    error = float(np.max(np.abs(expected-actual)))
    mismatches = int(np.sum(expected.argmax(axis=1) != actual.argmax(axis=1)))
    if error > 1e-9 or mismatches:
        raise ValueError("Serialized model numeric parity failed")
    return dict(rows=len(rows), maximum_logit_error=error, argmax_mismatches=mismatches,
                scope="Python serialized feature/weight math; shared preprocessing; not Android parity")


def evaluate(baseline_path, output):
    manifest = json.loads((HERE/"manifest-v4.json").read_text(encoding="utf-8"))
    if manifest["sources"] != sources():
        raise ValueError("V4 sources changed; regenerate and review the manifest")
    for name, digest in manifest["hashes"].items():
        if sha256(HERE/name) != digest:
            raise ValueError("Dataset differs from frozen manifest")
    if sha256(V3/"user-challenge-2026-10-10.csv") != manifest["exposed_user_challenge_sha256"] or \
            sha256(HERE.parent/"safety-v2.csv") != manifest["historical_safety_sha256"]:
        raise ValueError("Regression corpus changed")
    rows = read_csv(HERE/"intent-v4.csv")
    validate(rows)
    user = read_csv(V3/"user-challenge-2026-10-10.csv")
    historical = read_csv(HERE.parent/"safety-v2.csv")
    # Exact contributed strings stay outside fitting and all selection partitions.
    user_texts = {normalized_text(row["utterance"]) for row in user}
    if user_texts & {normalized_text(row["utterance"]) for row in rows}:
        raise ValueError("Contributed prompts must remain separate regressions")
    baseline = v2.load_baseline(baseline_path, rows+user+historical, sha256(HERE/"rules-input-v4.tsv"), baseline_source_hash(ROOT))
    native = v2.safety_metrics(historical, baseline)
    if native["failures"]:
        raise ValueError("Historical native baseline regression")
    parts = {part: [row for row in rows if row["split"] == part] for part in PARTITIONS}
    best, selection = select(parts)
    development, exposed = score(parts["test"], best, baseline), score(user, best, baseline)
    strict = [row for row in historical if row["intent"] == "UNKNOWN" and row["case_kind"] != "typo"]
    historical_score = score(strict, best, baseline)
    gates = {
        "macro_f1_0_90": {"met": development["hybrid"]["macro_f1"] >= .90, "scope": "development only"},
        "held_out_gain": {"met": development["hybrid"]["macro_f1"] > development["rules"]["macro_f1"], "scope": "development only"},
        "sample_unknown_rejection": {"met": development["hybrid"]["unknown_false_accepts"] == 0 and historical_score["hybrid"]["unknown_false_accepts"] == 0},
        "independent_evaluation": {"met": False, "reason": "Fresh reviewed benchmark required; user prompts exposed during v4 development"},
        "confidence_calibration": {"met": False, "reason": "Synthetic calibration only"},
        "candidate_entities_outcomes_policy": {"met": False, "reason": "Python entity/outcome prototype; no actual Android repository/policy integration"},
        "android_parity_latency_ram_battery": {"met": False, "reason": "Development export only; Android parity/device measurements not performed"},
    }
    artifact = export(best, output.parent/"read-query-v4-development-model.json")
    parity = export_parity(best, output.parent/"read-query-v4-development-model.json", parts["test"]+user+strict)
    report = dict(version=manifest["version"], data=manifest["data"], eligible_training_rows=sum(eligibility(row["utterance"],row["input_source"]) is None for row in parts["train"]),
                  candidate={key: value for key, value in best.items() if key not in {"model", "validation"}},
                  provenance=dict(manifest_sha256=sha256(HERE/"manifest-v4.json"), native_baseline_sha256=sha256(baseline_path), native_source_sha256=baseline_source_hash(ROOT)),
                  selection=selection, development=development, exposed_user_regressions=exposed, historical_strict=historical_score,
                  native_historical=native, development_export=artifact, export_parity=parity, gates=gates, decision=v2.adoption_decision(gates),
                  adoption_eligible=False, rules_retained=True, limit=manifest["limit"])
    atomic_text(output, json.dumps(report, indent=2, ensure_ascii=False)+"\n")
    print(json.dumps(dict(candidate=report["candidate"], development=development["hybrid"], user=exposed["hybrid"],
                         entity_rows=[development["exact_entity_rows"],development["annotated_entity_rows"]],
                         negative_simulated_reads=development["negative_simulated_reads"], decision=report["decision"]), indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=ROOT/"app/build/reports/phase15/rules-v4.tsv")
    parser.add_argument("--output", type=Path, default=ROOT/"app/build/reports/phase15/results-v4.json")
    args = parser.parse_args()
    evaluate(args.baseline, args.output)
