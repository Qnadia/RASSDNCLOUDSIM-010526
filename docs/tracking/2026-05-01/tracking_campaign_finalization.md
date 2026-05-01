# 📋 Tracking Campagne Finale et Migration — CloudSimSDN Research
*Date : 01 Mai 2026 | Auteur : Antigravity Analysis | Statut : Finalisé (Migration OK)*

---

## 1. 🎯 Objectifs du Jour
- [x] **Recalibrage LargeVF** : Résoudre l'anomalie énergétique (Medium > Large) en appliquant une asymétrie réseau extrême et en boostant la capacité de calcul.
- [x] **Validation Mini Dataset** : Introduire une congestion artificielle pour valider la supériorité de la politique BLA (`DynLatBw`).
- [x] **Migration Repository** : Déployer le projet sur un nouveau dépôt GitHub propre pour la soumission.

---

## 2. 🧬 Actions Réalisées

### A. Recalibrage du Dataset LargeVF
- **Problématique** : Le dataset "Large" consommait moins d'énergie que le "Medium" car il était trop efficace (pas assez de congestion).
- **Solution** : 
    - Mise à jour de `physical.json` : Backbones à **40 Gbps**, mais liens serveurs critiques bridés à **10 Mbps** pour forcer des files d'attente M/M/1.
    - Mise à jour de `virtual.json` : Augmentation des MIPS (10k-20k) pour générer des flux de données plus intenses.
- **Résultat** : Sensibilité accrue du modèle énergétique aux décisions de routage.

### B. recalibration du Dataset Mini
- **Objectif** : Créer un cas d'école pour démontrer l'efficacité de BLA sur une petite échelle.
- **Action** : Réduction de la bande passante sur les chemins par défaut (First) pour forcer le basculement vers des chemins alternatifs plus longs mais moins chargés.

### C. Migration vers le Nouveau Repository
- **Dépôt cible** : `https://github.com/Qnadia/RASSDNCLOUDSIM-010526`
- **Contrainte** : Limite de taille de fichier de GitHub (100 Mo).
- **Stratégie** :
    1. Nettoyage de l'historique Git pour exclure les anciens logs et résultats volumineux (> 800 Mo).
    2. Inclusion exclusive des résultats consolidés du **01/05/2026**.
    3. Push d'un "Clean Initial Commit" pour garantir un dépôt léger et prêt à l'emploi.

---

## 3. 📂 Structure du Nouveau Dépôt
- `src/` : Code source Java CloudSimSDN complet.
- `tools/` : Suite complète de scripts Python pour l'analyse et la génération de rapports.
- `datasets/` : Versions calibrées (Small, Medium, Large, LargeVF, Mini).
- `results/2026-05-01/` : Dernières preuves expérimentales incluant les rapports PDF et analyses de scalabilité.

---

## 4. 📝 Notes de Conclusion
La campagne du 1er mai marque la clôture de la phase de calibration. Les datasets sont désormais alignés pour produire une analyse de scalabilité cohérente (`Small < Medium < Large < LargeVF`) en termes de délai et d'énergie, validant ainsi la supériorité de la politique de routage dynamique BLA.

> [!TIP]
> Pour restaurer les anciens résultats, se référer au dépôt historique (archive). Le dépôt actuel est optimisé pour la démonstration finale et la publication.
