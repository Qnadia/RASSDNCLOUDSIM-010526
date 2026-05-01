import csv
import sys

def analyze(path):
    lengths = []
    with open(path, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            lengths.append(int(row['len_cloudlet']))
    if not lengths: return "Empty"
    return {
        "count": len(lengths),
        "min": min(lengths),
        "max": max(lengths),
        "avg": sum(lengths) / len(lengths)
    }

print("Large:", analyze("e:/Workspace/v2/cloudsimsdn-research/datasets/dataset-large/workload.csv"))
print("Medium:", analyze("e:/Workspace/v2/cloudsimsdn-research/datasets/dataset-medium/workload.csv"))
print("Small:", analyze("e:/Workspace/v2/cloudsimsdn-research/datasets/dataset-small/workload.csv"))
