"""
6_generate_scalability_plots.py  — RAS-SDNCloudSim v2
Génère les graphiques de scalabilité cross-dataset (Small / Medium / Large).
Améliorations vs v1:
  - Support automatique de N datasets (plus limité à 3 noms hardcodés)
  - 6 métriques au lieu de 4 (+ queuing delay, net latency)
  - Annotations de gain BLA vs First sur chaque barre
  - Courbes de tendance (line overlay sur barres)
  - Export tableau CSV des résultats de scalabilité
  - Figure "relative gain" séparée (% improvement vs scale)
  - Gestion robuste si un dataset est absent
"""

import os
import argparse
import warnings
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
import seaborn as sns

warnings.filterwarnings("ignore")

sns.set_theme(style="whitegrid")
plt.rcParams.update({
    "font.family": "serif",
    "axes.edgecolor": "black",
    "axes.linewidth": 1.5,
    "grid.alpha": 0.6,
    "figure.autolayout": True,
})

PALETTE = {"First": "#d62728", "BLA": "#1f77b4"}
SCALE_ORDER = ["Mini", "Small", "Medium", "Large", "LargeVF"]   # canonical order


# ──────────────────────────────────────────────
# DATA LOADING
# ──────────────────────────────────────────────
def load_csv(path):
    if not os.path.exists(path):
        return None
    try:
        df = pd.read_csv(path, sep=";")
        df.columns = [c.strip().lower() for c in df.columns]
        df["link_policy"] = df.get("link_policy", pd.Series(dtype=str)).replace(
            {"DynLatBw": "BLA", "dynlatbw": "BLA"}
        )
        return df
    except Exception:
        return None


def extract_metrics(ds_path, label):
    """
    Returns a list of dicts with metrics per link policy for one dataset.
    """
    data_dir = os.path.join(ds_path, "synthese", "data")
    rows = []

    df_pd  = load_csv(os.path.join(data_dir, "packet_delays.csv"))
    df_e   = load_csv(os.path.join(data_dir, "host_energy_total.csv"))
    df_sla = load_csv(os.path.join(data_dir, "qos_violations.csv"))
    df_path= load_csv(os.path.join(data_dir, "path_latency_final.csv"))

    for pol in ["First", "BLA"]:
        rec = {"Dataset": label, "Policy": pol}

        # ── Packet delay metrics ──
        if df_pd is not None and "delay_ms" in df_pd.columns:
            sub = df_pd[df_pd["link_policy"] == pol]
            rec["Avg Delay (ms)"]       = sub["delay_ms"].mean() if not sub.empty else np.nan
            rec["Avg Queue (ms)"]       = sub["queue_delay_ms"].mean() if "queue_delay_ms" in sub.columns and not sub.empty else np.nan
            rec["Avg Trans (ms)"]       = sub["trans_delay_ms"].mean() if "trans_delay_ms" in sub.columns and not sub.empty else np.nan

        # ── Energy ──
        if df_e is not None and "energy" in df_e.columns:
            sub = df_e[df_e["link_policy"] == pol]
            if not sub.empty and "time" in sub.columns:
                last = sub.sort_values("time").groupby(
                    [c for c in ["vm_policy","wf_policy","host_id"] if c in sub.columns]
                ).last()
                rec["Total Energy (Wh)"] = last["energy"].sum()
            elif not sub.empty:
                rec["Total Energy (Wh)"] = sub["energy"].sum()

        # ── SLA ──
        if df_sla is not None:
            sub = df_sla[df_sla["link_policy"] == pol]
            rec["SLA Violations"] = len(sub)

        # ── Network latency (selected paths) ──
        if df_path is not None:
            sel_col = "selected" if "selected" in df_path.columns else None
            sub = df_path[df_path["link_policy"] == pol]
            if sel_col:
                sub = sub[sub[sel_col] == 1]
            lat_col = next((c for c in ["network_latency_ms","network_lat_ms"]
                            if c in sub.columns), None)
            if lat_col and not sub.empty:
                rec["Net Latency (ms)"] = sub[lat_col].mean()
            bw_col = next((c for c in ["avg_pct_use"] if c in sub.columns), None)
            if bw_col and not sub.empty:
                rec["BW Util (%)"] = sub[bw_col].mean()

        rows.append(rec)

    return rows


# ──────────────────────────────────────────────
# PLOTTING
# ──────────────────────────────────────────────
def annotate_gain(ax, df, metric, datasets):
    """Annotate % gain BLA vs First above each dataset group."""
    for ds in datasets:
        sub = df[df["Dataset"] == ds]
        f = sub[sub["Policy"]=="First"][metric].values
        b = sub[sub["Policy"]=="BLA"][metric].values
        if len(f) == 0 or len(b) == 0:
            continue
        f_v, b_v = f[0], b[0]
        if np.isnan(f_v) or np.isnan(b_v) or f_v == 0:
            continue
        gain = (f_v - b_v) / f_v * 100
        sign = "−" if gain >= 0 else "+"
        # Find x position of this group
        x_pos = datasets.index(ds)
        y_pos = max(f_v, b_v) * 1.05
        ax.annotate(f"{sign}{abs(gain):.1f}%",
                    xy=(x_pos, y_pos), ha="center", va="bottom",
                    fontsize=9, fontweight="bold", color="#2ca02c" if gain >= 0 else "#d62728")


def fig_scalability_metrics(df_all, plot_dir, datasets):
    metrics = [
        ("Avg Delay (ms)",    "Packet Delay",        "ms"),
        ("Total Energy (Wh)", "Total Energy",         "Wh"),
        ("Avg Queue (ms)",    "Queuing Delay",        "ms"),
        ("SLA Violations",    "SLA Violations",       "#"),
        ("Net Latency (ms)",  "Network Latency",      "ms"),
        ("BW Util (%)",       "BW Utilization",       "%"),
    ]
    existing = [(m, t, u) for m, t, u in metrics if m in df_all.columns]
    nrows = (len(existing) + 1) // 2
    fig, axes = plt.subplots(nrows, 2, figsize=(16, 5 * nrows))
    axes = axes.flatten()

    for i, (metric, title, unit) in enumerate(existing):
        ax = axes[i]
        sub = df_all[df_all["Dataset"].isin(datasets)].copy()
        sub["Dataset"] = pd.Categorical(sub["Dataset"],
                                         [d for d in SCALE_ORDER if d in datasets])
        sub = sub.sort_values("Dataset")

        bars = sns.barplot(data=sub, x="Dataset", y=metric,
                           hue="Policy", palette=PALETTE, ax=ax,
                           edgecolor="black", alpha=0.87, errorbar=None)

        # Trend lines (mean per policy across scales)
        for pol, color in PALETTE.items():
            pol_data = sub[sub["Policy"] == pol].groupby("Dataset")[metric].mean()
            ds_order = [d for d in SCALE_ORDER if d in pol_data.index]
            ys = [pol_data.get(d, np.nan) for d in ds_order]
            xs = [datasets.index(d) for d in ds_order if d in datasets]
            if len(xs) > 1:
                ax.plot(xs, ys, color=color, linestyle="--",
                        linewidth=1.5, marker="o", markersize=5, zorder=10)

        annotate_gain(ax, sub, metric, [d for d in SCALE_ORDER if d in datasets])

        ax.set_title(title, fontsize=12, fontweight="bold")
        ax.set_xlabel("Scale"); ax.set_ylabel(f"{unit}")
        ax.yaxis.set_major_formatter(mticker.FuncFormatter(
            lambda x, _: f"{x:,.0f}" if x >= 1000 else f"{x:.1f}"
        ))

    # Hide unused axes
    for j in range(len(existing), len(axes)):
        axes[j].set_visible(False)

    fig.suptitle("Scalability Analysis: First vs BLA — Cross-Scale Comparison",
                 fontsize=16, fontweight="bold", y=1.01)
    path = os.path.join(plot_dir, "fig_scalability_all_metrics")
    for ext in ("png", "svg"):
        fig.savefig(f"{path}.{ext}", bbox_inches="tight", dpi=200)
    plt.close(fig)
    print(f"  [scalability metrics] → {path}.png")


def fig_relative_gain(df_all, plot_dir, datasets):
    """Show % BLA gain vs First for each metric and scale."""
    metrics = ["Avg Delay (ms)", "Total Energy (Wh)",
               "Avg Queue (ms)", "Net Latency (ms)"]
    metrics = [m for m in metrics if m in df_all.columns]

    records = []
    for ds in [d for d in SCALE_ORDER if d in datasets]:
        sub = df_all[df_all["Dataset"] == ds]
        for m in metrics:
            f = sub[sub["Policy"]=="First"][m].values
            b = sub[sub["Policy"]=="BLA"][m].values
            if len(f) and len(b) and f[0] != 0 and not np.isnan(f[0]):
                gain = (f[0] - b[0]) / f[0] * 100
                records.append({"Scale": ds, "Metric": m.split(" (")[0], "BLA Gain (%)": gain})

    if not records:
        return
    df_gain = pd.DataFrame(records)
    fig, ax = plt.subplots(figsize=(12, 6))
    sns.barplot(data=df_gain, x="Scale", y="BLA Gain (%)",
                hue="Metric", ax=ax, edgecolor="black", alpha=0.87,
                order=[d for d in SCALE_ORDER if d in datasets])
    ax.axhline(0, color="black", linewidth=1.2)
    ax.set_title("BLA Relative Gain (%) vs First — Scalability",
                 fontsize=14, fontweight="bold")
    ax.set_xlabel("Dataset Scale"); ax.set_ylabel("Reduction (%)")
    path = os.path.join(plot_dir, "fig_scalability_relative_gain")
    for ext in ("png", "svg"):
        fig.savefig(f"{path}.{ext}", bbox_inches="tight", dpi=200)
    plt.close(fig)
    print(f"  [relative gain]       → {path}.png")


# ──────────────────────────────────────────────
# MAIN
# ──────────────────────────────────────────────
def generate_scalability(results_dir, out_dir=None):
    results_dir = str(results_dir)
    if out_dir is None:
        out_dir = os.path.join(results_dir, "scalability_analysis")
    os.makedirs(out_dir, exist_ok=True)

    # Auto-discover datasets
    all_ds = []
    for item in sorted(os.listdir(results_dir)):
        path = os.path.join(results_dir, item)
        data_dir = os.path.join(path, "synthese", "data")
        if os.path.isdir(path) and os.path.exists(data_dir):
            all_ds.append((item, path))

    if not all_ds:
        print("[ERR] No datasets found with synthese/data/ structure.")
        return

    print(f"Found datasets: {[d for d, _ in all_ds]}")

    all_rows = []
    for ds_name, ds_path in all_ds:
        label = ds_name.replace("dataset-", "")
        if label.lower() == "largevf":
            label = "LargeVF"
        else:
            label = label.capitalize()
        rows = extract_metrics(ds_path, label)
        all_rows.extend(rows)

    if not all_rows:
        print("[ERR] No metrics extracted.")
        return

    df_all = pd.DataFrame(all_rows)
    datasets_present = [d for d in SCALE_ORDER if d in df_all["Dataset"].unique()]
    # Add any not in SCALE_ORDER
    for d in df_all["Dataset"].unique():
        if d not in datasets_present:
            datasets_present.append(d)

    # Export CSV summary
    csv_path = os.path.join(out_dir, "scalability_summary.csv")
    df_all.to_csv(csv_path, index=False, sep=";")
    print(f"  [summary CSV] -> {csv_path}")

    fig_scalability_metrics(df_all, out_dir, datasets_present)
    fig_relative_gain(df_all, out_dir, datasets_present)

    print(f"\n[DONE] Scalability figures -> {out_dir}/")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="RAS-SDNCloudSim — Scalability Analysis v2")
    parser.add_argument("results_dir", help="Root results directory")
    parser.add_argument("--out-dir", help="Output directory (default: results_dir/scalability_analysis)")
    args = parser.parse_args()
    generate_scalability(args.results_dir, args.out_dir)
