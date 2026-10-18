"""Plan 07 semantic review preparation. Never trains or freezes a dataset."""
import argparse
import hashlib
import json
from pathlib import Path

import yaml
from ml.nlu_v1.dataset_v0 import machine_curation as machine
from ml.nlu_v1.dataset_v0 import semantic_groups as semantic
from ml.nlu_v1.dataset_v0.coverage_additions import coverage_report, TEMPLATES, COVERAGE
from ml.nlu_v1.dataset_v0.similarity_audit import verify_binding
from ml.nlu_v1.data_management.dedupe import audit, collection_hash, verify_report
from ml.nlu_v1.data_management.records import DataError, load_groups, sha_json, validate_groups
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes
from ml.nlu_v1.splitting.split import INELIGIBLE, connected_groups, leakage_report, read_jsonl, official
from ml.nlu_v1.source_registry import ROOT, UniqueLoader

VERSION = 'nlu_v1_group_review_run_001'


def merge_groups(target, source):
    for index, field in ((0, 'families'), (1, 'scenarios')):
        for key, value in source[index][field].items():
            if key in target[index][field] and target[index][field][key] != value:
                raise DataError('GROUP_REGISTRY_COLLISION')
            target[index][field][key] = value
    validate_groups(*target)
    return target


def prepare(stage, embedding_dir):
    start_hashes = {**semantic.hashes(), 'dataset_v0/group_review.py': machine.file_hash(__file__),
                    'dataset_v0/similarity_audit.py': machine.file_hash(Path(__file__).with_name('similarity_audit.py'))}
    stage, embedding_dir = Path(stage), Path(embedding_dir)
    rows = read_jsonl(stage / 'full_embedding_input.jsonl')
    if any(official(r) or r['partition'] in ('PROTECTED_EVALUATION', 'FINAL_INDEPENDENT_TEST') or r['source']['source_class'] not in ('PUBLIC_DATASET', 'CONTROLLED_SYNTHETIC') for r in rows):
        raise DataError('PROTECTED_OR_NONPUBLIC_ROWS_FORBIDDEN_IN_CANDIDATE_COLLECTION')
    artifact = json.loads((embedding_dir / 'embeddings.json').read_text())
    receipt = json.loads((embedding_dir / 'embedding_receipt.json').read_text())
    verify_binding(rows, artifact, receipt)
    from ml.nlu_v1.context_contract import check
    check()
    evidence = machine.PublicEvidence()
    groups = load_groups()
    supplemental = yaml.load(machine.FAMILY_POLICY.read_text(encoding='utf-8'), Loader=UniqueLoader)
    groups[0]['families'].update(supplemental['families'])
    historical = json.loads((DATASETS / 'raw/plan05-seed17.groups.json').read_text())
    groups = merge_groups(groups, (historical['families'], historical['scenarios']))
    extension = json.loads((stage / 'synthetic_groups.json').read_text())
    groups = merge_groups(groups, (extension['families'], extension['scenarios']))
    revised, curation_reasons = [], {}
    for row in rows:
        if row['source']['source_class'] == 'PUBLIC_DATASET':
            revised_row, reasons, groups, _ = machine.curate_row(row, groups, evidence)
            revised.append(revised_row)
            curation_reasons[row['id']] = reasons
        else:
            revised.append(row)
    print(json.dumps({'stage': 'semantic_source_and_family_checks', 'rows': len(revised)}), flush=True)
    revised, _, _, _, _ = semantic.create_groups(revised, groups, evidence)
    print(json.dumps({'stage': 'full_cosine_audit', 'rows': len(revised), 'model': artifact['model_id']}), flush=True)
    audited, dedupe = audit(revised, artifact, groups=groups)
    verify_report(revised, dedupe)
    # Bind decisions to the post-audit current review states. Do not clear any
    # previous quarantine reason, including near-duplicate findings.
    final, reviews, exceptions, exception_tickets, summary = semantic.create_groups(audited, groups, evidence, dedupe)
    if final != audited:
        raise DataError('GROUP_PREPARATION_CHANGED_POST_AUDIT_FACTS')
    resolved = [r for r in final if r['family'] and r['scenario_id'] and r['semantic_parent_id']]
    assignments = {r['id']: r['partition'] if r['partition'] != 'UNASSIGNED' else INELIGIBLE for r in resolved}
    leakage = leakage_report(resolved, assignments)
    leakage['full_collection_grouping_complete'] = len(resolved) == len(final)
    leakage['scientific_split_readiness'] = False
    leakage['unresolved_group_rows'] = len(final) - len(resolved)
    leakage['connected_components'] = len(connected_groups(resolved))
    coverage = coverage_report(final)
    original = {r['id'] for r in read_jsonl(DATASETS / 'quarantine/plan07_public_candidates.jsonl')}
    summary['original_input_rows'] = len(original)
    summary['full_candidate_rows'] = len(final)
    summary['original_rows_covered_by_group_review'] = sum(len(set(g['row_ids']) & original) for g in reviews)
    summary['original_rows_requiring_individual_review'] = sum(e['row_id'] in original for e in exceptions)
    summary['original_grouped_tickets'] = sum(bool(set(g['row_ids']) & original) for g in reviews)
    summary['original_exception_tickets'] = sum(bool(set(t['row_ids']) & original) for t in exception_tickets)
    summary['public_additions'] = len(read_jsonl(stage / 'public_additions.jsonl'))
    summary['controlled_additions'] = len(read_jsonl(stage / 'synthetic_additions.jsonl'))
    summary['embedding_similarity'] = dedupe['checks']['embedding_similarity']
    summary['duplicate_match_groups'] = len(dedupe['matches'])
    summary['missing_candidate_coverage_groups'] = coverage['missing_candidate_groups']
    summary['missing_approved_coverage_groups'] = coverage['missing_approved_groups']
    summary['blockers'] = ['EXTERNAL_HUMAN_GROUP_APPROVAL_REQUIRED', 'EXCEPTIONS_UNRESOLVED', 'DUPLICATE_REVIEW_REQUIRED',
        'INTAKE_APPROVAL_REQUIRED', 'PUBLICATION_RIGHTS_APPROVAL_REQUIRED', 'FRESH_FINAL_INDEPENDENT_RESERVED', 'APPROVED_COVERAGE_REQUIRED']
    if not leakage['full_collection_grouping_complete']:
        summary['blockers'].append('UNRESOLVED_FAMILY_SCENARIO_GROUPS')
    if coverage['missing_candidate_groups']:
        summary['blockers'].append('CANDIDATE_COVERAGE_MINIMA_UNMET')
    summary.update(dataset_v0_frozen=False, plan07_complete=False, can_start_plan08=False,
                   source_intake_approvals_created=0, privacy_rights_approvals_created=0)
    if start_hashes != {**semantic.hashes(), 'dataset_v0/group_review.py': machine.file_hash(__file__),
                        'dataset_v0/similarity_audit.py': machine.file_hash(Path(__file__).with_name('similarity_audit.py'))}:
        raise DataError('CURATION_CODE_CHANGED_DURING_RUN')
    return {'rows': final, 'review_groups': reviews, 'exceptions': exceptions, 'exception_tickets': exception_tickets,
            'summary': summary, 'coverage': coverage, 'dedupe': dedupe, 'leakage': leakage, 'groups': groups,
            'curation_reasons': curation_reasons, 'embedding_receipt': receipt, 'artifact': artifact,
            'audit_input_rows': revised, 'code_hashes': start_hashes,
            'source_search': json.loads((stage / 'public_source_search_report.json').read_text())}


def refuse_freeze(result):
    if result['summary']['blockers'] or not result['summary']['approved_rows']:
        raise DataError('GROUP_QUEUE_IS_NOT_AN_APPROVED_DATASET')


def write(result, destination):
    destination = Path(destination)
    if destination.exists():
        raise DataError('GROUP_REVIEW_RUN_ALREADY_EXISTS')
    # This writer's scope is the ignored NLU quarantine tree.
    if (DATASETS / 'quarantine').resolve() not in destination.resolve().parents:
        raise DataError('GROUP_RUN_MUST_STAY_IN_QUARANTINE')
    files = {'semantic_review_groups.json': json_bytes(result['review_groups']),
             'semantic_review_summary.json': json_bytes(result['summary']),
             'semantic_review_exceptions.jsonl': jsonl_bytes(result['exceptions']),
             'exception_review_tickets.json': json_bytes(result['exception_tickets']),
             'group_approval_template.json': json_bytes([semantic.approval_template(g) for g in result['review_groups']]),
             'quarantined.jsonl': jsonl_bytes(result['rows']),
             'audit_input_rows.jsonl': jsonl_bytes(result['audit_input_rows']),
             'coverage_report.json': json_bytes(result['coverage']),
             'dedupe_report.json': json_bytes(result['dedupe']), 'leakage_report.json': json_bytes(result['leakage']),
             'group_registry.json': json_bytes({'families': result['groups'][0], 'scenarios': result['groups'][1]}),
             'curation_reason_codes.json': json_bytes(result['curation_reasons']),
             'public_source_search_report.json': json_bytes(result['source_search']),
             'embeddings.json': json_bytes(result['artifact']), 'embedding_receipt.json': json_bytes(result['embedding_receipt']),
             'dataset_v0_coverage_policy.yaml': COVERAGE.read_bytes(), 'coverage_templates.yaml': TEMPLATES.read_bytes(),
             'semantic_group_policy.yaml': semantic.POLICY.read_bytes(), 'group_reviewers.yaml': semantic.REVIEWERS.read_bytes()}
    code_hashes = result['code_hashes']
    if any(machine.file_hash(ROOT / 'ml/nlu_v1' / name) != digest for name, digest in code_hashes.items()):
        raise DataError('CURATION_CODE_CHANGED_BEFORE_COMMIT')
    for name in code_hashes:
        files['snapshot/' + name] = (ROOT / 'ml/nlu_v1' / name).read_bytes()
    manifest = {'manifest_version': VERSION, 'status': 'GROUP_REVIEW_PENDING_HUMAN', 'files': {name: hashlib.sha256(data).hexdigest() for name, data in files.items()},
                'code_config_mapping_sha256': code_hashes, 'input_collection_sha256': collection_hash(result['audit_input_rows']),
                'dataset_v0_frozen': False, 'training_authorized': False, 'final_independent': 'RESERVED_NOT_FABRICATED'}
    destination.mkdir(parents=True, exist_ok=False)
    for name, data in files.items():
        (destination / name).parent.mkdir(parents=True, exist_ok=True)
        atomic_new(destination / name, data)
    atomic_new(destination / 'group_review_manifest.json', json_bytes(manifest))
    return manifest


def verify(destination):
    destination = Path(destination)
    manifest = json.loads((destination / 'group_review_manifest.json').read_text())
    if manifest.get('manifest_version') != VERSION or manifest.get('status') != 'GROUP_REVIEW_PENDING_HUMAN' or manifest.get('dataset_v0_frozen') is not False or manifest.get('training_authorized') is not False or manifest.get('final_independent') != 'RESERVED_NOT_FABRICATED':
        raise DataError('INVALID_GROUP_REVIEW_MANIFEST')
    actual = {p.relative_to(destination).as_posix() for p in destination.rglob('*') if p.is_file()}
    if actual != set(manifest['files']) | {'group_review_manifest.json'}:
        raise DataError('GROUP_REVIEW_ARTIFACT_SET_CHANGED')
    for name, digest in manifest['files'].items():
        path = destination / name
        if destination.resolve() not in path.resolve().parents or machine.file_hash(path) != digest:
            raise DataError('GROUP_REVIEW_ARTIFACT_CHANGED')
    rows = read_jsonl(destination / 'quarantined.jsonl')
    originals = read_jsonl(destination / 'audit_input_rows.jsonl')
    dedupe = json.loads((destination / 'dedupe_report.json').read_text())
    verify_report(originals, dedupe)
    verify_binding(rows, json.loads((destination / 'embeddings.json').read_text()), json.loads((destination / 'embedding_receipt.json').read_text()))
    groups = json.loads((destination / 'semantic_review_groups.json').read_text())
    exceptions = [json.loads(line) for line in (destination / 'semantic_review_exceptions.jsonl').read_bytes().splitlines()]
    indexed = {r['id']: r for r in rows}
    covered = []
    for group in groups:
        payload = {key: group[key] for key in ('policy_version', 'identity', 'members', 'code_config_mapping_sha256')}
        if group['binding_sha256'] != sha_json(payload) or group['group_id'] != 'semantic-group:' + sha_json(payload):
            raise DataError('SEMANTIC_GROUP_BINDING_CHANGED')
        if any(sha_json(indexed[m['row_id']]) != m['row_content_sha256'] for m in group['members']):
            raise DataError('SEMANTIC_GROUP_MEMBER_CHANGED')
        if group['row_ids'] != [m['row_id'] for m in group['members']] or group['row_count'] != len(group['members']):
            raise DataError('SEMANTIC_GROUP_MEMBERSHIP_CHANGED')
        covered.extend(group['row_ids'])
    covered.extend(e['row_id'] for e in exceptions)
    summary = json.loads((destination / 'semantic_review_summary.json').read_text())
    if len(covered) != len(set(covered)) or set(covered) != set(indexed) or summary['approved_rows'] or summary['human_approvals_created'] or any(r['review_status'] != 'QUARANTINED' for r in rows):
        raise DataError('GROUP_REVIEW_PARTITION_OR_APPROVAL_INVALID')
    if summary['grouped_tickets'] != len(groups) or summary['individual_review_rows'] != len(exceptions) or summary['rows_covered_by_group_review'] != sum(g['row_count'] for g in groups):
        raise DataError('GROUP_REVIEW_COUNTS_CHANGED')
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path)
    parser.add_argument('--embeddings', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--verify', type=Path)
    parser.add_argument('--approval', type=Path)
    args = parser.parse_args()
    if args.verify:
        summary = verify(args.verify)
        if args.approval:
            approval = json.loads(args.approval.read_text())
            groups = json.loads((args.verify / 'semantic_review_groups.json').read_text())
            group = next((g for g in groups if g['group_id'] == approval.get('group_id')), None)
            if group is None:
                raise DataError('UNKNOWN_GROUP_APPROVAL')
            rows = [r for r in read_jsonl(args.verify / 'quarantined.jsonl') if r['id'] in group['row_ids']]
            print(json.dumps(semantic.verify_human_approval(group, approval, rows), sort_keys=True))
        else:
            print(json.dumps(summary, sort_keys=True))
    elif args.stage and args.embeddings and args.output:
        result = prepare(args.stage, args.embeddings)
        write(result, args.output)
        print(json.dumps(verify(args.output), sort_keys=True))
    else:
        parser.error('--stage, --embeddings and --output, or --verify required')


if __name__ == '__main__':
    main()
