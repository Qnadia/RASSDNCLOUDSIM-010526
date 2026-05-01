package org.cloudbus.cloudsim.sdn.policies.selectlink;

import java.util.*;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.example.LogManager;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;

public class LinkSelectionPolicyDynamicLatencyBw implements LinkSelectionPolicy {

    private final Map<Node, List<Link>> networkTopology;
    private final boolean enableDetailedLogging;

    /** MAJ Nadia : Dproc_switch du dernier chemin optimal sélectionné (secondes) */
    private double lastBestSwitchLatency = 0.0;

    public double getLastBestSwitchLatency() {
        return lastBestSwitchLatency;
    }

    private NetworkOperatingSystem nos;

    public void setNetworkOperatingSystem(NetworkOperatingSystem nos) {
        this.nos = nos;
    }

    public LinkSelectionPolicyDynamicLatencyBw(Map<Node, List<Link>> networkTopology) {
        this(networkTopology, false);
    }

    public LinkSelectionPolicyDynamicLatencyBw(Map<Node, List<Link>> networkTopology, boolean enableDetailedLogging) {
        this.networkTopology = networkTopology;
        this.enableDetailedLogging = enableDetailedLogging;
        Log.printLine("Network topology loaded with " + networkTopology.size() + " nodes.");
    }

    @Override
    public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
        if (links.isEmpty()) {
            Log.printLine(CloudSim.clock() + ": No available links!");
            return null;
        }

        Packet pkt = null;
        if (prevNode instanceof Packet) {
            pkt = (Packet) prevNode;
        }

        List<Link> bestPath = findBestPath(src, dest, pkt);

        return bestPath != null && !bestPath.isEmpty() ? bestPath.get(0) : null;
    }

    public List<Link> findBestPath(Node src, Node dest, Packet pkt) {
        // ⚠️ Cas local : pas besoin de chemin réel
        if (src.equals(dest)) {
            double requiredBw = pkt != null ? (pkt.getSize() * 8.0) / 1.0 : 1.5e9;
            List<Link> localPath = new ArrayList<>();
            localPath.add(new Link(src, dest, requiredBw, 0.0));
            return localPath;
        }

        if (pkt == null) {
            System.err.println("❌ [findBestPath] Packet is null. Cannot calculate path from " + src.getName() + " to "
                    + dest.getName());
            return Collections.emptyList(); // Court-circuiter complètement
        }

        if (enableDetailedLogging) {
            Log.printLine("\n=== Searching best path from " + src.getName() + " to " + dest.getName() + " ===");
        }

        if (src.equals(dest)) {
            return Collections.emptyList();
        }

        List<Link> bestPath = getShortestDynamicPath(src, dest, pkt);

        if (bestPath != null && !bestPath.isEmpty()) {
            PathMetrics metrics = calculatePathMetrics(bestPath, pkt, src);
            lastBestSwitchLatency = metrics.totalSwitchLatency;

            if (pkt != null && pkt.getPayload() instanceof org.cloudbus.cloudsim.sdn.workload.Request) {
                org.cloudbus.cloudsim.sdn.workload.Request req = (org.cloudbus.cloudsim.sdn.workload.Request) pkt
                        .getPayload();
                req.setSwitchProcessingDelay(metrics.totalSwitchLatency);
            }

            if (enableDetailedLogging) {
                String pathId = buildPathId(bestPath, src);
                LogManager.log("path_latency.csv", LogManager.formatData(
                        CloudSim.clock(), src.getName(), dest.getName(), pathId,
                        String.format("%.4f", metrics.minBandwidth / 1e6),
                        String.format("%.4f", metrics.totalLatency * 1000), true));
            }
        }

        return bestPath;
    }

    private String buildPathId(List<Link> path, Node start) {
        StringBuilder sb = new StringBuilder(start.getName());
        Node current = start;
        for (Link link : path) {
            current = link.getOtherNode(current);
            sb.append("->").append(current.getName());
        }
        return sb.toString();
    }

    private PathMetrics calculatePathMetrics(List<Link> path, Packet pkt, Node src) {
        PathMetrics metrics = new PathMetrics();
        long bits = pkt.getSize() * 8L;
        metrics.minBandwidth = Double.POSITIVE_INFINITY;

        for (Link link : path) {
            double bw = link.getBw(src);
            double usedBw = link.getUsedBw(src); // channels actifs en cours de tx

            // ✅ FIX BUG-02 — si aucun channel actif sur ce lien, utiliser la BW
            // réservée par les virtual links déjà déployés comme proxy de charge.
            // Sans ça : rho=0 systématiquement au premier routage → M/M/1 inactif.
            if (usedBw == 0.0) {
                usedBw = link.getAllocatedBandwidthForDedicatedChannels(src);
            }

            metrics.minBandwidth = Math.min(metrics.minBandwidth, bw);

            double linkLatency = link.getLatency() * 0.001;
            double propDelay = link.calculateDynamicPropagationDelay();
            double txDelay = bits / (bw * link.getEfficiency());

            double dqueueEst = 0;
            if (bw > 0) {
                double rho = Math.min(usedBw / bw, 0.99);

                if (rho > 0) {
                    double mu = bw / Math.max(pkt.getSize(), 1);
                    dqueueEst = rho / (mu * (1.0 - rho));
                    System.out.printf("  [M/M/1 ACTIF]  %s→%s | rho=%.3f | Dqueue=%.4fs%n",
                            src.getName(), link.getOtherNode(src).getName(), rho, dqueueEst);
                } else {
                    System.out.printf("  [M/M/1 INACTIF] %s→%s | usedBw=%.0f | bw=%.0f%n",
                            src.getName(), link.getOtherNode(src).getName(), usedBw, bw);
                }
            }

            metrics.totalLatency += linkLatency + propDelay + txDelay + dqueueEst;
            metrics.totalSwitchLatency += linkLatency;
            src = link.getOtherNode(src);
        }

        return metrics;
    }

    private boolean isBetterPath(PathMetrics metrics, double currentMinLatency, double currentMaxBandwidth) {
        return metrics.totalLatency < currentMinLatency - 1e-6 ||
                (Math.abs(metrics.totalLatency - currentMinLatency) < 1e-6 &&
                        metrics.minBandwidth > currentMaxBandwidth + 1e-6);
    }

    private void logPathDetails(List<Link> path, PathMetrics metrics, Node src) {
        Log.printLine(String.format(
                "Path %s | Latency: %.3f ms, Bandwidth: %.1f Mbps",
                formatPath(path, src),
                metrics.totalLatency * 1000,
                metrics.minBandwidth / 1e6));
    }

    private void logSelectedPath(List<Link> path, double latency, double bandwidth, Packet pkt, Node src) {
        Log.printLine("\n=== Selected Best Path ===");
        Log.printLine(String.format(
                "Total Latency: %.3f ms | Min Bandwidth: %.1f Mbps",
                latency * 1000,
                bandwidth / 1e6));

        Log.printLine("Detailed path:");
        for (Link link : path) {
            double bw = link.getBw(src);
            double propDelay = link.calculateDynamicPropagationDelay() / 1000;
            double txDelay = (pkt.getSize() * 8.0) / (bw * link.getEfficiency());

            Log.printLine(String.format(
                    "  %s → %s | BW: %.1f Mbps | Latency: %.3f ms (Prop: %.3f ms + Tx: %.3f ms)",
                    link.getHighOrder().getName(),
                    link.getLowOrder().getName(),
                    bw / 1e6,
                    (link.getLatency() + propDelay + txDelay) * 1000,
                    propDelay * 1000,
                    txDelay * 1000));

            src = link.getOtherNode(src);
        }
    }

    private String formatPath(List<Link> path, Node start) {
        StringBuilder sb = new StringBuilder(start.getName());
        Node current = start;

        for (Link link : path) {
            current = link.getOtherNode(current);
            sb.append(" → ").append(current.getName());
        }

        return sb.toString();
    }

    private class NodeDistance implements Comparable<NodeDistance> {
        Node node;
        double distance;

        public NodeDistance(Node node, double distance) {
            this.node = node;
            this.distance = distance;
        }

        @Override
        public int compareTo(NodeDistance other) {
            return Double.compare(this.distance, other.distance);
        }
    }

    private List<Link> getShortestDynamicPath(Node src, Node dest, Packet pkt) {
        Map<Node, Double> distances = new HashMap<>();
        Map<Node, Link> previousLink = new HashMap<>();
        Map<Node, Node> previousNode = new HashMap<>();
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>();

        for (Node n : networkTopology.keySet()) {
            distances.put(n, Double.POSITIVE_INFINITY);
        }
        distances.put(src, 0.0);
        pq.add(new NodeDistance(src, 0.0));

        long bits = (pkt != null) ? pkt.getSize() * 8L : 12000000000L;
        long pktSize = (pkt != null) ? pkt.getSize() : 1500000000L;

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            Node u = current.node;

            if (u.equals(dest)) {
                break; // Destination atteinte !
            }

            if (current.distance > distances.get(u))
                continue;

            List<Link> neighbors = networkTopology.getOrDefault(u, Collections.emptyList());
            for (Link link : neighbors) {
                Node v = link.getOtherNode(u);

                // Interdire les liens entre deux hôtes
                if (isHost(u) && isHost(v))
                    continue;

                double bw = link.getBw(u);
                if (bw <= 0)
                    continue; // Lien mort

                double usedBw = link.getUsedBw(u);
                double linkLatency = link.getLatency() * 0.001;
                double propDelay = link.calculateDynamicPropagationDelay();
                double txDelay = bits / (bw * link.getEfficiency());

                double dqueueEst = 0;
                double rho = Math.min(usedBw / bw, 0.99);
                if (rho > 0) {
                    double mu = bw / Math.max(pktSize, 1);
                    dqueueEst = rho / (mu * (1.0 - rho));
                }

                double weight = linkLatency + propDelay + txDelay + dqueueEst;
                double newDist = distances.get(u) + weight;

                if (newDist < distances.get(v)) {
                    distances.put(v, newDist);
                    previousLink.put(v, link);
                    previousNode.put(v, u);
                    pq.add(new NodeDistance(v, newDist));
                }
            }
        }

        // Reconstruction du chemin
        List<Link> path = new ArrayList<>();
        Node curr = dest;
        while (curr != null && !curr.equals(src)) {
            Link l = previousLink.get(curr);
            if (l == null)
                return Collections.emptyList(); // Aucun chemin possible
            path.add(l);
            curr = previousNode.get(curr);
        }
        Collections.reverse(path);
        return path;
    }

    private boolean isHost(Node node) {
        return node.getName().startsWith("h_");
    }

    @Override
    public boolean isDynamicRoutingEnabled() {
        return true;
    }

    public double getPathLatency(List<Link> path, Packet pkt, Node src) {
        return calculatePathMetrics(path, pkt, src).totalLatency;
    }

    private static class PathMetrics {
        double totalLatency = 0;
        double minBandwidth = 0;
        double totalSwitchLatency = 0; // MAJ Nadia : Dproc_switch = somme des link.getLatency() du chemin
        double totalUsedBandwidth;
        double avgUtilPercent;
    }

    public double getMinBandwidth(List<Link> path) {
        return path.stream()
                .mapToDouble(Link::getBw)
                .min()
                .orElse(0);
    }
}
