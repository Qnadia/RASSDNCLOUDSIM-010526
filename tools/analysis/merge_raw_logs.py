import pandas as pd
import os
import glob

# Configuration
base_dir = "results/2026-04-19/dataset-large/"
output_dir = os.path.join(base_dir, "RAW_DATA_ALL_CONSOLIDATED/")

if not os.path.exists(output_dir):
    os.makedirs(output_dir)

# 1. Identifier tous les types de fichiers CSV présents (en prenant le 1er dossier comme référence)
exp_paths = glob.glob(os.path.join(base_dir, "*", "experiment_*"))
if not exp_paths:
    print("No experiments found!")
    exit()

reference_exp = exp_paths[0]
csv_files = [f for f in os.listdir(reference_exp) if f.endswith('.csv')]

print(f"Found {len(csv_files)} types of CSV files to merge.")

# 2. Fusionner chaque type de fichier
for log_name in csv_files:
    output_name = "MASTER_" + log_name
    print(f"Merging {log_name}...")
    all_data = []
    
    for path in exp_paths:
        file_path = os.path.join(path, log_name)
        if os.path.exists(file_path):
            exp_id = os.path.basename(path)
            try:
                df = pd.read_csv(file_path, sep=";")
                if not df.empty:
                    # CAS SPÉCIAL : Pour les totaux, on ne prend que la dernière ligne
                    if "total" in log_name.lower():
                        df = df.tail(1)

                    # Extraction des politiques depuis le nom du dossier (format: experiment_VM_Routing_Workload)
                    parts = exp_id.split('_')
                    if len(parts) >= 4:
                        df.insert(0, 'workload_policy', parts[3])
                        df.insert(0, 'routing_policy', parts[2])
                        df.insert(0, 'vm_policy', parts[1])
                    
                    df.insert(0, 'experiment_id', exp_id)
                    all_data.append(df)
            except Exception as e:
                pass
    
    if all_data:
        master_df = pd.concat(all_data, ignore_index=True)
        master_df.to_csv(os.path.join(output_dir, output_name), sep=";", index=False)
        print(f"DONE: Created {output_name}")

print(f"\nSUCCESS: All {len(csv_files)} data types merged in {output_dir}")
