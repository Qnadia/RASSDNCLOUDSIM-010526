package org.cloudbus.cloudsim.sdn.physicalcomponents;

import java.util.ArrayList;
import java.util.List;

public class SimpleNode implements Node {
    private final int address;
    private final String name;
    private final List<Link> links = new ArrayList<>();
    private int rank;

    public SimpleNode(int address, String name) {
        this.address = address;
        this.name = name;
    }

    @Override public int getAddress() { return address; }
    @Override public long getBandwidth() { return 0; }
    @Override public void setRank(int rank) { this.rank = rank; }
    @Override public int getRank() { return rank; }
    @Override public String getName() { return name; }
    @Override public void clearVMRoutingTable() {}
    @Override public void addVMRoute(int src, int dest, int flowId, Node to) {}
    @Override public Node getVMRoute(int src, int dest, int flowId) { return null; }
    @Override public void removeVMRoute(int src, int dest, int flowId) {}
    @Override public void printVMRoute() {}
    @Override public void addRoute(Node destHost, Link to) {}
    @Override public List<Link> getRoute(Node destHost) { return null; }
    @Override public RoutingTable getRoutingTable() { return null; }
    @Override public void addLink(Link l) { links.add(l); }
    @Override public Link getLinkTo(Node nextHop) { return null; }
    @Override public void updateNetworkUtilization() {}
}
