/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies.selectlink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;

public class LinkSelectionPolicyFirst implements LinkSelectionPolicy {

    private NetworkOperatingSystem nos;

    public void setNetworkOperatingSystem(NetworkOperatingSystem nos) {
        this.nos = nos;
    }

    @Override
    public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
        if (links == null || links.isEmpty()) {
            return null;
        }
        Link chosen = links.get(0);
        // System.out.println("DEBUG [First]: Selecting link from " + (prevNode != null ? prevNode.getName() : "null") + " to " + (dest != null ? dest.getName() : "null") + " with BW: " + chosen.getBw());
        return chosen;
    }

    @Override
    public List<Link> findBestPath(Node src, Node dest, Packet pkt) {
        if (src == null || dest == null) {
            return Collections.emptyList();
        }
        if (src.equals(dest)) {
            return Collections.emptyList();
        }

        if (nos == null) {
            Log.printLine("LinkSelectionPolicyFirst: NOS reference is null; cannot find path.");
            return Collections.emptyList();
        }
        Map<Node, List<Link>> adj = nos.getNetworkTopology();
        if (adj == null || adj.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Node, Node> parentNode = new HashMap<>();
        Map<Node, Link> parentLink = new HashMap<>();

        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();

        parentNode.put(src, src);
        visited.add(src);
        queue.add(src);

        boolean found = false;

        bfs: while (!queue.isEmpty()) {
            Node current = queue.poll();

            List<Link> adjLinks = adj.get(current);
            if (adjLinks == null || adjLinks.isEmpty()) {
                continue;
            }

            Map<Node, List<Link>> linksByNeighbour = new HashMap<>();
            for (Link link : adjLinks) {
                Node neighbour = link.getOtherNode(current);
                if (neighbour == null || neighbour.equals(current)) {
                    continue;
                }
                linksByNeighbour.computeIfAbsent(neighbour, k -> new ArrayList<>()).add(link);
            }

            for (Map.Entry<Node, List<Link>> entry : linksByNeighbour.entrySet()) {
                Node neighbour = entry.getKey();
                List<Link> parallel = entry.getValue();

                if (visited.contains(neighbour)) {
                    continue;
                }

                if (neighbour instanceof SDNHost && !neighbour.equals(dest)) {
                    continue;
                }

                Link chosen = selectLink(parallel, pkt.getFlowId(), current, neighbour, current);
                if (chosen == null) {
                    continue;
                }

                visited.add(neighbour);
                parentNode.put(neighbour, current);
                parentLink.put(neighbour, chosen);

                if (neighbour.equals(dest)) {
                    found = true;
                    break bfs;
                }
                queue.add(neighbour);
            }
        }

        if (!found) {
            Log.printLine("LinkSelectionPolicyFirst: BFS found no path from "
                    + src.getName() + " to " + dest.getName());
            return Collections.emptyList();
        }

        List<Link> path = new ArrayList<>();
        Node step = dest;
        while (!step.equals(src)) {
            Link l = parentLink.get(step);
            if (l == null) {
                Log.printLine("LinkSelectionPolicyFirst: reconstruction failed at " + step.getName());
                return Collections.emptyList();
            }
            path.add(l);
            step = parentNode.get(step);
            if (step == null) {
                return Collections.emptyList();
            }
        }

        Collections.reverse(path);
        return path;
    }

    @Override
    public boolean isDynamicRoutingEnabled() {
        return false;
    }
}