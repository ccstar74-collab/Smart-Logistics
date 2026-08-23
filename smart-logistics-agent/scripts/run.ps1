param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot "build.ps1")
}

$JavaRuntime = Get-Command java -ErrorAction SilentlyContinue
if ($null -eq $JavaRuntime) {
    throw "java was not found. Install JDK 8 or newer."
}

Push-Location $ProjectRoot
try {
    & $JavaRuntime.Source -cp "out" com.smartlogistics.agent.Application
} finally {
    Pop-Location
}
