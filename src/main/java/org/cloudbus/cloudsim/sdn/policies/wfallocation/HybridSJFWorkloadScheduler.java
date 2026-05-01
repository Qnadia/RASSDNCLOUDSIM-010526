package org.cloudbus.cloudsim.sdn.policies.wfallocation;

import org.cloudbus.cloudsim.sdn.workload.Request;
import org.cloudbus.cloudsim.sdn.workload.Workload; 

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HybridSJFWorkloadScheduler implements WorkloadSchedulerPolicy {

    @Override
    public List<Workload> sort(List<Workload> workloads) {
        if (workloads == null || workloads.isEmpty()) return workloads;

        System.out.println("🔍 Avant tri (cloudletLen):");
        for (Workload w : workloads) {
            System.out.println("ReqID=" + w.getRequest().getRequestId() +
                " | CloudletLen=" + w.getRequest().getLastProcessingCloudletLen());
        }


        Collections.sort(workloads, new Comparator<Workload>() {
            @Override
            public int compare(Workload w1, Workload w2) {
                Request r1 = w1.getRequest();
                Request r2 = w2.getRequest();

                long hybrid1 = r1.getLastProcessingCloudletLen() + r1.getPacketSizeBytes();
                long hybrid2 = r2.getLastProcessingCloudletLen() + r2.getPacketSizeBytes();

                //return Long.compare(hybrid1, hybrid2);
                return Long.compare(hybrid2, hybrid1); // ordre décroissant (plus grand d'abord)

            }
        });

        System.out.println("✅ Après tri (cloudletLen):");
        System.out.println("🔍 Hybrid sort order:");
        for (Workload w : workloads) {
            //System.out.println("ReqID=" + w.getRequest().getRequestId() +
            //    " | HybridScore=" + (w.getRequest().getLastProcessingCloudletLen() + w.getRequest().getPacketSizeBytes()));
                System.out.println("Hybrid Score = " + 
                (w.getRequest().getLastProcessingCloudletLen() + w.getRequest().getPacketSizeBytes()));
            
        }
        

        return workloads;
    }

    @Override
    public String getName() {
        return "HybridSJF (CPU + Network)";
    }

    @Override
    public String toString() {
        return getName();
    }
}
