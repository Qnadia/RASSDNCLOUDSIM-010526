import os, re, base64
from markdown_pdf import MarkdownPdf, Section
from PIL import Image
import io

def generate_scientific_pdf():
    base_dir = r"E:\Workspace\v2\cloudsimsdn-research"
    plot_dir = os.path.join(base_dir, "results", "2026-05-02", "dataset-medium", "plot")
    out_pdf = os.path.join(base_dir, "results", "2026-05-02", "global_analysis", "Scientific_Article_Section_4.pdf")

    def get_b64(filename, width=1200):
        p = os.path.join(plot_dir, filename)
        if not os.path.exists(p): 
            p = os.path.join(base_dir, "results", "2026-05-02", "global_analysis", "plot_consolidated", filename)
            if not os.path.exists(p):
                print(f"Warning: {filename} not found.")
                return ""
        
        img = Image.open(p)
        w, h = img.size
        if w > width:
            new_h = int(h * (width / w))
            img = img.resize((width, new_h), Image.Resampling.LANCZOS)
        
        buf = io.BytesIO()
        img.save(buf, format="PNG", optimize=True)
        return base64.b64encode(buf.getvalue()).decode()

    # Manual HTML build to match Scientific Paper style
    # ESCAPING BRACES for .format()
    html = """
    <html>
    <head>
    <style>
        @page {{ margin: 25mm; }}
        body {{ font-family: 'Times New Roman', Times, serif; line-height: 1.5; color: #000; text-align: justify; font-size: 11pt; }}
        h1 {{ font-size: 18pt; text-align: center; margin-bottom: 20px; text-transform: uppercase; border-bottom: 1px solid #000; padding-bottom: 5px; }}
        h2 {{ font-size: 14pt; border-bottom: 1px solid #ccc; margin-top: 30px; }}
        h3 {{ font-size: 12pt; font-weight: bold; margin-top: 20px; }}
        table {{ width: 100%; border-collapse: collapse; margin: 20px 0; font-size: 10pt; font-family: sans-serif; }}
        th {{ border-top: 2px solid #000; border-bottom: 1px solid #000; padding: 8px; text-align: center; background: #f2f2f2; }}
        td {{ border-bottom: 1px solid #eee; padding: 6px; text-align: center; }}
        .table-caption {{ text-align: center; font-weight: bold; margin-bottom: 5px; font-size: 10pt; }}
        .figure {{ text-align: center; margin: 30px 0; }}
        .figure img {{ max-width: 90%; border: 1px solid #ddd; padding: 5px; }}
        .figure-caption {{ text-align: center; font-style: italic; font-size: 10pt; margin-top: 10px; }}
        .gain {{ color: #1a5276; font-weight: bold; }}
    </style>
    </head>
    <body>
    <h1>4. Experiment and Discussion</h1>
    <div style="text-align:center; font-style:italic; margin-bottom:30px;">Revised following supervisors' remarks (Pr. Tadonki & Pr. Samadi)</div>

    <p>In this section, we evaluate the benefits of the proposed BLA routing policy under realistic simulation conditions, comparing it against the default Select-First-Link (SFL) baseline across four performance metrics: end-to-end packet delay, queuing delay, energy consumption, and SLA severity.</p>

    <h2>4.1 Experimental Setup</h2>
    <p>All experiments are conducted using RAS-SDNCloudSim, our extension of CloudSimSDN, executed on a standard workstation running Java 21 under Windows 11. We evaluate three datasets of increasing complexity (Table 1), each deliberately designed with a bandwidth asymmetry that creates a realistic congestion scenario: SFL blindly selects the first available path and systematically falls into the congested route, while BLA detects saturation via its M/M/1 model and reroutes around it.</p>
    
    <div class="table-caption">Table 1: Dataset configurations</div>
    <table>
        <tr><th>Dataset</th><th>Hosts</th><th>VMs</th><th>Workloads</th><th>Congestion design</th></tr>
        <tr><td>Mini</td><td>4</td><td>4</td><td>50</td><td>Bottleneck: 50 Mbps vs. 800 Mbps backbone</td></tr>
        <tr><td>Small</td><td>6</td><td>8</td><td>100</td><td>Asymmetric Core: 80 Mbps vs. 300 Mbps</td></tr>
        <tr><td>Medium</td><td>12</td><td>20</td><td>500</td><td>Fat-Tree Core: 200–500 Mbps multi-path</td></tr>
    </table>

    <p>The power consumption characteristics of the physical hosts follow the multi-resource model described in Table 2.</p>

    <div class="table-caption">Table 2: Physical host power consumption model (EnhancedHostEnergyModel)</div>
    <table>
        <tr><th>Parameter</th><th>Value</th><th>Unit</th><th>Description</th></tr>
        <tr><td>P_idle</td><td>100</td><td>W</td><td>Baseline idle power</td></tr>
        <tr><td>alpha_cpu</td><td>1.0</td><td>W/%</td><td>Added power per 1% CPU load</td></tr>
        <tr><td>alpha_ram</td><td>0.2</td><td>W/%</td><td>Added power per 1% RAM load</td></tr>
        <tr><td>alpha_bw</td><td>0.1</td><td>W/%</td><td>Added power per 1% BW load</td></tr>
        <tr><td>T_off</td><td>300</td><td>s</td><td>Idle threshold before sleep mode</td></tr>
    </table>

    <p>Table 3 lists the evaluated policies. The full parameter space covers 18 combinations per dataset, yielding 54 simulations in total.</p>

    <div class="table-caption">Table 3: Evaluated policies</div>
    <table>
        <tr><th>Dimension</th><th>Values</th><th>Role</th></tr>
        <tr><td>Link selection</td><td>BLA (proposed) | SFL (baseline)</td><td>Network path selection through the SDN fabric</td></tr>
        <tr><td>VM placement</td><td>LFF | LWFF | MFF</td><td>VM-to-host distribution strategy</td></tr>
        <tr><td>Workload order</td><td>Priority | SJF | PSO</td><td>Cloudlet submission ordering</td></tr>
    </table>

    <h2>4.2 Results and Analysis</h2>
    <h3>4.2.1 End-to-End Packet Delay</h3>
    <p>Table 4 reports the average packet delay per VM policy and dataset. BLA consistently reduces delay under LFF and LWFF. Importantly, BLA produces exactly zero gain under MFF, which confirms that the measured improvements are exclusively attributable to the routing mechanism (falsifiability criterion).</p>

    <div class="table-caption">Table 4: Average packet delay (s) and BLA gain (%) per VM policy</div>
    <table>
        <tr><th>Dataset</th><th>VM Policy</th><th>SFL (s)</th><th>BLA (s)</th><th>Gain delay</th><th>Gain queue</th></tr>
        <tr><td rowspan="3">Mini</td><td>LFF</td><td>48.4</td><td>37.8</td><td class="gain">-21.9%</td><td class="gain">-20.8%</td></tr>
        <tr><td>LWFF</td><td>51.8</td><td>41.7</td><td class="gain">-19.4%</td><td class="gain">-17.8%</td></tr>
        <tr><td>MFF</td><td>16.0</td><td>16.0</td><td>0.0%</td><td>0.0%</td></tr>
        <tr><td rowspan="3">Small</td><td>LFF</td><td>535.3</td><td>494.5</td><td class="gain">-7.6%</td><td class="gain">-8.2%</td></tr>
        <tr><td>LWFF</td><td>341.8</td><td>317.7</td><td class="gain">-7.1%</td><td class="gain">-11.1%</td></tr>
        <tr><td>MFF</td><td>196.0</td><td>196.0</td><td>0.0%</td><td>0.0%</td></tr>
        <tr><td rowspan="3">Medium</td><td>LFF</td><td>856.3</td><td>802.7</td><td class="gain">-6.3%</td><td class="gain">-6.8%</td></tr>
        <tr><td>LWFF</td><td>995.5</td><td>816.3</td><td class="gain">-18.0%</td><td class="gain">-19.1%</td></tr>
        <tr><td>MFF</td><td>474.4</td><td>474.4</td><td>0.0%</td><td>0.0%</td></tr>
    </table>

    <div class="figure">
        <img src="data:image/png;base64,{b64_delay}">
        <div class="figure-caption">Figure 1: Average packet delay (ms) by VM policy and routing strategy --- dataset-medium.</div>
    </div>

    <h3>4.2.2 Network-Pure Latency</h3>
    <p>Table 5 reports the network-only latency of selected paths (queuing + transmission + propagation). BLA achieves near-complete elimination of network latency on Small and Medium scales.</p>

    <div class="table-caption">Table 5: Network-pure latency (ms) --- selected paths</div>
    <table>
        <tr><th>Dataset</th><th>VM Policy</th><th>SFL (ms)</th><th>BLA (ms)</th><th>Gain</th></tr>
        <tr><td rowspan="2">Mini</td><td>LFF</td><td>7,776</td><td>4,248</td><td class="gain">-45.4%</td></tr>
        <tr><td>LWFF</td><td>7,600</td><td>4,248</td><td class="gain">-44.1%</td></tr>
        <tr><td rowspan="2">Small</td><td>LFF</td><td>13,913</td><td>323</td><td class="gain">-97.7%</td></tr>
        <tr><td>LWFF</td><td>8,120</td><td>81</td><td class="gain">-99.0%</td></tr>
        <tr><td rowspan="2">Medium</td><td>LFF</td><td>21,742</td><td>3,884</td><td class="gain">-82.1%</td></tr>
        <tr><td>LWFF</td><td>62,394</td><td>2,656</td><td class="gain">-95.7%</td></tr>
    </table>

    <h3>4.2.3 Queuing Delay: Empirical Validation</h3>
    <p>The queuing delay is the most direct metric for validating the BLA mechanism. Under LWFF/Medium, BLA achieves a 19.1% reduction, steer flows away from the 200 Mbps saturated links toward the 500 Mbps alternatives.</p>

    <div class="figure">
        <img src="data:image/png;base64,{b64_queuing}">
        <div class="figure-caption">Figure 2: Average queuing delay (ms) by VM policy and routing strategy --- dataset-medium.</div>
    </div>

    <h3>4.2.4 Energy Consumption</h3>
    <p>BLA accelerates workload completion by resolving queuing bottlenecks earlier, thereby shortening the window during which hosts remain active. We term this the temporal compression effect.</p>

    <div class="table-caption">Table 6: Total energy consumption (Wh) and BLA gain (%) per VM policy</div>
    <table>
        <tr><th>Dataset</th><th>VM Policy</th><th>SFL (Wh)</th><th>BLA (Wh)</th><th>Gain</th></tr>
        <tr><td>Mini</td><td>LFF</td><td>3.84</td><td>3.56</td><td class="gain">-7.3%</td></tr>
        <tr><td rowspan="2">Small</td><td>LFF</td><td>135.8</td><td>125.8</td><td class="gain">-7.4%</td></tr>
        <tr><td>LWFF</td><td>43.6</td><td>42.0</td><td class="gain">-3.8%</td></tr>
        <tr><td rowspan="2">Medium</td><td>LWFF</td><td>1,045.4</td><td>880.8</td><td class="gain">-15.7%</td></tr>
        <tr><td>LFF</td><td>2,865.8</td><td>2,638.3</td><td class="gain">-7.9%</td></tr>
    </table>

    <div class="figure">
        <img src="data:image/png;base64,{b64_energy}">
        <div class="figure-caption">Figure 3: Impact Énergétique — Consommation par politique de placement (dataset-medium).</div>
    </div>

    <h3>4.2.5 SLA Severity</h3>
    <p>The SLA severity ratio captures the magnitude of the violation. The gain grows strongly with topology scale, confirming that BLA provides increasingly stronger QoS protection as congestion intensifies.</p>

    <div class="table-caption">Table 7: SLA severity (sigma) and BLA gain (%) per VM policy</div>
    <table>
        <tr><th>Dataset</th><th>VM Policy</th><th>sigma SFL</th><th>sigma BLA</th><th>Gain</th></tr>
        <tr><td>Mini</td><td>LFF</td><td>4.21</td><td>2.67</td><td class="gain">-36.4%</td></tr>
        <tr><td>Small</td><td>LFF</td><td>6.37</td><td>3.42</td><td class="gain">-46.4%</td></tr>
        <tr><td>Medium</td><td>LWFF</td><td>8.91</td><td>2.23</td><td class="gain">-74.9%</td></tr>
    </table>

    <h3>4.2.6 Pareto Analysis: Energy–Latency Trade-off</h3>
    <p>The Pareto plane demonstrates that BLA achieves Pareto dominance over SFL routing at all evaluated infrastructure scales.</p>

    <div class="figure">
        <img src="data:image/png;base64,{b64_pareto}">
        <div class="figure-caption">Figure 4: Pareto trade-off: total energy (Wh) vs. average delay (ms) --- dataset-medium.</div>
    </div>

    <h3>4.2.7 Workload Scheduler Impact</h3>
    <p>All three schedulers produce near-identical distributions, confirming that BLA's benefits are robust to the choice of scheduling strategy.</p>

    <div class="figure">
        <img src="data:image/png;base64,{b64_wf}">
        <div class="figure-caption">Figure 5: Packet delay distribution by workload scheduler (boxplot) --- dataset-medium.</div>
    </div>

    <h2>4.3 Cross-Dataset Synthesis and Scalability</h2>
    <div class="table-caption">Table 8: Global BLA gains vs. SFL --- cross-dataset synthesis</div>
    <table>
        <tr><th>Dataset</th><th>Delta Delay</th><th>Delta Queue</th><th>Delta Energy</th><th>Peak SLA gain</th></tr>
        <tr><td>Mini</td><td>-17.8%</td><td>-17.1%</td><td>-3.3%</td><td>-36.4% (LFF)</td></tr>
        <tr><td>Small</td><td>-6.0%</td><td>-8.9%</td><td>-6.3%</td><td>-46.4% (LFF)</td></tr>
        <tr><td>Medium</td><td>-10.0%</td><td>-11.7%</td><td>-9.2%</td><td>-74.9% (LWFF)</td></tr>
    </table>

    <h2>4.4 Discussion</h2>
    <p>BLA works best when the VM placement policy generates sufficient cross-fabric traffic (LFF or LWFF) and the topology offers meaningful path diversity. For balanced workloads, LWFF+BLA achieves the best simultaneous outcome across all four dimensions and is the configuration we recommend for general-purpose cloud deployments.</p>

    </body>
    </html>
    """

    # Embed figures
    b64_delay = get_b64("delay_by_vm.png")
    b64_queuing = get_b64("queuing_delay.png")
    b64_energy = get_b64("energy_by_vm.png")
    b64_pareto = get_b64("pareto_energy_delay.png")
    b64_wf = get_b64("wf_latency_impact.png")

    final_html = html.format(
        b64_delay=b64_delay,
        b64_queuing=b64_queuing,
        b64_energy=b64_energy,
        b64_pareto=b64_pareto,
        b64_wf=b64_wf
    )

    pdf = MarkdownPdf(toc_level=0)
    pdf.add_section(Section(final_html))
    pdf.save(out_pdf)
    print(f"Scientific Article Section PDF generated: {out_pdf}")

if __name__ == "__main__":
    generate_scientific_pdf()
