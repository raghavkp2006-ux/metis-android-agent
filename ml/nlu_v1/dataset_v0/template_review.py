"""Exact human template review. Preparation creates examples, never dataset rows."""
import argparse
import base64
from collections import Counter
import copy
from datetime import datetime, timezone
from pathlib import Path

import yaml
from ml.nlu_v1.dataset_v0 import semantic_groups as semantic, exception_packet as packet
from ml.nlu_v1.dataset_v0.privacy_terminal import atomic_replace, reviewer_lock, terminal_text, read_key
from ml.nlu_v1.source_registry import ROOT, UniqueLoader
from ml.nlu_v1.synthetic.generate import fields, render
from ml.nlu_v1.ingestion.common import tokenize
from ml.nlu_v1.data_management.records import DataError, check_utc, sha_json, validate_record, canonical_issues
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes
from contract import LABELS, validate as validate_prediction

POLICY = ROOT / 'ml/nlu_v1/configs/synthetic_review_policy_002.yaml'
CATALOG = ROOT / 'ml/nlu_v1/configs/internal_coverage_templates_001.yaml'
FOLDER = DATASETS / 'quarantine/plan07-template-review-002'
CHOICES = {'PENDING', 'APPROVE', 'REJECT', 'CHANGE'}
VERSION = 'nlu_v1_template_review_packet_002'


def preview(template, index):
    names = sorted(template['bindings'])
    values = {name: template['bindings'][name]['values'][(template['generation_seed'] + index + offset) % len(template['bindings'][name]['values'])]
              for offset, name in enumerate(names)}
    rendered = render(template['forms'][index % len(template['forms'])], values, template['bindings'])
    prediction = {'schema_version': 'nlu_v1_schema_001', 'text': rendered['text'],
                  'tokens': rendered['tokens'], 'bio_tags': rendered['bio_tags'],
                  'meaning': {key: template[key] for key in ('domain','action','disposition')}}
    prediction['meaning'].update(slots=rendered['slots'], ood=template['disposition']=='OUT_OF_DOMAIN')
    validate_prediction(prediction, 'output')
    return prediction


def validate_catalog(catalog, policy):
    if policy['policy_version'] != 'nlu_v1_synthetic_review_policy_002' or policy['publication_allowed'] is not False or policy['privacy'] != 'RESTRICTED':
        raise DataError('TEMPLATE_POLICY_AUTHORITY_INVALID')
    if catalog['source_id'] != policy['source_id'] or catalog['catalog_version'] != policy['source_version'] or catalog['publication_allowed'] is not False:
        raise DataError('TEMPLATE_SOURCE_OR_VERSION_INVALID')
    ids, seeds = set(), set()
    for t in catalog['templates']:
        if t['template_id'] in ids or t['generation_seed'] in seeds:
            raise DataError('DUPLICATE_TEMPLATE_ID_OR_SEED')
        ids.add(t['template_id']); seeds.add(t['generation_seed'])
        if t['template_version'] != catalog['catalog_version'] or t['privacy'] != 'RESTRICTED' or t['publication_allowed'] is not False or t['external_or_private_inputs'] is not False:
            raise DataError('TEMPLATE_SCOPE_OR_PRIVACY_CHANGED')
        if type(t['generation_seed']) is not int or t['generation_seed'] < 0 or type(t['planned_max_rows']) is not int or not 1 <= t['planned_max_rows'] <= 30:
            raise DataError('TEMPLATE_SEED_OR_BUDGET_INVALID')
        for field, labels in (('domain','domains'),('action','actions'),('disposition','dispositions')):
            if t[field] not in LABELS[labels]:
                raise DataError('UNKNOWN_TEMPLATE_LABEL')
        if not t['family'] or not t['scenario_id'] or not t['semantic_parent'] or not t['forms'] or len(t['forms']) != len(set(t['forms'])):
            raise DataError('TEMPLATE_GROUPS_OR_FORMS_INVALID')
        for binding in t['bindings'].values():
            if binding['type'] not in LABELS['slots'] or not binding['values'] or any(not isinstance(v,str) or not v.strip() or '{' in v or '}' in v for v in binding['values']):
                raise DataError('TEMPLATE_BINDING_INVALID')
        for i, form in enumerate(t['forms']):
            names = fields(form)
            if set(names) != set(t['bindings']) or sorted({t['bindings'][n]['type'] for n in names}) != t['allowed_slots']:
                raise DataError('TEMPLATE_SLOT_SET_CHANGED')
            preview(t, i)  # Illustrative/schema validation only; no managed row.
        if t['disposition']=='SUPPORTED_NEEDS_CONTEXT' and 'REFERENCE' not in t['allowed_slots']:
            raise DataError('TEMPLATE_CONTEXT_REFERENCE_REQUIRED')
        if t['generator_version'] != 'nlu_v1_internal_coverage_generator_001' or t['renderer'] != 'CLOSED_REVIEWED_FORM_AND_BINDING_TABLE_001':
            raise DataError('TEMPLATE_GENERATOR_VERSION_UNSUPPORTED')


def context():
    policy = yaml.load(POLICY.read_text(encoding='utf-8'), Loader=UniqueLoader)
    catalog = yaml.load(CATALOG.read_text(encoding='utf-8'), Loader=UniqueLoader)
    validate_catalog(catalog, policy)
    paths = [POLICY,CATALOG,Path(__file__),ROOT/'ml/nlu_v1/synthetic/generate.py',
             ROOT/'ml/nlu_v1/ingestion/common.py',ROOT/'ml/nlu_v1/contract.py',
             ROOT/'ml/nlu_v1/data_management/records.py',ROOT/'ml/nlu_v1/data_management/dedupe.py',
             ROOT/'ml/nlu_v1/splitting/split.py',ROOT/'ml/nlu_v1/configs/dataset_v0_coverage_policy.yaml',
             DATASETS/'quarantine/plan07-coverage-repair-001/coverage_after.json']
    bindings = {p.relative_to(ROOT).as_posix():packet.file_hash(p) for p in paths}
    return {'policy':policy,'catalog':catalog,'templates':{t['template_id']:t for t in catalog['templates']},'code_config_sha256':bindings}


def binding(t, ctx):
    return {'template_id':t['template_id'],'template_sha256':sha_json(t),
            'source_id':ctx['catalog']['source_id'],'source_version':ctx['catalog']['catalog_version'],
            'policy_version':ctx['policy']['policy_version'],'code_config_sha256':ctx['code_config_sha256'],
            'generator_version':t['generator_version'],'generation_seed':t['generation_seed'],
            'family':t['family'],'scenario_id':t['scenario_id'],'semantic_parent':t['semantic_parent'],
            'authority':'EXACT_TEMPLATE_ONLY_NOT_ARBITRARY_FUTURE_ROWS',
            'publication_allowed':False,'canonical_release_authorized':False,'training_authorized':False}


def draft(t, ctx):
    return {'record_version':VERSION,'kind':'HUMAN_TEMPLATE_REVIEW', 'binding':binding(t,ctx),
            'decision':'PENDING','requested_change':None,'reviewer_id':None,'reviewed_at':None,
            'reviewer_note':None,'reviewer_registry_sha256':None,'signature_ed25519_base64':None}


def document(ctx):
    lines=['# LOCAL template-only human review — policy002','','No dataset rows have been generated. The three renderings per target are illustrations only, without row IDs or dataset provenance. Review ALL finite patterns and binding lists, not merely the examples. No raw external utterance is included.','','Edit only Decision cells in the table. Allowed: PENDING, APPROVE, REJECT, CHANGE. For CHANGE, record requested_change in the target JSON under decisions/; it authorizes no generation and requires a new version/review after edits. No decision is inferred from project ownership.','','Human approval covers only the exact template hash, pattern set, fictional bindings, labels, family/scenario, seed, generator/validator hashes and source/policy version. Changes invalidate approval. Privacy/intake/training/canonical/freeze gates and actual dedupe/leakage remain separate. Future rows may cite template-level human authority, never fictitious individual review.','','Review interactively: `python -m ml.nlu_v1.dataset_v0.template_review review`','','After manual edits: `python -m ml.nlu_v1.dataset_v0.template_review sync`','','Sign only after human decisions with your existing enrolled key (replace placeholders):','','```powershell','python -m ml.nlu_v1.dataset_v0.template_review sign --batch-id template-human-001 --reviewer-id YOUR_ENROLLED_ID --key-file C:/PATH_OUTSIDE_REPO/reviewer-ed25519.pem --reviewed-at YYYY-MM-DDTHH:MM:SSZ --reason "YOUR_ACTUAL_REVIEW_REASON"','python -m ml.nlu_v1.dataset_v0.template_review verify --batch-id template-human-001','```','','The existing group_reviewers.yaml trust store and semantic_groups.signed_bytes/Ed25519 implementation are reused. Preparation creates no keys, signatures, reviewer identity, timestamps or approvals. Signed records are append-only. APPROVE is semantic template approval, not dataset release.','','## Decisions','','| Template | Capability / domain | Decision |','|---|---|---|']
    for ident,t in ctx['templates'].items():
        lines.append(f"| {ident} | {t['capability_group']} / {t['domain']} | PENDING |")
    for ident,t in ctx['templates'].items():
        lines += ['', '## '+ident,'',f"Template hash: `{sha_json(t)}`",'',
                  f"Capability/domain: {t['capability_group']} / {t['domain']}; action: {t['action']}; disposition: {t['disposition']}.",'',
                  f"Family: `{t['family']}`; fixed scenario: `{t['scenario_id']}`; shared parent: `{t['semantic_parent']}`.",'',
                  'Allowed slots: '+', '.join(t['allowed_slots'])+'.', '',
                  f"Fixed seed: {t['generation_seed']}; generator: `{t['generator_version']}`; future row-budget hint: {t['planned_max_rows']}.",'',
                  'All reviewed finite patterns:','']
        for form in t['forms']:
            lines.append('- '+form)
        lines += ['', 'Only allowed fictional binding values:','']
        for name,b in sorted(t['bindings'].items()):
            lines.append(f"- {name} ({b['type']}): "+'; '.join(b['values']))
        lines += ['', 'Three example renderings (illustrations only):','']
        for i in range(3):
            lines.append('- '+preview(t,i)['text'])
        lines += ['', 'Generated-row constraints: exact reviewed finite pattern/binding membership; fixed labels/family/scenario/seed; deterministic token/BIO/UTF-16 reconstruction; schema validation; exact template hash provenance; no external/private inputs; RESTRICTED; publication_allowed=false. All actual rows must pass source/rights/training/canonical gates and fresh complete dedupe/grouped-leakage checks. Related positive/context/boundary/OOD templates share a conservative parent per capability; siblings cannot cross splits. Budget hints are not proof that similarity or coverage will pass.','']
    return '\n'.join(lines)+'\n'


def choices(text, ctx):
    normalized, result = [], {}
    for line in text.splitlines():
        if line.startswith('| internalcov:'):
            parts=line.split('|')
            if len(parts)!=5 or parts[1].strip() not in ctx['templates'] or parts[1].strip() in result or parts[-2].strip() not in CHOICES:
                raise DataError('INVALID_TEMPLATE_DECISION_TABLE')
            result[parts[1].strip()]=parts[-2].strip()
            parts[-2]=' PENDING '
            line='|'.join(parts)
        normalized.append(line)
    if set(result)!=set(ctx['templates']) or '\n'.join(normalized)+'\n'!=document(ctx):
        raise DataError('TEMPLATE_PACKET_CONTENT_OR_MEMBERSHIP_CHANGED')
    return result


def require_local(folder):
    from .privacy_review import require_local as local_check
    local_check(folder)
    if (DATASETS/'quarantine').resolve() not in Path(folder).resolve().parents:
        raise DataError('TEMPLATE_REVIEW_MUST_REMAIN_LOCAL')


def prepare(ctx, folder=FOLDER):
    folder=Path(folder);require_local(folder)
    if folder.exists():
        raise DataError('TEMPLATE_PACKET_ALREADY_EXISTS')
    folder.mkdir();(folder/'decisions').mkdir()
    for ident,t in ctx['templates'].items():
        atomic_new(folder/'decisions'/(ident.replace(':','-')+'.json'),json_bytes(draft(t,ctx)))
    atomic_new(folder/'TEMPLATE_DECISIONS.md',document(ctx).encode())
    atomic_new(folder/'packet.json',json_bytes({'version':VERSION,'context':ctx,
        'target_bindings':{i:sha_json(binding(t,ctx)) for i,t in ctx['templates'].items()},
        'generated_dataset_rows':0,'automatic_template_approvals':0}))
    atomic_new(folder/'manifest.json',json_bytes({'version':VERSION,'packet_sha256':packet.file_hash(folder/'packet.json')}))
    return {'templates':len(ctx['templates']),'future_row_budget_hint':sum(t['planned_max_rows'] for t in ctx['templates'].values()),'generated_dataset_rows':0,'auto_approved':0}


def load(folder=FOLDER):
    folder=Path(folder);require_local(folder);ctx=context()
    manifest=packet.read_json(folder/'manifest.json')
    if manifest.get('version')!=VERSION or packet.file_hash(folder/'packet.json')!=manifest['packet_sha256']:
        raise DataError('TEMPLATE_PACKET_HASH_CHANGED')
    stored=packet.read_json(folder/'packet.json')
    if stored['context']!=ctx or stored['target_bindings']!={i:sha_json(binding(t,ctx)) for i,t in ctx['templates'].items()}:
        raise DataError('TEMPLATE_POLICY_CATALOG_GENERATOR_OR_BINDING_CHANGED_NEW_REVIEW_REQUIRED')
    selected=choices((folder/'TEMPLATE_DECISIONS.md').read_bytes().decode(),ctx)
    records={}
    for ident,t in ctx['templates'].items():
        record=packet.read_json(folder/'decisions'/(ident.replace(':','-')+'.json'))
        original=draft(t,ctx)
        if set(record)!=set(original) or any(record[k]!=original[k] for k in ('record_version','kind','binding')) or record['decision'] not in CHOICES:
            raise DataError('TEMPLATE_DECISION_BINDINGS_CHANGED')
        records[ident]=record
    return ctx,selected,records


def sync(folder=FOLDER):
    folder=Path(folder)
    ctx,selected,records=load(folder)
    for ident,record in records.items():
        if record['signature_ed25519_base64'] is not None:
            raise DataError('EDITABLE_TEMPLATE_DRAFT_MUST_BE_UNSIGNED')
        record['decision']=selected[ident]
        atomic_replace(folder/'decisions'/(ident.replace(':','-')+'.json'),json_bytes(record))
    return {'decisions':dict(Counter(selected.values())),'dataset_rows_generated':0}


def verify_approval(record, ctx, *, require_approve=False):
    if ctx != context():
        raise DataError('TEMPLATE_APPROVAL_CONTEXT_IS_NOT_CURRENT')
    ident=record.get('binding',{}).get('template_id')
    if ident not in ctx['templates'] or record.get('binding')!=binding(ctx['templates'][ident],ctx) or set(record)!=set(draft(ctx['templates'][ident],ctx)):
        raise DataError('TEMPLATE_APPROVAL_BINDING_CHANGED')
    if record['kind']!='HUMAN_TEMPLATE_REVIEW' or record['record_version']!=VERSION or record['decision'] not in {'APPROVE','REJECT','CHANGE'}:
        raise DataError('EXPLICIT_HUMAN_TEMPLATE_DECISION_REQUIRED')
    if require_approve and record['decision']!='APPROVE':
        raise DataError('TEMPLATE_NOT_APPROVED')
    if record['decision']=='CHANGE' and (not isinstance(record['requested_change'],str) or not record['requested_change'].strip()):
        raise DataError('TEMPLATE_CHANGE_DETAILS_REQUIRED')
    if record['decision']!='CHANGE' and record['requested_change'] is not None:
        raise DataError('TEMPLATE_CHANGE_DETAILS_WITHOUT_CHANGE')
    registry=semantic.load_reviewers(); reviewer=record['reviewer_id']
    if reviewer not in registry['reviewers'] or reviewer.startswith(('machine:','audit:','validator:','import:','group:')) or record['reviewer_registry_sha256']!=packet.file_hash(semantic.REVIEWERS):
        raise DataError('EXISTING_ENROLLED_HUMAN_REVIEWER_REQUIRED')
    check_utc(record['reviewed_at'])
    if datetime.fromisoformat(record['reviewed_at'].replace('Z','+00:00'))>datetime.now(timezone.utc) or not isinstance(record['reviewer_note'],str) or not record['reviewer_note'].strip():
        raise DataError('REAL_TEMPLATE_REVIEW_TIME_AND_REASON_REQUIRED')
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    try:
        key=Ed25519PublicKey.from_public_bytes(base64.b64decode(registry['reviewers'][reviewer]['public_key_ed25519_base64'],validate=True))
        key.verify(base64.b64decode(record['signature_ed25519_base64'],validate=True),semantic.signed_bytes(record))
    except Exception as error:
        raise DataError('HUMAN_TEMPLATE_SIGNATURE_INVALID') from error
    return {'template_id':ident,'decision':record['decision'],'template_semantic_authority':record['decision']=='APPROVE','individual_rows_reviewed':0,'dataset_rows_approved':0}


def sign(folder,batch_id,key,reviewer,reviewed_at,reason):
    if not batch_id or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-' for c in batch_id):
        raise DataError('INVALID_TEMPLATE_BATCH_ID')
    ctx,selected,records=load(folder)
    if any(records[i]['decision']!=selected[i] for i in records):
        raise DataError('SYNC_TEMPLATE_DECISIONS_BEFORE_SIGNING')
    if any(d=='PENDING' for d in selected.values()):
        raise DataError('TEMPLATE_DECISIONS_STILL_PENDING')
    output=[]
    for record in records.values():
        record=copy.deepcopy(record)
        record.update(reviewer_id=reviewer,reviewed_at=reviewed_at,reviewer_note=reason,
            reviewer_registry_sha256=packet.file_hash(semantic.REVIEWERS))
        record['signature_ed25519_base64']=base64.b64encode(key.sign(semantic.signed_bytes(record))).decode()
        verify_approval(record,ctx)
        output.append(record)
    destination=Path(folder)/('signed-'+batch_id)
    if destination.exists():
        raise DataError('TEMPLATE_SIGNED_BATCH_ALREADY_EXISTS')
    destination.mkdir()
    files={}
    for record in output:
        path=destination/(record['binding']['template_id'].replace(':','-')+'.json')
        atomic_new(path,json_bytes(record));files[path.name]=packet.file_hash(path)
    atomic_new(destination/'manifest.json',json_bytes({'version':VERSION,'files':files,'generated_rows':0}))
    return {'signed_template_decisions':len(output),'generated_rows':0}


def verify_batch(folder,batch_id):
    ctx,selected,drafts=load(folder)
    if not batch_id or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-' for c in batch_id):
        raise DataError('INVALID_TEMPLATE_BATCH_ID')
    destination=Path(folder)/('signed-'+batch_id)
    manifest=packet.read_json(destination/'manifest.json')
    if manifest['version']!=VERSION or {p.name for p in destination.iterdir()}!=set(manifest['files'])|{'manifest.json'}:
        raise DataError('TEMPLATE_SIGNED_BATCH_ARTIFACT_SET_INVALID')
    seen=set();counts=Counter()
    for name,digest in manifest['files'].items():
        if Path(name).name!=name or packet.file_hash(destination/name)!=digest:
            raise DataError('TEMPLATE_SIGNED_RECORD_CHANGED')
        record=packet.read_json(destination/name);verify_approval(record,ctx)
        ident=record['binding']['template_id']
        if ident in seen or selected[ident]!=record['decision'] or drafts[ident]['requested_change']!=record['requested_change']:
            raise DataError('TEMPLATE_SIGNED_DECISION_SUPERSEDED_OR_DUPLICATED')
        seen.add(ident);counts[record['decision']]+=1
    if seen!=set(ctx['templates']):
        raise DataError('TEMPLATE_SIGNED_DECISIONS_MISSING')
    return {'valid_template_decisions':len(seen),'decisions':dict(counts),'all_templates_approved':counts['APPROVE']==len(seen),'dataset_rows_generated':0,'dataset_rows_approved':0}


def verify_inheritance(row,approval,ctx,*,audit_context):
    """Future row gate only: no transitions, history forgery or generation."""
    verify_approval(approval,ctx,require_approve=True)
    t=ctx['templates'][approval['binding']['template_id']]
    validate_record(row)
    recipe=row['metadata'].get('template_review',{})
    ordinal=recipe.get('ordinal')
    if type(ordinal) is not int or not 0<=ordinal<t['planned_max_rows'] or recipe.get('template_sha256')!=sha_json(t) or recipe.get('approval_sha256')!=sha_json(approval):
        raise DataError('ROW_TEMPLATE_PROVENANCE_NOT_BOUND')
    if row['source']['original_split']!='generated' or row['source']['original_intent']!=t['template_id'] or row['source']['source_id']!=sha_json([sha_json(t),ordinal]) or row['metadata'].get('origin_capability_group')!=t['capability_group']:
        raise DataError('ROW_TEMPLATE_SOURCE_ID_OR_CAPABILITY_CHANGED')
    if row['source']['dataset']!=ctx['catalog']['source_id'] or row['source']['version']!=ctx['catalog']['catalog_version'] or row['source']['source_class']!='CONTROLLED_SYNTHETIC' or row['privacy']!='RESTRICTED' or not row['privacy_review'] or row['privacy_review']['publication_allowed']:
        raise DataError('ROW_SOURCE_PRIVACY_OR_PUBLICATION_NOT_ALLOWED')
    if row['family']!=t['family'] or row['scenario_id']!=t['scenario_id'] or row['semantic_parent_id']!=t['semantic_parent'] or not row['generation'] or row['generation']['seed']!=t['generation_seed'] or row['generation']['generator_id']!=t['generator_version'] or row['generation']['template_id']!=t['template_id']:
        raise DataError('ROW_FIXED_TEMPLATE_FIELDS_CHANGED')
    expected=preview(t,ordinal)
    if any(row[k]!=expected[k] for k in ('text','tokens','bio_tags')) or any(row[k]!=expected['meaning'][k] for k in ('domain','action','disposition','ood','slots')):
        raise DataError('ROW_NOT_EXACT_REVIEWED_TEMPLATE_RENDERING')
    from ml.nlu_v1.data_management.dedupe import verify_report
    from ml.nlu_v1.splitting.split import leakage_report, TUNING
    corpus=audit_context['rows']; report=audit_context['dedupe_report']
    verify_report(corpus,report)
    matching=[r for r in corpus if r['id']==row['id']]
    quarantined_ids={corpus[i]['id'] for i in report['quarantined_indices']}
    if matching!=[row] or row['id'] in quarantined_ids or row['id'] in {i for m in report['matches'] for i in m['row_ids']} or canonical_issues(row,audit_context['groups']):
        raise DataError('ROW_CANONICAL_OR_DEDUPE_GATES_FAILED')
    if any(r['partition'] in ('PROTECTED_EVALUATION','FINAL_INDEPENDENT_TEST') for r in corpus):
        raise DataError('PROTECTED_EVALUATION_CANNOT_ENTER_TEMPLATE_INHERITANCE')
    leakage=leakage_report(corpus,audit_context['assignments'])
    if not leakage['leakage_passed'] or not set(TUNING)<=set(audit_context['assignments'].values()):
        raise DataError('TEMPLATE_ROW_GROUPED_LEAKAGE_NOT_READY')
    return {'semantic_authority':'EXACT_HUMAN_REVIEWED_TEMPLATE','template_id':t['template_id'],
            'individual_row_human_review_claimed':False,'canonical_transition_performed':False}


def review(folder=FOLDER,*,key_input=None,prompt=input,output=print):
    folder=Path(folder);require_local(folder);key_input=key_input or read_key
    with reviewer_lock(folder):
        ctx,selected,records=load(folder);ids=list(ctx['templates'])
        state_path=folder/'template_session.json'
        state=packet.read_json(state_path) if state_path.exists() else {'cursor':0}
        if type(state.get('cursor')) is not int or not 0<=state['cursor']<len(ids):
            raise DataError('TEMPLATE_SESSION_CURSOR_INVALID')
        while True:
            ident=ids[state['cursor']];t=ctx['templates'][ident]
            output(terminal_text(f"{state['cursor']+1}/{len(ids)} {ident} [{selected[ident]}]\n"+document({'templates':{ident:t}}).split('## '+ident,1)[1]))
            output('1 APPROVE | 2 REJECT | 3 CHANGE | N next | P previous | S save | Q save and quit')
            try:
                key=key_input().strip().upper()
            except (EOFError,KeyboardInterrupt):
                key='Q'
            if key in ('1','2','3'):
                choice={'1':'APPROVE','2':'REJECT','3':'CHANGE'}[key]
                note=prompt('Requested change: ') if key=='3' else None
                if key=='3' and not note.strip():
                    output('CHANGE requires details.');continue
                before=(folder/'TEMPLATE_DECISIONS.md').read_bytes().decode()
                if choices(before,ctx)!=selected:
                    raise DataError('TEMPLATE_DECISIONS_CHANGED_IN_ANOTHER_EDITOR')
                line=next(l for l in before.splitlines() if l.startswith('| '+ident+' |'))
                parts=line.split('|');parts[-2]=' '+choice+' '
                after=before.replace(line,'|'.join(parts),1)
                choices(after,ctx)
                records[ident]['requested_change']=note
                atomic_replace(folder/'decisions'/(ident.replace(':','-')+'.json'),json_bytes(records[ident]))
                atomic_replace(folder/'TEMPLATE_DECISIONS.md',after.encode())
                selected[ident]=choice;sync(folder)
                state['cursor']=min(len(ids)-1,state['cursor']+1)
            elif key in ('N','P'):
                state['cursor']=max(0,min(len(ids)-1,state['cursor']+(1 if key=='N' else -1)))
            elif key not in ('S','Q'):
                output('Invalid key.');continue
            atomic_replace(state_path,json_bytes(state))
            if key=='Q':return


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=('prepare','check','review','sync','sign','verify'))
    parser.add_argument('--batch-id');parser.add_argument('--reviewer-id');parser.add_argument('--key-file',type=Path)
    parser.add_argument('--reviewed-at');parser.add_argument('--reason')
    args=parser.parse_args()
    if args.command=='prepare':result=prepare(context())
    elif args.command=='review':review();return
    elif args.command=='sync':
        with reviewer_lock(FOLDER):result=sync()
    elif args.command=='check':
        ctx,selected,records=load();result={'templates':len(selected),'decisions':dict(Counter(selected.values())),'dataset_rows_generated':0}
    elif args.command=='verify':
        if not args.batch_id:parser.error('verify requires --batch-id')
        result=verify_batch(FOLDER,args.batch_id)
    else:
        if not all((args.batch_id,args.reviewer_id,args.key_file,args.reviewed_at,args.reason)):
            parser.error('sign requires --batch-id --reviewer-id --key-file --reviewed-at --reason')
        from cryptography.hazmat.primitives.serialization import load_pem_private_key
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        raw=args.key_file.read_bytes()
        try:key=load_pem_private_key(raw,password=None)
        except TypeError:
            from getpass import getpass
            key=load_pem_private_key(raw,password=getpass('Existing key password: ').encode())
        if not isinstance(key,Ed25519PrivateKey):raise DataError('EXISTING_ED25519_KEY_REQUIRED')
        with reviewer_lock(FOLDER):
            result=sign(FOLDER,args.batch_id,key,args.reviewer_id,args.reviewed_at,args.reason)
    import json
    print(json.dumps(result,sort_keys=True))


if __name__=='__main__':main()
