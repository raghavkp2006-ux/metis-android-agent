"""Index existing exports by their JSON schema, without running inference."""
import json
import re

from retire import ARCHIVE, ROOT, write_json


def main():
    manifest = json.loads((ARCHIVE / "manifest.json").read_text(encoding="utf-8"))
    registry_path = ARCHIVE / "benchmark-registry.json"
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    exports = {v: [] for v in registry["models"]}
    for item in manifest["files"]:
        name = item["source"].rsplit("/", 1)[-1]
        if not name.endswith(".json") or not re.search(r"model|candidate|export", name):
            continue
        value = json.loads((ROOT / item["archive"]).read_text(encoding="utf-8"))
        if not isinstance(value, dict) or not ("coefficients" in value or "heads" in value and "payload_sha256" in value):
            continue
        match = re.search(r"(?:^|[-_])v(\d+)(?:$|[-_])", str(value.get("version", "")))
        if match and "v" + match[1] in exports:
            exports["v" + match[1]].append((item, value))
    architectures = {
        "v4": "Word/character TF-IDF + logistic regression, numeric JSON export",
        "v5": "Word/character TF-IDF + logistic regression with request-derived lexical features",
        "v9": "Word/character TF-IDF + balanced logistic regression",
        "v11": "Flat five-label word/character TF-IDF + balanced logistic regression; optional lexical cues",
        "v12": "Hierarchical two-head word/character TF-IDF + logistic regression",
        "v14": "Reuses isolated V12 two-head word/character TF-IDF engine with optional lexical cues",
        "v16": "Word TF-IDF 1–2 grams + char_wb TF-IDF 3–5 grams + balanced logistic regression",
    }
    sources = {"v4": "read_queries_v4", "v5": "read_queries_v5", "v9": "classifier_v9", "v11": "classifier_v11", "v12": "candidate_v12", "v14": "candidate_v14", "v16": "classifier_v16"}
    index = (ROOT / "docs/ml_legacy/LEGACY_MODEL_INDEX.md").read_text(encoding="utf-8")
    for version, entry in registry["models"].items():
        entry["architecture"] = architectures[version]
        entry["model_artifacts"] = [item["archive"] for item, value in exports[version]] or "NOT_AVAILABLE"
        entry["selected_configurations"] = [dict(artifact=item["archive"], selected=value.get("selected", {k: value[k] for k in ("temperature", "threshold") if k in value})) for item, value in exports[version]]
        relevant = [r for r in manifest["files"] if f"docs/evaluation/phase15/{sources[version]}/" in r["source"]]
        entry["dataset_and_configuration_evidence"] = [r["archive"] for r in relevant if r["source"].endswith((".csv", ".tsv", ".json"))]
        entry["training_scripts"] = [r["archive"] for r in relevant if r["source"].endswith(".py") and any(t in r["source"].rsplit("/", 1)[-1] for t in ("train", "model", "pipeline", "evaluate"))]
        entry["evaluation_scripts"] = [r["archive"] for r in relevant if r["source"].endswith(("evaluate.py", "pipeline.py"))]
        entry["environment"] = "archive/legacy_ml/private/evidence/docs/evaluation/phase15/requirements.txt"
        entry["data_policy"] = "Historical exposed diagnostics or protected evaluation; raw material private; no automatic training reuse"
        entry["known_problems"] = entry["summary"]
        entry["random_seed"] = {"v9": 1509, "v11": 1511, "v16": 1515}.get(version, "Recorded in preserved training source/configuration; not inferred")
        begin = index.index("## " + version.upper())
        end = index.find("\n## ", begin + 4)
        section = index[begin:end if end >= 0 else len(index)]
        section = re.sub(r"Architecture: [^\n]+", "Architecture: " + architectures[version], section)
        section = re.sub(r"Model artifact: [^\n]+", f"Model artifact: {len(exports[version])} schema-confirmed historical exports; exact paths and selected settings in registry.", section)
        index = index[:begin] + section + index[end:] if end >= 0 else index[:begin] + section
    write_json(registry_path, registry)
    (ROOT / "docs/ml_legacy/LEGACY_MODEL_INDEX.md").write_text(index, encoding="utf-8")
    print("Schema-confirmed historical exports:", {v: len(values) for v, values in exports.items()})


if __name__ == "__main__":
    main()
