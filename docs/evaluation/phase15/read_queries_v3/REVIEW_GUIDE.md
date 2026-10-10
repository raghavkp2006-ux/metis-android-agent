# Your Phase 15 benchmark review

**Pending your review.** The classifier has not been scored on this package. Keep real messages, contact details, audio, and other personal data out of it. All examples should remain fictional English/en-IN requests.

Open [review-package.csv](review-package.csv). It contains 100 suggested examples and 25 blank challenge rows. Review the examples before inspecting model predictions. Corrections are welcome; draft labels are suggestions, not approved truth.

## Fill the package

For the 100 draft rows, check the request, correct its meaning and entity spans as needed, then fill:

- `reviewed_intent`: one of the five labels below.
- `reviewed_entities`: a JSON array, including `[]` when no entity is resolved.
- `reviewed_outcome`: `read`, `clarify`, `reject`, or `unsupported`.
- `reviewer`: your chosen reviewer name, consistent across rows.
- `approved`: `yes` after review.

For the 25 blank challenge rows, write **five new prompts per label**, without copying or lightly editing corpus templates. Include normal, short, Indian English, voice-like, ambiguous, and difficult negative wording. Leave `human_authored=yes` and fill the same annotation columns. These fresh prompts are evaluated separately; they are not training data.

| Intent | Labeling guidance |
| --- | --- |
| CHECK_TASKS | Ask to view existing tasks. No entity required; an unambiguous query has outcome `read`. |
| CHECK_MEMORY | Ask to retrieve stored information about a topic. A `read` needs exactly one `MEMORY` entity. |
| WHAT_DID_I_PROMISE | Ask about an existing commitment to a person. A `read` needs exactly one `PERSON` entity. |
| CHECK_CALENDAR | Ask about schedule/availability. Use `unsupported` because calendar access is unavailable, or `clarify` for ambiguity. |
| UNKNOWN | Out-of-scope, negated, quoted, hypothetical, injected, compound, or creation requests. Use `reject`/`clarify` and `[]` entities. |

Missing or ambiguous topic/person details should receive `clarify`, not guessed entities. The package evaluates semantics; a read label does not authorize a mutation. Creation requests belong to `UNKNOWN` **for the learned candidate**, even where existing rules recognize them.

Entity spans use UTF-16 `[startOffset,endOffset)` in the **original** text. Example: `Find notes about optics` has:

```json
[{"type":"MEMORY","rawValue":"optics","startOffset":17,"endOffset":23}]
```

An emoji occupies two UTF-16 units. Don't lowercase or normalize the original text when measuring offsets. For reads, include one complete topic/person entity; leave ambiguous entities unresolved.

## Freeze and evaluate after review

Use the same Python environment as the experiment. Replace `<reviewer-name>` with the exact name entered in the CSV. From the repository root:

```powershell
python docs/evaluation/phase15/read_queries_v3/review.py freeze --reviewer "<reviewer-name>"
python docs/evaluation/phase15/read_queries_v3/review.py prepare-reviewed-input
.\gradlew.bat --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=app/build/reports/phase15/rules-input-reviewed-v3.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/rules-reviewed-v3.tsv"
python docs/evaluation/phase15/read_queries_v3/evaluate.py --reviewed-benchmark docs/evaluation/phase15/read_queries_v3/review-frozen-v3.json --baseline app/build/reports/phase15/rules-reviewed-v3.tsv --output app/build/reports/phase15/results-reviewed-v3.json
```

The freezer refuses incomplete annotations, duplicate/exposed text, invalid spans, missing human-authored coverage, and overwriting an existing frozen benchmark. Source changes invalidate the frozen package. Scoring creates a local receipt and refuses a second attempt on the same benchmark. Any subsequent tuning requires fresh review prompts.

You can return the completed CSV and ask for evaluation instead of running these commands. This review is a prerequisite; it does not by itself pass the remaining Android, entity/outcome, calibration, or device-cost gates.
