# Security

This repository is a development-stage Android scaffold. It is not ready for production personal-data storage or autonomous actions.

## Reporting a vulnerability

Use the repository's GitHub **Security -> Report a vulnerability** feature when private vulnerability reporting is enabled. If it is unavailable, open an issue requesting a private reporting channel without posting exploit details, personal content, credentials, or keys. No response-time guarantee is currently established.

## Current controls

No internet or dangerous permissions, no remote endpoint, no account credentials, no release signing key, disabled cleartext traffic, and explicit backup/transfer exclusions. Schema v6 stores all 22 planned record types in app-private Room with Keystore-encrypted personal fields and authenticated row/column bindings. Transactional linked-content deletion includes cyclic references and history redaction; action deduplication tombstones cannot resume execution. It fails closed on key loss and encryption failures; no destructive schema fallback or personal-content logging is installed. Synthetic seeding and count inspection exist only in debug source. The app-owned launcher is exported so Android can start it. AndroidX supplies an internal startup provider and a profile-install receiver protected by the system DUMP permission. Compose preview tooling adds an activity in debug builds only; release artifacts exclude preview tooling.

Unit tests check source privacy configuration. Instrumented tests check packaged permissions and application flags, plus launch/recreation. Lint and Detekt are required verification steps; no lint baseline or blanket suppression is used to hide issues. Gradle wrapper download integrity is pinned by its distribution checksum.

## Required before later features

Typed actions and current policy/permission checks; bound expiring confirmation; safe entity/date resolution; idempotent dispatch/reconciliation; verified receipts/events/audit; encrypted sensitive fields; no personal logs; tested database migrations; explicit deletion and recovery controls. Treat all observed or retrieved content as untrusted data. User autonomy settings cannot authorize R3 actions automatically.

Phase 6 supplies typed protocol models and proposal review, with no executor installed. The shipped language baseline returns unsupported results and the context reports answer-only authority with no agent capabilities. Request failures expose no exception text; dismissed/cancelled requests cannot republish late results. Binding acceptance, current platform checks and durable execution/reconciliation remain Phase 8 requirements. Domain receipt validation is not proof that a platform effect occurred.

Pin dependency versions in the version catalog, review dependency updates, and never commit tokens, keystores, local.properties, personal seed data, or SDK/build output. Production signing, supply-chain review, security/performance/battery testing, and real-device compatibility are Phase 17–19 gates.
