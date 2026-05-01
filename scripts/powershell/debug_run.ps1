# debug_run.ps1
# Usage: .\debug_run.ps1 [vmAlloc] [linkPolicy] [wfPolicy] [dataset]
# Exemple: .\debug_run.ps1 LFF BwAllocN Priority mini

param(
    [string]$vmAlloc = "LFF",
    [string]$linkPol = "BwAllocN",
    [string]$wfPol = "Priority",
    [ValidateSet("small", "medium", "large", "energy", "mini")][string]$dataset = "mini"
)

$ROOT = $PSScriptRoot
$MAIN = 'org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth'

# 1. Charger la configuration specifique (Home PC ou autre)
$configFile = "$ROOT\config_home.ps1"
if (Test-Path $configFile) {
    . $configFile
} else {
    $global:JAVA_EXE = "java"
}

# 2. Re-generer le classpath dynamiquement via Maven
$cpFile = "$ROOT\cp.txt"
if (!(Test-Path $cpFile) -or ((Get-Date) - (Get-Item $cpFile).LastWriteTime).TotalMinutes -gt 60) {
    Write-Host "🔄 Generation du classpath Maven dependencies..."
    & mvn dependency:build-classpath "-Dmdep.outputFile=$cpFile" -q
}

$mavenCp = Get-Content $cpFile
$fullCp = "$ROOT\target\classes;$ROOT\lib\cloudsim-4.0.jar;$mavenCp"

# 3. Préparer le dossier de résultats
$date = Get-Date -Format 'yyyy-MM-dd'
$outDir = "$ROOT\results\$date\dataset-$dataset"
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

Write-Host "==============================================================="
Write-Host " Lancement de la simulation: $vmAlloc / $linkPol / $wfPol ($dataset)"
Write-Host " Classpath : $fullCp"
Write-Host "==============================================================="

# 4. Lancement avec garde-fou (boucle/timeout de 2 minutes)
# 4. Lancement avec garde-fou (boucle/timeout de 2 minutes)
$outLog = "$outDir\stdout.log"
$errLog = "$outDir\stderr.log"

$process = Start-Process -FilePath $global:JAVA_EXE -ArgumentList "-cp", "`"$fullCp`"", $MAIN, $vmAlloc, $linkPol, $wfPol, $dataset -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -NoNewWindow
$waitResult = $process | Wait-Process -Timeout 600 -ErrorAction SilentlyContinue

if (!$process.HasExited) {
    Write-Error "❌ TIMEOUT (2 minutes). Boucle infinie detectee. Arret force du processus..."
    $process | Stop-Process -Force
    exit 1
}

if ($process.ExitCode -eq 0) {
    Write-Host "✅ Simulation terminee."
    exit 0
} else {
    Write-Host "❌ Simulation a echoue (Exit Code: $($process.ExitCode))."
    Write-Host "Consultez $errLog pour plus de details."
    exit $process.ExitCode
}
