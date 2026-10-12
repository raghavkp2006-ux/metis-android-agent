"""Current v15 certificate and immutable historical source/data evidence."""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
MANIFEST = HERE / 'development-manifest-v15.json'
HISTORICAL = ('read_safety_v6', 'read_coverage_v7', 'read_coverage_v8', 'classifier_v9', 'read_coverage_v10',
              'classifier_v11', 'candidate_v12', 'candidate_v13', 'candidate_v14', 'human_review_003',
              'human_development_004', 'human_intake_005')


def digest(path, raw=False):
    value = path.read_bytes()
    return hashlib.sha256(value if raw else value.replace(b'\r\n', b'\n')).hexdigest()


def check_prior():
    prior = json.loads((HERE.parent / 'candidate_v14/development-manifest-v14.json').read_text(encoding='utf-8'))
    for name, expected in prior['frozen_hashes'].items():
        if digest(ROOT / name, raw=True) != expected:
            raise ValueError('Frozen historical drift: ' + name)
    for name, expected in prior['hashes'].items():
        if name.startswith('docs/evaluation/phase15/candidate_v14/') and digest(ROOT / name) != expected:
            raise ValueError('Frozen v14 drift: ' + name)
    intake_dir = HERE.parent / 'human_intake_005'
    intake = json.loads((intake_dir / 'intake-manifest.json').read_text(encoding='utf-8'))
    for name, expected in intake['hashes'].items():
        if digest(intake_dir / name) != expected:
            raise ValueError('Original intake-005 drift: ' + name)
    if digest(intake_dir / 'intake.json') != intake['intake_sha256']:
        raise ValueError('Original intake-005 projection drift')
    return prior


def payload():
    check_prior()
    current = {path.relative_to(ROOT).as_posix() for path in (ROOT / 'app/src/main/java').rglob('*.kt')}
    current.update(path.relative_to(ROOT).as_posix() for path in HERE.iterdir()
                   if path.is_file() and path != MANIFEST)
    current.update(('app/build.gradle.kts', 'app/src/test/java/dev/metis/agent/CandidateV15Test.kt',
                    'app/src/androidTest/java/dev/metis/agent/CandidateV15DeviceTest.kt',
                    'app/src/androidTest/assets/phase15-v15.json', '.github/workflows/android.yml',
                    '.github/workflows/classifier-v11.yml', 'MODEL_CARD.md', 'docs/ROADMAP.md', 'docs/AGENT_TRAINING_PLAN.md'))
    frozen = {path.relative_to(ROOT).as_posix() for folder in HISTORICAL for path in (HERE.parent / folder).rglob('*')
              if path.is_file() and '__pycache__' not in path.parts}
    return dict(version='phase15-candidate-v15', ml_enabled=False, ml_packaged=False, human_approved=False,
                fresh_independent_evaluation=False, hashes={name: digest(ROOT / name) for name in sorted(current)},
                frozen_hashes={name: digest(ROOT / name, raw=True) for name in sorted(frozen)})


def check():
    stored = json.loads(MANIFEST.read_text(encoding='utf-8'))
    if stored != payload():
        raise ValueError('Current v15 or frozen artifact certificate drift')
    return stored


if __name__ == '__main__':
    if sys.argv[1:] == ['--write']:
        MANIFEST.write_text(json.dumps(payload(), indent=2) + '\n', encoding='utf-8', newline='\n')
    else:
        assert not sys.argv[1:]
        value = check()
        print(f'V15 integrity passed: {len(value["hashes"])} current; {len(value["frozen_hashes"])} frozen')
