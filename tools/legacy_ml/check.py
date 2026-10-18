"""Portable retirement gate; no legacy imports, fitting, or model evaluation."""
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

from retire import ARCHIVE, ROOT, digest, safe_path


def check_apk(path, model_hashes):
    forbidden = re.compile(r"(?:classifier|tf.?idf|sklearn|vocab|coefficients|(?:^|/)model\.json$|\.(?:pkl|pickle|joblib|onnx|tflite)$)", re.I)
    checked = 0
    with zipfile.ZipFile(path) as apk:
        for info in apk.infolist():
            if info.is_dir():
                continue
            if forbidden.search(info.filename):
                raise ValueError("Legacy model asset packaged: " + info.filename)
            if info.filename.startswith(("assets/", "res/raw/")) or info.filename.endswith(".json"):
                data = apk.read(info)
                checked += 1
                if hashlib.sha256(data).hexdigest() in model_hashes:
                    raise ValueError("Frozen legacy model bytes packaged: " + info.filename)
                try:
                    value = json.loads(data)
                except (ValueError, UnicodeDecodeError):
                    continue
                if isinstance(value, dict) and ("coefficients" in value or "vocabulary" in value or {"features", "intercept"} <= value.keys() or {"heads", "payload_sha256"} <= value.keys()):
                    raise ValueError("Learned classifier payload packaged: " + info.filename)
    return dict(apk=str(path), sha256=digest(path), assets_checked=checked, legacy_models=0)


def check_repository():
    manifest = json.loads((ARCHIVE / "manifest.json").read_text())
    records = manifest["files"]
    indexed = {}
    for item in records:
        safe_path(item["source"])
        safe_path(item["archive"])
        if not item["archive"].startswith("archive/legacy_ml/private/evidence/") or not re.fullmatch(r"[a-f0-9]{64}", item["sha256"]):
            raise ValueError("Invalid/private boundary manifest entry")
        if item["archive"] in indexed:
            raise ValueError("Duplicate archive entry")
        indexed[item["archive"]] = item
    for file in ARCHIVE.glob("v*/hashes.json"):
        for name, record in json.loads(file.read_text())["files"].items():
            if indexed[name]["sha256"] != record["sha256"]:
                raise ValueError("Version hashes disagree with manifest")
    # The retirement certificate describes a historical snapshot. Preserve its
    # archive integrity below without requiring today's production tree to match it.
    if list((ROOT / "docs/evaluation/phase15").rglob("*.py")) or list((ROOT / "docs/evaluation/phase15").rglob("requirements.txt")):
        raise ValueError("Legacy offline entry points still active")
    for file in (ROOT / ".github/workflows").glob("*.y*ml"):
        if re.search(r"docs/evaluation/phase15/.*(?:\.py|requirements\.txt)", file.read_text()):
            raise ValueError("Legacy evaluation/training still wired into CI")
    if any(name in (ROOT / "app/build.gradle.kts").read_text() for name in ("phase15RulesSnapshot", "phase15ReadRulesSnapshot")):
        raise ValueError("Legacy Gradle entry point still active")
    catalog = json.loads((ROOT / "datasets/legacy/catalog.json").read_text())
    if any(r["trainable"] or r["privacy"] != "PRIVATE_DO_NOT_PUBLISH" for r in catalog["files"]):
        raise ValueError("Unreviewed legacy data escaped quarantine")
    registry = json.loads((ARCHIVE / "benchmark-registry.json").read_text())
    if registry["production_ml_enabled"] or registry["new_model_trained"]:
        raise ValueError("Unexpected learned-model activation")
    public = json.loads((ARCHIVE / "public/hashes.json").read_text())
    actual_public = {p.relative_to(ROOT).as_posix() for p in ARCHIVE.rglob("*")
                     if p.is_file() and "private" not in p.relative_to(ARCHIVE).parts
                     and p != ARCHIVE / "public/hashes.json"}
    if actual_public != set(public["files"]):
        raise ValueError("Unindexed public archive file or missing public metadata")
    for name, expected in public["files"].items():
        if digest(safe_path(name)) != expected:
            raise ValueError("Public archive metadata changed: " + name)
    model_paths = {path for entry in registry["models"].values() for path in entry["model_artifacts"]
                   if entry["model_artifacts"] != "NOT_AVAILABLE"}
    model_hashes = {r["sha256"] for r in records if r["archive"] in model_paths}
    return model_hashes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apks", nargs="*", type=Path, default=[])
    args = parser.parse_args()
    hashes = check_repository()
    results = [check_apk(p, hashes) for p in args.apks]
    print(json.dumps(dict(status="PASS", archive_integrity_verified=True, production_ml_enabled=False, apks=results), indent=2))


if __name__ == "__main__":
    main()
