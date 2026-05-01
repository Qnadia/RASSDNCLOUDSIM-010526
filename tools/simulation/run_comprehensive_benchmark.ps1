# =============================================================
# run_comprehensive_benchmark.ps1
# Cible : Comparaison exhaustive des politiques
# VM (LFF, MFF) x Link (First, BwAllocN) x WF (Priority, RR, PSO, SJF)
# =============================================================

. "$PSScriptRoot\config_home.ps1"

$MAIN = 'org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth'
$WORK_DIR = $PSScriptRoot

$mavenCpTxt = "$PSScriptRoot\cp.txt"
if (!(Test-Path $mavenCpTxt)) {
    Write-Host " [Build] Generation du classpath dynamique via Maven..."
    & mvn dependency:build-classpath "-Dmdep.outputFile=$mavenCpTxt" -q
}
$depCp = Get-Content $mavenCpTxt -Raw
$depCp = $depCp.Trim()

$targetClasses = "$PSScriptRoot\target\classes"
$cloudsimJar = "$PSScriptRoot\lib\cloudsim-4.0.jar"
$mavenDeps = (Get-ChildItem "$PSScriptRoot\target\dependency\*.jar" | Select-Object -ExpandProperty FullName) -join ';'
$fullCp = "$targetClasses;$cloudsimJar;$mavenDeps;$depCp"

# Configuration de la comparaison
$VM_POLICIES       = @("LFF", "MFF")
$LINK_POLICIES     = @("First", "BwAllocN")
$WORKLOAD_POLICIES = @("Priority", "RR", "PSO", "SJF")
$DATASET           = "dataset-redundant"

$total = $VM_POLICIES.Count * $LINK_POLICIES.Count * $WORKLOAD_POLICIES.Count
$done  = 0
$summary = @()

Write-Host "`n====================================================="
Write-Host "  Comparaison Exhaustive de Tous les Algorithmes"
Write-Host "  Total planned combinations: $total"
Write-Host "=====================================================`n"

foreach ($vm in $VM_POLICIES) {
    foreach ($lnk in $LINK_POLICIES) {
        foreach ($wf in $WORKLOAD_POLICIES) {
            $done++
            $expName = "experiment_${vm}_${lnk}_${wf}_${DATASET}"
            $logFile = "$WORK_DIR\${expName}.log"
            $errLog  = "$WORK_DIR\${expName}.err.log"
            
            Write-Host "[$done/$total] >> $expName ..." -NoNewline

            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                $process = Start-Process -FilePath $global:JAVA_EXE -ArgumentList "-cp", "`"$fullCp`"", $MAIN, $vm, $lnk, $wf, $DATASET -RedirectStandardOutput $logFile -RedirectStandardError $errLog -PassThru -NoNewWindow
                
                $waitResult = $process | Wait-Process -Timeout 300 -ErrorAction SilentlyContinue
                
                if (!$process.HasExited) {
                    $process | Stop-Process -Force
                    $status = "TIMEOUT(300s)"
                } elseif ($process.ExitCode -eq 0) {
                    $status = "OK"
                } else {
                    $status = "ERROR($($process.ExitCode))"
                }
            } catch {
                $status = "EXCEPTION: $($_.Exception.Message)"
            }
            $sw.Stop()
            $dur = "$([int]$sw.Elapsed.TotalSeconds)s"
            Write-Host " $status ($dur)"
            
            $summary += [PSCustomObject]@{
                Experiment = $expName
                VM         = $vm
                Link       = $lnk
                Workload   = $wf
                Dataset    = $DATASET
                Status     = $status
                Duration   = $dur
            }
        }
    }
}

Write-Host "`nBatch complete! Generation du rapport de consolidation..."
python.exe "$WORK_DIR\Python-V2\consolidated_report.py"

$summaryPath = "$WORK_DIR\comprehensive_benchmark_summary.csv"
$summary | Export-Csv -Path $summaryPath -NoTypeInformation -Delimiter ";"
Write-Host "Resume enregistre dans : $summaryPath"
Write-Host "Graphiques disponibles dans : $WORK_DIR\figures_consolidated\"
