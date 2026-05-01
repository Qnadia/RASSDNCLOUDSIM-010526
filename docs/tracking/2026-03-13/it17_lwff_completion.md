# Itération 17 : Analyse et Clôture du Benchmark LWFF

**Date :** 2026-03-13  
**Objectif :** Documenter la finalisation du benchmark LWFF (Least Workload First) et synthétiser les résultats globaux du dataset-small.

---

## 🏁 Clôture de l'Itération 16 (LWFF Benchmark)

Le benchmark LWFF a été complété avec succès pour les 8 combinaisons du set `dataset-small`.

### Résultats Synthétiques (Lien Redondant 10M vs 5G)
| Configuration | VM Policy | Link Policy | Avg Packet Delay | Energy (Wh) | SLA Violations |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **LWFF + First** | LWFF | First | **~129 435 ms** | **~64.3 Wh** | 991 |
| **LWFF + BwAllocN** | LWFF | BwAllocN | **~6 454 ms** | **~16.1 Wh** | 991 |

**Observation :**  
- L'utilisation de **BwAllocN** avec **LWFF** réduit le délai des paquets de **95%**.
- La consommation d'énergie est réduite de **75%** grâce à l'efficacité du routage sur les liens à large bande passante, réduisant le temps total de simulation.
- **LWFF** montre des performances de délai de paquets similaires à **LFF** sous `BwAllocN`, mais subit une dégradation plus forte que **LFF** sous la politique `First` (129s vs 98s), soulignant l'importance d'une sélection de lien intelligente pour les politiques basées sur la charge de travail.

---

## 📈 Prochaines Étapes (Itération 17)

1. [x] Documentation de la complétion du benchmark LWFF.
2. [ ] Revue des figures consolidées finales dans `results/2026-03-11/dataset-small/figures_consolidated/`.
3. [ ] Préparation du rapport final pour le benchmark comparatif global (LFF vs MFF vs LWFF).
4. [ ] Analyse de l'impact spécifique de `BwAllocN` sur les politiques de Workload (PSO vs RR vs SJF).

---

## 🛠️ État Technique Final
- **Dataset utilisé** : `dataset-small` (Topologie avec liens parallèles stratégiques).
- **Correctifs actifs** : 
    - Cache de chemin PSO actif (Stabilité).
    - Hardcode de date (levé) pour la cohérence des dossiers.
    - Correction du calcul SLA dans `SDNBroker`.
