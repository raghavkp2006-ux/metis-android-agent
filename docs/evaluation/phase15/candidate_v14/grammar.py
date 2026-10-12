"""Closed memory-recall productions, with no corpus IDs or complete-example lookup."""
import json
import sys
from data import HERE, ROOT

sys.path.insert(0, str(HERE.parent / 'read_coverage_v10'))
from v10_grammar import PREFIX, SUFFIX  # noqa: E402

FORMS = [
    dict(mode='speech', expression=PREFIX +
         r'what was your recollection of (?<query>my talk with (?<person>.{1,100}?))' + SUFFIX),
    dict(mode='person_topic', expression=PREFIX +
         r'locate all the notes i left on (?<query>(?<person>.{1,100}?) and (?<subject>.{1,220}?))' + SUFFIX),
    dict(mode='topic_person', expression=PREFIX +
         r'what can you recall about (?<query>(?<subject>.{1,220}?) and (?<person>.{1,100}?))' + SUFFIX),
    dict(mode='topic', expression=PREFIX +
         r'(?:what did i (?:say|tell you) about|what (?:do you|did you) remember about|'
         r'what can you recall about|was any information saved from|'
         r'find (?:some|any) memory (?:about|involving)|look for (?:some|any) memory to do with|'
         r'find anything saved about|find (?:some|any)(?: kind of)? reference on|'
         r'locate anything to do with) (?<query>.{1,300}?)' + SUFFIX),
]


def kotlin():
    quote = lambda text: json.dumps(text, ensure_ascii=False)
    chunks = lambda value: (' +\n            ').join(quote(value[i:i + 70]) for i in range(0, len(value), 70))
    forms = ',\n'.join('        ReadFormV14(Regex(\n            ' + chunks(form['expression']) +
                         ',\n            RegexOption.IGNORE_CASE), ' + quote(form['mode']) + ')' for form in FORMS)
    return ('package dev.metis.agent.domain.agent\n\n'
            '/** Generated from candidate_v14/grammar.py; complete productions only. */\n'
            'internal object ReadPatternsV14 {\n    val forms = listOf(\n' + forms + '\n    )\n}\n\n'
            'internal data class ReadFormV14(val pattern: Regex, val mode: String)\n')


if __name__ == '__main__':
    (ROOT / 'app/src/main/java/dev/metis/agent/domain/agent/ReadPatternsV14.kt').write_text(
        kotlin(), encoding='utf-8', newline='\n')
