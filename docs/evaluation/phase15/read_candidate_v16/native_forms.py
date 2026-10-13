"""Emit native closed productions from the v16 syntax specification, not datasets."""
import json
from pathlib import Path
from v16_grammar import FORMS, PREFIX, END, ACTIVITY, MODIFIER

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def literal(text):
    value = text.replace('(?P<', '(?<')
    return json.dumps(value, ensure_ascii=False)


def chunks(text, indent):
    text = text.replace('(?P<', '(?<')
    return [indent + literal(text[start:start + 55]) + (' +' if start + 55 < len(text) else '')
            for start in range(0, len(text), 55)]


def generate():
    lines = ['package dev.metis.agent.domain.agent', '',
             'internal data class ReadFormV16(val kind: AgentIntent, val pattern: Regex,',
             '    val status: String, val mode: String, val negative: Boolean, val field: String,',
             '    val upcoming: Boolean, val meetings: Boolean, val appointments: Boolean)', '',
             '/** Generated closed productions; no benchmark IDs or complete-example lookup. */',
             'internal object ReadFormsV16 {',
             '    val activity = Regex(']
    lines += chunks(ACTIVITY.pattern, '        ')
    lines[-1] += ', RegexOption.IGNORE_CASE)'
    lines += ['    val modifier = Regex('] + chunks(MODIFIER.pattern, '        ')
    lines[-1] += ', RegexOption.IGNORE_CASE)'
    lines += ['    val forms = listOf(']
    for f in FORMS:
        pattern = PREFIX + f['source'] + END
        lines += ['        ReadFormV16(AgentIntent.' + f['intent'] + ', Regex(']
        lines += chunks(pattern, '            ')
        lines[-1] += ', RegexOption.IGNORE_CASE),'
        lines += ['            ' + ', '.join((literal(f['status']), literal(f['mode']), str(f['negative']).lower(),
                                             literal(f['field']), str(f['upcoming']).lower(), str(f['meetings']).lower(),
                                             str(f['appointments']).lower())) + '),']
    lines += ['    )', '}', '']
    target = ROOT / 'app/src/main/java/dev/metis/agent/domain/agent/ReadFormsV16.kt'
    target.write_text('\n'.join(lines), encoding='utf-8', newline='\n')
    print('Generated', len(FORMS), 'native productions')


if __name__ == '__main__':
    generate()
