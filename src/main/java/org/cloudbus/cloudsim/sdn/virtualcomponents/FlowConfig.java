/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.virtualcomponents;

/**
 * Traffic requirements between two VMs
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class FlowConfig {

	private int srcId;
	private int dstId;
	private int flowId;
	private long requiredBandwidth; // default: 0
	private double requiredLatency;
	/*MAJ Nadia  */
	// Dans FlowConfig, après private double requiredLatency;
	private double distance = 0.0;
	private double refractiveIndex = 1.0;

	// Mets à jour ton constructeur pour accepter distance et refractiveIndex
	public FlowConfig(int srcId, int dstId, int flowId, long reqBW, double reqLatency, double distance, double refractiveIndex) {
		this(srcId, dstId, flowId, reqBW, reqLatency);
		this.distance = distance;
		this.refractiveIndex = refractiveIndex;
	}

	// Nouveaux getters
	public double getDistance() {
		return distance;
	}

	public double getRefractiveIndex() {
		return refractiveIndex;
	}

	
	private String name=null;
	
	public FlowConfig(int srcId, int dstId, int flowId, long reqBW, double reqLatency) {
		super();
		this.srcId = srcId;
		this.dstId = dstId;
		this.flowId = flowId;
		this.requiredBandwidth = reqBW;
		this.requiredLatency = reqLatency;

		System.out.println("Création de FlowConfig: Flow ID = " + flowId + ", Source = " + srcId + ", Destination = " + dstId);
    
	}
	
	public void updateReqiredBandwidth(long bw) {
		this.requiredBandwidth = bw;
	}

	public int getSrcId() {
		return srcId;
	}

	public int getDstId() {
		return dstId;
	}
	public int getFlowId() {
		return flowId;
	}

	public long getBw() {
		return requiredBandwidth;
	}

	public double getLatency() {
		return requiredLatency;
	}
	
	public void setName(String flowName) {
		this.name = flowName;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String toString() {
		return "Arc:"+getName()+"... "+getSrcId()+" -> "+getDstId()+" : "+getFlowId();
	}
}
