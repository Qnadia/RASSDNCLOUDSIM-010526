/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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
import org.cloudbus.cloudsim.sdn.workload.Activity;
import org.cloudbus.cloudsim.sdn.workload.Workload;
import org.cloudbus.cloudsim.sdn.workload.WorkloadReader;
import org.cloudbus.cloudsim.sdn.workload.WorkloadResultWriter;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystemSimple;
import org.cloudbus.cloudsim.sdn.parsers.PhysicalTopologyParser;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyBandwidthAllocation;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicyBandwidthAllocationN;
import org.cloudbus.cloudsim.sdn.policies.vmallocation.*;

/**
 * CloudSimSDN example main program. It loads physical topology file, application
 * deployment configuration file and workload files, and run simulation.
 * Simulation result will be shown on the console 
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class SimpleExampleBWVF extends SimpleExample {
	protected static String physicalTopologyFile 	= "dataset-energy/3energy-physicalH10-latency.json";
	protected static String deploymentFile 		= "dataset-energy/1energy-virtualV40.json";
	protected static String[] workload_files 	= { 
		"dataset-energy/2energy-workload120.csv"
	};
	
	protected static List<String> workloads;
	private static boolean logEnabled = true;

	public interface VmAllocationPolicyFactory {
		public VmAllocationPolicy create(List<? extends Host> list);
	}

	enum VmAllocationPolicyEnum { CombLFF, CombMFF, MipLFF, FCFS, MipMFF, OverLFF, OverMFF, LFF, MFF, Overbooking, Spread, Binpack, LWFF, LWFFVD, RR, SJF }

	private static void printUsage() {
		String runCmd = "java SDNExample";
		System.out.format("Usage: %s <LFF|MFF> [physical.json] [virtual.json] [workload1.csv] [workload2.csv] [...]\n", runCmd);
	}

	public static void main(String[] args) {
		workloads = new ArrayList<>();
		
		// Parse system arguments
		if (args.length < 1) {
			printUsage();
			System.exit(1);
		}
		
		VmAllocationPolicyEnum vmAllocPolicy = VmAllocationPolicyEnum.valueOf(args[0]);
		if (args.length > 1)
			physicalTopologyFile = args[1];
		if (args.length > 2)
			deploymentFile = args[2];
		if (args.length > 3)
			for (int i = 3; i < args.length; i++) {
				workloads.add(args[i]);
			}
		else
			workloads = Arrays.asList(workload_files);
		
		printArguments(physicalTopologyFile, deploymentFile, workloads);
		Log.printLine("Starting CloudSim SDN...");

		try { 
			// Initialize
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			
			VmAllocationPolicyFactory vmAllocationFac = null;
			NetworkOperatingSystem nos = new NetworkOperatingSystemSimple();
			HostFactory hsFac = new HostFactorySimple();
			LinkSelectionPolicy ls = null;

			switch (vmAllocPolicy) {
				case CombMFF:
				case MFF:
					vmAllocationFac = hostList -> new VmAllocationPolicyCombinedMostFullFirst(hostList);
					break;
				case CombLFF:
				case LFF:
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
				case SJF:
					vmAllocationFac = hostList -> new WDAllocationPolicySJF(hostList);
					break;
				default:
					System.err.println("Choose proper VM placement policy!");
					printUsage();
					System.exit(1);
			}

			// Load physical topology
			PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
			ls = new LinkSelectionPolicyBandwidthAllocation();
			nos.setLinkSelectionPolicy(ls);

			// Create a Datacenter
			SDNDatacenter datacenter = createSDNDatacenter("Datacenter_0", physicalTopologyFile, nos, vmAllocationFac);

			// Broker
			SDNBroker broker = createBroker();

			// Submit virtual topology
			broker.submitDeployApplication(datacenter, deploymentFile);
			
			// Submit individual workloads
			submitWorkloads(broker, vmAllocPolicy.name());
			
			// Start simulation
			if (!logEnabled) 
				Log.disable();
			
			double finishTime = CloudSim.startSimulation();
			CloudSim.stopSimulation();
			Log.enable();
			
			broker.printResult();
			
			Log.printLine(finishTime + ": ========== EXPERIMENT FINISHED ===========");
			
			// Print results when simulation is over
			List<Workload> wls = broker.getWorkloads();
			if (wls != null)
				LogPrinter.printWorkloadList(wls);
			
			// Print hosts' and switches' total utilization.
			List<Host> hostList = nos.getHostList();
			List<Switch> switchList = nos.getSwitchList();
			LogPrinter.printEnergyConsumption(hostList, switchList, finishTime);

			Log.printLine("Simultaneously used hosts:" + maxHostHandler.getMaxNumHostsUsed());			
			Log.printLine("CloudSim SDN finished!");

		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}

	public static void submitWorkloads(SDNBroker broker, String sortingAlgorithm) {
		if (workloads != null) {
			for (String workload : workloads) {
				List<Workload> workloadList = WorkloadReader.readWorkload(workload, new WorkloadResultWriter("results.txt"));
				
				// Trier les requêtes en fonction de l'algorithme spécifié
				switch (sortingAlgorithm) {
					case "SJF":
						Collections.sort(workloadList, Comparator.comparingLong(w -> {
							List<Activity> activities = w.request.getActivities();
							if (!activities.isEmpty()) {
								return (long) activities.get(0).getExpectedTime(); // Conversion explicite en long
							}
							return Long.MAX_VALUE; // Si aucune activité, mettre une valeur par défaut
						}));
						break;
					case "FCFS":
						// Pas de tri pour FCFS (First-Come, First-Served)
						break;
					case "RR":
						// Pas de tri pour Round Robin
						break;
					default:
						// Par défaut, pas de tri
						break;
				}
				
				// Soumettre les requêtes (triées ou non)
				for (Workload wl : workloadList) {
					broker.submitRequest(wl.request);
				}
			}
		}
	}

	public static void printArguments(String physical, String virtual, List<String> workloads) {
		System.out.println("Data center infrastructure (Physical Topology) : " + physical);
		System.out.println("Virtual Machine and Network requests (Virtual Topology) : " + virtual);
		System.out.println("Workloads: ");
		for (String work : workloads)
			System.out.println("  " + work);		
	}

	protected static NetworkOperatingSystem nos;
	protected static PowerUtilizationMaxHostInterface maxHostHandler = null;

	protected static SDNDatacenter createSDNDatacenter(String name, String physicalTopology, NetworkOperatingSystem snos, VmAllocationPolicyFactory vmAllocationFactory) {
		nos = snos;
		List<Host> hostList = nos.getHostList();

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<>(); // we are not adding SAN devices by now

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		// Create Datacenter with previously set parameters
		SDNDatacenter datacenter = null;
		try {
			VmAllocationPolicy vmPolicy = vmAllocationFactory.create(hostList);
			maxHostHandler = (PowerUtilizationMaxHostInterface) vmPolicy;
			datacenter = new SDNDatacenter(name, characteristics, vmPolicy, storageList, 0, nos);
			nos.setDatacenter(datacenter);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return datacenter;
	}

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