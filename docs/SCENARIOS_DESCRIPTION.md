# Scénarios Expérimentaux / Experimental Scenarios

---

## 🇫🇷 Version Française

### 1. Description Générale

Ce chapitre présente les scénarios expérimentaux conçus pour évaluer l'impact des politiques de sélection de liens réseau dans un environnement SDN (Software-Defined Networking) simulé avec CloudSimSDN. L'objectif principal est de démontrer les avantages d'une politique **sensible à la bande passante et à la latence** (DynLatBw / BwAllocN) par rapport à une politique de sélection statique (First) qui choisit systématiquement le premier lien disponible sans considérer l'état du réseau.

Trois scénarios de taille croissante sont proposés, permettant d'observer l'émergence progressive des bénéfices des politiques intelligentes à mesure que la complexité et la charge du réseau augmentent.

### 2. Topologie Physique

Les trois scénarios utilisent une topologie **Fat-Tree** à trois niveaux (Core, Aggregation, Edge), caractérisée par :

- **Des chemins multiples** entre chaque paire d'hôtes, offrant des alternatives de routage.
- **Des liens hétérogènes** avec des bandes passantes variant de 1 à 5 Gbps et des latences de 0,03 à 0,15 ms.
- **Une structure hiérarchique** : chaque switch edge est connecté à 2 hôtes, les switches d'agrégation sont reliés à plusieurs switches edge, et les switches core assurent l'interconnexion globale.

| Paramètre | Small | Medium | Large |
|-----------|-------|--------|-------|
| Hôtes physiques | 6 | 12 | 20 |
| Switches core | 2 | 2 | 4 |
| Switches agrégation | 2 | 4 | 8 |
| Switches edge | 3 | 6 | 10 |
| Total nœuds | 13 | 24 | 42 |
| Liens réseau | 22 | 44 | 118 |
| Bande passante liens (Gbps) | 1 – 5 | 1 – 5 | 1 – 5 |
| Latence liens (ms) | 0,05 – 0,10 | 0,05 – 0,15 | 0,03 – 0,15 |

### 3. Topologie Virtuelle

Les machines virtuelles (VMs) modélisent une architecture d'application multi-tiers typique (Web → Application → Base de données → Services), avec des communications croisées entre les couches pour générer un trafic inter-hôte significatif.

| Paramètre | Small | Medium | Large |
|-----------|-------|--------|-------|
| VMs Web | 2 | 5 | 10 |
| VMs Application | 2 | 5 | 10 |
| VMs Base de données | 2 | 5 | 10 |
| VMs Services/Cache | 2 | 5 | 10 |
| **Total VMs** | **8** | **20** | **40** |
| Liens virtuels | 20 | 50 | 100 |
| RAM par VM (Mo) | 2048 – 4096 | 2048 – 4096 | 2048 – 4096 |
| MIPS par VM | 2000 – 4000 | 2000 – 4000 | 2000 – 4000 |
| BW demandé par VM (Mbps) | 200 – 500 | 200 – 500 | 200 – 500 |

Chaque VM Web communique avec 2 VMs Application, chaque VM Application communique avec 2 VMs Base de données, garantissant ainsi un trafic réseau traversant plusieurs switches et exploitant les chemins alternatifs de la topologie Fat-Tree.

### 4. Charge de Travail (Workload)

La charge de travail est constituée de requêtes réseau entre VMs, avec des tailles de paquets, des longueurs de cloudlet et des priorités variées.

| Paramètre | Small | Medium | Large |
|-----------|-------|--------|-------|
| Nombre de requêtes | 100 | 500 | 1 000 |
| Durée de simulation (s) | ~77 | ~204 | ~247 |
| Intervalle entre requêtes (s) | 0,5 – 1,0 | 0,2 – 0,6 | 0,1 – 0,4 |
| Taille des paquets (Mo) | 5 – 100 | 5 – 200 | 5 – 300 |
| Longueur cloudlet (MI) | 200 – 50 000 | 200 – 80 000 | 200 – 100 000 |
| Niveaux de priorité | 1 (haute), 2 (moyenne), 3 (basse) | idem | idem |

L'intervalle entre requêtes diminue avec la taille du scénario, créant une **congestion croissante** qui met en évidence les différences entre les politiques de sélection de liens.

### 5. Politiques Évaluées

#### Politiques de sélection de liens (Link Selection)
- **First** : Sélectionne le premier lien disponible dans la table de routage, sans considérer l'état du réseau. Sert de **baseline** (référence).
- **BwAllocN** : Sélectionne le lien avec la **bande passante libre la plus élevée**, permettant un équilibrage de charge dynamique.
- **DynLatBw** : Sélectionne le chemin optimisant conjointement la **latence minimale** et la **bande passante maximale**, en explorant tous les chemins alternatifs via un parcours DFS.

#### Politiques d'allocation de VMs (VM Allocation)
- **Spread** : Distribue les VMs sur un maximum d'hôtes (score de ressources libres le plus élevé).
- **BinPack** : Concentre les VMs sur un minimum d'hôtes (score de ressources libres le plus bas).
- **LFF** (Least Full First) : Place les VMs sur l'hôte le moins chargé.
- **MFF** (Most Full First) : Place les VMs sur l'hôte le plus chargé.

#### Politiques d'ordonnancement de flux (Workload Scheduling)
- **Priority** : Ordonne les flux par priorité (1 = haute).
- **SJF** (Shortest Job First) : Ordonne par longueur de cloudlet croissante.
- **FCFS** (First Come First Served) : Traite dans l'ordre d'arrivée.
- **RoundRobin** : Distribution circulaire.

### 6. Métriques Collectées

| Métrique | Fichier de sortie | Description |
|----------|------------------|-------------|
| Délai des paquets | `packet_delays.csv` | Latence end-to-end de chaque paquet (ms) |
| Violations QoS | `qos_violations.csv` | Paquets dépassant le seuil de latence SLA |
| Énergie des hôtes | `host_energy_total.csv` | Consommation énergétique totale (Wh) |
| Utilisation hôtes | `host_utilization.csv` | CPU, RAM, BW par hôte au cours du temps |
| Utilisation liens | `link_utilization_up/down.csv` | Débit sur chaque lien réseau |
| Latence de chemin | `path_latency_final.csv` | Latence calculée par chemin emprunté |
| Allocation VMs | `host_vm_allocation.csv` | Distribution des VMs sur les hôtes |
| Énergie switches | `sw_energy.csv` | Consommation des switches réseau |

### 7. Exécution des Expériences

Le simulateur est exécuté via la classe `SimpleExampleSelectLinkBandwidth` avec 5 arguments :

```
java SimpleExampleSelectLinkBandwidth <vmAlloc> <linkPolicy> <wfPolicy> <expName> <datasetDir>
```

| Argument | Description | Exemple |
|----------|-------------|--------|
| `vmAlloc` | Politique d'allocation VM | `Spread`, `Binpack`, `LFF`, `MFF` |
| `linkPolicy` | Politique de sélection de lien | `First`, `BwAllocN`, `DynLatBw` |
| `wfPolicy` | Politique d'ordonnancement | `Priority`, `SJF`, `FCFS` |
| `expName` | Nom du dossier de sortie (optionnel, `null` = auto) | `null` |
| `datasetDir` | Dossier du dataset (optionnel) | `dataset-small`, `dataset-medium`, `dataset-large` |

**Exemples :**
```bash
# Scénario small, Spread + BwAllocN + Priority
java ... SimpleExampleSelectLinkBandwidth Spread BwAllocN Priority null dataset-small

# Scénario large, LFF + DynLatBw + SJF
java ... SimpleExampleSelectLinkBandwidth LFF DynLatBw SJF null dataset-large
```

### 8. Organisation des Résultats

Les résultats sont automatiquement organisés par **date** et par **scénario** :

```
results/
  YYYY-MM-DD/
    dataset-small/
      experiment_Spread_First_Priority/
        packet_delays.csv, host_energy_total.csv, ...
      experiment_Spread_BwAllocN_Priority/
        ...
      figures_consolidated/
        fig1_energy_comparison.pdf
        fig2_latency_comparison.pdf
        fig3_utilization_comparison.pdf
        fig4_sla_satisfaction.pdf
        fig5_vm_pressure.pdf
        fig6_tradeoff_energy_latency.pdf
        consolidated_summary.csv
    dataset-medium/
      ...
    dataset-large/
      ...
```

Le rapport consolidé (`consolidated_report.py`) génère automatiquement les figures comparatives **par dataset**, permettant de comparer visuellement les politiques pour chaque taille de scénario.

---

## 🇬🇧 English Version

### 1. General Description

This chapter presents the experimental scenarios designed to evaluate the impact of network link selection policies in a simulated SDN (Software-Defined Networking) environment using CloudSimSDN. The primary objective is to demonstrate the advantages of a **bandwidth and latency-aware** policy (DynLatBw / BwAllocN) compared to a static selection policy (First) that systematically selects the first available link without considering the network state.

Three scenarios of increasing size are proposed, allowing observation of the progressive emergence of benefits from intelligent policies as network complexity and load increase.

### 2. Physical Topology

All three scenarios use a **Fat-Tree** topology with three levels (Core, Aggregation, Edge), characterized by:

- **Multiple paths** between each pair of hosts, providing routing alternatives.
- **Heterogeneous links** with bandwidths ranging from 1 to 5 Gbps and latencies from 0.03 to 0.15 ms.
- **Hierarchical structure**: each edge switch connects to 2 hosts, aggregation switches connect to multiple edge switches, and core switches ensure global interconnection.

| Parameter | Small | Medium | Large |
|-----------|-------|--------|-------|
| Physical hosts | 6 | 12 | 20 |
| Core switches | 2 | 2 | 4 |
| Aggregation switches | 2 | 4 | 8 |
| Edge switches | 3 | 6 | 10 |
| Total nodes | 13 | 24 | 42 |
| Network links | 22 | 44 | 118 |
| Link bandwidth (Gbps) | 1 – 5 | 1 – 5 | 1 – 5 |
| Link latency (ms) | 0.05 – 0.10 | 0.05 – 0.15 | 0.03 – 0.15 |

### 3. Virtual Topology

Virtual machines (VMs) model a typical multi-tier application architecture (Web → Application → Database → Services), with cross-layer communications to generate significant inter-host traffic.

| Parameter | Small | Medium | Large |
|-----------|-------|--------|-------|
| Web VMs | 2 | 5 | 10 |
| Application VMs | 2 | 5 | 10 |
| Database VMs | 2 | 5 | 10 |
| Service/Cache VMs | 2 | 5 | 10 |
| **Total VMs** | **8** | **20** | **40** |
| Virtual links | 20 | 50 | 100 |
| RAM per VM (MB) | 2048 – 4096 | 2048 – 4096 | 2048 – 4096 |
| MIPS per VM | 2000 – 4000 | 2000 – 4000 | 2000 – 4000 |
| Requested BW per VM (Mbps) | 200 – 500 | 200 – 500 | 200 – 500 |

Each Web VM communicates with 2 Application VMs, each Application VM communicates with 2 Database VMs, ensuring network traffic traverses multiple switches and exploits the alternative paths of the Fat-Tree topology.

### 4. Workload

The workload consists of network requests between VMs, with varying packet sizes, cloudlet lengths, and priorities.

| Parameter | Small | Medium | Large |
|-----------|-------|--------|-------|
| Number of requests | 100 | 500 | 1,000 |
| Simulation duration (s) | ~77 | ~204 | ~247 |
| Inter-request interval (s) | 0.5 – 1.0 | 0.2 – 0.6 | 0.1 – 0.4 |
| Packet size (MB) | 5 – 100 | 5 – 200 | 5 – 300 |
| Cloudlet length (MI) | 200 – 50,000 | 200 – 80,000 | 200 – 100,000 |
| Priority levels | 1 (high), 2 (medium), 3 (low) | same | same |

The inter-request interval decreases with scenario size, creating **increasing congestion** that highlights differences between link selection policies.

### 5. Evaluated Policies

#### Link Selection Policies
- **First**: Selects the first available link in the routing table without considering network state. Serves as the **baseline**.
- **BwAllocN**: Selects the link with the **highest available bandwidth**, enabling dynamic load balancing.
- **DynLatBw**: Selects the path optimizing both **minimum latency** and **maximum bandwidth**, exploring all alternative paths via DFS traversal.

#### VM Allocation Policies
- **Spread**: Distributes VMs across the maximum number of hosts (highest free resource score).
- **BinPack**: Consolidates VMs on the minimum number of hosts (lowest free resource score).
- **LFF** (Least Full First): Places VMs on the least loaded host.
- **MFF** (Most Full First): Places VMs on the most loaded host.

#### Workload Scheduling Policies
- **Priority**: Orders flows by priority (1 = highest).
- **SJF** (Shortest Job First): Orders by ascending cloudlet length.
- **FCFS** (First Come First Served): Processes in arrival order.
- **RoundRobin**: Circular distribution.

### 6. Collected Metrics

| Metric | Output file | Description |
|--------|------------|-------------|
| Packet delays | `packet_delays.csv` | End-to-end latency per packet (ms) |
| QoS violations | `qos_violations.csv` | Packets exceeding SLA latency threshold |
| Host energy | `host_energy_total.csv` | Total energy consumption (Wh) |
| Host utilization | `host_utilization.csv` | CPU, RAM, BW per host over time |
| Link utilization | `link_utilization_up/down.csv` | Throughput on each network link |
| Path latency | `path_latency_final.csv` | Computed latency per path taken |
| VM allocation | `host_vm_allocation.csv` | VM distribution across hosts |
| Switch energy | `sw_energy.csv` | Network switch energy consumption |

### 7. Running Experiments

The simulator is executed via the `SimpleExampleSelectLinkBandwidth` class with 5 arguments:

```
java SimpleExampleSelectLinkBandwidth <vmAlloc> <linkPolicy> <wfPolicy> <expName> <datasetDir>
```

| Argument | Description | Example |
|----------|-------------|--------|
| `vmAlloc` | VM allocation policy | `Spread`, `Binpack`, `LFF`, `MFF` |
| `linkPolicy` | Link selection policy | `First`, `BwAllocN`, `DynLatBw` |
| `wfPolicy` | Workload scheduling policy | `Priority`, `SJF`, `FCFS` |
| `expName` | Output folder name (optional, `null` = auto) | `null` |
| `datasetDir` | Dataset directory (optional) | `dataset-small`, `dataset-medium`, `dataset-large` |

**Examples:**
```bash
# Small scenario, Spread + BwAllocN + Priority
java ... SimpleExampleSelectLinkBandwidth Spread BwAllocN Priority null dataset-small

# Large scenario, LFF + DynLatBw + SJF
java ... SimpleExampleSelectLinkBandwidth LFF DynLatBw SJF null dataset-large
```

### 8. Results Organization

Results are automatically organized by **date** and **scenario**:

```
results/
  YYYY-MM-DD/
    dataset-small/
      experiment_Spread_First_Priority/
        packet_delays.csv, host_energy_total.csv, ...
      experiment_Spread_BwAllocN_Priority/
        ...
      figures_consolidated/
        fig1_energy_comparison.pdf
        fig2_latency_comparison.pdf
        fig3_utilization_comparison.pdf
        fig4_sla_satisfaction.pdf
        fig5_vm_pressure.pdf
        fig6_tradeoff_energy_latency.pdf
        consolidated_summary.csv
    dataset-medium/
      ...
    dataset-large/
      ...
```

The consolidated report (`consolidated_report.py`) automatically generates comparative figures **per dataset**, enabling visual comparison of policies for each scenario size.

