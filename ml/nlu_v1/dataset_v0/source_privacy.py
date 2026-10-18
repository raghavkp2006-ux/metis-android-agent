"""Prospective policy-002 evidence assessment. Never issues a privacy approval."""
import argparse
from collections import Counter
import copy
import hashlib
from pathlib import Path

import yaml
from ml.nlu_v1.source_registry import ROOT, UniqueLoader
from ml.nlu_v1.dataset_v0 import exception_packet as packet, review_recording
from ml.nlu_v1.dataset_v0.machine_curation import PublicEvidence
from ml.nlu_v1.data_management.records import DataError, sha_json
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes

POLICY = ROOT / 'ml/nlu_v1/configs/privacy_policy_002.yaml'
OUTPUT = DATASETS / 'quarantine/plan07-privacy-policy-002-assessment-001'
REQUIREMENTS = (
    'exact_source_identity', 'exact_version_revision', 'exact_training_split',
    'verified_artifact_hash', 'verified_source_provenance', 'verified_license_evidence',
    'public_benchmark_research_source', 'no_metis_private_account_collection',
    'no_connector_private_device_data', 'no_protected_evaluation_split',
    'source_collection_privacy_documentation', 'bound_source_version_hash_split',
    'publication_separate_and_false', 'training_authorization_separate')


def load_policy():
    value = yaml.load(POLICY.read_text(), Loader=UniqueLoader)
    if value['policy_version'] != 'nlu_v1_privacy_policy_002' or set(value['scope']) != {'massive_1_1_en_us', 'clinc150_full'} or tuple(value['required_evidence']) != REQUIREMENTS:
        raise DataError('PRIVACY_POLICY_002_SCOPE_OR_GATES_CHANGED')
    if any(value['application'][k] is not False for k in ('publication_allowed', 'training_authorized', 'canonical_release_authorized', 'individual_human_review_claim_allowed')):
        raise DataError('PRIVACY_POLICY_002_AUTHORITY_EXPANSION')
    return value


def finding(matrix, *, restricted=False):
    if set(matrix) != set(REQUIREMENTS):
        raise DataError('PRIVACY_ASSESSMENT_GATES_INCOMPLETE')
    if any(e.get('status') != 'VERIFIED' or not e.get('evidence') for e in matrix.values()):
        return 'INSUFFICIENT_EVIDENCE'
    return 'SOURCE_LEVEL_RESTRICTED' if restricted else 'SOURCE_LEVEL_PUBLIC'


def assess():
    policy = load_policy()
    context = review_recording.load_context()
    signed = review_recording.validate_batch(context, review_recording.FOLDER, 'human-review-001')
    if not signed['complete']:
        raise DataError('CURRENT_SEMANTIC_REVIEW_INVALID')
    evidence = PublicEvidence()
    if evidence.errors:
        raise DataError('PUBLIC_SOURCE_EVIDENCE_INVALID')
    paths = [POLICY, Path(__file__), ROOT / 'ml/nlu_v1/configs/dataset_sources.yaml',
             ROOT / 'ml/nlu_v1/configs/dataset_source_evidence.json', packet.RUN / 'group_review_manifest.json',
             packet.RUN / 'quarantined.jsonl', packet.RUN / 'dedupe_report.json',
             ROOT / 'ml/nlu_v1/data_management/records.py', ROOT / 'ml/nlu_v1/dataset_v0/acquire.py']
    rows = list(context['rows'].values())
    assessments = []
    metadata = packet.read_json(ROOT / 'ml/nlu_v1/configs/dataset_source_evidence.json')
    for source_id, pin in policy['scope'].items():
        source = evidence.sources[source_id]
        observation = source['observation']
        base = DATASETS / 'raw' / ('v0_' + source_id)
        artifact = base / observation['member_file']
        paths += [base / 'observation.json', base / 'license-evidence.txt', artifact]
        subset = sorted([r for r in rows if r['source']['dataset'] == source_id], key=lambda r: r['id'])
        if not subset or any(observation[k] != pin[p] for k, p in (
            ('source_version', 'version'), ('revision', 'revision'), ('sha256', 'archive_sha256'), ('member_sha256', 'member_sha256'))):
            raise DataError('POLICY_002_EXACT_SOURCE_PIN_MISMATCH')
        for row in subset:
            native = copy.deepcopy(row)
            for namespace in ('machine_curation', 'semantic_group_curation'):
                native['metadata'].pop(namespace, None)
            evidence.bind(native)
            if row['source']['original_split'] not in pin['original_splits'] or row['source']['source_class'] != 'PUBLIC_DATASET':
                raise DataError('POLICY_002_PROTECTED_OR_NONPUBLIC_ROW')
        refs = [{'path': (base / 'observation.json').relative_to(ROOT).as_posix(), 'sha256': packet.file_hash(base / 'observation.json')},
                {'path': 'ml/nlu_v1/configs/dataset_sources.yaml', 'sha256': packet.file_hash(ROOT / 'ml/nlu_v1/configs/dataset_sources.yaml')}]
        matrix = {key: {'status': 'VERIFIED', 'evidence': copy.deepcopy(refs)} for key in REQUIREMENTS}
        # Verified ingestion/native-row equality proves this scoped pool comes
        # from the public acquisition, not METIS accounts or connector content.
        # It does not prove the upstream collector's confidentiality practices.
        matrix['verified_source_provenance']['evidence'].append({'native_member_sha256': observation['member_sha256'], 'exact_candidate_membership_sha256': sha_json([[r['id'], sha_json(r)] for r in subset])})
        matrix['verified_license_evidence']['evidence'] = [{'path': (base / 'license-evidence.txt').relative_to(ROOT).as_posix(), 'sha256': observation['license_evidence_sha256']}]
        for key in ('publication_separate_and_false', 'training_authorization_separate'):
            matrix[key]['evidence'] = [{'path': POLICY.relative_to(ROOT).as_posix(), 'sha256': packet.file_hash(POLICY)}]
        metadata_key = 'massive' if source_id.startswith('massive') else 'clinc'
        readme = next(m for m in metadata['sources'][metadata_key]['metadata'] if m['path'] == 'README.md')
        # Search the established raw acquisition area for exact pinned README
        # bytes. A metadata digest without its document is not content evidence.
        local = [p for p in base.rglob('*') if p.is_file() and 'readme' in p.name.lower() and packet.file_hash(p) == readme['sha256']]
        matrix['source_collection_privacy_documentation'] = {
            'status': 'MISSING' if not local else 'REQUIRES_DOCUMENT_CONTENT_ASSESSMENT',
            'evidence': [], 'pinned_readme': readme,
            'reason': 'No locally available verified source collection/privacy documentation establishes policy rule 11. License/notice bytes alone are insufficient.',
            'verified_local_readme_paths': [p.relative_to(ROOT).as_posix() for p in local]}
        assessments.append({'source_id': source_id, 'version': pin['version'], 'revision': pin['revision'],
            'artifact_sha256': pin['archive_sha256'], 'member_sha256': pin['member_sha256'],
            'splits_covered': sorted({r['source']['original_split'] for r in subset}),
            'candidate_rows': len(subset), 'row_content_sha256': {r['id']: sha_json(r) for r in subset},
            'requirements': matrix, 'privacy_finding': finding(matrix),
            'evidence_status': 'INCOMPLETE_SOURCE_COLLECTION_PRIVACY_EVIDENCE',
            'confidence': 'NO_PRIVACY_CLEARANCE_ESTABLISHED',
            'rule_applied': policy['policy_version'] + ':all_14_required:rule_11',
            'publication_allowed': False, 'training_authorized': False, 'approval_issued': False})
    hashes = {p.relative_to(ROOT).as_posix(): packet.file_hash(p) for p in paths}
    dedupe = packet.read_json(packet.RUN / 'dedupe_report.json')
    suspected = {rid for m in dedupe['matches'] for rid in m['row_ids']}
    excluded = []
    for row in rows:
        reasons = []
        if row['source']['source_class'] == 'CONTROLLED_SYNTHETIC':
            reasons.append('OWNER_EXCLUDED_SYNTHETIC_PENDING_RIGHTS')
        else:
            reasons.append('SOURCE_POLICY_002_INSUFFICIENT_EVIDENCE')
        if not row['family'] or not row['scenario_id']:
            reasons.append('UNRESOLVED_FAMILY_SCENARIO')
        if row['id'] in suspected:
            reasons.append('DUPLICATE_SIMILARITY_EXCLUDED_FROM_V0_PRESERVED')
        excluded.append({'row_id': row['id'], 'row_content_sha256': sha_json(row), 'reasons': reasons})
    summary = {'policy_version': policy['policy_version'], 'rows_analyzed': len(rows),
        'source_results': {a['source_id']: a['privacy_finding'] for a in assessments},
        'synthetic_excluded': sum(r['source']['source_class'] == 'CONTROLLED_SYNTHETIC' for r in rows),
        'rows_eligible_after_privacy_intake': 0, 'rows_excluded_quarantined': len(rows),
        'existing_public_rows_preserved': sum(r['privacy'] == 'PUBLIC' for r in rows),
        'family_scenario_backlog_rows': sum(not r['family'] or not r['scenario_id'] for r in rows),
        'similarity_backlog_rows': len(suspected), 'similarity_findings': dict(Counter(m['check'] for m in dedupe['matches'])),
        'dataset_v0_rows': 0, 'split_counts': {k: 0 for k in ('TRAIN', 'DEVELOPMENT', 'CALIBRATION', 'SELECTION')},
        'splits_generated': False, 'coverage_passed': False, 'leakage_passed': False,
        'training_authorization_for_included_rows': False, 'dataset_v0_frozen': False, 'plan07_complete': False,
        'smallest_remaining_blocker': 'SOURCE_COLLECTION_PRIVACY_EVIDENCE_MISSING_FOR_POLICY_002_RULE_11',
        'individual_privacy_decisions_created': 0, 'source_privacy_approvals_created': 0, 'intake_approvals_created': 0,
        'privacy_states_changed': 0, 'rows_released': 0, 'model_trained': False, 'final_independent': 'RESERVED_UNTOUCHED'}
    for name, digest in hashes.items():
        if packet.file_hash(ROOT / name) != digest:
            raise DataError('ASSESSMENT_INPUT_CHANGED')
    return {'summary': summary, 'sources': assessments, 'excluded_rows': excluded, 'input_sha256': hashes}


def write(result, destination=OUTPUT):
    destination = Path(destination)
    if destination.exists() or (DATASETS / 'quarantine').resolve() not in destination.resolve().parents:
        raise DataError('NEW_LOCAL_ASSESSMENT_OUTPUT_REQUIRED')
    destination.mkdir()
    data = json_bytes(result)
    atomic_new(destination / 'assessment.json', data)
    atomic_new(destination / 'manifest.json', json_bytes({'policy_version': 'nlu_v1_privacy_policy_002',
        'assessment_sha256': hashlib.sha256(data).hexdigest(), 'input_sha256': result['input_sha256'],
        'approval_issued': False, 'dataset_v0_frozen': False}))


def main():
    argparse.ArgumentParser(description=__doc__).parse_args()
    result = assess()
    write(result)
    import json
    print(json.dumps(result['summary'], sort_keys=True))


if __name__ == '__main__':
    main()
