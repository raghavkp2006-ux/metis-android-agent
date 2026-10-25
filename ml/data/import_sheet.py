"""Strict teammate CSV validation; rejected input never becomes imported data."""
import argparse
from collections import Counter
from pathlib import Path

from ml.data.common import clauses, dump, intents, issues, normalize, read_csv, sha, write_csv
from ml.data.similarity import cross_collisions, embed, skeleton, THRESHOLD


def audit(rows):
    labels = intents()
    used = set()
    clause_counts = Counter()
    defects = []
    duplicates = []
    seen = {}
    for i, row in enumerate(rows, 1):
        errors = issues(row, labels)
        if errors:
            defects.append(dict(id=i, issues=errors))
        try:
            parts = clauses(row)
            used.update(c['intent'] for c in parts)
            clause_counts.update(c['intent'] for c in parts)
        except ValueError:
            used.add(row['intent'])
        key = normalize(row['text'])
        if key in seen:
            duplicates.append(dict(first=seen[key], duplicate=i, kind='normalized/exact'))
        else:
            seen[key] = i
    return dict(rows=len(rows), per_author=dict(Counter(r['author'] for r in rows)),
                per_intent=dict(Counter(r['intent'] for r in rows)),
                clause_intents=dict(clause_counts),
                intents_absent_from_code=sorted(used-labels), code_intents_absent_from_csv=sorted(labels-used),
                defects=defects, duplicates=duplicates)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--against', type=Path, action='append', default=[])
    parser.add_argument('--audit-only', action='store_true')
    args = parser.parse_args()
    rows = read_csv(args.input)
    report = audit(rows)
    report['input_sha256'] = sha(args.input)
    for other in args.against:
        report['duplicates'].extend(dict(against=str(other), **match) for match in cross_collisions(rows, read_csv(other)))
    # Typed skeletons and pinned semantic duplicates within the teammate submission.
    for i, row in enumerate(rows):
        for j in range(i):
            if skeleton(row) == skeleton(rows[j]) and normalize(row['text']) != normalize(rows[j]['text']):
                report['duplicates'].append(dict(first=j+1, duplicate=i+1, kind='skeleton'))
    report['semantic_status'] = 'not run: invalid labels or exact duplicates' if report['defects'] or report['duplicates'] else 'pending'
    if not report['defects'] and not report['duplicates'] and rows:
        import math
        vectors = embed([r['text'] for r in rows])
        for i in range(len(rows)):
            for j in range(i):
                cosine = math.fsum(a*b for a,b in zip(vectors[i], vectors[j]))
                if cosine >= THRESHOLD:
                    report['duplicates'].append(dict(first=j+1, duplicate=i+1, kind='semantic', cosine=cosine))
        report['semantic_status'] = 'complete: pinned Plan07 offline MiniLM'
    report['accepted'] = not report['defects'] and not report['duplicates']
    dump(args.report, report)
    if args.audit_only:
        return
    if not report['accepted']:
        raise SystemExit('CSV rejected; see report (no output written)')
    if not args.output:
        parser.error('--output required for import')
    if args.output.exists():
        raise ValueError('refusing to overwrite import')
    write_csv(args.output, rows)


if __name__ == '__main__':
    main()
