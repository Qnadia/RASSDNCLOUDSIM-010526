# Analyse Comparative : Politiques d'Allocation de Liens (First vs BwAllocN)

## 1. Différence de logique de routage

Dans la topologie Fat-Tree simulée, il existe de multiples chemins (ou liens parallèles) entre deux nœuds d'un niveau au niveau supérieur. Le choix du lien réseau à emprunter est crucial.

### Politique `First` (LinkSelectionPolicyFirst.java)
- **Logique** : Statique et "Aveugle".
- **Comment ça marche** : L'algorithme cherche le chemin le plus court (en nombre de sauts) via un simple parcours en largeur (BFS). Lorsqu'il doit choisir entre plusieurs liens parallèles reliant deux switchs, il prend systématiquement le premier de la liste (`links.get(0)`).
- **Problème** : Cette politique ne tient **aucun compte** de la charge actuelle du réseau, de la bande passante disponible ou de la latence. Elle va surcharger certains liens (créant des goulots d'étranglement) tout en laissant d'autres chemins alternatifs totalement inoccupés.

### Politique `BwAllocN` (LinkSelectionPolicyBandwidthAllocationN.java)
- **Logique** : Dynamique et "Sensible à la charge" (Load-aware).
- **Comment ça marche** : L'algorithme cherche également le chemin, mais lorsqu'il a le choix entre plusieurs liens parallèles, il interroge chaque lien pour connaître sa **bande passante libre restante** (`getFreeBandwidth(prevNode)`). Il sélectionne alors le lien disposant de la capacité libre **la plus élevée** (`maxBw`).
- **Avantage** : Cette politique effectue un équilibrage de charge dynamique (Load Balancing). Les flux réseau sont intelligemment répartis sur l'ensemble de la topologie Fat-Tree, évitant la congestion et minimisant la latence perçue par l'application.

*Note : La politique `BwAlloc` classique (sans le N) compte seulement le nombre de canaux/flux, tandis que `BwAllocN` vérifie précisément la bande passante disponible en Mbps/Gbps, ce qui est beaucoup plus fin.*

---

## 2. Alignement avec les besoins de votre Article

**OUI, cet axe de comparaison est parfaitement aligné avec votre article !** 
Le document `SCENARIOS_DESCRIPTION.md` indique clairement que l'objectif de votre papier est de :
> *"démontrer les avantages d'une politique sensible à la bande passante et à la latence (DynLatBw / BwAllocN) par rapport à une politique de sélection statique (First) qui choisit systématiquement le premier lien disponible sans considérer l'état du réseau."*

Comparer `First` et `BwAllocN` constitue précisément le cœur de votre démonstration scientifique. Cela permet de quantifier :
1. L'amélioration de l'utilisation globale du réseau (éviter les liens saturés / liens vides vus avec `First`).
2. La diminution des violations de QoS et des délais de paquets grâce au Load Balancing de `BwAllocN`.
3. L'impact potentiel sur la consommation énergétique des commutateurs (switches).

---

## 3. Recommandations pour débuter les tests

### Par quelle politique commencer ?
Vous devez **absolument commencer par la politique `First`**. 
- **Raison** : En recherche scientifique, il faut toujours établir une **Baseline** (ligne de base). Exécuter `First` vous donnera les métriques de référence d'un réseau "naïf" et congestionné. 
- Ensuite, vous exécuterez `BwAllocN` (et plus tard `DynLatBw`), et vous pourrez mesurer mathématiquement le gain (ex: "BwAllocN a réduit la latence moyenne de X% par rapport à First").

### Par quel scénario commencer ?
Je vous recommande de commencer par le **`dataset-small`**.
- **Pourquoi ?** : Il contient 6 hôtes physiques, 13 nœuds au total, et un workload de 100 requêtes. Il est suffisant pour générer des communications inter-switches avec le Fat-Tree, mais sa petite taille garantit que la simulation tournera en **moins de 2 minutes**.
- **Avantage** : Cela vous permet de valider de bout en bout votre chaîne d'expérimentation (exécution rapide -> vérification des CSV générés -> création des graphes). Une fois que le comparatif `First` vs `BwAllocN` fonctionne et donne des graphes cohérents sur `small`, vous pourrez lancer avec confiance les long scénarios (`medium` puis `large`) pour générer les résultats définitifs de votre article.
