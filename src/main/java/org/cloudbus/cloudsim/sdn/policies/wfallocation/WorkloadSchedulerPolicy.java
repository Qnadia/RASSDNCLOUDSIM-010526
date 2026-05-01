package org.cloudbus.cloudsim.sdn.policies.wfallocation;

import java.util.List;
import org.cloudbus.cloudsim.sdn.workload.Workload;


public interface WorkloadSchedulerPolicy {
    List<Workload> sort(List<Workload> workloads);
    String getName(); 
}
