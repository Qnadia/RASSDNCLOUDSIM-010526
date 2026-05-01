# =============================================================
# run_overnight_campaign.ps1
# Cible : Campagne massive pour Medium (MFF, LWFF) et Large (LFF, MFF, LWFF)
# =============================================================

. "$PSScriptRoot\config_home.ps1"

$MAIN     = 'org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth'
$WORK_DIR = $PSScriptRoot

# -- Classpath -----------------------------------------------------
$mavenCpTxt = "$PSScriptRoot\cp.txt"
if (!(Test-Path $mavenCpTxt)) {
    Write-Host " [Build] Generation du classpath via Maven..."
    & mvn dependency:build-classpath "-Dmdep.outputFile=$mavenCpTxt" -q
}
$depCp      = (Get-Content $mavenCpTxt -Raw).Trim()
$targetClasses = "$PSScriptRoot\target\classes"
$cloudsimJar   = "$PSScriptRoot\lib\cloudsim-4.0.jar"
$mavenDeps     = (Get-ChildItem "$PSScriptRoot\target\dependency\*.jar" | Select-Object -ExpandProperty FullName) -join ';'
$fullCp        = "$targetClasses;$cloudsimJar;$mavenDeps;$depCp"

# -- Configuration des Scénarios ------------------------------------
# Chaque objet représente une Dataset x VM_Policy
$scenarios = @(
    # MEDIUM: MFF et LWFF (LFF est déjà fait)
    @{ Dataset="dataset-medium"; VM="MFF";  Timeout=1800 },
    @{ Dataset="dataset-medium"; VM="LWFF"; Timeout=1800 },
    
    # LARGE: LFF, MFF et LWFF
    @{ Dataset="dataset-large";  VM="LFF";  Timeout=3600 },
    @{ Dataset="dataset-large";  VM="MFF";  Timeout=3600 },
    @{ Dataset="dataset-large";  VM="LWFF"; Timeout=3600 }
)

$LINK_POLICIES     = @("First", "DynLatBw")
$WORKLOAD_POLICIES = @("Priority", "SJF", "RoundRobin", "PSO")

# -- Initialisation -------------------------------------------------
$total = $scenarios.Count * $LINK_POLICIES.Count * $WORKLOAD_POLICIES.Count
$done  = 0
$results = @()

$logDir = "$WORK_DIR\logs"
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

Write-Host "`n====================================================="
Write-Host "  CAMPAGNE MASSIVE DE NUIT"
Write-Host "  Total planned simulations: $total"
Write-Host "  Datasets: medium, large"
Write-Host "=====================================================`n"

$globalSw = [System.Diagnostics.Stopwatch]::StartNew()

foreach ($s in $scenarios) {
    $ds = $s.Dataset
    $vm = $s.VM
    $to = $s.Timeout
    
    foreach ($lnk in $LINK_POLICIES) {
        foreach ($wf in $WORKLOAD_POLICIES) {
            $done++
            $pct = [int](($done / $total) * 100)
            $timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
            $expName = "exp_${ds}_${vm}_${lnk}_${wf}"
            $logFile = "$logDir\${expName}_${timestamp}.log"
            $errLog  = "$logDir\${expName}_${timestamp}.err.log"

            Write-Progress -Activity "Overnight Campaign" `
                           -Status "[$done/$total] $expName" `
                           -PercentComplete $pct

            # Calcul du temps restant estimé (très approximatif)
            $elapsed = $globalSw.Elapsed.TotalMinutes
            $avgTime = if ($done -gt 1) { $elapsed / ($done - 1) } else { 20 }
            $estRem  = [math]::Round($avgTime * ($total - $done), 1)

            Write-Host "[$done/$total][Est. $estRem m remaining] >> $expName ..." -NoNewline

            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                $process = Start-Process -FilePath $global:JAVA_EXE `
                    -ArgumentList "-cp", "`"$fullCp`"", $MAIN, $vm, $lnk, $wf, $ds `
                    -RedirectStandardOutput $logFile `
                    -RedirectStandardError  $errLog `
                    -PassThru -NoNewWindow

                $exited = $process | Wait-Process -Timeout $to -ErrorAction SilentlyContinue

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
            $dur = [math]::Round($sw.Elapsed.TotalSeconds, 1)

            $color = if ($status -eq "OK") { "Green" } else { "Red" }
            Write-Host " $status (${dur}s)" -ForegroundColor $color

            $results += [PSCustomObject]@{
                Dataset  = $ds
                VM       = $vm
                Link     = $lnk
                Workload = $wf
                Status   = $status
                Duration = $dur
                LogFile  = $logFile
            }
            
            # Sauvegarde intermédiaire pour ne pas perdre les données en cas de crash du script
            $results | Export-Csv -Path "$WORK_DIR\overnight_campaign_checkpoint.csv" -NoTypeInformation -Delimiter ";"
        }
    }
}

$globalSw.Stop()
Write-Host "`n====================================================="
Write-Host "  CAMPAGNE TERMINEE en $([math]::Round($globalSw.Elapsed.TotalHours, 1)) heures"
Write-Host "====================================================="

$results | Export-Csv -Path "$WORK_DIR\overnight_campaign_final.csv" -NoTypeInformation -Delimiter ";"
Write-Host "Rapport final généré : $WORK_DIR\overnight_campaign_final.csv"
