$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$jar = "target/cloudsimsdn-1.0-with-dependencies.jar"
$main = "org.cloudbus.cloudsim.sdn.example.SSLAB.SimpleExampleSelectLinkBandwidth"

Write-Host "--- Testing FCFS ---"
& java -cp $jar $main LFF BwAllocN FCFS small

Write-Host "`n--- Testing SJF ---"
& java -cp $jar $main LFF BwAllocN SJF small
