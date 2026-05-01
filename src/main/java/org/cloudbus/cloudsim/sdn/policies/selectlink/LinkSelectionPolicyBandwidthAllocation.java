/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies.selectlink;

import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;

public class LinkSelectionPolicyBandwidthAllocation implements LinkSelectionPolicy {

	private NetworkOperatingSystem nos;

	public void setNetworkOperatingSystem(NetworkOperatingSystem nos) {
		this.nos = nos;
	}

	public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
		if (links.size() == 1) {
			return links.get(0);
		}
		
		int numLinks = links.size();
		// Choix du lien initial :
		int linkid = dest.getAddress() % numLinks;
		Link link = links.get(linkid);

		// Choose the least full one.
		for (Link l : links) {
			int linkCn = link.getChannelCount(prevNode);
			int lCn = l.getChannelCount(prevNode);
			
			if (lCn < linkCn) {
				link = l;
			}
		}
		
		// Log diagnostic pour Fix #3
		if(link != null) {
			double util = link.getUtilizationPercent(prevNode);
			if(util >= 0.9) {
				Log.printLine(String.format("%f: [BwAlloc-WARN] High congestion on selected link %s -> %s (Util: %.2f%%, Channels: %d)", 
						CloudSim.clock(), prevNode.getName(), link.getOtherNode(prevNode).getName(), util * 100, link.getChannelCount(prevNode)));
			}
		}
		
		return link;
	}

	@Override
	public List<Link> findBestPath(Node src, Node dest, Packet pkt) {
		List<Link> links = nos.getLinks(src, dest);
		if (links == null || links.isEmpty()) {
			return java.util.Collections.emptyList();
		}
		Link best = selectLink(links, pkt.getFlowId(), src, dest, src);
		return java.util.Collections.singletonList(best);
	}

	@Override
	public boolean isDynamicRoutingEnabled() {
		return false;
	}
}
