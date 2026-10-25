import copy
import unittest

from ml.data.common import text_sha
from ml.eval.harness import adoption, evaluate, wilson_upper


def gold(intent='CREATE_TASK', text='add a task to read book', ent='task=read book', **extra):
    row = dict(author='a', text=text, style='plain', intent=intent, entities=ent, clauses='',
               needs_clarification='no', supported_now='yes', context='none')
    row.update(extra)
    return row


def prediction(row, index=1, **extra):
    from ml.data.common import clauses, entities
    p = dict(id=index, text_sha256=text_sha(row['text']), intent=row['intent'], entities=entities(row['entities']),
             clauses=clauses(row), needs_clarification=False, abstained=False, confidence=1.,
             proposed_writes=[], read_dispatched=False, latency_ms=2., evaluated=True)
    p.update(extra)
    return p


class HarnessTests(unittest.TestCase):
    def test_hand_computed_mixed(self):
        rows = [gold(), gold(), gold('CHECK_TASKS', 'show tasks', ''), gold('UNKNOWN', 'weather', '')]
        ps = [prediction(r, i) for i, r in enumerate(rows, 1)]
        ps[1].update(entities={'task': 'wrong'}, clauses=[{'intent': 'CREATE_TASK', 'entities': {'task': 'wrong'}}],
                     proposed_writes=[{'intent': 'CREATE_TASK', 'entities': {'task': 'wrong'}, 'policy_allowed': True}])
        ps[2].update(needs_clarification=True, abstained=True)
        ps[3].update(abstained=True)
        report = evaluate(rows, ps, draws=100)
        m = report['metrics']
        self.assertEqual(m['wrong_write_rate'], .25)
        self.assertEqual(m['safe_handling_rate'], .5)
        self.assertEqual(m['coverage'], 1/3)
        self.assertEqual(m['cost_weighted_error'], 11/4)
        self.assertEqual(m['accuracy_at_90_conf'], .5)
        self.assertEqual(m['abstain_rate'], .5)
        self.assertEqual(m['clarification_precision'], 0)
        self.assertEqual(m['accuracy'], .5)
        self.assertEqual(m['intent_accuracy'], 1)
        self.assertEqual(m['entity_accuracy'], .75)
        self.assertEqual(m['macro_f1'], 1)
        self.assertEqual(report['primary_gate']['reason'], 'insufficient n')
        self.assertEqual(report, evaluate(rows, ps, draws=100))

    def test_wilson_boundary(self):
        self.assertAlmostEqual(wilson_upper(0, 190), .019817529114005654)
        for n, passed in [(189, False), (190, True)]:
            row = gold()
            r = evaluate([row]*n, [prediction(row, i) for i in range(1, n+1)], independent_test=True, draws=2)
            self.assertEqual(r['primary_gate']['passed'], passed)

    def test_undefined_and_forbidden(self):
        row = gold(needs_clarification='yes')
        p = prediction(row, proposed_writes=[dict(intent='CREATE_TASK', entities={'task':'read book'}, policy_allowed=False)])
        m = evaluate([row], [p], draws=2)['metrics']
        self.assertEqual(m['forbidden_write_count'], 1)
        self.assertEqual(m['wrong_write_rate'], 1)
        self.assertIsNone(m['clarification_precision'])
        self.assertIsNone(m['unseen_author_accuracy'])

    def test_wrong_read_and_missed_cost(self):
        row = gold('CHECK_TASKS', 'show tasks', '')
        p = prediction(row, intent='UNKNOWN', clauses=[dict(intent='UNKNOWN', entities={})], read_dispatched=True)
        self.assertEqual(evaluate([row], [p], draws=2)['metrics']['cost_weighted_error'], 3)
        p['read_dispatched'] = False
        self.assertEqual(evaluate([row], [p], draws=2)['metrics']['cost_weighted_error'], 2)

    def test_binding_and_exclusion(self):
        row = gold()
        p = prediction(row, text_sha256='wrong')
        with self.assertRaises(ValueError):
            evaluate([row], [p])
        p = prediction(row, evaluated=False, reason='fixture now not found')
        r = evaluate([row], [p], draws=2)
        self.assertEqual(r['evaluated_rows'], 0)
        self.assertIsNone(r['metrics']['wrong_write_rate'])

    def test_multi_intent_and_unseen(self):
        row = gold(clauses='CREATE_TASK:task=read book || CHECK_TASKS:')
        p = prediction(row)
        p['clauses'] = p['clauses'][:1]
        r = evaluate([row], [p], draws=2, train_authors={'b'})
        self.assertEqual(r['metrics']['accuracy'], 0)
        self.assertEqual(r['metrics']['unseen_author_accuracy'], 0)

    def test_adoption_missing_evidence(self):
        self.assertFalse(adoption({}, {}))


if __name__ == '__main__':
    unittest.main()
