# Phase 4 — Task dependencies slice

Date: 2026-10-07. Continues the [memory search and migration slice](PHASE_4_SEARCH_VERIFICATION.md). The full Phase 4 gate remains open.

## Implemented scope

- Exported physical Room schema v3 adds `task_dependencies`, with UUID identity, timestamps, optimistic revision, and two task foreign keys. Both endpoints cascade-delete their edges. A unique pair index rejects duplicate prerequisites; the reverse index supports prerequisite deletion. SQL triggers enforce nonnegative revision, timestamp ordering, and no self-edge on insert/update.
- The internal dependency repository validates canonical, distinct task UUIDs and existing endpoints. It rejects cycles by iterative reachability inside the same Room write transaction as insertion/update. Updates exclude their previous edge during validation; stale saves/deletes fail without changing the graph. SQL constraints cover direct writes except cycles, which remain a repository invariant. Raw DAO/SQL writes are internal and must not bypass that invariant.
- Dependency metadata remains ordinary structured local metadata; no additional personal text is stored. The existing Keystore-readability guard applies to dependency observation and mutations. Lost keys fail without key replacement, data reset, or partial writes. Task titles/notes, schedule text, and memory content retain their encryption and AAD format.
- Today displays counts of explicitly saved prerequisite tasks, refreshed from Room Flow and retained across activity recreation. This does not infer readiness, block task actions, or schedule work. You's debug-only inspection also displays prerequisite-link counts; seeding still explicitly requires an empty database and creates the original three synthetic records.
- Explicit migrations cover v2 → v3 and chained v1 → v2 → v3. No existing encrypted column is rewritten, and no prerequisite is invented for existing tasks. Integrity triggers for previous tables are rebuilt. Unsupported/missing migration paths fail without destructive fallback.
- Debug/release KSP schema writers are ordered when both variants build in one Gradle invocation to prevent simultaneous writes to the shared exported schema JSON.
- Agent mutations, dependency editing through production UI, planning, device access, reminders, autonomous actions, networking, telemetry, exports, and behavioral inference remain unavailable.

## Validation

- Passed `assembleDebug`, `assembleRelease`, `testDebugUnitTest` (15 JVM tests), `lintDebug`, `lintRelease`, `detekt`, and `assembleDebugAndroidTest` on JDK 17. Lint reported no errors or warnings.
- Final APKs passed all 41 instrumented Android tests on the project-owned API 34 emulator, including 7 dependency tests and the dependency-aware screen test. The full run also passed existing storage, migration, search, navigation, and privacy coverage.
- Exported Room schema v3 is committed with the v1 and v2 schemas and explicit chained migration coverage.

Coverage includes canonical IDs/self-links; shared prerequisites, disconnected graphs and a 10,000-task iterative chain; duplicate/orphan/cycle rejection and rollback; stale revisions; competing opposite edges; cascade deletion from either endpoint; database reopen; SQL integrity constraints; key loss; v1/v2 migration preservation; and Today count refresh/recreation/deletion. Existing storage, search, navigation, accessibility, and privacy checks remain in the full suite.

## Remaining gates

The other target tables/relationships, broader developer inspection, representative security/performance/device checks, and future migration paths remain Phase 4 work. The [search latency limitation](PHASE_4_SEARCH_VERIFICATION.md) remains unresolved; this slice does not change or requalify search. API 26/36 and physical-device runtime are not claimed by local API 34 validation. Phase 5 ranking and later planner/action policy work remain deferred.
