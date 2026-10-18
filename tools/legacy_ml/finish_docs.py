"""Apply current status while preserving archived original documents."""
import json

from implement import put
from retire import ARCHIVE, ROOT


def main():
    evidence = ARCHIVE / "private/evidence"
    readme = (evidence / "README.md").read_text(encoding="utf-8")
    readme = readme.replace("deterministic reasoning, lightweight on-device ML, and Android APIs", "deterministic reasoning and Android APIs")
    intro = "\nLegacy ML through V16 is retired and [archived](archive/legacy_ml/README.md). Production remains deterministic with no learned model bundled or enabled. [METIS NLU v1](ml/nlu_v1/README.md) is the fresh architecture workspace and is not trained. Historical Phase 15 links below describe past experiments; their commands are retired. See the [retirement report](docs/ml_legacy/RETIREMENT_REPORT.md).\n"
    put("README.md", readme.replace("# metis-android-agent\n", "# metis-android-agent\n" + intro, 1))
    card = (evidence / "MODEL_CARD.md").read_text(encoding="utf-8")
    put("MODEL_CARD.md", "# Model card\n\nLegacy ML: RETIRED and archived. Production: deterministic only; no learned model packaged or enabled. New ML: METIS NLU v1, not trained. See [model registry](docs/ml_legacy/LEGACY_MODEL_INDEX.md) and [archive](archive/legacy_ml/README.md).\n\nThe previous model card is preserved byte-for-byte in private evidence/MODEL_CARD.md. The following is historical documentation, including preparation statements superseded by the frozen V16 first-result report.\n\n" + card.replace("# Model card", "## Historical model card", 1))
    phase = (evidence / "docs/evaluation/phase15/README.md").read_text(encoding="utf-8")
    put("docs/evaluation/phase15/README.md", "# Retired Phase 15 experiments\n\nAll offline ML entry points and requirements have moved to the private legacy archive. Do not run historical training/evaluation commands. Retained TSV/JSON data supports deterministic Android safety regression tests only. ReadCandidate.V16 is a deterministic parser version, not a learned model. See [retirement](../../ml_legacy/RETIREMENT_REPORT.md).\n\n## Historical documentation\n\n" + phase)
    for name in ("docs/AGENT_TRAINING_PLAN.md", "docs/ROADMAP.md"):
        original = (evidence / name).read_text(encoding="utf-8")
        first, rest = original.split("\n", 1)
        status = "\nLegacy ML through V16 is RETIRED. Production remains deterministic. METIS NLU v1 is not trained; its next gate is approval of the dataset/label schema. Previous classifier training, calibration and adoption gates below are historical and superseded by the [retirement record](ml_legacy/RETIREMENT_REPORT.md). Archived classifiers must not be trained or evaluated again.\n\n## Historical phase record\n"
        put(name, first + "\n" + status + rest)
    index = ROOT / "docs/ml_legacy/LEGACY_MODEL_INDEX.md"
    text = index.read_text(encoding="utf-8")
    registry = json.loads((ARCHIVE / "benchmark-registry.json").read_text(encoding="utf-8"))
    for version in ("v4", "v5"):
        start = text.index("## " + version.upper())
        end = text.find("\n## ", start + 4)
        section = text[start:end if end >= 0 else len(text)]
        import re
        replacement = re.sub(r"Model artifact: [^\n]+", f"Model artifact: {len(registry['models'][version]['model_artifacts'])} matching exports; exact paths in registry.", section)
        text = text[:start] + replacement + text[end:] if end >= 0 else text[:start] + replacement
    put("docs/ml_legacy/LEGACY_MODEL_INDEX.md", text)


if __name__ == "__main__":
    main()
