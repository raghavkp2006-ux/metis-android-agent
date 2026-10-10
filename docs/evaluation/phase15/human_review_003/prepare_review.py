"""Manual annotation proposals for IRS-003. No model prediction or approval."""
import argparse
import csv
import hashlib
import io
import json
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
FIELDS = ['id', 'utterance', 'draft_intent', 'draft_entities', 'draft_filters',
          'draft_disposition', 'expected_response', 'reviewed_intent',
          'reviewed_entities', 'reviewed_filters', 'reviewed_disposition',
          'reviewer', 'approved', 'human_authored']

# These strings were selected from supplied text and meaning by the assistant,
# independently of candidate extraction/prediction. They remain unapproved.
MEMORY_TOPICS = {
    1:'trip to munnar', 2:'wifi password for the cafe', 3:'recipe for kadhi',
    4:'conference', 5:'house i looked at on fridays road', 6:'book i was reading',
    7:'my friend said about her divorce', 8:'my aunty', 9:'meeting with the client',
    10:'not eating after 9pm', 11:"dog's vet", 12:'mechanic', 13:'exam syllabus',
    14:'argument with rohan', 15:'restaurant we liked in bangalore',
    16:'course on coursera', 17:'weather in shillong in october',
    18:"teacher at my niece's school", 19:'scan results', 20:'concert setlist',
    21:'password for the cloud drive', 22:'why i quit the running club',
    23:'land registry office hours', 24:'hotel in manali',
    25:'warranty on the washing machine'}
PEOPLE = {1:'kavita', 2:'arjun', 4:'priya', 6:'meera', 7:'sanjay', 9:'rohan',
          11:'nikhil', 12:'ananya', 19:'kavita', 21:'rohan'}
PROMISE_SUBJECTS = {1:'photos', 2:'shift', 4:'move', 6:'birthday party',
    7:'manuscript', 9:'cook', 11:'camera', 12:'job referral', 19:'thesis',
    21:'pay him back'}
TASK_TOPICS = {1:'renew my passport', 5:'office', 6:'gym membership',
               15:'leaky tap', 20:'landlord and the deposit',
               21:'renew my library card', 25:'visa application'}


def entity(text, kind, raw):
    starts = [i for i in range(len(text)) if text.startswith(raw, i)]
    if len(starts) != 1:
        raise ValueError(f'Non-unique manual entity: {raw}')
    begin = len(text[:starts[0]].encode('utf-16-le')) // 2
    return dict(type=kind, rawValue=raw, startOffset=begin,
                endOffset=begin + len(raw.encode('utf-16-le')) // 2)


def draft(row):
    s, n = int(row['section']), int(row['section_row'])
    text, label = row['utterance'], row['assistant_draft_intent']
    spans, filters = [], {}
    disposition, reason = 'clarify', 'Annotation requires scope/context review'
    if label == 'CHECK_TASKS':
        filters['status'] = 'incomplete'
        if n in TASK_TOPICS:
            filters['topic'] = TASK_TOPICS[n]
        if n == 3:
            filters.update(time_expression='this week', date_range={
                'start':'2026-10-05', 'end_exclusive':'2026-10-12'})
        if n == 13:
            filters['deadline_before'] = 'friday'
        if n == 15:
            filters['status'] = 'any'
        if n == 17:
            filters['creation_age_days_gt'] = 30
        reason = 'Requested topic/date/age/status lookup exceeds current task read port'
    elif label == 'CHECK_MEMORY':
        raw = MEMORY_TOPICS[n] if s == 2 else 'car service' if s == 1 else None
        if raw:
            spans = [entity(text, 'MEMORY', raw)]
            disposition, reason = 'read', 'Search stored notes by literal topic; do not infer unstored facts'
        else:
            reason = 'Missing topic/context'
        if s == 2 and n == 1:
            filters['time_expression'] = 'last year'
            disposition, reason = 'clarify', 'Historical time restriction is not supported by current read port'
        if s == 2 and n in {6,9,16}:
            disposition, reason = 'clarify', 'Specific book/meeting/course is unresolved without conversation context'
    elif label == 'WHAT_DID_I_PROMISE':
        if s == 3 and n in PEOPLE:
            spans = [entity(text, 'PERSON', PEOPLE[n])]
            filters['subject'] = PROMISE_SUBJECTS[n]
            reason = 'Subject/status restriction requires a richer commitment read port'
            if n in {4,11,19}:
                filters['status'] = 'any'
            if n == 4:
                filters['time_expression'] = 'sunday'
            if n == 21:
                filters['time_expression'] = 'end of month'
        elif s == 5 and n == 1:
            spans = [entity(text, 'PERSON', 'devika')]
            reason = 'Reviewer must resolve whether NOT is an ordinary existence question or intended clarification'
        else:
            reason = 'Person, collective identity, subject or temporal context is unresolved'
        if (s == 1 and n == 23) or (s == 3 and n == 3):
            filters['status'] = 'unresolved'
            reason = 'All-person unresolved commitments are outside the person-only read port'
        if s == 3 and n == 11:
            reason = 'Lender/returner role is ambiguous; approve intended meaning before status lookup'
    elif label == 'CHECK_CALENDAR':
        disposition, reason = 'unsupported', 'Calendar access is unavailable in current product'
        if s == 5 and n in {15,20}:
            disposition, reason = 'clarify', 'Date is missing or invalid'
        if s == 5 and n == 10:
            filters.update(time_expression='day after the day after tomorrow',
                           date_range={'start':'2026-10-12','end_exclusive':'2026-10-13'})
        if s == 5 and n == 25:
            filters.update(time_expression='today',
                           date_range={'start':'2026-10-09','end_exclusive':'2026-10-10'})
        reason += '; exact time constraints still require reviewer annotation'
    else:
        reason = 'Outside read-classifier scope; preserve native action handling and resolve ambiguity separately'
        if s == 5 and n in {2,4,8,17,21}:
            disposition = 'reject'
            reason = 'Hypothetical, unsupported request or non-actionable statement; no read or mutation'
    return spans, filters, disposition, reason


def prepare(output):
    if output.exists():
        raise ValueError('Never overwrite annotation proposals')
    with (HERE/'supplied-annotations.csv').open(encoding='utf-8', newline='') as stream:
        rows = list(csv.DictReader(stream))
    proposals, notes = [], []
    for row in rows:
        spans, qualifiers, disposition, reason = draft(row)
        proposals.append(dict(id=row['id'], utterance=row['utterance'],
            draft_intent=row['assistant_draft_intent'],
            draft_entities=json.dumps(spans, ensure_ascii=False),
            draft_filters=json.dumps(qualifiers, ensure_ascii=False),
            draft_disposition=disposition, expected_response=row['supplied_expected_response'],
            human_authored='yes'))
        notes.append(dict(id=row['id'], rationale=reason))
    if len(proposals) != 125 or len({r['id'] for r in proposals}) != 125:
        raise ValueError('Intake must contain 125 distinct rows')
    output.mkdir(parents=True)
    for name, fields, data in [('annotation-proposals.csv', FIELDS, proposals),
                              ('annotation-rationale.csv', ['id','rationale'], notes)]:
        stream = io.StringIO(newline='')
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator='\n')
        writer.writeheader(); writer.writerows(data)
        (output/name).write_text(stream.getvalue(), encoding='utf-8', newline='\n')
    manifest = ROOT/'docs/evaluation/phase15/read_queries_v5/manifest-v5.json'
    report = dict(status='UNAPPROVED_MANUAL_PROPOSALS', rows=len(proposals),
        candidate_manifest_sha256=hashlib.sha256(manifest.read_bytes()).hexdigest(),
        supplied_annotations_sha256=hashlib.sha256((HERE/'supplied-annotations.csv').read_bytes()).hexdigest(),
        proposal_generator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        proposed_intents=dict(Counter(r['draft_intent'] for r in proposals)),
        proposed_current_dispositions=dict(Counter(r['draft_disposition'] for r in proposals)),
        model_inference_run=False, human_approval=False, frozen=False,
        limit='Manual assistant proposals; extended constraint keys need reviewer approval. Partial calendar filters, uncertain routes and current capability projections are not final ground truth.')
    (output/'proposal-report.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    prepare(parser.parse_args().output)
