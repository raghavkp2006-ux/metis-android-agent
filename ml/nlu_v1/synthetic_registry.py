"""Local generation permission is deliberately narrower than dataset intake."""
from pathlib import Path
import yaml

from ml.nlu_v1.source_registry import UniqueLoader

PATH = Path(__file__).resolve().parent / "configs/synthetic_sources.yaml"


def load_source():
    registry = yaml.load(PATH.read_text(encoding="utf-8"), Loader=UniqueLoader)
    if registry["registry_version"] != "nlu_v1_synthetic_sources_001" or len(registry["sources"]) != 1:
        raise ValueError("INVALID_SYNTHETIC_REGISTRY")
    source = registry["sources"][0]
    if (source["source_id"], source["version"], source["metis_status"]) != (
            "metis_controlled_synthetic", "nlu_v1_synthetic_001", "GENERATION_ONLY_PENDING_REVIEW"):
        raise ValueError("INVALID_SYNTHETIC_IDENTITY")
    if source["allowed_splits"] != {"generated": ["QUARANTINE"]} or source["download_sha256"] is not None:
        raise ValueError("SYNTHETIC_USE_EXCEEDS_GENERATION")
    if source["license"]["status"] != "INTERNAL_CANDIDATES_ONLY" or source["privacy_class"] != "UNKNOWN_PRIVACY":
        raise ValueError("SYNTHETIC_RIGHTS_REVIEW_REQUIRED")
    return source
