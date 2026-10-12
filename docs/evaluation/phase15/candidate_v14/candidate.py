"""Python transcription of V14 closed parsing; no learned labels grant read authority."""
import sys
import unicodedata
from data import HERE
from grammar import FORMS

sys.path.insert(0, str(HERE.parent / 'candidate_v12'))
from v12_candidate import extract as old_extract, compile_java, valid_subject, units, PAT  # noqa: E402

GRAMMAR = [(form, compile_java(form['expression'])) for form in FORMS]


def extract(text):
    for form, pattern in GRAMMAR:
        match = pattern.fullmatch(text)
        if not match:
            continue
        mode = form['mode']
        query = match.group('query')
        key = 'person' if mode == 'speech' else 'query'
        article = 0 if mode in ('speech', 'person_topic') else PAT['ARTICLE'].match(match.group(key)).end()
        start, end = match.start(key) + article, match.end(key)
        raw = text[start:end]
        if not raw or units(raw) > 200 or units(query) > 256:
            continue
        if mode == 'topic':
            valid = valid_subject(query, raw, 'topic')
        else:
            person = match.group('person')
            valid = valid_subject(person, person, 'person')
            if mode != 'speech':
                topic = match.group('subject')
                valid = valid and valid_subject(topic, PAT['ARTICLE'].sub('', topic), 'topic')
        if valid:
            return 'CHECK_MEMORY', [dict(type='MEMORY', rawValue=raw, startOffset=units(text[:start]),
                                         endOffset=units(text[:end]))], query
    return old_extract(text)


def resolve(text, source='TEXT', predicted_intent=None):
    intent, entities, query = extract(text)
    valid = (source in ('TEXT', 'VOICE') and units(text) <= 512 and
             not any(unicodedata.category(c) in ('Cc', 'Cf') for c in text) and
             (predicted_intent is None or predicted_intent == intent))
    read = valid and intent in ('CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE', 'CHECK_CALENDAR')
    return dict(intent=intent, entities=entities if valid else [], query_text=query if read else '',
                reads=int(read), disposition='read' if read else 'clarify')
