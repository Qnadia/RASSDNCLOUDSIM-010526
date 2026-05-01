# IT31 : Optimisation du Routage SDN (Programmation Dynamique & Map-Reduce)

**Date :** 18 Avril 2026
**Statut :** En attente de validation.

**Objectif :** Résoudre l'explosion combinatoire $O(|V|!)$ du routage SDN sur les grandes topologies (Fat-Tree) en remplaçant la recherche exhaustive (DFS) par un algorithme de Plus Court Chemin (Dijkstra) appliqué sur des tenseurs de poids dynamiques, couplé à une parallélisation Map-Reduce du traitement des Workloads.

---

## 1. Formalisation Mathématique

### 1.1. Modèle Matriciel du Réseau
Soit $G = (V, E)$ le graphe du réseau.
*   **Capacité $C \in \mathbb{R}^{|V| \times |V|}$** : $C_{i,j}$ est la bande passante physique du lien $(i,j)$.
*   **Trafic Alloué $\Lambda \in \mathbb{R}^{|V| \times |V|}$** : $\Lambda_{i,j}$ est la somme du trafic actif sur le lien $(i,j)$.

### 1.2. Poids Dynamique (File M/M/1)
Le temps de traversée d'un lien $(i,j)$ dépend du délai de propagation statique $d_{i,j}$ et du délai de file d'attente (modèle M/M/1). Nous construisons une **Matrice de Poids Dynamiques $W(t)$** :

$$ W_{i,j}(t) = \begin{cases} d_{i,j} + \frac{1}{C_{i,j} - \Lambda_{i,j}(t)} & \text{si } C_{i,j} > \Lambda_{i,j}(t) \\ \infty & \text{sinon (Congestion absolue)} \end{cases} $$

### 1.3. Programmation Dynamique (Dijkstra)
Plutôt que d'explorer tous les chemins (DFS) et calculer leurs délais un par un, on calcule le chemin qui minimise $\sum W_{i,j}$. L'algorithme de Dijkstra (en $O(|V| \log |V|)$) remplace la recherche en profondeur.

---

## 2. Le Défi Technologique dans CloudSim

**Le problème d'origine :** Le moteur de base de CloudSim traite chaque événement (`SimEvent`) de manière rigoureusement **séquentielle**. Si $1000$ workloads démarrent à $t=0.0$, CloudSim va les traiter un par un. Le routage de l'un bloquant le routage de l'autre, et un simple `parallelStream()` sur l'envoi des événements corromprait la file d'attente globale de la simulation (provoquant des crashs de concurrence).

**La solution : L'architecture Map-Reduce sans blocage**
Il faut isoler la phase de calcul mathématique lourde (Dijkstra) pour la paralléliser, tout en gardant l'application des effets de bord (création de VMs, envoi de données) strictement séquentielle.

---

## 3. Workflow d'Exécution Détaillé (L'implémentation)

### A. Classe `CloudSimTagsSDN.java`
*   **Modification :** Ajout d'un nouveau tag système `WORKLOAD_BATCH_SUBMIT`.
*   **Rôle :** Permet de différencier une soumission isolée d'une soumission groupée (batch).

### B. Classe `SDNBroker.java` (L'Émetteur)
*   **Modification :** Refactorisation de la méthode `submitRequests`.
*   **Workflow :**
    1. Au lieu de boucler sur les requêtes et d'envoyer un événement pour chacune, le broker regroupe les `Workload` par date de démarrage (`time`) en utilisant l'API Stream Java `Collectors.groupingBy`.
    2. Pour chaque groupe temporel, il invoque la nouvelle méthode `scheduleWorkloadBatch`.
    3. Cette méthode envoie **un seul événement** au Datacenter de type `WORKLOAD_BATCH_SUBMIT`, contenant toute la liste (le batch) des requêtes simultanées.

### C. Classe `Request.java` (Le Conteneur)
*   **Modification :** Ajout d'un attribut `private List<Link> precalculatedPath` avec ses accesseurs.
*   **Rôle :** Sert de cache "Thread-Safe" propre à chaque requête pour sauvegarder le résultat du routage parallèle.

### D. Classe `SDNDatacenter.java` (Le Noyau de Calcul)
*   **Modification :** Ajout de la méthode `processWorkloadBatchSubmit` appelée par le moteur d'événements.
*   **Workflow Map-Reduce :**
    1. **Phase MAP (Parallèle) :** 
       * La méthode lance un `batch.parallelStream().forEach(...)` qui exploite 100% des cœurs CPU de la machine physique.
       * Chaque thread extrait un workload, résout les adresses IP (Hôte source et Hôte destination) sans faire aucune modification d'état global.
       * Il invoque la stratégie de routage `LinkSelectionPolicyDynamicLatencyBw.findBestPath(...)` (Le Dijkstra Dynamique basé sur M/M/1).
       * Il sauvegarde le chemin résultant dans le conteneur de la requête : `req.setPrecalculatedPath(...)`.
    2. **Phase REDUCE (Séquentielle) :** 
       * Une fois tous les threads terminés, on utilise une boucle standard (`for (Workload wl : batch)`).
       * Pour chaque workload, on appelle la méthode classique `processWorkloadSubmit`.
       * **Avantage :** L'intégrité de la simulation CloudSim est préservée. Toutes les allocations de CPU, créations de `Cloudlet` et soumissions d'événements futurs sont effectuées de manière sûre sur le thread principal.
       * **La magie :** Au lieu de faire le Dijkstra séquentiellement, l'ancienne méthode récupère simplement le résultat instantanément via `req.getPrecalculatedPath()`.

---

## 4. Bilan

*   **Algorithmique :** Passage de exponentiel à polynomial grâce à Dijkstra + modèle M/M/1.
*   **Infrastructure :** Passage d'un routage séquentiel engorgé à un calcul Map-Reduce 100% multi-thread.
*   **Stabilité :** Zéro verrou ou Mutex ajouté, préservation totale du cœur événementiel de CloudSim.

### Validation Requise
Le code a été compilé avec succès et la sauvegarde des anciennes politiques est effectuée dans `backup_mapreduce_prep`. Ce workflow mathématique et logiciel répond-il à vos exigences avant d'exécuter les tests de performances réels ?
