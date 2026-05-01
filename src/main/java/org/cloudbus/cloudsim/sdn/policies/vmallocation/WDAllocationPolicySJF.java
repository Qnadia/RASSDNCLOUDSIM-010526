package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

/**
 * VM Allocation Policy: SJF (Shortest Job First)
 * Allocates VMs to hosts based on the shortest job (smallest processing size) first.
 */
public class WDAllocationPolicySJF extends VmAllocationPolicyCombinedLeastFullFirstV2 {

    public WDAllocationPolicySJF(List<? extends Host> list) {
        super(list);
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        // Check if the VM is already allocated
        if (getVmTable().containsKey(vm.getUid())) {
            return false;
        }

        // VM resource requirements
        int requiredPes = vm.getNumberOfPes();
        double requiredMips = vm.getCurrentRequestedTotalMips();
        long requiredBw = vm.getCurrentRequestedBw();
        long requiredRam = vm.getCurrentRequestedRam();

        // Sort hosts by available MIPS in ascending order
        List<Host> sortedHosts = getHostList();
        Collections.sort(sortedHosts, Comparator.comparingDouble(Host::getAvailableMips));

        // Try to allocate the VM to the first suitable host
        for (Host host : sortedHosts) {
            // Check if the host has enough resources
            if (host.getAvailableMips() >= requiredMips &&
                host.getBwProvisioner().getAvailableBw() >= requiredBw &&
                host.getRamProvisioner().getAvailableRam() >= requiredRam) {

                // Attempt to create the VM on the host
                if (host.vmCreate(vm)) {
                    // Update allocation tracking
                    getVmTable().put(vm.getUid(), host);
                    getUsedPes().put(vm.getUid(), requiredPes);
                    getFreePes().set(sortedHosts.indexOf(host), getFreePes().get(sortedHosts.indexOf(host)) - requiredPes);

                    getUsedMips().put(vm.getUid(), (long) requiredMips);
                    getFreeMips().set(sortedHosts.indexOf(host), (long) (getFreeMips().get(sortedHosts.indexOf(host)) - requiredMips));

                    getUsedBw().put(vm.getUid(), requiredBw);
                    getFreeBw().set(sortedHosts.indexOf(host), getFreeBw().get(sortedHosts.indexOf(host)) - requiredBw);

                    getUsedRam().put(vm.getUid(), requiredRam);
                    getFreeRam().set(sortedHosts.indexOf(host), getFreeRam().get(sortedHosts.indexOf(host)) - requiredRam);

                    return true; // Allocation successful
                }
            }
        }

        // No suitable host found
        System.err.println("Cannot assign VM " + vm.getId() + " to any host. No host has sufficient resources.");
        return false;
    }
}