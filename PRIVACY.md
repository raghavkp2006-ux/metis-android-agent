# Privacy

METIS keeps raw and derived personal data on the user's Android device by default. The first Phase 4 slice stores task, schedule, and memory records in an app-private Room database. Personal text is encrypted with Keystore-backed AES-GCM keys and fresh nonces; no plaintext search duplicate is stored. The app stores no conversation, records no microphone audio, requests no device permissions, and has no account, analytics SDK, advertising SDK, backend, or internet permission. Request drafts remain temporary Android instance state, separate from encrypted records.

## Current safeguards

- Android backup is disabled; legacy backup and Android 12+ cloud/device-transfer rules exclude personal storage domains.
- Cleartext traffic is disabled. The installed app's permission set is tested to exclude internet and dangerous runtime permissions.
- The app contains no telemetry or remote reporting client. Build tools download public dependencies during development; that is separate from app runtime behavior.
- Reads/writes that cannot decrypt existing data fail visibly without resetting storage or replacing its key. Storage errors and personal content are not logged. Metadata such as IDs, dates, status, zones, and confidence remains unencrypted in app-private storage.
- Production opens an empty database without synthetic records. Only debug builds offer explicit synthetic seeding and on-screen record counts, with no content logging or export. Saved-record screens are read-only; no autonomous memory or behavioral inference is enabled.
- Local memory search decrypts a bounded metadata-filtered candidate set and builds an isolated in-memory FTS4 index that is closed after each query. Search text/results are transient ViewModel state, never SavedStateHandle or a disk search mirror. Physical v1 -> v2 migration preserves encryption; deleting a task or schedule also removes explicitly linked memory content in the same transaction.
- Release artifacts are unsigned development outputs, not a store-ready application.
- Task prerequisites store only structured UUID links and record metadata in schema v3. The repository checks key readability before observation or mutation; deleting either task cascades its links. The explicit upgrade leaves encrypted personal fields unchanged and creates no inferred prerequisites. No device permission or planner/action capability is added.

## Future capabilities

Additional reminders, calendar context, and behavioral statistics must remain local when enabled in later phases. Sensitive fields must retain Keystore-backed encryption. Contact/calendar/microphone/notification access is requested progressively for an enabled feature, with denial paths. Behavioral analysis and dataset contribution require explicit consent; contribution must redact private content and identifiers. No default private telemetry.

Opening a dialer, maps app, calendar editor, or messaging composer hands selected information to another app only after the applicable policy/confirmation checks. That app's privacy policy applies after the handoff. System speech recognition is not assumed offline: use on-device support where available and require opt-in for any online fallback.

Encrypted export/import and synchronization are not implemented. Neither raw data nor derived data is sent to a cloud service by default. Device-specific backup behavior and storage protections require further device testing before production; this scaffold does not claim forensic secure deletion or full production security certification.

The [product contract](docs/PRODUCT_CONTRACT.md) and [database contract](docs/contracts/DATABASE_V1.md) define the requirements for future phases. Update this document whenever actual data handling changes.
