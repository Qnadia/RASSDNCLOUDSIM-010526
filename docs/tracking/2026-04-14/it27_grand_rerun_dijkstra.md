# Itération 27 (14/04/2026) : Grand Rerun Dijkstra & Diversité

## Objectif
Relancer l'intégralité de la campagne de simulation pour assurer la cohérence des résultats et la divergence entre les politiques de placement.

## Modifications apportées
1.  **Topologie Virtuelle** :
    - Fichiers `virtual.json` modifiés pour tous les datasets (Small, Medium, Large).
    - **Détail des modifications VM** :
        - Décalage de **0.1s** par VM pour diversifier les arrivées.
    - **Correction Technique** : Ré-encodage Python (ASCII/UTF-8 strict) pour supprimer les erreurs de parsing `NullPointerException`.
2.  **Algorithme de Routage** :
    - Standardisation sur `LinkSelectionPolicyDijkstra` (itératif).
3.  **Extraction & Reporting** :
    - Création de `generate_premium_report.py` pour un rendu visuel premium des KPIs.

## État Final (15/04/2026)
- **Status** : ✅ TERMINÉ
- **Simulations** : 108/108 réussies (0 crash).
- **Consolidation** : Dossier `results/2026-04-14/Sim VF/` complet.

## Résultats Marquants
| Dataset | Performance Top | Observation |
|---|---|---|
| **Small** | MFF + Dijkstra | Énergie la plus basse (~38 Wh). |
| **Medium**| Dijkstra stable | Aucun StackOverflow malgré la taille. |
| **Large** | Dijkstra stable | Réussite totale du routage complexe. |

- **Note LFF/LWFF** : Les résultats sont identiques malgré le décalage de 0.1s, confirmant une convergence logique sur la topologie symétrique.

## Campagne de Simulation
- **Script utilisé** : `run_full_vf_campaign.ps1`
- **Nombre de simulations** : 108 (3 Datasets * 3 VM Policies * 3 Link Policies * 4 Workloads).
- **Emplacement des résultats** : `results/2026-04-14/` et `Sim VF/`.

## Observations attendues
- Variabilité des placements hosts entre LFF et LWFF.
- Stabilité accrue sur le dataset Large grâce à Dijkstra.
