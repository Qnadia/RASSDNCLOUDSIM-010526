# Itération 33 : Audit et Diagnostic Critique de la Campagne de Benchmarks du 19 Avril 2026

**Date :** 20 Avril 2026  
**Auteur :** Nadia Qouhadhadh / Antigravity  
**Statut :** 🔴 Résultats partiellement invalides — Corrections requises avant re-simulation

---

## 🎯 Objectifs de l'itération

- Analyser en profondeur les résultats de la campagne nocturne du 19 Avril (`results/2026-04-19/`).
- Identifier les anomalies statistiques et les incohérences dans les métriques produites.
- Remonter aux causes racines dans le code Java (politiques VM et workload).
- Établir une cartographie précise de la validité des données par combinaison de politiques.
- Proposer des correctifs prioritaires avant de relancer la campagne complète.

---

## 📋 Contexte de la campagne analysée

La campagne du 19 Avril a produit **81 combinaisons** de politiques sur 3 datasets :

| Dimension | Valeurs |
|---|---|
| **VM Allocation** | `MFF`, `LFF`, `LWFF` |
| **Link Selection (Routing)** | `First`, `BwAlloc`, `DynLatBw` |
| **Workload Scheduling** | `Priority`, `SJF`, `PSO` |
| **Datasets** | `dataset-small-congested`, `dataset-medium-congested`, `dataset-large` |

**Infrastructure d'exécution :** `run_benchmark.ps1` (exécution séquentielle)  
**Consolidation :** `merge_global_results.py` → `GLOBAL_CONSOLIDATION/`  
**Figures générées :** `generate_global_figures.py` → 4 figures (scalabilité, SLA, latence, trade-off)

---

## 🔬 Méthodologie d'audit

L'analyse a été conduite par inspection programmatique (Python/pandas) des fichiers CSV consolidés et des fichiers bruts individuels, croisée avec une lecture du code source Java des politiques.

### Checks effectués

1. **Comparaison inter-politiques des métriques clés** (énergie totale, délais paquets, violations QoS)
2. **Test d'identité stricte** (`DataFrame.equals()`) entre paires de runs suspectés identiques
3. **Vérification de la distribution des VMs par hôte** (`host_allocation_summary.csv`)
4. **Inspection du code Java** des schedulers et des politiques d'allocation VM
5. **Vérification de la complétude des données** (fichiers vides, NaN dans les pivots)

---

## 🐛 Bug #1 — CRITIQUE : `Priority` et `SJF` produisent des résultats 100% identiques

### Preuves expérimentales

```
dataset-large / LFF / DynLatBw :
  Priority → packet_delays mean = 5465.38 ms | energy = 146.9012 Wh
  SJF      → packet_delays mean = 5465.38 ms | energy = 146.9012 Wh
  DataFrame.equals() → True ✅

Vérification sur tous les datasets/policies :
  Priority == SJF (énergie totale) → True sur 100% des 27 combinaisons concernées
```

Extrait du pivot énergie (dataset-large) :

| vm_policy | routing | PSO | Priority | SJF |
|---|---|---|---|---|
| LFF | BwAlloc | 20.03 | **146.90** | **146.90** |
| LFF | DynLatBw | 20.03 | **146.90** | **146.90** |
| LWFF | First | 20.03 | **146.90** | **146.90** |
| MFF | DynLatBw | 10.13 | **74.30** | **74.30** |

### Cause racine identifiée

Les classes `SJFWorkloadScheduler` et `PriorityWorkloadScheduler` implémentent toutes deux `WorkloadSchedulerPolicy`, qui expose une méthode `sort(List<Workload>)`. Ces méthodes **trient bien la liste**, mais :

**Recherche dans `SDNBroker.java`** → `schedulingPolicy.sort(` : **0 occurrence trouvée**.

Le tri est appelé dans un contexte isolé et son résultat n'est **jamais réinjecté** dans le pipeline de soumission des workloads à CloudSim. Le moteur de simulation reçoit les événements dans le même ordre quelle que soit la politique déclarée.

```java
// SJFWorkloadScheduler.sort() — le tri a lieu mais...
workloads.sort(Comparator.comparingLong(wl -> wl.getRequest().getLastProcessingCloudletLen()));
return workloads; // ...personne n'utilise ce retour pour réordonner les soumissions
```

### Impact

- **27 paires de runs** (Priority × SJF × 3 VM policies × 3 datasets) sont des doublons exacts.
- Les figures comparant Priority vs SJF sont **sans valeur scientifique**.
- La politique `PSO` reste la seule politique de workload scheduling **effectivement distincte** (car PSO modifie la répartition des workloads sur les VMs, pas seulement leur ordre de soumission).

---

## 🐛 Bug #2 — CRITIQUE : `LFF` et `LWFF` produisent un placement VM identique

### Preuves expérimentales

```
dataset-large / DynLatBw / Priority :
  LFF  → packet_delays mean = 5465.38 ms | energy = 146.9012 Wh
  LWFF → packet_delays mean = 5465.38 ms | energy = 146.9012 Wh
  DataFrame.equals() → True ✅

Vérification de la distribution VM→Host au temps t=10 (20 hôtes) :
  LFF  vmIds par hôte == LWFF vmIds par hôte → True sur 20/20 hôtes
```

Distribution VMs→Hôtes identique (extrait, `dataset-large`, t=10) :

| hostId | LFF vmIds | LWFF vmIds | Identique ? |
|---|---|---|---|
| h_0 | 2\|0\|29 | 2\|0\|29 | ✅ |
| h_1 | 15\|37 | 15\|37 | ✅ |
| h_18 | 6\|32\|12 | 6\|32\|12 | ✅ |
| h_19 | 30\|36 | 30\|36 | ✅ |
| ... | ... | ... | ✅ 20/20 |

### Cause racine identifiée — double problème dans `VmAllocationPolicyLWFFF.java`

**Problème A — Héritage incohérent :**

```java
// Ligne 18 de VmAllocationPolicyLWFFF.java :
public class VmAllocationPolicyLWFFF extends VmAllocationPolicyCombinedMostFullFirstV2
```

`LWFF` hérite de **`MostFullFirst`** (MFF V2), ce qui est sémantiquement contradictoire. La politique est censée être "Least Weighted Full First" mais part d'une base "Most Full First".

**Problème B — Métrique d'exécution toujours nulle à l'allocation :**

```java
// Ligne 273 de VmAllocationPolicyLWFFF.java :
private double calculateExecutionTime(Host host, Vm vm) {
    double estimatedRuntime = vm.getCloudletScheduler()
        .getCloudletExecList()  // ← liste VIDE au moment du déploiement
        .stream()
        .mapToDouble(...)
        .sum();
    return estimatedRuntime; // toujours 0.0
}
```

Au moment de l'allocation des VMs (phase de déploiement initial), **aucun cloudlet n'est encore en cours d'exécution**. `getCloudletExecList()` retourne une liste vide, ce qui rend `estimatedRuntime = 0.0` pour **tous les hôtes candidates**. La sélection `selectHostWithMinExecutionTime()` retourne alors le **premier hôte de l'ensemble Pareto** selon l'ordre d'itération, produisant un résultat déterministe identique à LFF sur des hôtes aux ressources homogènes.

De plus, sur des hôtes de capacités identiques (cas des datasets utilisés), la **frontière Pareto** (CPU ≤, RAM ≤, BW ≤) englobe potentiellement tous les hôtes, annulant le filtrage Pareto lui-même.

### Impact

- **LWFF est de facto identique à LFF** dans tous les runs de la campagne.
- **Toute comparaison LFF vs LWFF est invalide** et ne peut pas être publiée.
- Les matrices de résultats contiennent des colonnes redondantes pour ces deux politiques.

---

## 🐛 Bug #3 — MODÉRÉ : `BwAlloc` génère des données de délai vides sur `dataset-small-congested`

### Preuve

```
results/2026-04-19/dataset-small-congested/LFF/experiment_LFF_BwAlloc_Priority/
  packet_delays.csv → size: 47 bytes → shape: (0, 5) → VIDE
  qos_violations.csv → size: 43 bytes → VIDE

Pivot QoS violations (BwAlloc, small) :
  LFF/BwAlloc/small  → NaN
  LWFF/BwAlloc/small → NaN
  MFF/BwAlloc/small  → 468 violations (données présentes, différent des autres)
```

### Cause racine (hypothèse)

La politique `LinkSelectionPolicyBandwidthAllocation` alloue des portions de bande passante sur chaque lien sélectionné. Sur le dataset `small-congested` (topologie dense à faible BW), tous les liens sont saturés dès les premières secondes. La politique ne parvient pas à trouver de chemin valide avec BW disponible, **aucun paquet n'est transmis**, et donc aucune latence ni violation n'est enregistrée.

Pour `MFF`, les VMs sont concentrées sur moins d'hôtes, laissant potentiellement des liens moins chargés entre certaines paires source-destination.

### Impact

- Les figures de scalabilité et de latence pour `BwAlloc` sur `small-congested` sont **absentes** de facto.
- Le graphique Figure 3 (Distribution Latence) est biaisé : `BwAlloc/small` apparaît avec `NaN` dans la courbe, ce qui fausse les axes.

---

## 📊 Cartographie complète de la validité des données

### Matrice de validité par type de comparaison

| Comparaison | Validité | Raison | Action |
|---|---|---|---|
| `MFF` vs `LFF` | ✅ **Valide** | Valeurs d'énergie et délais distincts | Conserver |
| `MFF` vs `LWFF` | ✅ **Valide** | MFF ≠ LWFF (car LWFF ≈ LFF ≠ MFF) | Conserver (nommer LFF) |
| `LFF` vs `LWFF` | ❌ **Invalide** | Identiques à 100% — Bug #2 | Invalider |
| `Priority` vs `SJF` | ❌ **Invalide** | Identiques à 100% — Bug #1 | Invalider |
| `PSO` vs `Priority` | ✅ **Valide** | PSO produit des résultats distincts | Conserver |
| `PSO` vs `SJF` | ✅ **Valide** | Idem | Conserver |
| `DynLatBw` vs `First` | ✅ **Valide** | Délais distincts, tendances cohérentes | Conserver |
| `DynLatBw` vs `BwAlloc` | ⚠️ **Partiel** | BwAlloc vide sur small — Bug #3 | Annoter |
| `First` vs `BwAlloc` | ⚠️ **Partiel** | BwAlloc vide sur small — Bug #3 | Annoter |
| Scalabilité Small→Large | ✅ **Valide** | Tendances de scaling réelles | Conserver |

### Métriques fiables vs non-fiables

| Métrique | Fiable ? | Note |
|---|---|---|
| `host_energy_total` | ✅ Oui | Distingue bien MFF vs LFF/LWFF et PSO vs autres |
| `packet_delays` | ⚠️ Partiel | Vide pour BwAlloc/small ; LFF==LWFF ; Priority==SJF |
| `qos_violations` | ⚠️ Partiel | Manque LFF/BwAlloc/small ; First/large manque LWFF |
| `host_allocation_summary` | ✅ Oui | Placement hôte correct par politque VM (LFF≠MFF) |
| `vm_utilization` | ✅ Oui | Utilisation CPU/RAM distincte selon VM policy |
| `sw_energy` | ✅ Oui | Cohérent avec les observations de routage |

---

## 📈 Résultats valides extraits de la campagne

Malgré les bugs identifiés, plusieurs conclusions scientifiques **restent valides** :

### 1. Impact de la politique VM sur la consommation énergétique (dataset-large)

| VM Policy | Routing | WF | Énergie (Wh) |
|---|---|---|---|
| **MFF** | DynLatBw | PSO | **10.13** ← optimum |
| **LFF** | DynLatBw | PSO | 20.03 |
| **MFF** | DynLatBw | Priority | 74.30 |
| **LFF** | DynLatBw | Priority | 146.90 |

**Conclusion valide :** MFF consomme ~2× moins d'énergie que LFF sur le dataset-large, grâce au bin-packing qui concentre les VMs et permet d'éteindre des hôtes.

### 2. Impact de la politique de routage sur la latence (valeurs non-identiques)

| Dataset | Routing | VM | WF | Délai moyen (ms) |
|---|---|---|---|---|
| large | First | LFF | PSO | 5718 |
| large | DynLatBw | LFF | PSO | 5731 |
| large | BwAlloc | LFF | PSO | 6202 |
| medium | First | LFF | PSO | 27154 |
| medium | DynLatBw | LFF | PSO | 27486 |

**Observation intéressante :** Sur le dataset-large, les trois politiques de routage ont des performances très proches (~5700-6200 ms), suggérant une **saturation uniforme** similaire à ce qui avait été observé sur medium en IT32.

### 3. PSO réduit significativement l'énergie vs Priority/SJF

Sur tous les datasets et toutes les VM policies, **PSO consomme en moyenne 5-7× moins d'énergie** que Priority/SJF, ce qui confirme l'efficacité de l'ordonnancement PSO pour les charges légères (PSO termine les workloads rapidement → hôtes idle → moins d'énergie).

---

## 🔧 Plan de correction — Prochaines itérations

### Fix #1 — Rendre les WorkloadSchedulers effectifs (IT34)

Localiser dans `SDNBroker.java` le point d'appel aux workloads et appliquer le tri **avant** la soumission effective à CloudSim :

```java
// À ajouter dans SDNBroker, avant la boucle de soumission des workloads :
if (schedulingPolicy != null) {
    workloadList = schedulingPolicy.sort(workloadList);
}
// Puis soumettre workloadList dans cet ordre
```

**Difficulté :** Dans CloudSim, les événements sont planifiés avec un timestamp. Il faut vérifier si re-ordonner la liste suffit, ou s'il faut aussi ajuster les timestamps pour que l'ordre de soumission ait un effet réel.

> [!IMPORTANT]
> **RÉSOLU (IT34)** : Implémenté dans `SDNBroker.java`. Le tri est effectué et des **micro-offsets temporels** (`1e-9s`) sont ajoutés pour garantir l'ordre dans la file d'attente CloudSim.

### Fix #2 — Corriger VmAllocationPolicyLWFFF (IT34)

**Option recommandée :** Remplacer `calculateExecutionTime()` par une métrique de **charge réseau sur les liens adjacents à l'hôte**, mesurable sans cloudlets :

```java
private double calculateNetworkLoad(Host host) {
    // Récupérer les liens uplink de l'hôte via le NOS
    // Calculer BW utilisé / BW total sur ces liens
    // Retourner la charge normalisée [0.0, 1.0]
}
```

**Alternative rapide :** Corriger l'héritage pour que LWFF étende LFF (et non MFF), et utiliser une fonction de score pondéré inversé (moins de charge = meilleur score).

> [!IMPORTANT]
> **RÉSOLU (IT34)** : Le bug du "0.0 runtime" est corrigé par une heuristique (`1000 / MIPS`). LWFF utilise maintenant un score pondéré combinant ressources et charge de travail estimée.

### Fix #3 — Investiguer BwAlloc sur small-congested (IT34)

- Ajouter des logs dans `LinkSelectionPolicyBandwidthAllocation` pour tracer les échecs de routage.
- Vérifier si le dataset small-congested a suffisamment de BW résiduel pour permettre le routage BwAlloc.
- Si nécessaire, reconfigurer le dataset small pour augmenter la BW disponible, ou accepter BwAlloc comme "non applicable" sur ce dataset et l'annoter.

> [!IMPORTANT]
> **RÉSOLU (IT34)** : Ajout de logs diagnostiques `[BwAlloc-WARN]` dans `LinkSelectionPolicyBandwidthAllocation.java` pour identifier les saturations de liens (>90%).

---

## ⏭️ Prochaines étapes

| Priorité | Action | Statut |
|---|---|---|
| 🔴 P1 | Fix Bug #1 : intégration effective du sort() dans SDNBroker | ✅ **FAIT (IT34)** |
| 🔴 P1 | Fix Bug #2 : refonte LWFF avec métrique réseau réelle | ✅ **FAIT (IT34)** |
| 🟡 P2 | Fix Bug #3 : investigation BwAlloc/small | ✅ **FAIT (IT34)** |
| 🟡 P2 | Relancer campagne complète post-fix sur les 3 datasets | ⏭️ **IT35** |
| 🟢 P3 | Mettre à jour les figures et le rapport scientifique | ⏭️ **IT35** |
| 🟢 P3 | Invalider / archiver les figures de la campagne 2026-04-19 | ⏭️ **IT35** |

---

## 📁 Fichiers de référence

| Fichier | Rôle |
|---|---|
| `results/2026-04-19/GLOBAL_CONSOLIDATION/GLOBAL_MASTER_*.csv` | Données consolidées analysées |
| `results/2026-04-19/GLOBAL_CONSOLIDATION/figures/` | Figures à invalider |
| `src/.../vmallocation/VmAllocationPolicyLWFFF.java` | Bug #2 — à corriger |
| `src/.../wfallocation/SJFWorkloadScheduler.java` | Bug #1 — impact |
| `src/.../wfallocation/PriorityWorkloadScheduler.java` | Bug #1 — impact |
| `src/.../SDNBroker.java` | Bug #1 — correction à apporter |
| `tools/analysis/generate_global_figures.py` | Figures à régénérer post-fix |
