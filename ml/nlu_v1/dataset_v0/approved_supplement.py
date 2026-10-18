"""Append-only execution of the signed finite template recipe; never trains models.

This is a candidate audit, not an alternate Plan 04 approval mechanism. Legacy
canonical findings are preserved verbatim. A failed gate never produces splits
or a frozen dataset, including when template semantics have human authority.
"""
import argparse
from collections import Counter
import copy
import hashlib
import json
from pathlib import Path

from ml.nlu_v1.dataset_v0 import template_review as review
from ml.nlu_v1.dataset_v0 import restricted_training as restricted
from ml.nlu_v1.dataset_v0 import exception_packet as packet
from ml.nlu_v1.dataset_v0.coverage_additions import coverage_report
from ml.nlu_v1.dataset_v0.similarity_audit import embed, verify_binding
from ml.nlu_v1.data_management.records import (
    DataError, append_event, canonical_issues, classify_privacy, facts_hash,
    quarantine, sha_json, text_hash, validate_groups, validate_record,
)
from ml.nlu_v1.data_management.dedupe import audit, verify_report
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes
from ml.nlu_v1.splitting.split import read_jsonl, plan, official, TUNING

DESTINATION = DATASETS / 'quarantine/plan07-approved-template-generation-002'
PRIOR_EXECUTION = DATASETS / 'quarantine/plan07-approved-template-generation-001'
BATCH = 'template-human-001'
VERSION = 'nlu_v1_approved_supplement_audit_002'


def verify_files(folder, *, inputs=False):
    manifest = packet.read_json(folder / 'manifest.json')
    for name, digest in manifest['files'].items():
        if Path(name).name != name or packet.file_hash(folder / name) != digest:
            raise DataError('SUPPLEMENT_INPUT_INTEGRITY_FAILED')
    if inputs:
        for name, digest in manifest.get('input_sha256', {}).items():
            if packet.file_hash(restricted.ROOT / name) != digest:
                raise DataError('SUPPLEMENT_BOUND_INPUT_CHANGED')
    return manifest


def approvals():
    receipt = review.verify_batch(review.FOLDER, BATCH)
    if not receipt['all_templates_approved']:
        raise DataError('ALL_EXACT_TEMPLATES_MUST_HAVE_HUMAN_APPROVAL')
    ctx = review.context()
    signed = review.FOLDER / ('signed-' + BATCH)
    records = {}
    for path in signed.glob('*.json'):
        if path.name == 'manifest.json':
            continue
        record = packet.read_json(path)
        review.verify_approval(record, ctx, require_approve=True)
        records[record['binding']['template_id']] = record
    if set(records) != set(ctx['templates']):
        raise DataError('APPROVED_TEMPLATE_MEMBERSHIP_INCOMPLETE')
    return ctx, records, receipt


def extend_groups(ctx, groups):
    groups = copy.deepcopy(groups)
    for t in ctx['templates'].values():
        family = dict(domain=t['domain'], action=t['action'],
                      required_slots=t['allowed_slots'], dispositions=[t['disposition']])
        scenario = dict(family=t['family'], semantic_parent_id=t['semantic_parent'],
                        description='Exact policy002 signed template; fixed fictional bindings only')
        for registry, section, key, value in (
            (groups[0], 'families', t['family'], family),
            (groups[1], 'scenarios', t['scenario_id'], scenario),
        ):
            if key in registry[section] and registry[section][key] != value:
                raise DataError('EXISTING_GROUP_DEFINITION_CANNOT_BE_REPLACED')
            registry[section][key] = value
    validate_groups(*groups)
    return groups


def make_row(ctx, template, approval, ordinal, groups):
    """The signed engine defines the recipe. No combinatorial/new paraphrases."""
    if type(ordinal) is not int or not 0 <= ordinal < template['planned_max_rows']:
        raise DataError('APPROVED_FINITE_ORDINAL_SPACE_EXHAUSTED')
    if approval['binding'] != review.binding(template, ctx) or approval['decision'] != 'APPROVE':
        raise DataError('TEMPLATE_APPROVAL_NOT_BOUND')
    prediction = review.preview(template, ordinal)
    source = dict(dataset=ctx['catalog']['source_id'], version=ctx['catalog']['catalog_version'],
        source_id=sha_json([sha_json(template), ordinal]), original_split='generated',
        original_intent=template['template_id'], mapper_version=template['generator_version'],
        mapping_version=template['template_version'],
        mapping_decision='OOD' if prediction['meaning']['ood'] else
            'KNOWN_UNSUPPORTED' if template['disposition'] == 'KNOWN_UNSUPPORTED' else 'MAP',
        artifact_sha256=packet.file_hash(review.CATALOG), intake_approval_id=None,
        source_class='CONTROLLED_SYNTHETIC')
    # Do not forge an intake approval ID: the legacy native source registry does
    # not yet register this new source. Its actual finding remains in the audit.
    row = dict(schema_version='nlu_v1_data_001', nlu_schema_version='nlu_v1_schema_001',
        id=hashlib.sha256(json.dumps([source[k] for k in
            ('dataset', 'version', 'original_split', 'source_id')], separators=(',', ':')).encode()).hexdigest(),
        text=prediction['text'], tokens=prediction['tokens'], bio_tags=prediction['bio_tags'],
        **prediction['meaning'], source=source, family=template['family'],
        scenario_id=template['scenario_id'], semantic_parent_id=template['semantic_parent'],
        family_registry_version='nlu_v1_families_001', scenario_registry_version='nlu_v1_scenarios_001',
        privacy='RESTRICTED', privacy_review=None, review_status='PENDING', review_history=[],
        quarantine_reasons=[], partition='UNASSIGNED', composition=None,
        generation=dict(parent_ids=[], seed=template['generation_seed'],
            generator_id=template['generator_version'], template_id=template['template_id']),
        text_sha256=text_hash(prediction['text']),
        metadata=dict(origin_capability_group=template['capability_group'],
            template_review=dict(ordinal=ordinal, template_sha256=sha_json(template),
                approval_sha256=sha_json(approval)),
            semantic_authority='EXACT_HUMAN_REVIEWED_TEMPLATE',
            individual_row_human_review_claimed=False, external_or_private_inputs=False,
            publication_allowed=False, synthetic_policy_sha256=packet.file_hash(review.POLICY)),
        provenance=dict(ingestion_row_sha256=sha_json(prediction), import_batch_id=VERSION,
            import_row_number=ordinal + 1, import_file_sha256=packet.file_hash(review.CATALOG),
            mapping_configuration_sha256=packet.file_hash(review.CATALOG),
            source_registry_sha256=packet.file_hash(restricted.ROOT / 'ml/nlu_v1/configs/dataset_sources.yaml'),
            source_registry_version='nlu_v1_sources_001', upstream_privacy='FIRST_PARTY_FICTIONAL_RESTRICTED',
            family_definition_sha256=sha_json(groups[0]['families'][template['family']]),
            scenario_definition_sha256=sha_json(groups[1]['scenarios'][template['scenario_id']])))
    append_event(row, None, 'PENDING', 'machine:approved-template-recipe', 'EXACT_APPROVED_TEMPLATE_RENDERED')
    # This is the policy's first-party restricted classification, not a claim
    # that a human individually reviewed privacy or licensed public publication.
    evidence = dict(binding_sha256=facts_hash(row),
        review_id='template-policy002:' + sha_json(template),
        reviewer_id='machine:synthetic-review-policy002', reviewed_at=row['review_history'][0]['at'],
        publication_allowed=False, license_id='LicenseRef-METIS-Internal-Training-Only')
    row = classify_privacy(row, 'RESTRICTED', evidence)
    validate_exact(row, template, approval, ctx)
    return quarantine(row, ['CANONICAL_SOURCE_INTAKE_PENDING', 'DEDUPE_LEAKAGE_PENDING'])


def validate_exact(row, template, approval, ctx):
    """Checks recipe only; does not imply full policy inheritance/release passes."""
    validate_record(row)
    recipe = row['metadata']['template_review']
    ordinal = recipe['ordinal']
    if type(ordinal) is not int or not 0 <= ordinal < template['planned_max_rows']:
        raise DataError('APPROVED_FINITE_ORDINAL_SPACE_EXHAUSTED')
    expected = review.preview(template, ordinal)
    if recipe != dict(ordinal=ordinal, template_sha256=sha_json(template), approval_sha256=sha_json(approval)):
        raise DataError('GENERATED_TEMPLATE_BINDING_CHANGED')
    if any(row[k] != expected[k] for k in ('text', 'tokens', 'bio_tags')) or any(
            row[k] != expected['meaning'][k] for k in ('domain', 'action', 'disposition', 'ood', 'slots')):
        raise DataError('GENERATED_ROW_NOT_EXACT_RECIPE')
    if (row['source']['dataset'], row['source']['version'], row['source']['source_id'],
            row['source']['original_split'], row['source']['original_intent']) != (
            ctx['catalog']['source_id'], ctx['catalog']['catalog_version'],
            sha_json([sha_json(template), ordinal]), 'generated', template['template_id']):
        raise DataError('GENERATED_SOURCE_PROVENANCE_CHANGED')
    if (row['family'], row['scenario_id'], row['semantic_parent_id'], row['generation']) != (
            template['family'], template['scenario_id'], template['semantic_parent'],
            dict(parent_ids=[], seed=template['generation_seed'],
                generator_id=template['generator_version'], template_id=template['template_id'])):
        raise DataError('GENERATED_LINEAGE_CHANGED')
    if row['privacy'] != 'RESTRICTED' or not row['privacy_review'] or row['privacy_review']['publication_allowed']:
        raise DataError('RESTRICTED_INTERNAL_ONLY_REQUIRED')
    if row['metadata'].get('individual_row_human_review_claimed') is not False:
        raise DataError('NO_FICTITIOUS_ROW_LEVEL_REVIEW')
    return True


def exclude_conflicts(public, generated, report):
    """All matched endpoints are excluded, preserving all rows as evidence."""
    verify_report(public + generated, report)
    matched = {ident for match in report['matches'] for ident in match['row_ids']}
    return ([r for r in public if r['id'] not in matched],
            [r for r in generated if r['id'] not in matched], matched)


def refuse_freeze(coverage, canonical_findings, split_report):
    if coverage['missing_candidate_groups']:
        group = min(coverage['missing_candidate_groups'], key=lambda g: (
            sum(coverage['groups'][g]['candidate']['deficit'].values()), g))
        return 'COVERAGE:' + group + ':' + json.dumps(coverage['groups'][group]['candidate']['deficit'], sort_keys=True)
    if canonical_findings:
        return 'CANONICAL_SOURCE_OR_RELEASE_AUTHORITY_UNRESOLVED'
    if not split_report['leakage_passed'] or any(not split_report['counts'].get(p) for p in TUNING):
        return 'FOUR_ELIGIBLE_NONEMPTY_GROUPED_PARTITIONS_REQUIRED'
    # A candidate preview is never freeze authority, even if scientific gates pass.
    return 'NATIVE_RESTRICTED_CANONICAL_RELEASE_AND_FREEZE_EVIDENCE_REQUIRED'


def run(destination=DESTINATION):
    destination = Path(destination)
    if (DATASETS / 'quarantine').resolve() not in destination.resolve().parents:
        raise DataError('LOCAL_QUARANTINE_OUTPUT_REQUIRED')
    ctx, signed, signed_receipt = approvals()
    if destination.exists():
        verify_files(destination, inputs=True)
        return packet.read_json(destination / 'summary.json')
    verify_files(restricted.DESTINATION, inputs=True)
    exclusions = packet.read_json(restricted.DESTINATION / 'selection_exclusions.json')
    excluded = set(exclusions['duplicate_similarity_row_ids']) | set(exclusions['family_scenario_row_ids'])
    public = [r for r in read_jsonl(restricted.DESTINATION / 'restricted_candidates.jsonl') if r['id'] not in excluded]
    if len(public) != 1762 or any(r['source']['dataset'] not in ('massive_1_1_en_us', 'clinc150_full') or
            r['source']['original_split'] not in ('train', 'oos_train') or official(r) or
            r['partition'] != 'UNASSIGNED' for r in public):
        raise DataError('VERIFIED_PUBLIC_POOL_CHANGED_OR_PROTECTED_ROW')
    evidence = restricted.PublicEvidence()
    if evidence.errors:
        raise DataError('PUBLIC_SOURCE_EVIDENCE_REVERIFICATION_FAILED')
    authorities = packet.read_json(restricted.DESTINATION / 'source_authorities.json')
    for source, records in authorities.items():
        observation = evidence.sources[source]['observation']
        restricted.approve_intake(source, DATASETS / 'raw' / ('v0_' + source) / observation['member_file'], records['intake'])
        if records['training']['status'] != 'APPROVED' or records['training']['publication_allowed'] is not False:
            raise DataError('PUBLIC_TRAINING_AUTHORITY_INVALID')
    for row in public:
        records = authorities[row['source']['dataset']]
        bound = row['metadata']['source_governance_003']
        if any(bound[key] != sha_json(records[name]) for key, name in (
                ('source_privacy_review_sha256', 'privacy'), ('training_authorization_sha256', 'training'),
                ('intake_authorization_sha256', 'intake'))) or row['privacy'] != 'RESTRICTED':
            raise DataError('PUBLIC_ROW_SOURCE_AUTHORITY_STALE')
    stored = packet.read_json(packet.RUN / 'group_registry.json')
    groups = extend_groups(ctx, (stored['families'], stored['scenarios']))
    generated = [make_row(ctx, t, signed[ident], i, groups)
                 for ident, t in ctx['templates'].items() for i in range(t['planned_max_rows'])]
    rows = public + generated
    print(json.dumps(dict(stage='exact_templates_rendered_schema_valid', public=len(public), generated=len(generated))), flush=True)
    if PRIOR_EXECUTION.exists():
        # The earlier execution remains immutable. Vectors bind exact IDs/text,
        # not import metadata; reuse requires the genuine original receipt and
        # pinned model verification against this complete ordered collection.
        verify_files(PRIOR_EXECUTION)
        vectors = packet.read_json(PRIOR_EXECUTION / 'embeddings.json')
        receipt = packet.read_json(PRIOR_EXECUTION / 'embedding_receipt.json')
    else:
        vectors, receipt = embed(rows)
    verify_binding(rows, vectors, receipt)
    print(json.dumps(dict(stage='real_offline_embedding_audit_ready', rows=len(rows))), flush=True)
    audited, dedupe = audit(rows, vectors, groups=groups)
    verify_report(rows, dedupe)
    kept_public, kept_generated, matches = exclude_conflicts(public, generated, dedupe)
    candidates = kept_public + kept_generated
    coverage = coverage_report(candidates)
    assignments, split_report = plan(candidates)
    canonical = [{'id': r['id'], 'issues': canonical_issues(r, groups)} for r in candidates]
    canonical = [entry for entry in canonical if entry['issues']]
    blocker = refuse_freeze(coverage, canonical, split_report)
    summary = dict(version=VERSION, signed_templates_verified=signed_receipt['valid_template_decisions'],
        synthetic_rows_generated=len(generated), synthetic_rows_schema_recipe_validated=len(generated),
        synthetic_rows_excluded=len(generated) - len(kept_generated), synthetic_candidates_retained=len(kept_generated),
        public_source_candidates_included=len(kept_public), public_rows_excluded=len(public) - len(kept_public),
        retained_curation_candidates=len(candidates), final_dataset_v0_rows=0,
        capability_counts={g: info['candidate']['counts'] for g, info in coverage['groups'].items()},
        missing_capability_groups=coverage['missing_candidate_groups'],
        coverage_passed=not coverage['missing_candidate_groups'],
        candidate_dedupe_passed=not any(set(m['row_ids']) & {r['id'] for r in candidates} for m in dedupe['matches']),
        grouping_preview_leakage_passed=split_report['leakage_passed'],
        provenance_privacy_training_authorization_passed=not canonical,
        split_counts={p: 0 for p in TUNING}, partitions_generated=False,
        dataset_v0_frozen=False, plan07_complete=False, smallest_remaining_blocker=blocker,
        model_trained=False, historical_synthetic_rows_reused=0, protected_final_rows_consumed=0,
        row_level_human_approvals_fabricated=0, canonical_rows_approved=0)
    files = dict()
    files['summary.json'] = json_bytes(summary)
    files['generated.jsonl'] = jsonl_bytes(generated)
    files['combined_candidates.jsonl'] = jsonl_bytes(rows)
    files['retained_candidates.jsonl'] = jsonl_bytes(candidates)
    files['quarantined.jsonl'] = jsonl_bytes(audited)
    files['exclusions.json'] = json_bytes(dict(row_ids=sorted(matches),
        synthetic_row_ids=sorted(r['id'] for r in generated if r['id'] in matches),
        finite_approved_space_exhausted=True, no_unreviewed_extra_renderings=True))
    files['coverage_before.json'] = json_bytes(coverage_report(public))
    files['coverage_after.json'] = json_bytes(coverage)
    files['canonical_findings.json'] = json_bytes(canonical)
    files['group_registry.json'] = json_bytes(dict(families=groups[0], scenarios=groups[1]))
    files['dedupe_report.json'] = json_bytes(dedupe)
    files['embeddings.json'] = json_bytes(vectors)
    files['embedding_receipt.json'] = json_bytes(receipt)
    files['signed_batch_verification.json'] = json_bytes(signed_receipt)
    files['grouped_split_preview.json'] = json_bytes(dict(assignments=assignments, report=split_report,
        status='PLANNING_ONLY_NOT_APPROVED_DATASET'))
    files['execution_scope.json'] = json_bytes(dict(
        authorization='CURRENT_USER_REQUEST_EXACT_SIGNED_TEMPLATE_SUPPLEMENT_AND_FULL_GATE_AUDIT',
        legacy_embedding_engine_authorization_label_is_historical=True,
        first_party_privacy_authority=ctx['policy']['policy_version'],
        publication_allowed=False, row_level_human_review_claimed=False,
        release_or_training_executed=False, native_canonical_findings_preserved=True))
    if PRIOR_EXECUTION.exists():
        files['embedding_reuse.json'] = json_bytes(dict(
            prior_manifest_sha256=packet.file_hash(PRIOR_EXECUTION / 'manifest.json'),
            prior_artifact_sha256=packet.file_hash(PRIOR_EXECUTION / 'embeddings.json'),
            prior_receipt_sha256=packet.file_hash(PRIOR_EXECUTION / 'embedding_receipt.json'),
            exact_ordered_id_text_binding_verified=True, inference_rerun=False,
            prior_execution_preserved=True,
            correction='Canonical registry digest names the actual registry; synthetic policy digest is separate metadata.'))
    destination.mkdir()
    for name, data in files.items():
        atomic_new(destination / name, data)
    inputs = dict(ctx['code_config_sha256'])
    for path in (Path(__file__), restricted.DESTINATION / 'manifest.json',
                 review.FOLDER / ('signed-' + BATCH) / 'manifest.json'):
        inputs[path.relative_to(restricted.ROOT).as_posix()] = packet.file_hash(path)
    atomic_new(destination / 'manifest.json', json_bytes(dict(version=VERSION, input_sha256=inputs,
        files={name: packet.file_hash(destination / name) for name in files},
        dataset_v0_frozen=False, plan07_complete=False, model_trained=False)))
    if approvals()[2] != signed_receipt:
        raise DataError('SIGNED_TEMPLATE_BATCH_CHANGED_DURING_EXECUTION')
    return summary


def main():
    argparse.ArgumentParser(description=__doc__).parse_args()
    print(json.dumps(run(), sort_keys=True))


if __name__ == '__main__':
    main()
