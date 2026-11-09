#!/usr/bin/env python3
"""
Convert Markdown schedule plans (weekday tables + Rules section) into METIS timetable JSON.

Usage:
    python tools/md_to_schedule_json.py --input path/to/plan.md --output path/to/timetable.json --pretty
"""

import argparse
import json
import re
import sys
import uuid

WEEKDAY_MAP = {
    "mon": 1, "monday": 1, "somwar": 1, "somvaar": 1,
    "tue": 2, "tues": 2, "tuesday": 2, "mangalwar": 2, "mangalvaar": 2,
    "wed": 3, "wednesday": 3, "budhwar": 3, "budhvaar": 3,
    "thu": 4, "thur": 4, "thurs": 4, "thursday": 4, "guruwar": 4, "guruvaar": 4, "veervaar": 4,
    "fri": 5, "friday": 5, "shukrawar": 5, "shukravaar": 5,
    "sat": 6, "saturday": 6, "shaniwar": 6, "shanivaar": 6,
    "sun": 7, "sunday": 7, "ravivar": 7, "ravivaar": 7, "itwar": 7,
}

def parse_time_str(time_str: str):
    """Normalize a time string to HH:mm (24-hour)."""
    clean = time_str.strip()
    match = re.match(r"^(\d{1,2}):(\d{2})\s*(am|pm)?$", clean, re.IGNORECASE)
    if not match:
        raise ValueError(f"Invalid time format: {time_str}")
    h, m, ampm = int(match.group(1)), int(match.group(2)), match.group(3)
    if ampm:
        ampm = ampm.lower()
        if ampm == "pm" and h < 12:
            h += 12
        elif ampm == "am" and h == 12:
            h = 0
    return f"{h:02d}:{m:02d}"

def parse_weekdays_spec(spec: str):
    """Parse weekdays string (e.g. 'Mon-Fri', 'all', 'Mon, Wed, Fri') into a list of 1..7 ints."""
    s = spec.strip().lower()
    if s in ("all", "daily", "everyday"):
        return [1, 2, 3, 4, 5, 6, 7]
    if s in ("weekdays", "mon-fri", "mon to fri"):
        return [1, 2, 3, 4, 5]
    if s in ("weekends", "sat-sun", "sat to sun"):
        return [6, 7]
    
    # Handle ranges like mon-thu
    range_match = re.match(r"^([a-z]+)\s*-\s*([a-z]+)$", s)
    if range_match:
        start_day = WEEKDAY_MAP.get(range_match.group(1))
        end_day = WEEKDAY_MAP.get(range_match.group(2))
        if start_day and end_day and start_day <= end_day:
            return list(range(start_day, end_day + 1))

    days = []
    tokens = re.split(r"[,;\s]+", s)
    for token in tokens:
        if not token:
            continue
        d = WEEKDAY_MAP.get(token)
        if d and d not in days:
            days.append(d)
    return sorted(days) if days else [1, 2, 3, 4, 5]

def parse_markdown_schedule(md_content: str):
    blocks = []
    rules = []
    
    lines = md_content.splitlines()
    current_weekday = None
    in_rules = False

    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue

        # Check section headers
        header_match = re.match(r"^#{1,3}\s+(.+)$", stripped)
        if header_match:
            section_name = header_match.group(1).strip().lower()
            if "rule" in section_name:
                in_rules = True
                current_weekday = None
                continue
            else:
                matched_day = None
                for name, d in WEEKDAY_MAP.items():
                    if name in section_name:
                        matched_day = d
                        break
                if matched_day:
                    current_weekday = matched_day
                    in_rules = False
                    continue

        if in_rules:
            # Bullet rule: - gym: 17:00 - 18:30 (weekdays: Mon-Fri, hardness: SOFT, note: Workout)
            bullet_match = re.match(r"^[-*]\s*([a-zA-Z0-9_-]+)\s*:\s*(\d{1,2}:\d{2}(?:\s*[ap]m)?)\s*-\s*(\d{1,2}:\d{2}(?:\s*[ap]m)?)(?:\s*\((.*?)\))?$", stripped, re.IGNORECASE)
            if bullet_match:
                key = bullet_match.group(1).strip()
                start_t = parse_time_str(bullet_match.group(2))
                end_t = parse_time_str(bullet_match.group(3))
                meta_str = bullet_match.group(4) or ""
                
                weekdays = [1, 2, 3, 4, 5]
                hardness = "SOFT"
                note = None

                for part in meta_str.split(","):
                    p = part.strip()
                    if ":" in p:
                        k, v = p.split(":", 1)
                        k, v = k.strip().lower(), v.strip()
                        if "weekday" in k:
                            weekdays = parse_weekdays_spec(v)
                        elif "hard" in k:
                            hardness = v.upper() if v.upper() in ("SOFT", "FIRM") else "SOFT"
                        elif "note" in k:
                            note = v

                rules.append({
                    "id": str(uuid.uuid4()),
                    "key": key,
                    "start": start_t,
                    "end": end_t,
                    "weekdays": weekdays,
                    "hardness": hardness,
                    "note": note
                })
                continue

            # Table row rule: | Key | Window | Weekdays | Hardness | Note |
            if stripped.startswith("|") and not stripped.startswith("|---"):
                cells = [c.strip() for c in stripped.strip("|").split("|")]
                if len(cells) >= 2 and cells[0].lower() not in ("key", "rule", "name"):
                    key = cells[0]
                    window = cells[1]
                    w_match = re.match(r"(\d{1,2}:\d{2}(?:\s*[ap]m)?)\s*-\s*(\d{1,2}:\d{2}(?:\s*[ap]m)?)", window, re.IGNORECASE)
                    if w_match:
                        start_t = parse_time_str(w_match.group(1))
                        end_t = parse_time_str(w_match.group(2))
                        weekdays = parse_weekdays_spec(cells[2]) if len(cells) > 2 and cells[2] else [1, 2, 3, 4, 5]
                        hardness = cells[3].upper() if len(cells) > 3 and cells[3].upper() in ("SOFT", "FIRM") else "SOFT"
                        note = cells[4] if len(cells) > 4 and cells[4] else None
                        rules.append({
                            "id": str(uuid.uuid4()),
                            "key": key,
                            "start": start_t,
                            "end": end_t,
                            "weekdays": weekdays,
                            "hardness": hardness,
                            "note": note
                        })
                continue

        if current_weekday is not None:
            # Table row block: | Time | Subject | Kind | Fixed | Tag |
            if stripped.startswith("|") and not stripped.startswith("|---"):
                cells = [c.strip() for c in stripped.strip("|").split("|")]
                if len(cells) >= 2 and not any(h in cells[0].lower() for h in ("time", "window", "start")):
                    time_cell = cells[0]
                    t_match = re.match(r"(\d{1,2}:\d{2}(?:\s*[ap]m)?)\s*-\s*(\d{1,2}:\d{2}(?:\s*[ap]m)?)", time_cell, re.IGNORECASE)
                    if t_match:
                        start_t = parse_time_str(t_match.group(1))
                        end_t = parse_time_str(t_match.group(2))
                        title = cells[1]
                        kind = cells[2].upper() if len(cells) > 2 and cells[2].upper() in ("CLASS", "LAB", "ROUTINE", "FREE") else "CLASS"
                        fixed = True
                        if len(cells) > 3:
                            f_val = cells[3].strip().lower()
                            if f_val in ("no", "false", "0", "soft"):
                                fixed = False
                        tag = cells[4] if len(cells) > 4 and cells[4] else None

                        blocks.append({
                            "id": str(uuid.uuid4()),
                            "weekday": current_weekday,
                            "start": start_t,
                            "end": end_t,
                            "title": title,
                            "kind": kind,
                            "fixed": fixed,
                            "tag": tag
                        })

    return {
        "version": 1,
        "blocks": blocks,
        "rules": rules
    }

def main():
    parser = argparse.ArgumentParser(description="Convert Markdown timetable to JSON schema.")
    parser.add_argument("-i", "--input", required=True, help="Path to input Markdown file")
    parser.add_argument("-o", "--output", help="Path to output JSON file (default: stdout)")
    parser.add_argument("--pretty", action="store_true", help="Pretty print JSON")
    args = parser.parse_args()

    with open(args.input, "r", encoding="utf-8") as f:
        content = f.read()

    result = parse_markdown_schedule(content)
    indent = 2 if args.pretty else None
    json_str = json.dumps(result, indent=indent)

    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(json_str + "\n")
        print(f"Successfully converted {len(result['blocks'])} blocks and {len(result['rules'])} rules to {args.output}")
    else:
        sys.stdout.write(json_str + "\n")

if __name__ == "__main__":
    main()

