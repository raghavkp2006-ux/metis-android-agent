"""Independent v15 development expectations, authored before new grammar implementation."""
import base64
import json
import re
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
SLOTS = [('the indigo resonator', 'Zoë O’Neil'), ('The Who', 'Asha Bedi'),
         ('café 🛰️ receiver', 'Émile Vidal')]
# scope keys are independent authored gold, not parser outputs.
FAMILIES = [
    ('task-exists', 'Have I already created a task to {action}?', 'CHECK_TASKS', 'TASK', 'ANY', '', ''),
    ('task-do-have', 'Do I have a task to {action}?', 'CHECK_TASKS', 'TASK', 'ANY', '', ''),
    ('task-topic', 'What tasks do I have related to {topic}?', 'CHECK_TASKS', 'TASK', 'OPEN', '', ''),
    ('task-completed', 'Find completed tasks related to {topic}.', 'CHECK_TASKS', 'TASK', 'COMPLETED', '', ''),
    ('task-cancelled', 'Show cancelled tasks about {topic}.', 'CHECK_TASKS', 'TASK', 'CANCELLED', '', ''),
    ('task-open', 'Find open tasks about {topic}.', 'CHECK_TASKS', 'TASK', 'OPEN', '', ''),
    ('task-due', 'Find tasks about {topic} due today.', 'CHECK_TASKS', 'TASK', 'ANY', 'DUE', 'today'),
    ('task-completed-date', 'Find tasks about {topic} completed on 2026-10-11.', 'CHECK_TASKS', 'TASK', 'COMPLETED', 'COMPLETED', '2026-10-11'),
    ('task-due-field', 'What is the due date of my task to {action}?', 'CHECK_TASKS', 'TASK', 'ANY', '', ''),
    ('task-completion-field', 'What is the completion date of my task to {action}?', 'CHECK_TASKS', 'TASK', 'ANY', '', ''),
    ('promise-content', 'Did I promise {person} to {action}?', 'WHAT_DID_I_PROMISE', 'PROMISE', 'ANY', '', ''),
    ('promise-told', 'Did I tell {person} I would {action}?', 'WHAT_DID_I_PROMISE', 'PROMISE', 'ANY', '', ''),
    ('promise-negative', 'Did I tell {person} I would not {action} today?', 'WHAT_DID_I_PROMISE', 'PROMISE', 'ANY', '', ''),
    ('promise-fulfilled', 'Find fulfilled promises to {person} about {topic}.', 'WHAT_DID_I_PROMISE', 'PROMISE', 'FULFILLED', '', ''),
    ('promise-due', 'Find promises to {person} about {topic} due tomorrow.', 'WHAT_DID_I_PROMISE', 'PROMISE', 'ANY', 'DUE', 'tomorrow'),
    ('memory-field', 'What email address did I save for {topic}?', 'CHECK_MEMORY', 'MEMORY', '', '', ''),
    ('memory-number', 'What number did I save for {topic}?', 'CHECK_MEMORY', 'MEMORY', '', '', ''),
    ('memory-negative', 'Did I save a note saying I would not {action} today?', 'CHECK_MEMORY', 'MEMORY', '', '', ''),
    ('memory-date', 'Find notes about {topic} saved today.', 'CHECK_MEMORY', 'MEMORY', '', 'CREATED', 'today'),
    ('calendar-topic', 'Do I have a meeting related to {topic}?', 'CHECK_CALENDAR', 'CALENDAR', '', '', ''),
    ('calendar-date', 'Find calendar events about {topic} on 2026-10-13.', 'CHECK_CALENDAR', 'CALENDAR', '', 'START', '2026-10-13'),
    ('calendar-person', 'Find meetings with {person} tomorrow.', 'CHECK_CALENDAR', 'CALENDAR', '', 'START', 'tomorrow'),
    ('task-status-due', 'Find completed tasks about {topic} due tomorrow.', 'CHECK_TASKS', 'TASK', 'COMPLETED', 'DUE', 'tomorrow'),
    ('task-exists-past', 'Did I create a task to {action}?', 'CHECK_TASKS', 'TASK', 'ANY', '', ''),
    ('memory-existence', 'Did I save anything about {topic}?', 'CHECK_MEMORY', 'MEMORY', '', '', ''),
    ('calendar-person-topic', 'Find meetings with {person} about {topic} on 2026-10-13.', 'CHECK_CALENDAR', 'CALENDAR', '', 'START', '2026-10-13'),
    ('calendar-saved-date', 'Did I save the {topic} date?', 'CHECK_CALENDAR', 'CALENDAR', '', '', ''),
    ('memory-attribution', 'Did I save anything about borrowing {topic} from {person}?', 'CHECK_MEMORY', 'MEMORY', '', '', ''),
    ('memory-book-attribution', 'Did I save anything about borrowing a book about {topic} from {person}?', 'CHECK_MEMORY', 'MEMORY', '', '', ''),
]


def expectations(text, scope):
    values = []
    for kind, raw, strip_article in [('PERSON', scope['person'], False),
        ('TASK' if scope['kind'] == 'TASK' else 'MEMORY' if scope['kind'] == 'MEMORY' else 'TITLE', scope['subject'], True),
        ('TIME', scope['date'], False), ('TITLE', scope['requested_field'], False)]:
        if not raw:
            continue
        reference = re.sub(r'^(?:my )?(?:(?:the|a|an)(?: |$))?', '', raw, flags=re.I) if strip_article else raw
        start = text.index(raw) + len(raw) - len(reference)
        values.append(dict(type=kind, rawValue=reference,
                           startOffset=len(text[:start].encode('utf-16-le')) // 2,
                           endOffset=len(text[:start + len(reference)].encode('utf-16-le')) // 2,
                           query=raw))
    return values


def prior_texts():
    def walk(value):
        if isinstance(value, dict):
            if isinstance(value.get('utterance'), str):
                yield value['utterance']
            for nested in value.values():
                yield from walk(nested)
        elif isinstance(value, list):
            for nested in value:
                yield from walk(nested)
    return {normalize(text) for path in HERE.parent.rglob('*.json') if HERE not in path.parents
            for text in walk(json.loads(path.read_text(encoding='utf-8-sig')))}


def normalize(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split())


def build():
    rows = []
    for family, template, intent, kind, status, field, day in FAMILIES:
        for index, (topic, person) in enumerate(SLOTS):
            action = f'review {topic}'
            text = ('Could you please ' if index == 1 else '') + template.format(topic=topic, person=person, action=action)
            subject = (f'borrowing a book about {topic} from {person}' if family == 'memory-book-attribution' else
                       f'borrowing {topic} from {person}' if family == 'memory-attribution' else
                       f'not {action} today' if 'negative' in family else
                       action if '{action}' in template else '' if family == 'calendar-person' else topic)
            scope = dict(kind=kind, subject=subject, person=person if kind == 'PROMISE' or family.startswith('calendar-person') else '',
                         status=status, date_field=field, date=day,
                         requested_field='email address' if family == 'memory-field' else 'number' if family == 'memory-number'
                         else 'due date' if family == 'task-due-field' else 'completion date' if family == 'task-completion-field'
                         else '', negative='negative' in family)
            variants = [('valid', text, True), ('external-negation', 'Do not ' + text, False),
                        ('compound', text.rstrip('?.') + ' and show my tasks', False),
                        ('action', text.rstrip('?.') + ' and delete the results', False),
                        ('count', text.rstrip('?.') + ' only the first three', False),
                        ('priority', text.rstrip('?.') + ' with high priority', False),
                        ('unresolved', text.replace(topic, 'that thing').replace(person, 'her'), False),
                        ('malformed', text.replace(topic, '').replace(person, ''), False),
                        ('control', text + '\u200b', False)]
            for variant, utterance, allowed in variants:
                rows.append(dict(id=f'v15-{family}-{index}-{variant}', family_id=family, utterance=utterance,
                                 intent=intent, read=allowed, scope=scope if allowed else None,
                                 entities=expectations(utterance, scope) if allowed else [],
                                 constraints_to_preserve=utterance, provenance='assistant_authored', human_approved=False))
    boundaries = ['Did I promise to review the indigo resonator?', 'Did I tell her I would review the indigo resonator?',
                  'Find tasks about that thing', 'Find calendar events about the indigo resonator on Friday',
                  'Find tasks about the indigo resonator due 2026-02-30',
                  'Find promises to Zoë O’Neil about the indigo resonator due end of month',
                  'Is there already a reminder to test the API?', 'Find any activity where the word antenna is used',
                  'What is my latest saved intention?', 'Did I finally decide to attend?',
                  'Did I tell Zoë O’Neil I would not review it today?',
                  'Did I create a task to edit methodology afterward?',
                  'Did I create a task to edit methodology later?',
                  'Do I have a task to review the indigo resonator unless the simulation works?']
    rows.extend(dict(id=f'v15-boundary-{index}', family_id='boundaries', utterance=text, intent='UNKNOWN',
                     read=False, scope=None, entities=[], constraints_to_preserve=text, provenance='assistant_authored',
                     human_approved=False) for index, text in enumerate(boundaries))
    earlier = prior_texts()
    excluded, unique = [], []
    seen = set(earlier)
    for row in rows:
        key = normalize(row['utterance'])
        if key in seen:
            excluded.append(row['id'])
        else:
            unique.append(row)
            seen.add(key)
    rows = unique
    assert len({normalize(row['utterance']) for row in rows}) == len(rows)
    data = dict(version='phase15-candidate-v15', authored_before_grammar_implementation=True,
                excluded_exact_duplicates=excluded,
                human_approved=False, fresh_independent_evaluation=False, rows=rows)
    (HERE / 'development.json').write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    lines = ['id\ttext_base64\tread'] + [f"{r['id']}\t{base64.b64encode(r['utterance'].encode()).decode()}\t{int(r['read'])}" for r in rows]
    (HERE / 'development.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    print(f"Authored {len(rows)} rows before implementation; {sum(r['read'] for r in rows)} positive")


if __name__ == '__main__':
    build()
