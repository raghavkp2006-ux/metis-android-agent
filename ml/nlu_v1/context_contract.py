"""Explicit context extension; schema 001 and its freeze remain immutable."""
import copy
import hashlib
import json
from pathlib import Path

from ml.nlu_v1.contract import build_schemas as baseline, verify_freeze

VERSION = "nlu_v1_schema_002"
HERE = Path(__file__).resolve().parent / "schema_versions" / VERSION


def build_schemas():
    schemas = {}
    for name, original in baseline().items():
        schema = copy.deepcopy(original)
        schemas[name] = schema
        schema["$id"] = "urn:metis:nlu:schema:002:" + name
        schema["properties"]["schema_version"] = {"const": VERSION}
        rules = schema["properties"]["meaning"]["allOf"]
        rules[1]["if"]["properties"]["disposition"]["enum"].remove("SUPPORTED_NEEDS_CONTEXT")
        rules[2]["then"]["properties"]["disposition"]["enum"].append("SUPPORTED_NEEDS_CONTEXT")
        # NONE never stands for a complete executable request, including known domains.
        rules.append({"if": {"properties": {"action": {"const": "NONE"}}},
                      "then": {"properties": {"domain": {"const": "UNKNOWN"}}}})
    return schemas


def check():
    verify_freeze()
    manifest = json.loads((HERE / "freeze.json").read_text(encoding="utf-8"))
    if manifest["schema_version"] != VERSION or manifest["status"] != "FROZEN":
        raise ValueError("INVALID_CONTEXT_FREEZE")
    if set(manifest["sha256"]) != {"row.schema.json", "output.schema.json", "README.md"}:
        raise ValueError("INVALID_CONTEXT_FREEZE_FILES")
    for name, digest in manifest["sha256"].items():
        if hashlib.sha256((HERE / name).read_bytes().replace(b"\r\n", b"\n")).hexdigest() != digest:
            raise ValueError("CONTEXT_FREEZE_CHANGED")
    for name, schema in build_schemas().items():
        if json.loads((HERE / name).read_text(encoding="utf-8")) != schema:
            raise ValueError("CONTEXT_SCHEMA_STALE")


if __name__ == "__main__":
    check()
    print("Context schema 002 frozen; original schema 001 unchanged")
