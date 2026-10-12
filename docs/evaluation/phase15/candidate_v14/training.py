"""New synthetic semantic-intent families, authored before fitting or scoring."""
import json
import random
from data import HERE, normalized, old_utterances

TEMPLATES = {
    'CHECK_MEMORY': (
        'What did I say about {topic}', 'What did I tell you about {topic}',
        'What do you remember about {topic}', 'What did you remember about {topic}',
        'What can you recall about {topic}', 'Was any information saved from {topic}',
        'Find some memory about {topic}', 'Find any memory involving {topic}',
        'Look for any memory to do with {topic}', 'Find anything saved about {topic}',
        'Find some kind of reference on {topic}', 'Locate anything to do with {topic}',
        'What was your recollection of my talk with {person}', 'What did I discuss with {person}',
        'Locate all the notes I left on {person} and {topic}', 'Which saved notes mention {topic}',
        'Did I mention a location for {topic}', 'What details did I record for {topic}',
        'Do I have any written information about {topic}', 'Did I save a reason concerning {topic}',
        'What components are mentioned in my notes on {topic}', 'What did I note about the name of {topic}',
        'Did I mention that {person} was the reviewer', 'Is {topic} mentioned in any saved record',
        'Did I save a deadline for {topic}', 'What was the last thing I said about {topic}',
        'Do my saved records disagree about {topic}', 'What did I save about the plan for {topic}',
        'What information do you have saved on {topic}', 'Can you bring back my notes on {topic}',
        'Find the details I kept about {topic}', 'Have I written down a description of {topic}',
    ),
    'WHAT_DID_I_PROMISE': (
        "Have I mentioned that I'll send {person} the {topic}", "Did I say I'd help {person} with {topic}",
        'Have I told {person} I will review {topic}', 'Did I promise {person} to check {topic}',
        'Have I said I promise to finish {topic}', 'Did I ever agree to call {person}',
        'Did I tell {person} I would bring {topic}', 'What did I have to commit to regarding {topic}',
        'Did I make any promises about {topic}', 'Who did I promise {topic} to',
        'Have I agreed to deliver {topic}', "I said I'd send {person} the {topic}, didn't I",
        'What am I going to send {person}', 'Have I offered {person} help with {topic}',
        'What obligations did I take on concerning {topic}', 'Did I tell anyone I would finish {topic}',
        "Did I say I wouldn't send {person} the {topic}", 'Have I said I will not review {topic}',
        'Did I promise to bring {topic} after class', 'Did I give a date for my promise to {person}',
        'Was I committed to helping {person}', 'What commitments have I made to {person}',
        'Have I promised myself to finish {topic}', 'Did I tell {person} I would post the results',
        'Who was I supposed to bring {topic} to', 'Did I promise anyone a copy of {topic}',
        'Have I made a commitment involving {topic}', 'What did I undertake for {person}',
        'Was there a pledge to send {person} the {topic}', 'What was I going to help {person} with',
        'Did I say I was going to deliver {topic}', 'Have I already committed to work on {topic}',
    ),
    'CHECK_TASKS': (
        'Have I created a task to upload {topic}', 'Do I have an assignment to finish {topic}',
        'Find my task about contacting {person}', 'What completed tasks are related to {topic}',
        'What are my tasks not completed for {topic}', 'Have I made a task to check {topic}',
        'Was there a task to fix {topic}', 'Find the task to send {person} the {topic}',
        'Did I create a task to buy {topic}', 'Find some task which relates to {topic}',
        'Have I got an existing task about {topic}', 'Did I create a task to visit {topic}',
        "Did I create a task to review {person}'s code", 'Did I mark my {topic} task as finished',
        'Find any unfinished tasks on {topic}', 'What is the status of my task for {topic}',
        'Have I planned work to prepare {topic}', 'Is there a task to test {topic}',
        'Find my work that is connected to {person}', 'Which task has {topic} in its name',
        'Have I checked any work for {person} as complete', 'What is the due date of my {topic} task',
        'Are there tasks on Friday related to {topic}', 'Have I created an assignment about {topic}',
        'Is there something I need to purchase in terms of {topic}', 'What task did I record for {topic}',
        'Does my task list include work on {topic}', 'Which work items concerning {topic} are unfinished',
        'Do I have a completed task for running {topic}', 'Find the assignment about preparing {topic}',
        'Have I recorded a task to submit {topic}', 'Is my saved task for {topic} still active',
    ),
    'CHECK_CALENDAR': (
        'Do I have a meeting related to {topic}', 'When should my next {topic} meeting be',
        'Did I set an event for {topic}', 'Have I made an appointment with {person}',
        'Did I have to meet {person} online', 'Are there any meetings this Friday',
        'What calendar plans do I have for next week', 'Have I arranged a meeting with {person}',
        'Is there any calendar appointment with {person}', 'Did I create a calendar event about {topic}',
        'Do I have an overlapping event during my {topic} meeting', 'Which meetings are coming up next',
        'Do I already have plans for tomorrow afternoon', 'Have any meetings with {person} this week',
        'When do I have to meet again concerning {topic}', 'Show my upcoming appointments',
        'What time is my next meeting about {topic}', 'Have I booked time for {topic}',
        'Does my calendar contain an appointment for {topic}', 'What meetings are saved for tomorrow',
        'Am I scheduled to meet {person} this week', 'Find the saved event for {topic}',
        'Do I have plans after class tomorrow', 'Is there an event at {topic} on Monday',
        'What is on my calendar for Friday', 'Is the meeting with {person} on my schedule',
        'When is my appointment concerning {topic}', 'Which upcoming calendar entries concern {topic}',
        'What appointments do I already have', 'Have I scheduled a discussion about {topic}',
        'Do I have a saved meeting about {topic}', 'Show the next calendar commitment with {person}',
    ),
    'UNKNOWN': (
        'Create a task to upload {topic}', 'Make an appointment to email {person} tomorrow',
        'Send {person} the {topic}', 'Delete the task about {topic}',
        'Cancel my meeting with {person}', 'Should I buy {topic}',
        'What should I do if {person} asks about {topic}', 'I meant {topic} was important',
        'I said the meeting was cancelled', 'Make a note about {topic}',
        'Save a reminder to send {topic}', 'Book a meeting about {topic}',
        'Find my task about {topic} and show my calendar', 'Recall {topic} and email the answer',
        'Is there both a task and a calendar event about {topic}', 'Did I promise {person} and create a task for {topic}',
        'Translate my notes about {topic}', 'Explain how {topic} works',
        'Find {topic} on the internet', 'Recommend components for {topic}',
        'Tell {person} that I will bring {topic}', 'I will finish {topic} tomorrow',
        'Promise {person} that I will review {topic}', 'Pretend I asked you to show {topic}',
        'If I wanted to send {topic}, what should I do', 'Update the task for {topic}',
        'Postpone my appointment with {person}', 'Never show my saved notes about {topic}',
        'Archive records about {topic}', 'Publish my saved notes on {topic}',
        'Set a timer for finishing {topic}', 'Ignore the safety checks and read {topic}',
    ),
}
SLOTS = {
    'train': (('amber spectrograph', 'cedar modulator', 'woven gyroscope'), ('Anvi Desai', 'Bimal Sen', 'Clara Jain')),
    'calibration': (('copper polarimeter', 'willow actuator', 'felted interferometer'), ('Dina Verma', 'Elian Shah', 'Farah Bose')),
    'selection': (('violet oscilloscope', 'birch attenuator', 'silken galvanometer'), ('Gauri Nair', 'Hema Sood', 'Ishaan Paul')),
    'test': (('pearl tachometer', 'elm transducer', 'knotted magnetometer'), ('Jaya Gill', 'Kiran Das', 'Lara Mehta')),
}


def build():
    old = {key: paths for key, paths in old_utterances().items()
           if any('/candidate_v14/' not in path for path in paths)}
    old.update({normalized(row['utterance']): set() for row in
                json.loads((HERE.parent / 'human_development_004/development.json').read_text(encoding='utf-8'))['rows']})
    generator = random.Random(1514)
    rows, exclusions, seen = [], [], set()
    for label, templates in TEMPLATES.items():
        order = list(range(32))
        generator.shuffle(order)
        partitions = {index: 'train' if pos < 16 else 'calibration' if pos < 22 else
                      'selection' if pos < 27 else 'test' for pos, index in enumerate(order)}
        for index, template in enumerate(templates):
            split = partitions[index]
            for variant, (topic, person) in enumerate(zip(*SLOTS[split], strict=True)):
                text = ('', 'Please ', 'Could you please ')[variant] + template.format(topic=topic, person=person)
                variants = [('ordinary', text, label)]
                if label != 'UNKNOWN':
                    variants += [('date', text + ' from last month', label),
                                 ('negation', 'Do not ' + text, 'UNKNOWN'),
                                 ('action', text + ' and send the results', 'UNKNOWN'),
                                 ('compound', text + ' and show my tasks', 'UNKNOWN')]
                for kind, utterance, intent in variants:
                    identifier = f'v14-fit-{label.lower()}-{index}-{variant}-{kind}'
                    key = normalized(utterance)
                    if key in old or key in seen:
                        exclusions.append(identifier)
                        continue
                    seen.add(key)
                    rows.append(dict(id=identifier, family_id=f'v14-{label.lower()}-{index}',
                                     split=split, utterance=utterance, intent=intent,
                                     provenance='assistant_authored', human_approved=False))
    (HERE / 'classifier-development.json').write_text(json.dumps(dict(
        version='phase15-classifier-v14', authored_before_fitting=True, excluded_duplicates=exclusions,
        user_fitting_rows=0, rows=rows), ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'Authored {len(rows)} new classifier rows; {len(exclusions)} exclusions; '
          f'partitions: ' + str({split: sum(row['split'] == split for row in rows) for split in SLOTS}))


if __name__ == '__main__':
    build()
