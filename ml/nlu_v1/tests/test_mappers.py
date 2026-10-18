"""Synthetic parser fixtures only; no public payloads or historical private rows."""
import copy
import csv
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from jsonschema import ValidationError

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from ml.nlu_v1.ingestion.common import HERE, LABELS, REGISTRY, MAPPER_VERSION, MappingError, digest, schema, to_frozen_record, validate_row
from contract import validate as validate_frozen, utf16_boundaries
from ml.nlu_v1.ingestion.check_mappings import check
from ml.nlu_v1.ingestion.ingest import approve_intake, file_digest, ingest, iter_native
from ml.nlu_v1.ingestion import massive_mapper, hwu_mapper, mtop_mapper, clinc_mapper, legacy_metis_mapper


def massive(split="train"):
    return {"id": "7", "locale": "en-US", "partition": split, "scenario": "alarm", "intent": "alarm_set",
            "utt": "Set alarm next Friday at 8", "annot_utt": "Set alarm [date : next Friday] at [time : 8]", "worker_id": "worker-1"}


def hwu(split="annotated_corpus"):
    return {"answerid": "8", "userid": "speaker-1", "scenario": "alarm", "intent": "set", "status": "OK",
            "answer": "Set alarm next Friday", "answer_annotation": "Set alarm [date : next Friday]", "split": split}


def mtop(split="train"):
    return {"id": "9", "locale": "en", "text": "Show Ada meeting next Friday", "split": split,
            "parse": "[IN:GET_EVENT Show [SL:PERSON Ada ] [SL:EVENT_NAME meeting ] [SL:DATE next Friday ] ]"}


def legacy(split="train", intent="CHECK_TASKS"):
    return {"id": "10", "text": "Show my tasks", "intent": intent, "entities": [], "split": split,
            "classification": "TRAINABLE_LEGACY", "exposure": "TRAINABLE_LEGACY", "privacy": "SYNTHETIC_NONPERSONAL"}


def legacy_approval(raw):
    return {"source_id": raw["id"], "text_sha256": digest(raw["text"]), "classification": "TRAINABLE_LEGACY",
            "license_verified": True, "privacy_reviewed": True, "approval_id": "fixture-only"}


def mapped_fixtures(protected=False):
    old = legacy("test" if protected else "train")
    return [
        (massive_mapper.map_record(massive("test" if protected else "train")), massive()["utt"], "7", "test" if protected else "train"),
        (hwu_mapper.map_record(hwu("cross_validation_folds" if protected else "annotated_corpus")), hwu()["answer"], "8", "cross_validation_folds" if protected else "annotated_corpus"),
        (mtop_mapper.map_record(mtop("test" if protected else "train")), mtop()["text"], "9", "test" if protected else "train"),
        (clinc_mapper.map_record(["Freeze my account", "freeze_account"], "test" if protected else "train", "11"), "Freeze my account", "11", "test" if protected else "train"),
        (legacy_metis_mapper.map_record(old, row_approval=legacy_approval(old)), old["text"], "10", old["split"]),
    ]


def promotion(row, split="TRAIN"):
    # Simulate trusted intake evidence for isolated synthetic fixtures only.
    row["source"].update(artifact_sha256="1" * 64, intake_approval_id="fixture-intake")
    source = row["source"]
    provenance = {"source_id": source["dataset"], "source_version": source["version"], "source_record_id": source["source_id"],
                  "source_class": "TRAINABLE_LEGACY" if source["dataset"] == "metis_trainable_legacy" else "PUBLIC_DATASET",
                  "source_sha256": "1" * 64, "source_split": source["original_split"], "mapper_version": MAPPER_VERSION,
                  "license_id": REGISTRY[source["dataset"]]["license"]["id"] or "unverified",
                  "approval_id": "fixture-only", "privacy": row["privacy"], "author_id": "fixture-author",
                  "reviewer_id": "fixture-reviewer", "generator_id": None, "parent_ids": []}
    approval = {"canonical_sha256": digest(json.dumps(row, sort_keys=True, ensure_ascii=False, separators=(",", ":"))),
                "artifact_sha256": "1" * 64, "split": split, "family_id": "fixture-family", "scenario_id": "fixture-scenario", "approval_id": "fixture-only",
                "intake_approval_id": "fixture-intake"}
    approval.update({key: True for key in ("license_verified", "identity_verified", "split_verified", "privacy_reviewed", "mapping_reviewed", "isolation_verified")})
    arguments = dict(split=split, provenance=provenance, family_id="fixture-family", scenario_id="fixture-scenario", approved_rows={row["id"]: approval})
    if source["dataset"] == "massive_1_1_en_us" and split == "TRAIN" and source["original_split"] == "train":
        from ml.nlu_v1.data_management.records import (append_event, assign_group, classify_privacy, facts_hash, load_groups, migrate_ingestion, register_scenario, transition)
        from ml.nlu_v1.data_management.dedupe import audit
        managed = migrate_ingestion(row, batch_id="fixture", row_number=1, import_file_sha256="b" * 64)
        managed["partition"] = split
        append_event(managed, "PENDING", "PENDING", "fixture-author", "FIXTURE_PARTITION")
        families, scenarios = load_groups()
        scenarios = register_scenario(scenarios, families, "fixture-scenario", "alarm_set", "fixture-family", "Nonpersonal governance fixture")
        groups = families, scenarios
        managed = assign_group(managed, "alarm_set", "fixture-scenario", groups=groups)
        evidence = {"binding_sha256": facts_hash({**managed, "privacy": "PUBLIC"}), "review_id": "fixture-privacy", "reviewer_id": "fixture-human",
                    "reviewed_at": "2026-10-16T00:00:00Z", "publication_allowed": True, "license_id": "CC-BY-4.0"}
        managed = classify_privacy(managed, "PUBLIC", evidence)
        managed = transition(managed, "AUTO_VALIDATED", actor="fixture", reason="VALIDATED", groups=groups)
        for state in ("HUMAN_REVIEWED", "APPROVED"):
            review = {"kind": "HUMAN_REVIEW", "binding_sha256": facts_hash(managed), "reviewer_id": "fixture-human", "review_id": "fixture-review",
                      "state_event_sha256": managed["review_history"][-1]["event_sha256"]}
            managed = transition(managed, state, actor="fixture-human", reason="FIXTURE_REVIEW", evidence=review, groups=groups)
        vectors = {"embedding_version": "nlu_v1_embeddings_001", "model_id": "fixture-vector-no-real-model", "model_sha256": "c" * 64,
                   "tokenizer_sha256": "d" * 64, "configuration_sha256": "e" * 64,
                   "rows": [{"id": managed["id"], "text_sha256": digest(managed["text"]), "vector": [1.0]}]}
        collection, report = audit([managed], vectors, groups=groups)
        arguments["governance"] = dict(row=collection[0], collection=collection, report=report, groups=groups)
    return arguments


def artifact_approval(path, source_id="massive_1_1_en_us"):
    source = REGISTRY[source_id]
    return {"source": source_id, "version": source["version"], "revision": source["revision"],
            "license_id": source["license"]["id"], "mapper_version": MAPPER_VERSION, "status": "APPROVED",
            "license_verified": True, "identity_verified": True, "split_verified": True, "privacy_reviewed": True,
            "approval_id": "fixture-only", "member_sha256": file_digest(path),
            "archive_sha256": file_digest(path), "archive_size_bytes": path.stat().st_size,
            "license_sha256": "2" * 64, "member_path": "fixture.jsonl", "locale": source["locale"],
            "source_revision": source["revision"], "requested_url": source["source_url"], "resolved_url": source["source_url"],
            "downloaded_at_utc": "2026-10-16T00:00:00Z", "original_split": "MIXED_OFFICIAL"}


class MapperTests(unittest.TestCase):
    def test_every_mapper_preserves_text_spans_id_split_and_validates(self):
        for row, text, source_id, split in mapped_fixtures():
            with self.subTest(source=row["source"]["dataset"]):
                self.assertEqual(text, row["text"])
                self.assertEqual(source_id, row["source"]["source_id"])
                self.assertEqual(split, row["source"]["original_split"])
                self.assertEqual("QUARANTINE", row["purpose"])
                self.assertEqual("PENDING_REVIEW", row["review_status"])
                validate_row(row)
                bounds = utf16_boundaries(text)
                for slot in row["slots"]:
                    self.assertEqual(slot["text"], text[bounds[slot["start"]]:bounds[slot["end"]]])

    def test_every_mapper_protected_rows_cannot_enter_training_even_with_approval(self):
        for row, *_ in mapped_fixtures(protected=True):
            with self.subTest(source=row["source"]["dataset"]):
                validate_row(row)
                with self.assertRaises(MappingError):
                    to_frozen_record(row, **promotion(row))

    def test_frozen_training_record_adapter_requires_bound_reviews(self):
        row = massive_mapper.map_record(massive())
        arguments = promotion(row)
        record = to_frozen_record(row, **arguments)
        validate_frozen(record)
        arguments["approved_rows"] = {}
        with self.assertRaises(MappingError):
            to_frozen_record(row, **arguments)

    def test_old_ingestion_adapter_cannot_bypass_canonical_privacy_and_dedupe(self):
        from ml.nlu_v1.data_management.records import DataError
        row = massive_mapper.map_record(massive())
        arguments = promotion(row)
        del arguments["governance"]
        with self.assertRaises(DataError):
            to_frozen_record(row, **arguments)

    def test_modified_semantics_or_grouping_invalidates_approval(self):
        row = massive_mapper.map_record(massive())
        arguments = promotion(row)
        row["action"] = "DELETE"
        with self.assertRaises(MappingError):
            to_frozen_record(row, **arguments)
        row = massive_mapper.map_record(massive())
        arguments = promotion(row)
        arguments["family_id"] = "other-family"
        with self.assertRaises(MappingError):
            to_frozen_record(row, **arguments)

    def test_mapping_tables_cover_inventories_and_schema_is_synchronized(self):
        self.assertEqual(60, check()["massive_intents"])
        self.assertEqual(schema(), json.loads((HERE / "canonical.schema.json").read_text(encoding="utf-8")))

    def test_massive_unknown_intents_slots_scenario_locale_and_drop(self):
        for patch in ({"intent": "alarm_magic"}, {"scenario": "calendar"}, {"locale": "fr-FR"},
                      {"annot_utt": "Set alarm [not_registered : next Friday] at [time : 8]"}):
            with self.assertRaises(MappingError):
                massive_mapper.map_record({**massive(), **patch})
        raw = {**massive(), "scenario": "play", "intent": "play_game"}
        self.assertIsNone(massive_mapper.map_record(raw))

    def test_massive_preserves_unicode_repeated_slots_and_whitespace(self):
        raw = {**massive(), "utt": "📞 email Ada  and Ada", "scenario": "email", "intent": "email_sendemail",
               "annot_utt": "📞 email [person : Ada]  and [person : Ada]"}
        row = massive_mapper.map_record(raw)
        self.assertEqual(raw["utt"], row["text"])
        self.assertEqual([9, 18], [s["start"] for s in row["slots"]])
        validate_row(row)

    def test_original_split_cannot_be_overridden(self):
        for mapper, raw in ((massive_mapper, massive("test")), (hwu_mapper, hwu("cross_validation_folds")), (mtop_mapper, mtop("test"))):
            with self.assertRaises(MappingError):
                mapper.map_record(raw, "train")

    def test_hwu_entities_and_user_metadata_use_original_answer(self):
        row = hwu_mapper.map_record(hwu())
        self.assertEqual("speaker-1", row["metadata"]["userid"])
        self.assertEqual("set", row["source"]["original_intent"])
        self.assertEqual("ALARM", row["domain"])
        raw = {**hwu(), "answer": "Set alarm NEXT Friday", "answer_normalised": hwu()["answer"]}
        with self.assertRaises(MappingError):
            hwu_mapper.map_record(raw)
        self.assertIsNone(hwu_mapper.map_record({**hwu(), "status": "IRR_NOISE"}))

    def test_mtop_tree_spans_and_raw_original_intent_are_preserved(self):
        raw = {**mtop(), "intent": "GET_EVENT"}
        row = mtop_mapper.map_record(raw)
        self.assertEqual(raw["parse"], row["composition"]["original_parse"])
        self.assertEqual("GET_EVENT", row["source"]["original_intent"])
        self.assertEqual(["PERSON", "EVENT", "DATE"], [s["type"] for s in row["slots"]])
        self.assertEqual("IN:GET_EVENT", row["composition"]["tree"]["label"])

    def test_mtop_nested_intents_preserved_without_silent_action(self):
        raw = {**mtop(), "text": "Remind me to call Ada", "parse": "[IN:CREATE_REMINDER Remind me to [SL:TITLE [IN:CREATE_CALL call [SL:PERSON Ada ] ] ] ]"}
        row = mtop_mapper.map_record(raw)
        self.assertTrue(row["metadata"]["nested_intents"])
        self.assertEqual("KNOWN_UNSUPPORTED", row["disposition"])
        self.assertEqual("NONE", row["action"])
        self.assertEqual("call Ada", row["slots"][0]["text"])
        self.assertEqual(raw["parse"], row["composition"]["original_parse"])

    def test_mtop_composite_temporal_slot_requires_exact_reviewed_spans(self):
        raw = {**mtop(), "parse": mtop()["parse"].replace("SL:DATE", "SL:DATE_TIME")}
        with self.assertRaises(MappingError):
            mtop_mapper.map_record(raw)
        start = raw["text"].index("next Friday")
        review = {"text_sha256": digest(raw["text"]), "reviewer_id": "fixture", "slots": [
            {"type": "DATE", "start": start, "end": len(raw["text"]), "text": "next Friday"}]}
        row = mtop_mapper.map_record(raw, temporal_review=review)
        validate_row(row)
        self.assertEqual("next Friday", row["slots"][-1]["text"])

    def test_mtop_mismatched_leaves_and_unmapped_nodes_rejected(self):
        for patch in ({"text": "Show Bob meeting next Friday"}, {"parse": "[IN:TRANSFER_MONEY transfer money ]"},
                      {"parse": mtop()["parse"].replace("SL:DATE", "SL:UNREGISTERED")}, {"parse": mtop()["parse"] + "]"}):
            with self.assertRaises(MappingError):
                mtop_mapper.map_record({**mtop(), **patch})

    def test_clinc_related_vs_unrelated_labels_and_oos(self):
        unrelated = clinc_mapper.map_record(["Freeze my account", "freeze_account"], "train", "0")
        self.assertEqual(("UNKNOWN", "NONE", "OUT_OF_DOMAIN"), (unrelated["domain"], unrelated["action"], unrelated["disposition"]))
        related = clinc_mapper.map_record(["Show calendar", "calendar"], "train", "1")
        self.assertEqual("KNOWN_UNSUPPORTED", related["disposition"])
        oos = clinc_mapper.map_record(["Unsupported fixture", "oos"], "oos_test", "0")
        self.assertEqual("oos_test", oos["source"]["original_split"])
        self.assertEqual("PENDING_REVIEW", oos["review_status"])
        with self.assertRaises(MappingError):
            clinc_mapper.map_record(["Show calendar", "calendar"], "oos_test", "0")

    def test_legacy_requested_relabels(self):
        for label, domain in (("CHECK_TASKS", "TASK"), ("CHECK_CALENDAR", "CALENDAR"), ("CHECK_MEMORY", "MEMORY"), ("WHAT_DID_I_PROMISE", "PROMISE")):
            raw = legacy(intent=label)
            row = legacy_metis_mapper.map_record(raw, row_approval=legacy_approval(raw))
            self.assertEqual((domain, "READ"), (row["domain"], row["action"]))
            self.assertEqual(label, row["source"]["original_intent"])

    def test_legacy_unknown_requires_reinterpretation_not_automatic_ood(self):
        raw = legacy(intent="UNKNOWN")
        approval = legacy_approval(raw)
        with self.assertRaises(MappingError):
            legacy_metis_mapper.map_record(raw, row_approval=approval)
        reinterpretation = {"source_id": raw["id"], "text_sha256": digest(raw["text"]), "reviewer_id": "fixture",
                            "reason": "Explicit task retrieval after semantic review", "slots": [],
                            "target": {"decision": "MAP", "domain": "TASK", "action": "READ", "disposition": "SUPPORTED_NEEDS_CONTEXT"}}
        row = legacy_metis_mapper.map_record(raw, row_approval=approval, reannotation=reinterpretation)
        self.assertFalse(row["ood"])
        self.assertEqual("TASK", row["domain"])
        reinterpretation["text_sha256"] = "0" * 64
        with self.assertRaises(MappingError):
            legacy_metis_mapper.map_record(raw, row_approval=approval, reannotation=reinterpretation)

    def test_legacy_private_exposed_protected_and_self_approval_are_rejected(self):
        for patch in ({"privacy": "PRIVATE_DO_NOT_PUBLISH"}, {"exposure": "EXPOSED"}, {"exposure": "PROTECTED_EVALUATION"}, {"classification": "EXPOSED"}):
            raw = {**legacy(), **patch}
            with self.assertRaises(MappingError):
                legacy_metis_mapper.map_record(raw, row_approval=legacy_approval(raw))
        with self.assertRaises(MappingError):
            legacy_metis_mapper.map_record(legacy())

    def test_canonical_rejects_malformed_rows_and_self_approval(self):
        row = massive_mapper.map_record(massive())
        for patch in ({"review_status": "APPROVED"}, {"purpose": "TRAIN"}, {"domain": "SAFETY"}, {"action": "CALL_PERSON"},
                      {"slots": []}, {"text": "Changed"}, {"authorized": True}):
            with self.assertRaises((ValueError, ValidationError)):
                validate_row({**row, **patch})

    def test_public_rows_never_promoted_to_fresh_independent_test(self):
        row = massive_mapper.map_record(massive())
        with self.assertRaises(MappingError):
            to_frozen_record(row, **promotion(row, "FINAL_INDEPENDENT_TEST"))

    def test_atomic_intake_of_approved_fixture_then_rejects_late_bad_row(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path, output = root / "fixture.jsonl", root / "quarantine.jsonl"
            path.write_text(json.dumps(massive()) + "\n", encoding="utf-8")
            result = ingest("massive", path, output, artifact_approval(path))
            self.assertEqual(1, result["mapped"])
            self.assertFalse(result["training_built"])
            validate_row(json.loads(output.read_text(encoding="utf-8")))
            with self.assertRaises(MappingError):
                ingest("massive", path, output, artifact_approval(path))
            invalid = {**massive(), "id": "other", "annot_utt": "WRONG"}
            path.write_text(json.dumps(massive()) + "\n" + json.dumps(invalid) + "\n", encoding="utf-8")
            destination = root / "rejected.jsonl"
            with self.assertRaises(MappingError):
                ingest("massive", path, destination, artifact_approval(path))
            self.assertFalse(destination.exists())
            self.assertEqual([], list(root.glob(".nlu-intake-*")))

    def test_intake_hash_license_blocked_sources_and_duplicate_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.jsonl"
            path.write_text(json.dumps(massive()) + "\n", encoding="utf-8")
            approval = artifact_approval(path)
            approval["member_sha256"] = "0" * 64
            with self.assertRaises(MappingError):
                approve_intake("massive_1_1_en_us", path, approval)
            for source in ("hwu_nlu_evaluation", "mtop_en", "metis_trainable_legacy"):
                with self.assertRaises(MappingError):
                    approve_intake(source, path, artifact_approval(path, source))
            path.write_text((json.dumps(massive()) + "\n") * 2, encoding="utf-8")
            with self.assertRaises(MappingError):
                ingest("massive", path, Path(directory) / "duplicate.jsonl", artifact_approval(path))

    def test_manifest_and_registry_guards_fail_closed(self):
        from source_registry import ROOT
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.jsonl"
            path.write_text(json.dumps(massive()) + "\n", encoding="utf-8")
            for patch in ({"license_verified": False}, {"member_path": "../escape"}, {"locale": "fr-FR"},
                          {"archive_sha256": None}, {"source_revision": "wrong"}, {"downloaded_at_utc": "2026-10-16T00:00:00"}):
                with self.assertRaises(MappingError):
                    approve_intake("massive_1_1_en_us", path, {**artifact_approval(path), **patch})
            with self.assertRaises(MappingError):
                ingest("massive", path, ROOT / "archive/legacy_ml/forbidden-output.jsonl", artifact_approval(path))

    def test_native_hwu_csv_mtop_tsv_and_clinc_json_readers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            raw = hwu()
            path = root / "hwu.csv"
            with path.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=list(raw), delimiter=";")
                writer.writeheader()
                writer.writerow(raw)
            self.assertEqual(raw["answer"], list(iter_native("hwu", path))[0][1]["text"])
            raw = mtop()
            path = root / "mtop.tsv"
            path.write_text("\t".join([raw["id"], "GET_EVENT", "", raw["text"], "events", "en", raw["parse"], "{}"]), encoding="utf-8")
            self.assertEqual(raw["text"], list(iter_native("mtop", path, "train"))[0][1]["text"])
            path = root / "clinc.json"
            data = {split: [] for split in ("train", "val", "test", "oos_train", "oos_val", "oos_test")}
            data["test"] = [["Freeze my account", "freeze_account"]]
            path.write_text(json.dumps(data), encoding="utf-8")
            self.assertEqual("test", list(iter_native("clinc", path))[0][1]["source"]["original_split"])

    def test_cli_rejection_does_not_echo_private_content(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path, approval_file = root / "fixture.jsonl", root / "approval.json"
            raw = {**massive(), "utt": "PRIVATE_MARKER"}
            path.write_text(json.dumps(raw) + "\n", encoding="utf-8")
            approval_file.write_text(json.dumps(artifact_approval(path)), encoding="utf-8")
            result = subprocess.run([sys.executable, str(HERE / "ingest.py"), "--source", "massive", "--input", str(path),
                                     "--output", str(root / "output.jsonl"), "--approval", str(approval_file)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertNotIn("PRIVATE_MARKER", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
