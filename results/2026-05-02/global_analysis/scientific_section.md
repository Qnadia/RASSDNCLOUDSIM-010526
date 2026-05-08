# 4. Experiment and Discussion

*Revised following supervisors' remarks (Pr. Tadonki & Pr. Samadi)*

In this section, we evaluate the benefits of the proposed BLA routing policy under realistic simulation conditions, comparing it against the default Select-First-Link (SFL) baseline across four performance metrics: end-to-end packet delay, queuing delay, energy consumption, and SLA severity.

## 4.1 Experimental Setup

All experiments are conducted using RAS-SDNCloudSim, our extension of CloudSimSDN, executed on a standard workstation running Java 21 under Windows 11. We evaluate three datasets of increasing complexity (Table 1), each deliberately designed with a bandwidth asymmetry that creates a realistic congestion scenario.

### Table 1: Dataset configurations

| Dataset | Hosts | VMs | Workloads | Congestion design |
| :--- | :--- | :--- | :--- | :--- |
| Mini | 4 | 4 | 50 | Bottleneck: 50 Mbps vs. 800 Mbps backbone |
| Small | 6 | 8 | 100 | Asymmetric Core: 80 Mbps vs. 300 Mbps |
| Medium | 12 | 20 | 500 | Fat-Tree Core: 200–500 Mbps multi-path |

### Table 2: Physical host power consumption model

| Parameter | Value | Unit | Description |
| :--- | :--- | :--- | :--- |
| P_idle | 100 | W | Baseline idle power |
| alpha_cpu | 1.0 | W/% | Added power per 1% CPU load |
| alpha_ram | 0.2 | W/% | Added power per 1% RAM load |
| alpha_bw | 0.1 | W/% | Added power per 1% BW load |
| T_off | 300 | s | Idle threshold before sleep mode |

## 4.2 Results and Analysis

### 4.2.1 End-to-End Packet Delay

Table 3 reports the average packet delay per VM policy and dataset. BLA consistently reduces delay under LFF and LWFF.

### Table 3: Average packet delay (s) and BLA gain (%) per VM policy

| Dataset | VM Policy | SFL (s) | BLA (s) | Gain delay | Gain queue |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Mini | LFF | 48.4 | 37.8 | **-21.9%** | **-20.8%** |
| Mini | LWFF | 51.8 | 41.7 | **-19.4%** | **-17.8%** |
| Mini | MFF | 16.0 | 16.0 | 0.0% | 0.0% |
| Small | LFF | 535.3 | 494.5 | **-7.6%** | **-8.2%** |
| Small | LWFF | 341.8 | 317.7 | **-7.1%** | **-11.1%** |
| Small | MFF | 196.0 | 196.0 | 0.0% | 0.0% |
| Medium | LFF | 856.3 | 802.7 | **-6.3%** | **-6.8%** |
| Medium | LWFF | 995.5 | 816.3 | **-18.0%** | **-19.1%** |
| Medium | MFF | 474.4 | 474.4 | 0.0% | 0.0% |

![Figure 1: Average packet delay (ms) - Medium](../../dataset-medium/plot/delay_by_vm.png)

### 4.2.2 Network-Pure Latency

| Dataset | VM Policy | SFL (ms) | BLA (ms) | Gain |
| :--- | :--- | :--- | :--- | :--- |
| Mini | LFF | 7,776 | 4,248 | **-45.4%** |
| Mini | LWFF | 7,600 | 4,248 | **-44.1%** |
| Small | LFF | 13,913 | 323 | **-97.7%** |
| Small | LWFF | 8,120 | 81 | **-99.0%** |
| Medium | LFF | 21,742 | 3,884 | **-82.1%** |
| Medium | LWFF | 62,394 | 2,656 | **-95.7%** |

### 4.2.3 Queuing Delay: Empirical Validation

![Figure 2: Average queuing delay (ms) - Medium](../../dataset-medium/plot/queuing_delay.png)

### 4.2.4 Energy Consumption

| Dataset | VM Policy | SFL (Wh) | BLA (Wh) | Gain |
| :--- | :--- | :--- | :--- | :--- |
| Mini | LFF | 3.84 | 3.56 | **-7.3%** |
| Small | LFF | 135.8 | 125.8 | **-7.4%** |
| Small | LWFF | 43.6 | 42.0 | **-3.8%** |
| Medium | LWFF | 1,045.4 | 880.8 | **-15.7%** |
| Medium | LFF | 2,865.8 | 2,638.3 | **-7.9%** |

![Figure 3: Energy Consumption - Medium](../../dataset-medium/plot/energy_by_vm.png)

### 4.2.5 SLA Severity

| Dataset | VM Policy | sigma SFL | sigma BLA | Gain |
| :--- | :--- | :--- | :--- | :--- |
| Mini | LFF | 4.21 | 2.67 | **-36.4%** |
| Small | LFF | 6.37 | 3.42 | **-46.4%** |
| Medium | LWFF | 8.91 | 2.23 | **-74.9%** |

### 4.2.6 Pareto Analysis: Energy–Latency Trade-off

![Figure 4: Pareto Trade-off - Medium](../../dataset-medium/plot/pareto_energy_delay.png)

### 4.2.7 Workload Scheduler Impact

![Figure 5: Scheduler Impact - Medium](../../dataset-medium/plot/wf_latency_impact.png)

## 4.3 Cross-Dataset Synthesis and Scalability

| Dataset | Delta Delay | Delta Queue | Delta Energy | Peak SLA gain |
| :--- | :--- | :--- | :--- | :--- |
| Mini | -17.8% | -17.1% | -3.3% | -36.4% (LFF) |
| Small | -6.0% | -8.9% | -6.3% | -46.4% (LFF) |
| Medium | -10.0% | -11.7% | -9.2% | -74.9% (LWFF) |

## 4.4 Discussion

BLA works best when the VM placement policy generates sufficient cross-fabric traffic (LFF or LWFF) and the topology offers meaningful path diversity. For balanced workloads, LWFF+BLA achieves the best simultaneous outcome across all four dimensions.
