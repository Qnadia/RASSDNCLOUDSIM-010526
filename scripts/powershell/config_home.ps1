# config_home.ps1
# Configuration d'exécution pour le PC Home (utilisant OpenJDK 21)
$global:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
$global:JAVA_EXE  = "$global:JAVA_HOME\bin\java.exe"

Write-Host "[Config] Environnement Home PC charge."
Write-Host "[Config] JAVA_EXE: $global:JAVA_EXE"
