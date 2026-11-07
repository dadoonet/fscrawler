"""Alternate healthy baseline/fixed crawls in persistent JVMs; never time concurrent crawlers."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time


def utc():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def revision(repo):
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip()
    dirty = subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=all'], cwd=repo, text=True)
    if dirty:
        raise RuntimeError(f'Commit benchmark sources before running: {repo}')
    parser = repo / 'core/src/main/java/fr/pilato/elasticsearch/crawler/fs/FsParser.java'
    harness = repo / 'integration-tests/src/test/java/fr/pilato/elasticsearch/crawler/fs/test/integration/elasticsearch/FsCrawlerCheckpointPairedBenchmarkIT.java'
    provider = harness.with_name('CheckpointBenchmarkFsProvider.java')
    return dict(revision=commit, parserSha256=hashlib.sha256(parser.read_bytes()).hexdigest(),
                harnessSha256=hashlib.sha256(harness.read_bytes().replace(b'\r\n', b'\n')).hexdigest(),
                providerSha256=hashlib.sha256(provider.read_bytes().replace(b'\r\n', b'\n')).hexdigest())


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--baseline', type=Path, required=True)
    p.add_argument('--fixed', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--cluster-url', default='http://127.0.0.1:19203')
    p.add_argument('--warmups', type=int, default=2)
    p.add_argument('--repeats', type=int, default=10)
    p.add_argument('--files', type=int, default=50000)
    p.add_argument('--directories', type=int, default=500)
    a = p.parse_args()
    if min(a.repeats, a.files, a.directories) < 1 or a.warmups < 0 or a.files % a.directories:
        p.error('Require positive counts, nonnegative warmups, and files divisible by directories')
    roots = dict(baseline=a.baseline.resolve(), fixed=a.fixed.resolve())
    info = {variant: revision(root) for variant, root in roots.items()}
    for field in ['harnessSha256', 'providerSha256']:
        if info['baseline'][field] != info['fixed'][field]:
            raise RuntimeError(f'Baseline and fixed {field} differ')
    out = a.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    control = out / 'control'
    control.mkdir()
    worker_script = Path(__file__).with_name('benchmark-checkpoint-worker.ps1')
    report = dict(startedUtc=utc(), configuration=dict(files=a.files, directories=a.directories,
                  warmups=a.warmups, repeats=a.repeats, clusterUrl=a.cluster_url,
                  javaToolOptions=os.environ.get('JAVA_TOOL_OPTIONS', ''),
                  coordinatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  workerSha256=hashlib.sha256(worker_script.read_bytes()).hexdigest(),
                  harnessHashNormalization='CRLF normalized to LF for harness/provider equality',
                  order='AB, BA, AB, BA including warm-ups; phase gates prevent fixture/teardown overlap'),
                  versions=info, samples=[], complete=False)
    workers, handles = {}, {}

    def save():
        temporary = out / 'paired-results.tmp'
        temporary.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        temporary.replace(out / 'paired-results.json')

    def await_file(path, timeout=900):
        deadline = time.monotonic() + timeout
        while not path.exists():
            for variant, process in workers.items():
                if process.poll() is not None:
                    raise RuntimeError(f'{variant} worker exited early with {process.returncode}; see its log')
            if time.monotonic() > deadline:
                raise TimeoutError(f'Waiting for {path.name}')
            time.sleep(0.1)

    save()
    try:
        for variant, root in roots.items():
            # Failsafe can verify an old failure summary in reactor modules with no selected tests.
            # Preserve those files rather than allowing stale results to invalidate this run.
            for old in list(root.glob('**/target/failsafe-reports/failsafe-summary.xml')):
                if old.is_relative_to(out):
                    continue
                saved = out / 'previous-failsafe-summaries' / variant / old.relative_to(root)
                saved.parent.mkdir(parents=True, exist_ok=True)
                old.replace(saved)
            handles[variant] = (out / f'{variant}.log').open('w', encoding='utf-8')
            args = ['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(worker_script),
                    '-RepoRoot', str(root), '-Variant', variant, '-ControlDirectory', str(control),
                    '-ClusterUrl', a.cluster_url, '-Warmups', str(a.warmups), '-Repeats', str(a.repeats),
                    '-Files', str(a.files), '-Directories', str(a.directories)]
            workers[variant] = subprocess.Popen(args, stdout=handles[variant], stderr=subprocess.STDOUT,
                                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        for workload in ['single-50000', 'many-50000']:
            (control / f'prepare-{workload}.start').touch()
            for variant in roots:
                await_file(control / f'{variant}-{workload}.ready')
            print(f'{utc()} both fixtures ready: {workload}', flush=True)
            for run in range(a.warmups + a.repeats):
                order = ['baseline', 'fixed'] if run % 2 == 0 else ['fixed', 'baseline']
                for variant in order:
                    turn = f'{variant}-{workload}-{run}'
                    started = utc()
                    (control / f'{turn}.start').touch()
                    await_file(control / f'{turn}.done')
                    row = json.loads((control / f'{turn}.done').read_text(encoding='utf-8'))
                    assert (row['variant'], row['workload'], row['run'], row['warmup'], row['documents'], row['state']) == (
                        variant, workload, run, run < a.warmups, a.files, 'COMPLETED')
                    row.update(startedUtc=started, completedUtc=utc(), order=len(report['samples']) + 1)
                    report['samples'].append(row)
                    save()
                    print(f"{utc()} {turn}: {row['elapsed_ms']:.3f} ms warmup={row['warmup']}", flush=True)
        for variant, process in workers.items():
            # Sequential exit avoids simultaneous framework teardown against shared test resources.
            (control / f'finish-{variant}.start').touch()
            if process.wait(timeout=180) != 0:
                raise RuntimeError(f'{variant} Maven validation failed; timings invalid')
        for handle in handles.values():
            handle.flush()
        for variant in roots:
            text = (out / f'{variant}.log').read_text(encoding='utf-8', errors='replace')
            if 'BUILD SUCCESS' not in text or re.search(
                    r'\bRetrying\b|Rate limited \(429\)|Bulk item failure for|Error while crawling|Failed to save checkpoint', text):
                raise RuntimeError(f'{variant} encountered failures/retries; timings invalid')
        assert len(report['samples']) == 4 * (a.warmups + a.repeats)
        report.update(complete=True, completedUtc=utc())
        save()
        print(f'Validated {len(report["samples"])} healthy crawls: {out / "paired-results.json"}', flush=True)
    except BaseException as error:
        report.update(error=str(error), completedUtc=utc())
        (control / 'stop').touch()
        save()
        for process in workers.values():
            if process.poll() is None:
                try:
                    process.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    if os.name == 'nt':
                        subprocess.run(['taskkill.exe', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
                    else:
                        process.kill()
        raise
    finally:
        for handle in handles.values():
            handle.close()


if __name__ == '__main__':
    main()
