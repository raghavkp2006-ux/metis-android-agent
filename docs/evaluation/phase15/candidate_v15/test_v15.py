"""Independent scope gold, retained safety, annotation isolation and source bindings."""
import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from integrity import HERE, check, check_prior, frozen_digest
from v15_candidate import resolve
from project import scope_text


class V15Tests(unittest.TestCase):
    def test_current_and_frozen_integrity(self):
        self.assertFalse(check()['ml_enabled'])
        check_prior()

    def test_checkout_metadata_normalization_keeps_evidence_byte_bound(self):
        with TemporaryDirectory() as directory:
            attributes = Path(directory) / '.gitattributes'
            evidence = Path(directory) / 'frozen.tsv'
            for path in (attributes, evidence):
                path.write_bytes(b'original\r\n')
            metadata_hash, evidence_hash = frozen_digest(attributes), frozen_digest(evidence)
            for path in (attributes, evidence):
                path.write_bytes(b'original\n')
            self.assertEqual(metadata_hash, frozen_digest(attributes))
            self.assertNotEqual(evidence_hash, frozen_digest(evidence))
            attributes.write_bytes(b'changed\n')
            self.assertNotEqual(metadata_hash, frozen_digest(attributes))

    def test_independent_gold_scopes_entities_and_full_queries(self):
        data = json.loads((HERE / 'development.json').read_text(encoding='utf-8'))
        self.assertTrue(data['authored_before_grammar_implementation'])
        self.assertEqual(90, sum(row['read'] for row in data['rows']))
        for row in data['rows']:
            result = resolve(row['utterance'])
            self.assertEqual(row['read'], bool(result['reads']), row['id'])
            if row['read']:
                self.assertEqual(row['scope'], result['scope'], row['id'])
                self.assertEqual([{k: v for k, v in e.items() if k != 'query'} for e in row['entities']], result['entities'])
                self.assertEqual(row['scope']['subject'] or row['scope']['person'], result['query_text'])
                self.assertTrue(scope_text(result['scope']))

    def test_all_negative_gold_force_every_read_label(self):
        rows = json.loads((HERE / 'development.json').read_text(encoding='utf-8'))['rows']
        for row in rows:
            if not row['read']:
                for label in ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR'):
                    self.assertEqual(0, resolve(row['utterance'], predicted_intent=label)['reads'], row['id'])

    def test_all_retained_admissions_and_original_failures(self):
        from v15_evaluate import ORIGINAL_FAILURES
        rows = json.loads((HERE / 'regression.json').read_text(encoding='utf-8'))
        self.assertEqual(785, sum(row['old_reads'] for row in rows))
        for row in rows:
            self.assertFalse(row['old_reads'] and not row['parsed']['reads'], row['id'])
        benchmark = json.loads((HERE.parent / 'human_review_003/first_evaluation/frozen-benchmark.json').read_text(encoding='utf-8'))['rows']
        probes = [row for row in benchmark if row['id'] in ORIGINAL_FAILURES]
        self.assertEqual(14, len(probes))
        self.assertTrue(all(not resolve(row['utterance'])['reads'] for row in probes))

    def test_reviewed_annotations_still_require_calibration_data(self):
        data = json.loads((HERE / 'review-projection.json').read_text(encoding='utf-8'))
        self.assertEqual(365, len(data['rows']))
        self.assertFalse(data['fitting_eligible'])
        self.assertEqual(365, sum(row['human_approved'] for row in data['rows']))
        self.assertEqual(365, data['semantic_meanings_approved'])
        self.assertTrue(all(row['semantic_meaning_human_approved'] for row in data['rows']))
        for row in data['rows']:
            self.assertFalse(row['fit_eligible'])
            self.assertIn(row['partition'], ('train', 'development_validation', 'development_test'))
            self.assertTrue(row['family_reviewed'])
            self.assertEqual('independent', row['conversation_context'])
            self.assertEqual(row['utterance'], row['constraints_draft']['verbatim_request'])
            if not row['human_approved']:
                self.assertFalse(all(row['reviewed_fields'].values()))
            if row['annotation_provenance'] == 'user_approved_development_batch':
                actual = resolve(row['utterance'])
                self.assertEqual(row['expected_disposition_draft'] == 'read', bool(actual['reads']), row['id'])
                if actual['reads']:
                    self.assertEqual(row['intended_category_draft'], actual['intent'])
                    self.assertEqual(row['entities_draft'], actual['entities'])
                    self.assertEqual(row['complete_subject_draft'], actual['query_text'])

    def test_date_and_subject_boundaries(self):
        for text in ('Find tasks about the report due Friday', 'Find tasks about the report due 2026-02-30',
                     'Did I promise to send the notes?', 'Did I tell her I would review the code?',
                     'What email address did I save for ' + 'x' * 257):
            self.assertEqual(0, resolve(text)['reads'])
        self.assertEqual(0, resolve('Find open tasks about the report', source='SCREEN')['reads'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
