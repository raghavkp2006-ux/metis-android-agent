# Next human inputs: calibration, then selection

Development annotations are frozen. These are two new, separate collections; neither is the later independent evaluation.

Use new scenarios rather than paraphrases of earlier batches. Requests remain independent. Share a family ID only to keep related scenarios together during data partitioning; it does not supply conversation history.

| Category | CALIBRATION | SELECTION |
| --- | ---: | ---: |
| CHECK_MEMORY | 30 | 15 |
| CHECK_CALENDAR | 30 | 15 |
| CHECK_TASKS | 15 | 5 |
| WHAT_DID_I_PROMISE | 15 | 5 |
| UNKNOWN / other / unsupported | 10 | 10 |
| Total | 100 | 50 |

Copy this structure for each request, using your own query:

```text
Collection: CALIBRATION
ID: cal-001
Family: your-new-scenario-id
Request: <your natural request>
Intended category: <one of the five labels>
Complete subject: <literal requested subject, or missing>
Recipient/person: <explicit name and role, or missing>
Constraints: <status, date AND what the date describes, stored fields, negation, location, other restrictions>
Expected outcome: <bounded read / clarify / unsupported capability / action confirmation>
Missing information / do-not-guess instructions: <if applicable>
Author: <name or identifier>
Reviewer: <name or identifier; same-person review must be disclosed>
```

For selection, use `Collection: SELECTION` and `sel-001` IDs. Keep calibration and selection scenario families disjoint from each other and from frozen development families. Labels are reviewed and frozen before predictions are shown. Temperature fitting uses calibration only; configuration/cutoff choices use selection only.

The later 150 independent-evaluation requests need a separate author and reviewer, and must wait until model/parser/threshold/protocol freeze. Do not send those yet. Production ML remains disabled.
