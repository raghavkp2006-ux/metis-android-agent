"""V8 regression report. Exposed data never becomes a fresh independent evaluation."""
import argparse
import base64
import json
from pathlib import Path

from v8_candidate import READ_LABELS, VERSION, resolve, v7
from v8_data import HERE, ROOT, binding_digest, digest
from v7_evaluate import native_entities, snapshot as legacy_snapshot, source_hash
from regressions import FROZEN, load


def snapshot(path, input_path, identifiers):
    rows = legacy_snapshot(path, input_path, identifiers)
    bindings = dict(line[2:].split('=', 1) for line in path.read_text(encoding='utf-8').splitlines()
                    if line.startswith('# '))
    exporter = ROOT / 'app/src/test/java/dev/metis/agent/evaluation/Phase15RulesSnapshot.kt'
    if bindings.get('exporter_sha256') != digest(exporter):
        raise ValueError('Stale native exporter snapshot')
    return rows


def check_manifest():
    manifest = json.loads((HERE / 'development-manifest-v8.json').read_text(encoding='utf-8'))
    if manifest['version'] != VERSION or any(manifest[k] for k in
            ('human_authored', 'human_approved', 'ml_enabled', 'ml_packaged')):
        raise ValueError('Wrong development manifest or unauthorized approval/enabled state')
    for path, expected in manifest['hashes'].items():
        if binding_digest(ROOT / path) != expected:
            raise ValueError('Development source/data drift: ' + path)
    for path, expected in manifest['retained_candidate_hashes'].items():
        if digest(ROOT / path) != expected:
            raise ValueError('Retained candidate changed: ' + path)
    return manifest


def section(rows, annotations, native, historical=None):
    details = []
    for row in rows:
        expected = annotations[row['id']]
        plan = resolve(row['utterance'], source=row['input_source'])
        previous = v7.resolve(row['utterance'], source=row['input_source'])
        actual = native[row['id']]
        prohibited = row['intent'] == 'UNKNOWN' or expected['disposition'] != 'read'
        forced = {label: resolve(row['utterance'], label, row['input_source'])['reads'] for label in READ_LABELS}
        details.append(dict(id=row['id'], expected_intent=row['intent'], expected=expected, prohibited=prohibited,
                            parsed=plan, baseline_v7=previous, forced_label_reads=forced, native=actual,
                            native_query=base64.b64decode(actual['query_base64']).decode(),
                            recorded_v5_label_replay=resolve(row['utterance'], historical[row['id']]['hybrid_intent'])
                            if historical else None))
    forbidden = [d for d in details if d['prohibited']]
    positives = [d for d in details if d['expected']['disposition'] == 'read']
    counts = dict(rows=len(details), expected_read_outcomes=len(positives),
                  candidate_reads=sum(d['parsed']['reads'] for d in details),
                  baseline_v7_reads=sum(d['baseline_v7']['reads'] for d in details),
                  retained_v7_reads=sum(bool(d['baseline_v7']['reads'] and d['parsed']['reads']) for d in details),
                  native_port_calls=sum(int(d['native']['reads']) for d in details),
                  native_proposals=sum(int(d['native']['proposals']) for d in details),
                  native_completed_actions=sum(int(d['native']['completed']) for d in details),
                  forbidden_simulated_reads=sum(d['parsed']['reads'] for d in forbidden),
                  forbidden_native_port_calls=sum(int(d['native']['reads']) for d in forbidden),
                  forced_label_attempts=len(forbidden) * len(READ_LABELS),
                  forced_label_forbidden_reads=sum(sum(d['forced_label_reads'].values()) for d in forbidden),
                  v5_label_replay_forbidden_reads=sum(d['recorded_v5_label_replay']['reads'] for d in forbidden)
                  if historical else 0,
                  exact_dispositions=sum(d['parsed']['disposition'] == d['expected']['disposition'] for d in details),
                  positive_exact_entities=sum(d['parsed']['entities'] == d['expected']['entities'] for d in positives),
                  accepted_positive_exact_entities=sum(d['parsed']['reads'] and
                      d['parsed']['entities'] == d['expected']['entities'] for d in positives),
                  native_admission_matches=sum(d['parsed']['reads'] == int(d['native']['reads']) for d in details),
                  native_accepted_span_matches=sum(d['parsed']['entities'] == native_entities(d['native'])
                                                   for d in details if d['parsed']['reads']),
                  complete_native_queries=sum(d['parsed']['query_text'] == d['native_query'] for d in details),
                  original_requests_retained=sum(d['parsed']['request_text'] == row['utterance']
                                                for d, row in zip(details, rows)))
    # Filters are reported independently from retained text; no advanced extraction claim.
    filter_rows = [d for d in details if 'filters' in d['expected'] and d['expected']['filters']]
    counts['annotated_filter_rows'] = len(filter_rows)
    counts['filter_rows_without_reads'] = sum(not d['parsed']['reads'] for d in filter_rows)
    if any(counts[k] for k in ('forbidden_simulated_reads', 'forbidden_native_port_calls',
                               'forced_label_forbidden_reads', 'v5_label_replay_forbidden_reads',
                               'native_proposals', 'native_completed_actions')):
        raise AssertionError('Forbidden read regression: ' + str(counts))
    if (counts['native_admission_matches'] != len(details) or
            counts['native_accepted_span_matches'] != counts['candidate_reads'] or
            counts['complete_native_queries'] != len(details)):
        raise AssertionError('Python/native read, span or literal-query mismatch: ' + str(counts))
    if counts['retained_v7_reads'] != counts['baseline_v7_reads']:
        raise AssertionError('Previously valid v7 read lost')
    return dict(counts=counts, details=details)


def evaluate(development_native, exposed_native, output, retained_native=None):
    if not output.resolve().is_relative_to((ROOT / 'app/build/reports/phase15/v8').resolve()):
        raise ValueError('V8 reports must stay in ignored app/build/reports/phase15/v8')
    manifest = check_manifest()
    frozen, before = load()
    development = json.loads((HERE / 'development-cases.json').read_text(encoding='utf-8'))
    retained = json.loads((HERE.parent / 'read_coverage_v7/development-cases.json').read_text(encoding='utf-8'))
    original = json.loads((FROZEN / 'first-reviewed-result.json').read_text(encoding='utf-8'))
    historical = {r['id']: r for r in original['reviewed']['details']}
    development_report = section(development, {r['id']: r for r in development},
                                 snapshot(development_native, HERE / 'development-input.tsv', [r['id'] for r in development]))
    exposed_report = section(frozen['rows'], frozen['annotations'],
                             snapshot(exposed_native, FROZEN / 'frozen-benchmark.tsv', [r['id'] for r in frozen['rows']]), historical)
    counts = development_report['counts']
    if (counts['candidate_reads'] != counts['expected_read_outcomes'] or counts['exact_dispositions'] != len(development)
            or counts['positive_exact_entities'] != counts['expected_read_outcomes']):
        raise AssertionError('Development expected outcome/entity failure')
    if exposed_report['counts']['candidate_reads'] <= exposed_report['counts']['baseline_v7_reads']:
        raise AssertionError('Exposed read coverage must improve over retained v7')
    if retained_native is None:
        retained_native = ROOT / 'app/build/reports/phase15/v8/native-retained-v7.tsv'
    retained_report = section(retained, {r['id']: r for r in retained},
                              snapshot(retained_native, HERE.parent / 'read_coverage_v7/development-input.tsv',
                                       [r['id'] for r in retained]))
    original_failures = [r['id'] for r in original['reviewed']['details'] if r['parsed']['reads'] and
                         (r['expected_intent'] == 'UNKNOWN' or r['expected']['disposition'] != 'read')]
    blocked = sum(not d['parsed']['reads'] and not int(d['native']['reads']) and
                  not d['recorded_v5_label_replay']['reads'] for d in exposed_report['details']
                  if d['id'] in original_failures)
    if blocked != len(original_failures):
        raise AssertionError('Original forbidden read replay failure')
    report = dict(version=VERSION, scope='Assistant-authored development and EXPOSED regressions; NOT independent evaluation',
                  decision='NO_GO', adoption_eligible=False, ml_enabled=False, ml_packaged=False,
                  advanced_filters_implemented=False,
                  development=development_report, exposed=exposed_report, retained_v7=retained_report,
                  original_forbidden_reads=len(original_failures), original_forbidden_reads_blocked=blocked,
                  manifest=manifest, manifest_sha256=digest(HERE / 'development-manifest-v8.json'), frozen_evidence=before,
                  native_source_sha256=source_hash(),
                  native_snapshots={str(p): digest(p) for p in (development_native, exposed_native, retained_native)},
                  limits=['No model training, calibration, independent evaluation or human approval.',
                          'Annotation articles may differ from literal query; titles and constraints remain in query text.',
                          'Unsupported constraints clarify before reads; no advanced filtering implemented.',
                          'Native spies are not actual Room/device or Android ML parity evidence.',
                          'V7 manifest remains unchanged and binds its historical shared sources, not current production.'])
    if load()[1] != before:
        raise AssertionError('Frozen evidence changed')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(dict(version=VERSION, decision='NO_GO', development=counts,
                          exposed=exposed_report['counts'], retained_v7=retained_report['counts'],
                          original_forbidden_reads_blocked=blocked), indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--development-native', type=Path, default=ROOT / 'app/build/reports/phase15/v8/native-development.tsv')
    parser.add_argument('--exposed-native', type=Path, default=ROOT / 'app/build/reports/phase15/v8/native-exposed.tsv')
    parser.add_argument('--retained-native', type=Path, default=ROOT / 'app/build/reports/phase15/v8/native-retained-v7.tsv')
    parser.add_argument('--output', type=Path, default=ROOT / 'app/build/reports/phase15/v8/development-regression-report.json')
    args = parser.parse_args()
    evaluate(args.development_native, args.exposed_native, args.output, args.retained_native)
