# IRS-003 revision 2: coverage complete, approval pending

The original 125 rows, 19 pasted additions and the newspaper query supplied in chat are preserved here as **145 distinct review rows**. No further examples are needed to meet the current minimum label counts, if these draft labels are approved.

| Draft intent | Rows | Minimum |
| --- | ---: | ---: |
| CHECK_TASKS | 25 | 25 |
| CHECK_MEMORY | 27 | 25 |
| WHAT_DID_I_PROMISE | 25 | 25 |
| CHECK_CALENDAR | 25 | 25 |
| UNKNOWN | 43 | 25 |

The flat-rent query explicitly asks for both tasks and notes, so its draft label is UNKNOWN under the single-read-intent protocol. The newspaper query asks whether a task is on the list; it is a task read, not permission to cancel a subscription. The Hindi/Roman-script month query retains the requested task intent; language coverage should be measured honestly.

## What the supplied review approves

`supplied-additions-and-review.txt` preserves the user's review and additions. That review approves product-level category headings and desired behavior, including creation, deletion and calendar edits. This approval is recorded; it is not discarded.

The unchanged v5 benchmark has a different scope: five semantic read labels, current calendar access unavailable, and no mutation authority for ML. Managing a list can include writes in the product, but a write is not a read for this classifier. Native supported actions must retain their separate handling. The user review's comments about query 72 (self-commitment), 109 and 124 (resolvable dates) remain preserved in the source review. Their draft read-gate labels/current outcomes and rationale are visible alongside all other rows.

## Review before first scoring

- [Readable annotation review](annotation-review.md) shows each query, draft label, entities, filters, current disposition and original expectation.
- [Machine-readable proposals](annotation-proposals.csv) retain blank `reviewed_*`, reviewer and approval columns. These must not be auto-approved by the assistant.
- [Rationale](annotation-rationale.csv) records per-row scope/interpretation issues.
- [Revision provenance](revision-report.json) records source/candidate/importer hashes and authorship carried forward from the human batch.
- [Integrity check](integrity-report.json) records spans, coverage and exact overlap checks without candidate inference.

**The pending decision is approval of these v5 read-only annotation proposals**, including the separation of current behavior from the broader desired product expectations. This is not another request for 125 queries. The existing freezer requires explicit reviewer approval of entities, filters and dispositions; the pasted category-level review does not supply those values or approve this new projection.

The proposals include extended constraint keys for desired topic/subject/age/priority/date filtering. They are not features implemented by v5. Some original calendar constraints remain incomplete and context/routing ambiguities remain highlighted; correct them before approval rather than approving a known incomplete draft. Preserve the original meaning and expectation even when the candidate cannot meet it. The additional newspaper query had no supplied meaning/response; its task-existence interpretation and safe response are assistant proposals.

Once corrected and explicitly approved, freeze against the unchanged candidate and run the first score using the existing v5 workflow. Preserve its first result even if it fails. No model inference, tuning or production change has occurred, and no adoption gate is claimed passed by this intake.
