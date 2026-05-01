import os
import pandas as pd
import matplotlib.pyplot as plt

# Chemin de base contenant les dossiers des politiques d'allocation
base_path = r"D:/Workspace/cloudsimsdn/ExpComp"  # Remplacez par votre chemin réel

# Dictionnaire pour stocker les données d'utilisation des ressources
resource_usage_data = {}

# Fonction pour charger et analyser un fichier spécifique
def analyze_resource_file(policy_name, file_path, metric_name):
    try:
        df = pd.read_csv(file_path, comment='#', skip_blank_lines=True)
        if df.empty:
            print(f"[{policy_name}] Le fichier {metric_name} est vide.")
            return None

        # Calculer la moyenne de la métrique
        avg_usage = df.iloc[:, 2].mean()  # La 3e colonne contient souvent les valeurs
        print(f"[{policy_name}] Moyenne de {metric_name} : {avg_usage:.3f}")
        return df.iloc[:, 2]  # Retourner la colonne de la métrique
    except Exception as e:
        print(f"[{policy_name}] Erreur lors de l'analyse de {metric_name} : {e}")
        return None

# Analyse des ressources pour une politique donnée
def analyze_resources(policy_name, policy_path):
    global resource_usage_data
    print(f"Début de l'analyse des ressources pour {policy_name}...")

    # Liste des fichiers pertinents
    resource_files = {
        "host_utilization": os.path.join(policy_path, "host_utilization.csv"),
        "vm_utilization": os.path.join(policy_path, "vm_utilization.csv"),
        "vm_bw_utilization": os.path.join(policy_path, "vm_bw_utilization.csv"),
        "host_energy": os.path.join(policy_path, "host_energy.csv")
    }

    # Analyser chaque fichier
    for metric_name, file_path in resource_files.items():
        if os.path.exists(file_path):
            print(f"[{policy_name}] Fichier trouvé : {file_path}")
            usage_data = analyze_resource_file(policy_name, file_path, metric_name)
            if usage_data is not None:
                if policy_name not in resource_usage_data:
                    resource_usage_data[policy_name] = {}
                resource_usage_data[policy_name][metric_name] = usage_data
        else:
            print(f"[{policy_name}] Fichier {metric_name} introuvable.")

# Parcourir les dossiers représentant chaque politique d'allocation
print("Liste des sous-dossiers dans base_path :")
for policy in os.listdir(base_path):
    policy_path = os.path.join(base_path, policy)
    if os.path.isdir(policy_path):
        print(f"Analyse pour la politique : {policy}")
        analyze_resources(policy, policy_path)

# Générer des graphiques comparatifs pour les ressources
if resource_usage_data:
    metrics = ["host_utilization", "vm_utilization", "vm_bw_utilization", "host_energy"]

    for metric in metrics:
        # Préparer les données pour le diagramme
        policies = []
        avg_usages = []
        for policy, metrics_data in resource_usage_data.items():
            if metric in metrics_data:
                policies.append(policy)
                avg_usages.append(metrics_data[metric].mean())

        # Diagramme à barres pour les moyennes
        if avg_usages:
            plt.figure(figsize=(10, 6))
            plt.bar(policies, avg_usages, color='lightcoral', edgecolor='black')
            plt.title(f"Moyenne de {metric} par politique d'allocation")
            plt.xlabel("Politiques d'allocation")
            plt.ylabel(f"Moyenne de {metric}")
            plt.xticks(rotation=45)
            plt.tight_layout()
            plt.show()

        # Boxplot pour les distributions
        if avg_usages:
            plt.figure(figsize=(12, 6))
            plt.boxplot(
                [resource_usage_data[policy][metric] for policy in policies],
                labels=policies, vert=True, patch_artist=True
            )
            plt.title(f"Distribution de {metric} par politique d'allocation")
            plt.xlabel("Politiques d'allocation")
            plt.ylabel(metric)
            plt.xticks(rotation=45)
            plt.tight_layout()
            plt.show()
else:
    print("Aucune donnée valide pour analyser les ressources.")
