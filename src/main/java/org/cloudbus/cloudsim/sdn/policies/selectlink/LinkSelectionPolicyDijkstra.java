package org.cloudbus.cloudsim.sdn.policies.selectlink;

import java.util.*;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;

/**
 * Dijkstra-based link selection policy to avoid StackOverflowError in large topologies.
 * Finds the path with minimum dynamic latency, breaking ties with maximum bandwidth.
 */
public class LinkSelectionPolicyDijkstra implements LinkSelectionPolicy {

    private final Map<Node, List<Link>> networkTopology;
    private NetworkOperatingSystem nos;
    private double lastBestSwitchLatency = 0.0;

    public LinkSelectionPolicyDijkstra(Map<Node, List<Link>> networkTopology) {
        this.networkTopology = networkTopology;
    }

    public void setNetworkOperatingSystem(NetworkOperatingSystem nos) {
        this.nos = nos;
    }

    public double getLastBestSwitchLatency() {
        return lastBestSwitchLatency;
    }

    @Override
    public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
        if (links.isEmpty()) return null;

        Packet pkt = (prevNode instanceof Packet) ? (Packet) prevNode : null;
        List<Link> bestPath = findBestPath(src, dest, pkt);

        return (bestPath != null && !bestPath.isEmpty()) ? bestPath.get(0) : null;
    }

    @Override
    public List<Link> findBestPath(Node src, Node dest, Packet pkt) {
        if (src.equals(dest)) {
            return Collections.emptyList();
        }
        if (pkt == null) return Collections.emptyList();

        // Dijkstra state
        Map<Node, Double> minLatency = new HashMap<>();
        Map<Node, Double> maxBandwidth = new HashMap<>();
        Map<Node, Link> parentLink = new HashMap<>();
        Map<Node, Node> parentNode = new HashMap<>();
        PriorityQueue<NodePathState> pq = new PriorityQueue<>();

        pq.add(new NodePathState(src, 0.0, Double.POSITIVE_INFINITY, 0.0));
        minLatency.put(src, 0.0);
        maxBandwidth.put(src, Double.POSITIVE_INFINITY);

        NodePathState bestDestState = null;

        while (!pq.isEmpty()) {
            NodePathState current = pq.poll();

            if (current.latency > minLatency.getOrDefault(current.node, Double.POSITIVE_INFINITY)) continue;
            
            if (current.node.equals(dest)) {
                bestDestState = current;
                break; 
            }

            for (Link link : networkTopology.getOrDefault(current.node, Collections.emptyList())) {
                Node next = link.getOtherNode(current.node);
                
                // Avoid redundant host-to-host links unless it's the destination
                if (isHost(current.node) && isHost(next) && !next.equals(dest)) continue;

                // Calculate edge metrics
                double edgeLatency = calculateEdgeLatency(link, pkt, current.node);
                double totalLat = current.latency + edgeLatency;
                double edgeBw = link.getBw(current.node);
                double pathBw = Math.min(current.minBw, edgeBw);
                double totalSwitchLat = current.switchLatency + (link.getLatency() * 0.001);

                if (totalLat < minLatency.getOrDefault(next, Double.POSITIVE_INFINITY) - 1e-9 ||
                   (Math.abs(totalLat - minLatency.getOrDefault(next, 0.0)) < 1e-9 && pathBw > maxBandwidth.getOrDefault(next, 0.0) + 1e-9)) {
                    
                    minLatency.put(next, totalLat);
                    maxBandwidth.put(next, pathBw);
                    parentLink.put(next, link);
                    parentNode.put(next, current.node);
                    pq.add(new NodePathState(next, totalLat, pathBw, totalSwitchLat));
                }
            }
        }

        if (bestDestState == null) return Collections.emptyList();

        // Reconstruct path
        this.lastBestSwitchLatency = bestDestState.switchLatency;
        
        // Update request if available
        if (pkt.getPayload() instanceof org.cloudbus.cloudsim.sdn.workload.Request) {
            ((org.cloudbus.cloudsim.sdn.workload.Request) pkt.getPayload()).setSwitchProcessingDelay(this.lastBestSwitchLatency);
        }

        List<Link> path = new ArrayList<>();
        Node step = dest;
        while (!step.equals(src)) {
            path.add(parentLink.get(step));
            step = parentNode.get(step);
        }
        Collections.reverse(path);
        return path;
    }

    private double calculateEdgeLatency(Link link, Packet pkt, Node src) {
        long bits = pkt.getSize() * 8L;
        double bw = link.getBw(src);
        double usedBw = link.getUsedBw(src);

        double linkLatency = link.getLatency() * 0.001;
        double propDelay = link.calculateDynamicPropagationDelay();
        double txDelay = bits / (bw * link.getEfficiency());

        double dqueueEst = 0;
        if (bw > 0) {
            double rho = Math.min(usedBw / bw, 0.99);
            if (rho > 0) {
                double mu = bw / Math.max(pkt.getSize(), 1);
                dqueueEst = rho / (mu * (1.0 - rho));
            }
        }
        return linkLatency + propDelay + txDelay + dqueueEst;
    }

    private boolean isHost(Node node) {
        return node.getName().startsWith("h_");
    }

    @Override
    public boolean isDynamicRoutingEnabled() {
        return true;
    }

    private static class NodePathState implements Comparable<NodePathState> {
        Node node;
        double latency;
        double minBw;
        double switchLatency;

        NodePathState(Node node, double latency, double minBw, double switchLatency) {
            this.node = node;
            this.latency = latency;
            this.minBw = minBw;
            this.switchLatency = switchLatency;
        }

        @Override
        public int compareTo(NodePathState other) {
            if (this.latency < other.latency - 1e-9) return -1;
            if (this.latency > other.latency + 1e-9) return 1;
            return Double.compare(other.minBw, this.minBw); // Higher BW first
        }
    }
}
