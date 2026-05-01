import os
import pandas as pd
import matplotlib.pyplot as plt

# Base path containing the policies
base_path = r"D:/Workspace/cloudsimsdn/Exp3-AlgoSansSJF"

# List of allocation policies (standard and SJF)
standard_policies = ["LFF", "FCFS", "Spread", "Binpack", "LWFF", "LWFFVD", "MipLFF", "MipMFF", "RR", "CombLFFV2", "CombLFF"]
sjf_policies = ["SJFLFF", "SJFFCFS", "SJFSpread", "SJFBinpack", "SJFLWFF", "SJFLWFFVD", "SJFMipLFF", "SJFMipMFF", "SJFRR", "SJFCombLFFV2", "SJFCombLFF"]

# Combine both lists for color mapping
all_policies = standard_policies + sjf_policies

# Define a color palette for all policies
policy_colors = {
    "LFF": "skyblue",
    "FCFS": "lightgreen",
    "Spread": "orange",
    "Binpack": "red",
    "LWFF": "purple",
    "LWFFVD": "brown",
    "MipLFF": "pink",
    "MipMFF": "gray",
    "RR": "blue",
    "CombMFF": "yellow",
    "CombLFF": "cyan",
    "SJFLFF": "skyblue",
    "SJFFCFS": "lightgreen",
    "SJFSpread": "orange",
    "SJFBinpack": "red",
    "SJFLWFF": "purple",
    "SJFLWFFVD": "brown",
    "SJFMipLFF": "pink",
    "SJFMipMFF": "gray",
    "SJFRR": "blue",
    "SJFCombMFF": "yellow",
    "SJFCombLFF": "cyan"
}

# Columns of the log file
columns = ["Host", "Time", "VM_ID", "CPU_Requested", "RAM_Requested", "BW_Requested"]

# Function to analyze an allocation file
def analyze_allocation_file(file_path):
    # Read the CSV file
    df = pd.read_csv(file_path, names=columns)

    # Calculate allocation statistics
    stats = {
        "Total_VMs": df["VM_ID"].nunique(),
        "Total_Hosts": df["Host"].nunique(),
        "Avg_CPU_Requested": df["CPU_Requested"].mean(),
        "Avg_RAM_Requested": df["RAM_Requested"].mean(),
        "Avg_BW_Requested": df["BW_Requested"].mean(),
        "Host_CPU_Load": df.groupby("Host")["CPU_Requested"].sum().to_dict(),  # CPU load per host
        "Host_RAM_Load": df.groupby("Host")["RAM_Requested"].sum().to_dict(),  # RAM load per host
        "Host_BW_Load": df.groupby("Host")["BW_Requested"].sum().to_dict(),    # BW load per host
    }

    return stats

# Function to plot the comparison graphs
def plot_policy_comparison(stats_dict):
    # Extract data for each policy
    policies = list(stats_dict.keys())

    # Average CPU load per host
    avg_cpu_load = [sum(stats_dict[policy]["Host_CPU_Load"].values()) / len(stats_dict[policy]["Host_CPU_Load"]) for policy in policies]

    # Average RAM load per host
    avg_ram_load = [sum(stats_dict[policy]["Host_RAM_Load"].values()) / len(stats_dict[policy]["Host_RAM_Load"]) for policy in policies]

    # Average BW load per host
    avg_bw_load = [sum(stats_dict[policy]["Host_BW_Load"].values()) / len(stats_dict[policy]["Host_BW_Load"]) for policy in policies]

    # Average number of VMs per host
    avg_vms_per_host = [stats_dict[policy]["Total_VMs"] / stats_dict[policy]["Total_Hosts"] for policy in policies]

    # Create a plot for average CPU load per host
    plt.figure(figsize=(6, 4))
    colors = [policy_colors.get(policy, "black") for policy in policies]
    plt.bar(policies, avg_cpu_load, color=colors, width=0.3)
    plt.title("Average CPU Load per Host by Policy", fontsize=14)
    plt.xlabel("Allocation Policy", fontsize=12)
    plt.ylabel("Average CPU Load (MIPS)", fontsize=12)
    plt.xticks(rotation=45, ha='right')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.show()

    # Create a plot for average RAM load per host
    plt.figure(figsize=(6, 4))
    plt.bar(policies, avg_ram_load, color=colors, width=0.3)
    plt.title("Average RAM Load per Host by Policy", fontsize=14)
    plt.xlabel("Allocation Policy", fontsize=12)
    plt.ylabel("Average RAM Load (MB)", fontsize=12)
    plt.xticks(rotation=45, ha='right')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.show()

    # Create a plot for average BW load per host
    plt.figure(figsize=(6, 4))
    plt.bar(policies, avg_bw_load, color=colors, width=0.3)
    plt.title("Average BW Load per Host by Policy", fontsize=14)
    plt.xlabel("Allocation Policy", fontsize=12)
    plt.ylabel("Average BW Load (bps)", fontsize=12)
    plt.xticks(rotation=45, ha='right')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.show()

    # Create a plot for average number of VMs per host
    plt.figure(figsize=(6, 4))
    plt.bar(policies, avg_vms_per_host, color=colors, width=0.3)
    plt.title("Average Number of VMs per Host by Policy", fontsize=14)
    plt.xlabel("Allocation Policy", fontsize=12)
    plt.ylabel("Average Number of VMs per Host", fontsize=12)
    plt.xticks(rotation=45, ha='right')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.show()

# Main function
def main():
    # Dictionary to store statistics for each policy
    stats_dict = {}

    # Analyze each policy
    for policy in all_policies:
        # Path to the log file for this policy
        file_path = os.path.join(base_path, policy, "host_vm_allocation.csv")

        # Check if the file exists
        if os.path.exists(file_path):
            # Analyze the file
            stats = analyze_allocation_file(file_path)
            stats_dict[policy] = stats
            print(f"Statistics for policy {policy}:")
            print(stats)
        else:
            print(f"File not found for policy {policy}: {file_path}")

    # Plot the comparison graphs
    if stats_dict:
        plot_policy_comparison(stats_dict)
    else:
        print("No files found. Please check the paths and file names.")

# Run the program
if __name__ == "__main__":
    main()