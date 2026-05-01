package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

import java.util.List;

/**
 * VM Allocation Policy: Round-Robin Logic
 * Allocates VMs to hosts in a circular manner, ensuring that each host is considered in turn.
 * This policy is efficient and ensures a balanced distribution of VMs across hosts.
 *
 * @author Your Name
 */
public class VmAllocationPolicyRR extends VmAllocationPolicyCombinedMostFullFirstV2 {

    /**
     * The index of the last host used to place a VM.
     */
    private int lastHostIndex;

    /**
     * Constructor for the Round-Robin VM allocation policy.
     *
     * @param list The list of hosts available for VM allocation.
     */
    public VmAllocationPolicyRR(List<? extends Host> list) {
        super(list);
    }

    /**
     * Allocates a host for a given VM using the Round-Robin strategy.
     *
     * @param vm The VM to allocate.
     * @return true if the host could be allocated; false otherwise.
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

        // Try to allocate the VM to a host in a circular manner
        for (int i = 0; i < numHosts; i++) {
            Host host = getHostList().get(lastHostIndex);
            lastHostIndex = (lastHostIndex + 1) % numHosts; // Move to the next host in a circular manner

            // Check whether the host can hold this VM
            if (getFreeMips().get(lastHostIndex) >= requiredMips 
                && getFreeBw().get(lastHostIndex) >= requiredBw 
                && getFreeRam().get(lastHostIndex) >= requiredRam) {
                
                // Allocate the VM to this host
                if (host.vmCreate(vm)) {
                    getVmTable().put(vm.getUid(), host);
                    getUsedPes().put(vm.getUid(), requiredPes);
                    getFreePes().set(lastHostIndex, getFreePes().get(lastHostIndex) - requiredPes);

                    getUsedMips().put(vm.getUid(), (long) requiredMips);
                    getFreeMips().set(lastHostIndex, (long) (getFreeMips().get(lastHostIndex) - requiredMips));

                    getUsedBw().put(vm.getUid(), (long) requiredBw);
                    getFreeBw().set(lastHostIndex, (long) (getFreeBw().get(lastHostIndex) - requiredBw));

                    getUsedRam().put(vm.getUid(), requiredRam); // Track RAM usage
                    getFreeRam().set(lastHostIndex, getFreeRam().get(lastHostIndex) - requiredRam); // Update free RAM

                    return true; // Allocation successful
                }
            }
        }

        // No suitable host found
        System.err.println("Cannot assign this VM (" + vm + ") to any host. NumHosts=" + numHosts);
        return false;
    }
}