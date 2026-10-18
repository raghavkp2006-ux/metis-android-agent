1. Phase and branch/commit hashes

CI flake triage only. Implementation: `70c830008dd35ff0357efeda146808c7bf5d35c4` (initial change `1ae8734c75c09070fa2b63c32552bf636040caf9`, exact-match preservation follow-up `70c8300`). The final follow-up adds this completed report and evidence only. Base: `ed8b2c48d83b82c0ae11dffdb697139ded8f378f`; branch: `codex/ci-flake-triage`.
PR #7 is parked as draft at `ae7c84ae1115d740fed2196fefa76bc85b487ddb`. No Phase 3 commits were copied or pushed; no merge was performed. Original checkout edits were preserved in place; implementation uses a separate worktree.

2. Files changed (path, one-line purpose each)

- `.github/workflows/android.yml`: Explicit cold boot, RAM/heap, readiness setup, and independent full manual workflow dispatches.
- `app/src/androidTest/java/dev/metis/agent/BehavioralReflectionScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/DecisionScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/FoundationScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/FoundationSmokeTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/FrontendTestNavigation.kt`: Shared synchronization for record loading, real action completion, lazy chat scrolling, and actual viewport visibility.
- `app/src/androidTest/java/dev/metis/agent/HabitCheckinScreenTest.kt`: Await restored controls after the ViewModel finishes; clear fixture ViewModels and close/delete its database/key.
- `app/src/androidTest/java/dev/metis/agent/HabitCheckinScreenshotTest.kt`: Tear down the Activity and fixture ViewModel before closing/deleting its database/key.
- `app/src/androidTest/java/dev/metis/agent/IsolatedAppStateRule.kt`: Cancel outstanding synthetic reminders and clear synthetic records outside the Activity lifecycle, before/after every app UI test.
- `app/src/androidTest/java/dev/metis/agent/MemoryEditorScreenTest.kt`: Use per-test isolated app state and synchronize seeded records before navigation.
- `app/src/androidTest/java/dev/metis/agent/MemorySearchScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/NavigationShellTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/PlannerScreenTest.kt`: Use isolated app state and shared result/scroll synchronization while preserving original text-match strictness.
- `app/src/androidTest/java/dev/metis/agent/PlanningScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/PreferenceScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/ProactiveScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/ReminderScreenTest.kt`: Use isolated app state and shared result/visibility synchronization while retaining every existing assertion.
- `app/src/androidTest/java/dev/metis/agent/RequestFlowTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/SavedRecordsScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/TaskActionScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/TaskDependencyScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `app/src/androidTest/java/dev/metis/agent/VoiceInputScreenTest.kt`: Apply per-test app state isolation before Activity launch and after Activity teardown.
- `docs/CI_FLAKE_TRIAGE_CHECKER_REPORT.md`: This completed seven-section report and both evidence tables.
- `docs/ci-flake-triage/failed-run-evidence.json`: All six original failed-job summaries, checked-out commits, and artifact SHA-256s.
- `docs/ci-flake-triage/proof-runs.json`: Five complete workflow results, both emulator results per run, and artifact/hash/isolation verification summaries.
- `tools/capability_testing/emulator_ready.py`: Bounded emulator-only boot/package-manager readiness and verification that all three animation scales are zero.
- `tools/capability_testing/harness.py`: Transfer installed APKs with adb pull, retain exact hashes, and restore readiness/animation setup after the proof reboot.
- `tools/capability_testing/test_harness.py`: Regressions for exact/truncated/changed APK transfer and refusal of physical-device readiness setup.

3. Commands run (exact) and their real result summaries, including test counts

Triage was reported in chat before any fixes. Retrieved all six failed-job logs plus each failed API's uploaded artifact. All six build jobs succeeded. The artifact evidence is recorded in `ci-flake-triage/failed-run-evidence.json`.

| Root-cause class | Run | API | Failing class#method | Error / stack top |
|---|---|---:|---|---|
| Async fixture/records load | [39112134248](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39112134248) | 26 | `MemoryEditorScreenTest#derivedFilterAndExpiryReviewNeverEditProvenanceOrDeleteWithoutConfirmation` | ComposeTimeoutException after 10000 ms; waitUntil -> test line 77 |
| DB write before UI readiness | [39121683094](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39121683094) | 26 | `HabitCheckinScreenTest#archiveRestoreAndDeleteFlow` | performScrollTo: restored habit delete node missing; fetchOneOrThrow -> test line 118 |
| DB write before UI readiness | [39126531482](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39126531482) | 36 | `HabitCheckinScreenTest#archiveRestoreAndDeleteFlow` | Same missing delete node; fetchOneOrThrow -> test line 118 |
| Async action / lazy conversation visibility | [39140352969](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39140352969) | 26 | `PlannerScreenTest#realComposerAcceptsConflictFreePlanThenSeparatelyReviewsAcceptsAndCancelsReminder` | ComposeTimeoutException after 10000 ms; waitUntil -> test line 85 |
| Async scroll/layout visibility | [39142093920](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39142093920) | 26 | `ReminderScreenTest#proposalDisclosesPrecisionAndOnlyAcceptedReminderCanBeCancelled` | Component not displayed; assertIsDisplayed -> test line 75 |
| ADB binary stream truncation | [39142096893](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39142096893) | 36 | `None: failed before instrumentation` | REFUSED: installed APK is not the verified APK; installed_target -> harness line 173 |

Five failures are instrumentation failures; the API 36 PR run failed before tests. Its readback artifact is a 120,320-byte truncated ZIP, rather than a complete APK. The hash refusal is retained; readback now uses `adb pull` instead of `exec-out cat`.

Planner and reminder classifications identify synchronization/visibility mechanisms supported by the failing location and source; the old artifacts do not contain a semantic tree or action-state dump proving every intermediate state. There is no evidence of OOM/ANR or boot timeout in these six failures. The PR workflow checked out merge commit `eef3ab53`, while the push workflow checked out head `ae7c84a`.

Local command:

```powershell
python -m unittest discover -s tools/capability_testing -p 'test_*.py' -v
```

Result: 11 tests passed, including 3 new regression tests.

```powershell
.\gradlew.bat --no-daemon :app:assembleCapabilityTest :app:assembleCapabilityTestAndroidTest
```

Result: BUILD SUCCESSFUL in 1m 47s; 68 actionable tasks, all executed.

The exact-match follow-up also compiled successfully using `:app:assembleCapabilityTestAndroidTest`: BUILD SUCCESSFUL in 36s; 50 actionable tasks (5 executed, 45 up-to-date).

Full workflow proof: **five consecutive fully green runs**, all on `70c830008dd35ff0357efeda146808c7bf5d35c4`, each passing on attempt 1. One push plus four manual workflow dispatches; no failed-job reruns.

| Run | Event | Build / unit / lint / Detekt | API 26 full suite | API 36 full suite |
|---|---|---|---|---|
| [39164321813](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39164321813) | push | PASS | PASS | PASS |
| [39164322383](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39164322383) | workflow_dispatch | PASS | PASS | PASS |
| [39164324098](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39164324098) | workflow_dispatch | PASS | PASS | PASS |
| [39164325692](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39164325692) | workflow_dispatch | PASS | PASS | PASS |
| [39164327488](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/39164327488) | workflow_dispatch | PASS | PASS | PASS |

Each emulator job runs the real guarded `proof` and `suite` harness commands from `.github/workflows/android.yml`, without a class filter. Completed artifacts report 223 passing instrumented tests and 3 pre-existing skipped statuses, with proof/reboot verification, exact APK identity, cleanup, and unchanged production-package snapshots passing. Logs confirm all three animation scales at zero before proof and again after its reboot.

Build artifact inspection: 38 JVM suites, 207 total tests, 187 passed, 20 pre-existing skips, zero failures/errors. All five builds passed packaging, JVM tests, lint, Detekt, archive integrity, 11 harness safety tests, and synthetic APK verification.

Proof launch/inspection commands (local ignored helper uses authenticated GitHub REST calls without printing credentials):

```powershell
git push origin codex/ci-flake-triage
python .local-evidence/ci_control.py dispatch 4
python .local-evidence/ci_control.py status
python .local-evidence/ci_control.py collect
```

`dispatch 4` sends four full `POST /repos/raghavkp2006-ux/metis-android-agent/actions/workflows/android.yml/dispatches` requests with `{"ref":"codex/ci-flake-triage"}`. Connector reads independently confirmed every job result. The earlier preflight run `39164225927` on `1ae8734` was superseded/cancelled by the exact-match follow-up before proof began; it is not counted as a pass.

4. New tests added and what each proves

- `InstalledApkReadbackTest#test_complete_transfer_still_requires_both_exact_hashes`: both installed APKs use serial-targeted ADB file transfer and retain identity checks.
- `InstalledApkReadbackTest#test_truncated_or_changed_readback_is_refused`: incomplete and changed bytes still fail exact verification.
- `InstalledApkReadbackTest#test_readiness_setup_refuses_physical_serial_before_adb`: physical serial `ed01e333` is refused without issuing an ADB command.

5. Anything NOT RUN or UNVERIFIED

The required five full workflows are verified on the implementation commit. The final documentation/evidence-only push and PR can trigger additional CI; those are separate from the completed five-run proof. Physical-device testing is intentionally paused. Instrumentation was run on CI emulators; a separate local emulator run was not needed. No Gradle connected*, install*, or uninstall* tasks were invoked.

6. Known risks, shortcuts, TODOs

No new ignores, removed assertions, loosened tolerances, test retries, workflow retries, or infrastructure retry wrappers. Boot readiness polling is bounded and occurs before instrumentation, including after reboot. Scroll synchronization waits for actual visibility with existing assertion semantics. CI already requested animations disabled; explicit readback verifies those settings after the harness reboot too.

7. Open questions for me

None. After opening the triage PR, stop. Phase 3 remains stopped until the user confirms resumption; PR #7 remains draft/unmerged and its branch remains at `ae7c84a`.
