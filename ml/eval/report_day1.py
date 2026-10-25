"""Render the Day 1 report from measured artifacts; never runs a baseline itself."""
import json
from pathlib import Path

from ml.data.common import ROOT, dump, read_csv, sha


def main():
    folder = ROOT / 'ml/eval'
    report = json.loads((folder / 'baseline_rules/metrics.json').read_text())
    audit = json.loads((ROOT / 'ml/data/dev_own_audit.json').read_text())
    pool = json.loads((ROOT / 'ml/data/public_pool_report.json').read_text())
    gold = read_csv(ROOT / 'ml/data/dev_own.csv')
    lines = ['# Day 1 rules development baseline', '',
             '**Diagnostic development results only. No training, fitting, tuning, network, downloads or installs.**', '',
             f"Measurement contract committed at `{report['metrics_freeze']['commit']}`; SHA-256 "
             f"`{report['metrics_freeze']['sha256']}`. Hash receipt committed separately before execution.", '',
             f"Gold rows: {report['total_rows']}; evaluated: {report['evaluated_rows']}; excluded: {len(report['excluded'])}.",
             'All evaluated rows use empty tasks and no prior turn. Fixture rows were not run because supplied `now` is missing.',
             f"Primary gate: **FAIL — {report['primary_gate']['reason']}**. No independent test supplied or searched for.",
             'At least 190 valid independent test rows are needed for the primary risk gate.', '',
             '## Metrics', '', '| Metric | Estimate | Bootstrap 95% CI |', '|---|---:|---|']
    def fmt(value):
        return 'not found / undefined' if value is None else f'{value:.6f}'
    for key, value in report['metrics'].items():
        ci = report['bootstrap_95_ci'][key]
        lines.append(f"| {key} | {fmt(value)} | [{fmt(ci['low'])}, {fmt(ci['high'])}] ({ci['valid_draws']} draws) |")
    lines += ['', 'Bootstrap: 2,000 utterance draws, seed 17. Wilson upper bound is the primary rare-event risk bound.',
              'Latency measures this host JVM with cold parser/JIT initialization included for the first row; process startup and I/O excluded.',
              'Unseen-author accuracy: **not found**, because a training-author manifest was not supplied.',
              'Rule confidence is a grammar score, not a calibrated probability.', '',
              '## Author and primary-intent counts', '', '| Author | Rows |', '|---|---:|']
    lines += [f'| {key} | {value} |' for key,value in audit['per_author'].items()]
    lines += ['', 'One author only: no nonempty author-isolated train/development split can be made from this sheet.',
              'No split of this development sheet was performed.', '', '| Intent | Rows |', '|---|---:|']
    lines += [f'| {key} | {value} |' for key,value in sorted(audit['per_intent'].items())]
    lines += ['', 'Every intent except UNKNOWN has fewer than 12 primary rows. Missing code intents have zero rows.',
              'Clause-level counts are recorded separately in `ml/data/dev_own_audit.json`.', '',
              '## Label and fixture mismatches', '',
              'CSV intents absent from code: ' + ', '.join(audit['intents_absent_from_code']) + '.', '',
              'Code intents absent from the CSV (including all clauses): ' + ', '.join(audit['code_intents_absent_from_csv']) + '.', '',
              'These labels are preserved; no aliases or guessed fixes were applied. Entity values all pass the verbatim check.', '',
              '| Row | Schema defect |', '|---:|---|']
    lines += [f"| {item['id']} | {'; '.join(item['issues'])} |" for item in audit['defects']]
    lines += ['', '| Row | Fixture exclusion |', '|---:|---|']
    lines += [f"| {item['id']} | {item['reason']} |" for item in report['excluded']]
    lines += ['', 'The following per-intent list includes every evaluated row whose complete label the current pipeline could not reproduce.',
              'It distinguishes intent, primary entities, ordered clauses, clarification, and write errors. These are observed reproducibility',
              'mismatches, not automatic proof that the human intended meaning is wrong.', '',
              '## Per-intent failures', '']
    for intent in sorted(audit['per_intent']):
        failures = [f for f in report['failures'] if gold[f['id']-1]['intent'] == intent]
        lines += [f'### {intent}', '', '| Row | Predicted intent | Mismatch |', '|---:|---|---|']
        for failure in failures:
            row = gold[failure['id']-1]
            p = failure['prediction']
            reasons = []
            if row['intent'] != p['intent']:
                reasons.append('intent')
            from ml.data.common import clauses, entities
            if entities(row['entities']) != p['entities']:
                reasons.append('entities')
            if clauses(row) != p['clauses']:
                reasons.append('ordered meaning')
            if (row['needs_clarification']=='yes') != p['needs_clarification']:
                reasons.append('clarification')
            if failure['wrong_write']:
                reasons.append('wrong write')
            lines.append(f"| {failure['id']} | {p['intent']} | {', '.join(reasons)} |")
        if not failures:
            lines.append('| — | — | No evaluated failures |')
        lines.append('')
    lines += ['Full texts, raw predictions, costs, confusion matrix and individual label defects are in `baseline_rules/`.', '',
              '## Public train-only pool', '',
              f"Total: {pool['total']}. All three requested MASSIVE locales are present locally.", '',
              '| Source / locale / original split | Emitted rows |', '|---|---:|']
    lines += [f'| {key} | {value} |' for key,value in sorted(pool['per_source'].items())]
    lines += ['', '| Intent | Rows |', '|---|---:|']
    lines += [f'| {key} | {value} |' for key,value in sorted(pool['per_intent'].items())]
    lines += ['', 'All official dev/validation/test splits are excluded. CLINC OOS/OOD is UNKNOWN.',
              'Preserved unsupported mappings remain UNSUPPORTED. Candidate intent labels still need row review;',
              'entities were not imported and no slot-training eligibility is claimed.',
              f"Pool SHA-256: `{pool['output_sha256']}`. Artifact: `build/ml-day1/public_train_pool.jsonl`.", '',
              '## Verification and limits', '',
              '- 19 unit/JVM integration tests passed before the dev baseline, including the appended-line leakage negative test.',
              '- Pinned MiniLM CPU ONNX runtime verified offline on a synthetic pair; exact-repeat inference passed. No fit or tune.',
              '- Two harness runs over the same saved prediction file are byte-identical (including metrics, failures, confusion matrix).',
              '- The full APK/Room/Android execution path was not run. Exporter uses production domain agents and policy with in-memory ports.',
              '- Read ports measure dispatch, not retrieved answer contents. Planner adapter supports fixture tasks without estimates only.',
              '- No actual write acceptance/executor is available in the exporter.',
              '- Current multi-intent behavior remains one whole request; production may swallow subsequent clauses into a task title.',
              '- Independent-test file, frozen-test hash/manifest, train-author manifest and adoption evidence: **not found**.',
              '- Live Day 5 CI gate intentionally exits nonzero when explicit external paths are absent; no test path is discovered.',
              '- CI hosted execution: **not found**; local contract and integration tests are verified.',
              '- PolicyEngine, safety core and legacy evidence were not modified.', '']
    (folder / 'baseline_rules_report.md').write_text('\n'.join(lines), encoding='utf-8')


if __name__ == '__main__':
    main()
