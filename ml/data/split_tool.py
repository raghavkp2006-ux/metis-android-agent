"""Author-isolated train/development splits; external Day 5 hash/leakage gate.

Never creates, discovers, or requests independent test data. `seal` and `check`
operate only on an explicit operator-supplied external path, when available.
"""
import argparse
import json
from pathlib import Path

from ml.data.common import ROOT, dump, read_csv, sha, write_csv, normalize, issues
from ml.data.similarity import cross_collisions


def split(rows, development_authors):
    if any(issues(row) for row in rows):
        raise ValueError('invalid gold schema/labels; import and review before splitting')
    authors = {normalize(r['author']) for r in rows}
    development_authors = {normalize(a) for a in development_authors}
    if not development_authors or not development_authors < authors:
        raise ValueError('need disjoint nonempty author groups; one author cannot be split')
    return ([r for r in rows if normalize(r['author']) not in development_authors],
            [r for r in rows if normalize(r['author']) in development_authors])


def external(path):
    path = Path(path).resolve()
    if path.is_relative_to(ROOT):
        raise ValueError('independent test must be supplied outside repository')
    return path


def seal(train, test, manifest):
    test = external(test)
    if Path(manifest).exists():
        raise ValueError('refusing to overwrite sealed manifest')
    a, b = read_csv(train), read_csv(test)
    if {normalize(r['author']) for r in a} & {normalize(r['author']) for r in b}:
        raise ValueError('author leakage')
    collisions = cross_collisions(a, b)
    if collisions:
        raise ValueError('train/test leakage: ' + json.dumps(collisions))
    dump(manifest, dict(version=1, test_sha256=sha(test), train_authors=sorted({r['author'] for r in a}),
                        test_authors=sorted({r['author'] for r in b}), policy='Plan07 MiniLM .95 + typed skeleton'))


def check(train, test, manifest):
    test = external(test)
    frozen = json.loads(Path(manifest).read_text())
    if sha(test) != frozen['test_sha256']:
        raise ValueError('test hash changed')
    a, b = read_csv(train), read_csv(test)
    if {normalize(r['author']) for r in a} & {normalize(r['author']) for r in b}:
        raise ValueError('author leakage')
    matches = cross_collisions(a, b)
    if matches:
        raise ValueError('train/test leakage: ' + json.dumps(matches))
    return dict(status='PASS', train_rows=len(a), test_rows=len(b), test_sha256=sha(test))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    s = sub.add_parser('split')
    s.add_argument('--input', required=True, type=Path)
    s.add_argument('--dev-author', action='append', required=True)
    s.add_argument('--output', required=True, type=Path)
    for name in ('seal', 'check'):
        p = sub.add_parser(name)
        for flag in ('train', 'test', 'manifest'):
            p.add_argument('--' + flag, required=True, type=Path)
    args = parser.parse_args()
    if args.command == 'split':
        train, dev = split(read_csv(args.input), set(args.dev_author))
        matches = cross_collisions(train, dev)
        if matches:
            raise ValueError('train/development leakage: ' + json.dumps(matches))
        if args.output.exists():
            raise ValueError('output directory exists')
        write_csv(args.output / 'train.csv', train)
        write_csv(args.output / 'development.csv', dev)
        dump(args.output / 'manifest.json', dict(input_sha256=sha(args.input),
            train_authors=sorted({r['author'] for r in train}), development_authors=sorted({r['author'] for r in dev}),
            independent_test='not found; not created or searched for'))
    elif args.command == 'seal':
        seal(args.train, args.test, args.manifest)
    else:
        print(json.dumps(check(args.train, args.test, args.manifest)))


if __name__ == '__main__':
    main()
