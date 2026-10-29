# External Actions Design and Implementation

## Overview
External actions bridge Metis's conversational agent to Android platform intents. Following Metis's core privacy and safety principles:
1. **Explicit Accept Only**: External actions are never dispatched automatically. The agent proposes the action with all resolved parameters; user must explicitly accept before the platform intent is launched.
2. **Permission Boundary**: No dangerous call/SMS permissions (`CALL_PHONE`, `SEND_SMS`) or network permissions (`INTERNET`) are requested. `AlarmClock.ACTION_SET_TIMER` and `AlarmClock.ACTION_SET_ALARM` use the normal platform permission `com.android.alarm.permission.SET_ALARM`. No `SCHEDULE_EXACT_ALARM` is used.
3. **Receipt and Audit Durability**: Every accepted external action records an entry in `action_runs` and `action_audit` via `AcceptedExternalActionStore`.
4. **Graceful Failure Handling**: If no app is installed to handle the intent (`ActivityNotFoundException`), the launcher catches it and returns `ExternalLaunchResult.NoHandler`, producing an explicit `AgentResultStatus.FAILED` receipt outcome without crashing.
5. **Decoupled Architecture**: All intent launches are isolated behind the `ExternalActionLauncher` interface, allowing full unit-test verification with fake launchers.

---

## 1. CREATE_TIMER
- **Trigger**: "set a timer for 5 minutes", "start timer for 30 seconds"
- **Specialist Resolution**: `LanguageSpecialist` extracts `EntityKind.DURATION` and optional `EntityKind.TITLE` (defaults to "Timer"). Proposes `CreateTimerAction(durationSeconds, label)` when `Capability.TIMER` is granted.
- **Duration Constraints**: Bounded from 1 second to 86,400 seconds (1 day). Invalid durations return clarification: "Use a timer duration from 1 second to 1 day."
- **Platform Intent**: `AlarmClock.ACTION_SET_TIMER` with extras:
  - `AlarmClock.EXTRA_LENGTH`: integer seconds
  - `AlarmClock.EXTRA_MESSAGE`: timer label
  - `AlarmClock.EXTRA_SKIP_UI`: `false` (shows system timer UI)
- **Manifest Permission**: Normal protection level `com.android.alarm.permission.SET_ALARM`.
- **Outcome Verification**: `OutcomeVerification.VERIFIED_PLATFORM` with `ActionStatus.SUCCEEDED` on successful launch, or `OutcomeVerification.UNVERIFIED` with `ActionStatus.FAILED` on `NoHandler`.

---

## 2. CREATE_ALARM
- **Trigger**: "set an alarm for 7 am", "wake me at 6:30 tomorrow", "wake me up at 6:30 tomorrow"
- **Grammar & Resolution**: `EnglishRules` classifies `AgentIntent.CREATE_ALARM`. `AlarmResolution` extracts `EntityKind.TIME` with date and time:
  - If a time on today has already elapsed and no date was specified, rolls forward to tomorrow.
  - Resolves bare 24-hr times or 12-hr AM/PM times (`7 am`, `6:30`, `18:30`).
  - Supports optional label (e.g. "called Work").
- **Specialist Resolution**: `LanguageSpecialist` proposes `CreateAlarmAction(trigger, label)` when `Capability.CLOCK_ALARM` is granted.
- **Platform Intent**: `AlarmClock.ACTION_SET_ALARM` with extras:
  - `AlarmClock.EXTRA_HOUR`: 24-hour integer hour
  - `AlarmClock.EXTRA_MINUTES`: integer minute
  - `AlarmClock.EXTRA_MESSAGE`: alarm label
  - `AlarmClock.EXTRA_SKIP_UI`: `false` (shows system clock UI)
- **Permission Safety**: Uses normal permission `com.android.alarm.permission.SET_ALARM`. Does **not** require or declare `android.permission.SCHEDULE_EXACT_ALARM`.
- **Outcome Verification**: `OutcomeVerification.VERIFIED_PLATFORM` with `ActionStatus.SUCCEEDED` on successful launch, or `OutcomeVerification.UNVERIFIED` with `ActionStatus.FAILED` on `NoHandler`.

---

## 3. CALL_PERSON
- **Trigger**: "call Rahul", "dial Rahul", "phone Mom"
- **Grammar**: `EnglishRules` matches `(?:call|dial|phone) (.+?)` to `AgentIntent.CALL_PERSON` extracting `EntityKind.PERSON`. Bare "call" returns a follow-up asking "Specify who you want to call."
- **Contact Resolution**: Queries `ContactsResolver.resolve(personName)`.
  - **Ambiguous Match**: Returns `AgentResultStatus.FOLLOW_UP` with a `FollowUpQuestion` listing candidate names/numbers and suggested choices for user disambiguation.
  - **NotFound Match**: Returns `AgentResultStatus.ANSWER` explaining no contact was found.
  - **PermissionNeeded**: Returns `AgentResultStatus.ANSWER` explaining contacts permission is needed.
  - **No Phone Number**: Returns `AgentResultStatus.ANSWER` explaining no phone number was found for the contact.
  - **Single Resolved Match**: Extracts `phoneNumber` and constructs a `CallAction(targetLabel, phoneNumber, CallMode.DIAL)` proposal displaying the resolved phone number.
- **Platform Intent**: `Intent.ACTION_DIAL` with `tel:<phone_number>` URI constructed safely via `Uri.fromParts("tel", phoneNumber, null)`.
- **Privacy & Redaction**: `CallAction.toString()` redacts `phoneNumber` and `targetLabel` as `[REDACTED]` to prevent PII leakage in logs and stringified representations.
- **Permission Safety**: Strictly avoids `Intent.ACTION_CALL`. Requires **no** `android.permission.CALL_PHONE` dangerous runtime permission.
- **Explicit Accept Requirement**: Launch never happens automatically; the phone dialer is only opened after the user explicitly accepts the proposal card. Pending proposals are atomically consumed via `compareAndSet(proposal, null)` to protect against double-tapping or replay.
- **Outcome Verification**: `OutcomeVerification.HANDOFF_ONLY` with `ActionStatus.HANDED_OFF`. Recorded to `action_runs` and `action_audit`.

---

## 4. SEND_MESSAGE
- **Trigger**: "message Mom: running late", "text Rahul that I'll be there at 8", "send a message to Mom: on my way", "text Rahul saying will be late"
- **Grammar**: `EnglishRules` matches `(?:send (?:a )?message to|message|text) (.+?)(?::\s*|\s+that\s+|\s+saying\s+)([\s\S]+?)` to `AgentIntent.SEND_MESSAGE`, extracting `EntityKind.PERSON` (recipient) and `EntityKind.TITLE` (message body). Bare "message" returns a follow-up asking "Specify a recipient and message body."
- **Contact Resolution**: Queries `ContactsResolver.resolve(recipient)`.
  - **Ambiguous Match**: Returns `AgentResultStatus.FOLLOW_UP` with a `FollowUpQuestion` listing candidate names/numbers and suggested choices.
  - **NotFound Match**: Returns `AgentResultStatus.ANSWER` stating no contact was found.
  - **PermissionNeeded**: Returns `AgentResultStatus.ANSWER` stating contacts permission is needed.
  - **No Phone Number**: Returns `AgentResultStatus.ANSWER` explaining no phone number was found for the contact.
  - **Single Resolved Match**: Extracts `phoneNumber` and constructs a `MessageAction(recipient, destination, body, MessageMode.COMPOSER)` proposal card showing recipient, phone number, and message body.
- **Platform Intent**: `Intent.ACTION_SENDTO` with `smsto:<phone_number>` URI constructed safely via `Uri.fromParts("smsto", phoneNumber, null)` and `putExtra("sms_body", body)`.
- **Privacy & Redaction**: `MessageAction.toString()` redacts `recipient` and `body` as `[REDACTED]` to prevent PII leakage in logs and stringified representations.
- **Permission Safety**: Opens the platform messaging composer only. Never sends messages automatically in the background. Requires **no** `android.permission.SEND_SMS` dangerous runtime permission.
- **Explicit Accept Requirement**: Launch never occurs until user explicitly accepts the proposal card. Pending proposals are atomically consumed via `compareAndSet(proposal, null)` to prevent double-tapping or replay.
- **Outcome Verification**: `OutcomeVerification.HANDOFF_ONLY` with `ActionStatus.HANDED_OFF`. Recorded to `action_runs` and `action_audit`.




