import json, pathlib
q = pathlib.Path(r'C:\antigravity_projects\metis-repo\ml\nlu_v1\datasets\quarantine\plan07-semantic-groups-002')
with open(q / 'exception_review_tickets.json') as f:
    tickets = json.load(f)
print(f'TICKETS={len(tickets)}')
for i, t in enumerate(tickets[:5]):
    eid = t['exception_id']
    reasons = t['reasons']
    source = t['source']
    intent = t['source_intent']
    row_ids = t['row_ids'][:3]
    indiv = t['individual_decisions_required']
    prot = t['protected']
    print(f'\nTICKET {i}: {eid}')
    print(f'  reasons={reasons}')
    print(f'  source={source} intent={intent}')
    print(f'  row_ids={row_ids}')
    print(f'  individual_decisions_required={indiv}')
    print(f'  protected={prot}')