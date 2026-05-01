package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;

/**
 * VM Allocation Policy: Least Weighted First Fit (LWFF) Logic
 * Utilise uniquement le CPU et la RAM pour l'allocation des VMs.
 */
public class VmAllocationPolicyLWFFF extends VmAllocationPolicyCombinedLeastFullFirstV2 implements PowerUtilizationMaxHostInterface {

    private int maxNumHostsUsed = 0;

    /**
     * Constructeur de la politique d'allocation LWFF.
     * 
     * @param list Liste des hôtes disponibles pour l'allocation des VMs.
     */
    public VmAllocationPolicyLWFFF(List<? extends Host> list) {
        super(list);
        updateMaxHostsUsed();
    }

    /**
     * Méthode principale pour allouer un hôte à une VM.
     */
    @Override
    public boolean allocateHostForVm(Vm vm) {
        if (getVmTable().containsKey(vm.getUid())) {
            return false;
        }

        List<Host> feasibleHosts = new ArrayList<>();
        for (Host host : getHostList()) {
            if (isSuitableForVm(host, vm)) {
                feasibleHosts.add(host);
            }
        }

        if (feasibleHosts.isEmpty()) {
            System.err.println("No feasible hosts found for VM: " + vm.getUid());
            return false;
        }

        // Étape 2: Sélectionner l'hôte avec la charge pondérée minimale (Least Weighted Load)
        // Contrairement à LFF (0.5 CPU / 0.5 BW), LWFF ici privilégie la disponibilité BW (0.6)
        Host bestHost = null;
        double minWeightedLoad = Double.MAX_VALUE;

        for (Host host : feasibleHosts) {
            double load = calculateWeightedLoad(host, vm);
            if (load < minWeightedLoad) {
                minWeightedLoad = load;
                bestHost = host;
            }
        }

        if (bestHost != null) {
            int bestHostIndex = getHostList().indexOf(bestHost);
            if (bestHost.vmCreate(vm)) {
                getVmTable().put(vm.getUid(), bestHost);

                long requiredMips = (long) Math.round(vm.getCurrentRequestedTotalMips());
                getUsedMips().put(vm.getUid(), requiredMips);
                getFreeMips().set(bestHostIndex, getFreeMips().get(bestHostIndex) - requiredMips);

                long requiredRam = vm.getCurrentRequestedRam();
                getUsedRam().put(vm.getUid(), requiredRam);
                getFreeRam().set(bestHostIndex, getFreeRam().get(bestHostIndex) - requiredRam);

                long requiredBw = vm.getBw();
                getUsedBw().put(vm.getUid(), requiredBw);
                getFreeBw().set(bestHostIndex, getFreeBw().get(bestHostIndex) - requiredBw);

                updateMaxHostsUsed();
                return true;
            }
        }

        return false;
    }

    private double calculateWeightedLoad(Host host, Vm vm) {
        // Calcul des taux d'utilisation actuels (0 = vide, 1 = plein)
        double cpuUtil = (host.getTotalMips() - host.getVmScheduler().getAvailableMips()) / host.getTotalMips();
        double ramUtil = (double) (host.getRamProvisioner().getRam() - host.getRamProvisioner().getAvailableRam()) / host.getRamProvisioner().getRam();
        double bwUtil = (double) (host.getBwProvisioner().getBw() - host.getBwProvisioner().getAvailableBw()) / host.getBwProvisioner().getBw();

        // Poids spécifiques pour LWFF : Priorité aux MIPS pour réduire la latence
        double wCpu = 0.01;
        double wRam = 0.01;
        double wBw = 0.01;
        double wWorkload = 0.97; // Poids majeur pour la puissance de calcul

        double weightedResLoad = (cpuUtil * wCpu) + (ramUtil * wRam) + (bwUtil * wBw);
        double workloadLoad = calculateExecutionTime(host, vm); // Retrait de la division par 1000 pour garder l'impact

        double weight = (wWorkload * workloadLoad) + (wCpu * cpuUtil) + (wBw * bwUtil) + (wRam * ramUtil);
		
		return weight;
    }

    /**
     * Estime le temps d'exécution d'une VM sur un hôte donné.
     * FIX IT34: Retourne une valeur basée sur la capacité MIPS si aucun cloudlet n'est actif.
     */
    private double calculateExecutionTime(Host host, Vm vm) {
        double estimatedRuntime = vm.getCloudletScheduler().getCloudletExecList().stream()
                .mapToDouble(cloudletExec -> {
                    return cloudletExec.getCloudlet().getCloudletLength() / (vm.getMips() * vm.getNumberOfPes());
                })
                .sum();
        
        // Si aucun cloudlet n'est encore soumis (phase d'allocation), on utilise une estimation basique
        if (estimatedRuntime == 0) {
            estimatedRuntime = 1000.0 / (host.getTotalMips() / host.getNumberOfPes()); // Heuristique
        }

        return estimatedRuntime;
    }

    /**
     * Vérifie si un hôte est adapté pour accueillir une VM en fonction des ressources disponibles (CPU et RAM).
     */
    private boolean isSuitableForVm(Host host, Vm vm) {
        // Vérifie la capacité de stockage
        if (host.getStorage() < vm.getSize()) {
            Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + host.getId()
                    + " failed by storage");
            return false;
        }

        // Vérifie la RAM
        if (!host.getRamProvisioner().isSuitableForVm(vm, vm.getCurrentRequestedRam())) {
            Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + host.getId()
                    + " failed by RAM");
            return false;
        }

        // FIX IT25: Add BW check (Critical oversight fix)
        if (!host.getBwProvisioner().isSuitableForVm(vm, vm.getBw())) {
            Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + host.getId()
                    + " failed by BW");
            return false;
        }

        // Vérifie les MIPS disponibles
        if (host.getVmScheduler().getAvailableMips() < vm.getCurrentRequestedTotalMips()) {
            Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + host.getId()
                    + " failed by Available MIPS");
            return false;
        }

        // Toutes les vérifications sont réussies
        return true;
    }

    private void updateMaxHostsUsed() {
        int currentUsedHosts = 0;
        for (Host host : getHostList()) {
            if (!host.getVmList().isEmpty()) {
                currentUsedHosts++;
            }
        }
        if (currentUsedHosts > maxNumHostsUsed) {
            maxNumHostsUsed = currentUsedHosts;
        }
    }

    @Override
    public void logMaxNumHostsUsed() {
        Log.printLine("Maximum number of hosts used (LWFF): " + maxNumHostsUsed);
    }

    @Override
    public int getMaxNumHostsUsed() {
        return maxNumHostsUsed;
    }

    /**
     * Calcule le nombre de PEs disponibles sur un hôte.
     * (Optionnel: Si vous souhaitez également ignorer les PEs, vous pouvez supprimer cette méthode.)
     */
    /*
    private int getAvailablePes(Host host) {
        int totalPes = host.getPeList().size(); // Nombre total de PEs sur l'hôte
        int usedPes = 0;
        for (Vm allocatedVm : host.getVmList()) {
            usedPes += allocatedVm.getNumberOfPes(); // Somme des PEs alloués à toutes les VMs
        }
        return totalPes - usedPes; // PEs disponibles avant allocation
    }
    */
}
