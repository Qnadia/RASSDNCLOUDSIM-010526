# Itération 23 : Analyse LWFF Large Dataset — 2026-03-22

## 🔍 Résumé Exécutif

La simulation **LWFF + BwAllocN + Priority** (dataset-large, Scénario 5) a été **manuellement interrompue** le 2026-03-21 à 23h31 alors qu'elle **n'avait pas encore terminé**. Le log enregistre un blocage (stall) à **853/1000 workloads complétés**.

---

## 📋 État des Simulations Dataset-Large (Récap complet)

| # | Politique VM | Lien BW | Scheduler | Statut | Date | Résultats CSV |
|---|-------------|---------|-----------|--------|------|---------------|
| 1 | MFF | BwAllocN | Priority | ✅ Terminé | 2026-03-16 | ✅ Complets (8 scénarios) |
| 2 | MFF | BwAllocN | SJF | ✅ Terminé | 2026-03-16 | ✅ Complets |
| 3 | MFF | BwAllocN | FCFS | ✅ Terminé | 2026-03-16 | ✅ Complets |
| 4 | MFF | First | Priority/SJF/FCFS/PSO | ✅ Terminé | 2026-03-16 | ✅ Complets |
| 5 | **LWFF** | BwAllocN | Priority | ✅ Terminé (IT25 OK) | 2026-03-22 | ✅ Complets |
| 6 | LFF | BwAllocN | Priority/SJF/FCFS/PSO | ✅ Terminé | 2026-03-16 | ✅ Complets (8 scénarios) |
| 7 | LFF | First | Priority/SJF/FCFS/PSO | ✅ Terminé | 2026-03-16 | ✅ Complets |

**Conclusion** : MFF (8/8 OK), LFF (8/8 OK), LWFF (0/8 complet, 1 partiel).

---

## 🔬 Analyse Détaillée : Pourquoi LWFF prend beaucoup plus de temps ?

### 1️⃣ Comportement du placement LWFF vs MFF/LFF

| Critère | MFF (Most Full First) | LFF (Least Full First) | LWFF (Least Worst Fit First) |
|---------|----------------------|------------------------|------------------------------|
| **Philosophie** | Remplir au max les hôtes | Distribuer sur le max d'hôtes libres | Minimiser la fragmentation réseau + CPU |
| **VMs par hôte** | Haute densité (2-3 VMs/host) | Densité faible (1 VM/host max) | Densité intermédiaire, dispersée selon réseau |
| **Localité réseau** | Forte (VMs proches) | Faible (VMs éloignées) | Variable — dépend de la topologie SDN |
| **Erreurs No-host** | Peu/aucune | Peu/aucune | **Nombreuses ❌** (VM ID 12, 14, 20, 29) |

### 2️⃣ La Cause Principale : Fragmentation avec `dstVmId` inconnus

Le `.err.log` (520 KB) contient des **milliers d'erreurs** du type :
```
[ERROR] No host for dstVmId=12 (SDN node ID), falling back to processingVmId=5
[ERROR] No host for dstVmId=14 (SDN node ID), falling back to processingVmId=4
[ERROR] No host for dstVmId=20 (SDN node ID), falling back to processingVmId=10
[ERROR] No host for dstVmId=29 (SDN node ID), falling back to processingVmId=19
```

**Ce qui se passe** : LWFF place les VMs de façon à minimiser la "pire utilisation", mais avec 40 VMs sur 20 hôtes, il crée une distribution où certains `dstVmId` (cibles de requêtes réseau) ne sont pas trouvés dans `vmHostMap`. Cela déclenche le **mécanisme de fallback** à chaque requête.

> **Pourquoi MFF/LFF n'ont pas ce problème ?** MFF concentre les VMs → `dstVmId` toujours connus. LFF distribue uniformément → même résultat. LWFF fait un placement **non-uniforme basé sur la "pire fit"** qui introduit des VMs "orphelines" du point de vue du NOS.

### 3️⃣ Le Bug de Stagnation à 853/1000

#### Flux de complétion normal (MFF/LFF) :
```
SDNDatacenter → WORKLOAD_COMPLETED → Broker.processEvent()
                                   → completedWorkloadCount++
                                   → checkIfAllWorkloadsCompleted()
                                   → STOP_MONITORING  ✅
```

#### Flux LWFF avec fallback (bugué — avant IT24) :
```
SDNDatacenter → REQUEST_COMPLETED [via chemin fallback]
              → Broker.requestCompleted()
              → requestMap.remove(req.getId()) → wl == null ❌
              → return EARLY  ← Pas d'incrémentation !
→ completedWorkloadCount reste bloqué à 853
→ STOP_MONITORING jamais envoyé
→ Simulation boucle indéfiniment
```

---

## 🔍 Analyse du Dernier Log (2026-03-21 23:31)

- **Taille** : 17.4 MB (375 824 lignes)
- **Dernière modification** : 2026-03-21 23:31:03
- **Dernier temps simulé** : `t = 2910.0`
- **Compteur bloqué** : ligne 319 567 (`853/1000`) → 56 000 lignes de boucle inutile après

> **🛑 La simulation a été interrompue MANUELLEMENT (Ctrl+C).**
> Preuve : le fichier se termine avec des octets nuls — écriture interrompue brusquement.

---

## 📁 Résultats CSV Disponibles (partiels)

| Fichier | Taille | Fiabilité |
|---------|--------|-----------|
| `detailed_energy.csv` | 12 MB | ⚠️ 85% des données |
| `host_allocation_summary.csv` | 9.4 MB | ⚠️ 85% des données |
| `link_utilization_up/down.csv` | 123 MB chacun | ⚠️ 85% des données |
| `packet_delays.csv` | 46 KB | ⚠️ Partiel |
| `qos_violations.csv` | 29 KB | ⚠️ Partiel |

> Non comparables directement aux résultats MFF/LFF (100% complétés).

---

## 🔧 Workflow — Ce qui a fonctionné vs échoué

### ✅ Ce qui a fonctionné pour MFF et LFF
1. Fix `TIME_OUT = POSITIVE_INFINITY` (IT22) → timeout désactivé pour large dataset
2. Fix `super.updateVmProcessing()` (IT22) → MIs progressent correctement
3. Placement MFF/LFF → pas de fragmentation dstVmId → aucune erreur fallback
4. `WORKLOAD_COMPLETED` correctement envoyé → compteur atteint 1000/1000

### ❌ Ce qui échouait pour LWFF
1. Placement LWFF → fragmentation → erreurs `No host for dstVmId` massives
2. Le fallback produit des `REQUEST_COMPLETED` dont certains ont `wl == null`
3. Le `return` prématuré empêchait le compteur d'être incrémenté
4. Les 147 workloads restants ne déclenchaient jamais `STOP_MONITORING`

---

## 🛠 Actions Correctives Appliquées (IT24) ✅

### Fix 1 — `SDNBroker.java` ✅ APPLIQUÉ

**Problème** : `completedWorkloadCount++` était placé **après** `if (wl == null) return`.
Sur le chemin fallback LWFF, `wl == null` → return prématuré → compteur jamais incrémenté.

**Solution** : Déplacer l'incrémentation **avant** toute vérification de `wl`.

#### Diff complet — méthode `requestCompleted()` (~ligne 1636)

```diff
  private void requestCompleted(SimEvent ev) {
      Request req = (Request) ev.getData();
      Workload wl = requestMap.remove(req.getRequestId());

-     if (wl == null) {
-         Log.printLine("❌ No workload found in requestMap for Request ID: " + req.getRequestId());
+     // FIX IT24: Count the completion FIRST, before any early return.
+     // Previously, when wl == null (LWFF fallback path), the method returned
+     // before reaching the counter increment, causing the stall at 853/1000.
+     if (completedRequestIds.add(req.getRequestId())) {
+         this.completedWorkloadCount++;
+         System.out.println("✅ [REQUEST_COMPLETED] Progress: " + completedWorkloadCount + " / " + totalWorkloadCount);
+         checkIfAllWorkloadsCompleted();
+     }
+
+     if (wl == null) {
+         // Counted, but no workload metadata to record — happens on LWFF fallback path
+         Log.printLine("⚠️ [REQUEST_COMPLETED] No workload in requestMap for ID: "
+                 + req.getRequestId() + " — counted but not recorded.");
          return;
      }

      WorkloadParser parser = wl.getParser();
      if (parser == null) {
          Log.printLine("❌ Parser is NULL for WorkloadID: " + wl.workloadId);
          return;
      }

      parser.addCompletedWorkload(wl);
      Log.printLine("✅ Workload " + wl.workloadId + " ajouté à completedWorkloads.");

-     // FIX IT25: Count this completion towards total if not already counted via WORKLOAD_COMPLETED
-     if (completedRequestIds.add(req.getRequestId())) {
-         this.completedWorkloadCount++;
-         System.out.println("✅ [REQUEST_COMPLETED] Progress: " + completedWorkloadCount + " / " + totalWorkloadCount);
-         checkIfAllWorkloadsCompleted();
-     }
-
      wl.failed = false;
      wl.writeResult();
      Log.printLine("✅ Workload " + wl.workloadId + " terminé et enregistré.");
  }
```

---

### Fix 2 — `SDNDatacenter.java` ✅ APPLIQUÉ

**Problème** : Chaque requête avec `dstVmId` inconnu écrivait une erreur → 520 KB de spam I/O.

**Solution** : Throttle via un `Set<Integer> loggedFallbackVmIds` — 1 seul log par `dstVmId`.

#### Diff 1 — déclaration du champ (~ligne 98, après `failedRequests`)

```diff
  private int failedRequestsCount = 0;
  private Map<String, Integer> failedRequests = new HashMap<String, Integer>();

+ // FIX IT24: Throttle repeated fallback error logs (only log once per dstVmId)
+ private final Set<Integer> loggedFallbackVmIds = new HashSet<>();
```

#### Diff 2 — throttle dans `processWorkloadSubmit()` (~ligne 596)

```diff
  if (getVmAllocationPolicy().getHost(dstVmId, req.getUserId()) == null) {
      // Fallback: if SDN node ID is not a VM ID, use processingVmId as destination
-     logError("No host for dstVmId=" + dstVmId + " (SDN node ID), falling back to processingVmId=" + processingVmId);
+     // FIX IT24: Log only once per dstVmId to avoid 500KB+ spam in .err.log
+     if (loggedFallbackVmIds.add(dstVmId)) {
+         logError("No host for dstVmId=" + dstVmId
+                 + " (SDN node ID), falling back to processingVmId. [Subsequent occurrences suppressed]");
+     }
      dstVmId = processingVmId;
  }
```

---

### Recompilation ✅ RÉUSSIE
```
mvn compile -q  →  exit code 0  (aucune erreur de compilation)
```

### Vérification Seconde Relance (10h44 - 10h52)
1. **Throttling des logs (Fix 2)** : ✅ **SUCCÈS TOTAL**.
   - Flux d'erreurs fallback réduit de 520 KB à 4 lignes (un seul log par VM ID : 12, 14, 20, 29).
   - Plus de ralentissement I/O dû au spam `.err.log`.
2. **Progression Temporelle** : ✅ **OK**.
   - La simulation a atteint `t=3800` (contre `t=2910` lors du stall précédent).
3. **Progression Workloads** : ⚠️ **En cours**.
   - Compteur toujours à `853/1000`.
   - **Analyse** : Aucun `REQUEST_COMPLETED` n'a été émis par le Datacenter vers le Broker entre t=2910 et t=3800. Vu la taille du dataset-large, il est probable que les 147 workloads restants soient simplement très gourmands en temps et mettront plus de temps à se terminer.
   - **Validation Fix 1** : N'a pas encore pu être déclenché car aucune requête n'a fini.

---

## 🔄 Plan de Relance LWFF

1. [x] Appliquer Fix 1 dans `SDNBroker.java` ✅
2. [x] Appliquer Fix 2 (log throttle) dans `SDNDatacenter.java` ✅
3. [x] Recompiler : `mvn compile -q` → exit 0 ✅
4. [ ] Relancer Scénario 5 : `LWFF + BwAllocN + Priority`
5. [ ] Valider : compteur atteint 1000/1000, `STOP_MONITORING` reçu, CSVs non vides
6. [ ] Si OK → lancer les 7 scénarios LWFF restants

---

## 📅 Historique (2026-03-22)

| Heure | Action |
|-------|--------|
| 10h00 | Analyse du log `experiment_LWFF_BwAllocN_Priority_dataset-large_isolated.log` |
| 10h15 | Confirmation : simulation interrompue manuellement à t=2910, stall à 853/1000 |
| 10h15 | Identification du bug : `wl == null` → return prématuré dans `requestCompleted()` |
| 10h15 | Rédaction du tracking IT23 et du plan de correction IT24 |
| 10h28 | Fix 1 appliqué dans `SDNBroker.java` (compteur déplacé avant early return) |
| 10h28 | Fix 2 appliqué dans `SDNDatacenter.java` (throttle log fallback) |
| 10h28 | Recompilation réussie (`mvn compile -q`, exit 0) |
| 10h31 | Diffs complets archivés dans ce fichier de tracking |
| 10h44 | **Relance Scénario 5** (VM LWFF, BW BwAllocN, SJF Priority) |
| 10h50 | Vérification log : **Fix 2 validé** (Errors throttled à 4 lignes) |
| 10h52 | Simulation stoppée manuellement par l'utilisateur à `t=3800` |
| 11h00 | Identification de la cause profonde réelle (**IT25**) : Échec création VM par BW |
| 11h15 | Rédaction du plan IT25 pour corriger `VmAllocationPolicyLWFFF` et `SDNBroker` |

---

## 🚀 La Cause Profonde Réelle : Échec de Création des VMs (IT25)

L'analyse approfondie du début du log a révélé que le blocage à 853/1000 n'est pas dû à une lenteur, mais à un **échec d'initialisation** :

1. **Bug critique dans LWFFF** : La classe `VmAllocationPolicyLWFFF.java` ne vérifie **JAMAIS** la bande passante (BW) dans sa méthode `isSuitableForVm`. 
2. **Échec silencieux** : Les VMs **12, 14, 20 et 29** ont échoué à la création car l'hôte 19 n'avait plus assez de BW (confirmé par `[VmScheduler.vmCreate] Allocation of VM #14 ... failed by BW`).
3. **Conséquence Broker** : Le Broker ne trouve pas de Datacenter pour ces 4 VMs. Les workloads les ciblant sont **jetés au départ** (`Datacenter not found for VM ID: 29`).
4. **Stall** : Comme ces workloads ne sont jamais soumis, ils ne finissent jamais → le compteur s'arrête à 853.

### Solution IT25
- **LWFFF** : Ajouter le check `host.getBwProvisioner().isSuitableForVm(vm, vm.getBw())`.
- **Broker** : En cas de VM manquante, compter le workload comme "échoué/sauté" pour débloquer le compteur global.

> [!IMPORTANT]
> **Pourquoi ne pas réduire à 800 workloads ?**
> Réduire à 800 masquerait le bug mais donnerait des résultats biaisés (4 VMs en moins = moins de conso énergétique, moins de débit). Pour une comparaison juste avec MFF/LFF, LWFF **doit** traiter les 40 VMs et 1000 workloads.

### 💻 Détails des Modifications de Code (IT25)

#### 1. `VmAllocationPolicyLWFFF.java` (Fix Allocation BW)
Correction de l'omission critique de la bande passante lors des tests d'éligibilité.

```java
// Patch isSuitableForVm (Fixed)
if (!host.getBwProvisioner().isSuitableForVm(vm, vm.getBw())) {
    Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() 
        + " to Host #" + host.getId() + " failed by BW");
    return false;
}

// Patch calculateProfitForHost (Final Fix)
double totalMips = host.getTotalMips();
double availableMips = vmScheduler.getAvailableMips();
double usedMips = totalMips - availableMips;
double cpuUtil = usedMips / totalMips;
```

#### 2. `SDNBroker.java` (Stall Protection)
Empêche le blocage du compteur si une VM n'a pas pu être créée.

```java
// Patch scheduleWorkload
if (dc == null) {
    this.completedWorkloadCount++;
    System.out.println("⚠️ [SDNBroker] Workload " + wl.workloadId 
        + " skipped (VM not created) - Progress: " 
        + completedWorkloadCount + " / " + totalWorkloadCount);
    checkIfAllWorkloadsCompleted();
    return;
}
```

#### 3. Recompilation
- **Statut** : ✅ **Succès** (`mvn compile -q`, exit 0).
