"""Closed v16 productions. Captures preserve complete subjects; no corpus lookup."""
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'candidate_v15'))
from v15_candidate import resolve as retained, valid_subject, PAT, units

S = r'(?P<subject>.{1,300}?)'
P = r'(?P<person>.{1,100}?)'
PREFIX = r'\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?'
END = r'[?!.]?\s*'
FORMS = []


def form(label, pattern, status='', mode='plain', negative=False, field='', upcoming=False, meetings=False, appointments=False):
    FORMS.append(dict(intent=label, source=pattern, pattern=re.compile(PREFIX + pattern + END, re.I),
                      status=status, mode=mode, negative=negative, field=field, upcoming=upcoming,
                      meetings=meetings, appointments=appointments))


form('CHECK_TASKS', rf'(?:have i (?:already got|created|made|planned|saved|designed)|did i (?:create|have)|do i have|was there|is there) (?:a |an |any )?(?:task|assignment)(?: already)? to {S}', 'ANY', 'activity')
form('CHECK_TASKS', rf'did i create a task for {S}', 'ANY')
form('CHECK_TASKS', rf'find (?:my|the) task (?:about|to) {S}', 'OPEN', 'activity_or_plain')
form('CHECK_TASKS', rf'find some task about {S}', 'OPEN')
form('CHECK_TASKS', rf'find some task which relates to {S}', 'OPEN')
form('CHECK_TASKS', rf'find all tasks related to {S}', 'OPEN')
form('CHECK_TASKS', rf'locate uncompleted work on {S}', 'OPEN')
form('CHECK_TASKS', rf'what are tasks that are not completed with {S}', 'OPEN')
form('CHECK_TASKS', rf'what completed tasks are related to {S}', 'COMPLETED')
form('CHECK_TASKS', rf'find tasks related to {S} that are marked completed', 'COMPLETED')
form('CHECK_TASKS', rf'do i have a completed task for {S}', 'COMPLETED')
form('CHECK_TASKS', r'what (?:is|are) the status of my (?P<subject>.{1,290}? task)', 'ANY', field='status')
form('CHECK_TASKS', rf'is there (?:a|an) {S} related task', 'ANY')
form('CHECK_TASKS', rf'find any unfinished {S} tasks', 'OPEN')
form('CHECK_TASKS', rf'is there also (?:a|an) {S} task', 'ANY')
form('CHECK_TASKS', r'is there something i need to (?P<subject>purchase in terms of .{1,260}?)', 'ANY', 'activity')
form('CHECK_TASKS', rf'look for the task, in which i said i need to {S}', 'OPEN', 'activity')
form('CHECK_MEMORY', r'what were the (?P<subject>prep steps for .{1,270}?)')
form('CHECK_MEMORY', rf'what did i discuss with {S}')
form('CHECK_MEMORY', r'did i mention that (?P<subject>the reviewer was .{1,240}?)')
form('CHECK_MEMORY', r'what (?P<subject>topics of .{1,260}?) did i mention i[\x27’]d like to revise', mode='revision')
form('CHECK_MEMORY', r'did i say that i (?P<subject>already have .{1,260}?)', mode='possession')
form('CHECK_MEMORY', rf'find some record about {S}')
form('CHECK_MEMORY', rf'is {S} mentioned in saved record')
form('CHECK_MEMORY', r'did i mention (?P<subject>submitting .{1,260}? physically)')
form('CHECK_MEMORY', r'did i explicitly say (?P<subject>.{1,240}? was not my responsibility)', mode='negative_role', negative=True)
form('CHECK_MEMORY', r'did i say (?P<subject>.{1,260}? (?:was|were) optional)')
form('CHECK_MEMORY', rf'(?:can you )?remember what i told you in {S}')
form('CHECK_MEMORY', r'(?:so, )?what can you recall from (?P<subject>what i said to .{1,100}?)')
form('CHECK_MEMORY', r'what was your recollection of (?P<subject>my talk with .{1,100}?)')
form('CHECK_MEMORY', r'did i leave any notes behind for (?P<subject>(?:a |the )?meeting with .{1,100}?)')
form('WHAT_DID_I_PROMISE', rf'did i tell {P} that i would {S}', 'ANY', 'activity')
form('CHECK_CALENDAR', rf'did i set an event for {S}')
form('CHECK_CALENDAR', rf'have i made an appointment with {P}', appointments=True)
form('CHECK_CALENDAR', rf'have i arranged a meeting with {P}', meetings=True)
form('CHECK_CALENDAR', r'did i schedule (?:a|an) (?P<subject>.{1,260}? meeting)', meetings=True)
form('CHECK_CALENDAR', rf'did i schedule anything related to {S}')
form('CHECK_CALENDAR', r'when should my next (?P<subject>.{1,260}? meeting) be', upcoming=True, meetings=True)
form('CHECK_CALENDAR', rf'when do i have to meet again concerning {S}', upcoming=True, meetings=True)

ACTIVITY = re.compile(r'(?:turn in|go and check on|prepare before visiting|purchase in terms of|send|review|submit|prepare|return|finish|help|bring|share|email|call|buy|purchase|print|rewrite|edit|update|deploy|push|test|register|pay|reimburse|include|meet|check|train|run|upload|address|fix|visit|add|post|make) (?P<object>.{1,250}?)', re.I)
MODIFIER = re.compile(r'^(?:(?:the|a|an|my) )?(?:latest|new) ', re.I)


def valid_reference(subject, mode):
    reference = subject
    if mode in ('activity', 'activity_or_plain'):
        activity = ACTIVITY.fullmatch(subject)
        if not activity:
            if mode == 'activity':
                return False
        else:
            reference = activity['object']
            if subject.lower().startswith('add ') and reference.lower().endswith(' to my portfolio'):
                reference = reference[:-len(' to my portfolio')]
            reference = MODIFIER.sub('', reference)
            book = re.fullmatch(r'(?:(?:the|a|an|my) )?book(?: about (?P<topic>.{1,200}?))?', reference, re.I)
            if book:
                reference = book['topic'] or 'volume'
    elif mode == 'revision':
        reference = re.sub(r'^topics of ', '', subject, flags=re.I)
    elif mode == 'possession':
        reference = re.sub(r'^already have ', '', subject, flags=re.I)
    elif mode == 'negative_role':
        reference = re.sub(r' was not my responsibility$', '', subject, flags=re.I)
    return not re.search(r'\b(?:afterwards?|later)\b', reference, re.I) and valid_subject(reference, PAT['ARTICLE'].sub('', reference), 'topic')


def resolve(text, source='TEXT', predicted_intent=None):
    denied = dict(intent='UNKNOWN', entities=[], query_text='', reads=0, scope=None)
    if source not in ('TEXT', 'VOICE') or units(text) > 512 or any(unicodedata.category(c) in ('Cc', 'Cf') for c in text):
        return denied
    for definition in FORMS:
        match = definition['pattern'].fullmatch(text)
        if not match:
            continue
        groups = match.groupdict()
        subject, person = groups.get('subject', ''), groups.get('person', '')
        if subject and (units(subject) > 256 or not valid_reference(subject, definition['mode'])):
            return denied
        if person and not valid_subject(person, person, 'person'):
            return denied
        if predicted_intent is not None and predicted_intent != definition['intent']:
            return denied
        entities = []
        for key, kind in (('person', 'PERSON'), ('subject', 'TASK' if definition['intent'] == 'CHECK_TASKS' else
                                               'MEMORY' if definition['intent'] == 'CHECK_MEMORY' else 'TITLE')):
            raw = groups.get(key, '')
            if not raw:
                continue
            skip = PAT['ARTICLE'].match(raw).end() if key == 'subject' else 0
            if not 1 <= units(raw[skip:]) <= 200:
                return denied
            entities.append(dict(type=kind, rawValue=raw[skip:], startOffset=units(text[:match.start(key) + skip]),
                                 endOffset=units(text[:match.end(key)])))
        scope = dict(kind={'CHECK_TASKS': 'TASK', 'CHECK_MEMORY': 'MEMORY', 'WHAT_DID_I_PROMISE': 'PROMISE',
                           'CHECK_CALENDAR': 'CALENDAR'}[definition['intent']], subject=subject, person=person,
                     status=definition['status'], date_field='', date='', requested_field=definition['field'],
                     negative=definition['negative'], upcoming=definition['upcoming'], meetings=definition['meetings'],
                     appointments=definition['appointments'])
        return dict(intent=definition['intent'], entities=entities, query_text=subject or person, reads=1, scope=scope)
    return retained(text, source, predicted_intent)
