"""Versioned local review of two finite templates and eight separate closure scopes.

Preparation and review never release data. Only an enrolled Ed25519 reviewer can
create the append-only signed batch consumed by restricted_close.
"""
import argparse
import base64
import copy
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import yaml
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey, Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import load_pem_private_key

from ml.nlu_v1.dataset_v0 import template_review, approved_supplement, semantic_groups, exception_packet, review_recording
from ml.nlu_v1.dataset_v0.privacy_terminal import atomic_replace, reviewer_lock, read_key
from ml.nlu_v1.data_management.records import DataError, check_utc, sha_json
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes
from ml.nlu_v1.source_registry import ROOT, UniqueLoader
from ml.nlu_v1.splitting.split import read_jsonl

VERSION = 'nlu_v1_restricted_closure_review_001'
BATCH = 'restricted-closure-human-001'
FOLDER = DATASETS / 'quarantine/plan07-restricted-closure-review-001'
CATALOG = ROOT / 'ml/nlu_v1/configs/internal_coverage_templates_002.yaml'
SOURCE = ROOT / 'ml/nlu_v1/configs/internal_source_001.yaml'
PROFILE = ROOT / 'ml/nlu_v1/configs/restricted_dataset_v0_profile_001.yaml'
SCOPES = ('SOURCE_REGISTRATION', 'FIRST_PARTY_INTAKE', 'FIRST_PARTY_RIGHTS',
          'RESTRICTED_TRAINING_USE', 'PUBLIC_DERIVATIVE_RELEASE',
          'TEMPLATE_DERIVED_RELEASE', 'RESTRICTED_PROFILE', 'FOUR_PARTITION_CLOSURE')
SCOPE_MEANING = {
    'SOURCE_REGISTRATION':'Register only the exact first-party source/catalog/generator versions for restricted development.',
    'FIRST_PARTY_INTAKE':'Authorize local generation intake of the exact approved finite templates and candidate membership; no download is claimed.',
    'FIRST_PARTY_RIGHTS':'Confirm project-authored patterns and fictional bindings; no external utterance or private user data copied; publication prohibited.',
    'RESTRICTED_TRAINING_USE':'Authorize restricted TRAIN/DEVELOPMENT/CALIBRATION/SELECTION and later Plan 08 development use; no training now.',
    'PUBLIC_DERIVATIVE_RELEASE':'Conditionally release exact 1,762 MASSIVE/CLINC derivatives only after signed semantics and all non-human gates.',
    'TEMPLATE_DERIVED_RELEASE':'Conditionally release exact reviewed-template descendants after row-level deterministic gates, without claiming individual human semantic review.',
    'RESTRICTED_PROFILE':'Authorize only RESTRICTED internal Dataset-v0, with publication and redistribution prohibited.',
    'FOUR_PARTITION_CLOSURE':'Authorize four development partitions; independent final evaluation remains reserved.'}


def catalog():
    result = yaml.load(CATALOG.read_text(encoding='utf-8'), Loader=UniqueLoader)
    old = template_review.context()
    policy = dict(old['policy'], source_version=result['catalog_version'])
    template_review.validate_catalog(result, policy)
    if len(result['templates']) != 2 or {t['capability_group'] for t in result['templates']} != {'CALENDAR', 'GOAL_PROJECT'}:
        raise DataError('EXACTLY_TWO_CLOSURE_TEMPLATES_REQUIRED')
    if {t['capability_group']:t['planned_max_rows'] for t in result['templates']} != {'CALENDAR':4,'GOAL_PROJECT':20}:
        raise DataError('REVIEWED_FINITE_RESERVE_CHANGED')
    if any(t['disposition'] != 'SUPPORTED_COMPLETE' for t in result['templates']):
        raise DataError('COMPLETE_EXAMPLES_REQUIRED')
    return result


def source_definition():
    definition = yaml.load(SOURCE.read_text(encoding='utf-8'), Loader=UniqueLoader)
    cat = catalog()
    if (definition['definition_version'] != 'nlu_v1_internal_source_definition_001' or
        definition['source_id'] != cat['source_id'] or definition['first_party'] is not True or
        definition['privacy'] != 'RESTRICTED' or definition['publication_allowed'] is not False or
        definition['external_or_private_inputs'] is not False or
        definition['allowed_partitions'] != ['TRAIN','DEVELOPMENT','CALIBRATION','SELECTION']):
        raise DataError('FIRST_PARTY_SOURCE_DEFINITION_INVALID')
    old = template_review.context()['catalog']
    catalogs = {c['catalog_version']: {'artifact_sha256':exception_packet.file_hash(ROOT / p),
        'template_sha256':{t['template_id']:sha_json(t) for t in c['templates']}}
        for c,p in ((old,definition['catalog_paths'][0]),(cat,definition['catalog_paths'][1]))}
    return dict(definition=definition, catalogs=catalogs,
        generator_sha256=exception_packet.file_hash(ROOT / definition['generator_path']),
        source_definition_sha256=exception_packet.file_hash(SOURCE))


def context():
    cat = catalog()
    source = source_definition()
    existing = approved_supplement.DESTINATION
    approved_supplement.verify_files(existing, inputs=True)
    selected = read_jsonl(existing/'retained_candidates.jsonl')
    public = [r for r in selected if r['source']['dataset'] in ('massive_1_1_en_us','clinc150_full')]
    synthetic = [r for r in selected if r['source']['dataset'] == 'metis_internal_coverage_v1']
    if len(public) != 1762 or len(synthetic) != 139 or len(selected) != 1901:
        raise DataError('CLOSURE_CANDIDATE_MEMBERSHIP_CHANGED')
    paths = [CATALOG,SOURCE,PROFILE,ROOT/'ml/nlu_v1/configs/synthetic_review_policy_002.yaml',
        ROOT/'ml/nlu_v1/configs/privacy_policy_003.yaml',
        ROOT/'ml/nlu_v1/configs/dataset_v0_coverage_policy.yaml',
        ROOT/'ml/nlu_v1/synthetic/generate.py',Path(__file__),
        ROOT/'ml/nlu_v1/dataset_v0/restricted_close.py',
        existing/'manifest.json',
        ROOT/'ml/nlu_v1/datasets/quarantine/plan07-restricted-training-003/manifest.json',
        review_recording.signed_batch(review_recording.FOLDER,'human-review-001')/'batch_manifest.json',
        template_review.FOLDER/'signed-template-human-001/manifest.json']
    bindings = {p.relative_to(ROOT).as_posix():exception_packet.file_hash(p) for p in paths}
    return dict(version=VERSION, catalog_version=cat['catalog_version'],
        templates={t['template_id']:t for t in cat['templates']}, source=source,
        profile=yaml.load(PROFILE.read_text(encoding='utf-8'), Loader=UniqueLoader),
        current_public_rows=len(public),current_synthetic_rows=len(synthetic),
        current_row_sha256={r['id']:sha_json(r) for r in selected},
        public_member_sha256=sha_json([[r['id'],sha_json(r)] for r in public]),
        existing_synthetic_member_sha256=sha_json([[r['id'],sha_json(r)] for r in synthetic]),
        files_sha256=bindings, old_signed_template_batch='template-human-001',
        historical_semantic_batch='human-review-001',
        future_ordinal_limits={t['template_id']:t['planned_max_rows'] for t in cat['templates']})


def targets(ctx):
    entries={}
    for ident,t in ctx['templates'].items():
        entries[ident]=dict(kind='TEMPLATE_SEMANTIC_REVIEW',target_id=ident,
            template_sha256=sha_json(t),catalog_version=ctx['catalog_version'],
            fixed_domain=t['domain'],fixed_action=t['action'],fixed_disposition=t['disposition'],
            family=t['family'],scenario_id=t['scenario_id'],parent=t['semantic_parent'],
            generation_seed=t['generation_seed'],generator_sha256=ctx['source']['generator_sha256'],
            finite_ordinal_limit=t['planned_max_rows'])
    for scope in SCOPES:
        entries['scope:'+scope]=dict(kind='OPERATOR_AUTHORIZATION',target_id='scope:'+scope,
            scope=scope, source_binding_sha256=sha_json(ctx['source']),
            current_member_sha256=sha_json(ctx['current_row_sha256']),
            public_member_sha256=ctx['public_member_sha256'],
            existing_synthetic_member_sha256=ctx['existing_synthetic_member_sha256'],
            future_template_sha256={i:sha_json(t) for i,t in ctx['templates'].items()},
            future_ordinal_limits=ctx['future_ordinal_limits'],
            profile_sha256=sha_json(ctx['profile']),publication_allowed=False,
            independent_final_reserved=True)
    return entries


def draft(binding,ctx):
    return dict(record_version=VERSION,kind=binding['kind'],binding=binding,
        context_sha256=sha_json({k:v for k,v in ctx.items() if k!='templates'}),decision='PENDING',
        requested_change=None,reviewer_id=None,reviewed_at=None,reviewer_note=None,
        reviewer_registry_sha256=None,signature_ed25519_base64=None)


def document(ctx):
    lines=['# LOCAL restricted Dataset-v0 closure decisions','',
        'These are ten separate decisions. Nothing is released or frozen by editing this file.',
        'Change only Decision cells. Allowed template choices: APPROVE, REJECT, CHANGE. Operator scopes: APPROVE, REJECT.',
        'CHANGE requires a requested_change in its draft JSON and a new reviewed template version before use.',
        'Authorization is restricted to exact bound source, current 1,901 candidates and finite new-template ordinals; publication remains prohibited.',
        'Independent final evaluation is reserved and inaccessible to this development dataset.','','| Target | Meaning | Decision |','|---|---|---|']
    for ident,t in ctx['templates'].items():
        lines.append(f"| {ident} | {t['capability_group']} complete, {t['planned_max_rows']} finite ordinals | PENDING |")
    for scope in SCOPES:
        lines.append(f'| scope:{scope} | {SCOPE_MEANING[scope]} | PENDING |')
    for ident,t in ctx['templates'].items():
        lines += ['', '## '+ident,'',f"Hash: `{sha_json(t)}`. Domain {t['domain']}; action {t['action']}; disposition {t['disposition']}; family `{t['family']}`; scenario `{t['scenario_id']}`; parent `{t['semantic_parent']}`; slots {', '.join(t['allowed_slots'])}; seed {t['generation_seed']}; generator `{t['generator_version']}`.",
            '',f"Finite capacity: {t['planned_max_rows']} rows. All patterns and fictional bindings below are review material:",'']
        lines.extend('- '+form for form in t['forms'])
        for name,b in sorted(t['bindings'].items()):
            lines.append(f"- {name} ({b['type']}): {'; '.join(b['values'])}")
        lines += ['','Three example renderings (illustrations, not generated dataset rows):','']
        lines.extend('- '+template_review.preview(t,i)['text'] for i in range(3))
        lines += ['','Generated-row constraints: exact approved pattern/binding/ordinal membership, fixed labels and lineage, UTF-16/BIO/schema checks, RESTRICTED, publication prohibited, signed template authority and separate source/intake/rights/training/release gates.','']
    lines += ['## Recording workflow','','Review interactively: `python -m ml.nlu_v1.dataset_v0.closure_review review`',
        'If edited manually: `python -m ml.nlu_v1.dataset_v0.closure_review sync`',
        'Sign with an existing enrolled reviewer key (no key is created here):',
        f'`python -m ml.nlu_v1.dataset_v0.closure_review sign --batch-id {BATCH} --reviewer-id YOUR_ENROLLED_ID --key-file C:/PATH_OUTSIDE_REPO/reviewer-ed25519.pem --reviewed-at YYYY-MM-DDTHH:MM:SSZ --reason "YOUR_REVIEW_REASON"`',
        f'`python -m ml.nlu_v1.dataset_v0.closure_review verify --batch-id {BATCH}`','']
    return '\n'.join(lines)+'\n'


def parse_document(text,ctx):
    found={}; normalized=[]; expected=targets(ctx)
    for line in text.splitlines():
        if line.startswith('| internalcov:') or line.startswith('| scope:'):
            parts=line.split('|'); ident=parts[1].strip()
            if len(parts)!=5 or ident not in expected or ident in found:
                raise DataError('CLOSURE_DECISION_TABLE_INVALID')
            choice=parts[-2].strip()
            permitted={'PENDING','APPROVE','REJECT','CHANGE'} if ident.startswith('internalcov:') else {'PENDING','APPROVE','REJECT'}
            if choice not in permitted:raise DataError('CLOSURE_DECISION_INVALID')
            found[ident]=choice;parts[-2]=' PENDING ';line='|'.join(parts)
        normalized.append(line)
    if set(found)!=set(expected) or '\n'.join(normalized)+'\n'!=document(ctx):
        raise DataError('CLOSURE_PACKET_CONTENT_CHANGED')
    return found


def filename(ident):return ident.replace(':','-')+'.json'


def local(folder):
    template_review.require_local(folder)


def prepare(folder=FOLDER):
    folder=Path(folder);local(folder)
    if folder.exists():raise DataError('CLOSURE_PACKET_EXISTS')
    ctx=context();folder.mkdir();(folder/'decisions').mkdir()
    for ident,b in targets(ctx).items():atomic_new(folder/'decisions'/filename(ident),json_bytes(draft(b,ctx)))
    atomic_new(folder/'DECISIONS.md',document(ctx).encode())
    atomic_new(folder/'packet.json',json_bytes(dict(version=VERSION,context=ctx,
        target_sha256={i:sha_json(b) for i,b in targets(ctx).items()},generated_rows=0,released_rows=0)))
    atomic_new(folder/'manifest.json',json_bytes(dict(version=VERSION,packet_sha256=exception_packet.file_hash(folder/'packet.json'))))
    return dict(semantic_targets=2,operator_scopes=8,generated_rows=0,released_rows=0)


def load(folder=FOLDER):
    folder=Path(folder);local(folder);ctx=context()
    m=exception_packet.read_json(folder/'manifest.json')
    if m.get('version')!=VERSION or m.get('packet_sha256')!=exception_packet.file_hash(folder/'packet.json'):
        raise DataError('CLOSURE_PACKET_HASH_CHANGED')
    packet=exception_packet.read_json(folder/'packet.json')
    if packet.get('context')!=ctx or packet.get('target_sha256')!={i:sha_json(b) for i,b in targets(ctx).items()}:
        raise DataError('CLOSURE_BOUND_CONTEXT_CHANGED')
    decisions=parse_document((folder/'DECISIONS.md').read_text(encoding='utf-8'),ctx)
    records={}
    for ident,b in targets(ctx).items():
        record=exception_packet.read_json(folder/'decisions'/filename(ident));d=draft(b,ctx)
        if set(record)!=set(d) or any(record[k]!=d[k] for k in ('record_version','kind','binding','context_sha256')):
            raise DataError('CLOSURE_RECORD_BINDING_CHANGED')
        records[ident]=record
    return ctx,decisions,records


def sync(folder=FOLDER):
    ctx,selected,records=load(folder)
    if any(r['signature_ed25519_base64'] is not None for r in records.values()):
        raise DataError('SIGNED_DRAFT_CANNOT_BE_EDITED')
    for ident,r in records.items():
        r['decision']=selected[ident]
        atomic_replace(Path(folder)/'decisions'/filename(ident),json_bytes(r))
    return dict(decisions=dict(Counter(selected.values())))


def verify_record(record,ctx,*,approved=False):
    ident=record.get('binding',{}).get('target_id');expected=targets(ctx)
    if ident not in expected or set(record)!=set(draft(expected[ident],ctx)) or record['binding']!=expected[ident] or record['context_sha256']!=draft(expected[ident],ctx)['context_sha256']:
        raise DataError('CLOSURE_SIGNATURE_BINDING_CHANGED')
    if record['decision'] not in ({'APPROVE','REJECT','CHANGE'} if ident.startswith('internalcov:') else {'APPROVE','REJECT'}):
        raise DataError('EXPLICIT_CLOSURE_DECISION_REQUIRED')
    if approved and record['decision']!='APPROVE':raise DataError('CLOSURE_DECISION_NOT_APPROVED')
    if (record['decision']=='CHANGE') != bool(record['requested_change']):raise DataError('CHANGE_DETAILS_INVALID')
    registry=semantic_groups.load_reviewers();reviewer=record['reviewer_id']
    if reviewer not in registry['reviewers'] or reviewer.startswith(('machine:','audit:','validator:','import:','group:')) or record['reviewer_registry_sha256']!=exception_packet.file_hash(semantic_groups.REVIEWERS):
        raise DataError('ENROLLED_REVIEWER_REQUIRED')
    check_utc(record['reviewed_at'])
    if datetime.fromisoformat(record['reviewed_at'].replace('Z','+00:00'))>datetime.now(timezone.utc) or not isinstance(record['reviewer_note'],str) or not record['reviewer_note'].strip():
        raise DataError('REAL_REVIEW_TIME_AND_REASON_REQUIRED')
    try:
        key=Ed25519PublicKey.from_public_bytes(base64.b64decode(registry['reviewers'][reviewer]['public_key_ed25519_base64'],validate=True))
        key.verify(base64.b64decode(record['signature_ed25519_base64'],validate=True),semantic_groups.signed_bytes(record))
    except Exception as exc:raise DataError('CLOSURE_SIGNATURE_INVALID') from exc
    return True


def sign(folder,batch_id,key,reviewer,reviewed_at,reason):
    ctx,selected,records=load(folder)
    if any(r['decision']!=selected[i] or r['decision']=='PENDING' for i,r in records.items()):
        raise DataError('SYNC_ALL_DECISIONS_BEFORE_SIGNING')
    dest=Path(folder)/('signed-'+batch_id)
    if dest.exists():raise DataError('SIGNED_CLOSURE_BATCH_EXISTS')
    signed=[]
    for r in records.values():
        r=copy.deepcopy(r);r.update(reviewer_id=reviewer,reviewed_at=reviewed_at,
            reviewer_note=reason,reviewer_registry_sha256=exception_packet.file_hash(semantic_groups.REVIEWERS))
        r['signature_ed25519_base64']=base64.b64encode(key.sign(semantic_groups.signed_bytes(r))).decode()
        verify_record(r,ctx);signed.append(r)
    dest.mkdir();files={}
    for r in signed:
        name=filename(r['binding']['target_id']);atomic_new(dest/name,json_bytes(r));files[name]=exception_packet.file_hash(dest/name)
    atomic_new(dest/'manifest.json',json_bytes(dict(version=VERSION,files=files)))
    return dict(signed_decisions=len(signed))


def verify_batch(folder=FOLDER,batch_id=BATCH,*,require_all_approved=True):
    ctx,selected,_=load(folder);dest=Path(folder)/('signed-'+batch_id)
    m=exception_packet.read_json(dest/'manifest.json')
    if m.get('version')!=VERSION or set(m.get('files',{}))!={filename(i) for i in targets(ctx)} or {p.name for p in dest.iterdir()}!=set(m['files'])|{'manifest.json'}:
        raise DataError('CLOSURE_BATCH_INCOMPLETE')
    records={}
    for ident in targets(ctx):
        path=dest/filename(ident)
        if m['files'][path.name]!=exception_packet.file_hash(path):raise DataError('CLOSURE_RECORD_CHANGED')
        r=exception_packet.read_json(path);verify_record(r,ctx,approved=require_all_approved)
        if r['decision']!=selected[ident]:raise DataError('CLOSURE_DECISION_SUPERSEDED')
        records[ident]=r
    return ctx,records


def review(folder=FOLDER,*,key_input=None,output=print):
    key_input=key_input or read_key
    with reviewer_lock(folder):
        ctx,selected,records=load(folder);ids=list(targets(ctx));cursor_path=Path(folder)/'session.json'
        state=exception_packet.read_json(cursor_path) if cursor_path.exists() else {'cursor':0}
        if type(state.get('cursor')) is not int or not 0<=state['cursor']<len(ids):raise DataError('CLOSURE_CURSOR_INVALID')
        while True:
            ident=ids[state['cursor']];output(f'{state["cursor"]+1}/{len(ids)} {ident} [{selected[ident]}]')
            if ident in ctx['templates']:
                t=ctx['templates'][ident]
                output(f'{t["domain"]}/{t["action"]}/{t["disposition"]}; {t["family"]}; {t["scenario_id"]}; SHA {sha_json(t)}')
                output('Patterns: '+' | '.join(t['forms']))
                output('Examples: '+' | '.join(template_review.preview(t,i)['text'] for i in range(3)))
            else:output(f'Operator scope: {ident[6:]}; {SCOPE_MEANING[ident[6:]]}; exact bindings in packet.json')
            output('1 APPROVE | 2 REJECT | 3 CHANGE (template only) | N next | P previous | Q save and quit')
            try:key=key_input().strip().upper()
            except (EOFError,KeyboardInterrupt):key='Q'
            if key in ('1','2','3'):
                if key=='3' and ident not in ctx['templates']:output('CHANGE unavailable for scope');continue
                choice={'1':'APPROVE','2':'REJECT','3':'CHANGE'}[key]
                before=(Path(folder)/'DECISIONS.md').read_text(encoding='utf-8');line=next(l for l in before.splitlines() if l.startswith('| '+ident+' |'))
                parts=line.split('|');parts[-2]=' '+choice+' ';after=before.replace(line,'|'.join(parts),1)
                parse_document(after,ctx)
                if key=='3':
                    note=input('Requested change: ').strip()
                    if not note:output('CHANGE needs details');continue
                    records[ident]['requested_change']=note
                    atomic_replace(Path(folder)/'decisions'/filename(ident),json_bytes(records[ident]))
                elif records[ident]['requested_change'] is not None:
                    records[ident]['requested_change']=None
                    atomic_replace(Path(folder)/'decisions'/filename(ident),json_bytes(records[ident]))
                atomic_replace(Path(folder)/'DECISIONS.md',after.encode());selected[ident]=choice;sync(folder)
                state['cursor']=min(len(ids)-1,state['cursor']+1)
            elif key in ('N','P'):state['cursor']=max(0,min(len(ids)-1,state['cursor']+(1 if key=='N' else -1)))
            elif key!='Q':output('Invalid key');continue
            atomic_replace(cursor_path,json_bytes(state))
            if key=='Q':return


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('command',choices=('prepare','review','sync','check','sign','verify'))
    p.add_argument('--batch-id',default=BATCH);p.add_argument('--reviewer-id');p.add_argument('--key-file',type=Path)
    p.add_argument('--reviewed-at');p.add_argument('--reason');a=p.parse_args()
    if a.command=='prepare':result=prepare()
    elif a.command=='review':review();return
    elif a.command=='sync':
        with reviewer_lock(FOLDER):result=sync()
    elif a.command=='check':
        _,d,_=load();result=dict(decisions=dict(Counter(d.values())))
    elif a.command=='verify':
        _,r=verify_batch(batch_id=a.batch_id);result=dict(valid=len(r),approved=sum(x['decision']=='APPROVE' for x in r.values()))
    else:
        if not all((a.reviewer_id,a.key_file,a.reviewed_at,a.reason)):p.error('sign needs reviewer, key file, UTC time, reason')
        raw=a.key_file.read_bytes()
        try:key=load_pem_private_key(raw,password=None)
        except TypeError:
            from getpass import getpass
            key=load_pem_private_key(raw,password=getpass('Existing key password: ').encode())
        if not isinstance(key,Ed25519PrivateKey):raise DataError('EXISTING_ED25519_KEY_REQUIRED')
        with reviewer_lock(FOLDER):result=sign(FOLDER,a.batch_id,key,a.reviewer_id,a.reviewed_at,a.reason)
    import json
    print(json.dumps(result,sort_keys=True))


if __name__=='__main__':main()
