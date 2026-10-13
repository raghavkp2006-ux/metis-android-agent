"""Separate, unapproved annotation projection. Never modifies historical intakes."""
import hashlib
import json
import re
from pathlib import Path
from author import normalize
from v15_candidate import resolve

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SOURCES = ('candidate_v12/user-development.json', 'human_development_004/development.json',
           'human_intake_005/intake.json')
TOPICS = ('seminar', 'book', 'group project', 'backend', 'registration', 'payment', 'aisha',
          'report', 'github', 'hosting', 'electronics', 'neha', 'internship', 'portfolio')


def semantic_draft(text, earlier):
    if earlier:
        return earlier
    lower = text.lower()
    if re.search(r'\b(promise|commitment|reimburse)\b|did i (?:tell|say).*would', lower):
        return 'WHAT_DID_I_PROMISE'
    if re.search(r'\b(calendar|meeting|schedule|event date)\b', lower):
        return 'CHECK_CALENDAR'
    if re.search(r'\b(task|tasks|deadline|completed|pending)\b', lower):
        return 'CHECK_TASKS'
    if re.search(r'\b(mention|save|saved|remember|record|stored|note|say|said)\b', lower):
        return 'CHECK_MEMORY'
    return 'UNKNOWN'


def group_draft(source, ordinal, text):
    if source.startswith('human_intake_005'):
        for end, topic in ((11, 'seminar'), (21, 'book-return'), (37, 'group-project'),
                           (61, 'registration-payment'), (71, 'aisha-call'), (101, 'report')):
            if ordinal <= end:
                return 'scenario-' + topic
    return next(('scenario-' + topic.replace(' ', '-') for topic in TOPICS if topic in text.lower()), None)


def build():
    decisions = json.loads((HERE / 'review-decisions.json').read_text(encoding='utf-8'))
    rows, seen, bindings = [], {}, {}
    for source in SOURCES:
        path = HERE.parent / source
        bindings[source] = hashlib.sha256(path.read_bytes()).hexdigest()
        for ordinal, original in enumerate(json.loads(path.read_text(encoding='utf-8'))['rows'], 1):
            text = original['utterance']
            runtime = resolve(text)
            key = normalize(text)
            duplicate = seen.get(key)
            seen.setdefault(key, original['id'])
            semantic = semantic_draft(text, original.get('intent_draft', original.get('intent')))
            unresolved = bool(re.search(r'\b(it|him|her|them|those|that thing|the thing|that task|earlier)\b', text, re.I))
            unavailable = bool(re.search(r'\b(reminder|latest|changed my mind|final|contradict|connected|same task)\b', text, re.I))
            desired = ('read' if runtime['reads'] else 'unsupported_capability' if unavailable
                       else 'clarify_reference' if unresolved else 'review_required')
            rows.append(dict(id=original['id'], source=source, source_ordinal=ordinal, utterance=text,
                             conversation_context='independent', annotation_provenance='assistant_draft',
                             intended_category_draft=semantic, entities_draft=runtime['entities'] if runtime['reads'] else None,
                             complete_subject_draft=runtime['query_text'] if runtime['reads'] else None,
                             constraints_draft=dict(verbatim_request=text, typed_scope=runtime['scope']),
                             expected_disposition_draft=desired, runtime_diagnostic=runtime,
                             missing_information_instruction=text if re.search(r'guess|missing|unavailable|clarif|invent', text, re.I) else None,
                             duplicate_of=duplicate, family_draft=group_draft(source, ordinal, text),
                             family_reviewed=False, author=None, reviewer=None,
                             reviewed_fields={key: False for key in ('category', 'entities', 'complete_subject', 'constraints', 'disposition')},
                             human_approved=False, fit_eligible=False, partition=None))
    for row in rows:
        if decisions.get('bulk_meaning_approval'):
            row.update(semantic_meaning_human_approved=True,
                       meaning_review_provenance='user_bulk_approved_draft_meaning',
                       meaning_review_confirmation=decisions['bulk_meaning_approval']['confirmation'])
            row['reviewed_fields']['category'] = True
            if row['expected_disposition_draft'] != 'review_required':
                row['reviewed_fields']['disposition'] = True
        for decision in decisions['conventions']:
            if row['utterance'] == decision['request']:
                row['intended_category_draft'] = decision['category']
                row['reviewed_fields']['category'] = True
                row['meaning_confirmation'] = decision['meaning']
                if decision.get('date_field'):
                    row['constraints_draft']['confirmed_date_field'] = decision['date_field']
                    row['constraints_draft']['confirmed_date_expression'] = decision['date_expression']
                if decision.get('capability'):
                    row['expected_disposition_draft'] = 'unsupported_capability'
        for approved in [item for key, batch in decisions.items() if key.startswith('approved_batch_') for item in batch]:
            if row['source'].startswith('human_intake_005') and row['source_ordinal'] == approved['ordinal']:
                assert row['utterance'] == approved['request'], 'Approval text drift'
                raw = approved['entity']
                start = row['utterance'].index(raw)
                row.update(intended_category_draft=approved['category'], complete_subject_draft=approved['subject'],
                           expected_disposition_draft=approved.get('disposition', 'read'), human_approved=True,
                           author='requesting user; submitted in chat', reviewer=decisions['reviewer'],
                           annotation_provenance='user_approved_development_batch',
                           entities_draft=[dict(type=approved['entity_type'], rawValue=approved['entity'],
                              startOffset=len(row['utterance'][:start].encode('utf-16-le')) // 2,
                              endOffset=len(row['utterance'][:start + len(raw)].encode('utf-16-le')) // 2)] if raw else [],
                           constraints_draft=dict(verbatim_request=row['utterance'], subject=approved['subject'],
                              status=approved['status'], person='', date=None,
                              requested_fields=approved.get('requested_fields',
                                  ['event dates'] if approved['category'] == 'CHECK_CALENDAR' else []),
                              unresolved=approved.get('unresolved', []), attribution=approved.get('attribution'),
                              notes=approved.get('notes', decisions['batch_1_constraints'])))
                row['reviewed_fields'] = {key: True for key in row['reviewed_fields']}
    assert len(rows) == 365 and len({row['id'] for row in rows}) == 365
    payload = dict(version='v15-review-projection', original_intakes_unchanged=True,
                   latest_101_development_authorization='Explicit user instruction in Phase 15 six-stage plan',
                   source_raw_sha256=bindings, human_approved=False, fitting_eligible=False,
                   semantic_meanings_approved=sum(row.get('semantic_meaning_human_approved', False) for row in rows),
                   complete_annotations_approved=sum(row['human_approved'] for row in rows),
                   annotation_rules='Semantic category, scope and capability are distinct. Grouping prevents leakage; it supplies no conversation context.',
                   rows=rows)
    (HERE / 'review-projection.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    approved_count = sum(row['human_approved'] for row in rows)
    lines = ['# V15 development annotation review', '',
             f'365 source requests: 50 earlier, 214 intake-004, 101 intake-005. All are independent. All draft semantic meanings have bulk user approval; {approved_count} complete annotations are approved. The other {365 - approved_count} retain annotation gaps. Ask only about gaps or contradictions, not the same meanings again. Family grouping remains unreviewed and all rows remain ineligible for fitting.', '',
             'Family suggestions group related scenarios for later partitioning only. They never resolve pronouns. Exact normalized duplicates are linked and must stay in one partition. Every annotation field and family requires review before fitting.', '',
             '| Source / # | Request | Draft semantic label | Draft disposition |', '| --- | --- | --- | --- |']
    for row in rows:
        lines.append(f"| {row['source'].split('/')[0]} / {row['source_ordinal']} | {row['utterance'].replace('|', ' / ')} | {row['intended_category_draft']} | {row['expected_disposition_draft']} |")
    (HERE / 'review.md').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    print(f'Review projection: {len(rows)} rows; {sum(r["human_approved"] for r in rows)} approved; {sum(bool(r["duplicate_of"]) for r in rows)} exact duplicates')


if __name__ == '__main__':
    build()
