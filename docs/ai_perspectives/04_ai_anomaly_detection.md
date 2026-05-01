# 🔍 Perspective 4 : Détection d'Anomalies par Auto-encodeurs (IA pour Sécurité)

## 1. Apprentissage de la Normalité via l'IA
Un **Auto-encodeur** est un réseau de neurones formé pour compresser et reconstruire les données d'entrée. 
- **Phase d'Entraînement** : On présente au modèle des traces de simulations saines (sans pannes, sans attaques). 
- **Phase de Détection** : Le modèle calcule l'Erreur de Reconstruction ($\text{loss} = \| X - \hat{X} \|^2$). Si la perte dépasse un seuil, le comportement est jugé anormal.

---

## 2. Flux de Données Sécurisé

### A. Extraction de Caractéristiques (Monitoring)
- **`Monitor.java`** : Extrait un vecteur toutes les $n$ secondes :
    - `[cpu_usage, ram_usage, out_packets_rate, in_packets_rate, avg_link_latency]`.

### B. Contrôle Dynamique (NOS)
- Si l'IA détecte une erreur de reconstruction élevée sur une VM spécifique, elle envoie un signal au `NetworkOperatingSystem`.

---

## 3. Implémentation du Loop de Sécurité

### Logique de Détection (Pseudocode Java)
```java
// Dans SecurityMonitor.java
public void auditData(List<Double> telemetry) {
    double anomalyScore = autoencoderModel.getReconstructionError(telemetry);
    
    if (anomalyScore > Configuration.AI_SECURITY_THRESHOLD) {
        // Déclencher le plan de réponse automatique
        SDNController.isolateTraffic(sourceId);
        SDNController.logSecurityIncident(sourceId, anomalyScore);
        
        // Optionnel : Migration vers un hôte "Isolation/Sandbox"
        Scheduler.migrateToSecureHost(sourceId);
    }
}
```

### Automatisation et Zero-Trust
Cette piste s'interface directement avec la **Perspective de Sécurité N°4 (Isolation Dynamique)** pour fermer la boucle : Détection IA $\rightarrow$ Action SDN.

---

## 4. Métriques de Confiance IA
1. **Reconstruction Loss Stability** : Évaluation du bruit de fond du modèle sur des données normales.
2. **Time to Mitigate (AI-driven)** : Temps de réaction entre l'injection d'un "Malicious Workload" et son blocage par l'IA.
3. **True/False Positives (Confusion Matrix)** : Performance globale du modèle face à des attaques variées (DDoS, Exfiltration).
