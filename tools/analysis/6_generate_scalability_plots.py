import os
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import argparse

# Configuration Style
sns.set_theme(style="whitegrid")
plt.rcParams.update({
    'font.family': 'serif',
    'axes.edgecolor': 'black',
    'axes.linewidth': 1.2,
    'figure.autolayout': True
})

def generate_scalability(results_dir):
    datasets = ["dataset-small", "dataset-medium", "dataset-large-congested"]
    all_data = []
    
    for ds in datasets:
        p_pd = os.path.join(results_dir, ds, "synthese", "data", "packet_delays.csv")
        p_e = os.path.join(results_dir, ds, "synthese", "data", "host_energy_total.csv")
        p_sla = os.path.join(results_dir, ds, "synthese", "data", "qos_violations.csv")
        
        if os.path.exists(p_pd) and os.path.exists(p_e):
            # Load Delays
            df_pd = pd.read_csv(p_pd, sep=";")
            df_pd["link_policy"] = df_pd["link_policy"].replace("DynLatBw", "BLA")
            avg_lat = df_pd.groupby("link_policy")["delay_ms"].mean()
            avg_qd = df_pd.groupby("link_policy")["queue_delay_ms"].mean()
            
            # Load Energy
            df_e = pd.read_csv(p_e, sep=";")
            df_e["link_policy"] = df_e["link_policy"].replace("DynLatBw", "BLA")
            df_e.columns = [c.strip().lower() for c in df_e.columns]
            df_last = df_e.sort_values("time").groupby(["link_policy", "vm_policy", "wf_policy", "host_id"]).last().reset_index()
            df_last["scenario"] = df_last["vm_policy"] + "_" + df_last["wf_policy"]
            energy_by_link = df_last.groupby(["link_policy", "scenario"])["energy"].sum().reset_index()
            avg_energy = energy_by_link.groupby("link_policy")["energy"].mean()

            # Load SLA
            sla_count = { "First": 0, "BLA": 0 }
            if os.path.exists(p_sla):
                df_sla = pd.read_csv(p_sla, sep=";")
                df_sla["link_policy"] = df_sla["link_policy"].replace("DynLatBw", "BLA")
                counts = df_sla.groupby("link_policy").size()
                sla_count["First"] = counts.get("First", 0)
                sla_count["BLA"] = counts.get("BLA", 0)

            label = ds.replace("dataset-", "").capitalize()
            for pol in ["First", "BLA"]:
                all_data.append({
                    "Dataset": label,
                    "Policy": pol,
                    "Latency (ms)": avg_lat.get(pol, 0),
                    "Queuing (ms)": avg_qd.get(pol, 0),
                    "Energy (Wh)": avg_energy.get(pol, 0),
                    "SLA Violations": sla_count.get(pol, 0)
                })
    
    if not all_data:
        print("No data found for scalability analysis.")
        return

    df_scal = pd.DataFrame(all_data)
    palette = {"First": "#1f77b4", "BLA": "#ff7f0e"}
    
    fig, axes = plt.subplots(2, 2, figsize=(15, 12))
    fig.suptitle("Scalability Comparison: First vs BLA across Network Scales", fontsize=20, fontweight='bold', y=0.98)

    metrics = [
        ("Latency (ms)", axes[0, 0], "Total Packet Delay"),
        ("Energy (Wh)", axes[0, 1], "Total Energy Consumption"),
        ("Queuing (ms)", axes[1, 0], "Network Queuing Delay"),
        ("SLA Violations", axes[1, 1], "Total SLA Violations")
    ]

    for metric, ax, title in metrics:
        sns.barplot(data=df_scal, x="Dataset", y=metric, hue="Policy", palette=palette, ax=ax, edgecolor='black', alpha=0.85)
        ax.set_title(title, fontsize=14, fontweight='bold')
        ax.set_ylabel(metric)
        ax.grid(axis='y', linestyle='--', alpha=0.5)
        # Annotations
        for p in ax.patches:
            if p.get_height() > 0:
                ax.annotate(f'{p.get_height():.1f}', (p.get_x() + p.get_width() / 2., p.get_height()),
                            ha='center', va='center', fontsize=10, xytext=(0, 7), textcoords='offset points', fontweight='bold')

    plt.tight_layout(rect=[0, 0.03, 1, 0.95])
    output_path = os.path.join(results_dir, "scalability_analysis.png")
    plt.savefig(output_path, dpi=300)
    plt.close()
    print(f"[SUCCESS] Comparative Scalability plot (First vs BLA) generated: {output_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir")
    args = parser.parse_args()
    generate_scalability(args.results_dir)
