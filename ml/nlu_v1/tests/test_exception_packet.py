"""Review documentation tests; invented privacy grants never approve real rows."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

from test_data_management import managed
from ml.nlu_v1.dataset_v0 import exception_packet as packet
from ml.nlu_v1.data_management.records import DataError, classify_privacy, facts_hash, sha_json


def row(key='1', text='fixture source utterance', intent='alarm_set'):
    item = managed('fixture-' + key, text=text, annotation=text)
    item['source']['original_intent'] = intent
    return item


def ticket(name, members, reasons=None):
    return {'exception_id': name, 'source': members[0]['source']['dataset'],
            'source_intent': members[0]['source']['original_intent'],
            'row_ids': [r['id'] for r in members],
            'reasons': reasons or ['WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN'],
            'protected': False, 'individual_decisions_required': True}


class ExceptionPacketTests(unittest.TestCase):
    def test_every_ticket_has_one_primary_cluster(self):
        rows = [row(str(i)) for i in range(8)]
        tickets = [ticket('fixture-' + str(i), [r]) for i, r in enumerate(rows)]
        index = packet.build_index(tickets, rows)
        assigned = [a['ticket_id'] for a in index['ticket_primary_assignments']]
        self.assertEqual(sorted(assigned), sorted(t['exception_id'] for t in tickets))
        self.assertEqual(len(assigned), len(set(assigned)))
        self.assertTrue(index['summary']['all_tickets_primary_assigned_exactly_once'])
        self.assertEqual(sum(c['unique_ticket_count'] for c in index['clusters']), 8)

    def test_duplicate_ticket_ids_fail_closed(self):
        item = row()
        t = ticket('same', [item])
        with self.assertRaisesRegex(DataError, 'DUPLICATE_EXCEPTION_TICKET'):
            packet.build_index([t, t], [item])

    def test_missing_row_is_rejected(self):
        with self.assertRaisesRegex(DataError, 'ROW_REFERENCE_INVALID'):
            packet.build_index([ticket('missing', [row()])], [])

    def test_unique_rows_and_reason_occurrences_are_distinct(self):
        item = row()
        one = ticket('one', [item], ['TEMPORAL_CONTEXT_UNRESOLVED', 'WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN'])
        two = ticket('two', [item], ['WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN'])
        index = packet.build_index([one, two], [item])
        counts = index['summary']
        self.assertEqual(counts['unique_tickets'], 2)
        self.assertEqual(counts['row_references'], 2)
        self.assertEqual(counts['unique_rows'], 1)
        self.assertEqual(counts['ticket_reason_occurrences'], 3)
        self.assertEqual(counts['unique_row_reason_occurrences'], 2)
        self.assertEqual(index['multi_reason_tickets'], ['one'])
        self.assertEqual(index['rows_in_multiple_reason_categories'][item['id']], sorted(one['reasons']))
        self.assertEqual(index['reason_counts']['WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN']['unique_tickets'], 2)
        self.assertEqual(index['reason_counts']['WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN']['unique_rows'], 1)

    def test_primary_reason_is_deterministic_and_secondary_retained(self):
        item = row()
        reasons = ['WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN', 'PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT']
        index = packet.build_index([ticket('fixture', [item], reasons)], [item])
        assigned = index['ticket_primary_assignments'][0]
        self.assertEqual(assigned['primary_reason'], 'PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT')
        self.assertEqual(assigned['secondary_reasons'], ['WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN'])
        self.assertEqual(packet.primary(reasons), packet.primary(list(reversed(reasons))))

    def test_representatives_belong_to_cluster_and_are_not_fabricated(self):
        rows = [row(str(i), text='fixture utterance ' + str(i)) for i in range(9)]
        index = packet.build_index([ticket('fixture', rows)], rows)
        cluster = index['clusters'][0]
        self.assertEqual(len(cluster['examples']), 5)
        self.assertTrue({e['row_id'] for e in cluster['examples']} <= set(cluster['row_ids']))
        indexed = {r['id']: r for r in rows}
        for example in cluster['examples']:
            self.assertEqual(example['row_content_sha256'], sha_json(indexed[example['row_id']]))

    def test_small_cluster_does_not_invent_three_examples(self):
        item = row()
        cluster = packet.build_index([ticket('fixture', [item])], [item])['clusters'][0]
        self.assertEqual(len(cluster['examples']), 1)

    def test_private_restricted_and_unknown_text_and_slot_values_are_excluded(self):
        for privacy in ('PRIVATE', 'RESTRICTED', 'UNKNOWN_PRIVACY'):
            secret = 'SENSITIVE_FIXTURE_SENTINEL_' + privacy
            item = row(text=secret)
            item['privacy'] = privacy
            item['slots'] = [{'type': 'PERSON', 'text': secret, 'start': 0, 'end': len(secret)}]
            index = packet.build_index([ticket('fixture', [item])], [item])
            self.assertNotIn(secret, json.dumps(index))
            self.assertNotIn(secret, packet.render(index))
            self.assertIsNone(index['clusters'][0]['examples'][0]['text'])
            self.assertEqual(index['summary']['text_excluded_rows'], 1)

    def test_bound_public_example_displays_exact_text_and_slots(self):
        item = managed('fixture-public', text='set alarm at seven am', annotation='set alarm at [time : seven am]')
        proposal = copy.deepcopy(item)
        proposal['privacy'] = 'PUBLIC'
        evidence = {'binding_sha256': facts_hash(proposal), 'review_id': 'fixture-privacy',
                    'reviewer_id': 'fixture-external-human', 'reviewed_at': '2026-01-01T00:00:00Z',
                    'publication_allowed': True, 'license_id': 'CC-BY-4.0'}
        item = classify_privacy(item, 'PUBLIC', evidence)
        index = packet.build_index([ticket('fixture', [item])], [item])
        example = index['clusters'][0]['examples'][0]
        self.assertEqual(example['text'], item['text'])
        self.assertEqual(example['slots'][0]['text'], 'seven am')
        self.assertIn('TIME = seven am', packet.render(index))
        item['privacy_review']['binding_sha256'] = '0' * 64
        self.assertFalse(packet.display_permission(item)[0])

    def test_different_actions_support_boundaries_and_sources_never_share_cluster(self):
        left, right = row('left'), row('right')
        right['action'] = 'DELETE'
        index = packet.build_index([ticket('a', [left]), ticket('b', [right])], [left, right])
        self.assertEqual(len(index['clusters']), 2)
        right = row('right')
        right['disposition'] = 'KNOWN_UNSUPPORTED'
        right['source']['mapping_decision'] = 'KNOWN_UNSUPPORTED'
        index = packet.build_index([ticket('a', [left]), ticket('b', [right])], [left, right])
        self.assertEqual(len(index['clusters']), 2)

    def test_incompatible_intents_have_separate_subgroups_and_no_bulk_decision(self):
        left, right = row('left', intent='calendar_query'), row('right', intent='calendar_set')
        # Identical proposed labels are insufficient to establish identical meaning.
        index = packet.build_index([ticket('a', [left]), ticket('b', [right])], [left, right])
        self.assertEqual(len(index['clusters']), 1)
        cluster = index['clusters'][0]
        self.assertEqual(len(cluster['subgroups']), 2)
        self.assertEqual(cluster['recommended_review_scope'], 'SUBGROUP_REQUIRED')
        self.assertFalse(cluster['bulk_decision_enabled'])
        self.assertFalse(cluster['semantically_homogeneous'])
        self.assertTrue(all(s['review_scope'] == 'INDIVIDUAL_REVIEW_REQUIRED' for s in cluster['subgroups']))

    def test_families_and_slot_patterns_are_separate_decision_subgroups(self):
        first, second = row('a'), row('b')
        first['family'] = 'alarm_set'
        second['family'] = 'other-fixture-family'
        second['slots'] = [{'type': 'REFERENCE', 'text': 'it', 'start': 0, 'end': 2}]
        cluster = packet.build_index([ticket('one-mixed-ticket', [first, second])], [first, second])['clusters'][0]
        self.assertEqual(cluster['unique_ticket_count'], 1)
        self.assertEqual(len(cluster['subgroups']), 2)
        self.assertEqual(cluster['recommended_review_scope'], 'SUBGROUP_REQUIRED')

    def test_generation_is_deterministic_and_original_ticket_bytes_unchanged(self):
        rows = [row(str(i)) for i in range(3)]
        tickets = [ticket(str(i), [r]) for i, r in enumerate(rows)]
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'original-ticket.json'
            path.write_text(json.dumps(tickets, indent=2), encoding='utf-8')
            before = path.read_bytes()
            a = packet.build_index(json.loads(before), rows)
            b = packet.build_index(list(reversed(json.loads(before))), list(reversed(rows)))
            self.assertEqual(a, b)
            self.assertEqual(packet.render(a), packet.render(b))
            self.assertEqual(path.read_bytes(), before)
            self.assertTrue(all(c['decision']['decision'] == 'PENDING' for c in a['clusters']))
            self.assertEqual(a['summary']['approval_events_created'], 0)

    @unittest.skipUnless((packet.RUN / 'exception_review_tickets.json').exists() and packet.INDEX.exists(), 'Local ignored Plan 07 artifacts unavailable')
    def test_real_239_tickets_covered_once_with_correct_unique_counts(self):
        before = (packet.RUN / 'exception_review_tickets.json').read_bytes()
        original = json.loads(before)
        index = json.loads(packet.INDEX.read_text(encoding='utf-8'))
        assigned = [a['ticket_id'] for a in index['ticket_primary_assignments']]
        self.assertEqual(len(assigned), 239)
        self.assertEqual(len(set(assigned)), 239)
        self.assertEqual(set(assigned), {t['exception_id'] for t in original})
        self.assertEqual(index['summary']['unique_rows'], 2029)
        self.assertEqual(len({i for c in index['clusters'] for i in c['row_ids']}), 2029)
        self.assertEqual(index['summary']['private_restricted_rows'], 0)
        for cluster in index['clusters']:
            self.assertTrue({e['row_id'] for e in cluster['examples']} <= set(cluster['row_ids']))
            self.assertTrue(all(e['text'] is None for e in cluster['examples']))
        self.assertEqual((packet.RUN / 'exception_review_tickets.json').read_bytes(), before)
