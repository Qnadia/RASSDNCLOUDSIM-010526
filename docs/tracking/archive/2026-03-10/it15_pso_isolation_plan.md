# IT15 - Plan d'implémentation pour l'isolation des simulations (Focus PSO)

## Contexte et Problème
Lors de l'exécution complète du script `run_comprehensive_benchmark.ps1`, la simulation a tendance à bloquer ou à atteindre un timeout complet, en particulier lorsque la politique **PSO** (Particle Swarm Optimization) est sélectionnée. Actuellement, le script exécute toutes les combinaisons séquentiellement de manière automatique. En cas de blocage d'une politique, l'enchaînement est perturbé et l'analyse devient difficile.

## Objectif
Permettre le lancement de chaque simulation de manière isolée "à la carte". Cela permettra :
1. De suivre l'exécution d'une simulation précise (ex: `PSO`) pour repérer le moment exact du blocage.
2. D'éviter de refaire tourner toutes les simulations si une seule échoue.
3. D'avoir un meilleur contrôle sur l'exécution.

## Plan d'implémentation proposé

### Étape 1 : Création d'un script d'exécution unitaire (`run_single_simulation.ps1`)
Créer un nouveau script PowerShell dédié à l'exécution d'**une seule** simulation. Ce script prendra en charge des arguments pour éviter d'avoir à modifier le code à chaque fois.
* **Paramètres attendus :** `-VmPolicy`, `-LinkPolicy`, `-WorkloadPolicy`, `-Dataset`
* **Comportement :**
  * Construira le classpath.
  * Lancera la classe `SimpleExampleSelectLinkBandwidth` avec les paramètres spécifiés.
  * Affichera la sortie standard (logs) directement dans la console (sans la masquer) ou redirigera dans un fichier de log tout en affichant la progression, afin qu'on puisse voir **sur quoi ça bloque** en temps réel.

### Étape 2 : Création d'un index d'exécution contrôlé (`run_selected_simulations.ps1` ou `.bat`)
Au lieu d'utiliser des boucles imbriquées `foreach` exécutant toutes les $2 \times 2 \times 4$ combinaisons, nous pouvons définir une liste explicite des simulations ou utiliser le script `run_single_simulation.ps1` pour lancer manuellement la combinaison posant problème.
* **Exemple d'utilisation :**
  `.\run_single_simulation.ps1 -VmPolicy LFF -LinkPolicy First -WorkloadPolicy PSO -Dataset "dataset-redundant"`

### Étape 3 : Investigation au niveau du code Java (PSO)
Une fois l'exécution isolée mise en place, lancer la simulation PSO posant le plus de problèmes avec le script unitaire.
* Identifier l'étape où le programme se fige (ex: création des VMs, allocation des requêtes au sein de l'algorithme PSO, boucle infinie...).
* Si le temps d'exécution est justifié (complexité de PSO), on pourra augmenter le paramètre `Timeout`. S'il s'agit d'une boucle infinie, corriger la logique au sein de l'allocation PSO.

## Validation requise
Êtes-vous d'accord pour que je commence par créer le script `run_single_simulation.ps1` conforme à ce plan ? Cela vous permettra immédiatement d'isoler la combinaison qui bloque.

## Résultat de l'Investigation (Mis à jour)
L'investigation avec le script unitaire a révélé que la simulation PSO "ne bloquait pas" dans une boucle infinie à proprement parler, mais souffrait d'un **goulot d'étranglement majeur** :
1. La sélection de lien (`findBestPath`) affichait des journaux (logs) à chaque saut réseau dans le BFS.
2. PSO, via sa méthode `fitness`, appelle la recherche de chemin réseau _des dizaines de milliers de fois_ (pour chaque particule et chaque itération).
3. L'exécution tentait d'écrire des millions de lignes de logs `System.out.println("DEBUG [...] Selecting link...")` dans la console en quelques secondes.

### Corrections Apportées
1. **Suppression des logs abusifs** : Les lignes `System.out.println` ont été commentées dans `LinkSelectionPolicyFirst.java` et `LinkSelectionPolicyBandwidthAllocationN.java`.
2. **Mise en cache du délai réseau** : Un `Map<String, Double>` (netDelayCache) a été introduit dans `PSOWorkloadScheduler.java`. Plutôt que de rechercher le chemin réseau à chaque fois pour la même paire (Source, Destination), PSO réutilise désormais la valeur en mémoire.
3. **Correction des métriques manquantes (Packet Delay null)** : Pour les algorithmes rapides (finissant en moins de 10s), le moniteur périodique ne se déclenchait pas. Un déclenchement manuel de `monitorQoSMetrics()` a été ajouté lors de l'arrêt de la simulation dans `LogMonitor.java` pour garantir le flush des dernières données de `QoSMonitor`.

### Stratégie de validation finale
L'exécution isolée `LFF + First + PSO` via PowerShell termine maintenant la simulation avec succès en **~51 secondes**. Les exécutions avec **BwAllocN** sont encore plus rapides (~26s pour PSO) et montrent une complétude totale des métriques (SLA, Packet Delay, Consommation).

### Synthèse des Résultats Finalisée (Benchmark 24 sims)
| Expérience | Énergie (Wh) | Latence Moy. (s) | Violations SLA | Délai Pkt Moy. (ms) |
| :--- | :---: | :---: | :---: | :---: |
| **MFF/BwAllocN/PSO** | **1.11** | 8.88 | 109 | 9769.09 |
| **LFF/BwAllocN/PSO** | 2.01 | 8.88 | 283 | 9889.26 |
| **LWFF/BwAllocN/PSO**| 2.01 | 8.88 | 233 | 9889.30 |
| **MFF/First/PSO** | **27.79** | 8.88 | 109 | 58131.27 |
| **LFF/First/PSO** | 50.29 | 8.88 | 245 | 53875.48 |
| **LWFF/First/PSO**| 50.29 | 8.88 | 233 | 100393.49 |

> [!TIP]
> **Conclusion** : La politique **MFF** reste l'option la plus performante pour la réduction d'énergie (-45% vs LFF) et la fiabilité QoS. **LWFF** (Pareto) apporte une légère amélioration de la stabilité SLA par rapport à LFF (+17% de succès), mais n'optimise pas autant l'énergie que MFF.

### Organisation hiérarchique des résultats
À la demande de l'utilisateur, les résultats sont désormais organisés par **politique d'allocation de VM** pour faciliter les comparaisons multi-scénarios (LFF, MFF, LWFF, etc.).

**Nouvelle structure de fichiers :**
- `results/YYYY-MM-DD/dataset-name/LFF/` : Contient toutes les simulations utilisant la politique **LFF**.
- `results/YYYY-MM-DD/dataset-name/MFF/` : Contient toutes les simulations utilisant la politique **MFF**.
- **Comptes rendus locaux** : Chaque dossier de politique (ex: `LFF/figures_consolidated/`) contient ses propres graphes comparatifs internes.
- **Compte rendu global** : Un dossier `figures_consolidated/` à la racine du dataset permet de comparer toutes les politiques entre elles (ex: LFF vs MFF).

**Modifications techniques clés :**
- **LogMonitor.java** : Ajout d'un déclenchement manuel de `monitorQoSMetrics()` lors de l'événement `STOP_MONITORING` pour capturer les métriques des simulations très rapides.
- **SimpleExampleSelectLinkBandwidth.java** : Inclusion de la politique de VM (`vmAlloc`) dans le chemin de sortie.
- **consolidated_report.py** : Support du scan récursif pour générer des figures à tous les niveaux de la hiérarchie.
