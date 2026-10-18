"""Deterministic group partitions. Planning never approves data or changes rows."""
import argparse
from collections import Counter, defaultdict
import copy
import hashlib
import json
from pathlib import Path
import re

from ml.nlu_v1.ingestion.common import tokenize
from contract import unique_keys, validate as validate_prediction, obj
from jsonschema import Draft202012Validator, ValidationError
from source_registry import ROOT, UniqueLoader
from ml.nlu_v1.data_management.records import (
    DataError, canonical_issues, facts_hash, registered_source, schema as canonical_schema, sha_json, validate_record,
)
from ml.nlu_v1.data_management.dedupe import (
    audit, collection_hash, load_policy as dedupe_policy, normalized, skeleton, source_key, verify_report,
)
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes
import yaml

POLICY = ROOT / "ml/nlu_v1/configs/split_policy.yaml"
TUNING = ("TRAIN", "DEVELOPMENT", "CALIBRATION", "SELECTION")
FINAL = "FINAL_INDEPENDENT_TEST"
PROTECTED = "PROTECTED_EVALUATION"
INELIGIBLE = "INELIGIBLE"
FILES = {"TRAIN": "train.jsonl", "DEVELOPMENT": "development.jsonl", "CALIBRATION": "calibration.jsonl",
         "SELECTION": "selection.jsonl", FINAL: "final_independent.jsonl"}
ACTORS = ("speaker_id", "speaker", "user_id", "userid", "worker_id", "participant_id")


def load_policy():
    policy = yaml.load(POLICY.read_text(encoding="utf-8"), Loader=UniqueLoader)
    expected = {"TRAIN": .70, "DEVELOPMENT": .10, "CALIBRATION": .075, "SELECTION": .075, FINAL: .05}
    if policy.get("policy_version") != "nlu_v1_splits_001" or policy.get("targets") != expected or policy.get("allocation") != "GROUPED_DETERMINISTIC_GREEDY":
        raise DataError("UNKNOWN_SPLIT_POLICY")
    if policy.get("group_keys") != ["family", "scenario", "source_id", "semantic_parent", "generation_parent", "source_actor", "generation_seed", "normalized_text", "source_skeleton"]:
        raise DataError("REQUIRED_GROUPING_REMOVED")
    if (policy.get("official_tests"), policy.get("final_source_class"), policy.get("final_minimum_rows"),
        policy.get("public_output_privacy"), policy.get("missing_embeddings"), policy.get("missing_eligible_rows"),
        policy.get("missing_independent_rows"), policy.get("final_tuning_access")) != (
            "PRESERVE_AND_EXCLUDE_CONNECTED_GROUP_FROM_TUNING", "INDEPENDENT_EVALUATION", 1, "PUBLIC", "FAIL_BUILD", "FAIL_BUILD", "FAIL_FINAL_FREEZE", "DENY_ALWAYS"):
        raise DataError("UNSAFE_SPLIT_POLICY")
    return policy


def strict_unicode(value):
    if isinstance(value, str):
        if any(0xD800 <= ord(char) <= 0xDFFF for char in value):
            raise DataError("MALFORMED_UNICODE")
        value.encode("utf-8", errors="strict")
    elif isinstance(value, dict):
        for key, item in value.items():
            strict_unicode(key)
            strict_unicode(item)
    elif isinstance(value, (list, tuple)):
        for item in value:
            strict_unicode(item)


def read_jsonl(path):
    try:
        data = Path(path).read_bytes()
        text = data.decode("utf-8", errors="strict")
        rows = [json.loads(line, object_pairs_hook=unique_keys,
                           parse_constant=lambda _: (_ for _ in ()).throw(DataError("NONFINITE_JSON")))
                for line in text.splitlines() if line.strip()]
        for row in rows:
            strict_unicode(row)
            validate_record(row)
    except (UnicodeError, json.JSONDecodeError):
        raise DataError("MALFORMED_UTF8_OR_JSON") from None
    return rows


def official(row):
    source = row["source"]
    registered = registered_source(source["dataset"], row["schema_version"])
    purposes = (registered or {}).get("allowed_splits", {}).get(source["original_split"], [])
    return bool(purposes) and not set(purposes) & set(TUNING + (FINAL,)) and all(
        "BENCHMARK" in purpose or purpose == "SMOKE_TEST" for purpose in purposes)


def group_keys(row):
    for field in ("family", "scenario_id", "semantic_parent_id"):
        if not row.get(field):
            raise DataError("GROUP_PROVENANCE_REQUIRED")
    source = row["source"]
    result = [("family", row["family"]), ("scenario", row["scenario_id"]),
              ("semantic_parent", row["semantic_parent_id"]), ("source_id", *source_key(row, dedupe_policy())),
              ("normalized_text", normalized(row["text"])),
              ("source_skeleton", source["dataset"], source["original_intent"], skeleton(row)),
              ("generation_parent", row["id"])]
    registry = registered_source(source["dataset"], row["schema_version"]) or {}
    actor_scope = registry.get("leakage_group", source["dataset"] + ":" + source["version"])
    for field in ACTORS:
        value = row["metadata"].get(field)
        if value is not None:
            if isinstance(value, bool) or not isinstance(value, (str, int)) or not str(value).strip():
                raise DataError("MALFORMED_ACTOR_METADATA")
            # Aliased user/worker/speaker identifiers are conservatively grouped;
            # raw identifiers never appear in reports or partition projections.
            result.append(("source_actor", actor_scope, str(value)))
    generation = row["generation"]
    if generation:
        result.extend(("generation_parent", parent) for parent in generation["parent_ids"])
        result.append(("generation_seed", source["dataset"], source["version"], generation["generator_id"], generation["seed"]))
    return result


def connected_groups(rows):
    parents = list(range(len(rows)))

    def find(index):
        while parents[index] != index:
            parents[index] = parents[parents[index]]
            index = parents[index]
        return index

    seen = {}
    ids = {row["id"] for row in rows}
    if len(ids) != len(rows):
        raise DataError("DUPLICATE_ROW_ID")
    for index, row in enumerate(rows):
        strict_unicode(row)
        validate_record(row)
        if set((row["generation"] or {}).get("parent_ids", [])) - ids:
            raise DataError("GENERATION_PARENT_MISSING")
        for key in group_keys(row):
            if key in seen:
                left, right = find(index), find(seen[key])
                parents[left] = right
            else:
                seen[key] = index
    components = defaultdict(list)
    for index in range(len(rows)):
        components[find(index)].append(rows[index]["id"])
    return sorted((sorted(ids) for ids in components.values()), key=lambda ids: sha_json(ids))


def allowed(row):
    if official(row) or row["partition"] == PROTECTED:
        return {PROTECTED}
    registered = registered_source(row["source"]["dataset"], row["schema_version"])
    permissions = set((registered or {}).get("allowed_splits", {}).get(row["source"]["original_split"], []))
    if row["source"]["source_class"] == "INDEPENDENT_EVALUATION":
        # Fresh independent sources require registration/approval at build time.
        return {FINAL} if FINAL in permissions else set()
    return permissions & set(TUNING)


def plan(rows, seed=17):
    """Group-only preview. Readiness/approvals are separate, mandatory build gates."""
    if type(seed) is not int or seed < 0:
        raise DataError("NONNEGATIVE_INTEGER_SEED_REQUIRED")
    rows = list(rows)
    policy = load_policy()
    components = connected_groups(rows)
    by_id = {row["id"]: row for row in rows}
    assignments, counts, blocks = {}, Counter(), []
    movable = []
    for ids in components:
        members = [by_id[key] for key in ids]
        locks = {row["partition"] for row in members} - {"UNASSIGNED"}
        if any(official(row) for row in members):
            locks.add(PROTECTED)
        if len(locks) > 1:
            raise DataError("CONNECTED_GROUP_HAS_CONFLICTING_LOCKS")
        if PROTECTED in locks:
            # Training-source neighbors are held out, never counted as official tests.
            if any(row["source"]["source_class"] == "INDEPENDENT_EVALUATION" for row in members):
                raise DataError("INDEPENDENT_GROUP_CONNECTED_TO_PUBLIC_BENCHMARK")
            destination = PROTECTED
        else:
            permissions = set.intersection(*(allowed(row) for row in members))
            if locks:
                destination = next(iter(locks))
                if destination not in permissions:
                    raise DataError("LOCKED_PARTITION_NOT_PERMITTED")
            elif permissions == {FINAL}:
                destination = FINAL
            elif permissions & set(TUNING):
                movable.append((ids, permissions))
                continue
            else:
                destination = INELIGIBLE
                blocks.append({"group_sha256": sha_json(ids), "code": "SOURCE_SPLIT_NOT_ELIGIBLE", "rows": len(ids)})
        for ident in ids:
            assignments[ident] = destination
        counts[destination] += len(ids)
    # Allocation targets apply after grouping. Final collection is independent;
    # never select 5% of a public training corpus and call it independent.
    assignable = sum(len(ids) for ids, _ in movable) + sum(counts[p] for p in TUNING)
    scale = sum(policy["targets"][p] for p in TUNING)
    targets = {p: assignable * policy["targets"][p] / scale for p in TUNING}
    movable.sort(key=lambda pair: (-len(pair[0]), sha_json([seed, pair[0]])))
    for ids, permissions in movable:
        choices = permissions & set(TUNING)
        def score(destination):
            cost = sum(((counts[p] + (len(ids) if p == destination else 0)) - targets[p]) ** 2 for p in TUNING)
            return cost, sha_json([seed, ids, destination])
        destination = min(choices, key=score)
        for ident in ids:
            assignments[ident] = destination
        counts[destination] += len(ids)
    report = leakage_report(rows, assignments)
    report.update(seed=seed, targets=policy["targets"], counts={p: counts[p] for p in (*TUNING, FINAL, PROTECTED, INELIGIBLE)},
                  connected_groups=len(components), largest_group_rows=max(map(len, components), default=0), blockers=blocks,
                  final_ready=counts[FINAL] >= policy["final_minimum_rows"], readiness="PLANNING_ONLY_NOT_APPROVAL")
    report["report_sha256"] = sha_json(report)
    return assignments, report


def leakage_report(rows, assignments):
    rows = list(rows)
    if set(assignments) != {row["id"] for row in rows} or set(assignments.values()) - set((*TUNING, FINAL, PROTECTED, INELIGIBLE)):
        raise DataError("ASSIGNMENTS_MUST_COVER_EVERY_ROW_EXACTLY")
    buckets = defaultdict(list)
    for row in rows:
        strict_unicode(row)
        validate_record(row)
        for key in group_keys(row) + [("exact_text", row["text"])]:
            buckets[key].append(row["id"])
    findings = []
    by_id = {row["id"]: row for row in rows}
    for key, ids in buckets.items():
        partitions = set()
        for ident in ids:
            destination = assignments[ident]
            if key[0] in ("exact_text", "normalized_text") and official(by_id[ident]):
                source = by_id[ident]["source"]
                destination = "OFFICIAL:" + ":".join(source[field] for field in ("dataset", "version", "original_split"))
            partitions.add(destination)
        partitions.discard(INELIGIBLE)
        if len(partitions) > 1:
            findings.append({"check": key[0], "key_sha256": sha_json(key), "row_ids": sorted(set(ids)), "partitions": sorted(partitions)})
    counts = Counter(item["check"] for item in findings)
    return {"report_version": "nlu_v1_split_report_001", "input_sha256": collection_hash(rows),
            "assignments_sha256": sha_json(assignments), "policy_sha256": sha_json(load_policy()), "rows": len(rows),
            "overlaps": {key: counts[key] for key in [*load_policy()["group_keys"], "exact_text"]},
            "findings": findings, "leakage_passed": bool(rows) and any(value != INELIGIBLE for value in assignments.values()) and not findings,
            "protected_partitions_present": sorted(set(assignments.values()) & {"DEVELOPMENT", "CALIBRATION", "SELECTION", FINAL, PROTECTED}),
            "official_rows": sum(official(row) for row in rows),
            "official_test_reassignments": sum(official(row) and assignments[row["id"]] != PROTECTED for row in rows),
            "actor_metadata_rows": sum(any(row["metadata"].get(field) is not None for field in ACTORS) for row in rows)}


def validate_build(rows, assignments, *, groups=None, embeddings=None, require_final=True):
    rows = list(rows)
    if not rows:
        raise DataError("EMPTY_DATASET_IS_NOT_VALIDATION_EVIDENCE")
    components = connected_groups(rows)
    leakage = leakage_report(rows, assignments)
    if INELIGIBLE in assignments.values():
        raise DataError("SOURCE_SPLIT_NOT_ELIGIBLE")
    if not leakage["leakage_passed"] or leakage["official_test_reassignments"]:
        raise DataError("CROSS_PARTITION_LEAKAGE_OR_OFFICIAL_TEST_MOVED")
    for ids in components:
        if len({assignments[key] for key in ids}) != 1:
            raise DataError("CONNECTED_GROUP_SPLIT")
    for row in rows:
        destination = assignments[row["id"]]
        if row["privacy"] != "PUBLIC":
            raise DataError("PRIVATE_RESTRICTED_OR_UNKNOWN_ROW_IN_PUBLIC_OUTPUT")
        if row["review_status"] != "APPROVED" or row["quarantine_reasons"] or canonical_issues(row, groups):
            raise DataError("ROW_NOT_REVIEWED_OR_PROVENANCE_LICENSE_UNVERIFIED")
        if not row["privacy_review"]["publication_allowed"]:
            raise DataError("PUBLIC_REDISTRIBUTION_NOT_APPROVED")
        if destination in TUNING and destination not in allowed(row):
            raise DataError("SOURCE_SPLIT_NOT_ALLOWED_FOR_TUNING")
        if destination == INELIGIBLE:
            raise DataError("INELIGIBLE_GROUP")
        if destination == FINAL:
            if row["source"]["source_class"] != "INDEPENDENT_EVALUATION" or FINAL not in allowed(row):
                raise DataError("PUBLIC_OR_SYNTHETIC_IS_NOT_INDEPENDENT")
            independence = row["metadata"].get("independence", {})
            required = ("collection_id", "collector_id", "reviewer_id", "protocol_sha256")
            if any(not independence.get(key) for key in required) or independence["collector_id"] == independence["reviewer_id"] or independence.get("development_exposed") is not False:
                raise DataError("FINAL_INDEPENDENCE_PROOF_REQUIRED")
            if not re.fullmatch(r"[a-f0-9]{64}", independence["protocol_sha256"]):
                raise DataError("FINAL_PROTOCOL_FINGERPRINT_REQUIRED")
    if require_final and not any(destination == FINAL for destination in assignments.values()):
        raise DataError("FRESH_FINAL_INDEPENDENT_DATA_MISSING")
    if any(destination == FINAL for destination in assignments.values()) and not set((*TUNING, FINAL)) <= set(assignments.values()):
        raise DataError("NONEMPTY_TRAIN_DEVELOPMENT_CALIBRATION_SELECTION_FINAL_REQUIRED")
    audited, report = audit(rows, embeddings, groups=groups)
    verify_report(rows, report)
    if report["quarantined_indices"]:
        raise DataError("DEDUPE_OR_SIMILARITY_QUARANTINE_UNRESOLVED")
    return leakage, report


def projection(row, destination):
    # Keep original row approvals intact. Partition membership is a separately
    # hashed overlay, never a mutation of reviewed canonical facts.
    result = {key: copy.deepcopy(row[key]) for key in ("id", "text", "domain", "action", "slots", "disposition", "ood", "tokens", "bio_tags",
        "nlu_schema_version", "family", "scenario_id", "semantic_parent_id", "generation")}
    result.update(schema_version="nlu_v1_partition_row_001", partition=destination, privacy="PUBLIC",
                  source=copy.deepcopy(row["source"]), license_id=row["privacy_review"]["license_id"],
                  canonical_facts_sha256=facts_hash(row), canonical_review_sha256=row["review_history"][-1]["event_sha256"])
    return result


def partition_schema():
    fields = copy.deepcopy(canonical_schema()["properties"])
    keep = {"id", "text", "domain", "action", "slots", "disposition", "ood", "tokens", "bio_tags", "nlu_schema_version",
            "family", "scenario_id", "semantic_parent_id", "generation", "source", "privacy", "partition", "schema_version"}
    fields = {key: value for key, value in fields.items() if key in keep}
    fields.update(schema_version={"const": "nlu_v1_partition_row_001"}, privacy={"const": "PUBLIC"},
                  partition={"enum": [*TUNING, FINAL, PROTECTED]}, nlu_schema_version={"enum": ["nlu_v1_schema_001", "nlu_v1_schema_002"]},
                  license_id={"type": "string", "minLength": 1},
                  canonical_facts_sha256={"type": "string", "pattern": "^[a-f0-9]{64}$"},
                  canonical_review_sha256={"type": "string", "pattern": "^[a-f0-9]{64}$"})
    for key in ("family", "scenario_id", "semantic_parent_id"):
        fields[key] = {"type": "string", "minLength": 1}
    return {"$schema": "https://json-schema.org/draft/2020-12/schema", "$id": "urn:metis:nlu:partition:001", **obj(fields)}


def validate_partition_row(row):
    strict_unicode(row)
    Draft202012Validator(partition_schema()).validate(row)
    validate_prediction({"schema_version": row["nlu_schema_version"], "text": row["text"], "tokens": row["tokens"], "bio_tags": row["bio_tags"],
                         "meaning": {key: row[key] for key in ("domain", "action", "slots", "disposition", "ood")}}, "output")
    if row["partition"] == FINAL and row["source"]["source_class"] != "INDEPENDENT_EVALUATION":
        raise DataError("FINAL_ROW_IS_NOT_INDEPENDENT")


def write_bundle(rows, destination, *, groups=None, embeddings=None, seed=17, require_final=True):
    rows = list(rows)
    assignments, planning = plan(rows, seed)
    leakage, dedupe = validate_build(rows, assignments, groups=groups, embeddings=embeddings, require_final=require_final)
    destination = Path(destination)
    if destination.exists():
        raise DataError("SPLIT_BUNDLE_ALREADY_EXISTS")
    files = {name: jsonl_bytes([projection(row, partition) for row in sorted(rows, key=lambda r: r["id"]) if assignments[row["id"]] == partition])
             for partition, name in FILES.items()}
    files["public_benchmarks.jsonl"] = jsonl_bytes([projection(row, PROTECTED) for row in rows if assignments[row["id"]] == PROTECTED and official(row)])
    files["heldout_neighbors.jsonl"] = jsonl_bytes([projection(row, PROTECTED) for row in rows if assignments[row["id"]] == PROTECTED and not official(row)])
    files["leakage_report.json"] = json_bytes({"planning": planning, "validation": leakage, "dedupe": dedupe})
    files["split_policy.yaml"] = POLICY.read_bytes()
    final_count = sum(value == FINAL for value in assignments.values())
    freeze = {"freeze_version": "nlu_v1_final_freeze_001", "status": "FROZEN" if final_count else "RESERVED_EMPTY_NOT_EVALUATION_READY",
              "rows": final_count, "file": FILES[FINAL], "sha256": hashlib.sha256(files[FILES[FINAL]]).hexdigest(),
              "policy_sha256": sha_json(load_policy()), "input_sha256": collection_hash(rows), "tuning_use": "DENY_ALWAYS"}
    files["final.freeze.json"] = json_bytes(freeze)
    manifest = {"manifest_version": "nlu_v1_split_bundle_001", "status": "COMMITTED" if final_count else "NO_FINAL_NOT_COMPLETE",
                "input_sha256": collection_hash(rows), "seed": seed, "final_ready": bool(final_count),
                "assignments_sha256": sha_json(assignments), "row_counts": {p: sum(v == p for v in assignments.values()) for p in (*TUNING, FINAL, PROTECTED)},
                "files": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}}
    resolved = destination.resolve()
    for protected in (ROOT / "archive/legacy_ml", ROOT / "datasets/legacy", ROOT / "ml/nlu_v1/schema", ROOT / "ml/nlu_v1/schema_versions", ROOT / "app/src"):
        if resolved == protected.resolve() or protected.resolve() in resolved.parents:
            raise DataError("PROTECTED_WRITE_LOCATION")
    # Validation precedes every mutation. Commit marker is last; failures preserve
    # incomplete evidence but never publish a partially valid build as committed.
    destination.mkdir(parents=True, exist_ok=False)
    for name, data in files.items():
        atomic_new(destination / name, data)
    atomic_new(destination / "manifest.json", json_bytes(manifest))
    return manifest


def verify_bundle(root):
    root = Path(root)
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
    expected = set(FILES.values()) | {"public_benchmarks.jsonl", "heldout_neighbors.jsonl", "leakage_report.json", "split_policy.yaml", "final.freeze.json"}
    if manifest.get("manifest_version") != "nlu_v1_split_bundle_001" or manifest.get("status") not in ("COMMITTED", "NO_FINAL_NOT_COMPLETE") or set(manifest.get("files", {})) != expected:
        raise DataError("INVALID_SPLIT_COMMIT_MARKER")
    for name, digest in manifest["files"].items():
        path = root / name
        if path.resolve().parent != root.resolve() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise DataError("FROZEN_SPLIT_FILE_CHANGED")
    protected_count = 0
    for partition, name in [*FILES.items(), (PROTECTED, "public_benchmarks.jsonl"), (PROTECTED, "heldout_neighbors.jsonl")]:
        rows = [json.loads(line, object_pairs_hook=unique_keys) for line in (root / name).read_text(encoding="utf-8").splitlines()]
        if partition == PROTECTED:
            protected_count += len(rows)
        elif len(rows) != manifest["row_counts"][partition]:
            raise DataError("SPLIT_ROW_COUNT_CHANGED")
        for row in rows:
            validate_partition_row(row)
            if row["partition"] != partition:
                raise DataError("ROW_IN_WRONG_PARTITION_FILE")
    if protected_count != manifest["row_counts"][PROTECTED]:
        raise DataError("PUBLIC_BENCHMARK_OR_NEIGHBOR_ROWS_CHANGED")
    freeze = json.loads((root / "final.freeze.json").read_text(encoding="utf-8"))
    policy = yaml.load((root / "split_policy.yaml").read_text(encoding="utf-8"), Loader=UniqueLoader)
    if freeze.get("freeze_version") != "nlu_v1_final_freeze_001" or freeze.get("file") != FILES[FINAL] or freeze.get("input_sha256") != manifest["input_sha256"] or freeze.get("policy_sha256") != sha_json(policy) or freeze.get("status") not in ("FROZEN", "RESERVED_EMPTY_NOT_EVALUATION_READY"):
        raise DataError("FINAL_FREEZE_PROTOCOL_INVALID")
    if freeze["sha256"] != manifest["files"][FILES[FINAL]] or freeze["tuning_use"] != "DENY_ALWAYS" or freeze["rows"] != manifest["row_counts"][FINAL] or manifest["final_ready"] != (freeze["status"] == "FROZEN") or manifest["final_ready"] != (freeze["rows"] > 0) or (manifest["status"] == "COMMITTED") != manifest["final_ready"]:
        raise DataError("FINAL_FREEZE_BINDING_INVALID")
    return manifest


def tuning_rows(root, partition):
    if partition not in TUNING:
        raise DataError("FINAL_AND_PUBLIC_BENCHMARKS_ARE_NOT_TUNING_MATERIAL")
    development_lock(root)
    if (Path(root) / "final.evaluation.started.json").exists():
        raise DataError("FINAL_EVALUATION_STARTED_DEVELOPMENT_CLOSED")
    return [json.loads(line, object_pairs_hook=unique_keys) for line in (Path(root) / FILES[partition]).read_text(encoding="utf-8").splitlines()]


def start_development(root, development_id):
    manifest = verify_bundle(root)
    if not manifest["final_ready"]:
        raise DataError("CANNOT_START_DEVELOPMENT_WITHOUT_FROZEN_FINAL")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,100}", development_id):
        raise DataError("DEVELOPMENT_ID_REQUIRED")
    value = {"status": "DEVELOPMENT_STARTED_FINAL_ISOLATED", "development_id": development_id,
             "split_manifest_sha256": hashlib.sha256((Path(root) / "manifest.json").read_bytes()).hexdigest(),
             "final_freeze_sha256": manifest["files"]["final.freeze.json"]}
    atomic_new(Path(root) / "development.lock.json", json_bytes(value))
    return value


def development_lock(root):
    manifest = verify_bundle(root)
    lock = json.loads((Path(root) / "development.lock.json").read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
    if not manifest["final_ready"] or lock.get("status") != "DEVELOPMENT_STARTED_FINAL_ISOLATED" or lock.get("split_manifest_sha256") != hashlib.sha256((Path(root) / "manifest.json").read_bytes()).hexdigest() or lock.get("final_freeze_sha256") != manifest["files"]["final.freeze.json"]:
        raise DataError("DEVELOPMENT_LOCK_OR_FINAL_FREEZE_CHANGED")
    return lock


def seal_model(root, artifacts):
    lock = development_lock(root)
    if set(artifacts) != {"model", "tokenizer", "configuration"}:
        raise DataError("MODEL_TOKENIZER_CONFIGURATION_REQUIRED")
    seal = {"status": "SEALED_BEFORE_FINAL_ACCESS", "development_id": lock["development_id"],
            "final_freeze_sha256": lock["final_freeze_sha256"],
            "artifacts": {key: hashlib.sha256(Path(path).read_bytes()).hexdigest() for key, path in artifacts.items()}}
    atomic_new(Path(root) / "model.seal.json", json_bytes(seal))
    return seal


def final_evaluation_rows(root, approval, artifacts=None):
    """One explicit release after model sealing; never called by tuning readers."""
    lock = development_lock(root)
    root = Path(root)
    seal_bytes = (root / "model.seal.json").read_bytes()
    seal = json.loads(seal_bytes, object_pairs_hook=unique_keys)
    if seal.get("status") != "SEALED_BEFORE_FINAL_ACCESS" or seal.get("development_id") != lock["development_id"] or seal.get("final_freeze_sha256") != lock["final_freeze_sha256"] or set(seal.get("artifacts", {})) != {"model", "tokenizer", "configuration"} or any(not re.fullmatch(r"[a-f0-9]{64}", digest) for digest in seal["artifacts"].values()):
        raise DataError("MODEL_SEAL_CHANGED")
    if not artifacts or set(artifacts) != set(seal["artifacts"]) or any(hashlib.sha256(Path(path).read_bytes()).hexdigest() != seal["artifacts"][key] for key, path in artifacts.items()):
        raise DataError("EVALUATED_ARTIFACTS_DO_NOT_MATCH_MODEL_SEAL")
    if not approval or approval.get("kind") != "FINAL_EVALUATION" or approval.get("final_freeze_sha256") != lock["final_freeze_sha256"] or approval.get("model_seal_sha256") != hashlib.sha256(seal_bytes).hexdigest() or not approval.get("approval_id") or not approval.get("evaluator_id") or approval["evaluator_id"] == lock["development_id"]:
        raise DataError("INDEPENDENT_FINAL_EVALUATION_APPROVAL_REQUIRED")
    atomic_new(root / "final.evaluation.started.json", json_bytes({"status": "FINAL_ACCESS_CONSUMED", "approval_sha256": sha_json(approval),
        "model_seal_sha256": hashlib.sha256(seal_bytes).hexdigest(), "final_freeze_sha256": lock["final_freeze_sha256"]}))
    return [json.loads(line, object_pairs_hook=unique_keys) for line in (root / FILES[FINAL]).read_text(encoding="utf-8").splitlines()]


def check():
    load_policy()
    from ml.nlu_v1.independent_registry import load_sources
    load_sources()
    if json.loads((Path(__file__).parent / "partition_row.schema.json").read_text(encoding="utf-8")) != partition_schema():
        raise DataError("STALE_PARTITION_ROW_SCHEMA")
    from ml.nlu_v1.context_contract import check as check_contracts
    check_contracts()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--check", action="store_true")
    modes.add_argument("--preview", type=Path)
    modes.add_argument("--build", type=Path)
    modes.add_argument("--verify", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--groups", type=Path)
    parser.add_argument("--embeddings", type=Path)
    parser.add_argument("--seed", type=int, default=17)
    args = parser.parse_args()
    check()
    if args.check:
        print("PASS: grouped split policy and both frozen model contracts")
    elif args.verify:
        print(json.dumps(verify_bundle(args.verify), sort_keys=True))
    elif args.preview:
        _, report = plan(read_jsonl(args.preview), args.seed)
        if args.output:
            atomic_new(args.output, json_bytes(report))
        print(json.dumps(report, sort_keys=True))
    else:
        if not args.output:
            parser.error("--output is required for --build")
        groups = None
        if args.groups:
            snapshot = json.loads(args.groups.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
            groups = snapshot["families"], snapshot["scenarios"]
        embeddings = json.loads(args.embeddings.read_text(encoding="utf-8"), object_pairs_hook=unique_keys) if args.embeddings else None
        print(json.dumps(write_bundle(read_jsonl(args.build), args.output, groups=groups, embeddings=embeddings, seed=args.seed), sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, RecursionError, ValidationError) as error:
        # No jsonschema instance dumps or user utterances in diagnostics.
        code = str(error) if isinstance(error, DataError) and re.fullmatch(r"[A-Z][A-Z0-9_]*", str(error)) else type(error).__name__
        raise SystemExit("FAILED: " + code) from None
