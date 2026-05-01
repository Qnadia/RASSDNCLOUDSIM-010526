import os
import pandas as pd
import matplotlib.pyplot as plt

# Chemin de base contenant les dossiers des politiques d'allocation
base_path = r"D:/Workspace/cloudsimsdn/ExpComp"  # Remplacez par votre chemin réel

# Dictionnaire pour stocker les données d'utilisation des ressources
link_usage_data = {}

# Fonction pour charger et analyser les données des liens
def analyze_link_file(policy_name, file_path, link_type):
    try:
        df = pd.read_csv(file_path, comment='#', skip_blank_lines=True, header=None)
        if df.empty:
            print(f"[{policy_name}] Le fichier {link_type} est vide.")
            return None

        # Supposons que les colonnes soient : [Commutateur, Temps, Utilisation]
        df.columns = ["Switch", "Time", "Utilization"]

        # Calculer la moyenne de l'utilisation des liens
        avg_usage = df["Utilization"].mean()
        print(f"[{policy_name}] Moyenne de l'utilisation {link_type} : {avg_usage:.3f}")

        return df["Utilization"]  # Retourner la colonne de l'utilisation
    except Exception as e:
        print(f"[{policy_name}] Erreur lors de l'analyse de {link_type} : {e}")
        return None

# Fonction pour analyser les liens pour une politique donnée
def analyze_links(policy_name, policy_path):
    global link_usage_data
    print(f"Début de l'analyse des liens pour {policy_name}...")

    # Liste des fichiers pertinents pour les liens
    link_files = {
        "link_up": os.path.join(policy_path, "link_utilization_up.csv"),
        "link_down": os.path.join(policy_path, "link_utilization_down.csv")
    }

    # Analyser chaque fichier
    for link_type, file_path in link_files.items():
        if os.path.exists(file_path):
            print(f"[{policy_name}] Fichier trouvé : {file_path}")
            usage_data = analyze_link_file(policy_name, file_path, link_type)
            if usage_data is not None:
                if policy_name not in link_usage_data:
                    link_usage_data[policy_name] = {}
                link_usage_data[policy_name][link_type] = usage_data
        else:
            print(f"[{policy_name}] Fichier {link_type} introuvable.")

# Parcourir les dossiers représentant chaque politique d'allocation
print("Liste des sous-dossiers dans base_path :")
for policy in os.listdir(base_path):
    policy_path = os.path.join(base_path, policy)
    if os.path.isdir(policy_path):
        print(f"Analyse pour la politique : {policy}")
        analyze_links(policy, policy_path)

# Générer des graphiques comparatifs pour les liens montants et descendants
if link_usage_data:
    link_types = ["link_up", "link_down"]

    for link_type in link_types:
        # Préparer les données pour le diagramme
        policies = []
        avg_usages = []
        for policy, link_data in link_usage_data.items():
            if link_type in link_data:
                policies.append(policy)
                avg_usages.append(link_data[link_type].mean())

        # Diagramme à barres pour les moyennes
        if avg_usages:
            plt.figure(figsize=(10, 6))
            plt.bar(policies, avg_usages, color='lightgreen', edgecolor='black')
            plt.title(f"Moyenne de l'utilisation des {link_type} par politique d'allocation")
            plt.xlabel("Politiques d'allocation")
            plt.ylabel(f"Moyenne de l'utilisation des {link_type}")
            plt.xticks(rotation=45)
            plt.tight_layout()
            plt.show()

        # Boxplot pour les distributions
        if avg_usages:
            plt.figure(figsize=(12, 6))
            plt.boxplot(
                [link_usage_data[policy][link_type] for policy in policies],
                labels=policies, vert=True, patch_artist=True
            )
            plt.title(f"Distribution de l'utilisation des {link_type} par politique d'allocation")
            plt.xlabel("Politiques d'allocation")
            plt.ylabel(f"Utilisation des {link_type}")
            plt.xticks(rotation=45)
            plt.tight_layout()



            plt.show()
else:
    print("Aucune donnée valide pour analyser les liens.")
