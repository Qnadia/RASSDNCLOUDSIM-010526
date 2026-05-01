# Analyse Diag Simulation Loop - 2026-03-09

## Problème Identifié : Boucle d'événements et double traitement

L'analyse de `SDNDatacenter.java` et des logs `debug_trace_mini.txt` a révélé une confusion majeure dans la logique de traitement des requêtes (Request/Activity).

### 1. Conflit entre `WORKLOAD_SUBMIT` et `REQUEST_SUBMIT`
Il semble y avoir deux chemins d'entrée pour les requêtes :
- `REQUEST_SUBMIT` : Chemin "standard" CloudSimSDN.
- `WORKLOAD_SUBMIT` : Chemin personnalisé (Nadia) qui semble faire beaucoup de calculs manuels de latence.

### 2. Logique de `processNextActivity` défaillante
La méthode `processNextActivity` dans `SDNDatacenter.java` (lignes 1922-1998) présente plusieurs anomalies graves :
- **Retrait immédiat de l'activité** : Elle appelle `req.removeNextActivity()` dès le début. Si cette méthode est appelée pour "démarrer" une activité, elle la retire de la file avant même qu'elle ne soit réellement traitée par un scheduler ou un canal réseau.
- **Auto-planification récursive** : Elle termine par `send(getId(), totalLatency, CloudSimTagsSDN.REQUEST_COMPLETED, req)`. Comme `REQUEST_COMPLETED` est géré par la même classe en rappelant `processNextActivity`, cela crée une chaîne qui consomme TOUTES les activités d'une requête les unes après les autres avec un simple délai `totalLatency`, sans jamais passer par le `CloudletScheduler` (pour les `Processing`) ou par `NetworkOperatingSystem` (pour les `Transmission`).
- **Calcul de latence incorrect** : Elle utilise `req.getLastProcessingCloudletLen()` (la taille d'un cloudlet) pour calculer une `transmissionDelay` sur un lien réseau, au lieu d'utiliser la taille du paquet (`packetSizeBytes`).

### 3. Effet de bord sur `checkCloudletCompletion`
La méthode `checkCloudletCompletion` est la méthode légitime appelée quand un Cloudlet (activité `Processing`) se termine.
- Elle appelle `processNextActivity(req)` (ligne 1590) pour passer à la suite.
- Mais si `processNextActivity` retire immédiatement l'activité suivante et replanifie un `REQUEST_COMPLETED` sur lui-même, alors les activités de transmission qui devraient suivre sont "simulées" par un simple timer au lieu d'être réellement envoyées sous forme de paquets réseau.

### 4. Cause probable de la boucle infinie (ou très longue)
Si `totalLatency` est calculé à 0 (ce qui arrive si `length` est 0 ou si les liens sont mal configurés), on obtient une récursion immédiate à `T = current_time`. Les logs montrent de nombreux "getNextActivity pour Req ID: 0" à la suite.

## Hypothèse de Correction
1. **Distinguer les types d'activités** dans `processNextActivity`.
2. **Ne pas retirer l'activité** tant qu'elle n'est pas effectivement finie (ou utiliser une variable `currentActivity`).
3. **Casser la boucle `REQUEST_COMPLETED -> processNextActivity`** si elle ne fait que des timers sans action réelle.
4. Harmoniser `WORKLOAD_SUBMIT` et `REQUEST_SUBMIT`.

## Prochaines étapes
- [ ] Vérifier si les cloudlets sont réellement exécutés ou simplement sautés.
- [ ] Corriger `processNextActivity` pour déléguer les `Transmission` au NOS et les `Processing` au Scheduler.
- [ ] S'assurer que `REQUEST_COMPLETED` ne boucle pas avec un délai nul.
