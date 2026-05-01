# 📉 Perspective 2 : Équilibrage de Charge Prédictif via LSTM

## 1. Analyse Prédictive des Séries Temporelles
Traditionnellement, l'équilibrage de charge dans CloudSimSDN est réactif (on réagit à la congestion actuelle). L'objectif ici est d'utiliser un modèle **LSTM (Long Short-Term Memory)** pour prédire la charge réseau à $T+n$ à partir de l'historique récent $[T-k, T]$.

Cela permet de déclencher des **proactive migrations** ou d'ajuster la $B_{alloc}$ avant que le paquet de données ne soit bloqué dans une file d'attente.

---

## 2. Pipeline de Données et Intégration

### A. Collecte (Logs)
- **`LogManager.java`** : Exporter les séries temporelles d'utilisation des liens réseau (`link_utilization.csv`) à intervalle régulier.
- **Feature Engineering** : Utiliser la charge CPU des VMs sources et la taille moyenne des paquets comme variables exogènes.

### B. Le Contrôleur Prédictif (NOS)
- **`NetworkOperatingSystem.java`** :
    - Héberger une instance de `PredictorService`.
    - `updateFlowsProactively()` : Méthode appelée toutes les 1s (temps sim).

---

## 3. Détails d'Implémentation Technique

### Logique de Proactivité (Pseudocode Java)
```java
// Dans NetworkOperatingSystem.java
public void checkPredictions() {
    for (Link link : physicalTopology.getAllLinks()) {
        double currentLoad = link.getUtilization();
        double predictedLoad = lstmService.getPrediction(link.getId(), 5.0); // Prédiction à 5s
        
        if (predictedLoad > Configuration.CONGESTION_THRESHOLD) {
            // Anticipation : Rééquilibrer les flux via des chemins alternatifs
            RerouteManager.triggerProactiveReroute(link); 
            log("Proactive action taken for link " + link.getName());
        }
    }
}
```

### Modélisation LSTM (Python/Keras)
Le modèle est entraîné sur les logs des simulations précédentes (Scenario Medium) pour apprendre les cycles de charge des applications multi-tiers.

---

## 4. Métriques d'Impact
1. **Anticipation Accuracy** : Mesure de l'erreur entre la charge prédite et la charge réellement observée.
2. **Congestion Avoidance Rate** : Réduction du nombre de paquets tombant dans des liens en saturation.
3. **Stability Index** : Nombre de ré-ordonnancements "inutiles" déclenchés par de fausses alertes de l'IA.
