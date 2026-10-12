"""Deterministic v12 replay. Classifier outputs never grant read authority."""
import sys
import unicodedata
from v12_data import HERE
from v12_grammar import FORMS, RESERVED
sys.path.insert(0,str(HERE.parent / 'read_coverage_v10'))
from v10_candidate import compile_java, extract as old_extract, valid_subject, units, PAT, READ_LABELS

GRAMMAR=[(f,compile_java(f['expression'])) for f in FORMS]
SHADOW=compile_java(RESERVED)


def extract(text):
    for form,pattern in GRAMMAR:
        match=pattern.fullmatch(text)
        if not match: continue
        mode=form['mode']
        if mode=='none': return form['intent'],[],''
        key='reference' if 'reference' in match.groupdict() else 'query'
        reference=match.group(key)
        query=reference if mode in {'person','calendar_person'} else match.group('query')
        if mode=='topic': reference=PAT['ARTICLE'].sub('',reference)
        valid=(valid_subject(reference,reference,'person') and valid_subject(match.group('subject'),match.group('subject'),'topic')
            if mode=='coordinated' else valid_subject(reference,reference,'person') if mode in {'speech','meeting','calendar_person'}
            else valid_subject(query,reference,'person' if mode=='person' else 'topic'))
        if not valid or units(query)>256: continue
        if form['intent']=='CHECK_CALENDAR': return form['intent'],[],''
        key='query' if mode=='coordinated' else key
        article=0 if mode in {'person','coordinated','speech','meeting'} else PAT['ARTICLE'].match(match.group(key)).end()
        start,end=match.start(key)+article,match.end(key)
        raw=text[start:end]
        if not raw or units(raw)>(100 if mode=='person' else 200): continue
        entity=dict(type='PERSON' if mode=='person' else 'MEMORY',rawValue=raw,
            startOffset=units(text[:start]),endOffset=units(text[:end]))
        return form['intent'],[entity],query
    return ('UNKNOWN',[],'') if SHADOW.search(text) else old_extract(text)


def resolve(text,predicted_intent=None,source='TEXT'):
    intent,entities,query=extract(text)
    result=dict(intent=intent,entities=entities,query_text='',reads=0,request_text=text,disposition='clarify')
    if source not in {'TEXT','VOICE'} or units(text)>512 or any(unicodedata.category(c) in {'Cc','Cf'} for c in text): return result
    if predicted_intent is not None and intent!=predicted_intent: return result
    if intent=='CHECK_CALENDAR': result['disposition']='unsupported'
    elif intent in READ_LABELS: result.update(reads=1,query_text=query,disposition='read')
    return result
