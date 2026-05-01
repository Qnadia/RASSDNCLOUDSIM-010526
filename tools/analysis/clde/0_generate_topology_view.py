"""
0_generate_topology_view.py  — RAS-SDNCloudSim v2
Génère une visualisation enrichie de la topologie physique + virtuelle.
Améliorations vs v1:
  - Supporte physical.json ET virtual.json (dual graph)
  - Détecte et colore les liens "bottleneck" (BW < seuil)
  - Génère un graphe séparé pour la topologie virtuelle (VM flows)
  - Table Markdown étendue avec latence et distance
  - Compatible avec les 3 scales (mini/small/medium/large)
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

# ──────────────────────────────────────────────
# HELPERS
# ──────────────────────────────────────────────

def bw_label(bps):
    if bps >= 1e9:
        return f"{bps/1e9:.0f} Gbps"
    return f"{bps/1e6:.0f} Mbps"


def is_bottleneck(bps, all_bws, pct=0.25):
    """Link is a bottleneck if its BW is in the lowest 25% of all links."""
    threshold = sorted(all_bws)[int(len(all_bws) * pct)]
    return bps <= threshold


# ──────────────────────────────────────────────
# PHYSICAL TOPOLOGY
# ──────────────────────────────────────────────

def draw_physical(data, out_dir, dataset_name):
    G = nx.DiGraph()
    node_meta = {}
    labels = {}

    for n in data["nodes"]:
        name = n["name"]
        ntype = n.get("type", "host").lower()
        bw = n.get("bw", 0)
        mips = n.get("mips", 0)
        pes = n.get("pes", 0)
        ram = n.get("ram", 0)
        node_meta[name] = {"type": ntype, "bw": bw, "mips": mips}
        G.add_node(name, **node_meta[name])

        if mips > 0:
            labels[name] = (
                f"{name}\n{mips:,} MIPS\n"
                f"{pes}C | {ram//1024}GB\n"
                f"{bw_label(bw)}"
            )
        else:
            labels[name] = f"{name}\n({bw_label(bw)})"

    all_link_bws = [l.get("upBW", 0) for l in data.get("links", [])]
    edge_colors = []
    edge_widths = []
    edge_labels = {}

    for l in data.get("links", []):
        u, v = l["source"], l["destination"]
        bw = l.get("upBW", 0)
        lat = l.get("latency", 0)
        G.add_edge(u, v, bw=bw, latency=lat)
        bottleneck = is_bottleneck(bw, all_link_bws)
        edge_colors.append("#d62728" if bottleneck else "#2ca02c")
        edge_widths.append(1.5 if bottleneck else 2.5)
        edge_labels[(u, v)] = f"{bw_label(bw)}\n{lat}ms"

    # Layout by layer
    pos = {}
    layers = {"host": 0, "edge": 1, "aggregate": 2, "core": 3}
    layer_nodes = {i: [] for i in range(4)}
    for name, meta in node_meta.items():
        t = meta["type"]
        lvl = next((v for k, v in layers.items() if k in t), 0)
        layer_nodes[lvl].append(name)

    spreads = {0: 3.5, 1: 9.0, 2: 13.0, 3: 14.0}
    for lvl, names in layer_nodes.items():
        names_sorted = sorted(names)
        w = len(names_sorted)
        sp = spreads.get(lvl, 4.0)
        for i, name in enumerate(names_sorted):
            pos[name] = ((i - (w - 1) / 2) * sp, lvl * 14)

    # Figure
    fig_w = max(16, len(data["nodes"]) * 0.8)
    fig, ax = plt.subplots(figsize=(fig_w, 14))
    ax.margins(0.12)

    color_map = {"host": "#2ca02c", "edge": "#1f77b4",
                 "aggregate": "#ff7f0e", "core": "#d62728"}
    shape_map = {"host": "s", "edge": "o", "aggregate": "o", "core": "D"}
    size_map  = {"host": 3000, "edge": 2200, "aggregate": 2200, "core": 2800}

    drawn_types = set()
    for t, shape in shape_map.items():
        nodes_t = [n for n, d in G.nodes(data=True)
                   if t in d.get("type", "").lower() or (t == "host" and d.get("mips", 0) > 0)]
        if not nodes_t:
            continue
        drawn_types.add(t)
        nx.draw_networkx_nodes(
            G, pos, nodelist=nodes_t,
            node_shape=shape,
            node_color=color_map[t],
            node_size=size_map[t],
            alpha=0.92, edgecolors="black", linewidths=1.8, ax=ax
        )

    edges_list = list(G.edges())
    nx.draw_networkx_edges(
        G, pos, edgelist=edges_list,
        edge_color=edge_colors, width=edge_widths,
        alpha=0.75, arrows=False, ax=ax
    )
    nx.draw_networkx_labels(G, pos, labels=labels, font_size=7,
                            font_color="black", font_weight="bold", ax=ax)
    nx.draw_networkx_edge_labels(
        G, pos,
        edge_labels={(u, v): f"{bw_label(l.get('upBW',0))}" for l in data.get("links",[])
                     for u, v in [(l["source"], l["destination"])]},
        font_size=6, label_pos=0.35, rotate=False,
        bbox=dict(facecolor="white", alpha=0.85, edgecolor="grey", boxstyle="round,pad=0.2"),
        ax=ax
    )

    # Legend
    legend_elems = [
        mpatches.Patch(color=color_map[t], label=t.capitalize())
        for t in drawn_types
    ]
    legend_elems += [
        mlines.Line2D([], [], color="#d62728", linewidth=2.5, label="Bottleneck Link (low BW)"),
        mlines.Line2D([], [], color="#2ca02c", linewidth=2.5, label="Normal Link"),
    ]
    ax.legend(handles=legend_elems, loc="upper right", fontsize=10, framealpha=0.9)
    ax.set_title(f"Physical Topology — {dataset_name}  |  "
                 f"{len(data['nodes'])} nodes · {len(data.get('links',[]))} links",
                 fontsize=16, fontweight="bold", pad=16)
    ax.axis("off")

    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "fig0_physical_topology.png")
    plt.savefig(path, bbox_inches="tight", dpi=200)
    plt.close()
    print(f"  [Physical topology] → {path}")


# ──────────────────────────────────────────────
# VIRTUAL TOPOLOGY
# ──────────────────────────────────────────────

def draw_virtual(data, out_dir, dataset_name):
    G = nx.DiGraph()
    tier_order = ["web", "app", "db", "svc", "cache"]
    tier_y = {"web": 3, "app": 2, "db": 1, "svc": 0, "cache": 0}
    tier_color = {"web": "#1f77b4", "app": "#ff7f0e",
                  "db": "#d62728", "svc": "#9467bd", "cache": "#8c564b"}
    pos = {}
    tier_counts = {t: 0 for t in tier_order}
    tier_nodes  = {t: [] for t in tier_order}

    for n in data["nodes"]:
        name = n["name"]
        tier = next((t for t in tier_order if name.startswith(t)), "other")
        tier_nodes[tier].append(name)
        G.add_node(name, tier=tier, mips=n.get("mips", 0), ram=n.get("ram", 0))

    for tier in tier_order:
        ns = sorted(tier_nodes[tier])
        for i, name in enumerate(ns):
            pos[name] = ((i - (len(ns) - 1) / 2) * 3.5, tier_y.get(tier, -1) * 8)

    for l in data.get("links", []):
        G.add_edge(l["source"], l["destination"],
                   bw=l.get("bandwidth", 0), name=l.get("name", ""))

    fig, ax = plt.subplots(figsize=(max(14, len(data["nodes"]) * 0.6), 10))
    ax.margins(0.12)

    for tier, ns in tier_nodes.items():
        if not ns:
            continue
        nx.draw_networkx_nodes(
            G, pos, nodelist=ns,
            node_color=tier_color.get(tier, "#7f7f7f"),
            node_size=1800, alpha=0.9, edgecolors="black", linewidths=1.5, ax=ax
        )

    nx.draw_networkx_edges(G, pos, edge_color="#888888", width=1.5,
                           alpha=0.6, arrows=True,
                           arrowstyle="-|>", arrowsize=12,
                           connectionstyle="arc3,rad=0.1", ax=ax)
    labels = {n["name"]: f"{n['name']}\n{n.get('mips',0)//1000}k MIPS\n{n.get('ram',0)//1024}GB"
              for n in data["nodes"]}
    nx.draw_networkx_labels(G, pos, labels=labels, font_size=7,
                            font_color="black", font_weight="bold", ax=ax)

    legend_elems = [mpatches.Patch(color=c, label=t.capitalize())
                    for t, c in tier_color.items() if tier_nodes.get(t)]
    ax.legend(handles=legend_elems, loc="upper right", fontsize=10, framealpha=0.9)
    ax.set_title(
        f"Virtual Topology (VM Flows) — {dataset_name}  |  "
        f"{len(data['nodes'])} VMs · {len(data.get('links',[]))} flows",
        fontsize=15, fontweight="bold", pad=14
    )
    ax.axis("off")

    path = os.path.join(out_dir, "fig0b_virtual_topology.png")
    plt.savefig(path, bbox_inches="tight", dpi=200)
    plt.close()
    print(f"  [Virtual topology]  → {path}")


# ──────────────────────────────────────────────
# MARKDOWN REPORT
# ──────────────────────────────────────────────

def write_markdown(physical_data, virtual_data, out_dir, dataset_name):
    lines = [
        f"# Topology Report — {dataset_name}\n",
        "## Physical Infrastructure\n",
        f"- **Nodes:** {len(physical_data['nodes'])}",
        f"- **Links:** {len(physical_data.get('links', []))}",
        "",
        "### Node Summary\n",
        "| Name | Type | MIPS | RAM (GB) | BW |",
        "| :--- | :--- | ---: | ---: | ---: |",
    ]
    for n in physical_data["nodes"]:
        ram_gb = n.get("ram", 0) // 1024 if n.get("ram", 0) >= 1024 else f"{n.get('ram',0)} MB"
        lines.append(
            f"| {n['name']} | {n.get('type','?')} | "
            f"{n.get('mips','-')} | {ram_gb} | {bw_label(n.get('bw',0))} |"
        )

    lines += [
        "",
        "### Physical Links\n",
        "| Source | Destination | BW | Latency (ms) | Distance (m) |",
        "| :--- | :--- | ---: | ---: | ---: |",
    ]
    for l in physical_data.get("links", []):
        lines.append(
            f"| {l['source']} | {l['destination']} | "
            f"{bw_label(l.get('upBW',0))} | {l.get('latency','?')} | {l.get('distance','?')} |"
        )

    if virtual_data:
        lines += [
            "",
            "## Virtual Infrastructure\n",
            f"- **VMs:** {len(virtual_data['nodes'])}",
            f"- **Virtual links:** {len(virtual_data.get('links', []))}",
            "",
            "### VM Specifications\n",
            "| Name | Type | MIPS | RAM (GB) | BW |",
            "| :--- | :--- | ---: | ---: | ---: |",
        ]
        for n in virtual_data["nodes"]:
            t = next((t for t in ["web","app","db","svc","cache"] if n["name"].startswith(t)), "vm")
            ram_gb = n.get("ram", 0) // 1024
            lines.append(
                f"| {n['name']} | {t} | "
                f"{n.get('mips','-')} | {ram_gb} | {bw_label(n.get('bw',0))} |"
            )

    md_path = os.path.join(out_dir, "topology_description.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"  [Markdown report]   → {md_path}")


# ──────────────────────────────────────────────
# MAIN
# ──────────────────────────────────────────────

def generate_topology_view(dataset_path, out_dir):
    physical_json = os.path.join(dataset_path, "physical.json")
    virtual_json  = os.path.join(dataset_path, "virtual.json")
    dataset_name  = os.path.basename(dataset_path)

    if not os.path.exists(physical_json):
        print(f"[ERROR] {physical_json} not found.")
        return

    with open(physical_json, encoding="utf-8") as f:
        physical_data = json.load(f)

    virtual_data = None
    if os.path.exists(virtual_json):
        with open(virtual_json, encoding="utf-8") as f:
            virtual_data = json.load(f)

    os.makedirs(out_dir, exist_ok=True)
    draw_physical(physical_data, out_dir, dataset_name)
    if virtual_data:
        draw_virtual(virtual_data, out_dir, dataset_name)
    write_markdown(physical_data, virtual_data, out_dir, dataset_name)
    print(f"[DONE] Topology outputs → {out_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="RAS-SDNCloudSim — Topology Visualizer")
    parser.add_argument("--dataset", required=True, help="Path to dataset folder (contains physical.json)")
    parser.add_argument("--out-dir", required=True, help="Output directory for figures and markdown")
    args = parser.parse_args()
    generate_topology_view(args.dataset, args.out_dir)
