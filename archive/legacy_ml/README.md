# METIS Legacy ML Archive

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
