# Human input for the next Phase 15 gates

No human input is required to reproduce v11. The current model still misses the adoption target and the new corpus exposes parser scoping gaps, so a final independent evaluation is premature.

## Development and calibration input

Natural human wording can help the next development candidate. A practical starting contribution is 30–50 requests written in your own words, especially how you ask about promises/commitments and how you distinguish looking up an existing task from creating a new one. Include memory recall, calendar questions, ambiguous references, unsupported constraints, negation and combined actions too. This starting batch is not a sufficient calibration sample or a promised statistical acceptance size; calibration coverage must reflect the intended deployment mix.

Use fictional names and subjects. For each request, provide:

| Field | What to record |
| --- | --- |
| Request | The exact wording you would type |
| Intended category | Tasks, memory, promises, calendar or other |
| Expected behavior | Read saved records, ask for clarification or report unavailable |
| Retrieval subject | The complete topic or saved person's name, if applicable |
| Constraints | Any requested date, status, limit, location, attribution or requested field |
| Ambiguity | Whether more than one reasonable interpretation exists |

Offsets can be generated from the exact submitted wording and reviewed; authors do not need to count UTF-16 units. Preserve disagreements for review rather than forcing an ambiguous request into a read label. Calendar remains unavailable; exact unique saved-person resolution is a separate repository check. Questions asking for facts may correctly return no saved match.

Queries shared for development become exposed data. They may be used for training or diagnosis under explicit provenance, or reserved for calibration before use, but a query used for fitting cannot also calibrate or independently evaluate the same candidate. Split related paraphrases and speakers/scenarios together. Decide and record each batch's purpose before access; do not recategorize a seen development batch as an unseen evaluation.

## Fresh evaluation after candidate freeze

The final evaluation must be a separate batch authored and reviewed after the complete candidate is frozen, without copying the repository's templates or iterating on the model's predictions. Author/reviewer roles, independence limits, category coverage and acceptance policy must be agreed before collecting or scoring it. Keep expected outcomes reviewed and unavailable to model fitting, calibration and selection. Freeze IDs, annotations, model/parser hashes and the first result; retries or corrections do not erase exposure.

No fresh human approval or evaluation is claimed by v11. Existing exposed benchmark approvals/results remain unchanged. Physical-device retrieval tests already performed do not satisfy Android model integration or resource-measurement gates.
