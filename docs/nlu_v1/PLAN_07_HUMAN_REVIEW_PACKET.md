# METIS NLU v1 Plan 07 Human Review Packet

**Generated from**: `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/`
**Total semantic groups**: 71
**Rows represented**: 460 (sum of member counts across all groups)
**Private rows excluded**: 0 (all groups have `UNKNOWN_PRIVACY` class; source policies permit local human review)

---

## Semantic Group Summaries

| # | Group ID | Rows | Source Dataset | Source Intent | Domain | Action | Disposition | Family | Slot Pattern | Anomalies | Representative Utterance |
|---|----------|------|---------------|--------------|--------|--------|-------------|--------|--------------|-----------|--------------------------|
| 0 | semantic-group:9948d6522fb76f695030399aae90e88e866f0b2e1c94485d6314fd6e3dd7f75f | 8 | metis_controlled_synthetic | knowledge_context | KNOWLEDGE | READ | SUPPORTED_NEEDS_CONTEXT | coverage:knowledge_context | REFERENCE | 0 | `show the other knowledge note` |
| 1 | semantic-group:86830203e41d1abb794f5b2f80c59c382f5bb2355da5142fc21afc44292f49c9 | 1 | massive_1_1_en_us | alarm_remove | ALARM | DELETE | SUPPORTED_COMPLETE | alarm_delete |  | 0 | `cancel all alarms` |
| 2 | semantic-group:da689020d9214e73cabd83d897dc7f4b6b425d17f9ed7e9a3ff357ba07342afa | 4 | metis_controlled_synthetic | focus_ood | UNKNOWN | NONE | OUT_OF_DOMAIN | coverage:focus_ood | DURATION | 0 | `buy a focus training subscription for twenty minutes` |
| 3 | semantic-group:62f8cf6d6b3daaec90ad22b40045046089c67733428540a92bfd9bcf8900c592 | 6 | massive_1_1_en_us | alarm_set | ALARM | SET | SUPPORTED_COMPLETE | alarm_set | TIME | 0 | `i need to get up at seven am` |
| 4 | semantic-group:59f20fe0ba1c45894265c9cbabd8be9483149b7197126548c0e2834d69723f93 | 4 | metis_controlled_synthetic | knowledge_ood | UNKNOWN | NONE | OUT_OF_DOMAIN | coverage:knowledge_ood | SUBJECT | 0 | `buy a textbook about homework` |
| 5 | semantic-group:c1e781b20a09231a4f79677087cc9b9aff3767129c377e845c1c4be60fe607bf | 5 | massive_1_1_en_us | calendar_query | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | coverage:calendar_query | DATE | 0 | `when am i supposed to meet barbara for lunch this week` |
| 6 | semantic-group:0e0c4d2c9fd9f0b9736ba7ea8cdc71b6101a0e35ccab6c84b067bb0e3381c737 | 4 | massive_1_1_en_us | calendar_event | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | coverage:calendar_event | DATE | 0 | `show my calendar events for tomorrow` |
| 7 | semantic-group:1480f5f3c6ed9f168e6db119517836b9fd95318db9d1181c60556ac23aaaba87 | 4 | massive_1_1_en_us | person_contact | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | coverage:person_contact | PERSON | 0 | `show my emails for me` |
| 8 | semantic-group:8e71dfcc7a162f8a40e782c5a5e3c9ba5260fc20f27b5c08d8d2d1754ebe93c8 | 4 | massive_1_1_en_us | communication_send | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | coverage:communication_send | REFERENCE | 0 | `send an email to john` |

*Table continues for all 71 groups. See the full JSON at `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/semantic_review_groups.json` for complete member row IDs and hashes.*

---

## Decision Fields

For each group, fill in exactly one decision:

```
Decision: PENDING / APPROVE / REJECT / CHANGE
```

- **PENDING** — No decision recorded yet; group remains in the external human approval queue.
- **APPROVE** — The group passes all semantic integrity checks and is ready for external signed approval (see `group_approval_template.json` format). The verifier requires `decision == 'APPROVE'` and a valid Ed25519 signature.
- **REJECT** — The group is determined to be unsupported or erroneous; it will not receive external approval. Note: the automated verifier only accepts `APPROVE` as a valid decision value; `REJECT` must be recorded in a separate sidecar, not in the signed approval template.
- **CHANGE** — The group requires corrections before approval. If CHANGE is selected, provide the corrected fields below.

### CHANGE Corrections (only if Decision = CHANGE)

| Field | Description | Example |
|---|---|---|
| corrected_domain | The corrected NLU domain | `CALENDAR` |
| corrected_action | The corrected action | `READ` |
| corrected_disposition | The corrected disposition | `SUPPORTED_COMPLETE` |
| corrected_family | The corrected family assignment | `coverage:calendar_query` |
| reviewer_note | Free‑form note justifying the change | `Family mis‑aligned with candidate template` |

---

## Review Order

1. Open `docs/nlu_v1/PLAN_07_HUMAN_REVIEW_PACKET.md` and inspect the group summaries.
2. For each group, decide on `PENDING / APPROVE / REJECT / CHANGE`.
3. If `CHANGE`, fill the correction fields and reviewer note.
4. Record the decision in a **separate signed sidecar** (the `group_approval_template.json` format); do **not** edit that file directly.
5. Move to the next group.

---

*This packet is read‑only. It does not modify `semantic_review_groups.json`, row review states, or any model data. Representative utterances are drawn from rows whose privacy/source policy permits local human review (all groups in this quarantine run have `UNKNOWN_PRIVACY` with `intake_approved: false` and `publication_allowed: false`; no personal identifiers are exposed).*