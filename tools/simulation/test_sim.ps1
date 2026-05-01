# test_sim.ps1
. .\config_home.ps1
$mavenCpTxt = '.\cp.txt'
$depCp = (Get-Content $mavenCpTxt -Raw).Trim()
$targetClasses = '.\target\classes'
$cloudsimJar = '.\lib\cloudsim-4.0.jar'
$mavenDeps = (Get-ChildItem '.\target\dependency\*.jar' | Select-Object -ExpandProperty FullName) -join ';'
$fullCp = "$targetClasses;$cloudsimJar;$mavenDeps;$depCp"

Write-Host "Running simulation..."
& $global:JAVA_EXE -cp "$fullCp" org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth LFF Dijkstra SJF dataset-small
