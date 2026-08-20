param(
    [string]$BackendRoot = (Join-Path $PSScriptRoot '..\SourceCode\cecsmsServe-springboot'),
    [switch]$WaitForHealth,
    [switch]$SkipInfrastructure,
    [switch]$Rebuild,
    [ValidateRange(15, 300)]
    [int]$WaitTimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
$resolvedBackend = [System.IO.Path]::GetFullPath($BackendRoot)
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $resolvedBackend '..\..'))
$dockerEnvironmentFile = Join-Path $projectRoot '.env.docker'
$redisVerificationScript = Join-Path $PSScriptRoot 'verify-docker-redis.ps1'
$hostConfig = Join-Path $resolvedBackend 'config\application-host.properties'
$pidFile = Join-Path $resolvedBackend 'target\local-backend.pid'

function Get-ProcessRecord([int]$ProcessId) {
    return Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
}

function Test-IsLegacyManagedJar([string]$CommandLine) {
    if ([string]::IsNullOrWhiteSpace($CommandLine)) { return $false }
    return $CommandLine -match '(?i)-jar\s+.*cecsms-serve-.*\.jar'
}

if ($Rebuild) {
    throw 'Rebuild is no longer supported by this compatibility command. Build or run the backend from IDEA so IDEA remains the sole Java process owner.'
}

if (-not $SkipInfrastructure) {
    & (Join-Path $PSScriptRoot 'docker-dev.ps1') redis -WaitTimeoutSeconds $WaitTimeoutSeconds
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not (Test-Path -LiteralPath $hostConfig)) {
    throw "IDEA host configuration is missing: $hostConfig. Run scripts\docker-dev.ps1 redis first."
}

& $redisVerificationScript -EnvironmentFile $dockerEnvironmentFile -RequireOnlyRedis
if ($LASTEXITCODE -ne 0) {
    throw "IDEA mode requires verified Docker Redis; its runtime gate returned exit code $LASTEXITCODE."
}

if (Test-Path -LiteralPath $pidFile) {
    $rawPid = (Get-Content -LiteralPath $pidFile -Raw).Trim()
    $managedProcessId = 0
    if ([int]::TryParse($rawPid, [ref]$managedProcessId)) {
        $managedProcess = Get-ProcessRecord $managedProcessId
        if ($null -ne $managedProcess -and (Test-IsLegacyManagedJar ([string]$managedProcess.CommandLine))) {
            throw "A legacy script-managed backend is still running (PID $managedProcessId). Run scripts\stop-local-backend.ps1, then start CecsmsServeApplication in IDEA."
        }
    }
    Remove-Item -LiteralPath $pidFile -Force
}

$deadline = [DateTime]::UtcNow.AddSeconds($(if ($WaitForHealth) { $WaitTimeoutSeconds } else { 1 }))
do {
    $listener = Get-NetTCPConnection -State Listen -LocalPort 8083 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $listener) { break }
    if (-not $WaitForHealth) { break }
    Start-Sleep -Seconds 1
} while ([DateTime]::UtcNow -lt $deadline)

if ($null -eq $listener) {
    Write-Host '[READY] Docker Redis and the external host profile are ready. No Java process was started.' -ForegroundColor Green
    Write-Host '[NEXT] Start com.cecsmsserve.CecsmsServeApplication in IDEA.' -ForegroundColor Cyan
    exit 0
}

$owner = Get-ProcessRecord ([int]$listener.OwningProcess)
$ownerCommand = if ($null -eq $owner) { '' } else { [string]$owner.CommandLine }
if (Test-IsLegacyManagedJar $ownerCommand) {
    throw "Port 8083 is owned by a Java -jar backend (PID $($listener.OwningProcess)), not the required IDEA-owned process."
}
if ($ownerCommand -notmatch 'com\.cecsmsserve\.CecsmsServeApplication') {
    $ownerName = if ($null -eq $owner) { 'unknown process' } else { [string]$owner.Name }
    throw "Port 8083 is occupied by $ownerName (PID $($listener.OwningProcess)), not CecsmsServeApplication."
}

try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8083/actuator/health' -TimeoutSec 3
    $redisHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:8083/actuator/health/redis' -TimeoutSec 3
    if ($health.status -ne 'UP' -or $redisHealth.status -ne 'UP') {
        throw 'health status was not UP'
    }
} catch {
    throw "The IDEA-owned backend listens on 8083 but is not healthy: $($_.Exception.Message)"
}

Write-Host "[PASS] IDEA-owned CecsmsServeApplication is healthy on 8083 (PID $($listener.OwningProcess)); this command did not start it." -ForegroundColor Green
exit 0
