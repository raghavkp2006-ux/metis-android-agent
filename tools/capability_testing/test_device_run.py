import csv
import os
import tempfile
import unittest

from tools.device_run import (
    ALL_PHRASES,
    VOLATILE_PHRASE_IDS,
    classify_response,
    generate_compare_csv,
    validate_csv_hashes,
)


class DeviceRunCompareIntegrityTest(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _write_csv(self, filename, fieldnames, rows):
        path = os.path.join(self.temp_dir.name, filename)
        with open(path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for r in rows:
                writer.writerow(r)
        return path

    def test_phrases_include_ml_alarm_77_to_80(self):
        self.assertIn(77, ALL_PHRASES)
        self.assertIn(78, ALL_PHRASES)
        self.assertIn(79, ALL_PHRASES)
        self.assertIn(80, ALL_PHRASES)
        self.assertEqual(ALL_PHRASES[77], '7:05 am')
        self.assertEqual(ALL_PHRASES[78], '7:15 am')
        self.assertEqual(ALL_PHRASES[79], '6:02 am')
        self.assertEqual(ALL_PHRASES[80], '7 baje')
        self.assertIn(57, VOLATILE_PHRASE_IDS)

    def test_classify_task_proposal_with_locally_is_not_call(self):
        nodes = [
            {'text': 'Review this task request. Nothing is saved until you accept.'},
            {'text': 'Create task'},
            {'text': 'Proposed action — awaiting acceptance'},
            {'text': 'Title: finish DSP assignment'},
            {'text': 'Reason: You requested this task. Accept to save it locally; no deadline or reminder is added.'},
            {'text': 'Accept proposal'},
            {'text': 'Cancel'},
        ]
        resp_class, detail, full_text = classify_response(nodes, 'dev.metis.agent', 'add task finish DSP assignment')
        self.assertEqual(resp_class, 'PROPOSAL')
        self.assertTrue(detail.startswith('CREATE_TASK'), f'Expected CREATE_TASK detail, got: {detail}')
        self.assertNotIn('CALL', detail)

    def test_classify_call_proposal(self):
        nodes = [
            {'text': 'Call contact'},
            {'text': 'Proposed action — awaiting acceptance'},
            {'text': 'Recipient: Rahul'},
            {'text': 'Accept proposal'},
            {'text': 'Cancel'},
        ]
        resp_class, detail, full_text = classify_response(nodes, 'dev.metis.agent', 'call Rahul')
        self.assertEqual(resp_class, 'PROPOSAL')
        self.assertTrue(detail.startswith('CALL'), f'Expected CALL detail, got: {detail}')

    def test_classify_alarm_proposal(self):
        nodes = [
            {'text': 'Review this alarm request. Nothing is set until you accept.'},
            {'text': 'Set alarm'},
            {'text': 'Proposed action — awaiting acceptance'},
            {'text': 'Time: 07:05'},
            {'text': 'Understood by language model (84%)'},
            {'text': 'Accept proposal'},
            {'text': 'Cancel'},
        ]
        resp_class, detail, full_text = classify_response(nodes, 'dev.metis.agent', '7:05 am')
        self.assertEqual(resp_class, 'PROPOSAL')
        self.assertTrue(detail.startswith('ALARM'), f'Expected ALARM detail, got: {detail}')
        self.assertIn('07:05', detail)

    def test_classify_timer_proposal(self):
        nodes = [
            {'text': 'Review this timer request. Nothing is started until you accept.'},
            {'text': 'Start timer'},
            {'text': 'Proposed action — awaiting acceptance'},
            {'text': 'Duration: 300s'},
            {'text': 'Accept proposal'},
            {'text': 'Cancel'},
        ]
        resp_class, detail, full_text = classify_response(nodes, 'dev.metis.agent', 'timer 5 mins')
        self.assertEqual(resp_class, 'PROPOSAL')
        self.assertTrue(detail.startswith('TIMER'), f'Expected TIMER detail, got: {detail}')
        self.assertIn('300s', detail)

    def test_refuse_compare_when_baseline_hash_missing(self):
        fields = ['id', 'phrase', 'build', 'class', 'detail', 'latency_ms', 'response_text', 'head_hash']
        main_path = self._write_csv('main.csv', fields, [
            {'id': '1', 'phrase': 'test', 'build': 'main', 'class': 'OTHER', 'detail': 'det', 'latency_ms': '10', 'response_text': 't', 'head_hash': 'h1'}
        ])
        pr_path = self._write_csv('pr.csv', fields + ['baseline_hash'], [
            {'id': '1', 'phrase': 'test', 'build': 'pr', 'class': 'OTHER', 'detail': 'det', 'latency_ms': '10', 'response_text': 't', 'baseline_hash': 'b1', 'head_hash': 'h1'}
        ])
        out_path = os.path.join(self.temp_dir.name, 'out.csv')
        with self.assertRaises(ValueError) as ctx:
            generate_compare_csv(main_path, pr_path, out_path, baseline_hash='b1', head_hash='h1')
        self.assertIn('missing baseline_hash', str(ctx.exception))

    def test_refuse_compare_when_head_hash_missing(self):
        fields = ['id', 'phrase', 'build', 'class', 'detail', 'latency_ms', 'response_text', 'baseline_hash']
        main_path = self._write_csv('main.csv', fields + ['head_hash'], [
            {'id': '1', 'phrase': 'test', 'build': 'main', 'class': 'OTHER', 'detail': 'det', 'latency_ms': '10', 'response_text': 't', 'baseline_hash': 'b1', 'head_hash': 'h1'}
        ])
        pr_path = self._write_csv('pr.csv', fields, [
            {'id': '1', 'phrase': 'test', 'build': 'pr', 'class': 'OTHER', 'detail': 'det', 'latency_ms': '10', 'response_text': 't', 'baseline_hash': 'b1'}
        ])
        out_path = os.path.join(self.temp_dir.name, 'out.csv')
        with self.assertRaises(ValueError) as ctx:
            generate_compare_csv(main_path, pr_path, out_path, baseline_hash='b1', head_hash='h1')
        self.assertIn('missing baseline_hash or head_hash', str(ctx.exception))

    def test_refuse_compare_when_hash_mismatch_build_under_test(self):
        fields = ['id', 'phrase', 'build', 'class', 'detail', 'latency_ms', 'response_text', 'baseline_hash', 'head_hash']
        main_path = self._write_csv('main.csv', fields, [
            {'id': '1', 'phrase': 'test', 'build': 'main', 'class': 'OTHER', 'detail': 'det', 'latency_ms': '10', 'response_text': 't', 'baseline_hash': 'wrong_base', 'head_hash': 'h1'}
        ])
        pr_path = self._write_csv('pr.csv', fields, [
            {'id': '1', 'phrase': 'test', 'build': 'pr', 'class': 'OTHER', 'detail': 'det', 'latency_ms': '10', 'response_text': 't', 'baseline_hash': 'wrong_base', 'head_hash': 'h1'}
        ])
        out_path = os.path.join(self.temp_dir.name, 'out.csv')
        with self.assertRaises(ValueError) as ctx:
            generate_compare_csv(main_path, pr_path, out_path, baseline_hash='expected_base', head_hash='h1')
        self.assertIn('does not match build under test expected hash', str(ctx.exception))

    def test_compare_class_diff_is_changed_never_same(self):
        fields = ['id', 'phrase', 'build', 'class', 'detail', 'latency_ms', 'response_text', 'baseline_hash', 'head_hash']
        main_path = self._write_csv('main.csv', fields, [
            {'id': '71', 'phrase': 'kal 8 baje alarm laga do', 'build': 'main', 'class': 'CLARIFICATION', 'detail': 'AM or PM?', 'latency_ms': '10', 'response_text': 't', 'baseline_hash': 'b1', 'head_hash': 'h1'}
        ])
        pr_path = self._write_csv('pr.csv', fields, [
            {'id': '71', 'phrase': 'kal 8 baje alarm laga do', 'build': 'pr', 'class': 'EXTERNAL_APP', 'detail': 'External app foreground', 'latency_ms': '10', 'response_text': 't', 'baseline_hash': 'b1', 'head_hash': 'h1'}
        ])
        out_path = os.path.join(self.temp_dir.name, 'out.csv')
        generate_compare_csv(main_path, pr_path, out_path, baseline_hash='b1', head_hash='h1')
        with open(out_path, 'r', encoding='utf-8') as f:
            rows = list(csv.DictReader(f))
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['change'], 'changed')
        self.assertNotEqual(rows[0]['change'], 'same')
        self.assertEqual(rows[0]['baseline_hash'], 'b1')
        self.assertEqual(rows[0]['head_hash'], 'h1')


if __name__ == '__main__':
    unittest.main()

