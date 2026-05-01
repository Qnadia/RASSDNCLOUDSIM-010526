"""
Rapport consolide multi-experiences CloudSimSDN
Lit tous les dossiers experiment_*/ (legacy) OU VM_*/Link_*/ (Sim VF)
et genere un rapport comparatif PDF + CSV.

Usage:
  python consolidated_report.py                         # auto-detect
  python consolidated_report.py --simvf PATH            # mode Sim VF
  python consolidated_report.py --results-dir PATH      # legacy experiment_*
"""
import os, glob, re, sys
import pandas as pd
import matplotlib
import matplotlib.pyplot as plt
import numpy as np

try:
    matplotlib.rc("font", family="Times New Roman")
except Exception:
    pass

matplotlib.rcParams.update({
    "font.size": 12,
    "axes.labelsize": 13, "axes.titlesize": 13,
    "legend.fontsize": 10, "xtick.labelsize": 10, "ytick.labelsize": 10,
    "savefig.bbox": "tight", "savefig.pad_inches": 0.05,
})

# Default Sim VF path
DEFAULT_SIMVF = r"E:\Workspace\v2\cloudsimsdn-research\results\2026-05-14\Sim VF"

# —— Config ——————————————————————————————————————————————————————————————————————
BASE_DIR    = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
RESULTS_DIR = os.path.join(BASE_DIR, "results")

# ── Mode Sim VF : VM_*/Link_*/ ────────────────────────────────────────────────
def find_simvf_dirs(simvf_root):
    """
    Parcourt <simvf_root>/<dataset>/VM_*/Link_*/ et retourne une liste de
    tuples (exp_dir_list, out_dir) — un par (dataset, vm).
    Structure attendue :
      simvf_root/
        small/VM_MFF/Link_First/   <- terminal exp dir
        small/VM_MFF/Link_BwAllocN/
        ...
        medium/...
        large/...
    """
    if not os.path.isdir(simvf_root):
        print(f"[ERROR] Dossier Sim VF introuvable : {simvf_root}")
        return []

    DATASETS = ["small", "medium", "large"]
    groups   = []

    for ds in DATASETS:
        ds_path = os.path.join(simvf_root, ds)
        if not os.path.isdir(ds_path):
            continue
        # Collecter tous les dossiers Link_* (terminaux) dans ce dataset
        exp_dirs = sorted(glob.glob(os.path.join(ds_path, "VM_*", "Link_*")))
        exp_dirs = [d for d in exp_dirs if os.path.isdir(d)]
        if exp_dirs:
            out_dir = os.path.join(simvf_root, "figures_consolidated", ds)
            groups.append((exp_dirs, out_dir, ds))
            print(f"[SCAN SimVF] {ds}: {len(exp_dirs)} combinaisons -> {out_dir}")

    # Groupe global (tous datasets)
    all_dirs = [d for exp_dirs, _out, _ds in groups for d in exp_dirs]
    if all_dirs:
        groups.append((all_dirs,
                       os.path.join(simvf_root, "figures_consolidated"),
                       "ALL"))
    return groups


def load_experiments_simvf(exp_dirs):
    """
    Charge les KPIs depuis des dossiers VM_*/Link_*/ (format Sim VF).
    Extrait vm_alloc et link_policy depuis les deux derniers segments du chemin.
    """
    rows = []
    for exp_path in sorted(exp_dirs):
        parts       = exp_path.replace("\\", "/").rstrip("/").split("/")
        link_pol    = parts[-1].replace("Link_", "")   # ex: BwAllocN
        vm_alloc    = parts[-2].replace("VM_",  "")    # ex: MFF
        dataset     = parts[-3] if len(parts) >= 3 else "?"
        exp_name    = f"{dataset}_{vm_alloc}_{link_pol}"

        # ── Energie ──────────────────────────────────────────────────────────
        total_energy = 0.0
        energy_file  = os.path.join(exp_path, "host_energy_total.csv")
        if os.path.exists(energy_file) and os.path.getsize(energy_file) > 5:
            try:
                df_e = pd.read_csv(energy_file, sep=";", comment="#", header=None)
                total_row = df_e[df_e[0].astype(str).str.contains("TOTAL", na=False)]
                if not total_row.empty:
                    total_energy = float(total_row.iloc[-1].iloc[-1])
            except Exception as ex:
                print(f"  [!] energy {exp_name}: {ex}")

        # ── Utilisation host ─────────────────────────────────────────────────
        avg_cpu = avg_ram = avg_bw = 0.0
        active_cpu = active_ram = active_bw = 0.0
        sim_duration = 0.0
        util_file = os.path.join(exp_path, "host_utilization.csv")
        if os.path.exists(util_file) and os.path.getsize(util_file) > 10:
            try:
                dfu = pd.read_csv(util_file, sep=";", comment="#",
                                  names=["time","hostId","cpu","ram","bw","energy"])
                dfu = dfu.apply(pd.to_numeric, errors="coerce").dropna()
                if not dfu.empty:
                    sim_duration = dfu["time"].max()
                    avg_cpu = dfu["cpu"].mean()
                    avg_ram = dfu["ram"].mean()
                    avg_bw  = dfu["bw"].mean()
                    if total_energy == 0:
                        total_energy = dfu.groupby("hostId")["energy"].max().sum()
                    active_df = dfu[dfu["cpu"] > 0.1]
                    if not active_df.empty:
                        active_cpu = active_df["cpu"].mean()
                        active_ram = active_df["ram"].mean()
                        active_bw  = active_df["bw"].mean()
            except Exception as ex:
                print(f"  [!] utilization {exp_name}: {ex}")

        # ── VM pressure ──────────────────────────────────────────────────────
        vm_mips_pressure = 0.0
        vm_file = os.path.join(exp_path, "vm_utilization.csv")
        if os.path.exists(vm_file) and os.path.getsize(vm_file) > 10:
            try:
                dfvm = pd.read_csv(vm_file, sep=";", comment="#",
                                   names=["time","vmId","cpu","ram","mips_alloc","ram_max"])
                dfvm = dfvm.apply(pd.to_numeric, errors="coerce").dropna()
                if not dfvm.empty:
                    vm_mips_pressure = dfvm["cpu"].mean()
            except Exception as ex:
                print(f"  [!] vm_util {exp_name}: {ex}")

        # ── Latence (path_latency_final) ─────────────────────────────────────
        avg_lat = max_lat = avg_jitter = n_req = 0
        lat_file = os.path.join(exp_path, "path_latency_final.csv")
        if os.path.exists(lat_file) and os.path.getsize(lat_file) > 10:
            try:
                from io import StringIO
                with open(lat_file, "r") as f:
                    lines = f.readlines()
                header     = lines[0].replace("# units: ","").replace("#","").strip().split(";")
                data_lines = [l for l in lines if not l.startswith("#")]
                dfl = pd.read_csv(StringIO("\n".join(data_lines)), sep=";", names=header)
                col = [c for c in dfl.columns if "total_delay" in c.lower()]
                if col and not dfl.empty:
                    dfl[col[0]] = pd.to_numeric(dfl[col[0]], errors="coerce")
                    avg_lat    = dfl[col[0]].mean() / 1000.0
                    max_lat    = dfl[col[0]].max()  / 1000.0
                    avg_jitter = dfl[col[0]].std()  / 1000.0 if len(dfl) > 1 else 0
                    n_req      = len(dfl)
            except Exception as ex:
                print(f"  [!] latency {exp_name}: {ex}")

        # ── Packet delays ────────────────────────────────────────────────────
        avg_pkt_delay = max_pkt_delay = 0.0
        pkt_file = os.path.join(exp_path, "packet_delays.csv")
        if os.path.exists(pkt_file) and os.path.getsize(pkt_file) > 10:
            try:
                dfp = pd.read_csv(pkt_file, sep=";", comment="#",
                                  names=["packetId","src","dst","psize","delay"])
                dfp["delay"] = pd.to_numeric(dfp["delay"], errors="coerce")
                avg_pkt_delay = dfp["delay"].mean()
                max_pkt_delay = dfp["delay"].max()
            except Exception as ex:
                print(f"  [!] pkt_delay {exp_name}: {ex}")

        # ── QoS violations (sep=';' dans Sim VF) ─────────────────────────────
        sla_viol = 0
        qos_file = os.path.join(exp_path, "qos_violations.csv")
        if os.path.exists(qos_file) and os.path.getsize(qos_file) > 5:
            try:
                dfq = pd.read_csv(qos_file, sep=";", comment="#",
                                  names=["ts","flowId","violationType"])
                sla_viol = len(dfq)
            except Exception as ex:
                print(f"  [!] qos {exp_name}: {ex}")

        rows.append({
            "experiment":    exp_name,
            "dataset":       dataset,
            "vm_alloc":      vm_alloc,
            "link_policy":   link_pol,
            "wf_policy":     "-",
            "energy_Wh":     round(total_energy,    4),
            "active_cpu":    round(active_cpu,      2),
            "active_ram":    round(active_ram,      2),
            "active_bw":     round(active_bw,       2),
            "vm_pressure":   round(vm_mips_pressure,2),
            "sim_duration":  round(sim_duration,    1),
            "avg_latency_s": round(avg_lat,         4),
            "max_latency_s": round(max_lat,         4),
            "jitter_s":      round(avg_jitter,      4),
            "avg_pkt_ms":    round(avg_pkt_delay,   2),
            "max_pkt_ms":    round(max_pkt_delay,   2),
            "sla_violations":sla_viol,
            "n_requests":    n_req,
        })
    return pd.DataFrame(rows)


# ── Mode legacy : experiment_*/ ────────────────────────────────────────────────
def find_experiment_dirs():
    """
    Cherche les dossiers experiment_* dans l'ordre de priorite :
    1. --results-dir <path>
    2. results/latest-date/dataset/vmAlloc/experiment_*/
    3. experiment_* (legacy flat)
    Retourne une liste de tuples (exp_dir_list, out_dir).
    """
    groups = []

    # Option 1 : --results-dir
    if "--results-dir" in sys.argv:
        idx  = sys.argv.index("--results-dir")
        if idx + 1 < len(sys.argv):
            rdir     = sys.argv[idx + 1]
            exp_dirs = sorted(glob.glob(os.path.join(rdir, "**/experiment_*"), recursive=True))
            exp_dirs = [d for d in exp_dirs if os.path.isdir(d)]
            if "--include-rr" not in sys.argv:
                exp_dirs = [d for d in exp_dirs
                            if "_RR_" not in os.path.basename(d)
                            and "_RR"  not in os.path.basename(d)]
            out_dir = os.path.join(rdir, "figures_consolidated")
            if exp_dirs:
                groups.append((exp_dirs, out_dir))
            return groups

    # Option 2 : results/YYYY-MM-DD/dataset/vmAlloc/experiment_*/
    if os.path.isdir(RESULTS_DIR):
        date_dirs = sorted([d for d in os.listdir(RESULTS_DIR)
                            if os.path.isdir(os.path.join(RESULTS_DIR, d))], reverse=True)
        if date_dirs:
            latest_date = date_dirs[0]
            date_path   = os.path.join(RESULTS_DIR, latest_date)
            for ds in sorted(os.listdir(date_path)):
                ds_path = os.path.join(date_path, ds)
                if not os.path.isdir(ds_path):
                    continue
                sub_folders = [f for f in os.listdir(ds_path)
                               if os.path.isdir(os.path.join(ds_path, f))
                               and f != "figures_consolidated"]
                for sub in sub_folders:
                    sub_path = os.path.join(ds_path, sub)
                    exp_dirs = sorted(glob.glob(os.path.join(sub_path, "experiment_*")))
                    exp_dirs = [d for d in exp_dirs if os.path.isdir(d)]
                    if exp_dirs:
                        out_dir = os.path.join(sub_path, "figures_consolidated")
                        groups.append((exp_dirs, out_dir))
                        print(f"[SCAN] {latest_date}/{ds}/{sub}: {len(exp_dirs)} experiments")
                all_exp = sorted(glob.glob(os.path.join(ds_path, "**/experiment_*"), recursive=True))
                all_exp = [d for d in all_exp if os.path.isdir(d)]
                if all_exp:
                    out_dir = os.path.join(ds_path, "figures_consolidated")
                    groups.append((all_exp, out_dir))
                    print(f"[SCAN GLOBAL] {latest_date}/{ds}: {len(all_exp)} experiments")
        if groups:
            return groups

    # Option 3 : legacy flat
    exp_dirs = sorted([d for d in glob.glob(os.path.join(BASE_DIR, "experiment_*"))
                       if os.path.isdir(d)])
    out_dir  = os.path.join(BASE_DIR, "figures_consolidated")
    if exp_dirs:
        groups.append((exp_dirs, out_dir))
    return groups


COLORS  = ["#1f77b4","#ff7f0e","#2ca02c","#d62728","#9467bd","#8c564b","#e377c2"]
MARKERS = ["o","s","^","D","x","*","P"]

def save_fig(fig, name, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    fig.savefig(os.path.join(out_dir, f"{name}.pdf"))
    fig.savefig(os.path.join(out_dir, f"{name}.png"), dpi=600)
    plt.close(fig)

# —— Lecture des expériences ————————————————————————————————————————————————————
def load_experiments(exp_dirs):
    rows = []
    for exp_path in sorted(exp_dirs):
        exp_name = os.path.basename(exp_path)
        # Extrait vmAlloc / linkPolicy / workloadPolicy depuis le nom du dossier
        parts = exp_name.replace("experiment_","").split("_", 2)
        vm_alloc   = parts[0] if len(parts) > 0 else "?"
        link_pol   = parts[1] if len(parts) > 1 else "?"
        wf_pol     = parts[2] if len(parts) > 2 else "?"

        # No filter: Include all VM allocation policies

        energy_total_file = os.path.join(exp_path, "host_energy_total.csv")
        total_energy = 0.0
        if os.path.exists(energy_total_file) and os.path.getsize(energy_total_file) > 5:
            try:
                # On lit sans names d'abord pour voir le contenu
                df_en = pd.read_csv(energy_total_file, sep=";", comment="#", header=None)
                if not df_en.empty:
                    # Le format est : TS; HostName; HostID; Energy
                    # Pour la ligne TOTAL, on cherche "TOTAL" dans la 1ère colonne (index 0)
                    total_row = df_en[df_en[0].astype(str).str.contains("TOTAL", case=False, na=False)]
                    if not total_row.empty:
                        total_energy = float(total_row.iloc[-1].iloc[-1]) # Dernière ligne, dernière colonne
            except Exception as e:
                print(f"  [!] Erreur energy pour {exp_name}: {e}")

        # —— Utilisation host (moyenne CPU/RAM/BW + ENERGY) ———————————————————————
        util_file = os.path.join(exp_path, "host_utilization.csv")
        avg_cpu = avg_ram = avg_bw = 0.0
        active_cpu = active_ram = active_bw = 0.0
        
        if os.path.exists(util_file) and os.path.getsize(util_file) > 10:
            try:
                # Nouveau format : time; hostId; cpu(%); ram(%); bw(%); energy(Wh)
                dfu = pd.read_csv(util_file, sep=";", comment="#",
                                  names=["time","hostId","cpu","ram","bw","energy"])
                
                for col_name in ["cpu", "ram", "bw", "energy"]:
                    if dfu[col_name].dtype == object:
                        dfu[col_name] = dfu[col_name].str.replace(",", ".", regex=False)
                
                dfu = dfu.apply(pd.to_numeric, errors="coerce").dropna()
                
                if not dfu.empty:
                    sim_duration = dfu["time"].max()
                    avg_cpu = dfu["cpu"].mean()
                    avg_ram = dfu["ram"].mean()
                    avg_bw  = dfu["bw"].mean()
                    
                    # Energie totale extraite de host_utilization si host_energy_total manque
                    if total_energy == 0:
                        total_energy = dfu.groupby("hostId")["energy"].max().sum()
                    
                    active_df = dfu[dfu["cpu"] > 0.1]
                    if not active_df.empty:
                        active_cpu = active_df["cpu"].mean()
                        active_ram = active_df["ram"].mean()
                        active_bw  = active_df["bw"].mean()
            except Exception as e:
                print(f"  [!] Erreur utilization pour {exp_name}: {e}")

        sim_duration = 0.0
        if os.path.exists(util_file):
            try:
                df_dur = pd.read_csv(util_file, sep=";", comment="#", usecols=[0], names=["time"])
                sim_duration = df_dur["time"].max()
            except: pass

        # —— Analyse VM (Pression MIPS) ———————————————————————————————————————————
        vm_util_file = os.path.join(exp_path, "vm_utilization.csv")
        vm_mips_pressure = 0.0
        if os.path.exists(vm_util_file) and os.path.getsize(vm_util_file) > 10:
            try:
                # Format: time; vmId; cpu(%); ram(%); mips_alloc; ram_max
                dfvm = pd.read_csv(vm_util_file, sep=";", comment="#",
                                   names=["time","vmId","cpu","ram","mips_alloc","ram_max"])
                dfvm = dfvm.apply(pd.to_numeric, errors="coerce").dropna()
                if not dfvm.empty:
                    # CPU % est l'utilisation des MIPS alloués.
                    vm_mips_pressure = dfvm["cpu"].mean()
            except Exception as e:
                print(f"  [!] Erreur VM util pour {exp_name}: {e}")

        # —— Latences et Packets ——————————————————————————————————————————————————
        lat_file = os.path.join(exp_path, "path_latency_final.csv")
        avg_lat = max_lat = avg_jitter = n_req = 0
        if os.path.exists(lat_file) and os.path.getsize(lat_file) > 10:
            try:
                with open(lat_file, 'r') as f:
                    lines = f.readlines()
                if lines:
                    header = lines[0].replace("# units: ", "").replace("#", "").strip().split(";")
                    data_lines = [l for l in lines if not l.startswith("#")]
                    from io import StringIO
                    dfl = pd.read_csv(StringIO("\n".join(data_lines)), sep=";", names=header)
                    if not dfl.empty:
                        col = [c for c in dfl.columns if "total_delay" in c.lower()]
                        if col:
                            dfl[col[0]] = pd.to_numeric(dfl[col[0]], errors="coerce")
                            avg_lat  = dfl[col[0]].mean() / 1000.0 # s
                            max_lat  = dfl[col[0]].max() / 1000.0
                            avg_jitter = dfl[col[0]].std() / 1000.0 if len(dfl) > 1 else 0
                            n_req    = len(dfl)
            except Exception as e:
                print(f"  [!] Erreur latency pour {exp_name}: {e}")

        # —— Packet Delays ————————————————————————————————————————————————————————
        pkt_file = os.path.join(exp_path, "packet_delays.csv")
        avg_pkt_delay = max_pkt_delay = 0.0
        if os.path.exists(pkt_file) and os.path.getsize(pkt_file) > 10:
            try:
                # Format: packetId; src; dst; psize; delay(ms)
                dfp = pd.read_csv(pkt_file, sep=";", comment="#", 
                                  names=["packetId", "src", "dst", "psize", "delay"])
                dfp["delay"] = pd.to_numeric(dfp["delay"], errors="coerce")
                avg_pkt_delay = dfp["delay"].mean()
                max_pkt_delay = dfp["delay"].max()
            except Exception as e:
                print(f"  [!] Erreur packet delays pour {exp_name}: {e}")

        # —— QoS violations ———————————————————————————————————————————————————————
        qos_file = os.path.join(exp_path, "qos_violations.csv")
        sla_viol = 0
        if os.path.exists(qos_file):
            try:
                # Essai sep=';' (Sim VF) puis sep=',' (legacy)
                dfq = pd.read_csv(qos_file, sep=";", comment="#")
                if dfq.shape[1] < 2:
                    dfq = pd.read_csv(qos_file, sep=",", comment="#")
                sla_viol = len(dfq)
            except:
                pass

        rows.append({
            "experiment":    exp_name,
            "vm_alloc":      vm_alloc,
            "link_policy":   link_pol,
            "wf_policy":     wf_pol,
            "energy_Wh":     round(total_energy, 4),
            "active_cpu":    round(active_cpu, 2),
            "active_ram":    round(active_ram, 2),
            "active_bw":     round(active_bw, 2),
            "vm_pressure":   round(vm_mips_pressure, 2),
            "sim_duration":  sim_duration,
            "avg_latency_s": round(avg_lat, 4),
            "max_latency_s": round(max_lat, 4),
            "jitter_s":      round(avg_jitter, 4),
            "avg_pkt_ms":    round(avg_pkt_delay, 2),
            "max_pkt_ms":    round(max_pkt_delay, 2),
            "sla_violations":sla_viol,
            "n_requests":    n_req
        })
    return pd.DataFrame(rows)

def generate_figures(df, OUT_DIR):
    """Génère toutes les figures pour un groupe d'expériences."""
    os.makedirs(OUT_DIR, exist_ok=True)
    
    # Tri personnalisé : LFF -> MFF -> LWFF, puis First -> BwAlloc -> Dijkstra -> DynLatBw
    vm_order = {"LFF": 1, "MFF": 2, "LWFF": 3}
    link_order = {"First": 1, "BwAlloc": 2, "Dijkstra": 3, "DynLatBw": 4}
    
    df["vm_sort"] = df["vm_alloc"].map(lambda x: vm_order.get(x, 99))
    df["link_sort"] = df["link_policy"].map(lambda x: link_order.get(x, 99))
    df = df.sort_values(by=["vm_sort", "link_sort"])


    labels = [f"{r.vm_alloc}\n{r.link_policy}\n{r.wf_policy}" for _, r in df.iterrows()]
    x = np.arange(len(df))

    # —— Figure 1 : Énergie totale —————————————————————————————————
    fig, ax = plt.subplots(figsize=(max(7, len(df)*1.2), 4))
    bars = ax.bar(x, df["energy_Wh"], color=COLORS[:len(df)], edgecolor="white")
    for bar, v in zip(bars, df["energy_Wh"]):
        ax.text(bar.get_x()+bar.get_width()/2, bar.get_height()+0.002,
                f"{v:.3f}", ha="center", fontsize=9)
    ax.set_xticks(x); ax.set_xticklabels(labels, fontsize=9); ax.set_ylabel("Total Energy (Wh)")
    ax.set_title("Total Energy Comparison"); ax.grid(True, axis="y", linestyle="--", alpha=0.4)
    save_fig(fig, "fig1_energy", OUT_DIR)

    # —— Figure 2 : Latence moyenne ————————————————————————————————
    fig, ax = plt.subplots(figsize=(max(7, len(df)*1.2), 4))
    bars = ax.bar(x, df["avg_latency_s"], color=COLORS[:len(df)], edgecolor="white")
    for bar, v in zip(bars, df["avg_latency_s"]):
        ax.text(bar.get_x()+bar.get_width()/2, bar.get_height()+0.01,
                f"{v:.2f}s", ha="center", fontsize=9)
    ax.set_xticks(x); ax.set_xticklabels(labels, fontsize=9); ax.set_ylabel("Avg Latency (s)")
    ax.set_title("Average E2E Latency"); ax.grid(True, axis="y", linestyle="--", alpha=0.4)
    save_fig(fig, "fig2_latency", OUT_DIR)

    # —— Figure 3 : SLA Violations (Total) ———————————————————————
    fig, ax = plt.subplots(figsize=(max(7, len(df)*1.2), 4))
    bars = ax.bar(x, df["sla_violations"], color="#d62728", edgecolor="white")
    for bar, v in zip(bars, df["sla_violations"]):
        ax.text(bar.get_x()+bar.get_width()/2, bar.get_height()+(df["sla_violations"].max()*0.02),
                f"{int(v)}", ha="center", fontsize=9)
    ax.set_xticks(x); ax.set_xticklabels(labels, fontsize=9); ax.set_ylabel("Total SLA Violations")
    ax.set_title("SLA Violations (QoE Degradation)"); ax.grid(True, axis="y", linestyle="--", alpha=0.4)
    save_fig(fig, "fig3_sla", OUT_DIR)

    # —— Figure 4 : Packet Delay (ms) —————————————————————————————
    fig, ax = plt.subplots(figsize=(max(7, len(df)*1.2), 4))
    w = 0.35
    ax.bar(x - w/2, df["avg_pkt_ms"], width=w, label="Avg Packet Delay", color="#4e79a7")
    ax.bar(x + w/2, df["max_pkt_ms"], width=w, label="Max Packet Delay", color="#a0cbe8")
    ax.set_xticks(x); ax.set_xticklabels(labels, fontsize=9); ax.set_ylabel("Packet Delay (ms)")
    ax.set_title("Packet Transmission Delays"); ax.legend(); ax.grid(True, axis="y", linestyle="--", alpha=0.4)
    save_fig(fig, "fig4_packet_delay", OUT_DIR)

    # —— Figure 5 : Active Resource Utilization ———————————————————
    fig, ax = plt.subplots(figsize=(max(8, len(df)*1.4), 4))
    w = 0.25
    ax.bar(x - w, df["active_cpu"], width=w, label="CPU", color="#1f77b4")
    ax.bar(x,     df["active_ram"], width=w, label="RAM", color="#ff7f0e")
    ax.bar(x + w, df["active_bw"],  width=w, label="BW",  color="#2ca02c")
    ax.set_xticks(x); ax.set_xticklabels(labels, fontsize=9); ax.set_ylabel("Utilization (%)")
    ax.set_ylim(0, 100); ax.set_title("Active Host Utilization"); ax.legend(); ax.grid(True, axis="y", linestyle="--", alpha=0.4)
    save_fig(fig, "fig5_utilization", OUT_DIR)

    # —— Nouveaux Graphiques de Comparaison de Routage (Grouped Bars) ——
    try:
        import seaborn as sns
        sns.set_theme(style="whitegrid")
        
        # 1. Packet Delay Grouped Bar
        fig, ax = plt.subplots(figsize=(10, 6))
        sns.barplot(data=df, x="vm_alloc", y="avg_pkt_ms", hue="link_policy", palette="Set2", ax=ax)
        ax.set_title("Comparaison du Délai Réseau des Paquets (Routage vs Placement)", fontsize=14, weight='bold')
        ax.set_ylabel("Délai Moyen des Paquets (ms)")
        ax.set_xlabel("Politique d'Allocation des VMs")
        save_fig(fig, "fig6_routing_delay", OUT_DIR)

        # 2. Energy Grouped Bar
        fig, ax = plt.subplots(figsize=(10, 6))
        sns.barplot(data=df, x="vm_alloc", y="energy_Wh", hue="link_policy", palette="Set1", ax=ax)
        ax.set_title("Comparaison de la Consommation Énergétique par Politique de Routage", fontsize=14, weight='bold')
        ax.set_ylabel("Énergie Totale (Wh)")
        ax.set_xlabel("Politique d'Allocation des VMs")
        save_fig(fig, "fig7_routing_energy", OUT_DIR)
        
        # 3. SLA Violations Grouped Bar
        fig, ax = plt.subplots(figsize=(10, 6))
        sns.barplot(data=df, x="vm_alloc", y="sla_violations", hue="link_policy", palette="Set3", ax=ax)
        ax.set_title("Violations SLA (Congestion) par Politique de Routage", fontsize=14, weight='bold')
        ax.set_ylabel("Nombre de Violations SLA")
        ax.set_xlabel("Politique d'Allocation des VMs")
        save_fig(fig, "fig8_routing_sla", OUT_DIR)
    except ImportError:
        print("[WARNING] Seaborn not installed, skipping grouped routing charts.")


def main():
    # ── Mode Sim VF ───────────────────────────────────────────────────────────
    if "--simvf" in sys.argv:
        idx      = sys.argv.index("--simvf")
        simvf_root = sys.argv[idx + 1] if idx + 1 < len(sys.argv) else DEFAULT_SIMVF
        groups   = find_simvf_dirs(simvf_root)
        if not groups:
            print("[ERROR] Aucune combinaison VM/Link trouvee dans le dossier Sim VF.")
            return
        for exp_dirs, out_dir, label in groups:
            print(f"\n[SIMVF] Dataset={label} | {len(exp_dirs)} combinaisons -> {out_dir}")
            df = load_experiments_simvf(exp_dirs)
            if df.empty:
                continue
            print(df[["experiment","energy_Wh","avg_pkt_ms","sla_violations"]].to_string(index=False))
            os.makedirs(out_dir, exist_ok=True)
            df.to_csv(os.path.join(out_dir, "consolidated_summary.csv"), index=False, sep=";")
            generate_figures(df, out_dir)
            print(f"  Figures -> {out_dir}/")
        return

    # ── Mode legacy experiment_*/ ─────────────────────────────────────────────
    groups = find_experiment_dirs()
    if not groups:
        print("[ERROR] No experiment folders found.")
        return

    for exp_dirs, out_dir in groups:
        print(f"\n[REPORT] Processing {len(exp_dirs)} experiments -> {out_dir}")
        df = load_experiments(exp_dirs)
        if df.empty:
            continue
        print(df[["experiment","energy_Wh","avg_latency_s","sla_violations","avg_pkt_ms"]].to_string(index=False))
        os.makedirs(out_dir, exist_ok=True)
        df.to_csv(os.path.join(out_dir, "consolidated_summary.csv"), index=False, sep=";")
        generate_figures(df, out_dir)
        print(f"  Done. Figures saved in {out_dir}/")

if __name__ == "__main__":
    main()
