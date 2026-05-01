import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# Chemin du fichier CSV
file_path = "D:/Workspace/cloudsimsdn/Exp2/summary_analysis.csv"

# Lire le fichier CSV
df = pd.read_csv(file_path)

# Données pour le graphique
policies = df["Policy"]
average_response_time = df["Average ResponseTime"]
average_network_time = df["Average NetworkTime"]

# Position des barres sur l'axe X
x = np.arange(len(policies))  # Positions des politiques

# Largeur des barres
width = 0.35  # Largeur des barres pour chaque métrique

# Créer le graphique à barres groupées
plt.figure(figsize=(12, 6))
plt.bar(x - width/2, average_response_time, width, label="Temps de Réponse Moyen", color='skyblue')
plt.bar(x + width/2, average_network_time, width, label="Temps Réseau Moyen", color='lightgreen')

# Ajouter des étiquettes, un titre et une légende
plt.xlabel("Politiques")
plt.ylabel("Temps (s)")
plt.title("Comparaison des Temps de Réponse et Réseau par Politique")
plt.xticks(x, policies, rotation=45)  # Étiquettes des politiques sur l'axe X
plt.legend()

# Afficher le graphique
plt.tight_layout()
plt.show()