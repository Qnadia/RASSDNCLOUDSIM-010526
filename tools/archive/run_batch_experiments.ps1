# =============================================================
# run_batch_experiments.ps1 - Automated CloudSimSDN Experiment Runner
# Iterates over all combinations of VM, Link, and Workload policies.
# =============================================================

# -- CONFIG -----------------------------------------------------------
$JAVA     = 'C:\Program Files\Java\jre-1.8\bin\java.exe'
$CP_JAR   = "$PSScriptRoot\target\cloudsimsdn-1.0-with-dependencies.jar"
$MAIN     = 'org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth'
$WORK_DIR = $PSScriptRoot

if (-not (Test-Path $CP_JAR)) {
    Write-Error "JAR not found: $CP_JAR. Please run 'mvn package' first."
    exit
}

# -- POLICY LISTS -----------------------------------------------------
$VM_POLICIES       = @("Binpack", "Spread", "LWFF", "LWFFVD", "LFF", "MFF")
$LINK_POLICIES     = @("DynLatBw", "Random", "BwAllocN", "First")
$WORKLOAD_POLICIES = @("Priority", "SJF", "FCFS", "RR", "PSO")

# -- SELECTION (Optionnel: décommentez pour un test rapide) -----------
# $VM_POLICIES       = @("LFF", "Binpack")
# $LINK_POLICIES     = @("DynLatBw", "Random")
# $WORKLOAD_POLICIES = @("Priority", "PSO")

# -- INIT -------------------------------------------------------------
$total = $VM_POLICIES.Count * $LINK_POLICIES.Count * $WORKLOAD_POLICIES.Count
$done  = 0
$summary = @()

Write-Host "`n====================================================="
Write-Host "  CloudSimSDN - Batch Runner for Scientific Article"
Write-Host "  Total planned combinations: $total"
Write-Host "=====================================================`n"

foreach ($vm in $VM_POLICIES) {
    foreach ($lnk in $LINK_POLICIES) {
        foreach ($wf in $WORKLOAD_POLICIES) {
            $done++
            $expName = "experiment_${vm}_${lnk}_${wf}"
            $logFile = "$WORK_DIR\${expName}.log"
            $pct     = [int](($done / $total) * 100)

            Write-Progress -Activity "CloudSimSDN Batch" `
                           -Status "[$done/$total] Running $expName" `
                           -PercentComplete $pct

            Write-Host "[$done/$total] >> $expName ..." -NoNewline

            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                # Commande optimisée pour ne pas bloquer la machine (Priorité Basse)
                $args = "-cp `"$CP_JAR`" $MAIN $vm $lnk $wf $expName"
                $process = Start-Process -FilePath $JAVA -ArgumentList $args `
                                         -NoNewWindow -Wait -PassThru `
                                         -Priority BelowNormal -RedirectStandardOutput $logFile
                
                $status = if ($process.ExitCode -eq 0) { "OK" } else { "ERROR($($process.ExitCode))" }
            } catch {
                $status = "EXCEPTION"
            }
            $sw.Stop()
            $dur = "$([int]$sw.Elapsed.TotalSeconds)s"

            Write-Host " $status ($dur)"
            
            $summary += [PSCustomObject]@{
                Experiment = $expName
                VM         = $vm
                Link       = $lnk
                Workload   = $wf
                Status     = $status
                Duration   = $dur
            }
        }
    }
}

# -- FINAL REPORT -----------------------------------------------------
Write-Host "`nBatch complete! Running consolidated report..."
& python "$WORK_DIR\Python-V2\consolidated_report.py"

$summaryPath = "$WORK_DIR\batch_run_summary.csv"
$summary | Export-Csv -Path $summaryPath -NoTypeInformation -Delimiter ";"
Write-Host "Summary saved to: $summaryPath"
Write-Host "Figures available in: $WORK_DIR\figures_consolidated\"
