param(
    [ValidateRange(5, 120)]
    [int]$WaitTimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$composeFile = Join-Path $projectRoot 'compose.yaml'
. (Join-Path $PSScriptRoot 'project-lifecycle.ps1')

function Invoke-StopDocker([string[]]$Arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& docker @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) { throw "Unable to complete or verify Docker shutdown: $($output -join '; ')" }
    return @($output | ForEach-Object { [string]$_ })
}

function Get-CheckoutContainers {
    $ids = @(Invoke-StopDocker @('ps', '-aq', '--filter', 'label=com.docker.compose.project.working_dir'))
    foreach ($containerId in $ids) {
        if ([string]::IsNullOrWhiteSpace($containerId)) { continue }
        # Inspect ownership metadata only; never expose container environment secrets.
        $metadata = (Invoke-StopDocker @('inspect', '--format', '{{json .Config.Labels}}', $containerId)) -join '' | ConvertFrom-Json
        $workingDirectory = [string]$metadata.'com.docker.compose.project.working_dir'
        $configurationFiles = @(([string]$metadata.'com.docker.compose.project.config_files').Split(','))
        if (-not [string]::Equals($workingDirectory.TrimEnd('\', '/'), $projectRoot.TrimEnd('\', '/'), [StringComparison]::OrdinalIgnoreCase)) { continue }
        if (-not ($configurationFiles | Where-Object { [string]::Equals($_.Trim(), $composeFile, [StringComparison]::OrdinalIgnoreCase) })) { continue }
        [pscustomobject]@{
            Id = $containerId
            Project = [string]$metadata.'com.docker.compose.project'
            Service = [string]$metadata.'com.docker.compose.service'
        }
    }
}

$lifecycleLock = Enter-ProjectLifecycleLock $projectRoot
try {
    if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI is unavailable; shutdown cannot be performed or verified. Restore Docker CLI and retry.'
    }
    Invoke-StopDocker @('info', '--format', '{{.ServerVersion}}') | Out-Null
    $containers = @(Get-CheckoutContainers)
    if ($containers.Count -gt 0) {
        Write-Host "[INFO] Stopping $($containers.Count) Docker containers owned by this checkout; named volumes are preserved." -ForegroundColor Cyan
        $networks = @{}
        foreach ($container in $containers) {
            $attached = (Invoke-StopDocker @('inspect', '--format', '{{json .NetworkSettings.Networks}}', $container.Id)) -join '' | ConvertFrom-Json
            foreach ($network in $attached.PSObject.Properties.Name) { $networks[$network] = $container.Project }
        }
        # Persisted Compose labels allow shutdown after the environment file was
        # lost or renamed. Explicit IDs cannot target another checkout by name.
        $containerIds = @($containers | ForEach-Object { $_.Id })
        $stopOrder = @{ frontend = 0; backend = 1; redis = 3; mysql = 4 }
        foreach ($container in ($containers | Sort-Object { if ($stopOrder.ContainsKey($_.Service)) { $stopOrder[$_.Service] } else { 2 } })) {
            Invoke-StopDocker @('stop', '--timeout', [string][Math]::Max(60, $WaitTimeoutSeconds), $container.Id) | Out-Null
        }
        Invoke-StopDocker (@('rm') + $containerIds) | Out-Null
        foreach ($networkName in $networks.Keys) {
            $network = (Invoke-StopDocker @('network', 'inspect', $networkName)) -join '' | ConvertFrom-Json
            if ($network.Labels.'com.docker.compose.project' -eq $networks[$networkName] -and @($network.Containers.PSObject.Properties).Count -eq 0) {
                Invoke-StopDocker @('network', 'rm', $networkName) | Out-Null
            }
        }
    }
    if (@(Get-CheckoutContainers).Count -ne 0) { throw 'Docker containers for this checkout remain after shutdown.' }
    Write-Host '[PASS] SilverPilot Docker services stopped and verified; named volumes were preserved.' -ForegroundColor Green
    Write-Host '[BOUNDARY] Stop the local backend in IDEA and the local frontend in VSCode; those application processes remain IDE-owned.' -ForegroundColor Cyan
} finally {
    Exit-ProjectLifecycleLock $lifecycleLock
}
exit 0
