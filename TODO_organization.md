# To-Do: Organisation du Projet CloudSimSDN-Research

Ce document récapitule les recommandations d'organisation pour une exécution ultérieure.

## 1. Restructuration des Dossiers
- [ ] Centraliser tous les scripts PowerShell et Python dans `/scripts/`.
- [ ] Créer un dossier `/data/` subdivisé en `topologies/` et `workloads/`.
- [ ] Isoler les sorties brutes dans `/logs/` (et ajouter au `.gitignore`).
- [ ] Organiser les résultats traités dans `/results/` (par date ou par campagne).
- [ ] Centraliser la documentation technique et les rapports dans `/docs/`.

## 2. Nettoyage de la Racine
- [ ] Déplacer les fichiers CSV de résultats (`batch_run_summary.csv`, etc.) vers `/results/`.
- [ ] Déplacer les fichiers de log de debug (`debug_sim.log`, `last_small_test.log`) vers `/logs/debug/`.
- [ ] Supprimer les fichiers temporaires inutiles (`cp.txt`, `sources.txt`).

## 3. Amélioration du Code Java
- [ ] Analyser les exclusions massives dans `pom.xml` et tenter de restaurer les dépendances ou supprimer le code mort.
- [ ] Documenter le mapping des politiques de sélection de lien dans le code source.

## 5. Iterations de Recherche
- [x] **Small Dataset (Congestionné)** : Amélioration ~10.6% de la latence avec DynLatBw. (23 Avril)
- [x] **Medium Dataset (Congestionné)** : Amélioration ~15.7% de la latence avec DynLatBw. (24 Avril)
- [x] **Large Dataset (Congestionné)** : Validation éclatante de DynLatBw à grande échelle. (27 Avril)
    - [x] Planification et calcul du facteur de congestion (10x).
    - [x] Adaptation des fichiers d'entrée (`physical.json` avec "Trappe de Congestion" 50Mbps/100ms).
    - [x] Lancement de la campagne complète (18 simulations : VM x Routing x WF).
    - [x] Consolidation, correction du calcul d'énergie (agrégation `.last()`) et génération des 13 figures.
    - [x] Centralisation des résultats dans `/results/2026-04-27/plots/`.

- [x] **Mini Dataset (Recalibration)** : Re-calibrer pour montrer l'impact BLA vs First (1 Mai)
    - [x] Adaptation de `physical.json` (Goulot Path A 50Mbps vs Backbone Path B 800Mbps).
    - [x] Suppression des résultats obsolètes du 01/05.
    - [x] Exécution et génération du rapport scientifique premium (+19.1% performance).
