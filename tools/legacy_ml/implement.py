"""One-time retirement migration. Originals must pass before any removal."""
import json
import re
from pathlib import Path

from retire import ARCHIVE, ROOT, digest, git, verify, write_json


def put(name, text):
    path = ROOT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(text.rstrip() + "\n")


def main():
    count = verify(originals=True)
    manifest = json.loads((ARCHIVE / "manifest.json").read_text())
    evidence = ARCHIVE / "private/evidence"
    start = dict(status="LEGACY_ML_RETIREMENT_STARTED", git_commit=git("rev-parse", "metis-pre-legacy-ml-retirement"), original_commit=manifest["git_commit"], branch=manifest["branch"], date="2026-10-16", legacy_models_frozen=True, initial_worktree="DIRTY; exact working bytes preserved in private evidence", safety_tag="metis-pre-legacy-ml-retirement")
    write_json(ROOT / "docs/ml_legacy/retirement-start.json", start)
    ignored = "\n# Retirement evidence is local/private; never publish raw prompts, vocabularies or audit snapshots.\n/archive/legacy_ml/private/\n/datasets/legacy/private/\n# Existing tracked deterministic fixtures/history remain versioned. New legacy data is quarantined.\n/docs/evaluation/phase15/**\n"
    with (ROOT / ".gitignore").open("a", encoding="utf-8") as stream:
        stream.write(ignored)
    put("archive/legacy_ml/.gitattributes", "private/** -text\n*.json text eol=lf\n*.md text eol=lf")
    put("archive/legacy_ml/README.md", """# METIS Legacy ML Archive

Status: RETIRED

These models are preserved only as historical benchmark evidence. Do not retrain,
retune, promote to production, silently modify, or present exposed data as fresh evaluation.

`public/` contains aggregate results only. `private/evidence/` preserves original
repository-relative paths and exact bytes, including ignored build reports and the
V16 first-attempt audit input snapshot. All unreviewed raw material is conservatively
private, including model vocabularies, scripts with embedded examples and predictions.
Private evidence is ignored by Git and is NOT backed up by a clone or either Git tag.
Back it up privately with `manifest.json`; never publish it automatically.

Verify local evidence: `python tools/legacy_ml/retire.py verify`.
The manifest is immutable; version hashes refer to the same files without duplicating them.
Missing historical artifacts are NOT_AVAILABLE, never reconstructed by fitting/scoring.
Deterministic Android read safety and its regression fixtures remain active.
""")
    result = json.loads((evidence / "app/build/reports/phase15/v16/first/first-result.json").read_text())
    safe_v16 = {k: result[k] for k in ("status", "selected", "semantic_test", "raw_semantic_test_diagnostic", "semantic_validation_diagnostic", "synthetic_challenge_diagnostic", "calibration_diagnostics", "numeric_export_parity", "read_safety", "production_ml_enabled", "independent_evaluation", "limitation")}
    write_json(ARCHIVE / "public/v16-aggregate-results.json", safe_v16)
    versions = {
        "v4": ["read_queries_v4", "/v4/", "irs002"], "v5": ["read_queries_v5", "gates_v5", "irs003", "human_review_003"],
        "v9": ["classifier_v9", "/v9/"], "v11": ["classifier_v11", "/v11/"],
        "v12": ["candidate_v12", "/v12/"], "v14": ["candidate_v14", "/v14/"],
        "v16": ["classifier_v16", "read_candidate_v16", "/v16/", "scored_attempt_041", "subset_freeze_040"],
    }
    summaries = {
        "v4": "Generated Macro-F1 ~0.9911; later human benchmark ~0.6116.",
        "v5": "Generated Macro-F1 ~0.9133; approved human evaluation ~0.4328; 14 forbidden simulated reads.",
        "v9": "Selected synthetic holdout Macro-F1 ~0.4390; zero selected negative accepts; zero safe-read gain.",
        "v11": "Experimental development generation; no new benchmark computed.",
        "v12": "Experimental development generation; development queries exposed; no independent score claimed.",
        "v14": "Experimental development generation; no new benchmark computed.",
        "v16": "Development-test Macro-F1 0.4219298245614035; zero UNKNOWN false accepts on that test; 60 on synthetic challenge; six existing reads lost; zero safe gain; -12 incremental ML admissions; NO_GO.",
    }
    registry = dict(status="RETIRED", production_ml_enabled=False, new_model="METIS NLU v1", new_model_trained=False, source_commit=manifest["git_commit"], comparison_policy="Different evaluation sources are not directly equivalent", models={})
    index = ["# Legacy model index", "", "Status: RETIRED. Raw evidence is private; no legacy model has been rerun.", ""]
    for version, tokens in versions.items():
        records = [r for r in manifest["files"] if any(t in r["source"] for t in tokens)]
        model = [r["archive"] for r in records if re.search(r"(?:model|export).*\.(?:json|pkl|joblib|onnx|tflite)$", r["source"], re.I)]
        scripts = [r["archive"] for r in records if r["source"].endswith(".py")]
        write_json(ARCHIVE / version / "hashes.json", dict(version=version.upper(), status="NO_GO" if version == "v16" else "RETIRED", git_commit=manifest["git_commit"], files={r["archive"]: dict(sha256=r["sha256"], source=r["source"]) for r in records}))
        put(f"archive/legacy_ml/{version}/README.md", f"# {version.upper()} historical evidence\n\nStatus: RETIRED\n\n{summaries[version]}\n\nExact private artifacts are indexed by `hashes.json`. Paths preserve their original tree. Global manifest includes shared dependencies, datasets and all other generations.\n")
        registry["models"][version] = dict(summary=summaries[version], model_artifacts=model or "NOT_AVAILABLE", scripts=scripts or "NOT_AVAILABLE", hashes=f"{version}/hashes.json", status="NO_GO" if version == "v16" else "RETIRED", original_decision="NOT_AVAILABLE" if version in ("v11", "v12", "v14") else "NO_GO", source_commit=manifest["git_commit"])
        index.extend([f"## {version.upper()}", "", f"Architecture: {'word 1–2 gram and char_wb 3–5 gram TF-IDF + balanced logistic regression' if version == 'v16' else 'Historical classifier; see preserved source/config for exact architecture.'}", "", "Status: RETIRED (V16 original decision: NO_GO).", "", summaries[version], "", f"Model artifact: {len(model)} matching exports" if model else "Model artifact: NOT_AVAILABLE", "", f"Training/evaluation scripts: {len(scripts)} preserved; exact paths in [registry](../../archive/legacy_ml/benchmark-registry.json).", "", f"Source commit: `{manifest['git_commit']}` plus exact pre-retirement working bytes in manifest.", "", f"Artifact hashes: [SHA-256 index](../../archive/legacy_ml/{version}/hashes.json).", "", "Training/validation/calibration/selection/evaluation datasets, configuration, seed, threshold and temperature: retained originals in the manifest; NOT_AVAILABLE where never recorded. See V16 explicit settings below.", ""])
    write_json(ARCHIVE / "benchmark-registry.json", registry)
    index.extend(["## V16 selected settings", "", "Seed 1515; C=1.0; class_weight=balanced; cues=false; cutoff=0.92; temperature=0.3620356161781817.", "", "TRAIN 254; CALIBRATION 100; SELECTION 50; DEVELOPMENT_TEST 55; DEVELOPMENT_VALIDATION 55; SYNTHETIC_CHALLENGE 313.", "", "Selection F1 0.3746778711 (30 known abstentions); raw development-test argmax F1 0.6412121212 (4 UNKNOWN false accepts); diagnostic validation F1 0.5568253968; synthetic challenge F1 0.4387792784.", "", "Android ML adapter: NOT_AVAILABLE. Production ML: disabled. Per-class metrics/confusion matrices: [aggregate evidence](../../archive/legacy_ml/public/v16-aggregate-results.json). Row-level probabilities/logits are preserved only where recorded; no missing outputs were fabricated."])
    put("docs/ml_legacy/LEGACY_MODEL_INDEX.md", "\n".join(index))
    inventory = ["# Legacy file inventory", "", "Complete original-to-archive paths, sizes and raw SHA-256 hashes are in [manifest](../../archive/legacy_ml/manifest.json). Inventoried before removal; includes ignored reports, source snapshots, model exports, logs, configs and data.", "", "| Original path | Version/purpose | Active after retirement? | Archive | Delete active copy? |", "| --- | --- | --- | --- | --- |"]
    for r in manifest["files"]:
        n = r["source"]
        if n.startswith(("docs/evaluation/phase15/", "app/build/reports/phase15/")):
            removed = n.endswith(".py") or Path(n).name == "requirements.txt"
            inventory.append(f"| `{n}` | Historical evidence | {'No' if removed else 'Historical documentation/data; deterministic fixtures retained'} | Private, exact bytes | {'Yes' if removed else 'No'} |")
    put("docs/ml_legacy/LEGACY_FILE_INVENTORY.md", "\n".join(inventory))
    put("docs/ml_legacy/BENCHMARK_HISTORY.md", "# Benchmark history\n\nEvaluation sources differ; these scores are not directly comparable. No fresh evaluation was run.\n\n| Model | Recorded result and evaluation source |\n| --- | --- |\n" + "\n".join(f"| {v.upper()} | {s} |" for v, s in summaries.items()) + "\n| METIS NLU v1 | Not trained; no results |\n\nSee the model index, immutable private originals, and public V16 aggregate report for failures and confusion matrices.")
    datasets = []
    for r in manifest["files"]:
        n = r["source"]
        if n.startswith("docs/evaluation/phase15/") and Path(n).suffix in (".json", ".jsonl", ".tsv", ".csv", ".txt"):
            category = "PROTECTED_EVALUATION" if any(t in n for t in ("independent_125", "human_review_003/first_evaluation")) else "EXPOSED"
            datasets.append(dict(source=n, archive=r["archive"], sha256=r["sha256"], privacy="PRIVATE_DO_NOT_PUBLISH", exposure=category, trainable=False, rationale="Conservative quarantine of historical data and configuration; fresh independent evaluation prohibited. Historical protected benchmark stays protected even after exposure."))
    write_json(ROOT / "datasets/legacy/catalog.json", dict(policy="No automatic reuse or publication; raw material stays in the private archive", files=datasets))
    for category in ("trainable", "exposed", "protected_evaluation", "private"):
        put(f"datasets/legacy/{category}/README.md", f"# {category}\n\nCategory index only. Raw bytes remain in archive/legacy_ml/private/evidence; use ../catalog.json. No legacy data is approved for NLU v1 training. Privacy and exposure are separate fields; private takes precedence over publication. Explicitly reviewed relabeling is required before any training reuse.")
    put("docs/ml_legacy/DATASET_PROVENANCE.md", """# Dataset provenance

48 of 50 earlier V12 development queries later appeared in V16 TRAIN (documented
historical exposure). V12 development is not fresh evaluation. V16 development,
calibration, selection and synthetic challenge material is exposed. Historical
independent/human first-evaluation sets retain protected status after scoring.

`datasets/legacy/catalog.json` records artifact-level privacy and exposure separately.
All rows in each artifact inherit the artifact's conservative restriction. Unreviewed
content, vocabulary and raw predictions remain PRIVATE_DO_NOT_PUBLISH; none is
automatically approved as TRAINABLE_LEGACY. This is a quarantine decision, not a
claim that every row contains personal data. Duplicate copies retain the same policy.

Fresh NLU data must be isolated by text, family and scenario, and by author/reviewer
where practical. Keep TRAIN, DEVELOPMENT, CALIBRATION, SELECTION and FINAL
INDEPENDENT TEST separate. Never treat exposed legacy material as independent.
""")
    # Remove only already verified offline Python/dependency entry points. Keep all
    # Kotlin deterministic safety implementations and their current fixture paths.
    removed = []
    for r in manifest["files"]:
        n = r["source"]
        if n.startswith("docs/evaluation/phase15/") and (n.endswith(".py") or Path(n).name == "requirements.txt"):
            (ROOT / n).unlink()
            removed.append(n)
    (ROOT / ".github/workflows/classifier-v11.yml").unlink()
    (ROOT / "tools/capability_testing/compatibility.py").unlink()
    build = (ROOT / "app/build.gradle.kts").read_text()
    begin = build.index("// Development-only baseline export")
    end = build.index("kotlin {", begin)
    put("app/build.gradle.kts", build[:begin] + build[end:])
    workflow = (ROOT / ".github/workflows/android.yml").read_text()
    begin = workflow.index("      - uses: actions/setup-python@v5")
    end = workflow.index("      - name: Upload reports and debug APK", begin)
    replacement = "      - uses: actions/setup-python@v5\n        with:\n          python-version: '3.12'\n      - name: Verify legacy retirement and packaged assets\n        run: python tools/legacy_ml/check.py --apks app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release-unsigned.apk\n"
    put(".github/workflows/android.yml", workflow[:begin] + replacement + workflow[end:])
    readme = (ROOT / "README.md").read_text().replace("deterministic reasoning, lightweight on-device ML, and Android APIs", "deterministic reasoning and Android APIs")
    intro = "\nLegacy ML through V16 is retired and [archived](archive/legacy_ml/README.md). Production remains deterministic with no learned model bundled or enabled. [METIS NLU v1](ml/nlu_v1/README.md) is the fresh architecture workspace and is not trained. Historical Phase 15 links below describe past experiments; their commands are retired. See the [retirement report](docs/ml_legacy/RETIREMENT_REPORT.md).\n"
    put("README.md", readme.replace("# metis-android-agent\n", "# metis-android-agent\n" + intro, 1))
    card = (ROOT / "MODEL_CARD.md").read_text()
    put("MODEL_CARD.md", "# Model card\n\nLegacy ML: RETIRED and archived. Production: deterministic only; no learned model packaged or enabled. New ML: METIS NLU v1, not trained. See [model registry](docs/ml_legacy/LEGACY_MODEL_INDEX.md) and [archive](archive/legacy_ml/README.md).\n\nThe previous model card is preserved byte-for-byte in private evidence/MODEL_CARD.md. The following is historical documentation, including preparation statements superseded by the frozen V16 first-result report.\n\n" + card.replace("# Model card", "## Historical model card", 1))
    phase = (ROOT / "docs/evaluation/phase15/README.md").read_text()
    put("docs/evaluation/phase15/README.md", "# Retired Phase 15 experiments\n\nAll offline ML entry points and requirements have moved to the private legacy archive. Do not run historical training/evaluation commands. Retained TSV/JSON data supports deterministic Android safety regression tests only. ReadCandidate.V16 is a deterministic parser version, not a learned model. See [retirement](../../ml_legacy/RETIREMENT_REPORT.md).\n\n## Historical documentation\n\n" + phase)
    put("docs/ml_legacy/REMAINING_REFERENCES.md", """# Remaining references

ACTIVE PRODUCTION: `ReadCandidate.V16`, EnglishRules, ReadSafety and the
IntentClassifier interface implement deterministic parsing/authorization. Keep them.
There are no sklearn, TF-IDF or learned-model runtime dependencies in Android.

TEST: Kotlin forced-label safety tests and retained TSV/JSON fixtures protect
deterministic task/calendar/memory/promise reads; they never fit or score a model.

HISTORICAL DOCUMENTATION: docs/evaluation/phase15 and earlier Phase 15 reports,
the historical model-card section, and capability source-binding JSON retain old
paths/commands as historical context. Those certificates are not current gates.

ARCHIVE: private exact-byte source snapshots, logs, trained exports and datasets;
public manifests/registry and aggregate metrics only. No active code imports them.

STALE REFERENCES removed: classifier workflow, Android CI training/evaluation
steps, phase15RulesSnapshot/phase15ReadRulesSnapshot Gradle tasks, offline Python
entry points and requirements, and capability_testing/compatibility.py (old model
certificate runner). Capability install isolation and harness tests remain active.
""")
    write_json(ROOT / "docs/ml_legacy/removed-entry-points.json", removed)
    print(f"Retired {len(removed)} offline entry points; preserved {count} archive artifacts")


if __name__ == "__main__":
    main()
