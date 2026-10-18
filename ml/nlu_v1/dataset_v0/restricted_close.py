"""Fail-closed restricted Dataset-v0 closure; execute only after ten signed decisions.

The public/five-partition builder and historical quarantine artifacts are untouched.
All outputs are new local snapshots. The independent final evaluation is reserved.
"""
import argparse
import copy
import hashlib
import json
from collections import Counter
from functools import lru_cache
from pathlib import Path

from jsonschema import Draft202012Validator
from ml.nlu_v1.dataset_v0 import (approved_supplement as supplement, closure_review,
    exception_packet, restricted_training, review_recording, template_review)
from ml.nlu_v1.dataset_v0.coverage_additions import coverage_report
from ml.nlu_v1.dataset_v0.similarity_audit import embed, verify_binding
from ml.nlu_v1.data_management.dedupe import audit, verify_report
from ml.nlu_v1.data_management.records import (DataError, append_event, canonical_issues,
    classify_privacy, facts_hash, load_groups, sha_json, text_hash, validate_record)
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes
from ml.nlu_v1.splitting.split import (TUNING, connected_groups, leakage_report,
    read_jsonl, official)
from ml.nlu_v1.source_registry import ROOT
from contract import validate as validate_prediction

VERSION='nlu_v1_restricted_closure_001'
DESTINATION=DATASETS/'frozen/nlu_v1_dataset_v0_restricted_001'
REVIEW_BATCH=closure_review.BATCH
SEMANTIC_KEYS=('text','tokens','bio_tags','domain','action','slots','disposition','ood',
               'family','scenario_id','semantic_parent_id')


def require_authorities():
    ctx,records=closure_review.verify_batch(batch_id=REVIEW_BATCH)
    old_ctx=template_review.context()
    signed=template_review.verify_batch(template_review.FOLDER,'template-human-001')
    if not signed['all_templates_approved'] or signed['valid_template_decisions']!=26:
        raise DataError('HISTORICAL_SIGNED_TEMPLATE_BATCH_INVALID')
    original_ctx=review_recording.load_context()
    original=review_recording.validate_batch(original_ctx,review_recording.FOLDER,'human-review-001')
    if not original['complete'] or original['valid_signed_decisions']!=2100 or original['decision_counts']!={'APPROVE':2100}:
        raise DataError('HISTORICAL_SEMANTIC_BATCH_INVALID')
    supplement.verify_files(restricted_training.DESTINATION,inputs=True)
    evidence=restricted_training.PublicEvidence()
    if evidence.errors:raise DataError('PUBLIC_SOURCE_ARTIFACT_EVIDENCE_INVALID')
    authorities=public_authorities()
    for source in ('massive_1_1_en_us','clinc150_full'):
        records=authorities[source]
        observed=evidence.sources[source]['observation']
        restricted_training.approve_intake(source,DATASETS/'raw'/('v0_'+source)/observed['member_file'],records['intake'])
        if records['training']['status']!='APPROVED' or records['training']['publication_allowed'] is not False or records['training']['binding']!=records['privacy']['binding']:
            raise DataError('PUBLIC_TRAINING_AUTHORITY_INVALID')
    return ctx,records,old_ctx,original_ctx


@lru_cache(maxsize=1)
def public_authorities():
    return exception_packet.read_json(restricted_training.DESTINATION/'source_authorities.json')


def scope(records,name):
    record=records.get('scope:'+name)
    if not record or record['decision']!='APPROVE' or record['binding']['scope']!=name:
        raise DataError('MISSING_DISTINCT_OPERATOR_SCOPE:'+name)
    return record


def source_gate(row,ctx,records,groups):
    """Native versioned first-party adapter, substituting only known registry gaps.

    It does not suppress other canonical findings, forge acquisition metadata, or
    alter the legacy global registry. Each substituted finding has a typed proof.
    """
    validate_record(row)
    definition=ctx['source'];s=row['source']
    if s['dataset']!='metis_internal_coverage_v1' or s['source_class']!='CONTROLLED_SYNTHETIC' or s['original_split']!='generated':
        raise DataError('FIRST_PARTY_SOURCE_ID_OR_SPLIT_INVALID')
    if definition['definition']['publication_allowed'] is not False or row['privacy']!='RESTRICTED' or row['metadata'].get('publication_allowed') is not False:
        raise DataError('RESTRICTED_FIRST_PARTY_PRIVACY_REQUIRED')
    if row['metadata'].get('external_or_private_inputs') is not False or row['metadata'].get('individual_row_human_review_claimed') is not False:
        raise DataError('FIRST_PARTY_INPUT_OR_REVIEW_CLAIM_INVALID')
    catalog=definition['catalogs'].get(s['version'])
    if not catalog or s['artifact_sha256']!=catalog['artifact_sha256'] or s['mapping_version']!=s['version']:
        raise DataError('FIRST_PARTY_CATALOG_ARTIFACT_STALE')
    ident=s['original_intent'];expected=catalog['template_sha256'].get(ident)
    if not expected or row['metadata'].get('template_review',{}).get('template_sha256')!=expected:
        raise DataError('FIRST_PARTY_TEMPLATE_HASH_INVALID')
    if s['mapper_version']!=definition['definition']['generator_version'] or row['generation']['generator_id']!=s['mapper_version'] or not s['intake_approval_id']:
        raise DataError('FIRST_PARTY_GENERATOR_OR_INTAKE_UNBOUND')
    authority=row['metadata'].get('first_party_authority_001')
    if not isinstance(authority,dict) or authority!={
        'source_registration_sha256':sha_json(records['scope:SOURCE_REGISTRATION']),
        'intake_sha256':sha_json(records['scope:FIRST_PARTY_INTAKE']),
        'rights_sha256':sha_json(records['scope:FIRST_PARTY_RIGHTS']),
        'restricted_training_use_sha256':sha_json(records['scope:RESTRICTED_TRAINING_USE']),
        'no_external_acquisition_url':True,'fictional_bindings_only':True,'publication_allowed':False}:
        raise DataError('FIRST_PARTY_RIGHTS_OR_USE_EVIDENCE_STALE')
    if s['intake_approval_id']!='first-party-intake-001:'+sha_json(records['scope:FIRST_PARTY_INTAKE']):
        raise DataError('FIRST_PARTY_INTAKE_ID_STALE')
    if row['privacy_review'] is None or row['privacy_review']['publication_allowed'] is not False or row['privacy_review']['license_id']!='LicenseRef-METIS-Internal-Training-Only':
        raise DataError('FIRST_PARTY_PRIVACY_EVIDENCE_INVALID')
    for name in ('SOURCE_REGISTRATION','FIRST_PARTY_INTAKE','FIRST_PARTY_RIGHTS',
                 'RESTRICTED_TRAINING_USE','TEMPLATE_DERIVED_RELEASE'):
        scope(records,name)
    issues=set(canonical_issues(row,groups))
    permitted={'SOURCE_NOT_APPROVED_FOR_CANONICAL_USE','SOURCE_ARTIFACT_UNVERIFIED'}
    if issues-permitted:raise DataError('FIRST_PARTY_CANONICAL_ISSUES:'+','.join(sorted(issues-permitted)))
    return True


def public_gate(row,ctx,records,original,groups):
    validate_record(row);s=row['source']
    if s['dataset'] not in ('massive_1_1_en_us','clinc150_full') or s['original_split'] not in ('train','oos_train') or official(row):
        raise DataError('PUBLIC_DERIVATIVE_SOURCE_OR_SPLIT_INVALID')
    if row['privacy']!='RESTRICTED' or row['privacy_review'] is None or row['privacy_review']['publication_allowed'] is not False:
        raise DataError('PUBLIC_DERIVATIVE_RESTRICTED_PRIVACY_INVALID')
    if row['metadata'].get('publication_allowed') is True or canonical_issues(row,groups):
        raise DataError('PUBLIC_DERIVATIVE_CANONICAL_FINDINGS')
    source_records=public_authorities()[s['dataset']]
    bound=row['metadata'].get('source_governance_003',{})
    if any(bound.get(k)!=sha_json(source_records[name]) for k,name in (
        ('source_privacy_review_sha256','privacy'),('training_authorization_sha256','training'),
        ('intake_authorization_sha256','intake'))):
        raise DataError('PUBLIC_DERIVATIVE_PRIVACY_INTAKE_OR_TRAINING_STALE')
    if s['intake_approval_id']!=source_records['intake']['approval_id']:
        raise DataError('PUBLIC_DERIVATIVE_INTAKE_ID_STALE')
    before=original['rows'].get(row['id'])
    if not before or any(row[k]!=before[k] for k in SEMANTIC_KEYS):
        raise DataError('ORIGINAL_SIGNED_SEMANTICS_CHANGED')
    if row['metadata']['source_governance_003']['original_row_content_sha256']!=sha_json(before):
        raise DataError('PUBLIC_DERIVATIVE_ORIGINAL_PROVENANCE_STALE')
    scope(records,'PUBLIC_DERIVATIVE_RELEASE')
    return True


def new_template_row(ctx,approval,ordinal,groups):
    """Render only an approved finite ordinal; no paraphrase or corpus sampling."""
    t=ctx['templates'][approval['binding']['target_id']]
    if approval['decision']!='APPROVE' or not 0<=ordinal<t['planned_max_rows']:
        raise DataError('UNAPPROVED_TEMPLATE_ORDINAL')
    pred=template_review.preview(t,ordinal)
    source=dict(dataset=ctx['source']['definition']['source_id'],version=t['template_version'],
        source_id=sha_json([sha_json(t),ordinal]),original_split='generated',
        original_intent=t['template_id'],mapper_version=t['generator_version'],
        mapping_version=t['template_version'],mapping_decision='MAP',
        artifact_sha256=ctx['source']['catalogs'][t['template_version']]['artifact_sha256'],
        intake_approval_id=None,source_class='CONTROLLED_SYNTHETIC')
    ident=hashlib.sha256(json.dumps([source[k] for k in ('dataset','version','original_split','source_id')],separators=(',',':')).encode()).hexdigest()
    row=dict(schema_version='nlu_v1_data_001',nlu_schema_version='nlu_v1_schema_001',id=ident,
        text=pred['text'],tokens=pred['tokens'],bio_tags=pred['bio_tags'],**pred['meaning'],source=source,
        family=t['family'],scenario_id=t['scenario_id'],semantic_parent_id=t['semantic_parent'],
        family_registry_version='nlu_v1_families_001',scenario_registry_version='nlu_v1_scenarios_001',
        privacy='RESTRICTED',privacy_review=None,review_status='PENDING',review_history=[],
        quarantine_reasons=[],partition='UNASSIGNED',composition=None,
        generation=dict(parent_ids=[],seed=t['generation_seed'],generator_id=t['generator_version'],
                        template_id=t['template_id']),text_sha256=text_hash(pred['text']),
        metadata=dict(origin_capability_group=t['capability_group'],
            template_review=dict(ordinal=ordinal,template_sha256=sha_json(t),approval_sha256=sha_json(approval)),
            semantic_authority='EXACT_HUMAN_REVIEWED_TEMPLATE',
            individual_row_human_review_claimed=False,external_or_private_inputs=False,publication_allowed=False,
            synthetic_policy_sha256=exception_packet.file_hash(template_review.POLICY)),
        provenance=dict(ingestion_row_sha256=sha_json(pred),import_batch_id=VERSION,
            import_row_number=ordinal+1,import_file_sha256=source['artifact_sha256'],
            mapping_configuration_sha256=source['artifact_sha256'],
            source_registry_sha256=ctx['source']['source_definition_sha256'],
            source_registry_version=ctx['source']['definition']['definition_version'],
            upstream_privacy='FIRST_PARTY_FICTIONAL_RESTRICTED',
            family_definition_sha256=sha_json(groups[0]['families'][t['family']]),
            scenario_definition_sha256=sha_json(groups[1]['scenarios'][t['scenario_id']])))
    append_event(row,None,'PENDING','machine:exact-reviewed-template','EXACT_REVIEWED_TEMPLATE_RENDERED')
    evidence=dict(binding_sha256=facts_hash(row),review_id='template-policy002:'+sha_json(t),
        reviewer_id='machine:synthetic-review-policy002',reviewed_at=row['review_history'][-1]['at'],
        publication_allowed=False,license_id='LicenseRef-METIS-Internal-Training-Only')
    row=classify_privacy(row,'RESTRICTED',evidence)
    validate_record(row)
    return row


def validate_new_template_row(row,ctx,approval,groups):
    t=ctx['templates'][approval['binding']['target_id']]
    ordinal=row['metadata']['template_review']['ordinal']
    expected=new_template_row(ctx,approval,ordinal,groups)
    immutable=('id','text','tokens','bio_tags','domain','action','slots','disposition','ood',
               'family','scenario_id','semantic_parent_id','generation','text_sha256')
    if any(row[k]!=expected[k] for k in immutable) or row['source']['source_id']!=expected['source']['source_id']:
        raise DataError('GENERATED_ROW_NOT_EXACT_REVIEWED_RECIPE')
    if row['metadata']['template_review']!=expected['metadata']['template_review']:
        raise DataError('GENERATED_TEMPLATE_APPROVAL_BINDING_CHANGED')
    validate_prediction(dict(schema_version=row['nlu_schema_version'],text=row['text'],tokens=row['tokens'],
        bio_tags=row['bio_tags'],meaning={k:row[k] for k in ('domain','action','slots','disposition','ood')}),'output')
    return True


def intake_snapshot(row,ctx,records):
    """Bind first-party intake separately from rights, use, semantic and release."""
    for name in ('SOURCE_REGISTRATION','FIRST_PARTY_INTAKE','FIRST_PARTY_RIGHTS','RESTRICTED_TRAINING_USE'):
        scope(records,name)
    if row['source']['dataset']!='metis_internal_coverage_v1' or row['privacy']!='RESTRICTED':
        raise DataError('FIRST_PARTY_INTAKE_SCOPE_INVALID')
    result=copy.deepcopy(row)
    approval=records['scope:FIRST_PARTY_INTAKE']
    result['source']['intake_approval_id']='first-party-intake-001:'+sha_json(approval)
    result['metadata']['first_party_authority_001']={
        'source_registration_sha256':sha_json(records['scope:SOURCE_REGISTRATION']),
        'intake_sha256':sha_json(approval),
        'rights_sha256':sha_json(records['scope:FIRST_PARTY_RIGHTS']),
        'restricted_training_use_sha256':sha_json(records['scope:RESTRICTED_TRAINING_USE']),
        'no_external_acquisition_url':True,'fictional_bindings_only':True,'publication_allowed':False}
    result['privacy_review']=None
    append_event(result,row['review_status'],row['review_status'],'operator:bound-first-party-intake','FIRST_PARTY_AUTHORITY_BOUND')
    proposal=copy.deepcopy(result)
    evidence=dict(binding_sha256=facts_hash(proposal),review_id='first-party-policy002:'+row['metadata']['template_review']['template_sha256'],
        reviewer_id='machine:synthetic-review-policy002',reviewed_at=result['review_history'][-1]['at'],
        publication_allowed=False,license_id='LicenseRef-METIS-Internal-Training-Only')
    result=classify_privacy(result,'RESTRICTED',evidence)
    if result['review_status']!=row['review_status'] or result['quarantine_reasons']!=row['quarantine_reasons']:
        raise DataError('INTAKE_CHANGED_REVIEW_STATE')
    return result


def groups_for_closure(ctx,old_ctx):
    stored=exception_packet.read_json(exception_packet.RUN/'group_registry.json')
    groups=supplement.extend_groups(old_ctx,(stored['families'],stored['scenarios']))
    augmented=copy.deepcopy(groups)
    for t in ctx['templates'].values():
        if t['family'] in augmented[0]['families'] or t['scenario_id'] in augmented[1]['scenarios']:
            raise DataError('NEW_TEMPLATE_GROUP_COLLIDES_WITH_HISTORY')
        augmented[0]['families'][t['family']]=dict(domain=t['domain'],action=t['action'],required_slots=t['allowed_slots'],dispositions=[t['disposition']])
        augmented[1]['scenarios'][t['scenario_id']]=dict(family=t['family'],semantic_parent_id=t['semantic_parent'],
            description='Exact reviewed closure supplement template')
    from ml.nlu_v1.data_management.records import validate_groups
    validate_groups(*augmented)
    return augmented


def release_snapshot(row,ctx,records,groups,original,old_ctx,old_approvals,new_approvals):
    """Copy historical quarantine; record separate release and semantic evidence."""
    validate_record(row)
    synthetic=row['source']['dataset']=='metis_internal_coverage_v1'
    if synthetic:
        tid=row['generation']['template_id']
        if tid in old_ctx['templates']:
            template_review.verify_approval(old_approvals[tid],old_ctx,require_approve=True)
            supplement.validate_exact(row,old_ctx['templates'][tid],old_approvals[tid],old_ctx)
        elif tid in ctx['templates']:
            closure_review.verify_record(new_approvals[tid],ctx,approved=True)
            validate_new_template_row(row,ctx,new_approvals[tid],groups)
        else:raise DataError('TEMPLATE_NOT_IN_SIGNED_BATCH')
        source_gate(row,ctx,records,groups)
        release=scope(records,'TEMPLATE_DERIVED_RELEASE')
    else:
        public_gate(row,ctx,records,original,groups)
        release=scope(records,'PUBLIC_DERIVATIVE_RELEASE')
    if row['review_status']=='REJECTED':raise DataError('REJECTED_ROW_CANNOT_BE_RELEASED')
    result=copy.deepcopy(row)
    evidence=dict(kind='HUMAN_REVIEW',binding_sha256=facts_hash(row),
        reviewer_id=release['reviewer_id'],review_id=sha_json(release),
        state_event_sha256=row['review_history'][-1]['event_sha256'],
        quarantine_sha256=sha_json(row['quarantine_reasons']))
    if result['review_status']=='QUARANTINED':
        result['quarantine_reasons']=[]
        append_event(result,'QUARANTINED','PENDING',release['reviewer_id'],'BOUND_CANONICAL_RELEASE',evidence)
    if result['review_status']!='PENDING':raise DataError('RELEASE_LIFECYCLE_START_INVALID')
    if synthetic:source_gate(result,ctx,records,groups)
    else:public_gate(result,ctx,records,original,groups)
    append_event(result,'PENDING','AUTO_VALIDATED','validator:restricted-closure-001','RESTRICTED_REAUDIT_PASSED')
    semantic_evidence=dict(kind='HUMAN_REVIEW',binding_sha256=facts_hash(result),
        reviewer_id=release['reviewer_id'],review_id=sha_json(release),
        semantic_authority='SIGNED_TEMPLATE_LEVEL' if synthetic else 'HISTORICAL_SIGNED_SEMANTIC_BATCH')
    append_event(result,'AUTO_VALIDATED','HUMAN_REVIEWED',release['reviewer_id'],
                 'BOUND_SEMANTIC_AND_CANONICAL_AUTHORITY',semantic_evidence)
    append_event(result,'HUMAN_REVIEWED','APPROVED',release['reviewer_id'],'RESTRICTED_CANONICAL_APPROVED',
                 dict(semantic_evidence,canonical_release_scope=release['binding']['scope']))
    validate_record(result)
    if result['privacy']!='RESTRICTED' or result['quarantine_reasons'] or result['review_status']!='APPROVED':
        raise DataError('RESTRICTED_RELEASE_FAILED')
    return result


def partition(rows):
    """Allocate connected components to four nonempty development partitions."""
    if len(rows)<4 or any(r['review_status']!='APPROVED' or r['privacy']!='RESTRICTED' or official(r) or
        r['partition']!='UNASSIGNED' for r in rows):
        raise DataError('RESTRICTED_PARTITION_INPUT_INVALID')
    components=connected_groups(rows)
    if len(components)<4:raise DataError('FOUR_CONNECTED_COMPONENTS_REQUIRED')
    assignments={};counts=Counter();names=list(TUNING)
    # Largest group stays in train; remaining groups populate held-out development partitions.
    ordered=sorted(components,key=lambda c:(-len(c),sha_json(c)))
    for i,component in enumerate(ordered):
        dest='TRAIN' if i==0 else (min(names[1:],key=lambda n:(counts[n],n)) if i<4 else min(names,key=lambda n:(counts[n]/({'TRAIN':.70,'DEVELOPMENT':.10,'CALIBRATION':.075,'SELECTION':.075}[n]),n)))
        for ident in component:assignments[ident]=dest
        counts[dest]+=len(component)
    report=leakage_report(rows,assignments)
    if not report['leakage_passed'] or any(counts[p]==0 for p in TUNING) or report['official_test_reassignments']:
        raise DataError('GROUPED_RESTRICTED_LEAKAGE_OR_EMPTY_PARTITION')
    return assignments,report


def restricted_projection(row,dest):
    if row['review_status']!='APPROVED' or row['privacy']!='RESTRICTED' or dest not in TUNING:
        raise DataError('RESTRICTED_PROJECTION_INVALID')
    return dict(projection_version='nlu_v1_restricted_partition_row_001',
        **{k:copy.deepcopy(row[k]) for k in ('id','text','tokens','bio_tags','domain','action','slots','disposition','ood',
            'nlu_schema_version','family','scenario_id','semantic_parent_id','generation','source')},
        privacy='RESTRICTED',partition=dest,publication_allowed=False,
        canonical_facts_sha256=facts_hash(row),canonical_review_sha256=row['review_history'][-1]['event_sha256'])


def validate_projection(projection,row,dest):
    if projection!=restricted_projection(row,dest):raise DataError('RESTRICTED_PROJECTION_CHANGED')
    validate_prediction(dict(schema_version=projection['nlu_schema_version'],text=projection['text'],
        tokens=projection['tokens'],bio_tags=projection['bio_tags'],
        meaning={k:projection[k] for k in ('domain','action','slots','disposition','ood')}),'output')
    return True


def verify_manifest(folder):
    folder=Path(folder);m=exception_packet.read_json(folder/'manifest.json')
    if m.get('version')!=VERSION or m.get('publication_allowed') is not False or m.get('independent_final_reserved') is not True or m.get('model_trained') is not False:
        raise DataError('RESTRICTED_MANIFEST_POLICY_INVALID')
    if set(m['files'])!={'canonical_approved.jsonl','coverage.json','dedupe.json','leakage.json','assignments.json',
                        'TRAIN.jsonl','DEVELOPMENT.jsonl','CALIBRATION.jsonl','SELECTION.jsonl','embedding_receipt.json','embeddings.json'}:
        raise DataError('RESTRICTED_MANIFEST_FILE_SET_INVALID')
    for name,digest in m['files'].items():
        if exception_packet.file_hash(folder/name)!=digest:raise DataError('RESTRICTED_MANIFEST_FILE_CHANGED')
    rows=read_jsonl(folder/'canonical_approved.jsonl')
    if len(rows)!=m['row_count'] or {r['id']:sha_json(r) for r in rows}!=m['row_sha256']:
        raise DataError('RESTRICTED_MANIFEST_MEMBERSHIP_CHANGED')
    assignments=exception_packet.read_json(folder/'assignments.json')
    if m['assignments_sha256']!=sha_json(assignments):raise DataError('PARTITION_ASSIGNMENT_BINDING_CHANGED')
    ctx,records,old_ctx,original=require_authorities()
    profile=ctx['profile']
    if m['authorization_ids']!={name:sha_json(scope(records,name)) for name in closure_review.SCOPES}:
        raise DataError('MANIFEST_AUTHORITY_IDS_CHANGED')
    if m['source_versions']!=sorted({r['source']['dataset']+':'+r['source']['version'] for r in rows}):
        raise DataError('MANIFEST_SOURCE_VERSIONS_CHANGED')
    if m['source_counts']!=dict(Counter(r['source']['dataset'] for r in rows)):
        raise DataError('MANIFEST_SOURCE_COUNTS_CHANGED')
    if m['profile_version']!=profile['profile_version'] or not profile['minimum_rows']<=len(rows)<=profile['maximum_rows']:
        raise DataError('RESTRICTED_PROFILE_CHANGED')
    c=coverage_report(rows)
    if c!=exception_packet.read_json(folder/'coverage.json') or c['missing_approved_groups']:
        raise DataError('APPROVED_COVERAGE_NOT_MET')
    if m['capability_counts']!={k:v['approved']['counts'] for k,v in c['groups'].items()} or m['coverage_evidence_sha256']!=sha_json(c):
        raise DataError('APPROVED_COVERAGE_MANIFEST_STALE')
    leakage=leakage_report(rows,assignments)
    if leakage!=exception_packet.read_json(folder/'leakage.json') or not leakage['leakage_passed']:
        raise DataError('RESTRICTED_LEAKAGE_CHANGED')
    if m['leakage_evidence_sha256']!=sha_json(leakage):raise DataError('LEAKAGE_EVIDENCE_STALE')
    groups=groups_for_closure(ctx,old_ctx)
    old_approvals={}
    for path in (template_review.FOLDER/'signed-template-human-001').glob('*.json'):
        if path.name!='manifest.json':
            approval=exception_packet.read_json(path)
            old_approvals[approval['binding']['template_id']]=approval
    for row in rows:
        if row['review_status']!='APPROVED' or row['quarantine_reasons'] or row['privacy']!='RESTRICTED':
            raise DataError('MANIFEST_CONTAINS_UNAPPROVED_ROW')
        if row['source']['dataset']=='metis_internal_coverage_v1':
            tid=row['generation']['template_id']
            if tid in old_ctx['templates']:
                if row['id'] not in ctx['current_row_sha256']:
                    raise DataError('HISTORICAL_SYNTHETIC_OUTSIDE_RETAINED_MEMBERSHIP')
                supplement.validate_exact(row,old_ctx['templates'][tid],old_approvals[tid],old_ctx)
            elif tid in ctx['templates']:
                validate_new_template_row(row,ctx,records[tid],groups)
            else:raise DataError('UNREVIEWED_TEMPLATE_IN_DATASET')
            source_gate(row,ctx,records,groups)
        else:
            if row['id'] not in ctx['current_row_sha256']:
                raise DataError('PUBLIC_ROW_OUTSIDE_RETAINED_MEMBERSHIP')
            public_gate(row,ctx,records,original,groups)
    vectors=exception_packet.read_json(folder/'embeddings.json')
    receipt=exception_packet.read_json(folder/'embedding_receipt.json')
    verify_binding(rows,vectors,receipt)
    _,dedupe=audit(rows,vectors,groups=groups)
    verify_report(rows,dedupe)
    if dedupe!=exception_packet.read_json(folder/'dedupe.json') or dedupe['matches'] or m['dedupe_evidence_sha256']!=sha_json(dedupe):
        raise DataError('FINAL_DEDUPE_EVIDENCE_STALE_OR_CONFLICTING')
    for p in TUNING:
        projected=read_jsonl(folder/(p+'.jsonl'))
        expected=[restricted_projection(r,p) for r in rows if assignments[r['id']]==p]
        if not projected or projected!=expected or len(projected)!=m['partition_counts'][p]:
            raise DataError('RESTRICTED_PARTITION_CONTENT_CHANGED')
    if m['partition_counts'].get('FINAL_INDEPENDENT_TEST',0)!=0 or m['publication_allowed'] is not False:
        raise DataError('FINAL_EVALUATION_OR_PUBLICATION_CONSUMED')
    return dict(verified_rows=len(rows),partition_counts=m['partition_counts'],frozen=m['frozen'])


def execute(destination=DESTINATION):
    """Run 2 only: all approvals and gates verified before writing a frozen bundle."""
    destination=Path(destination)
    if destination.exists() or DATASETS.resolve() not in destination.resolve().parents:
        raise DataError('NEW_DATASET_DESTINATION_REQUIRED')
    ctx,records,old_ctx,original=require_authorities()
    for name in closure_review.SCOPES:scope(records,name)
    if ctx['profile']['privacy']!='RESTRICTED' or ctx['profile']['publication_allowed'] is not False or ctx['profile']['partitions']!=list(TUNING):
        raise DataError('RESTRICTED_FOUR_PARTITION_PROFILE_INVALID')
    supplement.verify_files(supplement.DESTINATION,inputs=True)
    current=read_jsonl(supplement.DESTINATION/'retained_candidates.jsonl')
    if {r['id']:sha_json(r) for r in current}!=ctx['current_row_sha256']:
        raise DataError('CURRENT_CANDIDATE_MEMBERSHIP_CHANGED')
    groups=groups_for_closure(ctx,old_ctx)
    old_signed={}
    for path in (template_review.FOLDER/'signed-template-human-001').glob('*.json'):
        if path.name!='manifest.json':
            r=exception_packet.read_json(path);old_signed[r['binding']['template_id']]=r
    # Generate the finite reserve only after exact signed semantic and operator authority.
    prepared=[]
    for row in current:
        row=intake_snapshot(row,ctx,records) if row['source']['dataset']=='metis_internal_coverage_v1' else copy.deepcopy(row)
        if row['source']['dataset']=='metis_internal_coverage_v1':source_gate(row,ctx,records,groups)
        else:public_gate(row,ctx,records,original,groups)
        prepared.append(row)
    order=sorted(ctx['templates'],key=lambda x:(ctx['templates'][x]['capability_group']!='CALENDAR',x))
    counts={ident:(2 if ctx['templates'][ident]['capability_group']=='CALENDAR' else 10) for ident in order}
    while True:
        generated=[new_template_row(ctx,records[ident],i,groups) for ident in order for i in range(counts[ident])]
        generated=[intake_snapshot(r,ctx,records) for r in generated]
        candidates=prepared+generated
        vectors,receipt=embed(candidates);verify_binding(candidates,vectors,receipt)
        _,dedupe=audit(candidates,vectors,groups=groups);verify_report(candidates,dedupe)
        excluded={i for match in dedupe['matches'] for i in match['row_ids']}
        selected=[r for r in candidates if r['id'] not in excluded]
        if len({r['id'] for r in selected})!=len(selected):raise DataError('DUPLICATE_SELECTED_ROW_ID')
        candidate_coverage=coverage_report(selected)
        if not candidate_coverage['missing_candidate_groups']:break
        deficits={g:sum(candidate_coverage['groups'][g]['candidate']['deficit'].values()) for g in candidate_coverage['missing_candidate_groups']}
        available=[i for i in order if counts[i]<ctx['templates'][i]['planned_max_rows'] and ctx['templates'][i]['capability_group'] in deficits]
        if not available:raise DataError('FINITE_APPROVED_TEMPLATE_SPACE_INSUFFICIENT_FOR_COVERAGE')
        chosen=max(available,key=lambda i:(deficits[ctx['templates'][i]['capability_group']],i))
        counts[chosen]+=1
    # All matched endpoints are excluded; no similarity threshold is weakened.
    released=[release_snapshot(r,ctx,records,groups,original,old_ctx,old_signed,records) for r in selected]
    approved=coverage_report(released)
    if approved['missing_approved_groups']:
        raise DataError('APPROVED_CAPABILITY_COVERAGE_FAILED')
    if not ctx['profile']['minimum_rows']<=len(released)<=ctx['profile']['maximum_rows']:
        raise DataError('DATASET_V0_ROW_BOUNDS_FAILED')
    assignments,leakage=partition(released)
    # Fresh release-bound embedding/dedupe receipt; candidate audit is insufficient.
    final_vectors,final_receipt=embed(released);verify_binding(released,final_vectors,final_receipt)
    _,final_dedupe=audit(released,final_vectors,groups=groups);verify_report(released,final_dedupe)
    if final_dedupe['matches']:
        raise DataError('FINAL_RELEASE_DEDUPE_MATCHES')
    if any(r['privacy']!='RESTRICTED' or r['review_status']!='APPROVED' for r in released):
        raise DataError('FINAL_RELEASE_STATE_INVALID')
    files=dict(canonical_approved=jsonl_bytes(released),coverage=json_bytes(approved),
        dedupe=json_bytes(final_dedupe),leakage=json_bytes(leakage),assignments=json_bytes(assignments),
        embedding_receipt=json_bytes(final_receipt),embeddings=json_bytes(final_vectors))
    files={k+('.jsonl' if k=='canonical_approved' else '.json'):v for k,v in files.items()}
    counts={p:sum(x==p for x in assignments.values()) for p in TUNING}
    for p in TUNING:files[p+'.jsonl']=jsonl_bytes([restricted_projection(r,p) for r in released if assignments[r['id']]==p])
    manifest=dict(version=VERSION,dataset_version=ctx['profile']['dataset_version'],
        profile_version=ctx['profile']['profile_version'],schema_version='nlu_v1_schema_001',
        privacy_policy_versions=['nlu_v1_privacy_policy_003','nlu_v1_synthetic_review_policy_002'],
        source_versions=sorted({r['source']['dataset']+':'+r['source']['version'] for r in released}),
        authorization_ids={name:sha_json(scope(records,name)) for name in closure_review.SCOPES},
        template_review_batches=['template-human-001',REVIEW_BATCH],row_count=len(released),
        row_sha256={r['id']:sha_json(r) for r in released},
        source_counts=dict(Counter(r['source']['dataset'] for r in released)),
        capability_counts={k:v['approved']['counts'] for k,v in approved['groups'].items()},
        partition_counts={**counts,'FINAL_INDEPENDENT_TEST':0},assignments_sha256=sha_json(assignments),
        dedupe_evidence_sha256=sha_json(final_dedupe),leakage_evidence_sha256=sha_json(leakage),
        coverage_evidence_sha256=sha_json(approved),provenance_reference_sha256=ctx['files_sha256'],
        publication_allowed=False,independent_final_reserved=True,model_trained=False,
        frozen=True,plan07_complete=True,files={name:hashlib.sha256(data).hexdigest() for name,data in files.items()})
    # Verify in an ignored quarantine staging directory. Rename into frozen only
    # after the independent verifier succeeds; an interruption cannot fake freeze.
    stage=DATASETS/'quarantine/plan07-restricted-freeze-staging-001'
    if stage.exists():raise DataError('PRIOR_STAGING_EXISTS_REQUIRES_EXPLICIT_AUDIT')
    stage.mkdir(parents=True)
    for name,data in files.items():atomic_new(stage/name,data)
    atomic_new(stage/'manifest.json',json_bytes(manifest))
    result=verify_manifest(stage)
    atomic_new(stage/'plan07_completion.json',json_bytes(dict(version=VERSION,
        manifest_sha256=exception_packet.file_hash(stage/'manifest.json'),verified=result,
        independent_final_reserved=True,model_trained=False)))
    stage.rename(destination)
    return result


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('command',choices=('check','execute','verify'))
    p.add_argument('--destination',type=Path,default=DESTINATION);a=p.parse_args()
    if a.command=='check':
        ctx=closure_review.context();result=dict(profile=ctx['profile']['profile_version'],
            source=ctx['source']['definition']['source_id'],current_candidates=len(ctx['current_row_sha256']),
            signed_closure_batch_present=(closure_review.FOLDER/('signed-'+REVIEW_BATCH)).exists(),
            dataset_frozen=a.destination.exists())
    elif a.command=='verify':result=verify_manifest(a.destination)
    else:result=execute(a.destination)
    print(json.dumps(result,sort_keys=True))


if __name__=='__main__':main()
