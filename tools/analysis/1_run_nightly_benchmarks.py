import os
import subprocess
import shutil
import time
import argparse
from datetime import datetime

# Utilisation de la version SSLAB qui gère DynLatBw et les rapports détaillés
JAVA_MAIN = "org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth"
ROUTING_POLICIES = ["First", "BLA"]
VM_POLICIES = ["LFF", "MFF", "LWFF"]
WF_POLICIES = ["Priority", "SJF", "PSO"]

def run_simulation(ds_name, vm, routing, wf):
    # La version SSLAB attend : <vmAlloc> [link] [wf] [datasetDir]
    # Elle résout elle-même datasets/nom-du-dataset
    exp_id = f"experiment_{vm}_{routing}_{wf}"
    log_dir = f"logs/{datetime.now().strftime('%Y-%m-%d')}/{ds_name}"
    
    # SSLAB auto-génère results/YYYY-MM-DD/dataset/vmPolicy/experiment_...
    # On vide le dossier de résultats global pour éviter les conflits de timestamp
    date_str = datetime.now().strftime('%Y-%m-%d')
    out_base = f"results/{date_str}/{ds_name}/{vm}"
    
    if not os.path.exists(log_dir): os.makedirs(log_dir)
    log_file = f"{log_dir}/{exp_id}.log"

    cp = "target/classes;target/lib/*;lib/*;target/dependency/*"
    # Arguments pour SSLAB: vm routing wf datasetName
    cmd = ["java", "-cp", cp, JAVA_MAIN, vm, routing, wf, ds_name]
    
    print(f"  > Executing: {' '.join(cmd)}")
    with open(log_file, "w") as f:
        try:
            start = time.time()
            # On ne fait pas de rmtree ici, on laisse Java gérer ses dossiers horodatés
            result = subprocess.run(cmd, stdout=f, stderr=f, timeout=600) 
            elapsed = time.time() - start
            if result.returncode == 0:
                print(f"  [OK] {elapsed:.1f}s")
                return True
            else:
                print(f"  [FAILED] Exit: {result.returncode}")
                # Afficher la fin du log pour débugger
                return False
        except subprocess.TimeoutExpired:
            print("  [TIMEOUT]")
            return False

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="large-congested")
    args = parser.parse_args()

    print(f"=== CloudSimSDN SSLAB Runner | Dataset: {args.dataset} ===")
    success = 0
    total = 0
    
    for vm in VM_POLICIES:
        for routing in ROUTING_POLICIES:
            for wf in WF_POLICIES:
                print(f"[{total+1}/18] {vm} | {routing} | {wf}...", end="")
                if run_simulation(args.dataset, vm, routing, wf):
                    success += 1
                total += 1

    print(f"\nFinished: {success}/{total} successful.")

if __name__ == "__main__":
    main()
