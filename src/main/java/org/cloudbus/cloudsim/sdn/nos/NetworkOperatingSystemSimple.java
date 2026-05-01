/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.nos;

//import java.lang.classfile.components.ClassPrinter.Node;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
// import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionChainPolicy; // removed: sfc package deleted
import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;
import org.cloudbus.cloudsim.sdn.virtualcomponents.FlowConfig;
import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;

/**
 * Simple network operating system class for the example.
 * In this example, network operating system (aka SDN controller) finds shortest
 * path
 * when deploying the application onto the cloud.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class NetworkOperatingSystemSimple extends NetworkOperatingSystem {

	public NetworkOperatingSystemSimple(String name) {
		super(name);
	}

	public NetworkOperatingSystemSimple() {
		super("NOS");
	}

	// @Override
	// protected boolean deployApplication(List<Vm> vms, Collection<FlowConfig>
	// links, List<ServiceFunctionChainPolicy> sfcPolicy) {
	// Log.printLine(CloudSim.clock() + ": " + getName() + ": Starting deploying
	// application..");

	// // Sort VMs in decending order of the required MIPS
	// Collections.sort(vms, new Comparator<Vm>() {
	// public int compare(Vm o1, Vm o2) {
	// return (int) (o2.getMips()*o2.getNumberOfPes() -
	// o1.getMips()*o1.getNumberOfPes());
	// }
	// });

	// for(Vm vm:vms)
	// {
	// SDNVm tvm = (SDNVm)vm;
	// Log.printLine(CloudSim.clock() + ": " + getName() + ": Trying to Create VM #"
	// + tvm.getId()
	// + " in " + datacenter.getName() + ", (" + tvm.getStartTime() + "~"
	// +tvm.getFinishTime() + ")");
	// send(datacenter.getId(), tvm.getStartTime(), CloudSimTags.VM_CREATE_ACK,
	// tvm);

	// if(tvm.getFinishTime() != Double.POSITIVE_INFINITY) {
	// //System.err.println("VM will be terminated at: "+tvm.getFinishTime());
	// send(datacenter.getId(), tvm.getFinishTime(), CloudSimTags.VM_DESTROY, tvm);
	// send(this.getId(), tvm.getFinishTime(), CloudSimTags.VM_DESTROY, tvm);
	// }
	// }
	// return true;
	// }

	// @Override
	// public void processVmCreateAck(SimEvent ev) {
	// super.processVmCreateAck(ev);

	// // print the created VM info
	// SDNVm vm = (SDNVm) ev.getData();
	// Log.printLine(CloudSim.clock() + ": " + getName() + ": VM Created:: " + vm +
	// " in " + vm.getHost());
	// deployFlow(this.flowMapVmId2Flow.values());
	// }

	@Override
	protected void processVmCreateAck(SimEvent ev) {
		// Implémentation minimale : log ou traitement vide
		Object data = ev.getData();
		Log.printLine(CloudSim.clock() + ": " + getName() + " - processVmCreateAck received: " + data);
		// Vous pouvez ajouter ici le traitement nécessaire si vous en avez besoin.
	}

	// private boolean deployFlow(Collection<FlowConfig> arcs) {
	// for(FlowConfig arc:arcs) {
	// /*Nadia */
	// if(arc.getFlowId() < 1){
	// System.err.println("Flow ID invalide: " + arc.getFlowId() + " pour le flux "
	// + arc.getName());
	// continue; // Ignorer ce flux ou gérer l'erreur de manière appropriée
	// }
	// vnMapper.buildForwardingTable(arc.getSrcId(), arc.getDstId(),
	// arc.getFlowId());
	// //vnMapper.buildForwardingTable(arc.getSrcId(), arc.getDstId(),
	// arc.getFlowId());
	// }

	// // Print all routing tables.
	// for(org.cloudbus.cloudsim.sdn.physicalcomponents.Node
	// node:this.topology.getAllNodes()) {
	// Log.printLine("RRRRRRRRRRRRRRRRRRRRRRRRRRRR");
	// node.printVMRoute();
	// }

	// return true;
	// }

	/* Nadia */
	private boolean deployFlow(Collection<FlowConfig> arcs) {
		for (FlowConfig arc : arcs) {
			if (arc.getFlowId() < 1) {
				// System.err.println("Flow ID invalide: " + arc.getFlowId() + " pour le flux "
				// + arc.getName());
				continue; // Ignorer ce flux ou gérer l'erreur de manière appropriée
			}
			boolean routeAdded = vnMapper.buildForwardingTable(arc.getSrcId(), arc.getDstId(), arc.getFlowId());
			if (routeAdded) {
				// Log.printLine(CloudSim.clock() + ": " + getName() + ": Flow " +
				// arc.getFlowId() + " déployé de VM " + arc.getSrcId() + " à VM " +
				// arc.getDstId());
			} else {
				Log.printLine(CloudSim.clock() + ": " + getName() + ": Échec du déploiement du Flow " + arc.getFlowId()
						+ " de VM " + arc.getSrcId() + " à VM " + arc.getDstId());
			}
		}

		// Imprimer toutes les tables de routage.
		for (org.cloudbus.cloudsim.sdn.physicalcomponents.Node node : this.topology.getAllNodes()) {
			// Log.printLine("============= Table de routage pour " + node.getName() + "
			// =============");
			node.printVMRoute();
		}

		return true;
	}

}
