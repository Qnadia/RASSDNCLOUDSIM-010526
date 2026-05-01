# Itération 22 : Correction Bug LWFF + Large Dataset (2026-03-16)

## Contexte
Suite au diagnostic de l'IT21, les simulations LWFF + large dataset bouclent indéfiniment. Cause racine : `TIME_OUT = 120s` dans `Configuration.java`.

## C'est quoi le TIME_OUT ?

Le `TIME_OUT` est une limite de temps (en secondes **simulées**) imposée à chaque cloudlet (tâche CPU) :

- Quand `processTimeout()` est appelé, il compare : `arrivalTime < (currentTime - TIME_OUT)`
- Si un cloudlet est arrivé il y a plus de `TIME_OUT` secondes simulées **sans se terminer**, il est marqué **FAILED** et retiré de la file d'exécution
- **Avec 120s** : à `t=23020`, seuil = `22900` → tout cloudlet arrivé avant `t=22900` timeout instantanément
- **Résultat** : aucun cloudlet ne progresse → `MIs exécutés = 0` → boucle infinie
- Ce timeout avait été calibré pour les petits datasets (workloads courts < 60s simulées). Avec le large dataset, les workloads s'étalent sur 20 000+ secondes simulées, ce qui dépasse largement la limite.

## Fix Appliqué (Phase 1)

### `Configuration.java` — ligne 64
```java
// AVANT
public static final double TIME_OUT = 120;

// APRÈS
public static final double TIME_OUT = Double.POSITIVE_INFINITY; // Désactivé pour large dataset
```

> Le safety net reste actif : `SAFETY_TIMEOUT = 86400s` dans `LogMonitor.java` empêche les boucles infinies réelles.

### `CloudletSchedulerTimeSharedMonitor.java` (Phase 1.5)
- **Symptôme** : Malgré le fix du `TIME_OUT`, les simulations donnaient l'impression de boucler ("MIs exécutés: 0" dans les logs, le temps simulé s'incrémentant de 10 en 10 indéfiniment).
- **Cause** : L'override de `updateVmProcessing()` calculait `timeSpan = currentTime - getPreviousTime()`. Mais comme la méthode `getTotalProcessingPreviousTime()` était appelée en amont du monitoring et réinitialisait `previousTime`, **`timeSpan` valait systématiquement `0.0`**. Les Cloudlets ne progressaient jamais.
- **Fix** : Restauration de l'appel natif `super.updateVmProcessing(currentTime, mipsShare)` pour laisser la classe mère de CloudSim gérer correctement la progression CPU.

### Phase 1.6 : Nettoyage des Logs et du Monitoring
- **Format MIs (`SDNVm.java`)** : L'affichage des *MIs exécutés* retournait des milliards car CloudSim nativement calcule en *instructions pures*. J'ai divisé l'affichage dans le log par `1 000 000` (`Consts.MILLION`) pour rétablir une lecture propre.
- **Spam `.err.log` (`SDNDatacenter.java`)** : Le check de fin de Cloudlet ne trouvait parfois pas les requêtes, spammeant `❌ [checkCloudletCompletion] Aucune Request trouvée...` dans `System.err.println`. Ces lignes ont été mises en commentaire pour ne plus ralentir l'I/O ni grossir le fichier d'erreur de la simulation large.
- **Organisation (`run_single_simulation.ps1`)** : Le script a été modifié pour ranger de force tous ses exports `-SaveLogs` dans un dossier `/logs/` dédié.

## Fix En Attente de Test (Phase 2)

```java
// À évaluer après test de la Phase 1
public static final double CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT = 0.2; // → 1.0 ?
```
Raison : Ce paramètre plafonne chaque cloudlet à 20% des MIPS de la VM, rallongeant l'exécution × 5. À tester si les simulations LWFF restent lentes même après le fix du timeout.

## Recompilation
```powershell
cd e:\Workspace\v2\cloudsimsdn-research
mvn compile -q
```

## Scénarios à Valider (LWFF + Large Dataset)
- [ ] **Scénario 5** : LWFF + BwAllocN + Priority
- [ ] **Scénario 6** : LWFF + First + Priority
- [ ] **Scénario 7** : LWFF + BwAllocN + SJF
- [ ] **Scénario 8** : LWFF + BwAllocN + FCFS
- [ ] **Scénario 9** : MFF + BwAllocN + PSO (Final)

## Critères de Validation
- `MIs exécutés > 0` dès le premier intervalle de monitoring (`t=10`)
- Apparition de `🎉 Cloudlet ID X terminé avec succès` dans les logs
- Simulation se termine avec `🛑 Monitoring arrêté` (naturel, pas safety timeout)
- Fichiers CSV non vides dans `results/2026-03-16/dataset-large/LWFF/`

## État
- [x] Fix `TIME_OUT` appliqué dans `Configuration.java`
- [x] Fix `timeSpan = 0.0` (Avancement bloqué des MIs résolu) 
- [x] Fix Formatage MIs & logs `.err`
- [x] Recompilé
- [ ] Scénario 5 validé
- [ ] Scénario 6 validé
- [ ] Scénario 7 validé
- [ ] Scénario 8 validé
- [ ] Scénario 9 (PSO) validé
