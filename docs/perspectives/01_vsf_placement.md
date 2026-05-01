# 🛡️ Perspective 1 : Placement Optimisé de Fonctions de Sécurité Virtuelles (vSF)

## 1. Description Détaillée et Problématique
Contrairement aux VMs classiques de traitement, une **vSF (Virtual Security Function)** comme un IDS ou un Firewall a pour rôle d'analyser le trafic entrant sans nécessairement modifier la charge utile. Elle génère une latence d'inspection ($D_{inspect}$) proportionnelle à la complexité des règles et au débit du flux.
L'enjeu est double : 
- **Géographique** : Placer la vSF sur le chemin le plus court entre les tiers de l'application tout en minimisant la congestion réseau sur les switchs core.
- **Computationnel** : L'inspection consomme des cycles CPU (`MIPS`), ce qui peut impacter d'autres VMs hébergées sur le même hôte.

---

## 2. Architecture et Composantes à Modifier

### A. Modèle de Données (Core)
- **`SDNVm.java`** : 
    - Attr: `double inspectionMipsRequirement` (cycles requis par Mo inspecté).
    - Attr: `SecurityFunctionType type` (FIREWALL, IDS, HONEYPOT).
- **`workload.Workload.java`** :
    - Ajouter un drapeau `requiresSecurityChain` dans l'activité de transmission.

### B. Moteur de Simulation (Datacenter)
- **`SDNDatacenter.java`** :
    - Modifier `processNextActivityTransmission()` pour détecter si le nœud de destination est une vSF.
    - Si c'est le cas, ne pas marquer l'activité comme terminée immédiatement. Créer une sous-activité de traitement interne.

---

## 3. Détails d'Implémentation et Pseudocode

### Logique d'Inspection Dynamique (Java)
```java
// Dans SDNDatacenter.java
protected void processNextActivityTransmission(Request request) {
    SDNVm destVm = request.getNextActivity().getDestination();
    if(destVm.isSecurityFunction()) {
        double trafficSize = request.getNextActivity().getPacketSize();
        double computeTime = trafficSize * destVm.getInspectionOverhead() / destVm.getMips();
        
        // Simuler le délai d'inspection CPU avant de continuer la transmission
        scheduleCloudlet(new SecurityCloudlet(computeTime), destVm);
    }
}
```

### Optimisation par PSO (Adaptation Fitness)
La fonction de fitness dans `PSOAllocation.java` doit être enrichie pour évaluer le "Security Quality of Service" :
$$F = w_1 \cdot E + w_2 \cdot L + w_3 \cdot \frac{1}{S}$$
Où :
- $S$ = Score de proximité (Distance moyenne entre les VMs applicatives et leur vSF attitré).
- $L$ = Latence réseau incluant le délai de traversée de la vSF.

---

## 4. Métriques de Performance et Validation
1. **Inspection Overhead Ratio** : Rapport entre le temps d'inspection et le temps de transmission total.
2. **Resource Contention Alert** : Fréquence à laquelle l'inspection vSF sature le CPU de l'hôte au détriment des VMs applicatives.
3. **Consolidation Impact** : Vérifier si le placement des vSF favorise la consolidation des VMs (MFF) ou nécessite une distribution plus large (LFF) pour éviter les goulots d'étranglement.
