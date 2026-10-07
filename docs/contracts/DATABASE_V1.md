# Local database schema v1

Logical target schema specification. The first Phase 4 slice implements only tasks, schedule_blocks, and memories; its exported physical Room v1 is a subset described in [the verification record](../PHASE_4_VERIFICATION.md). Remaining tables, relationships, search, and migrations stay phase requirements. The source plan's table list is preserved, with supporting reminders/focus tables needed by its vertical slices. Additions to the shipped physical v1 require a new physical version and explicit migration.

## Conventions

Every table uses id TEXT PRIMARY KEY (UUID). Mutable tables use created_at/updated_at INTEGER epoch milliseconds and revision INTEGER for optimistic concurrency. Enumerated values use validated stable TEXT identifiers. Booleans use 0/1; confidence/importance use REAL constrained to [0, 1]. Foreign keys are enforced. Unknown values are NULL. Intervals use end > start and durations > 0. Defaults never invent a date/person.

All title/body/contact/raw-content fields marked **S** are Keystore-backed AES-GCM encrypted BLOBs with fresh nonces and a stored key/version identifier. Sensitive JSON receives the same protection; nonpersonal structured metadata can remain plain. No hardcoded keys. Encryption failure/key loss blocks access and surfaces a recovery path; never silently replace the database.

### Table contracts

Columns below supplement the common fields. Immutable event/audit/session outcome records use timestamp or started_at rather than mutable revision fields where specified.

| Table | Columns / relationships |
| --- | --- |
| user_profile | display_name **S**, zone_id, locale, autonomy_level (0–4), behavioral_analysis_consent; one active profile |
| persons | display_name **S**, phone **S** nullable, email **S** nullable, contact_lookup_key **S** nullable; provider IDs are references, not permission grants |
| relationships | person_id FK persons, kind, description **S** nullable; UNIQUE(person_id, kind) |
| projects | title **S**, description **S** nullable, status |
| goals | title **S**, description **S** nullable, project_id FK nullable, target_at nullable, status, priority |
| tasks | title **S**, notes **S** nullable, project_id/goal_id FK nullable, due_at nullable, due_zone_id nullable, estimated_seconds nullable, priority, status, completed_at nullable, recurrence_rule nullable, recurrence_zone_id nullable |
| task_dependencies | task_id FK tasks, depends_on_task_id FK tasks; UNIQUE pair; no self edge or cycles (repository validates cycles) |
| reminders | title **S**, task_id/person_id FK nullable, trigger_at, local_date_time, zone_id, precision, scheduling_state, platform_token nullable, delivered_at nullable, idempotency_key UNIQUE |
| focus_sessions | task_id FK nullable, started_at, planned_seconds, ended_at nullable, outcome |
| events | type, source, timestamp, entity_type/entity_id nullable, importance, metadata **S** when personal, schema_version, request_id/action_id nullable; append-only |
| promises | person_id FK persons, task_id FK nullable, content **S**, due_at nullable, status, source_event_id FK events nullable |
| habits | name **S**, definition **S**, sample_count, confidence, observation_start/observation_end, method_version, consent_required; derived, recomputable |
| preferences | key UNIQUE, typed_value **S** when personal, value_schema_version, source (EXPLICIT only initially) |
| memories | memory_type, content **S**, entity_type/entity_id nullable, source_event_id FK nullable, importance, confidence, expires_at nullable; explicit facts distinguished from derived items |
| routines | name **S**, recurrence_rule, zone_id, definition **S**, enabled |
| schedule_blocks | plan_id, task_id FK nullable, routine_id FK nullable, title **S**, start_at/end_at, zone_id, status (PROPOSED/ACCEPTED/COMPLETED/CANCELLED), score_components_json, reason **S** |
| action_runs | request_id, proposal_id, idempotency_key UNIQUE, action_type, payload **S**, risk, status, started_at, finished_at nullable, verification, receipt **S** nullable, safe_error_code nullable; retained for reconciliation |
| action_audit | action_run_id FK action_runs, timestamp, policy_version, autonomy_level, permission_snapshot_json, decision, reason **S**, evidence **S**, confirmation_id nullable; append-only |
| agent_sessions | started_at, ended_at nullable, summary **S** nullable; no raw audio |
| recommendations | entity_type/entity_id nullable, generated_at, expires_at, score, score_components_json, reason **S**, evidence **S**, status |
| experiments | name **S**, hypothesis **S**, started_at, ended_at nullable, consented_at, definition **S**, status; disabled before explicit consent |
| derived_insights | kind, entity_type/entity_id nullable, computed_at, observation_start/observation_end, sample_count, method_version, confidence, statistics_json **S** when personal, source_watermark |

Use a single plan_id to group schedule blocks for initial scheduling; a separate plans table is deferred until plan-level metadata is needed. Agent requests/proposals are working state until an action is accepted; accepted action payload and proposal/request correlations live in action_runs. New persistence needs require a reviewed schema increment.

The first slice stores memory fact/derivation provenance explicitly as `origin` (EXPLICIT/DERIVED). Personal field envelope v1 uses the Keystore alias `metis.personal.fields.v1`, a version byte, fresh 12-byte nonce, and ciphertext with a 128-bit authentication tag. The version selects the key identifier; AAD is `table/id/column`. Task priority is validated in 0–3; status is OPEN/COMPLETED/CANCELLED. Missing deadlines remain null. No repository or screen authorizes Android actions, autonomous memory creation, or behavioral inference.

## Integrity and deletion

- Task/project/goal, reminder/person, and schedule/task links generally use SET NULL when the linked entity is deleted; dependent join rows cascade. Completed events are historical and must not cascade-delete just because a task disappears.
- events.entity_id and other polymorphic references are application validated; they are not SQL foreign keys. Preserve enough safe historical context for explanation without retaining deleted personal content.
- Personal-data deletion must redact/delete relevant memories, event payloads, audit payloads, and derived items as well as the original row. Keeping a minimal nonpersonal idempotency tombstone may prevent replay, but must not retain the deleted content.
- Whole-app deletion includes database/WAL/SHM files, app-private exports/model personalization, DataStore, pending workers/alarms, and keys. Do not claim forensic secure erasure of flash storage.
- Facts and inferred items remain separate. Changes invalidate affected derived insights; confidence and sample count accompany every pattern.

## Required indexes and retrieval

| Query | Index |
| --- | --- |
| Open tasks by deadline | tasks(status, due_at) |
| Project/goal tasks | tasks(project_id, status), tasks(goal_id, status) |
| Timeline / entity history | events(timestamp), events(entity_type, entity_id, timestamp) |
| Due reminders | reminders(scheduling_state, trigger_at) |
| Promise lookup | promises(person_id, status, due_at) |
| Memory lookup | memories(memory_type, updated_at), memories(entity_type, entity_id) |
| Planner conflicts | schedule_blocks(start_at, end_at), schedule_blocks(plan_id, status) |
| Action deduplication / recovery | UNIQUE action_runs(idempotency_key), action_runs(status, started_at) |
| Recommendations | recommendations(status, expires_at) |
| Derived insights | derived_insights(kind, computed_at) |

Initial memory search combines metadata filtering, relationship relevance, recency, importance, and an FTS candidate search. Initial ranking weights: relevance 0.30, importance 0.25, recency 0.20, relationship relevance 0.15, confidence 0.10; configurable and normalized.

**Encryption/FTS boundary:** Room FTS cannot search encrypted text. Do not persist a plaintext duplicate of sensitive memory/task content in an ordinary FTS table. Phase 4/5 must implement a rebuildable in-memory SQLite FTS4 index populated after authorized decryption, destroyed when the process closes, with metadata candidates bounded before decryption. A privacy-tested encrypted whole-database alternative requires an explicit contract change. If bounded search misses its latency target, report the measurements and revise the design; do not silently remove encryption. Physical FTS/index implementation is an exit requirement, not completed by this document.

## Transactions, migrations, and backup

- Internal mutations + corresponding events + action outcome + audit share one transaction. Platform dispatch uses the pending/reconciliation lifecycle in the [agent protocol](AGENT_PROTOCOL.md).
- Schema v1 is created on fresh install; export Room schemas to version control. Every subsequent version has explicit migrations with upgrade/data-preservation tests. No destructive fallback in production.
- Test constraints, foreign keys, encryption read/write failure, query ordering, pending action recovery, recurrence DST behavior, FTS lifecycle, and app restart.
- Use synthetic development seed records only in debug builds; never seed fake data into the user's production database. Developer inspection is debug-only and cannot print personal content into logs.
- Android Auto Backup and device-transfer rules exclude personal storage. Explicit encrypted export/import remains disabled until format versioning, integrity verification, key handling, corruption/restore tests, and reviewable user controls exist.
