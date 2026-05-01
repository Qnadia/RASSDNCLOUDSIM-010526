# Rapport Technique Détaillé : Expérience Medium (Edition 2026)

## 1. Introduction
Ce rapport fournit une description approfondie de la configuration expérimentale utilisée pour le dataset `medium`. Ce scénario a été conçu pour tester les limites de l'ordonnancement SDN sous une charge hétérogène et une topologie réseau complexe avec redondances asymétriques.

---

## 2. Architecture de l'Infrastructure Physique

### 2.1 Hiérarchie Réseau (Fat-Tree)
L'infrastructure physique suit une topologie hiérarchique à trois niveaux, optimisée pour la redondance et la haute disponibilité.

| Niveau | Nom des Nœuds | Capacité (BW) | Rôle |
| :--- | :--- | :---: | :--- |
| **Core** | `core0`, `core1` | 10 Gbps | Cœur de réseau haute performance. |
| **Aggregate** | `agg0` to `agg3` | 5 Gbps | Agrégation et distribution multi-chemins. |
| **Edge** | `edge0` to `edge5` | 2 Gbps | Accès pour les hôtes physiques. |

### 2.2 Ressources de Calcul (Hosts)
L'architecture comprend **12 hôtes physiques** (`h_0` à `h_11`) dont les spécifications sont uniformisées pour le benchmark :
- **CPU** : 16 Pentes (PEs) à 4000 MIPS chacune (Total: 64,000 MIPS par hôte).
- **RAM** : 32 GB.
- **Stockage** : 10 TB.
- **Lien ToR** : 1 Gbps.

### 2.3 Redondances Asymétriques (Stress Test)
Pour forcer le contrôleur SDN à effectuer des choix critiques, des liens "pièges" ont été injectés :
- **Lien lent Edge0** : 10 Mbps (en parallèle du lien nominal 2 Gbps).
- **Lien lent Edge2** : 100 Mbps (en parallèle du lien nominal 2 Gbps).
- **Lien lent Edge4** : 50 Mbps (en parallèle du lien nominal 2 Gbps).

---

## 3. Architecture des Services Virtuels (SFC & Tiers)

L'écosystème virtuel est composé de **20 machines virtuelles** organisées en une architecture multi-tiers (N-Tier) :

- **Tier Web (web0-4)** : Services de front-end (2 PEs, 2000 MIPS, 2GB RAM).
- **Tier Application (app0-4)** : Moteur de business logic (2 PEs, 2000 MIPS, 2GB RAM).
- **Tier Base de Données (db0-4)** : Couche persistante (4 PEs, 4000 MIPS, 4GB RAM).
- **Tier Service (svc0-4)** : Micro-services auxiliaires (2 PEs, 2000 MIPS, 2GB RAM).

**Connectivité :** Un maillage complexe de liens virtuels de 200-500 Mbps assure la communication entre les tiers, simulant des dépendances applicatives réelles.

---

## 4. Analyse de la Charge de Travail (Workload)

Le workload est composé de **502 requêtes unitaires** (`workload.csv`) présentant des caractéristiques variées :

### 4.1 Composition des Requêtes
Chaque requête suit un cycle de vie standardisé :
1. **Soumission** : Temps de départ distribué sur l'horizon de simulation.
2. **Traitement Source** : Exécution de cloudlets sur la VM source.
3. **Transmission** : Transfert de paquets sur le réseau SDN (taille allant de 5 MB à 200 MB).
4. **Traitement Destination** : Finalisation du calcul sur la VM cible.

### 4.2 Gestion des Priorités
Pour l'ordonnancement, trois niveaux de priorité sont définis :
- **Prio 3 (Haute)** : Workloads critiques nécessitant un accès immédiat aux ressources.
- **Prio 2 (Standard)** : Trafic applicatif normal.
- **Prio 1 (Basse)** : Tâches de fond sans contraintes strictes.

---

## 5. Synthèse des Résultats d'Exécution

| Métrique Moyenne | Scénario Statique (First) | Scénario Dynamique (BwAllocN) | Gain (%) |
| :--- | :---: | :---: | :---: |
| **Latence E2E** | ~35.0 s | ~5.1 s | **-85.4%** |
| **Consommation Énergie** | ~36.1 Wh | ~4.0 Wh (PSO) | **-88.9%** |
| **Violations SLA** | ~2200 | ~1100 | **-50.0%** |

---

## 6. Conclusion
L'expérience Medium confirme que l'architecture SDN est capable de gérer une hétérogénéité de flux importante. L'introduction de liens asymétriques valide l'efficacité de la politique **BwAllocN** qui surpasse systématiquement la politique statique en termes de qualité de service et d'efficacité énergétique.
