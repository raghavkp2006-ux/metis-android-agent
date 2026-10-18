"""Fresh NLU contracts; no legacy model, training, resolution or dispatch imports.

Run from the repository root: python ml/nlu_v1/contract.py --check
Dataset validation does not establish provenance approval or split isolation.
"""
import argparse
import hashlib
import json
from pathlib import Path

from jsonschema import Draft202012Validator, ValidationError
import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKSPACE = ROOT / "ml/nlu_v1"
SCHEMA = WORKSPACE / "schema"


def load_labels():
    labels = {"schema_version": "nlu_v1_schema_001"}
    for name in ("domains", "actions", "slots", "dispositions"):
        value = yaml.safe_load((SCHEMA / (name + ".yaml")).read_text(encoding="utf-8"))
        if value["schema_version"] != labels["schema_version"]:
            raise ValueError("Label version mismatch")
        items = value["labels"]
        if not items or len(items) != len(set(items)):
            raise ValueError("Empty or duplicated labels")
        labels[name] = items
        if name == "slots" and (value["encoding"], value["outside_tag"], value["prefixes"]) != ("BIO", "O", ["B", "I"]):
            raise ValueError("Unsupported slot encoding")
    labels.update(
        splits=["TRAIN", "DEVELOPMENT", "CALIBRATION", "SELECTION", "FINAL_INDEPENDENT_TEST"],
        source_classes=["PUBLIC_DATASET", "TRAINABLE_LEGACY", "CONTROLLED_SYNTHETIC", "INDEPENDENT_EVALUATION"],
        privacy=["PUBLIC_LICENSED", "SYNTHETIC_NONPERSONAL", "CONSENTED_REDACTED", "PRIVATE_DO_NOT_PUBLISH"],
    )
    return labels


LABELS = load_labels()


def obj(properties, required=None):
    return {"type": "object", "properties": properties,
            "required": list(properties) if required is None else required,
            "additionalProperties": False}


def enum(values):
    return {"type": "string", "enum": list(values)}


def array(items, limit=64):
    return {"type": "array", "items": items, "maxItems": limit}


def build_schemas():
    string = {"type": "string", "minLength": 1, "maxLength": 4000}
    identifier = {"type": "string", "pattern": "^[A-Za-z0-9][A-Za-z0-9_.:/-]{0,199}$"}
    sha = {"type": "string", "pattern": "^[a-f0-9]{64}$"}
    slot = obj({"type": enum(LABELS["slots"]), "start": {"type": "integer", "minimum": 0},
                "end": {"type": "integer", "minimum": 1}, "text": string})
    meaning = obj({
        "domain": enum(LABELS["domains"]), "action": enum(LABELS["actions"]),
        "slots": array(slot), "disposition": enum(LABELS["dispositions"]),
        "ood": {"type": "boolean"},
    })
    meaning["allOf"] = [{
        "if": {"properties": {"disposition": {"const": "OUT_OF_DOMAIN"}}},
        "then": {"properties": {"domain": {"const": "UNKNOWN"}, "action": {"const": "NONE"},
                                  "ood": {"const": True}}},
        "else": {"properties": {"ood": {"const": False}}},
    }, {
        "if": {"properties": {"disposition": {"enum": ["SUPPORTED_COMPLETE", "SUPPORTED_NEEDS_CONTEXT", "SUPPORTED_NEEDS_CONFIRMATION"]}}},
        "then": {"properties": {"domain": {"not": {"const": "UNKNOWN"}},
                                  "action": {"not": {"const": "NONE"}}}},
    }, {
        "if": {"properties": {"domain": {"const": "UNKNOWN"}}},
        "then": {"properties": {"action": {"const": "NONE"}, "disposition": {"enum": ["KNOWN_UNSUPPORTED", "OUT_OF_DOMAIN"]}}},
    }]
    token = obj({"start": {"type": "integer", "minimum": 0},
                 "end": {"type": "integer", "minimum": 1}, "text": string})
    bio = ["O"] + [prefix + "-" + label for label in LABELS["slots"] for prefix in ("B", "I")]
    common = {"schema_version": {"const": LABELS["schema_version"]},
              "text": string, "tokens": {**array(token, 4000), "minItems": 1},
              "bio_tags": {**array(enum(bio), 4000), "minItems": 1}, "meaning": meaning}
    output = obj(common)
    provenance = obj({
        "source_id": identifier, "source_version": identifier, "source_record_id": identifier,
        "source_class": enum(LABELS["source_classes"]), "source_sha256": sha,
        "source_split": identifier, "mapper_version": identifier,
        "license_id": identifier, "approval_id": identifier,
        "privacy": enum(LABELS["privacy"]),
        "author_id": identifier, "reviewer_id": identifier,
        "generator_id": {"anyOf": [identifier, {"type": "null"}]},
        "parent_ids": {**array(identifier), "uniqueItems": True},
    })
    row = obj({**common, "id": identifier, "language": {"const": "en-US"},
               "split": enum(LABELS["splits"]), "family_id": identifier,
               "scenario_id": identifier, "provenance": provenance})
    # Final isolation and approvals require a registry and cross-row audit later.
    row["allOf"] = [
        {"if": {"properties": {"split": {"const": "FINAL_INDEPENDENT_TEST"}}},
         "then": {"properties": {"provenance": {"properties": {
             "source_class": {"const": "INDEPENDENT_EVALUATION"},
             "generator_id": {"type": "null"},
         }}}}},
        {"if": {"properties": {"provenance": {"properties": {
            "source_class": {"const": "CONTROLLED_SYNTHETIC"}}}}},
         "then": {"properties": {"provenance": {"properties": {
             "generator_id": identifier, "privacy": {"const": "SYNTHETIC_NONPERSONAL"}}}}}},
    ]
    for name, schema in (("output", output), ("record", row)):
        schema["$schema"] = "https://json-schema.org/draft/2020-12/schema"
        schema["$id"] = "urn:metis:nlu:v1:" + name
        schema["title"] = "METIS NLU v1 " + name
    return {"output.schema.json": output, "row.schema.json": row}


def utf16_boundaries(text):
    """Map UTF-16 indexes to Python indexes; surrogate interiors are absent."""
    boundaries = {0: 0}
    units = 0
    for index, char in enumerate(text):
        if 0xD800 <= ord(char) <= 0xDFFF:
            raise ValueError("Unpaired surrogate in input")
        units += 2 if ord(char) > 0xFFFF else 1
        boundaries[units] = index + 1
    return boundaries


def validate(value, kind="record"):
    if kind not in ("output", "record"):
        raise ValueError("Unknown contract kind")
    schemas = build_schemas()
    if isinstance(value, dict) and value.get("schema_version") == "nlu_v1_schema_002":
        from ml.nlu_v1.context_contract import build_schemas as context_schemas
        schemas = context_schemas()
    Draft202012Validator(schemas["row.schema.json" if kind == "record" else "output.schema.json"]).validate(value)
    text = value["text"]
    boundaries = utf16_boundaries(text)
    if not text.strip() or max(boundaries) > 4000:
        raise ValueError("Text must be nonblank and at most 4000 UTF-16 units")
    spans = []
    for slot in value["meaning"]["slots"]:
        start, end = slot["start"], slot["end"]
        if start not in boundaries or end not in boundaries or end <= start:
            raise ValueError("Invalid UTF-16 slot boundary")
        if text[boundaries[start]:boundaries[end]] != slot["text"]:
            raise ValueError("Slot text disagrees with raw input")
        if any(start < previous_end and previous_start < end for previous_start, previous_end in spans):
            raise ValueError("Overlapping slots are outside the v1 flat-span contract")
        spans.append((start, end))
    tokens, tags = value["tokens"], value["bio_tags"]
    if len(tokens) != len(tags):
        raise ValueError("One BIO tag is required per token")
    cursor = 0
    decoded = []
    previous_tag = "O"
    for token, tag in zip(tokens, tags):
        start, end = token["start"], token["end"]
        if start not in boundaries or end not in boundaries or not cursor <= start < end:
            raise ValueError("Invalid or unordered UTF-16 token boundaries")
        raw = text[boundaries[start]:boundaries[end]]
        if raw != token["text"] or not raw.strip():
            raise ValueError("Token text disagrees with input or is whitespace")
        if text[boundaries[cursor]:boundaries[start]].strip():
            raise ValueError("Tokens must cover all nonwhitespace input")
        if tag.startswith("I-") and previous_tag not in ("B-" + tag[2:], "I-" + tag[2:]):
            raise ValueError("Illegal BIO continuation")
        if tag.startswith("B-"):
            decoded.append({"type": tag[2:], "start": start, "end": end, "text": raw})
        elif tag.startswith("I-"):
            decoded[-1]["end"] = end
            decoded[-1]["text"] = text[boundaries[decoded[-1]["start"]]:boundaries[end]]
        cursor, previous_tag = end, tag
    if text[boundaries[cursor]:].strip():
        raise ValueError("Tokens must cover trailing nonwhitespace input")
    if decoded != value["meaning"]["slots"]:
        raise ValueError("BIO spans disagree with ordered slot annotations")
    if kind == "record":
        provenance = value["provenance"]
        if value["id"] in provenance["parent_ids"]:
            raise ValueError("Record cannot be its own parent")
        if value["split"] == "FINAL_INDEPENDENT_TEST":
            if provenance["author_id"] == provenance["reviewer_id"]:
                raise ValueError("Final evaluation requires an independent reviewer")
            if provenance["privacy"] == "PRIVATE_DO_NOT_PUBLISH":
                raise ValueError("Private material cannot enter the public final benchmark")


def verify_freeze(directory=SCHEMA):
    manifest = json.loads((directory / "freeze.json").read_text(encoding="utf-8"))
    expected = {"domains.yaml", "actions.yaml", "slots.yaml", "dispositions.yaml",
                "row.schema.json", "output.schema.json", "README.md"}
    if manifest["schema_version"] != LABELS["schema_version"] or manifest["status"] != "FROZEN" or set(manifest["sha256"]) != expected:
        raise ValueError("Invalid freeze manifest")
    for name, digest in manifest["sha256"].items():
        data = (directory / name).read_bytes().replace(b"\r\n", b"\n")
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError("Frozen contract changed: " + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--write-schemas", action="store_true")
    modes.add_argument("--check", action="store_true")
    modes.add_argument("--freeze", action="store_true")
    modes.add_argument("--validate-jsonl", type=Path)
    args = parser.parse_args()
    schemas = build_schemas()
    if args.validate_jsonl:
        verify_freeze()
        count = 0
        try:
            with args.validate_jsonl.open(encoding="utf-8") as stream:
                for count, line in enumerate(stream, 1):
                    try:
                        validate(json.loads(line, object_pairs_hook=unique_keys))
                    except (ValueError, ValidationError) as error:
                        # Never echo personal row text or jsonschema's instance dump.
                        raise SystemExit(f"Invalid record at line {count}: {type(error).__name__}") from None
        except UnicodeError:
            raise SystemExit("Invalid UTF-8 dataset") from None
        if not count:
            raise SystemExit("Empty dataset")
        print(f"Validated {count} records; provenance approval and isolation NOT checked")
        return
    for name, schema in schemas.items():
        Draft202012Validator.check_schema(schema)
        target = SCHEMA / name
        if args.write_schemas:
            if (SCHEMA / "freeze.json").exists():
                raise SystemExit("Frozen version cannot be regenerated; create a new schema version")
            target.write_text(json.dumps(schema, indent=2) + "\n", encoding="utf-8")
        elif json.loads(target.read_text(encoding="utf-8")) != schema:
            raise SystemExit("Stale schema: " + name)
    if args.freeze:
        target = SCHEMA / "freeze.json"
        if target.exists():
            verify_freeze()
        else:
            names = ["domains.yaml", "actions.yaml", "slots.yaml", "dispositions.yaml",
                     "row.schema.json", "output.schema.json", "README.md"]
            manifest = {"schema_version": LABELS["schema_version"], "status": "FROZEN",
                        "approval_basis": "User Plan 01 supplied and approved the exact label catalogs and requested freeze",
                        "frozen_on": "2026-10-16", "hash_encoding": "UTF-8; CRLF normalized to LF",
                        "sha256": {name: hashlib.sha256((SCHEMA / name).read_bytes().replace(b"\r\n", b"\n")).hexdigest() for name in names}}
            target.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    if not args.write_schemas:
        verify_freeze()
    print("NLU v1 schemas valid and synchronized" + ("; FROZEN" if not args.write_schemas else ""))


def unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON field")
        result[key] = value
    return result


if __name__ == "__main__":
    main()
