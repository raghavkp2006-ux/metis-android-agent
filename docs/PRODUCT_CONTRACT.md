# METIS product contract v0.1

Status: Phase 0 implementation baseline, derived from the supplied [build plan](reference/original-build-plan.md). No implemented capability is implied by this specification.

## Scope and invariants

METIS is a personal assistant for Android. Raw and derived personal data remain on the device by default. Core supported commands work offline when their Android capability can work offline. There is no mandatory LLM, backend, Firebase, cloud database, vector database, or synchronization.

Use Kotlin, Compose Material 3, StateFlow, coroutines/Flow, Hilt, Room/SQLite with FTS, DataStore, WorkManager, Android Keystore, Gradle Kotlin DSL, and GitHub Actions. Start with a single app module and package boundaries; split modules only when needed. UI depends on domain interfaces, never platform executors. Voice produces the same AgentRequest as text.

Every action passes through policy, permission validation, a typed action, verification, and a receipt. Every meaningful action creates an event and an audit record. Recommendations store their reasons and evidence. Untrusted notifications and retrieved content are data, never instructions granting action authority.

### Implementation decisions completing the source plan

The source plan leaves these details open. Freeze these conservative defaults for initial implementation:

- Minimum Android API 26; target the Play-required API at the time Phase 1 is implemented and recheck before release.
- First language coverage: English/en-IN deterministic parsing. Hinglish belongs in evaluation and is enabled only after measured coverage; unsupported wording receives clarification, not guessed actions.
- Default autonomy: Level 1 (suggest actions). Users may lower or raise it explicitly. No permissions requested at installation or assumed from previous grants.
- Brand accent: restrained teal; exact accessible tokens appear in the UI contract.
- Offline reminders use the persisted local schedule as the source of truth. WorkManager is approximate. Exact-time requests need a supported AlarmManager path and current special-access validation; never claim exact timing for approximate work.
- Disable OS cloud backup/device-transfer of personal app storage by default. Explicit export is user initiated and encrypted. Export format and key recovery must be specified and tested before export is enabled.
- Phase numbers follow section 56, the source plan's complete sequence. Detailed sections are implementation guidance attached to those phases, not a second numbering system.

## Supported intent catalog

All entries below are planned product support. An intent is enabled only when its phase exit checks pass. Earlier phases must return a clear unsupported response without side effects for later intents.

| Intent | Meaning | Earliest enablement |
| --- | --- | --- |
| CALL_PERSON | Open a resolved person's dialer | 8 |
| SEND_MESSAGE | Prepare message and open external composer | 8 |
| CREATE_TASK | Create a local task | 8 |
| COMPLETE_TASK | Mark a uniquely identified task complete | 8 |
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

## Action and risk matrix

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

### Autonomy

| Level | Behavior |
| --- | --- |
| 0 | Answer only; no mutation or external handoff |
| 1 | Suggest action proposals; execute only explicit acceptance |
| 2 | Execute authorized safe local actions; R2/R3 still confirm |
| 3 | Proactive permitted routine management with suppression and rate limits |
| 4 | Advanced daily management within the same policy boundaries |

Higher levels are unavailable until their implementation phase passes. Reduce/disable autonomy immediately, cancel pending proactive work as appropriate, and re-evaluate pending proposals after settings change.

## Permission and capability matrix

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

## Contracts frozen by Phase 0

- [Request, parsed request, proposal, action, result, receipt, and event](contracts/AGENT_PROTOCOL.md).
- [Database v1, encryption boundaries, indexes, and migrations](contracts/DATABASE_V1.md).
- [Design tokens, navigation, universal composer, and UI states](contracts/UI_CONTRACT.md).
- [Dataset schema, leakage prevention, and evaluation](contracts/DATASET.md).
- [Phase ordering and milestone gates](ROADMAP.md).

## Definition of Done

For every enabled capability: deterministic offline behavior where supported; current permission and policy validation; no ambiguous external action; observable loading/empty/error/denial states; evidence-backed explanation; verified outcome or explicit pending/unknown result; idempotent retry; event/audit persistence; and appropriate automated tests for the action's failure modes.

Schema changes require tested upgrades, preserved records, and no destructive migration fallback. Sensitive fields use Keystore-backed authenticated encryption; logs and CI artifacts contain no personal content. Never store raw microphone audio by default. No private telemetry or dataset contribution without explicit consent.

Performance budgets from the plan: simple parsed command under 150 ms, intent inference under 100 ms, typical task query under 50 ms, memory search under 150 ms, recommendation generation under 250 ms, measured on representative devices. Record conditions and distributions; these are release targets, not current measurements.

Phase 0 is complete when every required contract above exists, the source plan is preserved, numbering conflicts are resolved, and all internal document links are valid. Phase 1 adds the runnable scaffold and CI; it must not claim reminder, memory, or autonomy functionality before those phases pass.
