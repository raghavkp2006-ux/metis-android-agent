"""Offline host JVM compilation and CSV transport. No Gradle resolution/downloads."""
import argparse
import json
import os
from pathlib import Path
import subprocess

from ml.data.common import ROOT, dump, read_csv, sha, text_sha
from ml.eval.harness import verify_freeze


def jar(cache, group, name, version):
    matches = sorted((cache / group / name / version).glob('*/*.jar'))
    if len(matches) != 1:
        raise RuntimeError(f'cached jar not found or ambiguous: {group}:{name}:{version}')
    return matches[0]


def compile_exporter(build, cache):
    compiler = jar(cache, 'org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.1.20')
    runtime = [jar(cache, 'org.jetbrains.kotlin', 'kotlin-stdlib', '2.1.20'),
               jar(cache, 'org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.7.3'),
               jar(cache, 'com.google.code.gson', 'gson', '2.11.0'),
               jar(cache, 'org.jetbrains', 'annotations', '23.0.0')]
    compiler_deps = [compiler] + runtime + [
        jar(cache, 'org.jetbrains.kotlin', 'kotlin-script-runtime', '2.1.20'),
        jar(cache, 'org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
        jar(cache, 'org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330')]
    sources = sorted((ROOT / 'app/src/main/java/dev/metis/agent/domain').rglob('*.kt'))
    sources.append(ROOT / 'ml/eval/jvm/Day1RulesExporter.kt')
    build.mkdir(parents=True, exist_ok=True)
    target = build / 'rules.jar'
    argfile = build / 'compiler.args'
    # Java argument files use quoted forward-slash paths, not shell interpolation.
    args = ['-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', os.pathsep.join(map(str, runtime)),
            '-d', str(target)] + list(map(str, sources))
    argfile.write_text('\n'.join('"' + arg.replace('\\', '/') + '"' for arg in args), encoding='utf-8')
    subprocess.run(['java', '-cp', os.pathsep.join(map(str, compiler_deps)),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '@' + str(argfile)], cwd=ROOT, check=True)
    dump(build / 'source_binding.json', {str(p.relative_to(ROOT)): sha(p) for p in sources})
    dump(build / 'runtime_binding.json', {str(p): sha(p) for p in compiler_deps})
    return os.pathsep.join(map(str, [target] + runtime))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gold', type=Path)
    parser.add_argument('--fixture', type=Path, default=ROOT / 'fixtures/default_state.json')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--build', type=Path, default=ROOT / 'build/ml-day1')
    parser.add_argument('--cache', type=Path, default=Path.home() / '.gradle/caches/modules-2/files-2.1')
    parser.add_argument('--compile-only', action='store_true')
    args = parser.parse_args()
    freeze = verify_freeze()
    cp = compile_exporter(args.build, args.cache)
    if args.compile_only:
        return
    if not args.gold or not args.output:
        parser.error('--gold and --output required')
    rows = read_csv(args.gold)
    transport = args.build / 'input.jsonl'
    transport.write_text(''.join(json.dumps(dict(id=i, text=r['text'], text_sha256=text_sha(r['text']),
                                                context=r['context'], style=r['style']), ensure_ascii=False) + '\n'
                                 for i, r in enumerate(rows, 1)), encoding='utf-8')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['java', '-cp', cp, 'dev.metis.agent.evaluation.day1.Day1RulesExporter',
                    str(transport), str(args.output), str(args.fixture)], cwd=ROOT, check=True)
    dump(args.output.with_suffix('.receipt.json'), dict(gold_sha256=sha(args.gold), fixture_sha256=sha(args.fixture),
         predictions_sha256=sha(args.output), freeze=freeze,
         sources=json.loads((args.build / 'source_binding.json').read_text()),
         runtimes=json.loads((args.build / 'runtime_binding.json').read_text()),
         limitations=['read port reports dispatch only; retrieved answer content is not scored',
                     'planner fixture adapter supports no-estimate tasks only',
                     'none state uses epoch for lexical parsing; normalized time is not scored']))


if __name__ == '__main__':
    main()
