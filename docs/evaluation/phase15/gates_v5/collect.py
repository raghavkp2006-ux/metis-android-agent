"""Collect human truth without inferring it from the candidate being evaluated."""
import argparse
import csv
import io
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'read_queries_v5'))
import v5_review
from corpus import atomic_text, read_csv

FIELDS = ['id', 'intent', 'utterance', 'entity_text', 'entity_start_utf16',
          'filters_json', 'disposition', 'expected_response', 'reviewer',
          'approved', 'human_authored']


def write_csv(path, fields, rows):
    if path.exists():
        raise ValueError('Refusing to overwrite existing file')
    stream = io.StringIO(newline='')
    writer = csv.DictWriter(stream, fieldnames=fields, lineterminator='\n')
    writer.writeheader()
    writer.writerows(rows)
    atomic_text(path, stream.getvalue())


def prepare(path):
    write_csv(path, FIELDS, [dict(id=f'fresh-{i*25+j+1:03d}', intent=label)
              for i, label in enumerate(v5_review.LABELS) for j in range(25)])


def entities(text, label, raw, offset):
    if not raw:
        if offset:
            raise ValueError('Offset supplied without entity text')
        return []
    kind = {'CHECK_MEMORY': 'MEMORY', 'WHAT_DID_I_PROMISE': 'PERSON'}.get(label)
    if kind is None:
        raise ValueError('Entity text is supported only for memory/promise intents')
    starts = [i for i in range(len(text)) if text.startswith(raw, i)]
    offsets = [len(text[:i].encode('utf-16-le')) // 2 for i in starts]
    if offset:
        start = int(offset)
        if start not in offsets:
            raise ValueError('Entity offset does not match original text')
    elif len(offsets) == 1:
        start = offsets[0]
    else:
        raise ValueError('Entity must occur once, or supply its UTF-16 start offset')
    return [dict(type=kind, rawValue=raw, startOffset=start,
                 endOffset=start + len(raw.encode('utf-16-le')) // 2)]


def convert(source, output, reviewer):
    rows = []
    for row in read_csv(source):
        if (not reviewer.strip() or row['reviewer'] != reviewer or
                row['approved'] != 'yes' or row['human_authored'] != 'yes'):
            raise ValueError(f"{row['id']}: explicit authorship and reviewer approval required")
        label, disposition = row['intent'], row['disposition']
        if label not in v5_review.LABELS or disposition not in {'read', 'clarify', 'reject', 'unsupported'}:
            raise ValueError(f"{row['id']}: invalid intent/disposition")
        if not row['utterance'].strip() or not row['expected_response'].strip():
            raise ValueError(f"{row['id']}: query and expected response required")
        qualifiers = json.loads(row['filters_json'])
        if not isinstance(qualifiers, dict):
            raise ValueError('Filters must be a JSON object, including {} when absent')
        spans = entities(row['utterance'], label, row['entity_text'], row['entity_start_utf16'])
        if disposition == 'read':
            if label not in {'CHECK_TASKS', 'CHECK_MEMORY', 'WHAT_DID_I_PROMISE'}:
                raise ValueError('Unsupported read intent')
            if label != 'CHECK_TASKS' and not spans:
                raise ValueError('Memory/promise read requires a resolved entity')
        rows.append(dict(id=row['id'], utterance=row['utterance'],
                         reviewed_intent=label, reviewed_entities=json.dumps(spans, ensure_ascii=False),
                         reviewed_filters=json.dumps(qualifiers), reviewed_disposition=disposition,
                         expected_response=row['expected_response'], reviewer=reviewer,
                         approved=row['approved'], human_authored=row['human_authored']))
    write_csv(output, v5_review.FIELDS, rows)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('prepare'); p.add_argument('--output', type=Path, required=True)
    for command in ('convert', 'freeze'):
        p = sub.add_parser(command)
        p.add_argument('--input', type=Path, required=True)
        p.add_argument('--output', type=Path, required=True)
        p.add_argument('--reviewer', required=True)
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare(args.output)
    elif args.command == 'convert':
        convert(args.input, args.output, args.reviewer)
    else:
        v5_review.freeze(args.input, args.output, args.reviewer)
