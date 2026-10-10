# User-contributed standalone evaluation: 125 queries

Received and frozen 2026-10-10 against v4 commit `2e24bda`, before scoring or candidate changes. The user supplied 25 prompts per category, intended meanings and expected responses. Queries are preserved verbatim excluding surrounding quotation delimiters. Category labels and negative expectations are transcribed; positive expectations are summarized. Exact entity spans are not approved. The user states these are fresh examples; authorship/independence are not externally verified. Earlier annotation review remains separate and unapproved.

The manifest binds data to the prior candidate. Freeze/scoring scripts refuse to overwrite existing records. Scoring verifies source/data hashes and normalized overlap against generated v4 data and the earlier user prompts. It reconstructs the prior candidate from its existing development partitions only. No candidate or preprocessing changes were made. Results record every prompt. These checks prevent accidental reuse, not deliberate modification.

## First result

| Category | Correct intent / total |
| --- | --- |
| Pending tasks | 12/25 |
| Memory | 12/25 |
| Promises | 16/25 |
| Calendar | 14/25 |
| Reject/clarify routing abstention | 18/25 |

Hybrid macro F1: **0.6116**. Positive recognition: **56/100**, with two wrong positive labels and 42 abstentions. Accepted intent precision: 85.71% under the five-label projection. Generated F1 0.9911 did not generalize to this sample.

Reject/clarify is projected to UNKNOWN for strict routing scores, not asserted to be semantic unknown intent. Four of seven non-UNKNOWN predictions still produce simulated follow-up. Overall 22/25 negative cases receive simulated follow-up; this does not prove the exact requested acknowledgement/discussion/unsupported response. Three incorrectly reach simulated reads:

- “Find my notes about that thing from class.” accepts a vague topic.
- “What do I owe her from yesterday?” extracts `yesterday` as a person.
- “Find notes on chapter…” accepts an incomplete topic.

Calendar intent is recognized in 14/25 cases, all simulated UNSUPPORTED; the other 11 receive follow-up. No actual calendar lookup occurs. Positive entity accuracy and full response correctness cannot be computed from category labels alone. Date/status/category filters and entity extraction remain gaps; ANSWER does not prove matching retrieval. Hinglish exceeds the current intended English scope but remains included in the overall score.

## Decision and next work

**NO_GO.** This first score is independent from model fitting for unchanged v4, subject to single-contributor and unverified-authorship limitations. The sample is now exposed. Preserve this baseline; use failures for a separately versioned development revision. Later gains on the same prompts are regression improvements, not blind accuracy. Another adoption attempt needs fresh evaluation.

Priorities: clarify unresolved references/incomplete topics before reads; prevent temporal phrases becoming people; broaden short/slang/declarative recognition; represent task/person/time/status slots consistently; validate actual read outcomes; measure calibration and Android device performance. Earlier review/integration gates are not completed.

## Recorded execution

```powershell
.\gradlew.bat --offline --no-daemon :app:phase15ReadRulesSnapshot "-Pphase15ReadInput=docs/evaluation/phase15/independent_125/queries.tsv" "-Pphase15ReadOutput=app/build/reports/phase15/rules-independent125.tsv"
python docs/evaluation/phase15/independent_125/evaluate.py
```

Native export succeeded for 125 requests; its existing unit-test dependency was up to date. Python evaluation completed. No new unit tests were added. Candidate sources and generated v4 data remain unchanged. Preserve the first results; do not delete them to rescore a tuned candidate.
