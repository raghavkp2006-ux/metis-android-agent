# Phase 15 v15 deterministic development candidate

Production selects v15 explicitly. ML remains disabled and unbundled. This increment is scoped saved-record retrieval, not Phase 15 acceptance, classifier adoption or a fresh independent evaluation. Historical candidates, approvals, first results and intake bytes remain unchanged.

## Retrieval contract

The grammar matches a complete request, captures typed fields and validates all fields before context construction. Specialist dispatch reconstructs and compares the original intent, UTF-16 spans, normalized complete query and typed scope. Classifier confidence cannot authorize a read. Historical port methods and default constructors remain available; v15 uses the new scoped overloads.

| Store | Scope and matching |
| --- | --- |
| Tasks | Concrete subject; OPEN/COMPLETED/CANCELLED or ANY; explicit due/completion metadata dates; requested stored due/completion dates. Existence without status uses ANY, ordinary lists OPEN. |
| Promises | Required saved recipient, exact unique case-insensitive name; optional literal content, OPEN/FULFILLED/CANCELLED or ANY, explicit due metadata date. A scan exceeding 100 people cannot establish uniqueness. |
| Memories | Explicit saved notes only; complete literal subject and attribution retained; explicitly requested email/number fields; saved-on date filters the record's created-at metadata. Existing attributed/title/field recall remains available. |
| Calendar | Accepted local entries; literal topic/person-in-content constraints and local date overlap. Unconstrained new existence/topic forms search all saved dates within the candidate bound. Existing v13 upcoming/availability forms retain their original windows. Calendar text does not establish a contact identity or attendee relationship. |

ISO local dates, today, tomorrow and this week are supported. Weeks start Monday. Half-open date windows use the request's captured instant and one captured timezone, including DST. Deadline/completion/creation/event dates are distinct from dates mentioned in content. Friday, afterward, ambiguous references, unsupported filters and combined actions/requests clarify before context/read calls.

Explicit negative recall is accepted only inside validated content productions and matches the literal negative phrase. It never becomes a positive commitment. Positive promise-content queries conservatively exclude negative saved content. No final intentions, later changes, contradictions or relationships are inferred from matching words.

Task/promise status, recipient and date predicates execute in SQL before candidate decryption. Content uses literal Unicode words, never SQL/FTS operators. No schema change, new persistent plaintext index or stored-record mutation is introduced. At most 20 task/promise candidates are decrypted, then five matches displayed. Memory retains its 20-candidate default and existing transient literal FTS behavior. Calendar retains the existing 100-candidate bound plus lookahead. Existing readable-key probes may decrypt one field independently of a valid search's metadata predicate. Truncated scans/excerpts explicitly disclose incompleteness; no match is not proof that no older record exists.

Scheduled-reminder lookup, cross-store search and explicit task-assignee lookup report their unsupported capability without substituting another store. Other unsupported requests retain their complete original constraints in clarification. Existing action confirmation/execution paths retain their boundaries.

## Evidence and review

The new [development families](development.json) are assistant-authored and were declared before their corresponding grammar productions. They include literal titles, articles, apostrophes, Unicode, fields, negative content, local dates and malformed/unresolved/prohibited counterparts; exact earlier and in-batch duplicates are excluded. Native/Python agreement is regression evidence, not an independent semantic accuracy estimate.

[Regression report](regression-report.md) separates admissions, entities, queries, dispositions, constraint retention and encrypted-storage outcomes. Raw reports live only under ignored `app/build/reports/phase15/v15/`.

[Review projection](review.md) covers all 365 submitted development requests. Five annotations have explicit user approval; 360 remain drafts. All family suggestions remain unreviewed, and all rows remain ineligible for fitting. Grouping prevents partition leakage; it supplies no conversation history. [Review decisions](review-decisions.json) record the new confirmations without rewriting historical intakes. Intake-005's latest 101 items are now development-authorized and cannot become the new independent test.

## Remaining gates

[Stage status](stage-status.json) records each of the six stages. Review and freeze all development annotations/families first. Then collect 100 calibration and 50 selection requests with the quotas frozen in [protocol.json](protocol.json). Fit temperature on calibration only; select configuration/cutoff on selection only; retain the 0.90 cutoff floor and single numeric thread. Do not fit from the current drafts.

Only after model/parser/threshold/protocol freeze request 150 genuinely new independently authored/reviewed evaluation queries. Their quotas are 45 memory, 45 calendar, 20 task, 20 promise and 20 other/unsupported. Score once and retain a versioned NO_GO on failure. Additional tuning requires another candidate. These exposed regressions cannot satisfy that gate.

Android ML implementation and latency/PSS/battery measurement remain blocked by offline quality gates. The physical tests here exercise deterministic storage behavior, not model inference. API 26/36 emulator execution is configured in CI but has not been observed locally for this increment. Production ML remains disabled even if future measurements pass; adoption requires a separate recommendation and rollback procedure.

## Reproduce

Use JDK 17, Android SDK 36 and the pinned Phase 15 Python dependencies. Run from the repository root:

```text
gradlew :app:testDebugUnitTest :app:detekt :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
python docs/evaluation/phase15/candidate_v15/historical_tests.py
python docs/evaluation/phase15/candidate_v15/integrity.py
python -m unittest discover -s docs/evaluation/phase15/candidate_v15 -p test_*.py -v
python docs/evaluation/phase15/candidate_v15/v15_evaluate.py
```

Run `CandidateV15DeviceTest`, `CandidateV14DeviceTest` and `CalendarV13StorageTest` only against their isolated fixture databases/keys. Never clear installed personal data. The author/projection scripts are development utilities; editing frozen artifacts or regenerating an already scored classifier export is not a reproduction step.
