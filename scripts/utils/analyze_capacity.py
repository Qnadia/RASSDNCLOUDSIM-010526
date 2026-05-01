import json

phys_file = "datasets/dataset-large-congested/physical.json"
virt_file = "datasets/dataset-large-congested/virtual.json"

with open(phys_file, 'r') as f:
    phys = json.load(f)

with open(virt_file, 'r') as f:
    virt = json.load(f)

total_ram_cap = sum(n.get('ram', 0) for n in phys['nodes'] if n.get('type') == 'host')
total_mips_cap = sum(n.get('mips', 0) for n in phys['nodes'] if n.get('type') == 'host')
total_bw_cap = sum(n.get('bw', 0) for n in phys['nodes'] if n.get('type') == 'host')

total_ram_req = sum(v.get('ram', 0) for v in virt['nodes'] if v.get('type') == 'vm')
total_mips_req = sum(v.get('mips', 0) for v in virt['nodes'] if v.get('type') == 'vm')
total_bw_req = sum(v.get('bw', 0) for v in virt['nodes'] if v.get('type') == 'vm')

print(f"--- Comparison for Large Dataset ---")
print(f"RAM:  Cap={total_ram_cap} | Req={total_ram_req} | Ratio={total_ram_req/total_ram_cap:.2f}")
print(f"MIPS: Cap={total_mips_cap} | Req={total_mips_req} | Ratio={total_mips_req/total_mips_cap:.2f}")
print(f"BW:   Cap={total_bw_cap} | Req={total_bw_req} | Ratio={total_bw_req/total_bw_cap:.2f}")

# Check individual host capacity vs max VM req
max_vm_ram = max(v.get('ram', 0) for v in virt['nodes'] if v.get('type') == 'vm')
max_vm_mips = max(v.get('mips', 0) for v in virt['nodes'] if v.get('type') == 'vm')
max_host_ram = max(n.get('ram', 0) for n in phys['nodes'] if n.get('type') == 'host')
max_host_mips = max(n.get('mips', 0) for n in phys['nodes'] if n.get('type') == 'host')

print(f"Max VM RAM: {max_vm_ram} | Max Host RAM: {max_host_ram}")
print(f"Max VM MIPS: {max_vm_mips} | Max Host MIPS: {max_host_mips}")
