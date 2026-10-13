"""Python transcription of complete v15 productions; no ML or benchmark identifiers."""
import datetime
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'candidate_v14'))
from candidate import resolve as old_resolve  # noqa: E402
from v12_candidate import valid_subject, PAT, units  # noqa: E402

S = r'(?P<subject>.{1,300}?)'
P = r'(?P<person>.{1,100}?)'
D = r'(?P<date>today|tomorrow|this week|\d{4}-\d{2}-\d{2})'
PREFIX = r'\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?'
CONTENT = re.compile(r'(?P<negative>not )?(?:send|review|submit|prepare|return|finish|help|bring|share|email|call|buy|'
    r'purchase|print|rewrite|edit|update|deploy|push|test|register|pay|reimburse|include|meet|check) '
    r'(?P<object>.{1,220}?)(?: (?:today|tomorrow|this week))?', re.I)
RESERVED = re.compile('^' + PREFIX + r'(?:did i promise|did i tell|did i save a note saying|find (?:fulfilled|cancelled|open) promises)\b', re.I)


def form(intent, expression, status='', date_field='', requested_field='', content=False, negative=False):
    return dict(intent=intent, pattern=re.compile(PREFIX + expression + r'[?!.]?\s*', re.I), status=status,
                date_field=date_field, requested_field=requested_field, content=content, negative=negative)


FORMS = [
    form('CHECK_TASKS', rf'do i have (?:a |any )?task for {S}', 'ANY'),
    form('CHECK_TASKS', rf'(?:have i (?:already )?created|did i create|do i have) (?:a |any )?task to {S}', 'ANY', content=True),
    form('CHECK_TASKS', rf'what tasks do i have (?:related to|about) {S}', 'OPEN'),
    form('CHECK_TASKS', rf'(?:find|show|list) (?P<status>open|completed|cancelled) tasks (?:related to|about) {S} due {D}', date_field='DUE'),
    form('CHECK_TASKS', rf'(?:find|show|list) (?P<status>open|completed|cancelled) tasks (?:related to|about) {S}'),
    form('CHECK_TASKS', rf'(?:find|show|list) tasks (?:related to|about) {S} due {D}', 'ANY', 'DUE'),
    form('CHECK_TASKS', rf'(?:find|show|list) tasks (?:related to|about) {S} completed on {D}', 'COMPLETED', 'COMPLETED'),
    form('CHECK_TASKS', rf'what is the (?P<field>due date) of my task to {S}', 'ANY', requested_field='due date', content=True),
    form('CHECK_TASKS', rf'what is the (?P<field>completion date) of my task to {S}', 'ANY', requested_field='completion date', content=True),
    form('WHAT_DID_I_PROMISE', rf'did i (?:tell|promise) {P} i would (?P<subject>not .{{1,300}}?)', 'ANY', content=True, negative=True),
    form('WHAT_DID_I_PROMISE', rf'did i tell {P} i would {S}', 'ANY', content=True),
    form('WHAT_DID_I_PROMISE', rf'did i promise {P} to {S}', 'ANY', content=True),
    form('WHAT_DID_I_PROMISE', rf'(?:find|show) (?P<status>open|fulfilled|cancelled) promises to {P} about {S}'),
    form('WHAT_DID_I_PROMISE', rf'(?:find|show) promises to {P} about {S} due {D}', 'ANY', 'DUE'),
    form('CHECK_MEMORY', rf'what (?P<field>email address) did i save for {S}', requested_field='email address'),
    form('CHECK_MEMORY', r'did i (?:save|record|mention) anything about (?P<subject>(?P<core>.{1,220}?) from (?P<attributor>.{1,100}?))'),
    form('CHECK_MEMORY', rf'did i (?:save|record|mention) anything about {S}'),
    form('CHECK_MEMORY', rf'what (?P<field>number) did i save for {S}', requested_field='number'),
    form('CHECK_MEMORY', r'did i save a note saying i would (?P<subject>not .{1,300}?)', content=True, negative=True),
    form('CHECK_MEMORY', rf'(?:find|show) notes about {S} saved (?:on )?{D}', date_field='CREATED'),
    form('CHECK_CALENDAR', rf'do i have a meeting related to {S}'),
    form('CHECK_CALENDAR', rf'did i save the {S} date'),
    form('CHECK_CALENDAR', rf'(?:find|show) meetings with {P} about {S} on {D}', date_field='START'),
    form('CHECK_CALENDAR', rf'(?:find|show) calendar events about {S} on {D}', date_field='START'),
    form('CHECK_CALENDAR', rf'(?:find|show) meetings with {P} {D}', date_field='START'),
]


def valid_date(text):
    if text.lower() in ('today', 'tomorrow', 'this week'):
        return True
    try:
        datetime.date.fromisoformat(text)
        return True
    except ValueError:
        return False


def extract(text):
    for definition in FORMS:
        match = definition['pattern'].fullmatch(text)
        if not match:
            continue
        groups = match.groupdict()
        subject, person, day = (groups.get(key, '') or '' for key in ('subject', 'person', 'date'))
        if person and not valid_subject(person, person, 'person'):
            return None
        if day and not valid_date(day):
            return None
        if units(subject) > 256:
            return None
        if definition['content']:
            content = CONTENT.fullmatch(subject)
            if not content or bool(content['negative']) != definition['negative']:
                return None
            reference = re.sub(r'^(?:her|his|their) ', '', content['object'], flags=re.I) if person else content['object']
            if re.search(r'\b(?:afterwards?|later)\b', reference, re.I):
                return None
        elif groups.get('attributor'):
            if not valid_subject(groups['attributor'], groups['attributor'], 'person'):
                return None
            reference = groups['core']
            book = re.fullmatch(r'borrowing (?:a |the )?book(?: about (?P<topic>.{1,200}?))?', reference, re.I)
            if book:
                reference = book['topic'] or ''
        else:
            reference = subject
        if reference and not valid_subject(reference, PAT['ARTICLE'].sub('', reference), 'topic'):
            return None
        entities = []
        for key, kind in [('person', 'PERSON'), ('subject', 'TASK' if definition['intent'] == 'CHECK_TASKS'
            else 'MEMORY' if definition['intent'] == 'CHECK_MEMORY' else 'TITLE'), ('date', 'TIME'), ('field', 'TITLE')]:
            raw = groups.get(key)
            if not raw:
                continue
            skip = PAT['ARTICLE'].match(raw).end() if key == 'subject' else 0
            if units(raw[skip:]) > 200 or not raw[skip:]:
                return None
            entities.append(dict(type=kind, rawValue=raw[skip:], startOffset=units(text[:match.start(key) + skip]),
                                 endOffset=units(text[:match.end(key)])))
        kind = {'CHECK_TASKS': 'TASK', 'CHECK_MEMORY': 'MEMORY', 'WHAT_DID_I_PROMISE': 'PROMISE', 'CHECK_CALENDAR': 'CALENDAR'}[definition['intent']]
        scope = dict(kind=kind, subject=subject, person=person, status=(groups.get('status') or definition['status']).upper(),
                     date_field=definition['date_field'], date=day, requested_field=definition['requested_field'], negative=definition['negative'])
        return dict(intent=definition['intent'], entities=entities, query_text=subject or person, reads=1, scope=scope)
    if RESERVED.search(text):
        return None
    old = old_resolve(text)
    return dict(old, scope=None) if old['reads'] else None


def resolve(text, source='TEXT', predicted_intent=None):
    if source not in ('TEXT', 'VOICE') or units(text) > 512 or any(unicodedata.category(c) in ('Cc', 'Cf') for c in text):
        return dict(intent='UNKNOWN', entities=[], query_text='', reads=0, scope=None)
    parsed = extract(text)
    if parsed and (predicted_intent is None or parsed['intent'] == predicted_intent):
        return parsed
    return dict(intent='UNKNOWN', entities=[], query_text='', reads=0, scope=None)
