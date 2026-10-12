"""Prepare exposed user development intake; never creates approved gold labels."""
import base64
import csv
import hashlib
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]

# Assistant-draft semantic labels, independent of current parser predictions.
# M=memory recall, P=promise recall, T=task lookup, C=calendar lookup.
# Unlisted cases intentionally remain UNKNOWN pending review of their meaning.
GROUPS = {
    'attachment': {
        'M': '6 7 11 15 19 22 26 30 31 37 40 41 49 52 58 60 62 65 69 70 71 72 75 77 80 85 86 88 91 95 96 101 102 105 106 107 112 119 120',
        'P': '1 2 5 10 20 28 32 33 38 39 53 61 64 76 78 79 89 97 109 110 113',
        'T': '3 4 12 23 24 25 29 35 54 55 56 57 59 67 74 81 82 83 87 92 98 100 103 111 114 115 116 117 118',
        'C': '16 18 42 43 44 45 46 68 84',
    },
    'message': {
        'M': '1 6 7 8 14 15 17 18 19 20 24 27 28 30 31 33 34 36 39 40 41 43 46 47 48 49 53 54 55 56 57 58 60 65 66 69 70 71 72 73 74 75 77 79 80 81 82 85 87 88 89 91 97 98',
        'P': '4 5 11 21 23 37 38 50 59 63 64 78 90',
        'T': '2 3 9 12 13 16 22 26 29 32 35 42 45 51 61 62 76 84 93 94 96',
        'C': '44 67',
    },
}
INTENTS = {'M': 'CHECK_MEMORY', 'P': 'WHAT_DID_I_PROMISE',
           'T': 'CHECK_TASKS', 'C': 'CHECK_CALENDAR'}

# These annotations concern standalone unresolved references, not grammatical
# pronouns with an explicit antecedent in the same request (e.g. Neha/her code).
UNRESOLVED = {
    'attachment': '41 57 59 62 63 64 69 70 71 72 74 75 78 80 82 93 94 98 99 102 103 104 105 106 109 110 115 119',
    'message': '3 5 10 11 13 14 19 20 23 24 25 26 27 28 34 37 38 39 40 43 44 46 47 48 52 53 55 56 59 62 64 66 67 68 69 70 71 74 76 77 80 81 82 84 85 87 88 89 90 91 93 94 96 97 98',
}
SPECIAL = {
    ('attachment', 8): 'Task existence versus advice about purchases is ambiguous.',
    ('attachment', 9): 'Fragmented desire/communication statement; not a clear recall or action request.',
    ('attachment', 13): 'Explicit appointment-creation request with an email action; not a calendar read.',
    ('attachment', 15): 'Recall of a stated meeting intention; memory/promise/calendar meaning needs review.',
    ('attachment', 17): 'Meeting scheduling advice versus lookup is ambiguous.',
    ('attachment', 21): 'Negative declarative content; no explicit retrieval instruction.',
    ('attachment', 22): 'Negative statement recall, not an affirmative promise.',
    ('attachment', 24): 'Not completed denotes open task status; retain the app subject.',
    ('attachment', 34): 'Reminder lookup could mean a task, timer or calendar record.',
    ('attachment', 36): 'Other-person obligations are not the user\'s task list.',
    ('attachment', 47): 'Advice versus a saved deadline/obligation lookup is ambiguous.',
    ('attachment', 48): 'Negative declarative content and Monday deadline; not an explicit read.',
    ('attachment', 50): 'Malformed last-minute/DSP assignment wording needs clarification.',
    ('attachment', 51): 'Activity search spans unspecified record types.',
    ('attachment', 53): 'Broken-promise inference requires completion evidence, not a promise listing.',
    ('attachment', 66): 'Hypothetical advice; not saved-record retrieval.',
    ('attachment', 73): 'Cancellation statement; not an instruction to cancel or recall.',
    ('attachment', 89): 'Training promise wording and recipient all of you are ambiguous.',
    ('attachment', 90): 'Nested negation and declarative wording; do not infer an affirmative intention.',
    ('attachment', 96): 'They and evidence ownership need clarification; do not assert Rahul\'s identity.',
    ('attachment', 99): 'Cross-task comparison with an unresolved reference.',
    ('attachment', 104): 'Assistant obligation versus user task lookup is ambiguous.',
    ('attachment', 108): 'Importance statement; not a retrieval request.',
    ('attachment', 113): 'Self-commitment is not a uniquely saved person recipient.',
    ('attachment', 118): 'Retain the exact quantity five; do not drop it from a task lookup.',
    ('attachment', 119): 'Retain exactly 5; the object of writing is unresolved.',
    ('message', 7): 'Recall of negative content; do not push code or remove negation.',
    ('message', 11): 'Promise-content and today constraints; merge action is not authorized.',
    ('message', 34): 'Negative need and unresolved another one; do not convert to a purchase.',
    ('message', 44): 'Overlap lookup requires the missing time interval and event reference.',
    ('message', 47): 'Preserve conditional negative content and unresolved there; do not schedule/cancel.',
    ('message', 50): 'Her code has the explicit same-request antecedent Neha; retain review-code content.',
    ('message', 51): 'Neha\'s code is a literal task-content subject, not a new review action.',
    ('message', 52): 'Task/promise comparison needs cross-record links and explicit record references.',
    ('message', 58): 'Her antecedent is Neha; retain negative availability and today content.',
    ('message', 68): 'Task and calendar existence is a compound cross-record request.',
    ('message', 69): 'Company identity equality needs explicit records; do not guess from adjacent inputs.',
    ('message', 71): 'Contradiction analysis requires identified records; do not fabricate a resolution.',
    ('message', 72): 'Latest is an ordering constraint, not a plain topic search.',
    ('message', 78): 'Screenshots and too must be retained; recipient/context is unspecified.',
    ('message', 82): 'Keep the attached exam-ambiguity clarification instruction.',
    ('message', 85): 'Keep the attached instruction to ask when the thing is unresolved.',
    ('message', 88): 'Saved records alone do not establish a standalone pronoun antecedent.',
    ('message', 91): 'Keep the attached no-guessing instruction for multiple matches.',
    ('message', 94): 'Keep the attached instruction to clarify multiple matching tasks.',
}


def source_rows():
    attachment = (HERE / 'attachment.txt').read_text(encoding='utf-8-sig')
    rows = [('attachment', index, text) for index, text in
            enumerate(attachment.splitlines(), 1) if text.strip()]
    message = (HERE / 'message.txt').read_text(encoding='utf-8').strip()
    parts = re.split(r'(?<=[?.!])\s+(?=[A-Z])', message)
    instructions = {83, 86, 92, 95}
    for index, text in enumerate(parts, 1):
        if index in instructions:
            continue
        if index + 1 in instructions:
            text += ' ' + parts[index]
        rows.append(('message', index, text))
    assert len(parts) == 98 and len(rows) == 214
    return rows


def normalized(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split())


def old_utterances():
    """Audit structured utterance fields, never general documentation sentences."""
    found = defaultdict(set)
    for path in HERE.parent.rglob('*.json'):
        if HERE in path.parents or '__pycache__' in path.parts:
            continue
        try:
            payload = json.loads(path.read_text(encoding='utf-8-sig'))
        except (ValueError, UnicodeError):
            continue
        stack = [payload]
        while stack:
            value = stack.pop()
            if isinstance(value, dict):
                text = value.get('utterance')
                if isinstance(text, str):
                    found[normalized(text)].add(path.relative_to(ROOT).as_posix())
                stack.extend(value.values())
            elif isinstance(value, list):
                stack.extend(value)
    for pattern, delimiter in (('*.csv', ','), ('*.tsv', '\t')):
        for path in HERE.parent.rglob(pattern):
            if HERE in path.parents:
                continue
            try:
                with path.open(encoding='utf-8-sig', newline='') as stream:
                    for row in csv.DictReader(stream, delimiter=delimiter):
                        text = row.get('utterance') or row.get('text')
                        if not text and row.get('text_base64'):
                            text = base64.b64decode(row['text_base64'], validate=True).decode('utf-8')
                        if text:
                            found[normalized(text)].add(path.relative_to(ROOT).as_posix())
            except (ValueError, UnicodeError, csv.Error):
                continue
    return found


def make_rows():
    old = old_utterances()
    seen = defaultdict(list)
    labels = {}
    for source, groups in GROUPS.items():
        for code, indices in groups.items():
            for index in map(int, indices.split()):
                assert (source, index) not in labels
                labels[source, index] = INTENTS[code]
    unresolved = {(source, int(index)) for source, indices in UNRESOLVED.items()
                  for index in indices.split()}
    rows = []
    for source, index, text in source_rows():
        key = normalized(text)
        identifier = f'human-dev-004-{source}-{index:03}'
        notes = [SPECIAL[source, index]] if (source, index) in SPECIAL else []
        missing = (source, index) in unresolved
        if missing:
            notes.append('Standalone unresolved reference; do not use previous rows as context.')
        rows.append(dict(id=identifier, source=source, source_ordinal=index, utterance=text,
                         intent_draft=labels.get((source, index), 'UNKNOWN'),
                         annotation_provenance='assistant_draft', human_approved=False,
                         fit_eligible=False, calibration_eligible=False, holdout_eligible=False,
                         standalone_clarification_draft=missing,
                         constraints_to_preserve=text, entities_draft=None,
                         query_text_draft=None, disposition_draft=None,
                         review_notes=notes,
                         earlier_duplicate_paths=sorted(old[key]),
                         earlier_in_batch_duplicates=list(seen[key])))
        seen[key].append(identifier)
    return rows


def main():
    rows = make_rows()
    payload = dict(version='phase15-human-development-004-intake',
                   purpose='Exposed development intake and ungraded current-behavior diagnostics',
                   authorship='user_submitted; not independently verified',
                   conversation_context='separate requests, no previous-row history',
                   human_approved=False, fresh_independent_evaluation=False, ml_enabled=False,
                   source_sha256={name: hashlib.sha256((HERE / name).read_bytes()).hexdigest()
                                  for name in ('attachment.txt', 'message.txt')}, rows=rows)
    (HERE / 'development.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n',
                                         encoding='utf-8', newline='\n')
    lines = ['id\ttext_base64\tsource\ttimestamp\tzone']
    for row in rows:
        encoded = base64.b64encode(row['utterance'].encode()).decode()
        lines.append(f"{row['id']}\t{encoded}\tTEXT\t2026-10-12T05:30:00Z\tAsia/Kolkata")
    (HERE / 'input.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    review = ['# Review of human development intake 004', '',
              'All 214 cases are separate requests. Labels are assistant drafts, not approvals. '
              'Entities, complete queries and desired dispositions remain unlabelled pending review. '
              'The exact request is retained as the constraint-preservation checklist. '
              'UNKNOWN also includes genuinely ambiguous semantic intent; it is not a safety score.', '',
              '| ID | Request / constraints | Draft semantic intent | Clarify standalone reference? | Review notes |',
              '| --- | --- | --- | --- | --- |']
    for row in rows:
        notes = list(row['review_notes'])
        if row['earlier_in_batch_duplicates']:
            notes.append('Repeat of ' + ', '.join(row['earlier_in_batch_duplicates']))
        if row['earlier_duplicate_paths']:
            notes.append('Duplicate in earlier exposed structured inputs; exclude independent scoring.')
        cells = [row['id'], row['utterance'], row['intent_draft'],
                 'yes' if row['standalone_clarification_draft'] else 'review', '; '.join(notes)]
        review.append('| ' + ' | '.join(cell.replace('|', '\\|') for cell in cells) + ' |')
    (HERE / 'user-review.md').write_text('\n'.join(review) + '\n', encoding='utf-8', newline='\n')
    print(f"Prepared {len(rows)} rows; {len({normalized(r['utterance']) for r in rows})} unique; "
          f"{sum(bool(r['earlier_duplicate_paths']) for r in rows)} earlier structured-data duplicates")


if __name__ == '__main__':
    main()
