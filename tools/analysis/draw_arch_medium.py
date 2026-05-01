import networkx as nx
import matplotlib.pyplot as plt
import os

G = nx.Graph()

# Add nodes with hierarchy positions
pos = {}

# Core (Layer 3)
G.add_node("Core0\n(Fast)", layer=3); pos["Core0\n(Fast)"] = (2.5, 3)
G.add_node("Core1\n(Slow)", layer=3); pos["Core1\n(Slow)"] = (5.5, 3)

# Aggregation (Layer 2)
aggs = ["Agg0", "Agg1", "Agg2", "Agg3"]
for i, a in enumerate(aggs):
    G.add_node(a, layer=2)
    pos[a] = (1 + i*2, 2)

# Edge (Layer 1)
edges_nodes = ["Edge0", "Edge1", "Edge2", "Edge3", "Edge4", "Edge5"]
for i, e in enumerate(edges_nodes):
    G.add_node(e, layer=1)
    pos[e] = (0.5 + i*1.4, 1)

# Hosts (Layer 0)
hosts = [f"h_{i}" for i in range(12)]
for i, h in enumerate(hosts):
    G.add_node(h, layer=0)
    pos[h] = (i*0.7, 0)

# Add edges
# We only label the Core links to avoid clutter
edges = [
    ("Core0\n(Fast)", "Agg0", "500M\n0.05ms", '#2ecc71'),
    ("Core0\n(Fast)", "Agg1", "300M\n0.10ms", '#2ecc71'),
    ("Core0\n(Fast)", "Agg2", "400M\n0.07ms", '#2ecc71'),
    ("Core0\n(Fast)", "Agg3", "200M\n0.15ms", '#2ecc71'),
    
    ("Core1\n(Slow)", "Agg0", "200M\n0.12ms", '#e74c3c'),
    ("Core1\n(Slow)", "Agg1", "500M\n0.05ms", '#e74c3c'),
    ("Core1\n(Slow)", "Agg2", "300M\n0.08ms", '#e74c3c'),
    ("Core1\n(Slow)", "Agg3", "500M\n0.05ms", '#e74c3c'),
]

# Add Agg to Edge links
agg_edge = [
    ("Agg0", "Edge0"), ("Agg0", "Edge1"),
    ("Agg1", "Edge1"), ("Agg1", "Edge2"),
    ("Agg2", "Edge2"), ("Agg2", "Edge3"),
    ("Agg3", "Edge4"), ("Agg3", "Edge5")
]
for u, v in agg_edge:
    edges.append((u, v, "", '#bdc3c7'))

# Add Edge to Host links
edge_host = [
    ("Edge0", "h_0"), ("Edge0", "h_1"),
    ("Edge1", "h_2"), ("Edge1", "h_3"),
    ("Edge2", "h_4"), ("Edge2", "h_5"),
    ("Edge3", "h_6"), ("Edge3", "h_7"),
    ("Edge4", "h_8"), ("Edge4", "h_9"),
    ("Edge5", "h_10"), ("Edge5", "h_11")
]
for u, v in edge_host:
    edges.append((u, v, "", '#95a5a6'))

for u, v, lbl, color in edges:
    G.add_edge(u, v, label=lbl, color=color)

plt.figure(figsize=(14, 8))
edge_colors = [G[u][v]['color'] for u,v in G.edges()]

# Draw nodes
nx.draw(G, pos, with_labels=True, node_size=2000, node_color='#3498db', 
        font_size=9, font_weight='bold', font_color='white', edge_color=edge_colors, width=2)

# Draw edge labels (Only for Core links)
edge_labels = { (u,v): d['label'] for u,v,d in G.edges(data=True) if d['label'] != "" }
nx.draw_networkx_edge_labels(G, pos, edge_labels=edge_labels, font_color='#2c3e50', font_size=8, label_pos=0.6, bbox=dict(facecolor='white', edgecolor='none', alpha=0.7))

plt.title("Asymmetric SDN Fat-Tree Architecture (Medium Dataset)", fontsize=16, weight='bold', pad=20)
plt.axis('off')

out_dir = r"E:\Workspace\v2\cloudsimsdn-research\results\2026-04-18"
plt.savefig(os.path.join(out_dir, "fig0_architecture_medium.png"), dpi=300, bbox_inches='tight')
print("Medium Architecture schema generated successfully!")
