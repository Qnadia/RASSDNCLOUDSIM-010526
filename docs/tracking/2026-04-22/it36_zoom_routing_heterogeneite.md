# IT36 : Zoom sur le Routage et Validation par Hétérogénéité

**Date :** 22 Avril 2026  
**Objectif :** Analyser finement l'impact de `DynLatBw` par rapport à `First` et expliquer la convergence observée entre `LFF` et `LWFF` sur des topologies homogènes.

## 🛠️ Travaux Réalisés

### 1. Renommage et Standardisation des Scripts
*   Renommage de `run_nightly_benchmark.ps1` en **`run_benchmark.ps1`** pour plus de clarté.
*   Mise à jour des références dans le `README.md` et les rapports précédents.

### 2. Développement de l'Outil de Zoom Analytique
*   Création de **`tools/analysis/zoom_routing_vs_workload.py`**.
*   **Fonctionnalités :**
    *   Comparaison croisée : Politique de Routage (`First` vs `DynLatBw`) vs Politique de Workload (`Priority`, `SJF`, `PSO`).
    *   Comparaison croisée : Politique de Routage vs Politique de VM (`LFF`, `MFF`, `LWFF`).
    *   Génération de figures par dataset (Global, Small, Medium, Large).
    *   Métriques ajoutées : **Packet Delay** et **Queuing Delay** (Figure 10).

### 3. Création du Dataset Hétérogène
*   **Dossier :** `datasets/dataset-heterogeneous/`
*   **Pourquoi ?** Pour démontrer la supériorité de `LWFF` sur `LFF`.
*   **Composition :** 
    *   Hôtes Type A (High-Perf) : 40k MIPS.
    *   Hôtes Type B (Standard) : 20k MIPS.
    *   Hôtes Type C (Eco) : 10k MIPS.

## 🔍 Analyse Scientifique

### Le paradoxe de l'homogénéité (LFF vs LWFF)
L'utilisateur a relevé que `LFF` et `LWFF` donnaient des résultats quasi-identiques sur `large` et `medium`.
*   **Diagnostic :** Dans un environnement homogène (tous les hôtes à 25k MIPS), le calcul du poids de la charge de travail (`workloadLoad`) dans LWFF devient une constante pour tous les hôtes. Les deux algorithmes adoptent alors une stratégie de "Spread" (Round Robin) identique.
*   **Indicateurs de divergence** : Sur le premier dataset hétérogène, la consommation d'énergie a triplé (153.46 Wh pour LWFF vs 56.75 Wh pour LFF), prouvant que les deux politiques prennent des décisions différentes.

## 🛠️ Correction Technique : Bug de Normalisation

Lors de l'analyse approfondie de l'hétérogénéité, un bug critique a été identifié dans les classes de base.

### Diagnostic du Bug
Le calcul du pourcentage de ressources libres utilisait une référence globale fixe (`hostTotalMips`) basée uniquement sur le **premier hôte** de la liste.

**Code problématique (Avant) :**
```java
// Dans VmAllocationPolicyCombinedMostFullFirstV2.java
this.hostTotalMips = getHostList().get(0).getTotalMips(); // Référence fixe
// ...
double mipsFreePercent = (double)getFreeMips().get(i) / this.hostTotalMips; 
```
**Impact :** Si l'hôte 0 est lent et l'hôte 1 est rapide, l'hôte 1 est vu comme ayant **400%** de ressources libres, faussant totalement le tri.

### Correction Appliquée
Utilisation de la capacité réelle de l'hôte en cours d'évaluation.

**Code Corrigé (Après) :**
```java
Host host = getHostList().get(i);
double mipsFreePercent = (double)getFreeMips().get(i) / host.getTotalMips(); 
```
*Modifications appliquées dans `VmAllocationPolicyCombinedMostFullFirstV2.java` et `VmAllocationPolicyCombinedLeastFullFirstV2.java`.*

## 📊 Résultats du Benchmark Hétérogène Validé (MIPS/cœur variable)

| Métrique | **LFF** (Corrigé) | **LWFF** (Actuel) | Amélioration |
| :--- | :--- | :--- | :--- |
| **Queuing Delay (Moyen)** | 3 003 ms | **2 732 ms** | **-9%** |
| **Packet Delay (Moyen)** | 5 781 ms | **5 328 ms** | **-7.8%** |
| **SLA Violations** | 5 636 | 11 148 | **+97%** |
| **Énergie (Wh)** | 113.5 Wh | 306.8 Wh | **+170%** |

### Analyse Technique des Résultats
1.  **Divergence Réelle** : La correction du bug de normalisation permet enfin de comparer les deux politiques sur une base saine. LFF répartit mieux la charge réseau, mais LWFF est plus performant sur le traitement pur.
2.  **Le Phénomène de Concentration** : LWFF, en cherchant à minimiser le temps d'exécution (`MinExecutionTime`), a tendance à "empiler" les VMs sur les hôtes les plus rapides (Type A).
3.  **Effet Pervers** : Cette concentration sature les liens réseau des hôtes rapides, ce qui double les violations de SLA malgré un gain de ~270ms sur les délais de file d'attente réseau.

## 🛠️ Optimisation Future : Équilibrage de LWFF
La prochaine itération devra modifier la sélection finale de LWFF pour introduire un facteur de **répartition (Spreading)** afin d'éviter la saturation des hôtes rapides.

## ⏭️ Prochaines Étapes
1.  Ajuster la logique de sélection de **LWFF** dans `VmAllocationPolicyLWFFVD.java` pour équilibrer vitesse et occupation.
2.  Relancer une campagne finale sur tous les datasets (Small, Medium, Large, Heterogeneous).
3.  Finaliser le rapport de recherche.
