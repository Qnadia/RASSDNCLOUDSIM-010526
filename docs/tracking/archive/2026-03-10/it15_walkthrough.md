# Simulation Isolation Walkthrough

## Modifications effectuées
1. **Création de l'artefact de suivi IT**
   Mise à jour de `docs/tracking/2026-03-10/it15_pso_isolation_plan.md` pour définir le plan d'action visant à isoler le bug causé par la politique PSO.
2. **Implémentation de `run_single_simulation.ps1`**
   Ce script permet de lancer une seule combinaison (VM, Link, Workload) à la fois, avec ou sans redirection des logs (logs en direct si demandé).

## Vérification et Résultats
Le script a été testé avec la commande exacte pour lancer un workload comportant l'algorithme `PSO`.
`powershell.exe -ExecutionPolicy Bypass -File .\run_single_simulation.ps1 -VmPolicy LFF -LinkPolicy First -WorkloadPolicy PSO -Dataset "dataset-redundant"`

**Observation** :
Dès le lancement de la simulation avec PSO, le programme génère un volume extrême de logs de type `DEBUG [First]: Selecting link from...` à un rythme de milliers de lignes par seconde, entrant dans ce qui semble être une boucle infinie lors de la sélection/allocation des liens ou hôtes dans `PSO`.

## Étoffement
Grâce au script d'isolation :
- Le batch complet n'a plus besoin d'être exécuté pour observer le problème.
- Nous avons confirmé que PSO est le coupable et qu'il bloque l'exécution sous forme de boucle infinie (`TIMEOUT` lors de `run_comprehensive_benchmark.ps1`).

**Prochaine étape recommandée** : Utiliser ce script pour le _debugging_ du code source ciblant la politique PSO (`org.cloudbus.cloudsim.sdn.policies.vmallocation` / workload policy).

## Correction du Bug PSO (Goulot d'étranglement)
Après investigation du code Java :
1. Nous avons commenté les appels à `System.out.println` dans les stratégies de sélection de liens afin de stopper l'affichage de millions de lignes par seconde.
2. Nous avons introduit un mécanisme de **cache des délais réseau** dans `PSOWorkloadScheduler`. La fonction BFS cherchant le chemin le plus court n'est plus appelée des milliers de fois pour les mêmes paires `(Source, Destination)` à chaque évaluation de particule.

**Résultat** : La simulation `LFF/First/PSO/dataset-redundant` se termine désormais avec succès en **~51 secondes** au lieu de bloquer indéfiniment. Le workflow a repris son bon fonctionnement. L'optimisation a été un franc succès.

### Test de combinaison additionnelle : BwAllocN + PSO
À la demande de l'utilisateur, un run supplémentaire a été lancé pour la combinaison `LFF / BwAllocN / PSO`.
- Commande : `.\run_single_simulation.ps1 -VmPolicy LFF -LinkPolicy BwAllocN -WorkloadPolicy PSO ...`
- **Résultat** : Terminé avec succès en **25.9 secondes**. Les fichiers CSV de métriques (`link_utilization_down.csv`, etc.) ont été générés sans encombre.

### Test des autres combinaisons BwAllocN (RR, SJF, Priority)
À la demande de l'utilisateur, les algorithmes de charge de travail restants ont été simulés via `run_single_simulation.ps1` combinés avec la politique de lien `BwAllocN` et d'allocation de VM `LFF`.
- `LFF` + `BwAllocN` + `RR` : Simulation terminée avec succès en **~65.23 secondes**.
- `LFF` + `BwAllocN` + `SJF` : Simulation terminée avec succès en **~39.8 secondes**.
- `LFF` + `BwAllocN` + `Priority` : Simulation terminée avec succès en **~43.0 secondes**.
- `LFF` + `First` + `SJF` : Simulation terminée avec succès en **~1.55 secondes** (temps réel de calcul, mais ~350s en simulation).

### 4. Fix for Missing Logs (Packet Delay "Null")
For algorithms that finish very quickly in simulation time (like PSO in this specific scenario which finishes in ~9s), the default periodic monitoring interval (10s) was sometimes never triggered before the simulation ended. This resulted in empty `packet_delays.csv` and `qos_violations.csv` files.

**Solution implemented:**
- Modified [LogMonitor.java](file:///e:/Workspace/v2/cloudsimsdn-research/src/main/java/org/cloudbus/cloudsim/sdn/example/LogMonitor.java) to perform a final manual flush of QoS metrics during the `STOP_MONITORING` event.
- This ensures that all accumulated data in `QoSMonitor` is captured and written to disk before the log files are closed.

### 5. Final Consolidated Results
The final figures in `results/2026-03-10/dataset-redundant/figures_consolidated/` now show complete data for all policies.

| Experiment | Energy (Wh) | Avg Latency (s) | SLA Violations | Avg Pkt Delay (ms) |
| :--- | :---: | :---: | :---: | :--- |
| **MFF/BwAllocN/PSO** | **1.11** | 8.88 | 109 | 9769.09 |
| **LFF/BwAllocN/PSO** | 2.01 | 8.88 | 283 | 9889.26 |
| **LWFF/BwAllocN/PSO**| 2.01 | 8.88 | 233 | 9889.30 |
| **MFF/First/PSO** | **27.79** | 8.88 | 109 | 58131.27 |
| **LFF/First/PSO** | 50.29 | 8.88 | 245 | 53875.48 |
| **LWFF/First/PSO**| 50.29 | 8.88 | 233 | 100393.49 |

### 6. Organisation hiérarchique des résultats
À la demande de l'utilisateur, les résultats sont désormais organisés par **politique d'allocation de VM** pour faciliter les comparaisons multi-scénarios (LFF, MFF, LWFF, etc.).

**Nouvelle structure de fichiers :**
- `results/YYYY-MM-DD/dataset-name/LFF/` : Contient toutes les simulations utilisant la politique **LFF**.
- `results/YYYY-MM-DD/dataset-name/MFF/` : Contient toutes les simulations utilisant la politique **MFF**.
- **Comptes rendus locaux** : Chaque dossier de politique (ex: `LFF/figures_consolidated/`) contient ses propres graphes comparatifs internes.
- **Compte rendu global** : Un dossier `figures_consolidated/` à la racine du dataset permet de comparer toutes les politiques entre elles (ex: LFF vs MFF).

**Modifications techniques :**
- `SimpleExampleSelectLinkBandwidth.java` : Le chemin de sortie inclut maintenant la politique de VM comme sous-répertoire.
- `consolidated_report.py` : Le script scanne récursivement les dossiers et génère des rapports à chaque niveau de la hiérarchie.

> [!NOTE]
> **Conclusion du Benchmark (24 sims)** :
> 1.  **MFF** est le grand gagnant sur tous les plans (Énergie et SLA).
> 2.  **LWFF** (Pareto) offre une meilleure stabilité SLA que LFF (233 vs 283) mais ne parvient pas à égaler l'efficacité énergétique de MFF dans ce scénario spécifique.
> 3.  **LFF** reste la politique la moins performante énergétiquement.
