param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\.env.docker'),
    [string]$ExpectedVersion = '8.2.9',
    [switch]$RequireOnlyRedis
)

$ErrorActionPreference = 'Stop'
$resolvedEnvironmentFile = [System.IO.Path]::GetFullPath($EnvironmentFile)
$projectRoot = Split-Path -Parent $resolvedEnvironmentFile
$composeFile = Join-Path $projectRoot 'compose.yaml'

if (-not (Test-Path -LiteralPath $resolvedEnvironmentFile)) {
    throw "Docker environment file is missing: $resolvedEnvironmentFile"
}
if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file is missing: $composeFile"
}
if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is required for the Redis runtime gate.'
}

& docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Engine is not available for the Redis runtime gate.'
}

$containerId = [string](& docker compose --project-directory $projectRoot --file $composeFile --env-file $resolvedEnvironmentFile ps -q redis 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
    throw 'The Compose Redis service is not running.'
}
$containerId = $containerId.Trim()

$containerState = [string](& docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}|{{.Config.Image}}' $containerId 2>$null)
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect the Compose Redis container.'
}
$stateParts = $containerState.Trim().Split('|')
if ($stateParts.Count -ne 3 -or $stateParts[0] -ne 'running' -or $stateParts[1] -ne 'healthy') {
    throw "Docker Redis is not running and healthy: $($containerState.Trim())"
}
$expectedImage = "silverpilot-redis:$ExpectedVersion"
if ($stateParts[2] -ne $expectedImage) {
    throw "Docker Redis image downgrade or drift detected. Expected $expectedImage, found $($stateParts[2])."
}

$publishedBinding = @(& docker port $containerId '6379/tcp' 2>$null)
if ($LASTEXITCODE -ne 0 -or $publishedBinding.Count -ne 1 -or $publishedBinding[0].Trim() -notmatch '^127\.0\.0\.1:\d+$') {
    throw "Docker Redis must publish exactly one loopback-only host binding; found: $($publishedBinding -join ', ')"
}

$ping = [string](& docker exec $containerId sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw PING' 2>$null)
if ($LASTEXITCODE -ne 0 -or $ping.Trim() -ne 'PONG') {
    throw 'Authenticated Docker Redis PING failed.'
}

$serverInfo = @(& docker exec $containerId sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw INFO server' 2>$null)
if ($LASTEXITCODE -ne 0) { throw 'Unable to read Docker Redis server information.' }
$versionLine = $serverInfo | Where-Object { $_ -match '^redis_version:' } | Select-Object -First 1
$actualVersion = if ($null -eq $versionLine) { '' } else { ([string]$versionLine).Split(':', 2)[1].Trim() }
if ($actualVersion -ne $ExpectedVersion) {
    throw "Redis server downgrade or drift detected. Expected $ExpectedVersion, found $actualVersion."
}

$persistenceInfo = @(& docker exec $containerId sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw INFO persistence' 2>$null)
if ($LASTEXITCODE -ne 0) { throw 'Unable to read Docker Redis persistence information.' }
$aofLine = $persistenceInfo | Where-Object { $_ -match '^aof_enabled:' } | Select-Object -First 1
if ($null -eq $aofLine -or ([string]$aofLine).Trim() -ne 'aof_enabled:1') {
    throw 'Docker Redis AOF persistence is not enabled.'
}

if ($RequireOnlyRedis) {
    $runningServices = @(& docker compose --project-directory $projectRoot --file $composeFile --env-file $resolvedEnvironmentFile --profile full ps --services --status running 2>$null |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate running Compose services.' }
    if ($runningServices.Count -ne 1 -or $runningServices[0].Trim() -ne 'redis') {
        throw "Local mode must run only Redis in Docker; running Compose services: $($runningServices -join ', ')"
    }
}

Write-Host "[PASS] Docker Redis $actualVersion is healthy, authenticated, AOF-backed, and loopback-only ($($publishedBinding[0].Trim()))." -ForegroundColor Green
exit 0
