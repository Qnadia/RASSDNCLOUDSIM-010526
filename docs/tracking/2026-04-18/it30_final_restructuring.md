# Itération 30 — Restructuration Finale (18-04-2026)

## Objectif
Réorganiser le projet pour une structure claire, modulaire et professionnelle.

## Changements Effectués

### 1. Architecture des Dossiers
Les fichiers racines ont été regroupés dans des sous-répertoires thématiques sous `tools/` :
- **`tools/simulation/`** : Pilotage des expériences (`run_*.ps1`).
- **`tools/analysis/`** : Post-traitement et génération de figures (`*.py`).
- **`tools/topology/`** : Générateurs et utilitaires de topologies.
- **`datasets/`** : Centralisation de toutes les topologies (Small, Medium, Congested).

### 3. Optimisation du Benchmark
Le script `tools/simulation/run_dynlatbw_benchmark.ps1` a été mis à jour :
- **Politique de placement** : Bascule de `MFF` vers **`Spread`** (désormais la politique recommandée pour mettre en évidence `DynLatBw`).
- **Chemins dynamiques** : Utilisation de `$PSScriptRoot` pour une portabilité totale.
- **Accès Datasets** : Pointage vers le nouveau dossier `datasets/`.

## Prochaine Étape
Exécuter un dernier run complet sur la topologie asymétrique avec la politique `Spread` pour clore le chapitre des résultats.

*Créé le 2026-04-18 par Antigravity*
