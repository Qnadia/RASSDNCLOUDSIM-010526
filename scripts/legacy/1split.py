import os
import pandas as pd
import logging

# Configuration du logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# Chemin de base contenant les politiques
base_path = r"D:/Workspace/cloudsimsdn/Exp5-SJF"

# Colonnes attendues dans le fichier
columns = [
    "Workload_ID", "App_ID", "SubmitTime", "Pr:StartTime", "Pr:EndTime",
    "Pr:CPUTime", "Pr:Size", "Tr:StartTime", "Tr:EndTime", "Tr:NetworkTime",
    "Tr:Size", "Tr:Channel", "Pr:StartTime_Dup", "Pr:EndTime_Dup",
    "Pr:CPUTime_Dup", "Pr:Size_Dup", "ResponseTime"
]

def split_and_clean(file_path, output_dir):
    try:
        # Vérifier si le fichier existe
        if not os.path.exists(file_path):
            logging.error(f"Le fichier {file_path} n'existe pas.")
            return

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
                # Supprimer les espaces inutiles avant de diviser la ligne
                cleaned_line = line.strip().replace(" ", "")
                structured_data.append(cleaned_line)

        # Charger les données structurées avec le séparateur "|"
        data = [row.split("|") for row in structured_data]
        max_columns = max(len(row) for row in data)

        # Ajuster dynamiquement les colonnes si nécessaire
        if len(columns) < max_columns:
            extra_columns = [f"Extra_{i}" for i in range(len(columns), max_columns)]
            columns.extend(extra_columns)
        elif len(columns) > max_columns:
            logging.warning(f"Le fichier a moins de colonnes que prévu. Colonnes manquantes : {columns[max_columns:]}")

        # Création du DataFrame
        df = pd.DataFrame(data, columns=columns[:max_columns])

        # Convertir les colonnes numériques
        for col in df.columns:
            df[col] = pd.to_numeric(df[col].str.replace(",", ".").str.strip(), errors='coerce')

        # Remplacer les valeurs manquantes par une chaîne vide
        df = df.fillna('')

        # Créer le répertoire de sortie s'il n'existe pas
        os.makedirs(output_dir, exist_ok=True)

        # Sauvegarder les données nettoyées avec le séparateur "|"
        cleaned_file = os.path.join(output_dir, "cleaned_result.csv")
        df.to_csv(cleaned_file, sep="|", index=False)  # Utiliser sep="|" pour forcer les pipes
        logging.info(f"Fichier nettoyé sauvegardé : {cleaned_file}")

        # Sauvegarder les métadonnées
        metadata_file = os.path.join(output_dir, "metadata.txt")
        with open(metadata_file, 'w') as meta_file:
            meta_file.write("\n".join(metadata))
        logging.info(f"Fichier des métadonnées sauvegardé : {metadata_file}")

    except Exception as e:
        logging.error(f"Erreur lors du traitement du fichier {file_path}: {e}")

# Parcourir tous les répertoires de politiques
for policy in os.listdir(base_path):
    policy_path = os.path.join(base_path, policy, "dataset-energy", "result_2energy-workload120.csv")
    output_dir = os.path.join(base_path, policy, "dataset-energy")

    if os.path.exists(policy_path):
        logging.info(f"Traitement de la politique : {policy}")
        split_and_clean(policy_path, output_dir)
    else:
        logging.warning(f"Fichier introuvable pour la politique : {policy}")