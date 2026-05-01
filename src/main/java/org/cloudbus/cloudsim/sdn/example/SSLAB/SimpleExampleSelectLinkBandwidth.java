package org.cloudbus.cloudsim.sdn.example.SSLAB;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.CloudSimTagsSDN;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.HostFactory;
import org.cloudbus.cloudsim.sdn.HostFactorySimple;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.example.LogManager;
import org.cloudbus.cloudsim.sdn.example.LogMonitor;
import org.cloudbus.cloudsim.sdn.example.LogPrinter;
import org.cloudbus.cloudsim.sdn.monitor.power.EnhancedHostEnergyModel;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMonitor;
import org.cloudbus.cloudsim.sdn.workload.Workload;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemSimple;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyDijkstra;
import org.cloudbus.cloudsim.sdn.parsers.WorkloadParser;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyBandwidthAllocation;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyBandwidthAllocationN;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyDynamicLatencyBw;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyFirst;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyRandom;
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
import org.cloudbus.cloudsim.sdn.policies.vmallocation.VmAllocationPolicySpreadN;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.HybridSJFWorkloadScheduler;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.NoOpWorkloadScheduler;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.PriorityWorkloadScheduler;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.RoundRobinWorkloadScheduler;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.SJFWorkloadScheduler;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.PSO.PSOWorkloadScheduler;

/**
 * Classe pour gérer les politiques de planification non-SJF.
 */
public class SimpleExampleSelectLinkBandwidth extends SimpleExampleBase {

    /**
     * Enumération des politiques d'allocation de VM prises en charge (sauf SJF).
     */
    enum VmAllocationPolicyEnum {
        CombLFF, CombMFF, MipLFF, FCFS, MipMFF, OverLFF, OverMFF,
        LFF, MFF, Overbooking, Spread, Binpack, LWFF, LWFFVD, RR
    }

    /**
     * power
     * Définit les fichiers de configuration par défaut.
     */
    protected static String physicalTopologyFile = "dataset-energy/1energy-physicalHTest-.json";
    protected static String deploymentFile = "dataset-energy/1energy-virtualVTest-.json";
    protected static String[] workload_files = {
            "dataset-energy/2energy-workload120-DynLatTst-.csv"
    };

    /**
     * Affiche la syntaxe d'utilisation de l'application.
     */
    private static void printUsage() {
        String runCmd = "java org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth";
        System.out.format("Usage: %s <vmAllocPolicy> [linkPolicy] [wfPolicy] [datasetDir]%n", runCmd);
        System.out.println("  vmAllocPolicy : LFF|MFF|CombLFF|FCFS|RR|Spread|Binpack|LWFF|LWFFVD|MipLFF|MipMFF");
        System.out.println("  linkPolicy    : First|BwAllocN|DynLatBw|Random  (default: DynLatBw)");
        System.out.println("  wfPolicy      : Priority|SJF|FCFS|RoundRobin|NoOp|HybridSJF|PSO  (default: Priority)");
        System.out.println("  datasetDir    : small|medium|large  (default: dataset-energy)");
        System.out.println();
        System.out.println("Output: results/<date>/<dataset>/experiment_<vmAlloc>_<link>_<wf>/");
        System.out.println("Example: ... Spread BwAllocN Priority medium");
    }

    /**
     * Point d'entrée principal pour les politiques de planification non-SJF.
     *
     * @param args Les arguments de la ligne de commande.
     */
    public static void main(String[] args) {
        // IT24: Reset all static states for benchmark scenarios
        org.cloudbus.cloudsim.sdn.SDNBroker.reset();
        org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter.reset();
        org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm.reset();
        org.cloudbus.cloudsim.sdn.qos.QoSMonitor.reset();
        org.cloudbus.cloudsim.sdn.parsers.VirtualTopologyParser.reset();

        workloads = new ArrayList<String>();

        // ── Parsing des arguments ─────────────────────────────────────────────
        // args[0] = vmAllocPolicy (LFF | MFF | Spread | Binpack | ...)
        // args[1] = linkPolicy (First | BwAllocN | DynLatBw) [défaut: DynLatBw]
        // args[2] = wfPolicy (Priority | SJF | FCFS | ...) [défaut: Priority]
        // args[3] = datasetDir (small | medium | large) [défaut: dataset-energy]
        // Le nom d'expérience est TOUJOURS auto-généré :
        // experiment_<vmAlloc>_<linkPolicy>_<wfPolicy>
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

        String linkPolicyArg = (args.length > 1) ? args[1] : "DynLatBw";
        String workloadPolicyArg = (args.length > 2) ? args[2] : "Priority";
        String datasetDir = (args.length > 3) ? args[3] : null;

        // Traiter "null" et "" comme de vrais null
        if (datasetDir != null && (datasetDir.equalsIgnoreCase("null") || datasetDir.trim().isEmpty()))
            datasetDir = null;

        // ── Dataset directory override ──────────────────────────────────────────
        String datasetName = "dataset-energy"; // default
        if (datasetDir != null) {
            // Raccourcis : small → dataset-small, medium → dataset-medium, large →
            // dataset-large
            if (datasetDir.equalsIgnoreCase("small"))
                datasetDir = "dataset-small";
            if (datasetDir.equalsIgnoreCase("medium"))
                datasetDir = "dataset-medium";
            if (datasetDir.equalsIgnoreCase("large"))
                datasetDir = "dataset-large";
            if (datasetDir.equalsIgnoreCase("mini"))
                datasetDir = "dataset-mini";
                // ── Résolution intelligente du chemin du dataset ──
            java.io.File dsDir = new java.io.File(datasetDir);
            if (!dsDir.exists()) {
                // Tentative 1 : avec préfixe "dataset-"
                String try1 = "dataset-" + datasetDir;
                dsDir = new java.io.File(try1);
                if (!dsDir.exists()) {
                    // Tentative 2 : dans le dossier "datasets/"
                    String try2 = "datasets/" + datasetDir;
                    dsDir = new java.io.File(try2);
                    if (!dsDir.exists()) {
                        // Tentative 3 : "datasets/dataset-"
                        String try3 = "datasets/dataset-" + datasetDir;
                        dsDir = new java.io.File(try3);
                        if (!dsDir.exists()) {
                            System.err.println("❌ Dataset directory not found: " + datasetDir);
                            System.err.println("   Checked: " + datasetDir + ", " + try1 + ", " + try2 + ", " + try3);
                            System.exit(1);
                        }
                    }
                }
            }
            datasetDir = dsDir.getPath();
            // Assurer le slash final
            if (!datasetDir.endsWith("/") && !datasetDir.endsWith("\\"))
                datasetDir = datasetDir + "/";

            physicalTopologyFile = datasetDir + "physical.json";
            deploymentFile = datasetDir + "virtual.json";
            workload_files = new String[] { datasetDir + "workload.csv" };
            // Extraire le nom du dataset (ex: "dataset-small" de "dataset-small/")
            datasetName = datasetDir.replace("/", "").replace("\\", "");
            System.out.println("📂 Dataset directory: " + datasetDir);
        }

        // ── Dossier de sortie organisé par date, scénario et politique de VM ──────────────────
        // Structure: results/YYYY-MM-DD/<datasetName>/<vmAlloc>/experiment_<vmAlloc>_<link>_<wf>/
        // Si ce dossier existe déjà (run précédent du même jour), on ajoute _HH-mm-ss.
        String vmPolicyName = args[0];
        String expName = "experiment_" + vmPolicyName + "_" + linkPolicyArg + "_" + workloadPolicyArg;
        // Utiliser le nom du dossier uniquement pour le répertoire de sortie (ex: dataset-small)
        datasetName = new java.io.File(datasetDir).getName();
        String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        String outputDir = "results/" + dateStr + "/" + datasetName + "/raw/" + vmPolicyName + "/" + expName + "/";
        if (new java.io.File(outputDir).exists()) {
            String timeStr = new java.text.SimpleDateFormat("HH-mm-ss").format(new java.util.Date());
            expName = expName + "_" + timeStr;
            outputDir = "results/" + dateStr + "/" + datasetName + "/" + vmPolicyName + "/" + expName + "/";
        }
        Configuration.experimentName = outputDir;

        // Créer les dossiers de sortie
        new java.io.File(outputDir).mkdirs();
        System.out.println("📁 Output: " + outputDir);

        workloads = Arrays.asList(workload_files);

        // MAJ Nadia : résolution des chemins relatifs selon le répertoire de travail
        // détecté
        // ⚠ WorkloadParser.openFile() préfixe déjà workingDirectory sur les workloads →
        // on NE doit PAS prépendre wd aux workloads ici (sinon double préfixage).
        // En revanche, PhysicalTopologyParser et VirtualTopologyParser ne préfixent
        // PAS,
        // donc on les corrige manuellement.
        String wd = Configuration.workingDirectory;
        if (!wd.equals("./")) {
            if (!new java.io.File(physicalTopologyFile).isAbsolute())
                physicalTopologyFile = wd + physicalTopologyFile;
            if (!new java.io.File(deploymentFile).isAbsolute())
                deploymentFile = wd + deploymentFile;
            // ⚠ NE PAS toucher aux workloads : WorkloadParser.openFile() y ajoute déjà wd
            System.out.println("✅ Paths ajustés (physique + déploiement) : " + wd);
        }

        printArguments(physicalTopologyFile, deploymentFile, workloads);
        Log.printLine("Starting CloudSim SDN...");

        try {
            // Initialisation de CloudSim
            int num_user = 1; // nombre d'utilisateurs cloud
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // flag de traçage des événements
            CloudSim.init(num_user, calendar, trace_flag);

            VmAllocationPolicyFactory vmAllocationFac = null;
            NetworkOperatingSystem nos = new NetworkOperatingSystemSimple();

            // ➊ Instancie ton modèle (utilisé par les hôtes)
            EnhancedHostEnergyModel hostModel = new EnhancedHostEnergyModel(
                    25, // idleWatt
                    1.2, // wattPerCpuUtil
                    0.8, // wattPerRamUtil
                    0.5, // wattPerBwUtil
                    30 // powerOffDuration
            );
            // nos.setEnergyMonitor(energyMonitor); // Supprimé: redondant avec SDNHost

            // 4. Création + injection LogMonitor
            LogMonitor logMonitor = new LogMonitor("LogMonitor", nos);

            CloudSim.addEntity(logMonitor);
            LogMonitor.setNOS(nos);
            int monitorId = logMonitor.getId();

            HostFactory hsFac = new HostFactorySimple();
            LinkSelectionPolicyBandwidthAllocation ls = null;
            LinkSelectionPolicyBandwidthAllocationN lsN = null;
            LinkSelectionPolicyDynamicLatencyBw ns = null;
            LinkSelectionPolicyFirst lf = null;

            // ── VM Allocation Policy ──────────────────────────────────────────────
            // (découplé de la link policy — celle-ci est choisie séparément)
            switch (vmAllocPolicy) {
                case CombMFF:
                case MFF:
                    vmAllocationFac = hostList -> new VmAllocationPolicyCombinedMostFullFirst(hostList);
                    break;
                case LFF:
                    vmAllocationFac = hostList -> new VmAllocationPolicyCombinedLeastFullFirst(hostList);
                    break;
                case CombLFF:
                    vmAllocationFac = hostList -> new VmAllocationPolicyCombinedLeastFullFirstV2(hostList);
                    break;
                case MipMFF:
                    vmAllocationFac = hostList -> new VmAllocationPolicyMipsMostFullFirst(hostList);
                    break;
                case MipLFF:
                    vmAllocationFac = hostList -> new VmAllocationPolicyMipsLeastFullFirst(hostList);
                    break;
                case FCFS:
                    vmAllocationFac = hostList -> new VmAllocationPolicyFCFS(hostList);
                    break;
                case RR:
                    vmAllocationFac = hostList -> new VmAllocationPolicyRR(hostList);
                    break;
                case Spread:
                    vmAllocationFac = hostList -> new VmAllocationPolicySpreadN(hostList);
                    break;
                case Binpack:
                    vmAllocationFac = hostList -> new VmAllocationPolicyBinPack(hostList);
                    break;
                case LWFF:
                    vmAllocationFac = hostList -> new VmAllocationPolicyLWFFF(hostList);
                    break;
                case LWFFVD:
                    vmAllocationFac = hostList -> new VmAllocationPolicyLWFFVD(hostList);
                    break;
                default:
                    System.err.println("Choose proper VM placement policy!");
                    printUsage();
                    System.exit(1);
            }
            loadPhysicalTopology(physicalTopologyFile, nos, hsFac);

            // ── Link Selection Policy (paramétrable via args[1]) ─────────────────
            switch (linkPolicyArg) {
                case "First":
                    lf = new LinkSelectionPolicyFirst();
                    lf.setNetworkOperatingSystem(nos);
                    nos.setLinkSelectionPolicy(lf);
                    System.out.println("Link selection policy set to: First");
                    break;
                case "BwAlloc":
                    ls = new LinkSelectionPolicyBandwidthAllocation();
                    ls.setNetworkOperatingSystem(nos);
                    nos.setLinkSelectionPolicy(ls);
                    System.out.println("Link selection policy set to: BandwidthAllocation");
                    break;
                case "BwAllocN":
                    lsN = new LinkSelectionPolicyBandwidthAllocationN();
                    lsN.setNetworkOperatingSystem(nos);
                    nos.setLinkSelectionPolicy(lsN);
                    System.out.println("Link selection policy set to: BandwidthAllocationN");
                    break;
                case "Random":
                    LinkSelectionPolicyRandom lr = new LinkSelectionPolicyRandom();
                    nos.setLinkSelectionPolicy(lr);
                    System.out.println("Link selection policy set to: Random");
                    break;
                case "Dijkstra":
                    LinkSelectionPolicyDijkstra lDijkstra = new LinkSelectionPolicyDijkstra(nos.getNetworkTopology());
                    lDijkstra.setNetworkOperatingSystem(nos);
                    nos.setLinkSelectionPolicy(lDijkstra);
                    System.out.println("Link selection policy set to: Dijkstra");
                    break;
                case "BLA":
                case "DynLatBw":
                default:
                    ns = new LinkSelectionPolicyDynamicLatencyBw(nos.getNetworkTopology());
                    ns.setNetworkOperatingSystem(nos);
                    nos.setLinkSelectionPolicy(ns);
                    System.out.println("Link selection policy set to: DynamicLatencyBw");
                    break;
            }

            Configuration.allocationPolicyName = vmAllocPolicy.name();
            System.out.println(
                    "🔧 Config: vmAlloc=" + args[0] + " | link=" + linkPolicyArg + " | wf=" + workloadPolicyArg);
            System.out.println("📁 Output dir: " + Configuration.experimentName);

            // Appelé depuis main
            logMonitor.setNOS(nos); // à ajouter après création du LogMonitor
            // Créer le Datacenter
            // SDNDatacenter datacenter = createSDNDatacenter("Datacenter_0",
            // physicalTopologyFile, nos, vmAllocationFac);
            // parserId et workloadParsers déjà définis avant
            SDNDatacenter datacenter = createSDNDatacenter(
                    "Datacenter_0",
                    physicalTopologyFile,
                    nos,
                    vmAllocationFac);

            LogMonitor.initEnergyMonitors(datacenter, hostModel);
            LogMonitor.setDatacenter(datacenter);
            LogMonitor.setVmList(datacenter.getVmList());

            // Créer le Broker
            SDNBroker broker = createBroker();
            if (broker == null) {
                System.err.println("Failed to create broker.");
                System.exit(1);
            }

            // broker.setSchedulingPolicy(new NoOpWorkloadScheduler());
            // broker.setSchedulingPolicy(new SJFWorkloadScheduler()); // Active la
            // politique SJF
            // broker.setSchedulingPolicy(new HybridSJFWorkloadScheduler());
            // ── Workload Scheduling Policy (paramétrable via args[2]) ────────────
            switch (workloadPolicyArg) {
                case "RoundRobin":
                    broker.setSchedulingPolicy(new RoundRobinWorkloadScheduler());
                    break;
                case "SJF":
                    broker.setSchedulingPolicy(new SJFWorkloadScheduler());
                    break;
                case "HybridSJF":
                    broker.setSchedulingPolicy(new HybridSJFWorkloadScheduler());
                    break;
                case "NoOp":
                    broker.setSchedulingPolicy(new NoOpWorkloadScheduler());
                    break;
                case "PSO":
                    broker.setSchedulingPolicy(new PSOWorkloadScheduler(20, 100, 0.5, 1.5, 1.5));
                    break;
                case "Priority":
                default:
                    broker.setSchedulingPolicy(new PriorityWorkloadScheduler());
                    break;
            }
            System.out.println("🔧 Politique de scheduling définie: " + workloadPolicyArg);

            // System.out.println("🔧 SchedulingPolicy en place = " +
            // broker.getSchedulingPolicy().getName());

            broker.datacenter = datacenter;
            broker.workloadFileNames.addAll(workloads);
            broker.setLogMonitorId(monitorId);

// CloudSim.addEntity(broker); // Redundant: Broker constructor already adds itself
            /* MAJ Nadia */
            nos.setDatacenter(datacenter);

            /*
            System.out.println("==== Vérification de l'état des hosts avant déploiement ====");
            for (Host host : datacenter.getHostList()) {
                System.out.println("Host " + host.getId() + " : " +
                        "Total RAM=" + host.getRamProvisioner().getRam() +
                        " | Free RAM=" + host.getRamProvisioner().getAvailableRam() +
                        " | Total PEs=" + host.getNumberOfPes() +
                        " | Free PEs=" + host.getVmScheduler().getPeCapacity() +
                        " | Total MIPS=" + host.getTotalMips());
            }
            System.out.println("===========================================================");
            */

            int brokerId = broker.getId();

            // Soumettre la topologie virtuelle
            System.out.println("$$$$$$$$$$$$ dc ID main : " + datacenter.getId());
            broker.submitDeployApplicationn(datacenter, deploymentFile);

            // while (!broker.isApplicationDeployed()) {
            // while (!broker.isApplicationDeployed()) {
            // System.out.println("⏳ Waiting for application deployment to complete...");
            // Thread.sleep(100); // Attendre 100 ms avant de vérifier à nouveau
            // }

            List<WorkloadParser> workloadParsers = new ArrayList<>();

            // for (String workloadFile : workloads) {
            // System.out.println("📦 Loading workload: " + workloadFile);
            // WorkloadParser wp = new WorkloadParser(
            // workloadFile,
            // broker.getId(),
            // new UtilizationModelFull(),
            // broker.getVmNameIdMap(),
            // broker.getFlowNameIdMap(),
            // broker.getFlowIdToBandwidthMap()
            // );
            // workloadParsers.add(wp);
            // broker.submitRequests(wp, parserId);
            // parserId++;
            // }

            // Convertir la List en Map pour appeler la méthode correcte
            Map<Integer, WorkloadParser> workloadParserMap = new HashMap<>();
            int index = 0;
            for (WorkloadParser wp : workloadParsers) {
                workloadParserMap.put(index++, wp);

            }

            datacenter.setWorkloadParsers(workloadParserMap);
            System.out.println("Datacenter created with VM allocation policy: " + vmAllocPolicy);

            // Soumettre les workloads
            // submitWorkloads(broker);

            // Démarrer la simulation
            if (!SimpleExampleSelectLinkBandwidth.logEnabled)
                Log.enable();

            double finishTime = CloudSim.startSimulation();
            CloudSim.stopSimulation();
            // Finalisation
            // generateAnalysisReport();

            Log.enable();

            // Définir experimentFinishTime pour tous les WorkloadParsers
            SDNBroker.experimentFinishTime = finishTime;
            // broker.printResult();
            // Récupérer la liste des hosts du datacenter
            List<Host> hostList = datacenter.getHostList();

            List<SDNHost> sdnHostList = new ArrayList<>();
            // Récupérer seulement les SDNHost de la liste
            for (Host host : datacenter.getHostList()) {
                if (host instanceof SDNHost) {
                    sdnHostList.add((SDNHost) host);
                }
            }

            // Appeler la méthode d'analyse avec les résultats du broker et la liste des
            // hosts
            System.out.println("Métriques collectées : " + broker.getRequestMetrics().size());
            broker.printEnhancedNetworkResults(broker.getRequestMetrics(), sdnHostList);

            // broker.exportToCsv("simulation_results.csv");

            Log.printLine(finishTime + ": ========== EXPERIMENT FINISHED ===========");

            // Afficher les résultats après la simulation
            List<Workload> wls = broker.getWorkloads();
            if (wls != null)
                // System.out.println("Null");
                LogPrinter.printWorkloadList(wls);

            // Afficher l'utilisation totale des hôtes et des switches
            List<Switch> switchList = nos.getSwitchList();
            LogPrinter.printEnergyConsumption(hostList, switchList, finishTime);

            Log.printLine("Simultaneously used hosts:" + maxHostHandler.getMaxNumHostsUsed());
            Log.printLine("CloudSim SDN finished!");

            // IT25 FIX: Ensure final energy report is synchronized with finishTime
            LogMonitor.printFinalEnergyReport(finishTime);

            // IT25 FIX: Flush logs ONLY at the very end to ensure all final metrics (energy, etc.)
            // computed after stopSimulation() are included in the CSV files.
            LogManager.flushAll();

        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }

    /**
     * Soumet les workloads en fonction de la politique de tri spécifiée.
     *
     * @param broker           Instance de SDNBroker
     * @param sortingAlgorithm Nom de la politique de tri (ex. "SJF")
     * @param workloadFiles    Tableau des fichiers de workloads
     */

    public static void submitWorkloads(SDNBroker broker) {
        System.out.println("submitWorkloads method ");
        if (workloads != null) {
            for (String workload : workloads) {
                System.out.println("Submitting workload: " + workload);
                broker.submitRequests(workload);
            }
        } else {
            System.err.println("No workloads found to submit!");
        }
    }

    public static void printArguments(String physical, String virtual, List<String> workloads) {
        System.out.println("Data center infrastructure (Physical Topology) : " + physical);
        System.out.println("Virtual Machine and Network requests (Virtual Topology) : " + virtual);
        System.out.println("Workloads: ");
        for (String work : workloads)
            System.out.println(" printArguments method :  " + work);
    }
}
