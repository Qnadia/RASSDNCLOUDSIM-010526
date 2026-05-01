# 🕸️ Perspective 3 : Routage Intelligent par Graph Neural Networks (GNN)

## 1. Description Détaillée
Les réseaux SDN sont naturellement modélisables sous forme de graphes. Un **GNN** peut apprendre à mapper des flux de données complexes sur des chemins physiques en tenant compte de la topologie Fat-Tree entière, là où `BwAllocN` ou `DynLatBw` n'ont qu'une vision locale ou heuristique.

## 2. Composantes à Modifier
### `org.cloudbus.cloudsim.sdn.policies.selectlink`
- **`GNNLinkSelectionPolicy.java` (Nouveau)** : Utiliser une représentation en matrice de contiguïté de la topologie physique.
- **`LinkSelectionPolicyDynamicLatencyBw.java`** : Utiliser le GNN pour prioriser les chemins dans la recherche DFS.

### `org.cloudbus.cloudsim.sdn`
- **`PhysicalTopology.java`** : Ajouter des méthodes pour exporter la structure du graphe au format JSON pour l'inférence AI.

## 3. Impact au niveau du Code
1. **Graph Embedding** : Conversion de l'état des liens (BW disponible, latence actuelle) en vecteurs de caractéristiques pour les nœuds du graphe.
2. **Inference Latency** : Attention particulière à ne pas ajouter trop de délai de calcul au contrôleur SDN simulé.

## 4. Métriques à Suivre
- **Path Optimality** : Comparaison entre le chemin GNN et le chemin théorique optimal (Dijkstra).
- **Network Throughput** : Augmentation du débit global du datacenter par une meilleure distribution des flux.
- **Load Balancing (Variance)** : Réduction de la variance de charge entre les différents switchs core et aggregation.
