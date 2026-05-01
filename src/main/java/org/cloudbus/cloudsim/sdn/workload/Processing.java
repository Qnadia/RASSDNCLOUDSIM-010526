/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.workload;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.sdn.Configuration;

/**
 * CPU Processing activity to compute in VM. Basically a wrapper of Cloudlet.
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Processing implements Activity {
	Cloudlet cl;
	double startTime = 0;
	double finishTime = 0;

	/* MAJ Nadia */
	private Request request;

	public void setRequest(Request req) {
		this.request = req;
	}

	private double vmMipsPerPE = 0; // Capacité de la VM en MIPS
	private double maxMipsForCloudlet;

	public long cloudletTotalLength;

	public Processing(Cloudlet cl) {
		this.cl = cl;
	}

	public Cloudlet getCloudlet() {
		return cl;
	}

	public Processing(long cloudletLen) {
		this.cloudletTotalLength = cloudletLen;
	}

	public void setVmMipsPerPE(double mips) {
		vmMipsPerPE = mips;

		// cl.setMaxMipsLimit(getMaxMipsForCloudlet());
	}

	public void clearCloudlet() {
		maxMipsForCloudlet = getMaxMipsForCloudlet();
		cloudletTotalLength = cl.getCloudletTotalLength();
		cl = null;
	}

	private double getMaxMipsForCloudlet() {
		double mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT;
		/*
		 * long cloudletLen = cl.getCloudletLength();
		 * 
		 * if(cloudletLen < vmMipsPerPE * 0.5) {
		 * mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT[0];
		 * }
		 * else if(cloudletLen < vmMipsPerPE * 1.0) {
		 * mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT[1];
		 * }
		 * else if(cloudletLen < vmMipsPerPE * 1.5) {
		 * mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT[2];
		 * }
		 * else {
		 * mipsPercent = Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT[3];
		 * }
		 */

		// return vmMipsPerPE * mipsPercent;
		return vmMipsPerPE;
	}

	/* MAJ Nadia */
	@Override
	public double getExpectedTime() {
		double time = Double.POSITIVE_INFINITY;
		int currentLength = 0;

		double mipsUsedLog = 0;
		if (cl != null) {
			currentLength = (int) cl.getCloudletLength();
			double mips = getMaxMipsForCloudlet();
			mipsUsedLog = mips;
			if (mips > 0) {
				time = cl.getCloudletTotalLength() / mips;
				// Stocker dans la requête
				if (this.request != null) {
					this.request.setCloudletLength(currentLength);
					this.request.setProcessingDelay(time);
				}
			}
		} else {
			double mips = (this.maxMipsForCloudlet > 0) ? this.maxMipsForCloudlet : 2000.0;
			mipsUsedLog = mips;
			time = (double) this.cloudletTotalLength / mips;
		}

		// Log pour le suivi du calcul du processing delay
		// System.out.println("[Processing] Calcul du temps d'exécution:");
		// System.out.println("Cloudlet Length: " + (cl != null ?
		// cl.getCloudletTotalLength() : cloudletTotalLength));
		// System.out.println("VM MIPS: " + mipsUsedLog);
		// System.out.println("Processing Delay: " + time + "s");

		return time;
	}

	// @Override
	// public double getExpectedTime() {
	// double time = Double.POSITIVE_INFINITY;
	// if(cl != null) {
	// double maxMipsForCloudlet = getMaxMipsForCloudlet();
	// if(maxMipsForCloudlet != 0)
	// time = cl.getCloudletTotalLength() / maxMipsForCloudlet;
	// }
	// else if(this.maxMipsForCloudlet > 0) {
	// time = this.cloudletTotalLength / this.maxMipsForCloudlet;
	// }
	// return time;
	// }

	// public double getTransmissionExpectedTime() {
	// double transmissionTime = Double.POSITIVE_INFINITY;
	// if (this.submitPktSize > 0 && getRequestedBandwidth() > 0) { // Assurez-vous
	// d'avoir la bande passante demandée
	// transmissionTime = (double) this.submitPktSize / getRequestedBandwidth();
	// }
	// return transmissionTime;
	// }

	private double getRequestedBandwidth() {
		// Implémentez la logique pour récupérer la bande passante demandée pour ce
		// workload
		// Cela peut provenir de la configuration ou de la topologie réseau
		return 1000.0; // Exemple : 1000 Mbps
	}

	@Override
	public double getServeTime() {
		// return getCloudlet().getActualCPUTime();
		return finishTime - startTime;
	}

	public String toString() {
		if (cl != null)
			return "Processing:" + "VM=" + cl.getVmId() + ",Len=" + cl.getCloudletLength();
		return "Processing:" + "Len=" + this.cloudletTotalLength + ",Start=" + this.startTime + ",Finish="
				+ this.finishTime;
	}

	@Override
	public double getStartTime() {
		return startTime;
	}

	@Override
	public double getFinishTime() {
		return finishTime;
	}

	@Override
	public void setStartTime(double currentTime) {
		startTime = currentTime;

	}

	@Override
	public void setFinishTime(double currentTime) {
		finishTime = currentTime;
	}

	@Override
	public void setFailedTime(double currentTime) {
		finishTime = currentTime;
	}
}