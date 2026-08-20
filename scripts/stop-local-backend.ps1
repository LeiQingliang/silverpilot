param(
    [string]$BackendRoot = (Join-Path $PSScriptRoot '..\SourceCode\cecsmsServe-springboot'),
    [ValidateRange(5, 60)]
    [int]$WaitTimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
$resolvedBackend = [System.IO.Path]::GetFullPath($BackendRoot)
$pidFile = Join-Path $resolvedBackend 'target\local-backend.pid'

function Get-ProcessRecord([int]$ProcessId) {
    return Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
}

function Test-IsLegacyManagedJar([string]$CommandLine) {
    if ([string]::IsNullOrWhiteSpace($CommandLine)) { return $false }
    return $CommandLine -match '(?i)-jar\s+.*cecsms-serve-.*\.jar'
}

if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host '[PASS] No legacy script-managed backend PID file exists. IDEA-owned Java processes were not inspected or stopped.' -ForegroundColor Green
    exit 0
}

$rawPid = (Get-Content -LiteralPath $pidFile -Raw).Trim()
$managedProcessId = 0
if (-not [int]::TryParse($rawPid, [ref]$managedProcessId)) {
    Remove-Item -LiteralPath $pidFile -Force
    Write-Warning 'Removed an invalid legacy PID file; no process was stopped.'
    exit 0
}

$managedProcess = Get-ProcessRecord $managedProcessId
if ($null -eq $managedProcess) {
    Remove-Item -LiteralPath $pidFile -Force
    Write-Host '[PASS] Legacy managed backend was already stopped; its stale PID file was removed.' -ForegroundColor Green
    exit 0
}

if (-not (Test-IsLegacyManagedJar ([string]$managedProcess.CommandLine))) {
    Remove-Item -LiteralPath $pidFile -Force
    Write-Warning "PID $managedProcessId is not a legacy Java -jar backend. The PID file was removed and the process was not touched."
    exit 0
}

Stop-Process -Id $managedProcessId -ErrorAction Stop
$deadline = [DateTime]::UtcNow.AddSeconds($WaitTimeoutSeconds)
do {
    if ($null -eq (Get-ProcessRecord $managedProcessId)) { break }
    Start-Sleep -Milliseconds 500
} while ([DateTime]::UtcNow -lt $deadline)

if ($null -ne (Get-ProcessRecord $managedProcessId)) {
    throw "Legacy script-managed backend PID $managedProcessId did not stop within $WaitTimeoutSeconds seconds."
}

Remove-Item -LiteralPath $pidFile -Force
Write-Host "[PASS] Stopped legacy script-managed backend PID $managedProcessId. IDEA-owned processes were not touched." -ForegroundColor Green
exit 0
