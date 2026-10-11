"""V8 assistant-authored development families, defined before the v8 implementation."""
import argparse
import base64
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]

# Tuples: semantic intent, outcome, annotation text, complete read query, utterance.
# Scenario families are development-only; no human approval or independent claim.
FAMILIES = {}
for family, command, topic in [
    ('show-me', 'Show me notes about', 'the kiln glaze'),
    ('polite', 'Could you please find my notes regarding', 'the basalt carving'),
    ('do-you-have', 'Do you have any saved notes on', 'the reed weaving'),
    ('recall', 'Remind me of', 'the sourdough starter'),
]:
    text = f'{command} {topic}'
    FAMILIES[family] = [
        ('CHECK_MEMORY', 'read', topic[4:], topic, text + '?'),
        ('CHECK_MEMORY', 'read', topic[4:], topic, 'Please ' + text + '.'),
        ('CHECK_MEMORY', 'clarify', '', '', text + ' from last month'),
        ('CHECK_MEMORY', 'clarify', '', '', text + ' with tag workshop'),
        ('UNKNOWN', 'clarify', '', '', "Don't " + text),
        ('UNKNOWN', 'clarify', '', '', text + ' then archive the tasks'),
        ('UNKNOWN', 'clarify', '', '', text + ' and show my tasks'),
        ('CHECK_MEMORY', 'clarify', '', '', command + ' that thing'),
        ('CHECK_MEMORY', 'clarify', '', '', command + ' "' + topic + '"'),
    ]
FAMILIES['articles-titles'] = [
    ('CHECK_MEMORY', 'read', 'Who', 'The Who', 'Can you show me notes about The Who?'),
    ('CHECK_MEMORY', 'read', 'ceramic bowl', 'a ceramic bowl', 'Show me notes about a ceramic bowl.'),
    ('CHECK_MEMORY', 'read', 'oak cabinet', 'my oak cabinet', 'Do you have notes about my oak cabinet?'),
    ('CHECK_MEMORY', 'read', 'ceramic glaze', 'my ceramic glaze', 'Search memory for my ceramic glaze'),
    ('CHECK_MEMORY', 'clarify', '', '', 'Remind me of the topic'),
    ('CHECK_MEMORY', 'clarify', '', '', 'Show me notes about an'),
    ('UNKNOWN', 'clarify', '', '', 'Can you show me notes about The Who and send them?'),
]
FAMILIES['unicode-possessives'] = [
    ('CHECK_MEMORY', 'read', "cat's harness", "the cat's harness", "Remind me of the cat's harness."),
    ('CHECK_MEMORY', 'read', 'sitar 🚀 tuning', 'the sitar 🚀 tuning', 'Show me notes about the sitar 🚀 tuning?'),
    ('CHECK_MEMORY', 'read', 'café acoustics', 'the café acoustics', 'Could you retrieve notes regarding the café acoustics?'),
    ('CHECK_MEMORY', 'clarify', '', '', 'Remind me of the cat’s harness at 8 pm'),
    ('CHECK_MEMORY', 'clarify', '', '', 'Show me notes about sitar\u200b tuning'),
    ('UNKNOWN', 'clarify', '', '', 'Remind me of café acoustics or create a task'),
]
FAMILIES['polite-task-person'] = [
    ('CHECK_TASKS', 'read', '', '', 'Could you please list my open tasks?'),
    ('WHAT_DID_I_PROMISE', 'read', 'Zoë Sen', 'Zoë Sen', 'Can you show my promises to Zoë Sen?'),
    ('WHAT_DID_I_PROMISE', 'read', "Ravi O’Neil", "Ravi O’Neil", 'Please could you list promises for Ravi O’Neil?'),
    ('WHAT_DID_I_PROMISE', 'clarify', '', '', 'Could you show my promises to him?'),
    ('CHECK_TASKS', 'clarify', '', '', 'Could you list my open tasks due tomorrow?'),
    ('UNKNOWN', 'clarify', '', '', 'Could you list my open tasks then delete one?'),
]
FAMILIES['recall-action-boundary'] = [
    ('UNKNOWN', 'clarify', '', '', 'Remind me to water the terrarium'),
    ('UNKNOWN', 'clarify', '', '', 'Remind me of terrarium maintenance tomorrow at 7 pm'),
    ('UNKNOWN', 'clarify', '', '', 'Remind me of terrarium maintenance. Delete the rest.'),
    ('CHECK_MEMORY', 'clarify', '', '', 'Remind me of it'),
    ('CHECK_MEMORY', 'clarify', '', '', 'Remind me of the terrarium oldest notes'),
    ('UNKNOWN', 'clarify', '', '', 'If I say remind me of the terrarium, what happens?'),
]


def cases():
    rows = []
    for family, items in FAMILIES.items():
        for index, (intent, disposition, reference, query, text) in enumerate(items, 1):
            entities = []
            if reference:
                start = text.index(reference)
                entities = [dict(type='MEMORY' if intent == 'CHECK_MEMORY' else 'PERSON', rawValue=reference,
                                 startOffset=len(text[:start].encode('utf-16-le')) // 2,
                                 endOffset=len(text[:start + len(reference)].encode('utf-16-le')) // 2)]
            rows.append(dict(id=f'v8-dev-{family}-{index:02}', family_id=family, utterance=text, intent=intent,
                             disposition=disposition, entities=entities, query_text=query,
                             source='assistant_authored', human_authored=False, human_approved=False,
                             split='development', input_source='TEXT', timestamp='2026-10-09T05:30:00Z',
                             zone='Asia/Kolkata'))
    return rows


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def binding_digest(path):
    return hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest()


def build():
    rows = cases()
    (HERE / 'development-cases.json').write_text(json.dumps(rows, indent=2, ensure_ascii=False) + '\n',
                                                encoding='utf-8', newline='\n')
    lines = ['id\ttext_base64\tsource\ttimestamp\tzone']
    for row in rows:
        lines.append('\t'.join((row['id'], base64.b64encode(row['utterance'].encode()).decode(),
                                row['input_source'], row['timestamp'], row['zone'])))
    (HERE / 'development-input.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    expectations = ['id\tintent\tdisposition\treference_base64\tquery_base64\tstart\tend']
    for row in rows:
        entity = row['entities'][0] if row['entities'] else {}
        expectations.append('\t'.join((row['id'], row['intent'], row['disposition'],
            base64.b64encode(entity.get('rawValue', '').encode()).decode(),
            base64.b64encode(row['query_text'].encode()).decode(),
            str(entity.get('startOffset', 0)), str(entity.get('endOffset', 0)))))
    (HERE / 'development-expectations.tsv').write_text('\n'.join(expectations) + '\n',
                                                      encoding='utf-8', newline='\n')
    print(f'Authored {len(rows)} development cases in {len(FAMILIES)} families; no human approval.')


def manifest():
    files = sorted(p for p in HERE.iterdir() if p.suffix in {'.py', '.json', '.tsv'} and 'manifest' not in p.name)
    agent = ROOT / 'app/src/main/java/dev/metis/agent/domain/agent'
    files += [agent / (name + '.kt') for name in ('ReadGrammarV8', 'ReadSafetyV8', 'ReadGrammarV7',
              'ReadSafetyV7', 'ReadSafetyV6', 'EnglishRules', 'LanguageSpecialist', 'LocalAgentOrchestrator')]
    files += [ROOT / 'app/src/test/java/dev/metis/agent/evaluation/Phase15RulesSnapshot.kt',
              ROOT / 'app/src/test/java/dev/metis/agent/ReadCoverageV8Test.kt',
              ROOT / 'app/src/androidTest/java/dev/metis/agent/LanguageReadTest.kt',
              ROOT / 'app/src/main/java/dev/metis/agent/data/storage/LocalAgentReads.kt']
    # Preserve and bind the earlier candidates, without refreshing their manifests.
    retained = {}
    for directory in ('read_safety_v6', 'read_coverage_v7'):
        for path in sorted((HERE.parent / directory).iterdir()):
            if path.is_file():
                retained[path.relative_to(ROOT).as_posix()] = digest(path)
    payload = dict(version='phase15-read-coverage-v8', scope='Development and exposed regressions only',
                   rows=len(cases()), families=len(FAMILIES), human_authored=False, human_approved=False,
                   ml_enabled=False, ml_packaged=False,
                   hashes={p.relative_to(ROOT).as_posix(): binding_digest(p) for p in files},
                   retained_candidate_hashes=retained)
    (HERE / 'development-manifest-v8.json').write_text(json.dumps(payload, indent=2) + '\n',
                                                     encoding='utf-8', newline='\n')
    print('Bound v8 development sources; no approval or independent evaluation.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', action='store_true')
    manifest() if parser.parse_args().manifest else build()
