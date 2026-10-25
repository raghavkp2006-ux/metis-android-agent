# UI contract v0.2 — METIS frontend

METIS uses a chat-first Compose shell, one global composer and focused workspaces. The supplied visual references establish blue accents, rounded cards, a METIS orb and matching light/dark themes. Domain operations retain existing policy and confirmation requirements.

## Tokens

| Role | Light | Dark |
| --- | --- | --- |
| Background | #F7F9FE | #091321 |
| Surface | #FFFFFF | #122035 |
| Raised surface | #EAF0F8 | #22324B |
| Primary text | #18243B | #EEF3FF |
| Secondary text | #53627C | #B2BFD5 |
| Primary accent | #285BDA | #AAC7FF |
| On primary | #FFFFFF | #09285E |
| Border | #D6E0F0 | #2D3D57 |

Use centralized Material roles and semantic success/warning/orb tokens. Follow the system theme. Text contrast: 4.5:1 normal, 3:1 large text and meaningful control boundaries. State must not rely on color alone.

Platform type: display 28sp, page 24sp, section 18sp, body 16sp, secondary 14sp, caption 12sp. Spacing: 4/8/12/16/20/24/32/40/48dp. Screen padding 16dp; cards 16–20dp corners; primary actions pill-shaped; touch targets at least 48dp. Important text wraps at large font scales.

## Navigation

| Workspace | Responsibility |
| --- | --- |
| METIS Chat | Temporary requests, replies, proposals, receipts and undo |
| Today | Immediate tasks, next saved block, all-task access |
| Plan | Calendar, schedules, future tasks and planning |
| Memory | Stored knowledge, search and existing memory editing |
| People | Saved people and read-only details |
| Projects & Goals | Saved projects/goals and task links |
| Insights | Existing opt-in, user-triggered local reflections |
| Activity | Timeline, task actions, reminders, decision explanations |
| Settings | Profile, preferences, voice, proactive controls, privacy and data |

Below 840dp use a modal drawer; at 840dp use a collapsible 280dp sidebar. Chat/composer maximum width 800dp, other workspaces 1120dp. Narrow/large-font layouts use one column. One main content scroller per workspace. The global composer stays above the IME/system insets.

Map UI Workspace explicitly to existing AgentDestination: CHAT to AGENT; TODAY to TODAY; PLAN/PROJECTS_GOALS to PLAN; ACTIVITY to TIMELINE; remaining workspaces to YOU. Capture entity/workspace origin before opening Chat. Context propagation adds no pronoun-resolution or conversation reasoning capability.

Back closes modals/drawer, then entity detail, then returns to Chat; root Chat uses Android behavior. Navigation preserves the draft, conversation and available scroll state. Existing confirmation locks take precedence.

## Temporary chat and welcome

A shell-scoped ViewModel owns in-memory messages. Rotation/navigation preserve the session; process termination and New chat clear it. Submitted content never enters Room, preferences, saved-instance state, logs or analytics. Only the existing bounded unsent draft and UI routes are restored.

Only the current eligible result can Accept/Undo. Starting another request retires earlier controls in UI and ViewModel. Historical cards remain readable and explicitly inactive. Reject executes nothing; duplicate operations are blocked. New chat confirms when content/draft exists, cancels processing, discards pending proposals, clears draft/session and invalidates late callbacks. Reset cannot interrupt acceptance/undo or delete saved domain records.

Welcome appears until welcomeCompleted is saved. Bundled mountain/lake artwork appears only there. Permissions are deferred to supported feature use. Orb glow is reserved for welcome, greeting, processing and voice. Motion respects system animation settings.

## Design-system component contract

| Component | Required behavior |
| --- | --- |
| AgentTheme | Light/dark Material roles, typography, spacing, shapes |
| PrimaryButton / SecondaryButton / IconButton | Enabled, disabled, loading, accessible label, 48dp target |
| AgentComposer | Text draft, submit, optional microphone, partial transcript, stop/cancel, keyboard/IME handling, busy state; no silent submission |
| AgentMessage | Plain reply, timestamp as needed, accessible reading order |
| ActionProposal | Target, time/zone, action details, risk context, reason/evidence, acceptance/cancel controls |
| ActionReceipt | Verified outcome/handoff/pending/failure, affected target, time, available undo |
| ConfirmationSheet | Full action details and reason, explicit accept/cancel, stale/expired handling |
| TaskRow | Status, title, due time, priority, completion interaction |
| ScheduleBlock | Time range, title, proposed/accepted state, conflict indicator; explicit plan proposal, local availability assumptions and separate future-block reminder review |
| MemoryRow | Fact/type, source, confidence where relevant, edit/delete controls |
| TimelineRow | Event type, time, related entity, readable result |
| EmptyState / LoadingState / ErrorState | Helpful text, relevant recovery action, no fabricated content |
| PermissionExplainer | Capability, why access is needed, data scope, enable/not-now choice, denial recovery |

UI emits intents to a ViewModel/domain interface and renders immutable StateFlow state. Parsing, policy, repository queries, Android dispatch, and recommendation scoring never live in composables.

## State and interaction rules

Every screen handles loading, empty, error/retry, permission denied/revoked, and offline or unavailable capability. An offline state must not block local functionality. Voice explains microphone use before requesting RECORD_AUDIO, shows partial text and an explicit stop/cancel, and leaves the transcript editable until Send. Use Android's on-device recognizer when available. Other recognition services require a separate online-fallback opt-in in You; do not assume EXTRA_PREFER_OFFLINE guarantees offline processing.

People offers a user-started Find contact lookup. It asks for READ_CONTACTS only at first use, handles denial and revocation without blocking saved people, and asks for a choice when names remain ambiguous. More than five matches require a refined query. This manual lookup only returns a contact to transient UI; it never calls, messages, writes, proposes or records a receipt.

CREATE_REMINDER and CREATE_PROMISE may use the same read-only resolver after an exact saved Person match fails. The app shows a rationale before its one automatic READ_CONTACTS prompt. Denial, permanent denial and revocation continue with the typed name; ambiguous contacts require a choice before any proposal. Only an accepted action may link or create a Person, with encrypted display name and contact lookup key. Phone numbers and email addresses are not copied to local storage, action audit or diagnostics. Candidate lists stay in memory and are discarded with the clarification or session. No call or message is dispatched.

Proposals distinguish suggestion from completion. Confirmation always shows recipient/destination, content, local date/time and zone where relevant. High confidence never hides confirmation for R2/R3. Undo appears only when actually supported. Explainability shows reasons and evidence, including score components for rankings.

Edge-to-edge layouts account for status/navigation bars, gestures, display cutouts, and IME from the start. Verify TalkBack, focus order, dark mode, large font, landscape, small screens, and tablets. Animation communicates state changes only; respect reduced-motion/system settings.

Welcome describes local data and the global composer without changing autonomy or requesting permissions. Sensitive permissions are deferred until an enabled feature needs them.
