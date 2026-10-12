"""Separate native read outcomes, semantic classifier quality and exposed human drafts."""
import argparse
import base64
import json
import sys
from pathlib import Path
import numpy as np
from v12_data import HERE,ROOT,VERSION,digest,LABELS,aggregate
from v12_integrity import check_manifest
from v12_candidate import resolve,READ_LABELS
from v12_model import select,predict,metrics,export,exported_predict,bind,calibration_metrics

sys.path.insert(0,str(HERE.parent / 'read_coverage_v10'))
from v10_evaluate import snapshot,native_entities
OUTPUT=ROOT / 'app/build/reports/phase15/v12'


def check_reproduction(payload,path):
    if not path.exists(): return
    previous=json.loads(path.read_text(encoding='utf-8'))
    if bind(previous)!=previous['payload_sha256']: raise ValueError('Existing scored export drift')
    bindings=('data_sha256','model_sha256','protocol_sha256')
    if previous['heads']!=payload['heads'] or previous['selected']!=payload['selected'] or any(
        previous['bindings'][key]!=payload['bindings'][key] for key in bindings):
        raise ValueError('Do not retune a scored version; create a new candidate')


def outcomes(rows,native):
    details=[]
    for row in rows:
        plan=resolve(row['utterance'],source=row['input_source']);actual=native[row['id']]
        query=base64.b64decode(actual['query_base64']).decode()
        entities=native_entities(actual)
        prohibited=row['disposition']!='read'
        forced={label:resolve(row['utterance'],label,row['input_source'])['reads'] for label in READ_LABELS} if prohibited else {}
        if int(actual['reads'])!=plan['reads']: raise AssertionError('Native/Python admission drift: '+row['id'])
        if plan['reads'] and (query!=row.get('query_text','') or entities!=row['entities'] or actual['intent']!=row['intent']):
            raise AssertionError('Wrong retrieval scope: '+row['id'])
        if prohibited and (plan['reads'] or int(actual['reads']) or sum(forced.values())): raise AssertionError('Forbidden read: '+row['id'])
        message=base64.b64decode(actual['message_base64']).decode()
        if actual['status']=='FOLLOW_UP' and row['utterance'] not in message: raise AssertionError('Lost constraints')
        details.append(dict(id=row['id'],request_text=row['utterance'],expected_intent=row['intent'],expected_disposition=row['disposition'],
            requested_constraints=row.get('filters',{}),parsed=plan,native=actual,forced_label_reads=forced,
            correct_scoped_read=bool(plan['reads']),query_text=query,entities=entities))
    return dict(counts=dict(rows=len(rows),desired_reads=sum(r['disposition']=='read' for r in rows),
        correctly_scoped_reads=sum(d['correct_scoped_read'] for d in details),
        prohibited_rows=sum(r['disposition']!='read' for r in rows),
        prohibited_reads=sum(int(d['native']['reads']) for d,r in zip(details,rows) if r['disposition']!='read'),
        prohibited_context_calls=sum(int(d['native']['contexts']) for d,r in zip(details,rows) if r['disposition']!='read'),
        forced_label_attempts=sum(len(d['forced_label_reads']) for d in details),
        forced_label_reads=sum(sum(d['forced_label_reads'].values()) for d in details),
        dispositions={status:sum(d['native']['status']==status for d in details) for status in ('ANSWER','FOLLOW_UP','DENIED','UNSUPPORTED')},
        constraints_retained_in_followups=True),details=details)


def classifier_parts():
    base=json.loads((HERE.parent / 'classifier_v11/development-data.json').read_text(encoding='utf-8'))['rows']
    new=json.loads((HERE / 'classifier-development.json').read_text(encoding='utf-8'))['rows']
    parts={s:[r for r in base+new if r['split']==s and (s!='test' or r['id'].startswith('v12-'))]
           for s in ('train','calibration','selection','test')}
    return parts,[r for r in base if r['split']=='test']


def evaluate(native_path,output,previous_export=None):
    if not output.resolve().is_relative_to(OUTPUT.resolve()): raise ValueError('Reports must remain in ignored build output')
    manifest=check_manifest()
    rows=aggregate()
    actual=snapshot(native_path,HERE / 'regression-input.tsv',[r['id'] for r in rows])
    sections={s:outcomes([r for r in rows if r['section']==s],actual)
              for s in ('development','exposed','v9','v8','v7','v11','v12','user')}
    for s,target in (('development',78),('exposed',23),('v9',299),('v8',18),('v7',27),('v11',30),('v12',30),('user',4)):
        if sections[s]['counts']['correctly_scoped_reads']!=target: raise AssertionError('Coverage target: '+s)
    if sections['user']['counts']['prohibited_context_calls']: raise AssertionError('User nonread caused context access')
    parts,old_test=classifier_parts()
    model,selected,trials=select(parts['train'],parts['calibration'],parts['selection'])
    payload=export(model,selected,dict(data_sha256=digest(HERE / 'classifier-development.json'),
        model_sha256=digest(HERE / 'v12_model.py'),protocol_sha256=digest(HERE / 'protocol.json'),manifest_sha256=digest(HERE / 'development-manifest-v12.json')))
    previous_path=output.parent / 'disabled-candidate-v12.json'
    check_reproduction(payload,previous_path)
    all_rows=sum(parts.values(),[])
    labels,probabilities=predict(model,all_rows,selected['settings'])
    restored,values=exported_predict(payload,all_rows)
    if labels!=restored or not np.allclose(probabilities,values,atol=1e-12,rtol=1e-12): raise AssertionError('Numeric export parity')
    scores={name:dict(raw=metrics(values,predict(model,values)[0]),
        threshold=metrics(values,predict(model,values,selected['settings'])[0]),
        calibration=calibration_metrics(values,predict(model,values,selected['settings'])[1])) for name,values in parts.items()}
    human=[r for r in rows if r['section']=='user']
    diagnostics={name:dict(scope='EXPOSED development diagnostic; user annotations remain assistant draft',
        threshold=metrics(values,predict(model,values,selected['settings'])[0]),
        predictions=[dict(id=r['id'],utterance=r['utterance'],expected_draft=r['intent'],predicted=p,confidence=float(conf))
            for r,p,conf in zip(values,*[predict(model,values,selected['settings'])[0],predict(model,values,selected['settings'])[1].max(axis=1)])])
        for name,values in (('v11_test',old_test),('user_batch',human))}
    comparison=dict(available=False,reason='No retained numeric v11 export; historical candidate never refitted')
    if previous_export is not None and previous_export.exists():
        from v11_model import exported_predict as old_predict
        old=json.loads(previous_export.read_text(encoding='utf-8'))
        if old['bindings']['data_sha256']!=digest(HERE.parent / 'classifier_v11/development-data.json'): raise ValueError('Retained export data drift')
        old_labels,_=old_predict(old,parts['test'])
        comparison=dict(available=True,scope='Same 150 new synthetic test requests, not independent evaluation',
            v11=metrics(parts['test'],old_labels),v12=scores['test']['threshold'],retained_export_sha256=digest(previous_export),
            user_draft_v11=metrics(human,old_predict(old,human)[0]),
            user_draft_v12=diagnostics['user_batch']['threshold'],
            historical_retrained=False)
    report=dict(version=VERSION,decision='NO_GO',ml_enabled=False,ml_packaged=False,human_approved=False,
        scope='Exposed user development plus assistant-authored development; NOT fresh independent evaluation',
        selected=selected,trials=trials,classifier_scores=scores,exposed_diagnostics=diagnostics,same_test_comparison=comparison,
        sections=sections,fitting_audit={s:dict(rows=len(v),ids=[r['id'] for r in v],families=sorted({r['family_id'] for r in v})) for s,v in parts.items()},
        human_batch_fit_rows=0,scope_mismatches_fixed=10,python_numeric_export_parity=True,manifest=manifest,
        gates=dict(development_f1_0_90=scores['test']['threshold']['macro_f1']>=.90,
            development_unknown_rejection=scores['test']['threshold']['unknown_false_accepts']==0,
            independent_evaluation=False,representative_calibration=False,useful_ml_read_gain=False,android_model_parity_resources=False),
        limits=['Human query labels are assistant drafts, not user approval; separate-request context was explicitly confirmed.',
            'Intent recognition is distinct from supported filters, saved entity resolution and read authority.',
            'Two default context calls on v11 action commands remain distinct from zero forced-read-label calls.',
            'The classifier is offline; Android instrumentation tests deterministic code, not model latency or resources.'])
    check_manifest()
    output.parent.mkdir(parents=True,exist_ok=True)
    previous_path.write_text(json.dumps(payload,indent=2)+'\n',encoding='utf-8')
    output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    summary=dict(version=VERSION,decision='NO_GO',test=scores['test'],comparison=comparison,
        diagnostics={k:v['threshold'] for k,v in diagnostics.items()},sections={k:v['counts'] for k,v in sections.items()},gates=report['gates'])
    (output.parent / 'summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(test_f1=scores['test']['threshold']['macro_f1'],test_unknown_accepts=scores['test']['threshold']['unknown_false_accepts'],
        raw_f1=scores['test']['raw']['macro_f1'],exposed_v11_f1=diagnostics['v11_test']['threshold']['macro_f1'],
        user_draft_f1=diagnostics['user_batch']['threshold']['macro_f1'],selected=selected,sections=summary['sections']),indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native',type=Path,default=OUTPUT / 'native-regressions.tsv')
    parser.add_argument('--output',type=Path,default=OUTPUT / 'development-report.json')
    parser.add_argument('--previous-export',type=Path)
    args=parser.parse_args();evaluate(args.native,args.output,args.previous_export)
