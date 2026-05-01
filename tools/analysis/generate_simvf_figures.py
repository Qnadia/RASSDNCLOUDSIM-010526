"""
generate_simvf_figures.py
=========================
Génère les figures consolidées pour les résultats Sim VF (2026-05-14).
Structure attendue :
    <base_dir>/<dataset>/<VM_Policy>/<Link_Policy>/
        host_energy_total.csv
        packet_delays.csv
        qos_violations.csv
        host_utilization.csv
        detailed_energy.csv
        vm_utilization.csv

Sortie : <base_dir>/figures_consolidated/
    consolidated_summary.csv
    fig1_energy.png/pdf          — Énergie totale (barres groupées)
    fig2_latency.png/pdf         — Latence paquets moyenne (barres groupées)
    fig3_sla.png/pdf             — Violations SLA (barres groupées)
    fig4_packet_delay.png/pdf    — CDF délai paquets par dataset
    fig5_utilization.png/pdf     — Utilisation CPU/RAM/BW moyenne

Usage:
    python generate_simvf_figures.py [--base BASE_DIR] [--format pdf|png]
    python generate_simvf_figures.py
        # utilise E:\\Workspace\\v2\\cloudsimsdn-research\\results\\2026-05-14\\Sim VF
"""

import os
import sys
import argparse
import warnings
import pandas as pd
import numpy  as np
import matplotlib
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker

warnings.filterwarnings("ignore")

# ── Répertoire par défaut ──────────────────────────────────────────────────────
DEFAULT_BASE = r"E:\Workspace\v2\cloudsimsdn-research\results\2026-05-14\Sim VF"

# ── Style global IEEE ─────────────────────────────────────────────────────────
matplotlib.rcParams.update({
    "font.family":       "DejaVu Serif",   # fallback si Times New Roman absent
    "font.size":         12,
    "axes.labelsize":    13,
    "axes.titlesize":    14,
    "legend.fontsize":   10,
    "xtick.labelsize":   11,
    "ytick.labelsize":   11,
    "axes.linewidth":    1.2,
    "grid.linewidth":    0.6,
    "lines.linewidth":   1.8,
    "figure.dpi":        150,
    "savefig.dpi":       300,
    "savefig.bbox":      "tight",
    "savefig.pad_inches": 0.08,
})
try:
    matplotlib.rc("font", family="Times New Roman")
except Exception:
    pass

# ── Palettes ──────────────────────────────────────────────────────────────────
VM_COLORS = {
    "VM_MFF":  "#1f77b4",   # bleu
    "VM_LFF":  "#ff7f0e",   # orange
    "VM_LWFF": "#2ca02c",   # vert
}
LINK_HATCHES = {
    "Link_First":    "",
    "Link_BwAllocN": "//",
    "Link_Dijkstra": "xx",
}
DATASET_ORDER   = ["small", "medium", "large"]
VM_ORDER        = ["VM_MFF", "VM_LFF", "VM_LWFF"]
LINK_ORDER      = ["Link_First", "Link_BwAllocN", "Link_Dijkstra"]

# ── Helpers ───────────────────────────────────────────────────────────────────
def save_fig(fig, name, outdir, fmt="pdf"):
    os.makedirs(outdir, exist_ok=True)
    for ext in [fmt, "png"]:
        path = os.path.join(outdir, f"{name}.{ext}")
        fig.savefig(path, format=ext, dpi=300)
        print(f"  ✔  {path}")


def read_csv_safe(path, **kwargs):
    if not os.path.exists(path):
        return pd.DataFrame()
    try:
        return pd.read_csv(path, **kwargs)
    except Exception as e:
        print(f"  [WARN] Impossible de lire {path}: {e}")
        return pd.DataFrame()


# ── Extraction KPIs ───────────────────────────────────────────────────────────
def extract_kpis(base_dir):
    """
    Parcourt base_dir/<dataset>/<vm>/<link>/ et extrait les KPIs.
    Retourne un DataFrame avec une ligne par combinaison.
    """
    rows = []

    for dataset in DATASET_ORDER:
        for vm in VM_ORDER:
            for link in LINK_ORDER:
                exp_dir = os.path.join(base_dir, dataset, vm, link)
                if not os.path.isdir(exp_dir):
                    continue

                row = {"Dataset": dataset, "VM": vm, "Link": link, "Scenario": f"{vm}+{link}"}

                # ── Énergie totale (Wh) ──────────────────────────────────────
                energy_path = os.path.join(exp_dir, "host_energy_total.csv")
                df_e = read_csv_safe(energy_path, sep=";", comment="#",
                                     names=["ts", "hostName", "hostId", "energy"])
                if not df_e.empty:
                    # Garder uniquement les lignes TOTAL (dédoublonnées)
                    total_rows = df_e[df_e["hostName"] == "TOTAL"]
                    if not total_rows.empty:
                        row["TotalEnergy_Wh"] = pd.to_numeric(
                            total_rows["energy"], errors="coerce").dropna().iloc[-1]
                    else:
                        # Sinon sommer les hôtes (dernière occurrence)
                        host_rows = df_e[df_e["hostName"] != "TOTAL"].copy()
                        host_rows["energy"] = pd.to_numeric(host_rows["energy"], errors="coerce")
                        row["TotalEnergy_Wh"] = host_rows.groupby("hostName")["energy"].last().sum()
                else:
                    row["TotalEnergy_Wh"] = np.nan

                # ── Délai paquets (ms) ───────────────────────────────────────
                pkt_path = os.path.join(exp_dir, "packet_delays.csv")
                df_p = read_csv_safe(pkt_path, sep=";", comment="#",
                                     names=["pktId", "src", "dst", "size", "delay_ms"])
                if not df_p.empty:
                    df_p["delay_ms"] = pd.to_numeric(df_p["delay_ms"], errors="coerce")
                    row["AvgDelay_ms"]  = df_p["delay_ms"].mean()
                    row["MaxDelay_ms"]  = df_p["delay_ms"].max()
                    row["N_Packets"]    = len(df_p)
                else:
                    row["AvgDelay_ms"] = np.nan
                    row["MaxDelay_ms"] = np.nan
                    row["N_Packets"]   = 0

                # ── Violations SLA ───────────────────────────────────────────
                qos_path = os.path.join(exp_dir, "qos_violations.csv")
                df_q = read_csv_safe(qos_path, sep=";", comment="#",
                                     names=["ts", "flowId", "violationType"])
                row["SLA_Violations"] = 0 if df_q.empty else len(df_q)

                # ── Utilisation CPU/RAM/BW moyenne ────────────────────────────
                util_path = os.path.join(exp_dir, "host_utilization.csv")
                df_u = read_csv_safe(util_path, sep=";", comment="#",
                                     names=["time", "hostId", "cpu", "ram", "bw", "energy"])
                if not df_u.empty:
                    for col in ["cpu", "ram", "bw"]:
                        df_u[col] = pd.to_numeric(df_u[col], errors="coerce")
                    row["AvgCPU_pct"] = df_u["cpu"].mean()
                    row["AvgRAM_pct"] = df_u["ram"].mean()
                    row["AvgBW_pct"]  = df_u["bw"].mean()
                else:
                    row["AvgCPU_pct"] = np.nan
                    row["AvgRAM_pct"] = np.nan
                    row["AvgBW_pct"]  = np.nan

                rows.append(row)

    df = pd.DataFrame(rows)
    print(f"\n[OK] KPIs extraits : {len(df)} combinaisons")
    return df


# ── Figure 1 — Énergie totale (barres groupées par VM, X=Dataset) ─────────────
def fig1_energy(df, outdir, fmt="pdf"):
    print("\n[Fig 1] Énergie totale par politique VM")
    fig, ax = plt.subplots(figsize=(10, 5))

    datasets_present = [d for d in DATASET_ORDER if d in df["Dataset"].values]
    n_ds = len(datasets_present)
    n_vm = len(VM_ORDER)
    n_link = len(LINK_ORDER)
    n_groups = n_vm * n_link  # barres par dataset

    # Largeur et positions
    width   = 0.8 / n_groups
    x_base  = np.arange(n_ds)

    bar_idx = 0
    handles, labels = [], []
    for vi, vm in enumerate(VM_ORDER):
        for li, link in enumerate(LINK_ORDER):
            sub = df[(df["VM"] == vm) & (df["Link"] == link)].set_index("Dataset")
            vals = [sub.loc[d, "TotalEnergy_Wh"] if d in sub.index else np.nan
                    for d in datasets_present]
            x_pos = x_base - 0.4 + width * bar_idx + width / 2
            bars = ax.bar(x_pos, vals, width=width * 0.92,
                          color=VM_COLORS[vm], hatch=LINK_HATCHES[link],
                          alpha=0.85, edgecolor="white", linewidth=0.5)
            # Étiquette sur barre
            for b, v in zip(bars, vals):
                if not np.isnan(v) and v > 0:
                    ax.text(b.get_x() + b.get_width() / 2, b.get_height() + 0.3,
                            f"{v:.1f}", ha="center", va="bottom", fontsize=7, rotation=90)
            # Legend unique
            label = f"{vm.replace('VM_','')} + {link.replace('Link_','')}"
            if label not in labels:
                import matplotlib.patches as mpatches
                handles.append(mpatches.Patch(facecolor=VM_COLORS[vm],
                                              hatch=LINK_HATCHES[link],
                                              edgecolor="grey", label=label))
                labels.append(label)
            bar_idx += 1

    ax.set_xticks(x_base)
    ax.set_xticklabels([d.capitalize() for d in datasets_present], fontsize=12)
    ax.set_xlabel("Dataset")
    ax.set_ylabel("Total Energy Consumed (Wh)")
    ax.set_title("Fig. 1 — Total Energy Consumption by VM & Link Policy")
    ax.legend(handles=handles, labels=labels, ncol=3, fontsize=9,
              loc="upper left", bbox_to_anchor=(0, 1), framealpha=0.85)
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save_fig(fig, "fig1_energy", outdir, fmt)
    plt.close(fig)


# ── Figure 2 — Latence moyenne paquets (barres groupées) ─────────────────────
def fig2_latency(df, outdir, fmt="pdf"):
    print("\n[Fig 2] Latence moyenne paquets")
    fig, ax = plt.subplots(figsize=(10, 5))

    datasets_present = [d for d in DATASET_ORDER if d in df["Dataset"].values]
    n_ds = len(datasets_present)
    n_groups = len(VM_ORDER) * len(LINK_ORDER)
    width    = 0.8 / n_groups
    x_base   = np.arange(n_ds)

    bar_idx = 0
    handles, labels = [], []
    for vi, vm in enumerate(VM_ORDER):
        for li, link in enumerate(LINK_ORDER):
            sub  = df[(df["VM"] == vm) & (df["Link"] == link)].set_index("Dataset")
            vals = [sub.loc[d, "AvgDelay_ms"] if d in sub.index else np.nan
                    for d in datasets_present]
            x_pos = x_base - 0.4 + width * bar_idx + width / 2
            bars  = ax.bar(x_pos, vals, width=width * 0.92,
                           color=VM_COLORS[vm], hatch=LINK_HATCHES[link],
                           alpha=0.85, edgecolor="white", linewidth=0.5)
            label = f"{vm.replace('VM_','')} + {link.replace('Link_','')}"
            if label not in labels:
                import matplotlib.patches as mpatches
                handles.append(mpatches.Patch(facecolor=VM_COLORS[vm],
                                              hatch=LINK_HATCHES[link],
                                              edgecolor="grey", label=label))
                labels.append(label)
            bar_idx += 1

    ax.set_xticks(x_base)
    ax.set_xticklabels([d.capitalize() for d in datasets_present], fontsize=12)
    ax.set_xlabel("Dataset")
    ax.set_ylabel("Average Packet Delay (ms)")
    ax.set_title("Fig. 2 — Average End-to-End Packet Delay")
    ax.legend(handles=handles, labels=labels, ncol=3, fontsize=9,
              loc="upper right", framealpha=0.85)
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save_fig(fig, "fig2_latency", outdir, fmt)
    plt.close(fig)


# ── Figure 3 — Violations SLA ─────────────────────────────────────────────────
def fig3_sla(df, outdir, fmt="pdf"):
    print("\n[Fig 3] Violations SLA")
    fig, ax = plt.subplots(figsize=(10, 5))

    datasets_present = [d for d in DATASET_ORDER if d in df["Dataset"].values]
    n_ds    = len(datasets_present)
    n_groups = len(VM_ORDER) * len(LINK_ORDER)
    width   = 0.8 / n_groups
    x_base  = np.arange(n_ds)

    bar_idx = 0
    handles, labels = [], []
    for vi, vm in enumerate(VM_ORDER):
        for li, link in enumerate(LINK_ORDER):
            sub  = df[(df["VM"] == vm) & (df["Link"] == link)].set_index("Dataset")
            vals = [sub.loc[d, "SLA_Violations"] if d in sub.index else 0
                    for d in datasets_present]
            x_pos = x_base - 0.4 + width * bar_idx + width / 2
            ax.bar(x_pos, vals, width=width * 0.92,
                   color=VM_COLORS[vm], hatch=LINK_HATCHES[link],
                   alpha=0.85, edgecolor="white", linewidth=0.5)
            label = f"{vm.replace('VM_','')} + {link.replace('Link_','')}"
            if label not in labels:
                import matplotlib.patches as mpatches
                handles.append(mpatches.Patch(facecolor=VM_COLORS[vm],
                                              hatch=LINK_HATCHES[link],
                                              edgecolor="grey", label=label))
                labels.append(label)
            bar_idx += 1

    ax.set_xticks(x_base)
    ax.set_xticklabels([d.capitalize() for d in datasets_present], fontsize=12)
    ax.set_xlabel("Dataset")
    ax.set_ylabel("Number of SLA Violations")
    ax.set_title("Fig. 3 — SLA Violations by VM & Link Policy")
    ax.legend(handles=handles, labels=labels, ncol=3, fontsize=9,
              loc="upper right", framealpha=0.85)
    ax.grid(True, axis="y", linestyle="--", alpha=0.5)
    ax.set_axisbelow(True)
    fig.tight_layout()
    save_fig(fig, "fig3_sla", outdir, fmt)
    plt.close(fig)


# ── Figure 4 — CDF délai paquets (par dataset, toutes combinaisons) ───────────
def fig4_packet_delay_cdf(base_dir, outdir, fmt="pdf"):
    print("\n[Fig 4] CDF délai paquets par dataset")

    colors_line = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd",
                   "#8c564b", "#e377c2", "#7f7f7f", "#bcbd22"]

    for dataset in DATASET_ORDER:
        fig, ax = plt.subplots(figsize=(8, 5))
        ci = 0
        plotted = False
        for vm in VM_ORDER:
            for link in LINK_ORDER:
                exp_dir  = os.path.join(base_dir, dataset, vm, link)
                pkt_path = os.path.join(exp_dir, "packet_delays.csv")
                df_p = read_csv_safe(pkt_path, sep=";", comment="#",
                                     names=["pktId", "src", "dst", "size", "delay_ms"])
                if df_p.empty:
                    continue
                df_p["delay_ms"] = pd.to_numeric(df_p["delay_ms"], errors="coerce").dropna()
                if df_p["delay_ms"].empty:
                    continue
                sorted_d = np.sort(df_p["delay_ms"].values)
                cdf = np.arange(1, len(sorted_d) + 1) / len(sorted_d)
                label = f"{vm.replace('VM_','')} + {link.replace('Link_','')}"
                ax.plot(sorted_d, cdf, label=label,
                        color=colors_line[ci % len(colors_line)], lw=1.8)
                ci += 1
                plotted = True

        if not plotted:
            plt.close(fig)
            continue

        ax.set_xlabel("Packet Delay (ms)")
        ax.set_ylabel("CDF")
        ax.set_title(f"Fig. 4 — CDF of Packet Delays — Dataset {dataset.capitalize()}")
        ax.legend(fontsize=9, ncol=2, loc="lower right")
        ax.grid(True, linestyle="--", alpha=0.5)
        ax.set_ylim(0, 1.05)
        fig.tight_layout()
        save_fig(fig, f"fig4_packet_delay_{dataset}", outdir, fmt)
        plt.close(fig)


# ── Figure 5 — Utilisation CPU/RAM/BW moyenne (heatmap ou barres) ─────────────
def fig5_utilization(df, outdir, fmt="pdf"):
    print("\n[Fig 5] Utilisation CPU/RAM/BW")

    datasets_present = [d for d in DATASET_ORDER if d in df["Dataset"].values]
    metrics = {"AvgCPU_pct": "CPU (%)", "AvgRAM_pct": "RAM (%)", "AvgBW_pct": "BW (%)"}

    fig, axes = plt.subplots(1, len(metrics), figsize=(14, 5), sharey=False)

    n_groups = len(VM_ORDER) * len(LINK_ORDER)
    width    = 0.8 / n_groups
    x_base   = np.arange(len(datasets_present))

    for ax, (metric, ylabel) in zip(axes, metrics.items()):
        bar_idx = 0
        handles, labels = [], []
        for vi, vm in enumerate(VM_ORDER):
            for li, link in enumerate(LINK_ORDER):
                sub  = df[(df["VM"] == vm) & (df["Link"] == link)].set_index("Dataset")
                vals = [sub.loc[d, metric] if d in sub.index else np.nan
                        for d in datasets_present]
                x_pos = x_base - 0.4 + width * bar_idx + width / 2
                ax.bar(x_pos, vals, width=width * 0.92,
                       color=VM_COLORS[vm], hatch=LINK_HATCHES[link],
                       alpha=0.85, edgecolor="white", linewidth=0.5)
                label = f"{vm.replace('VM_','')} + {link.replace('Link_','')}"
                if label not in labels:
                    import matplotlib.patches as mpatches
                    handles.append(mpatches.Patch(facecolor=VM_COLORS[vm],
                                                  hatch=LINK_HATCHES[link],
                                                  edgecolor="grey", label=label))
                    labels.append(label)
                bar_idx += 1

        ax.set_xticks(x_base)
        ax.set_xticklabels([d.capitalize() for d in datasets_present], fontsize=10)
        ax.set_xlabel("Dataset")
        ax.set_ylabel(ylabel)
        ax.set_title(f"Avg {ylabel}")
        ax.grid(True, axis="y", linestyle="--", alpha=0.5)
        ax.set_axisbelow(True)

    # Légende globale sous la figure
    fig.suptitle("Fig. 5 — Average Host Resource Utilization", y=1.02, fontsize=13)
    axes[0].legend(handles=handles, labels=labels, ncol=3, fontsize=8,
                   loc="upper left", bbox_to_anchor=(0, -0.18))
    fig.tight_layout()
    save_fig(fig, "fig5_utilization", outdir, fmt)
    plt.close(fig)


# ── Figure 6 — Radar / Spider (optionnel) ────────────────────────────────────
def fig6_radar_comparison(df, outdir, fmt="pdf"):
    """Radar chart comparant les 3 VM policies sur les métriques clés."""
    print("\n[Fig 6] Radar chart comparaison VM policies")

    metrics = ["TotalEnergy_Wh", "AvgDelay_ms", "SLA_Violations", "AvgCPU_pct"]
    labels  = ["Energy (Wh)", "Avg Delay (ms)", "SLA Violations", "CPU Util (%)"]

    datasets_present = [d for d in DATASET_ORDER if d in df["Dataset"].values]

    for dataset in datasets_present:
        sub_ds = df[df["Dataset"] == dataset]
        # Agréger par VM policy (moyenne sur les link policies)
        vm_agg = sub_ds.groupby("VM")[metrics].mean()

        # Normaliser 0-1 pour le radar
        vm_norm = vm_agg.copy()
        for m in metrics:
            rng = vm_agg[m].max() - vm_agg[m].min()
            if rng > 0:
                vm_norm[m] = (vm_agg[m] - vm_agg[m].min()) / rng
            else:
                vm_norm[m] = 0.5

        N = len(metrics)
        angles = np.linspace(0, 2 * np.pi, N, endpoint=False).tolist()
        angles += angles[:1]

        fig, ax = plt.subplots(figsize=(6, 6),
                               subplot_kw=dict(polar=True))

        for vm in VM_ORDER:
            if vm not in vm_norm.index:
                continue
            values = vm_norm.loc[vm, metrics].tolist()
            values += values[:1]
            ax.plot(angles, values, "o-", lw=2, label=vm.replace("VM_", ""),
                    color=VM_COLORS[vm])
            ax.fill(angles, values, alpha=0.1, color=VM_COLORS[vm])

        ax.set_xticks(angles[:-1])
        ax.set_xticklabels(labels, fontsize=10)
        ax.set_ylim(0, 1)
        ax.set_title(f"VM Policy Comparison — Dataset {dataset.capitalize()}",
                     pad=20, fontsize=12)
        ax.legend(loc="upper right", bbox_to_anchor=(1.35, 1.15), fontsize=10)
        fig.tight_layout()
        save_fig(fig, f"fig6_radar_{dataset}", outdir, fmt)
        plt.close(fig)


# ── Export CSV consolidé ───────────────────────────────────────────────────────
def export_consolidated_csv(df, outdir):
    os.makedirs(outdir, exist_ok=True)
    path = os.path.join(outdir, "consolidated_summary.csv")
    df.to_csv(path, index=False, sep=";", float_format="%.4f")
    print(f"\n[OK] CSV consolide : {path}")


# ── Main ───────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(
        description="Génère les figures consolidées pour Sim VF (CloudSimSDN)")
    parser.add_argument("--base",   default=DEFAULT_BASE,
                        help="Répertoire racine Sim VF (défaut: %(default)s)")
    parser.add_argument("--outdir", default=None,
                        help="Répertoire de sortie (défaut: <base>/figures_consolidated)")
    parser.add_argument("--format", default="pdf",
                        choices=["pdf", "eps", "svg", "png"],
                        help="Format vectoriel de sortie (défaut: pdf)")
    args = parser.parse_args()

    base_dir = args.base.strip('"').strip("'")
    if not os.path.isdir(base_dir):
        print(f"[ERROR] Répertoire introuvable : {base_dir}")
        sys.exit(1)

    outdir = args.outdir if args.outdir else os.path.join(base_dir, "figures_consolidated")
    fmt    = args.format

    print(f"\n{'='*60}")
    print(f"  CloudSimSDN - Figures Consolidees Sim VF")
    print(f"  Source  : {base_dir}")
    print(f"  Sortie  : {outdir}")
    print(f"  Format  : {fmt} + PNG")
    print(f"{'='*60}")

    # 1. Extraction KPIs
    df = extract_kpis(base_dir)
    if df.empty:
        print("[ERROR] Aucune donnée trouvée. Vérifiez la structure des dossiers.")
        sys.exit(1)

    print("\n--- Apercu KPIs ---")
    pd.set_option("display.max_columns", 20)
    pd.set_option("display.width",       200)
    print(df[["Dataset", "VM", "Link", "TotalEnergy_Wh",
              "AvgDelay_ms", "SLA_Violations", "AvgCPU_pct"]].to_string(index=False))

    # 2. Sauvegarde CSV
    export_consolidated_csv(df, outdir)

    # 3. Figures
    fig1_energy(df,  outdir, fmt)
    fig2_latency(df, outdir, fmt)
    fig3_sla(df,     outdir, fmt)
    fig4_packet_delay_cdf(base_dir, outdir, fmt)
    fig5_utilization(df, outdir, fmt)
    fig6_radar_comparison(df, outdir, fmt)

    print(f"\n[DONE] Toutes les figures generees dans : {outdir}/")


if __name__ == "__main__":
    main()
