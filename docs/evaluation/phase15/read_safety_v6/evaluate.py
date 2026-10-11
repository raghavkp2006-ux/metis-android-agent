"""V6 exposed regression report only. Does not run or alter the frozen v5 evaluation."""
import argparse
import csv
import io
import json
from pathlib import Path

from candidate import READ_LABELS, VERSION, resolve
from regressions import FROZEN, HERE, ROOT, load, sha256


def native_rows(path, frozen):
    lines = path.read_text(encoding='utf-8').splitlines()
    from_source = dict(line[2:].split('=', 1) for line in lines if line.startswith('# '))
    if from_source.get('input_sha256') != sha256(FROZEN / 'frozen-benchmark.tsv'):
        raise ValueError('Native input drift')
    # Match the production snapshot's source binding; stale snapshots cannot pass.
    import hashlib
    digest = hashlib.sha256()
    sources = list((ROOT / 'app/src/main/java/dev/metis/agent/domain/agent').glob('*.kt'))
    sources += [ROOT / 'app/src/main/java/dev/metis/agent/domain/planner/PlannerWindow.kt']
    for source in sorted(sources, key=lambda p: p.relative_to(ROOT).as_posix()):
        digest.update(source.relative_to(ROOT).as_posix().encode())
        digest.update(source.read_bytes())
    if from_source.get('source_sha256') != digest.hexdigest():
        raise ValueError('Stale native source snapshot')
    rows = list(csv.DictReader(io.StringIO('\n'.join(line for line in lines if not line.startswith('#'))), delimiter='\t'))
    result = {row['id']: row for row in rows}
    if len(result) != len(rows) or set(result) != {row['id'] for row in frozen['rows']}:
        raise ValueError('Missing, repeated or unexpected native rows')
    return result, from_source


def evaluate(native_path, output):
    # Prevent accidentally targeting frozen approval/results or checked-in source.
    output = output.resolve()
    if not output.is_relative_to((ROOT / 'app/build/reports/phase15').resolve()):
        raise ValueError('V6 reports must stay in ignored app/build/reports/phase15')
    frozen, bindings = load()
    native, source_binding = native_rows(native_path, frozen)
    original = json.loads((FROZEN / 'first-reviewed-result.json').read_text(encoding='utf-8'))
    old = {row['id']: row for row in original['reviewed']['details']}
    details = []
    for row in frozen['rows']:
        expected = frozen['annotations'][row['id']]
        parsed = resolve(row['utterance'], source=row['input_source'])
        replay = resolve(row['utterance'], old[row['id']]['hybrid_intent'], row['input_source'])
        forced = {label: resolve(row['utterance'], label, row['input_source'])['reads'] for label in READ_LABELS}
        forbidden = row['intent'] == 'UNKNOWN' or expected['disposition'] != 'read'
        details.append(dict(id=row['id'], expected_intent=row['intent'], expected=expected,
                            forbidden=forbidden, parsed=parsed, v5_label_replay=replay,
                            forced_label_reads=forced, native=native[row['id']]))
    forbidden = [d for d in details if d['forbidden']]
    original_failures = [d for d in original['reviewed']['details'] if d['parsed']['reads'] and
                         (d['expected_intent'] == 'UNKNOWN' or d['expected']['disposition'] != 'read')]
    counts = dict(rows=len(details), prohibited_outcomes=len(forbidden),
                  original_forbidden_reads=len(original_failures),
                  candidate_forbidden_simulated_reads=sum(d['parsed']['reads'] for d in forbidden),
                  v5_label_replay_forbidden_reads=sum(d['v5_label_replay']['reads'] for d in forbidden),
                  forced_label_attempts=len(forbidden) * len(READ_LABELS),
                  forced_label_forbidden_reads=sum(sum(d['forced_label_reads'].values()) for d in forbidden),
                  native_forbidden_port_calls=sum(int(d['native']['reads']) for d in forbidden),
                  candidate_reads=sum(d['parsed']['reads'] for d in details),
                  approved_read_outcomes=sum(d['expected']['disposition'] == 'read' for d in details),
                  native_port_calls=sum(int(d['native']['reads']) for d in details),
                  exact_dispositions=sum(d['parsed']['disposition'] == d['expected']['disposition'] for d in details),
                  exact_entities=sum(d['parsed']['entities'] == d['expected']['entities'] for d in details),
                  original_requests_retained=sum(d['parsed']['request_text'] == row['utterance']
                                                for d, row in zip(details, frozen['rows'])))
    report = dict(version=VERSION, scope='Exposed regression development only; NOT fresh independent evaluation',
                  ml_enabled=False, ml_packaged=False, decision='NO_GO', adoption_eligible=False,
                  candidate_source_sha256=sha256(HERE / 'candidate.py'), frozen_evidence=bindings,
                  native_snapshot_sha256=sha256(native_path), native_source_binding=source_binding,
                  counts=counts, details=details,
                  limits=['Conservative full-form read admission; broader wording may clarify.',
                          'Advanced constraints are retained verbatim, not implemented as structured filters.',
                          'No new classifier, training, selection or calibration; v5 labels are replayed from recorded evidence.',
                          'Native port spies count access attempts, not actual Room or device evidence.',
                          'Fresh reviewed quality, representative calibration, actual adapter and device gates remain unmet.'])
    safety_keys = ('candidate_forbidden_simulated_reads', 'v5_label_replay_forbidden_reads',
                   'forced_label_forbidden_reads', 'native_forbidden_port_calls')
    if any(counts[key] for key in safety_keys):
        raise AssertionError('Forbidden read regression: ' + str(counts))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    _, after = load()
    if bindings != after:
        raise AssertionError('Frozen evidence was changed')
    print(json.dumps(dict(version=VERSION, scope=report['scope'], counts=counts, decision='NO_GO'), indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-snapshot', type=Path,
                        default=ROOT / 'app/build/reports/phase15/v6/native-exposed.tsv')
    parser.add_argument('--output', type=Path, default=ROOT / 'app/build/reports/phase15/v6/exposed-regression-report.json')
    args = parser.parse_args()
    evaluate(args.native_snapshot, args.output)
