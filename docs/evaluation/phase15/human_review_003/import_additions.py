"""Merge the supplied additions into a new review revision, without inference."""
import argparse
import csv
import hashlib
import io
import json
import re
from collections import Counter
from pathlib import Path

from prepare_review import HERE, ROOT, FIELDS


def read_csv(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream))


def write_csv(path, fields, rows):
    stream = io.StringIO(newline='')
    writer = csv.DictWriter(stream, fieldnames=fields, lineterminator='\n')
    writer.writeheader(); writer.writerows(rows)
    path.write_text(stream.getvalue(), encoding='utf-8', newline='\n')


def merge(source, output, extra_task=None):
    if output.exists():
        raise ValueError('Never overwrite a review revision')
    raw = source.read_bytes()
    content = raw.decode('utf-8-sig')
    queries_text, review_text = content.split('Now the review of the full gate set.', 1)
    mode, number, pending, additions, originals, notes = 'tasks', 0, None, [], [], []
    repairs = []
    task_topics = {1:'gas cylinder', 3:'visa', 5:'bike insured',
        6:'driving licence', 8:'school fee', 10:'water purifier filter',
        11:'gym payment', 13:'electricity connection', 14:'trip to kerala', 15:'passport'}
    for line in queries_text.splitlines():
        line = line.strip()
        if not line:
            continue
        if line == 'Now the 4 fresh calendar-read queries.':
            if pending is not None or number != 15:
                raise ValueError('Expected 15 task additions before calendar section')
            mode, number = 'calendar', 0
            continue
        if line.startswith('User means:'):
            if pending is None:
                raise ValueError('Meaning without query')
            meaning, expected = line[len('User means:'):].split(' Expected:', 1)
            identifier = f'irs003-add-{mode}-{number:02d}'
            compound = mode == 'tasks' and number == 7
            label = 'UNKNOWN' if compound else 'CHECK_TASKS' if mode == 'tasks' else 'CHECK_CALENDAR'
            filters, spans = {}, []
            disposition = 'clarify' if mode == 'tasks' else 'unsupported'
            reason = 'Requested structured task lookup exceeds current unfiltered read port'
            if mode == 'tasks' and not compound:
                filters['status'] = 'any' if number in {3,6,11,15} else 'incomplete'
                if number in task_topics:
                    filters['topic'] = task_topics[number]
                if number in {2,12}:
                    filters.update(time_expression='this month',
                        date_range={'start':'2026-10-01','end_exclusive':'2026-11-01'})
                if number == 4:
                    filters['order_by'] = 'deadline_urgency'
                if number == 9:
                    disposition, reason = 'read', 'Retrieve full pending list; do not assume it contains exactly seventeen items'
            elif compound:
                reason = 'Explicit search of both tasks and notes; outside single-read-intent protocol'
            else:
                reason = 'Calendar access is unavailable; supplied event lookup remains desired future behavior'
                if number == 1:
                    filters.update(time_expression='this weekend',
                        date_range={'start':'2026-10-10','end_exclusive':'2026-10-12'})
                elif number == 2:
                    filters['topic'] = 'school'
                elif number == 3:
                    filters.update(time_expression='wednesday morning',
                        date_range={'start':'2026-10-14','end_exclusive':'2026-10-15'}, time_of_day='morning')
                else:
                    filters.update(time_expression='next two weeks',
                        date_range={'start':'2026-10-09','end_exclusive':'2026-10-23'})
                    reason += '; reviewer must approve whether 14-day window includes reference day'
            additions.append(dict(id=identifier, utterance=pending, draft_intent=label,
                draft_entities=json.dumps(spans), draft_filters=json.dumps(filters),
                draft_disposition=disposition, expected_response=expected.strip(), human_authored='yes'))
            originals.append(dict(id=identifier, section=mode, section_row=number,
                utterance=pending, supplied_meaning=meaning.strip(),
                supplied_expected_response=expected.strip(), assistant_draft_intent=label))
            notes.append(dict(id=identifier, rationale=reason))
            pending = None
        elif line.endswith('"'):
            if pending is not None:
                raise ValueError('Query without meaning')
            number += 1
            if not line.startswith('"'):
                if mode != 'tasks' or number != 1:
                    raise ValueError('Unexpected missing opening quote')
                repairs.append('First query lacked an opening delimiter; utterance content unchanged')
            pending = line.removeprefix('"').removesuffix('"')
        else:
            raise ValueError(f'Unexpected input line: {line}')
    if pending is not None or len(additions) != 19 or mode != 'calendar' or number != 4:
        raise ValueError('Expected 15 task-section and 4 calendar-section additions')
    attachment_additions = len(additions)
    if extra_task:
        # Preserve the user's query content; remove only paste delimiters.
        query = extra_task.strip().removeprefix('"').removesuffix('"')
        additions.append(dict(id='irs003-add-tasks-16', utterance=query,
            draft_intent='CHECK_TASKS', draft_entities='[]',
            draft_filters=json.dumps(dict(status='incomplete', topic='cancelling the newspaper')),
            draft_disposition='clarify', expected_response='Check whether the pending newspaper-cancellation task exists; do not cancel a subscription',
            human_authored='yes'))
        originals.append(dict(id='irs003-add-tasks-16', section='tasks', section_row=16,
            utterance=query, supplied_meaning='User supplied query only; task-existence meaning is an assistant draft',
            supplied_expected_response='Not supplied; assistant proposal is in annotation-proposals.csv',
            assistant_draft_intent='CHECK_TASKS'))
        notes.append(dict(id='irs003-add-tasks-16', rationale='Read of a task about cancellation, not a cancellation command; topic-filter capability remains absent'))
    merged = read_csv(HERE/'proposals/annotation-proposals.csv') + additions
    seen, duplicates = {}, []
    for row in merged:
        key = re.sub(r'\s+', ' ', row['utterance'].casefold()).strip()
        if key in seen:
            duplicates.append([seen[key], row['id']])
        seen[key] = row['id']
    output.mkdir(parents=True)
    (output/'supplied-additions-and-review.txt').write_text(content, encoding='utf-8', newline='\n')
    write_csv(output/'supplied-additions.csv', list(originals[0]), originals)
    write_csv(output/'annotation-proposals.csv', FIELDS, merged)
    write_csv(output/'annotation-rationale.csv', ['id','rationale'],
              read_csv(HERE/'proposals/annotation-rationale.csv') + notes)
    counts = Counter(r['draft_intent'] for r in merged)
    report = dict(status='REVIEW_REVISION_2_NOT_FROZEN', rows=len(merged), additions=len(additions),
        attachment_additions=attachment_additions, extra_task_from_user_reply=extra_task,
        proposed_intents=dict(counts), minimum_shortfalls={label:max(0,25-counts[label]) for label in
            ['CHECK_TASKS','CHECK_MEMORY','WHAT_DID_I_PROMISE','CHECK_CALENDAR','UNKNOWN']},
        original_attachment_sha256=hashlib.sha256(raw).hexdigest(),
        repository_attachment_sha256=hashlib.sha256((output/'supplied-additions-and-review.txt').read_bytes()).hexdigest(),
        candidate_manifest_sha256=hashlib.sha256((ROOT/'docs/evaluation/phase15/read_queries_v5/manifest-v5.json').read_bytes()).hexdigest(),
        importer_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        exact_within_merged_duplicates=duplicates, delimiter_repairs=repairs,
        authorship='Continuation of user-confirmed human batch; prior session attestation carried forward, not independently verified',
        supplied_review_recorded=True, supplied_review_scope='Product expectations and category headings, including writes/calendar access',
        typed_read_gate_annotations_approved=False, model_inference_run=False, frozen=False,
        unresolved=['User category approval includes mutations and available calendar access; incompatible with unchanged v5 read-only capability protocol',
                    'Exact entities/extended filters/current-capability dispositions remain assistant proposals'])
    (output/'revision-report.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--extra-task')
    args = parser.parse_args()
    merge(args.input, args.output, args.extra_task)
