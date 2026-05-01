import os
import pandas as pd
import argparse
import shutil

COLUMN_MAP = {
    "host_energy_total.csv": ["time", "host_name", "host_id", "energy"],
    "packet_delays.csv": ["pkt_id", "src", "dest", "psize", "delay_ms", "proc_delay_ms", "prop_delay_ms", "trans_delay_ms", "queue_delay_ms"],
    "host_utilization.csv": ["time", "host_id", "cpu_util", "ram_util", "bw_util", "energy"],
    "qos_violations.csv": ["time", "flowId", "violationType"],  # 3 cols: timestamp;flowId;violationType
    "link_utilization_up.csv": ["time", "link_id", "utilization", "latency"],
    "path_latency_final.csv": ["time", "src", "dst", "path", "min_bw_mbps", "avg_bw_used", "avg_pct_use", "network_latency_ms", "proc_delay_ms", "total_delay_ms", "selected"]
}

def organize_and_consolidate(results_dir):
    print(f"--- Organizing Architecture: Dataset/{{raw, plot, synthese}} in {results_dir} ---")
    
    for ds_name in os.listdir(results_dir):
        ds_path = os.path.join(results_dir, ds_name)
        if not os.path.isdir(ds_path) or ds_name in ["plots", "raw", "global_analysis", "datasets", "detailed"]:
            continue
            
        print(f"\nProcessing Dataset: {ds_name}")
        raw_dir = os.path.join(ds_path, "raw")
        plot_dir = os.path.join(ds_path, "plot")
        synthese_dir = os.path.join(ds_path, "synthese")
        data_dir = os.path.join(synthese_dir, "data")
        for d in [raw_dir, plot_dir, synthese_dir, data_dir]:
            if not os.path.exists(d): os.makedirs(d)

        for item in os.listdir(ds_path):
            if item in ["LFF", "MFF", "LWFF"] or item.startswith("experiment_"):
                shutil.move(os.path.join(ds_path, item), os.path.join(raw_dir, item))

        exp_dirs = []
        for root, dirs, files in os.walk(raw_dir):
            if "experiment_" in root: exp_dirs.append(root)
        
        if not exp_dirs: continue

        csv_files = set()
        for exp_dir in exp_dirs:
            for f in os.listdir(exp_dir):
                if f.endswith(".csv"): csv_files.add(f)

        for csv_name in csv_files:
            if csv_name not in COLUMN_MAP: continue
            
            combined_rows = []
            for exp_dir in exp_dirs:
                parts = exp_dir.replace("\\", "/").split("/")
                vm_policy, link_policy, wf_policy = "Unknown", "Unknown", "Unknown"
                for part in parts:
                    if part.startswith("experiment_"):
                        p = part.replace("experiment_", "").split("_")
                        if len(p) >= 3: 
                            vm_policy, link_policy, wf_policy = p[0], p[1], p[2]
                
                f_path = os.path.join(exp_dir, csv_name)
                try:
                    names = COLUMN_MAP[csv_name]
                    # Tentative de lecture robuste
                    df = pd.read_csv(f_path, sep=";", comment="#", header=None, names=names, on_bad_lines='skip', engine='python')
                    
                    if "link_utilization" in csv_name:
                        # On s'assure que utilization est numérique
                        df["utilization"] = pd.to_numeric(df["utilization"], errors='coerce')
                        df = df.dropna(subset=["utilization"])
                        df = df.groupby("link_id")["utilization"].mean().reset_index()
                    
                    # Deduplication pour packet_delays (CloudSim logge chaque paquet a chaque hop reseau)
                    if "packet_delays" in csv_name:
                        df = df.drop_duplicates(subset=["pkt_id", "src", "dest"])
                    
                    df["vm_policy"] = vm_policy
                    df["link_policy"] = link_policy
                    df["wf_policy"] = wf_policy
                    combined_rows.append(df)
                except Exception as e:
                    pass # Silencieux pour ne pas polluer la console, les lignes bad sont skippées
            
            if combined_rows:
                master_df = pd.concat(combined_rows, ignore_index=True)
                if "link_policy" in master_df.columns:
                    master_df["link_policy"] = master_df["link_policy"].replace("DynLatBw", "BLA")
                
                out_path = os.path.join(data_dir, csv_name)
                master_df.to_csv(out_path, sep=";", index=False)
                print(f"  [OK] Consolidated {csv_name} -> {data_dir}")

    print("\n[DONE] Results are now organized in Dataset/{raw, plot, synthese} structure.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir")
    args = parser.parse_args()
    organize_and_consolidate(args.results_dir)
