import pandas as pd
import matplotlib.pyplot as plt
import os
from datetime import datetime
import numpy as np
from matplotlib.ticker import FormatStrFormatter

# Dossiers contenant les résultats
dir_first = r'E:\Workspace\v2\cloudsimsdn-research\results\2026-04-20\dataset-small\LWFF\experiment_LWFF_First_SJF'
dir_bwlat = r'E:\Workspace\v2\cloudsimsdn-research\results\2026-04-20\dataset-small\LWFF\experiment_LWFF_DynLatBw_SJF_21-45-09'

# Répertoire de sortie
timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
output_dir = f"results/plots_{timestamp}"
os.makedirs(output_dir, exist_ok=True)

def load_csv(directory, filename, columns=None):
    path = os.path.join(directory, filename)
    if not os.path.exists(path):
        print(f"❌ Fichier non trouvé : {path}")
        return pd.DataFrame()
    try:
        df = pd.read_csv(path, sep=';', header=0 if columns is None else None, names=columns, comment='#')
        # Nettoyage des colonnes
        df.columns = [c.strip().lower().replace('(', '').replace(')', '').replace('#', '').replace(' ', '').replace('%','') for c in df.columns]
        for col in df.columns:
            if any(key in col for key in ['time', 'delay', 'energy', 'cpu', 'bw', 'ram']):
                df[col] = pd.to_numeric(df[col], errors='coerce')
        return df
    except Exception as e:
        print(f"❌ Erreur de lecture {filename} : {e}")
        return pd.DataFrame()

def plot_packet_delay_breakdown():
    # Analyse granulaire des délais pour DynLatBw
    df = load_csv(dir_bwlat, 'packet_delays.csv', ['packetId', 'src', 'dst', 'psize', 'delay', 'proc', 'prop', 'trans', 'queue'])
    if df.empty:
        print("❌ Fichier packet_delays.csv introuvable ou vide dans DynLatBw.")
        return

    # Moyenne par type de délai
    avg_delays = df[['proc', 'prop', 'trans', 'queue']].mean()
    
    plt.figure(figsize=(10, 6))
    avg_delays.plot(kind='bar', color=['#3498db', '#e74c3c', '#2ecc71', '#f1c40f'])
    plt.title("Packet Delay Breakdown (DynLatBw)")
    plt.ylabel("Time (ms)")
    plt.xticks(rotation=0)
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    
    for i, v in enumerate(avg_delays):
        plt.text(i, v, f"{v:.2f}ms", ha='center', va='bottom', fontweight='bold')
    
    plt.tight_layout()
    save_path = os.path.join(output_dir, "delay_breakdown.png")
    plt.savefig(save_path)
    print(f"Breakdown saved to {save_path}")

def plot_delay_comparison():
    df1 = load_csv(dir_first, 'packet_delays.csv', ['packetId', 'src', 'dst', 'psize', 'delay', 'proc', 'prop', 'trans', 'queue'])
    df2 = load_csv(dir_bwlat, 'packet_delays.csv', ['packetId', 'src', 'dst', 'psize', 'delay', 'proc', 'prop', 'trans', 'queue'])
    
    if df1.empty or df2.empty:
        return

    plt.figure(figsize=(12, 6))
    
    # Histogramme de distribution des délais totaux
    plt.hist(df1['delay'], bins=30, alpha=0.5, label='First-Link', color='gray')
    plt.hist(df2['delay'], bins=30, alpha=0.5, label='DynLatBw', color='blue')
    
    plt.title("End-to-End Delay Distribution Comparison")
    plt.xlabel("Delay (ms)")
    plt.ylabel("Frequency")
    plt.legend()
    plt.grid(True, alpha=0.3)
    
    save_path = os.path.join(output_dir, "delay_distribution_comparison.png")
    plt.savefig(save_path)
    print(f"Distribution comparison saved to {save_path}")

if __name__ == "__main__":
    print(f"Analyzing results in {output_dir}...")
    plot_packet_delay_breakdown()
    plot_delay_comparison()
    print("Visualization complete.")
