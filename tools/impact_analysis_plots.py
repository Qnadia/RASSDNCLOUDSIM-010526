import os
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# Config
BASE_RESULTS_DIR = "results/2026-03-11/dataset-small"
OUTPUT_DIR = os.path.join(BASE_RESULTS_DIR, "analysis_plots")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Style
plt.rcParams.update({'font.family': 'sans-serif', 'font.size': 11})
COLORS = {'BwAllocN': '#2ca02c', 'First': '#d62728'} # Green for BwAllocN, Red for First

def load_summary():
    summary_file = os.path.join(BASE_RESULTS_DIR, "figures_consolidated/consolidated_summary.csv")
    if not os.path.exists(summary_file):
        print(f"Error: {summary_file} not found.")
        return None
    return pd.read_csv(summary_file, sep=";")

def plot_latency_histogram(df):
    plt.figure(figsize=(10, 6))
    
    # Aggregating by VM Policy and Link Policy
    pivot_df = df.pivot_table(index='vm_alloc', columns='link_policy', values='avg_pkt_ms', aggfunc='mean')
    
    # Plotting
    ax = pivot_df.plot(kind='bar', color=[COLORS.get(col, '#7f7f7f') for col in pivot_df.columns], width=0.8, ax=plt.gca())
    
    plt.title("Impact de la Sélection de Lien sur le Délai Paquet Moyen", fontsize=13, weight='bold', pad=15)
    plt.xlabel("Politique d'Allocation de VM", fontsize=12)
    plt.ylabel("Délai Paquet Moyen (ms)", fontsize=12)
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.legend(title="Politique de Lien")
    plt.xticks(rotation=0)
    
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_DIR, "impact_latency_histogram.png"), dpi=300)
    print("Saved: impact_latency_histogram.png")

def plot_energy_pie(df):
    # Sum energy per link policy
    energy_sum = df.groupby('link_policy')['energy_Wh'].sum()
    
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 7))
    
    # Combined Pie
    labels = [f"{idx}\n({val:.1f} Wh)" for idx, val in energy_sum.items()]
    ax1.pie(energy_sum, labels=labels, autopct='%1.1f%%', 
            colors=[COLORS.get(idx, '#7f7f7f') for idx in energy_sum.index],
            startangle=140, explode=[0.1 if idx == 'BwAllocN' else 0 for idx in energy_sum.index],
            shadow=True, textprops={'fontsize': 10, 'weight': 'bold'})
    ax1.set_title("Consommation Énergétique Totale", fontsize=12, weight='bold')
    
    # Comparison Bar (Log scale to show the massive difference)
    energy_sum.plot(kind='bar', color=[COLORS.get(idx, '#7f7f7f') for idx in energy_sum.index], ax=ax2)
    ax2.set_title("Comparaison Échelle Linéaire (Wh)", fontsize=12, weight='bold')
    ax2.set_ylabel("Watt-heures (Wh)")
    ax2.grid(axis='y', linestyle='--', alpha=0.7)
    
    plt.suptitle("Impact Energétique : BwAllocN vs First", fontsize=15, weight='bold', y=0.98)
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_DIR, "impact_energy_comparison.png"), dpi=300)
    print("Saved: impact_energy_comparison.png")

def plot_packet_delay_boxplot():
    all_packet_data = []
    mff_dir = os.path.join(BASE_RESULTS_DIR, "MFF")
    if not os.path.exists(mff_dir):
        print("MFF directory not found for boxplot.")
        return

    for exp in os.listdir(mff_dir):
        if "experiment_MFF_" in exp:
            link_pol = "BwAllocN" if "BwAllocN" in exp else "First"
            pfile = os.path.join(mff_dir, exp, "packet_delays.csv")
            if os.path.exists(pfile):
                try:
                    dfp = pd.read_csv(pfile, sep=";", names=["id", "src", "dst", "size", "delay"], comment="#")
                    dfp['link_policy'] = link_pol
                    all_packet_data.append(dfp)
                except: continue
    
    if not all_packet_data:
        print("No packet delay data found.")
        return
    
    df_all = pd.concat(all_packet_data)
    
    plt.figure(figsize=(10, 6))
    
    # Prepare data for boxplot
    data_to_plot = [df_all[df_all['link_policy'] == lp]['delay'] for lp in ['BwAllocN', 'First']]
    
    bp = plt.boxplot(data_to_plot, patch_artist=True, labels=['BwAllocN', 'First'], widths=0.6)
    
    # Colors
    colors_list = [COLORS['BwAllocN'], COLORS['First']]
    for patch, color in zip(bp['boxes'], colors_list):
        patch.set_facecolor(color)
        patch.set_alpha(0.7)

    plt.title("Dispersion des Délais de Paquets (MFF)", fontsize=13, weight='bold')
    plt.xlabel("Politique de Lien", fontsize=12)
    plt.ylabel("Délai du Paquet (ms)", fontsize=12)
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    
    max_delay = df_all['delay'].max()
    if max_delay > 10000:
        plt.yscale('log')
        plt.ylabel("Délai du Paquet (ms) - Échelle Log")
        plt.title("Dispersion des Délais de Paquets (MFF) - Échelle Logarithmique", fontsize=13, weight='bold')

    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_DIR, "impact_packet_delay_boxplot.png"), dpi=300)
    print("Saved: impact_packet_delay_boxplot.png")

def main():
    df = load_summary()
    if df is not None:
        plot_latency_histogram(df)
        plot_energy_pie(df)
        plot_packet_delay_boxplot()
    print(f"\nAll impact plots generated in: {OUTPUT_DIR}")

if __name__ == "__main__":
    main()
