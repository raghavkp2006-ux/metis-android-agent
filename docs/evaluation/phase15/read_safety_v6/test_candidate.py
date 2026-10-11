"""Exposed regressions and authored safety probes, never independent evaluation."""
import base64
import json
import unittest

from candidate import READ_LABELS, resolve
from regressions import FROZEN, HERE, load
from evaluate import evaluate, native_rows


class ReadSafetyTests(unittest.TestCase):
    def test_every_exposed_nonread_under_every_forced_read_label(self):
        frozen, _ = load()
        self.assertEqual(145, len(frozen['rows']))
        for row in frozen['rows']:
            if frozen['annotations'][row['id']]['disposition'] != 'read' or row['intent'] == 'UNKNOWN':
                for label in READ_LABELS:
                    with self.subTest(id=row['id'], label=label):
                        plan = resolve(row['utterance'], label, row['input_source'])
                        self.assertEqual(0, plan['reads'])
                        self.assertEqual(row['utterance'], plan['request_text'])
                        self.assertEqual((0, 0), (plan['proposals'], plan['completed']))

    def test_all_fourteen_original_forbidden_reads_are_blocked_on_replay(self):
        original = json.loads((FROZEN / 'first-reviewed-result.json').read_text(encoding='utf-8'))
        failures = [d for d in original['reviewed']['details'] if d['parsed']['reads'] and
                    (d['expected_intent'] == 'UNKNOWN' or d['expected']['disposition'] != 'read')]
        self.assertEqual(14, len(failures))
        for row in failures:
            with self.subTest(id=row['id']):
                self.assertEqual(0, resolve(row['utterance'], row['hybrid_intent'])['reads'])

    def test_native_fixture_is_an_exact_projection_of_exposed_truth(self):
        frozen, _ = load()
        rows = [line.split('\t') for line in (HERE / 'exposed-regressions.tsv').read_text().splitlines()[1:]]
        self.assertEqual(len(frozen['rows']), len(rows))
        for original, (identifier, encoded, disposition) in zip(frozen['rows'], rows):
            self.assertEqual(original['id'], identifier)
            self.assertEqual(original['utterance'], base64.b64decode(encoded).decode())
            self.assertEqual(frozen['annotations'][identifier]['disposition'], disposition)

    def test_supported_reads_and_original_spans(self):
        for text, label, raw in [('show my tasks', 'CHECK_TASKS', None),
                                 ('what do you know about DSP exam?', 'CHECK_MEMORY', 'DSP exam'),
                                 ('search memory for optics 🚀', 'CHECK_MEMORY', 'optics 🚀'),
                                 ('what did i write down about ceramic glazing?', 'CHECK_MEMORY', 'ceramic glazing'),
                                 ('what did i note about acoustic resonance?', 'CHECK_MEMORY', 'acoustic resonance'),
                                 ('what did i save about river erosion?', 'CHECK_MEMORY', 'river erosion'),
                                 ('what did i promise Rahul Kumar?', 'WHAT_DID_I_PROMISE', 'Rahul Kumar'),
                                 ('what did i promise Ravi 😀?', 'WHAT_DID_I_PROMISE', 'Ravi 😀'),
                                 ("what did i promise Zoë?", 'WHAT_DID_I_PROMISE', 'Zoë')]:
            with self.subTest(text=text):
                plan = resolve(text, label)
                self.assertEqual(1, plan['reads'])
                if raw:
                    entity = plan['entities'][0]
                    self.assertEqual(raw, entity['rawValue'])
                    self.assertEqual(raw, text.encode('utf-16-le')[entity['startOffset'] * 2:
                                                                 entity['endOffset'] * 2].decode('utf-16-le'))
                for wrong in set(READ_LABELS) - {label}:
                    self.assertEqual(0, resolve(text, wrong)['reads'])

    def test_person_topic_filters_actions_and_compounds_never_become_unfiltered_reads(self):
        for text in ['what did i promise him', 'what did i promise Tomorrow', 'what did i promise The Station',
                     'what did i promise 😀', 'what did i promise Rahul--Kumar',
                     'what did i promise to renew insurance', 'what did i promise my sister',
                     'what did i promise Rahul about the book', 'what did i promise Rahul last year',
                     'what did i promise Rahul and erase it', 'what do you know about that thing',
                     'search memory for the chapter', 'search memory for DSP exam last year',
                     'search memory for DSP exam with tag physics', 'search memory for DSP exam 2026',
                     'search memory for optics. delete tasks', 'show my tasks older than a month',
                     'show my tasks except completed ones', 'show my tasks and call Rahul',
                     'what do you know about optics or physics', 'remind me what i promised Rahul']:
            for label in READ_LABELS:
                with self.subTest(text=text, label=label):
                    plan = resolve(text, label)
                    self.assertEqual(0, plan['reads'])
                    self.assertEqual(text, plan['request_text'])

    def test_source_length_controls_and_calendar(self):
        for source in ['NOTIFICATION', 'SYSTEM', 'IMPORTED']:
            self.assertEqual(0, resolve('show my tasks', 'CHECK_TASKS', source)['reads'])
        for text in ['show my tasks\n', 'search memory for o\u200bld optics', 'search memory for ' + 'x' * 513]:
            self.assertEqual(0, resolve(text)['reads'])
        self.assertEqual('unsupported', resolve('show my calendar')['disposition'])

    def test_frozen_outputs_and_stale_native_snapshot_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'ignored'):
            evaluate(FROZEN / 'native-rules.tsv', FROZEN / 'first-reviewed-result.json')
        frozen, _ = load()
        with self.assertRaisesRegex(ValueError, 'Stale native'):
            native_rows(FROZEN / 'native-rules.tsv', frozen)


if __name__ == '__main__':
    unittest.main()
