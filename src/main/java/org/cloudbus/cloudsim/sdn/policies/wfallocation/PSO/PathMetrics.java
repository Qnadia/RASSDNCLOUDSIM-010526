// File: src/org/cloudbus/cloudsim/sdn/policies/wfallocation/PSO/PathMetrics.java
package org.cloudbus.cloudsim.sdn.policies.wfallocation.PSO;

/**
 * Contient les métriques agrégées d'un chemin :
 * - minBandwidth : la bande passante minimale (bottleneck) rencontrée (en bps)
 * - totalLatency : la latence totale cumulée (en secondes)
 */
public class PathMetrics {
    /** Bande passante minimale (bottleneck) le long du chemin (en bps) */
    public double minBandwidth;
    /** Latence totale (en secondes) le long du chemin */
    public double totalLatency;

    public PathMetrics() {
        this.minBandwidth = Double.POSITIVE_INFINITY;
        this.totalLatency = 0.0;
    }

    @Override
    public String toString() {
        return String.format("PathMetrics[minBW=%.2e, totalLatency=%.6fs]",
                             minBandwidth, totalLatency);
    }
}
