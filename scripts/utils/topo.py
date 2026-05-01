import json
import networkx as nx
import matplotlib.pyplot as plt

# Charger les données JSON
with open("./dataset-energy/energy-physicalH10.json", "r") as f:
    physical_topology = json.load(f)
    
with open("./1energy-virtualV40.json", "r") as f:
    vm_topology = json.load(f)

with open("./1energy-workload40.csv", "r") as f:
    workloads = f.readlines()

# Créer le graphe pour la topologie physique
physical_graph = nx.DiGraph()
for node in physical_topology["nodes"]:
    physical_graph.add_node(node["name"], type=node["type"], bw=node.get("bw", 0))

for link in physical_topology["links"]:
    physical_graph.add_edge(link["source"], link["destination"], bw=link["upBW"], latency=link["latency"])

# Créer le graphe pour la topologie virtuelle
vm_graph = nx.DiGraph()
for node in vm_topology["nodes"]:
    vm_graph.add_node(node["name"], type=node["type"], host=node["host"])

for link in vm_topology["links"]:
    vm_graph.add_edge(link["source"], link["destination"], bw=link["bandwidth"])

# Ajouter les liens VM vers hôtes physiques
for node in vm_topology["nodes"]:
    vm_host = node["host"]
    if vm_host in physical_graph.nodes:
        physical_graph.add_edge(vm_host, node["name"], type="host-vm")
        physical_graph.add_edge(node["name"], vm_host, type="vm-host")

# Dessiner la topologie physique
plt.figure(figsize=(12, 8))
pos = nx.spring_layout(physical_graph)
nx.draw(
    physical_graph,
    pos,
    with_labels=True,
    node_size=2000,
    node_color="lightblue",
    font_size=10,
)
nx.draw_networkx_edge_labels(
    physical_graph,
    pos,
    edge_labels=nx.get_edge_attributes(physical_graph, "bw"),
    font_size=8,
)
plt.title("Topologie physique")
plt.show()

# Dessiner la topologie virtuelle
plt.figure(figsize=(12, 8))
pos = nx.spring_layout(vm_graph)
nx.draw(
    vm_graph,
    pos,
    with_labels=True,
    node_size=2000,
    node_color="lightgreen",
    font_size=10,
)
nx.draw_networkx_edge_labels(
    vm_graph,
    pos,
    edge_labels=nx.get_edge_attributes(vm_graph, "bw"),
    font_size=8,
)
plt.title("Topologie virtuelle (VMs)")
plt.show()
