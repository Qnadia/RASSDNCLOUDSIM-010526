import pandas as pd
import matplotlib.pyplot as plt
import os
from datetime import datetime
import numpy as np

# Dossiers contenant les résultats
dir_first = r'C:/Workspace/cloudsimsdn090525/cloudsimsdn/experiment1-LFF-BLA-HSJF-ModifEnergy'
dir_bwlat = r'C:/Workspace/cloudsimsdn090525/cloudsimsdn/experiment1-LFF-SFL-HSJF-ModifEnergy'

# Répertoire de sortie avec timestamp
timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
output_dir = f"C:/Workspace/cloudsimsdn090525/graph_outputs/{timestamp}"
os.makedirs(output_dir, exist_ok=True)

def load_csv(directory, filename, columns=None):
    path = os.path.join(directory, filename)
    if not os.path.exists(path):
        print(f"❌ Fichier non trouvé : {path}")
        return pd.DataFrame()
    try:
        df = pd.read_csv(path, sep=';', header=None if columns else 'infer', names=columns)
        df.columns = [c.strip().lower().replace('(', '').replace(')', '').replace('#', '').replace(' ', '').replace('%','') for c in df.columns]
        for col in df.columns:
            if any(key in col for key in ['time', 'delay', 'energy', 'cpu', 'bw', 'ram']):
                df[col] = pd.to_numeric(df[col], errors='coerce')
        return df
    except Exception as e:
        print(f"❌ Erreur de lecture {filename} : {e}")
        return pd.DataFrame()

def plot_host_allocation_summary_comparison():
    columns = ['timestamp', 'hostId', 'vmIds', 'cpu(%)', 'ram(%)', 'bw(%)', 'policy', 'energy(W)']
    df1 = load_csv(dir_first, 'host_allocation_summary.csv', columns)
    df2 = load_csv(dir_bwlat, 'host_allocation_summary.csv', columns)
    
    if df1.empty or df2.empty:
        print("❌ Fichiers d'allocation hôte introuvables ou vides.")
        return

    # Nettoyage des noms de colonnes
    df1.columns = [col.lower().replace('%','').replace('(w)', 'watt').replace('(','').replace(')','') for col in df1.columns]
    df2.columns = df1.columns

    # Associer une politique claire
    df1['policy'] = 'First-Link'
    df2['policy'] = 'Latency+BW-Aware'
    df = pd.concat([df1, df2], ignore_index=True)

    # Sélection des métriques à comparer
    metrics = ['cpu', 'ram', 'bw', 'energyw']
    titles = ['CPU Utilization (%)', 'RAM Utilization (%)', 'Bandwidth Utilization (%)', 'Energy Consumption (W)']
    filenames = ['cpu_summary_comparison.png', 'ram_summary_comparison.png', 'bw_summary_comparison.png', 'energy_summary_comparison.png']

    for metric, title, fname in zip(metrics, titles, filenames):
        plt.figure(figsize=(12, 5))
        for policy_name, style in zip(['First-Link', 'Latency+BW-Aware'], ['o-', 's--']):
            df_policy = df[df['policy'] == policy_name]
            grouped = df_policy.groupby(['timestamp'])[metric].mean()
            plt.plot(grouped.index, grouped.values, style, label=policy_name)
            from matplotlib.ticker import FormatStrFormatter
            plt.gca().yaxis.set_major_formatter(FormatStrFormatter('%.6f'))
            for x, y in zip(grouped.index, grouped.values):
                plt.text(x, y, f"{y:.6f}", ha='center', va='bottom', fontsize=8)



        plt.title(title)
        plt.xlabel("Timestamp")
        plt.ylabel(title)
        plt.legend(loc='lower center', bbox_to_anchor=(0.5, -0.3), ncol=2)
        plt.grid(True)
        plt.tight_layout()
        save_path = os.path.join(output_dir, fname)
        plt.savefig(save_path, facecolor='white')
        print(f"📈 {title} → {save_path}")
        plt.show()
        plt.close()

def plot_energy_per_host_over_time():
    columns = ['timestamp', 'hostId', 'vmIds', 'cpu(%)', 'ram(%)', 'bw(%)', 'policy', 'energy(W)']
    df1 = load_csv(dir_first, 'host_allocation_summary.csv', columns)
    df2 = load_csv(dir_bwlat, 'host_allocation_summary.csv', columns)

    if df1.empty or df2.empty:
        print("❌ Impossible de charger les fichiers host_allocation_summary.csv")
        return

    df1['policy'] = 'First-Link'
    df2['policy'] = 'Latency+BW-Aware'
    df = pd.concat([df1, df2], ignore_index=True)

    # Nettoyage des colonnes
    df.columns = [col.lower().replace('%','').replace('(w)', 'watt').replace('(','').replace(')','') for col in df.columns]
    df['hostid'] = df['hostid'].astype(str).str.replace('h_', '', regex=False)
    df['hostid'] = pd.to_numeric(df['hostid'], errors='coerce')

    for policy in df['policy'].unique():
        df_policy = df[df['policy'] == policy]

        plt.figure(figsize=(12, 6))
        for host_id in sorted(df_policy['hostid'].dropna().unique()):
            df_host = df_policy[df_policy['hostid'] == host_id]
            plt.plot(df_host['timestamp'], df_host['energyw'], label=f"h_{int(host_id)}")

        plt.title(f"Energy Consumption per Host Over Time – {policy}")
        plt.xlabel("Timestamp")
        plt.ylabel("Energy (W)")
        plt.legend(title="Host ID", bbox_to_anchor=(1.05, 1), loc='upper left')
        plt.gca().yaxis.set_major_formatter(plt.FuncFormatter(lambda x, _: f'{x:.6f}'))  # plus de décimales
        plt.tight_layout()
        fig_path = os.path.join(output_dir, f"energy_per_host_{policy.replace('+','').replace(' ','')}.png")
        plt.savefig(fig_path, facecolor='white')
        print(f"📈 Énergie par hôte ({policy}) → {fig_path}")
        plt.show()
        plt.close()


def plot_cpu_per_host_over_time():
    columns = ['timestamp', 'hostId', 'vmIds', 'cpu(%)', 'ram(%)', 'bw(%)', 'policy', 'energy(W)']
    df1 = load_csv(dir_first, 'host_allocation_summary.csv', columns)
    df2 = load_csv(dir_bwlat, 'host_allocation_summary.csv', columns)

    if df1.empty or df2.empty:
        print("❌ Impossible de charger les fichiers host_allocation_summary.csv")
        return

    df1['policy'] = 'First-Link'
    df2['policy'] = 'Latency+BW-Aware'
    df = pd.concat([df1, df2], ignore_index=True)
    df['hostid'] = df['hostid'].astype(str).str.replace('h_', '', regex=False)
    df['hostid'] = pd.to_numeric(df['hostid'], errors='coerce')


    df.columns = [col.lower().replace('%','').replace('(w)', 'watt').replace('(','').replace(')','') for col in df.columns]

    for policy in df['policy'].unique():
        df_policy = df[df['policy'] == policy]

        plt.figure(figsize=(12, 6))
        for host_id in sorted(df_policy['hostid'].dropna().unique(), key=lambda x: int(str(x).strip('h_')) if str(x).startswith('h_') else int(x)):
            df_host = df_policy[df_policy['hostid'] == host_id]
            plt.plot(df_host['timestamp'], df_host['cpu'], label=f"{host_id}")


        plt.title(f"CPU Utilization Over Time per Host – {policy}")
        plt.xlabel("Timestamp")
        plt.ylabel("CPU (%)")
        plt.legend(title="Host ID", bbox_to_anchor=(1.05, 1), loc='upper left')
        plt.tight_layout()
        filename = os.path.join(output_dir, f'cpu_per_host_{policy.replace("+", "").replace(" ", "")}.png')
        plt.savefig(filename, facecolor='white')
        print(f"📈 Courbe CPU par hôte ({policy}) → {filename}")
        plt.show()
        plt.close()

def plot_metric_per_host_over_time(metric='ram'):
    assert metric in ['ram', 'bw'], "❌ Seules les métriques 'ram' ou 'bw' sont autorisées."

    columns = ['timestamp', 'hostId', 'vmIds', 'cpu(%)', 'ram(%)', 'bw(%)', 'policy', 'energy(W)']
    df1 = load_csv(dir_first, 'host_allocation_summary.csv', columns)
    df2 = load_csv(dir_bwlat, 'host_allocation_summary.csv', columns)

    if df1.empty or df2.empty:
        print("❌ Impossible de charger les fichiers host_allocation_summary.csv")
        return

    df1['policy'] = 'First-Link'
    df2['policy'] = 'Latency+BW-Aware'
    df = pd.concat([df1, df2], ignore_index=True)
    df.columns = [col.lower().replace('%', '').replace('(w)', 'watt').replace('(', '').replace(')', '') for col in df.columns]
    df['hostid'] = df['hostid'].astype(str).str.replace('h_', '', regex=False)
    df['hostid'] = pd.to_numeric(df['hostid'], errors='coerce')

    for policy in df['policy'].unique():
        df_policy = df[df['policy'] == policy]

        plt.figure(figsize=(12, 6))
        for host_id in sorted(df_policy['hostid'].dropna().unique()):
            df_host = df_policy[df_policy['hostid'] == host_id]
            plt.plot(df_host['timestamp'], df_host[metric], label=f"h_{int(host_id)}")

        plt.title(f"{metric.upper()} Utilization Over Time per Host – {policy}")
        plt.xlabel("Timestamp")
        plt.ylabel(f"{metric.upper()} (%)")
        plt.legend(title="Host ID", bbox_to_anchor=(1.05, 1), loc='upper left')
        plt.tight_layout()
        fig_path = os.path.join(output_dir, f"{metric}_per_host_{policy.replace('+','').replace(' ','')}.png")
        plt.savefig(fig_path, facecolor='white')
        print(f"📈 Courbe {metric.upper()} par hôte ({policy}) → {fig_path}")
        plt.show()
        plt.close()


def plot_comparison(ylabel, title, filename, df_first_series, df_bwlat_series, common_index, x_vals, labels, bar_width):
    x_vals = np.array(x_vals)
    plt.figure(figsize=(12, 5))
    plt.bar(x_vals - bar_width/2, df_first_series.reindex(common_index).values, width=bar_width, label='First-Link')
    plt.bar(x_vals + bar_width/2, df_bwlat_series.reindex(common_index).values, width=bar_width, label='Latency+BW-Aware')
    plt.xticks(x_vals, labels, rotation=45)
    plt.title(title)
    plt.xlabel("Src → Dst")
    plt.ylabel(ylabel)
    plt.legend(loc='lower center', bbox_to_anchor=(0.5, -0.3), ncol=2, frameon=False)
    plt.grid(False)
    plt.tight_layout()
    plt.savefig(filename, facecolor='white')
    plt.show()
    plt.close()

# def plot_energy_comparison():
#     df1 = load_csv(dir_first, 'host_energy_total.csv', ['time(s)', 'hostId', 'energy(watt)'])
#     df2 = load_csv(dir_bwlat, 'host_energy_total.csv', ['time(s)', 'hostId', 'energy(watt)'])
#     if df1.empty or df2.empty:
#         return
#     df1_grouped = df1.groupby('hostid')['energywatt'].max()
#     df2_grouped = df2.groupby('hostid')['energywatt'].max()
#     x = np.arange(len(df1_grouped.index))
#     bar_width = 0.35
#     plt.figure(figsize=(8,5))
#     plt.bar(x - bar_width/2, df1_grouped.values, width=bar_width, label='First-Link', alpha=0.7)
#     plt.bar(x + bar_width/2, df2_grouped.values, width=bar_width, label='Latency+BW-Aware', alpha=0.7)
#     plt.xticks(x, df1_grouped.index.astype(str))
#     plt.title("Max Energy Consumption per Host")
#     plt.xlabel("Host ID")
#     plt.ylabel("Max Energy (W)")
#     plt.legend(loc='lower center', bbox_to_anchor=(0.5, -0.3), ncol=2, frameon=False)
#     plt.grid(False)
#     plt.tight_layout()
#     plt.savefig(os.path.join(output_dir, "energy_comparison.png"), facecolor='white')
#     plt.show()
#     plt.close()~

def plot_energy_comparison():
    columns = ['time(s)', 'host_name', 'hostid', 'energy(watt)']
    df1 = load_csv(dir_first, 'host_energy_total.csv', columns)
    df2 = load_csv(dir_bwlat, 'host_energy_total.csv', columns)

    if df1.empty or df2.empty:
        print("❌ Fichiers d'énergie vides ou incorrects.")
        return

    # ➤ Lignes valides par hôte (hostid non vide)
    df1_hosts = df1[df1['hostid'].notna()]
    df2_hosts = df2[df2['hostid'].notna()]

    # ➤ Ligne TOTAL (hostid NaN)
    total1_val = round(df1[df1['hostid'].isna()]['energywatt'].values[0], 4)
    total2_val = round(df2[df2['hostid'].isna()]['energywatt'].values[0], 4)
    diff_total = round(abs(total1_val - total2_val), 4)

    print(f"🔋 First-Link TOTAL: {total1_val} W")
    print(f"🔋 Latency+BW-Aware TOTAL: {total2_val} W")
    print(f"📊 Différence totale: {diff_total:.4f} W")

    # ➤ Groupement par hôte
    df1_grouped = df1_hosts.groupby('hostid')['energywatt'].sum().round(4)
    df2_grouped = df2_hosts.groupby('hostid')['energywatt'].sum().round(4)

    # ➤ Comparaison par hôte
    print("\n🔍 Comparaison par hôte (4 chiffres après la virgule) :")
    for host_id in sorted(set(df1_grouped.index) & set(df2_grouped.index)):
        val1 = df1_grouped.loc[host_id]
        val2 = df2_grouped.loc[host_id]
        delta = round(val2 - val1, 4)
        print(f"  - h_{int(host_id)} → First: {val1:.4f} | Aware: {val2:.4f} | Δ: {delta:+.4f}")

    # ➤ Affichage du graphique
    x = np.arange(len(df1_grouped.index))
    bar_width = 0.35

    plt.figure(figsize=(8, 5))
    plt.bar(x - bar_width/2, df1_grouped.values, width=bar_width, label='First-Link', alpha=0.7)
    plt.bar(x + bar_width/2, df2_grouped.values, width=bar_width, label='Latency+BW-Aware', alpha=0.7)
    plt.xticks(x, df1_grouped.index.astype(int))
    plt.title(f"Total Energy per Host\nDifférence totale: {diff_total:.4f} W")
    plt.xlabel("Host ID")
    plt.ylabel("Total Energy (W)")
    plt.gca().yaxis.set_major_formatter(plt.FuncFormatter(lambda x, _: f'{x:.4f}'))  # << ici
    plt.legend(loc='lower center', bbox_to_anchor=(0.5, -0.3), ncol=2, frameon=False)
    ...
    plt.grid(False)
    plt.tight_layout()
    fig_path = os.path.join(output_dir, "energy_comparison_total.png")
    plt.savefig(fig_path, facecolor='white')
    print(f"\n📁 Graphique sauvegardé : {fig_path}")
    plt.show()
    plt.close()





def plot_packet_delay_comparison():
    df1 = load_csv(dir_first, 'packet_delays.csv', ['packetId', 'src', 'dst', 'delay(ms)'])
    df2 = load_csv(dir_bwlat, 'packet_delays.csv', ['packetId', 'src', 'dst', 'delay(ms)'])
    if df1.empty or df2.empty:
        return
    df1_grouped = df1.groupby('packetid')['delayms'].mean()
    df2_grouped = df2.groupby('packetid')['delayms'].mean()
    plt.figure(figsize=(10,5))
    plt.plot(df1_grouped.index, df1_grouped.values, 'o-', label='First-Link')
    plt.plot(df2_grouped.index, df2_grouped.values, 's-', label='Latency+BW-Aware')
    plt.title("Average Packet Delay")
    plt.xlabel("Packet ID")
    plt.ylabel("Average Delay (ms)")
    plt.legend(loc='lower center', bbox_to_anchor=(0.5, -0.3), ncol=2, frameon=False)
    plt.grid(False)
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "packet_delay_comparison.png"), facecolor='white')
    plt.show()
    plt.close()

def plot_path_latency_comparison():
    columns = ['time(s)', 'src', 'dst', 'path', 'min_bw_Mbps', 'avgBwUsedMbps',
               'avgPctUse(%)', 'network_latency(ms)', 'processing_delay(ms)',
               'total_delay(ms)', 'selected']
    df1 = load_csv(dir_first, 'path_latency_final.csv', columns)
    df2 = load_csv(dir_bwlat, 'path_latency_final.csv', columns)
    if df1.empty or df2.empty:
        return
    df1_grouped = df1.groupby(['src', 'dst'])['total_delayms'].mean()
    df2_grouped = df2.groupby(['src', 'dst'])['total_delayms'].mean()
    common_index = df1_grouped.index.intersection(df2_grouped.index)
    x = range(len(common_index))
    labels = [f"{src}->{dst}" for src, dst in common_index]
    bar_width = 0.35
    plot_comparison('Total Delay (ms)', 'Total Path Delay Comparison', f"{output_dir}/path_delay_comparison.png", df1_grouped, df2_grouped, common_index, x, labels, bar_width)

def plot_host_utilization_comparison():
    df1 = load_csv(dir_first, 'host_utilization.csv', ['time(s)', 'hostId', 'cpu(%)', 'ram(%)', 'bw(%)'])
    df2 = load_csv(dir_bwlat, 'host_utilization.csv', ['time(s)', 'hostId', 'cpu(%)', 'ram(%)', 'bw(%)'])
    if df1.empty or df2.empty:
        return
    df1.columns = [col.replace('%', '') for col in df1.columns]
    df2.columns = [col.replace('%', '') for col in df2.columns]
    df1_avg = df1.groupby('hostid')[['cpu', 'ram', 'bw']].mean()
    df2_avg = df2.groupby('hostid')[['cpu', 'ram', 'bw']].mean()
    host_ids = df1_avg.index.astype(str)
    metrics = ['cpu', 'ram', 'bw']
    titles = ['Average CPU Utilization (%)', 'Average RAM Utilization (%)', 'Average Bandwidth Utilization (%)']
    filenames = ['cpu_utilization_comparison.png', 'ram_utilization_comparison.png', 'bw_utilization_comparison.png']
    for metric, title, fname in zip(metrics, titles, filenames):
        plt.figure(figsize=(10, 5))
        x_vals = np.arange(len(df1_avg.index))
        plt.bar(x_vals - 0.2, df1_avg[metric].values, width=0.4, label='First-Link')
        plt.bar(x_vals + 0.2, df2_avg[metric].values, width=0.4, label='Latency+BW-Aware')
        plt.title(title)
        plt.xlabel("Host ID")
        plt.ylabel(f"{metric.upper()} (%)")
        plt.xticks(x_vals, host_ids)
        plt.legend(loc='lower center', bbox_to_anchor=(0.5, -0.3), ncol=2, frameon=False)
        plt.grid(False)
        plt.tight_layout()
        plt.savefig(os.path.join(output_dir, fname), facecolor='white')
        plt.show()
        plt.close()

if __name__ == "__main__":
    print("📂 Fichiers dans First-Link:", os.listdir(dir_first) if os.path.exists(dir_first) else "❌ Dossier introuvable")
    print("📂 Fichiers dans Latency+BW:", os.listdir(dir_bwlat) if os.path.exists(dir_bwlat) else "❌ Dossier introuvable")
    print("📊 Génération des graphiques...")

    plot_host_allocation_summary_comparison()
    plot_energy_per_host_over_time()
    plot_cpu_per_host_over_time()
    plot_metric_per_host_over_time(metric='ram')
    plot_metric_per_host_over_time(metric='bw')


    plot_energy_comparison()
    plot_packet_delay_comparison()
    plot_path_latency_comparison()
    #plot_host_utilization_comparison()

    print(f"✅ Terminé. Graphiques enregistrés dans : {output_dir}")
