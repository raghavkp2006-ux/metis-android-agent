"""Day 1 data contract. No training or network operations."""
import csv
import hashlib
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIELDS = 'author text style intent entities clauses needs_clarification supported_now context'.split()
KEYS = set('task new_name person project time from_time to_time priority duration anchor'.split())
WRITES = set('CALL_PERSON SEND_MESSAGE CREATE_TASK UPDATE_TASK COMPLETE_TASK DELETE_TASK POSTPONE_TASK CREATE_REMINDER CREATE_ALARM CREATE_TIMER CREATE_CALENDAR_EVENT CREATE_GOAL START_FOCUS STOP_FOCUS START_NAVIGATION SET_PREFERENCE'.split())


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def text_sha(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def dump(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False, allow_nan=False) + '\n', encoding='utf-8', newline='\n')


def intents():
    source = (ROOT / 'app/src/main/java/dev/metis/agent/domain/agent/AgentRequests.kt').read_text(encoding='utf-8')
    return set(re.findall(r'\b[A-Z][A-Z_]+\b', re.search(r'enum class AgentIntent\s*\{([^}]+)', source)[1]))


def normalize(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split())


def entities(value):
    result = {}
    if not value:
        return result
    for pair in value.split(';'):
        key, sep, val = pair.partition('=')
        if not sep or key not in KEYS or not val or key in result:
            raise ValueError('invalid/duplicate entity: ' + pair)
        result[key] = val
    return result


def clauses(row):
    if not row['clauses']:
        return [{'intent': row['intent'], 'entities': entities(row['entities'])}]
    result = []
    for clause in row['clauses'].split(' || '):
        intent, sep, values = clause.partition(':')
        if not sep:
            raise ValueError('clause missing colon')
        result.append({'intent': intent, 'entities': entities(values)})
    return result


def read_csv(path):
    with Path(path).open(encoding='utf-8-sig', newline='') as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != FIELDS:
            raise ValueError('CSV header must exactly match DATA_SCHEMA.md')
        rows = list(reader)
    if any(set(row) != set(FIELDS) or any(v is None for v in row.values()) for row in rows):
        raise ValueError('ragged CSV')
    return rows


def write_csv(path, rows):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, FIELDS, lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)


def issues(row, labels=None):
    labels = intents() if labels is None else labels
    errors = []
    if any(not row[key].strip() for key in ('author', 'text', 'style', 'intent')):
        errors.append('required field empty')
    if row['context'] not in ('none', 'fixture'):
        errors.append('context must be none or fixture')
    for key in ('needs_clarification', 'supported_now'):
        if row[key] not in ('yes', 'no'):
            errors.append(key + ' must be yes/no')
    try:
        parts = clauses(row)
        if parts[0] != {'intent': row['intent'], 'entities': entities(row['entities'])}:
            errors.append('first clause differs from primary intent/entities')
        for part in parts:
            if part['intent'] not in labels:
                errors.append('intent absent from code: ' + part['intent'])
            if part['intent'] == 'UNKNOWN' and part['entities']:
                errors.append('UNKNOWN entities must be empty')
            if part['intent'] == 'POSTPONE_TASK' and 'time' in part['entities']:
                errors.append('postpone target must use to_time')
            for key, val in part['entities'].items():
                if val not in row['text']:
                    errors.append('entity value not verbatim in text: ' + key + '=' + val)
    except ValueError as error:
        errors.append(str(error))
    return sorted(set(errors))
