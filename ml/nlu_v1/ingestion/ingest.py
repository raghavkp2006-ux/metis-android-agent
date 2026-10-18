"""Atomic quarantine JSONL intake. Requires external artifact approval; never trains.

python ml/nlu_v1/ingestion/ingest.py --help
Native MTOP TSV adapter remains blocked until the registry's license/layout gates clear.
"""
import argparse
import csv
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import re
from jsonschema import ValidationError

if not __package__:
    sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
    __package__ = "ml.nlu_v1.ingestion"
from .common import REGISTRY, MAPPER_VERSION, MappingError, load_registry, verify_freeze, validate_row
from .check_mappings import check as check_mappings
from contract import unique_keys
from source_registry import ROOT
from . import massive_mapper, hwu_mapper, mtop_mapper, clinc_mapper, legacy_metis_mapper

SOURCES = {"massive": "massive_1_1_en_us", "hwu": "hwu_nlu_evaluation", "mtop": "mtop_en",
           "clinc": "clinc150_full", "legacy_metis": "metis_trainable_legacy"}


def file_digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def approve_intake(source_id, input_path, approval):
    """Approval sidecar is trusted operator configuration, never a source row."""
    source = REGISTRY[source_id]
    if source["metis_status"] != "REGISTERED_PENDING_INTAKE" or source["license"]["verification"] != "VERIFIED_METADATA":
        raise MappingError("SOURCE_BLOCKED_BY_REGISTRY")
    expected = {"source": source_id, "version": source["version"], "revision": source["revision"],
                "license_id": source["license"]["id"], "mapper_version": MAPPER_VERSION, "status": "APPROVED"}
    if any(approval.get(k) != v for k, v in expected.items()):
        raise MappingError("INTAKE_APPROVAL_IDENTITY_MISMATCH")
    if any(approval.get(k) is not True for k in ("license_verified", "identity_verified", "split_verified", "privacy_reviewed")) or not approval.get("approval_id"):
        raise MappingError("INTAKE_APPROVAL_GATES_PENDING")
    required = load_registry()["policy"]["intake_manifest_required"]
    if any(key not in approval for key in required):
        raise MappingError("INTAKE_MANIFEST_INCOMPLETE")
    if any(not isinstance(approval[key], str) or not re.fullmatch(r"[a-f0-9]{64}", approval[key]) for key in ("archive_sha256", "member_sha256", "license_sha256")):
        raise MappingError("INTAKE_MANIFEST_HASH_INVALID")
    if approval["requested_url"] != source["source_url"] or approval["source_revision"] != source["revision"] or not approval["resolved_url"].startswith("https://"):
        raise MappingError("INTAKE_MANIFEST_ORIGIN_MISMATCH")
    member = Path(approval["member_path"])
    if member.is_absolute() or member.drive or ".." in member.parts or not approval["member_path"] or "\\" in approval["member_path"]:
        raise MappingError("UNSAFE_MEMBER_PATH")
    captured = datetime.fromisoformat(approval["downloaded_at_utc"].replace("Z", "+00:00"))
    if captured.utcoffset() is None or captured.utcoffset().total_seconds() != 0:
        raise MappingError("UTC_INTAKE_TIMESTAMP_REQUIRED")
    if type(approval["archive_size_bytes"]) is not int or approval["archive_size_bytes"] <= 0 or approval["locale"] != source["locale"]:
        raise MappingError("INTAKE_MANIFEST_SIZE_OR_LOCALE_INVALID")
    expected_hash = approval.get("member_sha256")
    if not isinstance(expected_hash, str) or len(expected_hash) != 64 or expected_hash != file_digest(input_path):
        raise MappingError("INPUT_HASH_MISMATCH")


def iter_native(name, path, original_split=None, reviews=None):
    reviews = reviews or {}
    with path.open(encoding="utf-8", newline="") as stream:
        if name == "hwu":
            reader = csv.DictReader(stream, delimiter=";")
            for ordinal, raw in enumerate(reader, 1):
                yield ordinal, hwu_mapper.map_record(raw, original_split)
        elif name == "clinc":
            data = json.load(stream, object_pairs_hook=unique_keys)
            if not isinstance(data, dict) or set(data) != {"train", "val", "test", "oos_train", "oos_val", "oos_test"}:
                raise MappingError("CLINC_FULL_VARIANT_REQUIRED")
            ordinal = 0
            for split, rows in data.items():
                if not isinstance(rows, list):
                    raise MappingError("CLINC_SPLIT_ARRAY_REQUIRED")
                for index, raw in enumerate(rows):
                    ordinal += 1
                    yield ordinal, clinc_mapper.map_record(raw, split, str(index))
        elif name == "mtop" and path.suffix == ".tsv":
            for ordinal, columns in enumerate(csv.reader(stream, delimiter="\t", quoting=csv.QUOTE_NONE), 1):
                if len(columns) != 8:
                    raise MappingError("MTOP_EIGHT_COLUMN_TSV_REQUIRED")
                key, intent, slots, text, domain, locale, parse, tokenization = columns
                raw = dict(id=key, intent=intent, text=text, locale=locale, parse=parse, domain=domain,
                           native_slots=slots, native_tokenization=json.loads(tokenization, object_pairs_hook=unique_keys))
                yield ordinal, mtop_mapper.map_record(raw, original_split, temporal_review=reviews.get("temporal_reviews", {}).get(key))
        else:
            for ordinal, line in enumerate(stream, 1):
                raw = json.loads(line, object_pairs_hook=unique_keys)
                if name == "massive":
                    row = massive_mapper.map_record(raw, original_split)
                elif name == "mtop":
                    row = mtop_mapper.map_record(raw, original_split, temporal_review=reviews.get("temporal_reviews", {}).get(str(raw["id"])))
                elif name == "legacy_metis":
                    key = str(raw["id"])
                    row = legacy_metis_mapper.map_record(raw, original_split,
                        row_approval=reviews.get("row_approvals", {}).get(key),
                        reannotation=reviews.get("legacy_reannotations", {}).get(key))
                else:
                    raise MappingError("UNKNOWN_ADAPTER")
                yield ordinal, row


def ingest(name, input_path, output_path, approval, original_split=None):
    verify_freeze()
    load_registry()
    check_mappings()
    if name not in SOURCES:
        raise MappingError("UNKNOWN_SOURCE")
    input_path, output_path = Path(input_path), Path(output_path)
    destination = output_path.resolve()
    for protected in (ROOT / "archive/legacy_ml", ROOT / "datasets/legacy", ROOT / "ml/nlu_v1/schema", ROOT / "ml/nlu_v1/schema_versions", ROOT / "app/src"):
        if destination == protected.resolve() or protected.resolve() in destination.parents:
            raise MappingError("PROTECTED_OUTPUT_LOCATION")
    if input_path.resolve() == output_path.resolve() or output_path.exists():
        raise MappingError("OUTPUT_MUST_BE_NEW_AND_DISTINCT")
    approve_intake(SOURCES[name], input_path, approval)
    if original_split is not None and original_split != approval["original_split"]:
        raise MappingError("SPLIT_OVERRIDE_NOT_APPROVED")
    count, dropped, seen = 0, [], set()
    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="\n", dir=output_path.parent,
                                         prefix=".nlu-intake-", suffix=".tmp", delete=False) as stream:
            temp_path = Path(stream.name)
            for ordinal, row in iter_native(name, input_path, original_split, reviews=approval):
                if row is None:
                    dropped.append({"ordinal": ordinal, "reason": "EXPLICIT_DROP_POLICY"})
                    continue
                if approval["original_split"] != "MIXED_OFFICIAL" and row["source"]["original_split"] != approval["original_split"]:
                    raise MappingError("ROW_SPLIT_NOT_APPROVED")
                row["source"].update(artifact_sha256=approval["member_sha256"], intake_approval_id=approval["approval_id"])
                validate_row(row)
                if row["id"] in seen:
                    raise MappingError("DUPLICATE_SOURCE_RECORD")
                seen.add(row["id"])
                stream.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
                count += 1
        if not count:
            raise MappingError("NO_CANONICAL_ROWS")
        # Protect against modification of source bytes between approval and parse.
        if file_digest(input_path) != approval["member_sha256"]:
            raise MappingError("INPUT_CHANGED_DURING_INTAKE")
        # Hard-link publication is atomic and refuses an existing destination.
        os.link(temp_path, output_path)
        return {"mapped": count, "dropped": dropped, "purpose": "QUARANTINE", "training_built": False,
                "output_sha256": file_digest(output_path), "approval_id": approval["approval_id"]}
    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", choices=SOURCES, required=True)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--approval", type=Path, required=True, help="Externally reviewed artifact approval JSON; never generated by this command")
    parser.add_argument("--original-split")
    args = parser.parse_args()
    try:
        approval = json.loads(args.approval.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
        result = ingest(args.source, args.input, args.output, approval, args.original_split)
    except (ValueError, KeyError, TypeError, OSError, RecursionError, ValidationError) as error:
        # No traceback/instance dump: input can contain private text.
        code = str(error) if isinstance(error, MappingError) else type(error).__name__
        raise SystemExit("Intake rejected: " + code) from None
    print(json.dumps(result))


if __name__ == "__main__":
    main()
