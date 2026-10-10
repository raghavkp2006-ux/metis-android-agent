# IRS-003: human-authored evaluation intake

**Status: annotation review pending; not frozen or scored.** The user explicitly confirmed human authorship. This is separate from the AI-assisted IRS-002 submission.

**Latest:** [revision 2](revision_2/README.md) merges the user's additions and review. Its 145-row draft now meets minimum semantic-label coverage; use that revision for further review. The original 125-row intake below remains preserved as history.

## Review files

- [Original submission](supplied-set.txt): original content with line endings normalized to LF. `review-report.json` records both the original attachment byte hash and repository-copy hash.
- [Supplied meanings and expectations](supplied-annotations.csv): unchanged annotations plus unapproved assistant draft semantic labels.
- [Annotation proposals](proposals/annotation-proposals.csv): draft labels, original UTF-16 entity spans, filters and current-capability dispositions. **All reviewed, reviewer and approval fields are blank.** `human_authored=yes` records the user's confirmation only.
- [Per-row rationale](proposals/annotation-rationale.csv): why an annotation or scope choice needs review.
- [Proposal provenance](proposals/proposal-report.json): candidate/source hashes and draft counts. It contains no measured model performance.

`prepare_review.py` reproduces these manual proposals without importing or running the candidate. It refuses an existing output directory:

```powershell
python docs/evaluation/phase15/human_review_003/prepare_review.py --output <new-proposal-directory>
```

## Findings and decisions needed

| Issue | Review decision |
| --- | --- |
| Source sections contain 25 tasks, 25 memories, 25 promises, 24 calendar and 26 reject/clarify rows | Count by final semantic intent, rather than headings |
| Draft semantic counts: 10 tasks, 27 memories, 25 promises, 21 calendar, 42 UNKNOWN | If approved, add at least 15 genuinely fresh human task-read queries and 4 calendar-read queries. More than 125 total is permitted |
| Task/reminder creation, completion/deletion and calendar edits appear in positive sections | Outside the five-label read classifier; retain as boundary cases, with supported native actions handled separately |
| Calendar expectations assume available calendar access | Preserve desired behavior, but separately approve current-capability unsupported/clarification outcomes |
| Task topic/age/status searches and promise subject/all-person searches exceed the current read port | Preserve constraints; record a feature gap instead of silently broadening the read |
| Task rows 4, 9, 14 and 23 have competing task/memory/reminder/commitment interpretations | Approve the intended route explicitly |
| Promise row 11 says the user lends a camera but asks whether the user gave it back | Resolve lender/returner identity |
| Contextual references to the book, meeting or course | Decide whether repository/conversation context exists; this intake has no context fixtures |
| Reject row 1 asks “did i NOT promise…” | Negation can be part of a valid existence query; approve the intended interpretation |
| Reject rows 10 and 25 express three days from now and today | Calendar intent, despite their original section; current calendar access remains unavailable |
| Reject row 24 mentions noon and midnight | Two distinct times are not inherently contradictory |
| Reject row 19 requests deletion with an exception | Scope/confirmation is needed; the wording is not inherently contradictory |

No exact normalized duplicate or overlap was found against the datasets listed by the intake check, including IRS-002. Normalization used the existing freezer's casefold/whitespace rule. Near-paraphrase novelty and prior use are not verified; confirm freshness separately before freezing.

## How to review the proposals

1. Read the supplied meaning and expected response alongside the proposal and rationale.
2. Correct the semantic label, exact entity text/span, filters and current expected disposition. Copy approved values into the `reviewed_*` columns; do not approve drafts blindly.
3. Keep the supplied desired response unchanged. Current-capability dispositions are separate proposals, not replacements for the user's intended behavior. If measuring future retrieval instead, document that protocol explicitly before scoring.
4. Extended filter keys such as `topic`, `subject`, `deadline_before` and `creation_age_days_gt` preserve requested constraints that the current candidate cannot represent. They are unapproved schema proposals and may reveal failures. Calendar filters are incomplete proposals. Resolve schema/time ambiguities before approving; do not delete constraints to improve a score.
5. Use local fixture reference **2026-10-09, Asia/Kolkata** for relative-date truth. Reviewer, approval and freshness attestations must come from the human reviewer. Fill `approved=yes` only after review.
6. Add the missing semantic-label coverage and rerun the existing freeze validator. Preserve the first evaluation result and consumption receipt.

The current adoption protocol requires at least 25 approved examples per label, valid entities/filters/dispositions, fresh human-authored inputs and a candidate-bound freeze before inference. This package meets authorship intake only. It does not close independent evaluation, representative calibration, repository/policy or Android/device gates. No model, production API or training data changed.
