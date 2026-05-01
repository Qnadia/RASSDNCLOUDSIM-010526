# =============================================================
# run_lwff_large.ps1
# Lance tous les scénarios LWFF restants sur dataset-large (800 workloads)
# Usage : powershell -ExecutionPolicy Bypass -File .\run_lwff_large.ps1
# =============================================================

. "$PSScriptRoot\config_home.ps1"

$MAIN    = 'org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth'
$WORK_DIR = $PSScriptRoot
$DATASET  = 'dataset-large'

$mavenCpTxt = "$PSScriptRoot\cp.txt"
if (!(Test-Path $mavenCpTxt)) {
    Write-Host "[Build] Génération du classpath dynamique..."
    & mvn dependency:build-classpath "-Dmdep.outputFile=$mavenCpTxt" -q
}
$depCp = (Get-Content $mavenCpTxt -Raw).Trim()
$targetClasses = "$PSScriptRoot\target\classes"
$cloudsimJar   = "$PSScriptRoot\lib\cloudsim-4.0.jar"
$mavenDeps     = (Get-ChildItem "$PSScriptRoot\target\dependency\*.jar" | Select-Object -ExpandProperty FullName) -join ';'
$fullCp        = "$targetClasses;$cloudsimJar;$mavenDeps;$depCp"

$logDir = "$WORK_DIR\logs\lwff_large_800"
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# -- Scénarios LWFF restants (scénario 5 déjà OK) --
$scenarios = @(
    [PSCustomObject]@{ VmPolicy='LWFF'; LinkPolicy='First';    WorkloadPolicy='Priority' },
    [PSCustomObject]@{ VmPolicy='LWFF'; LinkPolicy='BwAllocN'; WorkloadPolicy='SJF'      },
    [PSCustomObject]@{ VmPolicy='LWFF'; LinkPolicy='BwAllocN'; WorkloadPolicy='FCFS'     },
    [PSCustomObject]@{ VmPolicy='LWFF'; LinkPolicy='First';    WorkloadPolicy='SJF'      },
    [PSCustomObject]@{ VmPolicy='LWFF'; LinkPolicy='First';    WorkloadPolicy='FCFS'     },
    [PSCustomObject]@{ VmPolicy='LWFF'; LinkPolicy='BwAllocN'; WorkloadPolicy='PSO'      },
    [PSCustomObject]@{ VmPolicy='LWFF'; LinkPolicy='First';    WorkloadPolicy='PSO'      }
)

$total = $scenarios.Count
$done  = 0
$results = @()

Write-Host ""
Write-Host "============================================================="
Write-Host "  CloudSimSDN — LWFF Large Dataset (800 workloads)"
Write-Host "  Scénarios à lancer : $total"
Write-Host "  Logs dans          : $logDir"
Write-Host "============================================================="
Write-Host ""

foreach ($s in $scenarios) {
    $done++
    $vm  = $s.VmPolicy
    $lnk = $s.LinkPolicy
    $wf  = $s.WorkloadPolicy
    $ts  = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
    $expName  = "LWFF_${lnk}_${wf}_${DATASET}"
    $logFile  = "$logDir\${expName}_${ts}.log"
    $errFile  = "$logDir\${expName}_${ts}.err.log"

    Write-Host "------------------------------------------------------------"
    Write-Host "  [$done/$total] $vm | $lnk | $wf"
    Write-Host "  Log  : $logFile"
    Write-Host "------------------------------------------------------------"

    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    $process = Start-Process -FilePath $global:JAVA_EXE `
        -ArgumentList "-cp", "`"$fullCp`"", $MAIN, $vm, $lnk, $wf, $DATASET `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError  $errFile `
        -PassThru -NoNewWindow

    $process | Wait-Process
    $sw.Stop()

    $status = if ($process.ExitCode -eq 0) { "✅ OK" } else { "❌ ERROR($($process.ExitCode))" }
    $dur    = "$([math]::Round($sw.Elapsed.TotalSeconds, 1))s"

    Write-Host "  → $status  (Durée: $dur)"
    Write-Host ""

    $results += [PSCustomObject]@{
        Scenario       = $expName
        VmPolicy       = $vm
        LinkPolicy     = $lnk
        WorkloadPolicy = $wf
        Status         = $status
        Duration       = $dur
        LogFile        = $logFile
    }
}

# -- Résumé final --
Write-Host "============================================================="
Write-Host "  RÉSUMÉ LWFF LARGE (800 workloads)"
Write-Host "============================================================="
$results | Format-Table Scenario, VmPolicy, LinkPolicy, WorkloadPolicy, Status, Duration -AutoSize

$csvPath = "$logDir\summary_lwff_large_800.csv"
$results | Export-Csv -Path $csvPath -NoTypeInformation -Delimiter ";" -Encoding UTF8
Write-Host "Résumé CSV : $csvPath"
Write-Host ""
