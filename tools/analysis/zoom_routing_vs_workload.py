import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import os
import argparse

def generate_zoom_plots(results_dir):
    # Process GLOBAL consolidation
    global_summary = os.path.join(results_dir, "GLOBAL_CONSOLIDATION", "figures_consolidated", "consolidated_summary.csv")
    if os.path.exists(global_summary):
        process_summary(global_summary, os.path.join(results_dir, "ZOOM_ANALYSIS", "GLOBAL"))

    # Process each dataset
    for ds_folder in os.listdir(results_dir):
        ds_path = os.path.join(results_dir, ds_folder)
        if os.path.isdir(ds_path) and "dataset-" in ds_folder:
            ds_summary = os.path.join(ds_path, "figures_consolidated", "consolidated_summary.csv")
            if os.path.exists(ds_summary):
                process_summary(ds_summary, os.path.join(results_dir, "ZOOM_ANALYSIS", ds_folder))

def process_summary(summary_path, output_dir):
    print(f"Processing: {summary_path}")
    df = pd.read_csv(summary_path, sep=";")
    
    # Filter for the two routing policies
    df_filtered = df[df['link_policy'].isin(['First', 'DynLatBw'])]
    if df_filtered.empty:
        print(f"  No First/DynLatBw data in {summary_path}")
        return

    sns.set_theme(style="whitegrid")
    plt.rcParams.update({"font.size": 12})

    dataset_label = os.path.basename(os.path.dirname(os.path.dirname(output_dir))) if "GLOBAL" not in output_dir else "GLOBAL"

    metrics = [
        ("avg_latency_s", "Latence E2E Moyenne (s)", "latency"),
        ("energy_Wh", "Consommation Énergie (Wh)", "energy"),
        ("sla_violations", "Nombre de Violations SLA", "sla"),
        ("avg_pkt_ms", "Délai de Paquet Moyen (ms)", "packet_delay"),
        ("avg_queue_ms", "Délai de File d'Attente Moyen (ms)", "queuing_delay")
    ]

    # Analysis Types: by Workload and by VM Policy
    analysis_types = [
        ("wf_policy", "Workload Scheduling Policy", "BY_WORKLOAD"),
        ("vm_policy", "VM Allocation Policy", "BY_VM_POLICY")
    ]

    for group_col, xlabel, subfolder in analysis_types:
        group_output_dir = os.path.join(output_dir, subfolder)
        os.makedirs(group_output_dir, exist_ok=True)
        
        for col, ylabel, fname_prefix in metrics:
            if col in df_filtered.columns and df_filtered[col].notnull().any():
                plt.figure(figsize=(10, 6))
                sns.barplot(data=df_filtered, x=group_col, y=col, hue="link_policy", palette="Set1")
                plt.title(f"[{dataset_label}] {ylabel.split('(')[0].strip()} (by {group_col})", fontsize=14, weight='bold')
                plt.ylabel(ylabel)
                plt.xlabel(xlabel)
                plt.savefig(os.path.join(group_output_dir, f"{fname_prefix}_zoom.png"), dpi=300, bbox_inches='tight')
                plt.close()
        print(f"  Generated {subfolder} plots in {group_output_dir}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir")
    args = parser.parse_args()
    generate_zoom_plots(args.results_dir)
