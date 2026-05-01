# =============================================================
# update_topology_starttimes.ps1
# Décale les starttimes des VMs pour forcer la divergence LFF/LWFF
# =============================================================

function Update-Json {
    param($Path)
    Write-Host "Updating $Path ..."
    if (Test-Path $Path) {
        $json = Get-Content $Path | ConvertFrom-Json
        $vmCount = 0
        for ($i=0; $i -lt $json.nodes.Count; $i++) {
            if ($json.nodes[$i].type -eq "vm") {
                # Incrément de 0.1s par VM
                $json.nodes[$i].starttime = [Math]::Round($vmCount * 0.1, 1)
                $vmCount++
            }
        }
        $json | ConvertTo-Json -Depth 10 | Out-File -FilePath $Path -Encoding utf8
        Write-Host "✅ Done: $vmCount VMs updated."
    } else {
        Write-Warning "File not found: $Path"
    }
}

Update-Json "dataset-small/virtual.json"
Update-Json "dataset-medium/virtual.json"
Update-Json "dataset-large/virtual.json"
