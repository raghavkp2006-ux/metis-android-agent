# Phase 4 — First persistence slice

Date: 2026-10-07. Scope: encrypted local storage and read-only saved-record screens for tasks, schedules, and memory. This is the first slice, not completion of the full database phase.

## Implementation and boundaries

- Room 2.7.1 with KSP, physical schema v1 exported under `app/schemas/dev.metis.agent.data.storage.PersonalDatabase/1.json`. Three tables: `tasks`, `schedule_blocks`, `memories`. The logical contract remains the target for subsequent physical versions.
- Immutable domain records and replaceable TaskRepository, ScheduleRepository, and MemoryRepository interfaces. CRUD operations use transactions, stable identity, optimistic revisions, and ABORT inserts rather than REPLACE. Future protocol/policy layers must authorize calls; repository APIs grant no action authority.
- Indexed deadline/task-status, schedule interval/plan-status/task-reference, and memory type/update-time queries. SQL triggers validate status, priority, interval, completion/deadline pairs, confidence/importance, and metadata; domain validation additionally checks UUIDs, zones, content bounds, and positive durations. Triggers are separate from Room's exported table schema and must be recreated and tested by future migrations.
- Schedule task foreign key uses SET NULL on deletion. No reminders, events, audit rows, Android dispatch, or calendar availability claims are created. Missing deadlines remain null. Today displays all saved tasks, including future and undated tasks; it does not calculate a daily plan.
- Keystore AES-256-GCM encryption with fresh nonces for task title/notes, schedule title/reason, and memory content. A versioned envelope selects the v1 key alias; authenticated data binds ciphertext to table/UUID/column. IDs and structured scheduling/status/provenance metadata remain app-private plaintext. Encryption/decryption failures and missing keys fail closed without destructive fallback. Writes to populated storage first verify an existing field is readable, preventing key replacement after key loss.
- Lifecycle-aware saved-record state independently observes repositories on IO dispatchers. Today uses TaskRow; Plan uses ScheduleBlock with dates, zones, status and reason; You uses MemoryRow with explicit/derived provenance. Loading, empty, failure and retry states are visible; failed reads clear decrypted display rows without content logging. Editing/completion callbacks remain unavailable.
- Navigation, Back, draft restoration, disabled submission/voice, no-network/no-permission policy, and backup/device-transfer exclusions remain intact. Agent and Timeline retain accurate unavailable states.
- Debug builds alone include explicit transactional synthetic seeding into an empty database and on-screen record-count inspection. Seeding a nonempty database fails without resetting it. Release source contains only a no-op UI hook; no debug seed records or seed implementation. No exported inspection activity or content logs.

## Verification coverage

- JVM domain validation: invalid content, deadline/zone pairs, durations, priorities, completion state, schedule intervals and confidence; null deadlines and explicit provenance.
- Android persistence: encrypted field round trips after closing/reopening the database, stable ordering, task/schedule/memory updates and deletes, revision conflicts, SQL constraints, foreign keys, encryption-write rollback, missing-key read/write failure with no reset/key replacement, fresh nonces, authenticated tamper/binding rejection, and exported v1 schema validation.
- Android screen regressions: save repository records, observe them in Today/Plan/You, recreate the activity, and verify request submission remains disabled; explicitly seed the empty debug database, verify three records, and confirm a second seed refuses to overwrite them. All fixtures are synthetic; database tests use isolated random database names and test-only key aliases with cleanup.
- Existing privacy, shell navigation, design-system, and large-font tests remain part of validation.

## Local results

The complete Windows check command passed: `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebugAndroidTest`. Debug and optimized unsigned release APKs built; all 10 JVM tests passed with zero failures/errors; debug/release lint reported zero errors/warnings and 12 informational dependency notices; Detekt passed without a baseline or complexity suppressions. The corrected Android test class was rebuilt and debug lint rechecked successfully.

All 22 Android tests passed on the task-owned API 34 emulator: 13 existing privacy/design/navigation regressions, 7 database/encryption/schema tests, and 2 saved-record/debug-seed screen tests. Runtime evidence is retained at `.gradle/phase4-verification/instrumentation-api34.log` (ignored). Fixtures are cleaned up after tests. Reopening a database and recreating an activity are tested; these do not establish full system process-death or reboot recovery.

Documentation-relative links and `git diff --check` pass. API 26/36 CI and physical-device runtime have not been run for this slice. The API 34 emulator is project-owned; the connected physical phone was not modified. No full loaded-data visual/TalkBack review is claimed.

## Remaining Phase 4 work

Remaining logical tables/relationships, metadata-filtered bounded retrieval, rebuildable in-memory FTS4 (never plaintext on disk), broader debug inspection, and migration paths with data-preservation tests are still required. There is no pre-v1 shipped database to upgrade in this slice; MigrationTestHelper validates the exported v1 baseline only. Actual upgrade tests must accompany the next physical version. Memory ranking/search, retention/expiry enforcement, whole-app deletion/export/import, correlated event/action/audit transactions, and platform recovery arrive with their respective capability gates. No forensic deletion, full process-death/device reboot, performance, or production-hardening result is claimed.

Room configuration follows the [official Room documentation](https://developer.android.com/jetpack/androidx/releases/room); pinned dependencies retain compatibility with the repository's Kotlin 2.1.20 baseline.
