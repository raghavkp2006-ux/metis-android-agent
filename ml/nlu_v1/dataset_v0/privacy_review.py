"""Local-only privacy decision preparation; never classifies or releases rows."""
import argparse
import copy
from collections import Counter
from pathlib import Path
import re
import subprocess

from ml.nlu_v1.dataset_v0 import review_recording as recording, exception_packet as packet
from ml.nlu_v1.dataset_v0 import semantic_groups as semantic
from ml.nlu_v1.dataset_v0.machine_curation import PublicEvidence
from ml.nlu_v1.data_management.records import DataError, facts_hash, sha_json, validate_record
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes

FOLDER = DATASETS / 'quarantine/plan07-privacy-review-001'
SOURCES = {'massive_1_1_en_us', 'clinc150_full'}
CHOICES = {'PENDING', 'PUBLIC', 'PRIVATE', 'RESTRICTED', 'KEEP_UNKNOWN'}
VERSION = 'nlu_v1_privacy_review_preparation_001'


def cell(value):
    # Preserve source content as escaped display, including literal line breaks.
    import json
    return json.dumps(value, ensure_ascii=True).replace('|', '\\u007c').replace('<', '\\u003c').replace('>', '\\u003e')


def target(row, proof):
    validate_record(row)
    if row['source']['dataset'] not in SOURCES or row['source']['source_class'] != 'PUBLIC_DATASET' or row['privacy'] != 'UNKNOWN_PRIVACY' or row['source']['original_split'] not in ('train', 'oos_train'):
        raise DataError('PRIVACY_TARGET_OUT_OF_SCOPE')
    return {'target_id': 'privacy-row:' + row['id'], 'row_id': row['id'],
            'row_content_sha256': sha_json(row), 'facts_sha256': facts_hash(row),
            'state_event_sha256': row['review_history'][-1]['event_sha256'],
            'quarantine_sha256': sha_json(row['quarantine_reasons']),
            'source': copy.deepcopy(row['source']), 'source_proof': proof,
            'provenance': copy.deepcopy(row['provenance']),
            'privacy': row['privacy'], 'text': row['text'], 'slots': copy.deepcopy(row['slots'])}


def load_context():
    context = recording.load_context()
    validation = recording.validate_batch(context, recording.FOLDER, 'human-review-001')
    if not validation['complete']:
        raise DataError('SIGNED_SEMANTIC_REVIEW_INCOMPLETE')
    evidence = PublicEvidence()
    if evidence.errors:
        raise DataError('PUBLIC_EVIDENCE_REVERIFICATION_FAILED')
    registry = packet.read_json(packet.RUN / 'group_registry.json')
    groups = (registry['families'], registry['scenarios'])
    targets, public = {}, []
    for row in context['rows'].values():
        if row['source']['dataset'] not in SOURCES:
            continue
        native = copy.deepcopy(row)
        for key in ('machine_curation', 'semantic_group_curation'):
            native['metadata'].pop(key, None)
        proof = evidence.bind(native)
        if row['privacy'] == 'PUBLIC':
            validate_record(row)
            revised, verified, errors = semantic.prepare_row(row, groups, evidence)
            if errors or verified is None or revised != row or not row['privacy_review'] or row['privacy_review']['publication_allowed']:
                raise DataError('EXISTING_PUBLIC_PRIVACY_EVIDENCE_INVALID')
            public.append({'row_id': row['id'], 'content_sha256': sha_json(row),
                           'privacy_review_sha256': sha_json(row['privacy_review'])})
        elif row['privacy'] == 'UNKNOWN_PRIVACY':
            item = target(row, proof)
            targets[item['target_id']] = item
        else:
            raise DataError('UNEXPECTED_PUBLIC_SOURCE_PRIVACY_STATE')
    if len(public) != 60 or Counter(t['source']['dataset'] for t in targets.values()) != Counter({'massive_1_1_en_us': 1741, 'clinc150_full': 263}):
        raise DataError('PRIVACY_REVIEW_SCOPE_CHANGED')
    hashes = dict(context['source_hashes'])
    paths = [Path(__file__), packet.RUN / 'quarantined.jsonl',
             recording.signed_batch(recording.FOLDER, 'human-review-001') / 'batch_manifest.json',
             packet.ROOT / 'ml/nlu_v1/data_management/records.py', semantic.REVIEWERS]
    for source in SOURCES:
        base = DATASETS / 'raw' / ('v0_' + source)
        paths += [base / 'observation.json', base / 'license-evidence.txt']
    for path in paths:
        hashes[path.relative_to(packet.ROOT).as_posix()] = packet.file_hash(path)
    return {'targets': dict(sorted(targets.items())), 'public': public,
            'input_sha256': hashes, 'semantic_review': validation}


def document(targets):
    lines = ['# LOCAL ONLY: Plan 07 human privacy review', '',
             'This gitignored file contains source utterances. Keep it local; do not publish, attach to public documentation, or add it to Git.', '',
             'Edit only Proposed classification cells: PUBLIC, PRIVATE, RESTRICTED or KEEP_UNKNOWN. PENDING means no decision yet. Each target covers exactly one row; no shared privacy-decision policy proves equivalence for these exceptions.', '',
             'Inspect the entire utterance and slots, not only the source/license. PUBLIC means your assessment establishes public privacy suitability; PRIVATE identifies private content; RESTRICTED identifies content with applicable use restrictions; KEEP_UNKNOWN records insufficient evidence without a clearance. These are review guidance, not new automatic rules or assertions of consent. Record the factual basis and any restrictions in the matching decision JSON after synchronization.', '',
             'Plan04 mechanically requires privacy, binding_sha256, review_id, reviewer_id, reviewed_at (UTC), publication_allowed and license_id; its evidence must bind facts after the chosen privacy value, and PRIVACY_CLASSIFIED history must bind that evidence. The contract does not itself establish consent or privacy suitability. PRIVATE_DO_NOT_PUBLISH maps to PRIVATE and CONSENTED_REDACTED to RESTRICTED at ingestion; a public license or synthetic claim alone never establishes PUBLIC.', '',
             'This packet only records draft choices. It never calls classify_privacy, changes rows, signs decisions, approves intake, grants publication/training/release, or clears duplicate findings. Publication remains false. No synthetic row or existing PUBLIC row is a decision target.', '',
             'After editing, run from repository root:', '',
             '```powershell', 'python -m ml.nlu_v1.dataset_v0.privacy_review sync', '```', '',
             'Synchronization writes a new append-only draft batch. It is not application or signed review. Fill actual reviewer_id, reviewed_at and reviewer_note in the draft JSON; none is inferred from semantic review. An authorized later application must use Plan04 classify_privacy with freshly verified bindings, real human evidence, publication_allowed=false and exact license ID. KEEP_UNKNOWN stays UNKNOWN_PRIVACY; PENDING never produces classification evidence. Any subsequent facts change invalidates earlier semantic/privacy bindings; preserve originals and do not re-sign automatically.', '',
             'Full source/provenance/slot evidence and exact hashes: privacy_targets.json. Exact source/license bytes are referenced by its pinned local paths and source_proof; a license hash does not grant privacy clearance.', '',
             '| Review target | Source | Privacy-relevant evidence | Local-only utterance and slots | Current privacy | Proposed classification |',
             '|---|---|---|---|---|---|']
    for ident, t in targets.items():
        why = f"split={t['source']['original_split']}; source_id={t['source']['source_id']}; row_sha256={t['row_content_sha256']}; license={t['source_proof']['license_id']}; full evidence in privacy_targets.json"
        slots = [dict(sorted(slot.items())) for slot in t['slots']]
        lines.append('| ' + ' | '.join([ident, t['source']['dataset'], why, cell({'text': t['text'], 'slots': slots}), t['privacy'], 'PENDING']) + ' |')
    return '\n'.join(lines) + '\n'


def choices(text, targets):
    expected = {line.split('|')[1].strip(): line for line in document(targets).splitlines() if line.startswith('| privacy-row:')}
    found, normalized_lines = {}, []
    for line in text.splitlines():
        if not line.startswith('| privacy-row:'):
            normalized_lines.append(line)
            continue
        parts = line.split('|')
        if len(parts) != 8:
            raise DataError('MALFORMED_PRIVACY_REVIEW_ROW')
        ident, decision = parts[1].strip(), parts[-2].strip()
        if ident not in expected or ident in found or decision not in CHOICES:
            raise DataError('PRIVACY_TARGET_OR_DECISION_INVALID')
        parts[-2] = ' PENDING '
        if '|'.join(parts) != expected[ident]:
            raise DataError('PRIVACY_REVIEW_BINDINGS_OR_CONTENT_CHANGED')
        found[ident] = decision
        normalized_lines.append(expected[ident])
    if set(found) != set(targets):
        raise DataError('PRIVACY_REVIEW_MEMBERSHIP_CHANGED')
    # Instructions and all other bytes are immutable too.
    normalized = '\n'.join(normalized_lines) + ('\n' if text.endswith('\n') else '')
    if normalized != document(targets):
        raise DataError('PRIVACY_REVIEW_DOCUMENT_CHANGED_OUTSIDE_CHOICES')
    return found


def require_local(folder):
    folder = Path(folder).resolve()
    if (DATASETS / 'quarantine').resolve() not in folder.parents:
        raise DataError('PRIVACY_REVIEW_MUST_STAY_LOCAL_QUARANTINE')
    for name in ('privacy_targets.json', 'PRIVACY_DECISIONS.md', 'review_session.json', 'review_session.lock'):
        result = subprocess.run(['git', 'check-ignore', '-q', '--', str(folder / name)], cwd=packet.ROOT)
        if result.returncode != 0:
            raise DataError('PRIVACY_REVIEW_NOT_GITIGNORED')


def prepare(context, folder=FOLDER):
    folder = Path(folder)
    require_local(folder)
    if folder.exists():
        raise DataError('PRIVACY_REVIEW_ALREADY_EXISTS')
    folder.mkdir()
    payload = {'version': VERSION, **context, 'group_targets': 0, 'row_targets': len(context['targets']),
               'classification_authorized': False, 'publication_allowed': False}
    data = json_bytes(payload)
    atomic_new(folder / 'privacy_targets.json', data)
    atomic_new(folder / 'PRIVACY_DECISIONS.md', document(context['targets']).encode())
    atomic_new(folder / 'manifest.json', json_bytes({'version': VERSION, 'privacy_targets_sha256': packet.file_hash(folder / 'privacy_targets.json')}))
    return {'existing_public_verified': len(context['public']), 'row_targets': len(context['targets']), 'group_targets': 0}


def sync(context, folder=FOLDER):
    from .privacy_terminal import reviewer_lock
    require_local(folder)
    with reviewer_lock(folder):
        state = Path(folder) / 'review_session.json'
        if state.exists() and packet.read_json(state).get('pending_edit'):
            raise DataError('RESUME_PRIVACY_REVIEW_TO_RECOVER_PENDING_SAVE')
        return _sync(context, folder)


def _sync(context, folder=FOLDER):
    folder = Path(folder)
    require_local(folder)
    manifest = packet.read_json(folder / 'manifest.json')
    if manifest['version'] != VERSION or packet.file_hash(folder / 'privacy_targets.json') != manifest['privacy_targets_sha256']:
        raise DataError('PRIVACY_PACKET_CHANGED')
    stored = packet.read_json(folder / 'privacy_targets.json')
    current = copy.deepcopy(context)
    # The prepared packet binds its original engine. Preserve that exact engine
    # locally when adding the terminal frontend; never rewrite packet bindings.
    engine = 'ml/nlu_v1/dataset_v0/privacy_review.py'
    if stored['input_sha256'].get(engine) != current['input_sha256'].get(engine):
        if packet.file_hash(folder / 'prepared_privacy_review.py') != stored['input_sha256'][engine]:
            raise DataError('PREPARED_PRIVACY_ENGINE_SNAPSHOT_INVALID')
        current['input_sha256'][engine] = stored['input_sha256'][engine]
    if any(stored[key] != current[key] for key in current):
        raise DataError('PRIVACY_PACKET_INPUT_BINDINGS_STALE')
    found = choices((folder / 'PRIVACY_DECISIONS.md').read_bytes().decode(), context['targets'])
    destination = folder / ('drafts-' + sha_json(found))
    if destination.exists():
        raise DataError('PRIVACY_DRAFT_BATCH_ALREADY_EXISTS')
    destination.mkdir()
    for ident, decision in found.items():
        t = context['targets'][ident]
        atomic_new(destination / (t['row_id'] + '.json'), json_bytes({
            'version': VERSION, 'target': t, 'decision': decision, 'reviewer_id': None,
            'reviewed_at': None, 'reviewer_note': None, 'publication_allowed': False,
            'classification_applied': False, 'status': 'UNSIGNED_HUMAN_PRIVACY_DECISION_DRAFT'}))
    return {'draft_folder': str(destination), 'decisions': dict(Counter(found.values())), 'privacy_states_changed': 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('prepare', 'sync', 'review'))
    parser.add_argument('--pending-only', action='store_const', const=True, default=None)
    parser.add_argument('--all-targets', action='store_const', dest='pending_only', const=False)
    parser.add_argument('--source', choices=('all', 'massive', 'clinc'))
    args = parser.parse_args()
    import json
    if args.command == 'review':
        from .privacy_terminal import review
        review(FOLDER, pending_only=args.pending_only, source=args.source)
        return
    context = load_context()
    result = prepare(context) if args.command == 'prepare' else sync(context)
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    main()
