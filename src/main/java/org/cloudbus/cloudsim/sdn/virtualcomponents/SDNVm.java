/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.virtualcomponents;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.CloudletSchedulerMonitor;
import org.cloudbus.cloudsim.sdn.LogWriter;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.workload.Workload;

/**
 * Extension of VM that supports to set start and terminate time of VM in VM
 * creation request.
 * If start time and finish time is set up, specific CloudSim Event is triggered
 * in datacenter to create and terminate the VM.
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class SDNVm extends Vm {
	// private SDNHost sdnhost = null;
	private double startTime;
	private double finishTime;
	private String vmName = null;
	private String middleboxType = null;
	private String hostName = null;

	/* MAJ Nadia */
	private MonitoringValues mvRam = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
	private long monitoringUsedRamPerUnit = 0;

	/* Fin MAJ */

	private List<String> subDatacenters = new ArrayList<String>();

	private static int assignedVmId = 0;

	public static int getUniqueVmId() {
		int id = SDNVm.assignedVmId;
		SDNVm.assignedVmId++;
		return id;
	}

	public static void reset() {
		assignedVmId = 0;
		System.out.println("🔄 [SDNVm] Global state reset completed.");
	}

	public SDNVm(int id, int userId, double mips, int numberOfPes, int ram,
			long bw, long size, String vmm, CloudletScheduler cloudletScheduler) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
	}

	public SDNVm(int id, int userId, double mips, int numberOfPes, int ram,
			long bw, long size, String vmm, CloudletScheduler cloudletScheduler, double startTime, double finishTime) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);

		this.startTime = startTime;
		this.finishTime = finishTime;
	}

	public double getStartTime() {
		return startTime;
	}

	public double getFinishTime() {
		return finishTime;
	}

	// public void setSDNHost(SDNHost host) {
	// sdnhost = host;
	// }

	@Override
	public void setMips(double mips) {
		super.setMips(mips); // Rend la méthode visible
	}

	public void setName(String name) {
		this.vmName = name;
	}

	public String getName() {
		return this.vmName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName; // optional setting if this VM has to be placed in a specific host
	}

	public String getHostName() {
		return this.hostName;
	}

	public void setMiddleboxType(String mbType) {
		this.middleboxType = mbType;
	}

	public String getMiddleboxType() {
		return this.middleboxType;
	}

	public String toString() {
		return "VM #" + getId() + " (" + getName() + ") in (" + getHost() + ")";
	}

	@Override
	public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
		System.out.println("🌀 -----CloudletScheduler: updateVmProcessing called at time: " + currentTime + " | MIPS = "
				+ mipsShare);

		double sumMips = 0;
		for (double mips : mipsShare) {
			sumMips += mips;
			getCloudletScheduler().getCloudletExecList().forEach(cl -> {
				double util = cl.getCloudlet().getUtilizationModelCpu().getUtilization(currentTime);
				Log.printLine(String.format(
						"⚙️ [VM %-3s] Cloudlet %-3d — CPU Utilization @%.2f = %.2f%%",
						this.getName(), cl.getCloudletId(), currentTime, util * 100));

				System.out.printf("⚙️ [---- VM %s] Cloudlet %d - CPU Utilisation à t=%.2f = %.2f%% (from UtilModel)\n",
						this.getName(), cl.getCloudletId(), currentTime, util * 100);
			});
		}

		if (getCloudletScheduler() instanceof CloudletSchedulerMonitor) {
			CloudletSchedulerMonitor cls = (CloudletSchedulerMonitor) getCloudletScheduler();

			long totalGivenPrevTime = (long) (cls.getTimeSpentPreviousMonitoredTime(currentTime) * sumMips);
			long totalProcessingPrevTime = cls.getTotalProcessingPreviousTime(currentTime, mipsShare);

			// Monitoring CPU
			this.increaseProcessedMIs(totalProcessingPrevTime, totalGivenPrevTime);

			if (totalProcessingPrevTime > 0) {
				System.out.printf("💻 [PROGRESS] [VM %s] MIs exécutés: %d / %d (Total intervalle: %d)\n",
						this.getName(), totalProcessingPrevTime, totalGivenPrevTime, monitoringProcessedMIsPerUnit);
			}

			// ✅ Monitoring RAM
			this.monitoringUsedRamPerUnit = this.getCurrentRequestedRam();

			// Monitoring the host hosting this VM
			SDNHost sdnhost = (SDNHost) getHost();
			if (sdnhost != null)
				sdnhost.increaseProcessedMIs(totalProcessingPrevTime);
		}

		return super.updateVmProcessing(currentTime, mipsShare);
	}

	public boolean isIdle() {
		CloudletSchedulerMonitor sch = (CloudletSchedulerMonitor) getCloudletScheduler();
		return sch.isVmIdle();
	}

	public long getTotalMips() {
		return (long) (this.getMips() * this.getNumberOfPes());
	}

	@Override
	public long getCurrentRequestedBw() {
		return getBw();
	}

	@Override
	public List<Double> getCurrentRequestedMips() {
		List<Double> currentRequestedMips = new ArrayList<Double>();
		for (int i = 0; i < getNumberOfPes(); i++) {
			currentRequestedMips.add(getMips());
		}
		return currentRequestedMips;
	}

	// For monitor
	private MonitoringValues mvCPU = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
	private long monitoringProcessedMIsPerUnit = 0;

	private long monitoringGivenMIsPerUnit = 0;

	public void updateMonitor(double logTime, double timeUnit) {
		updateMonitorCPU(logTime, timeUnit);
		updateMonitorBW(logTime, timeUnit);
		updateMonitorRam(logTime);

	}

	private void updateMonitorCPU(double logTime, double timeUnit) {
		// long capacity = (long) (getTotalMips() *timeUnit);
		long capacity = monitoringGivenMIsPerUnit;

		double utilization = 0;

		if (capacity != 0)
			utilization = (double) monitoringProcessedMIsPerUnit / capacity / Consts.MILLION;

		// Option pour supprimer le log lourd des MIs ou le réduire. On divise par Consts.MILLION :
		System.out.printf("💻 [VM %s] MIs exécutés: %d / MIs donnés: %d → CPU Util: %.4f\n",
				this.getName(), (monitoringProcessedMIsPerUnit / 1000000), capacity, utilization);

		mvCPU.add(utilization, logTime);
		monitoringProcessedMIsPerUnit = 0;
		monitoringGivenMIsPerUnit = 0;

	}

	public MonitoringValues getMonitoringValuesVmCPUUtilization() {
		return mvCPU;
	}

	public double getMonitoredUtilizationCPU(double startTime, double endTime) {
		return getMonitoringValuesVmCPUUtilization().getAverageValue(startTime, endTime);
	}

	public void increaseProcessedMIs(long processedMIs, long totalGivenMIs) {
		this.monitoringProcessedMIsPerUnit += processedMIs;
		this.monitoringGivenMIsPerUnit += totalGivenMIs;
	}

	private MonitoringValues mvBW = new MonitoringValues(MonitoringValues.ValueType.DataRate_BytesPerSecond);
	private long monitoringProcessedBytesPerUnit = 0;

	private void updateMonitorBW(double logTime, double timeUnit) {
		// long capacity = (long) (getBw() *timeUnit);
		double dataRate = (double) monitoringProcessedBytesPerUnit / timeUnit;

		// if(capacity != 0 )
		// utilization = (double)monitoringProcessedBytesPerUnit / capacity;

		mvBW.add(dataRate, logTime);
		monitoringProcessedBytesPerUnit = 0;

		// LogWriter log = LogWriter.getLogger("vm_bw_utilization.csv");
		// log.printLine(this.getName()+","+logTime+","+dataRate);
	}

	public MonitoringValues getMonitoringValuesVmBwUtilization() {
		return mvBW;
	}

	public void increaseProcessedBytes(long processedThisRound) {
		this.monitoringProcessedBytesPerUnit += processedThisRound;
	}

	private ArrayList<SDNHost> migrationHistory = new ArrayList<SDNHost>(); // migration history for debugging

	public void addMigrationHistory(SDNHost host) {
		migrationHistory.add(host);
	}

	// Check how long this Host is overloaded (The served capacity is less than the
	// required capacity)
	private double overloadLoggerPrevTime = 0;
	private double overloadLoggerPrevScaleFactor = 1.0;
	private double overloadLoggerTotalDuration = 0;
	private double overloadLoggerOverloadedDuration = 0;
	private double overloadLoggerScaledOverloadedDuration = 0;

	public void logOverloadLogger(double scaleFactor) {
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
	//////////////////////////////////////////////////////

	// For vertical scaling
	public void updatePeMips(int pe, double mips) {
		// This function changes MIPS of this VM. Proper VM scheduling must be followed
		// to change Host settings.
		super.setNumberOfPes(pe);
		super.setMips(mips);
	}

	public void setOptionalDatacenters(List<String> subDatacenters) {
		this.subDatacenters = subDatacenters;
	}

	public List<String> getOptionalDatacenters() {
		return this.subDatacenters;
	}

	/* MAJ Nadia */
	public void increaseUsedRam(long usedRam) {
		this.monitoringUsedRamPerUnit = usedRam;
	}

	public long getUsedRam() {
		return monitoringUsedRamPerUnit;
	}

	private void updateMonitorRam(double logTime) {
		long ramCapacity = getRam();
		double utilization = 0;
		if (ramCapacity != 0)
			utilization = (double) monitoringUsedRamPerUnit / ramCapacity;

		mvRam.add(utilization, logTime);

		// 💡 Log avant de remettre à zéro
		System.out.printf("📦 [VM %s] RAM utilisée: %d / %d → Utilisation: %.2f%%\n",
				this.getName(), monitoringUsedRamPerUnit, getRam(), utilization * 100);

		monitoringUsedRamPerUnit = 0; // Réinitialisation après log
	}

	public MonitoringValues getMonitoringValuesVmRamUtilization() {
		return mvRam;
	}

}
