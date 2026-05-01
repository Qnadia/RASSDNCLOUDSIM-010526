"""
Analyse comparative : First-Link vs BW-Latency Aware (BLA)
Format de sortie : PDF vectoriel (jamais flou) + PNG 600 DPI
Style : IEEE publication-ready (Times New Roman 12pt)
"""

import pandas as pd
import matplotlib
import matplotlib.pyplot as plt
import os
import numpy as np
from collections import Counter

# ── Style publication IEEE ─────────────────────────────────────────────────
matplotlib.rcParams.update({
    "font.family":      "Times New Roman",
    "font.size":        12,
    "axes.labelsize":   13,
    "axes.titlesize":   13,
    "legend.fontsize":  11,
    "xtick.labelsize":  11,
    "ytick.labelsize":  11,
    "axes.linewidth":   1.2,
    "grid.linewidth":   0.6,
    "lines.linewidth":  1.8,
    "savefig.bbox":     "tight",
    "savefig.pad_inches": 0.05,
})

# ── Chemins (relatifs au script) ───────────────────────────────────────────
BASE = os.path.dirname(os.path.abspath(__file__))   # rep du script

# !! Remplacez uniquement ces 3 lignes si nécessaire !!
dir_first  = os.path.join(BASE, r'..\experiment1-LFF-SJF-SFL')   # SFL = First-Link
dir_bwlat  = os.path.join(BASE, r'..\experiment1-LFF-SJF-BLA')   # BLA = votre politique
output_dir = os.path.join(BASE, r'..\figures')
# ──────────────────────────────────────────────────────────────────────────

os.makedirs(output_dir, exist_ok=True)

COLORS = {"First-Link": "#1f77b4", "BLA": "#ff7f0e"}


def save_fig(fig, name):
    """Sauvegarde vectorielle (PDF) + raster (PNG 600 DPI)."""
    pdf = os.path.join(output_dir, f"{name}.pdf")
    png = os.path.join(output_dir, f"{name}.png")
    fig.savefig(pdf, format="pdf")       # ← vectoriel pour l'article
    fig.savefig(png, dpi=600)            # ← PNG haute résolution (slides/word)
    print(f"  ✔  {pdf}")
    print(f"  ✔  {png}")
    plt.close(fig)


def load_path_data(directory):
    filepath = os.path.join(directory, 'path_latency_final.csv')
    if not os.path.exists(filepath):
        print(f"⚠️  Fichier introuvable : {filepath}")
        return None
    df = pd.read_csv(filepath, sep=';', comment='#', header=None,
                     names=['time', 'src', 'dst', 'path', 'min_bw', 'avg_bw',
                            'pct_use', 'net_latency', 'proc_delay', 'total_delay', 'selected'])
    return df[df['selected'] == True]


df_first = load_path_data(dir_first)
df_bwlat = load_path_data(dir_bwlat)

if df_first is None or df_bwlat is None:
    print("❌ Arrêt : fichiers manquants — vérifiez dir_first et dir_bwlat")
    exit(1)


# ─────────────────────────────────────────────────────────────────────────────
# Figure 1 — Fréquence d'utilisation des chemins
# ─────────────────────────────────────────────────────────────────────────────
def plot_path_frequencies():
    first_counts = Counter(df_first['path'])
    bwlat_counts = Counter(df_bwlat['path'])
    all_paths    = sorted(set(first_counts) | set(bwlat_counts))

    x = np.arange(len(all_paths))
    w = 0.35

    fig, ax = plt.subplots(figsize=(9, 4))
    ax.bar(x - w/2, [first_counts.get(p, 0) for p in all_paths],
           width=w, label='First-Link', color=COLORS["First-Link"], alpha=0.85, edgecolor='white')
    ax.bar(x + w/2, [bwlat_counts.get(p, 0) for p in all_paths],
           width=w, label='BLA',        color=COLORS["BLA"],        alpha=0.85, edgecolor='white')

    ax.set_xticks(x)
    ax.set_xticklabels(all_paths, rotation=60, ha='right', fontsize=9)
    ax.set_ylabel("Path Selection Frequency")
    ax.set_xlabel("Network Path")
    ax.set_title("Path Selection Frequency: First-Link vs. BLA")
    ax.legend()
    ax.grid(True, axis='y', linestyle='--', alpha=0.4)
    fig.tight_layout()
    save_fig(fig, 'fig1_path_frequencies')


# ─────────────────────────────────────────────────────────────────────────────
# Figure 2 — Comparaison des délais moyens par paire src→dst
# ─────────────────────────────────────────────────────────────────────────────
def plot_delay_comparison():
    first_delays = df_first.groupby(['src', 'dst'])['total_delay'].mean()
    bwlat_delays = df_bwlat.groupby(['src', 'dst'])['total_delay'].mean()
    common = sorted(set(first_delays.index) & set(bwlat_delays.index))

    if not common:
        print("⚠️  Aucune paire src-dst commune")
        return

    pairs = [f"{s}→{d}" for s, d in common]
    x = np.arange(len(pairs))
    w = 0.35

    fig, ax = plt.subplots(figsize=(9, 4))
    b1 = ax.bar(x - w/2, [first_delays.loc[p] for p in common],
                width=w, label='First-Link', color=COLORS["First-Link"], alpha=0.85, edgecolor='white')
    b2 = ax.bar(x + w/2, [bwlat_delays.loc[p] for p in common],
                width=w, label='BLA',        color=COLORS["BLA"],        alpha=0.85, edgecolor='white')

    # Annotation du gain %
    for xi, pkey in zip(x, common):
        fv, bv = first_delays.loc[pkey], bwlat_delays.loc[pkey]
        gain = (fv - bv) / fv * 100 if fv > 0 else 0
        if abs(gain) > 1:
            ax.text(xi + w/2, bv + 0.05, f"{gain:+.0f}%", ha='center',
                    va='bottom', fontsize=8, color='darkgreen' if gain > 0 else 'red')

    ax.set_xticks(x)
    ax.set_xticklabels(pairs, rotation=35, ha='right')
    ax.set_ylabel("Average End-to-End Delay (ms)")
    ax.set_xlabel("Source → Destination Pair")
    ax.set_title("Average Delay Comparison: First-Link vs. BLA")
    ax.legend()
    ax.grid(True, axis='y', linestyle='--', alpha=0.4)
    fig.tight_layout()
    save_fig(fig, 'fig2_delay_comparison')


# ─────────────────────────────────────────────────────────────────────────────
# Figure 3 — Utilisation de la bande passante par chemin
# ─────────────────────────────────────────────────────────────────────────────
def plot_bandwidth_usage():
    first_bw  = df_first.groupby('path')['avg_bw'].mean()
    bwlat_bw  = df_bwlat.groupby('path')['avg_bw'].mean()
    all_paths = sorted(set(first_bw.index) | set(bwlat_bw.index))
    x = np.arange(len(all_paths))
    w = 0.35

    fig, ax = plt.subplots(figsize=(9, 4))
    ax.bar(x - w/2, [first_bw.get(p, 0) for p in all_paths],
           width=w, label='First-Link', color=COLORS["First-Link"], alpha=0.85, edgecolor='white')
    ax.bar(x + w/2, [bwlat_bw.get(p, 0) for p in all_paths],
           width=w, label='BLA',        color=COLORS["BLA"],        alpha=0.85, edgecolor='white')

    ax.set_xticks(x)
    ax.set_xticklabels(all_paths, rotation=60, ha='right', fontsize=9)
    ax.set_ylabel("Average Bandwidth Usage (Mbps)")
    ax.set_xlabel("Network Path")
    ax.set_title("Bandwidth Usage per Path: First-Link vs. BLA")
    ax.legend()
    ax.grid(True, axis='y', linestyle='--', alpha=0.4)
    fig.tight_layout()
    save_fig(fig, 'fig3_bandwidth_usage')


# ─────────────────────────────────────────────────────────────────────────────
# Figure 4 (NOUVEAU) — CDF des délais totaux
# ─────────────────────────────────────────────────────────────────────────────
def plot_delay_cdf():
    fig, ax = plt.subplots(figsize=(6, 4))

    for label, df, color in [
        ("First-Link", df_first, COLORS["First-Link"]),
        ("BLA",        df_bwlat, COLORS["BLA"]),
    ]:
        delays = np.sort(df['total_delay'].dropna().values)
        cdf    = np.arange(1, len(delays) + 1) / len(delays)
        ax.plot(delays, cdf, label=label, color=color, linewidth=2)

    ax.set_xlabel("End-to-End Delay (ms)")
    ax.set_ylabel("CDF")
    ax.set_title("CDF of End-to-End Delay: First-Link vs. BLA")
    ax.legend()
    ax.grid(True, linestyle='--', alpha=0.4)
    ax.set_ylim(0, 1.05)
    fig.tight_layout()
    save_fig(fig, 'fig4_delay_cdf')


# ─────────────────────────────────────────────────────────────────────────────
# Rapport texte
# ─────────────────────────────────────────────────────────────────────────────
def text_report():
    fp = set(df_first['path'].unique())
    bp = set(df_bwlat['path'].unique())
    lines = [
        "=== Path Analysis ===",
        f"Paths only in First-Link : {len(fp - bp)}",
        f"Paths only in BLA        : {len(bp - fp)}",
        f"Paths common to both     : {len(fp & bp)}",
        "",
        "=== Delay Statistics ===",
        f"First-Link — mean delay  : {df_first['total_delay'].mean():.3f} ms",
        f"BLA        — mean delay  : {df_bwlat['total_delay'].mean():.3f} ms",
        f"Improvement              : {(df_first['total_delay'].mean() - df_bwlat['total_delay'].mean()) / df_first['total_delay'].mean() * 100:+.1f}%",
    ]
    report_path = os.path.join(output_dir, "report.txt")
    with open(report_path, "w") as f:
        f.write("\n".join(lines))
    print("\n".join(lines))
    print(f"\n  ✔  {report_path}")


# ─────────────────────────────────────────────────────────────────────────────
print(f"\n📊 Génération des figures → {output_dir}\n")
plot_path_frequencies()
plot_delay_comparison()
plot_bandwidth_usage()
plot_delay_cdf()
text_report()
print(f"\n✅ Terminé — figures en PDF (vectoriel) + PNG 600 DPI")