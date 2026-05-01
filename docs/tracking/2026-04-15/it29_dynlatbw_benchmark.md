# Iteration 29 — Plan & Exécution : Benchmark DynamicLatencyBw sous Congestion (2026-04-15)

## Objectif

Démontrer l'apport de `LinkSelectionPolicyDynamicLatencyBw` vs `First`, `BwAllocN`, `Dijkstra`
en forçant une **congestion réseau réaliste** sur `dataset-medium`.

## Findings Pré-Benchmark

### Mapping Java vérifié (SimpleExampleSelectLinkBandwidth.java:303-340)

| Paramètre CLI | Politique instanciée |
|--------------|---------------------|
| `"First"` | `LinkSelectionPolicyFirst` |
| `"BwAllocN"` | `LinkSelectionPolicyBandwidthAllocationN` |
| `"Dijkstra"` | `LinkSelectionPolicyDijkstra` |
| `"DynLatBw"` | `LinkSelectionPolicyDynamicLatencyBw` ✅ |

> `"DynLatBw"` est le cas **default** dans le switch (ligne 334).
> Si le paramètre est inconnu, DynLatBw est utilisé → mapping correct.

### Pourquoi les sims précédentes ne montrent pas de différence

Topologie medium actuelle : liens 1-5 Gbps, VMs 200-500 Mbps.
Utilisation max = 500 Mbps / 1 Gbps = **50% → ρ = 0.5 → D_queue négligeable**.
→ Le modèle M/M/1 de DynLatBw ne décide rien de différent de Dijkstra.

### Solution retenue

`dataset-medium-congested/` : clone de `dataset-medium` avec liens **÷5**.
- Edge → Host : 1 Gbps → **200 Mbps**
- Agg → Edge : 1.5-2 Gbps → **300-400 Mbps**
- Core → Agg : 2-5 Gbps → **400 Mbps - 1 Gbps**
- Liens lents existants : inchangés (10, 50, 100 Mbps) → `DynLatBw` les évite activement

Avec 19 VMs × 200-500 Mbps sur des liens à 200-400 Mbps : **ρ > 0.8 → D_queue dominant**.

## Plan d'Exécution

### Etape 1 — Création dataset-medium-congested [FAIT]
- `physical_congested.json` créé à partir de `dataset-medium/physical.json`
- `virtual.json` et `workload.csv` : symlinks vers medium (identiques)

### Etape 2 — 4 runs ciblés

```
Run | VM  | Link     | Workload | Dataset           | Durée ~
----|-----|----------|----------|-------------------|--------
R1  | MFF | First    | Priority | medium-congested  | 15 min
R2  | MFF | BwAllocN | Priority | medium-congested  | 15 min
R3  | MFF | Dijkstra | Priority | medium-congested  | 15 min
R4  | MFF | DynLatBw | Priority | medium-congested  | 20 min
```

Résultats : `results/<date>/dataset-medium-congested/MFF/experiment_*/`

### Etape 3 — Analyse et figures
```powershell
python -X utf8 tools\consolidated_report.py --results-dir "results\<date>\dataset-medium-congested"
```

## Résultats Attendus

| Métrique | First | BwAllocN | Dijkstra | **DynLatBw** |
|---------|-------|----------|----------|----------|
| Délai pkt moy | worst | medium | medium | **best** |
| Violations SLA | worst | medium | medium | **best** |
| Utilisation BW | ~50% | ~60% | ~60% | **~75%** |
| Energie | ≈ identique | ≈ identique | ≈ identique | ≈ identique |

## Script utilisé
`run_dynlatbw_benchmark.ps1` — 4 runs séquentiels automatiques.

## Statut
- [x] Mapping Java vérifié
- [x] dataset-medium-congested créé
- [x] Script benchmark créé
- [ ] Runs lancés
- [ ] Analyse et figures
- [ ] Rédaction conclusion

*Créé le 2026-04-15*
