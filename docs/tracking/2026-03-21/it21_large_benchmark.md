# Suivi Itération 21 : Benchmark Dataset-Large — 2026-03-21

## État des Simulations (Aujourd'hui)

### LWFF — En cours / Debug 🔴 → 🟠

- [ ] **Scénario 5** : LWFF + BwAllocN + Priority
  - **Statut** : Stalling (Bloqué à 853/1000 completion)
  - **Analyse** : Mismatch entre `REQUEST_COMPLETED` (envoyé par Datacenter) et `WORKLOAD_COMPLETED` (attendu par Broker pour incrémentation).
  - **Action** : Correction de `SDNBroker.java` pour unifier le comptage et de `SDNDatacenter.java` pour les logs.
  - **Prochain essai** : Après application du fix IT25.

---

## 🔴 Nouveau Bug — Stalling en fin de simulation (IT25)

**Symptôme** : La simulation n'atteint jamais 1000/1000 completions et continue de boucler sur des mises à jour de statut (`updateVmProcessing`) sans progresser.

**Cause racine** :
1. Dans `SDNDatacenter.checkCloudletCompletion`, si une requête finit sur une activité de type `Processing`, elle envoie `REQUEST_COMPLETED` (ligne 1596).
2. Dans `SDNBroker.processEvent`, le tag `REQUEST_COMPLETED` appelle `requestCompleted()`, mais **n'incrémente pas** `completedWorkloadCount`.
3. Le compteur reste à `853/1000` (ou autre valeur < 1000), le Broker n'envoie jamais `STOP_MONITORING`, et la simulation ne s'arrête jamais.

**Correctif prévu (IT25)** :
- Unifier l'incrémentation du compteur dans `SDNBroker` pour qu'il prenne en compte les deux types de succès (`REQUEST_COMPLETED` et `WORKLOAD_COMPLETED`).
- Sécuriser avec `completedRequestIds` pour éviter les doubles comptages.

---

## 📅 Historique du jour (2026-03-21)

1. **22h46** : Lancement Scénario 5 (LWFF + BwAllocN + Priority).
2. **23h10** : Observation de logs massifs mais pas de fichiers CSV générés.
3. **23h15** : Identification d'erreurs "No host for dstVmId" (résolues par fallback automatique).
4. **23h20** : Identification du bug de stagnation du compteur (853/1000).
5. **En cours** : Application du fix IT25.
