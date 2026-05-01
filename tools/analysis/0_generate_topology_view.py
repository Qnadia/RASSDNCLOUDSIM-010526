import json
import os
import argparse
import networkx as nx
import matplotlib.pyplot as plt

def generate_topology_view(dataset_path, out_dir):
    physical_json = os.path.join(dataset_path, "physical.json")
    if not os.path.exists(physical_json):
        print(f"Error: {physical_json} not found.")
        return

    with open(physical_json, encoding="utf-8") as f:
        data = json.load(f)

    G = nx.Graph()
    
    # Nodes
    node_colors = []
    labels = {}
    node_shapes = {} # hosts will be 's' (square/rect-like)
    for n in data["nodes"]:
        name = n["name"]
        ntype = n.get("type", "host").lower()
        bw = n.get("bw", 0)
        mips = n.get("mips", 0)
        pes = n.get("pes", 0)
        ram = n.get("ram", 0)
        
        # Create detailed label: only show specs if it's a host (MIPS > 0)
        if mips > 0:
            labels[name] = f"{name}\n{mips:,.0f} MIPS\n{pes} CPU | {ram/1024:,.0f}G RAM\n{bw/1e6:,.0f} Mbps"
            node_shapes[name] = 's'
        else:
            labels[name] = f"{name}\n({bw/1e6:,.0f} Mbps)"
            node_shapes[name] = 'o'
            
        G.add_node(name, type=ntype, bw=bw, mips=mips, pes=pes, ram=ram)
        if "switch" in ntype:
            node_colors.append("#ff7f0e") # Orange for switches
        else:
            node_colors.append("#1f77b4") # Blue for hosts

    # Links
    link_info = []
    for l in data.get("links", []):
        u, v = l["source"], l["destination"]
        bw = l.get("upBW", 0) / 1e6 # Mbps
        G.add_edge(u, v, weight=bw)
        link_info.append({"src": u, "dst": v, "bw": bw})

    # 1. Define layers
    pos = {}
    nodes_by_layer = {0: [], 1: [], 2: [], 3: []}
    
    for n in data["nodes"]:
        name = n["name"]
        ntype = n.get("type", "host").lower()
        level = 0
        if "edge" in name.lower() or "edge" in ntype: level = 1
        elif "agg" in name.lower() or "agg" in ntype: level = 2
        elif "core" in name.lower() or "core" in ntype: level = 3
        nodes_by_layer[level].append(name)

    # 2. Assign positions
    for level, names in nodes_by_layer.items():
        names.sort()
        width = len(names)
        for i, name in enumerate(names):
            if level == 0:
                h_spread = 4.0
            elif level == 1:
                h_spread = 10.0
            else:
                h_spread = 14.0
            
            x = (i - (width - 1) / 2) * h_spread
            y = level * 15
            pos[name] = (x, y)

    # Plot
    plt.figure(figsize=(16, 12))
    ax = plt.gca()
    ax.margins(0.15) # Add padding
    
    # Define colors and sizes based on type
    color_map = {'host': '#2ca02c', 'edge': '#1f77b4', 'agg': '#ff7f0e', 'core': '#d62728'}
    
    # Draw hosts (squares)
    host_nodes = [n for n, d in G.nodes(data=True) if "switch" not in d["type"]]
    nx.draw_networkx_nodes(G, pos, nodelist=host_nodes, node_shape='s', 
                           node_color=color_map['host'], node_size=3500, alpha=0.9, edgecolors='black', linewidths=2)
    
    # Draw switches (circles)
    for sw_type in ['edge', 'agg', 'core']:
        nodes = [n for n, d in G.nodes(data=True) if sw_type in d.get("type", "").lower() or sw_type in n.lower()]
        if nodes:
            nx.draw_networkx_nodes(G, pos, nodelist=nodes, node_shape='o', 
                                   node_color=color_map[sw_type], node_size=2500, alpha=0.9, edgecolors='black', linewidths=2)
    
    # Draw edges
    nx.draw_networkx_edges(G, pos, edge_color="#888888", width=2.0, alpha=0.7)
    
    # Draw labels with larger black text
    nx.draw_networkx_labels(G, pos, labels=labels, font_size=9, 
                            font_color="black", font_weight="bold")
    
    # Draw edge weights (BW) with rotation and background box for clarity
    edge_labels = { (u, v): f"{d['weight']:,.0f} Mbps" for u, v, d in G.edges(data=True) }
    nx.draw_networkx_edge_labels(G, pos, edge_labels=edge_labels, 
                                 font_size=8, label_pos=0.3, 
                                 rotate=False, 
                                 bbox=dict(facecolor='white', alpha=0.9, edgecolor='black', boxstyle='round,pad=0.3'))
    
    # Legend
    import matplotlib.lines as mlines
    legend_handles = [
        mlines.Line2D([], [], color=color_map['core'], marker='o', linestyle='None', markersize=15, label='Core Switch'),
        mlines.Line2D([], [], color=color_map['agg'], marker='o', linestyle='None', markersize=15, label='Agg Switch'),
        mlines.Line2D([], [], color=color_map['edge'], marker='o', linestyle='None', markersize=15, label='Edge Switch'),
        mlines.Line2D([], [], color=color_map['host'], marker='s', linestyle='None', markersize=15, label='Host Server')
    ]
    # Filter legend to only types present in graph
    present_types = set()
    for n, d in G.nodes(data=True):
        ntype = d.get("type", "").lower()
        if "core" in n.lower() or "core" in ntype: present_types.add('Core Switch')
        elif "agg" in n.lower() or "agg" in ntype: present_types.add('Agg Switch')
        elif "edge" in n.lower() or "edge" in ntype: present_types.add('Edge Switch')
        elif "switch" not in ntype: present_types.add('Host Server')
        
    filtered_handles = [h for h in legend_handles if h.get_label() in present_types]
    plt.legend(handles=filtered_handles, loc='upper right', fontsize=12, framealpha=0.9, edgecolor='black')

    plt.title(f"Network Topology with Full Specifications: {os.path.basename(dataset_path)}", fontsize=20, fontweight='bold', pad=20)
    plt.axis('off')
    
    os.makedirs(out_dir, exist_ok=True)
    img_path = os.path.join(out_dir, "fig0_topology.png")
    plt.savefig(img_path, bbox_inches='tight', dpi=300)
    plt.close()
    print(f"  Topology image saved: {img_path}")

    # Generate MD snippet for links
    md_path = os.path.join(out_dir, "topology_description.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("### Descriptif des Liens Physiques\n\n")
        f.write("| Source | Destination | Bande Passante (Mbps) |\n")
        f.write("| :--- | :--- | :--- |\n")
        for l in link_info:
            f.write(f"| {l['src']} | {l['dst']} | {l['bw']:,} |\n")
    print(f"  Topology description saved: {md_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--out-dir", required=True)
    args = parser.parse_args()
    generate_topology_view(args.dataset, args.out_dir)
