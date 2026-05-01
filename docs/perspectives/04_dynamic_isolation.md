# 🧊 Perspective 4 : Micro-segmentation et Isolation Dynamique (Zero-Trust)

## 1. Vision Stratégique : Zero-Trust
L'approche **Zero-Trust** stipule qu'aucune VM ne doit être considérée comme sûre par défaut. Cette perspective propose un mécanisme d'isolation dynamique réagissant aux événements de simulation en temps réel. Si le `Monitor` détecte une anomalie (latence suspecte, saturation inhabituelle), le réseau doit être reconfiguré immédiatement.

---

## 2. Architecture de Micro-segmentation

### A. Surveillance Temps-Réel (Security Monitor)
- **`monitoring.QoSMonitor.java`** :
    - Étendre pour surveiller le trafic inter-VM (East-West Traffic).
    - Déclencheur : `onAnomalyDetected(int vmId)`.

### B. Contrôleur Réactif (NOS)
- **`NetworkOperatingSystem.java`** :
    - `public void executeIsolation(int compromisedVmId)` : Méthode qui injecte de nouvelles règles de flux prioritaires pour bloquer ou limiter tout le trafic sortant de cette VM.

---

## 3. Processus Technique d'Isolation

### Workflow de réponse (Pseudocode Java)
```java
// Dans NetworkOperatingSystem.java
public void updateFlowRulesOnSecurityEvent(int vmId) {
    // 1. Identifier tous les canaux actifs liés à la VM compromise
    List<Channel> channels = findChannelsByVM(vmId);
    
    for (Channel chan : channels) {
        // 2. Appliquer la politique de restriction (Quarantaine)
        chan.setAllocatedBw(0.0001); // Presque zéro (isolation logique)
        chan.setUpdateStatus(Status.ISOLATED);
        
        // 3. Re-router vers un nœud de Honeypot si nécessaire
        Link alternativePath = securityPolicy.getSafePath(chan.getSource());
        rerouteFlow(chan, alternativePath);
    }
    
    // 4. Libérer les ressources sur les switchs core pour les flux sains
    flushFlowTablesOnCoreSwitches();
}
```

### Mécanisme SDN (OpenFlow-like)
Simuler l'envoi d'un message `OF_FLOW_MOD` qui remplace les routes par défaut par des routes de contournement passant par une VM d'analyse.

---

## 4. Métriques d'Efficacité
1. **Containment Duration** : Temps simulé entre la détection et l'application complète de l'isolation sur tous les switchs du chemin.
2. **Impact on Good Traffic** : Latence subie par les flux sains colocalisés sur les mêmes switchs que le flux isolé.
3. **Recovery Rate** : Capacité du système à restaurer les routes normales après une levée d'alerte.
