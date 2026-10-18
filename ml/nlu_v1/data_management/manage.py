"""Plan 04 local governance CLI; no source downloads or training."""
import argparse
import json
from pathlib import Path
import sys

if not __package__:
    sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
    __package__ = "ml.nlu_v1.data_management"
from .records import HERE, DataError, load_groups, schema
from .dedupe import audit, load_policy
from .storage import DATASETS, atomic_new, json_bytes, process, verify_batch
from contract import unique_keys, verify_freeze
from jsonschema import Draft202012Validator, ValidationError


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--check", action="store_true")
    modes.add_argument("--write-schema", action="store_true")
    modes.add_argument("--empty-workspace-report", type=Path)
    modes.add_argument("--import-jsonl", type=Path)
    parser.add_argument("--batch-id")
    parser.add_argument("--root", type=Path, default=DATASETS)
    parser.add_argument("--embeddings", type=Path)
    args = parser.parse_args()
    try:
        verify_freeze()
        load_groups()
        load_policy()
        Draft202012Validator.check_schema(schema())
        target = HERE / "canonical_data.schema.json"
        if args.write_schema:
            target.write_bytes(json_bytes(schema()))
        elif json.loads(target.read_text(encoding="utf-8")) != schema():
            raise DataError("MANAGED_DATA_SCHEMA_STALE")
        if args.empty_workspace_report:
            for name in ("raw", "canonical", "quarantine", "frozen"):
                if not (args.root / name).is_dir():
                    raise DataError("WORKSPACE_DIRECTORY_MISSING")
                if any(p.is_file() and p.name != "README.md" for p in (args.root / name).rglob("*")):
                    raise DataError("EMPTY_REPORT_REQUIRES_EMPTY_WORKSPACE")
            _, report = audit([])
            atomic_new(args.empty_workspace_report, json_bytes(report))
            print("PASS: empty-workspace dedupe report written; no semantic embeddings or real rows evaluated")
        elif args.import_jsonl:
            if not args.batch_id:
                raise DataError("BATCH_ID_REQUIRED")
            vectors = json.loads(args.embeddings.read_text(encoding="utf-8"), object_pairs_hook=unique_keys) if args.embeddings else None
            result = process(args.import_jsonl, batch_id=args.batch_id, root=args.root, embeddings=vectors)
            verify_batch(args.root, args.batch_id)
            print(json.dumps({key: result[key] for key in ("batch_id", "canonical_rows", "quarantine_rows", "total_rows", "training_built")}))
        else:
            print("PASS: canonical data schema, privacy/review states, family/scenario registries and dedupe policy")
    except (ValueError, ValidationError, KeyError, TypeError, OSError, RecursionError) as error:
        code = str(error) if isinstance(error, DataError) else type(error).__name__
        raise SystemExit("Data operation rejected: " + code) from None


if __name__ == "__main__":
    main()
