import networkx as nx
import matplotlib.pyplot as plt
import os

G = nx.Graph()

# Add nodes with hierarchy positions
pos = {}
# Core
G.add_node("Core0\n(300M)", layer=3); pos["Core0\n(300M)"] = (1.5, 3)
G.add_node("Core1\n(80M)", layer=3); pos["Core1\n(80M)"] = (3.5, 3)

# Agg
G.add_node("Agg0", layer=2); pos["Agg0"] = (1.5, 2)
G.add_node("Agg1", layer=2); pos["Agg1"] = (3.5, 2)

# Edge
G.add_node("Edge0", layer=1); pos["Edge0"] = (0.5, 1)
G.add_node("Edge1", layer=1); pos["Edge1"] = (2.5, 1)
G.add_node("Edge2", layer=1); pos["Edge2"] = (4.5, 1)

# Hosts
hosts = ["Host0", "Host1", "Host2", "Host3", "Host4", "Host5"]
for i, h in enumerate(hosts):
    G.add_node(h, layer=0)
    pos[h] = (i*1.0, 0)

# Add edges with labels
edges = [
    ("Core0\n(300M)", "Agg0", "300 Mbps\n0.05 ms"),
    ("Core1\n(80M)", "Agg0", "80 Mbps\n0.10 ms"),
    ("Core0\n(300M)", "Agg1", "300 Mbps\n0.05 ms"),
    ("Core1\n(80M)", "Agg1", "80 Mbps\n0.10 ms"),
    ("Agg0", "Edge0", "500 Mbps"),
    ("Agg0", "Edge1", "500 Mbps"),
    ("Agg1", "Edge2", "500 Mbps"),
    ("Edge0", "Host0", "100 Mbps"),
    ("Edge0", "Host1", "100 Mbps"),
    ("Edge1", "Host2", "100 Mbps"),
    ("Edge1", "Host3", "100 Mbps"),
    ("Edge2", "Host4", "100 Mbps"),
    ("Edge2", "Host5", "100 Mbps"),
]

for u, v, lbl in edges:
    color = '#e74c3c' if '80' in lbl else '#2ecc71' if '300' in lbl else '#bdc3c7'
    G.add_edge(u, v, label=lbl, color=color)

plt.figure(figsize=(10, 8))
edge_colors = [G[u][v]['color'] for u,v in G.edges()]

# Draw nodes
nx.draw(G, pos, with_labels=True, node_size=3500, node_color='#3498db', 
        font_size=10, font_weight='bold', font_color='white', edge_color=edge_colors, width=2.5)

# Draw edge labels in groups to avoid overlap on the crossed links
labels_straight = { (u,v): d['label'] for u,v,d in G.edges(data=True) if (u.startswith('Core0') and v=='Agg0') or (u.startswith('Core1') and v=='Agg1') }
labels_cross_1 = { (u,v): d['label'] for u,v,d in G.edges(data=True) if (u.startswith('Core0') and v=='Agg1') }
labels_cross_2 = { (u,v): d['label'] for u,v,d in G.edges(data=True) if (u.startswith('Core1') and v=='Agg0') }
labels_other = { (u,v): d['label'] for u,v,d in G.edges(data=True) if not u.startswith('Core') }

nx.draw_networkx_edge_labels(G, pos, edge_labels=labels_straight, font_color='#2c3e50', font_size=9, label_pos=0.5, bbox=dict(facecolor='white', edgecolor='none', alpha=0.8))
nx.draw_networkx_edge_labels(G, pos, edge_labels=labels_cross_1, font_color='#2c3e50', font_size=9, label_pos=0.7, bbox=dict(facecolor='white', edgecolor='none', alpha=0.8))
nx.draw_networkx_edge_labels(G, pos, edge_labels=labels_cross_2, font_color='#2c3e50', font_size=9, label_pos=0.3, bbox=dict(facecolor='white', edgecolor='none', alpha=0.8))
nx.draw_networkx_edge_labels(G, pos, edge_labels=labels_other, font_color='#2c3e50', font_size=9, label_pos=0.5, bbox=dict(facecolor='white', edgecolor='none', alpha=0.8))

plt.title("Asymmetric SDN Fat-Tree Architecture (Small Dataset)", fontsize=16, weight='bold', pad=20)
plt.axis('off')

out_dir = r"E:\Workspace\v2\cloudsimsdn-research\results\2026-04-18"
plt.savefig(os.path.join(out_dir, "fig0_architecture.png"), dpi=300, bbox_inches='tight')
print("Architecture schema generated successfully!")
