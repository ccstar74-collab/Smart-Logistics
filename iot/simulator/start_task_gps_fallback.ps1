param(
    [string]$BusinessApiBase = 'http://111.170.148.177:58080',
    [string]$BusinessUsername = 'eta_service',
    [string]$BusinessPassword = $env:SMART_LOGISTICS_API_PASSWORD,
    [double]$PollSeconds = 2,
    [double]$FreshGpsSeconds = 10,
    [ValidateRange(0, 1)]
    [double]$AnomalyRate = 0,
    [ValidateSet('None', 'Stop', 'Drift', 'Open')]
    [string]$DemoAnomaly = 'None',
    [ValidateSet('Precomputed', 'Raw')]
    [string]$AlertMode = 'Precomputed',
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
    '--anomaly-rate'
    $AnomalyRate
    '--alert-mode'
    $AlertMode.ToLowerInvariant()
)

if ($DemoAnomaly -ne 'None') {
    $arguments += @('--demo-anomaly', $DemoAnomaly.ToLowerInvariant())
}

if ($DryRun) {
    $arguments += '--dry-run'
}
if ($Once) {
    $arguments += '--once'
}

python @arguments
exit $LASTEXITCODE
