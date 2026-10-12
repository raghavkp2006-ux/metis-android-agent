"""Closed scope corrections and recall productions. No query/example lookup."""
import json
import sys
from v12_data import HERE, ROOT
sys.path.insert(0,str(HERE.parent / 'read_coverage_v10'))
from v10_grammar import PREFIX, SUFFIX

def form(intent,expression,mode='topic'):
    return dict(intent=intent,expression=PREFIX+expression+SUFFIX,mode=mode)

FORMS=[
 form('WHAT_DID_I_PROMISE',r'remind me of (?:the|my) promises i made to (?<reference>.{1,100}?)','person'),
 form('CHECK_MEMORY',r'remind me of (?:my |the )?notes (?:about|on|regarding) (?<query>.{1,300}?)'),
 form('CHECK_MEMORY',r'can you remember what i told you in (?:the )?(?<query>.{1,300}?)'),
 form('CHECK_MEMORY',r'look for anything i saved on (?<query>(?<reference>.{1,100}?) and (?<subject>.{1,200}?))','coordinated'),
 form('CHECK_MEMORY',r'so, what can you recall from (?<query>what i said to (?<reference>.{1,100}?))','speech'),
 form('CHECK_MEMORY',r'did i leave any notes behind for a (?<query>meeting with (?<reference>.{1,100}?))','meeting'),
 form('CHECK_CALENDAR',r'do i already have (?:some |any )?plans for (?:today|tomorrow)(?: (?:morning|afternoon|evening))?','none'),
 form('CHECK_CALENDAR',r'which meetings are coming up next','none'),
 form('CHECK_CALENDAR',r'have (?:i got )?any meetings with (?<reference>.{1,100}?) this week','calendar_person'),
 form('CHECK_CALENDAR',r'when do i have to meet again concerning (?:the )?(?<query>.{1,200}?)','calendar_topic'),
]
RESERVED=PREFIX+r'remind me of (?:(?:my |the )?notes (?:about|on|regarding) |(?:the|my) promises i made to )'


def kotlin():
    quote=lambda text:json.dumps(text,ensure_ascii=False)
    chunks=lambda value,indent:(' +\n'+' '*indent).join(quote(value[i:i+85]) for i in range(0,len(value),85))
    forms=',\n'.join('        ReadFormV12(AgentIntent.'+f['intent']+', Regex(\n            '+chunks(f['expression'],12)+
        ',\n            RegexOption.IGNORE_CASE), '+quote(f['mode'])+')' for f in FORMS)
    return ('package dev.metis.agent.domain.agent\n\n'
        '/** Generated from candidate_v12/v12_grammar.py; complete productions only. */\n'
        'internal object ReadPatternsV12 {\n'
        '    val reserved = Regex(\n        '+chunks(RESERVED,8)+', RegexOption.IGNORE_CASE)\n'
        '    val forms = listOf(\n'+forms+'\n    )\n}\n\n'
        'internal data class ReadFormV12(val intent: AgentIntent, val pattern: Regex, val mode: String)\n')


if __name__=='__main__':
    (ROOT / 'app/src/main/java/dev/metis/agent/domain/agent/ReadPatternsV12.kt').write_text(kotlin(),encoding='utf-8',newline='\n')
