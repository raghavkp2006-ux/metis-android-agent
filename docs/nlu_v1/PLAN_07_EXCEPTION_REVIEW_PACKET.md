# METIS NLU v1 Plan 07 Exception Review Packet

Derived, review-only documentation of `plan07-semantic-groups-002`. Original tickets, rows, semantic groups, approvals and schemas are unchanged.

**Unique tickets: 239. Unique affected rows: 2029. Row references: 2029. Disjoint primary clusters: 93.**

Ticket–reason occurrences: **239**. Unique row–reason occurrences: **2029**. These are separate metrics, not extra tickets.

Multi-reason tickets: **0**. Rows in multiple current reason categories: **0**. Rows referenced by multiple tickets: **0**.

## Interpretation and decision safeguards

Every ticket has exactly one primary cluster. Primary-reason precedence is protection/malformed records, privacy/ambiguous slots, second actions, temporal ambiguity, family/reference uncertainty, wording/support, then historical templates; ties use lexical order. Other current codes remain secondary tags. Older curation reasons are not merged into current exception categories.

Clusters are navigation worksets, **not blanket approval units**. Topics are explicit source-intent catalogs, further separated by source/version, primary reason, mapping versions, domain/action/disposition and support boundary. Exact intent/family/slot-pattern subgroups remain separate in the index. Mixed original tickets are retained once and subdivided by row for navigation. No incompatible meanings are certified equivalent.

No cluster is marked GROUP_DECISION_SAFE without proof of semantic equivalence. SUBGROUP_REQUIRED means inspect the listed subgroup signatures; those subgroups still require individual decisions where their wording/privacy/template exception remains unresolved. INDIVIDUAL_REVIEW_REQUIRED forbids applying one header decision to every row. A smaller navigation packet does not erase the unresolved human workload.

**APPROVE / REJECT / CHANGE are draft review findings only.** They do not approve a row, release quarantine, waive privacy/license/duplicate gates or replace the external signed approval workflow. All decision fields below remain PENDING.

## Text-display policy

This packet and index live in versionable documentation. Dataset licensing alone does not clear privacy. [Plan 04 policy](../../ml/nlu_v1/data_management/README.md) states that UNKNOWN_PRIVACY blocks public export; [.gitignore](../../.gitignore) keeps working text local pending reviewed export. The unrelated human-review Markdown is not evidence of display permission.

Text excluded for **2029** affected rows; **0** rows have bound PUBLIC privacy, explicit publication permission and matching verified source/license evidence. Private/restricted rows: 0; UNKNOWN_PRIVACY rows: 2029.

Where permission is absent, representative records show safe labels and opaque provenance references, with utterance and slot text withheld. They are **not invented/redacted paraphrases**. A reviewer must obtain permitted access in the controlled local data workflow before deciding semantics. Hashes are provenance references, not anonymization. For clusters with fewer than three actual members, every available member is shown; no examples are fabricated.

## Current reason counts

| Reason | Unique tickets | Unique affected rows | Row-reference occurrences |
|---|---:|---:|---:|
| HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | 10 | 25 | 25 |
| PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | 34 | 871 | 871 |
| TEMPORAL_CONTEXT_UNRESOLVED | 6 | 41 | 41 |
| UNEXPECTED_SECOND_ACTION | 3 | 11 | 11 |
| WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | 186 | 1081 | 1081 |

The older machine-curation queue had 425 tickets / 1968 rows, 4026 row–reason occurrences and 1941 multi-reason rows. Its affected-row counts below overlap; they are not this disjoint ticket partition.

| Older curation reason (not a current exception cluster) | Affected rows, overlapping |
|---|---:|
| AMBIGUOUS_FAMILY | 17 |
| DUPLICATE_SKELETON_SOURCE_ID_OR_SIMILARITY_REVIEW | 112 |
| NO_DETERMINISTIC_FAMILY | 13 |
| PRIVACY_FREE_TEXT_OR_PERSONAL_SLOT_UNRESOLVED | 6 |
| PRIVACY_NONPERSONAL_PROOF_INCOMPLETE | 1939 |
| SEMANTICS_NOT_PROVEN_BY_MACHINE_POLICY | 1588 |
| TEMPORAL_CONTEXT_UNRESOLVED | 237 |
| UNRESOLVED_REFERENCE | 114 |

## Primary cluster index

| Cluster | Tickets | Rows | Topic | Primary reason | Required scope |
|---|---:|---:|---|---|---|
| [EXC-012e0cef0b888845](#exc-012e0cef0b888845) | 1 | 178 | source_scenario:calendar | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-03c7f01ec71b3088](#exc-03c7f01ec71b3088) | 1 | 21 | source_scenario:alarm | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-0461848934dc2de0](#exc-0461848934dc2de0) | 1 | 1 | template:promise_read_person:complete | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-068c526b3c69b6f3](#exc-068c526b3c69b6f3) | 1 | 72 | source_scenario:alarm | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-06bda62782be811a](#exc-06bda62782be811a) | 9 | 12 | account_information | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-0a2b2c4e100490e0](#exc-0a2b2c4e100490e0) | 1 | 1 | source_scenario:recommendation | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-0a4e41f98898a7fa](#exc-0a4e41f98898a7fa) | 1 | 1 | source_scenario:general | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-0bcdcc5155e23edc](#exc-0bcdcc5155e23edc) | 1 | 45 | source_scenario:email | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-138e0a9bd4837c3d](#exc-138e0a9bd4837c3d) | 1 | 1 | template:task_read_subject_date_status:delete | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-177ea4b04c1b0bae](#exc-177ea4b04c1b0bae) | 1 | 9 | source_scenario:alarm | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-18ae4845109a0f26](#exc-18ae4845109a0f26) | 1 | 1 | lists_and_orders | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-18e6994f5ee74679](#exc-18e6994f5ee74679) | 2 | 2 | source_scenario:social | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-226f3ca8e3636d84](#exc-226f3ca8e3636d84) | 1 | 1 | personal_assistant_operations | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-2c5976ac6c364cf9](#exc-2c5976ac6c364cf9) | 1 | 1 | personal_assistant_operations | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-2cf6ce467ff4ed5a](#exc-2cf6ce467ff4ed5a) | 1 | 62 | source_scenario:email | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-2d92a4f1dae72c46](#exc-2d92a4f1dae72c46) | 1 | 47 | source_scenario:alarm | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-2ee1471b4e601c3e](#exc-2ee1471b4e601c3e) | 4 | 4 | travel_information | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-2fb9a1a4646b4d6d](#exc-2fb9a1a4646b4d6d) | 1 | 2 | personal_assistant_operations | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-3189941bea1a25a5](#exc-3189941bea1a25a5) | 10 | 10 | employment_tax_and_benefits | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-38aa11e5a94001e3](#exc-38aa11e5a94001e3) | 4 | 4 | source_scenario:audio | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-3a2d2983a75f84e2](#exc-3a2d2983a75f84e2) | 3 | 3 | source_scenario:lists | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-3a9826bf14f7afda](#exc-3a9826bf14f7afda) | 1 | 1 | source_scenario:news | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-43191a126dbdb2a6](#exc-43191a126dbdb2a6) | 1 | 2 | source_scenario:email | UNEXPECTED_SECOND_ACTION | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-437b4eb7811456d7](#exc-437b4eb7811456d7) | 1 | 6 | template:memory_read_subject | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-45deb732e794f222](#exc-45deb732e794f222) | 1 | 19 | source_scenario:email | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-4b57863607136348](#exc-4b57863607136348) | 1 | 6 | template:promise_read_person | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-51b2b853646c513e](#exc-51b2b853646c513e) | 2 | 2 | source_scenario:general | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-558abdade0c89d07](#exc-558abdade0c89d07) | 1 | 1 | template:memory_read_subject:compound | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-57b9c10ceabb14b2](#exc-57b9c10ceabb14b2) | 1 | 107 | source_scenario:calendar | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-582cc5206f393966](#exc-582cc5206f393966) | 1 | 58 | source_scenario:alarm | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-5a07d3b0df7c9485](#exc-5a07d3b0df7c9485) | 9 | 16 | travel_booking | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-655fce4dda08976a](#exc-655fce4dda08976a) | 1 | 1 | intent:user_name | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-6a4b453cc71fa113](#exc-6a4b453cc71fa113) | 12 | 16 | information_queries | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-6a9a408e502952cf](#exc-6a9a408e502952cf) | 10 | 12 | vehicle_and_fuel | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-6ad5e645f17bd2fe](#exc-6ad5e645f17bd2fe) | 2 | 2 | personal_assistant_operations | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-6b06f3e6502a5e03](#exc-6b06f3e6502a5e03) | 2 | 3 | personal_assistant_operations | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-6b60b66746c251b7](#exc-6b60b66746c251b7) | 5 | 6 | dialogue_control | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-6b725b7c7cc72c7c](#exc-6b725b7c7cc72c7c) | 4 | 4 | source_scenario:music | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-6e20356b6b978d63](#exc-6e20356b6b978d63) | 3 | 4 | games_and_entertainment | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-6fee95d737e810bf](#exc-6fee95d737e810bf) | 1 | 1 | template:promise_read_person:compound | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-726286e772fecf73](#exc-726286e772fecf73) | 7 | 10 | financial_commands_and_billing | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-765b28cb5d610f04](#exc-765b28cb5d610f04) | 7 | 11 | location_and_transport | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-77b7dfb86433081f](#exc-77b7dfb86433081f) | 8 | 11 | card_and_credit_information | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-78030074234df0ab](#exc-78030074234df0ab) | 2 | 2 | source_scenario:datetime | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-78b62deb245f2704](#exc-78b62deb245f2704) | 1 | 65 | source_scenario:calendar | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-7b84c4ed7597865a](#exc-7b84c4ed7597865a) | 1 | 33 | unclassified_source_oos | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-7e7fac4071d131bc](#exc-7e7fac4071d131bc) | 3 | 4 | information_queries | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-7f58b993d82d84f0](#exc-7f58b993d82d84f0) | 1 | 102 | source_scenario:alarm | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-82899b499f8a4f2c](#exc-82899b499f8a4f2c) | 4 | 5 | restaurant_transactions | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-82f131883e976e6b](#exc-82f131883e976e6b) | 2 | 2 | source_scenario:play | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-832b9bb54de258e7](#exc-832b9bb54de258e7) | 2 | 2 | source_scenario:recommendation | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-875c830ea2c8cd90](#exc-875c830ea2c8cd90) | 8 | 11 | food_and_cooking | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-8767b8d4aadfcba4](#exc-8767b8d4aadfcba4) | 1 | 1 | source_scenario:alarm | TEMPORAL_CONTEXT_UNRESOLVED | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-894d8c362e7d71f6](#exc-894d8c362e7d71f6) | 4 | 4 | lists_and_orders | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-8ff4fbc5d92e3d5c](#exc-8ff4fbc5d92e3d5c) | 1 | 1 | personal_assistant_operations | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-90acaec4b09f8204](#exc-90acaec4b09f8204) | 1 | 21 | source_scenario:alarm | TEMPORAL_CONTEXT_UNRESOLVED | SUBGROUP_REQUIRED |
| [EXC-91c03d51610c6761](#exc-91c03d51610c6761) | 1 | 8 | source_scenario:alarm | TEMPORAL_CONTEXT_UNRESOLVED | SUBGROUP_REQUIRED |
| [EXC-9237b6f8db1fe5db](#exc-9237b6f8db1fe5db) | 1 | 1 | personal_assistant_operations | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-930e2b581d69703b](#exc-930e2b581d69703b) | 8 | 11 | card_security_and_replacement | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-9ca0ede7199e9008](#exc-9ca0ede7199e9008) | 2 | 2 | source_scenario:play | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-9e21d5dc338dfe11](#exc-9e21d5dc338dfe11) | 1 | 5 | source_scenario:calendar | TEMPORAL_CONTEXT_UNRESOLVED | SUBGROUP_REQUIRED |
| [EXC-a12476addbf041c5](#exc-a12476addbf041c5) | 1 | 1 | personal_assistant_operations | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-a463b1755e4470b4](#exc-a463b1755e4470b4) | 1 | 1 | source_scenario:alarm | UNEXPECTED_SECOND_ACTION | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-a48003a21909be50](#exc-a48003a21909be50) | 1 | 8 | source_scenario:calendar | UNEXPECTED_SECOND_ACTION | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-a5adebdce4c726ea](#exc-a5adebdce4c726ea) | 1 | 1 | source_scenario:transport | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-ab444d02c953db80](#exc-ab444d02c953db80) | 1 | 1 | source_scenario:cooking | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-ac9f1455fb252d64](#exc-ac9f1455fb252d64) | 1 | 154 | source_scenario:calendar | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-b38d715efbc4ee77](#exc-b38d715efbc4ee77) | 1 | 82 | source_scenario:calendar | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-b5bed18b8c197555](#exc-b5bed18b8c197555) | 1 | 137 | source_scenario:calendar | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-beef4596ff3df7ee](#exc-beef4596ff3df7ee) | 12 | 15 | assistant_identity_and_smalltalk | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-c6569f75b939c72e](#exc-c6569f75b939c72e) | 2 | 2 | source_scenario:qa | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-c68cb09dce60ad0f](#exc-c68cb09dce60ad0f) | 1 | 6 | template:task_read_subject_date_status | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-ce7df8c207be0275](#exc-ce7df8c207be0275) | 5 | 5 | source_scenario:iot | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-d176e6422284a976](#exc-d176e6422284a976) | 1 | 2 | source_scenario:calendar | TEMPORAL_CONTEXT_UNRESOLVED | SUBGROUP_REQUIRED |
| [EXC-d3cabfa13938d21e](#exc-d3cabfa13938d21e) | 1 | 127 | source_scenario:email | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-d48864e3fd16de46](#exc-d48864e3fd16de46) | 1 | 2 | personal_assistant_operations | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-d6e310823d148efb](#exc-d6e310823d148efb) | 4 | 4 | media | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-dcfa7accc70cb9ac](#exc-dcfa7accc70cb9ac) | 1 | 1 | source_scenario:cooking | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-dd62f215c1f7b637](#exc-dd62f215c1f7b637) | 1 | 186 | source_scenario:email | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-de762b54ffd733fb](#exc-de762b54ffd733fb) | 1 | 1 | template:memory_read_subject:delete | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-e0d94872b0d03dc0](#exc-e0d94872b0d03dc0) | 1 | 1 | template:task_read_subject_date_status:compound | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-e6006b10ee060c37](#exc-e6006b10ee060c37) | 1 | 4 | source_scenario:calendar | TEMPORAL_CONTEXT_UNRESOLVED | SUBGROUP_REQUIRED |
| [EXC-ed475c19a91b5f7c](#exc-ed475c19a91b5f7c) | 1 | 33 | lists_and_orders | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-eed30f6e9c8ce250](#exc-eed30f6e9c8ce250) | 1 | 119 | source_scenario:email | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-f11206d48ac439f9](#exc-f11206d48ac439f9) | 2 | 2 | source_scenario:takeaway | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-f2233009b28da88d](#exc-f2233009b28da88d) | 1 | 1 | template:task_read_subject_date_status:complete | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-f5869d0598226a41](#exc-f5869d0598226a41) | 3 | 3 | source_scenario:transport | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-f694f896cbd7345e](#exc-f694f896cbd7345e) | 4 | 4 | source_scenario:iot | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-f79872a643c12e8d](#exc-f79872a643c12e8d) | 9 | 15 | device_and_assistant_preferences | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | SUBGROUP_REQUIRED |
| [EXC-f849c0966a1fdb2e](#exc-f849c0966a1fdb2e) | 1 | 20 | source_scenario:email | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-fab071a9b496798c](#exc-fab071a9b496798c) | 1 | 1 | source_scenario:weather | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | INDIVIDUAL_REVIEW_REQUIRED |
| [EXC-fc5112ca611105b0](#exc-fc5112ca611105b0) | 3 | 3 | source_scenario:qa | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT | SUBGROUP_REQUIRED |
| [EXC-ff22573b55a5eb25](#exc-ff22573b55a5eb25) | 1 | 30 | source_scenario:email | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN | INDIVIDUAL_REVIEW_REQUIRED |

## EXC-012e0cef0b888845

- Unique tickets: **1**; unique rows: **178**; ticket–reason occurrences: 1; row–reason occurrences: 178.
- Sources: massive_1_1_en_us.
- Source intents: calendar_set.
- Current domain / action / disposition / OOD patterns: CALENDAR/CREATE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): calendar_create=178.
- Slot patterns (unique rows): DATE=2; DATE + EVENT=3; DATE + EVENT + DATE=1; DATE + EVENT + LOCATION=1; DATE + EVENT + PERSON=3; DATE + EVENT + TIME=1; DATE + KIND=2; DATE + LOCATION=1; DATE + LOCATION + EVENT=1; DATE + PERSON + EVENT=3; DATE + RECURRENCE + REFERENCE + EVENT=1; DATE + REFERENCE=1; DATE + TIME=2; DATE + TIME + EVENT=2; DATE + TIME + LOCATION=1; EVENT=13; EVENT + DATE=12; EVENT + DATE + LOCATION=2; EVENT + DATE + PERSON + TIME=2; EVENT + DATE + RECURRENCE=1; EVENT + DATE + TIME=10; EVENT + DATE + TIME + EVENT=1; EVENT + DATE + TIME + LOCATION=1; EVENT + DATE + TIME + TIME=2; EVENT + EVENT + TIME=1; EVENT + LOCATION=3; EVENT + LOCATION + DATE=3; EVENT + LOCATION + DATE + TIME=2; EVENT + LOCATION + TIME=1; EVENT + PERSON=1; EVENT + PERSON + DATE=5; EVENT + PERSON + DATE + TIME=8; EVENT + PERSON + DATE + TIME + TIME + KIND=1; EVENT + PERSON + LOCATION=1; EVENT + PERSON + PERSON=1; EVENT + PERSON + TIME + DATE=3; EVENT + PERSON + TIME + TIME=1; EVENT + RECURRENCE=2; EVENT + RECURRENCE + TIME=2; EVENT + RECURRENCE + TIME + DATE=1; EVENT + REFERENCE=2; EVENT + REFERENCE + DATE=3; EVENT + REFERENCE + DATE + TIME=1; EVENT + REFERENCE + TIME + DATE=1; EVENT + TIME=9; EVENT + TIME + DATE=4; EVENT + TIME + DATE + PERSON=1; EVENT + TIME + LOCATION + PERSON=1; EVENT + TIME + PERSON=1; EVENT + TIME + TIME=3; EVENT + TIME + TIME + LOCATION=1; KIND + DATE + TIME + PERSON + LOCATION=2; KIND + PERSON + DATE + TIME=1; KIND + REFERENCE + DATE=1; KIND + REFERENCE + DATE + DATE=1; KIND + REFERENCE + TIME=1; LOCATION=1; LOCATION + DATE=2; LOCATION + DATE + TIME=1; PERSON=1; PERSON + DATE + TIME=1; PERSON + EVENT=1; PERSON + EVENT + DATE=2; PERSON + EVENT + DATE + TIME=1; PERSON + PERSON + EVENT=1; PERSON + PERSON + EVENT + TIME + DATE=1; RECURRENCE=4; RECURRENCE + EVENT=1; RECURRENCE + PERSON + EVENT=1; RECURRENCE + TIME + EVENT=1; RECURRENCE + TIME + PERSON=1; REFERENCE=1; REFERENCE + DATE + EVENT=2; REFERENCE + EVENT=1; REFERENCE + EVENT + DATE=2; REFERENCE + EVENT + LOCATION + DATE=1; REFERENCE + EVENT + RECURRENCE=1; REFERENCE + EVENT + TIME=2; TIME=2; TIME + DATE=1; TIME + EVENT=3; TIME + EVENT + PERSON=1; TIME + EVENT + RECURRENCE=1; TIME + EVENT + REFERENCE=1; TIME + TIME=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-012e0cef0b888845`. Content binding: `a845a85ef2d5872c784fffc73c0a86688f5ac3ff30db79bfb0a598611ea36fbc`.
- Decision subgroups: 85; blanket cluster decisions disabled. `EXC-012e0cef0b888845-S-0048870b6915` (1 rows) `EXC-012e0cef0b888845-S-036c0ad54d2e` (1 rows) `EXC-012e0cef0b888845-S-05cb0ece8eb2` (2 rows) `EXC-012e0cef0b888845-S-0ab1e6395447` (1 rows) `EXC-012e0cef0b888845-S-0b721398fee1` (10 rows) `EXC-012e0cef0b888845-S-0bcb9715f18b` (12 rows) `EXC-012e0cef0b888845-S-0e124cd2dcd8` (3 rows) `EXC-012e0cef0b888845-S-10491d480910` (1 rows) `EXC-012e0cef0b888845-S-1116d2d49a69` (1 rows) `EXC-012e0cef0b888845-S-131aba58d271` (2 rows) `EXC-012e0cef0b888845-S-137e971970ac` (1 rows) `EXC-012e0cef0b888845-S-1482f825b061` (1 rows) `EXC-012e0cef0b888845-S-1497a1cd40e0` (1 rows) `EXC-012e0cef0b888845-S-14de5005cfc4` (3 rows) `EXC-012e0cef0b888845-S-177400d320a1` (9 rows) `EXC-012e0cef0b888845-S-1c8c0091b5f6` (3 rows) `EXC-012e0cef0b888845-S-1e6b409e2cd4` (3 rows) `EXC-012e0cef0b888845-S-1e92956efd2a` (1 rows) `EXC-012e0cef0b888845-S-250f4c863abf` (1 rows) `EXC-012e0cef0b888845-S-2f298faed947` (1 rows) `EXC-012e0cef0b888845-S-321d703cddbc` (1 rows) `EXC-012e0cef0b888845-S-3509a4d1f5c4` (1 rows) `EXC-012e0cef0b888845-S-3617233c59ed` (2 rows) `EXC-012e0cef0b888845-S-36d979dd8b0b` (1 rows) `EXC-012e0cef0b888845-S-3a4d68230847` (1 rows) `EXC-012e0cef0b888845-S-3ddd557cdbe5` (1 rows) `EXC-012e0cef0b888845-S-40453877b23d` (2 rows) `EXC-012e0cef0b888845-S-40611d879267` (1 rows) `EXC-012e0cef0b888845-S-41e66046cac8` (1 rows) `EXC-012e0cef0b888845-S-41f208c23be7` (1 rows) `EXC-012e0cef0b888845-S-46f3162fc3ef` (2 rows) `EXC-012e0cef0b888845-S-493bf4996038` (2 rows) `EXC-012e0cef0b888845-S-4df8ab9f3f18` (1 rows) `EXC-012e0cef0b888845-S-4fdb73be8b61` (1 rows) `EXC-012e0cef0b888845-S-533d5d1d2fd5` (1 rows) `EXC-012e0cef0b888845-S-54bfeba9691f` (1 rows) `EXC-012e0cef0b888845-S-560f8589c739` (1 rows) `EXC-012e0cef0b888845-S-5816cedde474` (3 rows) `EXC-012e0cef0b888845-S-5af11574d662` (2 rows) `EXC-012e0cef0b888845-S-5c1bd080b253` (1 rows) `EXC-012e0cef0b888845-S-63f14e8c45ab` (5 rows) `EXC-012e0cef0b888845-S-647bed931ac0` (1 rows) `EXC-012e0cef0b888845-S-6525aba7df20` (2 rows) `EXC-012e0cef0b888845-S-6691041e51b8` (1 rows) `EXC-012e0cef0b888845-S-68d40c8f2022` (1 rows) `EXC-012e0cef0b888845-S-6b0116d557ee` (1 rows) `EXC-012e0cef0b888845-S-74b008dea3b2` (2 rows) `EXC-012e0cef0b888845-S-7862d8353cdf` (3 rows) `EXC-012e0cef0b888845-S-7ac921020748` (2 rows) `EXC-012e0cef0b888845-S-7c787f8e5ddf` (1 rows) `EXC-012e0cef0b888845-S-85040dd25222` (1 rows) `EXC-012e0cef0b888845-S-88f9713dc71b` (1 rows) `EXC-012e0cef0b888845-S-88fc4e72d7b7` (1 rows) `EXC-012e0cef0b888845-S-8ec35925cf57` (1 rows) `EXC-012e0cef0b888845-S-9757d48514f4` (1 rows) `EXC-012e0cef0b888845-S-9b4600dcbd0a` (2 rows) `EXC-012e0cef0b888845-S-9dae73ece888` (2 rows) `EXC-012e0cef0b888845-S-9dccd3412dcd` (1 rows) `EXC-012e0cef0b888845-S-a11625d40782` (1 rows) `EXC-012e0cef0b888845-S-a15531abe4b3` (1 rows) `EXC-012e0cef0b888845-S-a36d0eb6abef` (2 rows) `EXC-012e0cef0b888845-S-a584c7081c03` (1 rows) `EXC-012e0cef0b888845-S-a77ca7a0af07` (1 rows) `EXC-012e0cef0b888845-S-a8ed0878be32` (1 rows) `EXC-012e0cef0b888845-S-b5a5d8ec5ac2` (1 rows) `EXC-012e0cef0b888845-S-b7407922f46f` (3 rows) `EXC-012e0cef0b888845-S-c0a56bae9f50` (1 rows) `EXC-012e0cef0b888845-S-c0c073859fae` (1 rows) `EXC-012e0cef0b888845-S-cafbb423f90c` (1 rows) `EXC-012e0cef0b888845-S-cb1ca0a2beb8` (8 rows) `EXC-012e0cef0b888845-S-cf00d6733901` (4 rows) `EXC-012e0cef0b888845-S-d08e2f4f2987` (2 rows) `EXC-012e0cef0b888845-S-dd8931deb8b7` (2 rows) `EXC-012e0cef0b888845-S-de9619b71222` (1 rows) `EXC-012e0cef0b888845-S-df43411149a9` (3 rows) `EXC-012e0cef0b888845-S-e09b5c158bbc` (13 rows) `EXC-012e0cef0b888845-S-e0fc02af8241` (3 rows) `EXC-012e0cef0b888845-S-e1a27b60eb0f` (2 rows) `EXC-012e0cef0b888845-S-e5ed07ceccb0` (1 rows) `EXC-012e0cef0b888845-S-e8563a909af7` (4 rows) `EXC-012e0cef0b888845-S-eb484156e9d9` (1 rows) `EXC-012e0cef0b888845-S-f207f68ca602` (1 rows) `EXC-012e0cef0b888845-S-f754dc65b7e2` (2 rows) `EXC-012e0cef0b888845-S-f9c048c95fbb` (1 rows) `EXC-012e0cef0b888845-S-fe54f8be3c24` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 178.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 2dae2010615250cb64e89a56b708ec71df3f87f20d15e3dd1d5dda10221798d6 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | RECURRENCE = [WITHHELD]; EVENT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| d847e2781d8be172e47767a14db63f7c9153a08c44e57e6c3fb145eddac2bf19 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | EVENT = [WITHHELD]; TIME = [WITHHELD]; TIME = [WITHHELD]; LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 3e4e3e97ad99c2d98b2f86108b5dcf2233e83bbf41429700e78d5bc1d0684c74 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | REFERENCE = [WITHHELD]; EVENT = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| db9bd0e777ba282a97b4eb93767ef37e44122b319896086db75b00fd0a248f3a | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | EVENT = [WITHHELD]; DATE = [WITHHELD]; RECURRENCE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 308e25969bd1ad5f5dd9df0f2f7f6915dc03fedd3dbf4cb992ddd31bbea12b05 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | EVENT = [WITHHELD]; DATE = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-03c7f01ec71b3088

- Unique tickets: **1**; unique rows: **21**; ticket–reason occurrences: 1; row–reason occurrences: 21.
- Sources: massive_1_1_en_us.
- Source intents: alarm_remove.
- Current domain / action / disposition / OOD patterns: ALARM/DELETE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_delete=21.
- Slot patterns (unique rows): DATE=3; DATE + EVENT=1; DATE + TIME=1; KIND=3; KIND + DATE=2; REFERENCE=1; TIME=8; TIME + TIME=2.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-03c7f01ec71b3088`. Content binding: `9da5874b96958ccc5bf39472a6bc314bb3bf55022838908cddd6a8764ae908a0`.
- Decision subgroups: 8; blanket cluster decisions disabled. `EXC-03c7f01ec71b3088-S-00ef714b47b7` (3 rows) `EXC-03c7f01ec71b3088-S-47274e2f0dc3` (1 rows) `EXC-03c7f01ec71b3088-S-74ce0cbc89e9` (2 rows) `EXC-03c7f01ec71b3088-S-8fe47aa6ddc6` (3 rows) `EXC-03c7f01ec71b3088-S-907dad8884bc` (1 rows) `EXC-03c7f01ec71b3088-S-9763dd2ca748` (2 rows) `EXC-03c7f01ec71b3088-S-df599d924530` (1 rows) `EXC-03c7f01ec71b3088-S-e6969a681010` (8 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 21.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 9a58401b7f3740ff325910723d9e303a18279bc8ac8964267d6ee2b3ae6bfa5b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | DATE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 34d920a9e8914e31c9ecaefb57cc2d9574835a29d68a28088b5ac5e3dc1e2136 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | REFERENCE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 3e48919da092b9ee8c606a7da7d56b9e2ad6fa2d914ac73f95ceae4c395e6553 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | KIND = [WITHHELD]; DATE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| a27eae3b7eefb8bd28c06d51bb571d61cc7722ef3f5d98dbc6817036f3c90425 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 4234b67982e6be3f1814ba73652ef797ccb17064297f3253e54edebe5c0a00fa | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | DATE = [WITHHELD]; EVENT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-0461848934dc2de0

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: metis_controlled_synthetic.
- Source intents: promise_read_person:complete.
- Current domain / action / disposition / OOD patterns: PROMISE/COMPLETE/SUPPORTED_COMPLETE/False.
- Families (unique rows): synthetic:promise_read_person:complete=1.
- Slot patterns (unique rows): PERSON=1.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-0461848934dc2de0`. Content binding: `ccad916248a1a6eec2c55ce281344727794d54f16b82fffe93922bf6747bc19c`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-0461848934dc2de0-S-98c4ad3909b7` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 77824f614aaaff65d00f1491b8129857ce549151d21c27104c25f56deaf06fd1 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PROMISE | COMPLETE | SUPPORTED_COMPLETE | synthetic:promise_read_person:complete | PERSON = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-068c526b3c69b6f3

- Unique tickets: **1**; unique rows: **72**; ticket–reason occurrences: 1; row–reason occurrences: 72.
- Sources: massive_1_1_en_us.
- Source intents: alarm_set.
- Current domain / action / disposition / OOD patterns: ALARM/SET/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_set=72.
- Slot patterns (unique rows): DATE=1; DATE + EVENT=2; DATE + EVENT + TIME=2; DATE + TIME=5; DATE + TIME + EVENT=2; DATE + TIME + EVENT + TIME=1; DATE + TIME + TIME + EVENT=1; EVENT=2; EVENT + DATE + TIME=1; EVENT + REFERENCE + TIME=1; EVENT + REFERENCE + TIME + DATE=1; EVENT + TIME=2; KIND + DATE + TIME=1; KIND + TIME=1; KIND + TIME + DATE + TIME=1; LOCATION=1; TIME=26; TIME + DATE=7; TIME + DATE + TIME=3; TIME + EVENT=4; TIME + RECURRENCE=1; TIME + TIME=5; TIME + TIME + TIME + DATE=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-068c526b3c69b6f3`. Content binding: `ea25bbcdead57a598f0dc08471b82ecc66f96136689f037f52205f5cb388f2b2`.
- Decision subgroups: 23; blanket cluster decisions disabled. `EXC-068c526b3c69b6f3-S-058879e55c02` (2 rows) `EXC-068c526b3c69b6f3-S-0e88e97bf696` (2 rows) `EXC-068c526b3c69b6f3-S-15ec8d4e4b64` (5 rows) `EXC-068c526b3c69b6f3-S-2c6db96c4561` (1 rows) `EXC-068c526b3c69b6f3-S-31a6787164d6` (26 rows) `EXC-068c526b3c69b6f3-S-391c0beb9e1b` (1 rows) `EXC-068c526b3c69b6f3-S-3c68c733d2c9` (1 rows) `EXC-068c526b3c69b6f3-S-3da47b0470e1` (1 rows) `EXC-068c526b3c69b6f3-S-401a17e21f5f` (4 rows) `EXC-068c526b3c69b6f3-S-50f840e50315` (2 rows) `EXC-068c526b3c69b6f3-S-51f5c0e14af5` (1 rows) `EXC-068c526b3c69b6f3-S-5aae3ccf1366` (1 rows) `EXC-068c526b3c69b6f3-S-6370bfe65b55` (7 rows) `EXC-068c526b3c69b6f3-S-75c824967b50` (3 rows) `EXC-068c526b3c69b6f3-S-92a1f76c670f` (2 rows) `EXC-068c526b3c69b6f3-S-9a15534229a6` (1 rows) `EXC-068c526b3c69b6f3-S-a1591a6c22bd` (1 rows) `EXC-068c526b3c69b6f3-S-a6b2accec714` (1 rows) `EXC-068c526b3c69b6f3-S-c357b0df6654` (1 rows) `EXC-068c526b3c69b6f3-S-f258cb50a4a0` (5 rows) `EXC-068c526b3c69b6f3-S-f95eec12979e` (1 rows) `EXC-068c526b3c69b6f3-S-f9d1095162d6` (2 rows) `EXC-068c526b3c69b6f3-S-f9d3b1b2551b` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 72.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 4cb7604889f135b4a364047762130e59f86d9333fd6aeb521dfac13b096f14ce | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | EVENT = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| a65e1ba2fff64f7cd72bea344c16f4944a1b61bf0d95488fa279abab56d25aab | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | DATE = [WITHHELD]; EVENT = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 295358c3e20543af011cb9e002aa49a041df013c65de0bef3e74eb27234cf5df | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| e926b896ffc315b68d7f606454077acf1ba901b593d1f9e2efdf1f3b298644c9 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | EVENT = [WITHHELD]; DATE = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 0b22c526a4734e2ff942536fd7ceb08ecee19c42e496ad307969c82bef3cb2ad | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-06bda62782be811a

- Unique tickets: **9**; unique rows: **12**; ticket–reason occurrences: 9; row–reason occurrences: 12.
- Sources: clinc150_full.
- Source intents: account_blocked, balance, direct_deposit, expiration_date, income, interest_rate, routing, spending_history, transactions.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=12.
- Slot patterns (unique rows): NO_SLOTS=12.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-06bda62782be811a`. Content binding: `1f3e14e7b30d1f98c6e9c1ef414e4961084ecf2fae897a0a844e8e81138b2c88`.
- Decision subgroups: 9; blanket cluster decisions disabled. `EXC-06bda62782be811a-S-017ea327bc3a` (1 rows) `EXC-06bda62782be811a-S-4d1a641297b2` (2 rows) `EXC-06bda62782be811a-S-5e2bf9055e00` (2 rows) `EXC-06bda62782be811a-S-649c077952cf` (1 rows) `EXC-06bda62782be811a-S-6fe4abb73d3a` (2 rows) `EXC-06bda62782be811a-S-7a999ff237d3` (1 rows) `EXC-06bda62782be811a-S-960dd92450c2` (1 rows) `EXC-06bda62782be811a-S-9e70d0efbecd` (1 rows) `EXC-06bda62782be811a-S-c89f570bcab0` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 12.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 93a61cf50b9c3ec2ad126272e0146da2570eac4e52d88347b02f8a3ddd761067 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 1c1dca79581615397fc328c4158b36914926c92c5d7d9d3198ee26bb1a1bd5c5 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 442b1b2fa209db71f7d838894c169a51f8336209cf38dd09018fc813cb65c561 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 4de0182f423058451ef5b665f1888e10401297dc8deb677637402bf0a10fe401 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 33c663654312e92fe8eb3f175021aaccd9e69d7a07407ece872c8983918fbab1 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-0a2b2c4e100490e0

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: massive_1_1_en_us.
- Source intents: recommendation_locations.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=1.
- Slot patterns (unique rows): KIND=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-0a2b2c4e100490e0`. Content binding: `8504324a11674fb0ba389d2298b0a3b4274cc528f8c83eeaa538f07fcce3510f`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-0a2b2c4e100490e0-S-7116c548fc16` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 448eef5260782e87b17384f415d3f182703d8df945bd220b41e72bd961f721fa | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-0a4e41f98898a7fa

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: massive_1_1_en_us.
- Source intents: general_joke.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/KNOWN_UNSUPPORTED/False.
- Families (unique rows): unknown_external_action=1.
- Slot patterns (unique rows): KIND=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-0a4e41f98898a7fa`. Content binding: `13f786eda83a53775c9f6c831db5196e343243cc70568de538efbf28be5e11ec`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-0a4e41f98898a7fa-S-024b3da7eb20` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 83dc90fdebf07478a3a63a527a53070968c90b9608c3a8c59f7b55d8ba2300ba | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | KNOWN_UNSUPPORTED | unknown_external_action | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-0bcdcc5155e23edc

- Unique tickets: **1**; unique rows: **45**; ticket–reason occurrences: 1; row–reason occurrences: 45.
- Sources: massive_1_1_en_us.
- Source intents: email_querycontact.
- Current domain / action / disposition / OOD patterns: PERSON/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): person_read=45.
- Slot patterns (unique rows): KIND=1; PERSON=35; PERSON + EVENT=3; PERSON + LOCATION=2; REFERENCE=3; SUBJECT=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-0bcdcc5155e23edc`. Content binding: `40e067ed728156e92bbe55d50960291489c0aa01ae4ee262e3bee9324e666af1`.
- Decision subgroups: 6; blanket cluster decisions disabled. `EXC-0bcdcc5155e23edc-S-0f8fc4ed5274` (1 rows) `EXC-0bcdcc5155e23edc-S-378a540d4b54` (3 rows) `EXC-0bcdcc5155e23edc-S-6275810b5003` (3 rows) `EXC-0bcdcc5155e23edc-S-71a004e378f0` (35 rows) `EXC-0bcdcc5155e23edc-S-958041732cc7` (2 rows) `EXC-0bcdcc5155e23edc-S-a8fbb06babf4` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 45.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| d52a72bf11b866c45c9fba195397836dba99761f4fa514a47b8bd018e9aa4e2f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 015baf004c34963a0f0fb5dc25127c377a310986c0f317db2d1dc3a6d79c9d58 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | REFERENCE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 46163d1b3e6a059838ff47394ede1e1a108b57d5db3a5cdffa519e0ed8b59f37 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | PERSON = [WITHHELD]; EVENT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 006a973bdaa463e7ac5d9e84b1cafad6f36ae7f64d7b90f3c3fb1566cc6eeb8f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | PERSON = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| cf5a7c5629fece9f64b3ccf895cea0b9e6c93e43db34a04726c4dd3a50dd653c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | PERSON = [WITHHELD]; LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-138e0a9bd4837c3d

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: metis_controlled_synthetic.
- Source intents: task_read_subject_date_status:delete.
- Current domain / action / disposition / OOD patterns: TASK/DELETE/SUPPORTED_COMPLETE/False.
- Families (unique rows): synthetic:task_read_subject_date_status:delete=1.
- Slot patterns (unique rows): STATUS + SUBJECT + DATE=1.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-138e0a9bd4837c3d`. Content binding: `6850f4cd6552ccd59302900707e854676a7b6eaf0143f21cb99f21d5af7e02e9`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-138e0a9bd4837c3d-S-e20e6bb96627` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 944f8bf617da274b7ce9bace84ca9c44052e455c104de89b29a446ddba2f89c2 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | DELETE | SUPPORTED_COMPLETE | synthetic:task_read_subject_date_status:delete | STATUS = [WITHHELD]; SUBJECT = [WITHHELD]; DATE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-177ea4b04c1b0bae

- Unique tickets: **1**; unique rows: **9**; ticket–reason occurrences: 1; row–reason occurrences: 9.
- Sources: massive_1_1_en_us.
- Source intents: alarm_query.
- Current domain / action / disposition / OOD patterns: ALARM/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_read=9.
- Slot patterns (unique rows): DATE + EVENT=1; KIND=2; KIND + TIME=1; LOCATION=1; TIME=2; TIME + EVENT=1; TIME + TIME=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-177ea4b04c1b0bae`. Content binding: `6beeee8f81a4223dc1b02b2b554ab351d399b65f83225acb26efa24f4260dc34`.
- Decision subgroups: 7; blanket cluster decisions disabled. `EXC-177ea4b04c1b0bae-S-530f2fdea64b` (1 rows) `EXC-177ea4b04c1b0bae-S-5955217ae71c` (1 rows) `EXC-177ea4b04c1b0bae-S-7da69346227b` (2 rows) `EXC-177ea4b04c1b0bae-S-adb6a6759a4d` (1 rows) `EXC-177ea4b04c1b0bae-S-ec5e317b0ef0` (2 rows) `EXC-177ea4b04c1b0bae-S-fa9846c2d41b` (1 rows) `EXC-177ea4b04c1b0bae-S-ff450755e5a9` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 9.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 87265164a515c8594663e7b920f1fd1613fa89b1704bcea9ee5b931b50241a1e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | DATE = [WITHHELD]; EVENT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| b15f4c1b996c83a539fbd4eba0fc261bd8a2c0025e66c9ecda9addc23d483919 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 3ca8ca84383321b0ce4165f95d8f7fdc9ffb1da3278fbe9925ec609e5090acf1 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 520c0e254c678841a94835bb8d8eecdc9f6240cdee0b41ff280f7a02871f881b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | KIND = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 14cc595404092c1951a11adb05834d767a1bd704b171607a753e38927d5964fd | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-18ae4845109a0f26

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: clinc150_full.
- Source intents: todo_list.
- Current domain / action / disposition / OOD patterns: TASK/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): task_read_all=1.
- Slot patterns (unique rows): NO_SLOTS=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-18ae4845109a0f26`. Content binding: `55c313f62f3cf3142323fdac8484ff413c8a53413d7588085c7e51fb63c74f52`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-18ae4845109a0f26-S-b06822bd72d9` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 4fa54ad63fe7ddb2988537a4e29e94ad08be612b99f9289b3bed5c2566b801e5 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | READ | KNOWN_UNSUPPORTED | task_read_all | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-18e6994f5ee74679

- Unique tickets: **2**; unique rows: **2**; ticket–reason occurrences: 2; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: social_post, social_query.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=2.
- Slot patterns (unique rows): KIND=1; LOCATION=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-18e6994f5ee74679`. Content binding: `4e465fa44754cb6d4baf39e9e2e68e64b5d787e94b7a3b1ecda45e54d5476d19`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-18e6994f5ee74679-S-63cfa2b1e294` (1 rows) `EXC-18e6994f5ee74679-S-798519910675` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| daae096f6f7834ea1107a6a3b5278fedcfcc9eb7cca5e5986f9a6aa125453c72 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 836a504295d20d63f8f37618cb2a7084a58c61dba82946d4ba8976b795c963b5 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-226f3ca8e3636d84

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: clinc150_full.
- Source intents: reminder_update.
- Current domain / action / disposition / OOD patterns: REMINDER/UPDATE/KNOWN_UNSUPPORTED/False.
- Families (unique rows): UNRESOLVED=1.
- Slot patterns (unique rows): NO_SLOTS=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-226f3ca8e3636d84`. Content binding: `6a4b32d27671aff6e7e1659209afe7948d672529727b4500ebc7691d6652a8b4`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-226f3ca8e3636d84-S-f7551b2dcba9` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 1521439a611b2c020d1dc1a73f27e1cee186399da32402fc3302a49d4a80e7f1 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | REMINDER | UPDATE | KNOWN_UNSUPPORTED | UNRESOLVED | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-2c5976ac6c364cf9

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: clinc150_full.
- Source intents: reminder.
- Current domain / action / disposition / OOD patterns: REMINDER/CREATE/KNOWN_UNSUPPORTED/False.
- Families (unique rows): reminder_create=1.
- Slot patterns (unique rows): NO_SLOTS=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-2c5976ac6c364cf9`. Content binding: `4baea4e2d99887555d9be55a7b6fa4e31493709cefb97ba8fcf7c02971ff625a`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-2c5976ac6c364cf9-S-8299e73d3cbd` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| b1eb8261738a86158fce605a7397ac9eb5ea708fdb80a022b929354837de2e47 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | REMINDER | CREATE | KNOWN_UNSUPPORTED | reminder_create | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-2cf6ce467ff4ed5a

- Unique tickets: **1**; unique rows: **62**; ticket–reason occurrences: 1; row–reason occurrences: 62.
- Sources: massive_1_1_en_us.
- Source intents: email_sendemail.
- Current domain / action / disposition / OOD patterns: COMMUNICATION/SEND/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): communication_send=62.
- Slot patterns (unique rows): NO_SLOTS=62.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-2cf6ce467ff4ed5a`. Content binding: `2ebb51a738ecc44cbb5bb7d36778dc0edffb2692a8656223719f3e1d406a4514`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-2cf6ce467ff4ed5a-S-c26ddf59e43f` (62 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 62.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 00b241dc5811fcb999d8128abbbd623948120c504f5df61651b109b5b4df7f9f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 08f8b23333304445c0a9f8af0ea391e95f199c2f35643e5b41a01bd606febea3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 27b5b5ba21bd98834b36c86b3e52928b5ffd16bd9eaec250368cc7251fcad1a7 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2e74255e87d9b7cfd81bab359efaf16b3b20c0813248ca918c9e1aa245e9c127 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2efb24b65eef5a4e80664643980fffb8e25ac3d93310e9bbd14a8fe9bb156500 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-2d92a4f1dae72c46

- Unique tickets: **1**; unique rows: **47**; ticket–reason occurrences: 1; row–reason occurrences: 47.
- Sources: massive_1_1_en_us.
- Source intents: alarm_remove.
- Current domain / action / disposition / OOD patterns: ALARM/DELETE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_delete=47.
- Slot patterns (unique rows): NO_SLOTS=30; DATE=4; DATE + TIME=1; TIME=10; TIME + DATE=2.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-2d92a4f1dae72c46`. Content binding: `6140c465582e0b9734da2335942877e4c72bd37ff2986c2516a6657b0addf1e1`.
- Decision subgroups: 5; blanket cluster decisions disabled. `EXC-2d92a4f1dae72c46-S-00ef714b47b7` (4 rows) `EXC-2d92a4f1dae72c46-S-48af0fbfc6fc` (2 rows) `EXC-2d92a4f1dae72c46-S-cfe0554fd29f` (30 rows) `EXC-2d92a4f1dae72c46-S-df599d924530` (1 rows) `EXC-2d92a4f1dae72c46-S-e6969a681010` (10 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 47.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 23485e1de48abf8e65478769fbff45b4cbaf73b075bb0d8918fc3d216d6d618b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 582cb3cbd4bb10400f9b1df9b7cbbfd9569288d82f246a7b1e2925a49cb2bd04 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | TIME = [WITHHELD]; DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 3921f89c0ac483ec320c7b337ad014583d2552d01b9dfb1d98d9402c35df3f97 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 8a408be24bfbfd847baaba82839d86020d194cfdd390a9d9531500a72d78f153 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | DATE = [WITHHELD]; TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 0feb5756ade4e56df6c528dd5b2a307ab67ab3e180178a85a5b798fdbd2520c7 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-2ee1471b4e601c3e

- Unique tickets: **4**; unique rows: **4**; ticket–reason occurrences: 4; row–reason occurrences: 4.
- Sources: clinc150_full.
- Source intents: plug_type, travel_alert, travel_notification, travel_suggestion.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=4.
- Slot patterns (unique rows): NO_SLOTS=4.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-2ee1471b4e601c3e`. Content binding: `d0a848153ee6c02753bdf2afb5b00215f5fa874c0ab46f65e3b6d7582adb2cfd`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-2ee1471b4e601c3e-S-065c79379ad2` (1 rows) `EXC-2ee1471b4e601c3e-S-69cd99239f69` (1 rows) `EXC-2ee1471b4e601c3e-S-a2e6640eb6ca` (1 rows) `EXC-2ee1471b4e601c3e-S-cc677b40e809` (1 rows).

### Representative records

4 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 4.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 9f86aeb424478ad46be22aac78571d9a35dd6cf4df40fd16853eec490780d6ce | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 527c8affb5dc6937f17c8abcaeaccd9b57a72876763fa6fbfdd31078df9e99cd | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 89a9645cf2ed9ac96ac3e3aabeff2f2dd938b86a60228e0b9db7e05bc5b0ef71 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| c233a3dd5481f76e6643525595a737b614193140fc12fc3abfcce729da68eb1f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-2fb9a1a4646b4d6d

- Unique tickets: **1**; unique rows: **2**; ticket–reason occurrences: 1; row–reason occurrences: 2.
- Sources: clinc150_full.
- Source intents: alarm.
- Current domain / action / disposition / OOD patterns: ALARM/SET/KNOWN_UNSUPPORTED/False.
- Families (unique rows): alarm_set=2.
- Slot patterns (unique rows): NO_SLOTS=2.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-2fb9a1a4646b4d6d`. Content binding: `0ca08e999df1eea02639738c38b0e53ed05bf1b747aead1d01c9b7854c150e98`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-2fb9a1a4646b4d6d-S-cce8af419c75` (2 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| a842bac9e80bd4f095b969aed665f38f785af2946d07a6bf7a9127ca325ca141 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | KNOWN_UNSUPPORTED | alarm_set | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| f1e7966dd316af971a843d7d16ea2bb331faf4ec0adc4a527c2e26582953d7cd | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | KNOWN_UNSUPPORTED | alarm_set | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-3189941bea1a25a5

- Unique tickets: **10**; unique rows: **10**; ticket–reason occurrences: 10; row–reason occurrences: 10.
- Sources: clinc150_full.
- Source intents: insurance, insurance_change, payday, pto_balance, pto_request, pto_request_status, pto_used, rollover_401k, taxes, w2.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=10.
- Slot patterns (unique rows): NO_SLOTS=10.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-3189941bea1a25a5`. Content binding: `c0d761969ede96a6d1f6ab66958128716ac59d14ebf4b76b1b289ee7acd2b1ae`.
- Decision subgroups: 10; blanket cluster decisions disabled. `EXC-3189941bea1a25a5-S-060ffbf58be1` (1 rows) `EXC-3189941bea1a25a5-S-092ab03653b3` (1 rows) `EXC-3189941bea1a25a5-S-25efa066c36c` (1 rows) `EXC-3189941bea1a25a5-S-379d0b29c9a9` (1 rows) `EXC-3189941bea1a25a5-S-38e3a362ce21` (1 rows) `EXC-3189941bea1a25a5-S-47274aa44a32` (1 rows) `EXC-3189941bea1a25a5-S-725b4211929d` (1 rows) `EXC-3189941bea1a25a5-S-e745ee020022` (1 rows) `EXC-3189941bea1a25a5-S-f12a4f7c165d` (1 rows) `EXC-3189941bea1a25a5-S-fa6d9baf8305` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 10.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 4f6251c9eba9bedd163d6ccdcc55920145d5b0968d587e8eba826b3a6c9e11be | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 57ab699b86d1635dcf70048681164a9713ce4865b1365c16b4e2751c73de5761 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 174255c120dcdbe5cd2a392833bd3dd4c69dd6c99ac5902a417180d8b3c7ba19 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 39008b8d4f86bf3f9e0c3f24f64c60170dbc46383f66f7463743c5d2e556e192 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 53f77c444bf2f635bf16a04d344feb34d22717e887fa58d32f772aefab39ec14 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-38aa11e5a94001e3

- Unique tickets: **4**; unique rows: **4**; ticket–reason occurrences: 4; row–reason occurrences: 4.
- Sources: massive_1_1_en_us.
- Source intents: audio_volume_down, audio_volume_mute, audio_volume_other, audio_volume_up.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=4.
- Slot patterns (unique rows): NO_SLOTS=4.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-38aa11e5a94001e3`. Content binding: `59b64002dd783423d163c0934f54185f9f3cf497bec4afa600f6d4da3eedbdf3`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-38aa11e5a94001e3-S-3843599db26b` (1 rows) `EXC-38aa11e5a94001e3-S-a065950e3c23` (1 rows) `EXC-38aa11e5a94001e3-S-b74fddf13081` (1 rows) `EXC-38aa11e5a94001e3-S-c0a6e42dbb70` (1 rows).

### Representative records

4 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 4.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 55d4c68a959aa448e13ab4083f9248f37168889c84577cb3a9aeeb9e8d151045 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| e0e193e2289cd2310c25f041b4622ccc769744625e8eb373d824831e7470aca5 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| a4713c412811a054eda25c776fdea9ab0f3d0f5a0c2a7ec805647dc307ba235d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 30b723b43c232fea621d39c8e7bf4caf7f146c8c541a70aba1ad5a343777d35f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-3a2d2983a75f84e2

- Unique tickets: **3**; unique rows: **3**; ticket–reason occurrences: 3; row–reason occurrences: 3.
- Sources: massive_1_1_en_us.
- Source intents: lists_createoradd, lists_query, lists_remove.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/KNOWN_UNSUPPORTED/False.
- Families (unique rows): unknown_external_action=3.
- Slot patterns (unique rows): NO_SLOTS=3.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-3a2d2983a75f84e2`. Content binding: `56dc19276e85e494daf624daac3346b28a0c7406b57f2f9de4c8ddc724815c50`.
- Decision subgroups: 3; blanket cluster decisions disabled. `EXC-3a2d2983a75f84e2-S-3dac18e42306` (1 rows) `EXC-3a2d2983a75f84e2-S-b0e8bdffc191` (1 rows) `EXC-3a2d2983a75f84e2-S-bb17613b4688` (1 rows).

### Representative records

3 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 3.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 803fcaeeee697727c085ba94317417d20c9baef82d6b2e3b874326a52df48e2d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | KNOWN_UNSUPPORTED | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2bb2d4f672e3e087bee6cf31dc7a77d240e34c7c3d856b509d746a7e043fa8b1 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | KNOWN_UNSUPPORTED | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 52dab94df37050dbf11af43c5b9867b766159d81b171c4f0afc60cf20203d123 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | KNOWN_UNSUPPORTED | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-3a9826bf14f7afda

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: massive_1_1_en_us.
- Source intents: news_query.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=1.
- Slot patterns (unique rows): KIND=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-3a9826bf14f7afda`. Content binding: `1803c6cb9e24875c1ae8d4bd2b42e531bfcf4ea837d70b59fc813739ccc3dc51`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-3a9826bf14f7afda-S-f243b5df2cf3` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| d44f8df76b9bc1bb17fc09ce626b49d331bdc645dd9e06acd8bd91b92bcfa451 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-43191a126dbdb2a6

- Unique tickets: **1**; unique rows: **2**; ticket–reason occurrences: 1; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: email_sendemail.
- Current domain / action / disposition / OOD patterns: COMMUNICATION/SEND/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): communication_send=2.
- Slot patterns (unique rows): NO_SLOTS=2.
- Primary reason: `UNEXPECTED_SECOND_ACTION`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-43191a126dbdb2a6`. Content binding: `29c27fb061a47ace88dae73db6ba0ec5741e3fa2a832f9c1e5d184ce445b11fa`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-43191a126dbdb2a6-S-c26ddf59e43f` (2 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 6d3badc11e134bf7108b679f915f342f256fe2cc21f13982d6cf13c60ca23bfe | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | none | UNEXPECTED_SECOND_ACTION |
| 9da8eef1d0899668a3b18502a5a09a382c06a8e0e287d8227b7551b649e5add6 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | none | UNEXPECTED_SECOND_ACTION |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-437b4eb7811456d7

- Unique tickets: **1**; unique rows: **6**; ticket–reason occurrences: 1; row–reason occurrences: 6.
- Sources: metis_controlled_synthetic.
- Source intents: memory_read_subject.
- Current domain / action / disposition / OOD patterns: MEMORY/READ/SUPPORTED_COMPLETE/False.
- Families (unique rows): synthetic:memory_read_subject=6.
- Slot patterns (unique rows): SUBJECT=6.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-437b4eb7811456d7`. Content binding: `ea93665440bac4fdaca41498597670f0c886a32af6cc654e7449c43e11b82bd6`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-437b4eb7811456d7-S-c6bde4498e16` (6 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 6.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 2b69d987865183cae09b53c65c6c52c964deed6aea38f4078e2b6f750557fc29 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | MEMORY | READ | SUPPORTED_COMPLETE | synthetic:memory_read_subject | SUBJECT = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| 2e08c52c32be6df251467024538104cd194304bacd078285327bea91281cd143 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | MEMORY | READ | SUPPORTED_COMPLETE | synthetic:memory_read_subject | SUBJECT = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| 4253f8d1a53443f5b0f8223ab9f83700f2efcc939400c8d9caa1cea19374f744 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | MEMORY | READ | SUPPORTED_COMPLETE | synthetic:memory_read_subject | SUBJECT = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| 76976fe5331e31a102696cc0e831ed2dbc3dce177d2c6a90691157bd64270abc | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | MEMORY | READ | SUPPORTED_COMPLETE | synthetic:memory_read_subject | SUBJECT = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| 8adbf06496a1f3e64efb9c5d3f648b1cd158fdc0e15dab187c0ed48f8958757d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | MEMORY | READ | SUPPORTED_COMPLETE | synthetic:memory_read_subject | SUBJECT = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-45deb732e794f222

- Unique tickets: **1**; unique rows: **19**; ticket–reason occurrences: 1; row–reason occurrences: 19.
- Sources: massive_1_1_en_us.
- Source intents: email_querycontact.
- Current domain / action / disposition / OOD patterns: PERSON/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): person_read=19.
- Slot patterns (unique rows): NO_SLOTS=19.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-45deb732e794f222`. Content binding: `cc95d00d26104d0cf3c9223b655ebbf376008b5d119af00cab2bbaa015f58119`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-45deb732e794f222-S-7faf25b36ac2` (19 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 19.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 028dd31d16630f1a8bdea6a17d08b4f76a0a108ec809cf8b95a5a3bd1eb7e7c4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 0a2440356f0d24b18a3c65d1a56863dc1a26b09a92189d736d23167ba36e8740 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2b91590d5acb58cc77d37361f7902cc40f0c7fb1c7e753772deedd533bd2025d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2d00520b03ba1daaab93fc2dc7f0c3885aa564bb0385525da52dacb3e509b500 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 464e246e4b0f3cbd7c4b3843c3c6862ab422a35d32f1f0a64857c9c2d4b9c284 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | READ | SUPPORTED_NEEDS_CONTEXT | person_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-4b57863607136348

- Unique tickets: **1**; unique rows: **6**; ticket–reason occurrences: 1; row–reason occurrences: 6.
- Sources: metis_controlled_synthetic.
- Source intents: promise_read_person.
- Current domain / action / disposition / OOD patterns: PROMISE/READ/SUPPORTED_COMPLETE/False.
- Families (unique rows): synthetic:promise_read_person=6.
- Slot patterns (unique rows): PERSON=6.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-4b57863607136348`. Content binding: `53c354fb171739f3779c23de943802fee2692d0b8a604aaa49addd3382834b6f`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-4b57863607136348-S-7d5318a1b65d` (6 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 6.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 5ab96aab847829eea0d632faa08385b399b66914ee9fa930b1bfba764571c238 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PROMISE | READ | SUPPORTED_COMPLETE | synthetic:promise_read_person | PERSON = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| 8106f1e9b3e7836deac8efe53b599848799deb99c9aec93927d5f127beab40f8 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PROMISE | READ | SUPPORTED_COMPLETE | synthetic:promise_read_person | PERSON = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| 8158d8fffdeb1d37783720ec7cf9305f12ff8e350fa82e29880d444534e3734e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PROMISE | READ | SUPPORTED_COMPLETE | synthetic:promise_read_person | PERSON = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| 99de86eab3243284d521378ed5ae28e7ecddb85c67fbcb4e403f380c8897532d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PROMISE | READ | SUPPORTED_COMPLETE | synthetic:promise_read_person | PERSON = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| e19bc760ead008f1f1b7521ec6670adda6b0d910af5a5e54cad1e684bd7a9ad9 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PROMISE | READ | SUPPORTED_COMPLETE | synthetic:promise_read_person | PERSON = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-51b2b853646c513e

- Unique tickets: **2**; unique rows: **2**; ticket–reason occurrences: 2; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: general_greet, general_quirky.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/KNOWN_UNSUPPORTED/False.
- Families (unique rows): unknown_external_action=2.
- Slot patterns (unique rows): NO_SLOTS=2.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-51b2b853646c513e`. Content binding: `42bd1df02583e15e61063d3b72e500e3af00b61882751c6cc8b1e4eb9140fcab`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-51b2b853646c513e-S-2adb0429e972` (1 rows) `EXC-51b2b853646c513e-S-9965ceae5614` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 14b8f66bdf8f44aee50f199aa16bcc673c69864d3309bf23fc999115fd7787db | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | KNOWN_UNSUPPORTED | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| a8d6d831f0d95813563d506cf599eaeb93d3f7d08800850beee03dec2ae4d42e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | KNOWN_UNSUPPORTED | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-558abdade0c89d07

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: metis_controlled_synthetic.
- Source intents: memory_read_subject:compound.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): synthetic:memory_read_subject:compound=1.
- Slot patterns (unique rows): SUBJECT + REFERENCE=1.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-558abdade0c89d07`. Content binding: `c75e3b2c81f532b8e0b8d0d0b7ad960f8c8481f455501f6c42a05e8537de84b8`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-558abdade0c89d07-S-a0c287dbdc51` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| ffb2f74c2075f080c535d957a14f28c3f4ce61484569b95dcd7924d04a47642f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | SUPPORTED_NEEDS_CONTEXT | synthetic:memory_read_subject:compound | SUBJECT = [WITHHELD]; REFERENCE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-57b9c10ceabb14b2

- Unique tickets: **1**; unique rows: **107**; ticket–reason occurrences: 1; row–reason occurrences: 107.
- Sources: massive_1_1_en_us.
- Source intents: calendar_query.
- Current domain / action / disposition / OOD patterns: CALENDAR/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): UNRESOLVED=28; calendar_read_date=20; calendar_read_date_event=22; calendar_read_date_event_time=3; calendar_read_event=31; calendar_read_person=3.
- Slot patterns (unique rows): DATE=13; DATE + EVENT=3; DATE + EVENT + TIME + PERSON=1; DATE + PERSON + EVENT=1; DATE + REFERENCE + EVENT=1; DATE + TIME=2; EVENT=21; EVENT + DATE=19; EVENT + DATE + TIME=1; EVENT + DATE + TIME + TIME=1; EVENT + KIND + DATE=1; EVENT + LOCATION=3; EVENT + LOCATION + DATE=1; EVENT + PERSON=2; EVENT + PERSON + DATE=1; EVENT + PERSON + TIME + DATE=1; EVENT + REFERENCE + DATE=1; EVENT + TIME=3; KIND=1; KIND + DATE=1; LOCATION + TIME + DATE=1; PERSON=3; PERSON + DATE + TIME=1; PERSON + EVENT=2; PERSON + EVENT + DATE=1; PERSON + EVENT + TIME=2; RECURRENCE + EVENT=2; REFERENCE + EVENT + DATE=1; SUBJECT=1; SUBJECT + DATE=1; TIME=9; TIME + EVENT=2; TIME + EVENT + DATE=1; TIME + TIME + DATE=2.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-57b9c10ceabb14b2`. Content binding: `65839c70d4bd0c17d1030f0787c1d459b306a9559c46ef7bc57eead147999ddd`.
- Decision subgroups: 34; blanket cluster decisions disabled. `EXC-57b9c10ceabb14b2-S-0a2767ec7799` (1 rows) `EXC-57b9c10ceabb14b2-S-0be241f9b13f` (1 rows) `EXC-57b9c10ceabb14b2-S-0f9d903b6905` (3 rows) `EXC-57b9c10ceabb14b2-S-14dc34405aa2` (3 rows) `EXC-57b9c10ceabb14b2-S-2b01e31f69a4` (2 rows) `EXC-57b9c10ceabb14b2-S-2cbf9e50b2cc` (1 rows) `EXC-57b9c10ceabb14b2-S-2fd73fadf08e` (21 rows) `EXC-57b9c10ceabb14b2-S-3882fddf25e8` (1 rows) `EXC-57b9c10ceabb14b2-S-3a6e14be137a` (1 rows) `EXC-57b9c10ceabb14b2-S-426c44d35811` (1 rows) `EXC-57b9c10ceabb14b2-S-4b8591b64183` (1 rows) `EXC-57b9c10ceabb14b2-S-5339c4582ccd` (1 rows) `EXC-57b9c10ceabb14b2-S-571a87517349` (1 rows) `EXC-57b9c10ceabb14b2-S-5baaf778f8d1` (2 rows) `EXC-57b9c10ceabb14b2-S-709f80b04440` (2 rows) `EXC-57b9c10ceabb14b2-S-7a74ca606d26` (1 rows) `EXC-57b9c10ceabb14b2-S-7caa1a83c4b6` (3 rows) `EXC-57b9c10ceabb14b2-S-9c1ed26ee44d` (2 rows) `EXC-57b9c10ceabb14b2-S-a029c0af8071` (3 rows) `EXC-57b9c10ceabb14b2-S-b958bd960d2d` (1 rows) `EXC-57b9c10ceabb14b2-S-bfe7f699ab2b` (1 rows) `EXC-57b9c10ceabb14b2-S-c0697c30d9ac` (1 rows) `EXC-57b9c10ceabb14b2-S-c70f0378ca5b` (1 rows) `EXC-57b9c10ceabb14b2-S-d205560307aa` (2 rows) `EXC-57b9c10ceabb14b2-S-d4f5bfbd39a2` (2 rows) `EXC-57b9c10ceabb14b2-S-d79e963d9200` (1 rows) `EXC-57b9c10ceabb14b2-S-ddf22c733258` (1 rows) `EXC-57b9c10ceabb14b2-S-eb0abe18a87f` (9 rows) `EXC-57b9c10ceabb14b2-S-ec7230f0a250` (1 rows) `EXC-57b9c10ceabb14b2-S-ef3284cf8eb7` (1 rows) `EXC-57b9c10ceabb14b2-S-f67200725c1b` (2 rows) `EXC-57b9c10ceabb14b2-S-f88937ea5a1e` (1 rows) `EXC-57b9c10ceabb14b2-S-f9da746695d5` (13 rows) `EXC-57b9c10ceabb14b2-S-fb6389f846da` (19 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 107.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| e09d742b822ed32752b1d4dc2698f792cd152fc74ef33c7aac13c046b5268d09 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | UNRESOLVED | EVENT = [WITHHELD]; KIND = [WITHHELD]; DATE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 7ed37639a0d8669864a80eefa6914208e360f33a97a952a5711024f0b755cfb6 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_date | LOCATION = [WITHHELD]; TIME = [WITHHELD]; DATE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 815197c833bd747e999e8a47bd6e6c9cbfbccd2834c91e24e8fa970ce8dedfd2 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_person | PERSON = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 625fb49cf410ebeb220ee62b9785da27606ad7e91f72e2586fcb8cc0b5746c6d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_event | EVENT = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| baa74575b058d5f1978ef0112c7a768527077fed6b0b475918cf58de69944ff3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | UNRESOLVED | PERSON = [WITHHELD]; EVENT = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-582cc5206f393966

- Unique tickets: **1**; unique rows: **58**; ticket–reason occurrences: 1; row–reason occurrences: 58.
- Sources: massive_1_1_en_us.
- Source intents: alarm_set.
- Current domain / action / disposition / OOD patterns: ALARM/SET/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_set=58.
- Slot patterns (unique rows): NO_SLOTS=31; DATE=3; DATE + TIME=2; EVENT=1; TIME=18; TIME + DATE=2; TIME + DATE + EVENT=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-582cc5206f393966`. Content binding: `11b94a54ca05f184b82fe8ebd016957e4adf089203b65b6820dff2ac6c821210`.
- Decision subgroups: 7; blanket cluster decisions disabled. `EXC-582cc5206f393966-S-0656780bae03` (1 rows) `EXC-582cc5206f393966-S-31a6787164d6` (18 rows) `EXC-582cc5206f393966-S-6370bfe65b55` (2 rows) `EXC-582cc5206f393966-S-92a1f76c670f` (1 rows) `EXC-582cc5206f393966-S-a1591a6c22bd` (3 rows) `EXC-582cc5206f393966-S-bf6647215952` (31 rows) `EXC-582cc5206f393966-S-f258cb50a4a0` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 58.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 5964b81b46b0f8e08bed51e12ed369852a616b752e6a47be1a4af649aa6ad691 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD]; DATE = [WITHHELD]; EVENT = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2786a777ed3fe7be9584f670dfef89d207637854fa198a3386ac296c828acc6e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 1b24b03d050f4685e5073747ddebe43affea1ff2541885981dcceb3244ee1e41 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD]; DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| dd64ee1e4bded52c75cba9c384d4ae0eb16a32a8672b6526bf606b1f3a85f126 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | EVENT = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 016e8e115dc6ac7b7f92c36c0a441f4a26b55dfaa18d2d8093f2d897402ac7e4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-5a07d3b0df7c9485

- Unique tickets: **9**; unique rows: **16**; ticket–reason occurrences: 9; row–reason occurrences: 16.
- Sources: clinc150_full.
- Source intents: book_flight, book_hotel, cancel_reservation, car_rental, carry_on, confirm_reservation, flight_status, international_visa, lost_luggage.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=16.
- Slot patterns (unique rows): NO_SLOTS=16.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-5a07d3b0df7c9485`. Content binding: `474f114fc40b4411d94da6c0aef105ccee0910f3b81373a2843fea90588ade95`.
- Decision subgroups: 9; blanket cluster decisions disabled. `EXC-5a07d3b0df7c9485-S-0a1ec698c485` (2 rows) `EXC-5a07d3b0df7c9485-S-0f34dbd8b6ff` (2 rows) `EXC-5a07d3b0df7c9485-S-3d5a89484abc` (2 rows) `EXC-5a07d3b0df7c9485-S-5198c611897a` (2 rows) `EXC-5a07d3b0df7c9485-S-5289ac6d968f` (1 rows) `EXC-5a07d3b0df7c9485-S-58da711fbed6` (2 rows) `EXC-5a07d3b0df7c9485-S-8c0659fc4916` (2 rows) `EXC-5a07d3b0df7c9485-S-9feaf7c54247` (1 rows) `EXC-5a07d3b0df7c9485-S-fd3dfef59002` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 16.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 0c8dcc9a635eda21efcc3314dfa5ec310c789747bc05b03a9e903b534837901c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 288de1a1acbec82dc5a314aec71c8a786b01d40e80160181e3ee7455acfdac9f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 064b2fe4bfd3afbdd18d9bb1a60e182bfcc37fa5e27d69fd8a08aea533c26e8a | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 17922b56df21b6ef986ae899843a4913de5ee4b2b7c9de04e2494671b0d1d88f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 83031ca27eaef0da057b893c45666cbe8d176a80e1413f852e16476138a07cb3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-655fce4dda08976a

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: clinc150_full.
- Source intents: user_name.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=1.
- Slot patterns (unique rows): NO_SLOTS=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-655fce4dda08976a`. Content binding: `c94f1a971292e21897b18b5d68aa9a57781bb165ad4d07f6e0babdc824c71b09`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-655fce4dda08976a-S-626ca6a8f18c` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 8f06954643370bca0ac55e000aaa480cb1072eeb082c0aa2af15f673caec9673 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-6a4b453cc71fa113

- Unique tickets: **12**; unique rows: **16**; ticket–reason occurrences: 12; row–reason occurrences: 16.
- Sources: clinc150_full.
- Source intents: calculator, definition, exchange_rate, fun_fact, meaning_of_life, measurement_conversion, next_holiday, spelling, timezone, translate, vaccines, weather.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=16.
- Slot patterns (unique rows): NO_SLOTS=16.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-6a4b453cc71fa113`. Content binding: `b32206a637252fb1c0d9b47fc16b55bccab695fb9626326d56eb88dbfc8d3d0d`.
- Decision subgroups: 12; blanket cluster decisions disabled. `EXC-6a4b453cc71fa113-S-04cf6503eb53` (1 rows) `EXC-6a4b453cc71fa113-S-12384f2b39bf` (1 rows) `EXC-6a4b453cc71fa113-S-14e4c3fe4118` (1 rows) `EXC-6a4b453cc71fa113-S-3f3194c0a084` (1 rows) `EXC-6a4b453cc71fa113-S-5dfc705e7242` (1 rows) `EXC-6a4b453cc71fa113-S-7086baffd782` (2 rows) `EXC-6a4b453cc71fa113-S-74ba1bab31bd` (1 rows) `EXC-6a4b453cc71fa113-S-9801a74d17e7` (2 rows) `EXC-6a4b453cc71fa113-S-b1b4f7dea67c` (2 rows) `EXC-6a4b453cc71fa113-S-b56b782b0e5b` (1 rows) `EXC-6a4b453cc71fa113-S-cc79c3eea9ec` (1 rows) `EXC-6a4b453cc71fa113-S-f1ed0d77c782` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 16.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 6902c61a0141a93af335d643ea10ca29d39fed0000d250e634bcf868fc34c638 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 62a1724e15d87dae066e298874c9239dba95dbd16ed8481e66b3245f2933e9a7 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 4f09aaa05e4e6e8a88abbca513607819fc904c108e1bae075769535abecf3569 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 74320d6b06d7a322b9fdbd38143b58ac30ee08a250488b45a1487bfdd0286af1 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2abf091c8a8b1ddfcaccf5eec5800c4ba9d2785e14cdc2b005df8bf504cb290f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-6a9a408e502952cf

- Unique tickets: **10**; unique rows: **12**; ticket–reason occurrences: 10; row–reason occurrences: 12.
- Sources: clinc150_full.
- Source intents: gas, gas_type, jump_start, last_maintenance, mpg, oil_change_how, oil_change_when, schedule_maintenance, tire_change, tire_pressure.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=12.
- Slot patterns (unique rows): NO_SLOTS=12.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-6a9a408e502952cf`. Content binding: `caecebf22c4de4d086ff20a6b0525798b212664b5797da8b95de3f5cb14d455d`.
- Decision subgroups: 10; blanket cluster decisions disabled. `EXC-6a9a408e502952cf-S-3504ba496540` (1 rows) `EXC-6a9a408e502952cf-S-387b2c040ff8` (1 rows) `EXC-6a9a408e502952cf-S-52998ff12650` (2 rows) `EXC-6a9a408e502952cf-S-786c149c8dbf` (2 rows) `EXC-6a9a408e502952cf-S-8a8fca8d70c2` (1 rows) `EXC-6a9a408e502952cf-S-9e0efc4b367d` (1 rows) `EXC-6a9a408e502952cf-S-a92743901814` (1 rows) `EXC-6a9a408e502952cf-S-b1aa7df60655` (1 rows) `EXC-6a9a408e502952cf-S-c8e9316516f6` (1 rows) `EXC-6a9a408e502952cf-S-f53ab073deb0` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 12.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 051dc541e71b21634d2148d388f2c39f35fa5dc522eae552814c1065fbf196ac | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 46c7daea6df38aba4aafce3dad159f3531deb90df8b104844851db20c895b942 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 74872c6f5cdb9463a9458b4ec3f3e4deda222c118abbbac3d9e1709bf36efbc4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 480ad7527bd34163f6f126c7137467b9423dbb5420c4f74bdff2bad5ca345c3f | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2ed2c3d3bd3d65bf475b503a4d12955ebe51d8caa8860db61cf7d9429922fff5 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-6ad5e645f17bd2fe

- Unique tickets: **2**; unique rows: **2**; ticket–reason occurrences: 2; row–reason occurrences: 2.
- Sources: clinc150_full.
- Source intents: smart_home, timer.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=2.
- Slot patterns (unique rows): NO_SLOTS=2.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-6ad5e645f17bd2fe`. Content binding: `66c4bb4fc850c8841e4b2f8bfed2c2f853d245d318ae737304d2e402e8ff84f4`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-6ad5e645f17bd2fe-S-88f749c3c8a3` (1 rows) `EXC-6ad5e645f17bd2fe-S-ca6b1903e64e` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 775fa3fdd04bee46ee3598615e94d6c643b50eb93ea8fa9929b5225e72d7bb00 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 4003bbc786c9672049381d668c92a2780ed6629b888052f0f20dfd66144fd5be | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-6b06f3e6502a5e03

- Unique tickets: **2**; unique rows: **3**; ticket–reason occurrences: 2; row–reason occurrences: 3.
- Sources: clinc150_full.
- Source intents: calendar, meeting_schedule.
- Current domain / action / disposition / OOD patterns: CALENDAR/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): calendar_read_all=3.
- Slot patterns (unique rows): NO_SLOTS=3.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-6b06f3e6502a5e03`. Content binding: `736e4794fde409e83ae273b340a182d67cb9f1a61fc39f8cacb5670f6a29268c`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-6b06f3e6502a5e03-S-277e614466dd` (1 rows) `EXC-6b06f3e6502a5e03-S-b170461c7f19` (2 rows).

### Representative records

3 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 3.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 41f54d69f2c6ea9fbe966da043058348475c15d30d8819df33222bd3d83e9ae2 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | KNOWN_UNSUPPORTED | calendar_read_all | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 85d77c60657ebc275a9691347bda4402d6b0288ea44c829ea3834b880c3945d9 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | KNOWN_UNSUPPORTED | calendar_read_all | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| d9328d424ca8641fb6db2b69f6ec6cf3fcd60004f0247b83bae1b1dfddbf403a | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | KNOWN_UNSUPPORTED | calendar_read_all | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-6b60b66746c251b7

- Unique tickets: **5**; unique rows: **6**; ticket–reason occurrences: 5; row–reason occurrences: 6.
- Sources: clinc150_full.
- Source intents: cancel, maybe, no, repeat, yes.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=6.
- Slot patterns (unique rows): NO_SLOTS=6.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-6b60b66746c251b7`. Content binding: `d0fe527ced43159323ca40be2274d68e1c06d2846c7cf13d9b3db0d823547a53`.
- Decision subgroups: 5; blanket cluster decisions disabled. `EXC-6b60b66746c251b7-S-277a23ea051c` (2 rows) `EXC-6b60b66746c251b7-S-6377d6cc41b6` (1 rows) `EXC-6b60b66746c251b7-S-67f36920ac44` (1 rows) `EXC-6b60b66746c251b7-S-877cf33703f2` (1 rows) `EXC-6b60b66746c251b7-S-914ca928cb58` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 6.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 03cf82071b5c8466d7fee5add3083bc261560bc4607205ed0e103866b3963963 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2a5ce5d7b55fbbc022664a52dc6e87512604a9a3c13244d06538a0e9507a92df | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| c746c0d35b703be538e8dade59afc4a7e45ab58289c13d29cdee9e25ab14f73a | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| e8088ba3f47bda776277459cf4901cefdfa1aa717b932e67ddc62ec0e959af81 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| ba16b99d96f573edea42fb4e31eeec169b350434357a6b8e2cfa23ebeffd5e12 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-6b725b7c7cc72c7c

- Unique tickets: **4**; unique rows: **4**; ticket–reason occurrences: 4; row–reason occurrences: 4.
- Sources: massive_1_1_en_us.
- Source intents: music_dislikeness, music_likeness, music_query, music_settings.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=4.
- Slot patterns (unique rows): NO_SLOTS=4.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-6b725b7c7cc72c7c`. Content binding: `f7b5ddaca8342f103cb47628816a2aa73dae677d5edd0aa98927233ace33126d`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-6b725b7c7cc72c7c-S-320fcfc0cbc3` (1 rows) `EXC-6b725b7c7cc72c7c-S-5011b86680ea` (1 rows) `EXC-6b725b7c7cc72c7c-S-50f5dd27085a` (1 rows) `EXC-6b725b7c7cc72c7c-S-68aebd9ebe3f` (1 rows).

### Representative records

4 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 4.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| f3e704a82a8c7812262517b14d85ec342f6b0ae85239be9b6bd0448f57120f91 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 3cfb3d1c08b4541296717453620b0215fe96ec281be55f155882ad136654b2ef | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 77c339a28d8e4a024eef412bd6295769f42aa742570d034036667260ae3c5aaf | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 30bfcb68f4a89c421994ba1dfabf5f668b292be921c0f30ae3213986ea3abab0 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-6e20356b6b978d63

- Unique tickets: **3**; unique rows: **4**; ticket–reason occurrences: 3; row–reason occurrences: 4.
- Sources: clinc150_full.
- Source intents: flip_coin, roll_dice, tell_joke.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=4.
- Slot patterns (unique rows): NO_SLOTS=4.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-6e20356b6b978d63`. Content binding: `b1766c2a8c6df68d9d8de0874f0bfc1a6b482793f76c31b622129d0c7dc1956d`.
- Decision subgroups: 3; blanket cluster decisions disabled. `EXC-6e20356b6b978d63-S-1fb9a0ed2be0` (1 rows) `EXC-6e20356b6b978d63-S-9deca735feb0` (1 rows) `EXC-6e20356b6b978d63-S-bb204b819603` (2 rows).

### Representative records

4 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 4.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 02896944e5625ad39bb3b59db96afce3033ce196a1456f8df2214c1fbb19082a | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 3175613f105b3c7000a4578691d1766fff86a4caeb87da41ceb57ab4d1354a2d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 029f082f7c5592361f88dd9384b011f041c8148112b05e6f8611b7125ac096b3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 4c9f0537d53e8a07be32c51b0cd48f98a22a45d02cd7429e7f47f22ea2884408 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-6fee95d737e810bf

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: metis_controlled_synthetic.
- Source intents: promise_read_person:compound.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): synthetic:promise_read_person:compound=1.
- Slot patterns (unique rows): PERSON + REFERENCE=1.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-6fee95d737e810bf`. Content binding: `3e0a30ee91c3e026858ac5c160179ad0b75d16d5b1966028203c7a58ecccc61e`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-6fee95d737e810bf-S-0973f0ee521b` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 048ad9bd85cbc1cd732c81538794cdf0da98ad046640971ebdb5d3651f023413 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | SUPPORTED_NEEDS_CONTEXT | synthetic:promise_read_person:compound | PERSON = [WITHHELD]; REFERENCE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-726286e772fecf73

- Unique tickets: **7**; unique rows: **10**; ticket–reason occurrences: 7; row–reason occurrences: 10.
- Sources: clinc150_full.
- Source intents: application_status, bill_balance, bill_due, order_checks, pay_bill, redeem_rewards, transfer.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=10.
- Slot patterns (unique rows): NO_SLOTS=10.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-726286e772fecf73`. Content binding: `0b04f0a369318ebd3fa0137510e9f1c279520bd85628f355473e255bceed1563`.
- Decision subgroups: 7; blanket cluster decisions disabled. `EXC-726286e772fecf73-S-17f537659a4f` (2 rows) `EXC-726286e772fecf73-S-39cf7c34808b` (1 rows) `EXC-726286e772fecf73-S-4697dff01653` (2 rows) `EXC-726286e772fecf73-S-4d3f4d788f44` (1 rows) `EXC-726286e772fecf73-S-75f1aeee8538` (1 rows) `EXC-726286e772fecf73-S-b11ac2ace3ec` (2 rows) `EXC-726286e772fecf73-S-eb7d899e1d5b` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 10.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 38020c6f8b19fa1916de36cde0e818986ea2886fa7ff937634e34a75ddcaedda | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| f338dcad73dc89a9f4b1d7048d08f1e3e528db8956d0beb4b449e8f7863a58c8 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 6f3b06794cc66e8efa86d61f7cb9502b29b232d6081419b6d9dd7423a2109a27 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 54722af1806ffa8ed58a48c491790a5d935c926f05972fd8dfd2c86ceecbbdb2 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| bd232f2b186f0484af598a8489a2a7d4ca658d65c4715a2857ef51b92588a505 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-765b28cb5d610f04

- Unique tickets: **7**; unique rows: **11**; ticket–reason occurrences: 7; row–reason occurrences: 11.
- Sources: clinc150_full.
- Source intents: current_location, directions, distance, find_phone, share_location, traffic, uber.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=11.
- Slot patterns (unique rows): NO_SLOTS=11.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-765b28cb5d610f04`. Content binding: `42874cdef008d4a72fa18437782a3fe35c02d19178fda88465459f5760d28da1`.
- Decision subgroups: 7; blanket cluster decisions disabled. `EXC-765b28cb5d610f04-S-62bf607ce08d` (1 rows) `EXC-765b28cb5d610f04-S-6a6ec90959d4` (2 rows) `EXC-765b28cb5d610f04-S-a4cb9b1d24b7` (2 rows) `EXC-765b28cb5d610f04-S-ad655e1a71e7` (1 rows) `EXC-765b28cb5d610f04-S-e139e10903f7` (2 rows) `EXC-765b28cb5d610f04-S-e75e31d6e4da` (1 rows) `EXC-765b28cb5d610f04-S-fb6cc45e2f0a` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 11.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| b2a13336ee340a7edd63d7b3fa31e975fc739bdbe30bbb2aa9fc353619b58a9c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 24e2421c71fa063a096ca3b698912f0caa3750236e58d1ff267d833409f00dc3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 43161a97b823beb1fba0de7690f2915027c3caef94109869c6ec2d4798a2882c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| b5ef56949c13f57ed152e4802c67e97c096b5d7372c9a666024d20a51120e6ce | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| c7c896f7c7b1b879290affa09d5e1e81857188a5e5b4295be0a588caaba26792 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-77b7dfb86433081f

- Unique tickets: **8**; unique rows: **11**; ticket–reason occurrences: 8; row–reason occurrences: 11.
- Sources: clinc150_full.
- Source intents: apr, credit_limit, credit_limit_change, credit_score, improve_credit_score, international_fees, min_payment, rewards_balance.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=11.
- Slot patterns (unique rows): NO_SLOTS=11.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-77b7dfb86433081f`. Content binding: `9a3b4601d4df53adbfa1f19650822b3c99db7c5f5b638098bd10698ce4dade89`.
- Decision subgroups: 8; blanket cluster decisions disabled. `EXC-77b7dfb86433081f-S-16ba5c004543` (1 rows) `EXC-77b7dfb86433081f-S-27616116e775` (1 rows) `EXC-77b7dfb86433081f-S-31e52cc4253b` (2 rows) `EXC-77b7dfb86433081f-S-509b3866af03` (1 rows) `EXC-77b7dfb86433081f-S-95aece9b9722` (1 rows) `EXC-77b7dfb86433081f-S-b3a0c42c88f6` (2 rows) `EXC-77b7dfb86433081f-S-b869eecb9e6b` (1 rows) `EXC-77b7dfb86433081f-S-e126fc01b18d` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 11.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| d550a352008f08cd0eb2dca10ebf8f77b0a2653237667f616bb359887727e1ff | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 71fc0111a7ab8abff06a2577d7552b0488ff87f74b165b0cb298992a1d9a4417 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 1d9088efd283478d75f0917ba8b1b02fc6078c04a82fc565f48ded838285762c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| ac4449d1ffdc3895d21bfc0661b965c5510e6bb96eeae32bf2d8a73c2f298e9c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| aa3214689bf0640cdcb93e3f6b11df3b8eb52434ccfcfe0eb65c19776bf08a66 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-78030074234df0ab

- Unique tickets: **2**; unique rows: **2**; ticket–reason occurrences: 2; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: datetime_convert, datetime_query.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=2.
- Slot patterns (unique rows): LOCATION=1; TIME + LOCATION + LOCATION=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-78030074234df0ab`. Content binding: `5bd4751d9489215ad9198255499f8f11ba652d258375e330e9210fcd36504db8`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-78030074234df0ab-S-2a4acc11e7a3` (1 rows) `EXC-78030074234df0ab-S-e8d896ecb34b` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 9a4ed47e48a090208fb58991e65682b919fab0101b93a27dce6412f0a4fa1608 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | TIME = [WITHHELD]; LOCATION = [WITHHELD]; LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 3067e75614583949f9cc1f4ad0f8f2996bff890e227ce4ce00c4b5f011e2ea00 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-78b62deb245f2704

- Unique tickets: **1**; unique rows: **65**; ticket–reason occurrences: 1; row–reason occurrences: 65.
- Sources: massive_1_1_en_us.
- Source intents: calendar_set.
- Current domain / action / disposition / OOD patterns: CALENDAR/CREATE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): calendar_create=65.
- Slot patterns (unique rows): NO_SLOTS=36; DATE=7; DATE + EVENT=2; DATE + TIME=1; EVENT=11; EVENT + DATE=5; EVENT + DATE + TIME=1; RECURRENCE=1; TIME + RECURRENCE + DATE=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-78b62deb245f2704`. Content binding: `723886a5ce7dba2385966dd47b98472d6aaa2f5754d8fe5b52f24c996fc6444c`.
- Decision subgroups: 9; blanket cluster decisions disabled. `EXC-78b62deb245f2704-S-0b721398fee1` (1 rows) `EXC-78b62deb245f2704-S-0bcb9715f18b` (5 rows) `EXC-78b62deb245f2704-S-151df6ae0bef` (1 rows) `EXC-78b62deb245f2704-S-40453877b23d` (1 rows) `EXC-78b62deb245f2704-S-493bf4996038` (7 rows) `EXC-78b62deb245f2704-S-680a32d72f6f` (36 rows) `EXC-78b62deb245f2704-S-7862d8353cdf` (2 rows) `EXC-78b62deb245f2704-S-e09b5c158bbc` (11 rows) `EXC-78b62deb245f2704-S-e8563a909af7` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 65.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| fb7be54af38e6e4451e13cc602a2568168a9a7eeb82c42564c37a1ddb6487108 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | EVENT = [WITHHELD]; DATE = [WITHHELD]; TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2cca53b97bd2ea230cb7e8cb8f98ebaecd828ad9e1cc72772a6af1c5eaa06445 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | EVENT = [WITHHELD]; DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 40eb061e85a039ee93261bf8551764296bc64ad264b5ca1bdb5be81c9d8b1b8e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | TIME = [WITHHELD]; RECURRENCE = [WITHHELD]; DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| b82d3b16f2008f9e06a3d6dcaa756638c90691c097a121f3b9cdf48fd19efb7a | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | DATE = [WITHHELD]; TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 24663478b955059ecab40c06eb191b055166b593bf6e16eb25041a70f6ece9e0 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-7b84c4ed7597865a

- Unique tickets: **1**; unique rows: **33**; ticket–reason occurrences: 1; row–reason occurrences: 33.
- Sources: clinc150_full.
- Source intents: oos.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=33.
- Slot patterns (unique rows): NO_SLOTS=33.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-7b84c4ed7597865a`. Content binding: `c35efca4d85b3b3d09fd287340553f5868b98e3a2e8ff66c5189e5b4f1e72b5a`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-7b84c4ed7597865a-S-97dcb05d2a4b` (33 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 33.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 1234b0ce615124571ff080a62f970ecd6ae1d928c9d54c813d09fcbb15f5c6ee | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 1957e5348596b2b820f0772b9b6f0d0ac9783665d832150d740bbe842b097864 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 1d1030999f651225b40c414994b4be6c06b2cff87ef05f69b13e17b1c747831b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 1e0672119f36316d83e771b26fec1afff8bebe9b96e1a9e16beb64e230ac9ffb | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 301af69c241b9bd83e28587615697571137fd5ba3c71bdde6fd46d12bbc88cb5 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-7e7fac4071d131bc

- Unique tickets: **3**; unique rows: **4**; ticket–reason occurrences: 3; row–reason occurrences: 4.
- Sources: clinc150_full.
- Source intents: date, time, what_can_i_ask_you.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=4.
- Slot patterns (unique rows): NO_SLOTS=4.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-7e7fac4071d131bc`. Content binding: `5547dfa4ab3fec4e1132ffac07e04869e181c9bd85fd0ba655c87c1c72fcbbac`.
- Decision subgroups: 3; blanket cluster decisions disabled. `EXC-7e7fac4071d131bc-S-0e1bab1593ab` (2 rows) `EXC-7e7fac4071d131bc-S-df5726dc5b9c` (1 rows) `EXC-7e7fac4071d131bc-S-f7aefd4f51d2` (1 rows).

### Representative records

4 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 4.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| e93b2e1262d40a76bb98e213cbc440a5e2947984aea124103b2ccf5180c38e82 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 20ef4fe9524d50a1741063f35348c59047f5ce01985f36872ca86064c17b46e3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 36706df603991b36e180e8bb8cfd6934e2be4ca9ec6b9468be2080595ae1780a | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| f2553a44e09324c8a740a09543faf79fb3f3ed58a8e4ac3a3b81ea8d03849ee7 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-7f58b993d82d84f0

- Unique tickets: **1**; unique rows: **102**; ticket–reason occurrences: 1; row–reason occurrences: 102.
- Sources: massive_1_1_en_us.
- Source intents: alarm_query.
- Current domain / action / disposition / OOD patterns: ALARM/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_read=102.
- Slot patterns (unique rows): NO_SLOTS=68; DATE=16; DATE + TIME=7; TIME=11.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-7f58b993d82d84f0`. Content binding: `bda891bf50b6b4242cb3b117e893a7ab17d7547f230f0e8fc2c635b9f6cff162`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-7f58b993d82d84f0-S-0aacbb20bee3` (16 rows) `EXC-7f58b993d82d84f0-S-151d7e82cf1a` (68 rows) `EXC-7f58b993d82d84f0-S-ec5e317b0ef0` (11 rows) `EXC-7f58b993d82d84f0-S-f73591681063` (7 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 102.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 060c4b321cbe4547f8df9903d4dcc4a1c84729878b3b2baa5093a4b07246112b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 0751577b9f7954089d646e6c9314d22b6181ed661bcdf48690bb93d31b83b33b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 0467e4896fbd95c0c70dd8c07865f7f8b85ffa7fcba0f3d51ec6701a36cec2f3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 0fe400b1ce30a062d48e4d2dcc68f533ee105e35d6f9a80072047ee094434e11 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | DATE = [WITHHELD]; TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 17ddca42bca7a879290fdffe6c7c6dab0bf9172a8d76e01e63ae6d16aef27f18 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-82899b499f8a4f2c

- Unique tickets: **4**; unique rows: **5**; ticket–reason occurrences: 4; row–reason occurrences: 5.
- Sources: clinc150_full.
- Source intents: accept_reservations, restaurant_reservation, restaurant_reviews, restaurant_suggestion.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=5.
- Slot patterns (unique rows): NO_SLOTS=5.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-82899b499f8a4f2c`. Content binding: `dcec1ec8f606de890ccb195919db23fe9e390ac2c9221471eb824205ef936354`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-82899b499f8a4f2c-S-04ec308b8db1` (2 rows) `EXC-82899b499f8a4f2c-S-3f00e9012aca` (1 rows) `EXC-82899b499f8a4f2c-S-7d6891ce6676` (1 rows) `EXC-82899b499f8a4f2c-S-7fa3b25404e8` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 5.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 210fd938ed695d3cb06ad2d8a59be420198e53f4d024cd1ccff41d31cdf7a96d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 4aad07766e242c125cb6ac1e1ceecf815e48a061e5ab70bd9f49462156155ee7 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 6b1eb366538a668cceda8e7eb85ad1cb62583ec1977928b60908402b918a9afc | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 377b5064df700a4f916cc9756d30f2932c765beefa78a85c08af4a588c3036a8 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 57e3f959d312df8914494158660b8ec6a0d7e47ea382f26cddf61c73e35f4598 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-82f131883e976e6b

- Unique tickets: **2**; unique rows: **2**; ticket–reason occurrences: 2; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: play_audiobook, play_music.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): UNRESOLVED=1; unknown_external_action=1.
- Slot patterns (unique rows): KIND=1; SUBJECT=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-82f131883e976e6b`. Content binding: `2379d1f24b50e9e80bd1bba48acecae3079a1ed808ebab7ee305c67c965e1b76`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-82f131883e976e6b-S-08dc9ec23e01` (1 rows) `EXC-82f131883e976e6b-S-b9029c0da3d0` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| dfe6fd9b3100a7539bcb860cc66a1885270a28d26d49bdae2f9c1fcf61429861 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 2ecd2da2f781717af77f13133dce2f287493a9e7c161ec75119847db518f02d4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | UNRESOLVED | SUBJECT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-832b9bb54de258e7

- Unique tickets: **2**; unique rows: **2**; ticket–reason occurrences: 2; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: recommendation_events, recommendation_movies.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=2.
- Slot patterns (unique rows): NO_SLOTS=2.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-832b9bb54de258e7`. Content binding: `24bac88855cd3df322ca62f110edb0440ad54e8d4b691159c51258894567774f`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-832b9bb54de258e7-S-658f91e9e7e6` (1 rows) `EXC-832b9bb54de258e7-S-bdc7a40c7262` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 4943aa8c7f7392b31d415b9e6cd8a2eb51ac50d08ac5290fc8bf9185078fb259 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 66a9630e577704a07a2a53f894ecba3dea36f805798fc4ece58ad881bff84a93 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-875c830ea2c8cd90

- Unique tickets: **8**; unique rows: **11**; ticket–reason occurrences: 8; row–reason occurrences: 11.
- Sources: clinc150_full.
- Source intents: calories, cook_time, food_last, ingredient_substitution, ingredients_list, meal_suggestion, nutrition_info, recipe.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=11.
- Slot patterns (unique rows): NO_SLOTS=11.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-875c830ea2c8cd90`. Content binding: `5d659bd013b20f1334e82a112349d4872f9717170a652fd4231966843c50a6f3`.
- Decision subgroups: 8; blanket cluster decisions disabled. `EXC-875c830ea2c8cd90-S-0f9fb577ed71` (1 rows) `EXC-875c830ea2c8cd90-S-12ba92b67a77` (1 rows) `EXC-875c830ea2c8cd90-S-171e62962d29` (1 rows) `EXC-875c830ea2c8cd90-S-85f40cc69995` (2 rows) `EXC-875c830ea2c8cd90-S-adc2e98f4f19` (2 rows) `EXC-875c830ea2c8cd90-S-d608fdb6746b` (1 rows) `EXC-875c830ea2c8cd90-S-d65abeea3585` (1 rows) `EXC-875c830ea2c8cd90-S-ebeb5d891b87` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 11.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| b76ccc4a0f8090236c1a897418e352ba404248cec51a8a33fce886d851d68056 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 8ec99416e6d0983a9350f8f264ac87f4cf08303ef8114695433c82dc599029ba | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 57c46316cda82e173e6be1d7563620d0f96af228633ef25e834b721f1ce59e8b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 12d0ccabb9050ebf8fb0d94edda2131b6f98f872c7c955e4d3fe7dffb74f8f96 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 212014b64b8727ba38cb5a1aa420754eb28124e24508fb9ad13b827c55413953 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-8767b8d4aadfcba4

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: massive_1_1_en_us.
- Source intents: alarm_query.
- Current domain / action / disposition / OOD patterns: ALARM/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_read=1.
- Slot patterns (unique rows): TIME=1.
- Primary reason: `TEMPORAL_CONTEXT_UNRESOLVED`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-8767b8d4aadfcba4`. Content binding: `5ef69d7e4ad8aefe529c766d9d7d61b304731c3679933d5bc67ab8f1e78673cb`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-8767b8d4aadfcba4-S-ec5e317b0ef0` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 9a868e51e4076386da678d38abb92549ddbeaf3b45a4c53d71cb0273d627b1b2 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | READ | SUPPORTED_NEEDS_CONTEXT | alarm_read | TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-894d8c362e7d71f6

- Unique tickets: **4**; unique rows: **4**; ticket–reason occurrences: 4; row–reason occurrences: 4.
- Sources: clinc150_full.
- Source intents: order, order_status, shopping_list, shopping_list_update.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=4.
- Slot patterns (unique rows): NO_SLOTS=4.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-894d8c362e7d71f6`. Content binding: `55dcfb48fbe1973838b041feeb1b885b7244890ec22ffb0d680405bec230db11`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-894d8c362e7d71f6-S-31bb9cbdd0c9` (1 rows) `EXC-894d8c362e7d71f6-S-8d6ea83ca0d2` (1 rows) `EXC-894d8c362e7d71f6-S-e49b7e63f0ac` (1 rows) `EXC-894d8c362e7d71f6-S-eefd9f732278` (1 rows).

### Representative records

4 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 4.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| edf8c8a464a1a41b06db8b75ec8587f14e66e5935119607b9a432563e04bc49b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| f57d039b5e90778766730c10044523af2387d32f68ea999fd036895476df7d06 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| efcb1b90f89e29ff27b630351006eb556d03fa865dea85c30233d55cbe07f4eb | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 5d99f5a8510060ac43c84df6db1a61d6188690ab52f32f2e1b3f09b81dfa1117 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-8ff4fbc5d92e3d5c

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: clinc150_full.
- Source intents: make_call.
- Current domain / action / disposition / OOD patterns: COMMUNICATION/CALL/KNOWN_UNSUPPORTED/False.
- Families (unique rows): communication_call=1.
- Slot patterns (unique rows): NO_SLOTS=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-8ff4fbc5d92e3d5c`. Content binding: `5309414bebefbe43837ab3e48f1b46858a1f1b33ba745f557ab598aaf3431426`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-8ff4fbc5d92e3d5c-S-7723f10ac509` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| ecf8ed987ce42374c78205df7d7ddd790d12b4d657a2ccbdbd6f33b5317d23dc | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | CALL | KNOWN_UNSUPPORTED | communication_call | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-90acaec4b09f8204

- Unique tickets: **1**; unique rows: **21**; ticket–reason occurrences: 1; row–reason occurrences: 21.
- Sources: massive_1_1_en_us.
- Source intents: alarm_set.
- Current domain / action / disposition / OOD patterns: ALARM/SET/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_set=21.
- Slot patterns (unique rows): DATE + DATE + TIME + TIME=1; DATE + TIME=1; DATE + TIME + TIME=2; TIME=8; TIME + DATE + TIME=4; TIME + RECURRENCE=1; TIME + TIME=3; TIME + TIME + DATE=1.
- Primary reason: `TEMPORAL_CONTEXT_UNRESOLVED`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-90acaec4b09f8204`. Content binding: `5d20c2f3bda4eef966175ecbfd19c87827d1e0c3ad9b0067f298660bdf71cb93`.
- Decision subgroups: 8; blanket cluster decisions disabled. `EXC-90acaec4b09f8204-S-15ec8d4e4b64` (3 rows) `EXC-90acaec4b09f8204-S-1df393ebea27` (2 rows) `EXC-90acaec4b09f8204-S-31a6787164d6` (8 rows) `EXC-90acaec4b09f8204-S-3c68c733d2c9` (1 rows) `EXC-90acaec4b09f8204-S-75c824967b50` (4 rows) `EXC-90acaec4b09f8204-S-cd9dd30c8e75` (1 rows) `EXC-90acaec4b09f8204-S-cf58cc55615e` (1 rows) `EXC-90acaec4b09f8204-S-f258cb50a4a0` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 21.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 8a631ddaf45eb39fba5fd43ba2f733a1b4a90ab3284400140c6090eef4a264be | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 820633da533f380e63585b5f2fe0021e6d45b8ccb3616e533e6bc45316bda68b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | DATE = [WITHHELD]; TIME = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 1d520dd62b205bc54ac1ed622dce254d102befe04e5a78d606a60c663af74222 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 90d679a7ed607af5ce98ecc3d69b640599f1002d1491456a19007760c9557895 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD]; RECURRENCE = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 20236d2093919b0ee0fea49fd67e6ce50862246d9aab4a0444f6d8df9493219c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD]; DATE = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-91c03d51610c6761

- Unique tickets: **1**; unique rows: **8**; ticket–reason occurrences: 1; row–reason occurrences: 8.
- Sources: massive_1_1_en_us.
- Source intents: alarm_remove.
- Current domain / action / disposition / OOD patterns: ALARM/DELETE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_delete=8.
- Slot patterns (unique rows): DATE + TIME=5; TIME=3.
- Primary reason: `TEMPORAL_CONTEXT_UNRESOLVED`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-91c03d51610c6761`. Content binding: `dbcb8f1f4ee2c2fafda265cbace01fa56d3c8f36c0395038250a0cd76ec8faf4`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-91c03d51610c6761-S-df599d924530` (5 rows) `EXC-91c03d51610c6761-S-e6969a681010` (3 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 8.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 0797679383dac638558a97b0c076a3978ced73eb924d98372ceba20be59ae1b4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | DATE = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 431922cf964617568fcc0b847760f2664fbe050aaa524edd0fb675cf942c6e11 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 2086452bfeea37f7d70b0bf6d9867e36dc8764273583860f1fab9676fc741220 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | DATE = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 60caa569b629da827446003933f693961e2800af71ab2f1a5956ff68276cdfc5 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| baa156737ac2e8f3e61ead3a20b41d2deb637e7e29f74cc76098c08753c13340 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | DELETE | SUPPORTED_NEEDS_CONTEXT | alarm_delete | DATE = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-9237b6f8db1fe5db

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: clinc150_full.
- Source intents: text.
- Current domain / action / disposition / OOD patterns: COMMUNICATION/SEND/KNOWN_UNSUPPORTED/False.
- Families (unique rows): communication_send=1.
- Slot patterns (unique rows): NO_SLOTS=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-9237b6f8db1fe5db`. Content binding: `70072a8b011dca7bb55a2035a2282e1be7b2ebfb72496f3043110aebfea65392`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-9237b6f8db1fe5db-S-3c1e073b77e2` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 7bbd8ee64b6909c5eaa1795edc334e751fc32c8dea0ebd46dc41c6e528cf4d74 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | KNOWN_UNSUPPORTED | communication_send | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-930e2b581d69703b

- Unique tickets: **8**; unique rows: **11**; ticket–reason occurrences: 8; row–reason occurrences: 11.
- Sources: clinc150_full.
- Source intents: card_declined, damaged_card, freeze_account, new_card, pin_change, replacement_card_duration, report_fraud, report_lost_card.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=11.
- Slot patterns (unique rows): NO_SLOTS=11.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-930e2b581d69703b`. Content binding: `c49ad85a961f7d794028637d278f303b569225fa1fcd2cb11fe6458d1cdeeec9`.
- Decision subgroups: 8; blanket cluster decisions disabled. `EXC-930e2b581d69703b-S-235f9c16b0b5` (2 rows) `EXC-930e2b581d69703b-S-319854ea9025` (2 rows) `EXC-930e2b581d69703b-S-4ad2c0f86650` (1 rows) `EXC-930e2b581d69703b-S-5a33aa649feb` (1 rows) `EXC-930e2b581d69703b-S-95a3b2d5dbba` (2 rows) `EXC-930e2b581d69703b-S-9d643f6e0d69` (1 rows) `EXC-930e2b581d69703b-S-c60872a1eacc` (1 rows) `EXC-930e2b581d69703b-S-ca9137d9cc84` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 11.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 2eb4ea8f11c51046021775e1fa8f52f8cf87db68a2a7fbcd9133150719cbbb19 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 20c44b3025730dfb0ee2b44a52954759c9ab56c3bdf0a28c368f9e1e2b872535 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| bf41f60897b55437df945bb472625201d3ab1318f6dccfdcdc447e353204611b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 442aaee8935615b28937ad520b04a8bd6a79d3a0a6e562b47b4348d9a3b04ac8 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 531bdbd4c3f0027cce10b38769eecfac0ec3fcd9028beb135735bc10865127d6 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-9ca0ede7199e9008

- Unique tickets: **2**; unique rows: **2**; ticket–reason occurrences: 2; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: play_podcasts, play_radio.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=2.
- Slot patterns (unique rows): NO_SLOTS=2.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-9ca0ede7199e9008`. Content binding: `d0b4f531ce0c59895c0750ccd4b98aacb71826871bbe9e6e5c105456ec7cbe21`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-9ca0ede7199e9008-S-4e5c07f1af83` (1 rows) `EXC-9ca0ede7199e9008-S-8a1e8deb797c` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 9d4c675559428a6db2e3851cde4fd7cc5dd928a8b25747524938694ba103bf65 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| c344def722690326d8780cf1a938c918c93ce50ee3ce7fde6f8e4024a7cefa5d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-9e21d5dc338dfe11

- Unique tickets: **1**; unique rows: **5**; ticket–reason occurrences: 1; row–reason occurrences: 5.
- Sources: massive_1_1_en_us.
- Source intents: calendar_set.
- Current domain / action / disposition / OOD patterns: CALENDAR/CREATE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): calendar_create=5.
- Slot patterns (unique rows): DATE + TIME + DATE=1; DATE + TIME + EVENT=1; EVENT + DATE + TIME + TIME=2; TIME + EVENT + TIME=1.
- Primary reason: `TEMPORAL_CONTEXT_UNRESOLVED`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-9e21d5dc338dfe11`. Content binding: `a997775fff4549e1df2e2e8d18631ce01e0002d710d53e9896bc039b8397eec0`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-9e21d5dc338dfe11-S-30305921ef98` (1 rows) `EXC-9e21d5dc338dfe11-S-46f3162fc3ef` (1 rows) `EXC-9e21d5dc338dfe11-S-a4bb856f3b6a` (1 rows) `EXC-9e21d5dc338dfe11-S-e1a27b60eb0f` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 5.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| e87fbb1f4a638329135b460d78a42ddf0e8027de0b8209f313884b0f1f2940c9 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | TIME = [WITHHELD]; EVENT = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| b70ec76dadebf49b1fa093536e92533463280dbacb3e747206b3e1c7844f20e2 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | DATE = [WITHHELD]; TIME = [WITHHELD]; EVENT = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| aaca17c74c41ddc7fa4f94946b42119768a71a574e4c337f12a1fb40aac07408 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | DATE = [WITHHELD]; TIME = [WITHHELD]; DATE = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 3907469cc4ced9c5aa76ffd30cb2ba9740cb54255936208c0eb820743527ee42 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | EVENT = [WITHHELD]; DATE = [WITHHELD]; TIME = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| e2c9fcd57277cb2ab5a795c3a6890e8929d76b6d6c1f60147a6498a5324e6209 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | SUPPORTED_NEEDS_CONTEXT | calendar_create | EVENT = [WITHHELD]; DATE = [WITHHELD]; TIME = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-a12476addbf041c5

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: clinc150_full.
- Source intents: schedule_meeting.
- Current domain / action / disposition / OOD patterns: CALENDAR/CREATE/KNOWN_UNSUPPORTED/False.
- Families (unique rows): calendar_create=1.
- Slot patterns (unique rows): NO_SLOTS=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-a12476addbf041c5`. Content binding: `40206d5740b15f3697557abb76960eea6299c791be59c4971b3e5ef152e6aaee`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-a12476addbf041c5-S-cea54ac5d645` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| e669c2125c2bd2416cf48ed2b403076cbfda57a724426299536b89c0467b7c37 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | CREATE | KNOWN_UNSUPPORTED | calendar_create | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-a463b1755e4470b4

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: massive_1_1_en_us.
- Source intents: alarm_set.
- Current domain / action / disposition / OOD patterns: ALARM/SET/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): alarm_set=1.
- Slot patterns (unique rows): TIME=1.
- Primary reason: `UNEXPECTED_SECOND_ACTION`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-a463b1755e4470b4`. Content binding: `4c7beed872484fdf8272da3dabe8f8bbe2cbedcc8459e1b08e7eb88e831de8c0`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-a463b1755e4470b4-S-31a6787164d6` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 65c1f593d5579c1103331ffe0cfdf812451bbd1d7225e1facde24977535b3f93 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | ALARM | SET | SUPPORTED_NEEDS_CONTEXT | alarm_set | TIME = [WITHHELD] | UNEXPECTED_SECOND_ACTION |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-a48003a21909be50

- Unique tickets: **1**; unique rows: **8**; ticket–reason occurrences: 1; row–reason occurrences: 8.
- Sources: massive_1_1_en_us.
- Source intents: calendar_remove.
- Current domain / action / disposition / OOD patterns: CALENDAR/DELETE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): calendar_delete=8.
- Slot patterns (unique rows): NO_SLOTS=8.
- Primary reason: `UNEXPECTED_SECOND_ACTION`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-a48003a21909be50`. Content binding: `eda6b5798b2fd5febc6fa638a081972193fffffa50c0bed49f86b8e326353350`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-a48003a21909be50-S-79b8f3a3f145` (8 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 8.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 03d78b131d92633eb155363a507b79d8c8da5efbfd31b0738ff7e0873a5074e1 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | none | UNEXPECTED_SECOND_ACTION |
| 0d9f61f77af1ae7289a1c93601e8d73fdc0a7de0628818de20510e31c142f9a3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | none | UNEXPECTED_SECOND_ACTION |
| 3158f44b73b06f91f72c4c7f9d77d38b5d149b10dcd66e9b67cbe8122c9ea110 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | none | UNEXPECTED_SECOND_ACTION |
| 82c63c6b9b110ca49c0b14a9494a5ad0e5dde2025329a655078238404678eaf7 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | none | UNEXPECTED_SECOND_ACTION |
| c23666690c0387ace70907d49f673cc01709e159a6f74976fce2d88872964012 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | none | UNEXPECTED_SECOND_ACTION |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-a5adebdce4c726ea

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: massive_1_1_en_us.
- Source intents: transport_traffic.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=1.
- Slot patterns (unique rows): NO_SLOTS=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-a5adebdce4c726ea`. Content binding: `be63e238ad87ef5e2c1750d98812a59442eaef662b3116fbfa8f14652c0df1f0`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-a5adebdce4c726ea-S-1e56943cebf7` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 8eb1429d630d9698eb4241b2e5e24de555f4ffd457fb34602c4129bd40aefe39 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-ab444d02c953db80

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: massive_1_1_en_us.
- Source intents: cooking_query.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=1.
- Slot patterns (unique rows): NO_SLOTS=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-ab444d02c953db80`. Content binding: `d1e6ffa8b451a9c0fb176bc17dfdb25688596fa003514d25bb510af975c8898b`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-ab444d02c953db80-S-8c4c620ae160` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 7ccef174d1f3db29cf68cbd1ffc89c50f9bc93ba25ba06ee26ca25cfb5ab699e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-ac9f1455fb252d64

- Unique tickets: **1**; unique rows: **154**; ticket–reason occurrences: 1; row–reason occurrences: 154.
- Sources: massive_1_1_en_us.
- Source intents: calendar_remove.
- Current domain / action / disposition / OOD patterns: CALENDAR/DELETE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): calendar_delete=154.
- Slot patterns (unique rows): NO_SLOTS=112; DATE=22; DATE + EVENT=1; EVENT=7; EVENT + DATE=12.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-ac9f1455fb252d64`. Content binding: `39e934846ff2edcad4520d487770eb544aab0d14f9609e3ddcea059764ebdd3e`.
- Decision subgroups: 5; blanket cluster decisions disabled. `EXC-ac9f1455fb252d64-S-19c209bff76a` (1 rows) `EXC-ac9f1455fb252d64-S-4257c2c1e629` (22 rows) `EXC-ac9f1455fb252d64-S-79b8f3a3f145` (112 rows) `EXC-ac9f1455fb252d64-S-add5dc68b3e3` (12 rows) `EXC-ac9f1455fb252d64-S-bc8b75659846` (7 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 154.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| b9297556cbda9262f403cc144f35381caad66c43c07ff533fe0ddc452fe8e1af | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | DATE = [WITHHELD]; EVENT = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 05ea537273f19e08e48cc593020c2516d6cfd587b760e58ca3945183c99c0364 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 011c131aa462dc83ffa99b3264beb954a0e35eb2f864277dba53a27f22e366f9 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 21fc0d835e6141a5fa04e9f6aa36f586b9048347e9236b232a1ae115757e9f11 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | EVENT = [WITHHELD]; DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 3470e1ea14f028214aa54fa63ba2aabf856394f8af0893eb4fa56c71bfa42674 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | EVENT = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-b38d715efbc4ee77

- Unique tickets: **1**; unique rows: **82**; ticket–reason occurrences: 1; row–reason occurrences: 82.
- Sources: massive_1_1_en_us.
- Source intents: calendar_remove.
- Current domain / action / disposition / OOD patterns: CALENDAR/DELETE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): calendar_delete=82.
- Slot patterns (unique rows): DATE=12; DATE + DATE=1; DATE + EVENT=1; EVENT=18; EVENT + DATE=19; EVENT + DATE + TIME=7; EVENT + LOCATION=1; EVENT + PERSON=1; EVENT + PERSON + DATE=1; EVENT + TIME=1; KIND + DATE=4; KIND + DATE + TIME=1; KIND + KIND + DATE=1; KIND + LOCATION=1; KIND + PERSON=2; PERSON + EVENT=3; REFERENCE + KIND + DATE=1; SUBJECT=1; TIME=5; TIME + EVENT=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-b38d715efbc4ee77`. Content binding: `b7a61142118de7f616cbb4c6351309d184d98689e42bc432dc1c2cf6354b4474`.
- Decision subgroups: 20; blanket cluster decisions disabled. `EXC-b38d715efbc4ee77-S-0caf5948ee0e` (3 rows) `EXC-b38d715efbc4ee77-S-188d281c2df0` (1 rows) `EXC-b38d715efbc4ee77-S-19c209bff76a` (1 rows) `EXC-b38d715efbc4ee77-S-1ea62a3bfe93` (1 rows) `EXC-b38d715efbc4ee77-S-21cb29e7ef7c` (1 rows) `EXC-b38d715efbc4ee77-S-2ab045b7b367` (5 rows) `EXC-b38d715efbc4ee77-S-3e59ed99b680` (1 rows) `EXC-b38d715efbc4ee77-S-4257c2c1e629` (12 rows) `EXC-b38d715efbc4ee77-S-594de288a276` (1 rows) `EXC-b38d715efbc4ee77-S-60ec8f59a2f7` (1 rows) `EXC-b38d715efbc4ee77-S-72a92c0527a2` (1 rows) `EXC-b38d715efbc4ee77-S-7ff54eb41101` (1 rows) `EXC-b38d715efbc4ee77-S-8a6b4a41a957` (4 rows) `EXC-b38d715efbc4ee77-S-9d9d58c3d057` (1 rows) `EXC-b38d715efbc4ee77-S-a954d0a4b7b1` (1 rows) `EXC-b38d715efbc4ee77-S-add5dc68b3e3` (19 rows) `EXC-b38d715efbc4ee77-S-ba278f0f65e5` (7 rows) `EXC-b38d715efbc4ee77-S-bc8b75659846` (18 rows) `EXC-b38d715efbc4ee77-S-e0da143cdf30` (1 rows) `EXC-b38d715efbc4ee77-S-f3bf74b2f75c` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 82.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 604a11f35b910fc7f957e7fe44eab513382d71128a748b0623e91561f548e54e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | PERSON = [WITHHELD]; EVENT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| d54db01e34164002bf0d7a40f93e065605892abb130a17caa123b72a5a936158 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | DATE = [WITHHELD]; DATE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| b4c113657686246847988919d5e8d244035c5ac9f6b116573c3c9b1ecff806f1 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | DATE = [WITHHELD]; EVENT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 484634bd0c253eec2938712fe621ecadd2984b9972c19e1140ef8f94fc838795 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | KIND = [WITHHELD]; DATE = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| b66f5431d837b9f5756a3a6b774dcbdaf01d74978ec8d0820f007862662d6399 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | REFERENCE = [WITHHELD]; KIND = [WITHHELD]; DATE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-b5bed18b8c197555

- Unique tickets: **1**; unique rows: **137**; ticket–reason occurrences: 1; row–reason occurrences: 137.
- Sources: massive_1_1_en_us.
- Source intents: calendar_query.
- Current domain / action / disposition / OOD patterns: CALENDAR/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): UNRESOLVED=1; calendar_read_all=55; calendar_read_date=53; calendar_read_date_event=16; calendar_read_event=12.
- Slot patterns (unique rows): NO_SLOTS=55; DATE=50; DATE + EVENT=1; DATE + TIME=3; EVENT=11; EVENT + DATE=15; EVENT + TIME=1; TIME=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-b5bed18b8c197555`. Content binding: `07de872d1baa3351784a36d7e319fd72fab7200a0661e33f6286b73be662767c`.
- Decision subgroups: 8; blanket cluster decisions disabled. `EXC-b5bed18b8c197555-S-14dc34405aa2` (1 rows) `EXC-b5bed18b8c197555-S-2fd73fadf08e` (11 rows) `EXC-b5bed18b8c197555-S-9c1ed26ee44d` (3 rows) `EXC-b5bed18b8c197555-S-a029c0af8071` (1 rows) `EXC-b5bed18b8c197555-S-b29288ae9522` (55 rows) `EXC-b5bed18b8c197555-S-eb0abe18a87f` (1 rows) `EXC-b5bed18b8c197555-S-f9da746695d5` (50 rows) `EXC-b5bed18b8c197555-S-fb6389f846da` (15 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 137.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 4f6b40f281f8e736806c1dceb27a89e1321e84f9478f292e8ee4d537fe0d66c4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_event | EVENT = [WITHHELD]; TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 11023e700fa8d201efd6731675aea85d43c0cf22f0dc02b1471a5d933e13fd5c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_event | EVENT = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 383f4fa3a1a8a34cafddfe6385436ab7af9ab59f48634f0bf73e98c129256ff8 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_date | DATE = [WITHHELD]; TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2b76ab69c09f376e8b21b9574f818d7243b0d6a85673cb1cd320cf0f48890e9e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_date_event | DATE = [WITHHELD]; EVENT = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 0654044899a3ab4db2990da24544402a1c97310d28227aa53e7bb4100b5b4b9e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_all | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-beef4596ff3df7ee

- Unique tickets: **12**; unique rows: **15**; ticket–reason occurrences: 12; row–reason occurrences: 15.
- Sources: clinc150_full.
- Source intents: are_you_a_bot, do_you_have_pets, goodbye, greeting, how_busy, how_old_are_you, thank_you, what_are_your_hobbies, what_is_your_name, where_are_you_from, who_do_you_work_for, who_made_you.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=15.
- Slot patterns (unique rows): NO_SLOTS=15.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-beef4596ff3df7ee`. Content binding: `daa14c18399c0120d5d4365d46e4494482ab1cc0e0401d9f35c74ee65497bb0d`.
- Decision subgroups: 12; blanket cluster decisions disabled. `EXC-beef4596ff3df7ee-S-0ed7633c1d87` (1 rows) `EXC-beef4596ff3df7ee-S-1bfee306632c` (1 rows) `EXC-beef4596ff3df7ee-S-1d3e6bf11426` (1 rows) `EXC-beef4596ff3df7ee-S-345f3d136b06` (1 rows) `EXC-beef4596ff3df7ee-S-36ac3ef9b107` (1 rows) `EXC-beef4596ff3df7ee-S-8f37aa00efbe` (1 rows) `EXC-beef4596ff3df7ee-S-9152e97422bb` (1 rows) `EXC-beef4596ff3df7ee-S-928a82b8f85f` (1 rows) `EXC-beef4596ff3df7ee-S-b24c8485587a` (2 rows) `EXC-beef4596ff3df7ee-S-e2fee305ffba` (1 rows) `EXC-beef4596ff3df7ee-S-ed78269918fb` (2 rows) `EXC-beef4596ff3df7ee-S-f82ff382d3a5` (2 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 15.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| f60b2589e3d22adbecbef68940f7162719bc314d25c33c0cf7adc8ab3f620772 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| c5a9bc4f8a4a8aea96fb7c3d427d3bda3ae8c3e1ac3a8bf0e3506e0a9b0e3b14 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 59bebc6b92cecee621dfa82352d6c6c022c00592ab11cd49cd19a0f11ff55ece | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 58a43622a278894bf31b776195686023f91a5863ede2352037cde20366889ff2 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 7105ed4a9935f3c3122e6c07ad4c1642fd030eb74ca128b18ac8b9e7d3f6048c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-c6569f75b939c72e

- Unique tickets: **2**; unique rows: **2**; ticket–reason occurrences: 2; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: qa_factoid, qa_maths.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=2.
- Slot patterns (unique rows): NO_SLOTS=2.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-c6569f75b939c72e`. Content binding: `05e299ae2ba91be15372fe24c5f6a5706ff0fef67253dffc7dd214934e0164ea`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-c6569f75b939c72e-S-569ee9ee839f` (1 rows) `EXC-c6569f75b939c72e-S-ccd83bf6288b` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 7d2ab6d7dfc048dfa36baa45c0597ba47a64ba9d197f00c7c8f2466651454201 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| f37b35115417426f601150f51f62e4021ea3b06453ed23fcff5fc4df3db3ddfd | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-c68cb09dce60ad0f

- Unique tickets: **1**; unique rows: **6**; ticket–reason occurrences: 1; row–reason occurrences: 6.
- Sources: metis_controlled_synthetic.
- Source intents: task_read_subject_date_status.
- Current domain / action / disposition / OOD patterns: TASK/READ/SUPPORTED_COMPLETE/False.
- Families (unique rows): synthetic:task_read_subject_date_status=6.
- Slot patterns (unique rows): STATUS + SUBJECT + DATE=6.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-c68cb09dce60ad0f`. Content binding: `e955a08c045b5cd28e1938838253561137f6a4d517e8be7d2c8eb22e40d1b0f6`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-c68cb09dce60ad0f-S-38638895f27d` (6 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 6.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 4741903a88b915d130abe99dab6ccb50f61e9d58af50f092906527e1bc3c87f7 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | READ | SUPPORTED_COMPLETE | synthetic:task_read_subject_date_status | STATUS = [WITHHELD]; SUBJECT = [WITHHELD]; DATE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| 78a0c0cac4ae617677874d0a799b3817607d4e8b3c980e4ede2cff5b8a6caa95 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | READ | SUPPORTED_COMPLETE | synthetic:task_read_subject_date_status | STATUS = [WITHHELD]; SUBJECT = [WITHHELD]; DATE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| b040d98cb9a41036eabee7e4aad5246a2f541c2988ca5d60e0e37dafea6cbe92 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | READ | SUPPORTED_COMPLETE | synthetic:task_read_subject_date_status | STATUS = [WITHHELD]; SUBJECT = [WITHHELD]; DATE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| bfb8dc6e1b6f6deff3f693321803924279e6bee08dce9f0a5fa786cf30aee7f0 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | READ | SUPPORTED_COMPLETE | synthetic:task_read_subject_date_status | STATUS = [WITHHELD]; SUBJECT = [WITHHELD]; DATE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |
| d1ea47fca841bec11146b4c10f23a0e58f323e3fc3b83710e191bf81f008053d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | READ | SUPPORTED_COMPLETE | synthetic:task_read_subject_date_status | STATUS = [WITHHELD]; SUBJECT = [WITHHELD]; DATE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-ce7df8c207be0275

- Unique tickets: **5**; unique rows: **5**; ticket–reason occurrences: 5; row–reason occurrences: 5.
- Sources: massive_1_1_en_us.
- Source intents: iot_cleaning, iot_coffee, iot_hue_lightdim, iot_hue_lightoff, iot_wemo_off.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=5.
- Slot patterns (unique rows): KIND=3; LOCATION=2.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-ce7df8c207be0275`. Content binding: `3181ea31d429cf56ea2e08386db8af2f5c4d374db401accdb21227be577c5980`.
- Decision subgroups: 5; blanket cluster decisions disabled. `EXC-ce7df8c207be0275-S-3a78d09af7bf` (1 rows) `EXC-ce7df8c207be0275-S-5af2f1a7ac21` (1 rows) `EXC-ce7df8c207be0275-S-67ba261956f4` (1 rows) `EXC-ce7df8c207be0275-S-e00cddb23fcc` (1 rows) `EXC-ce7df8c207be0275-S-e713ddda9c21` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 5.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 88b1229ea951ee88826f827124628ff5a3cf2f398038fae9f4f9aff9a0c223f3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 6dc5f2757c7ace7bed671e5750b2a5ff3c16a0b14109c68f9fc0c04131845214 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| b393e121c49dc2f7f6e7f380b4d0e19123b50a7f67fb42c2963f7fa3004887b7 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| dc388e78ba781c473786595cd3d8f56851d145c2d7c38167d0c8439173701530 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| bea01fbf07c07a6e9f204285ce375432b81e18856bbc50eb63e92f4a21d0038b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-d176e6422284a976

- Unique tickets: **1**; unique rows: **2**; ticket–reason occurrences: 1; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: calendar_query.
- Current domain / action / disposition / OOD patterns: CALENDAR/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): calendar_read_date=1; calendar_read_date_event_time=1.
- Slot patterns (unique rows): DATE + TIME + TIME=1; EVENT + DATE + TIME=1.
- Primary reason: `TEMPORAL_CONTEXT_UNRESOLVED`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-d176e6422284a976`. Content binding: `b84e3ad566c629b540391c32100ba8c2de0c51f72b1425ef645ab3ce73bac53d`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-d176e6422284a976-S-3218209754c5` (1 rows) `EXC-d176e6422284a976-S-3882fddf25e8` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| df5ff3c2618e599515577578c61b2ddf745830cd94f2b0a2d49f4ebeb086f497 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_date | DATE = [WITHHELD]; TIME = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| c739ab13703fbc46071c8c59e1f81850ac544b6c234b2d377b97eb4db72bd93a | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | READ | SUPPORTED_NEEDS_CONTEXT | calendar_read_date_event_time | EVENT = [WITHHELD]; DATE = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-d3cabfa13938d21e

- Unique tickets: **1**; unique rows: **127**; ticket–reason occurrences: 1; row–reason occurrences: 127.
- Sources: massive_1_1_en_us.
- Source intents: email_query.
- Current domain / action / disposition / OOD patterns: COMMUNICATION/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): communication_read=127.
- Slot patterns (unique rows): DATE=2; DATE + PERSON=1; EVENT=1; KIND=18; KIND + PERSON=4; KIND + TIME=1; LOCATION=3; PERSON=54; PERSON + DATE=4; PERSON + KIND=1; PERSON + TIME=4; REFERENCE=18; REFERENCE + DATE=1; REFERENCE + PERSON=1; TIME=14.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-d3cabfa13938d21e`. Content binding: `7550b7d1503126fa659a04345300fdd4ffc3804f9a6bd0accaba2bb23c355b82`.
- Decision subgroups: 15; blanket cluster decisions disabled. `EXC-d3cabfa13938d21e-S-0bba6b3a18bb` (1 rows) `EXC-d3cabfa13938d21e-S-18470b27dde3` (4 rows) `EXC-d3cabfa13938d21e-S-2c19694a9df5` (1 rows) `EXC-d3cabfa13938d21e-S-56d02fdc0f63` (54 rows) `EXC-d3cabfa13938d21e-S-5ef0bd6d54e8` (18 rows) `EXC-d3cabfa13938d21e-S-637c33ee2376` (2 rows) `EXC-d3cabfa13938d21e-S-8549dcec7196` (3 rows) `EXC-d3cabfa13938d21e-S-885dc860f588` (1 rows) `EXC-d3cabfa13938d21e-S-c162b1ca51be` (1 rows) `EXC-d3cabfa13938d21e-S-d41b88b8d9cf` (1 rows) `EXC-d3cabfa13938d21e-S-d71897eaa566` (1 rows) `EXC-d3cabfa13938d21e-S-dcf829c72589` (14 rows) `EXC-d3cabfa13938d21e-S-e413ad5d0914` (18 rows) `EXC-d3cabfa13938d21e-S-edf725924e2a` (4 rows) `EXC-d3cabfa13938d21e-S-f1a835270d9d` (4 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 127.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 67307a867319eccda97b899902767a8edc74b20f8ab9ffd4c7c971ad58d532dd | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | REFERENCE = [WITHHELD]; DATE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 7b235809a724c999fab33f0f769d607ac81e139781c5ed44df42185d5158f2f3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | PERSON = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| f899fa379fd649ea8f11d2bf89a3afd7205d6413f62f9f685617fffa58289b80 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | EVENT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 02de724c462141d193c86943589a8ced62808c6793d046c9393fa2714344b0ff | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | PERSON = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 0323ab68ece84bc56e4384392a9fef44f4ec52db83d435aa8f8c5044dcb0ea07 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-d48864e3fd16de46

- Unique tickets: **1**; unique rows: **2**; ticket–reason occurrences: 1; row–reason occurrences: 2.
- Sources: clinc150_full.
- Source intents: calendar_update.
- Current domain / action / disposition / OOD patterns: CALENDAR/UPDATE/KNOWN_UNSUPPORTED/False.
- Families (unique rows): calendar_update=2.
- Slot patterns (unique rows): NO_SLOTS=2.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-d48864e3fd16de46`. Content binding: `81540267a7e9dddd15cc0e824935c9cf4fbb7704e79b35918095a338bea11d8b`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-d48864e3fd16de46-S-4f9c796f9365` (2 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| bf5f3ef225f18ca8d9f451f3c5b362a446a06c59c072da869d816a26e75ff5f1 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | UPDATE | KNOWN_UNSUPPORTED | calendar_update | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| c75d9631e9af0b89aa1c83968f64a561998452fb36f24729a7ffbc0864fd3849 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | UPDATE | KNOWN_UNSUPPORTED | calendar_update | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-d6e310823d148efb

- Unique tickets: **4**; unique rows: **4**; ticket–reason occurrences: 4; row–reason occurrences: 4.
- Sources: clinc150_full.
- Source intents: next_song, play_music, update_playlist, what_song.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=4.
- Slot patterns (unique rows): NO_SLOTS=4.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-d6e310823d148efb`. Content binding: `0e0172860331ee4183cb00967d8d27a1091f869ed9132e036f18b577194783c5`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-d6e310823d148efb-S-18e4f5fa3963` (1 rows) `EXC-d6e310823d148efb-S-1e7cd8b014a8` (1 rows) `EXC-d6e310823d148efb-S-25f27c5402d0` (1 rows) `EXC-d6e310823d148efb-S-975cffaff97a` (1 rows).

### Representative records

4 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 4.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 27241cac114025fb058e69c6cf12a6aaf95a191f583bfd884edfb8ba0b35ba9c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 97d5d938b8e06a29efaf3f4ab68c04e822b023436bd0bf735cf02f07d505ebbb | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 368a3b6b81197959c64fbf646ea3d7961f48b887ed48cf5c771d91fd6d8ab4cd | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 470bf3961d4dde29316a8c1f48a5843ebc6bc3d5b53f2e8187be731fd9525938 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-dcfa7accc70cb9ac

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: massive_1_1_en_us.
- Source intents: cooking_recipe.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=1.
- Slot patterns (unique rows): KIND=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-dcfa7accc70cb9ac`. Content binding: `16b7ea515635cf30958e60714b256026f758ef43f62a221002d72b423c79e56d`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-dcfa7accc70cb9ac-S-b030993dfae6` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 108293329d4e6dad7653e5ef3eace72e8c6ade2cdb1f2e86c1be1df475098dfa | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-dd62f215c1f7b637

- Unique tickets: **1**; unique rows: **186**; ticket–reason occurrences: 1; row–reason occurrences: 186.
- Sources: massive_1_1_en_us.
- Source intents: email_sendemail.
- Current domain / action / disposition / OOD patterns: COMMUNICATION/SEND/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): UNRESOLVED=28; communication_send=158.
- Slot patterns (unique rows): CHANNEL=4; DATE=1; DATE + REFERENCE=1; EVENT + REFERENCE=1; LOCATION=1; PERSON=89; PERSON + CHANNEL=1; PERSON + DATE=7; PERSON + DATE + EVENT=2; PERSON + DATE + KIND + TIME=1; PERSON + DATE + TIME=3; PERSON + DATE + TIME + KIND + LOCATION=1; PERSON + EVENT=3; PERSON + EVENT + DATE=2; PERSON + EVENT + DATE + TIME=1; PERSON + EVENT + DATE + TIME + LOCATION=1; PERSON + KIND=1; PERSON + PERSON=2; PERSON + TIME=1; PERSON + TIME + KIND=1; REFERENCE=40; REFERENCE + CHANNEL=1; REFERENCE + DATE=7; REFERENCE + EVENT=2; REFERENCE + KIND=2; REFERENCE + KIND + DATE=1; REFERENCE + KIND + TIME=1; REFERENCE + LOCATION=1; REFERENCE + PERSON=1; REFERENCE + REFERENCE=1; REFERENCE + REFERENCE + EVENT + PERSON=1; REFERENCE + TIME=1; TIME + TIME=2; TIME + TIME + RECURRENCE=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-dd62f215c1f7b637`. Content binding: `dd469725bc7e8bb6128346e9c561549cc0f14dfb26ef620d6b3dbde9f6d1d5b9`.
- Decision subgroups: 34; blanket cluster decisions disabled. `EXC-dd62f215c1f7b637-S-005d73ee9585` (1 rows) `EXC-dd62f215c1f7b637-S-0a9f5c85e8c7` (40 rows) `EXC-dd62f215c1f7b637-S-13045e4590df` (3 rows) `EXC-dd62f215c1f7b637-S-1b3f4cad7b34` (2 rows) `EXC-dd62f215c1f7b637-S-1d59bc0ee56d` (1 rows) `EXC-dd62f215c1f7b637-S-228c10e12c2d` (1 rows) `EXC-dd62f215c1f7b637-S-27f65fa35b23` (1 rows) `EXC-dd62f215c1f7b637-S-2f36aa883105` (1 rows) `EXC-dd62f215c1f7b637-S-377b3b88fadb` (1 rows) `EXC-dd62f215c1f7b637-S-3cec96474a5c` (1 rows) `EXC-dd62f215c1f7b637-S-42fee5e1aa30` (2 rows) `EXC-dd62f215c1f7b637-S-44dad19c2d0b` (1 rows) `EXC-dd62f215c1f7b637-S-45175d9cd40e` (2 rows) `EXC-dd62f215c1f7b637-S-4eda140cd5f6` (1 rows) `EXC-dd62f215c1f7b637-S-5149cdabae8d` (1 rows) `EXC-dd62f215c1f7b637-S-59e5c5db12f3` (1 rows) `EXC-dd62f215c1f7b637-S-63555c9b8414` (2 rows) `EXC-dd62f215c1f7b637-S-6699239610ad` (1 rows) `EXC-dd62f215c1f7b637-S-6da32db8f712` (89 rows) `EXC-dd62f215c1f7b637-S-6e89db83f688` (4 rows) `EXC-dd62f215c1f7b637-S-71dbf50126a5` (1 rows) `EXC-dd62f215c1f7b637-S-7a5016a4dda9` (7 rows) `EXC-dd62f215c1f7b637-S-7c426f9fdf3c` (1 rows) `EXC-dd62f215c1f7b637-S-8d287cf4e1b2` (1 rows) `EXC-dd62f215c1f7b637-S-965ae38c8de1` (1 rows) `EXC-dd62f215c1f7b637-S-9fe37dcd6939` (1 rows) `EXC-dd62f215c1f7b637-S-ba532bdad40d` (1 rows) `EXC-dd62f215c1f7b637-S-c44bb14b3fcc` (2 rows) `EXC-dd62f215c1f7b637-S-c77af9f86fcd` (2 rows) `EXC-dd62f215c1f7b637-S-cb1827d267d2` (7 rows) `EXC-dd62f215c1f7b637-S-d90d5ceb3959` (1 rows) `EXC-dd62f215c1f7b637-S-dab932d6e886` (3 rows) `EXC-dd62f215c1f7b637-S-e3555339ff23` (1 rows) `EXC-dd62f215c1f7b637-S-fbd1d929ecf6` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 186.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 3212fbccf1fa7a6461caa7ceea8f2db1eb2dadcb6214f360ee3a367e3389f3da | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | UNRESOLVED | PERSON = [WITHHELD]; DATE = [WITHHELD]; KIND = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 050c9b5cf0b9f11e34e3487903098b66d7211bb914b67bb775e61c1a25625cb4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | REFERENCE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 04584a8f4a37713f1b95d3eb8ec5275520c4d73bcb3e664c1579703ffe5589c4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | PERSON = [WITHHELD]; EVENT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 1804fe13470f7620a2bd70fc9a9bb5789ecf5c897d70666452040dac14ca094d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | REFERENCE = [WITHHELD]; EVENT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| a958931d6b7f61a5031da7635d2458fbf2fad3832b388ab369d85467f67c948d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | SEND | SUPPORTED_NEEDS_CONTEXT | communication_send | REFERENCE = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-de762b54ffd733fb

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: metis_controlled_synthetic.
- Source intents: memory_read_subject:delete.
- Current domain / action / disposition / OOD patterns: MEMORY/DELETE/SUPPORTED_COMPLETE/False.
- Families (unique rows): synthetic:memory_read_subject:delete=1.
- Slot patterns (unique rows): SUBJECT=1.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-de762b54ffd733fb`. Content binding: `0a8ebcfff3dd454cf27a5a550ebd53b33d68b9765e29fe170ddd2e223afa4cc4`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-de762b54ffd733fb-S-c5e8f69dc392` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 25e5dc29c29c11f501b4269693491b20fa7513b4d4e2104b23f759f0886b779c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | MEMORY | DELETE | SUPPORTED_COMPLETE | synthetic:memory_read_subject:delete | SUBJECT = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-e0d94872b0d03dc0

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: metis_controlled_synthetic.
- Source intents: task_read_subject_date_status:compound.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): synthetic:task_read_subject_date_status:compound=1.
- Slot patterns (unique rows): STATUS + SUBJECT + DATE + REFERENCE=1.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-e0d94872b0d03dc0`. Content binding: `4dda18cd17a6bfdda83341f8ac3e967aea1e1cac2281a9bd2368b75955ce532c`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-e0d94872b0d03dc0-S-544191ce1c84` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 10cbab81b61f67990aafaae971c184335393e7bf69df01d68bb0f24126e1465e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | SUPPORTED_NEEDS_CONTEXT | synthetic:task_read_subject_date_status:compound | STATUS = [WITHHELD]; SUBJECT = [WITHHELD]; DATE = [WITHHELD]; REFERENCE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-e6006b10ee060c37

- Unique tickets: **1**; unique rows: **4**; ticket–reason occurrences: 1; row–reason occurrences: 4.
- Sources: massive_1_1_en_us.
- Source intents: calendar_remove.
- Current domain / action / disposition / OOD patterns: CALENDAR/DELETE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): calendar_delete=4.
- Slot patterns (unique rows): EVENT + TIME=1; EVENT + TIME + DATE=1; TIME=1; TIME + EVENT + DATE=1.
- Primary reason: `TEMPORAL_CONTEXT_UNRESOLVED`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-e6006b10ee060c37`. Content binding: `43d063e597eb35bd3b7b3728ce4b382e2f911c351c47df75984c00edf99e35d1`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-e6006b10ee060c37-S-2ab045b7b367` (1 rows) `EXC-e6006b10ee060c37-S-60ec8f59a2f7` (1 rows) `EXC-e6006b10ee060c37-S-a0656345a8ed` (1 rows) `EXC-e6006b10ee060c37-S-fc62b11255cc` (1 rows).

### Representative records

4 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 4.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 59c2b25ba1eb5db43a1ef65cbf3d0ae1c1916c2986b4edf9074a0d808cd8cb67 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 3887bc12c134ac095ce84298f7283227543cff5bf729aabc1186c6e94725ddcd | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | EVENT = [WITHHELD]; TIME = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| da8c6cd47c745f1bb89b8cd571333e9451befdd643beca9f102ca1f2ab0a33b6 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | TIME = [WITHHELD]; EVENT = [WITHHELD]; DATE = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |
| 23e2433e208a06d06c4f9d507924e783f84a85a0a6131afadc26d5090077b597 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | CALENDAR | DELETE | SUPPORTED_NEEDS_CONTEXT | calendar_delete | EVENT = [WITHHELD]; TIME = [WITHHELD]; DATE = [WITHHELD] | TEMPORAL_CONTEXT_UNRESOLVED |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-ed475c19a91b5f7c

- Unique tickets: **1**; unique rows: **33**; ticket–reason occurrences: 1; row–reason occurrences: 33.
- Sources: clinc150_full.
- Source intents: todo_list_update.
- Current domain / action / disposition / OOD patterns: TASK/UPDATE/KNOWN_UNSUPPORTED/False.
- Families (unique rows): task_update=33.
- Slot patterns (unique rows): NO_SLOTS=33.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-ed475c19a91b5f7c`. Content binding: `7430447cdd6ae9a7eb69d9a4ac94e7bf8ed9ed144a7cfa17a5c6c5d3f7a9ff2c`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-ed475c19a91b5f7c-S-0f290b4b9036` (33 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 33.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 0c44b16ead2435c5348a907ec647ffbf2538844f110ddd1f6729a571ff5018cc | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | UPDATE | KNOWN_UNSUPPORTED | task_update | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 188944e69ab01288c1524bea2ae92189afec49c2974ca3a603f5584f7bd117e4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | UPDATE | KNOWN_UNSUPPORTED | task_update | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 199ee5c2713e638d41a54a688e6a87865dc85211e8f5d1c0736646481cff01e9 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | UPDATE | KNOWN_UNSUPPORTED | task_update | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 1b3b3fcf1f25646914a07f1f49afd1597e8e234344e2ea518a191ea69ef24191 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | UPDATE | KNOWN_UNSUPPORTED | task_update | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 1d7d7ffc97a94658f2111d28ce89e93aca1bfca806e13c37c95decab9636c17c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | UPDATE | KNOWN_UNSUPPORTED | task_update | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-eed30f6e9c8ce250

- Unique tickets: **1**; unique rows: **119**; ticket–reason occurrences: 1; row–reason occurrences: 119.
- Sources: massive_1_1_en_us.
- Source intents: email_query.
- Current domain / action / disposition / OOD patterns: COMMUNICATION/READ/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): communication_read=119.
- Slot patterns (unique rows): NO_SLOTS=105; DATE=13; TIME=1.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-eed30f6e9c8ce250`. Content binding: `ce01896e812d17e3d7466b5f479076edafb3b592ab72852eca34aac5f9a0f007`.
- Decision subgroups: 3; blanket cluster decisions disabled. `EXC-eed30f6e9c8ce250-S-637c33ee2376` (13 rows) `EXC-eed30f6e9c8ce250-S-90d237755f6d` (105 rows) `EXC-eed30f6e9c8ce250-S-dcf829c72589` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 119.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 00c65257e418ea3786b407ef7a8d1322b998bad6ead1a1b30297c89274062699 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 006c04a9a3b9b887b37bbaaa63bb606ee4006fc42039574314c9992615883be3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 7e5df4d2c7ca0b1cd9e3f96ea3d526ef296d9066499be5d737b863e24c5fe6ba | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | TIME = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 3395e42dfd05ad297735f1e4a275d5cd37d9781939e8cbc9996e29ba0c6fd1c9 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | DATE = [WITHHELD] | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 026689068e04c926ec434481e31ee0278055e3f6b2612e0f25b64231ce3b5c16 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | COMMUNICATION | READ | SUPPORTED_NEEDS_CONTEXT | communication_read | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-f11206d48ac439f9

- Unique tickets: **2**; unique rows: **2**; ticket–reason occurrences: 2; row–reason occurrences: 2.
- Sources: massive_1_1_en_us.
- Source intents: takeaway_order, takeaway_query.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=2.
- Slot patterns (unique rows): KIND + LOCATION=1; LOCATION + KIND=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-f11206d48ac439f9`. Content binding: `3f1e36210eb17713133695ba495e60d889d36dbbaeb07f0877684b54487291c7`.
- Decision subgroups: 2; blanket cluster decisions disabled. `EXC-f11206d48ac439f9-S-5088eb75afd4` (1 rows) `EXC-f11206d48ac439f9-S-9d1f1f92a5b5` (1 rows).

### Representative records

2 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 2.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 54319a5e8722b49fb6deb8bccee2e621a1846640f778e62122a86b19b0dc989d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | KIND = [WITHHELD]; LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 97140726b788f5505aea30048b4cd25edc36832919b19228f533cd6ebf498c6a | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | LOCATION = [WITHHELD]; KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-f2233009b28da88d

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: metis_controlled_synthetic.
- Source intents: task_read_subject_date_status:complete.
- Current domain / action / disposition / OOD patterns: TASK/COMPLETE/SUPPORTED_COMPLETE/False.
- Families (unique rows): synthetic:task_read_subject_date_status:complete=1.
- Slot patterns (unique rows): STATUS + SUBJECT + DATE=1.
- Primary reason: `HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-f2233009b28da88d`. Content binding: `dec528c3a1c17411269b630932d48a772b71b76f9164ea736b83d808b63ce6a2`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-f2233009b28da88d-S-3ef88c05705c` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| ea2db335887ced30596ff8f879016dd2e020e43143728bd36b9f9f7f96c04de8 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | TASK | COMPLETE | SUPPORTED_COMPLETE | synthetic:task_read_subject_date_status:complete | STATUS = [WITHHELD]; SUBJECT = [WITHHELD]; DATE = [WITHHELD] | HISTORICAL_SYNTHETIC_REQUIRES_OWN_TEMPLATE_REVIEW |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-f5869d0598226a41

- Unique tickets: **3**; unique rows: **3**; ticket–reason occurrences: 3; row–reason occurrences: 3.
- Sources: massive_1_1_en_us.
- Source intents: transport_query, transport_taxi, transport_ticket.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=3.
- Slot patterns (unique rows): KIND + LOCATION=1; KIND + LOCATION + TIME=1; LOCATION=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-f5869d0598226a41`. Content binding: `f71cbd7ed12b15caf96ac3eb6132b0a1638d10c4388383813cd1f3cf6af565a9`.
- Decision subgroups: 3; blanket cluster decisions disabled. `EXC-f5869d0598226a41-S-3b9f778e2d4e` (1 rows) `EXC-f5869d0598226a41-S-66d51193fd84` (1 rows) `EXC-f5869d0598226a41-S-9593fbdb5b33` (1 rows).

### Representative records

3 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 3.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| f91fea1febb600fb8084740ede12144d332fea84231869d457db764ed2351ba8 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | KIND = [WITHHELD]; LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| c957c9288651ad08ce712ace7ed430a8d477ed1c0dd935f89b47959250d5b694 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | KIND = [WITHHELD]; LOCATION = [WITHHELD]; TIME = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| e0e5fc267c8ed58c6d2ecdb364b5cb59f4ca2dfe1d2b62e919c6d1415c7a64f0 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | LOCATION = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-f694f896cbd7345e

- Unique tickets: **4**; unique rows: **4**; ticket–reason occurrences: 4; row–reason occurrences: 4.
- Sources: massive_1_1_en_us.
- Source intents: iot_hue_lightchange, iot_hue_lighton, iot_hue_lightup, iot_wemo_on.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=4.
- Slot patterns (unique rows): NO_SLOTS=4.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-f694f896cbd7345e`. Content binding: `1d51cb334287df174fdebdedc94a78db5ee83a1733aff34feca97f60af60ac8b`.
- Decision subgroups: 4; blanket cluster decisions disabled. `EXC-f694f896cbd7345e-S-1cb96325a876` (1 rows) `EXC-f694f896cbd7345e-S-30168806c9d6` (1 rows) `EXC-f694f896cbd7345e-S-3741d0bd9e80` (1 rows) `EXC-f694f896cbd7345e-S-efc5373b09e7` (1 rows).

### Representative records

4 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 4.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| b9f6a572760a93770a985dac84c5ebfc340a165ace60f433702eedd3307782b0 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 91e75e7c8c5e309b9d7a6f2a8672e9dc50293f1334e61d31d055e786d3d2a229 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 3973078d873362b44fc1fc8dc37eb45cfc49ee8c275b72424f87700ab05def84 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| b621289bddf49268b22fc1eae80826d2d2eade268e070cf977d40ccf84b43d70 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-f79872a643c12e8d

- Unique tickets: **9**; unique rows: **15**; ticket–reason occurrences: 9; row–reason occurrences: 15.
- Sources: clinc150_full.
- Source intents: change_accent, change_ai_name, change_language, change_speed, change_user_name, change_volume, reset_settings, sync_device, whisper_mode.
- Current domain / action / disposition / OOD patterns: UNKNOWN/NONE/OUT_OF_DOMAIN/True.
- Families (unique rows): unknown_external_action=15.
- Slot patterns (unique rows): NO_SLOTS=15.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-f79872a643c12e8d`. Content binding: `bc0916e776b2b8b1923115af26065f1446c9a8e022553c88d3b5fdb70d92a521`.
- Decision subgroups: 9; blanket cluster decisions disabled. `EXC-f79872a643c12e8d-S-03b2edbd8b63` (2 rows) `EXC-f79872a643c12e8d-S-04ffe0ea91e4` (1 rows) `EXC-f79872a643c12e8d-S-0e68c5dd0a0f` (2 rows) `EXC-f79872a643c12e8d-S-444babb28f7f` (2 rows) `EXC-f79872a643c12e8d-S-49e3d679b56e` (2 rows) `EXC-f79872a643c12e8d-S-5b46b3362614` (1 rows) `EXC-f79872a643c12e8d-S-69fb7b7f630e` (2 rows) `EXC-f79872a643c12e8d-S-b8f09488f493` (2 rows) `EXC-f79872a643c12e8d-S-e1615e87c152` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 15.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 36df1d3f2a7dd7677a2dc34b0debd9ca21f3bcbedadd1ad2dd43b739e57dc12c | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 6979368b0d24138fe7b5d5a443345cd706fa6dc2a71e1942a0e723cd183563bc | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 2055b20d77d2cd761b5111febc90cfae5c91cf0a0176e8d727ec4da59c46a584 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 157b13ac138fee2f8a23ae221be1cc83ac1b16e28b61e63def1f694d6670b5a4 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 02962f51e12027662e9f9146515babe04f1591064e4908a0d9573e36c262aa02 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | UNKNOWN | NONE | OUT_OF_DOMAIN | unknown_external_action | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-f849c0966a1fdb2e

- Unique tickets: **1**; unique rows: **20**; ticket–reason occurrences: 1; row–reason occurrences: 20.
- Sources: massive_1_1_en_us.
- Source intents: email_addcontact.
- Current domain / action / disposition / OOD patterns: PERSON/CREATE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): person_create=20.
- Slot patterns (unique rows): CHANNEL=11; CHANNEL + PERSON=1; PERSON=5; PERSON + CHANNEL=2; SUBJECT=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-f849c0966a1fdb2e`. Content binding: `6abbafdc746c05b1566e3607c5aa6ffe6a1d429a2633b70dfb572fb4e4678784`.
- Decision subgroups: 5; blanket cluster decisions disabled. `EXC-f849c0966a1fdb2e-S-3bf423854898` (5 rows) `EXC-f849c0966a1fdb2e-S-660fcadc63f5` (2 rows) `EXC-f849c0966a1fdb2e-S-6c57ddf02db6` (1 rows) `EXC-f849c0966a1fdb2e-S-9e2b24ef6834` (11 rows) `EXC-f849c0966a1fdb2e-S-fcf03a6634c3` (1 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 20.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 446d9a3d6045ee43b95e0189a2974c0eb906fe4a6561824dcca2d95e63fcc8aa | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | PERSON = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 706579c4f4bc6eb43eca0f148821a47e4c12432961363e482cf1bbddab090ebe | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | PERSON = [WITHHELD]; CHANNEL = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 41eb1ea4eb410fac2c6ac6731326c8325e1904556297da77b0a2dd9cb7593b8b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | CHANNEL = [WITHHELD]; PERSON = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 14d327f2ac8dc173ce391056238bb7cbabd81952d0aac5f7801eea90f79b1995 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | CHANNEL = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 8584982e2df252fad43de5acdc8b9347439335c65fd1b00f5b65244d3b47a95d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | SUBJECT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-fab071a9b496798c

- Unique tickets: **1**; unique rows: **1**; ticket–reason occurrences: 1; row–reason occurrences: 1.
- Sources: massive_1_1_en_us.
- Source intents: weather_query.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=1.
- Slot patterns (unique rows): FIELD + DATE=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-fab071a9b496798c`. Content binding: `e398e8416ed1d6cc55df0f9613d9aa5058d6dd132528b5d9abe9c0b2b2777834`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-fab071a9b496798c-S-63971a817df6` (1 rows).

### Representative records

1 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 1.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| d27ca0741793cfdb31fada56a30fea260464699bba68704745e0b042e201f9d3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | FIELD = [WITHHELD]; DATE = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-fc5112ca611105b0

- Unique tickets: **3**; unique rows: **3**; ticket–reason occurrences: 3; row–reason occurrences: 3.
- Sources: massive_1_1_en_us.
- Source intents: qa_currency, qa_definition, qa_stock.
- Current domain / action / disposition / OOD patterns: KNOWLEDGE/READ/KNOWN_UNSUPPORTED/False.
- Families (unique rows): knowledge_read=3.
- Slot patterns (unique rows): KIND + KIND=1; PERSON=1; SUBJECT=1.
- Primary reason: `PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: false. Required scope: **SUBGROUP_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-fc5112ca611105b0`. Content binding: `e3cb84ed19b85277ddf8c3af777ec410e98ccf34769340ee44e7562eb5d52dc6`.
- Decision subgroups: 3; blanket cluster decisions disabled. `EXC-fc5112ca611105b0-S-8287c52da5b8` (1 rows) `EXC-fc5112ca611105b0-S-93c9fd473f4b` (1 rows) `EXC-fc5112ca611105b0-S-f98c66f63a43` (1 rows).

### Representative records

3 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 3.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| a1681c97416fcff236918e53b08dafcab5f99a62939320b9d07e8769c7e789f3 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | KIND = [WITHHELD]; KIND = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| 9d02bdc6cbf5b816b6cb283b42cfc035d427e7f03e69f696f77f226396334851 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | SUBJECT = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |
| a99fec6f58bafbe3280fffbb6840344c55960172aa473de4cba78845eaaefadc | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | KNOWLEDGE | READ | KNOWN_UNSUPPORTED | knowledge_read | PERSON = [WITHHELD] | PERSONAL_FREE_TEXT_OR_AMBIGUOUS_SLOT |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## EXC-ff22573b55a5eb25

- Unique tickets: **1**; unique rows: **30**; ticket–reason occurrences: 1; row–reason occurrences: 30.
- Sources: massive_1_1_en_us.
- Source intents: email_addcontact.
- Current domain / action / disposition / OOD patterns: PERSON/CREATE/SUPPORTED_NEEDS_CONTEXT/False.
- Families (unique rows): person_create=30.
- Slot patterns (unique rows): NO_SLOTS=30.
- Primary reason: `WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN`. Secondary current reasons: none.
- Semantically homogeneous: **not established**. Metadata homogeneous: true. Required scope: **INDIVIDUAL_REVIEW_REQUIRED**.
- Exact membership and content hashes: `PLAN_07_EXCEPTION_CLUSTER_INDEX.json` → `EXC-ff22573b55a5eb25`. Content binding: `705966f0374ebe99dce01bdbec74c11187797866bcc86560616df426208165b6`.
- Decision subgroups: 1; blanket cluster decisions disabled. `EXC-ff22573b55a5eb25-S-b8b0a9dba44f` (30 rows).

### Representative records

5 actual members selected deterministically for intent/family/slot-pattern variation. Display-cleared members in cluster: 0; text-excluded members: 30.

| Row ID | Exact utterance / display restriction | Domain | Action | Disposition | Family | Slots TYPE = text | Exception reasons |
|---|---|---|---|---|---|---|---|
| 158ce502683b429dd048b8ea4d9e2a796a2cd128870b2b464219b1eadc76ceb2 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 1a705bbef01cece9f5fa1481c0e46e23aac9d3bc5920558767015cc935e6d84e | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 3a9a26e73e68f5c46a24879bbeea717a0cb43217953fae85af05a766a7c8a755 | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 3e2b974180debb7fb814c423e38de33d243a9e13a136ca5788910fcb7e57046b | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |
| 3e8baf845a13c9e0c1b6b9e9319f23f0dce6386bb261e42f1d418fe03c41016d | [WITHHELD: UNKNOWN_PRIVACY_NO_DOCUMENTATION_DISPLAY_CLEARANCE] | PERSON | CREATE | SUPPORTED_NEEDS_CONTEXT | person_create | none | WORDING_OR_SUPPORT_BOUNDARY_NOT_PROVEN |

### Decision draft

`Decision: PENDING / APPROVE / REJECT / CHANGE` — current value: **PENDING**.
Target an exact subgroup/row ID; for unresolved subgroups use individual row IDs. This header is not permission for a bulk decision.

```yaml
decision: PENDING
decision_target_id: null
corrected_domain: null
corrected_action: null
corrected_disposition: null
corrected_family: null
slot_correction_note: null
slot_edits: []
# Slot edit shape (draft only):
# - operation: ADD | REMOVE | UPDATE
#   original_slot_index: null
#   corrected_type: null
#   corrected_start_utf16: null
#   corrected_end_utf16: null
#   corrected_text: null
reviewer_note: null
```

Use frozen label values. Slot spans are zero-based, end-exclusive UTF-16 offsets into the unchanged exact utterance; corrected text must reconstruct exactly. ADD supplies a new type/span/text, REMOVE identifies an existing slot, UPDATE supplies its replacement. Corrections are proposals requiring fresh validation and review bindings; this packet applies none.

## Integrity and regeneration

`python -m ml.nlu_v1.dataset_v0.exception_packet --write` regenerates only this derived Markdown and its JSON index. `--check` verifies deterministic regeneration and unchanged inputs. The index binds exact ticket IDs, row IDs, content hashes, representatives and primary assignments.

Inputs (SHA-256):
- `.gitignore`: `4d434bcf7c8170d2458232fdc75d80ad57fa5f64f8eaa1b5e23fefda1b83b2db`.
- `ml/nlu_v1/configs/dataset_sources.yaml`: `4fa2c0e1f873e84070de04191f93dd8a7ab278dcbe747a723e2af51d5e810c10`.
- `ml/nlu_v1/data_management/README.md`: `d97ce04105cff21af15baa2ba89ec574f0d090c861bc820eeecc99a29960bb64`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/audit_input_rows.jsonl`: `d75aeb7f9fb1e865ef4eac04752e2bb64975da3a6a7d0966b9159f4320b4251e`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/coverage_report.json`: `54696f28bfd02a9a902c71107927425f9bac5d8bcff297ff9add2b06074483c1`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/coverage_templates.yaml`: `19e7176039ae2c55bfb968020cbb3bebc860958b76698a562b956d6072856d65`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/curation_reason_codes.json`: `a48ea0d265f6df1e1c17aa98427e6fd1ea8068def333d0c41576cc1d9be811b5`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/dataset_v0_coverage_policy.yaml`: `6df19a51ce0b1eac2e5f00d9f13387c22777af5b9e99efe7ae1c79154f4ee1d9`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/dedupe_report.json`: `3545fdad8c8ef5769c56495bc856229752161cb8d9cc098d8e933f5a207b4f98`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/embedding_receipt.json`: `cfcf2fc4aa65f94a86ac213d372bcc6246746fdf9fc353192e584a8191ee8fc5`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/embeddings.json`: `1b8ba829d21b4c786fe39e9e444b93c8a9a7ffa9ac4949a4d0112d3bcabf120a`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/exception_review_tickets.json`: `b4f3c2ecd33b42723ecd75f6e41b0edf16c8b03257fe982ea937cab6ad4c3033`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/group_approval_template.json`: `91842be66e7dec9ae94b43e2c79a710ba260a4de386371879d4eddb794d02973`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/group_registry.json`: `4f811a53fdea4f14174bf8cc89ad1bd0ee1eccca289226d7ca07f2d7069b8203`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/group_review_manifest.json`: `c8947bb7c546fd2720dc5329efe314b6a68a82480097d14aeb465a47f031e57c`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/group_reviewers.yaml`: `b10c21cc2ccd294a3188331d901a0a635991ffd86ac926012df8d06fc56a5d46`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/leakage_report.json`: `bb6f24108d53c12de21f74cb2857ee575f1fb4bc84741cf3b1eeec9dee806c9f`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/public_source_search_report.json`: `83eec47a8392b343b0a3a3faa8c226e83c62ffad8eafbccf030e7e55f0638917`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/quarantined.jsonl`: `c10967f18dcd434d7e0161a9ee1b47c03ff8bcb6956cb44ed6e35fae31b33403`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/semantic_group_policy.yaml`: `f3f87c675ab5c0c1172bfbbf33901598d1accff4f2cd7f4bdf6c99febd12b321`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/semantic_review_exceptions.jsonl`: `745da80183dff70c0f6741efe4977c1ffa007f08219619dee5412371148e39b1`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/semantic_review_groups.json`: `3cb00fecb9e8d3cbbb65737c1008bece10d51a84e0d01c176125d424f98ec079`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/semantic_review_summary.json`: `cbb9ef1f2c33e9717c072588a467749d744da93b12a09ca33669ebea7b69996f`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/coverage_templates_001.yaml`: `19e7176039ae2c55bfb968020cbb3bebc860958b76698a562b956d6072856d65`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/dataset_source_evidence.json`: `e3b008c5a5251ceddad5e1479918237963b36a196bd0119f347ff1bf125370d4`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/dataset_sources.yaml`: `4fa2c0e1f873e84070de04191f93dd8a7ab278dcbe747a723e2af51d5e810c10`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/dataset_v0_coverage_policy.yaml`: `6df19a51ce0b1eac2e5f00d9f13387c22777af5b9e99efe7ae1c79154f4ee1d9`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/dedupe_policy.yaml`: `b419c2f1839f44d10c445a537ace3a656ced06cc1061d0529164e88bca86f6e7`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/machine_curation_002.yaml`: `ac091732a2e5c4df2d5580c324eb68443b00eaec93b9bcccf0dd052ac872a7ad`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/machine_families_002.yaml`: `68aed263450b95ab682a1c293bccc11d1f4db33ea9bb3869aff5adc6a0770c0a`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/semantic_group_policy_001.yaml`: `f3f87c675ab5c0c1172bfbbf33901598d1accff4f2cd7f4bdf6c99febd12b321`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/configs/synthetic_sources.yaml`: `5c803e688287c6eae5dd7c2f20ea5a012d7b393ddd9e458e129109b92955c79c`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/context_contract.py`: `edc2f1f157bca2aedbe12ba9b7137e90f07c5690a959ae47848eff6f0d041ca3`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/contract.py`: `39198e28760a0a993152d8ec53eec098639d5f5ee3012932ca18c14c4c8ddbae`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/data_management/dedupe.py`: `c494628c087783e4cf4ed2f641d865ba8d3fbd2568e4365b50bff3003c5032ed`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/data_management/records.py`: `8a497341b84b67c5336b12fa74b52149f5c41e5969cc4aaf4023419dcc1ffc95`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/dataset_v0/coverage_additions.py`: `688692b2cac0d107eefc2d5e62ddbc3b56c8b62664abf721dc12da3616948907`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/dataset_v0/group_review.py`: `4df3df18686a2df49dbddbe00b83cfcf5db51553c9ee56b0100de871d6480869`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/dataset_v0/machine_curation.py`: `757bb87696166da25bb96e22dbbad03fd690a7ff83168edabc0d39a1fce93949`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/dataset_v0/semantic_groups.py`: `451999b6930455761b5ed24c23a1d022d66d029835190ff71b331f13b209e827`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/dataset_v0/similarity_audit.py`: `8a8579bfc0c0b08bffcfac2f4050184e9d4553874a32a188b73620a56dbf5325`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/ingestion/clinc_mapper.py`: `bb70050450c5184b1204c23ddd369b5ea471665b6dcb06ea6ea3f8ace69fdea6`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/ingestion/common.py`: `108b84c0a80e05d299f2e2085c1ecbfad2fe921e8a2159c5c08212788d8ad2c8`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/ingestion/mappings/clinc_intent_mapping.yaml`: `7eddf1da5147efaa1574cd2873f85f9b5764d9875cf9572440e93b2ee592a271`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/ingestion/mappings/massive_intent_mapping.yaml`: `5e9520c302a399c73c67a44b9a83db534fb476f232f9a542cf1ee6956be29fa4`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/ingestion/massive_mapper.py`: `3611d0b6844872ea32a67b06cab6376da7878540c84c446131bb41591efdaf0c`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/source_registry.py`: `5d9e98f9cc27f3c91eeffb27c6e094330f8383f727010f3777e9790b1c509be6`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/splitting/split.py`: `6ef65b0a807926df43b225701ccdebd7d2dbc1a47070389eb3ab5eb7fdd2a2a1`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/synthetic/generate.py`: `3778458d542104264a6ff211a7adbfe2c23da74ed8677cb27c2adb0fd4842eaa`.
- `ml/nlu_v1/datasets/quarantine/plan07-semantic-groups-002/snapshot/synthetic_registry.py`: `8218cb0f862ffade0231266c79689f4725c109b894f626d91ab34dcdc7086caa`.
- `ml/nlu_v1/datasets/quarantine/plan07_machine_curation_005/curation_manifest.json`: `f7fb92fc3f83936de3ec3f41cb83a10837bdf0912b30e69e6608be51c46f5ea4`.
- `ml/nlu_v1/datasets/quarantine/plan07_machine_curation_005/curation_report.json`: `69f6bfdd336f216266572fa06c677e3c622aa85a3e0755d4c3f2557f70998987`.
- `ml/nlu_v1/datasets/quarantine/plan07_machine_curation_005/manual_review_queue.jsonl`: `d0a44182392b6c533ed13604dbd3c739c3ff11e1ea62fcc193e9e84539417030`.
