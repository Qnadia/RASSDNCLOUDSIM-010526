param([switch]$SkipRuns)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ROOT    = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$VM_POLICIES = @("MFF", "LFF", "LWFF")
$WF      = "Priority"
$LINKS   = @("First","BwAlloc","Dijkstra","DynLatBw")
$DATASET = "datasets/dataset-large"

. "$ROOT\config_home.ps1"

$MAIN = "org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth"
$depCp = (Get-Content "$ROOT\cp.txt" -Raw).Trim()
$mavenDeps = (Get-ChildItem "$ROOT\target\dependency\*.jar" | Select-Object -ExpandProperty FullName) -join ";"
$fullCp = "$ROOT\target\classes;$ROOT\lib\cloudsim-4.0.jar;$mavenDeps;$depCp"

$results = @()

if (-not $SkipRuns) {
    Write-Host "[BENCHMARK] dataset: $DATASET | VMs=$($VM_POLICIES -join ', ') | WF=$WF" -ForegroundColor Cyan
    foreach ($vm_policy in $VM_POLICIES) {
        foreach ($link in $LINKS) {
            Write-Host "  Run: $vm_policy / $link / $WF" -ForegroundColor Yellow
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            New-Item -ItemType Directory -Force -Path "$ROOT\logs" | Out-Null
            $ts      = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
            $dsName = (Split-Path $DATASET -Leaf)
            $logFile = "$ROOT\logs\bench_$($vm_policy)_$($link)_$($WF)_$($dsName)_$ts.log"
            $errFile = "$ROOT\logs\bench_$($vm_policy)_$($link)_$($WF)_$($dsName)_$ts.err"
            $proc = Start-Process -FilePath $global:JAVA_EXE `
                -ArgumentList "-cp", "`"$fullCp`"", $MAIN, $vm_policy, $link, $WF, $DATASET `
                -RedirectStandardOutput $logFile `
                -RedirectStandardError  $errFile `
                -WorkingDirectory $ROOT `
                -PassThru -NoNewWindow
            $proc | Wait-Process
            $sw.Stop()
            $dur    = [math]::Round($sw.Elapsed.TotalSeconds, 1)
            $status = if ($proc.ExitCode -eq 0) { "OK" } else { "FAIL" }
            $color  = if ($status -eq "OK") { "Green" } else { "Red" }
            Write-Host "    => $status | ${dur}s" -ForegroundColor $color
            $results += [PSCustomObject]@{
                Dataset=$DATASET; VM=$vm_policy; Link=$link; WF=$WF
                Status=$status; Duration="${dur}s"; Log=$logFile
            }
        }
    }
    $csvPath = "$ROOT\results\dynlatbw_small_bench.csv"
    New-Item -ItemType Directory -Force -Path "$ROOT\results" | Out-Null
    $results | Export-Csv -Path $csvPath -NoTypeInformation -Delimiter ";"
    Write-Host "[OK] Recap: $csvPath" -ForegroundColor Green
}

Write-Host "[ANALYSIS] Generating figures..." -ForegroundColor Cyan
$dateStr = Get-Date -Format "yyyy-MM-dd"
$rDir = "$ROOT\results\$dateStr\dataset-large-parralelisme"
if (Test-Path $rDir) {
    python -X utf8 "$ROOT\tools\analysis\consolidated_report.py" --results-dir "$rDir"
    Write-Host "[OK] Figures: $rDir\figures_consolidated\" -ForegroundColor Green
} else {
    Write-Host "[SKIP] Not found: $rDir" -ForegroundColor Yellow
}
Write-Host "[DONE]" -ForegroundColor Cyan