"""Current-source certificate plus byte-preserved historical evidence and exposed intake."""
import hashlib
import json
import sys
from data import HERE, ROOT

HISTORICAL = ('read_safety_v6', 'read_coverage_v7', 'read_coverage_v8', 'classifier_v9',
              'read_coverage_v10', 'classifier_v11', 'candidate_v12', 'candidate_v13',
              'human_review_003', 'human_development_004')


def digest(path, raw=False):
    content = path.read_bytes()
    return hashlib.sha256(content if raw else content.replace(b'\r\n', b'\n')).hexdigest()


def inventory():
    prior = json.loads((HERE.parent / 'candidate_v13/development-manifest-v13.json').read_text(encoding='utf-8'))
    current = {name for name in prior['hashes'] if not name.startswith('docs/evaluation/phase15/candidate_v13/')}
    current.update(path.relative_to(ROOT).as_posix() for path in
                   (ROOT / 'app/src/main/java/dev/metis/agent/domain/agent').glob('*.kt'))
    current.update(('app/src/test/java/dev/metis/agent/CandidateV14Test.kt',
                    'app/src/main/java/dev/metis/agent/domain/storage/MemorySearch.kt',
                    'app/src/androidTest/java/dev/metis/agent/CandidateV14DeviceTest.kt',
                    'app/src/androidTest/assets/phase15-v14.tsv'))
    current.update(path.relative_to(ROOT).as_posix() for path in HERE.iterdir()
                   if path.is_file() and path.name != 'development-manifest-v14.json')
    frozen = {path.relative_to(ROOT).as_posix() for directory in HISTORICAL
              for path in (HERE.parent / directory).rglob('*')
              if path.is_file() and '__pycache__' not in path.parts}
    return sorted(current), sorted(frozen)


def check_prior():
    prior = json.loads((HERE.parent / 'candidate_v13/development-manifest-v13.json').read_text(encoding='utf-8'))
    for name, expected in prior['frozen_hashes'].items():
        if digest(ROOT / name, raw=True) != expected:
            raise ValueError('Frozen v6-v12/first evaluation drift: ' + name)
    intake = json.loads((HERE.parent / 'human_development_004/intake-manifest.json').read_text(encoding='utf-8'))
    for name, expected in intake['hashes'].items():
        if digest(ROOT / name, raw=name.endswith('/attachment.txt')) != expected:
            raise ValueError('Exposed intake drift: ' + name)


def payload():
    check_prior()
    current, frozen = inventory()
    return dict(version='phase15-candidate-v14', ml_enabled=False, ml_packaged=False,
                human_approved=False, fresh_independent_evaluation=False,
                hashes={name: digest(ROOT / name) for name in current},
                frozen_hashes={name: digest(ROOT / name, raw=True) for name in frozen})


def check():
    actual = payload()
    stored = json.loads((HERE / 'development-manifest-v14.json').read_text(encoding='utf-8'))
    if stored != actual:
        raise ValueError('Candidate source/data/protocol certificate drift')
    return stored


if __name__ == '__main__':
    if sys.argv[1:] == ['--write']:
        (HERE / 'development-manifest-v14.json').write_text(json.dumps(payload(), indent=2) + '\n',
                                                          encoding='utf-8', newline='\n')
    else:
        assert not sys.argv[1:]
        value = check()
        print(f'V14 integrity passed: {len(value["hashes"])} current and {len(value["frozen_hashes"])} frozen files')
