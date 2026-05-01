package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

/**
 * Implémentation simple de VmMigrationPolicy qui ne migre aucune VM.
 */
public class VmMigrationPolicySimpleN extends VmMigrationPolicy {

    @Override
    protected Map<Vm, Host> buildMigrationMap(List<SDNHost> hosts) {
        // Aucune migration définie pour cette politique simple
        return new HashMap<>();
    }


    public void addVmInVmGroup(Vm vm, VmGroup vmGroup) {
        // Implémentation vide ou ajout de logique spécifique si nécessaire
        // Par exemple, vous pouvez simplement enregistrer le groupe ou logger l'action
        // Log.printLine("Ajout de la VM " + vm.getUid() + " au groupe " + vmGroup.getGroupId());
    }
}
