"""Deterministic, review-only documentation. Never edits candidate/review evidence."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path

from ml.nlu_v1.ingestion.common import REGISTRY
from ml.nlu_v1.data_management.records import DataError, facts_hash, sha_json, validate_record
from ml.nlu_v1.source_registry import ROOT
from contract import unique_keys

RUN = ROOT / 'ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002'
PRIOR = ROOT / 'ml/nlu_v1/datasets/quarantine/plan07_machine_curation_005'
PACKET = ROOT / 'docs/nlu_v1/PLAN_07_EXCEPTION_REVIEW_PACKET.md'
INDEX = ROOT / 'docs/nlu_v1/PLAN_07_EXCEPTION_CLUSTER_INDEX.json'
VERSION = 'nlu_v1_exception_packet_001'
PRIORITY = ['PROTECTED_OR_NONPUBLIC_EXCLUDED', 'MALFORMED_ANNOTATION_OR_REVIEW_BINDING',
            'PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT', 'UNEXPECTED_SECOND_ACTION',
            'TEMPORAL_CONTEXT_UNRESOLVED', 'FAMILY_OR_SCENARIO_UNRESOLVED',
            'UNRESOLVED_REFERENCE', 'WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN',
            'HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW']

# Navigation topics only. These explicit catalogs do not relabel source intents
# or certify equivalent semantics. Different intents remain separate subgroups.
CLINC_TOPICS = {
    'account_information': 'account_blocked balance routing transactions spending_history interest_rate income direct_deposit expiration_date',
    'card_and_credit_information': 'apr credit_limit credit_limit_change credit_score improve_credit_score international_fees min_payment rewards_balance',
    'card_security_and_replacement': 'card_declined damaged_card freeze_account new_card pin_change replacement_card_duration report_fraud report_lost_card',
    'financial_commands_and_billing': 'bill_balance bill_due pay_bill transfer order_checks redeem_rewards application_status',
    'employment_tax_and_benefits': 'payday pto_balance pto_request pto_request_status pto_used rollover_401k taxes w2 insurance insurance_change',
    'travel_booking': 'book_flight book_hotel cancel_reservation car_rental confirm_reservation flight_status international_visa lost_luggage carry_on',
    'travel_information': 'travel_alert travel_notification travel_suggestion plug_type',
    'restaurant_transactions': 'accept_reservations restaurant_reservation restaurant_reviews restaurant_suggestion',
    'food_and_cooking': 'calories cook_time food_last ingredient_substitution ingredients_list meal_suggestion nutrition_info recipe',
    'vehicle_and_fuel': 'gas gas_type jump_start last_maintenance mpg oil_change_how oil_change_when schedule_maintenance tire_change tire_pressure',
    'device_and_assistant_preferences': 'change_accent change_ai_name change_language change_speed change_user_name change_volume reset_settings sync_device whisper_mode',
    'assistant_identity_and_smalltalk': 'are_you_a_bot do_you_have_pets goodbye greeting how_busy how_old_are_you what_are_your_hobbies what_is_your_name where_are_you_from who_do_you_work_for who_made_you thank_you',
    'dialogue_control': 'cancel maybe no repeat yes',
    'media': 'next_song play_music update_playlist what_song',
    'location_and_transport': 'current_location directions distance find_phone share_location traffic uber',
    'information_queries': 'calculator date definition exchange_rate fun_fact meaning_of_life measurement_conversion next_holiday spelling time timezone translate vaccines weather what_can_i_ask_you',
    'lists_and_orders': 'order order_status shopping_list shopping_list_update todo_list todo_list_update',
    'personal_assistant_operations': 'alarm calendar calendar_update make_call meeting_schedule reminder reminder_update schedule_meeting text timer smart_home',
    'games_and_entertainment': 'flip_coin roll_dice tell_joke',
    'unclassified_source_oos': 'oos',
}


def file_hash(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=unique_keys)


def primary(reasons):
    if not reasons or len(reasons) != len(set(reasons)):
        raise DataError('EXCEPTION_REASONS_MISSING_OR_DUPLICATED')
    rank = {code: n for n, code in enumerate(PRIORITY)}
    return min(reasons, key=lambda code: (rank.get(code, len(rank)), code))


def topic(source, intent):
    if source == 'clinc150_full':
        matches = [name for name, values in CLINC_TOPICS.items() if intent in values.split()]
        if len(matches) > 1:
            raise DataError('AMBIGUOUS_REVIEW_TOPIC_CATALOG')
        return matches[0] if matches else 'intent:' + intent
    if source == 'massive_1_1_en_us':
        return 'source_scenario:' + intent.split('_')[0]
    # Historical template families, including their action boundaries, stay apart.
    return 'template:' + intent


def boundary(row):
    return {'domain': row['domain'], 'action': row['action'], 'disposition': row['disposition'],
            'ood': row['ood'], 'mapping_decision': row['source']['mapping_decision'],
            'composition': [{k: component[k] for k in ('domain', 'action')}
                            for component in (row.get('composition') or {}).get('requests', [])]}


def signature(row):
    return {'source': row['source']['dataset'], 'source_version': row['source']['version'],
            'source_intent': row['source']['original_intent'], 'mapper_version': row['source']['mapper_version'],
            'mapping_version': row['source']['mapping_version'],
            'mapping_configuration_sha256': row.get('provenance', {}).get('mapping_configuration_sha256'),
            'family_definition_sha256': row.get('provenance', {}).get('family_definition_sha256'), 'boundary': boundary(row),
            'family': row['family'], 'slot_pattern': [s['type'] for s in row['slots']],
            'privacy': row['privacy'], 'publication_allowed': bool((row.get('privacy_review') or {}).get('publication_allowed'))}


def display_permission(row, registry=REGISTRY):
    if row['privacy'] in ('PRIVATE', 'RESTRICTED'):
        return False, 'PRIVATE_OR_RESTRICTED_TEXT_EXCLUDED'
    if row['privacy'] != 'PUBLIC':
        return False, 'UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE'
    review = row.get('privacy_review') or {}
    source = registry.get(row['source']['dataset'], {})
    if review.get('binding_sha256') != facts_hash(row) or review.get('publication_allowed') is not True:
        return False, 'BOUND_PUBLICATION_PERMISSION_MISSING'
    if (source.get('version') != row['source']['version'] or source.get('identity_verified') is not True or
        source.get('license', {}).get('verification') != 'VERIFIED_METADATA' or
        review.get('license_id') != source.get('license', {}).get('id')):
        return False, 'SOURCE_LICENSE_DISPLAY_EVIDENCE_MISSING'
    return True, 'BOUND_PUBLIC_PRIVACY_AND_PUBLICATION_PERMISSION'


def select_examples(ids, rows, maximum=5):
    # Prefer display-cleared rows, then sample different actual slot/family/intent
    # patterns before taking a second member of a pattern. Never synthesize text.
    pools = defaultdict(list)
    for ident in ids:
        pools[(not display_permission(rows[ident])[0], sha_json(signature(rows[ident])))].append(ident)
    pools = {key: sorted(value) for key, value in pools.items()}
    chosen = []
    while len(chosen) < min(maximum, len(ids)):
        for key in sorted(pools):
            if pools[key] and len(chosen) < min(maximum, len(ids)):
                chosen.append(pools[key].pop(0))
    return chosen


def example(row, reasons, ticket_ids):
    allowed, status = display_permission(row)
    return {'row_id': row['id'], 'row_content_sha256': sha_json(row), 'text_sha256': row['text_sha256'],
            'text': row['text'] if allowed else None, 'display_status': status,
            'domain': row['domain'], 'action': row['action'], 'disposition': row['disposition'],
            'family': row['family'], 'slots': [{'type': slot['type'], 'text': slot['text'] if allowed else None,
                'start_utf16': slot['start'] if allowed else None, 'end_utf16': slot['end'] if allowed else None} for slot in row['slots']],
            'exception_reasons': sorted(reasons), 'ticket_ids': sorted(ticket_ids)}


def build_index(tickets, rows, *, input_hashes=None, historical=None):
    if len({t['exception_id'] for t in tickets}) != len(tickets):
        raise DataError('DUPLICATE_EXCEPTION_TICKET_ID')
    if len({r['id'] for r in rows}) != len(rows):
        raise DataError('DUPLICATE_CANONICAL_ROW_ID')
    indexed = {r['id']: r for r in rows}
    buckets, row_reasons, row_tickets = defaultdict(list), defaultdict(set), defaultdict(set)
    for ticket in tickets:
        if not ticket['row_ids'] or len(ticket['row_ids']) != len(set(ticket['row_ids'])) or not set(ticket['row_ids']) <= set(indexed):
            raise DataError('EXCEPTION_TICKET_ROW_REFERENCE_INVALID')
        reason = primary(ticket['reasons'])
        members = [indexed[i] for i in ticket['row_ids']]
        if any(r['source']['dataset'] != ticket['source'] or r['source']['original_intent'] != ticket['source_intent'] for r in members):
            raise DataError('EXCEPTION_TICKET_SOURCE_METADATA_CONTRADICTION')
        # Different domain/action/disposition/support boundaries never share a
        # primary workset. Existing mixed tickets remain explicitly marked mixed.
        key = {'source': ticket['source'], 'source_versions': sorted({r['source']['version'] for r in members}),
               'primary_reason': reason, 'topic': topic(ticket['source'], ticket['source_intent']),
               'boundaries': sorted({sha_json(boundary(r)): boundary(r) for r in members}.values(), key=sha_json),
               'mapping_versions': sorted({(r['source']['mapper_version'], r['source']['mapping_version']) for r in members}),
               'privacy_states': sorted({sha_json({'class': r['privacy'], 'publication_allowed': bool((r.get('privacy_review') or {}).get('publication_allowed')),
                   'license_id': (r.get('privacy_review') or {}).get('license_id'), 'display_status': display_permission(r)[1]}) for r in members}),
               'protected': bool(ticket['protected'])}
        buckets[sha_json(key)].append((key, ticket))
        for ident in ticket['row_ids']:
            row_reasons[ident].update(ticket['reasons'])
            row_tickets[ident].add(ticket['exception_id'])
    clusters, assignments = [], []
    for digest, entries in sorted(buckets.items()):
        key = entries[0][0]
        member_tickets = sorted([t for _, t in entries], key=lambda t: t['exception_id'])
        ids = sorted({ident for t in member_tickets for ident in t['row_ids']})
        cluster_id = 'EXC-' + digest[:16]
        local_tickets, local_reasons = defaultdict(set), defaultdict(set)
        for member_ticket in member_tickets:
            for ident in member_ticket['row_ids']:
                local_tickets[ident].add(member_ticket['exception_id'])
                local_reasons[ident].update(member_ticket['reasons'])
        subgroups = defaultdict(list)
        for ident in ids:
            subgroups[sha_json(signature(indexed[ident]))].append(ident)
        subunits = [{'subgroup_id': cluster_id + '-S-' + code[:12], 'signature': signature(indexed[members[0]]),
                     'row_ids': sorted(members), 'unique_row_count': len(members),
                     'ticket_ids': sorted({tid for ident in members for tid in local_tickets[ident]}),
                     'review_scope': 'INDIVIDUAL_REVIEW_REQUIRED',
                     'note': 'Metadata-compatible candidates; wording/privacy/template exceptions remain unproven. This is not a certified bulk approval.'}
                    for code, members in sorted(subgroups.items())]
        scope = 'SUBGROUP_REQUIRED' if len(subunits) > 1 else 'INDIVIDUAL_REVIEW_REQUIRED'
        secondary = sorted({code for t in member_tickets for code in t['reasons'] if code != key['primary_reason']})
        selected = select_examples(ids, indexed)
        exemplars = [example(indexed[ident], local_reasons[ident], local_tickets[ident]) for ident in selected]
        family_patterns = Counter(indexed[i]['family'] or 'UNRESOLVED' for i in ids)
        slot_patterns = Counter(tuple(slot['type'] for slot in indexed[i]['slots']) for i in ids)
        cluster = {'cluster_id': cluster_id, 'primary_reason': key['primary_reason'], 'secondary_reasons': secondary,
            'navigation_key': key, 'ticket_ids': [t['exception_id'] for t in member_tickets], 'row_ids': ids,
            'ticket_content_sha256': {t['exception_id']: sha_json(t) for t in member_tickets},
            'row_content_sha256': {ident: sha_json(indexed[ident]) for ident in ids},
            'unique_ticket_count': len(member_tickets), 'unique_row_count': len(ids),
            'ticket_reason_occurrence_count': sum(len(t['reasons']) for t in member_tickets),
            'row_reason_occurrence_count': len({(i, code) for t in member_tickets for i in t['row_ids'] for code in t['reasons']}),
            'source_datasets': sorted({indexed[i]['source']['dataset'] for i in ids}),
            'source_intents': sorted({indexed[i]['source']['original_intent'] for i in ids}),
            'semantic_patterns': key['boundaries'], 'family_patterns': dict(sorted(family_patterns.items())),
            'slot_patterns': [{'types': list(types), 'rows': count} for types, count in sorted(slot_patterns.items())],
            'semantically_homogeneous': False, 'metadata_homogeneous': len(subunits) == 1,
            'homogeneity_note': 'Current exceptions are not certified semantically equivalent. A common label or reason is not proof.',
            'recommended_review_scope': scope, 'bulk_decision_enabled': False,
            'subgroups': subunits, 'examples': exemplars,
            'display_cleared_rows': sum(display_permission(indexed[i])[0] for i in ids),
            'text_excluded_rows': sum(not display_permission(indexed[i])[0] for i in ids),
            'decision': {'decision': 'PENDING', 'decision_target_id': None, 'corrected_domain': None,
                'corrected_action': None, 'corrected_disposition': None, 'corrected_family': None,
                'slot_correction_note': None, 'slot_edits': [], 'reviewer_note': None}}
        cluster['content_sha256'] = sha_json(cluster)
        clusters.append(cluster)
        for ticket in member_tickets:
            assignments.append({'ticket_id': ticket['exception_id'], 'cluster_id': cluster_id,
                'ticket_content_sha256': sha_json(ticket), 'primary_reason': primary(ticket['reasons']),
                'secondary_reasons': sorted(set(ticket['reasons']) - {primary(ticket['reasons'])})})
    if len({c['cluster_id'] for c in clusters}) != len(clusters):
        raise DataError('EXCEPTION_CLUSTER_ID_COLLISION')
    reason_stats = {}
    for reason in sorted({code for t in tickets for code in t['reasons']}):
        affected = [t for t in tickets if reason in t['reasons']]
        reason_stats[reason] = {'unique_tickets': len(affected),
            'unique_rows': len({ident for t in affected for ident in t['row_ids']}),
            'row_reference_occurrences': sum(len(t['row_ids']) for t in affected)}
    summary = {'unique_tickets': len(tickets), 'row_references': sum(len(t['row_ids']) for t in tickets),
        'unique_rows': len(row_reasons), 'ticket_reason_occurrences': sum(len(t['reasons']) for t in tickets),
        'unique_row_reason_occurrences': sum(len(codes) for codes in row_reasons.values()),
        'primary_clusters': len(clusters), 'scope_counts': {scope: sum(c['recommended_review_scope'] == scope for c in clusters)
            for scope in ('GROUP_DECISION_SAFE', 'SUBGROUP_REQUIRED', 'INDIVIDUAL_REVIEW_REQUIRED')},
        'all_tickets_primary_assigned_exactly_once': len(assignments) == len(tickets) == len({a['ticket_id'] for a in assignments}),
        'display_cleared_rows': sum(display_permission(indexed[i])[0] for i in row_reasons),
        'text_excluded_rows': sum(not display_permission(indexed[i])[0] for i in row_reasons),
        'private_restricted_rows': sum(indexed[i]['privacy'] in ('PRIVATE', 'RESTRICTED') for i in row_reasons),
        'unknown_privacy_rows': sum(indexed[i]['privacy'] == 'UNKNOWN_PRIVACY' for i in row_reasons),
        'approval_events_created': 0}
    inventory = {name: dict(sorted(Counter(str(getter(indexed[i])) for i in row_reasons).items())) for name, getter in {
        'source_intents': lambda r: r['source']['dataset'] + ':' + r['source']['original_intent'],
        'domains': lambda r: r['domain'], 'actions': lambda r: r['action'],
        'dispositions': lambda r: r['disposition'], 'families': lambda r: r['family'] or 'UNRESOLVED'}.items()}
    return {'index_version': VERSION, 'review_only': True, 'input_sha256': input_hashes or {},
        'generator_sha256': file_hash(__file__), 'summary': summary, 'reason_counts': reason_stats,
        'inventory_unique_rows': inventory, 'multi_reason_tickets': sorted([t['exception_id'] for t in tickets if len(t['reasons']) > 1]),
        'rows_in_multiple_reason_categories': {ident: sorted(codes) for ident, codes in sorted(row_reasons.items()) if len(codes) > 1},
        'rows_referenced_by_multiple_tickets': {ident: sorted(tids) for ident, tids in sorted(row_tickets.items()) if len(tids) > 1},
        'historical_curation_counts_not_current_clusters': historical,
        'ticket_primary_assignments': sorted(assignments, key=lambda a: a['ticket_id']), 'clusters': sorted(clusters, key=lambda c: c['cluster_id'])}


def cell(value):
    return str(value).replace('\\', '\\\\').replace('|', '\\|').replace('\r', ' ').replace('\n', '<br>').replace('<', '&lt;').replace('>', '&gt;').replace('`', '&#96;')


def render(index):
    s = index['summary']
    lines = ['# METIS NLU v1 Plan 07 Exception Review Packet', '',
        'Derived, review-only documentation of `plan07-semantic-groups-002`. Original tickets, rows, semantic groups, approvals and schemas are unchanged.', '',
        f"**Unique tickets: {s['unique_tickets']}. Unique affected rows: {s['unique_rows']}. Row references: {s['row_references']}. Disjoint primary clusters: {s['primary_clusters']}.**", '',
        f"Ticket–reason occurrences: **{s['ticket_reason_occurrences']}**. Unique row–reason occurrences: **{s['unique_row_reason_occurrences']}**. These are separate metrics, not extra tickets.", '',
        f"Multi-reason tickets: **{len(index['multi_reason_tickets'])}**. Rows in multiple current reason categories: **{len(index['rows_in_multiple_reason_categories'])}**. Rows referenced by multiple tickets: **{len(index['rows_referenced_by_multiple_tickets'])}**.", '',
        '## Interpretation and decision safeguards', '',
        'Every ticket has exactly one primary cluster. Primary-reason precedence is protection/malformed records, privacy/ambiguous slots, second actions, temporal ambiguity, family/reference uncertainty, wording/support, then historical templates; ties use lexical order. Other current codes remain secondary tags. Older curation reasons are not merged into current exception categories.', '',
        'Clusters are navigation worksets, **not blanket approval units**. Topics are explicit source-intent catalogs, further separated by source/version, primary reason, mapping versions, domain/action/disposition and support boundary. Exact intent/family/slot-pattern subgroups remain separate in the index. Mixed original tickets are retained once and subdivided by row for navigation. No incompatible meanings are certified equivalent.', '',
        'No cluster is marked GROUP_DECISION_SAFE without proof of semantic equivalence. SUBGROUP_REQUIRED means inspect the listed subgroup signatures; those subgroups still require individual decisions where their wording/privacy/template exception remains unresolved. INDIVIDUAL_REVIEW_REQUIRED forbids applying one header decision to every row. A smaller navigation packet does not erase the unresolved human workload.', '',
        '**APPROVE / REJECT / CHANGE are draft review findings only.** They do not approve a row, release quarantine, waive privacy/license/duplicate gates or replace the external signed approval workflow. All decision fields below remain PENDING.', '',
        '## Text-display policy', '',
        'This packet and index live in versionable documentation. Dataset licensing alone does not clear privacy. [Plan 04 policy](../../ml/nlu_v1/data_management/README.md) states that UNKNOWN_PRIVACY blocks public export; [.gitignore](../../.gitignore) keeps working text local pending reviewed export. The unrelated human-review Markdown is not evidence of display permission.', '',
        f"Text excluded for **{s['text_excluded_rows']}** affected rows; **{s['display_cleared_rows']}** rows have bound PUBLIC privacy, explicit publication permission and matching verified source/license evidence. Private/restricted rows: {s['private_restricted_rows']}; UNKNOWN_PRIVACY rows: {s['unknown_privacy_rows']}.", '',
        'Where permission is absent, representative records show safe labels and opaque provenance references, with utterance and slot text withheld. They are **not invented/redacted paraphrases**. A reviewer must obtain permitted access in the controlled local data workflow before deciding semantics. Hashes are provenance references, not anonymization. For clusters with fewer than three actual members, every available member is shown; no examples are fabricated.', '',
        '## Current reason counts', '', '| Reason | Unique tickets | Unique affected rows | Row-reference occurrences |', '|---|---:|---:|---:|']
    for code, values in index['reason_counts'].items():
        lines.append(f"| {code} | {values['unique_tickets']} | {values['unique_rows']} | {values['row_reference_occurrences']} |")
    if index['historical_curation_counts_not_current_clusters']:
        h = index['historical_curation_counts_not_current_clusters']
        lines += ['', f"The older machine-curation queue had {h['tickets']} tickets / {h['unique_rows']} rows, {h['row_reason_occurrences']} row–reason occurrences and {h['rows_with_multiple_reasons']} multi-reason rows. Its affected-row counts below overlap; they are not this disjoint ticket partition.", '',
                  '| Older curation reason (not a current exception cluster) | Affected rows, overlapping |', '|---|---:|']
        lines += [f'| {code} | {count} |' for code, count in h['reason_affected_rows'].items()]
    lines += ['', '## Primary cluster index', '', '| Cluster | Tickets | Rows | Topic | Primary reason | Required scope |', '|---|---:|---:|---|---|---|']
    for c in index['clusters']:
        lines.append(f"| [{c['cluster_id']}](#{c['cluster_id'].lower()}) | {c['unique_ticket_count']} | {c['unique_row_count']} | {cell(c['navigation_key']['topic'])} | {c['primary_reason']} | {c['recommended_review_scope']} |")
    for c in index['clusters']:
        lines += ['', f"## {c['cluster_id']}", '',
            f"- Unique tickets: **{c['unique_ticket_count']}**; unique rows: **{c['unique_row_count']}**; ticket–reason occurrences: {c['ticket_reason_occurrence_count']}; row–reason occurrences: {c['row_reason_occurrence_count']}.",
            '- Sources: ' + ', '.join(c['source_datasets']) + '.',
            '- Source intents: ' + ', '.join(c['source_intents']) + '.',
            '- Current domain / action / disposition / OOD patterns: ' + '; '.join('/'.join(str(p[k]) for k in ('domain', 'action', 'disposition', 'ood')) for p in c['semantic_patterns']) + '.',
            '- Families (unique rows): ' + '; '.join(f'{name}={count}' for name, count in c['family_patterns'].items()) + '.',
            '- Slot patterns (unique rows): ' + '; '.join((' + '.join(p['types']) or 'NO_SLOTS') + '=' + str(p['rows']) for p in c['slot_patterns']) + '.',
            f"- Primary reason: `{c['primary_reason']}`. Secondary current reasons: " + (', '.join(c['secondary_reasons']) or 'none') + '.',
            f"- Semantically homogeneous: **not established**. Metadata homogeneous: {str(c['metadata_homogeneous']).lower()}. Required scope: **{c['recommended_review_scope']}**.",
            f"- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `{c['cluster_id']}`. Content binding: `{c['content_sha256']}`.",
            f"- Decision subgroups: {len(c['subgroups'])}; blanket cluster decisions disabled. " + ' '.join(f"`{sub['subgroup_id']}` ({sub['unique_row_count']} rows)" for sub in c['subgroups']) + '.', '',
            '### Representative records', '',
            f"{len(c['examples'])} actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: {c['display_cleared_rows']}; text-excluded members: {c['text_excluded_rows']}.", '',
            '| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |',
            '|---|---|---|---|---|---|---|---|']
        for e in c['examples']:
            utterance = e['text'] if e['text'] is not None else '[WITHHELD: ' + e['display_status'] + ']'
            slots = '; '.join(slot['type'] + ' = ' + (slot['text'] if slot['text'] is not None else '[WITHHELD]') for slot in e['slots']) or 'none'
            lines.append('| ' + ' | '.join(cell(x) for x in (e['row_id'], utterance, e['domain'], e['action'], e['disposition'], e['family'] or 'UNRESOLVED', slots, ', '.join(e['exception_reasons']))) + ' |')
        lines += ['', '### Decision draft', '',
            '`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.',
            'Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.', '',
            '```yaml', 'decision: PENDING', 'decision_target_id: null', 'corrected_domain: null', 'corrected_action: null',
            'corrected_disposition: null', 'corrected_family: null', 'slot_correction_note: null', 'slot_edits: []',
            '# Slot edit shape (draft only):', '# - operation: ADD | REMOVE | UPDATE', '#   original_slot_index: null',
            '#   corrected_type: null', '#   corrected_start_utf16: null', '#   corrected_end_utf16: null',
            '#   corrected_text: null', 'reviewer_note: null', '```', '',
            'Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.']
    lines += ['', '## Integrity and regeneration', '',
        '`python -m ml.nlu_v1.dataset_v0.exception_packet --write` regenerates only this derived Markdown and its JSON index. `--check` verifies deterministic regeneration and unchanged inputs. The index binds exact ticket IDs, row IDs, content hashes, representatives and primary assignments.', '',
        'Inputs (SHA-256):']
    lines += ['- `' + name + '`: `' + digest + '`.' for name, digest in sorted(index['input_sha256'].items())]
    return '\n'.join(lines) + '\n'


def load_inputs(run=RUN, prior=PRIOR):
    run, prior = Path(run), Path(prior)
    manifest = read_json(run / 'group_review_manifest.json')
    inputs = {str((run / 'group_review_manifest.json').relative_to(ROOT)).replace('\\', '/'): file_hash(run / 'group_review_manifest.json')}
    if manifest.get('status') != 'GROUP_REVIEW_PENDING_HUMAN' or manifest.get('training_authorized') is not False:
        raise DataError('EXCEPTION_PACKET_REQUIRES_PENDING_REVIEW_RUN')
    for name, digest in manifest['files'].items():
        path = run / name
        if run.resolve() not in path.resolve().parents or file_hash(path) != digest:
            raise DataError('EXCEPTION_PACKET_SOURCE_ARTIFACT_CHANGED')
        inputs[path.relative_to(ROOT).as_posix()] = digest
    tickets = read_json(run / 'exception_review_tickets.json')
    rows = [json.loads(line, object_pairs_hook=unique_keys) for line in (run / 'quarantined.jsonl').read_bytes().splitlines()]
    for row in rows:
        validate_record(row)
    summary = read_json(run / 'semantic_review_summary.json')
    groups = read_json(run / 'semantic_review_groups.json')
    exception_rows = {ident for t in tickets for ident in t['row_ids']}
    grouped_rows = [ident for group in groups for ident in group['row_ids']]
    if (len(tickets) != summary['exception_tickets'] or len(exception_rows) != summary['individual_review_rows'] or
        len(groups) != summary['grouped_tickets'] or exception_rows & set(grouped_rows) or
        exception_rows | set(grouped_rows) != {r['id'] for r in rows}):
        raise DataError('EXCEPTION_PACKET_SOURCE_COUNTS_INCONSISTENT')
    archived = None
    if prior.exists():
        prior_manifest = read_json(prior / 'curation_manifest.json')
        path = prior / 'manual_review_queue.jsonl'
        if file_hash(path) != prior_manifest['files']['manual_review_queue.jsonl']:
            raise DataError('ARCHIVED_CURATION_QUEUE_CHANGED')
        queue = [json.loads(line, object_pairs_hook=unique_keys) for line in path.read_bytes().splitlines()]
        by_row = defaultdict(set)
        for ticket in queue:
            for ident in ticket['row_ids']:
                by_row[ident].update(ticket['reasons'])
        archived = {'tickets': len(queue), 'unique_rows': len(by_row), 'row_reason_occurrences': sum(len(v) for v in by_row.values()),
                    'rows_with_multiple_reasons': sum(len(v) > 1 for v in by_row.values()),
                    'reason_affected_rows': dict(sorted(Counter(code for codes in by_row.values() for code in codes).items()))}
        old_report = read_json(prior / 'curation_report.json')
        if old_report['manual_queue_entries'] != len(queue) or old_report['manual_edge_case_rows'] != len(by_row) or old_report['manual_reason_counts'] != archived['reason_affected_rows']:
            raise DataError('ARCHIVED_CURATION_REASON_COUNTS_INCONSISTENT')
        for path in (prior / 'curation_manifest.json', prior / 'manual_review_queue.jsonl', prior / 'curation_report.json'):
            inputs[path.relative_to(ROOT).as_posix()] = file_hash(path)
    for path in (ROOT / 'ml/nlu_v1/configs/dataset_sources.yaml', ROOT / 'ml/nlu_v1/data_management/README.md', ROOT / '.gitignore'):
        inputs[path.relative_to(ROOT).as_posix()] = file_hash(path)
    return tickets, rows, inputs, archived


def generate(*, write=False):
    tickets, rows, hashes, archived = load_inputs()
    index = build_index(tickets, rows, input_hashes=hashes, historical=archived)
    text = render(index)
    index_bytes = (json.dumps(index, indent=2, ensure_ascii=False, sort_keys=True, allow_nan=False) + '\n').encode('utf-8')
    if any(file_hash(ROOT / name) != digest for name, digest in hashes.items()):
        raise DataError('EXCEPTION_PACKET_INPUT_CHANGED_DURING_GENERATION')
    if write:
        # Fixed derived documentation destinations only; no approval/data writer.
        PACKET.write_text(text, encoding='utf-8', newline='\n')
        INDEX.write_bytes(index_bytes)
    elif not PACKET.exists() or PACKET.read_bytes() != text.encode() or not INDEX.exists() or INDEX.read_bytes() != index_bytes:
        raise DataError('DERIVED_EXCEPTION_PACKET_NOT_CURRENT')
    if any(file_hash(ROOT / name) != digest for name, digest in hashes.items()):
        raise DataError('EXCEPTION_PACKET_SOURCE_WAS_MODIFIED')
    return index['summary']


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    choice = parser.add_mutually_exclusive_group(required=True)
    choice.add_argument('--write', action='store_true')
    choice.add_argument('--check', action='store_true')
    args = parser.parse_args()
    print(json.dumps(generate(write=args.write), sort_keys=True))
