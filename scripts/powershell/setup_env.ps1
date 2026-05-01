# Check Environment for CloudSimSDN
echo "--- Checking Prerequisites ---"

# 1. Java check
if (Get-Command java -ErrorAction SilentlyContinue) {
    $javaVer = java -version 2>&1 | Select-String "version"
    Write-Host "[OK] Java found: $javaVer" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Java NOT found. Please install JDK 8+." -ForegroundColor Red
}

# 2. Maven check
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvnVer = mvn -version | Select-String "Apache Maven"
    Write-Host "[OK] Maven found: $mvnVer" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Maven NOT found. Please install Apache Maven." -ForegroundColor Red
}

# 3. Python check
if (Get-Command python -ErrorAction SilentlyContinue) {
    $pyVer = python --version
    Write-Host "[OK] Python found: $pyVer" -ForegroundColor Green
    Write-Host "Installing dependencies from requirements.txt..."
    pip install -r requirements.txt
} else {
    Write-Host "[ERROR] Python NOT found. Please install Python 3.8+." -ForegroundColor Red
}

echo "--- Setup Complete ---"
