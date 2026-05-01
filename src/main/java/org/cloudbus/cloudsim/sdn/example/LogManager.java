package org.cloudbus.cloudsim.sdn.example;

import java.io.*;
import java.util.*;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.LogWriter;

public class LogManager {
    private static final Map<String, List<String>> logBuffers = new LinkedHashMap<>();
    private static final String LOG_DIR = ensureLogDir();
    private static double lastLogTime = 0.0;
    private static final Map<String, Integer> lastVmCountPerHost = new HashMap<>();

    // Configuration complète des fichiers de log
    private static final Map<String, String> LOG_CONFIG = new HashMap<String, String>() {
        {
            put("link_utilization_up.csv", "# units: time(s); linkId; bw_used(%); latency(ms)");
            put("link_utilization_down.csv", "# units: time(s); linkId; bw_used(%); latency(ms)");
            put("host_utilization.csv", "# units: time(s); hostId; cpu(%); ram(%); bw(%); energy(Wh)");
            put("vm_utilization.csv", "# units: time(s); vmId; cpu(%); ram(%); mips_alloc; ram_max");
            put("sw_energy.csv", "# units: time(s); switchId; energy(watt)");
            put("packet_delays.csv", "# units: packetId; src; dst; psize; delay(ms); proc_delay(ms); prop_delay(ms); trans_delay(ms); queue_delay(ms)");
            put("host_vm_allocation.csv", "# units: time(s); hostId; vmIds");
            put("host_allocation_summary.csv", "timestamp;hostId;vmIds;cpu(%);ram(%);bw(%);policy;energy(W)");
            put("detailed_energy.csv", "# units: time(s); nodeId; cpu_w; ram_w; bw_w; total_w");
            put("host_energy.csv", "# units: time(s); hostId; energy(Wh)");
            put("host_energy_total.csv", "# units: hostId; total_energy(Wh)");
            put("path_latency.csv", "# units: time(s); src; dst; path; min_bw_Mbps; network_latency(ms); isSelected");
            put("path_latency_final.csv",
                    "# units: time(s); src; dst; path; min_bw_Mbps; avgBwUsedMbps; avgPctUse(%); network_latency(ms); processing_delay(ms); total_delay(ms); selected");
            put("qos_violations.csv", "# units: timestamp; flowId; violationType");
            put("sfc_throughput.csv", "# units: timestamp; sfcId; throughputMbps");
        }
    };

    static {
        // Initialisation des buffers avec en-têtes
        LOG_CONFIG.forEach((filename, header) -> {
            logBuffers.put(filename, new ArrayList<>());
            logBuffers.get(filename).add(header); // Ajout de l'en-tête
        });
    }

    private static String ensureLogDir() {
        String dir = Configuration.workingDirectory + Configuration.experimentName;
        new File(dir).mkdirs();
        return dir;
    }

    public static void logHostAllocationSummary(double timestamp, String hostId, List<String> vmIds,
            double cpu, double ram, double bw,
            String policy, double energyWatt) {
        String vmList = String.join("|", vmIds);
        String line = formatData(timestamp, hostId, vmList, cpu, ram, bw, policy, energyWatt);
        log("host_allocation_summary.csv", line);
    }

    public static void log(String filename, String data) {
        if (logBuffers.containsKey(filename)) {
            logBuffers.get(filename).add(data);
        } else {
            System.err.println("Fichier de log non configuré: " + filename);
        }
    }

    public static void logWithTimestamp(String filename, String data) {
        log(filename, String.format("%.2f;%s", CloudSim.clock(), data));
    }

    public static void flushAll() {
        logBuffers.forEach((filename, lines) -> {
            File logFile = new File(LOG_DIR + File.separator + filename);
            try (PrintWriter out = new PrintWriter(logFile)) {
                lines.forEach(out::println);
                System.out.printf("[Log] %s: %d entrées sauvegardées%n",
                        filename, lines.size() - 1); // -1 pour l'en-tête
            } catch (FileNotFoundException e) {
                System.err.println("Erreur d'écriture: " + filename);
            }
        });
    }

    // Méthodes helpers pour un logging typé
    public static void logLinkUtilization(String linkId, double utilization, double latency) {
        log("link_utilization_up.csv",
                String.format("%.2f;%s;%.2f;%.2f",
                        CloudSim.clock(), linkId, utilization * 100, latency));
    }

    public static void logQoSViolation(String flowId, String violationType) {
        logWithTimestamp("qos_violations.csv",
                String.format("%s;%s", flowId, violationType));
    }

    private static final Map<String, Double> lastLogTimePerHost = new HashMap<>();

    private static final Map<String, Integer> lastLoggedVmCount = new HashMap<>();

    public static void logInitialHostVmAllocation(String hostId, int numVms) {
        log("host_vm_allocation.csv", String.format("%.2f;%s;%d", CloudSim.clock(), hostId, numVms));
    }

    public static void logPacketDelay(long packetId, String src, String dst, long psize, double delayMs, 
            double procMs, double propMs, double transMs, double queueMs) {
        log("packet_delays.csv", formatData(packetId, src, dst, psize, delayMs, procMs, propMs, transMs, queueMs));
    }

    public static void logVmUtilization(double time, int vmId, double cpu, double ram, double mipsAlloc, int ramMax) {
        log("vm_utilization.csv", formatData(time, vmId, cpu, ram, mipsAlloc, ramMax));
    }

    public static void logSfcThroughput(String sfcId, double throughputMbps) {
        logWithTimestamp("sfc_throughput.csv",
                String.format("%s;%.2f", sfcId, throughputMbps));
    }

    public static String formatData(Object... values) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < values.length; i++) {
            Object v = values[i];

            if (v == null) {
                sb.append("NA");
            } else if ((v instanceof Double || v instanceof Float) && i == 0) {
                // 🕒 Premier champ = timestamp → sans décimales
                sb.append(String.format(Locale.US, "%.0f", ((Number) v).doubleValue()));
            } else if (v instanceof Double || v instanceof Float) {
                sb.append(String.format(Locale.US, "%.4f", ((Number) v).doubleValue()));
            } else {
                String s = v.toString().replace(",", "."); // sécurité : remplacer les virgules
                sb.append(s);
            }

            if (i != values.length - 1)
                sb.append(";");
        }

        return sb.toString();
    }

}