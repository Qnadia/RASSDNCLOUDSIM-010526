# Implementation Plan: Refinement of Simulation Accuracy (Iteration 4 - 6)

Addressing reporting anomalies (Missing Paths, "Pure CPU" misclassification) and solving the artificial SLA violation bug.

## Proposed Changes

### [SDN Framework]

#### [MODIFY] [SDNDatacenter.java]
- **Fix `minBw` initialization**: Initialize to `Double.POSITIVE_INFINITY` to avoid `Infinity` transmission delays from division by zero.
- **Request Metadata Propagation**:
    - Call `req.setPacketSizeBytes(pkt.getSize())` if a packet exists.
    - Call `req.setCloudletLength(cloudletLen)` for all workloads.
    - Call `req.setSubmitTime(CloudSim.clock())` at the start of `processWorkloadSubmit`.
- **Fix `processingDelay` propagation**: Ensure `req.setProcessingDelay(processingDelay)` is called.

#### [MODIFY] [Request.java]
- **Fix Copy Constructor**: Propagate `submitTime`, `finishTime`, `appId`, `switchProcessingDelay`, `failedTime`. This is the critical fix for accurate SLA reporting.

#### [MODIFY] [SDNBroker.java]
- **Path Visibility**: Classification fix automatically restores path reporting in the console.

## Verification Plan
- **Run Simulation**: Execute `LFF BwAllocN Priority medium`.
- **Verify Results**: 
    - 100% SLA Compliance.
    - Accurate Hybrid classification for 200/200 requests.
    - 0.000s Queuing Delay (Corrected from false 80s).
