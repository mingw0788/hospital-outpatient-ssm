# Run in the IDEA terminal: powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectDir 'backend'
$localConfig = Join-Path $backendDir 'src/main/resources/application-local.yml'

if (-not (Test-Path -LiteralPath $localConfig)) {
    Copy-Item -LiteralPath ($localConfig + '.example') -Destination $localConfig
    Write-Host "Created $localConfig. Set your MySQL password, then run this script again."
    exit 1
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java is missing. Install JDK 17 or 21 and set JAVA_HOME/PATH.' }
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Maven is missing from PATH. You can also run HospitalApplication directly in IDEA.' }

Push-Location $backendDir
try {
    & mvn -s .mvn/settings.xml -gs .mvn/settings.xml spring-boot:run
    $resultCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $resultCode
