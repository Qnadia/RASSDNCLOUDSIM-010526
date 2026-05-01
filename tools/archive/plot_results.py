"""
Script de visualisation des résultats CloudSimSDN
Publication-ready : format vectoriel (PDF/EPS) + style IEEE

Usage:
    python plot_results.py --expdir experiment1/
"""

import os
import argparse
import pandas as pd
import matplotlib
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np

# ── Style global IEEE ─────────────────────────────────────────────────────────
matplotlib.rcParams.update({
    "font.family":      "Times New Roman",  # Police IEEE standard
    "font.size":        12,
    "axes.labelsize":   13,
    "axes.titlesize":   13,
    "legend.fontsize":  11,
    "xtick.labelsize":  11,
    "ytick.labelsize":  11,
    "axes.linewidth":   1.2,
    "grid.linewidth":   0.6,
    "lines.linewidth":  1.8,
    "figure.dpi":       150,           # Prévisualisation écran
    "savefig.dpi":      600,           # Export raster (si PNG demandé)
    "savefig.bbox":     "tight",
    "savefig.pad_inches": 0.05,
})

COLORS  = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd"]
MARKERS = ["o", "s", "^", "D", "x"]

def save_fig(fig, name, outdir, fmt="pdf"):
    """Sauvegarde en PDF/EPS/SVG (vectoriel) ET PNG 600 DPI."""
    os.makedirs(outdir, exist_ok=True)
    path_vec = os.path.join(outdir, f"{name}.{fmt}")
    path_png = os.path.join(outdir, f"{name}.png")
    fig.savefig(path_vec, format=fmt)   # vectoriel pour l'article
    fig.savefig(path_png, dpi=600)      # PNG haute résolution (backup)
    print(f"  OK  {path_vec}  +  {path_png}")


# ─────────────────────────────────────────────────────────────────────────────
# Figure 1 — Énergie par host dans le temps
# ─────────────────────────────────────────────────────────────────────────────
def plot_energy_per_host(expdir, outdir, fmt="pdf"):
    path = os.path.join(expdir, "detailed_energy.csv")
    if not os.path.exists(path):
        print(f"[SKIP] {path} introuvable")
        return

    df = pd.read_csv(path, sep=";", comment="#",
                     names=["time", "hostId", "cpu", "ram", "bw", "energy"])
    df = df.dropna()

    fig, ax = plt.subplots(figsize=(7, 4))
    for i, (hid, grp) in enumerate(df.groupby("hostId")):
        # Agréger par timestamp (au cas où plusieurs lignes par host/temps)
        g = grp.groupby("time")["energy"].sum().reset_index()
        ax.plot(g["time"], g["energy"], label=f"Host {int(hid)}",
                color=COLORS[i % len(COLORS)], marker=MARKERS[i % len(MARKERS)],
                markevery=max(1, len(g)//10), ms=5)

    ax.set_xlabel("Simulation time (s)")
    ax.set_ylabel("Energy consumed (Wh)")
    ax.set_title("Energy Consumption per Host Over Time")
    ax.legend(ncol=2, framealpha=0.8)
    ax.grid(True, linestyle="--", alpha=0.5)
    ax.yaxis.set_major_formatter(ticker.FormatStrFormatter("%.3f"))

    save_fig(fig, "fig1_energy_per_host", outdir, fmt)
    plt.close(fig)


# ─────────────────────────────────────────────────────────────────────────────
# Figure 2 — Utilisation CPU/RAM/BW par host
# ─────────────────────────────────────────────────────────────────────────────
def plot_host_utilization(expdir, outdir, fmt="pdf"):
    path = os.path.join(expdir, "host_utilization.csv")
    if not os.path.exists(path):
        print(f"[SKIP] {path} introuvable")
        return

    df = pd.read_csv(path, sep=";", comment="#",
                     names=["time", "hostId", "cpu", "ram", "bw"])
    df = df.dropna()

    metrics = {"CPU (%)": "cpu", "RAM (%)": "ram", "BW (%)": "bw"}
    fig, axes = plt.subplots(1, 3, figsize=(13, 4), sharey=False)

    for ax, (label, col) in zip(axes, metrics.items()):
        for i, (hid, grp) in enumerate(df.groupby("hostId")):
            g = grp.groupby("time")[col].mean().reset_index()
            ax.plot(g["time"], g[col], label=f"Host {int(hid)}",
                    color=COLORS[i % len(COLORS)])
        ax.set_title(label)
        ax.set_xlabel("Time (s)")
        ax.set_ylabel(label)
        ax.grid(True, linestyle="--", alpha=0.5)
        ax.legend(ncol=2, fontsize=9)

    fig.suptitle("Host Resource Utilization Over Time", fontsize=13, y=1.02)
    fig.tight_layout()
    save_fig(fig, "fig2_host_utilization", outdir, fmt)
    plt.close(fig)


# ─────────────────────────────────────────────────────────────────────────────
# Figure 3 — Décomposition de Le2e (barres empilées par priorité)
# ─────────────────────────────────────────────────────────────────────────────
def plot_latency_decomposition(latency_data, outdir, fmt="pdf"):
    """
    latency_data : list of dict avec clés
        priority, Dprop, Dtrans, Dproc_VM, Dproc_sw, Dqueue
    (extrait des logs console ou d'un CSV dédié à créer)
    """
    if not latency_data:
        # Données de démonstration si pas encore disponibles
        latency_data = [
            {"priority": 1, "Dprop": 0.0001, "Dtrans": 0.0002, "Dproc_VM": 0.002, "Dproc_sw": 0.00005, "Dqueue": 0.0003},
            {"priority": 2, "Dprop": 0.0001, "Dtrans": 0.0002, "Dproc_VM": 0.002, "Dproc_sw": 0.00005, "Dqueue": 0.0002},
            {"priority": 3, "Dprop": 0.0001, "Dtrans": 0.0002, "Dproc_VM": 0.002, "Dproc_sw": 0.00005, "Dqueue": 0.0001},
        ]
        print("[INFO] Utilisation de données de démo pour la décomposition Le2e")

    df = pd.DataFrame(latency_data)
    components = ["Dprop", "Dtrans", "Dproc_VM", "Dproc_sw", "Dqueue"]
    labels     = ["D$_{prop}$", "D$_{trans}$", "D$_{proc,VM}$", "D$_{proc,sw}$", "D$_{queue}$"]

    fig, ax = plt.subplots(figsize=(6, 4))
    x = np.arange(len(df))
    bottom = np.zeros(len(df))

    bar_colors = ["#4e79a7", "#f28e2b", "#59a14f", "#e15759", "#76b7b2"]
    for comp, label, color in zip(components, labels, bar_colors):
        vals = df[comp].values * 1000  # → ms
        ax.bar(x, vals, bottom=bottom * 1000, label=label, color=color, alpha=0.9, edgecolor="white")
        bottom += df[comp].values

    # Total Le2e
    totals = df[components].sum(axis=1) * 1000
    for xi, tot in zip(x, totals):
        ax.text(xi, tot + 0.01, f"{tot:.2f}", ha="center", va="bottom", fontsize=9)

    ax.set_xticks(x)
    ax.set_xticklabels([f"Priority {p}" for p in df["priority"]])
    ax.set_ylabel("Latency (ms)")
    ax.set_title("End-to-End Latency Decomposition by Priority Level")
    ax.legend(loc="upper right", ncol=1)
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)

    save_fig(fig, "fig3_latency_decomposition", outdir, fmt)
    plt.close(fig)


# ─────────────────────────────────────────────────────────────────────────────
# Figure 4 — CDF de Le2e par priorité
# ─────────────────────────────────────────────────────────────────────────────
def plot_latency_cdf(kpi_logs_df, outdir, fmt="pdf"):
    """
    kpi_logs_df : DataFrame avec colonnes [priority, totalLatency]
    Extraire depuis les logs console (ligne 📊 [KPI] ...)
    """
    if kpi_logs_df is None or kpi_logs_df.empty:
        print("[SKIP] Données KPI non disponibles — exécutez la simulation d'abord")
        return

    fig, ax = plt.subplots(figsize=(6, 4))
    for i, (prio, grp) in enumerate(kpi_logs_df.groupby("priority")):
        sorted_latency = np.sort(grp["totalLatency"].values) * 1000  # ms
        cdf = np.arange(1, len(sorted_latency) + 1) / len(sorted_latency)
        ax.plot(sorted_latency, cdf, label=f"Priority {int(prio)}",
                color=COLORS[i % len(COLORS)], linewidth=2)

    ax.set_xlabel("End-to-End Latency $L_{e2e}$ (ms)")
    ax.set_ylabel("CDF")
    ax.set_title("CDF of End-to-End Latency by Workload Priority")
    ax.legend()
    ax.grid(True, linestyle="--", alpha=0.5)
    ax.set_ylim(0, 1.05)

    save_fig(fig, "fig4_latency_cdf", outdir, fmt)
    plt.close(fig)


# ─────────────────────────────────────────────────────────────────────────────
# Figure 5 — Énergie totale par host (barres)
# ─────────────────────────────────────────────────────────────────────────────
def plot_total_energy(expdir, outdir, fmt="pdf"):
    path = os.path.join(expdir, "host_energy_total.csv")
    if not os.path.exists(path):
        print(f"[SKIP] {path} introuvable")
        return

    df = pd.read_csv(path, sep=";", comment="#",
                     names=["timestamp", "hostName", "hostId", "energy"])
    df = df[df["hostName"] != "TOTAL"].dropna()

    fig, ax = plt.subplots(figsize=(6, 4))
    x = np.arange(len(df))
    bars = ax.bar(x, df["energy"].astype(float),
                  color=COLORS[:len(df)], edgecolor="white", linewidth=0.8)

    for bar, val in zip(bars, df["energy"].astype(float)):
        ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.001,
                f"{val:.3f}", ha="center", va="bottom", fontsize=10)

    ax.set_xticks(x)
    ax.set_xticklabels(df["hostName"], rotation=25, ha="right")
    ax.set_ylabel("Total Energy (Wh)")
    ax.set_title("Total Energy Consumed per Host")
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)

    save_fig(fig, "fig5_total_energy_per_host", outdir, fmt)
    plt.close(fig)


# ─────────────────────────────────────────────────────────────────────────────
# Figure 6 — Utilisation liens (up + down)
# ─────────────────────────────────────────────────────────────────────────────
def plot_link_utilization(expdir, outdir, fmt="pdf"):
    for direction, fname in [("Uplink", "link_utilization_up.csv"),
                              ("Downlink", "link_utilization_down.csv")]:
        path = os.path.join(expdir, fname)
        if not os.path.exists(path):
            continue
        df = pd.read_csv(path, comment="#",
                         names=["time", "linkId", "bw_pct", "latency"])
        df = df.dropna()

        fig, ax = plt.subplots(figsize=(7, 4))
        for i, (lid, grp) in enumerate(df.groupby("linkId")):
            g = grp.groupby("time")["bw_pct"].mean().reset_index()
            ax.plot(g["time"], g["bw_pct"], label=lid,
                    color=COLORS[i % len(COLORS)], lw=1.5)

        ax.set_xlabel("Time (s)")
        ax.set_ylabel("BW Utilization (%)")
        ax.set_title(f"{direction} Bandwidth Utilization per Link")
        ax.legend(fontsize=8, ncol=2)
        ax.grid(True, linestyle="--", alpha=0.5)

        save_fig(fig, f"fig6_{direction.lower()}_utilization", outdir, fmt)
        plt.close(fig)


# ─────────────────────────────────────────────────────────────────────────────
# Figure 7 — Décomposition réelle des délais paquets
# ─────────────────────────────────────────────────────────────────────────────
def plot_packet_delay_breakdown_real(expdir, outdir, fmt="pdf"):
    path = os.path.join(expdir, "packet_delays.csv")
    if not os.path.exists(path):
        print(f"[SKIP] {path} introuvable")
        return

    df = pd.read_csv(path, sep=";", comment="#",
                     names=["packetId", "src", "dst", "psize", "delay", "proc", "prop", "trans", "queue"])
    df = df.dropna()

    if df.empty:
        print(f"[SKIP] {path} est vide")
        return

    avg = df[["proc", "prop", "trans", "queue"]].mean()
    
    fig, ax = plt.subplots(figsize=(7, 5))
    bars = ax.bar(avg.index, avg.values, color=COLORS[:4], edgecolor="black", alpha=0.8)
    
    for bar, val in zip(bars, avg.values):
        ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.05,
                f"{val:.2f}ms", ha="center", va="bottom", fontsize=10, fontweight="bold")

    ax.set_ylabel("Latency (ms)")
    ax.set_title("Packet Latency Breakdown (Averaged over all packets)")
    ax.set_xticklabels(["Processing", "Propagation", "Transmission", "Queuing"])
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)

    save_fig(fig, "fig7_latency_breakdown_real", outdir, fmt)
    plt.close(fig)


# ─────────────────────────────────────────────────────────────────────────────
# Figure 7 — Comparaison consolidée (Campagne complète)
# ─────────────────────────────────────────────────────────────────────────────
def plot_campaign_comparison(csv_path, outdir, fmt="pdf"):
    """Génère des graphiques comparatifs à partir du CSV de campagne."""
    if not os.path.exists(csv_path):
        print(f"[SKIP] {csv_path} introuvable")
        return

    df = pd.read_csv(csv_path, sep=";")
    os.makedirs(outdir, exist_ok=True)
    
    datasets = df['Dataset'].unique()
    for ds in datasets:
        ds_df = df[df['Dataset'] == ds].copy()
        
        # Figure Énergie
        fig_e, ax_e = plt.subplots(figsize=(8, 5))
        pivot_energy = ds_df.pivot(index='VM', columns='Link', values='TotalEnergy_Wh')
        pivot_energy.plot(kind='bar', ax=ax_e, width=0.8, edgecolor='black', alpha=0.8)
        ax_e.set_ylabel("Total Energy (Wh)")
        ax_e.set_title(f"Energy Consumption Comparison - Dataset {ds.upper()}")
        ax_e.legend(title="Link Policy", bbox_to_anchor=(1.05, 1), loc='upper left')
        ax_e.grid(axis='y', linestyle='--', alpha=0.5)
        plt.tight_layout()
        save_fig(fig_e, f"comparison_energy_{ds}", outdir, fmt)
        plt.close(fig_e)

        # Figure Latence
        fig_l, ax_l = plt.subplots(figsize=(8, 5))
        pivot_lat = ds_df.pivot(index='VM', columns='Link', values='AvgLatency_ms')
        pivot_lat.plot(kind='bar', ax=ax_l, width=0.8, edgecolor='black', alpha=0.8)
        ax_l.set_ylabel("Avg Latency (ms)")
        ax_l.set_title(f"End-to-End Latency Comparison - Dataset {ds.upper()}")
        ax_l.legend(title="Link Policy", bbox_to_anchor=(1.05, 1), loc='upper left')
        ax_l.grid(axis='y', linestyle='--', alpha=0.5)
        plt.tight_layout()
        save_fig(fig_l, f"comparison_latency_{ds}", outdir, fmt)
        plt.close(fig_l)

    print(f"  OK  Comparaisons générées pour les datasets : {', '.join(datasets)}")
def main():
    parser = argparse.ArgumentParser(description="CloudSimSDN — Figures scientifiques")
    parser.add_argument("--expdir",  default="experiment1",  help="Dossier des résultats pour une simulation unique")
    parser.add_argument("--consolidated", default=None,      help="Chemin vers comparison_data.csv pour mode campagne")
    parser.add_argument("--outdir",  default="figures",       help="Dossier de sortie des figures")
    parser.add_argument("--format",  default="pdf",
                        choices=["pdf", "eps", "svg", "png"],
                        help="Format de sortie vectoriel (défaut: pdf)")
    args = parser.parse_args()

    if args.consolidated:
        print(f"\nMode consolidé : analyse de la campagne via {args.consolidated}")
        plot_campaign_comparison(args.consolidated, args.outdir, args.format)
    else:
        print(f"\nMode unique : analyse du dossier {args.expdir}")
        plot_energy_per_host(args.expdir, args.outdir, args.format)
        plot_host_utilization(args.expdir, args.outdir, args.format)
        plot_latency_decomposition([], args.outdir, args.format)   # démo
        # plot_latency_cdf(kpi_df, args.outdir, args.format)       # après parsing logs
        plot_total_energy(args.expdir, args.outdir, args.format)
        plot_link_utilization(args.expdir, args.outdir, args.format)
        plot_packet_delay_breakdown_real(args.expdir, args.outdir, args.format)

    print(f"\nFigures sauvegardées dans : {args.outdir}/")


if __name__ == "__main__":
    main()
