package org.cloudbus.cloudsim.sdn.policies.wfallocation;

import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.sdn.workload.Workload;

/**
 * MAJ Nadia : Politique de scheduling basée sur la priorité définie par
 * l'utilisateur.
 * 
 * Les workloads sont triés par priorité décroissante : plus la valeur est
 * grande,
 * plus le workload est traité tôt.
 * 
 * Format CSV : ajouter une 10e colonne "priority" (entier >= 0).
 * Si la colonne est absente, priority = 0 par défaut (rétrocompatible).
 * 
 * Usage : broker.setSchedulingPolicy(new PriorityWorkloadScheduler());
 */
public class PriorityWorkloadScheduler implements WorkloadSchedulerPolicy {

    @Override
    public List<Workload> sort(List<Workload> workloads) {
        if (workloads == null || workloads.isEmpty())
            return workloads;

        System.out.println("📋 [PriorityWorkloadScheduler] Tri par priorité utilisateur :");

        workloads.sort(Comparator.comparingInt(Workload::getPriority).reversed());

        for (Workload w : workloads) {
            System.out.printf("   → ReqID=%d | Priority=%d%n",
                    w.getRequest().getRequestId(),
                    w.getPriority());
        }

        return workloads;
    }

    @Override
    public String getName() {
        return "PriorityWorkloadScheduler";
    }

    @Override
    public String toString() {
        return getName();
    }
}
