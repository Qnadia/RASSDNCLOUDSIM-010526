# IT41 : Industrialisation du Pipeline et Reporting Scientifique Premium

## Objectifs
- Finaliser l'organisation du répertoire de résultats (stable, sans timestamps).
- Automatiser la génération de la vue topologique.
- Intégrer la conversion PDF avec support complet des images.
- Valider l'impact énergétique du routage dynamique sur dataset stressé.

## Réalisations

### 1. Organisation Stable des Résultats
- **Modifications** : Mise à jour de `1_run_nightly_benchmarks.py` pour supprimer l'horodatage des dossiers.
- **Structure** : `results/DATE/Dataset/VM_Policy/Scenario/`.
- **Gestion des conflits** : Le script Python supprime désormais proactivement le dossier cible avant de lancer Java, évitant ainsi que le code Java n'ajoute son propre suffixe temporel.

### 2. Visualisation de la Topologie
- **Nouvel outil** : `0_generate_topology_view.py`.
- **Fonctions** :
    - Génération d'un schéma graphique (`fig0_topology.png`) via NetworkX.
    - Création d'un descriptif technique des liens (`topology_description.md`).
- **Intégration** : Automatisée en étape 0 du pipeline global.

### 3. Pipeline de Reporting Premium
- **Rapport MD** : Mise à jour de `4_generate_premium_report.py` pour inclure la topologie, le descriptif des liens et une analyse scientifique approfondie du compromis "Détour vs Congestion".
- **Conversion PDF** : Création de `5_generate_pdf_report.py` utilisant `markdown_pdf` avec support des chemins absolus pour garantir l'inclusion correcte de toutes les figures.
- **Workflow Global** : Mise à jour de `run_full_cycle.ps1` pour enchaîner les 6 étapes (Topologie -> Sim -> Consolidate -> Plot -> Report -> PDF).

### 4. Validation Scientifique (Dataset Small-Stressed)
- **Campagne** : 18 scénarios (LFF, MFF, LWFF x First, DynLatBw x Priority, SJF, PSO).
- **Résultats** :
    - **Énergie** : Gain de **7.1%** confirmé pour `DynLatBw` sur les scénarios de congestion.
    - **Latence** : Réduction massive du Queuing Delay malgré des chemins parfois plus longs physiquement.
    - **Stabilité** : 100% de succès sur la campagne.

## État Final
Le système est prêt pour une exploitation directe. Les résultats sont propres, archivés par date, et le rapport PDF est généré automatiquement avec toutes ses illustrations.
