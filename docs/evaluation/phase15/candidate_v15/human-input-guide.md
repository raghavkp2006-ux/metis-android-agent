# Remaining human-input gates

## Now: development annotations and scenario families

All 365 complete annotations and 24 scenario families are explicitly user-approved and hash-frozen. Zero annotation gaps remain. Seed 1515 partitions are 255 training, 55 development-validation and 55 development-test. Only two calendar requests are in development test; report this limitation. The original 325-row proposal is preserved with separately approved technical corrections.

The original 50, intake-004's 214, and intake-005's 101 remain exposed development data. They are never the new independent test. Source author and label reviewer must be recorded; the currently approved requests were submitted and reviewed by the same user, a disclosed development-data limitation.

Duplicate links, scenario groups and partitions are now reviewed and frozen. The one exact duplicate and related topic scenarios must stay together. “Independent request” means no preceding-row context; grouping for leakage prevention does not resolve “it”, “her”, “later”, “the thing” or another omitted subject. The approximately 70/15/15 development split is frozen; separate calibration/selection inputs remain required before fitting. Historical inspected benchmark/model tests must not enter fitting.

## Next: 150 calibration/selection requests

Collect only after development review/family freeze. Keep these two collections separate, with explicit author, reviewer, family ID, semantic label and full scope/disposition annotations; freeze labels before showing predictions. Do not replace them with assistant-authored examples or reuse inspected historical tests.

| Semantic category | Calibration | Selection |
| --- | ---: | ---: |
| Memory | 30 | 15 |
| Calendar | 30 | 15 |
| Task | 15 | 5 |
| Promise | 15 | 5 |
| Other/unsupported | 10 | 10 |
| Total | 100 | 50 |

Calibration fits temperature only. Selection chooses configuration and cutoff only. Related scenarios and duplicates cannot cross collections or development partitions. Include natural ambiguous, unsupported, negative-content and compound/action requests, with intended meanings distinguished from permitted capabilities. A five-label semantic classifier is not authorization to execute a read/action.

The grid, cutoff floor of 0.90, acceptance criteria and single-thread requirement are already recorded in `protocol.json` and the pushed deterministic milestone. Freeze new synthetic classifier test families before fitting. A failed first scored candidate produces a versioned NO_GO; do not overwrite its result or tune against it.

## Later: 150 genuinely fresh evaluation requests

Wait for the model/parser/threshold/annotation/protocol freeze before requesting them. One person authors and a different person reviews 45 memory, 45 calendar, 20 task, 20 promise and 20 other/unsupported requests. Neither sees predictions before labels are frozen. No development, calibration or selection request may be reused. Score once and preserve the first result, authorship disclosures and sample limitations.

## Device access

The connected API-36 phone has already run isolated deterministic storage and calendar UI tests without clearing personal data. Keep it available when Android ML testing becomes eligible. Full API 26/36 instrumentation passed remotely; local API 26 storage/navigation fixtures also passed. ML parity/latency/PSS/three paired battery workloads remain later gates; deterministic phone tests do not satisfy them. Production ML stays disabled throughout.

## Calibration intake 006 received

[Reviewed partial collection](../calibration_intake_006/README.md) contains 101 submissions / 99 unique requests, with explicit purpose and label/family approval before predictions. Unique labels: 73 memory, 19 task, three promise, four other, zero calendar-event lookups. The user agreed to send 30 calendar-event, 12 promise and six other additions. Exact 100-row subset selection still needs approval after additions; excess rows stay unused. The separate 50 selection requests are still pending. No fitting, temperature selection or predictions have run.
