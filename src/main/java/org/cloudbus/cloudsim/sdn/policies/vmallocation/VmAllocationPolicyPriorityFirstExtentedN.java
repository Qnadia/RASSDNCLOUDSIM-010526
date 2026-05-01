package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.container.hostSelectionPolicies.HostSelectionPolicy;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.PowerUtilizationMaxHostInterfaceN;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

import java.util.List;

/**
 * Classe étendue de VmAllocationPolicyPriorityFirst qui implémente PowerUtilizationMaxHostInterface.
 */
public class VmAllocationPolicyPriorityFirstExtentedN extends VmAllocationPolicyPriorityFirst implements PowerUtilizationMaxHostInterface {

    public VmAllocationPolicyPriorityFirstExtentedN(List<? extends Host> hostList,
                                                   org.cloudbus.cloudsim.sdn.policies.selecthost.HostSelectionPolicy hostSelectionPolicy,
                                                   VmMigrationPolicy vmMigrationPolicy) {
        super(hostList, hostSelectionPolicy, vmMigrationPolicy);
    }

    @Override
    public void reserveResourceForMigration(Host host, SDNVm vm) {
        // Implémentation spécifique à votre politique d'allocation
        // Vous pouvez appeler la méthode parent ou ajouter une logique personnalisée
        super.reserveResourceForMigration( host,vm); // Notez l'ordre des paramètres si nécessaire
    }

    @Override
    public int getMaxNumHostsUsed() {
        // Implémentation spécifique
        // Par exemple, retourner le nombre maximum d'hôtes utilisés durant la simulation
        return 0; // Remplacez par votre logique
    }

    // Implémentez d'autres méthodes si nécessaire
}
