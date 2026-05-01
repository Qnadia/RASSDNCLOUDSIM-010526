# =============================================================
# organize_sim_vf.ps1
# Organise les rǸsultats finaux (VF) dans la structure Sim VF
# =============================================================

$root = "$PSScriptRoot\results\2026-05-14\Sim VF"
if (-not (Test-Path $root)) { New-Item -ItemType Directory -Path $root -Force }

function Copy-Results {
    param($srcPath, $targetSubPath)
    $dest = "$root\$targetSubPath"
    if (Test-Path $srcPath) {
        if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest -Force }
        Write-Host "Copying $srcPath to $dest"
        Copy-Item -Path "$srcPath\*" -Destination $dest -Recurse -Force
    } else {
        Write-Warning "Source not found: $srcPath"
    }
}

# --- Detection du dossier de resultats le plus recent ---
$latestResults = Get-ChildItem -Path "results" -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$resDir = $latestResults.FullName
Write-Host "Utilisation des resultats depuis: $resDir"

function Copy-Results {
    param($ds, $vm, $lp, $targetSubPath)
    $dest = "$root\$targetSubPath"
    
    # Recherche du dossier d'experience correspondant
    $srcFolders = Get-ChildItem -Path "$resDir\$ds\$vm" -Filter "experiment_${vm}_${lp}_*" -Directory -ErrorAction SilentlyContinue
    
    if ($srcFolders) {
        if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest -Force }
        foreach ($folder in $srcFolders) {
            Write-Host "Copying $($folder.Name) to $dest"
            Copy-Item -Path "$($folder.FullName)\*" -Destination $dest -Recurse -Force
        }
    } else {
        Write-Warning "No source found for $ds/$vm/$lp"
    }
}

# --- DATASET SMALL ---
foreach ($vm in @("LFF", "MFF", "LWFF")) {
    Copy-Results "dataset-small" $vm "First" "small/VM_$vm/Link_First"
    Copy-Results "dataset-small" $vm "Dijkstra" "small/VM_$vm/Link_Dijkstra"
    Copy-Results "dataset-small" $vm "BwAllocN" "small/VM_$vm/Link_BwAllocN"
}

# --- DATASET MEDIUM ---
foreach ($vm in @("LFF", "MFF", "LWFF")) {
    Copy-Results "dataset-medium" $vm "First" "medium/VM_$vm/Link_First"
    Copy-Results "dataset-medium" $vm "Dijkstra" "medium/VM_$vm/Link_Dijkstra"
    Copy-Results "dataset-medium" $vm "BwAllocN" "medium/VM_$vm/Link_BwAllocN"
}

# --- DATASET LARGE ---
foreach ($vm in @("LFF", "MFF", "LWFF")) {
    Copy-Results "dataset-large" $vm "First" "large/VM_$vm/Link_First"
    Copy-Results "dataset-large" $vm "Dijkstra" "large/VM_$vm/Link_Dijkstra"
    Copy-Results "dataset-large" $vm "BwAllocN" "large/VM_$vm/Link_BwAllocN"
}

Write-Host "`n====================================================="
Write-Host "  ORGANISATION TERMINEE"
Write-Host "  Repertoire: $root"
Write-Host "====================================================="
