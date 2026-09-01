param(
    [int]$Port = 8080,
    [switch]$WithoutModel
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ApplicationClass = Join-Path $ProjectRoot "out\com\smartlogistics\agent\Application.class"

$JavaRuntime = Get-Command java -ErrorAction SilentlyContinue
if ($null -eq $JavaRuntime) {
    throw "java was not found. Install JDK 21 and add its bin directory to PATH."
}

if (-not (Test-Path -LiteralPath $ApplicationClass)) {
    & (Join-Path $PSScriptRoot "build.ps1")
}

$env:AGENT_PORT = "$Port"

if ($WithoutModel) {
    Remove-Item Env:MODEL_API_KEY -ErrorAction SilentlyContinue
} else {
    if ([string]::IsNullOrWhiteSpace($env:MODEL_API_KEY)) {
        $SecureModelKey = Read-Host "Enter Alibaba Model Studio API Key" -AsSecureString
        $env:MODEL_API_KEY = [System.Net.NetworkCredential]::new("", $SecureModelKey).Password.Trim()
        Remove-Variable SecureModelKey
    }

    if ($env:MODEL_API_KEY.Length -le 10 -or $env:MODEL_API_KEY -match '[\x00-\x1F\x7F]') {
        throw "The API key is missing, too short, or contains a control character. Paste it with right-click or Ctrl+Shift+V."
    }

    if ([string]::IsNullOrWhiteSpace($env:MODEL_API_STYLE)) {
        $env:MODEL_API_STYLE = "chat_completions"
    }
    if ([string]::IsNullOrWhiteSpace($env:MODEL_BASE_URL)) {
        $env:MODEL_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    }
    if ([string]::IsNullOrWhiteSpace($env:MODEL_NAME)) {
        $env:MODEL_NAME = "qwen-plus"
    }
}

Write-Host "Agent URL: http://localhost:$Port/"
Write-Host "Press Ctrl+C to stop."

Push-Location $ProjectRoot
try {
    & $JavaRuntime.Source -cp "out" com.smartlogistics.agent.Application
} finally {
    Pop-Location
}

