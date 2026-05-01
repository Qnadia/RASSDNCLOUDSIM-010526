package org.cloudbus.cloudsim.sdn;



import java.util.Random;

import org.cloudbus.cloudsim.UtilizationModel;

/**
 * A stochastic utilization model that returns random utilization values between 0 and a maximum.
 * Useful to simulate variable load cloudlets (CPU usage).
 */
public class UtilizationModelStochastic implements UtilizationModel {

    private final double maxUtilization;
    private final Random random;

    /**
     * Creates a new stochastic utilization model.
     *
     * @param maxUtilization The maximum utilization (between 0.0 and 1.0)
     * @param seed Random seed for reproducibility (can be null)
     */
    public UtilizationModelStochastic(double maxUtilization, Long seed) {
        if (maxUtilization < 0.0 || maxUtilization > 1.0)
            throw new IllegalArgumentException("maxUtilization must be between 0.0 and 1.0");

        this.maxUtilization = maxUtilization;
        this.random = (seed == null) ? new Random() : new Random(seed);
    }

    /**
     * Creates a new stochastic utilization model with default seed.
     *
     * @param maxUtilization The maximum utilization (between 0.0 and 1.0)
     */
    public UtilizationModelStochastic(double maxUtilization) {
        this(maxUtilization, null);
    }

    @Override
    public double getUtilization(double time) {
        return maxUtilization * random.nextDouble();
    }
}
