"""Current-production v10 regression evidence; never a fresh independent evaluation."""
import argparse
import base64
import csv
import hashlib
import io
import json
import sys
from pathlib import Path

from v10_candidate import READ_LABELS, VERSION, resolve
from v10_data import HERE, ROOT, digest

sys.path.insert(0, str(HERE.parent / 'read_coverage_v8'))
from v8_evaluate import native_entities, source_hash
from v7_evaluate import snapshot as historical_snapshot
from v8_candidate import resolve as v8_resolve, v7
from regressions import load, FROZEN


def snapshot(path, input_path, identifiers):
    rows = historical_snapshot(path, input_path, identifiers)
    bindings = dict(line[2:].split('=', 1) for line in path.read_text(encoding='utf-8').splitlines()
                    if line.startswith('# '))
    exporter = ROOT / 'app/src/test/java/dev/metis/agent/evaluation/Phase15RulesSnapshot.kt'
    if bindings.get('exporter_sha256') != digest(exporter, False): raise ValueError('Stale native exporter snapshot')
    return rows


def check_manifest():
    manifest = json.loads((HERE / 'development-manifest-v10.json').read_text(encoding='utf-8'))
    if manifest['version'] != VERSION or any(manifest[k] for k in ('human_approved', 'ml_enabled', 'ml_packaged')):
        raise ValueError('Unauthorized approval or ML state')
    for path, expected in manifest['hashes'].items():
        if digest(ROOT / path) != expected: raise ValueError('Current source/data drift: ' + path)
    for path, expected in manifest['retained_hashes'].items():
        if digest(ROOT / path, False) != expected: raise ValueError('Historical candidate/evidence changed: ' + path)
    return manifest


def section(rows, native):
    details = []
    for row in rows:
        parsed = resolve(row['utterance'], source=row['input_source'])
        actual = native[row['id']]
        forced = {label: resolve(row['utterance'], label, row['input_source'])['reads'] for label in READ_LABELS}
        details.append(dict(id=row['id'], expected_intent=row['intent'], expected_disposition=row['disposition'],
            expected_entities=row['entities'], expected_query=row['query_text'], requested_constraints=row.get('filters', {}),
            parsed=parsed, native=actual, forced_label_reads=forced))
    positive = [d for d in details if d['expected_disposition'] == 'read']
    forbidden = [d for d in details if d['expected_disposition'] != 'read']
    counts = dict(rows=len(details), labelled_valid_reads=len(positive), python_reads=sum(d['parsed']['reads'] for d in details),
        historical_v7_valid_reads=sum(r['disposition'] == 'read' and v7.resolve(r['utterance'], source=r['input_source'])['reads'] for r in rows),
        historical_v8_valid_reads=sum(r['disposition'] == 'read' and v8_resolve(r['utterance'], source=r['input_source'])['reads'] for r in rows),
        native_reads=sum(int(d['native']['reads']) for d in details),
        forbidden_python_reads=sum(d['parsed']['reads'] for d in forbidden),
        forbidden_native_reads=sum(int(d['native']['reads']) for d in forbidden),
        forbidden_native_context_calls=sum(int(d['native']['contexts']) for d in forbidden),
        forced_label_attempts=len(forbidden)*len(READ_LABELS),
        forced_label_forbidden_reads=sum(sum(d['forced_label_reads'].values()) for d in forbidden),
        exact_python_positive_entities=sum(d['parsed']['entities'] == d['expected_entities'] for d in positive),
        exact_native_positive_entities=sum(native_entities(d['native']) == d['expected_entities'] for d in positive),
        exact_python_complete_queries=sum(d['parsed']['query_text'] == d['expected_query'] for d in positive),
        exact_native_complete_queries=sum(base64.b64decode(d['native']['query_base64']).decode() == d['expected_query'] for d in positive),
        exact_python_dispositions=sum(d['parsed']['disposition'] == d['expected_disposition'] for d in details),
        exact_native_dispositions=sum(('read' if int(d['native']['reads']) else
            'unsupported' if d['native']['status'] == 'UNSUPPORTED' else
            'reject' if d['native']['status'] == 'DENIED' else 'clarify') == d['expected_disposition'] for d in details),
        annotated_constraint_rows=sum(bool(d['requested_constraints']) for d in details),
        nonread_constraint_rows_blocked=sum(bool(d['requested_constraints']) and not int(d['native']['reads'])
            for d in forbidden),
        native_follow_ups=sum(d['native']['status'] == 'FOLLOW_UP' for d in details),
        original_requests_retained=sum(d['parsed']['request_text'] == r['utterance'] for d,r in zip(details, rows)),
        native_follow_up_constraints_retained=sum(d['native']['status'] != 'FOLLOW_UP' or
            r['utterance'] in base64.b64decode(d['native']['message_base64']).decode() for d,r in zip(details, rows)),
        native_proposals=sum(int(d['native']['proposals']) for d in details),
        native_completed_actions=sum(int(d['native']['completed']) for d in details))
    for key in ('forbidden_python_reads', 'forbidden_native_reads', 'forbidden_native_context_calls',
                'forced_label_forbidden_reads', 'native_proposals', 'native_completed_actions'):
        if counts[key]: raise AssertionError(f'{key}: {counts[key]}')
    for key in ('python_reads','native_reads','exact_python_positive_entities','exact_native_positive_entities',
                'exact_python_complete_queries','exact_native_complete_queries'):
        if counts[key] != len(positive): raise AssertionError(f'{key}: {counts}')
    if counts['original_requests_retained'] != len(rows) or counts['native_follow_up_constraints_retained'] != len(rows):
        raise AssertionError('Requested constraints lost')
    # Read counts, positive labels, annotations and complete subjects must agree.
    # Negative classification/disposition differences are measured separately.
    for d in details:
        if int(d['native']['reads']) != d['parsed']['reads']: raise AssertionError('Admission drift: ' + d['id'])
        if d['parsed']['reads'] and d['native']['intent'] != d['expected_intent']: raise AssertionError('Intent drift')
    return dict(counts=counts, details=details)


def evaluate(native_path, output):
    if not output.resolve().is_relative_to((ROOT / 'app/build/reports/phase15/v10').resolve()):
        raise ValueError('V10 reports must stay in ignored build output')
    manifest = check_manifest()
    frozen, before = load()
    rows = json.loads((HERE / 'regression-expectations.json').read_text(encoding='utf-8'))
    native = snapshot(native_path, HERE / 'regression-input.tsv', [r['id'] for r in rows])
    sections = {name: section([r for r in rows if r['section'] == name], native)
                for name in ('development','exposed','v9','v8','v7')}
    if sections['exposed']['counts']['native_reads'] != 23 or sections['v9']['counts']['native_reads'] != 299:
        raise AssertionError('Coverage target missed')
    if sections['exposed']['counts']['native_reads'] <= sections['exposed']['counts']['historical_v7_valid_reads']:
        raise AssertionError('No improvement over v7 exposed coverage')
    historical = json.loads((FROZEN / 'first-reviewed-result.json').read_text(encoding='utf-8'))
    original = [r['id'] for r in historical['reviewed']['details'] if r['parsed']['reads'] and
                (r['expected_intent'] == 'UNKNOWN' or r['expected']['disposition'] != 'read')]
    assert len(original) == 14 and all(not int(native[i]['reads']) and not int(native[i]['contexts']) for i in original)
    report = dict(version=VERSION, scope='EXPOSED regressions and assistant-authored development; NOT independent evaluation',
        implementation='Deterministic complete grammar; no model fitting, retraining or ML enablement',
        decision='ML_NO_GO', adoption_eligible=False, ml_enabled=False, ml_packaged=False,
        original_forbidden_reads=14, original_forbidden_reads_blocked=14,
        sections=sections, manifest=manifest, frozen_evidence=before, native_source_sha256=source_hash(),
        native_snapshot_sha256=digest(native_path),
        limits=['Repository instrumentation compiled; execution/device measurements reported separately.',
            'Literal saved-record retrieval can honestly return no match; no factual identity resolution.',
            'Unsupported filters preserved verbatim in clarification; no advanced filtering claim.',
            'Fresh human evaluation, representative calibration and Android runtime gates remain separate.'])
    if load()[1] != before: raise AssertionError('Frozen evidence changed')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps({name:value['counts'] for name,value in sections.items()}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native', type=Path, default=ROOT / 'app/build/reports/phase15/v10/native-regressions.tsv')
    parser.add_argument('--output', type=Path, default=ROOT / 'app/build/reports/phase15/v10/development-regression-report.json')
    args = parser.parse_args()
    evaluate(args.native, args.output)
