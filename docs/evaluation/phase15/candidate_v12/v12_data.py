"""Preserve the user's 50 requests and define development families before implementation."""
import base64
import json
import random
import sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
VERSION='phase15-candidate-v12'
sys.path.insert(0,str(HERE.parent / 'classifier_v11'))
from v11_data import canonical, digest, LABELS

USER_TEXTS='''Have I told Riya I would send her the notes?
What I said I will do with Arjun last week?
Have I already got a task to turn in the lab report?
Please remind me what I said I was going to help Kabir with his project.
Did I ever agree to call Meera post-class?
If not, create a task for me to purchase the parts.
What tasks do I have related to the antenna project?
I hope I didn't tell Nisha that I would get the presentation done today.
Can you remember what I told you in the robotics competition plan?
Ever heard of a guy called Aarav in relation to the report?
Is there anything saved in my mind for Priya?
Did I promise anyone that I'd post the source code?
Identify and locate the word in which I indicated that I need to reach Rahul.
Should I be done with the assignment by Friday?
About the prototype deadline?I said about the deadline for the prototype?
I said I didn't want to go to the meeting?
Have I ever told Sameer that I don't have to help him out?
I did say I wouldn't submit it this week didn't I?
Look for anything I saved on Neha and database project.
So, what can you recall from what I said to Vikram?
Have I created a task already to send the document to Ananya?
Make a note to email the document to Ananya tomorrow.
Do I already have some plans for tomorrow afternoon?
Which meetings are coming up next?
Have any meetings with Rohit this week?
When do I have to meet again concerning the research project?
Did I leave any notes behind for a meeting with Ishita?
After meeting Karan, what would I do?
Am I right that I am supposed to have it done by Monday?
What guarantees have I given for the website project?
Have I let anyone know I would deploy the app?
The commitment here was that I will look at the document.
Did I say that I would send the file to someone or am I mis-remembering that?
Oh! I remember I need to do this for him.
Have I already done that for her?
Go back to the task that I mentioned earlier regarding the presentation.
So what was I supposed to submit?
Have I made any notes about going there next week?
Did Aditya have a task?
Have I offered Aditya anything to do with the demo?
Locate uncompleted work on the project.
What did you get done yesterday?
Are there any tasks on Friday that I need to finish before?
Find tasks related to the report that are marked completed.
Have I planned a task to meet Sana in Chennai?
What did I promise myself when I left for Delhi?
Did I say that I will send you only 3 files, Dev?
Identify the task that I was supposed to get in touch with Rohan and let me know the due date.
Did I say I'd send the slides to Maya and did I give her a job to do?
See if I can remember anything about the workshop and if I did make a task for it.'''.splitlines()

# Provisional assistant labels, never user approval. Separate requests, as the
# user explicitly confirmed; no inferred antecedents or saved-person fixtures.
PROMISE_IDS={1,2,4,5,12,17,30,31,33,40,46,47,49}
TASK_IDS={3,7,21,35,36,39,41,43,44,45,48}
MEMORY_IDS={9,10,11,15,16,18,19,20,27,38}
CALENDAR_IDS={23,24,25,26}
READS={9:('robotics competition plan','robotics competition plan'),
       19:('Neha and database project','Neha and database project'),
       20:('Vikram','what I said to Vikram'),27:('Ishita','meeting with Ishita')}
REASONS={
    1:'promised-action content filter: send her the notes',2:'time filter: last week; ambiguous with Arjun',
    3:'task content/existence filter: turn in the lab report',4:'promised-action/project filter',
    5:'promised-action and post-class time filter',6:'conditional depends on missing context; action creation',
    7:'task topic filter: antenna project',8:'belief/negative statement and today filter',
    10:'identity/world-knowledge ambiguity; clarify saved-record scope',11:'ambiguous mind/for-person scope',
    12:'recipient anyone is unresolved; action content filter',13:'ambiguous word-location request',
    14:'advice question with Friday constraint',15:'fragmented/repeated deadline question',
    16:'negative statement without explicit recall request',17:'negated obligation is not an affirmative promise',
    18:'negative content and this-week filter; uncertain reference',21:'task content/existence filter',
    22:'note/email/scheduling action combination',28:'predictive action question after a meeting',
    29:'unresolved it and Monday constraint',30:'project filter without a unique named promise recipient',
    31:'unnamed recipient and deployment-content filter',32:'declarative commitment without a recall request',
    33:'unresolved someone and action-content constraint',34:'unresolved this and him',35:'unresolved that/her and completion query',
    36:'prior-conversation reference plus task topic filter',37:'unspecified submission obligation',
    38:'unresolved there plus next-week constraint',39:'other-person task ownership not implemented',
    40:'demo/action content filter',41:'unresolved project topic filter',42:'assistant activity history is unavailable',
    43:'Friday filter and incomplete before clause',44:'topic and COMPLETED status filters',
    45:'meeting/person/location task content filters',46:'myself unresolved as saved person; departure/location constraint',
    47:'send/only-3-files promise-content constraint',48:'task content filter and requested due-date field',
    49:'two requests: promise recall and assigned-job recall',50:'compound memory and task-existence requests',
}


def entity(text, raw, kind='MEMORY'):
    start=text.index(raw)
    return dict(type=kind,rawValue=raw,startOffset=len(text[:start].encode('utf-16-le'))//2,
        endOffset=len(text[:start+len(raw)].encode('utf-16-le'))//2)


def row(identifier,text,intent,disposition,raw='',query='',section='v12',reason=''):
    return dict(id=identifier,utterance=text,intent=intent,disposition=disposition,
        entities=[entity(text,raw,'PERSON' if intent=='WHAT_DID_I_PROMISE' else 'MEMORY')] if raw else [],
        query_text=query,filters={'requested_constraints':reason} if reason and disposition=='clarify' else {},
        input_source='TEXT',timestamp='2026-10-12T05:30:00Z',zone='Asia/Kolkata',section=section,
        provenance='user_submitted' if section=='user' else 'assistant_authored',human_approved=False,
        annotation_provenance='assistant_draft' if section=='user' else 'assistant_authored',fit_eligible=False,
        reason=reason or ('bounded saved-record recall' if disposition=='read' else 'calendar unavailable'))


def user_rows():
    rows=[]
    for number,text in enumerate(USER_TEXTS,1):
        intent=('WHAT_DID_I_PROMISE' if number in PROMISE_IDS else 'CHECK_TASKS' if number in TASK_IDS else
            'CHECK_MEMORY' if number in MEMORY_IDS else 'CHECK_CALENDAR' if number in CALENDAR_IDS else 'UNKNOWN')
        raw,query=READS.get(number,('',''))
        rows.append(row(f'user-v12-{number:02}',text,intent,'read' if number in READS else
            'unsupported' if number in CALENDAR_IDS else 'clarify',raw,query,'user',REASONS.get(number,'')))
    return rows


# These independent authoring tuples precede the new grammar. They are not
# human labels; paired prohibited variants retain the complete constraints.
FAMILIES=(
    ('notes-wrapper','Remind me of my notes about {topic}','CHECK_MEMORY','topic'),
    ('promise-wrapper','Remind me of the promises I made to {person}','WHAT_DID_I_PROMISE','person'),
    ('plan-recall','Can you remember what I told you in the {topic}','CHECK_MEMORY','topic'),
    ('coordinated-topic','Look for anything I saved on {person} and {topic}','CHECK_MEMORY','coordinated'),
    ('speech','So, what can you recall from what I said to {person}','CHECK_MEMORY','speech'),
    ('meeting-notes','Did I leave any notes behind for a meeting with {person}','CHECK_MEMORY','meeting'),
)
TOPICS=('saffron telescope','cobalt glider','marble resonator','bamboo observatory','café receiver')
PEOPLE=('Asha Bedi','Bharat Gill','Chaya Sane','Deepak Rao','Zoë O’Neil')


def development_rows():
    rows=[]
    for family,template,intent,mode in FAMILIES:
        for index,(topic,person) in enumerate(zip(TOPICS,PEOPLE)):
            text=template.format(topic=topic,person=person)
            raw=person if mode in {'person','speech','meeting'} else person+' and '+topic if mode=='coordinated' else topic
            query=person if mode=='person' else 'what I said to '+person if mode=='speech' else 'meeting with '+person if mode=='meeting' else raw
            rows.append(row(f'v12-{family}-{index}',text,intent,'read',raw,query))
            for suffix,invalid in (('date',text+' from last week'),('negation','Do not '+text),
                ('action',text+' and email the results'),('compound',text+' and show my tasks'),
                ('count',text+' only the first three'),('malformed',text+'\u200b')):
                rows.append(row(f'v12-{family}-{index}-{suffix}',invalid,intent if suffix in {'date','count'} else 'UNKNOWN',
                    'clarify',reason=invalid))
    for index,text in enumerate(('Remind me of the promises I made to him','Remind me of my notes about that thing',
        'Look for anything I saved on someone and project','So, what can you recall from what I said to her',
        'Did I leave any notes behind for a meeting with Tomorrow')):
        rows.append(row(f'v12-unresolved-{index}',text,'UNKNOWN','clarify',reason='unresolved or invalid reference'))
    return rows


def aggregate():
    rows=json.loads((HERE.parent / 'read_coverage_v10/regression-expectations.json').read_text(encoding='utf-8'))
    earlier=json.loads((HERE.parent / 'classifier_v11/development-data.json').read_text(encoding='utf-8'))['rows']
    rows += [dict(r,section='v11') for r in earlier]
    return rows+development_rows()+user_rows()


def build():
    human=dict(version=VERSION,conversation_context='separate requests, explicitly confirmed by user',
        authorship='user-submitted; not independently verified',human_approved=False,
        purpose='Exposed development regression only; assistant draft labels; excluded from fitting/calibration/selection',rows=user_rows())
    (HERE / 'user-development.json').write_text(json.dumps(human,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    rows=aggregate()
    (HERE / 'regression-expectations.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    lines=['id\ttext_base64\tsource\ttimestamp\tzone']
    expectations=['id\tsection\tintent\tdisposition\tquery_base64\tentities_base64\tnative_entities']
    encode=lambda text:base64.b64encode(text.encode()).decode()
    for r in rows:
        lines.append('\t'.join((r['id'],encode(r['utterance']),r['input_source'],r['timestamp'],r['zone'])))
        expectations.append('\t'.join((r['id'],r['section'],r['intent'],r['disposition'],encode(r.get('query_text','')),
            encode(json.dumps(r['entities'],ensure_ascii=False)),
            ';'.join(':'.join((e['type'],str(e['startOffset']),str(e['endOffset']),encode(e['rawValue']),'')) for e in r['entities']))))
    (HERE / 'regression-input.tsv').write_text('\n'.join(lines)+'\n',encoding='utf-8',newline='\n')
    (HERE / 'regression-expectations.tsv').write_text('\n'.join(expectations)+'\n',encoding='utf-8',newline='\n')
    # Instrumentation reads test assets, never the user's actual saved records.
    device_rows=user_rows()+[r for r in development_rows() if r['disposition']=='read']
    device_rows += [r for r in rows if r['section']=='v11' and r['kind']=='ordinary' and
        (r['utterance'].lower().startswith('remind me of my notes about') or
         r['utterance'].lower().startswith('please remind me of my notes about') or
         r['family_id'] in {'check_memory-21','what_did_i_promise-04'})]
    device_rows=list({r['id']:r for r in device_rows}.values())
    asset=ROOT / 'app/src/androidTest/assets/phase15-v12.tsv'
    asset.parent.mkdir(parents=True,exist_ok=True)
    device_lines=['id\ttext_base64\tintent\tdisposition\tquery_base64']
    device_lines += ['\t'.join((r['id'],encode(r['utterance']),r['intent'],r['disposition'],encode(r.get('query_text','')))) for r in device_rows]
    asset.write_text('\n'.join(device_lines)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(user=len(human['rows']),development=len(development_rows()),aggregate=len(rows))))


if __name__=='__main__': build()
