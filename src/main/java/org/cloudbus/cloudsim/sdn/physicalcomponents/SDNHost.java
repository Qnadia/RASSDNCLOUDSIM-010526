/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.physicalcomponents;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.VmSchedulerTimeSharedOverSubscriptionDynamicVM;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationInterface;
import org.cloudbus.cloudsim.sdn.example.LogManager;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.parsers.VirtualTopologyParser;
import org.cloudbus.cloudsim.sdn.virtualcomponents.ForwardingRule;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;
import org.cloudbus.cloudsim.sdn.monitor.power.EnhancedHostEnergyModel;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMonitor;

/**
 * Extended class of Host to support SDN.
 * Added function includes data transmission after completion of Cloudlet
 * compute processing.
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class SDNHost extends Host implements Node {
	private ForwardingRule forwardingTable;
	private RoutingTable routingTable;
	private int rank = -1;
	// Ajoute ceci en haut de la classe, avec les autres champs :
	private final PowerUtilizationMonitor powerMonitor;

	private String name = null;

	private HashMap<Node, Link> linkToNextHop = new HashMap<Node, Link>();
	private MonitoringValues mv = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);

	public SDNHost(
			RamProvisioner ramProvisioner,
			BwProvisioner bwProvisioner,
			long storage,
			List<? extends Pe> peList,
			VmScheduler vmScheduler,
			String name) {
		super(NodeUtil.assignAddress(), ramProvisioner, bwProvisioner, storage, peList, vmScheduler);

		this.forwardingTable = new ForwardingRule();
		this.routingTable = new RoutingTable();
		this.name = name;

		/* MAJ NADIA */
		// ➊ Instancie ton modèle énergétique « Enhanced »
		EnhancedHostEnergyModel hostModel = new EnhancedHostEnergyModel(
				/* idleWatt= */ 120,
				/* wattPerCpuUtil= */ 1.54,
				/* wattPerRamUtil= */ 0.50,
				/* wattPerBwUtil= */ 0.10,
				/* powerOffDuration= */ 0);
		this.powerMonitor = new PowerUtilizationMonitor(hostModel, this.getId());

		// synchronize Host ID with VmScheduler monitor
		if (vmScheduler instanceof PowerUtilizationInterface) {
			((PowerUtilizationInterface) vmScheduler).setHost(this);
		} else {
			// Warning: VM scheduler won't track energy per-host correctly
			org.cloudbus.cloudsim.Log.printLine(
					"Warning: VmScheduler does not implement PowerUtilizationInterface for Host " + this.getId());
		}
	}

	/**
	 * Requests updating of processing of cloudlets in the VMs running in this host.
	 * 
	 * @param currentTime the current time
	 * @return expected time of completion of the next cloudlet in all VMs in this
	 *         host.
	 *         Double.MAX_VALUE if there is no future events expected in this host
	 * @pre currentTime >= 0.0
	 * @post $none
	 */
	public double updateVmsProcessing(double currentTime) {
		double smallerTime = Double.MAX_VALUE;

		// Update VM's processing for the previous time.
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			List<Double> mipsAllocated = getVmScheduler().getAllocatedMipsForVm(vm);

			// System.err.println(CloudSim.clock()+":"+vm + " is allocated: "+
			// mipsAllocated);
			vm.updateVmProcessing(currentTime, mipsAllocated);
		}

		// Change MIPS share proportion depending on the remaining Cloudlets.
		adjustMipsShare();

		// Check the next event time based on the updated MIPS share proportion
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			List<Double> mipsAllocatedAfter = getVmScheduler().getAllocatedMipsForVm(vm);
			// System.err.println(CloudSim.clock()+":"+vm + " is reallocated: "+
			// mipsAllocatedAfter);
			double time = vm.updateVmProcessing(currentTime, mipsAllocatedAfter);

			if (time > 0.0 && time < smallerTime) {
				smallerTime = time;
			}
		}

		// ➌ Monitoring removed from here, handled periodically by NOS
		// updateEnergyConsumption(currentTime);

		return smallerTime;
	}

	/**
	 * Retourne la quantité totale de MIPS demandés (en MI/s).
	 */
	public double getTotalRequestedCpuMips() {
		double total = 0;
		for (Vm vm : getVmList()) {
			total += vm.getCurrentRequestedTotalMips();
		}
		return total;
	}

	/** Idem en ratio [0..1] */
	public double getCpuUtilRatio() {
		return getTotalRequestedCpuMips() / getTotalMips();
	}

	/**
	 * Quantité de RAM libre (MB).
	 */
	public int getAvailableRam() {
		return getRamProvisioner().getAvailableRam();
	}

	/**
	 * Quantité de bande passante libre (Mbps).
	 */
	public long getAvailableBw() {
		return getBwProvisioner().getAvailableBw();
	}

	private double calculateBandwidthUtilization() {
		return Math.max(0,
				(double) (getBw() - getAvailableBandwidth()) / getBw());
	}

	/* MAJ Nadia */
	/**
	 * Calcule l'utilisation CPU actuelle du host (entre 0.0 et 1.0)
	 */
	public double getCpuUtilization() {
		double totalRequestedMips = 0.0;

		for (Vm vm : getVmList()) {
			double vmMips = vm.getCurrentRequestedTotalMips();
			totalRequestedMips += vmMips;
		}

		if (getTotalMips() == 0)
			return 0.0;

		double utilization = totalRequestedMips / getTotalMips();
		return Math.min(1.0, Math.max(0.0, utilization));
	}

	// public double getCpuUtilization() {
	// // Seuil minimal de détection (1% de charge)
	// final double MIN_UTIL_THRESHOLD = 0.01;

	// double totalRequestedMips = 0.0;
	// int activeVms = 0;

	// // for (Vm vm : getVmList()) {
	// // if (!vm.getCloudletScheduler().getCloudletExecList().isEmpty()) {
	// // double vmMips = vm.getCurrentRequestedTotalMips();
	// // if (vmMips > MIN_UTIL_THRESHOLD * vm.getMips()) {
	// // totalRequestedMips += vmMips;
	// // activeVms++;
	// // }
	// // }
	// // }

	// for (Vm vm : getVmList()) {
	// double vmMips = vm.getCurrentRequestedTotalMips(); // Inclut les cloudlets en
	// exécution et prévus
	// if (vmMips > 0) {
	// totalRequestedMips += vmMips;
	// activeVms++;
	// }
	// }

	// if (activeVms == 0) return 0.0; // Force à 0 si aucune VM active

	// double utilization = totalRequestedMips / getTotalMips();
	// return Math.min(1.0, Math.max(0.0, utilization)); // Borne entre 0 et 1
	// }
	// public double getCpuUtilization() {
	// double totalRequestedMips = 0.0;

	// for (Vm vm : getVmList()) {
	// // Vérifie si la VM exécute un cloudlet actif
	// if (!vm.getCloudletScheduler().getCloudletExecList().isEmpty()) {
	// totalRequestedMips += vm.getCurrentRequestedTotalMips();
	// }
	// }

	// double totalMips = getTotalMips(); // MIPS total du host
	// if (totalMips == 0.0) return 0.0;

	// return Math.min(1.0, totalRequestedMips / totalMips);
	// }

	// public double getCpuUtilization() {
	// double totalRequestedMips = 0.0;

	// for (Vm vm : getVmList()) {
	// totalRequestedMips += vm.getCurrentRequestedTotalMips();
	// }

	// double totalMips = getTotalMips(); // MIPS total du host
	// if (totalMips == 0.0) return 0.0;

	// return Math.min(1.0, totalRequestedMips / totalMips);
	// }

	public double getRamUtilization() {
		double usedRam = getRamProvisioner().getUsedRam();
		double totalRam = getRamProvisioner().getRam();
		if (totalRam == 0)
			return 0.0;
		return usedRam / totalRam;
	}

	/* Fin MAJ */

	public void adjustMipsShare() {
		if (getVmScheduler() instanceof VmSchedulerTimeSharedOverSubscriptionDynamicVM) {
			VmSchedulerTimeSharedOverSubscriptionDynamicVM sch = (VmSchedulerTimeSharedOverSubscriptionDynamicVM) getVmScheduler();
			double scaleFactor = sch.redistributeMipsDueToOverSubscriptionDynamic();

			logOverloadLogger(scaleFactor);
			for (SDNVm vm : this.<SDNVm>getVmList()) {
				vm.logOverloadLogger(scaleFactor);
			}
		}
	}

	/* MAJ NADIA */
	public double calculateProcessingDelay(long cloudletLen, int vmId) {

		// 1. Récupérer le datacenter de la VM
		SDNDatacenter dc = SDNBroker.vmIdToDc.get(vmId);

		SDNVm vm = (SDNVm) this.getVm(vmId);
		if (vm == null) {
			System.err.println("VM " + vmId + " NOT FOUND in Host " + getName());
			throw new IllegalStateException("VM " + vmId + " introuvable dans Host " + getName());
		}

		if (cloudletLen <= 0) {
			return 0;
		}

		double currentMips = vm.getMips();

		// 3. Récupération des MIPS depuis la topologie virtuelle (avec repli)
		if (dc != null) {
			VirtualTopologyParser parser = dc.getVirtualTopologyParser();
			if (parser != null && parser.getVmIdToMipsMap() != null) {
				Long expectedMips = parser.getVmIdToMipsMap().get(vmId);
				if (expectedMips != null && expectedMips > 0) {
					if (currentMips != expectedMips) {
						System.err.printf("[MIPS-ADJUST] Updating MIPS from %d to %d for VM %d%n",
								currentMips, expectedMips, vmId);
						vm.setMips(expectedMips);
						currentMips = expectedMips;
					}
				}
			}
		}

		// Validation finale
		if (currentMips <= 0) {
			throw new IllegalStateException(
					String.format("MIPS non configurés pour VM %d (%s)",
							vmId, vm.getName()));
		}

		// 4. Calcul du délai
		double delay = (double) cloudletLen / currentMips;
		return delay;

	}

	// public double calculateProcessingDelay(long cloudletLength) {
	// double processingPower = this.getTotalMips(); // en MI/s

	// if (processingPower <= 0) {
	// System.err.println("⚠ Erreur: Puissance de traitement invalide pour l'hôte "
	// + this.getAddress());
	// return Double.MAX_VALUE; // Éviter une division par zéro
	// }

	// // Calcul direct en secondes : cloudletLength (en MI) / processingPower (en
	// MI/s)
	// double processingDelay = (double) cloudletLength / processingPower;

	// return processingDelay;
	// }

	// Check how long this Host is overloaded (The served capacity is less than the
	// required capacity)
	private double overloadLoggerPrevTime = 0;
	private double overloadLoggerPrevScaleFactor = 1.0;
	private double overloadLoggerTotalDuration = 0;
	private double overloadLoggerOverloadedDuration = 0;
	private double overloadLoggerScaledOverloadedDuration = 0;

	private void logOverloadLogger(double scaleFactor) {
		// scaleFactor == 1 means enough resource is served
		// scaleFactor < 1 means less resource is served (only requested * scaleFactor
		// is served)
		double currentTime = CloudSim.clock();
		double duration = currentTime - overloadLoggerPrevTime;

		if (scaleFactor > 1) {
			System.err.println("scale factor cannot be >1!");
			System.exit(1);
		}

		if (duration > 0) {
			if (overloadLoggerPrevScaleFactor < 1.0) {
				// Host was overloaded for the previous time period
				overloadLoggerOverloadedDuration += duration;
			}
			overloadLoggerTotalDuration += duration;
			overloadLoggerScaledOverloadedDuration += duration * overloadLoggerPrevScaleFactor;
			updateOverloadMonitor(currentTime, overloadLoggerPrevScaleFactor);
		}
		overloadLoggerPrevTime = currentTime;
		overloadLoggerPrevScaleFactor = scaleFactor;
	}

	public double overloadLoggerGetOverloadedDuration() {
		return overloadLoggerOverloadedDuration;
	}

	public double overloadLoggerGetTotalDuration() {
		return overloadLoggerTotalDuration;
	}

	public double overloadLoggerGetScaledOverloadedDuration() {
		return overloadLoggerScaledOverloadedDuration;
	}

	public double overloadLoggerGetOverloadedDurationVM() {
		double total = 0;
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			total += vm.overloadLoggerGetOverloadedDuration();
		}
		return total;
	}

	public double overloadLoggerGetTotalDurationVM() {
		double total = 0;
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			total += vm.overloadLoggerGetTotalDuration();
		}
		return total;
	}

	public double overloadLoggerGetScaledOverloadedDurationVM() {
		double total = 0;
		for (SDNVm vm : this.<SDNVm>getVmList()) {
			total += vm.overloadLoggerGetScaledOverloadedDuration();
		}
		return total;
	}

	// For monitor
	private MonitoringValues mvOverload = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);

	private void updateOverloadMonitor(double logTime, double scaleFactor) {
		double scaleReverse = (scaleFactor != 0 ? 1 / scaleFactor : Float.POSITIVE_INFINITY);
		mvOverload.add(scaleReverse, logTime);
	}

	public MonitoringValues getMonitoringValuesOverloadMonitor() {
		return mvOverload;
	}

	public Vm getVm(int vmId) {
		for (Vm vm : getVmList()) {
			if (vm.getId() == vmId) {
				return vm;
			}
		}
		return null;
	}

	public boolean isSuitableForVm(Vm vm) {
		if (getStorage() < vm.getSize()) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by storage");
			return false;
		}

		if (!getRamProvisioner().isSuitableForVm(vm, vm.getCurrentRequestedRam())) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by RAM");
			return false;
		}

		if (!getBwProvisioner().isSuitableForVm(vm, vm.getBw())) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by BW");
			return false;
		}

		if (getVmScheduler().getPeCapacity() < vm.getCurrentRequestedMaxMips()) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by PE Capacity");
			return false;
		}

		if (getVmScheduler().getAvailableMips() < vm.getCurrentRequestedTotalMips()) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by Available MIPS");
			return false;
		}

		if (getVmScheduler().getAvailableMips() < vm.getCurrentRequestedTotalMips()) {
			Log.printLine("[VmScheduler.isSuitableForVm] Allocation of VM #" + vm.getId() + " to Host #" + getId()
					+ " failed by Available MIPS");
			return false;
		}
		return true;
	}

	/******* Routeable interface implementation methods ******/

	@Override
	public int getAddress() {
		return super.getId();
	}

	@Override
	public long getBandwidth() {
		return getBw();
	}

	public long getAvailableBandwidth() {
		return getBwProvisioner().getAvailableBw();
	}

	@Override
	public void clearVMRoutingTable() {
		this.forwardingTable.clear();
	}

	@Override
	public void addVMRoute(int src, int dest, int flowId, Node to) {
		forwardingTable.addRule(src, dest, flowId, to);
	}

	@Override
	public Node getVMRoute(int src, int dest, int flowId) {
		Node route = this.forwardingTable.getRoute(src, dest, flowId);
		if (route == null) {
			this.printVMRoute();
			System.err.println(
					toString() + " getVMRoute(): ERROR: Cannot find route:" + src + "->" + dest + ", flow =" + flowId);
		}

		return route;
	}

	@Override
	public void removeVMRoute(int src, int dest, int flowId) {
		forwardingTable.removeRule(src, dest, flowId);
	}

	@Override
	public void setRank(int rank) {
		this.rank = rank;
	}

	@Override
	public int getRank() {
		return rank;
	}

	@Override
	public void printVMRoute() {
		forwardingTable.printForwardingTable(getName());
	}

	public String toString() {
		return this.getName();
	}

	@Override
	public void addLink(Link l) {
		this.linkToNextHop.put(l.getOtherNode(this), l);
	}

	@Override
	public void updateNetworkUtilization() {
		// TODO Auto-generated method stub

	}

	@Override
	public void addRoute(Node destHost, Link to) {
		this.routingTable.addRoute(destHost, to);

	}

	@Override
	public List<Link> getRoute(Node destHost) {
		return this.routingTable.getRoute(destHost);
	}

	@Override
	public RoutingTable getRoutingTable() {
		return this.routingTable;
	}

	// For monitor
	// private MonitoringValues mv = new
	// MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
	private long monitoringProcessedMIsPerUnit = 0;

	// private PowerUtilizationMonitor powerMonitor = new
	// PowerUtilizationMonitor(new PowerUtilizationEnergyModelHostLinear());
	public double getConsumedEnergy() {
		return powerMonitor.getTotalEnergyConsumed();
	}

	// public void updateMonitor(double logTime, double timeUnit) {
	// long capacity = (long) (this.getTotalMips() * timeUnit);
	// double utilization = (double) monitoringProcessedMIsPerUnit / capacity /
	// Consts.MILLION;
	// mv.add(utilization, logTime);
	// updateMonitorBw(logTime, timeUnit);

	// System.out.printf("🧠 [HOST %s] Total MIs: %d | Processed: %d → CPU Util:
	// %.4f\n",
	// this.getName(), capacity, monitoringProcessedMIsPerUnit, utilization);

	// monitoringProcessedMIsPerUnit = 0;

	// // Utilisation RAM et BW
	// //double ramUtilization = getRamUtilization() * 100; // en %
	// double ramUtilization = getActualRamUtilizationFromVms() * 100;
	// double bwUtilization = (double) (getBw() - getAvailableBandwidth()) / getBw()
	// * 100;

	// // 💾 Log CPU, RAM, BW
	// // LogManager.log("host_utilization.csv",
	// // LogManager.formatData(logTime, getId(), utilization * 100, ramUtilization,
	// bwUtilization));

	// // 📦 Log allocation des VMs (nombre de VMs seulement)
	// // LogManager.log("host_vm_allocation.csv",
	// // LogManager.formatData(logTime, getId(), getVmList().size()));

	// // ⚡ Log énergie
	// // double energy = powerMonitor.addPowerConsumption(logTime, utilization);
	// // LogManager.log("host_energy.csv",
	// // LogManager.formatData(logTime, getId(), energy));

	// // 🔄 Mise à jour du monitoring des VMs hébergées
	// updateVmMonitor(timeUnit);
	// }
	/* MAJ NADIA */
	/**
	 * Met à jour les métriques CPU/RAM/BW et enregistre la conso d’énergie.
	 *
	 * @param logTime  Timestamp courant (CloudSim.clock()).
	 * @param timeUnit Durée écoulée depuis le dernier appel.
	 */
	public void updateMonitor(double logTime, double timeUnit) {
		// 1️⃣ Mise à jour interne CPU/RAM/BW
		double cpuUtil = getCpuUtilization(); // [0.0–1.0]

		// MAJ Nadia : On lit directement le provisioner pour la RAM
		double ramUtil = (getRamProvisioner().getRam() > 0)
				? (double) getRamProvisioner().getUsedRam() / getRamProvisioner().getRam()
				: 0.0;

		double bwUtil = ((double) (getBw() - getAvailableBandwidth()) / getBw()); // [0.0–1.0]

		mv.add(cpuUtil, logTime);

		// Si tu veux garder le logfile VM:
		updateVmMonitor(timeUnit);

		// 2️⃣ Enregistrement de la conso d’énergie
		// (ajoute aussi dans ton host_energy.csv via LogManager si besoin)
		powerMonitor.addPowerConsumption(logTime, cpuUtil * 100, ramUtil * 100, bwUtil * 100); // [0..1] -> %

		// 3️⃣ Mise à jour du monitor de BW
		updateMonitorBw(logTime, timeUnit);
	}

	// public double getActualRamUtilizationFromVms() {
	// long totalUsedRam = 0;

	// for (Vm vm : getVmList()) {
	// if (vm instanceof SDNVm) {
	// SDNVm sdnVm = (SDNVm) vm;
	// totalUsedRam += sdnVm.getUsedRam(); // 🔁 méthode à créer dans SDNVm
	// }
	// }

	// long totalRam = getRamProvisioner().getRam();
	// if (totalRam == 0) return 0.0;

	// return (double) totalUsedRam / totalRam;
	// }

	public double getActualRamUtilizationFromVms() {
		final long MIN_RAM_USAGE = 10; // 10 MB minimum pour considérer comme utilisé

		long totalUsedRam = 0;
		int activeVms = 0;

		for (Vm vm : getVmList()) {
			if (vm instanceof SDNVm) {
				SDNVm sdnVm = (SDNVm) vm;
				long vmRam = sdnVm.getUsedRam();
				if (vmRam >= MIN_RAM_USAGE) {
					totalUsedRam += vmRam;
					activeVms++;
				}
			}
		}

		if (activeVms == 0)
			return 0.0; // Force à 0 si aucune VM n'utilise de RAM

		long totalRam = getRamProvisioner().getRam();
		return totalRam > 0 ? (double) totalUsedRam / totalRam : 0.0;
	}

	private void updateVmMonitor(double timeUnit) {
		for (Vm vm : getVmList()) {
			SDNVm tvm = (SDNVm) vm;
			tvm.updateMonitor(CloudSim.clock(), timeUnit);
		}
	}

	public MonitoringValues getMonitoringValuesHostCPUUtilization() {
		return mv;
	}

	public void increaseProcessedMIs(long processedMIs) {
		// System.err.println(this.toString() +","+ processedMIs);
		this.monitoringProcessedMIsPerUnit += processedMIs;
	}

	// public MonitoringValues getMonitoringValuesHostBwUtilization() {
	// if(linkToNextHop.size() != 1) {
	// System.err.println(this+": Multiple links found!!");
	// }

	// if(linkToNextHop.size() > 0) {
	// return
	// linkToNextHop.values().iterator().next().getMonitoringValuesLinkUtilizationUp();
	// }
	// return null;
	// }

	@Override
	public Link getLinkTo(Node nextHop) {
		return this.linkToNextHop.get(nextHop);
	}

	public String getName() {
		return name;
	}

	/* MAJ Nadia */
	private long monitoringProcessedBwPerUnit = 0;
	private MonitoringValues mvBw = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);

	public void increaseProcessedBw(long bytes) {
		this.monitoringProcessedBwPerUnit += bytes;
	}

	public void updateMonitorBw(double logTime, double timeUnit) {
		long capacity = (long) (getBw() * timeUnit); // en bytes/s
		double utilization = (capacity > 0) ? (double) monitoringProcessedBwPerUnit / capacity : 0.0;

		mvBw.add(utilization, logTime);
		monitoringProcessedBwPerUnit = 0;
	}

	public MonitoringValues getMonitoringValuesHostBwUtilization() {
		return mvBw;
	}

}
