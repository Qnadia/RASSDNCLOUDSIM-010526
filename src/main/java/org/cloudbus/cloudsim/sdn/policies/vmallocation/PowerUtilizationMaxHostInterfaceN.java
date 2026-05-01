package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

public interface PowerUtilizationMaxHostInterfaceN {
    void reserveResourceForMigration(Host host, SDNVm vm);
    int getMaxNumHostsUsed();
    // Ajoutez d'autres méthodes si nécessaire
}
