package org.cloudbus.cloudsim.sdn.example.SSLAB;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.IOException;

import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.HostFactory;
import org.cloudbus.cloudsim.sdn.HostFactorySimple;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemSimple;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.parsers.PhysicalTopologyParser;
import org.cloudbus.cloudsim.sdn.parsers.WorkloadParser;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.*;

/**
 * Classe de base abstraite contenant les composants et méthodes communs.
 */
public abstract class SimpleExampleBase {
    protected static String physicalTopologyFile;
    protected static String deploymentFile;
    protected static String[] workload_files;
    protected static List<String> workloads;
    protected static boolean logEnabled = true;
    protected static NetworkOperatingSystem nos;
    protected static PowerUtilizationMaxHostInterface maxHostHandler = null; // Si utilisé

    /**
     * Interface pour la fabrique de politiques d'allocation de VM.
     */
    public interface VmAllocationPolicyFactory {
        public VmAllocationPolicy create(List<? extends Host> list);
    }

    /**
     * Imprime les arguments utilisés pour l'exécution.
     *
     * @param physical  Topologie physique
     * @param virtual   Topologie virtuelle
     * @param workloads Liste des fichiers de workloads
     */
    protected static void printArguments(String physical, String virtual, List<String> workloads) {
        System.out.println("Data center infrastructure (Physical Topology) : " + physical);
        System.out.println("Virtual Machine and Network requests (Virtual Topology) : " + virtual);
        System.out.println("Workloads: ");
        for (String work : workloads)
            System.out.println("  " + work);
    }

    /**
     * Charge la topologie physique.
     *
     * @param physicalTopologyFile Chemin vers le fichier de topologie physique
     * @param nos                  Instance de NetworkOperatingSystem
     * @param hsFac                Instance de HostFactory
     */
    protected static void loadPhysicalTopology(String physicalTopologyFile, NetworkOperatingSystem nos,
            HostFactory hsFac) {
        PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
    }

    /**
     * Crée un datacenter SDN.
     *
     * @param name                Nom du datacenter
     * @param physicalTopology    Chemin vers la topologie physique
     * @param snos                Instance de NetworkOperatingSystem
     * @param vmAllocationFactory Fabrique de politique d'allocation de VM
     * @return Instance de SDNDatacenter créée
     */
    protected static SDNDatacenter createSDNDatacenter(
            String name,
            String physicalTopologyFile,
            NetworkOperatingSystem nos,
            VmAllocationPolicyFactory vmAllocationFac,
            WorkloadParser workloadParser,
            int appId,
            Map<Integer, WorkloadParser> workloadParsers) throws Exception {

        // Charger la topologie physique
        HostFactory hostFactory = new HostFactorySimple();
        loadPhysicalTopology(physicalTopologyFile, nos, hostFactory);

        // Créer DatacenterCharacteristics (même que dans le main)
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                "x86",
                "Linux",
                "Xen",
                nos.getHostList(),
                10.0,
                0.01,
                0.05,
                0.1,
                0.1);

        // Politique d'allocation
        VmAllocationPolicy vmAllocationPolicy = vmAllocationFac.create(nos.getHostList());

        // Liste de storage (vide par défaut)
        List<Storage> storageList = new ArrayList<>();

        // Créer le SDNDatacenter avec tes nouveaux paramètres
        SDNDatacenter datacenter = new SDNDatacenter(
                name,
                characteristics,
                vmAllocationPolicy,
                storageList,
                1.0, // Scheduling interval
                nos,
                workloadParser,
                appId,
                workloadParsers);

        return datacenter;
    }

    protected static SDNDatacenter createSDNDatacenter(String name, String physicalTopology,
            NetworkOperatingSystem snos, VmAllocationPolicyFactory vmAllocationFactory) {
        List<Host> hostList = snos.getHostList();

        String arch = "x86"; // architecture système
        String os = "Linux"; // système d'exploitation
        String vmm = "Xen";

        double time_zone = 10.0; // fuseau horaire de la ressource
        double cost = 3.0; // coût de traitement
        double costPerMem = 0.05; // coût par mémoire
        double costPerStorage = 0.001; // coût par stockage
        double costPerBw = 0.0; // coût par bande passante
        List<Storage> storageList = new ArrayList<>(); // liste de stockage (vide pour l'instant)

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        // Créer le Datacenter avec les paramètres définis
        SDNDatacenter datacenter = null;
        try {
            VmAllocationPolicy vmPolicy = vmAllocationFactory.create(hostList);
            System.out.println("Classe SimpeExampelBase");
            if (vmPolicy instanceof PowerUtilizationMaxHostInterface) {
                maxHostHandler = (PowerUtilizationMaxHostInterface) vmPolicy;
            }
            datacenter = new SDNDatacenter(name, characteristics, vmPolicy, storageList, 0, snos);
            snos.setDatacenter(datacenter);
// CloudSim.addEntity(datacenter); // Redundant: Datacenter constructor already adds itself
        } catch (Exception e) {
            e.printStackTrace();
        }
        return datacenter;
    }

    /**
     * Crée un broker SDN.
     *
     * @return Instance de SDNBroker créée
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
}
