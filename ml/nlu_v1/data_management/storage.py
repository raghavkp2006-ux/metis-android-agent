"""Append-only local batches and explicitly reviewed public projection."""
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile

from contract import unique_keys
from source_registry import ROOT
from .dedupe import audit, collection_hash, verify_report
from .records import DataError, canonical_issues, facts_hash, migrate_ingestion, sha_json, validate_record

DATASETS = ROOT / "ml/nlu_v1/datasets"


def require_reuse(ingestion_row, governance, target_split, family_id, scenario_id):
    """Old ingestion-to-frozen adapter cannot bypass Plan 04 governance."""
    if not isinstance(governance, dict) or not {"row", "collection", "report", "groups"} <= governance.keys():
        raise DataError("CANONICAL_GOVERNANCE_REQUIRED")
    row, collection = governance["row"], governance["collection"]
    validate_record(row)
    verify_report(collection, governance["report"])
    matches = [(index, current) for index, current in enumerate(collection) if current["id"] == row["id"]]
    if len(matches) != 1 or matches[0][1] != row or matches[0][0] in governance["report"]["quarantined_indices"]:
        raise DataError("CANONICAL_REUSE_COLLECTION_MISMATCH")
    if row["review_status"] != "APPROVED" or row["privacy"] == "UNKNOWN_PRIVACY" or row["quarantine_reasons"] or canonical_issues(row, governance["groups"]):
        raise DataError("CANONICAL_ROW_NOT_REUSE_APPROVED")
    if row["provenance"]["ingestion_row_sha256"] != sha_json(ingestion_row):
        raise DataError("INGESTION_GOVERNANCE_ORIGIN_MISMATCH")
    if row["partition"] != target_split or row["semantic_parent_id"] != family_id or row["scenario_id"] != scenario_id:
        raise DataError("CANONICAL_REUSE_PARTITION_OR_GROUP_MISMATCH")
    # Preserve new privacy semantics through the immutable older record enum.
    return "PUBLIC_LICENSED" if row["privacy"] == "PUBLIC" else "PRIVATE_DO_NOT_PUBLISH"


def atomic_new(path, data):
    path = Path(path)
    resolved = path.resolve()
    for protected in (ROOT / "archive/legacy_ml", ROOT / "datasets/legacy", ROOT / "ml/nlu_v1/schema", ROOT / "ml/nlu_v1/schema_versions", ROOT / "app/src"):
        if resolved == protected.resolve() or protected.resolve() in resolved.parents:
            raise DataError("PROTECTED_WRITE_LOCATION")
    if path.exists():
        raise DataError("IMMUTABLE_OUTPUT_ALREADY_EXISTS")
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="wb", dir=path.parent, prefix=".nlu-data-", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(temporary, path)  # Atomic, no replacement, works on NTFS/POSIX.
    finally:
        if temporary:
            temporary.unlink(missing_ok=True)


def json_bytes(value):
    return (json.dumps(value, sort_keys=True, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode()


def jsonl_bytes(rows):
    return "".join(json.dumps(row, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False) + "\n" for row in rows).encode()


def read_rows(path, *, batch_id):
    path = Path(path)
    data = path.read_bytes()
    file_sha = hashlib.sha256(data).hexdigest()
    rows = []
    for number, line in enumerate(data.decode("utf-8").splitlines(), 1):
        row = json.loads(line, object_pairs_hook=unique_keys)
        if not isinstance(row, dict):
            raise DataError("MALFORMED_DATA_ROW")
        if row.get("schema_version") == "nlu_v1_ingest_001":
            row = migrate_ingestion(row, batch_id=batch_id, row_number=number, import_file_sha256=file_sha)
        else:
            validate_record(row)
        rows.append(row)
    if not rows:
        raise DataError("EMPTY_INPUT_COLLECTION")
    return rows, data


def store_batch(rows, report, input_bytes, *, batch_id, root=DATASETS, input_kind="INGESTION_OR_MANAGED_SNAPSHOT_NOT_PUBLISHER_ARCHIVE"):
    """A manifest is the commit marker; incomplete batches are never consumable."""
    if input_kind not in ("INGESTION_OR_MANAGED_SNAPSHOT_NOT_PUBLISHER_ARCHIVE", "GENERATED_STRUCTURAL_PLANS"):
        raise DataError("UNKNOWN_BATCH_INPUT_KIND")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,79}", batch_id):
        raise DataError("INVALID_BATCH_ID")
    root = Path(root).resolve()
    for protected in (ROOT / "archive/legacy_ml", ROOT / "datasets/legacy", ROOT / "ml/nlu_v1/schema", ROOT / "ml/nlu_v1/schema_versions", ROOT / "app/src"):
        if root == protected.resolve() or protected.resolve() in root.parents:
            raise DataError("PROTECTED_WRITE_LOCATION")
    for name in ("raw", "canonical", "quarantine", "frozen"):
        (root / name).mkdir(parents=True, exist_ok=True)
        if (root / name).resolve().parent != root:
            raise DataError("DATA_DIRECTORY_ESCAPES_ROOT")
    if report.get("input_sha256") != collection_hash(rows) or report.get("report_sha256") != sha_json({k: v for k, v in report.items() if k != "report_sha256"}):
        raise DataError("BATCH_REPORT_BINDING_MISMATCH")
    for row in rows:
        validate_record(row)
    canonical, quarantined = [], []
    for index, row in enumerate(rows):
        destination = canonical if row["review_status"] in ("AUTO_VALIDATED", "HUMAN_REVIEWED", "APPROVED") and not row["quarantine_reasons"] and index not in report["quarantined_indices"] else quarantined
        destination.append(row)
    files = {
        "raw/" + batch_id + ".input.jsonl": input_bytes,
        "canonical/" + batch_id + ".jsonl": jsonl_bytes(canonical),
        "quarantine/" + batch_id + ".jsonl": jsonl_bytes(quarantined),
        "quarantine/" + batch_id + ".dedupe.json": json_bytes(report),
    }
    marker = root / "raw" / (batch_id + ".manifest.json")
    if marker.exists() or any((root / name).exists() for name in files):
        raise DataError("BATCH_ID_ALREADY_EXISTS")
    # Exact copies and new snapshots only; never delete/move an existing row.
    for name, data in files.items():
        atomic_new(root / name, data)
    manifest = {"manifest_version": "nlu_v1_data_batch_001", "status": "COMMITTED", "batch_id": batch_id,
        "canonical_rows": len(canonical), "quarantine_rows": len(quarantined), "total_rows": len(rows),
        "files": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()},
        "input_kind": input_kind, "training_built": False}
    atomic_new(marker, json_bytes(manifest))
    return manifest


def verify_batch(root, batch_id):
    root = Path(root).resolve()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,79}", batch_id):
        raise DataError("INVALID_BATCH_ID")
    manifest = json.loads((root / "raw" / (batch_id + ".manifest.json")).read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
    expected = {f"raw/{batch_id}.input.jsonl", f"canonical/{batch_id}.jsonl", f"quarantine/{batch_id}.jsonl", f"quarantine/{batch_id}.dedupe.json"}
    if manifest.get("status") != "COMMITTED" or manifest.get("batch_id") != batch_id or set(manifest.get("files", {})) != expected:
        raise DataError("INVALID_BATCH_COMMIT_MARKER")
    for name, digest in manifest["files"].items():
        target = (root / name).resolve()
        if root not in target.parents or hashlib.sha256(target.read_bytes()).hexdigest() != digest:
            raise DataError("BATCH_FILE_MISSING_OR_MODIFIED")
    return manifest


def process(input_path, *, batch_id, root=DATASETS, embeddings=None, groups=None):
    rows, raw = read_rows(input_path, batch_id=batch_id)
    updated, report = audit(rows, embeddings, groups=groups)
    return store_batch(updated, report, raw, batch_id=batch_id, root=root)


def export_public(rows, report, destination, *, approval, groups=None, selected_ids=None):
    """All selected rows must pass; unknown/private/restricted rows are never filtered into public output."""
    rows = list(rows)
    verify_report(rows, report)
    ids = [r["id"] for r in rows]
    if len(set(ids)) != len(ids):
        raise DataError("PUBLIC_EXPORT_REQUIRES_UNIQUE_ROW_IDS")
    selected = set(ids) if selected_ids is None else set(selected_ids)
    if not selected or not selected <= set(ids):
        raise DataError("PUBLIC_EXPORT_SELECTION_INVALID")
    if not approval or approval.get("kind") != "PUBLIC_EXPORT" or approval.get("input_sha256") != collection_hash(rows) or approval.get("report_sha256") != report["report_sha256"] or not approval.get("reviewer_id") or not approval.get("approval_id"):
        raise DataError("EXTERNAL_PUBLIC_EXPORT_APPROVAL_REQUIRED")
    by_id = {row["id"]: row for row in rows}
    result = []
    for index, row in enumerate(rows):
        if row["id"] not in selected:
            continue
        validate_record(row)
        if row["privacy"] != "PUBLIC" or row["review_status"] != "APPROVED" or row["quarantine_reasons"] or index in report["quarantined_indices"] or canonical_issues(row, groups):
            raise DataError("ROW_NOT_PUBLIC_EXPORT_ELIGIBLE")
        evidence = row["privacy_review"]
        if not evidence["publication_allowed"] or approval.get("row_bindings", {}).get(row["id"]) != facts_hash(row):
            raise DataError("PUBLIC_RIGHTS_OR_ROW_BINDING_MISSING")
        if approval.get("row_review_bindings", {}).get(row["id"]) != row["review_history"][-1]["event_sha256"]:
            raise DataError("PUBLIC_REVIEW_STATE_APPROVAL_STALE")
        from ml.nlu_v1.ingestion.common import REGISTRY
        registered = REGISTRY[row["source"]["dataset"]]
        source_license = registered["license"]["id"]
        if evidence["license_id"] != source_license:
            raise DataError("PUBLIC_LICENSE_EVIDENCE_MISMATCH")
        # No private/restricted/unknown ancestry can silently become public.
        pending = [(parent_id, {row["id"]}) for parent_id in (row["generation"] or {}).get("parent_ids", [])]
        seen = set()
        while pending:
            parent_id, chain = pending.pop()
            if parent_id in chain:
                raise DataError("PUBLIC_GENERATION_ANCESTRY_CYCLE")
            if parent_id in seen:
                continue
            seen.add(parent_id)
            parent = by_id.get(parent_id)
            if not parent or parent["privacy"] != "PUBLIC" or parent["review_status"] != "APPROVED":
                raise DataError("PUBLIC_GENERATION_ANCESTRY_NOT_APPROVED")
            pending.extend((ancestor_id, chain | {parent_id}) for ancestor_id in (parent["generation"] or {}).get("parent_ids", []))
        # Source annotations/worker IDs/audit text stay in local records only.
        projection = {key: copy.deepcopy(row[key]) for key in (
            "id", "text", "domain", "action", "slots", "disposition", "ood", "tokens", "bio_tags", "source",
            "family", "scenario_id", "semantic_parent_id", "privacy", "review_status", "partition", "generation")}
        projection.update(schema_version="nlu_v1_public_001", nlu_schema_version=row["nlu_schema_version"],
                          license_id=source_license, canonical_facts_sha256=facts_hash(row),
                          attribution=copy.deepcopy(registered["citation"]),
                          license_evidence_url=registered["license"]["evidence_url"],
                          source_url=registered["source_url"],
                          changes="Original text retained; labels and slot tags mapped to METIS; source audit metadata excluded")
        result.append(projection)
    data = jsonl_bytes(result)
    atomic_new(destination, data)
    return {"public_rows": len(result), "sha256": hashlib.sha256(data).hexdigest(), "approval_id": approval["approval_id"]}
