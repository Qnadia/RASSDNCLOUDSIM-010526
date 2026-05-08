"""4_generate_all_plots_final.py - RAS-SDNCloudSim - Final Unified Script
All figures (v1+v2 consolidated). Titles in English. No figure numbers.
"""
import os, warnings, argparse
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import seaborn as sns
warnings.filterwarnings("ignore")

sns.set_theme(style="whitegrid")
plt.rcParams.update({"font.family":"serif","axes.edgecolor":"black",
    "axes.linewidth":1.5,"grid.color":"#ddd","grid.linestyle":"--",
    "grid.alpha":0.7,"legend.frameon":True,"figure.autolayout":True,"figure.dpi":150})

PALETTE = {"First":"#d62728","BLA":"#1f77b4"}
VM_ORDER = ["MFF","LWFF","LFF"]
PAL_VM   = {"MFF":"#1f77b4","LWFF":"#2ca02c","LFF":"#ff7f0e"}
DS_PALETTE = {"dataset-mini":"#9467bd","dataset-small":"#2ca02c",
              "dataset-medium":"#ff7f0e","dataset-large":"#d62728"}

def save(fig, path, close=True):
    for ext in ("png","svg"):
        fig.savefig(f"{path}.{ext}", bbox_inches="tight", dpi=200)
    if close: plt.close(fig)

def ann_bars(ax, fmt="{:.0f}", fs=8):
    for p in ax.patches:
        h = p.get_height()
        if np.isnan(h) or h==0: continue
        ax.annotate(fmt.format(h),(p.get_x()+p.get_width()/2,h),
                    ha="center",va="bottom",fontsize=fs,fontweight="bold")

def load(data_dir, base_name):
    """Load CSV: tries exact name, then name with dataset suffix (e.g. _medium)."""
    stem = base_name.replace(".csv","")
    # 1. Exact match
    p = os.path.join(data_dir, base_name)
    if os.path.exists(p):
        return _read(p)
    # 2. Match with any suffix: host_energy_total_medium.csv etc.
    for f in os.listdir(data_dir):
        if f.startswith(stem+"_") and f.endswith(".csv"):
            return _read(os.path.join(data_dir, f))
    return None

def _read(path):
    try:
        df = pd.read_csv(path, sep=";")
        df.columns = [c.strip().lower() for c in df.columns]
        for col in ["link_policy","vm_policy","wf_policy"]:
            if col in df.columns:
                df[col] = df[col].replace({"DynLatBw":"BLA","dynlatbw":"BLA"})
        return df
    except: return None

# ── ENERGY FIGURES ────────────────────────────────────────────────────────────
def fig_energy_by_vm(df_e, d, ds):
    if df_e is None or "energy" not in df_e.columns: return
    gc=[c for c in ["link_policy","vm_policy","wf_policy","host_id"] if c in df_e.columns]
    df_l=df_e.sort_values("time").groupby(gc).last().reset_index() if "time" in df_e.columns else df_e
    df_s=(df_l.groupby(["link_policy","vm_policy","wf_policy"])["energy"].sum().reset_index()
          .groupby(["link_policy","vm_policy"])["energy"].mean().reset_index())
    df_s["vm_policy"]=pd.Categorical(df_s["vm_policy"],VM_ORDER)
    df_s=df_s.sort_values("vm_policy")
    fig,ax=plt.subplots(figsize=(10,6))
    sns.barplot(data=df_s,x="vm_policy",y="energy",hue="link_policy",palette=PALETTE,ax=ax,order=VM_ORDER,edgecolor="black",alpha=0.88)
    ann_bars(ax,"{:.0f} Wh",7)
    ax.set_title(f"Total Energy Consumption by VM Placement Policy — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("VM Allocation Policy"); ax.set_ylabel("Total Energy (Wh)")
    save(fig,os.path.join(d,"energy_by_vm"))

def fig_energy_all_policies(df_e, d, ds):
    if df_e is None or "energy" not in df_e.columns: return
    if "wf_policy" not in df_e.columns or "vm_policy" not in df_e.columns: return
    gc=[c for c in ["link_policy","vm_policy","wf_policy","host_id"] if c in df_e.columns]
    df_l=df_e.sort_values("time").groupby(gc).last().reset_index() if "time" in df_e.columns else df_e
    df_l["group"]=df_l["vm_policy"]+"\n"+df_l["wf_policy"]
    df_s=df_l.groupby(["link_policy","group"])["energy"].sum().reset_index()
    fig,ax=plt.subplots(figsize=(14,7))
    sns.barplot(data=df_s,x="group",y="energy",hue="link_policy",palette=PALETTE,ax=ax,edgecolor="black",alpha=0.88)
    ann_bars(ax,"{:.0f}",7)
    ax.set_title(f"Total Energy — All VM and Workload Scheduling Policies — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("Policy (VM / WF Scheduling)"); ax.set_ylabel("Energy (Wh)")
    plt.xticks(rotation=30,ha="right")
    save(fig,os.path.join(d,"energy_all_policies"))

def fig_energy_timeseries(df_e, d, ds):
    if df_e is None or "time" not in df_e.columns: return
    df_ts=(df_e.sort_values("time")
           .groupby(["link_policy","vm_policy","wf_policy","host_id","time"]).last().reset_index()
           .groupby(["time","link_policy"])["energy"].mean().reset_index())
    fig,ax=plt.subplots(figsize=(12,6))
    for pol,grp in df_ts.groupby("link_policy"):
        ax.plot(grp["time"],grp["energy"],label=pol,color=PALETTE.get(pol,"grey"),lw=2)
    ax.set_title(f"Cumulative Energy Consumption over Simulation Time — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("Simulation Time (s)"); ax.set_ylabel("Energy (Wh)")
    ax.legend(); ax.grid(True,alpha=0.3)
    save(fig,os.path.join(d,"energy_timeseries"))

# ── DELAY FIGURES ─────────────────────────────────────────────────────────────
def fig_delay_by_vm(df_pd, d, ds):
    if df_pd is None or "delay_ms" not in df_pd.columns: return
    fig,ax=plt.subplots(figsize=(10,6))
    sns.barplot(data=df_pd,x="vm_policy",y="delay_ms",hue="link_policy",palette=PALETTE,ax=ax,
                order=VM_ORDER,edgecolor="black",alpha=0.88,estimator=np.mean,errorbar=None)
    ann_bars(ax,"{:.0f} ms",7)
    ax.set_title(f"Average End-to-End Packet Delay by VM Policy — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("VM Placement Policy"); ax.set_ylabel("Avg Packet Delay (ms)")
    save(fig,os.path.join(d,"delay_by_vm"))

def fig_cdf_delay(df_pd, d, ds):
    if df_pd is None: return
    fig,axes=plt.subplots(1,2,figsize=(14,6))
    for ax,col,label in zip(axes,["delay_ms","queue_delay_ms"],["Total Delay (ms)","Queuing Delay (ms)"]):
        if col not in df_pd.columns: continue
        for pol,grp in df_pd.groupby("link_policy"):
            vals=grp[col].dropna().sort_values()
            ax.plot(vals.values,np.arange(len(vals))/len(vals),label=pol,lw=2.5,color=PALETTE.get(pol,"grey"))
        ax.set_title(label,fontsize=11,fontweight="bold")
        ax.set_xlabel(label); ax.set_ylabel("CDF: P(X <= x)")
        ax.legend(); ax.grid(True,alpha=0.3)
    fig.suptitle(f"Cumulative Distribution of Packet Delays — {ds}",fontsize=13,fontweight="bold")
    save(fig,os.path.join(d,"cdf_delay"))

def fig_delay_components(df_pd, d, ds):
    if df_pd is None: return
    ex=[c for c in ["proc_delay_ms","prop_delay_ms","trans_delay_ms","queue_delay_ms"] if c in df_pd.columns]
    if not ex: return
    df_c=(df_pd.groupby("link_policy")[ex].mean().reset_index()
          .melt(id_vars="link_policy",var_name="Component",value_name="ms"))
    df_c["Component"]=df_c["Component"].str.replace("_delay_ms","").str.replace("_"," ").str.capitalize()
    fig,ax=plt.subplots(figsize=(11,6))
    sns.barplot(data=df_c,x="Component",y="ms",hue="link_policy",palette=PALETTE,ax=ax,edgecolor="black",alpha=0.88)
    ann_bars(ax,"{:.0f}",8)
    ax.set_title(f"Breakdown of Delay Components (Processing, Propagation, Transmission, Queuing) — {ds}",fontsize=12,fontweight="bold")
    ax.set_xlabel("Delay Component"); ax.set_ylabel("Avg Delay (ms)")
    save(fig,os.path.join(d,"delay_components"))

def fig_boxplot_delay(df_pd, d, ds):
    if df_pd is None or "delay_ms" not in df_pd.columns: return
    fig,ax=plt.subplots(figsize=(10,6))
    sns.boxplot(data=df_pd[df_pd["link_policy"].isin(["BLA","First"])],x="vm_policy",y="delay_ms",
                hue="link_policy",palette=PALETTE,ax=ax,order=VM_ORDER,fliersize=2,linewidth=1.5)
    ax.set_title(f"Packet Delay Distribution by VM and Routing Policy — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("VM Placement Policy"); ax.set_ylabel("Packet Delay (ms)")
    save(fig,os.path.join(d,"boxplot_delay"))

def fig_queuing_delay(df_pd, d, ds):
    if df_pd is None or "queue_delay_ms" not in df_pd.columns: return
    fig,ax=plt.subplots(figsize=(10,6))
    sns.barplot(data=df_pd,x="vm_policy",y="queue_delay_ms",hue="link_policy",palette=PALETTE,ax=ax,
                order=VM_ORDER,edgecolor="black",alpha=0.88,estimator=np.mean,errorbar=None)
    ann_bars(ax,"{:.0f}",7)
    ax.set_title(f"Average Queuing Delay — Congestion Impact — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("VM Placement Policy"); ax.set_ylabel("Avg Queuing Delay (ms)")
    save(fig,os.path.join(d,"queuing_delay"))

def fig_vm_latency_impact(df_pd, d, ds):
    if df_pd is None or "delay_ms" not in df_pd.columns: return
    fig,ax=plt.subplots(figsize=(10,6))
    sns.boxplot(data=df_pd,x="vm_policy",y="delay_ms",palette=PAL_VM,ax=ax,order=VM_ORDER,fliersize=2)
    ax.set_title(f"Impact of VM Placement on End-to-End Latency — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("VM Placement Policy"); ax.set_ylabel("Packet Delay (ms)")
    save(fig,os.path.join(d,"vm_latency_impact"))

def fig_wf_latency_impact(df_pd, d, ds):
    if df_pd is None or "delay_ms" not in df_pd.columns or "wf_policy" not in df_pd.columns: return
    fig,ax=plt.subplots(figsize=(10,6))
    sns.boxplot(data=df_pd,x="wf_policy",y="delay_ms",palette="Set2",ax=ax,fliersize=2)
    ax.set_title(f"Impact of Workload Scheduling on End-to-End Latency — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("Workload Scheduling Policy"); ax.set_ylabel("Packet Delay (ms)")
    save(fig,os.path.join(d,"wf_latency_impact"))

def fig_wf_impact_dual(df_pd, d, ds):
    if df_pd is None or "wf_policy" not in df_pd.columns: return
    fig,axes=plt.subplots(1,2,figsize=(14,6))
    for ax,col,label in zip(axes,["delay_ms","queue_delay_ms"],["Packet Delay","Queuing Delay"]):
        if col not in df_pd.columns: continue
        sns.barplot(data=df_pd,x="wf_policy",y=col,hue="link_policy",palette=PALETTE,ax=ax,
                    edgecolor="black",alpha=0.88,estimator=np.mean,errorbar=None)
        ax.set_title(label,fontsize=11,fontweight="bold")
        ax.set_xlabel("Workload Scheduling Policy"); ax.set_ylabel(f"Avg {label} (ms)")
    fig.suptitle(f"Impact of Workload Scheduling Policy on Delay Metrics — {ds}",fontsize=13,fontweight="bold")
    save(fig,os.path.join(d,"wf_impact_dual"))

# ── HOST / VM / NETWORK FIGURES ───────────────────────────────────────────────
def fig_host_utilization(df_util, d, ds):
    if df_util is None: return
    df_u=df_util.rename(columns={"cpu_util":"CPU","ram_util":"RAM","bw_util":"BW","cpu":"CPU","ram":"RAM","bw":"BW"})
    metrics=[m for m in ["CPU","RAM","BW"] if m in df_u.columns]
    if not metrics: return
    id_cols=[c for c in ["link_policy","vm_policy"] if c in df_u.columns]
    df_m=df_u.melt(id_vars=id_cols,value_vars=metrics,var_name="Resource",value_name="Usage")
    df_m["Usage"]=pd.to_numeric(df_m["Usage"],errors="coerce")
    fig,ax=plt.subplots(figsize=(11,6))
    x_col="vm_policy" if "vm_policy" in df_m.columns else "Resource"
    sns.barplot(data=df_m,x=x_col,y="Usage",hue="link_policy",palette=PALETTE,ax=ax,
                edgecolor="black",alpha=0.88,estimator=np.mean,errorbar=None)
    ax.set_title(f"Average Host Resource Utilization (CPU / RAM / BW) — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel(x_col); ax.set_ylabel("Avg Utilization (%)")
    save(fig,os.path.join(d,"host_utilization"))

def fig_vm_cpu_impact(df_util, d, ds):
    if df_util is None: return
    df_u=df_util.rename(columns={"cpu_util":"CPU","cpu":"CPU"})
    if "CPU" not in df_u.columns or "vm_policy" not in df_u.columns: return
    fig,ax=plt.subplots(figsize=(10,6))
    order=[v for v in VM_ORDER if v in df_u["vm_policy"].unique()]
    sns.boxplot(data=df_u,x="vm_policy",y="CPU",palette=PAL_VM,ax=ax,order=order,fliersize=2)
    ax.set_title(f"Impact of VM Placement Policy on CPU Utilization — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("VM Placement Policy"); ax.set_ylabel("CPU Utilization (%)")
    save(fig,os.path.join(d,"vm_cpu_impact"))

def fig_cpu_ram_timeseries(df_util, d, ds):
    if df_util is None or "time" not in df_util.columns: return
    df_u=df_util.rename(columns={"cpu_util":"CPU (%)","ram_util":"RAM (%)","cpu":"CPU (%)","ram":"RAM (%)"})
    metrics=[m for m in ["CPU (%)","RAM (%)"] if m in df_u.columns]
    if not metrics or "link_policy" not in df_u.columns: return
    vms=df_u["vm_policy"].dropna().unique() if "vm_policy" in df_u.columns else ["all"]
    for vm in vms:
        sub=df_u[df_u["vm_policy"]==vm] if "vm_policy" in df_u.columns else df_u
        fig,axes=plt.subplots(1,len(metrics),figsize=(14,5))
        if len(metrics)==1: axes=[axes]
        for ax,m in zip(axes,metrics):
            for pol,grp in sub.groupby("link_policy"):
                ts=grp.groupby("time")[m].mean()
                ax.plot(ts.index,ts.values,label=pol,color=PALETTE.get(pol,"grey"),lw=2)
            ax.set_title(m,fontsize=11,fontweight="bold")
            ax.set_xlabel("Simulation Time (s)"); ax.set_ylabel(m)
            ax.legend(fontsize=9); ax.grid(True,alpha=0.3)
        fig.suptitle(f"CPU and RAM Utilization over Time — VM: {vm} — {ds}",fontsize=13,fontweight="bold")
        safe=str(vm).lower().replace(" ","_")
        save(fig,os.path.join(d,f"cpu_ram_timeseries_{safe}"))

def fig_link_utilization(df_lu, d, ds):
    if df_lu is None or "utilization" not in df_lu.columns: return
    if "link_id" not in df_lu.columns: return
    df_v=df_lu.groupby(["link_policy","link_id"])["utilization"].mean().reset_index().dropna(subset=["utilization"])
    if df_v.shape[0]<2: return
    fig,ax=plt.subplots(figsize=(10,6))
    pols=list(PALETTE.keys())
    for pol,color in PALETTE.items():
        sub=df_v[df_v["link_policy"]==pol]
        if sub.empty: continue
        ax.boxplot(sub["utilization"].values,positions=[pols.index(pol)],widths=0.4,patch_artist=True,
                   boxprops=dict(facecolor=color,alpha=0.75),medianprops=dict(color="black",linewidth=2),
                   whiskerprops=dict(color=color),capprops=dict(color=color),flierprops=dict(markerfacecolor=color,markersize=4))
    ax.set_xticks(range(len(pols))); ax.set_xticklabels(pols)
    ax.set_title(f"Link Utilization Distribution by Routing Policy — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("Link Selection Policy"); ax.set_ylabel("Link Utilization (%)")
    save(fig,os.path.join(d,"link_utilization"))

def fig_path_quality(df_path, d, ds):
    if df_path is None: return
    sel="selected" if "selected" in df_path.columns else None
    df_s=df_path[df_path[sel]==1] if sel else df_path
    lat=next((c for c in ["network_latency_ms","network_lat_ms"] if c in df_s.columns),None)
    bw=next((c for c in ["avg_pct_use","avg_bw_used"] if c in df_s.columns),None)
    if lat is None: return
    cols=[c for c in [lat,bw] if c is not None and c in df_s.columns]
    lbls=["Network Latency (ms)","BW Utilization (%)"][:len(cols)]
    fig,axes=plt.subplots(1,len(cols),figsize=(7*len(cols),6))
    if len(cols)==1: axes=[axes]
    for ax,col,lbl in zip(axes,cols,lbls):
        sns.boxplot(data=df_s,x="link_policy",y=col,hue="link_policy",palette=PALETTE,ax=ax,width=0.5,legend=False)
        ax.set_title(lbl,fontsize=11,fontweight="bold")
        ax.set_xlabel("Link Selection Policy"); ax.set_ylabel(lbl)
    fig.suptitle(f"Quality of Selected Paths (Network Latency and Bandwidth) — {ds}",fontsize=13,fontweight="bold")
    save(fig,os.path.join(d,"path_quality"))

def fig_network_latency_by_vm(df_path, d, ds):
    if df_path is None or "network_latency_ms" not in df_path.columns: return
    if "vm_policy" not in df_path.columns: return
    sel="selected" if "selected" in df_path.columns else None
    df_s=df_path[df_path[sel]==1].copy() if sel else df_path.copy()
    grp=df_s.groupby(["vm_policy","link_policy"])["network_latency_ms"].mean().reset_index()
    grp["vm_policy"]=pd.Categorical(grp["vm_policy"],VM_ORDER)
    grp=grp.sort_values("vm_policy")
    fig,axes=plt.subplots(1,2,figsize=(14,6))
    ax=axes[0]
    sns.barplot(data=grp,x="vm_policy",y="network_latency_ms",hue="link_policy",
                palette=PALETTE,ax=ax,order=VM_ORDER,edgecolor="black",alpha=0.88)
    for i,vm in enumerate(VM_ORDER):
        sub=grp[grp["vm_policy"]==vm]
        bv=sub[sub["link_policy"]=="BLA"]["network_latency_ms"].values
        fv=sub[sub["link_policy"]=="First"]["network_latency_ms"].values
        if len(bv) and len(fv) and fv[0]>0:
            ax.annotate(f"-{abs((fv[0]-bv[0])/fv[0]*100):.1f}%",xy=(i,max(bv[0],fv[0])*1.08),
                        ha="center",fontsize=11,fontweight="bold",color="#2ca02c")
    ax.set_title("Network Latency by VM Placement Policy",fontsize=12,fontweight="bold")
    ax.set_xlabel("VM Placement Policy"); ax.set_ylabel("Network Latency (ms)")
    ax2=axes[1]
    df_no=df_s[df_s["vm_policy"]!="MFF"].copy()
    order2=[v for v in ["LWFF","LFF"] if v in df_no["vm_policy"].unique()]
    if not df_no.empty and order2:
        sns.boxplot(data=df_no,x="vm_policy",y="network_latency_ms",hue="link_policy",
                    palette=PALETTE,ax=ax2,order=order2,width=0.5,linewidth=1.5,fliersize=3)
        ax2.set_title("Network Latency Distribution (MFF excluded — latency=0)",fontsize=12,fontweight="bold")
        ax2.set_xlabel("VM Placement Policy"); ax2.set_ylabel("Network Latency (ms)")
    fig.suptitle(f"Pure Network Latency of Selected Paths — {ds}",fontsize=13,fontweight="bold")
    save(fig,os.path.join(d,"network_latency_by_vm"))

# ── ANALYTICAL FIGURES ────────────────────────────────────────────────────────
def fig_pareto(df_e, df_pd, d, ds):
    if df_e is None or df_pd is None: return
    gc=[c for c in ["link_policy","vm_policy","wf_policy","host_id"] if c in df_e.columns]
    df_el=df_e.sort_values("time").groupby(gc).last().reset_index() if "time" in df_e.columns else df_e
    df_ea=df_el.groupby(["link_policy","vm_policy"])["energy"].mean().reset_index()
    df_da=df_pd.groupby(["link_policy","vm_policy"])["delay_ms"].mean().reset_index()
    df_m=df_ea.merge(df_da,on=["link_policy","vm_policy"])
    fig,ax=plt.subplots(figsize=(10,7))
    for _,row in df_m.iterrows():
        c=PALETTE.get(row["link_policy"],"grey")
        m={"MFF":"o","LWFF":"s","LFF":"D"}.get(row["vm_policy"],"^")
        ax.scatter(row["energy"],row["delay_ms"],c=c,marker=m,s=180,edgecolors="black",zorder=5)
        ax.annotate(f"{row['vm_policy']}\n{row['link_policy']}",
                    (row["energy"],row["delay_ms"]),textcoords="offset points",xytext=(6,4),fontsize=8)
    lp=[mpatches.Patch(color=c,label=l) for l,c in PALETTE.items()]
    vm=[plt.Line2D([0],[0],marker=m,color="w",markerfacecolor="grey",markersize=10,label=v)
        for v,m in [("MFF","o"),("LWFF","s"),("LFF","D")]]
    ax.legend(handles=lp+vm,fontsize=9,framealpha=0.9)
    ax.set_xlabel("Total Energy Consumed (Wh)"); ax.set_ylabel("Avg Packet Delay (ms)")
    ax.set_title(f"Energy vs. Latency Tradeoff (Pareto View) — {ds}",fontsize=13,fontweight="bold")
    ax.grid(True,alpha=0.4)
    save(fig,os.path.join(d,"pareto_energy_delay"))

def fig_bla_gain(df_pd, df_e, d, ds):
    if df_pd is None: return
    records=[]
    for vm in VM_ORDER:
        sub=df_pd[df_pd["vm_policy"]==vm] if "vm_policy" in df_pd.columns else df_pd
        for metric,col in [("Delay","delay_ms"),("Queuing","queue_delay_ms"),("Transmission","trans_delay_ms")]:
            if col not in sub.columns: continue
            fv=sub[sub["link_policy"]=="First"][col].mean()
            bv=sub[sub["link_policy"]=="BLA"][col].mean()
            if fv>0: records.append({"VM Policy":vm,"Metric":metric,"BLA Gain (%)":(fv-bv)/fv*100})
    if not records: return
    df_g=pd.DataFrame(records)
    fig,ax=plt.subplots(figsize=(11,6))
    sns.barplot(data=df_g,x="VM Policy",y="BLA Gain (%)",hue="Metric",ax=ax,edgecolor="black",alpha=0.88)
    ax.axhline(0,color="black",linewidth=1)
    ax.set_title(f"BLA Routing Gain over First-Fit (%) by VM Policy — {ds}",fontsize=13,fontweight="bold")
    ax.set_ylabel("Reduction (%) — positive = BLA is better")
    save(fig,os.path.join(d,"bla_gain_summary"))

def fig_sla_violations(df_sla, d, ds):
    if df_sla is None or "link_policy" not in df_sla.columns: return
    gc=["link_policy","vm_policy"] if "vm_policy" in df_sla.columns else ["link_policy"]
    df_c=df_sla.groupby(gc).size().reset_index(name="violations")
    fig,ax=plt.subplots(figsize=(10,6))
    if "vm_policy" in gc:
        sns.barplot(data=df_c,x="vm_policy",y="violations",hue="link_policy",palette=PALETTE,ax=ax,order=VM_ORDER,edgecolor="black",alpha=0.88)
    else:
        sns.barplot(data=df_c,x="link_policy",y="violations",palette=PALETTE,ax=ax,edgecolor="black")
    ann_bars(ax,"{:.0f}",8)
    ax.set_title(f"Number of SLA Violations by Routing and VM Policy — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("VM Placement Policy"); ax.set_ylabel("SLA Violations Count")
    save(fig,os.path.join(d,"sla_violations"))

def fig_sla_severity(df_pd, d, ds):
    if df_pd is None or "delay_ms" not in df_pd.columns or "proc_delay_ms" not in df_pd.columns: return
    df_pd=df_pd.copy()
    df_pd["sla_sev"]=df_pd["delay_ms"]/df_pd["proc_delay_ms"].replace(0,1)
    fig,ax=plt.subplots(figsize=(10,6))
    sns.barplot(data=df_pd,x="vm_policy",y="sla_sev",hue="link_policy",palette=PALETTE,ax=ax,order=VM_ORDER,edgecolor="black",alpha=0.88)
    ann_bars(ax)
    ax.set_title(f"SLA Violation Severity (Delay / Processing Ratio) — {ds}",fontsize=13,fontweight="bold")
    ax.set_xlabel("VM Placement Policy"); ax.set_ylabel("Severity Ratio (Delay / Proc)")
    save(fig,os.path.join(d,"sla_severity"))

def fig_heatmap_delay(df_pd, d, ds):
    if df_pd is None or "delay_ms" not in df_pd.columns or "wf_policy" not in df_pd.columns: return
    pivot=(df_pd[df_pd["link_policy"]=="BLA"].groupby(["vm_policy","wf_policy"])["delay_ms"].mean().unstack(fill_value=0))
    if pivot.empty: return
    fig,ax=plt.subplots(figsize=(9,6))
    sns.heatmap(pivot,annot=True,fmt=".0f",cmap="RdYlGn_r",ax=ax,linewidths=0.5,cbar_kws={"label":"Avg Delay (ms)"})
    ax.set_title(f"Delay Heatmap for BLA Routing — VM Policy x Workload Scheduling — {ds}",fontsize=12,fontweight="bold")
    save(fig,os.path.join(d,"heatmap_delay_bla"))

def fig_scalability_intra(df_pd, df_e, d, ds):
    rows=[]
    for pol in ["First","BLA"]:
        rec={"Policy":pol}
        if df_pd is not None and "delay_ms" in df_pd.columns:
            sub=df_pd[df_pd["link_policy"]==pol]
            rec["Avg Delay (ms)"]=sub["delay_ms"].mean() if not sub.empty else np.nan
            if "queue_delay_ms" in sub.columns:
                rec["Avg Queuing Delay (ms)"]=sub["queue_delay_ms"].mean() if not sub.empty else np.nan
        if df_e is not None and "energy" in df_e.columns:
            sub=df_e[df_e["link_policy"]==pol]
            if not sub.empty:
                gc=[c for c in ["vm_policy","wf_policy","host_id"] if c in sub.columns]
                last=sub.sort_values("time").groupby(gc).last() if gc and "time" in sub.columns else sub
                rec["Total Energy (Wh)"]=last["energy"].sum()
        rows.append(rec)
    df_sc=pd.DataFrame(rows)
    cols=[c for c in ["Avg Delay (ms)","Avg Queuing Delay (ms)","Total Energy (Wh)"] if c in df_sc.columns]
    if not cols: return
    fig,axes=plt.subplots(1,len(cols),figsize=(6*len(cols),6))
    if len(cols)==1: axes=[axes]
    for ax,col in zip(axes,cols):
        colors=[PALETTE.get(p,"grey") for p in df_sc["Policy"]]
        bars=ax.bar(df_sc["Policy"],df_sc[col],color=colors,edgecolor="black",alpha=0.88)
        for bar,val in zip(bars,df_sc[col]):
            if not np.isnan(val):
                ax.annotate(f"{val:,.0f}",(bar.get_x()+bar.get_width()/2,val),ha="center",va="bottom",fontsize=9,fontweight="bold")
        fv=df_sc[df_sc["Policy"]=="First"][col].values
        bv=df_sc[df_sc["Policy"]=="BLA"][col].values
        if len(fv) and len(bv) and fv[0]>0 and not np.isnan(fv[0]):
            gain=(fv[0]-bv[0])/fv[0]*100
            sign="-" if gain>=0 else "+"
            ax.set_title(f"{col}\nBLA Gain: {sign}{abs(gain):.1f}%",fontsize=11,fontweight="bold")
        else: ax.set_title(col,fontsize=11,fontweight="bold")
        ax.set_xlabel("Routing Policy")
    fig.suptitle(f"BLA vs. First-Fit Routing — Summary Metrics — {ds}",fontsize=14,fontweight="bold")
    save(fig,os.path.join(d,"scalability_analysis"))

# ── CONSOLIDATED CROSS-DATASET FIGURES ────────────────────────────────────────
def fig_consolidated_scalability(all_data, out_dir):
    """Energy and Delay across datasets (scalability view)."""
    rows=[]
    for ds_name,(df_e,df_pd,_,_,_,_) in all_data.items():
        for pol in ["First","BLA"]:
            rec={"Dataset":ds_name,"Policy":pol}
            if df_pd is not None and "delay_ms" in df_pd.columns:
                sub=df_pd[df_pd["link_policy"]==pol]
                rec["Avg Delay (ms)"]=sub["delay_ms"].mean()
                if "queue_delay_ms" in sub.columns:
                    rec["Avg Queuing Delay (ms)"]=sub["queue_delay_ms"].mean()
            if df_e is not None and "energy" in df_e.columns:
                sub=df_e[df_e["link_policy"]==pol]
                if not sub.empty:
                    gc=[c for c in ["vm_policy","wf_policy","host_id"] if c in sub.columns]
                    last=sub.sort_values("time").groupby(gc).last() if gc and "time" in sub.columns else sub
                    rec["Total Energy (Wh)"]=last["energy"].sum()
            rows.append(rec)
    if not rows: return
    df=pd.DataFrame(rows)
    metrics=[c for c in ["Avg Delay (ms)","Avg Queuing Delay (ms)","Total Energy (Wh)"] if c in df.columns]
    fig,axes=plt.subplots(1,len(metrics),figsize=(7*len(metrics),6))
    if len(metrics)==1: axes=[axes]
    ds_order=["dataset-mini","dataset-small","dataset-medium","dataset-large"]
    ds_present=[d for d in ds_order if d in df["Dataset"].unique()]
    short={d:d.replace("dataset-","").capitalize() for d in ds_present}
    df["DS_short"]=df["Dataset"].map(short)
    ds_short=[short[d] for d in ds_present]
    for ax,metric in zip(axes,metrics):
        for pol,color in PALETTE.items():
            sub=df[df["Policy"]==pol].set_index("Dataset")
            vals=[sub.loc[d,metric] if d in sub.index and not pd.isna(sub.loc[d,metric]) else np.nan for d in ds_present]
            ax.plot(ds_short,vals,"o-",label=pol,color=color,lw=2.5,markersize=8,markeredgecolor="black")
        ax.set_title(metric,fontsize=12,fontweight="bold")
        ax.set_xlabel("Dataset Scale"); ax.set_ylabel(metric)
        ax.legend(fontsize=10); ax.grid(True,alpha=0.4)
    fig.suptitle("Scalability Analysis — BLA vs. First-Fit across Dataset Scales",fontsize=14,fontweight="bold")
    save(fig,os.path.join(out_dir,"consolidated_scalability"))

def fig_consolidated_bla_gain(all_data, out_dir):
    """BLA gain % across datasets."""
    rows=[]
    for ds_name,(df_e,df_pd,_,_,_,_) in all_data.items():
        if df_pd is None or "delay_ms" not in df_pd.columns: continue
        for metric,col in [("Delay","delay_ms"),("Queuing","queue_delay_ms")]:
            if col not in df_pd.columns: continue
            fv=df_pd[df_pd["link_policy"]=="First"][col].mean()
            bv=df_pd[df_pd["link_policy"]=="BLA"][col].mean()
            if fv>0:
                rows.append({"Dataset":ds_name.replace("dataset-","").capitalize(),
                             "Metric":metric,"BLA Gain (%)":(fv-bv)/fv*100})
    if not rows: return
    df=pd.DataFrame(rows)
    fig,ax=plt.subplots(figsize=(11,6))
    sns.barplot(data=df,x="Dataset",y="BLA Gain (%)",hue="Metric",ax=ax,edgecolor="black",alpha=0.88,
                order=[d.replace("dataset-","").capitalize() for d in ["dataset-mini","dataset-small","dataset-medium","dataset-large"] if d.replace("dataset-","").capitalize() in df["Dataset"].unique()])
    ax.axhline(0,color="black",linewidth=1)
    ax.set_title("BLA Routing Gain over First-Fit (%) across Dataset Scales",fontsize=13,fontweight="bold")
    ax.set_xlabel("Dataset Scale"); ax.set_ylabel("Reduction (%) — positive = BLA better")
    save(fig,os.path.join(out_dir,"consolidated_bla_gain"))

def fig_consolidated_energy(all_data, out_dir):
    """Energy comparison across datasets."""
    rows=[]
    for ds_name,(df_e,_,_,_,_,_) in all_data.items():
        if df_e is None or "energy" not in df_e.columns: continue
        for pol in ["First","BLA"]:
            sub=df_e[df_e["link_policy"]==pol]
            if sub.empty: continue
            gc=[c for c in ["vm_policy","wf_policy","host_id"] if c in sub.columns]
            last=sub.sort_values("time").groupby(gc).last() if gc and "time" in sub.columns else sub
            rows.append({"Dataset":ds_name.replace("dataset-","").capitalize(),"Policy":pol,"Energy (Wh)":last["energy"].sum()})
    if not rows: return
    df=pd.DataFrame(rows)
    fig,ax=plt.subplots(figsize=(10,6))
    sns.barplot(data=df,x="Dataset",y="Energy (Wh)",hue="Policy",palette=PALETTE,ax=ax,edgecolor="black",alpha=0.88)
    ann_bars(ax,"{:.0f} Wh",7)
    ax.set_title("Total Energy Consumption across Dataset Scales",fontsize=13,fontweight="bold")
    ax.set_xlabel("Dataset Scale"); ax.set_ylabel("Total Energy (Wh)")
    save(fig,os.path.join(out_dir,"consolidated_energy"))

def fig_consolidated_delay(all_data, out_dir):
    """Avg delay comparison across datasets."""
    rows=[]
    for ds_name,(_, df_pd,_,_,_,_) in all_data.items():
        if df_pd is None or "delay_ms" not in df_pd.columns: continue
        for pol in ["First","BLA"]:
            sub=df_pd[df_pd["link_policy"]==pol]
            rows.append({"Dataset":ds_name.replace("dataset-","").capitalize(),"Policy":pol,"Avg Delay (ms)":sub["delay_ms"].mean()})
    if not rows: return
    df=pd.DataFrame(rows)
    fig,ax=plt.subplots(figsize=(10,6))
    sns.barplot(data=df,x="Dataset",y="Avg Delay (ms)",hue="Policy",palette=PALETTE,ax=ax,edgecolor="black",alpha=0.88)
    ann_bars(ax,"{:.0f} ms",7)
    ax.set_title("Average End-to-End Packet Delay across Dataset Scales",fontsize=13,fontweight="bold")
    ax.set_xlabel("Dataset Scale"); ax.set_ylabel("Avg Packet Delay (ms)")
    save(fig,os.path.join(out_dir,"consolidated_delay"))

def fig_consolidated_pareto(all_data, out_dir):
    """Cross-dataset pareto: energy vs delay per dataset."""
    rows=[]
    for ds_name,(df_e,df_pd,_,_,_,_) in all_data.items():
        if df_e is None or df_pd is None: continue
        for pol in ["First","BLA"]:
            e_sub=df_e[df_e["link_policy"]==pol]
            p_sub=df_pd[df_pd["link_policy"]==pol]
            if e_sub.empty or p_sub.empty: continue
            gc=[c for c in ["vm_policy","wf_policy","host_id"] if c in e_sub.columns]
            last=e_sub.sort_values("time").groupby(gc).last() if gc and "time" in e_sub.columns else e_sub
            rows.append({"Dataset":ds_name,"Policy":pol,"Energy (Wh)":last["energy"].sum(),"Avg Delay (ms)":p_sub["delay_ms"].mean()})
    if not rows: return
    df=pd.DataFrame(rows)
    fig,ax=plt.subplots(figsize=(11,7))
    for ds_name in df["Dataset"].unique():
        c=DS_PALETTE.get(ds_name,"grey")
        for pol in ["First","BLA"]:
            sub=df[(df["Dataset"]==ds_name)&(df["Policy"]==pol)]
            if sub.empty: continue
            m="o" if pol=="BLA" else "X"
            ax.scatter(sub["Energy (Wh)"],sub["Avg Delay (ms)"],c=c,marker=m,s=200,edgecolors="black",zorder=5)
            ax.annotate(f"{ds_name.replace('dataset-','')}\n{pol}",(sub["Energy (Wh)"].values[0],sub["Avg Delay (ms)"].values[0]),
                        textcoords="offset points",xytext=(5,3),fontsize=7.5)
    ds_h=[mpatches.Patch(color=DS_PALETTE.get(d,"grey"),label=d.replace("dataset-","").capitalize()) for d in df["Dataset"].unique()]
    pol_h=[plt.Line2D([0],[0],marker=m,color="w",markerfacecolor="grey",markersize=11,label=l) for l,m in [("BLA","o"),("First","X")]]
    ax.legend(handles=ds_h+pol_h,fontsize=9,framealpha=0.9)
    ax.set_xlabel("Total Energy Consumed (Wh)"); ax.set_ylabel("Avg Packet Delay (ms)")
    ax.set_title("Cross-Dataset Energy vs. Latency Tradeoff (Pareto View)",fontsize=13,fontweight="bold")
    ax.grid(True,alpha=0.4)
    save(fig,os.path.join(out_dir,"consolidated_pareto"))

# ── ORCHESTRATOR ──────────────────────────────────────────────────────────────
def run_dataset(ds_path, ds_name, only=None):
    sd=os.path.join(ds_path,"synthese","data")
    if not os.path.exists(sd):
        print(f"  [SKIP] No synthese/data in {ds_path}"); return None
    pd_dir=os.path.join(ds_path,"plot"); os.makedirs(pd_dir,exist_ok=True)
    df_e    = load(sd,"host_energy_total.csv")
    df_pd   = load(sd,"packet_delays.csv")
    df_util = load(sd,"host_utilization.csv")
    df_lu   = load(sd,"link_utilization_up.csv")
    df_path = load(sd,"path_latency_final.csv")
    df_sla  = load(sd,"qos_violations.csv")
    figs={
        "energy_by_vm":         lambda: fig_energy_by_vm(df_e,pd_dir,ds_name),
        "energy_all_policies":  lambda: fig_energy_all_policies(df_e,pd_dir,ds_name),
        "energy_timeseries":    lambda: fig_energy_timeseries(df_e,pd_dir,ds_name),
        "delay_by_vm":          lambda: fig_delay_by_vm(df_pd,pd_dir,ds_name),
        "cdf_delay":            lambda: fig_cdf_delay(df_pd,pd_dir,ds_name),
        "delay_components":     lambda: fig_delay_components(df_pd,pd_dir,ds_name),
        "boxplot_delay":        lambda: fig_boxplot_delay(df_pd,pd_dir,ds_name),
        "queuing_delay":        lambda: fig_queuing_delay(df_pd,pd_dir,ds_name),
        "vm_latency_impact":    lambda: fig_vm_latency_impact(df_pd,pd_dir,ds_name),
        "wf_latency_impact":    lambda: fig_wf_latency_impact(df_pd,pd_dir,ds_name),
        "wf_impact_dual":       lambda: fig_wf_impact_dual(df_pd,pd_dir,ds_name),
        "host_utilization":     lambda: fig_host_utilization(df_util,pd_dir,ds_name),
        "vm_cpu_impact":        lambda: fig_vm_cpu_impact(df_util,pd_dir,ds_name),
        "cpu_ram_timeseries":   lambda: fig_cpu_ram_timeseries(df_util,pd_dir,ds_name),
        "link_utilization":     lambda: fig_link_utilization(df_lu,pd_dir,ds_name),
        "path_quality":         lambda: fig_path_quality(df_path,pd_dir,ds_name),
        "network_latency_by_vm":lambda: fig_network_latency_by_vm(df_path,pd_dir,ds_name),
        "pareto":               lambda: fig_pareto(df_e,df_pd,pd_dir,ds_name),
        "bla_gain":             lambda: fig_bla_gain(df_pd,df_e,pd_dir,ds_name),
        "sla_violations":       lambda: fig_sla_violations(df_sla,pd_dir,ds_name),
        "sla_severity":         lambda: fig_sla_severity(df_pd,pd_dir,ds_name),
        "heatmap_delay":        lambda: fig_heatmap_delay(df_pd,pd_dir,ds_name),
        "scalability_intra":    lambda: fig_scalability_intra(df_pd,df_e,pd_dir,ds_name),
    }
    to_run={k:v for k,v in figs.items() if only is None or k in only}
    print(f"  Generating {len(to_run)} figures for {ds_name} ...")
    ok=fail=0
    for name,fn in to_run.items():
        try: fn(); print(f"    [OK] {name}"); ok+=1
        except Exception as ex: print(f"    [WARN] {name}: {ex}"); fail+=1
    print(f"  Done: {ok} OK, {fail} failed -> {pd_dir}")
    return (df_e,df_pd,df_util,df_lu,df_path,df_sla)

def main():
    p=argparse.ArgumentParser(description="RAS-SDNCloudSim -- Final Unified Plot Generator")
    p.add_argument("results_dir")
    p.add_argument("--dataset",default=None)
    p.add_argument("--figs",default=None)
    p.add_argument("--no-consolidated",action="store_true",help="Skip cross-dataset consolidated figures")
    args=p.parse_args()
    if not os.path.isdir(args.results_dir):
        print(f"[ERR] Not found: {args.results_dir}"); return
    only=set(args.figs.split(",")) if args.figs else None
    datasets=[d for d in sorted(os.listdir(args.results_dir))
              if os.path.isdir(os.path.join(args.results_dir,d)) and d.startswith("dataset")]
    if args.dataset:
        datasets=[d for d in datasets if d==args.dataset]
    if not datasets:
        print(f"[ERR] No datasets in {args.results_dir}"); return
    print(f"\n{'='*60}\n  Final Plot Generator -- {len(datasets)} dataset(s)\n{'='*60}")
    all_data={}
    for ds in datasets:
        print(f"\n--- {ds} ---")
        result=run_dataset(os.path.join(args.results_dir,ds),ds,only)
        if result is not None: all_data[ds]=result
    if not args.no_consolidated and len(all_data)>1 and only is None:
        out=os.path.join(args.results_dir,"global_analysis","plot_consolidated")
        os.makedirs(out,exist_ok=True)
        print(f"\n--- Cross-dataset consolidated figures -> {out} ---")
        for name,fn in [("scalability",lambda:fig_consolidated_scalability(all_data,out)),
                        ("bla_gain",lambda:fig_consolidated_bla_gain(all_data,out)),
                        ("energy",lambda:fig_consolidated_energy(all_data,out)),
                        ("delay",lambda:fig_consolidated_delay(all_data,out)),
                        ("pareto",lambda:fig_consolidated_pareto(all_data,out))]:
            try: fn(); print(f"  [OK] consolidated_{name}")
            except Exception as ex: print(f"  [WARN] consolidated_{name}: {ex}")
    print("\n[DONE] All plots generated.")

if __name__=="__main__":
    main()
