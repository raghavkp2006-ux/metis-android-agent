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
            'independent_reviewed_evaluation': dict(status='pending', required='Fresh approved 125-query benchmark, frozen and scored once'),
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
    raise SystemExit(int(any(c['status'] == 'failed' for c in report['checks'].values())))
