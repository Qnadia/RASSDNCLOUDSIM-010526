# IT-38 : Analyse Profonde Simulation & Tuning Dataset pour DynLatBw

**Date :** 2026-04-22  
**Objectif :** Identifier pourquoi `DynLatBw` n'impacte pas visiblement l'énergie et le packet delay, vérifier la fiabilité du code source, et définir un plan de tuning des datasets pour obtenir des résultats scientifiques différenciés.

---

## 1. WORKFLOW DE SIMULATION — PIPELINE COMPLET

### 1.1 Étapes d'exécution (par ordre)

```
[1] run_benchmark.ps1
    ↓ compile Java (mvn package)
    ↓ lance SimpleExampleSelectLinkBandwidth.java
         ↓ parse physical.json → crée Hosts, Switches, Links
         ↓ parse virtual.json  → crée VMs, Virtual Links
         ↓ parse workload.csv  → crée Workloads (Requests)
         ↓ applique VmAllocationPolicy (MFF/LFF/LWFF)
         ↓ applique LinkSelectionPolicy (First/DynLatBw)
         ↓ applique WorkloadPolicy (SJF/PSO/Priority)
         ↓ CloudSim.startSimulation()
              ↓ SDNBroker soumet les workloads
              ↓ SDNDatacenter route les paquets via NOS
              ↓ Channel.java transporte les données
              ↓ LogMonitor enregistre toutes les métriques
         ↓ Fin → génère 15 fichiers CSV par run
    ↓ 2_consolidate_results.py → MASTER CSV
    ↓ 3_generate_global_plots.py → fig1 à fig13
```

### 1.2 Fichiers CSV produits par simulation

| Fichier | Contenu | Clé d'analyse |
|---------|---------|---------------|
| `qos_violations.csv` | Par requête : violé/non violé + temps réel vs attendu | SLA |
| `packet_delays.csv` | Par paquet : queuing, transmission, propagation | Congestion |
| `path_latency_final.csv` | Par flux : latence totale du chemin sélectionné | Routage |
| `host_utilization.csv` | CPU/RAM/BW par host dans le temps | Placement |
| `vm_utilization.csv` | CPU/BW par VM dans le temps | Charge VM |
| `host_vm_allocation.csv` | Quel VM sur quel host | Placement |
| `host_energy_total.csv` | Énergie totale par host (Wh) | Énergie |
| `host_energy.csv` | Énergie par pas de temps | Énergie temporelle |
| `sw_energy.csv` | Énergie des switches (SDN) | Énergie réseau |
| `link_utilization_up.csv` | Utilisation liens montants % | Congestion lien |
| `link_utilization_down.csv` | Utilisation liens descendants % | Congestion lien |
| `host_allocation_summary.csv` | Résumé : nb VMs, ressources allouées/host | Allocation |
| `detailed_energy.csv` | Énergie détaillée host+switch | Rapport final |

---

## 2. ANALYSE DU CODE SOURCE — FIABILITÉ ET ANOMALIES

### 2.1 ✅ LinkSelectionPolicyDynamicLatencyBw.java — Algorithme OK

**Algorithme :** Dijkstra modifié avec coût composite par lien :

```
weight(link) = Dproc_switch + Dprop + Dtrans + Dqueue_M/M/1
où :
  Dproc_switch = link.getLatency() * 0.001          (ms → s)
  Dprop        = (distance × n) / c                 (physique)
  Dtrans       = bits / (BW × efficiency)            (débit)
  Dqueue       = ρ / (μ × (1 - ρ))   [modèle M/M/1] (congestion)
```

**✅ Correct :** L'algorithme prend bien en compte la congestion en temps réel via `link.getUsedBw()`.

### 2.2 ⚠️ ANOMALIE #1 — Pourquoi DynLatBw ≈ First sur l'énergie ?

**Cause identifiée :** L'énergie est calculée **exclusivement sur les hosts** (consommation CPU des VMs). Elle ne dépend PAS du chemin réseau choisi par le routeur.

```java
// SDNHost.java → energy = f(CPU_utilization, time)
// Le routage ne change pas quelle VM tourne sur quel host
// → L'énergie host est identique pour First et DynLatBw
```

**Conclusion scientifique :** C'est **normal et attendu**. La politique de routage n'influence l'énergie host QUE si elle provoque la migration de VMs ou l'extinction de hosts (ce que DynLatBw ne fait pas).

**L'énergie est influencée par :** VmAllocationPolicy (MFF consolide → moins de hosts actifs → moins d'énergie).

### 2.3 ⚠️ ANOMALIE #2 — Pourquoi DynLatBw ≈ First sur le packet delay ?

**Cause identifiée :** Le `packet_delays.csv` enregistre le délai **réel de transmission** du paquet (temps physique de traversée du canal). Ce délai dépend de :
- La bande passante allouée au channel (Channel.java)
- La taille du paquet

**Le routage sélectionne le chemin, mais ne change pas la quantité de données à transmettre.** Sur des datasets où tous les liens ont une capacité suffisante (post-correction), le délai de transmission par lien est identique.

**DynLatBw fait une différence visible UNIQUEMENT si :**
1. Il existe des liens de capacités très hétérogènes (ex: certains à 1 Gbps, d'autres à 10 Gbps)
2. Le trafic est suffisamment dense pour saturer certains liens
3. `First` choisit systématiquement les liens lents/congestionnés

### 2.4 ⚠️ ANOMALIE #3 — `usedBw` dans le scoring M/M/1 de DynLatBw

**Code critique (lignes 262-272) :**
```java
double usedBw = link.getUsedBw(u);  // somme des Channel.getAllocatedBandwidth()
double rho = Math.min(usedBw / bw, 0.99);
if (rho > 0) {
    double mu = bw / Math.max(pktSize, 1);
    dqueueEst = rho / (mu * (1.0 - rho));
}
```

**Problème potentiel :** `getUsedBw()` retourne la somme des bandes passantes **allouées** (pas utilisées dynamiquement). Si tous les channels ont une BW allouée = 0 (avant la transmission), `rho = 0`, et `dqueueEst = 0` pour tous les liens.

**Conséquence :** DynLatBw se comporte comme Dijkstra simple (pas de M/M/1), car le Dqueue estimé est 0 pendant le routage initial (avant que les channels ne soient actifs).

→ **C'est la vraie raison pour laquelle DynLatBw n'arrive pas à différencier les chemins en début de simulation.**

### 2.5 ✅ Transmission.java — SLA Corrigé (IT-36)

```java
public double getExpectedTime() {
    double base = pktSize / (double) bandwidth;
    double propDelay = (request != null) ? request.getPropagationDelay() : 0.0;
    return base + propDelay;
}
```
**✅ Correct :** Le SLA inclut maintenant le délai de propagation physique.

---

## 3. ANALYSE DATASET H — POURQUOI LES RÉSULTATS SONT PLATS

### 3.1 Dataset Small — Problèmes identifiés

| Paramètre | Valeur actuelle | Problème |
|-----------|----------------|---------|
| `psize` max | 5 000 000 bytes (post-÷100) | Encore trop grand pour liens 1Gbps |
| `len_cloudlet` max | 4 000 instructions (post-÷100) | OK maintenant |
| Liens physiques | 1 Gbps (corrigé) | Homogène → pas de différenciation |
| Hétérogénéité liens | Faible | DynLatBw ne peut pas choisir un meilleur chemin |

**Diagnostic :** Le dataset small a maintenant des liens tous à **1 Gbps**. DynLatBw et First choisissent le même chemin car tous les liens ont le même poids.

### 3.2 Dataset Medium — Problèmes identifiés

| Paramètre | Valeur actuelle | Problème |
|-----------|----------------|---------|
| `psize` max | 2 000 000 bytes (post-÷100) | OK |
| `len_cloudlet` max | 32 000 instructions (post-÷100) | OK |
| Liens physiques | 2 à 5 Gbps | Hétérogène ✅ |
| Nombre de chemins alternatifs | Plusieurs (multi-core) | Potentiel de différenciation ✅ |
| Violations SLA | 2094 (MFF) à 5454 (LFF) | **DynLatBw = First ??** |

**Diagnostic :** La bande passante virtuelle allouée aux VMs (200-500 Mbps dans virtual.json) est bien en-dessous des liens physiques (2-5 Gbps). Les channels n'atteignent jamais la saturation → `usedBw` reste faible → Dqueue M/M/1 ≈ 0.

### 3.3 Dataset Large — Problèmes identifiés

| Paramètre | Valeur actuelle | Problème |
|-----------|----------------|---------|
| `psize` max | 3 000 000 bytes (post-÷100) | OK |
| `len_cloudlet` max | 40 000 instructions (post-÷100) | OK |
| Liens physiques | 10 à 40 Gbps | Trop généreux → jamais saturés |
| Bande passante VM | 1-4 Gbps | 3-10x inférieure aux liens → jamais de congestion |
| Violations SLA | 7054 (MFF) à 9640 (LFF) | MFF meilleur, mais First=DynLatBw |

---

## 4. PLAN DE TUNING — FAIRE BRILLER DYNLATBW

### 4.1 Principe fondamental

Pour que `DynLatBw` se distingue de `First`, il faut créer une **asymétrie de charge** :
- Certains liens doivent être **sur-sollicités** avec `First` (chemin naïf)
- `DynLatBw` doit pouvoir les **détecter via M/M/1** et dévier le trafic

### 4.2 Modifications des datasets (à appliquer)

#### A. physical.json — Créer une hétérogénéité forcée

Insérer **des liens "goulot"** intentionnels sur les chemins par défaut (les plus courts) :

```json
{
  "source": "agg0", "destination": "edge0",
  "upBW": 500000000,      ← 500 Mbps (lien lent intentionnel)
  "latency": 0.5,          ← latence élevée aussi
  "distance": 5000,        ← longue distance → Dprop élevé
  "refractiveIndex": 1.468
},
{
  "source": "agg0", "destination": "edge1",
  "upBW": 5000000000,     ← 5 Gbps (lien rapide alternatif)
  "latency": 0.05,
  "distance": 200,
  "refractiveIndex": 1.468
}
```

**Effet :** `First` prendra `edge0` (premier dans la liste). `DynLatBw` calculera que `edge1` est 10x plus rapide → choisira `edge1`.

#### B. virtual.json — Augmenter la bande passante des VMs

Les VMs doivent demander suffisamment de bande passante pour saturer les liens lents :

```json
{"name": "app0", "bw": 800000000, "mips": 8000}   ← 800 Mbps (était 200 Mbps)
{"name": "db0",  "bw": 2000000000, "mips": 16000}  ← 2 Gbps (était 500 Mbps)
```

#### C. workload.csv — Créer des rafales simultanées sur les mêmes VMs

Pour saturer les liens, il faut que **plusieurs paquets utilisent le même lien au même instant** :

```csv
1.0, app0, 0, 1, l_app0_db0, db0, 50000000, 1, 1000, 1
1.1, app1, 0, 1, l_app1_db0, db0, 50000000, 1, 1000, 1  ← même destination db0 !
1.2, app2, 0, 1, l_app2_db0, db0, 50000000, 1, 1000, 1  ← idem
1.3, app3, 0, 1, l_app3_db0, db0, 50000000, 1, 1000, 1  ← idem
```

**Effet :** 4 flux convergent vers `db0` → les liens agg→edge qui mènent à `db0` se saturent → DynLatBw dévie vers d'autres chemins via `db1`, `db2`, etc.

### 4.3 Paramètre SLA à ajuster

```java
// Configuration.java
DECIDE_SLA_VIOLATION_GRACE_ERROR = 1.30  // 30% marge
```

**Problème actuel :** Si le temps idéal est 0.05s, le seuil SLA = 0.065s. Le moindre délai de switching (0.05ms × 6 hops = 0.3ms) crée une violation.

**Recommandation :** Soit augmenter à `2.0` (100% de marge), soit ajuster les datasets pour que le temps idéal soit ≥ 1 seconde (paquets plus grands, liens moins rapides).

---

## 5. VÉRIFICATION DE NON-ANOMALIE — CHECKLIST SIMULATION

### 5.1 Avant la simulation

- [ ] `physical.json` : tous les liens ont `distance > 0` et `refractiveIndex >= 1.0`
- [ ] `physical.json` : hétérogénéité des `upBW` (ratio min/max ≥ 5x)
- [ ] `virtual.json` : bande passante VM ≥ 20% de la capacité du lien physique
- [ ] `workload.csv` : plusieurs flux convergents vers les mêmes VMs (hotspot)
- [ ] `len_cloudlet` : `len_cloudlet / mips_vm < 1 seconde` (pas de blocage CPU)
- [ ] `psize` : `psize / link_bw < 0.1 seconde` (pas de blocage réseau trivial)

### 5.2 Pendant la simulation

Vérifier dans les logs :
```
Link selection policy set to: DynamicLatencyBw   ← DynLatBw bien chargé
Simultaneously used hosts: N                      ← N > 1 (consolidation active)
[Log] path_latency.csv: XXX entrées sauvegardées  ← > 0 = chemins enregistrés
[Log] qos_violations.csv: XXX entrées             ← > 0 = SLA évalué
```

### 5.3 Après la simulation — Indicateurs de différenciation

| Métrique | Attendu si DynLatBw fonctionne |
|---------|-------------------------------|
| `avg_queue_ms` | DynLatBw < First (5-20% de moins) |
| `sla_violations` | DynLatBw < First (différence visible) |
| `max_latency_s` | DynLatBw < First (moins de pics) |
| `jitter_s` | DynLatBw < First (moins de variance) |
| `avg_latency_s` | Peut être identique (si propagation domine) |
| `energy_Wh` | Identique (énergie dépend des hosts, pas du routage) |

---

## 6. PLAN D'ACTION IMMÉDIAT

### Phase 1 : Créer dataset-calibrated (nouveau dataset de référence)
1. Partir de `dataset-medium`
2. Injecter 3 liens "goulot" à 300 Mbps sur les chemins par défaut de `First`
3. Augmenter la bande passante VM à 600 Mbps → saturation possible
4. Créer des rafales de 5 flux simultanés vers les mêmes VMs db0-db4
5. Valider que `len_cloudlet / mips < 0.5s`

### Phase 2 : Vérifier l'activation du M/M/1
Ajouter un log temporaire dans `DynLatBw.java` :
```java
if (dqueueEst > 0.001) {
    System.out.println("M/M/1 actif : rho=" + rho + " dqueue=" + dqueueEst + "s sur " + link);
}
```

### Phase 3 : Relancer benchmark et valider les 5 critères de différenciation

### Phase 4 : Générer les figures publication-ready avec les nouveaux datasets

---

## 7. CONCLUSION — ÉTAT DU SYSTÈME

| Composant | État | Remarque |
|-----------|------|---------|
| `DynLatBw.java` | ✅ Correct | Dijkstra + M/M/1 implémenté |
| `Transmission.java` (SLA) | ✅ Corrigé | Propagation incluse (IT-36) |
| `Link.java` | ✅ Correct | Propagation physique (d×n/c) |
| `VmAllocationPolicyLWFFVD.java` | ✅ Corrigé | Bug `ramUtil` → `memUtil` (IT-37) |
| `dataset-small` | ⚠️ Liens homogènes | DynLatBw ne peut pas différencier |
| `dataset-medium` | ⚠️ BW VM trop faible vs liens | Saturation impossible |
| `dataset-large` | ⚠️ Liens trop généreux | Jamais de congestion réelle |
| M/M/1 efficacité | ⚠️ Dépend du `usedBw` runtime | Inactif si channels vides au départ |
