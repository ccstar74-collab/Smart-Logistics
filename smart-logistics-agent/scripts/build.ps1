param(
    [string]$OutputDirectory = "out"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$OutputPath = Join-Path $ProjectRoot $OutputDirectory
$SourceRoot = Join-Path $ProjectRoot "src\main\java"
$TestRoot = Join-Path $ProjectRoot "src\test\java"

$JavaCompiler = Get-Command javac -ErrorAction SilentlyContinue
if ($null -eq $JavaCompiler) {
    throw "javac was not found. Install JDK 8 or newer and add its bin directory to PATH."
}

New-Item -ItemType Directory -Force -Path $OutputPath | Out-Null
$Sources = @(
    Get-ChildItem -LiteralPath $SourceRoot,$TestRoot -Recurse -Filter "*.java" |
        Select-Object -ExpandProperty FullName
)
if ($Sources.Count -eq 0) {
    throw "No Java source files were found."
}

$CompilerVersion = (& $JavaCompiler.Source -version 2>&1 | Out-String).Trim()
if ($CompilerVersion -match "^javac 1\.8") {
    $CompilerOptions = @("-encoding", "UTF-8", "-Xlint:-options", "-source", "8", "-target", "8")
} else {
    $CompilerOptions = @("-encoding", "UTF-8", "-Xlint:-options", "--release", "8")
}

& $JavaCompiler.Source @CompilerOptions -d $OutputPath $Sources
if ($LASTEXITCODE -ne 0) {
    throw "Java compilation failed with exit code $LASTEXITCODE."
}
Write-Host "Compilation completed: $OutputPath"
