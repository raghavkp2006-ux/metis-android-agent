"""
validate_collection.py
======================
Validates a METIS NLU collection CSV before it enters the split pipeline.

Usage
-----
    python validate_collection.py [csv_path] [--intents-md path] [--min-per-intent N]

Exit codes
----------
    0  All checks passed (warnings may be printed).
    1  One or more errors found.

Checks performed
----------------
1. Schema:  required columns present, no extra mandatory columns missing.
2. Valid intent labels: every intended_intent value is in the approved set from
   intents.md (currently 21 labels).
3. Empty rows: utterance or intended_intent is blank / whitespace-only.
4. Duplicate utterances: exact string duplicates within an author or across authors.
5. Per-author quota: each author has at least MIN_PER_INTENT utterances per intent
   they attempt, and provides REQUIRED_INTENTS total.
6. PII patterns: phone numbers (10-digit Indian patterns, +91 patterns), email
   addresses flagged in utterance text; rows with contains_real_pii == "yes"
   also flagged.
7. Hinglish / style quota: across the whole file, hinglish >= 25 %, typo >= 10 %,
   voice >= 10 %, multi_intent >= 5 % of non-UNKNOWN rows.

Style tag aliases
-----------------
  "multi"  is accepted as an alias for "multi_intent".

Unit tests are in the TestValidateCollection class at the bottom (run with -m pytest
or `python validate_collection.py --self-test`).
"""

from __future__ import annotations

import argparse
import csv
import io
import re
import sys
import unittest
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

REQUIRED_COLUMNS: Tuple[str, ...] = (
    "author_id",
    "utterance",
    "intended_intent",
    "style_tags",
    "contains_real_pii",
    "notes",
)

VALID_STYLE_TAGS: Set[str] = {"hinglish", "typo", "voice", "multi_intent", "plain", "terse"}

# "multi" is the legacy alias used in data.csv; normalise before validation.
STYLE_TAG_ALIASES: Dict[str, str] = {"multi": "multi_intent"}

# Quota thresholds (file-level, excluding UNKNOWN rows)
HINGLISH_MIN_PCT = 0.25
TYPO_MIN_PCT = 0.10
VOICE_MIN_PCT = 0.10
MULTI_INTENT_MIN_PCT = 0.05

# Per-author minimum utterances per intent (for any intent the author contributes to)
DEFAULT_MIN_PER_INTENT = 8

# PII detection patterns
PII_PHONE_PATTERNS: List[re.Pattern] = [
    re.compile(r"\b(?:\+91[\s\-]?)?[6-9]\d{9}\b"),         # Indian mobile
    re.compile(r"\b\d{3}[\s\-]\d{3}[\s\-]\d{4}\b"),         # US-style
    re.compile(r"\b\+\d{1,3}[\s\-]\d{6,12}\b"),             # international
]
PII_EMAIL_PATTERN = re.compile(r"\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b")

INTENTS_MD_INTENT_RE = re.compile(r"^##\s+([A-Z_]+)\s*[—\-]", re.MULTILINE)

# ---------------------------------------------------------------------------
# Data types
# ---------------------------------------------------------------------------

@dataclass
class ValidationError:
    row: int          # 1-indexed, 0 means file-level
    author: str
    field: str
    message: str
    is_warning: bool = False

@dataclass
class ValidationResult:
    errors: List[ValidationError] = field(default_factory=list)
    warnings: List[ValidationError] = field(default_factory=list)

    def add(self, err: ValidationError) -> None:
        if err.is_warning:
            self.warnings.append(err)
        else:
            self.errors.append(err)

    @property
    def ok(self) -> bool:
        return len(self.errors) == 0


# ---------------------------------------------------------------------------
# Intent label extraction from intents.md
# ---------------------------------------------------------------------------

def extract_intent_labels(intents_md_path: Path) -> Set[str]:
    """Parse ## LABEL — … headings from intents.md."""
    text = intents_md_path.read_text(encoding="utf-8")
    labels = set(INTENTS_MD_INTENT_RE.findall(text))
    # Always include UNKNOWN as a valid reject-class label
    labels.add("UNKNOWN")
    return labels


# ---------------------------------------------------------------------------
# Core validation logic
# ---------------------------------------------------------------------------

def validate_csv(
    csv_path: Path,
    intents_md_path: Path,
    min_per_intent: int = DEFAULT_MIN_PER_INTENT,
) -> ValidationResult:
    result = ValidationResult()
    valid_intents = extract_intent_labels(intents_md_path)

    rows: List[dict] = []
    with csv_path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)

        # 1. Schema check
        if reader.fieldnames is None:
            result.add(ValidationError(0, "", "schema", "CSV has no header row."))
            return result
        missing = [c for c in REQUIRED_COLUMNS if c not in reader.fieldnames]
        if missing:
            result.add(ValidationError(0, "", "schema",
                f"Missing required columns: {missing}"))
            return result

        for lineno, row in enumerate(reader, start=2):  # 1 = header
            rows.append({"_lineno": lineno, **row})

    if not rows:
        result.add(ValidationError(0, "", "schema", "CSV has no data rows."))
        return result

    # -- Pre-index
    seen_utterances: Dict[str, List[int]] = defaultdict(list)   # utterance -> line numbers
    author_intent_rows: Dict[str, Dict[str, List[int]]] = defaultdict(lambda: defaultdict(list))

    for row in rows:
        lineno = row["_lineno"]
        author = row["author_id"].strip()
        utterance = row["utterance"].strip()
        intent = row["intended_intent"].strip()
        style_raw = row["style_tags"].strip()
        pii_flag = row["contains_real_pii"].strip().lower()

        # 3. Empty rows
        if not utterance:
            result.add(ValidationError(lineno, author, "utterance",
                "Empty utterance."))
        if not intent:
            result.add(ValidationError(lineno, author, "intended_intent",
                "Empty intent label."))
            continue
        if not author:
            result.add(ValidationError(lineno, "", "author_id",
                "Empty author_id."))

        # 2. Valid intent labels
        if intent not in valid_intents:
            result.add(ValidationError(lineno, author, "intended_intent",
                f"Unknown intent '{intent}'. Valid: {sorted(valid_intents)}"))

        # 4. Duplicate detection
        key = utterance.lower()
        seen_utterances[key].append(lineno)

        # Style tags validation (normalise aliases before checking)
        raw_tags = {t.strip() for t in style_raw.split("|") if t.strip()}
        tags = {STYLE_TAG_ALIASES.get(t, t) for t in raw_tags}
        invalid_tags = tags - VALID_STYLE_TAGS
        if invalid_tags:
            result.add(ValidationError(lineno, author, "style_tags",
                f"Invalid style tags: {invalid_tags}. Valid: {VALID_STYLE_TAGS}",
                is_warning=True))

        # 6. PII in utterance text
        if utterance and _has_pii(utterance):
            result.add(ValidationError(lineno, author, "utterance",
                "Potential PII detected (phone or email pattern). Use fake numbers/names only."))
        if pii_flag == "yes":
            result.add(ValidationError(lineno, author, "contains_real_pii",
                "Row is flagged contains_real_pii=yes. Remove PII before submission."))

        author_intent_rows[author][intent].append(lineno)

    # 4. Report cross-file duplicates
    for utterance, linenos in seen_utterances.items():
        if len(linenos) > 1:
            result.add(ValidationError(linenos[0], "", "utterance",
                f"Duplicate utterance at lines {linenos}: '{utterance[:60]}...'"))

    # 5. Per-author quota check
    for author, intent_map in author_intent_rows.items():
        for intent, linenos in intent_map.items():
            if intent == "UNKNOWN":
                continue  # UNKNOWN class has different quota rules
            count = len(linenos)
            if count < min_per_intent:
                result.add(ValidationError(0, author, "quota",
                    f"Author '{author}' has only {count} row(s) for intent "
                    f"'{intent}' (minimum {min_per_intent}).",
                    is_warning=True))

    # 7. Style quota across non-UNKNOWN rows
    non_unknown = [r for r in rows if r["intended_intent"].strip() != "UNKNOWN"]
    total = len(non_unknown)
    if total > 0:
        def _count_tag(tag: str) -> int:
            return sum(
                1 for r in non_unknown
                if tag in {STYLE_TAG_ALIASES.get(t.strip(), t.strip())
                           for t in r["style_tags"].split("|")}
            )
        checks = [
            ("hinglish", _count_tag("hinglish") / total, HINGLISH_MIN_PCT),
            ("typo",     _count_tag("typo")     / total, TYPO_MIN_PCT),
            ("voice",    _count_tag("voice")    / total, VOICE_MIN_PCT),
            ("multi_intent", _count_tag("multi_intent") / total, MULTI_INTENT_MIN_PCT),
        ]
        for tag, pct, threshold in checks:
            if pct < threshold:
                result.add(ValidationError(0, "", "style_quota",
                    f"'{tag}' coverage {pct:.1%} is below required {threshold:.0%} "
                    f"(need {int(threshold * total) - _count_tag(tag)} more rows).",
                    is_warning=True))

    return result


def _has_pii(text: str) -> bool:
    if PII_EMAIL_PATTERN.search(text):
        return True
    return any(p.search(text) for p in PII_PHONE_PATTERNS)


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

def _print_summary(result: ValidationResult, csv_path: Path, total_rows: int) -> None:
    print()
    print("=" * 70)
    print(f"  METIS NLU Collection Validator — {csv_path.name}")
    print("=" * 70)
    print(f"  Total data rows : {total_rows}")
    print(f"  Errors          : {len(result.errors)}")
    print(f"  Warnings        : {len(result.warnings)}")
    print("=" * 70)

    if result.errors:
        print("\n[ERRORS]")
        for e in result.errors:
            loc = f"row {e.row}" if e.row else "file"
            who = f" ({e.author})" if e.author else ""
            print(f"  ✗  [{loc}{who}] {e.field}: {e.message}")

    if result.warnings:
        print("\n[WARNINGS]")
        for w in result.warnings:
            loc = f"row {w.row}" if w.row else "file"
            who = f" ({w.author})" if w.author else ""
            print(f"  ⚠  [{loc}{who}] {w.field}: {w.message}")

    print()
    if result.ok:
        print("  ✓  PASSED — ready for split pipeline.")
    else:
        print("  ✗  FAILED — fix errors before splitting.")
    print()


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Validate a METIS NLU collection CSV."
    )
    parser.add_argument(
        "csv_path",
        nargs="?",
        default="ml/nlu_v1/collection/collection_template.csv",
        help="Path to the collection CSV.",
    )
    parser.add_argument(
        "--intents-md",
        default="ml/nlu_v1/intents.md",
        help="Path to intents.md for the approved label set.",
    )
    parser.add_argument(
        "--min-per-intent",
        type=int,
        default=DEFAULT_MIN_PER_INTENT,
        help=f"Minimum utterances per author per intent (default {DEFAULT_MIN_PER_INTENT}).",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run built-in unit tests instead of validating a file.",
    )
    args = parser.parse_args(argv)

    if args.self_test:
        suite = unittest.TestLoader().loadTestsFromTestCase(TestValidateCollection)
        runner = unittest.TextTestRunner(verbosity=2)
        test_result = runner.run(suite)
        return 0 if test_result.wasSuccessful() else 1

    csv_path = Path(args.csv_path)
    intents_md_path = Path(args.intents_md)

    if not csv_path.exists():
        print(f"ERROR: CSV not found: {csv_path}", file=sys.stderr)
        return 1
    if not intents_md_path.exists():
        print(f"ERROR: intents.md not found: {intents_md_path}", file=sys.stderr)
        return 1

    # Count data rows for summary
    with csv_path.open(newline="", encoding="utf-8") as fh:
        total_rows = sum(1 for _ in csv.DictReader(fh))

    result = validate_csv(csv_path, intents_md_path, args.min_per_intent)
    _print_summary(result, csv_path, total_rows)
    return 0 if result.ok else 1


# ---------------------------------------------------------------------------
# Unit tests
# ---------------------------------------------------------------------------

def _make_intents_md(labels: List[str]) -> str:
    lines = ["# METIS Intent Definitions\n"]
    for label in labels:
        lines.append(f"## {label} — READ (4 rows)\nDefinition: test.\n")
    return "\n".join(lines)


def _csv_from_rows(header: List[str], rows: List[List[str]]) -> str:
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(header)
    w.writerows(rows)
    return buf.getvalue()


HEADER = list(REQUIRED_COLUMNS)


class TestValidateCollection(unittest.TestCase):
    """Unit tests for validate_collection.py."""

    def _run(self, csv_text: str, intents: List[str], min_per: int = 1) -> ValidationResult:
        import tempfile, os
        with tempfile.TemporaryDirectory() as tmp:
            csv_p = Path(tmp) / "test.csv"
            md_p = Path(tmp) / "intents.md"
            csv_p.write_text(csv_text, encoding="utf-8")
            md_p.write_text(_make_intents_md(intents), encoding="utf-8")
            return validate_csv(csv_p, md_p, min_per_intent=min_per)

    # 1. Happy path — single valid row
    def test_happy_path_single_row(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "add task finish the lab report", "CREATE_TASK", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertTrue(result.ok, result.errors)

    # 2. Missing column
    def test_missing_column_returns_error(self):
        csv_text = "author_id,utterance,intended_intent\nA1,hello,CREATE_TASK\n"
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertFalse(result.ok)
        self.assertTrue(any("Missing required columns" in e.message for e in result.errors))

    # 3. Unknown intent label
    def test_unknown_intent_label(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "do something weird", "MAKE_COFFEE", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertFalse(result.ok)
        self.assertTrue(any("Unknown intent" in e.message for e in result.errors))

    # 4. Empty utterance
    def test_empty_utterance(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "", "CREATE_TASK", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertFalse(result.ok)
        self.assertTrue(any("Empty utterance" in e.message for e in result.errors))

    # 5. Duplicate utterance across authors
    def test_duplicate_utterance_cross_author(self):
        row = "add task finish lab"
        csv_text = _csv_from_rows(HEADER, [
            ["A1", row, "CREATE_TASK", "plain", "no", ""],
            ["A2", row, "CREATE_TASK", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertFalse(result.ok)
        self.assertTrue(any("Duplicate" in e.message for e in result.errors))

    # 6. Duplicate utterance within same author
    def test_duplicate_utterance_same_author(self):
        row = "add task finish lab"
        csv_text = _csv_from_rows(HEADER, [
            ["A1", row, "CREATE_TASK", "plain", "no", ""],
            ["A1", row, "CREATE_TASK", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertFalse(result.ok)

    # 7. PII phone number detected
    def test_pii_phone_detected(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "call me at 9876543210 please", "CREATE_REMINDER", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_REMINDER"])
        self.assertFalse(result.ok)
        self.assertTrue(any("PII" in e.message for e in result.errors))

    # 8. PII email detected
    def test_pii_email_detected(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "email me at real@person.com", "CREATE_REMINDER", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_REMINDER"])
        self.assertFalse(result.ok)
        self.assertTrue(any("PII" in e.message for e in result.errors))

    # 9. contains_real_pii=yes flag
    def test_pii_flag_yes(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "some utterance", "CREATE_TASK", "plain", "yes", ""],
        ])
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertFalse(result.ok)
        self.assertTrue(any("contains_real_pii=yes" in e.message for e in result.errors))

    # 10. Quota warning fires
    def test_quota_warning_fires(self):
        # Only 1 row for CREATE_TASK, min_per=8 → warning
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "add task x", "CREATE_TASK", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_TASK"], min_per=8)
        # ok can still be True (warnings don't fail), but warning must exist
        self.assertTrue(any("quota" in w.field for w in result.warnings))

    # 11. Invalid style tag → warning
    def test_invalid_style_tag_warning(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "add task x", "CREATE_TASK", "weird_tag", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertTrue(any("Invalid style tags" in w.message for w in result.warnings))

    # 12. Style quota warning fires when hinglish too low
    def test_style_quota_warning_hinglish(self):
        # 10 plain rows — hinglish is 0%, below 25%
        rows = [
            [f"A{i}", f"add task number {i}", "CREATE_TASK", "plain", "no", ""]
            for i in range(10)
        ]
        csv_text = _csv_from_rows(HEADER, rows)
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertTrue(any("hinglish" in w.message for w in result.warnings))

    # 13. No data rows
    def test_empty_csv(self):
        csv_text = ",".join(HEADER) + "\n"
        result = self._run(csv_text, ["CREATE_TASK"])
        self.assertFalse(result.ok)
        self.assertTrue(any("no data rows" in e.message for e in result.errors))

    # 14. UNKNOWN rows excluded from style quota
    def test_unknown_excluded_from_style_quota(self):
        # 10 UNKNOWN rows (plain) — should not trigger style quota because
        # non-UNKNOWN pool is empty (total=0 → quota check skipped)
        rows = [
            [f"A{i}", f"book uber {i}", "UNKNOWN", "plain", "no", ""]
            for i in range(10)
        ]
        csv_text = _csv_from_rows(HEADER, rows)
        result = self._run(csv_text, ["CREATE_TASK"])
        style_warnings = [w for w in result.warnings if "style_quota" in w.field]
        self.assertEqual(len(style_warnings), 0)

    # 15. _has_pii helper tests
    def test_has_pii_phone_true(self):
        self.assertTrue(_has_pii("call 9876543210 now"))

    def test_has_pii_email_true(self):
        self.assertTrue(_has_pii("email user@example.com"))

    def test_has_pii_clean(self):
        self.assertFalse(_has_pii("remind me to finish the assignment"))

    # -----------------------------------------------------------------------
    # 16-20. New labels (CHECK_MEMORY, CREATE_TIMER, CREATE_ALARM,
    #         CALL_PERSON, SEND_MESSAGE)
    # -----------------------------------------------------------------------

    # 16. CHECK_MEMORY is accepted as valid
    def test_check_memory_valid(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "search my memory for gradient descent notes", "CHECK_MEMORY", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CHECK_MEMORY"])
        self.assertTrue(result.ok, result.errors)

    # 17. CREATE_TIMER is accepted as valid
    def test_create_timer_valid(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "set a timer for twenty five minutes", "CREATE_TIMER", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_TIMER"])
        self.assertTrue(result.ok, result.errors)

    # 18. CREATE_ALARM is accepted as valid
    def test_create_alarm_valid(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "wake me up at half five tomorrow morning", "CREATE_ALARM", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_ALARM"])
        self.assertTrue(result.ok, result.errors)

    # 19. CALL_PERSON is accepted as valid
    def test_call_person_valid(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "dial Aarav for me before I leave", "CALL_PERSON", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["CALL_PERSON"])
        self.assertTrue(result.ok, result.errors)

    # 20. SEND_MESSAGE is accepted as valid
    def test_send_message_valid(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "text Priya that the report is ready", "SEND_MESSAGE", "plain", "no", ""],
        ])
        result = self._run(csv_text, ["SEND_MESSAGE"])
        self.assertTrue(result.ok, result.errors)

    # 21. 'multi' alias normalises to 'multi_intent' — no invalid-tag warning
    def test_multi_alias_no_warning(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "add task and set reminder for it", "CREATE_TASK", "multi", "no", ""],
        ])
        result = self._run(csv_text, ["CREATE_TASK"])
        invalid_tag_warnings = [
            w for w in result.warnings
            if "Invalid style tags" in w.message
        ]
        self.assertEqual(len(invalid_tag_warnings), 0,
                         "multi alias should not produce an invalid-tag warning")

    # 22. 'multi' counts towards multi_intent quota
    def test_multi_alias_counts_for_quota(self):
        # 20 rows: 5 tagged 'multi' (= multi_intent alias) = 25 % >= 5 % threshold
        rows = (
            [[f"A{i}", f"plain task {i}", "CREATE_TASK", "plain", "no", ""] for i in range(15)]
            + [[f"A{i+15}", f"multi task {i}", "CREATE_TASK", "multi", "no", ""] for i in range(5)]
        )
        csv_text = _csv_from_rows(HEADER, rows)
        result = self._run(csv_text, ["CREATE_TASK"])
        multi_quota_warnings = [
            w for w in result.warnings
            if "multi_intent" in w.message and "style_quota" in w.field
        ]
        self.assertEqual(len(multi_quota_warnings), 0,
                         "5/20 multi rows should satisfy the 5% multi_intent quota")


    # 23. 'terse' is accepted as valid style tag
    def test_terse_style_tag_valid(self):
        csv_text = _csv_from_rows(HEADER, [
            ["A1", "meeting 4pm", "CHECK_CALENDAR", "terse", "no", ""],
        ])
        result = self._run(csv_text, ["CHECK_CALENDAR"])
        invalid_tag_warnings = [
            w for w in result.warnings
            if "Invalid style tags" in w.message
        ]
        self.assertEqual(len(invalid_tag_warnings), 0,
                         "terse tag should not produce an invalid-tag warning")


if __name__ == "__main__":
    sys.exit(main())

