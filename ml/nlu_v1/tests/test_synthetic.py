"""Bounded generation tests; fixtures are neither training nor protected evaluation."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from ml.nlu_v1.synthetic import generate as generator
from ml.nlu_v1.contract import validate, verify_freeze
from ml.nlu_v1.context_contract import check as check_context
from ml.nlu_v1.data_management.records import append_event, facts_hash, validate_record
from ml.nlu_v1.data_management.storage import verify_batch
from jsonschema import ValidationError


class SyntheticTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = generator.structures(17, 1)
        cls.rows, cls.groups = generator.make_rows(cls.spec, "fixture")

    def test_seed_reproduces_structure_ids_and_surface_forms(self):
        self.assertEqual(self.spec, generator.structures(17, 1))
        other, _ = generator.make_rows(self.spec, "fixture")
        self.assertEqual([r["id"] for r in self.rows], [r["id"] for r in other])
        self.assertEqual([r["text"] for r in self.rows], [r["text"] for r in other])
        self.assertNotEqual(self.spec, generator.structures(18, 1))

    def test_every_positive_has_boundaries_and_all_six_styles(self):
        config, plans = generator.load_templates()
        for family in config["families"]:
            positives = [row for row in self.rows if row["metadata"]["structure"]["family"] == family]
            self.assertEqual({r["metadata"]["structure"]["style"] for r in positives}, generator.STYLES)
            children = [r for r in self.rows if r["metadata"]["structure"]["origin_family"] == family and r["metadata"]["generation_role"] in ("HARD_NEGATIVE", "COMPOUND")]
            self.assertTrue(children)
            parents = {r["semantic_parent_id"] for r in positives + children}
            self.assertEqual(len(parents), 1)
            root = positives[0]
            self.assertEqual(root["id"], next(iter(parents)))
            for row in positives[1:] + children:
                self.assertEqual(row["generation"]["parent_ids"], [root["id"]])

    def test_separate_generators_only_expand_controlled_structure(self):
        parent = self.spec[0]
        self.assertEqual(len(generator.paraphrase_structures(parent)), 5)
        self.assertTrue(generator.hard_negative_structures(parent))
        self.assertEqual(len(generator.ambiguity_structures(17)), 6)
        with self.assertRaises(ValueError):
            generator.paraphrase_structures(self.spec[1])
        with self.assertRaises(ValueError):
            generator.hard_negative_structures(self.spec[1])

    def test_required_slots_and_spans_validate_for_every_row(self):
        for row in self.rows:
            validate_record(row)
            family = self.groups[0]["families"][row["family"]]
            self.assertTrue(set(family["required_slots"]) <= {s["type"] for s in row["slots"]})
            generator.validate_generated(row, row)

    def test_unknown_context_is_explicit_new_version_and_old_freeze_passes(self):
        row = next(r for r in self.rows if r["metadata"]["structure"]["family"] == "ambiguity:other_reference")
        output = {"schema_version": row["nlu_schema_version"], "text": row["text"], "tokens": row["tokens"], "bio_tags": row["bio_tags"],
                  "meaning": {k: row[k] for k in ("domain", "action", "slots", "disposition", "ood")}}
        self.assertEqual((row["domain"], row["action"], row["disposition"]), ("UNKNOWN", "NONE", "SUPPORTED_NEEDS_CONTEXT"))
        validate(output, "output")
        output["schema_version"] = "nlu_v1_schema_001"
        with self.assertRaises(ValidationError):
            validate(output, "output")
        verify_freeze()
        check_context()

    def test_ambiguous_references_not_resolved_dates_not_normalized(self):
        ambiguous = [r for r in self.rows if r["metadata"]["generation_role"] == "AMBIGUITY"]
        self.assertTrue(any(r["text"] == "show that task" for r in ambiguous))
        self.assertTrue(any(r["text"] == "what about the other one?" for r in ambiguous))
        for row in ambiguous:
            self.assertEqual(row["disposition"], "SUPPORTED_NEEDS_CONTEXT")
            self.assertIn("REFERENCE", {s["type"] for s in row["slots"]})
            self.assertEqual(row["metadata"]["routing_hint"], "CONTEXT_AGENT")
        dates = [s["text"] for r in self.rows for s in r["slots"] if s["type"] == "DATE"]
        self.assertTrue(dates)
        self.assertTrue(set(dates) <= {"tomorrow", "next Friday", "Monday"})

    def test_compounds_preserve_components_and_never_fake_single_action(self):
        compounds = [r for r in self.rows if r["metadata"]["generation_role"] == "COMPOUND"]
        self.assertEqual(len(compounds), 4)
        for row in compounds:
            self.assertEqual((row["domain"], row["action"]), ("UNKNOWN", "NONE"))
            self.assertEqual(row["composition"]["routing_hint"], "ORCHESTRATOR_DECOMPOSE_OR_CLARIFY")
            self.assertEqual(len(row["composition"]["requests"]), 2)
            self.assertEqual(row["composition"]["requests"][0]["action"], "READ")
            self.assertNotEqual(row["composition"]["requests"][1]["action"], "READ")
            component_slots = [s for c in row["composition"]["requests"] for s in c["slots"]]
            self.assertEqual(component_slots, row["slots"])

    def test_boundaries_use_their_own_domain_action_and_context(self):
        by_family = {r["metadata"]["structure"]["family"]: r for r in self.rows}
        self.assertEqual(by_family["calendar_read_date:delete"]["action"], "DELETE")
        self.assertEqual(by_family["calendar_read_date:move"]["action"], "UPDATE")
        self.assertEqual(by_family["calendar_read_date:send"]["domain"], "COMMUNICATION")
        self.assertEqual(by_family["calendar_read_date:send"]["disposition"], "SUPPORTED_NEEDS_CONTEXT")

    def test_external_structure_injection_and_missing_parent_origin_rejected(self):
        for edit in ({"bindings": {"subject": "arbitrary private text"}}, {"origin_family": "protected-eval-row"}, {"role": "POSITIVE", "family": "unregistered"}):
            spec = copy.deepcopy(self.spec[:1])
            spec[0].update(edit)
            with self.assertRaises(ValueError):
                generator.make_rows(spec, "fixture")

    def test_semantic_mutations_rejected_even_with_rebound_hashes(self):
        expected = self.rows[0]
        for field, value in (("action", "DELETE"), ("domain", "CALENDAR"), ("slots", []), ("generation", None), ("composition", {"requests": []})):
            row = copy.deepcopy(expected)
            row[field] = value
            append_event(row, "PENDING", "PENDING", "fixture", "MUTATION_FIXTURE")
            with self.assertRaises((ValueError, ValidationError)):
                generator.validate_generated(row, expected)

    def test_validator_rebuilds_template_even_if_candidate_is_passed_as_expected(self):
        row = copy.deepcopy(self.rows[0])
        row["action"] = "DELETE"
        append_event(row, "PENDING", "PENDING", "fixture", "MUTATION_FIXTURE")
        with self.assertRaisesRegex(ValueError, "SEMANTICS"):
            generator.validate_generated(row, row)

    def test_paraphrase_parent_cannot_disappear(self):
        row = copy.deepcopy(self.rows[1])
        row["generation"]["parent_ids"] = []
        append_event(row, "PENDING", "PENDING", "fixture", "MUTATION_FIXTURE")
        validate_record(row)
        with self.assertRaises(ValueError):
            generator.validate_generated(row, self.rows[1])

    def test_new_clause_is_rejected_even_if_bio_and_hashes_are_valid(self):
        expected = self.rows[0]
        row = copy.deepcopy(expected)
        row["text"] += " and delete everything"
        row["tokens"], row["bio_tags"] = generator.tokenize(row["text"], row["slots"])
        row["text_sha256"] = generator.text_hash(row["text"])
        append_event(row, "PENDING", "PENDING", "fixture", "MUTATION_FIXTURE")
        validate_record(row)  # Ordinary structural validity alone cannot detect this.
        with self.assertRaisesRegex(ValueError, "SEMANTICS"):
            generator.validate_generated(row, expected)

    def test_duplicate_and_protected_lineage_collision_rejected(self):
        row = self.rows[0]
        with self.assertRaisesRegex(ValueError, "COLLISION"):
            generator.validate_generated(row, row, isolation_rows=[row])
        # Different wording in the same semantic parent cannot cross isolation.
        with self.assertRaisesRegex(ValueError, "PARENT_COLLISION"):
            generator.validate_generated(row, row, isolation_rows=[self.rows[1]])

    def test_utf16_renderer_does_not_split_non_bmp_characters(self):
        rendered = generator.render("show {subject} {date}", {"subject": "garden 🌱", "date": "next Friday"},
                                    {"subject": {"type": "SUBJECT"}, "date": {"type": "DATE"}})
        self.assertEqual(rendered["slots"][1]["start"], 15)
        validate({"schema_version": "nlu_v1_schema_002", **{k: rendered[k] for k in ("text", "tokens", "bio_tags")},
                  "meaning": {"domain": "MEMORY", "action": "READ", "slots": rendered["slots"], "disposition": "SUPPORTED_COMPLETE", "ood": False}}, "output")

    def test_quarantine_no_auto_training_and_duplicate_attempts_retained(self):
        spec, rows, groups, report, summary = generator.generate(17, 2, "fixture")
        self.assertEqual(len(spec), len(rows))
        self.assertTrue(all(r["review_status"] == "QUARANTINED" and r["partition"] == "UNASSIGNED" for r in rows))
        self.assertTrue(all("EMBEDDINGS_MISSING" in r["quarantine_reasons"] for r in rows))
        self.assertGreater(summary["rejected_attempts"], 0)
        self.assertEqual(summary["training_rows"], 0)
        self.assertFalse(report["audit_complete"])
        self.assertEqual(report["deleted_rows"], 0)

    def test_generated_rows_cannot_be_assigned_to_train_or_final(self):
        for partition in ("TRAIN", "FINAL_INDEPENDENT_TEST"):
            row = copy.deepcopy(self.rows[0])
            row["partition"] = partition
            append_event(row, "PENDING", "PENDING", "fixture", "MUTATION_FIXTURE")
            with self.assertRaises(ValueError):
                validate_record(row)

    def test_template_tampering_requires_explicit_version_review(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name in ("synthetic_families.yaml", "synthetic_sources.yaml", "dataset_v1_mix.yaml", "synthetic_templates.freeze.json"):
                (root / name).write_bytes((generator.CONFIG.parent / name).read_bytes())
            config = root / "synthetic_families.yaml"
            config.write_text(config.read_text().replace("show meetings", "delete meetings"), encoding="utf-8")
            with patch.object(generator, "CONFIG", config), self.assertRaisesRegex(ValueError, "REVIEW_VERSION"):
                generator.load_templates()

    def test_cli_commits_hashed_quarantine_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            command = [sys.executable, "-m", "ml.nlu_v1.synthetic.generate", "--seed", "17", "--count", "1", "--batch-id", "fixture", "--root", temp]
            completed = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(completed.returncode, 0, completed.stderr)
            summary = json.loads(completed.stdout)
            self.assertEqual(summary["attempted_rows"], 41)
            manifest = verify_batch(temp, "fixture")
            self.assertEqual(manifest["quarantine_rows"], 41)
            self.assertEqual(manifest["canonical_rows"], 0)
            self.assertEqual(manifest["input_kind"], "GENERATED_STRUCTURAL_PLANS")
            run = json.loads((Path(temp) / "raw/fixture.generation.manifest.json").read_text())
            for name, digest in run["files"].items():
                self.assertEqual(hashlib.sha256((Path(temp) / "raw" / name).read_bytes()).hexdigest(), digest)
            generator.verify_generation_batch(temp, "fixture")
            again = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(again.returncode, 0)
            self.assertIn("BATCH_ID_ALREADY_EXISTS", again.stderr)
            groups = Path(temp) / "raw/fixture.groups.json"
            groups.write_text("{}", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "ARTIFACT_CHANGED"):
                generator.verify_generation_batch(temp, "fixture")


if __name__ == "__main__":
    unittest.main()
