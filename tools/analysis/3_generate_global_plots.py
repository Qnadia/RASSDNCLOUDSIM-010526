import os
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import argparse
import numpy as np

# Configuration Style Scientifique SSLAB
sns.set_theme(style="whitegrid")
plt.rcParams.update({
    'font.family': 'serif',
    'axes.edgecolor': 'black',
    'axes.linewidth': 1.5,
    'grid.color': '#ddd',
    'grid.linestyle': '--',
    'grid.alpha': 0.7,
    'legend.frameon': True,
    'legend.fancybox': True,
    'figure.autolayout': True
})

def generate_plots(results_dir):
    if not os.path.isdir(results_dir):
        print(f"[ERR] Dossier introuvable : {results_dir}")
        return

    datasets = [d for d in os.listdir(results_dir) if os.path.isdir(os.path.join(results_dir, d))]
    palette_link = {"First": "#d62728", "BLA": "#1f77b4"}
    palette_vm = {"LFF": "#ff7f0e", "MFF": "#1f77b4", "LWFF": "#2ca02c"}
    
    for ds_name in datasets:
        ds_path = os.path.join(results_dir, ds_name)
        synthese_dir = os.path.join(ds_path, "synthese")
        plot_dir = os.path.join(ds_path, "plot")
        
        if not os.path.exists(synthese_dir): continue
        if not os.path.exists(plot_dir): os.makedirs(plot_dir)

        def load_csv(name):
            p = os.path.join(synthese_dir, "data", name)
            if os.path.exists(p):
                try:
                    df = pd.read_csv(p, sep=";")
                    if "link_policy" in df.columns:
                        df["link_policy"] = df["link_policy"].replace("DynLatBw", "BLA")
                    if "vm_policy" in df.columns and "wf_policy" in df.columns:
                        df["group_label"] = df["vm_policy"] + "\n" + df["wf_policy"]
                    return df
                except Exception as e:
                    print(f"  [ERR] {name}: {e}")
            return None

        print(f"--- GÉNÉRATION PACK GRAPHIQUES (1 à 13) : {ds_name} ---")

        df_e = load_csv("host_energy_total.csv")
        df_pd = load_csv("packet_delays.csv")
        df_util = load_csv("host_utilization.csv")
        df_lu = load_csv("link_utilization_up.csv")

        # --- FIG 1: ENERGY ---
        if df_e is not None:
            df_last = df_e.sort_values("time").groupby(["link_policy", "vm_policy", "wf_policy", "host_id"]).last().reset_index()
            df_sum = df_last.groupby(["link_policy", "vm_policy"])["energy"].sum().reset_index()
            plt.figure(figsize=(10, 6))
            sns.barplot(data=df_sum, x="vm_policy", y="energy", hue="link_policy", palette=palette_link)
            plt.title("Fig 1: Consommation Énergétique Totale (Wh)", fontsize=14, fontweight='bold')
            plt.savefig(os.path.join(plot_dir, "-fig1_energy.png"), dpi=300)
            plt.close()

        # --- FIG 2 & 6: LATENCY ---
        if df_pd is not None:
            plt.figure(figsize=(10, 6))
            sns.barplot(data=df_pd, x="vm_policy", y="delay_ms", hue="link_policy", palette=palette_link)
            plt.title("Fig 2: Délai Moyen des Paquets (ms)", fontsize=14, fontweight='bold')
            plt.savefig(os.path.join(plot_dir, "-fig2_latency.png"), dpi=300)
            plt.savefig(os.path.join(plot_dir, "-fig6_routing_latency.png"), dpi=300)
            plt.close()

        # --- FIG 3 & 8: SLA ---
        if df_pd is not None:
            df_pd["sla_sev"] = df_pd["delay_ms"] / df_pd["proc_delay_ms"].replace(0, 1)
            plt.figure(figsize=(10, 6))
            sns.barplot(data=df_pd, x="vm_policy", y="sla_sev", hue="link_policy", palette=palette_link)
            plt.title("Fig 3: Sévérité des Violations SLA", fontsize=14, fontweight='bold')
            plt.savefig(os.path.join(plot_dir, "-fig3_sla.png"), dpi=300)
            plt.savefig(os.path.join(plot_dir, "-fig8_routing_sla.png"), dpi=300)
            plt.close()

        # --- FIG 4: CDF ---
        if df_pd is not None:
            plt.figure(figsize=(10, 6))
            for pol in sorted(df_pd['link_policy'].unique()):
                subset = df_pd[df_pd['link_policy'] == pol]
                sorted_d = np.sort(subset['delay_ms'])
                y = np.arange(len(sorted_d)) / float(len(sorted_d))
                plt.plot(sorted_d, y, label=pol, lw=2.5)
            plt.title("Fig 4: CDF du Délai des Paquets", fontsize=14, fontweight='bold')
            plt.xlabel("Délai (ms)"); plt.ylabel("Probabilité Cumulative")
            plt.legend(); plt.grid(True, alpha=0.3)
            plt.savefig(os.path.join(plot_dir, "-fig4_packet_delay.png"), dpi=300)
            plt.close()

        # --- FIG 5: UTILIZATION ---
        if df_util is not None:
            # Nettoyage des colonnes (certaines peuvent être nommées cpu_util ou cpu)
            rename_map = {"cpu_util": "CPU", "ram_util": "RAM", "bw_util": "BW", "cpu": "CPU", "ram": "RAM", "bw": "BW"}
            df_u = df_util.rename(columns=rename_map)
            metrics = [c for c in ["CPU", "RAM", "BW"] if c in df_u.columns]
            df_melt = df_u.melt(id_vars=["link_policy"], value_vars=metrics, var_name="Resource", value_name="Usage")
            df_melt["Usage"] *= 100
            plt.figure(figsize=(10, 6))
            sns.barplot(data=df_melt, x="Resource", y="Usage", hue="link_policy", palette=palette_link)
            plt.title("Fig 5: Utilisation Moyenne des Ressources", fontsize=14, fontweight='bold')
            plt.ylabel("Utilisation (%)"); plt.ylim(0, 100)
            plt.savefig(os.path.join(plot_dir, "-fig5_utilization.png"), dpi=300)
            plt.close()

        # --- FIG 7: ROUTING ENERGY ---
        if df_e is not None:
            df_last = df_e.sort_values("time").groupby(["link_policy", "vm_policy", "wf_policy", "host_id"]).last().reset_index()
            df_sum = df_last.groupby(["link_policy", "vm_policy"])["energy"].sum().reset_index()
            plt.figure(figsize=(10, 6))
            sns.barplot(data=df_sum, x="vm_policy", y="energy", hue="link_policy", palette=palette_link)
            plt.title("Fig 7: Impact du Routage sur l'Énergie", fontsize=14, fontweight='bold')
            plt.savefig(os.path.join(plot_dir, "-fig7_routing_energy.png"), dpi=300)
            plt.close()

        # --- FIG 9: TRADEOFF ---
        if df_e is not None and df_pd is not None:
            df_e_sum = df_e.sort_values("time").groupby(["link_policy", "vm_policy", "wf_policy", "host_id"]).last().reset_index()
            df_e_final = df_e_sum.groupby(["link_policy", "vm_policy", "wf_policy"])["energy"].sum().reset_index()
            df_p_final = df_pd.groupby(["link_policy", "vm_policy", "wf_policy"])["delay_ms"].mean().reset_index()
            df_trade = pd.merge(df_e_final, df_p_final, on=["link_policy", "vm_policy", "wf_policy"])
            plt.figure(figsize=(10, 6))
            sns.scatterplot(data=df_trade, x="delay_ms", y="energy", hue="link_policy", style="vm_policy", s=150)
            plt.title("Fig 9: Compromis Énergie / Latence", fontsize=14, fontweight='bold')
            plt.xlabel("Latence (ms)"); plt.ylabel("Énergie (Wh)")
            plt.savefig(os.path.join(plot_dir, "-fig9_tradeoff.png"), dpi=300)
            plt.close()

        # --- FIG 10: QUEUING ---
        if df_pd is not None:
            plt.figure(figsize=(10, 6))
            sns.boxplot(data=df_pd, x="vm_policy", y="queue_delay_ms", hue="link_policy", palette=palette_link, showfliers=False)
            plt.title("Fig 10: Distribution du Queuing Delay", fontsize=14, fontweight='bold')
            plt.savefig(os.path.join(plot_dir, "-fig10_queuing.png"), dpi=300)
            plt.close()

        # --- FIG 11: VM CPU IMPACT ---
        if df_util is not None:
            df_u = df_util.rename(columns={"cpu_util": "CPU", "cpu": "CPU"})
            plt.figure(figsize=(10, 6))
            sns.boxplot(data=df_u, x="vm_policy", y="CPU", palette=palette_vm)
            plt.title("Fig 11: Impact du Placement sur le CPU", fontsize=14, fontweight='bold')
            plt.savefig(os.path.join(plot_dir, "-fig11_vm_cpu_impact.png"), dpi=300)
            plt.close()

        # --- FIG 12: VM LATENCY IMPACT ---
        if df_pd is not None:
            plt.figure(figsize=(10, 6))
            sns.boxplot(data=df_pd, x="vm_policy", y="delay_ms", palette=palette_vm, showfliers=False)
            plt.title("Fig 12: Impact du Placement sur la Latence", fontsize=14, fontweight='bold')
            plt.savefig(os.path.join(plot_dir, "-fig12_vm_latency_impact.png"), dpi=300)
            plt.close()

        # --- FIG 13: WF LATENCY IMPACT ---
        if df_pd is not None:
            plt.figure(figsize=(10, 6))
            sns.boxplot(data=df_pd, x="wf_policy", y="delay_ms", palette="Set2", showfliers=False)
            plt.title("Fig 13: Impact de l'Ordonnancement sur la Latence", fontsize=14, fontweight='bold')
            plt.savefig(os.path.join(plot_dir, "-fig13_wf_latency_impact.png"), dpi=300)
            plt.close()

        # --- FIG 10B: LOAD BALANCING (UP LINKS) ---
        if df_lu is not None:
            plt.figure(figsize=(10, 6))
            sns.boxplot(data=df_lu, x="vm_policy", y="utilization", hue="link_policy", palette=palette_link, showfliers=False)
            plt.title("Fig 10b: Équilibrage de Charge Réseau", fontsize=14, fontweight='bold')
            plt.savefig(os.path.join(plot_dir, "-fig10_load_balancing.png"), dpi=300)
            plt.close()

    print("\n[SUCCÈS] Toutes les figures (1-13) ont été générées.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir")
    args = parser.parse_args()
    generate_plots(args.results_dir)
