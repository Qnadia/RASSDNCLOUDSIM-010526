package org.cloudbus.cloudsim.sdn.qos;

import java.util.ArrayList;
import java.util.List;


public class QoSViolation {
    private long flowId;
    private String type;

    public QoSViolation(long flowId, String type) {
        this.flowId = flowId;
        this.type = type;
    }

    public long getFlowId() { return flowId; }
    public String getType() { return type; }
}
