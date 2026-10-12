"""V11 development experiment plus exposed regressions. Never independent evaluation."""
import argparse
import base64
import json
import platform
import sys
from collections import Counter
from pathlib import Path

import numpy as np
import scipy
import sklearn

from v11_data import HERE, ROOT, LABELS, PARTITIONS, VERSION, digest, validate
from v11_integrity import check_manifest
from v11_model import select, predict, metrics, calibration_metrics, export, exported_predict, bind

sys.path.insert(0,str(HERE.parent / 'read_coverage_v10'))
from v10_candidate import resolve, READ_LABELS
from v10_evaluate import snapshot, native_entities

OUTPUT_ROOT=ROOT / 'app/build/reports/phase15/v11'


def output_path(path):
    if not path.resolve().is_relative_to(OUTPUT_ROOT.resolve()):
        raise ValueError('V11 reports/exports must remain in ignored build output')


def check_reproduction(payload, previous_path):
    if not previous_path.exists(): return
    previous=json.loads(previous_path.read_text(encoding='utf-8'))
    if bind(previous)!=previous['payload_sha256']: raise ValueError('Previously scored payload drift')
    # Report/document bindings can refresh, but observed results cannot silently
    # change this version's data, model, protocol, weights or selection.
    for key in ('data_sha256','model_source_sha256','protocol_sha256'):
        if previous['bindings'][key]!=payload['bindings'][key]:
            raise ValueError('Previously scored candidate changed; create a new version')
    for key in ('features','coefficients','intercept','selected','labels'):
        if previous[key]!=payload[key]: raise ValueError('Scored numeric candidate changed: '+key)


def section(rows, native, model, selected):
    accepted,probabilities=predict(model,rows,selected['settings'])
    raw,_=predict(model,rows)
    details=[]
    for row,label,raw_label,confidence in zip(rows,accepted,raw,probabilities.max(axis=1)):
        actual=native[row['id']]
        baseline=resolve(row['utterance'],source=row['input_source'])
        suggested=resolve(row['utterance'],label,row['input_source'])
        # Keep valid native reads. A model-only suggestion still needs the same
        # fixed full-request grammar, entities and complete retrieval subject.
        hybrid_reads=int(bool(int(actual['reads']) or suggested['reads']))
        query=base64.b64decode(actual['query_base64']).decode() if int(actual['reads']) else suggested['query_text']
        entities=native_entities(actual) if int(actual['reads']) else suggested['entities'] if suggested['reads'] else []
        prohibited=row['disposition']!='read'
        forced={intent:resolve(row['utterance'],intent,row['input_source'])['reads'] for intent in READ_LABELS} if prohibited else {}
        hybrid_label=baseline['intent'] if baseline['intent']!='UNKNOWN' else label
        # Keep native raw intent, semantic model output and disposition separate.
        disposition='read' if hybrid_reads else 'unsupported' if baseline['disposition']=='unsupported' else 'clarify'
        native_disposition=('read' if int(actual['reads']) else 'unsupported' if actual['status']=='UNSUPPORTED' else
                            'reject' if actual['status']=='DENIED' else 'clarify')
        details.append(dict(id=row['id'],request_text=row['utterance'],expected_intent=row['intent'],
            expected_disposition=row['disposition'],requested_constraints=row.get('filters',{}),
            expected_entities=row['entities'],expected_query=row.get('query_text',''),
            raw_label=raw_label,candidate_label=label,confidence=float(confidence),hybrid_label=hybrid_label,
            native_label=actual['intent'] if actual['intent'] in LABELS else 'UNKNOWN',
            native_reads=int(actual['reads']),native_contexts=int(actual['contexts']),
            native_proposals=int(actual['proposals']),native_completed=int(actual['completed']),
            dispatched_intent=actual['intent'] if int(actual['reads']) else suggested['intent'],
            hybrid_reads=hybrid_reads,entities=entities,query_text=query,disposition=disposition,
            native_disposition=native_disposition,forced_label_reads=forced,prohibited=prohibited,
            native_message=base64.b64decode(actual['message_base64']).decode()))
    positives=[d for d in details if d['expected_disposition']=='read']
    admitted=[d for d in positives if d['hybrid_reads']]
    prohibited=[d for d in details if d['prohibited']]
    counts=dict(rows=len(rows),labelled_valid_reads=len(positives),native_reads=sum(d['native_reads'] for d in details),
        simulated_hybrid_reads=sum(d['hybrid_reads'] for d in details),admitted_positive_read_calls=len(admitted),
        admitted_valid_reads=sum(d['dispatched_intent']==d['expected_intent'] and d['entities']==d['expected_entities']
            and d['query_text']==d['expected_query'] for d in admitted),
        retained_native_reads=sum(bool(d['native_reads'] and d['hybrid_reads']) for d in details),
        native_forbidden_reads=sum(d['native_reads'] for d in prohibited),
        simulated_forbidden_reads=sum(d['hybrid_reads'] for d in prohibited),
        forced_label_attempts=sum(len(d['forced_label_reads']) for d in prohibited),
        forced_label_forbidden_reads=sum(sum(d['forced_label_reads'].values()) for d in prohibited),
        native_nonread_context_calls=sum(d['native_contexts'] for d in prohibited),
        native_proposals=sum(d['native_proposals'] for d in details),native_completed=sum(d['native_completed'] for d in details),
        admitted_exact_entities=sum(d['entities']==d['expected_entities'] for d in admitted),
        admitted_exact_queries=sum(d['query_text']==d['expected_query'] for d in admitted),
        admitted_exact_intents=sum(d['dispatched_intent']==d['expected_intent'] for d in admitted),
        wrong_scope_positive_reads=sum(d['dispatched_intent']!=d['expected_intent'] or d['entities']!=d['expected_entities']
            or d['query_text']!=d['expected_query'] for d in admitted),
        positive_exact_entities=sum(bool(d['hybrid_reads']) and d['entities']==d['expected_entities'] for d in positives),
        exact_native_dispositions=sum(d['native_disposition']==d['expected_disposition'] for d in details),
        exact_simulated_dispositions=sum(d['disposition']==d['expected_disposition'] for d in details),
        requested_constraint_rows=sum(bool(d['requested_constraints']) for d in details),
        prohibited_constraint_rows_blocked=sum(bool(d['requested_constraints']) and not d['hybrid_reads'] for d in prohibited),
        verbatim_original_requests_retained=sum(d['request_text']==r['utterance'] for d,r in zip(details,rows)),
        native_follow_up_constraints_retained=sum(actual['status']!='FOLLOW_UP' or row['utterance'] in d['native_message']
            for row,d in zip(rows,details) for actual in (native[row['id']],)))
    for key in ('native_forbidden_reads','simulated_forbidden_reads','forced_label_forbidden_reads'):
        if counts[key]: raise AssertionError(f'{key}: {counts[key]}')
    if counts['retained_native_reads']!=counts['native_reads']: raise AssertionError('Existing valid reads lost')
    if counts['native_follow_up_constraints_retained']!=len(rows): raise AssertionError('Clarification lost request')
    return dict(counts=counts,semantic_intents=dict(native_raw=metrics(rows,[d['native_label'] for d in details]),
        raw_classifier=metrics(rows,raw),threshold_classifier=metrics(rows,accepted),
        rules_first_hybrid=metrics(rows,[d['hybrid_label'] for d in details])),
        calibration=dict(before=calibration_metrics(model,rows,1.0),
            after=calibration_metrics(model,rows,selected['settings']['temperature'])),details=details)


def historical_comparison(path, rows, selected_metrics):
    if path is None or not path.exists(): return dict(available=False,reason='No retained v9 numeric export; never refit v9')
    sys.path.insert(0,str(HERE.parent / 'classifier_v9'))
    from v9_model import exported_predict as legacy_predict
    payload=json.loads(path.read_text(encoding='utf-8'))
    if payload['data_sha256']!=digest(HERE.parent / 'classifier_v9/development-data.json'):
        raise ValueError('Historical export data binding changed')
    if payload['manifest_sha256']!=digest(HERE.parent / 'classifier_v9/development-manifest-v9.json'):
        raise ValueError('Historical export manifest binding changed')
    predictions,_=legacy_predict(payload,rows)
    old=metrics(rows,predictions)
    return dict(available=True,scope='Same new assistant-authored test rows; shared synthetic vocabulary, not independent',
        export_sha256=digest(path),v9_threshold_classifier=old,v11_threshold_classifier=selected_metrics,
        macro_f1_delta=selected_metrics['macro_f1']-old['macro_f1'],v9_retrained=False)


def evaluate(native_path,regression_native,output,legacy_export=None):
    output_path(output)
    manifest=check_manifest()
    data=json.loads((HERE / 'development-data.json').read_text(encoding='utf-8'))
    if data['protocol_sha256']!=digest(HERE / 'protocol.json'): raise ValueError('Authored protocol drift')
    rows=data['rows']; validate(rows)
    parts={split:[r for r in rows if r['split']==split] for split in PARTITIONS}
    native=snapshot(native_path,HERE / 'development-input.tsv',[r['id'] for r in rows])
    regression_rows=json.loads((HERE.parent / 'read_coverage_v10/regression-expectations.json').read_text(encoding='utf-8'))
    regression=snapshot(regression_native,HERE.parent / 'read_coverage_v10/regression-input.tsv',[r['id'] for r in regression_rows])
    # Neither test nor exposed rows/labels enter model, calibration or selection.
    model,selected,trials=select(parts['train'],parts['calibration'],parts['selection'])
    bindings=dict(data_sha256=digest(HERE / 'development-data.json'),manifest_sha256=digest(HERE / 'development-manifest-v11.json'),
        model_source_sha256=digest(HERE / 'v11_model.py'),protocol_sha256=digest(HERE / 'protocol.json'))
    payload=export(model,selected,bindings)
    check_reproduction(payload,output.parent / 'disabled-candidate-v11.json')
    predicted,probabilities=predict(model,rows,selected['settings'])
    restored,values=exported_predict(payload,rows)
    if predicted!=restored or not np.allclose(probabilities,values,rtol=1e-12,atol=1e-12): raise AssertionError('Numeric export parity failed')
    development={split:section(values,native,model,selected) for split,values in parts.items()}
    regressions={name:section([r for r in regression_rows if r['section']==name],regression,model,selected)
                 for name in ('development','exposed','v9','v8','v7')}
    old_holdout=[r for r in regression_rows if r['section']=='v9' and r['split']=='test']
    exposed_v9_holdout=section(old_holdout,regression,model,selected)
    for name,expected in (('exposed',23),('v9',299),('v8',18),('v7',27),('development',78)):
        if regressions[name]['counts']['admitted_valid_reads']!=expected: raise AssertionError('Retained coverage lost: '+name)
        if any(regressions[name]['counts'][key]!=expected for key in ('admitted_exact_entities','admitted_exact_queries')):
            raise AssertionError('Retained entity/query constraints changed: '+name)
    test=development['test']
    comparison=historical_comparison(legacy_export,parts['test'],test['semantic_intents']['threshold_classifier'])
    report=dict(version=VERSION,decision='NO_GO',ml_enabled=False,ml_packaged=False,adoption_eligible=False,
        scope='Assistant-authored development holdout and exposed regressions; NOT independent human evaluation',
        selected=selected,selection_trials=trials,development=development,regressions=regressions,
        exposed_v9_holdout=exposed_v9_holdout,
        historical_same_test_comparison=comparison,partition_rows={k:len(v) for k,v in parts.items()},
        partition_families=dict(Counter(data['families'].values())),
        fitting_audit={k:dict(ids=[r['id'] for r in v],families=sorted({r['family_id'] for r in v})) for k,v in parts.items()},
        original_v9_holdout_scope='The v9 test subset in exposed regressions; never independent or used for fitting',
        held_out_safe_read_gain=test['counts']['simulated_hybrid_reads']-test['counts']['native_reads'],
        python_export_parity=dict(rows=len(rows),labels_equal=True,probabilities_atol=1e-12,android_parity=False),
        manifest=manifest,native_snapshot_sha256=digest(native_path),regression_snapshot_sha256=digest(regression_native),
        runtime=dict(python=platform.python_version(),sklearn=sklearn.__version__,numpy=np.__version__,scipy=scipy.__version__),
        gates=dict(development_macro_f1_0_90=test['semantic_intents']['threshold_classifier']['macro_f1']>=.90,
            development_unknown_rejection=test['semantic_intents']['threshold_classifier']['unknown_false_accepts']==0,
            complete_read_improvement=test['counts']['simulated_hybrid_reads']>test['counts']['native_reads'],
            new_development_scope_correct=all(s['counts']['wrong_scope_positive_reads']==0 for s in development.values()),
            independent_evaluation=False,representative_calibration=False,android_model_parity_measurements=False),
        limits=['Related synthetic stems and common lexical cues limit generalization; this is not a deployment score.',
            'Same-test v9 comparison uses retained numeric weights only, if available, and never retrains v9.',
            'Fixed v10 safety grammar means this classifier adds no permitted read forms.',
            'New positive paraphrases also expose existing parser intent/query scoping mismatches; positive read-port calls are not automatically correctly scoped reads.',
            'Raw intent labels are distinct from dispositions, entity resolution, context calls and read permission.',
            'Default production may propose supported actions in broad UNKNOWN examples; forced read labels must cause zero context/read calls on prohibited rows.',
            'Native snapshots use synthetic ports. Existing physical-device read tests do not measure this offline model.',
            'Fresh human calibration/evaluation, useful incremental outcomes and Android model integration remain unmet.'])
    check_manifest()
    output.parent.mkdir(parents=True,exist_ok=True)
    (output.parent / 'disabled-candidate-v11.json').write_text(json.dumps(payload,indent=2)+'\n',encoding='utf-8')
    output.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    summary=dict(version=VERSION,decision=report['decision'],selected=selected,
        test_intents=test['semantic_intents'],test_counts=test['counts'],gates=report['gates'],
        exposed_v9_holdout_intents=exposed_v9_holdout['semantic_intents'],
        historical_same_test_comparison=comparison,
        regressions={k:v['counts'] for k,v in regressions.items()})
    (output.parent / 'summary.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(version=VERSION,selected=selected,test_macro_f1={k:v['macro_f1'] for k,v in test['semantic_intents'].items()},
        test_unknown_accepts=test['semantic_intents']['threshold_classifier']['unknown_false_accepts'],
        safe_read_gain=report['held_out_safe_read_gain'],comparison_delta=comparison.get('macro_f1_delta'),gates=report['gates']),indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native',type=Path,default=OUTPUT_ROOT / 'native-development.tsv')
    parser.add_argument('--regression-native',type=Path,default=ROOT / 'app/build/reports/phase15/v10/native-regressions.tsv')
    parser.add_argument('--output',type=Path,default=OUTPUT_ROOT / 'development-report.json')
    parser.add_argument('--legacy-export',type=Path)
    args=parser.parse_args()
    evaluate(args.native,args.regression_native,args.output,args.legacy_export)
