import pandas as pd
import matplotlib.pyplot as plt
import os

def main():
    base_dir = "results/2026-03-08"
    datasets = ["dataset-small", "dataset-medium", "dataset-large"]
    all_data = []

    for ds in datasets:
        csv_path = os.path.join(base_dir, ds, "figures_consolidated", "consolidated_summary.csv")
        if os.path.exists(csv_path):
            df = pd.read_csv(csv_path, sep=";")
            df["dataset"] = ds.replace("dataset-", "")
            all_data.append(df)

    if not all_data:
        print("No data found to compare.")
        return

    df_full = pd.concat(all_data, ignore_index=True)
    df_full = df_full[df_full["vm_alloc"] == "LFF"].copy()
    df_full["sla_rate"] = 100 * (1 - df_full["sla_violations"] / df_full["n_requests"].replace(0, 1))
    
    out_dir = os.path.join(base_dir, "global_comparison")
    os.makedirs(out_dir, exist_ok=True)
    
    # --- 1. Latency Evolution ---
    plt.figure(figsize=(10, 6))
    for policy in df_full["link_policy"].unique():
        subset = df_full[df_full["link_policy"] == policy]
        # Mean across workfload policies for simplicity in line plot
        agg = subset.groupby("dataset")["avg_latency_s"].mean().reindex(["small", "medium", "large"])
        plt.plot(agg.index, agg.values, marker='o', label=policy)
    
    plt.title("Scaling Impact: Average Latency across Datasets")
    plt.ylabel("Avg Latency (s)"); plt.legend()
    plt.savefig(os.path.join(out_dir, "comparison_latency.png"))
    plt.close()

    # --- 2. Energy Consumption ---
    plt.figure(figsize=(10, 6))
    agg_energy = df_full.pivot_table(index="dataset", columns="link_policy", values="energy_Wh", aggfunc="mean").reindex(["small", "medium", "large"])
    agg_energy.plot(kind="bar", ax=plt.gca())
    plt.title("Energy Consumption vs Topology Scale")
    plt.ylabel("Energy (Wh)")
    plt.savefig(os.path.join(out_dir, "comparison_energy.png"))
    plt.close()

    print(f"Global comparison generated in: {out_dir}")

if __name__ == "__main__":
    main()

if __name__ == "__main__":
    main()
