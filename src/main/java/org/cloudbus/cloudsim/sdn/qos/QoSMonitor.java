package org.cloudbus.cloudsim.sdn.qos;

import org.cloudbus.cloudsim.sdn.Configuration;
import java.util.ArrayList;
import java.util.List;

public class QoSMonitor {
    private static final List<QoSViolation> violations = new ArrayList<>();
    private static final List<PacketDelayInfo> packetDelays = new ArrayList<>();

    public static void reset() {
        violations.clear();
        packetDelays.clear();
        System.out.println("🔄 [QoSMonitor] Global state reset completed.");
    }

    public static void recordDelay(long packetId, String source, String destination, long psize, double delayInMs, 
            double procMs, double propMs, double transMs, double queueMs) {
        packetDelays.add(new PacketDelayInfo(packetId, source, destination, psize, delayInMs, 
            procMs, propMs, transMs, queueMs));
    }

    public static void checkAndLogViolation(long flowId, double actualDelay, double expectedDelay) {
        if (actualDelay > expectedDelay * Configuration.DECIDE_SLA_VIOLATION_GRACE_ERROR) {
            violations.add(new QoSViolation(flowId, "SLA_VIOLATION"));
        }
    }

    public static List<QoSViolation> getViolations() {
        return violations;
    }

    public static List<PacketDelayInfo> getPacketDelays() {
        return packetDelays;
    }
}
