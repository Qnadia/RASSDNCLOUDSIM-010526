import pandas as pd
import json
import os

def stress_dataset(directory):
    print(f"--- Stressing Dataset in: {directory} ---")
    
    # 1. Update Workload
    workload_path = os.path.join(directory, "workload.csv")
    if os.path.exists(workload_path):
        # Read without header because the header in dataset-small is wrong/misleading
        # But we know the order: start, source, z, w1, link, dest, psize, w2, len, prio
        df = pd.read_csv(workload_path, sep=",")
        
        # Scale up psize to create real congestion (x200)
        # Assuming psize is at column index 6
        df.iloc[:, 6] = df.iloc[:, 6] * 200 
        
        # Save back with the "correct" header for our simulation
        correct_header = ["start", "source", "z", "w1", "link", "dest", "psize", "w2", "len_cloudlet", "priority"]
        df.to_csv(workload_path, index=False, header=correct_header)
        print(f"DONE: Workload updated: psize x200, header fixed.")

    # 2. Update Physical Topology to create a bottleneck trap
    physical_path = os.path.join(directory, "physical.json")
    if os.path.exists(physical_path):
        with open(physical_path, 'r') as f:
            topo = json.load(f)
            
        # Create a trap: links between edge0 and agg0 are slow
        # Links between edge0 and agg1 (if they exist) or other paths are fast
        for link in topo['links']:
            src = link['source']
            dst = link['destination']
            
            # Identify "Default" paths (usually edgeN -> aggN)
            if (src == "edge0" and dst == "agg0") or (src == "agg0" and dst == "edge0"):
                link['upBW'] = 100_000_000  # 100 Mbps (Bottleneck)
                link['latency'] = 0.5        # High latency too
            
            # Identify "Alternative" paths (usually edgeN -> aggM where N!=M)
            # If they don't exist, we might need to add them, but let's check current ones first.
            if (src == "edge0" and dst == "agg1") or (src == "agg1" and dst == "edge0"):
                link['upBW'] = 10_000_000_000 # 10 Gbps (High speed)
                link['latency'] = 0.01        # Low latency
            
            # Boost Core links to ensure they are not the bottleneck
            if "core" in src or "core" in dst:
                link['upBW'] = 40_000_000_000 # 40 Gbps
        
        # Add a cross-link if missing to allow redirection
        # Let's ensure edge0 can reach agg1
        has_cross = any(l['source'] == "edge0" and l['destination'] == "agg1" for l in topo['links'])
        if not has_cross:
            topo['links'].append({
                "source": "edge0", "destination": "agg1",
                "upBW": 10_000_000_000, "latency": 0.01, "distance": 100, "refractiveIndex": 1.46
            })
            topo['links'].append({
                "source": "agg1", "destination": "edge0",
                "upBW": 10_000_000_000, "latency": 0.01, "distance": 100, "refractiveIndex": 1.46
            })

        with open(physical_path, 'w') as f:
            json.dump(topo, f, indent=4)
        print(f"DONE: Topology updated: Bottleneck at 100Mbps on default path, 10Gbps on alternative.")

if __name__ == "__main__":
    stress_dataset("e:/Workspace/v2/cloudsimsdn-research/datasetsH/dataset-small")
