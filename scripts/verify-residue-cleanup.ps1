param([string]$PowerShellExecutable = (Get-Process -Id $PID).Path)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testRoot = Join-Path $temporaryRoot ('silverpilot-cleanup-test-' + [Guid]::NewGuid().ToString('N'))
$utf8 = New-Object Text.UTF8Encoding($true)
$artifactPaths = @('.startup-build-state.json', 'SourceCode/cecsmsServe-springboot/logs/keep.log', 'SourceCode/cecsmsui-vue/node_modules/.vite/keep')

function Test-Cleanup([string]$Name, [string]$Scenario, [bool]$Preserved, [int]$ExpectedExit = 0, [switch]$StopRunning, [switch]$Busy) {
    foreach ($relative in $artifactPaths) {
        $path = Join-Path $testRoot $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
        [IO.File]::WriteAllText($path, 'preserve existing runtime evidence', $utf8)
    }
    $mutationPath = Join-Path $testRoot 'mutations.txt'
    if (Test-Path -LiteralPath $mutationPath) { Remove-Item -LiteralPath $mutationPath }
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $PowerShellExecutable
    $info.Arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $testRoot 'probe.ps1') + '" -Scenario ' + $Scenario
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.EnvironmentVariables['SILVERPILOT_CLEANUP_SELF'] = Join-Path $testRoot 'clean-project-residue.cmd'
    $info.EnvironmentVariables['SILVERPILOT_CLEANUP_STOP_RUNNING'] = if ($StopRunning) { '1' } else { '0' }
    $info.EnvironmentVariables['SILVERPILOT_CLEANUP_DRY_RUN'] = '0'
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(20000)) { $process.Kill(); throw "$Name timed out" }
        $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne $ExpectedExit) { throw "$Name returned $($process.ExitCode). $output" }
        foreach ($relative in $artifactPaths) {
            $path = Join-Path $testRoot $relative
            if ((Test-Path -LiteralPath $path) -ne $Preserved) { throw "$Name violated artifact preservation: $relative. $output" }
            if ($Preserved -and [IO.File]::ReadAllText($path) -ne 'preserve existing runtime evidence') { throw "$Name altered $relative" }
        }
        $mutations = if (Test-Path -LiteralPath $mutationPath) { @(Get-Content -LiteralPath $mutationPath) } else { @() }
        if ($Preserved -and $mutations.Count -gt 0) { throw "$Name changed active services: $mutations" }
        if (-not $Preserved -and $Scenario -ne 'ForeignOnly' -and $mutations -notcontains 'rm owned-id') { throw "$Name did not remove the expected stopped container. $output" }
        if ($StopRunning -and -not $Preserved -and $mutations -notcontains 'stop owned-id') { throw "$Name did not honor explicit shutdown. $output" }
        if ($Busy -and $output -notmatch '\[SKIPPED\].*in progress') { throw "$Name did not respect the startup lock. $output" }
        Write-Host "[PASS] $Name" -ForegroundColor Green
    } finally { $process.Dispose() }
}

try {
    New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot 'clean-project-residue.cmd') -Destination $testRoot
    [IO.File]::WriteAllText((Join-Path $testRoot 'compose.yaml'), 'test-only compose fixture', $utf8)
    # Execute the unchanged embedded cleaner against fake OS/Docker boundaries.
    # Every mutation is recorded, and any foreign-container access fails the test.
    $probe = @'
param([string]$Scenario)
$ErrorActionPreference = 'Stop'
$global:ownedRemoved = $Scenario -eq 'ForeignOnly'
$global:containerState = if ($Scenario -eq 'Restarting') { 'restarting' } elseif ($Scenario -eq 'Paused') { 'paused' } elseif ($Scenario -in @('Stopped', 'LocalRunning', 'LauncherRunning')) { 'exited' } else { 'running' }
function docker.exe { & docker @args }
function docker {
    $arguments = @($args)
    $global:LASTEXITCODE = 0
    switch ($arguments[0]) {
        'info' { if ($Scenario -eq 'Unavailable') { $global:LASTEXITCODE = 1 } else { 'test-engine' } }
        'ps' { if (-not $global:ownedRemoved) { 'owned-id' }; 'foreign-id' }
        'inspect' {
            $foreign = $arguments[-1] -eq 'foreign-id'
            $root = if ($foreign) { Join-Path $PSScriptRoot 'another-checkout' } else { $PSScriptRoot }
            $fixture = @{
                Id = $arguments[-1]; Name = '/' + $arguments[-1]
                Config = @{ Labels = @{
                    'com.docker.compose.project' = 'silverpilot'
                    'com.docker.compose.service' = 'backend'
                    'com.docker.compose.project.working_dir' = $root
                    'com.docker.compose.project.config_files' = (Join-Path $root 'compose.yaml')
                } }
                State = @{ Status = $(if ($foreign) { 'running' } else { $global:containerState }) }
            }
            ConvertTo-Json -InputObject @($fixture) -Depth 8 -Compress
        }
        'stop' {
            if ($arguments[-1] -ne 'owned-id') { throw 'Foreign container was stopped' }
            Add-Content -LiteralPath (Join-Path $PSScriptRoot 'mutations.txt') -Value 'stop owned-id'
            $global:containerState = 'exited'
        }
        'rm' {
            if ($arguments.Count -ne 2 -or $arguments[-1] -ne 'owned-id' -or $global:containerState -ne 'exited') { throw 'Unexpected container or volume removal' }
            Add-Content -LiteralPath (Join-Path $PSScriptRoot 'mutations.txt') -Value 'rm owned-id'
            $global:ownedRemoved = $true
        }
        'network' { if ($arguments[1] -ne 'ls') { throw 'Unexpected network mutation' } }
        default { throw "Unexpected Docker command: $arguments" }
    }
}
function Get-CimInstance {
    param($ClassName, $Filter)
    if ($Scenario -eq 'LocalRunning') {
        [pscustomobject]@{ ProcessId = 700001; ParentProcessId = 0; Name = 'java.exe'; CommandLine = 'java -cp "' + (Join-Path $PSScriptRoot 'SourceCode/cecsmsServe-springboot/target/classes') + '" com.cecsmsserve.CecsmsServeApplication' }
    } elseif ($Scenario -eq 'LauncherRunning') {
        [pscustomobject]@{ ProcessId = 700002; ParentProcessId = 0; Name = 'pwsh.exe'; CommandLine = 'pwsh -File "' + (Join-Path $PSScriptRoot 'scripts/start-project.ps1') + '" -Mode Docker' }
    }
}
function Get-NetTCPConnection { param($State, $LocalPort) }
function Stop-Process { throw 'An active local process was terminated' }
function Start-Process { throw 'Docker Desktop is unavailable in this isolated fixture' }
$text = [IO.File]::ReadAllText($env:SILVERPILOT_CLEANUP_SELF)
$marker = ':__SILVERPILOT_POWERSHELL__'
& ([scriptblock]::Create($text.Substring($text.LastIndexOf($marker) + $marker.Length)))
exit $LASTEXITCODE
'@
    [IO.File]::WriteAllText((Join-Path $testRoot 'probe.ps1'), $probe, $utf8)
    Test-Cleanup 'Running Docker services and artifacts are preserved' 'Running' $true
    Test-Cleanup 'A restarting backend is not treated as residue' 'Restarting' $true
    Test-Cleanup 'Paused containers are preserved' 'Paused' $true
    Test-Cleanup 'Active local Java is preserved' 'LocalRunning' $true
    Test-Cleanup 'An active startup window is preserved' 'LauncherRunning' $true
    Test-Cleanup 'Stopped containers can still be cleaned without deleting data volumes' 'Stopped' $false
    Test-Cleanup 'A different checkout sharing the Compose name is untouched' 'ForeignOnly' $false
    Test-Cleanup 'Explicit full cleanup still stops and removes the owned container' 'Running' $false -StopRunning
    Test-Cleanup 'Unavailable Docker preserves diagnostic artifacts' 'Unavailable' $true 1
    . (Join-Path $PSScriptRoot 'project-lifecycle.ps1')
    $lock = Enter-ProjectLifecycleLock $testRoot
    try {
        Test-Cleanup 'Cleanup cannot interrupt a concurrent startup' 'Stopped' $true -Busy
        Test-Cleanup 'Explicit cleanup also respects an active lifecycle operation' 'Running' $true -Busy -StopRunning
    } finally { Exit-ProjectLifecycleLock $lock }
    Test-Cleanup 'Cleanup is available again after the lifecycle owner exits' 'Stopped' $false
    Write-Host '[PASS] All 12 residue-cleanup checks passed without accessing live services.' -ForegroundColor Green
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $prefix = $temporaryRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $resolved) -notmatch '^silverpilot-cleanup-test-[a-f0-9]{32}$') {
        throw 'Refusing to delete an unexpected test directory.'
    }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
exit 0
