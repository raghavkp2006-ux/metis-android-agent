"""Curate whole approved groups and freeze a traceable v0; never trains a model."""
import argparse
from collections import Counter
import copy
import hashlib
from importlib.metadata import version as package_version
import json
from pathlib import Path
import platform
import sys

from ml.nlu_v1.ingestion.common import REGISTRY
from contract import LABELS, unique_keys
from source_registry import ROOT, UniqueLoader
from ml.nlu_v1.data_management.records import DataError, canonical_issues, facts_hash, registered_source, sha_json, validate_record
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes
from ml.nlu_v1.data_management.dedupe import prepare_embeddings, collection_hash
from ml.nlu_v1.splitting.split import (
    FINAL, PROTECTED, INELIGIBLE, TUNING, FILES, connected_groups, plan, read_jsonl, strict_unicode, verify_bundle, write_bundle,
)
from jsonschema import ValidationError
import yaml

CONFIG = ROOT / "ml/nlu_v1/configs/dataset_v0.yaml"
VERSION = "nlu_v1_dataset_v0_001"
DOMAIN_GROUPS = {"TASK": "TASK", "CALENDAR": "CALENDAR", "MEMORY": "MEMORY", "PROMISE": "PROMISE", "PERSON": "PERSON",
    "GOAL": "GOAL_PROJECT", "PROJECT": "GOAL_PROJECT", "REMINDER": "REMINDER_ALARM_TIMER", "ALARM": "REMINDER_ALARM_TIMER", "TIMER": "REMINDER_ALARM_TIMER",
    "PLANNING": "PLANNING_FOCUS_HABIT", "FOCUS": "PLANNING_FOCUS_HABIT", "HABIT": "PLANNING_FOCUS_HABIT",
    "COMMUNICATION": "COMMUNICATION_NAVIGATION", "NAVIGATION": "COMMUNICATION_NAVIGATION",
    "PREFERENCE": "PREFERENCE_KNOWLEDGE_ETC", "KNOWLEDGE": "PREFERENCE_KNOWLEDGE_ETC", "REFLECTION": "PREFERENCE_KNOWLEDGE_ETC", "UNKNOWN": "PREFERENCE_KNOWLEDGE_ETC"}


def load_config():
    config = yaml.load(CONFIG.read_text(encoding="utf-8"), Loader=UniqueLoader)
    if config.get("build_version") != VERSION or (config.get("minimum_rows"), config.get("maximum_rows"), config.get("target_rows")) != (1500, 2000, 2000):
        raise DataError("UNKNOWN_V0_BUILD_CONFIGURATION")
    if set(config["quotas"]) != set(DOMAIN_GROUPS.values()) | {"OOD_UNSUPPORTED"} or sum(config["quotas"].values()) != config["target_rows"]:
        raise DataError("V0_QUOTA_GROUPS_OR_TOTAL_INVALID")
    if config.get("source_order") != ["PUBLIC_DATASET", "TRAINABLE_LEGACY", "CONTROLLED_SYNTHETIC", "HARD_NEGATIVE"] or set(config["required_groups"]) != set(config["quotas"]):
        raise DataError("V0_SOURCE_ORDER_OR_COVERAGE_CHANGED")
    if config.get("quota_policy") != "SOFT_COUNTS_REQUIRED_NONZERO_GROUP_COVERAGE" or config.get("final_policy") != "PRESERVE_ALL_ELIGIBLE_INDEPENDENT_GROUPS_BEFORE_CURATION" or set(config["freeze_requires"]) != {"APPROVED_ROWS", "PUBLIC_RIGHTS", "VERIFIED_INTAKE", "COMPLETE_EMBEDDINGS", "GROUPED_LEAKAGE_PASS", "NONEMPTY_FIVE_PARTITIONS"}:
        raise DataError("V0_FREEZE_GATES_CHANGED")
    return config


def bucket(row):
    if row["disposition"] in ("OUT_OF_DOMAIN", "KNOWN_UNSUPPORTED"):
        return "OOD_UNSUPPORTED"
    return DOMAIN_GROUPS[row["domain"]]


def source_rank(row):
    if row["metadata"].get("generation_role") in ("HARD_NEGATIVE", "COMPOUND"):
        return 3
    return {"PUBLIC_DATASET": 0, "TRAINABLE_LEGACY": 1, "CONTROLLED_SYNTHETIC": 2, "INDEPENDENT_EVALUATION": -1}[row["source"]["source_class"]]


def issues(row, groups):
    strict_unicode(row)
    validate_record(row)
    reasons = set(canonical_issues(row, groups))
    if row["review_status"] != "APPROVED":
        reasons.add("ROW_NOT_APPROVED")
    if row["privacy"] != "PUBLIC":
        reasons.add("ROW_NOT_PUBLIC")
    if not row["privacy_review"] or not row["privacy_review"]["publication_allowed"]:
        reasons.add("PUBLICATION_RIGHTS_UNREVIEWED")
    reasons.update(row["quarantine_reasons"])
    return sorted(reasons)


def distribution(rows):
    counters = {key: Counter() for key in ("groups", "sources", "source_classes", "families", "domains", "actions", "dispositions", "slots", "privacy", "licenses", "review_states")}
    unresolved_families = 0
    for row in rows:
        counters["groups"][bucket(row)] += 1
        counters["sources"][row["source"]["dataset"] + "@" + row["source"]["version"]] += 1
        counters["source_classes"][row["source"]["source_class"]] += 1
        counters["families"][row["family"] or "UNRESOLVED"] += 1
        unresolved_families += row["family"] is None
        for key, field in (("domains", "domain"), ("actions", "action"), ("dispositions", "disposition"), ("privacy", "privacy"), ("review_states", "review_status")):
            counters[key][row[field]] += 1
        counters["slots"].update(slot["type"] for slot in row["slots"])
        source = registered_source(row["source"]["dataset"], row["schema_version"])
        counters["licenses"][(source or {}).get("license", {}).get("id", "UNVERIFIED")] += 1
    return {"rows": len(rows), **{key: dict(sorted(value.items())) for key, value in counters.items()},
            "slot_count_unit": "annotated_spans_not_BIO_tokens", "unresolved_family_rows": unresolved_families,
            "missing_labels": {key: sorted(set(LABELS[key]) - set(counters[key])) for key in ("domains", "actions", "dispositions", "slots")}}


def curate(rows, *, groups=None):
    """Unapproved groups are reported, never promoted or silently relabeled."""
    rows = list(rows)
    config = load_config()
    diagnostics = {row["id"]: issues(row, groups) for row in rows}
    selected, rejected = [], []
    counts = Counter()
    if not rows:
        state = "BLOCKED_EMPTY_CORPUS"
    elif any(not row["family"] or not row["scenario_id"] or not row["semantic_parent_id"] for row in rows):
        # Grouping cannot be safely reconstructed from guessed source intents.
        state = "BLOCKED_GROUP_METADATA_UNRESOLVED"
        rejected = [{"row_id": row["id"], "reasons": diagnostics[row["id"]]} for row in rows]
    else:
        state = "CURATION_PREVIEW_NOT_FROZEN"
        components = connected_groups(rows)
        by_id = {row["id"]: row for row in rows}
        assignments, _ = plan(rows, config["seed"])
        candidates = []
        for ids in components:
            component = [by_id[ident] for ident in ids]
            reasons = sorted({reason for ident in ids for reason in diagnostics[ident]})
            destination = assignments[ids[0]]
            if destination == INELIGIBLE:
                reasons.append("SOURCE_SPLIT_NOT_ELIGIBLE")
            if reasons:
                rejected.append({"group_sha256": sha_json(ids), "rows": len(ids), "reasons": reasons})
                if destination in (FINAL, PROTECTED, "DEVELOPMENT", "CALIBRATION", "SELECTION"):
                    state = "BLOCKED_PROTECTED_GROUP_NOT_APPROVED"
                continue
            # Preserve independent and official material, including their connected
            # neighbors. Curation never selects convenient final examples.
            mandatory = destination in (FINAL, PROTECTED) or any(row["partition"] in (FINAL, PROTECTED, *TUNING[1:]) for row in component)
            if mandatory:
                selected.extend(component)
                if destination != PROTECTED:
                    counts.update(bucket(row) for row in component)
            else:
                candidates.append((component, max(source_rank(row) for row in component)))
        candidates.sort(key=lambda item: (item[1], sha_json([config["seed"], sorted(row["id"] for row in item[0])])))
        # Whole-group selection, soft class quotas, deterministic source order.
        while candidates:
            useful = [(index, component, priority) for index, (component, priority) in enumerate(candidates)
                      if any(counts[bucket(row)] < config["quotas"][bucket(row)] for row in component)]
            available = useful or [(index, component, priority) for index, (component, priority) in enumerate(candidates)]
            rank = min(priority for _, _, priority in available)
            choices = [(index, component) for index, component, priority in available if priority == rank]
            def score(item):
                index, component = item
                need = sum(max(0, config["quotas"][bucket(row)] - counts[bucket(row)]) for row in component) / len(component)
                return -need, sha_json([config["seed"], sorted(row["id"] for row in component)])
            index, component = min(choices, key=score)
            candidates.pop(index)
            if sum(counts.values()) + len(component) > config["target_rows"]:
                rejected.append({"group_sha256": sha_json(sorted(row["id"] for row in component)), "rows": len(component), "reasons": ["WHOLE_GROUP_EXCEEDS_REMAINING_BUDGET"]})
                continue
            selected.extend(component)
            counts.update(bucket(row) for row in component)
    summary = {"report_version": "nlu_v1_v0_curation_001", "status": state, "input_sha256": collection_hash(rows),
        "input_distribution": distribution(rows), "selected_distribution": distribution(selected),
        "scientific_group_counts": dict(sorted(counts.items())), "scientific_rows": sum(counts.values()),
        "minimum_rows": config["minimum_rows"], "maximum_rows": config["maximum_rows"], "quota_targets": config["quotas"],
        "missing_groups": sorted(set(config["required_groups"]) - {key for key, value in counts.items() if value}),
        "quota_deviation": {key: counts[key] - quota for key, quota in config["quotas"].items()},
        "rejected": rejected, "rejection_reason_counts": dict(sorted(Counter(reason for item in rejected for reason in item["reasons"]).items())),
        "dataset_v0_frozen": False}
    return sorted(selected, key=lambda row: row["id"]), summary


def build(rows, destination, *, groups=None, embeddings=None):
    config = load_config()
    rows = list(rows)
    selected, summary = curate(rows, groups=groups)
    if summary["status"].startswith("BLOCKED"):
        raise DataError(summary["status"])
    if not config["minimum_rows"] <= summary["scientific_rows"] <= config["maximum_rows"]:
        raise DataError("V0_CURATED_ROW_COUNT_OUT_OF_RANGE")
    if summary["missing_groups"]:
        raise DataError("V0_REQUIRED_SEMANTIC_GROUPS_MISSING")
    # Verify complete embeddings against the entire submitted corpus first;
    # selecting a subset cannot hide missing/stale source vectors.
    _, status, _, _ = prepare_embeddings(rows, embeddings)
    if status != "COMPLETE":
        raise DataError("V0_COMPLETE_CORPUS_EMBEDDINGS_REQUIRED")
    filtered = copy.deepcopy(embeddings)
    ids = {row["id"] for row in selected}
    filtered["rows"] = [entry for entry in filtered["rows"] if entry["id"] in ids]
    destination = Path(destination)
    if destination.exists():
        raise DataError("V0_FREEZE_DESTINATION_ALREADY_EXISTS")
    partitions = write_bundle(selected, destination / "partitions", groups=groups, embeddings=filtered, seed=config["seed"])
    hashes = []
    split_hashes = {}
    for partition, name in FILES.items():
        data = (destination / "partitions" / name).read_bytes()
        split_hashes[name] = hashlib.sha256(data).hexdigest()
        for line in data.splitlines():
            row = json.loads(line, object_pairs_hook=unique_keys)
            hashes.append({"id": row["id"], "partition": partition, "row_sha256": hashlib.sha256(line).hexdigest(),
                           "canonical_facts_sha256": row["canonical_facts_sha256"], "canonical_review_sha256": row["canonical_review_sha256"]})
    artifacts = {"row_hashes.jsonl": jsonl_bytes(sorted(hashes, key=lambda row: row["id"])), "curation_report.json": json_bytes(summary),
        "distribution_report.json": json_bytes({"scientific": distribution([row for row in selected if row["id"] in {h["id"] for h in hashes}]),
                                                "all_selected_including_external": distribution(selected)}),
        "dataset_v0_config.yaml": CONFIG.read_bytes()}
    for name, data in artifacts.items():
        atomic_new(destination / name, data)
    scientific = json.loads(artifacts["distribution_report.json"])["scientific"]
    source_licenses = {}
    for row in selected:
        source = registered_source(row["source"]["dataset"], row["schema_version"])
        source_licenses[row["source"]["dataset"]] = {"license_id": source["license"]["id"], "license_evidence_url": source["license"]["evidence_url"],
            "source_url": source["source_url"], "citation": source["citation"]}
    code_paths = ["dataset_v0/build.py", "splitting/split.py", "data_management/records.py", "data_management/dedupe.py", "contract.py", "context_contract.py"]
    manifest = {"manifest_version": VERSION, "status": "FROZEN", "scientific_rows": len(hashes), "source_counts": scientific["sources"],
        "family_counts": scientific["families"], "class_counts": {key: scientific[key] for key in ("domains", "actions", "dispositions")},
        "slot_counts": scientific["slots"], "privacy_license_summary": {key: scientific[key] for key in ("privacy", "licenses")},
        "source_licenses": source_licenses, "split_hashes": split_hashes, "split_row_counts": partitions["row_counts"],
        "partition_manifest_sha256": hashlib.sha256((destination / "partitions/manifest.json").read_bytes()).hexdigest(),
        "input_sha256": collection_hash(rows), "selected_sha256": collection_hash(selected), "seed": config["seed"],
        "files": {name: hashlib.sha256(data).hexdigest() for name, data in artifacts.items()},
        "code_sha256": {name: hashlib.sha256((ROOT / "ml/nlu_v1" / name).read_bytes().replace(b"\r\n", b"\n")).hexdigest() for name in code_paths},
        "environment": {"python": sys.version, "platform": platform.platform(), "packages": {name: package_version(name) for name in ("jsonschema", "PyYAML")}},
        "model_trained": False, "semantic_model_metrics": "NOT_EVALUATED_PLAN_08_AND_09"}
    atomic_new(destination / "dataset_v0_manifest.json", json_bytes(manifest))
    return manifest


def verify(root):
    root = Path(root)
    manifest = json.loads((root / "dataset_v0_manifest.json").read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
    if manifest.get("manifest_version") != VERSION or manifest.get("status") != "FROZEN" or set(manifest["files"]) != {"row_hashes.jsonl", "curation_report.json", "distribution_report.json", "dataset_v0_config.yaml"}:
        raise DataError("V0_FREEZE_MANIFEST_INVALID")
    config = load_config()
    if not config["minimum_rows"] <= manifest["scientific_rows"] <= config["maximum_rows"]:
        raise DataError("V0_FROZEN_ROW_COUNT_INVALID")
    for name, digest in manifest["files"].items():
        path = root / name
        if path.resolve().parent != root.resolve() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise DataError("V0_FROZEN_ARTIFACT_CHANGED")
    partitions = verify_bundle(root / "partitions")
    if not partitions["final_ready"] or hashlib.sha256((root / "partitions/manifest.json").read_bytes()).hexdigest() != manifest["partition_manifest_sha256"]:
        raise DataError("V0_PARTITION_MANIFEST_CHANGED")
    rows = []
    for name, digest in manifest["split_hashes"].items():
        if name not in FILES.values() or digest != partitions["files"][name]:
            raise DataError("V0_SPLIT_HASH_CHANGED")
        rows.extend((root / "partitions" / name).read_bytes().splitlines())
    expected = []
    for line in rows:
        row = json.loads(line, object_pairs_hook=unique_keys)
        expected.append({"id": row["id"], "partition": row["partition"], "row_sha256": hashlib.sha256(line).hexdigest(),
                         "canonical_facts_sha256": row["canonical_facts_sha256"], "canonical_review_sha256": row["canonical_review_sha256"]})
    actual = [json.loads(line, object_pairs_hook=unique_keys) for line in (root / "row_hashes.jsonl").read_text(encoding="utf-8").splitlines()]
    if set(manifest["split_hashes"]) != set(FILES.values()) or len(expected) != manifest["scientific_rows"] or len({row["id"] for row in expected}) != len(expected) or sorted(expected, key=lambda row: row["id"]) != actual:
        raise DataError("V0_ROW_HASH_BINDINGS_INVALID")
    statistics = json.loads((root / "distribution_report.json").read_text(encoding="utf-8"))["scientific"]
    if statistics["rows"] != manifest["scientific_rows"] or manifest["source_counts"] != statistics["sources"] or manifest["family_counts"] != statistics["families"] or manifest["class_counts"] != {key: statistics[key] for key in ("domains", "actions", "dispositions")} or manifest["slot_counts"] != statistics["slots"] or manifest["privacy_license_summary"] != {key: statistics[key] for key in ("privacy", "licenses")} or manifest["split_row_counts"] != partitions["row_counts"]:
        raise DataError("V0_DISTRIBUTION_SUMMARY_CHANGED")
    return manifest


def snapshot_candidates(rows, destination, *, acquisition_report=None):
    """An immutable, explicitly UNFROZEN candidate manifest, never a release."""
    rows = list(rows)
    for row in rows:
        strict_unicode(row)
        validate_record(row)
    destination = Path(destination)
    if destination.exists():
        raise DataError("V0_CANDIDATE_SNAPSHOT_ALREADY_EXISTS")
    # Use the same guarded writer before creating a directory. No existing
    # artifact, protected archive, app or frozen schema can be replaced.
    resolved = destination.resolve()
    for protected in (ROOT / "archive", ROOT / "datasets/legacy", ROOT / "app", ROOT / "ml/nlu_v1/schema", ROOT / "ml/nlu_v1/schema_versions"):
        if protected.resolve() == resolved or protected.resolve() in resolved.parents:
            raise DataError("PROTECTED_CANDIDATE_SNAPSHOT_LOCATION")
    payload = jsonl_bytes(rows)
    hashes = []
    for row, line in zip(rows, payload.splitlines()):
        hashes.append({"id": row["id"], "row_sha256": hashlib.sha256(line).hexdigest(), "canonical_facts_sha256": facts_hash(row)})
    statistics = distribution(rows)
    config = load_config()
    report = {"status": "QUARANTINED_CANDIDATES_NOT_A_DATASET_FREEZE", "distribution": statistics,
        "target_groups": config["quotas"], "missing_groups": sorted(set(config["required_groups"]) - set(statistics["groups"])),
        "quota_deviation": {key: statistics["groups"].get(key, 0) - count for key, count in config["quotas"].items()},
        "acquisition_report": acquisition_report, "schema_validated": True, "semantic_labels_reviewed": False,
        "leakage_validated": False, "real_embedding_audit_complete": False, "final_independent_frozen": False, "model_metrics": "NOT_EVALUATED"}
    artifacts = {"candidates.jsonl": payload, "row_hashes.jsonl": jsonl_bytes(hashes), "distribution_report.json": json_bytes(report)}
    manifest = {"manifest_version": VERSION, "status": "QUARANTINED_CANDIDATES_NOT_FROZEN", "candidate_rows": len(rows),
        "curated_approved_rows": 0, "source_counts": statistics["sources"], "family_counts": statistics["families"],
        "class_counts": {key: statistics[key] for key in ("domains", "actions", "dispositions")}, "slot_counts": statistics["slots"],
        "privacy_license_summary": {key: statistics[key] for key in ("privacy", "licenses")},
        "split_hashes": {name: None for name in FILES.values()}, "split_hash_status": "NO_APPROVED_PARTITIONS_EXIST",
        "files": {name: hashlib.sha256(data).hexdigest() for name, data in artifacts.items()},
        "input_sha256": collection_hash(rows), "seed": config["seed"], "training_authorized": False, "dataset_v0_frozen": False}
    destination.mkdir(parents=True, exist_ok=False)
    for name, data in artifacts.items():
        atomic_new(destination / name, data)
    atomic_new(destination / "dataset_v0_manifest.json", json_bytes(manifest))
    return manifest


def verify_candidates(root):
    root = Path(root)
    manifest = json.loads((root / "dataset_v0_manifest.json").read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
    if manifest.get("manifest_version") != VERSION or manifest.get("status") != "QUARANTINED_CANDIDATES_NOT_FROZEN" or manifest.get("training_authorized") is not False or manifest.get("dataset_v0_frozen") is not False or set(manifest["files"]) != {"candidates.jsonl", "row_hashes.jsonl", "distribution_report.json"} or manifest.get("split_hashes") != {name: None for name in FILES.values()}:
        raise DataError("V0_CANDIDATE_MANIFEST_INVALID")
    for name, digest in manifest["files"].items():
        path = root / name
        if path.resolve().parent != root.resolve() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise DataError("V0_CANDIDATE_ARTIFACT_CHANGED")
    rows = read_jsonl(root / "candidates.jsonl")
    hashes = [{"id": row["id"], "row_sha256": hashlib.sha256(line).hexdigest(), "canonical_facts_sha256": facts_hash(row)}
              for row, line in zip(rows, (root / "candidates.jsonl").read_bytes().splitlines())]
    stored = [json.loads(line, object_pairs_hook=unique_keys) for line in (root / "row_hashes.jsonl").read_text(encoding="utf-8").splitlines()]
    statistics = json.loads((root / "distribution_report.json").read_text(encoding="utf-8"))["distribution"]
    if len(rows) != manifest["candidate_rows"] or len({row["id"] for row in rows}) != len(rows) or stored != hashes or collection_hash(rows) != manifest["input_sha256"] or statistics != distribution(rows):
        raise DataError("V0_CANDIDATE_ROW_BINDINGS_INVALID")
    if manifest["source_counts"] != statistics["sources"] or manifest["family_counts"] != statistics["families"] or manifest["class_counts"] != {key: statistics[key] for key in ("domains", "actions", "dispositions")} or manifest["slot_counts"] != statistics["slots"] or manifest["privacy_license_summary"] != {key: statistics[key] for key in ("privacy", "licenses")}:
        raise DataError("V0_CANDIDATE_DISTRIBUTION_CHANGED")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--check", action="store_true")
    modes.add_argument("--preview", type=Path)
    modes.add_argument("--build", type=Path)
    modes.add_argument("--verify", type=Path)
    modes.add_argument("--verify-candidates", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--groups", type=Path)
    parser.add_argument("--embeddings", type=Path)
    args = parser.parse_args()
    load_config()
    if args.check:
        print("PASS: Dataset v0 targets, source order, coverage and freeze gates")
    elif args.verify:
        print(json.dumps(verify(args.verify), sort_keys=True))
    elif args.verify_candidates:
        print(json.dumps(verify_candidates(args.verify_candidates), sort_keys=True))
    else:
        groups = None
        if args.groups:
            snapshot = json.loads(args.groups.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
            groups = snapshot["families"], snapshot["scenarios"]
        rows = read_jsonl(args.preview or args.build)
        if args.preview:
            _, summary = curate(rows, groups=groups)
            if args.output:
                atomic_new(args.output, json_bytes(summary))
            print(json.dumps(summary, sort_keys=True))
        else:
            if not args.output:
                parser.error("--output is required for --build")
            embedding_data = json.loads(args.embeddings.read_text(encoding="utf-8"), object_pairs_hook=unique_keys) if args.embeddings else None
            print(json.dumps(build(rows, args.output, groups=groups, embeddings=embedding_data), sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, RecursionError, ValidationError) as error:
        code = str(error) if isinstance(error, DataError) else type(error).__name__
        raise SystemExit("V0 build rejected: " + code) from None
