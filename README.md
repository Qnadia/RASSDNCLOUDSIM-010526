# CloudSimSDN

CloudSimSDN: SDN extension of CloudSim project. Version 2.0 (CloudSimSDN-NFV) is now availalbe.

## New Features:

* Resource provisioning for NFV in the edge computing environment;
* Simulation framework for NFV in edge and cloud computing (inter cloud data centers);
* Policy supports of Network link selection, VM allocation, Virtual Network Function(VNF) placement, and SFC auto-scaling algorithms;
* Performance evaluation of the framework with use case scenarios.

## Introduction

**CloudSimSDN** is to simulate utilization of hosts and networks, and response time of requests in SDN-enabled cloud data centers.
**CloudSimSDN** is an add-on package to [CloudSim](http://www.cloudbus.org/cloudsim/), thus it is highly recommended to learn how to use CloudSim before using CloudSimSDN.
CloudSimSDN supports calculating power consumption by both hosts and switches. For instance, network-aware VM placement policies can be evaluated using CloudSimSDN. As an example, we will present energy savings in SDN-enabled cloud data center via VM consolidation. If VMs are consolidated to the minimum number of hosts, the unused hosts and switches can be powered off to save more power. We will show two different VM placement policies: Best Fit (MFF, Most Full First) and Worst Fit (LFF, Least Full First).

## Development Iterations Summary
The project has evolved through 20 technical iterations aimed at enhancing stability, performance, and features:
- **It 25 (Latest)**: Energy sync fix (t=100) and **Multi-dimensional Excel Reporting**. Automatic partitioning of results by VM/Routing/Workload policies.
- **It 20-24**: Large-scale benchmark on `dataset-medium`. Validation of **PSO** efficiency and asymetric link routing.
- **It 16-19**: Integration of **User Priority** (PriorityWorkloadScheduler), link redundancy handling, and unified theoretical modeling.
- **It 1-15**: Stability baseline, BFS routing fix, and early BwAlloc implementation. (Archived)

For detailed logs, see [docs/tracking/README.md](docs/tracking/README.md).
Detailed experimental setup: [Rapport Technique Médium](docs/analysis/Rapport_Technique_Medium_IT20.md).

## Program Dependencies📄
You need to integrate CloudSim with CloudSimSDN. There are two ways to include CloudSim into the project: (1) including the CloudSim Source code; (2) importing the CloudSim jar.

### Method1: including the CloudSim Source Code
In order to integrate the CloudSim src code, download CloudSim source code from (https://github.com/Cloudslab/cloudsim), then copy the CloudSim src code (\cloudsim-master\modules\cloudsim\src\main) into (\cloudsimsdn-master\src\main).
For the dependency of opencsv used in the container module of CloudSim, include the dependency in the pom.xml:
```
<dependency>
        <groupId>com.opencsv</groupId>
        <artifactId>opencsv</artifactId>
        <version>3.7</version>
</dependency>
```

### Method2: importing the CloudSim jar
1. You need download or clone [CloudSim](https://github.com/Cloudslab/cloudsim) and export the jar of the newest version (bugs fixed to support cloudsimsdn-nfv), name it (cloudsim-4.0.jar);
2. Add generated cloudsim-4.0.jar local jar into the maven dependencies: add the cloudsim dependency in pom.xml.
````
        <dependency>
            <groupId>org.cloudbus.cloudsim</groupId>
            <artifactId>cloudsim</artifactId>
            <version>4.0</version>
            <scope>system</scope>
    	    <systemPath>${project.basedir}/YOUR_PATH/cloudsim-4.0.jar</systemPath>
        </dependency>
````
3. enter the project's root directory and execute `mvn clean install` to install the jar packages into your local maven repository.

Other dependencies are already included.

## Quick Start⚡️
After the mvn build, you could simply run the project's example in IDE's Run Configurations by adding commands in the Arguments:

* For example, to start the simulation example of SimpleExampleInterCloud:
````
LFF example-intercloud/intercloud.physical.json example-intercloud/intercloud.virtual.json example-intercloud/intercloud-example-workload.csv example-intercloud/intercloud-example-workload2.csv
````

* To run StartExperimentSFCEdge:
````
LFF 0 example-edge/edge.physical.json example-edge/edge.virtual.json example-edge/ edge.workload_host1.csv edge.workload_host2.csv
````
* To run StartExperimentSFC:
1 for enable SFC auto-scaling
````
LFF 1 example-sfc/sfc-example-physical.json example-sfc/sfc-example-scale-virtual.json example-sfc/ sfc-example-scale-workload.csv
````

## Package Components
1. org.cloudbus.cloudsim.sdn

  Main components of CloudSimSDN. Core functions are implemented in this package source codes.
  
2. org.cloudbus.cloudsim.example

  Example program. SimpleExample.java is the entry point of the example program. Please follow the code from SimpleExample.java
  This document is to describe the example program.
  Other Scenarios includes:
  Inter cloud data centers, Link Selection Policy, Overbooking Host Resources, QoS, Service Function Chaining, and
  Service Function Chaining in edge computing.
  
3. org.cloudbus.cloudsim.sdn.exmaple.topogenerators

  Example topology generators. Physical / Virtual topology files (inter-clouds, Edge computing, SFC, multi-tier web application, etc.) can be generated by using these generators with customizable parameters. Some distributions can be used within topology generators.
  
4. org.cloudbus.cloudsim.sdn.monitor

  Energy consumption and utilization monitor.
  
5. org.cloudbus.cloudsim.sdn.nos

 Main components of Networking Operation System includes flow channel manager, and extended version of nos for different scenarios. 

## Analysis & Excel Reporting 📊

Le projet inclut désormais un pipeline complet pour extraire et analyser les données dans Excel.

### 1. Consolidation Matricielle (Pivot Tables)
Génère des fichiers CSV structurés pour Excel (point-virgule) avec les politiques de Placement en colonnes et le Routage en lignes.
```powershell
python tools/analysis/generate_excel_matrices.py
```
*Génère : `MATRICE_ENERGIE_SJF.csv`, `MATRICE_SLA_PSO.csv`, etc.*

### 2. Fusion des Logs Bruts (Master Logs)
Fusionne tous les fichiers de log temporels de toutes les simulations (27+ runs) en un seul fichier "Master" par indicateur, en ajoutant des colonnes pour filtrer par politique (**vm_policy**, **routing_policy**, **workload_policy**).
```powershell
python tools/analysis/merge_raw_logs.py
```
*Génère : `MASTER_host_energy.csv`, `MASTER_packet_delays.csv`, etc. dans `RAW_DATA_ALL_CONSOLIDATED/`.*

6. org.cloudbus.cloudsim.sdn.parsers
  
  Parsers for physical topology, virtual topology, and workload.
  
7. org.cloudbus.cloudsim.sdn.physicalcomponents

  SDN-enabled components includes node, link, physical topology, switches (Aggregation, core, edge, gateway, inter-cloud), routing table, extended datacenter, and host.
  
8. org.cloudbus.cloudsim.sdn.policies

  Policies (algorithms) for Host selection, Link selection, Vm allocation, Host overbooking.
  
 9. org.cloudbus.cloudsim.sdn.provisioners
 
  Bandwidth(bw) and CPU(Pe) overbooking provisioners.
  
 10. org.cloudbus.cloudsim.sdn.sfc
 
  Main components of Service Function Chaining (SFC) festures, including SFC Forwarder, SFC policy, auto scaling algorihtms (scaling up and out), etc.
  
 11. org.cloudbus.cloudsim.sdn.virtualcomponents
 
  Flows created in VMs (SDNVM.java) through channel(Channel.java) based on corresponding Flow configuration(FlowConfig.java) are forwarded according to rules(ForwardingRule.java) in SDN-enabled switches. VirtualNetworkMapper includes the main APIs for network traffic forwarding.
  
  12. org.cloudbus.cloudsim.sdn.workload
 
  Core components for workload processing and networking transmission. Request.java represents the message submitted to VM that includes a list of activities(Activity.java) that should be performed at the VM (Processing and Transmission). Furthermore, in some senarios, one request could include another request for the subsequent requests which will be performed at the other VMs.
  
## Workload Processing Workflow
The following details the lifecycle of a workload request within CloudSimSDN:

1. **Submission**: `SDNBroker` parses CSV workload files and schedules `WORKLOAD_SUBMIT` events for individual `Workload` objects.
2. **Datacenter Arrival**: `SDNDatacenter` receives the submission and retrieves the corresponding `Request` pipeline.
3. **Activity Decomposition**: Each `Request` contains a chain of `Processing` (CPU) and `Transmission` (Network) activities.
4. **Execution Pipeline**:
    - **CPU Processing**: `SDNDatacenter` generates a `Cloudlet` for the `Processing` activity and submits it to the target VM's scheduler.
    - **Network Transmission**: Upon CPU completion, the next activity is often a `Transmission`. `SDNDatacenter` asks the `NOS` (Network Operating System) for the best path between source and destination VMs. A `Packet` is then sent through the simulated network.
5. **Event Chaining**:
    - `checkCloudletCompletion()` monitors finished Cloudlets and triggers `processNextActivity()`.
    - `REQUEST_COMPLETED` events signal the end of a transmission, moving the pipeline forward.
6. **Finalization**: Once all activities in a `Request` are removed, the datacenter sends a `WORKLOAD_COMPLETED` event back to the `SDNBroker`, which increments the completion counter.

> [!NOTE]
> We implemented idempotency guards (`isFinishedProcessed`) to ensure that even with re-routing or multiple event triggers, each workload is counted exactly once.
  
## Input Data
We need to submit three input files to CloudSimSDN: data center configuration (physical topology), resource deployment request (virtual topology), and workloads for VMs.

### Physical topology (Data center configuration)
Configurations of physical hosts, switches and links that consist of SDN-enabled cloud data center. This can input as JSON file.  Please look at sdn-example-physical.json file. 
In this example, data center is configured to operate 100 hosts, 10 edge switches connecting 10 hosts each, and one core switch that connects all edge switches.

* Host nodes
  1. type: "host"
  2. name: name of the host
  3. pes, mips, ram, storage : the host specification
  4. bw: connection bandwidth with the edge switch

* Switch nodes
  1. type: either "core", "aggregate" or "edge"
  2. name: name of the switch
  3. bw: maximum bandwidth support by switch

* Links
  1. source: the name of source node
  2. destination: the name of destination node

### Virtual topology (Resource deployment request)
When customers send VM creation requests to the cloud data center, they provide virtual topology for their network QoS and SLA. Virtual topology consists of VM types and virtual links between VMs. This can input as JSON file. Please look at sdn-example-virtual.json file.

The resource deployment file includes 500 VM creation requests in which three to five VMs are grouped in a same virtual network to communicate with each other. 

* Nodes
  1. type: "vm"
  2. name: name of the vm
  3. pes, mips, ram, size: the VM specification
* Links
  1. name: the name of the link that can be used in workloads. For default link, use "default"
  2. source: the name of source VM
  3. destination: the name of destination VM
  4. bandwidth (optional): specifically requested bandwidth for the link

### Workloads (workload.csv)
After VMs are created in the data center, computation and network transmission workloads from end-users are passed to VMs to be processed. A workload consists of compute processing and network transmission. This can input as CSV file.
Please look at `sdn-example-workload-*.csv` files

Workload file has a long packet transmission between VMs in a same virtual network. Since we should measure power consumption of switches, data transmissions between VMs are necessary to let switches work for the experiment time. To make the experiment simple, we make VMs use network bandwidth in full during their lifetime, so that just one long packet transmission workload for each VM is given in the workload file.

* CSV file structure
  1. Submission time
  2. Submission VM (VM1)
  3. Packet size of the transmission to VM1 (use 0)
  4. Computational workload for VM1
  5. The name of virtual link to transfer packet to the next VM (VM2)
  6. The next VM (VM2)
  7. Packet size of the transmission to VM2
  8. Computational workload for VM2
  9. ... (repeat v ~ viii)

**A tutorial for 3-tier web application (wikipedia) workloads:**
https://github.com/Cloudslab/sfcwikiworkload

## Simulation Execution
You have to build the project using your IDE or typing `mvn clean install` at the project's root directory.
After that, to execute the example, enter the project's `target` directory and use the following command:

```
java -cp cloudsimsdn-1.0-with-dependencies.jar org.cloudbus.cloudsim.sdn.example.SDNExample <LFF|MFF> [physical.json] [virtual.json] [workload1.csv] [workload2.csv] [...]
```

* ```<LFF | MFF>```: Choose VM placement policy. LFF(Least Full First) or MFF(Most Full First)
* ```[physical.json]```: Filename of physical topology (data center configuration)
* ```[virtual.json]```: Filename of virtual topology (VM creation and network request)
* ```[workload1.csv] ...```: Filenames of workload files. Multiple files can be supplied.

### EXAMPLE:
```
java -cp cloudsimsdn-1.0-with-dependencies.jar org.cloudbus.cloudsim.sdn.example.SDNExample MFF ../dataset-energy/energy-physical.json ../dataset-energy/energy-virtual.json ../dataset-energy/energy-workload.csv > results.out
```

This command will run the simulation using MFF algorithm, and the output is redirected to results.out file.

## Post-Processing Scripts
Analysis tools are located in the `tools/analysis/` directory:
- `tools/analysis/consolidated_report.py`: Primary tool to generate PDF/PNG reports from experiment data.
- `tools/analysis/parse_log_summaries.py`: New tool to extract metrics (Latency, Energy) from simulation logs.
- `tools/analysis/generate_simvf_figures.py`: Tool for Sim VF analytical figures.

## Simulation results
The results have five parts.

* Part 1) Detailed result of workloads: shows computational time and transmission time of each workload components. It also shows total response time of each workload.
* Part 2) Average result of workloads: shows the total number of workloads, average rate of all workload requests, and the average response time.
* Part 3) Host power consumption and detailed utilization: shows total power consumption and detailed utilization history (in MIPS) for each host
* Part 4) Switch power consumption and detailed utilization: shows total power consumption and detailed utilization history (in number of active ports) for each switch
* Part 5) Total power consumption: shows total power consumption over the data center with the maximum hosts utilized at the same time

### EXAMPLE:
* Part 1 / 2) In our example, part 1 and 2 (for workload results) is not useful; because the workload is generated solely to make switches work for the whole lifetime of communicating VMs. 
* Part 3 / 4)
```
Host #0: 29653.168930555563
0.0, 4000.0
0.0, 16000.0
0.0, 35200.0
0.0, 51200.0
2390.0, 55200.0
2423.0, 59200.0
...
Switch #103: 27511.461264316662
22660.21001, 2
90180.21001, 3
502117.0, 2
1458312.66651, 0
```
Part 3 and 4 shows the detailed power consumption and utilization level of each host or switch. 
For Host #0, it consumed 29,653 Wh which hosted 4 VMs at the time 0. From the time 0 until 2390, the host utilized 51200 MIPS. 
For Switch #103, it consumed 27,511 Wh for the whole experiment. No traffic was occurred until the time 22660, and 2 ports were active between 22660 and 90180 seconds.

* Part 5)
```
========== TOTAL POWER CONSUMPTION ===========
Host energy consumed: 1848038.3846250002
Switch energy consumed: 92493.37391543222
Total energy consumed: 1940531.7585404324
Simultanously used hosts:30
```
Part 5 is the main result of this example. Using MFF policy, total energy consumption of the data center was 1,940,531Wh and at most 30 hosts were used at the same time.
 
To compare with the result of LFF policy, run the same program with 'LFF' parameter instead of 'MFF'. The result shows that 2,508,871Wh was consumed with LFF policy.
 
## Generate different scenarios
1. Use topology generators (org.cloudbus.cloudsim.sdn.example.topogenerators) to create more complex scenario in larger scale.
2. Implement different VM allocation policy to test different VM placement algorithms
3. Implement different NetworkOperatingSystem to test different network policies.

## Publication
For the newest Edge computing, NFV and SFC version, please cite this paper:
* Jungmin Son, TianZhang He, and Rajkumar Buyya, ["CloudSimSDN-NFV: Modeling and Simulation of Network Function Virtualization and Service Function Chaining in Edge Computing Environments"](https://doi.org/10.1002/spe.2755), Software: Practive and Experience. 2019;1–17.https://doi.org/10.1002/spe.2755

Please cite this paper:
* Jungmin Son, Amir Vahid Dastjerdi, Rodrigo N. Calheiros, Xiaohui Ji, Young Yoon, and Rajkumar Buyya, ["CloudSimSDN: Modeling and Simulation of Software-Defined Cloud Data Centers"](http://ieeexplore.ieee.org/document/7152513/), Proceedings of the 15th IEEE/ACM International Symposium on Cluster, Cloud and Grid Computing (CCGrid 2015), Shenzhen, China, May 4-7, 2015. doi:10.1109/CCGrid.2015.87

---

# 🚀 Version Finale & Guide de Recherche (IT30)

Cette section documente la structure finale du projet suite à la restructuration du 18 avril 2026.

## Structure du Projet

```text
.
├── datasets/               # Topologies physiques et virtuelles (JSON/CSV)
├── docs/                   # Documentation et historique des itérations
├── src/                    # Code source Java (Core CloudSimSDN)
├── tools/                  # Écosystème d'outils (Scripts de pilotage)
│   ├── analysis/           # Post-traitement Python (Rapports, Plots, Logs)
│   ├── simulation/         # Scripts PowerShell (Benchmarks, Campagnes)
│   └── topology/           # Générateurs de topologie (Congestion, Asymétrie)
└── results/                # Sorties de simulation (ignoré par Git)
```

## Évaluation de LinkSelectionPolicyDynamicLatencyBw

The goal of this final version is to validate the **`DynLatBw`** policy (M/M/1 model) against congestion.

### 1. Lancer le Benchmark Complet (Nightly)
Utilisez le script de benchmark automatisé pour tester toutes les combinaisons (27 runs par dataset).
```powershell
./tools/simulation/run_benchmark.ps1
```powershell
# Figures consolidées (PDF/PNG)
python tools/analysis/consolidated_report.py --results-dir results/2026-04-19/dataset-small-congested/

# Matrices Excel et Master Logs
python tools/analysis/generate_excel_matrices.py
python tools/analysis/merge_raw_logs.py
```

*Dernière mise à jour : 19 avril 2026 (IT 25)*
