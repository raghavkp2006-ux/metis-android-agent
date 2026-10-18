import json, pathlib
q = pathlib.Path(r'C:\antigravity_projects\metis-repo\ml\nlu_v1\datasets\quarantine\plan07-semantic-groups-002')
with open(q / 'semantic_review_groups.json') as f:
    groups = json.load(f)
print(f'GROUPS={len(groups)}')
for i, g in enumerate(groups[:5]):
    print(f'\nGROUP {i}: {g["group_id"]}')
    print(f'  rows={len(g["members"])}')
    print(f'  source={g["identity"]["source_dataset"]} intent={g["identity"]["source_intent"]}')
    print(f'  domain={g["identity"]["domain"]} action={g["identity"]["action"]} disposition={g["identity"]["disposition"]}')
    print(f'  family={g["identity"]["family"]} slot_pattern={",".join(g["identity"]["slot_pattern"])}')
    print(f'  anomaly={g["anomaly_count"]}')
    mid = g['members'][0]['row_id']
    with open(q / 'audit_input_rows.jsonl') as ar:
        for line in ar:
            r = json.loads(line)
            if r['id'] == mid:
                print(f'  first_text={r.get("text","")[:80]}')
                break