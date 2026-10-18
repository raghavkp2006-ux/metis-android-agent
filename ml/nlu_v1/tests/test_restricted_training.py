"""Fixture authority records exercise restricted governance, never real approvals."""
import copy
import tempfile
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from test_machine_curation import alarm
from ml.nlu_v1.dataset_v0 import restricted_training as restricted, collection_evidence
from ml.nlu_v1.data_management.records import DataError, sha_json, quarantine


class RestrictedTrainingTests(unittest.TestCase):
    def records(self):
        row = quarantine(alarm(), ['FIXTURE_REVIEW_REQUIRED'])
        binding = {'row_content_sha256': {row['id']: sha_json(row)}, 'license_id': 'CC-BY-4.0'}
        with patch.object(restricted.packet, 'file_hash', return_value='a' * 64):
            privacy, training = restricted.authority_records(binding, {k: {'fixture': True} for k in ('clinc', 'massive', 'slurp')}, restricted.policy(), restricted.OWNER_REQUEST_SHA256, '2026-10-17T00:00:00Z')
        intake = {'binding': binding, 'privacy_review_sha256': sha_json(privacy), 'approval_id': 'fixture-intake-not-real'}
        return row, privacy, training, intake

    def test_policy_uses_existing_restricted_enum_and_no_public_authority(self):
        policy = restricted.policy()
        self.assertEqual(policy['row_privacy'], 'RESTRICTED')
        self.assertFalse(policy['publication_allowed'])
        self.assertFalse(policy['raw_row_republication'])
        self.assertEqual(set(policy['scope']), {'massive_1_1_en_us', 'clinc150_full'})

    def test_restricted_classification_is_source_level_not_individual(self):
        row, privacy, training, intake = self.records()
        changed = restricted.apply_restricted_snapshot(row, privacy, training, intake)
        self.assertEqual(changed['privacy'], 'RESTRICTED')
        self.assertFalse(changed['privacy_review']['publication_allowed'])
        self.assertFalse(privacy['individual_privacy_review_claimed'])
        self.assertEqual(changed['metadata']['source_governance_003']['authority_level'], 'SOURCE_LEVEL_NOT_INDIVIDUAL_REVIEW')

    def test_original_history_and_quarantine_are_preserved(self):
        row, privacy, training, intake = self.records()
        before = copy.deepcopy(row)
        changed = restricted.apply_restricted_snapshot(row, privacy, training, intake)
        self.assertEqual(row, before)
        self.assertEqual(changed['review_history'][:len(row['review_history'])], row['review_history'])
        self.assertEqual(changed['review_status'], 'QUARANTINED')
        self.assertEqual(changed['quarantine_reasons'], row['quarantine_reasons'])
        self.assertFalse(any(e['next'] in ('HUMAN_REVIEWED', 'APPROVED') for e in changed['review_history']))

    def test_changed_content_or_member_is_rejected(self):
        row, privacy, training, intake = self.records()
        row['text'] = 'different fixture'
        with self.assertRaises(DataError):
            restricted.apply_restricted_snapshot(row, privacy, training, intake)

    def test_changed_source_authority_bindings_are_rejected(self):
        row, privacy, training, intake = self.records()
        training['binding'] = {}
        with self.assertRaises(DataError):
            restricted.apply_restricted_snapshot(row, privacy, training, intake)

    def test_changed_privacy_record_cannot_inherit_intake_training(self):
        row, privacy, training, intake = self.records()
        privacy['reason'] = 'changed fixture'
        with self.assertRaises(DataError):
            restricted.apply_restricted_snapshot(row, privacy, training, intake)

    def test_missing_authoritative_collection_paper_fails_closed(self):
        with self.assertRaises(DataError):
            restricted.authority_records({}, {}, restricted.policy(), restricted.OWNER_REQUEST_SHA256, '2026-10-17T00:00:00Z')

    def test_different_owner_directive_cannot_authorize(self):
        with self.assertRaises(DataError):
            restricted.authority_records({}, {}, restricted.policy(), '0' * 64, '2026-10-17T00:00:00Z')

    def test_synthetic_or_future_sources_rejected(self):
        for source in ('metis_controlled_synthetic', 'future_source'):
            with self.assertRaises(DataError):
                restricted.source_binding(source, [alarm()], SimpleNamespace(), restricted.policy())

    def test_protected_split_cannot_inherit_source_authority(self):
        row = alarm()
        row['source']['original_split'] = 'test'
        pin = restricted.policy()['scope']['massive_1_1_en_us']
        observed = {'source_version': pin['version'], 'revision': pin['revision'], 'sha256': pin['archive_sha256'], 'member_sha256': pin['member_sha256']}
        evidence = SimpleNamespace(sources={'massive_1_1_en_us': {'observation': observed}})
        with self.assertRaises(DataError):
            restricted.source_binding('massive_1_1_en_us', [row], evidence, restricted.policy())

    def test_evidence_acquisition_rejects_non_authoritative_domains(self):
        for url in ('http://aclanthology.org/D19-1131.pdf', 'https://example.com/paper.pdf'):
            with self.assertRaises(DataError):
                collection_evidence.fetch(url)

    def test_paper_byte_tampering_fails_offline_verification(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            (path / 'paper.pdf').write_bytes(b'%PDF-fixture-changed')
            manifest = {'version': 'nlu_v1_collection_evidence_003', 'records': {k:{} for k in collection_evidence.PAPERS}, 'files': {'paper.pdf': '0' * 64}}
            (path / 'manifest.json').write_bytes(restricted.json_bytes(manifest))
            with self.assertRaises(DataError):
                collection_evidence.verify(path)


if __name__ == '__main__':
    unittest.main()
