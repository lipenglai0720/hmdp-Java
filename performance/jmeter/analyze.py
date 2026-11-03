"""Summarize JMeter CSV/JTL files without counting sold-out/duplicate as system failures."""
from pathlib import Path
import argparse,collections,csv,json,math,sys

def percentile(values,p):
    if not values:return None
    ordered=sorted(values)
    return ordered[max(0,math.ceil(len(ordered)*p/100)-1)]
def summarize(path,skip=0,expected_threads=None):
    with path.open(encoding="utf-8-sig",newline="") as f:rows=list(csv.DictReader(f))
    if not rows:raise ValueError("No samples: check CSV input, thread count and JMeter log")
    required={"timeStamp","elapsed","label","success","threadName"}
    if not required.issubset(rows[0]):raise ValueError("Required JTL fields missing; use the generated current.properties")
    cutoff=min(int(r["timeStamp"]) for r in rows)+skip*1000
    rows=[r for r in rows if int(r["timeStamp"])>=cutoff]
    if not rows:raise ValueError("Warm-up exclusion removed all samples")
    start=min(int(r["timeStamp"]) for r in rows);end=max(int(r["timeStamp"])+int(r["elapsed"]) for r in rows)
    duration=max((end-start)/1000,.001)
    groups=collections.defaultdict(list)
    for row in rows:groups[row["label"]].append(row)
    metrics={}
    for label,samples in groups.items():
        latency=[int(r["elapsed"]) for r in samples]
        errors=sum(r["success"].lower()!="true" for r in samples)
        metrics[label]={"samples":len(samples),"errors":errors,"error_percent":round(errors/len(samples)*100,3),
                        "requests_per_second_over_measured_window":round(len(samples)/duration,3),
                        "avg_ms":round(sum(latency)/len(latency),3),"p50_ms":percentile(latency,50),"p95_ms":percentile(latency,95),"p99_ms":percentile(latency,99)}
    submissions=[r for r in rows if r["label"] in {"seckill.submit","claim.submit"}]
    finals=[r for r in rows if r["label"]=="order.final"]
    outcomes=collections.Counter(r.get("outcome") or "MISSING_OUTCOME" for r in submissions)
    terminal=collections.Counter(r.get("outcome") or "MISSING_OUTCOME" for r in finals)
    business=[int(r["business_ms"]) for r in finals if r.get("outcome")=="CREATED" and r.get("business_ms","").isdigit()]
    accepted=[r for r in submissions if r.get("outcome")=="ACCEPTED"]
    accepted_ids=[r.get("order_id") for r in accepted]
    threads=len({r["threadName"] for r in rows})
    unexpected=sum(r["success"].lower()!="true" for r in rows)
    incomplete=len(accepted)!=len(finals) or any(k in terminal for k in ("PENDING_TIMEOUT","ORDER_FAILED","MISSING_OUTCOME"))
    report={"file":str(path),"measurement_seconds":round(duration,3),"observed_threads":threads,
            "percentile_method":"nearest-rank; HTML dashboard estimators may differ",
            "samplers":metrics,"submission_outcomes":dict(outcomes),"terminal_outcomes":dict(terminal),
            "duplicate_accepted_ids":len(accepted_ids)-len(set(accepted_ids)),
            "successful_order_observed_end_to_end_ms":{"samples":len(business),"p50":percentile(business,50),"p95":percentile(business,95),"p99":percentile(business,99)},
            "accepted_without_created":incomplete,"unexpected_failed_samples":unexpected,
            "missing_outcome_columns":bool(submissions and "outcome" not in rows[0]),
            "thread_count_matches":expected_threads is None or threads==expected_threads}
    report["passed"]=unexpected==0 and not incomplete and report["duplicate_accepted_ids"]==0 and not report["missing_outcome_columns"] and report["thread_count_matches"]
    return report

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("jtl",type=Path);parser.add_argument("--skip-first-seconds",type=int,default=0)
    parser.add_argument("--expected-threads",type=int);parser.add_argument("--output",type=Path);args=parser.parse_args()
    report=summarize(args.jtl,args.skip_first_seconds,args.expected_threads)
    text=json.dumps(report,ensure_ascii=False,indent=2);print(text)
    if args.output:args.output.write_text(text,encoding="utf-8")
    if not report["passed"]:sys.exit(1)
if __name__=="__main__":main()
