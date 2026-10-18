"""Additive, closed Plan 05 templates and public-first Plan 07 coverage gates."""
from collections import Counter
import copy
import hashlib
import json
from functools import lru_cache

import yaml
from ml.nlu_v1.dataset_v0.machine_curation import file_hash, massive, clinc, load_mapping
from ml.nlu_v1.dataset_v0.build import bucket, DOMAIN_GROUPS
from ml.nlu_v1.data_management.records import (DataError, append_event, facts_hash, load_groups,
    migrate_ingestion, quarantine, sha_json, text_hash, validate_record, validate_groups)
from ml.nlu_v1.synthetic.generate import fields, render, make_rows, structures, SOURCE_PATH
from ml.nlu_v1.source_registry import ROOT, UniqueLoader
from ml.nlu_v1.splitting.split import official

TEMPLATES = ROOT / 'ml/nlu_v1/configs/coverage_templates_001.yaml'
COVERAGE = ROOT / 'ml/nlu_v1/configs/dataset_v0_coverage_policy.yaml'
VERSION = 'nlu_v1_coverage_generator_001'


def config():
    value = yaml.load(TEMPLATES.read_text(encoding='utf-8'), Loader=UniqueLoader)
    if value['template_version'] != 'nlu_v1_coverage_templates_001' or value['allowed_use'] != 'QUARANTINE_ONLY':
        raise DataError('COVERAGE_TEMPLATES_NOT_QUARANTINE_ONLY')
    return value


def generated_rows(batch='plan07-coverage-001', domains=None):
    spec = config()
    # Reuse the established canonical envelope, renderer and validators. Only
    # this new additive template extension creates the facts below.
    base, groups = make_rows(structures(17, 1)[:1], batch)
    rows = []
    for domain, forms in spec['capabilities'].items():
        if domains is not None and domain not in domains:
            continue
        for seed in range(4):
            values = {name: binding['values'][seed] for name, binding in spec['bindings'].items()}
            parent = None
            for role in ('read', 'write', 'context', 'boundary', 'ood'):
                styles = ('canonical', 'polite', 'fragment') if role in ('read', 'write') else ('canonical', 'polite') if role == 'context' else ('canonical',)
                for style in styles:
                    form = forms[role]
                    if style == 'polite':
                        form = 'please ' + form
                    elif style == 'fragment':
                        form = 'can you ' + form
                    rendered = render(form, {name: values[name] for name in fields(form)}, spec['bindings'])
                    action = forms['write_action'] if role == 'write' else 'SEND' if role == 'boundary' else 'NONE' if role == 'ood' else 'READ'
                    target_domain = 'UNKNOWN' if role == 'ood' else domain
                    disposition = {'context': 'SUPPORTED_NEEDS_CONTEXT', 'boundary': 'KNOWN_UNSUPPORTED', 'ood': 'OUT_OF_DOMAIN'}.get(role, 'SUPPORTED_COMPLETE')
                    structure = {'domain': domain, 'role': role, 'style': style, 'seed': seed,
                                 'template_version': spec['template_version'], 'bindings': {name: values[name] for name in fields(form)}}
                    row = copy.deepcopy(base[0])
                    row.update(rendered, domain=target_domain, action=action, disposition=disposition, ood=role == 'ood',
                               family=f'coverage:{domain.lower()}_{role}', metadata={'coverage_structure': structure,
                               'template_sha256': file_hash(TEMPLATES), 'generator_sha256': file_hash(__file__),
                               'origin_capability_group': bucket({'domain': domain, 'disposition': 'SUPPORTED_COMPLETE'}),
                               'generation_role': role, 'additive_source_extension': True},
                               privacy='UNKNOWN_PRIVACY', privacy_review=None, review_history=[], review_status='PENDING',
                               quarantine_reasons=[], composition=None, partition='UNASSIGNED', text_sha256=text_hash(rendered['text']))
                    row['source'].update(source_id=sha_json(structure), original_intent=domain.lower() + '_' + role,
                        mapper_version=VERSION, mapping_version=spec['template_version'],
                        mapping_decision='OOD' if role == 'ood' else 'KNOWN_UNSUPPORTED' if role == 'boundary' else 'MAP',
                        artifact_sha256=sha_json({'templates': file_hash(TEMPLATES), 'source': file_hash(SOURCE_PATH), 'engine': file_hash(__file__)}))
                    row['id'] = hashlib.sha256(json.dumps([row['source'][k] for k in ('dataset', 'version', 'original_split', 'source_id')], separators=(',', ':')).encode()).hexdigest()
                    if parent is None:
                        parent = row['id']
                    row['semantic_parent_id'] = parent
                    row['scenario_id'] = 'coverage-scenario:' + sha_json([row['family'], parent])
                    row['generation'] = {'parent_ids': [] if row['id'] == parent else [parent], 'seed': seed,
                                         'generator_id': VERSION, 'template_id': domain.lower() + ':' + role + ':' + style}
                    definition = {'domain': target_domain, 'action': action, 'required_slots': sorted({s['type'] for s in row['slots']}), 'dispositions': [disposition]}
                    groups[0]['families'][row['family']] = definition
                    scenario = {'family': row['family'], 'semantic_parent_id': parent, 'description': 'Additive closed coverage template; fictional seed; quarantine only'}
                    groups[1]['scenarios'][row['scenario_id']] = scenario
                    row['provenance'].update(ingestion_row_sha256=sha_json(rendered), import_row_number=len(rows) + 1,
                        import_file_sha256=file_hash(TEMPLATES), mapping_configuration_sha256=file_hash(TEMPLATES),
                        family_definition_sha256=sha_json(definition), scenario_definition_sha256=sha_json(scenario))
                    append_event(row, None, 'PENDING', 'machine:' + VERSION, 'CONTROLLED_STRUCTURE_RENDERED')
                    rows.append(quarantine(row, ['NEW_TEMPLATE_SEMANTIC_REVIEW_REQUIRED', 'PRIVACY_REVIEW_REQUIRED', 'INTAKE_REVIEW_PENDING', 'SPLIT_AUDIT_PENDING']))
    validate_groups(*groups)
    return rows, groups


@lru_cache(maxsize=4)
def trusted_extension(batch, template_sha256, engine_sha256):
    return {r['id']: r for r in generated_rows(batch)[0]}


def validate_extension(row):
    found = trusted_extension(row['provenance']['import_batch_id'], file_hash(TEMPLATES), file_hash(__file__)).get(row['id'])
    keys = ('text', 'slots', 'tokens', 'bio_tags', 'domain', 'action', 'disposition', 'ood', 'family',
            'scenario_id', 'semantic_parent_id', 'source', 'metadata', 'generation', 'provenance', 'partition')
    if found is None or any(row[k] != found[k] for k in keys):
        raise DataError('COVERAGE_GENERATION_NOT_BOUND_TO_TEMPLATE')
    validate_record(row)
    return True


def public_candidates(evidence, existing, maximum_per_group=32):
    counts, result = Counter(), []
    evidence.public_scan_exceptions = []
    wanted = {'TASK', 'MEMORY', 'PROMISE', 'GOAL_PROJECT', 'PLANNING_FOCUS_HABIT', 'PREFERENCE_KNOWLEDGE_ETC'}
    for dataset, source in sorted(evidence.sources.items()):
        table = load_mapping('massive_intent_mapping.yaml' if dataset.startswith('massive') else 'clinc_intent_mapping.yaml')
        for (split, ident), native in sorted(source['lookup'].items()):
            if split not in ('train', 'oos_train'):
                continue
            intent = native['intent'] if isinstance(native, dict) else native[1]
            target = table['intents'].get(intent, {})
            intended_group = DOMAIN_GROUPS.get(target.get('domain'))
            if intended_group not in wanted or counts[intended_group] >= maximum_per_group:
                continue
            try:
                mapped = massive(native) if dataset.startswith('massive') else clinc(native, split, ident)
            except ValueError as error:
                evidence.public_scan_exceptions.append({'source': dataset, 'original_split': split, 'source_id': ident,
                                                        'native_sha256': sha_json(native), 'reason': str(error)})
                continue
            capability = DOMAIN_GROUPS.get(mapped['domain']) if mapped else None
            if mapped is None or capability not in wanted:
                continue
            row = migrate_ingestion(mapped, batch_id='plan07-public-coverage-001', row_number=len(result) + 1,
                                    import_file_sha256=source['observation']['member_sha256'])
            if row['id'] in existing or counts[capability] >= maximum_per_group:
                continue
            row['source']['artifact_sha256'] = source['observation']['member_sha256']
            # Append a fact-bound event rather than editing an imported event.
            append_event(row, 'PENDING', 'PENDING', 'machine:coverage-public-001', 'SOURCE_ARTIFACT_BOUND')
            evidence.bind(row)
            result.append(quarantine(row, ['PROVISIONAL_SOURCE_MAPPING', 'PRIVACY_REVIEW_REQUIRED', 'INTAKE_REVIEW_PENDING']))
            counts[capability] += 1
    return result, dict(counts)


def coverage_report(rows):
    policy = yaml.load(COVERAGE.read_text(encoding='utf-8'), Loader=UniqueLoader)
    if policy['policy_version'] != 'nlu_v1_v0_coverage_001' or policy['freeze_requires_approved_coverage'] is not True:
        raise DataError('INVALID_COVERAGE_POLICY')
    report = {'policy_version': policy['policy_version'], 'groups': {}, 'missing_candidate_groups': [], 'missing_approved_groups': []}
    for group in policy['required_groups']:
        stages = {}
        for stage in ('candidate', 'approved'):
            subset = [r for r in rows if not official(r) and r['partition'] not in ('PROTECTED_EVALUATION', 'FINAL_INDEPENDENT_TEST') and
                      (DOMAIN_GROUPS.get(r['domain']) == group or r['metadata'].get('origin_capability_group') == group) and
                      (stage == 'candidate' or r['review_status'] == 'APPROVED')]
            counts = {'rows': len(subset), 'families': len({r['family'] for r in subset if r['family']}),
                      'scenarios': len({r['scenario_id'] for r in subset if r['scenario_id']}),
                      'actions': len({r['action'] for r in subset if r['action'] != 'NONE'}),
                      'slot_types': len({s['type'] for r in subset for s in r['slots']}),
                      'supported_complete': sum(r['disposition'] == 'SUPPORTED_COMPLETE' for r in subset),
                      'needs_context': sum(r['disposition'] == 'SUPPORTED_NEEDS_CONTEXT' for r in subset),
                      'known_unsupported': sum(r['disposition'] == 'KNOWN_UNSUPPORTED' for r in subset)}
            deficit = {key: max(0, minimum - counts[key]) for key, minimum in policy['minimums'].items()}
            near_ood = sum(r['ood'] and r['metadata'].get('origin_capability_group') == group for r in subset)
            # OOD variation applies to the explicitly extended missing capabilities.
            if group in {'TASK', 'MEMORY', 'PROMISE', 'GOAL_PROJECT', 'PLANNING_FOCUS_HABIT', 'PREFERENCE_KNOWLEDGE_ETC'}:
                deficit['near_ood'] = max(0, policy['near_ood_by_origin_minimum'] - near_ood)
            stages[stage] = {'counts': counts, 'targets': policy['minimums'], 'near_ood': near_ood, 'deficit': deficit,
                             'sources': dict(Counter(r['source']['dataset'] for r in subset))}
            if any(deficit.values()):
                report['missing_' + stage + '_groups'].append(group)
        report['groups'][group] = stages
    return report
