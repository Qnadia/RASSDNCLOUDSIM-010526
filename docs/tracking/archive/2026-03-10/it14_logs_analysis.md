# Iteration 14: Résolution des Boucles Infinies et Fiabilisation des Logs (IT14 & IT15)

## Date : 2026-03-10

## 1. Description Générale
Cette itération regroupe plusieurs correctifs critiques ayant pour but de stabiliser la simulation et de garantir l'intégrité de la génération des fichiers de logs CSV de télémétrie. Nous avons identifié et résolu deux problèmes majeurs :
- Des boucles infinies qui bloquaient la progression du temps dans CloudSim.
- La génération de fichiers CSV vides causée par un arrêt prématuré du système de supervision (`LogMonitor`).

## 2. Historique des Problèmes et Diagnostics

### 2.1 Boucle Infinie dans le Scheduler (IT14)
- **Symptôme :** La simulation se figeait à l'exécution de certains cloudlets, générant un nombre aberrant de MIs traités sans que la simulation n'avance dans le temps.
- **Diagnostic :**
  1. La classe `CloudletSchedulerTimeSharedMonitor.java` contenait une erreur logique (un `break` mal placé au lieu d'un `continue`), ce qui empêchait la mise à jour complète de l'allocation des VMs.
  2. La classe `SDNDatacenter.java` oubliait de retirer les activités traitées de la file d'attente pour la prochaine exécution (`req.removeNextActivity()`), forçant le même traitement en boucle.
  3. L'absence d'un délai minimum (`CloudSim.getMinTimeBetweenEvents()`) créait des avalanches d'événements à `t=0`, empêchant l'horloge interne de CloudSim de progresser.

### 2.2 Fichiers Logs Vides et Arrêt Prématuré (IT15 / Suite IT14)
- **Symptôme :** Malgré le succès de la simulation, de nombreux fichiers CSV finaux (`host_utilization.csv`, `qos_violations.csv`, etc.) ne contenaient que l'en-tête de 61 octets.
- **Diagnostic :** 
  - L'événement `STOP_MONITORING` était envoyé dès la seconde `0.0s`. 
  - La cause fondamentale : `SDNDatacenter` déclenchait le statut `WORKLOAD_COMPLETED` à chaque micro-activité (packet, processing) au lieu de le faire seulement pour la requête (Workload) racine. Le compteur `completedWorkloadCount` de `SDNBroker` (cible = 25) explosait instantanément (atteignant parfois 279), coupant brutalement la collecte des logs.

### 2.3 Redondance des données métriques
- **Symptôme :** Lors de l'analyse des logs remplis, une immense récurrence de lignes avec 0% d'utilisation a été remarquée jusqu'à la seconde ~260s.
- **Analyse :** Ce comportement n'est **pas un bug**. La vaste majorité des workloads se terminent dans les 20 premières secondes, mais un flux lourd (100 MB via `app0 -> cache0`) met anormalement longtemps à s'achever à cause de la saturation du réseau. `LogMonitor` prenant des clichés fixes à intervalle régulier (5s) jusqu'à ce que ce dernier flux soit achevé, on retrouve logiquement la trace d'un data center "au repos" total (0%) pendant la longue attente de transmission. Ceci est essentiel pour produire des tracés graphiques cohérents orientés séries temporelles.

### 2.4 "Graphique SLA vide" et "Utilisation Hôte identique MIPS/RAM"
- **Raison SLA :** Le script d'analyse Python calculait initialement un "Pourcentage de SLA". Comme le SLA est évalué sur *chaque paquet* en retard, le simulateur produisait des centaines de violations pour un petit nombre de "Requêtes", entraînant des pourcentages négatifs insensés (ex: -984%) qui sortaient hors du champ (0-100) du graphique. 
- **[MODIFICATION]** Refonte de `consolidated_report.py` pour afficher l'histogramme des valeurs pures ("Total SLA Violations").
- **Raison Hôte Identique :** Les deux politiques (`First` et `BwAllocN`) traitent la même charge CPU (même Workload.csv). Le CPU (MIPS) est impacté par le code source exécuté, pas par la manière dont la donnée réseau transite sur la couche SDN. Il est donc mathématiquement régulier que la moyenne de stress CPU calculée pendante que la VM tourne soit rigoureusement identique. La vraie punition affecte le réseau (les paquets retardés, la latence ou la consommation totale due aux switchs encombrés).

## 3. Modifications Effectuées

### 3.1 `CloudletSchedulerTimeSharedMonitor.java`
- **[MODIFICATION]** Remplacement du `break` par `continue` dans la méthode `updateVmProcessing()`.

### 3.2 `SDNDatacenter.java`
- **[MODIFICATION]** Réintégration de la fonction vitale `req.removeNextActivity()` dans `checkCloudletCompletion()`, permettant d'avancer les requêtes sans causer de blocages.
- **[MODIFICATION]** Remplacement des délais absolus par des temporisations relatives ou des garanties `CloudSim.getMinTimeBetweenEvents()`.

### 3.3 `SDNBroker.java`
- **[MODIFICATION]** Instauration d'un filtre correctif (déduplication) basé sur un Set en Java (`private Set<Long> completedRequestIds = new HashSet<>();`).
- **[MODIFICATION]** L'incrémentation du `completedWorkloadCount` n'est désormais validée que pour les identifiants de requêtes uniques achevées. Dès lors, le signal `STOP_MONITORING` n'est envoyé que lorsque les 25 transferts majeurs sont réellement terminés.

### 3.4 `Configuration.java` & `LogMonitor.java` (Limitation de la redondance temporelle)
- **[MODIFICATION]** Mise à jour de la constante globale `Configuration.monitoringTimeInterval` de `1.0` (ou paramètre par défaut) à `10.0` secondes.
- **[MODIFICATION]** Dans `LogMonitor.java`, la constante codée en dur `MONITOR_INTERVAL = 5;` a été remplacée par une référence dynamique à `Configuration.monitoringTimeInterval`. Cela empêche la collecte excessive à vide tout en maintenant une qualité de surveillance optimale.

## 4. Résultats et Validations
1. **Compilation** : Succès (`recompile.ps1`).
2. **Exécution du script de benchmark** : `compare_priority_link_selection.ps1` s'est exécuté sans s'enliser et a mis correctement fin aux opérations, confirmant l'absence de blocage infini.
3. **Production des CSV** : La réapparition des volumes normaux de logs a officiellement validé la pérennité du fix logique. 
4. **Réduction de la Volumétrie** : Grâce à l'intervalle passé à 10s, la taille du fichier `vm_utilization.csv` a été exactement divisée par deux (de 832 à 416 entrées), optimisant ainsi la consommation disque et mémoire pour les graphiques Python sans perte d'information utile.

## 5. Perspectives
- Re-génération et inspection des graphiques `.png` via Python (`Python-V2/consolidated_report.py`) pour certifier la concordance visuelle des données télémétriques allégées.
- Clôture de l'IT14 et passage à la prochaine étape (IT15).
