"""Reviewed human-development artifacts, leakage guards and fail-closed outcomes."""
import hashlib
import json
import unittest
from collections import defaultdict
from pathlib import Path
from integrity import check as check_current_integrity
from v15_candidate import resolve

HERE = Path(__file__).resolve().parent


class ReviewedAnnotations(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((HERE / 'review-projection.json').read_text(encoding='utf-8'))

    def test_explicit_approval_binds_immutable_proposals(self):
        check_current_integrity()
        from freeze_review import check
        check()
        for stem in ('completion', 'family'):
            approval = json.loads((HERE / f'{stem}-approval.json').read_text(encoding='utf-8'))
            self.assertEqual(approval['proposal_sha256'], hashlib.sha256((HERE / f'{stem}-proposal.json').read_bytes()).hexdigest())
            self.assertTrue(approval['confirmation'])
        self.assertTrue(self.data['development_annotations_frozen'])
        self.assertEqual(365, self.data['complete_annotations_approved'])
        self.assertFalse(self.data['fitting_eligible'])

    def test_exact_utf16_spans_subjects_and_no_unfinished_dispositions(self):
        allowed = {'PERSON', 'TASK', 'MEMORY', 'TITLE', 'TIME'}
        for row in self.data['rows']:
            self.assertTrue(row['human_approved'], row['id'])
            self.assertTrue(all(row['reviewed_fields'].values()), row['id'])
            self.assertNotEqual('review_required', row['expected_disposition_draft'])
            self.assertEqual(row['utterance'], row['constraints_draft']['verbatim_request'])
            encoded = row['utterance'].encode('utf-16-le')
            for entity in row['entities_draft']:
                self.assertIn(entity['type'], allowed)
                start, end = entity['startOffset'], entity['endOffset']
                self.assertTrue(0 <= start < end <= len(encoded) // 2, row['id'])
                self.assertEqual(entity['rawValue'], encoded[2 * start:2 * end].decode('utf-16-le'), row['id'])
            if row['complete_subject_draft']:
                self.assertLessEqual(len(row['complete_subject_draft'].encode('utf-16-le')) // 2, 256)

    def test_whole_families_and_exact_duplicates_never_cross_partitions(self):
        groups = defaultdict(set)
        by_id = {r['id']: r for r in self.data['rows']}
        for row in self.data['rows']:
            groups[row['family_draft']].add(row['partition'])
            if row['duplicate_of']:
                other = by_id[row['duplicate_of']]
                self.assertEqual(other['partition'], row['partition'])
                self.assertEqual(other['family_draft'], row['family_draft'])
            self.assertFalse(row['fit_eligible'])
        self.assertTrue(all(len(parts) == 1 for parts in groups.values()))

    def test_nonread_gold_blocks_every_forced_read_label(self):
        for row in self.data['rows']:
            if row['expected_disposition_draft'] != 'read':
                self.assertEqual(0, resolve(row['utterance'])['reads'], row['id'])
                for label in ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR'):
                    self.assertEqual(0, resolve(row['utterance'], predicted_intent=label)['reads'], row['id'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
