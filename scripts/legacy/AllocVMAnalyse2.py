import os
import pandas as pd
import matplotlib.pyplot as plt

# Chemin de base contenant les politiques
base_path = r"D:/Workspace/cloudsimsdn/ExpComp"

# Liste des politiques d'allocation
policies = ["LFF", "FCFS", "Spread", "Binpack", "LWFF", "LWFFVD", "MipLFF", "MipMFF"]

# Colonnes du fichier de log
columns = ["Host", "Time", "VM_ID", "CPU_Requested", "RAM_Requested", "BW_Requested"]

# Fonction pour analyser un fichier d'allocation
def analyze_allocation_file(file_path):
    # Lire le fichier CSV
    df = pd.read_csv(file_path, names=columns)

    # Calculer les statistiques de répartition
    stats = {
        "Total_VMs": df["VM_ID"].nunique(),
        "Total_Hosts": df["Host"].nunique(),
        "Avg_CPU_Requested": df["CPU_Requested"].mean(),
        "Avg_RAM_Requested": df["RAM_Requested"].mean(),
        "Avg_BW_Requested": df["BW_Requested"].mean(),
        "Host_CPU_Load": df.groupby("Host")["CPU_Requested"].sum().to_dict(),  # Charge CPU par hôte
        "Host_RAM_Load": df.groupby("Host")["RAM_Requested"].sum().to_dict(),  # Charge RAM par hôte
        "Host_BW_Load": df.groupby("Host")["BW_Requested"].sum().to_dict(),    # Charge BW par hôte
    }

    return stats

# Fonction pour tracer les graphiques
def plot_policy_comparison(stats_dict):
    # Extraire les données pour chaque politique
    policies = list(stats_dict.keys())

    # Charge CPU moyenne par hôte
    avg_cpu_load = [sum(stats_dict[policy]["Host_CPU_Load"].values()) / len(stats_dict[policy]["Host_CPU_Load"]) for policy in policies]

    # Charge RAM moyenne par hôte
    avg_ram_load = [sum(stats_dict[policy]["Host_RAM_Load"].values()) / len(stats_dict[policy]["Host_RAM_Load"]) for policy in policies]

    # Charge BW moyenne par hôte
    avg_bw_load = [sum(stats_dict[policy]["Host_BW_Load"].values()) / len(stats_dict[policy]["Host_BW_Load"]) for policy in policies]

    # Nombre de VMs par hôte
    avg_vms_per_host = [stats_dict[policy]["Total_VMs"] / stats_dict[policy]["Total_Hosts"] for policy in policies]

    # Créer un graphique pour la charge CPU moyenne par hôte
    plt.figure(figsize=(12, 6))
    plt.bar(policies, avg_cpu_load, color='skyblue')
    plt.title("Charge CPU moyenne par hôte par politique", fontsize=14)
    plt.xlabel("Politique d'allocation", fontsize=12)
    plt.ylabel("Charge CPU moyenne (MIPS)", fontsize=12)
    plt.xticks(rotation=45, ha='right')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.show()

    # Créer un graphique pour la charge RAM moyenne par hôte
    plt.figure(figsize=(12, 6))
    plt.bar(policies, avg_ram_load, color='lightgreen')
    plt.title("Charge RAM moyenne par hôte par politique", fontsize=14)
    plt.xlabel("Politique d'allocation", fontsize=12)
    plt.ylabel("Charge RAM moyenne (Mo)", fontsize=12)
    plt.xticks(rotation=45, ha='right')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.show()

    # Créer un graphique pour la charge BW moyenne par hôte
    plt.figure(figsize=(12, 6))
    plt.bar(policies, avg_bw_load, color='salmon')
    plt.title("Charge BW moyenne par hôte par politique", fontsize=14)
    plt.xlabel("Politique d'allocation", fontsize=12)
    plt.ylabel("Charge BW moyenne (bps)", fontsize=12)
    plt.xticks(rotation=45, ha='right')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.show()

    # Créer un graphique pour le nombre moyen de VMs par hôte
    plt.figure(figsize=(12, 6))
    plt.bar(policies, avg_vms_per_host, color='purple')
    plt.title("Nombre moyen de VMs par hôte par politique", fontsize=14)
    plt.xlabel("Politique d'allocation", fontsize=12)
    plt.ylabel("Nombre moyen de VMs par hôte", fontsize=12)
    plt.xticks(rotation=45, ha='right')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.show()

# Fonction principale
def main():
    # Dictionnaire pour stocker les statistiques de chaque politique
    stats_dict = {}

    # Analyser chaque politique
    for policy in policies:
        # Chemin du fichier de log pour cette politique
        file_path = os.path.join(base_path, policy, "host_vm_allocation.csv")

        # Vérifier si le fichier existe
        if os.path.exists(file_path):
            # Analyser le fichier
            stats = analyze_allocation_file(file_path)
            stats_dict[policy] = stats
            print(f"Statistiques pour la politique {policy}:")
            print(stats)
        else:
            print(f"Fichier non trouvé pour la politique {policy} : {file_path}")

    # Tracer les graphiques de comparaison
    if stats_dict:
        plot_policy_comparison(stats_dict)
    else:
        print("Aucun fichier trouvé. Vérifiez les chemins et les noms de fichiers.")

# Exécuter le programme
if __name__ == "__main__":
    main()