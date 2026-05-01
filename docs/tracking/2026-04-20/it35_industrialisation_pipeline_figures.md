# IT35 - Industrialisation du Pipeline de Benchmarking & Figures

## Contexte
Suite à la validation des corrections algorithmiques (IT33 et IT34), l'objectif était d'industrialiser la génération des graphiques scientifiques pour la campagne massive de 81 scénarios, afin de pouvoir comparer visuellement et de manière fiable `First`, `BwAlloc` et `DynLatBw`.

## Problèmes identifiés
1. Le script précédent générait des graphiques plats de 27 barres côte à côte, ce qui rendait les labels illisibles sur l'axe X (surcharge visuelle).
2. La variable `vm_policy` n'était pas correctement conservée ou mappée lors de la consolidation de l'énergie et des KPIs, générant une colonne unifiée étiquetée `?` au lieu de `LFF`, `MFF` et `LWFF`.
3. L'analyse des résultats sur le dataset `small-congested` indiquait des résultats contradictoires (où `First` et `DynLatBw` donnaient des résultats similaires), car la topologie était trop simple/petite pour exploiter l'algorithme global de `DynLatBw`.

## Actions réalisées
1. **Refonte du script `3_generate_global_plots.py`** :
   - Migration de `fig1` à `fig5` vers des graphiques en barres groupées (Grouped Bars) via Seaborn.
   - Groupement par politique d'allocation de VM (`vm_policy`) sur l'axe X, et distinction par politique de routage (`link_policy`) avec des couleurs.
   - L'axe X montre désormais clairement 3 groupes distincts (LFF, MFF, LWFF), offrant un rendu de niveau publication scientifique.

2. **Correction des variables et Parsing** :
   - Alignement exact de la logique de lecture du `GLOBAL_MASTER_*.csv` pour s'assurer que le champ `vm_policy` est correctement identifié (résolution du bug affichant `?`).

3. **Conservation des "Vues Détaillées"** :
   - Mise en place d'une fonction `generate_detailed_figures()` permettant de générer en parallèle les figures plates d'origine avec toutes les 27 combinaisons détaillées (`vm_policy` + `routing` + `workload`).
   - Ces figures détaillées sont enregistrées proprement dans le sous-dossier `figures_consolidated/detailed/` afin de ne pas polluer les graphiques groupés principaux.

## Prochaine Étape
Maintenant que le pipeline génère 10 figures groupées publiables + 5 figures détaillées par dossier de campagne, et que la pipeline d'analyse est robuste, nous sommes prêts à lancer la grande campagne sur les datasets `medium` et `large` pour démontrer la supériorité globale de la politique `DynLatBw` sur la `BwAlloc`.
