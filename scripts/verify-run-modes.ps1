param(
    [switch]$FullAudit,
    [switch]$CrossSwitch,
    [ValidateRange(60, 900)]
    [int]$WaitTimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$startScript = Join-Path $PSScriptRoot 'start-project.ps1'
$stopScript = Join-Path $PSScriptRoot 'stop-project.ps1'
$redisVerificationScript = Join-Path $PSScriptRoot 'verify-docker-redis.ps1'
$separationVerificationScript = Join-Path $PSScriptRoot 'verify-ide-separation.ps1'
$environmentFile = Join-Path $projectRoot '.env.docker'
$composeFile = Join-Path $projectRoot 'compose.yaml'
$backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
$frontendRoot = Join-Path $projectRoot 'SourceCode\cecsmsui-vue'
$backendIdentity = ([regex]::Replace($backendRoot, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
$frontendIdentity = ([regex]::Replace($frontendRoot, '[^a-zA-Z0-9]', '')).ToLowerInvariant()

function Get-ProjectLocalProcesses {
    return @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -in @('java.exe', 'node.exe') } |
        Where-Object {
            $normalized = ([regex]::Replace([string]$_.CommandLine, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
            ($normalized.Contains($backendIdentity) -and (
                $normalized.Contains('cecsmsserve100snapshotjar') -or
                $normalized.Contains('comcecsmsservececsmsserveapplication')
            )) -or ($normalized.Contains($frontendIdentity) -and $normalized.Contains('vite'))
        })
}

function Get-ProjectComposeRows {
    if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) { return @() }
    if (-not (Test-Path -LiteralPath $composeFile) -or -not (Test-Path -LiteralPath $environmentFile)) { return @() }
    & docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) { return @() }

    $jsonLines = @(& docker compose --project-directory $projectRoot --file $composeFile `
        --env-file $environmentFile --profile full ps -a --format json 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate this Compose project.' }
    $rows = @()
    foreach ($jsonLine in $jsonLines) {
        if ([string]::IsNullOrWhiteSpace($jsonLine)) { continue }
        $rows += @($jsonLine | ConvertFrom-Json)
    }
    return @($rows)
}

function Get-ProjectContainers {
    return @(Get-ProjectComposeRows | ForEach-Object { [string]$_.Name })
}

function Get-Listener([int]$Port) {
    return Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
}

function Read-EnvironmentValues {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $parts = $line.Split('=', 2)
        $values[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $values
}

function Get-ConfiguredPort([hashtable]$Values, [string]$Name, [int]$DefaultValue) {
    $rawValue = [string]$Values[$Name]
    if ([string]::IsNullOrWhiteSpace($rawValue)) { return $DefaultValue }
    $parsed = 0
    if (-not [int]::TryParse($rawValue, [ref]$parsed) -or $parsed -lt 1 -or $parsed -gt 65535) {
        throw "$Name must be an integer from 1 to 65535."
    }
    return $parsed
}

function Get-DockerHostPorts {
    $values = Read-EnvironmentValues
    return [ordered]@{
        MySql = Get-ConfiguredPort $values 'SILVERPILOT_MYSQL_PORT' 3307
        Redis = Get-ConfiguredPort $values 'SILVERPILOT_REDIS_PORT' 6380
        Backend = Get-ConfiguredPort $values 'SILVERPILOT_BACKEND_PORT' 8083
        Frontend = Get-ConfiguredPort $values 'SILVERPILOT_FRONTEND_PORT' 8082
    }
}

function Assert-NoIdeProcesses([string]$Checkpoint) {
    $processes = @(Get-ProjectLocalProcesses)
    if ($processes.Count -gt 0) {
        $processText = @($processes | ForEach-Object { "$($_.Name)/PID$($_.ProcessId)" }) -join ', '
        throw "$Checkpoint requires IDEA and VSCode application processes to be stopped first: $processText"
    }
}

function Assert-NoIdeRuntime([string]$Checkpoint) {
    Assert-NoIdeProcesses $Checkpoint
    foreach ($port in @(8081, 8083)) {
        $listener = Get-Listener $port
        if ($null -ne $listener) { throw "$Checkpoint requires port $port to be free; PID $($listener.OwningProcess) owns it." }
    }
}

function Assert-NoDockerResidue([string]$Checkpoint) {
    $containers = @(Get-ProjectContainers)
    if ($containers.Count -gt 0) {
        throw "$Checkpoint left SilverPilot containers: $($containers -join ', ')"
    }
    $dockerPorts = Get-DockerHostPorts
    foreach ($port in @($dockerPorts.Values | Sort-Object -Unique)) {
        $listener = Get-Listener $port
        if ($null -ne $listener) { throw "$Checkpoint left port $port occupied by PID $($listener.OwningProcess)." }
    }
    Write-Host "[PASS] ${Checkpoint}: no SilverPilot Docker container or Docker-mode listener remains." -ForegroundColor Green
}

function Assert-LocalPreparation {
    $localRows = @(Get-ProjectComposeRows)
    $localServices = @($localRows | ForEach-Object { [string]$_.Service })
    if ($localRows.Count -ne 1 -or $localServices[0] -ne 'redis' -or [string]$localRows[0].State -ne 'running') {
        $details = @($localRows | ForEach-Object { "$($_.Service)/$($_.Name)/$($_.State)" }) -join ', '
        throw "Local preparation must run exactly this Compose project's Redis service; found: $details"
    }
    & $redisVerificationScript -EnvironmentFile $environmentFile -RequireOnlyRedis
    if ($LASTEXITCODE -ne 0) { throw "Local Docker Redis gate returned exit code $LASTEXITCODE." }
    Assert-NoIdeRuntime 'Local preparation boundary check'
    if (Test-Path -LiteralPath (Join-Path $backendRoot 'target\local-backend.pid')) {
        throw 'Local preparation created a forbidden script-managed backend PID file.'
    }
    Write-Host '[PASS] Local preparation runs Redis only and creates no Java or Vite process.' -ForegroundColor Green
}

function Start-And-VerifyLocalPreparation {
    $before = @((Get-ProjectLocalProcesses) | Select-Object -ExpandProperty ProcessId | Sort-Object)
    & $startScript -Mode Local -WaitTimeoutSeconds $WaitTimeoutSeconds
    if ($LASTEXITCODE -ne 0) { throw "Local preparation returned exit code $LASTEXITCODE." }
    $after = @((Get-ProjectLocalProcesses) | Select-Object -ExpandProperty ProcessId | Sort-Object)
    if (@(Compare-Object -ReferenceObject $before -DifferenceObject $after).Count -ne 0) {
        throw 'Local preparation changed the IDEA/VSCode application process set.'
    }
    Assert-LocalPreparation
}

function Assert-DockerModeRunning {
    $expectedServices = @('mysql', 'redis', 'backend', 'frontend')
    $rows = @(Get-ProjectComposeRows)
    $unexpected = @($rows | Where-Object { [string]$_.Service -notin $expectedServices })
    if ($unexpected.Count -gt 0) {
        throw "Docker mode has unexpected Compose services: $(@($unexpected | ForEach-Object Service) -join ', ')."
    }
    foreach ($service in $expectedServices) {
        $matches = @($rows | Where-Object { [string]$_.Service -eq $service })
        if ($matches.Count -ne 1) { throw "Docker mode must have exactly one '$service' container; found $($matches.Count)." }
        $row = $matches[0]
        if ([string]$row.State -ne 'running' -or [string]$row.Health -ne 'healthy') {
            throw "Docker service '$service' is not healthy (container: $($row.Name), state: $($row.State), health: $($row.Health))."
        }
    }
    Assert-NoIdeProcesses 'Docker mode boundary check'
    $dockerPorts = Get-DockerHostPorts
    $baseUrl = "http://127.0.0.1:$($dockerPorts.Frontend)"
    $healthResponse = Invoke-RestMethod -Uri "$baseUrl/api/actuator/health" -TimeoutSec 10
    if ($healthResponse.status -ne 'UP') { throw "Docker proxied health is $($healthResponse.status)." }
    $redisHealth = Invoke-RestMethod -Uri "$baseUrl/api/actuator/health/redis" -TimeoutSec 10
    if ($redisHealth.status -ne 'UP') { throw "Docker backend Redis component is $($redisHealth.status)." }
    Write-Host '[PASS] Full Docker mode has four healthy, independently built services and no IDE-owned application process.' -ForegroundColor Green
}

function Start-And-VerifyDocker([bool]$Audit) {
    & $startScript -Mode Docker -WaitTimeoutSeconds $WaitTimeoutSeconds -FullAudit:$Audit
    if ($LASTEXITCODE -ne 0) { throw "Docker startup returned exit code $LASTEXITCODE." }
    Assert-DockerModeRunning
}

function Stop-DockerAndAssertClean([string]$Checkpoint) {
    & $stopScript -WaitTimeoutSeconds ([math]::Min($WaitTimeoutSeconds, 120))
    if ($LASTEXITCODE -ne 0) { throw "Docker cleanup returned exit code $LASTEXITCODE at $Checkpoint." }
    Assert-NoDockerResidue $Checkpoint
}

try {
    & $separationVerificationScript
    if ($LASTEXITCODE -ne 0) { throw "IDE separation gate returned exit code $LASTEXITCODE." }
    Assert-NoIdeRuntime 'Run-mode verification preflight'
    Stop-DockerAndAssertClean 'initial Docker cleanup'

    Start-And-VerifyLocalPreparation
    Start-And-VerifyLocalPreparation
    Write-Host '[PASS] Repeating Local preparation retained Redis-only infrastructure without spawning application processes.' -ForegroundColor Green
    Stop-DockerAndAssertClean 'local preparation shutdown'

    Start-And-VerifyDocker ([bool]$FullAudit)
    Start-And-VerifyDocker $false
    Write-Host '[PASS] Repeating Docker startup retained the four-service container topology.' -ForegroundColor Green
    Stop-DockerAndAssertClean 'Docker mode shutdown'

    if ($CrossSwitch) {
        Start-And-VerifyLocalPreparation
        Start-And-VerifyDocker $false
        Write-Host '[PASS] Local infrastructure to Docker switch did not create host Java/Vite processes.' -ForegroundColor Green
        & $startScript -Mode Local -WaitTimeoutSeconds $WaitTimeoutSeconds
        if ($LASTEXITCODE -ne 0) { throw "Docker-to-local preparation returned exit code $LASTEXITCODE." }
        Assert-LocalPreparation
        Write-Host '[PASS] Docker to local switch removed application containers and retained Redis only.' -ForegroundColor Green
        Stop-DockerAndAssertClean 'cross-switch shutdown'
    }

    Write-Host '[PASS] Strict IDE separation, Redis-only local preparation, Docker lifecycle, and requested cross-mode transitions passed.' -ForegroundColor Green
    exit 0
} finally {
    try {
        & $stopScript -WaitTimeoutSeconds ([math]::Min($WaitTimeoutSeconds, 120))
    } catch {
        Write-Warning "Final Docker cleanup failed: $($_.Exception.Message)"
    }
}
