"""Read-only audit of frozen v5 evidence. Never grants deployment approval."""
import argparse
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
V5 = HERE.parent / 'read_queries_v5'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def reviewed_gate(reference):
    folder = HERE.parent / 'human_review_003/first_evaluation'
    path = folder / 'first-reviewed-result.json'
    if not path.is_file():
        return dict(status='pending', required='Fresh approved benchmark with at least 25 examples per label, frozen and scored once')
    try:
        evidence = json.loads(path.read_text(encoding='utf-8'))
        frozen = json.loads((folder/'frozen-benchmark.json').read_text(encoding='utf-8'))
        approval = json.loads((folder/'human-approval.json').read_text(encoding='utf-8'))
        receipt = json.loads((folder/'exposure-receipt.json').read_text(encoding='utf-8'))
        for name, key in [('human-approval.json','human_approval_sha256'),
                          ('approved-review.csv','approved_review_sha256'),
                          ('frozen-benchmark.json','frozen_benchmark_sha256'),
                          ('frozen-benchmark.tsv','native_input_sha256'),
                          ('native-rules.tsv','native_snapshot_sha256'),
                          ('exposure-receipt.json','exposure_receipt_sha256')]:
            if sha(folder/name) != evidence[key]:
                raise ValueError(f'Reviewed evidence hash mismatch: {name}')
        if (evidence['candidate_manifest_sha256'] != sha(V5/'manifest-v5.json') or
                evidence['candidate'] != reference['candidate'] or
                evidence['export_sha256'] != reference['artifact']['sha256'] or
                evidence['reference_report_sha256'] != sha(V5/'results-v5.json')):
            raise ValueError('Reviewed evidence belongs to another candidate/reference')
        if (approval['status'] != 'HUMAN_APPROVED' or
                approval['approved_review_sha256'] != evidence['approved_review_sha256'] or
                frozen['review_sha256'] != evidence['approved_review_sha256'] or
                frozen['input_sha256'] != evidence['native_input_sha256'] or
                receipt['benchmark_sha256'] != evidence['frozen_benchmark_sha256']):
            raise ValueError('Reviewed approval/freeze/receipt binding mismatch')
        reviewed = evidence['reviewed']
        details = reviewed['details']
        if len(details) != len(frozen['rows']):
            raise ValueError('Reviewed evidence row-count mismatch')
        for row, detail in zip(frozen['rows'], details):
            if (row['id'] != detail['id'] or row['utterance'] != detail['utterance'] or
                    row['intent'] != detail['expected_intent'] or
                    frozen['annotations'][row['id']] != detail['expected']):
                raise ValueError('Reviewed evidence input/annotation mismatch')
        behavior = reviewed['behavior']
        passed = (reviewed['hybrid']['macro_f1'] >= .90 and not behavior['unsafe_simulated_reads']
                  and behavior['correct_dispositions'] == behavior['annotated_dispositions']
                  and all(d['parsed']['entities'] == d['expected']['entities'] and
                          d['parsed']['filters'] == d['expected']['filters'] for d in details))
        return dict(status='passed' if passed else 'failed', evidence_sha256=sha(path),
                    rows=len(details), macro_f1=reviewed['hybrid']['macro_f1'],
                    unsafe_simulated_reads=behavior['unsafe_simulated_reads'],
                    limit='Bound first-score evidence; no inference rerun. User-attested review/freshness. Subsequent candidates need fresh evaluation.')
    except (OSError, ValueError, KeyError, TypeError) as error:
        return dict(status='invalid_evidence', error=str(error))


def audit():
    manifest_path = V5 / 'manifest-v5.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    result_path = V5 / 'results-v5.json'
    result = json.loads(result_path.read_text(encoding='utf-8'))
    failures = []
    for group in ('sources', 'hashes', 'regressions'):
        for name, expected in manifest[group].items():
            path = (V5 if group == 'hashes' else ROOT) / name
            if not path.is_file() or sha(path) != expected:
                failures.append(f'{group}: {name}')
    if result['provenance']['manifest_sha256'] != sha(manifest_path):
        failures.append('Reference report does not bind to current manifest')
    artifact_path = ROOT / 'app/build/reports/phase15/read-query-v5-development-model.json'
    artifact_errors = []
    if not artifact_path.is_file():
        artifact_errors.append('Development export missing; reproduce using v5 README')
    else:
        if sha(artifact_path) != result['artifact']['sha256']:
            artifact_errors.append('Export SHA differs from frozen report')
        exported = json.loads(artifact_path.read_text(encoding='utf-8'))
        if exported.get('enabled') is not False or result['artifact']['packaged'] is not False:
            artifact_errors.append('Disabled/unpackaged evidence missing')
    return dict(version='v5-gate-audit-v1', decision='NO_GO',
        reference_sha256=sha(result_path), candidate_manifest_sha256=sha(manifest_path),
        checks={
            'frozen_evidence_integrity': dict(status='failed' if failures else 'passed', errors=failures),
            'development_export_integrity': dict(status='failed' if artifact_errors else 'passed', errors=artifact_errors),
            'python_export_parity': dict(status='recorded', evidence=result['export_parity'],
                                        limit='Recorded Python result; not rerun or Android parity'),
            'development_quality': dict(status='needs_improvement',
                evidence=result['development']['behavior'],
                unknown_false_accepts=result['development']['hybrid']['unknown_false_accepts']),
            'independent_reviewed_evaluation': reviewed_gate(result),
            'representative_calibration': dict(status='pending', required='Separate representative calibration data, never evaluation data'),
            'real_repository_policy_outcomes': dict(status='pending', required='Actual adapter/repository policy evidence after prerequisite gates'),
            'android_parity_and_devices': dict(status='pending', required='Android parity, physical-device behavior/latency/memory evidence after prerequisite gates')},
        limit='Audits existing development evidence only; no gate is automatically approved. Legacy scope exception remains in reference report.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = audit()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
    integrity_failed = any(report['checks'][key]['status'] == 'failed' for key in
                           ('frozen_evidence_integrity', 'development_export_integrity'))
    invalid_review = report['checks']['independent_reviewed_evaluation']['status'] == 'invalid_evidence'
    raise SystemExit(int(integrity_failed or invalid_review))
