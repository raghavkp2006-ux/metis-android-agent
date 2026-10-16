"""Integrity and disguised-asset failure cases, without touching real evidence."""
import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
