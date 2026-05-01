import os
import pandas as pd

# Chemin de base contenant les politiques
base_path = r"D:/Workspace/cloudsimsdn/ExpComp"

# Colonnes attendues dans le fichier
columns = [
    "Workload_ID", "App_ID", "SubmitTime", "Pr:StartTime", "Pr:EndTime",
    "Pr:CPUTime", "Pr:Size", "Tr:StartTime", "Tr:EndTime", "Tr:NetworkTime",
    "Tr:Size", "Tr:Channel", "Pr:StartTime_Dup", "Pr:EndTime_Dup",
    "Pr:CPUTime_Dup", "Pr:Size_Dup", "ResponseTime"
]

# Fonction pour splitter et nettoyer un fichier
def split_and_clean(file_path, output_dir):
    # Lire et nettoyer les données
    with open(file_path, 'r') as file:
        lines = file.readlines()

    # Séparer les données structurées et métadonnées
    structured_data = []
    metadata = []
    is_metadata = False

    for line in lines:
        if line.startswith("#"):
            is_metadata = True
        if is_metadata:
            metadata.append(line.strip())
        else:
            structured_data.append(line.strip())

    # Charger les données structurées avec le séparateur "|"
    data = [row.split("|") for row in structured_data]
    max_columns = max(len(row) for row in data)

    # Ajuster dynamiquement les colonnes si nécessaire
    if len(columns) != max_columns:
        extra_columns = [f"Extra_{i}" for i in range(len(columns) + 1, max_columns + 1)]
        columns.extend(extra_columns)

    # Création du DataFrame
    df = pd.DataFrame(data, columns=columns[:max_columns])
    for col in df.columns:
        df[col] = pd.to_numeric(df[col].str.replace(",", ".").str.strip(), errors='coerce')

    # Sauvegarder les données nettoyées avec le séparateur "|"
    cleaned_file = os.path.join(output_dir, "cleaned_result.csv")
    df.to_csv(cleaned_file, sep="|", index=False)
    print(f"Fichier nettoyé sauvegardé : {cleaned_file}")

    # Sauvegarder les métadonnées
    metadata_file = os.path.join(output_dir, "metadata.txt")
    with open(metadata_file, 'w') as meta_file:
        meta_file.write("\n".join(metadata))
    print(f"Fichier des métadonnées sauvegardé : {metadata_file}")

# Parcourir tous les répertoires de politiques
for policy in os.listdir(base_path):
    policy_path = os.path.join(base_path, policy, "dataset-energy", "result_1energy-workload40.csv")
    output_dir = os.path.join(base_path, policy, "dataset-energy")

    if os.path.exists(policy_path):
        print(f"Traitement de la politique : {policy}")
        split_and_clean(policy_path, output_dir)
    else:
        print(f"Fichier introuvable pour la politique : {policy}")