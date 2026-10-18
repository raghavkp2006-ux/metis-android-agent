"""Reviewed fresh evaluation intake namespace; currently no sources admitted."""
import hashlib
from pathlib import Path
import re
import yaml

from ml.nlu_v1.source_registry import UniqueLoader

PATH = Path(__file__).resolve().parent / "configs/independent_sources.yaml"
VERSION = "nlu_v1_independent_sources_001"


def registry_hash():
    return hashlib.sha256(PATH.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def load_sources():
    value = yaml.load(PATH.read_text(encoding="utf-8"), Loader=UniqueLoader)
    if value.get("registry_version") != VERSION or not isinstance(value.get("sources"), list):
        raise ValueError("INVALID_INDEPENDENT_SOURCE_REGISTRY")
    sources = {}
    for source in value["sources"]:
        required = {"id", "version", "source_class", "independent_evaluation", "metis_status", "allowed_splits", "license", "source_url", "citation",
                    "download", "identity_verified", "privacy_class", "collector_id", "reviewer_id", "collection_protocol_sha256"}
        if set(source) != required or source["id"] in sources or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,100}", source["id"]):
            raise ValueError("INVALID_INDEPENDENT_SOURCE_IDENTITY")
        if source["source_class"] != "INDEPENDENT_EVALUATION" or source["independent_evaluation"] is not True or source["identity_verified"] is not True or source["metis_status"] != "REGISTERED_PENDING_INTAKE":
            raise ValueError("INDEPENDENT_SOURCE_NOT_APPROVED")
        if not source["allowed_splits"] or any(purposes != ["FINAL_INDEPENDENT_TEST"] for purposes in source["allowed_splits"].values()):
            raise ValueError("INDEPENDENT_SOURCE_CANNOT_TUNE")
        if source["license"].get("verification") != "VERIFIED_AT_INTAKE" or not source["license"].get("id") or not source["license"].get("evidence_url") or not source["source_url"] or not source["citation"]:
            raise ValueError("INDEPENDENT_LICENSE_OR_ATTRIBUTION_MISSING")
        if source["download"].get("status") != "VERIFIED" or not re.fullmatch(r"[a-f0-9]{64}", source["download"].get("sha256", "")) or not re.fullmatch(r"[a-f0-9]{64}", source["collection_protocol_sha256"]):
            raise ValueError("INDEPENDENT_ARTIFACT_OR_PROTOCOL_UNVERIFIED")
        if source["privacy_class"] != "PUBLIC_REVIEW_REQUIRED" or not source["collector_id"] or not source["reviewer_id"] or source["collector_id"] == source["reviewer_id"]:
            raise ValueError("INDEPENDENT_REVIEW_REQUIRED")
        sources[source["id"]] = source
    return sources
