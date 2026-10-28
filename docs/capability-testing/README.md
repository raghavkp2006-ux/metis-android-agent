# Synthetic Android capability-test harness

The repository-defined `capabilityTest` **build type** uses application ID `dev.metis.agent.capabilitytest`. Debug/release production IDs remain `dev.metis.agent`. This is test-infrastructure remediation, not capability implementation or ML enablement.

## Isolation boundaries

- APK target and instrumentation IDs must exactly match `tools/capability_testing/policy.json`.
- The target APK must have no shared UID and must disable backup. Its actual binary manifest is inspected with SDK `apkanalyzer` before installation.
- The test runner checks the actual target context, synthetic BuildConfig flag and process UID before Application creation and test fixtures.
- Synthetic storage uses `dev.metis.agent.capabilitytest.metis-personal.db`, a separately named Keystore alias, scoped preferences, work names and notification channels. Android also assigns separate UID/files/cache/private storage to the target package. No DataStore implementation currently exists to share.
- Production resource names and database schema remain unchanged; no production migration or reinstall is needed.
- Host destructive utilities permit only the exact two synthetic package IDs. Both installed APK hashes and the installed instrumentation declaration are rechecked before tests.
- Android Gradle still lists `install*`, `uninstall*`, and `connected*` tasks. Their configured `doFirst` guards fail the task before its actions run, but never invoke them while a physical device is attached. Use the guarded host harness instead. The legacy `assembleDebugAndroidTest` alias builds only the synthetic test APK; it does not run it.
- Every run starts with absent synthetic packages and uses explicit synthetic test fixtures. Existing synthetic installations are refused, rather than silently cleared.

## Physical-device guard

- Always unset `ANDROID_SERIAL` and pass an explicit `adb -s <serial>` on every ADB command. Never rely on an implicitly selected device.
- Never run Gradle tasks whose names start with `connected`, `install`, or `uninstall` while a physical device is attached, even though Gradle lists them and their guards fail before task actions run.
- Physical capability testing may use only the synthetic target `dev.metis.agent.capabilitytest` and its instrumentation package `dev.metis.agent.capabilitytest.test`. Never install, clear, or uninstall the production package `dev.metis.agent` or the legacy package `dev.metis.agent.test`.
- Record `adb -s <serial> shell pm list packages` and `adb -s <serial> shell pm path dev.metis.agent` before and after each phone session in that phase's `CHECKER_REPORT`.
- Physical device `ed01e333` is paused until the unexplained absence of `dev.metis.agent` is resolved. Until then, only those two read-only package queries may be run against that serial.

## Build and inspect, without device writes

From the repository root, using Python 3.12+ and the configured Android SDK:

```powershell
.\gradlew.bat --no-daemon :app:assembleCapabilityTest :app:assembleCapabilityTestAndroidTest
python tools/capability_testing/harness.py verify --sdk C:\Android\sdk --app app/build/outputs/apk/capabilityTest/app-capabilityTest.apk --test app/build/outputs/apk/androidTest/capabilityTest/app-capabilityTest-androidTest.apk --output app/build/reports/capability-testing/apk-verification.json
python -B -m unittest discover -s tools/capability_testing -p test_harness.py -v
```

`verify` records the final manifest identities, both APK SHA-256 values, source-content hash, build variant and Git HEAD. Git HEAD alone does not identify uncommitted changes: the source-content hash binds the actual checkout used by the harness. Keep other editors paused during builds and device attestations.

## Required device sequence

Use a fresh emulator first. Never implicitly select an ADB device. Use a new output directory for each attempt; existing output directories are refused.

```powershell
python tools/capability_testing/harness.py proof --sdk C:\Android\sdk --app app/build/outputs/apk/capabilityTest/app-capabilityTest.apk --test app/build/outputs/apk/androidTest/capabilityTest/app-capabilityTest-androidTest.apk --serial emulator-5582 --output app/build/reports/capability-testing/emulator-proof-NEW
python tools/capability_testing/harness.py suite --sdk C:\Android\sdk --app app/build/outputs/apk/capabilityTest/app-capabilityTest.apk --test app/build/outputs/apk/androidTest/capabilityTest/app-capabilityTest-androidTest.apk --serial emulator-5582 --proof app/build/reports/capability-testing/emulator-proof-NEW/result.json --output app/build/reports/capability-testing/emulator-suite-NEW
```

The small proof explicitly seeds a fictional task, encrypted storage, preferences and delayed synthetic work, then verifies persistence across process restart and emulator reboot. Cleanup uninstalls only the synthetic target/test packages and checks production package state before/after. If production is absent, the evidence states that; it does not claim preservation of an existing production database.

Physical `proof` and `suite` commands require `--emulator-attestation <passing-emulator-suite/result.json>`. That attestation must bind the same APKs and source and include the successful isolation/reboot proof. Physical suite also requires a successful proof for that exact phone via `--proof`. Failed/missing/stale emulator evidence refuses execution before installation. The physical proof does not reboot the personal phone.

Instrumentation logs stream to the output directory. Failure still triggers only allowlisted synthetic cleanup. Existing `@Ignore` cases are reported as skipped, not as passed or as verified by a JVM check.

## Historical compatibility

V16/V17 archived files, model scores and manifests are not rewritten. Source changes for test isolation necessarily invalidate V16's *current-source* certificate. `tools/capability_testing/compatibility.py` uses a separately versioned harness source binding while retaining all frozen-data hash checks and original behavior assertions. It does not recertify a model or grant fitting/adoption approval. Earlier binding attempts are retained when source drift/build corrections require a later binding.

```powershell
python -B tools/capability_testing/compatibility.py --current
python -B tools/capability_testing/compatibility.py
```

Raw direct legacy discovery can still reject obsolete current-source certificates; do not edit historical manifests to silence those failures. Final manifest inspection uses the official SDK [apkanalyzer tool](https://developer.android.com/tools/apkanalyzer).

## Incident boundary

Read [the recovery assessment](RECOVERY_ASSESSMENT.md). v001 remains FAILED with personal data not recovered. Its surviving evidence is archived under ignored `.local-evidence/`, outside Gradle clean outputs. The preservation audit also records Gradle's deletion of old generated build outputs, including the failed-run application APK; a hash alone is not a recoverable APK copy. No successful preservation claim is made for that deleted material.

Capability implementation and language dataset/model work remain stopped until the new baseline's required gates pass.
