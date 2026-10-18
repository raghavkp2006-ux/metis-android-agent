# Plan 01 verification — 2026-10-16

Status: DONE. `nlu_v1_schema_001` frozen with the user's exact Plan 01 catalogs.
Steps 2–14 remain outstanding. No training data or independent-evaluation rows
were built. Unit-test fixtures are isolated code checks, not dataset artifacts.

Implemented four YAML catalogs, strict row/output JSON schemas, token/BIO/span
validation, annotation rules, hash freeze manifest and dedicated CI contract job.
Schema regeneration is rejected after freeze; label/schema drift fails checks.

Local Python 3.12.10, jsonschema 4.26.0, PyYAML 6.0.2:

| Check | Result |
| --- | --- |
| `python ml/nlu_v1/contract.py --check` | PASS; synchronized and frozen |
| `python -m unittest discover -s ml/nlu_v1/tests -v` | PASS; 19 tests |
| `python -m unittest discover -s tools/legacy_ml -p 'test_*.py' -v` | PASS; 6 tests |
| `python tools/legacy_ml/check.py` | PASS; production sources unchanged, ML disabled |
| `git diff --check` | PASS |

Rejection coverage: unknown labels, orphan/mismatched I tags, token/tag length
mismatch, mismatched slot text/spans, malformed rows, illegal dispositions/OOD,
UTF-16 surrogate interiors, token omissions/order, overlapping spans, authority
fields, duplicate JSON keys, and frozen-file drift. Tests verify raw DATE and
REFERENCE extraction without normalized values.

No Android build/device test, APK asset inspection or remote CI run was performed
for this offline contract change. There is no trained model or model-quality,
Android-performance or final-independent-evaluation evidence. Single-row schema
validation does not verify source licenses/approvals, bytes, privacy, authorship
or cross-partition leakage. Those remain later ordered gates.
