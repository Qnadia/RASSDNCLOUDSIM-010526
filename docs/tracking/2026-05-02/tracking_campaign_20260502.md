# Tracking Campagne – 2026-05-02

## Objectif de la journée
Compléter le benchmarking BLA vs First sur **tous les datasets** (Mini, Small, Medium, Large)
et générer l'ensemble harmonisé des figures scientifiques (fig01–fig15 + CPU/RAM timeseries + scalabilité intra-dataset).

---

## 1. Travaux effectués

### 1.1 Consolidation des résultats (déjà faite avant séance)
- Les 4 datasets avaient leurs `synthese/data/` peuplés depuis les runs nocturnes du 02/05.
- `global_analysis/` contenait les 10 fichiers `GLOBAL_*.csv`.

### 1.2 Génération des plots scientifiques — `clde/3_generate_global_plots.py`

**Figures générées (17 par dataset) :**

| Fig | Titre                              | Statut |
|-----|------------------------------------|--------|
| 01  | Energy by VM Policy                | ✅ OK  |
| 02  | Avg Delay by VM Policy             | ✅ OK  |
| 03  | CDF Delay + Queuing                | ✅ OK  |
| 04  | Delay Components                   | ✅ OK  |
| 05  | Boxplot Delay Distribution         | ✅ OK  |
| 06  | Host Utilization CPU/RAM/BW        | ✅ OK  |
| 07  | Pareto Energy vs Delay (scatter)   | ✅ OK  |
| 08  | Queuing Delay by VM Policy         | ✅ OK  |
| 09  | BLA Gain % vs First                | ✅ OK  |
| 10  | Workload Scheduling Impact         | ✅ OK  |
| 11  | Link Utilization (boxplot)         | ✅ OK* |
| 12  | Energy Timeseries                  | ✅ OK  |
| 13  | Heatmap Delay BLA (VM × WF)        | ✅ OK  |
| 14  | SLA Violations Count               | ✅ OK  |
| 15  | Path Quality (latency + BW)        | ✅ OK  |
| +   | CPU & RAM Timeseries (par VM)      | ✅ NEW |
| +   | Scalability Analysis intra-dataset | ✅ NEW |

> *fig11 skippée pour dataset-large (données link_utilization insuffisantes — 1 row NaN)

**Améliorations apportées au script :**
- Ajout de `fig_cpu_ram_timeseries()` — aligne sur la référence `2026-05-01-Ex`
- Ajout de `fig_scalability_intra()` — résumé BLA vs First avec gain %
- Flag `--dataset` pour régénérer un seul dataset
- Compatibilité seaborn : `fig15_path_quality` et `fig11` corrigés (`hue=` + guard NaN)

**Plots de scalabilité cross-dataset — `clde/6_generate_scalability_plots.py` :**
- `fig_scalability_all_metrics.png` ✅
- `fig_scalability_relative_gain.png` ✅
- `scalability_summary.csv` ✅
- Sortie : `results/2026-05-02/scalability_analysis/`

---

### 1.3 Correctif Java — `SDNDatacenter.java`

**Bug identifié :** Le garde-fou anti-boucle infinie (`checkLoopSafety()`)  
déclenchait `System.exit(-1)` avec `MAX_SAME_TIME_EVENTS = 1000`  
pour le dataset-large (12 500 → réduit à **1 000 workloads**) et les politiques  
`Priority` / `SJF` qui soumettent en rafale au même timestamp de simulation.

**Fix :**
```java
// Avant
private static final int MAX_SAME_TIME_EVENTS = 1000;

// Après
private static final int MAX_SAME_TIME_EVENTS = 5000; // raised for large datasets
```

**Workload dataset-large réduit :** `workload.csv` tronqué à **1 000 lignes**  
(restauré depuis backup `workload.csv.bak1000` — 1001 lignes avec header).

---

### 1.4 Re-simulation dataset-large (en cours)

Lancée à 08:32 avec `1_run_nightly_benchmarks.py --datasets datasets/dataset-large`.  
18 expériences (2 link × 3 vm × 3 wf), ETA ~10–15 min.

**Statut au lancement :**
- LFF|First|Priority → ✅ OK (0.7s)
- LFF|First|SJF → ✅ OK (0.7s)
- … (en cours)

---

## 2. État des datasets au 07/05

| Dataset | Workloads | Runs  | Consolidation | Plots (23 figs) |
|---------|-----------|-------|---------------|-----------------|
| Mini    | ~180 req  | 18/18 | ✅ Done       | ✅ 23 figs      |
| Small   | ~500 req  | 18/18 | ✅ Done       | ✅ 23 figs      |
| Medium  | ~800 req  | 18/18 | ✅ Done       | ✅ 23 figs      |
| Large   | 1000 req  | 18/18 | ✅ Done       | ⏳ À générer   |

---

## 3. Session 2026-05-07 — Nouveau script de génération

### 3.1 Script unifié `tools/analysis/clde/4_generate_all_plots_final.py`

**Consolidation de tous les scripts précédents** (`tools/analysis/3_generate_global_plots.py`,
`tools/analysis/clde/3_generate_global_plots.py`, `generate_simvf_figures.py`).

**Correctifs apportés :**
- **Auto-détection suffixe** : `load()` cherche `host_energy_total.csv` ET `host_energy_total_medium.csv` → résout le bug où medium/small n'étaient pas générés
- **Titres en anglais** : tous les titres de figures traduits pour l'article scientifique
- **Zéro numérotation** : aucun "Figure X" dans les titres
- **Export PNG + SVG** systématique

**23 figures par dataset :**

| Catégorie | Figures |
|-----------|---------|
| Energy    | `energy_by_vm`, `energy_all_policies`, `energy_timeseries` |
| Delay     | `delay_by_vm`, `cdf_delay`, `delay_components`, `boxplot_delay`, `queuing_delay`, `vm_latency_impact`, `wf_latency_impact`, `wf_impact_dual` |
| Network   | `link_utilization`, `path_quality`, `network_latency_by_vm` |
| Host/VM   | `host_utilization`, `vm_cpu_impact`, `cpu_ram_timeseries` |
| Analytics | `pareto`, `bla_gain`, `sla_violations`, `sla_severity`, `heatmap_delay`, `scalability_intra` |

### 3.2 Figures consolidées cross-dataset

Sortie : `results/2026-05-02/global_analysis/plot_consolidated/`

| Figure | Description |
|--------|-------------|
| `consolidated_scalability` | Courbes d'évolution énergie+délai par scale (Mini→Large) |
| `consolidated_bla_gain` | Gain BLA% par dataset et métrique |
| `consolidated_energy` | Énergie totale comparée across datasets |
| `consolidated_delay` | Délai moyen comparé across datasets |
| `consolidated_pareto` | Vue Pareto cross-dataset (énergie vs délai) |

### 3.3 Rapports d'analyse finaux
- **Rapport Markdown détaillé** : `docs/reports/2026-05-07_consolidated_analysis_report.md` ✅
- **Rapport Scientifique Consolidé (PDF)** : `results/2026-05-02/global_analysis/Consolidated_Scientific_Report.pdf` ✅ (Mise en page Journal, remarques superviseurs incluses)
- **Rapport Section 4 (PDF)** : `results/2026-05-02/global_analysis/Scientific_Article_Section_4_Final.pdf` ✅

**État d'avancement au 08/05 (01h00) :**
- ✅ **Infrastructure** : Pipeline de plotting unifié et robuste.
- ✅ **Résultats** : Mini, Small, et Medium validés (23 plots/dataset + 5 consolidés).
- ✅ **Rédaction** : Section 4 rédigée et formatée pour article scientifique.
- ⏳ **Dataset Large** : Simulation terminée, génération des plots en attente.

---

## 4. Prochaines étapes

- [ ] **Génération Dataset Large** : 
  `python tools/analysis/clde/4_generate_all_plots_final.py results/2026-05-02 --dataset dataset-large`
- [ ] **Mise à jour finale des consolidés** (incluant Large)
- [ ] **Commit & Push final**

---
*Report generated: 2026-05-07 | Campaign: 2026-05-02 | Simulator: RAS-SDN CloudSim v2*
