# Phase 13 — voice input

Phase 13 is complete as a bounded voice-input baseline. Speech enters the same `AgentRequest` path as typed input and remains under the user's explicit control.

## Behavior and privacy

- Tapping the microphone first explains the microphone purpose and whether online fallback is enabled. Only after the user continues does METIS request `RECORD_AUDIO`. Denial leaves typed requests available.
- When Android reports an on-device recognizer as available (API 31+), METIS uses it. If it is unavailable, speech recognition remains unavailable by default. The separate **Allow online speech fallback** setting in You is off by default and is required before METIS may use the general Android recognition service. The implementation does not treat Android's offline-preference hint as a guarantee.
- Listening is user initiated and shows partial text. Stop finalizes recognition; Cancel stops it and restores the draft that existed before voice input. Recognition failures and missing local recognition capability are shown in the composer.
- The transcript remains editable. It is submitted as `InputSource.VOICE` through the same request, parser and result pipeline as text only when the user presses Send. Voice does not create a separate agent or bypass proposal review.
- If on-device recognition returns no transcript and online fallback is enabled, the composer offers a separate **Retry online** button. Online recognition starts only after that tap; when fallback is disabled, no online retry is offered.
- METIS does not persist raw microphone audio. With online fallback enabled and needed, Android may send speech audio to its recognition provider. METIS retains only the editable transcript in the temporary draft, which is discarded with the composer.
- The manifest adds `RECORD_AUDIO` and recognition-service package visibility only. No Internet permission, database migration, audio file, telemetry or background recording was added.

## Verification — 2026-10-09

- Debug and release APK assembly passed.
- Debug and release lint, Detekt and all 95 JVM tests passed.
- The connected Android regression passed on API 26 and API 34: 360 reported test cases total, zero failures, and six existing host-dependent reminder tests skipped (three per device). The voice UI cases and shared request-flow coverage ran as part of this suite.
- Emulator checks cover permission disclosure/denial handling, online-fallback default and setting, parser source handling, and the shared request pipeline. Live audio capture and transcription with a physical device and installed speech provider were not validated.

Commands:

```powershell
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebug :app:assembleRelease
.\gradlew.bat --offline --no-daemon :app:connectedDebugAndroidTest
```

Android recognition capability varies by OS version and installed speech provider. On-device recognition is available through Android's API on supported devices; a general recognizer may use network services, and the platform's offline preference is only a hint. See the [SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer), [RecognizerIntent API](https://developer.android.com/reference/android/speech/RecognizerIntent), and [Android package visibility guidance](https://developer.android.com/training/package-visibility/use-cases).

## Device follow-up — 2026-10-10

After a connected OPPO CPH2691 on Android 16 repeatedly returned no transcript, the phone was checked: `RECORD_AUDIO` was granted and Android reported speech recognition services. The voice path now offers a separate online retry after an on-device no-result only when the user has enabled online fallback. `:app:assembleDebug` passed and the updated APK installed and launched on the connected phone. Live speech transcription still needs a spoken-device retest.
