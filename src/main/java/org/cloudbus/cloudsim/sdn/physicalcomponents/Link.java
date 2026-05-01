// /*
//  * Title:        CloudSimSDN
//  * Description:  SDN extension for CloudSim
//  * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
//  *
//  * Copyright (c) 2015, The University of Melbourne, Australia
//  */
package org.cloudbus.cloudsim.sdn.physicalcomponents;

import org.cloudbus.cloudsim.sdn.LogWriter;
import org.cloudbus.cloudsim.sdn.example.LogManager;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;
import java.util.LinkedList;
import java.util.List;

// // import java.util.LinkedList;
// // import java.util.List;

public class Link {
    private Node highOrder;
    private Node lowOrder;
    private double upBW;
    private double downBW;
    private double distance;
    private double refractiveIndex;
    private double usedUpBW; // Bande passante montante utilisée
    private double usedDownBW; // Bande passante descendante utilisée
    private double latency; // in milliseconds, need to *0.001 to transform into seconds.
    private double lastUtilizationUp = 0;
    private double lastUtilizationDown = 0;

    private List<Channel> upChannels;
    private List<Channel> downChannels;

    private double efficiency = 1.0;

    /** Retourne le facteur d'efficacité (η) pour le calcul de transmission delay */
    public double getEfficiency() {
        return efficiency;
    }

    /** Permet de régler η (entre 0 et 1) si tu veux simuler contention */
    public void setEfficiency(double efficiency) {
        if (efficiency < 0 || efficiency > 1) {
            System.err.println("⚠ Efficiency must be in [0,1]");
            return;
        }
        this.efficiency = efficiency;
    }

    public Link(Node highOrder, Node lowOrder, long bandwidth, double distance, double refractiveIndex) {
        this.highOrder = highOrder;
        this.lowOrder = lowOrder;
        this.upBW = this.downBW = bandwidth;
        this.distance = distance;
        this.refractiveIndex = refractiveIndex;
        this.upChannels = new LinkedList<>();
        this.downChannels = new LinkedList<>();
    }

    // Constructeur
    public Link(Node highOrder, Node lowOrder, double upBW, double downBW) {
        this.highOrder = highOrder;
        this.lowOrder = lowOrder;
        this.upBW = upBW;
        this.downBW = downBW;
        this.usedUpBW = 0;
        this.usedDownBW = 0;
        this.upChannels = new LinkedList<>();
        this.downChannels = new LinkedList<>();
    }

    public Link(Node highOrder, Node lowOrder, double latency, long bandwidth, double distance,
            double refractiveIndex) {
        this.highOrder = highOrder;
        this.lowOrder = lowOrder;
        this.latency = latency; // Dproc switch en ms ← bug corrigé
        this.upBW = this.downBW = bandwidth;
        this.distance = distance;
        this.refractiveIndex = refractiveIndex;
        this.upChannels = new LinkedList<>();
        this.downChannels = new LinkedList<>();
    }

    /**
     * Propagation delay (seconds) based on distance & refractive index
     * Formula: Dprop = (d × n) / c
     * Returns seconds (CloudSim native unit)
     */
    public double calculateDynamicPropagationDelay() {
        double speedOfLight = 3e8; // m/s
        if (distance <= 0 || refractiveIndex < 1.0)
            return 0;
        return (distance * refractiveIndex) / speedOfLight; // seconds
    }

    // /** Bandwidth in bps from given node */
    // public double getBw(Node from) {
    // return (from.equals(lowOrder)) ? upBW : downBW;
    // }

    public Node getHighOrder() {
        return highOrder;
    }

    public Node getLowOrder() {
        return lowOrder;
    }

    @Override
    public String toString() {
        return String.format("Link{%d->%d, bw=%.2e bps, dist=%.1fm}",
                highOrder.getAddress(), lowOrder.getAddress(), upBW, distance);
    }

    // ✅ Monitoring des performances
    private MonitoringValues mvUp = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
    private MonitoringValues mvDown = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
    private long monitoringProcessedBytesPerUnitUp = 0;
    private long monitoringProcessedBytesPerUnitDown = 0;

    public double updateMonitor(double logTime, double timeUnit) {
        long capacity = (long) (this.getBw() * timeUnit);
        double utilization1 = (capacity > 0) ? (double) monitoringProcessedBytesPerUnitUp / capacity : 0;
        this.lastUtilizationUp = utilization1;
        mvUp.add(utilization1, logTime);
        monitoringProcessedBytesPerUnitUp = 0;

        // LogWriter log = LogWriter.getLogger("link_utilization_up.csv");
        // log.printLine(this.lowOrder + "," + logTime + "," + utilization1);

        LogManager.log("link_utilization_up.csv", String.format("%.4f,%s,%.4f,%.4f", logTime, lowOrder.getName(),
                utilization1 * 100, 0.0, this.getLatency()));

        double utilization2 = (capacity > 0) ? (double) monitoringProcessedBytesPerUnitDown / capacity : 0;
        this.lastUtilizationDown = utilization2;
        mvDown.add(utilization2, logTime);
        monitoringProcessedBytesPerUnitDown = 0;

        // LogWriter logDown = LogWriter.getLogger("link_utilization_down.csv");
        // logDown.printLine(this.highOrder + "," + logTime + "," + utilization2);

        LogManager.log("link_utilization_down.csv", String.format("%.4f,%s,%.4f,%.4f", logTime, highOrder.getName(),
                utilization2 * 100, 0.0, this.getLatency()));

        return Math.max(utilization1, utilization2);
    }

    public MonitoringValues getMonitoringValuesLinkUtilizationDown() {
        return mvDown;
    }

    public MonitoringValues getMonitoringValuesLinkUtilizationUp() {
        return mvUp;
    }

    // public void increaseProcessedBytes(Node from, long processedBytes) {
    // if (isUplink(from)) {
    // this.monitoringProcessedBytesPerUnitUp += processedBytes;
    // } else {
    // this.monitoringProcessedBytesPerUnitDown += processedBytes;
    // }
    // }
    /* MAJ nADIA */
    public void increaseProcessedBytes(Node from, long processedBytes) {
        if (isUplink(from)) {
            this.monitoringProcessedBytesPerUnitUp += processedBytes;
        } else {
            this.monitoringProcessedBytesPerUnitDown += processedBytes;
        }

        // 🔄 Mise à jour de l'utilisation de la BW côté hôte source
        if (from instanceof SDNHost) {
            ((SDNHost) from).increaseProcessedBw(processedBytes);
        }

        // 🛰️ Éventuellement : mise à jour aussi côté destination
        Node to = getOtherNode(from);
        if (to instanceof SDNHost) {
            ((SDNHost) to).increaseProcessedBw(processedBytes);
        }
    }

    public double getDedicatedChannelAdjustFactor(Node from) {
        double totalRequested = getRequestedBandwidthForDedicatedChannels(from);

        if (totalRequested > this.getBw()) {
            // Log.printLine("Link.getDedicatedChannelAdjustFactor() Exceeds link bandwidth.
            // Reduce requested bandwidth!");
            return this.getBw() / totalRequested;
        }
        return 1.0;
    }

    public boolean addChannel(Node from, Channel ch) {
        getChannels(from).add(ch);
        updateRequestedBandwidthForDedicatedChannels(from);
        return true;
    }

    public boolean removeChannel(Node from, Channel ch) {
        boolean ret = getChannels(from).remove(ch);
        updateRequestedBandwidthForDedicatedChannels(from);
        return ret;
    }

    /* Maj Nadia */
    public double getUtilizationPercent(Node node) {
        if (node.equals(lowOrder)) {
            return lastUtilizationUp;
        } else if (node.equals(highOrder)) {
            return lastUtilizationDown;
        } else {
            return 0.0;
        }
    }

    // /*
    // private double allocatedBandwidthDedicatedUp = 0;
    // private double allocatedBandwidthDedicatedDown = 0;

    // private double getAllocatedBandwidthForDedicatedChannels(Node from) {
    // if(this.isUplink(from))
    // return allocatedBandwidthDedicatedUp;
    // else
    // return allocatedBandwidthDedicatedDown;
    // }
    // */
    public double getPropagationDelay() {
        return calculateDynamicPropagationDelay();
    }

    public Node getSrc() {
        return this.highOrder;
    }

    public Node getDst() {
        return this.lowOrder;
    }

    private double getAllocatedBandwidthForDedicatedChannels(Node from) {

        double bw = 0;
        for (Channel ch : getChannels(from)) {
            if (ch.getChId() != -1) {
                // chId == -1 : default channel
                bw += ch.getAllocatedBandwidth();
            }
        }
        return bw;
    }

    private double requestedBandwidthDedicatedUp = 0;
    private double requestedBandwidthDedicatedDown = 0;

    private double getRequestedBandwidthForDedicatedChannels(Node from) {
        if (this.isUplink(from))
            return requestedBandwidthDedicatedUp;
        else
            return requestedBandwidthDedicatedDown;
    }

    private void updateRequestedBandwidthForDedicatedChannels(Node from) {
        // Look through all busy channel and sum up the amount of total requested
        // bandwidth.
        double bw = 0;
        for (Channel ch : getChannels(from)) {
            if (ch.getChId() != -1) {
                // chId == -1 : default channel
                bw += ch.getRequestedBandwidth(); // Only counted for 'Dedicated' channels
            }
        }
        if (isUplink(from)) {
            requestedBandwidthDedicatedUp = bw;
        } else {
            requestedBandwidthDedicatedDown = bw;
        }
    }

    // public int getChannelCount(Node from) {
    // List<Channel> channels = getChannels(from);
    // return channels.size();
    // }

    public int getDedicatedChannelCount(Node from) {
        int num = 0;
        for (Channel ch : getChannels(from)) {
            if (ch.getChId() != -1) {
                // chId == -1 : default channel
                num++;
            }
        }
        return num;
    }

    public int getSharedChannelCount(Node from) {
        int num = getChannels(from).size() - getDedicatedChannelCount(from);
        return num;
    }

    // // Retourne les canaux liés à un nœud donné
    private List<Channel> getChannels(Node from) {
        return isUplink(from) ? this.upChannels : this.downChannels;
    }

    private boolean isUplink(Node from) {
        if (from == lowOrder) {
            return true;
        } else if (from == highOrder) {
            return false;
        } else {
            throw new IllegalArgumentException("Link.isUplink(): from(" + from + ") Node is incorrect!!");
        }
    }

    public double getBw(Node from) {
        return isUplink(from) ? upBW : downBW;
    }

    public double getBw() {
        if (upBW != downBW) {
            throw new IllegalArgumentException("Downlink/Uplink BW are different!");
        }
        return upBW;
    }

    public double getLatency() {
        return latency;
    }

    /**
     * MAJ Nadia : Retourne la latence en secondes (conversion ms → s, pour
     * Channel.initialize())
     */
    public double getLatencyInSeconds() {
        return latency * 0.001; // ms → seconds
    }

    // ✅ Méthode pour obtenir l'autre nœud du lien
    public Node getOtherNode(Node currentNode) {
        if (currentNode.equals(highOrder)) {
            return lowOrder;
        } else if (currentNode.equals(lowOrder)) {
            return highOrder;
        } else {
            throw new IllegalArgumentException("Le nœud n'est pas connecté par ce lien.");
        }
    }

    // ✅ Getter et Setter pour Distance
    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        if (distance < 0) {
            System.err.println("⚠ Erreur : La distance ne peut pas être négative !");
            return;
        }
        this.distance = distance;
    }

    // ✅ Getter et Setter pour Indice de Réfraction
    public double getRefractiveIndex() {
        return refractiveIndex;
    }

    public void setRefractiveIndex(double refractiveIndex) {
        if (refractiveIndex < 1.0) {
            System.err.println("⚠ Erreur : L'indice de réfraction doit être >= 1 !");
            return;
        }
        this.refractiveIndex = refractiveIndex;
    }

    public boolean isActive() {
        return !this.upChannels.isEmpty() || !this.downChannels.isEmpty() ||
                this.lastUtilizationUp > 0 || this.lastUtilizationDown > 0 ||
                this.monitoringProcessedBytesPerUnitUp > 0 || this.monitoringProcessedBytesPerUnitDown > 0;
    }

    // public boolean isActive() {
    // if(this.upChannels.size() >0 || this.downChannels.size() >0)
    // return true;

    // return false;

    // }

    // public double getUsedBw(Node node) {
    // return node.equals(this.getLowOrder()) ? currentUsedBwUp : currentUsedBwDown;
    // }

    // public double getUsedBw(Node fromNode) {
    // if (fromNode.equals(this.lowOrder)) {
    // // direction montante (lowOrder → highOrder)
    // return this.monitoringProcessedBytesPerUnitUp / 1.0; // 1.0 = timeUnit en
    // seconde (ou ajustable)
    // } else if (fromNode.equals(this.highOrder)) {
    // // direction descendante (highOrder → lowOrder)
    // return this.monitoringProcessedBytesPerUnitDown / 1.0;
    // } else {
    // System.err.println("⚠️ getUsedBw: Node " + fromNode.getName() + " is not part
    // of this link " + this);
    // return 0;
    // }
    // }
    /**
     * MAJ Nadia : Retourne le nombre de canaux actifs sur ce lien pour un nœud
     * donné
     */
    public int getChannelCount(Node from) {
        List<Channel> channels = isUplink(from) ? this.upChannels : this.downChannels;
        return channels.size();
    }

    /**
     * MAJ Nadia : Bande passante partagée par canal = BW totale / nb canaux actifs
     */
    public double getSharedBandwidthPerChannel(Node from) {
        int count = getChannelCount(from);
        if (count == 0)
            return getBw(from);
        return getBw(from) / count;
    }

    /**
     * MAJ Nadia : Retourne la BW actuellement utilisée = somme des BW allouées aux
     * canaux actifs
     */
    public double getUsedBw(Node from) {
        List<Channel> channels = isUplink(from) ? this.upChannels : this.downChannels;
        double used = 0;
        for (Channel ch : channels) {
            used += ch.getAllocatedBandwidth();
        }
        return used;
    }

    /**
     * MAJ Nadia : Appelé par Channel.updateLinks() quand la BW allouée au canal
     * change
     */
    public void updateChannel(Node from, Channel ch) {
        updateRequestedBandwidthForDedicatedChannels(from);
    }

    /**
     * MAJ Nadia : Retourne la bande passante libre = BW totale - BW utilisée
     */
    public double getFreeBandwidth(Node from) {
        return getBw(from) - getUsedBw(from);
    }

}

// package org.cloudbus.cloudsim.sdn.physicalcomponents;

// /**
// * This is a physical link between hosts and switches to build the physical
// topology.
// * Links have latency and bandwidth.
// *
// * @author Jungmin Son
// * @author Rodrigo N. Calheiros
// * @since CloudSimSDN 1.0
// */
// public class Link {
// // Bi-directional link (one link = both ways)
// private Node highOrder;
// private Node lowOrder;
// private double upBW; // low -> high
// private double downBW; // high -> low
// private double latency; // in milliseconds, need to *0.001 to transform into
// seconds.

// /* ✅ MAJ Nadia : Ajout de distance et indice de réfraction */
// private double usedUpBW; // Bande passante montante utilisée
// private double usedDownBW; // Bande passante descendante utilisée
// private double propagationDelay;
// private double processingDelay;
// private double transmissionDelay;
// private double distance;
// private double refractiveIndex = 1.0;

// // Constructeur
// public Link(Node highOrder, Node lowOrder, double upBW, double downBW) {
// this.highOrder = highOrder;
// this.lowOrder = lowOrder;
// this.upBW = upBW;
// this.downBW = downBW;
// this.usedUpBW = 0;
// this.usedDownBW = 0;
// }

// // Mettre à jour la bande passante utilisée
// public void updateUsedBandwidth(long bandwidth, Node from) {
// if (isUplink(from)) {
// this.usedUpBW += bandwidth;
// } else {
// this.usedDownBW += bandwidth;
// }
// }

// // Getters

// public double getUpBW() { return upBW; }
// public double getDownBW() { return downBW; }

// public Link(Node highOrder, Node lowOrder, double processingDelay, double
// propagationDelay, double transmissionDelay, double bw, double distance,
// double refractiveIndex) {
// this.highOrder = highOrder;
// this.lowOrder = lowOrder;
// this.processingDelay = processingDelay;
// this.propagationDelay = propagationDelay;
// this.transmissionDelay = transmissionDelay;
// this.upBW = this.downBW = bw;
// this.distance = distance;
// this.refractiveIndex = refractiveIndex;
// }

// /**
// * Calcule la latence dynamique sans prendre en compte Queuing Delay
// * Latency = Processing Delay + Propagation Delay + Transmission Delay
// */
// public double calculateDynamicPropagationDelay() {
// //System.out.println("calculateDynamicLatency de Link");
// double dynamicPropagationDelay = calculatePropagationDelay();
// return dynamicPropagationDelay ;
// }

// public double getProcessingDelay() {
// return processingDelay;
// }

// public void setProcessingDelay(double processingDelay) {
// this.processingDelay = processingDelay;
// }

// public double getPropagationDelay() {
// return propagationDelay;
// }

// public void setPropagationDelay(double propagationDelay) {
// this.propagationDelay = propagationDelay;
// }

// public double getTransmissionDelay() {
// return transmissionDelay;
// }

// public void setTransmissionDelay(double transmissionDelay) {
// this.transmissionDelay = transmissionDelay;
// }

// @Override
// public String toString() {
// return "Link{" +
// "source=" + (highOrder != null ? highOrder.getAddress() : "null") +
// ", destination=" + (lowOrder != null ? lowOrder.getAddress() : "null") +
// ", distance=" + distance + "m" +
// ", refractiveIndex=" + refractiveIndex +
// ", bandwidth=" + upBW + " bps" +
// ", processingDelay=" + processingDelay + " ms" +
// ", propagationDelay=" + propagationDelay + " ms" +
// ", transmissionDelay=" + transmissionDelay + " ms" +
// ", totalLatency=" + calculateDynamicLatency() + " ms" +
// '}';
// }
// /* Fin MAJ */

// // ✅ Retourne le nombre de canaux actifs sur ce lien pour un nœud donné
// public int getChannelCount(Node from) {
// if (isUplink(from)) {
// return this.upChannels.size();
// } else {
// return this.downChannels.size();
// }
// }

// // Retourne la bande passante restante sur le lien pour un nœud donné
// public double getFreeBandwidth(Node from) {
// double allocatedBandwidth = 0;

// // Calcul de la bande passante déjà allouée
// for (Channel ch : getChannels(from)) {
// allocatedBandwidth += ch.getAllocatedBandwidth();
// }

// // Vérification et calcul de la bande passante restante
// double freeBw = getBw(from) - allocatedBandwidth;

// if (freeBw < 0) {
// System.err.println("⚠ Avertissement : Pas de bande passante disponible sur le
// lien ! " + this);
// freeBw = 0; // Assurer que la valeur ne soit pas négative
// }

// return freeBw;
// }

// // // Constructeur avec bande passante unique
// // public Link(Node highOrder, Node lowOrder, double latency, double bw) {
// // this.highOrder = highOrder;
// // this.lowOrder = lowOrder;
// // this.upBW = this.downBW = bw;
// // this.latency = latency;
// // this.upChannels = new LinkedList<>();
// // this.downChannels = new LinkedList<>();
// // }

// // Constructeur avec bande passante asymétrique
// public Link(Node highOrder, Node lowOrder, double latency, double upBW,
// double downBW) {
// this(highOrder, lowOrder, latency, upBW);
// this.downBW = downBW;
// }

// /* ✅ MAJ Nadia : Calcul du délai de propagation */
// public double calculatePropagationDelay() {
// //System.out.println("################################
// calculatePropagationDelay");
// double speedOfLight = 3.0e8; // Vitesse de la lumière en m/s
// if (distance <= 0 || refractiveIndex < 1.0) {
// return 0;
// }
// return (distance / (speedOfLight / refractiveIndex)) * 1000;
// }

// public Link(Node highOrder, Node lowOrder, double latency, long bandwidth,
// double distance, double refractiveIndex) {
// this.highOrder = highOrder;
// this.lowOrder = lowOrder;
// this.upBW = this.downBW = bandwidth;
// this.latency = latency;
// this.distance = distance;
// this.refractiveIndex = refractiveIndex;

// this.upChannels = new LinkedList<>();
// this.downChannels = new LinkedList<>();
// }

// /* Fin MAJ */

// public Node getHighOrder() {
// return highOrder;
// }

// public Node getLowOrder() {
// return lowOrder;
// }

// /* MAJ NADIA */
// public double getLatencyInSeconds() {
// return calculateDynamicPropagationDelay() * 0.001;
// }

//
// // public double getFreeBandwidth(Node from) {
// // double bw = this.getBw(from);
// // double dedicatedBw = getAllocatedBandwidthForDedicatedChannels(from);

// // double freeBw = bw-dedicatedBw;

// // if(freeBw <0) {
// // System.err.println("This link has no free BW, all occupied by dedicated
// channels!"+this);
// // freeBw=0;
// // }

// // return freeBw;
// // }

// // /*
// // public double getFreeBandwidthForDedicatedChannel(Node from) {
// // double bw = this.getBw(from);
// // double dedicatedBw = getRequestedBandwidthForDedicatedChannels(from);

// // return bw-dedicatedBw;
// // }
// // */

// public double getSharedBandwidthPerChannel(Node from) {
// double freeBw = getFreeBandwidth(from);
// double sharedBwEachChannel = freeBw / getSharedChannelCount(from);

// if(sharedBwEachChannel < 0)
// System.err.println("Negative BW on link:"+this);

// return sharedBwEachChannel;
// }

// public void updateChannel(Node from, Channel ch) {
// updateRequestedBandwidthForDedicatedChannels(from);
// }
// }

// // package org.cloudbus.cloudsim.sdn.physicalcomponents;

// // import java.util.LinkedList;
// // import java.util.List;

// // import org.cloudbus.cloudsim.sdn.LogWriter;
// // import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
// // import org.cloudbus.cloudsim.sdn.virtualcomponents.Channel;

// // /**
// // * This is physical link between hosts and switches to build physical
// topology.
// // * Links have latency and bandwidth.
// // *
// // * @author Jungmin Son
// // * @author Rodrigo N. Calheiros
// // * @since CloudSimSDN 1.0
// // */
// // public class Link {
// // // bi-directional link (one link = both ways)
// // private Node highOrder;
// // private Node lowOrder;
// // private double upBW; // low -> high
// // private double downBW; // high -> low
// // private double latency; // in milliseconds, need to *0.001 to transform in
// seconds.

// // /* MAJ Nadia */
// // private double propagationDelay;
// // private double distance;
// // private double refractiveIndex;
// // /* Fin MAJ */

// // private List<Channel> upChannels;
// // private List<Channel> downChannels;

// // public Link(Node highOrder, Node lowOrder, double latency, double bw) {
// // this.highOrder = highOrder;
// // this.lowOrder = lowOrder;
// // this.upBW = this.downBW = bw;
// // this.latency = latency;

// // this.upChannels = new LinkedList<Channel>();
// // this.downChannels = new LinkedList<Channel>();
// // }

// // public Link(Node highOrder, Node lowOrder, double latency, double upBW,
// double downBW) {
// // this(highOrder, lowOrder, latency, upBW);
// // this.downBW = downBW;
// // }

// // /* MAJ Nadia */
// // private double calculatePropagationDelay() {
// // double speedOfLight = 3.0e8;
// // if (distance <= 0 || refractiveIndex <= 0 ){
// // return 0;
// // }
// // return distance/ (speedOfLight / refractiveIndex);
// // }

// // public void setDistance(double distance) {
// // if (distance < 0) {
// // System.err.println("⚠ Erreur : La distance ne peut pas être négative !");
// // return;
// // }
// // this.distance = distance;
// // }

// // public void setRefractiveIndex(double refractiveIndex) {
// // if (refractiveIndex < 1.0) { // L'indice de réfraction doit être >= 1.0
// // System.err.println("⚠ Erreur : L'indice de réfraction doit être supérieur
// ou égal à 1 !");
// // return;
// // }
// // this.refractiveIndex = refractiveIndex;
// // }

// // @Override
// // public String toString() {
// // return "Link{" +
// // "source=" + (highOrder != null ? highOrder.getAddress() : "null") +
// // ", destination=" + (lowOrder != null ? lowOrder.getAddress() : "null") +
// // ", distance=" + distance + "m" +
// // ", refractiveIndex=" + refractiveIndex +
// // ", bandwidth=" + getBw() + " bps" +
// // ", latency=" + latency + " ms" +
// // ", propagationDelay=" + calculatePropagationDelay() + " s" +
// // '}';
// // }
// // /* Fin MAJ */

// // public Node getHighOrder() {
// // return highOrder;
// // }

// // public Node getLowOrder() {
// // return lowOrder;
// // }

// // // public Node getOtherNode(Node from) {
// // // if(highOrder.equals(from))
// // // return lowOrder;

// // // return highOrder;
// // // }

// // /* Nadia */
// // // Méthode pour obtenir l'autre nœud du lien
// // public Node getOtherNode(Node currentNode) {
// // if (currentNode.equals(highOrder)) {
// // return lowOrder;
// // } else if (currentNode.equals(lowOrder)) {
// // return highOrder;
// // } else {
// // throw new IllegalArgumentException("Le nœud n'est pas connecté par ce
// lien.");
// // }
// // }

// // private boolean isUplink(Node from) {
// // if(from == lowOrder) {
// // return true;
// // }
// // else if(from == highOrder) {
// // return false;
// // }
// // else {
// // throw new IllegalArgumentException("Link.isUplink(): from("+from+") Node
// is wrong!!");
// // }
// // }

// // public double getBw(Node from) {
// // if(isUplink(from)) {
// // return upBW;
// // }
// // else {
// // return downBW;
// // }
// // }

// // public double getBw() {
// // if(upBW != downBW) {
// // throw new IllegalArgumentException("Downlink/Uplink BW are different!");
// // }
// // return upBW;
// // }

// // public double getLatency() {
// // return latency;
// // }

// // public double getLatencyInSeconds() {
// // return latency*0.001;
// // }

// // private List<Channel> getChannels(Node from) {
// // List<Channel> channels;
// // if(isUplink(from)) {
// // channels = this.upChannels;
// // }
// // else {
// // channels = this.downChannels;
// // }

// // return channels;
// // }

// // public double getDedicatedChannelAdjustFactor(Node from) {
// // double totalRequested = getRequestedBandwidthForDedicatedChannels(from);

// // if(totalRequested > this.getBw()) {
// // //Log.printLine("Link.getDedicatedChannelAdjustFactor() Exceeds link
// bandwidth. Reduce requested bandwidth!");
// // return this.getBw() / totalRequested;
// // }
// // return 1.0;
// // }

// // public boolean addChannel(Node from, Channel ch) {
// // getChannels(from).add(ch);
// // updateRequestedBandwidthForDedicatedChannels(from);
// // return true;
// // }

// // public boolean removeChannel(Node from, Channel ch) {
// // boolean ret = getChannels(from).remove(ch);
// // updateRequestedBandwidthForDedicatedChannels(from);
// // return ret;
// // }

// // public void updateChannel(Node from, Channel ch) {
// // updateRequestedBandwidthForDedicatedChannels(from);
// // }

// // /*
// // private double allocatedBandwidthDedicatedUp = 0;
// // private double allocatedBandwidthDedicatedDown = 0;

// // private double getAllocatedBandwidthForDedicatedChannels(Node from) {
// // if(this.isUplink(from))
// // return allocatedBandwidthDedicatedUp;
// // else
// // return allocatedBandwidthDedicatedDown;
// // }
// // */

// // private double getAllocatedBandwidthForDedicatedChannels(Node from) {

// // double bw=0;
// // for(Channel ch: getChannels(from)) {
// // if(ch.getChId() != -1) {
// // // chId == -1 : default channel
// // bw += ch.getAllocatedBandwidth();
// // }
// // }
// // return bw;
// // }

// // private double requestedBandwidthDedicatedUp = 0;
// // private double requestedBandwidthDedicatedDown = 0;

// // private double getRequestedBandwidthForDedicatedChannels(Node from) {
// // if(this.isUplink(from))
// // return requestedBandwidthDedicatedUp;
// // else
// // return requestedBandwidthDedicatedDown;
// // }

// // private void updateRequestedBandwidthForDedicatedChannels(Node from) {
// // // Look through all busy channel and sum up the amount of total requested
// bandwidth.
// // double bw=0;
// // for(Channel ch: getChannels(from)) {
// // if(ch.getChId() != -1) {
// // // chId == -1 : default channel
// // bw += ch.getRequestedBandwidth(); // Only counted for 'Dedicated' channels
// // }
// // }
// // if(isUplink(from)) {
// // requestedBandwidthDedicatedUp = bw;
// // }
// // else{
// // requestedBandwidthDedicatedDown = bw;
// // }
// // }

// // public int getChannelCount(Node from) {
// // List<Channel> channels = getChannels(from);
// // return channels.size();
// // }

// // public int getDedicatedChannelCount(Node from) {
// // int num=0;
// // for(Channel ch: getChannels(from)) {
// // if(ch.getChId() != -1) {
// // // chId == -1 : default channel
// // num ++;
// // }
// // }
// // return num;
// // }

// // public int getSharedChannelCount(Node from) {
// // int num = getChannels(from).size() - getDedicatedChannelCount(from);
// // return num;
// // }

// // public double getFreeBandwidth(Node from) {
// // double bw = this.getBw(from);
// // double dedicatedBw = getAllocatedBandwidthForDedicatedChannels(from);

// // double freeBw = bw-dedicatedBw;

// // if(freeBw <0) {
// // System.err.println("This link has no free BW, all occupied by dedicated
// channels!"+this);
// // freeBw=0;
// // }

// // return freeBw;
// // }

// // /*
// // public double getFreeBandwidthForDedicatedChannel(Node from) {
// // double bw = this.getBw(from);
// // double dedicatedBw = getRequestedBandwidthForDedicatedChannels(from);

// // return bw-dedicatedBw;
// // }
// // */

// // public double getSharedBandwidthPerChannel(Node from) {
// // double freeBw = getFreeBandwidth(from);
// // double sharedBwEachChannel = freeBw / getSharedChannelCount(from);

// // if(sharedBwEachChannel < 0)
// // System.err.println("Negative BW on link:"+this);

// // return sharedBwEachChannel;
// // }

// // // public String toString() {
// // // return "Link:"+this.highOrder.toString() + " <->
// "+this.lowOrder.toString() + ", upBW:" + upBW + ", Latency:"+ latency;
// // // }

// // public boolean isActive() {
// // if(this.upChannels.size() >0 || this.downChannels.size() >0)
// // return true;

// // return false;

// // }

// // // For monitor
// // private MonitoringValues mvUp = new
// MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
// // private MonitoringValues mvDown = new
// MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
// // private long monitoringProcessedBytesPerUnitUp = 0;
// // private long monitoringProcessedBytesPerUnitDown = 0;

// // public double updateMonitor(double logTime, double timeUnit) {
// // long capacity = (long) (this.getBw() * timeUnit);
// // double utilization1 = (double)monitoringProcessedBytesPerUnitUp /
// capacity;
// // mvUp.add(utilization1, logTime);
// // monitoringProcessedBytesPerUnitUp = 0;

// // LogWriter log = LogWriter.getLogger("link_utilization_up.csv");
// // log.printLine(this.lowOrder+","+logTime+","+utilization1);

// // double utilization2 = (double)monitoringProcessedBytesPerUnitDown /
// capacity;
// // mvDown.add(utilization2, logTime);
// // monitoringProcessedBytesPerUnitDown = 0;
// // LogWriter logDown = LogWriter.getLogger("link_utilization_down.csv");
// // logDown.printLine(this.highOrder+","+logTime+","+utilization2);

// // return Double.max(utilization1, utilization2);
// // }

// // public MonitoringValues getMonitoringValuesLinkUtilizationDown() {
// // return mvDown;
// // }
// // public MonitoringValues getMonitoringValuesLinkUtilizationUp() {
// // return mvUp;
// // }

// // public void increaseProcessedBytes(Node from, long processedBytes) {
// // if(isUplink(from))
// // this.monitoringProcessedBytesPerUnitUp += processedBytes;
// // else
// // this.monitoringProcessedBytesPerUnitDown += processedBytes;

// // }
// // }
