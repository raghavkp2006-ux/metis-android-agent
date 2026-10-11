# METIS — Detailed Phase-by-Phase Development Report

Prepared: 10 October 2026  
Repository snapshot: `5718b5a`  
Coverage: canonical Phases 0–19

## How to read this report

This report explains the purpose, implementation, user behavior, validation, failures and limitations of each development phase. It consolidates the repository's phase verification records into one Markdown document. Detailed historical records are included within their respective phases so that implementation details, commands, recovery work and evidence are available without opening many separate files.

**Current position:** Phases 0–14 have documented or implemented baselines; Phase 15 remains active with a **NO_GO** deployment decision; Phases 16–19 are pending. “Complete baseline” means the bounded scope recorded for that phase passed its recorded gate. It does not mean every feature in the original product vision exists or that production readiness has been established.

All test results below are **previously recorded results**, not fresh test executions performed while preparing this report. Counts describe the suite at each checkpoint, generally including earlier regression tests; they must not be summed as unique tests. Compiled instrumentation is distinct from executed device tests, and skipped tests are never counted as passes. References to “now,” “future” or “pending” inside historical records refer to that checkpoint; the phase introduction and current-status sections reconcile later changes.

The roadmap uses the complete sequence from section 56 of the original build plan. Some detailed source-plan sections reuse phase numbers; they are not separate competing phases. The README's older “14–19 pending” row and older architecture statements about absent voice/execution are superseded by the specific Phase 14/15 records and later implementation.

## Phase status overview

| Phase | Main outcome | Current interpretation |
| --- | --- | --- |
| 0 | Product and engineering contracts | Documentation baseline complete |
| 1 | Android project, reproducible builds and CI | Complete baseline |
| 2 | Accessible reusable Compose components | Complete baseline |
| 3 | Five-destination adaptive app shell | Complete baseline |
| 4 | Encrypted Room schema v6 and saved-data foundations | Complete baseline, delivered in seven slices |
| 5 | Explicit local memory editing, retrieval and retention | Complete baseline; search performance limitations recorded |
| 6 | Typed request/proposal/result/action protocol | Complete baseline |
| 7 | Bounded English rules and local reads | Complete bounded baseline |
| 8 | Confirmed task actions and approximate reminders | Complete local baseline; original slice reports retained |
| 9 | Transactional events and correlated Timeline | Complete local baseline |
| 10 | Explainable next-task recommendations | Complete local baseline |
| 11 | Deadline-first accepted day/evening plans | Complete local baseline |
| 12 | Opt-in proactive local notices | Complete local baseline |
| 13 | User-started voice through the same request path | Complete bounded baseline; live spoken-device retest remains open |
| 14 | Consent-based daily/weekly aggregate reflections | Complete bounded baseline |
| 15 | Development ML candidates and reviewed evaluation | Active; latest reviewed gate failed; ML disabled |
| 16 | Bounded emotional-support flows | Pending |
| 17 | Comprehensive hardening | Pending |
| 18 | Consenting real-user pilot | Pending |
| 19 | Production release preparation | Pending |

## Overall system built so far

METIS is an Android personal agent built around local data and explicit user control. Kotlin and Compose provide the interface, Room provides durable records, Android Keystore protects sensitive fields, pure domain engines implement deterministic reasoning, and Android adapters perform bounded platform work. A single app module uses package boundaries rather than premature module splitting.

The main user path is: shared composer → typed request → language interpretation → read answer or proposal → current policy/capability checks → explicit acceptance where required → durable execution → verification → receipt, event and audit. Voice produces editable input for this same path. A request to remember a call never authorizes placing a call.

There are five destinations: Today, Plan, Agent, Timeline and You. Saved data feeds the screens; synthetic demo records are confined to debug tools. The data layer separates explicit memories, factual outcomes, recommendations and derived insights. A stored autonomy value or typed action catalog entry does not establish executable capability.

Important remaining boundaries include unavailable external calendar reading, exact alarms, general unrestricted language understanding, broad external actions and enabled ML. Approximate reminders can run late. Recommendation explanations are deterministic scores, not probabilities. Plans consider saved commitments and do not claim complete calendar availability. Voice online fallback is a separate opt-in, and behavioral reflection is separately consented.



## Phase 0 — Product contract and engineering specification

### Purpose and outcome

Phase 0 turned the original build plan into explicit contracts before implementation. It fixed local-first privacy, offline boundaries, API/language/autonomy defaults, risk levels, permission handling, typed requests/actions/events, storage, UI, datasets and completion gates. These documents defined what later work must prove; they did not create an Android feature. The product catalog remains a specification; later phases enable only evaluated subsets.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PRODUCT_CONTRACT.md

[Original record](PRODUCT_CONTRACT.md)

##### METIS product contract v0.1

Status: Phase 0 implementation baseline, derived from the supplied [build plan](reference/original-build-plan.md). No implemented capability is implied by this specification.

###### Scope and invariants

METIS is a personal assistant for Android. Raw and derived personal data remain on the device by default. Core supported commands work offline when their Android capability can work offline. There is no mandatory LLM, backend, Firebase, cloud database, vector database, or synchronization.

Use Kotlin, Compose Material 3, StateFlow, coroutines/Flow, Hilt, Room/SQLite with FTS, DataStore, WorkManager, Android Keystore, Gradle Kotlin DSL, and GitHub Actions. Start with a single app module and package boundaries; split modules only when needed. UI depends on domain interfaces, never platform executors. Voice produces the same AgentRequest as text.

Every action passes through policy, permission validation, a typed action, verification, and a receipt. Every meaningful action creates an event and an audit record. Recommendations store their reasons and evidence. Untrusted notifications and retrieved content are data, never instructions granting action authority.

###### Implementation decisions completing the source plan

The source plan leaves these details open. Freeze these conservative defaults for initial implementation:

- Minimum Android API 26; target the Play-required API at the time Phase 1 is implemented and recheck before release.
- First language coverage: English/en-IN deterministic parsing. Hinglish belongs in evaluation and is enabled only after measured coverage; unsupported wording receives clarification, not guessed actions.
- Default autonomy: Level 1 (suggest actions). Users may lower or raise it explicitly. No permissions requested at installation or assumed from previous grants.
- Brand accent: restrained teal; exact accessible tokens appear in the UI contract.
- Offline reminders use the persisted local schedule as the source of truth. WorkManager is approximate. Exact-time requests need a supported AlarmManager path and current special-access validation; never claim exact timing for approximate work.
- Disable OS cloud backup/device-transfer of personal app storage by default. Explicit export is user initiated and encrypted. Export format and key recovery must be specified and tested before export is enabled.
- Phase numbers follow section 56, the source plan's complete sequence. Detailed sections are implementation guidance attached to those phases, not a second numbering system.

###### Supported intent catalog

All entries below are planned product support. An intent is enabled only when its phase exit checks pass. Earlier phases must return a clear unsupported response without side effects for later intents.

| Intent | Meaning | Earliest enablement |
| --- | --- | --- |
| CALL_PERSON | Open a resolved person's dialer | 8 |
| SEND_MESSAGE | Prepare message and open external composer | 8 |
| CREATE_TASK | Create a local task | 8 |
| COMPLETE_TASK | Mark a uniquely identified task complete | 8 |
| UPDATE_TASK | Rename or change priority of a uniquely identified local task | 8 |
| DELETE_TASK | Delete a uniquely identified task after confirmation | 8 |
| POSTPONE_TASK | Change a task's resolved due time | 8 |
| CHECK_TASKS | Read/filter tasks | 7 |
| CREATE_REMINDER | Create a persisted local reminder | 8 |
| CREATE_ALARM | Request a user-facing exact alarm | 8 |
| CREATE_TIMER | Start a timer with a resolved duration | 8 |
| CREATE_CALENDAR_EVENT | Propose a calendar insert | 8 |
| CHECK_CALENDAR | Read permitted calendar entries | 7, with calendar adapter |
| CHECK_MEMORY | Search structured memories | 7 |
| CHECK_PERSON | Retrieve locally known person facts | 7 |
| WHAT_DID_I_PROMISE | Retrieve commitments associated with a person | 7 |
| CREATE_GOAL | Create a local goal | 8 |
| CHECK_GOALS | Read local goals | 7 |
| CHECK_PROGRESS | Read factual goal/task progress | 7 |
| PLAN_DAY | Propose a schedule for a day/evening | 11 |
| PLAN_WEEK | Propose a week's schedule | 11 |
| WHAT_SHOULD_I_DO | Rank eligible next actions with evidence | 10 |
| DAILY_REVIEW | Summarize daily recorded outcomes | 14 |
| WEEKLY_REVIEW | Summarize weekly outcomes and patterns | 14 |
| WHAT_DID_I_MISS | Read missed items and permitted events | 9 |
| CATCH_UP | Summarize recent permitted events | 9 |
| START_FOCUS | Start a local focus session | 8 |
| STOP_FOCUS | Stop an existing focus session | 8 |
| START_NAVIGATION | Open the user's maps handler | 8 |
| CHECK_HABITS | Show evidence-backed behavioral patterns | 14 |
| SET_PREFERENCE | Save an explicit preference | 8 |
| START_REFLECTION | Guide a structured review | 14 |
| EMOTIONAL_SUPPORT | Offer a bounded support flow | 16 |

The supplied taxonomy contains 32 names despite the suggested 25–30 initial target. Preserve its full catalog; enable a smaller evaluated subset incrementally. Internal UNKNOWN/UNSUPPORTED outcomes are not executable intents.

###### Action and risk matrix

| Action | Risk | Confirmation / execution boundary |
| --- | --- | --- |
| Search/read tasks, memories, calendar, progress | R0 | Read only; require any underlying access grant |
| Create/update/complete task, note/memory, goal, preference | R1 | Explicit accepted proposal at Level 1; Level 2+ may execute an unambiguous authorized local action |
| Create/cancel reminder, start/stop focus, create timer | R1 | Same as above; timing and delivery capabilities must be validated |
| Create alarm | R1 | Explicit timing and available scheduling capability; permission/access denial means no success receipt |
| Open dialer, message composer, maps handler | R2 | Confirm recipient/destination and content before handoff; the external app controls final action |
| Insert/update calendar event | R2 | Confirm complete event details; provider writes require current grant |
| Delete important task/memory/history, share/export personal data | R3 | Fresh explicit confirmation for a specific target; never autonomous |
| Public communication, financial action, unrestricted app control | R3 | Out of initial scope; reject without side effects |

An autonomy setting never lowers an action's risk. Negated commands, missing parameters, low confidence, conflicting evidence, stale confirmation, or ambiguous entities prevent execution at every level. A reminder to call a person authorizes a reminder only.

###### Autonomy

| Level | Behavior |
| --- | --- |
| 0 | Answer only; no mutation or external handoff |
| 1 | Suggest action proposals; execute only explicit acceptance |
| 2 | Execute authorized safe local actions; R2/R3 still confirm |
| 3 | Proactive permitted routine management with suppression and rate limits |
| 4 | Advanced daily management within the same policy boundaries |

Higher levels are unavailable until their implementation phase passes. Reduce/disable autonomy immediately, cancel pending proactive work as appropriate, and re-evaluate pending proposals after settings change.

###### Permission and capability matrix

| Capability | Preferred path | Access requested only when needed | Denial / absence |
| --- | --- | --- | --- |
| Local tasks/memories/preferences | App-private storage | None | Storage failure is reported |
| Contact selection | System contact picker | URI access supplied by picker; validate scope/lifetime | Ask user to choose/enter target; no broad scraping |
| Broad contact lookup, if justified later | Contacts provider | READ_CONTACTS | Disable lookup; picker/manual target remains available |
| Calling | ACTION_DIAL | No CALL_PHONE | Explain missing handler; never report call completed |
| Messaging | ACTION_SENDTO, supported messaging URI | No SEND_SMS | Explain missing handler; never report message sent |
| Calendar insertion | Calendar insert intent | No calendar permission | Report handoff only; no verified insertion claim |
| Calendar reading | Calendar provider | READ_CALENDAR | Explain access need; availability stays unknown |
| Verified calendar write | Calendar provider | WRITE_CALENDAR; READ_CALENDAR if verification reads require it | No write; propose intent alternative with its limitations |
| Notification delivery | Notification channels | POST_NOTIFICATIONS on API 33+; check app/channel settings on all APIs | Keep local item; explicitly report delivery unavailable |
| Exact reminders | AlarmManager | SCHEDULE_EXACT_ALARM special access when required; check canScheduleExactAlarms | Offer approximate timing with consent or leave unscheduled |
| Device restart restoration | Boot receiver | Manifest RECEIVE_BOOT_COMPLETED | Restore only persisted valid schedules; revalidate access |
| Voice | Android SpeechRecognizer | RECORD_AUDIO at microphone tap | Text remains available; use on-device recognizer when available, explain any online fallback and require opt-in |
| Notification observation | NotificationListenerService | User-granted notification listener special access | Observation disabled; no silent access |
| Navigation | External maps intent | No local location permission for explicit destination | No handler means no launch; external app privacy applies |
| Optional location context | Android location API | Foreground coarse/fine only when justified | Work without location; no initial background location |
| Health integration | Future scoped provider | Deferred to a reviewed feature contract | Disabled in initial scope |

Do not declare sensitive permissions until the corresponding feature exists. AccessibilityService, SMS/call-log access, continuous microphone/GPS, and a permanent foreground service are not initial requirements. Third-party apps may transmit data after a user-authorized handoff; the UI must explain the destination and handoff boundary.

###### Contracts frozen by Phase 0

- [Request, parsed request, proposal, action, result, receipt, and event](contracts/AGENT_PROTOCOL.md).
- [Database v1, encryption boundaries, indexes, and migrations](contracts/DATABASE_V1.md).
- [Design tokens, navigation, universal composer, and UI states](contracts/UI_CONTRACT.md).
- [Dataset schema, leakage prevention, and evaluation](contracts/DATASET.md).
- [Phase ordering and milestone gates](ROADMAP.md).

###### Definition of Done

For every enabled capability: deterministic offline behavior where supported; current permission and policy validation; no ambiguous external action; observable loading/empty/error/denial states; evidence-backed explanation; verified outcome or explicit pending/unknown result; idempotent retry; event/audit persistence; and appropriate automated tests for the action's failure modes.

Schema changes require tested upgrades, preserved records, and no destructive migration fallback. Sensitive fields use Keystore-backed authenticated encryption; logs and CI artifacts contain no personal content. Never store raw microphone audio by default. No private telemetry or dataset contribution without explicit consent.

Performance budgets from the plan: simple parsed command under 150 ms, intent inference under 100 ms, typical task query under 50 ms, memory search under 150 ms, recommendation generation under 250 ms, measured on representative devices. Record conditions and distributions; these are release targets, not current measurements.

Phase 0 is complete when every required contract above exists, the source plan is preserved, numbering conflicts are resolved, and all internal document links are valid. Phase 1 adds the runnable scaffold and CI; it must not claim reminder, memory, or autonomy functionality before those phases pass.


## Phase 1 — Repository, Android scaffold and continuous integration

### Purpose and outcome

This phase made the project buildable and reviewable. It established the Kotlin/Compose app, pinned toolchain, debug/release outputs, static analysis, privacy smoke tests and API 26/36 CI. The initial local emulator attempt was inconclusive and the first CI attempt failed during SDK setup; the later recovery run supplies the actual closing evidence.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_1_VERIFICATION.md

[Original record](PHASE_1_VERIFICATION.md)

##### Phase 1 verification report

Date: 2026-10-04. Scope: repository/build/CI foundation only. The user approved committing and pushing the reviewed Phase 1 changes on 2026-10-05. The previous local Phase 0 commit is `7222424`.

###### Implemented

- Single Kotlin/Compose `:app` module, scrollable light/dark foundation screen, and edge-to-edge activity.
- Gradle wrapper with verified distribution checksum, Kotlin DSL, version catalog, Java/Kotlin target 17, API 36 compile/target and API 26 minimum.
- Debug APK, optimized unsigned release APK, and Android test APK.
- Four JVM privacy regression tests and three Android launch/recreation/packaged-privacy smoke tests.
- Lint and Detekt checks, GitHub Actions build/report job and emulator jobs for APIs 26 and 36.
- All seven required project documents, alongside the preserved Phase 0 contracts.

No command parser, reminders, database, agent engine, voice, autonomous actions, or ML model is implemented in this phase.

###### Environment and commands

Cloud Linux workspace; Temurin JDK 17.0.20.1; Gradle 8.11.1; AGP 8.10.1; Kotlin 2.1.20; Android Platform 36 and Build Tools 35.0.0. Proxy/SDK paths are local setup outside tracked source. The cloud has two CPU cores and no KVM hardware acceleration.

```sh
./gradlew --no-daemon --continue :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebugAndroidTest
```

Final result: **BUILD SUCCESSFUL**, 126 tasks executed. Initial compiler/lint/Detekt findings were corrected before this successful run.

###### Results

| Check | Result | Evidence / limit |
| --- | --- | --- |
| Debug compilation and packaging | PASS | app-debug.apk produced; APK signature v2 verifies |
| Optimized release compilation and packaging | PASS | R8 succeeds; app-release-unsigned.apk produced; unsigned by design |
| JVM tests | PASS | 4 tests, 0 failures, 0 errors, 0 skipped |
| Debug lint | PASS | 0 errors, 0 warnings, 7 informational dependency/tool upgrade notices |
| Release lint | PASS | 0 errors, 0 warnings, same 7 informational notices |
| Detekt | PASS | 0 findings |
| Android test compilation | PASS | All three smoke tests compile; instrumented APK produced |
| Android runtime tests | INCONCLUSIVE | API 26 software emulator boots, but APK installation does not complete within the eight-minute Gradle attempt (timeout exit 124); no smoke-test pass is claimed |
| API 36 runtime | NOT RUN | Configured in CI; no hardware-accelerated runtime available locally |
| Packaged release privacy | PASS | Backup/cleartext disabled, no internet permission, only app launcher activity; debug preview excluded |
| Native/APK alignment | PASS | Release ZIP passes 16 KB alignment check; all bundled native LOAD segments are >=16 KB aligned |
| Workflow validation | PASS | actionlint 1.7.7 accepts workflow; matrix and read-only repository permissions checked |
| Configuration/document validation | PASS | Version catalog TOML, XML, repository Markdown links, and whitespace checks |
| GitHub Actions execution | FAILED (first run) | Pushed on 2026-10-05; the first run failed during SDK setup before compilation/device tests. See the recovery record below. |

The seven informational notices announce newer Gradle/AGP/AndroidX versions. The compatible baseline remains pinned; upgrade availability stays visible in lint reports. Correctness warnings remain fatal. No lint baseline or broad suppression was added.

Gradle also reports that it retains the prebuilt graphics library's debug symbols because stripping is unavailable in this toolchain. Packaging, signature verification, release optimization, and native alignment checks pass. This message does not establish real-device compatibility.

###### Corrections made during verification

- Installed a complete JDK 17 after the preinstalled Java runtime lacked javac.
- Replaced XML test constants absent from Android's compile stubs with supported secure parser features.
- Removed an API 27 navigation-bar theme attribute from the API 26 base resources; Activity edge-to-edge setup handles the system bars.
- Made the IDE preview visible within the module so Detekt no longer treats it as an unused private member.
- Removed the unnecessary Compose test-manifest dependency; smoke tests launch the actual app activity.
- Preserved the original gitignore rules and added Kotlin/OS cache exclusions.

###### Evidence locations

Build reports: `app/build/reports/`. JVM XML results: `app/build/test-results/testDebugUnitTest/`. Android attempt log: `app/build/outputs/androidTest-results/connected/debug/`. These are generated artifacts, excluded from Git. Local verification logs and downloadable review artifacts are retained outside the repository in the workspace.

###### Remaining phase gate

Phase 1 is closed: recovery commit `4702d9f` passed the build job and both API 26/API 36 CI smoke jobs on 2026-10-06. Phase 2 may now start. Full device, accessibility, battery, performance, signing, and production security validation belong to later phases and are not claimed here.

###### CI recovery — 2026-10-06

The [first main-branch run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37343306736) failed in all three jobs. The build log shows `sdkmanager tools` followed by `Failed to find package 'tools'`: setup-android v3's default package list includes a retired SDK package. No compilation or instrumented test ran in that attempt.

Recovery commit `4702d9f` explicitly requests only `platform-tools` from setup-android in both jobs, keeps the separate required API 36/Build Tools 35.0.0 installation, and disables verbose license text. It also records `gradlew` as executable (100755), required by the Linux runner's `./gradlew` commands.

The [recovery run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37465741379) completed successfully. The compile/unit-test/lint/static-analysis job passed, and all three Android smoke tests passed on each of API 26 and API 36. This supplies the previously missing runtime and remote-CI evidence and closes Phase 1.


## Phase 2 — Design system

### Purpose and outcome

The app gained a common visual and interaction language: semantic light/dark colors, typography, spacing, buttons, composer, proposal/receipt cards, rows and explicit empty/error/denial states. Components expose callbacks rather than business authority. Accessibility checks covered contrast, labelled controls, reachable details and touch targets; human spoken TalkBack usability remained a later gate.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_2_VERIFICATION.md

[Original record](PHASE_2_VERIFICATION.md)

##### Phase 2 — Design system verification

Date: 2026-10-06. Scope: reusable presentation components; no navigation, command pipeline, storage, Android executors, or voice recognition.

###### Implementation

- Explicit Material 3 light/dark semantic roles using the frozen teal/neutral palette; system sans-serif typography, spacing tokens, and control/item/sheet/composer shapes.
- Primary/secondary/icon buttons with labelled controls, minimum 48dp targets, disabled and working states.
- Stateless text composer with keyboard send, blank-input rejection, busy/listening state, optional voice callback, errors, and no automatic recording or submission.
- Messages, typed presentation-only proposals and receipts, scrollable confirmation sheet, task/schedule/memory/timeline rows.
- Empty/loading/error/permission-denied components with retry, enable-access, and not-now callbacks.
- Light/dark, narrow/large-font, tablet, and expired-confirmation previews using clearly marked synthetic content. Synthetic fixtures never appear in production MainActivity.
- The foundation now uses AgentTheme and the disabled composer with an accurate unavailable explanation.

`ProposalDisplay` defaults to unavailable; domain acceptance authority is not implemented. The UI distinguishes verified outcomes from external handoff/pending/failed/cancelled outcomes. Busy proposals disable acceptance/cancellation and block sheet dismissal. Undo is displayed only if supplied by the caller.

###### Automated checks

The JVM contrast regression independently calculates WCAG ratios for semantic text/control color pairs in both themes (normal text >=4.5:1; input boundary >=3:1). Four existing privacy regressions remain.

Instrumented interaction tests exercise whitespace/IME rejection, busy repeat-submission prevention, minimum send target size, unavailable-proposal cancellation, explicit confirmation with full details, unverified external handoff/no unsupported undo, and permission denial/not-now. Existing launch/recreation/packaged privacy tests remain.

###### Results

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

###### Visual review

Use the debug-only host on an emulator:

```sh
adb shell am start -n dev.metis.agent/.DesignSystemTestActivity --ez showcase true
adb shell am start -n dev.metis.agent/.DesignSystemTestActivity --ez showcase true --ez confirmation true
```

Verify light/dark, 2x text on a narrow viewport, landscape/tablet layouts, scroll reachability, disabled/busy state labels, keyboard handling, and full confirmation details. Inspect focus/labels/touch targets. TalkBack and real-device coverage must be recorded separately; an emulator screenshot does not establish either.

Screenshots and accessibility hierarchy snapshots are retained locally under `.gradle/phase2-visuals/` (ignored generated artifacts). Review used the checksum-verified CI APK. Visual review corrected the preview icon and made the confirmation sheet open expanded with navigation-bar padding. Synthetic examples are clearly labelled and remain outside MainActivity.

TalkBack was activated on the task emulator, and labelled nodes/keyboard focus were inspected. Spoken-output quality and touch-exploration usability were not evaluated with a person; real devices, full accessibility, battery, performance, and production hardening remain Phase 17 work. Tablet review validates component resizing, not Phase 3 adaptive navigation. No timing/performance claims are made.

The debug host and gallery are excluded from release; no METIS permissions or data collection were added. Phase 2's component baseline is complete; Phase 3 may begin.


## Phase 3 — Navigation shell and shared draft

### Purpose and outcome

The five destinations, adaptive bottom navigation/rail, shared composer draft and predictable Back behavior became real app structure. State restoration was bounded and deliberately distinguished from durable memory. Agent submission was still disabled at this historical checkpoint; subsequent phases supplied it.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_3_VERIFICATION.md

[Original record](PHASE_3_VERIFICATION.md)

##### Phase 3 — Navigation shell verification

Date: 2026-10-07. Scope: navigation and temporary draft editing. Agent requests, command parsing, storage, actions, and voice remain unavailable.

###### Implementation

- Five typed main destinations: Today, Plan, Agent, Timeline, You, with accurate unavailable states and no synthetic tasks, memories, or receipts in production.
- Bottom navigation on normal phones; scrollable navigation rail on windows at least 600dp wide, under 480dp high, or using at least 1.5x font scaling. Labels and controls remain available with large text.
- Shared request-draft sheet reachable from every destination and the informational privacy detail. Button and IME submission stay disabled; no microphone handler exists.
- Immutable StateFlow presentation state in a lifecycle-aware ViewModel; destination, draft, and sheet/detail visibility use SavedStateHandle. Draft length is limited to 4,000 UTF-16 code units to bound instance-state size. Android instance-state restoration is not durable memory or guaranteed recovery after app dismissal.
- Privacy detail retains source context. Back dismisses IME/input/detail before returning to Today; root Back delegates to Android. Per-destination scroll state uses a saveable state holder.
- Edge-to-edge scaffold consumes safe-drawing padding; composer sheet handles navigation-bar and IME insets and scrolls on short layouts. No new app permissions.

###### Checks

Three JVM state regressions cover Back precedence, draft retention, restoration from saved primitive values, detail source, unknown saved route, and bounded draft size. Five new Android tests cover every destination/composer entry, disabled submission including IME, shared draft/recreation, sheet/detail Back behavior, keyboard Back, and dark 2x text navigation reachability. Existing five JVM and eight Android privacy/design-system regressions remain.

###### Local results

The complete Windows verification command passed on implementation commit `2be9ba2`: debug and optimized unsigned release builds, all 8 JVM tests (zero failures/errors), debug/release lint (zero errors/warnings; 9 informational upgrade notices), Detekt, and instrumented APK compilation. No lint/static-analysis baseline was added.

All 13 Android tests also passed on the task-owned API 34 emulator using the `311535b` application/test APKs. The subsequent `2be9ba2` change only removes unused strings and rewords the draft-length label; its rebuilt application APK was used for the final landscape/tablet review. Runtime output is retained at `.gradle/phase3-visuals/instrumentation-api34.log` (ignored).

Visual review covered a normal phone in light mode, a 320dp phone with 2x system text in dark mode, the expanded composer, a short landscape window with rail scrolling to You, and a roughly 1,067dp tablet width. Status/navigation/taskbar areas remain outside content controls. Large text wraps and content scrolls; shared composer entry stays reachable. Screenshots and hierarchy dumps are retained at `.gradle/phase3-visuals/` (ignored).

###### CI results and phase gate

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

###### References

Implementation follows Android's [state-saving guidance](https://developer.android.com/develop/ui/compose/state-saving) and [inset handling](https://developer.android.com/develop/ui/compose/system/insets-ui). Local generated screenshots/reports are ignored build artifacts.


## Phase 4 — Encrypted database and persistence foundations

### Purpose and outcome

Phase 4 supplied durable private data before command execution. Seven slices progressively added the first records, bounded search, task dependencies, preferences, planning links, diagnostics and the complete foundation schema. Physical versions v1–v6 describe migrations within this phase, not six product phases. The final schema has 22 tables; entity/repository presence does not imply that an executor or inference feature is enabled.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_4_VERIFICATION.md

[Original record](PHASE_4_VERIFICATION.md)

##### Phase 4 — First persistence slice

Date: 2026-10-07. Scope: encrypted local storage and read-only saved-record screens for tasks, schedules, and memory. This is the first slice, not completion of the full database phase.

###### Implementation and boundaries

- Room 2.7.1 with KSP, physical schema v1 exported under `app/schemas/dev.metis.agent.data.storage.PersonalDatabase/1.json`. Three tables: `tasks`, `schedule_blocks`, `memories`. The logical contract remains the target for subsequent physical versions.
- Immutable domain records and replaceable TaskRepository, ScheduleRepository, and MemoryRepository interfaces. CRUD operations use transactions, stable identity, optimistic revisions, and ABORT inserts rather than REPLACE. Future protocol/policy layers must authorize calls; repository APIs grant no action authority.
- Indexed deadline/task-status, schedule interval/plan-status/task-reference, and memory type/update-time queries. SQL triggers validate status, priority, interval, completion/deadline pairs, confidence/importance, and metadata; domain validation additionally checks UUIDs, zones, content bounds, and positive durations. Triggers are separate from Room's exported table schema and must be recreated and tested by future migrations.
- Schedule task foreign key uses SET NULL on deletion. No reminders, events, audit rows, Android dispatch, or calendar availability claims are created. Missing deadlines remain null. Today displays all saved tasks, including future and undated tasks; it does not calculate a daily plan.
- Keystore AES-256-GCM encryption with fresh nonces for task title/notes, schedule title/reason, and memory content. A versioned envelope selects the v1 key alias; authenticated data binds ciphertext to table/UUID/column. IDs and structured scheduling/status/provenance metadata remain app-private plaintext. Encryption/decryption failures and missing keys fail closed without destructive fallback. Writes to populated storage first verify an existing field is readable, preventing key replacement after key loss.
- Lifecycle-aware saved-record state independently observes repositories on IO dispatchers. Today uses TaskRow; Plan uses ScheduleBlock with dates, zones, status and reason; You uses MemoryRow with explicit/derived provenance. Loading, empty, failure and retry states are visible; failed reads clear decrypted display rows without content logging. Editing/completion callbacks remain unavailable.
- Navigation, Back, draft restoration, disabled submission/voice, no-network/no-permission policy, and backup/device-transfer exclusions remain intact. Agent and Timeline retain accurate unavailable states.
- Debug builds alone include explicit transactional synthetic seeding into an empty database and on-screen record-count inspection. Seeding a nonempty database fails without resetting it. Release source contains only a no-op UI hook; no debug seed records or seed implementation. No exported inspection activity or content logs.

###### Verification coverage

- JVM domain validation: invalid content, deadline/zone pairs, durations, priorities, completion state, schedule intervals and confidence; null deadlines and explicit provenance.
- Android persistence: encrypted field round trips after closing/reopening the database, stable ordering, task/schedule/memory updates and deletes, revision conflicts, SQL constraints, foreign keys, encryption-write rollback, missing-key read/write failure with no reset/key replacement, fresh nonces, authenticated tamper/binding rejection, and exported v1 schema validation.
- Android screen regressions: save repository records, observe them in Today/Plan/You, recreate the activity, and verify request submission remains disabled; explicitly seed the empty debug database, verify three records, and confirm a second seed refuses to overwrite them. All fixtures are synthetic; database tests use isolated random database names and test-only key aliases with cleanup.
- Existing privacy, shell navigation, design-system, and large-font tests remain part of validation.

###### Local results

The complete Windows check command passed: `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebugAndroidTest`. Debug and optimized unsigned release APKs built; all 10 JVM tests passed with zero failures/errors; debug/release lint reported zero errors/warnings and 12 informational dependency notices; Detekt passed without a baseline or complexity suppressions. The corrected Android test class was rebuilt and debug lint rechecked successfully.

All 22 Android tests passed on the task-owned API 34 emulator: 13 existing privacy/design/navigation regressions, 7 database/encryption/schema tests, and 2 saved-record/debug-seed screen tests. Runtime evidence is retained at `.gradle/phase4-verification/instrumentation-api34.log` (ignored). Fixtures are cleaned up after tests. Reopening a database and recreating an activity are tested; these do not establish full system process-death or reboot recovery.

Documentation-relative links and `git diff --check` pass. API 26/36 CI and physical-device runtime have not been run for this slice. The API 34 emulator is project-owned; the connected physical phone was not modified. No full loaded-data visual/TalkBack review is claimed.

###### Remaining Phase 4 work

Historical scope of this first slice. The subsequent [memory search and migration slice](PHASE_4_SEARCH_VERIFICATION.md) adds bounded in-memory FTS4 and physical v1 -> v2 upgrade coverage; the full phase gate remains open.

Remaining logical tables/relationships, metadata-filtered bounded retrieval, rebuildable in-memory FTS4 (never plaintext on disk), broader debug inspection, and migration paths with data-preservation tests are still required. There is no pre-v1 shipped database to upgrade in this slice; MigrationTestHelper validates the exported v1 baseline only. Actual upgrade tests must accompany the next physical version. Memory ranking/search, retention/expiry enforcement, whole-app deletion/export/import, correlated event/action/audit transactions, and platform recovery arrive with their respective capability gates. No forensic deletion, full process-death/device reboot, performance, or production-hardening result is claimed.

Room configuration follows the [official Room documentation](https://developer.android.com/jetpack/androidx/releases/room); pinned dependencies retain compatibility with the repository's Kotlin 2.1.20 baseline.


#### Source record: PHASE_4_SEARCH_VERIFICATION.md

[Original record](PHASE_4_SEARCH_VERIFICATION.md)

##### Phase 4 — Memory search and migration slice

Date: 2026-10-07. Continues the [first persistence slice](PHASE_4_VERIFICATION.md). The full Phase 4 gate remains open.

###### Implemented scope

- Physical Room schema v2 adds nullable memory `entity_type`/`entity_id` and their index. The supported references are TASK and SCHEDULE. Repository writes validate the referenced row inside the write transaction. Deleting either entity removes its explicitly linked memory in that same transaction, while unrelated memory remains. Failed revision checks do not delete linked records.
- Explicit migration 1 -> 2 adds the columns/index and rebuilds integrity triggers. Existing encrypted BLOBs, IDs, timestamps, revisions, schedule/task foreign keys, and key/AAD format are preserved. Unknown references stay NULL. Both exported schemas are versioned; destructive fallback remains absent.
- MemorySearchQuery validates text length (200 characters), result bounds (1–100), candidate bounds (1–200), and paired typed UUID references. Candidate selection applies type, explicit/derived origin, entity, and expiry filters before decryption using a fixed SQL statement with bound values. Search checks the 20 most recently updated eligible memories by default; You offers an explicit expansion to 200. The DAO reads one additional encrypted row solely to detect truncation. A truncated candidate set is reported even if no match is found. Default output is at most 50 matches, ordered by update time then UUID; this is not Phase 5 relevance ranking.
- Literal Unicode words are quoted and combined with implicit AND (whitespace) for bound FTS MATCH input, compatible with Android FTS4 builds that use basic query syntax. Operators and punctuation in user input cannot create SQL or advanced FTS expressions. Punctuation-only input yields an empty result without invoking FTS.
- Each search builds a new SQLite `:memory:` FTS4 index with memory-only temporary storage from decrypted candidates, then closes it on success, failure, or cancellation. It never attaches the persistent database, exports the index, or creates a plaintext disk mirror. Decrypted display results remain ordinary process memory; no forensic memory-erasure claim is made.
- You has local word search with loading, no-match, failure/retry, candidate/result-limit states, and an explicit larger-search control. Input and results stay in transient ViewModel state, outside SavedStateHandle. Editing cancels prior queries and resets the budget for changed text; repository changes and retry preserve the current budget. Clearing input restores saved-record browsing. Expiry exclusion applies to query time; stored-record browsing and retention/deletion policies remain separate work.
- Commands, mutations from production UI, reminders, calendar/device access, voice, autonomous actions, exports, telemetry and network permissions stay unavailable. No Phase 5 ranking, relationship weighting, inference, or behavioral analysis is enabled.

###### Validation

- Passed `assembleDebug`, `assembleRelease`, `testDebugUnitTest` (12 JVM tests), `lintDebug`, `lintRelease`, `detekt`, and `assembleDebugAndroidTest` on JDK 17. Lint reported no errors or warnings.
- Final APKs passed all 32 instrumented Android tests on the project-owned API 34 emulator. Earlier runs encountered a focused System UI ANR dialog intercepting injected Back events; restarting that emulator cleared the dialog and the full suite passed. No production Back handling changed.
- Exported Room schema v2 and the original v1 schema are committed together with the explicit migration and migration tests.

Tests cover literal query handling and bounds; metadata/expiry filtering before decryption; exactly 20/default and 200/expanded decryptions; stable output and visible truncation; Unicode case/diacritic matching; index rebuilding after updates/deletes; absence of persistent FTS tables, database files, and plaintext; linked-memory deletion/revision rollback; invalid references, key loss and corrupt selected content; v1 -> v2 preservation of encrypted records and BLOB bytes; restored SQL triggers and foreign-key behavior; fresh v2 schema; missing-migration refusal without deletion; local search recreation/update/delete behavior; and explicit expansion to find an older match. Existing Back tests wait for IME visibility across activity/sheet windows and asynchronous sheet dismissal; no production Back behavior is changed.

The initial 200-candidate debug/API 34 measurement was 1,356 ms, above the source plan's typical 150 ms search target. The design was revised to a configurable 20-candidate default with explicit 200-candidate expansion; encryption and disk privacy were retained. An intermediate run measured 122 ms/default and 1,191 ms/expanded; the final full-suite run measured 170 ms/default and 1,619 ms/expanded. These single synthetic emulator/debug samples show variability and do not establish a representative-device performance gate. The typical latency target remains unverified, with the final default sample also above it.

###### Remaining gates

The remaining logical tables/relationships, broader developer inspection, representative performance/security/device tests, and additional migration paths are still Phase 4 work. Phase 5 still owns configurable memory ranking and fuller retrieval semantics. Other phases own event/action/audit transactions, policy-gated mutations, retention, encrypted export/import, whole-app deletion, and platform recovery. API 26/36 and physical-device runtime are not claimed by the local API 34 run.

Implementation follows SQLite's [in-memory database documentation](https://www.sqlite.org/inmemorydb.html) and [FTS4 documentation](https://www.sqlite.org/fts3.html).


#### Source record: PHASE_4_DEPENDENCIES_VERIFICATION.md

[Original record](PHASE_4_DEPENDENCIES_VERIFICATION.md)

##### Phase 4 — Task dependencies slice

Date: 2026-10-07. Continues the [memory search and migration slice](PHASE_4_SEARCH_VERIFICATION.md). The full Phase 4 gate remains open.

###### Implemented scope

- Exported physical Room schema v3 adds `task_dependencies`, with UUID identity, timestamps, optimistic revision, and two task foreign keys. Both endpoints cascade-delete their edges. A unique pair index rejects duplicate prerequisites; the reverse index supports prerequisite deletion. SQL triggers enforce nonnegative revision, timestamp ordering, and no self-edge on insert/update.
- The internal dependency repository validates canonical, distinct task UUIDs and existing endpoints. It rejects cycles by iterative reachability inside the same Room write transaction as insertion/update. Updates exclude their previous edge during validation; stale saves/deletes fail without changing the graph. SQL constraints cover direct writes except cycles, which remain a repository invariant. Raw DAO/SQL writes are internal and must not bypass that invariant.
- Dependency metadata remains ordinary structured local metadata; no additional personal text is stored. The existing Keystore-readability guard applies to dependency observation and mutations. Lost keys fail without key replacement, data reset, or partial writes. Task titles/notes, schedule text, and memory content retain their encryption and AAD format.
- Today displays counts of explicitly saved prerequisite tasks, refreshed from Room Flow and retained across activity recreation. This does not infer readiness, block task actions, or schedule work. You's debug-only inspection also displays prerequisite-link counts; seeding still explicitly requires an empty database and creates the original three synthetic records.
- Explicit migrations cover v2 → v3 and chained v1 → v2 → v3. No existing encrypted column is rewritten, and no prerequisite is invented for existing tasks. Integrity triggers for previous tables are rebuilt. Unsupported/missing migration paths fail without destructive fallback.
- Debug/release KSP schema writers are ordered when both variants build in one Gradle invocation to prevent simultaneous writes to the shared exported schema JSON.
- Agent mutations, dependency editing through production UI, planning, device access, reminders, autonomous actions, networking, telemetry, exports, and behavioral inference remain unavailable.

###### Validation

- Passed `assembleDebug`, `assembleRelease`, `testDebugUnitTest` (15 JVM tests), `lintDebug`, `lintRelease`, `detekt`, and `assembleDebugAndroidTest` on JDK 17. Lint reported no errors or warnings.
- Final APKs passed all 41 instrumented Android tests on the project-owned API 34 emulator, including 7 dependency tests and the dependency-aware screen test. The full run also passed existing storage, migration, search, navigation, and privacy coverage.
- Exported Room schema v3 is committed with the v1 and v2 schemas and explicit chained migration coverage.

Coverage includes canonical IDs/self-links; shared prerequisites, disconnected graphs and a 10,000-task iterative chain; duplicate/orphan/cycle rejection and rollback; stale revisions; competing opposite edges; cascade deletion from either endpoint; database reopen; SQL integrity constraints; key loss; v1/v2 migration preservation; and Today count refresh/recreation/deletion. Existing storage, search, navigation, accessibility, and privacy checks remain in the full suite.

###### Remaining gates

The other target tables/relationships, broader developer inspection, representative security/performance/device checks, and future migration paths remain Phase 4 work. The [search latency limitation](PHASE_4_SEARCH_VERIFICATION.md) remains unresolved; this slice does not change or requalify search. API 26/36 and physical-device runtime are not claimed by local API 34 validation. Phase 5 ranking and later planner/action policy work remain deferred.


#### Source record: PHASE_4_PREFERENCES_VERIFICATION.md

[Original record](PHASE_4_PREFERENCES_VERIFICATION.md)

##### Phase 4 — Explicit preferences slice

Date: 2026-10-08. Continues the [task dependency slice](PHASE_4_DEPENDENCIES_VERIFICATION.md). The full Phase 4 gate remains open.

###### Implemented scope

- Physical Room schema v4 adds `preferences`, with UUID/timestamp/revision metadata, a unique registered key, encrypted `typed_value`, stable value kind, value schema version 1, and EXPLICIT source. The registry supports minute-precision day start time, focus-block minutes (5–240), and week start day. Typed Kotlin constructors require the correct value for each key. No default preference is stored or inferred.
- SQL insert/update triggers enforce registered key/kind pairings, EXPLICIT origin, supported value version, nonnegative revision and timestamp ordering. Encrypted value contents are validated after authorized decryption by the codec, not by SQL. Unsupported keys, including autonomy/behavioral consent, cannot be stored through this registry.
- The preference repository observes ordered saved values on IO. Saves/deletes use transactions and optimistic revisions; duplicate keys and changing a row's registered key fail rather than silently replace another record. Personal values use the existing Keystore AES-GCM envelope, fresh nonce and `preferences/id/typed_value` AAD.
- The existing readable-key guard now includes preferences, including when they are the only stored personal records. Lost keys or invalid selected ciphertext block writes without replacement keys, fallback deletion, or reset. Empty-database debug seed checks also count preferences, so they cannot seed over preference-only storage. Debug inspection displays preference counts; the original synthetic fixture set is unchanged.
- You observes explicit saved planning preferences with loading/error/empty behavior shared with saved records. Values refresh after repository changes and activity recreation. There is no production preference editor, planner activation, autonomous action, or consent/autonomy control.
- Explicit v3 → v4 migration creates the empty table/index, rebuilds existing integrity triggers, and installs preference constraints. V1/v2 upgrades chain through the earlier versions. Existing ciphertext, AAD, task links and dependency metadata are preserved. Exported schema v4 is versioned alongside earlier schemas; destructive fallback remains absent.
- Added an [agent training plan](AGENT_TRAINING_PLAN.md) documenting specialist responsibilities, deterministic baselines, dataset splits, future learned-model gates, and local personalization privacy. It introduces no model, runtime, training dataset, or telemetry.

###### Validation

- Passed `assembleDebug`, `assembleRelease`, `testDebugUnitTest` (18 JVM tests), `lintDebug`, `lintRelease`, `detekt`, and `assembleDebugAndroidTest` on JDK 17. Lint reported no errors or warnings.
- Final APKs passed all 49 instrumented Android tests on the project-owned API 34 emulator, including 6 preference storage tests, the preference screen test, and direct v3 migration preservation. Existing storage, search, dependency, navigation, privacy and accessibility tests also passed.
- Exported schema v4 and repository-relative Markdown links were checked; `git diff --check` passed. Earlier schemas remain preserved.
- The full-suite synthetic search sample measured 204 ms/default (20 candidates) and 1,713 ms/expanded (200 candidates). These debug/emulator samples remain above the typical target in this run and do not establish a representative-device performance gate. Encryption and search bounds remain intact.

Coverage includes typed key/value validation, duration bounds and minute precision; encrypted round trips and reopen; no plaintext personal values in the persisted database; uniqueness/immutable keys/revisions; preference-only key loss blocking other repository writes; encryption rollback; SQL registry/source/version constraints; corrupt encrypted values failing without partial results; v1/v2/v3 migration and existing-data preservation; fresh v4 schema; and You recreation/update/delete refresh. Existing dependency, storage, search, privacy, navigation and accessibility coverage remains in the full suite.

###### Remaining gates

Other target tables/relationships, broader developer inspection, representative performance/security/device checks, future migrations and the previous search-latency limitation remain Phase 4 work. This slice does not certify search performance, API 26/36 or physical-device runtime. Phase 5 ranking, Phase 6 protocol, Phase 7 parsing and later training/planning/action/behavioral features remain pending.


#### Source record: PHASE_4_PLANNING_VERIFICATION.md

[Original record](PHASE_4_PLANNING_VERIFICATION.md)

##### Phase 4 projects and goals

Schema v5 adds encrypted projects and goals, nullable task project/goal links, supporting indexes, status/priority/metadata constraints and an explicit v4 upgrade. A missing association remains null. Project/goal deletion uses SET NULL, preserving tasks, schedules, dependencies and unrelated goals. Personal titles/descriptions use the same Keystore AES-GCM envelopes and authenticated table/id/column bindings as other records. Project-only and goal-only storage participate in the existing readable-key guard.

PlanningRepository observes and saves typed immutable records with revision checks and transactional writes. It rejects missing references and conflicting task project/goal assignments; changing a goal's project cannot invalidate existing explicit task project links. Goal/project consistency is an application rule, not a raw SQL constraint. ABORT inserts preserve identity; no REPLACE or destructive migration is used.

Today shows the actual saved project/goal associated with each task. Plan lists saved projects and goals with statuses and descriptions. Loading/error handling clears decrypted rows consistently. This does not enable project editing, planning decisions, reminders, task completion, requests or autonomous behavior. Debug inspection now covers all seven physical tables, and seeding remains restricted to entirely empty personal storage.

Validation: debug/release builds, 18 JVM tests, lint and Detekt passed. Six new Android tests cover encrypted reopen/disk exclusion, updates/revision conflicts, deletion and association detachment, invalid references and SQL constraints, project-only/goal-only key loss, v4→v5 ciphertext preservation, and screen recreation/refresh. Existing earlier-version upgrades still run through the configured migration chain. All 58 Android tests passed on both project-owned API 26/34 emulators. A first run exposed schema-asset copy ordering on a clean build; instrumentation asset merging now depends on debug KSP export, and both complete suites passed with schema v5 included. The [GitHub verification run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37753678799) passed build checks and all 58 tests on API 26/36.

Historical remaining Phase 4 work, now implemented in [schema v6](PHASE_4_COMPLETION.md), was tracked in the database contract: profile/contact/relationship, reminder/focus, event/promise, routine, action/audit/session, recommendation/experiment and derived-data foundations, remaining task/schedule/memory links and broader validation. Their capability-specific execution belongs to later phases.


#### Source record: PHASE_4_INSPECTION_VERIFICATION.md

[Original record](PHASE_4_INSPECTION_VERIFICATION.md)

##### Phase 4 debug database inspection

This fifth database slice completes the first aggregate inspection interface requested in section 60 of the source build plan. It adds no tables or migrations; the saved database remains schema v4.

In a debug build, You offers **Refresh database inspection (debug)**. It reads actual SQLite counts for tasks, schedule_blocks, memories, task_dependencies and preferences, the stored schema version, foreign-key enforcement, foreign-key check and quick check. The result is a manual snapshot; refresh after mutations. It survives Activity recreation in its ViewModel, and is cleared while a refresh is running or if the query fails. The existing explicit synthetic seed control still refuses a nonempty database.

The inspector is compiled only from the debug source set. It uses fixed aggregate queries in one Room transaction off the main thread. It does not select personal fields, decrypt ciphertext, access/create a Keystore key, mutate saved records, expose arbitrary SQL, log diagnostic strings, export data or change backup/network permissions. It remains usable when encrypted record access fails, so a missing key cannot turn stored row counts into a misleading empty result. Failure copy is generic and never exposes exception text. Release builds retain their empty developer-tools implementation.

These are SQLite diagnostics, not a security verdict. [SQLite quick_check](https://www.sqlite.org/pragma.html#pragma_quick_check) does not check foreign-key errors, index/table consistency or uniqueness; the separate foreign-key check covers dangling references. Neither check verifies encryption authentication, key availability or application rules such as task graph cycle freedom. The screen states these limits. Manual checks may scan the database and hold a transaction; representative large-database timing remains open.

Validation on 2026-10-08: debug/release builds, 18 JVM tests, debug/release lint, Detekt and all 52 Android tests passed locally on the project-owned API 26/34 emulators. Inspection classes and button text are present in the debug APK and absent from the release APK. The [GitHub API 26/36 run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37751228777) also passed all checks and 52 tests. The preceding [CI recovery run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37749783812) passed all build checks and 49 Android tests on API 26/36. New tests verify counts across all five tables, no decryption or key replacement after key loss, detection of an injected dangling foreign key without repairing it, and manual screen refresh across Activity recreation and task deletion. All fixtures are synthetic and removed from owned test databases.

The remaining table/migration work is now covered by [schema v6](PHASE_4_COMPLETION.md); broader security/performance and device checks remain hardening gates. Export/import, autonomous actions and trained models remain unavailable.


#### Source record: PHASE_4_CI_RECOVERY.md

[Original record](PHASE_4_CI_RECOVERY.md)

##### Phase 4 API 26 CI recovery

The preference slice's [GitHub run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37714325812) passed build/static checks and API 36. API 26 ran 49 tests with four failures: two keyboard waits timed out, then two saved-record assertions encountered 21 memory fixtures whose cleanup had been skipped.

The keyboard Back test captures the unobscured viewport before typing, verifies that the keyboard reduces it, and waits for it to return after Back. Window insets and registered keyboard windows can outlive the visible keyboard; the tests no longer use them to infer visibility. An unavailable viewport fails explicitly. Search cleanup invokes the real Search keyboard action without asserting Compose's focus bit, which remains set on API 26. Search fixtures are removed in a nested finally block even if the keyboard action throws. The Back helper allows the system to consume one Back for IME focus, then requires the sheet to disappear and the source destination to remain selected. Only the expected wait timeout triggers the second Back; unrelated test errors propagate.

Navigation now clears Compose text focus before changing destination or opening Privacy/the request draft. Memory search has an explicit Search keyboard action that clears text focus; search remains local and reactive. The navigation regression types a memory search before opening Privacy and the draft, then verifies Back dismissal after recreation. No app permissions, dependencies, encryption, or schema changes are introduced. Back dismissal and fixture assertions remain enabled.

Validation: debug/release builds, Android test APK compilation, 18 JVM tests, debug/release lint, and Detekt passed. All 49 Android tests passed locally on API 26 and API 34. The [recovery run for bcf7c65](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37749783812) passed build/static checks and all 49 Android tests on both API 26 and API 36. No tests were disabled.


#### Source record: PHASE_4_COMPLETION.md

[Original record](PHASE_4_COMPLETION.md)

##### Phase 4 database completion

The seventh persistence slice completes the Phase 4 database baseline defined by the canonical roadmap. Physical Room schema v6 contains all 22 planned tables, exported schemas and explicit upgrades from versions 1–5. This is a persistence milestone; it does not enable command execution, scheduling, behavioral analysis or trained agents.

###### Gate coverage

| Requirement | Implemented evidence |
| --- | --- |
| Entities, DAOs and repository interfaces | Typed records and repositories for tasks, dependencies, projects/goals, preferences, memory, people/relationships/profile, reminders/focus, promises/routines, events, action runs/audits/sessions, recommendations/experiments and habits/derived insights |
| Personal-field protection | AES-GCM BLOB envelopes with Keystore keys and table/record/column authentication; personal JSON is bounded, validated and encrypted; populated writes require a readable existing key |
| Integrity | Foreign keys, indexes, unique identities, SQL metadata/state checks, repository reference validation and optimistic revisions; no REPLACE inserts or destructive fallback |
| Search | Temporary in-memory FTS4 with metadata filtering before bounded decryption, expiry filtering and visible 20/200 candidate limits; no persistent plaintext index |
| Atomic outcomes | Action, correlated event and audit saved in one transaction; duplicate or failed records roll back the complete outcome |
| Privacy deletion | Cleanup before foreign-key cascades; iterative traversal handles cyclic memory references, owned relationships/promises/dependencies/audits and source-event memories; unrelated records survive |
| History | Events/audits/sessions/insights have append-only ordinary APIs; explicit privacy deletion can redact linked history. Action payloads/receipts are removed while a nonpersonal, non-resumable idempotency tombstone remains |
| Migrations and diagnostics | Data-preserving v5→v6 and configured legacy chains; debug-only empty-storage synthetic seed and fixed aggregate inspection across all 22 tables |
| Screens | Today shows saved tasks/links/focus outcomes; Plan shows saved blocks/projects/goals/reminders/routines; You shows saved memory/preferences/profile/people; Timeline observes saved events, including recreation and safe error handling |

The profile consent column stays false. At this Phase 4 milestone, habit/derived-insight writes through public repositories were blocked pending the consent and inference phase. Phase 14 now adds a separate default-off local consent control and a consent-guarded on-demand reflection slice; see the [Phase 14 report](PHASE_14_VERIFICATION.md). An autonomy number, routine flag, persisted action or opaque JSON document is storage metadata and grants no execution authority. Successful outcomes require an appropriate verification category, receipt and finish time; payload/identity cannot be rewritten or terminal actions resumed. Android scheduling and verification are not installed.

###### Validation

Final validation passed on 2026-10-08: debug/release builds, 21 JVM tests, debug/release lint, Detekt and instrumented APK compilation. All 85 Android tests passed on both owned API 26/34 emulators and in GitHub CI on API 26/36, including encryption-failure rollback and transitive reference cleanup regressions. Release APK inspection confirms the debug inspector and control text are absent. The exported v6 schema contains 22 tables; updated local documentation links resolve and whitespace checks pass. The Phase 4 database baseline is complete.

Implementation commit: `bd232c25278926deb16e2bd5482933392388d92f`. The [passing CI run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37779790806) checks documentation follow-up `2872b15c0e8ffc4f828c52d6c35eb2ee830e55f7` with identical implementation code. All three jobs passed. Its [first CI run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37779222859) failed on API 36 during root Gradle dependency resolution, before app compilation or Android testing: published Kotlin/AGP transitive artifacts were reported unavailable from the configured repositories. The connector lacked job-rerun permission; pushing the verification note triggered the successful fresh run. No skipped test is counted as passed.

The final debug search fixture measured 20/200 candidates at 132/1,263 ms on API 26 and 130/1,462 ms on API 34. These are emulator measurements, with the expanded search remaining a reported performance limitation.

The checks cover encrypted reopen and disk-marker exclusion, stale updates/deletes, missing-key reads/writes, malformed/deep JSON, direct SQL constraints, append-only identities, outcome rollback, foreign-key detachment/cascades, chained migrations, saved-data screens and release exclusion of developer controls. Fixtures are synthetic and restricted to owned test databases/emulators.

###### Remaining capability gates

Phase 5 owns memory CRUD workflows, retrieval/ranking and retention policy. Phase 6 introduces typed request/proposal protocols and policy authority; Phase 7 implements evaluated deterministic language specialists. Phases 8–9 introduce Android action scheduling, verified outcomes and restart reconciliation. Phase 11 implements recurrence/planning decisions; Phase 14 adds explicit behavioral consent and inference.

Physical-device testing, process-death/reboot recovery, forensic/security review and representative performance/battery work remain Phase 17 hardening. Debug measurements of expanded 200-record searches can exceed one second and are not a production performance claim. Import/export stays unavailable pending a reviewed format and privacy workflow. Personal context remains local; no automatic training upload or model is added. See the [agent training plan](AGENT_TRAINING_PLAN.md).

Historical slice reports record their own schema/test counts. This report supersedes their statements about remaining Phase 4 table work.


## Phase 5 — Local memory engine

### Purpose and outcome

Users gained manual memory creation, editing, reviewed deletion, explicit/derived filters, structured retrieval, deterministic ranked search and explicit expiry cleanup. Personal text is decrypted only for bounded search candidates and indexed temporarily in RAM. Ranking weights explain ordering; confidence is stored provenance, not truth verification. Emulator measurements revealed that expanded search and the API 26 default search exceeded the production target.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_5_VERIFICATION.md

[Original record](PHASE_5_VERIFICATION.md)

##### Phase 5 memory engine verification

Status: complete memory-engine baseline on 2026-10-08; local and GitHub validation passed. The database remains physical Room schema v6. Phase 5 adds local memory workflows and retrieval without enabling commands, Android actions, background inference or model training.

###### Delivered behavior

You supports explicitly adding, editing and deleting memories. The editor previews content, type, importance and retention before Save; deletion shows the selected content in a scrollable review and requires confirmation. Immutable provenance prevents promoting a derived record through either the engine or ordinary repository updates. Derived records remain labelled and read-only, with separate explicit/derived filters in browsing and search. Existing entity/source links and confidence are preserved on manual edits.

The MemoryEngine exposes structured retrieval, exact linked-entity lookup and ephemeral working context. Empty query text means bounded structured retrieval without FTS matching; nonempty search still uses literal all-word FTS4 matches. Type, origin, entity and expiry predicates run before bounded decryption. Working records without expiry remain archived and are excluded from retrieval; newly saved working memories expire within 24 hours. No legacy records are silently rewritten.

Ranking is deterministic and explainable over the eligible bounded candidates:

| Component | Default weight | Definition |
| --- | --- | --- |
| Relevance | 0.30 | Mean of normalized query-word coverage and matching-word density; Unicode case/accent normalization |
| Importance | 0.25 | Stored importance in 0–1 |
| Recency | 0.20 | Exponential decay with a configurable 30-day half-life, evaluated against the caller's fixed time |
| Relationship | 0.15 | Exact stored reference matches the hard entity scope or explicitly supplied context; no inferred relationship or graph traversal |
| Confidence | 0.10 | Stored confidence in 0–1; this is provenance metadata, not verification of truth |

Weights must be finite and nonnegative with a positive finite sum; the score divides by that sum. Scores are returned alongside records and shown in search results. Ties use updated time then canonical ID. Text-free retrieval has zero lexical relevance. Candidate selection still starts with the 20 most recently updated eligible records, with explicit expansion to 200; ranking cannot recover excluded older candidates. Context is capped at 20 references. No learned ranker or persistent plaintext index is installed.

###### Retention and privacy

Retention choices preserve the current expiry, keep a memory until explicit deletion, or expire it after 24 hours/seven days. Working context is always capped at 24 hours. Expired items remain visibly archived and are excluded from retrieval. There is no scheduler, automatic purge, hidden cleanup or forensic erasure claim.

Review expired memories selects at most 50 IDs/revisions/expiry values without reading personal content or using a key. The confirmation rechecks the exact snapshot, not a fresh broad predicate. A changed or missing reviewed row aborts the whole transaction. An unrelated newly expired record outside that snapshot is left for another review. Linked-content privacy cleanup can remove related records or redact history, as explained in the confirmation. Cycles between reviewed records are handled safely. Key/encryption failures roll back deletion. More batches require another explicit review.

Unsaved edit text stays in RAM and is discarded on screen exit or Activity recreation. It is never written to SavedStateHandle, preferences or logs. Successful saves use the existing AES-GCM envelopes, authenticated field bindings and optimistic revision checks. Save failures discard editor content and show generic errors; concurrent changes require opening the fresh record again. Existing request submission remains disabled. Network permissions, backup exclusions and behavioral-consent boundaries are unchanged.

###### Validation

Local validation on 2026-10-08 passed debug/release builds, all 26 JVM tests, debug/release lint, Detekt and instrumented APK compilation. All 95 Android tests passed on owned API 26/34 emulators. Release APK inspection confirms debug inspector/control exclusion. Schema v6 is unchanged and updated local documentation links resolve. New tests cover normalized ranking, context/tie/clock behavior, encrypted manual CRUD, immutable derived provenance, filtered structured retrieval, working expiry, bounded metadata-only review, stale/key-loss rollback, cyclic retention cleanup, UI confirmation/filtering/recreation and concurrent edits.

The debug search fixture measured 20/200 candidates at 182/1,807 ms on API 26 and 138/1,308 ms on API 34. These are single emulator runs; the API 26 default and both expanded searches exceed the 150 ms production target. No representative-device latency pass is claimed.

A final UI follow-up makes long-memory deletion reviews scrollable. The three editor/confirmation tests passed again on local API 26/34, along with repeated build, JVM, lint and Detekt checks. The engine and schema are unchanged by that follow-up.

The [final Phase 5 CI run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37798814741) passed all three jobs, including all 95 Android tests on API 26/36, for `05a80aa8aee9ec21ebfaa80a6d24bc7002a5268e`. The [first implementation run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37797737889) also passed all checks for `f53b266e994933a0a1a3d7019c30db6729f7199b`. This completion update changes documentation only. The Phase 5 memory-engine baseline is complete.

###### Limits and next gates

The memory archive still observes saved rows for display; bounded candidate/decryption limits apply to retrieval, not all archive rendering. Large-archive pagination and representative device/security/performance checks remain hardening work. Expanded 200-candidate searches can exceed the production latency target. Synthetic ranking tests establish deterministic baseline behavior; they are not a real-user relevance or model-accuracy claim.

Phase 6 introduces the unified typed request/result pipeline and policy authority. Phase 7 adds deterministic language specialists with evaluated unknown/negation/ambiguity handling. Android executors, behavioral inference and learned models remain later gates. See the [agent training plan](AGENT_TRAINING_PLAN.md).


## Phase 6 — Typed agent protocol

### Purpose and outcome

The composer became a typed request/result pipeline, with validated identities, times, entities, capabilities, proposals and truthful receipts. Sealed action payloads prevent arbitrary classifier strings or opaque maps from dispatching effects. At this checkpoint the language baseline remained unsupported and no action executed. Later parser and executor phases reused these contracts.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_6_VERIFICATION.md

[Original record](PHASE_6_VERIFICATION.md)

##### Phase 6 agent protocol verification

Status: complete agent-protocol baseline on 2026-10-08; local and GitHub validation passed. Physical Room schema v6 and the encrypted storage baseline are unchanged.

###### Delivered behavior

The shared composer creates a typed AgentRequest from button or IME submission on any destination. RequestViewModel prevents duplicate submission while processing, clears submitted text from the restorable shell draft, and exposes a transient AgentResult. The shipped UnsupportedLanguage baseline reports: “Command understanding is not available yet. No action was taken.” It never guesses an intent or produces an action. Closing the sheet cancels processing and clears the response; editing a new draft clears the previous result. No microphone, model, conversation persistence or platform execution is enabled.

The domain protocol includes all 32 planned intents plus nonexecutable UNKNOWN/UNSUPPORTED; requests, typed extracted entities, fixed time/zone/context, capability snapshots, proposals, follow-up questions, explanations, results, receipts and events. Kotlin UUID/Instant/ZoneId values carry identity and time. Text and collections are bounded, confidence is finite in 0–1, durations/revisions are validated, UTF-16 offsets must exactly match original text without splitting surrogate pairs, and resolved local times must match a valid zone offset. DST overlap requires a selected matching instant; gaps cannot be represented as resolved time.

All 14 action types have sealed typed payloads. Task/memory/goal updates carry typed targets and revisions; preferences reuse the registered typed value rules. Reminder/alarm/calendar times retain local time and zone, and reminders specify exact/approximate precision. Dialer and messaging payloads expose handoff modes only; message destinations reject arbitrary schemes, URI query/body injection and malformed single destinations. Plan blocks carry explicit revisions and reminder proposal IDs require separate authorization. No opaque map/JSON or raw classifier string is dispatchable.

Classifier, extractor, context builder, specialist, policy, orchestrator and generic typed executor interfaces are replaceable. LocalAgentOrchestrator handles negated, unknown, below-0.90 confidence and unresolved inputs before suggestions, checks request correlation, routes suggestions through proposal policy, and produces no execution receipts. Below-0.90 confidence conservatively asks a follow-up. Exceptions become generic failures; coroutine cancellation propagates. The baseline context reports answer-only authority and no implemented agent capabilities even if a dormant profile stores higher autonomy.

ProposalPolicy derives minimum risk and capability requirements from the sealed action, disallows unsupported higher autonomy, and checks current revisions/capabilities. Proposals require confirmation, expire within five minutes, and defensively copy evidence/collections. Preview review rejects expiry, capability revocation or changed revisions. Receipts enforce verified success versus unverified pending/failure/unknown and handoff; dialer/message/maps receipts cannot claim success. Event metadata is typed; storage catalog names remain compatible.

###### Privacy and remaining gates

Submitted requests/results stay in RAM and are discarded on sheet dismissal/process loss. The existing bounded unsent draft may still be retained by Android instance-state restoration; it is cleared on submission. No request logging, telemetry, encryption/key change, new permission, automatic memory update or training upload is introduced. Manual Phase 5 memory controls remain their own explicit user editing flow.

Proposal review is not acceptance or execution authority. No action can execute in Phase 6. Phase 7 adds evaluated deterministic English rules and safe read adapters, including negation and ambiguity cases. Phase 8 must add immutable bound acceptance, reference/platform revalidation, durable idempotency, pending/reconciliation states, and atomic mutation/receipt/audit adapters. Phase 9 adds event encoding and persistence. The current typed models do not serialize themselves into the older encrypted storage JSON fields. No process-death/external-action recovery or language-accuracy claim is made in this phase.

###### Validation

Local debug/release builds, all 48 JVM tests, debug/release lint, Detekt and instrumented APK compilation passed. All 98 Android tests passed on owned API 26/34 emulators. Release APK inspection confirms no internet/dangerous permissions and excludes the debug inspector. Schema v6 and dependency versions are unchanged; updated documentation links resolve and git diff --check passes.

New JVM regressions cover catalog compatibility, original UTF-16 extraction, malformed values, DST gaps/overlaps, unsafe external payloads, minimum risk/confirmation/expiry, truthful receipts, request correlation, defensive collections, autonomy/capability/revision review, unknown/negated/unresolved requests, unsupported sources, failure sanitization and cancellation. Android regressions cover shared composer submission/restore and request duplicate/cancel/error handling, including a dependency delaying cancellation. No external-action, real-user language accuracy or physical-phone validation is claimed.

The [Phase 6 GitHub run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37803899355) passed all three jobs for implementation commit `19966fa9cd1ebb6415b408e2249f55fa84e4b661`. Completed emulator logs confirm 98 tests and BUILD SUCCESSFUL on both API 26 and API 36. This completion update changes documentation only. The Phase 6 baseline is complete; Phase 7 may now begin.


## Phase 7 — Deterministic language engine

### Purpose and outcome

Closed English rules enabled bounded task, memory and promise reads and extracted supported reminder/timer information. Unknown, negated, ambiguous or unresolved requests were handled conservatively. Fixed request time and zone made relative dates reproducible. The perfect development-corpus score is a regression result, not broad English accuracy, and follow-up answers remain fresh complete requests rather than merged conversation.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_7_VERIFICATION.md

[Original record](PHASE_7_VERIFICATION.md)

##### Phase 7 bounded English language verification

Status: complete bounded language baseline on 2026-10-09; local and GitHub API 26/36 validation passed. Phases 8–19 remain pending.

###### Delivered scope

The composer uses EnglishRules, LanguageSpecialist and a read-only AgentReadPort through the existing LocalAgentOrchestrator. The context advertises LOCAL_READ and answer-only authority. Stored autonomy cannot enable mutations. No executor, action acceptance, receipt, permission, schema migration, network access, request persistence or model is added.

Supported closed grammar:

| Intent | Example | Result |
| --- | --- | --- |
| CHECK_TASKS | What tasks do I have? / Show my tasks | Up to five open saved task titles |
| CHECK_MEMORY | What do you know about my DSP exam? / Search memory for DSP exam | Up to five matching explicit nonexpired saved memories |
| WHAT_DID_I_PROMISE | What did I promise Rahul? | Saved promises and recorded status for exactly one matching local person |
| CHECK_CALENDAR | When am I free tomorrow? | Access unavailable; free time remains unknown |
| CREATE_REMINDER | Remind me tomorrow at 8 pm to call Rahul | Resolved date/time/zone and explicit scheduling-unavailable result |
| CREATE_TASK | Add a task to study | Recognition only; creation unavailable |
| CREATE_TIMER | Set a timer for 5 minutes | Bounded seconds/minutes extraction; execution unavailable |

Rules accept case variation, optional “please”, outer whitespace and terminal punctuation. Memory matching still requires all search words and uses the existing Phase 5 ranking and candidate bounds. “My” before a query is optional syntax. Answers identify local saved records and label bounded excerpts or incomplete searches. Promise names match saved local display names without contacts access; zero or multiple people produce a follow-up. Promise status is quoted from storage, without inferring fulfillment. Read errors produce generic failures, never partial or falsely empty answers.

Reminder date resolution accepts today, tomorrow or ISO YYYY-MM-DD. It uses the request timestamp and one captured device zone. Time requires AM/PM or a two-digit 24-hour HH:mm form. Invalid dates/times, past instants, absent date/title, bare hours, DST gaps and DST overlaps remain unresolved and produce follow-up questions. A reminder title containing “call Rahul” is a reminder, never a call or contact lookup. Timers accept 1 second through 1 day. Entity spans preserve original UTF-16 text and emoji.

Negation conservatively rejects the entire request before reads. Quoted, hypothetical, compound read requests, unsupported wording and languages receive clarification. The negation rule intentionally also rejects titles containing words such as “not” or “stop”. Rule match score 1.0 is a deterministic match marker, not calibrated statistical confidence.

###### Evaluation and limitations

The versioned [45-case synthetic development corpus](evaluation/english-rules-v1.txt) specifies expected intents and pipeline outcomes at a fixed clock and zone. It has five cases for each of seven recognized intents, five unknown cases and five negated cases. It was authored with this implementation and is a regression corpus, not an independent held-out natural-language evaluation. No blind test accuracy, broad English coverage, confidence calibration, representative physical-phone latency, memory/battery measurements or Hinglish support is claimed. The dataset contract's larger independently reviewed family-split dataset remains a gate for broader language enablement and learned models.

JVM regressions separately cover exact UTF-16 spans, midnight/noon, zone date rollover, invalid/past times, DST gaps/overlaps, unsupported input sources, negation before reads, duration bounds, generic failures and cancellation. Android regressions exercise real encrypted local reads, explicit/derived/expiry separation, open task filtering, bounded output, zero/duplicate person resolution, no mutation or receipt records, missing-key failure and the shared composer's clarification/restoration behavior.

Follow-up answers are fresh complete requests; there is no persisted conversation or automatic merging of a reply with earlier text. Unsupported catalog intents, reordering, misspellings, relative durations for reminders, contact resolution, memory entity inference and platform effects remain unavailable. Phase 8 must implement binding acceptance, fresh policy/capability checks, durable idempotency, lifecycle, verification and receipt/audit transactions. The integrated Phase 8–9 reminder gate remains unchanged.

###### Validation

Debug/release builds, all 58 JVM tests, debug/release lint and Detekt passed. All 45 corpus cases matched their expected intent and safe pipeline outcome: per-label precision/recall and macro F1 are 1.0 on this development corpus only (seven recognized intent labels plus UNKNOWN). Negated cases reject before reads. This is not an independent accuracy estimate. Release APK inspection shows no internet or dangerous permissions. Schema v6 and dependency declarations are unchanged; documentation links resolve and git diff --check passes. All 102 Android tests passed on both owned API 26/34 emulators, with zero failures, errors or skips. The initial offline device invocation failed because the UTP host additional-output plugin was not cached; rerunning with dependency resolution enabled fetched it and ran the full suites. GitHub API 26/36 also passed in the run recorded below. Physical-phone and broad language validation remain later gates.

###### Branch consolidation

Before implementation, main was fast-forwarded through the complete existing history of codex/database-persistence. codex/navigation-shell, codex/ci-recovery and metis-phase-1 were already ancestors, so they required no additional conflict resolution. The uncommitted Phase 6 completion notes were preserved in commit 931168c. Branch references are retained; no history was rewritten or deleted.

The [Phase 7 GitHub run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37870891856) passed the build/static job and both API 26/36 runtime jobs for implementation commit 80208a8916466c0cf6cf34ca399bdd5a03bb220d. The bounded baseline is complete; Phase 8 may begin.


## Phase 8 — Confirmed action engine and reminders

### Purpose and outcome

Phase 8 grew from task creation to completion, rename, priority change, postponement and guarded permanent deletion, then approximate reminders. Canonical proposal binding, current policy, revisions, expiry, durable pending reservations, transactional local verification and replay protection establish the execution boundary. Undo rechecks the affected task and dependencies; permanent deletion has no undo. The original reports below are historical slices and still mention then-pending work; the current roadmap/core-feature record closes the larger local baseline.

The completed reminder path reserves encrypted reminder/action data before scheduling unique WorkManager work containing only a UUID. Registration is read back and recorded independently from later notification posting. Retry reconciles existing work; cancellation is verified. Notification permission/channel denial is visible. A durable delivery claim prevents blind reposting after uncertain interruption. Resume recovery and privacy scrubbing preserve honest outcomes. WorkManager provides approximate timing rather than an exact-alarm guarantee. The Phase 8 final local checkpoint records 66 JVM tests and 143 Android tests on each API 26/34 device; Phase 9 supplies the integrated Timeline milestone.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_8_VERIFICATION.md

[Original record](PHASE_8_VERIFICATION.md)

##### Phase 8 task-action slice verification

Status: first slice implemented and locally verified on 2026-10-09. Phase 8 remains in progress. This slice delivers plain local task creation, durable accepted-action records and guarded undo; the Android reminder milestone and other task/external mutations are pending.

###### User flow

“Add a task to study” produces a proposal showing the exact title, local-write risk, reason, lack of deadline/reminder and five-minute expiry. Nothing is saved until Accept. Cancel, draft editing, a new request and sheet dismissal invalidate unaccepted proposals. ConfirmedTaskAgent binds acceptance to the canonical immutable proposal object; a copied/forged or superseded proposal cannot execute. The composer disables edits and dismissal while an accepted mutation completes. No higher autonomy can skip acceptance.

After acceptance, LocalAcceptedTaskStore persists an encrypted PENDING action and confirmation audit. It then rechecks current policy/expiry and commits the task, verified SUCCEEDED receipt and allow audit in one Room transaction. Task IDs derive deterministically from the action identity; the unique stored idempotency key binds request/proposal/payload/risk/reason/expiry. Replaying a completed accepted action returns the original receipt without creating another task. A success receipt means a task row was read back and verified; it does not claim any platform effect or notification delivery.

Only plain TaskMutation.Create is supported, with no deadline, priority, project, goal or reminder. Titles are bounded to 500 UTF-16 units and exclude control characters. Other typed actions and unsupported task mutations cannot dispatch. The original read-only Phase 7 orchestrator remains available for its regression corpus; the production composer uses the confirmed-task session and delegates read/reminder/timer responses to the same LanguageSpecialist.

###### Policy, recovery and undo

Autonomy is read from current active local profile metadata inside action transactions. No profile defaults to suggestion mode for this explicitly confirmed local feature. Answer-only or conflicting profiles deny execution. Higher stored autonomy is capped at suggestion mode. Only LOCAL_READ/LOCAL_WRITE implementation capabilities are advertised; no Android permission or new schema/dependency is introduced.

Accepted task actions in You survive model/database recreation. A failed mutation/receipt/audit transaction rolls back all task effects, leaving the previously committed PENDING reservation. Explicit Retry revalidates the persisted, versioned plain-task payload, original acceptance deadline and current policy. Expired/revoked pending work becomes an unverified failed result; it does not execute. Cancel pending task records cancellation before any mutation. There is no automatic startup/background execution. A key/storage failure remains visible without resetting the database or creating a replacement key.

Undo requires a matching stored receipt and unchanged task revision, plus no newly linked schedule, memory, dependency, reminder, promise, focus, event, recommendation, insight or other action record. It removes only the newly created task, verifies absence and saves a separate undo action/audit atomically. Edits and links block undo. Existing privacy cleanup scrubs the original action's personal payload, receipt and evidence; it retains the idempotency tombstone and prevents replay from recreating deleted personal data. Undo replay is harmless. Other task deletions and cascade cleanup are not enabled through this flow.

Once explicitly accepted, mutation storage runs to a durable outcome even if a coroutine is cancelled. If the process dies before the completion transaction commits, the accepted reservation is reviewable in You; it is never reported as verified success. No claim is made that host tests reproduce every physical process-kill timing. Android effects need a separate reconciliation protocol in the reminder slice.

###### Verification

Debug/release builds, debug/release lint and Detekt passed. All 63 JVM tests and all 110 Android tests on each of local API 26 and API 34 passed with no failures or skips. Regression coverage includes canonical proposal binding, duplicate/late acceptance, cancellation, policy revocation, expiry, database reopen, concurrent replay, receipt-write rollback/retry, pending cancellation, missing-key safety, changed/linked-task undo denial, privacy tombstones and visible composer review/accept/cancel/undo. Release APK permissions remain limited to the app's own dynamic-receiver permission. [GitHub verification](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37874762675) passed the build/static and API 26/36 jobs for commit 60b2e424b4b63825c4ac0185bd792de254f61448.

The initial Android run caught an invalid audit policy tag and an incorrect test button label. Reservation transactions rolled back before task creation. The tag now follows the stored uppercase identifier contract, and the UI test uses the actual string resource; the full corrected run passed. Static-analysis findings were resolved before the final run.

###### Remaining Phase 8 gates

- The [next completion slice](PHASE_8_TASK_COMPLETION.md) adds exact-title target/revision resolution and confirmed status completion. Task update/delete/postpone and broader target resolution remain pending.
- Android reminder scheduling, permission/notification denial, precision disclosure, schedule verification, retry/cancel/undo, restart/reboot reconciliation and delivery outcome handling.
- Other typed executor capabilities remain unavailable until their own policy, permission and verified-outcome gates pass.
- Phase 9 event encoding/persistence and Timeline integration, followed by the full Phase 8–9 reminder acceptance scenario. No new timeline event is emitted by this slice.

See [implemented core features](CORE_FEATURES.md) for the app-wide status.


#### Source record: PHASE_8_TASK_COMPLETION.md

[Original record](PHASE_8_TASK_COMPLETION.md)

##### Phase 8 confirmed task completion

Status: implemented and locally verified on 2026-10-09. Phase 8 remains in progress, with reminder scheduling and other mutations pending. GitHub verification for this slice is pending.

###### Working flow

`complete task: Study!` looks for exactly one OPEN task with that title, ignoring letter case but preserving title punctuation. Optional “please” and “the” are accepted. Substrings, fuzzy selection, multiple matching open tasks and recurring tasks do not produce an executable proposal. Negated input is rejected before storage reads. Supported action titles are bounded to 500 UTF-16 units without control characters. Other command forms require a fresh supported request.

The proposal displays the selected title, status change, risk, reason and five-minute acceptance deadline. It binds the selected task ID and revision. Nothing changes before explicit acceptance. Acceptance is bound to the canonical session proposal and invalidated by cancellation, editing, dismissal or a new request, using the same session mechanism as task creation.

Completion changes only status to COMPLETED and sets completedAt. Notes, priority, deadlines, project/goal links, schedules, reminders and memories stay as they are. Recurrence advancement, reminder cancellation, schedule completion and Phase 9 events are not implied or executed.

###### Durable safety and recovery

Acceptance rechecks current policy, expiry, the selected task revision, OPEN status, absence of recurrence and completion of all prerequisites. The encrypted pending action is linked to the existing task so privacy deletion also scrubs pending personal content. Retry refreshes policy, expiry, title, revision, status, recurrence and prerequisite state; stale or revoked work becomes failed without changing the task. Pending cancellation is explicit. No startup execution is added.

The status update, read-back verification, success receipt and allow audit commit in one Room transaction after the durable pending reservation. A receipt-write failure rolls back the task update and leaves pending work for review. The persisted idempotency key prevents duplicate status changes and returns the original historical receipt after replay. Success describes the verified local action, not notification delivery or the current status after a later edit/undo.

Undo reopens only the unchanged completed task at the receipt revision and clears completedAt. It refuses to reopen a prerequisite with completed dependent tasks. The status restoration, verification, separate linked undo action and audit commit atomically. Undo replay does not increment the revision again. History in You distinguishes creation from completion and marks a completed undo without offering it again. Task privacy deletion scrubs completion and undo records while preserving their idempotency tombstones. Creation undo retains its stronger no-linked-record deletion guard.

###### Verification

Debug/release builds, debug/release lint and Detekt passed, together with all 66 JVM tests. All 118 Android tests passed on each of local API 26 and API 34 with zero failures, errors or skips. Release APK permissions remain limited to the app's own dynamic-receiver permission; schema v6, migrations and dependencies are unchanged. Documentation links and whitespace checks passed.

Coverage includes exact-title punctuation and UTF-16 extraction, canonical acceptance, negation, unresolved context and answer-only policy, ambiguity, recurring tasks, concurrent replay, field preservation, encryption, stale revisions, unfinished prerequisites, rollback/retry, expiry, cancellation, policy revocation, pending task deletion, database reopen, repeated undo, edited task conflicts and completed dependent tasks. A real-app composer test reviews the title, accepts completion and verifies undo.

The original 45-case Phase 7 corpus remains a regression set for its original seven intents; this additional completion grammar is tested separately. No broader language-accuracy claim is made.

Verification recovery: an early title-extraction regression caught trailing outer whitespace; the non-greedy capture now excludes it while retaining punctuation. The first device invocation found no connected emulators, and a later interrupted run left partial reports and synthetic fixtures. After restart, those leftovers caused three existing API 34 screen tests to fail on duplicate records/nonempty counts. The test app was installed and its synthetic data reset successfully on each owned emulator before the final full rerun. Partial/interrupted results are excluded from passing counts.

See [task creation verification](PHASE_8_VERIFICATION.md), [core features](CORE_FEATURES.md) and [roadmap](ROADMAP.md).


## Phase 9 — Event engine and Timeline

### Purpose and outcome

Normalized task/reminder outcomes became durable history, correlated with request/action IDs and policy audits. Event writes share the local outcome transaction, so encryption failure cannot leave an apparently successful mutation without its event. Reminder registration, posting and uncertain/denied delivery remain distinct. Timeline exposes verification and explanations, while privacy deletion redacts personal history without removing replay tombstones.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_9_VERIFICATION.md

[Original record](PHASE_9_VERIFICATION.md)

##### Phase 9 event engine

Complete baseline, implemented and locally verified on 2026-10-09.

###### Persisted outcomes

Supported confirmed task creation, completion, rename, priority, postpone, deletion and undo now append normalized events. Reminder registration, cancellation, verified notification posting and denied/uncertain delivery produce separate events. The event writer runs inside the action-run save transaction, so an event encryption/storage failure rolls back the local mutation and receipt while preserving the earlier pending reservation for reviewed retry. The surrounding executor also commits its policy audit in that transaction.

Events use the existing append-only schema v6 table and encrypted version-1 metadata containing a readable result, verification, historical status, operation and optional title/error code. Stable UUIDs derived from action ID and outcome status prevent duplicate recording during acceptance/retry/worker replay. Unknown outcomes are unverified; a later verified outcome, if supported, is a separate historical event. Pending reservations and unaccepted, dismissed, negated or ambiguous proposals create no success events. Cancellation of accepted pending work uses ACTION_FAILED with explicit CANCELLED metadata because the frozen catalog has no generic cancellation event.

Every generated event correlates to its request and action, whose policy audit remains independently persisted. Entity ownership follows the correlated action rather than a second direct entity link: generated history does not introduce an artificial linked-record blocker for task undo/deletion. Privacy deletion traverses those action links and clears encrypted event details and audit content while retaining generic historical types and idempotency tombstones. Deletion outcomes contain no deleted title. Existing pre-Phase-9 receipts are not backfilled or re-executed.

###### Reminder milestone

REMINDER_CREATED means verified approximate WorkManager registration. REMINDER_TRIGGERED requires an active local notification readback and never claims the notification was read or a call was made. Revoked notifications and interrupted/unverifiable posts produce REMINDER_DELIVERY_FAILED with UNVERIFIED metadata. Resume reconciliation now records a separate unavailable-delivery child outcome for a missing/denied registration; repeated reconciliation cannot duplicate it or replace the original successful registration receipt.

WorkManager dispatch remains a platform side effect outside atomic guarantees: a durable claim precedes posting, and interrupted uncertain posts are never blindly repeated. Existing Phase 8 permission, cancellation, expiry, replay and restart checks remain required.

###### Timeline

Timeline observes persisted events and correlated audits, newest first with stable ID ordering for timestamp ties. Rows show type, optional title, local date/time with zone, readable outcome and verification. Expandable details show source, request/action correlation and policy decisions/reasons. Loading, empty, read-error/retry and redacted states are explicit; personal display rows are discarded on read failures. Fifty rows render initially, with a Show more history control. Reads currently load the existing repository's full history; large-history storage pagination and performance belong to hardening.

###### Verification

Added instrumentation checks for event-write rollback/retry, encrypted metadata, request/action/audit correlation, database reopen, privacy redaction, task mutation/undo/deletion events, and absence of events for unaccepted/negated input. Existing reminder tests now verify separate registration/posting/cancellation/failure events, single posting under replay, reconciliation idempotency and deletion redaction. The real composer reminder screen test checks Timeline registration, cancellation and expanded policy reasons.

Final debug/release builds, debug/release lint, Detekt and all 67 JVM tests passed. The full Android regression run passed 147 executed tests on each local API 26 and API 34 emulator, with zero failures/errors. Each device also reported three existing explicitly ignored host-only tests: notification app-op mutation and the two restart/reboot stages. These skips are not passing checks, and those host procedures were not rerun in Phase 9. Notification denial and database reopen are covered by the executed store tests.

After that run, the empty-state resource reference was corrected and a dark-theme/double-font-size populated Timeline test was added. The final three-test UI run passed on each API 26/34 device with no skips: large-font navigation, populated Timeline outcome/audit expansion and touch-target accessibility, and the real reminder composer/Timeline flow. The new test uses the existing edge-to-edge shell and scrollable audit controls. The full regression reports are retained locally under `.android-local/phase9-full-reports/`; the latest focused reports are in the standard Gradle connected-test directory. Markdown links and whitespace checks passed. Schema v6, dependencies and permissions are unchanged; no network or telemetry was introduced.

Verification caught and resolved initial Detekt complexity/constant findings and the final unused empty-state resource. No GitHub run, physical-device review, manual screenshot review, or production/performance claim is made here. Decision scoring and planning remain Phase 10 onward.


## Phase 10 — Explainable decision engine

### Purpose and outcome

Explicit next-task questions now rank eligible saved tasks and persist the winning explanation. The pure scorer uses deadline urgency, priority, active goals, overdue status, explicit focus duration and an effort penalty. It excludes blocked/inactive/unsupported tasks and unknown-duration tasks when a budget is supplied. Stored explanation history records the snapshot rather than asserting current truth or executing the recommendation.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_10_VERIFICATION.md

[Original record](PHASE_10_VERIFICATION.md)

##### Phase 10 decision engine

Complete baseline locally on 2026-10-09; final build, static checks and API 26/34 regression passed.

###### Working flow

The shared composer supports `What should I do?`, `What should I do now?`, `What should I do next?`, and `What should I do with 30 minutes?` (1–1,440 minutes). It returns one saved-task suggestion, its recorded deadline/zone, priority, estimated effort, weighted score contributions, input limitations and snapshot time. Invalid durations need clarification; unknown or negated requests do not generate a recommendation. A recommendation request invalidates any outstanding mutation proposal. It never starts, completes, edits or schedules a task and has no Accept action. Answer-only users can request this advisory answer.

The query reads tasks, prerequisites, goals, projects and explicit preferences from one Room transaction snapshot. Only open, nonrecurring tasks with active linked goals/projects and completed prerequisites are eligible. Missing prerequisite records also block eligibility. With an explicit duration, the task must have a known estimate that fits; unknown estimates are not treated as fitting. Without a duration, the response states that available time is unknown. The saved focus-block preference never proves free time.

###### Scoring policy: TASK_DECISION_V1

Every suggested winner stores the exact contribution values and total in encrypted `score_components_json`. These fixed rules are not calibrated probabilities or trained weights.

| Component | Contribution |
| --- | --- |
| Deadline | 0.35 × urgency: due now/past = 1; future under 24 hours = 0.9; under 72 hours = 0.6; under 7 days = 0.3; later = 0.1; no deadline = 0 |
| Priority / recorded importance | 0.30 × task priority / 3 |
| Active goal | 0.15 × (goal priority + 1) / 4; unlinked = 0 |
| Overdue | 0.10 if strictly before the snapshot time; otherwise 0 |
| Explicit focus preference | 0.10 when a known task estimate fits the saved focus-block duration; otherwise 0 |
| Effort penalty | Subtract 0.10 × min(estimated seconds / 14,400, 1); unknown estimate uses a fixed 0.05 penalty |

The total is clamped to 0–1. Equal scores use deadline, creation time and UUID in that order, making selection independent of database iteration order. Calendar availability, behavioral success and context switching are explicitly unknown and contribute nothing; their stored components are JSON null. Day-start and week-start preferences do not establish availability and are unused. There is no invented separate task-importance signal. The app does not infer behavior, observe other apps or train a model.

More than 1,000 saved task rows causes an explicit unsupported response rather than a partial ranking. No eligible candidate produces an honest empty answer without a fabricated recommendation or success event. Candidate reads are local and exhaustive within this baseline; broader scale/performance optimization remains hardening work.

###### Persistence, replay and privacy

The winner, versioned components, reason, request/task-revision evidence, scored-source IDs, generated/expiry timestamps and correlated RECOMMENDATION_SHOWN event commit atomically. Recommendation content remains Keystore encrypted in existing schema v6. Raw request text and task notes are not copied into the recommendation. SHOWN means a suggestion was generated for an explicit answer, not proof that a person read it, accepted it or completed work. VERIFIED_LOCAL on its event verifies local persistence only; it grants no execution authority.

The snapshot lifetime is five minutes and task changes can invalidate it sooner. History in You shows up to ten recent generated/expiry timestamps and expandable saved explanations, clearly labeled historical rather than current task status. Old recommendations are never automatically executed or treated as a live plan. Repeating a request UUID that already generated a stored suggestion, including after restart or privacy redaction, asks for a fresh request instead of presenting a stale recommendation or recording another event. A new request UUID evaluates current inputs. Empty and failed evaluations have no stored recommendation and may be evaluated again.

Generated recommendations own personal content through their encrypted source evidence, including the selected task ID; they do not introduce a direct task-link blocker for guarded creation undo or confirmed deletion. Task deletion removes its source-owned recommendation and redacts the derived event. Deleting a scored goal, project or focus preference also removes dependent recommendations and redacts their event metadata. Existing event tombstones retain only generic history and request IDs. Key/read/write failures become visible failure answers; no empty-result fallback, key replacement, upload, new permission, dependency or physical schema migration was introduced.

###### Verification

Ten JVM scenarios cover urgency/priority boundaries, explicit budget and unknown estimates, completed/missing/cancelled prerequisites, task lifecycle and recurrence, active goals/projects, explicit focus preference, deterministic ties, score bounds and duration grammar/negation. Nine device tests cover encrypted components, exact score reconstruction, no task/action/reminder mutation, answer-only behavior, concurrent request replay, database reopen, stale-request handling, atomic event-write rollback/retry, empty/ineligible answers, task/goal/project/preference deletion/redaction, guarded creation undo and confirmed deletion after advice, proposal invalidation and missing-key safety.

The real-app composer tests check a fitting recommendation, stored history/explanation, Timeline correlation and an empty answer. A dark-theme/double-font-size test checks explanation expansion/collapse and touch-target reachability in the existing edge-to-edge shell.

Final debug/release builds, debug/release lint, Detekt and all 77 JVM tests passed. The final full Android regression passed 160 executed tests on each local API 26 and API 34 emulator, with zero failures/errors. Each device also reported three existing explicitly ignored host-only tests: notification app-op mutation and the two restart/reboot stages. These skips are not passing checks, and those host procedures were not rerun in Phase 10. The final run includes the source-ownership fix and both guarded undo/deletion regression cases.

Verification command: `./gradlew.bat --offline --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:connectedDebugAndroidTest`. Connected XML reports are in `app/build/outputs/androidTest-results/connected/debug/`; JVM XML reports are in `app/build/test-results/testDebugUnitTest/`. Markdown links and whitespace checks passed. Schema v6, dependencies and permissions are unchanged.

No GitHub CI, physical-device, manual screenshot or measured latency/battery claim is made at this stage. Planning and availability calculations remain Phase 11.


## Phase 11 — Deadline-first planner

### Purpose and outcome

Day/evening requests now calculate free gaps around accepted/completed saved blocks and place whole estimated tasks before deadlines. Review exposes assumptions, conflicts and unplaced work. Acceptance fingerprints the contributing records and rechecks authority before atomic persistence. Reminder proposals require separate acceptance. External calendar availability, weighted optimization and plan undo remain unavailable.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_11_VERIFICATION.md

[Original record](PHASE_11_VERIFICATION.md)

##### Phase 11 planner

Baseline implementation and local verification status are recorded here after the final regression run.

###### Planner behavior

The Plan destination can open an editable `Plan my day` or `Plan my evening` request, and the shared composer accepts `Plan my day tomorrow` or a user-entered local window such as `Plan my day tomorrow from 09:00 to 17:00`. Day defaults to the explicit day-start preference, or 08:00; evening defaults to 18:00; both end at 22:00. An implicit same-day window begins at the next five-minute boundary. Default windows are labeled as assumptions in the review. Only one local date is supported. Invalid, past, overnight or DST-gap/overlap boundaries ask for clarification.

The local planner reads tasks, prerequisites, goals, projects, explicit day-start preference, saved schedules and enabled routines. Accepted/completed saved blocks consume their overlapping interval; proposed/cancelled blocks do not. Existing overlapping commitment pairs are reported. Open, nonrecurring tasks with known whole-task estimates and completed prerequisites are ordered earliest-deadline-first, then priority, creation time and UUID. A task is placed only when its full estimated duration fits a free gap and ends by its deadline. Unknown durations, incomplete/missing prerequisites, inactive links, booked tasks, missed deadlines, enabled routines and work beyond 16 proposed blocks are not scheduled. Nothing is split and schedule acceptance does not mark tasks complete.

###### Acceptance and reminders

Generating a proposal saves no plan blocks. An explicit review and Accept saves encrypted schedule-block text plus a local action run, policy audit and `PLAN_ACCEPTED` Timeline outcome in one Room transaction. Acceptance compares a deterministic snapshot of all source inputs, refreshes current profile authority/capabilities, rechecks the canonical displayed proposal and verifies every persisted block before committing. Concurrent proposals over changed inputs go stale. Replayed accepted requests return their existing receipt; a new request evaluates current inputs. The plan receipt and review state that no reminder was authorized.

Plan displays a separate optional reminder-review control for each future accepted block. It rounds the block start up to a minute, presents the existing approximate reminder proposal and requires its own Accept; users can undo/cancel that reminder through the existing reminder flow. Calendar access and external commitments are not read. User-entered windows confirm only the input the user provided; routine expansion, quiet hours, energy and availability outside saved commitments are unavailable. Enabled routines cause an explicit no-plan response.

Generated block task provenance is held in encrypted block evidence instead of a direct schedule-to-task foreign link. This lets privacy deletion remove generated blocks and redact their accepted-plan action/event/audit trail without making those blocks an unrelated-task deletion blocker. Deleting a generated block also privacy-scrubs the plan that owns it. No schema migration or new permission is introduced.

###### Verification

###### Final verification — 2026-10-09

Phase 11 is complete as a bounded local baseline. Debug and release APK assembly, debug/release lint, Detekt and JVM tests passed. The JVM suite ran 88 tests with no failures. Connected Android regression ran 174 tests per emulator on API 26 and API 34: 171 executed successfully and 3 were skipped on each device. The skipped checks depend on host notification app-op or host process-restart behavior and are not reported as passing.

Commands:

```powershell
.\gradlew.bat --offline --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt
.\gradlew.bat --offline --no-daemon :app:connectedDebugAndroidTest
```

The test XML reports are archived under the ignored `.android-local/phase11-full-reports/` directory. No physical calendar provider, GitHub CI, device screenshots, measured latency or battery result is claimed. Planner V1 deliberately uses earliest deadline first; weighted planning, conflict resolution with external calendar and optimization remain future work. No schema migration, dependency or Android permission was added.


## Phase 12 — Opt-in proactive system

### Purpose and outcome

Default-off WorkManager reviews inspect approaching/missed deadlines, due promises, repeated postponements and overlaps among accepted saved blocks. Deterministic priority selects generic private notifications. Two-per-day, four-hour spacing and same-item suppression bound interruption. These checks do not execute tasks or infer behavior, and approximate work cannot guarantee urgent delivery.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_12_VERIFICATION.md

[Original record](PHASE_12_VERIFICATION.md)

##### Phase 12 — proactive system

Phase 12 is complete as a bounded local baseline. It delivers opt-in checks over saved records with priority selection and notification suppression.

###### Behavior

- The Proactive updates switch on You defaults off. Enabling it schedules an immediate review and a six-hour periodic WorkManager review. Turning it off cancels both jobs and dismisses the current proactive notice.
- Open tasks due within 24 hours or already past due are candidates. Deadlines are low priority at 12–24 hours, medium at 2–12 hours, and urgent under two hours. Missed tasks are important. Completed and cancelled tasks are excluded.
- An open promise with a due time within the next 24 hours is a candidate. It is urgent when due within two hours and important otherwise. Fulfilled and cancelled promises are excluded.
- Three successful postponements of the same still-open task within 30 days create an important candidate. Postponement history comes from verified local task actions; other edits do not count.
- Overlapping accepted saved schedule blocks that intersect the current or next 24-hour period create an important candidate. Proposed and cancelled blocks are excluded.
- Candidate selection is deterministic by priority, trigger kind and opaque record identity. The selected notification uses a matching low, standard or urgent Android channel. Android channel settings can lower or disable a channel.
- Notifications use generic text, private lock-screen visibility and a generic public version. The limiter allows at most two notifications per rolling 24 hours, keeps at least four hours between notices, and suppresses the same item for 24 hours.
- Consent and limiter state use app-private preferences. The ledger stores hashed identities and timestamps only. Notification content stays out of preferences; tasks, schedules and promises are read through encrypted local storage.
- No database migration, new permission, network access, behavioral inference or automatic action was added. Android notification permission remains required for delivery. WorkManager timing is approximate and does not guarantee urgent delivery.

###### Verification — 2026-10-09

- Debug and release APK assembly passed.
- Debug and release lint, Detekt and all 94 JVM tests passed.
- The full connected Android regression passed on API 26 and API 34. Gradle started 175 tests per device; three pre-existing host-dependent reminder checks were skipped on each device, with no failures.
- The focused ProactiveScreenTest passed both tests on API 26 and API 34. It confirms the switch starts disabled, enabling schedules periodic work, disabling cancels it, notification text excludes task titles, and repeated checks of the same item are suppressed.
- The full regression also exercised the existing task, planner, reminder, privacy and navigation flows. No physical-device or production rollout check is claimed.

Commands:

```powershell
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebug :app:assembleRelease
.\gradlew.bat --offline --no-daemon :app:connectedDebugAndroidTest
.\gradlew.bat --offline --no-daemon :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.metis.agent.ProactiveScreenTest'
```


## Phase 13 — Voice input

### Purpose and outcome

User-started Android speech recognition feeds an editable transcript into the existing request path. Disclosure precedes microphone permission; on-device recognition is preferred where available, and general recognition requires a separate default-off online fallback setting. Cancel restores the prior draft. A later phone follow-up added an explicit opted-in Retry online action after no transcript; installation/launch succeeded, but spoken transcription still needs retesting.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_13_VERIFICATION.md

[Original record](PHASE_13_VERIFICATION.md)

##### Phase 13 — voice input

Phase 13 is complete as a bounded voice-input baseline. Speech enters the same `AgentRequest` path as typed input and remains under the user's explicit control.

###### Behavior and privacy

- Tapping the microphone first explains the microphone purpose and whether online fallback is enabled. Only after the user continues does METIS request `RECORD_AUDIO`. Denial leaves typed requests available.
- When Android reports an on-device recognizer as available (API 31+), METIS uses it. If it is unavailable, speech recognition remains unavailable by default. The separate **Allow online speech fallback** setting in You is off by default and is required before METIS may use the general Android recognition service. The implementation does not treat Android's offline-preference hint as a guarantee.
- Listening is user initiated and shows partial text. Stop finalizes recognition; Cancel stops it and restores the draft that existed before voice input. Recognition failures and missing local recognition capability are shown in the composer.
- The transcript remains editable. It is submitted as `InputSource.VOICE` through the same request, parser and result pipeline as text only when the user presses Send. Voice does not create a separate agent or bypass proposal review.
- If on-device recognition returns no transcript and online fallback is enabled, the composer offers a separate **Retry online** button. Online recognition starts only after that tap; when fallback is disabled, no online retry is offered.
- METIS does not persist raw microphone audio. With online fallback enabled and needed, Android may send speech audio to its recognition provider. METIS retains only the editable transcript in the temporary draft, which is discarded with the composer.
- The manifest adds `RECORD_AUDIO` and recognition-service package visibility only. No Internet permission, database migration, audio file, telemetry or background recording was added.

###### Verification — 2026-10-09

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

###### Device follow-up — 2026-10-10

After a connected OPPO CPH2691 on Android 16 repeatedly returned no transcript, the phone was checked: `RECORD_AUDIO` was granted and Android reported speech recognition services. The voice path now offers a separate online retry after an on-device no-result only when the user has enabled online fallback. `:app:assembleDebug` passed and the updated APK installed and launched on the connected phone. Live speech transcription still needs a spoken-device retest.


## Phase 14 — Habits and aggregate insights

### Purpose and outcome

Default-off behavioral consent enables user-triggered daily/weekly reflection over event types and timestamps. The analyzer avoids task text, notes, memory, audio and request contents. Repeated weekday patterns require three observations on three distinct dates in 28 days. Encrypted aggregates retain method/provenance, and consent withdrawal removes derived records. This bounded reflection engine does not alter recommendations or autonomously learn routines.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_14_VERIFICATION.md

[Original record](PHASE_14_VERIFICATION.md)

##### Phase 14 — habits and insights

Status: complete bounded baseline on 2026-10-10.

###### Behavior and privacy

- You provides a separate, default-off local behavioral-analysis consent switch. Withdrawing consent disables analysis first, then removes saved habits and Phase 14 derived insights. Enabling consent starts a fresh analysis period by clearing old Phase 14 records first.
- Daily and weekly reflections run only after consent and only when the user taps a review action. Summaries count normalized task-completed, task-postponed, and focus-completed events. The analyzer reads event types and timestamps only. It does not inspect event payloads, task titles, notes, memory, requests, speech transcripts, or audio.
- Weekday patterns require at least three observations on three distinct dates sharing that weekday in the rolling 28-day evidence window. Results are deterministic, carry a versioned method and source watermark, and are persisted as encrypted aggregate insight statistics. The stored confidence is the observed distinct-weekday support divided by the four-week maximum; it is an evidence-support score, not a calibrated probability.
- Saved reflections are shown in You while consent is enabled. Users can delete derived reflections or recompute them from the saved factual event history. Reflection does not rewrite append-only events; correcting source facts uses existing privacy deletion paths.
- The You switch is the source of truth for behavioral consent in this single-profile app. It is a separate local-only setting. The reserved profile consent column remains false and grants no authority. Planning preferences do not opt users in.
- No background analysis, notification, network access, new permission, model, or training flow was added.

###### Verification — 2026-10-10

- Debug and release APK assembly passed.
- Debug and release lint and Detekt passed.
- All 100 JVM tests passed, including consent denial, local-time daily/weekly boundaries, future-event exclusion, rolling evidence window and repeated-pattern threshold coverage.
- The API 34 connected Android regression passed: 182 test cases total, zero failures, 179 executed and three host-dependent reminder tests skipped. The Phase 14 consent screen and encrypted storage guard/deletion tests ran and passed.

Commands:

```powershell
.\gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:detekt :app:assembleDebug :app:assembleRelease
.\gradlew.bat --offline --no-daemon :app:connectedDebugAndroidTest
```

This is an aggregate informational baseline. It does not make recommendations, alter planning scores, schedule actions, or infer routines from private text. API 26 device coverage and broader production hardening remain in Phase 17.


## Phase 15 — Advanced ML development and evaluation

### Purpose and outcome

This phase built reproducible offline experiments, not a deployed Android model. V2's impressive generated F1 was rejected after 13/24 strict-probe false accepts. V3 narrowed to read queries and strengthened negatives/review gates. V4 improved generated results but the first 125-query user benchmark exposed weak intent accuracy and three forbidden simulated reads. V5 added structured interpretation, entity/filter/disposition metrics, broader synthetic families and review tooling; exposed regression gains did not close independent adoption gates.

**Latest decisive evidence:** the unchanged v5 candidate was frozen and scored on a separately human-approved 145-row benchmark. Intent macro F1 was 0.4328 against a 0.90 requirement; only 31/102 positive intents were correct. There were 14 forbidden simulated reads, against a zero requirement, 86/145 exact dispositions, 3/37 end-to-end nonempty entity matches and 1/62 annotated filter-field matches. Approval/freeze was completed for this evaluation; statements in earlier records that approval remained pending are historical. Quality failed. No real repository read or mutation occurred. The 145 rows are now exposed regressions and cannot be reused as fresh evaluation for another candidate. ML remains disabled and unbundled; representative calibration, actual adapter/policy outcomes, Android parity and device costs remain open.

### Detailed implementation and verification records

The following records retain checkpoint-specific scope, corrections, evidence and limitations.


#### Source record: PHASE_15_VERIFICATION.md

[Original record](PHASE_15_VERIFICATION.md)

##### Phase 15 development evaluation and model no-go

Status on 2026-10-10: the bounded candidate evaluation is finished with `NO_GO`. The ML adoption gates have not passed. The Android app retains its deterministic language rules; no trained model or ML runtime is packaged or enabled.

###### Delivered

The repository now has a reproducible development experiment, a native JVM baseline runner, versioned synthetic data with integrity hashes, validation-only confidence calibration, safety/resolution fixtures, and explicit adoption gates. The CI build job runs the experiment and uploads its report. Successful execution means the tooling worked; the report's separate decision determines model eligibility. This candidate's decision is `NO_GO`.

###### Dataset and scope

The corrected [v2 intent corpus](evaluation/phase15/intent-v2.csv) contains 1,600 synthetic utterances for seven language intents and `UNKNOWN`: 1,130 train, 240 validation, and 230 test. There are 157 family groups, split 110/24/23. Similar template stems are joined before an approximately 70/15/15 family split; integer rounding per label and merged template families explain the row ratios. The corpus has no exact normalized duplicates or families shared across splits. Each intent has at least 140 training examples.

These examples are authored in the repository with fictional slots. They contain no private messages, audio, contact exports, notifications, or user timelines. `case_kind=intent_only` rows annotate semantic intent; their empty entity arrays do not claim an entity extraction evaluation, and their product-level `expected_outcome` labels are not scored as executed actions. The separate [50-case safety corpus](evaluation/phase15/safety-v2.csv) fully annotates the expected production rule intent, UTF-16 entity spans, normalization, and the read-only language harness's status at a fixed clock and zone. Two typo probes describe the current grammar's follow-up behavior and are reported separately from unsafe acceptance.

The initial v1 experiment was inspected and corrected during development, including task-label errors and a Python transcription of the rules. Its earlier scores are superseded. The v2 families are isolated from fitting and threshold selection, but this is a single-author synthetic development evaluation with prior exploratory exposure. It is not an independent blind or real-user accuracy benchmark. The [manifest](evaluation/phase15/manifest-v2.json) records dataset/source hashes and that limitation.

###### Method

The `:app:phase15RulesSnapshot` JVM task calls the production `EnglishRules` and `languageAgentOrchestrator` directly with fictional read ports. It exports all 1,650 requests and ties the snapshot to hashes of its input and relevant production source. Stale snapshots, missing predictions, incomplete label coverage, duplicate IDs/text, invalid UTF-16 annotations, family leakage, and partial dataset writes are rejected. The helper lives in unit-test sources and is excluded from application APKs.

The candidate is word TF-IDF (1–2 grams, lowercase, Unicode accent stripping, sublinear term frequency) plus logistic regression (`C=1`, `max_iter=2000`, seed 1501). Vocabulary, IDF, and classifier weights are fitted on train only. Eight validation families (80 rows) fit a scalar temperature against negative log likelihood; the other sixteen validation families (160 rows) select the lowest cutoff from 0.90 to 0.99 with no unsupported validation false accepts. Calibration and cutoff selection use disjoint families. The selected temperature is approximately 0.1349 and cutoff is 0.90, matching the orchestrator's existing high-confidence requirement.

The simulated candidate preserves recognized rule intents, unsupported input sources, and the rule engine's negation guard. It only attempts classification where the rules return unnegated `UNKNOWN`. This simulation is intent-only; it does not implement a model-compatible entity extractor or action adapter. The approach follows the documented [TF-IDF](https://scikit-learn.org/stable/modules/feature_extraction.html) and [logistic-regression](https://scikit-learn.org/stable/modules/generated/sklearn.linear_model.LogisticRegression.html) interfaces. Confidence fitting uses data separate from model training, as required by the [calibration guidance](https://scikit-learn.org/stable/modules/calibration.html), with a bounded [SciPy scalar minimizer](https://docs.scipy.org/doc/scipy/reference/generated/scipy.optimize.minimize_scalar.html).

###### Results

| Measurement | Result |
| --- | ---: |
| Production rules macro F1 on 230 generated test prompts | 0.0288 |
| ML raw argmax macro F1 on those prompts | 0.9571 |
| Rules-first calibrated candidate macro F1 at 0.90 | 0.9958 |
| Native safety intent/status/entity exact matches | 50 / 50 each |
| Native annotated time/duration normalization decisions | 14 / 14 |
| Native negated requests reaching reads | 0 / 8 |
| Candidate high-confidence false accepts on strict rejection probes | **13 / 24** |

The ordinary test families deliberately stress wording outside the closed grammar. The rules' low score on those families does not describe correctness within their supported grammar. The candidate's high score is limited to easy generated prompts. After validation calibration, its synthetic-test top-label ECE is 0.0227 and Brier score is 0.0274, yet it is highly confident on unsafe or unsupported wording.

For example, a quoted timer command becomes `CREATE_TIMER`, a hypothetical task request becomes `CREATE_TASK`, and a compound calendar/call request becomes `CHECK_CALENDAR`. These are incorrect candidate classifications in the experiment, not executed Android actions. The actual rules reject these probes. The eight negation probes remain blocked in the candidate simulation because the existing deterministic guard is retained. Recognition of the two typo probes is reported as a possible language improvement, not counted as unsafe acceptance.

The native entity and outcome results describe the existing read-only language harness, with synthetic successful reads and zero proposed or completed actions. They do not validate the candidate's entity handling, policy integration, or external effects. Complete per-label metrics, confusion matrices, calibration diagnostics, and failed probe IDs are in the [reference result](evaluation/phase15/results-v2.json).

###### Decision and remaining gates

Reject this candidate for deployment. The machine-readable decision requires every gate and cannot be made eligible by a good F1 score alone. The strict rejection gate fails, independent evaluation is absent, and deployment calibration, candidate entities/outcomes/policy, Android parity, and representative-device cost gates remain unmet. Model size, device latency, RAM, and battery were not measured because the safety failure stops adoption before packaging. No host timing is represented as a phone measurement.

A future candidate needs independently authored and reviewed frozen data with difficult negatives, complete entity/outcome evaluation, policy validation, and Android parity/device measurements. Further tuning on these inspected probes would be development work and would require fresh independent evaluation before adoption. This record closes the present candidate with a no-go decision; it does not declare the ML enablement gates passed.

###### Reproduction

Use Python 3.12 with the [pinned dependencies](evaluation/phase15/requirements.txt) installed in an isolated environment. The [runtime record](evaluation/phase15/runtime.txt) lists the local versions. From the repository root on Windows:

```powershell
python -m venv .venv-phase15
.\.venv-phase15\Scripts\python.exe -m pip install -r docs/evaluation/phase15/requirements.txt
.\docs\evaluation\phase15\run_evaluation.ps1 -Python .\.venv-phase15\Scripts\python.exe
```

Use `-Offline` only when Gradle dependencies are already cached. The wrapper stops on each failed step and writes native predictions and the fresh experiment report under `app/build/reports/phase15/`. It does not regenerate the checked-in corpus. Intentional dataset changes use `build_dataset.py`, require review of the manifest and annotations, and supersede prior evaluation claims. CI performs the equivalent commands on Linux and retains `NO_GO` as a valid evaluation outcome without enabling a model.

###### Checks

The local native snapshot exported 1,650 requests. All 50 native safety/resolution cases matched their expected intent, outcome, and entities, including 14 normalization decisions. The Python integrity suite covers malformed/truncated corpora, leakage, duplicate text, UTF-16 annotations, atomic generation, stale/missing snapshots, disjoint validation families, deterministic reproduction, and adoption gates that fail closed.

Local verification passed: 13 Python integrity regressions, all 100 existing JVM tests, debug/release lint, Detekt, debug/release APK builds, and instrumentation APK assembly. The native snapshot exported 1,650 requests and its 50 safety/resolution cases passed. The experiment completed successfully with a separate `NO_GO` adoption decision.

The Android production sources are unchanged by this phase. Android instrumentation was compiled but was not rerun on a device; physical-device latency/RAM/battery and GitHub CI completion remain unclaimed. `git diff --check` passed after the final documentation/artifact update.


#### Source record: PHASE_15_READ_QUERIES.md

[Original record](PHASE_15_READ_QUERIES.md)

##### Phase 15 continuation: read-query development candidate

Status on 2026-10-10: **NO_GO; Phase 15 remains active.** Development tooling and the pending review package are implemented. Independent benchmark evaluation, Android integration, and device measurements are not complete. The app still uses rules with no packaged model or ML runtime.

Follow-up: the user supplied 25 challenge prompts, evaluated separately against the unchanged candidate with 40% read-intent coverage and zero false accepts on five negatives. The full review package remains unapproved; see [the contribution report](PHASE_15_USER_CHALLENGE.md). The delivery details below describe the original v3 checkpoint.

###### Delivered

- Preserved and committed the original experiment as `f9dd951` with its rejected [v2 report](PHASE_15_VERIFICATION.md). V2 dataset, evaluator, manifest, and reference results remain unchanged.
- Added a separate [v3 experiment](evaluation/phase15/read_queries_v3/README.md) for four read intents plus UNKNOWN, stronger negatives, family-separated calibration/model selection, conservative learned acceptance, actual JVM rule comparisons, and coverage reporting.
- Added a [user review guide](evaluation/phase15/read_queries_v3/REVIEW_GUIDE.md), 100 unapproved fictional draft examples, 25 blank human challenge slots, complete-review validation, source-bound freezing, and one-evaluation receipts. No user approvals or independent passing claims are fabricated.
- Added a native snapshot task, Windows runner, and CI development evaluation. No application production source or dependency was changed.

###### Development dataset and training

The 7,200 intent-only examples split into 5,110 train / 370 calibration / 720 threshold-model-selection / 1,000 test rows, covering 110/8/16/23 families. Each positive base prompt and all its negative wrappers share a family and partition. Training includes at least 140 positive examples per read intent plus 4,540 UNKNOWN examples. No normalized duplicates or family overlap are accepted.

Positive wording reuses exposed v2 templates. Negative examples cover quoted, hypothetical, compound, injection-like, negated, unsupported-language, creation, and unrelated requests. The larger sample is intentionally negative-heavy. Its test partition is for development comparison only; author exposure and heuristic grouping prevent an independent blind accuracy claim. Empty entity arrays do not establish entity correctness.

TF-IDF word 1–2 grams and balanced logistic regression fit train only. A scalar temperature fits separate calibration families. Threshold-validation families choose C from 1/4/16/64 and cutoff from 0.90–0.99, requiring zero false accepts and some accepted queries. Ties prefer smaller C/lower cutoff. The selected C is 1, cutoff 0.90, and temperature approximately 0.16627. The first unbalanced development trial rejected everything; it was not counted as a useful safety success. All four balanced candidates were compared using validation only.

The conservative eligibility filter trades coverage for rejection. It is not a general language detector or complete injection defense. The hybrid preserves native known intents and negation behavior. Creation and other non-read intents are projected to UNKNOWN only for the read-scope metric; the separate preservation check verifies that their original rule predictions are retained. No mutation is dispatched by this experiment.

###### Results

| Development test measurement | Rules | Learned candidate | Rules-first hybrid |
| --- | ---: | ---: | ---: |
| Macro F1, five labels | 0.2682 | 0.8171 | 0.7913 |
| Read coverage, 110 positive prompts | 9.09% | 65.45% | 66.36% |
| False accepts, 890 negative prompts | 10 | 0 | 10 |

The candidate accepts 72/110 positive read requests, all correctly on this generated sample. It also rejects all 24 historical strict probes within its narrower read scope. These results do not establish independent accuracy, seven-intent parity with v2, end-to-end read correctness, or device inference performance.

The hybrid's ten false accepts are compound memory requests, such as `what do you know about the lab report and send a message to Nila`. The broad existing memory rule treats the suffix as part of its search topic. The learned candidate rejects it, but known rule matches are preserved by this experiment. These are incorrect read-intent classifications against the v3 rejection contract, not sent messages or executed external actions. The failures remain explicit in [results-v3.json](evaluation/phase15/read_queries_v3/results-v3.json); no production rule behavior was silently changed.

The native runner exports 7,250 requests using actual production rules and the read-only language harness. All 50 historical regression cases still match intent, status, and entity annotations, including 14 time/duration normalization decisions and zero negated reads. Those historical checks do not cover every v3 negative or validate an ML entity adapter.

###### Decision and review checkpoint

Retain rules. Hybrid F1 is below 0.90 and strict rejection still fails. User-reviewed evaluation, deployment calibration, candidate entity/outcome/policy integration, and Android parity/device-cost gates remain unmet. A high development improvement over rules cannot override them.

The review package has not been frozen or scored. Completing it requires user review of 100 draft examples and five fresh human-authored prompts per label. Freezing checks complete annotations, original UTF-16 entity spans, duplicate/exposed text, per-label human coverage, and candidate/data/native-source provenance. Scoring reports the human-authored subset separately and requires its F1/gain alongside full-benchmark rejection checks. Local receipts prevent accidental repeated scoring; they are workflow protection, not a security boundary or proof of reviewer identity.

Android adapter work is intentionally deferred under the agreed plan until reviewed offline gates pass. There are no weights bundled, new production runtime, background training, private-data collection, or uploads. Representative-device latency (<100 ms target), RAM, size, and battery remain unmeasured. Phase 16 has not started.

###### Verification

The v3 Python suite passes 26 checks for split integrity, wrapped-family leakage, duplicate/truncated data, reproducibility, training/selection boundaries, zero-coverage rejection, source/negation/known-rule preservation, high-confidence rejection, review completeness, UTF-16 annotations, human challenge requirements, stale/tampered freezes, and repeat-scoring refusal. The original 13 Python checks also pass.

All 100 existing JVM tests pass with zero failures/errors/skips after a fresh `:app:testDebugUnitTest --rerun-tasks` run. The final Windows wrapper passes sequentially, exporting 7,250 native requests and reproducing the NO_GO decision. An earlier overlapping Gradle run caused transient missing-test-class failures; the fresh run and sequential wrapper resolved them.

Debug/release lint, Detekt, debug/release APK builds, and instrumentation APK assembly pass locally. Original v2 evaluation reproduces its NO_GO and 13/24 rejection failures. Changed-document local links and `git diff --check` pass. Android instrumentation was not run on a device; assembly is not a device-test result. Physical-device performance/battery and remote GitHub CI completion remain unclaimed. Reproduction commands and pinned dependencies are linked from the [experiment README](evaluation/phase15/read_queries_v3/README.md).


#### Source record: PHASE_15_USER_CHALLENGE.md

[Original record](PHASE_15_USER_CHALLENGE.md)

##### Phase 15: user-contributed query challenge

Evaluated on 2026-10-10 against the unchanged v3 development candidate. **NO_GO remains in effect.** This is a small intent-only diagnostic, not a completed deployment benchmark.

###### Contribution and provenance

The user supplied 25 original prompts, grouped into five task, memory, promise, calendar, and reject/clarify examples. [The CSV](evaluation/phase15/read_queries_v3/user-challenge-2026-10-10.csv) preserves wording and punctuation, removing only the surrounding quotation marks used to present each example. The user's categories supply intent labels. Entity spans and expected outcomes are assistant-drafted annotations, not independently approved labels. [The manifest](evaluation/phase15/read_queries_v3/user-challenge-manifest.json) records input/data/candidate hashes.

All 25 prompts are distinct and none exactly duplicates a normalized v3 development prompt. One contributor and five examples per label do not establish population coverage or reliable accuracy estimates. The candidate's source, train data, calibration, model selection, and cutoff remain unchanged. The challenge was excluded from training and tuning. Following this evaluation it is exposed; a later tuned candidate needs a fresh benchmark for an independent claim.

The 25 challenge slots in the existing review package now contain these prompts and draft annotations. All approval/reviewer fields remain blank, and the original 100 synthetic drafts are still unapproved. No full-benchmark freezer or independent-adoption gate was bypassed. The standalone diagnostic does not enable ML.

###### Results

The native JVM task exported predictions for all 25 prompts using actual production rules and the existing read-only language harness. Input/source hashes were checked. The frozen classifier was reconstructed from existing development partitions before loading the user challenge; no parameters were selected against these prompts.

| User category | Correct learned/hybrid intent |
| --- | ---: |
| Task query | 0 / 5 |
| Memory query | 3 / 5 |
| Promise query | 3 / 5 |
| Calendar query | 2 / 5 |
| Reject or clarify | 5 / 5 |

Read-intent coverage is **8/20 (40%)**, with all eight accepted intents correct. Macro F1 is **0.5052**; total intent agreement is 13/25. The existing rules identify none of the 20 read intents in this sample and reject/follow up on all five negatives. The learned fallback also accepts none of the five negatives. These are classifications, not verified reads: no model-compatible entity/outcome pipeline or calendar access was added.

Complete predictions, confidence, eligibility reasons, and native statuses are in [results-user-challenge.json](evaluation/phase15/read_queries_v3/results-user-challenge.json). A zero false-accept count on five examples is not evidence of general rejection safety and does not erase the existing ten development-suite hybrid failures.

###### Findings and annotation caveats

- `What tasks have I not completed yet?` is a positive task query. The existing lexical negation guard treats `not` as a negated request; the candidate also rejects it. This needs contextual treatment of task state versus command negation.
- `Show me the stuff that’s still pending.` is blocked by the conservative apostrophe/quote filter. Ordinary contractions must be distinguished from quoted commands.
- `Do I have anything planned between 3 and 6?` is blocked by the broad conjunction filter. Time ranges must be distinguished from multiple commands; date and AM/PM are also unspecified.
- Other missed requests fall below the current confidence cutoff or predict UNKNOWN. Do not lower the cutoff from this small test to manufacture a passing result.
- `What all do I need to finish today?` includes a today filter that the current task read does not implement. Its semantic intent is a task query, but a correct intent is not proof of correct filtering.
- `Did I promise Arjun anything recently?` has an unspecified recency window. `What am I supposed to help Meera with?` could refer to commitments or broader obligations. The user placed both in promise queries; their proposed outcomes remain clarification where context is needed.
- `Remind me to submit it tomorrow.` is a creation request outside the learned read scope, not nonsense. It needs the referent/title and a time clarified; UNKNOWN here does not mean the app should permanently reject reminders.

###### Reproduction

Use the pinned Phase 15 Python environment. From the repository root:

```powershell
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/read_queries_v3/user-challenge-2026-10-10.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/rules-user-challenge.tsv"
python docs/evaluation/phase15/read_queries_v3/user_challenge.py
```

This diagnostic validates the frozen development/source hashes, contributed CSV/native-input hashes, five-per-label coverage, duplicate/exposure checks, and actual native baseline provenance. It reports results without approving the full review package. Production sources and the v3 model/dataset/reference result are unchanged. No regression suite or device performance checks were rerun for this contribution.


#### Source record: PHASE_15_READ_QUERIES_V4.md

[Original record](PHASE_15_READ_QUERIES_V4.md)

##### Phase 15: read-query ML improvements v4

Recorded 2026-10-10. **Decision: NO_GO for deployment; development improvements completed.** The app still uses deterministic rules. No model/runtime or changed command policy is bundled.

###### Changes

- Expanded generated development data to 11,168 rows with family-separated partitions and nine negative wrappers.
- Added word/character TF-IDF model selection, original-text eligibility filtering, classifier-only topic/person masking, and calibrated abstention.
- Distinguished unfinished-task wording, contractions and time ranges from some negated, quoted, hypothetical and compound requests.
- Added original-string UTF-16 entity spans and conservative simulated read outcomes. The development wrapper blocks unsafe broad memory-rule matches.
- Exported complete disabled model parameters and compared serialized feature/weight math with sklearn. Source/data/native snapshot hashes preserve reproducibility.

###### Recorded evaluation

**Later standalone benchmark:** unchanged v4 scores 0.6116 macro F1 and 56/100 positive intents on the user's fresh 125 queries. Three reject/clarify cases incorrectly reach simulated reads. See [the frozen first result](evaluation/phase15/independent_125/README.md). These failures limit the generated-data numbers below; no tuning was performed before scoring.

| Measurement | Result |
| --- | --- |
| Generated evaluation macro F1, hybrid | 0.9911 |
| Positive read-intent coverage | 164/168 (97.62%) |
| Accepted intent precision | 100% in this sample |
| Negative false accepts | 0/1,602 |
| Exact annotated entity spans | 78/78 generated rows |
| Negative simulated reads | 0 |
| User positive intent regressions | 20/20; v3 was 8/20 |
| User negative intent regressions | 5/5 declined |
| Historical strict rejection probes | 0/24 false accepts |
| Existing native historical regressions | 50/50 intent, entity and harness outcome matches |
| Serialized model feature/weight parity | 1,819 rows; zero argmax differences |

The selected model uses word 1–2 grams plus character 3–5 grams, C=16, temperature approximately 0.3251 and cutoff 0.90. Model/cutoff selection uses threshold families only. Four task examples remain abstentions. Comparisons with v3 use different generated corpora and do not establish an independent accuracy gain.

All 25 user prompts are exposed regressions. Their exact text is excluded from training and selection data, but development was informed by them. Generated examples reuse template patterns and slot values. Zero observed false accepts is not a population error guarantee. Entity and outcome annotations have not received human approval.

Intent recognition does not imply the request can execute. For example, “finish today” receives follow-up because the current reader cannot filter tasks by date; ambiguous promises receive follow-up; calendar queries remain unsupported. Simulated statuses are not actual repository or executor outcomes. The user’s incomplete reminder remains outside this read-only candidate’s scope.

###### Remaining work

1. Freeze and score a fresh human-reviewed benchmark, including varied English, unseen entities, ambiguity, negation and general-information distractors. The previous review package is still unapproved.
2. Measure calibration on representative independent data and improve remaining abstentions without weakening rejection.
3. After the accuracy/rejection/review gates pass, integrate a read-only Android adapter with consistent intent/entities, real policy/outcome validation and fallback.
4. Measure Android numerical parity, latency (<100 ms target), artifact size, RAM and battery on representative devices before enabling.

The [reproduction guide](evaluation/phase15/read_queries_v4/README.md) and [recorded results](evaluation/phase15/read_queries_v4/results-v4.json) contain methodology, provenance, metrics and gates. V2/v3 records remain unchanged. No full independent benchmark or Phase 15 adoption gate is claimed complete.


#### Source record: PHASE_15_READ_QUERIES_V5.md

[Original record](PHASE_15_READ_QUERIES_V5.md)

##### Phase 15: v5 ML and structured read interpretation

Recorded 2026-10-10. **Development implementation completed; deployment remains NO_GO.** No Android model, production rule change, database migration or actual read integration is introduced. Phase 16 has not started.

###### Implemented

- Separate learned intent from read/clarify/reject/unsupported disposition. Resolve original UTF-16 topic/person spans and preserve structured retrieval constraints.
- Clarify vague/incomplete topics, unresolved people, confusing negation and contradictory requests. Missing-reference protection applies even when the classifier chooses the wrong read label.
- Expand short/slang/declarative English and limited Roman Hinglish. Distinguish past promise actions from action commands; retain unavailable-calendar responses and native non-read rule matches.
- Add 18,570 synthetic examples with family separation and disjoint topic/person pools, word versus word/character model selection, masking and explicit lexical features, bounded synthetic calibration and abstention.
- Add disabled serialized model export, numeric feature/weight parity, separate entity/filter/disposition metrics, unapproved annotation drafts and fresh-review freezing/scoring tools. CI runs the development checks.

###### Recorded results

| Measurement | V5 |
| --- | --- |
| Generated evaluation macro F1 | 0.9133 |
| Generated positive intent coverage | 215/246; includes six wrong positive labels |
| Generated unknown false accepts | 6/2,502; all misclassified as calendar, with no simulated read |
| Forbidden simulated reads in generated evaluation | 0 |
| Isolated entity extraction with expected intent | 120/120 draft spans |
| End-to-end entity spans | 114/120 |
| Isolated annotated filter fields | 66/66 |
| End-to-end annotated filter fields | 47/66 |
| Exact generated dispositions | 2,639/2,748 |
| Exposed 125-query macro F1 | 0.8115; original v4 first score 0.6116 |
| Exposed positive intents correct | 80/100; v4 was 56/100 |
| Exposed reject/clarify cases | 25/25 receive clarify/reject, zero simulated reads |
| Earlier exposed 25 prompts | 20/20 positive intents; 5/5 negative routing abstentions |
| Serialized feature/weight parity | 2,898 rows; zero argmax differences |

On the exposed 125-query set: tasks 18/25, memory 21/25, promises 23/25, calendar 18/25 correct intents. One additional positive receives a wrong read label, so 81% read-label coverage is not 81% intent accuracy. Five reject/clarify cases retain a semantic read label but safely clarify; strict routing abstention is 20/25. Full response wording and actual retrieval are not verified. Entity/outcome annotations for these prompts remain unapproved.

The selected model uses word/character TF-IDF, C=4, temperature approximately 0.7432 and cutoff 0.90. Vocabulary/weights fit eligible train only, temperature fits eligible calibration only (84 rows), and model/cutoff selection uses threshold families only. ECE/Brier are recorded separately on eligible classifier inputs. Representative calibration remains unproven.

V5 partitions and all contributed prompts were exposed during development. The generated corpus is harder and differs from v4; its score cannot establish a direct independent gain. An intermediate run reached higher exposed coverage but was rejected after calibration/negative-request weaknesses were found. The final result preserves abstention and reports remaining errors instead of claiming the interim score.

###### Safety and contract limits

No real read, proposal, completion or mutation is executed. Current task/person read interfaces cannot apply the requested date/status/category filters; v5 clarifies rather than silently ignoring them. Calendar queries return unsupported when recognized. Limited Hinglish does not mean general Hindi or multilingual support. All extraction patterns and lexical guards remain bounded heuristics.

The original native 50-case baseline still matches all recorded intents/entities/harness outcomes. Of the old 24 strict negatives, `mere tasks dikhao` is intentionally eligible under the user's newly approved Hinglish scope. Its original UNKNOWN expectation and raw legacy result remain preserved: one legacy mismatch. The other 23 current-scope strict cases have zero false accepts or simulated reads. This is an explicit scope change, not a rewritten historical fixture.

###### Verification and remaining gates

- All 22 Python regression tests pass, covering original unsafe reads, incorrect-label bypass, negation/compounds, entities/UTF-16, filters/snapshot dates, limited Hinglish, calibration bounds, data separation and review freezing/non-overwrite. Manifest/source/data integrity and blank human-approval fields were also checked.
- Production JVM snapshot export succeeds for 18,770 requests; its existing unit-test dependency was up to date. No Android/device parity or runtime measurement is claimed.
- Serialized JSON feature/weight math matches sklearn to approximately 1.42e-14 maximum logit error. Shared preprocessing means this does not establish Android parity.
- Fresh human-reviewed evaluation, representative calibration, actual repository/policy outcomes and Android latency/RAM/battery remain pending. Generated unknown rejection and end-to-end filter/disposition results are not yet sufficient for adoption.

The [reproduction and review guide](evaluation/phase15/read_queries_v5/README.md), [recorded metrics](evaluation/phase15/read_queries_v5/results-v5.json) and [unapproved annotation drafts](evaluation/phase15/read_queries_v5/annotation-review-exposed.csv) make the work reviewable. Neither the older annotation package nor these drafts are automatically approved. A fresh benchmark must be frozen against the final candidate before another independent adoption attempt.

###### First approved v5 benchmark, 2026-10-10

The separate [145-row human-approved benchmark](evaluation/phase15/human_review_003/first_evaluation/README.md) was frozen before scoring against unchanged v5. It yields macro F1 **0.4328**, 31/102 correct positive intents, 6/43 UNKNOWN read-label accepts and **14 forbidden simulated reads**. Exact dispositions match 86/145, nonempty expected entities 3/37, and annotated filter fields 1/62. Extended approved topic/subject/age/priority/date constraints expose capability gaps; not all strict annotation mismatches imply an unsafe read. No actual repository read or mutation occurred.

Candidate parameters and export bytes match the prior reference. The first result, native snapshot, approved annotations, approval/freeze hashes and exposure receipt are preserved separately; the development reference above is unchanged. The reviewed quality gate **failed**. Representative calibration, repository/policy outcomes and Android/device evidence remain pending, and ML remains disabled/unbundled. These 145 queries are now exposed regressions for future development, not a fresh evaluation set for another candidate.


### Latest reviewed Phase 15 evidence

The [first approved 145-query evaluation](evaluation/phase15/human_review_003/first_evaluation/README.md) supersedes earlier pending-review status for that benchmark. It preserves approval, frozen annotations, native predictions, candidate hashes, first results and an exposure receipt. The first result was not improved by relabelling or retuning after inference.

| Gate | Latest status |
| --- | --- |
| Human approval, freshness confirmation and candidate-bound freeze | Completed for the first 145-row evaluation |
| Reviewed intent accuracy and rejection | Failed |
| Reviewed entities, filters and dispositions | Insufficient |
| Representative deployment calibration | Pending |
| Actual repository reads and policy outcomes | Pending |
| Android numerical parity and physical-device latency/RAM/battery | Pending |
| Model adoption | NO_GO; disabled and unbundled |

Failure categories include classifier abstention, wrong read labels, invalid person/topic extraction and failure to preserve requested constraints. An unsupported age or priority filter must cause clarification before a repository read rather than an unfiltered answer. Filter mismatches and forbidden reads are reported separately: an exact annotation failure does not always mean an unsafe read.

Development can use the now-exposed benchmark for regression checks, but the next adoption attempt needs a separately versioned fixed candidate and another fresh reviewed benchmark. No Phase 16 implementation is recorded.

## Phase 16 — Bounded emotional support

**Status: pending. No completed implementation or verification is claimed.**

### Intended purpose

The roadmap reserves this phase for structured supportive interaction with explicit boundaries: no diagnosis, no replacement for professional help and no claims encouraging emotional dependency. The product contract contains an EMOTIONAL_SUPPORT intent, but catalog presence is only a planned interface.

### Work still required

The support flow needs its own concrete contract: supported situations, bounded responses, escalation/exit behavior, privacy handling and how it routes through the shared agent protocol. Any local facts used by the flow must remain distinguishable from inferred emotional interpretations. It needs user-facing language and failure handling consistent with existing consent, authority and memory boundaries.

### Exit evidence still required

The roadmap requires bounded structured support flows. A detailed test plan, evaluated examples and implementation evidence have not been recorded. These must be defined and demonstrated before calling the phase complete. This report does not invent delivered screens, support models or tested outcomes.

## Phase 17 — Security, reliability, performance and accessibility hardening

**Status: pending as a dedicated phase. Earlier protective mechanisms and tests are implemented, but do not close this phase.**

### Intended purpose

Hardening must challenge the integrated system under adverse platform and data conditions. Earlier phases established encryption, revision checks, failure rollback, proposal expiry, privacy cleanup and permissions. Phase 17 must assess those mechanisms across the complete app and representative devices.

### Work still required

- Security: broaden validation of encrypted fields, key loss, malformed stored data, sensitive logging, backup exclusions, release packaging and permission boundaries.
- Reliability: process death at action boundaries, restart/reboot, lost WorkManager registrations, permission revocation and corruption recovery without destructive reset.
- Performance: representative distributions for parse/query/search/recommendation latency, large-history and archive pagination, memory use and battery impact.
- UI/accessibility: physical phones, large fonts, narrow/landscape/tablet layouts, full Back/IME behavior, touch exploration and human TalkBack usability.
- Platform coverage: revisit skipped host-dependent notification/restart tests and complete device coverage omitted from later individual phases.

### Known evidence gaps feeding this phase

Memory search measured 182/1,807 ms for 20/200 candidates on API 26 and 138/1,308 ms on API 34; these single emulator measurements do not meet every 150 ms target. Timeline initially displays 50 rows but underlying reads load full history. Decision ranking rejects more than 1,000 saved tasks rather than silently ranking a partial subset. Later test suites retain explicitly skipped host-dependent reminder checks. Phase 14 reports API 34 execution without a Phase 14 API 26 run. Voice still needs live spoken-device retesting.

### Exit evidence still required

Recorded security, permission, process-death, restart and corruption results; representative battery/performance measurements; device compatibility; and UI/accessibility validation. Earlier regression passes are useful inputs, but cannot replace these missing integrated checks.

## Phase 18 — Consenting real-user pilot

**Status: pending. No completed pilot, participant count or field-performance result is recorded.**

### Intended purpose

A pilot should test whether real people can complete supported tasks reliably with understandable proposals, failures and receipts. Synthetic examples and emulator tests cannot establish practical usefulness or real-world failure rates.

### Work still required

Recruit consenting participants, define the supported feature set and measurement conditions, and assess task success, wrong actions, visible failures, battery cost and compatibility. Collection must respect the roadmap's prohibition on default raw telemetry. Private requests, memories and audio must not become automatic evaluation uploads.

Pilot design should distinguish language misunderstanding, unsupported capability, denied permissions, stale proposals, execution failure and uncertain delivery. For example, successful reminder registration and notification posting are distinct observations; neither proves that a user read the notice or made a call.

### Exit evidence still required

A consented, documented evaluation of the roadmap's success/error/battery/compatibility outcomes, with limitations and follow-up fixes. No numeric release threshold or pilot size is invented here because the current roadmap does not provide one.

## Phase 19 — Production preparation

**Status: pending. A buildable unsigned release APK is available from earlier work; a production release is not established.**

### Intended purpose

Turn the verified, bounded product into a store-ready release with appropriate packaging, policies, signing, versioning and operational readiness.

### Work still required

Prepare release signing and versioning, validate the final optimized artifact, reconcile store/privacy declarations with actual permissions and provider behavior, review target SDK and production requirements at release time, and ensure remaining claims match supported capabilities. Sensitive keys must not be committed into source.

Production documentation must explain approximate reminders, optional provider speech processing, local data retention/deletion, unsupported integrations and disabled ML accurately. Earlier native/alignment and manifest checks are historical evidence rather than a guarantee about a future release artifact.

### Exit evidence still required

The roadmap calls for a store-ready build, policies, signing/versioning and production requirement review. No store submission, distribution approval, production signing or completed rollout is recorded in this repository snapshot.

## Cross-phase engineering decisions and why they matter

### 1. Data protection is a storage property and a workflow property

AES-GCM envelopes protect personal fields, with authenticated data binding ciphertext to table, record and column. Existing populated data must decrypt before writes proceed; missing keys do not trigger replacement or destructive migration. Repository transactions and optimistic revisions stop stale edits from silently replacing newer facts.

Deletion also follows derived content and action correlations. Removing a task may require removing recommendation explanations or redacting event/audit details. Minimal idempotency tombstones prevent replay from recreating deleted content. These application-level guarantees should not be interpreted as forensic erasure guarantees.

### 2. Stored intent and execution authority are separate

The original catalog is broader than the implemented grammar and executors. A typed payload, capability declaration or autonomy number cannot independently authorize an effect. Current policy, supported capability, canonical acceptance, expiry and target revisions are checked at the execution boundary.

The implemented app caps higher stored autonomy at suggestion mode for confirmed features. Answer-only authority denies effects. Unrecognized, negated, stale or ambiguous input does not gain authority through a high classifier score.

### 3. Local transactions cannot make platform work atomic

Task mutation, receipt, event and audit can share a Room transaction. Notification posting and WorkManager operations are external side effects. The reminder design therefore reserves work, verifies registration, records a durable delivery claim and reports uncertain interruption honestly.

That distinction is visible to the user: registration success is not delivery success, posting is not reading, and a reminder containing a person's name is not a call.

### 4. Explanations preserve provenance and limits

Memory ranking uses fixed relevance/importance/recency/relationship/confidence components. Task decisions use a separate fixed scoring policy. Planner V1 uses earliest deadlines and saved gaps. Behavioral reflection counts factual event types. These are distinct deterministic engines with different inputs, purposes and guarantees.

Unknown calendar availability is not treated as free time. Stored confidence is not calibrated truth. Historical recommendations retain snapshot/expiry context. Derived insights do not rewrite source events.

### 5. Evaluation quality controls adoption

Phase 7's authored regression corpus demonstrated bounded grammar behavior. Phase 15's synthetic ML metrics explored possible coverage improvements, while strict negatives and human-reviewed data revealed adoption failures. Intent accuracy, entities, filters, dispositions, policy outcomes and device costs are separate gates.

The latest NO_GO is a documented engineering outcome: the app continues using rules. It is neither a claim that no ML work was done nor a claim that advanced ML is complete.

## Verification summary by checkpoint

These are cumulative checkpoint results copied or reconciled from phase records. Counts are not additive; device/API scope and skips must remain attached to each result.

| Phase | JVM tests recorded | Android execution recorded | Important qualification |
| --- | ---: | --- | --- |
| 0 | Not applicable | Not run | Contract/link/schema/catalog consistency checks only |
| 1 | 4 | 3 per API 26/36 in recovery CI | Initial local runtime inconclusive; first CI failed setup |
| 2 | 5 | 8 per API 26/36 | Visual review on selected emulator layouts |
| 3 | 8 | 13 per API 26/34/36 | Local API 34 plus API 26/36 CI |
| 4 | 21 | 85 per local API 26/34 and CI API 26/36 | Final completion checkpoint |
| 5 | 26 | 95 per local API 26/34 and CI API 26/36 | Emulator search latency limitations |
| 6 | 48 | 98 per local API 26/34 and CI API 26/36 | Protocol baseline, no execution yet |
| 7 | 58 | 102 per local API 26/34; CI API 26/36 passed | 45 authored language cases, not independent accuracy |
| 8 | 66 | Final local baseline: 143 per API 26/34 | Earlier slice reports have smaller counts |
| 9 | 67 | 147 executed per API 26/34, plus focused UI | 3 host-only skips per full device run |
| 10 | 77 | 160 executed per API 26/34 | 3 host-only skips per device |
| 11 | 88 | 171 executed per API 26/34 | 3 host-only skips per device |
| 12 | 94 | 175 reported per API 26/34, 172 executed | 3 host-only skips per device; focused UI also passed |
| 13 | 95 | 360 reported across API 26/34, 354 executed | 6 total host-only skips; live speech not proven |
| 14 | 100 | API 34: 182 reported, 179 executed | 3 host-only skips; API 26 phase-specific coverage pending |
| 15 | 100 at recorded v2/v3 checkpoints | Instrumentation assembled, no phase-specific device rerun claimed | V5 Python regressions: 22; latest reviewed ML gate failed |
| 16–19 | No completed phase results | No completed phase results | Pending |

## Evidence index and maintenance notes

Primary status sources are [the roadmap](ROADMAP.md), [implemented core features](CORE_FEATURES.md), and each original verification record linked within this report. [Architecture](../ARCHITECTURE.md), [README](../README.md), [training plan](AGENT_TRAINING_PLAN.md), and [original source plan](reference/original-build-plan.md) provide background; some introductory status passages lag later phase records.

The foundational contracts are [agent protocol](contracts/AGENT_PROTOCOL.md), [database](contracts/DATABASE_V1.md), [UI](contracts/UI_CONTRACT.md) and [datasets](contracts/DATASET.md). Privacy/security expectations are also recorded in [PRIVACY](../PRIVACY.md), [SECURITY](../SECURITY.md), [DATASETS](../DATASETS.md) and [MODEL_CARD](../MODEL_CARD.md).

No source implementation, benchmark annotation, candidate artifact, historical verification record or phase status was changed in preparing this consolidated report. Future updates should state the new repository snapshot, preserve first-evaluation failures and distinguish historical checkpoint wording from current status.


