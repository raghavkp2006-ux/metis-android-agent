# Phase 15: user-contributed query challenge

Evaluated on 2026-10-10 against the unchanged v3 development candidate. **NO_GO remains in effect.** This is a small intent-only diagnostic, not a completed deployment benchmark.

## Contribution and provenance

The user supplied 25 original prompts, grouped into five task, memory, promise, calendar, and reject/clarify examples. [The CSV](evaluation/phase15/read_queries_v3/user-challenge-2026-10-10.csv) preserves wording and punctuation, removing only the surrounding quotation marks used to present each example. The user's categories supply intent labels. Entity spans and expected outcomes are assistant-drafted annotations, not independently approved labels. [The manifest](evaluation/phase15/read_queries_v3/user-challenge-manifest.json) records input/data/candidate hashes.

All 25 prompts are distinct and none exactly duplicates a normalized v3 development prompt. One contributor and five examples per label do not establish population coverage or reliable accuracy estimates. The candidate's source, train data, calibration, model selection, and cutoff remain unchanged. The challenge was excluded from training and tuning. Following this evaluation it is exposed; a later tuned candidate needs a fresh benchmark for an independent claim.

The 25 challenge slots in the existing review package now contain these prompts and draft annotations. All approval/reviewer fields remain blank, and the original 100 synthetic drafts are still unapproved. No full-benchmark freezer or independent-adoption gate was bypassed. The standalone diagnostic does not enable ML.

## Results

The native JVM task exported predictions for all 25 prompts using actual production rules and the existing read-only language harness. Input/source hashes were checked. The frozen classifier was reconstructed from existing development partitions before loading the user challenge; no parameters were selected against these prompts.

| User category | Correct learned/hybrid intent |
| --- | ---: |
| Task query | 0 / 5 |
| Memory query | 3 / 5 |
| Promise query | 3 / 5 |
| Calendar query | 2 / 5 |
| Reject or clarify | 5 / 5 |

Read-intent coverage is **8/20 (40%)**, with all eight accepted intents correct. Macro F1 is **0.5052**; total intent agreement is 13/25. The existing rules identify none of the 20 read intents in this sample and reject/follow up on all five negatives. The learned fallback also accepts none of the five negatives. These are classifications, not verified reads: no model-compatible entity/outcome pipeline or calendar access was added.

Complete predictions, confidence, eligibility reasons, and native statuses are in [results-user-challenge.json](evaluation/phase15/read_queries_v3/results-user-challenge.json). A zero false-accept count on five examples is not evidence of general rejection safety and does not erase the existing ten development-suite hybrid failures.

## Findings and annotation caveats

- `What tasks have I not completed yet?` is a positive task query. The existing lexical negation guard treats `not` as a negated request; the candidate also rejects it. This needs contextual treatment of task state versus command negation.
- `Show me the stuff that’s still pending.` is blocked by the conservative apostrophe/quote filter. Ordinary contractions must be distinguished from quoted commands.
- `Do I have anything planned between 3 and 6?` is blocked by the broad conjunction filter. Time ranges must be distinguished from multiple commands; date and AM/PM are also unspecified.
- Other missed requests fall below the current confidence cutoff or predict UNKNOWN. Do not lower the cutoff from this small test to manufacture a passing result.
- `What all do I need to finish today?` includes a today filter that the current task read does not implement. Its semantic intent is a task query, but a correct intent is not proof of correct filtering.
- `Did I promise Arjun anything recently?` has an unspecified recency window. `What am I supposed to help Meera with?` could refer to commitments or broader obligations. The user placed both in promise queries; their proposed outcomes remain clarification where context is needed.
- `Remind me to submit it tomorrow.` is a creation request outside the learned read scope, not nonsense. It needs the referent/title and a time clarified; UNKNOWN here does not mean the app should permanently reject reminders.

## Reproduction

Use the pinned Phase 15 Python environment. From the repository root:

```powershell
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/read_queries_v3/user-challenge-2026-10-10.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/rules-user-challenge.tsv"
python docs/evaluation/phase15/read_queries_v3/user_challenge.py
```

This diagnostic validates the frozen development/source hashes, contributed CSV/native-input hashes, five-per-label coverage, duplicate/exposure checks, and actual native baseline provenance. It reports results without approving the full review package. Production sources and the v3 model/dataset/reference result are unchanged. No regression suite or device performance checks were rerun for this contribution.
