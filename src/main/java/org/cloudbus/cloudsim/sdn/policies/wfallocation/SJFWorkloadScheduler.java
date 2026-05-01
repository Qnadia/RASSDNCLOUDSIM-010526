package org.cloudbus.cloudsim.sdn.policies.wfallocation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.workload.Workload;

public class SJFWorkloadScheduler implements WorkloadSchedulerPolicy {

   
        @Override
        public List<Workload> sort(List<Workload> workloads) {
            System.out.println("🔍 Avant tri (cloudletLen):");
            for (Workload w : workloads) {
                System.out.println("ReqID=" + w.getRequest().getRequestId() +
                    " | CloudletLen=" + w.getRequest().getLastProcessingCloudletLen());
            }

            workloads.sort(Comparator.comparingLong(wl -> wl.getRequest().getLastProcessingCloudletLen()));

            System.out.println("✅ Après tri (cloudletLen):");
            for (Workload w : workloads) {
                System.out.println("ReqID=" + w.getRequest().getRequestId() +
                    " | CloudletLen=" + w.getRequest().getLastProcessingCloudletLen());
            }

            return workloads;
        }
    
        @Override
        public String getName() {
            return "SJF";
        }

//     @Override
//     public void schedule(List<Workload> workloads, SDNBroker broker, int parserId) {
//         Map<Double, List<Workload>> byTime = workloads.stream()
//             .collect(Collectors.groupingBy(w -> w.time));

//         for (Map.Entry<Double, List<Workload>> entry : byTime.entrySet()) {
//             List<Workload> sorted = entry.getValue().stream()
//             .sorted(Comparator.comparingLong(w -> (long) w.getCloudletLength()))
//             .collect(Collectors.toList());

//             for (Workload wl : sorted) {
//                 broker.scheduleWorkload(wl, parserId);
//             }
//         }
// }

}
