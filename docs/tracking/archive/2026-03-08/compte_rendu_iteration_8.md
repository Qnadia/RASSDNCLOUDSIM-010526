# Compte Rendu — Itération 8 (2026-03-08T15:19)

**Objet :** Analyse croisée tracking/code + correction des anomalies résiduelles

---

## 1. Vérification des Corrections Documentées

Une analyse croisée systématique a été effectuée entre toutes les corrections documentées (Ité 1→7) et l'état réel du code source.

### ✅ Corrections Confirmées Présentes dans le Code

| Correction Documentée | Fichier | Ligne | Statut |
|:---|:---|:---|:---|
| `VM_UPDATE = SDN_BASE + 28` | `CloudSimTagsSDN.java` | 44 | ✅ |
| `super.processEvent(ev)` dans le bloc `default` | `SDNDatacenter.java` | 322, 325 | ✅ |
| `case CloudSimTagsSDN.VM_UPDATE` | `SDNDatacenter.java` | 394 | ✅ |
| `Request.submitTime` propagé (constructeur de copie) | `Request.java` | 69 | ✅ |
| `minBw = Double.POSITIVE_INFINITY` | `SDNDatacenter.java` | 625 | ✅ (faux positif) |
| `req.setPacketSizeBytes(pkt.getSize())` | `SDNDatacenter.java` | 635 | ✅ (faux positif) |
| `Link.isActive()` étendu | `Link.java` | 377–381 | ✅ |

> Les anomalies `minBw` et `setPacketSizeBytes` rapportées étaient des **faux positifs** : le code était correct, le problème venait du chemin de recherche utilisé pour la vérification.

---

## 2. Corrections Appliquées dans cette Itération

### 🐛 A. NPE dans `processApplication` (Crash si VM non allouée)
- **Cause :** Lors de l'itération sur les flows, si une VM n'a pas pu être allouée (ex: `not enough BW`), `vmIdToNode.get(flow.getDstId())` retourne `null`. Appeler `.getName()` sur ce null provoquait un `NullPointerException`.
- **Correction :** Ajout d'une garde null pour `src` et `dst` dans la boucle de flows — les flows orphelins sont ignorés avec un avertissement.
- **Fichier :** `SDNDatacenter.java` (méthode `processApplication`, ligne ~2286)

### 🔧 B. Script `debug_run.ps1` Réécrit
- **Cause :** L'ancien script utilisait le JAR ombragé (`cloudsimsdn-1.0-with-dependencies.jar`) qui causait systématiquement un `ClassNotFoundException` pour les classes `SSLAB`.
- **Correction :** Réécriture complète pour utiliser `target/classes` + `cp.txt` (généré par `mvn dependency:build-classpath`). Accepte maintenant des paramètres `[vmAlloc] [linkPol] [wfPol] [dataset]`.
- **Fichier :** `debug_run.ps1`

### 🧹 C. Suppression du Répertoire Orphelin
- `result_dataset-small/` supprimé : répertoire créé par erreur lors du débogage. La simulation écrit correctement dans `results/YYYY-MM-DD/dataset-X/experiment_Y/`.

### 🔀 D. Algorithme de Routage "First" Corrigé (BFS Sans Boucle)
- **Cause :** L'algorithme `First` natif échouait systématiquement à trouver des chemins dans la topologie hiérarchique Fat-Tree car il tentait un `getLinks(src, dest)` direct ignorant les switches intermédiaires. Ceci causait l'échec de 100% des requêtes (erreur `No path found in hierarchical topology`) et laissait les MIPS/l'énergie à zéro.
- **Correction :** Implémentation du même algorithme de Breadth-First Search (BFS) sans boucle que celui utilisé pour `BwAllocN`, garantissant de trouver un chemin valide.
- **Vérification (Demande Utilisateur) :** Contrairement à `BwAllocN` qui trie par bande passante restante, la logique de choix finale dans `LinkSelectionPolicyFirst` a été préservée pour sélectionner aveuglément le premier lien du groupe parallèle (`links.get(0)`), ignorant totalement la bande passante et la latence.
- **Fichier :** `LinkSelectionPolicyFirst.java`

---

## 3. Nouveau Problème Identifié — VM `db1` Non Allouée

### Symptôme
```
not enough BW
Cannot assign this VM(VM #13 (db1)) to any host. NumHosts=6
```

### Analyse
La politique **LFF** (`LeastFullFirst`) distribue les VMs sur les hôtes en cherchant celui le moins utilisé. Avec 8 VMs et leurs BW cumulées :
- `web0`=200M, `web1`=200M, `app0`=300M, `app1`=300M, `db0`=500M, `db1`=500M, `cache0`=400M, `cache1`=400M
- **Total cumulé ≈ 2800 Mbps** sur 6 hôtes à **1000 Mbps** chacun

En LFF, les premières VMs sont distribuées 1 par hôte (ok), mais `db0` (500M) puis `db1` (500M) peuvent tomber sur des hôtes ayant déjà des VMs, saturant leur BW.

### Solutions Envisagées
| Option | Action | Impact |
|:---|:---|:---|
| **A (Recommandée)** | Augmenter BW hôtes dans `physical.json` : `1000000000` → `2000000000` | Aucun impact fonctionnel, fix immédiat |
| **B** | Réduire BW des VMs db0/db1 dans `virtual.json` : `500000000` → `300000000` | Peut changer les résultats de performance |
| **C** | Utiliser `Binpack` ou `Spread` à la place de `LFF` pour ce dataset | Pas d'allocation mixte |

**Statut :** ⏳ En attente de décision utilisateur (optionA/B/C).

---

## 4. État Global du Projet (2026-03-08T15:19)

| Composant | Statut |
|:---|:---|
| Routage BFS (anti-boucle + anti-lien fantôme) | ✅ Opérationnel (BwAllocN et First) |
| Événements VM_UPDATE / super.processEvent | ✅ Corrigés |
| Request.submitTime + copy constructor | ✅ Correct |
| Exécution via target/classes | ✅ Stable (debug_run.ps1) |
| NPE processApplication | ✅ Corrigé (Ité 8) |
| Allocation VM db1 (dataset-small + LFF) | ✅ Faux Positif (VM correctement allouée) |
| Erreur "No path found" pour politique First | ✅ Corrigé (BFS implémenté Ité 8) |
| Énergie switch active > IDLE | 🟡 Toujours 0 — à investiguer |
| Classpath JAR ombragé | ✅ Contourné (cp.txt) |
