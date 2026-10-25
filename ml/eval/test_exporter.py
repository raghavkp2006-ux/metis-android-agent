"""Offline JVM integration cases using synthetic requests and temporary fixtures only."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from ml.data.common import ROOT, dump, text_sha
from ml.eval.export_rules import compile_exporter


class ExporterIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cp = compile_exporter(ROOT / 'build/ml-day1-integration', Path.home() / '.gradle/caches/modules-2/files-2.1')

    def run_rows(self, texts, fixture, contexts=None):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rows = [dict(id=i, text=t, text_sha256=text_sha(t), style='plain',
                         context=(contexts or ['none']*len(texts))[i-1]) for i,t in enumerate(texts,1)]
            source = root / 'input.jsonl'
            output = root / 'output.jsonl'
            source.write_text(''.join(json.dumps(r)+'\n' for r in rows))
            dump(root / 'fixture.json', fixture)
            subprocess.run(['java','-cp',self.cp,'dev.metis.agent.evaluation.day1.Day1RulesExporter',
                            str(source),str(output),str(root/'fixture.json')], check=True, capture_output=True)
            return [json.loads(line) for line in output.read_text().splitlines()]

    def test_empty_state_and_no_prior_turn(self):
        result = self.run_rows(['add a task to read book', 'complete task: read book'], {'tasks':[]})
        self.assertEqual(result[0]['status'], 'PROPOSAL')
        self.assertEqual(result[1]['status'], 'FOLLOW_UP')
        self.assertEqual(result[1]['task_state_size'], 0)
        self.assertEqual(result[1]['resolved_task_ids'], [])
        self.assertFalse(result[1]['prior_turn'])

    def test_fixture_now_required(self):
        result = self.run_rows(['complete task: read book'], {'tasks':[]}, ['fixture'])[0]
        self.assertFalse(result['evaluated'])
        self.assertIn('now missing', result['reason'])

    def test_exact_reference_resolution_and_ambiguity(self):
        fixture = dict(now='2026-10-25T12:00:00Z', tasks=[dict(id='one',title='Read book',status='pending')])
        result = self.run_rows(['complete task: read book'], fixture, ['fixture'])[0]
        self.assertEqual(result['status'], 'PROPOSAL')
        self.assertEqual(result['resolved_task_ids'], ['one'])
        self.assertEqual(result['clock'], fixture['now'])
        fixture['tasks'].append(dict(id='two',title='Read book',status='pending'))
        result = self.run_rows(['complete task: read book'], fixture, ['fixture'])[0]
        self.assertEqual(result['status'], 'FOLLOW_UP')

    def test_current_multi_intent_behavior(self):
        result = self.run_rows(['add a task to read book and delete task: write notes'], {'tasks':[]})[0]
        self.assertEqual(len(result['clauses']), 1)
        self.assertEqual(result['entities']['task'], 'read book and delete task: write notes')
        self.assertEqual(len(result['proposed_writes']), 1)

    def test_pinned_reminder_and_missing_date(self):
        fixture = dict(now='2026-10-25T12:00:00Z', tasks=[])
        result = self.run_rows(['remind me tomorrow at 8 pm to read book', 'remind me at 8 pm to read book'],
                               fixture, ['fixture','fixture'])
        self.assertEqual(result[0]['status'], 'PROPOSAL')
        self.assertEqual(result[1]['status'], 'FOLLOW_UP')
        self.assertTrue(result[0]['proposed_writes'][0]['policy_allowed'])


if __name__ == '__main__':
    unittest.main()
