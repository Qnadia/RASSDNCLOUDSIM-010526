# 🤖 Perspective 1 : Placement de VMs par Deep Reinforcement Learning (DRL)

## 1. Problématique et Approche DRL
Les politiques classiques comme `LFF` ou `MFF` sont des heuristiques gloutonnes (greedy). Le **DRL** permet de capturer des relations non-linéaires complexes entre la topologie Fat-Tree et les patterns de trafic fluctuants. On utilise un agent (ex: PPO - Proximal Policy Optimization) pour décider du placement optimal.

---

## 2. Cadre d'Apprentissage (Mapping CloudSimSDN)

### A. Vecteur d'État (State Space $S$)
Le `SDNDatacenter` fournit à l'agent un vecteur normalisé :
- **Nodes CPU Utilization** : $[u_{host0}, u_{host1}, ..., u_{hostn}]$
- **Link Bandwidth Availability** : $[\%bw_{link0}, \%bw_{link1}, ...]$
- **Current Energy Consumption** : Valeur cumulative.

### B. Espace des Actions (Action Space $A$)
L'action renvoyée par l'IA est l'index de l'Hôte Physique cible pour la VM courante :
$$A = \{0, 1, 2, ..., N_{hosts}-1\}$$

### C. Fonction de Récompense (Reward $R$)
La récompense est calculée à la fin de chaque itération ou après chaque placement :
$$R = - ( \text{Energy\_Consumed} + \lambda \cdot \text{SLA\_Violations} )$$

---

## 3. Architecture d'Intégration technique

### Le Bridge CloudSim-Python
La simulation tourne en Java, tandis que les modèles IA tournent souvent en Python.
- **`AIAllocationPolicy.java`** : Implémente une communication Socket ou via un fichier temporaire JSON pour passer le vecteur d'état.
- **`Agent.py`** : Script externe contenant le modèle PyTorch/TensorFlow.

### Logique d'Exécution (Java)
```java
// Dans AIAllocationPolicy.java
public Host allocateHostForVm(Vm vm) {
    double[] state = datacenter.getCurrentStateVector();
    int hostId = aiBridge.requestAction(state); // Appel bloquant vers Python
    return datacenter.getHostList().get(hostId);
}
```

---

## 4. Métriques et Évaluation
1. **Training Convergence Rate** : Nombre d'épisodes de simulation nécessaires pour atteindre une récompense stable.
2. **Energy Efficiency Gain** : Amélioration en % par rapport à la politique PSO (Most Full First).
3. **Generalization Performance** : Test de l'agent sur un dataset (Large) après entraînement sur un dataset (Small).
