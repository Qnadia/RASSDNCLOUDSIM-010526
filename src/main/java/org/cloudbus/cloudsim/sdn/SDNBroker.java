package org.cloudbus.cloudsim.sdn;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.sdn.CloudSimTagsSDN;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.HostFactory;
import org.cloudbus.cloudsim.sdn.HostFactorySimple;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.example.LogManager;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.parsers.VirtualTopologyParser;
import org.cloudbus.cloudsim.sdn.parsers.WorkloadParser;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.NoOpWorkloadScheduler;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.SJFWorkloadScheduler;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.WorkloadSchedulerPolicy;
import org.cloudbus.cloudsim.sdn.qos.QoSMonitor;
// import org.cloudbus.cloudsim.sdn.sfc.ServiceFunction; // removed: sfc package deleted
// import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionChainPolicy; // removed: sfc package deleted
import org.cloudbus.cloudsim.sdn.virtualcomponents.FlowConfig;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;
import org.cloudbus.cloudsim.sdn.workload.Activity;
import org.cloudbus.cloudsim.sdn.workload.Request;
import org.cloudbus.cloudsim.sdn.workload.Transmission;
import org.cloudbus.cloudsim.sdn.workload.Workload;
import org.cloudbus.cloudsim.sdn.workload.WorkloadResultWriter;

/**
 * Broker class for CloudSimSDN example. This class represents a broker (Service
 * Provider)
 * who uses the Cloud data center.
 * 
 * @author ...
 * @since CloudSimSDN 1.0
 */
public class SDNBroker extends SimEntity {

    public static double experimentStartTime = -1;
    public static double experimentFinishTime = Double.POSITIVE_INFINITY;

    public static int lastAppId = 0;

    public static Map<String, SDNDatacenter> datacenters = new HashMap<String, SDNDatacenter>();
    public static Map<Integer, SDNDatacenter> vmIdToDc = new HashMap<Integer, SDNDatacenter>();

    private String applicationFileName = null;
    // private HashMap<WorkloadParser, Integer> workloadId = null;

    private HashMap<Long, Workload> requestMap = null;
    public List<String> workloadFileNames = null;

    /* Nadia */

    // WFscheduling
    private WorkloadSchedulerPolicy workloadSchedulerPolicy = new NoOpWorkloadScheduler(); // FCFS natif

    public void setSchedulingPolicy(WorkloadSchedulerPolicy policy) {
        this.workloadSchedulerPolicy = policy;
        System.out.println("🧠 Politique de scheduling définie: " + policy.getName());
    }

    // Ajout des mappings
    // Déclarer globalVmDatacenterMap comme une Map statique
    public static Map<Integer, SDNDatacenter> globalVmDatacenterMap = new HashMap<>();

    private boolean applicationDeployed = false;

    private List<WorkloadParser> workloadParsers = new ArrayList<>();

    private Map<String, Integer> vmNameIdMap;
    private Map<String, Integer> flowNameIdMap;
    private Map<Integer, Long> flowIdToBandwidthMap;
    private Map<Integer, Long> vmIdToMipsMap;

    public static void reset() {
        datacenters.clear();
        vmIdToDc.clear();
        globalVmDatacenterMap.clear();
        lastAppId = 0;
        experimentStartTime = -1;
        experimentFinishTime = Double.POSITIVE_INFINITY;
        System.out.println("🔄 [SDNBroker] Global state reset completed.");
    }

    public int totalWorkloadCount = 0;
    public int completedWorkloadCount = 0;
    private Set<Long> completedRequestIds = new HashSet<>();

    private int logMonitorId = -1;

    public void setLogMonitorId(int id) {
        this.logMonitorId = id;
    }

    public class RequestMetrics {
        int requestId;
        String workloadType = ""; // Initialize with empty string
        String srcHostName = "";
        String dstHostName = "";
        String srcVmName = "";
        String dstVmName = "";
        int srcVmId = -1;
        int dstVmId = -1;
        long cloudletLength = 0;
        long packetSizeBytes = 0;
        double processingDelay = 0;
        double propagationDelay = 0;
        double transmissionDelay = 0;
        double switchProcessingDelay = 0; // MAJ Nadia : Dproc_switch = somme des latences switches
        double queueingDelay = 0;
        double totalLatency = 0;
        String path = "";
        boolean isSlaViolated = false;
        int priority = 0; // MAJ Nadia : priorité utilisateur (colonne 10 du CSV)
    }

    public Map<String, Integer> getVmNameIdMap() {
        return this.vmNameIdMap;
    }

    public Map<String, Integer> getFlowNameIdMap() {
        return this.flowNameIdMap;
    }

    public Map<Integer, Long> getFlowIdToBandwidthMap() {
        return this.flowIdToBandwidthMap;
    }

    protected List<Cloudlet> cloudletReceivedList;
    public static Map<String, Vm> vmNameMap = new HashMap<>();
    public SDNDatacenter datacenter; // Ajouter un champ pour stocker le datacenter

    /* MAJ Nadia DS */
    public void printResult() {
        System.out.println("📊 Simulation results are logged in CSV files via LogManager.");
    }

    public void scheduleRequestt(WorkloadParser workloadParser) {
        submitRequests(workloadParser, 0);
    }

    public void submitRequests(WorkloadParser workloadParser) {
        submitRequests(workloadParser, 0);
    }

    public void submitRequests(WorkloadParser workloadParser, int parserId) {
        System.out.println("🚀 Submitting requests from WorkloadParser ID: " + parserId);

        // Récupérer les workloads parsés
        workloadParser.parseNextWorkloads(); // ou parseNextWorkloadsSJF() si vous utilisez SJF
        List<Workload> workloads = workloadParser.getParsedWorkloads();

        // ➕ Injection de stratégie de tri
        if (workloadSchedulerPolicy != null) {
            workloads = workloadSchedulerPolicy.sort(workloads);

            // FIX IT34: Add micro-offsets to maintain sorted order in CloudSim's event queue
            // for workloads having the same original timestamp.
            for (int i = 0; i < workloads.size(); i++) {
                workloads.get(i).time += (i * 1e-9); 
            }

            System.out.println("📋 Tri appliqué via stratégie: " + workloadSchedulerPolicy.getName() + " (with micro-offsets)");
        }

        System.out.println("📦 Batching workloads into time windows for Map-Reduce processing...");
        java.util.Map<Double, java.util.List<Workload>> groupedWorkloads = workloads.stream()
                .collect(java.util.stream.Collectors.groupingBy(w -> w.time));

        for (java.util.Map.Entry<Double, java.util.List<Workload>> entry : groupedWorkloads.entrySet()) {
            double time = entry.getKey();
            java.util.List<Workload> batch = entry.getValue();
            scheduleWorkloadBatch(batch, time, parserId);
        }
    }

    public void scheduleWorkloadBatch(java.util.List<Workload> batch, double time, int parserId) {
        if (batch == null || batch.isEmpty()) return;

        double scheduleTime = time - org.cloudbus.cloudsim.core.CloudSim.clock();
        if (scheduleTime < 0) {
            org.cloudbus.cloudsim.Log.printLine("**" + org.cloudbus.cloudsim.core.CloudSim.clock() + ": SDNBroker: Abnormal start time for Workload Batch at " + time);
            return;
        }

        if (datacenter != null) {
            send(datacenter.getId(), scheduleTime, CloudSimTagsSDN.WORKLOAD_BATCH_SUBMIT, batch);
        } else {
            System.err.println("❌ SDNBroker: Datacenter is null, cannot schedule workload batch!");
            return;
        }

        for (Workload wl : batch) {
            wl.appId = parserId;
            org.cloudbus.cloudsim.sdn.workload.Request terminalRequest = wl.request.getTerminalRequest();
            if (terminalRequest != null) {
                requestMap.put(terminalRequest.getRequestId(), wl);
            }
        }
        org.cloudbus.cloudsim.Log.printLine(org.cloudbus.cloudsim.core.CloudSim.clock() + ": SDNBroker: Scheduled batch of " + batch.size() + " workloads at " + time);
    }
    // public void submitRequests(WorkloadParser wp, int parserId) {
    // System.out.println("📤 Submitting workload file: " + wp.getFile() + " with
    // parserId: " + parserId);

    // // Vérifier que les mappings ne sont pas null
    // if (this.vmNameIdMap == null || this.flowNameIdMap == null ||
    // this.flowIdToBandwidthMap == null) {
    // System.err.println("Erreur : Les mappings ne sont pas initialisés !");
    // return;
    // }
    // // Configurer les temps de début et de fin de l'expérience
    // if (SDNBroker.experimentStartTime < 0) {
    // SDNBroker.experimentStartTime = 0; // Début à t=0
    // }
    // wp.forceStartTime(SDNBroker.experimentStartTime);
    // wp.forceFinishTime(SDNBroker.experimentFinishTime);

    // workloadParsers.add(wp); // ✅ Ajoute ici ton parser à la liste globale !

    // //this.workloadId.put(wp, parserId);
    // this.workloadIdMap.put(wp, parserId);

    // // Planifier le parsing
    // processWorkloadParser(wp);
    // }

    // Map pour stocker les associations WorkloadParser -> workloadId
    private Map<WorkloadParser, Integer> workloadIdMap = new HashMap<>();

    /* Fin */

    // Liste pour stocker tous les Workloads
    private List<Workload> allWorkloads = new ArrayList<>();

    // Nouveau constructeur pour accepter le datacenter
    public SDNBroker(String name, SDNDatacenter datacenter) throws Exception {
        this(name); // Appeler le constructeur existant pour initialiser les champs
        this.datacenter = datacenter; // Initialiser le champ datacenter
    }

    // Getter pour le datacenter
    public SDNDatacenter getDatacenter() {
        return datacenter;
    }

    public SDNBroker(String name) throws Exception {
        super(name);
        this.workloadFileNames = new ArrayList<String>();
        // this.workloadId = new HashMap<WorkloadParser, Integer>(); // Initialisation
        // de workloadId
        this.workloadIdMap = new HashMap<>();

        this.requestMap = new HashMap<Long, Workload>();
        this.allWorkloads = new ArrayList<>(); // Initialisation de allWorkloads:

        // 💡 Initialize your mappings!
        this.vmNameIdMap = new HashMap<>();
        this.flowNameIdMap = new HashMap<>();
        this.flowIdToBandwidthMap = new HashMap<>();
    }

    @Override
    public void startEntity() {
        System.out.println("🚀 Broker : startEntity - ID: " + getId() + " | DC ID: " + datacenter.getId());
        cloudletReceivedList = new ArrayList<>();
        sendNow(datacenter.getId(), CloudSimTagsSDN.APPLICATION_SUBMIT, this.applicationFileName);
    }

    @Override
    public void shutdownEntity() {
        for (SDNDatacenter datacenter : datacenters.values()) {
            List<Vm> vmList = datacenter.getVmList();
            for (Vm vm : vmList) {
                Log.printLine(CloudSim.clock() + ": " + getName() + ": Shuttingdown.. VM:" + vm.getId());
            }
        }
    }

    /* MAJ Nadia *******----- */

    // public void printResult() {
    // try {
    // System.out.println("########## printResult ###############");

    // // 1. Affichage des métriques brutes
    // System.out.println("ReqID,CloudletLength,PacketSize,SrcVmId,DstVmId,ProcDelay,PropDelay,TxDelay,TotalLatency");
    // for(RequestMetrics m : metrics) {
    // System.out.printf("%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f%n",
    // m.requestId, m.cloudletLength, m.packetSizeBytes,
    // m.srcVmId, m.dstVmId,
    // m.processingDelay, m.propagationDelay, m.transmissionDelay, m.totalLatency);
    // }

    // // 2. Initialisation des compteurs
    // int numWorkloads = 0;
    // int numWorkloadsCPU = 0;
    // int numWorkloadsNetwork = 0;
    // int numWorkloadsOver = 0;
    // int numWorkloadsNetworkOver = 0;
    // int numWorkloadsCPUOver = 0;
    // int numTimeout = 0;

    // double totalServeTime = 0;
    // double totalServeTimeCPU = 0;
    // double totalServeTimeNetwork = 0;

    // // 3. Initialisation des groupes
    // int numGroups = Math.max(1, SDNBroker.lastAppId);
    // int[] groupNumWorkloads = new int[numGroups];
    // double[] groupTotalServeTime = new double[numGroups];
    // double[] groupTotalServeTimeCPU = new double[numGroups];
    // double[] groupTotalServeTimeNetwork = new double[numGroups];

    // // 4. Collecte des données depuis les parsers
    // Log.printLine("\n============= [SDNBroker] Aggregating Workload Results
    // =============================");
    // for (WorkloadParser wp : workloadParsers) {
    // WorkloadResultWriter wrw = wp.getResultWriter();
    // if (wrw == null) {
    // System.err.println("No result writer for parser: " + wp);
    // continue;
    // }

    // wrw.printStatistics();
    // wrw.close();

    // // Agrégation des statistiques
    // numWorkloads += wrw.getWorkloadNum();
    // numTimeout += wrw.getTimeoutNum();
    // numWorkloadsOver += wrw.getWorkloadNumOvertime();
    // numWorkloadsCPU += wrw.getWorkloadNumCPU();
    // numWorkloadsCPUOver += wrw.getWorkloadNumCPUOvertime();
    // numWorkloadsNetwork += wrw.getWorkloadNumNetwork();
    // numWorkloadsNetworkOver += wrw.getWorkloadNumNetworkOvertime();

    // totalServeTime += wrw.getServeTime();
    // totalServeTimeCPU += wrw.getServeTimeCPU();
    // totalServeTimeNetwork += wrw.getServeTimeNetwork();

    // // Statistiques par groupe
    // int groupId = wp.getGroupId();
    // if(groupId >= 0 && groupId < numGroups) {
    // groupNumWorkloads[groupId] += wrw.getWorkloadNum();
    // groupTotalServeTime[groupId] += wrw.getServeTime();
    // groupTotalServeTimeCPU[groupId] += wrw.getServeTimeCPU();
    // groupTotalServeTimeNetwork[groupId] += wrw.getServeTimeNetwork();
    // }
    // }

    // // 5. Affichage des statistiques globales
    // Log.printLine("\n============= [SDNBroker] Global Statistics
    // =============================");
    // Log.printLine(String.format("Total Workloads : %,d", numWorkloads));
    // Log.printLine(String.format(" - CPU Workloads : %,d (Overtime: %,d)",
    // numWorkloadsCPU, numWorkloadsCPUOver));
    // Log.printLine(String.format(" - Network Workloads : %,d (Overtime: %,d)",
    // numWorkloadsNetwork, numWorkloadsNetworkOver));
    // Log.printLine(String.format("Timed Out Workloads : %,d", numTimeout));

    // if (numWorkloads > 0) {
    // Log.printLine(String.format("Total Serve Time : %.4f s", totalServeTime));
    // Log.printLine(String.format("Avg Serve Time/Workload : %.4f s",
    // totalServeTime / numWorkloads));
    // Log.printLine(String.format("Overtime Percentage : %.2f%%",
    // 100.0 * numWorkloadsOver / numWorkloads));
    // }

    // // 6. Statistiques détaillées par groupe
    // if (numGroups > 1) {
    // Log.printLine("\n============= [SDNBroker] Per Group Analysis
    // =======================");
    // for (int groupId = 0; groupId < numGroups; groupId++) {
    // if (groupNumWorkloads[groupId] == 0) continue;

    // Log.printLine(String.format("\n--- Group #%d ---", groupId));
    // Log.printLine(String.format("Workloads: %,d", groupNumWorkloads[groupId]));
    // Log.printLine(String.format("Total Serve Time: %.4f s",
    // groupTotalServeTime[groupId]));
    // Log.printLine(String.format("Avg Serve Time : %.4f s",
    // groupTotalServeTime[groupId] / groupNumWorkloads[groupId]));
    // }
    // }

    // } catch (Exception e) {
    // System.err.println("Error in printResult():");
    // e.printStackTrace();

    // // Log des erreurs dans un fichier
    // try (PrintWriter pw = new PrintWriter(new FileWriter("error_log.txt", true)))
    // {
    // pw.println("Error at " + new Date() + ":");
    // e.printStackTrace(pw);
    // } catch (IOException ioe) {
    // ioe.printStackTrace();
    // }
    // }
    // }
    // // public void printResult() {
    // // System.out.println(" ########## printResult ###############");

    // // if (workloadParsers.isEmpty()) {
    // // System.err.println("Aucun WorkloadParser trouvé !");
    // // return;
    // // }

    // // for (WorkloadParser wp : workloadParsers) {
    // // List<Workload> completedWorkloads = wp.getCompletedWorkloads();
    // // if (completedWorkloads != null && !completedWorkloads.isEmpty()) {
    // // System.out.println("✅ Parser ID: " + workloadIdMap.get(wp) + " |
    // Completed: " + completedWorkloads.size());
    // // } else {
    // // System.out.println("⚠️ Aucun workload terminé pour Parser ID: " +
    // workloadIdMap.get(wp));
    // // }
    // // }
    // // }

    // ... autres attributs ...
    private List<RequestMetrics> requestMetrics = new ArrayList<>();

    // Méthode pour récupérer les métriques
    public List<RequestMetrics> getRequestMetrics() {
        return this.requestMetrics;
    }

    private String fillString(char c, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++)
            sb.append(c);
        return sb.toString();
    }

    public void printEnhancedNetworkResults(List<RequestMetrics> metrics, List<SDNHost> hosts) {
        System.out.println("\n" + fillString('═', 120));
        System.out.println("📊 ENHANCED NETWORK PATH ANALYSIS - " + new java.util.Date());
        System.out.println(fillString('═', 120));

        System.out.printf(
                "%-6s | %-8s | %-7s | %-7s | %-6s | %-6s | %-11s | %-9s | %-8s | %-8s | %-9s | %-8s | %-8s | %-5s%n",
                "ReqID", "Type", "HostSrc", "HostDst", "SrcVM", "DstVM", "CloudletLen", "PktSize",
                "Proc(ms)", "Prop(ms)", "Trans(ms)", "Q(ms)", "Total(s)", "SLA");
        System.out.println(fillString('─', 120));

        java.util.Collections.sort(metrics, (m1, m2) -> Long.compare(m1.requestId, m2.requestId));

        int zeroNetworkCount = 0;
        int highQueuingCount = 0;
        int slaCompliantCount = 0;

        for (RequestMetrics m : metrics) {
            double procMs = m.processingDelay * 1000;
            double propMs = m.propagationDelay * 1000;
            double transMs = m.transmissionDelay * 1000;
            double qMs = m.queueingDelay * 1000;

            boolean zeroNetwork = (propMs <= 0.001 && transMs <= 0.001 && !m.workloadType.contains("CPU"));
            boolean highQueuing = (m.queueingDelay > m.totalLatency * 0.5 && m.totalLatency > 0.001);

            if (zeroNetwork)
                zeroNetworkCount++;
            if (highQueuing)
                highQueuingCount++;
            if (!m.isSlaViolated)
                slaCompliantCount++;

            String warning = "";
            if (highQueuing)
                warning += " ⚠️";
            if (zeroNetwork)
                warning += " 🔴";

            String slaIcon = !m.isSlaViolated ? "✅" : "❌";

            System.out.printf(
                    "%-6d | %-8s | %-7s | %-7s | %-6s | %-6s | %-11d | %-9d | %-8.2f | %-8.4f | %-9.4f | %-8.2f | %-8.3f | %-5s %-4s%n",
                    m.requestId, m.workloadType, m.srcHostName, m.dstHostName, m.srcVmName, m.dstVmName,
                    m.cloudletLength, m.packetSizeBytes,
                    procMs, propMs, transMs, qMs, m.totalLatency, slaIcon, warning);

            if (m.path != null && !m.path.isEmpty() && !"DIRECT".equals(m.path)) {
                System.out.println("       ↳ Path: " + m.path);
            }
        }

        System.out.println("\n" + fillString('═', 120));
        System.out.println("📈 STATISTIQUES AVANCÉES");
        System.out.println(fillString('─', 120));

        int total = metrics.size();
        if (total > 0) {
            System.out.printf("✅ SLA Compliance: %d/%d (%.1f%%)%n",
                    slaCompliantCount, total, (slaCompliantCount * 100.0 / total));
            System.out.printf("🔴 Délais réseau nuls: %d requêtes (%.1f%%)%n",
                    zeroNetworkCount, (zeroNetworkCount * 100.0 / total));
            System.out.printf("⚠️ Forte contention (>50%% queue): %d requêtes (%.1f%%)%n",
                    highQueuingCount, (highQueuingCount * 100.0 / total));
        }
        System.out.println(fillString('═', 120));

        printLatencyHotspots(metrics);

        printGlobalStatistics(metrics);
    }

    private void printLatencyHotspots(List<RequestMetrics> metrics) {
        System.out.println("\n🔥 TOP 5 NETWORK LATENCY HOTSPOTS (Highest Queuing Delay)");
        System.out.println(fillString('-', 80));

        metrics.stream()
                .filter(m -> m.queueingDelay > 0)
                .sorted((m1, m2) -> Double.compare(m2.queueingDelay, m1.queueingDelay))
                .limit(5)
                .forEach(m -> System.out.printf("Req %-4d: %s -> %s | QDelay: %.3fs | Path: %s%n",
                        m.requestId, m.srcHostName, m.dstHostName, m.queueingDelay, m.path));

        System.out.println(fillString('-', 80));
    }

    private void printGlobalStatistics(List<RequestMetrics> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            System.out.println("\nNo metrics available for statistics");
            return;
        }

        // Statistiques globales
        double totalRequests = metrics.size();
        DoubleSummaryStatistics latencyStats = metrics.stream()
                .mapToDouble(m -> m.totalLatency)
                .summaryStatistics();

        // Statistiques par type de workload (Filtres plus précis)
        // Hybrid: CPU,NETWORK
        DoubleSummaryStatistics hybridStats = metrics.stream()
                .filter(m -> m.workloadType.contains("CPU") && m.workloadType.contains("NETWORK"))
                .mapToDouble(m -> m.totalLatency)
                .summaryStatistics();

        DoubleSummaryStatistics pureCpuStats = metrics.stream()
                .filter(m -> m.workloadType.equals("CPU"))
                .mapToDouble(m -> m.totalLatency)
                .summaryStatistics();

        DoubleSummaryStatistics pureNetStats = metrics.stream()
                .filter(m -> m.workloadType.equals("NETWORK"))
                .mapToDouble(m -> m.totalLatency)
                .summaryStatistics();

        long slaViolations = metrics.stream()
                .filter(m -> m.isSlaViolated)
                .count();

        System.out.println(
                "\n╔" + fillString('═', 110) + "╗");
        System.out.println(
                "║                                       GLOBAL STATISTICS                                              ║");
        System.out.println(
                "╠" + fillString('═', 110) + "╣");
        System.out.printf("║ %-40s ║ %15s ║ %15s ║ %15s ║ %15s ║%n", "Metric", "All", "Hybrid", "Pure CPU", "Pure Net");
        // Assuming boxChars is defined elsewhere or this line needs to be adjusted.
        // For now, I'll use the original closing line structure.
        char[] boxChars = { '╔', '═', '╗', '╠', '─', '╣', '╚', '╬', '╝' }; // Define boxChars locally for this example
        System.out.println(boxChars[3] + fillString(boxChars[1], 42) + boxChars[7] + fillString(boxChars[1], 17)
                + boxChars[7] + fillString(boxChars[1], 17) + boxChars[7] + fillString(boxChars[1], 17) + boxChars[7]
                + fillString(boxChars[1], 17) + boxChars[6]);
        System.out.printf("║ %-40s ║ %15d ║ %15d ║ %15d ║ %15d ║%n", "Total Requests", (long) totalRequests,
                hybridStats.getCount(), pureCpuStats.getCount(), pureNetStats.getCount());
        System.out.printf("║ %-40s ║ %15.3f ║ %15.3f ║ %15.3f ║ %15.3f ║%n", "Average Latency (sec)",
                latencyStats.getAverage(), hybridStats.getAverage(), pureCpuStats.getAverage(),
                pureNetStats.getAverage());
        System.out.printf("║ %-40s ║ %15.3f ║ %15.3f ║ %15.3f ║ %15.3f ║%n", "Max Latency (sec)", latencyStats.getMax(),
                hybridStats.getMax(), pureCpuStats.getMax(), pureNetStats.getMax());
        System.out.printf("║ %-40s ║ %15d ║ %15s ║ %15s ║ %15s ║%n", "SLA Violations", slaViolations, "N/A", "N/A",
                "N/A");
        System.out.printf("║ %-40s ║ %14.1f%% ║ %15s ║ %15s ║ %15s ║%n", "Compliance Rate",
                (totalRequests - slaViolations) * 100.0 / totalRequests, "N/A", "N/A", "N/A");
        System.out.println("╚" + fillString('═', 42) + "╩" + fillString('═', 17) + "╩" + fillString('═', 17) + "╩"
                + fillString('═', 17) + "╩" + fillString('═', 17) + "╝");
    }

    private void printGlobalStatisticsGen(List<RequestMetrics> metrics) {
        double avgLatency = metrics.stream()
                .mapToDouble(m -> m.totalLatency)
                .average()
                .orElse(0);

        long slaViolations = metrics.stream()
                .filter(m -> m.isSlaViolated)
                .count();

        System.out.println("\nGLOBAL STATISTICS");
        System.out.println("----------------------------------------");
        System.out.printf("Total Requests: %d\n", metrics.size());
        System.out.printf("Average Latency: %.3f sec\n", avgLatency);
        System.out.printf("SLA Violations: %d (%.1f%% compliance)\n",
                slaViolations,
                (1 - (double) slaViolations / metrics.size()) * 100);
        System.out.println("----------------------------------------\n");
    }

    // Méthode utilitaire pour trouver SDNHost par VM ID
    // Returns host name as String
    private String getHostNameForVm(List<SDNHost> hostList, int vmId) {
        for (SDNHost host : hostList) {
            if (host.getVm(vmId) != null) {
                return host.getName();
            }
        }
        return "UNKNOWN";
    }

    // public void printEnhancedResults() {
    // // Méthode helper pour répéter les caractères
    // String separatorLine = repeatChar('=', 80);
    // String headerLine = repeatChar('-', 80);

    // // Configuration du formatage
    // DecimalFormat df = new DecimalFormat("0.000");
    // DecimalFormat sciFormat = new DecimalFormat("0.###E0");
    // sciFormat.setGroupingUsed(false);

    // // En-tête amélioré avec alignement
    // String headerFormat = "%-6s | %-7s | %-8s | %-8s| %-6s | %-6s | %-11s | %-9s
    // | %-9s | %-9s | %-9s | %-9s | %-6s | %-6s%n";
    // String rowFormat = "%-6d | %-7s | %-6d | %-6d | %-11d | %-9s | %-9s | %-9s |
    // %-9s | %-9s | %-6s | %-6s%n";

    // System.out.println("\n" + separatorLine);
    // System.out.println("ENHANCED SIMULATION RESULTS");
    // System.out.println(separatorLine);
    // System.out.printf(headerFormat,
    // "ReqID", "Type" , "HostSrc", "HostDst", "SrcVM", "DstVM", "CloudletLen",
    // "PktSize",
    // "ProcDelay", "PropDelay", "TxDelay", "Total", "QDelay", "SLA");
    // System.out.println(headerLine);

    // // Affichage des données (identique)
    // metrics.forEach(m -> {
    // String pktSize = m.packetSizeBytes > 1_000_000 ?
    // sciFormat.format(m.packetSizeBytes) :
    // String.valueOf(m.packetSizeBytes);
    // int hostSrcId = getHostIdForVm(m.srcVmId);
    // int hostDstId = getHostIdForVm(m.dstVmId);

    // System.out.printf(rowFormat,
    // m.requestId,
    // m.workloadType,
    // m.srcVmId,
    // m.dstVmId,
    // m.cloudletLength,
    // pktSize,
    // df.format(m.processingDelay),
    // df.format(m.propagationDelay),
    // df.format(m.transmissionDelay),
    // df.format(m.totalLatency),
    // df.format(m.queueingDelay),
    // m.isSlaViolated ? "FAIL" : "OK");
    // });

    // System.out.println("====================================================================================================================\n");

    // // Ajouter des statistiques par chemin réseau
    // printPathStatistics(metrics);

    // System.out.println("========================================Advnaced Stat
    // ============================================================================\n");

    // printAdvancedStatistics();
    // //printResourceUtilization();
    // }

    // Méthode pour obtenir le nom du host à partir d'une VM
    // private String getHostNameForVm(int vmId) {
    // for (Host host : hostList) {
    // if (host instanceof SDNHost) {
    // SDNHost sdnHost = (SDNHost) host;
    // return host.getName(); // Retourne "h_0", "h_1", etc.
    // }
    // }

    // return "UNKNOWN";
    // }

    // private void printPathStatistics(List<RequestMetrics> metrics) {
    // Map<String, PathStats> pathStatsMap = new HashMap<>();

    // for (RequestMetrics m : metrics) {
    // String pathKey = getHostIdForVm(m.srcVmId) + "→" + getHostIdForVm(m.dstVmId);

    // pathStatsMap.computeIfAbsent(pathKey, k -> new PathStats())
    // .addRequest(m.totalLatency, m.isSlaViolated);
    // }

    // System.out.println("PATH STATISTICS");
    // System.out.println("----------------------------------------");
    // System.out.printf("%-15s | %-6s | %-9s | %-9s | %-6s%n",
    // "Path", "Count", "Avg Latency", "Max Latency", "SLA %");

    // pathStatsMap.forEach((path, stats) -> {
    // System.out.printf("%-15s | %-6d | %-9.3f | %-9.3f | %-6.1f%%%n",
    // path,
    // stats.count,
    // stats.totalLatency / stats.count,
    // stats.maxLatency,
    // (1 - (double)stats.violations / stats.count) * 100);
    // });
    // System.out.println("----------------------------------------\n");
    // }

    // class PathStats {
    // int count = 0;
    // double totalLatency = 0;
    // double maxLatency = 0;
    // int violations = 0;

    // void addRequest(double latency, boolean isViolated) {
    // count++;
    // totalLatency += latency;
    // maxLatency = Math.max(maxLatency, latency);
    // if (isViolated) violations++;
    // }
    // }

    private String repeatChar(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private void printAdvancedStatistics() {

        String headerLine = repeatChar('=', 80);
        System.out.println("PERFORMANCE STATISTICS");
        System.out.println(String.format("%0" + 80 + "d", 0).replace("0", "="));

        DoubleSummaryStatistics cpuStats = this.requestMetrics.stream()
                .filter(m -> "CPU".equals(m.workloadType))
                .mapToDouble(m -> m.totalLatency)
                .summaryStatistics();

        DoubleSummaryStatistics netStats = this.requestMetrics.stream()
                .filter(m -> "NETWORK".equals(m.workloadType))
                .mapToDouble(m -> m.totalLatency)
                .summaryStatistics();

        // Formatage spécial pour éviter "Infinity"
        String maxCpu = cpuStats.getCount() > 0 ? String.format("%.3f", cpuStats.getMax()) : "N/A";
        String avgCpu = cpuStats.getCount() > 0 ? String.format("%.3f", cpuStats.getAverage()) : "0.000";

        System.out.printf("%-25s: Count=%-3d  Avg=%-8s  Max=%-6s%n",
                "CPU Workloads", cpuStats.getCount(), avgCpu, maxCpu);

        System.out.printf("%-25s: Count=%-3d  Avg=%-8.3f  Max=%-6.3f%n",
                "Network Workloads", netStats.getCount(),
                netStats.getAverage(), netStats.getMax());

        // Détection des goulots d'étranglement
        // detectBottlenecks();
    }

    // private void printResourceUtilization() {
    // System.out.println("\n" + "=".repeat(80));
    // System.out.println("RESOURCE UTILIZATION SUMMARY");
    // System.out.println("=".repeat(80));

    // // Exemple - à adapter avec vos données réelles
    // System.out.printf("%-15s | %-12s | %-12s | %-12s%n",
    // "Host", "CPU Usage", "Memory", "Power");
    // System.out.println("-".repeat(55));

    // for(int i=0; i<4; i++) {
    // System.out.printf("%-15s | %-12.2f | %-12.2f | %-12.2f%n",
    // "Host #"+i,
    // getCpuUsage(i), // À implémenter
    // getMemoryUsage(i), // À implémenter
    // getPowerConsumption(i)); // À implémenter
    // }

    // System.out.println("\nVirtual Machines:");
    // System.out.printf("%-15s | %-12s | %-12s%n", "VM", "Host", "Utilization");
    // System.out.println("-".repeat(40));
    // // Ajouter les données VM ici
    // }

    // private void detectBottlenecks() {
    // System.out.println("\n" + "=".repeat(80));
    // System.out.println("BOTTLENECK ANALYSIS");
    // System.out.println("=".repeat(80));

    // // 1. Détection des liens les plus congestionnés
    // Optional<Link> busiestLink = findBusiestLink();
    // busiestLink.ifPresent(link ->
    // System.out.println("Busiest Link: " + link + " with utilization: "
    // + String.format("%.2f%%", link.getUtilization()*100)));

    // // 2. Détection des hôtes surchargés
    // // ... implémentation similaire
    // }
    // public void printResult() {
    // try{

    // System.out.println(" ########## printResult ###############");
    // System.out.println("ReqID,CloudletLength,PacketSize,SrcVmId,DstVmId,ProcDelay,PropDelay,TxDelay,TotalLatency");
    // for(RequestMetrics m : metrics) {
    // System.out.printf("%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f%n",
    // m.requestId, m.cloudletLength, m.packetSizeBytes,
    // m.srcVmId, m.dstVmId,
    // m.processingDelay, m.propagationDelay, m.transmissionDelay, m.totalLatency);
    // }

    // int numWorkloads = 0;
    // int numWorkloadsCPU = 0;
    // int numWorkloadsNetwork = 0;
    // int numWorkloadsOver = 0;
    // int numWorkloadsNetworkOver = 0;
    // int numWorkloadsCPUOver = 0;
    // int numTimeout = 0;

    // double totalServeTime = 0;
    // double totalServeTimeCPU = 0;
    // double totalServeTimeNetwork = 0;

    // // Initialize group arrays only if there are applications
    // int numGroups = Math.max(1, SDNBroker.lastAppId); // At least 1 group
    // int[] groupNumWorkloads = new int[numGroups];
    // double[] groupTotalServeTime = new double[numGroups];
    // double[] groupTotalServeTimeCPU = new double[numGroups];
    // double[] groupTotalServeTimeNetwork = new double[numGroups];

    // Log.printLine("\n============= [SDNBroker] Aggregating Workload Results
    // =============================");

    // // Collect statistics from each workload parser
    // System.out.println("########## printResult ###############");

    // for (WorkloadParser wp : workloadParsers) {
    // // Get the existing writer that was created in the constructor
    // WorkloadResultWriter wrw = wp.getResultWriter();
    // if (wrw == null) {
    // System.err.println("No result writer for parser: " + wp);
    // continue;
    // }

    // wrw.printStatistics();
    // wrw.close();

    // List<Workload> completed = wp.getCompletedWorkloads();
    // int nbCompleted = completed != null ? completed.size() : 0;
    // System.out.println("Parser ID: " + workloadIdMap.get(wp) + " | Completed: " +
    // nbCompleted);
    // }

    // // ======== Print Global Results ========
    // Log.printLine("\n============= [SDNBroker] Global Statistics
    // =============================");
    // Log.printLine(String.format("Total Workloads : %,d", numWorkloads));
    // Log.printLine(String.format(" - CPU Workloads : %,d (Overtime: %,d)",
    // numWorkloadsCPU, numWorkloadsCPUOver));
    // Log.printLine(String.format(" - Network Workloads : %,d (Overtime: %,d)",
    // numWorkloadsNetwork, numWorkloadsNetworkOver));
    // Log.printLine(String.format("Timed Out Workloads : %,d", numTimeout));

    // if (numWorkloads > 0) {
    // Log.printLine(String.format("Total Serve Time : %.4f s", totalServeTime));
    // Log.printLine(String.format(" - CPU Serve Time : %.4f s",
    // totalServeTimeCPU));
    // Log.printLine(String.format(" - Network Serve Time : %.4f s",
    // totalServeTimeNetwork));
    // Log.printLine(String.format("Avg Serve Time per Workload : %.4f s",
    // totalServeTime / numWorkloads));
    // Log.printLine(String.format("Overall Overtime Percentage : %.2f%%", 100.0 *
    // numWorkloadsOver / numWorkloads));
    // }

    // if (numWorkloadsCPU > 0) {
    // Log.printLine(String.format("Avg CPU Serve Time per Workload : %.4f s",
    // totalServeTimeCPU / numWorkloadsCPU));
    // Log.printLine(String.format("CPU Workloads Overtime Percentage : %.2f%%",
    // 100.0 * numWorkloadsCPUOver / numWorkloadsCPU));
    // }

    // if (numWorkloadsNetwork > 0) {
    // Log.printLine(String.format("Avg Network Serve Time per Workload : %.4f s",
    // totalServeTimeNetwork / numWorkloadsNetwork));
    // Log.printLine(String.format("Network Workloads Overtime Percentage : %.2f%%",
    // 100.0 * numWorkloadsNetworkOver / numWorkloadsNetwork));
    // }

    // // ======== Print Group Analysis ========
    // if (SDNBroker.lastAppId > 0) {
    // Log.printLine("\n============= [SDNBroker] Per Group (Application) Analysis
    // =======================");
    // for (int groupId = 0; groupId < numGroups; groupId++) {
    // int groupWorkloads = groupNumWorkloads[groupId];
    // if (groupWorkloads == 0) continue;

    // Log.printLine(String.format("\n--- Group #%d Statistics ---", groupId));
    // Log.printLine(String.format("Total Workloads : %,d", groupWorkloads));
    // Log.printLine(String.format("Total Serve Time : %.4f s",
    // groupTotalServeTime[groupId]));
    // Log.printLine(String.format(" - CPU Serve Time : %.4f s",
    // groupTotalServeTimeCPU[groupId]));
    // Log.printLine(String.format(" - Network Serve Time : %.4f s",
    // groupTotalServeTimeNetwork[groupId]));

    // Log.printLine(String.format("Avg Serve Time per Workload : %.4f s",
    // groupTotalServeTime[groupId] / groupWorkloads));
    // Log.printLine(String.format("Avg CPU Serve Time per Workload : %.4f s",
    // groupTotalServeTimeCPU[groupId] / groupWorkloads));
    // Log.printLine(String.format("Avg Network Serve Time per Workload : %.4f s",
    // groupTotalServeTimeNetwork[groupId] / groupWorkloads));
    // }
    // }

    // Log.printLine("\n============= [SDNBroker] End of Results
    // =============================\n");
    // }
    // catch (Exception e) {
    // System.err.println(" Error in printResult():");
    // System.err.println(" - lastAppId: " + SDNBroker.lastAppId);
    // System.err.println(" - workloadParsers size: " + workloadParsers.size());
    // System.err.println(" - workloadIdMap contents: " + workloadIdMap);
    // e.printStackTrace();

    // // Write to error log file
    // try (PrintWriter pw = new PrintWriter(new FileWriter("error_log.txt", true)))
    // {
    // // pw.println("Error at " + new Date() + ":");
    // pw.println(" - lastAppId: " + SDNBroker.lastAppId);
    // pw.println(" - workloadParsers: " + workloadParsers);
    // e.printStackTrace(pw);
    // } catch (IOException ioe) {
    // ioe.printStackTrace();
    // }
    // }

    // }

    // public void printResult() {

    // System.out.println(" ########## printResult ###############");

    // System.out.println("ReqID,CloudletLength,PacketSize,SrcVmId,DstVmId,ProcDelay,PropDelay,TxDelay,TotalLatency");
    // for(RequestMetrics m : metrics) {
    // System.out.printf("%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f%n",
    // m.requestId, m.cloudletLength, m.packetSizeBytes,
    // m.srcVmId, m.dstVmId,
    // m.processingDelay, m.propagationDelay, m.transmissionDelay, m.totalLatency);
    // }

    // int numWorkloads = 0;
    // int numWorkloadsCPU = 0;
    // int numWorkloadsNetwork = 0;
    // int numWorkloadsOver = 0;
    // int numWorkloadsNetworkOver = 0;
    // int numWorkloadsCPUOver = 0;
    // int numTimeout = 0;

    // double totalServeTime = 0;
    // double totalServeTimeCPU = 0;
    // double totalServeTimeNetwork = 0;

    // // For group analysis (per application group)
    // int[] groupNumWorkloads = new int[SDNBroker.lastAppId];
    // double[] groupTotalServeTime = new double[SDNBroker.lastAppId];
    // double[] groupTotalServeTimeCPU = new double[SDNBroker.lastAppId];
    // double[] groupTotalServeTimeNetwork = new double[SDNBroker.lastAppId];

    // Log.printLine("\n============= [SDNBroker] Aggregating Workload Results
    // =============================");

    // // Collect statistics from each workload parser

    // for (WorkloadParser wp : workloadParsers) {
    // List<Workload> completed = wp.getCompletedWorkloads();

    // // Stats parser-level
    // System.out.println("✅ Parser ID: " + workloadIdMap.get(wp) + " | Completed: "
    // + wp.getCompletedWorkloads().size());

    // //WorkloadResultWriter wrw = wp.getResultWriter();
    // WorkloadResultWriter wrw = wp.getResultWriter();
    // //wrw.addWorkloadResult(wp);
    // wrw.printStatistics(); // Prints individual workload parser results if needed
    // wrw.close();
    // int nbCompleted = wp.getCompletedWorkloads().size(); // À créer ci-dessous

    // numWorkloads += wrw.getWorklaodNum();
    // numTimeout += wrw.getTimeoutNum();
    // numWorkloadsOver += wrw.getWorklaodNumOvertime();
    // numWorkloadsCPU += wrw.getWorklaodNumCPU();
    // numWorkloadsCPUOver += wrw.getWorklaodNumCPUOvertime();
    // numWorkloadsNetwork += wrw.getWorklaodNumNetwork();
    // numWorkloadsNetworkOver += wrw.getWorklaodNumNetworkOvertime();

    // totalServeTime += wrw.getServeTime();
    // totalServeTimeCPU += wrw.getServeTimeCPU();
    // totalServeTimeNetwork += wrw.getServeTimeNetwork();

    // int groupId = wp.getGroupId();
    // groupNumWorkloads[groupId] += wrw.getWorklaodNum();
    // groupTotalServeTime[groupId] += wrw.getServeTime();
    // groupTotalServeTimeCPU[groupId] += wrw.getServeTimeCPU();
    // groupTotalServeTimeNetwork[groupId] += wrw.getServeTimeNetwork();
    // }

    // // ======== Print Global Results ========
    // Log.printLine("\n============= [SDNBroker] Global Statistics
    // =============================");

    // Log.printLine(String.format("Total Workloads : %,d", numWorkloads));
    // Log.printLine(String.format(" - CPU Workloads : %,d (Overtime: %,d)",
    // numWorkloadsCPU, numWorkloadsCPUOver));
    // Log.printLine(String.format(" - Network Workloads : %,d (Overtime: %,d)",
    // numWorkloadsNetwork, numWorkloadsNetworkOver));
    // Log.printLine(String.format("Timed Out Workloads : %,d", numTimeout));

    // Log.printLine(String.format("Total Serve Time : %.4f s", totalServeTime));
    // Log.printLine(String.format(" - CPU Serve Time : %.4f s",
    // totalServeTimeCPU));
    // Log.printLine(String.format(" - Network Serve Time : %.4f s",
    // totalServeTimeNetwork));

    // if (numWorkloads != 0) {
    // Log.printLine(String.format("Avg Serve Time per Workload : %.4f s",
    // totalServeTime / numWorkloads));
    // Log.printLine(String.format("Overall Overtime Percentage : %.2f%%", 100.0 *
    // numWorkloadsOver / numWorkloads));
    // }

    // if (numWorkloadsCPU != 0) {
    // Log.printLine(String.format("Avg CPU Serve Time per Workload : %.4f s",
    // totalServeTimeCPU / numWorkloadsCPU));
    // Log.printLine(String.format("CPU Workloads Overtime Percentage : %.2f%%",
    // 100.0 * numWorkloadsCPUOver / numWorkloadsCPU));
    // }

    // if (numWorkloadsNetwork != 0) {
    // Log.printLine(String.format("Avg Network Serve Time per Workload : %.4f s",
    // totalServeTimeNetwork / numWorkloadsNetwork));
    // Log.printLine(String.format("Network Workloads Overtime Percentage : %.2f%%",
    // 100.0 * numWorkloadsNetworkOver / numWorkloadsNetwork));
    // }

    // // ======== Print Group Analysis ========
    // Log.printLine("\n============= [SDNBroker] Per Group (Application) Analysis
    // =======================");

    // for (int groupId = 0; groupId < SDNBroker.lastAppId; groupId++) {
    // int groupWorkloads = groupNumWorkloads[groupId];

    // if (groupWorkloads == 0) continue; // Skip groups with no workloads

    // Log.printLine(String.format("\n--- Group #%d Statistics ---", groupId));
    // Log.printLine(String.format("Total Workloads : %,d", groupWorkloads));
    // Log.printLine(String.format("Total Serve Time : %.4f s",
    // groupTotalServeTime[groupId]));
    // Log.printLine(String.format(" - CPU Serve Time : %.4f s",
    // groupTotalServeTimeCPU[groupId]));
    // Log.printLine(String.format(" - Network Serve Time : %.4f s",
    // groupTotalServeTimeNetwork[groupId]));

    // Log.printLine(String.format("Avg Serve Time per Workload : %.4f s",
    // groupTotalServeTime[groupId] / groupWorkloads));
    // Log.printLine(String.format("Avg CPU Serve Time per Workload : %.4f s",
    // groupTotalServeTimeCPU[groupId] / groupWorkloads));
    // Log.printLine(String.format("Avg Network Serve Time per Workload : %.4f s",
    // groupTotalServeTimeNetwork[groupId] / groupWorkloads));
    // }

    // Log.printLine("\n============= [SDNBroker] End of Results
    // =============================\n");
    // }

    // public void printResult() {
    // int numWorkloads = allWorkloads.size(), numWorkloadsCPU = 0,
    // numWorkloadsNetwork = 0,
    // numWorkloadsOver = 0, numWorkloadsNetworkOver = 0, numWorkloadsCPUOver = 0,
    // numTimeout = 0;
    // double totalServetime = 0, totalServetimeCPU = 0, totalServetimeNetwork = 0;

    // // For group analysis
    // int[] groupNumWorkloads = new int[SDNBroker.lastAppId];
    // double[] groupTotalServetime = new double[SDNBroker.lastAppId];
    // double[] groupTotalServetimeCPU = new double[SDNBroker.lastAppId];
    // double[] groupTotalServetimeNetwork = new double[SDNBroker.lastAppId];

    // for(WorkloadParser wp: workloadId.keySet()) {
    // WorkloadResultWriter wrw = wp.getResultWriter();
    // wrw.printStatistics();

    // numWorkloads += wrw.getWorklaodNum();
    // numTimeout += wrw.getTimeoutNum();
    // numWorkloadsOver += wrw.getWorklaodNumOvertime();
    // numWorkloadsCPU += wrw.getWorklaodNumCPU();
    // numWorkloadsCPUOver += wrw.getWorklaodNumCPUOvertime();
    // numWorkloadsNetwork += wrw.getWorklaodNumNetwork();
    // numWorkloadsNetworkOver += wrw.getWorklaodNumNetworkOvertime();

    // totalServetime += wrw.getServeTime();
    // totalServetimeCPU += wrw.getServeTimeCPU();
    // totalServetimeNetwork += wrw.getServeTimeNetwork();

    // // For group analysis
    // groupNumWorkloads[wp.getGroupId()] += wrw.getWorklaodNum();
    // groupTotalServetime[wp.getGroupId()] += wrw.getServeTime();
    // groupTotalServetimeCPU[wp.getGroupId()] += wrw.getServeTimeCPU();
    // groupTotalServetimeNetwork[wp.getGroupId()] += wrw.getServeTimeNetwork();
    // }

    // Log.printLine("============= SDNBroker.printResult()
    // =============================");

    // Log.printLine(String.format("Workloads Total: %d", numWorkloads));
    // Log.printLine(String.format(" - CPU workloads: %d (Overtime: %d)",
    // numWorkloadsCPU, numWorkloadsCPUOver));
    // Log.printLine(String.format(" - Network workloads: %d (Overtime: %d)",
    // numWorkloadsNetwork, numWorkloadsNetworkOver));
    // Log.printLine(String.format("Timed Out workloads: %d", numTimeout));

    // Log.printLine("Total serve time: "+ totalServetime);
    // Log.printLine("Total serve time CPU: "+ totalServetimeCPU);
    // Log.printLine("Total serve time Network: "+ totalServetimeNetwork);
    // if(numWorkloads != 0) {
    // Log.printLine("Avg serve time: "+ totalServetime / numWorkloads);
    // Log.printLine("Overall overtime percentage: "+ ((double)numWorkloadsOver /
    // numWorkloads));
    // }
    // if(numWorkloadsCPU != 0) {
    // Log.printLine("Avg serve time CPU: "+ totalServetimeCPU / numWorkloadsCPU);
    // Log.printLine("CPU overtime percentage: "+ ((double)numWorkloadsCPUOver /
    // numWorkloadsCPU));
    // }
    // if(numWorkloadsNetwork != 0) {
    // Log.printLine("Avg serve time Network: "+ totalServetimeNetwork /
    // numWorkloadsNetwork);
    // Log.printLine("Network overtime percentage: "+
    // ((double)numWorkloadsNetworkOver / numWorkloadsNetwork));
    // }

    // // For group analysis
    // Log.printLine("============= SDNBroker.printResult() Group analysis
    // =======================");
    // for(int i = 0; i < SDNBroker.lastAppId; i++) {
    // if(groupNumWorkloads[i] != 0) {
    // Log.printLine("Group num: "+i+", groupNumWorkloads:"+groupNumWorkloads[i]);
    // Log.printLine("Group num: "+i+",
    // groupTotalServetime:"+groupTotalServetime[i]);
    // Log.printLine("Group num: "+i+",
    // groupTotalServetimeCPU:"+groupTotalServetimeCPU[i]);
    // Log.printLine("Group num: "+i+",
    // groupTotalServetimeNetwork:"+groupTotalServetimeNetwork[i]);
    // Log.printLine("Group num: "+i+", group avg Serve
    // time:"+groupTotalServetime[i]/groupNumWorkloads[i]);
    // Log.printLine("Group num: "+i+", group avg Serve time
    // CPU:"+groupTotalServetimeCPU[i]/groupNumWorkloads[i]);
    // Log.printLine("Group num: "+i+", group avg Serve time
    // Network:"+groupTotalServetimeNetwork[i]/groupNumWorkloads[i]);
    // }
    // }
    // }

    public void submitDeployApplication(SDNDatacenter dc, String filename) {
        System.out.println("submitDeployApplication");
        SDNBroker.datacenters.put(dc.getName(), dc); // Enregistrer le DC

        this.applicationFileName = filename; // Stocker le fichier de déploiement
    }

    /* MAJ Nadia */
    public void submitDeployApplicationn(SDNDatacenter dc, String filename) {

        System.out.println(" ################# submitDeployApplicationn: " + filename);
        System.out.println(" Submitting deployment application: " + filename);
        SDNBroker.datacenters.put(dc.getName(), dc); // Enregistrer le datacenter
        this.applicationFileName = filename; // Stocker le fichier de déploiement

        // Parser la topologie virtuelle
        // VirtualTopologyParser parser = new VirtualTopologyParser(dc.getName(),
        // filename, this.getId());

        // Add VMs to the datacenter and populate vmIdToDc
        // for (SDNVm vm : parser.getVmList(dc.getName())) {
        // dc.getNOS().addVm(vm);
        // SDNBroker.vmIdToDc.put(vm.getId(), dc);
        // System.out.println("Added VM: " + vm.getName() + " (ID: " + vm.getId() + ")
        // to Datacenter: " + dc.getName());
        // }

        // // Récupérer les mappings
        // this.vmNameIdMap = parser.getVmNameIdMap();
        // this.flowNameIdMap = parser.getFlowNameIdMap();
        // this.flowIdToBandwidthMap = parser.getFlowIdToBandwidthMap();

        System.out.println("$$$$$$$$$$$$$ Datacenter : " + dc.getId());
        // System.out.println("Sending APPLICATION_SUBMIT event to datacenter: " +
        // dc.getId());
        // Planifier l'envoi APRÈS que la simulation ait commencé
        // send(dc.getId(), CloudSim.getMinTimeBetweenEvents(),
        // CloudSimTagsSDN.APPLICATION_SUBMIT, filename);

        // sendNow(dc.getId(), CloudSimTagsSDN.APPLICATION_SUBMIT, filename);
        // System.out.println("APPLICATION_SUBMIT event sent");

    }
    // public void submitDeployApplicationn(SDNDatacenter dc, String filename) {
    // System.out.println("submitDeployApplicationn MAJ ");

    // this.applicationFileName = filename;
    // // Ajout du datacenter dans la map globale :
    // SDNBroker.datacenters.put(dc.getName(), dc);

    // // VirtualTopologyParser parser = new VirtualTopologyParser(dc.getName(),
    // filename, this.getId());

    // // // Récupérer les mappages
    // // this.vmNameIdMap = parser.getVmNameIdMap();
    // // this.flowNameIdMap = parser.getFlowNameIdMap();
    // // this.flowIdToBandwidthMap = parser.getFlowIdToBandwidthMap();

    // // // Transmettre les mappages au NOS
    // // NetworkOperatingSystem nos = dc.getNOS();
    // // nos.setVmNameIdMap(vmNameIdMap);
    // // nos.setFlowNameIdMap(flowNameIdMap);
    // // nos.setFlowIdToBandwidthMap(flowIdToBandwidthMap);

    // }

    // public void submitDeployApplicationn(SDNDatacenter dc, String filename) {
    // System.out.println("submitDeployApplication MAJ ");
    // SDNBroker.datacenters.put(dc.getName(), dc); // default DC
    // this.applicationFileName = filename;

    // // Charger la topologie virtuelle
    // VirtualTopologyParser parser = new VirtualTopologyParser(dc.getName(),
    // filename, this.getId());

    // // Ajouter les VMs au NOS et à vmIdToDc
    // NetworkOperatingSystem nos = dc.getNOS();
    // for(SDNVm vm : parser.getVmList(dc.getName())) {
    // nos.addVm(vm); // Ajouter la VM au NOS
    // SDNBroker.vmIdToDc.put(vm.getId(), dc); // Ajouter la VM à la carte globale

    // // Log pour vérifier que chaque VM est ajoutée
    // Log.printLine("Added VM: " + vm.getName() + " (ID: " + vm.getId() + ") to NOS
    // and vmIdToDc");
    // }

    // // Ajouter les flux et les politiques SFC
    // for(FlowConfig flow : parser.getArcList()) {
    // nos.addFlow(flow);
    // }
    // for(ServiceFunctionChainPolicy policy : parser.getSFCPolicyList()) {
    // nos.addSFCPolicy(policy);
    // }

    // // Démarrer le déploiement de l'application
    // nos.startDeployApplicatoin();

    // // Log pour vérifier que la topologie virtuelle est chargée
    // Log.printLine("Virtual topology loaded from file: " + filename);
    // Log.printLine("Number of VMs loaded: " +
    // parser.getVmList(dc.getName()).size());
    // Log.printLine("Number of flows loaded: " + parser.getArcList().size());
    // }

    public void submitDeployApplication(Collection<SDNDatacenter> dcs, String filename) {
        for (SDNDatacenter dc : dcs) {
            if (dc != null)
                SDNBroker.datacenters.put(dc.getName(), dc); // default DC
        }
        this.applicationFileName = filename;
    }

    /**
     * Soumet les workloads directement sans tri.
     *
     * @param workloadFile Le fichier de workload à soumettre.
     */
    public void submitRequests(String workloadFileName) {
        WorkloadParser wp = new WorkloadParser(workloadFileName, getId(), new org.cloudbus.cloudsim.UtilizationModelFull(), 
            getVmNameIdMap(), getFlowNameIdMap(), getFlowIdToBandwidthMap());
        submitRequests(wp, 0);
    }

    public void submitRequest(org.cloudbus.cloudsim.sdn.workload.Request request) {
        if (datacenter != null) {
            sendNow(datacenter.getId(), CloudSimTagsSDN.REQUEST_SUBMIT, request);
        }
    }

    @Override
    public void processEvent(SimEvent ev) {
        int tag = ev.getTag();

        switch (tag) {
            case CloudSimTags.VM_CREATE_ACK:
                processVmCreate(ev);
                break;

            case CloudSimTagsSDN.APPLICATION_SUBMIT_ACK:
                System.out.println("APPLICATION_SUBMIT_ACK event");
                Map<String, Object> mappings = (Map<String, Object>) ev.getData();

                this.vmNameIdMap = (Map<String, Integer>) mappings.get("vmNameIdMap");
                this.flowNameIdMap = (Map<String, Integer>) mappings.get("flowNameIdMap");
                this.flowIdToBandwidthMap = (Map<Integer, Long>) mappings.get("flowIdToBandwidthMap");

                System.out.println(" Received mappings in broker from Datacenter.");
                applicationSubmitCompleted(ev);
                break;
            case CloudSimTagsSDN.REQUEST_COMPLETED:
                System.out.println("REQUEST_COMPLETED");
                requestCompleted(ev);
                break;
            case CloudSimTagsSDN.REQUEST_FAILED:
                System.out.println("REQUEST_FAILED");
                requestFailed(ev);
                break;
            case CloudSimTagsSDN.REQUEST_OFFER_MORE:
                System.out.println("REQUEST_OFFER_MORE");
                requestOfferMode(ev);
                break;
            /* MAJ Nadia */
            case CloudSimTagsSDN.WORKLOAD_SUBMIT_ACK:
                System.out.println("WORKLOAD_SUBMIT_ACK");
                processWorkloadSubmitAck(ev);
                break;

            case CloudSimTagsSDN.WORKLOAD_COMPLETED:
                System.out.println("======= WORKLOAD_COMPLETED Event receved ");
                Request req = (Request) ev.getData();
                double finishTime = CloudSim.clock();

                if (completedRequestIds.add(req.getRequestId())) {
                    this.completedWorkloadCount++;
                    System.out.println("✅ Workload terminé: " + completedWorkloadCount + " / " + totalWorkloadCount);
                    checkIfAllWorkloadsCompleted();
                } else {
                    System.out.println("ℹ️ Workload " + req.getRequestId() + " déjà comptabilisé comme terminé.");
                }

                // In Broker when receiving
                System.out.println("DEBUG - Received Request ID " + req.getRequestId() +
                        " with values:");
                System.out.println("- CloudletLen: " + req.getCloudletLength());
                System.out.println("- ProcDelay: " + req.getProcessingDelay());
                System.out.println("- PropDelay: " + req.getPropagationDelay());
                System.out.println("- TxDelay: " + req.getTransmissionDelay());
                System.out.println("- SrcHost: " + req.getSrcHostName());
                System.out.println("- DestHost: " + req.getDstHostName());

                // Safe destination VM resolution
                // int dstVmId = req.getDestinationVmIdFromPacket();
                // System.out.println("[Broker] Using destination VM ID: " + dstVmId);

                // Get actual VM MIPS instead of relying on transferred data
                // 1. Obtenir le datacenter approprié
                // FIX IT23: Fallback — if vmIdToDc doesn't have the VM (e.g. ID shift after
                // double processApplication), search across all registered datacenters.
                SDNDatacenter dc = vmIdToDc.get(req.getLastProcessingVmId());
                if (dc == null) {
                    int searchVmId = req.getLastProcessingVmId();
                    System.err
                            .println("[Broker] ⚠️ vmIdToDc miss for VM ID: " + searchVmId + " — searching all DCs...");
                    for (SDNDatacenter candidateDc : datacenters.values()) {
                        for (Host h : candidateDc.getHostList()) {
                            if (h.getVm(searchVmId, req.getUserId()) != null) {
                                dc = candidateDc;
                                // Repair the map to avoid repeated scans
                                vmIdToDc.put(searchVmId, dc);
                                System.out.println("[Broker] ✅ VM ID " + searchVmId + " found in DC "
                                        + candidateDc.getName() + " — map repaired.");
                                break;
                            }
                        }
                        if (dc != null)
                            break;
                    }
                }
                if (dc == null) {
                    System.err.println("[Broker] ❌ Datacenter not found for VM ID: " + req.getLastProcessingVmId()
                            + " — skipping WORKLOAD_COMPLETED metrics.");
                    return;
                }

                // 2. Obtenir la liste des VMs du datacenter
                List<SDNHost> hostList = dc.getHostList();
                Vm vm = null;

                // 3. Chercher la VM dans tous les hosts
                for (SDNHost host : hostList) {
                    vm = host.getVm(req.getLastProcessingVmId(), req.getUserId());
                    if (vm != null)
                        break;
                }

                if (vm == null) {
                    System.err.println("VM non trouvée ID: " + req.getLastProcessingVmId());
                    return;
                }

                // 4. Utiliser les informations de la VM
                double vmMips = vm.getMips();
                System.out.printf("[Broker] VM trouvée: ID=%d, MIPS=%.2f%n",
                        vm.getId(), vmMips);

                int vmId = req.getLastProcessingVmId();

                System.out.println("[Processing] Execution time calculation:");
                System.out.println("Cloudlet Length: " + req.getCloudletLength());
                System.out.println("VM MIPS: " + vmMips);

                // Use the pre-calculated delay instead of recalculating
                // double processingDelay = req.getProcessingDelay();
                // if (req.getCloudletLength() > 0 && processingDelay <= 0) {
                // // Fallback calculation if delay wasn't preserved
                // processingDelay = req.getCloudletLength() / vmMips;
                // }

                RequestMetrics m = new RequestMetrics();
                m.requestId = (int) req.getRequestId();
                m.srcVmId = req.getLastProcessingVmId();
                m.totalLatency = CloudSim.clock() - req.getSubmitTime();

                // Récupération explicite des délais
                m.processingDelay = req.getProcessingDelay();
                m.propagationDelay = req.getPropagationDelay(); // <-- Utilisez directement la valeur de la requête
                m.transmissionDelay = req.getTransmissionDelay(); // <-- Idem
                m.switchProcessingDelay = req.getSwitchProcessingDelay(); // MAJ Nadia : Dproc_switch
                m.priority = req.getPriority(); // MAJ Nadia : priorité utilisateur
                m.srcHostName = req.getSrcHostName();
                m.dstHostName = req.getDstHostName();
                m.srcVmName = req.getSourceVmName();
                m.dstVmName = req.getDestinationVmName();
                m.cloudletLength = req.getCloudletLength();
                m.packetSizeBytes = req.getPacketSizeBytes();

                if (m.cloudletLength > 0 && m.packetSizeBytes > 0) {
                    m.workloadType = "CPU,NETWORK";
                    m.path = getNetworkPath(req);
                } else if (m.cloudletLength > 0) {
                    m.workloadType = "CPU";
                } else if (m.packetSizeBytes > 0) {
                    m.workloadType = "NETWORK";
                    m.path = getNetworkPath(req);
                } else {
                    m.workloadType = "UNKNOWN";
                }

                // MAJ Nadia : Dqueue = Le2e - Dproc_VM - Dproc_switch - Dprop - Dtrans
                m.queueingDelay = Math.max(0, m.totalLatency - m.processingDelay
                        - m.transmissionDelay - m.propagationDelay - m.switchProcessingDelay);
                // m.isSlaViolated = checkSlaViolation(req, m.totalLatency);

                System.out.println("Extracted delays from Request:");
                System.out.println("- Processing: " + req.getProcessingDelay());
                System.out.println("- Propagation: " + req.getPropagationDelay());
                System.out.println("- Transmission: " + req.getTransmissionDelay());

                // QoS Monitoring Dynamique
                QoSMonitor.recordDelay(m.requestId, m.srcVmName, m.dstVmName, m.packetSizeBytes, m.totalLatency * 1000,
                    m.processingDelay * 1000, m.propagationDelay * 1000, m.transmissionDelay * 1000, m.queueingDelay * 1000);

                if (checkSlaViolation(req, m.totalLatency)) {
                    m.isSlaViolated = true;
                    // On calcule l'expected pour le log QoS
                    double expectedLatency = 0;
                    for (Activity act : req.getActivities()) {
                        expectedLatency += act.getExpectedTime();
                    }
                    QoSMonitor.checkAndLogViolation(m.requestId, m.totalLatency, expectedLatency);
                }

                // metrics.add(m);

                this.requestMetrics.add(m); // Ajoutez à la liste du broker

                System.out.printf(
                        "📊 [KPI] ReqID=%d | Priority=%d | Le2e=%.6fs | Dprop=%.6fs | Dtrans=%.6fs | Dproc_VM=%.6fs | Dproc_sw=%.6fs | Dqueue=%.6fs%n",
                        m.requestId, m.priority, m.totalLatency,
                        m.propagationDelay, m.transmissionDelay,
                        m.processingDelay, m.switchProcessingDelay, m.queueingDelay);

                System.out.println("📦 Workloads complétés : " + completedWorkloadCount + "/" + totalWorkloadCount);

                checkIfAllWorkloadsCompleted();

                break;

            case CloudSimTagsSDN.WORKLOAD_FAILED:
                Workload failedWorkload = (Workload) ev.getData();
                System.err.println("❌ Workload ID " + failedWorkload.workloadId + " a échoué (Tag FAILED).");
                if (completedRequestIds.add((long) failedWorkload.workloadId)) {
                    this.completedWorkloadCount++;
                    System.out.println("📦 [Echec] Progress: " + completedWorkloadCount + "/" + totalWorkloadCount);
                    checkIfAllWorkloadsCompleted();
                }
                break;
            case CloudSimTags.CLOUDLET_RETURN:
                System.out.println("CLOUDLET_RETURN");
                processCloudletReturn(ev);
                break;

            default:
                System.out.println("Unknown event received by " + super.getName() + ". Tag:" + ev.getTag());
                break;
        }
    }

    /* MAJ Nadia */
    private void checkIfAllWorkloadsCompleted() {
        if (completedWorkloadCount == totalWorkloadCount) {
            System.out.println("✅✅✅ Tous les workloads ont été exécutés.");
            sendNow(logMonitorId, CloudSimTagsSDN.STOP_MONITORING);
            // LogManager.flushAll(); <-- Tu peux l’appeler ici si tu veux flush tout
        }
    }

    private String resolveNodeName(int id, NetworkOperatingSystem nos) {
        if (nos == null)
            return String.valueOf(id);
        Node n = nos.getNodeById(id);
        if (n != null)
            return n.getName();
        String vmName = NetworkOperatingSystem.getVmName(id);
        if (vmName != null)
            return vmName;
        return String.valueOf(id);
    }

    private String getNetworkPath(Request req) {
        if (req == null || req.getActivities() == null) {
            return "NO_PATH";
        }

        NetworkOperatingSystem nos = null;
        if (datacenters != null && !datacenters.isEmpty()) {
            nos = datacenters.values().iterator().next().getNOS();
        }

        List<String> nodeNames = new ArrayList<>();
        double totalPathLatMs = 0;

        // On saute la première transmission car c'est la "logique" ajoutée par le
        // Broker
        boolean firstTransmissionSkipped = false;

        for (Activity act : req.getActivities()) {
            if (act instanceof Transmission) {
                Transmission tr = (Transmission) act;
                if (!firstTransmissionSkipped) {
                    firstTransmissionSkipped = true;
                    continue;
                }

                if (tr != null && tr.getPacket() != null) {
                    int origin = tr.getPacket().getOrigin();
                    int dest = tr.getPacket().getDestination();
                    String originName = resolveNodeName(origin, nos);
                    String destName = resolveNodeName(dest, nos);

                    if (nodeNames.isEmpty()) {
                        nodeNames.add(originName);
                    }
                    if (!nodeNames.contains(destName)) {
                        nodeNames.add(destName);
                    }
                    totalPathLatMs += tr.getExpectedTime() * 1000.0;
                }
            }
        }

        if (nodeNames.isEmpty()) {
            // Si c'est intra-host, on n'a que la transmission logique (qu'on a sauté)
            // On essaie de retrouver les VMs de la première activité
            for (Activity act : req.getActivities()) {
                if (act instanceof Transmission) {
                    Transmission tr = (Transmission) act;
                    return resolveNodeName(tr.getPacket().getOrigin(), nos) + " (intra-host)";
                }
            }
            return "localhost";
        }

        return String.join(" -> ", nodeNames) + " (latence: " + String.format(Locale.US, "%.4f", totalPathLatMs)
                + "ms)";
    }

    private boolean checkSlaViolation(Request req, double actualLatency) {
        double expectedLatency = 0;
        for (Activity act : req.getActivities()) {
            expectedLatency += act.getExpectedTime();
        }
        return actualLatency > expectedLatency * Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR;
    }

    public void exportToCsv(String filename) {
        try (PrintWriter pw = new PrintWriter(filename)) {
            // En-tête
            pw.println("RequestID,Type,SourceVM,DestVM,CloudletLength,PacketSize,"
                    + "ProcessingDelay,PropagationDelay,TransmissionDelay,QueueingDelay,"
                    + "TotalLatency,NetworkPath,SLAStatus");

            // Données
            this.requestMetrics.forEach(m -> {
                pw.printf("%d,%s,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,\"%s\",%s%n",
                        m.requestId,
                        m.workloadType,
                        m.srcVmId,
                        m.dstVmId,
                        m.cloudletLength,
                        m.packetSizeBytes,
                        m.processingDelay,
                        m.propagationDelay,
                        m.transmissionDelay,
                        m.queueingDelay,
                        m.totalLatency,
                        m.path != null ? m.path : "",
                        m.isSlaViolated ? "VIOLATED" : "OK");

            });
        } catch (IOException e) {
            System.err.println("Error exporting to CSV: " + e.getMessage());
        }
    }
    /* Fin MAJ */

    private void processVmCreate(SimEvent ev) {
        // Implémentez la logique de traitement des confirmations de création de VM
    }

    private void requestFailed(SimEvent ev) {
        System.out.println("################# requestFailed");
        Request req = (Request) ev.getData();
        Workload wl = requestMap.remove(req.getRequestId());
        if (wl != null) {
            wl.failed = true;
            wl.writeResult();
        }
    }

    /* MAJ Nadia */
    private void processWorkloadSubmitAck(SimEvent ev) {
        System.out.println("################# processWorkloadSubmitAck");
        Workload workload = (Workload) ev.getData();
        System.out.println("✅ WORKLOAD_SUBMIT_ACK reçu pour Workload ID: " + workload.workloadId);

        // Mettre à jour l'état du workload avec une chaîne de caractères
        workload.setStatus("ACKNOWLEDGED");

        // - Soumettre le prochain workload

    }

    private void requestCompleted(SimEvent ev) {
        Request req = (Request) ev.getData();
        Workload wl = requestMap.remove(req.getRequestId());

        // FIX IT24: Count the completion FIRST, before any early return.
        // Previously, when wl == null (LWFF fallback path), the method returned
        // before reaching the counter increment, causing the stall at 853/1000.
        if (completedRequestIds.add(req.getRequestId())) {
            this.completedWorkloadCount++;
            System.out
                    .println("✅ [REQUEST_COMPLETED] Progress: " + completedWorkloadCount + " / " + totalWorkloadCount);
            checkIfAllWorkloadsCompleted();
        }

        if (wl == null) {
            // Counted, but no workload metadata to record — happens on LWFF fallback path
            Log.printLine("⚠️ [REQUEST_COMPLETED] No workload in requestMap for ID: "
                    + req.getRequestId() + " — counted but not recorded.");
            return;
        }

        WorkloadParser parser = wl.getParser();
        if (parser == null) {
            Log.printLine("❌ Parser is NULL for WorkloadID: " + wl.workloadId);
            return;
        }

        parser.addCompletedWorkload(wl);
        Log.printLine("✅ Workload " + wl.workloadId + " ajouté à completedWorkloads.");

        wl.failed = false;
        wl.writeResult();
        Log.printLine("✅ Workload " + wl.workloadId + " terminé et enregistré.");
    }

    // private void requestCompleted(SimEvent ev) {
    // Request req = (Request) ev.getData();
    // Workload wl = requestMap.remove(req.getRequestId());
    // if (wl != null) {
    // wl.failed = false;

    // /* MAJ Nadia */
    // // ✅ Nouveau : ajoute-le dans le WorkloadParser
    // WorkloadParser parser = wl.getParser(); // ou autre moyen d'y accéder

    // if (parser != null) {
    // parser.addCompletedWorkload(wl);
    // System.out.println("✅ [requestCompleted] Workload ajouté à parser.
    // WorkloadID: " + wl.workloadId);
    // } else {
    // System.out.println("❌ [requestCompleted] Parser non trouvé pour WorkloadID: "
    // + wl.workloadId);
    // }
    // //wl.writeResult();

    // }
    // }

    private boolean applicationSubmitted = false;

    private void applicationSubmitCompleted(SimEvent ev) {
        if (applicationSubmitted) {
            System.out.println("########## Application already submitted. Skipping duplicate request parsing.");
            return;
        }
        applicationSubmitted = true;
        System.out.println("########## Application deployment completed.");

        // Créer les parsers et soumettre les workloads
        int parserId = 0;
        for (String workloadFile : workloadFileNames) {
            UtilizationModelStochastic stochasticModel = new UtilizationModelStochastic(0.8);
            WorkloadParser wp = new WorkloadParser(workloadFile, getId(),
                    new UtilizationModelStochastic(1.0), // CPU jusqu’à 100%
                    new UtilizationModelStochastic(0.6), // RAM ~60%
                    new UtilizationModelStochastic(0.3), // BW ~30%,
                    vmNameIdMap, flowNameIdMap, flowIdToBandwidthMap);
            workloadParsers.add(wp);
            submitRequests(wp, parserId++);
        }

        // ✅ Compter le nombre total de workloads après le parsing
        this.totalWorkloadCount = workloadParsers.stream()
                .mapToInt(wp -> wp.getParsedWorkloads().size())
                .sum();

        System.out.println("🔢 Total workloads à exécuter : " + totalWorkloadCount);

    }

    // private boolean workloadAlreadySubmitted = true;

    // private void applicationSubmitCompleted(SimEvent ev) {
    // System.out.print("##################### applicationSubmitCompleted ");
    // for(String filename: this.workloadFileNames) {
    // System.out.println("applicationSubmitCompleted");
    // if(workloadAlreadySubmitted) {
    // System.out.println("Workloads déjà soumis. Skip.");
    // return;
    // }
    // // workloadAlreadySubmitted = true;
    // //workloadId.put(wParser, SDNBroker.lastAppId);

    // // ✅ 1️⃣ Crée le parser APRES le déploiement ➔ les mappings sont prêts
    // // WorkloadParser wParser = startWorkloadParser(filename);

    // // ✅ 2️⃣ Associe ce workloadParser à un AppID (utile si tu analyses par app)
    // //workloadIdMap.put(wParser, SDNBroker.lastAppId);

    // // ✅ 3️⃣ Incrémente l'AppID (logique)
    // //SDNBroker.lastAppId++;

    // // ✅ 4️⃣ Lance le parsing du workload ➔ cloudlets, packets, etc.
    // //processWorkloadParser(wParser); // méthode refactorisée !

    // //workloadIdMap.put(wParser, SDNBroker.lastAppId);

    // }
    // }

    /* MAJ Nadia */

    public void processApplicationSubmitAck(SimEvent ev) {
        System.out.println("✅ Application deployment completed.");
        this.applicationDeployed = true;
    }

    public boolean isApplicationDeployed() {
        return this.applicationDeployed;
    }

    protected void processCloudletReturn(SimEvent ev) {
        System.out.println("################### processCloudletReturn");
        Cloudlet cl = (Cloudlet) ev.getData();

        System.out.println("✅ Cloudlet terminé ! ID : " + cl.getCloudletId() + " | Time : " + CloudSim.clock());
        System.out.println("Cloudlet finished: " + cl.getCloudletId() + ", Status: " + cl.getStatus());

        // Ajoute ici si tu veux accumuler des stats sur le Cloudlet :
        this.cloudletReceivedList.add(cl); // À déclarer si pas déjà fait

        // Affiche aussi la perf si besoin
        System.out.println("✅ Cloudlet reçu, temps d'exécution : " + (cl.getFinishTime() - cl.getExecStartTime()));
    }

    // public void processApplication(int userId, String vmsFileName) {
    // if(applicationDeployed) {
    // System.out.println(" processApplication déjà exécutée. Skip.");
    // // IMPORTANT : Envoi de l'ACK APPLICATION_SUBMIT_ACK au user
    // // Cet événement déclenchera ensuite le parsing et la soumission des
    // workloads.
    // send(userId, 0, CloudSimTagsSDN.APPLICATION_SUBMIT_ACK, vmsFileName);
    // // sendNow(getId(), CloudSimTags.VM_DATACENTER_EVENT);

    // return; // On évite de re-déployer

    // }

    // System.out.println(" processApplication démarre.");
    // applicationDeployed = true;

    // System.out.println("################# processApplication");

    // // Vérifier que des datacenters sont enregistrés
    // if (SDNBroker.datacenters.isEmpty()) {
    // System.err.println("❌ Aucun datacenter enregistré dans SDNBroker.datacenters
    // !");
    // return;
    // }
    // // On récupère le premier DataCenter enregistré
    // SDNDatacenter defaultDC =
    // SDNBroker.datacenters.entrySet().iterator().next().getValue();

    // // Création d'un parser pour la topologie virtuelle
    // VirtualTopologyParser parser = new VirtualTopologyParser(defaultDC.getName(),
    // vmsFileName, userId);

    // System.out.println("📌 Vérification du mappage des VMs...");

    // // Pour chaque datacenter enregistré, on ajoute les VMs définies dans le
    // fichier de topologie virtuelle
    // for (String dcName : SDNBroker.datacenters.keySet()) {
    // SDNDatacenter dc = SDNBroker.datacenters.get(dcName);
    // NetworkOperatingSystem nos = dc.getNOS();

    // // Récupération de la liste des VMs pour le datacenter (selon le dcName)
    // for (SDNVm vm : parser.getVmList(dcName)) {
    // System.out.println("------ Ajout de la VM " + vm.getName() + " (ID: " +
    // vm.getId() + ") au Datacenter " + dcName);
    // nos.addVm(vm);

    // // On enregistre le mapping VM ID -> DataCenter
    // SDNBroker.vmIdToDc.put(vm.getId(), dc);

    // // 🔥 Ajoute cette ligne pour remplir vmNameMap :
    // vmNameMap.put(vm.getName(), vm);
    // vmNameIdMap.put(vm.getName(), vm.getId());
    // }

    // //Ajouter les flows dans le NOS
    // for (FlowConfig flow : parser.getArcList()) {
    // nos.addFlow(flow);
    // }

    // }

    // // Stockage des mappings pour une utilisation ultérieure (dans le parsing des
    // workloads)
    // this.vmNameIdMap = parser.getVmNameIdMap();
    // this.flowNameIdMap = parser.getFlowNameIdMap();
    // this.flowIdToBandwidthMap = parser.getFlowIdToBandwidthMap();

    // // Démarrage du déploiement de l'application dans chaque datacenter
    // for (String dcName : SDNBroker.datacenters.keySet()) {
    // SDNDatacenter dc = SDNBroker.datacenters.get(dcName);
    // NetworkOperatingSystem nos = dc.getNOS();
    // // Injection de la map des BW dans le NOS
    // nos.setFlowIdToBandwidthMap(parser.getFlowIdToBandwidthMap());
    // System.out.println("flowIdToBandwidthMap injecté dans le NOS de " + dcName);
    // nos.startDeployApplicatoin();
    // }

    // // IMPORTANT : Envoi de l'ACK APPLICATION_SUBMIT_ACK au user
    // // Cet événement déclenchera ensuite le parsing et la soumission des
    // workloads.
    // send(userId, 0, CloudSimTagsSDN.APPLICATION_SUBMIT_ACK, vmsFileName);
    // }

    // private void processApplication(int userId, String vmsFileName){
    // SDNDatacenter defaultDC =
    // SDNBroker.datacenters.entrySet().iterator().next().getValue();
    // VirtualTopologyParser parser = new VirtualTopologyParser(defaultDC.getName(),
    // vmsFileName, userId);

    // System.out.println("📌 Vérification du mappage des VMs...");

    // for(String dcName: SDNBroker.datacenters.keySet()) {
    // SDNDatacenter dc = SDNBroker.datacenters.get(dcName);
    // NetworkOperatingSystem nos = dc.getNOS();

    // for(SDNVm vm : parser.getVmList(dcName)) {
    // nos.addVm(vm);
    // if(vm instanceof ServiceFunction) {
    // ServiceFunction sf = (ServiceFunction)vm;
    // sf.setNetworkOperatingSystem(nos);
    // }
    // SDNBroker.vmIdToDc.put(vm.getId(), dc);
    // }
    // }

    // for(FlowConfig arc : parser.getArcList()) {
    // SDNDatacenter srcDc = SDNBroker.vmIdToDc.get(arc.getSrcId());
    // SDNDatacenter dstDc = SDNBroker.vmIdToDc.get(arc.getDstId());

    // if(srcDc.equals(dstDc)) {
    // // Trafic intra-DC : créer un flux virtuel dans le DC
    // srcDc.getNOS().addFlow(arc);
    // }
    // else {
    // // Trafic inter-DC : créer dans les NOS inter-DC
    // srcDc.getNOS().addFlow(arc);
    // dstDc.getNOS().addFlow(arc);
    // }
    // }

    // // Ajouter les politiques Service Function Chain
    // for(ServiceFunctionChainPolicy policy : parser.getSFCPolicyList()) {
    // SDNDatacenter srcDc = SDNBroker.vmIdToDc.get(policy.getSrcId());
    // SDNDatacenter dstDc = SDNBroker.vmIdToDc.get(policy.getDstId());
    // if(srcDc.equals(dstDc)) {
    // // Trafic intra-DC : créer une politique SFC dans le DC
    // srcDc.getNOS().addSFCPolicy(policy);
    // }
    // else {
    // // Trafic inter-DC : créer dans les NOS inter-DC
    // srcDc.getNOS().addSFCPolicy(policy);
    // dstDc.getNOS().addSFCPolicy(policy);
    // }
    // }

    // /* Nadia */
    // // Stocker les mappings
    // this.vmNameIdMap = parser.getVmNameIdMap();
    // this.flowNameIdMap = parser.getFlowNameIdMap();
    // this.flowIdToBandwidthMap = parser.getFlowIdToBandwidthMap();

    // for(String dcName : SDNBroker.datacenters.keySet()) {
    // SDNDatacenter dc = SDNBroker.datacenters.get(dcName);
    // NetworkOperatingSystem nos = dc.getNOS();
    // nos.startDeployApplicatoin();
    // }
    // /* Fin */

    // // Cette boucle est redondante et peut être supprimée car elle est déjà
    // exécutée ci-dessus
    // /*
    // for(String dcName: SDNBroker.datacenters.keySet()) {
    // SDNDatacenter dc = SDNBroker.datacenters.get(dcName);
    // NetworkOperatingSystem nos = dc.getNOS();
    // nos.startDeployApplicatoin();
    // }
    // */

    // send(userId, 0, CloudSimTagsSDN.APPLICATION_SUBMIT_ACK, vmsFileName);
    // }

    public static SDNDatacenter getDataCenterByName(String dcName) {
        return SDNBroker.datacenters.get(dcName);
    }

    public static SDNDatacenter getDataCenterByVmID(int vmId) {
        return SDNBroker.vmIdToDc.get(vmId);
    }

    private void requestOfferMode(SimEvent ev) {
        WorkloadParser wp = (WorkloadParser) ev.getData();
        processWorkloadParser(wp);
    }

    /* Nadia */

    private WorkloadParser startWorkloadParser(String workloadFile) {
        System.out.println("######################### startWorkloadParser ");
        // Vérifier que les maps ne sont pas null
        if (this.vmNameIdMap == null || this.flowNameIdMap == null || this.flowIdToBandwidthMap == null) {
            System.err.println("Erreur : Les mappings ne sont pas initialisés !");
            return null;
        }
        WorkloadParser workParser = new WorkloadParser(
                workloadFile,
                this.getId(),
                new UtilizationModelStochastic(1.0), // CPU jusqu’à 100%
                new UtilizationModelStochastic(0.6), // RAM ~60%
                new UtilizationModelStochastic(0.3), // BW ~30%,
                this.vmNameIdMap, // Utiliser les mappings stockés
                this.flowNameIdMap,
                this.flowIdToBandwidthMap);

        // Définir des valeurs par défaut si non initialisées
        if (SDNBroker.experimentStartTime < 0) {
            SDNBroker.experimentStartTime = 0; // Début à t=0
        }

        workParser.forceFinishTime(SDNBroker.experimentFinishTime);
        workParser.forceStartTime(experimentStartTime);
        // workParser.forceFinishTime(experimentFinishTime);
        // Définir une valeur de secours si nécessaire
        // if (SDNBroker.experimentFinishTime == Double.POSITIVE_INFINITY) {
        // SDNBroker.experimentFinishTime = 1000; // Ex: 1000 unités de temps
        // }

        // workParser.forceFinishTime(SDNBroker.experimentFinishTime);
        return workParser;
    }
    /* Fin */

    /**
     * Nouvelle méthode pour planifier un WorkloadParser.
     * 
     * @param workParser Le WorkloadParser à planifier.
     */
    /* MAJ Nadia DS */

    public void processWorkloadParser(WorkloadParser wp) {
        System.out.println("############### processWorkloadParser ");
        System.out.println("📦 Processing workload parser: " + wp.getFile());

        try {
            // Parser les workloads
            int workloadId = this.workloadIdMap.get(wp);
            wp.parseNextWorkloads();
            List<Workload> parsedWorkloads = wp.getParsedWorkloads();

            if (parsedWorkloads == null || parsedWorkloads.isEmpty()) {
                System.out.println("⚠️ No workloads found in file: " + wp.getFile());
                return;
            }

            // Récupère l'ID du parser
            int parserId = workloadIdMap.get(wp);

            // Appliquer la politique de scheduling si définie
            if (this.workloadSchedulerPolicy != null) {
                parsedWorkloads = workloadSchedulerPolicy.sort(parsedWorkloads);
                System.out.println("📋 Tri des workloads appliqué via : " + workloadSchedulerPolicy.getName());
            }

            // Ajoute chaque workload à la liste globale
            for (Workload wl : parsedWorkloads) {
                wl.setParser(wp);
                allWorkloads.add(wl);
            }

            System.out.println("✅ " + parsedWorkloads.size() + " workloads traités avec la politique de scheduling.");
        } catch (Exception e) {
            System.err.println("❌ Failed to process workload parser: " + wp.getFile());
            e.printStackTrace();
        }
    }

    // V1 sans politique de WF
    // public void processWorkloadParser(WorkloadParser wp) {
    // System.out.println("############### processWorkloadParser ");

    // // ✅ 1. Récupérer l'ID de la source de workloads
    // // int workloadId = this.workloadId.get(workParser);
    // int workloadId = this.workloadIdMap.get(wp);

    // // ✅ 2. Parser les workloads depuis le parser courant
    // wp.parseNextWorkloads();

    // // ✅ 3. Récupérer la liste des workloads nouvellement parsés
    // List<Workload> parsedWorkloads = wp.getParsedWorkloads();

    // if (parsedWorkloads == null || parsedWorkloads.isEmpty()) {
    // System.out.println("⚠️ Aucun workload trouvé dans le fichier.");
    // return;
    // }

    // System.out.println("✅ Workloads trouvés : " + parsedWorkloads.size());

    // // ✅ 4. Ajouter à la liste globale (utile pour suivi/gestion plus tard)
    // for (Workload wl : parsedWorkloads) {
    // wl.setParser(wp);
    // allWorkloads.add(wl); // Stockage global
    // System.out.println("📥 Workload ID : " + wl.workloadId + " soumis.");
    // scheduleWorkload(wl, workloadId);
    // }

    // // // ✅ 5. Planification (après ajout dans la liste globale)
    // // for (Workload wl : parsedWorkloads) {
    // // System.out.println("⏰ Planning Workload ID " + wl.workloadId + " à t=" +
    // wl.time);
    // // scheduleWorkload(wl, workloadId);
    // // }
    // }

    // public void processWorkloadParser(WorkloadParser workParser) {
    // int workloadId = this.workloadId.get(workParser);
    // workParser.parseNextWorkloads();
    // List<Workload> parsedWorkloads = workParser.getParsedWorkloads();

    // System.out.println("Workloads trouvés : " + parsedWorkloads.size());
    // for (Workload wl : parsedWorkloads) {
    // System.out.println(" Workload ID : " + wl.workloadId + " soumis.");
    // allWorkloads.add(wl);
    // }

    // if (parsedWorkloads.isEmpty()) {
    // System.out.println("Aucun workload trouvé dans le fichier.");
    // return;
    // }
    // System.out.println("Parsed workloads: " + parsedWorkloads.size());

    // if (parsedWorkloads.size() > 0) {
    // for (Workload wl : parsedWorkloads) {
    // System.out.println("Workload ID " + wl.workloadId + " submitted at time: " +
    // wl.time);
    // scheduleWorkload(wl, workloadId);
    // /* MAJ Nadia */
    // // Ajouter le workload à la liste globale :
    // allWorkloads.add(wl);
    // }
    // } else {
    // System.out.println("No workloads parsed from file.");
    // }
    // }

    // public void processWorkloadParser(WorkloadParser workParser) {
    // int workloadId = this.workloadId.get(workParser);
    // workParser.parseNextWorkloads();
    // List<Workload> parsedWorkloads = workParser.getParsedWorkloads();

    // if (parsedWorkloads.size() > 0) {
    // // Planifier les workloads extraits
    // for (Workload wl : parsedWorkloads) {
    // scheduleWorkload(wl, workloadId);
    // allWorkloads.add(wl); // Ajouter à la liste globale
    // System.out.println("Workload ajouté : " + wl.workloadId);
    // }

    // // Planifier la soumission des prochains workloads
    // Workload lastWorkload = parsedWorkloads.get(parsedWorkloads.size() - 1);
    // send(this.getId(), lastWorkload.time - CloudSim.clock(),
    // CloudSimTagsSDN.REQUEST_OFFER_MORE, workParser);
    // } else {
    // System.out.println("Aucun workload parsé."); // Log pour débogage
    // }
    // }

    /**
     * Planifier un seul Workload.
     * 
     * @param wl         Le Workload à planifier.
     * @param workloadId L'ID de l'application associée.
     */
    public void scheduleWorkload(Workload wl, int workloadId) {
        /* MAJ Nadia */
        // Add cloudlet length validation
        if (wl.request.getLastProcessingCloudletLen() <= 0) {
            System.err.println("⚠ Warning: Workload " + wl.workloadId +
                    " has invalid cloudlet length: " +
                    wl.request.getLastProcessingCloudletLen());
        }
        // Check if the workload is null
        if (wl == null) {
            System.err.println("Erreur : workload NULL !");
        } else {
            System.out.println("Workload ID " + wl.workloadId + " planifié.");
        }

        System.out.println("###################### scheduleWorkload");
        System.out.println("######## Scheduling Workload ID: " + wl.workloadId);
        System.out.println("Workload details:");
        System.out.println("- Submit VM ID: " + wl.submitVmId);
        System.out.println("- WD req Dest VM ID: " + wl.request.getDestinationVmName());
        System.out.println("- Cloudlet Length: " + wl.request.getLastProcessingCloudletLen());
        System.out.println("- Packet Size: "
                + (wl.request.getLastTransmission() != null ? wl.request.getLastTransmission().getPacket().getSize()
                        : "null"));

        // if (wl == null) {
        // Log.printLine("**" + CloudSim.clock() + ": SDNBroker.scheduleWorkload():
        // Workload is null.");
        // return;
        // }

        // Check if the request is null
        if (wl.request == null) {
            Log.printLine("**" + CloudSim.clock() + ": SDNBroker.scheduleWorkload(): Request is null for Workload ID: "
                    + wl.workloadId);
            return;
        }

        System.out.println("Scheduling workload " + wl.workloadId + " at time: " + wl.time);

        // Check if the terminal request is null
        Request terminalRequest = wl.request.getTerminalRequest();
        if (terminalRequest == null) {
            Log.printLine("**" + CloudSim.clock()
                    + ": SDNBroker.scheduleWorkload(): Terminal request is null for Workload ID: " + wl.workloadId);
            return;
        }

        // Calculate schedule time
        double scheduleTime = wl.time - CloudSim.clock();
        if (scheduleTime < 0) {
            Log.printLine("**" + CloudSim.clock()
                    + ": SDNBroker.scheduleWorkload(): Abnormal start time for Workload ID: " + wl.workloadId);
            return;
        }

        // Set the application ID
        wl.appId = workloadId;

        // Retrieve the data center for the VM
        SDNDatacenter dc = SDNBroker.vmIdToDc.get(wl.submitVmId);
        if (dc == null) {
            Log.printLine("**" + CloudSim.clock() + ": SDNBroker.scheduleWorkload(): Datacenter not found for VM ID: "
                    + wl.submitVmId);
            // System.err.println("Contenu de vmIdToDc : " + SDNBroker.vmIdToDc);

            // FIX IT25: Count skipped workloads to prevent simulation stall
            this.completedWorkloadCount++;
            System.out.println("⚠️ [SDNBroker] Workload " + wl.workloadId + " skipped (VM not created) - Progress: "
                    + completedWorkloadCount + " / " + totalWorkloadCount);
            checkIfAllWorkloadsCompleted();
            return;
        }

        // Send the request to the data center
        send(dc.getId(), scheduleTime, CloudSimTagsSDN.WORKLOAD_SUBMIT, wl);

        // Add the workload to the request map
        requestMap.put(terminalRequest.getRequestId(), wl);

        // Log successful scheduling
        Log.printLine(CloudSim.clock() + ": SDNBroker.scheduleWorkload(): Workload ID " + wl.workloadId
                + " scheduled successfully.");
    }

    /* MAJ Nadia */
    /**
     * Nouvelle méthode pour planifier un seul workload.
     * 
     * @param wl Le workload à planifier.
     */
    public void scheduleWorkload(Workload wl) {
        try (PrintWriter logWriter = new PrintWriter(
                new FileWriter("D:/Workspace/CLOUDSIMSDN/scheduleWorkload_log.txt", true))) {
            // Calculer le temps de planification
            double scheduleTime = wl.time - CloudSim.clock();
            if (scheduleTime < 0) {
                logWriter.println("Temps de début anormal pour le workload : " + wl.workloadId);
                return;
            }

            // Récupérer le datacenter correspondant à la VM
            SDNDatacenter dc = SDNBroker.vmIdToDc.get(wl.submitVmId);

            if (dc == null) {
                logWriter.println("VM ID : " + wl.submitVmId + " non trouvé dans aucun datacenter ! Skipping...");

                // FIX IT25: Count skipped workloads to prevent simulation stall
                this.completedWorkloadCount++;
                System.out.println("⚠️ [SDNBroker] Workload " + wl.workloadId + " skipped (VM not created) - Progress: "
                        + completedWorkloadCount + " / " + totalWorkloadCount);
                checkIfAllWorkloadsCompleted();
                return;
            }

            logWriter.println("VM ID : " + wl.submitVmId + " trouvé dans le datacenter : " + dc.getName());

            // Envoyer la requête au datacenter
            send(dc.getId(), scheduleTime, CloudSimTagsSDN.REQUEST_SUBMIT, wl.request);
            System.out.println("📨 Sent Request to Datacenter: " + wl.request);
            requestMap.put(wl.request.getTerminalRequest().getRequestId(), wl);
            logWriter.println("Workload " + wl.workloadId + " ajouté à requestMap.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /* Fin */

    public List<Workload> getWorkloads() {
        return allWorkloads;
        // return null;
    }

    /* Nadia */
    public void writeResult(Workload workload) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("D:/Workspace/CLOUDSIMSDN/resultssssss.txt", true))) {
            writer.println("Workload ID: " + workload.workloadId +
                    ", App ID: " + workload.appId +
                    ", Time: " + workload.time +
                    ", VM ID: " + workload.submitVmId +
                    ", Packet Size: " + workload.submitPktSize);
            Log.printLine("Result written successfully for Workload ID: " + workload.workloadId);
        } catch (IOException e) {
            e.printStackTrace();
            Log.printLine("Failed to write result for Workload ID: " + workload.workloadId);
        }
    }

    public SimEntity getSchedulingPolicy() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSchedulingPolicy'");
    }
}
