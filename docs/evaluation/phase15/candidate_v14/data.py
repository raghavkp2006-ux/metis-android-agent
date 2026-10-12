"""Authoring precedes grammar and fitting; all new families are assistant-authored."""
import base64
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(HERE.parent / 'human_development_004'))
from prepare import normalized, old_utterances  # noqa: E402

# Independent, explicit retrieval expectations; no expected values are parser outputs.
TOPICS = ('The Who', "O’Neil’s telescope", 'café receiver', 'quantum 🛰️ resonator', 'the maple observatory')
PEOPLE = ('Asha Bedi', 'Zoë O’Neil', 'Chaya Sane', 'Deepak Rao', 'Émile Vidal')
FAMILIES = (
    ('said-about', 'What did I say about {topic}', 'topic'),
    ('told-about', 'What did I tell you about {topic}', 'topic'),
    ('remember', 'What do you remember about {topic}', 'topic'),
    ('remember-past', 'What did you remember about {topic}', 'topic'),
    ('recall', 'What can you recall about {topic}', 'topic'),
    ('saved-from', 'Was any information saved from {topic}', 'topic'),
    ('some-memory', 'Find some memory about {topic}', 'topic'),
    ('memory-involving', 'Find any memory involving {topic}', 'topic'),
    ('memory-to-do', 'Look for any memory to do with {topic}', 'topic'),
    ('anything-saved', 'Find anything saved about {topic}', 'topic'),
    ('some-reference', 'Find some reference on {topic}', 'topic'),
    ('locate-subject', 'Locate anything to do with {topic}', 'topic'),
    ('speech-recollection', 'What was your recollection of my talk with {person}', 'speech'),
    ('coordinated-notes', 'Locate all the notes I left on {person} and {topic}', 'person_topic'),
    ('coordinated-recall', 'What can you recall about {topic} and {person}', 'topic_person'),
)


def span(text, raw):
    start = text.index(raw)
    return dict(type='MEMORY', rawValue=raw, startOffset=len(text[:start].encode('utf-16-le')) // 2,
                endOffset=len(text[:start + len(raw)].encode('utf-16-le')) // 2)


def development():
    old = {key: paths for key, paths in old_utterances().items()
           if any('/candidate_v14/' not in path for path in paths)}
    old.update({normalized(row['utterance']): set() for row in
                json.loads((HERE.parent / 'human_development_004/development.json').read_text(encoding='utf-8'))['rows']})
    rows, seen, excluded = [], set(), []
    for family, template, mode in FAMILIES:
        for index, (topic, person) in enumerate(zip(TOPICS, PEOPLE, strict=True)):
            query = (f'my talk with {person}' if mode == 'speech' else
                     f'{person} and {topic}' if mode == 'person_topic' else
                     f'{topic} and {person}' if mode == 'topic_person' else topic)
            raw = person if mode == 'speech' else query
            if mode in ('topic', 'topic_person'):
                for prefix in ('my the ', 'my a ', 'my an ', 'my ', 'the ', 'a ', 'an '):
                    if raw.lower().startswith(prefix):
                        raw = raw[len(prefix):]
                        break
            text = ('Could you please ' if index % 2 else '') + template.format(topic=topic, person=person)
            variants = [('valid', text, True)] + [
                ('date', text + ' from last week', False),
                ('outer-negation', 'Do not ' + text, False),
                ('action', text + ' and email the results', False),
                ('compound', text + ' and show my tasks', False),
                ('count', text + ' only the first three', False),
                ('malformed', text + '\u200b', False),
            ]
            for kind, utterance, valid in variants:
                identifier = f'v14-{family}-{index}-{kind}'
                key = normalized(utterance)
                if key in old or key in seen:
                    excluded.append(identifier)
                    continue
                seen.add(key)
                rows.append(dict(id=identifier, family_id=family, utterance=utterance,
                                 intent='CHECK_MEMORY', disposition='read' if valid else 'clarify',
                                 entities=[span(utterance, raw)] if valid else [],
                                 query_text=query if valid else '', constraints_to_preserve=utterance,
                                 provenance='assistant_authored', human_approved=False))
    for index, text in enumerate(('What did I say about that thing',
        'What do you remember about him', 'What was your recollection of my talk with someone',
        'Locate all the notes I left on someone and workshop',
        'What can you recall about the prototype and her',
        'Find some memory about the assignment from yesterday',
        'What did I say about the project? Delete the task',
        'What did I say about the project without reading notes')):
        rows.append(dict(id=f'v14-unresolved-{index}', family_id='unresolved', utterance=text,
                         intent='UNKNOWN', disposition='clarify', entities=[], query_text='',
                         constraints_to_preserve=text, provenance='assistant_authored', human_approved=False))
    return rows, excluded


def build():
    rows, excluded = development()
    payload = dict(version='phase15-candidate-v14', authored_before_grammar_implementation=True,
                   provenance='assistant_authored_development', human_approved=False,
                   fresh_independent_evaluation=False, excluded_exact_duplicates=excluded, rows=rows)
    (HERE / 'development.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n',
                                         encoding='utf-8', newline='\n')
    lines = ['id\ttext_base64\tsource\ttimestamp\tzone']
    for row in rows:
        lines.append('\t'.join((row['id'], base64.b64encode(row['utterance'].encode()).decode(),
                                'TEXT', '2026-10-12T05:30:00Z', 'Asia/Kolkata')))
    (HERE / 'input.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    print(f'Authored {len(rows)} development rows before grammar; '
          f'{sum(r["disposition"] == "read" for r in rows)} positive reads; {len(excluded)} exact exclusions')


if __name__ == '__main__':
    build()
