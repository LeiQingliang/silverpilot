[CmdletBinding()]
param(
    [ValidateSet('All', 'Update', 'Scan', 'Status')]
    [string]$Mode = 'All',
    [ValidateRange(1, 60)]
    [int]$UpdateTimeoutMinutes = 15,
    [ValidateRange(1, 60)]
    [int]$ScanTimeoutMinutes = 10,
    [ValidateRange(24, 720)]
    [int]$MaximumDataAgeHours = 168,
    [ValidateRange(1, 168)]
    [int]$RefreshAfterHours = 24,
    [switch]$ForceUpdate
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
$dataDirectory = Join-Path $projectRoot '.dependency-check-data'
$databasePath = Join-Path $dataDirectory 'odc.mv.db'
$markerPath = Join-Path $dataDirectory 'silverpilot-update.json'
$updateLockPath = Join-Path $dataDirectory 'odc.update.lock'
$backupDirectory = Join-Path $dataDirectory 'silverpilot-last-verified'
$backupDatabasePath = Join-Path $backupDirectory 'odc.mv.db'
$backupMarkerPath = Join-Path $backupDirectory 'silverpilot-update.json'
$reportDirectory = Join-Path $backendRoot 'target\dependency-check'
$logDirectory = Join-Path $reportDirectory 'logs'
$pluginVersion = '13.0.0'
$pluginCoordinate = "org.owasp:dependency-check-maven:$pluginVersion"
$mirrorUrl = 'https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/nvdcve-{0}.json.gz'
$runningOnWindows = $env:OS -eq 'Windows_NT'
$mavenWrapper = Join-Path $backendRoot $(if ($runningOnWindows) { 'mvnw.cmd' } else { 'mvnw' })

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven wrapper is missing: $mavenWrapper"
}

function Get-DataState {
    $database = Get-Item -LiteralPath $databasePath -ErrorAction SilentlyContinue
    $updatedAt = $null
    $source = 'none'
    $markerVersion = ''
    $expectedDatabaseHash = ''

    if (Test-Path -LiteralPath $markerPath -PathType Leaf) {
        try {
            $marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
            $updatedAt = [DateTimeOffset]::Parse([string]$marker.lastSuccessfulUpdateUtc).ToUniversalTime()
            $markerVersion = [string]$marker.dependencyCheckVersion
            $expectedDatabaseHash = [string]$marker.databaseSha256
            $source = 'marker'
        } catch {
            Write-Warning "Ignoring an invalid Dependency-Check marker: $markerPath"
        }
    }

    if ($null -eq $updatedAt -and $null -ne $database) {
        $updatedAt = [DateTimeOffset]$database.LastWriteTimeUtc
        $source = 'database-mtime'
    }

    $ageHours = [double]::PositiveInfinity
    if ($null -ne $updatedAt) {
        $ageHours = ([DateTimeOffset]::UtcNow - $updatedAt).TotalHours
    }
    $databaseBytes = if ($null -ne $database) { [long]$database.Length } else { 0L }
    $integrityVerified = $false
    if ($databaseBytes -ge 1MB -and $expectedDatabaseHash -match '^[0-9a-fA-F]{64}$') {
        $actualDatabaseHash = (Get-FileHash -LiteralPath $databasePath -Algorithm SHA256).Hash
        $integrityVerified = $actualDatabaseHash -ceq $expectedDatabaseHash.ToUpperInvariant()
    }
    $versionMatches = $markerVersion -ceq $pluginVersion
    $usable = $databaseBytes -ge 1MB -and $ageHours -ge -1 -and $ageHours -le $MaximumDataAgeHours -and $integrityVerified -and $versionMatches

    return [pscustomobject]@{
        DatabaseExists = $null -ne $database
        DatabaseBytes = $databaseBytes
        UpdatedAtUtc = $updatedAt
        AgeHours = $ageHours
        TimestampSource = $source
        IntegrityVerified = $integrityVerified
        VersionMatches = $versionMatches
        Usable = $usable
    }
}

function Write-DataState([object]$State) {
    $age = if ([double]::IsPositiveInfinity([double]$State.AgeHours)) { 'unknown' } else { '{0:N1}' -f $State.AgeHours }
    $updated = if ($null -eq $State.UpdatedAtUtc) { 'unknown' } else { $State.UpdatedAtUtc.ToString('u') }
    Write-Host "[INFO] Dependency-Check cache: exists=$($State.DatabaseExists), bytes=$($State.DatabaseBytes), updated=$updated, ageHours=$age, sha256Verified=$($State.IntegrityVerified), versionMatches=$($State.VersionMatches), usable=$($State.Usable)." -ForegroundColor Cyan
}

function Remove-ValidatedChildDirectory([string]$Path, [string]$Parent) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $fullParent = [System.IO.Path]::GetFullPath($Parent).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $requiredPrefix = $fullParent + [System.IO.Path]::DirectorySeparatorChar
    if (-not $fullPath.StartsWith($requiredPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a directory outside the Dependency-Check cache: $fullPath"
    }
    if (Test-Path -LiteralPath $fullPath -PathType Container) {
        [System.IO.Directory]::Delete($fullPath, $true)
    }
}

function Restore-VerifiedBackup {
    if (-not (Test-Path -LiteralPath $backupDatabasePath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $backupMarkerPath -PathType Leaf)) {
        return $false
    }
    Remove-Item -LiteralPath $updateLockPath -Force -ErrorAction SilentlyContinue
    Copy-Item -LiteralPath $backupDatabasePath -Destination $databasePath -Force
    Copy-Item -LiteralPath $backupMarkerPath -Destination $markerPath -Force
    return $true
}

function Stop-ProcessTree([System.Diagnostics.Process]$Process) {
    if ($Process.HasExited) { return }
    if ($runningOnWindows) {
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & taskkill.exe /PID $Process.Id /T /F *> $null
        } finally {
            $ErrorActionPreference = $previousPreference
        }
    } else {
        try { $Process.Kill($true) } catch { $Process.Kill() }
    }
    [void]$Process.WaitForExit(10000)
}

function Show-LogTail([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
    $lines = @(Get-Content -LiteralPath $Path -ErrorAction SilentlyContinue)
    if ($lines.Count -eq 0) { return }
    Write-Host "--- $Label (last $([Math]::Min(200, $lines.Count)) lines) ---" -ForegroundColor DarkGray
    $lines | Select-Object -Last 200 | ForEach-Object { Write-Host $_ }
}

function Invoke-BoundedMaven {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][int]$TimeoutMinutes
    )

    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $slug = ($Label -replace '[^a-zA-Z0-9_-]', '-').ToLowerInvariant()
    $stamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMdd-HHmmss')
    $stdoutPath = Join-Path $logDirectory "$stamp-$slug.stdout.log"
    $stderrPath = Join-Path $logDirectory "$stamp-$slug.stderr.log"
    $startParameters = @{
        FilePath = $mavenWrapper
        ArgumentList = $Arguments
        WorkingDirectory = $backendRoot
        PassThru = $true
        RedirectStandardOutput = $stdoutPath
        RedirectStandardError = $stderrPath
    }
    if ($runningOnWindows) { $startParameters.WindowStyle = 'Hidden' }

    Write-Host "[INFO] $Label started with a ${TimeoutMinutes}-minute hard timeout." -ForegroundColor Cyan
    $process = Start-Process @startParameters
    $deadline = [DateTimeOffset]::UtcNow.AddMinutes($TimeoutMinutes)
    $timedOut = $false
    while (-not $process.WaitForExit(1000)) {
        if ([DateTimeOffset]::UtcNow -ge $deadline) {
            $timedOut = $true
            Stop-ProcessTree $process
            break
        }
    }
    if (-not $process.HasExited) { Stop-ProcessTree $process }
    $process.Refresh()
    $exitCode = if ($timedOut) { 124 } else { $process.ExitCode }

    Show-LogTail $stdoutPath "$Label stdout"
    Show-LogTail $stderrPath "$Label stderr"
    Write-Host "[INFO] Full logs: $stdoutPath ; $stderrPath" -ForegroundColor DarkGray

    return [pscustomobject]@{
        ExitCode = $exitCode
        TimedOut = $timedOut
        StandardOutput = $stdoutPath
        StandardError = $stderrPath
    }
}

function Invoke-Update {
    New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
    $preUpdateState = Get-DataState
    $backupAvailable = $false
    Remove-ValidatedChildDirectory $backupDirectory $dataDirectory
    # Preserve a hash-verified prior cache even across scanner upgrades. It may
    # not be readable by the new scanner, but an interrupted migration must not
    # destroy the last known-good database and trust marker.
    if ($preUpdateState.DatabaseExists -and $preUpdateState.DatabaseBytes -ge 1MB -and $preUpdateState.IntegrityVerified) {
        New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
        Copy-Item -LiteralPath $databasePath -Destination $backupDatabasePath
        Copy-Item -LiteralPath $markerPath -Destination $backupMarkerPath
        $backupAvailable = $true
    }

    try {
        $result = Invoke-BoundedMaven -Label 'Dependency-Check mirror update' -TimeoutMinutes $UpdateTimeoutMinutes -Arguments @(
            '-B',
            '-ntp',
            '-Pdependency-check',
            "${pluginCoordinate}:update-only",
            '-DautoUpdate=true'
        )
    } catch {
        if ($backupAvailable) { [void](Restore-VerifiedBackup) }
        throw
    }
    if ($result.ExitCode -ne 0) {
        if ($backupAvailable) { [void](Restore-VerifiedBackup) }
        return $result
    }

    try {
        Remove-Item -LiteralPath $updateLockPath -Force -ErrorAction SilentlyContinue
        $database = Get-Item -LiteralPath $databasePath -ErrorAction SilentlyContinue
        if ($null -eq $database -or $database.Length -lt 1MB) {
            throw "Dependency-Check update exited successfully but did not create a usable database at $databasePath"
        }
        $databaseHash = (Get-FileHash -LiteralPath $databasePath -Algorithm SHA256).Hash.ToLowerInvariant()
        $temporaryMarkerPath = Join-Path $dataDirectory "silverpilot-update-$PID.tmp"
        [ordered]@{
            schemaVersion = 2
            dependencyCheckVersion = $pluginVersion
            lastSuccessfulUpdateUtc = [DateTimeOffset]::UtcNow.ToString('o')
            source = $mirrorUrl
            directNvdApiUsed = $false
            databaseSha256 = $databaseHash
        } | ConvertTo-Json | Set-Content -LiteralPath $temporaryMarkerPath -Encoding UTF8
        Move-Item -LiteralPath $temporaryMarkerPath -Destination $markerPath -Force
        Remove-ValidatedChildDirectory $backupDirectory $dataDirectory
    } catch {
        if ($backupAvailable) { [void](Restore-VerifiedBackup) }
        throw
    }

    return $result
}

function Show-ReportSummary {
    $jsonReport = Join-Path $reportDirectory 'dependency-check-report.json'
    if (-not (Test-Path -LiteralPath $jsonReport -PathType Leaf)) { return }
    try {
        $report = Get-Content -LiteralPath $jsonReport -Raw | ConvertFrom-Json
        $dependencies = @($report.dependencies)
        $vulnerabilities = New-Object System.Collections.Generic.List[object]
        $affected = New-Object System.Collections.Generic.List[object]
        foreach ($dependency in $dependencies) {
            $property = $dependency.PSObject.Properties['vulnerabilities']
            if ($null -eq $property) { continue }
            $dependencyVulnerabilities = @($property.Value)
            if ($dependencyVulnerabilities.Count -eq 0) { continue }
            $affected.Add($dependency)
            foreach ($vulnerability in $dependencyVulnerabilities) { $vulnerabilities.Add($vulnerability) }
        }
        Write-Host "[INFO] SCA report summary: dependencies=$($dependencies.Count), affected=$($affected.Count), vulnerabilities=$($vulnerabilities.Count)." -ForegroundColor Cyan
    } catch {
        Write-Warning "Unable to summarize $jsonReport, but the original report remains available. $($_.Exception.Message)"
    }
}

function Invoke-Scan {
    $state = Get-DataState
    Write-DataState $state
    if (-not $state.Usable) {
        throw "Dependency-Check cache is missing or older than $MaximumDataAgeHours hours. Run this script in Update or All mode while the OWASP mirror is reachable."
    }

    $result = Invoke-BoundedMaven -Label 'Dependency-Check offline scan' -TimeoutMinutes $ScanTimeoutMinutes -Arguments @(
        '-B',
        '-ntp',
        '-Pdependency-check',
        "${pluginCoordinate}:check",
        '-DautoUpdate=false'
    )
    Show-ReportSummary
    if ($result.TimedOut) { throw "Dependency-Check offline scan exceeded $ScanTimeoutMinutes minutes." }
    if ($result.ExitCode -ne 0) {
        throw "Dependency-Check offline scan failed or found a CVSS 7.0+ vulnerability. Review $reportDirectory and the retained Maven logs."
    }
    foreach ($requiredReport in @('dependency-check-report.html', 'dependency-check-report.json', 'dependency-check-report.sarif', 'dependency-check-junit.xml')) {
        $path = Join-Path $reportDirectory $requiredReport
        if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-Item -LiteralPath $path).Length -eq 0) {
            throw "Dependency-Check did not produce the required report: $path"
        }
    }
    Write-Host "[PASS] Dependency-Check completed from the persistent offline cache. Reports: $reportDirectory" -ForegroundColor Green
}

$initialState = Get-DataState
Write-DataState $initialState
if ($Mode -eq 'Status') { exit $(if ($initialState.Usable) { 0 } else { 1 }) }

if ($Mode -in @('All', 'Update')) {
    $needsUpdate = $ForceUpdate -or -not $initialState.Usable -or $initialState.AgeHours -ge $RefreshAfterHours
    if ($needsUpdate) {
        $updateResult = Invoke-Update
        if ($updateResult.ExitCode -ne 0) {
            $fallbackState = Get-DataState
            if ($Mode -eq 'All' -and $fallbackState.Usable) {
                $reason = if ($updateResult.TimedOut) { 'timed out' } else { "failed with exit code $($updateResult.ExitCode)" }
                Write-Warning "The OWASP mirror refresh $reason; continuing with the last verified cache because it is within the $MaximumDataAgeHours-hour offline window."
            } else {
                $reason = if ($updateResult.TimedOut) { "exceeded $UpdateTimeoutMinutes minutes" } else { "failed with exit code $($updateResult.ExitCode)" }
                throw "Dependency-Check mirror update $reason and no acceptable cached database is available."
            }
        } else {
            $updatedState = Get-DataState
            Write-DataState $updatedState
            if (-not $updatedState.Usable) { throw 'Dependency-Check update did not leave a usable cache.' }
            Write-Host '[PASS] Dependency-Check data refreshed from the OWASP-maintained mirror without using the NVD REST API.' -ForegroundColor Green
        }
    } else {
        Write-Host "[PASS] Dependency-Check data is newer than $RefreshAfterHours hours; remote refresh skipped." -ForegroundColor Green
    }
}

if ($Mode -in @('All', 'Scan')) { Invoke-Scan }
