# Phase 15 deterministic v16 and ML preparation

V16 adds closed read productions for the 52 missing reviewed development forms. Android selects deterministic `ReadCandidate.V16`; historical constructors still default to v12 and explicit v15 behavior remains reproducible. Production ML is disabled, with no model assets or new F1 result.

New productions cover task existence and ordinary/completed scopes, saved action content, literal new/latest content modifiers, negative responsibility statements, full attribution/subject spans, named-recipient commitments and accepted local-calendar event/person/topic reads. “Next meeting” uses a bounded upcoming window from the captured request clock/timezone. Instructions, unsupported filters, compounds, outer negation, unresolved references, malformed spans and forged scopes still fail before read dispatch. Scoped ports, schemas, storage bounds and confirmation boundaries are retained.

Development families were authored before the grammar, with separate pre-addition supplements for book-content and full-span corrections. There are 1,208 new assistant-authored cases, including 326 valid cases and paired prohibited variants. Exact earlier duplicates are excluded from the main families; supplemental scope/annotation cases are explicitly authored development evidence. They are not human calibration or independent evaluation.

The complete replay comprises 11,442 native/Python cases with 45,768 forced read-label trials. All 909 v15 admissions remain available. The reviewed 365-request set now admits 96/96 intended reads (44 previously) and blocks all 269 prohibited requests. This measures bounded read admission, not factual-answer accuracy. Exact entities, queries, scopes, dispositions and constraints are reported separately. Two calendar person-only reads legitimately use the explicit name as the query. One frozen task annotation mistakenly says ANY for an ordinary open-task list; the original is preserved and an explicit policy erratum records the previously confirmed OPEN behavior.

Original v6–v15 artifacts, approvals, intake files and first evaluation results remain unchanged. The v15 certificate is retained as historical evidence; its assertion that it certifies current shared source is superseded by the separate v16 certificate. `historical_tests.py` patches only that obsolete certificate assertion, runs retained behavior unchanged, and replays v15 evaluators in an isolated interpreter. No historical score is overwritten or claimed as fresh evaluation.

Validation: 141 JVM tests, 144 current/retained Python tests, lint Debug/Release, Detekt, debug/release APK and instrumentation compilation passed. On the connected CPH2691 (Android 16/API 36), all four new isolated encrypted-storage tests passed. The first combined phone run passed 10/11 tests, with a calendar UI startup/hierarchy failure; the unchanged seven UI tests then passed on retry. Both logs are retained, the temporary USB stay-awake setting was restored, and no personal data was cleared. No local emulator was used. These results do not measure Android ML. CI retains the API 26/36 matrix; new remote results are reported only when confirmed. See the [regression report](regression-report.md).

Commands:

```powershell
.venv-phase15\Scripts\python.exe docs/evaluation/phase15/read_candidate_v16/integrity.py
.venv-phase15\Scripts\python.exe docs/evaluation/phase15/read_candidate_v16/historical_tests.py
.venv-phase15\Scripts\python.exe -m unittest discover -s docs/evaluation/phase15/read_candidate_v16 -p 'test_*.py' -v
.\gradlew.bat :app:testDebugUnitTest
.venv-phase15\Scripts\python.exe docs/evaluation/phase15/read_candidate_v16/historical_tests.py --evaluate
.venv-phase15\Scripts\python.exe docs/evaluation/phase15/read_candidate_v16/evaluate.py
```

Raw native reports remain in ignored `app/build/reports/phase15/read-v16/`. [Classifier preparation](../classifier_v16/README.md) implements the next model pipeline, but fitting remains blocked by the reviewed calibration/selection collections. [Stage status](stage-status.json) names all remaining prerequisites.
