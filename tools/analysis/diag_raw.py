import pandas as pd

col = ['pkt_id','src','dest','psize','delay_ms','proc_delay_ms','prop_delay_ms','trans_delay_ms','queue_delay_ms']
col_sla = ['time','flowId','violationType']

print("=== PER-EXPERIMENT: First vs BLA/DynLatBw (LFF, Priority) ===")
for ds in ['dataset-small','dataset-medium','dataset-large-congested']:
    # Large uses DynLatBw, others use BLA in folder name
    import glob
    base = f'results/2026-04-27/{ds}/raw/LFF'
    f_path = glob.glob(f'{base}/experiment_LFF_First_Priority')[0] if glob.glob(f'{base}/experiment_LFF_First_Priority') else None
    b_path = (glob.glob(f'{base}/experiment_LFF_BLA_Priority') or glob.glob(f'{base}/experiment_LFF_DynLatBw_Priority'))
    b_path = b_path[0] if b_path else None
    
    if not f_path or not b_path:
        print(f"  [{ds}] MISSING PATH: first={f_path}, bla={b_path}")
        continue

    df_f = pd.read_csv(f'{f_path}/packet_delays.csv', sep=';', comment='#', header=None, names=col)
    df_b = pd.read_csv(f'{b_path}/packet_delays.csv', sep=';', comment='#', header=None, names=col)
    f_dedup = df_f.drop_duplicates()
    b_dedup = df_b.drop_duplicates()
    f_sla = pd.read_csv(f'{f_path}/qos_violations.csv', sep=';', comment='#', header=None, names=col_sla)
    b_sla = pd.read_csv(f'{b_path}/qos_violations.csv', sep=';', comment='#', header=None, names=col_sla)
    
    dup_ratio_f = len(df_f) / len(f_dedup) if len(f_dedup)>0 else 0
    print(f"\n  [{ds}]  (bla_path: {b_path.split('/')[-1]})")
    print(f"  First delay: {df_f['delay_ms'].mean():.1f} ms raw | {f_dedup['delay_ms'].mean():.1f} ms dedup | rows: {len(df_f)} -> {len(f_dedup)} (x{dup_ratio_f:.1f} duplicates)")
    print(f"  BLA   delay: {df_b['delay_ms'].mean():.1f} ms raw | {b_dedup['delay_ms'].mean():.1f} ms dedup | rows: {len(df_b)} -> {len(b_dedup)}")
    print(f"  First queue: {df_f['queue_delay_ms'].mean():.1f} ms | BLA queue: {df_b['queue_delay_ms'].mean():.1f} ms")
    print(f"  Max delay First: {df_f['delay_ms'].max():.1f} ms | Max BLA: {df_b['delay_ms'].max():.1f} ms")
    print(f"  First SLA: {len(f_sla)} | BLA SLA: {len(b_sla)} | Ratio: {len(f_sla)/len(df_f)*100:.1f}% violation rate")
    print(f"  Packet files identical: {df_f.equals(df_b)}")
    
print("\n=== DUPLICATION ROOT CAUSE: checking pkt_id uniqueness ===")
for ds in ['dataset-medium']:
    base = f'results/2026-04-27/{ds}/raw/LFF/experiment_LFF_First_Priority'
    df = pd.read_csv(f'{base}/packet_delays.csv', sep=';', comment='#', header=None, names=col)
    print(f"  Unique pkt_ids: {df['pkt_id'].nunique()} | Total rows: {len(df)} -> each packet logged ~{len(df)/df['pkt_id'].nunique():.1f}x")
    print(f"  Unique (pkt_id, src, dest) combos: {df[['pkt_id','src','dest']].drop_duplicates().shape[0]}")
    print(f"  Sample pkt_id 7 rows: {len(df[df['pkt_id']==7])}")
