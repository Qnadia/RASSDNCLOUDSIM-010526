package org.cloudbus.cloudsim.sdn.example.SSLAB;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmAllocationPolicyLWFF;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.HostFactory;
import org.cloudbus.cloudsim.sdn.HostFactorySimple;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.example.LogPrinter;
import org.cloudbus.cloudsim.sdn.workload.Activity;
import org.cloudbus.cloudsim.sdn.workload.Workload;
import org.cloudbus.cloudsim.sdn.workload.WorkloadReader;
import org.cloudbus.cloudsim.sdn.workload.WorkloadResultWriter;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemGroupPriority;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemSimple;
import org.cloudbus.cloudsim.sdn.parsers.PhysicalTopologyParser;
import org.cloudbus.cloudsim.sdn.parsers.VirtualTopologyParser;
import org.cloudbus.cloudsim.sdn.parsers.WorkloadParser;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyBandwidthAllocation;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyBandwidthAllocationN;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyBinPack;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyCombinedLeastFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyCombinedLeastFullFirstV2;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyCombinedMostFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyFCFS;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyLWFFF;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyLWFFVD;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyMipsLeastFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyMipsMostFullFirst;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicyRR;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.WDAllocationPolicySJF;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicySpreadN;

/**
 * Classe pour gérer la politique de planification SJF.
 */
public class SimpleExampleSelectLinkBandwidthSJF extends SimpleExampleBase {

    /**
     * Enumération pour la politique SJF.
     */
    enum VmAllocationPolicyEnum {
        SJF
    }

    /**
     * Définit les fichiers de configuration par défaut.
     */
    protected static String physicalTopologyFile = "dataset-energy/3energy-physicalH10-latency.json";
    protected static String deploymentFile = "dataset-energy/1energy-virtualV40.json";
    protected static String[] workload_files = {
            "dataset-energy/2energy-workload120.csv"
    };

    /**
     * Affiche la syntaxe d'utilisation de l'application.
     */
    private static void printUsage() {
        String runCmd = "java org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidthSJF";
        System.out.format("Usage: %s <SJF> [physical.json] [virtual.json] [workload1.csv] [workload2.csv] [...]\n",
                runCmd);
    }

    /**
     * Point d'entrée principal pour la politique SJF.
     *
     * @param args Les arguments de la ligne de commande.
     */
    public static void main(String[] args) {

        List<String> workloads = new ArrayList<>();

        // Analyse des arguments système
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        VmAllocationPolicyEnum vmAllocPolicy = null;
        try {
            vmAllocPolicy = VmAllocationPolicyEnum.valueOf(args[0]);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid VM Allocation Policy: " + args[0]);
            printUsage();
            System.exit(1);
        }

        if (args.length > 1)
            physicalTopologyFile = args[1];
        if (args.length > 2)
            deploymentFile = args[2];
        if (args.length > 3)
            for (int i = 3; i < args.length; i++) {
                workloads.add(args[i]);
            }
        else {
            if (workload_files != null && workload_files.length > 0) {
                workloads = Arrays.asList(workload_files);
            } else {
                Log.printLine("Aucun fichier de charge de travail fourni et workload_files est null ou vide.");
                printUsage();
                System.exit(1);
            }
        }

        printArguments(physicalTopologyFile, deploymentFile, workloads);
        Log.printLine("Starting CloudSim SDN with SJF policy...");

        try {
            // Initialisation de CloudSim
            int num_user = 1; // nombre d'utilisateurs cloud
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // flag de traçage des événements
            CloudSim.init(num_user, calendar, trace_flag);

            VmAllocationPolicyFactory vmAllocationFac = null;
            switch (vmAllocPolicy) {
                case SJF:
                    vmAllocationFac = hostList -> new WDAllocationPolicySJF(hostList);
                    break;
                // Ajouter d'autres cas si nécessaire
                default:
                    System.err.println("Unsupported VM Allocation Policy: " + vmAllocPolicy);
                    printUsage();
                    System.exit(1);
            }

            NetworkOperatingSystem nos = new NetworkOperatingSystemSimple();
            HostFactory hsFac = new HostFactorySimple();
            LinkSelectionPolicyBandwidthAllocation ls = new LinkSelectionPolicyBandwidthAllocation();

            // Charger la topologie physique
            loadPhysicalTopology(physicalTopologyFile, nos, hsFac);

            // Définir la politique de sélection de lien
            nos.setLinkSelectionPolicy(ls);

            // Créer le Datacenter
            SDNDatacenter datacenter = createSDNDatacenter("Datacenter_SJF", physicalTopologyFile, nos,
                    vmAllocationFac);

            // Créer le Broker
            SDNBroker broker = createBroker();
            if (broker == null) {
                System.err.println("Failed to create broker.");
                System.exit(1);
            }
            int brokerId = broker.getId();

            // Soumettre la topologie virtuelle
            broker.submitDeployApplicationn(datacenter, deploymentFile);

            // Soumettre les workloads avec SJF
            submitWorkloadsSJF(broker, workload_files);

            // Démarrer la simulation
            if (!SimpleExampleSelectLinkBandwidthSJF.logEnabled)
                Log.disable();

            double finishTime = CloudSim.startSimulation();
            CloudSim.stopSimulation();
            Log.enable();

            broker.printResult();

            Log.printLine(finishTime + ": ========== SJF EXPERIMENT FINISHED ===========");

            // Afficher les résultats après la simulation
            List<Workload> wls = broker.getWorkloads();
            if (wls != null)
                LogPrinter.printWorkloadList(wls);

            // Afficher l'utilisation totale des hôtes et des switches
            List<Host> hostList = nos.getHostList();
            List<Switch> switchList = nos.getSwitchList();
            LogPrinter.printEnergyConsumption(hostList, switchList, finishTime);

            Log.printLine("Simultaneously used hosts:" + maxHostHandler.getMaxNumHostsUsed());
            Log.printLine("CloudSim SDN with SJF finished!");

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }

    /**
     * Soumet les workloads en utilisant la politique SJF.
     *
     * @param broker        Instance de SDNBroker
     * @param workloadFiles Tableau des fichiers de workloads
     */
    /**
     * Soumet les workloads en utilisant la politique SJF.
     *
     * @param broker        Instance de SDNBroker
     * @param workloadFiles Tableau des fichiers de workloads
     */
    public static void submitWorkloadsSJF(SDNBroker broker, String[] workloadFiles) {
        try (PrintWriter logWriter = new PrintWriter(
                new FileWriter("D:/Workspace/CLOUDSIMSDN/submitWorkloadsSJF_log.txt", true))) {
            logWriter.println("Starting submitWorkloadsSJF with SJF policy.");

            // Vérifier que les maps sont bien remplies
            Map<String, Integer> vmNames = NetworkOperatingSystem.getVmNameToIdMap();
            Map<String, Integer> flowNames = NetworkOperatingSystem.getFlowNameToIdMap();
            Map<Integer, Long> flowIdToBandwidthMap = NetworkOperatingSystem.getFlowIdToBandwidthMap(); // Récupérer la
                                                                                                        // bande
                                                                                                        // passante

            if (vmNames == null || vmNames.isEmpty()) {
                logWriter.println(
                        "VM Names map is empty or null. Ensure the virtual topology is loaded before creating WorkloadParser.");
                return;
            }
            if (flowNames == null || flowNames.isEmpty()) {
                logWriter.println(
                        "Flow Names map is empty or null. Ensure the virtual topology is loaded before creating WorkloadParser.");
                return;
            }

            // Vérifier si workloadFiles est défini
            if (workloadFiles != null && workloadFiles.length > 0) {
                for (String workloadFile : workloadFiles) {
                    logWriter.println("Processing workload file: " + workloadFile);

                    // Créer un WorkloadParser pour lire le fichier de charge de travail
                    WorkloadParser workloadParser = new WorkloadParser(
                            workloadFile, // Nom du fichier
                            broker.getId(), // ID de l'utilisateur
                            new UtilizationModelFull(), // Modèle d'utilisation
                            vmNames, // Mappage des noms de VM aux ID
                            flowNames, // Mappage des noms de flux aux ID
                            flowIdToBandwidthMap);

                    // Passer le WorkloadParser au broker pour gérer le parsing et la planification
                    broker.processWorkloadParser(workloadParser);

                    logWriter.println("WorkloadParser processed for file: " + workloadFile);
                }
            } else {
                logWriter.println("Aucun fichier de charge de travail défini (workloadFiles est null ou vide).");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
