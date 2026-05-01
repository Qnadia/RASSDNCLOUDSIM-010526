Viewed it33_diagnostic_resultats_campagne.md:302-315
Searched for "DynLatBw"
Viewed SimpleExampleSelectLinkBandwidth.java:1-563
Viewed LinkSelectionPolicyDijkstra.java:1-168
Viewed LinkSelectionPolicyDynamicLatencyBw.java:1-503

Dijkstra (souvent écrit "Diskistra" par erreur) est l'algorithme de référence pour trouver le plus court chemin dans un graphe. Dans votre projet CloudSimSDN, il est au cœur de la politique de routage **`DynLatBw`** (Dynamic Latency Bandwidth).

Voici une explication de son fonctionnement théorique et de son implémentation concrète dans votre code (notamment dans `LinkSelectionPolicyDynamicLatencyBw.java`).

### 1. Le concept de Dijkstra dans un SDN
L'algorithme classique cherche le chemin avec le poids total minimal. Dans un réseau SDN "intelligent" comme le vôtre, le **"poids"** d'un lien n'est pas une valeur fixe (comme la distance), mais une **valeur dynamique** qui représente le coût en temps (latence) pour traverser ce lien à un instant T.

### 2. Implémentation dans le code (`getShortestDynamicPath`)

L'implémentation se trouve principalement dans la méthode `getShortestDynamicPath` de la classe `LinkSelectionPolicyDynamicLatencyBw.java` (lignes 227-297) :

#### A. Initialisation (Lignes 228-237)
L'algorithme utilise trois structures de données clés :
*   **`distances` (Map)** : Stocke la latence minimale trouvée pour atteindre chaque nœud (initialisée à l'infini).
*   **`pq` (PriorityQueue)** : Une file de priorité qui extrait toujours le nœud ayant la plus petite latence actuelle à explorer.
*   **`previousLink` / `previousNode`** : Pour reconstruire le chemin final une fois la destination atteinte.

#### B. La fonction de coût "Intelligente" (Lignes 263-274)
C'est ici que réside l'innovation de votre recherche. Le poids (`weight`) d'un lien est calculé dynamiquement :
```java
double weight = linkLatency + propDelay + txDelay + dqueueEst;
```
1.  **`linkLatency`** : Délai fixe du switch.
2.  **`propDelay`** : Délai de propagation physique sur le câble.
3.  **`txDelay`** : Temps d'émission du paquet (dépend de la taille du paquet et de la bande passante).
4.  **`dqueueEst` (Le modèle M/M/1)** : C'est l'estimation du délai d'attente en file d'attente. 
    *   Si le lien est saturé (utilisation proche de 100%), `dqueueEst` tend vers l'infini.
    *   **Conséquence** : Dijkstra "évitera" naturellement les liens congestionnés pour préférer des chemins plus longs physiquement mais plus rapides temporellement.

#### C. Exploration et Relaxation (Lignes 242-284)
L'algorithme explore les voisins de chaque nœud. Si un chemin via un nœud `u` vers un voisin `v` est plus court (moins de latence totale) que ce qui était connu, on met à jour la distance et on ajoute `v` à la file de priorité.

#### D. Reconstruction du chemin (Lignes 287-296)
Une fois que la destination (`dest`) est extraite de la file, l'algorithme remonte les pointeurs `previousNode` pour retourner la liste ordonnée des liens (`List<Link>`) que le paquet doit suivre.

### 3. Pourquoi c'est important pour vos résultats ?
Dans votre rapport scientifique (**IT33/IT35**), vous observez que **DynLatBw** réduit les violations de SLA. C'est précisément parce que l'implémentation de Dijkstra dans le code recalcul de manière "myope" (au sens mathématique mais global au sens réseau) le meilleur chemin en évitant les bouchons grâce au calcul dynamique du poids des liens.

**Fichier à consulter pour voir le code :**
[LinkSelectionPolicyDynamicLatencyBw.java](file:///e:/Workspace/v2/cloudsimsdn-research/src/main/java/org/cloudbus/cloudsim/sdn/policies/selectlink/LinkSelectionPolicyDynamicLatencyBw.java#L227-L297)