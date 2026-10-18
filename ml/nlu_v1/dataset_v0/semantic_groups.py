"""Exact semantic-group proposals, exception queues and external signed review."""
import base64
from collections import Counter, defaultdict
import copy
from datetime import datetime, timezone
import json
import re

import yaml
from jsonschema import ValidationError
from ml.nlu_v1.dataset_v0 import machine_curation as machine
from ml.nlu_v1.dataset_v0.coverage_additions import validate_extension, TEMPLATES, COVERAGE
from ml.nlu_v1.data_management.records import (DataError, append_event, canonical_issues, check_utc,
    classify_privacy, facts_hash, family_matches, sha_json, transition, utc_now, validate_record)
from ml.nlu_v1.data_management.dedupe import skeleton
from ml.nlu_v1.source_registry import ROOT, UniqueLoader
from ml.nlu_v1.splitting.split import official

VERSION = 'nlu_v1_semantic_groups_001'
POLICY = ROOT / 'ml/nlu_v1/configs/semantic_group_policy_001.yaml'
REVIEWERS = ROOT / 'ml/nlu_v1/configs/group_reviewers.yaml'


def hashes():
    paths = ['dataset_v0/semantic_groups.py', 'dataset_v0/coverage_additions.py', 'dataset_v0/machine_curation.py',
             'data_management/records.py', 'data_management/dedupe.py', 'splitting/split.py',
             'contract.py', 'context_contract.py', 'source_registry.py', 'synthetic_registry.py',
             'synthetic/generate.py', 'ingestion/common.py', 'ingestion/massive_mapper.py', 'ingestion/clinc_mapper.py',
             'configs/dedupe_policy.yaml', 'configs/dataset_source_evidence.json', 'configs/synthetic_sources.yaml',
             'configs/semantic_group_policy_001.yaml', 'configs/coverage_templates_001.yaml',
             'configs/dataset_v0_coverage_policy.yaml', 'configs/machine_curation_002.yaml',
             'configs/machine_families_002.yaml', 'configs/dataset_sources.yaml',
             'ingestion/mappings/massive_intent_mapping.yaml', 'ingestion/mappings/clinc_intent_mapping.yaml']
    return {path: machine.file_hash(ROOT / 'ml/nlu_v1' / path) for path in paths}


def semantic_proof(row):
    if row['source']['source_class'] == 'CONTROLLED_SYNTHETIC':
        if 'coverage_structure' not in row['metadata']:
            return None, ['HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW']
        validate_extension(row)
        return 'CLOSED_EXTENSION:' + row['metadata']['coverage_structure']['role'], []
    if not all(machine.safe_slot(slot) for slot in row['slots']):
        return None, ['PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT']
    rule, error = machine.semantics(row)
    if error in ('UNRESOLVED_REFERENCE', 'TEMPORAL_CONTEXT_UNRESOLVED'):
        return None, [error]
    if rule:
        return rule, []
    text = skeleton(row).rstrip('?.!')
    policy = yaml.load(POLICY.read_text(encoding='utf-8'), Loader=UniqueLoader)
    pair = row['domain'] + '/' + row['action']
    if any(re.fullmatch(pattern, text) for pattern in policy['additional_closed_rules'].get(pair, [])):
        return 'CLOSED_GROUP_GRAMMAR:' + pair, []
    if re.search(r'\b(?:and|then|also) (?:delete|remove|cancel|send|create|set|move|publish|buy)\b', text):
        return None, ['UNEXPECTED_SECOND_ACTION']
    return None, ['WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN']


def prepare_row(row, groups, evidence):
    validate_record(row)
    if official(row) or row['partition'] in ('PROTECTED_EVALUATION', 'FINAL_INDEPENDENT_TEST') or row['source']['source_class'] not in ('PUBLIC_DATASET', 'CONTROLLED_SYNTHETIC'):
        return row, None, ['PROTECTED_OR_NONPUBLIC_EXCLUDED']
    if row['review_status'] not in ('PENDING', 'QUARANTINED'):
        return row, None, ['CURRENT_REVIEW_STATE_REQUIRES_SEPARATE_POLICY']
    if row['privacy'] in ('PRIVATE', 'RESTRICTED'):
        return row, None, ['PRIVACY_RESTRICTION_REQUIRES_INDIVIDUAL_REVIEW']
    try:
        # The native verifier requires exact native metadata. Remove only these
        # explicit derived namespaces in a temporary source view; never remove
        # arbitrary metadata or change the actual reviewed canonical record.
        native_view = copy.deepcopy(row)
        for namespace in ('machine_curation', 'semantic_group_curation'):
            native_view['metadata'].pop(namespace, None)
        source_proof = evidence.bind(native_view) if row['source']['source_class'] == 'PUBLIC_DATASET' else {'template_sha256': machine.file_hash(TEMPLATES), 'source_registry_sha256': row['provenance']['source_registry_sha256'], 'rights': 'INTERNAL_PENDING_REVIEW'}
        rule, reasons = semantic_proof(row)
    except (DataError, ValueError, KeyError) as error:
        reason = str(error)
        return row, None, [reason if re.fullmatch(r'[A-Z][A-Z0-9_]+', reason) else 'SOURCE_OR_TEMPLATE_PROOF_INVALID']
    if reasons:
        return row, None, reasons
    family = groups[0]['families'].get(row['family'])
    scenario = groups[1]['scenarios'].get(row['scenario_id'])
    if not family or not scenario or not family_matches(row, family) or scenario['family'] != row['family'] or scenario['semantic_parent_id'] != row['semantic_parent_id']:
        return row, None, ['FAMILY_OR_SCENARIO_UNRESOLVED']
    if row['provenance']['family_definition_sha256'] != sha_json(family) or row['provenance']['scenario_definition_sha256'] != sha_json(scenario):
        return row, None, ['FAMILY_OR_SCENARIO_DEFINITION_STALE']
    result = copy.deepcopy(row)
    if row['source']['source_class'] == 'PUBLIC_DATASET':
        existing = row['metadata'].get('semantic_group_curation')
        if existing:
            if existing.get('policy_version') != VERSION or existing.get('policy_sha256') != machine.file_hash(POLICY) or existing.get('rule') != rule or existing.get('source_proof') != source_proof:
                return row, None, ['PRIOR_GROUP_PROOF_STALE']
            return row, {'rule': rule, 'source_proof': source_proof, 'family_sha256': sha_json(family), 'scenario_sha256': sha_json(scenario)}, []
        # These wholly matched grammars prove argument completeness. The source
        # mapping is explicitly provisional; retain its original value as evidence.
        if result['disposition'] == 'SUPPORTED_NEEDS_CONTEXT':
            result['disposition'] = 'SUPPORTED_COMPLETE'
        result['privacy_review'] = None
        result['privacy'] = 'UNKNOWN_PRIVACY'
        result['metadata']['semantic_group_curation'] = {'policy_version': VERSION, 'rule': rule,
            'source_proof': source_proof, 'original_disposition': row['disposition'], 'original_facts_sha256': facts_hash(row),
            'policy_sha256': machine.file_hash(POLICY)}
        append_event(result, result['review_status'], result['review_status'], 'machine:' + VERSION, 'GROUP_SEMANTIC_PROOF_BOUND')
        proposal = copy.deepcopy(result)
        proposal['privacy'] = 'PUBLIC'
        result = classify_privacy(result, 'PUBLIC', {'binding_sha256': facts_hash(proposal),
            'review_id': 'group-privacy:' + sha_json([source_proof, rule]), 'reviewer_id': 'machine:' + VERSION,
            'reviewed_at': utc_now(), 'publication_allowed': False, 'license_id': source_proof['license_id']})
    validate_record(result)
    return result, {'rule': rule, 'source_proof': source_proof, 'family_sha256': sha_json(family),
                    'scenario_sha256': sha_json(scenario)}, []


def member(row, proof):
    return {'row_id': row['id'], 'row_content_sha256': sha_json(row), 'facts_sha256': facts_hash(row),
            'review_state_sha256': sha_json({'review_status': row['review_status'], 'history': row['review_history'],
                                           'quarantine_reasons': row['quarantine_reasons'], 'privacy_review': row['privacy_review']}),
            'family_definition_sha256': proof['family_sha256'], 'scenario_definition_sha256': proof['scenario_sha256'],
            'scenario_id': row['scenario_id'], 'semantic_parent_id': row['semantic_parent_id'],
            'privacy_license_evidence_sha256': sha_json({'privacy': row['privacy'], 'review': row['privacy_review'], 'source': proof['source_proof']})}


def create_groups(rows, groups, evidence, audit_report=None):
    batches, exceptions, output = defaultdict(list), [], []
    engine_hashes = hashes()
    duplicate_ids = set()
    if audit_report:
        duplicate_ids = {ident for match in audit_report['matches'] for ident in match['row_ids']}
    for row in rows:
        try:
            updated, proof, reasons = prepare_row(row, groups, evidence)
        except (DataError, ValueError, KeyError, ValidationError) as error:
            updated, proof, reasons = row, None, ['MALFORMED_ANNOTATION_OR_REVIEW_BINDING']
        output.append(updated)
        if reasons:
            exceptions.append({'row_id': row['id'], 'row_content_sha256': sha_json(row), 'source': row['source']['dataset'],
                               'source_intent': row['source']['original_intent'], 'reasons': reasons,
                               'protected': 'PROTECTED_OR_NONPUBLIC_EXCLUDED' in reasons})
            continue
        slot_pattern = [slot['type'] for slot in row['slots']]
        source = row['source']
        privacy_state = {'class': updated['privacy'], 'publication_allowed': bool((updated['privacy_review'] or {}).get('publication_allowed')),
                         'license_id': proof['source_proof'].get('license_id', 'INTERNAL_PENDING_REVIEW'),
                         'license_sha256': proof['source_proof'].get('license_sha256'),
                         'intake_approved': bool(source['intake_approval_id'])}
        identity = {'source_dataset': source['dataset'], 'source_version': source['version'], 'source_split': source['original_split'],
                    'source_intent': source['original_intent'], 'domain': updated['domain'], 'action': updated['action'],
                    'disposition': updated['disposition'], 'ood': updated['ood'], 'family': updated['family'],
                    'slot_pattern': slot_pattern, 'mapping_version': source['mapping_version'], 'mapper_version': source['mapper_version'],
                    'mapping_decision': source['mapping_decision'], 'semantic_rule': proof['rule'],
                    'scenario_pattern': sha_json([proof['family_sha256'], source['original_intent'], slot_pattern, proof['rule']]),
                    'privacy_license_state': privacy_state, 'hard_negative_behavior': updated['metadata'].get('generation_role', 'PUBLIC_SOURCE'),
                    'duplicate_audit_pending': row['id'] in duplicate_ids}
        batches[sha_json(identity)].append((identity, updated, proof))
    result = []
    for key, batch in sorted(batches.items()):
        identity = batch[0][0]
        members = sorted([member(row, proof) for _, row, proof in batch], key=lambda m: m['row_id'])
        payload = {'policy_version': VERSION, 'identity': identity, 'members': members, 'code_config_mapping_sha256': engine_hashes}
        result.append({'group_id': 'semantic-group:' + sha_json(payload), 'binding_sha256': sha_json(payload), **payload,
                       'row_ids': [m['row_id'] for m in members], 'row_count': len(members), 'anomaly_count': 0,
                       'validator_results': {'schema_spans_bio': 'PASS', 'native_or_template_binding': 'PASS',
                           'semantic_equivalence': 'CLOSED_RULE_MATCH', 'privacy_license': 'EVIDENCE_BOUND_RIGHTS_AND_INTAKE_PENDING',
                           'duplicate_audit': 'REQUIRES_GROUP_REVIEW' if identity['duplicate_audit_pending'] else 'NO_MATCH_FOR_MEMBERS'},
                       'review_scope': 'SEMANTIC_MAPPING_ONLY', 'canonical_approval': 'BLOCKED_UNTIL_EXTERNAL_GATES_PASS',
                       'review_rationale': 'Every exact member matches one closed semantic rule, the same slot pattern, family, source mapping and privacy/license state; exceptions cannot inherit this decision.'})
    compact = defaultdict(list)
    for exception in exceptions:
        compact[(exception['source'], exception['source_intent'], tuple(exception['reasons']), exception['protected'])].append(exception['row_id'])
    exception_tickets = [{'exception_id': sha_json(list(key)), 'source': key[0], 'source_intent': key[1], 'reasons': list(key[2]),
                          'protected': key[3], 'row_ids': sorted(ids), 'individual_decisions_required': True}
                         for key, ids in sorted(compact.items())]
    summary = {'original_manual_tickets': 425, 'grouped_tickets': len(result), 'rows_covered_by_group_review': sum(g['row_count'] for g in result),
               'individual_review_rows': len(exceptions), 'exception_tickets': len(exception_tickets),
               'exception_reasons': dict(Counter(reason for e in exceptions for reason in e['reasons'])),
               'approved_rows': 0, 'human_approvals_created': 0}
    return output, result, exceptions, exception_tickets, summary


def approval_template(group):
    return {'kind': 'HUMAN_SEMANTIC_GROUP_APPROVAL', 'scope': 'SEMANTIC_MAPPING_ONLY', 'decision': 'PENDING',
            'canonical_release_authorized': False,
            'group_id': group['group_id'], 'binding_sha256': group['binding_sha256'], 'group_content_sha256': sha_json(group),
            'members': group['members'], 'semantic_mapping': group['identity'],
            'code_config_mapping_sha256': group['code_config_mapping_sha256'],
            'reviewer_registry_sha256': machine.file_hash(REVIEWERS),
            'reviewer_id': None, 'reviewed_at': None, 'reason': None, 'signature_ed25519_base64': None}


def signed_bytes(approval):
    return json.dumps({k: v for k, v in approval.items() if k != 'signature_ed25519_base64'},
                      sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode()


def load_reviewers():
    registry = yaml.load(REVIEWERS.read_text(encoding='utf-8'), Loader=UniqueLoader)
    if registry.get('registry_version') != 'nlu_v1_group_reviewers_001' or not isinstance(registry.get('reviewers'), dict):
        raise DataError('ENROLLED_REVIEWER_REGISTRY_INVALID')
    return registry


def verify_human_approval(group, approval, current_rows):
    # A caller-provided key registry cannot enroll itself. Tests mock this loader;
    # production entry points always read the enrolled repository trust store.
    registry = load_reviewers()
    template = approval_template(group)
    required = set(template)
    if set(approval) != required or approval['decision'] != 'APPROVE' or approval['kind'] != template['kind'] or approval['scope'] != template['scope']:
        raise DataError('REAL_EXTERNAL_HUMAN_GROUP_APPROVAL_REQUIRED')
    if type(approval['canonical_release_authorized']) is not bool:
        raise DataError('EXPLICIT_CANONICAL_RELEASE_DECISION_REQUIRED')
    for key in ('group_id', 'binding_sha256', 'group_content_sha256', 'members', 'semantic_mapping', 'code_config_mapping_sha256', 'reviewer_registry_sha256'):
        if approval[key] != template[key]:
            raise DataError('GROUP_APPROVAL_BINDING_CHANGED')
    if group['code_config_mapping_sha256'] != hashes():
        raise DataError('GROUP_APPROVAL_CODE_OR_CONFIG_CHANGED')
    indexed = {r['id']: r for r in current_rows}
    if len(indexed) != len(current_rows) or set(indexed) != set(group['row_ids']) or any(sha_json(indexed[m['row_id']]) != m['row_content_sha256'] for m in group['members']):
        raise DataError('GROUP_APPROVAL_MEMBERSHIP_OR_CONTENT_CHANGED')
    reviewer = approval['reviewer_id']
    if not isinstance(reviewer, str) or reviewer.startswith(('machine:', 'audit:', 'validator:', 'import:', 'group:')) or reviewer not in registry.get('reviewers', {}):
        raise DataError('ENROLLED_EXTERNAL_HUMAN_REVIEWER_REQUIRED')
    if not isinstance(approval['reason'], str) or not approval['reason'].strip() or not approval['reviewed_at']:
        raise DataError('HUMAN_REVIEW_REASON_AND_UTC_REQUIRED')
    check_utc(approval['reviewed_at'])
    if datetime.fromisoformat(approval['reviewed_at'].replace('Z', '+00:00')) > datetime.now(timezone.utc):
        raise DataError('HUMAN_REVIEW_TIMESTAMP_IN_FUTURE')
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    try:
        key = Ed25519PublicKey.from_public_bytes(base64.b64decode(registry['reviewers'][reviewer]['public_key_ed25519_base64'], validate=True))
        key.verify(base64.b64decode(approval['signature_ed25519_base64'], validate=True), signed_bytes(approval))
    except Exception as error:
        raise DataError('EXTERNAL_HUMAN_SIGNATURE_INVALID') from error
    return {'group_id': group['group_id'], 'approval_sha256': sha_json(approval), 'reviewer_id': reviewer,
            'semantic_approval': True, 'canonical_rows_approved': 0, 'external_gates_still_required': True}


def apply_canonical_approval(group, approval, current_rows, groups, *, audit_context=None):
    receipt = verify_human_approval(group, approval, current_rows)
    if not approval['canonical_release_authorized']:
        raise DataError('GROUP_SEMANTIC_APPROVAL_CANNOT_BYPASS_RELEASE_AUTHORIZATION')
    if audit_context is None or any(canonical_issues(row, groups) or row['privacy'] != 'PUBLIC' or not (row['privacy_review'] or {}).get('publication_allowed') for row in current_rows):
        raise DataError('GROUP_SEMANTIC_APPROVAL_CANNOT_BYPASS_EXTERNAL_GATES')
    from ml.nlu_v1.data_management.dedupe import verify_report
    corpus, report = audit_context['rows'], audit_context['report']
    verify_report(corpus, report)
    member_ids = set(group['row_ids'])
    if any(member_ids.intersection(match['row_ids']) for match in report['matches']) or any(
        official(r) or r['partition'] in ('PROTECTED_EVALUATION', 'FINAL_INDEPENDENT_TEST') for r in current_rows):
        raise DataError('DUPLICATE_OR_PROTECTED_ROWS_CANNOT_INHERIT_RELEASE')
    corpus_index = {r['id']: r for r in corpus}
    if any(corpus_index.get(r['id']) != r for r in current_rows):
        raise DataError('GROUP_RELEASE_AUDIT_CONTENT_CHANGED')
    output = []
    for original in current_rows:
        row = copy.deepcopy(original)
        for target in (['PENDING'] if row['review_status'] == 'QUARANTINED' else []) + ['AUTO_VALIDATED', 'HUMAN_REVIEWED', 'APPROVED']:
            evidence = {'kind': 'HUMAN_REVIEW', 'binding_sha256': facts_hash(row), 'reviewer_id': receipt['reviewer_id'],
                        'review_id': group['group_id'], 'state_event_sha256': row['review_history'][-1]['event_sha256'],
                        'quarantine_sha256': sha_json(row['quarantine_reasons']), 'external_group_approval_sha256': receipt['approval_sha256']}
            row = transition(row, target, actor=receipt['reviewer_id'], reason='EXTERNAL_GROUP_APPROVAL_APPLIED', evidence=evidence, groups=groups)
        output.append(row)
    return output
