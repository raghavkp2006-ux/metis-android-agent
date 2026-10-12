"""Independent development expectations, fitting isolation and frozen evidence checks."""
import json
import unittest
from pathlib import Path

from candidate import resolve
from classifier import partitions
from data import HERE, ROOT
from grammar import kotlin
from integrity import check, check_prior


class V14Tests(unittest.TestCase):
    def test_generated_patterns_and_source_certificate(self):
        self.assertEqual(kotlin(), (ROOT / 'app/src/main/java/dev/metis/agent/domain/agent/ReadPatternsV14.kt')
                         .read_text(encoding='utf-8'))
        self.assertFalse(check()['ml_enabled'])
        check_prior()

    def test_independently_authored_complete_queries_and_utf16_spans(self):
        rows = json.loads((HERE / 'development.json').read_text(encoding='utf-8'))['rows']
        self.assertEqual(75, sum(row['disposition'] == 'read' for row in rows))
        for row in rows:
            plan = resolve(row['utterance'])
            self.assertEqual(row['disposition'] == 'read', bool(plan['reads']), row['id'])
            if plan['reads']:
                self.assertEqual(row['entities'], plan['entities'], row['id'])
                self.assertEqual(row['query_text'], plan['query_text'], row['id'])
            else:
                for label in ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR'):
                    self.assertEqual(0, resolve(row['utterance'], predicted_intent=label)['reads'], row['id'])

    def test_unsupported_sources_control_characters_and_reference_bounds(self):
        text = 'What do you remember about The Who?'
        self.assertEqual(0, resolve(text, source='SCREEN')['reads'])
        for variant in (text + '\u200b', text + '\n', 'What did I say about ' + 'x' * 201):
            self.assertEqual(0, resolve(variant)['reads'])

    def test_fitting_and_calibration_partitions_exclude_humans_and_old_test(self):
        parts = partitions()
        self.assertEqual(315, len(parts['test']))
        self.assertTrue(all(row['id'].startswith('v14-fit-') for row in parts['test']))
        for rows in parts.values():
            self.assertTrue(all(row['provenance'] == 'assistant_authored' for row in rows))
            self.assertTrue(all(not row['id'].startswith(('human-', 'user-')) for row in rows))

    def test_original_forbidden_reads_and_unresolved_human_references_stay_blocked(self):
        rows = json.loads((HERE / 'regression.json').read_text(encoding='utf-8'))
        frozen = json.loads((HERE.parent / 'human_review_003/first_evaluation/frozen-benchmark.json')
                            .read_text(encoding='utf-8'))
        # The complete retained projection already asserts every old noncalendar prohibited row is blocked.
        for row in rows:
            if row['original_disposition'] not in (None, 'read') and row['parsed']['intent'] != 'CHECK_CALENDAR':
                self.assertEqual(0, row['parsed']['reads'], row['id'])
        original_failures = {f'p15v5-fresh-{index:04}' for index in
                             (14, 17, 21, 26, 52, 57, 65, 66, 70, 71, 103, 129, 132, 139)}
        benchmark = {row['id']: row for row in frozen['rows']}
        self.assertTrue(original_failures <= benchmark.keys())
        for identifier in original_failures:
            self.assertEqual(0, resolve(benchmark[identifier]['utterance'])['reads'], identifier)
        human = json.loads((HERE / 'human-review-proposal.json').read_text(encoding='utf-8'))['rows']
        for row in human:
            self.assertFalse(row['human_approved'])
            if row['standalone_clarification_draft']:
                self.assertEqual(0, resolve(row['utterance'])['reads'], row['id'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
