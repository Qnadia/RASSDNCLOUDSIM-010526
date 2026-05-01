import pandas as pd

for ds in ['dataset-small', 'dataset-medium', 'dataset-large-congested']:
    p = f'results/2026-04-27/{ds}/synthese/data'
    print(f'\n=== {ds.upper()} ===')
    
    # Packet delays
    try:
        df = pd.read_csv(f'{p}/packet_delays.csv', sep=';')
        df['link_policy'] = df['link_policy'].replace('DynLatBw', 'BLA')
        print('  PACKET DELAY (mean ms):')
        print(df.groupby('link_policy')[['delay_ms', 'queue_delay_ms']].mean().to_string())
        print(f'  Total packets: {len(df)}, unique experiments: {df["vm_policy"].nunique() * df["wf_policy"].nunique()}')
    except Exception as e:
        print(f'  [ERR delay] {e}')
    
    # Energy
    try:
        df_e = pd.read_csv(f'{p}/host_energy_total.csv', sep=';')
        df_e.columns = [c.strip().lower() for c in df_e.columns]
        df_e['link_policy'] = df_e['link_policy'].replace('DynLatBw', 'BLA')
        df_last = df_e.sort_values('time').groupby(['link_policy','vm_policy','wf_policy','host_id']).last().reset_index()
        df_last['scenario'] = df_last['vm_policy'] + '_' + df_last['wf_policy']
        energy_sc = df_last.groupby(['link_policy','scenario'])['energy'].sum().reset_index()
        print('  ENERGY Wh (mean/min/max per link_policy across scenarios):')
        print(energy_sc.groupby('link_policy')['energy'].agg(['mean','min','max']).to_string())
        n_hosts = df_last['host_id'].nunique()
        n_scenarios = df_last['scenario'].nunique()
        print(f'  Unique hosts: {n_hosts}, Unique scenarios: {n_scenarios}')
    except Exception as e:
        print(f'  [ERR energy] {e}')
    
    # SLA
    try:
        df_s = pd.read_csv(f'{p}/qos_violations.csv', sep=';')
        df_s['link_policy'] = df_s['link_policy'].replace('DynLatBw', 'BLA')
        print('  SLA violations total per policy:')
        print(df_s.groupby('link_policy').size().to_string())
        if len(df_s.columns) > 3:
            col = df_s.columns[3]
            print(f'  Violation types ({col}): {df_s[col].unique()}')
    except Exception as e:
        print(f'  [ERR sla] {e}')
