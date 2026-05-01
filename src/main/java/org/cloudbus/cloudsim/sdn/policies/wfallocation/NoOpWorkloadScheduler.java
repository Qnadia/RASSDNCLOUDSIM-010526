package org.cloudbus.cloudsim.sdn.policies.wfallocation;

import org.cloudbus.cloudsim.sdn.workload.Workload;
import java.util.List;

public class NoOpWorkloadScheduler implements WorkloadSchedulerPolicy {
    @Override
    public List<Workload> sort(List<Workload> workloads) {
        // Ne rien faire, renvoyer la liste telle quelle
        return workloads;
    }

    @Override
    public String getName() {
        return "NoOp"; 
    }
}
