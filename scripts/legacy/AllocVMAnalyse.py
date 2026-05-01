import os
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# Chemin de base contenant les politiques
base_path = r"D:/Workspace/cloudsimsdn/ExpComp"

# Liste des politiques d'allocation
policies = ["LFF", "MFF", "FCFS", "Spread", "Binpack", "LWFF", "LWFFVD"]

# Colonnes du fichier de log
columns = ["Host", "Time", "VM_ID", "CPU_Requested", "RAM_Requested", "BW_Requested"]

# Fonction pour analyser un fichier d'allocation
def analyze_allocation_file(file_path):
    # Lire le fichier CSV
    df = pd.read_csv(file_path, names=columns)

    # Calculer les statistiques
    stats = {
        "Total_VMs": df["VM_ID"].nunique(),
        "Total_Hosts": df["Host"].nunique(),
        "Avg_CPU_Requested": df["CPU_Requested"].mean(),
        "Avg_RAM_Requested": df["RAM_Requested"].mean(),
        "Avg_BW_Requested": df["BW_Requested"].mean(),
        "Max_CPU_Requested": df["CPU_Requested"].max(),
        "Max_RAM_Requested": df["RAM_Requested"].max(),
        "Max_BW_Requested": df["BW_Requested"].max(),
    }

    return stats

# Fonction pour tracer les graphiques
def plot_policy_comparison(stats_dict):
    # Extraire les données pour chaque politique
    policies = list(stats_dict.keys())
    avg_cpu = [stats_dict[policy]["Avg_CPU_Requested"] for policy in policies]
    avg_ram = [stats_dict[policy]["Avg_RAM_Requested"] for policy in policies]
    avg_bw = [stats_dict[policy]["Avg_BW_Requested"] for policy in policies]

    # Créer un graphique pour l'utilisation moyenne du CPU
    plt.figure(figsize=(10, 6))
    plt.bar(policies, avg_cpu, color='skyblue')
    plt.title("Utilisation moyenne du CPU par politique")
    plt.xlabel("Politique d'allocation")
    plt.ylabel("CPU moyen demandé (MIPS)")
    plt.show()

    # Créer un graphique pour l'utilisation moyenne de la RAM
    plt.figure(figsize=(10, 6))
    plt.bar(policies, avg_ram, color='lightgreen')
    plt.title("Utilisation moyenne de la RAM par politique")
    plt.xlabel("Politique d'allocation")
    plt.ylabel("RAM moyenne demandée (Mo)")
    plt.show()

    # Créer un graphique pour l'utilisation moyenne de la bande passante
    plt.figure(figsize=(10, 6))
    plt.bar(policies, avg_bw, color='salmon')
    plt.title("Utilisation moyenne de la bande passante par politique")
    plt.xlabel("Politique d'allocation")
    plt.ylabel("Bande passante moyenne demandée (bps)")
    plt.show()

# Fonction principale
def main():
    # Dictionnaire pour stocker les statistiques de chaque politique
    stats_dict = {}

    # Analyser chaque politique
    for policy in policies:
        # Chemin du fichier de log pour cette politique
        file_path = os.path.join(base_path, policy, "vm_allocation.csv")

        # Vérifier si le fichier existe
        if os.path.exists(file_path):
            # Analyser le fichier
            stats = analyze_allocation_file(file_path)
            stats_dict[policy] = stats
            print(f"Statistiques pour la politique {policy}:")
            print(stats)
        else:
            print(f"Fichier non trouvé pour la politique {policy}.")

    # Tracer les graphiques de comparaison
    plot_policy_comparison(stats_dict)

# Exécuter le programme
if __name__ == "__main__":
    main()