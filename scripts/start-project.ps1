param(
    [ValidateSet('Choose', 'Local', 'Docker', 'Stop', 'Exit')]
    [string]$Mode = 'Choose',
    [switch]$OpenBrowser,
    [switch]$WaitForStop,
    [switch]$KeepConsoleOpen,
    [switch]$Rebuild,
    [switch]$FullAudit,
    [ValidateRange(60, 900)]
    [int]$WaitTimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$environmentFile = Join-Path $projectRoot '.env.docker'
$cleanupScript = Join-Path $PSScriptRoot 'stop-project.ps1'
$backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
$managedBackendPidFile = Join-Path $backendRoot 'target\local-backend.pid'

function Select-RunMode {
    Write-Host ''
    Write-Host 'SilverPilot 启动方式' -ForegroundColor Cyan
    Write-Host '  1. 准备本地 IDE 调试：只启动 Docker Redis，后端必须由 IDEA 启动，前端必须由 VSCode 启动'
    Write-Host '  2. 全 Docker：前端 + 后端 + MySQL + Redis（用于演示/集成验收）'
    Write-Host '  3. 停止 Docker 服务（不会终止 IDEA 或 VSCode 进程）'
    Write-Host '  0. 退出'
    while ($true) {
        $choice = (Read-Host '请选择 [1/2/3/0]').Trim()
        switch ($choice) {
            '1' { return 'Local' }
            '2' { return 'Docker' }
            '3' { return 'Stop' }
            '0' { return 'Exit' }
            default { Write-Host '请输入 1、2、3 或 0。' -ForegroundColor Yellow }
        }
    }
}

function Invoke-DockerCleanup {
    & $cleanupScript -WaitTimeoutSeconds ([math]::Min($WaitTimeoutSeconds, 120))
    if ($LASTEXITCODE -ne 0) { throw "Docker cleanup returned exit code $LASTEXITCODE." }
}

function Get-ConfiguredDockerFrontendPort {
    $frontendPort = 8082
    if (Test-Path -LiteralPath $environmentFile) {
        foreach ($line in Get-Content -LiteralPath $environmentFile) {
            if ($line -match '^SILVERPILOT_FRONTEND_PORT=(\d+)$') {
                $frontendPort = [int]$Matches[1]
                break
            }
        }
    }
    return $frontendPort
}

function Get-ProcessRecord([int]$ProcessId) {
    return Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
}

function Test-IsManagedJarBackend([string]$CommandLine) {
    if ([string]::IsNullOrWhiteSpace($CommandLine)) { return $false }
    return $CommandLine -match '(?i)-jar\s+.*cecsms-serve-.*\.jar'
}

function Assert-NoScriptManagedBackend {
    if (Test-Path -LiteralPath $managedBackendPidFile) {
        $rawPid = (Get-Content -LiteralPath $managedBackendPidFile -Raw).Trim()
        $managedProcessId = 0
        if ([int]::TryParse($rawPid, [ref]$managedProcessId)) {
            $managedProcess = Get-ProcessRecord $managedProcessId
            if ($null -ne $managedProcess -and (Test-IsManagedJarBackend ([string]$managedProcess.CommandLine))) {
                throw "A legacy script-managed backend is still running (PID $managedProcessId). Run .\scripts\stop-local-backend.ps1 once, then start the backend from IDEA."
            }
        }
        Remove-Item -LiteralPath $managedBackendPidFile -Force
        Write-Host '[INFO] Removed a stale legacy backend PID file; no process was stopped.' -ForegroundColor Cyan
    }

    $orphan = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { Test-IsManagedJarBackend ([string]$_.CommandLine) } |
        Select-Object -First 1
    if ($null -ne $orphan) {
        throw "A Java -jar backend is running outside IDEA (PID $($orphan.ProcessId)). Stop it before using the strict IDEA + VSCode workflow."
    }
}

function Prepare-LocalIdeMode {
    Assert-NoScriptManagedBackend
    & (Join-Path $PSScriptRoot 'docker-dev.ps1') redis `
        -WaitTimeoutSeconds $WaitTimeoutSeconds -Rebuild:$Rebuild -VerifyLocalHost
    if ($LASTEXITCODE -ne 0) { throw "Docker Redis preparation returned exit code $LASTEXITCODE." }

    Write-Host '[READY] Local infrastructure is prepared. No Java or Vite process was started.' -ForegroundColor Green
    Write-Host '[NEXT 1] In IDEA, run com.cecsmsserve.CecsmsServeApplication from the backend module.' -ForegroundColor Cyan
    Write-Host '[NEXT 2] After http://127.0.0.1:8083/actuator/health is UP, run npm run dev in the VSCode frontend terminal.' -ForegroundColor Cyan
}

function Start-DockerMode {
    & (Join-Path $PSScriptRoot 'docker-dev.ps1') full `
        -WaitTimeoutSeconds $WaitTimeoutSeconds -Rebuild:$Rebuild -FullAudit:$FullAudit
    if ($LASTEXITCODE -ne 0) { throw "Docker startup returned exit code $LASTEXITCODE." }
}

if ($Mode -eq 'Choose') { $Mode = Select-RunMode }
if ($Mode -eq 'Exit') {
    Write-Host '[INFO] 未启动项目。' -ForegroundColor Cyan
    exit 0
}

if ($Mode -eq 'Stop') {
    try {
        Invoke-DockerCleanup
        exit 0
    } catch {
        Write-Host "[FAIL] Docker cleanup failed: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}

if ($Mode -eq 'Local') {
    try {
        Prepare-LocalIdeMode
        if ($OpenBrowser) {
            Write-Host '[INFO] Browser was not opened because IDEA and VSCode still own the application processes.' -ForegroundColor Cyan
        }
        if ($WaitForStop) {
            Write-Host '[INFO] WaitForStop is ignored in separated IDE mode; stop Java in IDEA and Vite in VSCode.' -ForegroundColor Cyan
        }
        exit 0
    } catch {
        Write-Host "[FAIL] Local IDE preparation failed: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "[NEXT] Run '.\scripts\docker-dev.ps1 doctor' for infrastructure diagnostics. Java and Vite were not started." -ForegroundColor Yellow
        exit 1
    }
}

try {
    # Keep streamed Docker/Compose output out of the browser target. Native
    # commands write success-stream records, so assigning this function call
    # would collect those records together with any returned value as Object[].
    Start-DockerMode
    [string]$loginUrl = "http://127.0.0.1:$(Get-ConfiguredDockerFrontendPort)/login"
    if ($OpenBrowser) {
        try {
            Start-Process -FilePath $loginUrl | Out-Null
        } catch {
            Write-Warning "Services are ready, but the browser could not be opened. Open $loginUrl manually. $($_.Exception.Message)"
        }
    }
    Write-Host "[READY] SilverPilot Docker mode is fully operational: $loginUrl" -ForegroundColor Green

    if ($WaitForStop) {
        $stopChoice = Read-Host '项目已在后台运行。输入 STOP 停止服务；直接回车退出窗口并保持运行'
        if ([string]$stopChoice -ieq 'STOP') {
            Invoke-DockerCleanup
        } else {
            Write-Host '[RUNNING] Services remain running in Docker. Use .\stop-project.cmd to stop them.' -ForegroundColor Green
        }
    } else {
        Write-Host '[RUNNING] You can close this startup window; services will keep running in Docker.' -ForegroundColor Green
        Write-Host '[STOP] Run .\stop-project.cmd when finished. IDEA and VSCode processes are never managed by this command.' -ForegroundColor Cyan
        if ($KeepConsoleOpen) {
            try {
                [void](Read-Host '启动已完成，服务正在 Docker 后台运行。按回车关闭本窗口，服务继续运行')
            } catch {
                Write-Host '[RUNNING] Console input is unavailable. Services remain running in Docker.' -ForegroundColor Green
            }
        }
    }
    exit 0
} catch {
    $startupError = $_.Exception.Message
    # A failed retry, readiness check, or console action must not tear down an
    # already running stack. docker-dev retains diagnostics and data for retry.
    Write-Host "[FAIL] SilverPilot Docker mode did not pass its startup gate: $startupError" -ForegroundColor Red
    Write-Host '[INFO] Existing containers and data were retained. Use .\stop-project.cmd for an explicit stop.' -ForegroundColor Cyan
    Write-Host "[NEXT] Stop IDEA/VSCode listeners if they occupy Docker ports, then run '.\scripts\docker-dev.ps1 doctor' and '.\scripts\docker-dev.ps1 logs'." -ForegroundColor Yellow
    exit 1
}
