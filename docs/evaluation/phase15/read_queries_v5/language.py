"""Development-only read parsing. No Android, repository, or executor dependency."""
import re
from dataclasses import asdict, dataclass, field
from datetime import date, timedelta

import numpy as np
from scipy.special import softmax, logsumexp
from scipy.optimize import minimize_scalar
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import FeatureUnion, Pipeline

from data import READ_LABELS, SEED
from v4_language import normalize, entity, RANGE

@dataclass
class ReadParse:
    intent: str
    disposition: str
    reason: str
    entities: list = field(default_factory=list)
    filters: dict = field(default_factory=dict)
    reads: int = 0
    proposals: int = 0
    completed: int = 0
    scope: str = 'Development simulation; no repository calls or mutations'

    def json(self):
        return asdict(self)

def canonical(text):
    value = normalize(text)
    substitutions = {'abt':'about', 're:':'about', 'pls':'please', 'rn':'right now', 'gonna':'going to',
                     'dikhao':'show', 'batao':'show', 'yaar':'', 'bro':'', 'kya hai':'',
                     'ka scene':'status', 'ka':'', 'scene':'status'}
    for old,new in substitutions.items():
        value=re.sub(r'(?<!\w)'+re.escape(old)+r'(?!\w)',new,value)
    return re.sub(r'\s+',' ',value).strip()

def guard(text, source='TEXT'):
    import unicodedata
    if source not in {'TEXT','VOICE'}:
        return 'unsupported_source'
    if any(unicodedata.category(c).startswith('C') for c in text):
        return 'control_character'
    if not text.strip() or len(text.encode('utf-16-le'))//2>512:
        return 'length'
    if any(c.isalpha() and 'LATIN' not in unicodedata.name(c,'') for c in text):
        return 'unsupported_script'
    syntax=re.sub(r"(?<=[^\W\d_])['’](?=[^\W\d_])",'',text)
    if re.search(r'''["'‘’“”`;\\|&=<>]''',syntax):
        return 'quoted_or_complex'
    value=canonical(text)
    if re.match(r'^(?:no|nahi|mat)\b',value):return 'explicit_negation'
    if re.match(r'^(?:turn (?:on|off)|how can i|how do i|how to)\b',value):
        return 'action_or_advice_outside_read_scope'
    if re.match(r'^(?:show public|find internet|search the web|look up public|explain\b|define\b|write\b|suggest\b)',value):
        return 'general_information_or_external_search'
    if re.match(r'^what (?:is|are)\b',value) and not re.search(r'\b(?:i|my|saved|stored|recorded|pending|unfinished|left|remaining|schedule|calendar)\b',value):
        return 'general_information_definition'
    if re.match(r'^what (?:is|are)\b.*\bused for\b',value):return 'general_information_definition'
    if re.search(r'\b(?:suppose|imagine|hypothetical|pretend|if someone|if i|never said|system|developer|ignore|override)\b',value):
        return 'hypothetical_or_injection'
    if re.search(r'\b(?:do not|does not|never|cannot|will not|without|stop|avoid)\b',value):
        return 'negated_or_contradictory'
    # Verbs inside a question about an existing promise describe a past commitment.
    promise_question=bool(re.search(r'\b(?:i (?:promise\w*|commit\w*|agreed?|owe|told|tell|say|said)|did i|what (?:was|am) i)\b',value))
    if re.search(r'\b(?:delete|erase|wipe|remove|rename|postpone|buy|order|book|cancel|execute|create|notify|alert)\b',value):
        return 'action_outside_read_scope'
    if re.search(r'\b(?:send|message|call|upload|bring|give)\b',value) and not promise_question:
        # "give me my remaining tasks" is a retrieval request.
        if not re.match(r'^give me\b',value):
            return 'external_or_action_request'
    remainder=RANGE.sub(' time range ',value)
    remainder=re.sub(r'\boverdue and not done\b','overdue unfinished',remainder)
    remainder=re.sub(r'\bor chill\b','',remainder)
    remainder=re.sub(r'\bbetween me and\b','involving',remainder)
    remainder=re.sub(r'\s+if possible[?!.]*$','',remainder)
    remainder=re.sub(r'^check if\b','check whether',remainder)
    if re.search(r'\b(?:and|then|also|or)\b',remainder):
        return 'compound_or_ambiguous'
    # not-done describes a task state; double negation remains ambiguous.
    remainder=re.sub(r'\bnot\s+(?:yet\s+)?(?:been\s+)?(?:done|finished|completed|ticked off)\b','unfinished',remainder)
    if re.search(r'\bnot\b',remainder):
        return 'unresolved_negation'
    if ':' in re.sub(r'\b(?:re|search):','',RANGE.sub('range',value)):
        return 'complex_syntax'
    return None

def _core(text):
    core=text
    while True:
        end=re.search(r'(?:\s+(?:please|pls|for me|right now|thanks|if possible|batao|dikhao))?[?!.]*$',core,re.I).start()
        if end==len(core):return core
        core=core[:end]

def extract(text,intent):
    core=_core(text)
    if intent=='CHECK_MEMORY':
        # Ignore the retrieval verb "look for"; preserve original offsets.
        begin=re.match(r'look\s+for\b',core,re.I)
        start_at=begin.end() if begin else 0
        pattern=r'\b(?:about|abt|concerning|regarding|mentioning|mention|under|on|for|from|re:)\s+(.+)$'
        match=re.search(pattern,core[start_at:],re.I)
        if not match:
            match=re.search(r'\bmemory search:\s*(.+)$',core,re.I)
            start_at=0
        if not match:
            match=re.search(r'^(.+?)\s+ke\s+(?:saved\s+)?notes$',core,re.I)
            start_at=0
        if not match:
            return []
        start,end=match.span(1);start+=start_at;end+=start_at
        # A trailing retrieval instruction or qualifier is not part of the topic.
        tail=re.search(r',\s*(?:find it|check|the old ones)\b|\s+somewhere$',core[start:end],re.I)
        if tail:
            end=start+tail.start()
        raw=text[start:end].strip()
        if not raw or len(raw)>200 or re.search(r'…|\.\.\.|\b(?:it|this|that|thing|something|anything|whichever)\b',raw,re.I) or re.fullmatch(r'(?:the\s+)?(?:chapter|topic|class|notes?)',raw,re.I):
            return []
        return [entity(text,'MEMORY',start,end)]
    if intent=='WHAT_DID_I_PROMISE':
        # Grammar-bounded captures allow names without treating temporal nouns as people.
        patterns=[
            r'\b(?:tell|told)\s+(.+?)\s+(?:I[’\']d|I would|I was|I will)\b',
            r'\b(?:owe|send|give|bring|help|helping)\s+(.+?)(?:\s+(?:some work|anything|something|with|from last week|from yesterday)|$)',
            r'\b(?:promise|promised)\s+(?!to\b)(.+?)(?:\s+(?:anything|something|this week|recently)|$)',
            r'\bbetween me and\s+(.+)$',
            r'\b(?:for|to|with|from|involving|concerns|towards)\s+(.+?)(?:\s+(?:on|with|still incomplete|anything|something|this week|recently|from last week)|$)',
            r'^(.+?)\s+ko\s+kya\s+promise\s+kiya\s+tha$',
        ]
        invalid=r'\b(?:her|him|them|someone|somebody|anyone|it|me|myself|you|yesterday|tomorrow|today|week|recently|anything|something|do|doing|done|get|work|need|giving|finish|help|handle|send|upload|yes|my|commitment|promise|for|to|with|from|concerns|involving|towards)\b'
        for pattern in patterns:
            for match in re.finditer('(?='+pattern+')',core,re.I):
                raw=match.group(1)
                if len(raw.split())<=3 and len(raw)<=100 and re.fullmatch(r"[^\W\d_]+(?:[’' -][^\W\d_]+)*",raw) and not re.search(invalid,raw,re.I):
                    return [entity(text,'PERSON',*match.span(1))]
    return []

def filters(text,intent,reference='2026-10-09'):
    value=canonical(text)
    result={}
    if intent=='CHECK_TASKS':
        result['status']='incomplete'
        if re.search(r'\boverdue\b',value):result['overdue']=True
        if re.search(r'\bassignments?\b',value):result['category']='assignment'
    temporal=re.search(r'\b(?:today|tonight|tomorrow|yesterday|this week|last week|recently|before i sleep)\b',value)
    if temporal:
        raw=temporal.group()
        result['time_expression']=raw
        anchor=date.fromisoformat(reference)
        if raw in {'today','tonight','tomorrow','yesterday'}:
            day=anchor+timedelta(days={'tomorrow':1,'yesterday':-1}.get(raw,0))
            result['date_range']={'start':day.isoformat(),'end_exclusive':(day+timedelta(days=1)).isoformat()}
    if re.search(r'\b(?:kal|parso)\b',value):result['ambiguous_relative_date']=True
    if intent=='WHAT_DID_I_PROMISE' and re.search(r'\b(?:pending|incomplete|still owe)\b',value):result['status']='unresolved'
    if intent=='CHECK_MEMORY' and re.search(r'\b(?:old ones|older)\b',value):result['age']='older_unspecified'
    return result

def resolve(text,intent,source='TEXT',reference='2026-10-09'):
    unsafe=guard(text,source)
    if unsafe:
        clarify=unsafe in {'compound_or_ambiguous','unresolved_negation'} or bool(re.search(r'\b(?:but|except)\b',text,re.I))
        return ReadParse(intent,'clarify' if clarify else 'reject',unsafe).json()
    # Missing-reference protection is independent of the classifier's chosen label.
    value=canonical(text)
    ambiguous_person=re.search(r'\b(?:promise\w*|owe)\b.*\b(?:him|her|them|someone|somebody|anyone)\b',value)
    ambiguous_duty=re.fullmatch(r'what (?:was|am) i (?:supposed|meant|going) to do[?!.]*',value)
    ambiguous_notes=re.search(r'\bnotes?\b',value) and not extract(text,'CHECK_MEMORY')
    if ambiguous_person or ambiguous_duty or ambiguous_notes:
        return ReadParse(intent,'clarify','unresolved_reference_or_topic').json()
    if intent not in READ_LABELS:return ReadParse('UNKNOWN','clarify','unknown_or_low_confidence').json()
    entities=extract(text,intent)
    qualifiers=filters(text,intent,reference)
    if re.search(r'…|\.\.\.|\bnext\s*[?!.]*$',text,re.I):
        return ReadParse(intent,'clarify','incomplete_request',entities,qualifiers).json()
    if intent=='CHECK_CALENDAR':return ReadParse(intent,'unsupported','calendar_unavailable',entities,qualifiers).json()
    if intent in {'CHECK_MEMORY','WHAT_DID_I_PROMISE'} and not entities:
        return ReadParse(intent,'clarify','missing_or_ambiguous_entity',[],qualifiers).json()
    if intent=='WHAT_DID_I_PROMISE' and re.search(r'\bsupposed\b',text,re.I):
        return ReadParse(intent,'clarify','ambiguous_commitment',entities,qualifiers).json()
    # Current read port has no structured filters. Never discard a requested constraint.
    unsupported=set(qualifiers)-({'status'} if intent=='CHECK_TASKS' else set())
    if unsupported:
        return ReadParse(intent,'clarify','unsupported_retrieval_filter',entities,qualifiers).json()
    return ReadParse(intent,'read','resolved',entities,qualifiers,reads=1).json()

def feature_text(text):
    spans=extract(text,'CHECK_MEMORY') if re.search(r'\b(?:notes?|saved|stored|memory|wrote|written|writing|points|kept|recorded|information|info|records|facts|details|entry)\b',text,re.I) else []
    if not spans and re.search(r'\b(?:i|my|me|promise\w*|commit\w*)\b',text,re.I):
        spans=extract(text,'WHAT_DID_I_PROMISE')
    markers=[]
    value=canonical(text)
    memory_cue=bool(re.search(r'\b(?:notes?|saved|stored|memory|wrote|written|records|recorded|kept)\b',value))
    calendar_cue=bool(re.search(r'\b(?:calendar|schedule|appointments?|meetings?|booked|busy|free|slots?|gap|clash|back-to-back)\b',value))
    promise_cue=bool(re.search(r'\b(?:promise\w*|commit\w*|owe|agreed?|pledged?|obligation)\b',value))
    task_state=bool(re.search(r'\b(?:pending|unfinished|incomplete|undone|remaining|left|outstanding|overdue|ticked off|wrap up|halfway|not done|not completed|yet to finish)\b',value))
    if memory_cue:markers.append('memorywording')
    if calendar_cue:markers.append('calendarwording')
    if promise_cue or spans and spans[0]['type']=='PERSON':markers.append('commitmentwording')
    if task_state and not (memory_cue or calendar_cue or promise_cue or spans and spans[0]['type']=='PERSON'):
        markers.append('unfinishedwording')
    if spans:
        span=spans[0];encoded=text.encode('utf-16-le')
        text=encoded[:span['startOffset']*2].decode('utf-16-le')+(' topicvalue ' if span['type']=='MEMORY' else ' personvalue ')+encoded[span['endOffset']*2:].decode('utf-16-le')
    return canonical(text)+' '+' '.join(markers)

def train(rows,C,characters):
    if not rows or any(r['split']!='train' for r in rows):raise ValueError('Train partition required')
    rows=[r for r in rows if guard(r['utterance'],r['input_source']) is None]
    blocks=[('word',TfidfVectorizer(preprocessor=feature_text,ngram_range=(1,2),sublinear_tf=True,max_features=14000))]
    if characters:blocks.append(('char',TfidfVectorizer(preprocessor=feature_text,analyzer='char_wb',ngram_range=(3,5),sublinear_tf=True,max_features=18000)))
    model=Pipeline([('features',FeatureUnion(blocks,transformer_weights={'word':1.,'char':.5} if characters else {'word':1.})),
                    ('classifier',LogisticRegression(C=C,class_weight='balanced',max_iter=2000,random_state=SEED))])
    model.fit([r['utterance'] for r in rows],[r['intent'] for r in rows])
    return model

def predict(model,rows,temperature,threshold):
    probabilities=softmax(model.decision_function([r['utterance'] if not guard(r['utterance'],r['input_source']) else '' for r in rows])/temperature,axis=1)
    labels=[]
    for row,p in zip(rows,probabilities):
        label=str(model.classes_[p.argmax()])
        labels.append(label if not guard(row['utterance'],row['input_source']) and label in READ_LABELS and threshold is not None and np.isfinite(p).all() and p.max()>=threshold else 'UNKNOWN')
    return labels,probabilities

def calibrate(model,rows):
    """Bound sharpening on small synthetic calibration; not deployment calibration."""
    if not rows or any(r['split']!='calibration' for r in rows):raise ValueError('Calibration families required')
    logits=model.decision_function([r['utterance'] for r in rows])
    targets=np.asarray([list(model.classes_).index(r['intent']) for r in rows])
    def loss(log_temperature):
        scaled=logits/np.exp(log_temperature)
        return float(np.mean(logsumexp(scaled,axis=1)-scaled[np.arange(len(rows)),targets]))
    bounds=(float(np.log(.25)),float(np.log(4.)))
    result=minimize_scalar(loss,bounds=bounds,method='bounded')
    if not result.success:raise ValueError('Calibration did not converge')
    return float(np.exp(result.x)),dict(method='Bounded scalar temperature on eligible synthetic calibration families',
        temperature_bounds=[.25,4.],raw_nll=loss(0.),calibrated_nll=float(result.fun),
        limit='Floor limits extreme sharpening on small synthetic samples; not evidence of representative calibration')
