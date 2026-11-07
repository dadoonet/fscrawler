"""Compare healthy benchmark logs or runner JSON; warm-up samples are excluded."""
import argparse
import json
import random
import re
import statistics
from pathlib import Path

def samples(path):
    text=Path(path).read_text(encoding='utf-8-sig',errors='replace')
    if path.endswith('.json'):
        rows=json.loads(text)['samples']
        pairs=[(r['workload'],float(r['elapsed_ms'])) for r in rows if not r['warmup']]
    else:
        pairs=[(name,float(ms)) for name,ms in re.findall(
            r'(?m)^CHECKPOINT_BENCH workload=(\S+) iteration=\d+ warmup=false elapsed_ms=([\d.]+)',text)]
        if 'BUILD SUCCESS' not in text:
            raise ValueError('Only successful, fully validated benchmark logs can be compared')
    grouped={}
    for name,ms in pairs: grouped.setdefault(name,[]).append(ms)
    return grouped

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline'); parser.add_argument('fixed')
    args=parser.parse_args(); before=samples(args.baseline); after=samples(args.fixed)
    if set(before)!=set(after) or len(before)!=3: raise ValueError('Workloads do not match')
    rng=random.Random(3939); result=[]
    for name in before:
        a,b=before[name],after[name]
        if min(len(a),len(b))<3: raise ValueError('At least three measured runs are required')
        ratios=sorted(100*(statistics.median(rng.choices(b,k=len(b)))/statistics.median(rng.choices(a,k=len(a)))-1)
                      for _ in range(20000))
        change=100*(statistics.median(b)/statistics.median(a)-1)
        lo,hi=ratios[500],ratios[19499]
        result.append(dict(workload=name,baseline_ms=a,fixed_ms=b,
            baseline_median_ms=statistics.median(a),fixed_median_ms=statistics.median(b),
            median_change_percent=change,bootstrap_95_percent_interval=[lo,hi],
            practical_threshold_percent=10,
            interpretation=('material slowdown detected' if lo>10 else
                            'no material slowdown in these samples' if hi<10 else
                            'insufficient precision to rule out a material slowdown')))
    print(json.dumps(result,indent=2))

if __name__=='__main__': main()
