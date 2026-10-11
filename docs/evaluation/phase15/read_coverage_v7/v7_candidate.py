"""V7 bounded coverage candidate. No model, training, external data or repository access."""
import re
import sys
import unicodedata
from dataclasses import asdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'read_safety_v6'))
import candidate as v6

VERSION = 'phase15-read-coverage-v7'
READ_LABELS = v6.READ_LABELS
UNSAFE = v6.words('and|or|then|also|but|except|only|if|unless|remind|create|add|delete|erase|'
                  'wipe|remove|rename|postpone|send|message|call|buy|order|book|cancel|notify|alert|execute|'
                  'ignore|override|system|developer|suppose|imagine|pretend|not|never|without|stop|avoid|'
                  "publish|archive|email|text|share|export|mark|don['’]?t")
REFERENCE = v6.words('it|this|that|these|those|thing|something|anything|whatever|everything|whichever|'
                     'someone|him|her|them|somewhere|stuff')
FILTER = v6.words('today|tonight|tomorrow|yesterday|week|weeks|month|months|year|years|day|days|hours?|'
                  'minutes?|seconds?|monday|tuesday|wednesday|thursday|friday|saturday|sunday|january|'
                  'february|march|april|may|june|july|august|september|october|november|december|noon|'
                  'midnight|morning|evening|recently|before|after|since|until|between|older|oldest|newest|'
                  'latest|last|next|urgent|priority|overdue|pending|incomplete|completed|done|status|due|'
                  'deadline|ago|kal|parso|old|new|recent|earlier|finished|resolved|where|whose|which|'
                  'tagged|tags?|containing|contains|excluding|exclude|limit|first|top|most|least|sort|'
                  'sorted|by|from|during')
TIME = re.compile(r'\b(?:\d{4}|\d{1,2}:\d{2}|\d{1,2} ?(?:am|pm)|at \d+)\b', re.I)
FORMS = (
    ('CHECK_TASKS', r'(?:what tasks do i have|(?:show|list) (?:my )?(?:(?:open|pending|unfinished|incomplete) )?tasks|'
                    r'what tasks are (?:open|pending|unfinished|incomplete)|'
                    r'do i have (?:any )?(?:open|pending|unfinished|incomplete) tasks|'
                    r'what is (?:still )?pending on my task list)'),
    ('CHECK_MEMORY', r'(?:what do you know about|search (?:my )?memory for|'
                     r'what did i (?:write(?: down)?|note|save) about|'
                     r'what have i (?:written|noted|saved|recorded) about|'
                     r'(?:show|find|retrieve|look up) (?:my )?(?:saved )?(?:notes|memories|records|details) '
                     r'(?:about|on|regarding)|(?:do i have|are there) (?:any )?(?:saved )?(?:notes|memories) '
                     r'(?:about|on|regarding)) (?:my )?(.{1,200}?)'),
    ('WHAT_DID_I_PROMISE', r'(?:what did i promise (?:to )?|what have i promised (?:to )?|what do i owe |'
                          r'(?:show|list) (?:my )?promises (?:to|for) )(.{1,200}?)'),
)


def extract(text):
    for intent, expression in FORMS:
        match = re.fullmatch(r'\s*(?:please )?' + expression + r'[?!.]?\s*', text, re.I)
        if not match:
            continue
        entities = []
        if match.lastindex:
            start, end = match.span(1)
            entities = [dict(type='MEMORY' if intent == 'CHECK_MEMORY' else 'PERSON', rawValue=text[start:end],
                             startOffset=len(text[:start].encode('utf-16-le')) // 2,
                             endOffset=len(text[:end].encode('utf-16-le')) // 2)]
        return intent, entities
    intent, entities = v6.extract(text)
    return (intent, entities) if intent == 'CHECK_CALENDAR' else ('UNKNOWN', [])


def valid_topic(topic):
    quoted = any(c in "'’" and (i == 0 or i == len(topic) - 1 or
                               not topic[i - 1].isalpha() or not topic[i + 1].isalpha())
                 for i, c in enumerate(topic))
    return (topic and topic == topic.strip() and any(c.isalnum() for c in topic) and
            not v6.GENERIC.fullmatch(topic) and not quoted and
            not any(c in '.,?!:;"“”‘`/\\|&=<>' for c in topic) and
            not REFERENCE.search(topic) and not FILTER.search(topic) and not TIME.search(topic))


def resolve(text, predicted_intent=None, source='TEXT'):
    intent, entities = extract(text)
    plan = v6.ReadPlan(intent, 'clarify', 'unsupported_read_form_or_constraints', text, entities, version=VERSION)
    if source not in {'TEXT', 'VOICE'}:
        plan.disposition, plan.reason = 'reject', 'unsupported_source'
    elif len(text.encode('utf-16-le')) // 2 > 512 or any(unicodedata.category(c) in {'Cc', 'Cf'} for c in text):
        plan.disposition, plan.reason = 'reject', 'length_or_control_character'
    elif UNSAFE.search(text):
        plan.reason = 'compound_action_or_unsupported_constraint'
    elif predicted_intent is not None and intent != predicted_intent:
        plan.reason = 'label_does_not_authorize_read'
    elif intent == 'CHECK_CALENDAR':
        plan.disposition, plan.reason = 'unsupported', 'calendar_unavailable'
    elif (intent == 'CHECK_TASKS' or (intent == 'CHECK_MEMORY' and entities and valid_topic(entities[0]['rawValue']))
          or (intent == 'WHAT_DID_I_PROMISE' and entities and v6.valid_reference(entities[0]['rawValue'], person=True))):
        plan.disposition, plan.reason, plan.reads = 'read', 'bounded_read', 1
    result = asdict(plan)
    result['filters'] = {'status': 'open'} if plan.reads and intent == 'CHECK_TASKS' else {}
    return result
