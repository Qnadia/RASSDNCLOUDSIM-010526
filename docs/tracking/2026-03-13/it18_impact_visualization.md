# Itération 18 : Visualisation Haute Fidélité de l'Impact SDN

**Date :** 2026-03-13  
**Objectif :** Générer des graphiques percutants démontrant la supériorité de la politique `BwAllocN` sur `First` à travers l'énergie, la latence et la dispersion des délais de paquets.

---

## 📈 Objectifs de l'Itération

1. [ ] Création du script `impact_analysis_plots.py` pour une analyse granulaire.
2. [ ] Génération d'un **Histogramme de Latence E2E** comparatif.
3. [ ] Génération de **Pie Charts de Consommation Énergétique**.
4. [ ] Génération d'un **Box Plot de Dispersion** des délais paquets.

---

## 🛠️ Plan de Visualisation

### 1. Histogramme de Latence E2E
- **X** : Politique de VM (LFF, MFF, LWFF).
- **Couleur** : Politique de lien (BwAllocN vs First).
- **Y** : Latence moyenne (s).

### 2. Pie Charts Énergétiques
- Comparer la part d'énergie consommée par simulation entre `First` (longue durée) et `BwAllocN` (courte durée).
- Visualiser le gain énergétique net.

### 3. Box Plots (Délai Paquet)
- Utilisation des données brutes de `packet_delays.csv`.
- Montrer l'instabilité et la lenteur extrême de la politique `First` due à la saturation du lien 10 Mbps.

---

## ✅ État d'Avancement
- [x] Approbation du plan d'implémentation.
- [x] Implémentation du script Python.
- [x] Revue des graphiques générés.
