"""Offline registry checks only. No downloads, ingestion, mapping or training."""
import json
from pathlib import Path
import re

import yaml

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = ROOT / "ml/nlu_v1/configs/dataset_sources.yaml"
EXPECTED_IDS = {
    "massive_1_1_en_us", "hwu_nlu_evaluation", "mtop_en", "clinc150_full", "metis_trainable_legacy",
    "sgd", "sgd_x", "slurp_text", "slurp_audio", "snips_custom_intents", "banking77",
}
TRAIN_PURPOSES = {"TRAIN", "DEVELOPMENT", "CALIBRATION", "SELECTION"}
PURPOSES = TRAIN_PURPOSES | {"BENCHMARK_DEVELOPMENT", "BENCHMARK_TEST", "OOD_BENCHMARK_TEST", "SMOKE_TEST"}
SOURCE_FIELDS = {
    "id", "tier", "name", "version", "locale", "repository", "revision", "source_url", "license",
    "download", "identity_verified", "split_provenance", "allowed_splits", "intended_use", "privacy_class",
    "redistribution_rules", "citation", "preserve_metadata", "leakage_group", "metis_status", "blockers",
}
STATUSES = {"REGISTERED_PENDING_INTAKE", "BLOCKED_SPLIT_PROVENANCE", "BLOCKED_LICENSE", "QUARANTINED", "DEFERRED", "BLOCKED_PURPOSE"}


class UniqueLoader(yaml.SafeLoader):
    pass


def unique_mapping(loader, node):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node)
        if key in result:
            raise ValueError("Duplicate registry key")
        result[key] = loader.construct_object(value_node)
    return result


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)


def validate_registry(value):
    if not isinstance(value, dict) or value.get("registry_version") != "nlu_v1_sources_001" or value.get("schema_version") != "nlu_v1_schema_001":
        raise ValueError("Registry/schema version missing or unsupported")
    if value.get("stage") != "REGISTRY_ONLY":
        raise ValueError("Plan 02 is registry-only")
    policy = value["policy"]
    required_policy = {
        "default_use": "DENY", "unclear_license_identity_or_split": "BLOCK",
        "payload_downloaded": False, "training_authorized": False,
        "final_independent_test_from_public_sources": False,
        "mapping_decisions": ["MAP", "KNOWN_UNSUPPORTED", "OOD", "DROP"],
        "unmapped_intent": "BLOCK", "hash_algorithm": "SHA-256",
        "hash_mismatch": "QUARANTINE", "missing_hash": "BLOCK_USE", "unknown_split": "QUARANTINE",
        "denied_legacy_classes": ["EXPOSED", "PROTECTED_EVALUATION", "PRIVATE_DO_NOT_PUBLISH"],
    }
    for key, expected in required_policy.items():
        if type(policy.get(key)) is not type(expected) or policy[key] != expected:
            raise ValueError("Unsafe registry policy: " + key)
    required_manifest = {"requested_url", "resolved_url", "source_revision", "downloaded_at_utc", "archive_sha256",
                         "archive_size_bytes", "member_path", "member_sha256", "license_sha256", "original_split",
                         "locale", "mapper_version", "approval_id"}
    if not required_manifest <= set(policy.get("intake_manifest_required", [])) or not policy.get("hash_scope") or not policy.get("publication"):
        raise ValueError("Hashing/intake/publication policy missing")
    sources = value["sources"]
    ids = [s["id"] for s in sources]
    if len(ids) != len(set(ids)) or set(ids) != EXPECTED_IDS:
        raise ValueError("Missing, duplicate or unexpected source")
    for source in sources:
        if not SOURCE_FIELDS <= source.keys() or source.keys() - SOURCE_FIELDS - {"official_landing_url", "official_short_url"}:
            raise ValueError("Malformed source record")
        for key in ("name", "version", "source_url", "intended_use", "privacy_class", "redistribution_rules", "split_provenance", "leakage_group"):
            if not isinstance(source[key], str) or not source[key].strip():
                raise ValueError("Missing source documentation: " + key)
        if source["metis_status"] not in STATUSES or source["tier"] not in ("PRIMARY", "SECONDARY"):
            raise ValueError("Unknown METIS status/tier")
        if source["tier"] != ("PRIMARY" if source["id"] in {"massive_1_1_en_us", "hwu_nlu_evaluation", "mtop_en", "clinc150_full", "metis_trainable_legacy"} else "SECONDARY"):
            raise ValueError("Unexpected source tier")
        if type(source["identity_verified"]) is not bool:
            raise ValueError("Identity verification must be explicit")
        if source["revision"] is not None and not re.fullmatch(r"[a-f0-9]{40}", source["revision"]):
            raise ValueError("Invalid source revision")
        license_info = source["license"]
        if set(license_info) != {"id", "verification", "evidence_url", "note"}:
            raise ValueError("Missing license evidence")
        if license_info["verification"] not in {"VERIFIED_METADATA", "UNVERIFIED", "PER_ROW_APPROVAL_REQUIRED"}:
            raise ValueError("Unknown license verification")
        if license_info["verification"] == "VERIFIED_METADATA" and not license_info["id"]:
            raise ValueError("Verified license requires identity")
        if license_info["verification"] == "UNVERIFIED" and source["metis_status"] != "BLOCKED_LICENSE":
            raise ValueError("Unverified license must block use")
        if not source["identity_verified"] and not source["metis_status"].startswith("BLOCKED_"):
            raise ValueError("Unverified identity must block use")
        if "UNRESOLVED" in source["split_provenance"] and source["metis_status"] != "BLOCKED_SPLIT_PROVENANCE":
            raise ValueError("Unclear split provenance must block use")
        download = source["download"]
        if set(download) != {"sha256", "status"} or download["status"] not in {"NOT_DOWNLOADED", "LOCAL_CATALOG_ONLY"} or download["sha256"] is not None:
            raise ValueError("Plan 02 must not fabricate payload hashes or download state")
        if not isinstance(source["allowed_splits"], dict):
            raise ValueError("Split allowlist missing")
        for split, purposes in source["allowed_splits"].items():
            if not isinstance(purposes, list) or len(purposes) != len(set(purposes)) or not set(purposes) <= PURPOSES:
                raise ValueError("Invalid split purposes")
            if any(x in split.lower() for x in ("test", "dev", "eval", "val")) and set(purposes) & TRAIN_PURPOSES:
                raise ValueError("Protected source split cannot feed development/tuning/training partitions")
        if not source["citation"] or any(set(c) != {"title", "url"} or not c["title"] or not c["url"] for c in source["citation"]):
            raise ValueError("Citation missing")
        if not source["preserve_metadata"] or not source["blockers"]:
            raise ValueError("Metadata preservation and blockers must be explicit")
    indexed = {s["id"]: s for s in sources}
    legacy = indexed["metis_trainable_legacy"]
    if legacy["metis_status"] != "QUARANTINED" or set(legacy["allowed_splits"]["TRAINABLE_LEGACY"]) != TRAIN_PURPOSES:
        raise ValueError("Legacy must remain quarantined with per-row training classification")
    if any(legacy["allowed_splits"].get(label) != [] for label in policy["denied_legacy_classes"]):
        raise ValueError("Protected/private/exposed legacy reuse prohibited")
    if indexed["slurp_audio"]["allowed_splits"] or indexed["slurp_audio"]["license"]["id"] != "CC-BY-NC-4.0":
        raise ValueError("SLURP audio requires separate purpose approval")
    if any(p != "SMOKE_TEST" for ps in indexed["snips_custom_intents"]["allowed_splits"].values() for p in ps):
        raise ValueError("SNIPS is smoke-test-only")
    if indexed["clinc150_full"]["allowed_splits"].get("oos_test") != ["OOD_BENCHMARK_TEST"]:
        raise ValueError("OOS test must stay protected for OOD evaluation")
    if indexed["banking77"]["allowed_splits"].get("test") != ["OOD_BENCHMARK_TEST"] or "OOD-only" not in indexed["banking77"]["intended_use"]:
        raise ValueError("Banking77 is OOD-only")


def load_registry(path=REGISTRY):
    value = yaml.load(path.read_text(encoding="utf-8"), Loader=UniqueLoader)
    validate_registry(value)
    return value


def require_use(registry, source_id, source_split, purpose):
    """Deny actual use at Plan 02; planned permissions are not intake approval."""
    validate_registry(registry)
    source = next((s for s in registry["sources"] if s["id"] == source_id), None)
    if source is None or purpose not in source["allowed_splits"].get(source_split, []):
        raise ValueError("Source/split/purpose not allowed")
    raise ValueError("Dataset use blocked: registry-only; hash, mapping, privacy and isolation gates pending")


def check_evidence(registry):
    evidence_path = ROOT / registry["metadata_evidence"]
    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    pinned = {record["repository"]: record for record in evidence["sources"].values()}
    for source in registry["sources"]:
        if source["revision"] is None:
            continue
        record = pinned[source["repository"]]
        if record["revision"] != source["revision"]:
            raise ValueError("Metadata revision mismatch")
        for item in record["metadata"]:
            if source["revision"] not in item["url"] or not re.fullmatch(r"[a-f0-9]{64}", item["sha256"]):
                raise ValueError("Metadata evidence is not pinned/hashed")


if __name__ == "__main__":
    registry = load_registry()
    check_evidence(registry)
    print(f"PASS: {len(registry['sources'])} registered source records; all dataset use blocked pending intake gates")
