"""V5 synthetic development data with family and fictional-slot separation."""
import hashlib
import json
import sys
from pathlib import Path
from collections import Counter

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
V4=HERE.parent/'read_queries_v4'
sys.path.insert(0,str(V4))
import v4_data as old
from corpus import atomic_text, read_csv, sha256, normalized_text, write_native_input
from build_dataset import FAMILIES, grouped_templates, row_text

LABELS,READ_LABELS,PARTITIONS=old.LABELS,old.READ_LABELS,old.PARTITIONS
SEED=1505
ADDITIONS={
'CHECK_TASKS':[
 'unfinished tasks please','pending work right now','my incomplete task items',
 'which things are yet to be ticked off','what have i left undone on my list',
 'work left to wrap up please','any outstanding items for me','tell me about my open work items',
 'need a list of remaining jobs','which tasks did i leave partly finished',
 'pending work ka status batao','mere pending tasks dikhao','bache hue tasks batao',
 'mera pending ka scene batao','abhi kya pending tasks hain','adhure assignments dikhao',
 'show pending work for today','incomplete tasks before tonight please','show overdue work items',
 'pending assignments please','show incomplete tasks this week','what tasks have i not ticked off',
 'any work i have not done yet','show work that has not been completed',
 'what remains unfinished from my tasks','task list status please','remaining work yaar batao',
 'are there tasks still waiting for me','what jobs are active on my list','which chores still need doing',
],
'CHECK_MEMORY':[
 'saved notes for {topic} please','my notes on {topic} please',
 'i wrote a few things about {topic}, find it','i kept some information on {topic}',
 'any saved details for {topic}','notes re: {topic}','memory search: {topic}',
 'bring back my points from {topic}','check what i noted down on {topic}',
 'i had written about {topic}, check','i stored details abt {topic}',
 'do my memories contain notes on {topic}','mera notes on {topic} dikhao',
 '{topic} ke saved notes dikhao','saved memory about {topic} batao',
 'search saved stuff for {topic}','anything in memory regarding {topic}',
 'show my saved points from {topic}','look for my notes on {topic}',
 'find my earlier records about {topic}','show my notes about {topic}, the old ones',
],
'WHAT_DID_I_PROMISE':[
 'what do i still owe {person}','any commitment pending involving {person}',
 'what have i agreed to help {person} with','anything i told {person} i would finish',
 'what did i tell {person} i’d handle','i told {person} i’d do something, what was it',
 'what was i going to send {person}','what am i meant to give {person}',
 'what was i supposed to send {person}','check if i owe {person} some work',
 'what did i agree with {person} on','any promise to {person} still incomplete',
 'what did i say i’d bring for {person}','any favour i agreed to do for {person}',
 'did i say yes to anything for {person}','promise status for {person}',
 'anything pending from my side for {person}','what is pending between me and {person}',
 'did i tell {person} i’d upload something','any promise i made to {person} this week',
 'anything i owe {person} from last week','{person} ko kya promise kiya tha',
 'mera promise for {person} dikhao','commitment involving {person} batao',
],
'CHECK_CALENDAR':[
 'busy tomorrow morning or free','any free gap after four on Friday',
 'schedule for Monday please','free around lunch tomorrow',
 'anything booked this afternoon','do i have a class at ten tomorrow',
 'check if Saturday has free time','any meetings scheduled before noon',
 'schedule looking busy tomorrow','got anything from two to five',
 'do i have time for a workout tonight','calendar ka scene tomorrow batao',
 'kal calendar mein kya hai','mera schedule tomorrow dikhao',
 'calendar status for tomorrow','any appointment clash at six',
 'find an empty one hour slot tomorrow','any free time after seven',
 'busy at around three','check tomorrow between breakfast and lunch',
 'any events before my class','how much free time is there on Thursday',
 'any adjacent meetings tomorrow','next free slot please',
 'free one-hour block {date}','my appointments {date} please',
],
'UNKNOWN':[
 'what is memory capacity in computers','what is a calendar used for',
 'explain how tasks work in an operating system','what does making a promise mean',
 'tell me the weather this evening','explain the theory of {topic}',
 'show public notes on {topic}','what do people owe their government',
 'find internet information about {topic}','how can i improve my interview preparation',
 'what are incomplete assignments in mathematics','what is a pending bank payment',
 'what does a commitment mean in a database','how do calendars measure years',
 'write me a story about {topic}','give me shopping suggestions for {topic}',
 'search the web for {topic}','suggest a book about {topic}',
 'show a tutorial on {topic}','explain the word pending',
 'anything pending in my bank account','show pending software updates',
 'find the unfinished movie sequels','is there free storage on my device',
 'show public calendar information','what tasks run in my operating system',
 'show pending parcel deliveries','any unfinished download on my computer',
]}

TOPICS={
'train':['acoustic resonance','bridge inspection','ceramic design','forest survey','battery recycling','wind experiments'],
'calibration':['marine sensors','urban drainage','laser alignment','fluid control','crop planning','transistor bias'],
'threshold':['satellite tracking','museum visit','thermal insulation','rail safety','solar tracking','enzyme activity'],
'test':['geological mapping','harbour navigation','textile research','turbine maintenance','soil sampling','spectral analysis']}
PEOPLE={
'train':['Alina','Bharat','Cyrus','Dalia','Elvin','Farah'],
'calibration':['Girish','Hana','Imran','Jaya','Kiran','Leela'],
'threshold':['Milan','Noor','Ojas','Pavel','Qasim','Reva'],
'test':['Sahil','Trisha','Uma','Veda','Waseem','Xenia']}
WRAPPERS={k:v for k,v in old.WRAPPERS.items() if k!='unsupported_language'}
WRAPPERS.update(contradictory='{text}, but do not actually show anything', destructive='{text} and erase every saved record')

def sources():
    files=list(HERE.glob('*.py'))+[HERE/'requirements.txt',V4/'v4_data.py',V4/'v4_language.py',V4/'v4_evaluate.py',
        old.V3/'dataset.py',old.V3/'evaluate.py',HERE.parent/'build_dataset.py',HERE.parent/'corpus.py']
    return {p.relative_to(ROOT).as_posix():sha256(p) for p in sorted(files)}

def partitions(groups):
    ordered=sorted(set(groups.values()),key=lambda x:hashlib.sha256(f'{SEED}:{x}'.encode()).hexdigest())
    count=len(ordered);a=round(count*.7);b=a+max(1,round(count*.05));c=round(count*.85)
    return {family:'train' if i<a else 'calibration' if i<b else 'threshold' if i<c else 'test' for i,family in enumerate(ordered)}

def annotations(text,label,slot=None):
    result={'disposition':'reject' if label=='UNKNOWN' else 'unsupported' if label=='CHECK_CALENDAR' else 'read',
            'entities':[], 'filters':{'status':'incomplete'} if label=='CHECK_TASKS' else {}}
    if slot and label in {'CHECK_MEMORY','WHAT_DID_I_PROMISE'}:
        start=text.index(slot)
        result['entities']=[dict(type='MEMORY' if label=='CHECK_MEMORY' else 'PERSON',rawValue=slot,
            startOffset=len(text[:start].encode('utf-16-le'))//2,endOffset=len(text[:start+len(slot)].encode('utf-16-le'))//2)]
    # Independently authored fixture annotations, not copied from parser output.
    if label=='CHECK_TASKS':
        for marker in ('today','tonight','tomorrow','yesterday','this week','last week','recently'):
            if marker in text.lower():
                result['filters']['time_expression']=marker
                days={'today':('2026-10-09','2026-10-10'),'tonight':('2026-10-09','2026-10-10'),
                      'tomorrow':('2026-10-10','2026-10-11'),'yesterday':('2026-10-08','2026-10-09')}
                if marker in days:result['filters']['date_range']={'start':days[marker][0],'end_exclusive':days[marker][1]}
        if 'overdue' in text.lower():result['filters']['overdue']=True
        if 'assignment' in text.lower():result['filters']['category']='assignment'
    if label=='CHECK_MEMORY' and 'old ones' in text.lower():result['filters']['age']='older_unspecified'
    if label=='WHAT_DID_I_PROMISE':
        for marker in ('this week','last week','recently'):
            if marker in text.lower():result['filters']['time_expression']=marker
        if any(s in text.lower() for s in ('pending','incomplete','still owe')):result['filters']['status']='unresolved'
        if 'supposed' in text.lower():result['disposition']='clarify'
    if label!='CHECK_CALENDAR' and set(result['filters'])-({'status'} if label=='CHECK_TASKS' else set()):result['disposition']='clarify'
    return result

def build():
    from language import guard
    from build_dataset import TOPICS as OLD_TOPICS, PEOPLE as OLD_PEOPLE
    exposed=read_csv(HERE.parent/'independent_125/queries.csv')+read_csv(old.V3/'user-challenge-2026-10-10.csv')
    excluded={normalized_text(r['utterance']) for r in exposed}
    unique={};expected={};dropped=0
    for label in LABELS:
        templates=(FAMILIES[label]+old.EXTRA.get(label,[])+ADDITIONS[label])
        groups=grouped_templates(label,templates);mapping=partitions(groups)
        if label=='UNKNOWN':
            # Stratify families by classifier eligibility so calibration cannot
            # consist entirely of upstream-rejected negatives. Never split a family.
            eligible={groups[i] for i,t in enumerate(templates) if guard(row_text(label,t,0)) is None}
            mapping={**partitions({i:f for i,f in groups.items() if f in eligible}),
                     **partitions({i:f for i,f in groups.items() if f not in eligible})}
        for i,template in enumerate(templates):
            family=groups[i];part=mapping[family]
            for j in range(6):
                topic,person=TOPICS[part][j],PEOPLE[part][j]
                if label=='UNKNOWN':
                    text=row_text(label,template,j).replace(OLD_TOPICS[j],topic).replace(OLD_PEOPLE[j],person)
                else:
                    text=template.format(topic=topic,person=person,date=old.DATES[j],range=old.RANGES[j],
                        task_items=['my tasks','my pending tasks','my unfinished tasks','my open work','my to-dos','my outstanding tasks'][j],
                        task_list=['my task list','my work queue','my to-do list','my pending task list','my list of work','my current task list'][j])
                text+=old.TAILS[j]
                slot=topic if label=='CHECK_MEMORY' else person if label=='WHAT_DID_I_PROMISE' else None
                variants=[(text,label,'base',slot)]+([(v.format(text=text),'UNKNOWN',k,None) for k,v in WRAPPERS.items()] if label!='UNKNOWN' else [])
                for utterance,intent,kind,entity_slot in variants:
                    key=normalized_text(utterance)
                    if key in excluded:dropped+=1;continue
                    row=old.make_row(utterance,intent,family,part,kind,entity_slot)
                    row['id']='p15v5-'+hashlib.sha256(utterance.encode()).hexdigest()[:16]
                    row['language']='en-IN+roman-hinglish'
                    annotation=annotations(utterance,intent,entity_slot)
                    if kind in {'compound_read','contradictory'}:annotation['disposition']='clarify'
                    row['expected_outcome']=annotation['disposition']
                    if key in unique:
                        if unique[key]['family_id']!=family:raise ValueError('Duplicate across families')
                        continue
                    unique[key]=row;expected[row['id']]=annotation
    rows=list(unique.values());stats=old.validate(rows)
    # Slot pools must be disjoint even when templates share vocabulary.
    for slots in (TOPICS,PEOPLE):
        assert len(set(sum(slots.values(),[])))==sum(map(len,slots.values()))
    atomic_text(HERE/'intent-v5.csv',old.csv_text(rows))
    atomic_text(HERE/'annotations-v5.json',json.dumps(expected,indent=2,ensure_ascii=False)+'\n')
    historical=read_csv(HERE.parent/'safety-v2.csv')
    write_native_input(HERE/'rules-input-v5.tsv',rows+exposed+historical)
    manifest=dict(version='metis-p15-read-queries-v5',seed=SEED,data=stats,sources=sources(),
        hashes={name:sha256(HERE/name) for name in ('intent-v5.csv','annotations-v5.json','rules-input-v5.tsv')},
        regressions={str(p.relative_to(ROOT).as_posix()):sha256(p) for p in [HERE.parent/'independent_125/queries.csv',old.V3/'user-challenge-2026-10-10.csv',HERE.parent/'safety-v2.csv']},
        excluded_exact_exposed_rows=dropped,slots={'topics':TOPICS,'people':PEOPLE},
        limit='Synthetic development; templates informed by exposed failures. Disjoint fictional slots do not establish real-world generalization. All 150 user prompts are exposed regressions, never independent v5 evidence.')
    atomic_text(HERE/'manifest-v5.json',json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(stats,indent=2))

if __name__=='__main__':build()
