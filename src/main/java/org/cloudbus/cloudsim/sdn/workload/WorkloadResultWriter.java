package org.cloudbus.cloudsim.sdn.workload;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.example.LogPrinter;

public class WorkloadResultWriter {
    private boolean headPrinted = false;
    private String filename;
    private PrintWriter out = null; // Utiliser PrintWriter directement

    // Pour les statistiques
    private double totalServeTime;    // Temps total de service
    private double cpuServeTime;      // Temps CPU uniquement
    private double networkServeTime;  // Temps réseau uniquement

    private int printedWorkloadNum;       // Nombre de workloads imprimés
    private int overNum;                  // Nombre de workloads dépassant le temps estimé
    private int timeoutNum = 0;           // Nombre de workloads ayant expiré

    private int cloudletNum;              // Nombre de cloudlets
    private int cloudletOverNum;          // Nombre de cloudlets dépassant le temps estimé
    private int transmissionNum;          // Nombre de transmissions
    private int transmissionOverNum;      // Nombre de transmissions dépassant le temps estimé
    private DecimalFormat df = new DecimalFormat();

    // Taille du buffer pour l'écriture des workloads
    private static final int workloadBufferSize = 1;
    private List<Workload> workloadBuffer = new ArrayList<>(workloadBufferSize);

    // Définir le séparateur comme une constante
    private static final String SEPARATOR = "|";

    public WorkloadResultWriter(String file) throws IOException {
        df.setMaximumFractionDigits(3);
        df.setGroupingUsed(false);

        this.filename = file;
        out = new PrintWriter(new java.io.FileWriter(filename, true)); // Utilisation de PrintWriter directement

        // Avec PrintWriter, 'out' ne devrait jamais être null
    }

    public void writeResult(Workload wl) {
        System.out.println("🟢 [Writer] Ajout de workload ID: " + wl.getActivities());
        // Et vérifie bien qu'il ajoute aux compteurs internes
       

        printWorkloadBuffer(wl);
    }

    private void printWorkloadBuffer(Workload wl) {
        System.out.println("printWorkloadBuffer");
        workloadBuffer.add(wl);
        if (workloadBuffer.size() >= workloadBufferSize)
            flushWorkloadBuffer();
    }

    private void flushWorkloadBuffer() {
        System.out.println("flushWorkloadBuffer");
        for (Workload wl : workloadBuffer) {
            printWorkload(wl);
        }
        workloadBuffer.clear();
    }

    public void printWorkload(Workload wl) {
        System.out.println("printWorkload");
        if (!headPrinted) {
            this.printHead(wl);
            headPrinted = true;
        }

        double serveTime;

        printDetailInt(wl.workloadId);
        printDetailInt(wl.appId);
        printDetailFloat(wl.time);

        if (wl.failed) {
            System.out.println("Workload ID: " + wl.workloadId + " failed? " + wl.failed);
            System.out.println("Activities: " + wl.getActivities());
            System.out.println("Request: " + wl.request);
            System.out.println("RemovedActivities: " + wl.request.getRemovedActivities());
            System.out.println("AllActivities: " + getAllActivities(wl.request));

            printRequest(wl.request, false);

            printDetail("TimeOut");
            printDetail("\n");

            this.timeoutNum++;
        } else {
            printRequest(wl.request, true);

            serveTime = getWorkloadFinishTime(wl) - getWorkloadStartTime(wl);

            // Utiliser println pour terminer la ligne
            printDetail(String.format("%.3f", serveTime));
            printDetail(SEPARATOR + "\n");

            this.totalServeTime += serveTime;

            if (isOverTime(wl, serveTime)) {
                overNum++;
            }
            printedWorkloadNum++;
        }
    }

    // private void printRequestTitle(Request req) {
    //     System.out.println("######### printRequestTitle");
    //     List<Activity> acts = req.getRemovedActivities();
    //     for (Activity act : acts) {
    //         if (act instanceof Transmission) {
    //             Transmission tr = (Transmission) act;

    //             if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
    //                 print("Tr:StartTime" + SEPARATOR); // Utiliser le séparateur |
    //                 print("Tr:EndTime" + SEPARATOR);
    //             }

    //             print("Tr:NetworkTime" + SEPARATOR);

    //             if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
    //                 print("Tr:Size" + SEPARATOR);
    //                 print("Tr:Channel" + SEPARATOR);
    //             }

    //             printRequestTitle(tr.getPacket().getPayload());
    //         } else {
    //             if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
    //                 print("Pr:StartTime" + SEPARATOR);
    //                 print("Pr:EndTime" + SEPARATOR);
    //             }

    //             print("Pr:CPUTime" + SEPARATOR);

    //             if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
    //                 print("Pr:Size" + SEPARATOR);
    //             }

    //         }
    //     }
    // }

    /* MAJ Nadia */
    private void printRequestTitle(Request req) {
        System.out.println("######### printRequestTitle");
        
    
        if (req == null) {
            System.err.println("⚠️ Request is null! Skipping...");
            return;
        }
    
        List<Activity> acts = req.getRemovedActivities();
    
        if (acts == null || acts.isEmpty()) {
            System.out.println("⚠️ No activities in Request ID: " + req.getRequestId());
            return;
        }
    
        for (Activity act : acts) {
    
            if (act instanceof Transmission) {
                Transmission tr = (Transmission) act;
    
                if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
                    print("Tr:StartTime" + SEPARATOR);
                    print("Tr:EndTime" + SEPARATOR);
                }
    
                print("Tr:NetworkTime" + SEPARATOR);
    
                if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
                    print("Tr:Size" + SEPARATOR);
                    print("Tr:Channel" + SEPARATOR);
                }
    
                // 🔴 NE PAS RAPPELER printRequestTitle sur le payload !!!
                Request payload = tr.getPacket().getPayload();
    
                if (payload != null) {
                    print("Payload Req ID: " + payload.getRequestId() + SEPARATOR);
                    print("Payload User ID: " + payload.getUserId() + SEPARATOR);
    
                    // Facultatif : afficher ses activités (si tu veux)
                    List<Activity> payloadActs = payload.getActivities();
                    if (payloadActs != null && !payloadActs.isEmpty()) {
                        print("Payload Activities: ");
                        for (Activity payloadAct : payloadActs) {
                            print(payloadAct.toString() + SEPARATOR);
                        }
                    } else {
                        print("Payload has no activities" + SEPARATOR);
                    }
    
                } else {
                    print("No payload" + SEPARATOR);
                }
    
            } else if (act instanceof Processing) {
    
                if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
                    print("Pr:StartTime" + SEPARATOR);
                    print("Pr:EndTime" + SEPARATOR);
                }
    
                print("Pr:CPUTime" + SEPARATOR);
    
                if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
                    print("Pr:Size" + SEPARATOR);
                }
            }
    
            System.out.println(); // passer à la ligne à la fin de chaque activité
        }
    }
    

    private void printRequest(Request req, boolean includeStatistics) {
        printRequest(req, includeStatistics, new HashSet<>());
    }

    private void printRequest(Request req, boolean includeStatistics, Set<Request> processedRequests) {
        if (processedRequests.contains(req)) {
            // Skip this request to avoid infinite recursion
            return;
        }
        processedRequests.add(req);

        System.out.println("############# printRequest");
        List<Activity> acts = req.getRemovedActivities();

        // ✅ Fallback si removedActivities est vide
        if (acts == null || acts.isEmpty()) {
            acts = req.getActivities();
        }

        for (Activity act : acts) {
            if (act instanceof Transmission) {
                Transmission tr = (Transmission) act;
                double serveTime = tr.getServeTime();

                if (includeStatistics) {
                    networkServeTime += serveTime;
                    transmissionNum++;

                    if (isOverTime(tr))
                        transmissionOverNum++;
                }

                if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
                    printDetailFloat(tr.getStartTime());
                    printDetailFloat(tr.getFinishTime());
                }

                printDetailFloat(serveTime);

                if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
                    printDetailInt(tr.getPacket().getSize());
                    printDetailInt(tr.getPacket().getFlowId());
                }

                // ✅ Traverse le payload (Request) s’il existe
                printRequest(tr.getPacket().getPayload(), includeStatistics, processedRequests);

            } else if (act instanceof Processing) {
                Processing pr = (Processing) act;
                double serveTime = pr.getServeTime();

                if (includeStatistics) {
                    cpuServeTime += serveTime;
                    cloudletNum++;

                    if (isOverTime(pr))
                        cloudletOverNum++;
                }

                if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
                    printDetailFloat(pr.getStartTime());
                    printDetailFloat(pr.getFinishTime());
                }

                printDetailFloat(serveTime);

                if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
                    printDetailInt(pr.cloudletTotalLength);
                }
            }
        }
    }
    // private void printRequest(Request req, boolean includeStatistics) {
    //     List<Activity> acts = req.getRemovedActivities();
    //     for (Activity act : acts) {
    //         if (act instanceof Transmission) {
    //             Transmission tr = (Transmission) act;
    //             double serveTime = tr.getServeTime();

    //             if (includeStatistics) {
    //                 networkServeTime += serveTime;
    //                 transmissionNum++;

    //                 if (isOverTime(tr))
    //                     transmissionOverNum++;
    //             }

    //             if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
    //                 printDetailFloat(tr.getStartTime());   // Start time
    //                 printDetailFloat(tr.getFinishTime());  // Finish time
    //             }

    //             printDetailFloat(serveTime); // Network processing time

    //             if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
    //                 printDetailInt(tr.getPacket().getSize());     // Size
    //                 printDetailInt(tr.getPacket().getFlowId());  // Channel #
    //             }
    //             printRequest(tr.getPacket().getPayload(), includeStatistics);
    //         } else {
    //             Processing pr = (Processing) act;
    //             double serveTime = pr.getServeTime();

    //             if (includeStatistics) {
    //                 cpuServeTime += serveTime;
    //                 cloudletNum++;
    //                 if (isOverTime(pr))
    //                     cloudletOverNum++;
    //             }

    //             if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
    //                 printDetailFloat(pr.getStartTime());   // Start time
    //                 printDetailFloat(pr.getFinishTime());  // Finish time
    //             }
    //             printDetailFloat(serveTime); // Processing time
    //             if (Configuration.DEBUG_PRINT_DETAIL_SIZE_TIME) {
    //                 printDetailInt(pr.cloudletTotalLength); // Size
    //             }
    //         }
    //     }
    // }

    private void printHead(Workload sample) {
        print("Workload_ID" + SEPARATOR);
        print("App_ID" + SEPARATOR);
        print("SubmitTime" + SEPARATOR);
        printRequestTitle(sample.request);
        print("ResponseTime");
        printLine();
    }

    public void printWorkloadList(List<Workload> wls) {
        for (Workload wl : wls) {
            printWorkload(wl);
        }
    }

    public void printStatistics() {
        flushWorkloadBuffer();

        printLine("#======================================");
        printLine("#Number of workloads:" + printedWorkloadNum);
        printLine("#Timeout workloads:" + timeoutNum);
        if (timeoutNum + printedWorkloadNum != 0)
            printLine("#Timeout workloads per cent:" + ((double) timeoutNum / (timeoutNum + printedWorkloadNum)));

        printLine("#Over workloads:" + overNum);
        if (printedWorkloadNum != 0)
            printLine("#Over workloads per cent:" + ((double) overNum / printedWorkloadNum));
        printLine("#Number of Cloudlets:" + cloudletNum);
        printLine("#Over Cloudlets:" + cloudletOverNum);
        if (cloudletNum != 0)
            printLine("#Over Cloudlets per cent:" + ((double) cloudletOverNum / cloudletNum));
        printLine("#Number of transmissions:" + transmissionNum);
        printLine("#Over transmissions:" + transmissionOverNum);
        if (transmissionNum != 0)
            printLine("#Over transmissions per cent:" + ((double) transmissionOverNum / transmissionNum));
        printLine("#======================================");
        printLine("#Total serve time:" + totalServeTime);
        printLine("#CPU serve time:" + cpuServeTime);
        printLine("#Network serve time:" + networkServeTime);
        if (printedWorkloadNum != 0) {
            printLine("#Average total serve time:" + (totalServeTime / printedWorkloadNum));
            printLine("#Average CPU serve time per workload:" + (cpuServeTime / printedWorkloadNum));
            printLine("#Average network serve time per workload:" + (networkServeTime / printedWorkloadNum));
        }
        if (cloudletNum != 0)
            printLine("#Average CPU serve time per Cloudlet:" + (cpuServeTime / cloudletNum));
        if (transmissionNum != 0)
            printLine("#Average network serve time per transmission:" + (networkServeTime / transmissionNum));
    }

    // Méthodes utilitaires pour l'écriture des détails
    protected void printDetailInt(long l) {
        printDetail(l + SEPARATOR); // Utiliser le séparateur |
    }

    protected void printDetailFloat(double f) {
        printDetail(df.format(f) + SEPARATOR); // Utiliser le séparateur |
    }

    protected void printDetail(String s) {
        if (Configuration.DEBUG_RESULT_WRITE_DETAIL)
            out.print(s);
    }

    protected void printLine() {
        out.println(); // Remplacer printLine() par println()
    }

    protected void print(String s) {
        out.print(s);
    }

    protected void printLine(String s) {
        out.println(s); // Remplacer printLine(s) par println(s)
    }

    // Autres méthodes restantes...
    public int getWorklaodNum() {
        return printedWorkloadNum;
    }

    public int getTimeoutNum() {
        return timeoutNum;
    }

    public int getWorklaodNumOvertime() {
        return overNum;
    }

    public int getWorklaodNumCPU() {
        return cloudletNum;
    }

    public int getWorklaodNumCPUOvertime() {
        return cloudletOverNum;
    }

    public int getWorklaodNumNetwork() {
        return transmissionNum;
    }

    public int getWorklaodNumNetworkOvertime() {
        return transmissionOverNum;
    }

    public double getServeTime() {
        return totalServeTime;
    }

    public double getServeTimeCPU() {
        return cpuServeTime;
    }

    public double getServeTimeNetwork() {
        return networkServeTime;
    }

    public static double getWorkloadStartTime(Workload w) {
        System.out.println("getWorkloadStartTime");
        List<Activity> acts = getAllActivities(w.request);

        for (Activity act : acts) {
            if (act instanceof Processing) {
                return act.getStartTime();
            }
        }
        return -1;
    }

    public static double getWorkloadFinishTime(Workload w) {
        double finishTime = -1;

        if (w.failed)
            return finishTime;

        List<Activity> acts = getAllActivities(w.request);

        for (Activity act : acts) {
            if (act instanceof Processing) {
                finishTime = act.getFinishTime();
            }
        }
        return finishTime;
    }

    private boolean isOverTime(final Activity ac) {
        double serveTime = ac.getServeTime();
        double expectedTime = getExpectedTime(ac);

        if (serveTime > expectedTime * Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR) {
            // SLA violated. Served too late.
            return true;
        }
        return false;
    }

    private boolean isOverTime(final Workload wl, double serveTime) {
        if (Configuration.DEBUG_CHECK_OVER_TIME_REQUESTS) {
            double expectedTime = 0;
            List<Activity> acts = getAllActivities(wl.request);

            for (Activity ac : acts) {
                expectedTime += getExpectedTime(ac);
            }

            if (serveTime > expectedTime * Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR) {
                // SLA violated. Served too late.
                return true;
            }
            return false;
        }

        return false;
    }

    private double getExpectedTime(Activity ac) {
        double expectedTime = ac.getExpectedTime();

        if (ac instanceof Transmission) {
            if (expectedTime < CloudSim.getMinTimeBetweenEvents())
                expectedTime = CloudSim.getMinTimeBetweenEvents();
        } else {
            if (expectedTime < CloudSim.getMinTimeBetweenEvents())
                expectedTime = CloudSim.getMinTimeBetweenEvents();
        }
        return expectedTime;
    }
    /* MAJ Nadia  */
    private static List<Activity> getAllActivities(Request req) {
        return getAllActivities(req, new HashSet<>());
    }
    
    private static List<Activity> getAllActivities(Request req, Set<Request> processedRequests) {
        List<Activity> outputActList = new ArrayList<>();
    
        // If the request has already been processed, return an empty list to avoid infinite recursion
        if (processedRequests.contains(req)) {
            return outputActList;
        }
        processedRequests.add(req); // Mark this request as processed
    
        // Récupérer d'abord les activités retirées
        List<Activity> acts = req.getRemovedActivities();
        // Si la liste est vide, alors on récupère la liste complète
        if (acts == null || acts.isEmpty()) {
            acts = req.getActivities();
        }
    
        for (Activity act : acts) {
            outputActList.add(act);
            if (act instanceof Transmission) {
                // Recursively process the payload, passing the set of processed requests
                outputActList.addAll(getAllActivities(((Transmission) act).getPacket().getPayload(), processedRequests));
            }
        }
        return outputActList;
    }
    // private static List<Activity> getAllActivities(Request req) {
    //     List<Activity> outputActList = new ArrayList<Activity>();
    //     // Récupérer d'abord les activités retirées
    //     List<Activity> acts = req.getRemovedActivities();
    //     // Si la liste est vide, alors on récupère la liste complète
    //     if (acts == null || acts.isEmpty()) {
    //         acts = req.getActivities();
    //     }
    //     for (Activity act : acts) {
    //         outputActList.add(act);
    //         if (act instanceof Transmission) {
    //             getAllActivities(((Transmission) act).getPacket().getPayload(), outputActList);
    //         }
    //     }
    //     return outputActList;
    // }
    
    private static void getAllActivities(Request req, List<Activity> outputActList) {
        List<Activity> acts = req.getRemovedActivities();
        if (acts == null || acts.isEmpty()) {
            acts = req.getActivities();
        }
        for (Activity act : acts) {
            outputActList.add(act);
            if (act instanceof Transmission) {
                getAllActivities(((Transmission) act).getPacket().getPayload(), outputActList);
            }
        }
    }
    
    
    // private static List<Activity> getAllActivities(Request req) {
    //     List<Activity> outputActList = new ArrayList<Activity>();
    //     getAllActivities(req, outputActList);
    //     return outputActList;
    // }

    // private static void getAllActivities(Request req, List<Activity> outputActList) {
    //     List<Activity> acts = req.getRemovedActivities();
    //     for (Activity act : acts) {
    //         outputActList.add(act);
    //         if (act instanceof Transmission) {
    //             getAllActivities(((Transmission) act).getPacket().getPayload(), outputActList);
    //         }
    //     }
    // }

    // Méthode close()
    public void close() {
        if (out != null) {
            out.close();
        }
    }
}
