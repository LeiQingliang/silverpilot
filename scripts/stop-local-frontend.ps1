param(
    [string]$FrontendRoot = (Join-Path $PSScriptRoot '..\SourceCode\cecsmsui-vue')
)

$ErrorActionPreference = 'Stop'
$resolvedFrontend = [System.IO.Path]::GetFullPath($FrontendRoot)
$stopMarker = Join-Path $resolvedFrontend 'node_modules\.cecsms-vite-stop-requested'
$frontendProcessIdentity = ([regex]::Replace($resolvedFrontend, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
if (-not (Test-Path -LiteralPath (Join-Path $resolvedFrontend 'package.json'))) {
    throw "Frontend package.json was not found under: $resolvedFrontend"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue |
    Select-Object -First 1
function Test-IsProjectViteCommand([string]$CommandLine) {
    if ([string]::IsNullOrWhiteSpace($CommandLine)) {
        return $false
    }
    $normalizedCommand = ([regex]::Replace($CommandLine, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
    return $normalizedCommand.Contains($frontendProcessIdentity) -and $normalizedCommand.Contains('vite')
}

if ($null -ne $listener) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)" -ErrorAction SilentlyContinue
    $ownerCommand = if ($null -eq $owner) { '' } else { [string]$owner.CommandLine }
    if (-not (Test-IsProjectViteCommand $ownerCommand)) {
        $ownerName = if ($null -eq $owner) { 'unknown process' } else { $owner.Name }
        throw "Port 8081 belongs to $ownerName (PID $($listener.OwningProcess)), not this project. Nothing was stopped."
    }
}

$viteProcesses = @(Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" -ErrorAction SilentlyContinue |
    Where-Object {
        Test-IsProjectViteCommand ([string]$_.CommandLine)
    })
if ($viteProcesses.Count -eq 0) {
    if (Test-Path -LiteralPath $stopMarker) {
        Remove-Item -LiteralPath $stopMarker -Force
    }
    Write-Host '[PASS] This project has no running Vite process; port 8081 is already free.' -ForegroundColor Green
    exit 0
}

[IO.File]::WriteAllText($stopMarker, 'requested', [Text.UTF8Encoding]::new($false))
foreach ($process in $viteProcesses) {
    Stop-Process -Id $process.ProcessId -Force
    Write-Host "[INFO] Stopped project Vite process PID $($process.ProcessId)."
}

for ($attempt = 0; $attempt -lt 10; $attempt++) {
    if ($null -eq (Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue)) {
        Write-Host '[PASS] Frontend stopped; port 8081 is free.' -ForegroundColor Green
        exit 0
    }
    Start-Sleep -Milliseconds 200
}
throw 'The project Vite process was stopped, but port 8081 did not become free.'
