"""
2_consolidate_results.py  — RAS-SDNCloudSim v3
Consolide les CSV de résultats bruts en un dataset analysable.
Améliorations vs v1/v2:
  - Détection automatique du format (mini vs small/medium/large headers)
  - Gestion robuste des fichiers corrompus ou vides
  - Déduplication intelligente (packet_delays: un enregistrement par paquet)
  - Normalisation DynLatBw → BLA dans link_policy
  - Export global MASTER pour analyse cross-dataset
  - Rapport de consolidation (nb lignes par fichier, échecs, warnings)
  - Compatible Python 3.8+
"""

import os
import pandas as pd
import argparse
import shutil
import json
from pathlib import Path
from datetime import datetime

# ──────────────────────────────────────────────
# COLUMN MAPS (both mini-style and flat-style)
# ──────────────────────────────────────────────
COLUMN_MAP = {
    "host_energy_total.csv": ["time", "host_name", "host_id", "energy"],
    "packet_delays.csv": [
        "pkt_id", "src", "dest", "psize",
        "delay_ms", "proc_delay_ms", "prop_delay_ms",
        "trans_delay_ms", "queue_delay_ms"
    ],
    "host_utilization.csv": ["time", "host_id", "cpu_util", "ram_util", "bw_util", "energy"],
    "qos_violations.csv":   ["time", "flowId", "violationType"],
    "link_utilization_up.csv": ["link_id", "utilization"],
    "path_latency_final.csv": [
        "time", "src", "dst", "path",
        "min_bw_mbps", "avg_bw_used", "avg_pct_use",
        "network_latency_ms", "proc_delay_ms", "total_delay_ms", "selected"
    ],
    "sw_energy.csv":     ["time", "switch_id", "energy"],
    "vm_utilization.csv":["time", "vm_id", "cpu_util", "ram_util", "mips_alloc", "ram_max"],
    "detailed_energy.csv":["time", "node_id", "cpu_w", "ram_w", "bw_w", "total_w"],
    "host_allocation_summary.csv": [
        "timestamp", "host_id", "vm_ids", "cpu_pct",
        "ram_pct", "bw_pct", "policy", "energy_w"
    ],
}

DEDUP_KEYS = {
    "packet_delays.csv": ["pkt_id", "src", "dest"],
}

POLICY_NORM = {"DynLatBw": "BLA", "dynlatbw": "BLA",
               "First": "First", "first": "First", "BLA": "BLA"}


def normalize_policy(val):
    return POLICY_NORM.get(str(val).strip(), str(val).strip())


# ──────────────────────────────────────────────
# SAFE CSV READER
# ──────────────────────────────────────────────
def safe_read_csv(path, csv_name):
    """
    Reads a raw simulation CSV.
    Tries two strategies:
      1. Skip lines starting with '#' (comment-style header used in mini format)
      2. Read as-is with column names from COLUMN_MAP
    Returns a clean DataFrame or None.
    """
    names = COLUMN_MAP.get(csv_name)
    if names is None:
        return None

    try:
        # Try comment-skip first (mini / new format)
        df = pd.read_csv(
            path, sep=";", comment="#",
            header=None, names=names,
            on_bad_lines="skip", engine="python"
        )
        # Drop rows where first column looks like a header string
        first_col = df.columns[0]
        df = df[pd.to_numeric(df[first_col], errors="coerce").notna() |
                df[first_col].astype(str).str.strip().isin(["", "nan"])]
        df = df.dropna(how="all")
        if df.empty:
            return None

        # Deduplicate if needed
        dedup = DEDUP_KEYS.get(csv_name)
        if dedup:
            df = df.drop_duplicates(subset=dedup)

        # Numeric coercion for key columns
        numeric_cols = [c for c in names
                        if any(k in c for k in ["delay","energy","util","bw","mips","pct","selected","psize"])]
        for col in numeric_cols:
            if col in df.columns:
                df[col] = pd.to_numeric(df[col], errors="coerce")

        return df

    except Exception as e:
        return None


# ──────────────────────────────────────────────
# POLICY EXTRACTION FROM PATH
# ──────────────────────────────────────────────
def extract_policies(exp_dir_str):
    """
    Extract (vm_policy, link_policy, wf_policy) from the experiment folder path.
    Supports both: experiment_LFF_BLA_Priority  and  LFF/experiment_BLA_Priority
    """
    parts = exp_dir_str.replace("\\", "/").split("/")
    for part in reversed(parts):
        if part.startswith("experiment_"):
            p = part.replace("experiment_", "").split("_")
            if len(p) >= 3:
                return p[0], p[1], p[2]
    # Fallback: look for VM policy as a parent folder
    for i, part in enumerate(parts):
        if part in ("LFF", "MFF", "LWFF"):
            vm = part
            # Next part might be experiment_link_wf
            for j in range(i+1, len(parts)):
                if parts[j].startswith("experiment_"):
                    q = parts[j].replace("experiment_", "").split("_")
                    if len(q) >= 2:
                        return vm, q[0], q[1] if len(q) > 1 else "Priority"
    return "Unknown", "Unknown", "Unknown"


# ──────────────────────────────────────────────
# DATASET CONSOLIDATION
# ──────────────────────────────────────────────
def consolidate_dataset(ds_raw_path, ds_output_path, ds_name):
    """
    Consolidates all experiment CSVs for one dataset.
    Returns a dict {csv_name: master_DataFrame}.
    """
    # Collect all experiment directories
    exp_dirs = []
    for root, dirs, files in os.walk(ds_raw_path):
        if "experiment_" in root or any(
            p in root for p in ["LFF", "MFF", "LWFF"]
        ):
            if any(f.endswith(".csv") for f in files):
                exp_dirs.append(root)

    if not exp_dirs:
        print(f"  [WARN] No experiment dirs found in {ds_raw_path}")
        return {}

    print(f"  Found {len(exp_dirs)} experiment directories")

    # Collect all CSV names
    csv_names = set()
    for exp_dir in exp_dirs:
        for f in os.listdir(exp_dir):
            if f.endswith(".csv") and f in COLUMN_MAP:
                csv_names.add(f)

    master_dfs = {}
    report_rows = []

    for csv_name in sorted(csv_names):
        combined = []
        for exp_dir in exp_dirs:
            fpath = os.path.join(exp_dir, csv_name)
            if not os.path.exists(fpath):
                continue

            vm, link, wf = extract_policies(exp_dir)
            link = normalize_policy(link)

            df = safe_read_csv(fpath, csv_name)
            if df is None or df.empty:
                report_rows.append({"file": fpath, "rows": 0, "status": "EMPTY/ERROR"})
                continue

            df["vm_policy"]   = vm
            df["link_policy"] = link
            df["wf_policy"]   = wf
            combined.append(df)
            report_rows.append({"file": fpath, "rows": len(df), "status": "OK"})

        if combined:
            master = pd.concat(combined, ignore_index=True)
            out_path = os.path.join(ds_output_path, csv_name)
            master.to_csv(out_path, sep=";", index=False)
            master_dfs[csv_name] = master
            print(f"  [{csv_name}] {len(master):,} rows -> {out_path}")

    return master_dfs, report_rows


# ──────────────────────────────────────────────
# MAIN
# ──────────────────────────────────────────────
def consolidate(results_dir, output_global=True):
    results_dir = Path(results_dir)
    print(f"\n{'='*60}")
    print(f"Consolidation: {results_dir}")
    print(f"{'='*60}\n")

    all_masters   = {}
    full_report   = []
    processed     = 0

    for item in sorted(results_dir.iterdir()):
        if not item.is_dir():
            continue
        ds_name = item.name
        if ds_name in ("plots", "raw", "global_analysis", "global", "__pycache__"):
            continue

        print(f"\n[Dataset] {ds_name}")

        # Determine raw source: might be item itself or item/raw/
        raw_path = item / "raw"
        raw_path = raw_path if raw_path.exists() else item

        # Set up output paths
        synthese_dir = item / "synthese" / "data"
        synthese_dir.mkdir(parents=True, exist_ok=True)

        # Move loose experiment_ folders into raw/ if needed
        for sub in item.iterdir():
            if sub.is_dir() and (
                sub.name.startswith("experiment_") or
                sub.name in ("LFF", "MFF", "LWFF")
            ):
                dest = item / "raw" / sub.name
                if not dest.exists():
                    shutil.move(str(sub), str(item / "raw"))

        result = consolidate_dataset(str(raw_path), str(synthese_dir), ds_name)
        if not result:
            continue
        master_dfs, report_rows = result
        full_report.extend(report_rows)

        for csv_name, df in master_dfs.items():
            df["dataset"] = ds_name
            all_masters.setdefault(csv_name, []).append(df)

        processed += 1

    # ── Global master files ──
    if output_global and all_masters:
        global_dir = results_dir / "global_analysis"
        global_dir.mkdir(exist_ok=True)
        for csv_name, df_list in all_masters.items():
            global_master = pd.concat(df_list, ignore_index=True)
            out = global_dir / f"GLOBAL_{csv_name}"
            global_master.to_csv(out, sep=";", index=False)
            print(f"  [GLOBAL] {csv_name} -> {out}  ({len(global_master):,} rows)")

    # ── Consolidation report ──
    report_df = pd.DataFrame(full_report)
    ok  = (report_df["status"] == "OK").sum() if not report_df.empty else 0
    err = (report_df["status"] != "OK").sum() if not report_df.empty else 0
    report_path = results_dir / f"consolidation_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
    if not report_df.empty:
        report_df.to_csv(report_path, index=False)

    print(f"\n{'='*60}")
    print(f"Datasets processed : {processed}")
    print(f"Files OK           : {ok}")
    print(f"Files with errors  : {err}")
    print(f"Report             : {report_path}")
    print(f"{'='*60}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="RAS-SDNCloudSim — Results Consolidator v3"
    )
    parser.add_argument("results_dir", help="Root results directory")
    parser.add_argument("--no-global", action="store_true",
                        help="Skip global MASTER file generation")
    args = parser.parse_args()
    consolidate(args.results_dir, output_global=not args.no_global)
