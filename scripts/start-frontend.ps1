# Run in another IDEA terminal: powershell -ExecutionPolicy Bypass -File .\scripts\start-frontend.ps1
$ErrorActionPreference = 'Stop'
$frontendDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend'
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Node.js/npm is missing. Install Node.js 22.12 or newer.' }

Push-Location $frontendDir
try {
    if (-not (Test-Path -LiteralPath 'node_modules')) {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed. Check the error above and your network connection.' }
    }
    & npm.cmd run dev
    $resultCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $resultCode
