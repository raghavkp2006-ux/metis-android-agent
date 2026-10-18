"""Canonical metadata, evidence-bound review transitions and registry validation."""
import copy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re

from ml.nlu_v1.ingestion.common import REGISTRY, schema as ingestion_schema, validate_row as validate_ingestion
from contract import LABELS, obj, validate as validate_prediction
from jsonschema import Draft202012Validator
from source_registry import ROOT, UniqueLoader
import yaml

VERSION = "nlu_v1_data_001"
HERE = Path(__file__).resolve().parent
PRIVACY = ["PUBLIC", "PRIVATE", "RESTRICTED", "UNKNOWN_PRIVACY"]
STATES = ["PENDING", "AUTO_VALIDATED", "HUMAN_REVIEWED", "APPROVED", "REJECTED", "QUARANTINED"]
PARTITIONS = ["UNASSIGNED", "TRAIN", "DEVELOPMENT", "CALIBRATION", "SELECTION", "PROTECTED_EVALUATION", "FINAL_INDEPENDENT_TEST"]
TRANSITIONS = {"PENDING": {"AUTO_VALIDATED", "REJECTED", "QUARANTINED"},
               "AUTO_VALIDATED": {"HUMAN_REVIEWED", "REJECTED", "QUARANTINED"},
               "HUMAN_REVIEWED": {"APPROVED", "REJECTED", "QUARANTINED"},
               "APPROVED": {"QUARANTINED"}, "QUARANTINED": {"PENDING", "REJECTED"}, "REJECTED": set()}
IDENTIFIER = {"type": "string", "pattern": "^[A-Za-z0-9][A-Za-z0-9_.:/-]{0,199}$"}
SHA = {"type": "string", "pattern": "^[a-f0-9]{64}$"}


class DataError(ValueError):
    """Fixed diagnostic codes, never raw content."""


def sha_json(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode()).hexdigest()


def text_hash(text):
    return hashlib.sha256(text.encode()).hexdigest()


def facts_hash(row):
    return sha_json({key: value for key, value in row.items() if key not in (
        "review_status", "review_history", "quarantine_reasons", "privacy_review")})


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def check_utc(value):
    captured = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if captured.utcoffset() is None or captured.utcoffset().total_seconds() != 0:
        raise DataError("UTC_TIMESTAMP_REQUIRED")


def schema(version=VERSION):
    old = copy.deepcopy(ingestion_schema())
    fields = old["properties"]
    fields["schema_version"] = {"const": VERSION}
    fields.pop("purpose")
    fields.update({
        "privacy": {"enum": PRIVACY}, "review_status": {"enum": STATES},
        "family": {"anyOf": [IDENTIFIER, {"type": "null"}]},
        "scenario_id": {"anyOf": [IDENTIFIER, {"type": "null"}]},
        "semantic_parent_id": {"anyOf": [IDENTIFIER, {"type": "null"}]},
        "partition": {"enum": PARTITIONS}, "text_sha256": SHA,
        "family_registry_version": {"const": "nlu_v1_families_001"},
        "scenario_registry_version": {"const": "nlu_v1_scenarios_001"},
        "generation": {"anyOf": [{"type": "null"}, obj({
            "parent_ids": {"type": "array", "items": SHA, "uniqueItems": True, "maxItems": 64},
            "seed": {"type": "integer", "minimum": 0}, "generator_id": IDENTIFIER, "template_id": IDENTIFIER,
        })]},
        "quarantine_reasons": {"type": "array", "items": {"type": "string", "pattern": "^[A-Z][A-Z0-9_]*$"}, "uniqueItems": True},
        "privacy_review": {"anyOf": [{"type": "null"}, obj({
            "binding_sha256": SHA, "review_id": IDENTIFIER, "reviewer_id": IDENTIFIER,
            "reviewed_at": {"type": "string"}, "publication_allowed": {"type": "boolean"}, "license_id": IDENTIFIER,
        })]},
        "provenance": obj({"ingestion_row_sha256": SHA, "import_batch_id": IDENTIFIER,
            "import_row_number": {"type": "integer", "minimum": 1},
            "import_file_sha256": {"anyOf": [SHA, {"type": "null"}]},
            "mapping_configuration_sha256": SHA, "source_registry_sha256": SHA,
            "family_definition_sha256": {"anyOf": [SHA, {"type": "null"}]},
            "scenario_definition_sha256": {"anyOf": [SHA, {"type": "null"}]},
            "source_registry_version": {"const": "nlu_v1_sources_001"}, "upstream_privacy": {"type": "string", "minLength": 1}}),
        "review_history": {"type": "array", "minItems": 1, "items": obj({
            "previous": {"anyOf": [{"enum": STATES}, {"type": "null"}]}, "next": {"enum": STATES},
            "actor": IDENTIFIER, "reason": {"type": "string", "pattern": "^[A-Z][A-Z0-9_]*$"},
            "at": {"type": "string"}, "binding_sha256": SHA,
            "review_id": {"anyOf": [IDENTIFIER, {"type": "null"}]},
            "evidence_sha256": {"anyOf": [SHA, {"type": "null"}]},
            "previous_event_sha256": {"anyOf": [SHA, {"type": "null"}]}, "event_sha256": SHA,
        })},
    })
    source = fields["source"]
    source["properties"]["source_class"] = {"enum": LABELS["source_classes"]}
    source["required"].append("source_class")
    result = obj(fields)
    result.update({"$schema": old["$schema"], "$id": "urn:metis:nlu:data:001", "allOf": old["allOf"]})
    if version == "nlu_v1_data_002":
        from ml.nlu_v1.context_contract import build_schemas
        result["$id"] = "urn:metis:nlu:data:002"
        fields["schema_version"] = {"const": version}
        fields["nlu_schema_version"] = {"const": "nlu_v1_schema_002"}
        result["allOf"] = build_schemas()["output.schema.json"]["properties"]["meaning"]["allOf"]
        fields["provenance"]["properties"]["source_registry_version"] = {"const": "nlu_v1_synthetic_sources_001"}
    elif version != VERSION:
        raise DataError("UNKNOWN_MANAGED_SCHEMA_VERSION")
    return result


def load_groups():
    families = yaml.load((ROOT / "ml/nlu_v1/configs/families.yaml").read_text(encoding="utf-8"), Loader=UniqueLoader)
    scenarios = yaml.load((ROOT / "ml/nlu_v1/configs/scenarios.yaml").read_text(encoding="utf-8"), Loader=UniqueLoader)
    validate_groups(families, scenarios)
    return families, scenarios


def validate_groups(families, scenarios):
    if families.get("registry_version") != "nlu_v1_families_001" or scenarios.get("registry_version") != "nlu_v1_scenarios_001":
        raise DataError("GROUP_REGISTRY_VERSION_MISMATCH")
    for key, value in families["families"].items():
        if not re.fullmatch(IDENTIFIER["pattern"], key) or value["domain"] not in LABELS["domains"] or value["action"] not in LABELS["actions"]:
            raise DataError("INVALID_FAMILY_DEFINITION")
        if set(value) - {"domain", "action", "any_slots", "required_slots", "forbidden_slots", "dispositions"}:
            raise DataError("UNKNOWN_FAMILY_CONSTRAINT")
        for field in ("any_slots", "required_slots", "forbidden_slots"):
            if not set(value.get(field, [])) <= set(LABELS["slots"]):
                raise DataError("UNKNOWN_FAMILY_SLOT")
        if not set(value.get("dispositions", [])) <= set(LABELS["dispositions"]):
            raise DataError("UNKNOWN_FAMILY_DISPOSITION")
    for key, value in scenarios["scenarios"].items():
        if set(value) != {"family", "semantic_parent_id", "description"} or value["family"] not in families["families"]:
            raise DataError("INVALID_SCENARIO_DEFINITION")
        if not re.fullmatch(IDENTIFIER["pattern"], key) or not re.fullmatch(IDENTIFIER["pattern"], value["semantic_parent_id"]) or not value["description"]:
            raise DataError("OPAQUE_SCENARIO_AND_PARENT_IDS_REQUIRED")


def register_scenario(scenarios, families, scenario_id, family, semantic_parent_id, description):
    result = copy.deepcopy(scenarios)
    if scenario_id in result["scenarios"]:
        raise DataError("SCENARIO_ID_ALREADY_REGISTERED")
    result["scenarios"][scenario_id] = dict(family=family, semantic_parent_id=semantic_parent_id, description=description)
    validate_groups(families, result)
    return result


def family_matches(row, definition):
    slots = {slot["type"] for slot in row["slots"]}
    return set(definition.get("required_slots", [])) <= slots and row["domain"] == definition["domain"] and row["action"] == definition["action"] and (
        not definition.get("any_slots") or bool(slots & set(definition["any_slots"]))) and not (
        slots & set(definition.get("forbidden_slots", []))) and (
        not definition.get("dispositions") or row["disposition"] in definition["dispositions"])


def append_event(row, previous, target, actor, reason, evidence=None):
    event = {"previous": previous, "next": target, "actor": actor, "reason": reason, "at": utc_now(),
             "binding_sha256": facts_hash(row),
             "review_id": evidence.get("review_id") if evidence else None,
             "evidence_sha256": sha_json(evidence) if evidence else None,
             "previous_event_sha256": row["review_history"][-1]["event_sha256"] if row["review_history"] else None}
    event["event_sha256"] = sha_json(event)
    row["review_history"].append(event)
    row["review_status"] = target


def migrate_ingestion(row, *, batch_id, row_number, import_file_sha256=None):
    validate_ingestion(row)
    result = copy.deepcopy(row)
    result["schema_version"] = VERSION
    result.pop("purpose")
    upstream_privacy = result["privacy"]
    # Licensing or a synthetic claim alone never establishes publication privacy.
    result["privacy"] = {"PRIVATE_DO_NOT_PUBLISH": "PRIVATE", "CONSENTED_REDACTED": "RESTRICTED"}.get(upstream_privacy, "UNKNOWN_PRIVACY")
    result.update(family=None, scenario_id=None, semantic_parent_id=None, generation=None,
                  review_status="PENDING", review_history=[], privacy_review=None, quarantine_reasons=[],
                  text_sha256=text_hash(row["text"]), family_registry_version="nlu_v1_families_001", scenario_registry_version="nlu_v1_scenarios_001")
    source = result["source"]
    source["source_class"] = "TRAINABLE_LEGACY" if source["dataset"] == "metis_trainable_legacy" else "PUBLIC_DATASET"
    allowed = REGISTRY[source["dataset"]]["allowed_splits"].get(source["original_split"], [])
    result["partition"] = "PROTECTED_EVALUATION" if allowed and all("BENCHMARK" in p or p == "SMOKE_TEST" for p in allowed) else "UNASSIGNED"
    mapping_names = {"massive_1_1_en_us": "massive", "hwu_nlu_evaluation": "hwu", "mtop_en": "mtop", "clinc150_full": "clinc", "metis_trainable_legacy": "legacy"}
    mapping = ROOT / "ml/nlu_v1/ingestion/mappings" / (mapping_names[source["dataset"]] + "_intent_mapping.yaml")
    result["provenance"] = {"ingestion_row_sha256": sha_json(row), "import_batch_id": batch_id,
        "import_row_number": row_number, "import_file_sha256": import_file_sha256,
        "mapping_configuration_sha256": hashlib.sha256(mapping.read_bytes().replace(b"\r\n", b"\n")).hexdigest(),
        "source_registry_sha256": hashlib.sha256((ROOT / "ml/nlu_v1/configs/dataset_sources.yaml").read_bytes().replace(b"\r\n", b"\n")).hexdigest(),
        "source_registry_version": "nlu_v1_sources_001", "upstream_privacy": upstream_privacy}
    result["provenance"].update(family_definition_sha256=None, scenario_definition_sha256=None)
    append_event(result, None, "PENDING", "import:nlu_v1_data_001", "INGESTION_IMPORTED")
    validate_record(result)
    return result


def validate_record(row):
    if not isinstance(row, dict):
        raise DataError("MALFORMED_CANONICAL_ROW")
    Draft202012Validator(schema(row.get("schema_version"))).validate(row)
    validate_prediction({"schema_version": row["nlu_schema_version"], "text": row["text"], "tokens": row["tokens"], "bio_tags": row["bio_tags"],
        "meaning": {k: row[k] for k in ("domain", "action", "slots", "disposition", "ood")}}, "output")
    if row["text_sha256"] != text_hash(row["text"]):
        raise DataError("EXACT_TEXT_HASH_MISMATCH")
    source = row["source"]
    expected_id = hashlib.sha256(json.dumps([source[k] for k in ("dataset", "version", "original_split", "source_id")], separators=(",", ":")).encode()).hexdigest()
    if row["id"] != expected_id:
        raise DataError("ROW_SOURCE_ID_MISMATCH")
    if source["source_class"] == "CONTROLLED_SYNTHETIC":
        if row["generation"] is None:
            raise DataError("SYNTHETIC_PARENT_SEED_GENERATOR_REQUIRED")
    elif row["generation"] is not None:
        raise DataError("GENERATION_REQUIRES_SYNTHETIC_SOURCE")
    if row["generation"] and row["id"] in row["generation"]["parent_ids"]:
        raise DataError("SELF_GENERATION_PARENT")
    history = row["review_history"]
    previous_state, previous_hash = None, None
    for event in history:
        check_utc(event["at"])
        if event["previous"] != previous_state or event["previous_event_sha256"] != previous_hash or sha_json({k: v for k, v in event.items() if k != "event_sha256"}) != event["event_sha256"]:
            raise DataError("REVIEW_HISTORY_CHAIN_INVALID")
        if previous_state is None and event["next"] != "PENDING":
            raise DataError("REVIEW_MUST_START_PENDING")
        if previous_state is not None and event["next"] != previous_state and event["next"] not in TRANSITIONS[previous_state]:
            raise DataError("ILLEGAL_REVIEW_TRANSITION")
        previous_state, previous_hash = event["next"], event["event_sha256"]
    if previous_state != row["review_status"] or history[-1]["binding_sha256"] != facts_hash(row):
        raise DataError("REVIEW_NOT_BOUND_TO_CURRENT_FACTS")
    if row["privacy_review"]:
        check_utc(row["privacy_review"]["reviewed_at"])
        if row["privacy_review"]["binding_sha256"] != facts_hash(row):
            raise DataError("PRIVACY_REVIEW_STALE")
        classifications = [event for event in history if event["reason"] == "PRIVACY_CLASSIFIED"]
        if not classifications or classifications[-1]["evidence_sha256"] != sha_json(row["privacy_review"]):
            raise DataError("PRIVACY_REVIEW_EVIDENCE_MODIFIED")
    if row["review_status"] in ("AUTO_VALIDATED", "HUMAN_REVIEWED", "APPROVED"):
        if row["privacy"] == "UNKNOWN_PRIVACY" or not row["privacy_review"] or not row["family"] or not row["scenario_id"] or not row["semantic_parent_id"] or row["quarantine_reasons"]:
            raise DataError("REVIEWED_RECORD_METADATA_INCOMPLETE")
    registered = registered_source(source["dataset"], row["schema_version"])
    if registered:
        if source["version"] != registered["version"]:
            raise DataError("SOURCE_VERSION_MISMATCH")
        purposes = registered["allowed_splits"].get(source["original_split"], [])
        if row["partition"] in ("TRAIN", "DEVELOPMENT", "CALIBRATION", "SELECTION") and row["partition"] not in purposes:
            raise DataError("PROTECTED_SOURCE_PARTITION_PROMOTION")
        independent = registered.get("independent_evaluation") is True and registered.get("source_class") == "INDEPENDENT_EVALUATION" and source["source_class"] == "INDEPENDENT_EVALUATION"
        if row["partition"] == "FINAL_INDEPENDENT_TEST" and not (independent and "FINAL_INDEPENDENT_TEST" in purposes):
            raise DataError("REGISTERED_SOURCE_IS_NOT_FRESH_INDEPENDENT_TEST")
    return row


def registered_source(dataset, schema_version=VERSION):
    if dataset == "metis_controlled_synthetic" and schema_version == "nlu_v1_data_002":
        from ml.nlu_v1.synthetic_registry import load_source
        return load_source()
    if dataset in REGISTRY:
        return REGISTRY[dataset]
    from ml.nlu_v1.independent_registry import load_sources
    return load_sources().get(dataset)


def canonical_issues(row, groups=None):
    validate_record(row)
    families, scenarios = groups or load_groups()
    validate_groups(families, scenarios)
    issues = []
    source = row["source"]
    registered = registered_source(source["dataset"], row["schema_version"])
    if not registered or registered["metis_status"] != "REGISTERED_PENDING_INTAKE":
        issues.append("SOURCE_NOT_APPROVED_FOR_CANONICAL_USE")
    expected_class = {"metis_trainable_legacy": "TRAINABLE_LEGACY", "metis_controlled_synthetic": "CONTROLLED_SYNTHETIC",
                      "metis_independent_evaluation": "INDEPENDENT_EVALUATION"}.get(source["dataset"], "PUBLIC_DATASET")
    if registered and source["source_class"] != expected_class:
        if not (registered.get("independent_evaluation") is True and source["source_class"] == registered.get("source_class") == "INDEPENDENT_EVALUATION"):
            issues.append("SOURCE_CLASS_MISMATCH")
    if registered and registered.get("independent_evaluation") is True:
        from ml.nlu_v1.independent_registry import VERSION as independent_version, registry_hash
        independence = row["metadata"].get("independence", {})
        if independence.get("registry_version") != independent_version or independence.get("registry_sha256") != registry_hash() or independence.get("protocol_sha256") != registered["collection_protocol_sha256"]:
            issues.append("INDEPENDENT_REGISTRY_OR_PROTOCOL_UNBOUND")
        if source["artifact_sha256"] != registered["download"]["sha256"] or independence.get("collector_id") != registered["collector_id"] or independence.get("reviewer_id") != registered["reviewer_id"] or independence.get("development_exposed") is not False:
            issues.append("INDEPENDENT_INTAKE_OR_REVIEW_UNBOUND")
        if row["review_status"] in ("HUMAN_REVIEWED", "APPROVED") and not any(event["next"] == "HUMAN_REVIEWED" and event["actor"] == registered["reviewer_id"] for event in row["review_history"]):
            issues.append("INDEPENDENT_HUMAN_REVIEWER_NOT_BOUND")
    if not source["artifact_sha256"] or not source["intake_approval_id"] or not row["provenance"]["import_file_sha256"]:
        issues.append("SOURCE_ARTIFACT_UNVERIFIED")
    if row["privacy"] == "UNKNOWN_PRIVACY":
        issues.append("PRIVACY_UNRESOLVED")
    if not row["privacy_review"]:
        issues.append("PRIVACY_REVIEW_REQUIRED")
    elif registered and row["privacy_review"]["license_id"] != registered["license"]["id"]:
        issues.append("PRIVACY_LICENSE_EVIDENCE_MISMATCH")
    family, scenario = families["families"].get(row["family"]), scenarios["scenarios"].get(row["scenario_id"])
    if not family or not family_matches(row, family):
        issues.append("FAMILY_UNRESOLVED_OR_INCOMPATIBLE")
    if not scenario or scenario["family"] != row["family"] or scenario["semantic_parent_id"] != row["semantic_parent_id"]:
        issues.append("SCENARIO_PARENT_UNRESOLVED_OR_INCOMPATIBLE")
    if family and row["provenance"]["family_definition_sha256"] != sha_json(family):
        issues.append("FAMILY_DEFINITION_CHANGED")
    if scenario and row["provenance"]["scenario_definition_sha256"] != sha_json(scenario):
        issues.append("SCENARIO_DEFINITION_CHANGED")
    return issues


def assign_group(row, family, scenario_id, *, groups):
    validate_record(row)
    families, scenarios = groups
    validate_groups(families, scenarios)
    scenario = scenarios["scenarios"].get(scenario_id)
    if family not in families["families"] or not family_matches(row, families["families"][family]) or not scenario or scenario["family"] != family:
        raise DataError("GROUP_ASSIGNMENT_INCOMPATIBLE")
    if row["review_status"] not in ("PENDING", "QUARANTINED"):
        raise DataError("REOPEN_BEFORE_FACT_CHANGES")
    result = copy.deepcopy(row)
    result.update(family=family, scenario_id=scenario_id, semantic_parent_id=scenario["semantic_parent_id"], privacy_review=None)
    result["provenance"].update(family_definition_sha256=sha_json(families["families"][family]), scenario_definition_sha256=sha_json(scenario))
    append_event(result, row["review_status"], row["review_status"], "group:nlu_v1_data_001", "GROUP_ASSIGNED")
    validate_record(result)
    return result


def classify_privacy(row, privacy, evidence):
    validate_record(row)
    if privacy not in PRIVACY or row["review_status"] not in ("PENDING", "QUARANTINED"):
        raise DataError("PRIVACY_CHANGE_NOT_ALLOWED")
    result = copy.deepcopy(row)
    result["privacy"] = privacy
    if evidence.get("binding_sha256") != facts_hash(result):
        raise DataError("PRIVACY_EVIDENCE_NOT_BOUND")
    result["privacy_review"] = copy.deepcopy(evidence)
    append_event(result, row["review_status"], row["review_status"], evidence["reviewer_id"], "PRIVACY_CLASSIFIED", evidence)
    validate_record(result)
    return result


def transition(row, target, *, actor, reason, evidence=None, groups=None):
    validate_record(row)
    previous = row["review_status"]
    if target not in TRANSITIONS[previous]:
        raise DataError("ILLEGAL_REVIEW_TRANSITION")
    if target in ("HUMAN_REVIEWED", "APPROVED") or (previous == "QUARANTINED" and target == "PENDING"):
        if not evidence or evidence.get("kind") != "HUMAN_REVIEW" or evidence.get("binding_sha256") != facts_hash(row) or evidence.get("reviewer_id") != actor or not evidence.get("review_id"):
            raise DataError("BOUND_HUMAN_REVIEW_REQUIRED")
        if evidence.get("state_event_sha256") != row["review_history"][-1]["event_sha256"]:
            raise DataError("REVIEW_STATE_EVIDENCE_STALE")
        if previous == "QUARANTINED" and evidence.get("quarantine_sha256") != sha_json(row["quarantine_reasons"]):
            raise DataError("QUARANTINE_RELEASE_EVIDENCE_STALE")
    if target in ("AUTO_VALIDATED", "HUMAN_REVIEWED", "APPROVED") and (canonical_issues(row, groups) or row["quarantine_reasons"]):
        raise DataError("UNRESOLVED_CANONICAL_ISSUES")
    result = copy.deepcopy(row)
    if previous == "QUARANTINED" and target == "PENDING":
        # Release is explicit, evidence-bound, and recorded; re-audit still required.
        result["quarantine_reasons"] = []
    append_event(result, previous, target, actor, reason, evidence)
    validate_record(result)
    return result


def quarantine(row, reasons):
    validate_record(row)
    result = copy.deepcopy(row)
    result["quarantine_reasons"] = sorted(set(row["quarantine_reasons"]) | set(reasons))
    if row["review_status"] == "REJECTED":
        append_event(result, "REJECTED", "REJECTED", "audit:nlu_v1_data_001", "AUDIT_RECORDED")
    else:
        append_event(result, row["review_status"], "QUARANTINED", "audit:nlu_v1_data_001", "AUDIT_QUARANTINED")
    validate_record(result)
    return result
