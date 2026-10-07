# Phase 4 — Memory search and migration slice

Date: 2026-10-07. Continues the [first persistence slice](PHASE_4_VERIFICATION.md). The full Phase 4 gate remains open.

## Implemented scope

- Physical Room schema v2 adds nullable memory `entity_type`/`entity_id` and their index. The supported references are TASK and SCHEDULE. Repository writes validate the referenced row inside the write transaction. Deleting either entity removes its explicitly linked memory in that same transaction, while unrelated memory remains. Failed revision checks do not delete linked records.
- Explicit migration 1 -> 2 adds the columns/index and rebuilds integrity triggers. Existing encrypted BLOBs, IDs, timestamps, revisions, schedule/task foreign keys, and key/AAD format are preserved. Unknown references stay NULL. Both exported schemas are versioned; destructive fallback remains absent.
- MemorySearchQuery validates text length (200 characters), result bounds (1–100), candidate bounds (1–200), and paired typed UUID references. Candidate selection applies type, explicit/derived origin, entity, and expiry filters before decryption using a fixed SQL statement with bound values. Search checks the 20 most recently updated eligible memories by default; You offers an explicit expansion to 200. The DAO reads one additional encrypted row solely to detect truncation. A truncated candidate set is reported even if no match is found. Default output is at most 50 matches, ordered by update time then UUID; this is not Phase 5 relevance ranking.
- Literal Unicode words are quoted and combined with implicit AND (whitespace) for bound FTS MATCH input, compatible with Android FTS4 builds that use basic query syntax. Operators and punctuation in user input cannot create SQL or advanced FTS expressions. Punctuation-only input yields an empty result without invoking FTS.
- Each search builds a new SQLite `:memory:` FTS4 index with memory-only temporary storage from decrypted candidates, then closes it on success, failure, or cancellation. It never attaches the persistent database, exports the index, or creates a plaintext disk mirror. Decrypted display results remain ordinary process memory; no forensic memory-erasure claim is made.
- You has local word search with loading, no-match, failure/retry, candidate/result-limit states, and an explicit larger-search control. Input and results stay in transient ViewModel state, outside SavedStateHandle. Editing cancels prior queries and resets the budget for changed text; repository changes and retry preserve the current budget. Clearing input restores saved-record browsing. Expiry exclusion applies to query time; stored-record browsing and retention/deletion policies remain separate work.
- Commands, mutations from production UI, reminders, calendar/device access, voice, autonomous actions, exports, telemetry and network permissions stay unavailable. No Phase 5 ranking, relationship weighting, inference, or behavioral analysis is enabled.

## Validation

- Passed `assembleDebug`, `assembleRelease`, `testDebugUnitTest` (12 JVM tests), `lintDebug`, `lintRelease`, `detekt`, and `assembleDebugAndroidTest` on JDK 17. Lint reported no errors or warnings.
- Final APKs passed all 32 instrumented Android tests on the project-owned API 34 emulator. Earlier runs encountered a focused System UI ANR dialog intercepting injected Back events; restarting that emulator cleared the dialog and the full suite passed. No production Back handling changed.
- Exported Room schema v2 and the original v1 schema are committed together with the explicit migration and migration tests.

Tests cover literal query handling and bounds; metadata/expiry filtering before decryption; exactly 20/default and 200/expanded decryptions; stable output and visible truncation; Unicode case/diacritic matching; index rebuilding after updates/deletes; absence of persistent FTS tables, database files, and plaintext; linked-memory deletion/revision rollback; invalid references, key loss and corrupt selected content; v1 -> v2 preservation of encrypted records and BLOB bytes; restored SQL triggers and foreign-key behavior; fresh v2 schema; missing-migration refusal without deletion; local search recreation/update/delete behavior; and explicit expansion to find an older match. Existing Back tests wait for IME visibility across activity/sheet windows and asynchronous sheet dismissal; no production Back behavior is changed.

The initial 200-candidate debug/API 34 measurement was 1,356 ms, above the source plan's typical 150 ms search target. The design was revised to a configurable 20-candidate default with explicit 200-candidate expansion; encryption and disk privacy were retained. An intermediate run measured 122 ms/default and 1,191 ms/expanded; the final full-suite run measured 170 ms/default and 1,619 ms/expanded. These single synthetic emulator/debug samples show variability and do not establish a representative-device performance gate. The typical latency target remains unverified, with the final default sample also above it.

## Remaining gates

The remaining logical tables/relationships, broader developer inspection, representative performance/security/device tests, and additional migration paths are still Phase 4 work. Phase 5 still owns configurable memory ranking and fuller retrieval semantics. Other phases own event/action/audit transactions, policy-gated mutations, retention, encrypted export/import, whole-app deletion, and platform recovery. API 26/36 and physical-device runtime are not claimed by the local API 34 run.

Implementation follows SQLite's [in-memory database documentation](https://www.sqlite.org/inmemorydb.html) and [FTS4 documentation](https://www.sqlite.org/fts3.html).
