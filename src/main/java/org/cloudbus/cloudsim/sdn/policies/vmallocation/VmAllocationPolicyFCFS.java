package org.cloudbus.cloudsim.sdn.policies.vmallocation;


import java.util.List;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

/**
 * VM Allocation Policy: FCFS (First-Come, First-Served)
 * Allocates VMs to the first available host that satisfies its requirements.
 * 
 * @author Nadia QOUDHADH
 * La politique FCFS parcourt la liste des hôtes dans l’ordre de leur apparition (du premier 
 * hôte vers le dernier) et tente d’allouer la VM sur le premier hôte qui a suffisamment de 
 * ressources pour répondre à ses besoins.
 */
public class VmAllocationPolicyFCFS extends VmAllocationPolicyCombinedMostFullFirstV2 {

    public VmAllocationPolicyFCFS(List<? extends Host> list) {
        super(list);
    }

    /**
     * Allocates a host for a given VM using FCFS.
     * 
     * @param vm VM specification
     * @return true if the host could be allocated; false otherwise
     */
    @Override
    public boolean allocateHostForVm(Vm vm) {
        if (getVmTable().containsKey(vm.getUid())) { // If this VM was already created
            return false;
        }

        int numHosts = getHostList().size();

        // VM resource requirements
        int requiredPes = vm.getNumberOfPes();
        double requiredMips = vm.getCurrentRequestedTotalMips();
        long requiredBw = vm.getCurrentRequestedBw();
        long requiredRam = vm.getCurrentRequestedRam();

        for (int i = 0; i < numHosts; i++) {
            Host host = getHostList().get(i);

            // Check whether the host can hold this VM
            if (getFreeMips().get(i) >= requiredMips 
                && getFreeBw().get(i) >= requiredBw 
                && getFreeRam().get(i) >= requiredRam) {
                
                // Allocate the VM to this host
                if (host.vmCreate(vm)) {
                    getVmTable().put(vm.getUid(), host);
                    getUsedPes().put(vm.getUid(), requiredPes);
                    getFreePes().set(i, getFreePes().get(i) - requiredPes);

                    getUsedMips().put(vm.getUid(), (long) requiredMips);
                    getFreeMips().set(i, (long) (getFreeMips().get(i) - requiredMips));

                    getUsedBw().put(vm.getUid(), (long) requiredBw);
                    getFreeBw().set(i, (long) (getFreeBw().get(i) - requiredBw));

                    getUsedRam().put(vm.getUid(), requiredRam); // Track RAM usage
                    getFreeRam().set(i, getFreeRam().get(i) - requiredRam); // Update free RAM

                    return true; // Allocation successful
                }
            }
        }

        // No suitable host found
        System.err.println("Cannot assign this VM (" + vm + ") to any host. NumHosts=" + numHosts);
        return false;
    }
}
