"""Read exposed IRS-003 evidence. Never freeze, approve, train or overwrite it."""
import base64
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
FROZEN = HERE.parent / 'human_review_003/first_evaluation'


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load():
    evidence = json.loads((FROZEN / 'first-reviewed-result.json').read_text(encoding='utf-8'))
    bindings = {'frozen-benchmark.json': 'frozen_benchmark_sha256',
                'frozen-benchmark.tsv': 'native_input_sha256', 'approved-review.csv': 'approved_review_sha256',
                'human-approval.json': 'human_approval_sha256', 'native-rules.tsv': 'native_snapshot_sha256',
                'exposure-receipt.json': 'exposure_receipt_sha256'}
    for file, binding in bindings.items():
        if sha256(FROZEN / file) != evidence[binding]:
            raise ValueError('Frozen evidence drift: ' + file)
    frozen = json.loads((FROZEN / 'frozen-benchmark.json').read_text(encoding='utf-8'))
    return frozen, {file: sha256(FROZEN / file) for file in (*bindings, 'first-reviewed-result.json',
                                                                        'failure-analysis.csv', 'README.md')}


def write_fixture():
    frozen, _ = load()
    lines = ['id\ttext_base64\tdisposition']
    for row in frozen['rows']:
        lines.append('\t'.join((row['id'], base64.b64encode(row['utterance'].encode()).decode(),
                                frozen['annotations'][row['id']]['disposition'])))
    (HERE / 'exposed-regressions.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')


if __name__ == '__main__':
    write_fixture()
