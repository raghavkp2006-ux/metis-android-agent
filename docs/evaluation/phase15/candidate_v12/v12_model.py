"""Disabled hierarchical classifier. Both heads learn from training text only."""
import json
import re
import sys
import numpy as np
from scipy.optimize import minimize_scalar
from scipy.special import logsumexp,softmax
from sklearn.feature_extraction import DictVectorizer
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline,FeatureUnion
from sklearn.preprocessing import FunctionTransformer

from v12_data import HERE,LABELS
sys.path.insert(0,str(HERE.parent / 'classifier_v11'))
from v11_model import preprocess,cue_rows as old_cues,metrics,require_partition,bind,export as numeric_head

MODEL_VERSION='phase15-classifier-v12'
PROTOCOL=json.loads((HERE / 'protocol.json').read_text(encoding='utf-8'))
THRESHOLDS=(.90,.92,.94,.96,.98,.99)


def cue_rows(texts):
    values=old_cues(texts)
    for text,value in zip(texts,values):
        value['recalled_speech']=int(bool(re.search(r'\b(?:have i|did i|what did i|what i)\b.*\b(?:said|say|told|tell|agree|agreed|offer|offered|promised|promise)\b',preprocess(text))))
        value['recorded_future']=int(bool(re.search(r'\b(?:would|will|was going to)\b',preprocess(text))))
        value['imperative_creation']=int(bool(re.search(r'^(?:(?:please|could you|can you|would you)\s+)*(?:create|add|make|set up|save|send|email|promise|tell|arrange|cancel|delete)\b',preprocess(text))))
    return values


def head(rows,labels,configuration):
    features=[('word',TfidfVectorizer(preprocessor=preprocess,ngram_range=(1,2),max_features=14000,sublinear_tf=True)),
        ('char',TfidfVectorizer(preprocessor=preprocess,analyzer='char_wb',ngram_range=(3,5),max_features=18000,sublinear_tf=True))]
    if configuration['cues']:
        features.append(('cue',Pipeline([('extract',FunctionTransformer(cue_rows)),('vectorize',DictVectorizer())])))
    model=Pipeline([('features',FeatureUnion(features)),('classifier',LogisticRegression(C=configuration['c'],
        class_weight='balanced',max_iter=2000,random_state=1512))])
    model.fit([r['utterance'] for r in rows],labels)
    return model


class Hierarchy:
    classes_=np.array(LABELS)
    def __init__(self,binary,intent): self.binary,self.intent=binary,intent
    def decision_function(self,texts):
        binary=self.binary.predict_proba(texts)
        intent=self.intent.predict_proba(texts)
        bclasses=list(self.binary.classes_);iclasses=list(self.intent.classes_)
        probabilities=np.column_stack([binary[:,bclasses.index('UNKNOWN')] if label=='UNKNOWN' else
            binary[:,bclasses.index('READ')]*intent[:,iclasses.index(label)] for label in LABELS])
        return np.log(np.maximum(probabilities,1e-15))


def fit(rows,configuration):
    require_partition(rows,'train')
    if configuration not in PROTOCOL['grid']: raise ValueError('Unspecified configuration')
    if any(r.get('provenance')=='user_submitted' or r['id'].startswith('user-') for r in rows):
        raise ValueError('User draft labels cannot enter fitting')
    known=[r for r in rows if r['intent']!='UNKNOWN']
    return Hierarchy(head(rows,['UNKNOWN' if r['intent']=='UNKNOWN' else 'READ' for r in rows],configuration),
        head(known,[r['intent'] for r in known],configuration))


def calibrate(model,rows):
    require_partition(rows,'calibration')
    if any(r.get('provenance')=='user_submitted' or r['id'].startswith('user-') for r in rows):
        raise ValueError('User draft batch cannot calibrate')
    logits=model.decision_function([r['utterance'] for r in rows])
    indices=np.array([LABELS.index(r['intent']) for r in rows])
    def loss(temp):
        scaled=logits/temp
        return float(np.mean(logsumexp(scaled,axis=1)-scaled[np.arange(len(rows)),indices]))
    result=minimize_scalar(loss,bounds=(.25,4.0),method='bounded')
    if not result.success: raise ValueError('Calibration failed')
    return float(result.x)


def predict(model,rows,settings=None):
    temp=1.0 if settings is None else settings['temperature']
    if not .25<=temp<=4.0: raise ValueError('Invalid temperature')
    probabilities=softmax(model.decision_function([r['utterance'] for r in rows])/temp,axis=1)
    raw=[LABELS[i] for i in probabilities.argmax(axis=1)]
    if settings is None: return raw,probabilities
    if settings['threshold'] not in THRESHOLDS: raise ValueError('Invalid threshold')
    return [label if not settings['abstain'] and p>=settings['threshold'] else 'UNKNOWN'
            for label,p in zip(raw,probabilities.max(axis=1))],probabilities


def calibration_metrics(rows,probabilities):
    indices=np.array([LABELS.index(r['intent']) for r in rows])
    one_hot=np.eye(len(LABELS))[indices]
    confidence=probabilities.max(axis=1);correct=probabilities.argmax(axis=1)==indices
    bins=[];ece=0.0
    for lower in np.arange(0,1,.1):
        mask=(confidence>=lower)&(confidence<lower+.1 if lower<.9 else confidence<=1)
        count=int(mask.sum())
        if count:
            accuracy=float(correct[mask].mean());mean=float(confidence[mask].mean())
            ece+=count/len(rows)*abs(accuracy-mean)
            bins.append(dict(lower=float(lower),count=count,accuracy=accuracy,confidence=mean))
    return dict(nll=float(-np.log(np.maximum(probabilities[np.arange(len(rows)),indices],1e-15)).mean()),
        brier=float(np.square(probabilities-one_hot).sum(axis=1).mean()),ece_10_bins=ece,bins=bins,
        scope='Synthetic development calibration diagnostic; not representative deployment calibration')


def select(train,calibration,selection):
    for rows,part in ((train,'train'),(calibration,'calibration'),(selection,'selection')):
        require_partition(rows,part)
        if any(r.get('provenance')=='user_submitted' for r in rows): raise ValueError('User draft batch cannot fit/calibrate/select')
    groups=[{r['family_id'] for r in rows} for rows in (train,calibration,selection)]
    if any(groups[i]&groups[j] for i in range(3) for j in range(i)): raise ValueError('Family overlap')
    trials=[];best=None;winner=None
    for configuration in PROTOCOL['grid']:
        model=fit(train,configuration);temperature=calibrate(model,calibration)
        for threshold in THRESHOLDS:
            settings=dict(temperature=temperature,threshold=threshold,abstain=False)
            labels,_=predict(model,selection,settings);score=metrics(selection,labels)
            trial=dict(configuration=configuration,settings=settings,score=score,eligible=score['unknown_false_accepts']==0)
            trials.append(trial)
            rank=(score['macro_f1'],-score['known_abstentions'],-int(configuration['cues']),-configuration['c'],-threshold)
            if trial['eligible'] and (best is None or rank>best): best,winner=rank,(model,trial)
    if winner is None:
        configuration=PROTOCOL['grid'][0];model=fit(train,configuration)
        winner=model,dict(configuration=configuration,settings=dict(temperature=calibrate(model,calibration),threshold=.99,abstain=True),eligible=False)
    return winner[0],winner[1],trials


def export(model,selected,bindings):
    heads={}
    for name,value in (('binary',model.binary),('intent',model.intent)):
        h=numeric_head(value,{},{});h['version']=MODEL_VERSION+'-'+name;h['payload_sha256']=bind(h);heads[name]=h
    payload=dict(version=MODEL_VERSION,ml_enabled=False,ml_packaged=False,adoption_eligible=False,
        selected=selected,bindings=bindings,heads=heads)
    payload['payload_sha256']=bind(payload)
    return payload


def restored_head(payload,texts):
    if bind(payload)!=payload['payload_sha256']: raise ValueError('Head drift')
    blocks=[]
    for f in payload['features']:
        if f['name']=='cue':
            v=DictVectorizer();v.vocabulary_=f['vocabulary'];v.feature_names_=sorted(v.vocabulary_,key=v.vocabulary_.get)
            matrix=v.transform(cue_rows(texts))
        else:
            v=TfidfVectorizer(preprocessor=preprocess,vocabulary=f['vocabulary'],analyzer=f['analyzer'],
                ngram_range=tuple(f['ngram_range']),sublinear_tf=f['sublinear_tf'],norm=f['norm'],token_pattern=f['token_pattern'])
            v.idf_=np.array(f['idf']);matrix=v.transform(texts)
        blocks.append(matrix)
    from scipy.sparse import hstack
    logits=hstack(blocks)@np.array(payload['coefficients']).T+np.array(payload['intercept'])
    if len(payload['labels'])==2:
        # sklearn binary logistic stores one log-odds row, not two multinomial rows.
        from scipy.special import expit
        positive=expit(logits[:,0]);return np.column_stack((1-positive,positive))
    return softmax(logits,axis=1)


def exported_predict(payload,rows):
    if bind(payload)!=payload['payload_sha256'] or payload['version']!=MODEL_VERSION: raise ValueError('Payload drift')
    if any(payload[k] for k in ('ml_enabled','ml_packaged','adoption_eligible')): raise ValueError('Unauthorized ML state')
    texts=[r['utterance'] for r in rows]
    b,i=payload['heads']['binary'],payload['heads']['intent']
    bp,ip=restored_head(b,texts),restored_head(i,texts)
    probabilities=np.column_stack([bp[:,b['labels'].index('UNKNOWN')] if label=='UNKNOWN' else
        bp[:,b['labels'].index('READ')]*ip[:,i['labels'].index(label)] for label in LABELS])
    settings=payload['selected']['settings']
    if not .25<=settings['temperature']<=4 or settings['threshold'] not in THRESHOLDS:
        raise ValueError('Invalid exported calibration settings')
    probabilities=softmax(np.log(np.maximum(probabilities,1e-15))/settings['temperature'],axis=1)
    raw=[LABELS[k] for k in probabilities.argmax(axis=1)]
    return [label if not settings['abstain'] and p>=settings['threshold'] else 'UNKNOWN' for label,p in zip(raw,probabilities.max(axis=1))],probabilities
