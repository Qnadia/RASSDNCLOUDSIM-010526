package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.VmScheduler;
														 
								 

/**
 * VM Allocation Policy: Least Weighted First Fit (LWFF) Logic
   
																															
																										   
																											  
   
							
																					
													
																 
																						
   
				   
 */
public class VmAllocationPolicyLWFFVD extends VmAllocationPolicyCombinedMostFullFirstV2 {

    private int maxNumHostsUsed = 0;

    /**
     * Constructeur de la politique d'allocation LWFF.
     * 
     * @param list Liste des hôtes disponibles pour l'allocation des VMs.
     */
    public VmAllocationPolicyLWFFVD(List<? extends Host> list) {
        super(list);
        updateMaxHostsUsed();
    }

    /**
     * Méthode principale pour allouer un hôte à une VM.
	   
															  
     */
    @Override
    public boolean allocateHostForVm(Vm vm) {
        // Vérifie si la VM est déjà allouée à un hôte
        if (getVmTable().containsKey(vm.getUid())) {
            return false;
        }

        // Étape 1: Filtrer les hôtes faisables en fonction des exigences en ressources de la VM
        List<Host> feasibleHosts = new ArrayList<>();
        List<Integer> feasibleHostIndices = new ArrayList<>();
        for (int i = 0; i < getHostList().size(); i++) {
            Host host = getHostList().get(i);
            if (isSuitableForVm(host, vm)) { // Vérifie si l'hôte peut accueillir la VM
                feasibleHosts.add(host);
                feasibleHostIndices.add(i);
            }
        }

        // Si aucun hôte faisable n'est trouvé, affiche un message d'erreur et retourne False
        if (feasibleHosts.isEmpty()) {
            System.err.println("No feasible hosts found for VM: " + vm.getUid());
            return false;
        }

        // Étape 2: Calculer le profit pour chaque hôte faisable
        List<Profit> profitList = new ArrayList<>();
        for (Host host : feasibleHosts) {
            Profit profit = calculateProfitForHost(host, vm);
            profitList.add(profit);
        }

        // Étape 3: Effectuer une optimisation Pareto sur la liste des profits
        List<Profit> paretoOptimalHosts = getParetoEfficientHosts(profitList);

        // Étape 4: Sélectionner l'hôte avec le moins de temps d'exécution parmi l'ensemble Pareto
        Host bestHost = selectHostWithMinExecutionTime(paretoOptimalHosts, vm);

        // Si un hôte optimal est trouvé, tente de créer la VM sur cet hôte
        if (bestHost != null) {
            int bestHostIndex = getHostList().indexOf(bestHost);
            if (bestHost.vmCreate(vm)) { // Tente de créer la VM sur l'hôte
                // Met à jour les tables d'allocation avec les ressources utilisées
                getVmTable().put(vm.getUid(), bestHost);
                getUsedPes().put(vm.getUid(), vm.getNumberOfPes());
                getFreePes().set(bestHostIndex, getFreePes().get(bestHostIndex) - vm.getNumberOfPes());

                long requiredMips = (long) Math.round(vm.getCurrentRequestedTotalMips());
                getUsedMips().put(vm.getUid(), requiredMips);
                getFreeMips().set(bestHostIndex, getFreeMips().get(bestHostIndex) - requiredMips);

                long requiredBw = vm.getCurrentRequestedBw();
                getUsedBw().put(vm.getUid(), requiredBw);
                getFreeBw().set(bestHostIndex, getFreeBw().get(bestHostIndex) - requiredBw);

                long requiredRam = vm.getCurrentRequestedRam();
                getUsedRam().put(vm.getUid(), requiredRam);
                getFreeRam().set(bestHostIndex, getFreeRam().get(bestHostIndex) - requiredRam);

                updateMaxHostsUsed(); // Mise à jour du nombre maximal d'hôtes utilisés
                return true; // Allocation réussie
            }
        }

        // Si l'allocation échoue, affiche un message d'erreur et retourne False
        System.err.println("Cannot assign VM (" + vm.getUid() + ") to any host.");
        return false;
    }

    /**
     * Méthode pour déallouer une VM d'un hôte.
	   
									 
     */
    @Override
    public void deallocateHostForVm(Vm vm) {
        super.deallocateHostForVm(vm);
        updateMaxHostsUsed();
    }
												  
															 

    /**
     * Met à jour le nombre maximal d'hôtes utilisés.
     */
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

    /**
     * Implémentation de la méthode logMaxNumHostsUsed de l'interface PowerUtilizationMaxHostInterface.
     */
    // @Override
    // public void logMaxNumHostsUsed() {
    //     Log.printLine("Maximum number of hosts used: " + maxNumHostsUsed);
    // }

    // /**
    //  * Implémentation de la méthode getMaxNumHostsUsed de l'interface PowerUtilizationMaxHostInterface.
    //  */
    // @Override
    // public int getMaxNumHostsUsed() {
    //     return maxNumHostsUsed;
    // }

    /**
     * Calcule le profit de l'allocation d'une VM à un hôte, basé sur l'utilisation de la mémoire, du CPU,
     * de la bande passante et des PEs.
	   
													   
								  
																	   
     */
    private Profit calculateProfitForHost(Host host, Vm vm) {
        // Accès aux provisionneurs pour obtenir les ressources
        RamProvisioner ramProvisioner = host.getRamProvisioner();
        BwProvisioner bwProvisioner = host.getBwProvisioner();
        VmScheduler vmScheduler = host.getVmScheduler();

        // Vérification supplémentaire pour éviter les divisions par zéro
        if (ramProvisioner == null || bwProvisioner == null || vmScheduler == null) {
            throw new IllegalStateException("Provisioner or VM Scheduler is not initialized for Host: " + host.getId());
        }

        // Calcul de l'utilisation de la RAM si la VM est allouée
        int totalRam = ramProvisioner.getRam(); // Obtient la RAM totale de l'hôte
        int availableRam = ramProvisioner.getAvailableRam(); // Obtient la RAM disponible de l'hôte
        int requestedRam = vm.getCurrentRequestedRam(); // RAM requise par la VM
        double ramUtil = (double) (totalRam - availableRam + requestedRam) / totalRam; // Utilisation de la RAM après allocation

        // Calcul de l'utilisation du CPU en tenant compte des PEs
        double totalMips = host.getTotalMips(); // Capacité totale en MIPS de l'hôte
        double availableMips = vmScheduler.getAvailableMips(); // MIPS disponibles sur l'hôte
        double usedMips = totalMips - availableMips;
																																 
        double cpuUtil = usedMips / totalMips; // Utilisation du CPU

        // Calcul de l'utilisation de la bande passante si la VM est allouée
        long totalBw = bwProvisioner.getBw(); // Bande passante totale de l'hôte
        long availableBw = bwProvisioner.getAvailableBw(); // Bande passante disponible de l'hôte
        long requestedBw = vm.getCurrentRequestedBw(); // Bande passante requise par la VM
        double bwUtil = (double) (totalBw - availableBw + requestedBw) / totalBw; // Utilisation de la bande passante après allocation

        // Calcul manuel de l'utilisation des PEs
        int totalPes = host.getPeList().size(); // Nombre total de PEs sur l'hôte
        int usedPes = 0;
        for (Vm allocatedVm : host.getVmList()) {
            usedPes += allocatedVm.getNumberOfPes(); // Somme des PEs alloués à toutes les VMs
        }
																				  
        int newTotalPes = usedPes + vm.getNumberOfPes(); // PEs après allocation de la nouvelle VM
        double peUtil = (double) newTotalPes / totalPes; // Utilisation des PEs après allocation

        return new Profit(host, ramUtil, cpuUtil, bwUtil, peUtil);
    }

    /**
     * Classe interne pour stocker les informations de profit d'un hôte.
     */
    private static class Profit {
        Host host;         // L'hôte concerné
        double memUtil;    // Utilisation de la mémoire (RAM)
        double cpuUtil;    // Utilisation du CPU
        double bwUtil;     // Utilisation de la bande passante
        double peUtil;     // Utilisation des Processing Elements (PEs)

        /**
         * Constructeur de la classe Profit.
         */
        public Profit(Host host, double memUtil, double cpuUtil, double bwUtil, double peUtil) {
            this.host = host;
            this.memUtil = memUtil;
            this.cpuUtil = cpuUtil;
            this.bwUtil = bwUtil;
            this.peUtil = peUtil;
        }
    }

    /**
     * Filtre les hôtes basés sur l'efficacité Pareto.
     */
    private List<Profit> getParetoEfficientHosts(List<Profit> profits) {
        List<Profit> paretoSet = new ArrayList<>(profits);

        // Compare chaque paire de profits pour déterminer l'efficacité Pareto
        for (Profit profit1 : profits) {
            for (Profit profit2 : profits) {
                if (profit1 != profit2 && dominates(profit1, profit2)) {
                    paretoSet.remove(profit2);
                }
            }
        }
        return paretoSet;
    }

    /**
     * Vérifie si un profit domine un autre.
									   
	   
												 
												
														   
     */
    private boolean dominates(Profit profit1, Profit profit2) {
        return (profit1.memUtil <= profit2.memUtil &&
                profit1.cpuUtil <= profit2.cpuUtil &&
                profit1.bwUtil <= profit2.bwUtil &&
                profit1.peUtil <= profit2.peUtil) &&
               (profit1.memUtil < profit2.memUtil ||
                profit1.cpuUtil < profit2.cpuUtil ||
                profit1.bwUtil < profit2.bwUtil ||
                profit1.peUtil < profit2.peUtil);
    }

    /**
     * Sélectionne le meilleur hôte parmi l'ensemble Pareto en équilibrant vitesse et répartition.
     */
    private Host selectHostWithMinExecutionTime(List<Profit> paretoHosts, Vm vm) {
        Host bestHost = null;
        double minScore = Double.MAX_VALUE;

        // On cherche le score minimal (combinaison de vitesse et d'occupation)
        for (Profit profit : paretoHosts) {
            double executionTime = calculateExecutionTime(profit.host, vm);
            
            // SCORE : Pondération entre Performance (vitesse) et Spreading (occupation)
            // On utilise cpuUtil et bwUtil du Profit (déjà calculés après allocation théorique)
            double utilizationFactor = (profit.cpuUtil * 0.4) + (profit.bwUtil * 0.4) + (profit.memUtil * 0.2);
            
            // Formule : Temps d'exécution pénalisé par l'occupation actuelle
            // Cela évite de saturer les hôtes rapides si des hôtes standards sont vides
            double combinedScore = executionTime * (1.0 + utilizationFactor * 2.0); 

            if (combinedScore < minScore) {
                minScore = combinedScore;
                bestHost = profit.host;
            }
        }

        return bestHost;
    }

    /**
     * Estime le temps d'exécution d'une VM sur un hôte donné.
												   
	   
																		 
															  
											 
     */
    private double calculateExecutionTime(Host host, Vm vm) {
        double schedulingLatency = 0; // Placeholder pour le calcul de la latence de planification
        double waitTime = 0;           // Placeholder pour le calcul du temps d'attente

        // Calcul du temps d'exécution estimé basé sur la longueur des cloudlets et les MIPS de la VM
        double estimatedRuntime = vm.getCloudletScheduler().getCloudletExecList().stream()
                .mapToDouble(cloudletExec -> {
                    // On suppose que chaque cloudlet a une longueur et nécessite des MIPS spécifiques
                    return cloudletExec.getCloudlet().getCloudletLength() / (vm.getMips() * vm.getNumberOfPes());
                })
                .sum();

        // FIX IT34: Si aucun cloudlet n'est encore soumis (phase d'allocation), on utilise une estimation basique
        if (estimatedRuntime == 0) {
            estimatedRuntime = 1000.0 / (host.getTotalMips() / host.getNumberOfPes()); // Heuristique basique
        }

        return schedulingLatency + waitTime + estimatedRuntime;
    }

    /**
																		 
																								  
	   
								 
											   
															  
												
															  
																	   

		   
											
		   
										 
													 
											 
														  
											 
		   
																								
							 
								   
								   
								 
								 
		 
	 

	   
     * Vérifie si un hôte est adapté pour accueillir une VM en fonction des ressources disponibles.
	   
									 
								  
																  
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

        // Vérifie la bande passante
        if (!host.getBwProvisioner().isSuitableForVm(vm, vm.getBw())) {
            Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + host.getId()
                    + " failed by BW");
            return false;
        }

        // Vérifie la capacité des PEs (Max MIPS requis par la VM)
        if (host.getVmScheduler().getPeCapacity() < vm.getCurrentRequestedMaxMips()) {
            Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + host.getId()
                    + " failed by PE Capacity");
            return false;
        }

        // Vérifie les MIPS disponibles
        if (host.getVmScheduler().getAvailableMips() < vm.getCurrentRequestedTotalMips()) {
            Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + host.getId()
                    + " failed by Available MIPS");
            return false;
        }

        // Vérifie le nombre de PEs disponibles
        if (getAvailablePes(host) < vm.getNumberOfPes()) {
            Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + host.getId()
                    + " failed by Available PEs");
            return false;
        }

        // Toutes les vérifications sont réussies
        return true;
    }

    /**
     * Calcule le nombre de PEs disponibles sur un hôte.
	   
																	
											
     */
    private int getAvailablePes(Host host) {
        int totalPes = host.getPeList().size(); // Nombre total de PEs sur l'hôte
        int usedPes = 0;
        for (Vm allocatedVm : host.getVmList()) {
            usedPes += allocatedVm.getNumberOfPes(); // Somme des PEs alloués à toutes les VMs
        }
        return totalPes - usedPes; // PEs disponibles avant allocation
    }

    
}
