# Phase 4 API 26 CI recovery

The preference slice's [GitHub run](https://github.com/raghavkp2006-ux/metis-android-agent/actions/runs/37714325812) passed build/static checks and API 36. API 26 ran 49 tests with four failures: two keyboard waits timed out, then two saved-record assertions encountered 21 memory fixtures whose cleanup had been skipped.

The keyboard Back test captures the unobscured viewport before typing, verifies that the keyboard reduces it, and waits for it to return after Back. Window insets and registered keyboard windows can outlive the visible keyboard; the tests no longer use them to infer visibility. An unavailable viewport fails explicitly. Back still must dismiss the draft and preserve its source destination. Search cleanup invokes the real Search keyboard action and asserts text focus is cleared; it no longer compares viewport sizes across unrelated screen/layout changes. Search fixtures are removed in a nested finally block even if focus verification throws.

Navigation now clears Compose text focus before changing destination or opening Privacy/the request draft. Memory search has an explicit Search keyboard action that clears text focus; search remains local and reactive. The navigation regression types a memory search before opening Privacy and the draft, then verifies Back dismissal after recreation. No app permissions, dependencies, encryption, or schema changes are introduced. Back dismissal and fixture assertions remain enabled.

Validation: Android test APK compilation, 18 JVM tests, debug/release lint, and Detekt passed locally. Fresh full API 26/36 runs are pending; this report does not claim they passed yet.
