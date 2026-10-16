# Capability completion v002 — FAILED / physical stage BLOCKED

Date: 2026-10-15, Asia/Calcutta. **READY_FOR_CAPABILITY_IMPLEMENTATION = false.**

The recovery assessment found no recoverable personal METIS copy in the accessible sources checked. Personal data was not recovered. The personal package was not reinstalled or modified in this remediation run. Capability implementation, language dataset creation, MiniLM training and production ML enablement remain stopped.

## Requested results

| Item | Result |
| --- | --- |
| 1. Recovery assessment | Closed with data not recovered; see `../../capability-testing/RECOVERY_ASSESSMENT.md` |
| 2. Recoverable personal data | None found. APKs, hashes, source ZIPs and emulator snapshots are not claimed as personal-device backups |
| 3. New application variant | Repository-defined **build type** `capabilityTest`, target ID `dev.metis.agent.capabilitytest`; instrumentation ID `dev.metis.agent.capabilitytest.test` |
| 4. Production refusal guard | PASS: real production APK rejected before device access; direct Gradle production install refused; eight host safety tests passed; runtime runner checks target package/flag/UID before Application creation |
| 5. Final APK/manifest verification | PASS for app, test package, instrumentation target/runner, no shared UID, disabled backup and both installed APK hashes |
| 6. Emulator isolation | PASS: explicit fictional seed, namespaced database/preferences/Keystore/channels/work, process restart, emulator reboot, encrypted-data readback and synthetic-package cleanup |
| 7. Physical isolation proof | NOT RUN. The actual host guard refused the phone because emulator suite evidence failed; no phone installation occurred |
| 8. v002 baseline | FAILED: two unique JVM shell tests fail in both variants; five emulator UI tests fail; three pre-existing host-managed tests remain ignored |
| 9. Synthetic data | All executed device tests used the separate synthetic application and synthetic fixtures. The personal package was absent before/after the emulator proof; no claim is made about preservation of an extant production database on that emulator |
| 10. Readiness | FALSE. Do not restart capability implementation |

## Source and reproducibility

- Branch: `codex/capability-test-isolation-v002`.
- Git HEAD: `00f6a9c8fdbb18136ed74ad888e311a7310da6f4`.
- **The tested checkout contains uncommitted changes. HEAD alone does not identify the built source.** Existing concurrent UI changes were retained and paused by the user. This task also fixed their missing luminance extension import/call and a line-length violation, without changing intended navigation behavior.
- Final app/test source-content SHA-256: `c1efce3e4a3a228b3658546cbb8732529265cc418f36b4626ef033d694b14253`.
- Final target APK SHA-256: `95ffb7f1db0e2eb65294686fe0a55c3a0c19f680ed1c19e883a5d5d95282a194`.
- Final instrumentation APK SHA-256: `6fcf7f9d88359184f1ae80d1b1aa33e941e2bb5b4bb67bc43813eeef87bc2af4`.
- A source-input archive, including the uncommitted source inputs, is retained outside Gradle clean outputs at `.local-evidence/capability-testing/v002/source-inputs-final.zip`; SHA-256 `31f0945eaaa554c6aca34768ee39eed8faa88bcdf60c2b30a912fb1493101cac`.
- No commit was created, and unrelated paused work was not staged or reverted.

## Checks executed

| Check | Final result |
| --- | --- |
| Debug JVM | 141 executed: 139 passed, 2 failed, no skips |
| Release JVM | 141 executed: 139 passed, 2 failed, no skips |
| Debug/release APK builds | PASS |
| `capabilityTest` app/instrumentation builds | PASS |
| Debug/release/capability-test lint | PASS |
| Detekt | PASS |
| Python root evaluation tests | 13 passed |
| Current source-bound compatibility tests | 18 passed |
| Historical behavior compatibility tests | 58 passed |
| Harness safety tests | 8 passed |
| Emulator small proof | Three checks passed: initial seed, process restart readback, emulator reboot readback; cleanup and unchanged-production-state checks passed |
| Final full emulator suite | 204 selected: 201 executed, 196 passed, 5 failed, 3 skipped; suite completed and cleanup passed |
| Physical test stage | BLOCKED; failure gate refused before installation |

The Android emulator was a fresh API 34 / Android 14 synthetic AVD (`metis_capability_isolation_v002`). The connected CPH2691 phone is Android 16 / API 36, but no positive physical test result is claimed.

The first complete emulator attempt had six failures, including one test that expected the old production WorkManager tag. That test was corrected to assert the exact synthetic namespace. The APKs were rebuilt, and both the isolation proof and complete suite were rerun. The final suite retains five UI failures. Earlier attempt logs/results remain separate; no failed attempt was relabeled as passing.

## Failing and skipped cases

The two JVM failures in `ShellStateTest` are:

- `invalidRestoredRouteFallsBackAndDraftIsBoundedBeforeSaving`;
- `backClosesInputAndDetailBeforeReturningToTodayWithoutDiscardingDraft`.

Both expect Today while the retained concurrent navigation changes use Agent/Chat. Their expectations were not weakened to force a passing baseline.

The five final emulator failures are:

- `FoundationSmokeTest.shellLaunchesAndSurvivesRecreation`: expected status text not found;
- `NavigationShellTest.backDismissesDraftThenDetailThenReturnsToToday`: expected Today selection not present;
- `PlanningScreenTest.planAndTaskLinksReflectStoredPlanningRecordsAndDeletion`: UI condition timeout;
- `SavedRecordsScreenTest.screensObserveSavedRowsAcrossActivityRecreationWithoutEnablingRequests`: UI condition timeout;
- `TaskDependencyScreenTest.todayReflectsSavedPrerequisitesAcrossRecreationAndDeletion`: UI condition timeout.

These failures must be reconciled with the intended shell/navigation behavior before declaring a passing baseline. No physical run may bypass the emulator gate to investigate them.

Three pre-existing ignored cases remain explicit:

- `ReminderPlatformTest.disabledNotificationsPreventRegistration`;
- `ReminderRestartTest.stage1RegisterForHostRestart`;
- `ReminderRestartTest.stage2VerifyAndCleanAfterHostRestart`.

The harness independently exercised synthetic process/reboot persistence; that does not relabel those ignored product-specific tests as passed.

See `jvm-tests.json` and `emulator-tests.json` for exact counts and case names, and `emulator-isolation-proof.json` / `emulator-suite-result.json` for target/source identities and cleanup checks.

The final target/instrumentation APKs are also retained read-only under `.local-evidence/capability-testing/v002/`. The complete raw harness report archive is `harness-reports-closed.zip`, verified to contain all 53 source report files, SHA-256 `387f6234bd8c9227aaae4599eccdc7ff73804c5369879d8498cbc0c702cddad1`. An earlier incomplete archive, created while emulator logs were open, is retained separately as `harness-reports-partial-v001.zip` and must not be treated as the complete archive.

## Infrastructure changes

- Added permanent `capabilityTest` build configuration and distinct synthetic app label.
- Added `AppIsolation` resource naming. Production database, preferences, aliases, work and channel names remain unchanged; synthetic names are explicit and Android private storage is additionally separated by package UID. No schema migration was introduced.
- Added a runtime guarded AndroidJUnitRunner and guarded storage fixture initialization.
- Added a small synthetic isolation/restart/reboot test and a package-allowlisted host harness that inspects binary manifests, both installed APK hashes and installed instrumentation declarations.
- Direct Gradle device install/uninstall/test entrypoints refuse execution; the guarded host harness owns device mutations and cleanup. Existing automation invoking direct connected tasks must use the documented harness sequence.
- Physical runs require a passing, source/APK-bound full emulator attestation and same-device isolation proof.
- Added source-bound historical compatibility tooling without changing V16/V17 archived manifests, scores or data. Source-binding revisions disclose checkout changes rather than rewriting old certificates.

## Incident evidence preservation — important limitation

All **741 required incident/history evidence files** checked remain byte-identical: the 681 Phase 15 evidence files, v001 incident documentation, logs, metadata, pre/post hashes, failed init script and instrumentation reports. The v001 failure report was not rewritten.

However, the whole old generated build directory was **not** preserved unchanged. On returning to the normal build directory, Gradle stale-output cleanup deleted **1,447 generated files** from the previous relocated output directory. These include the old failed-run app APK and its output metadata. The lost APK's recorded SHA-256 is `e6d89372690cd92481d2ac74e21d7bdf803333535906dcf845fae1b95db3108d`; no matching copy was found. A recorded hash is not restoration of those bytes. This preservation failure is not hidden by the unchanged incident reports.

The surviving v001 tree was archived outside Gradle clean outputs at `.local-evidence/capability-completion/v001/incident-surviving-evidence.zip`, marked read-only, SHA-256 `cb0ddb6a51c7978a53fdb4ddc49ff205f51de78dfcc6aec7f1d69f8d55d91d2e`. The original full before-hash inventory and deletion audit are retained beside it. The archive does not contain the already deleted generated material, and contains no restorable copy of the lost personal database.

## Stop boundary

Keep capability implementation, the 600-example dataset, MiniLM training, personalization and production ML stopped. Reconcile the current shell/UI failures and ignored required scenarios, rebuild/freeze a new source/APK pair, repeat the complete emulator gate, and only then perform the synthetic physical-device isolation proof and physical baseline.
