# Phase 2 — Design system verification

Date: 2026-10-06. Scope: reusable presentation components; no navigation, command pipeline, storage, Android executors, or voice recognition.

## Implementation

- Explicit Material 3 light/dark semantic roles using the frozen teal/neutral palette; system sans-serif typography, spacing tokens, and control/item/sheet/composer shapes.
- Primary/secondary/icon buttons with labelled controls, minimum 48dp targets, disabled and working states.
- Stateless text composer with keyboard send, blank-input rejection, busy/listening state, optional voice callback, errors, and no automatic recording or submission.
- Messages, typed presentation-only proposals and receipts, scrollable confirmation sheet, task/schedule/memory/timeline rows.
- Empty/loading/error/permission-denied components with retry, enable-access, and not-now callbacks.
- Light/dark, narrow/large-font, tablet, and expired-confirmation previews using clearly marked synthetic content. Synthetic fixtures never appear in production MainActivity.
- The foundation now uses AgentTheme and the disabled composer with an accurate unavailable explanation.

`ProposalDisplay` defaults to unavailable; domain acceptance authority is not implemented. The UI distinguishes verified outcomes from external handoff/pending/failed/cancelled outcomes. Busy proposals disable acceptance/cancellation and block sheet dismissal. Undo is displayed only if supplied by the caller.

## Automated checks

The JVM contrast regression independently calculates WCAG ratios for semantic text/control color pairs in both themes (normal text >=4.5:1; input boundary >=3:1). Four existing privacy regressions remain.

Instrumented interaction tests exercise whitespace/IME rejection, busy repeat-submission prevention, minimum send target size, unavailable-proposal cancellation, explicit confirmation with full details, unverified external handoff/no unsupported undo, and permission denial/not-now. Existing launch/recreation/packaged privacy tests remain.

## Results

Implementation commit: `a71f223`. The [CI run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37469136020) passed all three jobs.

| Check | Result | Evidence |
| --- | --- | --- |
| Debug and optimized unsigned release APKs | PASS | CI assembleDebug/assembleRelease |
| JVM tests | PASS | 5 tests; 0 failures/errors (4 privacy, 1 contrast) |
| Debug/release lint | PASS | 0 errors/warnings; 7 informational dependency/tool notices |
| Detekt | PASS | 0 findings; no baseline |
| Android test compilation | PASS | Instrumented test APK produced |
| API 26 runtime | PASS | All 8 tests executed and passed |
| API 36 runtime | PASS | All 8 tests executed and passed |
| Emulator visual review | PASS for reviewed layouts | API 34 phone, light/dark, 320dp with 2x text, tablet; full confirmation details and controls remain scroll-reachable |
| Accessibility baseline | PASS for inspected behavior | 48dp send target tested; labelled control hierarchy and keyboard focus checked with TalkBack enabled/bound |

The frozen palette/type-scale declarations have narrowly scoped MagicNumber annotations: their literal values are design tokens. Long-parameter-list exemptions apply only to Composable APIs. Other code-style findings were fixed rather than baselined.

Local Windows SDK Platform 36 and Build Tools 35.0.0 were installed. The local full-suite attempt encountered stalled dependency downloads; a resumed Kotlin compiler download was verified against Maven Central's published checksum. CI is the complete build/runtime evidence above; local setup attempts are not counted as passing checks.

## Visual review

Use the debug-only host on an emulator:

```sh
adb shell am start -n dev.metis.agent/.DesignSystemTestActivity --ez showcase true
adb shell am start -n dev.metis.agent/.DesignSystemTestActivity --ez showcase true --ez confirmation true
```

Verify light/dark, 2x text on a narrow viewport, landscape/tablet layouts, scroll reachability, disabled/busy state labels, keyboard handling, and full confirmation details. Inspect focus/labels/touch targets. TalkBack and real-device coverage must be recorded separately; an emulator screenshot does not establish either.

Screenshots and accessibility hierarchy snapshots are retained locally under `.gradle/phase2-visuals/` (ignored generated artifacts). Review used the checksum-verified CI APK. Visual review corrected the preview icon and made the confirmation sheet open expanded with navigation-bar padding. Synthetic examples are clearly labelled and remain outside MainActivity.

TalkBack was activated on the task emulator, and labelled nodes/keyboard focus were inspected. Spoken-output quality and touch-exploration usability were not evaluated with a person; real devices, full accessibility, battery, performance, and production hardening remain Phase 17 work. Tablet review validates component resizing, not Phase 3 adaptive navigation. No timing/performance claims are made.

The debug host and gallery are excluded from release; no METIS permissions or data collection were added. Phase 2's component baseline is complete; Phase 3 may begin.
