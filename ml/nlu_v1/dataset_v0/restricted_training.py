"""Policy003 source authority and native intake; no public export or model training."""
import argparse
from collections import Counter
import copy
from datetime import datetime, timezone
from pathlib import Path
import re
import unicodedata

import yaml
from ml.nlu_v1.dataset_v0 import collection_evidence, review_recording, exception_packet as packet
from ml.nlu_v1.dataset_v0.machine_curation import PublicEvidence
from ml.nlu_v1.dataset_v0.coverage_additions import coverage_report
from ml.nlu_v1.dataset_v0.build import distribution
from ml.nlu_v1.dataset_v0.similarity_audit import verify_binding
from ml.nlu_v1.data_management.dedupe import audit, verify_report
from ml.nlu_v1.data_management.records import (DataError, append_event, canonical_issues,
    classify_privacy, facts_hash, sha_json, validate_record)
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes
from ml.nlu_v1.ingestion.common import REGISTRY, MAPPER_VERSION
from ml.nlu_v1.ingestion.ingest import approve_intake
from ml.nlu_v1.source_registry import ROOT, UniqueLoader
from ml.nlu_v1.splitting.split import plan, connected_groups

POLICY = ROOT / 'ml/nlu_v1/configs/privacy_policy_003.yaml'
DESTINATION = DATASETS / 'quarantine/plan07-restricted-training-003'
OWNER_REQUEST = Path('C:/Users/ragha/.codex/attachments/191ade2a-67f7-4f45-9d42-1ccf6d7fa68e/Pasted text.txt')
OWNER_REQUEST_SHA256 = '4c695069bc3a76247f448af58961d7b60b7cd8ba208e0a027614473000097375'
OPERATOR = 'project-owner-current-request'
INTAKE_REASON = ('Operator authorizes intake of the exact verified MASSIVE and CLINC150 '
    'public-source artifacts for METIS NLU v1 Dataset v0 curation. Intake authorization '
    'does not grant publication, canonical release, production use, or protected-final-evaluation authority.')


def policy():
    value = yaml.load(POLICY.read_text(encoding='utf-8'), Loader=UniqueLoader)
    if value['policy_version'] != 'nlu_v1_privacy_policy_003' or value['row_privacy'] != 'RESTRICTED' or set(value['scope']) != {'massive_1_1_en_us', 'clinc150_full'}:
        raise DataError('RESTRICTED_POLICY_SCOPE_INVALID')
    if value['publication_allowed'] is not False or value['raw_row_republication'] is not False:
        raise DataError('RESTRICTED_POLICY_CANNOT_AUTHORIZE_PUBLICATION')
    return value


def normalized(text):
    return ' '.join(unicodedata.normalize('NFKC', text).replace('-\n', '').split())


def collection_findings():
    manifest = collection_evidence.verify()
    pages = packet.read_json(collection_evidence.FOLDER / 'paper_text.json')['pages']
    # Exact short spans, checked against pinned local paper text, plus location
    # references for the complete methodology. No inference of individual consent.
    specs = {
        'clinc': (1, 'a scoping crowdsourcing task', 'Sections 2.1–2.3: scoping/rephrase/scenario elicitation; OOS elicitation; worker grouping for in-scope, prompt grouping for OOS.'),
        'massive': (0, 'professional translators to localize the English-only SLURP dataset', 'Abstract and data creation: professional localization; English source is SLURP.'),
        'slurp': (1, 'we collected textual data by prompting Mechanical Turk (AMT) workers', 'Section 3.1: textual robot-assistant commands elicited using 200 predefined prompts. Audio/demographic collection is a separate process and outside this authorization.')}
    findings = {}
    for key, (page, quote, interpretation) in specs.items():
        text = normalized(pages[key][page])
        start = text.find(quote)
        if start < 0:
            raise DataError('PINNED_COLLECTION_METHOD_PASSAGE_NOT_FOUND')
        findings[key] = {'pdf_page': page + 1, 'normalizer': 'NFKC_DEHYPHENATE_NEWLINE_WHITESPACE_001',
            'normalized_page_sha256': sha_json(text), 'start': start, 'end': start + len(quote),
            'exact_short_quote': quote, 'collection_method_assessment': interpretation,
            'pdf_sha256': manifest['records'][key]['pdf_sha256'],
            'canonical_url': manifest['records'][key]['canonical_url']}
    return findings


def source_binding(source_id, rows, evidence, config):
    if source_id not in config['scope'] or not rows:
        raise DataError('SOURCE_OUTSIDE_RESTRICTED_POLICY')
    pin = config['scope'][source_id]
    source = evidence.sources[source_id]
    observed = source['observation']
    for obs_key, pin_key in (('source_version', 'version'), ('revision', 'revision'), ('sha256', 'archive_sha256'), ('member_sha256', 'member_sha256')):
        if observed[obs_key] != pin[pin_key]:
            raise DataError('SOURCE_ARTIFACT_OUTSIDE_RESTRICTED_POLICY_PIN')
    for row in rows:
        if row['source']['dataset'] != source_id or row['source']['source_class'] != 'PUBLIC_DATASET' or row['source']['original_split'] not in pin['original_splits'] or row['partition'] in ('PROTECTED_EVALUATION', 'FINAL_INDEPENDENT_TEST'):
            raise DataError('PROTECTED_OR_NONPUBLIC_ROW_OUTSIDE_AUTHORITY')
        native = copy.deepcopy(row)
        for namespace in ('machine_curation', 'semantic_group_curation'):
            native['metadata'].pop(namespace, None)
        evidence.bind(native)
    return {'source': source_id, 'version': pin['version'], 'revision': pin['revision'],
            'artifact_sha256': pin['archive_sha256'], 'member_sha256': pin['member_sha256'],
            'eligible_splits': sorted({r['source']['original_split'] for r in rows}),
            'license_id': source['license_id'], 'license_sha256': source['license_sha256'],
            'acquisition_evidence_sha256': source['observation_sha256'],
            'source_registry_sha256': packet.file_hash(ROOT / 'ml/nlu_v1/configs/dataset_sources.yaml'),
            'policy_version': config['policy_version'], 'policy_sha256': packet.file_hash(POLICY),
            'row_content_sha256': {r['id']: sha_json(r) for r in sorted(rows, key=lambda r: r['id'])}}


def authority_records(binding, findings, config, owner_sha256, timestamp):
    if owner_sha256 != OWNER_REQUEST_SHA256:
        raise DataError('EXPLICIT_OWNER_AUTHORIZATION_BINDING_REQUIRED')
    if set(findings) != {'clinc', 'massive', 'slurp'}:
        raise DataError('AUTHORITATIVE_COLLECTION_EVIDENCE_REQUIRED')
    shared = {'binding': binding, 'operator_id': OPERATOR,
        'operator_identity_basis': 'PROJECT_OWNER_ROLE_EXPLICITLY_ASSERTED_IN_BOUND_CURRENT_REQUEST_NOT_AN_ENROLLED_SIGNER',
        'owner_request_sha256': owner_sha256, 'recorded_authorization_at_utc': timestamp,
        'publication_allowed': False, 'canonical_release_authorized': False,
        'individual_privacy_review_claimed': False, 'cryptographic_signature': None,
        'trust_basis': 'EXISTING_TRUSTED_OPERATOR_CONFIGURATION_BOUND_TO_EXPLICIT_OWNER_REQUEST'}
    privacy = {**copy.deepcopy(shared), 'kind': 'SOURCE_LEVEL_PRIVACY_REVIEW',
        'classification': 'SOURCE_LEVEL_RESTRICTED_TRAINING', 'row_privacy': 'RESTRICTED',
        'collection_evidence': findings, 'evidence_manifest_sha256': packet.file_hash(collection_evidence.FOLDER / 'manifest.json'),
        'reason': 'Owner-authorized policy003 assessment of independently documented benchmark elicitation/localization. No PUBLIC, individual consent, individual privacy review or identifier-free claim.'}
    privacy['review_id'] = 'source-privacy-003:' + sha_json(privacy)
    training = {**copy.deepcopy(shared), 'kind': 'TRAINING_AUTHORIZATION',
        'status': 'APPROVED', 'source_privacy_review_sha256': sha_json(privacy),
        'allowed_purposes': config['allowed_purposes'], 'denied_purposes': config['denied_purposes'],
        'reason': 'Explicit owner authorization in bound request TASK 5 for eligible public training-source rows; no model execution requested.'}
    training['approval_id'] = 'training-003:' + sha_json(training)
    return privacy, training


def intake_record(binding, privacy, observed):
    if privacy['classification'] != 'SOURCE_LEVEL_RESTRICTED_TRAINING' or privacy['publication_allowed'] is not False:
        raise DataError('VALID_RESTRICTED_PRIVACY_PREREQUISITE_REQUIRED')
    source = REGISTRY[binding['source']]
    result = {'source': binding['source'], 'version': source['version'], 'revision': source['revision'],
        'license_id': source['license']['id'], 'mapper_version': MAPPER_VERSION, 'status': 'APPROVED',
        'license_verified': True, 'identity_verified': True, 'split_verified': True, 'privacy_reviewed': True,
        'archive_sha256': observed['sha256'], 'member_sha256': observed['member_sha256'],
        'archive_size_bytes': observed['size_bytes'], 'license_sha256': observed['license_evidence_sha256'],
        'member_path': observed['member_path'], 'locale': observed['locale'], 'source_revision': source['revision'],
        'requested_url': observed['requested_url'], 'resolved_url': observed['resolved_url'],
        'downloaded_at_utc': observed['downloaded_at_utc'], 'original_split': 'MIXED_OFFICIAL' if len(binding['eligible_splits']) > 1 else binding['eligible_splits'][0],
        'scope': 'EXACT_BOUND_CANDIDATE_MEMBERSHIP_AND_ELIGIBLE_SPLITS_ONLY', 'binding': binding,
        'privacy_review_sha256': sha_json(privacy), 'operator_id': privacy['operator_id'],
        'owner_request_sha256': privacy['owner_request_sha256'],
        'recorded_authorization_at_utc': privacy['recorded_authorization_at_utc'], 'reason': INTAKE_REASON,
        'publication_allowed': False, 'training_authorized_by_intake': False, 'canonical_release_authorized': False}
    result['approval_id'] = 'intake-003:' + sha_json(result)
    return result


def apply_restricted_snapshot(original, privacy, training, intake):
    binding = privacy['binding']
    if binding['row_content_sha256'].get(original['id']) != sha_json(original) or training['binding'] != binding or intake['binding'] != binding:
        raise DataError('SOURCE_AUTHORITY_MEMBERSHIP_OR_CONTENT_CHANGED')
    if training['source_privacy_review_sha256'] != sha_json(privacy) or intake['privacy_review_sha256'] != sha_json(privacy):
        raise DataError('SOURCE_AUTHORITY_RECORD_BINDING_CHANGED')
    row = copy.deepcopy(original)
    row['source']['intake_approval_id'] = intake['approval_id']
    row['privacy_review'] = None
    row['metadata']['source_governance_003'] = {'original_row_content_sha256': sha_json(original),
        'source_privacy_review_sha256': sha_json(privacy), 'training_authorization_sha256': sha_json(training),
        'intake_authorization_sha256': sha_json(intake), 'authority_level': 'SOURCE_LEVEL_NOT_INDIVIDUAL_REVIEW'}
    append_event(row, row['review_status'], row['review_status'], OPERATOR, 'SOURCE_LEVEL_AUTHORITY_003_BOUND')
    validate_record(row)
    proposal = copy.deepcopy(row)
    proposal['privacy'] = 'RESTRICTED'
    native_evidence = {'binding_sha256': facts_hash(proposal), 'review_id': privacy['review_id'],
        'reviewer_id': OPERATOR, 'reviewed_at': privacy['recorded_authorization_at_utc'],
        'publication_allowed': False, 'license_id': binding['license_id']}
    result = classify_privacy(row, 'RESTRICTED', native_evidence)
    if result['review_status'] != original['review_status'] or result['quarantine_reasons'] != original['quarantine_reasons']:
        raise DataError('SOURCE_PRIVACY_MUST_NOT_RELEASE_OR_CLEAR_QUARANTINE')
    return result


def run(destination=DESTINATION):
    destination = Path(destination)
    if destination.exists() or (DATASETS / 'quarantine').resolve() not in destination.resolve().parents:
        raise DataError('NEW_LOCAL_RESTRICTED_RUN_REQUIRED')
    config = policy()
    directive = OWNER_REQUEST.read_bytes()
    if packet.file_hash(OWNER_REQUEST) != OWNER_REQUEST_SHA256:
        raise DataError('OWNER_DIRECTIVE_CHANGED')
    context = review_recording.load_context()
    signed = review_recording.validate_batch(context, review_recording.FOLDER, 'human-review-001')
    if not signed['complete']:
        raise DataError('SIGNED_ORIGINAL_SEMANTIC_REVIEW_INVALID')
    findings = collection_findings()
    evidence = PublicEvidence()
    if evidence.errors:
        raise DataError('PUBLIC_SOURCE_EVIDENCE_REVERIFICATION_FAILED')
    original = list(context['rows'].values())
    groups_file = packet.read_json(packet.RUN / 'group_registry.json')
    groups = (groups_file['families'], groups_file['scenarios'])
    now = datetime.now(timezone.utc).isoformat()
    derived, records = [], {}
    for source in config['scope']:
        subset = [r for r in original if r['source']['dataset'] == source]
        binding = source_binding(source, subset, evidence, config)
        privacy, training = authority_records(binding, findings, config, OWNER_REQUEST_SHA256, now)
        intake = intake_record(binding, privacy, evidence.sources[source]['observation'])
        base = DATASETS / 'raw' / ('v0_' + source)
        approve_intake(source, base / evidence.sources[source]['observation']['member_file'], intake)
        records[source] = {'privacy': privacy, 'training': training, 'intake': intake}
        derived.extend(apply_restricted_snapshot(r, privacy, training, intake) for r in subset)
    print('{"stage":"restricted_privacy_native_intake_and_training_authority_verified","rows":'+str(len(derived))+'}', flush=True)
    full_vectors = packet.read_json(packet.RUN / 'embeddings.json')
    verify_binding(original, full_vectors, packet.read_json(packet.RUN / 'embedding_receipt.json'))
    vectors = copy.deepcopy(full_vectors)
    public_ids = {r['id'] for r in derived}
    vectors['rows'] = [v for v in vectors['rows'] if v['id'] in public_ids]
    audited, dedupe = audit(derived, vectors, groups=groups)
    verify_report(derived, dedupe)
    matched = {rid for m in dedupe['matches'] for rid in m['row_ids']}
    # Preserve conservative findings against excluded original/backlog neighbors.
    original_dedupe = packet.read_json(packet.RUN / 'dedupe_report.json')
    matched |= {rid for m in original_dedupe['matches'] for rid in m['row_ids']} & public_ids
    family_ids = {r['id'] for r in derived if not r['family'] or not r['scenario_id'] or not r['semantic_parent_id']}
    selected = [r for r in derived if r['id'] not in matched | family_ids]
    coverage = coverage_report(selected)
    preview_assignments, leakage = plan(selected)
    # This is grouping readiness, not authorization or a dataset partition bundle.
    inventory = {}
    for name in ('massive', 'clinc'):
        mapping = yaml.load((ROOT / f'ml/nlu_v1/ingestion/mappings/{name}_intent_mapping.yaml').read_text(encoding='utf-8'), Loader=UniqueLoader)
        inventory[name] = {domain: [intent for intent, m in mapping['intents'].items() if m.get('domain') == domain]
                           for domain in ('TASK', 'MEMORY', 'PROMISE', 'GOAL', 'PROJECT', 'PLANNING', 'FOCUS', 'HABIT')}
    coverage_blocked = bool(coverage['missing_candidate_groups'])
    summary = {'policy_version': config['policy_version'], 'source_classification': {s:'SOURCE_LEVEL_RESTRICTED_TRAINING' for s in records},
        'publication_allowed': False, 'intake_authorized_rows': len(derived), 'training_authorized_rows': len(derived),
        'restricted_snapshot_rows': len(derived), 'original_rows_changed': 0, 'synthetic_excluded': 425,
        'duplicate_similarity_exclusions': len(matched), 'family_scenario_exclusions': len(family_ids),
        'exclusion_union_public': len(matched | family_ids), 'retained_curation_candidates': len(selected),
        'rows_excluded_backlog': 425 + len(matched | family_ids), 'rows_not_in_final_dataset': len(original),
        'canonical_provenance_pass_rows': sum('SOURCE_ARTIFACT_UNVERIFIED' not in canonical_issues(r,groups) for r in derived),
        'missing_candidate_coverage_groups': coverage['missing_candidate_groups'], 'coverage_passed': not coverage_blocked,
        'grouping_preview_leakage_passed': leakage['leakage_passed'], 'grouping_preview_counts': leakage['counts'],
        'dataset_v0_rows': 0, 'split_counts': {p:0 for p in ('TRAIN','DEVELOPMENT','CALIBRATION','SELECTION')},
        'partitions_generated': False, 'leakage_passed_for_dataset': False,
        'dataset_v0_frozen': False, 'plan07_complete': False, 'model_trained': False,
        'smallest_remaining_blocker': 'REQUIRED_CAPABILITY_COVERAGE_UNAVAILABLE_UNDER_APPROVED_SOURCE_MAPPINGS',
        'canonical_rows_approved': 0, 'individual_privacy_reviews_created': 0, 'original_signatures_reused_for_changed_facts': False}
    if not coverage_blocked:
        raise DataError('CANONICAL_RESTRICTED_BUILD_GATES_REQUIRE_EXPLICIT_VALIDATION_NOT_ASSUMED')
    files = {'owner_directive.txt':directive, 'summary.json':json_bytes(summary), 'source_authorities.json':json_bytes(records),
        'collection_findings.json':json_bytes(findings), 'restricted_candidates.jsonl':jsonl_bytes(derived),
        'reaudited_quarantined.jsonl':jsonl_bytes(audited), 'dedupe_report.json':json_bytes(dedupe),
        'coverage_report.json':json_bytes(coverage), 'distribution_report.json':json_bytes(distribution(selected)),
        'grouped_split_preview.json':json_bytes({'assignments':preview_assignments,'report':leakage,'status':'PLANNING_ONLY_NOT_APPROVED_DATASET'}),
        'source_mapping_coverage_inventory.json':json_bytes(inventory),
        'selection_exclusions.json':json_bytes({'duplicate_similarity_row_ids':sorted(matched),'family_scenario_row_ids':sorted(family_ids),'synthetic_rows_excluded':425}),
        'embedding_subset_derivation.json':json_bytes({'parent_artifact_sha256':packet.file_hash(packet.RUN/'embeddings.json'),
            'parent_receipt_sha256':packet.file_hash(packet.RUN/'embedding_receipt.json'), 'subset_sha256':sha_json(vectors),
            'selected_row_ids':sorted(public_ids),'inference_rerun':False,'exact_text_and_vectors_preserved':True})}
    destination.mkdir()
    for name,data in files.items():
        atomic_new(destination/name,data)
    inputs = {**context['source_hashes'], 'ml/nlu_v1/configs/privacy_policy_003.yaml':packet.file_hash(POLICY),
        'ml/nlu_v1/dataset_v0/restricted_training.py':packet.file_hash(__file__),
        'ml/nlu_v1/datasets/raw/plan07-collection-evidence-003/manifest.json':packet.file_hash(collection_evidence.FOLDER/'manifest.json'),
        'ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/quarantined.jsonl':packet.file_hash(packet.RUN/'quarantined.jsonl')}
    atomic_new(destination/'manifest.json',json_bytes({'version':'nlu_v1_restricted_training_run_003','input_sha256':inputs,
        'files':{name:packet.file_hash(destination/name) for name in files},'dataset_v0_frozen':False,'model_trained':False}))
    if review_recording.validate_batch(review_recording.load_context(), review_recording.FOLDER,'human-review-001') != signed:
        raise DataError('ORIGINAL_SIGNED_REVIEW_CHANGED')
    return summary


def main():
    argparse.ArgumentParser(description=__doc__).parse_args()
    import json
    print(json.dumps(run(), sort_keys=True))


if __name__ == '__main__':
    main()
