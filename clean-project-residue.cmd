@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"

rem Standalone SilverPilot runtime cleaner. The PowerShell payload is embedded
rem below so this file does not depend on any script under scripts\.
set "SILVERPILOT_CLEANUP_SELF=%~f0"

where pwsh.exe >nul 2>&1
if errorlevel 1 goto use_windows_powershell

pwsh.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$text=[IO.File]::ReadAllText($env:SILVERPILOT_CLEANUP_SELF); $marker=':__SILVERPILOT_POWERSHELL__'; $start=$text.LastIndexOf($marker); if($start -lt 0){throw 'Embedded cleanup payload is missing.'}; & ([scriptblock]::Create($text.Substring($start+$marker.Length)))"
goto cleanup_finished

:use_windows_powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$text=[IO.File]::ReadAllText($env:SILVERPILOT_CLEANUP_SELF); $marker=':__SILVERPILOT_POWERSHELL__'; $start=$text.LastIndexOf($marker); if($start -lt 0){throw 'Embedded cleanup payload is missing.'}; & ([scriptblock]::Create($text.Substring($start+$marker.Length)))"

:cleanup_finished
set "SILVERPILOT_CLEANUP_EXIT=%ERRORLEVEL%"
set "SILVERPILOT_CLEANUP_SELF="
if "%SILVERPILOT_CLEANUP_EXIT%"=="0" exit /b 0

echo.
echo Cleanup did not reach a verified clean state. Read the diagnostics above.
if /I "%SILVERPILOT_CLEANUP_NO_PAUSE%"=="1" exit /b %SILVERPILOT_CLEANUP_EXIT%
pause
exit /b %SILVERPILOT_CLEANUP_EXIT%

:__SILVERPILOT_POWERSHELL__
$ErrorActionPreference = 'Stop'
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$cleanupFile = [System.IO.Path]::GetFullPath($env:SILVERPILOT_CLEANUP_SELF)
$projectRoot = [System.IO.Path]::GetFullPath((Split-Path -Parent $cleanupFile)).TrimEnd('\')
$backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
$frontendRoot = Join-Path $projectRoot 'SourceCode\cecsmsui-vue'
$environmentFile = Join-Path $projectRoot '.env.docker'
$dryRun = $env:SILVERPILOT_CLEANUP_DRY_RUN -eq '1'
$failures = [System.Collections.Generic.List[string]]::new()
$knownServices = @('frontend', 'backend', 'redis', 'mysql')

function Write-Info([string]$Message) {
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Write-Pass([string]$Message) {
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Add-Failure([string]$Message) {
    [void]$failures.Add($Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Normalize-Identity([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return '' }
    return ([regex]::Replace($Value, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
}

function Test-SamePath([string]$Left, [string]$Right) {
    if ([string]::IsNullOrWhiteSpace($Left) -or [string]::IsNullOrWhiteSpace($Right)) {
        return $false
    }
    try {
        $leftPath = [System.IO.Path]::GetFullPath($Left.Trim()).TrimEnd('\')
        $rightPath = [System.IO.Path]::GetFullPath($Right.Trim()).TrimEnd('\')
        return $leftPath.Equals($rightPath, [System.StringComparison]::OrdinalIgnoreCase)
    } catch {
        return $false
    }
}

function Assert-ProjectPath([string]$Path) {
    $resolved = [System.IO.Path]::GetFullPath($Path)
    $prefix = $projectRoot + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean a path outside the project: $resolved"
    }
    return $resolved
}

function Read-SelectedEnvironmentValues {
    $values = @{}
    if (-not (Test-Path -LiteralPath $environmentFile)) { return $values }
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') { continue }
        $name = $Matches[1]
        if ($name -notin @(
            'COMPOSE_PROJECT_NAME',
            'SILVERPILOT_FRONTEND_PORT',
            'SILVERPILOT_BACKEND_PORT',
            'SILVERPILOT_MYSQL_PORT',
            'SILVERPILOT_REDIS_PORT'
        )) { continue }
        $value = $Matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$name] = $value
    }
    return $values
}

function Get-ConfiguredPort([hashtable]$Values, [string]$Name, [int]$DefaultValue) {
    if (-not $Values.ContainsKey($Name)) { return $DefaultValue }
    $port = 0
    if ([int]::TryParse([string]$Values[$Name], [ref]$port) -and $port -ge 1 -and $port -le 65535) {
        return $port
    }
    return $DefaultValue
}

function Get-NativeOutput([string[]]$Arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& docker @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        $detail = ($output | ForEach-Object { [string]$_ }) -join [Environment]::NewLine
        throw "docker $($Arguments -join ' ') failed with exit code $exitCode. $detail"
    }
    return @($output | ForEach-Object { [string]$_ })
}

function Test-DockerReady {
    if ($null -eq (Get-Command docker.exe -ErrorAction SilentlyContinue)) { return $false }
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & docker info --format '{{.ServerVersion}}' *> $null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Ensure-DockerReady {
    if ($null -eq (Get-Command docker.exe -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI is unavailable. Docker residue cannot be verified.'
    }
    if (Test-DockerReady) { return }
    if ($dryRun) {
        Write-Info 'Dry run: Docker Engine is stopped; a real cleanup would start Docker Desktop to remove restartable project containers.'
        return
    }

    $desktopCandidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ProgramFiles)) {
        $desktopCandidates += Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    }
    if (-not [string]::IsNullOrWhiteSpace(${env:ProgramFiles(x86)})) {
        $desktopCandidates += Join-Path ${env:ProgramFiles(x86)} 'Docker\Docker\Docker Desktop.exe'
    }
    $desktopExecutable = $desktopCandidates |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
    if ($null -eq $desktopExecutable) {
        throw 'Docker Engine is stopped and Docker Desktop was not found in a standard installation directory.'
    }

    Write-Info 'Docker Engine is stopped. Starting Docker Desktop so restartable project containers can be removed.'
    Start-Process -FilePath $desktopExecutable -WindowStyle Hidden | Out-Null
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Seconds 2
        if (Test-DockerReady) {
            Write-Pass 'Docker Engine is ready for residue cleanup.'
            return
        }
    }
    throw 'Docker Desktop did not become ready within 120 seconds.'
}

$selectedEnvironment = Read-SelectedEnvironmentValues
$composeProjectName = if ($selectedEnvironment.ContainsKey('COMPOSE_PROJECT_NAME') -and
    -not [string]::IsNullOrWhiteSpace([string]$selectedEnvironment['COMPOSE_PROJECT_NAME'])) {
    [string]$selectedEnvironment['COMPOSE_PROJECT_NAME']
} else {
    'silverpilot'
}

$portsToVerify = [System.Collections.Generic.HashSet[int]]::new()
foreach ($port in @(
    8081,
    8083,
    (Get-ConfiguredPort $selectedEnvironment 'SILVERPILOT_FRONTEND_PORT' 8082),
    (Get-ConfiguredPort $selectedEnvironment 'SILVERPILOT_BACKEND_PORT' 8083),
    (Get-ConfiguredPort $selectedEnvironment 'SILVERPILOT_MYSQL_PORT' 3307),
    (Get-ConfiguredPort $selectedEnvironment 'SILVERPILOT_REDIS_PORT' 6380)
)) {
    [void]$portsToVerify.Add([int]$port)
}
foreach ($scope in @('Process', 'User', 'Machine')) {
    $customBackendPort = [Environment]::GetEnvironmentVariable('CECSMS_SERVER_PORT', $scope)
    $parsedPort = 0
    if ([int]::TryParse($customBackendPort, [ref]$parsedPort) -and $parsedPort -ge 1 -and $parsedPort -le 65535) {
        [void]$portsToVerify.Add($parsedPort)
    }
}

$backendIdentity = Normalize-Identity $backendRoot
$frontendIdentity = Normalize-Identity $frontendRoot
$projectIdentity = Normalize-Identity $projectRoot
$launcherScriptIdentities = @(
    (Normalize-Identity (Join-Path $projectRoot 'scripts\start-project.ps1')),
    (Normalize-Identity (Join-Path $projectRoot 'scripts\start-local-backend.ps1')),
    (Normalize-Identity (Join-Path $projectRoot 'scripts\start-local-frontend.ps1')),
    (Normalize-Identity (Join-Path $projectRoot 'scripts\docker-dev.ps1'))
)

function Test-IsBackendProcess($Process) {
    if ($null -eq $Process) { return $false }
    $name = ([string]$Process.Name).ToLowerInvariant()
    if ($name -notin @('java.exe', 'javaw.exe', 'cmd.exe')) { return $false }
    $command = Normalize-Identity ([string]$Process.CommandLine)
    if (-not $command.Contains($backendIdentity)) { return $false }
    return $command.Contains('comcecsmsservececsmsserveapplication') -or
        ($command.Contains('cecsmsserve') -and $command.Contains('snapshotjar')) -or
        $command.Contains('springbootrun')
}

function Test-IsFrontendProcess($Process) {
    if ($null -eq $Process) { return $false }
    $name = ([string]$Process.Name).ToLowerInvariant()
    if ($name -ne 'node.exe') { return $false }
    $command = Normalize-Identity ([string]$Process.CommandLine)
    return $command.Contains($frontendIdentity) -and $command.Contains('vite')
}

function Test-IsProjectLauncher($Process) {
    if ($null -eq $Process) { return $false }
    $name = ([string]$Process.Name).ToLowerInvariant()
    if ($name -notin @('powershell.exe', 'pwsh.exe')) { return $false }
    $rawCommand = [string]$Process.CommandLine
    if ($rawCommand -notmatch '(?i)(?:^|\s)-File(?:\s|:)') { return $false }
    $command = Normalize-Identity $rawCommand
    foreach ($identity in $launcherScriptIdentities) {
        if ($command.Contains($identity)) { return $true }
    }
    return $false
}

function Test-IsFrontendWrapper($Process) {
    if ($null -eq $Process) { return $false }
    $name = ([string]$Process.Name).ToLowerInvariant()
    if ($name -notin @('node.exe', 'cmd.exe')) { return $false }
    $command = Normalize-Identity ([string]$Process.CommandLine)
    return $command.Contains('runvitedevmjs') -or
        ($command.Contains('npm') -and $command.Contains('rundev'))
}

function Test-IsBatchLauncherWrapper($Process) {
    if ($null -eq $Process -or ([string]$Process.Name).ToLowerInvariant() -ne 'cmd.exe') {
        return $false
    }
    $command = Normalize-Identity ([string]$Process.CommandLine)
    if (-not $command.Contains($projectIdentity)) { return $false }
    return $command.Contains('startprojectcmd') -or
        $command.Contains('startlocalcmd') -or
        $command.Contains('startdockercmd')
}

function Get-ProjectProcessTargets {
    $processes = @(Get-CimInstance Win32_Process -ErrorAction Stop)
    $byId = @{}
    $childrenByParent = @{}
    foreach ($process in $processes) {
        $id = [int]$process.ProcessId
        $parentId = [int]$process.ParentProcessId
        $byId[$id] = $process
        if (-not $childrenByParent.ContainsKey($parentId)) {
            $childrenByParent[$parentId] = [System.Collections.Generic.List[int]]::new()
        }
        [void]$childrenByParent[$parentId].Add($id)
    }

    $targetIds = [System.Collections.Generic.HashSet[int]]::new()
    $frontendSeedIds = [System.Collections.Generic.List[int]]::new()
    $launcherSeedIds = [System.Collections.Generic.List[int]]::new()
    foreach ($process in $processes) {
        $id = [int]$process.ProcessId
        if (Test-IsBackendProcess $process) {
            [void]$targetIds.Add($id)
        }
        if (Test-IsFrontendProcess $process) {
            [void]$targetIds.Add($id)
            [void]$frontendSeedIds.Add($id)
        }
        if (Test-IsProjectLauncher $process) {
            [void]$targetIds.Add($id)
            [void]$launcherSeedIds.Add($id)
        }
    }

    $frontendListener = Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $frontendListener -and $byId.ContainsKey([int]$frontendListener.OwningProcess)) {
        $listenerProcess = $byId[[int]$frontendListener.OwningProcess]
        $identifiedByCommand = Test-IsFrontendProcess $listenerProcess
        $identifiedByHeader = $false
        if (-not $identifiedByCommand) {
            try {
                $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8081/login' -TimeoutSec 2
                $identifiedByHeader = $response.Headers['X-SilverPilot-Dev-Server'] -eq 'cecsmsui-v1'
            } catch { }
        }
        if ($identifiedByCommand -or $identifiedByHeader) {
            [void]$targetIds.Add([int]$frontendListener.OwningProcess)
            [void]$frontendSeedIds.Add([int]$frontendListener.OwningProcess)
        }
    }

    $backendPorts = @($portsToVerify | Where-Object { $_ -eq 8083 -or $_ -eq (Get-ConfiguredPort $selectedEnvironment 'SILVERPILOT_BACKEND_PORT' 8083) })
    foreach ($backendPort in $backendPorts) {
        $backendListener = Get-NetTCPConnection -State Listen -LocalPort $backendPort -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($null -eq $backendListener -or -not $byId.ContainsKey([int]$backendListener.OwningProcess)) {
            continue
        }
        $listenerProcess = $byId[[int]$backendListener.OwningProcess]
        $listenerName = ([string]$listenerProcess.Name).ToLowerInvariant()
        $identifiedByCommand = Test-IsBackendProcess $listenerProcess
        $identifiedByHealth = $false
        if (-not $identifiedByCommand -and $listenerName -in @('java.exe', 'javaw.exe')) {
            try {
                $health = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/actuator/health" -TimeoutSec 2
                $identifiedByHealth = [string]$health.status -in @('UP', 'DOWN', 'OUT_OF_SERVICE', 'UNKNOWN')
            } catch { }
        }
        if ($identifiedByCommand -or $identifiedByHealth) {
            [void]$targetIds.Add([int]$backendListener.OwningProcess)
        }
    }

    foreach ($seedId in $frontendSeedIds) {
        $current = $byId[$seedId]
        while ($null -ne $current -and $byId.ContainsKey([int]$current.ParentProcessId)) {
            $parent = $byId[[int]$current.ParentProcessId]
            if (-not (Test-IsFrontendWrapper $parent)) { break }
            [void]$targetIds.Add([int]$parent.ProcessId)
            $current = $parent
        }
    }

    foreach ($seedId in $launcherSeedIds) {
        $current = $byId[$seedId]
        if ($null -ne $current -and $byId.ContainsKey([int]$current.ParentProcessId)) {
            $parent = $byId[[int]$current.ParentProcessId]
            if (Test-IsBatchLauncherWrapper $parent) {
                [void]$targetIds.Add([int]$parent.ProcessId)
            }
        }
    }

    $descendantQueue = [System.Collections.Generic.Queue[int]]::new()
    foreach ($id in @($targetIds)) { $descendantQueue.Enqueue($id) }
    while ($descendantQueue.Count -gt 0) {
        $parentId = $descendantQueue.Dequeue()
        if (-not $childrenByParent.ContainsKey($parentId)) { continue }
        foreach ($childId in $childrenByParent[$parentId]) {
            if (-not $byId.ContainsKey($childId)) { continue }
            $childName = ([string]$byId[$childId].Name).ToLowerInvariant()
            if ($childName -notin @('java.exe', 'javaw.exe', 'node.exe', 'cmd.exe', 'powershell.exe', 'pwsh.exe', 'docker.exe')) {
                continue
            }
            if ($targetIds.Add($childId)) { $descendantQueue.Enqueue($childId) }
        }
    }

    $targets = @()
    foreach ($id in $targetIds) {
        if (-not $byId.ContainsKey($id)) { continue }
        $process = $byId[$id]
        $role = if (Test-IsBackendProcess $process) {
            'backend'
        } elseif ((Test-IsFrontendProcess $process) -or (Test-IsFrontendWrapper $process)) {
            'frontend'
        } elseif ((Test-IsProjectLauncher $process) -or (Test-IsBatchLauncherWrapper $process)) {
            'launcher'
        } else {
            'child'
        }
        $depth = 0
        $cursor = $process
        while ($null -ne $cursor -and $byId.ContainsKey([int]$cursor.ParentProcessId) -and $depth -lt 64) {
            $depth++
            $cursor = $byId[[int]$cursor.ParentProcessId]
        }
        $targets += [pscustomobject]@{
            ProcessId = [int]$process.ProcessId
            Name = [string]$process.Name
            Role = $role
            Depth = $depth
        }
    }
    return @($targets | Sort-Object -Property `
        @{ Expression = 'Depth'; Descending = $true }, `
        @{ Expression = 'ProcessId'; Ascending = $true })
}

function Stop-ProjectProcesses {
    $targets = @(Get-ProjectProcessTargets)
    if ($targets.Count -eq 0) {
        Write-Pass 'No project-owned Java, Vite, or dedicated launcher process is running.'
        return
    }
    foreach ($target in $targets) {
        if ($dryRun) {
            Write-Info "Dry run: would stop $($target.Role) $($target.Name) PID $($target.ProcessId)."
            continue
        }
        $liveProcess = Get-Process -Id $target.ProcessId -ErrorAction SilentlyContinue
        if ($null -eq $liveProcess) { continue }
        Stop-Process -Id $target.ProcessId -Force -ErrorAction Stop
        Write-Info "Stopped $($target.Role) $($target.Name) PID $($target.ProcessId)."
    }
    if (-not $dryRun) {
        $deadline = [DateTime]::UtcNow.AddSeconds(15)
        do {
            $remaining = @(Get-ProjectProcessTargets)
            if ($remaining.Count -eq 0) { break }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($remaining.Count -gt 0) {
            $descriptions = $remaining | ForEach-Object { "$($_.Name) PID $($_.ProcessId)" }
            throw "Project processes remained after termination: $($descriptions -join ', ')"
        }
        Write-Pass 'Project-owned Java, Vite, and dedicated launcher processes are stopped.'
    }
}

function Get-ProjectContainers {
    if (-not (Test-DockerReady)) { return @() }
    $containerIds = @(Get-NativeOutput @('ps', '-aq', '--filter', 'label=com.docker.compose.project')) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    $matches = @()
    foreach ($containerId in $containerIds) {
        $inspectJson = (Get-NativeOutput @('inspect', $containerId)) -join [Environment]::NewLine
        $container = $inspectJson | ConvertFrom-Json
        $labels = $container[0].Config.Labels
        $workingDirectory = [string]$labels.'com.docker.compose.project.working_dir'
        $configFiles = [string]$labels.'com.docker.compose.project.config_files'
        $projectName = [string]$labels.'com.docker.compose.project'
        $serviceName = [string]$labels.'com.docker.compose.service'
        $sameConfiguration = $false
        foreach ($configFile in @($configFiles -split ',')) {
            if (Test-SamePath $configFile (Join-Path $projectRoot 'compose.yaml')) {
                $sameConfiguration = $true
                break
            }
        }
        $matchesProject = (Test-SamePath $workingDirectory $projectRoot) -or
            $sameConfiguration -or
            ($projectName -eq $composeProjectName -and $serviceName -in $knownServices)
        if (-not $matchesProject) { continue }
        $matches += [pscustomobject]@{
            Id = [string]$container[0].Id
            Name = ([string]$container[0].Name).TrimStart('/')
            Project = $projectName
            Service = $serviceName
            State = [string]$container[0].State.Status
        }
    }
    return $matches
}

function Stop-ProjectContainers {
    Ensure-DockerReady
    if (-not (Test-DockerReady)) { return }
    $serviceOrder = @{ frontend = 0; backend = 1; redis = 2; mysql = 3 }
    $containers = @(Get-ProjectContainers | Sort-Object -Property @{ Expression = {
        if ($serviceOrder.ContainsKey($_.Service)) { $serviceOrder[$_.Service] } else { 99 }
    } })
    if ($containers.Count -eq 0) {
        Write-Pass 'No SilverPilot Docker container remains.'
        return
    }
    foreach ($container in $containers) {
        if ($dryRun) {
            Write-Info "Dry run: would stop and remove container $($container.Name) [$($container.Service), $($container.State)]."
            continue
        }
        if ($container.State -eq 'paused') {
            [void](Get-NativeOutput @('unpause', $container.Id))
        }
        if ($container.State -notin @('exited', 'created', 'dead')) {
            [void](Get-NativeOutput @('stop', '--time', '60', $container.Id))
        }
        [void](Get-NativeOutput @('rm', $container.Id))
        Write-Info "Stopped and removed container $($container.Name); its named volume was preserved."
    }
    if ($dryRun) { return }

    $remaining = @(Get-ProjectContainers)
    if ($remaining.Count -gt 0) {
        throw "Project containers remained after cleanup: $($remaining.Name -join ', ')"
    }

    $projectNames = @($composeProjectName) + @($containers | Select-Object -ExpandProperty Project)
    foreach ($projectName in @($projectNames | Where-Object { $_ } | Sort-Object -Unique)) {
        $networkIds = @(Get-NativeOutput @('network', 'ls', '--quiet', '--filter', "label=com.docker.compose.project=$projectName")) |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        foreach ($networkId in $networkIds) {
            [void](Get-NativeOutput @('network', 'rm', $networkId))
            Write-Info "Removed unused Compose network $networkId for project $projectName."
        }
    }
    Write-Pass 'SilverPilot Docker containers and runtime networks are gone; named data volumes and images were preserved.'
}

function Remove-RuntimeArtifacts {
    $paths = @(
        (Join-Path $projectRoot '.startup-build-state.json'),
        (Join-Path $backendRoot 'target\local-backend.pid'),
        (Join-Path $backendRoot 'target\local-backend.stdout.log'),
        (Join-Path $backendRoot 'target\local-backend.stderr.log'),
        (Join-Path $backendRoot 'logs'),
        (Join-Path $frontendRoot 'node_modules\.cecsms-vite-stop-requested'),
        (Join-Path $frontendRoot 'node_modules\.cecsms-vite.stdout.log'),
        (Join-Path $frontendRoot 'node_modules\.cecsms-vite.stderr.log'),
        (Join-Path $frontendRoot 'node_modules\.vite'),
        (Join-Path $frontendRoot 'node_modules\.vite-temp')
    )
    $crashLogs = @(
        Get-ChildItem -LiteralPath $projectRoot -Filter 'hs_err_pid*.log' -File -ErrorAction SilentlyContinue
        Get-ChildItem -LiteralPath $backendRoot -Filter 'hs_err_pid*.log' -File -ErrorAction SilentlyContinue
    ) | Select-Object -ExpandProperty FullName -Unique
    $paths += $crashLogs

    foreach ($candidate in $paths) {
        $path = Assert-ProjectPath $candidate
        if (-not (Test-Path -LiteralPath $path)) { continue }
        if ($dryRun) {
            Write-Info "Dry run: would remove runtime artifact $path"
            continue
        }
        Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction Stop
        Write-Info "Removed runtime artifact $path"
    }
    if (-not $dryRun) {
        Write-Pass 'Stale PID/marker files, launcher logs, crash logs, Vite caches, backend logs, and the Docker build fingerprint were removed.'
    }
}

function Get-PortOwnerDescription([int]$Port, [int]$ProcessId) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    $ownerName = if ($null -eq $owner) { 'unknown process' } else { [string]$owner.Name }
    $description = "$ownerName (PID $ProcessId)"
    if (Test-DockerReady) {
        try {
            $containers = @(Get-NativeOutput @('ps', '--filter', "publish=$Port", '--format', '{{.Names}}')) |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
            if ($containers.Count -gt 0) {
                $description += "; Docker container(s): $($containers -join ', ')"
            }
        } catch { }
    }
    return $description
}

function Assert-CleanState {
    $remainingProcesses = @(Get-ProjectProcessTargets)
    if ($remainingProcesses.Count -gt 0) {
        $descriptions = $remainingProcesses | ForEach-Object { "$($_.Name) PID $($_.ProcessId)" }
        throw "Verified project processes are still running: $($descriptions -join ', ')"
    }
    if (Test-DockerReady) {
        $remainingContainers = @(Get-ProjectContainers)
        if ($remainingContainers.Count -gt 0) {
            throw "Verified project containers are still present: $($remainingContainers.Name -join ', ')"
        }
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $portsToVerify.Contains([int]$_.LocalPort) })
        if ($listeners.Count -eq 0) { break }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)

    if ($listeners.Count -gt 0) {
        $descriptions = foreach ($listener in $listeners) {
            $owner = Get-PortOwnerDescription ([int]$listener.LocalPort) ([int]$listener.OwningProcess)
            "port $($listener.LocalPort): $owner"
        }
        throw "A non-project owner still occupies a required project port. It was not killed blindly: $($descriptions -join ' | ')"
    }
    Write-Pass "Required project ports are free: $((@($portsToVerify) | Sort-Object) -join ', ')."
    Write-Pass 'No verified project process or container remains.'
}

try {
    foreach ($requiredPath in @(
        (Join-Path $projectRoot 'compose.yaml'),
        $backendRoot,
        $frontendRoot
    )) {
        if (-not (Test-Path -LiteralPath $requiredPath)) {
            throw "This cleaner must stay in the SilverPilot project root. Missing: $requiredPath"
        }
    }

    Write-Host ''
    Write-Host 'SilverPilot complete runtime cleanup' -ForegroundColor White
    Write-Host "Project: $projectRoot"
    if ($dryRun) {
        Write-Host '[DRY RUN] Nothing will be stopped or deleted.' -ForegroundColor Yellow
    } else {
        Write-Host '[SCOPE] Stops only verified project processes/containers and clears generated runtime state.' -ForegroundColor Cyan
        Write-Host '[PRESERVED] IDEA, VSCode, MySQL 3306, Redis 6379, source, dependencies, uploads, images, Docker images, and named data volumes.' -ForegroundColor Cyan
    }

    try { Stop-ProjectProcesses } catch { Add-Failure "Local process cleanup: $($_.Exception.Message)" }
    try { Stop-ProjectContainers } catch { Add-Failure "Docker cleanup: $($_.Exception.Message)" }
    try { Remove-RuntimeArtifacts } catch { Add-Failure "Runtime artifact cleanup: $($_.Exception.Message)" }

    if ($dryRun) {
        Write-Pass 'Dry-run discovery completed; no state was changed.'
    } else {
        try { Assert-CleanState } catch { Add-Failure "Final verification: $($_.Exception.Message)" }
    }

    if ($failures.Count -gt 0) {
        throw ($failures -join [Environment]::NewLine)
    }
    if (-not $dryRun) {
        Write-Host ''
        Write-Host '[READY] Clean baseline verified. Start with IDEA + VSCode, start-local.cmd, start-docker.cmd, or start-project.cmd.' -ForegroundColor Green
    }
    exit 0
} catch {
    Write-Host ''
    Write-Host "[FAILED] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host '[SAFETY] An unrecognized port owner is never force-killed. Close the reported application/service, then run this file again.' -ForegroundColor Yellow
    exit 1
}
