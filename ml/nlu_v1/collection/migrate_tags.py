"""
migrate_tags.py
===============
Rewrites legacy style tags in a METIS NLU collection CSV, writing the result
to a new file without modifying the input.

Currently supported migrations
-------------------------------
  multi  ->  multi_intent

Usage
-----
    python migrate_tags.py <input_csv> [--out <output_csv>]

    --out   Path for the migrated output file.
            Default: <input_stem>_migrated.csv in the same directory.
    --dry-run
            Print a summary of changes without writing any file.

Rules
-----
- The tag rewrite is applied to the `style_tags` column only.
- Tags are pipe-separated. Each tag is trimmed and checked against the alias
  table. Other tags pass through unchanged.
- Existing `multi_intent` tags are left as-is (idempotent).
- If no migration is needed the output file is still written (or reported in
  dry-run) for consistency.
- The input file is never modified.
"""

from __future__ import annotations

import argparse
import csv
import io
import sys
import unittest
from pathlib import Path
from typing import Dict, List, Optional

# ---------------------------------------------------------------------------
# Migration table
# ---------------------------------------------------------------------------

TAG_ALIASES: Dict[str, str] = {
    "multi": "multi_intent",
}

STYLE_TAGS_COLUMN = "style_tags"

REQUIRED_COLUMNS = (
    "author_id",
    "utterance",
    "intended_intent",
    "style_tags",
    "contains_real_pii",
    "notes",
)


# ---------------------------------------------------------------------------
# Core migration
# ---------------------------------------------------------------------------

def _migrate_tags(raw: str) -> tuple[str, bool]:
    """Migrate alias tags in a pipe-separated style_tags string.

    Returns (migrated_string, changed: bool).
    """
    parts = [t.strip() for t in raw.split("|") if t.strip()]
    migrated = [TAG_ALIASES.get(t, t) for t in parts]
    result = "|".join(migrated)
    return result, (parts != migrated)


def migrate_csv(
    input_path: Path,
    output_path: Path,
    dry_run: bool = False,
) -> Dict[str, int]:
    """Read input_path, migrate tags, write to output_path.

    Returns a stats dict:
        {"rows_read": int, "rows_changed": int, "tags_migrated": int}
    """
    rows: List[dict] = []
    fieldnames: List[str] = []
    with input_path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        if reader.fieldnames is None:
            raise ValueError(f"{input_path}: CSV has no header row.")
        fieldnames = list(reader.fieldnames)
        if STYLE_TAGS_COLUMN not in fieldnames:
            raise ValueError(
                f"{input_path}: column '{STYLE_TAGS_COLUMN}' not found. "
                f"Found columns: {fieldnames}"
            )
        rows = list(reader)

    rows_changed = 0
    tags_migrated = 0

    migrated_rows: List[dict] = []
    for row in rows:
        new_row = dict(row)
        raw = row.get(STYLE_TAGS_COLUMN, "")
        new_tags, changed = _migrate_tags(raw)
        if changed:
            new_row[STYLE_TAGS_COLUMN] = new_tags
            rows_changed += 1
            # Count individual tag substitutions
            old_parts = [t.strip() for t in raw.split("|") if t.strip()]
            new_parts = [t.strip() for t in new_tags.split("|") if t.strip()]
            tags_migrated += sum(1 for o, n in zip(old_parts, new_parts) if o != n)
        migrated_rows.append(new_row)

    stats = {
        "rows_read": len(rows),
        "rows_changed": rows_changed,
        "tags_migrated": tags_migrated,
    }

    if dry_run:
        print(f"[dry-run] {input_path.name} -> {output_path.name}")
        print(f"  rows read    : {stats['rows_read']}")
        print(f"  rows changed : {stats['rows_changed']}")
        print(f"  tags migrated: {stats['tags_migrated']}")
        return stats

    with output_path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(migrated_rows)

    print(f"Migrated {stats['rows_changed']} rows ({stats['tags_migrated']} tags) "
          f"-> {output_path}")
    return stats


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Migrate legacy style tags in a METIS NLU collection CSV."
    )
    parser.add_argument(
        "input_csv",
        nargs="?",
        default=None,
        help="Path to the source CSV (will not be modified).",
    )
    parser.add_argument(
        "--out",
        default=None,
        help="Output path. Defaults to <stem>_migrated.csv alongside the input.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Report what would change without writing any file.",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run built-in unit tests.",
    )
    args = parser.parse_args(argv)

    if args.self_test:
        suite = unittest.TestLoader().loadTestsFromTestCase(TestMigrateTags)
        runner = unittest.TextTestRunner(verbosity=2)
        result = runner.run(suite)
        return 0 if result.wasSuccessful() else 1

    if not args.input_csv:
        parser.error("input_csv is required unless --self-test is specified")

    input_path = Path(args.input_csv)
    if not input_path.exists():
        print(f"ERROR: input file not found: {input_path}", file=sys.stderr)
        return 1

    if args.out:
        output_path = Path(args.out)
    else:
        output_path = input_path.with_stem(input_path.stem + "_migrated")

    if output_path == input_path:
        print("ERROR: output path must differ from input path.", file=sys.stderr)
        return 1

    try:
        migrate_csv(input_path, output_path, dry_run=args.dry_run)
    except ValueError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    return 0


# ---------------------------------------------------------------------------
# Unit tests
# ---------------------------------------------------------------------------

def _csv_str(header: List[str], rows: List[List[str]]) -> str:
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(header)
    w.writerows(rows)
    return buf.getvalue()


HEADER = list(REQUIRED_COLUMNS)


class TestMigrateTags(unittest.TestCase):

    def _write_and_migrate(self, csv_text: str, dry_run: bool = False) -> tuple[dict, str]:
        """Write csv_text to a temp file, migrate it, return (stats, output_text)."""
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            inp = Path(tmp) / "input.csv"
            out = Path(tmp) / "output.csv"
            inp.write_text(csv_text, encoding="utf-8")
            stats = migrate_csv(inp, out, dry_run=dry_run)
            output_text = out.read_text(encoding="utf-8") if out.exists() else ""
        return stats, output_text

    # 1. 'multi' rewrites to 'multi_intent'
    def test_multi_rewritten(self):
        csv_text = _csv_str(HEADER, [
            ["A1", "add task and set reminder", "CREATE_TASK", "multi", "no", ""],
        ])
        stats, output = self._write_and_migrate(csv_text)
        self.assertEqual(stats["rows_changed"], 1)
        self.assertEqual(stats["tags_migrated"], 1)
        self.assertIn("multi_intent", output)
        self.assertNotIn(",multi,", output)

    # 2. 'multi_intent' passes through unchanged
    def test_multi_intent_idempotent(self):
        csv_text = _csv_str(HEADER, [
            ["A1", "some utterance", "CREATE_TASK", "multi_intent", "no", ""],
        ])
        stats, output = self._write_and_migrate(csv_text)
        self.assertEqual(stats["rows_changed"], 0)
        self.assertEqual(stats["tags_migrated"], 0)
        self.assertIn("multi_intent", output)

    # 3. Mixed tags: 'hinglish|multi' -> 'hinglish|multi_intent'
    def test_mixed_tags_migrated(self):
        csv_text = _csv_str(HEADER, [
            ["A1", "hinglish multi row", "CREATE_TASK", "hinglish|multi", "no", ""],
        ])
        stats, output = self._write_and_migrate(csv_text)
        self.assertEqual(stats["rows_changed"], 1)
        self.assertIn("hinglish|multi_intent", output)

    # 4. Plain rows are untouched
    def test_plain_rows_untouched(self):
        csv_text = _csv_str(HEADER, [
            ["A1", "plain utterance", "CREATE_TASK", "plain", "no", ""],
        ])
        stats, output = self._write_and_migrate(csv_text)
        self.assertEqual(stats["rows_changed"], 0)

    # 5. Multiple rows — only multi rows change
    def test_only_multi_rows_change(self):
        csv_text = _csv_str(HEADER, [
            ["A1", "plain row", "CREATE_TASK", "plain", "no", ""],
            ["A2", "multi row", "CREATE_TASK", "multi", "no", ""],
            ["A3", "hinglish row", "CREATE_TASK", "hinglish", "no", ""],
        ])
        stats, _ = self._write_and_migrate(csv_text)
        self.assertEqual(stats["rows_changed"], 1)
        self.assertEqual(stats["rows_read"], 3)

    # 6. Dry run does not write output file
    def test_dry_run_no_write(self):
        import tempfile
        csv_text = _csv_str(HEADER, [
            ["A1", "multi row", "CREATE_TASK", "multi", "no", ""],
        ])
        with tempfile.TemporaryDirectory() as tmp:
            inp = Path(tmp) / "input.csv"
            out = Path(tmp) / "output.csv"
            inp.write_text(csv_text, encoding="utf-8")
            stats = migrate_csv(inp, out, dry_run=True)
            self.assertFalse(out.exists(), "dry-run must not write output file")
        self.assertEqual(stats["rows_changed"], 1)

    # 7. Missing style_tags column raises ValueError
    def test_missing_column_raises(self):
        bad_csv = "author_id,utterance,intended_intent\nA1,hi,CREATE_TASK\n"
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            inp = Path(tmp) / "bad.csv"
            out = Path(tmp) / "out.csv"
            inp.write_text(bad_csv, encoding="utf-8")
            with self.assertRaises(ValueError):
                migrate_csv(inp, out)

    # 8. _migrate_tags helper — single alias
    def test_migrate_tags_single(self):
        result, changed = _migrate_tags("multi")
        self.assertEqual(result, "multi_intent")
        self.assertTrue(changed)

    # 9. _migrate_tags helper — no alias
    def test_migrate_tags_no_change(self):
        result, changed = _migrate_tags("plain")
        self.assertEqual(result, "plain")
        self.assertFalse(changed)

    # 10. _migrate_tags helper — already canonical
    def test_migrate_tags_canonical(self):
        result, changed = _migrate_tags("multi_intent")
        self.assertEqual(result, "multi_intent")
        self.assertFalse(changed)

    # 11. Row count preserved
    def test_row_count_preserved(self):
        rows = [[f"A{i}", f"utt {i}", "CREATE_TASK", "multi", "no", ""] for i in range(20)]
        csv_text = _csv_str(HEADER, rows)
        stats, output = self._write_and_migrate(csv_text)
        reader = list(csv.DictReader(io.StringIO(output)))
        self.assertEqual(len(reader), 20)


if __name__ == "__main__":
    sys.exit(main())

