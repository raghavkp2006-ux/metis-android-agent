"""Score synthetic diagnostics after selection; never score unapproved human drafts."""
import base64
import csv
import hashlib
import json
import sys
from pathlib import Path
import numpy as np
from threadpoolctl import threadpool_limits
from data import HERE, ROOT
from classifier import engine, partitions
from candidate import resolve

OUTPUT = ROOT / 'app/build/reports/phase15/v14'


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest()


def native_report():
    rows = json.loads((HERE / 'regression.json').read_text(encoding='utf-8'))
    lines = (OUTPUT / 'native.tsv').read_text(encoding='utf-8').splitlines()
    if lines[0] != '# input_sha256=' + hashlib.sha256((HERE / 'regression.tsv').read_bytes()).hexdigest():
        raise ValueError('Native input binding drift')
    sources = list((ROOT / 'app/src/main/java/dev/metis/agent/domain/agent').glob('*.kt')) + [
        ROOT / 'app/src/main/java/dev/metis/agent/presentation/navigation/AgentApp.kt',
        ROOT / 'app/src/main/java/dev/metis/agent/domain/storage/MemorySearch.kt']
    binding = hashlib.sha256()
    for path in sorted(sources, key=lambda path: path.relative_to(ROOT).as_posix()):
        binding.update(path.relative_to(ROOT).as_posix().encode())
        binding.update(path.read_bytes().replace(b'\r\n', b'\n'))
    if lines[1] != '# production_sha256=' + binding.hexdigest():
        raise ValueError('Stale native production snapshot')
    actual = list(csv.DictReader((line for line in lines if not line.startswith('#')), delimiter='\t'))
    if [row['id'] for row in rows] != [row['id'] for row in actual]:
        raise ValueError('Missing/reordered native results')
    for row, result in zip(rows, actual, strict=True):
        if int(result['reads']) != row['parsed']['reads']:
            raise ValueError('Admission mismatch: ' + row['id'])
        if int(result['reads']) and base64.b64decode(result['query_base64']).decode() != row['parsed']['query_text']:
            raise ValueError('Query mismatch: ' + row['id'])
        if result['status'] == 'FOLLOW_UP' and row['utterance'] not in base64.b64decode(result['message_base64']).decode():
            raise ValueError('Constraint lost: ' + row['id'])
    summaries = {}
    for section in ('retained', 'v14', 'human004', 'classifier14'):
        pairs = [(row, result) for row, result in zip(rows, actual, strict=True) if row['section'] == section]
        summaries[section] = dict(rows=len(pairs), reads=sum(int(result['reads']) for _, result in pairs),
                                 dispositions=dict(__import__('collections').Counter(result['status'] for _, result in pairs)),
                                 constraints_retained_in_followups=True)
    return summaries


def main():
    parts = partitions()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    model, selected, trials = engine.select(parts['train'], parts['calibration'], parts['selection'])
    bindings = {name: digest(HERE / name) for name in ('protocol.json', 'classifier-development.json', 'classifier.py')}
    bindings['retained_engine_sha256'] = digest(HERE.parent / 'candidate_v12/v12_model.py')
    payload = engine.export(model, selected, bindings)
    previous_path = OUTPUT / 'disabled-candidate-v14.json'
    if previous_path.exists():
        previous = json.loads(previous_path.read_text(encoding='utf-8'))
        if previous['heads'] != payload['heads'] or previous['selected'] != payload['selected'] or previous['bindings'] != payload['bindings']:
            raise ValueError('Scored candidate cannot be retuned; create a separate version')
    test = parts['test']
    labels, probabilities = engine.predict(model, test, selected['settings'])
    restored, values = engine.exported_predict(payload, test)
    if labels != restored or not np.allclose(probabilities, values, atol=1e-12, rtol=1e-12):
        raise ValueError('Numeric export parity failed')
    score = engine.metrics(test, labels)
    raw = engine.metrics(test, engine.predict(model, test)[0])
    false_accepts = [dict(id=row['id'], utterance=row['utterance'], predicted=label,
                         read_calls=resolve(row['utterance'], predicted_intent=label)['reads'])
                     for row, label in zip(test, labels, strict=True)
                     if row['intent'] == 'UNKNOWN' and label != 'UNKNOWN']
    if any(row['read_calls'] for row in false_accepts):
        raise ValueError('Classifier UNKNOWN false accept bypassed the deterministic gate')
    human = json.loads((HERE / 'human-review-proposal.json').read_text(encoding='utf-8'))['rows']
    predictions, confidence = engine.predict(model, human, selected['settings'])
    comparison = dict(available=False, reason='Retained v12 numeric export not present; no historical model refit')
    old_path = ROOT / 'app/build/reports/phase15/v12/disabled-candidate-v12.json'
    if old_path.exists():
        import v12_model
        old = json.loads(old_path.read_text(encoding='utf-8'))
        if old['bindings']['data_sha256'] != digest(HERE.parent / 'candidate_v12/classifier-development.json'):
            raise ValueError('Retained v12 export data drift')
        comparison = dict(available=True, scope='Same new synthetic test; not independent evaluation',
                          v12=v12_model.metrics(test, v12_model.exported_predict(old, test)[0]),
                          v14=score, historical_model_refitted=False)
    report = dict(version='phase15-candidate-v14', decision='NO_GO', ml_enabled=False, ml_packaged=False,
                  independent_evaluation=False, representative_human_calibration=False,
                  user_training_rows=0, user_calibration_rows=0, user_selection_rows=0,
                  synthetic_test_rows=len(test), synthetic_test=score, synthetic_test_raw=raw,
                  classifier_false_accept_gate_probes=false_accepts,
                  synthetic_calibration=engine.calibration_metrics(test, probabilities),
                  selected=selected, trials=trials, same_test_comparison=comparison,
                  partition_sizes={split: len(rows) for split, rows in parts.items()},
                  native=native_report(), numeric_export_parity=True,
                  human_diagnostic=dict(ungraded=True, macro_f1=None,
                                        predictions=[dict(id=row['id'], intent=prediction, confidence=float(value))
                                                     for row, prediction, value in zip(human, predictions,
                                                         confidence.max(axis=1), strict=True)]),
                  remaining_gates=['reviewed human annotations', 'representative human calibration',
                                   'fresh unseen human evaluation', 'useful safe ML read gain',
                                   'Android model parity and latency/RAM/battery'])
    previous_path.write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
    (OUTPUT / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: report[key] for key in ('decision', 'synthetic_test', 'synthetic_test_raw',
                                                  'partition_sizes', 'same_test_comparison', 'native')}, indent=2))


if __name__ == '__main__':
    # Parallel BLAS reductions can change last-bit coefficients across repeated fits.
    # Reproduce the first scored single-thread run; never relax the immutable export guard.
    with threadpool_limits(limits=1):
        main()
