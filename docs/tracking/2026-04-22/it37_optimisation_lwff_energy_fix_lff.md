# Itération 37 : Détails Techniques de l'Optimisation LWFF et Correction du Simulateur

**Date** : 22 Avril 2026  
**Auteur** : Antigravity (Pair Programming)  
**Contexte** : Calibration de la politique LWFF sur le dataset `small` et correction des biais de calcul énergétique et d'allocation.

---

## 1. Détail des Modifications de Code

### A. Correction du calcul de la charge (LWFF)
**Fichier** : `src/main/java/org/cloudbus/cloudsim/sdn/policies/vmallocation/VmAllocationPolicyLWFFF.java`

**Problématique** : La politique dispersait les VMs car elle était trop sensible à l'utilisation CPU/RAM instantanée des gros serveurs.  
**Solution** : Augmenter radicalement le poids de la puissance brute de l'hôte (`wWorkload`) pour forcer la consolidation.

```java
// AVANT
double wWorkload = 0.7;
double wCpu = 0.1;
double wRam = 0.1;
double wBw = 0.1;

// APRÈS (Ligne 60+)
double wWorkload = 0.97; // Priorité à la capacité totale (Consolidation)
double wCpu = 0.01;      // Sensibilité minimale à la charge instantanée
double wRam = 0.01;
double wBw = 0.01;
```

### B. Gestion de l'Énergie et Extinction (Power Management)
**Fichier** : `src/main/java/org/cloudbus/cloudsim/sdn/physicalcomponents/SDNHost.java`  
**Fichier associé** : `src/main/java/org/cloudbus/cloudsim/sdn/monitor/power/EnhancedHostEnergyModel.java`

**Problématique** : Les hôtes vides consommaient ~13.6 Wh car ils ne s'éteignaient qu'après 1h d'inactivité (`powerOffDuration = 3600`).  
**Solution** : Passer le délai d'extinction à 0 pour une mise hors tension immédiate dès que l'hôte est détecté vide entre deux cycles de monitoring.

```java
// SDNHost.java (Ligne 76)
EnhancedHostEnergyModel hostModel = new EnhancedHostEnergyModel(
    120, 1.54, 0.50, 0.10, 
    0 // powerOffDuration passée de 3600 à 0
);
```

### C. Correction du Bug de l'Hétérogénéité (Classe de Base)
**Fichier** : `src/main/java/org/cloudbus/cloudsim/sdn/policies/vmallocation/VmAllocationPolicyCombinedMostFullFirst.java`

**Problématique** : Le simulateur calculait les pourcentages de ressources libres en utilisant la capacité du **premier hôte** pour toute la flotte.  
**Correction** : Suppression des constantes globales et utilisation des méthodes `host.getTotalMips()` et `host.getBw()` dans les boucles d'allocation.

```java
// AVANT
double mipsFreePercent = (double)getFreeMips().get(i) / this.hostTotalMips; 

// APRÈS (Boucle d'allocation)
Host host = getHostList().get(i);
double mipsFreePercent = (double)getFreeMips().get(i) / host.getTotalMips(); 
```
*Note : Cette correction a été répercutée sur toutes les classes dérivées (LFF, MFF, Ex).*

---

## 2. Analyse des Preuves (Logs)

### Diagnostic du Bug LFF
Avant la correction, les logs montraient que `LFF` choisissait l'Host 0 (64k) même après que celui-ci ait reçu plusieurs VMs, ignorant les Host 1, 2, 4, 5 (32k) pourtant vides.
- **Cause** : Un Host 1 vide (32k) était vu comme "rempli à 50%" (`32/64`) alors qu'il était à 0% d'utilisation réelle.

### Validation de la Consolidation LWFF
Logs du run final (21:06) :
```text
VM web0 (ID: 0) is allocated to Host 0
VM app0 (ID: 2) is allocated to Host 0
VM db0 (ID: 4) is allocated to Host 3
...
```
Tous les identifiants de placement pointent vers `Host 0` (64k) ou `Host 3` (64k).

---

## 3. Synthèse des Résultats Finaux (Dataset Small)

| Métrique | LFF (Spread corrigé) | LWFF (Consolidé) | Delta (%) |
| :--- | :--- | :--- | :--- |
| **Placement** | 6 hôtes actifs | 2 hôtes actifs | **-66% d'infra** |
| **Consommation** | **614.63 Wh** | **273.08 Wh** | **-55.5% d'énergie** |
| **Durée Simulation** | 3091 s | 4093 s | +32.4% temps |

**Analyse du compromis** :  
Le ralentissement de 32% en mode `LWFF` s'explique par la politique `VmSchedulerTimeSharedEnergy`. Les VMs sur un même hôte se partagent la puissance totale. En `LFF`, une VM seule récupère "le bonus" de puissance de l'hôte, d'où une exécution plus rapide mais un gaspillage énergétique massif (614 Wh contre 273 Wh).

---

## 4. Statut du Simulateur
Le simulateur est désormais :
1. **Juste** : Les politiques respectent l'hétérogénéité des hôtes.
2. **Réaliste** : L'extinction des serveurs inactifs est modélisée.
3. **Optimisé** : La politique LWFF remplit son rôle de consolidateur efficace.

---
**Statut** : ✅ **APPROUVÉ ET VALIDÉ**
