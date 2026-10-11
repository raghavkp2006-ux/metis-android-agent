"""V7 development and exposed regressions only; no new independent evaluation or approval."""
import argparse
import base64
import csv
import hashlib
import io
import json
from pathlib import Path

from v7_candidate import READ_LABELS, VERSION, resolve, v6
from v7_data import HERE, ROOT, binding_digest, digest
from regressions import FROZEN, load


def check_manifest():
    manifest = json.loads((HERE / 'development-manifest-v7.json').read_text(encoding='utf-8'))
    if manifest['version'] != VERSION or manifest['human_approved'] or manifest['ml_enabled'] or manifest['ml_packaged']:
        raise ValueError('Wrong development manifest or unauthorized enabled/approved state')
    for path, expected in manifest['hashes'].items():
        if binding_digest(ROOT / path) != expected:
            raise ValueError('Development source/data drift: ' + path)
    return manifest


def source_hash():
    files = list((ROOT / 'app/src/main/java/dev/metis/agent/domain/agent').glob('*.kt'))
    files += [ROOT / 'app/src/main/java/dev/metis/agent/domain/planner/PlannerWindow.kt']
    result = hashlib.sha256()
    for path in sorted(files, key=lambda p: p.relative_to(ROOT).as_posix()):
        result.update(path.relative_to(ROOT).as_posix().encode())
        result.update(path.read_bytes())
    return result.hexdigest()


def snapshot(path, input_path, identifiers):
    lines = path.read_text(encoding='utf-8').splitlines()
    bindings = dict(line[2:].split('=', 1) for line in lines if line.startswith('# '))
    if bindings.get('input_sha256') != digest(input_path):
        raise ValueError('Native input drift')
    if bindings.get('source_sha256') != source_hash():
        raise ValueError('Stale native source snapshot')
    rows = list(csv.DictReader(io.StringIO('\n'.join(line for line in lines if not line.startswith('#'))), delimiter='\t'))
    by_id = {row['id']: row for row in rows}
    if len(by_id) != len(rows) or set(by_id) != set(identifiers):
        raise ValueError('Missing, repeated or unexpected native rows')
    return by_id


def native_entities(row):
    result = []
    for span in filter(None, row['entities'].split(';')):
        kind, start, end, raw, _ = span.split(':')
        result.append(dict(type=kind, startOffset=int(start), endOffset=int(end),
                           rawValue=base64.b64decode(raw).decode()))
    return result


def section(rows, annotations, native, historical=None):
    details = []
    for row in rows:
        expected = annotations[row['id']]
        plan = resolve(row['utterance'], source=row['input_source'])
        prohibited = row['intent'] == 'UNKNOWN' or expected['disposition'] != 'read'
        forced = {label: resolve(row['utterance'], label, row['input_source'])['reads'] for label in READ_LABELS}
        details.append(dict(id=row['id'], expected_intent=row['intent'], expected=expected, prohibited=prohibited,
                            parsed=plan, baseline_v6=v6.resolve(row['utterance'], source=row['input_source']),
                            forced_label_reads=forced, native=native[row['id']],
                            recorded_v5_label_replay=resolve(row['utterance'], historical[row['id']]['hybrid_intent'])
                            if historical else None))
    forbidden = [d for d in details if d['prohibited']]
    positives = [d for d in details if d['expected']['disposition'] == 'read']
    counts = dict(rows=len(details), expected_read_outcomes=len(positives),
                  candidate_reads=sum(d['parsed']['reads'] for d in details),
                  baseline_v6_reads=sum(d['baseline_v6']['reads'] for d in details),
                  native_port_calls=sum(int(d['native']['reads']) for d in details),
                  forbidden_simulated_reads=sum(d['parsed']['reads'] for d in forbidden),
                  forbidden_native_port_calls=sum(int(d['native']['reads']) for d in forbidden),
                  forced_label_attempts=len(forbidden) * len(READ_LABELS),
                  forced_label_forbidden_reads=sum(sum(d['forced_label_reads'].values()) for d in forbidden),
                  v5_label_replay_forbidden_reads=sum(d['recorded_v5_label_replay']['reads'] for d in forbidden)
                  if historical else 0,
                  exact_dispositions=sum(d['parsed']['disposition'] == d['expected']['disposition'] for d in details),
                  positive_exact_entities=sum(d['parsed']['entities'] == d['expected']['entities'] for d in positives),
                  native_admission_matches=sum(d['parsed']['reads'] == int(d['native']['reads']) for d in details),
                  native_accepted_span_matches=sum(d['parsed']['entities'] == native_entities(d['native'])
                                                   for d in details if d['parsed']['reads']),
                  original_requests_retained=sum(d['parsed']['request_text'] == row['utterance']
                                                for d, row in zip(details, rows)))
    if any(counts[k] for k in ('forbidden_simulated_reads', 'forbidden_native_port_calls',
                               'forced_label_forbidden_reads', 'v5_label_replay_forbidden_reads')):
        raise AssertionError('Forbidden read regression: ' + str(counts))
    if counts['native_admission_matches'] != len(details) or counts['native_accepted_span_matches'] != counts['candidate_reads']:
        raise AssertionError('Python/native read admission or accepted-span mismatch')
    return dict(counts=counts, details=details)


def evaluate(development_native, exposed_native, output):
    if not output.resolve().is_relative_to((ROOT / 'app/build/reports/phase15/v7').resolve()):
        raise ValueError('V7 reports must stay in ignored app/build/reports/phase15/v7')
    manifest = check_manifest()
    frozen, before = load()
    development = json.loads((HERE / 'development-cases.json').read_text(encoding='utf-8'))
    dev_annotations = {r['id']: dict(disposition=r['disposition'], entities=r['entities']) for r in development}
    dev_native = snapshot(development_native, HERE / 'development-input.tsv', [r['id'] for r in development])
    exp_native = snapshot(exposed_native, FROZEN / 'frozen-benchmark.tsv', [r['id'] for r in frozen['rows']])
    original = json.loads((FROZEN / 'first-reviewed-result.json').read_text(encoding='utf-8'))
    historical = {r['id']: r for r in original['reviewed']['details']}
    report = dict(version=VERSION, scope='Assistant-authored development plus EXPOSED regressions; NOT independent evaluation',
                  decision='NO_GO', adoption_eligible=False, ml_enabled=False, ml_packaged=False,
                  development=section(development, dev_annotations, dev_native),
                  exposed=section(frozen['rows'], frozen['annotations'], exp_native, historical),
                  manifest=manifest, manifest_sha256=digest(HERE / 'development-manifest-v7.json'), frozen_evidence=before,
                  native_source_sha256=source_hash(),
                  native_snapshots={str(development_native): digest(development_native), str(exposed_native): digest(exposed_native)},
                  limits=['Development cases were authored by the assistant; related examples share scenario families.',
                          'No fitting, model selection, calibration, new human approval or fresh benchmark scoring.',
                          'Native validation uses synthetic ports; read/spans agreement is not Android model parity or actual adapter evidence.',
                          'Unsupported filters retain the full request and clarify; broad language/entity quality gates remain unmet.'])
    if load()[1] != before:
        raise AssertionError('Frozen evidence changed')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(json.dumps(dict(version=VERSION, decision='NO_GO', development=report['development']['counts'],
                          exposed=report['exposed']['counts']), indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--development-native', type=Path, default=ROOT / 'app/build/reports/phase15/v7/native-development.tsv')
    parser.add_argument('--exposed-native', type=Path, default=ROOT / 'app/build/reports/phase15/v7/native-exposed.tsv')
    parser.add_argument('--output', type=Path, default=ROOT / 'app/build/reports/phase15/v7/development-regression-report.json')
    args = parser.parse_args()
    evaluate(args.development_native, args.exposed_native, args.output)
