# V15 deterministic regression report

Scope: exposed development evidence; no new independent evaluation, human macro-F1 or ML measurement. Improvements come from deterministic parsing and scoped storage.

| Check | Result |
| --- | --- |
| Native/Python replay | 10,234 cases: 9,366 retained, 767 new development, 101 intake-005 |
| Retained admissions | All 785 preserved; 23 additional concrete scoped requests admitted |
| New independent assistant-authored gold | 90 valid scoped reads, 677 prohibited/boundary cases; zero prohibited reads |
| Latest 101 runtime diagnostic | 11 bounded admissions; ungraded, 325/365 complete annotations still pending; all draft meanings approved |
| Original approved benchmark | 23/23 valid reads; 0 reads in all 122 reject/clarify/unsupported cases, including the original 14 failures |
| V9 development | 299/299 valid task/memory/promise reads; 0 reads in 1,300 clarification cases; 100 formerly unavailable calendar forms retain v13 local-calendar support |
| Entity/query evidence | Exact UTF-16 annotation and complete-query agreement on admitted cases; all 90 new typed scopes match independently authored gold |
| Dispositions/constraints | Native dispositions reported separately; follow-ups preserve the entire request, unsupported reminder/cross-store/assignee meaning retained |
| Forced labels | 40,936 read-label trials; invalid requests or mismatched labels cause zero context and read-port calls |
| Forged inputs | Modified scope kind, subject, status, date field/window, recipient, requested field, polarity; modified entity type, offsets and normalized query all blocked |
| JVM | 136 tests passed, including retained behavior and confirmation boundaries |
| Python | 122 passed: 47 retained v8–v14, 7 v15, 13 baseline, 26 v3, 22 v5 and 7 v6 tests |
| Historical evaluators | V2–v6 replayed successfully into ignored output; original decisions/results retained |
| Static/build | Detekt, debug/release lint, debug/release APK and instrumentation compilation passed |
| Physical Android | 18 tests passed on CPH2691, Android 16/API 36, including calendar UI add/edit/delete; no personal data clearing |
| Updated v15 phone fixtures | All four tests passed again with 90 valid scopes and paired prohibited forms (66.116 s) |
| Remote CI before certificate fix | Compilation/JVM/lint/Detekt/APKs and API 36 instrumentation passed; certificate failed from checkout metadata line endings; API 26 composer-close test failed on three attempts |
| Historical evidence | Byte-preserved v6–v14, original first evaluation, intake-004 and intake-005; current shared sources certified only by v15 |

The 23 newly admitted retained requests were previously unsupported content/existence/topic forms. Their scopes are listed in the ignored runtime report rather than relabeling historical approvals. Existing valid v7/v8/v9/v10/v12/v14 forms remain preserved. A finite regression suite does not prove correctness for arbitrary wording.

Encrypted fixtures check all 90 new valid scopes with matching records and decoys, plus their paired denied variants. Additional tests check metadata-first status/due filtering, completed-task existence versus OPEN list scope, missing dates/email, incomplete candidate scans, duplicate/missing/incompletely scanned saved people, negative/positive commitment separation, historical calendar entries and unchanged stored records. Valid reads may honestly return no match or an incomplete result.

The first phone run found a context-clock mismatch, fixed by binding v15 read context and capability snapshot to the captured request instant/timezone while preserving action behavior. Final device tests passed after the fix. The calendar UI smoke initially could not find a Compose hierarchy with another app foregrounded; it passed after foregrounding the isolated test host. Raw failed and successful logs remain in ignored build output; no frozen evaluation was overwritten.

Fresh human evaluation, representative calibration/selection and Android ML measurements are unexecuted pending prerequisites. Production has no ML artifact or enabled classifier.

The next validated increment adds the complete `Do I have a task for SUBJECT?` existence form, preserving its full literal subject and ANY-status scope. One new assistant-authored family with three subject slots and prohibited counterparts were authored before grammar implementation. Native/Python replay and encrypted fixtures cover this addition. Twenty-five human annotations are approved; remaining drafts and all scenario groups stay ineligible for fitting. The certificate fix applies LF normalization only to `.gitattributes` metadata, preserving raw hashes for historical evidence. An exported LF checkout reproduces the corrected check; API 26 remains a named failing gate pending investigation.

After commit `03e275c`, both the dedicated v15 workflow and the full repository build/evaluator job passed remotely. API 36 instrumentation passed; API 26 still failed the composer-close test. The next test-only change advances the Compose animation clock before locating the close button, asserts visibility and retains the real touch/dismissal assertions. All six navigation tests passed on the phone (19.528 s); API 26 verification is pending. No production navigation behavior changed.

API 26 also failed after entrance synchronization, now at the added visibility assertion: the close button remains offscreen after scrolling. The full click/dismissal assertion remains intact. Diagnostics capture bounds and the draft semantics tree; this is an open UI failure, not a passed matrix gate. Bulk user approval covers remaining semantic meanings only; missing annotation fields and `review_required` dispositions still block fitting.
