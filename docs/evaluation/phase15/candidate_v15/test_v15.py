"""Independent scope gold, retained safety, annotation isolation and source bindings."""
import json
import unittest
from integrity import HERE, check, check_prior
from v15_candidate import resolve
from project import scope_text


class V15Tests(unittest.TestCase):
    def test_current_and_frozen_integrity(self):
        self.assertFalse(check()['ml_enabled'])
        check_prior()

    def test_independent_gold_scopes_entities_and_full_queries(self):
        data = json.loads((HERE / 'development.json').read_text(encoding='utf-8'))
        self.assertTrue(data['authored_before_grammar_implementation'])
        self.assertEqual(81, sum(row['read'] for row in data['rows']))
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

    def test_human_projection_is_unapproved_and_not_partitioned(self):
        data = json.loads((HERE / 'review-projection.json').read_text(encoding='utf-8'))
        self.assertEqual(365, len(data['rows']))
        self.assertFalse(data['fitting_eligible'])
        self.assertEqual(5, sum(row['human_approved'] for row in data['rows']))
        for row in data['rows']:
            self.assertFalse(row['fit_eligible'])
            self.assertIsNone(row['partition'])
            self.assertEqual('independent', row['conversation_context'])
            self.assertEqual(row['utterance'], row['constraints_draft']['verbatim_request'])

    def test_date_and_subject_boundaries(self):
        for text in ('Find tasks about the report due Friday', 'Find tasks about the report due 2026-02-30',
                     'Did I promise to send the notes?', 'Did I tell her I would review the code?',
                     'What email address did I save for ' + 'x' * 257):
            self.assertEqual(0, resolve(text)['reads'])
        self.assertEqual(0, resolve('Find open tasks about the report', source='SCREEN')['reads'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
