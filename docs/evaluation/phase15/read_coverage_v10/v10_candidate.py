"""Deterministic Python replay, not a trained model or independent evaluation."""
import re
import unicodedata
from v10_grammar import FORMS, PATTERNS

VERSION = 'phase15-read-coverage-v10'
READ_LABELS = ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR')


def compile_java(pattern):
    return re.compile(re.sub(r'\(\?<([a-z]+)>', r'(?P<\1>', pattern), flags=re.I)


PAT = {key: compile_java(value) for key, value in PATTERNS.items()}
GRAMMAR = [(form, compile_java(form['expression'])) for form in FORMS]


def units(text):
    return len(text.encode('utf-16-le')) // 2


def plain(value):
    return (bool(value.strip()) and value == value.strip() and any(c.isalnum() for c in value)
        and not PAT['GENERIC'].fullmatch(value)
        and not any(c in '.,?!:;"“”‘`/\\|&=<>' or unicodedata.category(c) in {'Cc', 'Cf'} for c in value)
        and not any(c in "'’" and (i == 0 or i == len(value)-1 or not value[i-1].isalpha()
                    or not value[i+1].isalpha()) for i, c in enumerate(value))
        and not any(PAT[key].search(value) for key in ('UNSAFE', 'REFERENCE', 'FILTER', 'TIME')))


def name(value):
    words = value.split(' ')
    if words and all(unicodedata.category(c) in {'So', 'M', 'Mn', 'Mc', 'Me'} for c in words[-1]): words.pop()
    return (1 <= len(words) <= 3 and all(w and w[0].isupper() and all(c.isalpha() or c in "'’-" for c in w)
        and not re.search(r"['’-]{2}|['’-]$", w) for w in words))


def valid_subject(query, reference, mode):
    if units(reference) > (100 if mode == 'person' else 200) or units(query) > 256: return False
    if mode == 'person': return name(reference) and plain(reference) and not PAT['PERSON_EXCLUSIONS'].search(reference)
    if mode == 'street': return bool(PAT['STREET'].fullmatch(query))
    if mode in {'attribution', 'identity', 'hotel'}:
        match = PAT[mode.upper()].fullmatch(query)
        return bool(match) and all(plain(value) for value in match.groupdict().values() if value is not None)
    if plain(query): return True
    for key in ('CONTENT', 'WEATHER', 'HOURS', 'PASSWORD_ARCHIVE'):
        match = PAT[key].fullmatch(query)
        if match and all(plain(value) for value in match.groupdict().values()): return True
    return False


def extract(text):
    for form, pattern in GRAMMAR:
        match = pattern.fullmatch(text)
        if not match: continue
        mode = form['mode']
        if mode == 'none': return form['intent'], [], ''
        query = match.group('reference' if mode == 'person' else 'query')
        key = 'reference' if 'reference' in match.groupdict() else 'query'
        reference = match.group(key)
        literal = mode in {'person', 'street'} or (mode == 'attribution' and reference.lower().startswith('my '))
        article = 0 if literal else PAT['ARTICLE'].match(reference).end()
        start, end = match.start(key) + article, match.end(key)
        reference = text[start:end]
        if not reference or not valid_subject(query, reference, mode): continue
        entity = dict(type='PERSON' if mode == 'person' else 'MEMORY', rawValue=reference,
            startOffset=units(text[:start]), endOffset=units(text[:end]))
        return form['intent'], [entity], query
    return 'UNKNOWN', [], ''


def resolve(text, predicted_intent=None, source='TEXT'):
    intent, entities, query = extract(text)
    result = dict(version=VERSION, intent=intent, entities=entities, query_text='', request_text=text,
        disposition='clarify', reads=0, filters={}, reason='unsupported_form_or_constraints')
    if source not in {'TEXT', 'VOICE'}:
        result.update(disposition='reject', reason='unsupported_source')
    elif units(text) > 512 or any(unicodedata.category(c) in {'Cc', 'Cf'} for c in text): pass
    elif predicted_intent is not None and intent != predicted_intent: pass
    elif intent == 'CHECK_CALENDAR': result.update(disposition='unsupported', reason='calendar_unavailable')
    elif intent in READ_LABELS:
        result.update(disposition='read', reads=1, query_text=query, reason='bounded_saved_record_read',
            filters={'status': 'open'} if intent == 'CHECK_TASKS' else {})
    return result
