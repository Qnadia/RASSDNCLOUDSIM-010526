package org.cloudbus.cloudsim.sdn.virtualcomponents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;



/* Nadia  */

public class VirtualNetworkMapper {
	
	protected NetworkOperatingSystem nos;
	protected LinkSelectionPolicy linkSelector;
	
	public VirtualNetworkMapper(NetworkOperatingSystem nos) {
		this.nos = nos;
	}
	
	public void setLinkSelectionPolicy(LinkSelectionPolicy linkSelectionPolicy) {
		System.out.println("################## setLinkSelectionPolicy 2");
		this.linkSelector = linkSelectionPolicy;
	}

	public boolean buildForwardingTable(int srcVm, int dstVm, int flowId) {
		System.out.println("################## buildForwardingTable");
		SDNHost srchost = (SDNHost)nos.findHost(srcVm);
		SDNHost dsthost = (SDNHost)nos.findHost(dstVm);
		if(srchost == null || dsthost == null) {
			System.err.println(CloudSim.clock() + "############## Cannot find src VM ("+srcVm+":"+srchost+") or dst VM ("+dstVm+":"+dsthost+")");
			return false;
		}
		
		if(srchost.equals(dsthost)) {
			Log.printLine(CloudSim.clock() + ": Source SDN Host is same as Destination. Go loopback");
			srchost.addVMRoute(srcVm, dstVm, flowId, dsthost);
		}
		else {
			Log.printLine(CloudSim.clock() + ": VMs are in different hosts:" + srchost + "(" + srcVm + ")->" + dsthost + "(" + dstVm + ")");
			System.out.println(CloudSim.clock() + ": VMs are in different hosts:" + srchost + "(" + srcVm + ")->" + dsthost + "(" + dstVm + ")");
			
			Set<Node> visitedNodes = new HashSet<>(); // Initialiser l'ensemble des nœuds visités
			boolean findRoute = buildForwardingTableRec(srchost, srcVm, dstVm, flowId, visitedNodes);
			
			if(!findRoute) {
				System.err.println("NetworkOperatingSystem.deployFlow: Could not find route!!" + 
						NetworkOperatingSystem.getVmName(srcVm) + "->" + NetworkOperatingSystem.getVmName(dstVm));
				return false;
			}
		}
		return true;
	}

	protected boolean buildForwardingTableRec(Node node, int srcVm, int dstVm, int flowId, Set<Node> visitedNodes) {
		System.out.println("################## buildForwardingTableRec - Start");
		System.out.println("Current Node: " + node.getName());
		System.out.println("Visited Nodes: " + visitedNodes);
	
		if (visitedNodes.contains(node)) {
			System.out.println("Cycle detected at node: " + node.getName());
			return false;
		}
	
		visitedNodes.add(node);
	
		SDNHost desthost = nos.findHost(dstVm);
		if(node.equals(desthost)) {
			System.out.println("Destination reached: " + node.getName());
			return true;
		}
	
		List<Link> nextLinkCandidates = node.getRoute(desthost);
		System.out.println("Next Link Candidates: " + nextLinkCandidates);
	
		if(nextLinkCandidates == null || nextLinkCandidates.isEmpty()) {
			System.out.println("No next links found for node: " + node.getName());
			throw new RuntimeException("Cannot find next links for the flow:" + srcVm + "->" + dstVm + "(" + flowId + ") for node=" + node + ", dest node=" + desthost);
		}
	
		List<Link> filteredLinks = nextLinkCandidates.stream()
			.filter(link -> {
				Node adjacentNode = link.getOtherNode(node);
				return !adjacentNode.equals(node) && link.getBw() > 0;
			})
			.collect(Collectors.toList());
	
		System.out.println("Filtered Links: " + filteredLinks);
	
		if(filteredLinks.isEmpty()) {
			System.out.println("No valid links found for node: " + node.getName());
			return false;
		}
	
		Link nextLink = linkSelector.selectLink(filteredLinks, flowId, nos.findHost(srcVm), desthost, node);
		System.out.println("Selected Link: " + nextLink);
	
		if (nextLink == null) {
			System.out.println("No suitable link selected for node: " + node.getName());
			return false;
		}
	
		Node nextHop = nextLink.getOtherNode(node);
		System.out.println("Next Hop: " + nextHop.getName());
	
		if (visitedNodes.contains(nextHop)) {
			System.out.println("Cycle detected at next hop: " + nextHop.getName());
			return false;
		}
	
		System.out.println("Adding VM Route from " + node.getName() + " to " + nextHop.getName());
		node.addVMRoute(srcVm, dstVm, flowId, nextHop);
	
		boolean result = buildForwardingTableRec(nextHop, srcVm, dstVm, flowId, visitedNodes);
		System.out.println("################## buildForwardingTableRec - End: " + result);
		return result;
	}
	// protected boolean buildForwardingTableRec(Node node, int srcVm, int dstVm, int flowId, Set<Node> visitedNodes) {
	// 	System.out.println("################## buildForwardingTableRec");
	// 	// Vérifier si le nœud courant a déjà été visité
	// 	if (visitedNodes.contains(node)) {
	// 		Log.printLine(CloudSim.clock() + ": Node " + node.getAddress() + " already visited. Skipping to avoid cycle.");
	// 		return false; // Cycle détecté, arrêter la récursion
	// 	}
		
	// 	// Ajouter le nœud courant aux nœuds visités
	// 	visitedNodes.add(node);
		
	// 	SDNHost desthost = nos.findHost(dstVm);
	// 	if(node.equals(desthost)) {
	// 		Log.printLine(CloudSim.clock() + ": Destination node " + node.getName() + " reached.");
	// 		return true;
	// 	}
		
	// 	List<Link> nextLinkCandidates = node.getRoute(desthost);
		
	// 	if(nextLinkCandidates == null || nextLinkCandidates.isEmpty()) {
	// 		throw new RuntimeException("Cannot find next links for the flow:" + srcVm + "->" + dstVm + "(" + flowId + ") for node=" + node + ", dest node=" + desthost);
	// 	}
		
	// 	// Filtrer les liens de boucle locale et avec upBW > 0
	// 	List<Link> filteredLinks = nextLinkCandidates.stream()
	// 		.filter(link -> {
	// 			Node adjacentNode = link.getOtherNode(node);
	// 			return !adjacentNode.equals(node) && link.getBw() > 0;
	// 		})
	// 		.collect(Collectors.toList());
		
	// 	if(filteredLinks.isEmpty()) {
	// 		Log.printLine(CloudSim.clock() + ": All next links are invalid for the flow:" + srcVm + "->" + dstVm + "(" + flowId + ") at node=" + node + ", dest node=" + desthost);
	// 		return false; // Aucun lien valide trouvé
	// 	}
		
	// 	// Choisir quel lien suivre
	// 	Link nextLink = linkSelector.selectLink(filteredLinks, flowId, nos.findHost(srcVm), desthost, node);
	// 	System.out.println("VirtualNetworkMapper: appel de selectLink avec " + filteredLinks.size() + " liens candidats.");

	// 	if (nextLink == null) {
	// 		Log.printLine(CloudSim.clock() + ": LinkSelector could not find a suitable link for the flow:" + srcVm + "->" + dstVm + "(" + flowId + ") at node=" + node + ", dest node=" + desthost);
	// 		return false; // Aucun lien approprié trouvé
	// 	}
		
	// 	Node nextHop = nextLink.getOtherNode(node);
		
	// 	// Vérifier si le prochain saut a déjà été visité
	// 	if (visitedNodes.contains(nextHop)) {
	// 		Log.printLine(CloudSim.clock() + ": Cycle detected when selecting link for the flow:" + srcVm + "->" + dstVm + "(" + flowId + ") at node=" + node + ", nextHop=" + nextHop);
	// 		return false; // Cycle détecté
	// 	}
		
	// 	Log.printLine(CloudSim.clock() + ": Selecting link " + nextLink + " from node " + node.getName() + " to next hop " + nextHop.getName());
		
	// 	node.addVMRoute(srcVm, dstVm, flowId, nextHop);
	// 	boolean result = buildForwardingTableRec(nextHop, srcVm, dstVm, flowId, visitedNodes);
		
	// 	return result;
	// }

	public boolean updateDynamicForwardingTableRec(Node node, int srcVm, int dstVm, int flowId, boolean isNewRoute) {
		System.out.println("updateDynamicForwardingTableRec");
		if(!linkSelector.isDynamicRoutingEnabled())
			return false;
		
		Set<Node> visitedNodes = new HashSet<>();
		return updateDynamicForwardingTableRec(node, srcVm, dstVm, flowId, isNewRoute, visitedNodes);
	}
	
	protected boolean updateDynamicForwardingTableRec(Node node, int srcVm, int dstVm, int flowId, boolean isNewRoute, Set<Node> visitedNodes) {
		System.out.println("updateDynamicForwardingTableRec");
		// Vérifier si le nœud courant a déjà été visité
		if (visitedNodes.contains(node)) {
			Log.printLine(CloudSim.clock() + ": Node " + node.getName() + " already visited during dynamic update. Skipping to avoid cycle.");
			return false; // Cycle détecté, arrêter la récursion
		}
		
		// Ajouter le nœud courant aux nœuds visités
		visitedNodes.add(node);
		
		// There are multiple links. Determine which hop to go.
		SDNHost desthost = nos.findHost(dstVm);
		if(node.equals(desthost))
			return false;	// Nothing changed
		
		List<Link> nextLinkCandidates = node.getRoute(desthost);
		
		if(nextLinkCandidates == null || nextLinkCandidates.isEmpty()) {
			throw new RuntimeException("Cannot find next links for the flow:" + srcVm + "->" + dstVm + "(" + flowId + ") for node=" + node + ", dest node=" + desthost);
		}
		
		// Filtrer les liens de boucle locale et avec upBW > 0
		List<Link> filteredLinks = nextLinkCandidates.stream()
			.filter(link -> {
				Node adjacentNode = link.getOtherNode(node);
				return !adjacentNode.equals(node) && link.getBw() > 0;
			})
			.collect(Collectors.toList());
		
		if(filteredLinks.isEmpty()) {
			Log.printLine(CloudSim.clock() + ": All next links are invalid for the flow:" + srcVm + "->" + dstVm + "(" + flowId + ") at node=" + node + ", dest node=" + desthost);
			return false; // Aucun lien valide trouvé
		}
		
		// Choisir quel lien suivre
		Link nextLink = linkSelector.selectLink(filteredLinks, flowId, nos.findHost(srcVm), desthost, node);
		
		if (nextLink == null) {
			Log.printLine(CloudSim.clock() + ": LinkSelector could not find a suitable link for the flow:" + srcVm + "->" + dstVm + "(" + flowId + ") at node=" + node + ", dest node=" + desthost);
			return false; // Aucun lien approprié trouvé
		}
		
		Node nextHop = nextLink.getOtherNode(node);
		
		// Vérifier si le prochain saut a déjà été visité
		if (visitedNodes.contains(nextHop)) {
			Log.printLine(CloudSim.clock() + ": Cycle detected during dynamic update for the flow:" + srcVm + "->" + dstVm + "(" + flowId + ") at node=" + node + ", nextHop=" + nextHop);
			return false; // Cycle détecté
		}
		
		Log.printLine(CloudSim.clock() + ": Selecting link " + nextLink + " from node " + node.getName() + " to next hop " + nextHop.getName());
		
		Node oldNextHop = node.getVMRoute(srcVm, dstVm, flowId);
		if(isNewRoute || !nextHop.equals(oldNextHop)) {
			// Créer une nouvelle route
			node.addVMRoute(srcVm, dstVm, flowId, nextHop);
			
			// Appel récursif avec suivi des nœuds visités
			boolean updated = updateDynamicForwardingTableRec(nextHop, srcVm, dstVm, flowId, true, visitedNodes);
			return updated;
		}
		else {
			// Rien n'a changé pour ce nœud.
			boolean updated = updateDynamicForwardingTableRec(nextHop, srcVm, dstVm, flowId, false, visitedNodes);
			return updated;
		}
	}
	
	/**
	 * Gets the list of nodes and links that a channel will pass through.
	 * 
	 * @param src source VM id
	 * @param dst destination VM id
	 * @param flowId flow id
	 * @param srcNode source node (host of src VM)
	 * @param nodes empty list to get return of the nodes on the route
	 * @param links empty list to get return of the links on the route
	 * @return none
	 * @pre $none
	 * @post $none
	 */
	public void buildNodesLinks(int src, int dst, int flowId, Node srcNode,
			List<Node> nodes, List<Link> links) {
		
		System.out.println("buildNodesLinks");
		
		// Build the list of nodes and links that this channel passes through
		Node origin = srcNode;
		Node dest = origin.getVMRoute(src, dst, flowId);
		
		if(dest == null) {
			System.err.println("buildNodesLinks() Cannot find dest!");
			return;	
		}

		nodes.add(origin);

		while(dest != null) {
			Link link = origin.getLinkTo(dest);
			if(link == null)
				throw new IllegalArgumentException("Link is NULL for srcNode:" + origin + " -> dstNode:" + dest);
			
			links.add(link);
			nodes.add(dest);
			
			if(dest instanceof SDNHost)
				break;
			
			origin = dest;
			dest = origin.getVMRoute(src, dst, flowId);
		}
	}

	// This function rebuilds the forwarding table only for the specific VM
	public void rebuildForwardingTable(int srcVmId, int dstVmId, int flowId, Node srcHost) {
		System.out.println("rebuildForwardingTable");
		// Remove the old routes.
		List<Node> oldNodes = new ArrayList<Node>();
		List<Link> oldLinks = new ArrayList<Link>();
		buildNodesLinks(srcVmId, dstVmId, flowId, srcHost, oldNodes, oldLinks);
		
		for(Node node : oldNodes) {
			//System.err.println("Removing routes for: "+node + "("+arc+")");
			node.removeVMRoute(srcVmId, dstVmId, flowId);
		}
		
		// Build a forwarding table for the new route.
		if(buildForwardingTable(srcVmId, dstVmId, flowId) == false) {
			System.err.println("NetworkOperatingSystem.processVmMigrate: cannot build a new forwarding table!!");
			System.exit(0);
		}
	}
}


// package org.cloudbus.cloudsim.sdn.virtualcomponents;

// import java.util.ArrayList;
// import java.util.List;

// import org.cloudbus.cloudsim.Log;
// import org.cloudbus.cloudsim.core.CloudSim;
// import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
// import org.cloudbus.cloudsim.sdn.physicalcomponents.Link;
// import org.cloudbus.cloudsim.sdn.physicalcomponents.Node;
// import org.cloudbus.cloudsim.sdn.physicalcomponents.SDNHost;
// import org.cloudbus.cloudsim.sdn.policies.selectlink.LinkSelectionPolicy;

// public class VirtualNetworkMapper {
	
// 	protected NetworkOperatingSystem nos;
// 	protected LinkSelectionPolicy linkSelector;
	
// 	public VirtualNetworkMapper(NetworkOperatingSystem nos) {
// 		this.nos = nos;
// 	}
	
// 	public void setLinkSelectionPolicy(LinkSelectionPolicy linkSelectionPolicy) {
// 		this.linkSelector = linkSelectionPolicy;
// 	}

// 	public boolean buildForwardingTable(int srcVm, int dstVm, int flowId) {
// 		SDNHost srchost = (SDNHost)nos.findHost(srcVm);
// 		SDNHost dsthost = (SDNHost)nos.findHost(dstVm);
// 		if(srchost == null || dsthost == null) {
// 			//System.err.println(CloudSim.clock() + ": " + getName() + ": Cannot find src VM ("+srcVm+":"+srchost+") or dst VM ("+dstVm+":"+dsthost+")");
// 			return false;
// 		}
		
// 		if(srchost.equals(dsthost)) {
// 			Log.printLine(CloudSim.clock() + ": Source SDN Host is same as Destination. Go loopback");
// 			srchost.addVMRoute(srcVm, dstVm, flowId, dsthost);
// 		}
// 		else {
// 			Log.printLine(CloudSim.clock() + ": VMs are in different hosts:"+ srchost+ "("+srcVm+")->"+dsthost+"("+dstVm+")");
// 			boolean findRoute = buildForwardingTableRec(srchost, srcVm, dstVm, flowId);
			
// 			if(!findRoute) {
// 				System.err.println("NetworkOperatingSystem.deployFlow: Could not find route!!" + 
// 						NetworkOperatingSystem.getVmName(srcVm) + "->"+NetworkOperatingSystem.getVmName(dstVm));
// 				return false;
// 			}
// 		}
// 		return true;
// 	}

// 	protected boolean buildForwardingTableRec(Node node, int srcVm, int dstVm, int flowId) {
// 		// There are multiple links. Determine which hop to go.
// 		SDNHost desthost = nos.findHost(dstVm);
// 		if(node.equals(desthost))
// 			return true;
		
// 		List<Link> nextLinkCandidates = node.getRoute(desthost);
		
// 		if(nextLinkCandidates == null) {
// 			throw new RuntimeException("Cannot find next links for the flow:"+srcVm+"->"+dstVm+"("+flowId+") for node="+node+", dest node="+desthost);
// 		}
		
// 		// Choose which link to follow
// 		Link nextLink = linkSelector.selectLink(nextLinkCandidates, flowId, nos.findHost(srcVm), desthost, node);
// 		Node nextHop = nextLink.getOtherNode(node);
		
// 		node.addVMRoute(srcVm, dstVm, flowId, nextHop);
// 		buildForwardingTableRec(nextHop, srcVm, dstVm, flowId);
		
// 		return true;
// 	}

// 	public boolean updateDynamicForwardingTableRec(Node node, int srcVm, int dstVm, int flowId, boolean isNewRoute) {
// 		if(!linkSelector.isDynamicRoutingEnabled())
// 			return false;
		
// 		// There are multiple links. Determine which hop to go.
// 		SDNHost desthost = nos.findHost(dstVm);
// 		if(node.equals(desthost))
// 			return false;	// Nothing changed
		
// 		List<Link> nextLinkCandidates = node.getRoute(desthost);
		
// 		if(nextLinkCandidates == null) {
// 			throw new RuntimeException("Cannot find next links for the flow:"+srcVm+"->"+dstVm+"("+flowId+") for node="+node+", dest node="+desthost);
// 		}
		
// 		// Choose which link to follow
// 		Link nextLink = linkSelector.selectLink(nextLinkCandidates, flowId, nos.findHost(srcVm), desthost, node);
// 		Node nextHop = nextLink.getOtherNode(node);
		
// 		Node oldNextHop = node.getVMRoute(srcVm, dstVm, flowId);
// 		if(isNewRoute || !nextHop.equals(oldNextHop)) {
// 			// Create a new route
// 			//node.removeVMRoute(srcVm, dstVm, flowId);
// 			node.addVMRoute(srcVm, dstVm, flowId, nextHop);
// 			//Log.printLine(CloudSim.clock() + ": " + getName() + ": Updating VM route for flow:"+srcVm+"->"+dstVm+"("+flowId+") From="+node+", Old="+oldNextHop+", New="+nextHop);
			
// 			updateDynamicForwardingTableRec(nextHop, srcVm, dstVm, flowId, true);
// 			return true;
// 		}
// 		else {
// 			// Nothing changed for this node.
// 			return updateDynamicForwardingTableRec(nextHop, srcVm, dstVm, flowId, false);
// 		}
// 	}
	
	
// 	/**
// 	 * Gets the list of nodes and links that a channel will pass through.
// 	 * 
// 	 * @param src source VM id
// 	 * @param dst destination VM id
// 	 * @param flowId flow id
// 	 * @param srcNode source node (host of src VM)
// 	 * @param nodes empty list to get return of the nodes on the route
// 	 * @param links empty list to get return of the links on the route
// 	 * @return none
// 	 * @pre $none
// 	 * @post $none
// 	 */
// 	public void buildNodesLinks(int src, int dst, int flowId, Node srcNode,
// 			List<Node> nodes, List<Link> links) {
		
// 		// Build the list of nodes and links that this channel passes through
// 		Node origin = srcNode;
// 		Node dest = origin.getVMRoute(src, dst, flowId);
		
// 		if(dest==null) {
// 			System.err.println("buildNodesLinks() Cannot find dest!");
// 			return;	
// 		}
	
// 		nodes.add(origin);
	
// 		while(dest != null) {
// 			Link link = origin.getLinkTo(dest);
// 			if(link == null)
// 				throw new IllegalArgumentException("Link is NULL for srcNode:"+origin+" -> dstNode:"+dest);
			
// 			links.add(link);
// 			nodes.add(dest);
			
// 			if(dest instanceof SDNHost)
// 				break;
			
// 			origin = dest;
// 			dest = origin.getVMRoute(src, dst, flowId);
// 		}
// 	}

// 	// This function rebuilds the forwarding table only for the specific VM
// 	public void rebuildForwardingTable(int srcVmId, int dstVmId, int flowId, Node srcHost) {
// 		// Remove the old routes.
// 		List<Node> oldNodes = new ArrayList<Node>();
// 		List<Link> oldLinks = new ArrayList<Link>();
// 		buildNodesLinks(srcVmId, dstVmId, flowId, srcHost, oldNodes, oldLinks);
		
// 		for(Node node:oldNodes) {
// 			//System.err.println("Removing routes for: "+node + "("+arc+")");
// 			node.removeVMRoute(srcVmId, dstVmId, flowId);
// 		}
		
// 		// Build a forwarding table for the new route.
// 		if(buildForwardingTable(srcVmId, dstVmId, flowId) == false) {
// 			System.err.println("NetworkOperatingSystem.processVmMigrate: cannot build a new forwarding table!!");
// 			System.exit(0);
// 		}
// 	}

// }
