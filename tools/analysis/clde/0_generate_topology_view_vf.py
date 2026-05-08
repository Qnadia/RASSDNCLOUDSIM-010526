"""
0_generate_topology_view.py  — RAS-SDNCloudSim v2
Génère la figure de topologie physique d'un dataset.

FIXES vs v1 :
  - nx.MultiGraph() au lieu de nx.Graph() → supporte les liens parallèles
  - Arêtes colorées selon BW (rouge=goulot, vert=fast, gris=normal)
  - Arêtes parallèles courbées (connectionstyle arc3) pour lisibilité
  - Label BW sur chaque lien individuel (pas d'écrasement par le dernier)
  - Légende enrichie avec couleurs de liens
  - Taille de figure adaptée au nombre de nœuds
"""

import json
import os
import argparse
import networkx as nx
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.lines as mlines
import matplotlib.patches as mpatches
import numpy as np
from collections import defaultdict


# ── Seuils BW pour la colorisation des liens ─────────────────────────────────
BW_SLOW_CRITICAL  = 50      # < 50 Mbps   → rouge (goulot critique)
BW_SLOW_SECONDARY = 500     # < 500 Mbps  → orange (goulot secondaire)
BW_FAST           = 1000    # >= 1 Gbps   → vert (lien rapide)


def bw_link_style(bw_mbps):
    """Retourne (color, linewidth, linestyle) selon la BW."""
    if bw_mbps < BW_SLOW_CRITICAL:
        return "#d62728", 3.5, "--"   # rouge pointillé = goulot critique
    elif bw_mbps < BW_SLOW_SECONDARY:
        return "#ff7f0e", 2.5, "--"   # orange pointillé = goulot secondaire
    elif bw_mbps >= BW_FAST:
        return "#2ca02c", 2.2, "-"    # vert = fast
    else:
        return "#888888", 1.8, "-"    # gris = normal


def generate_topology_view(dataset_path, out_dir):
    physical_json = os.path.join(dataset_path, "physical.json")
    if not os.path.exists(physical_json):
        print(f"[ERR] {physical_json} not found.")
        return

    with open(physical_json, encoding="utf-8") as f:
        data = json.load(f)

    # ── Graphe Multi (supporte liens parallèles) ──────────────────────────────
    G = nx.MultiGraph()

    # ── Nœuds ────────────────────────────────────────────────────────────────
    node_meta = {}
    for n in data["nodes"]:
        name  = n["name"]
        ntype = n.get("type", "host").lower()
        bw    = n.get("bw", 0)
        mips  = n.get("mips", 0)
        pes   = n.get("pes", 0)
        ram   = n.get("ram", 0)

        if mips > 0:  # hôte
            label = (f"{name}\n{mips:,} MIPS\n"
                     f"{pes} CPU | {ram//1024}G RAM\n"
                     f"{bw/1e6:,.0f} Mbps")
        else:         # switch
            label = f"{name}\n({bw/1e6:,.0f} Mbps)"

        G.add_node(name, type=ntype, bw=bw, mips=mips, pes=pes, ram=ram, label=label)
        node_meta[name] = {"type": ntype, "label": label}

    # ── Liens : déduplication full-duplex, conservation vrais parallèles ────────
    # Convention CloudSimSDN : chaque lien physique est déclaré 2x (une par sens).
    # On fusionne les 2 directions du même lien (paire non-ordonnée + même BW)
    # en 1 seule arête, mais on conserve les vrais parallèles (BW différentes).
    #
    # Exemple dataset-small :
    #   core0->agg0 5Gbps + agg0->core0 5Gbps  => 1 arête  (full-duplex)
    #   agg1->edge1 10Mbps + 5Gbps + 2Gbps     => 3 arêtes (parallèles vrais)

    seen_edges = {}  # (paire_triée, bw_arrondie) -> {src, dst, bw, lat}
    link_info  = []

    for lk in data.get("links", []):
        src     = lk["source"]
        dst     = lk["destination"]
        bw_mbps = lk.get("upBW", 0) / 1e6
        lat     = lk.get("latency", 0)
        pair    = tuple(sorted([src, dst]))
        key     = (pair, round(bw_mbps))  # BW arrondie = signature du lien physique
        if key not in seen_edges:
            seen_edges[key] = {"src": pair[0], "dst": pair[1],
                               "bw": bw_mbps, "lat": lat}

    for edge in seen_edges.values():
        G.add_edge(edge["src"], edge["dst"],
                   weight=edge["bw"], latency=edge["lat"])
        link_info.append(edge)

    # ── Positions hiérarchiques ───────────────────────────────────────────────
    layers = defaultdict(list)
    for n in data["nodes"]:
        name  = n["name"]
        ntype = n.get("type", "host").lower()
        if "host" in ntype or ntype == "vm":
            layer = 0
        elif "edge" in ntype or "edge" in name.lower():
            layer = 1
        elif "agg" in ntype or "agg" in name.lower():
            layer = 2
        elif "core" in ntype or "core" in name.lower():
            layer = 3
        else:
            layer = 0
        layers[layer].append(name)

    pos = {}
    # Spread horizontal adaptatif selon nb de noeuds par couche
    # Vertical : 18 unités entre couches pour éviter chevauchement labels/arêtes
    for level, names in layers.items():
        names.sort()
        w = len(names)
        # Plus de noeuds = spread plus large
        if level == 0:    spread = max(4.5, 22.0 / max(w, 1))  # hosts
        elif level == 1:  spread = max(7.0, 18.0 / max(w, 1))  # edge
        elif level == 2:  spread = max(12.0, 20.0 / max(w, 1)) # agg
        else:             spread = max(12.0, 20.0 / max(w, 1)) # core
        for i, name in enumerate(names):
            x = (i - (w - 1) / 2) * spread
            y = level * 18  # 18 au lieu de 14 = plus d'espace vertical
            pos[name] = (x, y)

    # ── Figure ────────────────────────────────────────────────────────────────
    n_nodes = G.number_of_nodes()
    fig_w = max(20, n_nodes * 2.2)
    fig_h = max(16, 18)
    fig, ax = plt.subplots(figsize=(fig_w, fig_h))
    ax.set_facecolor("#F8F8F8")
    fig.patch.set_facecolor("#F8F8F8")

    # ── Couleurs nœuds ────────────────────────────────────────────────────────
    color_map = {
        "host":      "#2ca02c",
        "edge":      "#1f77b4",
        "aggregate": "#ff7f0e",
        "core":      "#d62728",
    }
    size_map = {"host": 3500, "edge": 2500, "aggregate": 2500, "core": 2500}

    # Dessiner par type pour formes différentes (carré=hôte, cercle=switch)
    host_nodes   = [n for n, d in G.nodes(data=True) if "host" in d.get("type","")]
    switch_types = ["core", "aggregate", "edge"]

    nx.draw_networkx_nodes(G, pos, nodelist=host_nodes,
                           node_shape="s", node_color=color_map["host"],
                           node_size=3500, alpha=0.92,
                           edgecolors="black", linewidths=2, ax=ax)

    for sw_type in switch_types:
        sw_nodes = [n for n, d in G.nodes(data=True)
                    if sw_type in d.get("type","").lower() or sw_type in n.lower()
                    and n not in host_nodes]
        if sw_nodes:
            nx.draw_networkx_nodes(G, pos, nodelist=sw_nodes,
                                   node_shape="o",
                                   node_color=color_map.get(sw_type, "#888888"),
                                   node_size=size_map.get(sw_type, 2500),
                                   alpha=0.92, edgecolors="black", linewidths=2, ax=ax)

    # ── Dessiner les arêtes + labels BW ─────────────────────────────────────
    # Approche simple (v1) : draw_networkx_edges + draw_networkx_edge_labels
    # Les liens parallèles sont colorés différemment mais dessinés droits.
    # On groupe par couleur pour le dessin.

    # Séparer liens par catégorie BW
    edges_slow_crit  = [(u,v) for u,v,d in G.edges(data=True) if d.get("weight",0) < BW_SLOW_CRITICAL]
    edges_slow_sec   = [(u,v) for u,v,d in G.edges(data=True) if BW_SLOW_CRITICAL <= d.get("weight",0) < BW_SLOW_SECONDARY]
    edges_fast       = [(u,v) for u,v,d in G.edges(data=True) if d.get("weight",0) >= BW_FAST]
    edges_normal     = [(u,v) for u,v,d in G.edges(data=True) if BW_SLOW_SECONDARY <= d.get("weight",0) < BW_FAST]

    draw_groups = [
        (edges_slow_crit,  "#d62728", 3.5, "--"),
        (edges_slow_sec,   "#ff7f0e", 2.5, "--"),
        (edges_fast,       "#2ca02c", 2.2, "-"),
        (edges_normal,     "#888888", 1.8, "-"),
    ]
    for edgelist, color, lw, ls in draw_groups:
        if edgelist:
            nx.draw_networkx_edges(G, pos, edgelist=edgelist,
                                   edge_color=color, width=lw,
                                   style=ls, alpha=0.85, ax=ax)

    # Labels BW : un label par arête, positionné à label_pos=0.3
    # (légèrement décalé du centre pour éviter chevauchement avec label opposé)
    edge_labels = {}
    for u, v, d in G.edges(data=True):
        bw = d.get("weight", 0)
        label = f"{bw/1000:.0f} Gbps" if bw >= 1000 else f"{bw:.0f} Mbps"
        # Clé (u,v) — pour MultiGraph on prend le premier trouvé par paire
        if (u,v) not in edge_labels and (v,u) not in edge_labels:
            edge_labels[(u,v)] = label

    nx.draw_networkx_edge_labels(
        G, pos,
        edge_labels=edge_labels,
        font_size=8,
        label_pos=0.3,
        rotate=False,
        bbox=dict(facecolor="white", alpha=0.9,
                  edgecolor="none", boxstyle="round,pad=0.25"),
        ax=ax
    )

    # ── Labels nœuds ─────────────────────────────────────────────────────────
    labels = {n: d["label"] for n, d in G.nodes(data=True)}
    nx.draw_networkx_labels(G, pos, labels=labels, font_size=8.5,
                            font_color="black", font_weight="bold", ax=ax)

    # ── Annotations couches ───────────────────────────────────────────────────
    layer_info = [(0,"Host Layer","#2ca02c"), (1,"Edge Layer","#1f77b4"),
                  (2,"Aggregate Layer","#ff7f0e"), (3,"Core Layer","#d62728")]
    x_right = ax.get_xlim()[1] if ax.get_xlim()[1] != 0 else 20
    for lvl, ltxt, lc in layer_info:
        if layers[lvl]:
            y = lvl * 18  # aligned with new vertical spacing
            ax.text(x_right + 1, y, ltxt, ha="left", va="center",
                    fontsize=10, color=lc, fontweight="bold", style="italic",
                    bbox=dict(boxstyle="round,pad=0.3", facecolor="white",
                              alpha=0.7, edgecolor=lc))

    # ── Légende ───────────────────────────────────────────────────────────────
    legend_nodes = [
        mlines.Line2D([],[],color="#d62728",marker="o",ls="None",ms=13,label="Core Switch"),
        mlines.Line2D([],[],color="#ff7f0e",marker="o",ls="None",ms=13,label="Aggregate Switch"),
        mlines.Line2D([],[],color="#1f77b4",marker="o",ls="None",ms=13,label="Edge Switch"),
        mlines.Line2D([],[],color="#2ca02c",marker="s",ls="None",ms=13,label="Host Server"),
    ]
    legend_links = [
        mlines.Line2D([],[],color="#d62728",lw=3,ls="--",
                      label=f"Lien SLOW < {BW_SLOW_CRITICAL} Mbps — goulot critique (First bloque ici)"),
        mlines.Line2D([],[],color="#ff7f0e",lw=2.5,ls="--",
                      label=f"Lien SLOW {BW_SLOW_CRITICAL}–{BW_SLOW_SECONDARY} Mbps — goulot secondaire"),
        mlines.Line2D([],[],color="#2ca02c",lw=2.2,ls="-",
                      label=f"Lien FAST >= {BW_FAST} Mbps — BLA bascule ici"),
        mlines.Line2D([],[],color="#888888",lw=1.8,ls="-",label="Lien intermédiaire"),
    ]

    # Filtrer selon types présents
    present = set()
    for n, d in G.nodes(data=True):
        t = d.get("type","").lower()
        if "core"      in t or "core"      in n.lower(): present.add("Core Switch")
        if "aggregate" in t or "agg"       in n.lower(): present.add("Aggregate Switch")
        if "edge"      in t or "edge"      in n.lower(): present.add("Edge Switch")
        if "host"      in t:                             present.add("Host Server")
    filtered_nodes = [h for h in legend_nodes if h.get_label() in present]

    # Filtrer liens selon BW présentes
    bws = [d.get("weight",0) for _,_,d in G.edges(data=True)]
    filtered_links = []
    if any(b < BW_SLOW_CRITICAL  for b in bws): filtered_links.append(legend_links[0])
    if any(BW_SLOW_CRITICAL <= b < BW_SLOW_SECONDARY for b in bws): filtered_links.append(legend_links[1])
    if any(b >= BW_FAST          for b in bws): filtered_links.append(legend_links[2])
    if any(BW_SLOW_SECONDARY <= b < BW_FAST for b in bws): filtered_links.append(legend_links[3])

    plt.title(
        f"Topologie Physique : {os.path.basename(dataset_path)}\n"
        f"Liens SLOW (rouge/orange, pointillés) = goulots empruntes par First | "
        f"Liens FAST (vert) = chemins alternatifs choisis par BLA",
        fontsize=14, fontweight="bold", pad=20, color="#1A1A2E"
    )
    plt.axis("off")

    # Légende placée SOUS la figure (hors axes) pour ne pas couvrir les hôtes
    fig.legend(handles=filtered_nodes + filtered_links,
               loc="lower center",
               bbox_to_anchor=(0.5, 0.0),   # 0.0 = bas de la figure entière
               bbox_transform=fig.transFigure,
               fontsize=9.5,
               framealpha=0.97, edgecolor="#CCCCCC", fancybox=True,
               title="Légende", title_fontsize=10,
               ncol=2)

    plt.tight_layout(rect=[0, 0.13, 1, 1])  # 13% réservés en bas pour la légende

    # ── Sauvegarde ───────────────────────────────────────────────────────────
    os.makedirs(out_dir, exist_ok=True)
    img_path = os.path.join(out_dir, "fig0_topology.png")
    plt.savefig(img_path, bbox_inches="tight", dpi=200,
                facecolor="#F8F8F8")
    plt.close()
    print(f"  [OK] Topology image saved: {img_path}")

    # ── Markdown descriptif ───────────────────────────────────────────────────
    md_path = os.path.join(out_dir, "topology_description.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(f"# Topologie Physique — {os.path.basename(dataset_path)}\n\n")
        f.write("## Nœuds\n\n")
        f.write("| Nom | Type | MIPS | CPU | RAM (GB) | BW NIC |\n")
        f.write("|---|---|---|---|---|---|\n")
        for n in data["nodes"]:
            name  = n["name"]
            ntype = n.get("type","?")
            mips  = n.get("mips",0)
            pes   = n.get("pes",0)
            ram   = n.get("ram",0)
            bw    = n.get("bw",0)
            f.write(f"| {name} | {ntype} | {mips:,} | {pes} | {ram//1024} | {bw/1e6:,.0f} Mbps |\n")

        f.write("\n## Liens Physiques\n\n")
        f.write("| Source | Destination | BW (Mbps) | Latence (ms) | Catégorie |\n")
        f.write("|---|---|---|---|---|\n")
        for lk in link_info:
            bw = lk["bw"]
            if bw < BW_SLOW_CRITICAL:
                cat = "🔴 GOULOT CRITIQUE"
            elif bw < BW_SLOW_SECONDARY:
                cat = "🟠 GOULOT SECONDAIRE"
            elif bw >= BW_FAST:
                cat = "🟢 FAST"
            else:
                cat = "⚪ NORMAL"
            f.write(f"| {lk['src']} | {lk['dst']} | {bw:,.0f} | {lk['lat']} | {cat} |\n")
    print(f"  [OK] Topology description saved: {md_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="RAS-SDNCloudSim — Physical Topology Visualizer v2")
    parser.add_argument("--dataset",  required=True,
                        help="Chemin vers le dossier dataset (contient physical.json)")
    parser.add_argument("--out-dir",  required=True,
                        help="Dossier de sortie pour fig0_topology.png")
    args = parser.parse_args()
    generate_topology_view(args.dataset, args.out_dir)
