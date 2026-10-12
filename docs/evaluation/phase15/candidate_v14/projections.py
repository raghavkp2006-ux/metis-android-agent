"""Separate runtime regression expectations and unapproved semantic review proposals."""
import base64
import json
import sys
from data import HERE
from candidate import resolve

sys.path.insert(0, str(HERE.parent / 'candidate_v12'))
from v12_data import aggregate  # noqa: E402


def rows():
    retained = aggregate()
    development = json.loads((HERE / 'development.json').read_text(encoding='utf-8'))['rows']
    human = json.loads((HERE.parent / 'human_development_004/development.json').read_text(encoding='utf-8'))['rows']
    classifier = json.loads((HERE / 'classifier-development.json').read_text(encoding='utf-8'))['rows']
    result = []
    for section, inputs in (('retained', retained), ('v14', development), ('human004', human),
                            ('classifier14', classifier)):
        for row in inputs:
            plan = resolve(row['utterance'], row.get('input_source', 'TEXT'))
            # New positives have independent authored entity/query gold, not parser expectations.
            if section == 'v14':
                assert bool(plan['reads']) == (row['disposition'] == 'read'), row['id']
                if plan['reads']:
                    assert plan['entities'] == row['entities'] and plan['query_text'] == row['query_text'], row['id']
            if section == 'retained':
                if row['disposition'] == 'read' and row['section'] != 'v11':
                    assert plan['reads'] and plan['entities'] == row['entities'] and plan['query_text'] == row['query_text'], row['id']
                if row['disposition'] != 'read' and row['intent'] != 'CHECK_CALENDAR':
                    assert not plan['reads'], 'Earlier prohibited request admitted: ' + row['id']
            result.append(dict(id=row['id'], utterance=row['utterance'], section=section,
                               legacy_section=row.get('section', ''), source=row.get('input_source', 'TEXT'),
                               original_disposition=row.get('disposition'), parsed=plan))
    assert len({row['id'] for row in result}) == len(result)
    return result


def review():
    original = json.loads((HERE.parent / 'human_development_004/development.json').read_text(encoding='utf-8'))['rows']
    reviewed = []
    for row in original:
        entry = dict(row)
        label = row['intent_draft']
        ordinal = row['source_ordinal']
        # User-confirmed conventions clarify meaning without approving all row annotations.
        if row['source'] == 'attachment' and ordinal == 8:
            label = 'CHECK_TASKS'
        if row['source'] == 'attachment' and ordinal == 17:
            label = 'CHECK_CALENDAR'
        future = ('would ', "i'd ", 'i’d ', "i'll ", 'i’ll ', 'will ')
        text = row['utterance'].casefold()
        if any(token in text for token in future) and any(token in text for token in
            ('did i say', 'have i said', 'have i mentioned', 'i said i', 'did i mention i was going')):
            label = 'WHAT_DID_I_PROMISE'
        plan = resolve(row['utterance'])
        confirmed_scope = {8: 'existing_task_lookup', 14: 'new_advice', 17: 'saved_event_lookup',
                           34: 'actual_scheduled_reminder_lookup', 51: 'cross_store_search'}
        if row['source'] == 'attachment' and ordinal in confirmed_scope:
            entry['user_confirmed_scope'] = confirmed_scope[ordinal]
        entry.update(intent_draft=label, annotation_status='assistant_draft_with_user_clarified_conventions',
                     runtime_disposition_draft=plan['disposition'], runtime_entities_draft=plan['entities'],
                     runtime_query_draft=plan['query_text'], human_approved=False,
                     semantic_entities_and_queries_review_pending=True)
        reviewed.append(entry)
    (HERE / 'human-review-proposal.json').write_text(json.dumps(dict(
        all_rows_human_approved=False, fitting_eligible=False, rows=reviewed), ensure_ascii=False, indent=2) + '\n',
        encoding='utf-8', newline='\n')
    lines = ['# V14 review proposal for the 214 exposed requests', '',
             'Six user-confirmed meaning clarifications are applied. All row labels remain drafts; '
             'runtime expectations describe supported capability, not semantic accuracy. '
             'Every request is separate. Correct the intended lookup type and any missing constraints. '
             'No row enters training or calibration.', '',
             '| ID | Request | Draft meaning | Supported disposition | Literal query (if admitted) |',
             '| --- | --- | --- | --- | --- |']
    for row in reviewed:
        cells = [row['id'], row['utterance'], row['intent_draft'], row['runtime_disposition_draft'], row['runtime_query_draft']]
        lines.append('| ' + ' | '.join(cell.replace('|', '\\|') for cell in cells) + ' |')
    (HERE / 'user-review.md').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')


def build():
    inputs = rows()
    (HERE / 'regression.json').write_text(json.dumps(inputs, ensure_ascii=False, indent=2) + '\n',
                                         encoding='utf-8', newline='\n')
    lines = ['id\ttext_base64\tsource\tintent\treads\tquery_base64\tentities_base64']
    encode = lambda value: base64.b64encode(value.encode()).decode()
    for row in inputs:
        plan = row['parsed']
        entities = ';'.join(':'.join((entity['type'], str(entity['startOffset']), str(entity['endOffset']),
                                       encode(entity['rawValue']))) for entity in plan['entities'])
        lines.append('\t'.join((row['id'], encode(row['utterance']), row['source'], plan['intent'],
                                str(plan['reads']), encode(plan['query_text']), encode(entities))))
    (HERE / 'regression.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    review()
    print(f'Projected {len(inputs)} native/Python regressions; human004 reads: ' +
          str(sum(row['parsed']['reads'] for row in inputs if row['section'] == 'human004')))


if __name__ == '__main__':
    build()
