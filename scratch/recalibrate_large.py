import json
import pandas as pd
import os

# Paths
DS_PATH = 'datasets/dataset-LargeVF'
PHYSICAL_FILE = os.path.join(DS_PATH, 'physical.json')
VIRTUAL_FILE = os.path.join(DS_PATH, 'virtual.json')
WORKLOAD_FILE = os.path.join(DS_PATH, 'workload.csv')

# 1. Recalibrate Physical Topology
print("Recalibrating Physical Topology...")
with open(PHYSICAL_FILE, 'r') as f:
    topo = json.load(f)

# Upgrade Switch Capacities
for node in topo['nodes']:
    if node['type'] == 'core':
        node['bw'] = 100000000000  # 100 Gbps
        node['iops'] = 10000000000 # 10B
    elif node['type'] == 'aggregate':
        node['bw'] = 40000000000   # 40 Gbps
        node['iops'] = 5000000000  # 5B
    elif node['type'] == 'edge':
        node['bw'] = 10000000000   # 10 Gbps
        node['iops'] = 2000000000  # 2B
    elif node['type'] == 'host':
        node['mips'] = 200000      # 200k MIPS per host
        node['bw'] = 40000000000   # 40 Gbps

# Apply Extreme Asymmetry
# Goal: Make some paths "Gold" and others "Bottleneck"
for link in topo['links']:
    src = link['source']
    dst = link['destination']
    
    # Core-Agg: Always Backbone
    if (src.startswith('core') and dst.startswith('agg')) or (src.startswith('agg') and dst.startswith('core')):
        link['upBW'] = 40000000000 # 40 Gbps
        link['latency'] = 0.01
        
    # Agg-Edge: Asymmetry
    elif (src.startswith('agg') and dst.startswith('edge')) or (src.startswith('edge') and dst.startswith('agg')):
        # We create a "Gold Path" for even-indexed edge switches
        edge_id = int(src.replace('edge', '')) if src.startswith('edge') else int(dst.replace('edge', ''))
        
        if edge_id % 2 == 0:
            link['upBW'] = 40000000000 # 40 Gbps (Gold Path)
            link['latency'] = 0.05
        else:
            link['upBW'] = 10000000    # 10 Mbps (Bottleneck)
            link['latency'] = 5.0      # High latency penalty
            
    # Host-Edge: High speed
    elif (src.startswith('h_') or dst.startswith('h_')):
        link['upBW'] = 40000000000 # 40 Gbps
        link['latency'] = 0.01

with open(PHYSICAL_FILE, 'w') as f:
    json.dump(topo, f, indent=4)

# 2. Upgrade Virtual Topology
print("Upgrading VM capacities...")
with open(VIRTUAL_FILE, 'r') as f:
    v_topo = json.load(f)

for node in v_topo['nodes']:
    if node['type'] == 'vm':
        node['mips'] = 100000 # 100k MIPS
        node['bw'] = 10000000000 # 10 Gbps
        node['endtime'] = 1000.0 # Double simulation time

with open(VIRTUAL_FILE, 'w') as f:
    json.dump(v_topo, f, indent=4)

# 3. Calibrate Workload
print("Calibrating workload intensity...")
df = pd.read_csv(WORKLOAD_FILE)
# Reduce psize so it doesn't take 1000s on 10Mbps links, but still causes congestion
# Let's target ~10MB packets on average.
# Original psize was up to 270MB. Let's /10.
df['psize'] = (df['psize'] / 10).astype(int)
df.to_csv(WORKLOAD_FILE, index=False)

print("Recalibration COMPLETE.")
print("  - Topology: Gold/Bottleneck asymmetry (40Gbps vs 10Mbps)")
print("  - VMs: 100k MIPS, 1000s duration")
print("  - Workload: Normalized psize (/10)")
