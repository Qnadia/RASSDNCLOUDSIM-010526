package org.cloudbus.cloudsim.sdn.policies.selecthost;

import java.util.Arrays;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

/**
 * Implémentation simple de HostSelectionPolicy qui sélectionne le premier hôte disponible.
 */
public class HostSelectionPolicySimpleN extends HostSelectionPolicy {
    
    @Override
    public List<Host> selectHostForVm(SDNVm vm, List<SDNHost> candidateHosts) {
        if (candidateHosts == null || candidateHosts.isEmpty()) {
            return null;
        }
        // Sélectionne le premier hôte disponible
        return Arrays.asList(candidateHosts.get(0));
    }
}
