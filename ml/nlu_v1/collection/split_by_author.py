"""
split_by_author.py
==================
Deterministic author-grouped train / dev / test split for the METIS NLU
collection. No author ever appears in more than one split. Test authors are
never seen during training or validation.

Usage
-----
    python split_by_author.py <validated_csv> [options]

    --train-ratio   Fraction of authors assigned to train (default 0.60)
    --dev-ratio     Fraction of authors assigned to dev   (default 0.20)
    --seed          Random seed (default 42) — kept constant for reproducibility
    --out-dir       Directory to write train/dev/test CSVs + manifest
    --test-authors  Explicit comma-separated list of author IDs to freeze in test
                    (overrides ratio-based selection for test split)

Frozen test set protection
--------------------------
Once a test set manifest (<out-dir>/test_manifest.json) exists, this script
REFUSES to overwrite train.csv, dev.csv, or test.csv unless --force-retrain is
passed.  The test split (test authors) is additionally locked: even with
--force-retrain the test author list is loaded from the manifest and cannot
change without explicit --unlock-test-authors (which prints a loud warning and
requires interactive confirmation).

Manifest format
---------------
{
  "schema_version": "1",
  "created_at": "<ISO-8601>",
  "seed": <int>,
  "test_authors": ["A", "B"],
  "dev_authors": ["C"],
  "train_authors": ["D", "E", "F"],
  "split_sha256": {
    "train": "<sha256 of train.csv>",
    "dev":   "<sha256 of dev.csv>",
    "test":  "<sha256 of test.csv>"
  },
  "row_counts": {"train": <int>, "dev": <int>, "test": <int>}
}
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import random
import sys
import unittest
import io
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

REQUIRED_COLUMNS = (
    "author_id",
    "utterance",
    "intended_intent",
    "style_tags",
    "contains_real_pii",
    "notes",
)

MANIFEST_FILENAME = "test_manifest.json"
SCHEMA_VERSION = "1"

DEFAULT_SEED = 42
DEFAULT_TRAIN_RATIO = 0.60
DEFAULT_DEV_RATIO = 0.20


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def _write_split(rows: List[dict], path: Path) -> None:
    with path.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(REQUIRED_COLUMNS))
        writer.writeheader()
        writer.writerows({k: row.get(k, "") for k in REQUIRED_COLUMNS} for row in rows)


def _load_csv(path: Path) -> List[dict]:
    with path.open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def _group_by_author(rows: List[dict]) -> Dict[str, List[dict]]:
    groups: Dict[str, List[dict]] = {}
    for row in rows:
        author = row["author_id"].strip()
        groups.setdefault(author, []).append(row)
    return groups


# ---------------------------------------------------------------------------
# Split logic
# ---------------------------------------------------------------------------

def assign_authors(
    authors: List[str],
    train_ratio: float,
    dev_ratio: float,
    seed: int,
    forced_test_authors: Optional[List[str]] = None,
) -> Tuple[List[str], List[str], List[str]]:
    """
    Assign each author to exactly one of train / dev / test.

    If forced_test_authors is provided, those authors go directly to test.
    Remaining authors are split by ratio (train / dev); test gets what remains.
    """
    rng = random.Random(seed)
    all_authors = sorted(authors)  # deterministic sort before shuffling

    if forced_test_authors:
        test_set: Set[str] = set(forced_test_authors)
        unknown = test_set - set(all_authors)
        if unknown:
            raise ValueError(f"Forced test authors not found in data: {unknown}")
        remaining = [a for a in all_authors if a not in test_set]
    else:
        test_set = set()
        remaining = list(all_authors)

    rng.shuffle(remaining)

    n = len(remaining)
    if forced_test_authors:
        n_train = round(n * (train_ratio / (train_ratio + dev_ratio)))
    else:
        # No forced test — compute from ratios; test gets leftover
        n_train = math.floor(n * train_ratio)
        n_dev = math.floor(n * dev_ratio)
        # After dev, the rest go to test unless forced_test_authors is set
        test_set = set(remaining[n_train + n_dev:])
        remaining_for_split = remaining[: n_train + n_dev]
        n_train = math.floor(len(remaining_for_split) * (train_ratio / (train_ratio + dev_ratio + 1e-12)))
        train_authors = remaining_for_split[:n_train]
        dev_authors = remaining_for_split[n_train:]
        return sorted(train_authors), sorted(dev_authors), sorted(test_set)

    train_authors = remaining[:n_train]
    dev_authors = remaining[n_train:]
    return sorted(train_authors), sorted(dev_authors), sorted(test_set)


def split_data(
    rows: List[dict],
    train_authors: List[str],
    dev_authors: List[str],
    test_authors: List[str],
) -> Tuple[List[dict], List[dict], List[dict]]:
    train_set = set(train_authors)
    dev_set = set(dev_authors)
    test_set = set(test_authors)
    train, dev, test = [], [], []
    for row in rows:
        a = row["author_id"].strip()
        if a in train_set:
            train.append(row)
        elif a in dev_set:
            dev.append(row)
        elif a in test_set:
            test.append(row)
        # Authors not in any group are silently skipped (should not happen after validate)
    return train, dev, test


# ---------------------------------------------------------------------------
# Manifest I/O
# ---------------------------------------------------------------------------

def _load_manifest(out_dir: Path) -> Optional[dict]:
    p = out_dir / MANIFEST_FILENAME
    if p.exists():
        with p.open(encoding="utf-8") as fh:
            return json.load(fh)
    return None


def _save_manifest(
    out_dir: Path,
    seed: int,
    train_authors: List[str],
    dev_authors: List[str],
    test_authors: List[str],
    row_counts: Dict[str, int],
    split_sha: Dict[str, str],
) -> None:
    manifest = {
        "schema_version": SCHEMA_VERSION,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "seed": seed,
        "test_authors": sorted(test_authors),
        "dev_authors": sorted(dev_authors),
        "train_authors": sorted(train_authors),
        "split_sha256": split_sha,
        "row_counts": row_counts,
    }
    p = out_dir / MANIFEST_FILENAME
    p.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"  Manifest written -> {p}")


# ---------------------------------------------------------------------------
# Main split runner
# ---------------------------------------------------------------------------

def run_split(
    csv_path: Path,
    out_dir: Path,
    train_ratio: float = DEFAULT_TRAIN_RATIO,
    dev_ratio: float = DEFAULT_DEV_RATIO,
    seed: int = DEFAULT_SEED,
    test_authors_override: Optional[List[str]] = None,
    force_retrain: bool = False,
    unlock_test_authors: bool = False,
) -> int:
    """Run the split and write output files. Returns exit code."""
    # Load data
    rows = _load_csv(csv_path)
    if not rows:
        print("ERROR: CSV has no data rows.", file=sys.stderr)
        return 1

    authors = sorted({r["author_id"].strip() for r in rows if r["author_id"].strip()})
    if not authors:
        print("ERROR: No valid author_ids found.", file=sys.stderr)
        return 1

    out_dir.mkdir(parents=True, exist_ok=True)
    existing_manifest = _load_manifest(out_dir)

    # ---- Frozen test set protection ----
    if existing_manifest and not force_retrain:
        print("ERROR: A frozen test set manifest already exists at "
              f"'{out_dir / MANIFEST_FILENAME}'.")
        print("       Pass --force-retrain to regenerate train/dev splits while "
              "preserving the frozen test authors.")
        print("       Pass --force-retrain --unlock-test-authors to reassign test "
              "authors (dangerous — destroys held-out integrity).")
        return 1

    if existing_manifest and force_retrain:
        frozen_test = existing_manifest["test_authors"]
        if test_authors_override and set(test_authors_override) != set(frozen_test):
            if not unlock_test_authors:
                print(
                    "ERROR: --test-authors differs from frozen manifest "
                    f"({sorted(frozen_test)}).\n"
                    "       Pass --unlock-test-authors to override (DESTROYS held-out integrity).",
                    file=sys.stderr,
                )
                return 1
            else:
                print(
                    "WARNING ⚠ : You are changing the frozen test author set. "
                    "This invalidates prior evaluation results.",
                    file=sys.stderr,
                )
                confirm = input("Type 'yes I understand' to continue: ")
                if confirm.strip() != "yes I understand":
                    print("Aborted.")
                    return 1
        else:
            # Reuse frozen test authors
            test_authors_override = frozen_test
            print(f"  Reusing frozen test authors from manifest: {frozen_test}")

    # ---- Author assignment ----
    try:
        train_authors, dev_authors, test_authors = assign_authors(
            authors,
            train_ratio=train_ratio,
            dev_ratio=dev_ratio,
            seed=seed,
            forced_test_authors=test_authors_override,
        )
    except ValueError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    # Sanity check: no overlap
    overlap = set(train_authors) & set(dev_authors) & set(test_authors)
    if overlap:
        print(f"ERROR: Authors appear in multiple splits: {overlap}", file=sys.stderr)
        return 1

    # ---- Split rows ----
    train_rows, dev_rows, test_rows = split_data(rows, train_authors, dev_authors, test_authors)

    print(f"\n  Authors total  : {len(authors)}")
    print(f"  Train authors  : {train_authors} ({len(train_rows)} rows)")
    print(f"  Dev authors    : {dev_authors} ({len(dev_rows)} rows)")
    print(f"  Test authors   : {test_authors} ({len(test_rows)} rows)")

    # ---- Write files ----
    paths = {
        "train": out_dir / "train.csv",
        "dev":   out_dir / "dev.csv",
        "test":  out_dir / "test.csv",
    }
    for split_name, split_rows in [("train", train_rows), ("dev", dev_rows), ("test", test_rows)]:
        _write_split(split_rows, paths[split_name])
        print(f"  Written -> {paths[split_name]}")

    # ---- Manifest ----
    sha = {k: _sha256_file(v) for k, v in paths.items()}
    _save_manifest(
        out_dir,
        seed=seed,
        train_authors=train_authors,
        dev_authors=dev_authors,
        test_authors=test_authors,
        row_counts={k: len(r) for k, r in [("train", train_rows), ("dev", dev_rows), ("test", test_rows)]},
        split_sha=sha,
    )

    print("\n  [OK]  Split complete.")
    return 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Deterministic author-grouped train/dev/test split for METIS NLU."
    )
    parser.add_argument("csv_path", nargs="?",
                        default="ml/nlu_v1/collection/collection_template.csv",
                        help="Path to validated collection CSV.")
    parser.add_argument("--out-dir", default="ml/nlu_v1/collection/splits",
                        help="Output directory for split CSVs and manifest.")
    parser.add_argument("--train-ratio", type=float, default=DEFAULT_TRAIN_RATIO)
    parser.add_argument("--dev-ratio",   type=float, default=DEFAULT_DEV_RATIO)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--test-authors",
                        help="Comma-separated author IDs to freeze in test split.")
    parser.add_argument("--force-retrain", action="store_true",
                        help="Allow overwriting existing train/dev splits.")
    parser.add_argument("--unlock-test-authors", action="store_true",
                        help="Allow changing frozen test authors (dangerous).")
    parser.add_argument("--self-test", action="store_true",
                        help="Run unit tests.")
    args = parser.parse_args(argv)

    if args.self_test:
        suite = unittest.TestLoader().loadTestsFromTestCase(TestSplitByAuthor)
        result = unittest.TextTestRunner(verbosity=2).run(suite)
        return 0 if result.wasSuccessful() else 1

    test_authors = (
        [a.strip() for a in args.test_authors.split(",")]
        if args.test_authors else None
    )

    return run_split(
        csv_path=Path(args.csv_path),
        out_dir=Path(args.out_dir),
        train_ratio=args.train_ratio,
        dev_ratio=args.dev_ratio,
        seed=args.seed,
        test_authors_override=test_authors,
        force_retrain=args.force_retrain,
        unlock_test_authors=args.unlock_test_authors,
    )


# ---------------------------------------------------------------------------
# Unit tests
# ---------------------------------------------------------------------------

def _make_csv(rows: List[List[str]], header: Optional[List[str]] = None) -> str:
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(header or list(REQUIRED_COLUMNS))
    w.writerows(rows)
    return buf.getvalue()


class TestSplitByAuthor(unittest.TestCase):

    def _base_rows(self, authors: List[str], n_per_author: int = 5) -> List[List[str]]:
        rows = []
        for a in authors:
            for i in range(n_per_author):
                rows.append([a, f"utterance {a} {i}", "CREATE_TASK", "plain", "no", ""])
        return rows

    # 1. Basic split — no author in more than one partition
    def test_no_author_overlap(self):
        authors = [f"A{i}" for i in range(10)]
        train, dev, test = assign_authors(
            authors, train_ratio=0.6, dev_ratio=0.2, seed=42
        )
        all_assigned = train + dev + test
        self.assertEqual(len(all_assigned), len(set(all_assigned)),
                         "Duplicate author across splits")
        self.assertEqual(sorted(all_assigned), sorted(authors))

    # 2. Forced test authors respected
    def test_forced_test_authors(self):
        authors = [f"A{i}" for i in range(6)]
        forced = ["A0", "A1"]
        train, dev, test = assign_authors(
            authors, train_ratio=0.6, dev_ratio=0.2, seed=42,
            forced_test_authors=forced
        )
        self.assertEqual(sorted(test), sorted(forced))
        self.assertNotIn("A0", train)
        self.assertNotIn("A0", dev)

    # 3. Unknown forced test author raises
    def test_unknown_forced_test_author_raises(self):
        authors = ["A1", "A2"]
        with self.assertRaises(ValueError):
            assign_authors(authors, 0.6, 0.2, 42, forced_test_authors=["GHOST"])

    # 4. Determinism — same seed → same assignment
    def test_deterministic(self):
        authors = [f"A{i}" for i in range(12)]
        r1 = assign_authors(authors, 0.6, 0.2, seed=99)
        r2 = assign_authors(authors, 0.6, 0.2, seed=99)
        self.assertEqual(r1, r2)

    # 5. Different seeds → different assignments
    def test_different_seeds_different_result(self):
        authors = [f"A{i}" for i in range(12)]
        r1 = assign_authors(authors, 0.6, 0.2, seed=1)
        r2 = assign_authors(authors, 0.6, 0.2, seed=2)
        self.assertNotEqual(r1, r2)

    # 6. split_data correctly routes rows
    def test_split_data_routing(self):
        rows = [
            {"author_id": "A1", "utterance": "hi", "intended_intent": "CREATE_TASK",
             "style_tags": "plain", "contains_real_pii": "no", "notes": ""},
            {"author_id": "A2", "utterance": "hey", "intended_intent": "DELETE_TASK",
             "style_tags": "plain", "contains_real_pii": "no", "notes": ""},
            {"author_id": "A3", "utterance": "bye", "intended_intent": "CHECK_TASKS",
             "style_tags": "plain", "contains_real_pii": "no", "notes": ""},
        ]
        train, dev, test = split_data(rows, ["A1"], ["A2"], ["A3"])
        self.assertEqual(len(train), 1)
        self.assertEqual(len(dev), 1)
        self.assertEqual(len(test), 1)
        self.assertEqual(train[0]["author_id"], "A1")
        self.assertEqual(test[0]["author_id"], "A3")

    # 7. Manifest written and frozen test protected
    def test_manifest_freeze_protection(self):
        import tempfile, os
        authors = [f"A{i}" for i in range(6)]
        rows_data = self._base_rows(authors)
        csv_text = _make_csv(rows_data)
        with tempfile.TemporaryDirectory() as tmp:
            csv_p = Path(tmp) / "data.csv"
            csv_p.write_text(csv_text, encoding="utf-8")
            out_dir = Path(tmp) / "splits"
            # First run — should succeed
            rc = run_split(csv_p, out_dir, seed=42,
                           test_authors_override=["A0", "A1"])
            self.assertEqual(rc, 0)
            manifest_p = out_dir / MANIFEST_FILENAME
            self.assertTrue(manifest_p.exists())
            # Second run without --force-retrain — must refuse
            rc2 = run_split(csv_p, out_dir, seed=42,
                            test_authors_override=["A0", "A1"])
            self.assertEqual(rc2, 1)

    # 8. SHA256 manifest matches written files
    def test_manifest_sha256_correct(self):
        import tempfile, json as _json
        authors = [f"B{i}" for i in range(6)]
        rows_data = self._base_rows(authors)
        csv_text = _make_csv(rows_data)
        with tempfile.TemporaryDirectory() as tmp:
            csv_p = Path(tmp) / "data.csv"
            csv_p.write_text(csv_text, encoding="utf-8")
            out_dir = Path(tmp) / "splits"
            rc = run_split(csv_p, out_dir, seed=42,
                           test_authors_override=["B0"])
            self.assertEqual(rc, 0)
            manifest = _json.loads((out_dir / MANIFEST_FILENAME).read_text())
            for split_name in ("train", "dev", "test"):
                actual_sha = _sha256_file(out_dir / f"{split_name}.csv")
                self.assertEqual(actual_sha, manifest["split_sha256"][split_name],
                                 f"SHA mismatch for {split_name}")

    # 9. Empty CSV returns error
    def test_empty_csv_returns_error(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            csv_p = Path(tmp) / "empty.csv"
            csv_p.write_text(",".join(REQUIRED_COLUMNS) + "\n", encoding="utf-8")
            rc = run_split(csv_p, Path(tmp) / "splits", seed=42)
            self.assertEqual(rc, 1)

    # 10. _sha256_file is stable
    def test_sha256_stable(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "f.txt"
            p.write_text("hello world\n", encoding="utf-8")
            h1 = _sha256_file(p)
            h2 = _sha256_file(p)
            self.assertEqual(h1, h2)


if __name__ == "__main__":
    sys.exit(main())
