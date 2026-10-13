"""Versioned offline pipeline; data gates run before importing/fitting any model."""
import argparse
import copy
import hashlib
import importlib.util
import importlib.metadata
import json
import os
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
V15 = HERE.parent / 'candidate_v15'
OUTPUT = ROOT / 'app/build/reports/phase15/v16'
PROTOCOL = json.loads((HERE / 'protocol.json').read_text(encoding='utf-8'))
LABELS = PROTOCOL['labels']


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest()


def normalized(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split())


def frozen_development():
    freeze = json.loads((V15 / 'annotation-freeze.json').read_text(encoding='utf-8'))
    for name, expected in freeze['hashes'].items():
        if digest(V15 / name) != expected:
            raise ValueError('Frozen human-development drift: ' + name)
    data = json.loads((V15 / 'review-projection.json').read_text(encoding='utf-8'))
    rows = []
    for row in data['rows']:
        if not (row['human_approved'] and row['family_reviewed'] and all(row['reviewed_fields'].values())):
            raise ValueError('Unreviewed development annotation: ' + row['id'])
        if not row['duplicate_of']:
            rows.append(dict(id=row['id'], utterance=row['utterance'], intent=row['intended_category_draft'],
                             family_id=row['family_draft'], split=row['partition'],
                             disposition=row['expected_disposition_draft'],
                             entities=row['entities_draft'], subject=row['complete_subject_draft'],
                             constraints=row['constraints_draft'], human_approved=True,
                             author=row['author'], reviewer=row['reviewer']))
    return rows


def load_collection(path, purpose):
    """A packet is produced only after review of the exact final subset, not a pool."""
    path = Path(path)
    data = json.loads(path.read_text(encoding='utf-8'))
    if data.get('purpose') != purpose or not data.get('subset_human_approved') or not data.get('labels_frozen'):
        raise ValueError(purpose + ': exact subset and labels must be explicitly approved/frozen')
    if not data.get('subset_confirmation'):
        raise ValueError(purpose + ': missing human subset confirmation')
    if data.get('predictions_seen_before_label_freeze', True):
        raise ValueError(purpose + ': predictions preceded label freeze or exposure is undisclosed')
    rows = data['rows']
    quota = PROTOCOL[purpose.lower()]
    if Counter(row['intent'] for row in rows) != Counter(quota):
        raise ValueError(purpose + ': category quotas not met exactly')
    sources = data.get('source_bindings', {})
    if not sources:
        raise ValueError(purpose + ': missing reviewed source bindings')
    bound = {}
    for relative, expected in sources.items():
        source_path = (ROOT / relative).resolve()
        if not source_path.is_relative_to((ROOT / 'docs/evaluation/phase15').resolve()) or digest(source_path) != expected:
            raise ValueError(purpose + ': source path/hash mismatch')
        source = json.loads(source_path.read_text(encoding='utf-8'))
        for row in source.get('rows', []):
            if row['id'] in bound:
                raise ValueError('Source ID duplicated across packets')
            bound[row['id']] = row
    for row in rows:
        original = bound.get(row['id'])
        if original is None or not original.get('human_approved') or not original.get('family_human_reviewed'):
            raise ValueError(purpose + ': row not bound to reviewed human annotations')
        if not str(original.get('intended_collection', '')).startswith(purpose) or original.get('predictions_run'):
            raise ValueError(purpose + ': source collection purpose or pre-freeze exposure mismatch')
        expected = dict(utterance=original['utterance'], intent=original['intended_category_draft'],
                        family_id=original['proposed_family'], disposition=original['expected_disposition_draft'],
                        entities=original['entities_draft'], subject=original['complete_subject_draft'],
                        constraints=original['constraints_draft'])
        if any(row.get(key) != value for key, value in expected.items()):
            raise ValueError(purpose + ': reviewed row fields changed')
        if original.get('duplicate_of') or original.get('earlier_exact_matches'):
            raise ValueError(purpose + ': duplicate or earlier exposed exact overlap')
        if not row.get('author') or not row.get('reviewer') or not row.get('human_approved'):
            raise ValueError(purpose + ': provenance/review missing')
        if row.get('split') != purpose.lower():
            raise ValueError(purpose + ': wrong partition')
    return rows


def validate_partitions(parts):
    families, texts, ids = {}, {}, set()
    for split, rows in parts.items():
        if not rows or set(row['intent'] for row in rows) != set(LABELS):
            raise ValueError('All five labels required in ' + split)
        for row in rows:
            if row['split'] != split or row['id'] in ids:
                raise ValueError('Partition or ID mismatch in ' + split)
            ids.add(row['id'])
            key = normalized(row['utterance'])
            if key in texts:
                raise ValueError('Normalized duplicate/exposure across partitions')
            texts[key] = split
            family = row['family_id']
            if not family or (family in families and families[family] != split):
                raise ValueError('Family leakage across partitions')
            families[family] = split


def preflight(calibration_path=None, selection_path=None):
    blockers, parts = [], {}
    certificate_path = HERE.parent / 'read_candidate_v16/integrity.py'
    spec = importlib.util.spec_from_file_location('metis_v16_current_certificate', certificate_path)
    certificate = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(certificate)
    try:
        certificate.check()
    except (ValueError, FileNotFoundError) as error:
        blockers.append(str(error))
    original = json.loads((V15 / 'protocol.json').read_text(encoding='utf-8'))
    fixed = original['classifier']
    if (PROTOCOL['feature_grid'] != fixed['grid'] or PROTOCOL['seed'] != fixed['seed'] or
        PROTOCOL['threshold_grid'] != fixed['thresholds'] or
        PROTOCOL['temperature_bounds'] != fixed['temperature_bounds']):
        blockers.append('Classifier configuration differs from the predeclared v15 plan')
    if (PROTOCOL['calibration'] != original['later_human_data']['calibration'] or
        PROTOCOL['selection'] != original['later_human_data']['selection']):
        blockers.append('Human collection quotas differ from the predeclared v15 plan')
    for requirement in (HERE.parent / 'requirements.txt').read_text(encoding='utf-8').splitlines():
        package, expected = requirement.split('==')
        try:
            if importlib.metadata.version(package) != expected:
                blockers.append('Dependency version mismatch: ' + requirement)
        except importlib.metadata.PackageNotFoundError:
            blockers.append('Missing pinned dependency: ' + requirement)
    development = frozen_development()
    for split in ('train', 'development_validation', 'development_test'):
        parts[split] = [row for row in development if row['split'] == split]
    paths = {'calibration': calibration_path, 'selection': selection_path}
    for split, path in paths.items():
        if path is None or not Path(path).is_file():
            blockers.append('Missing exact reviewed/frozen ' + split + ' packet')
        else:
            try:
                parts[split] = load_collection(path, split.upper())
            except (ValueError, KeyError, OSError) as error:
                blockers.append(str(error))
    if not blockers:
        try:
            validate_partitions(parts)
        except ValueError as error:
            blockers.append(str(error))
    status = dict(version=PROTOCOL['version'], status='BLOCKED' if blockers else 'READY_TO_FIT',
                  blockers=blockers, production_ml_enabled=False, fitting_started=False,
                  counts={split: dict(Counter(row['intent'] for row in rows)) for split, rows in parts.items()},
                  known_collection_gap='Intake 006 reviewed pool still needs 30 calendar-event, 12 promise and 6 other additions, then exact subset approval; separate 50 selection requests pending',
                  parser_gap='Frozen v15 baseline had 52 missing read forms; separate v16 deterministic parser closes them in native/Python regressions. Classifier labels still cannot widen read authority.',
                  independent_evaluation=False)
    return status, parts


def engine():
    # Set thread limits before any NumPy/scikit-learn import, regardless of shell defaults.
    for name in ('OMP_NUM_THREADS', 'OPENBLAS_NUM_THREADS', 'MKL_NUM_THREADS', 'NUMEXPR_NUM_THREADS'):
        os.environ[name] = '1'
    folder = HERE.parent / 'classifier_v11'
    sys.path.insert(0, str(folder))
    spec = importlib.util.spec_from_file_location('metis_v16_flat_engine', folder / 'v11_model.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.PROTOCOL = copy.deepcopy(PROTOCOL)
    module.VERSION = 'phase15-classifier-v16'
    return module


def train(calibration_path, selection_path, attempt):
    status, parts = preflight(calibration_path, selection_path)
    if status['blockers']:
        return status  # No model import, fitting, calibration or predictions.
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}', attempt):
        raise ValueError('Invalid attempt name')
    output = OUTPUT / attempt
    if OUTPUT.exists() and list(OUTPUT.glob('*/attempt.json')):
        raise ValueError('This candidate has already started a scored attempt; further tuning requires another candidate version')
    if output.exists():
        raise ValueError('Existing attempt cannot be overwritten; scored failure requires a new candidate version')
    # Claim output before fitting so crashes cannot silently become unrecorded retrials.
    output.mkdir(parents=True)
    bindings = {'protocol': digest(HERE / 'protocol.json'), 'development_freeze': digest(V15 / 'annotation-freeze.json'),
                'calibration': digest(Path(calibration_path)), 'selection': digest(Path(selection_path)),
                'pipeline': digest(HERE / 'pipeline.py'),
                'current_certificate': digest(HERE.parent / 'read_candidate_v16/development-manifest-v16.json'),
                'requirements': digest(HERE.parent / 'requirements.txt')}
    (output / 'attempt.json').write_text(json.dumps(dict(status='STARTED', bindings=bindings,
        production_ml_enabled=False), indent=2) + '\n', encoding='utf-8')
    try:
        m = engine()
        from threadpoolctl import threadpool_limits
        with threadpool_limits(limits=1):
            return _run(m, parts, bindings, output)
    except Exception as error:
        (output / 'failure.json').write_text(json.dumps(dict(status='FAILED', error=str(error),
            production_ml_enabled=False), indent=2) + '\n', encoding='utf-8')
        raise


def _run(m, parts, bindings, output):
    winner, trials = None, []
    for configuration in PROTOCOL['feature_grid']:
        model = m.fit(parts['train'], configuration)
        temperature = m.calibrate(model, parts['calibration'])
        for cutoff in PROTOCOL['threshold_grid']:
            settings = dict(temperature=temperature, thresholds={label: cutoff for label in LABELS}, abstain=False)
            predictions, _ = m.predict(model, parts['selection'], settings)
            metrics = m.metrics(parts['selection'], predictions)
            trial = dict(configuration=configuration, settings=settings, metrics=metrics,
                         eligible=metrics['unknown_false_accepts'] == 0)
            trials.append(trial)
            rank = (metrics['macro_f1'], -metrics['known_abstentions'], -int(configuration['cues']), -configuration['c'], cutoff)
            if trial['eligible'] and (winner is None or rank > winner[0]):
                winner = rank, model, trial
    if winner is None:
        result = dict(status='NO_GO', reason='No zero-UNKNOWN-false-accept configuration on selection',
                      trials=trials, bindings=bindings, production_ml_enabled=False, independent_evaluation=False)
    else:
        _, model, selected = winner
        # Score development test only after the selection decision is fixed; never feed it back.
        test_predictions, test_probabilities = m.predict(model, parts['development_test'], selected['settings'])
        test_metrics = m.metrics(parts['development_test'], test_predictions)
        validation_predictions, _ = m.predict(model, parts['development_validation'], selected['settings'])
        payload = m.export(model, selected, bindings)
        restored, restored_probabilities = m.exported_predict(payload, parts['development_test'])
        parity = restored == test_predictions and bool(m.np.allclose(restored_probabilities, test_probabilities, atol=1e-10, rtol=1e-10))
        safety = safety_diagnostic(parts['development_test'], test_predictions)
        challenge = json.loads((HERE / 'synthetic-challenge.json').read_text(encoding='utf-8'))['rows']
        challenge_predictions, _ = m.predict(model, challenge, selected['settings'])
        raw_test = [str(model.classes_[i]) for i in test_probabilities.argmax(axis=1)]
        passed = (test_metrics['macro_f1'] >= .90 and test_metrics['unknown_false_accepts'] == 0 and parity
                  and safety['forbidden_reads'] == 0 and safety['existing_valid_reads_lost'] == 0
                  and safety['safe_outcome_gain'] > 0)
        result = dict(status='OFFLINE_GO_PENDING_NATIVE_VALIDATION' if passed else 'NO_GO', selected=selected,
                      semantic_test=test_metrics, raw_semantic_test_diagnostic=m.metrics(parts['development_test'], raw_test),
                      semantic_validation_diagnostic=m.metrics(parts['development_validation'], validation_predictions),
                      synthetic_challenge_diagnostic=m.metrics(challenge, challenge_predictions),
                      calibration_diagnostics=m.calibration_metrics(model, parts['calibration'], selected['settings']['temperature']),
                      numeric_export_parity=parity, read_safety=safety, trials=trials, bindings=bindings,
                      production_ml_enabled=False, independent_evaluation=False,
                      limitation='Exposed same-user reviewed development; only two calendar development-test examples. No fresh evaluation or Android ML validation. Python safety oracle must be followed by native context/read-port tests.')
        (output / 'model.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (output / 'first-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    return result


def safety_diagnostic(rows, predictions):
    sys.path.insert(0, str(V15))
    from v15_candidate import resolve as baseline_rules
    sys.path.insert(0, str(HERE.parent / 'read_candidate_v16'))
    from v16_grammar import resolve as current_rules
    forbidden = lost = gain = parser_gain = classifier_gain = 0
    for row, prediction in zip(rows, predictions, strict=True):
        baseline = baseline_rules(row['utterance'])
        current = current_rules(row['utterance'])
        modeled = current_rules(row['utterance'], predicted_intent=prediction)
        allowed = row['disposition'] == 'read'
        forbidden += int(bool(modeled['reads']) and not allowed)
        lost += int(bool(baseline['reads']) and not modeled['reads'])
        gain += int(allowed and bool(modeled['reads'])) - int(allowed and bool(baseline['reads']))
        parser_gain += int(allowed and bool(current['reads'])) - int(allowed and bool(baseline['reads']))
        classifier_gain += int(allowed and bool(modeled['reads'])) - int(allowed and bool(current['reads']))
    return dict(forbidden_reads=forbidden, existing_valid_reads_lost=lost, safe_outcome_gain=gain,
                deterministic_parser_gain=parser_gain, incremental_classifier_read_gain=classifier_gain,
                scope='Python full-request guard diagnostic; never native repository authorization',
                structural_limit='Additional read-form coverage is deterministic parser work; confidence alone never widens authority. Incremental ML benefit must be assessed separately before adoption.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--calibration', type=Path)
    parser.add_argument('--selection', type=Path)
    parser.add_argument('--fit', action='store_true')
    parser.add_argument('--attempt', default='first')
    args = parser.parse_args()
    result = train(args.calibration, args.selection, args.attempt) if args.fit else preflight(args.calibration, args.selection)[0]
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if not args.fit:
        (OUTPUT / 'preflight.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
