# Phase 4 — Explicit preferences slice

Date: 2026-10-08. Continues the [task dependency slice](PHASE_4_DEPENDENCIES_VERIFICATION.md). The full Phase 4 gate remains open.

## Implemented scope

- Physical Room schema v4 adds `preferences`, with UUID/timestamp/revision metadata, a unique registered key, encrypted `typed_value`, stable value kind, value schema version 1, and EXPLICIT source. The registry supports minute-precision day start time, focus-block minutes (5–240), and week start day. Typed Kotlin constructors require the correct value for each key. No default preference is stored or inferred.
- SQL insert/update triggers enforce registered key/kind pairings, EXPLICIT origin, supported value version, nonnegative revision and timestamp ordering. Encrypted value contents are validated after authorized decryption by the codec, not by SQL. Unsupported keys, including autonomy/behavioral consent, cannot be stored through this registry.
- The preference repository observes ordered saved values on IO. Saves/deletes use transactions and optimistic revisions; duplicate keys and changing a row's registered key fail rather than silently replace another record. Personal values use the existing Keystore AES-GCM envelope, fresh nonce and `preferences/id/typed_value` AAD.
- The existing readable-key guard now includes preferences, including when they are the only stored personal records. Lost keys or invalid selected ciphertext block writes without replacement keys, fallback deletion, or reset. Empty-database debug seed checks also count preferences, so they cannot seed over preference-only storage. Debug inspection displays preference counts; the original synthetic fixture set is unchanged.
- You observes explicit saved planning preferences with loading/error/empty behavior shared with saved records. Values refresh after repository changes and activity recreation. There is no production preference editor, planner activation, autonomous action, or consent/autonomy control.
- Explicit v3 → v4 migration creates the empty table/index, rebuilds existing integrity triggers, and installs preference constraints. V1/v2 upgrades chain through the earlier versions. Existing ciphertext, AAD, task links and dependency metadata are preserved. Exported schema v4 is versioned alongside earlier schemas; destructive fallback remains absent.
- Added an [agent training plan](AGENT_TRAINING_PLAN.md) documenting specialist responsibilities, deterministic baselines, dataset splits, future learned-model gates, and local personalization privacy. It introduces no model, runtime, training dataset, or telemetry.

## Validation

- Passed `assembleDebug`, `assembleRelease`, `testDebugUnitTest` (18 JVM tests), `lintDebug`, `lintRelease`, `detekt`, and `assembleDebugAndroidTest` on JDK 17. Lint reported no errors or warnings.
- Final APKs passed all 49 instrumented Android tests on the project-owned API 34 emulator, including 6 preference storage tests, the preference screen test, and direct v3 migration preservation. Existing storage, search, dependency, navigation, privacy and accessibility tests also passed.
- Exported schema v4 and repository-relative Markdown links were checked; `git diff --check` passed. Earlier schemas remain preserved.
- The full-suite synthetic search sample measured 204 ms/default (20 candidates) and 1,713 ms/expanded (200 candidates). These debug/emulator samples remain above the typical target in this run and do not establish a representative-device performance gate. Encryption and search bounds remain intact.

Coverage includes typed key/value validation, duration bounds and minute precision; encrypted round trips and reopen; no plaintext personal values in the persisted database; uniqueness/immutable keys/revisions; preference-only key loss blocking other repository writes; encryption rollback; SQL registry/source/version constraints; corrupt encrypted values failing without partial results; v1/v2/v3 migration and existing-data preservation; fresh v4 schema; and You recreation/update/delete refresh. Existing dependency, storage, search, privacy, navigation and accessibility coverage remains in the full suite.

## Remaining gates

Other target tables/relationships, broader developer inspection, representative performance/security/device checks, future migrations and the previous search-latency limitation remain Phase 4 work. This slice does not certify search performance, API 26/36 or physical-device runtime. Phase 5 ranking, Phase 6 protocol, Phase 7 parsing and later training/planning/action/behavioral features remain pending.
