"""New harness source certificate; archived V16 certificates/results are never rewritten.

Only the current-source certificate is superseded for harness changes. Historical
data hashes and every behavior assertion retain their original checks.
"""
import argparse
import importlib.util
import importlib.machinery
import json
from pathlib import Path
import runpy
import subprocess
import sys
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
V16 = ROOT / "docs/evaluation/phase15/read_candidate_v16"
CERTIFICATE = HERE / "source-binding-v002-r4.json"
sys.path.insert(0, str(V16))
spec = importlib.util.spec_from_file_location("integrity", V16 / "integrity.py")
integrity = importlib.util.module_from_spec(spec)
sys.modules["integrity"] = integrity
spec.loader.exec_module(integrity)


def check():
    expected = json.loads(CERTIFICATE.read_text(encoding="utf-8"))
    actual = integrity.payload()
    original = json.loads(integrity.MANIFEST.read_text(encoding="utf-8"))
    if integrity.digest(integrity.MANIFEST, raw=True) != expected["original_v16_certificate_sha256"]:
        raise ValueError("Archived V16 certificate changed")
    for group in ("frozen_hashes", "frozen_intake006_lf_hashes"):
        if actual[group] != original[group]:
            raise ValueError("Historical artifact bytes changed: " + group)
    if actual["hashes"] != expected["current_source_hashes"]:
        raise ValueError("Harness source changed after source-binding-v002-r4 freeze")
    return actual


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--freeze", action="store_true")
    parser.add_argument("--v15", action="store_true")
    parser.add_argument("--current", action="store_true")
    args = parser.parse_args()
    if args.freeze:
        if CERTIFICATE.exists():
            raise ValueError("Source certificate already exists; use a new reviewed version")
        original = json.loads(integrity.MANIFEST.read_text(encoding="utf-8"))
        actual = integrity.payload()
        differences = sorted(name for name in set(actual["hashes"]) | set(original["hashes"])
                             if actual["hashes"].get(name) != original["hashes"].get(name))
        value = dict(purpose="Capability test harness only; not a model recertification or new ML score",
                     original_v16_certificate_sha256=integrity.digest(integrity.MANIFEST, raw=True),
                     changed_current_sources=differences, current_source_hashes=actual["hashes"])
        CERTIFICATE.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
        print("Created new source binding; V16 history unchanged:", *differences, sep="\n")
        return
    check()
    integrity.check = check
    if args.current:
        success = True
        original_exec = importlib.machinery.SourceFileLoader.exec_module

        def bound_exec(loader, module):
            original_exec(loader, module)
            if module.__name__ == "metis_v16_current_certificate" and Path(loader.path).resolve() == V16 / "integrity.py":
                module.check = check

        with patch.object(importlib.machinery.SourceFileLoader, "exec_module", bound_exec):
            for folder in (V16, V16.parent / "classifier_v16", V16.parent / "calibration_intake_006"):
                suite = unittest.TestLoader().discover(str(folder), pattern="test_*.py")
                success = unittest.TextTestRunner(verbosity=2).run(suite).wasSuccessful() and success
        raise SystemExit(not success)
    original_run = subprocess.run

    def preserved_run(command, *positional, **keywords):
        if len(command) >= 2 and Path(command[1]).resolve() == V16 / "historical_tests.py":
            command = [command[0], str(Path(__file__).resolve()), *command[2:]]
        return original_run(command, *positional, **keywords)

    with patch("subprocess.run", preserved_run), patch.object(sys, "argv", [str(V16 / "historical_tests.py")] +
                                                             (["--v15"] if args.v15 else [])):
        runpy.run_path(str(V16 / "historical_tests.py"), run_name="__main__")


if __name__ == "__main__":
    main()
