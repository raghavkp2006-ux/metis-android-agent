"""Record-only human review drafts, using the existing Ed25519 trust/serialization.

No row promotion, model training, privacy approval or dataset freeze is performed.
"""
import argparse
import base64
from collections import Counter
import copy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re

from ml.nlu_v1.dataset_v0 import exception_packet as packet
from ml.nlu_v1.dataset_v0 import semantic_groups as semantic
from ml.nlu_v1.data_management.records import DataError, check_utc, facts_hash, family_matches, sha_json
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes
from ml.nlu_v1.ingestion.common import tokenize
from contract import LABELS, unique_keys, utf16_boundaries, validate as validate_prediction

VERSION = 'nlu_v1_human_review_record_001'
FOLDER = DATASETS / 'quarantine/plan07-review-decisions-001'
DOCUMENT = packet.ROOT / 'docs/nlu_v1/PLAN_07_DECISIONS_TO_RECORD.md'
CHOICES = {'PENDING', 'APPROVE', 'REJECT', 'CHANGE'}
CORRECTIONS = {'corrected_domain': None, 'corrected_action': None, 'corrected_disposition': None,
               'corrected_family': None, 'slot_edits_by_row': {}, 'slot_correction_note': None}


def load_context():
    tickets, rows, source_hashes, _ = packet.load_inputs()
    index = packet.read_json(packet.INDEX)
    if index['generator_sha256'] != packet.file_hash(packet.__file__):
        raise DataError('EXCEPTION_INDEX_GENERATOR_CHANGED')
    if {a['ticket_id'] for a in index['ticket_primary_assignments']} != {t['exception_id'] for t in tickets}:
        raise DataError('EXCEPTION_INDEX_MEMBERSHIP_CHANGED')
    for name, digest in index['input_sha256'].items():
        if packet.file_hash(packet.ROOT / name) != digest:
            raise DataError('EXCEPTION_INDEX_SOURCE_BINDING_STALE')
    for path in (packet.INDEX, packet.PACKET, packet.ROOT / 'docs/nlu_v1/PLAN_07_HUMAN_REVIEW_PACKET.md'):
        source_hashes[path.relative_to(packet.ROOT).as_posix()] = packet.file_hash(path)
    groups = packet.read_json(packet.RUN / 'semantic_review_groups.json')
    templates = packet.read_json(packet.RUN / 'group_approval_template.json')
    registry = packet.read_json(packet.RUN / 'group_registry.json')
    return {'rows': {r['id']: r for r in rows}, 'groups': {g['group_id']: g for g in groups},
            'templates': {t['group_id']: t for t in templates}, 'tickets': tickets, 'index': index,
            'families': registry['families']['families'], 'source_hashes': dict(sorted(source_hashes.items()))}


def member(row):
    return {'row_id': row['id'], 'row_content_sha256': sha_json(row), 'facts_sha256': facts_hash(row),
            'review_state_sha256': sha_json({k: row[k] for k in ('review_status', 'review_history', 'quarantine_reasons', 'privacy_review')}),
            'family_definition_sha256': row['provenance']['family_definition_sha256'],
            'scenario_definition_sha256': row['provenance']['scenario_definition_sha256'],
            'scenario_id': row['scenario_id'], 'semantic_parent_id': row['semantic_parent_id'],
            'privacy_license_evidence_sha256': sha_json({'privacy': row['privacy'], 'review': row['privacy_review'], 'source': row['source']})}


def shared_binding(context):
    return {'source_artifacts_sha256': context['source_hashes'],
            'code_config_mapping_sha256': semantic.hashes(), 'recording_engine_sha256': packet.file_hash(__file__),
            'intended_use': 'HUMAN_REVIEW_RECORDING_ONLY', 'row_promotion_authorized': False,
            'training_authorized': False, 'dataset_freeze_authorized': False}


def targets(context, shared):
    result = {}
    rows = context['rows']
    for ident, group in sorted(context['groups'].items()):
        template = context['templates'][ident]
        if template['decision'] != 'PENDING' or template['signature_ed25519_base64'] is not None:
            raise DataError('SOURCE_TEMPLATE_IS_NOT_UNSIGNED_PENDING')
        if group['code_config_mapping_sha256'] != shared['code_config_mapping_sha256']:
            raise DataError('GROUP_SOURCE_CODE_OR_CONFIG_STALE')
        # This check also preserves the exact original member schema expected by
        # the existing semantic verifier, without rewriting its source template.
        if template['members'] != group['members'] or template['group_content_sha256'] != sha_json(group):
            raise DataError('GROUP_TEMPLATE_MEMBER_BINDING_CHANGED')
        binding = {'target_type': 'SEMANTIC_GROUP', 'target_id': ident, 'shared_binding_sha256': sha_json(shared),
            'target_content_sha256': sha_json(group), 'members': copy.deepcopy(group['members']),
            'schema_versions': sorted({rows[i]['schema_version'] for i in group['row_ids']}),
            'nlu_schema_versions': sorted({rows[i]['nlu_schema_version'] for i in group['row_ids']}),
            'mapping_versions': sorted({rows[i]['source']['mapping_version'] for i in group['row_ids']}),
            'native_group_template_sha256': sha_json(template)}
        result[ident] = {'binding': binding, 'native_group_approval_template': copy.deepcopy(template),
                        'path': 'drafts/semantic/' + ident.split(':')[-1] + '.json'}
    for ticket in sorted(context['tickets'], key=lambda t: t['exception_id']):
        if ticket.get('individual_decisions_required') is not True:
            raise DataError('EXCEPTION_DECISION_SCOPE_REQUIRES_EXPLICIT_POLICY')
        for row_id in sorted(ticket['row_ids']):
            ident = 'exception-row:' + row_id
            if ident in result:
                raise DataError('EXCEPTION_ROW_HAS_MULTIPLE_TICKETS')
            row = rows[row_id]
            locations = [(cluster['cluster_id'], subgroup['subgroup_id']) for cluster in context['index']['clusters']
                         if row_id in cluster['row_ids'] and ticket['exception_id'] in cluster['ticket_ids']
                         for subgroup in cluster['subgroups'] if row_id in subgroup['row_ids']]
            if len(locations) != 1:
                raise DataError('EXCEPTION_ROW_PRIMARY_CLUSTER_OR_SUBGROUP_AMBIGUOUS')
            binding = {'target_type': 'EXCEPTION_ROW', 'target_id': ident, 'shared_binding_sha256': sha_json(shared),
                'target_content_sha256': sha_json({'ticket': ticket, 'row': row}), 'members': [member(row)],
                'ticket_id': ticket['exception_id'], 'ticket_content_sha256': sha_json(ticket),
                'cluster_id': locations[0][0], 'subgroup_id': locations[0][1],
                'schema_versions': [row['schema_version']], 'nlu_schema_versions': [row['nlu_schema_version']],
                'mapping_versions': [row['source']['mapping_version']], 'exception_reasons': ticket['reasons']}
            result[ident] = {'binding': binding, 'native_group_approval_template': None,
                            'path': 'drafts/exceptions/' + row_id + '.json'}
    return dict(sorted(result.items()))


def pending_record(target):
    return {'record_version': VERSION, 'kind': 'HUMAN_REVIEW_RECORD',
            'record_id': 'review-record:' + sha_json(target['binding']), 'binding': copy.deepcopy(target['binding']),
            'native_group_approval_template': copy.deepcopy(target['native_group_approval_template']),
            'decision': 'PENDING', 'reviewer_id': None, 'reviewed_at': None, 'reviewer_note': None,
            'reviewer_registry_sha256': packet.file_hash(semantic.REVIEWERS),
            'canonical_release_authorized': False, 'corrections': copy.deepcopy(CORRECTIONS),
            'signature_ed25519_base64': None}


def description(target, context):
    binding = target['binding']
    rows = [context['rows'][m['row_id']] for m in binding['members']]
    first = rows[0]
    if binding['target_type'] == 'SEMANTIC_GROUP':
        why = first['source']['dataset'] + '/' + first['source']['original_intent'] + '; ' + str(len(rows)) + ' exact members'
    else:
        why = first['source']['dataset'] + '/' + first['source']['original_intent'] + '; ' + ', '.join(binding['exception_reasons'])
    why += '; slots: ' + (', '.join(s['type'] for s in first['slots']) or 'none')
    allowed, _ = packet.display_permission(first)
    why += '; example: ' + (first['text'] if allowed else '[text withheld: no documentation display clearance]')
    labels = '/'.join(first[k] for k in ('domain', 'action', 'disposition')) + '; family=' + (first['family'] or 'UNRESOLVED')
    return packet.cell(why), packet.cell(labels)


def human_document(expected, context):
    semantic_count = sum(t['binding']['target_type'] == 'SEMANTIC_GROUP' for t in expected.values())
    lines = ['# Plan 07 human decisions to record', '',
        '**Edit the Decision cells in this file.** Every cell starts PENDING. Your external review has not yet been recorded as repository evidence.', '',
        f'Targets: **{semantic_count} semantic groups** and **{len(expected) - semantic_count} exception rows**. Exception clusters/subgroups are navigation only; the immutable tickets require individual decisions.', '',
        'Choose exactly PENDING, APPROVE, REJECT or CHANGE for each exact target. Do not edit target IDs, descriptions or labels. Leave unresolved items PENDING. After choosing CHANGE, fill that target’s draft JSON correction fields and optional reviewer note before signing. Draft locations are listed in the local recording manifest. Do not edit immutable binding fields.', '',
        'APPROVE records a semantic finding only. It does not grant source intake, privacy, license, publication, duplicate/leakage clearance, canonical row approval, training or a freeze. No decisions or signatures are generated from the statement that external review was completed.', '',
        '## Workflow', '',
        'Run from `C:/antigravity_projects/metis-repo`. The existing trust store is `ml/nlu_v1/configs/group_reviewers.yaml`; it currently has no enrolled reviewer. Enroll your real public key there before signing, using the existing schema below. Keep your matching Ed25519 private key outside the repository. No reviewer identity, public key or private key is created by preparation.', '',
        '```yaml', 'registry_version: nlu_v1_group_reviewers_001', 'reviewers:', '  YOUR_REAL_REVIEWER_ID:', '    public_key_ed25519_base64: YOUR_BASE64_32_BYTE_ED25519_PUBLIC_KEY', '```', '',
        '1. Edit Decision cells below; do not fill decisions you did not make.',
        '2. Synchronize those choices to the editable pending sidecars:', '',
        '```powershell', 'python -m ml.nlu_v1.dataset_v0.review_recording sync', '```', '',
        '3. For CHANGE, edit the matching draft under `ml/nlu_v1/datasets/quarantine/plan07-review-decisions-001/drafts/`: `semantic/<group-hash>.json` or `exceptions/<row-id>.json`. Fill corrected_domain/action/disposition/family only as intended. `slot_edits_by_row` is keyed by exact member row ID. An ADD/UPDATE supplies corrected_type, corrected_start_utf16, corrected_end_utf16 and corrected_text; REMOVE supplies slot_index. UPDATE also supplies the original slot_index. Offsets are zero-based, end-exclusive UTF-16; text must reconstruct exactly. Edits outside the target or frozen catalogs are rejected. A semantic group correction applies label fields uniformly to its exact members; row-specific slot edits remain explicit.', '',
        '4. Sign only your non-PENDING choices using your actual review UTC time and reason (replace all capitalized placeholders):', '',
        '```powershell', 'python -m ml.nlu_v1.dataset_v0.review_recording sign --batch-id human-review-001 --reviewer-id "YOUR_REAL_REVIEWER_ID" --key-file "C:/PATH_OUTSIDE_REPO/reviewer-ed25519.pem" --reviewed-at "YYYY-MM-DDTHH:MM:SSZ" --reason "YOUR_ACTUAL_REVIEW_REASON"', '```', '',
        'The signer uses the existing `semantic_groups.signed_bytes`, Ed25519 key registry and signature verifier. APPROVE semantic-group records also produce the exact existing group-approval format and run its existing verifier. REJECT/CHANGE and exception decisions use a bound recording envelope with the same serialization, keys and algorithm. No separate trust store or key-generation path exists. An encrypted PEM prompts locally for its password; secrets are never printed.', '',
        '5. Validate your signed batch:', '',
        '```powershell', 'python -m ml.nlu_v1.dataset_v0.review_recording validate --batch-id human-review-001', '```', '',
        'To inspect unsigned draft readiness without signing:', '',
        '```powershell', 'python -m ml.nlu_v1.dataset_v0.review_recording check', '```', '',
        'The check command succeeds for a structurally valid pending packet and reports pending counts; it is not review completion. The signed-batch validator fails if required decisions are missing/invalid. Signed output is append-only in `signed/<batch-id>/`; never overwrite an older signed batch. Use a new batch ID if rerecording. None of these commands applies corrections to dataset rows or starts training/freezing. Keep canonical_release_authorized false unless you expressly intend a later gated release; signing alone never performs one.', '',
        '## Decisions', '', '| Review target | Examples/description | Current labels | Decision |', '|---|---|---|---|']
    for ident, target in expected.items():
        why, labels = description(target, context)
        lines.append(f'| {ident} | {why} | {labels} | PENDING |')
    return '\n'.join(lines) + '\n'


def decision_cells(text, expected, context=None):
    found = {}
    for line in text.splitlines():
        if not line.startswith('| '):
            continue
        parts = re.split(r'(?<!\\)\|', line)
        if len(parts) != 6:
            continue
        ident, choice = parts[1].strip(), parts[4].strip()
        if ident not in expected:
            if ident.startswith(('semantic-group:', 'exception-row:')):
                raise DataError('UNKNOWN_DECISION_TABLE_TARGET')
            continue
        if ident in found or choice not in CHOICES:
            raise DataError('DUPLICATED_TARGET_OR_INVALID_DECISION_CELL')
        if context is not None:
            why, labels = description(expected[ident], context)
            if parts[2].strip() != why or parts[3].strip() != labels:
                raise DataError('DECISION_TABLE_IMMUTABLE_DESCRIPTION_OR_LABELS_CHANGED')
        found[ident] = choice
    if set(found) != set(expected):
        raise DataError('DECISION_TABLE_MEMBERSHIP_CHANGED')
    return found


def prepare(context, folder=FOLDER, document=DOCUMENT):
    folder = Path(folder)
    if folder.exists() or Path(document).exists():
        raise DataError('REVIEW_RECORDING_PREPARATION_ALREADY_EXISTS')
    if (DATASETS / 'quarantine').resolve() not in folder.resolve().parents:
        raise DataError('REVIEW_RECORDS_MUST_STAY_IN_LOCAL_QUARANTINE')
    shared = shared_binding(context)
    expected = targets(context, shared)
    manifest = {'manifest_version': VERSION, 'status': 'PREPARED_UNSIGNED_PENDING_DRAFTS', 'shared_binding': shared,
        'targets': {ident: {'path': target['path'], 'binding_sha256': sha_json(target['binding']),
                    'native_template_sha256': sha_json(target['native_group_approval_template'])} for ident, target in expected.items()},
        'human_document': Path(document).resolve().as_posix(), 'prepared_semantic_targets': len(context['groups']),
        'prepared_exception_targets': len(expected) - len(context['groups']), 'auto_approved_decisions': 0}
    folder.mkdir(parents=True, exist_ok=False)
    for target in expected.values():
        path = folder / target['path']
        path.parent.mkdir(parents=True, exist_ok=True)
        atomic_new(path, json_bytes(pending_record(target)))
    atomic_new(folder / 'recording_manifest.json', json_bytes(manifest))
    atomic_new(Path(document), human_document(expected, context).encode())
    return manifest


def check_bindings(record, target):
    original = pending_record(target)
    if set(record) != set(original) or any(record[k] != original[k] for k in ('record_version', 'kind', 'record_id', 'binding', 'native_group_approval_template')):
        raise DataError('REVIEW_TARGET_MEMBERS_CONTENT_OR_BINDINGS_CHANGED')
    if record['decision'] not in CHOICES or type(record['canonical_release_authorized']) is not bool:
        raise DataError('INVALID_REVIEW_DECISION')
    if record['decision'] != 'APPROVE' and record['canonical_release_authorized']:
        raise DataError('CANONICAL_RELEASE_CONSENT_REQUIRES_APPROVE')
    if set(record['corrections']) != set(CORRECTIONS):
        raise DataError('UNKNOWN_CORRECTION_FIELDS')
    if record['decision'] != 'CHANGE' and record['corrections'] != CORRECTIONS:
        raise DataError('CORRECTIONS_REQUIRE_EXPLICIT_CHANGE_DECISION')


def validate_corrections(record, context):
    if record['decision'] != 'CHANGE':
        return
    corrections = record['corrections']
    if not any(corrections[k] is not None for k in ('corrected_domain', 'corrected_action', 'corrected_disposition', 'corrected_family')) and not corrections['slot_edits_by_row']:
        raise DataError('CHANGE_DECISION_HAS_NO_RECORDED_CORRECTIONS')
    ids = {m['row_id'] for m in record['binding']['members']}
    edits = corrections['slot_edits_by_row']
    if not isinstance(edits, dict) or not set(edits) <= ids:
        raise DataError('SLOT_CORRECTION_OUTSIDE_EXACT_REVIEW_TARGET')
    for ident in ids:
        proposal = copy.deepcopy(context['rows'][ident])
        for key in ('domain', 'action', 'disposition', 'family'):
            if corrections['corrected_' + key] is not None:
                proposal[key] = corrections['corrected_' + key]
        original_slots, replacements, removed, added = proposal['slots'], {}, set(), []
        for edit in edits.get(ident, []):
            operation = edit.get('operation')
            if operation not in ('ADD', 'REMOVE', 'UPDATE'):
                raise DataError('INVALID_SLOT_EDIT_OPERATION')
            slot_index = edit.get('slot_index')
            if operation != 'ADD':
                if type(slot_index) is not int or not 0 <= slot_index < len(original_slots) or slot_index in removed or slot_index in replacements:
                    raise DataError('INVALID_OR_REPEATED_ORIGINAL_SLOT_INDEX')
            if operation == 'REMOVE':
                removed.add(slot_index)
            else:
                slot = {key: edit.get('corrected_' + field) for key, field in
                        (('type', 'type'), ('start', 'start_utf16'), ('end', 'end_utf16'), ('text', 'text'))}
                if slot['type'] not in LABELS['slots'] or type(slot['start']) is not int or type(slot['end']) is not int:
                    raise DataError('INVALID_SLOT_CORRECTION_LABEL_OR_OFFSETS')
                boundaries = utf16_boundaries(proposal['text'])
                if slot['start'] not in boundaries or slot['end'] not in boundaries or slot['end'] <= slot['start'] or proposal['text'][boundaries[slot['start']]:boundaries[slot['end']]] != slot['text']:
                    raise DataError('SLOT_CORRECTION_UTF16_OR_EXACT_TEXT_INVALID')
                if operation == 'ADD':
                    added.append(slot)
                else:
                    replacements[slot_index] = slot
        slots = [replacements.get(i, s) for i, s in enumerate(original_slots) if i not in removed] + added
        proposal['slots'] = sorted(slots, key=lambda s: (s['start'], s['end']))
        proposal['tokens'], proposal['bio_tags'] = tokenize(proposal['text'], proposal['slots'])
        proposal['ood'] = proposal['disposition'] == 'OUT_OF_DOMAIN'
        try:
            validate_prediction({'schema_version': proposal['nlu_schema_version'], 'text': proposal['text'],
                'tokens': proposal['tokens'], 'bio_tags': proposal['bio_tags'],
                'meaning': {k: proposal[k] for k in ('domain', 'action', 'slots', 'disposition', 'ood')}}, 'output')
        except Exception as error:
            raise DataError('CORRECTED_SEMANTICS_OR_BIO_INVALID') from error
        if corrections['corrected_family'] is not None:
            family = context['families'].get(proposal['family'])
            if not family or not family_matches(proposal, family):
                raise DataError('CORRECTED_FAMILY_NOT_REGISTERED_OR_COMPATIBLE')


def load_prepared(context, folder=FOLDER):
    folder = Path(folder)
    manifest = packet.read_json(folder / 'recording_manifest.json')
    shared = shared_binding(context)
    if manifest.get('manifest_version') != VERSION or manifest.get('shared_binding') != shared:
        raise DataError('RECORDING_SOURCE_CONFIG_OR_CONTRACT_STALE')
    expected = targets(context, shared)
    if set(manifest['targets']) != set(expected):
        raise DataError('RECORDING_TARGET_MANIFEST_MEMBERSHIP_CHANGED')
    records = {}
    for ident, target in expected.items():
        entry = manifest['targets'][ident]
        if entry != {'path': target['path'], 'binding_sha256': sha_json(target['binding']),
                     'native_template_sha256': sha_json(target['native_group_approval_template'])}:
            raise DataError('RECORDING_BINDING_MANIFEST_CHANGED')
        path = folder / target['path']
        if path.resolve().is_relative_to(folder.resolve()) is not True:
            raise DataError('RECORDING_DRAFT_PATH_ESCAPE')
        records[ident] = packet.read_json(path)
        check_bindings(records[ident], target)
        validate_corrections(records[ident], context)
    actual = {p.relative_to(folder).as_posix() for p in (folder / 'drafts').rglob('*.json')}
    if actual != {t['path'] for t in expected.values()}:
        raise DataError('RECORDING_DRAFT_ARTIFACT_SET_CHANGED')
    return manifest, expected, records


def summary(records):
    counts = Counter(r['decision'] for r in records.values())
    return {'pending_semantic_decisions': sum(r['decision'] == 'PENDING' and r['binding']['target_type'] == 'SEMANTIC_GROUP' for r in records.values()),
            'pending_exception_decisions': sum(r['decision'] == 'PENDING' and r['binding']['target_type'] == 'EXCEPTION_ROW' for r in records.values()),
            'draft_decisions': dict(sorted(counts.items())), 'dataset_rows_approved': 0, 'training_authorized': False, 'dataset_frozen': False}


def sync(context, folder=FOLDER):
    manifest, expected, records = load_prepared(context, folder)
    choices = decision_cells(Path(manifest['human_document']).read_text(encoding='utf-8'), expected, context)
    # Validate the full transaction before writing any editable working draft.
    updated = {}
    for ident, record in records.items():
        if record['signature_ed25519_base64'] is not None:
            raise DataError('SIGNED_RECORDS_MUST_NOT_BE_EDITED_IN_PLACE')
        record = copy.deepcopy(record)
        record['decision'] = choices[ident]
        check_bindings(record, expected[ident])
        # CHANGE may still need the user's correction details before signing.
        updated[ident] = record
    for ident, record in updated.items():
        (Path(folder) / expected[ident]['path']).write_bytes(json_bytes(record))
    return summary(updated)


def verify_signature(record):
    if record['reviewer_registry_sha256'] != packet.file_hash(semantic.REVIEWERS):
        raise DataError('REVIEWER_REGISTRY_BINDING_STALE')
    reviewer = record['reviewer_id']
    registry = semantic.load_reviewers()
    if not isinstance(reviewer, str) or reviewer.startswith(('machine:', 'audit:', 'validator:', 'import:', 'group:')) or reviewer not in registry['reviewers']:
        raise DataError('ENROLLED_EXTERNAL_HUMAN_REVIEWER_REQUIRED')
    if not isinstance(record['reviewer_note'], str) or not record['reviewer_note'].strip() or not record['reviewed_at']:
        raise DataError('REAL_REVIEW_TIME_AND_REASON_REQUIRED')
    check_utc(record['reviewed_at'])
    if datetime.fromisoformat(record['reviewed_at'].replace('Z', '+00:00')) > datetime.now(timezone.utc):
        raise DataError('HUMAN_REVIEW_TIMESTAMP_IN_FUTURE')
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    try:
        key = Ed25519PublicKey.from_public_bytes(base64.b64decode(registry['reviewers'][reviewer]['public_key_ed25519_base64'], validate=True))
        key.verify(base64.b64decode(record['signature_ed25519_base64'], validate=True), semantic.signed_bytes(record))
    except Exception as error:
        raise DataError('EXTERNAL_HUMAN_SIGNATURE_INVALID') from error


def native_approval(record, context):
    group = context['groups'][record['binding']['target_id']]
    approval = semantic.approval_template(group)
    approval.update(decision='APPROVE', reviewer_id=record['reviewer_id'], reviewed_at=record['reviewed_at'],
                    reason=record['reviewer_note'], canonical_release_authorized=record['canonical_release_authorized'])
    return approval


def sign_records(context, records, key, reviewer, reviewed_at, reason):
    selected = {ident: r for ident, r in records.items() if r['decision'] != 'PENDING'}
    if not selected:
        raise DataError('NO_HUMAN_DECISIONS_SELECTED_FOR_SIGNING')
    from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
    registry = semantic.load_reviewers()
    public = base64.b64encode(key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)).decode()
    if registry['reviewers'].get(reviewer, {}).get('public_key_ed25519_base64') != public:
        raise DataError('PRIVATE_KEY_DOES_NOT_MATCH_ENROLLED_REVIEWER')
    output = {}
    for ident, original in selected.items():
        record = copy.deepcopy(original)
        if record['signature_ed25519_base64'] is not None:
            raise DataError('DO_NOT_RESIGN_OR_EDIT_SIGNED_DRAFT_IN_PLACE')
        record.update(reviewer_id=reviewer, reviewed_at=original['reviewed_at'] or reviewed_at,
                      reviewer_note=original['reviewer_note'] or reason,
                      reviewer_registry_sha256=packet.file_hash(semantic.REVIEWERS))
        validate_corrections(record, context)
        record['signature_ed25519_base64'] = base64.b64encode(key.sign(semantic.signed_bytes(record))).decode()
        verify_signature(record)
        output[ident] = {'record': record, 'native_approval': None}
        if record['decision'] == 'APPROVE' and record['binding']['target_type'] == 'SEMANTIC_GROUP':
            approval = native_approval(record, context)
            approval['signature_ed25519_base64'] = base64.b64encode(key.sign(semantic.signed_bytes(approval))).decode()
            group = context['groups'][ident]
            semantic.verify_human_approval(group, approval, [context['rows'][i] for i in group['row_ids']])
            output[ident]['native_approval'] = approval
    return output


def signed_batch(folder, batch_id):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]{0,79}', batch_id):
        raise DataError('INVALID_SIGNED_BATCH_ID')
    return Path(folder) / 'signed' / batch_id


def write_signed(folder, batch_id, output):
    destination = signed_batch(folder, batch_id)
    if destination.exists():
        raise DataError('SIGNED_BATCH_ALREADY_EXISTS_NEVER_OVERWRITE')
    files = {}
    for ident, item in output.items():
        name = sha_json(ident)
        files[name + '.record.json'] = json_bytes(item['record'])
        if item['native_approval'] is not None:
            files[name + '.group-approval.json'] = json_bytes(item['native_approval'])
    destination.mkdir(parents=True, exist_ok=False)
    for name, data in files.items():
        atomic_new(destination / name, data)
    atomic_new(destination / 'batch_manifest.json', json_bytes({'batch_version': VERSION,
        'files': {name: hashlib.sha256(data).hexdigest() for name, data in files.items()},
        'recorded_decisions': len(output), 'dataset_rows_approved': 0}))


def validate_batch(context, folder, batch_id):
    preparation, expected, drafts = load_prepared(context, folder)
    choices = decision_cells(Path(preparation['human_document']).read_text(encoding='utf-8'), expected, context)
    if any(choices[ident] != draft['decision'] for ident, draft in drafts.items()):
        raise DataError('DECISION_TABLE_NOT_SYNCED_TO_DRAFTS')
    destination = signed_batch(folder, batch_id)
    if not destination.exists():
        return {'valid_signed_decisions': 0, 'required_decisions': len(expected),
                'missing_recorded_decisions': len(expected), 'decision_counts': {}, 'complete': False,
                'status': 'MISSING_RECORDED_DECISION', 'dataset_rows_approved': 0}
    manifest = packet.read_json(destination / 'batch_manifest.json')
    if manifest.get('batch_version') != VERSION or {p.name for p in destination.iterdir()} != set(manifest['files']) | {'batch_manifest.json'}:
        raise DataError('SIGNED_BATCH_ARTIFACT_SET_INVALID')
    for name, digest in manifest['files'].items():
        path = destination / name
        if path.resolve().parent != destination.resolve() or packet.file_hash(path) != digest:
            raise DataError('SIGNED_BATCH_FILE_CHANGED')
    reviewed, consumed = {}, set()
    for name in manifest['files']:
        if not name.endswith('.record.json'):
            continue
        record = packet.read_json(destination / name)
        consumed.add(name)
        ident = record['binding']['target_id']
        if ident not in expected or ident in reviewed or record['decision'] == 'PENDING':
            raise DataError('SIGNED_REVIEW_TARGET_INVALID_OR_DUPLICATED')
        check_bindings(record, expected[ident])
        verify_signature(record)
        validate_corrections(record, context)
        if any(record[k] != drafts[ident][k] for k in ('decision', 'corrections', 'canonical_release_authorized')):
            raise DataError('SIGNED_DECISION_SUPERSEDED_BY_DRAFT_EDIT')
        if record['decision'] == 'APPROVE' and record['binding']['target_type'] == 'SEMANTIC_GROUP':
            approval = packet.read_json(destination / name.replace('.record.json', '.group-approval.json'))
            consumed.add(name.replace('.record.json', '.group-approval.json'))
            group = context['groups'][ident]
            semantic.verify_human_approval(group, approval, [context['rows'][i] for i in group['row_ids']])
            if semantic.signed_bytes(approval) != semantic.signed_bytes(native_approval(record, context)):
                raise DataError('NATIVE_APPROVAL_DIFFERS_FROM_RECORDED_DECISION')
        reviewed[ident] = record
    if consumed != set(manifest['files']) or manifest.get('recorded_decisions') != len(reviewed):
        raise DataError('SIGNED_BATCH_ORPHAN_APPROVAL_OR_COUNT_MISMATCH')
    counts = Counter(r['decision'] for r in reviewed.values())
    return {'valid_signed_decisions': len(reviewed), 'required_decisions': len(expected),
            'missing_recorded_decisions': len(expected) - len(reviewed), 'decision_counts': dict(counts),
            'complete': len(reviewed) == len(expected), 'dataset_rows_approved': 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('prepare', 'check', 'sync', 'sign', 'validate'))
    parser.add_argument('--folder', type=Path, default=FOLDER)
    parser.add_argument('--batch-id')
    parser.add_argument('--reviewer-id')
    parser.add_argument('--key-file', type=Path)
    parser.add_argument('--reviewed-at')
    parser.add_argument('--reason')
    args = parser.parse_args()
    context = load_context()
    if args.command == 'prepare':
        manifest = prepare(context, args.folder)
        print(json.dumps({k: manifest[k] for k in ('prepared_semantic_targets', 'prepared_exception_targets', 'auto_approved_decisions')}))
    elif args.command == 'check':
        preparation, expected, records = load_prepared(context, args.folder)
        decision_cells(Path(preparation['human_document']).read_text(encoding='utf-8'), expected, context)
        print(json.dumps(summary(records), sort_keys=True))
    elif args.command == 'sync':
        print(json.dumps(sync(context, args.folder), sort_keys=True))
    elif args.command == 'sign':
        if not all((args.batch_id, args.reviewer_id, args.key_file, args.reviewed_at, args.reason)):
            parser.error('sign requires --batch-id, --reviewer-id, --key-file, --reviewed-at and --reason')
        preparation, expected, records = load_prepared(context, args.folder)
        choices = decision_cells(Path(preparation['human_document']).read_text(encoding='utf-8'), expected, context)
        if any(choices[ident] != draft['decision'] for ident, draft in records.items()):
            raise DataError('DECISION_TABLE_NOT_SYNCED_TO_DRAFTS')
        if all(r['decision'] == 'PENDING' for r in records.values()):
            raise DataError('NO_HUMAN_DECISIONS_SELECTED_FOR_SIGNING')
        from cryptography.hazmat.primitives.serialization import load_pem_private_key
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        raw = args.key_file.read_bytes()
        try:
            key = load_pem_private_key(raw, password=None)
        except TypeError:
            from getpass import getpass
            key = load_pem_private_key(raw, password=getpass('Ed25519 key password: ').encode())
        if not isinstance(key, Ed25519PrivateKey):
            raise DataError('EXISTING_REVIEW_WORKFLOW_REQUIRES_ED25519_KEY')
        output = sign_records(context, records, key, args.reviewer_id, args.reviewed_at, args.reason)
        write_signed(args.folder, args.batch_id, output)
        print(json.dumps({'signed_decisions': len(output), 'dataset_rows_approved': 0}))
    else:
        if not args.batch_id:
            parser.error('validate requires --batch-id')
        result = validate_batch(context, args.folder, args.batch_id)
        print(json.dumps(result, sort_keys=True))
        if not result['complete']:
            raise SystemExit(2)


if __name__ == '__main__':
    main()
