import os
import argparse
import pandas as pd


def safe_read(ds_dir, fname):
    p = os.path.join(ds_dir, fname)
    if not os.path.exists(p): return None
    try:
        df = pd.read_csv(p, sep=";")
        df.columns = [c.strip().lower() for c in df.columns]
        return df
    except Exception: return None


def extract_kpis(ds_dir):
    kpis = {}
    
    # Energy
    df_e = safe_read(ds_dir, "host_energy_total.csv")
    if df_e is not None:
        ecol = "energy"
        grp = df_e.groupby(["vm_policy", "link_policy"])
        for (vm, rt), g in grp:
            val = pd.to_numeric(g[ecol], errors="coerce").mean()
            kpis.setdefault((vm, rt), {})["energy_Wh"] = round(val, 2)

    # Latency
    df_pkt = safe_read(ds_dir, "packet_delays.csv")
    if df_pkt is not None:
        grp = df_pkt.groupby(["vm_policy", "link_policy"])
        for (vm, rt), g in grp:
            val = pd.to_numeric(g["delay_ms"], errors="coerce").mean()
            q_val = pd.to_numeric(g["queue_delay_ms"], errors="coerce").mean()
            if val > 10000:
                kpis.setdefault((vm, rt), {})["avg_lat_s"] = round(val/1000, 2)
            else:
                kpis.setdefault((vm, rt), {})["avg_lat_ms"] = round(val, 2)
            kpis.setdefault((vm, rt), {})["avg_queue_ms"] = round(q_val, 2)

    # SLA
    df_sla = safe_read(ds_dir, "qos_violations.csv")
    if df_sla is not None:
        grp = df_sla.groupby(["vm_policy", "link_policy"])
        for (vm, rt), g in grp:
            kpis.setdefault((vm, rt), {})["sla_violations"] = len(g)

    return kpis

def format_kpi_table(kpis):
    vm_order = ["MFF", "LFF", "LWFF"]
    rt_order = ["First", "DynLatBw"]
    lines = ["| VM Policy | Routing | Énergie (Wh) | Latence (s/ms) | Queue (ms) | SLA |", "|---|---|---|---|---|---|"]
    for vm in vm_order:
        for rt in rt_order:
            d = kpis.get((vm, rt), {})
            rt_label = f"**{rt}**" if rt == "DynLatBw" else rt
            
            lat_str = "N/A"
            if "avg_lat_s" in d: lat_str = f"{d['avg_lat_s']} s"
            elif "avg_lat_ms" in d: lat_str = f"{d['avg_lat_ms']} ms"
            
            lines.append(f"| {vm} | {rt_label} | {d.get('energy_Wh', 'N/A')} | {lat_str} | {d.get('avg_queue_ms', 'N/A')} | {d.get('sla_violations', 0)} |")
    return "\n".join(lines)

def generate_report(results_dir):
    datasets_root = os.path.join(results_dir, "datasets")
    md_path = os.path.join(results_dir, "SUMMARY_SCIENTIFIC_REPORT.md")
    
    if not os.path.exists(datasets_root): return
    
    sections = ""
    for ds_name in sorted(os.listdir(datasets_root)):
        ds_path = os.path.join(datasets_root, ds_name)
        if not os.path.isdir(ds_path): continue
        kpis = extract_kpis(ds_path)
        if not kpis: continue
        
        sections += f"""
---
## Analyse du Dataset : {ds_name}

### Tableau de Synthèse Complet
{format_kpi_table(kpis)}

### Visualisations de Performance
````carousel
![Consommation Énergétique](datasets/{ds_name}/plots/fig1_energy.png)
<!-- slide -->
![Analyse Latence](datasets/{ds_name}/plots/fig2_latency.png)
<!-- slide -->
![Violations SLA](datasets/{ds_name}/plots/fig3_sla.png)
<!-- slide -->
![Délais de Queue](datasets/{ds_name}/plots/fig4_queuing.png)
<!-- slide -->
![Utilisation CPU](datasets/{ds_name}/plots/fig5_utilization.png)
````
"""

    report_content = f"""# Rapport de Recherche : Optimisation du Routage SDN
*Organisation structurée des résultats — {results_dir}*

{sections}

---
## Conclusion
Ce rapport consolide les performances du routage dynamique DynLatBw.
"""
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(report_content)
    print(f"Full report generated: {md_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir")
    args = parser.parse_args()
    generate_report(args.results_dir)
