# IT 10 - Simulation Loop Fix Tracking

## Date: 2026-03-09
## Objective: Fix Infinite Event Loop in SDNDatacenter.java

### Diagnostic Re-cap
The method `processNextActivity` in `SDNDatacenter.java` was:
- Removing activities before they were executed.
- Scheduling `REQUEST_COMPLETED` immediately to itself.
- Bypassing the `CloudletScheduler` and `NetworkOperatingSystem`.

### Modifs Details

#### Component: SDNDatacenter.java
- [x] Refactor `processNextActivity` to trigger activities without removing them immediately.
    - Added `processNextActivityProcessing` to delegate to Cloudlet submission.
## Hypothèse de Correction (Implémentée)
1. **Distinguer les types d'activités** dans `processNextActivity`. (Fait)
2. **Ne pas retirer l'activité** tant qu'elle n'est pas effectivement finie (Fait : retrait lors de la détection de complétion).
3. **Casser la boucle `REQUEST_COMPLETED -> processNextActivity`** : Remplacée par une délégation directe aux composants (CloudletScheduler/NOS). (Fait)
4. Harmoniser `WORKLOAD_SUBMIT` et `REQUEST_SUBMIT`. (En cours)
- [x] Ensure `REQUEST_COMPLETED` (SDN tag) is no longer used for local timers in `processNextActivity` to prevent infinite zero-delay recursion.
- [x] **Ajout de garde-fous supplémentaires :**
    - **Compteur de boucle infinie** dans `SDNDatacenter.java` (MAX_SAME_TIME_EVENTS = 1000).
    - **TimeOut de 2 minutes** dans `debug_run.ps1` pour forcer l'arrêt du processus Java si nécessaire.

#### Component: Request.java
- [ ] Ensure activities are tracked correctly (Executed vs Pending).
    - *Note: `SDNDatacenter` now handles activity progression via `getNextActivity` and `requestsTable` mappings.*

### Verification Steps
- [ ] Run `debug_run.ps1` with `dataset-mini`.
- [ ] Monitor `debug_trace_mini.txt` for time progression.
- [ ] compare results with expected latency.
