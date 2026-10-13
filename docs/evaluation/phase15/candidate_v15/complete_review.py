"""Assistant-authored completion proposal. Human review is recorded separately.

Subjects below are authored from requests, not copied from parser predictions.
Empty subjects preserve missing information. Grouping supplies no conversational context.
"""
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent

# ordinal | complete literal subject span | missing references/capability restriction
# An explicit reason always prevents access. No row resolves a pronoun from another row.
EARLY = '''
1|send her the notes|her may refer to Riya but this binding needs a complete validated construction
2||missing promise content; recorded last week is an unsupported retrieval-date expression
3|turn in the lab report|
4|help Kabir with his project|his reference requires a validated binding to Kabir
5|call Meera post-class|
6|purchase the parts|conditional action; missing antecedent; action confirmation required
7|the antenna project|
8|get the presentation done today|outer negation and uncertain statement; clarify recall intent
9|the robotics competition plan|
10|Aarav in relation to the report|clarify whether literal notes content or person identity lookup
11|Priya|clarify literal note subject versus person attribution
12|post the source code|missing named promise recipient
13|reach Rahul|word-location search is unsupported; clarify record type
14|the assignment|advice versus saved deadline unclear; Friday ambiguous
15|the deadline for the prototype|multiple fragments; clarify single request and record type/date field
16|didn't want to go to the meeting|missing concrete meeting
17|don't have to help him out|him unresolved; retain negative content and Sameer recipient
18|wouldn't submit it this week|it unresolved; negative future promise convention and recipient need clarification
19|Neha and database project|
20|what I said to Vikram|clarify literal note attribution; do not infer relationships
21|send the document to Ananya|
22|email the document to Ananya tomorrow|note creation action; existing action-confirmation boundary
23||tomorrow afternoon time-of-day filter unsupported
24||
25||
26|the research project|
27|a meeting with Ishita|
28|meeting Karan|new advice or unresolved follow-up; no saved-store scope
29|have it done by Monday|it unresolved; Monday ambiguous; advice versus lookup unclear
30|the website project|missing named promise recipient
31|deploy the app|missing named promise recipient
32|look at the document|statement rather than explicit retrieval request; missing recipient
33|send the file to someone|missing named recipient; clarify uncertainty without treating it as an action
34|do this for him|this and him unresolved; statement not explicit retrieval
35|done that for her|that and her unresolved
36|the presentation|earlier-task reference unresolved; clarify literal topic search versus historical reference
37|submit|missing concrete subject and record type
38|going there next week|there unresolved; retain next week as content, not a retrieval filter
39||explicit Aditya task ownership/assignee field unsupported
40|anything to do with the demo|clarify whether saved commitment to Aditya; preserve demo subject
41|the project|
42||you is not a personal saved-task subject; yesterday filter unsupported
43||Friday ambiguous; missing before boundary and task subject
44|the report|
45|meet Sana in Chennai|
46||self recipient is not a named saved person; departure condition unsupported
47|send you only 3 files|you unresolved despite named Dev; quantity must remain in subject
48|get in touch with Rohan|clarify incomplete task wording; requested due date retained
49|send the slides to Maya|two requests including assignment; her unresolved; no access
50|the workshop|compound memory/task request; cross-store search unsupported
'''

ATTACHMENT = '''
1|send the circuit diagram|missing named promise recipient
2|get Meera some work done|
3|upload the report|
4|contacting Rohan|
5|help Kavya with her presentation|her requires validated binding to Kavya
6|my talk with Dev|
7|the robotics demo|
8|purchase in terms of sensors|
9|see her document|statement/advice versus recall unclear; her unresolved
10|the prototype|missing named promise recipient
11|Ishaan and the workshop|
12|send an e-mail to the professor|professor is a role reference; clarify if literal task subject or a person reference
13|send an email to the Professor tomorrow|appointment creation action
14|share with Aditi|confirmed new advice; no saved-record read
15|meet Arnav this weekend|missing promise recipient; this weekend is content restriction, not retrieval-date metadata
16|the design review|
17|project meeting|
18|the lab discussion|
19|Tara|
20|do coding today|missing named promise recipient
21|won't say no to the competition|statement not explicit recall; retain negative content
22|not be at the workshop|missing named recipient; retain negative promise content
23|buy resistors|
24|the app|
25|the report|
26|that deadline for the presentation|outer negation; clarify date field and recall intent
27|submit the file|you unresolved; advice versus stored record unclear
28|send the slides before Friday|missing named recipient; retain before Friday as content restriction
29|call Riya|yesterday retrieval date unsupported; task versus advice clarification
30|when I called Riya|clarify literal call content versus record creation date
31|the prototype and Sameer|
32||clarify past saved promises to Sameer versus advice about new promises
33|the web site|missing named promise recipient
34|test the API|actual scheduled reminder lookup unsupported; do not substitute tasks
35|go and check on the database|
36||other person's task ownership unsupported; record type unclear
37|prep steps for the demo|
38|send the notes after class|missing named recipient; after class remains content
39|the notes|missing named recipient; reverse-recipient search unsupported
40|the person's name I was supposed to call|missing concrete call/person subject; requested stored name
41|do after the meeting|meeting unresolved; self commitment versus note unclear
42||relative-to-class time filter unsupported; class unresolved
43||
44||online is a requested content constraint, not a supported calendar metadata field
45||Friday ambiguous
46||next week retrieval date unsupported
47|finish the prototype before the review|advice versus saved task unclear; review unresolved
48|would not be ready by Monday|statement not explicit read; Monday ambiguous; retain negative content
49|submission deadlines|clarify notes content versus task due-date metadata
50|the DSP assignment|meaning of last minute unclear
51|antenna|confirmed cross-store search unsupported
52|the antenna project|
53|not doing the antenna|missing recipient; broken-promise inference unsupported
54|run simulation|
55|running the simulation|
56|simulation task|
57|that assignment|that assignment unresolved; requested due-date field
58|execute the simulation|requested stored location; clarify which simulation if needed
59||afterwards and conversation reference unresolved; relationship inference unsupported
60|Arjun|
61|give the final report to Arjun|
62|wouldn't send him the draft|him unresolved; missing recipient; preserve negative content
63|that report|that report and record type unresolved
64|give him/her the report|named recipient missing; him/her unresolved
65|Vikram|
66|Vikram|new conditional advice; no saved-record lookup
67|Vikram|clarify literal task subject versus stored person relationship
68||
69|the meeting|missing concrete meeting; requested stored subject
70|that meeting|missing concrete meeting; requested stored location
71|the meeting|missing concrete meeting; preserve Chennai location constraint
72|where that was|that unresolved; requested stored location
73|the meeting was canceled|statement not retrieval; meeting unresolved
74|rescheduling it|it unresolved
75|the rescheduling of the meeting|meeting unresolved; clarify notes versus commitment
76||clarify existing promises to Priya versus new advice
77|send something to her|her and something unresolved; request versus commitment distinction
78|send that|that unresolved; recipient missing; speculative advice versus recall
79||clarify existing promise to Priya versus future advice
80|this to be sent|this unresolved; date field unspecified
81|Priya|confirmed record type/relationship clarification before access
82|that chore|that chore unresolved
83|Priya|clarify literal task content versus stored person relationship
84|Priya|clarify calendar subject versus named meeting person
85|Priya|outer negation; last week recorded-date filter unsupported
86|my machine learning project|
87|train the model|
88|get more data|clarify statement recall versus promise recipient; no inferred recipient
89|be trained|all of you unresolved; outer negation; clarify today date field
90|wouldn't use the old data set|nested outer negation and unresolved it; clarify complete recall
91|data collection|
92|check the data set|
93|look at it|you and it unresolved; advice versus lookup unclear
94|read it|it unresolved; no concrete store subject
95|the reviewer was Rahul|
96|Rahul is the reviewer|they unresolved; clarify whose records and literal content
97|post the results|
98|sharing activity|missing task subject; action activity/relationship unsupported
99|share the report|cross-record comparison unsupported; that unresolved
100||count/comparison request unsupported; missing concrete task subject
101|the research paper|
102|read the paper|missing named promise recipient
103|sum up it|it unresolved
104|summarize it before Wednesday|you and it unresolved; Wednesday ambiguous
105|number of pages|missing document subject; requested stored page count
106|particular section|missing document subject; requested stored section
107|section three|
108|section three was important|statement not explicit recall
109|explain it to somebody|it unresolved; named recipient missing
110|explain it|it unresolved; named recipient missing; reverse-recipient search unsupported
111|edit Java|
112|topics of the Java language|
113|complete trees today|self recipient not a named saved person
114|binary tree|
115|that|that unresolved; requested completion state
116|DSA|
117|sliding window|name-only field filter unsupported; do not broaden to all content
118|address five issues|
119|exactly 5|missing quantity subject; preserve exact 5
120|problem limit|missing concrete problem subject; requested stored limit
'''

MESSAGE = '''
1|my GitHub project|
2|push the latest code|
3|it|it unresolved; requested completion state
4|push the code to someone|missing named promise recipient
5|that promise|that promise unresolved; reverse-recipient search unsupported
6|a branch name|missing project subject; requested stored branch name
7|not push directly to main|missing named recipient; retain negative commitment content
8|creating a pull request|
9|review a pull request|
10|pull request|missing concrete pull request; owner field/store unclear
11|merge it today|it unresolved; missing named recipient
12|fix a merge conflict|
13|that task|that task unresolved; active status must be clarified
14|doing it|it unresolved; notes date versus task due date unclear
15|my hosting plan|
16|deploy the site|
17|use GitHub Pages|missing named promise recipient
18|use Vercel|missing named promise recipient
19||subject missing; change-of-mind inference unsupported
20|final decision|subject missing; final-intention inference unsupported
21|a live link|missing named promise recipient
22|send a live link|
23|send it|it unresolved; named recipient missing
24|send it|it unresolved; clarify stored date field
25|deploy|missing concrete subject and store
26|it|it unresolved; requested completion state
27|already did it|it unresolved; cross-store record type unspecified
28|testing it after deploying|it unresolved; retain after deploying content condition
29|testing the hosted version|
30|my electronics project|
31|components|missing concrete project subject; requested stored components
32|buy a capacitor|
33|already have a capacitor|
34|don't need another one|another one unresolved; preserve negative content
35|components|
36|components|cross-store records versus notes unspecified; requested stored components
37|bring those components|those components unresolved; named recipient missing
38|bring it|it unresolved; named recipient missing
39|bring them|them unresolved; requested stored location
40|bring them to the lab|them unresolved; missing named recipient
41|the lab|requested stored location; lab identity unresolved
42|visit the lab|
43|it|it unresolved; date field and store unspecified
44||that time unresolved; overlap comparison unsupported
45|prepare before visiting the lab|
46|preparation|preparation reference unresolved; requested stored steps
47|wouldn't go there unless the simulation works|there unresolved; named recipient missing; retain negative condition
48|that|that unresolved; stored content versus inferred condition distinction
49|cancelling the lab visit|
50|check her code|her requires validated binding to Neha
51|review Neha's code|
52||cross-store relationship inference unsupported; it unresolved
53|review it|it unresolved; named recipient missing; recorded-date versus content-date unclear
54|file to review|missing review subject; requested stored filename
55|the programming language|missing project/review subject; requested stored language
56|Java|it unresolved; clarify subject
57|Java|
58|can't review it today|it unresolved; explicit negative commitment to Neha
59|review it tomorrow instead|it unresolved; named recipient missing; retain tomorrow/instead content
60|my internship application|
61|update my resume|
62|this task|this task unresolved; requested completion state
63|send my resume to someone|missing named recipient
64|send it|it unresolved; named recipient missing; reverse-recipient search unsupported
65|company|missing application subject; requested stored company
66|this application|this application unresolved; requested stored deadline, date field unclear
67|that|that unresolved
68|the same application|cross-store relationship comparison unsupported
69|the same company|they unresolved; relationship inference unsupported
70|won't apply to that company|that company unresolved; preserve negative content
71|this application|this application unresolved; contradiction inference unsupported
72|applying|latest-record filter unsupported
73|not applying|missing application subject; requested saved reason; retain negative content
74|the reason|reason and subject unresolved; inferential sufficiency unsupported
75|updating my portfolio|
76|add the new project to my portfolio|
77|which project|missing record subject; requested stored project
78|add screenshots|missing named promise recipient; too references another commitment
79|updating portfolio|clarify notes content date versus task due-date field
80|do it after the exam|it and exam unresolved; named recipient missing; retain after exam content
81|exam|missing record subject; requested stored exam
82|this reference|reference unresolved; clarify rather than guess between exams
84|the thing I had to finish after class|the thing unresolved; retain after class constraint
85|the thing|the thing unresolved; explicit instruction to clarify
87|meet him tomorrow|him unresolved; named recipient missing
88|him|him unresolved; don't infer person identity from saved records
89||missing referenced people/record subject; no uniqueness established by guesses
90|the report|her unresolved; named recipient missing
91|her|her unresolved; explicit instruction not to guess
93|the task which I mentioned|earlier task unresolved
94|task|which task unresolved; explicit clarification instruction
96|it|it unresolved; requested completion state
97|that|that unresolved
98||missing subject/reference; clarification required
'''

LATEST = '''
14|return the book|
19|the return|return reference unresolved; college is literal requested location
21|the return|return reference unresolved; preserve Kabir meeting person
25|frontend was not my responsibility|
26|the backend|
29|group meeting|
36|screenshots were optional|
37|requirement|missing subject; latest-record filter unsupported
40|registration|confirmed task due-date field; clarify which registration
46|registration|
47|the event|missing concrete event; event/deadline comparison unsupported
48||both date references unresolved; comparison unsupported
49||standalone missing-information instruction; dates unresolved; never guess
51|the fee|which fee unresolved; requested stored amount
52|reimburse someone|named promise recipient missing
53|reimburse|named promise recipient missing; reverse-recipient search unsupported
54|how much|missing amount subject; requested stored amount
55|the amount|missing record/amount subject; requested stored amount
56|payment|confirmed calendar event date; clarify which payment event
57|make the payment|
58|the payment|clarify task status versus payment transaction store
59|the payment failed|later-record time reference unresolved; latest/history filter unsupported
60|status|missing record type/subject; latest-record filter unsupported
61|follow-up related to it|it unresolved; preserve pending status
62|call Aisha about the event|event unresolved; recipient is not necessarily Aisha; require named promise recipient
63|call her|her unresolved
64|the call|call unresolved
65||three records unresolved; cross-store relationship inference unsupported
66|call|missing concrete call subject; requested stored reason
67|the venue|it unresolved; requested stored venue
68|the venue|venue/event reference unresolved
69|not call until Friday|named recipient and call subject missing; preserve negative content/until Friday
70|the call|call unresolved; earliest allowed-date inference unsupported
71|that restriction|restriction and time reference unresolved; historical change inference unsupported
73|rewrite it|it unresolved
74|rewrite it|it unresolved; named recipient missing
75|word limit|missing document subject; requested stored word limit
76|500 words|missing document/limit subject; preserve 500 constraint
77|500|missing quantity subject; stored versus assumed distinction
78|not change the methodology section|named recipient missing; preserve negative content
79|that|that unresolved; later-record and conflict inference unsupported
80|edit methodology afterward|afterward time reference unresolved
81||record references unresolved; historical comparison unsupported
82|instruction I gave myself|subject missing; latest-record filter unsupported
84|copies|missing printing subject; requested stored count
85|print one for someone else|named recipient missing; one and someone else unresolved
86|that person|person reference unresolved; store unspecified
87|printing|
88|printing location|missing printing subject; requested stored location
89|near campus|it unresolved; preserve literal location restriction
90|that exact location|location/record unresolved; exact stored location requested
91|shop name|standalone no-invention instruction; missing record subject
92|submitting the report physically|
93|online submission|
94||both records unresolved; requirement/relationship inference unsupported
96|email it|it unresolved; named recipient missing; preserve too without inferring linkage
97|the email|email and recipient unresolved; record type unspecified
98|their name|their unresolved; requested stored name
99|their email address|their unresolved; requested stored email address
100|the address|standalone missing-information instruction; address subject unresolved
101|email|separate-from-what reference unresolved; relationship inference unsupported
'''


def authored_rows():
    result = {}
    for prefix, raw in (('user-v12-', EARLY), ('human-dev-004-attachment-', ATTACHMENT),
                        ('human-dev-004-message-', MESSAGE), ('human-intake-005-', LATEST)):
        for line in raw.strip().splitlines():
            ordinal, subject, reason = line.split('|', 2)
            key = prefix + f'{int(ordinal):0{2 if prefix == "user-v12-" else 3}d}'
            result[key] = dict(subject=subject, reason=reason)
    return result


def span(text, raw, kind):
    if not raw:
        return None
    start = text.find(raw)
    if start < 0:
        raise ValueError(f'Nonliteral annotation {raw!r}: {text}')
    return dict(type=kind, rawValue=raw,
                startOffset=len(text[:start].encode('utf-16-le')) // 2,
                endOffset=len(text[:start + len(raw)].encode('utf-16-le')) // 2)


def build():
    if (HERE / 'completion-approval.json').exists():
        raise ValueError('Reviewed proposal is immutable; author a separately versioned correction instead')
    projection_path = HERE / 'review-projection.json'
    source = json.loads(projection_path.read_text(encoding='utf-8'))
    authored = authored_rows()
    rows = []
    for original in source['rows']:
        if original['human_approved']:
            continue
        draft = authored[original['id']]
        text, subject, reason = original['utterance'], draft['subject'], draft['reason']
        category = original['intended_category_draft']
        # Explicit new user answers override conflicting draft meanings only in this proposal.
        if text == 'Did I specify a payment date?':
            category = 'CHECK_CALENDAR'
        if re.search(r"\bdid i say (?:that )?i (?:would|will|wouldn't|won't)|\bdid i say i['’]d", text, re.I):
            category = 'WHAT_DID_I_PROMISE'
        if original['id'] == 'human-dev-004-attachment-015':
            category = 'WHAT_DID_I_PROMISE'
        if original['id'] == 'human-dev-004-attachment-022':
            category = 'WHAT_DID_I_PROMISE'
        kind = {'CHECK_TASKS': 'TASK', 'CHECK_MEMORY': 'MEMORY',
                'CHECK_CALENDAR': 'TITLE', 'WHAT_DID_I_PROMISE': 'TITLE'}.get(category, 'TITLE')
        annotation = re.sub(r'^(?:the|a|an|my) ', '', subject, flags=re.I)
        entities = [item for item in [span(text, annotation, kind)] if item]
        # Named people are annotated literally; only promise recipients authorize unique-person resolution.
        names = re.findall(r'\b(?:Riya|Arjun|Kabir|Meera|Nisha|Aarav|Priya|Rahul|Neha|Vikram|Ananya|Ishita|Karan|Sameer|Aditya|Sana|Dev|Maya|Rohan|Kavya|Naina|Ishaan|Aditi|Arnav|Tara|Kunal|Anika|Aisha)\b', text)
        recipient = ''
        if category == 'WHAT_DID_I_PROMISE':
            match = re.search(r'\b(?:told|tell|promised?|offered) (Riya|Arjun|Kabir|Meera|Nisha|Sameer|Aditya|Maya|Dev|Neha|Rahul|Kavya)\b', text)
            if match:
                recipient = match[1]
                person = span(text, recipient, 'PERSON')
                if person not in entities:
                    entities.append(person)
            if not recipient and not reason:
                reason = 'missing explicit named promise recipient; a mentioned person is not automatically the promise recipient'
        if category == 'CHECK_CALENDAR' and names:
            for name in names:
                person = span(text, name, 'PERSON')
                if person not in entities:
                    entities.append(person)
        status = ''
        if category == 'CHECK_TASKS':
            status = ('COMPLETED' if re.search(r'\b(?:completed|complete|finished|already done|already do|did i do|did i already)\b', text, re.I)
                      and not re.search(r'not completed|uncompleted', text, re.I) else
                      'ANY' if re.search(r'\b(?:have i|did i|do i|was there|is there|status|due date|deadline)\b', text, re.I)
                      else 'OPEN')
        requested = []
        for pattern, field in ((r'due date|deadline', 'due date'), (r'\bstatus\b', 'status'),
                               (r'\bamount\b|how much|fee', 'amount'), (r'\bname\b', 'name'),
                               (r'email address', 'email address'), (r'\blocation\b|\bwhere\b|venue', 'location'),
                               (r'word limit|500 words', 'word limit'), (r'how many|number of|copies', 'count'),
                               (r'\bdate\b|\bwhen\b', 'date'), (r'\breason\b|\bwhy\b', 'reason')):
            if re.search(pattern, text, re.I):
                requested.append(field)
        if category != 'CHECK_TASKS':
            requested = ['stored deadline' if field == 'due date' else field for field in requested]
        date_constraints = re.findall(r'\b(?:last week|next week|this weekend|this Friday|tomorrow afternoon|tomorrow|today|yesterday|this week|Friday|Monday|Wednesday)\b', text, re.I)
        negative = bool(re.search(r"\b(?:not|don't|didn't|wouldn't|won't|can't|wasn't|haven't)\b", subject, re.I))
        constraints = dict(verbatim_request=text, status=status, named_recipient=recipient,
                           named_people_literal=names, requested_stored_fields=requested,
                           date_expressions_verbatim=date_constraints,
                           date_role='requires clarification unless explicitly noted below; never infer retrieval metadata from content',
                           negative_content=negative, restriction=reason,
                           preserve_missing_information_instructions=original['missing_information_instruction'],
                           do_not_infer='No conversation history, identities, relationships, final intentions, missing fields or date values')
        if text == 'What I said I will do with Arjun last week?':
            constraints.update(date_role='CREATED: recorded/stated last week; unsupported v15 date expression')
        if text == 'Did I mention the registration deadline?':
            constraints.update(requested_stored_fields=['task due date'], date_role='requested DUE field; no date predicate')
        if text == 'Did I specify a payment date?':
            constraints.update(requested_stored_fields=['calendar event date'], date_role='requested START field; no date predicate')
        if original['id'] in ('user-v12-24', 'user-v12-25', 'human-dev-004-attachment-043', 'human-dev-004-attachment-068'):
            constraints['date_role'] = 'calendar lookup; explicit this week uses captured clock/timezone; otherwise no inferred date'
        disposition = ('unsupported_capability' if re.search(r'unsupported|inference|comparison|cross-store|reverse-recipient', reason)
                       else 'action_confirmation' if re.search(r'creation action|action.*confirmation|conditional action', reason)
                       else 'clarify_reference' if reason else 'read')
        if category == 'UNKNOWN' and not reason:
            disposition = 'clarify_intent'
        # Scenario families group leakage, never resolve request references. Draft groups are deliberately conservative.
        family = original['family_draft']
        if not family:
            if original['source'].startswith('human_development_004') and 'attachment' in original['id']:
                n = original['source_ordinal']
                family = next('scenario-attachment-' + topic for end, topic in
                              ((14, 'general'), (30, 'events'), (50, 'prototype'), (64, 'simulation'),
                               (85, 'people'), (100, 'model'), (110, 'paper'), (120, 'java')) if n <= end)
            else:
                family = 'scenario-' + ('unresolved-reference' if reason else category.lower())
        rows.append(dict(id=original['id'], utterance=text, category=category, entities=entities,
                         complete_subject=subject, constraints=constraints, expected_disposition=disposition,
                         disposition_definition='Requested safe outcome, not a prediction; unsupported grammar is a separate coverage gap',
                         proposed_family=family, duplicate_of=original['duplicate_of'],
                         annotation_provenance='assistant-authored completion proposal; not approved until explicit review',
                         human_approved=False, fit_eligible=False))
    assert len(rows) == len(authored) == 325
    result = dict(version='v15-completion-proposal-001', source_projection_sha256=hashlib.sha256(projection_path.read_bytes()).hexdigest(),
                  all_requests_independent=True, human_approved=False, fitting_eligible=False,
                  meaning_corrections_provenance='Explicit user answers: task registration due date, calendar payment date, four corrections approved',
                  reviewed_before_fitting_required=True,
                  counts=dict(Counter(r['expected_disposition'] for r in rows)), rows=rows)
    (HERE / 'completion-proposal.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    lines = ['# V15 remaining 325 complete annotation proposals', '',
             'Assistant-authored details for user review. Existing 40 complete approvals remain unchanged. Every request is independent. No fitting or new evaluation is authorized by generating this file.', '',
             'Read means a bounded literal lookup is the intended safe outcome, subject to full grammar and scope validation. Clarify means zero context/read calls until missing information is supplied. Unsupported capabilities preserve the requested meaning instead of searching a substitute store. Actions retain confirmation. Entities use exact UTF-16 offsets in the JSON; complete subjects and every original request constraint are retained separately.', '',
             'Scenario groups are proposed only to prevent leakage; they never supply conversational context. Exact duplicates must stay together. Category changes apply only the explicit new user conventions. Production parser outputs were not used to construct these details.', '',
             '| ID | Request | Category | Complete subject | Expected disposition / reason | Proposed family |',
             '| --- | --- | --- | --- | --- | --- |']
    for r in rows:
        cells = [r['id'], r['utterance'], r['category'], r['complete_subject'] or '(missing)',
                 r['expected_disposition'] + ': ' + (r['constraints']['restriction'] or 'bounded literal saved-record lookup'), r['proposed_family']]
        lines.append('| ' + ' | '.join(cell.replace('|', '/') for cell in cells) + ' |')
    (HERE / 'completion-proposal.md').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(dict(rows=len(rows), dispositions=result['counts'], human_approved=False)))


if __name__ == '__main__':
    build()
