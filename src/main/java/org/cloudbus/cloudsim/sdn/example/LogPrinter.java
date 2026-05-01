/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

 package org.cloudbus.cloudsim.sdn.example;

 import java.util.List;
import java.util.Locale;

import org.cloudbus.cloudsim.Cloudlet;
 import org.cloudbus.cloudsim.Host;
 import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.SDNBroker;
 import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationHistoryEntry;
 import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
 import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
 import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;
 import org.cloudbus.cloudsim.sdn.virtualcomponents.SDNVm;
 import org.cloudbus.cloudsim.sdn.workload.Activity;
 import org.cloudbus.cloudsim.sdn.workload.Processing;
 import org.cloudbus.cloudsim.sdn.workload.Request;
 import org.cloudbus.cloudsim.sdn.workload.Transmission;
 import org.cloudbus.cloudsim.sdn.workload.Workload;
 
 /**
  * This class is to print out logs into console.
  * 
  * @author Jungmin Son
  * @since CloudSimSDN 1.0
  */
 public class LogPrinter {
	 private static double hostEnergyConsumption;
	 private static double switchEnergyConsumption;
	 private static double hostTotalTime;
	 private static double hostOverTime;
	 private static double hostOverScaleTime;
	 private static double vmTotalTime;
	 private static double vmOverTime;
	 private static double vmOverScaleTime;
 
	 public static void printEnergyConsumption(List<Host> hostList, List<Switch> switchList, double finishTime) {
		 hostEnergyConsumption= 0; switchEnergyConsumption = 0;hostTotalTime =0; hostOverTime =0; hostOverScaleTime=0;
		 vmTotalTime =0; vmOverTime =0; vmOverScaleTime=0;
		 
		 /*
		 Log.printLine("========== HOST POWER CONSUMPTION calculated based MIPS allocation ===========");
		 for(Host host:hostList) {
			 // Allocated MIPS based power consumption
			 PowerUtilizationInterface scheduler =  (PowerUtilizationInterface) host.getVmScheduler();
			 scheduler.addUtilizationEntryTermination(finishTime);
			 
			 double energy = scheduler.getUtilizationEnergyConsumption();
			 Log.printLine("[MIPS allocation] Host #"+host.getId()+": "+energy);
			 hostEnergyConsumptionMIPS+= energy;
 //			printHostUtilizationHistory(scheduler.getUtilizationHisotry());
		 }
		 //*/
		 
		//  Log.printLine("========== HOST POWER CONSUMPTION based on Actual Workload processing ===========");
		//  for(Host host:hostList) {
		// 	 // Actual workload based power consumption
		// 	 double consumedEnergy = ((SDNHost)host).getConsumedEnergy();
		// 	 Log.printLine("Host #"+host.getId()+": "+consumedEnergy);
		// 	 hostEnergyConsumption+= consumedEnergy;
		//  }

		Log.printLine("========== HOST POWER CONSUMPTION based on Actual Workload processing ==========");
		double totalEnergy = 0.0;

		for (Host host : hostList) {
			if (host instanceof SDNHost) {
				SDNHost sdnHost = (SDNHost) host;
				double consumed = sdnHost.getConsumedEnergy(); // provient du powerMonitor
				totalEnergy += consumed;
				Log.printLine(String.format(Locale.US, "Host #%d (%s): %.6f Wh", host.getId(), sdnHost.getName(), consumed));
			}
		}

		Log.printLine(String.format(Locale.US, "TOTAL HOST ENERGY: %.6f Wh", totalEnergy));

		 
		 Log.printLine("========== SWITCH POWER CONSUMPTION AND DETAILED UTILIZATION ===========");
		 for(Switch sw:switchList) {
			 //sw.addUtilizationEntryTermination(finishTime);
			 double energy = sw.getConsumedEnergy();
			 Log.printLine("Switch:"+sw.getName()+": "+energy);
			 switchEnergyConsumption+= energy;
 
 //			printSwitchUtilizationHistory(sw.getUtilizationHisotry());
 
		 }
		 Log.printLine("========== HOST Overload percentage ===========");
		 for(Host host:hostList) {
			 // Overloaded time
			 double overScaleTime = ((SDNHost)host).overloadLoggerGetScaledOverloadedDuration();
			 double overTime = ((SDNHost)host).overloadLoggerGetOverloadedDuration();
			 double totalTime = ((SDNHost)host).overloadLoggerGetTotalDuration();
			 double overPercent = (totalTime != 0) ? overTime/totalTime : 0; 
			 Log.printLine("Overload Host #"+host.getId()+": "+overTime+"/"+totalTime+"="+overPercent + "... Scaled Overload duration= "+overScaleTime);
			 hostTotalTime += totalTime;
			 hostOverTime += overTime;
			 hostOverScaleTime += overScaleTime;
		 }
		 
		 Log.printLine("========== VM Overload percentage ===========");
		 for(Host host:hostList) {
			 for (SDNVm vm : host.<SDNVm>getVmList()) {
				 // Overloaded time
				 double overScaleTime = vm.overloadLoggerGetScaledOverloadedDuration();
				 double overTime = vm.overloadLoggerGetOverloadedDuration();
				 double totalTime = vm.overloadLoggerGetTotalDuration();
				 double overPercent = (totalTime != 0) ? overTime/totalTime : 0; 
				 Log.printLine("Vm("+vm+"): "+overTime+"/"+totalTime+"="+overPercent + "... Scaled Overload duration= "+overScaleTime);
				 vmTotalTime += totalTime;
				 vmOverTime += overTime;
				 vmOverScaleTime += overScaleTime;
			 }
		 }
	 }
	 public static void printTotalEnergy() {
		 Log.printLine("========== TOTAL POWER CONSUMPTION ===========");
		 Log.printLine("Host energy consumed: "+hostEnergyConsumption);
		 Log.printLine("Switch energy consumed: "+switchEnergyConsumption);
		 Log.printLine("Total energy consumed: "+(hostEnergyConsumption+switchEnergyConsumption));
		 //Log.printLine("Host (MIPS based) energy consumed: "+hostEnergyConsumptionMIPS);
		 
		 Log.printLine("========== MIGRATION ===========");
		 Log.printLine("Attempted: " + SDNDatacenter.migrationAttempted);
		 Log.printLine("Completed: " + SDNDatacenter.migrationCompleted);
 
		 Log.printLine("========== HOST OVERLOADED ===========");
		 Log.printLine("Scaled overloaded: " +( 1.0-(hostTotalTime == 0? 0:hostOverScaleTime/hostTotalTime)));
		 Log.printLine("Overloaded Percent: " + (hostTotalTime == 0? 0: hostOverTime / hostTotalTime));
		 Log.printLine("Total Time: " + hostTotalTime);
		 Log.printLine("Overloaded Time: " + hostOverTime);
		 
		 Log.printLine("========== VM OVERLOADED ===========");
		 Log.printLine("Scaled overloaded: " + (1.0-(vmTotalTime == 0? 0:vmOverScaleTime/vmTotalTime)));
		 Log.printLine("Overloaded Percent: " + (vmTotalTime == 0? 0: vmOverTime / vmTotalTime));
		 Log.printLine("Total Time: " + vmTotalTime);
		 Log.printLine("Overloaded Time: " + vmOverTime);
	 }
 
	 protected static void printHostUtilizationHistory(
			 List<PowerUtilizationHistoryEntry> utilizationHisotry) {
		 if(utilizationHisotry != null)
			 for(PowerUtilizationHistoryEntry h:utilizationHisotry) {
				 Log.printLine(h.startTime+", "+h.utilPercentage);
			 }
	 }
	 
	 static public String indent = ",";
	 static public String tabSize = "10";
	 static public String fString = 	"%"+tabSize+"s"+indent;
	 static public String fInt = 	"%"+tabSize+"d"+indent;
	 static public String fFloat = 	"%"+tabSize+".3f"+indent;
	 
	 public static void printCloudletList(List<Cloudlet> list) {
		 int size = list.size();
		 Cloudlet cloudlet;
 
		 Log.printLine();
		 Log.printLine("========== OUTPUT ==========");
		 
		 Log.print(String.format(LogPrinter.fString, "Cloudlet_ID"));
		 Log.print(String.format(LogPrinter.fString, "STATUS" ));
		 Log.print(String.format(LogPrinter.fString, "DataCenter_ID"));
		 Log.print(String.format(LogPrinter.fString, "VM_ID"));
		 Log.print(String.format(LogPrinter.fString, "Length"));
		 Log.print(String.format(LogPrinter.fString, "Time"));
		 Log.print(String.format(LogPrinter.fString, "Start Time"));
		 Log.print(String.format(LogPrinter.fString, "Finish Time"));
		 Log.print("\n");
 
		 //DecimalFormat dft = new DecimalFormat("######.##");
		 for (int i = 0; i < size; i++) {
			 cloudlet = list.get(i);
			 printCloudlet(cloudlet);
		 }
	 }
	 
	 private static void printCloudlet(Cloudlet cloudlet) {
		 Log.print(String.format(LogPrinter.fInt, cloudlet.getCloudletId()));
 
		 if (cloudlet.getStatus() == Cloudlet.SUCCESS) {
			 Log.print(String.format(LogPrinter.fString, "SUCCESS"));
			 Log.print(String.format(LogPrinter.fInt, cloudlet.getResourceId()));
			 Log.print(String.format(LogPrinter.fInt, cloudlet.getVmId()));
			 Log.print(String.format(LogPrinter.fInt, cloudlet.getCloudletLength()));
			 Log.print(String.format(LogPrinter.fFloat, cloudlet.getActualCPUTime()));
			 Log.print(String.format(LogPrinter.fFloat, cloudlet.getSubmissionTime()));
			 Log.print(String.format(LogPrinter.fFloat, cloudlet.getFinishTime()));
			 Log.print("\n");
		 }
		 else {
			 Log.printLine("FAILED");
		 }
	 }
	 
	 private static double startTime, finishTime;
	 private static int[] appIdNum = new int[SDNBroker.lastAppId];
	 private static double[] appIdTime = new double[SDNBroker.lastAppId];
	 private static double[] appIdStartTime = new double[SDNBroker.lastAppId];
	 private static double[] appIdFinishTime = new double[SDNBroker.lastAppId];
	 private static double totalTime = 0.0;
	 
	 protected static void printWorkload(Workload wl) {

		System.out.println("🔎 Workload ID: " + wl.workloadId);
		System.out.println("    Failed? " + wl.failed);
		//System.out.println("    Start Time: " + getWorkloadStartTime(wl));
		//System.out.println("    Finish Time: " + getWorkloadFinishTime(wl));
		System.out.println("    Activities: " + wl.getActivities());
		System.out.println("    Request activities: " + wl.request.getActivities());
		System.out.println("    Removed activities: " + wl.request.getRemovedActivities());

		 double serveTime;
		 
		 startTime = finishTime = -1;
 
		 Log.print(String.format(LogPrinter.fInt, wl.appId));
		 printRequest(wl.request);
		 
		 serveTime= (finishTime - startTime);
		 
		 Log.print(String.format(LogPrinter.fFloat, serveTime));
		 Log.printLine();
		 
		 totalTime += serveTime;
		 
		 appIdNum[wl.appId] ++;	//How many workloads in this app.
		 appIdTime[wl.appId] += serveTime;
		 if(appIdStartTime[wl.appId] <=0) {
			 appIdStartTime[wl.appId] = wl.time;
		 }
		 appIdFinishTime[wl.appId] = wl.time;
	 }
	 
	 public static void printWorkloadList(List<Workload> wls) {

		if (wls == null || wls.isEmpty()) {
			System.out.println("No workloads to print.");
			return;
		}
		
	
		 Log.print(String.format(LogPrinter.fString, "App_ID"));
		 printRequestTitleVD(wls.get(0).request);
		 Log.print(String.format(LogPrinter.fString, "ResponseTime"));
		 Log.printLine();
 
		 for(Workload wl:wls) {
			 printWorkload(wl);
		 }
		 
	
		
		 Log.printLine("========== AVERAGE RESULT OF WORKLOADS ===========");
		 for(int i=0; i<SDNBroker.lastAppId; i++) {
			 Log.printLine("App Id ("+i+"): "+appIdNum[i]+" requests, Start=" + appIdStartTime[i]+
					 ", Finish="+appIdFinishTime[i]+", Rate="+(double)appIdNum[i]/(appIdFinishTime[i] - appIdStartTime[i])+
					 " req/sec, Response time=" + appIdTime[i]/appIdNum[i]);
		 }
		 
		 //printGroupStatistics(WORKLOAD_GROUP_PRIORITY, appIdNum, appIdTime);
		 
		 Log.printLine("Average Response Time:"+(totalTime / wls.size()));
		 
	 }
 /*
  * 
	 public static void printWorkloadList(List<Workload> wls) {
		 
		 Log.printLine();
		 Log.printLine("========== DETAILED RESPONSE TIME OF WORKLOADS ===========");
 
		 if(wls.size() == 0) return;
		 
		 Log.print(String.format(LogPrinter.fString, "App_ID"));
		 printRequestTitle(wls.get(0).request);
		 Log.print(String.format(LogPrinter.fString, "ResponseTime"));
		 Log.printLine();
 
		 for(Workload wl:wls) {
			 printWorkload(wl);
		 }
 
		 Log.printLine("========== AVERAGE RESULT OF WORKLOADS ===========");
		 for(int i=0; i<SDNBroker.lastAppId; i++) {
			 Log.printLine("App Id ("+i+"): "+appIdNum[i]+" requests, Start=" + appIdStartTime[i]+
					 ", Finish="+appIdFinishTime[i]+", Rate="+(double)appIdNum[i]/(appIdFinishTime[i] - appIdStartTime[i])+
					 " req/sec, Response time=" + appIdTime[i]/appIdNum[i]);
		 }
		 
		 //printGroupStatistics(WORKLOAD_GROUP_PRIORITY, appIdNum, appIdTime);
		 
		 Log.printLine("Average Response Time:"+(totalTime / wls.size()));
		 
	 }
 
  */
//   private static void printRequestTitle(Request req) {
//     if (req == null) {
//         System.err.println("Request is null in printRequestTitle. Skipping processing.");
//         return;
//     }

//     List<Activity> acts = req.getRemovedActivities();
//     if (acts == null) {
//         System.err.println("Removed activities list is null for Request ID: " + req.getRequestId());
//         return;
//     }

//     for (Activity act : acts) {
//         if (act instanceof Transmission) {
//             Transmission tr = (Transmission) act;
//             Log.print(String.format(LogPrinter.fString, "Tr:Size"));
//             Log.print(String.format(LogPrinter.fString, "Tr:Channel"));
//             Log.print(String.format(LogPrinter.fString, "Tr:time"));
//             Log.print(String.format(LogPrinter.fString, "Tr:Start"));
//             Log.print(String.format(LogPrinter.fString, "Tr:End"));
// 			Request payload = tr.getPacket().getPayload();
//             if (payload != null) {
//                 // Affichage des informations du payload sans récursion
//                 Log.printLine("Payload details:");
//                 Log.printLine("  Request ID: " + payload.getRequestId());
//                 Log.printLine("  User ID: " + payload.getUserId());
//                 // Si vous souhaitez afficher la liste des activités du payload :
//                 List<Activity> payloadActs = payload.getActivities();
//                 if (payloadActs != null && !payloadActs.isEmpty()) {
//                     Log.printLine("  Activities in payload:");
//                     for (Activity a : payloadActs) {
//                         Log.printLine("    " + a.toString());
//                     }
//                 } else {
//                     Log.printLine("  No activities in payload.");
//                 }
//             } else {
//                 System.err.println("Payload is null for Transmission: " + tr);
//             }
//         } else {
//             Log.print(String.format(LogPrinter.fString, "Pr:Size"));
//             Log.print(String.format(LogPrinter.fString, "Pr:time"));
//             Log.print(String.format(LogPrinter.fString, "Pr:Start"));
//             Log.print(String.format(LogPrinter.fString, "Pr:End"));
//         }
//     }
// }
	/* MAJ Nadia  */
	private static void printRequestTitle(Request req) {
		if (req == null) {
			System.err.println("Request is null in printRequestTitle. Skipping processing.");
			return;
		}
		
		// Afficher les informations générales de la requête (par exemple, son ID et User ID)
		Log.print(String.format(LogPrinter.fString, "Payload Req_ID: " + req.getRequestId()));
		Log.print(String.format(LogPrinter.fString, "Payload User_ID: " + req.getUserId()));
		
		// Afficher ensuite les détails de ses activités, par exemple en appelant une méthode dédiée :
		printPayloadDetails(req);
	}
	
	private static void printPayloadDetails(Request req) {
		// Récupérer la liste des activités dans le payload
		List<Activity> acts = req.getActivities();  // ou getRemovedActivities() selon votre logique
		if (acts == null || acts.isEmpty()) {
			Log.printLine("No activities in payload.");
		} else {
			// Affichage en mode tableau
			Log.print(String.format(LogPrinter.fString, "Activity Type"));
			Log.print(String.format(LogPrinter.fString, "Size/Length"));
			Log.print(String.format(LogPrinter.fString, "Time"));
			Log.print(String.format(LogPrinter.fString, "Start"));
			Log.print(String.format(LogPrinter.fString, "End"));
			Log.printLine("");
			for (Activity act : acts) {
				if (act instanceof Processing) {
					Processing proc = (Processing) act;
					Cloudlet cl = proc.getCloudlet();
					// Affichage pour Processing
					Log.print(String.format(LogPrinter.fString, "Processing"));
					if (cl != null) {
						Log.print(String.format(LogPrinter.fInt, cl.getCloudletLength()));
						Log.print(String.format(LogPrinter.fFloat, cl.getActualCPUTime()));
						Log.print(String.format(LogPrinter.fFloat, cl.getExecStartTime()));
						Log.print(String.format(LogPrinter.fFloat, cl.getFinishTime()));
					} else {
						Log.print(String.format(LogPrinter.fString, "Cloudlet null"));
						Log.print(String.format(LogPrinter.fString, "0"));
						Log.print(String.format(LogPrinter.fString, "0"));
						Log.print(String.format(LogPrinter.fString, "0"));
						Log.print(String.format(LogPrinter.fString, "0"));
					}
					Log.printLine("");
				} else if (act instanceof Transmission) {
					Transmission trans = (Transmission) act;
					// Affichage pour Transmission
					Log.print(String.format(LogPrinter.fString, "Transmission"));
					Log.print(String.format(LogPrinter.fInt, trans.getPacket().getSize()));
					// Vous pouvez ajouter d'autres infos comme le temps, start, end, etc.
					Log.print(String.format(LogPrinter.fFloat, trans.getExpectedTime()));
					Log.print(String.format(LogPrinter.fFloat, trans.getPacket().getStartTime()));
					Log.print(String.format(LogPrinter.fFloat, trans.getPacket().getFinishTime()));
					Log.printLine("");
				}
			}
		}
	}
	
	
	private static void printRequestTitleVD(Request req) {
		if (req == null) {
			System.err.println("Request is null in printRequestTitle. Skipping processing.");
			return;
		}

		List<Activity> acts = req.getRemovedActivities();
		if (acts == null) {
			System.err.println("Removed activities list is null for Request ID: " + req.getRequestId());
			return;
		}

		// Afficher les en-têtes (headers) dans un format tabulaire
		// On utilise ici des chaînes de format définies dans LogPrinter (fString, fInt, etc.)
		// Vous pouvez ajuster le format (largeur, précision) en fonction de vos besoins.
		Log.print(String.format(LogPrinter.fString, "Tr:Size"));
		Log.print(String.format(LogPrinter.fString, "Tr:Channel"));
		Log.print(String.format(LogPrinter.fString, "Tr:time"));
		Log.print(String.format(LogPrinter.fString, "Tr:Start"));
		Log.print(String.format(LogPrinter.fString, "Tr:End"));
		// Colonnes pour le payload
		Log.print(String.format(LogPrinter.fString, "Payload Req_ID"));
		Log.print(String.format(LogPrinter.fString, "Payload User_ID"));
		Log.print(String.format(LogPrinter.fString, "Payload Activities"));
		Log.printLine("");  // Passage à la ligne

		// Parcourir toutes les activités supprimées
		for (Activity act : acts) {
			if (act instanceof Transmission) {
				Transmission tr = (Transmission) act;
				Packet pkt = tr.getPacket();
				// Affichage des données de la transmission
				Log.print(String.format(LogPrinter.fInt, pkt.getSize()));
				Log.print(String.format(LogPrinter.fInt, pkt.getFlowId()));
				double trTime = pkt.getFinishTime() - pkt.getStartTime();
				Log.print(String.format(LogPrinter.fFloat, trTime));
				Log.print(String.format(LogPrinter.fFloat, pkt.getStartTime()));
				Log.print(String.format(LogPrinter.fFloat, pkt.getFinishTime()));

				// Récupération du payload et affichage de ses informations dans le même tableau
				Request payload = pkt.getPayload();
				if (payload != null) {
					Log.print(String.format(LogPrinter.fInt, payload.getRequestId()));
					Log.print(String.format(LogPrinter.fInt, payload.getUserId()));
					// Concaténer les activités du payload dans une chaîne
					StringBuilder sb = new StringBuilder();
					if (payload.getActivities() != null && !payload.getActivities().isEmpty()) {
						for (Activity a : payload.getActivities()) {
							sb.append(a.toString()).append(" ");
						}
					} else {
						sb.append("No activities");
					}
					Log.print(String.format(LogPrinter.fString, sb.toString().trim()));
				} else {
					Log.print(String.format(LogPrinter.fString, "No payload"));
					Log.print(String.format(LogPrinter.fString, "No payload"));
					Log.print(String.format(LogPrinter.fString, "No payload"));
				}
				Log.printLine("");
			} else if (act instanceof Processing) {
				// Pour les activités de type Processing, vous pouvez afficher des colonnes dédiées
				Processing pr = (Processing) act;
				Cloudlet cl = pr.getCloudlet();
				if (cl != null) {
					Log.print(String.format(LogPrinter.fInt, cl.getCloudletLength()));
					Log.print(String.format(LogPrinter.fFloat, cl.getActualCPUTime()));
					Log.print(String.format(LogPrinter.fFloat, cl.getSubmissionTime()));
					Log.print(String.format(LogPrinter.fFloat, cl.getFinishTime()));
					// Pour Processing, on peut ne pas avoir de payload, on affiche des tirets
					Log.print(String.format(LogPrinter.fString, "-"));
					Log.print(String.format(LogPrinter.fString, "-"));
					Log.print(String.format(LogPrinter.fString, "-"));
				} else {
					System.err.println("Cloudlet is null in Processing activity.");
					Log.print(String.format(LogPrinter.fString, "Cloudlet null"));
				}
				Log.printLine("");
			}
		}
	}

	public static void printRequest(Request req) {
		if (req == null) {
			System.err.println("Request is null in printRequest. Skipping processing.");
			return;
		}
		
		// Affiche les informations principales de la requête
		Log.printLine("Request ID: " + req.getRequestId() + ", User ID: " + req.getUserId());
		
		// Appel à la méthode dédiée pour afficher les détails des activités (payload)
		printPayloadDetails(req);
	}
	
	//  public static void printRequest(Request req) {
	// 	 //Log.print(String.format(LogPrinter.fInt, req.getRequestId()));
	// 	 //Log.print(String.format(LogPrinter.fFloat, req.getStartTime()));
	// 	 //Log.print(String.format(LogPrinter.fFloat, req.getFinishTime()));
	// 	 List<Activity> acts = req.getRemovedActivities();

	// 	 /* MAJ Nadia  */
	// 	 if (req == null) {
	// 		System.err.println("Request is null in printRequest. Skipping processing.");
	// 		return;
	// 	}
	
		
	// 	if (acts == null) {
	// 		System.err.println("Removed activities list is null for Request ID: " + req.getRequestId());
	// 		return;
	// 	}
	// 	/* Fin MAJ  */
		 
		
	// 	 for(Activity act:acts) {
	// 		 if(act instanceof Transmission) {
	// 			 Transmission tr=(Transmission)act;
	// 			 Log.print(String.format(LogPrinter.fInt, tr.getPacket().getSize()));
	// 			 Log.print(String.format(LogPrinter.fInt, tr.getPacket().getFlowId()));
				 
	// 			 Log.print(String.format(LogPrinter.fFloat, tr.getPacket().getFinishTime() - tr.getPacket().getStartTime()));
	// 			 Log.print(String.format(LogPrinter.fFloat, tr.getPacket().getStartTime()));
	// 			 Log.print(String.format(LogPrinter.fFloat, tr.getPacket().getFinishTime()));
	// 			/* MAJ Nadia */
	// 			 // Vérifier que tr.getPacket().getPayload() n'est pas null avant de l'envoyer à printRequest
	// 			 Request payload = tr.getPacket().getPayload();
	// 			 if (payload != null) {
	// 				 // Affichage des informations du payload sans récursion
	// 				 Log.printLine("Payload details:");
	// 				 Log.printLine("  Request ID: " + payload.getRequestId());
	// 				 Log.printLine("  User ID: " + payload.getUserId());
	// 				 // Si vous souhaitez afficher la liste des activités du payload :
	// 				 List<Activity> payloadActs = payload.getActivities();
	// 				 if (payloadActs != null && !payloadActs.isEmpty()) {
	// 					 Log.printLine("  Activities in payload:");
	// 					 for (Activity a : payloadActs) {
	// 						 Log.printLine("    " + a.toString());
	// 					 }
	// 				 } else {
	// 					 Log.printLine("  No activities in payload.");
	// 				 }
	// 			 } else {
	// 				 System.err.println("Payload is null for Transmission: " + tr);
	// 			 }
	// 			 /* Fin MAJ */
	// 			 //printRequest(tr.getPacket().getPayload());
	// 		 }
	// 		 else if (act instanceof Processing) {
	// 			 Processing pr=(Processing)act;
	// 			 Cloudlet cl = pr.getCloudlet();
	// 			 if (cl != null) { // Vérifiez que cloudlet n'est pas null
	// 			 Log.print(String.format(LogPrinter.fInt, cl.getCloudletLength()));
	// 			 Log.print(String.format(LogPrinter.fFloat, cl.getActualCPUTime()));
	// 			 Log.print(String.format(LogPrinter.fFloat, cl.getSubmissionTime()));
	// 			 Log.print(String.format(LogPrinter.fFloat, cl.getFinishTime()));
	// 			 if (startTime == -1) {
	// 				 startTime = cl.getExecStartTime();
	// 			 }
	// 			 finishTime = cl.getFinishTime();
	// 		 } else {
	// 			System.err.println("Cloudlet is null in Processing activity.");
	// 			 Log.print(String.format(LogPrinter.fString, "Cloudlet null"));
	// 		 }
		 
	//  }
	// }}
	 
	 public static void printGroupStatistics(int groupSeperateNum, int[] appIdNum, double[] appIdTime) {
 
		 double prioritySum = 0, standardSum = 0;
		 int priorityReqNum = 0, standardReqNum =0;
		 
		 for(int i=0; i<SDNBroker.lastAppId; i++) {
			 double avgResponseTime = appIdTime[i]/appIdNum[i];
			 if(i<groupSeperateNum) {
				 prioritySum += avgResponseTime;
				 priorityReqNum += appIdNum[i];
			 }
			 else {
				 standardSum += avgResponseTime;
				 standardReqNum += appIdNum[i];
			 }
		 }
 
		 Log.printLine("Average Response Time(Priority):"+(prioritySum / priorityReqNum));
		 Log.printLine("Average Response Time(Standard):"+(standardSum / standardReqNum));
	 }
	 
	 public static void printConfiguration() {
		 Log.printLine("========== CONFIGURATIONS ===========");
		 Log.printLine("workingDirectory :"+Configuration.workingDirectory);
		 
		 
		 //Log.printLine("minTimeBetweenEvents: "+Configuration.minTimeBetweenEvents);
		 //Log.printLine("resolutionPlaces:"+Configuration.resolutionPlaces);
		 //Log.printLine("timeUnit:"+Configuration.timeUnit);
		 
		 Log.printLine("overbookingTimeWindowInterval:"+ Configuration.overbookingTimeWindowInterval);	// Time interval between points 
 
		 Log.printLine("OVERLOAD_THRESHOLD:"+ Configuration.OVERLOAD_THRESHOLD);
		 Log.printLine("OVERLOAD_THRESHOLD_ERROR:"+ Configuration.OVERLOAD_THRESHOLD_ERROR);
		 Log.printLine("OVERLOAD_THRESHOLD_BW_UTIL:"+ Configuration.OVERLOAD_THRESHOLD_BW_UTIL);
	 
		 Log.printLine("UNDERLOAD_THRESHOLD_HOST:"+ Configuration.UNDERLOAD_THRESHOLD_HOST);
		 Log.printLine("UNDERLOAD_THRESHOLD_HOST_BW:"+ Configuration.UNDERLOAD_THRESHOLD_HOST_BW);
		 Log.printLine("UNDERLOAD_THRESHOLD_VM:"+ Configuration.UNDERLOAD_THRESHOLD_VM);
		 
		 Log.printLine("DECIDE_SLA_VIOLATION_GRACE_ERROR:"+ Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR);
		 
 
		 Log.printLine("==================================================");
		 Log.printLine("========== PARAMETERS ===========");
		 Log.printLine("experimentName :"+Configuration.experimentName);
		 
		 Log.printLine("CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT:"+ Configuration.CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT);
		 
		 Log.printLine("monitoringTimeInterval:"+ Configuration.monitoringTimeInterval); // every 60 seconds, polling utilization.
		 Log.printLine("overbookingTimeWindowNumPoints:"+ Configuration.overbookingTimeWindowNumPoints);	// How many points to track
		 Log.printLine("migrationTimeInterval:"+ Configuration.migrationTimeInterval); // every 1 seconds, polling utilization.
	 
		 Log.printLine("OVERBOOKING_RATIO_MAX:"+ Configuration.OVERBOOKING_RATIO_MAX); 
		 Log.printLine("OVERBOOKING_RATIO_MIN:"+ Configuration.OVERBOOKING_RATIO_MIN);
		 Log.printLine("OVERBOOKING_RATIO_INIT:"+ Configuration.OVERBOOKING_RATIO_INIT);
		 
		 Log.printLine("OVERBOOKING_RATIO_UTIL_PORTION:"+ Configuration.OVERBOOKING_RATIO_UTIL_PORTION);	
		 Log.printLine("OVERLOAD_HOST_PERCENTILE_THRESHOLD:"+ Configuration.OVERLOAD_HOST_PERCENTILE_THRESHOLD);
		 
	 }	
 }
 