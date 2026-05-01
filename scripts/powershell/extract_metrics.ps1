# =============================================================
# extract_metrics.ps1
# Extrait et agrège les métriques depuis Sim VF
# =============================================================

$root = "$PSScriptRoot\results\2026-05-14\Sim VF"
$outFile = "$PSScriptRoot\comparison_data.csv"
"Dataset;VM;Link;AvgLatency_ms;TotalEnergy_Wh;AvgThroughput_Mbps" | Out-File -FilePath $outFile -Encoding utf8

$datasets = @("small", "medium", "large")
$vmPolicies = @("VM_LFF", "VM_MFF", "VM_LWFF")
$linkPolicies = @("Link_First", "Link_BwAllocN", "Link_DynLatBw", "Link_Dijkstra")

foreach ($ds in $datasets) {
    foreach ($vm in $vmPolicies) {
        foreach ($lp in $linkPolicies) {
            $path = "$root\$ds\$vm\$lp"
            if (Test-Path $path) {
                # --- Aggregate Latency ---
                $latencyFiles = Get-ChildItem -Path $path -Filter "path_latency_final.csv" -Recurse
                $totalLat = 0; $countLat = 0
                foreach ($f in $latencyFiles) {
                    $lines = Get-Content $f.FullName | Where-Object { $_ -notmatch "^#" -and $_ -ne "" }
                    foreach ($line in $lines) {
                        $parts = $line.Split(';')
                        if ($parts.Count -ge 10) {
                            $totalLat += [double]$parts[9]
                            $countLat++
                        }
                    }
                }
                $avgLat = if ($countLat -gt 0) { [Math]::Round($totalLat / $countLat, 2) } else { 0 }

                # --- Aggregate Energy ---
                $energyFiles = Get-ChildItem -Path $path -Filter "host_energy_total.csv" -Recurse
                $totalEnergy = 0
                foreach ($f in $energyFiles) {
                    $lines = Get-Content $f.FullName | Where-Object { $_ -notmatch "^#" -and $_ -ne "" }
                    foreach ($line in $lines) {
                        $parts = $line.Split(';')
                        if ($parts.Count -ge 4) {
                            $totalEnergy += [double]$parts[3]
                        }
                    }
                }
                $avgEnergy = if ($energyFiles.Count -gt 0) { [Math]::Round($totalEnergy / $energyFiles.Count, 2) } else { 0 }

                # --- Aggregate Throughput ---
                $tpFiles = Get-ChildItem -Path $path -Filter "sfc_throughput.csv" -Recurse
                $totalTp = 0; $countTp = 0
                foreach ($f in $tpFiles) {
                    $lines = Get-Content $f.FullName | Where-Object { $_ -notmatch "^#" -and $_ -ne "" }
                    foreach ($line in $lines) {
                        $parts = $line.Split(';')
                        if ($parts.Count -ge 3) {
                            $totalTp += [double]$parts[2]
                            $countTp++
                        }
                    }
                }
                $avgTp = if ($countTp -gt 0) { [Math]::Round($totalTp / $countTp, 2) } else { 0 }

                # Save line
                "$ds;$vm;$lp;$avgLat;$avgEnergy;$avgTp" | Out-File -FilePath $outFile -Append -Encoding utf8
                Write-Host "Processed: $ds/$vm/$lp -> Lat: $avgLat, Energy: $avgEnergy"
            }
        }
    }
}

Write-Host "`nExtraction terminee ! Donnees dans $outFile"
