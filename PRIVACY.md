# Privacy

METIS is designed to keep raw and derived personal data on the user's Android device by default. Phase 1 is a foundation screen: it collects no personal data, stores no conversation, records no microphone audio, requests no device permissions, and has no account, analytics SDK, advertising SDK, backend, or internet permission.

## Current safeguards

- Android backup is disabled; legacy backup and Android 12+ cloud/device-transfer rules exclude personal storage domains.
- Cleartext traffic is disabled. The installed app's permission set is tested to exclude internet and dangerous runtime permissions.
- The app contains no telemetry or remote reporting client. Build tools download public dependencies during development; that is separate from app runtime behavior.
- Release artifacts are unsigned development outputs, not a store-ready application.

## Future capabilities

When enabled in later phases, tasks, reminders, memories, calendar context, and behavioral statistics remain local. Sensitive fields must be encrypted at rest with Keystore-backed keys. Contact/calendar/microphone/notification access is requested progressively for an enabled feature, with denial paths. Behavioral analysis and dataset contribution require explicit consent; contribution must redact private content and identifiers. No default private telemetry.

Opening a dialer, maps app, calendar editor, or messaging composer hands selected information to another app only after the applicable policy/confirmation checks. That app's privacy policy applies after the handoff. System speech recognition is not assumed offline: use on-device support where available and require opt-in for any online fallback.

Encrypted export/import and synchronization are not implemented. Neither raw data nor derived data is sent to a cloud service by default. Device-specific backup behavior and storage protections require further device testing before production; this scaffold does not claim forensic secure deletion or full production security certification.

The [product contract](docs/PRODUCT_CONTRACT.md) and [database contract](docs/contracts/DATABASE_V1.md) define the requirements for future phases. Update this document whenever actual data handling changes.
