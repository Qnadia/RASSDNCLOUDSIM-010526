/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example;

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
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmAllocationPolicyLWFF;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.HostFactory;
import org.cloudbus.cloudsim.sdn.HostFactorySimple;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.workload.Activity;
import org.cloudbus.cloudsim.sdn.workload.Workload;
import org.cloudbus.cloudsim.sdn.workload.WorkloadReader;
import org.cloudbus.cloudsim.sdn.workload.WorkloadResultWriter;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem_Old;
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
 * CloudSimSDN example main program. It loads physical topology file, application
 * deployment configuration file and workload files, and run simulation.
 * Simulation result will be shown on the console 
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */

/* cmde d'execution :  d:; cd 'd:\Workspace\cloudsimsdn'; & 'C:\Program Files\Java\jdk-23\bin\java.exe' '@C:\Users\ADMINI~1\AppData\Local\Temp\cp_6kpxfj80elftoran07yk4bgrb.argfile' 'org.cloudbus.cloudsim.sdn.example.SimpleExampleSelectLinkBandwidth'
 LFF dataset-energy\energy-physicalV2.json dataset-energy\energy-virtualV2.json dataset-energy\energy-workloadV2.csv */
public class SimpleExampleSelectLinkBandwidthVMAllocPol extends SimpleExample {
	protected static String physicalTopologyFile 	= "dataset-energy/3energy-physicalH10-latency.json";
	protected static String deploymentFile 		= "dataset-energy/1energy-virtualV40.json";
	protected static String [] workload_files 			= { 
		"dataset-energy/2energy-workload120.csv"
		};
	
	protected static List<String> workloads;
	
	private  static boolean logEnabled = true;

	public interface VmAllocationPolicyFactory {
		public VmAllocationPolicy create(List<? extends Host> list);
	}

	enum VmAllocationPolicyEnum{ CombLFF, CombMFF, MipLFF,FCFS, MipMFF, OverLFF, OverMFF, LFF, MFF, Overbooking, Spread,Binpack,LWFF,LWFFVD,RR,SJF}	
	
	private static void printUsage() {
		String runCmd = "java SDNExample";
		System.out.format("Usage: %s <LFF|MFF> [physical.json] [virtual.json] [workload1.csv] [workload2.csv] [...]\n", runCmd);
	}

	/**
	 * Creates main() to run this example.
	 *
	 * @param args the args
	 */
	@SuppressWarnings("unused")
	public static void main(String[] args) {

		workloads = new ArrayList<String>();
		
		// Parse system arguments
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
			workloads = (List<String>) Arrays.asList(workload_files);
		
		printArguments(physicalTopologyFile, deploymentFile, workloads);
		Log.printLine("Starting CloudSim SDN...");

		try { 
			// Initialize
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			
			VmAllocationPolicyFactory vmAllocationFac = null;
			NetworkOperatingSystem_Old nos = new NetworkOperatingSystemSimple();
			HostFactory hsFac = new HostFactorySimple();
			LinkSelectionPolicy ls = null;
			switch(vmAllocPolicy) {
			case CombMFF:
			case MFF:
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> hostList) { return new VmAllocationPolicyCombinedMostFullFirst(hostList); }
				};
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();
				//ls = new LinkSelectionPolicyBandwidthAllocationN();
				break;
			case LFF: 
				vmAllocationFac = hostList -> new VmAllocationPolicyCombinedLeastFullFirst(hostList);
					
				// vmAllocationFac = new VmAllocationPolicyFactory() {
				// 	public VmAllocationPolicy create(List<? extends Host> hostList) { return new VmAllocationPolicyCombinedLeastFullFirst(hostList); }
				// };
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();
				//ls = new LinkSelectionPolicyBandwidthAllocationN();
			case CombLFF:
				vmAllocationFac = hostList -> new VmAllocationPolicyCombinedLeastFullFirstV2(hostList);
				
				// vmAllocationFac = new VmAllocationPolicyFactory() {
				// 	public VmAllocationPolicy create(List<? extends Host> hostList) { return new VmAllocationPolicyCombinedLeastFullFirst(hostList); }
				// };
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();
				//ls = new LinkSelectionPolicyBandwidthAllocationN();
				break;
			case MipMFF: 
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> hostList) { return new VmAllocationPolicyMipsMostFullFirst(hostList); }
				};
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();
				//ls = new LinkSelectionPolicyBandwidthAllocationN();
				break;
			case MipLFF:
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> hostList) { return new VmAllocationPolicyMipsLeastFullFirst(hostList); }
				};
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();
				//ls = new LinkSelectionPolicyBandwidthAllocationN();
				break;

			case FCFS: // Nouveau cas pour FCFS
				//vmAllocationFac = hostList -> 
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> hostList) { return new   VmAllocationPolicyFCFS(hostList); }
				};
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();

				//ls = new LinkSelectionPolicyBandwidthAllocationN();
				break;
			
			case RR: // Nouveau cas pour Round Robin 
				//vmAllocationFac = hostList -> 
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> hostList) { return new   VmAllocationPolicyRR(hostList); }
				};
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();

				//ls = new LinkSelectionPolicyBandwidthAllocationN();
				break;

			case Spread: // Nouveau cas pour Spread
				//vmAllocationFac = hostList -> 
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> hostList) { return new   VmAllocationPolicySpreadN(hostList); }
				};
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();

				//ls = new LinkSelectionPolicyBandwidthAllocationN();
				break;
			
			case Binpack: // Nouveau cas pour Binpack
				
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> hostList) { return new   VmAllocationPolicyBinPack(hostList); }
				};
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();

				//ls = new LinkSelectionPolicyBandwidthAllocationN();
				break;

			case LWFF:
				vmAllocationFac = hostList -> new VmAllocationPolicyLWFFF(hostList);
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();
				//ls = new LinkSelectionPolicyBandwidthAllocationN();
				break;

			case LWFFVD:
				vmAllocationFac = hostList -> new VmAllocationPolicyLWFFVD(hostList);
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocationN();
				//System.out.println("ttttttttttttttttttttttttt");
				//ls = new LinkSelectionPolicyBandwidthAllocation();
				break;
			
			case SJF:
				vmAllocationFac = hostList -> new VmAllocationPolicyLWFFF(hostList);
				//vmAllocationFac = hostList -> new VmAllocationPolicyBinPack(hostList);
				//vmAllocationFac = hostList -> new VmAllocationPolicyLWFFVD(hostList);
				//vmAllocationFac = hostList -> new VmAllocationPolicySpreadN(hostList);
				//vmAllocationFac = hostList -> new VmAllocationPolicyRR(hostList);
				//vmAllocationFac = hostList -> new VmAllocationPolicyFCFS(hostList);
				//vmAllocationFac = hostList -> new VmAllocationPolicyMipsLeastFullFirst(hostList);
				//vmAllocationFac = hostList -> new VmAllocationPolicyMipsMostFullFirst(hostList);
				
				//CombLFFV2
				//vmAllocationFac = hostList -> new VmAllocationPolicyCombinedLeastFullFirstV2(hostList);
				// LFF = CombLFF
				//vmAllocationFac = hostList -> new VmAllocationPolicyCombinedLeastFullFirst(hostList);
				
				PhysicalTopologyParser.loadPhysicalTopologySingleDC(physicalTopologyFile, nos, hsFac);
				ls = new LinkSelectionPolicyBandwidthAllocation();
				break;
				
			default:
				System.err.println("Choose proper VM placement polilcy!");
				printUsage();
				System.exit(1);
			}
			
			// Set LinkSelectionPolicy
			nos.setLinkSelectionPolicy(ls);

			// Create a Datacenter
			SDNDatacenter datacenter = createSDNDatacenter("Datacenter_0", physicalTopologyFile, nos, vmAllocationFac);

			// Broker
			SDNBroker broker = createBroker();
			int brokerId = broker.getId();

			// Submit virtual topology
			broker.submitDeployApplication(datacenter, deploymentFile);
			
			// Submit individual workloads
			//submitWorkloads(broker,vmAllocPolicy.name(), workload_files);

			//submitWorkloads(broker);

			submitWorkloads(broker, "SJF", workload_files);

			
			// Sixth step: Starts the simulation
			if(!SimpleExampleSelectLinkBandwidthVMAllocPol.logEnabled) 
				Log.disable();
			
			double finishTime = CloudSim.startSimulation();
			CloudSim.stopSimulation();
			Log.enable();
			
			broker.printResult();
			
			Log.printLine(finishTime+": ========== EXPERIMENT FINISHED ===========");
			
			// Print results when simulation is over
			List<Workload> wls = broker.getWorkloads();
			if(wls != null)
				LogPrinter.printWorkloadList(wls);
			
			// Print hosts' and switches' total utilization.
			List<Host> hostList = nos.getHostList();
			List<Switch> switchList = nos.getSwitchList();
			LogPrinter.printEnergyConsumption(hostList, switchList, finishTime);

			Log.printLine("Simultanously used hosts:"+maxHostHandler.getMaxNumHostsUsed());			
			Log.printLine("CloudSim SDN finished!");

		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	public static void submitWorkloads(SDNBroker broker) {
		// Submit workload files individually
		if(workloads != null) {
			for(String workload:workloads)
				broker.submitRequests(workload);
		}
		
		// Or, Submit groups of workloads
		//submitGroupWorkloads(broker, WORKLOAD_GROUP_NUM, WORKLOAD_GROUP_PRIORITY, WORKLOAD_GROUP_FILENAME, WORKLOAD_GROUP_FILENAME_BG);
	}

	/* Nadia QOUDHADH : Modification SJF */

	public static void submitWorkloads(SDNBroker broker, String sortingAlgorithm, String[] workloadFiles) {
		try (PrintWriter logWriter = new PrintWriter(new FileWriter("D:/Workspace/CLOUDSIMSDN/submitWorkloads_log.txt", true))) {
			logWriter.println("Starting submitWorkloads with sortingAlgorithm: " + sortingAlgorithm);
	
			// Vérifier que les cartes sont bien remplies
			Map<String, Integer> vmNames = NetworkOperatingSystem_Old.getVmNameToIdMap();
			Map<String, Integer> flowNames = NetworkOperatingSystem_Old.getFlowNameToIdMap();
			Map<Integer, Long> flowIdToBandwidthMap = NetworkOperatingSystem_Old.getFlowIdToBandwidthMap(); // Récupérer la bande passante

			if (vmNames == null || vmNames.isEmpty()) {
				logWriter.println("VM Names map is empty or null. Ensure the virtual topology is loaded before creating WorkloadParser.");
				return;
			}
			if (flowNames == null || flowNames.isEmpty()) {
				logWriter.println("Flow Names map is empty or null. Ensure the virtual topology is loaded before creating WorkloadParser.");
				return;
			}
	
			// Vérifiez si workloadFiles est défini
			if (workloadFiles != null && workloadFiles.length > 0) {
				for (String workloadFile : workloadFiles) {
					logWriter.println("Processing workload file: " + workloadFile);
	
					// Traitement spécifique pour la politique SJF
					if ("SJF".equals(sortingAlgorithm)) {
						logWriter.println("Sorting workloads using SJF policy.");
	
						// Créer un WorkloadParser pour lire le fichier de charge de travail
						WorkloadParser workloadParser = new WorkloadParser(
							workloadFile, // Nom du fichier
							broker.getId(), // ID de l'utilisateur
							new UtilizationModelFull(), // Modèle d'utilisation
							vmNames, // Mappage des noms de VM aux ID
							flowNames ,// Mappage des noms de flux aux ID
							flowIdToBandwidthMap

						);
	
						// Lire les workloads
						workloadParser.parseNextWorkloadss();
						List<Workload> workloadList = workloadParser.getParsedWorkloads();
	
						logWriter.println("Number of workloads parsed: " + workloadList.size());
	
						// Vérifier si des workloads ont été parsés
						if (workloadList.isEmpty()) {
							logWriter.println("No workloads parsed from file: " + workloadFile);
							continue;
						}
	
						// Trier les workloads selon la politique SJF
						// Trier les workloads selon la politique SJF
						workloadList.sort(Comparator.comparingDouble(wl -> wl.request.getTotalLength()));
						logWriter.println("Workloads sorted using SJF policy.");
	
						// Soumettre les requêtes triées selon la politique SJF
						for (Workload w : workloadList) {
							if (w.request == null) {
								logWriter.println("Skipping workload " + w.workloadId + " because request is null.");
								continue;
							}
	
							logWriter.println("Submitting workload: " + w.workloadId);
							broker.scheduleRequestt(workloadParser); // Soumettre la requête directement
	
							// Écrire les résultats de la charge de travail
							broker.writeResult(w);
						}
					} else {
						// Pour les autres politiques, soumettre les workloads directement sans tri
						logWriter.println("No sorting applied for policy: " + sortingAlgorithm);
						broker.submitRequests(workloadFile); // Soumettre le fichier de charge de travail directement
					}
				}
			} else {
				logWriter.println("Aucun fichier de charge de travail défini (workloadFiles est null ou vide).");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	public static void printArguments(String physical, String virtual, List<String> workloads) {
		System.out.println("Data center infrastructure (Physical Topology) : "+ physical);
		System.out.println("Virtual Machine and Network requests (Virtual Topology) : "+ virtual);
		System.out.println("Workloads: ");
		for(String work:workloads)
			System.out.println("  "+work);		
	}
	
	/**
	 * Creates the datacenter.
	 *
	 * @param name the name
	 *
	 * @return the datacenter
	 */
	protected static NetworkOperatingSystem_Old nos;
	protected static PowerUtilizationMaxHostInterface maxHostHandler = null;
	protected static SDNDatacenter createSDNDatacenter(String name, String physicalTopology, NetworkOperatingSystem_Old snos, VmAllocationPolicyFactory vmAllocationFactory) {
		// In order to get Host information, pre-create NOS.
		nos=snos;
		List<Host> hostList = nos.getHostList();

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		// Create Datacenter with previously set parameters
		SDNDatacenter datacenter = null;
		try {
			VmAllocationPolicy vmPolicy = vmAllocationFactory.create(hostList);
			maxHostHandler = (PowerUtilizationMaxHostInterface)vmPolicy;
			datacenter = new SDNDatacenter(name, characteristics, vmPolicy, storageList, 0, nos);
			
			
			nos.setDatacenter(datacenter);
		} 
		// try {
		// 	VmAllocationPolicy vmPolicy = vmAllocationFactory.create(hostList);
		// 	if (vmPolicy instanceof PowerUtilizationMaxHostInterface) {
		// 		maxHostHandler = (PowerUtilizationMaxHostInterface) vmPolicy;
		// 	} else {
		// 		throw new ClassCastException("VmAllocationPolicy does not implement PowerUtilizationMaxHostInterface");
		// 	}
		// 	datacenter = new SDNDatacenter(name, characteristics, vmPolicy, storageList, 0, nos);
			
		// 	nos.setDatacenter(datacenter);
		// } 
		// catch (Exception e) {
		// 	e.printStackTrace();
		// }
		
		// return datacenter;
		// try {
		// 	VmAllocationPolicy vmPolicy = vmAllocationFactory.create(hostList);
		// 	System.out.println("Created VmAllocationPolicy class: " + vmPolicy.getClass().getName());
	
		// 	if (vmPolicy instanceof PowerUtilizationMaxHostInterface) {
		// 		System.out.println("vmPolicy implements PowerUtilizationMaxHostInterface");
		// 		maxHostHandler = (PowerUtilizationMaxHostInterface) vmPolicy;
		// 	} else {
		// 		throw new ClassCastException("VmAllocationPolicy does not implement PowerUtilizationMaxHostInterface");
		// 	}
		// 	datacenter = new SDNDatacenter(name, characteristics, vmPolicy, storageList, 0, snos);
		// 	snos.setDatacenter(datacenter);
		// } /
		catch (Exception e) {
			e.printStackTrace();
		}
		 return datacenter;
	}

	// We strongly encourage users to develop their own broker policies, to
	// submit vms and cloudlets according
	// to the specific rules of the simulated scenario
	/**
	 * Creates the broker.
	 *
	 * @return the datacenter broker
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
	

	// static String WORKLOAD_GROUP_FILENAME = "workload_10sec_100_default.csv";	// group 0~9
	// static String WORKLOAD_GROUP_FILENAME_BG = "workload_10sec_100.csv"; // group 10~29
	// static int WORKLOAD_GROUP_NUM = 2;
	// static int WORKLOAD_GROUP_PRIORITY = 1;
	
	// public static void submitGroupWorkloads(SDNBroker broker, int workloadsNum, int groupSeperateNum, String filename_suffix_group1, String filename_suffix_group2) {
	// 	for(int set=0; set<workloadsNum; set++) {
	// 		String filename = filename_suffix_group1;
	// 		if(set>=groupSeperateNum) 
	// 			filename = filename_suffix_group2;
			
	// 		filename = set+"_"+filename;
	// 		broker.submitRequests(filename);
	// 	}
	// }

	
	/// Under development
	/*
	static class WorkloadGroup {
		static int autoIdGenerator = 0;
		final int groupId;
		
		String groupFilenamePrefix;
		int groupFilenameStart;
		int groupFileNum;
		
		WorkloadGroup(int id, String groupFilenamePrefix, int groupFileNum, int groupFilenameStart) {
			this.groupId = id;
			this.groupFilenamePrefix = groupFilenamePrefix;
			this.groupFileNum = groupFileNum;
		}
		
		List<String> getFileList() {
			List<String> filenames = new LinkedList<String>();
			
			for(int fileId=groupFilenameStart; fileId< this.groupFilenameStart+this.groupFileNum; fileId++) {
				String filename = groupFilenamePrefix + fileId;
				filenames.add(filename);
			}
			return filenames;
		}
		
		public static WorkloadGroup createWorkloadGroup(String groupFilenamePrefix, int groupFileNum) {
			return new WorkloadGroup(autoIdGenerator++, groupFilenamePrefix, groupFileNum, 0);
		}
		public static WorkloadGroup createWorkloadGroup(String groupFilenamePrefix, int groupFileNum, int groupFilenameStart) {
			return new WorkloadGroup(autoIdGenerator++, groupFilenamePrefix, groupFileNum, groupFilenameStart);
		}
	}
	
	static LinkedList<WorkloadGroup> workloadGroups = new LinkedList<WorkloadGroup>();
	 */
}