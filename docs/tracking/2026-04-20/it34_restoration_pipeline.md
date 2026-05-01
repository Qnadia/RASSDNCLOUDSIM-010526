# Itération 34 : Restauration du Pipeline de Simulation et Monitoring du Queuing Delay

**Date :** 20 Avril 2026  
**Auteur :** Antigravity / Nadia Qouhadhadh  
**Statut :** ✅ Pipeline Restauré — Build OK — Bug IT33 Résolus (P1) — Monitoring Opérationnel

---

## 🎯 Objectifs de l'itération

- Résoudre les erreurs de compilation massives causées par des désalignements d'API entre les exemples legacy et le core refactorisé.
- Restaurer la compatibilité des scripts d'expérimentation (`SimpleExampleSelectLinkBandwidth`).
- Valider le nouveau système de monitoring du **Queuing Delay** (délai de file d'attente).
- Produire les premières visualisations scientifiques basées sur les données réelles de délai.

---

## 🛠 Anomalies Détectées et Correctifs Techniques

### 1. WorkloadParser : Signature de Constructeur Incompatible
- **Anomalie** : Les scripts d'expérimentation utilisaient un constructeur `WorkloadParser(String, int, UtilizationModel, ...)` alors que la nouvelle version attendait trois modèles de ressources (CPU, RAM, BW).
- **Classe Modifiée** : `org.cloudbus.cloudsim.sdn.parsers.WorkloadParser`
- **Détail Technique** : Ajout d'un constructeur de compatibilité mappant l'unique `UtilizationModel` aux trois types de ressources internes.
- **Correctif additionnel** : Ajout de l'alias `parseNextWorkloadss()` (typo détectée dans certains scripts legacy).

### 2. SDNBroker : API Surface Incomplète
- **Anomalie** : Plusieurs méthodes de soumission de workload attendues par les exemples SSLAB étaient absentes ou privées.
- **Classe Modifiée** : `org.cloudbus.cloudsim.sdn.SDNBroker`
- **Détails Techniques** :
    - Restauration de `scheduleRequestt(WorkloadParser)` comme alias de `submitRequests`.
    - Implémentation de `submitRequests(String filename)` avec instanciation automatique du parser.
    - Ajout de `submitRequest(Request)` pour la soumission individuelle de requêtes.
    - Passage de `processWorkloadParser(WorkloadParser)` en `public` pour permettre son invocation par les entités externes.
    - Suppression des définitions en double au sein de la classe (dettes techniques).

### 3. NetworkOperatingSystem (NOS) : Visibilité des Métadonnées
- **Anomalie** : Les générateurs de topologie et les scripts d'allocation VM ne pouvaient plus accéder aux mappings VM/Flow car les méthodes étaient devenues d'instance au lieu de statiques.
- **Classe Modifiée** : `org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem`
- **Détail Technique** : Ajout de helpers statiques (`getVmNameToIdMap`, `getFlowNameToIdMap`, `getFlowIdToBandwidthMap`) agissant comme ponts vers l'instance singleton du NOS.

### 4. Erreurs de Compilation Legacy (Sources.txt)
- **Anomalie** : Le compilateur tentait de builder des fichiers obsolètes (`NetworkOperatingSystem_Old`, versions V2/V3 expérimentales) contenant des erreurs insolubles.
- **Action** : Mise en place d'une **stratégie de Whitelist** dans `sources.txt`.
### 5. Correctifs Critiques (Plan IT33)
- **Bug #1 : Tri des Workloads (SJF/Priority)**
    - **Anomalie** : Le tri était ignoré par l'ordonnanceur CloudSim.
    - **Classe Modifiée** : `org.cloudbus.cloudsim.sdn.SDNBroker`
    - **Correctif** : Activation de `sort()` et ajout de **micro-offsets temporels** (`1e-9s`) pour forcer l'ordre de priorité dans la file d'événements.
- **Bug #2 : Distinction LFF vs LWFF**
    - **Anomalie** : Placement identique au déploiement (runtime estimé à 0).
    - **Classes Modifiées** : `VmAllocationPolicyLWFFF`, `VmAllocationPolicyLWFFVD`
    - **Correctif** : Implémentation d'une **heuristique d'estimation** (`1000/MIPS`) et intégration du poids de charge de travail (`wWorkload=0.2`) dans le score de placement.
- **Bug #3 : Robustesse BwAlloc**
    - **Anomalie** : Absence de données sur les topologies saturées.
    - **Classe Modifiée** : `LinkSelectionPolicyBandwidthAllocation`
    - **Correctif** : Ajout de **logs diagnostiques** `[BwAlloc-WARN]` en cas de congestion > 90% pour identifier les points de rupture.

---

## 🔬 Validation Expérimentale

### Scénario de Test
- **Dataset** : `dataset-small`
- **Politiques** : `LWFF` (VM) + `DynLatBw` (Routing) + `SJF` (Workload)
- **Objectif** : Vérifier l'extraction des délais granulaires.

### Résultats du Monitoring (packet_delays.csv)
L'inspection du fichier produit confirme la capture des 4 composantes du délai :
| Composante | Valeur Moyenne (ms) | Description |
|---|---|---|
| **Proc** | ~500.00 | Temps de traitement VM |
| **Prop** | ~0.35 | Latence physique des liens |
| **Trans** | ~40.00 | Temps d'émission (psize/BW) |
| **Queue** | **~2680.14** | **Délai de congestion (nouveau)** |

---

## 📈 Visualisation Scientifique

Le script [plot_results.py](file:///e:/Workspace/v2/cloudsimsdn-research/tools/analysis/plot_results.py) a été enrichi d'une fonction `plot_packet_delay_breakdown_real` qui génère une décomposition en barres des délais réels observés.

**Figures produites dans `results/2026-04-20/plots_official/` :**
- `fig7_latency_breakdown_real.png` : Analyse de la congestion.
- `fig1_energy_per_host.png` : Profil de consommation.
- `fig6_uplink_utilization.png` : Charge réseau.

---

## 🚀 Prochaines Étapes
1. Relancer la **Campagne de Benchmarking Complète** (81 combinaisons) avec les correctifs de l'IT33 (tri effectif des workloads) et les nouveaux outils de visualisation.
2. Analyser l'impact du `Queuing Delay` sur la précision du routage `DynLatBw`.
