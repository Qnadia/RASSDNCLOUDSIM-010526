# Analyse des Résultats de Simulation (Dataset: mini)

## Date: 2026-03-09
## Scénarios comparés:
1. **BwAllocN** : Allocation dynamique de bande passante.
2. **First** : Sélection du premier lien disponible (Latency-Aware).
*Configuration commune : VM Allocation = LFF, Workflow = Priority.*

---

## 1. Performance (Latence)
Les deux politiques affichent des résultats **identiques** sur ce dataset réduit.

| Métrique | BwAllocN | First |
| :--- | :--- | :--- |
| **Latence Moyenne (ms)** | 1076.92 ms | 1076.92 ms |
| **Latence Réseau Max (ms)** | 800.2 ms | 800.2 ms |
| **Délai de traitement Max (ms)** | 2339.8 ms | 2339.8 ms |

> [!NOTE]
> Sur le dataset `mini`, la topologie est simple et la charge est faible. Il n'y a pas de congestion de liens, donc la politique de sélection de liens n'a pas encore d'impact différentiel visible sur la latence.

---

## 2. Consommation Énergétique
La consommation des hôtes est également identique, ce qui est attendu puisque le placement des VMs (LFF) est le même.

| Composant | Consommation (Wh) |
| :--- | :--- |
| **Host h_0** | 0.1843 Wh |
| **Host h_1** | 0.1840 Wh |
| **Total** | **0.3683 Wh** |

---

## 3. Qualité de Service (QoS)
- **Violations SLA** : 10 violations enregistrées pour les deux scénarios.
- Ces violations sont principalement dues à la nature de la politique `Priority` et aux délais de traitement CPU élevés sur certaines requêtes spécifiques du workload (ex: Req 21 avec 2339ms de processing).

---

## Conclusion et Prochaines Étapes
Le fix de la boucle infinie est **confirmé stable** pour les deux politiques. Étant donné que le dataset `mini` ne permet pas de différencier les politiques de liens, il est recommandé de passer au dataset `small` ou `medium` pour observer des deltas significatifs dans l'article.

### Recommandation :
Lancer la campagne scientifique complète avec le dataset `small` :
`powershell -File .\run_scientific_campaign.ps1` (limitée aux datasets de petite taille pour l'instant).
