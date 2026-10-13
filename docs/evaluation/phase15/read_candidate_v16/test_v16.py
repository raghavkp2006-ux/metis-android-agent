"""New independent gold, historical preservation and exposed reviewed outcomes."""
import json
import unittest
from pathlib import Path
from integrity import check, HERE
from v16_grammar import resolve
from v16_project import gold_entities


class V16Tests(unittest.TestCase):
    def test_current_and_byte_preserved_historical_artifacts(self):
        self.assertFalse(check()['ml_enabled'])

    def test_independently_authored_gold_scopes_queries_and_offsets(self):
        for name in ('development.json', 'development-supplement-001.json', 'development-supplement-002.json'):
            data = json.loads((HERE / name).read_text(encoding='utf-8'))
            for row in data['rows']:
                actual = resolve(row['utterance'])
                self.assertEqual(row['read'], bool(actual['reads']), row['id'])
                if row['read']:
                    self.assertEqual(row['subject'] or row['person'], actual['query_text'])
                    self.assertEqual(gold_entities(row), actual['entities'])
                    for field in ('subject', 'person', 'status', 'negative', 'requested_field', 'upcoming'):
                        self.assertEqual(row[field], actual['scope'][field], row['id'])
                else:
                    for label in ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR'):
                        self.assertEqual(0, resolve(row['utterance'], predicted_intent=label)['reads'], row['id'])

    def test_all_96_reviewed_reads_and_269_prohibitions(self):
        rows = json.loads((HERE.parent / 'candidate_v15/review-projection.json').read_text(encoding='utf-8'))['rows']
        self.assertEqual(96, sum(r['expected_disposition_draft'] == 'read' for r in rows))
        for row in rows:
            actual = resolve(row['utterance'])
            self.assertEqual(row['expected_disposition_draft'] == 'read', bool(actual['reads']), row['id'])
            if not actual['reads']:
                for label in ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR'):
                    self.assertEqual(0, resolve(row['utterance'], predicted_intent=label)['reads'], row['id'])

    def test_benchmark_and_all_retained_admissions(self):
        benchmark = json.loads((HERE.parent / 'human_review_003/first_evaluation/frozen-benchmark.json').read_text(encoding='utf-8'))['rows']
        for row in benchmark:
            self.assertEqual(row['expected_outcome'] == 'read', bool(resolve(row['utterance'])['reads']), row['id'])
        for row in json.loads((HERE.parent / 'candidate_v15/regression.json').read_text(encoding='utf-8')):
            if row['parsed']['reads']:
                self.assertTrue(resolve(row['utterance'])['reads'], row['id'])

    def test_confirmed_open_task_policy_and_literal_metadata_remain_separate(self):
        result = resolve('What tasks do I have related to the antenna project?')
        self.assertEqual('OPEN', result['scope']['status'])
        self.assertEqual('ANY', resolve('Did I create a task to review the antenna project?')['scope']['status'])
        self.assertTrue(resolve('Did I create a task to push the latest antenna code?')['reads'])
        self.assertFalse(resolve('Did I create a task to push the antenna code from last month?')['reads'])

    def test_predeclared_appointment_kind_does_not_become_generic_meeting(self):
        gold = json.loads((HERE / 'development-supplement-003.json').read_text(encoding='utf-8'))
        self.assertTrue(gold['authored_before_corresponding_scope_addition'])
        for row in gold['rows']:
            scope = resolve(row['utterance'])['scope']
            self.assertTrue(scope['appointments'])
            self.assertFalse(scope['meetings'])
            self.assertEqual(row['person'], scope['person'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
