/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.physicalcomponents;

import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.nos.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.AggregationSwitch;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.CoreSwitch;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.EdgeSwitch;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.GatewaySwitch;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.IntercloudSwitch;
import org.cloudbus.cloudsim.sdn.physicalcomponents.switches.Switch;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;


/**
 * Network connection maps including switches, hosts, and links between them in physical layer.
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public abstract class PhysicalTopology {
	public enum NodeType {
		Intercloud,
		Gateway,
		Core,
		Aggr,
		Edge,
		Host,
	};
	protected final int RANK_INTERCLOUD = 5; // This switch type belongs to multiple Datacenters / network operators
	protected final int RANK_GATEWAY = 10; // This switch type belongs to multiple Datacenters / network operators
	protected final int RANK_CORE = 100;
	protected final int RANK_AGGR = 200;
	protected final int RANK_EDGE = 300;
	protected final int RANK_HOST = 1000;
	
	protected Hashtable<Integer,Node> nodesTable;	// Address -> Node
	//protected Table<Integer, Integer, Link> linkTable; 	// From : To -> Link
	/**Nadia */
	protected Multimap<Integer, Link> linkTable; // From -> Links

	protected Multimap<Node,Link> nodeLinks;	// Node -> all Links

	public PhysicalTopology() {
		nodesTable = new Hashtable<Integer,Node>();
		nodeLinks = LinkedHashMultimap.create();
		//linkTable = HashBasedTable.create();
		/**Nadia */
		linkTable = LinkedHashMultimap.create();
	}	

	public abstract void buildDefaultRouting();
	
	public Node getNode(int id) {
		return nodesTable.get(id);
	}
	
	public void addNode(Node node){
		nodesTable.put(node.getAddress(), node);
		if (node instanceof CoreSwitch){//coreSwitch is rank 0 (root)
			node.setRank(RANK_CORE);
		} else if (node instanceof IntercloudSwitch){
			node.setRank(RANK_INTERCLOUD);
		} else if (node instanceof GatewaySwitch){
			node.setRank(RANK_GATEWAY);
		} else if (node instanceof AggregationSwitch){//Hosts are on the bottom of hierarchy (leaf)
			node.setRank(RANK_AGGR);
		} else if (node instanceof EdgeSwitch){//Edge switches are just before hosts in the hierarchy
			node.setRank(RANK_EDGE);
		} else if (node instanceof SDNHost){//Hosts are on the bottom of hierarchy (leaf)
			node.setRank(RANK_HOST);
		} else {
			throw new IllegalArgumentException();
		}
		
		addLoopbackLink(node);
	}
	
	public Collection<Node> getNodesType(NodeType tier) {
		Collection<Node> allNodes = getAllNodes();
		Collection<Node> nodes = new LinkedList<Node>();
		for(Node node:allNodes) {
			 if(tier == NodeType.Host && node.getRank() == RANK_HOST) {
					nodes.add(node);
			}
			else if(tier == NodeType.Intercloud && node.getRank() == RANK_INTERCLOUD) {
				nodes.add(node);
			}
			else if(tier == NodeType.Gateway && node.getRank() == RANK_GATEWAY) {
				nodes.add(node);
			}
			else if(tier == NodeType.Core && node.getRank() == RANK_CORE) {
				nodes.add(node);
			}
			else if(tier == NodeType.Aggr && node.getRank() == RANK_AGGR) {
				nodes.add(node);
			}
			else if(tier == NodeType.Edge && node.getRank() == RANK_EDGE) {
				nodes.add(node);
			}
		}
		return nodes;
	}
	
	public Collection<Node> getConnectedNodesLow(Node node) {
		// Get the list of lower order
		Collection<Node> nodes = new LinkedList<Node>();
		Collection<Link> links = getAdjacentLinks(node);
		for(Link l:links) {
			if(l.getHighOrder().equals(node))
				nodes.add(l.getLowOrder());
		}
		return nodes;
	}

	public Collection<Node> getConnectedNodesHigh(Node node) {
		// Get the list of higher order
		Collection<Node> nodes = new LinkedList<Node>();
		Collection<Link> links = getAdjacentLinks(node);
		for(Link l:links) {
			if(l.getLowOrder().equals(node))
				nodes.add(l.getHighOrder());
		}
		return nodes;
	}

	/*public void addLink(Link link){
		Node highNode = link.getHighOrder();
		Node lowNode = link.getLowOrder();
		
		if(getNode(highNode.getAddress()) != null &&
				getNode(lowNode.getAddress()) != null ) {
			
			// Only if both nodes are included in this NOS, the link is added.
			if(highNode.getRank() > lowNode.getRank()) {
				Node temp = highNode;
				highNode = lowNode;
				lowNode = temp;
			}
			double latency = link.getLatency();
			addLink(highNode, lowNode, latency);
		}
	}*/

	/*MAJ Add link Nadia */
	 /* MAJ Nadia link with distance and refraction  */
	 public void addLink(Link link) {
		System.out.println("################### addLink de PhysicalTopology ");
        Node highNode = link.getHighOrder();
        Node lowNode = link.getLowOrder();
   
        if (getNode(highNode.getAddress()) != null && getNode(lowNode.getAddress()) != null) {
            if (highNode.getRank() > lowNode.getRank()) {
                Node temp = highNode;
                highNode = lowNode;
                lowNode = temp;
            }
   
            double latency = link.getLatency();
            double bw = link.getBw();
            double distance = link.getDistance();
            double refractiveIndex = link.getRefractiveIndex();
   
            addLink(highNode, lowNode, latency, (long) bw, distance, refractiveIndex);
        }
    }
   
    public void addLink(Node fromNode, Node toNode, double latency, long upBW, double distance, double refractiveIndex) {
    int from = fromNode.getAddress();
    int to = toNode.getAddress();
   
    if (!nodesTable.containsKey(from) || !nodesTable.containsKey(to)) {
        throw new IllegalArgumentException("Unknown node on link: " + from + " -> " + to);
    }


    // Création du lien avec distance et indice de réfraction
    Link l = new Link(fromNode, toNode, latency, upBW, distance, refractiveIndex);


    // Ajout du lien aux structures de données
    linkTable.put(from, l);
    linkTable.put(to, l);
    nodeLinks.put(fromNode, l);
    nodeLinks.put(toNode, l);
    fromNode.addLink(l);
    toNode.addLink(l);


    // Log pour vérification
    Log.printLine(CloudSim.clock() + ": Ajout du lien : " + l);
}


	/*MAJ Add link Nadia */
	 public void addLink(Node fromNode, Node toNode, double latency, double upBW) {
    int from = fromNode.getAddress();
    int to = toNode.getAddress();
    
    long bw = (long) upBW; // Utiliser directement upBW du lien
    
    if (!nodesTable.containsKey(from) || !nodesTable.containsKey(to)) {
        throw new IllegalArgumentException("Unknown node on link: " + from + "->" + to);
    }
    
    // Créer un lien unique
    Link l = new Link(fromNode, toNode, latency, bw);
    
    // Ajouter le lien dans les deux directions
    linkTable.put(from, l);
    linkTable.put(to, l);
    
    nodeLinks.put(fromNode, l);
    nodeLinks.put(toNode, l);
    
    fromNode.addLink(l);
    toNode.addLink(l);
    
    // Ajouter des logs pour vérification
    Log.printLine(CloudSim.clock() + ": Ajout du lien : " + l);
}


	/*public void addLink(Node fromNode, Node toNode, double latency){
		int from = fromNode.getAddress();
		int to = toNode.getAddress();
		
		long bw = (fromNode.getBandwidth()<toNode.getBandwidth())? fromNode.getBandwidth():toNode.getBandwidth();
		
		if(!nodesTable.containsKey(from)||!nodesTable.containsKey(to)){
			throw new IllegalArgumentException("Unknown node on link:"+nodesTable.get(from).getAddress()+"->"+nodesTable.get(to).getAddress());
		}
		
		if (linkTable.contains(fromNode.getAddress(), toNode.getAddress())){
			throw new IllegalArgumentException("Link added twice:"+fromNode.getAddress()+"->"+toNode.getAddress());
		}
		
		if(fromNode.getRank()==-1&&toNode.getRank()==-1){
			throw new IllegalArgumentException("Unable to establish orders for nodes on link:"+nodesTable.get(from).getAddress()+"->"+nodesTable.get(to).getAddress());
		}
		
		Link l = new Link(fromNode, toNode, latency, bw);
		
		// Two way links (From -> to, To -> from)
		linkTable.put(from, to, l);
		linkTable.put(to, from, l);
		
		nodeLinks.put(fromNode, l);
		nodeLinks.put(toNode, l);
		
		fromNode.addLink(l);
		toNode.addLink(l);
	}*/
	
	// private void addLoopbackLink(Node node) {
	// 	int nodeId = node.getAddress();
	// 	long bw = NetworkOperatingSystem.bandwidthWithinSameHost;
	// 	double latency = NetworkOperatingSystem.latencyWithinSameHost;
		
	// 	Link l = new Link(node, node, latency, bw);
		
	// 	// Two way links (From -> to, To -> from)
	// 	linkTable.put(nodeId, nodeId, l);
	// 	node.addLink(l);
	// }

	/*MAJ Nadia  */
	private void addLoopbackLink(Node node) {
		int nodeId = node.getAddress();
		long bw = NetworkOperatingSystem.bandwidthWithinSameHost;
		double latency = NetworkOperatingSystem.latencyWithinSameHost;
		
		Link l = new Link(node, node, latency, bw, 0.0, 1.0);
		
		// Ajouter le lien dans la Multimap avec deux arguments
		linkTable.put(nodeId, l);
		
		// Ajouter le lien dans nodeLinks
		nodeLinks.put(node, l);
		
		// Ajouter le lien au nœud
		node.addLink(l);
		
		// Ajouter des logs pour vérification
		Log.printLine(CloudSim.clock() + ": Ajout du lien de boucle locale : " + l);
	}
	 
	
	// public Collection<Link> getAdjacentLinks(Node node) {
	// 	return nodeLinks.get(node);
	// }

	/* MAJ Nadia  */
	public Collection<Link> getAdjacentLinks(Node node) {
		Collection<Link> links = nodeLinks.get(node);
		
		// Log des liens adjacents
		for (Link l : links) {
			//Log.printLine("🔗 Lien adjacent trouvé : " + l);
		}
	
		return links;
	}
	
	
	public Collection<Node> getAllNodes() {
		return nodesTable.values();
	}
	
	public Collection<Switch> getAllSwitches() {
		Collection<Switch> allSwitches = new LinkedList<>();
		for(Node n:nodesTable.values()) {
			if(n instanceof Switch)
				allSwitches.add((Switch) n);
		}
		return allSwitches;
	}
	
	public Collection<SDNHost> getAllHosts() {
		Collection<SDNHost> allHosts = new LinkedList<>();
		for(Node n:nodesTable.values()) {
			if(n instanceof SDNHost)
				allHosts.add((SDNHost) n);
		}
		return allHosts;
	}
	
	// public Collection<Link> getAllLinks() {
	// 	HashSet<Link> allLinks = new HashSet<Link>();
	// 	allLinks.addAll(nodeLinks.values());
	// 	return allLinks;
	// }
	/* MAJ NADIA */
	public Collection<Link> getAllLinks() {
		HashSet<Link> allLinks = new HashSet<>(nodeLinks.values());
	
		// Log des liens
		// for (Link l : allLinks) {
		// 	Log.printLine("🌐 Lien enregistré : " + l);
		// }
	
		return allLinks;
	}
	
	
	public void printTopology() {
		System.out.println("printTopology de Physical Topology ");
		for(Node n:getAllNodes()) {
			System.out.println("============================================");
			System.out.println("Node: "+n);
			n.getRoutingTable().printRoutingTable();
		}
	}


}
