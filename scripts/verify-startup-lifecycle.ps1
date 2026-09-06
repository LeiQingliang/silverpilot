param([string]$PowerShellExecutable = (Get-Process -Id $PID).Path)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testRoot = Join-Path $temporaryRoot ('silverpilot-startup-test-' + [Guid]::NewGuid().ToString('N'))
$testScripts = Join-Path $testRoot 'scripts'
$stopMarker = Join-Path $testRoot 'stopped'
$failureMarker = Join-Path $testRoot 'fail-start'
$utf8 = New-Object Text.UTF8Encoding($true)

function Write-TestFile([string]$RelativePath, [string]$Content) {
    [IO.File]::WriteAllText((Join-Path $testRoot $RelativePath), $Content, $utf8)
}

function Test-Launch([string]$Name, [string]$FileName, [string]$Arguments, [int]$ExpectedExit, [bool]$ExpectedStop, [string]$InputLine = '') {
    if (Test-Path -LiteralPath $stopMarker) { Remove-Item -LiteralPath $stopMarker }
    $startInfo = New-Object Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FileName
    $startInfo.Arguments = $Arguments
    $startInfo.WorkingDirectory = $testRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.EnvironmentVariables['SILVERPILOT_SKIP_BROWSER'] = '1'
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.WriteLine($InputLine)
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(20000)) {
            $process.Kill()
            throw "$Name did not finish after console input closed."
        }
        $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne $ExpectedExit -or (Test-Path -LiteralPath $stopMarker) -ne $ExpectedStop) {
            throw "$Name violated its exit/stop contract (exit $($process.ExitCode), stopped $(Test-Path -LiteralPath $stopMarker)). $output"
        }
        Write-Host "[PASS] $Name" -ForegroundColor Green
    } finally {
        $process.Dispose()
    }
}

function Test-ReadyConsole([string]$Name, [string]$FileName, [string]$Arguments, [switch]$Terminate) {
    if (Test-Path -LiteralPath $stopMarker) { Remove-Item -LiteralPath $stopMarker }
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $FileName
    $info.Arguments = $Arguments
    $info.WorkingDirectory = $testRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.EnvironmentVariables['SILVERPILOT_SKIP_BROWSER'] = '1'
    $info.EnvironmentVariables['SILVERPILOT_NO_PAUSE'] = '0'
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $stderr = $process.StandardError.ReadToEndAsync()
        $ready = $false
        $deadline = [DateTime]::UtcNow.AddSeconds(15)
        while (-not $ready -and [DateTime]::UtcNow -lt $deadline) {
            $line = $process.StandardOutput.ReadLineAsync()
            if (-not $line.Wait(5000)) { throw "$Name did not reach the ready message" }
            if ($null -eq $line.Result) { break }
            $ready = $line.Result -match '\[READY\]'
        }
        Start-Sleep -Milliseconds 200
        if (-not $ready -or $process.HasExited) { throw "$Name closed before showing the ready console" }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        if ($Terminate) { $process.Kill() } else { $process.StandardInput.Close() }
        if (-not $process.WaitForExit(10000)) { throw "$Name did not exit after its console closed" }
        if ((-not $Terminate -and $process.ExitCode -ne 0) -or (Test-Path -LiteralPath $stopMarker)) {
            throw "$Name stopped services or returned an unexpected failure. $($stderr.GetAwaiter().GetResult())"
        }
        Write-Host "[PASS] $Name" -ForegroundColor Green
    } finally {
        if (-not $process.HasExited) { $process.StandardInput.Close(); if (-not $process.WaitForExit(2000)) { $process.Kill() } }
        $process.Dispose()
    }
}

try {
    New-Item -ItemType Directory -Path $testScripts -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start-project.ps1') -Destination $testScripts
    foreach ($entry in @('start-project.cmd', 'start-docker.cmd')) {
        Copy-Item -LiteralPath (Join-Path $projectRoot $entry) -Destination $testRoot
    }
    # Execute the real launcher and CMD wrappers. Only Docker work and browser
    # launching are replaced; no live service, account, or volume is touched.
    Write-TestFile 'scripts/docker-dev.ps1' @'
param($Action, $WaitTimeoutSeconds, [switch]$Rebuild, [switch]$FullAudit)
if (Test-Path -LiteralPath (Join-Path $PSScriptRoot '../fail-start')) { exit 7 }
Write-Host '[TEST] Existing stack is healthy.'
exit 0
'@
    Write-TestFile 'scripts/stop-project.ps1' @'
param($WaitTimeoutSeconds)
Set-Content -LiteralPath (Join-Path $PSScriptRoot '../stopped') -Value 'explicit stop'
exit 0
'@
    Write-TestFile 'browser-failure.ps1' @'
function Start-Process { param($FilePath) throw 'Simulated default browser failure' }
& (Join-Path $PSScriptRoot 'scripts/start-project.ps1') -Mode Docker -OpenBrowser
exit $LASTEXITCODE
'@
    $launcherArgs = '-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $testScripts 'start-project.ps1') + '"'
    Test-Launch 'PowerShell startup exits while services remain running' $PowerShellExecutable "$launcherArgs -Mode Docker" 0 $false
    Test-Launch 'Docker CMD closes without stopping services' $env:ComSpec '/d /c start-docker.cmd' 0 $false
    Test-Launch 'Menu CMD supports nonblocking Docker startup' $env:ComSpec '/d /c start-project.cmd -Mode Docker' 0 $false
    Test-ReadyConsole 'Docker CMD displays ready status until console input closes' $env:ComSpec '/d /c start-docker.cmd'
    Test-ReadyConsole 'Menu CMD displays ready status until console input closes' $env:ComSpec '/d /c start-project.cmd -Mode Docker'
    Test-ReadyConsole 'Terminating the ready launcher cannot stop Docker services' $PowerShellExecutable "$launcherArgs -Mode Docker -KeepConsoleOpen" -Terminate
    Test-Launch 'Default ready console does not interpret input as shutdown' $PowerShellExecutable "$launcherArgs -Mode Docker -KeepConsoleOpen" 0 $false 'STOP'
    Test-Launch 'Enter in optional wait mode keeps services running' $PowerShellExecutable "$launcherArgs -Mode Docker -WaitForStop" 0 $false
    Test-Launch 'Explicit STOP in wait mode stops services' $PowerShellExecutable "$launcherArgs -Mode Docker -WaitForStop" 0 $true 'STOP'
    Test-Launch 'Browser launch failure preserves the ready stack' $PowerShellExecutable ('-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $testRoot 'browser-failure.ps1') + '"') 0 $false
    Write-TestFile 'fail-start' 'simulate startup gate failure'
    Test-Launch 'Failed startup returns an error without tearing down existing services' $PowerShellExecutable "$launcherArgs -Mode Docker" 1 $false
    Test-Launch 'Explicit Stop mode remains available' $PowerShellExecutable "$launcherArgs -Mode Stop" 0 $true
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'project-lifecycle.ps1') -Destination $testScripts
    . (Join-Path $testScripts 'project-lifecycle.ps1')
    Write-TestFile 'lock-probe.ps1' @'
param([switch]$OtherCheckout)
. (Join-Path $PSScriptRoot 'scripts/project-lifecycle.ps1')
$root = if ($OtherCheckout) { Join-Path $PSScriptRoot 'another-checkout' } else { $PSScriptRoot }
try {
    $lock = Enter-ProjectLifecycleLock $root
    Exit-ProjectLifecycleLock $lock
    exit 0
} catch { exit 1 }
'@
    $lockArgs = '-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $testRoot 'lock-probe.ps1') + '"'
    $lock = Enter-ProjectLifecycleLock $testRoot
    try {
        Test-Launch 'Concurrent lifecycle operation is rejected before mutation' $PowerShellExecutable $lockArgs 1 $false
        Test-Launch 'Independent checkout is not blocked' $PowerShellExecutable "$lockArgs -OtherCheckout" 0 $false
    } finally { Exit-ProjectLifecycleLock $lock }
    Test-Launch 'Lifecycle can proceed after the previous owner exits' $PowerShellExecutable $lockArgs 0 $false

    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'stop-project.ps1') -Destination $testScripts -Force
    Write-TestFile 'stop-probe.ps1' @'
param([switch]$EngineUnavailable, [switch]$AlreadyStopped, [switch]$FailStop)
$ErrorActionPreference = 'Stop'
$global:testContainersRemoved = [bool]$AlreadyStopped
function docker {
    $arguments = @($args)
    $global:LASTEXITCODE = 0
    switch ($arguments[0]) {
        'info' { if ($EngineUnavailable) { $global:LASTEXITCODE = 1; 'daemon unavailable' } else { 'test' } }
        'ps' { if (-not $global:testContainersRemoved) { 'owned-id' }; 'foreign-id' }
        'inspect' {
            if ($arguments[-1] -eq 'foreign-id') {
                @{ 'com.docker.compose.project.working_dir' = (Join-Path $PSScriptRoot 'other'); 'com.docker.compose.project.config_files' = (Join-Path $PSScriptRoot 'other/compose.yaml') } | ConvertTo-Json -Compress
            } elseif ($arguments[2] -eq '{{json .Config.Labels}}') {
                @{ 'com.docker.compose.project.working_dir' = $PSScriptRoot; 'com.docker.compose.project.config_files' = (Join-Path $PSScriptRoot 'compose.yaml'); 'com.docker.compose.project' = 'own-project' } | ConvertTo-Json -Compress
            } else { '{"own-network":{}}' }
        }
        'stop' {
            if ($arguments[-1] -ne 'owned-id' -or $arguments -contains 'foreign-id') { throw 'Foreign container targeted' }
            if ($FailStop) { $global:LASTEXITCODE = 1; 'simulated stop failure' }
        }
        'rm' {
            if ($arguments.Count -ne 2 -or $arguments[1] -ne 'owned-id') { throw 'Unexpected removal or volume deletion' }
            $global:testContainersRemoved = $true
            Set-Content -LiteralPath (Join-Path $PSScriptRoot 'stopped') -Value 'verified own container removed'
        }
        'network' {
            if ($arguments[1] -eq 'inspect') { '[{"Labels":{"com.docker.compose.project":"own-project"},"Containers":{}}]' }
            elseif ($arguments[1] -ne 'rm' -or $arguments[-1] -ne 'own-network') { throw 'Foreign network targeted' }
        }
        default { throw "Unexpected Docker command: $arguments" }
    }
}
& (Join-Path $PSScriptRoot 'scripts/stop-project.ps1')
exit $LASTEXITCODE
'@
    $stopArgs = '-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $testRoot 'stop-probe.ps1') + '"'
    Test-Launch 'Missing environment file still stops only owned containers' $PowerShellExecutable $stopArgs 0 $true
    Test-Launch 'Repeated shutdown is idempotent and leaves foreign containers alone' $PowerShellExecutable "$stopArgs -AlreadyStopped" 0 $false
    Test-Launch 'Unavailable engine cannot be reported as successful shutdown' $PowerShellExecutable "$stopArgs -EngineUnavailable" 1 $false
    Test-Launch 'Failed graceful stop cannot remove containers or report success' $PowerShellExecutable "$stopArgs -FailStop" 1 $false
    Write-Host '[PASS] All 19 startup/shutdown lifecycle checks passed without accessing live Docker.' -ForegroundColor Green
} finally {
    $resolvedTestRoot = [IO.Path]::GetFullPath($testRoot)
    $allowedPrefix = $temporaryRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedTestRoot.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $resolvedTestRoot) -notmatch '^silverpilot-startup-test-[a-f0-9]{32}$') {
        throw 'Refusing to clean an unexpected temporary test path.'
    }
    if (Test-Path -LiteralPath $resolvedTestRoot) { Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force }
}
exit 0
