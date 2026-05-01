"""
Genere dataset-small-congested avec ASYMETRIE explicite pour forcer DynLatBw a
faire des choix differents de First/Dijkstra.

Strategie :
  - Chemin via core0 : BW large (300 Mbps) mais SATURE sous charge normale
  - Chemin via core1 : BW etroite (80 Mbps) mais LIBRE
  - Liens edge->host : 100 Mbps (bottleneck commun)
  - Liens slow existants (agg1-edge1 10Mbps, agg0-edge0 100Mbps) : PRESERVES

Avec des VMs consommant 200 Mbps :
  core0 : rho = 200/300 = 0.67  -> D_queue eleve (M/M/1)
  core1 : rho = 200/80 = ???    (core1 moins utilise au debut)

En fait on veut :
  - Tous les paquets optent d'abord pour core0 (BW plus grande = cout initial plus bas)
  - Quand core0 se sature, DynLatBw bascule sur core1
  - First/Dijkstra restent toujours sur core0 -> plus de violations

Topologie actuelle small :
  edge0 (h0,h1) -> agg0 -> core0 -> agg1 -> edge1 (h2,h3)
                          -> core1 -^
                -> agg1 -> edge2 (h4,h5)
"""
import json, shutil, os

SRC  = r"e:\Workspace\v2\cloudsimsdn-research\dataset-small\physical.json"
DST  = r"e:\Workspace\v2\cloudsimsdn-research\dataset-small-congested\physical.json"
os.makedirs(os.path.dirname(DST), exist_ok=True)

with open(SRC, encoding="utf-8") as f:
    data = json.load(f)

# Conserver les BW originales des noeuds (on joue seulement sur les liens)
# Les liens sont identifies par (source, destination) et upBW

# On va remplacer les liens selon la logique suivante :
# Garder uniquement les liens nommés slow tels quels (10 et 100 Mbps asymetriques)
# Redefinir :
#   core0 <-> agg0 : 300 Mbps  (chemin "prefere", se sature)
#   core0 <-> agg1 : 300 Mbps
#   core1 <-> agg0 : 80 Mbps   (chemin alternatif, DynLatBw y bascule)
#   core1 <-> agg1 : 80 Mbps
#   agg0  <-> edge0 (fast) : 200 Mbps
#   agg1  <-> edge1 (fast) : 200 Mbps
#   agg1  <-> edge2        : 150 Mbps
#   edge* <-> h_*          : 100 Mbps (bottleneck commun)

SLOW_LINKS = {
    ("agg1","edge1", 10_000_000),   # slow existant preserve
    ("edge1","agg1", 10_000_000),
    ("agg0","edge0", 100_000_000),  # slow existant preserve
    ("edge0","agg0", 100_000_000),
}

# Map des nouvelles BW por paire (src, dst) -- chemins principaux
NEW_BW = {
    # --- Core0 (chemin prefere, BW raisonnable -> se sature)
    ("core0","agg0"): 300_000_000,
    ("agg0","core0"): 300_000_000,
    ("core0","agg1"): 300_000_000,
    ("agg1","core0"): 300_000_000,
    # --- Core1 (chemin alternatif, BW plus etroite -> libre)
    ("core1","agg0"): 80_000_000,
    ("agg0","core1"): 80_000_000,
    ("core1","agg1"): 80_000_000,
    ("agg1","core1"): 80_000_000,
    # --- Agg -> Edge (fast links)
    ("agg0","edge0"): 200_000_000,
    ("edge0","agg0"): 200_000_000,
    ("agg1","edge1"): 200_000_000,
    ("edge1","agg1"): 200_000_000,
    ("agg1","edge2"): 150_000_000,
    ("edge2","agg1"): 150_000_000,
    # --- Edge -> Host
    ("edge0","h_0"): 100_000_000,
    ("edge0","h_1"): 100_000_000,
    ("edge1","h_2"): 100_000_000,
    ("edge1","h_3"): 100_000_000,
    ("edge2","h_4"): 100_000_000,
    ("edge2","h_5"): 100_000_000,
    ("h_0","edge0"): 100_000_000,
    ("h_1","edge0"): 100_000_000,
    ("h_2","edge1"): 100_000_000,
    ("h_3","edge1"): 100_000_000,
    ("h_4","edge2"): 100_000_000,
    ("h_5","edge2"): 100_000_000,
}

# Patch les noeuds : reduire BW des switches pour forcer la saturation
for node in data["nodes"]:
    t = node.get("type","")
    if t == "core":
        node["bw"] = 600_000_000    # 600 Mbps par core
    elif t == "aggregate":
        node["bw"] = 500_000_000
    elif t == "edge":
        node["bw"] = 300_000_000
    # hosts : inchanges

# Patch les liens
new_links = []
seen_slow = set()
for link in data["links"]:
    src = link.get("source","")
    dst = link.get("destination","")
    orig_bw = link.get("upBW", 0)
    # Liens slow : garder tels quels (10 Mbps et 100 Mbps nommes)
    if orig_bw in (10_000_000, 100_000_000) and (src,dst) in [(s,d) for s,d,_ in SLOW_LINKS]:
        new_links.append(link)
        continue
    # Liens fast redondants (doublons agg<->edge) : on les supprime si deja couverts
    # (la topo originale a des doublons - on garde un seul par paire)
    key = (src, dst)
    if key in NEW_BW:
        link = dict(link)  # copie
        link["upBW"] = NEW_BW[key]
        new_links.append(link)
        del NEW_BW[key]  # eviter les doublons
    # Sinon on ignore (doublons deja traites)

data["links"] = new_links

with open(DST, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=4)

# Rapport
lk_bw = [l["upBW"] for l in data["links"]]
print(f"[OK] dataset-small-congested/physical.json cree avec asymetrie")
print(f"  Links upBW : {sorted(set(lk_bw))}")
print(f"  Total liens: {len(data['links'])}")
print()
print("  Chemins alternatifs (par exemple h_0 -> h_2) :")
print("    Chemin A (core0) : edge0->agg0->core0->agg1->edge1  [300 Mbps core]")
print("    Chemin B (core1) : edge0->agg0->core1->agg1->edge1  [ 80 Mbps core]")
print()
print("  Comportement attendu sous charge :")
print("    First/Dijkstra : toujours chemin A -> saturation -> D_queue explose")
print("    DynLatBw       : bascule sur chemin B quand rho(core0) > 0.7")

# Copier virtual.json et workload.csv
shutil.copy(
    r"e:\Workspace\v2\cloudsimsdn-research\dataset-small\virtual.json",
    r"e:\Workspace\v2\cloudsimsdn-research\dataset-small-congested\virtual.json"
)
shutil.copy(
    r"e:\Workspace\v2\cloudsimsdn-research\dataset-small\workload.csv",
    r"e:\Workspace\v2\cloudsimsdn-research\dataset-small-congested\workload.csv"
)
print("[OK] virtual.json + workload.csv copies")
