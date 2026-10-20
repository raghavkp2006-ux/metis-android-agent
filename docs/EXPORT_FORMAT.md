# METIS discipline export format

The `.metis` file is a password-encrypted backup of only the explicit discipline data in schema v7: `habit_definitions`, `daily_checkins`, and `habit_ticks`. It does not include inferred `habits`, consent settings, or transient insights.

## Container version 1

The file is a fixed header followed by one AES-GCM ciphertext. No payload fields, labels, counts, names, dates, moods, notes, or completion data appear outside the ciphertext.

| Offset | Size | Value |
| --- | ---: | --- |
| 0 | 8 bytes | ASCII magic `METISEXP` |
| 8 | 2 bytes | Unsigned big-endian container version (`1`) |
| 10 | 4 bytes | Big-endian PBKDF2 iteration count (`600000` for version 1) |
| 14 | 16 bytes | Random salt |
| 30 | 12 bytes | Random AES-GCM nonce |
| 42 | remaining | AES-256-GCM ciphertext followed by the 16-byte authentication tag |

The complete 42-byte header is AES-GCM additional authenticated data. Changing the version, work factor, salt, or nonce therefore prevents authentication. Version 1 derives a 256-bit key using PBKDF2-HMAC-SHA256 and the user password. Each export uses a fresh cryptographically random salt and nonce. The minimum password length is 12 characters. The file is capped at 16 MiB; version 1 also caps habits at 2,000, check-ins at 20,000, and ticks at 200,000.

Unknown container versions, invalid work factors, malformed/truncated input, authentication failures, and oversized files are rejected before import. The decrypted payload is held in memory only; it is never staged as a plaintext file.

## Encrypted payload version 1

All integers use big-endian byte order. Strings are UTF-8 preceded by a signed 32-bit byte length; nullable strings start with a one-byte boolean. The payload begins with the four-byte marker `PAY1`, app-version string, schema version (7), export creation time in epoch milliseconds, and a 32-bit habit count. Each habit contains metadata (`id`, created/updated epoch milliseconds, revision), name, nullable description, cadence weekday mask, start date, and nullable exclusive archive date. A 32-bit check-in count follows. Each check-in stores metadata, canonical local date, captured zone ID, a mood-presence flag and optional integer mood, nullable note, and tick count. Each tick stores check-in UUID, habit UUID, completion boolean, and tick UUID. Mood values are restricted to 1–5. Dates use strict `yyyy-MM-dd`; cadence `0` means daily and masks 1–127 represent Monday through Sunday in bits 0–6. Records are validated for unique IDs/days, date/archive rules, foreign keys, tick cadence, strings, counts and total file size before database access for import.

## Import and conflicts

The UI decrypts and validates the entire file before showing a preview. Import applies the staged records in one Room transaction only after explicit confirmation. A habit or tick whose ID already exists is skipped and never overwritten. A check-in is skipped if its ID or unique local day already exists; its ticks are also skipped. A tick whose `(check-in ID, habit ID)` pair already exists is skipped. The completion result reports added and skipped counts. A transaction error rolls back the complete import.

Exports are created from bounded repository date windows of at most 400 inclusive days. No unbounded record list is loaded. The picker uses Android's system document picker (`CreateDocument` / `OpenDocument`) and adds no runtime permission.
