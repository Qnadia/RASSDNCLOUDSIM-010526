import os
import pandas as pd
import glob
import argparse
import shutil

# Mapping exact des colonnes basés sur les headers # units
COLUMN_MAP = {
    "host_energy_total.csv": ["time", "host_name", "host_id", "energy"],
    "path_latency_final.csv": [
        "time", "src", "dest", "path", "min_bw_mbps", "avg_bw_used", 
        "avg_pct_use", "network_lat_ms", "proc_delay_ms", "total_delay_ms", "selected"
    ],
    "packet_delays.csv": [
        "pkt_id", "src", "dest", "psize", "delay_ms", "proc_delay_ms", 
        "prop_delay_ms", "trans_delay_ms", "queue_delay_ms"
    ],
    "host_utilization.csv": ["time", "host_id", "cpu_util", "ram_util", "bw_util"],
    "qos_violations.csv": ["time", "vm_id", "req_id", "violation_type", "value"]
}

def consolidate(results_dir):
    print(f"--- Reorganizing and Consolidating Results in {results_dir} ---")
    
    raw_dir = os.path.join(results_dir, "raw")
    datasets_root = os.path.join(results_dir, "datasets")
    global_dir = os.path.join(results_dir, "global_analysis")
    
    for d in [raw_dir, datasets_root, global_dir]:
        if not os.path.exists(d): os.makedirs(d)

    # Move experiments if not already in raw/
    for item in os.listdir(results_dir):
        if item.startswith("dataset-") and os.path.isdir(os.path.join(results_dir, item)):
            shutil.move(os.path.join(results_dir, item), os.path.join(raw_dir, item))

    dataset_dirs = [d for d in os.listdir(raw_dir) if os.path.isdir(os.path.join(raw_dir, d))]
    all_dfs = {}

    for ds_name in dataset_dirs:
        ds_raw_path = os.path.join(raw_dir, ds_name)
        ds_output_path = os.path.join(datasets_root, ds_name)
        if not os.path.exists(ds_output_path): os.makedirs(ds_output_path)
            
        exp_dirs = []
        for root, dirs, files in os.walk(ds_raw_path):
            if "experiment_" in root: exp_dirs.append(root)
        
        if not exp_dirs: continue

        csv_files = set()
        for exp_dir in exp_dirs:
            for f in os.listdir(exp_dir):
                if f.endswith(".csv"): csv_files.add(f)

        for csv_name in csv_files:
            combined_rows = []
            for exp_dir in exp_dirs:
                parts = exp_dir.replace("\\", "/").split("/")
                vm_policy, link_policy, wf_policy = "Unknown", "Unknown", "Unknown"
                for part in parts:
                    if part.startswith("experiment_"):
                        p = part.replace("experiment_", "").split("_")
                        if len(p) >= 3: vm_policy, link_policy, wf_policy = p[0], p[1], p[2]
                
                f_path = os.path.join(exp_dir, csv_name)
                try:
                    names = COLUMN_MAP.get(csv_name)
                    # Use header=None if names are provided
                    df = pd.read_csv(f_path, sep=";", comment="#", header=None, names=names)
                    
                    df["vm_policy"] = vm_policy
                    df["link_policy"] = link_policy
                    df["wf_policy"] = wf_policy
                    combined_rows.append(df)
                except Exception: continue
            
            if combined_rows:
                master_df = pd.concat(combined_rows, ignore_index=True)
                master_df.to_csv(os.path.join(ds_output_path, csv_name), sep=";", index=False)
                if csv_name not in all_dfs: all_dfs[csv_name] = []
                master_df["dataset"] = ds_name
                all_dfs[csv_name].append(master_df)

    for csv_name, df_list in all_dfs.items():
        global_master = pd.concat(df_list, ignore_index=True)
        global_master.to_csv(os.path.join(global_dir, "GLOBAL_MASTER_" + csv_name), sep=";", index=False)

    print("\n[OK] Reorganization & Consolidation Complete.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir")
    args = parser.parse_args()
    consolidate(args.results_dir)
