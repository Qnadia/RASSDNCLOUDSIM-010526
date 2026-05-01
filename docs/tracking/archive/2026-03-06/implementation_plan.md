# Fix Hierarchical Topology and Simulation Loop Issues

This plan addresses the simulation loop, idle VMs (0 MIs executed), and confusing "Host -1" logs observed when using the `dataset-medium` hierarchical topology.

## User Review Required

> [!IMPORTANT]
> The current simulation uses a "delay-based" approach in `SDNDatacenter` that bypasses real Cloudlet execution. This is why VMs report 0 MIs executed. I will fix the routing so the simulation completes, but the MI count will remain zero unless we revert to traditional Cloudlet submission. 

## Proposed Changes

### [Component] Network Routing & Pathfinding

### [MODIFY] [LinkSelectionPolicyBandwidthAllocationN.java](file:///e:/Workspace/v2/cloudsimsdn090525/cloudsimsdn090525/cloudsimsdn/src/main/java/org/cloudbus/cloudsim/sdn/policies/selectlink/LinkSelectionPolicyBandwidthAllocationN.java)

```java
private Map<String, Link> physicalLinkCache = new HashMap<>();

@Override
public List<Link> findBestPath(Node src, Node dest, Packet pkt) {
    List<Link> path = new ArrayList<>();
    Node current = src;
    int maxHops = 20;

    while (!current.equals(dest) && maxHops > 0) {
        List<Link> nextLinks = current.getRoutingTable().getRoute(dest);
        Link best = null;

        if (nextLinks != null && !nextLinks.isEmpty()) {
            best = selectLink(nextLinks, pkt.getFlowId(), current, dest, current);
        }

        if (best == null) {
            best = findPhysicalLink(current, dest); 
        }

        if (best == null) return Collections.emptyList(); 

        path.add(best);
        current = best.getOtherNode(current);
        maxHops--;
    }
    return path;
}

private Link findPhysicalLink(Node src, Node dest) {
    String cacheKey = src.getName() + "-" + dest.getName();
    if (physicalLinkCache.containsKey(cacheKey)) {
        return physicalLinkCache.get(cacheKey);
    }
    
    for (Link link : src.getAdjacentLinks()) {
        Node neighbor = link.getOtherNode(src);
        if (neighbor.equals(dest)) {
            physicalLinkCache.put(cacheKey, link);
            return link;
        }
        // 2-level recursive search (core->agg->edge)
        if (neighbor.getAdjacentLinks() != null) {
            for (Link l2 : neighbor.getAdjacentLinks()) {
                if (l2.getOtherNode(neighbor).equals(dest)) {
                    physicalLinkCache.put(cacheKey, link);
                    return link;
                }
            }
        }
    }
    return null;
}
```

---

### [Component] Power Monitoring & Host Identification

### [MODIFY] [PowerUtilizationInterface.java](file:///e:/Workspace/v2/cloudsimsdn090525/cloudsimsdn090525/cloudsimsdn/src/main/java/org/cloudbus/cloudsim/sdn/monitor/power/PowerUtilizationInterface.java)
```java
// Add this method to the interface
public void setHost(Node host);
```

#### [MODIFY] [PowerUtilizationMonitor.java](file:///e:/Workspace/v2/cloudsimsdn090525/cloudsimsdn090525/cloudsimsdn/src/main/java/org/cloudbus/cloudsim/sdn/monitor/power/PowerUtilizationMonitor.java)
```java
// Change entityId from final to non-final
private int entityId = -1; // Explicit default

public void setEntityId(int id) {
    this.entityId = id;
}

public boolean hasValidHost() {
    return entityId != -1;
}
```

#### [MODIFY] [VmSchedulerTimeSharedOverSubscriptionDynamicVM.java](file:///e:/Workspace/v2/cloudsimsdn090525/cloudsimsdn090525/cloudsimsdn/src/main/java/org/cloudbus/cloudsim/sdn/VmSchedulerTimeSharedOverSubscriptionDynamicVM.java)
```java
@Override
public void setHost(Node host) {
    if(this.powerMonitor != null) {
        this.powerMonitor.setEntityId(host.getId());
    }
}
```

#### [MODIFY] [SDNHost.java](file:///e:/Workspace/v2/cloudsimsdn090525/cloudsimsdn/src/main/java/org/cloudbus/cloudsim/sdn/physicalcomponents/SDNHost.java)
```java
public SDNHost(...) {
    super(...);
    // synchronize Host ID with VmScheduler monitor
    if(vmScheduler instanceof PowerUtilizationInterface) {
        ((PowerUtilizationInterface)vmScheduler).setHost(this);
    } else {
        // Warning: VM scheduler won't track energy per-host correctly
        Log.printLine("Warning: VmScheduler does not implement PowerUtilizationInterface for Host " + this.getId());
    }
}
```

---

### [Component] Simulation Core

### [MODIFY] [SDNDatacenter.java](file:///e:/Workspace/v2/cloudsimsdn090525/cloudsimsdn090525/cloudsimsdn/src/main/java/org/cloudbus/cloudsim/sdn/physicalcomponents/SDNDatacenter.java)

```java
private int failedRequestsCount = 0;
private Map<String, Integer> failedRequests = new HashMap<>();

private void markRequestAsFailed(Request req, Workload wl, String reason) {
    logDebug(String.format("Request failed at time %.2f: src=%s, dest=%s, reason=%s",
        CloudSim.clock(), wl.submitVmId, req.getDstHostName(), reason));
    
    failedRequestsCount++;
    failedRequests.put(reason, failedRequests.getOrDefault(reason, 0) + 1);
    
    send(req.getUserId(), 0, CloudSimTagsSDN.WORKLOAD_FAILED, wl);
}

// In processWorkloadSubmit:
if (path == null || path.isEmpty()) {
    markRequestAsFailed(req, wl, "No path found in hierarchical topology");
    return;
}
```

## Questions & Answers

### 1. Handling multiple paths in `findBestPath`
In the proposed recursive logic, `current.getRoutingTable().getRoute(dest)` returns all available next-hop links for that destination. `selectLink()` then selects the best individual link (greedy local search). This works well for FatTree and hierarchical topologies where there are multiple "equal cost" paths at the Aggregate layer.

### 2. Available Bandwidth in `selectLink`
Yes, `LinkSelectionPolicyBandwidthAllocationN.selectLink` is already designed to use `link.getFreeBandwidth()`. It selects the link with the highest available capacity.

### 3. Safety in `SDNHost`
If the `vmScheduler` does not implement `PowerUtilizationInterface`, the code will now log a warning. While not ideal for energy metrics, it prevents the simulation from crashing if a generic scheduler is used.

## Verification Plan

### Automated Tests
1. **Routing Test**: Run simulation. Verify console does NOT contain "No path found" for any webX->appX or appX->dbX request.
2. **Log Test**: Check `detailed_energy.csv`. Assert no lines contain `;-1;`.
3. **Completion Test**: Assert simulation time reaches 300.0 without hanging.
4. **MI Check**: Confirm that while the MI reported is 0 (due to delay-based model), the total energy matches the expected active duration.

### Manual Verification
- Check `detailed_energy.csv` to ensure all Host energy records have valid IDs.
- Confirm the simulation finish time in the console output.
