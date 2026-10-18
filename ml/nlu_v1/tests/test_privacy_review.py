"""Local fixture packets never authorize privacy classification."""
import copy
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

from test_machine_curation import alarm
from ml.nlu_v1.dataset_v0 import privacy_review as review
from ml.nlu_v1.data_management.records import DataError


class PrivacyReviewTests(unittest.TestCase):
    def setUp(self):
        self.row = alarm(key='1')
        self.target = review.target(self.row, {'license_id': 'fixture-only'})
        self.targets = {self.target['target_id']: self.target}
        self.context = {'targets': self.targets, 'public': [], 'input_sha256': {}, 'semantic_review': {}}

    def test_default_is_pending_and_exactly_one_row(self):
        text = review.document(self.targets)
        self.assertEqual(set(review.choices(text, self.targets).values()), {'PENDING'})
        self.assertEqual(self.target['row_id'], self.row['id'])
        self.assertEqual(self.target['privacy'], 'UNKNOWN_PRIVACY')

    def test_each_supported_choice_can_be_recorded_without_classifying(self):
        before = copy.deepcopy(self.row)
        for choice in review.CHOICES:
            text = review.document(self.targets).replace('| PENDING |', '| ' + choice + ' |')
            self.assertEqual(set(review.choices(text, self.targets).values()), {choice})
        self.assertEqual(before, self.row)

    def test_binding_text_and_instruction_changes_are_rejected(self):
        text = review.document(self.targets)
        for changed in (text.replace('UNKNOWN_PRIVACY |', 'PUBLIC |'), text.replace('LOCAL ONLY:', 'PUBLISHED:'), text.replace(self.row['id'], '0' * 64)):
            with self.assertRaises(DataError):
                review.choices(changed, self.targets)

    def test_missing_duplicate_and_invalid_choice_rejected(self):
        text = review.document(self.targets)
        line = next(s for s in text.splitlines() if s.startswith('| privacy-row:'))
        for changed in (text.replace(line + '\n', ''), text + line + '\n', text.replace('| PENDING |', '| APPROVE |')):
            with self.assertRaises(DataError):
                review.choices(changed, self.targets)

    def test_synthetic_public_and_protected_rows_out_of_scope(self):
        for field, value in (('privacy', 'PUBLIC'), ('source_class', 'CONTROLLED_SYNTHETIC'), ('original_split', 'test')):
            row = copy.deepcopy(self.row)
            if field == 'privacy':
                row[field] = value
            else:
                row['source'][field] = value
            with patch.object(review, 'validate_record'):
                with self.assertRaises(DataError):
                    review.target(row, {})

    def test_sync_only_writes_unsigned_drafts_and_never_calls_classifier(self):
        before = copy.deepcopy(self.context)
        with tempfile.TemporaryDirectory() as directory, patch.object(review, 'require_local'):
            folder = Path(directory) / 'local'
            review.prepare(self.context, folder)
            path = folder / 'PRIVACY_DECISIONS.md'
            path.write_bytes(path.read_bytes().replace(b'| PENDING |', b'| PRIVATE |'))
            result = review.sync(self.context, folder)
            self.assertEqual(result['privacy_states_changed'], 0)
            draft = review.packet.read_json(next(Path(result['draft_folder']).glob('*.json')))
            self.assertEqual(draft['decision'], 'PRIVATE')
            self.assertIsNone(draft['reviewer_id'])
            self.assertIsNone(draft['reviewed_at'])
            self.assertFalse(draft['publication_allowed'])
            self.assertFalse(draft['classification_applied'])
            with self.assertRaises(DataError):
                review.sync(self.context, folder)
        self.assertEqual(before, self.context)

    def test_packet_tampering_rejected(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(review, 'require_local'):
            folder = Path(directory) / 'local'
            review.prepare(self.context, folder)
            path = folder / 'privacy_targets.json'
            path.write_bytes(path.read_bytes() + b' ')
            with self.assertRaises(DataError):
                review.sync(self.context, folder)

    def test_outside_quarantine_cannot_receive_utterances(self):
        with self.assertRaises(DataError):
            review.require_local(review.packet.ROOT / 'docs/privacy')


if __name__ == '__main__':
    unittest.main()
