"""Materialize user-contributed evaluation queries; never train or tune with them."""
import csv
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
V4 = HERE.parent / 'read_queries_v4'
sys.path.insert(0, str(V4))
from v4_data import make_row, csv_text
from corpus import atomic_text, write_native_input, sha256

GROUPS = {
'CHECK_TASKS': '''What’s still unfinished?
Anything pending for me rn?
What have I not ticked off yet?
Show whatever’s left for today.
Pending ka scene kya hai?
What am I supposed to get done?
Any work left before tonight?
List my unfinished stuff.
What haven’t I done yet bro
Anything still open?
Tell me my remaining work.
What’s left from my list?
tasks pending??
What do I still need to wrap up?
Show today’s incomplete ones.
Anything overdue and not done?
Which tasks are still active?
What work did I leave halfway?
Give me the stuff I haven’t completed.
Pending assignments dikhao.
Anything from this week still left?
Need to know what’s remaining.
My undone tasks?
what all pending yaar
Before I sleep, what’s still not done?''',
'CHECK_MEMORY': '''What did I save about control systems?
Pull up my stuff on the internship interview.
I had written something about FIR filters, find it.
Show me whatever I kept on the drone project.
Any saved notes for microwave lab?
Find that thing I saved about Jaipur travel.
Do I have anything stored on Smith charts?
notes on embedded systems pls
What did I note down about the antenna viva?
Search my memory for RISC-V.
I saved some points about placement prep.
Find what I wrote on quantum computing.
Anything stored for the robotics club?
Show saved info about the hostel application.
What have I kept about my RF project?
Find notes re: convolution.
Did I save anything on Kavya’s project idea?
Show my saved points from the DSP seminar.
memory search: machine learning CAT2
I had some note about hostel fees somewhere.
Bring up whatever I saved about the hackathon.
Anything in memory about Arnav’s presentation?
Find my notes for signals quiz, the old ones.
I wrote something abt op-amp stability, check.
Search saved stuff for VLSI project report.''',
'WHAT_DID_I_PROMISE': '''Did I say I’d do anything for Ishaan?
What do I owe Naina?
Anything I told Dev I would finish?
What was I supposed to send Riya?
Did I commit to something for Manav?
What have I agreed to help Zoya with?
Any commitment pending with Aarush?
What did I tell Kiara I’d handle?
Did I promise Vihaan anything this week?
What am I meant to give Anika?
Check if I owe Samar some work.
Anything pending from my side for Diya?
What did I agree with Neil on?
I told Myra I’d do something, what was it?
Any promise to Advik still incomplete?
What was I gonna send Sanvi?
Did I say yes to anything for Kunal?
What favour did I agree to do for Tanya?
Check promises involving Reyansh.
Anything I committed to for Pihu?
What’s pending between me and Laksh?
Did I tell Aditi I’d upload something?
What did I say I’d bring for Yash?
promise check for Mehul
Anything I still owe Sneha from last week?''',
'CHECK_CALENDAR': '''Am I busy tomorrow morning?
Any gap after 4 on Friday?
What’s my schedule looking like Monday?
Free around lunchtime tomorrow?
Anything booked this evening?
Do I have class at 10 tomorrow?
Check if Saturday is open.
Any meetings before noon?
When am I free on Wednesday?
Schedule packed tomorrow or chill?
Got anything from 2 to 5?
Is Sunday mostly free?
What do I have after my 11 AM class?
Do I have time for gym tonight?
Calendar scene for tomorrow?
Any clash at 6 PM?
Is there a free one-hour slot tomorrow?
What’s booked for the afternoon?
Can I fit a study session after 7?
Busy at 3ish?
Check tomorrow between breakfast and lunch.
Any event before my lab?
How much free time do I have Thursday?
Do I have anything back-to-back tomorrow?
When’s my next open slot?''',
'UNKNOWN': '''Don’t list my pending tasks.
If someone said ‘show all my notes,’ what intent is that?
Show pending work and wipe all completed tasks.
Find my notes about that thing from class.
Tell Aarav I’m running late and show my tasks.
Book me a flight to Kochi.
Delete everything I ever saved.
What did I promise him?
Find my notes.
Do not check my calendar tomorrow.
Suppose I asked you to remove all my tasks.
Show my tasks then message Kabir the whole list.
What do I owe her from yesterday?
Find notes on chapter…
Cancel my Netflix subscription.
Show only tasks that aren’t not incomplete.
Did I promise someone something?
My calendar next…
Show my optics notes, but don’t search my saved stuff.
Call Rohan and also erase every promise involving him.
I never said ‘show my unfinished work.’
Anything pending, except don’t actually show it.
Find my note about Priyanka or Priya, whichever.
What was I supposed to do?
Send all my stored notes to Nikhil.'''
}

NEGATIVE_EXPECTATIONS = '''Do not show tasks; acknowledge the negation
Do not execute search; treat it as discussion about a command
Do not treat as simple task query; separate intents and require confirmation/unsupported handling for deletion
Ask which topic/class they mean
Do not collapse into one intent; clarify or process as multiple intents according to supported behavior
Explain that the request is unsupported
Do not execute silently; require explicit confirmation and scope
Ask who “him” refers to
Ask what topic they want to search
Do not perform calendar lookup
Do not delete anything; respond as hypothetical
Do not treat as a basic query; require separate handling/confirmation
Ask who “her” means
Ask which chapter/topic
Explain that this action is unsupported
Ask for clarification rather than guessing
Clarify whether the user wants all recent promises or a specific person
Ask what date/time period they want
Ask for clarification
Separate commands; require clarification/confirmation and do not silently delete
Do not execute task search
Clarify or acknowledge that no results will be displayed
Ask which person or show disambiguation choices
Ask what they mean unless recent context resolves it
Do not treat as a memory query; reject or require explicit scoped confirmation depending on supported capabilities'''.splitlines()

def freeze():
    if (HERE/'manifest.json').exists():
        raise RuntimeError('Already frozen; do not overwrite the evaluation set')
    rows = []
    for label, block in GROUPS.items():
        assert len(block.splitlines()) == 25
        for index, text in enumerate(block.splitlines(), 1):
            row = make_row(text, label, f'user125-{label}-{index}', 'independent', 'user_contributed')
            row.update(id=f'user125-{label}-{index:02d}', source='user_contributed',
                       expected_outcome='unsupported' if label=='CHECK_CALENDAR' else 'clarify_or_decline' if label=='UNKNOWN' else 'answer')
            rows.append(row)
    atomic_text(HERE/'queries.csv', csv_text(rows))
    write_native_input(HERE/'queries.tsv', rows)
    expectations = []
    for row in rows:
        label = row['intent']; index = int(row['id'].split('-')[-1])-1
        expectation = NEGATIVE_EXPECTATIONS[index] if label=='UNKNOWN' else (
            'Recognize calendar intent and explain calendar access is unavailable' if label=='CHECK_CALENDAR' else
            'Show matching saved information, respecting the requested topic/qualifiers' if label=='CHECK_MEMORY' else
            'Show matching commitments, respecting person/status/time qualifiers' if label=='WHAT_DID_I_PROMISE' else
            'Show matching pending tasks, respecting requested date/status/category qualifiers')
        expectations.append(dict(id=row['id'], expected_response=expectation))
    atomic_text(HERE/'expectations.json', json.dumps(expectations,indent=2,ensure_ascii=False)+'\n')
    atomic_text(HERE/'manifest.json', json.dumps(dict(
        version='metis-user125-2026-10-10', rows=125, labels_per_category=25,
        candidate_commit='2e24bda', candidate_manifest_sha256=sha256(V4/'manifest-v4.json'),
        hashes={name:sha256(HERE/name) for name in ('queries.csv','queries.tsv','expectations.json')},
        provenance='User supplied standalone frozen queries, intended meanings and response expectations in chat. Human authorship/independence not externally verified.',
        annotation_scope='Category labels and negative expectations supplied by user; positive response summaries and UNKNOWN read-router projection transcribed by assistant. No approved exact entity spans.',
        limits='Single contributor, small benchmark. No model tuning on this set. Reject/clarify category is routing abstention, not necessarily semantic UNKNOWN. Earlier review package remains separate.'
    ),indent=2)+'\n')

if __name__=='__main__':
    freeze()
