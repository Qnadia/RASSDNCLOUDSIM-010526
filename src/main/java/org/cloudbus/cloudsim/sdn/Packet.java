/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.workload.Request;

/**
 * Network data packet to transfer from source to destination.
 * Payload of Packet will have a list of activities. 
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Packet {
	private static long automaticPacketId = 0;
	private final long id;
	private int origin;			// origin VM adress (vm.getId())
	private int destination;	// destination VM adress (vm.getId())
	private final long size;
	private final int flowId;
	private Request payload;

	private double startTime=-1;
	private double finishTime=-1;

	private Packet pktEncapsulated = null;
	
	public Packet(int origin, int destination, long size, int flowId, Request payload) {
		this.origin = origin;
		this.destination = destination;
		this.size = size;
		this.flowId = flowId;
		this.payload = payload;
		this.id = automaticPacketId++;
	
		if (size < 0) {
			throw new RuntimeException("Packet size cannot be minus! Pkt=" + this + ", size=" + size);
		}
	
		// Log pour vérifier l'initialisation du payload
		if (payload == null) {
			System.err.println("⚠ ERREUR : Payload est null dans le constructeur de Packet !");
		} else {
			System.out.println("Packet créé avec Payload : " + payload);
		}
	}
	
	public Packet(int origin, int destination, long size, int flowId, Request payload, Packet encapsulatedPkt) { 
		this(origin, destination, size, flowId, payload);
		this.pktEncapsulated = encapsulatedPkt; 
	}

	/* MAJ Nadia  */
	private double propagationDelay = 0.0; // Stocke le délai de propagation

	public void setPropagationDelay(double delay) {
		if (delay < 0) {
			System.err.println("⚠ ERREUR : Délai de propagation négatif détecté !");
			return;
		}
		this.propagationDelay = delay;
	}

	public double getPropagationDelay() {
		return this.propagationDelay;
	}
	/* Fin MAJ  */

	
	public int getOrigin() {
		return origin;
	}



	public SDNHost getOriginHost(NetworkOperatingSystem nos) {
		// Vérifier si le NOS (Network Operating System) est null
		if (nos == null) {
			System.err.println("⚠ ERREUR : NetworkOperatingSystem est null dans Packet !");
			return null;
		}

		// Utilisation de nos.findHost() pour récupérer l'hôte source
		SDNHost srcHost = (SDNHost) nos.findHost(this.origin);

		if (srcHost == null) {
			System.err.println("⚠ ERREUR : Impossible de trouver l'hôte source pour le paquet " + this);
		}

		return srcHost;
	}

	
	public void changeOrigin(int vmId) {
		origin = vmId;
	}

	public int getDestination() {
		return destination;
	}

	public void changeDestination(int vmId) {
		destination = vmId;
	}
	
	public long getSize() {
		return size;
	}

	public Request getPayload() {
		return payload;
	}
	
	public int getFlowId() {
		return flowId;
	}
	
	@Override
	public String toString() {
		String payloadStr = (payload != null) ? payload.toString() : "No payload";
		return "PKG:" + origin + "->" + destination + " - " + payloadStr;
	}

	
	@Override
	public int hashCode() {
		return Long.hashCode(id);
	}

	public void setPacketStartTime(double time) {
		this.startTime = time;
		
		if(pktEncapsulated != null && pktEncapsulated.getStartTime() == -1) {
			pktEncapsulated.setPacketStartTime(time);
		}
	}
	
	public void setPacketFinishTime(double time) {
		this.finishTime = time;
		
		if(pktEncapsulated != null) {
			pktEncapsulated.setPacketFinishTime(time);
		}
	}
	
	/** MAJ Nadia  */
	public void setPacketFailedTime(double currentTime) {
		// Vérifier si ce paquet a déjà été marqué comme ayant échoué
		if (this.finishTime != -1) {
			return; // Éviter la récursion infinie
		}
	
		setPacketFinishTime(currentTime); // Marquer ce paquet comme ayant échoué
	
		// Marquer le payload comme ayant échoué (s'il existe)
		if (this.payload != null) {
			this.payload.setFailedTime(currentTime);
		}
	
		// Marquer le paquet encapsulé comme ayant échoué (s'il existe)
		if (pktEncapsulated != null) {
			pktEncapsulated.setPacketFailedTime(currentTime);
		}
	}
	// public void setPacketFailedTime(double currentTime) {
	// 	setPacketFinishTime(currentTime);
	// 	getPayload().setFailedTime(currentTime);
	// 	if(pktEncapsulated != null) {
	// 		pktEncapsulated.setPacketFailedTime(currentTime);
	// 	}
	// }
	
	public double getStartTime() {
		//if(pktEncapsulated != null) {
		//	return pktEncapsulated.getStartTime();
		//}
		
		return this.startTime;
	}
	
	public double getFinishTime() {
		//if(pktEncapsulated != null) {
		//	return pktEncapsulated.getFinishTime();
		//}
		
		return this.finishTime;
	}
	
	public long getPacketId() {
		return this.id;
	}
}
