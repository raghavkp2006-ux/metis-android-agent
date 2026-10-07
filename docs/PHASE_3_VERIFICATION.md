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

Results are pending; no unexecuted test is counted as passing. Full system process-death, spoken TalkBack, physical-device, and performance validation belong to later hardening. Phase 4 does not start until this phase gate is recorded as passed.

## References

Implementation follows Android's [state-saving guidance](https://developer.android.com/develop/ui/compose/state-saving) and [inset handling](https://developer.android.com/develop/ui/compose/system/insets-ui). Local generated screenshots/reports are ignored build artifacts.
