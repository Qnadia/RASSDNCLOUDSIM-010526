# Compte Rendu — Itération 9 (2026-03-09)

**Objet :** Identification de la cause racine des Timeouts (Double Soumission) et Préparation du Banc d'Essai Priorisé

---

## 1. Analyse Détaillée de l'Anomalie de Mapping (Cloudlet ID)

Suite aux tests sur `dataset-mini` avec la politique `First`, une erreur persistante `Aucune Request trouvée pour Cloudlet ID` empêchait la simulation de se terminer proprement (Timeout).

### 🔍 Cause Racine : Soumission Redondante
L'analyse des traces (`debug_trace_mini.txt`) a révélé que chaque workload était soumis **deux fois**.
*   **Source 1 :** `SDNBroker.startEntity()` envoie un événement `APPLICATION_SUBMIT`.
*   **Source 2 :** `SDNBroker.submitDeployApplicationn()` (appelé manuellement dans le main) envoie également un événement `APPLICATION_SUBMIT`.

### ⚡ Impact
1.  Le `SDNDatacenter` reçoit deux fois l'ordre de déployer et de lancer les workloads.
2.  Deux Cloudlets identiques (même ID) sont créés et soumis au scheduler.
3.  Lorsque le premier se termine, il est retiré de la `requestsTable`.
4.  Lorsque le second se termine, le code tente de le retirer de la table, ne le trouve pas, et déclenche une erreur de mapping qui bloque la progression de la simulation vers l'état "terminé".

---

## 2. Corrections et Améliorations Appliquées

### ✅ A. Suppression de la Double Soumission (`SDNBroker.java`)
*   Le `sendNow` dans `submitDeployApplicationn` a été identifié comme redondant avec le cycle de vie standard de CloudSim géré par `startEntity`.
*   **Action :** Nettoyage de la méthode pour ne conserver qu'une seule source de vérité pour le lancement de l'application.

### ✅ B. Sécurisation du Mapping (`SDNDatacenter.java`)
*   Garantie que `requestsTable.put(cloudletId, req)` est appelé systématiquement lors de `processWorkloadSubmit`.
*   Suppression des injections redondantes dans les chemins de code obsolètes (ex: `processRequestSubmit` pour les anciennes APIs).

---

## 3. Nouveau Scénario de Test : Impact de la Priorité

Pour valider le bon fonctionnement du `PriorityWorkloadScheduler` et la robustesse du nouveau système de mapping, le workload a été étendu.

### 📊 Configuration du Test (Scale 10)
*   **Volume :** 10 requêtes (clonées et diversifiées).
*   **Variante :** Affectation de priorités différentes (1=Haute, 2=Moyenne, 3=Basse) dans la 10ème colonne du CSV.
*   **Objectif :** Vérifier que le broker trie correctement les requêtes avant soumission et que la latence reflète l'ordre de passage.

---

## 4. État Global du Projet

| Composant | Statut |
|:---|:---|
| Correction Double Soumission | ✅ Appliquée |
| Mapping Cloudlet <-> Request | ✅ Fiabilisé |
| Routage First (BFS) | ✅ Opérationnel |
| Workload Priorisé (10 reqs) | 🔄 En cours de test |
| Campagne Scientifique Globale | 🚀 Prête pour relance |
