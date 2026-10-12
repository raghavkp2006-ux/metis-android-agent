"""Preserve human inputs and audit exact overlap; never fit, predict, or read user records."""
import hashlib
import json
import re
import runpy
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest()


def normalized(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split())


def utterances(value):
    if isinstance(value, dict):
        if isinstance(value.get('utterance'), str):
            yield value['utterance']
        for nested in value.values():
            yield from utterances(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from utterances(nested)


def project():
    earlier = {}
    audit = {}
    for path in sorted(HERE.parent.rglob('*.json')):
        if HERE in path.parents:
            continue
        texts = list(utterances(json.loads(path.read_text(encoding='utf-8-sig'))))
        if not texts:
            continue
        name = path.relative_to(ROOT).as_posix()
        audit[name] = digest(path)
        for text in texts:
            earlier.setdefault(normalized(text), set()).add(name)
    rows = []
    seen = {}
    for index, line in enumerate((HERE / 'requests.txt').read_text(encoding='utf-8').splitlines(), 1):
        match = re.fullmatch(r'(\d+)\. (.+)', line)
        assert match and int(match[1]) == index, 'Missing or reordered numbered item'
        text = match[2]
        key = normalized(text)
        identifier = f'human-intake-005-{index:03}'
        rows.append(dict(id=identifier, ordinal=index, utterance=text, constraints_to_preserve=text,
                         context='independent_no_history', provenance='human_authored',
                         intent=None, entities=None, complete_query=None, expected_disposition=None,
                         human_labels_approved=False, fit_eligible=False, calibration_eligible=False,
                         selection_eligible=False, evaluation_eligibility='undecided',
                         duplicate_of=seen.get(key), earlier_exact_matches=sorted(earlier.get(key, ()))))
        seen.setdefault(key, identifier)
    assert len(rows) == 101, 'All 101 numbered items must remain preserved'
    return dict(version='phase15-human-intake-005', received_date='2026-10-12',
                context_confirmed_by_user='independent_no_history', source_sha256=digest(HERE / 'requests.txt'),
                scored=False, fresh_independent_evaluation=False, ml_enabled=False,
                count=len(rows), normalized_unique=len(seen),
                earlier_overlap_count=sum(bool(row['earlier_exact_matches']) for row in rows),
                audit_source_hashes=audit, rows=rows)


def main():
    data = project()
    files = ('.gitattributes', 'requests.txt', 'README.md', 'intake.py')
    manifest = dict(version=data['version'], hashes={name: digest(HERE / name) for name in files},
                    intake_sha256=hashlib.sha256((json.dumps(data, ensure_ascii=False, indent=2) + '\n')
                                                .encode('utf-8')).hexdigest())
    if sys.argv[1:] == ['--prepare']:
        for name, value in (('intake.json', data), ('intake-manifest.json', manifest)):
            (HERE / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n',
                                    encoding='utf-8', newline='\n')
    else:
        assert not sys.argv[1:]
        assert json.loads((HERE / 'intake.json').read_text(encoding='utf-8')) == data, 'Projection drift'
        assert json.loads((HERE / 'intake-manifest.json').read_text(encoding='utf-8')) == manifest, 'Manifest drift'
        assert digest(HERE / 'intake.json') == manifest['intake_sha256'], 'Serialized intake drift'
    # Run only the existing source/preservation certificate; do not import a classifier/evaluator.
    checker = HERE.parent / 'candidate_v14'
    sys.path.insert(0, str(checker))
    runpy.run_path(str(checker / 'integrity.py'))['check']()
    print(f"Intake verified: {data['count']} items, {data['normalized_unique']} normalized unique, "
          f"{data['earlier_overlap_count']} exact prior overlaps; no predictions or scoring")


if __name__ == '__main__':
    main()
