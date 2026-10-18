Review Summary

- Semantic groups in human-review packet: 71 (all groups from plan07-semantic-groups-002)
- Exception groups in exception-review packet: 12 clusters (derived from 239 tickets indexing 2,029 rows)
- Rows represented in the packets:
  * Semantic groups: 460 rows (sum of member counts across all 71 groups)
  * Exception tickets: 2,029 rows indexed by the 239 tickets
- Private rows excluded: 0 (all groups have UNKNOWN_PRIVACY class; source policies permit local human review; no personal identifiers exposed)
- Exact file paths to open:
  1. `docs/nlu_v1/PLAN_07_HUMAN_REVIEW_PACKET.md` — group summaries with decision fields
  2. `docs/nlu_v1/PLAN_07_EXCEPTION_REVIEW_PACKET.md` — 12 exception clusters with decision fields
  3. `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/semantic_review_groups.json` — source JSON for 71 groups (read-only reference)
  4. `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/exception_review_tickets.json` — source JSON for 239 tickets (read-only reference)
  5. `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/group_approval_template.json` — approval sidecar format reference
  6. `docs/nlu_v1/GROUP_REVIEW.md` — human-review workflow and approval-sidecar specification
  7. `docs/nlu_v1/dataset_v0/README.md` — candidate-pool and release-gate overview

All files are read-only; no code, data, mappings, approvals, schemas, review states, or model files were altered. No approvals were auto-generated. No model was trained. Dataset v0 was not modified.