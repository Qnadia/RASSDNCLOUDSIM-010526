# IT-40 : Analyse Énergétique des Commutateurs et Stress du Dataset Small

**Date :** 2026-04-23  
**Statut :** 🚀 EN COURS  
**Auteur :** Antigravity (Pair Programming with User)

## 1. OBJECTIF
Démontrer l'impact du routage SDN non seulement sur la latence, mais aussi sur la consommation énergétique des commutateurs en utilisant un modèle de ports actifs.

---

## 2. ANALYSE DU MODÈLE ÉNERGÉTIQUE
L'analyse du code source (`Switch.java` et `PowerUtilizationEnergyModelSwitchActivePort.java`) a révélé le fonctionnement suivant :
- **Modèle** : `66.7W` (consommation statique/idle) + `1.0W` par port actif.
- **Lien avec le Routage** : La consommation totale ($E = P \times t$) dépend du temps pendant lequel les ports réseau restent en état "actif" (en cours de transmission).
- **Hypothèse** : Si `First` sature un lien lent, le transfert dure plus longtemps, maintenant le port actif et consommant ainsi plus d'énergie qu'un routage dynamique qui finit le travail plus vite sur un lien rapide.

---

## 3. ACTIONS RÉALISÉES : STRESS DU `DATASET-SMALL`
Le dataset original était trop "léger" pour montrer une quelconque différence. Nous avons implémenté le script `stress_small_dataset.py` pour :

### 3.1 Augmentation de la Charge
- **Facteur x200** : Les paquets passent de quelques Ko à plusieurs dizaines de Mo (25 Mo - 150 Mo).
- **Objectif** : Créer des files d'attente significatives et durables.

### 3.2 Création d'un "Bottleneck Trap" (Piège à goulot)
Pour forcer la différenciation entre `First` (statique) et `DynLatBw` (dynamique) :
- **Chemin par défaut (Edge0 <-> Agg0)** : Bridé à **100 Mbps** avec une latence élevée. C'est le chemin que `First` choisira car c'est le premier dans la topologie.
- **Chemin alternatif (Edge0 <-> Agg1)** : Boosté à **10 Gbps**. `DynLatBw` devrait détecter la congestion du lien à 100 Mbps et dévier le trafic vers ce lien 100 fois plus rapide.

---

## 4. RÉSULTATS ATTENDUS
1. **Latence** : `DynLatBw` devrait être massivement plus rapide (~100x moins de délai de transmission).
2. **Énergie** : Une réduction mesurable de la consommation des commutateurs avec `DynLatBw` car les ports repasseront en mode idle beaucoup plus tôt.
3. **SLA** : Moins de violations de timeout pour le trafic routé dynamiquement.

---
**PROCHAINE ÉTAPE :** Lancer le benchmark sur ce nouveau `dataset-small` stressé et valider les graphiques d'énergie.
