# run_full_cycle.ps1
$ErrorActionPreference = "Stop"
$env:PYTHONIOENCODING="utf-8"

Write-Host "STARTING SIMULATION CYCLE"

$dateStr = Get-Date -Format "yyyy-MM-dd"
$resultPath = Join-Path "results" $dateStr
if (!(Test-Path $resultPath)) { New-Item -ItemType Directory -Path $resultPath }

# Step 0: Topology Visualization
python tools/analysis/0_generate_topology_view.py --dataset datasetsH/dataset-medium --out-dir $resultPath

# Step 1: Simulations
python tools/analysis/1_run_nightly_benchmarks.py

Write-Host "TARGET DIR: $resultPath"

# Step 2: Consolidating
python tools/analysis/2_consolidate_results.py $resultPath

# Step 3: Plotting
python tools/analysis/3_generate_global_plots.py $resultPath

# Step 4: Reporting (MD)
python tools/analysis/4_generate_premium_report.py $resultPath

# Step 5: PDF Conversion
python tools/analysis/5_generate_pdf_report.py --results-dir $resultPath

Write-Host "ALL DONE"
