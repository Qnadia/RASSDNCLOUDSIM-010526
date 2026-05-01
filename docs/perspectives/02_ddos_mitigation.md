# ⚡ Perspective 2 : Résilience du Contrôleur contre les Attaques DDoS

## 1. Description Détaillée du Risque
Dans un environnement SDN, l'attaque **Control Plane Saturation** (ou PacketIn Flooding) consiste à envoyer une multitude de flux dont les en-têtes sont inconnus des switchs. Chaque nouveau flux déclenche une requête au contrôleur (`NetworkOperatingSystem` dans CloudSimSDN), saturant sa file d'attente et son CPU.

L'objectif de cette piste est de doter le `NOS` d'une capacité d'auto-défense capable de distinguer un pic de trafic légitime (ex: burst de requêtes Web) d'une attaque malveillante.

---

## 2. Architecture de Défense (NOS)

### A. Monitoring de Flux (Flow Inspector)
- **`NetworkOperatingSystem.java`** :
    - Implémenter une fenêtre glissante (Time Window) de 100ms.
    - Créer un `Map<Integer, Long> flowIncounter` pour stocker le nombre de requêtes `PacketIn` par ID de VM.

### B. Algorithme de Régulation (Throttle Policy)
- **`BwAllocN.java` / `SelectLinkDynamicLatencyBw.java`** :
    - Avant d'allouer de la bande passante, consulter le statut de la VM source auprès du NOS.

---

## 3. Logicielle et Intégration Code

### Mécanisme de Filtrage (Pseudocode Java)
```java
// Dans NetworkOperatingSystem.java
public void processPacketIn(Packet packet) {
    int sourceId = packet.getPayload().getSrcVmId();
    updateCounter(sourceId);
    
    if (getRate(sourceId) > Configuration.SECURITY_THRESHOLD) {
        // Alerte : Attaque suspectée
        dropPacket(packet);
        quarantineSource(sourceId);
        triggerAlarm("DDoS detected from VM: " + sourceId);
    } else {
        // Comportement normal : installation de la règle de flux
        installFlowRule(packet);
    }
}
```

### Politique d'Atténuation (Quarantaine)
L'isolation ne doit pas être binaire (ON/OFF) pour éviter les faux positifs. On utilise la micro-segmentation :
- Réduire la `allocatedBw` à 1% de la demande pour les VMs en quarantaine.
- Rediriger les flux vers un seul switch edge spécifique pour limiter l'impact sur le Core.

---

## 4. Métriques de Résilience
1. **Controller Response Time under Stress** : Latence du NOS lors du traitement de requêtes légitimes alors qu'une attaque est en cours.
2. **Mitigation Latency** : Temps nécessaire au système pour identifier l'attaquant et appliquer le bridage de bande passante.
3. **Control Overhead** : Charge CPU additionnelle imposée par le monitoring des compteurs de flux sur le contrôleur.
