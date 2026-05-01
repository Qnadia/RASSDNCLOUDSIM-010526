# Pipeline de Simulation et d'Analyse (1-2-3-4)

Ce guide décrit le workflow standard pour produire des résultats scientifiques validés dans CloudSimSDN.

---

## Étape 1 : Simulation Matrix (`1_run_nightly_benchmarks.py`)
Lance une série de simulations croisant les politiques VM, Routage et Workload sur les datasets sélectionnés.
```powershell
python tools/analysis/1_run_nightly_benchmarks.py
```
*   **Datasets inclus** : `dataset-calibrated`, `medium-congested`, `large`.
*   **Sortie** : Un nouveau dossier daté dans `results/YYYY-MM-DD/`.

---

## Étape 2 : Consolidation des Données (`2_consolidate_results.py`)
Agrège les résultats de toutes les simulations individuelles en fichiers maîtres utilisables pour les graphiques.
```powershell
python tools/analysis/2_consolidate_results.py results/2026-04-23/
```
*   **Action** : Crée `consolidated_summary.csv` et les dossiers `GLOBAL_CONSOLIDATION`.
*   **Intelligence** : Calcule les moyennes, gère les unités et extrait les métadonnées des chemins.

---

## Étape 3 : Visualisation Scientifique (`3_generate_global_plots.py`)
Génère la batterie complète de graphiques comparatifs.
```powershell
python tools/analysis/3_generate_global_plots.py results/2026-04-23/
```
*   **Graphiques clés** : 
    - `fig10_queuing.png` : Impact du routage sur le délai de file d'attente.
    - `fig9_tradeoff.png` : Compromis Énergie vs Latence.
    - `fig3_sla.png` : Violations des accords de niveau de service.

---

## Étape 4 : Génération de Rapport Premium (`4_generate_premium_report.py`)
Compile les données et les graphiques dans un rapport Markdown structuré pour analyse.
```powershell
python tools/analysis/4_generate_premium_report.py results/2026-04-23/
```
*   **Sortie** : `GLOBAL_SCIENTIFIC_REPORT.md` (prêt pour archivage ou discussion).

---

## Outils d'Analyse Profonde (Optionnels)
Si vous avez besoin de "zoomer" sur un comportement spécifique :

- **Zoom Routage vs Workload** : Pour voir comment chaque politique de lien réagit à une charge spécifique.
  ```powershell
  python tools/analysis/zoom_routing_vs_workload.py results/2026-04-23/dataset-calibrated/
  ```
- **Inspection de Topologie** :
  ```powershell
  python tools/topology/inspect_topology.py datasetsH/dataset-calibrated/physical.json
  ```

---
**Configuration Requise :**
Assurez-vous que l'encodage est activé pour les scripts Python :
`$env:PYTHONIOENCODING="utf-8"`
