# Legacy ML retirement — 2026-10-16

Status: IMPLEMENTED. Legacy ML is retired; production remains deterministic.
METIS NLU v1 is an empty, untrained fresh workspace. No historical model was fitted,
retuned or rescored during this retirement.

## Preservation

- 3,207 original files copied with exact bytes; 3,207 SHA-256 matches, zero missing/mismatched files.
- Required V16 evidence also matched the pre-existing scored_attempt_041 bindings before removal.
- Models, first-result reports, configuration trials, raw predictions/logits/probabilities
  where recorded, confusion matrices, audit inputs, environments, source snapshots,
  historical failures, logs and dependencies remain in the private archive.
- Registry covers V4, V5, V9, V11, V12, V14 and V16. The global manifest also covers
  other generations, including existing V17 MASSIVE work; it was not used as new NLU data.
- Emulator disk images, emulator download chunks and Python caches are explicitly
  excluded infrastructure, not training/evaluation evidence.
- 412 historical data/configuration artifacts have separate privacy/exposure records;
  protected historical benchmarks remain protected, and no automatic training reuse is approved.
- Confirmed 48/50 V12 development utterances in the 254 unique V16 training rows.

## Active development

162 offline Python/dependency entry points removed after verification. Old classifier
CI, fitting/scoring steps, Gradle snapshot exports and the old capability certificate
runner are retired. Historical docs/certificates retain old paths as historical context.
No legacy sklearn dependencies remain in the active development path.

All production source/resource bytes match the pre-retirement snapshot, including
EnglishRules, deterministic V16 parsing, authorization, storage and V17 validators.
The portable CI certificate normalizes only Git checkout line endings; archived
evidence retains raw-byte hashes. Deterministic Kotlin safety regressions and their
fixtures remain active. No app/database/device installation or storage operation ran.

## Validation

- 180 JVM tests: zero failures/errors (baseline executed; final run reused valid task outputs).
- Debug and optimized unsigned Release APK builds: PASS.
- Debug/Release lint: PASS.
- Detekt: PASS with an explicit baseline for nine pre-existing V17 style/complexity
  findings. The initial failure is not a new retirement regression; validator source
  remains unchanged. New findings still fail. See `config/detekt-legacy-retirement-baseline.xml`.
- Retirement verifier tests: six PASS; capability harness safety tests: eight PASS.
- Final APK model-asset inspection: PASS; exact APK SHA-256/results in `apk-verification.json`.
- Private archive hash re-verification: PASS; portable repository/source gate: PASS.
- Staged public-only checkout (without private evidence): repository gate and six verifier tests PASS.
- Private evidence files are read-only; public generated metadata uses LF for portable hash checks.
- No device instrumentation ran for this source-preserving retirement; JVM regressions
  do not constitute a new phone/emulator certification.

Commands: `gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug
:app:assembleRelease :app:lintDebug :app:lintRelease :app:detekt`,
`python tools/legacy_ml/retire.py verify`,
`python -m unittest discover -s tools/legacy_ml -p 'test_*.py' -v`, and
`python tools/legacy_ml/check.py --apks <debug-apk> <release-apk>`.

## Git and privacy

`metis-pre-legacy-ml-retirement` records the existing app work before removal.
`retirement-start.json` also records the original HEAD and dirty-state provenance;
the exact initial working bytes are independently preserved in the private manifest.
`metis-legacy-ml-final` identifies the completed retirement and empty NLU workspace.

Raw evidence is local/private and ignored by Git. Git commits/tags back up public
metadata and previously tracked history, NOT ignored private artifacts. Keep a
private backup of `archive/legacy_ml/private/evidence` together with `manifest.json`.
Nothing was pushed or published. Conservative quarantine is not an assertion that
all historical data is personal; it prevents unreviewed raw data from being published.

Next task: METIS NLU v1 — Dataset and Label Schema. No training is authorized yet.
