"""Emit a local TRAIN-ONLY candidate pool using preserved Plan07 mappings. Fit nothing."""
import argparse
from collections import Counter
import json
from pathlib import Path
import tarfile

from ml.data.common import ROOT, dump, intents, sha
from ml.data.similarity import EVIDENCE, SNAPSHOT

# Translate preserved domain/action semantics only where a code enum exists.
BRIDGE = {('PERSON','READ'):'CHECK_PERSON', ('CALENDAR','READ'):'CHECK_CALENDAR',
          ('COMMUNICATION','SEND'):'SEND_MESSAGE', ('ALARM','SET'):'CREATE_ALARM',
          ('CALENDAR','CREATE'):'CREATE_CALENDAR_EVENT'}


def label(mapping):
    if mapping['decision'] == 'DROP':
        return None
    if mapping['decision'] == 'OOD':
        return 'UNKNOWN'
    if mapping['decision'] == 'KNOWN_UNSUPPORTED':
        return 'UNSUPPORTED'
    if mapping['decision'] != 'MAP':
        raise ValueError('unrecognized preserved mapping')
    result = BRIDGE.get((mapping['domain'], mapping['action']), 'UNSUPPORTED')
    if result not in intents():
        raise ValueError('bridge intent absent from code')
    return result


def build(output, report_path, evidence=EVIDENCE, snapshot=SNAPSHOT):
    import yaml
    maps = {key: yaml.safe_load((snapshot / 'ingestion/mappings' / (key + '_intent_mapping.yaml')).read_text(encoding='utf-8'))['intents']
            for key in ('massive', 'clinc')}
    counts, source_counts, excluded, decisions = Counter(), Counter(), Counter(), Counter()
    artifacts = {}
    locales = {}
    for directory, payload in [('v0_massive_1_1_en_us', 'source.tar.gz'), ('v0_clinc150_full', 'source.json')]:
        observation = json.loads((evidence / 'raw' / directory / 'observation.json').read_text())
        if sha(evidence / 'raw' / directory / payload) != observation['sha256']:
            raise ValueError('preserved public archive hash mismatch: ' + directory)
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation prevents accidental replacement of an earlier artifact.
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        def emit(source, source_id, text, original_intent, split, locale, force_oos=False):
            mapping = maps['massive' if source == 'MASSIVE' else 'clinc'].get(original_intent)
            if mapping is None:
                raise ValueError('unmapped source intent: ' + original_intent)
            intent = 'UNKNOWN' if force_oos else label(mapping)
            decisions[source + ':' + mapping['decision']] += 1
            if intent is None:
                excluded[source + ':DROP'] += 1
                return
            key = source + ':' + locale + ':' + split
            record = dict(partition='TRAIN', source=source, source_id=str(source_id), original_split=split,
                          locale=locale, original_intent=original_intent, text=text, intent=intent, entities={},
                          mapping=mapping, training_eligible_candidate=True, row_review='NOT_REVIEWED',
                          execution_authority=False)
            stream.write(json.dumps(record, sort_keys=True, ensure_ascii=False) + '\n')
            counts[intent] += 1
            source_counts[key] += 1

        archive = evidence / 'raw/v0_massive_1_1_en_us/source.tar.gz'
        artifacts[str(archive.relative_to(ROOT))] = sha(archive)
        with tarfile.open(archive, 'r:gz') as tar:
            members = {Path(m.name).name:m for m in tar.getmembers() if m.isfile()}
            for locale in ('en-US', 'hi-IN', 'ta-IN'):
                member = members.get(locale + '.jsonl')
                locales[locale] = 'present' if member else 'not found'
                if member is None:
                    continue
                for raw in tar.extractfile(member):
                    row = json.loads(raw)
                    if row['locale'] != locale:
                        raise ValueError('locale binding mismatch')
                    if row['partition'] != 'train':
                        excluded['MASSIVE:' + locale + ':' + row['partition']] += 1
                        continue
                    emit('MASSIVE', row['id'], row['utt'], row['intent'], 'train', locale)
        source = evidence / 'raw/v0_clinc150_full/source.json'
        artifacts[str(source.relative_to(ROOT))] = sha(source)
        data = json.loads(source.read_text(encoding='utf-8'))
        for split, rows in data.items():
            if split not in ('train', 'oos_train'):
                excluded['CLINC150:' + split] += len(rows)
                continue
            for i, (text, original_intent) in enumerate(rows):
                emit('CLINC150', i, text, original_intent, split, 'en', force_oos=split == 'oos_train')
    mappings = {str((snapshot / 'ingestion/mappings' / (key + '_intent_mapping.yaml')).relative_to(ROOT)):
                sha(snapshot / 'ingestion/mappings' / (key + '_intent_mapping.yaml')) for key in maps}
    report = dict(partition='TRAIN_ONLY', trained=False, total=sum(counts.values()), per_intent=dict(counts),
                  per_source=dict(source_counts), excluded=dict(excluded), locales=locales, decisions=dict(decisions),
                  input_sha256=artifacts, mapping_sha256=mappings, output=str(output), output_sha256=sha(output),
                  source_attribution={'MASSIVE':'Amazon MASSIVE 1.1, CC-BY-4.0; changes: train selection and METIS intent bridge',
                                      'CLINC150':'CLINC oos-eval, CC-BY-3.0; changes: train/oos_train selection and METIS intent bridge'},
                  bridge={domain + ':' + action: intent for (domain, action), intent in BRIDGE.items()},
                  limitations=['Preserved mappings are provisional; row-level semantic/privacy review still required.',
                               'Empty entities mean slots not imported; this is an intent candidate pool, not a slot gold set.',
                               'Unsupported mappings retained as UNSUPPORTED, never silently converted into supported intents.'])
    dump(report_path, report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.output, args.report)['per_source'], sort_keys=True))


if __name__ == '__main__':
    main()
