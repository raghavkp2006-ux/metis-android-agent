"""v0 tests use invented records, temporary approvals and tiny fixture budgets."""
import copy
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from test_splits import fixture_rows, independent_fixture
from test_data_management import embeddings
from ml.nlu_v1.dataset_v0 import acquire, build
from ml.nlu_v1.data_management.storage import json_bytes, jsonl_bytes


class V0Tests(unittest.TestCase):
    def test_production_quotas_sum_to_2000_and_groups_cover_all_domains(self):
        config = build.load_config()
        self.assertEqual(sum(config["quotas"].values()), 2000)
        self.assertEqual(config["minimum_rows"], 1500)
        self.assertEqual(config["quotas"]["OOD_UNSUPPORTED"], 250)
        self.assertEqual(len(build.DOMAIN_GROUPS), 19)

    def test_unsupported_bucket_precedes_domain_avoids_double_counting(self):
        row = {"domain": "KNOWLEDGE", "disposition": "KNOWN_UNSUPPORTED"}
        self.assertEqual(build.bucket(row), "OOD_UNSUPPORTED")
        row["disposition"] = "SUPPORTED_COMPLETE"
        self.assertEqual(build.bucket(row), "PREFERENCE_KNOWLEDGE_ETC")

    def test_source_preference_places_human_before_legacy_synthetic_and_boundaries(self):
        rows, _ = fixture_rows(1)
        row = rows[0]
        self.assertEqual(build.source_rank(row), 0)
        row["source"]["source_class"] = "TRAINABLE_LEGACY"
        self.assertEqual(build.source_rank(row), 1)
        row["source"]["source_class"] = "CONTROLLED_SYNTHETIC"
        self.assertEqual(build.source_rank(row), 2)
        row["metadata"]["generation_role"] = "HARD_NEGATIVE"
        self.assertEqual(build.source_rank(row), 3)

    def test_reports_preserve_counts_and_slot_unit(self):
        rows, _ = fixture_rows(3)
        report = build.distribution(rows)
        self.assertEqual(report["rows"], 3)
        self.assertEqual(report["slots"], {"DATE": 3, "TIME": 3})
        self.assertEqual(report["privacy"], {"UNKNOWN_PRIVACY": 3})
        self.assertEqual(sum(report["domains"].values()), 3)
        self.assertEqual(sum(report["groups"].values()), 3)

    def test_unapproved_rows_are_not_curated_or_changed(self):
        rows, groups = fixture_rows(3)
        original = copy.deepcopy(rows)
        selected, report = build.curate(rows, groups=groups)
        self.assertFalse(selected)
        self.assertGreater(report["rejection_reason_counts"]["ROW_NOT_APPROVED"], 0)
        self.assertEqual(rows, original)
        self.assertFalse(report["dataset_v0_frozen"])

    def test_row_count_and_missing_groups_cannot_be_padded_to_target(self):
        rows, groups = fixture_rows(3, approve_rows=True)
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp) / "v0"
            with self.assertRaisesRegex(ValueError, "ROW_COUNT_OUT_OF_RANGE"):
                build.build(rows, destination, groups=groups, embeddings=embeddings(rows))
            self.assertFalse(destination.exists())

    def test_empty_and_unresolved_families_are_not_silently_invented(self):
        selected, report = build.curate([])
        self.assertEqual(report["status"], "BLOCKED_EMPTY_CORPUS")
        rows, _ = fixture_rows(1)
        from ml.nlu_v1.data_management.records import append_event
        rows[0]["family"] = None
        append_event(rows[0], "PENDING", "PENDING", "fixture", "UNRESOLVED_FIXTURE")
        selected, report = build.curate(rows)
        self.assertEqual(report["status"], "BLOCKED_GROUP_METADATA_UNRESOLVED")
        self.assertFalse(selected)

    def test_successful_freeze_and_hash_verification_use_temporary_fixture_policy(self):
        with independent_fixture() as (rows, groups), tempfile.TemporaryDirectory() as temp:
            config = build.load_config()
            config.update(minimum_rows=10, maximum_rows=20, target_rows=13, required_groups=["REMINDER_ALARM_TIMER"])
            config["quotas"] = {key: 0 for key in config["quotas"]}
            config["quotas"]["REMINDER_ALARM_TIMER"] = 13
            config_path = Path(temp) / "fixture-policy.yaml"
            config_path.write_text(json.dumps(config), encoding="utf-8")
            destination = Path(temp) / "v0"
            with patch.object(build, "load_config", return_value=config), patch.object(build, "CONFIG", config_path):
                manifest = build.build(rows, destination, groups=groups, embeddings=embeddings(rows))
                self.assertEqual(manifest["status"], "FROZEN")
                self.assertEqual(manifest["scientific_rows"], 13)
                self.assertEqual(sum(manifest["class_counts"]["domains"].values()), 13)
                self.assertEqual(manifest["slot_counts"], {"DATE": 13, "TIME": 13})
                self.assertFalse(manifest["model_trained"])
                build.verify(destination)
                with self.assertRaisesRegex(ValueError, "DESTINATION_ALREADY_EXISTS"):
                    build.build(rows, destination, groups=groups, embeddings=embeddings(rows))
                manifest_path = destination / "dataset_v0_manifest.json"
                mutated = json.loads(manifest_path.read_text())
                mutated["slot_counts"] = {}
                manifest_path.write_text(json.dumps(mutated), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "DISTRIBUTION_SUMMARY_CHANGED"):
                    build.verify(destination)
                manifest_path.write_bytes(json_bytes(manifest))
                hashes = destination / "row_hashes.jsonl"
                hashes.write_bytes(hashes.read_bytes() + b"\n")
                with self.assertRaisesRegex(ValueError, "FROZEN_ARTIFACT_CHANGED"):
                    build.verify(destination)

    def test_missing_required_groups_remain_a_quality_gate(self):
        rows, groups = fixture_rows(3, approve_rows=True)
        config = build.load_config()
        config.update(minimum_rows=1, maximum_rows=5, target_rows=3)
        with patch.object(build, "load_config", return_value=config), tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "SEMANTIC_GROUPS_MISSING"):
                build.build(rows, Path(temp) / "v0", groups=groups)

    def test_complete_embeddings_required_even_for_rows_not_selected(self):
        rows, groups = fixture_rows(3, approve_rows=True)
        config = build.load_config()
        config.update(minimum_rows=1, maximum_rows=5, target_rows=3, required_groups=["REMINDER_ALARM_TIMER"])
        with patch.object(build, "load_config", return_value=config), tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(ValueError, "COMPLETE_CORPUS_EMBEDDINGS"):
                build.build(rows, Path(temp) / "v0", groups=groups)

    def test_network_downloader_is_bounded_and_does_not_replace_existing_bytes(self):
        class Response(io.BytesIO):
            url = "https://example.invalid/pinned"
            headers = {"Content-Length": "7"}
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "source"
            for _ in range(2):
                with patch.object(acquire.urllib.request, "urlopen", return_value=Response(b"fixture")):
                    result = acquire.download("https://example.invalid/pinned", target)
                self.assertEqual(result["sha256"], hashlib.sha256(b"fixture").hexdigest())
            with patch.object(acquire.urllib.request, "urlopen", return_value=Response(b"changed")):
                with self.assertRaisesRegex(ValueError, "EXISTING_ACQUISITION_BYTES_CHANGED"):
                    acquire.download("https://example.invalid/pinned", target)
            self.assertEqual(target.read_bytes(), b"fixture")
            self.assertEqual([file.name for file in Path(temp).iterdir()], ["source"])
            with self.assertRaisesRegex(ValueError, "HTTPS_SOURCE_REQUIRED"):
                acquire.download("http://example.invalid", target)

    def test_unclear_sources_cannot_be_downloaded(self):
        with self.assertRaisesRegex(ValueError, "NOT_ADMITTED"):
            acquire.fetch("mtop_en")

    def test_candidate_snapshot_is_explicitly_unfrozen_and_has_null_split_hashes(self):
        rows, _ = fixture_rows(2)
        with tempfile.TemporaryDirectory() as temp:
            destination = Path(temp) / "candidate-snapshot"
            manifest = build.snapshot_candidates(rows, destination)
            self.assertEqual(manifest["candidate_rows"], 2)
            self.assertFalse(manifest["dataset_v0_frozen"])
            self.assertFalse(manifest["training_authorized"])
            self.assertTrue(all(value is None for value in manifest["split_hashes"].values()))
            hashes = [json.loads(line) for line in (destination / "row_hashes.jsonl").read_text().splitlines()]
            self.assertEqual(len(hashes), 2)
            build.verify_candidates(destination)
            with self.assertRaisesRegex(ValueError, "FREEZE_MANIFEST_INVALID"):
                build.verify(destination)
            with self.assertRaisesRegex(ValueError, "SNAPSHOT_ALREADY_EXISTS"):
                build.snapshot_candidates(rows, destination)
            payload = destination / "candidates.jsonl"
            payload.write_bytes(payload.read_bytes() + b"\n")
            with self.assertRaisesRegex(ValueError, "CANDIDATE_ARTIFACT_CHANGED"):
                build.verify_candidates(destination)


if __name__ == "__main__":
    unittest.main()
