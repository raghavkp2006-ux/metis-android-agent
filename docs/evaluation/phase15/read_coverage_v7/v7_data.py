"""Authored contract/scenario development cases, not human or independent evaluation."""
import base64
import argparse
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
# Defined before implementing the v7 grammar. Variants of a scenario share a family.
# These are new assistant-authored examples; no authorship/independence attestation.
FAMILIES = {
    'task-inventory': [
        ('CHECK_TASKS', 'read', '', 'Show my open tasks.'),
        ('CHECK_TASKS', 'read', '', 'Do I have any pending tasks?'),
        ('CHECK_TASKS', 'read', '', 'What tasks are unfinished?'),
        ('CHECK_TASKS', 'clarify', '', 'Show my open tasks due before Friday.'),
        ('CHECK_TASKS', 'clarify', '', 'Show my open tasks with priority 2.'),
        ('UNKNOWN', 'clarify', '', 'Show my open tasks and archive the rest.'),
    ],
    'task-state-wording': [
        ('CHECK_TASKS', 'read', '', 'List incomplete tasks.'),
        ('CHECK_TASKS', 'read', '', 'What is still pending on my task list?'),
        ('CHECK_TASKS', 'read', '', 'Do I have unfinished tasks?'),
        ('CHECK_TASKS', 'clarify', '', 'List completed tasks.'),
        ('CHECK_TASKS', 'clarify', '', 'List incomplete tasks about replacing the desk.'),
        ('UNKNOWN', 'clarify', '', 'List incomplete tasks then send them to Lata.'),
    ],
    'craft-journal': [
        ('CHECK_MEMORY', 'read', 'indigo dyeing', 'Find my notes about indigo dyeing.'),
        ('CHECK_MEMORY', 'read', 'indigo dyeing', 'Are there any saved notes on indigo dyeing?'),
        ('CHECK_MEMORY', 'read', 'indigo dyeing', 'What have I recorded about indigo dyeing?'),
        ('CHECK_MEMORY', 'clarify', '', 'Find my notes about indigo dyeing from last month.'),
        ('CHECK_MEMORY', 'clarify', '', 'Find my notes about indigo dyeing tagged workshop.'),
        ('UNKNOWN', 'clarify', '', 'Find my notes about indigo dyeing and publish them.'),
    ],
    'mechanics-study': [
        ('CHECK_MEMORY', 'read', 'friction in pulleys', 'Show my saved notes about friction in pulleys.'),
        ('CHECK_MEMORY', 'read', 'friction in pulleys', 'Search my memory for friction in pulleys.'),
        ('CHECK_MEMORY', 'read', 'friction in pulleys', 'What did I note about friction in pulleys?'),
        ('CHECK_MEMORY', 'clarify', '', 'Show my saved notes about friction in pulleys before noon.'),
        ('CHECK_MEMORY', 'clarify', '', 'Show my saved notes about friction in pulleys with tag mechanics.'),
        ('UNKNOWN', 'clarify', '', 'Show my saved notes about friction in pulleys then delete them.'),
    ],
    'garden-log': [
        ('CHECK_MEMORY', 'read', 'seed stratification', 'Retrieve my memories regarding seed stratification.'),
        ('CHECK_MEMORY', 'read', 'seed stratification', 'Do I have any notes about seed stratification?'),
        ('CHECK_MEMORY', 'read', 'seed stratification', 'What have I written about seed stratification?'),
        ('CHECK_MEMORY', 'clarify', '', 'Retrieve my memories regarding seed stratification in 2025.'),
        ('CHECK_MEMORY', 'clarify', '', 'Retrieve my memories regarding seed stratification during June.'),
        ('UNKNOWN', 'clarify', '', 'Retrieve my memories regarding seed stratification or buy some seeds.'),
    ],
    'sound-design': [
        ('CHECK_MEMORY', 'read', 'experiments with resonators', 'Find my notes on experiments with resonators.'),
        ('CHECK_MEMORY', 'read', 'experiments with resonators', 'What did I save about experiments with resonators?'),
        ('CHECK_MEMORY', 'read', 'experiments with resonators', 'Search memory for experiments with resonators.'),
        ('CHECK_MEMORY', 'clarify', '', 'Find my notes on experiments with resonators at 5 pm.'),
        ('CHECK_MEMORY', 'clarify', '', 'Find only my newest notes on experiments with resonators.'),
        ('UNKNOWN', 'clarify', '', 'Find my notes on experiments with resonators and email them.'),
    ],
    'field-notebook': [
        ('CHECK_MEMORY', 'read', 'moss on limestone', 'Look up my records about moss on limestone.'),
        ('CHECK_MEMORY', 'read', 'moss on limestone', 'What do you know about moss on limestone?'),
        ('CHECK_MEMORY', 'read', 'moss on limestone', 'Show my details regarding moss on limestone.'),
        ('CHECK_MEMORY', 'clarify', '', 'Look up my records about moss on limestone older than a year.'),
        ('CHECK_MEMORY', 'clarify', '', 'Look up my records about moss on limestone sorted by date.'),
        ('UNKNOWN', 'clarify', '', 'Look up my records about moss on limestone also text them to Ira.'),
    ],
    'equipment-checkout': [
        ('WHAT_DID_I_PROMISE', 'read', 'Mihir Sen', 'Show my promises to Mihir Sen.'),
        ('WHAT_DID_I_PROMISE', 'read', 'Mihir Sen', 'What do I owe Mihir Sen?'),
        ('WHAT_DID_I_PROMISE', 'read', 'Mihir Sen', 'What did I promise to Mihir Sen?'),
        ('WHAT_DID_I_PROMISE', 'clarify', '', 'Show my pending promises to Mihir Sen.'),
        ('WHAT_DID_I_PROMISE', 'clarify', '', 'What do I owe Mihir Sen for the tripod?'),
        ('UNKNOWN', 'clarify', '', 'Show my promises to Mihir Sen and mark them done.'),
    ],
    'volunteer-commitments': [
        ('WHAT_DID_I_PROMISE', 'read', 'Lata', 'List promises for Lata.'),
        ('WHAT_DID_I_PROMISE', 'read', 'Lata', 'What have I promised Lata?'),
        ('WHAT_DID_I_PROMISE', 'read', 'Lata', 'What did I promise Lata?'),
        ('WHAT_DID_I_PROMISE', 'clarify', '', 'List promises for Lata this weekend.'),
        ('WHAT_DID_I_PROMISE', 'clarify', '', 'What have I promised my neighbour?'),
        ('UNKNOWN', 'clarify', '', 'List promises for Lata then remind me to finish them.'),
    ],
    'reference-boundaries': [
        ('CHECK_MEMORY', 'clarify', '', 'Find my notes about that thing.'),
        ('CHECK_MEMORY', 'clarify', '', "Find my notes about 'indigo dyeing'."),
        ('WHAT_DID_I_PROMISE', 'clarify', '', 'Show my promises to someone.'),
        ('WHAT_DID_I_PROMISE', 'clarify', '', 'Show my promises to The Station.'),
        ('UNKNOWN', 'clarify', '', 'If I ask for my notes about seed stratification, what happens?'),
        ('UNKNOWN', 'clarify', '', 'Find everything saved about moss on limestone.'),
    ],
}


def cases():
    result = []
    for family, items in FAMILIES.items():
        for index, (intent, disposition, reference, text) in enumerate(items, 1):
            entities = []
            if reference:
                start = text.index(reference)
                entities = [dict(type='MEMORY' if intent == 'CHECK_MEMORY' else 'PERSON', rawValue=reference,
                                 startOffset=len(text[:start].encode('utf-16-le')) // 2,
                                 endOffset=len(text[:start + len(reference)].encode('utf-16-le')) // 2)]
            result.append(dict(id=f'v7-dev-{family}-{index:02}', family_id=family, utterance=text,
                               intent=intent, disposition=disposition, entities=entities, source='assistant_authored',
                               human_authored=False, split='development', input_source='TEXT',
                               timestamp='2026-10-09T05:30:00Z', zone='Asia/Kolkata'))
    return result


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def binding_digest(path):
    # Kotlin checkouts may use CRLF on Windows. Bind source content, not checkout line endings.
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
    expectations = ['id\tintent\tdisposition\treference_base64']
    for row in rows:
        raw = row['entities'][0]['rawValue'] if row['entities'] else ''
        expectations.append('\t'.join((row['id'], row['intent'], row['disposition'],
                                       base64.b64encode(raw.encode()).decode())))
    (HERE / 'development-expectations.tsv').write_text('\n'.join(expectations) + '\n', encoding='utf-8', newline='\n')
    print(f'Wrote {len(rows)} assistant-authored development cases in {len(FAMILIES)} families; no approval or fresh evaluation.')


def manifest():
    paths = ['development-cases.json', 'development-input.tsv', 'development-expectations.tsv',
             'v7_candidate.py', 'v7_data.py']
    files = [HERE / path for path in paths]
    files += [HERE.parent / 'read_safety_v6/candidate.py']
    files += [ROOT / 'app/src/main/java/dev/metis/agent/domain/agent' / (name + '.kt') for name in
              ('ReadGrammarV7', 'ReadSafetyV7', 'ReadSafetyV6', 'EnglishRules', 'LanguageSpecialist',
               'LocalAgentOrchestrator')]
    payload = dict(version='phase15-read-coverage-v7', scope='Assistant-authored development; NOT independent evaluation',
                   rows=len(cases()), families=len(FAMILIES), human_authored=False, human_approved=False,
                   ml_enabled=False, ml_packaged=False,
                   provenance='New fictional scenario families authored from product/read-port requirements; shared language is possible.',
                   requirement_sources=['docs/PRODUCT_CONTRACT.md', 'docs/contracts/DATASET.md',
                                        'app/src/main/java/dev/metis/agent/data/storage/LocalAgentReads.kt'],
                   hashes={p.relative_to(ROOT).as_posix(): binding_digest(p) for p in files})
    (HERE / 'development-manifest-v7.json').write_text(json.dumps(payload, indent=2) + '\n',
                                                     encoding='utf-8', newline='\n')
    print('Bound v7 development sources; this is not human approval or a fresh benchmark freeze.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', action='store_true', help='Explicitly bind current development sources after fixing them')
    if parser.parse_args().manifest:
        manifest()
    else:
        build()
