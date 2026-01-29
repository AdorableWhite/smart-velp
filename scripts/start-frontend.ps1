$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$frontendDir = Join-Path $repoRoot "frontend"

Push-Location $frontendDir
try {
    if (!(Test-Path (Join-Path $frontendDir "node_modules"))) {
        npm install
    }
    npm run dev
} finally {
    Pop-Location
}
