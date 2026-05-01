# Script to organize experiment log files into their results directories
$baseDir = "results/2026-03-14/dataset-medium"
$logFiles = Get-ChildItem "experiment_*.log"

foreach ($log in $logFiles) {
    if ($log.Name -match "experiment_([^_]+)_([^_]+)_([^_]+)_dataset-medium_isolated") {
        $vmPolicy = $Matches[1]
        $linkPolicy = $Matches[2]
        $workloadPolicy = $Matches[3]
        $isErr = $log.Name.Contains(".err.")
        
        $destDir = "$baseDir/$vmPolicy/experiment_$($vmPolicy)_$($linkPolicy)_$($workloadPolicy)"
        if (Test-Path $destDir) {
            $destName = if ($isErr) { "stderr.log" } else { "stdout.log" }
            Write-Host "Moving $($log.Name) to $destDir/$destName"
            Move-Item $log.FullName "$destDir/$destName" -Force
        } else {
            Write-Host "Destination not found: $destDir" -ForegroundColor Yellow
        }
    }
}
