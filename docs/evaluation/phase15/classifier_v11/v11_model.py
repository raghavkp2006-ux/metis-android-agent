"""Offline multiclass experiment. Predictions never confer repository authority."""
import hashlib
import json
import re
import unicodedata

import numpy as np
from scipy.optimize import minimize_scalar
from scipy.special import logsumexp, softmax
from sklearn.feature_extraction import DictVectorizer
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import FeatureUnion, Pipeline
from sklearn.preprocessing import FunctionTransformer

from v11_data import HERE, LABELS, VERSION

PROTOCOL = json.loads((HERE / 'protocol.json').read_text(encoding='utf-8'))


def preprocess(text):
    return ' '.join(unicodedata.normalize('NFKC', text).lower().split())


# General request-derived lexical signals. These are learned input features,
# not classification rules or admission rules. Literal input is retained in
# the word/character blocks, including negation, constraints and full subjects.
CUES = {
    'task': r'\b(tasks?|work items?|task list|work list|backlog|to do)\b',
    'memory': r'\b(notes?|memories|memory|records?|recollections?|observations?|written|wrote|noted|saved|stored)\b',
    'promise': r'\b(promises?|promised|commitments?|commit|obligations?|pledges?|pledged|agreed|undertaken|undertake|owe)\b',
    'calendar': r'\b(calendar|appointments?|schedule|scheduled|agenda|meetings?|events?|diary|engagements?)\b',
    'outer_negation': r'^(?:(?:please|can you|could you|would you)\s+)*(?:do not|don[\x27’]t|never)\b|\bi do not want\b',
    'actions': r'\b(delete|erase|email|send|remove|publish|upload|cancel|buy|purchase|create|add|set|save|book|arrange|translate|explain|recommend|write)\b',
    'web': r'\b(web|internet|website)\b',
    'hypothetical': r'\b(pretend|perhaps|imagine|might|if i (?:wanted|asked))\b',
    'unresolved': r'\b(it|him|her|them|someone|somebody|that thing)\b',
    'recall': r'\b(recall|remember|remind|read back|look up|retrieve|fetch|show|list|find|review|inspect)\b',
    'future_action': r'\bremind me to\b|\bnew (?:fact|promise|note)\b',
    'compound': r'\b(?:and|then|after)\b',
}
COMPILED_CUES = {key: re.compile(value) for key, value in CUES.items()}


def cue_rows(texts):
    return [{key: min(3, len(pattern.findall(preprocess(text)))) for key, pattern in COMPILED_CUES.items()}
            for text in texts]


def require_partition(rows, split):
    if not rows or any(r['split'] != split for r in rows): raise ValueError('Expected only ' + split)
    if {r['intent'] for r in rows} != set(LABELS): raise ValueError('Missing labels in ' + split)


def fit(rows, configuration):
    require_partition(rows, 'train')
    if configuration not in PROTOCOL['feature_grid']: raise ValueError('Unspecified model configuration')
    features = [
        ('word', TfidfVectorizer(preprocessor=preprocess, ngram_range=(1, 2), max_features=14000, sublinear_tf=True)),
        ('char', TfidfVectorizer(preprocessor=preprocess, analyzer='char_wb', ngram_range=(3, 5), max_features=18000, sublinear_tf=True)),
    ]
    if configuration['cues']:
        features.append(('cue', Pipeline([('extract', FunctionTransformer(cue_rows)), ('vectorize', DictVectorizer())])))
    model = Pipeline([('features', FeatureUnion(features)), ('classifier', LogisticRegression(
        C=configuration['c'], class_weight='balanced', max_iter=2000, random_state=PROTOCOL['seed']))])
    model.fit([r['utterance'] for r in rows], [r['intent'] for r in rows])
    return model


def calibrate(model, rows):
    require_partition(rows, 'calibration')
    logits = model.decision_function([r['utterance'] for r in rows])
    indices = np.array([list(model.classes_).index(r['intent']) for r in rows])
    def loss(temperature):
        scaled = logits / temperature
        return float(np.mean(logsumexp(scaled, axis=1)-scaled[np.arange(len(rows)), indices]))
    result = minimize_scalar(loss, bounds=PROTOCOL['temperature_bounds'], method='bounded')
    if not result.success or not np.isfinite(result.fun): raise ValueError('Calibration failed')
    return float(result.x)


def apply_thresholds(labels, probabilities, settings):
    if not .25 <= settings['temperature'] <= 4: raise ValueError('Invalid temperature')
    thresholds = settings['thresholds']
    if set(thresholds) != set(LABELS) or any(value not in PROTOCOL['threshold_grid'] for value in thresholds.values()):
        raise ValueError('Invalid confidence thresholds')
    return [label if not settings.get('abstain', False) and confidence >= thresholds[label] else 'UNKNOWN'
            for label, confidence in zip(labels, probabilities.max(axis=1))]


def predict(model, rows, settings=None):
    temperature = 1.0 if settings is None else settings['temperature']
    if not .25 <= temperature <= 4: raise ValueError('Invalid temperature')
    probabilities = softmax(model.decision_function([r['utterance'] for r in rows])/temperature, axis=1)
    labels = [str(model.classes_[i]) for i in probabilities.argmax(axis=1)]
    return (labels if settings is None else apply_thresholds(labels, probabilities, settings)), probabilities


def metrics(rows, predictions):
    from sklearn.metrics import classification_report, confusion_matrix, f1_score
    expected = [r['intent'] for r in rows]
    report = classification_report(expected, predictions, labels=LABELS, output_dict=True, zero_division=0)
    return dict(rows=len(rows), macro_f1=float(f1_score(expected, predictions, labels=LABELS, average='macro', zero_division=0)),
        unknown_false_accepts=sum(a=='UNKNOWN' and b!='UNKNOWN' for a,b in zip(expected, predictions)),
        known_abstentions=sum(a!='UNKNOWN' and b=='UNKNOWN' for a,b in zip(expected, predictions)),
        per_class={label:report[label] for label in LABELS}, labels=list(LABELS),
        confusion=confusion_matrix(expected, predictions, labels=LABELS).tolist())


def calibration_metrics(model, rows, temperature):
    probabilities = softmax(model.decision_function([r['utterance'] for r in rows])/temperature, axis=1)
    classes = list(model.classes_)
    targets = np.array([[int(label==r['intent']) for label in classes] for r in rows])
    indices = np.array([classes.index(r['intent']) for r in rows])
    correct = probabilities.argmax(axis=1)==indices
    confidence = probabilities.max(axis=1)
    bins = np.minimum((confidence*10).astype(int), 9)
    ece = sum(float(np.mean(bins==b))*abs(float(confidence[bins==b].mean()-correct[bins==b].mean()))
              for b in range(10) if (bins==b).any())
    return dict(nll=float(-np.log(np.maximum(probabilities[np.arange(len(rows)),indices],1e-15)).mean()),
        brier=float(np.mean(np.sum((probabilities-targets)**2,axis=1))), ece_10_bins=ece,
        representative_deployment_calibration=False)


def select(train, calibration, selection):
    for rows, split in ((train,'train'),(calibration,'calibration'),(selection,'selection')): require_partition(rows,split)
    groups = [{r['family_id'] for r in rows} for rows in (train,calibration,selection)]
    if any(groups[i]&groups[j] for i in range(3) for j in range(i)): raise ValueError('Overlapping families')
    trials, best, winner = [], None, None
    for config in PROTOCOL['feature_grid']:
        model = fit(train,config)
        temperature = calibrate(model,calibration)
        probabilities = softmax(model.decision_function([r['utterance'] for r in selection])/temperature,axis=1)
        raw = [str(model.classes_[i]) for i in probabilities.argmax(axis=1)]
        settings_list = [dict(temperature=temperature, thresholds={label:t for label in LABELS}, mode='global', abstain=False)
                         for t in PROTOCOL['threshold_grid']]
        # Optimize each known class's cutoff on selection only, with no sampled
        # UNKNOWN accepts for that class. Multiclass aggregate is scored below.
        cutoffs = {'UNKNOWN': .90}
        for label in LABELS[:-1]:
            candidates = []
            for threshold in PROTOCOL['threshold_grid']:
                accepted = [pred==label and confidence>=threshold for pred,confidence in zip(raw,probabilities.max(axis=1))]
                if any(ok and row['intent']=='UNKNOWN' for ok,row in zip(accepted,selection)): continue
                tp = sum(ok and row['intent']==label for ok,row in zip(accepted,selection))
                fp = sum(ok and row['intent']!=label for ok,row in zip(accepted,selection))
                fn = sum(not ok and row['intent']==label for ok,row in zip(accepted,selection))
                candidates.append((2*tp/max(1,2*tp+fp+fn), -threshold, threshold))
            if not candidates: break
            cutoffs[label] = max(candidates)[2]
        if len(cutoffs)==len(LABELS):
            settings_list.append(dict(temperature=temperature, thresholds=cutoffs, mode='per_intent', abstain=False))
        for settings in settings_list:
            predictions = apply_thresholds(raw,probabilities,settings)
            score = metrics(selection,predictions)
            trial = dict(configuration=config, settings=settings, metrics=score, eligible=score['unknown_false_accepts']==0)
            trials.append(trial)
            if trial['eligible']:
                rank = (score['macro_f1'], -score['known_abstentions'], -int(config['cues']), -config['c'],
                        -int(settings['mode']=='per_intent'))
                if best is None or rank>best: best,winner=rank,(model,trial)
    if winner is None:
        config=PROTOCOL['feature_grid'][0]
        model=fit(train,config)
        winner=(model,dict(configuration=config,eligible=False,metrics={},settings=dict(temperature=calibrate(model,calibration),
            thresholds={label:.99 for label in LABELS},mode='global',abstain=True)))
    return winner[0],winner[1],trials


def bind(payload):
    value = {k:v for k,v in payload.items() if k!='payload_sha256'}
    return hashlib.sha256(json.dumps(value,sort_keys=True).encode()).hexdigest()


def export(model, selected, bindings):
    features=[]
    for name,block in model.named_steps['features'].transformer_list:
        vectorizer=block.named_steps['vectorize'] if name=='cue' else block
        feature=dict(name=name,vocabulary={k:int(v) for k,v in vectorizer.vocabulary_.items()})
        if name!='cue': feature.update(idf=vectorizer.idf_.tolist(),analyzer=vectorizer.analyzer,
            ngram_range=list(vectorizer.ngram_range),sublinear_tf=vectorizer.sublinear_tf,norm=vectorizer.norm,
            token_pattern=vectorizer.token_pattern)
        features.append(feature)
    classifier=model.named_steps['classifier']
    payload=dict(version=VERSION,ml_enabled=False,ml_packaged=False,adoption_eligible=False,bindings=bindings,
        selected=selected,labels=classifier.classes_.tolist(),features=features,
        coefficients=classifier.coef_.tolist(),intercept=classifier.intercept_.tolist())
    payload['payload_sha256']=bind(payload)
    return payload


def exported_predict(payload, rows):
    if bind(payload)!=payload['payload_sha256']: raise ValueError('Payload drift')
    if payload['version']!=VERSION or any(payload[k] for k in ('ml_enabled','ml_packaged','adoption_eligible')):
        raise ValueError('Unauthorized model state')
    blocks=[]
    for feature in payload['features']:
        if feature['name']=='cue':
            vectorizer=DictVectorizer()
            vectorizer.vocabulary_=feature['vocabulary']
            vectorizer.feature_names_=sorted(feature['vocabulary'],key=feature['vocabulary'].get)
            matrix=vectorizer.transform(cue_rows([r['utterance'] for r in rows]))
        else:
            vectorizer=TfidfVectorizer(preprocessor=preprocess,vocabulary=feature['vocabulary'],analyzer=feature['analyzer'],
                ngram_range=tuple(feature['ngram_range']),sublinear_tf=feature['sublinear_tf'],norm=feature['norm'],
                token_pattern=feature['token_pattern'])
            vectorizer.idf_=np.array(feature['idf'])
            matrix=vectorizer.transform([r['utterance'] for r in rows])
        blocks.append(matrix)
    from scipy.sparse import hstack
    logits=hstack(blocks)@np.array(payload['coefficients']).T+np.array(payload['intercept'])
    settings=payload['selected']['settings']
    probabilities=softmax(logits/settings['temperature'],axis=1)
    raw=[payload['labels'][i] for i in probabilities.argmax(axis=1)]
    return apply_thresholds(raw,probabilities,settings),probabilities
