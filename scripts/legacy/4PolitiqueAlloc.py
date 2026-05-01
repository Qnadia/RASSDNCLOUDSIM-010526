import os
import pandas as pd
import matplotlib.pyplot as plt

# Base path containing the policies
base_path = r"D:/Workspace/cloudsimsdn/Exp2"

# List of allocation policies
policies = ["LFF", "FCFS", "Spread", "Binpack", "LWFF", "LWFFVD", "MipLFF", "MipMFF"]

# Charger les données depuis un fichier CSV
def load_data(file_path):
    if not os.path.exists(file_path):
        print(f"Fichier non trouvé : {file_path}")
        return None

    # Spécifier les noms des colonnes manuellement
    column_names = ["host", "time", "VM_ID", "CPU", "RAM", "BW"]
    df = pd.read_csv(file_path, names=column_names)
    return df

# Créer un tableau d'allocation des VMs aux hôtes
def create_allocation_table(df):
    if df is None:
        return None
    allocation_table = df[["host", "time", "VM_ID", "CPU", "RAM", "BW"]]
    return allocation_table

# Calculer les ressources utilisées par hôte
def calculate_resource_usage(df):
    if df is None:
        return None
    resource_usage = df.groupby("host").agg({
        "CPU": "sum",
        "RAM": "sum",
        "BW": "sum"
    }).reset_index()
    return resource_usage

# Visualiser les ressources CPU utilisées par hôte
def plot_cpu_usage(resource_usage, policy):
    if resource_usage is None:
        return
    plt.figure(figsize=(10, 6))
    plt.bar(resource_usage["host"], resource_usage["CPU"], color="skyblue", edgecolor="black")
    plt.title(f"Ressources CPU utilisées par hôte - Politique {policy}")
    plt.xlabel("Hôte")
    plt.ylabel("CPU Utilisé (MIPS)")
    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.show()

# Fonction principale
def main():
    # Parcourir chaque politique
    for policy in policies:
        # Chemin du fichier CSV pour la politique actuelle
        policy_folder = os.path.join(base_path, policy)  # Dossier de la politique
        file_path = os.path.join(policy_folder, "host_vm_allocation.csv")  # Chemin du fichier CSV

        # Charger les données
        df = load_data(file_path)
        if df is None:
            continue

        # Créer le tableau d'allocation
        allocation_table = create_allocation_table(df)
        if allocation_table is None:
            continue
        print(f"\nTableau d'Allocation des VMs aux Hôtes - Politique {policy}:")
        print(allocation_table)

        # Calculer les ressources utilisées
        resource_usage = calculate_resource_usage(df)
        if resource_usage is None:
            continue
        print(f"\nRessources Utilisées par Hôte - Politique {policy}:")
        print(resource_usage)

        # Visualiser les ressources CPU utilisées
        plot_cpu_usage(resource_usage, policy)

if __name__ == "__main__":
    main()