import os
import glob
import pandas as pd

base_dir = 'results/2026-04-22'
results = []

for ds in ['dataset-small', 'dataset-medium', 'dataset-large', 'dataset-heterogeneous']:
    for vm in ['LFF', 'LWFF', 'MFF']:
        for rt in ['First', 'DynLatBw']:
            for wf in ['SJF', 'PSO', 'Priority']:
                # Use glob to match directories with or without timestamps
                pattern = os.path.join(base_dir, ds, vm, f'experiment_{vm}_{rt}_{wf}*', 'path_latency_final.csv')
                matching_files = glob.glob(pattern)
                
                if matching_files:
                    # Take the most recent one
                    path = max(matching_files, key=os.path.getmtime)
                    try:
                        # Skip comment lines starting with #
                        df = pd.read_csv(path, sep=';', comment='#', header=None)
                        # Column 9 is total_delay(ms) based on the comment header
                        if not df.empty and len(df.columns) >= 10:
                            avg_delay = df[9].mean()
                            results.append({
                                'Dataset': ds,
                                'VmAlloc': vm,
                                'Routing': rt,
                                'Workload': wf,
                                'AvgDelay': avg_delay
                            })
                    except Exception as e:
                        pass

df_res = pd.DataFrame(results)
if not df_res.empty:
    print(df_res.to_string(index=False))
else:
    print("No results found yet.")
