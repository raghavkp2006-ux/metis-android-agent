"""Ephemeral fixture keys/drafts exercise recording, never actual review evidence."""
import base64
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from test_machine_curation import alarm, EvidenceFixture
from ml.nlu_v1.dataset_v0 import machine_curation as machine, semantic_groups as semantic
from ml.nlu_v1.dataset_v0 import review_recording as recording
from ml.nlu_v1.data_management.records import DataError, load_groups, quarantine, sha_json


def fixture_context():
    original = alarm(key='1')
    evidence = EvidenceFixture([original])
    row, _, groups, _ = machine.curate_row(original, load_groups(), evidence)
    row = quarantine(row, ['FIXTURE_REVIEW_REQUIRED'])
    rows, review, exceptions, _, _ = semantic.create_groups([row], groups, evidence)
    assert not exceptions
    extra = quarantine(alarm(key='2'), ['FIXTURE_EXCEPTION'])
    ticket = {'exception_id': 'fixture-ticket', 'row_ids': [extra['id']], 'individual_decisions_required': True,
              'source': extra['source']['dataset'], 'source_intent': extra['source']['original_intent'], 'reasons': ['FIXTURE_EXCEPTION']}
    return {'rows': {r['id']: r for r in rows + [extra]}, 'groups': {g['group_id']: g for g in review},
            'templates': {g['group_id']: semantic.approval_template(g) for g in review},
            'tickets': [ticket], 'index': {'clusters': [{'cluster_id': 'fixture-cluster', 'ticket_ids': ['fixture-ticket'],
                'row_ids': [extra['id']], 'subgroups': [{'subgroup_id': 'fixture-subgroup', 'row_ids': [extra['id']]}]}]},
            'families': groups[0]['families'], 'source_hashes': {}}


class ReviewRecordingTests(unittest.TestCase):
    def setUp(self):
        self.context = fixture_context()
        self.targets = recording.targets(self.context, recording.shared_binding(self.context))
        self.records = {ident: recording.pending_record(target) for ident, target in self.targets.items()}

    def key_fixture(self):
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
        key = Ed25519PrivateKey.generate()
        public = base64.b64encode(key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)).decode()
        registry = {'registry_version': 'nlu_v1_group_reviewers_001', 'reviewers': {'fixture-external-human': {'public_key_ed25519_base64': public}}}
        return key, registry

    def exception(self):
        ident = next(i for i in self.records if i.startswith('exception-row:'))
        return ident, self.records[ident]

    def test_all_new_records_are_pending_unsigned_and_metadata_bound(self):
        self.assertEqual(len(self.records), 2)
        for ident, draft in self.records.items():
            recording.check_bindings(draft, self.targets[ident])
            self.assertEqual(draft['decision'], 'PENDING')
            self.assertIsNone(draft['reviewer_id'])
            self.assertIsNone(draft['reviewed_at'])
            self.assertIsNone(draft['signature_ed25519_base64'])
            self.assertFalse(draft['canonical_release_authorized'])
            self.assertTrue(draft['binding']['members'])

    def test_semantic_native_template_is_copied_exactly_without_mutation(self):
        before = copy.deepcopy(self.context['templates'])
        for ident in before:
            self.assertEqual(self.records[ident]['native_group_approval_template'], before[ident])
        self.assertEqual(self.context['templates'], before)

    def test_all_table_targets_required_once(self):
        text = recording.human_document(self.targets, self.context)
        choices = recording.decision_cells(text, self.targets, self.context)
        self.assertEqual(set(choices), set(self.targets))
        self.assertEqual(set(choices.values()), {'PENDING'})
        line = next(line for line in text.splitlines() if line.startswith('| exception-row:'))
        with self.assertRaisesRegex(DataError, 'DUPLICATED_TARGET'):
            recording.decision_cells(text + line + '\n', self.targets, self.context)
        with self.assertRaisesRegex(DataError, 'MEMBERSHIP_CHANGED'):
            recording.decision_cells(text.replace(line, ''), self.targets, self.context)

    def test_description_and_labels_cannot_be_silently_changed(self):
        text = recording.human_document(self.targets, self.context)
        text = text.replace('ALARM/SET/', 'ALARM/DELETE/')
        with self.assertRaisesRegex(DataError, 'DESCRIPTION_OR_LABELS_CHANGED'):
            recording.decision_cells(text, self.targets, self.context)

    def test_changed_member_hash_binding_rejected(self):
        ident, draft = self.exception()
        draft['binding']['members'][0]['row_content_sha256'] = 'f' * 64
        with self.assertRaisesRegex(DataError, 'BINDINGS_CHANGED'):
            recording.check_bindings(draft, self.targets[ident])

    def test_change_must_have_explicit_corrected_fields(self):
        _, draft = self.exception()
        draft['decision'] = 'CHANGE'
        with self.assertRaisesRegex(DataError, 'NO_RECORDED_CORRECTIONS'):
            recording.validate_corrections(draft, self.context)

    def test_valid_semantic_change_is_checked_without_changing_source_row(self):
        _, draft = self.exception()
        before = copy.deepcopy(self.context['rows'])
        draft['decision'] = 'CHANGE'
        draft['corrections']['corrected_action'] = 'READ'
        recording.validate_corrections(draft, self.context)
        self.assertEqual(self.context['rows'], before)
        draft['corrections']['corrected_domain'] = 'INVENTED_DOMAIN'
        with self.assertRaisesRegex(DataError, 'SEMANTICS_OR_BIO_INVALID'):
            recording.validate_corrections(draft, self.context)

    def test_exact_utf16_slot_add_and_mismatch_rejection(self):
        ident, draft = self.exception()
        row_id = draft['binding']['members'][0]['row_id']
        # This is an isolated proposal fixture; source corpus remains unmodified.
        self.context['rows'][row_id]['text'] = 'a😀 Friday'
        self.context['rows'][row_id]['slots'] = []
        draft['decision'] = 'CHANGE'
        draft['corrections']['slot_edits_by_row'] = {row_id: [{'operation': 'ADD', 'corrected_type': 'DATE',
            'corrected_start_utf16': 4, 'corrected_end_utf16': 10, 'corrected_text': 'Friday'}]}
        recording.validate_corrections(draft, self.context)
        edit = draft['corrections']['slot_edits_by_row'][row_id][0]
        edit['corrected_start_utf16'] = 2  # Inside the surrogate pair.
        with self.assertRaisesRegex(DataError, 'UTF16_OR_EXACT_TEXT_INVALID'):
            recording.validate_corrections(draft, self.context)

    def test_slot_update_remove_and_outside_target_guard(self):
        _, draft = self.exception()
        row_id = draft['binding']['members'][0]['row_id']
        slot = self.context['rows'][row_id]['slots'][0]
        draft['decision'] = 'CHANGE'
        draft['corrections']['slot_edits_by_row'] = {row_id: [{'operation': 'UPDATE', 'slot_index': 0,
            'corrected_type': slot['type'], 'corrected_start_utf16': slot['start'],
            'corrected_end_utf16': slot['end'], 'corrected_text': slot['text']}]}
        recording.validate_corrections(draft, self.context)
        draft['corrections']['slot_edits_by_row'][row_id] = [{'operation': 'REMOVE', 'slot_index': 0}]
        recording.validate_corrections(draft, self.context)
        draft['corrections']['slot_edits_by_row']['not-a-member'] = []
        with self.assertRaisesRegex(DataError, 'OUTSIDE_EXACT_REVIEW_TARGET'):
            recording.validate_corrections(draft, self.context)

    def test_pending_records_cannot_be_signed(self):
        key, registry = self.key_fixture()
        with patch.object(semantic, 'load_reviewers', return_value=registry):
            with self.assertRaisesRegex(DataError, 'NO_HUMAN_DECISIONS'):
                recording.sign_records(self.context, self.records, key, 'fixture-external-human', '2026-01-01T00:00:00Z', 'UNIT FIXTURE')

    def test_sign_and_existing_approve_verifier_share_crypto_contract(self):
        key, registry = self.key_fixture()
        for draft in self.records.values():
            draft['decision'] = 'APPROVE'
        before = copy.deepcopy(self.context['rows'])
        with patch.object(semantic, 'load_reviewers', return_value=registry):
            output = recording.sign_records(self.context, self.records, key, 'fixture-external-human', '2026-01-01T00:00:00Z', 'UNIT FIXTURE ONLY')
            for ident, item in output.items():
                recording.verify_signature(item['record'])
                if item['native_approval']:
                    group = self.context['groups'][ident]
                    semantic.verify_human_approval(group, item['native_approval'], [self.context['rows'][i] for i in group['row_ids']])
        self.assertEqual(self.context['rows'], before)
        self.assertTrue(all(r['review_status'] != 'APPROVED' for r in self.context['rows'].values()))

    def test_reject_and_change_can_be_recorded_without_promoting_rows(self):
        key, registry = self.key_fixture()
        ident, draft = self.exception()
        for choice in ('REJECT', 'CHANGE'):
            selected = copy.deepcopy(draft)
            selected['decision'] = choice
            if choice == 'CHANGE':
                selected['corrections']['corrected_action'] = 'READ'
            with patch.object(semantic, 'load_reviewers', return_value=registry):
                output = recording.sign_records(self.context, {ident: selected}, key, 'fixture-external-human', '2026-01-01T00:00:00Z', 'UNIT FIXTURE ONLY')
                recording.verify_signature(output[ident]['record'])
            self.assertIsNone(output[ident]['native_approval'])

    def test_signature_tampering_and_wrong_key_are_rejected(self):
        key, registry = self.key_fixture()
        ident, draft = self.exception()
        draft['decision'] = 'REJECT'
        with patch.object(semantic, 'load_reviewers', return_value=registry):
            signed = recording.sign_records(self.context, {ident: draft}, key, 'fixture-external-human', '2026-01-01T00:00:00Z', 'UNIT FIXTURE')[ident]['record']
            signed['decision'] = 'APPROVE'
            with self.assertRaisesRegex(DataError, 'SIGNATURE_INVALID'):
                recording.verify_signature(signed)
            other_key, _ = self.key_fixture()
            with self.assertRaisesRegex(DataError, 'DOES_NOT_MATCH_ENROLLED'):
                recording.sign_records(self.context, {ident: draft}, other_key, 'fixture-external-human', '2026-01-01T00:00:00Z', 'UNIT FIXTURE')

    def test_prepare_and_sync_write_only_new_drafts_and_never_overwrite(self):
        before = sha_json(self.context)
        with tempfile.TemporaryDirectory() as tmp:
            datasets = Path(tmp) / 'datasets'
            folder = datasets / 'quarantine/review-fixture'
            document = Path(tmp) / 'decisions.md'
            with patch.object(recording, 'DATASETS', datasets):
                recording.prepare(self.context, folder, document)
                with self.assertRaisesRegex(DataError, 'ALREADY_EXISTS'):
                    recording.prepare(self.context, folder, document)
            manifest, expected, drafts = recording.load_prepared(self.context, folder)
            self.assertEqual(recording.summary(drafts)['pending_exception_decisions'], 1)
            text = document.read_text().replace('| PENDING |', '| REJECT |')
            document.write_text(text, encoding='utf-8')
            recording.sync(self.context, folder)
            _, _, drafts = recording.load_prepared(self.context, folder)
            self.assertTrue(all(d['decision'] == 'REJECT' for d in drafts.values()))
            missing = recording.validate_batch(self.context, folder, 'unsigned-fixture')
            self.assertEqual(missing['status'], 'MISSING_RECORDED_DECISION')
            self.assertFalse(missing['complete'])
            self.assertEqual(missing['dataset_rows_approved'], 0)
        self.assertEqual(sha_json(self.context), before)

    def test_exception_draft_binds_exact_cluster_subgroup_ticket_and_row(self):
        _, draft = self.exception()
        self.assertEqual(draft['binding']['cluster_id'], 'fixture-cluster')
        self.assertEqual(draft['binding']['subgroup_id'], 'fixture-subgroup')
        self.assertEqual(draft['binding']['ticket_id'], 'fixture-ticket')
        self.assertEqual(len(draft['binding']['members']), 1)

    def test_signed_batch_end_to_end_and_append_only_evidence(self):
        key, registry = self.key_fixture()
        with tempfile.TemporaryDirectory() as tmp:
            datasets = Path(tmp) / 'datasets'
            folder, document = datasets / 'quarantine/recording-fixture', Path(tmp) / 'decisions.md'
            with patch.object(recording, 'DATASETS', datasets):
                recording.prepare(self.context, folder, document)
            document.write_text(document.read_text().replace('| PENDING |', '| APPROVE |'), encoding='utf-8')
            recording.sync(self.context, folder)
            _, _, records = recording.load_prepared(self.context, folder)
            with patch.object(semantic, 'load_reviewers', return_value=registry):
                output = recording.sign_records(self.context, records, key, 'fixture-external-human', '2026-01-01T00:00:00Z', 'UNIT FIXTURE ONLY')
                recording.write_signed(folder, 'fixture-batch', output)
                result = recording.validate_batch(self.context, folder, 'fixture-batch')
                self.assertTrue(result['complete'])
                self.assertEqual(result['dataset_rows_approved'], 0)
                self.assertEqual(result['valid_signed_decisions'], 2)
                with self.assertRaisesRegex(DataError, 'ALREADY_EXISTS_NEVER_OVERWRITE'):
                    recording.write_signed(folder, 'fixture-batch', output)
                document.write_text(document.read_text().replace('| APPROVE |', '| PENDING |'), encoding='utf-8')
                with self.assertRaisesRegex(DataError, 'NOT_SYNCED'):
                    recording.validate_batch(self.context, folder, 'fixture-batch')


if __name__ == '__main__':
    unittest.main()
