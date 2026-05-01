#!/usr/bin/env python3
"""
create_calibrated_dataset.py
============================
Génère le dataset-calibrated pour révéler l'avantage de DynLatBw vs First.

Stratégie scientifique :
  1. Topologie physique avec hétérogénéité forcée (liens "goulot" et liens "rapides")
  2. VMs avec bande passante élevée pour saturer les liens goulots
  3. Workload concentré (hotspots) pour créer de la congestion
  4. len_cloudlet faible pour ne pas bloquer le CPU (focus sur réseau)

Topologie (3-tier Fat-Tree simplifié, 2 core, 4 agg, 4 edge, 12 hosts) :
  - core0, core1 (commutateurs cœur : 10 Gbps)
  - agg0..agg3   (commutateurs agrégation : 5 Gbps)
  - edge0..edge3 (commutateurs accès : variable selon hétérogénéité)
  - h_0..h_11    (serveurs hétérogènes)

Hétérogénéité réseau intentionnelle :
  - core → agg : liens rapides (5 Gbps) vs lents (500 Mbps)
    → First choisira le 1er lien dans la liste (potentiellement lent)
    → DynLatBw évitera les liens lents via scoring M/M/1

Author: IT-38 Nadia
"""

import json
import csv
import os
import random

random.seed(42)

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

# ─────────────────────────────────────────────────────────────────────────────
# 1. PHYSICAL.JSON — Topologie physique hétérogène
# ─────────────────────────────────────────────────────────────────────────────

def make_physical():
    """
    Crée une topologie 3-tier avec hétérogénéité volontaire.
    
    Hétérogénéité clé :
      - edge0 et edge1 → accès LENT (500 Mbps, latence 2ms, 5km)
        Ces edges servent app0/app1 et db0/db1 (VMs hotspot)
        First prendra ces chemins par défaut (premiers dans la liste)
      - edge2 et edge3 → accès RAPIDE (5 Gbps, latence 0.05ms, 200m)
        DynLatBw pourra les détecter et les préférer
      - agg0 connecté à core0 (lent, 500 Mbps) et core1 (rapide, 5 Gbps)
        → Force DynLatBw à choisir le chemin via core1
    """
    nodes = [
        # Hôtes hétérogènes (3 niveaux de puissance)
        {"ram": 131072, "pes": 16, "name": "h_0",  "bw": 20000000000, "mips": 64000, "type": "host", "storage": 10000000},
        {"ram":  65536, "pes": 16, "name": "h_1",  "bw": 10000000000, "mips": 32000, "type": "host", "storage": 10000000},
        {"ram":  32768, "pes": 16, "name": "h_2",  "bw":  5000000000, "mips": 16000, "type": "host", "storage": 10000000},
        {"ram": 131072, "pes": 16, "name": "h_3",  "bw": 20000000000, "mips": 64000, "type": "host", "storage": 10000000},
        {"ram":  65536, "pes": 16, "name": "h_4",  "bw": 10000000000, "mips": 32000, "type": "host", "storage": 10000000},
        {"ram":  32768, "pes": 16, "name": "h_5",  "bw":  5000000000, "mips": 16000, "type": "host", "storage": 10000000},
        {"ram": 131072, "pes": 16, "name": "h_6",  "bw": 20000000000, "mips": 64000, "type": "host", "storage": 10000000},
        {"ram":  65536, "pes": 16, "name": "h_7",  "bw": 10000000000, "mips": 32000, "type": "host", "storage": 10000000},
        {"ram":  32768, "pes": 16, "name": "h_8",  "bw":  5000000000, "mips": 16000, "type": "host", "storage": 10000000},
        {"ram": 131072, "pes": 16, "name": "h_9",  "bw": 20000000000, "mips": 64000, "type": "host", "storage": 10000000},
        {"ram":  65536, "pes": 16, "name": "h_10", "bw": 10000000000, "mips": 32000, "type": "host", "storage": 10000000},
        {"ram":  32768, "pes": 16, "name": "h_11", "bw":  5000000000, "mips": 16000, "type": "host", "storage": 10000000},

        # Commutateurs cœur
        {"iops": 1000000000, "name": "core0", "bw": 10000000000, "type": "core"},
        {"iops": 1000000000, "name": "core1", "bw": 10000000000, "type": "core"},

        # Commutateurs agrégation
        {"iops": 1000000000, "name": "agg0", "bw": 5000000000, "type": "aggregate"},
        {"iops": 1000000000, "name": "agg1", "bw": 5000000000, "type": "aggregate"},
        {"iops": 1000000000, "name": "agg2", "bw": 5000000000, "type": "aggregate"},
        {"iops": 1000000000, "name": "agg3", "bw": 5000000000, "type": "aggregate"},

        # Commutateurs accès : edge0/1 LENTS (goulots), edge2/3 RAPIDES
        {"iops": 1000000000, "name": "edge0", "bw":  500000000, "type": "edge"},   # ← GOULOT 500 Mbps
        {"iops": 1000000000, "name": "edge1", "bw":  500000000, "type": "edge"},   # ← GOULOT 500 Mbps
        {"iops": 1000000000, "name": "edge2", "bw": 5000000000, "type": "edge"},   # ← RAPIDE 5 Gbps
        {"iops": 1000000000, "name": "edge3", "bw": 5000000000, "type": "edge"},   # ← RAPIDE 5 Gbps
    ]

    links = []

    def add_link(src, dst, bw, latency_ms, distance_m, n=1.468):
        """Ajoute un lien bidirectionnel."""
        for s, d in [(src, dst), (dst, src)]:
            links.append({
                "source": s, "destination": d,
                "upBW": bw,
                "latency": latency_ms,
                "distance": distance_m,
                "refractiveIndex": n
            })

    # ── Core → Agg : hétérogène (clé pour différencier First vs DynLatBw) ──
    # core0 est lié à agg0/agg1 avec des liens LENTS (goulots intentionnels)
    add_link("core0", "agg0",  500000000, 2.0, 5000)   # ← LENT : 500 Mbps, 2ms, 5km
    add_link("core0", "agg1",  500000000, 2.0, 5000)   # ← LENT : 500 Mbps, 2ms, 5km
    add_link("core0", "agg2", 5000000000, 0.05, 200)   # ← RAPIDE : 5 Gbps
    add_link("core0", "agg3", 5000000000, 0.05, 200)   # ← RAPIDE : 5 Gbps

    # core1 est lié à tous les agg avec des liens RAPIDES (chemin alternatif)
    add_link("core1", "agg0", 5000000000, 0.05, 200)   # ← RAPIDE via core1
    add_link("core1", "agg1", 5000000000, 0.05, 200)   # ← RAPIDE via core1
    add_link("core1", "agg2", 5000000000, 0.05, 200)
    add_link("core1", "agg3", 5000000000, 0.05, 200)

    # ── Agg → Edge : edge0/1 lents (goulots), edge2/3 rapides ──
    add_link("agg0", "edge0",  500000000, 1.0, 3000)   # ← LENT : 500 Mbps
    add_link("agg0", "edge2", 5000000000, 0.05, 200)   # ← RAPIDE alternatif
    add_link("agg1", "edge1",  500000000, 1.0, 3000)   # ← LENT : 500 Mbps
    add_link("agg1", "edge3", 5000000000, 0.05, 200)   # ← RAPIDE alternatif
    add_link("agg2", "edge0", 5000000000, 0.05, 200)   # ← RAPIDE (chemin DynLatBw)
    add_link("agg2", "edge2", 5000000000, 0.05, 200)
    add_link("agg3", "edge1", 5000000000, 0.05, 200)   # ← RAPIDE (chemin DynLatBw)
    add_link("agg3", "edge3", 5000000000, 0.05, 200)

    # ── Edge → Hosts ──
    # edge0 (lent) sert h_0, h_1, h_2 (VMs hotspot web0-4, app0-2)
    add_link("edge0", "h_0",  500000000, 0.5, 100)     # ← GOULOT intentionnel
    add_link("edge0", "h_1",  500000000, 0.5, 100)     # ← GOULOT intentionnel
    add_link("edge0", "h_2",  500000000, 0.5, 100)     # ← GOULOT intentionnel

    # edge1 (lent) sert h_3, h_4, h_5 (VMs hotspot db0-2, svc0-2)
    add_link("edge1", "h_3",  500000000, 0.5, 100)     # ← GOULOT intentionnel
    add_link("edge1", "h_4",  500000000, 0.5, 100)     # ← GOULOT intentionnel
    add_link("edge1", "h_5",  500000000, 0.5, 100)     # ← GOULOT intentionnel

    # edge2/3 (rapides) servent h_6..h_11 (VMs moins sollicitées)
    add_link("edge2", "h_6",  5000000000, 0.05, 50)
    add_link("edge2", "h_7",  5000000000, 0.05, 50)
    add_link("edge2", "h_8",  5000000000, 0.05, 50)
    add_link("edge3", "h_9",  5000000000, 0.05, 50)
    add_link("edge3", "h_10", 5000000000, 0.05, 50)
    add_link("edge3", "h_11", 5000000000, 0.05, 50)

    return {"nodes": nodes, "links": links}


# ─────────────────────────────────────────────────────────────────────────────
# 2. VIRTUAL.JSON — VMs avec bande passante suffisante pour saturer les goulots
# ─────────────────────────────────────────────────────────────────────────────

def make_virtual():
    """
    VMs avec BW = 400 Mbps (> 80% des liens goulots à 500 Mbps).
    
    4 flux simultanés vers db0 = 4 × 400 Mbps = 1.6 Gbps > 500 Mbps du goulot.
    → Saturation garantie → DynLatBw doit dévier via edge2/edge3.
    
    mips = 8000, len_cloudlet ≤ 4000 → durée CPU max = 4000/8000 = 0.5s (pas de blocage).
    """
    nodes = []
    t = 0.0
    
    # 5 VMs web (BW 400 Mbps, mips 8000)
    for i in range(5):
        nodes.append({"endtime": 5000.0, "starttime": round(t, 1),
                       "ram": 2048, "pes": 2, "name": f"web{i}",
                       "bw": 400000000, "mips": 8000, "type": "vm", "size": 1000000})
        t += 0.1

    # 5 VMs app (BW 600 Mbps, mips 8000) — hotspot principal
    for i in range(5):
        nodes.append({"endtime": 5000.0, "starttime": round(t, 1),
                       "ram": 4096, "pes": 4, "name": f"app{i}",
                       "bw": 600000000, "mips": 8000, "type": "vm", "size": 1000000})
        t += 0.1

    # 5 VMs db (BW 800 Mbps, mips 16000) — destination hotspot
    for i in range(5):
        nodes.append({"endtime": 5000.0, "starttime": round(t, 1),
                       "ram": 8192, "pes": 8, "name": f"db{i}",
                       "bw": 800000000, "mips": 16000, "type": "vm", "size": 1000000})
        t += 0.1

    # 5 VMs svc (BW 400 Mbps, mips 8000)
    for i in range(5):
        nodes.append({"endtime": 5000.0, "starttime": round(t, 1),
                       "ram": 2048, "pes": 2, "name": f"svc{i}",
                       "bw": 400000000, "mips": 8000, "type": "vm", "size": 1000000})
        t += 0.1

    # Liens virtuels (flow paths SDN)
    links = []
    
    def add_vlink(src, dst, bw=400000000):
        links.append({"source": src, "name": f"l_{src}_{dst}", "bandwidth": bw, "destination": dst})

    # web → app (tous les web peuvent aller vers tous les app)
    for w in range(5):
        for a in range(5):
            if abs(w - a) <= 1:  # liaisons proches seulement
                add_vlink(f"web{w}", f"app{a}", 400000000)

    # app → db (chaque app vers 2 db adjacents — crée des hotspots)
    for a in range(5):
        add_vlink(f"app{a}", f"db{a}", 600000000)
        add_vlink(f"app{a}", f"db{(a+1)%5}", 600000000)

    # db → app (retour)
    for d in range(5):
        add_vlink(f"db{d}", f"app{d}", 600000000)
        add_vlink(f"db{d}", f"app{(d-1)%5}", 600000000)

    # app → svc
    for a in range(5):
        add_vlink(f"app{a}", f"svc{a}", 400000000)

    # svc → app (retour)
    for s in range(5):
        add_vlink(f"svc{s}", f"app{s}", 400000000)

    # app → web (retour)
    for a in range(5):
        add_vlink(f"app{a}", f"web{a}", 400000000)
        add_vlink(f"app{a}", f"web{(a-1)%5}", 400000000)

    return {"nodes": nodes, "links": links}


# ─────────────────────────────────────────────────────────────────────────────
# 3. WORKLOAD.CSV — Trafic concentré créant des hotspots DB0/DB1
# ─────────────────────────────────────────────────────────────────────────────

def make_workload():
    """
    Génère un workload avec :
    - Des rafales vers les VMs db0 et db1 (hotspots)
      → Saturent les liens edge0/edge1 (goulots)
      → Force DynLatBw à dévier vers edge2/edge3
    - psize : 10M-80M bytes (calibré pour links 500Mbps)
      → txDelay = 80M*8 / 500M = 1.28s (congestione mesurable)
    - len_cloudlet : 800-4000 instructions (CPU max = 4000/8000 = 0.5s)
    - 200 requêtes sur ~200 secondes (1 req/s en moyenne)
    """
    rows = []
    
    # Flux hotspot : 5 sources (app0-4) → db0 en rafale (t=0..5s)
    # → Saturent le lien edge1→h_3 (500 Mbps)
    hotspot_bursts = [
        # t_start, src, link, dst, psize, cloudlet, priority
        (0.5,  "app0", "l_app0_db0", "db0", 40000000, 2000, 1),
        (0.8,  "app1", "l_app1_db0", "db0", 40000000, 2000, 2),
        (1.0,  "app2", "l_app2_db0", "db0", 40000000, 2000, 3),
        (1.2,  "app3", "l_app3_db0", "db0", 40000000, 3000, 1),
        (1.5,  "app4", "l_app4_db0", "db0", 40000000, 3000, 2),
        # 2ème rafale sur db1
        (2.0,  "app0", "l_app0_db1", "db1", 50000000, 2500, 3),
        (2.3,  "app1", "l_app1_db1", "db1", 50000000, 2500, 1),
        (2.5,  "app2", "l_app2_db1", "db1", 50000000, 4000, 2),
        (2.8,  "app3", "l_app3_db1", "db1", 50000000, 4000, 3),
        (3.0,  "app4", "l_app4_db1", "db1", 50000000, 4000, 1),
        # 3ème rafale : db0 encore (congestion cumulée)
        (3.5,  "app0", "l_app0_db0", "db0", 60000000, 3000, 2),
        (3.7,  "app1", "l_app1_db0", "db0", 60000000, 3000, 3),
        (3.9,  "app2", "l_app2_db0", "db0", 80000000, 2000, 1),
        (4.2,  "app3", "l_app3_db0", "db0", 80000000, 2000, 2),
        (4.5,  "app4", "l_app4_db0", "db0", 80000000, 2000, 3),
    ]
    
    for burst in hotspot_bursts:
        t, src, link, dst, psize, cl, prio = burst
        rows.append({"start": t, "source": src, "z": 0, "w1": 1,
                     "link": link, "dest": dst, "psize": psize // 8,
                     "w2": 1, "len_cloudlet": cl, "priority": prio})

    # Flux normaux distribués : web→app, app→svc, db→app
    normal_flows = []
    sources = [
        ("web", range(5), "app", range(5), "l_{src}_{dst}", 10000000, 800, 1),
        ("app", range(5), "svc", range(5), "l_{src}_{dst}", 20000000, 1600, 2),
        ("svc", range(5), "app", range(5), "l_{src}_{dst}", 15000000, 1200, 3),
        ("app", range(5), "web", range(5), "l_{src}_{dst}", 10000000,  800, 2),
        ("db",  range(5), "app", range(5), "l_{src}_{dst}", 30000000, 2400, 1),
        ("app", range(5), "db",  range(5), "l_{src}_{dst}", 25000000, 2000, 3),
    ]
    
    t = 5.0
    for src_prefix, src_range, dst_prefix, dst_range, _, psize, cl, prio in sources:
        for i in src_range:
            for j in dst_range:
                if abs(i - j) <= 1:  # liaisons proches pour la cohérence
                    src = f"{src_prefix}{i}"
                    dst = f"{dst_prefix}{j}"
                    link = f"l_{src}_{dst}"
                    # Variation aléatoire du psize ±30% (scale down inclus)
                    ps = int((psize // 8) * (0.7 + random.random() * 0.6))
                    c  = int(cl   * (0.7 + random.random() * 0.6))
                    rows.append({"start": round(t, 1), "source": src, "z": 0, "w1": 1,
                                 "link": link, "dest": dst, "psize": ps,
                                 "w2": 1, "len_cloudlet": c, "priority": prio})
                    t += 0.3

    # Deuxième série de rafales hotspot à mi-simulation (t=100-110s)
    for i, burst in enumerate(hotspot_bursts):
        _, src, link, dst, psize, cl, prio = burst
        rows.append({"start": round(100.0 + i * 0.8, 1), "source": src, "z": 0, "w1": 1,
                     "link": link, "dest": dst, "psize": int((psize // 8) * 1.5),
                     "w2": 1, "len_cloudlet": cl, "priority": prio})

    # Troisième série à t=170s
    for i, burst in enumerate(hotspot_bursts):
        _, src, link, dst, psize, cl, prio = burst
        rows.append({"start": round(170.0 + i * 0.5, 1), "source": src, "z": 0, "w1": 1,
                     "link": link, "dest": dst, "psize": int((psize // 8) * 2.0),
                     "w2": 1, "len_cloudlet": cl, "priority": prio})

    rows.sort(key=lambda r: r["start"])
    return rows


# ─────────────────────────────────────────────────────────────────────────────
# GÉNÉRATION DES FICHIERS
# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)

    # physical.json
    phys = make_physical()
    with open(os.path.join(OUT_DIR, "physical.json"), "w") as f:
        json.dump(phys, f, indent=4)
    print(f"✅ physical.json : {len(phys['nodes'])} nœuds, {len(phys['links'])} liens")

    # virtual.json
    virt = make_virtual()
    with open(os.path.join(OUT_DIR, "virtual.json"), "w") as f:
        json.dump(virt, f, indent=4)
    print(f"✅ virtual.json  : {len(virt['nodes'])} VMs, {len(virt['links'])} liens virtuels")

    # workload.csv
    wl = make_workload()
    # Ordre attendu par WorkloadParser.java : start(0), source(1), z(2), w1(3), link(4), dest(5), psize(6), w2(7), len_cloudlet(8), priority(9)
    # Mon script actuel : start, source, z, w1, dest, link, psize, w2, len_cloudlet, priority
    # On doit échanger dest et link
    fieldnames = ["start","source","z","w1","link","dest","psize","w2","len_cloudlet","priority"]
    with open(os.path.join(OUT_DIR, "workload.csv"), "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in wl:
            # On s'assure que l'ordre des colonnes est correct
            writer.writerow(row)
    print(f"✅ workload.csv  : {len(wl)} requêtes, t=[{wl[0]['start']}..{wl[-1]['start']}]s")

    # Vérification des ratios
    print("\n── VÉRIFICATION SCIENTIFIQUE ──")
    max_psize = max(r["psize"] for r in wl)
    max_cl    = max(r["len_cloudlet"] for r in wl)
    min_bw_goulot = 500_000_000
    vm_mips = 8000

    tx_max = max_psize * 8 / min_bw_goulot
    cpu_max = max_cl / vm_mips

    print(f"  psize max        : {max_psize/1e6:.1f} Mo")
    print(f"  Tx delay max     : {tx_max:.3f} s sur lien goulot 500Mbps")
    print(f"  len_cloudlet max : {max_cl} instructions")
    print(f"  CPU delay max    : {cpu_max:.3f} s (target < 0.5s)")
    print(f"  Ratio hotspot    : 5 flux × 600Mbps = {5*600/1000:.1f} Gbps > 500 Mbps goulot ✅")

    if cpu_max > 0.5:
        print(f"  ⚠️  CPU delay trop élevé ({cpu_max:.2f}s > 0.5s), réduire len_cloudlet")
    else:
        print(f"  ✅ CPU delay OK ({cpu_max:.3f}s < 0.5s)")

    print("\n📂 Dataset généré dans:", OUT_DIR)
