import pandas as pd

col_util = ['time','host_id','cpu_util','ram_util','bw_util','energy']
col_sla  = ['time','flowId','violationType']

print("=" * 70)
print("EXPERT DIAGNOSTIC: Host Utilization & SLA - First vs BLA")
print("=" * 70)

for ds in ['dataset-small','dataset-medium','dataset-large-congested']:
    import glob
    base = f'results/2026-04-27/{ds}/raw/LFF'
    f_dir = f'{base}/experiment_LFF_First_Priority'
    b_dirs = glob.glob(f'{base}/experiment_LFF_BLA_Priority') + glob.glob(f'{base}/experiment_LFF_DynLatBw_Priority')
    b_dir = b_dirs[0] if b_dirs else None
    if not b_dir:
        continue

    print(f"\n{'='*60}")
    print(f" DATASET: {ds}")
    print(f"{'='*60}")

    # --- HOST UTILIZATION ---
    df_f = pd.read_csv(f'{f_dir}/host_utilization.csv', sep=';', comment='#', header=None, names=col_util)
    df_b = pd.read_csv(f'{b_dir}/host_utilization.csv', sep=';', comment='#', header=None, names=col_util)
    
    identical = df_f.equals(df_b)
    
    print(f"\n  [HOST UTILIZATION] Files identical: {identical}")
    for label, df in [('First', df_f), ('BLA', df_b)]:
        n_hosts = df['host_id'].nunique()
        n_times = df['time'].nunique()
        print(f"\n  {label} ({n_hosts} hosts x {n_times} timestamps = {len(df)} rows):")
        print(f"    CPU  -> mean: {df['cpu_util'].mean():.2f}%  max: {df['cpu_util'].max():.2f}%  std: {df['cpu_util'].std():.2f}")
        print(f"    RAM  -> mean: {df['ram_util'].mean():.2f}%  max: {df['ram_util'].max():.2f}%  std: {df['ram_util'].std():.2f}")
        print(f"    BW   -> mean: {df['bw_util'].mean():.2f}%  max: {df['bw_util'].max():.2f}%  std: {df['bw_util'].std():.2f}")
        print(f"    Nrg  -> mean: {df['energy'].mean():.4f} Wh  max: {df['energy'].max():.4f} Wh")

    # CPU/RAM/BW differences
    diff_cpu = (df_f['cpu_util'] - df_b['cpu_util']).abs().mean()
    diff_ram = (df_f['ram_util'] - df_b['ram_util']).abs().mean()
    diff_bw  = (df_f['bw_util']  - df_b['bw_util']).abs().mean()
    diff_nrg = (df_f['energy']   - df_b['energy']).abs().mean()
    print(f"\n  |First - BLA| mean absolute diff:")
    print(f"    CPU={diff_cpu:.4f}%  RAM={diff_ram:.4f}%  BW={diff_bw:.4f}%  Energy={diff_nrg:.6f} Wh")

    # --- SLA ---
    df_sla_f = pd.read_csv(f'{f_dir}/qos_violations.csv', sep=';', comment='#', header=None, names=col_sla)
    df_sla_b = pd.read_csv(f'{b_dir}/qos_violations.csv', sep=';', comment='#', header=None, names=col_sla)
    print(f"\n  [SLA] First: {len(df_sla_f)} violations | BLA: {len(df_sla_b)} violations")
    print(f"  [SLA] Types: {df_sla_f['violationType'].unique()}")
    
    # Vérifier si le flowId varie (chercher différences réelles)
    common_flows = set(df_sla_f['flowId'].unique()) & set(df_sla_b['flowId'].unique())
    only_first = set(df_sla_f['flowId'].unique()) - set(df_sla_b['flowId'].unique())
    only_bla   = set(df_sla_b['flowId'].unique()) - set(df_sla_f['flowId'].unique())
    print(f"  [SLA] Flows violating ONLY in First: {len(only_first)} | ONLY in BLA: {len(only_bla)} | Both: {len(common_flows)}")

print("\n" + "="*70)
print("CONCLUSION:")
print("  - Si host_utilization identique -> le monitoring ne varie PAS avec le routage")
print("  - Si SLA identique -> 100% saturation ou log pre-agrege")
print("  - Si diff BW ~ 0% mais diff delay > 0 -> BW loggué au niveau host (pas link)")
