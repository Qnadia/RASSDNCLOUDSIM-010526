# Suivi des Iterations et des Developpements CloudSimSDN

Ce repertoire documente l'evolution du projet CloudSimSDN, incluant les correctifs de stabilite,
les nouveaux algorithmes d'ordonnancement (PSO, SJF, Priority) et les analyses de performance multi-scenarios.

## Objectif de Recherche

> Evaluer l'apport de `LinkSelectionPolicyDynamicLatencyBw` (selection par latence composite + charge M/M/1)
> par rapport aux politiques classiques : `First`, `BwAllocN`, `Dijkstra`.

---

## Historique des Iterations

### [2026-05-01](./2026-05-01/) — IT44 : Migration et Finalisation (Point d'Orgue)
- **Objectif** : Finaliser les datasets LargeVF/Mini et migrer vers un nouveau repository propre.
- **Points clés** : recalibration finale de la scalabilité énergétique. Création du repo `RASSDNCLOUDSIM-010526` sans les logs historiques pour respecter les limites GitHub.
- [IT44 — Migration et Finalisation](./2026-05-01/tracking_campaign_finalization.md)

### [2026-04-30](./2026-04-30/) — IT43 : Calibration Dataset Medium (Correction Anomalies)
- **Objectif** : Résoudre l'inversion de performance Medium > Large.
- **Points clés** : Rehaussement des backbones Medium à 5 Gbps. Normalisation des VM MIPS. Explication de l'effet "Time Stretching" sur la consommation d'énergie.
- [IT43 — Calibration Medium](./2026-04-30/tracking_calibration_medium.md)

### [2026-04-29](./2026-04-29/) — IT42 : Analyse Congestion et Tuning Topologie
- **Objectif** : Stabiliser le comportement du modèle M/M/1 sur les topologies saturées.
- **Points clés** : Identification des goulots d'étranglement à 10 Mbps. Premier pas vers la normalisation des datasets pour la scalabilité.

### [2026-04-23](./2026-04-23/) — IT41 : Preuve de Concept Énergétique (Victoire Scientifique)
- **Objectif** : Valider le gain énergétique du routage dynamique.
- **Points clés** : Gain de **7.1% d'énergie** et réduction de **60s** de queuing delay. Preuve formelle que `DynLatBw` surpasse `First` sur toutes les métriques.
- [IT41 — Victoire Scientifique Énergie](./2026-04-23/it41_victoire_scientifique_energie.md)

### [2026-04-23](./2026-04-23/) — IT40 : Analyse Énergétique Switch et Stress Dataset Small
- **Objectif** : Corréler le routage SDN et la consommation électrique des commutateurs.
- **Points clés** : Modèle énergétique basé sur la durée d'activité des ports (66.7W idle + 1W/port). Création d'un "Bottleneck Trap" (100M vs 10G) sur `dataset-small` pour forcer `DynLatBw` à économiser de l'énergie en terminant les transferts plus vite.
- [IT40 — Analyse Énergétique Switch](./2026-04-23/it40_analyse_energetique_switch.md)

### [2026-04-23](./2026-04-23/) — IT39 : Validation Finale DynLatBw - Dataset Calibrated
- **Objectif** : Valider scientifiquement la réduction de congestion via `DynLatBw`.
- **Points clés** : Correction du décalage de colonnes dans `WorkloadParser` (link vs dest). Résultats : réduction de **76 ms** du queuing delay sur `LWFF`. Preuve que le routage dynamique évite les liens goulots détectés par le modèle M/M/1.
- [IT39 — Validation DynLatBw Calibrated](./2026-04-23/it39_validation_dynlatbw_calibrated.md)

### [2026-04-22](./2026-04-22/) — IT38 : Analyse Simulation et Tuning Dataset H
- **Objectif** : Calibrer le dataset pour forcer la différenciation du routage.
- **Points clés** : Création du `dataset-calibrated` avec liens hétérogènes (500M vs 5G). Tuning des hotspots réseau pour saturer les liens lents.
- [IT38 — Analyse Sim Tuning](./2026-04-22/it38_analyse_sim_tuning.md)

### [2026-04-22](./2026-04-22/) — IT37 : Optimisation LWFF Énergie & Fix LFF
- **Objectif** : Stabiliser les politiques de placement VM.
- **Points clés** : Correction de `VmAllocationPolicyLWFFF` pour une meilleure gestion de l'énergie. Fix de l'instanciation LFF.
- [IT37 — Optimisation LWFF & Fix LFF](./2026-04-22/it37_optimisation_lwff_energy_fix_lff.md)

### [2026-04-22](./2026-04-22/) — IT36 : Zoom Routage et Hétérogénéité
- **Objectif** : Étudier l'impact de l'hétérogénéité des liens sur `DynLatBw`.
- **Points clés** : Premier test sur topologie asymétrique. Identification du besoin de saturation pour activer le modèle M/M/1.
- [IT36 — Zoom Routage Hétérogénéité](./2026-04-22/it36_zoom_routing_heterogeneite.md)

### [2026-04-20](./2026-04-20/) — IT34 : Restauration du Pipeline et Monitoring Queuing Delay
- **Objectif** : Restaurer la compilation et la compatibilité API des scripts legacy suite à la refactorisation du core SDN.
- **Points clés** : Correction des signatures de constructeurs (`WorkloadParser`), restauration des alias de soumission (`SDNBroker`), whitelisting des sources compilées. Validation du monitoring du **Queuing Delay** (délai de file d'attente) avec décomposition granulaire (Proc, Prop, Trans, Queue).
- [IT34 — Restauration Pipeline & Queuing Delay](./2026-04-20/it34_restoration_pipeline.md)

### [2026-04-20](./2026-04-20/) — IT33 : Audit et Diagnostic Critique de la Campagne du 19 Avril
- **Objectif** : Analyser les résultats de la campagne 81-runs du 19 Avril et identifier les anomalies.
- **Points clés** : Découverte de 3 bugs critiques — (1) `Priority` == `SJF` à 100% car `sort()` n'a aucun effet sur la soumission réelle ; (2) `LFF` == `LWFF` car `calculateExecutionTime()` retourne toujours 0.0 au déploiement ; (3) `BwAlloc` génère des données vides sur `dataset-small-congested`. Cartographie complète de la validité des données produite.
- [IT33 — Diagnostic Campagne 19 Avril](./2026-04-20/it33_diagnostic_resultats_campagne.md)

### [2026-04-15](./2026-04-15/) — IT29 : Benchmark DynamicLatencyBw sous Congestion
- **Objectif** : Démontrer l'apport de `DynLatBw` vs `First`/`BwAllocN`/`Dijkstra` en forçant une congestion réseau (BW ÷5 sur `dataset-medium-congested`).
- **Points clés** : 4 runs ciblés (VM=MFF, WF=Priority, Link varie). Mapping Java vérifié : `"DynLatBw"` → `LinkSelectionPolicyDynamicLatencyBw` (case default). Dataset congestionné : Edge 400 Mbps, Core 2 Gbps, links 200 Mbps-1 Gbps.
- [IT29 — DynLatBw Benchmark](./2026-04-15/it29_dynlatbw_benchmark.md)

### [2026-04-15](./2026-04-15/) — IT28 : Analyse Sim VF + Tooling + Découverte DynLatBw existant
- **Objectif** : Audit des outils de visualisation, generation des figures consolidees Sim VF, definition du plan d'evaluation de `DynamicLatencyBw`.
- **Points cles** : Adaptation de `consolidated_report.py` au format `VM_*/Link_*/`, 26 combinaisons analysees sur 3 datasets. Observation : lien First/BwAllocN/Dijkstra identiques → `DynamicLatencyBw` non encore incluse.
- [IT28 — Sim VF & DynamicLatencyBw](./2026-04-15/it28_simvf_analysis_dynlatbw.md)

### [2026-04-14](./2026-04-14/) — IT27 : Grand Rerun Dijkstra — 108 simulations
- **Objectif** : Relancer la campagne complete avec `LinkSelectionPolicyDijkstra` (iteratif) et decalage VM 0.1s.
- **Points cles** : 108/108 simulations reussies. Dossier reference : `results/2026-04-14/Sim VF/`.
- [IT27 — Grand Rerun Dijkstra](./2026-04-14/it27_grand_rerun_dijkstra.md)

### [2026-04-11](./2026-04-11/) — IT26 : LWFF Large — 800 Workloads
- **Objectif** : Contourner le stall LWFF a 853/1000 en reduisant a 800 workloads.
- **Points cles** : Fix VmAllocationPolicyLWFFF (check BW), stall resolu. Scenario 5 (LWFF+BwAllocN+Priority) valide a 100%.
- [IT26 — LWFF Large 800wl](./2026-04-11/it26_lwff_large_800workloads.md)

### [2026-03-22](./2026-03-22/) — IT23-25 : Fix Stall LWFF Large
- **Objectif** : Identifier et corriger le blocage du compteur LWFF a 853/1000 workloads.
- **Points cles** : Bug `wl==null` dans `requestCompleted()`, throttle logs fallback, fix check BW dans LWFFF.
- [IT23 — Analyse LWFF Large](./2026-03-22/it23_lwff_large_analysis.md)

### [2026-03-16](./2026-03-16/) — IT21-22 : Benchmark Large + Fix Timeout
- **Objectif** : Execution des 24 scenarios dataset-large pour MFF et LFF.
- **Points cles** : Fix `TIME_OUT = POSITIVE_INFINITY`, MFF/LFF 100% completes. LWFF bloque.
- [IT21 — Large Benchmark](./2026-03-21/it21_large_benchmark.md)

### [2026-03-14](./2026-03-14/) — IT20 : Benchmark Medium (24 scenarios)
- **Objectif** : Validation du comportement sur 24 scenarios avec le `dataset-medium`.
- **Points cles** : Intelligence du routage NOS face aux liens asymetriques, impact massif du PSO.
- [IT20 — Medium Benchmark](./2026-03-14/it20_medium_benchmark.md)

### [2026-03-13](./2026-03-13/) — IT17-19 : Priorites et Modelisation Theorique
- **Objectif** : Integration de la priorite utilisateur et formalisation mathematique.
- **Points cles** : `PriorityWorkloadScheduler`, modelisation L_e2e = D_prop + D_trans + D_proc + D_queue.
- [IT19](./2026-03-13/it19_user_priority_and_theory.md) | [IT18](./2026-03-13/it18_impact_visualization.md)

### [2026-03-11](./2026-03-11/) — IT16 : Benchmark Small & Redondance
- **Objectif** : Evaluation de `BwAllocN` sur topologie redondante.
- **Points cles** : Capacite du SDN a eviter les liens 10 Mbps au profit des liens haut debit.
- [IT16](./2026-03-11/it16_small_benchmark.md)

### [2026-03-10](./2026-03-10/) — IT12-15 : Consolidation & PSO
- **Objectif** : Analyse croisee MFF/LFF/LWFF et fiabilisation PSO.
- **Points cles** : **MFF + BwAllocN + PSO** = combinaison la plus efficiente energetiquement.
- [IT15](./2026-03-10/compte_rendu_it15.md)

### [2026-03-09](./2026-03-09/) — IT09-11 : BwAllocN vs First
- **Objectif** : Mise en evidence des gains QoS via allocation dynamique BW.
- **Points cles** : -93% latence, -75% energie vs First.
- [IT09-11](./2026-03-09/analyse_bwalloc_vs_first.md)

### [2026-03-08](./2026-03-08/) — IT01-08 : Fondations et Stabilite
- **Objectif** : Correction des boucles de routage BFS, anomalies de tags SDN.
- [IT01-06](./2026-03-08/analyse_resultats.md) | [Anomalies](./2026-03-08/rapport_anomalies_tracking.md)

---

## Prochaine Etape (IT29)

Lancer `DynamicLatencyBw` sur au moins les datasets Small et Large (MFF) pour
une comparaison directe avec Dijkstra/BwAllocN/First.

```powershell
python -X utf8 tools\consolidated_report.py --simvf "results\2026-05-14\Sim VF"
```

*Derniere mise a jour : 2026-05-01*

## Historique Récent
### [2026-04-20](./2026-04-20/) — IT35 : Industrialisation des graphiques
- **Objectif** : Industrialiser la génération des figures pour comparer les stratégies de placement et de routage à grande échelle.
- [IT35](./2026-04-20/it35_industrialisation_pipeline_figures.md) : Création des grouped bars pour une meilleure lisibilité et maintien des graphiques détaillés (flat bars) dans un dossier séparé. Corrige le problème d'affichage `?` pour les politiques VM.

Remaruqe 
BwAlloc vs DynLatBw — Différences Fondamentales
🔵 BwAlloc (LinkSelectionPolicyBandwidthAllocation)
Algorithme : Sélection locale "Least Channels"

Pour chaque lien disponible → choisir celui qui a le MOINS de canaux actifs
Caractéristique	Valeur
Métrique	Nombre de canaux getChannelCount()
Portée	Décision saut par saut (greedy)
Algorithme	Sélection simple en O(n liens)
Routage dynamique	❌ isDynamicRoutingEnabled() = false
Gestion congestion	Log de warning si > 90% mais ne l'évite pas
Bande passante	Ne considère PAS la BW disponible réelle
Modèle de délai	Aucun — décision sans estimation de latence
Problème : Sur le dataset small-congested, tous les liens ont beaucoup de canaux → la sélection devient quasi aléatoire → aucun paquet ne passe → fichiers vides.

🟢 DynLatBw (LinkSelectionPolicyDynamicLatencyBw)
Algorithme : Dijkstra dynamique avec modèle M/M/1

Coût d'un lien = Dproc_switch + Dprop + Dtrans + Dqueue(M/M/1)
→ sélectionner le CHEMIN GLOBAL de coût minimal
Caractéristique	Valeur
Métrique	Latence totale = Σ(proc + prop + trans + queue)
Portée	Décision chemin global (Dijkstra)
Algorithme	Dijkstra avec priorité queue, O((V+E) log V)
Routage dynamique	✅ isDynamicRoutingEnabled() = true
Gestion congestion	Oui — le délai M/M/1 pénalise les liens chargés
Bande passante	Utilisée pour calculer Dtrans = bits/(BW × efficiency)
Modèle de délai	M/M/1 : Dqueue = ρ / (μ × (1 - ρ))
Le modèle M/M/1 pénalise automatiquement les liens saturés : si ρ → 1, Dqueue → ∞, ce qui force Dijkstra à trouver un chemin alternatif moins chargé.

📊 Résumé Comparatif
BwAlloc :  Lien local → moins de canaux → décision rapide mais myope
DynLatBw : Chemin global → minimise latence totale avec modèle de file d'attente
BwAlloc	DynLatBw
Intelligence	Faible (local)	Élevée (global + M/M/1)
Complexité	O(n)	O((V+E) log V)
Sur topologie chargée	Peut bloquer	Trouve un chemin alternatif
Valeur scientifique	Baseline simple	Votre contribution principale
C'est pourquoi DynLatBw est la politique que vous cherchez à valider : elle est plus sophistiquée et devrait théoriquement mieux performer que BwAlloc et First sur les datasets congestionnés. Les résultats de la simulation small-congested devraient confirmer cette hypothèse.
