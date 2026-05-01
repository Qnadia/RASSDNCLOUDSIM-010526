# =============================================================
# run_single_simulation.ps1
# Cible : Exécution isolée d'une seule configuration pour le debug (ex: PSO)
# =============================================================

param (
    [string]$VmPolicy = "LWFF",
    [string]$LinkPolicy = "First",
    [string]$WorkloadPolicy = "FCFS",
    [string]$Dataset = "dataset-large",
    [switch]$SaveLogs
)

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

Write-Host "`n====================================================="
Write-Host "  Exécution Isolée de la Simulation CloudSimSDN"
Write-Host "  VM Policy       : $VmPolicy"
Write-Host "  Link Policy     : $LinkPolicy"
Write-Host "  Workload Policy : $WorkloadPolicy"
Write-Host "  Dataset         : $Dataset"
Write-Host "=====================================================`n"

$sw = [System.Diagnostics.Stopwatch]::StartNew()

if ($SaveLogs) {
    $expName = "experiment_${VmPolicy}_${LinkPolicy}_${WorkloadPolicy}_${Dataset}_isolated"
    $logDir = "$WORK_DIR\logs"
    if (!(Test-Path -Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
    
    # IT25: Add timestamp to log filenames to avoid overwriting previous runs
    $timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
    $logFile = "$logDir\${expName}_${timestamp}.log"
    $errLog  = "$logDir\${expName}_${timestamp}.err.log"
    
    Write-Host "Lancement de la simulation (Logs redirigés vers $logFile et $errLog)..."
    
    $process = Start-Process -FilePath $global:JAVA_EXE `
        -ArgumentList "-cp", "`"$fullCp`"", $MAIN, $VmPolicy, $LinkPolicy, $WorkloadPolicy, $Dataset `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError $errLog `
        -PassThru -NoNewWindow
        
    $process | Wait-Process
    
    if ($process.ExitCode -eq 0) {
        Write-Host "Terminé avec succès." -ForegroundColor Green
    } else {
        Write-Host "Erreur lors de l'exécution. Code de sortie : $($process.ExitCode)" -ForegroundColor Red
    }
} else {
    Write-Host "Lancement de la simulation (Logs affichés en direct)...`n"
    # Lancement direct pour voir les logs dans la console
    & $global:JAVA_EXE -cp $fullCp $MAIN $VmPolicy $LinkPolicy $WorkloadPolicy $Dataset
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`nTerminé avec succès." -ForegroundColor Green
    } else {
        Write-Host "`nErreur lors de l'exécution (voir stacktrace plus haut). Code de sortie : $LASTEXITCODE" -ForegroundColor Red
    }
}

$sw.Stop()
Write-Host "Durée d'exécution : $([math]::Round($sw.Elapsed.TotalSeconds, 2)) secondes.`n"
