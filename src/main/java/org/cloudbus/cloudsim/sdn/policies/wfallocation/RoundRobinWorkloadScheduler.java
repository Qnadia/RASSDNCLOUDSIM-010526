package org.cloudbus.cloudsim.sdn.policies.wfallocation;

import org.cloudbus.cloudsim.sdn.workload.Workload;
import java.util.List;
import java.util.ArrayList;

public class RoundRobinWorkloadScheduler implements WorkloadSchedulerPolicy {

    private int nextIndex = 0;

    @Override
    public List<Workload> sort(List<Workload> workloads) {
        if (workloads == null || workloads.isEmpty()) return workloads;

        List<Workload> sorted = new ArrayList<>(workloads.size());

        // Créer une liste temporaire pour simuler un round robin (par VM ou juste par position)
        int n = workloads.size();
        boolean[] used = new boolean[n];
        int count = 0;

        System.out.println("🔄 Ordre initial des workloads (Round Robin Simulation):");
        while (count < n) {
            if (!used[nextIndex]) {
                sorted.add(workloads.get(nextIndex));
                used[nextIndex] = true;
                System.out.println("→ ReqID=" + workloads.get(nextIndex).getRequest().getRequestId());
                count++;
            }
            nextIndex = (nextIndex + 1) % n;
        }

        return sorted;
    }

    @Override
    public String getName() {
        return "RoundRobinScheduler";
    }

    @Override
    public String toString() {
        return getName();
    }
}

