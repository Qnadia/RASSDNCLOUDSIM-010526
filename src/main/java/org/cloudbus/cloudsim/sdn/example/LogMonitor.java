package org.cloudbus.cloudsim.sdn.example;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.sdn.*;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.monitor.power.EnhancedHostEnergyModel;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMonitor;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;
// import org.cloudbus.cloudsim.sdn.policies.*;
import org.cloudbus.cloudsim.sdn.qos.PacketDelayInfo;
import org.cloudbus.cloudsim.sdn.qos.QoSMonitor;
import org.cloudbus.cloudsim.sdn.qos.QoSViolation;
import org.cloudbus.cloudsim.core.predicates.Predicate;

import java.util.*;
import java.util.stream.Collectors;

public class LogMonitor extends SimEntity {
    private static final double MONITOR_INTERVAL = Configuration.monitoringTimeInterval;
    private static final double SAFETY_TIMEOUT = 86400; // 24h simulation time safety exit
    private static NetworkOperatingSystem nos;
    private static SDNDatacenter datacenter;
    private static List<? extends Vm> vmList;
    private double finishTime;
    private boolean isMonitoringActive = true;

    // en haut de la classe LogMonitor
    // private static PowerUtilizationMonitor energyMonitor;

    // public static void setEnergyMonitor(PowerUtilizationMonitor monitor) {
    // energyMonitor = monitor;
    // }

    // Au lieu de static unique…
    private static Map<Integer, PowerUtilizationMonitor> hostEnergyMonitors = new HashMap<>();

    public static void initEnergyMonitors(SDNDatacenter dc, EnhancedHostEnergyModel model) {
        hostEnergyMonitors.clear(); // Nettoyer les monitors existants
        for (Host h : dc.getHostList()) {
            if (h instanceof SDNHost) {
                SDNHost sdnHost = (SDNHost) h;
                hostEnergyMonitors.put(sdnHost.getId(), new PowerUtilizationMonitor(model, sdnHost.getId()));
            }
        }
    }

    public void setFinishTime(double time) {
        this.finishTime = time;
    }

    public double getFinishTime() {
        return this.finishTime;
    }

    public static void setVmList(List<? extends Vm> list) {
        vmList = list;
    }

    public static void setDatacenter(SDNDatacenter _datacenter) {
        datacenter = _datacenter;
    }

    public static void setNOS(NetworkOperatingSystem _nos) {
        nos = _nos;
    }

    public LogMonitor(String name, NetworkOperatingSystem nos) {
        super(name);
        this.nos = nos;
    }

    @Override
    public void startEntity() {
        // Premier déclenchement
        scheduleNextMonitoring();
    }

    public static void printFinalEnergyReport(double finishTime) {
        System.out.println("📊 [LogMonitor] Generating final energy report at t=" + finishTime);
        double totalEnergy = 0.0;
        for (Host h : datacenter.getHostList()) {
            if (h instanceof SDNHost) {
                SDNHost sdnHost = (SDNHost) h;
                double energy = sdnHost.getConsumedEnergy();
                totalEnergy += energy;

                // Log final par hôte dans le CSV
                LogManager.log("host_energy_total.csv", LogManager.formatData(
                        finishTime, sdnHost.getName(), sdnHost.getId(), energy));
            }
        }
        // Ligne de TOTAL final dans le CSV
        LogManager.log("host_energy_total.csv", LogManager.formatData("TOTAL", "", "", totalEnergy));
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION:
                if (isMonitoringActive) {
                    if (CloudSim.clock() > SAFETY_TIMEOUT) {
                        System.err.println("🚨 SAFETY TIMEOUT: Simulating too long (" + CloudSim.clock()
                                + "). Force stopping monitoring.");
                        isMonitoringActive = false;
                        LogManager.flushAll();
                        return;
                    }
                    collectAllMetrics();
                    scheduleNextMonitoring();
                } else {
                    System.out.println("⚠️ Monitoring ignoré car désactivé.");
                }
                break;

            case CloudSimTagsSDN.STOP_MONITORING:
                System.out.println("🛑 Monitoring arrêté (tous les workloads terminés)");
                isMonitoringActive = false; // ✅ On stoppe proprement
                double totalEnergy = 0.0;
                for (Host h : datacenter.getHostList()) {
                    if (h instanceof SDNHost) {
                        SDNHost sdnHost = (SDNHost) h;

                        double energy = sdnHost.getConsumedEnergy();
                        totalEnergy += energy;

                        // ✅ Log par hôte
                        LogManager.log("host_energy_total.csv", LogManager.formatData(
                                CloudSim.clock(), sdnHost.getName(), sdnHost.getId(), energy));

                        // Écrire uniquement host_utilization.csv

                        System.out.println("✅ host_utilization.csv généré dans " +
                                Configuration.workingDirectory + Configuration.experimentName);

                    }
                }

                // ✅ Ligne de total global à la fin
                LogManager.log("host_energy_total.csv", LogManager.formatData("TOTAL", "", "", totalEnergy));

                // On s'assure d'extraire les dernières métriques QoS stockées en mémoire
                monitorQoSMetrics();

                // 3) On écrit **tous** les fichiers de logs (dont host_utilization.csv)
                LogManager.flushAll();

                break;

            default:
                System.out.println("🔍 Événement inconnu reçu dans LogMonitor: " + ev.getTag());
                break;
        }
    }

    @Override
    public void shutdownEntity() {
        // Nettoyage éventuel
        System.out.println("🔻 LogMonitor terminé.");
    }

    private void scheduleNextMonitoring() {
        CloudSim.send(getId(), getId(), MONITOR_INTERVAL, CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION, null);
    }

    public static void collectAllMetrics() {
        monitorLinkUtilizations();
        monitorHostResources();
        monitorVmResources();
        monitorEnergyConsumption();
        monitorQoSMetrics();

        // scheduleNextMonitoring(); <-- ❌ retirer cet appel
    }

    // ================== Méthodes de Monitoring ==================

    // 1. Utilisation des liens
    private static void monitorLinkUtilizations() {
        for (Link link : nos.getPhysicalTopology().getAllLinks()) {
            // Créer un identifiant lisible basé sur les nœuds connectés
            String linkId = link.getHighOrder().getName() + "->" + link.getLowOrder().getName();

            String dataUp = LogManager.formatData(
                    CloudSim.clock(),
                    linkId,
                    link.getMonitoringValuesLinkUtilizationUp().getLatestValue() * 100,
                    link.getLatency());

            LogManager.log("link_utilization_up.csv", dataUp);

            String dataDown = LogManager.formatData(
                    CloudSim.clock(),
                    linkId,
                    link.getMonitoringValuesLinkUtilizationDown().getLatestValue() * 100,
                    link.getLatency());

            LogManager.log("link_utilization_down.csv", dataDown);
        }
    }

    // 2. Ressources des hôtes
    private static void monitorHostResources() {
        for (SDNHost host : datacenter.<SDNHost>getHostList()) {
            // double cpuUtil =
            // host.getMonitoringValuesHostCPUUtilization().getLatestValue() * 100;
            // double ramUtil = (double) host.getRamProvisioner().getUsedRam() /
            // host.getRamProvisioner().getRam() * 100;
            double cpuUtil = host.getCpuUtilization() * 100; // now returns [0–1]
            double ramUtil = (double) host.getRamProvisioner().getUsedRam()
                    / host.getRamProvisioner().getRam() * 100.0;

            double energy = host.getConsumedEnergy();

            List<String> vmIds = host.getVmList().stream()
                    .map(vm -> String.valueOf(vm.getId()))
                    .collect(Collectors.toList());

            // Bande passante utilisée (en Mbps)
            MonitoringValues mvBw = host.getMonitoringValuesHostBwUtilization();
            double bwUtil = (double) host.getBwProvisioner().getUsedBw()
                    / host.getBwProvisioner().getBw() * 100.0;

            // Log global
            // Log global avec énergie consolidée
            LogManager.log("host_utilization.csv", LogManager.formatData(
                    CloudSim.clock(),
                    host.getId(),
                    cpuUtil,
                    ramUtil,
                    bwUtil,
                    energy));

            // Log nombre de VMs hébergées
            // LogManager.log("host_vm_allocation.csv", LogManager.formatData(
            // CloudSim.clock(), host.getId(), host.getVmList().size()));

            String policy = Configuration.allocationPolicyName; // ou hardcoded "RR", "BestFit", etc.

            String vmIdList = String.join("|", vmIds); // ex: "0|2|5"

            LogManager.log("host_vm_allocation.csv", LogManager.formatData(
                    CloudSim.clock(), host.getId(), vmIdList));

            LogManager.logHostAllocationSummary(
                    CloudSim.clock(), host.getName(), vmIds, cpuUtil, ramUtil, bwUtil, policy, energy);

        }
    }

    private static void monitorVmResources() {
        for (Vm genericVm : datacenter.getVmList()) {
            if (genericVm instanceof SDNVm) {
                SDNVm vm = (SDNVm) genericVm;

                double cpu = vm.getMonitoringValuesVmCPUUtilization().getLatestValue() * 100;
                double ram = vm.getMonitoringValuesVmRamUtilization().getLatestValue() * 100;
                double bw = vm.getMonitoringValuesVmBwUtilization().getLatestValue() / 1_000_000.0;

                double mipsAlloc = vm.getCurrentRequestedTotalMips();
                int ramMax = vm.getRam();

                LogManager.logVmUtilization(CloudSim.clock(), vm.getId(), cpu, ram, mipsAlloc, ramMax);

                // LogManager.log("vm_bw_utilization.csv", LogManager.formatData(
                // CloudSim.clock(), vm.getId(), bw));
            }
        }
    }

    static Map<Integer, Double> lastEnergyPerHost = new HashMap<>();

    // 4. Consommation d'énergie
    private static void monitorEnergyConsumption() {
        double now = CloudSim.clock();

        for (Host h : datacenter.getHostList()) {
            if (!(h instanceof SDNHost))
                continue;

            SDNHost host = (SDNHost) h;

            double cpu = host.getCpuUtilization() * 100;
            // MAJ Nadia : getActualRamUtilizationFromVms() retournait 0 car
            // monitoringUsedRamPerUnit
            // est réinitialisé immédiatement après chaque lecture dans increaseUsedRam().
            // On utilise directement getRamProvisioner().getUsedRam() qui reflète la RAM
            // allouée par CloudSim.
            double ram = (double) host.getRamProvisioner().getUsedRam()
                    / host.getRamProvisioner().getRam() * 100;
            double bw = Optional.ofNullable(host.getMonitoringValuesHostBwUtilization())
                    .map(mv -> mv.getLatestValue() * 100).orElse(0.0);

            // Vérifie si toutes les VMs du host sont complètement inactives
            boolean allVmsIdle = host.getVmList().stream()
                    .filter(vm -> vm instanceof SDNVm)
                    .map(vm -> (SDNVm) vm)
                    .allMatch(vm -> vm.getMonitoringValuesVmCPUUtilization().getLatestValue() == 0.0 &&
                            vm.getMonitoringValuesVmRamUtilization().getLatestValue() == 0.0 &&
                            vm.getMonitoringValuesVmBwUtilization().getLatestValue() == 0.0);

            // ✅ Si CPU/RAM/BW = 0 ET aucune VM active → pas de consommation énergétique
            if (cpu == 0.0 && ram == 0.0 && bw == 0.0 && allVmsIdle) {
                LogManager.log("host_energy.csv", LogManager.formatData(now, host.getId(), 0.0));
                continue;
            }

            // ⚡ Sinon, on calcule normalement
            // ⚡ Energie gérée par SDNHost.powerMonitor directement
            PowerUtilizationMonitor mon = hostEnergyMonitors.get(host.getId());
            double consumed = mon.addPowerConsumption(now, cpu, ram, bw);

            // System.out.printf(
            // "t=%.1f | Host %d | CPU=%.2f%% RAM=%.2f%% BW=%.2f%% → Energy=%.5f Wh\n",
            // now, host.getId(), cpu, ram, bw, consumed);

            LogManager.log("host_energy.csv", LogManager.formatData(now, host.getId(), consumed));
        }

        // Mesure énergie des switches
        for (Switch sw : nos.getSwitchList()) {
            double energy = sw.getEnergy();
            LogManager.log("sw_energy.csv", LogManager.formatData(now, sw.getId(), energy));
        }
    }

    // private static void monitorEnergyConsumption() {
    // double now = CloudSim.clock();
    // // 1. Énergie des hôtes
    // for (Host h : datacenter.getHostList()) {
    // if (h instanceof SDNHost) {
    // SDNHost host = (SDNHost) h;
    // double cpu = host.getCpuUtilization() * 100;
    // double ram = host.getActualRamUtilizationFromVms() * 100;
    // double bw = Optional.ofNullable(host.getMonitoringValuesHostBwUtilization())
    // .map(mv -> mv.getLatestValue() * 100).orElse(0.0);

    // // ✅ Ignore l'énergie si tout est à zéro
    // // if (cpu == 0.0 && ram == 0.0 && bw == 0.0) {
    // // LogManager.log("host_energy.csv", LogManager.formatData(now, host.getId(),
    // 0.0));
    // // continue;
    // // }

    // // ⚡ Calcul via le monitor global (detailed_energy.csv)
    // PowerUtilizationMonitor mon = hostEnergyMonitors.get(host.getId());
    // double consumed = mon.addPowerConsumption(now, cpu, ram, bw);
    // System.out.printf(
    // "t=%.1f | Host %d | CPU=%.2f%% RAM=%.2f%% BW=%.2f%% → Energy=%.4f\n",
    // now, host.getId(), cpu, ram, bw, consumed);

    // LogManager.log("host_energy.csv", LogManager.formatData(now, host.getId(),
    // consumed));

    // }
    // }
    // // 1. Énergie des hôtes
    // // for (Host host : datacenter.getHostList()) {
    // // if (host instanceof SDNHost) {
    // // SDNHost sdnHost = (SDNHost) host;

    // // // Consommation totale depuis le powerMonitor
    // // double energy = sdnHost.getConsumedEnergy();

    // // //if (lastEnergyPerHost.getOrDefault(sdnHost.getId(), -1.0) != energy) {
    // // // LogManager.log("host_energy.csv",
    // LogManager.formatData(CloudSim.clock(), sdnHost.getId(), energy));
    // // // lastEnergyPerHost.put(sdnHost.getId(), energy);
    // // //}

    // // // Exemple de correction :
    // // LogManager.log("host_energy.csv",
    // // LogManager.formatData(CloudSim.clock(), sdnHost.getId(), energy));

    // // // if (energy > 0.0001) { // Tu peux ajuster le seuil ici
    // // // LogManager.log("host_energy.csv", String.format("%.4f;%d;%.4f",
    // CloudSim.clock(), host.getId(), energy));
    // // // lastEnergyPerHost.put(sdnHost.getId(), energy);
    // // // }
    // // }
    // // }

    // // 2. Énergie des switches
    // for (Switch sw : nos.getSwitchList()) {
    // double energy = sw.getEnergy(); // suppose que cette méthode est implémentée
    // LogManager.log("sw_energy.csv", LogManager.formatData(
    // CloudSim.clock(), sw.getId(), energy));
    // }
    // }

    // 5. QoS - Délai paquet / Violation SLA / Débit SFC
    private static void monitorQoSMetrics() {
        for (QoSViolation v : QoSMonitor.getViolations()) {
            LogManager.log("qos_violations.csv", LogManager.formatData(
                    CloudSim.clock(), v.getFlowId(), v.getType()));
        }
        QoSMonitor.getViolations().clear(); // ✅ Éviter les logs redondants

        for (PacketDelayInfo delay : QoSMonitor.getPacketDelays()) {
            LogManager.logPacketDelay(delay.packetId, delay.source, delay.destination, delay.psize, 
                delay.delayInMs, delay.procDelay, delay.propDelay, delay.transDelay, delay.queueDelay);
        }
        QoSMonitor.getPacketDelays().clear(); // ✅ Éviter les logs redondants
    }

}
