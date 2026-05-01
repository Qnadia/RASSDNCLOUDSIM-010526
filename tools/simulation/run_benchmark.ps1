# Simulation Nocturne CloudSimSDN Parallel (Version Convention 14-Avril)
# Objectif : Respecter la structure de dossiers YYYY-MM-DD/dataset/VM/experiment/

$ROOT = "E:\Workspace\v2\cloudsimsdn-research"
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Chargement config
. "$ROOT\config_home.ps1"

# Préparation Classpath Complet
$depCp = (Get-Content "$ROOT\cp.txt" -Raw).Trim()
$mavenDeps = (Get-ChildItem "$ROOT\target\dependency\*.jar" | Select-Object -ExpandProperty FullName) -join ";"
$fullCp = "$ROOT\target\classes;$ROOT\lib\cloudsim-4.0.jar;$mavenDeps;$depCp"

$VM_POLICIES = @("MFF", "LFF", "LWFF")
$LINKS = @("First", "DynLatBw")
$DATASETS = @(
    @{ path = "datasetsH/dataset-calibrated"; name = "dataset-calibrated" }
)
$WF_POLICIES = @("SJF", "Priority")

$dateStr = Get-Date -Format "yyyy-MM-dd"
$baseResultDir = "$ROOT\results\$dateStr"

$MAIN = "org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth"

foreach ($wf in $WF_POLICIES) {
    foreach ($ds in $DATASETS) {
        $dsPath = $ds.path
        $dsName = $ds.name
        
        Write-Host "`n>>> Starting Batch: WF=$wf | Dataset=$dsName" -ForegroundColor Cyan
        
        foreach ($vm in $VM_POLICIES) {
            foreach ($link in $LINKS) {
                Write-Host "  Run: $vm / $link / $wf" -ForegroundColor Yellow
                $ts = Get-Date -Format "HH-mm-ss"
                $logFile = "$ROOT\logs\nightly_${vm}_${link}_${wf}_${dsName}_${ts}.log"
                
                # Exécution Java
                cmd /c "`"$global:JAVA_EXE`" -cp $fullCp $MAIN $vm $link $wf $dsPath > $logFile 2>&1"

                # Correction du nommage de dossier si Java crée "datasetsdataset-xxx"
                $wrongDir = "$baseResultDir\datasets$dsName"
                $correctDir = "$baseResultDir\$dsName"
                if (Test-Path $wrongDir) {
                    if (!(Test-Path $correctDir)) { New-Item -ItemType Directory -Path $correctDir }
                    Copy-Item -Path "$wrongDir\*" -Destination $correctDir -Recurse -Force
                    Remove-Item -Path $wrongDir -Recurse -Force
                }
            }
        }
        
        # Post-process : Analyse consolidée par Dataset/WF
        Write-Host "  [ANALYSIS] Generating consolidated results and figures..." -ForegroundColor Cyan
        
        # 1. Consolidate results
        python tools/analysis/2_consolidate_results.py $baseResultDir
        
        # 2. Generate plots
        python tools/analysis/3_generate_global_plots.py $baseResultDir
    }
}

Write-Host "`n[ALL DONE] Nightly Simulation Cycle Completed." -ForegroundColor Green
