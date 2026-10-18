"""Download only identified, licensed public sources into local quarantine.

Does not create intake approvals, claim human review, or authorize training.
"""
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import tarfile
import tempfile
import urllib.request

from ml.nlu_v1.ingestion.common import REGISTRY
from ml.nlu_v1.ingestion.massive_mapper import map_record as map_massive
from ml.nlu_v1.ingestion.clinc_mapper import map_record as map_clinc
from ml.nlu_v1.data_management.records import migrate_ingestion, quarantine, sha_json
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes
from ml.nlu_v1.splitting.split import strict_unicode
from contract import unique_keys, verify_freeze
from jsonschema import ValidationError

LIMIT = 128 * 1024 * 1024


def download(url, path):
    """Bounded HTTPS copy, exact-byte hash, atomic no-replacement publication."""
    if not url.startswith("https://"):
        raise ValueError("HTTPS_SOURCE_REQUIRED")
    path = Path(path)
    digest = hashlib.sha256()
    size, temp_path = 0, None
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "METIS-NLU-v0-quarantine/1"}), timeout=30) as response:
            if not response.url.startswith("https://"):
                raise ValueError("HTTPS_REDIRECT_REQUIRED")
            if int(response.headers.get("Content-Length", 0)) > LIMIT:
                raise ValueError("SOURCE_SIZE_LIMIT")
            with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".download-", delete=False) as stream:
                temp_path = Path(stream.name)
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    size += len(chunk)
                    if size > LIMIT:
                        raise ValueError("SOURCE_SIZE_LIMIT")
                    digest.update(chunk)
                    stream.write(chunk)
                expected_size = int(response.headers.get("Content-Length", 0))
                if size == 0 or expected_size and size != expected_size:
                    raise ValueError("INCOMPLETE_SOURCE_DOWNLOAD")
                stream.flush()
                os.fsync(stream.fileno())
            if path.exists():
                if hashlib.sha256(path.read_bytes()).hexdigest() != digest.hexdigest():
                    raise ValueError("EXISTING_ACQUISITION_BYTES_CHANGED")
            else:
                os.link(temp_path, path)
            return {"requested_url": url, "resolved_url": response.url, "sha256": digest.hexdigest(), "size_bytes": size}
    finally:
        if temp_path:
            temp_path.unlink(missing_ok=True)


def fetch(source_id, root=DATASETS):
    verify_freeze()
    if source_id not in ("massive_1_1_en_us", "clinc150_full"):
        raise ValueError("SOURCE_NOT_ADMITTED_FOR_QUARANTINE_ACQUISITION")
    source = REGISTRY[source_id]
    if source["metis_status"] != "REGISTERED_PENDING_INTAKE" or not source["identity_verified"] or source["license"]["verification"] != "VERIFIED_METADATA" or not source["split_provenance"].startswith("DOCUMENTED_OFFICIAL"):
        raise ValueError("SOURCE_LICENSE_IDENTITY_OR_SPLIT_UNCLEAR")
    base = Path(root).resolve() / "raw" / ("v0_" + source_id)
    from source_registry import ROOT
    for protected in (ROOT / "archive", ROOT / "datasets/legacy", ROOT / "app", ROOT / "ml/nlu_v1/schema", ROOT / "ml/nlu_v1/schema_versions"):
        if protected.resolve() == base or protected.resolve() in base.parents:
            raise ValueError("PROTECTED_INTAKE_LOCATION")
    if (base / "observation.json").exists():
        raise ValueError("ACQUISITION_ALREADY_COMMITTED")
    base.mkdir(parents=True, exist_ok=True)
    artifact = base / ("source.tar.gz" if source_id.startswith("massive") else "source.json")
    observation = download(source["source_url"], artifact)
    license_url = source["license"]["evidence_url"].replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/")
    evidence = download(license_url, base / "license-evidence.txt")
    notice = (base / "license-evidence.txt").read_text(encoding="utf-8")
    if source["license"]["id"] == "CC-BY-4.0" and "CC BY 4.0" not in notice:
        raise ValueError("EXPECTED_MASSIVE_DATA_LICENSE_MISSING")
    if source["license"]["id"] == "CC-BY-3.0" and not ("Creative Commons Legal Code" in notice and "Attribution 3.0 Unported" in notice):
        raise ValueError("EXPECTED_CLINC_DATA_LICENSE_MISSING")
    pinned = json.loads((ROOT / "ml/nlu_v1/configs/dataset_source_evidence.json").read_text(encoding="utf-8"))
    expected_license_hashes = [item["sha256"] for record in pinned["sources"].values() if record["repository"] == source["repository"] for item in record["metadata"] if item["url"] == license_url]
    if expected_license_hashes != [evidence["sha256"]]:
        raise ValueError("PINNED_LICENSE_EVIDENCE_CHANGED")
    if source_id.startswith("massive"):
        with tarfile.open(artifact, "r:gz") as archive:
            members = archive.getmembers()
            english = [member for member in members if member.isfile() and Path(member.name).name == "en-US.jsonl"]
            licenses = [member for member in members if member.isfile() and "LICENSE" in Path(member.name).name.upper()]
            if len(english) != 1 or not licenses or english[0].size > LIMIT:
                raise ValueError("MASSIVE_ARCHIVE_IDENTITY_OR_LICENSE_MISSING")
            # Read named bytes only; never extract archive-controlled paths.
            payload = archive.extractfile(english[0]).read()
            license_bytes = [archive.extractfile(member).read(1024 * 1024) for member in licenses if member.size <= 1024 * 1024]
            if not any(b"4.0" in content and (b"Creative Commons" in content or b"creativecommons.org" in content) for content in license_bytes):
                raise ValueError("BUNDLED_DATA_LICENSE_NOT_VERIFIED")
            atomic_new(base / "en-US.jsonl", payload)
            atomic_new(base / "bundled-license.txt", b"\n\n".join(license_bytes))
            member_path, member_file = english[0].name, base / "en-US.jsonl"
    else:
        member_path, member_file = "data/data_full.json", artifact
    observation.update(observation_version="nlu_v1_public_acquisition_001", source_id=source_id, source_version=source["version"],
        revision=source["revision"], downloaded_at_utc=datetime.now(timezone.utc).isoformat(), locale=source["locale"],
        member_path=member_path, member_file=member_file.name, member_sha256=hashlib.sha256(member_file.read_bytes()).hexdigest(),
        license_id=source["license"]["id"], license_evidence_sha256=evidence["sha256"],
        status="RAW_PUBLIC_QUARANTINE_REVIEW_PENDING", intake_approval_id=None, privacy_reviewed=False, training_authorized=False)
    atomic_new(base / "observation.json", json_bytes(observation))
    return observation


def prepare(root=DATASETS, target=2000):
    """Provisional review pool only; training eligibility and family assignment absent."""
    from .build import bucket, load_config
    config = load_config()
    if not 1 <= target <= config["maximum_rows"]:
        raise ValueError("CANDIDATE_TARGET_OUT_OF_RANGE")
    candidates, errors, protected, scanned = [], Counter(), {}, Counter()
    for source_id in ("massive_1_1_en_us", "clinc150_full"):
        base = Path(root) / "raw" / ("v0_" + source_id)
        observation = json.loads((base / "observation.json").read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
        source = REGISTRY[source_id]
        expected = {"source_id": source_id, "source_version": source["version"], "revision": source["revision"], "requested_url": source["source_url"],
                    "resolved_url": source["source_url"], "locale": source["locale"], "license_id": source["license"]["id"],
                    "status": "RAW_PUBLIC_QUARANTINE_REVIEW_PENDING", "intake_approval_id": None, "training_authorized": False}
        if any(observation.get(key) != value for key, value in expected.items()):
            raise ValueError("ACQUIRED_SOURCE_IDENTITY_CHANGED")
        artifact = base / ("source.tar.gz" if source_id.startswith("massive") else "source.json")
        if hashlib.sha256(artifact.read_bytes()).hexdigest() != observation["sha256"] or hashlib.sha256((base / "license-evidence.txt").read_bytes()).hexdigest() != observation["license_evidence_sha256"]:
            raise ValueError("ACQUIRED_ARTIFACT_OR_LICENSE_CHANGED")
        member = base / observation["member_file"]
        if hashlib.sha256(member.read_bytes()).hexdigest() != observation["member_sha256"]:
            raise ValueError("ACQUIRED_MEMBER_CHANGED")
        if source_id.startswith("massive"):
            native = [json.loads(line, object_pairs_hook=unique_keys) for line in member.read_text(encoding="utf-8").splitlines()]
            split_counts = Counter(row["partition"] for row in native)
            training = ((str(row["id"]), "train", row) for row in native if row["partition"] == "train")
        else:
            native = json.loads(member.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)
            if set(native) != {"train", "val", "test", "oos_train", "oos_val", "oos_test"}:
                raise ValueError("CLINC_OFFICIAL_SPLITS_MISSING")
            split_counts = {key: len(value) for key, value in native.items()}
            training = ((str(index), split, raw) for split in ("train", "oos_train") for index, raw in enumerate(native[split]))
        protected[source_id] = {split: count for split, count in split_counts.items() if split not in ("train", "oos_train")}
        for source_key, original_split, raw in training:
            scanned[source_id] += 1
            try:
                strict_unicode(raw)
                row = map_massive(raw) if source_id.startswith("massive") else map_clinc(raw, original_split, source_key)
                if row is None:
                    errors["EXPLICIT_DROP"] += 1
                    continue
                row["source"]["artifact_sha256"] = observation["member_sha256"]
                candidates.append(row)
            except (ValueError, KeyError, TypeError, ValidationError) as error:
                errors[type(error).__name__] += 1
    # Balanced source-intent round robin; supported buckets first. Never read
    # protected utterances for candidate selection or relabel them into training.
    pools = defaultdict(list)
    for row in candidates:
        pools[bucket(row), row["source"]["dataset"], row["source"]["original_intent"]].append(row)
    for pool in pools.values():
        pool.sort(key=lambda row: sha_json([config["seed"], row["id"]]))
    selected, counts, normalized_seen = [], Counter(), set()
    from ml.nlu_v1.data_management.dedupe import normalized
    keys = sorted(pools, key=lambda key: (key[0] == "OOD_UNSUPPORTED", key))
    def take(key):
        while pools[key]:
            row = pools[key].pop()
            text_key = normalized(row["text"])
            if text_key in normalized_seen:
                errors["NORMALIZED_CANDIDATE_DUPLICATE"] += 1
                continue
            normalized_seen.add(text_key)
            selected.append(row)
            counts[key[0]] += 1
            return True
        return False
    # OOD/unsupported is capped so public negative abundance cannot hide missing
    # supported domains. Quotas are soft; gaps are exposed in the report.
    while len(selected) < target:
        progress = False
        for key in keys:
            if len(selected) >= target:
                break
            quota = config["quotas"][key[0]]
            if counts[key[0]] < quota:
                progress |= take(key)
        if not progress:
            break
    while len(selected) < target:
        progress = False
        for key in keys:
            if len(selected) >= target:
                break
            if key[0] != "OOD_UNSUPPORTED":
                progress |= take(key)
        if not progress:
            break
    rows = []
    for ordinal, row in enumerate(sorted(selected, key=lambda row: row["id"]), 1):
        managed = migrate_ingestion(row, batch_id="plan07_public_review_pool", row_number=ordinal,
                                    import_file_sha256=row["source"]["artifact_sha256"])
        rows.append(quarantine(managed, ["INTAKE_REVIEW_PENDING", "PRIVACY_REVIEW_REQUIRED", "PRIVACY_UNRESOLVED",
                                        "PROVISIONAL_SOURCE_MAPPING", "FAMILY_SCENARIO_REVIEW_REQUIRED", "EMBEDDINGS_MISSING", "SPLIT_AUDIT_PENDING"]))
    summary = {"report_version": "nlu_v1_v0_public_candidates_001", "candidate_rows": len(rows), "training_approved_rows": 0,
        "scanned_training_rows": dict(scanned), "mapping_and_selection_rejections": dict(errors), "groups": dict(counts),
        "missing_groups": sorted(set(config["required_groups"]) - set(counts)), "protected_split_counts_kept_raw_only": protected,
        "payloads_in_git": False, "intake_approvals_created": False, "human_reviews_created": False, "dataset_v0_frozen": False}
    base = Path(root) / "quarantine"
    base.mkdir(parents=True, exist_ok=True)
    atomic_new(base / "plan07_public_candidates.jsonl", jsonl_bytes(rows))
    atomic_new(base / "plan07_public_candidates.report.json", json_bytes(summary))
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--fetch", choices=("massive_1_1_en_us", "clinc150_full"))
    modes.add_argument("--prepare", action="store_true")
    args = parser.parse_args()
    print(json.dumps(fetch(args.fetch) if args.fetch else prepare(), sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, TypeError, OSError, ValidationError) as error:
        # Source text and jsonschema instance dumps never enter diagnostics.
        raise SystemExit("Public quarantine acquisition rejected: " + type(error).__name__) from None
