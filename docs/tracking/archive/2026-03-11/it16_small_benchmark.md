# Itération 16 : Benchmark sur Dataset-Small avec Redondance de Liens

**Date :** 2026-03-11  
**Objectif :** Évaluer l'impact des politiques de sélection de liens (BwAllocN vs First) et d'allocation de VM sur une topologie contenant des liens redondants à bande passante contrastée (10 Mbps vs 5 Gbps).

---

## 📋 Plan de Mise en Œuvre

L'objectif est de relancer une série complète de tests sur le `dataset-small` après avoir configuré une redondance de liens stratégique.

### Configuration
- **Total de simulations** : 24
- **VM Policies** : LFF, MFF, LWFF
- **Link Policies** : First, BwAllocN
- **Workload Policies** : PSO, RR, Priority, SJF

### Stratégie de Redondance
- Injection de liens parallèles entre `agg1` et `edge1` (10M vs 5G).
- Injection de liens parallèles entre `agg0` et `edge0` (100M vs 5G).
- **Hypothèse** : `BwAllocN` devrait systématiquement choisir le lien 5G, tandis que `First` pourrait être piégé sur le lien lent, augmentant drastiquement les délais de paquets et la consommation d'énergie.

---

## ✅ Liste des Tâches

- [x] Vérifier la topologie et le workload (Redondances, Cohérence)
- [x] Exécuter simulations LFF sur small (8 combinaisons)
- [x] Exécuter simulations MFF sur small (8 combinaisons)
- [x] Exécuter simulations LWFF sur small (8 combinaisons)
- [x] Générer le rapport consolidé pour dataset-small
- [x] Analyse comparative finale (Énergie, SLA, Délai Paquet)

---

## 🛠️ État d'Avancement Technique
Les correctifs de stabilité suivants sont actifs :
1. **minBw** : Initialisé à `POSITIVE_INFINITY` pour éviter div/0.
2. **setPacketSizeBytes** : Correctement propagé pour la classification des flux hybrides.
3. **VM-to-Host Resolution** : Fixé dans `processNextActivity`.
4. **Log Flushing** : Forcé en fin de simulation pour capturer toutes les métriques.
