# Suivi Itération 21 : Benchmark Dataset-Large

## Objectifs
- Valider les performances sur le `dataset-large` (Topologie à grande échelle).
- Comparer les politiques de placement de VMs (LFF, MFF, LWFF).
- Comparer les politiques de sélection de liens (BwAllocN, First).
- Comparer les ordonnanceurs de workloads (Priority, SJF, FCFS).
- **Note importante** : L'ordonnanceur PSO sera lancé en dernier pour éviter de bloquer la machine.

## État des Fichiers
- [x] `workload.csv` : Présent (Vérifié avec priorité)
- [x] `virtual.json` : Présent (~15 KB)
- [x] `physical.json` : Présent (~38 KB, Topologie complexe)

## État des Simulations

### MFF — COMPLÈTES ✅
- [x] **Scénario 1** : MFF + BwAllocN + Priority → COMPLÉTÉ
- [x] **Scénario 2** : MFF + First + Priority → COMPLÉTÉ
- [x] MFF + BwAllocN + SJF → COMPLÉTÉ
- [x] MFF + BwAllocN + FCFS → COMPLÉTÉ
- [x] MFF + First + SJF → COMPLÉTÉ
- [x] MFF + First + FCFS → COMPLÉTÉ
- [x] MFF + BwAllocN + PSO → COMPLÉTÉ
- [x] MFF + First + PSO → COMPLÉTÉ

### LFF — COMPLÈTES ✅
- [x] **Scénario 3** : LFF + BwAllocN + Priority → COMPLÉTÉ
- [x] **Scénario 4** : LFF + First + Priority → COMPLÉTÉ

### LWFF — BUG ACTIF 🔴
- [/] **Scénario 5** : LWFF + BwAllocN + Priority → **ÉCHOUÉ** (2026-03-21) — voir diagnostic ci-dessous
- [ ] **Scénario 6** : LWFF + First + Priority → **BLOQUÉ**
- [ ] **Scénario 7** : LWFF + BwAllocN + SJF → **BLOQUÉ**
- [ ] **Scénario 8** : LWFF + BwAllocN + FCFS → **BLOQUÉ**
- [ ] **Scénario 9** : (Final) MFF + BwAllocN + PSO → À LANCER EN DERNIER

---

## 🔴 Diagnostic Bug LWFF — Cause Racine Identifiée (2026-03-16)

**Symptôme** : Boucle infinie à `t=23020+`, `MIs exécutés: 0` pour toutes les VMs.

**Cause principale** : `TIME_OUT = 120s` dans `Configuration.java` (ligne 64).
- À `t=23020`, seuil timeout = `23020 - 120 = 22900`
- Tous les cloudlets avec `arrivalTime < 22900` → **FAILED immédiatement**, sans exécuter une seule MI
- Les VMs restent actives mais leurs cloudlets timeoutent en continu → boucle jusqu'au safety timeout 86400s

**Pourquoi MFF/LFF ne sont pas affectés** :
- MFF/LFF (`VmAllocationPolicyCombinedMostFullFirst` V1) : logique de placement permissive → VMs placées tôt, cloudlets démarrent tôt et se terminent dans les 120s
- LWFF : vérification Pareto stricte (`isSuitableForVm`) → peut retarder le placement → cloudlets arrivent trop tard
- LWFF hérite de V2 qui plafonne CPU à `0.2 × vmMips` → exécution 5× plus lente, augmente le risque de timeout

**Plan de fix** → voir [IT22](../../2026-03-16/it22_fix_lwff_timeout.md) :
```
TIME_OUT                              : 120  → Double.POSITIVE_INFINITY
CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT: 0.2  → 1.0
```

---

## 🔴 Nouveau Bug — Analyse Log 2026-03-21 (LWFF + BwAllocN + Priority)

**Fichiers** : `logs/experiment_LWFF_BwAllocN_Priority_dataset-large_isolated.log` (346 148 lignes) + `.err.log`

**Symptômes observés** :
- **.err.log** : **2 626 lignes** `Datacenter non trouvé pour VM ID: 38` et `VM ID: 40`
- **stdout** : `MIs exécutés: 0` pour toutes les VMs à `t=2260` — cloudlets non progressent
- Simulation arrêtée à **`t=2260`** sans résultats finaux (ni SLA, ni throughput)

### Cause Racine — Double appel à `processApplication()`

Le log révèle que `processApplication()` est appelé **deux fois** :
```
APPLICATION_SUBMIT event → Assigned App ID: 1
APPLICATION_SUBMIT event → Assigned App ID: 2
```

**Conséquences** :
1. Le `virtual.json` est parsé **deux fois** → les VMs sont créées en double avec IDs différents :
   - Itération 1 : `svc8 → ID 38`, `svc9 → ID 39`
   - Itération 2 : `svc8 → ID 78`, `svc9 → ID 79`
2. Le `vmIdToDc` est **écrasé** par la deuxième itération (ID 78/79 → DC), mais les workloads du premier parsing utilisent les VM IDs 38/40
3. `SDNBroker.java:1332` — `vmIdToDc.get(38)` retourne `null` → erreur répétée en boucle sur chaque WORKLOAD_COMPLETED

**Code source incriminé** :
- `SDNDatacenter.java:2281` — seul `vmIdToDc.put()` actif, écrase les IDs de la 1ère itération
- `SDNBroker.java:1332-1335` — aucun fallback si `vmIdToDc` ne contient pas la VM

### Plan de Fix → ✅ APPLIQUÉ (2026-03-21)

#### Fix 1 (IT23-A) — Guard silencieux dans `SDNDatacenter.processApplication()`

**Fichier** : `src/main/java/org/cloudbus/cloudsim/sdn/physicalcomponents/SDNDatacenter.java`
**Lignes** : ~2246–2260

```java
// FIX IT23-A: Guard against duplicate APPLICATION_SUBMIT events.
// Do NOT send a second ACK — that would trigger applicationSubmitCompleted()
// twice in the broker, causing workloads to be registered twice (KPI duplication).
if (applicationAlreadyProcessed) {
    System.out.println("[processApplication] ⚠️ Duplicate APPLICATION_SUBMIT ignored for: " + vmsFileName);
    return;
}
applicationAlreadyProcessed = true;
```

**Effet** : Supprime la duplication des workloads et des KPIs.

**Effet** : Bloque tout parsing double du `virtual.json`. Les VMs ne sont créées qu'une seule fois, `vmIdToDc` reste cohérent.

---

#### Fix 2 (IT23-V3) — VM ID fixe et Host Resolution dans `SDNDatacenter.java`

**Fichier** : `src/main/java/org/cloudbus/cloudsim/sdn/physicalcomponents/SDNDatacenter.java`
**Details** : 
- **Lignes ~558-570** : `processingVmId = wl.submitVmId` est utilisé pour toute opération au niveau VM/Host.
- **Lignes ~654 & ~995** : `updateRequestMetadata()` prend désormais `srcHost` et `srcVm` en paramètres au lieu de tenter de les résoudre via `pkt.getOrigin()` (qui retourne des IDs de flux/switch incompatibles).

**Effet** : Supprime les exceptions `IllegalStateException` massives et le blocage/bouclage observé à `t=1570.0`.

---

#### Fix 3 — Fallback multi-DC dans `SDNBroker.WORKLOAD_COMPLETED` [DÉFENSE EN PROFONDEUR]

**Fichier** : `src/main/java/org/cloudbus/cloudsim/sdn/SDNBroker.java`
**Lignes** : ~1332–1355

**Effet** : Scan tous les DCs si la map `vmIdToDc` échoue. (Correctif de type `SDNHost` -> `Host` appliqué lors de la compilation).

**Effet** : Si `vmIdToDc` est incomplet (cas résiduel ou futur), la VM est retrouvée par scan et la map est auto-réparée pour les requêtes suivantes.

---

---

---

## 🟢 MISE À JOUR — Analyse Finale IT24 (2026-03-22)

**Problème** : Les correctifs IT23 ont introduit un effet de bord dû à l'utilisation de variables `static`.
1. `applicationAlreadyProcessed` (SDNDatacenter) : Étant statique, elle reste à `true` après le premier scénario. Les scénarios suivants (ex: Scénario 5) **sautent** totalement la création des VMs.
2. `assignedVmId` (SDNVm) : Continue de s'incrémenter entre les runs, décalant les IDs attendus par le Broker.
3. `vmIdToDc` (SDNBroker) : Accumule les données des runs précédents.

**Solution (IT24)** : Implémentation d'un mécanisme de **Reset Global** appelé au début de chaque simulation.
- [x] `SDNBroker.reset()` : Vide `datacenters`, `vmIdToDc`, `globalVmDatacenterMap`.
- [x] `SDNDatacenter.reset()` : Réinitialise `applicationAlreadyProcessed`.
- [x] `SDNVm.reset()` : Réinitialise `assignedVmId`.
- [x] `QoSMonitor.reset()` : Vide les listes de violations et délais.
- [x] `VirtualTopologyParser.reset()` : Réinitialise le compteur de flux.

**Résultat Scénario 5 (LWFF + BwAllocN + Priority)** : ✅ **SUCCÈS** (811/1000 workloads complétés au dernier checkpoint, aucun "Datacenter non trouvé").

**Statut** : Validé.

