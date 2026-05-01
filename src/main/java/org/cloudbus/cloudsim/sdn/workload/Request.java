/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.workload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;

/**
 * Request class represents a message submitted to VM. Each request has a list
 * of activities
 * that should be performed at the VM. (Processing and Transmission)
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Request {

	private long requestId;
	private int userId;
	private LinkedList<Activity> activities;
	private LinkedList<Activity> removedActivites; // Logging purpose only
	private boolean finishedProcessed = false; // MAJ Nadia : flag pour éviter le sur-comptage
	/* MAJ Nadia */
	// Add this static map
	private static Map<String, Integer> vmNameToIdMap = new HashMap<>();

	// Add this method to set the mapping
	public static void setVmNameIdMapping(Map<String, Integer> mapping) {
		vmNameToIdMap = new HashMap<>(mapping);
	}

	// Add this helper method
	private int getVmId(String vmName) {
		return vmNameToIdMap.getOrDefault(vmName, -1);
	}

	private long cloudletLength;
	private double processingDelay;
	private double propagationDelay;
	private double transmissionDelay;
	private long packetSizeBytes;

	public Request(Request source) {
		this.requestId = source.requestId;
		this.userId = source.userId;
		this.cloudletLength = source.cloudletLength;
		this.lastProcessingCloudletLen = source.lastProcessingCloudletLen;
		this.processingDelay = source.processingDelay;
		this.propagationDelay = source.propagationDelay;
		this.transmissionDelay = source.transmissionDelay;
		this.switchProcessingDelay = source.switchProcessingDelay;
		this.srcHostName = source.srcHostName;
		this.dstHostName = source.dstHostName;
		this.destinationVmName = source.destinationVmName;
		this.sourceVmName = source.sourceVmName;
		this.packetSizeBytes = source.packetSizeBytes;
		this.submitTime = source.submitTime;
		this.finishTime = source.finishTime;
		this.appId = source.appId;
		this.workloadParserId = source.workloadParserId;
		this.failedTime = source.failedTime;
		this.priority = source.priority;
		this.lastProcessingVmId = source.lastProcessingVmId;
		this.processedCloudlet = source.processedCloudlet;
		this.prevActivity = source.prevActivity;

		this.activities = new LinkedList<>(source.activities);
		this.removedActivites = new LinkedList<>(source.removedActivites);
		this.executedActivities = new ArrayList<>(source.executedActivities);
		this.finishedProcessed = source.finishedProcessed;
	}

	public double getProcessingDelay() {
		return processingDelay;
	}

	public void setProcessingDelay(double d) {
		processingDelay = d;
	}

	public double getPropagationDelay() {
		return propagationDelay;
	}

	public void setPropagationDelay(double d) {
		propagationDelay = d;
	}

	public double getTransmissionDelay() {
		return transmissionDelay;
	}

	public void setTransmissionDelay(double d) {
		transmissionDelay = d;
	}

	public boolean isFinishedProcessed() {
		return finishedProcessed;
	}

	public void setFinishedProcessed(boolean finishedProcessed) {
		this.finishedProcessed = finishedProcessed;
	}

	/**
	 * MAJ Nadia : Dproc_switch = somme des latences des switches sur le chemin
	 * sélectionné (en secondes)
	 */
	private double switchProcessingDelay = 0.0;

	public double getSwitchProcessingDelay() {
		return switchProcessingDelay;
	}

	public void setSwitchProcessingDelay(double d) {
		switchProcessingDelay += d;
	} // cumulatif (plusieurs transmissions)

	/** MAJ Nadia : priorité utilisateur (depuis la colonne 10 du CSV) */
	private int priority = 0;

	public int getPriority() {
		return priority;
	}

	public void setPriority(int p) {
		this.priority = p;
	}

	public void setCloudletLength(long cloudletLength) {
		this.cloudletLength = cloudletLength;
	}

	private int workloadParserId;

	public int getWorkloadParserId() {
		return workloadParserId;
	}

	public void setWorkloadParserId(int workloadParserId) {
		this.workloadParserId = workloadParserId;
	}

	public long getLenCloudlet() {
		return cloudletLength;
	}

	private long lastProcessingCloudletLen;
	private int lastProcessingVmId;

	public long getPacketSizeBytes() {
		return packetSizeBytes;
	}

	public void setPacketSizeBytes(long packetSizeBytes) {
		this.packetSizeBytes = packetSizeBytes;
	}

	public long getLastProcessingCloudletLen() {
		return lastProcessingCloudletLen;
	}

	public void setLastProcessingCloudletLen(long lastProcessingCloudletLen) {
		this.lastProcessingCloudletLen = lastProcessingCloudletLen;
	}

	public int getLastProcessingVmId() {
		return lastProcessingVmId;
	}

	public void setLastProcessingVmId(int lastProcessingVmId) {
		this.lastProcessingVmId = lastProcessingVmId;
	}

	private Cloudlet processedCloudlet; // pour un accès direct au cloudlet si besoin

	public Cloudlet getProcessedCloudlet() {
		return processedCloudlet;
	}

	public void setProcessedCloudlet(Cloudlet processedCloudlet) {
		this.processedCloudlet = processedCloudlet;
	}

	private String destinationVmName; // Ajout du champ
	private String sourceVmName;

	private String pathString;
	private double minBwVal;
	private double avgBwUsedMbps;
	private double avgPctUseMbps;
	private List<Link> precalculatedPath = null;

	public List<Link> getPrecalculatedPath() {
		return precalculatedPath;
	}

	public void setPrecalculatedPath(List<Link> path) {
		this.precalculatedPath = path;
	}

	// Getter et setter
	public String getPathString() {
		return pathString;
	}

	public void setPathString(String pathString) {
		this.pathString = pathString;
	}

	public double getMinBwVal() {
		return minBwVal;
	}

	public void setMinBwVal(double minBwVal) {
		this.minBwVal = minBwVal;
	}

	public double getAvgBwUsedMbps() {
		return avgBwUsedMbps;
	}

	public void setAvgBwUsedMbps(double avgBwUsedMbps) {
		this.avgBwUsedMbps = avgBwUsedMbps;
	}

	public double getAvgPctUseMbps() {
		return avgPctUseMbps;
	}

	public void setAvgPctUseMbps(double avgPctUseMbps) {
		this.avgPctUseMbps = avgPctUseMbps;
	}

	public String getDestinationVmName() {
		return destinationVmName;
	}

	public void setDestinationVmName(String destinationVmName) {
		this.destinationVmName = destinationVmName;
	}

	public String getSourceVmName() {
		return sourceVmName;
	}

	public void setSourceVmName(String sourceVmName) {
		this.sourceVmName = sourceVmName;
	}

	// public int getDestinationVmIdFromPacket() {
	// // Fallback to packet destination if transmission exists
	// Transmission lastTransmission = getLastTransmission();
	// if (lastTransmission != null && lastTransmission.getPacket() != null) {
	// return lastTransmission.getPacket().getDestination();
	// }

	// // Final fallback to processing VM
	// return this.lastProcessingVmId;
	// }
	public int getDestinationVmIdFromPacket() {
		// First check if there's any transmission at all
		if (this.activities.stream().noneMatch(a -> a instanceof Transmission)) {
			// If no transmissions exist, use the processing VM as destination
			System.out.println("⚠️ No transmissions in request " + requestId +
					", using processing VM as destination");
			return this.lastProcessingVmId;
		}

		Transmission lastTransmission = getLastTransmission();
		if (lastTransmission == null) {
			System.err.println("❗ No Transmission found for Request ID: " + this.requestId);
			return this.lastProcessingVmId; // Fallback to processing VM
		}

		Packet p = lastTransmission.getPacket();
		if (p == null) {
			System.err.println("❗ Packet is null in Transmission for Request ID: " + this.requestId);
			return this.lastProcessingVmId; // Fallback to processing VM
		}

		return p.getDestination();
	}

	private double finishTime = -1;
	private double submitTime = -1;
	private int appId;

	public double getFinishTime() {
		return finishTime;
	}

	public void setFinishTime(double finishTime) {
		this.finishTime = finishTime;
	}

	public double getSubmitTime() {
		return submitTime;
	}

	public void setSubmitTime(double submitTime) {
		this.submitTime = submitTime;
	}

	public int getAppId() {
		return appId;
	}

	public void setAppId(int appId) {
		this.appId = appId;
	}

	private List<Activity> executedActivities = new ArrayList<>();

	public List<Activity> getExecutedActivities() {
		return executedActivities;
	}

	public void markActivityExecuted(Activity activity) {
		executedActivities.add(activity);
	}

	// Retourne l’ID de la VM à laquelle cette requête doit être soumise
	public int getSubmitVmId() {
		return this.lastProcessingVmId; // ou un champ dédié si tu en crées un
	}

	// Retourne l’ID du flux associé à cette requête
	public int getFlowId() {
		// Si tu n’as pas de champ flowId, utilise celui du dernier Transmission
		Transmission lastTx = getLastTransmission();
		return (lastTx != null) ? lastTx.getPacket().getFlowId() : -1;
	}

	private Activity prevActivity;

	public void setPrevActivity(Activity activity) {
		this.prevActivity = activity;
	}

	public Activity getPrevActivity() {
		return this.prevActivity;
	}

	// Fin MAJ

	// Autres champs de la classe
	private double failedTime = -1; // Ajout du champ failedTime //MAJ Nadia

	private static long numRequests = 0;

	public Request(int userId) {
		this.requestId = numRequests++;
		this.userId = userId;
		this.activities = new LinkedList<Activity>();
		this.removedActivites = new LinkedList<Activity>();
	}

	public long getRequestId() {
		return requestId;
	}

	public int getUserId() {
		return userId;
	}

	// public boolean isFinished(){
	// return (activities.size()==0);
	// }
	public boolean isFinished() {
		return (activities == null || activities.size() == 0);
	}

	public void addActivity(Activity act) {
		activities.add(act);
	}

	// public Activity getNextActivity(){
	// System.out.println("############# getNextActivity");
	// if(activities.size() > 0) {
	// Activity act = activities.get(0);
	// return act;
	// }
	// return null;
	// }
	public Activity getNextActivity() {
		System.out.println("############# getNextActivity pour Req ID: " + this.requestId);

		if (activities.isEmpty()) {
			System.out.println("⚠️ getNextActivity: Aucune activité restante pour Req ID: " + this.requestId);
			return null;
		}

		return activities.get(0);
	}

	// public Activity getPrevActivity(){
	// if(removedActivites.size() == 0)
	// return null;

	// Activity act = removedActivites.get(removedActivites.size()-1);
	// return act;
	// }

	public Transmission getNextTransmission() {
		for (Activity act : activities) {
			if (act instanceof Transmission)
				return (Transmission) act;
		}
		return null;
	}

	// public Activity removeNextActivity(){
	// Activity act = activities.remove(0);

	// this.removedActivites.add(act);

	// return act;
	// }
	public Activity removeNextActivity() {
		if (activities.isEmpty()) {
			System.err.println("❗ removeNextActivity: Aucune activité restante dans Req ID: " + this.requestId);
			return null;
		}

		Activity act = activities.remove(0);
		this.removedActivites.add(act);
		return act;
	}

	public String toString() {
		return "Request. UserID:" + this.userId + ",Req ID:" + this.requestId;
	}

	public List<Activity> getRemovedActivities() {
		return this.removedActivites;
	}

	public Transmission getLastTransmission() {
		for (int i = activities.size() - 1; i >= 0; i--) {
			Activity act = activities.get(i);
			if (act instanceof Transmission) {
				Transmission tx = (Transmission) act;
				if (tx.getPacket() != null) {
					return tx;
				}
			}
		}
		return null;
	}

	/* MAJ Nadia */
	private String srcHostName;
	private String dstHostName;

	// ... constructeurs existants ...

	// Getters et setters
	public String getSrcHostName() {
		return srcHostName;
	}

	public void setSrcHostName(String srcHostName) {
		this.srcHostName = srcHostName;
	}

	public String getDstHostName() {
		return dstHostName;
	}

	public void setDstHostName(String dstHostName) {
		this.dstHostName = dstHostName;
	}

	public Cloudlet getProcessingCloudlet() {
		for (Activity act : activities) {
			if (act instanceof Processing) {
				return ((Processing) act).getCloudlet();
			}
		}
		return null;
	}

	/* Fin MAJ */
	// public Request getTerminalRequest() {
	// // The request that processes at last.
	// Transmission t= getLastTransmission();
	// if(t == null)
	// return this;

	// Packet p = t.getPacket();
	// Request lastReq = p.getPayload();
	// return lastReq.getTerminalRequest();
	// }
	/* Nadia */
	public Request getTerminalRequest() {
		// The request that processes at last.
		Transmission t = getLastTransmission();
		if (t == null) {
			// No transmission found, this is the terminal request
			return this;
		}

		Packet p = t.getPacket();
		if (p == null) {
			// Log error and return this request as terminal
			System.err.println("Packet is null in Transmission for Request ID: " + this.requestId);
			return this;
		}

		Request lastReq = p.getPayload();
		if (lastReq == null) {
			// Log error and return this request as terminal
			System.err.println("Payload is null in Packet for Request ID: " + this.requestId);
			return this;
		}

		// If the payload refers to the current request, then return this to break the
		// cycle.
		if (lastReq == this) {
			return this;
		}

		// Otherwise, continue recursively
		return lastReq.getTerminalRequest();
	}

	/* MAJ Nadia */
	public void setFailedTime(double time) {
		// Vérifier si cette requête a déjà été marquée comme ayant échoué
		if (this.failedTime != -1) {
			return; // Éviter la récursion infinie
		}

		this.failedTime = time; // Marquer cette requête comme ayant échoué

		// Parcourir les activités et marquer leur échec
		for (Activity ac : activities) {
			ac.setFailedTime(time);
		}
	}
	// public void setFailedTime(double time) {
	// for(Activity ac:activities) {
	// ac.setFailedTime(time);
	// }
	// }

	/**
	 * Returns the list of activities in the request.
	 *
	 * @return The list of activities.
	 * 
	 *         Nadia QOUDHADH
	 */
	public List<Activity> getActivities() {
		return this.activities;
	}

	public double getTotalLength() {
		double totalLength = 0.0;
		for (Activity activity : activities) {
			if (activity instanceof Processing) {
				totalLength += ((Processing) activity).getExpectedTime();
			} else if (activity instanceof Transmission) {
				totalLength += ((Transmission) activity).getTransmissionTime();
			}
		}
		return totalLength;
	}

	public long getCloudletLength() {
		return lastProcessingCloudletLen;
	}

}
