# Phase 3 — Navigation shell verification

Date: 2026-10-07. Scope: navigation and temporary draft editing. Agent requests, command parsing, storage, actions, and voice remain unavailable.

## Implementation

- Five typed main destinations: Today, Plan, Agent, Timeline, You, with accurate unavailable states and no synthetic tasks, memories, or receipts in production.
- Bottom navigation on normal phones; scrollable navigation rail on windows at least 600dp wide, under 480dp high, or using at least 1.5x font scaling. Labels and controls remain available with large text.
- Shared request-draft sheet reachable from every destination and the informational privacy detail. Button and IME submission stay disabled; no microphone handler exists.
- Immutable StateFlow presentation state in a lifecycle-aware ViewModel; destination, draft, and sheet/detail visibility use SavedStateHandle. Draft length is limited to 4,000 UTF-16 code units to bound instance-state size. Android instance-state restoration is not durable memory or guaranteed recovery after app dismissal.
- Privacy detail retains source context. Back dismisses IME/input/detail before returning to Today; root Back delegates to Android. Per-destination scroll state uses a saveable state holder.
- Edge-to-edge scaffold consumes safe-drawing padding; composer sheet handles navigation-bar and IME insets and scrolls on short layouts. No new app permissions.

## Checks

Three JVM state regressions cover Back precedence, draft retention, restoration from saved primitive values, detail source, unknown saved route, and bounded draft size. Five new Android tests cover every destination/composer entry, disabled submission including IME, shared draft/recreation, sheet/detail Back behavior, keyboard Back, and dark 2x text navigation reachability. Existing five JVM and eight Android privacy/design-system regressions remain.

## Local results

The complete Windows verification command passed on implementation commit `2be9ba2`: debug and optimized unsigned release builds, all 8 JVM tests (zero failures/errors), debug/release lint (zero errors/warnings; 9 informational upgrade notices), Detekt, and instrumented APK compilation. No lint/static-analysis baseline was added.

All 13 Android tests also passed on the task-owned API 34 emulator using the `311535b` application/test APKs. The subsequent `2be9ba2` change only removes unused strings and rewords the draft-length label; its rebuilt application APK was used for the final landscape/tablet review. Runtime output is retained at `.gradle/phase3-visuals/instrumentation-api34.log` (ignored).

Visual review covered a normal phone in light mode, a 320dp phone with 2x system text in dark mode, the expanded composer, a short landscape window with rail scrolling to You, and a roughly 1,067dp tablet width. Status/navigation/taskbar areas remain outside content controls. Large text wraps and content scrolls; shared composer entry stays reachable. Screenshots and hierarchy dumps are retained at `.gradle/phase3-visuals/` (ignored).

## CI results and phase gate

Implementation commit: `2be9ba2fb62421307e79ef70ce5e91dc4d266324`. The [CI run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37621594177) passed all three jobs.

| Check | Result | Evidence |
| --- | --- | --- |
| Debug and optimized unsigned release builds | PASS | Complete local and CI builds |
| JVM regressions | PASS | All 8 local tests; CI testDebugUnitTest successful |
| Debug/release lint and Detekt | PASS | Required CI/local tasks successful; no baseline |
| Android test APK compilation | PASS | Local and CI assembleDebugAndroidTest |
| API 26 runtime | PASS | Job 112793163875: all 13 tests executed, BUILD SUCCESSFUL |
| API 34 runtime | PASS | Local instrumented runner: OK (13 tests) |
| API 36 runtime | PASS | Job 112793163317: all 13 tests executed, BUILD SUCCESSFUL |
| Visual/inset/accessibility baseline | PASS for reviewed behavior | Phone/light, narrow 2x text/dark, sheet, landscape rail scroll, tablet; labelled hierarchy and reachability inspected |
| Repository docs/formatting | PASS | Relative Markdown links resolve; git diff --check clean |

Phase 3's navigation baseline is complete; Phase 4 (Room database) may begin. Full system process-death, spoken TalkBack, physical-device, and performance validation remain later hardening work. No real commands, durable drafts, personal memory, or Android actions are claimed.

## References

Implementation follows Android's [state-saving guidance](https://developer.android.com/develop/ui/compose/state-saving) and [inset handling](https://developer.android.com/develop/ui/compose/system/insets-ui). Local generated screenshots/reports are ignored build artifacts.
