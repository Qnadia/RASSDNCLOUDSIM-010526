import os
import glob
import pandas as pd

base_dir = r"E:\Workspace\v2\cloudsimsdn-research\results\2026-05-01\dataset-mini"
raw_dir = os.path.join(base_dir, "raw")
global_dir = os.path.join(base_dir, "Global")
data_dir = os.path.join(base_dir, "synthese", "data")

os.makedirs(global_dir, exist_ok=True)
os.makedirs(data_dir, exist_ok=True)

exp_dirs = glob.glob(os.path.join(raw_dir, "*", "experiment_*"))

# Identify all CSV files
all_csvs = set()
for d in exp_dirs:
    csvs = [f for f in os.listdir(d) if f.endswith(".csv")]
    all_csvs.update(csvs)

print(f"Found {len(all_csvs)} types of CSV files to merge.")

for csv_name in all_csvs:
    all_data = []
    for d in exp_dirs:
        filepath = os.path.join(d, csv_name)
        if os.path.exists(filepath):
            try:
                df = pd.read_csv(filepath, sep=";")
                if not df.empty:
                    # special case for totals
                    if "total" in csv_name.lower() or "summary" in csv_name.lower():
                        df = df.tail(1)
                    
                    exp_id = os.path.basename(d)
                    parts = exp_id.split('_')
                    if len(parts) >= 4:
                        df.insert(0, 'wf_policy', parts[3])
                        df.insert(0, 'link_policy', parts[2])
                        df.insert(0, 'vm_policy', parts[1])
                    all_data.append(df)
            except Exception as e:
                pass
    
    if all_data:
        merged_df = pd.concat(all_data, ignore_index=True)
        # Rename to include _mini suffix
        out_name = csv_name.replace(".csv", "_mini.csv")
        
        merged_df.to_csv(os.path.join(global_dir, out_name), sep=";", index=False)
        merged_df.to_csv(os.path.join(data_dir, out_name), sep=";", index=False)
        print(f"Saved {out_name}")

print("Done generating missing files for dataset-mini!")
