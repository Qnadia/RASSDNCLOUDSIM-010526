# 📋 Tracking d'Améliorations — CloudSimSDN Research Simulator
*Date : 29 Avril 2026 | Auteur : Analyse Expert Antigravity | Statut : En attente de validation*

---

## 🎯 Objectif du Projet (Rappel de Contexte)

**But scientifique** : Valider empiriquement que la politique de routage SDN
`LinkSelectionPolicyDynamicLatencyBw` (**DynLatBw / BLA**) — basée sur un modèle Dijkstra
dynamique avec coût M/M/1 — surpasse les politiques classiques (`First`, `BwAllocN`, `Dijkstra`)
en termes de latence bout-en-bout, consommation énergétique, et respect des SLA, dans un
datacenter cloud SDN simulé sous CloudSimSDN (CloudSim 4.0).

**Contribution scientifique principale** :
> *Coût_lien(DynLatBw) = D_proc_switch + D_prop + D_trans + D_queue(M/M/1)*
> où *D_queue = ρ / (μ × (1 − ρ))* pénalise automatiquement les liens saturés.

---

## 1. 🔬 Analyse de l'Architecture et du Workflow

### 1.1 Workflow Global (Correct et Fonctionnel ✅)

```
[JSON Topologie Physique + Virtuelle + CSV Workload]
            ↓
[Java - SimpleExampleSelectLinkBandwidth.main()]
            ↓
[CloudSim Engine] ←→ [NOS] ←→ [DynLatBw/First/BwAllocN...]
            ↓
[LogManager.flushAll() → CSV outputs]
            ↓
[Python - 2_consolidate_results_v2.py]
            ↓
[Python - 3_generate_global_plots.py]
            ↓
[Figures PNG/PDF prêtes pour publication]
```

### 1.2 Politiques Comparées (Matrice Expérimentale)

| Axe | Politiques |
|---|---|
| **Placement VM** | LFF, MFF, CombLFF, FCFS, RR, Spread, Binpack, LWFF, LWFFVD, MipLFF, MipMFF |
| **Routage SDN (Lien)** | **DynLatBw** *(contribution)*, First, BwAllocN, Dijkstra, Random |
| **Ordonnancement Workload** | Priority, SJF, HybridSJF, PSO, RoundRobin, NoOp |
| **Datasets** | Small, Medium, Large (topologies à différentes échelles) |

### 1.3 KPIs Collectés par Simulation

| Fichier CSV | Métriques | Discriminant pour routage ? |
|---|---|---|
| `packet_delays.csv` | delay_ms, proc, prop, trans, queue | ✅ OUI — Métrique principale |
| `host_energy_total.csv` | énergie (Wh) par host | ✅ OUI — Corrélée à durée sim |
| `link_utilization_up.csv` | utilisation % par lien SDN | ✅ OUI — Seule BW réseau réelle |
| `host_utilization.csv` | cpu%, ram%, bw% par hôte | ⚠️ PARTIEL — Intégrale oui, snapshot non |
| `qos_violations.csv` | count violations SLA | ❌ NON (identique à ρ > 0.8) |
| `path_latency_final.csv` | latence de chemin | ✅ OUI — Debug routing |

---

## 2. 🐛 Bugs et Problèmes Identifiés

### 🔴 Critique (bloque la validité scientifique)

#### BUG-01 — Code mort dans `DynLatBw` (180 lignes commentées)
- **Fichier** : `src/.../selectlink/LinkSelectionPolicyDynamicLatencyBw.java`, lignes 328–503
- **Problème** : L'ancienne implémentation DFS (Depth-First Search exhaustif) est laissée commentée
  dans le même fichier. Elle est fonctionnellement abandonnée mais crée une ambiguïté de lecture.
  Un relecteur scientifique pourrait confondre l'algorithme actif (Dijkstra O((V+E)log V)) avec l'ancien DFS.
- **Impact** : Confusion sur la complexité algorithmique réelle présentée dans l'article.
- **Statut** : `[ ] À corriger`
- **Action** : Supprimer ou déplacer dans `backup_dfs_routing/` (dossier qui existe déjà ✅)

#### BUG-02 — `Configuration.java` : 160 lignes de code commenté (legacy)
- **Fichier** : `src/.../sdn/Configuration.java`, lignes 87–250
- **Problème** : Plusieurs versions successives de la configuration sont empilées en commentaires.
  Le paramètre `DECIDE_SLA_VIOLATION_GRACE_ERROR = 1.30` est actif mais son choix n'est pas
  documenté dans le code (pourquoi 30% de grâce SLA ?).
- **Impact** : Difficulté à reproduire les expériences; le lecteur ne sait pas quelle config est active.
- **Statut** : `[ ] À corriger`
- **Action** : Purger les blocs commentés; ajouter Javadoc sur chaque constante critique.

#### BUG-03 — `generate_simvf_figures.py` : `DynLatBw` absent de la palette de figures IEEE
- **Fichier** : `tools/analysis/generate_simvf_figures.py`, ligne 78
- **Problème** : `LINK_ORDER = ["Link_First", "Link_BwAllocN", "Link_Dijkstra"]` — **DynLatBw n'est pas
  inclus**. Le script de figures IEEE ne génère aucune figure pour la contribution principale.
- **Impact** : Toutes les figures `fig1` à `fig6` sont invalides pour l'article (la politique clé est absente).
- **Statut** : `[ ] À corriger URGENT`
- **Action** : Ajouter `"Link_DynLatBw"` dans `LINK_ORDER`, `VM_COLORS`, `LINK_HATCHES`.

#### BUG-04 — `3_generate_global_plots.py` : palette hardcodée `First` vs `BLA` seulement
- **Fichier** : `tools/analysis/3_generate_global_plots.py`, ligne 23
- **Problème** : `palette = {"First": "#1f77b4", "BLA": "#ff7f0e"}` — Dijkstra et BwAllocN sont ignorés.
- **Impact** : Figures partielles; l'article ne peut pas présenter une comparaison 4 politiques.
- **Statut** : `[ ] À corriger`
- **Action** : Étendre la palette :
  ```python
  palette = {
      "First":      "#1f77b4",
      "BLA":        "#ff7f0e",
      "Dijkstra":   "#2ca02c",
      "BwAllocN":   "#d62728"
  }
  ```

#### BUG-05 — Static state resets dans `main()` — fragilité architecturale
- **Fichier** : `SimpleExampleSelectLinkBandwidth.java`, lignes 108–112
- **Problème** : Le simulateur repose sur des `reset()` manuels de champs statiques pour permettre
  plusieurs runs dans la même JVM. C'est un pattern anti-fragile (oubli d'un reset → données
  croisées entre simulations).
- **Impact** : Risque silencieux de données corrompues dans la campagne benchmark (81 runs).
- **Statut** : `[ ] À surveiller`
- **Action** : Audit de tous les champs statiques dans `SDNBroker`, `SDNDatacenter`, `SDNVm`, `QoSMonitor`.
  Envisager de spawner chaque run dans un process JVM séparé (via PowerShell) plutôt que de réutiliser la même JVM.

---

### 🟠 Majeur (impacte la qualité scientifique)

#### IMP-01 — Aucun test de reproductibilité (seed aléatoire non fixé)
- **Problème** : `LinkSelectionPolicyRandom` et `PSOWorkloadScheduler(20, 100, 0.5, 1.5, 1.5)` utilisent
  des valeurs aléatoires non seedées → résultats non reproductibles.
- **Action** :
  ```java
  // Dans Configuration.java
  public static final long RANDOM_SEED = 42L;
  // Dans chaque politique stochastique :
  private final Random rng = new Random(Configuration.RANDOM_SEED);
  ```

#### IMP-02 — `diag_cpu_timeseries.py` : chemins de résultats hardcodés
- **Fichier** : `tools/analysis/diag_cpu_timeseries.py`, lignes 13–14
- **Problème** : `base = f'results/2026-04-27/{ds}/raw/LFF'` — date et politique VM hardcodées.
  Le script est inutilisable sur d'autres runs sans modification manuelle.
- **Action** : Ajouter `argparse` :
  ```python
  parser.add_argument("--results-dir", default="results/2026-04-27")
  parser.add_argument("--vm-policy", default="LFF")
  ```

#### IMP-03 — `generate_simvf_figures.py` : chemin absolu Windows hardcodé
- **Fichier** : `tools/analysis/generate_simvf_figures.py`, ligne 41
- **Problème** : `DEFAULT_BASE = r"E:\Workspace\v2\cloudsimsdn-research\results\2026-05-14\Sim VF"`
- **Action** :
  ```python
  DEFAULT_BASE = str(Path(__file__).parent.parent.parent / "results")
  ```

#### IMP-04 — Scripts legacy non archivés (confusion de versioning)
- **Problème** : Coexistence dans `tools/analysis/` de `v1` et `v2` pour les scripts 2, 4, 5.
- **Action** : Déplacer toutes les versions `_v1` (sans suffixe) dans `tools/archive/`.

#### IMP-05 — `pom.xml` : 10+ classes exclues sans documentation de motif
- **Fichier** : `pom.xml`, lignes 23–49
- **Action** : Documenter chaque exclusion avec son motif dans un commentaire XML explicite
  (ex: `<!-- Excluded: SFC package deleted in IT15, kept for git history -->`).

#### IMP-06 — Seuil SLA 1.30 non justifié formellement
- **Fichier** : `Configuration.java:49` + `3_generate_global_plots.py:188`
- **Action** : Ajouter dans la méthodologie de l'article :
  > "Le seuil SLA est fixé à ε = 1.30 (tolérance de 30% au-delà du temps de traitement théorique),
  > conformément au modèle QoS des datacenters cloud [citation]."

---

### 🟡 Mineur (qualité du code et robustesse)

#### MIN-01 — `SimpleExampleBase.java` : doublon de `createSDNDatacenter` (2 surcharges)
- L'une avec `workloadParsers` (inutilisée), l'autre sans (utilisée par `main()`).
- **Action** : Supprimer la surcharge inutilisée ou merger.

#### MIN-02 — `submitWorkloads()` dans `main()` est une méthode morte + typo
- `submitWorkloads(broker)` n'est jamais appelée dans le flux réel.
- `broker.submitDeployApplicationn()` : typo "Applicationn" (double 'n').
- **Action** : Supprimer la méthode morte + corriger la typo.

#### MIN-03 — Logging de debug en production (`System.out.println`)
- `System.out.println("$$$$$$$$$$$$ dc ID main : " + datacenter.getId())` (ligne 436)
- **Action** : Remplacer par `Log.printLine()` ou supprimer avant soumission.

#### MIN-04 — `requirements.txt` probablement incomplet (107 octets)
- **Action** : Vérifier que `matplotlib`, `seaborn`, `pandas`, `numpy`, `reportlab`/`weasyprint`
  sont listés avec leurs versions fixées (ex: `matplotlib==3.8.0`).

---

## 3. ✅ Recommandations Prioritaires pour Publication

### PRIO-1 — Compléter la Comparaison 4 Politiques (Bloquant)

> **IMPORTANT** : L'article compare 4 politiques mais les scripts de figures IEEE n'en produisent que 3.
> Sans DynLatBw dans les figures, l'article est scientifiquement incomplet.

**Actions** :
- [ ] Ajouter `Link_DynLatBw` dans `generate_simvf_figures.py` (BUG-03)
- [ ] Étendre la palette dans `3_generate_global_plots.py` (BUG-04)
- [ ] Vérifier que les dossiers `experiment_*/Link_DynLatBw/` existent dans chaque dataset

### PRIO-2 — Valider la Zone de Différenciation SLA (ρ ∈ [0.5, 0.7])

> **IMPORTANT** : À charge élevée (ρ > 0.8), SLA est violé à 100% pour toutes les politiques.
> La contribution de DynLatBw n'est visible sur le SLA que dans la zone ρ ∈ [0.5, 0.7].

**Actions** :
- [ ] Lancer une campagne sur `dataset-small-moderate` (bande passante calibrée pour ρ ≈ 0.6)
- [ ] Vérifier dans `link_utilization_up.csv` que le core link atteint 50–70% (pas 87%+)
- [ ] Produire la figure "SLA Violation Rate vs Load (ρ)" : courbe First vs DynLatBw

### PRIO-3 — Nettoyer le Code Source pour Reproductibilité

**Actions** :
- [ ] Supprimer les 180 lignes commentées dans `DynLatBw.java` (BUG-01)
- [ ] Purger `Configuration.java` (BUG-02) + ajouter Javadoc
- [ ] Fixer le seed aléatoire (IMP-01)
- [ ] Archiver les scripts `_v1` (IMP-04)

### PRIO-4 — Documenter le Modèle Mathématique Complet

Pour l'article, la section Méthodologie doit formaliser :

```
L_e2e = Σ_{i=1}^{h} [ D_proc_switch_i + D_prop_i + D_trans_i + D_queue_i ]

où :
  D_proc_switch_i = latence traitement switch i (ms)
  D_prop_i        = distance / vitesse_propagation
  D_trans_i       = taille_paquet(bits) / (BW_i × η_i)
  D_queue_i       = ρ_i / (μ_i × (1 − ρ_i))   [modèle M/M/1]
  ρ_i             = BW_used_i / BW_i  ∈ [0, 0.99]
  μ_i             = BW_i / taille_paquet
  η_i             = efficacité du lien ∈ (0, 1]
```

- [ ] Ajouter cette formalisation dans `docs/architecture_ras-sdncloudsim.md`
- [ ] Vérifier cohérence avec le code Java (`calculatePathMetrics()`, DynLatBw.java lignes 114–158)

### PRIO-5 — Pipeline de Figures IEEE-Ready

**Checklist figures pour article** :
- [ ] **Fig. 1** — Énergie totale (Wh) : barres groupées VM×Link, 3 datasets (+ DynLatBw)
- [ ] **Fig. 2** — Latence paquets moyenne (ms) : barres groupées (+ DynLatBw)
- [ ] **Fig. 3** — SLA Severity Ratio (actual/expected) : violin + bar ρ-zone
- [ ] **Fig. 4** — CDF des délais paquets par dataset (multi-politique)
- [ ] **Fig. 5** — CPU/RAM intégrales (%.s) : comparaison temporelle
- [ ] **Fig. 6** — Trade-off Énergie vs Latence : scatter plot
- [ ] **Fig. 7** — Link Load Balancing (boxplot utilisation liens)
- [ ] **Fig. 8** *(optionnel)* — Radar comparaison multi-critère VM policies

---

## 4. 📊 État des Métriques — Validité Scientifique

| Métrique | Discriminant | Données OK | Prêt Article |
|---|:---:|:---:|:---:|
| Packet Delay moyen (ms) | ✅ OUI | ✅ | ✅ OUI |
| Queuing Delay (ms) | ✅ OUI | ✅ | ✅ OUI |
| Énergie totale (Wh) | ✅ OUI (+7.1% gap IT41) | ✅ | ✅ OUI |
| CPU Intégrale (%.s) | ✅ OUI (-7.3%) | ✅ | ✅ OUI |
| RAM Intégrale (%.s) | ✅ OUI (-7.3%) | ✅ | ✅ OUI |
| Link Utilization (%) | ✅ OUI | ✅ | ⚠️ Manque DynLatBw dans palette |
| SLA Severity Ratio | ✅ OUI (plage ρ [0.5,0.7]) | ⚠️ Partiel | ❌ Recalibration requise |
| CPU Snapshot Mean | ❌ NON (invariant) | — | — |
| SLA Count (violations) | ❌ NON (identique à ρ>0.8) | — | — |
| BW Host-level | ❌ NON (allocation VM, pas SDN) | — | — |

---

## 5. 📈 Résultats Scientifiques Acquis (IT41, 27–29 Avril 2026)

| Dataset | Métrique | First | DynLatBw | Gain |
|---|---|---|---|---|
| Small | Délai moyen | baseline | -10% | ✅ |
| Small | Énergie totale | baseline | **-7.1%** | ✅ IT41 |
| Small | Durée simulation | 3310s | 3070s | **-7.3%** |
| Small | CPU Intégrale (%.s) | 7,304 | 6,773 | **-7.3%** |
| Small | Queuing Delay cumulé | baseline | -60s | ✅ |
| Medium | Durée simulation | 5440s | 5010s | **-7.9%** |
| Medium | CPU Intégrale (%.s) | 13,080 | 12,044 | **-7.9%** |
| Medium | RAM Intégrale (%.s) | 35,351 | 32,552 | **-7.9%** |

> **Conclusion Expert** : La preuve de concept est établie. DynLatBw surpasse First sur
> toutes les métriques continues (énergie, délai, ressources intégrales). La seule lacune
> restante est la comparaison SLA dans la zone de charge modérée (ρ ∈ [0.5, 0.7]).

---

## 6. 🗂️ Plan d'Action — Prêt à Exécuter (en attente de validation)

### Phase 1 — Corrections Bloquantes (Avant Soumission)
- [ ] **BUG-03** : Ajouter `Link_DynLatBw` dans `generate_simvf_figures.py`
- [ ] **BUG-04** : Étendre palette dans `3_generate_global_plots.py` + fig4 policies
- [ ] **BUG-01** : Nettoyer DFS commenté dans `DynLatBw.java`
- [ ] **BUG-02** : Purger `Configuration.java` + Javadoc constantes
- [ ] **PRIO-2** : Campagne `dataset-small-moderate` (ρ ≈ 0.6)

### Phase 2 — Qualité Scientifique (Avant Revue)
- [ ] **IMP-01** : Fixer seed aléatoire (`Configuration.RANDOM_SEED = 42`)
- [ ] **IMP-04** : Archiver scripts `_v1` dans `tools/archive/`
- [ ] **IMP-02** : Paramétrer `diag_cpu_timeseries.py` avec argparse
- [ ] **IMP-03** : Corriger chemin absolu dans `generate_simvf_figures.py`
- [ ] **PRIO-4** : Formalisation mathématique dans la doc

### Phase 3 — Robustesse Long Terme
- [ ] **BUG-05** : Spawner chaque run dans un process JVM séparé
- [ ] **MIN-01** : Merger surcharges `createSDNDatacenter`
- [ ] **MIN-02** : Supprimer `submitWorkloads()` dead + corriger typo "Applicationn"
- [ ] **MIN-03** : Remplacer `System.out.println` debug
- [ ] **MIN-04** : Fixer `requirements.txt` avec versions fixées
- [ ] Tests JUnit pour `LinkSelectionPolicyDynamicLatencyBw`

---

*Dernière mise à jour : 29 Avril 2026*
