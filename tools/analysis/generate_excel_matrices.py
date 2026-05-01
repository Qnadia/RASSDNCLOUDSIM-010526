import pandas as pd
import os

# Chemin du fichier source
source_file = "results/2026-04-19/dataset-large/figures_consolidated/consolidated_summary.csv"
output_dir = "results/2026-04-19/dataset-large/MATRICES_EXCEL/"

if not os.path.exists(output_dir):
    os.makedirs(output_dir)

# Lecture du fichier consolidé
df = pd.read_csv(source_file, sep=";")

# On filtre pour chaque workload
workloads = df['wf_policy'].unique()

# Liste des métriques à transformer en matrices
metrics = {
    'energy_Wh': 'ENERGIE',
    'avg_latency_s': 'LATENCE',
    'sla_violations': 'SLA',
    'avg_pkt_ms': 'DELAIS_PAQUETS',
    'active_cpu': 'CPU_UTIL',
    'active_ram': 'RAM_UTIL',
    'active_bw': 'BW_UTIL'
}

for wf in workloads:
    df_wf = df[df['wf_policy'] == wf]
    
    for col, name in metrics.items():
        if col in df_wf.columns:
            # Création du tableau croisé (Lignes: Routage, Colonnes: Placement)
            pivot = df_wf.pivot(index='link_policy', columns='vm_alloc', values=col)
            # Sauvegarde avec point-virgule pour Excel FR
            pivot.to_csv(f"{output_dir}MATRICE_{name}_{wf}.csv", sep=";")

print(f"DONE: ALL matrices generated in {output_dir}")
