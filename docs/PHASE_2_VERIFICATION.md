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

Validation results are pending. The implementation is not a passing phase gate until results below are recorded.

## Visual review

Use the debug-only host on an emulator:

```sh
adb shell am start -n dev.metis.agent/.DesignSystemTestActivity --ez showcase true
adb shell am start -n dev.metis.agent/.DesignSystemTestActivity --ez showcase true --ez confirmation true
```

Verify light/dark, 2x text on a narrow viewport, landscape/tablet layouts, scroll reachability, disabled/busy state labels, keyboard handling, and full confirmation details. Inspect focus/labels/touch targets. TalkBack and real-device coverage must be recorded separately; an emulator screenshot does not establish either.

The debug host and gallery are excluded from release; no permissions or data collection were added.
