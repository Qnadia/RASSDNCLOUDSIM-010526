# Experimental Setup - Dataset Small (Congested Asymmetric)

## 1. Physical Infrastructure
The physical infrastructure is a Software-Defined Data Center (SDDC) topology with 7 switches and 6 hosts.

### Table 1: Physical Resource Characteristics
| Component | Type | CPU (MIPS) | RAM (GB) | BW (Mbps) |
| :--- | :--- | :--- | :--- | :--- |
| **Host (h0-h5)** | Host | 16 x 4000 | 16 | 2000 |
| **Core Switch** | Core | - | - | 600 |
| **Agg Switch** | Aggregate | - | - | 500 |
| **Edge Switch** | Edge | - | - | 300 |

### Table 2: Physical Link Asymmetry (Core-Aggregate)
| Link | Bandwidth (Mbps) | Latency (ms) | Distance (m) |
| :--- | :--- | :--- | :--- |
| **Path A (Best)** | 300 | 0.05 | 500 |
| **Path B (Slow)** | 80 | 0.10 | 1000 |

## 2. Virtual Topology (VMs)
The virtual environment consists of an N-tier application architecture.

### Table 3: Virtual Machine Profiles
*(Total: 8 VMs réparties en 4 niveaux)*

| VM Tier | Instances | vCPU (MIPS) | RAM (MB) | BW (Mbps) |
| :--- | :--- | :--- | :--- | :--- |
| **Web** | 2 | 2 x 2000 | 2048 | 200 |
| **App** | 2 | 2 x 2000 | 2048 | 300 |
| **DB** | 2 | 4 x 4000 | 4096 | 500 |
| **Cache** | 2 | 2 x 2000 | 2048 | 400 |

## 3. Workload Configuration (Trafic Réseau)
Le trafic réseau généré lors de la simulation s'appuie sur un fichier de requêtes modélisant un flux de données applicatif réaliste.
- **Modèle de Communication** : Chaîne de requêtes-réponses Multi-tiers (ex: Web -> App -> DB -> Cache).
- **Intensité et Durée** : Le workload simule un pic d'activité (Burst) avec de multiples flux de données lourds (plusieurs centaines de Mégaoctets par requête) échangés de manière asynchrone, jusqu'à la fin de la simulation fixée à `t=90.0s`.
- **Saturation Ciblée** : Cette intensité a été délibérément calibrée pour forcer une saturation réseau (taux d'occupation ρ > 0.8) sur le lien asymétrique de 80 Mbps de la couche Core, déclenchant ainsi des phénomènes de files d'attente (Queuing Delay) observables.
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
