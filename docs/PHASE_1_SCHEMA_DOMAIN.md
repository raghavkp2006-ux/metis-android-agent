# Schema v7: explicit discipline domain

This phase adds storage and domain APIs only. It does not add UI or change chat/agent code, behavioral-analysis consent, or the existing inferred `habits` table.

## Schema and encryption

- `habit_definitions`: UUID identity, created/updated epoch milliseconds and optimistic revision; encrypted name and nullable description; cadence; active flag; strict `start_local_date` and nullable exclusive `archived_at` local dates.
- `daily_checkins`: UUID identity and revision metadata; unique strict `local_date`; captured `zone_id`; nullable encrypted mood (integer 1..5 encoded as text inside the field cipher) and note.
- `habit_ticks`: UUID identity; cascading check-in/habit-definition foreign keys; completed boolean; unique `(checkin_id, habit_id)` pair. Tick writes use the check-in's revision.

`DisciplineMigration.FROM_6_TO_7` only creates these tables, indexes and their own triggers. It neither rebuilds nor alters the 22 v6 tables or their triggers. Room generates the committed `app/schemas/dev.metis.agent.data.storage.PersonalDatabase/7.json`; old schemas remain unchanged. New databases install the same new constraints through the existing creation callback.

`HabitCadence.DAILY` stores 0. Nonempty weekday masks store 1..127, with Monday in bit 0 and Sunday in bit 6. Empty weekday sets and all other values are rejected. Cadence, active/archive metadata, dates, IDs, links, timestamps and completion flags are plaintext app-private metadata; see [PRIVACY.md](../PRIVACY.md) for what it exposes. Sensitive fields use the existing `FieldCipher` and table/row/column AES-GCM bindings, with no plaintext mirror.

## Dates and history

- All persisted local dates parse strictly as four-digit-year `yyyy-MM-dd`, year 0001..9999. SQL triggers also reject malformed or invalid Gregorian dates.
- Check-ins can be written for today or the previous seven **local calendar days**, inclusive; future entries are rejected. The repository captures/validates the current system zone when a day is first inserted. Its date and zone cannot subsequently be rewritten.
- Habit definitions may start in the future. Expected days are dates on/after start, before the exclusive archive date, matching cadence. The function depends on stored local dates, never the current zone or elapsed 24-hour periods. An archived definition still contributes to its historical window.
- Start dates and cadence are immutable on an existing definition so updates cannot rewrite historical expectations. Ordinary updates rename/edit description only.
- Archive computes `max(today in the current zone, latest recorded tick local_date + 1)`, including false/completed=false tick records. It rejects a result at/before the start; the caller can explicitly delete that definition instead. Archive does not silently delete data.
- There is no unarchive in v1. Restore requires an archived source and its revision, and creates a fresh UUID with start=today and revision=0, preserving the original definition and ticks.
- `DisciplineDateRange` is inclusive and limited to 400 days, covering the 53-week graph. The DAO applies date predicates before fetching/decrypting results. Mood statistics, when introduced, must operate on these bounded results in application code.

## Writes and concurrency

`LocalPersonalRepository.discipline` exposes `DisciplineRepository`. It also supports direct construction with a fixed clock and current-zone provider for deterministic tests.

- `saveHabit` inserts or renames an active definition with an expected metadata revision.
- `saveCheckin` saves optional mood/note and patches the supplied completion map in one transaction; omitted ticks remain unchanged. Each supplied habit must be expected on that date.
- `setTick` accepts an explicit expected check-in revision. It updates the existing pair rather than replacing the row identity, and increments the check-in revision alongside the mood/note/tick set.
- A stale revision fails before mutation. SQL compare-and-set updates and Room transactions serialize competing writers; a failed tick rolls back the mood, revision and all earlier tick patches in that transaction.
- Deleting a check-in cascades its ticks. Deleting a habit cascades its ticks and increments the affected check-in revisions so stale day snapshots cannot overwrite that change. Other definitions and days remain intact.
- A bounded key-readability probe includes new discipline ciphertext. It protects both new APIs and writes through older repositories when only new encrypted records exist. Wrong/missing keys fail without replacement keys, clearing records or returning substitute empty data.

## Verification map

- `DisciplineDomainTest`: strict dates, optional mood bounds, text/zone validation, Monday..Sunday masks, start/archive/cadence expectations, archive-at/before-start rejection, seven-day/future validation, captured-zone day boundaries, DST/calendar arithmetic, leap/year edges and bounded query ranges.
- `DisciplineStorageTest`: encrypted round trips, duplicate day, repository date limits, range isolation, optimistic conflicts and concurrent tick updates, atomic rollback, both cascade directions, archive/restore identities, timezone-stable history and wrong/missing-key failures (including older repository writes).
- `DisciplineIntegrityTest`: expected-window/cadence tick admission, historical archive backfill, SQL pair uniqueness/FKs/flags/date validation, immutable dates/archive windows and authenticated field-swap rejection.
- `DisciplineMigrationTest`: a populated v6 fixture with 23 rows across **all 22** existing tables, old constraint triggers and foreign-key links. The test compares every stored value (BLOBs byte-for-byte), every old table/index/trigger SQL definition, and all links before/after migration; validates Room's v7 export; and checks the three new tables start empty. It also validates a fresh v7 export and new date/archive constraints.

No existing test is deleted or newly skipped. Legacy-schema tests retain their old helper validation and update only the expected current version/table count after opening through the v7 fixture. Physical-device preservation is not claimed by synthetic emulator results.
