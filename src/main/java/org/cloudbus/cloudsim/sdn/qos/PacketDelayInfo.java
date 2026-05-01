package org.cloudbus.cloudsim.sdn.qos;

import java.util.ArrayList;
import java.util.List;

public class PacketDelayInfo {
    public long packetId;
    public String source;
    public String destination;
    public long psize;
    public double delayInMs;
    public double procDelay;
    public double propDelay;
    public double transDelay;
    public double queueDelay;

    public PacketDelayInfo(long packetId, String source, String destination, long psize, double delayInMs, 
            double procDelay, double propDelay, double transDelay, double queueDelay) {
        this.packetId = packetId;
        this.source = source;
        this.destination = destination;
        this.psize = psize;
        this.delayInMs = delayInMs;
        this.procDelay = procDelay;
        this.propDelay = propDelay;
        this.transDelay = transDelay;
        this.queueDelay = queueDelay;
    }
}
