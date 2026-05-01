# IT 12 - Redundant Links and Policy Comparison Fixes

## Date: 2026-03-10
## Objective: Validate Link Selection Policies (First vs BwAllocN) using Redundant Links

### Initial Observations
The simulations for different link selection policies (`First` vs `BwAllocN`) were producing strictly identical latency results, which indicated that the detailed network topology routing logic was either being bypassed or the metrics weren't accumulating properly.

### Applied Modifications

#### 1. Topology & Dataset Configuration (`dataset-redundant`)
- **`dataset-redundant/physical.json`**:
  - Removed bypass direct links between `agg0` and `edge1` to force traffic to pass through the redundant links between `agg1` and `edge1`.
  - Defined two parallel links between `agg1` and `edge1`:
    - One low bandwidth / high latency link: 100 Mbps.
    - One high bandwidth / low latency link: 5 Gbps.

#### 2. Preservation of Link Selection Order
- **`PhysicalTopology.java`**: Changed internal `nodeLinks` and `linkTable` data structures from `HashMultimap` to `LinkedHashMultimap`. This ensures that the insertion order defined in `physical.json` is strictly maintained.
- **`NetworkOperatingSystem.java`**: Replaced the inefficient graph reconstruction in `getNetworkTopology()` with a direct call to `topology.getAdjacentLinks(node)` to ensure the NOS preserves the intended link order.

#### 3. Reporting Scripts
- **`Python-V2/consolidated_report.py`**: 
  - Improved data extraction from `host_utilization.csv` and `path_latency_final.csv`.
  - Added packet delay and average latency metrics to the final output.
- **`compare_priority_link_selection.ps1`**: Configured to run `dataset-redundant` with both `First` and `BwAllocN` policies sequentially.

### Ongoing Analysis - The Identical Results Bug
Despite the wall-clock execution time demonstrating that the algorithms *do* select different links internally (`First` took 84s, `BwAllocN` took 67s on a high-latency test), the final CSV reports still show identical `avg_latency_s` of **0.53s**.

#### Root Cause Identified: 
In `SDNDatacenter.java` (`processNextActivityTransmission`), the network delay is wrongly re-evaluated via `calculateTransmissionDelay()`. This method attempts to find a **direct** physical link (`nos.getLinks(srcNode, dstNode)`) between two end-hosts, which doesn't exist in a hierarchical topology. It silently fails and returns a hardcoded `0.001s` transmission delay.
Because of this hardcoded fast-forwarding, the actual traversal latency accurately modeled in `path_latency_final.csv` doesn't advance `CloudSim.clock()`. Thus, the logged total delays are artificially squashed down to just the CPU processing time.

### Next Steps (Pending User Validation)
1. Replace `calculateTransmissionDelay(tr)` invocation in `SDNDatacenter.java` with the accurately calculated `tr.getFinishTime()` which accumulates all hop penalties.
2. Recompile the project overriding execution policies (`powershell -ExecutionPolicy Bypass -File .\recompile.ps1`).
3. Re-run experiments and regenerate plots to confirm the performance gap.
