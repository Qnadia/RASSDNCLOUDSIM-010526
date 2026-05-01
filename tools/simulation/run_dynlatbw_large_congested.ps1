param([switch]$SkipRuns)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ROOT    = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# Configuration pour le dataset Large Congestionné
$VM_POLICIES = @("MFF") # On se concentre sur MFF pour gagner du temps, ou @("MFF", "LFF")
$WF      = "Priority"
$LINKS   = @("First", "Dijkstra", "DynLatBw")
$DATASET = "datasets/dataset-large-congested"

. "$ROOT\config_home.ps1"

$MAIN = "org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth"
$depCp = (Get-Content "$ROOT\cp.txt" -Raw).Trim()
$mavenDeps = (Get-ChildItem "$ROOT\target\dependency\*.jar" | Select-Object -ExpandProperty FullName) -join ";"
$fullCp = "$ROOT\target\classes;$ROOT\lib\cloudsim-4.0.jar;$mavenDeps;$depCp"

$results = @()

if (-not $SkipRuns) {
    Write-Host "[BENCHMARK LARGE CONGESTED] dataset: $DATASET" -ForegroundColor Cyan
    foreach ($vm_policy in $VM_POLICIES) {
        foreach ($link in $LINKS) {
            Write-Host "  Run: $vm_policy / $link / $WF" -ForegroundColor Yellow
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            
            $ts      = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
            $logFile = "$ROOT\logs\large_congested_$($vm_policy)_$($link)_$ts.log"
            $errFile = "$ROOT\logs\large_congested_$($vm_policy)_$($link)_$ts.err"
            
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
    
    $csvPath = "$ROOT\results\large_congested_bench_summary.csv"
    $results | Export-Csv -Path $csvPath -NoTypeInformation -Delimiter ";"
    Write-Host "[OK] Recap: $csvPath" -ForegroundColor Green
}

Write-Host "[ANALYSIS] Generating figures..." -ForegroundColor Cyan
# On attend que l'utilisateur lance l'analyse manuellement ou on pointe vers le dossier de sortie habituel
Write-Host "[DONE]" -ForegroundColor Cyan
