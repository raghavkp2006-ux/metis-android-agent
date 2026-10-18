"""Training-only coverage search; cue retrieval never changes semantic labels."""
import argparse
from collections import Counter, defaultdict
import copy
from pathlib import Path
import re

import yaml
from ml.nlu_v1.dataset_v0 import restricted_training as restricted, exception_packet as packet
from ml.nlu_v1.dataset_v0.machine_curation import PublicEvidence
from ml.nlu_v1.dataset_v0.coverage_additions import coverage_report, COVERAGE
from ml.nlu_v1.data_management.dedupe import verify_report
from ml.nlu_v1.data_management.records import DataError, sha_json
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes
from ml.nlu_v1.splitting.split import read_jsonl, plan
from ml.nlu_v1.source_registry import ROOT, UniqueLoader

OUTPUT = DATASETS / 'quarantine/plan07-coverage-repair-001'
MEMORY_CUE = re.compile(r'\b(?:remember|recall|forgot|forget|memor(?:y|ies)|save|record|notes?|told|said|put|kept|stored|wrote)\b', re.I)
PROMISE_CUE = re.compile(r'\b(?:promis\w*|pledge\w*|vow\w*|commit\w*|agreed|owe|owed|assur\w*|undertook)\b', re.I)
EXPLICIT_PROMISE = re.compile(r'\b(?:promis(?:e|ed|es|ing)|pledge(?:d|s)?|vow(?:ed|s)?|commitment(?:s)?|agreed to|said (?:i|we|he|she|you) would|told .{0,40} (?:i|we) would)\b', re.I)


def native_fields(source, split, ident, native):
    if source not in ('massive_1_1_en_us', 'clinc150_full') or split not in ('train', 'oos_train'):
        raise DataError('COVERAGE_SEARCH_PROTECTED_OR_UNAUTHORIZED_SOURCE')
    if source == 'massive_1_1_en_us':
        if native['partition'] != split or str(native['id']) != str(ident):
            raise DataError('COVERAGE_SEARCH_NATIVE_IDENTITY_CHANGED')
        text, intent = native['utt'], native['intent']
    else:
        text, intent = native
    return {'source': source, 'original_split': split, 'source_id': str(ident),
            'original_intent': intent, 'text': text, 'native_sha256': sha_json(native)}


def promise_screen(text, intent):
    # These are exclusion/routing diagnostics, not canonical annotations.
    if EXPLICIT_PROMISE.search(text):
        return 'EXPLICIT_PROMISE_CUE_REQUIRES_MEANING_AND_MAPPING_VALIDATION'
    if intent == 'report_fraud' and re.search(r'\bcommitted\b', text, re.I):
        return 'FRAUD_COMMITTED_NOT_PROMISE'
    if intent in ('bill_balance', 'bill_due', 'taxes', 'min_payment', 'pto_balance', 'w2'):
        return 'FINANCIAL_OR_STATUTORY_OBLIGATION_NOT_RECORDED_PERSONAL_PROMISE'
    if intent == 'thank_you':
        return 'SOCIAL_IDIOM_NOT_SPECIFIC_PROMISE'
    if intent == 'yes':
        return 'DIALOGUE_AGREEMENT_NOT_PROMISE'
    if intent == 'confirm_reservation':
        return 'RESERVATION_CONFIRMATION_NOT_PROMISE'
    if re.search(r'\bvowels?\b', text, re.I):
        return 'PREFIX_RETRIEVAL_FALSE_POSITIVE'
    return 'UNRESOLVED_BOUNDARY_NOT_AUTOMATICALLY_MAPPED'


def search(sources):
    counts, selected, promise_counts = Counter(), [], Counter()
    for source_id, source in sorted(sources.items()):
        for (split, ident), native in sorted(source['lookup'].items()):
            row = native_fields(source_id, split, ident, native)
            counts[source_id] += 1
            row['cues'] = [name for name, pattern in (('MEMORY', MEMORY_CUE), ('PROMISE', PROMISE_CUE)) if pattern.search(row['text'])]
            if not row['cues']:
                continue
            row['status'] = 'RETRIEVAL_ONLY_NOT_CANONICAL_OR_TRAINING_APPROVAL'
            if 'PROMISE' in row['cues']:
                row['promise_screen'] = promise_screen(row['text'], row['original_intent'])
                promise_counts[row['promise_screen']] += 1
            selected.append(row)
    return {'scanned_training_rows': dict(counts), 'search_hits': selected,
            'cue_counts': dict(Counter(c for row in selected for c in row['cues'])),
            'promise_screen_counts': dict(promise_counts)}


def run(destination=OUTPUT):
    destination = Path(destination)
    if destination.exists() or (DATASETS / 'quarantine').resolve() not in destination.resolve().parents:
        raise DataError('NEW_LOCAL_COVERAGE_SEARCH_OUTPUT_REQUIRED')
    base = restricted.DESTINATION
    manifest = packet.read_json(base / 'manifest.json')
    for name, digest in manifest['files'].items():
        if packet.file_hash(base / name) != digest:
            raise DataError('RESTRICTED_CURATION_RUN_CHANGED')
    for name, digest in manifest['input_sha256'].items():
        if packet.file_hash(ROOT / name) != digest:
            raise DataError('RESTRICTED_CURATION_INPUT_CHANGED')
    rows = read_jsonl(base / 'restricted_candidates.jsonl')
    verify_report(rows, packet.read_json(base / 'dedupe_report.json'))
    exclusions = packet.read_json(base / 'selection_exclusions.json')
    excluded_ids = set(exclusions['duplicate_similarity_row_ids']) | set(exclusions['family_scenario_row_ids'])
    eligible = [r for r in rows if r['id'] not in excluded_ids]
    before = coverage_report(eligible)
    if before != packet.read_json(base / 'coverage_report.json') or len(eligible) != 1762:
        raise DataError('BASELINE_COVERAGE_OR_ELIGIBLE_MEMBERSHIP_CHANGED')
    evidence = PublicEvidence()
    if evidence.errors:
        raise DataError('COVERAGE_SEARCH_SOURCE_EVIDENCE_INVALID')
    result = search(evidence.sources)
    authorities = packet.read_json(base / 'source_authorities.json')
    authority_members = {source: set(value['privacy']['binding']['row_content_sha256']) for source, value in authorities.items()}
    eligible_ids = {r['id'] for r in eligible}
    # Canonical IDs depend on source/version/split/native ID, not query text.
    import hashlib, json
    for hit in result['search_hits']:
        source = hit['source']
        version = authorities[source]['privacy']['binding']['version']
        ident = hashlib.sha256(json.dumps([source,version,hit['original_split'],hit['source_id']], separators=(',',':')).encode()).hexdigest()
        hit['canonical_id'] = ident
        hit['within_existing_authorization_membership'] = ident in authority_members[source]
        hit['currently_retained_curation_candidate'] = ident in eligible_ids
        # Native labels are retained even where notes resemble MEMORY. No
        # unreviewed single-source broad intent override becomes an admission.
    explicit = result['promise_screen_counts'].get('EXPLICIT_PROMISE_CUE_REQUIRES_MEANING_AND_MAPPING_VALIDATION', 0)
    unresolved = result['promise_screen_counts'].get('UNRESOLVED_BOUNDARY_NOT_AUTOMATICALLY_MAPPED', 0)
    if explicit or unresolved:
        raise DataError('PROMISE_BOUNDARIES_REQUIRE_MEANING_ASSESSMENT_BEFORE_DEFICIT_CONCLUSION')
    inventory = {}
    for name in ('massive', 'clinc'):
        mapping = yaml.load((ROOT / f'ml/nlu_v1/ingestion/mappings/{name}_intent_mapping.yaml').read_text(encoding='utf-8'), Loader=UniqueLoader)
        inventory[name] = {'mapping_version': mapping['mapping_version'],
            'capability_intents': {domain:[k for k,v in mapping['intents'].items() if v.get('domain') == domain]
                                  for domain in ('MEMORY','PROMISE','TASK','GOAL','PROJECT','PLANNING','FOCUS','HABIT')}}
    # The observed missing PROMISE minimum is decisive. Do not modify unrelated
    # annotations/additions when the task explicitly says to stop if impossible.
    assignments, leakage = plan(eligible)
    policy = yaml.load(COVERAGE.read_text(encoding='utf-8'), Loader=UniqueLoader)
    summary = {'eligible_rows_before':len(eligible), 'approved_rows_before':sum(r['review_status']=='APPROVED' for r in eligible),
        'capabilities': {group: {'eligible': value['candidate']['counts'], 'approved':value['approved']['counts'],
                               'deficit':value['candidate']['deficit']} for group,value in before['groups'].items()},
        'search_scanned_training_rows':result['scanned_training_rows'], 'cue_counts':result['cue_counts'],
        'promise_screen_counts':result['promise_screen_counts'],
        'validated_memory_rows':before['groups']['MEMORY']['candidate']['counts']['rows'],
        'validated_promise_rows':before['groups']['PROMISE']['candidate']['counts']['rows'],
        'minimum_rows_per_capability':policy['minimums']['rows'], 'new_rows_added':0, 'rows_newly_excluded':0,
        'smallest_remaining_blocker':{'capability':'PROMISE','required_minimum':24,'available_validated_rows':0,'deficit':24},
        'meaning_assessment_scope':'Full training corpus searched by source inventory and broad lexical/phrase retrieval; all retrieved PROMISE cases screened. This is not a proof that every conceivable paraphrase is absent.',
        'memory_scope':'Some note-like/reminder/recall wording exists; no new fully governed MEMORY row was validated/admitted. Zero describes retained/validated coverage, not absence of all memory-like language.',
        'authorization_scope':'Search-only broader train corpus; existing policy003 authority is exact-member bound. Search hits outside that membership are not silently authorized or admitted.',
        'coverage_passed':False,'missing_candidate_groups':before['missing_candidate_groups'],
        'grouping_preview_leakage_passed':leakage['leakage_passed'],'dataset_leakage_passed':False,
        'dataset_v0_rows':0,'split_counts':{k:0 for k in ('TRAIN','DEVELOPMENT','CALIBRATION','SELECTION')},
        'dataset_v0_frozen':False,'plan07_complete':False,'training_executed':False,
        'protected_rows_searched':0,'synthetic_rows_used':0,'original_rows_or_decisions_modified':0}
    paths = [Path(__file__), COVERAGE, base/'manifest.json', base/'coverage_report.json', base/'source_authorities.json']
    for name in ('massive','clinc'):
        paths.append(ROOT/f'ml/nlu_v1/ingestion/mappings/{name}_intent_mapping.yaml')
    for source in evidence.sources:
        paths.append(DATASETS/'raw'/('v0_'+source)/'observation.json')
    fingerprints = {p.relative_to(ROOT).as_posix():packet.file_hash(p) for p in paths}
    files = {'summary.json':json_bytes(summary),'training_source_search.json':json_bytes(result),
        'source_mapping_inventory.json':json_bytes(inventory),'coverage_before.json':json_bytes(before),
        'coverage_after.json':json_bytes(before),'grouping_preview.json':json_bytes({'assignments':assignments,'report':leakage,'not_dataset_partitions':True})}
    destination.mkdir()
    for name,data in files.items():
        atomic_new(destination/name,data)
    atomic_new(destination/'manifest.json',json_bytes({'version':'nlu_v1_coverage_repair_search_001',
        'input_sha256':fingerprints,'files':{name:packet.file_hash(destination/name) for name in files},
        'rows_added':0,'publication_allowed':False,'dataset_v0_frozen':False}))
    return summary


def main():
    argparse.ArgumentParser(description=__doc__).parse_args()
    import json
    print(json.dumps(run(),sort_keys=True))


if __name__ == '__main__':
    main()
