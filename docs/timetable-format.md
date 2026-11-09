# METIS Timetable & Routine Format Specification

## Overview
METIS supports importing weekly schedules and routine rules from a structured JSON document. The timetable data is imported entirely on-device, validated, and encrypted at rest with hardware-backed Android Keystore keys (AES-256-GCM). METIS does not transmit schedule data over the network and requires zero internet permissions.

---

## JSON Schema Specification

The root JSON object contains:
* `version` (*integer*, required): Document schema version (currently `1`).
* `blocks` (*array*, required): List of recurring weekly schedule blocks (classes, labs, routines, free slots).
* `rules` (*array*, optional): List of soft or firm routine rules (e.g., gym, sleep, reading windows).

### `blocks` Array Item Structure

| Field | Type | Description | Required | Default |
|---|---|---|---|---|
| `id` | string | Unique block identifier (UUID). | No | Auto-generated UUID |
| `weekday` | integer or string | Day of the week: `1` (Monday) through `7` (Sunday), or name (`"monday"`, `"Fri"`, `"somwar"`, etc.). | Yes | — |
| `start` / `startMin` | string or integer | Start time as `"HH:mm"` (24-hour) or minute of day (`0` to `1439`). | Yes | — |
| `end` / `endMin` | string or integer | End time as `"HH:mm"` (24-hour) or minute of day (`1` to `1440`). Must be strictly greater than start. | Yes | — |
| `title` | string | Course or activity title (e.g. `"Mathematics"`, `"Operating Systems Lab"`). **Encrypted at rest.** | Yes | — |
| `kind` | string | Block classification: `"CLASS"`, `"LAB"`, `"ROUTINE"`, `"FREE"`. | No | `"CLASS"` |
| `fixed` | boolean | If `true`, this block is a hard commitment and blocks planner tasks and `FREE_AT` queries. | No | `true` |
| `tag` | string | Optional category tag (e.g. `"lecture"`, `"tutorial"`, `"lab"`). **Encrypted at rest.** | No | `null` |

### `rules` Array Item Structure

| Field | Type | Description | Required | Default |
|---|---|---|---|---|
| `id` | string | Unique rule identifier (UUID). | No | Auto-generated UUID |
| `key` | string | Routine identifier (e.g. `"gym"`, `"library"`, `"walk"`, `"sleep"`). | Yes | — |
| `start` / `windowStartMin` | string or integer | Start time of the routine window (`"HH:mm"` or minute). | Yes | — |
| `end` / `windowEndMin` | string or integer | End time of the routine window (`"HH:mm"` or minute). Must be greater than start. | Yes | — |
| `weekdays` / `weekdaysMask` | array, string, or integer | Target days. Supported formats:<br>• Array of numbers/names: `[1, 2, 3, 4, 5]` or `["mon", "wed", "fri"]`<br>• Preset string: `"mon-fri"`, `"weekdays"`, `"all"`, `"weekends"`<br>• Bitmask: integer `1`..`127` (Monday=1, Tuesday=2, ..., Sunday=64). | Yes | — |
| `hardness` | string | `"SOFT"` (planner prioritizes outside window unless task is overdue) or `"FIRM"`. | No | `"SOFT"` |
| `note` | string | Optional details/instructions. **Encrypted at rest.** | No | `null` |

---

## Validation Rules
When importing, METIS validates:
1. **No Overlapping Fixed Blocks**: Fixed blocks (`fixed: true`) on the same weekday cannot overlap.
2. **End After Start**: `end` must be strictly after `start`.
3. **Valid Weekdays**: Weekday values must map to days 1 through 7.
4. **Line-Level Error Reporting**: In the Settings Import preview, any validation error is displayed with the exact block or rule number and cause.

---

## Full Example JSON (`timetable.json`)

```json
{
  "version": 1,
  "blocks": [
    {
      "weekday": 1,
      "start": "09:00",
      "end": "10:30",
      "title": "Mathematics",
      "kind": "CLASS",
      "fixed": true,
      "tag": "lecture"
    },
    {
      "weekday": 1,
      "start": "11:00",
      "end": "13:00",
      "title": "Physics Lab",
      "kind": "LAB",
      "fixed": true,
      "tag": "lab"
    },
    {
      "weekday": 2,
      "start": "10:00",
      "end": "11:30",
      "title": "Computer Networks",
      "kind": "CLASS",
      "fixed": true,
      "tag": "lecture"
    },
    {
      "weekday": 5,
      "start": "14:00",
      "end": "15:30",
      "title": "Algorithms",
      "kind": "CLASS",
      "fixed": true,
      "tag": "lecture"
    }
  ],
  "rules": [
    {
      "key": "gym",
      "start": "17:00",
      "end": "18:30",
      "weekdays": ["mon", "tue", "wed", "thu", "fri"],
      "hardness": "SOFT",
      "note": "Evening workout"
    },
    {
      "key": "sleep",
      "start": "23:00",
      "end": "07:00",
      "weekdays": "all",
      "hardness": "FIRM",
      "note": "Night rest"
    }
  ]
}
```

---

## Markdown to JSON Conversion
Use the bundled Python tool `tools/md_to_schedule_json.py` to convert Markdown timetable files (with weekday tables and a "Rules" section) into valid timetable JSON.

