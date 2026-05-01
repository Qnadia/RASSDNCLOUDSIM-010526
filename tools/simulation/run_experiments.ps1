# =============================================================
# run_experiments.ps1 - Runner CloudSimSDN (selection optimisee)
# Usage :  powershell -ExecutionPolicy Bypass -File .\run_experiments.ps1 -DryRun
#          powershell -ExecutionPolicy Bypass -File .\run_experiments.ps1
# =============================================================

param([switch]$DryRun)

# -- CONFIG -----------------------------------------------------------
$JAVA     = 'C:\Program Files\Java\jre-1.8\bin\java.exe'
$CP_JAR   = (Resolve-Path "$PSScriptRoot\target\cloudsim-4.0.jar").Path
$MAIN     = 'org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth'
$WORK_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path

# -- EXPERIENCES SELECTIONNEES (6 combinaisons cibles) ----------------
# Chaque entree = hashtable {vm; lnk; wf}
# Role de chaque experience :
#   1. LFF + DynLatBw + Priority   => Scenario propose (contribution)
#   2. MFF + DynLatBw + Priority   => Comparaison energie (consolidation)
#   3. FCFS + First + NoOp         => Baseline reference (rien optimise)
#   4. LFF + First + Priority      => Impact link policy
#   5. LFF + DynLatBw + SJF        => Impact workload scheduling vs Priority
#   6. LFF + DynLatBw + RoundRobin => Impact workload scheduling vs Priority

$experiments = @(
    [PSCustomObject]@{vm='LFF';  lnk='DynLatBw'; wf='Priority';   role='Proposed (BW+Lat Aware)'},
    [PSCustomObject]@{vm='LFF';  lnk='BwAlloc';  wf='Priority';   role='Comparison (BW Only)'},
    [PSCustomObject]@{vm='LFF';  lnk='First';    wf='Priority';   role='Comparison (Static Link)'},
    [PSCustomObject]@{vm='MFF';  lnk='DynLatBw'; wf='Priority';   role='Energy (Consolidation)'},
    [PSCustomObject]@{vm='FCFS'; lnk='First';    wf='NoOp';       role='Baseline (Reference)'},
    [PSCustomObject]@{vm='LFF';  lnk='DynLatBw'; wf='SJF';        role='Impact Workload SJF'}
)

# -- INIT ---------------------------------------------------------------
$results = @()
$total   = $experiments.Count
$done    = 0

Write-Host ""
Write-Host "====================================================="
Write-Host "  CloudSimSDN - Batch Runner (6 experiences cibles)"
Write-Host "====================================================="
Write-Host "  Total : $total   |   DryRun : $DryRun"
Write-Host ""

if (-not $CP_JAR) {
    Write-Warning "Aucun cp_*.jar trouve dans Temp. Lancez d'abord la simulation depuis VSCode."
    $CP_JAR = "MISSING_JAR"
}

# -- BOUCLE PRINCIPALE --------------------------------------------------
foreach ($exp in $experiments) {
    $done++
    $vm      = $exp.vm
    $lnk     = $exp.lnk
    $wf      = $exp.wf
    $role    = $exp.role
    $expName = "experiment_${vm}_${lnk}_${wf}"
    $logFile = "$WORK_DIR\${expName}.log"
    $pct     = [int](($done / $total) * 100)

    Write-Progress -Activity "CloudSimSDN Experiments" `
                   -Status "[$done/$total] $expName ($role)" `
                   -PercentComplete $pct

    Write-Host "[$done/$total] >> $expName"
    Write-Host "         Role : $role"

    if ($DryRun) {
        Write-Host "  [DRY-RUN] java -cp <JAR> $MAIN $vm $lnk $wf $expName"
        $results += [PSCustomObject]@{
            Experiment=$expName; VM=$vm; Link=$lnk; WF=$wf
            Role=$role; Status="DryRun"; Duration="0s"
        }
        Write-Host ""
        continue
    }

    if ($CP_JAR -eq "MISSING_JAR") {
        Write-Warning "  JAR introuvable - simulation ignoree."
        continue
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        Push-Location $WORK_DIR
        & $JAVA -cp $CP_JAR $MAIN $vm $lnk $wf $expName *>&1 |
            Tee-Object -FilePath $logFile
        Pop-Location
        $status = if ($LASTEXITCODE -eq 0) { "OK" } else { "ERROR($LASTEXITCODE)" }
    } catch {
        Pop-Location
        $status = "EXCEPTION: $_"
    }
    $sw.Stop()
    $dur = "$([int]$sw.Elapsed.TotalSeconds)s"

    Write-Host "  -> $status  ($dur)"
    Write-Host "  -> log: $logFile"
    Write-Host ""
    $results += [PSCustomObject]@{
        Experiment=$expName; VM=$vm; Link=$lnk; WF=$wf
        Role=$role; Status=$status; Duration=$dur
    }
}

Write-Progress -Activity "CloudSimSDN Experiments" -Completed

# -- RESUME -------------------------------------------------------------
Write-Host ""
Write-Host "====================================================="
Write-Host "  RESUME DES EXPERIENCES"
Write-Host "====================================================="
$results | Format-Table Experiment, VM, Link, WF, Status, Duration -AutoSize

$summaryPath = "$WORK_DIR\experiments_run_summary.csv"
$results | Export-Csv -Path $summaryPath -NoTypeInformation -Delimiter ";"
Write-Host "Resume CSV : $summaryPath"

# -- RAPPORT PYTHON -----------------------------------------------------
if (-not $DryRun) {
    Write-Host ""
    Write-Host "Generation du rapport consolide Python..."
    & python "$WORK_DIR\Python-V2\consolidated_report.py"
    Write-Host "Figures dans : $WORK_DIR\figures_consolidated\"
}

Write-Host ""
Write-Host "Batch termine - $done / $total experiences."
