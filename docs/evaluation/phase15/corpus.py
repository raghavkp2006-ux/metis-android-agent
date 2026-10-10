"""Dataset integrity and interchange helpers for the development experiment."""

from __future__ import annotations

import base64
import csv
import hashlib
import io
import json
import os
import re
import tempfile
from pathlib import Path

LABELS = ["CHECK_TASKS", "CHECK_MEMORY", "WHAT_DID_I_PROMISE", "CHECK_CALENDAR",
          "CREATE_REMINDER", "CREATE_TASK", "CREATE_TIMER", "UNKNOWN"]
FIELDS = ["id", "intent", "utterance", "language", "style", "source", "noise_type",
          "entities", "difficulty", "split", "family_id", "expected_outcome",
          "case_kind", "input_source", "timestamp", "zone", "expected_status"]
FIXED_TIME = "2026-10-09T05:30:00Z"
FIXED_ZONE = "Asia/Kolkata"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalized_text(value: str) -> str:
    return re.sub(r"\s+", " ", value.casefold()).strip()


def validate(rows: list[dict[str, str]], require_splits: bool = True) -> dict:
    if not rows:
        raise ValueError("Empty corpus")
    identifiers: set[str] = set()
    texts: dict[tuple[str, str, str, str], tuple[str, str, str]] = {}
    families: dict[str, tuple[str, str]] = {}
    for row in rows:
        if not set(FIELDS) <= row.keys():
            raise ValueError("Missing dataset columns")
        if row["id"] in identifiers:
            raise ValueError(f"Duplicate ID: {row['id']}")
        identifiers.add(row["id"])
        if not row["utterance"].strip() or len(row["utterance"].encode("utf-16-le")) // 2 > 4_000:
            raise ValueError("Invalid utterance length")
        split, label, family = row["split"], row["intent"], row["family_id"]
        if split not in {"train", "validation", "test"} or not family:
            raise ValueError("Invalid split or missing family")
        if require_splits and label not in LABELS:
            raise ValueError("Label outside the classifier's enabled evaluation scope")
        if families.setdefault(family, (split, label)) != (split, label):
            raise ValueError(f"Family leakage or conflicting family labels: {family}")
        fingerprint = (normalized_text(row["utterance"]), row["input_source"], row["timestamp"], row["zone"])
        previous = texts.setdefault(fingerprint, (split, label, row["id"]))
        if previous[:2] != (split, label):
            raise ValueError(f"Duplicate text across splits or labels: {row['id']}")
        if require_splits and previous[2] != row["id"]:
            raise ValueError(f"Duplicate normalized text: {row['id']}")
        entities = json.loads(row["entities"])
        for entity in entities:
            text_bytes = row["utterance"].encode("utf-16-le")
            start, end = entity["startOffset"], entity["endOffset"]
            if not 0 <= start < end <= len(text_bytes) // 2:
                raise ValueError("Invalid UTF-16 entity bounds")
            if text_bytes[start * 2:end * 2].decode("utf-16-le") != entity["rawValue"]:
                raise ValueError("UTF-16 entity annotation does not match original text")
    if require_splits:
        for split in ("train", "validation", "test"):
            if {row["intent"] for row in rows if row["split"] == split} != set(LABELS):
                raise ValueError(f"Incomplete label coverage in {split}")
    return {"rows": len(rows), "families": len(families), "normalized_duplicates": len(rows) - len(texts)}


def atomic_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as handle:
            handle.write(text)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def write_csv(path: Path, rows: list[dict[str, str]], require_splits: bool = True) -> None:
    validate(rows, require_splits)
    buffer = io.StringIO(newline="")
    writer = csv.DictWriter(buffer, fieldnames=FIELDS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    atomic_text(path, buffer.getvalue())


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def write_native_input(path: Path, rows: list[dict[str, str]]) -> None:
    lines = ["id\ttext_base64\tsource\ttimestamp\tzone"]
    for row in rows:
        encoded = base64.b64encode(row["utterance"].encode("utf-8")).decode("ascii")
        lines.append("\t".join((row["id"], encoded, row["input_source"], row["timestamp"], row["zone"])))
    atomic_text(path, "\n".join(lines) + "\n")


def baseline_source_hash(root: Path) -> str:
    agent = root / "app/src/main/java/dev/metis/agent/domain/agent"
    sources = list(agent.glob("*.kt")) + [root / "app/src/main/java/dev/metis/agent/domain/planner/PlannerWindow.kt"]
    digest = hashlib.sha256()
    for source in sorted(sources, key=lambda item: item.relative_to(root).as_posix()):
        digest.update(source.relative_to(root).as_posix().encode("utf-8"))
        digest.update(source.read_bytes())
    return digest.hexdigest()
