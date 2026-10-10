"""One recorded evaluation of frozen v4; does not fit on contributed queries."""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
V4 = HERE.parent/'read_queries_v4'
sys.path.insert(0,str(V4))
from v4_data import sources, PARTITIONS, ROOT
from corpus import read_csv, sha256, normalized_text, baseline_source_hash, atomic_text
from v4_evaluate import select, score, v2

def evaluate():
    output=HERE/'results.json'
    if output.exists():
        raise RuntimeError('Already scored; preserve the first result and obtain fresh data after tuning')
    frozen=json.loads((HERE/'manifest.json').read_text())
    manifest=json.loads((V4/'manifest-v4.json').read_text())
    if sha256(V4/'manifest-v4.json') != frozen['candidate_manifest_sha256'] or sources()!=manifest['sources']:
        raise ValueError('Frozen candidate changed')
    for folder, hashes in ((HERE,frozen['hashes']),(V4,manifest['hashes'])):
        for name,digest in hashes.items():
            if sha256(folder/name)!=digest:
                raise ValueError('Frozen data changed')
    development=read_csv(V4/'intent-v4.csv')
    # Selection uses existing development partitions only, before loading this benchmark.
    candidate,_=select({part:[r for r in development if r['split']==part] for part in PARTITIONS})
    rows=read_csv(HERE/'queries.csv')
    prior=read_csv(HERE.parent/'read_queries_v3/user-challenge-2026-10-10.csv')
    texts={normalized_text(r['utterance']) for r in rows}
    if len(rows)!=125 or len(texts)!=125 or texts & {normalized_text(r['utterance']) for r in development+prior}:
        raise ValueError('Duplicate or previously used exact prompt')
    baseline_path=ROOT/'app/build/reports/phase15/rules-independent125.tsv'
    baseline=v2.load_baseline(baseline_path,rows,sha256(HERE/'queries.tsv'),baseline_source_hash(ROOT))
    scored=score(rows,candidate,baseline)
    categories={label:score([r for r in rows if r['intent']==label],candidate,baseline)
                for label in ('CHECK_TASKS','CHECK_MEMORY','WHAT_DID_I_PROMISE','CHECK_CALENDAR','UNKNOWN')}
    expectations={r['id']:r['expected_response'] for r in json.loads((HERE/'expectations.json').read_text(encoding='utf-8'))}
    for detail in scored['details']:
        detail['user_expected_response']=expectations[detail['id']]
    report=dict(candidate_commit='2e24bda', candidate_manifest_sha256=frozen['candidate_manifest_sha256'],
                benchmark_manifest_sha256=sha256(HERE/'manifest.json'), evaluator_sha256=sha256(Path(__file__)),
                native_baseline_sha256=sha256(baseline_path), native_source_sha256=baseline_source_hash(ROOT),
                candidate={k:v for k,v in candidate.items() if k not in {'model','validation'}},
                evaluation=scored, category_counts={label:dict(correct=sum(d['correct_intent'] for d in s['details']),total=len(s['details']),
                    simulated_outcomes=s['simulated_outcomes'],negative_simulated_reads=s['negative_simulated_reads']) for label,s in categories.items()},
                decision='NO_GO',adoption_eligible=False,rules_retained=True,
                limitations='First scored user-contributed standalone benchmark against unchanged v4; now exposed. UNKNOWN is a reject/clarify routing projection. Exact entity annotations absent. Simulated statuses cannot prove full user response correctness or real policy/executor behavior. Positive filters and ambiguous topics may be mishandled. Hinglish outside current intended English coverage. No human authorship audit or representative-device evaluation. Prior review package not approved.')
    atomic_text(output,json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(dict(metrics=scored['hybrid'],categories=report['category_counts'],negative_simulated_reads=scored['negative_simulated_reads']),indent=2))

if __name__=='__main__':
    evaluate()
