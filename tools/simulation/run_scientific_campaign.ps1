# =============================================================
# run_scientific_campaign.ps1 - Specialized Run for 30 Permutations
# =============================================================

. "$PSScriptRoot\config_home.ps1"

# -- CONFIG -----------------------------------------------------------
$MAIN     = 'org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth'
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
$fullCp = "$targetClasses;$cloudsimJar;$depCp"

# -- DATASETS ---------------------------------------------------------
$DATASETS = @("small", "medium", "large")

# -- 10 SPECIFIC CONFIGURATIONS ---------------------------------------
$CONFIGS = @(
    @{vm="LFF"; link="First"; wf="Priority"},
    @{vm="LFF";  link="First"; wf="SJF"},
    @{vm="LFF";  link="First"; wf="FCFS"},
    @{vm="LFF";  link="First"; wf="RR"},
    @{vm="LFF";  link="First"; wf="PSO"},

    @{vm="LFF"; link="BwAllocN"; wf="Priority"},
    @{vm="LFF";  link="BwAllocN"; wf="SJF"},
    @{vm="LFF";  link="BwAllocN"; wf="FCFS"},
    @{vm="LFF";  link="BwAllocN"; wf="RR"},
    @{vm="LFF";  link="BwAllocN"; wf="PSO"}
)

# -- INIT -------------------------------------------------------------
$total = $DATASETS.Count * $CONFIGS.Count
$done  = 0
$summary = @()

Write-Host "====================================================="
Write-Host "  CloudSimSDN - SCIENTIFIC CAMPAIGN START"
Write-Host "  Total planned simulations: $total"
Write-Host "=====================================================`n"

foreach ($ds in $DATASETS) {
    Write-Host "--- DATASET: $ds ---" -ForegroundColor Cyan
    foreach ($cfg in $CONFIGS) {
        $done++
        $vm  = $cfg.vm
        $lnk = $cfg.link
        $wf  = $cfg.wf
        $expName = "sci_${ds}_${vm}_${lnk}_${wf}"
        $logFile = "$WORK_DIR\${expName}.log"
        $errLog  = "$WORK_DIR\${expName}.err.log"
        $pct = [int](($done / $total) * 100)

        Write-Progress -Activity "Scientific Campaign" `
                       -Status "[$done/$total] $($ds): $vm-$lnk-$wf" `
                       -PercentComplete $pct

        Write-Host "[$done/$total] >> $vm $lnk $wf ($ds) ..." -NoNewline

        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $process = Start-Process -FilePath $global:JAVA_EXE -ArgumentList "-cp", "`"$fullCp`"", $MAIN, $vm, $lnk, $wf, $ds -RedirectStandardOutput $logFile -RedirectStandardError $errLog -PassThru -NoNewWindow
            
            $waitResult = $process | Wait-Process -Timeout 120 -ErrorAction SilentlyContinue
            
            if (!$process.HasExited) {
                $process | Stop-Process -Force
                $status = "TIMEOUT(120s)"
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
            Dataset    = $ds
            Allocation = $vm
            Routing    = $lnk
            Scheduling = $wf
            Status     = $status
            Duration   = $dur
        }
    }
}

# -- FINAL REPORT -----------------------------------------------------
Write-Host "`nCampaign complete! Running consolidated report..."
# & python "$WORK_DIR\Python-V2\consolidated_report.py"

$summaryPath = "$WORK_DIR\scientific_campaign_summary.csv"
$summary | Export-Csv -Path $summaryPath -NoTypeInformation -Delimiter ";"
Write-Host "Summary saved to: $summaryPath"
Write-Host "Results categorized by dataset in: results/"
