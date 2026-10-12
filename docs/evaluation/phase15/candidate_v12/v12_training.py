"""Predeclared assistant-authored classifier families; user batch is never a fit source."""
import json
import random
import sys

from v12_data import HERE, canonical, digest, LABELS
sys.path.insert(0,str(HERE.parent / 'classifier_v11'))
from v11_data import earlier_texts

TEMPLATES={
 'CHECK_TASKS':(
  'Have I got a task about {topic}','Did I already create a task for {topic}',
  'Do I have unfinished tasks for {topic}','Find completed tasks about {topic}',
  'What tasks concern {topic}','Have I put {topic} on my task list',
  'Which tasks on {topic} are pending','Show the due date for my {topic} task',
  'Find my work items related to {topic}','Did I make a task to work on {topic}',
  'List my tasks involving {topic}','Have I already recorded a task for {topic}'),
 'CHECK_MEMORY':(
  'Can you recall what I saved on {topic}','Have I written anything down about {topic}',
  'Find anything I noted on {topic}','Do my saved notes contain {topic}',
  'Can you remember the notes I made about {topic}','Read what I told you about {topic}',
  'What did I record concerning {topic}','Locate my written details about {topic}',
  'Do I have a saved recollection of {topic}','Pull up anything I stored about {topic}',
  'Remind me what my notes say about {topic}','What is saved in my records for {topic}'),
 'WHAT_DID_I_PROMISE':(
  'Have I told {person} I would send the material','What did I say I would do for {person}',
  'Did I ever agree to help {person}','Have I offered {person} help with the design',
  'What was I going to help {person} with','Did I say I would call {person}',
  'Have I said to {person} that I will review the draft','What have I undertaken for {person}',
  'Did I commit to sending {person} the slides','Have I agreed with {person} to finish the model',
  'What did I say I was going to do for {person}','Have I promised {person} that I would post the file'),
 'CHECK_CALENDAR':(
  'Do I already have plans for this evening','Which meetings are coming up soon',
  'Have I booked a meeting with {person}','When do I meet {person} again',
  'Are there appointments concerning {topic}','What meetings do I have next week',
  'Have I scheduled time to discuss {topic}','When is my next calendar event',
  'Do I have a meeting later this afternoon','Is {topic} on my event schedule',
  'Which appointments are upcoming','When is the meeting about {topic}'),
 'UNKNOWN':(
  'Create a task to purchase {topic}','Please add a task for {topic}',
  'Make a new task about {topic}','Set up a task to finish {topic}',
  'Save a note about {topic}','Email {person} the document',
  'Promise {person} that I will send the file','Arrange a meeting with {person}',
  'Delete the task about {topic}','Cancel the meeting with {person}',
  'Should I buy {topic}','Tell {person} I will help them'),
}
SLOTS={
 'train':(('ochre pendulum','cypress tuner','quilted astrolabe'),('Alka Sethi','Bhavin Jha','Chitra Pai')),
 'calibration':(('teal chronograph','sycamore scanner','felted orrery'),('Daya Mistry','Ekta Puri','Faisal Nanda')),
 'selection':(('indigo seismograph','chestnut detector','woven sextant'),('Geeta Anand','Harish Rane','Ira Kapoor')),
 'test':(('crimson heliograph','larch encoder','knitted gnomon'),('Jatin Vora','Kavya Roy','Leena Bansal')),
}


def authored():
    generator=random.Random(1512)
    rows=[]
    for label,templates in TEMPLATES.items():
        order=list(range(12)); generator.shuffle(order)
        partitions={index:('train' if p<6 else 'calibration' if p<8 else 'selection' if p<10 else 'test')
                    for p,index in enumerate(order)}
        for index,template in enumerate(templates):
            split=partitions[index]
            topics,people=SLOTS[split]
            for variant,prefix in enumerate(('', 'Please ', 'Could you please ')):
                text=prefix+template.format(topic=topics[variant],person=people[variant])
                variants=[('ordinary',text+'?',label)]
                if label!='UNKNOWN':
                    variants += [('filter',text+' from last month?',label),
                        ('count','Only the first two results: '+text+'?',label),
                        ('negation','Do not '+text,'UNKNOWN'),
                        ('compound',text+' and send the results','UNKNOWN'),
                        ('action','Delete the records after you '+text,'UNKNOWN')]
                for kind,utterance,intent in variants:
                    rows.append(dict(id=f'v12-fit-{label.lower()}-{index}-{variant}-{kind}',
                        family_id=f'v12-{label.lower()}-{index}',split=split,utterance=utterance,intent=intent,
                        provenance='assistant_authored',human_approved=False))
    return rows


def build():
    earlier=earlier_texts()
    for directory in ('classifier_v11','candidate_v12'):
        for filename in ('development-data.json','user-development.json','regression-expectations.json'):
            path=HERE.parent / directory / filename
            if not path.exists(): continue
            data=json.loads(path.read_text(encoding='utf-8'))
            earlier.update(canonical(r['utterance']) for r in (data['rows'] if isinstance(data,dict) else data))
    rows=[]; exclusions=[]; seen=set()
    for row in authored():
        key=canonical(row['utterance'])
        if key in earlier or key in seen: exclusions.append(row['id']); continue
        seen.add(key);rows.append(row)
    data=dict(version='phase15-classifier-v12',protocol_sha256=digest(HERE / 'protocol.json'),
        provenance='Assistant-authored classifier development; no human fitting labels',exclusions=exclusions,rows=rows)
    (HERE / 'classifier-development.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(rows=len(rows),exclusions=len(exclusions),partitions={s:sum(r['split']==s for r in rows)
        for s in ('train','calibration','selection','test')})))


if __name__=='__main__': build()
