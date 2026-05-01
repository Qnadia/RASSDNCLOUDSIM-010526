import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec

col = ['time','host_id','cpu_util','ram_util','bw_util','energy']

print("=" * 70)
print("EXPERT DIAGNOSTIC: CPU/RAM TIME SERIES — First vs BLA")
print("=" * 70)

import os
import argparse
import glob

def run_diagnostic(results_dir):
    datasets = [d for d in os.listdir(results_dir) if os.path.isdir(os.path.join(results_dir, d)) and d.startswith("dataset-")]
    
    for ds_name in datasets:
        ds_path = os.path.join(results_dir, ds_name)
        # Search for LFF in raw/ or directly in ds_path
        raw_path = os.path.join(ds_path, "raw")
        base = os.path.join(raw_path, "LFF") if os.path.exists(os.path.join(raw_path, "LFF")) else os.path.join(ds_path, "LFF")
        
        if not os.path.exists(base):
            continue
            
        f_dir = os.path.join(base, "experiment_LFF_First_Priority")
        b_dirs = glob.glob(os.path.join(base, "experiment_LFF_BLA_Priority")) + \
                 glob.glob(os.path.join(base, "experiment_LFF_DynLatBw_Priority"))
        
        b_dir = b_dirs[0] if b_dirs else None
        if not b_dir or not os.path.exists(f_dir):
            continue

        plot_dir = os.path.join(ds_path, "plot")
        os.makedirs(plot_dir, exist_ok=True)

        try:
            df_f = pd.read_csv(os.path.join(f_dir, 'host_utilization.csv'), sep=';', comment='#', header=None, names=col)
            df_b = pd.read_csv(os.path.join(b_dir, 'host_utilization.csv'), sep=';', comment='#', header=None, names=col)
        except:
            continue

        for df in [df_f, df_b]:
            df['time'] = pd.to_numeric(df['time'], errors='coerce')
            df['cpu_util'] = pd.to_numeric(df['cpu_util'], errors='coerce')
            df['ram_util'] = pd.to_numeric(df['ram_util'], errors='coerce')
            df['energy'] = pd.to_numeric(df['energy'], errors='coerce')

        print(f"\n{'='*60}")
        print(f" DATASET: {ds_name} | LFF + Priority")
        print(f"{'='*60}")

        t_end_f = df_f['time'].max()
        t_end_b = df_b['time'].max()
        
        ts_f = df_f.groupby('time')[['cpu_util', 'ram_util']].mean().reset_index()
        ts_b = df_b.groupby('time')[['cpu_util', 'ram_util']].mean().reset_index()
        
        # CPU Integral
        cpu_integral_f = (ts_f['cpu_util'].values[:-1] * ts_f['time'].diff().dropna().values).sum()
        cpu_integral_b = (ts_b['cpu_util'].values[:-1] * ts_b['time'].diff().dropna().values).sum()
        
        print(f"  Duration: First={t_end_f:.1f}s | BLA={t_end_b:.1f}s (Gain: {(t_end_f-t_end_b)/t_end_f*100:.1f}%)")
        print(f"  CPU Integral: First={cpu_integral_f:.2f} | BLA={cpu_integral_b:.2f} (Gain: {(cpu_integral_f-cpu_integral_b)/cpu_integral_f*100:.1f}%)")

        fig, axes = plt.subplots(2, 2, figsize=(16, 10))
        fig.suptitle(f"CPU/RAM Time Series: First vs BLA — {ds_name} (LFF+Priority)", fontsize=16, fontweight='bold')
        
        for i, (col_plot, label, row) in enumerate([('cpu_util','CPU Utilization (%)' ,0), ('ram_util','RAM Utilization (%)',1)]):
            ts_first = df_f.groupby('time')[col_plot].mean()
            ts_bla   = df_b.groupby('time')[col_plot].mean()
            
            axes[row,0].plot(ts_first.index, ts_first.values, label='First', color='#d62728', alpha=0.7)
            axes[row,0].plot(ts_bla.index,   ts_bla.values,   label='BLA',   color='#1f77b4', alpha=0.7)
            axes[row,0].set_title(f"{label} — Time Series")
            axes[row,0].legend()
            
            ts_first_cum = (ts_first * ts_first.index.to_series().diff().fillna(0)).cumsum()
            ts_bla_cum   = (ts_bla   * ts_bla.index.to_series().diff().fillna(0)).cumsum()
            axes[row,1].plot(ts_first.index, ts_first_cum.values, label='First', color='#d62728')
            axes[row,1].plot(ts_bla.index,   ts_bla_cum.values,   label='BLA',   color='#1f77b4')
            axes[row,1].set_title(f"{label} — Cumulative Integral")
            axes[row,1].legend()

        plt.tight_layout()
        out = os.path.join(plot_dir, 'cpu_ram_timeseries_lff.png')
        plt.savefig(out, dpi=200)
        plt.close()
        print(f"  Graph saved: {out}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir", help="Results directory (e.g. results/2026-05-01)")
    args = parser.parse_args()
    run_diagnostic(args.results_dir)

