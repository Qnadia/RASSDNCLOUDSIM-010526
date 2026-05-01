# =============================================================
# run_final_rerun_campaign.ps1
# Campagne de simulation unifiée (27 configurations)
# Utilise Dijkstra pour toutes les simulations "dynamiques"
# =============================================================

. "$PSScriptRoot\config_home.ps1"

$MAIN     = 'org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth'
$WORK_DIR = $PSScriptRoot

# -- Classpath -----------------------------------------------------
$mavenCpTxt = "$PSScriptRoot\cp.txt"
$depCp      = (Get-Content $mavenCpTxt -Raw).Trim()
$targetClasses = "$PSScriptRoot\target\classes"
$cloudsimJar   = "$PSScriptRoot\lib\cloudsim-4.0.jar"
$mavenDeps     = (Get-ChildItem "$PSScriptRoot\target\dependency\*.jar" | Select-Object -ExpandProperty FullName) -join ';'
$fullCp        = "$targetClasses;$cloudsimJar;$mavenDeps;$depCp"

# -- Paramètres ----------------------------------------------------
$DATASETS          = @("dataset-small", "dataset-medium", "dataset-large")
$VM_POLICIES       = @("LFF", "MFF", "LWFF")
$LINK_POLICIES     = @("First", "BwAllocN", "Dijkstra")
$WORKLOAD_POLICIES = @("Priority", "SJF", "RoundRobin", "PSO")
$TIMEOUT_SEC       = 3600

$TODAY     = "2026-04-14"
$RESULT_ROOT = "$PSScriptRoot\results\$TODAY"
if (-not (Test-Path $RESULT_ROOT)) { New-Item -ItemType Directory -Path $RESULT_ROOT -Force }

$summaryFile = "$RESULT_ROOT\final_rerun_summary.csv"
"Dataset;VM;Link;Workload;Status;Duration;LogFile" | Out-File -FilePath $summaryFile -Encoding utf8

$totalTasks = $DATASETS.Count * $VM_POLICIES.Count * $LINK_POLICIES.Count * $WORKLOAD_POLICIES.Count
$current    = 0

Write-Host "`n====================================================="
Write-Host "  GRAND RERUN CLEAN - CAMPAGNE UNIFIEE"
Write-Host "  Total planned simulations: $totalTasks"
Write-Host "=====================================================`n"

foreach ($ds in $DATASETS) {
    foreach ($vm in $VM_POLICIES) {
        foreach ($lp in $LINK_POLICIES) {
            foreach ($wf in $WORKLOAD_POLICIES) {
                $current++
                $expName = "exp_final_${ds}_${vm}_${lp}_${wf}"
                $timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
                $logFile = "$RESULT_ROOT\${expName}_${timestamp}.log"
                $errLog  = "$RESULT_ROOT\${expName}_${timestamp}.err.log"

                Write-Host "[$current/$totalTasks] >> $expName ..." -NoNewline

                $sw = [system.diagnostics.stopwatch]::StartNew()
                try {
                    $process = Start-Process -FilePath $global:JAVA_EXE `
                        -ArgumentList "-cp", "`"$fullCp`"", $MAIN, $vm, $lp, $wf, $ds `
                        -RedirectStandardOutput $logFile `
                        -RedirectStandardError  $errLog `
                        -PassThru -NoNewWindow

                    $exited = $process | Wait-Process -Timeout $TIMEOUT_SEC -ErrorAction SilentlyContinue

                    if (-not $process.HasExited) {
                        $process | Stop-Process -Force
                        $status = "TIMEOUT"
                    } elseif ($process.ExitCode -eq 0) {
                        $status = "OK"
                    } else {
                        $status = "ERROR($($process.ExitCode))"
                    }
                } catch {
                    $status = "EXCEPTION"
                }
                $sw.Stop()
                $duration = [Math]::Round($sw.Elapsed.TotalSeconds, 1)

                Write-Host " $status ($($duration)s)"
                
                # Save to summary
                "$ds;$vm;$lp;$wf;$status;$duration;$logFile" | Out-File -FilePath $summaryFile -Append -Encoding utf8
            }
        }
    }
}

Write-Host "`n====================================================="
Write-Host "  CAMPAGNE TERMINEE"
Write-Host "  Rapport: $summaryFile"
Write-Host "====================================================="

# Mise à jour automatique de Sim VF et du rapport Premium
powershell -ExecutionPolicy Bypass -File ".\organize_sim_vf.ps1"
powershell -ExecutionPolicy Bypass -File ".\extract_metrics.ps1"
python .\generate_premium_report.py
