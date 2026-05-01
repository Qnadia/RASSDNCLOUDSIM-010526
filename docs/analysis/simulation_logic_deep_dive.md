# 🧠 Deep Dive : Logique de Simulation et Calcul des Résultats

Ce document détaille le fonctionnement interne du simulateur CloudSimSDN, le workflow d'exécution et les formules mathématiques utilisées pour générer les fichiers de sortie, avec les références au code source.

---

## 1. Workflow de la Simulation

### A. Phase d'Initialisation
**Classe :** `org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth`
- **Action** : Configure l'environnement et instancie le modèle énergétique.
- **Code Clé** :
```java
EnhancedHostEnergyModel hostModel = new EnhancedHostEnergyModel(25, 1.2, 0.8, 0.5, 30);
LogMonitor logMonitor = new LogMonitor("LogMonitor", nos);
```

### B. Phase d'Exécution (Cœur CloudSim)
**Classes :** `NetworkOperatingSystem`, `VmAllocationPolicy`, `LinkSelectionPolicy`
- **Placement** : Décidé par les classes dans `org.cloudbus.cloudsim.sdn.policies.vmallocation`.
- **Routage** : Géré par `LinkSelectionPolicyDynamicLatencyBw` (ou autre).

### C. Phase de Monitoring
**Classe :** `org.cloudbus.cloudsim.sdn.example.LogMonitor`
- **Action** : S'exécute toutes les 10 secondes (défini dans `Configuration.monitoringTimeInterval`).
- **Code Clé** :
```java
public void processEvent(SimEvent ev) {
    case CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION:
        collectAllMetrics(); // Récupère CPU, RAM, BW, Énergie
}
```

---

## 2. Logique de Calcul des Résultats

### ⚡ Énergie (`host_energy_total.csv` & `detailed_energy.csv`)
**Classes principales :** `EnhancedHostEnergyModel.java` et `PowerUtilizationMonitor.java`

#### A. Le Modèle : `EnhancedHostEnergyModel.java` (La Formule)
C'est ici que la physique du serveur est définie. La puissance instantanée ($P$) est la somme des consommations de chaque composant :
```java
double power = idleWatt
             + (wattPerCpuUtil * cpuUtil / 100.0)
             + (wattPerRamUtil * ramUtil / 100.0)
             + (wattPerBwUtil  * bwUtil  / 100.0);
```

#### B. Le Comptable : `PowerUtilizationMonitor.java` (L'Intégration)
Cette classe est responsable de l'accumulation de l'énergie au fil du temps. Elle fonctionne par **intégration discrète** :

1.  **Mesure de la durée** : `duration = currentTime - previousTime;`
2.  **Calcul de l'énergie sur l'intervalle** : $E_{intervalle} = \frac{P \times duration}{3600}$
3.  **Accumulation** : `totalEnergy += E_intervalle;`
4.  **Mise à jour de l'horloge interne** : `previousTime = currentTime;`

**Pourquoi c'est important ?**
Ce mécanisme garantit que même si l'intervalle entre deux mesures change (ex: 10s, puis 2s lors de l'arrêt final), le calcul de l'énergie reste mathématiquement exact car il est toujours multiplié par la durée réelle écoulée.

#### C. Détail des Paramètres Physiques
Ces valeurs (définies dans votre fichier `physical.json`) simulent un serveur réel :
*   **Idle Watt (25W)** : C'est la consommation de base du serveur dès qu'il est allumé, même si aucune VM ne tourne. C'est le "coût fixe" électrique.
*   **CPU Factor (1.2W/%)** : Pour chaque 1% de charge processeur, le serveur consomme 1.2 Watts supplémentaires. C'est le facteur le plus sensible.
*   **RAM Factor (0.8W/%)** : Consommation liée à l'accès mémoire. Plus les VMs utilisent de RAM, plus ce facteur pèse.
*   **BW Factor (0.5W/%)** : Consommation des cartes réseau (NIC). Chaque % de bande passante utilisée génère de la chaleur et consomme de l'énergie.

#### 📝 Exemple de calcul illustratif
Imaginons un serveur avec la charge suivante pendant **10 secondes** :
*   **CPU** : 50%
*   **RAM** : 20%
*   **BW** : 10%

**1. Calcul de la Puissance Instantanée ($P$) :**
$$P = 25W + (1.2 \times 50) + (0.8 \times 20) + (0.5 \times 10)$$
$$P = 25W + 60W + 16W + 5W = \mathbf{106 \text{ Watts}}$$

**2. Calcul de l'Énergie pour cet intervalle de 10s ($E$) :**
Comme l'énergie est en Watt-heures (Wh), on divise par 3600 (secondes dans une heure) :
$$E = \frac{106 \text{ W} \times 10 \text{ s}}{3600} = \mathbf{0.2944 \text{ Wh}}$$

**Résultat** : Dans votre fichier `host_energy.csv`, vous verriez une ligne avec la valeur **0.2944** pour cet intervalle. Le `PowerUtilizationMonitor` ajoutera ce chiffre au grand total final.

### 📉 Qualité de Service (SLA)
**Classes :** `QoSMonitor.java` et `Configuration.java`

#### A. La Règle de Décision
Une violation SLA n'est pas simplement déclarée dès qu'il y a un retard. Le système applique un **facteur de grâce** pour tolérer de légères fluctuations réseau.

**Formule mathématique :**
$$Violation = ActualDelay > (ExpectedDelay \times GraceFactor)$$

#### B. Paramètres actuels
*   **Grace Factor (`DECIDE_SLA_VIOLATION_GRACE_ERROR`)** : **1.30**
    *   Cela signifie qu'une "marge d'erreur" de **30%** est accordée. 
    *   *Exemple* : Si une requête a une deadline de 100ms, elle ne sera marquée en "SLA_VIOLATION" que si elle met plus de **130ms** à arriver.

#### 📝 Pourquoi ce facteur ?
En recherche sur les réseaux SDN, on considère souvent que de micro-délais sont acceptables. Un facteur de 1.3 est assez généreux et permet de filtrer le "bruit" pour ne se concentrer que sur les congestions sévères.

### ⏱️ Latence et Délais (`packet_delays.csv`)
**Classes :** `org.cloudbus.cloudsim.sdn.physicalcomponents.Link`

#### A. Le Calcul
Le délai total d'un paquet est la somme du délai de propagation (physique) et du délai de transmission (lié à la bande passante).

**Code Source (`Link.java`) :**
```java
public double addPacket(Node src, Node dst, double size, Flow flow) {
    double transmissionDelay = size / bandwidth;
    double totalDelay = latency + transmissionDelay; 
    return totalDelay;
}
```
}
```

### ❌ Violations SLA (`qos_violations.csv`)
**Classe :** `org.cloudbus.cloudsim.sdn.qos.QoSMonitor`
- **Logique** : Compare le temps de réponse réel au temps "idéal" (sans congestion).
- **Code Source (`QoSMonitor.java`)** :
```java
if (actualTime > expectedTime * Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR) {
    recordViolation(flowId, "LATENCY");
}
```

---

## 3. Système de Sortie (Logging)

### Le Scribe : `LogManager.java`
- Gère les buffers mémoire pour éviter ralentir la simulation par des écritures disque trop fréquentes.
- **Fichier Clé** : `src/main/java/org/cloudbus/cloudsim/sdn/example/LogManager.java`

### L'Export Final
- Lorsque tous les workloads sont terminés, `LogMonitor` reçoit l'événement `STOP_MONITORING`.
- Il appelle `LogManager.flushAll()` qui vide les buffers dans les fichiers `.csv`.

---

## 4. Répertoire des Fichiers vs Classes

| Fichier CSV | Classe Responsable | Méthode Clé |
| :--- | :--- | :--- |
| `host_energy_total.csv` | `LogMonitor` | `processEvent(STOP_MONITORING)` |
| `detailed_energy.csv` | `PowerUtilizationMonitor` | `addPowerConsumption()` |
| `packet_delays.csv` | `QoSMonitor` | `addPacketDelay()` |
| `qos_violations.csv` | `QoSMonitor` | `recordViolation()` |
| `host_utilization.csv` | `LogMonitor` | `monitorHostResources()` |

---

## 5. Guide d'Interprétation Rapide

1.  **Vérifier la validité de l'énergie** : Regarder `detailed_energy.csv` pour voir si un hôte consomme environ `idleWatt` quand il est vide.
2.  **Identifier un goulot d'étranglement** : Si `packet_delays.csv` montre des valeurs très supérieures à la `latency` du lien dans `physical.json`, c'est que le réseau est saturé.
3.  **Analyser l'impact de DynLatBw** : Comparer `qos_violations.csv` entre `First` et `DynLatBw`. Moins de violations = meilleur routage dynamique.
