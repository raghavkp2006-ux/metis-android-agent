"""Seal explicitly reviewed development labels; this never fits a classifier."""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
FILES = ('review-projection.json', 'review-decisions.json', 'completion-proposal.json',
         'completion-proposal.md', 'completion-approval.json', 'family-proposal.json',
         'family-proposal.md', 'family-approval.json', 'protocol.json')


def payload():
    data = json.loads((HERE / 'review-projection.json').read_text(encoding='utf-8'))
    assert data['development_annotations_frozen']
    assert len(data['rows']) == 365
    assert all(r['human_approved'] and r['family_reviewed'] and all(r['reviewed_fields'].values()) for r in data['rows'])
    return dict(version='v15-development-annotation-freeze-001', requests=365, families=24, seed=1515,
                partitions={'train': 255, 'development_validation': 55, 'development_test': 55},
                author='requesting user; assistant annotation drafts explicitly reviewed by that same user',
                independent_evaluation=False, ml_enabled=False, fitting_started=False,
                fitting_prerequisite='Separate 100 calibration and 50 selection human requests, reviewed and frozen before predictions',
                sample_limitation='Development test has only two calendar requests; development evidence is exposed and same-person reviewed',
                hashes={name: hashlib.sha256((HERE / name).read_bytes()).hexdigest() for name in FILES})


def check():
    assert json.loads((HERE / 'annotation-freeze.json').read_text(encoding='utf-8')) == payload(), 'Frozen development-label drift'


if __name__ == '__main__':
    target = HERE / 'annotation-freeze.json'
    if target.exists():
        check()
    else:
        target.write_text(json.dumps(payload(), indent=2) + '\n', encoding='utf-8', newline='\n')
    print('Development annotations frozen: 365 reviewed requests; 24 whole families; fitting not started')
