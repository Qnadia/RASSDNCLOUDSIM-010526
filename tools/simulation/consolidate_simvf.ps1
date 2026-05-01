
<#
.SYNOPSIS
    Centralise les résultats de simulation dans SIM-Test-VF et lance l'analyse.

.DESCRIPTION
    Fusionne les résultats de :
      - results/2026-05-14/Sim VF/       (Final : small+medium+large, First/BwAllocN/Dijkstra)
      - results/2026-04-14-Sim V0/Sim VF/ (V0 : small+medium avec DynLatBw en plus)
    vers :
      - results/SIM-Test-VF/             (dossier consolidé)

    Structure cible :
        SIM-Test-VF/
          small/
            VM_MFF/Link_First/
            VM_MFF/Link_BwAllocN/
            VM_MFF/Link_Dijkstra/
            VM_MFF/Link_DynLatBw/    <- depuis Sim V0
            VM_LFF/...
            VM_LWFF/...
          medium/ (idem)
          large/  (depuis Sim VF Final seulement - pas de DynLatBw disponible)

    Ensuite lance :
        python -X utf8 tools\consolidated_report.py --simvf "results\SIM-Test-VF"

.PARAMETER SkipCopy
    Ne recopie pas les fichiers (utile si déjà fait).

.PARAMETER SkipAnalysis
    Ne lance pas l'analyse Python.

.EXAMPLE
    .\consolidate_simvf.ps1
    .\consolidate_simvf.ps1 -SkipCopy
#>

param(
    [switch]$SkipCopy,
    [switch]$SkipAnalysis
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ROOT       = "E:\Workspace\v2\cloudsimsdn-research"
$DEST       = Join-Path $ROOT "results\SIM-Test-VF"

# ── Sources ────────────────────────────────────────────────────────────────────
$SOURCES = @(
    @{
        Path     = Join-Path $ROOT "results\2026-05-14\Sim VF"
        Label    = "Final (First/BwAllocN/Dijkstra)"
        Priority = 1   # priorité basse : sera écrasé par Sim V0 si conflit
    },
    @{
        Path     = Join-Path $ROOT "results\2026-04-14-Sim V0\Sim VF"
        Label    = "Sim V0 (+ DynLatBw)"
        Priority = 2   # priorité haute
    }
)

$DATASETS = @("small", "medium", "large")

# ── Étape 1 — Copie des CSV ────────────────────────────────────────────────────
if (-not $SkipCopy) {
    Write-Host "`n[MERGE] Centralisation vers : $DEST" -ForegroundColor Cyan

    # Trier par priorité croissante (la plus haute écrasera)
    $orderedSources = $SOURCES | Sort-Object { $_.Priority }

    foreach ($src in $orderedSources) {
        if (-not (Test-Path $src.Path)) {
            Write-Host "  [SKIP] Source introuvable : $($src.Path)" -ForegroundColor Yellow
            continue
        }
        Write-Host "`n  [SRC] $($src.Label)" -ForegroundColor DarkCyan

        foreach ($ds in $DATASETS) {
            $dsPath = Join-Path $src.Path $ds
            if (-not (Test-Path $dsPath)) { continue }

            $vmDirs = Get-ChildItem $dsPath -Directory | Where-Object { $_.Name -match "^VM_" }
            foreach ($vmDir in $vmDirs) {
                $linkDirs = Get-ChildItem $vmDir.FullName -Directory | Where-Object { $_.Name -match "^Link_" }
                foreach ($linkDir in $linkDirs) {

                    $destDir = Join-Path $DEST "$ds\$($vmDir.Name)\$($linkDir.Name)"
                    New-Item -ItemType Directory -Force -Path $destDir | Out-Null

                    $csvFiles = Get-ChildItem $linkDir.FullName -File -Filter "*.csv"
                    foreach ($csv in $csvFiles) {
                        Copy-Item $csv.FullName -Destination $destDir -Force
                    }

                    Write-Host "    $ds/$($vmDir.Name)/$($linkDir.Name) [$(($csvFiles).Count) CSVs]"
                }
            }
        }
    }
    Write-Host "`n[OK] Copie terminee." -ForegroundColor Green
} else {
    Write-Host "[SKIP] Copie ignoree (-SkipCopy)." -ForegroundColor Yellow
}

# ── Étape 2 — Inventaire final ─────────────────────────────────────────────────
Write-Host "`n[INVENTAIRE] $DEST" -ForegroundColor Cyan
$allLinks = Get-ChildItem $DEST -Recurse -Directory | Where-Object { $_.Name -match "^Link_" }
$summary  = $allLinks | Group-Object { $_.FullName.Replace($DEST,"").TrimStart("\") -replace "\\[^\\]+$","" } |
            ForEach-Object { "$($_.Name) : $($_.Count) link(s)" } | Sort-Object

foreach ($line in $summary) { Write-Host "  $line" }

$total = ($allLinks | ForEach-Object { (Get-ChildItem $_.FullName -File -Filter "*.csv").Count } | Measure-Object -Sum).Sum
Write-Host "`n  Total : $($allLinks.Count) dossiers, $total CSV files" -ForegroundColor Green

# ── Étape 3 — Analyse Python ───────────────────────────────────────────────────
if (-not $SkipAnalysis) {
    Write-Host "`n[ANALYSIS] Lancement consolidated_report.py --simvf ..." -ForegroundColor Cyan

    $scriptPath = Join-Path $ROOT "tools\consolidated_report.py"
    $cmd = "python -X utf8 `"$scriptPath`" --simvf `"$DEST`""
    Write-Host "  Commande : $cmd"

    Push-Location $ROOT
    try {
        Invoke-Expression $cmd
    } finally {
        Pop-Location
    }

    Write-Host "`n[OK] Analyse terminee." -ForegroundColor Green
    Write-Host "    Figures : $DEST\figures_consolidated\" -ForegroundColor Green
} else {
    Write-Host "[SKIP] Analyse ignoree (-SkipAnalysis)." -ForegroundColor Yellow
}

Write-Host "`n[DONE] SIM-Test-VF pret." -ForegroundColor Cyan
