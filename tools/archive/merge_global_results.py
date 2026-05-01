import pandas as pd
import os
import glob

# Configuration
date_str = "2026-04-19"
base_results_dir = f"results/{date_str}/"
datasets = ["dataset-small-congested", "dataset-medium-congested", "dataset-large"]
output_dir = os.path.join(base_results_dir, "GLOBAL_CONSOLIDATION/")

if not os.path.exists(output_dir):
    os.makedirs(output_dir)

# 1. Identifier tous les types de fichiers CSV possibles
all_csv_types = set()
for ds in datasets:
    ds_path = os.path.join(base_results_dir, ds)
    exp_paths = glob.glob(os.path.join(ds_path, "*", "experiment_*"))
    if exp_paths:
        csv_files = [f for f in os.listdir(exp_paths[0]) if f.endswith('.csv')]
        all_csv_types.update(csv_files)

print(f"Found {len(all_csv_types)} types of CSV files to merge globally.")

# 2. Fusionner chaque type de fichier pour tous les datasets
for log_name in all_csv_types:
    output_name = "GLOBAL_MASTER_" + log_name
    print(f"Global Merging {log_name}...")
    all_data = []
    
    for ds in datasets:
        ds_path = os.path.join(base_results_dir, ds)
        exp_paths = glob.glob(os.path.join(ds_path, "*", "experiment_*"))
        
        for path in exp_paths:
            file_path = os.path.join(path, log_name)
            if os.path.exists(file_path):
                exp_id = os.path.basename(path)
                try:
                    df = pd.read_csv(file_path, sep=";")
                    if not df.empty:
                        # Cas spécial pour les totaux
                        if "total" in log_name.lower():
                            df = df.tail(1)

                        # Extraction des politiques
                        parts = exp_id.split('_')
                        if len(parts) >= 4:
                            df.insert(0, 'workload_policy', parts[3])
                            df.insert(0, 'routing_policy', parts[2])
                            df.insert(0, 'vm_policy', parts[1])
                        
                        df.insert(0, 'experiment_id', exp_id)
                        # AJOUT DE LA COLONNE DATASET
                        df.insert(0, 'dataset_name', ds)
                        
                        all_data.append(df)
                except Exception as e:
                    pass
    
    if all_data:
        master_df = pd.concat(all_data, ignore_index=True)
        master_df.to_csv(os.path.join(output_dir, output_name), sep=";", index=False)
        print(f"DONE: Created {output_name}")

print(f"\nSUCCESS: Global Consolidation complete in {output_dir}")
