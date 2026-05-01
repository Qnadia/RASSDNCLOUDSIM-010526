# Itération 26 : LWFF Large Dataset — 800 Workloads (2026-04-11)

## 🎯 Objectif

Relancer tous les scénarios **LWFF** sur `dataset-large` avec **800 workloads** (au lieu de 1000)
afin de contourner le stall structurel identifié en IT23–IT25.

> **Pourquoi 800 ?**
> Les VMs 12, 14, 20, 29 échouaient à la création (BW insuffisant sur hôte 19). Leurs workloads
> associés ne pouvaient jamais se terminer, bloquant le compteur avant d'atteindre 1000.
> Avec 800 workloads, on réduit la charge pour que la simulation s'achève proprement.
> Les fixes IT24/IT25 (reset global + check BW LWFFF) sont maintenus.

---

## 🔧 Préparation

### ✅ Backup workload original
```
dataset-large/workload.csv.bak1000  →  1001 lignes (header + 1000 workloads)
dataset-large/workload.csv          →   801 lignes (header + 800 workloads)  ← ACTIF
```

### ✅ Compilation
```
mvn compile -q  →  exit 0 (2026-04-11 ~17h24)
```

---

## 📋 Scénarios LWFF à Valider

| # | VM    | Lien BW   | Scheduler | Statut     | Date | CSV |
|---|-------|-----------|-----------|------------|------|-----|
| 5 | LWFF  | BwAllocN  | Priority  | ✅ Terminé  | 03-22 | ✅ (IT25 OK, 1000 WL) |
| 6 | LWFF  | First     | Priority  | ⏳ À faire  | — | — |
| 7 | LWFF  | BwAllocN  | SJF       | ⏳ À faire  | — | — |
| 8 | LWFF  | BwAllocN  | FCFS      | ⏳ À faire  | — | — |
| 9 | LWFF  | First     | SJF       | ⏳ À faire  | — | — |
|10 | LWFF  | First     | FCFS      | ⏳ À faire  | — | — |
|11 | LWFF  | BwAllocN  | PSO       | ⏳ À faire  | — | — |
|12 | LWFF  | First     | PSO       | ⏳ À faire  | — | — |

---

## 🚀 Commandes de Lancement Manuel

> Exécuter **une par une** depuis un terminal PowerShell dans le dossier du projet.

### Scénario 6 — LWFF + First + Priority
```powershell
powershell -ExecutionPolicy Bypass -File .\run_single_simulation.ps1 -VmPolicy LWFF -LinkPolicy First -WorkloadPolicy Priority -Dataset dataset-large -SaveLogs
```

### Scénario 7 — LWFF + BwAllocN + SJF
```powershell
powershell -ExecutionPolicy Bypass -File .\run_single_simulation.ps1 -VmPolicy LWFF -LinkPolicy BwAllocN -WorkloadPolicy SJF -Dataset dataset-large -SaveLogs
```

### Scénario 8 — LWFF + BwAllocN + FCFS
```powershell
powershell -ExecutionPolicy Bypass -File .\run_single_simulation.ps1 -VmPolicy LWFF -LinkPolicy BwAllocN -WorkloadPolicy FCFS -Dataset dataset-large -SaveLogs
```

### Scénario 9 — LWFF + First + SJF
```powershell
powershell -ExecutionPolicy Bypass -File .\run_single_simulation.ps1 -VmPolicy LWFF -LinkPolicy First -WorkloadPolicy SJF -Dataset dataset-large -SaveLogs
```

### Scénario 10 — LWFF + First + FCFS
```powershell
powershell -ExecutionPolicy Bypass -File .\run_single_simulation.ps1 -VmPolicy LWFF -LinkPolicy First -WorkloadPolicy FCFS -Dataset dataset-large -SaveLogs
```

### Scénario 11 — LWFF + BwAllocN + PSO ⚠️ (lent)
```powershell
powershell -ExecutionPolicy Bypass -File .\run_single_simulation.ps1 -VmPolicy LWFF -LinkPolicy BwAllocN -WorkloadPolicy PSO -Dataset dataset-large -SaveLogs
```

### Scénario 12 — LWFF + First + PSO ⚠️ (lent)
```powershell
powershell -ExecutionPolicy Bypass -File .\run_single_simulation.ps1 -VmPolicy LWFF -LinkPolicy First -WorkloadPolicy PSO -Dataset dataset-large -SaveLogs
```

---

## ✅ Critères de Validation par Scénario

Un scénario est validé si dans le log :
- `800/800` completions atteint (ou `🎉 STOP_MONITORING`)
- Au moins un fichier CSV non vide généré dans `results/`
- Pas d'erreur `❌ Datacenter non trouvé` en boucle

---

## 📅 Historique (2026-04-11)

| Heure | Action |
|-------|--------|
| ~17h22 | Décision de réduire à 800 workloads pour débloquer LWFF |
| ~17h23 | Backup `workload.csv.bak1000` créé |
| ~17h23 | `workload.csv` tronqué à 801 lignes (header + 800 WL) |
| ~17h24 | `mvn compile -q` → exit 0 ✅ |
| ~17h24 | Création du script `run_lwff_large.ps1` (batch séquentiel) |
| ~17h25 | Création de ce fichier de tracking IT26 |
| — | Lancement Scénario 6 (LWFF + First + Priority) |
| — | Lancement Scénario 7 (LWFF + BwAllocN + SJF) |
| — | Lancement Scénario 8 (LWFF + BwAllocN + FCFS) |
| — | Lancement Scénario 9 (LWFF + First + SJF) |
| — | Lancement Scénario 10 (LWFF + First + FCFS) |
| — | Lancement Scénario 11 (LWFF + BwAllocN + PSO) |
| — | Lancement Scénario 12 (LWFF + First + PSO) |

---

## 📝 Notes

- Le scénario **5 (LWFF + BwAllocN + Priority)** reste valide depuis IT25 (1000 WL, terminé).
  Pas besoin de le relancer sauf si tu veux une base homogène à 800 WL.
- Les logs sont sauvegardés dans `logs/` avec horodatage automatique.
- Après chaque sim, vérifier la taille du CSV dans `results/`.
