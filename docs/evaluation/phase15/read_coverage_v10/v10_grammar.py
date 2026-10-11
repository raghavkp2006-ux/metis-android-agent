"""Closed v10 productions, shared by native generation and Python regression parsing.

No dataset IDs or complete example strings participate in parsing.
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PREFIX = r'\s*(?:please )?(?:(?:can|could|would) you (?:please )?)?'
SUFFIX = r'[?!.]?\s*'
ARTICLE = r'^(?:my )?(?:(?:the|a|an)(?: |$))?'
SUBJECT = r'(?<query>.{1,300}?)'
PERSON = r'(?<reference>.{1,100}?)'
RELATION = r'(?:friend|cousin|aunty|aunt|uncle|sister|brother|mother|father|niece|nephew)'
MONTH = r'(?:january|february|march|april|may|june|july|august|september|october|november|december)'

TASK = (
    r'(?:what tasks do i have|(?:show|list) (?:my )?(?:(?:open|pending|unfinished|incomplete) )?tasks|'
    r'what tasks are (?:still )?(?:open|pending|unfinished|incomplete)|'
    r'do i have (?:any )?(?:open|pending|unfinished|incomplete) tasks|what is (?:still )?pending on my task list|'
    r'show my outstanding tasks|which tasks have i left unfinished|retrieve the tasks i have not completed|'
    r'give me an overview of my task list|what remains on my to do list|display my pending work items|'
    r'can i see the tasks awaiting completion|help me recall my remaining tasks|read out my unfinished task list|'
    r'what work have i still got to do|bring up my open task inventory|i would like to view my incomplete tasks|'
    r'tell me which tasks remain outstanding|fetch my current task backlog|show the work items i still owe myself|'
    r'summarize the tasks that remain open|let me inspect my pending task list|may i see my unfinished work list|'
    r'i have (?:like )?(?:\d{1,3}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|'
    r'fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty) things to do and i can[\x27’]?t remember any of them)'
)
MEMORY = (
    r'(?:what do you know about|search (?:my )?memory for|what did i (?:write(?: down)?|note|save) about|'
    r'what have i (?:written|noted|saved|recorded) about|'
    r'(?:show|find|retrieve|look up) (?:me )?(?:my )?(?:saved )?(?:notes|memories|records|details) (?:about|on|regarding)|'
    r'(?:do i have|do you have|are there) (?:any )?(?:saved )?(?:notes|memories) (?:about|on|regarding)|remind me of|'
    r'have i recorded anything concerning|bring up what i saved on|can i read my stored details for|'
    r'help me recall my notes concerning|fetch my written recollections of|find my personal entries concerning|'
    r'what information did i keep on|i want to see what i noted on|read back my saved observations about|'
    r'do my records mention|show the material i stored concerning|which of my notes discuss|'
    r'let me review my recorded thoughts on) '
)
PROMISE = (
    r'(?:what did i promise (?:to )?|what have i promised (?:to )?|what do i owe |'
    r'(?:show|list) (?:my )?promises (?:to|for) |what promises did i make to |show commitments to |'
    r'retrieve the commitments i made to |remind me what i owe |which commitments involve |'
    r'have i agreed to do anything for |bring up my promises involving |what obligations did i take on for |'
    r'can i see what i pledged to |find the commitments i recorded for |read back what i promised to |'
    r'i want to review my commitments toward |tell me about my promises for |what have i undertaken to do for |'
    r'fetch the promises i made involving |let me inspect my recorded obligations to )'
)
CALENDAR = (
    r'(?:show (?:my )?calendar|when am i free (?:today|tomorrow)|what is on my schedule|read my calendar entries|'
    r'list my scheduled events|bring up my appointments|what events have i scheduled|can i see my calendar agenda|'
    r'retrieve my planned appointments|what meetings are on my calendar|show the events in my diary|'
    r'give me a view of my schedule|which appointments have i booked|help me recall my scheduled meetings|'
    r'read back my calendar agenda|i want to inspect my event schedule|what does my appointment diary contain|'
    r'fetch my calendar commitments|let me review my scheduled engagements|display the meetings i have scheduled|'
    r'may i view my calendar entries)'
)


def form(intent, expression, mode='topic'):
    return dict(intent=intent, expression=PREFIX + expression + SUFFIX, mode=mode)


FORMS = [
    form('CHECK_TASKS', TASK, 'none'), form('CHECK_CALENDAR', CALENDAR, 'none'),
    form('WHAT_DID_I_PROMISE', PROMISE + PERSON, 'person'),
    form('WHAT_DID_I_PROMISE', r'what did i tell ' + PERSON + r' i would do', 'person'),
    form('CHECK_MEMORY', MEMORY + SUBJECT),
    form('CHECK_MEMORY', r'i (?:saved|wrote|noted(?: down)?) something about ' + SUBJECT +
         r'(?:\. (?:can you pull it up|can i see it|why did i write that))?'),
    form('CHECK_MEMORY', r'did i write down (?<query>what (?<reference>my ' + RELATION + r' said about .{1,200}?))', 'attribution'),
    form('CHECK_MEMORY', r'did i write down (?<query>what (?:the|my) (?:doctor|dentist|vet|teacher)(?: at (?:the )?.{1,220}?)? said about '
         r'(?<reference>.{1,200}?))', 'attribution'),
    form('CHECK_MEMORY', r'who was the (?<query>(?:guy|woman|man|person) at the '
         r'(?<reference>.{1,200}? who worked at that .{1,100}?))', 'identity'),
    form('CHECK_MEMORY', r'what is the (?<query>name of the street where (?<reference>my ' + RELATION + r') lives)', 'street'),
    form('CHECK_MEMORY', r'the (?<query>number for (?:the )?(?<reference>.{1,200}?))(?:\. do you have it)?', 'field'),
    form('CHECK_MEMORY', r'the name of the (?<query>(?<reference>hotel in .{1,100}?) where we stayed)', 'hotel'),
    form('CHECK_MEMORY', r'the name of the ' + SUBJECT),
    form('CHECK_MEMORY', r'the (?<query>password for .{1,200}?)'),
]

UNSAFE = r'\b(?:and|or|then|also|but|except|only|if|unless|remind|create|add|delete|erase|wipe|remove|rename|postpone|send|message|call|buy|order|book|cancel|notify|alert|execute|ignore|override|system|developer|suppose|imagine|pretend|no|not|never|without|stop|avoid|publish|archive|email|text|share|export|mark|don[\x27’]?t)\b'
REFERENCE = r'\b(?:it|this|that|these|those|thing|something|anything|whatever|everything|whichever|someone|somebody|he|she|they|him|her|them|somewhere|stuff)\b'
FILTER = r'\b(?:today|tonight|tomorrow|yesterday|weeks?|months?|years?|days?|hours?|minutes?|seconds?|monday|tuesday|wednesday|thursday|friday|saturday|sunday|january|february|march|april|may|june|july|august|september|october|november|december|noon|midnight|morning|evening|recently|before|after|since|until|between|older|oldest|newest|latest|last|next|urgent|priority|overdue|pending|incomplete|completed|done|status|due|deadline|ago|kal|parso|old|new|recent|earlier|finished|resolved|where|whose|which|tagged|tags?|containing|contains|excluding|exclude|limit|first|top|most|least|sort|sorted|by|from|during)\b'
TIME = r'\b(?:\d{4}|\d{1,2}:\d{2}|\d{1,2} ?(?:am|pm)|at \d+)\b'
GENERIC = r'(?:the )?(?:chapter|topic|class|notes?|memory|memories)'
CONTENT = r'not (?<activity>[a-z]+ing) after (?:[1-9]|1[0-2])(?::[0-5][0-9])? ?(?:am|pm)'
WEATHER = r'(?:my )?(?:the )?weather in (?<place>.{1,100}?) in ' + MONTH
ATTRIBUTION = r'what (?:(?:my ' + RELATION + r')|(?:(?:the|my) (?:doctor|dentist|vet|teacher)(?: at (?:the )?(?<place>.{1,220}?))?)) said about (?:his |her |their )?(?<subject>.+)'
IDENTITY = r'(?:guy|woman|man|person) at the (?<place>.+?) who worked at that (?<subject>.+)'
STREET = r'name of the street where my ' + RELATION + r' lives'
HOTEL = r'hotel in (?<place>.+?) where we stayed'
HOURS = r'(?:the )?(?<place>.+ (?:office|conservatory)) hours'
PASSWORD_ARCHIVE = r'password for (?:the )?(?<subject>.+) archive'
PERSON_EXCLUSIONS = r'\b(?:it|this|that|him|her|them|someone|somebody|anyone|who|me|you|my|your|our|their|myself|person|people|promise|promises|task|tasks|memory|notes|work|trip|station|insurance|renewal|library|card|book|sister|brother|mother|father|mom|dad|to|for|with|from|about|on|at|by|in|would|will|need|help|cover|review|pay|back|fly|mars)\b'
PATTERNS = {key: globals()[key] for key in ('ARTICLE', 'UNSAFE', 'REFERENCE', 'FILTER', 'TIME', 'GENERIC',
    'CONTENT', 'WEATHER', 'ATTRIBUTION', 'IDENTITY', 'STREET', 'HOTEL', 'HOURS', 'PASSWORD_ARCHIVE', 'PERSON_EXCLUSIONS')}


def kotlin():
    quote = lambda value: json.dumps(value, ensure_ascii=False)
    def expression(value, indent):
        return (' +\n' + ' ' * indent).join(quote(value[i:i+85]) for i in range(0, len(value), 85))
    declarations = '\n'.join(f'    val {name} = Regex(\n        {expression(value, 8)}, RegexOption.IGNORE_CASE)' for name, value in PATTERNS.items())
    forms = ',\n'.join(f'        ReadFormV10(AgentIntent.{f["intent"]}, Regex(\n            {expression(f["expression"], 12)},\n            RegexOption.IGNORE_CASE), {quote(f["mode"])})' for f in FORMS)
    return ('package dev.metis.agent.domain.agent\n\n'
        '/** Generated by read_coverage_v10/v10_grammar.py; complete productions, no corpus lookup. */\n'
        'internal object ReadPatternsV10 {\n' + declarations + '\n    val forms = listOf(\n' + forms + '\n    )\n}\n\n'
        'internal data class ReadFormV10(val intent: AgentIntent, val pattern: Regex, val mode: String)\n')


if __name__ == '__main__':
    (ROOT / 'app/src/main/java/dev/metis/agent/domain/agent/ReadPatternsV10.kt').write_text(kotlin(), encoding='utf-8', newline='\n')
