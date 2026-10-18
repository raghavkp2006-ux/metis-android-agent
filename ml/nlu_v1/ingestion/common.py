"""Pure source conversion and strict ingestion envelope; no action dispatch."""
import copy
import argparse
from functools import lru_cache
import hashlib
import json
from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from contract import LABELS, build_schemas, obj, unique_keys, validate as validate_prediction, utf16_boundaries, verify_freeze
from jsonschema import Draft202012Validator, ValidationError
from source_registry import UniqueLoader, load_registry
import yaml

HERE = Path(__file__).resolve().parent
VERSION = "nlu_v1_ingest_001"
MAPPER_VERSION = "nlu_v1_mappers_001"
REGISTRY = {s["id"]: s for s in load_registry()["sources"]}


class MappingError(ValueError):
    """Messages are fixed codes; never include source utterances."""


def digest(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def u16(text, offset):
    return len(text[:offset].encode("utf-16-le")) // 2


def source_split(raw, supplied=None, field="split"):
    embedded = raw.get(field)
    if supplied is not None and embedded is not None and supplied != embedded:
        raise MappingError("SPLIT_DISAGREEMENT")
    result = embedded if embedded is not None else supplied
    if not isinstance(result, str) or not result:
        raise MappingError("ORIGINAL_SPLIT_REQUIRED")
    return result


@lru_cache(maxsize=5)
def load_mapping(name):
    value = yaml.load((HERE / "mappings" / name).read_text(encoding="utf-8"), Loader=UniqueLoader)
    if value["schema_version"] != LABELS["schema_version"] or not value["mapping_version"]:
        raise MappingError("MAPPING_VERSION_MISMATCH")
    return value


def target(table, intent):
    if intent not in table["intents"]:
        raise MappingError("UNMAPPED_INTENT")
    entry = copy.deepcopy(table["intents"][intent])
    if entry["decision"] == "DROP":
        return None
    return entry


def annotated_slots(text, annotation, slot_mapping):
    """Decode bracket markup while proving exact reconstruction, without search."""
    if not isinstance(text, str) or not isinstance(annotation, str):
        raise MappingError("ANNOTATION_REQUIRED")
    pieces, slots, cursor, length = [], [], 0, 0
    pattern = re.compile(r"\[([A-Za-z0-9_]+)\s*:\s*([^\[\]]+)\]")
    for match in pattern.finditer(annotation):
        prefix = annotation[cursor:match.start()]
        if "[" in prefix or "]" in prefix:
            raise MappingError("MALFORMED_MARKUP")
        pieces.append(prefix)
        length += len(prefix)
        name, value = match.group(1, 2)
        if name not in slot_mapping:
            raise MappingError("UNMAPPED_SLOT")
        mapped = slot_mapping[name]
        if mapped is not None:
            slots.append({"type": mapped, "start": u16(text, length),
                          "end": u16(text, length + len(value)), "text": value})
        pieces.append(value)
        length += len(value)
        cursor = match.end()
    suffix = annotation[cursor:]
    if "[" in suffix or "]" in suffix:
        raise MappingError("MALFORMED_MARKUP")
    pieces.append(suffix)
    if "".join(pieces) != text:
        raise MappingError("ANNOTATION_TEXT_MISMATCH")
    return slots


def tokenize(text, slots):
    boundaries = utf16_boundaries(text)
    cuts = {0, len(text)}
    for slot in slots:
        if slot["start"] not in boundaries or slot["end"] not in boundaries or slot["end"] <= slot["start"]:
            raise MappingError("INVALID_SLOT_BOUNDARY")
        cuts.update((boundaries[slot["start"]], boundaries[slot["end"]]))
    tokens, tags = [], []
    ordered = sorted(cuts)
    seen = set()
    for left, right in zip(ordered, ordered[1:]):
        for match in re.finditer(r"\S+", text[left:right]):
            start, end = u16(text, left + match.start()), u16(text, left + match.end())
            token = {"start": start, "end": end, "text": match.group()}
            tokens.append(token)
            owners = [i for i, slot in enumerate(slots) if slot["start"] <= start and end <= slot["end"]]
            if len(owners) > 1:
                raise MappingError("OVERLAPPING_SLOTS")
            if owners:
                owner = owners[0]
                tags.append(("I-" if owner in seen else "B-") + slots[owner]["type"])
                seen.add(owner)
            else:
                tags.append("O")
    return tokens, tags


def schema():
    prediction = build_schemas()["output.schema.json"]
    meaning = prediction["properties"]["meaning"]
    source = obj({key: {"type": "string", "minLength": 1} for key in (
        "dataset", "version", "source_id", "original_split", "original_intent", "mapper_version", "mapping_version")})
    source["properties"]["mapping_decision"] = {"enum": ["MAP", "KNOWN_UNSUPPORTED", "OOD"]}
    source["required"].append("mapping_decision")
    source["properties"]["artifact_sha256"] = {"anyOf": [{"type": "string", "pattern": "^[a-f0-9]{64}$"}, {"type": "null"}]}
    source["properties"]["intake_approval_id"] = {"type": ["string", "null"]}
    source["required"].extend(["artifact_sha256", "intake_approval_id"])
    fields = {
        "schema_version": {"const": VERSION}, "nlu_schema_version": {"const": LABELS["schema_version"]},
        "id": {"type": "string", "pattern": "^[a-f0-9]{64}$"},
        "text": prediction["properties"]["text"], "tokens": prediction["properties"]["tokens"],
        "bio_tags": prediction["properties"]["bio_tags"], **meaning["properties"],
        "family": {"type": "string", "minLength": 1}, "source": source,
        "privacy": {"enum": LABELS["privacy"]},
        "review_status": {"enum": ["PENDING_REVIEW", "APPROVED", "REJECTED"]},
        "purpose": {"const": "QUARANTINE"},
        "metadata": {"type": "object"},
        "composition": {"type": ["object", "null"]},
    }
    result = obj(fields)
    result.update({"$schema": "https://json-schema.org/draft/2020-12/schema", "$id": "urn:metis:nlu:ingest:001",
                   "allOf": meaning["allOf"]})
    return result


def validate_row(row):
    Draft202012Validator(schema()).validate(row)
    envelope = {"schema_version": LABELS["schema_version"], "text": row["text"],
                "tokens": row["tokens"], "bio_tags": row["bio_tags"],
                "meaning": {key: row[key] for key in ("domain", "action", "slots", "disposition", "ood")}}
    validate_prediction(envelope, "output")
    source = row["source"]
    expected_disposition = {"OOD": "OUT_OF_DOMAIN", "KNOWN_UNSUPPORTED": "KNOWN_UNSUPPORTED"}.get(source["mapping_decision"])
    if expected_disposition is not None and row["disposition"] != expected_disposition:
        raise MappingError("MAPPING_DECISION_DISAGREEMENT")
    if source["mapping_decision"] == "MAP" and not row["disposition"].startswith("SUPPORTED_"):
        raise MappingError("MAP_REQUIRES_SUPPORTED_DISPOSITION")
    if source["dataset"] not in REGISTRY or source["version"] != REGISTRY[source["dataset"]]["version"]:
        raise MappingError("SOURCE_IDENTITY_MISMATCH")
    expected_id = digest(json.dumps([source[key] for key in ("dataset", "version", "original_split", "source_id")], separators=(",", ":")))
    if row["id"] != expected_id:
        raise MappingError("SOURCE_ID_MISMATCH")
    if row["privacy"] == "PRIVATE_DO_NOT_PUBLISH":
        raise MappingError("PRIVATE_SOURCE_REJECTED")
    if row["review_status"] != "PENDING_REVIEW":
        raise MappingError("MAPPER_OUTPUT_CANNOT_SELF_APPROVE")


def make_row(dataset, source_id, text, split, intent, entry, slots, table, metadata=None, composition=None, family=None, privacy="PUBLIC_LICENSED"):
    if entry is None:
        return None  # Explicit DROP; runner records the source row ordinal/reason.
    if source_id is None or isinstance(source_id, bool) or not isinstance(source_id, (str, int)) or str(source_id) == "":
        raise MappingError("SOURCE_ID_REQUIRED")
    if not isinstance(text, str):
        raise MappingError("SOURCE_TEXT_REQUIRED")
    if split not in REGISTRY[dataset]["allowed_splits"] and dataset != "metis_trainable_legacy":
        raise MappingError("UNKNOWN_SOURCE_SPLIT")
    slots = sorted(copy.deepcopy(slots), key=lambda s: (s["start"], s["end"]))
    tokens, bio = tokenize(text, slots)
    source = {"dataset": dataset, "version": REGISTRY[dataset]["version"], "source_id": str(source_id),
              "original_split": split, "original_intent": intent, "mapper_version": MAPPER_VERSION,
              "mapping_version": table["mapping_version"], "mapping_decision": entry["decision"],
              "artifact_sha256": None, "intake_approval_id": None}
    row = {"schema_version": VERSION, "nlu_schema_version": LABELS["schema_version"],
           "id": digest(json.dumps([source[k] for k in ("dataset", "version", "original_split", "source_id")], separators=(",", ":"))),
           "text": text, "domain": entry["domain"], "action": entry["action"], "slots": slots,
           "disposition": entry["disposition"], "ood": entry["decision"] == "OOD", "tokens": tokens, "bio_tags": bio,
           "family": family or "unresolved:" + digest(dataset + ":" + str(source_id)), "source": source,
           "privacy": privacy, "review_status": "PENDING_REVIEW", "purpose": "QUARANTINE",
           "metadata": copy.deepcopy(metadata or {}), "composition": copy.deepcopy(composition)}
    validate_row(row)
    return row


def to_frozen_record(row, *, split, provenance, family_id, scenario_id, approved_rows, governance=None):
    """Explicit promotion uses trusted, external per-row approvals, never source flags."""
    validate_row(row)
    source = row["source"]
    if not source["artifact_sha256"] or not source["intake_approval_id"]:
        raise MappingError("VERIFIED_INTAKE_REQUIRED")
    approved = approved_rows.get(row["id"])
    if not approved or approved.get("canonical_sha256") != digest(json.dumps(row, sort_keys=True, ensure_ascii=False, separators=(",", ":"))) or approved.get("split") != split:
        raise MappingError("ROW_APPROVAL_REQUIRED")
    if approved.get("family_id") != family_id or approved.get("scenario_id") != scenario_id:
        raise MappingError("GROUPING_APPROVAL_REQUIRED")
    if split not in LABELS["splits"] or split == "FINAL_INDEPENDENT_TEST":
        raise MappingError("PUBLIC_OR_LEGACY_NOT_FRESH_INDEPENDENT_EVALUATION")
    allowed = REGISTRY[source["dataset"]]["allowed_splits"].get(source["original_split"], [])
    if split not in allowed:
        raise MappingError("PROTECTED_OR_UNKNOWN_SOURCE_SPLIT")
    if REGISTRY[source["dataset"]]["metis_status"] != "REGISTERED_PENDING_INTAKE":
        raise MappingError("REGISTRY_SOURCE_BLOCKED")
    from ml.nlu_v1.data_management.storage import require_reuse
    governed_privacy = require_reuse(row, governance, split, family_id, scenario_id)
    if governance["row"]["privacy"] in ("PRIVATE", "RESTRICTED") and approved.get("private_training_allowed") is not True:
        raise MappingError("PRIVATE_TRAINING_CONSENT_REQUIRED")
    required = {"license_verified", "identity_verified", "split_verified", "privacy_reviewed", "mapping_reviewed", "isolation_verified"}
    if any(approved.get(gate) is not True for gate in required):
        raise MappingError("ROW_REVIEW_GATES_PENDING")
    for key, expected in (("source_id", source["dataset"]), ("source_version", source["version"]),
                          ("source_record_id", source["source_id"]), ("source_split", source["original_split"]),
                          ("mapper_version", source["mapper_version"]), ("privacy", governed_privacy),
                          ("source_class", "TRAINABLE_LEGACY" if source["dataset"] == "metis_trainable_legacy" else "PUBLIC_DATASET")):
        if provenance.get(key) != expected:
            raise MappingError("PROVENANCE_DISAGREEMENT")
    if provenance.get("source_sha256") != approved.get("artifact_sha256") or not approved.get("artifact_sha256"):
        raise MappingError("ARTIFACT_APPROVAL_REQUIRED")
    if source["artifact_sha256"] != approved["artifact_sha256"] or source["intake_approval_id"] != approved.get("intake_approval_id"):
        raise MappingError("INTAKE_PROVENANCE_DISAGREEMENT")
    if provenance.get("approval_id") != approved.get("approval_id") or not approved.get("approval_id"):
        raise MappingError("APPROVAL_ID_DISAGREEMENT")
    if provenance.get("license_id") != REGISTRY[source["dataset"]]["license"]["id"]:
        raise MappingError("LICENSE_DISAGREEMENT")
    record = {"schema_version": LABELS["schema_version"], "id": row["id"], "text": row["text"], "language": "en-US",
              "tokens": copy.deepcopy(row["tokens"]), "bio_tags": row["bio_tags"][:],
              "meaning": {k: copy.deepcopy(row[k]) for k in ("domain", "action", "slots", "disposition", "ood")},
              "split": split, "family_id": family_id, "scenario_id": scenario_id, "provenance": copy.deepcopy(provenance)}
    validate_prediction(record)
    return record


def main():
    parser = argparse.ArgumentParser(description="Check ingestion schema or validate quarantine JSONL")
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--write-schema", action="store_true")
    modes.add_argument("--check", action="store_true")
    modes.add_argument("--validate-jsonl", type=Path)
    args = parser.parse_args()
    verify_freeze()
    file = HERE / "canonical.schema.json"
    if args.write_schema:
        file.write_text(json.dumps(schema(), indent=2) + "\n", encoding="utf-8")
    elif json.loads(file.read_text(encoding="utf-8")) != schema():
        raise SystemExit("Ingestion schema is stale")
    if args.validate_jsonl:
        count = 0
        try:
            with args.validate_jsonl.open(encoding="utf-8") as stream:
                for count, line in enumerate(stream, 1):
                    validate_row(json.loads(line, object_pairs_hook=unique_keys))
        except (ValueError, ValidationError, TypeError, KeyError, OSError):
            raise SystemExit(f"Invalid canonical input near row {count}; content omitted") from None
        if count == 0:
            raise SystemExit("Empty canonical input")
        print(f"PASS: {count} quarantine rows; training eligibility not established")
        return
    print("PASS: ingestion schema matches frozen prediction contract")


if __name__ == "__main__":
    main()
