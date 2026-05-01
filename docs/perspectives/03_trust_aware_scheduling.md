# 🤝 Perspective 3 : Ordonnancement Basé sur la Confiance (Trust-Aware)

## 1. Description du Concept de Confiance
Dans un Cloud multi-tenants, la cohabitation de VMs de différents utilisateurs sur un même hôte physique présente des risques de canaux auxiliaires (Side-channel attacks). Cette perspective propose un ordonnancement qui respecte des contraintes de sécurité basées sur un score de confiance de l'hôte (`TrustScore`).

Un hôte peut avoir un score élevé s'il utilise des technologies de confiance (ex: Intel SGX, AMD SEV) ou s'il est situé dans une zone géographique certifiée.

---

## 2. Implémentation du Modèle de Confiance

### A. Attributs de Sécurité (Extensions Java)
- **`SDNHost.java`** :
    - `double trustFactor` : Valeur entre 0.0 et 1.0 calculée lors de l'initialisation.
    - `List<String> securityCapabilities` : Liste de features de sécurité (TEE, FIREWALL_HW, etc.).
- **`SDNVm.java`** :
    - `double minTrustRequired` : Seuil de sécurité acceptable pour l'utilisateur.

### B. Algorithmes de Placement (Policies)
Modifier le processus de décision de placement dans les politiques existantes (`LFF`, `MFF`, `LWFF`, `PSO`) pour intégrer un filtre de sécurité prioritaire.

---

## 3. Détails de l'Algorithme de Filtrage

### Logique de Sélection (Pseudocode Java)
```java
// Dans VmAllocationPolicy.java (ou classe parente SDN)
public Host findHostForVm(Vm vm) {
    List<Host> candidates = getHostsSortedByPolicy(); // LFF ou MFF
    
    for (Host host : candidates) {
        // Filtre de Sécurité : PRIORITÉ ABSOLUE
        if (host.getTrustFactor() < vm.getSecurityRequirement()) {
            continue; // Rejet immédiat de l'hôte non-conforme
        }
        
        // Filtre de Ressources : CPU / RAM / BW
        if (isHostResourcesAvailable(host, vm)) {
            return host;
        }
    }
    return null; // Aucun hôte ne satisfait à la fois la Sécurité et les Ressources
}
```

### Scénario de Test (Dataset JSON)
Modifier `energy-physical.json` pour inclure des attributs de sécurité :
```json
{
  "name": "host0",
  "type": "host",
  "trustLevel": 0.9,
  "mips": 40000,
  "bw": 10000000000
}
```

---

## 4. Métriques de Robustesse
1. **Security-Resource Conflict Ratio** : Nombre de fois où un placement optimal en termes d'énergie (MFF) est rejeté pour des raisons de sécurité.
2. **Mean Trust of Infrastructure** : Score de confiance moyen de l'ensemble des hôtes actifs.
3. **Tenant Isolation Score** : Indicateur calculé sur le nombre d'utilisateurs distincts partageant le même hôte physique.
