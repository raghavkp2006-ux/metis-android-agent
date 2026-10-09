# Security

This repository is a development-stage Android scaffold. It is not ready for production personal-data storage or autonomous actions.

## Reporting a vulnerability

Use the repository's GitHub **Security -> Report a vulnerability** feature when private vulnerability reporting is enabled. If it is unavailable, open an issue requesting a private reporting channel without posting exploit details, personal content, credentials, or keys. No response-time guarantee is currently established.

## Current controls

No internet permission, remote endpoint, account credentials or release signing key is included. Notification permission is the only requested runtime permission; You exposes an explicit request control and denial leaves other local features usable. WorkManager contributes normal wake-lock, boot, network-state and foreground-service declarations. Backup/transfer and cleartext traffic remain disabled. Schema v6 stores all 22 planned record types in app-private Room with Keystore-encrypted personal fields and authenticated row/column bindings. Linked-content deletion redacts history and preserves deduplication tombstones. Key loss and encryption failures fail closed; no destructive schema fallback or personal-content logging is installed. Debug seeding/inspection and Compose tooling remain excluded from release.

Unit tests check source privacy configuration. Instrumented tests check packaged permissions and application flags, plus launch/recreation. Lint and Detekt are required verification steps; no lint baseline or blanket suppression is used to hide issues. Gradle wrapper download integrity is pinned by its distribution checksum.

## Required before later features

Typed actions and current policy/permission checks; bound expiring confirmation; safe entity/date resolution; idempotent dispatch/reconciliation; verified receipts/events/audit; encrypted sensitive fields; no personal logs; tested database migrations; explicit deletion and recovery controls. Treat all observed or retrieved content as untrusted data. User autonomy settings cannot authorize R3 actions automatically.

The production action boundary requires the canonical expiring proposal and explicit acceptance. Task mutations refresh current policy, revisions and relevant prerequisites/links inside Room transactions. Only one exact-title open nonrecurring target is supported; deletion is R3, requires acceptance, rejects linked tasks and has no undo. Reminder registration and delivery each refresh notification availability and suggestion-mode policy. Exact scheduling is unavailable and never silently substituted for approximate precision. Durable unique work, registration readback, separate delivery claims, cancellation and uncertain-outcome handling prevent replay from becoming a duplicate verified action. A registered reminder does not prove notification posting, and posting does not prove reading. Calls, messages, calendar, timers and autonomous execution remain unavailable. Unknown/negated/ambiguous requests fail safely; exception text is never exposed.

Pin dependency versions in the version catalog, review dependency updates, and never commit tokens, keystores, local.properties, personal seed data, or SDK/build output. Production signing, supply-chain review, security/performance/battery testing, and real-device compatibility are Phase 17–19 gates.
