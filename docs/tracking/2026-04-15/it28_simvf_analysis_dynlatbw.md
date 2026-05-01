# Iteration 28 — Analyse Sim VF & DynamicLatencyBw : Résultats Existants (2026-04-15)

## Objectif de Recherche

> Evaluer l'apport de `LinkSelectionPolicyDynamicLatencyBw` par rapport à
> `First`, `BwAllocN`, `Dijkstra`.
> **Bonne nouvelle : les simulations existent déjà !**

---

## Ce qui a été fait aujourd'hui (2026-04-15)

### 1. Adaptation de `consolidated_report.py` pour le format Sim VF (VM_*/Link_*/)
- Ajout de `find_simvf_dirs()` + `load_experiments_simvf()`
- Fix séparateur `qos_violations.csv` (`;` dans Sim VF)
- Nouveau flag `--simvf <path>`

```powershell
python -X utf8 tools\consolidated_report.py --simvf "results\2026-05-14\Sim VF"
```

### 2. Figures générées pour `results\2026-05-14\Sim VF`
- `figures_consolidated/small/` — 9 combinaisons, fig1→fig5
- `figures_consolidated/medium/` — 9 combinaisons
- `figures_consolidated/large/` — 8 combinaisons (LWFF_Dijkstra manquant)
- `figures_consolidated/` ALL — rapport global 26 combinaisons

### 3. Découverte : DynamicLatencyBw déjà simulée dans des campagnes antérieures
(voir section ci-dessous)

---

## Inventaire Complet des Simulations DynamicLatencyBw (ordre décroissant)

### 2026-04-14 ► `results/2026-04-14-Sim V0/Sim VF/` — Sim V0 (format VM_*/Link_DynLatBw/)

| Dataset | VM | Link | Energie (Wh) | Délai pkt moy (ms) | SLA Viol |
|---------|-----|------|-------------|---------------------|----------|
| small | MFF | **DynLatBw** | 6.45 | **6 043** | 404 |
| small | LFF | **DynLatBw** | 16.05 | 6 509 | 1 016 |
| small | LWFF | **DynLatBw** | 16.05 | 6 509 | 1 016 |
| medium | MFF | **DynLatBw** | 59.08 | 7 418 | 5 638 |
| medium | LFF | **DynLatBw** | 88.30 | **7 326** | 5 572 |
| medium | LWFF | **DynLatBw** | 88.30 | **7 326** | 5 572 |

> Structure : `small/VM_MFF/Link_DynLatBw/`, `medium/VM_MFF/Link_DynLatBw/`, etc.
> CSVs complets : 15 fichiers par dossier.

### 2026-04-13 ► `results/2026-04-13/dataset-medium/` — Campagne overnight (format experiment_*)

| VM | Workload | Energie (Wh) | Paquets | SLA Viol |
|----|----------|-------------|---------|----------|
| MFF | Priority | 59.08 | 5 638 | 5 638 |
| MFF | SJF | 59.08 | 5 638 | 5 638 |
| MFF | RoundRobin | 59.08 | 5 638 | 5 638 |
| MFF | PSO | **5.63** | 5 638 | 5 638 |
| LWFF | Priority | 88.30 | 5 572 | 5 572 |
| LWFF | SJF | 88.30 | 5 572 | 5 572 |
| LWFF | RoundRobin | 88.30 | 5 572 | 5 572 |
| LWFF | PSO | **8.03** | 5 572 | 5 572 |

> Format legacy `experiment_*_DynLatBw_*/` — compatible avec `consolidated_report.py` sans flag.

### 2026-03-xx ► `batch_run_summary.csv` — Ancienne campagne (EXCEPTION)

Toutes les entrées DynLatBw dans `batch_run_summary.csv` ont le statut **EXCEPTION** :
```
experiment_LWFF_DynLatBw_Priority;LWFF;DynLatBw;Priority;EXCEPTION;0s
experiment_MFF_DynLatBw_Priority;MFF;DynLatBw;Priority;EXCEPTION;0s
...
```
> Ces runs antérieurs échouaient — probablement avant le fix StackOverflow (IT27).
> **A ignorer.**

---

## Analyse Comparative DynamicLatencyBw vs Autres Politiques

### Dataset SMALL — Résultats consolidés (toutes sources confondues)

| VM | Link | Energie (Wh) | Délai pkt (ms) | SLA Viol |
|----|------|-------------|----------------|----------|
| MFF | First | 6.45 | 6 043 | 404 |
| MFF | BwAllocN | 6.45 | 6 043 | 404 |
| MFF | Dijkstra | 6.45 | 6 043 | 404 |
| MFF | **DynLatBw** | **6.45** | **6 043** | **404** |
| LFF | First | 62.18 | 75 166 | 1 016 |
| LFF | BwAllocN | 16.05 | 6 509 | 1 016 |
| LFF | Dijkstra | 16.05 | 6 509 | 1 016 |
| LFF | **DynLatBw** | 16.05 | 6 509 | 1 016 |

### Dataset MEDIUM — Résultats consolidés

| VM | Link | Energie (Wh) | Délai pkt (ms) | SLA Viol |
|----|------|-------------|----------------|----------|
| MFF | First | 59.08 | 7 418 | 5 638 |
| MFF | BwAllocN | 59.08 | 7 418 | 5 638 |
| MFF | Dijkstra | 59.08 | 7 418 | 5 638 |
| MFF | **DynLatBw** | **59.08** | **7 418** | **5 638** |
| LFF | First | 84.28 | 7 326 | 5 572 |
| LFF | BwAllocN | 84.28 | 7 326 | 5 572 |
| LFF | Dijkstra | 84.28 | 7 326 | 5 572 |
| LFF | **DynLatBw** | 88.30 | **7 326** | 5 572 |

---

## Observation Critique : DynamicLatencyBw = Dijkstra/BwAllocN sur ces topologies ?

> [!IMPORTANT]
> Sur `dataset-small` et `dataset-medium`, `DynamicLatencyBw` donne des résultats **identiques ou
> très proches** de `Dijkstra` et `BwAllocN`.
>
> **Hypothèse** : la charge réseau reste faible dans ces simulations → le terme D_queue (M/M/1)
> est négligeable (ρ ≈ 0) → DynLatBw se comporte comme un Dijkstra standard.
>
> **Pour observer un apport réel de DynLatBw**, il faudrait :
> 1. Une charge réseau plus élevée (ex: plus de workloads simultanés, liens plus étroits)
> 2. Ou des topologies avec plusieurs chemins alternatifs de longueurs comparables

### Sur `dataset-large` — Manquant pour DynLatBw
DynLatBw n'a pas encore été testé sur Large. C'est là que la charge est maximale
et que son avantage serait le plus visible.

---

## Structure des Politiques — Comparaison Théorique

| Politique | Critère | Charge dynamique ? | Implémentation |
|-----------|---------|-------------------|----------------|
| `First` | Premier lien dispo | Non | O(1) |
| `BwAllocN` | Capacité BW déclarée | Non | O(links) |
| `Dijkstra` | Min hops | Non | O(V log V) |
| **`DynamicLatencyBw`** | **Min(D_prop+D_trans+D_proc+D_queue M/M/1)** | **Oui** | O(V! paths × hops) |

---

## Plan Prochaine Etape (IT29)

> [!IMPORTANT]
> **Action prioritaire** : Lancer DynLatBw sur `dataset-large` pour observer son apport
> réel sur une topologie fat-tree avec charge élevée.

```powershell
# Exemple via run_single_simulation.ps1 (vérifier le paramètre LinkPolicy)
powershell -ExecutionPolicy Bypass -File .\run_single_simulation.ps1 `
    -VmPolicy MFF -LinkPolicy DynLatBw -WorkloadPolicy Priority `
    -Dataset dataset-large -SaveLogs
```

Puis générer les figures comparatives :
```powershell
python -X utf8 tools\consolidated_report.py --simvf "results\2026-04-14-Sim V0\Sim VF"
```

---

## Note sur les outils de figures

| Script | Commande | Pour quoi |
|--------|---------|-----------|
| `tools/consolidated_report.py` | `--simvf <path>` | **Rapport par dataset** (fig1-5) |
| `scripts/analysis/generate_simvf_figures.py` | arg `--base` | fig1-6 + radar chart |
| `tools/consolidated_report.py` | `--results-dir <path>` | Legacy `experiment_*/` |

---

*Créé le 2026-04-15 | Dernière mise à jour : 2026-04-15*
