# Itération 19 : Priorité Utilisateur et Modélisation Théorique Avancée

**Date :** 2026-03-13  
**Objectif :** Intégrer la logique de priorité utilisateur et produire un rapport académique détaillé incluant les formules de latence et l'analyse par politique de VM.

## 🚀 Réalisations

### 1. Fonctionnalité de Priorité Utilisateur
- **Java confirmed** : La classe `PriorityWorkloadScheduler.java` est en place et utilisée par défaut dans `SimpleExampleSelectLinkBandwidth.java`.
- **Parsing** : Le `WorkloadParser.java` extrait correctement la 10e colonne (priorité) du CSV.
- **Impact** : Tri des workloads par priorité décroissante avant soumission au Datacenter.

### 2. Modélisation Théorique de la Latence
- Ajout des chapitres détaillés sur les composantes de latence ($D_{proc}$, $D_{prop}$, $D_{trans}$, $D_{queue}$).
- Intégration des extraits de code source Java (`Link.java`, `LinkSelectionPolicyDynamicLatencyBw.java`) dans le rapport d'analyse.

### 3. Restructuration du Rapport d'Analyse
- **Analyse par Politique d'Allocation** : Chapitres dédiés à MFF, LFF et LWFF.
- **Synthèse Best-in-Class** : Identification du combo **LWFF + BwAllocN + SJF** comme le plus performant pour la latence.
- **Analyse Approfondie** : Clarification sur le mapping physique/virtuel et l'utilité des métriques dynamiques pour la QoS.

### 4. Visualisation Haute Fidélité corrigée
- Correction du script `Python-V2/impact_analysis_plots.py` pour utiliser le **Délai Paquet Moyen (ms)** au lieu de la latence globale, rendant visible la réduction spectaculaire de 90%.

## 📂 Fichiers mis à jour
- [Analyse_Selection_Liens.md](file:///e:/Workspace/v2/cloudsimsdn-research/docs/analysis/Analyse_Selection_Liens.md)
- [Rapport_Analyse_SDN_V19.docx](file:///e:/Workspace/v2/cloudsimsdn-research/docs/analysis/Rapport_Analyse_SDN_V19.docx)
- [impact_analysis_plots.py](file:///e:/Workspace/v2/cloudsimsdn-research/Python-V2/impact_analysis_plots.py)
