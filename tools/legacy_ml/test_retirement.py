"""Integrity and disguised-asset failure cases, without touching real evidence."""
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import check
import retire
from check import check_apk
from retire import safe_path


class RetirementTests(unittest.TestCase):
    def test_manifest_cannot_escape_workspace(self):
        with self.assertRaises(ValueError):
            safe_path("../outside.json")

    def apk(self, name, data, hashes=()):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr(name, data)
            return check_apk(path, set(hashes))

    def test_rejects_disguised_model_payload(self):
        with self.assertRaises(ValueError):
            self.apk("assets/settings.json", '{"coefficients": [1], "intercept": [0]}')

    def test_rejects_renamed_exact_frozen_model(self):
        data = b"frozen classifier bytes"
        with self.assertRaises(ValueError):
            self.apk("assets/settings.bin", data, [hashlib.sha256(data).hexdigest()])

    def test_rejects_legacy_binary_extension(self):
        with self.assertRaises(ValueError):
            self.apk("assets/intent.tflite", b"binary")

    def test_rejects_disguised_hierarchical_export(self):
        with self.assertRaises(ValueError):
            self.apk("assets/settings.bin", '{"heads": {}, "payload_sha256": "legacy"}')

    def test_allows_deterministic_resource(self):
        self.assertEqual(0, self.apk("assets/settings.json", '{"theme": "blue"}')["legacy_models"])

    def repository(self, root):
        """Minimal public archive; no private evidence or evaluation fixtures."""
        archive = root / "archive/legacy_ml"
        files = {
            archive / "manifest.json": {"files": []},
            archive / "benchmark-registry.json": {
                "production_ml_enabled": False, "new_model_trained": False, "models": {}},
            root / "datasets/legacy/catalog.json": {"files": []},
        }
        for path, data in files.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data), encoding="utf-8")
        public = archive / "public/hashes.json"
        public.parent.mkdir(parents=True)
        public.write_text(json.dumps({"files": {
            path.relative_to(root).as_posix(): retire.digest(path)
            for path in files if path.is_relative_to(archive)
        }}), encoding="utf-8")
        gradle = root / "app/build.gradle.kts"
        gradle.parent.mkdir(parents=True)
        gradle.write_text("// fixture", encoding="utf-8")
        return archive

    def test_production_edits_and_new_files_do_not_require_retirement_recertification(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = self.repository(root)
            source = root / "app/src/main/java/Example.kt"
            source.parent.mkdir(parents=True)
            source.write_text("package example\n", encoding="utf-8")
            with patch.object(check, "ROOT", root), patch.object(check, "ARCHIVE", archive), \
                    patch.object(retire, "ROOT", root):
                self.assertEqual(set(), check.check_repository())
                source.write_text("package example\n// comment-only edit\n", encoding="utf-8")
                self.assertEqual(set(), check.check_repository())
                source.with_name("New.kt").write_text("package example\n", encoding="utf-8")
                self.assertEqual(set(), check.check_repository())

    def test_public_archive_tampering_is_still_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = self.repository(root)
            manifest = archive / "manifest.json"
            manifest.write_text('{"files": [], "tampered": true}', encoding="utf-8")
            with patch.object(check, "ROOT", root), patch.object(check, "ARCHIVE", archive), \
                    patch.object(retire, "ROOT", root):
                with self.assertRaisesRegex(ValueError, "Public archive metadata changed"):
                    check.check_repository()


if __name__ == "__main__":
    unittest.main()
