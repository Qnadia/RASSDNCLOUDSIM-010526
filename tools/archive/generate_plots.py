import pandas as pd
import matplotlib.pyplot as plt
import os

# Configuration
csv_path = 'comparison_data.csv'
output_dir = 'results/2026-05-14/Sim VF/plots'
os.makedirs(output_dir, exist_ok=True)

# Load data
df = pd.read_csv(csv_path, sep=';')

# Cleaning: Remove Link_First if it has extreme outliers (like energy > 10000) to keep plots readable, 
# or just plot them as is but warn. Actually, let's keep them and use log scale if needed.

datasets = df['Dataset'].unique()

for ds in datasets:
    ds_df = df[df['Dataset'] == ds].copy()
    
    # --- Energy Plot ---
    plt.figure(figsize=(12, 6))
    pivot_energy = ds_df.pivot(index='VM', columns='Link', values='TotalEnergy_Wh')
    ax = pivot_energy.plot(kind='bar', figsize=(12, 6), width=0.8)
    plt.title(f'Consommation Énergétique - Dataset {ds.upper()}', fontsize=14)
    plt.ylabel('Énergie (Wh)')
    plt.xlabel('Politique de Placement VM')
    plt.xticks(rotation=0)
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.legend(title='Link Policy', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, f'energy_{ds}.png'))
    plt.close()
    
    # --- Latency Plot ---
    plt.figure(figsize=(12, 6))
    pivot_lat = ds_df.pivot(index='VM', columns='Link', values='AvgLatency_ms')
    ax = pivot_lat.plot(kind='bar', figsize=(12, 6), width=0.8, color=['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728'])
    plt.title(f'Latence Moyenne - Dataset {ds.upper()}', fontsize=14)
    plt.ylabel('Latence (ms)')
    plt.xlabel('Politique de Placement VM')
    plt.xticks(rotation=0)
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.legend(title='Link Policy', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, f'latency_{ds}.png'))
    plt.close()

print(f"✅ Plots generated successfully in {output_dir}")
