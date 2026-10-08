# Phase 5 memory engine verification

Status: complete memory-engine baseline on 2026-10-08; local and GitHub validation passed. The database remains physical Room schema v6. Phase 5 adds local memory workflows and retrieval without enabling commands, Android actions, background inference or model training.

## Delivered behavior

You supports explicitly adding, editing and deleting memories. The editor previews content, type, importance and retention before Save; deletion shows the selected content in a scrollable review and requires confirmation. Immutable provenance prevents promoting a derived record through either the engine or ordinary repository updates. Derived records remain labelled and read-only, with separate explicit/derived filters in browsing and search. Existing entity/source links and confidence are preserved on manual edits.

The MemoryEngine exposes structured retrieval, exact linked-entity lookup and ephemeral working context. Empty query text means bounded structured retrieval without FTS matching; nonempty search still uses literal all-word FTS4 matches. Type, origin, entity and expiry predicates run before bounded decryption. Working records without expiry remain archived and are excluded from retrieval; newly saved working memories expire within 24 hours. No legacy records are silently rewritten.

Ranking is deterministic and explainable over the eligible bounded candidates:

| Component | Default weight | Definition |
| --- | --- | --- |
| Relevance | 0.30 | Mean of normalized query-word coverage and matching-word density; Unicode case/accent normalization |
| Importance | 0.25 | Stored importance in 0–1 |
| Recency | 0.20 | Exponential decay with a configurable 30-day half-life, evaluated against the caller's fixed time |
| Relationship | 0.15 | Exact stored reference matches the hard entity scope or explicitly supplied context; no inferred relationship or graph traversal |
| Confidence | 0.10 | Stored confidence in 0–1; this is provenance metadata, not verification of truth |

Weights must be finite and nonnegative with a positive finite sum; the score divides by that sum. Scores are returned alongside records and shown in search results. Ties use updated time then canonical ID. Text-free retrieval has zero lexical relevance. Candidate selection still starts with the 20 most recently updated eligible records, with explicit expansion to 200; ranking cannot recover excluded older candidates. Context is capped at 20 references. No learned ranker or persistent plaintext index is installed.

## Retention and privacy

Retention choices preserve the current expiry, keep a memory until explicit deletion, or expire it after 24 hours/seven days. Working context is always capped at 24 hours. Expired items remain visibly archived and are excluded from retrieval. There is no scheduler, automatic purge, hidden cleanup or forensic erasure claim.

Review expired memories selects at most 50 IDs/revisions/expiry values without reading personal content or using a key. The confirmation rechecks the exact snapshot, not a fresh broad predicate. A changed or missing reviewed row aborts the whole transaction. An unrelated newly expired record outside that snapshot is left for another review. Linked-content privacy cleanup can remove related records or redact history, as explained in the confirmation. Cycles between reviewed records are handled safely. Key/encryption failures roll back deletion. More batches require another explicit review.

Unsaved edit text stays in RAM and is discarded on screen exit or Activity recreation. It is never written to SavedStateHandle, preferences or logs. Successful saves use the existing AES-GCM envelopes, authenticated field bindings and optimistic revision checks. Save failures discard editor content and show generic errors; concurrent changes require opening the fresh record again. Existing request submission remains disabled. Network permissions, backup exclusions and behavioral-consent boundaries are unchanged.

## Validation

Local validation on 2026-10-08 passed debug/release builds, all 26 JVM tests, debug/release lint, Detekt and instrumented APK compilation. All 95 Android tests passed on owned API 26/34 emulators. Release APK inspection confirms debug inspector/control exclusion. Schema v6 is unchanged and updated local documentation links resolve. New tests cover normalized ranking, context/tie/clock behavior, encrypted manual CRUD, immutable derived provenance, filtered structured retrieval, working expiry, bounded metadata-only review, stale/key-loss rollback, cyclic retention cleanup, UI confirmation/filtering/recreation and concurrent edits.

The debug search fixture measured 20/200 candidates at 182/1,807 ms on API 26 and 138/1,308 ms on API 34. These are single emulator runs; the API 26 default and both expanded searches exceed the 150 ms production target. No representative-device latency pass is claimed.

A final UI follow-up makes long-memory deletion reviews scrollable. The three editor/confirmation tests passed again on local API 26/34, along with repeated build, JVM, lint and Detekt checks. The engine and schema are unchanged by that follow-up.

The [final Phase 5 CI run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37798814741) passed all three jobs, including all 95 Android tests on API 26/36, for `05a80aa8aee9ec21ebfaa80a6d24bc7002a5268e`. The [first implementation run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37797737889) also passed all checks for `f53b266e994933a0a1a3d7019c30db6729f7199b`. This completion update changes documentation only. The Phase 5 memory-engine baseline is complete.

## Limits and next gates

The memory archive still observes saved rows for display; bounded candidate/decryption limits apply to retrieval, not all archive rendering. Large-archive pagination and representative device/security/performance checks remain hardening work. Expanded 200-candidate searches can exceed the production latency target. Synthetic ranking tests establish deterministic baseline behavior; they are not a real-user relevance or model-accuracy claim.

Phase 6 introduces the unified typed request/result pipeline and policy authority. Phase 7 adds deterministic language specialists with evaluated unknown/negation/ambiguity handling. Android executors, behavioral inference and learned models remain later gates. See the [agent training plan](AGENT_TRAINING_PLAN.md).
