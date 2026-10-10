"""Finish manual, pre-inference annotation proposals under the chosen scope."""
import argparse
import csv
import hashlib
import io
import json
from collections import Counter
from pathlib import Path

from prepare_review import HERE, ROOT


def finalize(output):
    if output.exists():
        raise ValueError('Refusing to overwrite a final review package')
    source = HERE/'revision_2/annotation-proposals.csv'
    with source.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream)
        fields, rows = reader.fieldnames, list(reader)
    # Constraints follow supplied meanings, not candidate predictions. Richer
    # constraints intentionally expose gaps even when the safe outcome clarifies.
    calendar = {
        1:dict(time_expression='tomorrow', time_of_day='evening', date_range={'start':'2026-10-10','end_exclusive':'2026-10-11'}),
        2:dict(topic='doctor'),
        3:dict(time_expression='thursday', topic='meetings', date_range={'start':'2026-10-15','end_exclusive':'2026-10-16'}),
        4:dict(availability='next_open_slot'),
        5:dict(time_expression='12th', date_range={'start':'2026-10-12','end_exclusive':'2026-10-13'}),
        7:dict(time_expression='saturday', date_range={'start':'2026-10-10','end_exclusive':'2026-10-11'}),
        8:dict(time_expression='today', time_of_day='afternoon', date_range={'start':'2026-10-09','end_exclusive':'2026-10-10'}),
        10:dict(time_expression='20th', topic='flight', date_range={'start':'2026-10-20','end_exclusive':'2026-10-21'}),
        11:dict(topic='meeting with the vendor'),
        13:dict(time_expression='this week', date_range={'start':'2026-10-05','end_exclusive':'2026-10-12'}),
        14:dict(time_expression='monday', topic='yoga class', date_range={'start':'2026-10-12','end_exclusive':'2026-10-13'}),
        16:dict(time_expression='next friday', date_range={'start':'2026-10-16','end_exclusive':'2026-10-17'}),
        17:dict(time_expression='15th', date_range={'start':'2026-10-15','end_exclusive':'2026-10-16'}),
        19:dict(time_expression='tuesday', date_range={'start':'2026-10-13','end_exclusive':'2026-10-14'}),
        20:dict(after_event='lunch with sanjay'),
        22:dict(time_expression='thursday', between_events='two meetings', date_range={'start':'2026-10-15','end_exclusive':'2026-10-16'}),
        24:dict(time_expression='june', unresolved_year=True)}
    changes = []
    for row in rows:
        key = row['id']
        qualifiers = json.loads(row['draft_filters'])
        if key.startswith('irs003-4-') and row['draft_intent'] == 'CHECK_CALENDAR':
            qualifiers = calendar[int(key.rsplit('-',1)[1])]
            changes.append(dict(id=key, change='Completed requested calendar constraints from original wording'))
        if key == 'irs003-1-05':
            qualifiers['topic'] = 'office ka paper'
            changes.append(dict(id=key, change='Preserved office-paper topic rather than office alone'))
        if key == 'irs003-1-13':
            qualifiers['deadline_before'] = 'friday'
            qualifiers['date_resolution'] = 'clarify_current_or_next_friday'
            changes.append(dict(id=key, change='Flagged before-Friday ambiguity on Friday reference date'))
        if key == 'irs003-2-04':
            text = row['utterance']
            raw = 'conference who worked at that AI startup'
            start = len(text[:text.index(raw)].encode('utf-16-le'))//2
            row['draft_entities'] = json.dumps([dict(type='MEMORY', rawValue=raw,
                startOffset=start, endOffset=start+len(raw.encode('utf-16-le'))//2)])
            changes.append(dict(id=key, change='Preserved conference and AI-startup contact context together'))
        if key == 'irs003-5-01':
            qualifiers['subject'] = 'call devika'
            qualifiers['negation_interpretation'] = 'clarify_existence_question_or_denial'
            changes.append(dict(id=key, change='Recorded unresolved negated-promise interpretation'))
        if key == 'irs003-add-tasks-02':
            qualifiers['aggregation'] = 'count_and_list'
            changes.append(dict(id=key, change='Preserved explicit count plus list request'))
        if key == 'irs003-add-tasks-09':
            # The user's estimate of seventeen items is not an actual limit.
            assert qualifiers == {'status':'incomplete'}
        row['draft_filters'] = json.dumps(qualifiers, ensure_ascii=False)
    for row in rows:
        assert all(not row[key] for key in ['reviewed_intent','reviewed_entities','reviewed_filters',
                                          'reviewed_disposition','reviewer','approved'])
        for span in json.loads(row['draft_entities']):
            encoded = row['utterance'].encode('utf-16-le')
            assert encoded[span['startOffset']*2:span['endOffset']*2].decode('utf-16-le') == span['rawValue']
        assert isinstance(json.loads(row['draft_filters']), dict)
    counts = Counter(r['draft_intent'] for r in rows)
    assert len(rows) == 145 and all(counts[label] >= 25 for label in
        ['CHECK_TASKS','CHECK_MEMORY','WHAT_DID_I_PROMISE','CHECK_CALENDAR','UNKNOWN'])
    output.mkdir(parents=True)
    stream = io.StringIO(newline='')
    writer = csv.DictWriter(stream, fieldnames=fields, lineterminator='\n')
    writer.writeheader(); writer.writerows(rows)
    target = output/'annotation-proposals.csv'
    target.write_text(stream.getvalue(), encoding='utf-8', newline='\n')
    lines = ['# Final pre-score annotation review', '',
        '**Pending human approval. No model inference has occurred.**', '',
        'Scope: current v5 read-only evaluation, with broader product expectations preserved separately.', '',
        'Calendar access is unavailable. Actions are outside this read classifier; supported native actions remain separate. Topic/age/subject restrictions that cannot be applied safely require clarification.', '',
        'Reference: 2026-10-09, Asia/Kolkata. Week starts Monday. Unqualified weekdays mean the next occurrence on or after the reference date; next Friday is 2026-10-16. Day-of-month references use the current reference month. Next two weeks is [2026-10-09, 2026-10-23). June has no inferred year. No conversation/repository identity fixture is supplied.', '',
        'Extended filter keys preserve intended constraints even where v5 lacks extraction support. These can cause exact-filter failures. They do not authorize broader reads or future product actions. Original expectations below are retained as product requirements, separately from current safe dispositions.', '']
    for row in rows:
        lines += [f"## {row['id']}", '', f"> {row['utterance']}", '',
            f"- Intent: `{row['draft_intent']}`", f"- Entities: `{row['draft_entities']}`",
            f"- Filters: `{row['draft_filters']}`", f"- Current disposition: `{row['draft_disposition']}`",
            f"- Broader supplied expectation: {row['expected_response']}", '']
    (output/'annotation-review.md').write_text('\n'.join(lines), encoding='utf-8', newline='\n')
    sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    record = dict(status='FINAL_ANNOTATION_APPROVAL_PENDING', rows=len(rows),
        user_selected_scope='Use the current v5 read-only scope; preserve broader expectations separately',
        source_proposals_sha256=sha(source), annotation_proposals_sha256=sha(target),
        annotation_review_sha256=sha(output/'annotation-review.md'), finalizer_sha256=sha(Path(__file__)),
        candidate_manifest_sha256=sha(ROOT/'docs/evaluation/phase15/read_queries_v5/manifest-v5.json'),
        proposed_intents=dict(counts), manual_changes=changes,
        source_meaning_review='User supplied category-level approval retained in revision_2',
        final_typed_annotation_review='Pending: no human reviewer approval inferred from scope selection',
        model_inference_run=False, frozen=False)
    (output/'approval-pending.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps(record, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    finalize(parser.parse_args().output)
