"""V8 deterministic development candidate. No ML, training or real repository access."""
import re
import sys
import unicodedata
from dataclasses import asdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / 'read_coverage_v7'))
import v7_candidate as v7

v6 = v7.v6
READ_LABELS = v7.READ_LABELS
VERSION = 'phase15-read-coverage-v8'
PREFIX = re.compile(r'^\s*(?:please )?(?:(?:can|could) you (?:please )?)?', re.I)
ARTICLE = re.compile(r'^(?:my )?(?:(?:the|a|an)(?: |$))?', re.I)
MEMORY = re.compile(
    r'(?:what do you know about|search (?:my )?memory for|'
    r'what did i (?:write(?: down)?|note|save) about|'
    r'what have i (?:written|noted|saved|recorded) about|'
    r'(?:show|find|retrieve|look up) (?:me )?(?:my )?(?:saved )?(?:notes|memories|records|details) '
    r'(?:about|on|regarding)|(?:do i have|do you have|are there) (?:any )?(?:saved )?'
    r'(?:notes|memories) (?:about|on|regarding)|remind me of) ((?:my )?.{1,200}?)[?!.]?\s*', re.I)
NEGATION = re.compile(r"\b(?:not|never|no|don['’]?t|do\s+not|cancel|stop|avoid|without)\b", re.I)


def extract(text):
    offset = PREFIX.match(text).end()
    body = text[offset:]
    match = MEMORY.fullmatch(body)
    if match:
        query = match.group(1)
        start = offset + match.start(1) + ARTICLE.match(query).end()
        end = offset + match.end(1)
        if start >= end:
            return 'UNKNOWN', [], ''
        entity = dict(type='MEMORY', rawValue=text[start:end],
                      startOffset=len(text[:start].encode('utf-16-le')) // 2,
                      endOffset=len(text[:end].encode('utf-16-le')) // 2)
        return 'CHECK_MEMORY', [entity], query
    intent, entities = v7.extract(body)
    if intent == 'CHECK_MEMORY':
        return 'UNKNOWN', [], ''
    shift = len(text[:offset].encode('utf-16-le')) // 2
    for entity in entities:
        entity['startOffset'] += shift
        entity['endOffset'] += shift
    return intent, entities, entities[0]['rawValue'] if entities else ''


def resolve(text, predicted_intent=None, source='TEXT'):
    intent, entities, query = extract(text)
    plan = v6.ReadPlan(intent, 'clarify', 'unsupported_read_form_or_constraints', text, entities, version=VERSION)
    body = text[PREFIX.match(text).end():]
    suffix = re.sub(r'^remind me of ', '', body, count=1, flags=re.I) if intent == 'CHECK_MEMORY' else body
    if source not in {'TEXT', 'VOICE'}:
        plan.disposition, plan.reason = 'reject', 'unsupported_source'
    elif len(text.encode('utf-16-le')) // 2 > 512 or any(unicodedata.category(c) in {'Cc', 'Cf'} for c in text):
        plan.reason = 'length_or_control_character'
    elif NEGATION.search(text) or v7.UNSAFE.search(suffix):
        plan.reason = 'compound_action_or_unsupported_constraint'
    elif predicted_intent is not None and intent != predicted_intent:
        plan.reason = 'label_does_not_authorize_read'
    elif intent == 'CHECK_CALENDAR':
        plan.disposition, plan.reason = 'unsupported', 'calendar_unavailable'
    elif ((intent == 'CHECK_MEMORY' and entities and
           v7.resolve('Search memory for ' + query)['reads'] and
           v7.resolve('Search memory for ' + entities[0]['rawValue'])['reads']) or
          (intent in {'CHECK_TASKS', 'WHAT_DID_I_PROMISE'} and v7.resolve(body)['reads'])):
        plan.disposition, plan.reason, plan.reads = 'read', 'bounded_read', 1
    result = asdict(plan)
    result['query_text'] = query if plan.reads else ''
    result['filters'] = {'status': 'open'} if plan.reads and intent == 'CHECK_TASKS' else {}
    return result
