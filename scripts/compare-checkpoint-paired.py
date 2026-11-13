"""Summarize complete alternating baseline/fixed runs, retaining pairs in bootstrap resampling."""
import argparse
import json
from pathlib import Path
import random
import statistics as stats


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('report', type=Path)
    a = p.parse_args()
    report = json.loads(a.report.read_text(encoding='utf-8-sig'))
    if not report.get('complete'):
        raise ValueError('A complete, validated benchmark report is required')
    rng = random.Random(500050000)
    result = []
    for workload in ['single-50000', 'many-50000']:
        rows = [r for r in report['samples'] if r['workload'] == workload and not r['warmup']]
        pairs = {}
        for row in rows:
            pairs.setdefault(row['run'], {})[row['variant']] = row['elapsed_ms']
        if len(pairs) != report['configuration']['repeats'] or any(set(v) != {'baseline', 'fixed'} for v in pairs.values()):
            raise ValueError('Missing or incomplete baseline/fixed pairs')
        values = list(pairs.values())
        baseline = [v['baseline'] for v in values]
        fixed = [v['fixed'] for v in values]
        median_a, median_b = stats.median(baseline), stats.median(fixed)
        changes = []
        for _ in range(20000):
            sample = rng.choices(values, k=len(values))
            changes.append(100 * (stats.median(v['fixed'] for v in sample) /
                                  stats.median(v['baseline'] for v in sample) - 1))
        changes.sort()
        lo, hi = changes[500], changes[19499]
        result.append(dict(workload=workload, measured_pairs=len(values), baseline_ms=baseline, fixed_ms=fixed,
                           baseline_median_ms=median_a, fixed_median_ms=median_b,
                           baseline_documents_per_second=1000 * report['configuration']['files'] / median_a,
                           fixed_documents_per_second=1000 * report['configuration']['files'] / median_b,
                           median_change_percent=100 * (median_b / median_a - 1),
                           median_paired_change_percent=stats.median(100 * (v['fixed'] / v['baseline'] - 1) for v in values),
                           paired_bootstrap_95_percent_interval=[lo, hi], practical_threshold_percent=10,
                           interpretation='material slowdown detected' if lo > 10 else
                           'no material slowdown in these samples' if hi < 10 else
                           'insufficient precision to rule out a material slowdown'))
    print(json.dumps(dict(comparisons=result,
                         caveat='Indicative paired bootstrap intervals; persistent-JVM samples may be correlated. '
                                'Alternating order reduces but does not remove environmental effects.'), indent=2))


if __name__ == '__main__':
    main()
