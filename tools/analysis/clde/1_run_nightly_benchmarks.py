"""
1_run_nightly_benchmarks.py  — RAS-SDNCloudSim v2
Lance les 18 simulations (2 link × 3 vm × 3 wf) sur un ou plusieurs datasets.
Améliorations vs v1:
  - Support multi-dataset en un seul appel (--datasets small medium large)
  - Timeout configurable, retry automatique sur échec transitoire
  - Barre de progression avec ETA
  - Rapport final JSON (durations, status, return codes)
  - Détection auto du classpath Java (Maven target/ ou Gradle build/)
  - Logging structuré par niveau (INFO/WARN/ERROR)
  - Option --dry-run pour vérifier la config sans lancer
"""

import os
import subprocess
import time
import argparse
import json
from datetime import datetime
from pathlib import Path

# ──────────────────────────────────────────────
# CONFIGURATION
# ──────────────────────────────────────────────
JAVA_MAIN    = "org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth"
LINK_POLICIES = ["First", "BLA"]
VM_POLICIES   = ["LFF", "MFF", "LWFF"]
WF_POLICIES   = ["Priority", "SJF", "PSO"]
TOTAL_RUNS    = len(LINK_POLICIES) * len(VM_POLICIES) * len(WF_POLICIES)   # 18


# ──────────────────────────────────────────────
# LOGGING
# ──────────────────────────────────────────────
def log(level, msg):
    ts = datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] [{level:5s}] {msg}")

def info(msg):  log("INFO",  msg)
def warn(msg):  log("WARN",  msg)
def error(msg): log("ERROR", msg)


# ──────────────────────────────────────────────
# CLASSPATH DETECTION
# ──────────────────────────────────────────────
def detect_classpath():
    """
    Try common Maven / Gradle output paths.
    Returns the classpath string or raises FileNotFoundError.
    """
    candidates = [
        "target/classes;target/lib/*;lib/*;target/dependency/*",
        "target/classes:target/lib/*:lib/*:target/dependency/*",
        "build/classes/java/main:build/libs/*:lib/*",
    ]
    # Check if target/classes exists (Maven)
    if Path("target/classes").exists():
        sep = ";" if os.name == "nt" else ":"
        return sep.join(["target/classes",
                          "target/lib/*",
                          "lib/*",
                          "target/dependency/*"])
    # Gradle
    if Path("build/classes").exists():
        sep = ";" if os.name == "nt" else ":"
        return sep.join(["build/classes/java/main",
                          "build/libs/*",
                          "lib/*"])
    # Fallback — let the user fix it
    warn("Could not auto-detect classpath. Using default Maven layout.")
    sep = ";" if os.name == "nt" else ":"
    return sep.join(["target/classes", "target/lib/*",
                      "lib/*", "target/dependency/*"])


# ──────────────────────────────────────────────
# SIMULATION RUNNER
# ──────────────────────────────────────────────
def run_simulation(ds_name, vm, link, wf, classpath,
                   timeout=600, max_retries=1, dry_run=False):
    """
    Execute one simulation and return (success, elapsed_s, returncode).
    Retries once on non-zero exit (transient JVM issues).
    """
    log_dir  = Path("logs") / datetime.now().strftime("%Y-%m-%d") / ds_name
    log_dir.mkdir(parents=True, exist_ok=True)
    log_file = log_dir / f"experiment_{vm}_{link}_{wf}.log"

    cmd = ["java", "-cp", classpath, JAVA_MAIN, vm, link, wf, ds_name]

    if dry_run:
        info(f"  [DRY-RUN] {' '.join(cmd)}")
        return True, 0.0, 0

    for attempt in range(1, max_retries + 2):
        start = time.time()
        with open(log_file, "w") as lf:
            try:
                result = subprocess.run(
                    cmd, stdout=lf, stderr=lf,
                    timeout=timeout
                )
                elapsed = time.time() - start
                if result.returncode == 0:
                    return True, elapsed, 0
                else:
                    warn(f"  Attempt {attempt} failed (rc={result.returncode})")
                    if attempt <= max_retries:
                        time.sleep(2)
            except subprocess.TimeoutExpired:
                elapsed = time.time() - start
                warn(f"  Timeout after {elapsed:.0f}s (attempt {attempt})")
                if attempt <= max_retries:
                    time.sleep(5)
                else:
                    return False, elapsed, -1

    return False, time.time() - start, result.returncode


# ──────────────────────────────────────────────
# PROGRESS BAR
# ──────────────────────────────────────────────
def progress_bar(done, total, elapsed, width=30):
    pct = done / total
    filled = int(width * pct)
    bar = "#" * filled + "-" * (width - filled)
    eta = (elapsed / done * (total - done)) if done > 0 else 0
    eta_str = f"{int(eta//60)}m{int(eta%60):02d}s" if eta > 0 else "..."
    print(f"\r  [{bar}] {done}/{total} ({pct*100:.0f}%) ETA: {eta_str}", end="", flush=True)


# ──────────────────────────────────────────────
# MAIN
# ──────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(
        description="RAS-SDNCloudSim — Nightly Benchmark Runner"
    )
    parser.add_argument("--datasets", nargs="+", default=["dataset-small"],
                        help="One or more dataset names (e.g. dataset-small dataset-large)")
    parser.add_argument("--timeout", type=int, default=600,
                        help="Per-simulation timeout in seconds (default: 600)")
    parser.add_argument("--retries", type=int, default=1,
                        help="Max retries on failure (default: 1)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print commands without executing")
    parser.add_argument("--skip-link", nargs="*", default=[],
                        help="Skip specific link policies (e.g. --skip-link First)")
    parser.add_argument("--skip-vm", nargs="*", default=[],
                        help="Skip specific VM policies")
    parser.add_argument("--skip-wf", nargs="*", default=[],
                        help="Skip specific WF policies")
    args = parser.parse_args()

    classpath = detect_classpath()
    info(f"Classpath: {classpath}")

    link_policies = [p for p in LINK_POLICIES if p not in args.skip_link]
    vm_policies   = [p for p in VM_POLICIES   if p not in args.skip_vm]
    wf_policies   = [p for p in WF_POLICIES   if p not in args.skip_wf]
    total_per_ds  = len(link_policies) * len(vm_policies) * len(wf_policies)

    report = {"run_date": datetime.now().isoformat(), "datasets": {}}
    global_start = time.time()

    for ds_name in args.datasets:
        info(f"\n{'='*60}")
        info(f"DATASET: {ds_name}  ({total_per_ds} experiments)")
        info(f"{'='*60}")

        ds_report  = {"success": 0, "failed": 0, "runs": []}
        ds_start   = time.time()
        done       = 0

        for vm in vm_policies:
            for link in link_policies:
                for wf in wf_policies:
                    done += 1
                    elapsed_ds = time.time() - ds_start
                    label = f"{vm}|{link}|{wf}"
                    info(f"  [{done:2d}/{total_per_ds}] {label}")

                    ok, elapsed, rc = run_simulation(
                        ds_name, vm, link, wf,
                        classpath=classpath,
                        timeout=args.timeout,
                        max_retries=args.retries,
                        dry_run=args.dry_run
                    )

                    status = "OK" if ok else "FAIL"
                    info(f"  -> {status}  {elapsed:.1f}s")
                    progress_bar(done, total_per_ds, elapsed_ds)

                    ds_report["runs"].append({
                        "vm": vm, "link": link, "wf": wf,
                        "status": status, "elapsed_s": round(elapsed, 1),
                        "returncode": rc
                    })
                    if ok:
                        ds_report["success"] += 1
                    else:
                        ds_report["failed"] += 1

        print()  # newline after progress bar
        ds_elapsed = time.time() - ds_start
        info(f"  {ds_name}: {ds_report['success']}/{total_per_ds} OK  ({ds_elapsed:.1f}s total)")
        report["datasets"][ds_name] = ds_report

    # ── Write JSON report ──
    report["total_elapsed_s"] = round(time.time() - global_start, 1)
    report_path = Path("logs") / f"benchmark_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with open(report_path, "w") as f:
        json.dump(report, f, indent=2)

    info(f"\nReport saved -> {report_path}")
    total_ok  = sum(d["success"] for d in report["datasets"].values())
    total_all = sum(d["success"] + d["failed"] for d in report["datasets"].values())
    info(f"GLOBAL: {total_ok}/{total_all} successful runs in {report['total_elapsed_s']:.1f}s")


if __name__ == "__main__":
    main()
