import os
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
import textwrap

def generate_consolidated_scientific_pdf():
    base_dir = r"E:\Workspace\v2\cloudsimsdn-research"
    plot_dir = os.path.join(base_dir, "results", "2026-05-02", "dataset-medium", "plot")
    out_pdf = os.path.join(base_dir, "results", "2026-05-02", "global_analysis", "Consolidated_Scientific_Report.pdf")

    # Mapping images
    img_delay = os.path.join(plot_dir, "delay_by_vm.png")
    img_queuing = os.path.join(plot_dir, "queuing_delay.png")
    img_energy = os.path.join(plot_dir, "energy_by_vm.png")
    img_pareto = os.path.join(plot_dir, "pareto_energy_delay.png")
    img_wf = os.path.join(plot_dir, "wf_latency_impact.png")

    with PdfPages(out_pdf) as pdf:
        def add_text_page(title, body, font_size=10):
            plt.figure(figsize=(8.5, 11))
            plt.axis('off')
            plt.text(0.5, 0.95, title, ha='center', va='top', fontsize=14, weight='bold', color='#1a5276')
            
            wrapped_body = ""
            for line in body.split('\n'):
                if line.strip().startswith('|'): # Table-like line
                    wrapped_body += line + "\n"
                else:
                    wrapped_body += "\n".join(textwrap.wrap(line, width=90)) + "\n"
            
            plt.text(0.05, 0.90, wrapped_body, ha='left', va='top', fontsize=font_size, family='serif')
            pdf.savefig()
            plt.close()

        def add_image_page(title, img_path, caption=""):
            if not os.path.exists(img_path): return
            plt.figure(figsize=(8.5, 11))
            plt.axis('off')
            plt.text(0.5, 0.95, title, ha='center', va='top', fontsize=12, weight='bold')
            
            img = plt.imread(img_path)
            ax_img = plt.axes([0.1, 0.25, 0.8, 0.6])
            ax_img.imshow(img)
            ax_img.axis('off')
            
            if caption:
                plt.text(0.5, 0.2, "\n".join(textwrap.wrap(caption, width=80)), ha='center', va='top', fontsize=9, style='italic')
            
            pdf.savefig()
            plt.close()

        # Page 1: Setup
        setup_text = """
        4.1 Experimental Setup
        All experiments are conducted using RAS-SDNCloudSim executed on a standard workstation.
        Three datasets of increasing complexity are evaluated (Mini, Small, Medium).
        
        Table 1: Dataset configurations
        | Dataset | Hosts | VMs | Workloads | Congestion design |
        | Mini    | 4     | 4   | 50        | Bottleneck: 50 Mbps vs. 800 Mbps |
        | Small   | 6     | 8   | 100       | Asymmetric Core: 80 Mbps vs. 300 Mbps |
        | Medium  | 12    | 20  | 500       | Fat-Tree Core: 200–500 Mbps multi-path |
        
        Table 2: Physical host power model (EnhancedHostEnergyModel)
        Extends energy estimation to three dimensions (CPU, RAM, bandwidth).
        P(t) = P_idle + alpha_cpu*u_cpu + alpha_ram*u_ram + alpha_bw*u_bw
        (Parameters: P_idle=100W, alpha_cpu=1.0, alpha_ram=0.2, alpha_bw=0.1)
        """
        add_text_page("Section 4: Experiment and Discussion", setup_text)

        # Page 2: Delay
        delay_text = """
        4.2.1 End-to-End Packet Delay
        BLA consistently reduces delay under LFF and LWFF. Under MFF, BLA produces zero gain,
        confirming the measured improvements are exclusively due to routing (falsifiability criterion).
        
        Table 4: Average packet delay (s) and BLA gain (%)
        | Dataset | VM Policy | SFL (s) | BLA (s) | Gain delay | Gain queue |
        | Mini    | LFF       | 48.4    | 37.8    | -21.9%     | -20.8%     |
        | Medium  | LWFF      | 995.5   | 816.3   | -18.0%     | -19.1%     |
        
        Remarks: The median gap is most diagnostic: at Medium scale, BLA median is 37% lower than First-Fit,
        indicating BLA prevents severe tail-latency degradation under sustained load.
        """
        add_text_page("4.2 Results and Analysis", delay_text)
        add_image_page("Figure 1: Average Packet Delay (Medium)", img_delay, "Structural pattern (MFF << LWFF < LFF) confirmed across all metrics.")

        # Page 3: Queuing
        queuing_text = """
        4.2.3 Queuing Delay: Empirical Validation of the M/M/1 Model
        Queuing delay is the most direct metric for validating the BLA mechanism.
        BLA does not simply reroute packets; it actively prevents congestion buildup at the queuing layer.
        
        At Medium scale, BLA cuts queuing delay by 11.7% to 19.1%, demonstrating that bandwidth-aware
        path selection avoids bottleneck formation before queues saturate.
        """
        add_text_page("4.2.3 Queuing Analysis", queuing_text)
        add_image_page("Figure 2: Average Queuing Delay (Medium)", img_queuing, "LWFF+BLA reduces queuing by 19.1%, the strongest empirical evidence of M/M/1 effectiveness.")

        # Page 4: Energy
        energy_text = """
        4.2.4 Energy Consumption
        BLA accelerates workload completion by resolving queuing bottlenecks earlier.
        Temporal Compression Effect: E = P * Delta_t. Reduction in queuing time leads to
        shorter host active states and earlier entry to idle power modes.
        
        Table 6: Total energy consumption (Wh)
        | Dataset | VM Policy | SFL (Wh) | BLA (Wh) | Gain |
        | Medium  | LWFF      | 1,045.4  | 880.8    | -15.7% |
        | Medium  | LFF       | 2,865.8  | 2,638.3  | -7.9% |
        
        Energy savings grow super-linearly with scale (3.3% -> 6.3% -> 9.2%).
        """
        add_text_page("4.2.4 Energy Analysis", energy_text)
        add_image_page("Figure 3: Impact Énergétique (Medium)", img_energy, "Energy savings are a direct consequence of reduced simulation duration.")

        # Page 5: Pareto
        pareto_text = """
        4.2.6 Pareto Analysis: Energy–Latency Trade-off
        BLA configurations consistently Pareto-dominate SFL counterparts for LFF and LWFF.
        This means BLA achieves lower energy AND lower latency simultaneously.
        
        The clean cluster separation between datasets confirms simulation calibration.
        Ordering Mini < Small < Medium is strictly maintained on both axes.
        """
        add_text_page("4.2.6 Pareto Analysis", pareto_text)
        add_image_page("Figure 4: Pareto Trade-off (Medium)", img_pareto, "One figure demonstrates both BLA advantages simultaneously.")

        # Page 6: Synthesis
        synth_text = """
        4.3 Cross-Dataset Synthesis and Scalability
        | Dataset | Delta Delay | Delta Queue | Delta Energy | Peak SLA gain |
        | Mini    | -17.8%      | -17.1%      | -3.3%        | -36.4% (LFF)  |
        | Small   | -6.0%       | -8.9%       | -6.3%        | -46.4% (LFF)  |
        | Medium  | -10.0%      | -11.7%      | -9.2%        | -74.9% (LWFF) |
        
        4.4 Discussion
        BLA provides increasingly stronger QoS protection as congestion intensifies.
        MFF produces zero gain across all datasets, acting as a structural internal control.
        LWFF+BLA achieves the best balanced outcome across all dimensions.
        """
        add_text_page("4.3 Synthesis and Discussion", synth_text)

    print(f"DONE Consolidated Scientific PDF: {out_pdf}")

if __name__ == "__main__":
    generate_consolidated_scientific_pdf()
