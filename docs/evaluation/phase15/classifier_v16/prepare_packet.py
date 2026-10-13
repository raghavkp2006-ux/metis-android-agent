"""Propose an exact, unapproved calibration/selection subset; never predict or fit."""
import argparse
import json
from collections import Counter
from pathlib import Path
from pipeline import HERE, ROOT, PROTOCOL, digest, frozen_development, normalized


def prepare(paths, purpose, destination):
    if purpose not in ('CALIBRATION', 'SELECTION'):
        raise ValueError('Explicit collection purpose required')
    destination = Path(destination).resolve()
    if not destination.is_relative_to((ROOT / 'app/build/reports/phase15/v16').resolve()):
        raise ValueError('Subset proposals belong in ignored build output')
    if destination.exists():
        raise ValueError('Do not overwrite an existing subset proposal')
    old = {normalized(r['utterance']) for r in frozen_development()}
    seen, pool, bindings = set(), [], {}
    for path in paths:
        path = Path(path).resolve()
        if not path.is_relative_to((ROOT / 'docs/evaluation/phase15').resolve()):
            raise ValueError('Source must be a versioned human intake')
        data = json.loads(path.read_text(encoding='utf-8'))
        bindings[path.relative_to(ROOT).as_posix()] = digest(path)
        for row in data['rows']:
            if not row.get('human_approved') or not row.get('family_human_reviewed'):
                raise ValueError('Source annotations/families are not reviewed')
            if not str(row.get('intended_collection', '')).startswith(purpose) or row.get('predictions_run'):
                raise ValueError('Wrong source collection or predictions preceded freeze')
            key = normalized(row['utterance'])
            if key in seen or key in old or row.get('duplicate_of') or row.get('earlier_exact_matches'):
                continue
            seen.add(key)
            pool.append(row)
    quotas = PROTOCOL[purpose.lower()]
    counts = Counter(r['intended_category_draft'] for r in pool)
    deficits = {label: quota - counts[label] for label, quota in quotas.items() if counts[label] < quota}
    if deficits:
        return dict(status='BLOCKED', purpose=purpose, unique_pool_counts=dict(counts), missing=deficits,
                    predictions_run=False, fitting_started=False, subset_written=False)
    chosen = []
    # Stable label/ID order, independent of model predictions. User approves exact IDs next.
    for label, quota in quotas.items():
        chosen += sorted((r for r in pool if r['intended_category_draft'] == label), key=lambda r: r['id'])[:quota]
    rows = [dict(id=r['id'], utterance=r['utterance'], intent=r['intended_category_draft'],
                 family_id=r['proposed_family'], split=purpose.lower(),
                 disposition=r['expected_disposition_draft'], entities=r['entities_draft'],
                 subject=r['complete_subject_draft'], constraints=r['constraints_draft'],
                 human_approved=True, author=r['author'], reviewer=r['reviewer']) for r in chosen]
    payload = dict(version='v16-subset-proposal', purpose=purpose, subset_human_approved=False,
                   subset_confirmation=None, labels_frozen=False, predictions_seen_before_label_freeze=False,
                   source_bindings=bindings, rows=rows,
                   unused_pool_ids=[r['id'] for r in pool if r['id'] not in {c['id'] for c in chosen}],
                   unused_policy='Unused requests remain in their original purpose; never silently move to another collection')
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return dict(status='PROPOSED_REVIEW_REQUIRED', purpose=purpose, rows=len(rows), unused=len(payload['unused_pool_ids']),
                path=str(destination), predictions_run=False, fitting_started=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--purpose', required=True, choices=('CALIBRATION', 'SELECTION'))
    parser.add_argument('--source', required=True, action='append', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(prepare(args.source, args.purpose, args.output), indent=2))
