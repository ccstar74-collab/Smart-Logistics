param(
    [string]$BusinessApiBase = 'http://111.170.148.177:58080',
    [string]$BusinessUsername = 'eta_service',
    [string]$BusinessPassword = $env:SMART_LOGISTICS_API_PASSWORD,
    [double]$PollSeconds = 2,
    [double]$FreshGpsSeconds = 10,
    [switch]$DryRun,
    [switch]$Once
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($BusinessPassword)) {
    $securePassword = Read-Host 'Business backend password' -AsSecureString
    $credential = [pscredential]::new($BusinessUsername, $securePassword)
    $BusinessPassword = $credential.GetNetworkCredential().Password
}

$env:SMART_LOGISTICS_API_PASSWORD = $BusinessPassword
$simulator = Join-Path $PSScriptRoot 'task_gps_fallback.py'
$mqttCredentials = Join-Path $env:USERPROFILE '.smart-logistics\mqtt_cloud.env'

$arguments = @(
    '-u'
    $simulator
    '--business-api-base'
    $BusinessApiBase
    '--business-username'
    $BusinessUsername
    '--mqtt-credentials'
    $mqttCredentials
    '--poll-seconds'
    $PollSeconds
    '--fresh-gps-seconds'
    $FreshGpsSeconds
)

if ($DryRun) {
    $arguments += '--dry-run'
}
if ($Once) {
    $arguments += '--once'
}

python @arguments
exit $LASTEXITCODE
