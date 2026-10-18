"""Structure-first generation and exact licensed-template validation.

python -m ml.nlu_v1.synthetic.generate --check
python -m ml.nlu_v1.synthetic.generate --batch-id plan05-seed17 --seed 17 --count 3
"""
import argparse
from collections import Counter
import copy
import hashlib
import json
import platform
import subprocess
import sys
from importlib.metadata import version as package_version
from pathlib import Path
import random
import string

from ml.nlu_v1.ingestion.common import tokenize, u16
from contract import LABELS, validate as validate_prediction
from ml.nlu_v1.context_contract import VERSION as MODEL_VERSION, check as check_context
from ml.nlu_v1.synthetic_registry import PATH as SOURCE_PATH, load_source
from ml.nlu_v1.source_registry import UniqueLoader
from ml.nlu_v1.data_management.records import (
    DataError, append_event, load_groups, register_scenario, schema, sha_json,
    text_hash, validate_record, validate_groups,
)
from ml.nlu_v1.data_management.dedupe import audit, normalized, skeleton
from ml.nlu_v1.data_management.storage import DATASETS, atomic_new, json_bytes, jsonl_bytes, store_batch, verify_batch
import yaml

ROOT = Path(__file__).resolve().parents[3]
CONFIG = ROOT / "ml/nlu_v1/configs/synthetic_families.yaml"
VERSION = "nlu_v1_generator_001"
DATA_VERSION = "nlu_v1_data_002"
STYLES = {"canonical", "polite", "fragment", "slang", "indirect", "typo"}
CODE_FILES = ["contract.py", "context_contract.py", "synthetic_registry.py", "ingestion/common.py",
              "data_management/records.py", "data_management/dedupe.py", "data_management/storage.py", "synthetic/generate.py"]


def file_hash(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def code_hashes():
    return {name: file_hash(ROOT / "ml/nlu_v1" / name) for name in CODE_FILES}


def fields(form):
    result = []
    for _, name, format_spec, conversion in string.Formatter().parse(form):
        if name is not None:
            if format_spec or conversion or not name.isidentifier():
                raise DataError("UNSAFE_TEMPLATE_FIELD")
            result.append(name)
    if len(result) != len(set(result)):
        raise DataError("REPEATED_TEMPLATE_FIELD")
    return result


def load_templates():
    manifest_path = CONFIG.parent / "synthetic_templates.freeze.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("status") != "FROZEN_FOR_CANDIDATE_GENERATION" or manifest.get("template_version") != "nlu_v1_templates_001":
        raise DataError("INVALID_TEMPLATE_FREEZE")
    for name in ("synthetic_families.yaml", "synthetic_sources.yaml", "dataset_v1_mix.yaml"):
        if file_hash(CONFIG.parent / name) != manifest["sha256"].get(name):
            raise DataError("TEMPLATE_REVIEW_VERSION_REQUIRED")
    config = yaml.load(CONFIG.read_text(encoding="utf-8"), Loader=UniqueLoader)
    if config["template_version"] != "nlu_v1_templates_001" or config["generator_version"] != VERSION:
        raise DataError("UNKNOWN_TEMPLATE_VERSION")
    plans = {}
    for family, base in config["families"].items():
        if not base.get("boundaries"):
            raise DataError("POSITIVE_WITHOUT_BOUNDARIES")
        plans[family] = {**base, "role": "POSITIVE", "origin_family": family}
        for name, boundary in base["boundaries"].items():
            plans[family + ":" + name] = {**boundary, "role": "COMPOUND" if boundary.get("components") else "HARD_NEGATIVE", "origin_family": family}
    for family, definition in config["ambiguities"].items():
        plans["ambiguity:" + family] = {**definition, "role": "AMBIGUITY", "origin_family": "ambiguity:" + family}
    for binding in config["bindings"].values():
        if binding["type"] not in LABELS["slots"] or not binding["values"] or any(not isinstance(v, str) or not v.strip() for v in binding["values"]):
            raise DataError("INVALID_SYNTHETIC_BINDING")
    for definition in plans.values():
        if definition["domain"] not in LABELS["domains"] or definition["action"] not in LABELS["actions"] or definition["disposition"] not in LABELS["dispositions"]:
            raise DataError("UNKNOWN_TEMPLATE_LABEL")
        if not set(definition["forms"]) <= STYLES or "canonical" not in definition["forms"]:
            raise DataError("INVALID_TEMPLATE_STYLES")
        required = set(definition["required_slots"])
        if not required <= set(LABELS["slots"]):
            raise DataError("UNKNOWN_REQUIRED_SLOT")
        for form in definition["forms"].values():
            names = fields(form)
            if not set(names) <= set(config["bindings"]):
                raise DataError("UNDEFINED_TEMPLATE_BINDING")
            if required != {config["bindings"][name]["type"] for name in names}:
                raise DataError("FORM_REQUIRED_SLOTS_DISAGREE")
        if definition["role"] == "AMBIGUITY" and ("REFERENCE" not in required or definition["disposition"] != "SUPPORTED_NEEDS_CONTEXT"):
            raise DataError("AMBIGUITY_REQUIRES_CONTEXT_REFERENCE")
        if definition["role"] == "COMPOUND":
            if (definition["domain"], definition["action"], definition["disposition"]) != ("UNKNOWN", "NONE", "SUPPORTED_NEEDS_CONTEXT") or len(definition["components"]) < 2:
                raise DataError("COMPOUND_MUST_NOT_COLLAPSE")
            component_names = []
            for component in definition["components"]:
                if component["domain"] not in LABELS["domains"] or component["action"] not in LABELS["actions"] or component["action"] == "NONE":
                    raise DataError("INVALID_COMPOUND_COMPONENT")
                component_names.extend(component["bindings"])
            if set(component_names) != set(fields(definition["forms"]["canonical"])) or len(component_names) != len(set(component_names)):
                raise DataError("COMPOUND_BINDINGS_NOT_PARTITIONED")
    return config, plans


def values_for(config, seed):
    if type(seed) is not int or seed < 0:
        raise DataError("NONNEGATIVE_INTEGER_SEED_REQUIRED")
    rng = random.Random(seed)
    return {name: rng.choice(binding["values"]) for name, binding in sorted(config["bindings"].items())}


def render(form, values, bindings):
    text, slots = "", []
    for literal, name, _, _ in string.Formatter().parse(form):
        text += literal
        if name is not None:
            value = values[name]
            start = u16(text, len(text))
            text += value
            slots.append({"type": bindings[name]["type"], "text": value, "start": start, "end": u16(text, len(text))})
    tokens, tags = tokenize(text, slots)
    return dict(text=text, slots=slots, tokens=tokens, bio_tags=tags)


def structures(seed, count):
    config, plans = load_templates()
    if type(count) is not int or not 1 <= count <= 1000:
        raise DataError("COUNT_OUT_OF_RANGE")
    values_for(config, seed)  # Strict seed type/range check even before iteration.
    result = []
    for current in range(seed, seed + count):
        values = values_for(config, current)
        for family, definition in plans.items():
            for style, form in definition["forms"].items():
                result.append({"family": family, "origin_family": definition["origin_family"], "role": definition["role"],
                    "seed": current, "style": style, "bindings": {key: values[key] for key in fields(form)},
                    "template_id": family + ":" + style})
    return result


def paraphrase_structures(parent):
    """Expand a declared positive canonical parent, never external utterances."""
    make_rows([parent], "structure-check")
    if parent["role"] != "POSITIVE" or parent["style"] != "canonical":
        raise DataError("POSITIVE_CANONICAL_PARENT_REQUIRED")
    return [item for item in structures(parent["seed"], 1)
            if item["family"] == parent["family"] and item["style"] != "canonical"]


def hard_negative_structures(parent):
    """Action/domain boundaries and compound requests share their positive origin."""
    make_rows([parent], "structure-check")
    if parent["role"] != "POSITIVE" or parent["style"] != "canonical":
        raise DataError("POSITIVE_CANONICAL_PARENT_REQUIRED")
    return [item for item in structures(parent["seed"], 1)
            if item["origin_family"] == parent["family"] and item["role"] in ("HARD_NEGATIVE", "COMPOUND")]


def ambiguity_structures(seed):
    return [item for item in structures(seed, 1) if item["role"] == "AMBIGUITY"]


def row_id(structure, source):
    return text_hash(json.dumps([source["source_id"], source["version"], "generated", sha_json(structure)], separators=(",", ":")))


def make_rows(plans_input, batch_id):
    check_context()
    config, plans = load_templates()
    source = load_source()
    families, scenarios = load_groups()
    input_hash = hashlib.sha256(jsonl_bytes(plans_input)).hexdigest()
    artifact_hash = sha_json({"template_sha256": file_hash(CONFIG), "source_registry_sha256": file_hash(SOURCE_PATH), "code_sha256": code_hashes()})
    rows = []
    for ordinal, structure in enumerate(plans_input, 1):
        if set(structure) != {"family", "origin_family", "role", "seed", "style", "bindings", "template_id"} or structure["family"] not in plans:
            raise DataError("INVALID_GENERATION_STRUCTURE")
        definition = plans[structure["family"]]
        values = values_for(config, structure["seed"])
        if structure["style"] not in definition["forms"]:
            raise DataError("UNREGISTERED_SURFACE_FORM")
        expected_bindings = {key: values[key] for key in fields(definition["forms"][structure["style"]])}
        if structure["bindings"] != expected_bindings or structure["origin_family"] != definition["origin_family"] or structure["role"] != definition["role"] or structure["template_id"] != structure["family"] + ":" + structure["style"]:
            raise DataError("UNTRUSTED_GENERATION_STRUCTURE")
        root_family = structure["origin_family"]
        root_definition = plans[root_family]
        root = dict(structure, family=root_family, role=root_definition["role"], style="canonical", template_id=root_family + ":canonical",
                    bindings={key: values[key] for key in fields(root_definition["forms"]["canonical"])})
        parent_id = row_id(root, source)
        ident = row_id(structure, source)
        family = "synthetic:" + structure["family"]
        family_definition = {key: definition[key] for key in ("domain", "action", "required_slots")}
        family_definition["dispositions"] = [definition["disposition"]]
        families["families"][family] = family_definition
        scenario_id = "synthetic:" + sha_json([family, parent_id])
        if scenario_id not in scenarios["scenarios"]:
            scenarios = register_scenario(scenarios, families, scenario_id, family, parent_id, "Closed fictional structural seed; no evaluation input")
        scenario = scenarios["scenarios"][scenario_id]
        rendered = render(definition["forms"][structure["style"]], structure["bindings"], config["bindings"])
        composition = None
        if definition.get("components"):
            composition = {"routing_hint": "ORCHESTRATOR_DECOMPOSE_OR_CLARIFY", "requests": [
                {"domain": component["domain"], "action": component["action"], "slots": [slot for name in component["bindings"] for slot in rendered["slots"] if slot["text"] == structure["bindings"][name] and slot["type"] == config["bindings"][name]["type"]]}
                for component in definition["components"]]}
        row = {"schema_version": DATA_VERSION, "nlu_schema_version": MODEL_VERSION, "id": ident, **rendered,
            "domain": definition["domain"], "action": definition["action"], "disposition": definition["disposition"], "ood": False,
            "family": family, "source": {"dataset": source["source_id"], "version": source["version"], "source_id": sha_json(structure),
                "original_split": "generated", "original_intent": structure["family"], "mapper_version": VERSION, "mapping_version": config["template_version"],
                "mapping_decision": "MAP", "artifact_sha256": artifact_hash, "intake_approval_id": None, "source_class": "CONTROLLED_SYNTHETIC"},
            "privacy": "UNKNOWN_PRIVACY", "review_status": "PENDING", "metadata": {"structure": copy.deepcopy(structure),
                "template_sha256": file_hash(CONFIG), "generator_sha256": file_hash(Path(__file__)),
                "routing_hint": "ORCHESTRATOR_DECOMPOSE_OR_CLARIFY" if composition else "CONTEXT_AGENT" if definition["disposition"] == "SUPPORTED_NEEDS_CONTEXT" else "DETERMINISTIC_VALIDATOR_THEN_SAFETY",
                "unresolved_action": definition.get("unresolved_action"), "generation_role": structure["role"]},
            "composition": composition, "scenario_id": scenario_id, "semantic_parent_id": parent_id,
            "partition": "UNASSIGNED", "text_sha256": text_hash(rendered["text"]),
            "family_registry_version": "nlu_v1_families_001", "scenario_registry_version": "nlu_v1_scenarios_001",
            "generation": {"parent_ids": [] if ident == parent_id else [parent_id], "seed": structure["seed"], "generator_id": VERSION, "template_id": structure["template_id"]},
            "quarantine_reasons": [], "privacy_review": None, "review_history": [],
            "provenance": {"ingestion_row_sha256": sha_json(rendered), "import_batch_id": batch_id, "import_row_number": ordinal, "import_file_sha256": input_hash,
                "mapping_configuration_sha256": file_hash(CONFIG), "source_registry_sha256": file_hash(SOURCE_PATH),
                "family_definition_sha256": sha_json(family_definition), "scenario_definition_sha256": sha_json(scenario),
                "source_registry_version": "nlu_v1_synthetic_sources_001", "upstream_privacy": "UNKNOWN_PRIVACY"}}
        append_event(row, None, "PENDING", VERSION, "CONTROLLED_STRUCTURE_RENDERED")
        validate_record(row)
        rows.append(row)
    validate_groups(families, scenarios)
    return rows, (families, scenarios)


def validate_generated(row, expected, *, isolation_rows=()):
    """External paraphrases are accepted only if they equal a trusted bounded form.

    This is a structural guard, not an unconstrained semantic oracle. expected is
    rendered afresh from repository templates; never taken from candidate metadata.
    """
    validate_record(row)
    trusted, _ = make_rows([expected["metadata"]["structure"]], expected["provenance"]["import_batch_id"])
    for key in ("text", "tokens", "bio_tags", "slots", "domain", "action", "disposition", "ood", "family", "scenario_id", "semantic_parent_id", "source", "generation", "metadata", "composition", "partition"):
        if row[key] != trusted[0][key]:
            raise DataError("GENERATED_SEMANTICS_OR_PROVENANCE_CHANGED")
    for key in ("text", "tokens", "bio_tags", "slots", "domain", "action", "disposition", "ood", "family", "scenario_id", "semantic_parent_id", "source", "generation", "metadata", "composition", "partition", "provenance"):
        if row[key] != expected[key]:
            raise DataError("GENERATED_SEMANTICS_OR_PROVENANCE_CHANGED")
    if row["generation"]["parent_ids"] != expected["generation"]["parent_ids"]:
        raise DataError("GENERATION_PARENT_MISSING")
    for reference in isolation_rows:
        validate_record(reference)
        if normalized(row["text"]) == normalized(reference["text"]) or row["id"] == reference["id"] or skeleton(row) == skeleton(reference):
            raise DataError("DUPLICATE_OR_SKELETON_COLLISION")
        if row["semantic_parent_id"] == reference["semantic_parent_id"] or row["scenario_id"] == reference["scenario_id"]:
            raise DataError("PROTECTED_GROUP_OR_PARENT_COLLISION")
    return row


def generate(seed=17, count=1, batch_id="synthetic-preview", isolation_rows=()):
    spec = structures(seed, count)
    rows, groups = make_rows(spec, batch_id)
    # Re-render independently; do not validate a candidate against itself.
    expected, _ = make_rows(spec, batch_id)
    rejected = []
    seen = {}
    rejection_reasons = {}
    for row, reference in zip(rows, expected):
        try:
            validate_generated(row, reference, isolation_rows=isolation_rows)
            key = normalized(row["text"])
            if key in seen:
                raise DataError("DUPLICATE_GENERATED_TEXT")
            seen[key] = row["id"]
        except DataError as error:
            rejected.append(row["id"])
            rejection_reasons[row["id"]] = str(error)
    # All attempts, including rejected duplicates, remain in a reviewable quarantine.
    generated_rows, report = audit(rows, groups=groups)
    for match in report["matches"]:
        if match["check"] in {"exact_text", "normalized_text", "source_id", "source_skeleton", "row_id", "embedding_similarity"}:
            rejected.extend(rows[index]["id"] for index in match["indices"])
            for index in match["indices"]:
                rejection_reasons[rows[index]["id"]] = "GENERATION_DEDUPE_REJECTED"
    rejected = set(rejected)
    from ml.nlu_v1.data_management.records import quarantine
    generated_rows = [quarantine(row, ["GENERATION_CHECK_REJECTED"]) if row["id"] in rejected else row for row in generated_rows]
    summary = {"generator_version": VERSION, "seed": seed, "count": count, "attempted_rows": len(rows),
        "structurally_accepted": len(rows) - len(rejected), "rejected_attempts": len(rejected),
        "roles": dict(sorted(Counter(row["metadata"]["generation_role"] for row in rows).items())),
        "rejection_reasons": dict(sorted(Counter(rejection_reasons.values()).items())),
        "canonical_rows": 0, "training_rows": 0, "semantic_similarity": report["checks"]["embedding_similarity"],
        "template_sha256": file_hash(CONFIG), "generator_sha256": file_hash(Path(__file__)),
        "source_registry_sha256": file_hash(SOURCE_PATH), "mix_policy_sha256": file_hash(ROOT / "ml/nlu_v1/configs/dataset_v1_mix.yaml")}
    summary["code_sha256"] = code_hashes()
    summary["environment"] = {"python": sys.version, "platform": platform.platform(),
        "packages": {name: package_version(name) for name in ("jsonschema", "PyYAML")},
        "model": None, "tokenizer": None}
    revision = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True)
    summary["git_revision"] = revision.stdout.strip() if revision.returncode == 0 else None
    return spec, generated_rows, groups, report, summary


def check():
    check_context()
    load_source()
    load_templates()
    stored = ROOT / "ml/nlu_v1/data_management/canonical_data_002.schema.json"
    if json.loads(stored.read_text(encoding="utf-8")) != schema(DATA_VERSION):
        raise DataError("STALE_MANAGED_CONTEXT_SCHEMA")
    mix = yaml.load((ROOT / "ml/nlu_v1/configs/dataset_v1_mix.yaml").read_text(encoding="utf-8"), Loader=UniqueLoader)
    if sum(mix["design_example"].values()) != 100 or any(not limits[0] <= mix["design_example"][key] <= limits[1] for key, limits in mix["ranges"].items()):
        raise DataError("INVALID_DATASET_MIX_DESIGN")


def verify_generation_batch(root, batch_id):
    # Validate the ID/path before opening any generation-specific file.
    batch = verify_batch(root, batch_id)
    base = Path(root) / "raw"
    manifest = json.loads((base / (batch_id + ".generation.manifest.json")).read_text(encoding="utf-8"))
    suffixes = ("groups.json", "generation.json", "templates.yaml", "sources.yaml", "generator.py", "code.json", "mix.yaml", "manifest.json")
    if manifest.get("manifest_version") != "nlu_v1_generation_batch_001" or manifest.get("status") != "COMMITTED" or manifest.get("training_built") is not False or set(manifest["files"]) != {batch_id + "." + suffix for suffix in suffixes}:
        raise DataError("INVALID_GENERATION_COMMIT_MARKER")
    for name, digest in manifest["files"].items():
        path = base / name
        if path.resolve().parent != base.resolve() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise DataError("GENERATION_ARTIFACT_CHANGED")
    return batch


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--check", action="store_true")
    modes.add_argument("--verify-batch")
    parser.add_argument("--seed", type=int, default=17)
    parser.add_argument("--count", type=int, default=1)
    modes.add_argument("--batch-id")
    parser.add_argument("--root", type=Path, default=DATASETS)
    args = parser.parse_args()
    check()
    if args.check:
        print("Synthetic templates, versioned schemas, registry and mix policy valid")
        return
    if args.verify_batch:
        print(json.dumps(verify_generation_batch(args.root, args.verify_batch), sort_keys=True))
        return
    spec, rows, groups, report, summary = generate(args.seed, args.count, args.batch_id)
    # A standalone generation run has no external evaluation input. Plan 06 owns
    # full-corpus cross-source isolation; missing embeddings keep every row held.
    manifest = store_batch(rows, report, jsonl_bytes(spec), batch_id=args.batch_id, root=args.root, input_kind="GENERATED_STRUCTURAL_PLANS")
    artifacts = {
        args.batch_id + ".groups.json": json_bytes({"families": groups[0], "scenarios": groups[1]}),
        args.batch_id + ".generation.json": json_bytes(summary),
        args.batch_id + ".templates.yaml": CONFIG.read_bytes(),
        args.batch_id + ".sources.yaml": SOURCE_PATH.read_bytes(),
        args.batch_id + ".generator.py": Path(__file__).read_bytes(),
        args.batch_id + ".code.json": json_bytes({name: (ROOT / "ml/nlu_v1" / name).read_text(encoding="utf-8") for name in CODE_FILES}),
        args.batch_id + ".mix.yaml": (ROOT / "ml/nlu_v1/configs/dataset_v1_mix.yaml").read_bytes(),
    }
    for name, data in artifacts.items():
        atomic_new(args.root / "raw" / name, data)
    artifacts[args.batch_id + ".manifest.json"] = (args.root / "raw" / (args.batch_id + ".manifest.json")).read_bytes()
    atomic_new(args.root / "raw" / (args.batch_id + ".generation.manifest.json"), json_bytes({
        "manifest_version": "nlu_v1_generation_batch_001", "status": "COMMITTED",
        "files": {name: hashlib.sha256(data).hexdigest() for name, data in artifacts.items()}, "training_built": False}))
    print(json.dumps({**summary, "manifest": manifest["batch_id"]}, sort_keys=True))


if __name__ == "__main__":
    main()
