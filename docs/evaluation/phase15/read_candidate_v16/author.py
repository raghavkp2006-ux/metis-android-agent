"""New assistant-authored read families, generated before v16 grammar implementation."""
import hashlib
import json
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
SLOTS = (('cobalt tide gauge', 'Zara Iyer'), ('spruce star tracker', 'Quentin Rao'),
         ('café flux counter', 'Zoë O’Connell'))
# Complete read request, full subject, semantic label, task status, promise/calendar person, negative content.
FAMILIES = (
('Have I already got a task to review {topic}?', 'review {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Have I created a task already to review {topic}?', 'review {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Have I planned a task to visit {topic}?', 'visit {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Do I have an assignment to upload {topic}?', 'upload {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Have I made a task to run {topic}?', 'run {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Have I saved a task to edit {topic}?', 'edit {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Have I designed a task to address {topic}?', 'address {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Did I have a task to review {topic}?', 'review {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Was there a task to fix {topic}?', 'fix {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Is there a task to check {topic}?', 'check {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Find my task about contacting {person}.', 'contacting {person}', 'CHECK_TASKS', 'OPEN', '', False),
('Find the task to send {topic}.', 'send {topic}', 'CHECK_TASKS', 'OPEN', '', False),
('Find some task about testing {topic}.', 'testing {topic}', 'CHECK_TASKS', 'OPEN', '', False),
('Find some task which relates to {topic}.', '{topic}', 'CHECK_TASKS', 'OPEN', '', False),
('Find all tasks related to {topic}.', '{topic}', 'CHECK_TASKS', 'OPEN', '', False),
('Locate uncompleted work on {topic}.', '{topic}', 'CHECK_TASKS', 'OPEN', '', False),
('What are tasks that are not completed with {topic}?', '{topic}', 'CHECK_TASKS', 'OPEN', '', False),
('What completed tasks are related to {topic}?', '{topic}', 'CHECK_TASKS', 'COMPLETED', '', False),
('Find tasks related to {topic} that are marked completed.', '{topic}', 'CHECK_TASKS', 'COMPLETED', '', False),
('Do I have a completed task for running {topic}?', 'running {topic}', 'CHECK_TASKS', 'COMPLETED', '', False),
('What are the status of my {topic} task?', '{topic} task', 'CHECK_TASKS', 'ANY', '', False),
('Is there a {topic} related task?', '{topic}', 'CHECK_TASKS', 'ANY', '', False),
('Find any unfinished {topic} tasks.', '{topic}', 'CHECK_TASKS', 'OPEN', '', False),
('Did I create a task for printing {topic}?', 'printing {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Is there also an {topic} task?', '{topic}', 'CHECK_TASKS', 'ANY', '', False),
('Is there something I need to purchase in terms of {topic}?', 'purchase in terms of {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Look for the task, in which I said I need to buy {topic}.', 'buy {topic}', 'CHECK_TASKS', 'OPEN', '', False),
('Did I create a task to turn in {topic}?', 'turn in {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Did I create a task to go and check on {topic}?', 'go and check on {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Did I create a task to push the latest {topic}?', 'push the latest {topic}', 'CHECK_TASKS', 'ANY', '', False),
('Did I create a task to add the new {topic} to my portfolio?', 'add the new {topic} to my portfolio', 'CHECK_TASKS', 'ANY', '', False),
('Did I create a task to prepare before visiting {topic}?', 'prepare before visiting {topic}', 'CHECK_TASKS', 'ANY', '', False),
('What were the prep steps for {topic}?', 'prep steps for {topic}', 'CHECK_MEMORY', '', '', False),
('What did I discuss with {person}?', '{person}', 'CHECK_MEMORY', '', '', False),
('Did I mention that the reviewer was {person}?', 'the reviewer was {person}', 'CHECK_MEMORY', '', '', False),
('What topics of {topic} did I mention I’d like to revise?', 'topics of {topic}', 'CHECK_MEMORY', '', '', False),
('Did I say that I already have a {topic}?', 'already have a {topic}', 'CHECK_MEMORY', '', '', False),
('Find some record about {topic}.', '{topic}', 'CHECK_MEMORY', '', '', False),
('Is {topic} mentioned in saved record?', '{topic}', 'CHECK_MEMORY', '', '', False),
('Did I mention submitting {topic} physically?', 'submitting {topic} physically', 'CHECK_MEMORY', '', '', False),
('Did I explicitly say {topic} was not my responsibility?', '{topic} was not my responsibility', 'CHECK_MEMORY', '', '', True),
('Did I say {topic} were optional?', '{topic} were optional', 'CHECK_MEMORY', '', '', False),
('Did I tell {person} that I would post {topic}?', 'post {topic}', 'WHAT_DID_I_PROMISE', 'ANY', '{person}', False),
('Did I set an event for {topic}?', '{topic}', 'CHECK_CALENDAR', '', '', False),
('Have I made an appointment with {person}?', '', 'CHECK_CALENDAR', '', '{person}', False),
('Have I arranged a meeting with {person}?', '', 'CHECK_CALENDAR', '', '{person}', False),
('Did I schedule a {topic} meeting?', '{topic} meeting', 'CHECK_CALENDAR', '', '', False),
('Did I schedule anything related to {topic}?', '{topic}', 'CHECK_CALENDAR', '', '', False),
('When should my next {topic} meeting be?', '{topic} meeting', 'CHECK_CALENDAR', '', '', False),
)


def normalized(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split())


def build():
    output = HERE / 'development.json'
    if output.exists():
        raise ValueError('Pre-implementation development gold cannot be overwritten')
    old = set()
    for folder, filename in (('candidate_v15', 'regression.json'), ('candidate_v15', 'review-projection.json'),
                              ('calibration_intake_006', 'reviewed.json'), ('classifier_v16', 'synthetic-challenge.json')):
        content = json.loads((HERE.parent / folder / filename).read_text(encoding='utf-8'))
        old.update(normalized(row['utterance']) for row in (content if isinstance(content, list) else content['rows']))
    rows, seen, excluded = [], set(), []
    for family, (template, subject, label, status, person, negative) in enumerate(FAMILIES):
        for slot, (topic, name) in enumerate(SLOTS):
            values = dict(topic=topic, person=name)
            text, query, recipient = template.format(**values), subject.format(**values), person.format(**values)
            positive = dict(utterance=text, intent=label, subject=query, person=recipient, status=status,
                            negative=negative, requested_field='status' if 'the status' in text else '',
                            upcoming='When should my next' in text, read=True)
            variants = [('base', positive),
                        ('polite', dict(positive, utterance='Could you please ' + text)),
                        ('filter', dict(positive, utterance=text.rstrip('?.') + ' from last month?', read=False)),
                        ('negation', dict(positive, utterance='Do not ' + text, read=False)),
                        ('action', dict(positive, utterance=text.rstrip('?.') + ' and delete the records.', read=False)),
                        ('compound', dict(positive, utterance=text.rstrip('?.') + ' and show my notes.', read=False)),
                        ('unresolved', dict(positive, utterance=text.replace(topic, 'it').replace(name, 'her'), read=False)),
                        ('malformed', dict(positive, utterance=text.rstrip('?.') + ' ;;', read=False))]
            for kind, row in variants:
                key = normalized(row['utterance'])
                identifier = f'v16-read-{family:02d}-{slot}-{kind}'
                if key in old or key in seen:
                    excluded.append(identifier)
                    continue
                seen.add(key)
                rows.append(dict(row, id=identifier, family=f'v16-read-family-{family:02d}',
                                 provenance='assistant_authored_development', human_approved=False,
                                 fitting_eligible=False, calibration_eligible=False, independent_evaluation=False))
    output.write_text(json.dumps(dict(version='v16-read-development-001', authored_before_grammar_implementation=True,
        protocol_sha256=hashlib.sha256((HERE / 'protocol.json').read_bytes()).hexdigest(),
        excluded_exact_duplicates=excluded, rows=rows), ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'Authored {len(rows)} v16 read cases, {sum(r["read"] for r in rows)} valid; {len(excluded)} duplicate exclusions')


if __name__ == '__main__':
    build()
