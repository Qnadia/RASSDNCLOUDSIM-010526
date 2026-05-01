# IT 13 - Transmission Delay Fix & Redundant Links Validation

## Date: 2026-03-10
## Objective: Fix inaccurate network delay accumulations in SDNDatacenter to validate link selection policies properly.

### Planned Modifications
1. **`SDNDatacenter.java`**: Fix `processNextActivityTransmission` to use the accurate `tr.getExpectedTime()` instead of hardcoded bypass calculations that ignore topological routes.
2. **Re-run Experiments**: Execute `compare_priority_link_selection.ps1` with the `dataset-redundant` workload.

### Progress Tracking
- [x] Implement fix in `SDNDatacenter.java`
- [x] Recompile project
- [ ] Execute simulations and generate reports
- [ ] Analyze reports to verify distinct latency results

### IT14 - Infinite Simulation Loop Fix
* **Issue**: Found a simulation bug causing infinite logging at `t=8.94` in `experiment_LFF_First_Priority_dataset-redundant.log`. `CloudletSchedulerTimeSharedMonitor` broke its processing loop after completing *one* cloudlet, preventing concurrent cloudlets from advancing.
* **Fix**: Replaced the `break` statement with `continue` while removing finished cloudlets properly, allowing the simulation to proceed.
