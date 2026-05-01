# Configuration du Dataset Medium (Congested)

## 1. Topologie Physique
Le réseau physique du dataset Medium est une topologie de type **Fat-Tree asymétrique** comprenant 12 serveurs et des liens de capacités variables.

### Table 1: Physical Hosts (Serveurs)
| Host Profile | Quantité | CPU (MIPS) | RAM (MB) | Storage (MB) | BW (Gbps) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Standard Host** | 12 | 16 x 4000 | 32768 | 10000000 | 1 |

### Table 2: Network Links (Liens Réseaux)
L'asymétrie est définie au niveau des liens de type "Core". Le réseau possède de multiples liens d'agrégation avec des capacités asymétriques pour simuler des goulots d'étranglement complexes.

| Link Type | Bandwidth | Latency (ms) |
| :--- | :--- | :--- |
| **Core Links** | 200 Mbps à 500 Mbps | 0.05 ms à 0.15 ms |
| **Aggregation Links** | 100 Mbps à 200 Mbps | 0.05 ms à 0.10 ms |
| **Edge-Host Links** | 100 Mbps | 0.05 ms |

## 2. Virtual Topology (VMs)
L'environnement virtuel consiste en une architecture d'application multi-tiers.

### Table 3: Virtual Machine Profiles
*(Total: 20 VMs réparties en 4 niveaux)*

| VM Tier | Instances | vCPU (MIPS) | RAM (MB) | BW (Mbps) |
| :--- | :--- | :--- | :--- | :--- |
| **Web** | 5 | 2 x 2000 | 2048 | 200 |
| **App** | 5 | 2 x 2000 | 2048 | 300 |
| **DB** | 5 | 4 x 4000 | 4096 | 500 |
| **Cache/Svc** | 5 | 2 x 2000 | 2048 | 400 |

## 3. Workload Configuration (Trafic Réseau)
Le trafic réseau généré lors de la simulation s'appuie sur un fichier de requêtes modélisant un flux de données applicatif réaliste à grande échelle.
- **Modèle de Communication** : Chaîne de requêtes-réponses Multi-tiers (ex: Web -> App -> DB -> Cache).
- **Intensité et Durée** : Le workload simule un pic d'activité (Burst) avec de multiples flux de données lourds (plusieurs centaines de Mégaoctets par requête) échangés de manière asynchrone, jusqu'à la fin de la simulation fixée à `t=300.0s`.
- **Saturation Ciblée** : Cette intensité a été délibérément calibrée pour forcer une saturation réseau (taux d'occupation ρ > 0.8) sur les multiples liens asymétriques, déclenchant ainsi des phénomènes de files d'attente (Queuing Delay) observables.
- **Ordonnancement** : L'envoi des paquets utilise la stratégie Priority-aware (`PriorityWorkloadScheduler`).

## 4. Matrice d'Évaluation
L'ensemble des scénarios évalue l'algorithme `LinkSelectionPolicyDynamicLatencyBw` (basé sur le modèle prédictif M/M/1) face aux approches traditionnelles de la littérature, en faisant varier la politique de placement initial des VMs.

Les 3 politiques de placement (VM Allocation) testées sont :
- **MFF-P (Most Full First)** : Priorité à la consolidation (regroupement des VMs sur le minimum de serveurs physiques).
- **LFF-P (Least Full First)** : Priorité à l'équilibrage de charge (dispersion des VMs sur tout le Datacenter).
- **LWFF-P (Least Weighted First Fit)** : Placement hybride basé sur la charge de travail attendue.

Chacun de ces 3 scénarios de placement est croisé avec 4 algorithmes de routage (Link Selection) :
1. `First` (Approche naïve, premier lien disponible).
2. `BwAlloc` (Approche IntServ, réservation stricte de bande passante).
3. `Dijkstra` (Approche théorique optimale basée sur le graphe statique).
4. **`DynamicLatencyBw`** (Notre proposition dynamique intégrant la latence des files d'attente M/M/1).
