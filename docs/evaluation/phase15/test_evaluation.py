"""Regressions for leakage, truncated data, stale baselines, and fail-closed gates."""

import copy
import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from build_dataset import FAMILIES, build
from corpus import read_csv, validate, write_csv
from evaluate import HERE, REQUIRED_GATES, adoption_decision, load_baseline, partition_validation
from safety_cases import build_safety


class EvaluationIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.rows = read_csv(HERE / "intent-v2.csv")

    def test_family_leakage_is_rejected(self):
        original = self.rows[0]
        duplicate = copy.deepcopy(original)
        duplicate.update(id="leaked-family", utterance=original["utterance"] + " later",
                         split="test" if original["split"] != "test" else "train")
        with self.assertRaisesRegex(ValueError, "Family leakage"):
            validate(self.rows + [duplicate])

    def test_normalized_duplicate_is_rejected(self):
        duplicate = copy.deepcopy(self.rows[0])
        duplicate.update(id="duplicate-text", utterance="  " + duplicate["utterance"].upper() + "  ")
        with self.assertRaisesRegex(ValueError, "Duplicate normalized text"):
            validate(self.rows + [duplicate])

    def test_truncated_corpus_without_unknown_label_is_rejected(self):
        rows = [row for row in self.rows if row["intent"] != "UNKNOWN"]
        with self.assertRaisesRegex(ValueError, "Incomplete label coverage"):
            validate(rows)

    def test_entity_offsets_use_utf16_and_cannot_split_surrogates(self):
        rows = build_safety()
        validate(rows, require_splits=False)
        row = next(row for row in rows if row["case_kind"] == "unicode")
        import json
        entities = json.loads(row["entities"])
        entities[0]["endOffset"] -= 1
        row["entities"] = json.dumps(entities)
        with self.assertRaises(ValueError):
            validate(rows, require_splits=False)

    def test_invalid_generated_input_preserves_previous_csv(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "intent-v2.csv"
            target.write_text("previous complete artifact", encoding="utf-8")
            with patch.dict(FAMILIES, {"CHECK_TASKS": FAMILIES["CHECK_TASKS"][:-1]}):
                with self.assertRaisesRegex(ValueError, "Expected 20 templates"):
                    build(target)
            self.assertEqual("previous complete artifact", target.read_text(encoding="utf-8"))

    def test_invalid_csv_does_not_replace_previous_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "intent.csv"
            target.write_text("previous artifact", encoding="utf-8")
            with self.assertRaises(ValueError):
                write_csv(target, self.rows + [self.rows[0]])
            self.assertEqual("previous artifact", target.read_text(encoding="utf-8"))

    def test_stale_native_source_hash_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "baseline.tsv"
            target.write_text("# input_sha256=current-input\n# source_sha256=old-source\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Stale native baseline"):
                load_baseline(target, self.rows, "current-input", "current-source")

    def test_missing_native_predictions_cannot_count_as_success(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "baseline.tsv"
            target.write_text("# input_sha256=current-input\n# source_sha256=current-source\n"
                              "id\tintent\tnegated\tstatus\tentities\treads\tproposals\tcompleted\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "missing or unexpected requests"):
                load_baseline(target, self.rows, "current-input", "current-source")

    def test_perfect_accuracy_cannot_bypass_unmeasured_device_gate(self):
        gates = {key: {"met": True} for key in REQUIRED_GATES}
        gates["android_parity_latency_ram_battery"]["met"] = False
        self.assertEqual("NO_GO", adoption_decision(gates))

    def test_missing_gates_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Missing or unexpected adoption gates"):
            adoption_decision({"macro_f1_0_90": {"met": True}})

    def test_unsafe_predictions_cannot_be_hidden_by_good_accuracy(self):
        gates = {key: {"met": True} for key in REQUIRED_GATES}
        gates["sample_unknown_rejection"]["met"] = False
        self.assertEqual("NO_GO", adoption_decision(gates))

    def test_calibration_and_threshold_selection_use_disjoint_families(self):
        rows = [row for row in self.rows if row["split"] == "validation"]
        calibration, threshold = partition_validation(rows)
        self.assertEqual({row["id"] for row in rows}, {row["id"] for row in calibration + threshold})
        self.assertFalse({row["family_id"] for row in calibration} & {row["family_id"] for row in threshold})
        self.assertEqual({row["intent"] for row in calibration}, {row["intent"] for row in threshold})

    def test_checked_in_data_matches_reproducible_builder(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "intent-v2.csv"
            with contextlib.redirect_stdout(io.StringIO()):
                build(output)
            for name in ("intent-v2.csv", "safety-v2.csv", "rules-input-v2.tsv", "manifest-v2.json"):
                self.assertEqual((HERE / name).read_bytes(), (output.parent / name).read_bytes(), name)


if __name__ == "__main__":
    unittest.main()
