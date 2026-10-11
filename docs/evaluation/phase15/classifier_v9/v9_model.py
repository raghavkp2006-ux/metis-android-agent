"""Offline v9 classifier experiment. Labels never authorize access or actions."""
import hashlib
import json
import unicodedata

import numpy as np
from scipy.optimize import minimize_scalar
from scipy.special import logsumexp, softmax
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import FeatureUnion, Pipeline

from v9_data import LABELS, SEED, VERSION

GRID = ((1.0, False), (4.0, False), (1.0, True), (4.0, True))
THRESHOLDS = tuple(n / 100 for n in range(90, 100))


def preprocess(text):
    # Preserve literal references. No oracle entity masking, task state or filter removal.
    return ' '.join(unicodedata.normalize('NFKC', text).lower().split())


def require_partition(rows, split):
    if not rows or any(r['split'] != split for r in rows):
        raise ValueError('Expected only ' + split + ' families')
    if {r['intent'] for r in rows} != set(LABELS):
        raise ValueError('Missing labels in ' + split)


def fit(rows, c, characters):
    require_partition(rows, 'train')
    features = [('word', TfidfVectorizer(preprocessor=preprocess, ngram_range=(1, 2),
                                        max_features=14000, sublinear_tf=True))]
    if characters:
        features.append(('char', TfidfVectorizer(preprocessor=preprocess, analyzer='char_wb',
                                                ngram_range=(3, 5), max_features=18000, sublinear_tf=True)))
    model = Pipeline([('features', FeatureUnion(features)), ('classifier', LogisticRegression(
        C=c, class_weight='balanced', max_iter=2000, random_state=SEED))])
    model.fit([r['utterance'] for r in rows], [r['intent'] for r in rows])
    return model


def calibrate(model, rows):
    require_partition(rows, 'calibration')
    logits = model.decision_function([r['utterance'] for r in rows])
    indices = np.array([list(model.classes_).index(r['intent']) for r in rows])
    def loss(temperature):
        scaled = logits / temperature
        return float(np.mean(logsumexp(scaled, axis=1) - scaled[np.arange(len(rows)), indices]))
    result = minimize_scalar(loss, bounds=(.25, 4.0), method='bounded')
    if not result.success or not np.isfinite(result.fun):
        raise ValueError('Calibration failed')
    return float(result.x)


def predict(model, rows, temperature=1.0, threshold=None, abstain=False):
    if not .25 <= temperature <= 4.0 or (threshold is not None and threshold not in THRESHOLDS):
        raise ValueError('Invalid calibration/threshold')
    probabilities = softmax(model.decision_function([r['utterance'] for r in rows]) / temperature, axis=1)
    raw = [str(model.classes_[index]) for index in probabilities.argmax(axis=1)]
    accepted = [label if not abstain and (threshold is None or confidence >= threshold) else 'UNKNOWN'
                for label, confidence in zip(raw, probabilities.max(axis=1))]
    return accepted, probabilities


def metrics(rows, labels):
    from sklearn.metrics import classification_report, confusion_matrix, f1_score
    expected = [r['intent'] for r in rows]
    report = classification_report(expected, labels, labels=LABELS, zero_division=0, output_dict=True)
    return dict(rows=len(rows), macro_f1=float(f1_score(expected, labels, labels=LABELS, average='macro', zero_division=0)),
        accuracy=sum(a == b for a, b in zip(expected, labels)) / len(rows),
        unknown_rows=expected.count('UNKNOWN'),
        unknown_false_accepts=sum(a == 'UNKNOWN' and b != 'UNKNOWN' for a, b in zip(expected, labels)),
        known_clarifications=sum(a != 'UNKNOWN' and b == 'UNKNOWN' for a, b in zip(expected, labels)),
        per_class={label: report[label] for label in LABELS},
        confusion=confusion_matrix(expected, labels, labels=LABELS).tolist(), labels=list(LABELS))


def calibration_metrics(model, rows, temperature):
    _, probabilities = predict(model, rows, temperature)
    classes = list(model.classes_)
    targets = np.array([[int(label == r['intent']) for label in classes] for r in rows])
    expected_index = np.array([classes.index(r['intent']) for r in rows])
    correct = probabilities.argmax(axis=1) == expected_index
    confidence = probabilities.max(axis=1)
    buckets = np.minimum((confidence * 10).astype(int), 9)
    ece = sum(float(np.mean(buckets == b)) * abs(float(confidence[buckets == b].mean() - correct[buckets == b].mean()))
              for b in range(10) if (buckets == b).any())
    return dict(nll=float(-np.log(np.maximum(probabilities[np.arange(len(rows)), expected_index], 1e-15)).mean()),
                multiclass_brier=float(np.mean(np.sum((probabilities - targets) ** 2, axis=1))),
                top_label_ece_10_bins=ece, representative_deployment_calibration=False)


def select(train, calibration, selection):
    require_partition(train, 'train'); require_partition(calibration, 'calibration'); require_partition(selection, 'selection')
    groups = [{r['family_id'] for r in rows} for rows in (train, calibration, selection)]
    if any(groups[i] & groups[j] for i in range(3) for j in range(i)):
        raise ValueError('Overlapping fitting/calibration/selection families')
    trials, best, winner = [], None, None
    for c, characters in GRID:
        model = fit(train, c, characters)
        temperature = calibrate(model, calibration)
        for threshold in THRESHOLDS:
            labels, _ = predict(model, selection, temperature, threshold)
            score = metrics(selection, labels)
            trial = dict(c=c, characters=characters, temperature=temperature, threshold=threshold, score=score,
                         eligible=score['unknown_false_accepts'] == 0)
            trials.append(trial)
            if trial['eligible']:
                rank = (score['macro_f1'], -score['known_clarifications'], -int(characters), -c, threshold)
                if best is None or rank > best:
                    best, winner = rank, (model, trial)
    if winner is None:
        # No threshold may be relaxed using held-out or exposed outcomes.
        model = fit(train, GRID[0][0], GRID[0][1])
        winner = (model, dict(c=GRID[0][0], characters=False, temperature=calibrate(model, calibration),
                             threshold=None, abstain=True, eligible=False))
    winner[1].setdefault('abstain', False)
    return winner[0], winner[1], trials


def export(model, selected, data_hash):
    features = []
    for name, vectorizer in model.named_steps['features'].transformer_list:
        features.append(dict(name=name, vocabulary={key: int(value) for key, value in vectorizer.vocabulary_.items()}, idf=vectorizer.idf_.tolist(),
            analyzer=vectorizer.analyzer, ngram_range=list(vectorizer.ngram_range), sublinear_tf=vectorizer.sublinear_tf,
            norm=vectorizer.norm, token_pattern=vectorizer.token_pattern))
    classifier = model.named_steps['classifier']
    payload = dict(version=VERSION, ml_enabled=False, ml_packaged=False, adoption_eligible=False,
        scope='Python development export only; no Android adapter or parity claim',
        preprocessing='NFKC, lowercase, collapse whitespace; no entity masking',
        data_sha256=data_hash, selected=selected, labels=classifier.classes_.tolist(),
        features=features, coefficients=classifier.coef_.tolist(), intercept=classifier.intercept_.tolist())
    payload['payload_sha256'] = hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()
    return payload


def exported_predict(payload, rows):
    """Reconstruct only feature configuration and numeric weights, never unpickle."""
    binding = payload['payload_sha256']
    value = {key: item for key, item in payload.items() if key != 'payload_sha256'}
    if hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest() != binding:
        raise ValueError('Export payload drift')
    if any(payload[key] for key in ('ml_enabled', 'ml_packaged', 'adoption_eligible')) or payload['version'] != VERSION:
        raise ValueError('Unauthorized export state')
    blocks = []
    for feature in payload['features']:
        vectorizer = TfidfVectorizer(preprocessor=preprocess, vocabulary=feature['vocabulary'], analyzer=feature['analyzer'],
            ngram_range=tuple(feature['ngram_range']), sublinear_tf=feature['sublinear_tf'], norm=feature['norm'],
            token_pattern=feature['token_pattern'])
        vectorizer.idf_ = np.array(feature['idf'])
        blocks.append(vectorizer.transform([r['utterance'] for r in rows]))
    from scipy.sparse import hstack
    logits = hstack(blocks) @ np.array(payload['coefficients']).T + np.array(payload['intercept'])
    probabilities = softmax(logits / payload['selected']['temperature'], axis=1)
    labels = [payload['labels'][i] for i in probabilities.argmax(axis=1)]
    settings = payload['selected']
    return [label if not settings['abstain'] and confidence >= settings['threshold'] else 'UNKNOWN'
            for label, confidence in zip(labels, probabilities.max(axis=1))], probabilities
