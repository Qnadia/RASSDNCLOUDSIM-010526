package org.cloudbus.cloudsim.sdn.policies.wfallocation.PSO;


import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.SDNBroker;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.policies.wfallocation.WorkloadSchedulerPolicy;
import org.cloudbus.cloudsim.sdn.workload.Request;
import org.cloudbus.cloudsim.sdn.workload.Transmission;
import org.cloudbus.cloudsim.sdn.workload.Workload;

import java.util.*;

public class PSOWorkloadScheduler implements WorkloadSchedulerPolicy {

    private final int swarmSize, maxIterations;
    private final double w, c1, c2;
    private final Map<String, Double> netDelayCache = new HashMap<>();

    public PSOWorkloadScheduler(int swarmSize, int maxIterations,
                               double inertia, double cognitive, double social) {
        this.swarmSize     = swarmSize;
        this.maxIterations = maxIterations;
        this.w             = inertia;
        this.c1            = cognitive;
        this.c2            = social;
    }

    @Override
    public List<Workload> sort(List<Workload> workloads) {
        System.out.println("▶▶▶ [PSO] sort() called with " + workloads.size() + " workloads");
        int n = workloads.size();
        if (n <= 1) return workloads;

        // 1) init swarm
        List<Particle> swarm = new ArrayList<>();
        for (int i = 0; i < swarmSize; i++) {
            swarm.add(new Particle(n));
        }
        // 2) init pbest & gbest
        double globalBestFitness = Double.POSITIVE_INFINITY;
        int[] globalBestPosition = null;
        for (Particle p : swarm) {
            double fit = fitness(p.position, workloads);
            p.pbestFitness = fit;
            p.pbest = p.position.clone();
            if (fit < globalBestFitness) {
                globalBestFitness = fit;
                globalBestPosition = p.position.clone();
            }
        }
        // 3) main loop
        for (int iter = 0; iter < maxIterations; iter++) {
            for (Particle p : swarm) {
                p.updateVelocity(globalBestPosition, w, c1, c2);
                p.applyVelocity();
                double fit = fitness(p.position, workloads);
                if (fit < p.pbestFitness) {
                    p.pbestFitness = fit;
                    p.pbest = p.position.clone();
                }
                if (fit < globalBestFitness) {
                    globalBestFitness = fit;
                    globalBestPosition = p.position.clone();
                }
            }
        }
        // 4) reorder
        List<Workload> sorted = reorder(workloads, globalBestPosition);
       // 5) Adjust submission times to reflect PSO ordering
       double baseTime = workloads.get(0).time;
       double delta = 0.01; // 10ms interval between submissions
       for (int i = 0; i < sorted.size(); i++) {
           sorted.get(i).time = baseTime + delta * i;
       }

       return sorted;
    }

    @Override
    public String getName() {
        return "PSO";
    }

    /**
     * Fitness = average total latency = CPU-processing + network-transmission + propagation + queuing.
     * On s’appuie ici sur les méthodes SDNHost.calculateProcessingDelay(), Transmission.getServeTime()
     * et Request.getPropagationDelay().
     */
  private double fitness(int[] order, List<Workload> wl) {
    double sumLatency   = 0;
    double queueingDelay = 0;

    for (int idx : order) {
        Workload w   = wl.get(idx);
        Request req  = w.getRequest();
        long cloudletLen = req.getLastProcessingCloudletLen();
        int  vmId       = req.getLastProcessingVmId();

        // 1) CPU delay
        SDNDatacenter dc = SDNBroker.globalVmDatacenterMap.get(vmId);
        SDNHost       host = (SDNHost)dc.getVmAllocationPolicy().getHost(vmId, req.getUserId());
        double cpuDelay = host.calculateProcessingDelay(cloudletLen, vmId);

        // 2) Network & propagation via calculatePathMetrics()
        double netDelay = 0;
        Transmission tr = req.getLastTransmission();
        if (tr != null && tr.getPacket() != null) {
            Packet pkt = tr.getPacket();
            // reconstruire srcNode / dstNode
            NetworkOperatingSystem nos = dc.getNOS();
            Node srcNode = nos.getNodeById(host.getId());

            int dstVmId = pkt.getDestination();
            SDNHost dstHost = (SDNHost)dc.getVmAllocationPolicy().getHost(dstVmId, req.getUserId());
            Node dstNode = nos.getNodeById(dstHost.getId());

            // Cache key: srcNodeName-dstNodeName
            String cacheKey = srcNode.getName() + "-" + dstNode.getName();
            
            if (netDelayCache.containsKey(cacheKey)) {
                netDelay = netDelayCache.get(cacheKey);
            } else {
                // trouver le chemin
                List<Link> path = nos.getLinkSelectionPolicy().findBestPath(srcNode, dstNode, pkt);
                // calculer métriques réseau
                PathMetrics pm = calculatePathMetrics(path, pkt, srcNode);
                netDelay = pm.totalLatency;
                netDelayCache.put(cacheKey, netDelay);
            }
        }

        // 3) latence totale pour ce workload
        double thisLatency = cpuDelay + netDelay + queueingDelay;
        sumLatency += thisLatency;

        // 4) incrément queueing pour le prochain
        queueingDelay += (cpuDelay + netDelay);
    }

    return sumLatency / order.length;
}

/**
 * Récupère vos métriques de chemin existantes :
 */
private PathMetrics calculatePathMetrics(List<Link> path, Packet pkt, Node src) {
    PathMetrics metrics = new PathMetrics();
    long bits = pkt.getSize() * 8L;

    metrics.minBandwidth = Double.MAX_VALUE;
    metrics.totalLatency = 0;

    for (Link link : path) {
        double bw        = link.getBw(src);
        double propDelay = link.calculateDynamicPropagationDelay() / 1000.0; // en secondes
        double txDelay   = bits / (bw * link.getEfficiency());
        double linkLat   = link.getLatency();

        // mettre à jour métriques agrégées
        metrics.minBandwidth = Math.min(metrics.minBandwidth, bw);
        metrics.totalLatency += linkLat + propDelay + txDelay;

        // préparer prochain saut
        src = link.getOtherNode(src);
    }
    return metrics;
}

    private List<Workload> reorder(List<Workload> wl, int[] pos) {
        List<Workload> sorted = new ArrayList<>(wl.size());
        for (int i : pos) sorted.add(wl.get(i));
        return sorted;
    }

    // --- Particle interne inchangé ---
    private static class Particle {
        int[] position, pbest;
        List<Swap> velocity = new ArrayList<>();
        double pbestFitness = Double.POSITIVE_INFINITY;
        Particle(int n) { position = randomPerm(n); pbest = position.clone(); }
        private static int[] randomPerm(int n) { List<Integer> t=new ArrayList<>(); for(int i=0;i<n;i++)t.add(i); Collections.shuffle(t); int[] p=new int[n]; for(int i=0;i<n;i++)p[i]=t.get(i); return p; }
        void updateVelocity(int[] gbest, double w, double c1, double c2) {
            List<Swap> v = new ArrayList<>();
            for (Swap s: velocity) if (Math.random()<w) v.add(s);
            v.addAll(diff(position,pbest,c1));
            v.addAll(diff(position,gbest,c2));
            velocity=v;
        }
        void applyVelocity() {
            for (Swap s: velocity) {
                int tmp=position[s.i]; position[s.i]=position[s.j]; position[s.j]=tmp;
            }
        }
        private static List<Swap> diff(int[] src,int[] dst,double fact){
            List<Swap> l=new ArrayList<>();
            int n=src.length, m=(int)(fact*n);
            for(int k=0;k<m;k++){
                int i=(int)(Math.random()*n);
                int j=indexOf(dst,src[i]);
                l.add(new Swap(i,j));
            }
            return l;
        }
        private static int indexOf(int[] a,int v){for(int i=0;i<a.length;i++)if(a[i]==v)return i;return -1;}
    }
    private static class Swap { final int i,j; Swap(int i,int j){this.i=i;this.j=j;} }
}

