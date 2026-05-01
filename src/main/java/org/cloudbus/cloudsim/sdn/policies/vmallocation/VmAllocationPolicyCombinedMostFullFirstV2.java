/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.policies.vmallocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;

/**
 *
 * VM Allocation Policy - BW, Compute, and RAM combined.
 * Allocates hosts based on combined usage of compute power, bandwidth, and RAM.
 *
 *  
 * @author QOUDHADH NADIA 
 * @since CloudSimSDN 1.0
 */
public class VmAllocationPolicyCombinedMostFullFirstV2 extends VmAllocationPolicy implements PowerUtilizationMaxHostInterface {

	
	/** The vm table. */
	private Map<String, Host> vmTable;

	/** The used pes. */
	private Map<String, Integer> usedPes;

	/** The free pes. */
	private List<Integer> freePes;
	
	private Map<String, Long> usedMips;
	private List<Long> freeMips;
	private Map<String, Long> usedBw;
	private List<Long> freeBw;
	private Map<String, Long> usedRam; // RAM used
    private List<Long> freeRam;        // RAM free

	/**
	 * Creates the new VmAllocationPolicySimple object.
	 * 
	 * @param list the list
	 * @pre $none
	 * @post $none
	 */
	public VmAllocationPolicyCombinedMostFullFirstV2(List<? extends Host> list) {
		super(list);

		setFreePes(new ArrayList<Integer>());
		setFreeMips(new ArrayList<Long>());
		setFreeBw(new ArrayList<Long>());
		setFreeRam(new ArrayList<>()); // Initialize RAM tracking
		
		for (Host host : getHostList()) {
			getFreePes().add(host.getNumberOfPes());
			getFreeMips().add((long) host.getTotalMips());
			getFreeBw().add(host.getBw());
			getFreeRam().add((long) host.getRam()); // Initialize RAM
		}
		// No longer setting global hostTotal fields here

		setVmTable(new HashMap<String, Host>());
		setUsedPes(new HashMap<String, Integer>());
		setUsedMips(new HashMap<String, Long>());
		setUsedBw(new HashMap<String, Long>());
		setUsedRam(new HashMap<>()); // Initialize RAM usage
	}

	protected double convertWeightedMetric(double mipsPercent, double bwPercent, double ramPercent) {
        double weightMips = 0.3; // Weight for MIPS
        double weightBw = 0.4;   // Weight for Bandwidth
        double weightRam = 0.3;  // Weight for RAM
        return (mipsPercent * weightMips) + (bwPercent * weightBw) + (ramPercent * weightRam);
    }
	/**
	 * Allocates a host for a given VM.
	 * 
	 * @param vm VM specification
	 * @return $true if the host could be allocated; $false otherwise
	 * @pre $none
	 * @post $none
	 */
	@Override
	public boolean allocateHostForVm(Vm vm) {
		if (getVmTable().containsKey(vm.getUid())) { // if this vm was not created
			return false;
		}
		
		int numHosts = getHostList().size();

		// 1. Find/Order the best host for this VM by comparing a metric
		int requiredPes = vm.getNumberOfPes();
		double requiredMips = vm.getCurrentRequestedTotalMips();
		long requiredBw = vm.getCurrentRequestedBw();
		long requiredRam = vm.getCurrentRequestedRam(); // RAM required by VM

		boolean result = false;
		
		double[] freeResources = new double[numHosts];
		for (int i = 0; i < numHosts; i++) {
			Host host = getHostList().get(i);
			double mipsFreePercent = (double)getFreeMips().get(i) / host.getTotalMips(); 
			double bwFreePercent = (double)getFreeBw().get(i) / host.getBw();
			double ramFreePercent = (double) getFreeRam().get(i) / host.getRam(); // Calculate free RAM %
			
			freeResources[i] = convertWeightedMetric(mipsFreePercent, bwFreePercent, ramFreePercent);
		}

		for(int tries = 0; result == false && tries < numHosts; tries++) {// we still trying until we find a host or until we try all of them
			double lessFree = Double.POSITIVE_INFINITY;
			int idx = -1;

			// we want the host with less pes in use
			for (int i = 0; i < numHosts; i++) {
				if (freeResources[i] < lessFree) {
					lessFree = freeResources[i];
					idx = i;
				}
			}
			freeResources[idx] = Double.POSITIVE_INFINITY;
			Host host = getHostList().get(idx);
			

			// // Check whether the host can hold this VM or not.
			// if( getFreeMips().get(idx) < requiredMips) {
			// 	System.err.println("not enough MIPS:"+getFreeMips().get(idx)+", req="+requiredMips);
			// 	//Cannot host the VM
			// 	continue;
			// }
			// if( getFreeBw().get(idx) < requiredBw) {
			// 	System.err.println("not enough BW:"+getFreeBw().get(idx)+", req="+requiredBw);
			// 	//Cannot host the VM
			// 	//continue;
			// }

			if (getFreeMips().get(idx) < requiredMips || getFreeBw().get(idx) < requiredBw || getFreeRam().get(idx) < requiredRam) {
                continue;
            }
			
			result = host.vmCreate(vm);

			if (result) { // if vm were succesfully created in the host
				getVmTable().put(vm.getUid(), host);
				getUsedPes().put(vm.getUid(), requiredPes);
				getFreePes().set(idx, getFreePes().get(idx) - requiredPes);
				
				getUsedMips().put(vm.getUid(), (long) requiredMips);
				getFreeMips().set(idx,  (long) (getFreeMips().get(idx) - requiredMips));

				getUsedBw().put(vm.getUid(), (long) requiredBw);
				getFreeBw().set(idx,  (long) (getFreeBw().get(idx) - requiredBw));

				getUsedRam().put(vm.getUid(), requiredRam); // Track RAM usage
                getFreeRam().set(idx, getFreeRam().get(idx) - requiredRam); // Update free RAM

				break;
			}
		}
		
		if(!result) {
			System.err.println("VmAllocationPolicy: WARNING:: Cannot create VM!!!!");
		}
		logMaxNumHostsUsed();
		return result;
	}
	
	protected int maxNumHostsUsed=0;
	public void logMaxNumHostsUsed() {
		// Get how many are used
		int numHostsUsed=0;
		int numHosts = getHostList().size();
		for(int i=0; i<numHosts; i++) {
			if(getFreePes().get(i) < getHostList().get(i).getNumberOfPes()) {
				numHostsUsed++;
			}
		}
		if(maxNumHostsUsed < numHostsUsed)
			maxNumHostsUsed = numHostsUsed;
		System.out.println("Number of online hosts:"+numHostsUsed + ", max was ="+maxNumHostsUsed);
	}
	public int getMaxNumHostsUsed() { return maxNumHostsUsed;}

	/**
	 * Releases the host used by a VM.
	 * 
	 * @param vm the vm
	 * @pre $none
	 * @post none
	 */
	@Override
	public void deallocateHostForVm(Vm vm) {
		Host host = getVmTable().remove(vm.getUid());
		if (host != null) {
			int idx = getHostList().indexOf(host);
			host.vmDestroy(vm);
			
			Integer pes = getUsedPes().remove(vm.getUid());
			getFreePes().set(idx, getFreePes().get(idx) + pes);
			
			Long mips = getUsedMips().remove(vm.getUid());
			getFreeMips().set(idx, getFreeMips().get(idx) + mips);
			
			Long bw = getUsedBw().remove(vm.getUid());
			getFreeBw().set(idx, getFreeBw().get(idx) + bw);

			Long ram = getUsedRam().remove(vm.getUid()); // Deallocate RAM
            getFreeRam().set(idx, getFreeRam().get(idx) + ram); // Update free RAM
		}
	}

	/**
	 * Gets the host that is executing the given VM belonging to the given user.
	 * 
	 * @param vm the vm
	 * @return the Host with the given vmID and userID; $null if not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Host getHost(Vm vm) {
		return getVmTable().get(vm.getUid());
	}

	/**
	 * Gets the host that is executing the given VM belonging to the given user.
	 * 
	 * @param vmId the vm id
	 * @param userId the user id
	 * @return the Host with the given vmID and userID; $null if not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Host getHost(int vmId, int userId) {
		return getVmTable().get(Vm.getUid(userId, vmId));
	}

	/**
	 * Gets the vm table.
	 * 
	 * @return the vm table
	 */
	public Map<String, Host> getVmTable() {
		return vmTable;
	}

	/**
	 * Sets the vm table.
	 * 
	 * @param vmTable the vm table
	 */
	protected void setVmTable(Map<String, Host> vmTable) {
		this.vmTable = vmTable;
	}

	/**
	 * Gets the used pes.
	 * 
	 * @return the used pes
	 */
	protected Map<String, Integer> getUsedPes() {
		return usedPes;
	}

	/**
	 * Sets the used pes.
	 * 
	 * @param usedPes the used pes
	 */
	protected void setUsedPes(Map<String, Integer> usedPes) {
		this.usedPes = usedPes;
	}

	/**
	 * Gets the free pes.
	 * 
	 * @return the free pes
	 */
	protected List<Integer> getFreePes() {
		return freePes;
	}

	/**
	 * Sets the free pes.
	 * 
	 * @param freePes the new free pes
	 */
	protected void setFreePes(List<Integer> freePes) {
		this.freePes = freePes;
	}

	protected Map<String, Long> getUsedMips() {
		return usedMips;
	}
	protected void setUsedMips(Map<String, Long> usedMips) {
		this.usedMips = usedMips;
	}
	protected Map<String, Long> getUsedBw() {
		return usedBw;
	}
	protected void setUsedBw(Map<String, Long> usedBw) {
		this.usedBw = usedBw;
	}
	protected List<Long> getFreeMips() {
		return this.freeMips;
	}
	protected void setFreeMips(List<Long> freeMips) {
		this.freeMips = freeMips;
	}
	
	protected List<Long> getFreeBw() {
		return this.freeBw;
	}
	protected void setFreeBw(List<Long> freeBw) {
		this.freeBw = freeBw;
	}
	 // Getters and Setters for RAM
	 protected Map<String, Long> getUsedRam() {
        return usedRam;
    }

    protected void setUsedRam(Map<String, Long> usedRam) {
        this.usedRam = usedRam;
    }

	protected List<Long> getFreeRam() {
        return freeRam;
    }

    protected void setFreeRam(List<Long> freeRam) {
        this.freeRam = freeRam;
    }


	/*
	 * (non-Javadoc)
	 * @see cloudsim.VmAllocationPolicy#optimizeAllocation(double, cloudsim.VmList, double)
	 */
	@Override
	public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.cloudbus.cloudsim.VmAllocationPolicy#allocateHostForVm(org.cloudbus.cloudsim.Vm,
	 * org.cloudbus.cloudsim.Host)
	 */
	@Override
	public boolean allocateHostForVm(Vm vm, Host host) {
		if (host.vmCreate(vm)) { // if vm has been succesfully created in the host
			getVmTable().put(vm.getUid(), host);

			int pe = vm.getNumberOfPes();
			double requiredMips = vm.getCurrentRequestedTotalMips();
			long requiredBw = vm.getCurrentRequestedBw();
			
			int idx = getHostList().indexOf(host);
			
			getUsedPes().put(vm.getUid(), pe);
			getFreePes().set(idx, getFreePes().get(idx) - pe);
			
			getUsedMips().put(vm.getUid(), (long) requiredMips);
			getFreeMips().set(idx,  (long) (getFreeMips().get(idx) - requiredMips));

			getUsedBw().put(vm.getUid(), (long) requiredBw);
			getFreeBw().set(idx, (long) (getFreeBw().get(idx) - requiredBw));

			Log.formatLine(
					"%.2f: VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),
					CloudSim.clock());
			return true;
		}

		return false;
	}	
}

