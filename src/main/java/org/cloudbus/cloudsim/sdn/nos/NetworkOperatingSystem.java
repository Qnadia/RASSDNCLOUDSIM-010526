/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.nos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.PredicateType;
import org.cloudbus.cloudsim.sdn.CloudSimEx;
import org.cloudbus.cloudsim.sdn.CloudSimTagsSDN;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.LogWriter;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.example.LogManager;
import org.cloudbus.cloudsim.sdn.example.LogMonitor;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMonitor;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.PhysicalTopology;
import org.cloudbus.cloudsim.sdn.physicalcomponents.PhysicalTopologyInterCloud;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
// import org.cloudbus.cloudsim.sdn.policies.vmallocation.overbooking.OverbookingVmAllocationPolicy; // removed: overbooking excluded
// import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionChainPolicy; // removed: sfc package deleted
import org.cloudbus.cloudsim.sdn.virtualcomponents.FlowConfig;
import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;
import org.cloudbus.cloudsim.sdn.virtualcomponents.VirtualNetworkMapper;
import org.cloudbus.cloudsim.sdn.workload.Transmission;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * NOS calculates and estimates network behaviour. It also mimics SDN Controller
 * functions.
 * It manages channels between allSwitches, and assigns packages to channels and
 * control their completion
 * Once the transmission is completed, forward the packet to the destination.
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public abstract class NetworkOperatingSystem extends SimEntity {
	protected SDNDatacenter datacenter;

	// Physical topology
	protected PhysicalTopology topology;

	// Virtual topology
	protected VirtualNetworkMapper vnMapper = null;
	protected ChannelManager channelManager = null;
	protected boolean isApplicationDeployed = false;

	// Map: Vm ID -> VM
	protected HashMap<Integer, Vm> vmMapId2Vm = new HashMap<>();

	// Global map (static): Vm ID -> VM
	protected static HashMap<Integer, Vm> gvmMapId2Vm = new HashMap<>();

	// Vm ID (src or dst) -> all Flow from/to the VM
	protected Multimap<Integer, FlowConfig> flowMapVmId2Flow = HashMultimap.create();

	// Global map (static): Flow ID -> VM
	protected static Map<Integer, FlowConfig> gFlowMapFlowId2Flow = new HashMap<>();

	// MAJ NADIA
	/** monitor for hosts’ energy consumption */
	private PowerUtilizationMonitor energyMonitor;

	/**
	 * Inject the same monitor you built in main.
	 */
	public void setEnergyMonitor(PowerUtilizationMonitor monitor) {
		this.energyMonitor = monitor;
	}

	protected Map<Integer, Long> flowIdToBandwidthMap = new HashMap<>();
	private Map<String, Integer> vmNameIdMap;
	private Map<String, Integer> flowNameIdMap;

	// Ajoute ceci dans ta classe NetworkOperatingSystem
	private boolean stopMonitoring = false;

	// Ajoutez ces méthodes pour définir les mappages
	public void setVmNameIdMap(Map<String, Integer> vmNameIdMap) {
		this.vmNameIdMap = vmNameIdMap;
	}

	public void setFlowNameIdMap(Map<String, Integer> flowNameIdMap) {
		this.flowNameIdMap = flowNameIdMap;
	}

	// Ajoutez ces méthodes pour récupérer les mappages (optionnel)
	public Map<String, Integer> getVmNameIdMap() {
		return vmNameIdMap;
	}

	public Map<String, Integer> getFlowNameIdMap() {
		return flowNameIdMap;
	}

	// Resolution of the result.
	public static final long bandwidthWithinSameHost = 1500000000; // bandwidth between VMs within a same host: 12Gbps =
																	// 1.5GBytes/sec
	public static final double latencyWithinSameHost = 0.1; // 0.1 msec latency

	private double lastMigration = 0;
	private double lastAdjustAllChannelTime = -1;
	private double nextEventTime = -1;

	/* MAJ Nadia */
	private List<Link> links; // Une liste pour stocker tous les liens

	// Ajouter un lien à la topologie
	public void addLink(Link link) {
		this.links.add(link);
	}

	// Récupérer les liens entre deux nœuds
	public List<Link> getLinks(Node src, Node dest) {
		List<Link> result = new ArrayList<>();
		for (Link link : links) {
			if ((link.getHighOrder().equals(src) && link.getLowOrder().equals(dest)) ||
					(link.getHighOrder().equals(dest) && link.getLowOrder().equals(src))) {
				result.add(link);
			}
		}
		return result;
	}

	private Map<Integer, Node> nodes; // Une map pour stocker les nœuds par leur ID

	// Ajouter un nœud à la topologie
	public void addNode(Node node) {
		this.nodes.put(node.getAddress(), node);
	}

	// Récupérer un nœud par son ID
	public Node getNodeById(int nodeId) {
		Node node = nodes.get(nodeId);
		if (node == null) {
			System.err.println("⚠ Nœud introuvable pour l'ID : " + nodeId);
		}
		return node;
	}

	public void setFlowIdToBandwidthMap(Map<Integer, Long> flowIdToBandwidthMap) {
		this.flowIdToBandwidthMap = flowIdToBandwidthMap;

		// Vérification simple
		System.out.println("FlowIdToBandwidthMap reçu dans le NOS :");
		for (Map.Entry<Integer, Long> entry : flowIdToBandwidthMap.entrySet()) {
			System.out.println("FlowID: " + entry.getKey() + ", BW: " + entry.getValue());
		}
	}

	/**
	 * Retourne la topologie réseau actuelle.
	 * 
	 * @return La topologie réseau sous forme de Map<Node, List<Link>>.
	 */
	public Map<Node, List<Link>> getNetworkTopology() {
		Map<Node, List<Link>> networkTopology = new HashMap<>();

		for (Node node : topology.getAllNodes()) {
			List<Link> adjLinks = new ArrayList<>(topology.getAdjacentLinks(node));
			networkTopology.put(node, adjLinks);
		}

		return networkTopology;
	}

	/**
	 * 1. Map VMs and middleboxes to hosts, add the new vm/mb to the vmHostTable,
	 * advise host, advise dc
	 * 2. Set channels and bws
	 * 3. Set routing tables to restrict hops to meet latency
	 */
	// protected abstract boolean deployApplication(List<Vm> vms,
	// Collection<FlowConfig> links, List<Object> sfcPolicy);

	// public NetworkOperatingSystem(String name) {
	// super(name);
	// this.vnMapper = new VirtualNetworkMapper(this);
	// this.channelManager = new ChannelManager(this, vnMapper);
	// this.topology = new PhysicalTopologyInterCloud();
	// }
	public NetworkOperatingSystem(String name) {
		super(name);
		this.vnMapper = new VirtualNetworkMapper(this);
		this.channelManager = new ChannelManager(this, vnMapper);
		this.topology = new PhysicalTopologyInterCloud();
		this.nodes = new HashMap<>(); // Initialiser la map des nœuds
		this.links = new ArrayList<>(); // Initialiser la liste des liens
	}

	@Override
	public void processEvent(SimEvent ev) {
		int tag = ev.getTag();

		switch (tag) {
			case CloudSimTagsSDN.SDN_INTERNAL_CHANNEL_PROCESS:
				processInternalAdjustChannels();
				break;
			case CloudSimTagsSDN.SDN_INTERNAL_PACKET_PROCESS:
				processInternalPacketProcessing();
				break;
			case CloudSimTags.VM_DESTROY:
				processVmDestroyAck(ev);
				break;
			case CloudSimTags.VM_CREATE_ACK:
				processVmCreateAck(ev);
				break;
			case CloudSimTagsSDN.SDN_VM_CREATE_DYNAMIC_ACK:
				processVmCreateDynamicAck(ev);
				break;
			case CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION:
				System.out.println(" NOS case MONITOR_UPDATE_UTILIZATION");
				// 1. Traitement des mises à jour
				if (this.datacenter != null) {
					this.datacenter.processUpdateProcessing();
					channelManager.updatePacketProcessing();
				}

				updateSwitchMonitor(Configuration.monitoringTimeInterval);
				updateBWMonitor(Configuration.monitoringTimeInterval);
				updateHostMonitor(Configuration.monitoringTimeInterval);

				updateVmMonitor(CloudSim.clock());
				// LogMonitor.collectAllMetrics();

				// 2. Calcul du prochain intervalle
				double nextMonitorDelay = calculateNextMonitorInterval();

				// 3. Envoi conditionnel du prochain événement
				// if(shouldScheduleNextMonitor(nextMonitorDelay)) {
				// send(getId(), nextMonitorDelay, CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
				// }
				if (shouldScheduleNextMonitor(nextMonitorDelay) && !stopMonitoring) {
					send(getId(), nextMonitorDelay, CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
				}

				break;

			case CloudSimTagsSDN.STOP_MONITORING:
				System.out.println("🛑 NOS: Monitoring arrêté (fin de simulation)");
				stopMonitoring = true; // un flag que tu peux créer dans le NOS
				break;

			// System.out.println(" NOS case MONITOR_UPDATE_UTILIZATION");

			// // // Stocker les données dans le buffer au lieu d'écrire directement
			// // LogWriter linkLogger = LogWriter.getLogger("link_utilization_up.csv");
			// // linkLogger.printLine(getLinkStats()); // getLinkStats() retourne les
			// données à logger*

			// // if(CloudSimEx.hasMoreEvent(CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION)) {
			// // send(getId(), Configuration.monitoringTimeInterval,
			// // CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
			// // }

			// if(!CloudSimEx.hasMoreEvent(CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION)) {
			// send(this.getId(), Configuration.monitoringTimeInterval,
			// CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
			// }

			// if(this.datacenter != null)
			// this.datacenter.processUpdateProcessing();
			// channelManager.updatePacketProcessing();

			// this.updateBWMonitor(Configuration.monitoringTimeInterval);
			// this.updateHostMonitor(Configuration.monitoringTimeInterval);
			// this.updateSwitchMonitor(Configuration.monitoringTimeInterval);

			// // if(CloudSim.clock() >= lastMigration + Configuration.migrationTimeInterval
			// && this.datacenter != null) {
			// // sfcScaler.scaleSFC(); // Start SFC Auto Scaling

			// // this.datacenter.startMigrate(); // Start Migration

			// // lastMigration = CloudSim.clock();
			// // }
			// this.updateVmMonitor(CloudSim.clock());

			// if(CloudSimEx.hasMoreEvent(CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION)) {
			// double nextMonitorDelay = Configuration.monitoringTimeInterval;
			// double nextEventDelay = CloudSimEx.getNextEventTime() - CloudSim.clock();

			// // If there's no event between now and the next monitoring time, skip
			// monitoring until the next event time.
			// if(nextEventDelay > nextMonitorDelay) {
			// nextMonitorDelay = nextEventDelay;
			// }

			// long numPackets = channelManager.getTotalNumPackets();

			// System.err.println(CloudSim.clock() + ": Elasped time="+
			// CloudSimEx.getElapsedTimeString()+", "
			// +CloudSimEx.getNumFutureEvents()+" more events,"+" # packets="+numPackets+",
			// next monitoring in "+nextMonitorDelay);
			// send(this.getId(), nextMonitorDelay,
			// CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
			// }
			// break;
			default:
				System.out.println("Unknown event received by " + super.getName() + ". Tag:" + ev.getTag());
		}
	}

	/* MAJ Nadia */
	// public Link getDirectLink(Node src, Node dest) {
	// return topology.get(src).stream()
	// .filter(l -> l.getOtherNode(src).equals(dest))
	// .findFirst()
	// .orElse(null);
	// }
	private double calculateNextMonitorInterval() {
		double nextMonitorDelay = Configuration.monitoringTimeInterval;
		double nextEventTime = CloudSimEx.getNextEventTime();

		if (nextEventTime > 0) {
			double timeUntilNextEvent = nextEventTime - CloudSim.clock();
			if (timeUntilNextEvent > nextMonitorDelay) {
				nextMonitorDelay = timeUntilNextEvent;
			}
		}
		return nextMonitorDelay;
	}

	private boolean shouldScheduleNextMonitor(double nextInterval) {
		// Ne pas planifier si:
		// 1. Aucun événement futur
		// 2. L'intervalle est trop grand (simulation presque finie)
		// 3. Le temps de simulation maximum est atteint

		final double MAX_SIMULATION_TIME = 24 * 3600; // 24 heures en secondes
		return CloudSimEx.getNumFutureEvents() > 0 &&
				nextInterval < MAX_SIMULATION_TIME &&
				CloudSim.clock() < MAX_SIMULATION_TIME;
	}

	public boolean startDeployApplicatoin() {
		List<Vm> vms = new ArrayList<Vm>(vmMapId2Vm.values());
		// Passer une liste vide pour les politiques SFC
		System.out.println("🔍 vmMapId2Vm.size(): " + vmMapId2Vm.size());
		boolean result = deployApplication(vms, this.flowMapVmId2Flow.values(), new ArrayList<>());

		isApplicationDeployed = result;
		return result;
	}

	public boolean deployApplication(List<Vm> vms, Collection<FlowConfig> links,
			List<?> sfcPolicy) {
		// Ignorer sfcPolicy car SFC n'est pas utilisé
		System.out.println("deployApplication");

		return deployApplicationWithoutSFC(vms, links);
	}

	protected void processVmCreateAck(SimEvent ev) {
		// Default implementation
	}

	protected void processVmCreateDynamicAck(SimEvent ev) {
		// Default implementation
	}

	public boolean deployApplicationWithoutSFC(List<Vm> vms, Collection<FlowConfig> links) {
		System.out.println("deployApplicationWithoutSFC");

		if (datacenter.getVmAllocationPolicy() == null) {
			System.err.println("❌ La VM Allocation Policy est NULL !");
		} else {
			System.out.println("✅ VM Allocation Policy détectée : " + datacenter.getVmAllocationPolicy());
			System.out.println("✅ Link Allocation Policy détectée : " + datacenter.getNOS().linkSelectionPolicy);
		}

		// Étape 1 : Déployer les VMs
		// for (Vm vm : vms) {
		// boolean allocated = datacenter.getVmAllocationPolicy().allocateHostForVm(vm);
		// System.out.println("Tentative d'allocation pour VM ID=" + vm.getId() +
		// " | RAM=" + vm.getRam() +
		// " | PEs=" + vm.getNumberOfPes() +
		// " | MIPS=" + vm.getMips());

		// if (!allocated) {
		// Log.printLine("❌ Impossible d'allouer la VM : " + vm.getId());
		// System.out.println(" ➡️ VM demandé : RAM=" + vm.getRam() + ", MIPS=" +
		// vm.getMips() + ", PEs=" + vm.getNumberOfPes());
		// System.out.println(" ➡️ Vérifie que le host a assez de ressources !");
		// return false;
		// }else{
		// System.out.println("✅ VM " + vm.getId() + " allouée !");}
		// }

		System.out.println("📊 Vérification des BW après déploiement:");
		if (flowIdToBandwidthMap == null) {
			System.out.println("❗ flowIdToBandwidthMap est NULL");
		} else if (flowIdToBandwidthMap.isEmpty()) {
			System.out.println("❗ flowIdToBandwidthMap est VIDE");
		} else {
			System.out.println("✅ flowIdToBandwidthMap contient : " + flowIdToBandwidthMap.size() + " éléments");
		}

		for (Map.Entry<Integer, Long> entry : flowIdToBandwidthMap.entrySet()) {
			System.out.println("  🔹 Flow ID: " + entry.getKey() + " | BW: " + entry.getValue() + " bps");
		}

		// 1. Mapping des VMs et création des canaux pour les flux
		// for (FlowConfig flow : links) {
		// int srcId = flow.getSrcId();
		// int dstId = flow.getDstId();
		// int flowId = flow.getFlowId();

		// // Récupérer l'hôte physique de la VM source via le NOS
		// SDNHost srcHost = findHost(srcId);
		// if (srcHost == null) {
		// System.err.println("deployApplication: Aucune hôte trouvé pour la VM source "
		// + srcId);
		// return false;
		// }

		// // Construire la table de routage dynamique pour ce flux
		// boolean tableBuilt = vnMapper.buildForwardingTable(srcId, dstId, flowId);
		// if (!tableBuilt) {
		// System.err.println("deployApplication: Échec de la construction de la table
		// de routage pour le flux " + flowId);
		// return false;
		// }

		// // Créer un canal pour ce flux
		// Channel channel = channelManager.createChannel(srcId, dstId, flowId,
		// srcHost);
		// if (channel == null) {
		// System.err.println("deployApplication: Échec de la création du canal pour le
		// flux " + flowId);
		// return false;
		// }

		// // Ajouter le canal dans la table de canaux
		// channelManager.addChannel(srcId, dstId, flowId, channel);
		// }

		// // 2. (Optionnel) Mise à jour globale de la topologie physique
		// // Si besoin, on peut reconstruire les routes ou mettre à jour les tables de
		// routage.
		// // Exemple : topology.buildDefaultRouting();
		// topology.buildDefaultRouting();

		// 🔽 Log initial des VMs par hôte (1 seule fois, au moment du déploiement)
		for (Host h : this.getHostList()) {
			SDNHost host = (SDNHost) h;
			int numVms = host.getVmList().size();
			LogManager.logInitialHostVmAllocation(host.getName(), numVms);
		}

		// 3. Marquer l'application comme déployée
		isApplicationDeployed = true;
		return true;
	}

	// private boolean deployApplicationWithoutSFC(List<Vm> vms,
	// Collection<FlowConfig> links) {
	// // Étape 1 : Déployer les VMs
	// for (Vm vm : vms) {
	// Host host = findSuitableHostForVm(vm);
	// if (host != null) {
	// boolean vmAllocated =
	// datacenter.getVmAllocationPolicy().allocateHostForVm(vm, host);
	// if (!vmAllocated) {
	// Log.printLine(CloudSim.clock() + ": Échec du déploiement de la VM " +
	// vm.getId());
	// return false;
	// }
	// } else {
	// Log.printLine(CloudSim.clock() + ": Aucun hôte disponible pour la VM " +
	// vm.getId());
	// return false;
	// }
	// }

	// // Étape 2 : Configurer les flux
	// for (FlowConfig flow : links) {
	// int srcVmId = flow.getSrcId();
	// int dstVmId = flow.getDstId();
	// int flowId = flow.getFlowId();

	// SDNHost srcHost = findHost(srcVmId);
	// SDNHost dstHost = findHost(dstVmId);

	// if (srcHost != null && dstHost != null) {
	// Channel channel = channelManager.createChannel(srcVmId, dstVmId, flowId,
	// srcHost);
	// if (channel != null) {
	// channelManager.addChannel(srcVmId, dstVmId, flowId, channel);
	// } else {
	// Log.printLine(CloudSim.clock() + ": Échec de la création du canal entre VM "
	// + srcVmId + " et VM " + dstVmId);
	// return false;
	// }
	// } else {
	// Log.printLine(CloudSim.clock() + ": Hôte source ou destination introuvable
	// pour le flux " + flowId);
	// return false;
	// }
	// }

	// return true;
	// }

	public boolean isApplicationDeployed() {
		return isApplicationDeployed;
	}

	public static Vm findVmGlobal(int vmId) {
		return gvmMapId2Vm.get(vmId);
	}

	// /* MAJ Nadia */
	public double getRequestedBandwidth(Packet pkt) {
		int src = pkt.getOrigin();
		int dst = pkt.getDestination();
		int flowId = pkt.getFlowId();

		// 🔎 Rechercher un Channel actif
		Channel channel = channelManager.findChannel(src, dst, flowId);

		if (channel != null) {
			return channel.getRequestedBandwidth();
		}

		// 🔍 Si le Channel n'existe pas, utiliser la BW du flux initiale
		Long bw = flowIdToBandwidthMap.get(flowId);
		if (bw == null || bw <= 0) {
			System.err.println(" Aucun canal et aucune bande passante définie pour Flow ID: " + flowId);
			return 0;
		}

		System.out.println("🔄 Utilisation de la BW de `flowIdToBandwidthMap` pour Flow ID: " + flowId + " = " + bw);
		return bw;
	}

	// public double getRequestedBandwidth(Packet pkt) {
	// int src = pkt.getOrigin();
	// int dst = pkt.getDestination();
	// int flowId = pkt.getFlowId();
	// Channel channel = channelManager.findChannel(src, dst, flowId);

	// if (channel == null) {
	// System.err.println("⚠ Alerte : Aucun canal trouvé entre " + src + " et " +
	// dst + " pour le flow ID " + flowId);
	// return 0; // Retourne 0 si aucun canal n'est trouvé
	// }

	// return channel.getRequestedBandwidth();
	// }

	/* Nadia Ajout de log */
	public long getRequestedBandwidth(int flowId) {
		FlowConfig flow = gFlowMapFlowId2Flow.get(flowId);
		if (flow != null) {
			// Log.printLine(CloudSim.clock() + ": " + getName() + ": Flow ID " + flowId + "
			// a reqBW=" + flow.getBw());
			return flow.getBw();
		}
		// Log.printLine(CloudSim.clock() + ": " + getName() + ": Flow ID " + flowId + "
		// n'existe pas.");
		return 0;
	}

	public void processCompletePackets(List<Channel> channels) {
		for (Channel ch : channels) {
			for (Transmission tr : ch.getArrivedPackets()) {
				Packet pkt = tr.getPacket();
				int vmId = pkt.getDestination();
				Datacenter dc = SDNDatacenter.findDatacenterGlobal(vmId);

				// Log.printLine(CloudSim.clock() + ": " + getName() + ": Packet completed:
				// "+pkt +". Send to destination:"+ch.getLastNode());
				sendPacketCompleteEvent(dc, pkt, ch.getTotalLatency());
			}

			for (Transmission tr : ch.getFailedPackets()) {
				Packet pkt = tr.getPacket();
				sendPacketFailedEvent(this.datacenter, pkt, ch.getTotalLatency());
			}
		}
	}

	private void sendPacketCompleteEvent(Datacenter dc, Packet pkt, double latency) {
		send(dc.getId(), latency, CloudSimTagsSDN.SDN_PACKET_COMPLETE, pkt);
	}

	private void sendPacketFailedEvent(Datacenter dc, Packet pkt, double latency) {
		send(dc.getId(), latency, CloudSimTagsSDN.SDN_PACKET_FAILED, pkt);
	}

	public void sendAdjustAllChannelEvent() {
		if (CloudSim.clock() != lastAdjustAllChannelTime) {
			send(getId(), 0, CloudSimTagsSDN.SDN_INTERNAL_CHANNEL_PROCESS);
			lastAdjustAllChannelTime = CloudSim.clock();
		}
	}

	private void sendInternalEvent() {
		if (channelManager.getTotalChannelNum() != 0) {
			if (nextEventTime == CloudSim.clock() + CloudSim.getMinTimeBetweenEvents())
				return;

			// More to process. Send event again
			double delay = channelManager.nextFinishTime();

			if (delay < CloudSim.getMinTimeBetweenEvents()) {
				// Log.printLine(CloudSim.clock() + ":Channel: delay is too short: "+ delay);
				delay = CloudSim.getMinTimeBetweenEvents();
			}

			// Log.printLine(CloudSim.clock() + ": " + getName() + ".sendInternalEvent():
			// delay for next event="+ delay);

			if ((nextEventTime > CloudSim.clock() + delay) || nextEventTime <= CloudSim.clock()) {
				// Log.printLine(CloudSim.clock() + ": " + getName() + ".sendInternalEvent():
				// next event time changed! old="+ nextEventTime+",
				// new="+(CloudSim.clock()+delay));

				CloudSim.cancelAll(getId(), new PredicateType(CloudSimTagsSDN.SDN_INTERNAL_PACKET_PROCESS));
				send(this.getId(), delay, CloudSimTagsSDN.SDN_INTERNAL_PACKET_PROCESS);
				nextEventTime = CloudSim.clock() + delay;
			}
		}
	}

	public void updateChannelBandwidth(int src, int dst, int flowId, long newBandwidth) {
		if (channelManager.updateChannelBandwidth(src, dst, flowId, newBandwidth)) {
			// As the requested bandwidth updates, find alternative path if the current path
			// cannot provide the new bandwidth.
			SDNHost sender = findHost(src);
			vnMapper.updateDynamicForwardingTableRec(sender, src, dst, flowId, false);

			sendAdjustAllChannelEvent();
		}
	}

	private void migrateChannel(Vm vm, SDNHost oldHost, SDNHost newHost) {
		for (Channel ch : channelManager.findAllChannels(vm.getId())) {
			List<Node> nodes = new ArrayList<Node>();
			List<Link> links = new ArrayList<Link>();

			SDNHost sender = findHost(ch.getSrcId()); // After migrated

			vnMapper.buildNodesLinks(ch.getSrcId(), ch.getDstId(),
					ch.getChId(), sender, nodes, links);

			// update with the new nodes and links
			ch.updateRoute(nodes, links);
		}
	}

	public Collection<SDNHost> getAllHosts() {
		return topology.getAllHosts();
	}

	public SDNHost findHost(int vmId) {
		System.out.println("findHost by id VM ");
		Vm vm = findVmLocal(vmId);
		if (vm != null) {
			SDNHost host = (SDNHost) this.datacenter.getVmAllocationPolicy().getHost(vm);
			if (host != null) {
				System.out.println("Hôte trouvé localement: " + host);
				return host;
			} else {
				System.out.println("Aucun hôte trouvé localement pour la VM ID: " + vmId);
			}
		} else {
			System.out.println("VM non trouvée localement, recherche globale...");
		}

		// Recherche dans d'autres datacenters
		// VM is in another data center. Find the host!
		vm = findVmGlobal(vmId);
		if (vm != null) {
			Datacenter dc = SDNDatacenter.findDatacenterGlobal(vmId);
			if (dc != null) {
				SDNHost host = (SDNHost) dc.getVmAllocationPolicy().getHost(vm);
				if (host != null) {
					System.out.println("Hôte trouvé globalement: " + host);
					return host;
				} else {
					System.out.println("Aucun hôte trouvé globalement pour la VM ID: " + vmId);
				}
			} else {
				System.out.println("Datacenter non trouvé pour la VM ID: " + vmId);
			}
		} else {
			System.out.println("VM non trouvée globalement pour la VM ID: " + vmId);
		}

		return null;
	}

	public Vm findVmLocal(int vmId) {
		return vmMapId2Vm.get(vmId);
	}

	public static String getVmName(int vmId) {
		SDNVm vm = (SDNVm) gvmMapId2Vm.get(vmId);
		if (vm == null) {
			return "vm_" + vmId;
		}
		return vm.getName();
	}

	public void configurePhysicalTopology(Collection<SDNHost> hosts, Collection<Switch> switches,
			Collection<Link> links) {
		for (SDNHost sdnHost : hosts) {
			topology.addNode(sdnHost);
			this.addNode(sdnHost); // Fix: populate local nodes map
			System.out.println("Nœud hôte ajouté : " + sdnHost.getName());
		}

		for (Switch sw : switches) {
			topology.addNode(sw);
			this.addNode(sw); // Fix: populate local nodes map
			System.out.println("Nœud switch ajouté : " + sw.getName());
		}

		for (Link link : links) {
			topology.addLink(link);
			System.out.println("############# Link added: " + link.getLowOrder().getAddress() + " -> "
					+ link.getHighOrder().getAddress());
		}

		topology.buildDefaultRouting();
		System.out.println("▶ Topologie construite : " + topology.getAllLinks().size() + " liens");
	}

	// public void setLinkSelectionPolicy(LinkSelectionPolicy linkSelectionPolicy) {
	// System.out.println("############### setLinkSelectionPolicy 1");
	// vnMapper.setLinkSelectionPolicy(linkSelectionPolicy);
	// }

	public void addVm(SDNVm vm) {
		vmMapId2Vm.put(vm.getId(), vm);
		gvmMapId2Vm.put(vm.getId(), vm);
	}

	/* MAJ Nadia */
	private LinkSelectionPolicy linkSelectionPolicy;

	public void setLinkSelectionPolicy(LinkSelectionPolicy policy) {
		this.linkSelectionPolicy = policy;
	}

	public LinkSelectionPolicy getLinkSelectionPolicy() {
		return this.linkSelectionPolicy;
	}

	public void addFlow(FlowConfig flow) {
		insertFlowToMap(flow);
		if (flow.getFlowId() != -1) {
			gFlowMapFlowId2Flow.put(flow.getFlowId(), flow);
			System.out.println("Flux ajouté : ID=" + flow.getFlowId() + ", Source=" + flow.getSrcId() + ", Destination="
					+ flow.getDstId());

			Node src = getNodeById(flow.getSrcId());
			Node dst = getNodeById(flow.getDstId());

			if (src != null) {
				if (topology.getNode(src.getAddress()) == null) {
					topology.addNode(src);
				}
				this.addNode(src);
			}
			if (dst != null) {
				if (topology.getNode(dst.getAddress()) == null) {
					topology.addNode(dst);
				}
				this.addNode(dst);
			}
		}
	}

	// public void addFlow(FlowConfig flow) {
	// insertFlowToMap(flow);
	// if (flow.getFlowId() != -1) {
	// gFlowMapFlowId2Flow.put(flow.getFlowId(), flow);
	// System.out.println("Flux ajouté : ID=" + flow.getFlowId() + ", Source=" +
	// flow.getSrcId() + ", Destination=" + flow.getDstId());
	// }
	// }

	private void insertFlowToMap(FlowConfig flow) {
		flowMapVmId2Flow.put(flow.getSrcId(), flow);
		flowMapVmId2Flow.put(flow.getDstId(), flow);
	}

	// public Vm getSFForwarderOriginalVm(int vmId) {
	// return this.sfcForwarder.getOriginalSF(vmId);
	// }

	@SuppressWarnings("unchecked")
	public <T extends Host> List<T> getHostList() {
		return (List<T>) topology.getAllHosts();
	}

	public List<Switch> getSwitchList() {
		return (List<Switch>) topology.getAllSwitches();
	}

	public void setDatacenter(SDNDatacenter dc) {
		this.datacenter = dc;
	}

	protected void processVmCreate(SimEvent ev) {
		Object[] data = (Object[]) ev.getData();
		SDNVm newVm = (SDNVm) data[0];
		boolean result = (boolean) data[1];

		if (result) {
			Log.printLine(CloudSim.clock() + ": " + getName() + ".processVmCreate: Dynamic VM (" + newVm
					+ ") creation successful!");
			// Si la VM est dynamique et qu'il y avait un traitement SFC auparavant,
			// celui-ci est désormais ignoré.
		} else {
			Log.printLine(
					CloudSim.clock() + ": " + getName() + ".processVmCreate: Dynamic VM cannot be created!!: " + newVm);
			System.err.println(
					CloudSim.clock() + ": " + getName() + ".processVmCreate: Dynamic VM cannot be created!!: " + newVm);
			// Gérer ici l'erreur de création de VM dynamique (par exemple, en réessayant ou
			// en notifiant l'utilisateur)
		}
	}

	// Migrate network flow from previous routing
	public void processVmMigrate(Vm vm, SDNHost oldHost, SDNHost newHost) {
		// Find the virtual route associated with the migrated VM
		// VM is already migrated to the new host
		for (FlowConfig flow : this.flowMapVmId2Flow.get(vm.getId())) {
			SDNHost sender = findHost(flow.getSrcId()); // Sender will be the new host after migrated
			if (flow.getSrcId() == vm.getId())
				sender = oldHost; // In such case, sender should be changed to the old host

			vnMapper.rebuildForwardingTable(flow.getSrcId(), flow.getDstId(), flow.getFlowId(), sender);
		}

		// Move the transferring data packets in the old channel to the new one.
		migrateChannel(vm, oldHost, newHost);

		// Print all routing tables.
		// for(Node node:this.topology.getAllNodes()) {
		// node.printVMRoute();
		// }
	}

	public PhysicalTopology getPhysicalTopology() {
		return this.topology;
	}

	// /* MAJ Nadia */
	public Link getLink(int src, int dst) {
		System.out.println("############### getLink de nos class");
		System.out.println("Recherche d'un lien entre " + src + " et " + dst);
		for (Link link : topology.getAllLinks()) {
			if ((link.getLowOrder().getAddress() == src && link.getHighOrder().getAddress() == dst) ||
					(link.getHighOrder().getAddress() == src && link.getLowOrder().getAddress() == dst)) {
				System.out.println("Lien trouvé : " + link);
				return link;
			}
		}
		System.err.println("Aucun lien trouvé entre " + src + " et " + dst);
		return null;
	}

	protected void processInternalAdjustChannels() {
		channelManager.adjustAllChannel();
	}

	private void processInternalPacketProcessing() {
		if (channelManager.updatePacketProcessing()) {
			sendInternalEvent();
		}
	}

	protected void processVmDestroyAck(SimEvent ev) {
		Vm destroyedVm = (Vm) ev.getData();
		// remove all channels transferring data from or to this vm.
		for (Vm vm : this.vmMapId2Vm.values()) {
			channelManager.removeChannel(vm.getId(), destroyedVm.getId(), -1);
			channelManager.removeChannel(destroyedVm.getId(), vm.getId(), -1);
		}
		sendInternalEvent();
	}

	// MONITOR_UPDATE_UTILIZATION

	@Override
	public void startEntity() {

		send(this.getId(), Configuration.monitoringTimeInterval, CloudSimTagsSDN.WORKLOAD_SUBMIT);
		send(this.getId(), Configuration.monitoringTimeInterval, CloudSimTagsSDN.MONITOR_UPDATE_UTILIZATION);
	}

	@Override
	public void shutdownEntity() {

	}

	// for monitoring
	private void updateBWMonitor(double monitoringTimeUnit) {
		double highest = 0;
		// Update utilization of all links
		Set<Link> links = new HashSet<Link>(this.topology.getAllLinks());
		for (Link l : links) {
			double util = l.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
			if (util > highest)
				highest = util;
		}
		// System.err.println(CloudSim.clock()+": Highest utilization of Links =
		// "+highest);

		channelManager.updateMonitor(monitoringTimeUnit);
	}

	// private void updateHostMonitor(double monitoringTimeUnit) {
	// if(datacenter != null)
	// for(SDNHost h: datacenter.<SDNHost>getHostList()) {
	// h.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
	// }
	// }

	// Dans NetworkOperatingSystem.java

	/**
	 * Met à jour les métriques CPU/RAM/BW et logge la conso d’énergie pour chaque
	 * hôte.
	 */
	protected void updateHostMonitor(double monitoringTimeUnit) {
		double now = CloudSim.clock();
		if (datacenter != null) {
			for (SDNHost h : datacenter.<SDNHost>getHostList()) {
				// 1️⃣ Mise à jour des métriques internes (CPU, RAM, BW…)
				h.updateMonitor(now, monitoringTimeUnit);

				// 2️⃣ Calcul de la conso d’énergie instantanée
				double cpu = h.getCpuUtilization() * 100;
				double ram = h.getActualRamUtilizationFromVms() * 100;
				MonitoringValues mvBw = h.getMonitoringValuesHostBwUtilization();
				double bw = (mvBw != null)
						? mvBw.getLatestValue() * 100
						: 0.0;

				if (energyMonitor != null) {
					energyMonitor.addPowerConsumption(now, cpu, ram, bw);
				}

				// 3️⃣ Écriture dans host_energy.csv
				// LogManager.log("host_energy.csv",
				// LogManager.formatData(now, h.getId(), consumed));
			}
		}
	}

	private void updateSwitchMonitor(double monitoringTimeUnit) {
		for (Switch s : getSwitchList()) {
			s.updateMonitor(CloudSim.clock(), monitoringTimeUnit);
		}
	}

	private void updateVmMonitor(double logTime) {
		if (datacenter == null)
			return;

		// OverbookingVmAllocationPolicy monitoring removed: overbooking package
		// excluded
		// VmAllocationPolicy vmAlloc = datacenter.getVmAllocationPolicy();
		// if (vmAlloc instanceof OverbookingVmAllocationPolicy) { ... }
	}

	// protected void processVmCreateDynamicAck(SimEvent ev) {

	// Object [] data = (Object []) ev.getData();
	// SDNVm newVm = (SDNVm) data[0];
	// boolean result = (boolean) data[1];

	// if(result) {
	// Log.printLine(CloudSim.clock() + ": " + getName() + ".processVmCreateDynamic:
	// Dynamic VM("+newVm+") creation succesful!");
	// if(newVm instanceof ServiceFunction)
	// sfcForwarder.processVmCreateDyanmicAck((ServiceFunction)newVm);
	// }
	// else {
	// // VM cannot be created here..
	// Log.printLine(CloudSim.clock() + ": " + getName() + ".processVmCreateDynamic:
	// Dynamic VM cannot be created!! :"+newVm);
	// System.err.println(CloudSim.clock() + ": " + getName() +
	// ".processVmCreateDynamic: Dynamic VM cannot be created!! :"+newVm);
	// sfcForwarder.processVmCreateDyanmicFailed((ServiceFunction)newVm);
	// }
	// }

	public static Map<String, Integer> getVmNameToIdMap() {
		Map<String, Integer> map = new HashMap<>();
		for (Vm vm : gvmMapId2Vm.values()) {
			SDNVm svm = (SDNVm) vm;
			map.put(svm.getName(), svm.getId());
		}
		return map;
	}

	public static Map<String, Integer> getFlowNameToIdMap() {
		Map<String, Integer> map = new HashMap<>();
		for (FlowConfig flow : gFlowMapFlowId2Flow.values()) {
			map.put(flow.getName(), flow.getFlowId());
		}
		return map;
	}

	public static Map<Integer, Long> getFlowIdToBandwidthMap() {
		Map<Integer, Long> flowIdToBandwidthMap = new HashMap<>();
		for (Map.Entry<Integer, FlowConfig> entry : gFlowMapFlowId2Flow.entrySet()) {
			FlowConfig flow = entry.getValue();
			flowIdToBandwidthMap.put(entry.getKey(), flow.getBw());
		}
		return flowIdToBandwidthMap;
	}

}

// /*
// * Title: CloudSimSDN
// * Description: SDN extension for CloudSim
// * Licence: GPL - http://www.gnu.org/copyleft/gpl.html
// *
// * Copyright (c) 2015, The University of Melbourne, Australia
// */

// package org.cloudbus.cloudsim.sdn.nos;

// import java.util.ArrayList;
// import java.util.Collection;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.LinkedList;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;

// import org.cloudbus.cloudsim.Datacenter;
// import org.cloudbus.cloudsim.Host;
// import org.cloudbus.cloudsim.Log;
// import org.cloudbus.cloudsim.Vm;
// import org.cloudbus.cloudsim.VmAllocationPolicy;
// import org.cloudbus.cloudsim.core.CloudSim;
// import org.cloudbus.cloudsim.core.CloudSimTags;
// import org.cloudbus.cloudsim.core.SimEntity;
// import org.cloudbus.cloudsim.core.SimEvent;
// import org.cloudbus.cloudsim.core.predicates.PredicateType;
// import org.cloudbus.cloudsim.sdn.CloudSimEx;
// import org.cloudbus.cloudsim.sdn.CloudSimTagsSDN;
// import org.cloudbus.cloudsim.sdn.Configuration;
// import org.cloudbus.cloudsim.sdn.LogWriter;
// import org.cloudbus.cloudsim.sdn.Packet;
// import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
// import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
// import org.cloudbus.cloudsim.sdn.physicalcomponents.PhysicalTopology;
// import
// org.cloudbus.cloudsim.sdn.physicalcomponents.PhysicalTopologyInterCloud;
// import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
// import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
// import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
// import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;
// import
// org.cloudbus.cloudsim.sdn.policies.vmallocation.overbooking.OverbookingVmAllocationPolicy;
// import org.cloudbus.cloudsim.sdn.sfc.ServiceFunction;
// import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionAutoScaler;
// import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionChainPolicy;
// import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionForwarder;
// import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionForwarderLatencyAware;
// import org.cloudbus.cloudsim.sdn.virtualcomponents.FlowConfig;
// import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;
// import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;
// import org.cloudbus.cloudsim.sdn.virtualcomponents.VirtualNetworkMapper;
// import org.cloudbus.cloudsim.sdn.workload.Transmission;

// import com.google.common.collect.HashMultimap;
// import com.google.common.collect.Multimap;

// /**
// * NOS calculates and estimates network behaviour. It also mimics SDN
// Controller functions.
// * It manages channels between allSwitches, and assigns packages to channels
// and control their completion
// * Once the transmission is completed, forward the packet to the destination.
// *
// * @author Jungmin Son
// * @author Rodrigo N. Calheiros
// * @since CloudSimSDN 1.0
// */
// public abstract class NetworkOperatingSystem extends SimEntity {
// protected SDNDatacenter datacenter;

// // Physical topology
// protected PhysicalTopology topology;

// // Virtual topology
// protected VirtualNetworkMapper vnMapper = null;
// protected ChannelManager channelManager = null;
// protected boolean isApplicationDeployed = false;

// // Map: Vm ID -> VM
// protected HashMap<Integer, Vm> vmMapId2Vm = new HashMap<Integer, Vm>();

// // Global map (static): Vm ID -> VM
// protected static HashMap<Integer, Vm> gvmMapId2Vm = new HashMap<Integer,
// Vm>();

// // Vm ID (src or dst) -> all Flow from/to the VM
// protected Multimap<Integer, FlowConfig> flowMapVmId2Flow =
// HashMultimap.create();

// // Global map (static): Flow ID -> VM
// protected static Map<Integer, FlowConfig> gFlowMapFlowId2Flow = new
// HashMap<Integer, FlowConfig>();

// protected ServiceFunctionForwarder sfcForwarder;
// protected ServiceFunctionAutoScaler sfcScaler;

// //Resolution of the result.
// public static final long bandwidthWithinSameHost = 1500000000; // bandwidth
// between VMs within a same host: 12Gbps = 1.5GBytes/sec
// public static final double latencyWithinSameHost = 0.1; //0.1 msec latency

// private double lastMigration = 0;
// private double lastAdjustAllChannelTime = -1;
// private double nextEventTime = -1;

// /* MAJ Nadia */
// /**
// * Retourne la topologie réseau actuelle.
// * @return La topologie réseau sous forme de Map<Node, List<Link>>.
// */
// public Map<Node, List<Link>> getNetworkTopology() {
// // Convertir la topologie physique en une Map<Node, List<Link>>
// Map<Node, List<Link>> networkTopology = new HashMap<>();

// System.out.println("Construction de la networkTopology...");

// // Parcourir tous les nœuds de la topologie
// for (Node node : topology.getAllNodes()) {
// System.out.println("Traitement du nœud: " + node);
// // Récupérer les liens associés à ce nœud
// List<Link> links = new ArrayList<>();
// for (Link link : topology.getAllLinks()) {
// if (link.getLowOrder().equals(node) || link.getHighOrder().equals(node)) {
// System.out.println(" -> Lien trouvé: " + link);
// links.add(link);
// }
// }
// networkTopology.put(node, links);
// System.out.println("Nombre de liens pour le nœud " + node + " : " +
// links.size());
// }

// return networkTopology;
// }
// /**
// * 1. map VMs and middleboxes to hosts, add the new vm/mb to the vmHostTable,
// advise host, advise dc
// * 2. set channels and bws
// * 3. set routing tables to restrict hops to meet latency
// * @param sfcPolicy
// */
// protected abstract boolean deployApplication(List<Vm> vms,
// Collection<FlowConfig> links, List<ServiceFunctionChainPolicy> sfcPolicy);

// public NetworkOperatingSystem(String name) {
// super(name);

// if(Configuration.SFC_LATENCY_AWARE_ENABLE)
// this.sfcForwarder = new ServiceFunctionForwarderLatencyAware(this);
// else
// this.sfcForwarder = new ServiceFunctionForwarder(this);

// this.vnMapper = new VirtualNetworkMapper(this);
// this.channelManager = new ChannelManager(this, vnMapper, sfcForwarder);

// this.sfcScaler = new ServiceFunctionAutoScaler(this, sfcForwarder);

// this.topology = new PhysicalTopologyInterCloud();
// }

// /* MAJ Nadia */
// @Override
// protected boolean deployApplication(List<Vm> vms, Collection<FlowConfig>
// links, List<ServiceFunctionChainPolicy> sfcPolicy) {
// // Ignorer sfcPolicy car SFC n'est pas utilisé
// return deployApplicationWithoutSFC(vms, links);
// }

// private boolean deployApplicationWithoutSFC(List<Vm> vms,
// Collection<FlowConfig> links) {
// // Étape 1 : Déployer les VMs
// for (Vm vm : vms) {
// Host host = findSuitableHostForVm(vm);
// if (host != null) {
// boolean vmAllocated =
// datacenter.getVmAllocationPolicy().allocateHostForVm(vm, host);
// if (!vmAllocated) {
// Log.printLine(CloudSim.clock() + ": Échec du déploiement de la VM " +
// vm.getId());
// return false;
// }
// } else {
// Log.printLine(CloudSim.clock() + ": Aucun hôte disponible pour la VM " +
// vm.getId());
// return false;
// }
// }

// // Étape 2 : Configurer les flux
// for (FlowConfig flow : links) {
// int srcVmId = flow.getSrcId();
// int dstVmId = flow.getDstId();
// int flowId = flow.getFlowId();

// SDNHost srcHost = findHost(srcVmId);
// SDNHost dstHost = findHost(dstVmId);

// if (srcHost != null && dstHost != null) {
// Channel channel = channelManager.createChannel(srcVmId, dstVmId, flowId,
// srcHost);
// if (channel != null) {
// channelManager.addChannel(srcVmId, dstVmId, flowId, channel);
// } else {
// Log.printLine(CloudSim.clock() + ": Échec de la création du canal entre VM "
// + srcVmId + " et VM " + dstVmId);
// return false;
// }
// } else {
// Log.printLine(CloudSim.clock() + ": Hôte source ou destination introuvable
// pour le flux " + flowId);
// return false;
// }
// }

// return true;
// }

// public void setLinkSelectionPolicy(LinkSelectionPolicy linkSelectionPolicy) {
// System.out.println("############### setLinkSelectionPolicy 1");
// vnMapper.setLinkSelectionPolicy(linkSelectionPolicy);
// }

// /* MAJ Nadia */

// public double getDynamicLatency(Link link) {
// System.out.println("############### getDynamicLatency de nos class");
// if (link == null) {
// System.err.println("⚠ Aucun lien trouvé !");
// return Double.MAX_VALUE;
// }

// double latency = link.calculateDynamicLatency();
// System.out.println("🔄 Latence dynamique calculée : " + latency + " ms pour
// le lien " + link);
// return latency;
// }

// /* MAJ Nadia */
// public Link getLink(int src, int dst) {
// System.out.println("############### getLink de nos class");
// System.out.println("Recherche d'un lien entre " + src + " et " + dst);
// for (Link link : topology.getAllLinks()) {
// if ((link.getLowOrder().getAddress() == src &&
// link.getHighOrder().getAddress() == dst) ||
// (link.getHighOrder().getAddress() == src && link.getLowOrder().getAddress()
// == dst)) {
// System.out.println("Lien trouvé : " + link);
// return link;
// }
// }
// System.err.println("Aucun lien trouvé entre " + src + " et " + dst);
// return null;
// }

// // public Link getLink(int src, int dst) {
// // // Récupérer les hôtes physiques associés aux IDs de VM src et dst
// // SDNHost srcHost = this.findHost(src);
// // SDNHost dstHost = this.findHost(dst);

// // if (srcHost == null || dstHost == null) {
// // System.err.println("Impossible de trouver l'hôte physique pour src=" + src
// + " ou dst=" + dst);
// // return null;
// // }

// // // Récupérer les adresses physiques des hôtes
// // int physicalSrc = srcHost.getAddress();
// // int physicalDst = dstHost.getAddress();
// // System.out.println("getLink: physicalSrc=" + physicalSrc + ",
// physicalDst=" + physicalDst);

// // // Parcourir les liens de la topologie et comparer les adresses physiques
// // for (Link link : topology.getAllLinks()) {
// // if ((link.getLowOrder().getAddress() == physicalSrc &&
// link.getHighOrder().getAddress() == physicalDst) ||
// // (link.getHighOrder().getAddress() == physicalSrc &&
// link.getLowOrder().getAddress() == physicalDst)) {
// // return link;
// // }
// // }
// // return null; // Aucun lien trouvé
// // }

// // public Link getLink(int src, int dst) {
// // // Récupérer les hôtes physiques associés aux IDs de VM src et dst
// // SDNHost srcHost = this.findHost(src);
// // SDNHost dstHost = this.findHost(dst);

// // System.out.println("getLink");
// // for (Link link : topology.getAllLinks()) {
// // if ((link.getLowOrder().getAddress() == src &&
// link.getHighOrder().getAddress() == dst) ||
// // (link.getHighOrder().getAddress() == src &&
// link.getLowOrder().getAddress() == dst)) {
// // return link;
// // }
// // }
// // return null; // Aucun lien trouvé
// // }

// public void configurePhysicalTopology(Collection<SDNHost> hosts,
// Collection<Switch> switches, Collection<Link> links) {
// for(SDNHost sdnHost: hosts) {
// topology.addNode(sdnHost);
// }

// for(Switch sw:switches) {
// topology.addNode(sw);
// }

// for(Link link:links) {
// topology.addLink(link);
// System.out.println("############# Link added: " +
// link.getLowOrder().getAddress() + " -> " + link.getHighOrder().getAddress());
// }

// topology.buildDefaultRouting();
// }

// protected void processVmCreateAck(SimEvent ev) {
// // SDNVm vm = (SDNVm) ev.getData();
// // Host host = findHost(vm.getId());
// // vm.setSDNHost(host);
// }

// // public boolean startDeployApplicatoin() {
// // List<Vm> vms = new ArrayList<Vm>(vmMapId2Vm.values());
// // List<ServiceFunctionChainPolicy> sfcPolicies = new
// ArrayList<ServiceFunctionChainPolicy>(sfcForwarder.getAllPolicies());
// // boolean result = deployApplication(vms, this.flowMapVmId2Flow.values(),
// sfcPolicies);

// // isApplicationDeployed = result;
// // return result;
// // }
// /* MAJ Nadia */
// public boolean startDeployApplicatoin() {
// List<Vm> vms = new ArrayList<Vm>(vmMapId2Vm.values());
// // Déployer l'application sans SFC
// boolean result = deployApplication(vms, this.flowMapVmId2Flow.values());

// isApplicationDeployed = result;
// return result;
// }
// public Packet addPacketToChannel(Packet pkt) {
// int src = pkt.getOrigin();
// int dst = pkt.getDestination();
// int flowId = pkt.getFlowId();

// SDNHost srcHost = findHost(src);
// SDNHost dstHost = findHost(dst);

// if (srcHost == null || dstHost == null) {
// System.err.println("Erreur : Hôte introuvable pour le paquet " + pkt);
// return pkt;
// }

// Channel channel = channelManager.findChannel(src, dst, flowId);
// if (channel == null) {
// System.err.println("Canal introuvable entre " + src + " et " + dst + ".
// Création d'un nouveau canal.");
// channel = channelManager.createChannel(src, dst, flowId, srcHost);
// channelManager.addChannel(src, dst, flowId, channel);
// }

// channel.addTransmission(new Transmission(pkt));
// System.out.println("Paquet ajouté au canal : " + pkt);
// return pkt;
// }
// // public Packet addPacketToChannel(Packet orgPkt) {
// // Packet pkt = orgPkt;

// // if (Configuration.ENABLE_SFC) {
// // pkt = sfcForwarder.enforceSFC(pkt);
// // }

// // channelManager.updatePacketProcessing();

// // int src = pkt.getOrigin();
// // int dst = pkt.getDestination();
// // int flowId = pkt.getFlowId();

// // Channel channel = channelManager.findChannel(src, dst, flowId);
// // if (channel == null) {
// // System.err.println("❌ ERREUR : Aucun canal trouvé entre " + src + " et " +
// dst);
// // return pkt;
// // }

// // // Récupérer le lien et calculer le délai de propagation
// // Link link = getLink(src, dst);
// // if (link != null) {
// // double propagationDelay = link.calculatePropagationDelay();
// // pkt.setPropagationDelay(propagationDelay);
// // Log.printLine(CloudSim.clock() + ": " + getName() + " → Délai de
// propagation pour " + pkt + " = " + propagationDelay + "s");
// // } else {
// // Log.printLine(CloudSim.clock() + ": ⚠ Aucun lien trouvé entre " + src + "
// et " + dst);
// // }

// // channel.addTransmission(new Transmission(pkt));
// // channel.addTransmission(new Transmission(pkt, this)); // 'this' représente
// le NetworkOperatingSystem

// // sendInternalEvent();

// // return pkt;
// // }

// // public Packet addPacketToChannel(Packet orgPkt) {
// // Packet pkt = orgPkt;
// // /*
// // if(sender.equals(sender.getVMRoute(src, dst, flowId))) {
// // // For loopback packet (when src and dst is on the same host)
// // //Log.printLine(CloudSim.clock() + ": " + getName() +
// ".addPacketToChannel: Loopback package: "+pkt +". Send to destination:"+dst);
// // sendNow(sender.getAddress(),Constants.SDN_PACKAGE,pkt);
// // return;
// // }
// // */
// // if(Configuration.ENABLE_SFC)
// // pkt = sfcForwarder.enforceSFC(pkt);

// // channelManager.updatePacketProcessing();

// // int src = pkt.getOrigin();
// // int dst = pkt.getDestination();
// // int flowId = pkt.getFlowId();

// // // Check if VM is removed by auto-scaling
// // if(findVmGlobal(src) == null) {
// // src = getSFForwarderOriginalVm(src).getId();
// // pkt.changeOrigin(src);
// // }
// // if(findVmGlobal(dst) == null) {
// // dst = getSFForwarderOriginalVm(dst).getId();
// // pkt.changeDestination(dst);
// // }

// // Channel channel = channelManager.findChannel(src, dst, flowId);
// // if(channel == null) {
// // //No channel established. Create a new channel.
// // SDNHost sender = findHost(src);
// // channel = channelManager.createChannel(src, dst, flowId, sender);

// // if(channel == null) {
// // // failed to create channel
// // System.err.println("ERROR!! Cannot create channel!" + pkt);
// // return pkt;
// // }
// // channelManager.addChannel(src, dst, flowId, channel);
// // }

// // channel.addTransmission(new Transmission(pkt));
// // // Log.printLine(CloudSim.clock() + ": " + getName() +
// ".addPacketToChannel ("+channel
// // // +"): Transmission added:" +
// // // NetworkOperatingSystem.getVmName(src) + "->"+
// // // NetworkOperatingSystem.getVmName(dst) + ", flow ="+flowId + " /
// eft="+eft);

// // sendInternalEvent();

// // return pkt;
// // }

// public void processCompletePackets(List<Channel> channels){
// for(Channel ch:channels) {
// for (Transmission tr:ch.getArrivedPackets()){
// Packet pkt = tr.getPacket();
// int vmId = pkt.getDestination();
// Datacenter dc = SDNDatacenter.findDatacenterGlobal(vmId);

// //Log.printLine(CloudSim.clock() + ": " + getName() + ": Packet completed:
// "+pkt +". Send to destination:"+ch.getLastNode());
// sendPacketCompleteEvent(dc, pkt, ch.getTotalLatency());
// }

// for (Transmission tr:ch.getFailedPackets()){
// Packet pkt = tr.getPacket();
// sendPacketFailedEvent(this.datacenter, pkt, ch.getTotalLatency());
// }
// }
// }

// public void addExtraVm(SDNVm vm, NetworkOperatingSystem callback) {
// vmMapId2Vm.put(vm.getId(), vm);
// gvmMapId2Vm.put(vm.getId(), vm);

// Log.printLine(CloudSim.clock() + ": " + getName() + ": Add extra VM #" +
// vm.getId()
// + " in " + datacenter.getName() + ", (" + vm.getStartTime() + "~"
// +vm.getFinishTime() + ")");

// Object[] data = new Object[2];
// data[0] = vm;
// data[1] = callback;

// send(datacenter.getId(), vm.getStartTime(),
// CloudSimTagsSDN.SDN_VM_CREATE_DYNAMIC, data);
// }

// public void removeExtraVm(SDNVm vm) {
// vmMapId2Vm.remove(vm.getId());
// gvmMapId2Vm.remove(vm.getId());

// Log.printLine(CloudSim.clock() + ": " + getName() + ": Remove extra VM #" +
// vm.getId()
// + " in " + datacenter.getName() + ", (" + vm.getStartTime() + "~"
// +vm.getFinishTime() + ")");

// send(datacenter.getId(), vm.getStartTime(), CloudSimTags.VM_DESTROY, vm);
// }

// public void addExtraPath(int orgVmId, int newVmId) {
// List<FlowConfig> newFlowList = new ArrayList<FlowConfig>();
// // This function finds all Flows involving orgVmId and add another virtual
// path for newVmId.
// for(FlowConfig flow:this.flowMapVmId2Flow.get(orgVmId)) {
// int srcId = flow.getSrcId();
// int dstId = flow.getDstId();
// int flowId = flow.getFlowId();

// // Replace the source or destination with the new VM
// if(srcId == orgVmId)
// srcId = newVmId;
// if(dstId == orgVmId)
// dstId = newVmId;
// if(findVmGlobal(srcId) == null || findVmGlobal(dstId) == null)
// continue;

// FlowConfig extraFlow = new FlowConfig(srcId, dstId, flowId, flow.getBw(),
// flow.getLatency());
// newFlowList.add(extraFlow);

// if(vnMapper.buildForwardingTable(srcId, dstId, flowId) == false) {
// throw new RuntimeException("Cannot build a forwarding table!");
// }
// }

// for(FlowConfig flow:newFlowList)
// insertFlowToMap(flow);
// }

// public void updateVmMips(SDNVm orgVm, int newPe, double newMips) {
// Host host = orgVm.getHost();
// this.datacenter.getVmAllocationPolicy().deallocateHostForVm(orgVm);

// orgVm.updatePeMips(newPe, newMips);
// if(!this.datacenter.getVmAllocationPolicy().allocateHostForVm(orgVm, host)) {
// System.err.println("ERROR!! VM cannot be resized! "+orgVm+" (new Pe
// "+newPe+", Mips "+newMips+") in host: "+host);
// System.exit(-1);
// }
// }

// // public long getRequestedBandwidth(int flowId) {
// // FlowConfig flow = gFlowMapFlowId2Flow.get(flowId);
// // if(flow != null)
// // return flow.getBw();

// // return 0L;
// // }
// /*Nadia Ajout de log */
// public long getRequestedBandwidth(int flowId){
// FlowConfig flow = gFlowMapFlowId2Flow.get(flowId);
// if(flow != null){
// //Log.printLine(CloudSim.clock() + ": " + getName() + ": Flow ID " + flowId +
// " a reqBW=" + flow.getBw());
// return flow.getBw();
// }
// //Log.printLine(CloudSim.clock() + ": " + getName() + ": Flow ID " + flowId +
// " n'existe pas.");
// return 0;
// }

// // public double getRequestedBandwidth(Packet pkt) {
// // int src = pkt.getOrigin();
// // int dst = pkt.getDestination();
// // int flowId = pkt.getFlowId();
// // Channel channel=channelManager.findChannel(src, dst, flowId);
// // double bw = channel.getRequestedBandwidth();

// // return bw;
// // }
// /* MAJ Nadia */

// public double getRequestedBandwidth(Packet pkt) {
// int src = pkt.getOrigin();
// int dst = pkt.getDestination();
// int flowId = pkt.getFlowId();
// Channel channel = channelManager.findChannel(src, dst, flowId);

// if (channel == null) {
// System.err.println("⚠ Alerte : Aucun canal trouvé entre " + src + " et " +
// dst + " pour le flow ID " + flowId);
// return 0; // Retourne 0 si aucun canal n'est trouvé
// }

// return channel.getRequestedBandwidth();
// }

// public void updateBandwidthFlow(int srcVm, int dstVm, int flowId, long newBw)
// {
// if(flowId == -1) {
// return;
// }

// FlowConfig flow = gFlowMapFlowId2Flow.get(flowId);
// flow.updateReqiredBandwidth(newBw);
// }

// @Override
// public String toString() {
// return "NOS:"+getName();
// }

// public static Map<String, Integer> getVmNameToIdMap() {
// Map<String, Integer> map = new HashMap<>();
// for(Vm vm:gvmMapId2Vm.values()) {
// SDNVm svm = (SDNVm)vm;
// map.put(svm.getName(), svm.getId());
// }

// return map;
// }

// public static Map<String, Integer> getFlowNameToIdMap() {
// Map<String, Integer> map = new HashMap<String, Integer>();
// for(FlowConfig flow:gFlowMapFlowId2Flow.values()) {
// map.put(flow.getName(), flow.getFlowId());
// }

// /*Nadia */
// //map.put("default", -1);

// return map;
// }

// public boolean isApplicationDeployed() {
// return isApplicationDeployed;
// }

// public Vm findVmLocal(int vmId) {
// return vmMapId2Vm.get(vmId);
// }

// public static String getVmName(int vmId) {
// SDNVm vm = (SDNVm) gvmMapId2Vm.get(vmId);
// return vm.getName();
// }

// public static Vm findVmGlobal(int vmId) {
// return gvmMapId2Vm.get(vmId);
// }

// public SDNHost findHost(int vmId) {
// Vm vm = findVmLocal(vmId);
// if(vm != null) {
// // VM is in this NOS (datacenter)
// return (SDNHost)this.datacenter.getVmAllocationPolicy().getHost(vm);
// }

// // VM is in another data center. Find the host!
// vm = findVmGlobal(vmId);
// if(vm != null) {
// Datacenter dc = SDNDatacenter.findDatacenterGlobal(vmId);
// if(dc != null)
// return (SDNHost)dc.getVmAllocationPolicy().getHost(vm);
// }

// return null;
// }

// public void addVm(SDNVm vm) {
// vmMapId2Vm.put(vm.getId(), vm);
// gvmMapId2Vm.put(vm.getId(), vm);
// }

// private void insertFlowToMap(FlowConfig flow) {
// flowMapVmId2Flow.put(flow.getSrcId(), flow);
// flowMapVmId2Flow.put(flow.getDstId(), flow);
// }

// public void addFlow(FlowConfig flow) {
// insertFlowToMap(flow);

// if(flow.getFlowId() != -1) {
// gFlowMapFlowId2Flow.put(flow.getFlowId(), flow);
// }
// }

// public void addSFCPolicy(ServiceFunctionChainPolicy policy) {
// sfcForwarder.addPolicy(policy);
// List<FlowConfig> extraFlows = createExtraFlowSFCPolicy(policy);
// for(FlowConfig flow:extraFlows)
// insertFlowToMap(flow);
// }

// private List<FlowConfig> createExtraFlowSFCPolicy(ServiceFunctionChainPolicy
// policy) {
// // Add extra Flow for ServiceFunctionChain

// List<FlowConfig> flowList = new LinkedList<FlowConfig>();
// int flowId = policy.getFlowId();

// long bw = 0;
// double latency = 0.0;

// if(flowId != -1)
// {
// FlowConfig orgFlow = gFlowMapFlowId2Flow.get(flowId);
// bw = orgFlow.getBw();
// latency = orgFlow.getLatency();
// }

// List<Integer> vmIds = policy.getServiceFunctionChainIncludeVM();
// for(int i=0; i < vmIds.size()-1; i++) {
// // Build channel chain: SrcVM ---> SF1 ---> SF2 ---> DstVM
// int fromId = vmIds.get(i);
// int toId = vmIds.get(i+1);

// FlowConfig sfcFlow = new FlowConfig(fromId, toId, flowId, bw, latency);
// flowList.add(sfcFlow);
// }

// policy.setInitialBandwidth(bw);
// return flowList;
// }

// public Vm getSFForwarderOriginalVm(int vmId) {
// return this.sfcForwarder.getOriginalSF(vmId);
// }

// public double calculateLatency(int srcVmId, int dstVmId, int flowId) {
// List<Node> nodes = new ArrayList<Node>();
// List<Link> links = new ArrayList<Link>();
// Node srcHost = findHost(srcVmId);
// vnMapper.buildNodesLinks(srcVmId, dstVmId, flowId, srcHost, nodes, links);

// double latency = 0;
// // Calculate the latency of the links.
// for(Link l:links) {
// latency += l.getLatencyInSeconds();
// }

// return latency;
// }
// /* Nadia */
// public static Map<Integer, Long> getFlowIdToBandwidthMap() {
// Map<Integer, Long> flowIdToBandwidthMap = new HashMap<>();
// for (Map.Entry<Integer, FlowConfig> entry : gFlowMapFlowId2Flow.entrySet()) {
// FlowConfig flow = entry.getValue();
// flowIdToBandwidthMap.put(entry.getKey(), flow.getBw());
// }
// return flowIdToBandwidthMap;
// }

// /*
// protected void debugPrintMonitoredValues() {
// //////////////////////////////////////////////////////////////
// //////////////////////////////////////////////////////////////
// // For debug only

// Collection<Link> links = this.topology.getAllLinks();
// for(Link l:links) {
// System.err.println(l);
// MonitoringValues mv = l.getMonitoringValuesLinkUtilizationUp();
// System.err.print(mv);
// mv = l.getMonitoringValuesLinkUtilizationDown();
// System.err.print(mv);
// }
// //
// // for(Channel ch:this.allChannels) {
// // System.err.println(ch);
// // MonitoringValues mv = ch.getMonitoringValuesLinkUtilization();
// // System.err.print(mv);
// // }

// for(SDNHost h:datacenter.<SDNHost>getHostList()) {
// System.err.println(h);
// MonitoringValues mv = h.getMonitoringValuesHostCPUUtilization();
// System.err.print(mv);
// }

// for(Vm vm:vmMapId2Vm.values()) {
// SDNVm tvm = (SDNVm)vm;
// System.err.println(tvm);
// MonitoringValues mv = tvm.getMonitoringValuesVmCPUUtilization();
// System.err.print(mv);
// }
// }
// */
// }
