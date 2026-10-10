# Final read-only annotation review

The user selected **current v5 read-only scope**, with broader product expectations retained separately. This file is the final pre-inference review handoff; it is **not yet frozen or scored**.

## Ready for human review

- [Readable annotations](annotation-review.md): all 145 queries, entities, filters, current dispositions and preserved product expectations.
- [Machine-readable annotations](annotation-proposals.csv): the same data with blank human-reviewed/approval fields.
- [Candidate and review hashes](approval-pending.json): binds these proposals to the unchanged v5 candidate. CSV SHA-256: `87eec1b305ed85ec6d0b939430fd83ca3f8c2e6d6ef61dc20303ff93b9bf0e87`.

Coverage is complete: tasks 25, memory 27, promises 25, calendar 25, UNKNOWN 43. All entity spans and row identifiers have been checked. These are data-integrity checks, not model performance or independent human review.

The existing `read_queries_v5/v5_review.py` freezer requires explicit reviewer approval of exact entities, filters and dispositions. The earlier pasted review approved supplied meanings/product expectations; the later answer selected the protocol scope. Neither explicitly approves the final typed annotations completed afterward. No assistant-generated approval is inserted.

## Scope and interpretation

- Only task, memory and promise reads can receive `read`. Calendar access is unavailable. Commands to create/edit/delete are outside the read classifier and retain separate native handling.
- Requested task-topic, priority, age, completed-status and promise-subject/all-person restrictions can require clarification because the current read port cannot safely apply them. The requested constraints remain visible instead of being discarded.
- Filter truth includes extended keys which v5 may not emit. This can produce exact-filter failures despite a safe clarification. Report these separately from forbidden reads; do not remove them after seeing results.
- There are no conversation/repository identity fixtures. Unresolved people, books, meetings or courses require clarification as specified per row.
- Relative-date conventions are fixed in the readable review. The reference is **2026-10-09, Asia/Kolkata**, not the machine's date. The month/year ambiguity for June and the before-Friday ambiguity remain explicit.
- Human authorship is recorded from the session. Freshness requires reviewer confirmation; exact duplicate checking does not prove near-paraphrase independence.

## Approval and first-score workflow

After a human explicitly approves this final file and its freshness, record the approving message and this file's SHA. Copy approved draft values into `reviewed_*`, record a reviewer identifier and `approved=yes`, and retain the original proposals. The assistant may serialize that approval; it must not invent one. Use the existing validator to freeze a new JSON/TSV against v5, then generate its native rules snapshot and run one evaluation:

```powershell
python docs/evaluation/phase15/read_queries_v5/v5_review.py freeze-fresh --input <approved-review.csv> --output <fresh-frozen.json> --reviewer <recorded-reviewer>
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=<fresh-frozen.tsv>" "-Pphase15ReadOutput=<fresh-native.tsv>"
python docs/evaluation/phase15/read_queries_v5/evaluate.py --reviewed-benchmark <fresh-frozen.json> --reviewed-baseline <fresh-native.tsv> --output <first-score.json>
```

Preserve the frozen set, approved review, native snapshot, first result, exported artifact and consumption receipt. Bind the result hashes to the frozen benchmark and native snapshot in an evidence record; the evaluator's aggregate report alone does not bind all reviewed-input hashes. Do not use `--record-reference`, mutate the candidate, tune on these queries or overwrite a first result.

Even a passing reviewed score closes only the reviewed simulated-evaluation gate. Representative calibration, real repository/policy outcomes, Android parity and physical-device measurements remain separate adoption requirements.
