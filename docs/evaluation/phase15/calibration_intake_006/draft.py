"""Unscored calibration intake and assistant label proposal; no model/parser imports."""
import hashlib
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRIOR = HERE.parent / 'candidate_v15/review-projection.json'
QUOTA = {'CHECK_MEMORY': 30, 'CHECK_CALENDAR': 30, 'CHECK_TASKS': 15, 'WHAT_DID_I_PROMISE': 15, 'UNKNOWN': 10}
# Original ordinal | semantic category | complete literal subject | expected safe outcome | reason
ANNOTATIONS = '''
51|CHECK_TASKS|it|clarify_reference|it unresolved; requested completion status
52|CHECK_MEMORY|Phase 15|read|
53|CHECK_TASKS|Phase 15|read|existence lookup across ANY status
54|CHECK_MEMORY|Phase 15 was complete|read|literal saved statement; no inferred completion
55|CHECK_MEMORY|still open|clarify_reference|it unresolved; preserve open statement
56|CHECK_MEMORY|status|unsupported_capability|subject/store missing; latest-record filter unsupported
57|WHAT_DID_I_PROMISE|collect more queries|clarify_reference|named promise recipient missing
58|CHECK_TASKS|collecting queries|read|task existence across ANY status
59|CHECK_MEMORY|how many|clarify_reference|quantity subject missing; requested stored count
60|CHECK_MEMORY|50 or a different number|clarify_reference|quantity subject missing; preserve 50 without assuming it is stored
61|CHECK_MEMORY|development or evaluation|clarify_reference|that unresolved; requested stored batch purpose
62|CHECK_MEMORY|those queries could be reused later|clarify_reference|those queries unresolved; preserve reuse and later restrictions
63|CHECK_MEMORY|could not be reused|clarify_reference|they unresolved; retain negative saved content
64|CHECK_MEMORY|exposed data|read|requested saved rule; no new policy advice
65|CHECK_MEMORY|calibration|read|
66|CHECK_TASKS|review calibration coverage|read|task existence across ANY status
67|CHECK_MEMORY|who would review it|clarify_reference|it unresolved; requested stored reviewer
68|CHECK_MEMORY|the reviewer|clarify_reference|reviewer of what is missing; requested stored reviewer
69|WHAT_DID_I_PROMISE|freeze the candidate afterward|clarify_reference|named recipient and afterward reference missing
70|CHECK_TASKS|candidate freeze|read|task existence across ANY status
71|CHECK_TASKS|that task|clarify_reference|that task unresolved; requested completion status
72|CHECK_MEMORY|parser issues|read|
73|CHECK_MEMORY|parser gap|read|requested literal saved parser issue
74|CHECK_TASKS|fix parser scoping|read|task existence across ANY status
75|CHECK_TASKS|the parser fix|read|requested COMPLETED task status; no identity or history inference
76|CHECK_MEMORY|the adoption target was met|read|literal statement recall; do not infer acceptance
77|CHECK_MEMORY|still being missed|clarify_reference|it unresolved; preserve saved status content
78|CHECK_MEMORY|model-status|unsupported_capability|latest-record filter unsupported
79|CHECK_TASKS|rerun evaluation|read|task existence across ANY status
80|CHECK_MEMORY|before or after freeze|clarify_reference|that unresolved; preserve ordering constraint
81|CHECK_MEMORY|the ordering|clarify_reference|ordering/subject missing; do not infer sequence
82|CHECK_MEMORY|independent evaluation|read|
83|CHECK_MEMORY|premature|clarify_reference|it unresolved; literal statement recall only
84|CHECK_TASKS|collect fresh evaluation data|read|task existence across ANY status
85|CHECK_MEMORY|after candidate freeze|clarify_reference|it unresolved; retain after restriction
86|CHECK_MEMORY|fresh evaluation|read|requested all stored constraints; bounded results must report incomplete search honestly
87|CHECK_MEMORY|reviewer independence|read|
88|CHECK_MEMORY|an acceptance policy|read|
89|UNKNOWN||clarify_reference|standalone conditional missing-information instruction; no subject/antecedent; report unavailable rather than guess
90|CHECK_MEMORY|preserving the first result|read|
91|CHECK_MEMORY|retries could replace the first result|read|literal saved rule; do not authorize replacement
92|CHECK_MEMORY|retries|read|requested exact saved rule; quote only necessary stored words; do not synthesize policy
93|CHECK_TASKS|freeze hashes|read|task existence across ANY status
94|CHECK_MEMORY|hashes|clarify_reference|missing record subject; requested stored hash names
95|CHECK_MEMORY|model and parser hashes|read|one literal notes subject; no cross-store join
96|CHECK_MEMORY|IDs and annotations|clarify_reference|too refers to missing record; preserve both fields
97|CHECK_TASKS|all of those|clarify_reference|those unresolved; preserve all requested constraints
98|CHECK_MEMORY|Android integration|read|
99|CHECK_MEMORY|physical-device testing|clarify_reference|that gate unresolved; stored policy versus new advice needs clarification
100|CHECK_MEMORY|not enough|clarify_reference|it unresolved; retain explicit negative statement
101|CHECK_MEMORY|the Android gate|clarify_reference|remaining versus which saved gate/record unclear; do not infer completion
102|WHAT_DID_I_PROMISE|measure resources|clarify_reference|named promise recipient missing
103|CHECK_TASKS|measure memory usage|read|task existence across ANY status
104|CHECK_TASKS|latency|clarify_reference|one and too refer to missing task; do not infer task to measure
105|CHECK_TASKS|separate tasks|unsupported_capability|those unresolved; cross-record relationship inference unsupported
106|CHECK_MEMORY|a device for measurements|clarify_reference|which measurements missing; requested stored device
107|CHECK_MEMORY|an acceptance threshold|clarify_reference|which acceptance gate missing; requested stored threshold
108|UNKNOWN|threshold|clarify_reference|standalone missing-information instruction; threshold subject missing; report missing rather than guess
109|CHECK_MEMORY|repository checks|read|
110|CHECK_MEMORY|repository check|read|literal stored check lookup; no claim checks passed
111|CHECK_MEMORY|unique saved-person resolution|read|literal saved topic; do not resolve a person for this query
112|CHECK_MEMORY|the model gate or repository gate|clarify_reference|that unresolved; requested stored gate assignment
113|CHECK_MEMORY|that distinction|clarify_reference|distinction/record unresolved; do not infer
114|CHECK_TASKS|test saved-person resolution|read|task existence across ANY status
115|CHECK_TASKS|it|clarify_reference|it unresolved; requested completion status
116|CHECK_MEMORY|calendar support|read|notes about capability, not an event lookup
117|CHECK_MEMORY|calendar|unsupported_capability|current availability cannot be inferred from potentially historical notes; clarify a concrete saved record or explicit statement
118|CHECK_MEMORY|calendar-related development queries|clarify_reference|query creation history is not a calendar-event lookup; clarify whether explicit saved notes about those queries are intended
119|CHECK_MEMORY|calendar questions should report unavailable|read|literal stored policy; do not impose it on current capability
120|CHECK_MEMORY|always true or dependent on context|clarify_reference|that rule unresolved; do not infer policy universality
121|UNKNOWN|the saved rule|clarify_reference|standalone insufficient-information instruction; rule unresolved; preserve ambiguity response
122|CHECK_MEMORY|facts that may return no saved match|read|literal topic; honest no-match behavior
123|CHECK_MEMORY|a no-match result|clarify_reference|stored policy versus new validity advice unclear
124|CHECK_TASKS|test no-match behavior|read|task existence across ANY status
125|CHECK_MEMORY|examples of no-match questions|read|literal saved examples; no implicit authorization to fit them
126|CHECK_MEMORY|those examples|clarify_reference|those examples unresolved; no inferred exposure status
127|CHECK_MEMORY|exposed queries could be used for fitting|read|literal stored policy, not fitting authorization
128|CHECK_MEMORY|condition|clarify_reference|condition subject missing
129|CHECK_MEMORY|explicit provenance|read|
130|CHECK_TASKS|record provenance|read|task existence across ANY status
131|CHECK_MEMORY|fields|clarify_reference|missing record/subject; stored field requirements versus advice unclear
132|CHECK_MEMORY|speaker grouping|read|
133|CHECK_MEMORY|related paraphrases should stay together|read|literal saved grouping policy
134|CHECK_TASKS|grouping them|clarify_reference|them unresolved; preserve grouping task
135|CHECK_MEMORY|batch purpose|read|
136|CHECK_MEMORY|purpose must be decided before access|read|literal saved restriction; retain before access
137|UNKNOWN|a seen development batch|unsupported_capability|general policy/advice question without explicit saved-record scope; never recategorize seen data as unseen evaluation
138|CHECK_MEMORY|no|clarify_reference|missing subject; no alone does not identify negative content
139|CHECK_MEMORY|recategorizing seen data|read|requested stored rule, no recategorization action
140|CHECK_MEMORY|author and reviewer roles|read|
141|CHECK_MEMORY|those roles|clarify_reference|those roles unresolved; do not infer finalization
142|CHECK_MEMORY|independence limits|read|
143|CHECK_MEMORY|the limits|clarify_reference|which limits/agreement missing
144|CHECK_MEMORY|category coverage requirements|read|
145|CHECK_MEMORY|those requirements|clarify_reference|those requirements unresolved; do not infer freeze
146|CHECK_MEMORY|an acceptance policy|read|
147|CHECK_MEMORY|finalized or still pending|clarify_reference|it unresolved; do not infer final status
148|CHECK_MEMORY|a final independent evaluation could start now|read|literal saved statement; not current permission to start evaluation
149|CHECK_MEMORY|should wait|clarify_reference|it unresolved; preserve wait restriction
150|CHECK_MEMORY|before it starts|clarify_reference|it unresolved; requested stored prerequisite
151|CHECK_MEMORY|the candidate|clarify_reference|which candidate missing; do not infer freeze status
'''


def normalized(text):
    return ' '.join(unicodedata.normalize('NFKC', text).casefold().split())


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest()


def build():
    if (HERE / 'review-approval.json').exists():
        raise ValueError('Approved intake drafts are immutable; add a separately versioned amendment')
    prior = json.loads(PRIOR.read_text(encoding='utf-8'))['rows']
    old = {}
    for row in prior:
        old.setdefault(normalized(row['utterance']), []).append(row['id'])
    authored = {}
    for line in ANNOTATIONS.strip().splitlines():
        ordinal, category, subject, disposition, reason = line.split('|', 4)
        authored[int(ordinal)] = dict(category=category, subject=subject, disposition=disposition, reason=reason)
    rows, seen = [], {}
    for line in (HERE / 'requests.txt').read_text(encoding='utf-8').splitlines():
        match = re.fullmatch(r'(\d+)\. (.+)', line)
        assert match, line
        ordinal, text = int(match[1]), match[2]
        annotation = authored[ordinal]
        subject = annotation['subject']
        raw_entity = re.sub(r'^(?:the|a|an|my) ', '', subject, flags=re.I)
        entities = []
        if raw_entity:
            start = text.find(raw_entity)
            assert start >= 0, (ordinal, raw_entity)
            entity_type = {'CHECK_TASKS': 'TASK', 'WHAT_DID_I_PROMISE': 'TITLE'}.get(annotation['category'], 'MEMORY')
            entities = [dict(type=entity_type,
                             rawValue=raw_entity, startOffset=len(text[:start].encode('utf-16-le')) // 2,
                             endOffset=len(text[:start + len(raw_entity)].encode('utf-16-le')) // 2)]
        key = normalized(text)
        row_id = f'calibration-intake-006-{ordinal:03d}'
        rows.append(dict(id=row_id, submitted_ordinal=ordinal, utterance=text,
                         intended_collection='CALIBRATION; user explicitly confirmed',
                         conversation_context='independent_no_history', author='submitted by requesting user; authorship not independently verified',
                         annotation_provenance='assistant_draft_requested_by_user', human_approved=False,
                         intended_category_draft=annotation['category'], entities_draft=entities,
                         complete_subject_draft=subject,
                         constraints_draft=dict(verbatim_request=text, explanation=annotation['reason'],
                                                named_promise_recipient=None, no_previous_row_context=True,
                                                task_status='COMPLETED' if ordinal in (51, 71, 75, 115) else 'ANY' if annotation['category'] == 'CHECK_TASKS' else None,
                                                requested_information_rule='Preserve every submitted restriction; report missing/ambiguous stored information, never invent it'),
                         expected_disposition_draft=annotation['disposition'],
                         proposed_family='phase15-process-policy-and-status', family_human_reviewed=False,
                         duplicate_of=seen.get(key), earlier_exact_matches=old.get(key, []),
                         fit_eligible=False, calibration_eligible=False, selection_eligible=False,
                         independent_evaluation_eligible=False, predictions_run=False))
        seen.setdefault(key, row_id)
    assert [row['submitted_ordinal'] for row in rows] == list(range(51, 152))
    unique = [row for row in rows if not row['duplicate_of']]
    counts = Counter(row['intended_category_draft'] for row in unique)
    payload = dict(version='phase15-calibration-intake-006', received_date='2026-10-13',
                   source_confirmation='Yes—calibration; draft labels for my review', count=len(rows),
                   normalized_unique=len(unique), duplicate_count=len(rows) - len(unique),
                   prior_exact_overlap_count=sum(bool(r['earlier_exact_matches']) for r in unique),
                   draft_counts_unique=dict(counts), target_quotas=QUOTA,
                   draft_shortfalls={label: max(0, quota - counts[label]) for label, quota in QUOTA.items()},
                   review_required=True, labels_frozen=False, predictions_run=False, fitting_started=False,
                   fresh_independent_evaluation=False, ml_enabled=False,
                   calibration_gate='BLOCKED: draft labels need review; exact duplicates/prior overlaps excluded; quotas/family separation must be met before temperature fitting',
                   source_sha256=digest(HERE / 'requests.txt'), earlier_projection_sha256=digest(PRIOR), rows=rows)
    (HERE / 'intake.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    lines = ['# Calibration intake 006: label draft for review', '',
             'User-confirmed calibration purpose. Items 51–151 are 101 independent submissions; no earlier-row context is supplied. Labels are assistant drafts and require explicit review. No parser/classifier predictions, personal-record queries, model fitting or temperature selection were run.', '',
             f"Normalized unique requests: {len(unique)}; duplicate rows: {len(rows) - len(unique)}. Draft unique-label counts: {dict(counts)}. Draft quota shortfalls: {payload['draft_shortfalls']}.", '',
             'Questions about saved notes on calendar support are CHECK_MEMORY, not calendar-event requests. A mentioned promise without a named recipient clarifies before access. Instructions such as “If not, report unavailable” remain standalone independent requests; they cannot supply context to adjacent rows. All constraints remain verbatim in JSON.', '',
             'Family proposal: Phase 15 process/policy/status. Related paraphrases must remain together, and this family cannot appear in selection. Semantic overlap with earlier data still needs review; exact text comparison is insufficient to establish independence.', '',
             '| # | Request | Draft category | Complete subject | Draft safe outcome / explanation | Duplicate / earlier exact match |',
             '| --- | --- | --- | --- | --- | --- |']
    for row in rows:
        cells = [str(row['submitted_ordinal']), row['utterance'], row['intended_category_draft'], row['complete_subject_draft'] or '(missing)',
                 row['expected_disposition_draft'] + ': ' + row['constraints_draft']['explanation'],
                 row['duplicate_of'] or ', '.join(row['earlier_exact_matches']) or 'none']
        lines.append('| ' + ' | '.join(c.replace('|', '/') for c in cells) + ' |')
    (HERE / 'review.md').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    names = ('requests.txt', 'draft.py', 'intake.json', 'review.md')
    (HERE / 'intake-manifest.json').write_text(json.dumps(dict(version='calibration-intake-006', hash_policy='LF-normalized UTF-8',
        hashes={name: digest(HERE / name) for name in names}), indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps({key: value for key, value in payload.items() if key != 'rows'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    build()
