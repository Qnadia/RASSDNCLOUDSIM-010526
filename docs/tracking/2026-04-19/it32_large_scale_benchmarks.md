# Itération 32 : Exécution des Benchmarks à Grande Échelle et Analyse Comparative (VF)
**Date :** 19 Avril 2026

## 🎯 Objectifs de l'itération
* Finaliser et stabiliser le script d'automatisation des benchmarks nocturnes (`run_nightly_benchmark.ps1`).
* S'assurer que la structure des dossiers générés respecte scrupuleusement la convention établie le 14 Avril (`results/YYYY-MM-DD/<dataset>/<VM_Policy>/experiment_<VM>_<Link>_<WF>`).
* Exécuter l'ensemble des combinaisons de politiques de routage et de placement sur les datasets **Small-Congested** et **Medium-Congested** pour les charges de travail **PSO** et **SJF**.
* Consolider tous les résultats finaux dans un répertoire unique `VF` (Version Finale).
* Produire une analyse comparative définitive entre l'algorithme `First` et la solution optimisée `DynLatBw` basée sur le modèle M/M/1.

## 🛠️ Actions réalisées

### 1. Refactorisation et Stabilisation de l'Infrastructure de Test
*   **Correction du script PowerShell** : Ajustement de la logique de nommage des dossiers de résultats pour éviter les doublons (`datasetsdataset-xxx`) et correspondre au format standard.
*   **Résolution d'erreurs Java critiques** : Correction d'une erreur de compilation (`NoClassDefFoundError`) due à des processus Java bloquants, en forçant le nettoyage et la recompilation propre du projet Maven (`mvn clean compile` & `mvn dependency:copy-dependencies`).
*   **Optimisation des temps d'exécution** : Ciblage spécifique des politiques `SJF` sur le dataset `Small` puis `Medium`, avec désactivation temporaire de la génération de graphiques intermédiaires pour accélérer les tests.

### 2. Centralisation des Données (Dossier VF)
*   Fusion complète des résultats générés ce jour (19 Avril) avec les historiques de la politique `Priority` obtenus précédemment.
*   Création de l'arborescence définitive :
    *   `results/VF/dataset-small-congested/`
    *   `results/VF/dataset-medium-congested/`
    *   `results/VF/dataset-large/`

### 3. Génération des Rapports Premium
*   Mise à jour du script `generate_premium_report.py` pour pointer vers les données consolidées du répertoire `VF`.
*   Génération avec succès des rapports complets au format Markdown et conversion en PDF (`Simulation_Report_Small.pdf` et `Simulation_Report_Medium.pdf`).

## 📊 Conclusions d'Analyse (DynLatBw vs First)

L'analyse des graphiques générés (notamment `fig6_routing_delay` et `fig7_routing_energy`) a permis de tirer deux conclusions majeures pour la publication des travaux de recherche :

1.  **Validation de l'approche sur le Dataset Small** :
    *   Sur une topologie offrant des chemins alternatifs, `DynLatBw` surclasse largement la politique par défaut (`First`).
    *   Le **délai réseau est divisé par 3,6** (passant de ~64s à ~17s).
    *   La **consommation énergétique chute drastiquement** (de 64 Wh à 20 Wh) grâce à l'écoulement plus rapide du trafic.

2.  **Mise en évidence d'une limitation topologique sur le Dataset Medium** :
    *   Les résultats de `DynLatBw` et `First` se sont révélés identiques sur le dataset Medium (délais stagnants à ~26s).
    *   Ce phénomène met en lumière le fait que l'intelligence du plan de contrôle SDN est inefficace si la topologie physique sous-jacente (Data Plane) ne dispose pas de chemins redondants (goulot d'étranglement en bordure de réseau - Edge Bottlenecks). Cette saturation uniforme de tous les chemins physiques possibles rend caduque toute tentative d'optimisation logicielle.

## ⏭️ Prochaines étapes
*   Intégration de cette analyse et des figures clés (1, 2 et 3) dans le manuscrit final ou l'article de recherche.
*   (Optionnel) Ajustement de la topologie du dataset `Medium` pour y introduire des liens alternatifs, afin de prouver que `DynLatBw` reprend l'avantage dès qu'une route de contournement physique existe.
