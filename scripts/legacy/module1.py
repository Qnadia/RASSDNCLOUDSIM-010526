import matplotlib.pyplot as plt
import networkx as nx

# Exemple de topologie simplifiée
physical_topology = {
    "nodes": [
        {"name": "c", "type": "core"},
        {"name": "e0", "type": "edge"},
        {"name": "e1", "type": "edge"},
        {"name": "h_0_0", "type": "host"},
        {"name": "h_0_1", "type": "host"},
        {"name": "h_1_0", "type": "host"}
    ],
    "links": [
        {"source": "c", "destination": "e0", "bw": 1000000000},
        {"source": "e0", "destination": "h_0_0", "bw": 500000000},
        {"source": "e0", "destination": "h_0_1", "bw": 500000000},
        {"source": "c", "destination": "e1", "bw": 1000000000},
        {"source": "e1", "destination": "h_1_0", "bw": 500000000}
    ]
}

# Créer le graphe
G = nx.DiGraph()

# Ajouter les nœuds
for node in physical_topology["nodes"]:
    G.add_node(node["name"], type=node["type"])

# Ajouter les liens
for link in physical_topology["links"]:
    G.add_edge(link["source"], link["destination"], bw=link["bw"])

# Dessiner le graphe
pos = nx.spring_layout(G)
edge_labels = nx.get_edge_attributes(G, "bw")

nx.draw(G, pos, with_labels=True, node_size=3000, node_color="lightblue")
nx.draw_networkx_edge_labels(G, pos, edge_labels=edge_labels)

plt.title("Topologie Réseau")
plt.show()
