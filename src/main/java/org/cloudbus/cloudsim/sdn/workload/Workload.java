/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.workload;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.sdn.parsers.WorkloadParser;

/**
 * Class to keep workload information parsed from files.
 * This class is used in WorkloadParser
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class Workload implements Comparable<Workload> {
	public int workloadId;
	public int appId;
	public int submitVmId;
	public int submitPktSize;
	public Request request;
	private String status;

	/* Nadia */
	public double time;
	/**
	 * MAJ Nadia : Priorité définie par l'utilisateur (plus grand = plus
	 * prioritaire, défaut = 0)
	 */
	public int priority = 0;

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	/** Temps de soumission (en secondes) */
	public void setTime(double t) {
		this.time = t;
	}

	public double getTime() {
		return this.time;
	}

	public Request getRequest() {
		return this.request;
	}

	// Getter et Setter pour status
	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	private List<Activity> activities; // Liste des activités associées au workload

	public WorkloadResultWriter resultWriter;
	private WorkloadParser parser;

	public boolean failed = false;

	public Workload(int workloadId, WorkloadResultWriter writer) {
		this.workloadId = workloadId;
		this.resultWriter = writer;
		this.activities = new ArrayList<Activity>();
	}

	public Workload(long workloadId, int appId, double time, Request request, boolean failed) {
		this.workloadId = (int) workloadId; // ou cast long → int, sinon change la classe Workload
		this.appId = appId;
		this.time = time;
		this.request = request;
		this.failed = failed;

		this.activities = new ArrayList<Activity>(); // Initialise au cas où
	}

	public void writeResult() {
		System.out.println("############### writeResult");
		if (resultWriter != null) {
			resultWriter.writeResult(this);
		} else {
			System.out.println("⚠️ [Workload] resultWriter est null pour workloadId: " + this.workloadId);
		}

		// ✅ On doit ajouter le workload à completedWorkloads !
		if (parser != null) {
			if (!parser.getCompletedWorkloads().contains(this)) {
				parser.addCompletedWorkload(this); // <- Important pour la remontée des stats
			} else {
				System.out.println("🔵 Workload " + workloadId + " déjà ajouté !");
			}
		} else {
			System.out.println("⚠️ [Workload] parser est null pour workloadId: " + workloadId);
		}
	}

	@Override
	public int compareTo(Workload that) {
		return this.workloadId - that.workloadId;
	}

	@Override
	public String toString() {
		return "Workload (ID:" + workloadId + "/" + appId + ", time:" + time + ", VM:" + submitVmId;
	}

	/* Nadia */
	public int getAppId() {
		return this.appId;
	}

	public int getWorkloadId() {
		return this.workloadId;
	}

	/**
	 * Ajoute une activité au workload.
	 * 
	 * @param activity L'activité à ajouter.
	 */
	public void addActivity(Activity activity) {
		activities.add(activity);
	}

	/**
	 * Retourne la liste des activités.
	 * 
	 * @return La liste des activités.
	 */
	public List<Activity> getActivities() {
		return activities;
	}

	/**
	 * Calcule le temps total attendu du workload en additionnant les temps de
	 * toutes les activités.
	 * 
	 * @return Le temps total attendu.
	 */
	public double getTotalExpectedTime() {
		double totalTime = 0.0;
		for (Activity activity : activities) {
			totalTime += activity.getExpectedTime();
		}
		return totalTime;
	}

	public void setParser(WorkloadParser parser) {
		this.parser = parser;
	}

	public WorkloadParser getParser() {
		return this.parser;
	}

	// Getter pour submitVmId
	public int getSubmitVmId() {
		return this.submitVmId;
	}

	// Setter pour submitVmId
	public void setSubmitVmId(int submitVmId) {
		this.submitVmId = submitVmId;
	}

	public long getCloudletLength() {
		if (this.request != null)
			return this.request.getCloudletLength();
		return 0;
	}

}
