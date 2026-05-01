package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import java.util.List;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;

/**
 * VM Allocation Policy: Bin Packing Logic
 * Allocates VMs to hosts prioritizing those with the least available resources.
 * This ensures maximum utilization of each host before using the next.
 * 
 * @author Nadia QOUDHADH
 */
public class VmAllocationPolicyBinPack extends VmAllocationPolicyCombinedMostFullFirstV2 {

    public VmAllocationPolicyBinPack(List<? extends Host> list) {
        super(list);
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        if (getVmTable().containsKey(vm.getUid())) { // Vérifie si la VM est déjà allouée
            return false;
        }

        // Bin Packing strategy: prioritize hosts with the least available resources
        Host bestHost = null;
        int bestHostIndex = -1;
        for (int i = 0; i < getHostList().size(); i++) {
            Host host = getHostList().get(i);

            if (host.isSuitableForVm(vm)) {
                if (bestHost == null ||
                        (getFreeRam().get(i) < getFreeRam().get(getHostList().indexOf(bestHost)) &&
                                getFreeBw().get(i) < getFreeBw().get(getHostList().indexOf(bestHost)) &&
                                getFreeMips().get(i) < getFreeMips().get(getHostList().indexOf(bestHost)) &&
                                getFreePes().get(i) < getFreePes().get(getHostList().indexOf(bestHost)))) {
                    bestHost = host;
                    bestHostIndex = i;
                }
            }
        }

        if (bestHost != null) {
            if (bestHost.vmCreate(vm)) {
                getVmTable().put(vm.getUid(), bestHost);
                getUsedPes().put(vm.getUid(), vm.getNumberOfPes());
                getFreePes().set(bestHostIndex, getFreePes().get(bestHostIndex) - vm.getNumberOfPes());

                long requiredMips = (long) vm.getCurrentRequestedTotalMips();
                getUsedMips().put(vm.getUid(), requiredMips);
                getFreeMips().set(bestHostIndex, getFreeMips().get(bestHostIndex) - requiredMips);

                long requiredBw = vm.getCurrentRequestedBw();
                getUsedBw().put(vm.getUid(), requiredBw);
                getFreeBw().set(bestHostIndex, getFreeBw().get(bestHostIndex) - requiredBw);

                long requiredRam = vm.getCurrentRequestedRam();
                getUsedRam().put(vm.getUid(), requiredRam);
                getFreeRam().set(bestHostIndex, getFreeRam().get(bestHostIndex) - requiredRam);

                return true; // Allocation réussie
            }
        }

        // Aucun hôte adéquat trouvé
        System.err.println("Cannot assign this VM (" + vm + ") to any host.");
        return false;
    }

    @Override
    public void deallocateHostForVm(Vm vm) {
        Host host = getVmTable().remove(vm.getUid());
        if (host != null) {
            int idx = getHostList().indexOf(host);
            host.vmDestroy(vm);

            getFreePes().set(idx, getFreePes().get(idx) + getUsedPes().remove(vm.getUid()));
            getFreeMips().set(idx, getFreeMips().get(idx) + getUsedMips().remove(vm.getUid()));
            getFreeBw().set(idx, getFreeBw().get(idx) + getUsedBw().remove(vm.getUid()));
            getFreeRam().set(idx, getFreeRam().get(idx) + getUsedRam().remove(vm.getUid()));
        }
    }
}
