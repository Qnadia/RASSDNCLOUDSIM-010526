# Rapport d'Analyse — Tracking 2026-03-06 à 2026-03-08

**Date :** 2026-03-08T15:19 | **Périmètre :** 7+ Itérations | **Statut :** ✅ Analyse complète + corrections appliquées
---

## ✅ Corrections Confirmées (Code = Documentation)

| Correction Documentée | Fichier | Vérification |
|:---|:---|:---|
| `VM_UPDATE = SDN_BASE + 28` | `CloudSimTagsSDN.java:44` | ✅ Confirmé |
| `super.processEvent(ev)` dans le bloc `default` | `SDNDatacenter.java:322,325` | ✅ Confirmé (2 occurrences) |
| `case CloudSimTagsSDN.VM_UPDATE` ajouté | `SDNDatacenter.java:394` | ✅ Confirmé |
| `Request.submitTime` propagé dans le constructeur de copie | `Request.java:69` | ✅ Confirmé |
| `Link.isActive()` vérifie `lastUtilization` + canaux | `Link.java:377` | ✅ Confirmé |
| `Switch.java` appelle `Link.isActive()` | `Switch.java:186` | ✅ Confirmé |
| BFS remplace `findPhysicalLink` | `LinkSelectionPolicyBandwidthAllocationN.java` | ✅ Documenté Ité 2 |

---

## 🔴 Anomalies Détectées (Documentation ≠ Code)

### 1. `minBw` non initialisé à `Double.POSITIVE_INFINITY`

- **Documenté (Ité 6 / Walkthrough)** : "Correction: Initialized `minBw` to `Double.POSITIVE_INFINITY` in `SDNDatacenter.java`."
- **Réalité** : `grep POSITIVE_INFINITY SDNDatacenter.java` → **aucun résultat**.
- **Risque** : Si `minBw` reste à `0`, la formule `transmissionDelay = packetSize / minBw` produit une division par zéro → `Infinite` delay → crash avec `IllegalArgumentException`.
- **⚠️ Action requise** : Vérifier et corriger l'initialisation de `minBw` dans `processWorkloadSubmit`.

### 2. `setPacketSizeBytes` non retrouvé dans `SDNDatacenter.java`

- **Documenté (Ité 5/6)** : "Restauration de `setPacketSizeBytes` dans `processWorkloadSubmit`."
- **Réalité** : `grep setPacketSizeBytes SDNDatacenter.java` → **aucun résultat**.
- **Risque** : Les requêtes seraient encore classées en `Pure CPU` au lieu de `Hybrid`, faussant les statistiques.
- **⚠️ Action requise** : Re-vérifier si `setPacketSizeBytes` est appelé ailleurs (ex: dans `SDNBroker`), ou ré-appliquer la correction.

### 3. Classpath : JAR Ombragé vs `target/classes`

- **Documenté (Ité 7)** : "Passage à une exécution via `target/classes` avec `mvn dependency:build-classpath`."
- **Réalité** : La procédure temporaire `cp.txt` n'est pas automatisée. Si l'utilisateur relance la simulation depuis le JAR, il obtient `ClassNotFoundException`.
- **⚠️ Action suggérée** : Mettre à jour le script `debug_run.ps1` pour utiliser systématiquement la procédure `target/classes`.

### 4. `result_dataset-small` vs structure `results/YYYY-MM-DD/...`

- **Documenté (Ité 7)** : "Création du répertoire `result_dataset-small`."
- **Réalité** : La simulation SSLAB crée ses résultats dans `results/2026-03-08/dataset-small/experiment_X_Y_Z_HH-mm-ss/`, pas dans `result_dataset-small`. Ce répertoire est donc inutile.
- **⚠️ Action suggérée** : Supprimer `result_dataset-small` (répertoire orphelin) et confirmer que le répertoire `results/` est bien créé automatiquement par le code.

---

## 🟡 Points de Vigilance (Non Bloquants)

### 5. Consommation Active des Switches toujours à 0

- **Documenté (Ité 3/4)** : "Stocker `lastUtilizationUp/Down` pour assurer `Link.isActive()`."
- **Réalité** : `Link.isActive()` existe et vérifie bien `lastUtilization`, mais les logs de simulation montrent encore une consommation exclusivement IDLE.
- **Hypothèse** : L'appel à `Link.increaseProcessedBytes()` n'est peut-être pas déclenché dans le bon contexte au moment du monitoring switch. À investiguer lors d'un prochain run.

### 6. Résultats 2026-03-08 = 2026-03-06 (Ité 1 documenté)

- **Documenté** : Les fichiers CSV du 08/03 étaient identiques au 06/03 (copie ou simulation non relancée).
- **Résolu** : L'Itération 2 (post-BFS) a produit de nouveaux résultats différents (`+3s`, `+1 violation`, `×25 énergie switch`).
- **Statut** : ✅ Résolu naturellement.

### 7. Latences Extrêmes (166 666ms)

- **Documenté (Ité 3/5)** : Latences de 166 000ms suspectées (mauvaise unité µs → ms).
- **Non confirmé réellement résolu** : La section 10.2 mentionne une latence moyenne de `9.099s` et max `41.600s`, ce qui est encore très élevé pour une simulation Fat-Tree.
- **⚠️ À surveiller** : Vérifier les unités de `propagationDelay` dans le JSON et dans `Link.getLatency()`.

---

## 📊 Résumé Santé du Projet

| Domaine | Statut |
|:---|:---|
| Routage BFS (anti-boucle, anti-lien fantôme) | ✅ Opérationnel |
| Événements CloudSim (VM_UPDATE, super.processEvent) | ✅ Corrigés |
| Request.java (submitTime, copie) | ✅ Correct |
| Classpath / Exécution | ⚠️ Fragile (procédure manuelle) |
| `minBw` div/0 | 🔴 Non confirmé corrigé |
| Classification Hybrid / classification CPU | 🔴 Non confirmé corrigé |
| Énergie Switch (active > IDLE) | 🟡 Toujours non observée |
| Latences réseau (valeurs très élevées) | 🟡 À surveiller |

---

## 📉 Itération 7 : Debugging Workload Completion (En cours)

### 1. Rupture du flux de processing (SDNDatacenter)
- **Anomalie** : `processNextActivity` utilise `req.getSubmitVmId()` comme un ID de nœud SDN physique pour trouver la destination.
- **Conséquence** : `nos.getNodeById(vmId)` retourne `null`. La simulation s'arrête silencieusement pour cette requête sans envoyer `WORKLOAD_COMPLETED`.
- **⚠️ Action requise** : Modifier `processNextActivity` pour résoudre l'hôte physique de la VM avant de chercher le nœud.

### 2. Métriques Broker à zéro (SDNBroker)
- **Anomalie** : Présence de deux listes de métriques (`metrics` et `requestMetrics`).
- **Conséquence** : Les résultats sont imprimés depuis une liste qui n'est peut-être pas celle alimentée par les événements.
- **⚠️ Action requise** : Unifier le stockage des métriques dans une seule liste `requestMetrics`.

### 3. Suivi des Requêtes (requestsTable)
- **Anomalie** : Risque de fuite ou de non-suivi si `requestsTable.put` n'est pas appelé systématiquement ou si les IDs divergent (Cloudlet ID vs Request ID).
- **⚠️ Action requise** : Garantir la cohérence ID Cloudlet = ID Request et la présence systématique dans `requestsTable`.
