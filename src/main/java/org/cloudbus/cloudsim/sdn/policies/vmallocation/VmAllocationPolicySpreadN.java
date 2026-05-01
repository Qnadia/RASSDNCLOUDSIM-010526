package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import java.util.List;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;

/**
 * VM Allocation Policy: Spread Policy
 * Allocates VMs to hosts prioritizing those with the MOST available resources,
 * distributing VMs across as many hosts as possible.
 * 
 * @author Nadia QOUDHADH
 */
public class VmAllocationPolicySpreadN extends VmAllocationPolicyCombinedMostFullFirstV2 {

    public VmAllocationPolicySpreadN(List<? extends Host> list) {
        super(list);
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        if (getVmTable().containsKey(vm.getUid())) {
            return false;
        }

        int requiredPes = vm.getNumberOfPes();
        double requiredMips = vm.getCurrentRequestedTotalMips();
        long requiredBw = vm.getCurrentRequestedBw();
        long requiredRam = vm.getCurrentRequestedRam();

        // Spread: select host with the MOST available resources (highest combined
        // score)
        Host bestHost = null;
        int bestHostIndex = -1;
        double bestScore = -1;

        for (int i = 0; i < getHostList().size(); i++) {
            Host host = getHostList().get(i);

            // Use isSuitableForVm for proper compatibility check
            if (host.isSuitableForVm(vm)) {
                // Combined score: higher = more free resources = better for spread
                double score = getFreeMips().get(i) + getFreeBw().get(i) + getFreeRam().get(i);
                if (score > bestScore) {
                    bestHost = host;
                    bestHostIndex = i;
                    bestScore = score;
                }
            }
        }

        if (bestHost != null && bestHost.vmCreate(vm)) {
            getVmTable().put(vm.getUid(), bestHost);
            getUsedPes().put(vm.getUid(), requiredPes);
            getFreePes().set(bestHostIndex, getFreePes().get(bestHostIndex) - requiredPes);

            getUsedMips().put(vm.getUid(), (long) requiredMips);
            getFreeMips().set(bestHostIndex, getFreeMips().get(bestHostIndex) - (long) requiredMips);

            getUsedBw().put(vm.getUid(), requiredBw);
            getFreeBw().set(bestHostIndex, getFreeBw().get(bestHostIndex) - requiredBw);

            getUsedRam().put(vm.getUid(), requiredRam);
            getFreeRam().set(bestHostIndex, getFreeRam().get(bestHostIndex) - requiredRam);

            return true;
        }

        System.err.println("Cannot assign this VM (" + vm + ") to any host.");
        return false;
    }

    @Override
    public void deallocateHostForVm(Vm vm) {
        Host host = getVmTable().remove(vm.getUid());
        if (host != null) {
            int idx = getHostList().indexOf(host);
            host.vmDestroy(vm);

            // Mettre à jour les ressources après désallocation
            getFreePes().set(idx, getFreePes().get(idx) + getUsedPes().remove(vm.getUid()));
            getFreeMips().set(idx, getFreeMips().get(idx) + getUsedMips().remove(vm.getUid()));
            getFreeBw().set(idx, getFreeBw().get(idx) + getUsedBw().remove(vm.getUid()));
            getFreeRam().set(idx, getFreeRam().get(idx) + getUsedRam().remove(vm.getUid()));
        }
    }
}
