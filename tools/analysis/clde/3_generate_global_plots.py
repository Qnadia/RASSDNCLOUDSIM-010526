"""
3_generate_global_plots.py  — RAS-SDNCloudSim v2
Génère 15 graphiques scientifiques par dataset.
Améliorations vs v1:
  - 15 figures au lieu de 10 (ajout CDF queuing, heatmap delay/énergie,
    boxplot distribution, BW utilisation par lien, scatter Pareto)
  - Annotations automatiques (valeurs sur les barres, gains % BLA vs First)
  - Palette cohérente et accessible (daltonien-safe)
  - Gestion propre des datasets incomplets (skip si CSV absent)
  - Sauvegarde en PNG + SVG pour publication
  - Compatible avec large dataset (1000 requêtes)
"""

import os
import argparse
import warnings
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import seaborn as sns

warnings.filterwarnings("ignore")

# ──────────────────────────────────────────────
# STYLE
# ──────────────────────────────────────────────
sns.set_theme(style="whitegrid")
plt.rcParams.update({
    "font.family": "serif",
    "axes.edgecolor": "black",
    "axes.linewidth": 1.5,
    "grid.color": "#ddd",
    "grid.linestyle": "--",
    "grid.alpha": 0.7,
    "legend.frameon": True,
    "legend.fancybox": True,
    "figure.autolayout": True,
    "figure.dpi": 150,
})

PALETTE_LINK = {"First": "#d62728", "BLA": "#1f77b4"}
PALETTE_VM   = {"LFF": "#ff7f0e", "MFF": "#1f77b4", "LWFF": "#2ca02c"}
PALETTE_WF   = {"Priority": "#9467bd", "SJF": "#8c564b", "PSO": "#e377c2"}
VM_ORDER     = ["MFF", "LWFF", "LFF"]


# ──────────────────────────────────────────────
# HELPERS
# ──────────────────────────────────────────────
def save(fig, path_noext, close=True):
    for ext in ("png", "svg"):
        fig.savefig(f"{path_noext}.{ext}", bbox_inches="tight", dpi=200)
    if close:
        plt.close(fig)


def annotate_bars(ax, fmt="{:.0f}", fontsize=8):
    """Write value labels on top of each bar."""
    for patch in ax.patches:
        h = patch.get_height()
        if np.isnan(h) or h == 0:
            continue
        ax.annotate(
            fmt.format(h),
            (patch.get_x() + patch.get_width() / 2, h),
            ha="center", va="bottom", fontsize=fontsize, fontweight="bold"
        )


def bla_gain_text(first_val, bla_val):
    if first_val == 0:
        return ""
    gain = (first_val - bla_val) / first_val * 100
    sign = "−" if gain >= 0 else "+"
    return f"{sign}{abs(gain):.1f}%"


def load(synthese_dir, fname):
    path = os.path.join(synthese_dir, fname)
    if not os.path.exists(path):
        return None
    try:
        df = pd.read_csv(path, sep=";")
        df.columns = [c.strip().lower().replace(" ", "_") for c in df.columns]
        # Normalize policy names
        for col in ["link_policy", "vm_policy", "wf_policy"]:
            if col in df.columns:
                df[col] = df[col].replace({"DynLatBw": "BLA", "dynlatbw": "BLA"})
        return df
    except Exception as e:
        print(f"  [WARN] Cannot load {fname}: {e}")
        return None


# ──────────────────────────────────────────────
# FIGURE GENERATORS
# ──────────────────────────────────────────────

def fig_energy_by_vm(df_e, plot_dir, ds_name):
    if df_e is None or "energy" not in df_e.columns:
        return
    df_last = (df_e.sort_values("time")
               .groupby(["link_policy","vm_policy","wf_policy","host_id"])
               .last().reset_index())
    df_sum = (df_last.groupby(["link_policy","vm_policy"])["energy"]
              .sum().reset_index())
    df_sum["vm_policy"] = pd.Categorical(df_sum["vm_policy"], VM_ORDER)
    df_sum = df_sum.sort_values("vm_policy")

    fig, ax = plt.subplots(figsize=(10, 6))
    sns.barplot(data=df_sum, x="vm_policy", y="energy",
                hue="link_policy", palette=PALETTE_LINK, ax=ax,
                order=VM_ORDER, edgecolor="black", alpha=0.88)
    annotate_bars(ax, fmt="{:.0f} Wh", fontsize=7)
    ax.set_title(f"Fig 1: Total Energy by VM Allocation Policy — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_xlabel("VM Allocation Policy"); ax.set_ylabel("Total Energy (Wh)")
    save(fig, os.path.join(plot_dir, "fig01_energy_by_vm"))


def fig_delay_by_vm(df_pd, plot_dir, ds_name):
    if df_pd is None or "delay_ms" not in df_pd.columns:
        return
    fig, ax = plt.subplots(figsize=(10, 6))
    sns.barplot(data=df_pd, x="vm_policy", y="delay_ms",
                hue="link_policy", palette=PALETTE_LINK, ax=ax,
                order=VM_ORDER, edgecolor="black", alpha=0.88,
                estimator=np.mean, errorbar=None)
    annotate_bars(ax, fmt="{:.0f}", fontsize=7)
    ax.set_title(f"Fig 2: Average Packet Delay by VM Policy — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_xlabel("VM Allocation Policy"); ax.set_ylabel("Avg Packet Delay (ms)")
    save(fig, os.path.join(plot_dir, "fig02_delay_by_vm"))


def fig_cdf_delay(df_pd, plot_dir, ds_name):
    if df_pd is None:
        return
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))
    for ax, col, label in zip(axes,
                              ["delay_ms", "queue_delay_ms"],
                              ["Total Packet Delay", "Queuing Delay"]):
        if col not in df_pd.columns:
            continue
        for pol, grp in df_pd.groupby("link_policy"):
            vals = grp[col].dropna().sort_values()
            cdf  = np.arange(len(vals)) / len(vals)
            ax.plot(vals.values, cdf, label=pol, lw=2.5,
                    color=PALETTE_LINK.get(pol, "grey"))
        ax.set_title(f"CDF — {label}", fontsize=11, fontweight="bold")
        ax.set_xlabel(f"{label} (ms)"); ax.set_ylabel("P(X ≤ x)")
        ax.legend(); ax.grid(True, alpha=0.3)
    fig.suptitle(f"Fig 3: Delay CDF — {ds_name}", fontsize=13, fontweight="bold")
    save(fig, os.path.join(plot_dir, "fig03_cdf_delay"))


def fig_delay_components(df_pd, plot_dir, ds_name):
    if df_pd is None:
        return
    cols = ["proc_delay_ms","prop_delay_ms","trans_delay_ms","queue_delay_ms"]
    existing = [c for c in cols if c in df_pd.columns]
    if not existing:
        return
    df_comp = (df_pd.groupby("link_policy")[existing]
               .mean().reset_index()
               .melt(id_vars="link_policy", var_name="Component", value_name="ms"))
    df_comp["Component"] = df_comp["Component"].str.replace("_delay_ms","").str.replace("_"," ").str.capitalize()

    fig, ax = plt.subplots(figsize=(11, 6))
    sns.barplot(data=df_comp, x="Component", y="ms",
                hue="link_policy", palette=PALETTE_LINK, ax=ax,
                edgecolor="black", alpha=0.88)
    annotate_bars(ax, fmt="{:.0f}", fontsize=8)
    ax.set_title(f"Fig 4: Average Delay by Component — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_xlabel("Delay Component"); ax.set_ylabel("Avg Delay (ms)")
    save(fig, os.path.join(plot_dir, "fig04_delay_components"))


def fig_boxplot_delay(df_pd, plot_dir, ds_name):
    if df_pd is None or "delay_ms" not in df_pd.columns:
        return
    fig, ax = plt.subplots(figsize=(10, 6))
    df_box = df_pd[df_pd["link_policy"].isin(["BLA","First"])].copy()
    sns.boxplot(data=df_box, x="vm_policy", y="delay_ms",
                hue="link_policy", palette=PALETTE_LINK, ax=ax,
                order=VM_ORDER, fliersize=2, linewidth=1.5)
    ax.set_title(f"Fig 5: Delay Distribution (Boxplot) — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_xlabel("VM Policy"); ax.set_ylabel("Packet Delay (ms)")
    save(fig, os.path.join(plot_dir, "fig05_boxplot_delay"))


def fig_host_utilization(df_util, plot_dir, ds_name):
    if df_util is None:
        return
    rename = {"cpu_util": "CPU", "ram_util": "RAM", "bw_util": "BW"}
    df_u = df_util.rename(columns=rename)
    metrics = [m for m in ["CPU","RAM","BW"] if m in df_u.columns]
    if not metrics:
        return
    df_melt = df_u.melt(id_vars=["link_policy","vm_policy"],
                         value_vars=metrics, var_name="Resource", value_name="Usage")
    df_melt["Usage"] = pd.to_numeric(df_melt["Usage"], errors="coerce")

    fig, ax = plt.subplots(figsize=(11, 6))
    sns.barplot(data=df_melt, x="vm_policy", y="Usage",
                hue="link_policy", palette=PALETTE_LINK, ax=ax,
                order=VM_ORDER, edgecolor="black", alpha=0.88,
                estimator=np.mean, errorbar=None)
    ax.set_title(f"Fig 6: Avg Host Utilization (CPU/RAM/BW) — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_xlabel("VM Policy"); ax.set_ylabel("Avg Utilization (%)")
    save(fig, os.path.join(plot_dir, "fig06_host_utilization"))


def fig_energy_vs_delay_scatter(df_e, df_pd, plot_dir, ds_name):
    if df_e is None or df_pd is None:
        return
    df_e_agg = (df_e.groupby(["link_policy","vm_policy"])["energy"].sum().reset_index())
    df_d_agg = (df_pd.groupby(["link_policy","vm_policy"])["delay_ms"].mean().reset_index())
    df_merge = df_e_agg.merge(df_d_agg, on=["link_policy","vm_policy"])

    fig, ax = plt.subplots(figsize=(10, 7))
    for _, row in df_merge.iterrows():
        color = PALETTE_LINK.get(row["link_policy"], "grey")
        marker = {"MFF":"o","LWFF":"s","LFF":"D"}.get(row["vm_policy"], "^")
        ax.scatter(row["energy"], row["delay_ms"],
                   c=color, marker=marker, s=180, edgecolors="black", zorder=5)
        ax.annotate(f"{row['vm_policy']}\n{row['link_policy']}",
                    (row["energy"], row["delay_ms"]),
                    textcoords="offset points", xytext=(6, 4), fontsize=8)

    # Legend
    link_patches = [mpatches.Patch(color=c, label=l) for l, c in PALETTE_LINK.items()]
    vm_markers = [plt.Line2D([0],[0], marker=m, color="w",
                              markerfacecolor="grey", markersize=10, label=v)
                  for v, m in [("MFF","o"),("LWFF","s"),("LFF","D")]]
    ax.legend(handles=link_patches + vm_markers, fontsize=9, framealpha=0.9)
    ax.set_xlabel("Total Energy (Wh)"); ax.set_ylabel("Avg Packet Delay (ms)")
    ax.set_title(f"Fig 7: Pareto — Energy vs Delay — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.grid(True, alpha=0.4)
    save(fig, os.path.join(plot_dir, "fig07_pareto_energy_delay"))


def fig_queue_delay_by_vm(df_pd, plot_dir, ds_name):
    if df_pd is None or "queue_delay_ms" not in df_pd.columns:
        return
    fig, ax = plt.subplots(figsize=(10, 6))
    sns.barplot(data=df_pd, x="vm_policy", y="queue_delay_ms",
                hue="link_policy", palette=PALETTE_LINK, ax=ax,
                order=VM_ORDER, edgecolor="black", alpha=0.88,
                estimator=np.mean, errorbar=None)
    annotate_bars(ax, fmt="{:.0f}", fontsize=7)
    ax.set_title(f"Fig 8: Avg Queuing Delay (Congestion Impact) — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_xlabel("VM Policy"); ax.set_ylabel("Avg Queuing Delay (ms)")
    save(fig, os.path.join(plot_dir, "fig08_queuing_delay"))


def fig_bla_gain_summary(df_pd, df_e, plot_dir, ds_name):
    """Bar chart showing % gain of BLA over First for each VM policy."""
    if df_pd is None:
        return
    records = []
    for vm in VM_ORDER:
        sub = df_pd[df_pd["vm_policy"] == vm]
        for metric, col in [("Delay", "delay_ms"),
                              ("Queue", "queue_delay_ms"),
                              ("Trans", "trans_delay_ms")]:
            if col not in sub.columns:
                continue
            f_val = sub[sub["link_policy"]=="First"][col].mean()
            b_val = sub[sub["link_policy"]=="BLA"][col].mean()
            if f_val > 0:
                gain = (f_val - b_val) / f_val * 100
                records.append({"VM Policy": vm, "Metric": metric, "BLA Gain (%)": gain})

    if not records:
        return
    df_gain = pd.DataFrame(records)
    fig, ax = plt.subplots(figsize=(11, 6))
    sns.barplot(data=df_gain, x="VM Policy", y="BLA Gain (%)",
                hue="Metric", ax=ax, edgecolor="black", alpha=0.88)
    ax.axhline(0, color="black", linewidth=1)
    ax.set_title(f"Fig 9: BLA % Gain vs First — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_ylabel("Reduction (%): positive = BLA better")
    save(fig, os.path.join(plot_dir, "fig09_bla_gain_summary"))


def fig_wf_policy_impact(df_pd, plot_dir, ds_name):
    if df_pd is None or "wf_policy" not in df_pd.columns:
        return
    fig, axes = plt.subplots(1, 2, figsize=(14, 6))
    for ax, col, label in zip(axes,
                               ["delay_ms","queue_delay_ms"],
                               ["Packet Delay","Queuing Delay"]):
        if col not in df_pd.columns:
            continue
        sns.barplot(data=df_pd, x="wf_policy", y=col,
                    hue="link_policy", palette=PALETTE_LINK, ax=ax,
                    edgecolor="black", alpha=0.88,
                    estimator=np.mean, errorbar=None)
        ax.set_title(label, fontsize=11, fontweight="bold")
        ax.set_xlabel("Workload Scheduling Policy")
        ax.set_ylabel(f"Avg {label} (ms)")
    fig.suptitle(f"Fig 10: Workload Scheduling Policy Impact — {ds_name}",
                 fontsize=13, fontweight="bold")
    save(fig, os.path.join(plot_dir, "fig10_wf_impact"))


def fig_link_utilization(df_lu, plot_dir, ds_name):
    if df_lu is None or "utilization" not in df_lu.columns:
        return
    # Average utilization per link_id grouped by link_policy
    if "link_id" not in df_lu.columns:
        return
    df_avg = (df_lu.groupby(["link_policy","link_id"])["utilization"]
              .mean().reset_index())
    fig, ax = plt.subplots(figsize=(max(12, len(df_avg["link_id"].unique())//2), 6))
    sns.boxplot(data=df_avg, x="link_policy", y="utilization",
                palette=PALETTE_LINK, ax=ax, width=0.5)
    ax.set_title(f"Fig 11: Link Utilization Distribution — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_xlabel("Link Selection Policy"); ax.set_ylabel("Link Utilization (%)")
    save(fig, os.path.join(plot_dir, "fig11_link_utilization"))


def fig_energy_timeseries(df_e, plot_dir, ds_name):
    if df_e is None or "time" not in df_e.columns:
        return
    df_ts = (df_e.groupby(["time","link_policy"])["energy"]
             .sum().reset_index())
    fig, ax = plt.subplots(figsize=(12, 6))
    for pol, grp in df_ts.groupby("link_policy"):
        ax.plot(grp["time"], grp["energy"], label=pol,
                color=PALETTE_LINK.get(pol, "grey"), lw=2)
    ax.set_title(f"Fig 12: Cumulative Energy over Time — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_xlabel("Simulation Time (s)"); ax.set_ylabel("Total Energy (Wh)")
    ax.legend(); ax.grid(True, alpha=0.3)
    save(fig, os.path.join(plot_dir, "fig12_energy_timeseries"))


def fig_heatmap_delay(df_pd, plot_dir, ds_name):
    if df_pd is None or "delay_ms" not in df_pd.columns:
        return
    if "wf_policy" not in df_pd.columns:
        return
    pivot = (df_pd[df_pd["link_policy"]=="BLA"]
             .groupby(["vm_policy","wf_policy"])["delay_ms"]
             .mean().unstack(fill_value=0))
    if pivot.empty:
        return
    fig, ax = plt.subplots(figsize=(9, 6))
    sns.heatmap(pivot, annot=True, fmt=".0f", cmap="RdYlGn_r", ax=ax,
                linewidths=0.5, cbar_kws={"label": "Avg Delay (ms)"})
    ax.set_title(f"Fig 13: Delay Heatmap (BLA) — VM × WF Policy — {ds_name}",
                 fontsize=13, fontweight="bold")
    save(fig, os.path.join(plot_dir, "fig13_heatmap_delay_bla"))


def fig_sla_violations(df_sla, plot_dir, ds_name):
    if df_sla is None:
        return
    if "link_policy" not in df_sla.columns:
        return
    grp_cols = ["link_policy","vm_policy"] if "vm_policy" in df_sla.columns else ["link_policy"]
    df_cnt = df_sla.groupby(grp_cols).size().reset_index(name="violations")

    fig, ax = plt.subplots(figsize=(10, 6))
    if "vm_policy" in grp_cols:
        sns.barplot(data=df_cnt, x="vm_policy", y="violations",
                    hue="link_policy", palette=PALETTE_LINK, ax=ax,
                    order=VM_ORDER, edgecolor="black", alpha=0.88)
    else:
        sns.barplot(data=df_cnt, x="link_policy", y="violations",
                    palette=PALETTE_LINK, ax=ax, edgecolor="black")
    annotate_bars(ax, fmt="{:.0f}", fontsize=8)
    ax.set_title(f"Fig 14: SLA Violation Count — {ds_name}",
                 fontsize=13, fontweight="bold")
    ax.set_xlabel("VM Policy"); ax.set_ylabel("SLA Violations")
    save(fig, os.path.join(plot_dir, "fig14_sla_violations"))


def fig_path_latency(df_path, plot_dir, ds_name):
    if df_path is None:
        return
    sel_col = "selected" if "selected" in df_path.columns else None
    if sel_col:
        df_sel = df_path[df_path[sel_col] == 1]
    else:
        df_sel = df_path

    lat_col = next((c for c in ["network_latency_ms","network_lat_ms"] if c in df_sel.columns), None)
    bw_col  = next((c for c in ["avg_pct_use","avg_bw_used"] if c in df_sel.columns), None)
    if lat_col is None:
        return

    fig, axes = plt.subplots(1, 2, figsize=(14, 6))
    for ax, col, label in zip(axes,
                               [lat_col, bw_col],
                               ["Network Latency (ms)", "BW Utilization (%)"]):
        if col is None or col not in df_sel.columns:
            continue
        sns.boxplot(data=df_sel, x="link_policy", y=col,
                    palette=PALETTE_LINK, ax=ax, width=0.5)
        ax.set_title(label, fontsize=11, fontweight="bold")
        ax.set_xlabel("Link Selection Policy"); ax.set_ylabel(label)

    fig.suptitle(f"Fig 15: Selected Path Quality — {ds_name}",
                 fontsize=13, fontweight="bold")
    save(fig, os.path.join(plot_dir, "fig15_path_quality"))


# ──────────────────────────────────────────────
# MAIN
# ──────────────────────────────────────────────
def generate_plots(results_dir):
    results_dir = str(results_dir)
    if not os.path.isdir(results_dir):
        print(f"[ERR] Not found: {results_dir}")
        return

    for ds_name in sorted(os.listdir(results_dir)):
        ds_path = os.path.join(results_dir, ds_name)
        if not os.path.isdir(ds_path) or ds_name in ("global_analysis", "plots", "__pycache__"):
            continue

        synthese_dir = os.path.join(ds_path, "synthese", "data")
        plot_dir     = os.path.join(ds_path, "plot")
        if not os.path.exists(synthese_dir):
            continue
        os.makedirs(plot_dir, exist_ok=True)

        print(f"\n=== {ds_name} ===")

        df_e    = load(synthese_dir, "host_energy_total.csv")
        df_pd   = load(synthese_dir, "packet_delays.csv")
        df_util = load(synthese_dir, "host_utilization.csv")
        df_lu   = load(synthese_dir, "link_utilization_up.csv")
        df_sla  = load(synthese_dir, "qos_violations.csv")
        df_path = load(synthese_dir, "path_latency_final.csv")

        fns = [
            (fig_energy_by_vm,           (df_e,  plot_dir, ds_name)),
            (fig_delay_by_vm,            (df_pd, plot_dir, ds_name)),
            (fig_cdf_delay,              (df_pd, plot_dir, ds_name)),
            (fig_delay_components,       (df_pd, plot_dir, ds_name)),
            (fig_boxplot_delay,          (df_pd, plot_dir, ds_name)),
            (fig_host_utilization,       (df_util, plot_dir, ds_name)),
            (fig_energy_vs_delay_scatter,(df_e, df_pd, plot_dir, ds_name)),
            (fig_queue_delay_by_vm,      (df_pd, plot_dir, ds_name)),
            (fig_bla_gain_summary,       (df_pd, df_e, plot_dir, ds_name)),
            (fig_wf_policy_impact,       (df_pd, plot_dir, ds_name)),
            (fig_link_utilization,       (df_lu, plot_dir, ds_name)),
            (fig_energy_timeseries,      (df_e, plot_dir, ds_name)),
            (fig_heatmap_delay,          (df_pd, plot_dir, ds_name)),
            (fig_sla_violations,         (df_sla, plot_dir, ds_name)),
            (fig_path_latency,           (df_path, plot_dir, ds_name)),
        ]

        for fn, args in fns:
            try:
                fn(*args)
                print(f"  [{fn.__name__}] OK")
            except Exception as e:
                print(f"  [{fn.__name__}] SKIP — {e}")

        print(f"  Figures → {plot_dir}/")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="RAS-SDNCloudSim — Scientific Plot Generator v2")
    parser.add_argument("results_dir", help="Root results directory")
    args = parser.parse_args()
    generate_plots(args.results_dir)
