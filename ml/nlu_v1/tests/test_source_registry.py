"""Registry policy regression tests; no dataset payloads or network requests."""
import copy
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from source_registry import check_evidence, load_registry, require_use, validate_registry


class RegistryTests(unittest.TestCase):
    def setUp(self):
        self.registry = load_registry()
        self.sources = {s["id"]: s for s in self.registry["sources"]}

    def reject(self):
        with self.assertRaises(ValueError):
            validate_registry(self.registry)

    def test_complete_registry_and_metadata_pins(self):
        check_evidence(self.registry)
        self.assertEqual(5, sum(s["tier"] == "PRIMARY" for s in self.sources.values()))
        self.assertEqual(6, sum(s["tier"] == "SECONDARY" for s in self.sources.values()))

    def test_missing_and_duplicate_sources(self):
        self.registry["sources"].append(copy.deepcopy(self.registry["sources"][0]))
        self.reject()
        self.registry["sources"].pop()
        self.registry["sources"].pop()
        self.reject()

    def test_missing_license_identity_and_unresolved_splits_block(self):
        for source_id in ("mtop_en", "hwu_nlu_evaluation", "slurp_audio"):
            fresh = load_registry()
            next(s for s in fresh["sources"] if s["id"] == source_id)["metis_status"] = "REGISTERED_PENDING_INTAKE"
            with self.assertRaises(ValueError):
                validate_registry(fresh)

    def test_protected_splits_never_promoted(self):
        for source_id, split in (("massive_1_1_en_us", "dev"), ("mtop_en", "test"), ("clinc150_full", "oos_test"), ("banking77", "test")):
            fresh = load_registry()
            next(s for s in fresh["sources"] if s["id"] == source_id)["allowed_splits"][split].append("TRAIN")
            with self.assertRaises(ValueError):
                validate_registry(fresh)

    def test_quarantined_legacy_classes_are_never_eligible(self):
        self.sources["metis_trainable_legacy"]["allowed_splits"]["EXPOSED"] = ["TRAIN"]
        self.reject()

    def test_smoke_and_noncommercial_audio_restrictions(self):
        self.sources["snips_custom_intents"]["allowed_splits"]["train"] = ["TRAIN"]
        self.reject()
        self.registry = load_registry()
        next(s for s in self.registry["sources"] if s["id"] == "slurp_audio")["allowed_splits"] = {"train": ["TRAIN"]}
        self.reject()
        self.registry = load_registry()
        self.registry["sources"][0]["download"]["sha256"] = "0" * 64
        self.reject()

    def test_missing_mapping_and_global_use_are_deny_by_default(self):
        self.registry["policy"]["training_authorized"] = True
        self.reject()
        self.registry["policy"]["training_authorized"] = False
        self.registry["policy"]["unmapped_intent"] = "MAP"
        self.reject()

    def test_actual_use_denied_even_for_planned_train_split(self):
        for source_id, split, purpose in (("massive_1_1_en_us", "train", "TRAIN"), ("clinc150_full", "oos_test", "TRAIN"),
                                          ("nonexistent", "train", "TRAIN"), ("metis_trainable_legacy", "TRAINABLE_LEGACY", "TRAIN"),
                                          ("snips_custom_intents", "test", "SMOKE_TEST")):
            with self.assertRaises(ValueError):
                require_use(self.registry, source_id, split, purpose)

    def test_frozen_schema_stays_separate(self):
        from contract import verify_freeze
        verify_freeze()


if __name__ == "__main__":
    unittest.main()
