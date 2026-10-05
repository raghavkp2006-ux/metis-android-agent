# UI contract v0.1

Specification only. Phase 2 builds the design system; Phase 3 builds navigation. The interface is calm and minimal, uses platform sans-serif typography, and makes actions, reasons, and limitations visible. No decorative AI orb, gradients, unrelated dashboard cards, or OpenAI branding.

## Tokens

| Role | Light | Dark |
| --- | --- | --- |
| Background | #FAFAFA | #121212 |
| Surface | #FFFFFF | #1E1E1E |
| Primary text / on surface | #171717 | #F5F5F5 |
| Secondary text | #525252 | #BDBDBD |
| Primary accent | #006A60 | #80D5C8 |
| On primary | #FFFFFF | #003730 |
| Error | #B3261E | #F2B8B5 |
| On error | #FFFFFF | #601410 |
| Divider | #E0E0E0 | #383838 |

These are fixed initial tokens, not a complete Material color scheme. Phase 2 maps remaining roles and verifies actual text, controls, disabled states, and focus indicators. Normal text needs WCAG AA 4.5:1 contrast; large text and meaningful control boundaries need 3:1. A divider is not the sole indication of an interactive boundary. Accent is used for primary actions, selection, listening, and progress, never every surface.

| Typography | Size | Weight |
| --- | --- | --- |
| Display | 28sp | Medium |
| Page title | 24sp | Medium |
| Section | 18sp | Medium |
| Body | 16sp | Regular |
| Body small | 14sp | Regular |
| Caption | 12sp | Regular |

Spacing values: 4, 8, 12, 16, 20, 24, 32, 40, 48dp. Horizontal screen padding: 16dp; large layouts may use 20dp. Section gaps: 24–32dp. Radii: controls 8dp, interactive items 12dp, sheets 20dp, composer 24dp. Touch targets at least 48dp. Font size follows system scaling; labels must wrap rather than clip essential text.

## Navigation

| Destination | Responsibility |
| --- | --- |
| Today | Current day, next useful action, compact relevant outcomes |
| Plan | Tasks, calendar, goals, proposed and accepted schedules |
| Agent | Conversation, proposals, follow-ups, receipts, explanations |
| Timeline | Meaningful normalized event history |
| You | Memory, explicit preferences, insights, permissions, autonomy, privacy/settings |

Use five bottom destinations on phones, adapting to a rail on large layouts when supported. Preserve selected destination and draft input across rotation. Back dismisses a sheet/input state before navigating. Detail routes retain source context. The composer remains accessible from every main destination; a submission enters AgentRequest and may open Agent with the same conversation context.

Do not show fake tasks, memories, or successful actions in the navigation scaffold. Empty states explain unavailable or unimplemented capabilities accurately.

## Design-system component contract

| Component | Required behavior |
| --- | --- |
| AgentTheme | Light/dark Material roles, typography, spacing, shapes |
| PrimaryButton / SecondaryButton / IconButton | Enabled, disabled, loading, accessible label, 48dp target |
| AgentComposer | Text draft, submit, optional microphone, keyboard/IME handling, busy state; no silent submission or recording |
| AgentMessage | Plain reply, timestamp as needed, accessible reading order |
| ActionProposal | Target, time/zone, action details, risk context, reason/evidence, acceptance/cancel controls |
| ActionReceipt | Verified outcome/handoff/pending/failure, affected target, time, available undo |
| ConfirmationSheet | Full action details and reason, explicit accept/cancel, stale/expired handling |
| TaskRow | Status, title, due time, priority, completion interaction |
| ScheduleBlock | Time range, title, proposed/accepted state, conflict indicator |
| MemoryRow | Fact/type, source, confidence where relevant, edit/delete controls |
| TimelineRow | Event type, time, related entity, readable result |
| EmptyState / LoadingState / ErrorState | Helpful text, relevant recovery action, no fabricated content |
| PermissionExplainer | Capability, why access is needed, data scope, enable/not-now choice, denial recovery |

UI emits intents to a ViewModel/domain interface and renders immutable StateFlow state. Parsing, policy, repository queries, Android dispatch, and recommendation scoring never live in composables.

## State and interaction rules

Every screen handles loading, empty, error/retry, permission denied/revoked, and offline or unavailable capability. An offline state must not block local functionality. Voice shows listening state and an explicit stop/cancel; microphone access occurs only after a user gesture. Platform speech services are not assumed offline.

Proposals distinguish suggestion from completion. Confirmation always shows recipient/destination, content, local date/time and zone where relevant. High confidence never hides confirmation for R2/R3. Undo appears only when actually supported. Explainability shows reasons and evidence, including score components for rankings.

Edge-to-edge layouts account for status/navigation bars, gestures, display cutouts, and IME from the start. Verify TalkBack, focus order, dark mode, large font, landscape, small screens, and tablets. Animation communicates state changes only; respect reduced-motion/system settings.

Initial onboarding states that data is local, describes the single composer, selects autonomy (default Level 1), and optionally introduces notifications with a “Not now” path. Sensitive permissions are deferred until an enabled feature needs them.
