import pandas as pd
import matplotlib.pyplot as plt

# Chemin du fichier CSV
file_path = "D:/Workspace/cloudsimsdn/Exp2/summary_analysis.csv"

# Lire le fichier CSV
df = pd.read_csv(file_path)

# Données pour le graphique
policies = df["Policy"]
average_response_time = df["Average ResponseTime"]
average_network_time = df["Average NetworkTime"]

# Créer le graphique en ligne
plt.figure(figsize=(12, 6))
plt.plot(policies, average_response_time, marker='o', label="Temps de Réponse Moyen", color='skyblue')
plt.plot(policies, average_network_time, marker='o', label="Temps Réseau Moyen", color='lightgreen')

# Ajouter des étiquettes, un titre et une légende
plt.xlabel("Politiques")
plt.ylabel("Temps (s)")
plt.title("Comparaison des Temps de Réponse et Réseau par Politique")
plt.xticks(rotation=45)  # Rotation des étiquettes des politiques
plt.legend()

# Afficher le graphique
plt.tight_layout()
plt.show()