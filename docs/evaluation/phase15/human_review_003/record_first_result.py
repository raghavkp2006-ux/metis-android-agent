"""Preserve a bound first-score record without rerunning or tuning inference."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def record():
    saved = HERE/'first_evaluation'
    generated = ROOT/'app/build/reports/phase15/irs003-first'
    target = saved/'first-reviewed-result.json'
    copies = [saved/'native-rules.tsv', saved/'exposure-receipt.json', target]
    if any(path.exists() for path in copies):
        raise ValueError('First result evidence already preserved; never overwrite it')
    frozen_path = saved/'frozen-benchmark.json'
    frozen = json.loads(frozen_path.read_text(encoding='utf-8'))
    full_path = generated/'full-first-results.json'
    full = json.loads(full_path.read_text(encoding='utf-8'))
    reference_path = ROOT/'docs/evaluation/phase15/read_queries_v5/results-v5.json'
    reference = json.loads(reference_path.read_text(encoding='utf-8'))
    native_path = generated/'native-rules.tsv'
    receipt_path = generated/f'v5-review-consumed-{sha(frozen_path)}.json'
    receipt = json.loads(receipt_path.read_text(encoding='utf-8'))
    if receipt['benchmark_sha256'] != sha(frozen_path):
        raise ValueError('Receipt benchmark binding mismatch')
    if frozen['review_sha256'] != sha(saved/'approved-review.csv'):
        raise ValueError('Approved review changed')
    if frozen['input_sha256'] != sha(saved/'frozen-benchmark.tsv'):
        raise ValueError('Native input changed')
    if full['provenance']['manifest_sha256'] != frozen['candidate_manifest_sha256']:
        raise ValueError('Candidate binding mismatch')
    reviewed = full['reviewed']
    if [r['id'] for r in frozen['rows']] != [d['id'] for d in reviewed['details']]:
        raise ValueError('Reviewed rows differ from frozen input')
    for row, detail in zip(frozen['rows'], reviewed['details']):
        if (row['utterance'] != detail['utterance'] or row['intent'] != detail['expected_intent'] or
                detail['expected'] != frozen['annotations'][row['id']]):
            raise ValueError('First result truth/input mismatch')
    artifact = generated/'read-query-v5-development-model.json'
    if sha(artifact) != full['artifact']['sha256']:
        raise ValueError('First-score artifact differs from reported hash')
    # Preserve the first score even if reproduction differs, marking its validity.
    same_candidate = full['candidate'] == reference['candidate']
    same_artifact = full['artifact']['sha256'] == reference['artifact']['sha256']
    evidence = dict(version='irs003-first-score-evidence-v1', rows=len(frozen['rows']),
        human_approval_sha256=sha(saved/'human-approval.json'),
        approved_review_sha256=sha(saved/'approved-review.csv'), frozen_benchmark_sha256=sha(frozen_path),
        native_input_sha256=sha(saved/'frozen-benchmark.tsv'), native_snapshot_sha256=sha(native_path),
        exposure_receipt_sha256=sha(receipt_path), full_first_report_sha256=sha(full_path),
        reference_report_sha256=sha(reference_path), candidate_manifest_sha256=frozen['candidate_manifest_sha256'],
        export_sha256=sha(artifact), same_candidate_parameters_as_reference=same_candidate,
        same_export_as_reference=same_artifact, candidate=full['candidate'], artifact=full['artifact'],
        reviewed=reviewed, gates=full['gates'], decision=full['decision'], adoption_eligible=full['adoption_eligible'],
        limit=full['reviewed_limit']+'; these queries are now exposed and cannot be fresh evaluation for a subsequent candidate')
    (saved/'native-rules.tsv').write_bytes(native_path.read_bytes())
    (saved/'exposure-receipt.json').write_bytes(receipt_path.read_bytes())
    target.write_text(json.dumps(evidence, indent=2, ensure_ascii=False)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps(dict(rows=evidence['rows'], reviewed_metrics=reviewed['hybrid'],
        behavior=reviewed['behavior'], gates=evidence['gates'], same_candidate=same_candidate,
        same_export=same_artifact, first_result_sha256=sha(target)), indent=2))


if __name__ == '__main__':
    record()
