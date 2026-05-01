package org.cloudbus.cloudsim.sdn.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.HostFactory;
import org.cloudbus.cloudsim.sdn.HostFactorySimple;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.workload.Workload;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemGroupPriority;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemSimple;
import org.cloudbus.cloudsim.sdn.parsers.PhysicalTopologyParser;
import org.cloudbus.cloudsim.sdn.physicalcomponents.PhysicalTopology;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
import org.cloudbus.cloudsim.sdn.policies.selecthost.HostSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.selecthost.HostSelectionPolicySimpleN;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyBandwidthAllocation;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.PowerUtilizationMaxHostInterfaceN;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyCombinedLeastFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyCombinedMostFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyMipsLeastFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyMipsMostFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyPriorityFirstExtentedN;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmMigrationPolicy;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmMigrationPolicySimpleN;

/**
 * CloudSimSDN example main program corrigée. Elle charge le fichier de topologie physique,
 * le fichier de déploiement d'application et les fichiers de workloads, puis exécute la simulation.
 * Les résultats de la simulation sont affichés dans la console.
 * 
 * Commande d'exécution :
 * java org.cloudbus.cloudsim.sdn.example.SimpleExampleSelectLinkBandwidthV2Group CombLFF dataset-energy/energy-physicalV3.json dataset-energy/energy-virtualV3.json dataset-energy/energy-workloadV3.csv
 * 
 * @author ...
 * @since CloudSimSDN 1.0
 */
public class SimpleExampleSelectLinkBandwidthV2Group extends SimpleExample {
    protected static String physicalTopologyFile = "dataset-energy/energy-physicalV3.json";
    protected static String deploymentFile = "dataset-energy/energy-virtualV3.json";
    protected static String[] workload_files = { 
        "dataset-energy/energy-workloadV3.csv"
    };
    
    protected static List<String> workloads;
    
    private static boolean logEnabled = true;

    public interface VmAllocationPolicyFactory {
        public VmAllocationPolicy create(List<? extends Host> list);
    }
    enum VmAllocationPolicyEnum { CombLFF, CombMFF, MipLFF, MipMFF, OverLFF, OverMFF, LFF, MFF, Overbooking }    
    
    private static void printUsage() {
        String runCmd = "java org.cloudbus.cloudsim.sdn.example.SimpleExampleSelectLinkBandwidthV2Group";
        System.out.format("Usage: %s <LFF|MFF|CombLFF|CombMFF|MipLFF|MipMFF|...> [physical.json] [virtual.json] [workload1.csv] [workload2.csv] [...]\n", runCmd);
    }

    /**
     * Crée le main() pour exécuter cet exemple.
     *
     * @param args les arguments
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {

        workloads = new ArrayList<String>();
        
        // Analyse des arguments système
        if(args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        VmAllocationPolicyEnum vmAllocPolicy = VmAllocationPolicyEnum.valueOf(args[0]);
        if(args.length > 1)
            physicalTopologyFile = args[1];
        if(args.length > 2)
            deploymentFile = args[2];
        if(args.length > 3)
            for(int i=3; i<args.length; i++) {
                workloads.add(args[i]);
            }
        else
            workloads = Arrays.asList(workload_files);
        
        printArguments(physicalTopologyFile, deploymentFile, workloads);
        Log.printLine("Starting CloudSim SDN...");

        try {
            // Initialisation
            int num_user = 1; // nombre d'utilisateurs cloud
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // trace les événements
            
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Instanciation des politiques de sélection d'hôtes et de migration
            HostSelectionPolicy hostSelectionPolicy = new HostSelectionPolicySimpleN(); // Implémentation simple
            VmMigrationPolicy vmMigrationPolicy = new VmMigrationPolicySimpleN(); // Implémentation corrigée
            
            VmAllocationPolicyFactory vmAllocationFac = null;
            NetworkOperatingSystem nos = new NetworkOperatingSystemGroupPriority();
            HostFactory hsFac = new HostFactorySimple();
            LinkSelectionPolicy ls = null;
            
            switch(vmAllocPolicy) {
                case CombMFF:
                case MFF:
                    vmAllocationFac = new VmAllocationPolicyFactory() {
                        @Override
                        public VmAllocationPolicy create(List<? extends Host> hostList) { 
                            return new VmAllocationPolicyCombinedMostFullFirst(hostList); 
                        }
                    };
                    PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
                    ls = new LinkSelectionPolicyBandwidthAllocation();
                    break;
                case CombLFF:
                case LFF:
                    vmAllocationFac = new VmAllocationPolicyFactory() {
                        @Override
                        public VmAllocationPolicy create(List<? extends Host> hostList) { 
                            return new VmAllocationPolicyPriorityFirstExtentedN(
                                hostList, // Liste des hôtes
                                hostSelectionPolicy, // Politique de sélection d'hôtes
                                vmMigrationPolicy // Politique de migration de VMs
                            );
                        }
                    };
                    PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
                    ls = new LinkSelectionPolicyBandwidthAllocation();
                    break;
                case MipMFF:
                    vmAllocationFac = new VmAllocationPolicyFactory() {
                        @Override
                        public VmAllocationPolicy create(List<? extends Host> hostList) { 
                            return new VmAllocationPolicyMipsMostFullFirst(hostList); 
                        }
                    };
                    PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
                    ls = new LinkSelectionPolicyBandwidthAllocation();
                    break;
                case MipLFF:
                    vmAllocationFac = new VmAllocationPolicyFactory() {
                        @Override
                        public VmAllocationPolicy create(List<? extends Host> hostList) { 
                            return new VmAllocationPolicyMipsLeastFullFirst(hostList); 
                        }
                    };
                    PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
                    ls = new LinkSelectionPolicyBandwidthAllocation();
                    break;
                default:
                    System.err.println("Choose proper VM placement policy!");
                    printUsage();
                    System.exit(1);
            }
            
            // Définir la politique de sélection de lien
            nos.setLinkSelectionPolicy(ls);

            // Créer un Datacenter
            SDNDatacenter datacenter = createSDNDatacenter("Datacenter_0", physicalTopologyFile, nos, vmAllocationFac);
            
            if(datacenter == null) {
                Log.printLine("Datacenter creation failed. Exiting simulation.");
                System.exit(1);
            }

            // Broker
            SDNBroker broker = createBroker();
            int brokerId = broker.getId();

            // Soumettre la topologie virtuelle
            broker.submitDeployApplication(datacenter, deploymentFile);
            
            // Soumettre les workloads individuels
            submitWorkloads(broker);
            
            // Démarrer la simulation
            if(!SimpleExampleSelectLinkBandwidthV2Group.logEnabled) 
                Log.disable();
            
            double finishTime = CloudSim.startSimulation();
            CloudSim.stopSimulation();
            Log.enable();
            
            broker.printResult();
            
            Log.printLine(finishTime + ": ========== EXPERIMENT FINISHED ===========");
            
            // Afficher les résultats après la simulation
            List<Workload> wls = broker.getWorkloads();
            if(wls != null)
                LogPrinter.printWorkloadList(wls);
            
            // Afficher l'utilisation totale des hôtes et des switches
            List<Host> hostList = nos.getHostList();
            List<Switch> switchList = nos.getSwitchList();
            LogPrinter.printEnergyConsumption(hostList, switchList, finishTime);

            Log.printLine("Simultanously used hosts:" + (maxHostHandler != null ? maxHostHandler.getMaxNumHostsUsed() : "N/A"));            
            Log.printLine("CloudSim SDN finished!");
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }
    
    public static void submitWorkloads(SDNBroker broker) {
        // Soumettre les fichiers de workload individuellement
        if(workloads != null) {
            for(String workload : workloads)
                broker.submitRequests(workload);
        }
        
        // Ou, Soumettre des groupes de workloads
        // submitGroupWorkloads(broker, WORKLOAD_GROUP_NUM, WORKLOAD_GROUP_PRIORITY, WORKLOAD_GROUP_FILENAME, WORKLOAD_GROUP_FILENAME_BG);
    }
    
    public static void printArguments(String physical, String virtual, List<String> workloads) {
        System.out.println("Data center infrastructure (Physical Topology) : " + physical);
        System.out.println("Virtual Machine and Network requests (Virtual Topology) : " + virtual);
        System.out.println("Workloads: ");
        for(String work : workloads)
            System.out.println("  " + work);        
    }
    
    /**
     * Crée le datacenter.
     *
     * @param name le nom
     * @param physicalTopology le fichier de topologie physique
     * @param snos le Network Operating System
     * @param vmAllocationFactory la fabrique de politique d'allocation des VMs
     *
     * @return le datacenter
     */
    protected static NetworkOperatingSystem nos;
    protected static PowerUtilizationMaxHostInterfaceN maxHostHandler = null;
    protected static SDNDatacenter createSDNDatacenter(String name, String physicalTopology, NetworkOperatingSystem snos, VmAllocationPolicyFactory vmAllocationFactory) {
        // Pré-créer le NOS pour obtenir les informations des hôtes
        nos = snos;
        List<Host> hostList = nos.getHostList();

        String arch = "x86"; // architecture système
        String os = "Linux"; // système d'exploitation
        String vmm = "Xen";
        
        double time_zone = 10.0; // fuseau horaire
        double cost = 3.0; // coût d'utilisation du processeur
        double costPerMem = 0.05; // coût d'utilisation de la mémoire
        double costPerStorage = 0.001; // coût d'utilisation du stockage
        double costPerBw = 0.0; // coût d'utilisation de la bande passante
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // pas d'ajout de SAN pour l'instant

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        // Créer le Datacenter avec les paramètres définis
        SDNDatacenter datacenter = null;
        try {
            VmAllocationPolicy vmPolicy = vmAllocationFactory.create(hostList);
            
            // Vérifier si vmPolicy implémente PowerUtilizationMaxHostInterfaceN
            if (vmPolicy instanceof PowerUtilizationMaxHostInterfaceN) {
                maxHostHandler = (PowerUtilizationMaxHostInterfaceN) vmPolicy;
            } else {
                Log.printLine("VmAllocationPolicy does not implement PowerUtilizationMaxHostInterfaceN");
                // Décider comment gérer ce cas : soit continuer sans maxHostHandler, soit arrêter
                // Par exemple, vous pouvez choisir de continuer :
                maxHostHandler = null;
            }

            datacenter = new SDNDatacenter(name, characteristics, vmPolicy, storageList, 0, nos);
            
            if(datacenter != null) {
                nos.setDatacenter(datacenter);
                Log.printLine("Datacenter created successfully: " + datacenter.getId());
            } else {
                Log.printLine("Datacenter creation failed.");
            }
            
            // Assigner la topologie à la politique d'allocation si nécessaire
            if(vmPolicy instanceof VmAllocationPolicyPriorityFirstExtentedN) {
                PhysicalTopology topology = nos.getPhysicalTopology();
                if(topology != null) {
                    ((VmAllocationPolicyPriorityFirstExtentedN) vmPolicy).setTopology(topology);
                    Log.printLine("Topology set for VmAllocationPolicyPriorityFirstExtendedN.");
                } else {
                    Log.printLine("PhysicalTopology is null. Cannot set topology for VmAllocationPolicyPriorityFirstExtendedN.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return datacenter;
    }

    /**
     * Crée le broker.
     *
     * @return le broker
     */
    protected static SDNBroker createBroker() {
        SDNBroker broker = null;
        try {
            broker = new SDNBroker("Broker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }
    
    // Sous-développement
    /*
    static class WorkloadGroup { ... }
    static LinkedList<WorkloadGroup> workloadGroups = new LinkedList<WorkloadGroup>();
    */
}
