/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.workload;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;

/**
 * This class represents transmission of a package. It controls
 * amount of data transmitted in a shared data medium. Relation between
 * Transmission and Channel is the same as Cloudlet and CloudletScheduler,
 * but here we consider only the time shared case, representing a shared
 * channel among different simultaneous package transmissions.
 * Note that estimated transmission time is calculated in NOS.
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Transmission implements Activity {
	private Packet pkt = null;
	private long amountToBeProcessed;

	private double requestedBw = 0;
	/* MAJ Nadia */
	private NetworkOperatingSystem nos;

	// Autres champs de la classe
	private double failedTime = -1; // Ajout du champ failedTime //MAJ Nadia

	public Transmission(Packet pkt) {
		this.pkt = pkt;
		this.amountToBeProcessed = pkt.getSize();
		this.nos = nos;
	}

	public Transmission(Packet pkt, NetworkOperatingSystem nos) {
		this.pkt = pkt;
		this.amountToBeProcessed = pkt.getSize();
		this.nos = nos; // Initialisation du NOS
	}

	public Transmission(int origin, int destination, long size, int flowId, Request payload) {
		this(new Packet(origin, destination, size, flowId, payload));
	}

	public Transmission(int origin, int destination, long size, int flowId, Request payload, Packet encapsulatedPkt) {
		this(new Packet(origin, destination, size, flowId, payload, encapsulatedPkt));
	}

	public long getSize() {
		return amountToBeProcessed;
	}

	public Packet getPacket() {
		return pkt;
	}

	/**
	 * Sums some amount of data to the already transmitted data
	 * 
	 * @param completed amount of data completed since last update
	 */
	public void addCompletedLength(long completed) {
		if (completed < 0) {
			throw new IllegalArgumentException("Completed length cannot be negative.");
		}
		amountToBeProcessed -= completed;
		if (amountToBeProcessed < 0) {
			amountToBeProcessed = 0;
		}
	}

	/**
	 * Say if the Package transmission finished or not.
	 * 
	 * @return true if transmission finished; false otherwise
	 */
	public boolean isCompleted() {
		return amountToBeProcessed == 0;
	}

	public String toString() {
		return "Transmission:" + this.pkt.toString();
	}

	public void setRequestedBW(double bw) {
		this.requestedBw = bw;
	}

	public double getExpectedDuration() {
		double time = Double.POSITIVE_INFINITY;
		if (requestedBw != 0)
			time = pkt.getSize() / requestedBw;
		return time;
	}

	// @Override
	// public double getExpectedTime() {
	// // TODO Auto-generated method stub
	// return 0;
	// }

	/* MAJ NADIA */
	// Nadia : Transmission Delay
	// @Override
	// public double getExpectedTime() {
	// double time = Double.POSITIVE_INFINITY;
	// if(requestedBw > 0) {
	// double efficiency=0.9;
	// time = (double) pkt.getSize() / (efficiency * requestedBw);
	// }
	// return time;
	// }

	// @Override
	// public double getExpectedTime() {
	// double time = Double.POSITIVE_INFINITY;

	// // Vérification que la bande passante demandée est positive.
	// if (requestedBw > 0) {
	// double efficiency = 0.9; // Facteur d'efficacité du canal, représentant
	// l'utilisation réelle de la bande passante.

	// // Calcul du Transmission Delay en utilisant le facteur d'efficacité.
	// double transmissionDelay = (double) pkt.getSize() / (efficiency *
	// requestedBw);

	// // Vérifier que le NetworkOperatingSystem (nos) est défini.
	// if (nos == null) {
	// System.err.println("⚠ ERREUR: NetworkOperatingSystem (NOS) non défini dans
	// Transmission.java !");
	// // Retourne le délai de transmission uniquement en l'absence du NOS.
	// return transmissionDelay;
	// }

	// // Récupération de l'ID source à partir du paquet.
	// int srcId = pkt.getOrigin();

	// // Récupération de l'hôte source via le NOS.
	// SDNHost srcHost = (SDNHost) nos.findHost(srcId);

	// // Calcul du Processing Delay en fonction de la taille du paquet.
	// // Ce délai correspond au temps de traitement sur la VM source.
	// double processingDelay = (srcHost != null) ?
	// srcHost.calculateProcessingDelay(pkt.getSize()) : 0;

	// // La durée totale est la somme du transmissionDelay et du processingDelay.
	// time = transmissionDelay + processingDelay;
	// }

	// return time;
	// }

	@Override
	public double getExpectedTime() {
		if (requestedBw <= 0) {
			requestedBw = 1e6; // 1 Mbps fallback
		}

		double efficiency = 0.9;

		// Dtrans : temps idéal pour pousser le paquet sur le lien
		double transmissionDelay = (double) pkt.getSize() * 8 / (efficiency * requestedBw);

		// Dprop : distance physique du câble — pardonnée par le SLA (physique, pas
		// congestion)
		double propagationDelay = 0;
		if (pkt.getPayload() != null) {
			propagationDelay = pkt.getPayload().getPropagationDelay();
		}

		// ✅ processingDelay RETIRÉ : le CPU est déjà compté par
		// Processing.getExpectedTime()
		// L'inclure ici causait un double comptage → seuil SLA surévalué → violations
		// manquées

		return transmissionDelay + propagationDelay;
	}

	public double getTransmissionTime() {
		return getExpectedTime();
	}
	/* Fin */

	@Override
	public double getServeTime() {
		return getPacket().getFinishTime() - getPacket().getStartTime();
	}

	@Override
	public double getStartTime() {
		return getPacket().getStartTime();
	}

	@Override
	public double getFinishTime() {
		return getPacket().getFinishTime();
	}

	@Override
	public void setStartTime(double currentTime) {
		getPacket().setPacketStartTime(currentTime);
	}

	@Override
	public void setFinishTime(double currentTime) {
		getPacket().setPacketFinishTime(currentTime);
	}

	/* MAJ Nadia */
	@Override
	public void setFailedTime(double time) {
		// Vérifier si cette transmission a déjà été marquée comme ayant échoué
		if (this.failedTime != -1) {
			System.out.println("Transmission déjà marquée comme ayant échoué. Ignorer.");
			return; // Éviter la récursion infinie
		}

		this.failedTime = time; // Marquer cette transmission comme ayant échoué

		// Marquer le paquet comme ayant échoué (s'il existe)
		if (this.pkt != null) {
			System.out.println("Marquer le paquet comme ayant échoué.");
			this.pkt.setPacketFailedTime(time);
		}
	}
	// @Override
	// public void setFailedTime(double currentTime) {
	// getPacket().setPacketFailedTime(currentTime);
	// }

}
