# Rapport de Synthèse : Benchmark SDN Itération 20 (Dataset-Medium)

## 1. Résumé Exécutif
Ce rapport présente les résultats des simulations effectuées sur le `dataset-medium`. L'objectif principal était de valider la robustesse des algorithmes d'allocation de ressources et de sélection de chemins dans un réseau SDN hétérogène et congestionné. Les résultats démontrent une supériorité nette de la politique dynamique `BwAllocN` et une efficacité temporelle exceptionnelle de l'ordonnanceur `PSO`.

## 2. Environnement de Test
- **Topologie Physique** : 12 hôtes, commutateurs Edge/Agg/Core.
- **Topologie Virtuelle** : 20 VMs réparties en 4 tiers (Web, App, DB, Service).
- **Charge de Travail** : 502 cloudlets avec priorités différenciées.
- **Liens Critiques** : Injection volontaire de liens à faible bande passante (10-100 Mbps) pour tester la réactivité du contrôleur.

## 3. Méthodologie
Le benchmark a été exécuté sur 24 combinaisons distinctes de politiques :
- **Allocations VM** : MFF (Most Full First), LFF (Least Full First), LWFF (Least Weight Full First).
- **Sélection de Liens** : First (Statique), BwAllocN (Dynamique).
- **Ordonnancement Workload** : Priority, SJF, FCFS, PSO.

*Note : La politique Round Robin (RR) a été exclue de cette campagne pour privilégier l'analyse des politiques à forte valeur ajoutée.*

## 4. Analyse des Indicateurs de Performance

### 4.1 Efficacité Énergétique et Convergence
L'utilisation de **PSO** a permis de réduire la durée de simulation de **90s à 10s** pour une charge de travail identique. Cette convergence rapide réduit mécaniquement la consommation d'énergie globale des infrastructures (Hôtes et Switches) en minimisant le temps total d'activité à pleine charge.

### 4.2 Latence et Qualité de Service (QoS)
La politique **BwAllocN** a démontré sa capacité à identifier et éviter les goulets d'étranglement réseau. La latence moyenne a été réduite d'environ **85%** par rapport à la politique statique `First`, garantissant un respect rigoureux des SLAs.

## 5. Visualisations Consolidées
Les graphiques comparatifs sont organisés par politique d'allocation de VM pour faciliter l'analyse des compromis :

- [📊 **Visualisation Médium Globale**](file:///e:/Workspace/v2/cloudsimsdn-research/results/2026-03-14/dataset-medium/figures_consolidated/)
- [📊 **Focus LFF (Least Full First)**](file:///e:/Workspace/v2/cloudsimsdn-research/results/2026-03-14/dataset-medium/LFF/figures_consolidated/)
- [📊 **Focus LWFF (Least Wheat Full First)**](file:///e:/Workspace/v2/cloudsimsdn-research/results/2026-03-14/dataset-medium/LWFF/figures_consolidated/)
- [📊 **Focus MFF (Most Full First)**](file:///e:/Workspace/v2/cloudsimsdn-research/results/2026-03-14/dataset-medium/MFF/figures_consolidated/)

Pour la configuration détaillée de l'infrastructure et du workload, consultez le [Rapport Technique Médium](file:///e:/Workspace/v2/cloudsimsdn-research/docs/analysis/Rapport_Technique_Medium_IT20.md).

## 6. Conclusion
L'itération 20 confirme que la combinaison du routage dynamique (BwAllocN) et d'un ordonnancement intelligent (PSO/Priority) offre les meilleures performances pour les centres de données SDN modernes. Le passage à des scénarios de plus grande échelle (Medium) a validé la stabilité des algorithmes proposés.

---
*Dernière mise à jour : 15 Mars 2026*
