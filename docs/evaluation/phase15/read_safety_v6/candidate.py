"""V6 deterministic read admission; no fitting, model import, or repository access.

This intentionally bounds coverage to the native English read forms. Classifier
labels are untrusted suggestions. Unsupported constraints remain verbatim in the
plan and must be clarified, never silently removed to make a request admissible.
"""
import re
import unicodedata
from dataclasses import asdict, dataclass, field

VERSION = 'phase15-read-safety-v6'
READ_LABELS = ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR')


def words(value):
    return re.compile(r'\b(?:' + value + r')\b', re.I)


UNSAFE = words('and|or|then|also|but|except|only|if|unless|remind|create|add|delete|erase|'
               'wipe|remove|rename|postpone|send|message|call|buy|order|book|cancel|notify|alert|execute|'
               'ignore|override|system|developer|suppose|imagine|pretend|not|never|without|stop|avoid')
FILTER = words('today|tonight|tomorrow|yesterday|week|weeks|month|months|year|years|monday|tuesday|'
               'wednesday|thursday|friday|saturday|sunday|morning|evening|recently|before|after|since|'
               'until|between|older|oldest|newest|latest|last|next|urgent|priority|overdue|pending|'
               'incomplete|completed|done|status|due|deadline|ago|kal|parso|old|new|recent|earlier|'
               'finished|resolved|with|where|whose|which|tagged|tags?|containing|contains|excluding|'
               'exclude|limit|first|top|most|least|sort|sorted|by|from|during|at|on|in')
NUMERIC_TIME = re.compile(r'\b(?:\d{4}|\d{1,2}:\d{2}|\d{1,2} ?(?:am|pm))\b', re.I)
TOPIC_EXCLUSIONS = words('it|this|that|these|those|thing|something|anything|whatever|everything|'
                         'whichever|someone|him|her|them')
PERSON_EXCLUSIONS = words('it|this|that|him|her|them|someone|somebody|anyone|who|me|you|my|your|our|'
                          'their|myself|person|people|promise|promises|task|tasks|memory|notes|work|'
                          'trip|station|insurance|renewal|library|card|book|sister|brother|mother|father|'
                          'mom|dad|to|for|with|from|about|on|at|by|in|would|will|need|help|cover|review|'
                          'pay|back|fly|mars')
GENERIC = re.compile(r'(?:the )?(?:chapter|topic|class|notes?|memory|memories)', re.I)
RULES = (
    ('CHECK_TASKS', r'(?:what tasks do i have|show (?:my )?tasks|list (?:my )?tasks)'),
    ('CHECK_MEMORY', r'(?:what do you know about|search (?:my )?memory for|'
                     r'what did i (?:write(?: down)?|note|save) about) (?:my )?(.{1,200}?)'),
    ('WHAT_DID_I_PROMISE', r'what did i promise (.{1,200}?)'),
    ('CHECK_CALENDAR', r'(?:when am i free (?:today|tomorrow)|show (?:my )?calendar)'),
)


@dataclass
class ReadPlan:
    intent: str
    disposition: str
    reason: str
    request_text: str
    entities: list = field(default_factory=list)
    reads: int = 0
    proposals: int = 0
    completed: int = 0
    version: str = VERSION


def extract(text):
    """Match the full grammar and retain original UTF-16 spans, without normalization."""
    for intent, expression in RULES:
        match = re.fullmatch(r'\s*(?:please )?' + expression + r'[?!.]?\s*', text, re.I)
        if not match:
            continue
        entities = []
        if match.lastindex:
            start, end = match.span(1)
            entities = [dict(type='MEMORY' if intent == 'CHECK_MEMORY' else 'PERSON',
                             rawValue=text[start:end], startOffset=len(text[:start].encode('utf-16-le')) // 2,
                             endOffset=len(text[:end].encode('utf-16-le')) // 2)]
        return intent, entities
    return 'UNKNOWN', []


def valid_reference(value, person=False):
    exclusions = PERSON_EXCLUSIONS if person else TOPIC_EXCLUSIONS
    if (not value or value != value.strip() or exclusions.search(value) or FILTER.search(value) or NUMERIC_TIME.search(value)
            or any(c in '.,?!:;"“”‘`/\\|&=<>' for c in value)):
        return False
    if not person:
        return any(c.isalnum() for c in value) and not GENERIC.fullmatch(value)
    # Explicit name spelling, not pronouns, relationship references or arbitrary noun phrases.
    # The Android adapter still requires exactly one matching saved person before promise access.
    parts = value.split(' ')
    if parts[-1] and all(unicodedata.category(c) == 'So' or unicodedata.category(c).startswith('M')
                         for c in parts[-1]):
        parts = parts[:-1]
    return (len(value) <= 100 and bool(parts) and len(parts) <= 3 and all(
        part and part[0].isupper() and all(segment.isalpha() for segment in re.split("['’-]", part))
        for part in parts))


def resolve(text, predicted_intent=None, source='TEXT'):
    intent, entities = extract(text)
    plan = ReadPlan(intent, 'clarify', 'unsupported_read_form_or_constraints', text, entities)
    if source not in {'TEXT', 'VOICE'}:
        plan.disposition, plan.reason = 'reject', 'unsupported_source'
    elif len(text.encode('utf-16-le')) // 2 > 512 or any(unicodedata.category(c) in {'Cc', 'Cf'} for c in text):
        plan.disposition, plan.reason = 'reject', 'length_or_control_character'
    elif UNSAFE.search(text):
        plan.reason = 'compound_action_or_unsupported_constraint'
    elif predicted_intent is not None and predicted_intent != intent:
        plan.reason = 'label_does_not_authorize_read'
    elif intent == 'CHECK_CALENDAR':
        plan.disposition, plan.reason = 'unsupported', 'calendar_unavailable'
    elif intent == 'CHECK_TASKS' or (entities and valid_reference(
            entities[0]['rawValue'], person=intent == 'WHAT_DID_I_PROMISE')):
        plan.disposition, plan.reason, plan.reads = 'read', 'bounded_read', 1
    return asdict(plan)
