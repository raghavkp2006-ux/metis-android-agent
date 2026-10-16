# Remaining references

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
