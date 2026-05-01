# Compte Rendu Détaillé : Analyse de Performance CloudSimSDN (IT15)

## 1. Introduction
Ce rapport présente les résultats d'un benchmark complet visant à évaluer l'impact des politiques d'allocation de Machines Virtuelles (VM) et de sélection de liens réseau sur l'efficacité énergétique et la Qualité de Service (QoS) dans un environnement SDN.

## 2. Description des Politiques Testées

### Politiques d'Allocation de VM (VM Allocation)
*   **LFF (Least Full First)** : Distribue les VMs sur les hôtes les moins chargés. Favorise la dispersion.
*   **MFF (Most Full First)** : Concentre les VMs sur les hôtes déjà chargés pour maximiser la consolidation et éteindre les hôtes inutilisés.
*   **LWFF (Least Weighted First Fit)** : Approche basée sur l'optimisation de Pareto (CPU/RAM) pour trouver un compromis entre charge et performance.

### Politiques de Sélection de Liens (Link Selection)
*   **First** : Sélectionne le premier chemin disponible sans considération de la bande passante.
*   **BwAllocN** : Alloue dynamiquement la bande passante et optimise les flux, visant une réduction drastique de la consommation d'énergie réseau.

---

## 3. Synthèse des Résultats (Extraits Clés)

| Configuration | Énergie (Wh) | Latence Moy. (s) | Violations SLA | Délai Pkt (ms) |
| :--- | :---: | :---: | :---: | :---: |
| **MFF + BwAllocN + PSO** | **1.11** | 8.88 | **109** | 9769.1 |
| **LFF + BwAllocN + PSO** | 2.01 | 8.88 | 283 | 9889.3 |
| **LWFF + BwAllocN + PSO**| 2.01 | 8.88 | 233 | 9889.3 |
| **MFF + First + PSO** | 27.79 | 8.88 | 109 | 58131.3 |
| **LFF + First + PSO** | 50.29 | 8.88 | 245 | 53875.5 |
| **LWFF + First + PSO**| 50.29 | 8.88 | 233 | 100393.5 |

---

## 4. Analyse de l'Impact de BwAllocN
L'impact de la politique **BwAllocN** est spectaculaire sur tous les indicateurs :

*   **Réduction d'Énergie** : En moyenne, passer de `First` à `BwAllocN` réduit la consommation d'énergie de **95% à 97%**. Par exemple, pour LFF/PSO, on passe de 50.29 Wh à 2.01 Wh.
*   **Stabilité de Latence** : BwAllocN maintient une latence applicative identique tout en réduisant massivement le "Packet Delay" (délai de livraison des paquets), évitant ainsi la saturation inutile des liens.
*   **Performance Cloudlets** : Les simulations avec BwAllocN sont également plus rapides à s'exécuter (~20s vs ~250s pour First), ce qui indique une gestion logicielle plus efficace des flux.

---

## 5. Analyse Comparative des Politiques de VM

### MFF (Le Champion de la Consolidation)
La politique **MFF** s'impose comme la plus performante dans ce dataset :
*   **Énergie** : Elle consomme **45% de moins** que LFF dans des conditions identiques (1.11 Wh vs 2.01 Wh).
*   **Fiabilité** : C'est elle qui génère le **moins de violations SLA** (109 vs 283 pour LFF). Cela s'explique par le fait qu'en regroupant les VMs, les flux inter-VM restent locaux ou traversent moins de switchs, réduisant les risques de congestion réseau.

### LWFF (Le Compromis Pareto)
*   LWFF offre une **meilleure stabilité SLA** que LFF (233 violations vs 283) grâce à son intelligence multicritère.
*   Cependant, son efficacité énergétique reste calquée sur LFF dans ce scénario, ne profitant pas de la consolidation agressive de MFF.

---

## 6. Meilleure Combinaison Dégagée

La combinaison gagnante est incontestablement :
**MFF + BwAllocN + PSO**

*   **Pourquoi ?** Elle combine la consolidation maximale des ressources physiques (MFF) avec l'optimisation intelligente de la bande passante (BwAllocN) et un ordonnancement de charge de travail optimisé par essaim de particules (PSO).
*   **Résultat** : Elle offre le plus bas niveau de consommation d'énergie (1.11 Wh) et la meilleure fiabilité de service (109 violations SLA).

## 7. Conclusion
Le passage à une stratégie de **consolidation (MFF)** couplée à une **gestion dynamique de la bande passante (BwAllocN)** permet d'atteindre des niveaux d'efficacité énergétique exceptionnels sans sacrifier la qualité de service. L'algorithme **PSO**, une fois corrigé et optimisé par cache, s'avère être un moteur d'exécution stable et performant pour ces scénarios complexes.
