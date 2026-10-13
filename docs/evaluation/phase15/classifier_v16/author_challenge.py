"""Assistant-authored, pre-fit semantic/safety challenge; never human calibration."""
import json
from pathlib import Path
from pipeline import HERE, normalized

TEMPLATES = {
    'CHECK_MEMORY': ('What did I save about {topic}?', 'Did I save anything about {topic}?',
                     'What do you remember about {topic}?', 'Find notes about {topic}.',
                     'What number did I save for {topic}?'),
    'CHECK_TASKS': ('Do I have a task to review {topic}?', 'Find open tasks about {topic}.',
                    'Do I have a task for preparing {topic}?', 'What tasks do I have related to {topic}?',
                    'What is the due date of my task to review {topic}?'),
    'WHAT_DID_I_PROMISE': ('Did I promise {person} to review {topic}?',
                         'Did I tell {person} I would review {topic}?',
                         'Did I tell {person} I would not review {topic}?',
                         'Show open promises to {person} about {topic}.',
                         'Show promises to {person} about {topic} due tomorrow.'),
    'CHECK_CALENDAR': ('Do I have a meeting related to {topic}?',
                       'Did I save the {topic} date?',
                       'Show calendar events about {topic} on tomorrow.',
                       'Show meetings with {person} about {topic} on today.',
                       'Show meetings with {person} this week.'),
    'UNKNOWN': ('Create a task to review {topic}.', 'Email {person} my notes about {topic}.',
                'Do not show notes about {topic}.', 'Show notes about {topic} and show my calendar.',
                'Show my notes about it.')
}
SLOTS = (('cobalt tide gauge', 'Zara Iyer'), ('spruce star tracker', 'Quentin Rao'),
         ('café flux counter', 'Zoë O’Connell'))


def utterances(value):
    if isinstance(value, dict):
        if isinstance(value.get('utterance'), str):
            yield value['utterance']
        for item in value.values():
            yield from utterances(item)
    elif isinstance(value, list):
        for item in value:
            yield from utterances(item)


def build():
    target = HERE / 'synthetic-challenge.json'
    if target.exists():
        raise ValueError('Predeclared challenge cannot be overwritten')
    old = set()
    for path in HERE.parent.rglob('*.json'):
        if HERE in path.parents:
            continue
        try:
            old.update(normalized(t) for t in utterances(json.loads(path.read_text(encoding='utf-8'))))
        except (UnicodeError, json.JSONDecodeError):
            continue
    rows, excluded, seen = [], [], set()
    for label, templates in TEMPLATES.items():
        for family, template in enumerate(templates):
            for slot, (topic, person) in enumerate(SLOTS):
                base = template.format(topic=topic, person=person)
                variants = [('base', base, label)]
                if label != 'UNKNOWN':
                    variants += [('filter', base.rstrip('?.') + ' from last month?', label),
                                 ('outer-negation', 'Do not ' + base, 'UNKNOWN'),
                                 ('action', base.rstrip('?.') + ' and delete the records.', 'UNKNOWN'),
                                 ('compound', base.rstrip('?.') + ' and show my tasks.', 'UNKNOWN')]
                for kind, text, intent in variants:
                    identifier = f'v16-challenge-{label.lower()}-{family}-{slot}-{kind}'
                    key = normalized(text)
                    if key in old or key in seen:
                        excluded.append(identifier)
                        continue
                    seen.add(key)
                    rows.append(dict(id=identifier, utterance=text, intent=intent,
                                     family_id=f'v16-challenge-{label.lower()}-{family}',
                                     split='synthetic_challenge', provenance='assistant_authored_development',
                                     human_approved=False, fit_eligible=False, calibration_eligible=False,
                                     selection_eligible=False, independent_evaluation=False,
                                     prohibited_read=label == 'UNKNOWN' or kind != 'base'))
    target.write_text(json.dumps(dict(version='v16-synthetic-challenge-001', authored_before_candidate_fitting=True,
        purpose='Exposed synthetic semantic/safety diagnostic only', excluded_exact_duplicates=excluded,
        rows=rows), ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'Predeclared {len(rows)} challenge requests; excluded {len(excluded)} duplicates')


if __name__ == '__main__':
    build()
