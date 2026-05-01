import os
import re
import csv
import argparse
from pathlib import Path

def parse_log_file(filepath):
    """
    Parses a CloudSimSDN log file to extract global statistics and energy metrics.
    """
    stats = {
        "filename": os.path.basename(filepath),
        "total_requests": None,
        "avg_latency_s": None,
        "max_latency_s": None,
        "sla_violations": None,
        "compliance_rate": None,
        "total_host_energy_wh": None,
        "dataset": "unknown",
        "vm_policy": "unknown",
        "link_policy": "unknown",
        "wf_policy": "unknown"
    }

    # Extract metadata from filename
    fname = stats["filename"]
    
    if fname.startswith("exp_final_dataset-"):
        # Format: exp_final_dataset-small_LFF_First_Priority_2026-04-14_00-59-17.log
        match = re.search(r'dataset-([^_]+)_([^_]+)_([^_]+)_([^_]+)', fname)
        if match:
            stats["dataset"] = match.group(1)
            stats["vm_policy"] = match.group(2)
            stats["link_policy"] = match.group(3)
            stats["wf_policy"] = match.group(4)
    elif fname.startswith("bench_"):
        # Format: bench_MFF_BwAllocN_Priority_small_2026-04-16_01-02-07.log
        # Or: bench_MFF_BwAllocN_Priority_2026-04-15_23-52-40.log
        parts = fname.split('_')
        if len(parts) >= 4:
            stats["vm_policy"] = parts[1]
            stats["link_policy"] = parts[2]
            stats["wf_policy"] = parts[3]
            # If there's a 5th part before the date (YYYY-MM-DD), it's the dataset
            if len(parts) >= 6 and not re.match(r'\d{4}-\d{2}-\d{2}', parts[4]):
                stats["dataset"] = parts[4]
            else:
                stats["dataset"] = "small" # default

    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

            # Global Statistics Block
            # Metric | All
            # Total Requests | 1016
            
            # Helper to clean numeric strings (handle comma as decimal separator)
            def clean_num(s):
                if not s: return None
                return float(s.replace(',', '.').replace(' ', ''))

            # Search patterns
            m_req = re.search(r'\? Total Requests\s+\?\s+([\d, ]+)\s+\?', content)
            if m_req: stats["total_requests"] = int(m_req.group(1).replace(' ', ''))

            m_lat = re.search(r'\? Average Latency \(sec\)\s+\?\s+([\d, ]+\.?\d*)\s+\?', content)
            if m_lat: stats["avg_latency_s"] = clean_num(m_lat.group(1))

            m_max = re.search(r'\? Max Latency \(sec\)\s+\?\s+([\d, ]+\.?\d*)\s+\?', content)
            if m_max: stats["max_latency_s"] = clean_num(m_max.group(1))

            m_sla = re.search(r'\? SLA Violations\s+\?\s+([\d, ]+)\s+\?', content)
            if m_sla: stats["sla_violations"] = int(m_sla.group(1).replace(' ', ''))

            m_comp = re.search(r'\? Compliance Rate\s+\?\s+([\d, ]+%)\s+\?', content)
            if m_comp: stats["compliance_rate"] = m_comp.group(1).strip()

            # Energy
            m_energy = re.search(r'TOTAL HOST ENERGY:\s+([\d,.]+) Wh', content)
            if m_energy: stats["total_host_energy_wh"] = clean_num(m_energy.group(1))

    except Exception as e:
        print(f"Error parsing {filepath}: {e}")

    return stats

def main():
    parser = argparse.ArgumentParser(description="Extract summary statistics from CloudSimSDN .log files")
    parser.add_argument("--dir", required=True, help="Directory to scan for .log files")
    parser.add_argument("--output", default="log_summaries.csv", help="Output CSV file")
    args = parser.parse_args()

    log_files = []
    for root, dirs, files in os.walk(args.dir):
        for file in files:
            if file.endswith(".log") and not file.endswith(".err.log"):
                log_files.append(os.path.join(root, file))

    if not log_files:
        print(f"No .log files found in {args.dir}")
        return

    print(f"Found {len(log_files)} log files. Parsing...")

    results = []
    for log_file in log_files:
        data = parse_log_file(log_file)
        if data["dataset"] == "unknown":
            print(f"  [WARN] Metadata failed for: {data['filename']}")
        results.append(data)

    # Sort results
    results.sort(key=lambda x: (x['dataset'], x['vm_policy'], x['link_policy'], x['wf_policy']))

    # Write to CSV
    keys = results[0].keys()
    with open(args.output, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=keys, delimiter=';')
        writer.writeheader()
        writer.writerows(results)

    print(f"Successfully saved summaries to {args.output}")

    # Generate Markdown Table for display
    print("\n### Summary Table (Markdown)\n")
    headers = ["Dataset", "VM", "Link", "WF", "Avg Latency (s)", "SLA %", "Energy (Wh)"]
    print("| " + " | ".join(headers) + " |")
    print("| " + " | ".join(["---"] * len(headers)) + " |")
    
    for r in results:
        row = [
            r["dataset"], r["vm_policy"], r["link_policy"], r["wf_policy"],
            f"{r['avg_latency_s']:.3f}" if r["avg_latency_s"] is not None else "N/A",
            r["compliance_rate"] if r["compliance_rate"] else "N/A",
            f"{r['total_host_energy_wh']:.2f}" if r["total_host_energy_wh"] is not None else "N/A"
        ]
        print("| " + " | ".join(row) + " |")

if __name__ == "__main__":
    main()
