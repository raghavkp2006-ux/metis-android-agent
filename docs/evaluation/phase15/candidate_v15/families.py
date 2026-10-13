"""Propose reproducible scenario partitions before fitting; human review required."""
import hashlib
import json
import random
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
SEED = 1515
ALIASES = {'scenario-book-return': 'scenario-book', 'scenario-backend': 'scenario-group-project',
           'scenario-registration-payment': 'scenario-registration', 'scenario-payment': 'scenario-registration'}


def build():
    if (HERE / 'family-approval.json').exists():
        raise ValueError('Reviewed family proposal is immutable')
    path = HERE / 'review-projection.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    assignments = {r['id']: ALIASES.get(r['family_draft'], r['family_draft']) for r in data['rows']}
    assert all(assignments.values())
    # Merge complete families if an exact duplicate links them, never split a family.
    for row in data['rows']:
        if row['duplicate_of']:
            old, new = assignments[row['id']], assignments[row['duplicate_of']]
            assignments = {key: new if value == old else value for key, value in assignments.items()}
    groups = defaultdict(list)
    for row in data['rows']:
        groups[assignments[row['id']]].append(row)
    rng = random.Random(SEED)
    best = None
    # Deterministic, labels-only balancing. No model predictions or historical test scores.
    for _ in range(10000):
        keys = sorted(groups)
        rng.shuffle(keys)
        buckets = {'train': [], 'development_validation': [], 'development_test': []}
        target = {'train': .70 * len(data['rows']), 'development_validation': .15 * len(data['rows']),
                  'development_test': .15 * len(data['rows'])}
        counts = Counter()
        for key in keys:
            destination = max(buckets, key=lambda part: (target[part] - counts[part]) / target[part])
            buckets[destination].append(key)
            counts[destination] += len(groups[key])
        class_counts = {part: Counter(r['intended_category_draft'] for key in keys_ for r in groups[key])
                        for part, keys_ in buckets.items()}
        missing = sum(len(set(r['intended_category_draft'] for r in data['rows']) - set(c)) for c in class_counts.values())
        score = missing * 10000 + sum((counts[part] - target[part]) ** 2 for part in buckets)
        if best is None or score < best[0]:
            best = score, buckets, counts, class_counts
    _, buckets, counts, class_counts = best
    partition = {family: part for part, keys in buckets.items() for family in keys}
    rows = [dict(id=row['id'], family=assignments[row['id']], partition=partition[assignments[row['id']]],
                 duplicate_of=row['duplicate_of']) for row in data['rows']]
    payload = dict(version='v15-family-proposal-001', seed=SEED, human_approved=False, fitting_eligible=False,
                   source_projection_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                   rule='Reviewed scenario drafts, conservative unresolved/reference groups and aliases; exact duplicates merge whole families; no conversation context',
                   balance_algorithm='10000 fixed-seed label-only shuffled greedy assignments; minimize missing labels then squared size error; never inspect predictions',
                   counts=dict(counts), class_counts={key: dict(value) for key, value in class_counts.items()},
                   families=[dict(id=key, size=len(value), partition=partition[key], row_ids=[r['id'] for r in value])
                             for key, value in sorted(groups.items())], rows=rows)
    (HERE / 'family-proposal.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8', newline='\n')
    lines = ['# V15 scenario-family and partition proposal', '',
             'Every request remains independent. Groups prevent leakage, never resolve omitted references. Exact duplicates stay together. The split uses fixed seed 1515 and reviewed semantic labels only; no model has been fitted or scored.', '',
             f'Proposed counts: {dict(counts)}. {len(groups)} indivisible scenario groups. This is development training/validation/test; the later calibration, selection and independent evaluation collections stay separate.', '',
             '| Family | Requests | Partition | IDs |', '| --- | ---: | --- | --- |']
    lines += [f"| {g['id']} | {g['size']} | {g['partition']} | {', '.join(g['row_ids'])} |" for g in payload['families']]
    (HERE / 'family-proposal.md').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(dict(families=len(groups), counts=dict(counts), class_counts=payload['class_counts'])))


if __name__ == '__main__':
    build()
