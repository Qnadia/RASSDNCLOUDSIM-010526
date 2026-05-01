/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.monitor.power;

import java.util.Locale;

import org.cloudbus.cloudsim.sdn.example.LogManager;

public class PowerUtilizationMonitor {
	private double previousTime = 0;
	private double totalEnergy = 0;
	private int entityId;
	/* MAJ Nadia */
	private final PowerUtilizationEnergyModel model;

	public PowerUtilizationMonitor(PowerUtilizationEnergyModel model, int entityId) {
		this.model = model;
		this.entityId = entityId; // Initialisez entityIdId
	}

	public void setEntityId(int id) {
		this.entityId = id;
	}

	public boolean hasValidHost() {
		return entityId != -1;
	}

	public PowerUtilizationMonitor(PowerUtilizationEnergyModel model) {
		this(model, -1);
	}

	/**
	 * Calcule et retourne l'énergie consommée depuis le dernier appel,
	 * puis enregistre un log détaillé.
	 */
	public double addPowerConsumption(double currentTime,
			double cpuUtil,
			double ramUtil,
			double bwUtil) {
		double duration = currentTime - previousTime;
		if (duration <= 0)
			return 0.0; // Évite les calculs incorrects
		double consumed = ((EnhancedHostEnergyModel) model).calculateEnergyConsumption(duration, cpuUtil, ramUtil,
				bwUtil);
		totalEnergy += consumed;
		previousTime = currentTime;

		// Log détaillé
		String state;
		if (consumed == 0)
			state = "OFF";
		else if (cpuUtil == 0 && ramUtil == 0 && bwUtil == 0)
			state = "IDLE";
		else
			state = "ACTIVE";

		System.out.printf("[t=%.1f] Host %d %s: CPU=%.1f%% RAM=%.1f%% BW=%.1f%% → %.5f Wh%n",
				currentTime, entityId, state, cpuUtil, ramUtil, bwUtil, consumed);

		String line = String.format(Locale.US,
				"%.2f;%d;%.2f;%.2f;%.2f;%.4f",
				currentTime, entityId, cpuUtil, ramUtil, bwUtil, consumed);
		LogManager.log("detailed_energy.csv", line);

		return consumed;
	}

	/* Fin MAJ */

	// private PowerUtilizationEnergyModel energyModel;

	// public PowerUtilizationMonitor(PowerUtilizationEnergyModel model) {
	// this.energyModel = model;
	// }

	public double addPowerConsumption(double currentTime, double cpuUtilizationOfLastPeriod) {

		double duration = currentTime - previousTime;
		double energyConsumption = model.calculateEnergyConsumption(duration, cpuUtilizationOfLastPeriod);

		totalEnergy += energyConsumption;
		previousTime = currentTime;

		System.out.printf("[t=%.1f] Node %d : Util=%.1f → %.5f Wh%n", currentTime, entityId, cpuUtilizationOfLastPeriod,
				energyConsumption);
		return energyConsumption;
	}

	public void addPowerConsumptionDuration(double duration, double cpuUtilizationOfLastPeriod) {
		double energyConsumption = model.calculateEnergyConsumption(duration, cpuUtilizationOfLastPeriod);

		totalEnergy += energyConsumption;
	}

	public double getTotalEnergyConsumed() {
		return totalEnergy;
	}
}
