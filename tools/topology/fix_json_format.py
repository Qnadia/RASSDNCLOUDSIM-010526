import json
import os

def fix_json(file_path):
    print(f"Processing {file_path}...")
    if not os.path.exists(file_path):
        print(f"Warning: {file_path} not found.")
        return

    with open(file_path, 'r', encoding='utf-8') as f:
        # Load JSON, handling potential BOM or garbage
        content = f.read().strip()
        # If file starts with BOM, strip it
        if content.startswith('\ufeff'):
            content = content[1:]
        data = json.loads(content)

    vm_count = 0
    for node in data.get('nodes', []):
        if node.get('type') == 'vm':
            # Force float to avoid ClassCastException in Java (Long vs Double)
            node['starttime'] = float(round(vm_count * 0.1, 1))
            # Also ensure endtime is float
            if 'endtime' in node:
                node['endtime'] = float(node['endtime'])
            vm_count += 1

    # Write back with strict encoding and clean formatting (4 spaces)
    with open(file_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=4, ensure_ascii=False)
    
    print(f"DONE: Successfully updated {vm_count} VMs in {file_path}")

datasets = ["dataset-small", "dataset-medium", "dataset-large"]
for ds in datasets:
    target = os.path.join(ds, "virtual.json")
    fix_json(target)
