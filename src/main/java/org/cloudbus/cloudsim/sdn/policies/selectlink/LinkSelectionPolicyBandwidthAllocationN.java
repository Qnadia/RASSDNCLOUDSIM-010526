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

/**
 * Link-selection policy based on available bandwidth allocation.
 * Selects the link with the highest free bandwidth for routing a flow.
 * Path discovery uses BFS over the physical topology exposed by the NOS,
 * which guarantees loop-free traversal of Fat-Tree topology.
 *
 * @since CloudSimSDN 1.0
 */
public class LinkSelectionPolicyBandwidthAllocationN implements LinkSelectionPolicy {

	private NetworkOperatingSystem nos;

	public void setNetworkOperatingSystem(NetworkOperatingSystem nos) {
		this.nos = nos;
	}

	/**
	 * Selects the link with the highest available bandwidth among the candidates.
	 *
	 * @param links    List of candidate links
	 * @param flowId   Flow identifier
	 * @param src      Source node (unused here, kept for interface compatibility)
	 * @param dest     Destination node (unused here, kept for interface
	 *                 compatibility)
	 * @param prevNode Node from which we are departing on this link (used to query
	 *                 free BW direction)
	 * @return the selected link, or null if the list is null/empty
	 */
	@Override
	public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
		if (links == null || links.isEmpty()) {
			return null;
		}

		if (links.size() == 1) {
			return links.get(0);
		}

		Link selectedLink = null;
		double maxBw = Double.MIN_VALUE;

		for (Link link : links) {
			double bw = link.getFreeBandwidth(prevNode);
			if (bw > maxBw) {
				maxBw = bw;
				selectedLink = link;
			}
		}

		if (selectedLink != null) {
			// System.out.println("DEBUG [BwAllocN]: Selecting link from " + prevNode.getName() + " with max BW: " + selectedLink.getBw());
		}
		return selectedLink;
	}

	/**
	 * Finds a loop-free path from {@code src} to {@code dest} using BFS over
	 * the physical topology.
	 *
	 * <p>
	 * The BFS proceeds via {@link Node#getAdjacentLinks()} which is populated
	 * by {@code PhysicalTopology.addLink()} at topology-construction time and
	 * therefore reflects the complete Fat-Tree graph. When multiple parallel
	 * links exist between two adjacent nodes, {@link #selectLink} picks the one
	 * with the highest available bandwidth.
	 *
	 * <p>
	 * A {@link HashSet} of visited nodes ensures we never revisit a node,
	 * preventing routing loops regardless of topology shape.
	 *
	 * @param src  source node
	 * @param dest destination node
	 * @param pkt  packet being routed (flowId is passed through to selectLink)
	 * @return ordered list of {@link Link} objects from src to dest, or an empty
	 *         list when no path exists
	 */
	@Override
	public List<Link> findBestPath(Node src, Node dest, Packet pkt) {
		if (src == null || dest == null) {
			return Collections.emptyList();
		}
		if (src.equals(dest)) {
			return Collections.emptyList();
		}

		// Build adjacency map from the NOS physical topology.
		// NOS.getNetworkTopology() returns Map<Node, List<Link>> built from
		// PhysicalTopology.nodeLinks so it mirrors getAdjacentLinks(Node) exactly.
		if (nos == null) {
			Log.printLine("LinkSelectionPolicyBandwidthAllocationN: NOS reference is null; cannot find path.");
			return Collections.emptyList();
		}
		Map<Node, List<Link>> adj = nos.getNetworkTopology();
		if (adj == null || adj.isEmpty()) {
			return Collections.emptyList();
		}

		// BFS bookkeeping: parent maps let us reconstruct the winning path
		Map<Node, Node> parentNode = new HashMap<>();
		Map<Node, Link> parentLink = new HashMap<>();

		Set<Node> visited = new HashSet<>();
		Queue<Node> queue = new LinkedList<>();

		// Seed BFS at src
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

			// Group parallel links by neighbour so we can call selectLink per group
			Map<Node, List<Link>> linksByNeighbour = new HashMap<>();
			for (Link link : adjLinks) {
				Node neighbour = link.getOtherNode(current);
				if (neighbour == null || neighbour.equals(current)) {
					continue; // skip self-loops / loopback links
				}
				linksByNeighbour.computeIfAbsent(neighbour, k -> new ArrayList<>()).add(link);
			}

			for (Map.Entry<Node, List<Link>> entry : linksByNeighbour.entrySet()) {
				Node neighbour = entry.getKey();
				List<Link> parallel = entry.getValue();

				if (visited.contains(neighbour)) {
					continue;
				}

				// MAJ Nadia/Antigravity: Prohibit host-jumping.
				// A path can only traverse switches. If a neighbour is a Host,
				// it must be the final destination to be considered.
				if (neighbour instanceof SDNHost && !neighbour.equals(dest)) {
					continue;
				}

				// Pick the highest-bandwidth link to this neighbour
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
			Log.printLine("LinkSelectionPolicyBandwidthAllocationN: BFS found no path from "
					+ src.getName() + " to " + dest.getName());
			return Collections.emptyList();
		}

		// Reconstruct path by walking parent maps from dest back to src, then reverse
		List<Link> path = new ArrayList<>();
		Node step = dest;
		while (!step.equals(src)) {
			Link l = parentLink.get(step);
			if (l == null) {
				Log.printLine("LinkSelectionPolicyBandwidthAllocationN: reconstruction failed at "
						+ step.getName());
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
		return true;
	}
}
