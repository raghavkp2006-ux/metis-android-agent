"""Evaluate v5 development, exposed regressions, slots, and dispositions separately."""
import argparse
import json
from pathlib import Path
from datetime import datetime
from zoneinfo import ZoneInfo

import numpy as np
from threadpoolctl import threadpool_limits

from data import HERE, ROOT, V4, PARTITIONS, READ_LABELS, sources, old
from corpus import atomic_text, read_csv, sha256, baseline_source_hash
from language import guard, predict, resolve, train, feature_text, extract, filters, calibrate
import v4_evaluate as previous

def combine(rows,labels,baseline):
    output=[]
    for row,label in zip(rows,labels):
        rule=baseline[row['id']]['intent']
        if rule not in {*READ_LABELS,'UNKNOWN'}:output.append(rule)
        elif guard(row['utterance'],row['input_source']):output.append('UNKNOWN')
        else:output.append(rule if rule in READ_LABELS else label)
    return output

def resolved(row,label):
    # Snapshot date and captured zone, not the machine's wall clock.
    reference=datetime.fromisoformat(row['timestamp'].replace('Z','+00:00')).astimezone(ZoneInfo(row['zone'])).date().isoformat()
    return resolve(row['utterance'],label,row['input_source'],reference)

def select(parts,annotations):
    if set(parts)!=set(PARTITIONS) or any(r['split']!=p for p,rows in parts.items() for r in rows):
        raise ValueError('Wrong partitions')
    candidates=[]
    for characters in (False,True):
        for C in (1.,4.,16.):
            with threadpool_limits(limits=1):model=train(parts['train'],C,characters)
            calibration=[r for r in parts['calibration'] if not guard(r['utterance'],r['input_source'])]
            if {r['intent'] for r in calibration}!=set(old.LABELS):raise ValueError('Incomplete eligible calibration')
            temperature,calibration_metrics=calibrate(model,calibration)
            options=[]
            for cutoff in previous.v2.THRESHOLDS:
                labels,_=predict(model,parts['threshold'],temperature,cutoff)
                metrics=previous.v3.metrics(parts['threshold'],labels)
                unsafe=sum(annotations[r['id']]['disposition']!='read' and resolved(r,l)['reads'] for r,l in zip(parts['threshold'],labels))
                if not metrics['unknown_false_accepts'] and not unsafe and metrics['accepted_count']:
                    options.append((metrics['macro_f1'],metrics['read_coverage'],-cutoff,cutoff))
            cutoff=max(options)[-1] if options else None
            labels,_=predict(model,parts['threshold'],temperature,cutoff)
            metrics=previous.v3.metrics(parts['threshold'],labels)
            item=dict(model=model,characters=characters,C=C,temperature=temperature,threshold=cutoff,
                      calibration={**calibration_metrics,'eligible_rows':len(calibration)},validation=metrics)
            candidates.append(item)
            print(json.dumps(dict(selection='threshold families only',characters=characters,C=C,threshold=cutoff,f1=metrics['macro_f1'])),flush=True)
    best=max(candidates,key=lambda c:(c['validation']['macro_f1'],c['validation']['read_coverage'],not c['characters'],-c['C']))
    return best,[{k:v for k,v in c.items() if k!='model'} for c in candidates]

def score(rows,candidate,baseline,annotations=None):
    labels,probabilities=predict(candidate['model'],rows,candidate['temperature'],candidate['threshold'])
    combined=combine(rows,labels,baseline)
    projected=previous.v3.project_read_labels(combined)
    details=[];counts=dict(unsafe_simulated_reads=0,annotated_entities=0,exact_entities=0,
                          annotated_dispositions=0,correct_dispositions=0,annotated_filters=0,correct_filter_fields=0,
                          isolated_exact_entities=0,isolated_correct_filter_fields=0,
                          native_nonread_matches=0,preserved_native_nonread_matches=0)
    for row,learned,joined,full,p in zip(rows,labels,projected,combined,probabilities):
        outcome=resolved(row,joined)
        expected=(annotations or {}).get(row['id'])
        if row['intent']=='UNKNOWN' or expected and expected['disposition']!='read':
            counts['unsafe_simulated_reads']+=outcome['reads']
        native=baseline[row['id']]['intent']
        if native not in {*READ_LABELS,'UNKNOWN'}:
            counts['native_nonread_matches']+=1
            counts['preserved_native_nonread_matches']+=full==native
        if expected:
            counts['annotated_dispositions']+=1
            counts['correct_dispositions']+=outcome['disposition']==expected['disposition']
            if expected['entities']:
                counts['annotated_entities']+=1
                counts['exact_entities']+=outcome['entities']==expected['entities']
                counts['isolated_exact_entities']+=extract(row['utterance'],row['intent'])==expected['entities']
            if expected['filters']:
                counts['annotated_filters']+=1
                counts['correct_filter_fields']+=all(outcome['filters'].get(k)==v for k,v in expected['filters'].items())
                isolated=filters(row['utterance'],row['intent'])
                counts['isolated_correct_filter_fields']+=all(isolated.get(k)==v for k,v in expected['filters'].items())
        details.append(dict(id=row['id'],utterance=row['utterance'],expected_intent=row['intent'],native_intent=native,
            learned_intent=learned,hybrid_intent=joined,confidence=float(p.max()),correct_intent=joined==row['intent'],
            expected=expected,parsed=outcome))
    eligible=[i for i,r in enumerate(rows) if not guard(r['utterance'],r['input_source'])]
    calibration=previous.v2.calibration_metrics([rows[i]['intent'] for i in eligible],candidate['model'].classes_,probabilities[eligible]) if eligible else None
    return dict(hybrid=previous.v3.metrics(rows,projected),learned=previous.v3.metrics(rows,labels),
                rules=previous.v3.metrics(rows,previous.v3.project_read_labels([baseline[r['id']]['intent'] for r in rows])),
                classifier_calibration_eligible_only=calibration,behavior=counts,details=details)

def export(candidate,path):
    model=candidate['model'];features=model.named_steps['features']
    payload=dict(format='METIS_DEVELOPMENT_LINEAR_READ_V2',version='v5',enabled=False,packaged=False,
        labels=model.classes_.tolist(),features=[dict(name=name,weight=features.transformer_weights[name],
            vocabulary={k:int(v) for k,v in vectorizer.vocabulary_.items()},idf=vectorizer.idf_.tolist(),
            analyzer=vectorizer.analyzer,ngram_range=vectorizer.ngram_range,token_pattern=vectorizer.token_pattern,
            sublinear_tf=True,norm='l2') for name,vectorizer in features.transformer_list],
        coefficients=model.named_steps['classifier'].coef_.tolist(),intercept=model.named_steps['classifier'].intercept_.tolist(),
        temperature=candidate['temperature'],threshold=candidate['threshold'],preprocessing_source_sha256=sha256(HERE/'language.py'),
        dataset_manifest_sha256=sha256(HERE/'manifest-v5.json'),limit='Python development only; guard/resolution required; no Android parity')
    atomic_text(path,json.dumps(payload,separators=(',',':'))+'\n')
    return dict(bytes=path.stat().st_size,sha256=sha256(path),packaged=False)

def evaluate(baseline_path,output,reviewed_path=None,reviewed_baseline=None,record_reference=False):
    manifest=json.loads((HERE/'manifest-v5.json').read_text())
    if sources()!=manifest['sources']:raise ValueError('Sources changed; explicitly regenerate manifest')
    for name,digest in manifest['hashes'].items():
        if sha256(HERE/name)!=digest:raise ValueError('Data drift')
    for name,digest in manifest['regressions'].items():
        if sha256(ROOT/name)!=digest:raise ValueError('Historical corpus drift')
    rows=read_csv(HERE/'intent-v5.csv');old.validate(rows)
    annotations=json.loads((HERE/'annotations-v5.json').read_text(encoding='utf-8'))
    user=read_csv(HERE.parent/'independent_125/queries.csv')
    earlier=read_csv(old.V3/'user-challenge-2026-10-10.csv')
    historical=read_csv(HERE.parent/'safety-v2.csv')
    baseline=previous.v2.load_baseline(baseline_path,rows+user+earlier+historical,sha256(HERE/'rules-input-v5.tsv'),baseline_source_hash(ROOT))
    native=previous.v2.safety_metrics(historical,baseline)
    if native['failures']:raise ValueError('Native historical regression')
    candidate,selection=select({p:[r for r in rows if r['split']==p] for p in PARTITIONS},annotations)
    development=score([r for r in rows if r['split']=='test'],candidate,baseline,annotations)
    exposed=score(user,candidate,baseline)
    initial=score(earlier,candidate,baseline)
    strict=score([r for r in historical if r['intent']=='UNKNOWN' and r['case_kind']!='typo'],candidate,baseline)
    # Preserve the original legacy score. One old language-rejection expectation
    # conflicts with the user's newly approved limited Hinglish coverage.
    scope_changes=[r for r in historical if r['id']=='p15-safety-024']
    if len(scope_changes)!=1 or scope_changes[0]['utterance']!='mere tasks dikhao':raise ValueError('Unexpected legacy scope fixture')
    current_strict=score([r for r in historical if r['intent']=='UNKNOWN' and r['case_kind']!='typo' and r['id']!='p15-safety-024'],candidate,baseline)
    artifact_path=output.parent/'read-query-v5-development-model.json'
    artifact=export(candidate,artifact_path)
    # Reuse prior independently implemented serialized feature math, substituting v5 preprocessing locally.
    previous.feature_text=feature_text
    parity=previous.export_parity(candidate,artifact_path,[r for r in rows if r['split']=='test']+user+earlier)
    report=dict(version=manifest['version'],data=manifest['data'],candidate={k:v for k,v in candidate.items() if k not in {'model','validation'}},
        provenance=dict(manifest_sha256=sha256(HERE/'manifest-v5.json'),native_sha256=sha256(baseline_path),native_source_sha256=baseline_source_hash(ROOT)),
        selection=selection,development=development,exposed125=exposed,exposed25=initial,historical_strict=strict,
        historical_current_scope=current_strict,
        legacy_scope_changes=[dict(id='p15-safety-024',utterance='mere tasks dikhao',old_expectation='UNKNOWN: unsupported language',
            new_scope='User approved limited Roman-script Hinglish: eligible task query; original fixture and raw score preserved')],native_historical=native,
        artifact=artifact,export_parity=parity,decision='NO_GO',adoption_eligible=False,rules_retained=True,
        gates=dict(independent_reviewed_evaluation=False,representative_calibration=False,real_repository_policy_outcomes=False,android_parity_and_devices=False),
        limit=manifest['limit'])
    if reviewed_path:
        if reviewed_baseline is None:raise ValueError('Fresh benchmark requires its own native snapshot')
        frozen=json.loads(reviewed_path.read_text(encoding='utf-8'))
        if frozen['candidate_manifest_sha256']!=sha256(HERE/'manifest-v5.json'):raise ValueError('Reviewed benchmark bound to another candidate')
        receipt=output.parent/('v5-review-consumed-'+sha256(reviewed_path)+'.json')
        if receipt.exists():raise ValueError('Benchmark already exposed; preserve first result, obtain fresh examples after tuning')
        input_path=reviewed_path.with_suffix('.tsv')
        if sha256(input_path)!=frozen['input_sha256']:raise ValueError('Reviewed native input drift')
        reviewed_rows=frozen['rows']
        reviewed_native=previous.v2.load_baseline(reviewed_baseline,reviewed_rows,sha256(input_path),baseline_source_hash(ROOT))
        atomic_text(receipt,json.dumps(dict(benchmark_sha256=sha256(reviewed_path),candidate_manifest_sha256=sha256(HERE/'manifest-v5.json'),
            scope='Exposure receipt; not access control. First result must be retained.'))+'\n')
        reviewed=score(reviewed_rows,candidate,reviewed_native,frozen['annotations'])
        report['reviewed']=reviewed
        behavior=reviewed['behavior']
        report['gates']['independent_reviewed_evaluation']=(reviewed['hybrid']['macro_f1']>=.90 and not behavior['unsafe_simulated_reads']
            and behavior['correct_dispositions']==behavior['annotated_dispositions']
            and all(d['parsed']['entities']==d['expected']['entities'] and d['parsed']['filters']==d['expected']['filters']
                    for d in reviewed['details']))
        report['reviewed_limit']=frozen['limit']+'; simulated Python outcomes only; remaining adoption gates still unmet'
    atomic_text(output,json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    compact=json.loads(json.dumps(report))
    for key in ('development','historical_strict','historical_current_scope'):compact[key].pop('details')
    if record_reference:atomic_text(HERE/'results-v5.json',json.dumps(compact,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps({key:{'intent':report[key]['hybrid'],'behavior':report[key]['behavior']} for key in ('development','exposed125','exposed25')},indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--baseline',type=Path,default=ROOT/'app/build/reports/phase15/rules-v5.tsv')
    parser.add_argument('--output',type=Path,default=ROOT/'app/build/reports/phase15/results-v5.json')
    parser.add_argument('--reviewed-benchmark',type=Path)
    parser.add_argument('--reviewed-baseline',type=Path)
    parser.add_argument('--record-reference',action='store_true')
    args=parser.parse_args();evaluate(args.baseline,args.output,args.reviewed_benchmark,args.reviewed_baseline,args.record_reference)
