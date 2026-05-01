# Analyse des Résultats de Simulation CloudSimSDN

## 1. Résumé Exécutif

> [!CAUTION]
> **La topologie actuelle est trop petite et trop homogène** pour montrer l'avantage d'une politique de sélection de liens basée sur la bande passante et la latence. Les résultats de `First` et `BwAllocN` sont **identiques** dans presque toutes les configurations.

---

## 2. Topologie Physique Actuelle

```
         ┌──────────┐
         │  Core (c) │
         └──┬─┬─┬─┬──┘
   1Gbps   │ │ │ │  500M
  ┌────────┘ │ │ └────────┐
  │   500M   │ │   500M   │
┌─┴──┐  ┌───┴─┴──┐  ┌───┴──┐  ┌────┐
│ e0 │──│ e1     │──│ e2   │──│ e3 │
└─┬──┘  └───┬────┘  └───┬──┘  └─┬──┘
  │1G       │1G         │1G     │1G
┌─┴──┐  ┌───┴──┐    ┌───┴──┐  ┌─┴──┐
│h_0 │  │ h_1  │    │ h_2  │  │h_3 │
└────┘  └──────┘    └──────┘  └────┘
```

**Problèmes identifiés :**

| Aspect | Valeur actuelle | Impact |
|--------|----------------|--------|
| Hôtes | **4** | Trop peu pour montrer une différence |
| Links edge→host | **Tous 1 Gbps, même latence 0.1ms** | Aucun choix à faire pour la politique de lien |
| VMs | **4** (web0, app0, db0, web1) | Trop peu pour saturer les liens |
| Chemins alternatifs | **Limités** | BwAllocN n'a pas de choix significatif |
| Workload | **50 requêtes** seulement | Pas assez de charge pour créer de la congestion |

---

## 3. Résultats Comparatifs

### Énergie Totale (Wh)

| VM Alloc | Link Policy | Workload | Énergie (Wh) | VMs/hôte |
|----------|-------------|----------|-------------|----------|
| Binpack | First | Priority | **2.75** | 4 VMs sur h_3 |
| Binpack | BwAllocN | Priority | **2.75** | 4 VMs sur h_3 |
| Binpack | First | FCFS | **2.75** | 4 VMs sur h_3 |
| Binpack | BwAllocN | FCFS | **2.75** | 4 VMs sur h_3 |
| MFF | BwAllocN | Priority | **2.75** | 4 VMs sur h_3 |
| LFF | BwAllocN | Priority | **8.42** | 1 VM/hôte |
| Binpack | First | PSO | **1.91** | — |
| Binpack | BwAllocN | PSO | **1.91** | — |

> [!WARNING]
> **First vs BwAllocN → résultats identiques** partout. La politique de lien n'a aucun impact observable.

### Packet Delays (ms) — Premiers paquets

| Expérience | Paquet 0 | Paquet 1 | Paquet 2 | Paquet 3 |
|------------|----------|----------|----------|----------|
| Binpack_First_Priority | 2100 | 2650 | 3300 | 3850 |
| Binpack_BwAllocN_Priority | 2100 | 2650 | 3300 | 3850 |

**Même résultat exactement.** La raison : le chemin entre les VMs est unique (1 seul lien edge→host), donc `BwAllocN` choisit le même lien que `First`.

---

## 4. Pourquoi les Résultats Sont Identiques

### Cause Racine : Topologie Sans Chemins Alternatifs
- Chaque hôte est connecté à **1 seul switch edge** par **1 seul lien**
- Quand les 4 VMs sont sur le même hôte (BinPack/MFF), **tout le trafic est local** → pas de réseau
- Quand les VMs sont sur des hôtes différents (LFF), il y a **1 seul chemin** entre chaque paire → pas de choix de lien

### Pour que BwAllocN/DynLatBw montre un avantage, il faut :
1. **Plusieurs chemins** entre les même paires source-destination
2. **Des liens hétérogènes** (bandwidths et latences différentes)
3. **De la congestion** (charge suffisante pour saturer certains liens)

---

## 5. Recommandations pour la Contribution Scientifique

### A. Topologie Agrandie (Fat-Tree ou Leaf-Spine)

```
              ┌───────┐  ┌───────┐
              │Core_0 │  │Core_1 │
              └─┬─┬─┬─┘  └─┬─┬─┬─┘
        ┌──────┘ │ └──┐ ┌──┘ │ └──────┐
      ┌─┴──┐ ┌──┴──┐ └─┘ ┌──┴──┐ ┌───┴─┐
      │Agg0│ │Agg1 │     │Agg2 │ │Agg3 │
      └┬─┬─┘ └┬──┬─┘     └┬──┬─┘ └┬──┬──┘
   ┌───┘ └─┐┌─┘  └──┐ ┌───┘  └─┐┌──┘ └──┐
 ┌─┴─┐ ┌──┴┴┐  ┌───┴┴─┐  ┌───┴┴─┐  ┌──┴─┐
 │e0 │ │e1  │  │e2    │  │e3    │  │e4  │
 └┬┬─┘ └┬┬──┘  └┬┬────┘  └┬┬────┘  └┬┬──┘
  ││     ││      ││        ││        ││
 h0 h1  h2 h3  h4 h5     h6 h7    h8 h9
```

**Recommandation minimale :**
- **10-20 hôtes** (au lieu de 4)
- **2 niveaux de switch** (edge + aggregation) + core → **chemins multiples**
- **Liens hétérogènes** : varier les BW de 100M à 10G et les latences de 0.05ms à 2ms
- **20-50 VMs** avec des modèles de communication variés

### B. Workload à Augmenter

| Paramètre | Actuel | Recommandé |
|-----------|--------|------------|
| Nombre de requêtes | 50 | **500-2000** |
| Durée simulation | ~25s | **120-600s** |
| Taille paquets | 5M-100M | Varier de 1K à 500M |
| Nombre de VMs | 4 | **20-50** |
| Flux simultanés | ~4 | **50-200** (pour créer de la congestion) |

### C. Métriques à Collecter

Pour une publication, les métriques les plus impactantes :

1. **Latence moyenne end-to-end** (la plus importante pour DynLatBw)
2. **Latence au 95ème percentile** (pour montrer les queues de distribution)
3. **Violation SLA rate** (% de paquets dépassant le seuil)
4. **Utilisation des liens** (distribution du trafic : uniforme vs déséquilibrée)
5. **Throughput total** du réseau
6. **Énergie totale** (consolidation impact)

### D. Scénarios de Test Recommandés

| Scénario | Objectif | Attendu |
|----------|----------|---------|
| Charge faible (20%) | Baseline | Pas de différence entre les politiques |
| Charge moyenne (50%) | Début de congestion | BwAllocN commence à surpasser First |
| Charge élevée (80%) | Congestion forte | **DynLatBw >> BwAllocN >> First** |
| Burst traffic | Pics de trafic | DynLatBw s'adapte, First sature un lien |
| Liens hétérogènes | BW/latence variées | DynLatBw optimise le choix |

---

## 6. Est-ce que les Métriques Sont Correctes ?

> [!WARNING]
> **Plusieurs métriques semblent suspectes :**

1. **`path_latency.csv`** : Seulement 80 bytes → quasi-vide, pas exploitable
2. **`sfc_throughput.csv`** : 43 bytes → vide (SFC supprimé)
3. **Énergie identique** entre First et BwAllocN → normal vu la topologie
4. **`host_vm_allocation.csv`** montre que BinPack met les 4 VMs sur h_3 → **correct** (BinPack concentre) mais ça élimine tout trafic réseau inter-hôte

> [!TIP]
> Le fait que les VMs soient toutes sur le même hôte avec BinPack annule complètement l'impact du réseau. Pour montrer l'impact de la politique de lien, **utilisez LFF ou Spread** qui distribuent les VMs sur plusieurs hôtes.
