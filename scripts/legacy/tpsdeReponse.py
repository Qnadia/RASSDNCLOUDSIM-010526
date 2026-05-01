import os
import pandas as pd
import matplotlib.pyplot as plt

# Chemin de base contenant les dossiers des politiques d'allocation
base_path = r"D:/Workspace/cloudsimsdn/ExpComp"  # Remplacez par votre chemin réel

# Fonction pour analyser les colonnes et ajuster dynamiquement
def rename_columns(df, num_columns):
    base_columns = [
        "Workload_ID", "App_ID", "SubmitTime", "Pr:StartTime", "Pr:EndTime",
        "Pr:CPUTime", "Pr:Size", "Tr:StartTime", "Tr:EndTime", "Tr:NetworkTime",
        "Tr:Size", "Tr:Channel", "Pr:StartTime2", "Pr:EndTime2", "Pr:CPUTime2",
        "Pr:Size2", "ResponseTime"
    ]
    if num_columns > len(base_columns):  # Si plus de colonnes que prévu
        extra_columns = [f"Extra_Column_{i}" for i in range(1, num_columns - len(base_columns) + 1)]
        base_columns.extend(extra_columns)
    df.columns = base_columns[:num_columns]
    return df

# Fonction pour analyser les politiques
def analyze_policy(policy_name, policy_path):
    print(f"Début de l'analyse pour {policy_name}...")

    # Initialiser un dictionnaire pour les fichiers de la politique
    policy_data = {}

    # Parcourir les fichiers dans le dossier de la politique
    for root, dirs, files in os.walk(policy_path):
        for file in files:
            if file.endswith(".csv"):
                file_path = os.path.join(root, file)
                try:
                    df = pd.read_csv(file_path, comment='#', skip_blank_lines=True)
                    policy_data[file] = df
                    print(f"[{policy_name}] Fichier chargé : {file}, Nombre de lignes : {len(df)}")
                except pd.errors.ParserError as e:
                    print(f"[{policy_name}] Erreur lors du chargement de {file} : {e}")

    # Analyser le fichier `result_energy-workloadV3.csv` ou `result_energy-workloadV3_corrected.csv`
    result_file = None
    if "result_energy-workload40.csv" in policy_data:
        result_file = "result_energy-workload40.csv"
    elif "result_energy-workload40_corrected.csv" in policy_data:
        result_file = "result_energy-workload40_corrected.csv"

    if result_file:
        print(f"[{policy_name}] Fichier `{result_file}` trouvé.")
        result_df = policy_data[result_file]

        # Renommer les colonnes
        num_columns = len(result_df.columns)
        print(f"[{policy_name}] Nombre de colonnes détectées : {num_columns}")
        result_df = rename_columns(result_df, num_columns)

        # Calculer le temps moyen de réponse
        if "ResponseTime" in result_df.columns:
            average_response_time = result_df["ResponseTime"].mean()
            print(f"[{policy_name}] Temps moyen de réponse : {average_response_time:.3f}")

            # Créer un histogramme des temps de réponse
            plt.hist(result_df["ResponseTime"], bins=10, edgecolor="black")
            plt.title(f"Distribution des temps de réponse ({policy_name})")
            plt.xlabel("Temps de réponse")
            plt.ylabel("Fréquence")
            plt.show()
        else:
            print(f"[{policy_name}] La colonne `ResponseTime` est absente.")
    else:
        print(f"[{policy_name}] Aucun fichier `result_energy-workloadV3.csv` ou `result_energy-workloadV3_corrected.csv` trouvé.")

# Parcourir les dossiers représentant chaque politique d'allocation
print("Liste des sous-dossiers dans base_path :")
for policy in os.listdir(base_path):
    policy_path = os.path.join(base_path, policy)
    if os.path.isdir(policy_path):
        print(f"Analyse pour la politique : {policy}")
        analyze_policy(policy, policy_path)
