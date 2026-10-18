"""Isolated contract fixtures; never training or independent evaluation data."""
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from contract import LABELS, SCHEMA, WORKSPACE, validate, verify_freeze
from jsonschema import ValidationError


def output():
    return {"schema_version": "nlu_v1_schema_001", "text": "Call Ada",
            "tokens": [{"start": 0, "end": 4, "text": "Call"}, {"start": 5, "end": 8, "text": "Ada"}],
            "bio_tags": ["O", "B-PERSON"], "meaning": {"domain": "COMMUNICATION", "action": "CALL",
            "slots": [{"type": "PERSON", "start": 5, "end": 8, "text": "Ada"}],
            "disposition": "SUPPORTED_COMPLETE", "ood": False}}


def record():
    return {**output(), "id": "fixture:1", "language": "en-US", "split": "TRAIN",
            "family_id": "fixture-family", "scenario_id": "fixture-scenario",
            "provenance": {"source_id": "test-only", "source_version": "1", "source_record_id": "1",
                "source_class": "CONTROLLED_SYNTHETIC", "source_sha256": "0" * 64,
                "source_split": "fixture", "mapper_version": "test", "license_id": "test-only",
                "approval_id": "test-only", "privacy": "SYNTHETIC_NONPERSONAL",
                "author_id": "author", "reviewer_id": "reviewer", "generator_id": "unit-test", "parent_ids": []}}


class ContractTests(unittest.TestCase):
    def reject(self, value, kind="output"):
        with self.assertRaises((ValueError, ValidationError)):
            validate(value, kind)

    def test_exact_approved_catalogs(self):
        expected = {
            "domains": "TASK CALENDAR MEMORY PROMISE PERSON GOAL PROJECT PLANNING REMINDER ALARM TIMER FOCUS HABIT COMMUNICATION NAVIGATION PREFERENCE REFLECTION KNOWLEDGE UNKNOWN",
            "actions": "READ CREATE UPDATE DELETE COMPLETE POSTPONE START STOP SET PLAN REVIEW CALL SEND NAVIGATE RECOMMEND NONE",
            "slots": "PERSON SUBJECT DATE TIME DATE_RANGE DURATION STATUS PRIORITY LOCATION PROJECT GOAL TASK EVENT FIELD KIND RECURRENCE QUANTITY CHANNEL PREFERENCE_VALUE REFERENCE",
            "dispositions": "SUPPORTED_COMPLETE SUPPORTED_NEEDS_CONTEXT SUPPORTED_NEEDS_CONFIRMATION KNOWN_UNSUPPORTED OUT_OF_DOMAIN",
        }
        for name, text in expected.items():
            self.assertEqual(text.split(), LABELS[name])

    def test_valid_output_and_record(self):
        validate(output(), "output")
        validate(record())

    def test_unknown_labels(self):
        for key, value in (("domain", "SAFETY"), ("action", "CALL_PERSON"), ("disposition", "REQUEST")):
            row = output()
            row["meaning"][key] = value
            self.reject(row)
        for tag in ("B-BOGUS", "E-PERSON", "PERSON"):
            row = output()
            row["bio_tags"][1] = tag
            self.reject(row)
        row = output()
        row["meaning"]["slots"][0]["type"] = "TITLE"
        self.reject(row)

    def test_bio_continuations(self):
        for tags in (["I-PERSON", "O"], ["O", "I-PERSON"], ["B-TASK", "I-PERSON"]):
            row = output()
            row["bio_tags"] = tags
            self.reject(row)
        row = output()
        row["bio_tags"] = ["B-SUBJECT", "I-SUBJECT"]
        row["meaning"]["slots"] = [{"type": "SUBJECT", "start": 0, "end": 8, "text": "Call Ada"}]
        validate(row, "output")

    def test_bio_adjacent_same_type_begins_new_span(self):
        row = output()
        row["bio_tags"] = ["B-SUBJECT", "B-SUBJECT"]
        row["meaning"]["slots"] = [{"type": "SUBJECT", **token} for token in row["tokens"]]
        validate(row, "output")

    def test_bio_token_length_and_slot_agreement(self):
        for tags in (["O"], ["O", "O"], ["O", "B-TASK"]):
            row = output()
            row["bio_tags"] = tags
            self.reject(row)

    def test_slot_text_and_bounds(self):
        for patch in ({"text": "Bob"}, {"end": 20}, {"start": 8, "end": 5}, {"start": True}):
            row = output()
            row["meaning"]["slots"][0].update(patch)
            self.reject(row)

    def test_token_text_bounds_coverage_order(self):
        for patch in ({"text": "Bob"}, {"end": 20}, {"start": 8}, {"start": True}):
            row = output()
            row["tokens"][1].update(patch)
            self.reject(row)
        for tokens in (output()["tokens"][1:], list(reversed(output()["tokens"]))):
            row = output()
            row["tokens"] = tokens
            row["bio_tags"] = ["O"] * len(tokens)
            self.reject(row)

    def test_utf16_surrogate_boundaries_and_raw_spans(self):
        row = output()
        row["text"] = "📞 Call Ada"
        row["tokens"] = [{"start": 0, "end": 2, "text": "📞"},
                         {"start": 3, "end": 7, "text": "Call"}, {"start": 8, "end": 11, "text": "Ada"}]
        row["bio_tags"] = ["O", "O", "B-PERSON"]
        row["meaning"]["slots"][0].update(start=8, end=11)
        validate(row, "output")
        row["tokens"][0]["start"] = 1
        self.reject(row)
        row["text"] = "\ud800"
        self.reject(row)

    def test_blank_and_utf16_limit(self):
        for text in (" ", "📞" * 2001):
            row = output()
            row["text"] = text
            self.reject(row)

    def test_overlap_and_duplicate_spans(self):
        row = output()
        row["meaning"]["slots"].append(copy.deepcopy(row["meaning"]["slots"][0]))
        self.reject(row)

    def test_illegal_dispositions_and_ood(self):
        for patch in ({"ood": True}, {"disposition": "OUT_OF_DOMAIN"},
                      {"domain": "UNKNOWN"}, {"action": "NONE"}):
            row = output()
            row["meaning"].update(patch)
            self.reject(row)
        row = output()
        row["meaning"].update(domain="UNKNOWN", action="NONE", disposition="OUT_OF_DOMAIN", ood=True)
        validate(row, "output")
        row["meaning"].update(disposition="KNOWN_UNSUPPORTED", ood=False)
        validate(row, "output")

    def test_supported_dispositions_never_add_authority(self):
        for disposition in LABELS["dispositions"][:4]:
            row = output()
            row["meaning"]["disposition"] = disposition
            validate(row, "output")
        for key in ("authorized", "execute", "requires_confirmation", "resolved_entity_id"):
            row = output()
            row["meaning"][key] = True
            self.reject(row)
        row = output()
        row["meaning"]["slots"][0]["normalized_value"] = "contact-1"
        self.reject(row)

    def test_dates_and_references_remain_raw(self):
        for text, slot_type in (("next Friday", "DATE"), ("him", "REFERENCE"), ("that meeting", "REFERENCE")):
            row = output()
            row["text"] = text
            words = text.split()
            cursor = 0
            row["tokens"] = []
            for word in words:
                row["tokens"].append({"start": cursor, "end": cursor + len(word), "text": word})
                cursor += len(word) + 1
            row["bio_tags"] = ["B-" + slot_type] + ["I-" + slot_type] * (len(words) - 1)
            row["meaning"]["slots"] = [{"type": slot_type, "start": 0, "end": len(text), "text": text}]
            row["meaning"]["disposition"] = "SUPPORTED_NEEDS_CONTEXT"
            validate(row, "output")

    def test_malformed_rows(self):
        for value in (None, [], {}, {**record(), "schema_version": "metis-nlu-v1.0"}):
            self.reject(value, "record")
        row = record()
        del row["provenance"]["approval_id"]
        self.reject(row, "record")

    def test_final_and_synthetic_provenance_constraints(self):
        row = record()
        row["split"] = "FINAL_INDEPENDENT_TEST"
        self.reject(row, "record")
        row["provenance"].update(source_class="INDEPENDENT_EVALUATION", generator_id=None)
        validate(row)
        for patch in ({"author_id": "reviewer"}, {"generator_id": "model"}, {"privacy": "PRIVATE_DO_NOT_PUBLISH"}):
            invalid = copy.deepcopy(row)
            invalid["provenance"].update(patch)
            self.reject(invalid, "record")
        row = record()
        row["provenance"]["generator_id"] = None
        self.reject(row, "record")

    def test_self_parent_rejected(self):
        row = record()
        row["provenance"]["parent_ids"] = [row["id"]]
        self.reject(row, "record")

    def run_cli(self, *args):
        return subprocess.run([sys.executable, str(WORKSPACE / "contract.py"), *args], capture_output=True, text=True)

    def test_jsonl_cli_rejects_bad_rows_without_echoing_text(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixtures.jsonl"
            path.write_text(json.dumps(record()) + "\n", encoding="utf-8")
            self.assertEqual(0, self.run_cli("--validate-jsonl", str(path)).returncode)
            invalid = record()
            invalid["text"] = "PRIVATE_MARKER"
            for text in ("", "\n", "{", json.dumps(record()) + "\n" + json.dumps(invalid),
                         json.dumps(record())[:-1] + ', "id": "duplicate"}'):
                path.write_text(text, encoding="utf-8")
                result = self.run_cli("--validate-jsonl", str(path))
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("PRIVATE_MARKER", result.stdout + result.stderr)
            path.write_bytes(b"PRIVATE_MARKER\xff")
            result = self.run_cli("--validate-jsonl", str(path))
            self.assertNotEqual(0, result.returncode)
            self.assertNotIn("PRIVATE_MARKER", result.stdout + result.stderr)

    def test_frozen_artifacts_and_drift_detection(self):
        self.assertEqual(0, self.run_cli("--check").returncode)
        self.assertNotEqual(0, self.run_cli("--write-schemas").returncode)
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "schema"
            shutil.copytree(SCHEMA, target)
            verify_freeze(target)
            with (target / "domains.yaml").open("a", encoding="utf-8") as stream:
                stream.write("# drift\n")
            with self.assertRaises(ValueError):
                verify_freeze(target)


if __name__ == "__main__":
    unittest.main()
