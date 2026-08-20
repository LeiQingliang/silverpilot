param(
    [string]$FrontendRoot = (Join-Path $PSScriptRoot '..\SourceCode\cecsmsui-vue'),
    [switch]$InstallOnly,
    [switch]$RepairDependencies,
    [ValidateRange(15, 300)]
    [int]$WaitTimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
$resolvedFrontend = [System.IO.Path]::GetFullPath($FrontendRoot)
$packageJson = Join-Path $resolvedFrontend 'package.json'
$packageLock = Join-Path $resolvedFrontend 'package-lock.json'
$viteCommand = Join-Path $resolvedFrontend 'node_modules\.bin\vite.cmd'
$stopMarker = Join-Path $resolvedFrontend 'node_modules\.cecsms-vite-stop-requested'
$frontendProcessIdentity = ([regex]::Replace($resolvedFrontend, '[^a-zA-Z0-9]', '')).ToLowerInvariant()

if (-not (Test-Path -LiteralPath $packageJson)) {
    throw "Frontend package.json was not found: $packageJson"
}
if (-not (Test-Path -LiteralPath $packageLock)) {
    throw "Frontend package-lock.json was not found: $packageLock"
}
if ($null -eq (Get-Command node.exe -ErrorAction SilentlyContinue)) {
    throw 'Node.js is not available in PATH. Install the version declared in package.json first.'
}
if ($null -eq (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
    throw 'npm is not available in PATH. Install the version declared in package.json first.'
}

function Test-IsProjectViteCommand([string]$CommandLine) {
    if ([string]::IsNullOrWhiteSpace($CommandLine)) {
        return $false
    }
    $normalizedCommand = ([regex]::Replace($CommandLine, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
    return $normalizedCommand.Contains($frontendProcessIdentity) -and $normalizedCommand.Contains('vite')
}

function Get-ProjectViteProcesses {
    return @(Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" -ErrorAction SilentlyContinue |
        Where-Object {
            Test-IsProjectViteCommand ([string]$_.CommandLine)
        })
}

function Test-FrontendPage {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8081/login' -TimeoutSec 3
        return $response.StatusCode -eq 200 -and $response.Content -match 'id=["'']app["'']'
    } catch {
        return $false
    }
}

function Test-RequiredBackendHealth {
    $backendTarget = if (-not [string]::IsNullOrWhiteSpace($env:CECSMS_LOCAL_BACKEND_URL)) {
        $env:CECSMS_LOCAL_BACKEND_URL.TrimEnd('/')
    } elseif (-not [string]::IsNullOrWhiteSpace($env:VITE_BACKEND_TARGET)) {
        $env:VITE_BACKEND_TARGET.TrimEnd('/')
    } else {
        'http://127.0.0.1:8083'
    }

    try {
        $applicationHealth = Invoke-RestMethod -Uri "$backendTarget/actuator/health" -TimeoutSec 3
        $redisHealth = Invoke-RestMethod -Uri "$backendTarget/actuator/health/redis" -TimeoutSec 3
        return $applicationHealth.status -eq 'UP' -and $redisHealth.status -eq 'UP'
    } catch {
        return $false
    }
}

function Test-FrontendDependencies {
    if (-not (Test-Path -LiteralPath $viteCommand)) {
        return $false
    }
    Push-Location $resolvedFrontend
    try {
        & npm.cmd ls --depth=0 --silent *> $null
        return $LASTEXITCODE -eq 0
    } finally {
        Pop-Location
    }
}

function Install-FrontendDependencies {
    $viteProcesses = @(Get-ProjectViteProcesses)
    if ($viteProcesses.Count -gt 0) {
        $processIds = ($viteProcesses | Select-Object -ExpandProperty ProcessId) -join ', '
        throw "The project Vite server is still running (PID: $processIds). Run the VSCode task 'Frontend: stop dev server' before a clean npm install. No files were changed."
    }

    Write-Host '[INFO] Installing the exact frontend dependency tree from package-lock.json...'
    Push-Location $resolvedFrontend
    try {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) {
            throw "npm ci failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
    if (-not (Test-FrontendDependencies)) {
        throw 'npm ci completed, but the frontend dependency tree is still invalid.'
    }
    Write-Host '[PASS] Frontend dependencies are complete.' -ForegroundColor Green
}

$nodeVersion = (& node.exe --version).Trim()
$npmVersion = (& npm.cmd --version).Trim()
Write-Host "[INFO] Runtime: Node.js $nodeVersion, npm $npmVersion"

if ($InstallOnly) {
    Install-FrontendDependencies
    exit 0
}

$listener = Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -ne $listener) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)" -ErrorAction SilentlyContinue
    $ownerCommand = if ($null -eq $owner) { '' } else { [string]$owner.CommandLine }
    $isCurrentProject = Test-IsProjectViteCommand $ownerCommand
    if ($isCurrentProject -and (Test-FrontendPage) -and (Test-RequiredBackendHealth)) {
        Write-Host "[PASS] This project's Vite server is already available at http://127.0.0.1:8081/login (PID $($listener.OwningProcess))." -ForegroundColor Green
        exit 0
    }
    if ($isCurrentProject) {
        Write-Warning "This project's Vite process owns port 8081 but the complete local stack is not healthy; stopping PID $($listener.OwningProcess) before recovery."
        Stop-Process -Id $listener.OwningProcess -Force
        Start-Sleep -Milliseconds 500
        if ($null -ne (Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue)) {
            throw 'The unhealthy project Vite process was stopped, but port 8081 did not become free.'
        }
    } else {
        $ownerName = if ($null -eq $owner) { 'unknown process' } else { $owner.Name }
        throw "Port 8081 is occupied by $ownerName (PID $($listener.OwningProcess)); the frontend was not started."
    }
}

if ($RepairDependencies) {
    Write-Warning 'A previous Vite startup attempt failed; rebuilding node_modules from the lockfile before retrying.'
    Install-FrontendDependencies
} elseif (-not (Test-FrontendDependencies)) {
    throw "Frontend dependencies are missing or inconsistent. Startup did not modify node_modules. Stop this project's Vite task, run 'npm ci' in $resolvedFrontend, review the result, then retry."
} else {
    Write-Host '[PASS] Frontend dependency tree is complete.' -ForegroundColor Green
}

Write-Host '[INFO] Starting Vite at http://127.0.0.1:8081/login ...'
if (Test-Path -LiteralPath $stopMarker) {
    Remove-Item -LiteralPath $stopMarker -Force
}

Push-Location $resolvedFrontend
try {
    & npm.cmd run dev
    $viteExit = $LASTEXITCODE
} finally {
    Pop-Location
}
if (Test-Path -LiteralPath $stopMarker) {
    Remove-Item -LiteralPath $stopMarker -Force
    Write-Host '[PASS] Vite stopped after an explicit project stop request.' -ForegroundColor Green
    exit 0
}
if ($viteExit -ne 0) {
    throw "Vite exited with code $viteExit"
}
exit 0
