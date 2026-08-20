param(
    [ValidateRange(5, 120)]
    [int]$WaitTimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$composeFile = Join-Path $projectRoot 'compose.yaml'
$environmentFile = Join-Path $projectRoot '.env.docker'

if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host '[PASS] Docker CLI is unavailable, so no Docker service was stopped. IDEA and VSCode processes were not touched.' -ForegroundColor Green
    exit 0
}

& docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host '[PASS] Docker Engine is not running. IDEA and VSCode processes were not touched.' -ForegroundColor Green
    exit 0
}

if (-not (Test-Path -LiteralPath $environmentFile)) {
    Write-Host '[PASS] .env.docker is absent; there is no configured SilverPilot Docker stack to stop.' -ForegroundColor Green
    exit 0
}

Write-Host '[INFO] Stopping only SilverPilot Docker services; named volumes are preserved.' -ForegroundColor Cyan
& docker compose --project-directory $projectRoot --file $composeFile --env-file $environmentFile --profile full down --remove-orphans
if ($LASTEXITCODE -ne 0) { throw "Docker Compose cleanup returned exit code $LASTEXITCODE." }

$deadline = [DateTime]::UtcNow.AddSeconds($WaitTimeoutSeconds)
do {
    $remainingContainers = @(& docker compose --project-directory $projectRoot --file $composeFile --env-file $environmentFile --profile full ps --quiet)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to verify Docker Compose cleanup.' }
    if ($remainingContainers.Count -eq 0) { break }
    Start-Sleep -Milliseconds 500
} while ([DateTime]::UtcNow -lt $deadline)

if ($remainingContainers.Count -gt 0) {
    throw "SilverPilot Docker containers are still present after cleanup: $($remainingContainers -join ', ')"
}

Write-Host '[PASS] SilverPilot Docker services stopped; named volumes were preserved.' -ForegroundColor Green
Write-Host '[BOUNDARY] IDEA backend and VSCode frontend processes are IDE-owned and were not inspected, stopped, or restarted.' -ForegroundColor Cyan
exit 0
