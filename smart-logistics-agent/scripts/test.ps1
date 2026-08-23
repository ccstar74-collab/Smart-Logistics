$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot "build.ps1")

$JavaRuntime = Get-Command java -ErrorAction SilentlyContinue
if ($null -eq $JavaRuntime) {
    throw "java was not found. Install JDK 8 or newer."
}
& $JavaRuntime.Source -cp (Join-Path $ProjectRoot "out") com.smartlogistics.agent.AgentSelfTest
if ($LASTEXITCODE -ne 0) {
    throw "Tests failed with exit code $LASTEXITCODE."
}
