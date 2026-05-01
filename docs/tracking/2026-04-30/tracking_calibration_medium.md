# 📋 Tracking Calibration Dataset Medium — CloudSimSDN Research
*Date : 30 Avril 2026 | Auteur : Antigravity Analysis | Statut : En cours d'exécution (Validation)*

---

## 1. 🔍 Synthèse de l'Anomalie (Rappel)

L'analyse des résultats du **27/04/2026** a confirmé une inversion critique des performances :
- **Durée Simulation (First)** : Medium (7900s) >> Large (2403s)
- **Délai Paquet** : Medium (775s) >> Large (345s)

Cette inversion rend les comparaisons de scalabilité scientifiquement invalides, car le dataset "Medium" est paradoxalement plus "lourd" et plus congestif que le dataset "Large".

---

## 2. 🧬 Diagnostic des Causes Racines

| Fichier | Problème Identifié | Impact |
| :--- | :--- | :--- |
| **`physical.json`** | Liens Core/Agg à **10 Mbps** et **50 Mbps** dans le dataset Medium. | Goulot d'étranglement extrême non représentatif d'un dataset "moyen". |
| **`workload.csv`** | `psize` atteignant **400 Mo** pour des liens à 10 Mbps. | Temps de transmission par paquet > 300s (T = 400MB / 10Mbps). |
| **`virtual.json`** | Puissance VM identique entre Medium et Large (2000 MIPS). | Large absorbe mieux la charge car il a 2x plus de serveurs et des liens 80x plus larges. |

---

## 3. 🛠️ Plan de Modification (Calibration)

### Phase 1 : Topologie Physique (`datasets/dataset-medium/physical.json`)
- [x] **Supprimer** les liens "slow" (10M, 50M, 100M).
- [x] **Rehausser** les liens Core-Aggregation à une cible de **5 Gbps** (5 000 000 000).
- [x] **Uniformiser** les liens Aggregation-Edge à **1 Gbps** (1 000 000 000).
- [x] Passer la bande passante totale des switches Core à **50 Gbps**.

### Phase 2 : Workload Applicatif (`datasets/dataset-medium/workload.csv`)
- [ ] **Capper** la valeur de `psize` à un maximum de **150 Mo**.
- [ ] Maintenir la densité de requêtes actuelle pour garder la pression réseau (ρ zone [0.4, 0.7]).

### Phase 3 : Topologie Virtuelle (`datasets/dataset-medium/virtual.json`)
- [x] Augmenter la puissance de calcul des VMs (MIPS) pour situer Medium entre Small (1000) et Large (8000).
- [x] **Cible** : 4000 MIPS pour les VMs Web/App/Svc, 8000 MIPS pour DB.
- [x] Ajuster `endtime` à **450s** pour couvrir l'extension du workload.

---

## 4. 📈 Résultats Attendus après Calibration

Une progression linéaire et logique des métriques :
1. **Délai Small < Délai Medium < Délai Large**
2. **Énergie Small < Énergie Medium < Énergie Large**
3. **Facteur de charge (ρ)** en zone de différenciation pour BLA (0.5 - 0.7) sur le dataset Medium.

---

## 5. 🗓️ Journal d'Exécution

- [x] Génération des fichiers corrigés.
- [x] Lancement d'un run de validation (Baseline First vs BLA).
- [/] Vérification de la cohérence `Medium < Large`.

---

## 6. 📝 Rapport d'Analyse Post-Exécution (Données du 27/04)

### Constat d'Anomalie
Le Dataset Medium consommait plus d'énergie que le Dataset Large (~1414 Wh vs ~823 Wh).

### Explication Technique (Antigravity Analysis)
1. **Saturation Réseau Critique** : Le dataset Medium du 27/04 possédait des liens Core à 500 Mbps et Edge à 100 Mbps, tandis que le Large bénéficiait de liens Core à 1000 Mbps (et certains à 40 Gbps dans la topologie JSON).
2. **Effet "Time Stretching"** : La simulation CloudSimSDN ne se termine qu'à l'arrivée du dernier paquet. La congestion extrême du Medium a étiré la durée de complétion à **7 900s** (contre 2 403s pour le Large).
3. **Multiplicateur Énergétique** : Bien que le Large ait plus d'hôtes (20 vs 12), le fait que le Medium tourne **3,3x plus longtemps** double quasiment l'intégrale de consommation (host-seconds).
   - Medium : 12 hôtes × 7 900 s = 94 800 hs.
   - Large : 20 hôtes × 2 403 s = 48 060 hs.

> [!IMPORTANT]
> Cette analyse valide la nécessité de la calibration actuelle. Le dataset Medium ne doit pas être un "stress-test de saturation" plus dur que le Large, mais un point intermédiaire cohérent.
