# Current Features

For the full feature matrix, see [CORE_FEATURES.md](CORE_FEATURES.md).

## Timetable as Data (`feat/timetable-data`)

- **Schedule Import**: Import recurring weekly schedule blocks (`timetable_blocks`) and routine rules (`routine_rules`) from structured JSON via Android Storage Access Framework (SAF) file picker. Zero `READ_EXTERNAL_STORAGE` or `INTERNET` permissions.
- **Privacy & Encryption**: All text fields (`title`, `tag`, `note`) are encrypted at rest using Android Keystore AES-256-GCM authenticated encryption (`FieldCipher`). The schedule remains entirely on-device; no data is ever transmitted off the device.
- **Atomic Replace & Undo**: Re-importing a schedule replaces the previous timetable atomically within a Room transaction, backing up the previous schedule to allow one-click undo in Settings.
- **Read Queries**:
  - `NEXT_CLASS`: "what is my next class", "next class kab hai", etc.
  - `FREE_AT`: "am I free at 4", "kya main 3 baje free hu", etc.
  - `TODAY_REMAINING`: "what's left today", "aaj kya bacha hai", etc.
  - `DAY_SCHEDULE`: "what do I have on friday", "somwar ka schedule", etc.
  - Empty schedule returns: "No schedule imported."
  - Closed grammar rules only (no ML writes or intent hallucinations).
- **Planner Hookup**:
  - `fixed = true` blocks are treated as hard unavailable time by `PlannerEngine` and `FREE_AT`.
  - `RoutineRule` windows are soft slots that the planner keeps free for tasks, unless a task is overdue. Explanations surface `"kept <key> window free"`.
  - When no schedule is imported, behavior is identical to baseline (0 regression).

