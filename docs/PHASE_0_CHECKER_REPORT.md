1. Phase and branch/commit hashes

Phase 0: Baseline and unblock. Branch: `codex/phase-0-baseline-unblock`.
Base commit: `0e8a92f41c2809cfc5fb167f4008a553ec038e60`.
This report is included in the phase implementation commit; obtain its hash with `git log -1 --format=%H -- docs/PHASE_0_CHECKER_REPORT.md`.
Only Phase 0 is implemented. Phase 1 has not started.

The starting working tree contained user changes. Those were preserved. In particular, the pre-existing `nlu-contract` workflow job was left unstaged; the phase commit contains only the Android workflow changes. Unrelated user changes and untracked ML/data files are excluded from this commit.

2. Files changed (path, one-line purpose each)

- `.github/workflows/android.yml`: Use capabilityTest build tasks and APK verification; run the existing guarded proof/suite harness on CI emulators; upload its reports.
- `CONTRIBUTING.md`: Replace the disabled connectedDebugAndroidTest instructions and document archival test policy.
- `app/build.gradle.kts`: Remove production Kotlin and archived TSV unit-test input bindings; retain the project directory property and Detekt baseline.
- `app/src/main/java/dev/metis/agent/AppIsolation.kt`: Comment-only production edit used to verify that historical source certificates no longer freeze current development.
- `app/src/test/java/dev/metis/agent/CalendarV13Test.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/CandidateV12ReadTest.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/CandidateV14Test.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/CandidateV15Test.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/CandidateV16Test.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/ClassifierV11SafetyTest.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/EnglishRulesTest.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/HumanDevelopment004Test.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/ReadCoverageV10Test.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/ReadCoverageV7Test.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/ReadCoverageV8Test.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `app/src/test/java/dev/metis/agent/ReadSafetyV6Test.kt`: Mark only historical evaluation-fixture methods archived with explicit JUnit Ignore reasons; preserve self-contained tests.
- `tools/legacy_ml/check.py`: Remove the live production-tree certificate comparison; retain archive integrity and packaged-model checks; report archive integrity accurately.
- `tools/legacy_ml/test_retirement.py`: Add isolated source-edit acceptance and archive-tamper rejection regressions.
- `docs/PHASE_0_CHECKER_REPORT.md`: Record verification evidence, changed-test reasons, limitations and the phase boundary.

3. Commands run (exact) and their real result summaries, including test counts executed/passed/failed/skipped

Commands below ran from `C:\antigravity_projects\metis-repo` using PowerShell, JDK 17 and Python 3.12.10. Raw logs and XML snapshots are under ignored `.local-evidence/phase0/`; these are local evidence, not committed build artifacts.

Baseline full local verification:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebugAndroidTest --continue *> "$env:TEMP\metis-phase0-baseline.log"
```

Real output:

```text
BUILD SUCCESSFUL in 42s
155 actionable tasks: 9 executed, 146 up-to-date
```

The baseline unit task and Detekt task were UP-TO-DATE. The instrumentation alias resolved to `assembleCapabilityTestAndroidTest`; it did not run on a device. The log was copied to `.local-evidence/phase0/baseline.log`.

Fresh baseline unit run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --rerun-tasks *> .local-evidence/phase0/baseline-unit-fresh.log
```

Real output:

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 31s
26 actionable tasks: 26 executed
```

JUnit XML totals: 180 discovered, 180 executed/passed, 0 failed/errors, 0 skipped. XML preserved in `.local-evidence/phase0/baseline-fresh-unit-results/`.

First changed-tree verification:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleCapabilityTest :app:assembleCapabilityTestAndroidTest :app:verifyCapabilityTestApks --continue *> .local-evidence/phase0/final-verification.log
```

This attempt found 20 new `MaxLineLength` Detekt findings in the archival annotations. All reasons were shortened without changing which tests are archived. Real output: `BUILD FAILED in 3m 7s`, `174 actionable tasks: 37 executed, 137 up-to-date`. Its unit XML showed 160 executed/passed, 20 skipped, 0 failed/errors. APK identity verification printed `{"status": "PASS", "mode": "verify", "application_id": "dev.metis.agent.capabilitytest"}`. The failed attempt is retained, not represented as a passing verification run.

Corrected verification:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleCapabilityTest :app:assembleCapabilityTestAndroidTest :app:verifyCapabilityTestApks --continue *> .local-evidence/phase0/corrected-verification.log
```

Real output:

```text
> Task :app:detekt
> Task :app:verifyCapabilityTestApks
{"status": "PASS", "mode": "verify", "application_id": "dev.metis.agent.capabilitytest"}
> Task :app:testDebugUnitTest
> Task :app:lintDebug
BUILD SUCCESSFUL in 1m 11s
174 actionable tasks: 10 executed, 164 up-to-date
```

Fresh final JUnit XML totals: 180 discovered, 160 executed/passed, 0 failed/errors, 20 explicitly archived/skipped. Both debug and release lint reports say `0 errors, 0 warnings, 14 hints`. Assemble debug/release, Detekt, capability-test application and Android test compilation, and final APK manifest verification all succeeded (unchanged tasks were UP-TO-DATE). This verifies local acceptance with the comment-only production edit present. The harness metadata was generated before the phase commit; its source digest binds the actual working tree used for the build.


Python verification:

```powershell
python -m unittest discover -s tools/legacy_ml -p 'test_*.py' -v
python -m unittest discover -s tools/capability_testing -p 'test_*.py' -v
```

Retirement tests: 8 executed/passed, 0 failed, 0 skipped (`Ran 8 tests in 0.038s`, `OK`). Harness tests: 8 executed/passed, 0 failed, 0 skipped (`Ran 8 tests in 0.007s`, `OK`). Outputs saved as `retirement-tests.log` and `harness-tests.log`.

The baseline retirement suite also ran before edits: 6 executed/passed, no failures or skips. The baseline APK gate returned PASS with `production_sources_unchanged: true` for both debug and release APKs.

Final APK/archive check:

```powershell
python tools/legacy_ml/check.py --apks app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release-unsigned.apk *> .local-evidence/phase0/final-archive-apk-check.log
```

Real JSON output:

```json
{
  "status": "PASS",
  "archive_integrity_verified": true,
  "production_ml_enabled": false,
  "apks": [
    {
      "apk": "app\\build\\outputs\\apk\\debug\\app-debug.apk",
      "sha256": "181021db236fcd461eb42f9c0e5cbc321e271d81d0eb84bf55468d564f83ef56",
      "assets_checked": 0,
      "legacy_models": 0
    },
    {
      "apk": "app\\build\\outputs\\apk\\release\\app-release-unsigned.apk",
      "sha256": "5e501ad032ce841b9b70d0a4102c04f60be9b7b1be69d8bb627fca1095b1967e",
      "assets_checked": 3,
      "legacy_models": 0
    }
  ]
}
```

Additional checks performed:

- Parsed the exact staged workflow with PyYAML and asserted that its build/device jobs use capability tasks and harness proof/suite commands. Output: `Staged workflow YAML parsed; build and android-smoke jobs use explicit capability tasks and harness proof/suite.` This is a local configuration check, not a GitHub Actions run.
- Compared baseline/current `AppIsolation.kt` with block comments removed: `PASS: AppIsolation.kt differs from the baseline only inside a block comment.`
- `git diff --quiet -- docs/evaluation archive/legacy_ml`: exit 0; no tracked archival/evaluation changes.
- `git diff --check`: exit 0 (Git emitted line-ending conversion notices).

4. New tests added and what each proves

- `RetirementTests.test_production_edits_and_new_files_do_not_require_retirement_recertification`: a synthetic repository with no historical certificate or evaluation fixtures passes before a comment edit, after that edit, and after adding a production Kotlin file.
- `RetirementTests.test_public_archive_tampering_is_still_rejected`: changing public archive metadata without updating its recorded digest still fails closed.

No existing test assertions were deleted or rewritten. The following 20 methods were marked archived; each one-line reason is recorded here and on its JUnit annotation:

- `CalendarV13Test.kt#exposedUserBatchHasFourCalendarAndFourMemoryReadsOnly`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CalendarV13Test.kt#exposedBenchmarkClarificationsRemainBlockedWithCalendarEnabled`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CalendarV13Test.kt#otherExposedCalendarRequestsStillNeedClarification`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CandidateV12ReadTest.kt#completeCorpusPreservesScopeAndBlocksEveryForcedUnsafeLabel`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CandidateV12ReadTest.kt#newRecallCannotForgeEntityTypesSpansOrCompleteQuery`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CandidateV14Test.kt#completeNativePythonReplayAndAllForcedReadLabels`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CandidateV14Test.kt#forgedSpansTypesAndCompleteQueriesFailBeforeContextAndDispatch`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CandidateV15Test.kt#nativePythonScopesEntitiesQueriesAndForcedLabels`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CandidateV15Test.kt#forgedScopesAndExtractionNeverReachReadPorts`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CandidateV16Test.kt#nativePythonScopesEntitiesQueriesAndForcedLabels`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `CandidateV16Test.kt#forgedScopesAndExtractionNeverReachReadPorts`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `ClassifierV11SafetyTest.kt#everyProhibitedRequestRejectsEveryForcedReadLabelBeforeContext`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `EnglishRulesTest.kt#versionedDevelopmentCorpusMatchesIntentAndSafeOutcome`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `HumanDevelopment004Test.kt#exportCurrentLocalCalendarEnabledBehavior`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `HumanDevelopment004Test.kt#forcedReadLabelsAndForgedExtractionCannotBypassAdmission`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `ReadCoverageV10Test.kt#completeCorpusChecksCoverageSpansQueriesConstraintsAndAllForcedLabels`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `ReadCoverageV10Test.kt#forgedTypesSpansAndCompleteQueriesCannotReachContextOrPorts`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `ReadCoverageV7Test.kt#developmentCasesPreserveReferencesAndCannotGainAuthorityFromWrongLabels`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `ReadCoverageV8Test.kt#developmentCasesCheckSpansLiteralQueriesAndEveryForcedLabelBeforeContext`: Archived Phase 0: requires historical docs/evaluation fixtures.
- `ReadSafetyV6Test.kt#exposedNonreadsCannotReadEvenUnderEveryIncorrectClassifierLabel`: Archived Phase 0: requires historical docs/evaluation fixtures.

5. Anything NOT RUN or UNVERIFIED

- GitHub Actions: NOT RUN. The branch was not pushed and no remote workflow was dispatched. CI acceptance of the comment-only edit is UNVERIFIED, despite local task/configuration checks.
- Emulator/API 26 and 36 instrumentation: NOT RUN. Phase 0 requested Android test compilation, which is distinct from execution. The updated CI proof/suite sequence has not executed in this session.
- Physical-device testing: NOT RUN; no device installation or data mutation performed.
- The user's pre-existing NLU job and unrelated ML/data work: NOT RUN; outside Phase 0 and excluded from the phase commit.
- Archived corpus methods are intentionally skipped, not passed or recertified.

6. Known risks, shortcuts, TODOs

- Local comment-only acceptance passed. Remote CI acceptance remains pending an actual workflow run.
- Archived corpus coverage is removed from active verification: 20 skipped methods; 160 self-contained methods remain active. Historical implementations/fixtures remain available as evidence.
- The SDK tooling emitted its pre-existing XML-format version warning. It did not prevent the baseline build.
- Existing Detekt baseline is retained; this phase does not claim to fix historical findings suppressed by it.
- The legacy build-only instrumentation alias is retained for compatibility. CI and contributor instructions now use the real capability variant tasks. Production install/connected tasks remain disabled.
- No runtime behavior, schema, permissions, field encryption, chat/agent implementation, evaluation files or ML archive contents changed.

7. Open questions for me

None for Phase 0 implementation. Stop here for review; do not begin Phase 1 until the user explicitly requests it.
