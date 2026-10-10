"""Prepare exposed annotation drafts or freeze a fresh, explicitly reviewed benchmark."""
import argparse
import csv
import io
import json
from collections import Counter
from pathlib import Path

from data import HERE, ROOT, LABELS, old, sources
from corpus import atomic_text, read_csv, sha256, normalized_text, write_native_input
from language import resolve

FIELDS=['id','utterance','draft_intent','draft_entities','draft_filters','draft_disposition','expected_response',
        'reviewed_intent','reviewed_entities','reviewed_filters','reviewed_disposition','reviewer','approved','human_authored']

def prepare(output):
    if output.exists():raise ValueError('Never overwrite a review file')
    rows=read_csv(HERE.parent/'independent_125/queries.csv')
    expectations={r['id']:r['expected_response'] for r in json.loads((HERE.parent/'independent_125/expectations.json').read_text(encoding='utf-8'))}
    stream=io.StringIO(newline='');writer=csv.DictWriter(stream,fieldnames=FIELDS,lineterminator='\n');writer.writeheader()
    for row in rows:
        parsed=resolve(row['utterance'],row['intent'])
        writer.writerow(dict(id=row['id'],utterance=row['utterance'],draft_intent=row['intent'],
            draft_entities=json.dumps(parsed['entities'],ensure_ascii=False),draft_filters=json.dumps(parsed['filters']),
            draft_disposition=parsed['disposition'],expected_response=expectations[row['id']],human_authored='yes'))
    atomic_text(output,stream.getvalue())

def freeze(review_path,output,reviewer):
    if output.exists() or output.with_suffix('.tsv').exists():raise ValueError('Benchmark already exists')
    manifest=json.loads((HERE/'manifest-v5.json').read_text())
    if manifest['sources']!=sources():raise ValueError('Candidate sources changed')
    excluded=set()
    for path in (HERE/'intent-v5.csv',HERE.parent/'independent_125/queries.csv',old.V3/'user-challenge-2026-10-10.csv',
                 old.V3/'review-package.csv',HERE.parent/'safety-v2.csv'):
        excluded.update(normalized_text(r['utterance']) for r in read_csv(path))
    incoming=read_csv(review_path);rows=[];annotations={};seen=set()
    for entry in incoming:
        text=entry['utterance'];key=normalized_text(text)
        if key in excluded or key in seen or not key:raise ValueError('Fresh prompts required; exposed/duplicate text found')
        seen.add(key)
        if entry['approved']!='yes' or entry['human_authored']!='yes' or entry['reviewer']!=reviewer or not reviewer.strip():
            raise ValueError('Explicit human review/authorship and consistent reviewer required')
        label=entry['reviewed_intent'];disposition=entry['reviewed_disposition']
        if label not in LABELS or disposition not in {'read','clarify','reject','unsupported'}:raise ValueError('Invalid reviewed label/disposition')
        entities=json.loads(entry['reviewed_entities']);qualifiers=json.loads(entry['reviewed_filters'])
        if not isinstance(entities,list) or not isinstance(qualifiers,dict):raise ValueError('Invalid entity/filter structures')
        for span in entities:
            begin,end=span['startOffset'],span['endOffset'];encoded=text.encode('utf-16-le')
            if type(begin)!=int or type(end)!=int or not 0<=begin<end<=len(encoded)//2 or span['type'] not in {'PERSON','MEMORY'}:
                raise ValueError('Invalid UTF-16 span')
            if encoded[begin*2:end*2].decode('utf-16-le')!=span['rawValue']:raise ValueError('Span does not match original text')
        if disposition=='read':
            if label not in {'CHECK_TASKS','CHECK_MEMORY','WHAT_DID_I_PROMISE'}:raise ValueError('Unsupported read intent')
            if label in {'CHECK_MEMORY','WHAT_DID_I_PROMISE'} and (len(entities)!=1 or entities[0]['type']!=('MEMORY' if label=='CHECK_MEMORY' else 'PERSON')):
                raise ValueError('Exactly one resolved entity required for read')
        row=old.make_row(text,label,f'fresh-{len(rows)}','independent','reviewed')
        row['id']=f'p15v5-fresh-{len(rows)+1:04d}';row['source']='user_reviewed';row['entities']=json.dumps(entities)
        row['expected_outcome']=disposition;rows.append(row)
        annotations[row['id']]=dict(entities=entities,filters=qualifiers,disposition=disposition)
    if any(Counter(r['intent'] for r in rows)[label]<25 for label in LABELS):raise ValueError('At least 25 fresh reviewed examples per label required')
    write_native_input(output.with_suffix('.tsv'),rows)
    atomic_text(output,json.dumps(dict(version='v5-fresh-reviewed-v1',rows=rows,annotations=annotations,
        candidate_manifest_sha256=sha256(HERE/'manifest-v5.json'),review_sha256=sha256(review_path),reviewer=reviewer,
        input_sha256=sha256(output.with_suffix('.tsv')),
        limit='Reviewer authorship/freshness attested, not externally verified; near paraphrase overlap still possible'),indent=2,ensure_ascii=False)+'\n')

if __name__=='__main__':
    parser=argparse.ArgumentParser();sub=parser.add_subparsers(dest='command',required=True)
    p=sub.add_parser('prepare-exposed');p.add_argument('--output',type=Path,default=HERE/'annotation-review-exposed.csv')
    p=sub.add_parser('freeze-fresh');p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--reviewer',required=True)
    args=parser.parse_args()
    if args.command=='prepare-exposed':prepare(args.output)
    else:freeze(args.input,args.output,args.reviewer)
