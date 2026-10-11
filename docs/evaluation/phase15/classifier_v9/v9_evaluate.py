"""Compare the disabled v9 classifier with fixed v8; exposed data is regression only."""
import argparse
import base64
import json
import platform
import sys
from collections import Counter
from pathlib import Path

import numpy as np
import scipy
import sklearn

from v9_data import HERE, ROOT, VERSION, LABELS, PARTITIONS, digest, validate
from v9_model import select, predict, metrics, calibration_metrics, export, exported_predict

sys.path.insert(0, str(HERE.parent / 'read_coverage_v8'))
from v8_evaluate import check_manifest as check_v8, snapshot, native_entities, load
from v8_candidate import resolve

OUTPUT_ROOT = ROOT / 'app/build/reports/phase15/v9'


def check_manifest():
    check_v8()
    value = json.loads((HERE / 'development-manifest-v9.json').read_text(encoding='utf-8'))
    if value['version'] != VERSION or any(value[k] for k in ('human_approved', 'ml_enabled', 'ml_packaged')):
        raise ValueError('Unauthorized v9 manifest state')
    for path, expected in value['hashes'].items():
        if digest(ROOT / path) != expected:
            raise ValueError('V9 source/data drift: ' + path)
    return value


def output_path(path):
    if not path.resolve().is_relative_to(OUTPUT_ROOT.resolve()):
        raise ValueError('V9 reports/exports must stay in ignored app/build/reports/phase15/v9')


def complete_outcomes(rows, native, model, selected):
    accepted, probabilities = predict(model, rows, selected['temperature'], selected['threshold'], selected['abstain'])
    raw, _ = predict(model, rows)
    details = []
    for row, label, raw_label, confidence in zip(rows, accepted, raw, probabilities.max(axis=1)):
        baseline = native[row['id']]
        base_reads = int(baseline['reads'])
        base_label = baseline['intent'] if baseline['intent'] in LABELS else 'UNKNOWN'
        plan = resolve(row['utterance'], label, row['input_source'])
        # Existing valid native reads always win. Candidate labels go through the
        # complete fixed parser/gate; model confidence grants no read authority.
        hybrid_reads = base_reads or plan['reads']
        hybrid_label = base_label if base_label != 'UNKNOWN' else label
        prohibited = row['disposition'] != 'read'
        forced = {candidate: resolve(row['utterance'], candidate, row['input_source'])['reads']
                  for candidate in LABELS if candidate != 'UNKNOWN'} if prohibited else {}
        native_query = base64.b64decode(baseline['query_base64']).decode()
        query = native_query if base_reads else plan['query_text']
        entities = native_entities(baseline) if base_reads else plan['entities'] if hybrid_reads else []
        disposition = ('read' if hybrid_reads else 'unsupported' if baseline['status'] == 'UNSUPPORTED'
                       or label == 'CHECK_CALENDAR' else 'clarify')
        details.append(dict(id=row['id'], expected_intent=row['intent'], expected_disposition=row['disposition'],
            expected_entities=row['entities'], requested_filters=row['filters'], request_text=row['utterance'],
            raw_label=raw_label, candidate_label=label, confidence=float(confidence), native_label=base_label,
            hybrid_label=hybrid_label, native_reads=base_reads, simulated_hybrid_reads=int(hybrid_reads),
            prohibited=prohibited, disposition=disposition, entities=entities, query_text=query,
            forced_label_reads=forced, native_status=baseline['status'], native_entities=native_entities(baseline)))
    positive = [d for d in details if d['expected_disposition'] == 'read']
    admitted = [d for d in positive if d['simulated_hybrid_reads']]
    prohibited = [d for d in details if d['prohibited']]
    entity_rows = [d for d in positive if d['expected_entities']]
    entity_key = lambda entity: (entity['type'], entity['rawValue'], entity['startOffset'], entity['endOffset'])
    entity_expected = sum(len(d['expected_entities']) for d in entity_rows)
    entity_produced = sum(len(d['native_entities']) for d in entity_rows)
    entity_matches = sum(len({entity_key(e) for e in d['expected_entities']} &
                             {entity_key(e) for e in d['native_entities']}) for d in entity_rows)
    counts = dict(rows=len(rows), expected_reads=len(positive), native_v8_reads=sum(d['native_reads'] for d in details),
        simulated_hybrid_reads=sum(d['simulated_hybrid_reads'] for d in details),
        native_forbidden_reads=sum(d['native_reads'] for d in prohibited),
        simulated_hybrid_forbidden_reads=sum(d['simulated_hybrid_reads'] for d in prohibited),
        retained_native_reads=sum(bool(d['native_reads'] and d['simulated_hybrid_reads']) for d in details),
        forced_label_attempts=sum(len(d['forced_label_reads']) for d in prohibited),
        forced_label_forbidden_reads=sum(sum(d['forced_label_reads'].values()) for d in prohibited),
        exact_dispositions=sum(d['disposition'] == d['expected_disposition'] for d in details),
        admitted_exact_entities=sum(d['entities'] == d['expected_entities'] for d in admitted),
        admitted_positive_reads=len(admitted),
        expected_entity_rows=len(entity_rows), native_exact_entity_rows=sum(d['native_entities'] == d['expected_entities'] for d in entity_rows),
        native_entity_precision=entity_matches / entity_produced if entity_produced else None,
        native_entity_recall=entity_matches / entity_expected if entity_expected else None,
        admitted_annotated_queries=sum('query_text' in row for d, row in zip(details, rows)
                                   if d['simulated_hybrid_reads'] and row['disposition'] == 'read'),
        admitted_exact_queries=sum(d['query_text'] == row['query_text'] for d, row in zip(details, rows)
                                   if d['simulated_hybrid_reads'] and row['disposition'] == 'read' and 'query_text' in row),
        filter_rows=sum(bool(row['filters']) for row in rows),
        filter_rows_without_reads=sum(bool(row['filters']) and not d['simulated_hybrid_reads'] for d, row in zip(details, rows)),
        request_strings_retained=sum(d['request_text'] == row['utterance'] for d, row in zip(details, rows)))
    if any(counts[k] for k in ('native_forbidden_reads', 'simulated_hybrid_forbidden_reads', 'forced_label_forbidden_reads')):
        raise AssertionError('Forbidden read: ' + str(counts))
    if counts['retained_native_reads'] != counts['native_v8_reads']:
        raise AssertionError('Lost valid v8 reads')
    if any(int(r['proposals']) or int(r['completed']) for r in native.values()):
        raise AssertionError('Read-only native snapshot produced effects')
    return dict(counts=counts, semantic_intents=dict(
        native_v8=metrics(rows, [d['native_label'] for d in details]), raw_classifier=metrics(rows, raw),
        threshold_classifier=metrics(rows, accepted), rules_first_hybrid=metrics(rows, [d['hybrid_label'] for d in details])),
        calibration=dict(before=calibration_metrics(model, rows, 1.0),
                         after=calibration_metrics(model, rows, selected['temperature'])), details=details)


def evaluate(native_path, output):
    output_path(output)
    manifest = check_manifest()
    frozen, before = load()
    data = json.loads((HERE / 'development-data.json').read_text(encoding='utf-8'))
    rows = data['rows']
    validate(rows)
    parts = {part: [r for r in rows if r['split'] == part] for part in PARTITIONS}
    # Test and all exposed labels are unreachable by model/temperature/threshold selection.
    model, selected, trials = select(parts['train'], parts['calibration'], parts['selection'])
    payload = export(model, selected, digest(HERE / 'development-data.json'))
    payload['selection_partitions'] = ['train', 'calibration', 'selection']
    payload['manifest_sha256'] = digest(HERE / 'development-manifest-v9.json')
    # Include metadata in the integrity binding, rather than leaving an unbound sidecar.
    import hashlib
    payload.pop('payload_sha256')
    payload['payload_sha256'] = hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()
    candidate_labels, candidate_probabilities = predict(model, rows, selected['temperature'], selected['threshold'], selected['abstain'])
    exported_labels, exported_probabilities = exported_predict(payload, rows)
    if candidate_labels != exported_labels or not np.allclose(candidate_probabilities, exported_probabilities, rtol=1e-12, atol=1e-12):
        raise AssertionError('Python numeric export parity failure')
    baseline = snapshot(native_path, HERE / 'development-input.tsv', [r['id'] for r in rows])
    sections = {part: complete_outcomes(values, {r['id']: baseline[r['id']] for r in values}, model, selected)
                for part, values in parts.items()}
    regressions = {}
    for name, directory, input_name, native_name in (
        ('exposed_irs003', 'human_review_003/first_evaluation', 'frozen-benchmark.tsv', 'native-exposed.tsv'),
        ('v8_development', 'read_coverage_v8', 'development-input.tsv', 'native-development.tsv'),
        ('v7_development', 'read_coverage_v7', 'development-input.tsv', 'native-retained-v7.tsv')):
        directory = HERE.parent / directory
        if name == 'exposed_irs003':
            regression_rows = [dict(row, **frozen['annotations'][row['id']]) for row in frozen['rows']]
            for row in regression_rows:
                row.setdefault('filters', {})
        else:
            regression_rows = json.loads((directory / 'development-cases.json').read_text(encoding='utf-8'))
            for row in regression_rows:
                row.setdefault('filters', {})
        actual = snapshot(ROOT / 'app/build/reports/phase15/v8' / native_name, directory / input_name,
                          [r['id'] for r in regression_rows])
        regressions[name] = complete_outcomes(regression_rows, actual, model, selected)
    exposed_counts = regressions['exposed_irs003']['counts']
    test_counts = sections['test']['counts']
    gain = test_counts['simulated_hybrid_reads'] - test_counts['native_v8_reads']
    report = dict(version=VERSION, decision='NO_GO', adoption_eligible=False, ml_enabled=False, ml_packaged=False,
        scope='Assistant-authored development holdout and exposed regressions; NOT independent human evaluation',
        selected=selected, selection_trials=trials, partition_rows={k: len(v) for k, v in parts.items()},
        partition_families=dict(Counter(data['families'].values())), excluded_exact_matches=data['exclusions'],
        fitting_audit={k: dict(ids=[r['id'] for r in v], families=sorted({r['family_id'] for r in v})) for k, v in parts.items()},
        development=sections, regressions=regressions, held_out_safe_read_gain=gain,
        exposed_safe_read_gain=exposed_counts['simulated_hybrid_reads'] - exposed_counts['native_v8_reads'],
        python_export_parity=dict(rows=len(rows), labels_equal=True, probabilities_atol=1e-12, android_parity=False),
        manifest=manifest, frozen_before=before, frozen_after=load()[1],
        runtime=dict(python=platform.python_version(), sklearn=sklearn.__version__, numpy=np.__version__, scipy=scipy.__version__),
        gates=dict(complete_read_improvement=gain > 0,
                   macro_f1_0_90=sections['test']['semantic_intents']['rules_first_hybrid']['macro_f1'] >= .90,
                   sample_unknown_rejection=sections['test']['semantic_intents']['threshold_classifier']['unknown_false_accepts'] == 0,
                   validated_entity_outcomes=False, independent_evaluation=False,
                   representative_calibration=False, actual_repository_policy=False, android_parity_measurements=False),
        limits=['No new human approval, frozen evaluation, Android model, device or Room execution.',
                'Synthetic family partitions are not independent speakers/scenarios; template vocabulary is shared.',
                'Calibration uses only five synthetic families and does not establish deployment reliability.',
                'Native snapshot uses fixed v8 with synthetic ports; hybrid/model outcomes are Python simulations.',
                'Fixed full-request gate cannot admit semantic paraphrases absent from its grammar.',
                'Constraint retention measures verbatim request text and blocked filters, not successful filter execution.'])
    if report['frozen_before'] != report['frozen_after']:
        raise AssertionError('Frozen evidence changed')
    output.parent.mkdir(parents=True, exist_ok=True)
    (output.parent / 'disabled-candidate-v9.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(dict(version=VERSION, decision=report['decision'], selected={k:v for k,v in selected.items() if k != 'score'},
        partitions=report['partition_rows'], test=test_counts,
        test_intents={k:dict(macro_f1=v['macro_f1'], unknown_false_accepts=v['unknown_false_accepts'])
                     for k,v in sections['test']['semantic_intents'].items()}, held_out_safe_read_gain=gain,
        exposed=exposed_counts, exposed_safe_read_gain=report['exposed_safe_read_gain']), indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native', type=Path, default=OUTPUT_ROOT / 'native-development.tsv')
    parser.add_argument('--output', type=Path, default=OUTPUT_ROOT / 'development-report.json')
    args = parser.parse_args()
    evaluate(args.native, args.output)
