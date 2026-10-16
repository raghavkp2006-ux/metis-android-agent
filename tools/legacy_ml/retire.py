"""Freeze local evidence without executing, importing or rescoring legacy models.

Raw evidence stays ignored/private. This tool never publishes data, trains a model,
or modifies Android storage. Run freeze once, then verify before retiring sources.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = ROOT / "archive/legacy_ml"
MANIFEST = ARCHIVE / "manifest.json"


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(value, indent=2, sort_keys=True) + "\n")


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT).decode().strip()


def safe_path(name):
    path = (ROOT / name).resolve()
    if not path.is_relative_to(ROOT) or path == ROOT:
        raise ValueError("Path escapes repository: " + name)
    return path


def verify(originals=False):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    errors = []
    expected = {item["archive"] for item in manifest["files"]}
    actual = {p.relative_to(ROOT).as_posix() for p in (ARCHIVE / "private/evidence").rglob("*") if p.is_file()}
    if actual != expected:
        raise ValueError("Archive file set changed: " + ", ".join(sorted(actual ^ expected)))
    for item in manifest["files"]:
        archived = safe_path(item["archive"])
        if not archived.is_file() or digest(archived) != item["sha256"]:
            errors.append(item["archive"])
        if originals:
            source = safe_path(item["source"])
            if not source.is_file() or digest(source) != item["sha256"]:
                errors.append(item["source"])
    if errors:
        raise ValueError("Missing or changed evidence: " + ", ".join(errors))
    return len(manifest["files"])


def freeze():
    if MANIFEST.exists():
        raise ValueError("Archive already frozen; use verify, never overwrite it")
    # Bind to the pre-existing scored-attempt record, not just newly made hashes.
    bindings = json.loads((ROOT / "docs/evaluation/phase15/scored_attempt_041/manifest.json").read_text())
    for name, expected in bindings["artifact_bindings"].items():
        if not safe_path(name).is_file() or digest(safe_path(name)) != expected:
            raise ValueError("V16 required original missing/changed: " + name)
    paths = set()
    excluded = []
    for folder in ("docs/evaluation/phase15", "app/build/reports/phase15"):
        for path in (ROOT / folder).rglob("*"):
            if not path.is_file():
                continue
            name = path.relative_to(ROOT).as_posix()
            # AVD disk images/download chunks are emulator infrastructure, not ML evidence.
            if "__pycache__" in path.parts or any(p in path.parts for p in ("api26-avd", "test-avd")) or path.name.startswith("image-part-") or path.name == "api26-image-curl.zip":
                excluded.append(name)
                continue
            paths.add(name)
    # Preserve exact source/environment/build/CI state before any active changes.
    paths.update(git("ls-files").splitlines())
    paths.update(git("ls-files", "--others", "--exclude-standard").splitlines())
    paths = {n for n in paths if not n.startswith(("archive/", "docs/ml_legacy/", "tools/legacy_ml/")) and safe_path(n).is_file()}
    records = []
    for name in sorted(paths):
        source = safe_path(name)
        target = ARCHIVE / "private/evidence" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            raise ValueError("Refusing to overwrite evidence: " + str(target))
        expected = digest(source)
        shutil.copyfile(source, target)
        if digest(source) != expected or digest(target) != expected:
            raise ValueError("Copy verification failed: " + name)
        records.append(dict(source=name, archive=target.relative_to(ROOT).as_posix(), sha256=expected, bytes=source.stat().st_size))
    write_json(MANIFEST, dict(status="FROZEN", hash_policy="SHA-256 exact bytes; no newline normalization", git_commit=git("rev-parse", "HEAD"), branch=git("branch", "--show-current"), private_evidence_required=True, files=records, exclusions=excluded))
    count = verify(originals=True)
    report = ROOT / "docs/ml_legacy/ARCHIVE_VERIFICATION.md"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(f"# Archive verification\n\nOriginal files checked: {count}\n\nArchived files checked: {count}\n\nHash matches: {count}\n\nMismatch: 0\n\nMissing: 0\n\nRequired V16 artifacts also match the pre-existing scored_attempt_041 SHA-256 bindings.\n\nRaw evidence is local/private and ignored by Git. This verification does not make privacy or fresh-evaluation claims. Emulator disk images, download chunks and Python caches are explicitly excluded in the manifest.\n", encoding="utf-8")
    print(f"Frozen and verified {count} files; zero mismatches")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("freeze", "verify"))
    parser.add_argument("--originals", action="store_true")
    args = parser.parse_args()
    if args.command == "freeze":
        freeze()
    else:
        print(f"Verified {verify(args.originals)} archived files")


if __name__ == "__main__":
    main()
