# Plan IT29 : Démontrer l'apport de LinkSelectionPolicyDynamicLatencyBw

## Contexte et Diagnostic

**Question de recherche** : La politique `DynamicLatencyBw` (sélection de lien par
minimisation de la latence composite D_prop + D_trans + D_proc + D_queue M/M/1)
apporte-t-elle une amélioration mesurable par rapport aux politiques classiques ?

**Pourquoi les simulations précédentes ne montrent pas de différence :**
- Les 108 runs du Grand Rerun (IT27) utilisaient uniquement `Dijkstra` — pas `DynLatBw`
- Les runs `DynLatBw` existants (Sim V0, 2026-04-13) tournaient sur une topologie
  **sous-chargée** → D_queue ≈ 0 → DynLatBw se comporte comme Dijkstra
- **La charge réseau actuelle ne provoque pas de congestion** : liens à 1-5 Gbps pour
  des VMs consommant 200-500 Mbps → utilisation < 25% → M/M/1 inactif

**Solution** : Créer un dataset dédié `dataset-medium-congested` avec des liens
**~5× plus étroits** pour forcer ρ > 0.6 et rendre D_queue visible.

> [!IMPORTANT]
> `physical.json` original conservé en backup (`physical_backup.json`).
> Le nouveau `physical_congested.json` est un **fichier séparé** — jamais d'écrasement.

---

## Proposed Changes

### dataset-medium-congested/ [NEW]

#### [NEW] physical_congested.json
Copie de `dataset-medium/physical.json` avec **BW des liens core/agg/edge divisée par 5** :

| Lien | BW Originale | BW Congestionnée |
|------|-------------|-----------------|
| core → agg (principal) | 5 Gbps | **1 Gbps** |
| core → agg (secondaire) | 2-4 Gbps | **400-800 Mbps** |
| agg → edge (principal) | 2 Gbps | **400 Mbps** |
| agg → edge (secondaire) | 1-1.5 Gbps | **200-300 Mbps** |
| edge → host | 1 Gbps | **200 Mbps** |
| liens lents existants | 10-100 Mbps | inchangés |

**Effet attendu** : avec 19 VMs chacune consommant 200-500 Mbps, les liens à
400-800 Mbps seront saturés (ρ > 0.7) → D_queue dominant → DynLatBw choisira
des chemins alternatifs moins chargés.

#### [NEW] virtual.json
Identique à `dataset-medium/virtual.json` — même topologie, mêmes VMs.

#### [NEW] workload.csv
Identique à `dataset-medium/workload.csv`.

---

### Script de campagne ciblée [NEW] run_dynlatbw_benchmark.ps1

**4 runs identiques**, seul le paramètre `LinkPolicy` varie :

| Run | VM | Link | Workload | Dataset | Durée estimée |
|-----|-----|------|----------|---------|--------------|
| R1 | MFF | First | Priority | medium-congested | ~15 min |
| R2 | MFF | BwAllocN | Priority | medium-congested | ~15 min |
| R3 | MFF | Dijkstra | Priority | medium-congested | ~15 min |
| R4 | MFF | **DynLatBw** | Priority | medium-congested | ~15-20 min |

**Total : ~1h** — résultats dans `results/SIM-DynLatBw-Benchmark/`

---

### Script d'analyse [NEW] run_dynlatbw_analysis.ps1

Appelle `consolidated_report.py --simvf` sur le dossier de résultats et produit :
- `fig1_energy.pdf` — comparaison énergie (attendu : similaire entre les 4)
- `fig2_latency.pdf` — **latence E2E** (attendu : DynLatBw < First)
- `fig3_sla.pdf` — **violations SLA** (attendu : DynLatBw meilleur sous congestion)
- `fig4_packet_delay.pdf` — **délai paquets** (attendu : DynLatBw réduit p99)
- `fig5_utilization.pdf` — utilisation CPU/RAM/BW

---

## Vérification Préalable (avant de lancer)

> [!WARNING]
> Vérifier que `DynLatBw` est bien mappé dans `SimpleExampleSelectLinkBandwidth.java`
> comme valeur du paramètre `LinkPolicy`.

```powershell
# Vérifier le mapping dans le code Java
Select-String -Path "src\main\java\**\*.java" `
    -Pattern "DynLatBw|DynamicLatencyBw" -Recurse | Select-Object Filename, Line
```

---

## Etapes d'Exécution

```
Etape 1  [5 min]  : Créer dataset-medium-congested/ + copier physical_congested.json
Etape 2  [5 min]  : Vérifier mapping DynLatBw dans le .java
Etape 3  [~1h]   : Lancer run_dynlatbw_benchmark.ps1 (4 runs séquentiels)
Etape 4  [5 min]  : Lancer run_dynlatbw_analysis.ps1 → figures + CSV
Etape 5  [15 min] : Analyser et rédiger conclusion
```

---

## Résultat Attendu

Sous congestion forcée (ρ ≈ 0.7-0.9) :

| Métrique | First | BwAllocN | Dijkstra | DynLatBw |
|---------|-------|----------|----------|----------|
| Délai pkt moy (ms) | **worst** | medium | medium | **best** |
| Violations SLA | **worst** | medium | medium | **best** |
| Energie (Wh) | identique | identique | identique | identique |
| BW utilisée (%) | ~50% | ~60% | ~60% | **~70%** |

> DynLatBw est le seul à **rééquilibrer la charge** entre les chemins alternatifs.
> Sur une topologie asymétrique (liens lents existants dans physical.json),
> il devrait éviter `l_agg0_edge0_slow` (10 Mbps) et `l_agg2_edge2_slow` (100 Mbps).

---

## Open Questions

> [!IMPORTANT]
> **Confirmer le nom exact du paramètre `DynLatBw`** tel qu'il est passé à Java.
> Dans `run_single_simulation.ps1`, le paramètre `$LinkPolicy` est passé directement
> en argument JVM. Il faut vérifier comment `SimpleExampleSelectLinkBandwidth.java`
> fait le switch-case sur ce paramètre.

---

## Verification Plan

### Tests automatiques
```powershell
# Verification que les 4 runs ont produit des CSVs
Get-ChildItem "results\SIM-DynLatBw-Benchmark" -Recurse -Filter "packet_delays.csv" | Measure-Object
# Attendu : 4 fichiers

# Verification energetique (sanity check)
python -X utf8 tools\consolidated_report.py --simvf "results\SIM-DynLatBw-Benchmark"
```

### Validation manuelle
- [ ] `fig3_sla.pdf` montre DynLatBw avec moins de violations que First
- [ ] `fig4_packet_delay.pdf` montre une réduction du délai moyen avec DynLatBw
- [ ] `path_latency_final.csv` dans le run DynLatBw montre des chemins différents
      de ceux choisis par First (preuve du reroutage actif)
