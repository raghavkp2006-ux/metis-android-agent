"""Deterministic utterance-level evaluator; see docs/ml/METRICS.md."""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import random
import subprocess

from ml.data.common import ROOT, WRITES, clauses, dump, entities, intents, issues, read_csv, sha, text_sha


def verify_freeze():
    receipt = json.loads((ROOT / 'docs/ml/metrics_freeze.json').read_text())
    path = receipt['path']
    committed = subprocess.check_output(['git', 'show', receipt['commit'] + ':' + path], cwd=ROOT)
    if hashlib.sha256(committed).hexdigest() != receipt['sha256'] or sha(ROOT / path) != receipt['sha256']:
        raise ValueError('measurement contract hash changed')
    subprocess.run(['git', 'merge-base', '--is-ancestor', receipt['commit'], 'HEAD'], cwd=ROOT, check=True)
    return receipt


def wilson_upper(errors, n):
    if not n:
        return None
    z = 1.959963984540054
    p = errors / n
    return (p + z*z/(2*n) + z*math.sqrt(p*(1-p)/n + z*z/(4*n*n))) / (1 + z*z/n)


def ratio(a, b):
    return a / b if b else None


def validate_prediction(p, row, index):
    required = {'id', 'text_sha256', 'intent', 'entities', 'clauses', 'needs_clarification',
                'abstained', 'confidence', 'proposed_writes', 'read_dispatched', 'latency_ms', 'evaluated'}
    if not required <= set(p) or p['id'] != index or p['text_sha256'] != text_sha(row['text']):
        raise ValueError(f'prediction binding/schema mismatch at {index}')
    for field in ('needs_clarification', 'abstained', 'read_dispatched', 'evaluated'):
        if type(p[field]) is not bool:
            raise ValueError('prediction boolean required: ' + field)
    for field in ('confidence', 'latency_ms'):
        if type(p[field]) not in (float, int) or not math.isfinite(p[field]) or p[field] < 0:
            raise ValueError('invalid ' + field)
    if p['confidence'] > 1 or p['intent'] not in intents() or not isinstance(p['entities'], dict):
        raise ValueError('invalid intent/entities/confidence')
    if not isinstance(p['clauses'], list) or not p['clauses'] or not isinstance(p['proposed_writes'], list):
        raise ValueError('clauses/proposals must be lists')
    for part in p['clauses'] + p['proposed_writes']:
        if part.get('intent') not in intents() or not isinstance(part.get('entities'), dict):
            raise ValueError('invalid structured prediction')
        if any(not isinstance(k, str) or not isinstance(v, str) for k, v in part['entities'].items()):
            raise ValueError('entity strings required')
    for part in p['proposed_writes']:
        if part['intent'] not in WRITES or type(part.get('policy_allowed')) is not bool:
            raise ValueError('write proposal needs policy evidence')
    if not p['evaluated'] and not p.get('reason'):
        raise ValueError('excluded prediction needs reason')


def observation(row, p, train_authors):
    gold = clauses(row)
    gold_parts = [{'intent': c['intent'], 'entities': c['entities']} for c in gold]
    predicted = [{'intent': c['intent'], 'entities': c['entities']} for c in p['clauses']]
    clarify = row['needs_clarification'] == 'yes'
    supported = row['supported_now'] == 'yes'
    known = row['intent'] not in ('UNKNOWN', 'UNSUPPORTED')
    meaning = gold_parts == predicted
    exact = meaning and clarify == p['needs_clarification']
    gold_writes = [c for c in gold_parts if c['intent'] in WRITES]
    writes = [{'intent': c['intent'], 'entities': c['entities']} for c in p['proposed_writes']]
    # Each proposed write must be an ordered subsequence of intended writes.
    cursor = 0
    wrong_write = False
    for write in writes:
        while cursor < len(gold_writes) and gold_writes[cursor] != write:
            cursor += 1
        if cursor == len(gold_writes):
            wrong_write = True
        cursor += 1
    wrong_write = bool(writes) and (wrong_write or clarify or not supported)
    forbidden = sum(not c['policy_allowed'] or clarify or not supported for c in p['proposed_writes'])
    handled = exact and not p['abstained'] and not p['needs_clarification']
    safe = not wrong_write and not forbidden and (handled or (clarify and p['needs_clarification']) or
           ((not known or not supported) and p['abstained']))
    wrong_read = p['read_dispatched'] and not meaning
    needless = p['needs_clarification'] and not clarify
    missed = known and supported and not clarify and not handled
    cost = 10 if wrong_write else 3 if wrong_read else 1 if needless else 2 if missed else 0
    return dict(gold=row['intent'], pred=p['intent'], wrong_write=int(wrong_write), forbidden=forbidden,
                safe=int(safe), command=int(known and supported), covered=int(known and supported and handled),
                clarification=int(p['needs_clarification']), correct_clarification=int(clarify and p['needs_clarification']),
                cost=cost, high=int(not p['abstained'] and p['confidence'] >= .9), exact=int(exact),
                intent_exact=int(row['intent'] == p['intent']), entity_exact=int(entities(row['entities']) == p['entities']),
                abstain=int(p['abstained']), unseen=None if train_authors is None else int(row['author'] not in train_authors),
                latency=p['latency_ms'])


def percentile(values, q):
    if not values:
        return None
    values = sorted(values)
    return values[max(0, math.ceil(q * len(values)) - 1)]


def summarize(obs):
    n = len(obs)
    total = lambda key: sum(o[key] for o in obs)
    labels = sorted({o['gold'] for o in obs} | {o['pred'] for o in obs})
    f1 = []
    for label in labels:
        tp = sum(o['gold'] == o['pred'] == label for o in obs)
        denom = sum(o['gold'] == label for o in obs) + sum(o['pred'] == label for o in obs)
        f1.append(2 * tp / denom if denom else 0)
    return dict(wrong_write_rate=ratio(total('wrong_write'), n),
                wrong_write_wilson_upper=wilson_upper(total('wrong_write'), n),
                forbidden_write_count=total('forbidden'), safe_handling_rate=ratio(total('safe'), n),
                coverage=ratio(total('covered'), total('command')),
                clarification_precision=ratio(total('correct_clarification'), total('clarification')),
                cost_weighted_error=ratio(total('cost'), n),
                accuracy_at_90_conf=ratio(sum(o['exact']*o['high'] for o in obs), total('high')),
                abstain_rate=ratio(total('abstain'), n),
                unseen_author_accuracy=ratio(sum(o['exact'] for o in obs if o['unseen'] == 1), sum(o['unseen'] == 1 for o in obs)),
                inference_latency_ms=ratio(total('latency'), n),
                latency_p50_ms=percentile([o['latency'] for o in obs], .5),
                latency_p95_ms=percentile([o['latency'] for o in obs], .95),
                accuracy=ratio(total('exact'), n), intent_accuracy=ratio(total('intent_exact'), n),
                entity_accuracy=ratio(total('entity_exact'), n), macro_f1=ratio(sum(f1), len(f1)))


def evaluate(gold, predictions, *, draws=2000, seed=17, train_authors=None, independent_test=False):
    if len(gold) != len(predictions) or not gold:
        raise ValueError('nonempty, one-to-one gold/predictions required')
    by_id = {p['id']: p for p in predictions}
    if len(by_id) != len(predictions) or set(by_id) != set(range(1, len(gold)+1)):
        raise ValueError('duplicate/missing prediction ids')
    obs, failures, excluded, label_issues = [], [], [], []
    for index, row in enumerate(gold, 1):
        p = by_id[index]
        validate_prediction(p, row, index)
        defects = issues(row)
        if defects:
            label_issues.append(dict(id=index, issues=defects))
        if not p['evaluated']:
            excluded.append(dict(id=index, reason=p['reason']))
            continue
        o = observation(row, p, train_authors)
        obs.append(o)
        if not o['exact'] or o['wrong_write'] or o['forbidden']:
            failures.append(dict(id=index, text=row['text'], gold=clauses(row), prediction=p,
                                 wrong_write=bool(o['wrong_write']), cost=o['cost'], label_issues=defects))
    metrics = summarize(obs)
    samples = {key: [] for key in metrics}
    rng = random.Random(seed)
    if draws < 1:
        raise ValueError('bootstrap draws must be positive')
    for _ in range(draws if obs else 0):
        result = summarize(rng.choices(obs, k=len(obs)))
        for key, value in result.items():
            if value is not None:
                samples[key].append(value)
    ci = {key: dict(low=percentile(values, .025), high=percentile(values, .975), valid_draws=len(values))
          for key, values in samples.items()}
    counts = Counter(row['intent'] for row in gold)
    matrix = {}
    for o in obs:
        matrix.setdefault(o['gold'], Counter())[o['pred']] += 1
    n = len(obs)
    gate_reason = 'insufficient n' if n < 190 else 'development only' if not independent_test else 'invalid/excluded labels' if excluded or label_issues else 'evaluated'
    gate = independent_test and n >= 190 and not excluded and not label_issues and metrics['wrong_write_rate'] == 0 and metrics['wrong_write_wilson_upper'] < .02
    return dict(metrics=metrics, bootstrap_95_ci=ci, seed=seed, draws=draws, total_rows=len(gold), evaluated_rows=n,
                excluded=excluded, label_issues=label_issues, per_author_counts=dict(Counter(r['author'] for r in gold)),
                per_intent_counts=dict(counts), warnings=[f'{key}: under 12 rows ({counts[key]})' for key in sorted(intents() | set(counts)) if counts[key] < 12],
                unseen_author_status='not found' if train_authors is None else 'explicit manifest',
                primary_gate=dict(passed=gate, reason=gate_reason), forbidden_gate=bool(obs) and not excluded and metrics['forbidden_write_count'] == 0,
                confusion_matrix=matrix, failures=failures)


def adoption(model, baseline):
    """Compare bound reports; missing metrics or different populations fail closed."""
    keys = ('gold_sha256', 'evaluated_ids')
    if any(key not in model or model.get(key) != baseline.get(key) for key in keys):
        return False
    a, b = model['metrics'], baseline['metrics']
    required = ('wrong_write_rate', 'forbidden_write_count', 'coverage', 'cost_weighted_error')
    if any(a.get(k) is None or b.get(k) is None for k in required):
        return False
    return (model['primary_gate']['passed'] and a['wrong_write_rate'] <= b['wrong_write_rate'] and
            a['forbidden_write_count'] == 0 and a['coverage'] >= b['coverage'] + .10 and
            a['cost_weighted_error'] < b['cost_weighted_error'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gold', required=True, type=Path)
    parser.add_argument('--predictions', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--train-authors', type=Path)
    parser.add_argument('--independent-test', action='store_true')
    args = parser.parse_args()
    freeze = verify_freeze()
    gold = read_csv(args.gold)
    predictions = [json.loads(line) for line in args.predictions.read_text(encoding='utf-8').splitlines() if line.strip()]
    authors = None if args.train_authors is None else set(json.loads(args.train_authors.read_text()))
    report = evaluate(gold, predictions, train_authors=authors, independent_test=args.independent_test)
    report.update(gold_sha256=sha(args.gold), predictions_sha256=sha(args.predictions), metrics_freeze=freeze,
                  evaluator_sha256=sha(Path(__file__)), data_contract_sha256=sha(ROOT / 'ml/data/common.py'),
                  evaluated_ids=[p['id'] for p in predictions if p['evaluated']])
    args.output.mkdir(parents=True, exist_ok=True)
    for name, data in [('metrics', report), ('confusion_matrix', report['confusion_matrix']), ('failures', report['failures'])]:
        dump(args.output / (name + '.json'), data)
    print(json.dumps(dict(evaluated=report['evaluated_rows'], primary_gate=report['primary_gate'])))


if __name__ == '__main__':
    main()
