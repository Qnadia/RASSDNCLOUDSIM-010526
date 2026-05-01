/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.parsers;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.example.LogPrinter;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.workload.Processing;
import org.cloudbus.cloudsim.sdn.workload.Request;
import org.cloudbus.cloudsim.sdn.workload.Transmission;
import org.cloudbus.cloudsim.sdn.workload.Workload;
import org.cloudbus.cloudsim.sdn.workload.WorkloadResultWriter;
import org.cloudbus.cloudsim.sdn.UtilizationModelStochastic;

/**
 * Parse [request].csv file.
 * 
 * File format : req_time, vm_name(1), pkt_size(1), cloudlet_len(1),
 * vm_name(2), pkt_size(2), cloudlet_len(2),
 * ...
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */

public class WorkloadParser {
	private static final int NUM_PARSE_EACHTIME = 1200;

	private double forcedStartTime = -1;
	private double forcedFinishTime = Double.POSITIVE_INFINITY;

	private final Map<String, Integer> vmNames;
	private final Map<String, Integer> flowNames;
	/* Nadia */
	private final UtilizationModel utilCpu;
	private final UtilizationModel utilRam;
	private final UtilizationModel utilBw;

	private final Map<Integer, Long> flowIdToBandwidthMap; // Nouvelle map

	private String file;
	private int userId;
	// private UtilizationModel utilizationModel;
	private UtilizationModelStochastic utilizationModelStochastic;

	private List<Workload> parsedWorkloads;
	private List<Workload> completedWorkloads = new ArrayList<>();

	private WorkloadResultWriter resultWriter = null;

	private int workloadNum = 0;

	private BufferedReader bufReader = null;

	private WorkloadParser parser;

	private int groupId;

	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}

	public int getGroupId() {
		return this.groupId;
	}

	// ✅ Constructeur complémentaire pour compatibilité avec SDNBroker
	public WorkloadParser(String file, int userId, UtilizationModel cloudletUtilModel,
			Map<String, Integer> vmNameIdMap, Map<String, Integer> flowNameIdMap,
			Map<Integer, Long> flowIdToBandwidthMap) {
		this(file, userId, cloudletUtilModel, cloudletUtilModel, cloudletUtilModel, 
             vmNameIdMap, flowNameIdMap, flowIdToBandwidthMap);
	}

	public WorkloadParser(String file, int userId, UtilizationModel utilCpu, UtilizationModel utilRam,
			UtilizationModel utilBw,
			Map<String, Integer> vmNameIdMap, Map<String, Integer> flowNameIdMap,
			Map<Integer, Long> flowIdToBandwidthMap) {

		System.out.println("############### WorkloadParser");

		// Validation des paramètres obligatoires
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Le fichier de workload ne peut pas être null ou vide.");
		}

		// if (cloudletUtilModel == null) {
		// throw new IllegalArgumentException("Le modèle d'utilisation ne peut pas être
		// null.");
		// }

		this.file = file;
		this.userId = userId;
		// this.utilizationModelStochastic = cloudletUtilModel;
		this.utilCpu = utilCpu;
		this.utilRam = utilRam;
		this.utilBw = utilBw;

		// Initialisation des maps avec des valeurs par défaut si elles sont null
		this.vmNames = (vmNameIdMap != null) ? vmNameIdMap : new HashMap<>();
		this.flowNames = (flowNameIdMap != null) ? flowNameIdMap : new HashMap<>();
		this.flowIdToBandwidthMap = (flowIdToBandwidthMap != null) ? flowIdToBandwidthMap : new HashMap<>();

		// Debug: Afficher les mappings des VMs et des flux
		System.out.println("VM Names in WorkloadParser:");
		if (this.vmNames.isEmpty()) {
			System.out.println("Aucun mapping VM trouvé !");
		} else {
			this.vmNames.forEach((name, id) -> System.out.println(name + " -> " + id));
		}

		System.out.println("Flow Names in WorkloadParser:");
		if (this.flowNames.isEmpty()) {
			System.out.println("Aucun mapping Flow trouvé !");
		} else {
			this.flowNames.forEach((name, id) -> System.out.println(name + " -> " + id));
		}

		// Log pour vérifier les mappings dans SDNBroker
		System.out.println("Mappings stockés dans SDNBroker:");
		if (vmNameIdMap != null) {
			vmNameIdMap.forEach((name, id) -> System.out.println("VM: " + name + " -> ID: " + id));
		} else {
			System.out.println("vmNameIdMap est null !");
		}

		if (flowNameIdMap != null) {
			flowNameIdMap.forEach((name, id) -> System.out.println("Flow: " + name + " -> ID: " + id));
		} else {
			System.out.println("flowNameIdMap est null !");
		}

		// Initialisation du WorkloadResultWriter
		String result_file = Configuration.workingDirectory + getResultFileName(this.file); // MAJ Nadia : préfixe
																							// workingDirectory
		try {
			this.resultWriter = new WorkloadResultWriter(result_file);
		} catch (IOException e) {
			System.err.println("❌ Failed to initialize WorkloadResultWriter for file: " + result_file);
			System.err.println("Erreur: " + e.getMessage());
			e.printStackTrace();
			// Vous pouvez choisir de lever une exception au lieu de quitter l'application
			throw new RuntimeException("Impossible d'initialiser WorkloadResultWriter.", e);
		}

		// Ouvrir le fichier de workload
		try {
			openFile();
		} catch (Exception e) {
			System.err.println("❌ Failed to open workload file: " + this.file);
			System.err.println("Erreur: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Impossible d'ouvrir le fichier de workload.", e);
		}
	}

	public void forceStartTime(double forcedStartTime) {
		this.forcedStartTime = forcedStartTime;
	}

	public void forceFinishTime(double forcedFinishTime) {
		this.forcedFinishTime = forcedFinishTime;
	}

	public static String getResultFileName(String fileName) {
		String result_file = null;
		int indexSlash = fileName.lastIndexOf("/");
		String path_file = (indexSlash != -1) ? fileName.substring(indexSlash + 1) : fileName;

		if (Configuration.experimentName != null && !Configuration.experimentName.isEmpty()) {
			// Save in the current experiment output directory
			result_file = Configuration.experimentName + "/result_" + path_file;
		} else {
			// Fallback: save in the dataset folder
			if (indexSlash != -1) {
				String path_folder = fileName.substring(0, indexSlash + 1);
				result_file = path_folder + "result_" + path_file;
			} else {
				result_file = "result_" + fileName;
			}
		}
		return result_file;
	}

	public void parseNextWorkloadsSJF() {
		System.out.println("parseNextWorkloads appelée.");
		this.parsedWorkloads = new ArrayList<Workload>();
		parseNextt(NUM_PARSE_EACHTIME);
	}

	public void parseNextWorkloadss() {
		parseNextWorkloads();
	}

	public void parseNextWorkloads() {
		System.out.println("parseNextWorkloadsSsansJF appelée.");
		this.parsedWorkloads = new ArrayList<Workload>();
		parseNextt(NUM_PARSE_EACHTIME);
	}

	public List<Workload> getParsedWorkloads() {
		return this.parsedWorkloads;
	}

	public WorkloadResultWriter getResultWriter() {
		return resultWriter;
	}

	private int getVmId(String vmName) {
		Integer vmId = this.vmNames.get(vmName);
		if (vmId == null) {
			System.err.println("Cannot find VM name: " + vmName);
			return -1; // Retourner -1 si la VM n'est pas trouvée
		}
		return vmId;
	}

	/* MAJ Nadia */
	/**
	 * Retourne le fichier de workload associé à ce parser.
	 *
	 * @return Le fichier de workload.
	 */
	public String getFile() {
		return this.file;
	}

	public Cloudlet generateCloudlet(long cloudletId, int userId, int vmId, int length) {
		System.out.println("############# generateCloudlet");
		int peNum = 1;
		long fileSize = 300;
		long outputSize = 300;

		Cloudlet cloudlet = new Cloudlet(
				(int) cloudletId,
				length,
				peNum,
				fileSize,
				outputSize,
				utilCpu,
				utilRam,
				utilBw);

		cloudlet.setUserId(userId);
		cloudlet.setVmId(vmId);

		System.out.println("✅ Cloudlet généré : ID=" + cloudletId + " | Length=" + length + " | VM ID=" + vmId
				+ " | User ID=" + userId);
		System.out.println("******************** Util CPU (stochastic) pour cloudlet " + cloudletId + ": " +
				utilCpu.getUtilization(0));

		return cloudlet;
	}

	// public Cloudlet generateCloudlet(long cloudletId, int vmId, int length) {
	// int peNum=1;
	// long fileSize = 300;
	// long outputSize = 300;
	// Cloudlet cloudlet= new Cloudlet((int)cloudletId, length, peNum, fileSize,
	// outputSize, utilizationModel, utilizationModel, utilizationModel);
	// cloudlet.setUserId(userId);
	// cloudlet.setVmId(vmId);

	// return cloudlet;
	// }

	// private Request parseRequest(int fromVmId, Queue<String> lineitems) {
	// // Vérifier si la file est vide
	// if (lineitems.size() <= 0) {
	// System.err.println("No REQUEST! ERROR: La file lineitems est vide.");
	// return null;
	// }

	// // Extraire la longueur du cloudlet
	// long cloudletLen = Long.parseLong(lineitems.poll());
	// cloudletLen *= Configuration.CPU_SIZE_MULTIPLY;
	// if (cloudletLen < 0) {
	// System.err.println("Longueur du cloudlet invalide : " + cloudletLen);
	// return null;
	// }

	// // Créer la Request
	// Request req = new Request(userId);

	// // Créer le Cloudlet
	// Cloudlet cl = generateCloudlet(req.getRequestId(), fromVmId, (int)
	// cloudletLen);
	// if (cl == null) {
	// throw new IllegalStateException("Cloudlet is null for request ID: " +
	// req.getRequestId());
	// }

	// // Ajouter l'activité de Processing
	// Processing proc = new Processing(cl);
	// req.addActivity(proc);
	// System.out.println("Cloudlet créé avec ID : " + cl.getCloudletId() + ",
	// longueur : " + cl.getCloudletLength());

	// // Vérifier s'il reste des éléments dans la file
	// if (lineitems.size() != 0) {
	// // Ignorer w1
	// lineitems.poll();

	// // Extraire les informations de transmission
	// String linkName = lineitems.poll();
	// Integer flowId = this.flowNames.get(linkName);

	// if (flowId == null) {
	// throw new IllegalArgumentException("No such link name in virtual.json: " +
	// linkName);
	// }

	// String vmName = lineitems.poll();
	// int toVmId = getVmId(vmName);
	// if (toVmId == -1) {
	// throw new IllegalArgumentException("Invalid destination VM name: " + vmName);
	// }

	// long pktSize = Long.parseLong(lineitems.poll());
	// pktSize *= Configuration.NETWORK_PACKET_SIZE_MULTIPLY;
	// if (pktSize < 0) {
	// pktSize = 0;
	// }

	// // Ignorer w2
	// lineitems.poll();

	// // Ajouter l'activité de Transmission
	// Transmission trans = new Transmission(fromVmId, toVmId, pktSize, flowId,
	// null);
	// req.addActivity(trans);
	// } else {
	// // C'est la dernière requête
	// System.out.println("Dernière requête traitée.");
	// }

	// return req;
	// }

	/* MAJ Nadia */
	public void parseNextt(int numRequests) {
		System.out.println("🔎 parseNextt - Start parsing workloads...");
		String line;

		try {
			while (((line = bufReader.readLine()) != null) && (parsedWorkloads.size() < numRequests)) {
				if (line.trim().isEmpty() || line.startsWith("#"))
					continue;

				String[] splitLine = line.split(",");
				if (splitLine.length < 9) {
					System.err.println("⚠ Invalid workload line (need 9 columns): " + line);
					continue;
				}

				try {
					int idx = 0;
					double time = Double.parseDouble(splitLine[idx++]);

					if (time < forcedStartTime || time > forcedFinishTime) {
						System.out.println("🕒 Ignored workload time out of range: " + time);
						continue;
					}

					String srcVmName = splitLine[idx++];
					int fromVmId = getVmId(srcVmName);
					if (fromVmId == -1) {
						System.err.println("⚠ VM not found: " + srcVmName);
						continue;
					}

					// Ignorer z et w1
					idx += 2;

					String linkName = splitLine[idx++];
					String destVmName = splitLine[idx++];
					long pktSize = Long.parseLong(splitLine[idx++]);
					idx++; // Ignorer w2
					long cloudletLen = Long.parseLong(splitLine[idx++]);
					System.out.println("Parsed cloudlet length: " + cloudletLen);

					// Validate cloudlet length
					if (cloudletLen < 0) {
						System.err.println("⚠ Invalid cloudlet length (" + cloudletLen + ") in line: " + line);
						continue;
					}

					// Générer la Request
					Request req = parseRequestt(fromVmId, linkName, destVmName, pktSize, cloudletLen);
					if (req == null) {
						System.err.println("❌ Failed to create request for workload " + workloadNum);
						continue;
					}

					// Verify cloudlet length was set
					System.out.println("Request cloudlet length after creation: " +
							req.getLastProcessingCloudletLen()); // Debug log

					// Créer le Workload
					Workload tr = new Workload(workloadNum++, resultWriter);
					tr.time = time;
					tr.submitVmId = fromVmId;
					tr.request = req;

					// MAJ Nadia : lecture de la priorité (colonne optionnelle, index 9)
					if (splitLine.length >= 10) {
						try {
							int prio = Integer.parseInt(splitLine[9].trim());
							tr.setPriority(prio);
							tr.request.setPriority(prio); // propagation vers Request pour les métriques
						} catch (NumberFormatException e) {
							System.err.println("⚠ Priority invalide, défaut 0 : " + splitLine[9]);
						}
					}

					parsedWorkloads.add(tr);
					System.out.println("✅ Parsed workload " + tr.workloadId + " | Time: " + tr.time + " | Priority: "
							+ tr.getPriority());

				} catch (Exception e) {
					System.err.println("❌ Parsing error on line: " + line);
					e.printStackTrace();
				}
			}

			if (!parsedWorkloads.isEmpty()) {
				System.out.println("📊 Total workloads parsed: " + parsedWorkloads.size());
			} else {
				System.out.println("⚠ No valid workloads found in this batch.");
			}

		} catch (IOException e) {
			System.err.println("❌ File reading error in parseNextt()");
			e.printStackTrace();
		}
	}

	// public void parseNextt(int numRequests) {
	// System.out.println("parseNextt - Début de la lecture des workloads.");
	// String line;

	// try {
	// while (((line = bufReader.readLine()) != null)
	// && (parsedWorkloads.size() < numRequests)) {
	// // Ignorer les lignes vides ou les commentaires
	// if (line.trim().isEmpty() || line.startsWith("#")) {
	// continue;
	// }

	// try {
	// System.out.println("Lecture de la ligne : " + line);

	// // Split la ligne par virgule
	// String[] splitLine = line.split(",");

	// // Vérifiez que nous avons tous les champs nécessaires
	// if (splitLine.length < 9) {
	// System.err.println("Ligne de workload invalide (9 champs attendus) : " +
	// line);
	// continue;
	// }

	// // Créer un nouveau workload
	// Workload tr = new Workload(workloadNum++, this.resultWriter);

	// // Parsing des champs dans l'ordre défini
	// int idx = 0;

	// // Temps de démarrage
	// //tr.time = Double.parseDouble(splitLine[idx++]);
	// // if (tr.time < this.forcedStartTime || tr.time > this.forcedFinishTime) {
	// // System.out.println("Temps de workload hors plage. Ignoré.");
	// // continue;
	// // }
	// try {
	// tr.time = Double.parseDouble(splitLine[idx++]);

	// // Activer la vérification des temps
	// if (tr.time < this.forcedStartTime || tr.time > this.forcedFinishTime ||
	// Double.isNaN(tr.time)) {
	// System.out.println("Workload ignoré (hors plage ou temps invalide): " +
	// tr.time);
	// continue;
	// }
	// } catch (NumberFormatException e) {
	// System.err.println("Temps de workload invalide : " + line);
	// continue;
	// }

	// // Bloquer les temps hors plage ou non numériques
	// if (tr.time < this.forcedStartTime || tr.time > this.forcedFinishTime ||
	// Double.isNaN(tr.time)) {
	// System.out.println("Workload ignoré (hors plage ou temps invalide): " +
	// tr.time);
	// continue;
	// }
	// System.out.println("Temps traité : " + tr.time + ", Ligne : " + line);

	// // Nom de la VM source
	// String vmName = splitLine[idx++];
	// tr.submitVmId = getVmId(vmName);
	// if (tr.submitVmId == -1) {
	// System.err.println("VM ID invalide pour le nom de VM : " + vmName);
	// continue;
	// }

	// // z (troisième champ, ignoré mais lu)
	// long z = Long.parseLong(splitLine[idx++]);
	// System.out.println("z = " + z);

	// // w1 (quatrième champ)
	// long w1 = Long.parseLong(splitLine[idx++]);
	// System.out.println("w1 = " + w1);

	// // Nom du lien
	// String linkName = splitLine[idx++];

	// // Nom de la VM de destination
	// String destVmName = splitLine[idx++];

	// // Taille du paquet
	// long pktSize = Long.parseLong(splitLine[idx++]);
	// System.out.println("pktSize = " + pktSize);

	// // w2 (huitième champ)
	// long w2 = Long.parseLong(splitLine[idx++]);
	// System.out.println("w2 = " + w2);

	// // Longueur du cloudlet
	// long cloudletLen = Long.parseLong(splitLine[idx++]);
	// System.out.println("Longueur du cloudlet = " + cloudletLen);

	// // Créer et configurer la requête
	// tr.request = parseRequestt(tr.submitVmId, linkName, destVmName, pktSize,
	// cloudletLen);

	// if (tr.request == null) {
	// System.err.println("Request null pour le workload ID: " + tr.workloadId);
	// continue;
	// }

	// // Ajouter le workload à la liste
	// parsedWorkloads.add(tr);
	// System.out.println("Workload ID " + tr.workloadId + " lu avec succès.");

	// } catch (Exception e) {
	// System.err.println("Erreur lors de la lecture de la ligne de workload : " +
	// line);
	// e.printStackTrace();
	// }
	// }

	// // Trier les workloads selon SJF (si nécessaire)
	// if (!parsedWorkloads.isEmpty()) {
	// //parsedWorkloads.sort(Comparator.comparingDouble(wl ->
	// wl.request.getTotalLength()));
	// //System.out.println("Workloads triés selon SJF.");
	// System.out.println("Workloads conservés dans l'ordre original.");
	// } else {
	// System.out.println("Aucun workload n'a été parsé correctement.");
	// }
	// } catch (IOException e) {
	// System.err.println("Erreur de lecture du fichier workload :");
	// e.printStackTrace();
	// }
	// }

	/* MAJ Nadia */
	private Request parseRequestt(int fromVmId, String linkName, String destVmName, long pktSize, long cloudletLen) {
		System.out.println("📥 parseRequestt - Parsing Request");

		try {
			Request req = new Request(userId);
			req.setWorkloadParserId(this.getGroupId());
			req.setDstHostName(destVmName);

			System.out.println("DEBUG : RequestID" + req.getRequestId() + "1111111 setDstHostName(destVmName)"
					+ req.getDstHostName());

			// Set uniquement la longueur, le Cloudlet sera généré au moment du traitement
			if (cloudletLen > 0) {
				cloudletLen *= Configuration.CPU_SIZE_MULTIPLY;
				req.setLastProcessingCloudletLen(cloudletLen);
				// ❌ Pas de génération de Cloudlet ici
				Processing proc = new Processing(cloudletLen); // ou juste un marqueur
				req.addActivity(proc);
				req.setPrevActivity(proc);

			}

			int toVmId = getVmId(destVmName);
			if (toVmId == -1) {
				throw new IllegalArgumentException("❌ VM destination introuvable : " + destVmName);
			}

			if (pktSize > 0) {
				pktSize *= Configuration.NETWORK_PACKET_SIZE_MULTIPLY;

				Integer flowId = this.flowNames.get(linkName);
				if (flowId == null) {
					throw new IllegalArgumentException("❌ Aucun flow pour linkName : " + linkName);
				}

				Packet packet = new Packet(fromVmId, toVmId, pktSize, flowId, req);
				Transmission trans = new Transmission(packet);

				Long bw = flowIdToBandwidthMap.get(flowId);
				if (bw == null || bw <= 0) {
					bw = 1_000_000L; // valeur par défaut
				}

				trans.setRequestedBW(bw);
				req.addActivity(trans);
			}

			if (req.getActivities().isEmpty()) {
				System.err.println("❗ La Request ID: " + req.getRequestId() + " n'a aucune activité !");
				return null;
			}

			return req;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	// private Request parseRequestt(int fromVmId, String linkName, String
	// destVmName, long pktSize, long cloudletLen) {
	// System.out.println("📥 parseRequestt - Parsing Request");

	// try {
	// Request req = new Request(userId);
	// req.setWorkloadParserId(this.getGroupId()); // Correct
	// req.setDstHostName(destVmName); // Correct
	// System.out.println("DEBUG : RequestID"+req.getRequestId()+"1111111
	// setDstHostName(destVmName)"+ req.getDestinationVmName());

	// // Set BOTH computation and network components
	// req.setLastProcessingCloudletLen(cloudletLen); // This is critical

	// boolean hasActivity = false;
	// int toVmId = getVmId(destVmName);

	// if (cloudletLen > 0) {
	// cloudletLen *= Configuration.CPU_SIZE_MULTIPLY;

	// //Cloudlet cl = generateCloudlet(req.getRequestId(), userId, fromVmId, (int)
	// cloudletLen);
	// Cloudlet cl = generateCloudlet(req.getRequestId(), userId, toVmId, (int)
	// cloudletLen);

	// if (cl == null) {
	// throw new IllegalStateException("❌ Cloudlet is null for request ID: " +
	// req.getRequestId());
	// }

	// Processing proc = new Processing(cl);
	// req.addActivity(proc);
	// hasActivity = true;
	// }

	// if (pktSize > 0) {
	// pktSize *= Configuration.NETWORK_PACKET_SIZE_MULTIPLY;

	// Integer flowId = this.flowNames.get(linkName);
	// if (flowId == null) {
	// throw new IllegalArgumentException("❌ Aucun flow pour linkName : " +
	// linkName);
	// }

	// if (toVmId == -1) {
	// throw new IllegalArgumentException("❌ VM destination introuvable : " +
	// destVmName);
	// }

	// Packet packet = new Packet(fromVmId, toVmId, pktSize, flowId, req);
	// Transmission trans = new Transmission(packet);

	// Long bw = flowIdToBandwidthMap.get(flowId);
	// if (bw == null || bw <= 0) {
	// bw = 1_000_000L; // Default BW
	// }

	// trans.setRequestedBW(bw);
	// req.addActivity(trans);
	// hasActivity = true;
	// }

	// if (!hasActivity) {
	// System.err.println("❗ La Request ID: " + req.getRequestId() + " n'a aucune
	// activité !");
	// return null;
	// }

	// return req;

	// } catch (Exception e) {
	// e.printStackTrace();
	// return null;
	// }
	// }

	/* MAJ Nadia */

	public int getTotalWorkloadCount() {
		return parsedWorkloads.size() + completedWorkloads.size();
	}

	public void addCompletedWorkload(Workload wl) {
		System.out.println("################### addCompletedWorkload");
		resultWriter.writeResult(wl);

		// MAJ des stats locales (très important !)
		this.completedWorkloads.add(wl); // si tu les stockes
		System.out.println("✅ Workload ajouté au parserId: " + wl.getAppId() + ", workloadId: " + wl.getWorkloadId());
	}

	// private Request parseRequestt(int fromVmId, String linkName, String
	// destVmName, long pktSize, long cloudletLen) {
	// System.out.println("📥 parseRequestt - Parsing Request");

	// try {
	// Request req = new Request(userId);

	// // 1. Génération et ajout du Cloudlet (si applicable)
	// if (cloudletLen > 0) {
	// cloudletLen *= Configuration.CPU_SIZE_MULTIPLY;

	// Cloudlet cl = generateCloudlet(req.getRequestId(), fromVmId, (int)
	// cloudletLen);

	// if (cl == null) {
	// throw new IllegalStateException("Cloudlet is null for request ID: " +
	// req.getRequestId());
	// }

	// Processing proc = new Processing(cl);
	// req.addActivity(proc);

	// System.out.println(" Added Processing Activity | CloudletID: " +
	// cl.getCloudletId() + " | Length: " + cl.getCloudletLength());
	// }

	// // ✅ 2. Génération et ajout de la Transmission (si applicable)
	// if (pktSize > 0) {
	// pktSize *= Configuration.NETWORK_PACKET_SIZE_MULTIPLY;

	// Integer flowId = this.flowNames.get(linkName);
	// if (flowId == null) {
	// throw new IllegalArgumentException("No such link name in virtual.json: " +
	// linkName);
	// }

	// int toVmId = getVmId(destVmName);
	// if (toVmId == -1) {
	// throw new IllegalArgumentException("Invalid destination VM name: " +
	// destVmName);
	// }

	// Packet packet = new Packet(fromVmId, toVmId, pktSize, flowId, req); // ⚠
	// Payload = this Request
	// Transmission trans = new Transmission(packet);
	// // Récupère la bande passante à partir du flowId
	// Long bw = flowIdToBandwidthMap.get(flowId); // map de flowId vers BW

	// if (bw == null || bw <= 0) {
	// System.err.println("❌ Bande passante introuvable ou invalide pour FlowID: " +
	// flowId + " sur link: " + linkName);
	// bw = 1_000_000L; // fallback 1 Mbps (ou ce que tu veux comme valeur minimale)
	// }

	// // Attribue la bande passante à la transmission
	// trans.setRequestedBW(bw);

	// req.addActivity(trans);

	// System.out.println(" Added Transmission Activity | FlowID: " + flowId + " |
	// PacketSize: " + pktSize);
	// }

	// return req;

	// } catch (Exception e) {
	// System.err.println(" Failed to parse request: " + e.getMessage());
	// e.printStackTrace();
	// return null;
	// }
	// }

	// private Request parseRequestt(int fromVmId, String linkName, String
	// destVmName, long pktSize, long cloudletLen) {
	// System.out.println("############# parseRequestt");
	// try {
	// // Créer la requête principale
	// Request req = new Request(userId);

	// // Si cloudletLen > 0, générer un Cloudlet et ajouter l'activité Processing
	// dans la requête principale
	// if (cloudletLen > 0) {
	// cloudletLen *= Configuration.CPU_SIZE_MULTIPLY;
	// System.out.println("Adjusted cloudletLen after multiplication: " +
	// cloudletLen);

	// Cloudlet cl = generateCloudlet(req.getRequestId(), fromVmId, (int)
	// cloudletLen);
	// if (cl == null) {
	// throw new IllegalStateException("Cloudlet is null for request ID: " +
	// req.getRequestId());
	// }
	// System.out.println("Generated Cloudlet with ID: " + cl.getCloudletId());

	// // Ajouter directement l'activité Processing dans la requête principale
	// Processing proc = new Processing(cl);
	// req.addActivity(proc);
	// System.out.println("Added Processing activity to Request.");
	// } else {
	// System.out.println("No Cloudlet generated for this request (cloudletLen =
	// 0).");
	// }

	// // Récupérer l'ID du flow à partir du nom du lien
	// Integer flowId = this.flowNames.get(linkName);
	// if (flowId == null) {
	// throw new IllegalArgumentException("No such link name in virtual.json: " +
	// linkName);
	// }
	// System.out.println("Parsed Flow ID: " + flowId + " for Link Name: " +
	// linkName);

	// // Récupérer l'ID de la VM de destination
	// int toVmId = getVmId(destVmName);
	// if (toVmId == -1) {
	// throw new IllegalArgumentException("Invalid destination VM name: " +
	// destVmName);
	// }
	// System.out.println("Parsed Destination VM ID: " + toVmId + " for VM Name: " +
	// destVmName);

	// // Ajuster la taille du paquet
	// if (pktSize <= 0) {
	// throw new IllegalArgumentException("Packet size must be greater than 0.
	// Found: " + pktSize);
	// }
	// pktSize *= Configuration.NETWORK_PACKET_SIZE_MULTIPLY;
	// System.out.println("Adjusted Packet Size after multiplication: " + pktSize);

	// // Créer un Packet avec la requête principale comme payload
	// Packet packet = new Packet(fromVmId, toVmId, pktSize, flowId, req); // Ici,
	// req est le payload
	// if (packet == null) {
	// throw new IllegalStateException("Failed to create Packet in parseRequestt.");
	// }
	// System.out.println("Packet créé : " + packet);

	// // Créer l'activité Transmission avec le Packet
	// Transmission trans = new Transmission(packet);
	// req.addActivity(trans);
	// System.out.println("Added Transmission activity to Request with psize: " +
	// pktSize);

	// return req;
	// } catch (Exception e) {
	// System.err.println("Failed to parse request: " + e.getMessage());
	// e.printStackTrace();
	// return null;
	// }
	// }

	// private Request parseRequestt(int fromVmId, String linkName, String
	// destVmName, long pktSize, long cloudletLen) {
	// try {
	// // Créer une nouvelle requête
	// Request req = new Request(userId);

	// // Générer la cloudlet si cloudletLen > 0
	// if (cloudletLen > 0) {
	// cloudletLen *= Configuration.CPU_SIZE_MULTIPLY;
	// System.out.println("Adjusted cloudletLen after multiplication: " +
	// cloudletLen);

	// Cloudlet cl = generateCloudlet(req.getRequestId(), fromVmId, (int)
	// cloudletLen);
	// if (cl == null) {
	// throw new IllegalStateException("Cloudlet is null for request ID: " +
	// req.getRequestId());
	// }
	// System.out.println("Generated Cloudlet with ID: " + cl.getCloudletId());

	// Processing proc = new Processing(cl);
	// req.addActivity(proc);
	// System.out.println("Added Processing activity to Request.");
	// } else {
	// System.out.println("No Cloudlet generated for this request (cloudletLen =
	// 0).");
	// }

	// // Parse flow ID à partir du nom du lien
	// Integer flowId = this.flowNames.get(linkName);
	// if (flowId == null) {
	// throw new IllegalArgumentException("No such link name in virtual.json: " +
	// linkName);
	// }
	// System.out.println("Parsed Flow ID: " + flowId + " for Link Name: " +
	// linkName);

	// // Parse destVmId
	// int toVmId = getVmId(destVmName);
	// if (toVmId == -1) {
	// throw new IllegalArgumentException("Invalid destination VM name: " +
	// destVmName);
	// }
	// System.out.println("Parsed Destination VM ID: " + toVmId + " for VM Name: " +
	// destVmName);

	// // Ajuster la taille du paquet
	// if (pktSize <= 0) {
	// throw new IllegalArgumentException("Packet size must be greater than 0.
	// Found: " + pktSize);
	// }
	// pktSize *= Configuration.NETWORK_PACKET_SIZE_MULTIPLY;
	// System.out.println("Adjusted Packet Size after multiplication: " + pktSize);

	// // Créer la transmission avec nextReq = null
	// Transmission trans = new Transmission(fromVmId, toVmId, pktSize, flowId,
	// null);
	// req.addActivity(trans);
	// System.out.println("Added Transmission activity to Request with psize: " +
	// pktSize);

	// return req;
	// } catch (Exception e) {
	// System.err.println("Failed to parse request: " + e.getMessage());
	// e.printStackTrace();
	// return null;
	// }
	// }

	private void openFile() {
		System.out.println("openFile");
		try {
			bufReader = new BufferedReader(new FileReader(Configuration.workingDirectory + file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		try {
			@SuppressWarnings("unused")
			String head = bufReader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// private void parseNext(int numRequests) {
	// System.out.println("parseNext");
	// String line;

	// try {
	// while (((line = bufReader.readLine()) != null)
	// && (parsedWorkloads.size() < numRequests)) {
	// System.out.println("parsing:" + line);
	// Workload tr = new Workload(workloadNum++, this.resultWriter);

	// // Diviser la ligne en champs
	// String[] splitLine = line.split(",");
	// Queue<String> lineitems = new LinkedList<String>(Arrays.asList(splitLine));

	// // Extraire les champs dans le bon ordre
	// tr.time = Double.parseDouble(lineitems.poll()); // start
	// String vmName = lineitems.poll(); // source
	// tr.submitVmId = getVmId(vmName); // source (converti en ID)

	// // Passer la file restante à parseRequest
	// tr.request = parseRequest(tr.submitVmId, lineitems);

	// if (tr.request == null) {
	// System.err.println("Request null pour le workload ID: " + tr.workloadId);
	// continue;
	// }

	// parsedWorkloads.add(tr);
	// System.out.println("Workload ID " + tr.workloadId + " ajouté à
	// parsedWorkloads.");
	// }
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// }

	/* MAJ nADIA */
	// private void parseNextt(int numRequests) {
	// System.out.println("Début du parsing des workloads...");
	// String line;

	// try {
	// while (((line = bufReader.readLine()) != null) && (parsedWorkloads.size() <
	// numRequests)) {
	// try {
	// System.out.println("Lecture de la ligne : " + line);

	// // Vérifier si la ligne est vide ou mal formatée
	// if (line.trim().isEmpty()) {
	// System.err.println("Ligne vide ignorée.");
	// continue;
	// }

	// // Séparer la ligne en colonnes
	// String[] splitLine = line.split(",");
	// if (splitLine.length < 9) { // Vérifier si la ligne contient suffisamment de
	// colonnes
	// System.err.println("Ligne mal formatée (9 colonnes attendues) : " + line);
	// continue;
	// }

	// // Créer un nouveau Workload
	// Workload tr = new Workload(workloadNum++, this.resultWriter);

	// // Extraire les champs
	// tr.time = Double.parseDouble(splitLine[0]); // Temps
	// String vmName = splitLine[1]; // Nom de la VM source
	// tr.submitVmId = getVmId(vmName); // ID de la VM source

	// if (tr.submitVmId == -1) {
	// System.err.println("VM ID introuvable pour : " + vmName);
	// continue;
	// }

	// // Extraire les autres champs
	// String linkName = splitLine[3]; // Nom du lien
	// String destVmName = splitLine[4]; // Nom de la VM destination
	// long pktSize = Long.parseLong(splitLine[5]); // Taille du paquet
	// long cloudletLen = Long.parseLong(splitLine[7]); // Longueur du cloudlet

	// // Générer la requête
	// tr.request = parseRequestt(tr.submitVmId, linkName, destVmName, pktSize,
	// cloudletLen);
	// if (tr.request == null) {
	// System.err.println("Request null pour le workload ID : " + tr.workloadId);
	// continue;
	// }

	// // Ajouter le workload à la liste
	// parsedWorkloads.add(tr);
	// System.out.println("Workload ID " + tr.workloadId + " ajouté avec succès !");
	// } catch (Exception e) {
	// System.err.println("Erreur lors du parsing de la ligne : " + line);
	// e.printStackTrace();
	// }
	// }

	// // Vérification finale
	// System.out.println("📊 Total workloads ajoutés : " + parsedWorkloads.size());
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// }
	// private void parseNextt(int numRequests) {
	// System.out.println("Début du parsing des workloads...");
	// String line;

	// try {
	// while (((line = bufReader.readLine()) != null) && (parsedWorkloads.size() <
	// numRequests)) {
	// try {
	// System.out.println("Lecture de la ligne : " + line);
	// Workload tr = new Workload(workloadNum++, this.resultWriter);

	// // Vérifier si la ligne est vide ou mal formatée
	// if (line.trim().isEmpty()) {
	// System.err.println("Ligne vide ignorée.");
	// continue;
	// }

	// // Séparer la ligne en colonnes et créer une file pour parcourir les valeurs
	// String[] splitLine = line.split(",");
	// if (splitLine.length < 9) { // Vérifier si la ligne contient suffisamment de
	// colonnes
	// System.err.println(" Ligne mal formatée : " + line);
	// continue;
	// }
	// Queue<String> lineitems = new LinkedList<>(Arrays.asList(splitLine));

	// // Extraction et conversion des champs
	// tr.time = Double.parseDouble(lineitems.poll());
	// System.out.println("⏳ Temps du workload : " + tr.time);

	// String vmName = lineitems.poll();
	// tr.submitVmId = getVmId(vmName);
	// System.out.println("🖥️ VM Source : " + vmName + " -> ID : " +
	// tr.submitVmId);
	// if (tr.submitVmId == -1) {
	// System.err.println(" VM ID introuvable pour " + vmName);
	// continue;
	// }

	// // Extraction des autres champs
	// lineitems.poll(); // Ignorer `z`
	// lineitems.poll(); // Ignorer `w1`

	// String linkName = lineitems.poll();
	// String destVmName = lineitems.poll();
	// long pktSize = Long.parseLong(lineitems.poll());

	// lineitems.poll(); // Ignorer `w2`
	// long cloudletLen = Long.parseLong(lineitems.poll());

	// System.out.println(" Lien : " + linkName + ", Destination VM : " + destVmName
	// + ", PktSize : " + pktSize + ", CloudletLen : " + cloudletLen);

	// // Génération de la requête
	// tr.request = parseRequestt(tr.submitVmId, linkName, destVmName, pktSize,
	// cloudletLen);
	// if (tr.request == null) {
	// System.err.println("Request null pour le workload ID : " + tr.workloadId);
	// continue;
	// }

	// // Ajouter le workload traité à la liste
	// parsedWorkloads.add(tr);
	// System.out.println(" Workload ID " + tr.workloadId + " ajouté avec succès
	// !");
	// } catch (Exception e) {
	// System.err.println(" Erreur lors du parsing de la ligne : " + line);
	// e.printStackTrace();
	// }
	// }

	// // Vérification finale
	// System.out.println("📊 Total workloads ajoutés : " + parsedWorkloads.size());
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// }

	// public void parseNextt(int numRequests) {
	// System.out.println("parseNextt - Début de la lecture des workloads.");
	// String line;

	// try {
	// while (((line = bufReader.readLine()) != null)
	// && (parsedWorkloads.size() < numRequests)) {
	// try {
	// System.out.println("Lecture de la ligne : " + line);
	// Workload tr = new Workload(workloadNum++, this.resultWriter);

	// String[] splitLine = line.split(",");
	// if (splitLine.length < 9) { // Vérifiez que la ligne contient au moins 9
	// champs
	// System.err.println("Ligne de workload invalide (9 champs attendus) : " +
	// line);
	// continue;
	// }

	// Queue<String> lineitems = new LinkedList<>(Arrays.asList(splitLine));

	// // Parser les champs
	// tr.time = Double.parseDouble(lineitems.poll());
	// if (tr.time < this.forcedStartTime || tr.time > this.forcedFinishTime) {
	// System.out.println("Temps de workload hors plage. Ignoré.");
	// continue;
	// }

	// String vmName = lineitems.poll();
	// tr.submitVmId = getVmId(vmName);
	// if (tr.submitVmId == -1) {
	// System.err.println("VM ID invalide pour le nom de VM : " + vmName);
	// continue;
	// }

	// // Extraire les MIPS du Cloudlet depuis la file `lineitems`
	// //long mipsCloudlet = Long.parseLong(lineitems.poll());
	// //System.out.println("MIPS du Cloudlet : " + mipsCloudlet);

	// long cloudletLen = Long.parseLong(lineitems.poll()); // Lire len_cloudlet
	// String linkName = lineitems.poll();
	// String destVmName = lineitems.poll();
	// long pktSize = Long.parseLong(lineitems.poll());

	// // Calcul dynamique du Processing Delay : cloudletLen (instructions) /
	// mipsCloudlet (vitesse de traitement)
	// // double processingDelay = (cloudletLen > 0 && mipsCloudlet > 0) ?
	// (cloudletLen / (double) mipsCloudlet) : 0;
	// // System.out.println("Processing Delay calculé : " + processingDelay + "s");

	// // Ignorer les champs w1 et w2
	// lineitems.poll(); // w1
	// lineitems.poll(); // w2

	// tr.request = parseRequestt(tr.submitVmId, linkName, destVmName, pktSize,
	// cloudletLen);

	// if (tr.request == null) {
	// System.err.println("Request null pour le workload ID: " + tr.workloadId);
	// continue;
	// }

	// parsedWorkloads.add(tr);
	// System.out.println("Workload ID " + tr.workloadId + " lu avec succès.");
	// } catch (Exception e) {
	// System.err.println("Erreur lors de la lecture de la ligne de workload : " +
	// line);
	// e.printStackTrace();
	// }
	// }

	// // Trier les workloads selon SJF
	// parsedWorkloads.sort(Comparator.comparingDouble(wl ->
	// wl.request.getTotalLength()));
	// System.out.println("Workloads triés selon SJF.");
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// }

	/*
	 * private String getOutputFilename(String filename) {
	 * String ext = LogWriter.getExtension(this.file);
	 * String dir = LogWriter.getPath(this.file);
	 * String name = "result_"+LogWriter.getBaseName(this.file);
	 * System.err.println(dir+"/"+name+"."+ext);
	 * return dir+"/"+name+"."+ext;
	 * }
	 */

	public int getWorkloadNum() {
		return workloadNum;
	}

	// public int getGroupId() {
	// String first_word = this.file.split("_")[0];
	// int groupId = 0;
	// try {
	// groupId = Integer.parseInt(first_word);
	// } catch (NumberFormatException e) {
	// // Do nothing
	// }
	// return groupId;
	// }

	public List<Workload> getCompletedWorkloads() {
		return this.completedWorkloads;
	}

}
