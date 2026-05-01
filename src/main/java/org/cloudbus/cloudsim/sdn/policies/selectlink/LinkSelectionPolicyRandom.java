/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.policies.selectlink;

import java.util.List;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;

public class LinkSelectionPolicyRandom implements LinkSelectionPolicy {

	// Choose a random link regardless of the flow
	int i = 0;

	public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
		return links.get(i++ % links.size());
	}

	@Override
	public List<Link> findBestPath(Node src, Node dest, Packet pkt) {
		if (nos == null)
			return java.util.Collections.emptyList();
		List<Link> links = nos.getLinks(src, dest);
		if (links == null || links.isEmpty())
			return java.util.Collections.emptyList();
		// Pour Random, on peut juste retourner le premier chemin trouvé par le NOS ou
		// un seul lien
		return java.util.Collections.singletonList(links.get(0));
	}

	private NetworkOperatingSystem nos;

	public void setNetworkOperatingSystem(NetworkOperatingSystem nos) {
		this.nos = nos;
	}

	@Override
	public boolean isDynamicRoutingEnabled() {
		return false;
	}
}
