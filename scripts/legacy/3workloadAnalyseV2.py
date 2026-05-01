import pandas as pd
import os
import matplotlib.pyplot as plt

# Base path containing the policies
base_path = r"D:/Workspace/cloudsimsdn/Exp5-SJF"

# Expected columns in the files
columns = [
    "Workload_ID", "App_ID", "SubmitTime", "Pr:StartTime", "Pr:EndTime", "Pr:CPUTime",
    "Pr:Size", "Tr:StartTime", "Tr:EndTime", "Tr:NetworkTime", "Tr:Size", "Tr:Channel",
    "Pr:StartTime_Dup", "Pr:EndTime_Dup", "Pr:CPUTime_Dup", "Pr:Size_Dup", "ResponseTime"
]

# Initialize a list to store the results
all_policy_results = []

# Define a color palette for policies
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

# Function to analyze a specific file
def analyze_policy_file(file_path, policy_name):
    try:
        # Load the CSV file using the file headers
        data = pd.read_csv(file_path, sep="|")

        # Display the columns of the CSV file
        print(f"[DEBUG] Columns in file {policy_name}: {data.columns}")

        # Check if the ResponseTime column exists
        if "ResponseTime" not in data.columns:
            print(f"[ERROR] Column 'ResponseTime' missing in {policy_name}.")
            return

        # Remove unnecessary (empty) columns
        data = data.dropna(axis=1, how="all")

        # Calculate the necessary statistics
        stats = {
            "Policy": policy_name,
            "Average ResponseTime": data["ResponseTime"].mean(),
            "Median ResponseTime": data["ResponseTime"].median(),
            "Max ResponseTime": data["ResponseTime"].max(),
            "Min ResponseTime": data["ResponseTime"].min(),
            "Total Workloads": data["Workload_ID"].nunique(),
            "Total NetworkTime": data["Tr:NetworkTime"].sum(),
            "Average NetworkTime": data["Tr:NetworkTime"].mean()
        }
        all_policy_results.append(stats)
        print(f"[INFO] Analysis completed for {policy_name}.")
    except Exception as e:
        print(f"[ERROR] Unable to analyze {policy_name}: {e}")

# Traverse all subdirectories to find cleaned_result.csv files
for root, dirs, files in os.walk(base_path):
    print(f"[DEBUG] Exploring directory: {root}")  # Display the current directory
    for file in files:
        print(f"[DEBUG] File found: {file}")  # Display each file found
        if file == "cleaned_result.csv":  # Check if the file matches
            # Extract the policy name from the directory structure
            policy_name = os.path.basename(os.path.dirname(root))
            file_path = os.path.join(root, file)
            print(f"[DEBUG] cleaned_result.csv file found: {file_path}")  # Display the full path
            analyze_policy_file(file_path, policy_name)

# Check if results have been collected
if all_policy_results:
    # Create a DataFrame to group the results
    results_df = pd.DataFrame(all_policy_results)

    # Save the results to a CSV file
    output_file = os.path.join(base_path, "summary_analysis.csv")
    results_df.to_csv(output_file, index=False)
    print(f"[INFO] Results saved in {output_file}.")

    # Visualization of results
    # 1. Average response time per policy
    plt.figure(figsize=(6, 4))
    colors = [policy_colors.get(policy, "black") for policy in results_df["Policy"]]
    results_df.set_index("Policy")["Average ResponseTime"].plot(kind="bar", color=colors, edgecolor="black", width=0.3)
    plt.title("Execution Time")
    plt.ylabel("Average Response Time")
    plt.xlabel("Policies")
    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.show()

    # 2. Total network time per policy
    plt.figure(figsize=(6, 4))
    results_df.set_index("Policy")["Total NetworkTime"].plot(kind="bar", color=colors, edgecolor="black")
    plt.title("Comparison of Total Network Times by Policy")
    plt.ylabel("Total Network Time")
    plt.xlabel("Policies")
    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.show()

    # 3. Total number of workloads per policy
    plt.figure(figsize=(6, 4))
    results_df.set_index("Policy")["Total Workloads"].plot(kind="bar", color=colors, edgecolor="black")
    plt.title("Total Number of Workloads by Policy")
    plt.ylabel("Number of Workloads")
    plt.xlabel("Policies")
    plt.xticks(rotation=45)
    plt.tight_layout()
    plt.show()
else:
    print("[ERROR] No cleaned_result.csv files found.")