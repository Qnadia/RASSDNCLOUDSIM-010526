# run_mini_recalibrated.ps1
# Specialized execution for dataset-mini recalibration validation

. "$PSScriptRoot\tools\simulation\config_home.ps1"

$VM_POLICIES = @("MFF", "LFF", "LWFF")
$LINK_POLICIES = @("First", "DynLatBw")
$WF_POLICY = "Priority"
$DATASET = "mini"

$ROOT = $PSScriptRoot
$MAIN = 'org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth'

$mavenCpTxt = "$ROOT\cp.txt"
$depCp = Get-Content $mavenCpTxt -Raw
$depCp = $depCp.Trim()
$targetClasses = "$ROOT\target\classes"
$cloudsimJar = "$ROOT\lib\cloudsim-4.0.jar"
$mavenDeps = (Get-ChildItem "$ROOT\target\dependency\*.jar" | Select-Object -ExpandProperty FullName) -join ';'
$fullCp = "$targetClasses;$cloudsimJar;$mavenDeps;$depCp"

$logDir = "$ROOT\logs\2026-05-01\mini"
if (!(Test-Path -Path $logDir)) { New-Item -ItemType Directory -Force -Path $logDir | Out-Null }

Write-Host "Starting Mini Recalibrated Campaign..." -ForegroundColor Cyan

foreach ($vm in $VM_POLICIES) {
    foreach ($link in $LINK_POLICIES) {
        Write-Host "Running: $vm | $link | $WF_POLICY ($DATASET) ..." -NoNewline
        $logFile = "$logDir\experiment_${vm}_${link}_${WF_POLICY}.log"
        $errFile = "$logDir\experiment_${vm}_${link}_${WF_POLICY}.err"
        
        $proc = Start-Process -FilePath $global:JAVA_EXE -ArgumentList "-cp", "`"$fullCp`"", $MAIN, $vm, $link, $WF_POLICY, $DATASET -RedirectStandardOutput $logFile -RedirectStandardError $errFile -PassThru -NoNewWindow
        $proc | Wait-Process
        
        if ($proc.ExitCode -eq 0) {
            Write-Host " OK" -ForegroundColor Green
        } else {
            Write-Host " FAIL ($($proc.ExitCode))" -ForegroundColor Red
        }
    }
}

Write-Host "Campaign Complete. Consolidation starting..." -ForegroundColor Cyan
# After runs, we need to move logs to results and run the report generator
# We simulate the structure expected by consolidated_report.py
$resDir = "$ROOT\results\2026-05-01\dataset-mini"
if (!(Test-Path -Path $resDir)) { New-Item -ItemType Directory -Force -Path $resDir | Out-Null }

# Copy logs to results for processing
Copy-Item "$logDir\*.log" $resDir

# Run consolidation (assuming consolidated_report.py is in tools/analysis/ or Python-V2/)
python -X utf8 "$ROOT\tools\analysis\consolidated_report.py" --results-dir "$resDir"

Write-Host "All done. Check results/2026-05-01/dataset-mini/synthese/SUMMARY_SCIENTIFIC_REPORT.md" -ForegroundColor Green
