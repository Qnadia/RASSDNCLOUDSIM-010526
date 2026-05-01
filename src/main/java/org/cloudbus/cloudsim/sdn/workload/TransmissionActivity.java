package org.cloudbus.cloudsim.sdn.workload;

import org.cloudbus.cloudsim.sdn.Packet;

public class TransmissionActivity {
    private Packet packet;
    private double processingDelay;
    private double propagationDelay;
    private double transmissionDelay;

    public TransmissionActivity(Packet packet, double processingDelay, double propagationDelay, double transmissionDelay) {
        this.packet = packet;
        this.processingDelay = processingDelay;
        this.propagationDelay = propagationDelay;
        this.transmissionDelay = transmissionDelay;
    }

    public Packet getPacket() {
        return this.packet;
    }

    public double getProcessingDelay() {
       return processingDelay;
    }

    public double getPropagationDelay() {
        return propagationDelay;
    }

    public double getTransmissionDelay() {
        return transmissionDelay;
    }

    // Getters and setters
}
