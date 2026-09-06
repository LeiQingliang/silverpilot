param(
    [ValidateSet('up', 'dev', 'redis', 'full', 'restart', 'down', 'status', 'logs', 'config', 'doctor', 'rotate-mcp', 'reset')]
    [string]$Action = 'up',
    [switch]$Force,
    [switch]$Rebuild,
    [switch]$FullAudit,
    [switch]$VerifyLocalHost,
    [ValidateRange(60, 900)]
    [int]$WaitTimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security -ErrorAction Stop
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$composeFile = Join-Path $projectRoot 'compose.yaml'
$environmentFile = Join-Path $projectRoot '.env.docker'
$environmentTemplate = Join-Path $projectRoot '.env.docker.example'
$environmentBackup = Join-Path $projectRoot '.env.docker.dpapi'
$buildStateFile = Join-Path $projectRoot '.startup-build-state.json'
$verificationScript = Join-Path $PSScriptRoot 'verify-running-stack.ps1'
$seedImageVerificationScript = Join-Path $PSScriptRoot 'validate-seed-assets.ps1'
$redisVerificationScript = Join-Path $PSScriptRoot 'verify-docker-redis.ps1'
$versionVerificationScript = Join-Path $PSScriptRoot 'verify-version-policy.ps1'
$backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
$localConfigDirectory = Join-Path $backendRoot 'config'
$hostProperties = Join-Path $localConfigDirectory 'application-host.properties'
$legacyProfileProperties = @(
    (Join-Path $backendRoot 'src\main\resources\application-local.properties'),
    (Join-Path $backendRoot 'src\main\resources\application-host.properties')
)
$script:runningComposeServices = $null
$script:excludedTcpPortRanges = @()
$script:excludedTcpPortRangesLoaded = $false
$requiredSecretNames = @(
    'SILVERPILOT_MYSQL_ROOT_PASSWORD',
    'SILVERPILOT_DB_PASSWORD',
    'SILVERPILOT_REDIS_PASSWORD',
    'SILVERPILOT_JWT_SECRET',
    'SILVERPILOT_MCP_API_KEY'
)

function Invoke-DockerCapture([string[]]$Arguments) {
    # Windows PowerShell 5.1 promotes native stderr to an ErrorRecord when the
    # script uses Stop. Docker probes intentionally return non-zero for absent
    # images, volumes, containers, or a stopped daemon, so capture their native
    # contract and let each caller decide whether that result is expected.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& docker @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = @($output | ForEach-Object { [string]$_ })
    }
}

function Format-DockerFailure($Result) {
    $details = @($Result.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join '; '
    if ([string]::IsNullOrWhiteSpace($details)) { return "exit code $($Result.ExitCode)" }
    return "exit code $($Result.ExitCode): $details"
}

function New-LocalSecret([int]$ByteCount = 32) {
    $bytes = New-Object byte[] $ByteCount
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Set-EnvironmentLine([string]$Content, [string]$Name, [string]$Value) {
    $escapedName = [regex]::Escape($Name)
    $pattern = "(?m)^$escapedName=.*$"
    if ($Content -notmatch $pattern) {
        return $Content.TrimEnd() + [Environment]::NewLine + "$Name=$Value" + [Environment]::NewLine
    }
    return [regex]::Replace($Content, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{
        param($match)
        return "$Name=$Value"
    })
}

function Get-EnvironmentValueFromContent([string]$Content, [string]$Name) {
    $escapedName = [regex]::Escape($Name)
    $match = [regex]::Match($Content, "(?m)^$escapedName=(.*)$")
    if (-not $match.Success) { return '' }
    return $match.Groups[1].Value.Trim()
}

function Test-EnvironmentSecretsValid([string]$Content) {
    foreach ($name in $requiredSecretNames) {
        $value = Get-EnvironmentValueFromContent $Content $name
        if ($value.Length -lt 32 -or $value -eq '__GENERATE__') { return $false }
    }
    return $true
}

function Get-EnvironmentBackupContent {
    if (-not (Test-Path -LiteralPath $environmentBackup)) { return $null }
    try {
        $encrypted = [IO.File]::ReadAllBytes($environmentBackup)
        $entropy = [Text.Encoding]::UTF8.GetBytes('SilverPilot-env-v1')
        $plain = [System.Security.Cryptography.ProtectedData]::Unprotect(
            $encrypted,
            $entropy,
            [System.Security.Cryptography.DataProtectionScope]::CurrentUser
        )
        return [Text.Encoding]::UTF8.GetString($plain)
    } catch {
        throw "The encrypted environment backup cannot be decrypted by the current Windows user: $environmentBackup"
    }
}

function Save-EnvironmentBackup([string]$Content) {
    $existing = $null
    try {
        $existing = Get-EnvironmentBackupContent
    } catch {
        Write-Warning 'The previous DPAPI environment backup was unreadable and will be replaced from the valid .env.docker file.'
    }
    if ($null -ne $existing -and $existing -ceq $Content) { return }
    $entropy = [Text.Encoding]::UTF8.GetBytes('SilverPilot-env-v1')
    $encrypted = [System.Security.Cryptography.ProtectedData]::Protect(
        [Text.Encoding]::UTF8.GetBytes($Content),
        $entropy,
        [System.Security.Cryptography.DataProtectionScope]::CurrentUser
    )
    [IO.File]::WriteAllBytes($environmentBackup, $encrypted)
    Write-Host '[PASS] Refreshed the Git-ignored DPAPI backup of local startup settings.' -ForegroundColor Green
}

function Test-ProjectDataVolumesExist([string]$Content) {
    $projectName = Get-EnvironmentValueFromContent $Content 'COMPOSE_PROJECT_NAME'
    if ([string]::IsNullOrWhiteSpace($projectName)) { $projectName = 'silverpilot' }
    foreach ($volumeName in @(
        "${projectName}_silverpilot-mysql-data",
        "${projectName}_silverpilot-redis-data"
    )) {
        $inspect = Invoke-DockerCapture @('volume', 'inspect', $volumeName)
        if ($inspect.ExitCode -eq 0) { return $true }
        $details = $inspect.Output -join ' '
        if ($details -notmatch '(?i)(?:no such volume|not found)') {
            throw "Unable to inspect Docker volume '$volumeName' ($(Format-DockerFailure $inspect))."
        }
    }
    return $false
}

function Ensure-EnvironmentFile {
    if (-not (Test-Path -LiteralPath $environmentTemplate)) {
        throw "Docker environment template is missing: $environmentTemplate"
    }

    $templateContent = Get-Content -LiteralPath $environmentTemplate -Raw
    $content = if (Test-Path -LiteralPath $environmentFile) {
        Get-Content -LiteralPath $environmentFile -Raw
    } else {
        $null
    }

    if ($null -eq $content -or -not (Test-EnvironmentSecretsValid $content)) {
        $backupContent = Get-EnvironmentBackupContent
        if ($null -ne $backupContent -and (Test-EnvironmentSecretsValid $backupContent)) {
            $content = $backupContent
            [IO.File]::WriteAllText($environmentFile, $content, [Text.UTF8Encoding]::new($false))
            Write-Host '[PASS] Restored .env.docker from its current-user encrypted backup.' -ForegroundColor Green
        } else {
            $volumeProbeContent = if ($null -eq $content) { $templateContent } else { $content }
            if (Test-ProjectDataVolumesExist $volumeProbeContent) {
                throw 'Local startup secrets are missing or invalid while persistent data volumes already exist. Automatic secret rotation was refused. Restore .env.docker or its DPAPI backup before starting.'
            }
            $content = if ($null -eq $content) { $templateContent } else { $content }
            foreach ($name in $requiredSecretNames) {
                $content = Set-EnvironmentLine $content $name (New-LocalSecret)
            }
            $providerVariables = @{
                SILVERPILOT_DEEPSEEK_API_KEY = 'DEEPSEEK_API_KEY'
                SILVERPILOT_DEEPSEEK_MODEL = 'DEEPSEEK_MODEL'
                SILVERPILOT_DEEPSEEK_THINKING_ENABLED = 'DEEPSEEK_THINKING_ENABLED'
                SILVERPILOT_DOUBAO_API_KEY = 'DOUBAO_API_KEY'
                SILVERPILOT_DOUBAO_MODEL = 'DOUBAO_MODEL'
            }
            foreach ($name in $providerVariables.Keys) {
                $value = [Environment]::GetEnvironmentVariable($providerVariables[$name], 'User')
                if (-not [string]::IsNullOrWhiteSpace($value)) {
                    $content = Set-EnvironmentLine $content $name $value.Trim()
                }
            }
            [IO.File]::WriteAllText($environmentFile, $content, [Text.UTF8Encoding]::new($false))
            Write-Host '[INFO] Generated isolated .env.docker with local-only random secrets.' -ForegroundColor Cyan
        }
    }

    if (-not (Test-EnvironmentSecretsValid $content)) {
        throw '.env.docker still contains missing, short, or placeholder startup secrets.'
    }
    Save-EnvironmentBackup $content
}

function Read-EnvironmentFile {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $parts = $line.Split('=', 2)
        $values[$parts[0].Trim()] = $parts[1]
    }
    return $values
}

function Get-EnvironmentValueOrDefault([hashtable]$Values, [string]$Name, [string]$DefaultValue) {
    $value = $Values[$Name]
    if ([string]::IsNullOrWhiteSpace($value)) { return $DefaultValue }
    return $value.Trim()
}

function Get-EnvironmentBoolean([hashtable]$Values, [string]$Name, [string]$DefaultValue) {
    $value = Get-EnvironmentValueOrDefault $Values $Name $DefaultValue
    if ($value -notmatch '^(?i:true|false)$') { throw "$Name must be true or false." }
    return $value.ToLowerInvariant()
}

function Get-ScopedEnvironmentValue([string]$Name) {
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $value = [Environment]::GetEnvironmentVariable($Name, $scope)
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value.Trim() }
    }
    return ''
}

function ConvertTo-JavaPropertyValue([string]$Value) {
    if ($null -eq $Value) { return '' }
    if ($Value.Contains('${')) {
        throw 'A local configuration value contains the Spring placeholder sequence ${ and cannot be written safely.'
    }
    return $Value.Replace('\', '\\').Replace("`r", '\r').Replace("`n", '\n')
}

function Write-IdeaHostProfile {
    $values = Read-EnvironmentFile
    $required = @(
        'SILVERPILOT_REDIS_PASSWORD', 'SILVERPILOT_REDIS_PORT', 'SILVERPILOT_JWT_SECRET',
        'SILVERPILOT_MCP_API_KEY'
    )
    foreach ($name in $required) {
        if ([string]::IsNullOrWhiteSpace($values[$name])) { throw "$name is missing in .env.docker" }
    }
    if (-not (Test-Path -LiteralPath $localConfigDirectory)) {
        New-Item -ItemType Directory -Path $localConfigDirectory -Force | Out-Null
    }
    $mockEnabled = Get-EnvironmentBoolean $values 'SILVERPILOT_AI_MOCK_ENABLED' 'false'
    $deepseekKey = Get-EnvironmentValueOrDefault $values 'SILVERPILOT_DEEPSEEK_API_KEY' ''
    $deepseekModel = Get-EnvironmentValueOrDefault $values 'SILVERPILOT_DEEPSEEK_MODEL' 'deepseek-v4-flash'
    $deepseekThinking = Get-EnvironmentBoolean $values 'SILVERPILOT_DEEPSEEK_THINKING_ENABLED' 'false'
    $doubaoKey = Get-EnvironmentValueOrDefault $values 'SILVERPILOT_DOUBAO_API_KEY' ''
    $doubaoModel = Get-EnvironmentValueOrDefault $values 'SILVERPILOT_DOUBAO_MODEL' ''
    $hostDatabasePassword = Get-ScopedEnvironmentValue 'CECSMS_LOCAL_DB_PASSWORD'
    if ([string]::IsNullOrWhiteSpace($hostDatabasePassword)) {
        $hostDatabasePassword = Get-ScopedEnvironmentValue 'CECSMS_DB_PASSWORD'
    }
    if ([string]::IsNullOrWhiteSpace($hostDatabasePassword)) {
        throw 'IDEA local mode requires CECSMS_LOCAL_DB_PASSWORD or CECSMS_DB_PASSWORD in the process, user, or machine environment.'
    }
    $hostDatabaseUrl = Get-ScopedEnvironmentValue 'CECSMS_LOCAL_DB_URL'
    if ([string]::IsNullOrWhiteSpace($hostDatabaseUrl)) {
        $hostDatabaseUrl = 'jdbc:mysql://127.0.0.1:3306/a_old?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&sslMode=DISABLED&allowPublicKeyRetrieval=true'
    }
    if ($hostDatabaseUrl -notmatch '^jdbc:mysql://(?:127\.0\.0\.1|localhost|\[::1\]):3306/') {
        throw 'CECSMS_LOCAL_DB_URL must use the host MySQL listener on loopback port 3306 in Local mode.'
    }
    $hostDatabaseUsername = Get-ScopedEnvironmentValue 'CECSMS_LOCAL_DB_USERNAME'
    if ([string]::IsNullOrWhiteSpace($hostDatabaseUsername)) { $hostDatabaseUsername = 'root' }

    $properties = @(
        '# Generated by scripts/docker-dev.ps1. External host config; never commit this file.'
        "spring.datasource.url=$(ConvertTo-JavaPropertyValue $hostDatabaseUrl)"
        "spring.datasource.username=$(ConvertTo-JavaPropertyValue $hostDatabaseUsername)"
        "spring.datasource.password=$(ConvertTo-JavaPropertyValue $hostDatabasePassword)"
        'agent.redis.enabled=true'
        'management.health.redis.enabled=true'
        'spring.data.redis.host=127.0.0.1'
        "spring.data.redis.port=$(ConvertTo-JavaPropertyValue ([string]$values.SILVERPILOT_REDIS_PORT))"
        "spring.data.redis.password=$(ConvertTo-JavaPropertyValue ([string]$values.SILVERPILOT_REDIS_PASSWORD))"
        "app.jwt.secret=$(ConvertTo-JavaPropertyValue ([string]$values.SILVERPILOT_JWT_SECRET))"
        "agent.mcp.api-key=$(ConvertTo-JavaPropertyValue ([string]$values.SILVERPILOT_MCP_API_KEY))"
        "agent.ai.mock-enabled=$mockEnabled"
        "deepseek.api.key=$(ConvertTo-JavaPropertyValue $deepseekKey)"
        "deepseek.api.model=$(ConvertTo-JavaPropertyValue $deepseekModel)"
        "deepseek.api.thinking-enabled=$deepseekThinking"
        "doubao.api.key=$(ConvertTo-JavaPropertyValue $doubaoKey)"
        "doubao.api.model=$(ConvertTo-JavaPropertyValue $doubaoModel)"
        # Do not inherit CECSMS_UPLOAD_DIR from another checkout. IDEA can use a
        # different working directory, so pin the writable media root explicitly.
        "file.image-base-path=$(ConvertTo-JavaPropertyValue $projectRoot)"
    ) -join [Environment]::NewLine
    [IO.File]::WriteAllText($hostProperties, $properties + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}

function Assert-NoEmbeddedMachineProfile {
    foreach ($legacyProperties in $legacyProfileProperties) {
        if (Test-Path -LiteralPath $legacyProperties) {
            throw "Unsafe machine-specific profile exists inside src/main/resources and could enter a JAR: $legacyProperties. Keep local values only in $hostProperties."
        }
    }
}

function Test-DockerDaemon {
    $probe = Invoke-DockerCapture @('info', '--format', '{{.ServerVersion}}')
    return $probe.ExitCode -eq 0
}

function Ensure-DockerReady {
    if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI is not available in PATH. Install or repair Docker Desktop first.'
    }
    $composeProbe = Invoke-DockerCapture @('compose', 'version')
    if ($composeProbe.ExitCode -ne 0) {
        throw "Docker Compose is unavailable ($(Format-DockerFailure $composeProbe)). Install the Docker Compose plugin before starting SilverPilot."
    }
    if (Test-DockerDaemon) {
        Write-Host '[PASS] Docker Engine is ready.' -ForegroundColor Green
        return
    }

    $desktopCandidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ProgramFiles)) {
        $desktopCandidates += Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    }
    if (-not [string]::IsNullOrWhiteSpace(${env:ProgramFiles(x86)})) {
        $desktopCandidates += Join-Path ${env:ProgramFiles(x86)} 'Docker\Docker\Docker Desktop.exe'
    }
    $desktopCandidates = @($desktopCandidates | Where-Object { Test-Path -LiteralPath $_ })
    $desktopExecutable = $desktopCandidates | Select-Object -First 1
    if ($null -eq $desktopExecutable) {
        throw 'Docker Engine is not responding and Docker Desktop could not be found in its standard installation directories.'
    }

    Write-Host '[INFO] Docker Engine is stopped; starting Docker Desktop and waiting for readiness...' -ForegroundColor Cyan
    Start-Process -FilePath $desktopExecutable -WindowStyle Hidden | Out-Null
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Seconds 2
        if (Test-DockerDaemon) {
            Write-Host '[PASS] Docker Desktop started and Docker Engine is ready.' -ForegroundColor Green
            return
        }
    }
    throw 'Docker Desktop did not become ready within 120 seconds. Open Docker Desktop once to inspect its engine or WSL error.'
}

function Assert-ProjectPreflight {
    $requiredPaths = @(
        'compose.yaml',
        'database\Dockerfile',
        'database\a_old.sql',
        'image',
        'redis\Dockerfile',
        'knowledge-base\ima-ready',
        'SourceCode\cecsmsServe-springboot\Dockerfile',
        'SourceCode\cecsmsServe-springboot\pom.xml',
        'SourceCode\cecsmsui-vue\Dockerfile',
        'SourceCode\cecsmsui-vue\package-lock.json',
        'scripts\verify-running-stack.ps1'
    )
    foreach ($relativePath in $requiredPaths) {
        $absolutePath = Join-Path $projectRoot $relativePath
        if (-not (Test-Path -LiteralPath $absolutePath)) {
            throw "Required startup path is missing: $absolutePath"
        }
    }

    # Optional upload categories can start empty. Seed images are required and
    # are checked above; never replace a missing image directory with an empty one.
    foreach ($relativePath in @('file', 'video')) {
        $absolutePath = Join-Path $projectRoot $relativePath
        if (Test-Path -LiteralPath $absolutePath) {
            if (-not (Test-Path -LiteralPath $absolutePath -PathType Container)) {
                throw "Runtime media path must be a directory: $absolutePath"
            }
            continue
        }
        [void][IO.Directory]::CreateDirectory($absolutePath)
        Write-Host "[INFO] Created missing Git-ignored runtime media directory: $relativePath" -ForegroundColor Cyan
    }

    & $seedImageVerificationScript
    if ($LASTEXITCODE -ne 0) {
        throw "Repository seed-image verification failed with exit code $LASTEXITCODE."
    }

    $driveName = [IO.Path]::GetPathRoot($projectRoot).Substring(0, 1)
    $drive = Get-PSDrive -Name $driveName
    $freeGiB = [math]::Round($drive.Free / 1GB, 2)
    if ($freeGiB -lt 1) { throw "Only $freeGiB GiB is free on drive $driveName. At least 1 GiB is required to start safely." }
    if ($freeGiB -lt 5) { Write-Warning "Only $freeGiB GiB is free on drive $driveName; Docker builds may require cleanup." }

    Assert-NoEmbeddedMachineProfile
    Invoke-Compose @('--profile', 'full', 'config', '--quiet')
    Write-Host "[PASS] Startup preflight passed; $freeGiB GiB is free on drive $driveName." -ForegroundColor Green
}

function Invoke-Compose([string[]]$Arguments) {
    & docker compose --project-directory $projectRoot --file $composeFile --env-file $environmentFile @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed with exit code $LASTEXITCODE" }
}

function Get-FileManifestFingerprint([object[]]$Files) {
    # Sort with an explicit comparer so Windows PowerShell 5.1 and PowerShell 7
    # produce the same fingerprint regardless of culture/collation differences.
    $uniquePaths = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($file in $Files) {
        [void]$uniquePaths.Add([System.IO.Path]::GetFullPath([string]$file.FullName))
    }
    [string[]]$sortedPaths = @($uniquePaths)
    [Array]::Sort($sortedPaths, [StringComparer]::OrdinalIgnoreCase)

    $manifest = @($sortedPaths | ForEach-Object {
        $relativePath = $_.Substring($projectRoot.Length).TrimStart('\').Replace('\', '/')
        $fileHash = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()
        "$relativePath`t$fileHash"
    }) -join "`n"
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($manifest))
    } finally {
        $sha256.Dispose()
    }
    return ([BitConverter]::ToString($digest)).Replace('-', '').ToLowerInvariant()
}

function Get-BuildFingerprint {
    $backendContext = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
    $frontendContext = Join-Path $projectRoot 'SourceCode\cecsmsui-vue'
    $files = @()
    $files += Get-ChildItem -LiteralPath $backendContext -Force -File | Where-Object {
        $_.Name -notmatch '^\.env(\.|$)' -and $_.Name -ne 'README.md' -and $_.Name -notlike '*.log'
    }
    Get-ChildItem -LiteralPath $backendContext -Force -Directory | Where-Object {
        $_.Name -notin @('target', 'logs', '.idea', '.run', 'config')
    } | ForEach-Object {
        $files += Get-ChildItem -LiteralPath $_.FullName -Recurse -Force -File | Where-Object {
            $_.Name -notmatch '^\.env(\.|$)' -and $_.Name -notlike '*.log'
        }
    }
    $files += Get-ChildItem -LiteralPath $frontendContext -Force -File | Where-Object {
        $_.Name -notmatch '^\.env(\.|$)' -and $_.Name -notlike '*.log'
    }
    Get-ChildItem -LiteralPath $frontendContext -Force -Directory | Where-Object {
        $_.Name -notin @('node_modules', 'dist', 'coverage', '.vscode', '.idea')
    } | ForEach-Object {
        $files += Get-ChildItem -LiteralPath $_.FullName -Recurse -Force -File | Where-Object {
            $_.Name -notmatch '^\.env(\.|$)' -and $_.Name -notlike '*.log'
        }
    }
    $files += Get-Item -LiteralPath (Join-Path $projectRoot 'database\Dockerfile')
    $files += Get-Item -LiteralPath (Join-Path $projectRoot 'redis\Dockerfile')
    $files += Get-Item -LiteralPath (Join-Path $projectRoot 'redis\docker-entrypoint.sh')
    $files += Get-Item -LiteralPath (Join-Path $projectRoot 'redis\.dockerignore')

    return Get-FileManifestFingerprint $files
}

function Get-RedisBuildFingerprint {
    $files = @(
        Get-Item -LiteralPath (Join-Path $projectRoot 'redis\Dockerfile')
        Get-Item -LiteralPath (Join-Path $projectRoot 'redis\docker-entrypoint.sh')
        Get-Item -LiteralPath (Join-Path $projectRoot 'redis\.dockerignore')
    )
    return Get-FileManifestFingerprint $files
}

function Test-RequiredImagesExist {
    $config = Invoke-DockerCapture @(
        'compose', '--project-directory', $projectRoot, '--file', $composeFile,
        '--env-file', $environmentFile, '--profile', 'full', 'config', '--images'
    )
    if ($config.ExitCode -ne 0) {
        throw "Unable to resolve required Compose images ($(Format-DockerFailure $config))."
    }
    $images = @($config.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
    if ($images.Count -eq 0) { return $false }
    foreach ($image in $images) {
        $inspect = Invoke-DockerCapture @('image', 'inspect', $image)
        if ($inspect.ExitCode -eq 0) { continue }
        if (($inspect.Output -join ' ') -match '(?i)(?:no such image|not found)') { return $false }
        throw "Unable to inspect Docker image '$image' ($(Format-DockerFailure $inspect))."
    }
    return $true
}

function Test-BuildRequired([string]$Fingerprint) {
    if ($Rebuild -or -not (Test-RequiredImagesExist)) { return $true }
    if (-not (Test-Path -LiteralPath $buildStateFile)) { return $true }
    try {
        $state = Get-Content -LiteralPath $buildStateFile -Raw | ConvertFrom-Json
        return [int]$state.schemaVersion -ne 1 -or [string]$state.fingerprint -cne $Fingerprint
    } catch {
        return $true
    }
}

function Test-RedisBuildRequired([string]$Fingerprint) {
    if ($Rebuild) { return $true }
    $inspect = Invoke-DockerCapture @('image', 'inspect', 'silverpilot-redis:8.2.9')
    if ($inspect.ExitCode -ne 0) {
        if (($inspect.Output -join ' ') -notmatch '(?i)(?:no such image|not found)') {
            throw "Unable to inspect the Redis image ($(Format-DockerFailure $inspect))."
        }
        return $true
    }
    if (-not (Test-Path -LiteralPath $buildStateFile)) { return $true }
    try {
        $state = Get-Content -LiteralPath $buildStateFile -Raw | ConvertFrom-Json
        return [int]$state.schemaVersion -ne 1 -or [string]$state.redisFingerprint -cne $Fingerprint
    } catch {
        return $true
    }
}

function Save-BuildState([string]$Fingerprint) {
    $state = [ordered]@{
        schemaVersion = 1
        fingerprint = $Fingerprint
        redisFingerprint = Get-RedisBuildFingerprint
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
        verifiedRedisAtUtc = [DateTime]::UtcNow.ToString('o')
    } | ConvertTo-Json
    [IO.File]::WriteAllText($buildStateFile, $state + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}

function Save-RedisBuildState([string]$Fingerprint) {
    $fullFingerprint = ''
    $fullVerifiedAt = $null
    if (Test-Path -LiteralPath $buildStateFile) {
        try {
            $previous = Get-Content -LiteralPath $buildStateFile -Raw | ConvertFrom-Json
            if ([int]$previous.schemaVersion -eq 1) {
                $fullFingerprint = [string]$previous.fingerprint
                $fullVerifiedAt = $previous.verifiedAtUtc
            }
        } catch { }
    }
    $state = [ordered]@{
        schemaVersion = 1
        fingerprint = $fullFingerprint
        redisFingerprint = $Fingerprint
        verifiedAtUtc = $fullVerifiedAt
        verifiedRedisAtUtc = [DateTime]::UtcNow.ToString('o')
    } | ConvertTo-Json
    [IO.File]::WriteAllText($buildStateFile, $state + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}

function Write-FullStackDiagnostics {
    Write-Host '[INFO] Docker service state:' -ForegroundColor Cyan
    & docker compose --project-directory $projectRoot --file $composeFile --env-file $environmentFile --profile full ps
    Write-Host '[INFO] Last 120 log lines:' -ForegroundColor Cyan
    & docker compose --project-directory $projectRoot --file $composeFile --env-file $environmentFile --profile full logs --tail 120 --no-color
}

function Invoke-RunningStackVerification {
    & $verificationScript -EnvironmentFile $environmentFile -TimeoutSeconds ([math]::Min($WaitTimeoutSeconds, 300)) -FullAudit:$FullAudit
    if ($LASTEXITCODE -ne 0) { throw "Runtime verification failed with exit code $LASTEXITCODE." }
}

function Get-ConfiguredPort([hashtable]$Values, [string]$Name, [int]$DefaultValue) {
    $rawValue = $Values[$Name]
    if ([string]::IsNullOrWhiteSpace($rawValue)) { return $DefaultValue }
    $parsed = 0
    if (-not [int]::TryParse($rawValue, [ref]$parsed) -or $parsed -lt 1 -or $parsed -gt 65535) {
        throw "$Name must be an integer from 1 to 65535."
    }
    return $parsed
}

function Assert-ConfiguredPortsUnique([hashtable]$Values) {
    $configuredPorts = [ordered]@{
        SILVERPILOT_MYSQL_PORT = Get-ConfiguredPort $Values 'SILVERPILOT_MYSQL_PORT' 3307
        SILVERPILOT_REDIS_PORT = Get-ConfiguredPort $Values 'SILVERPILOT_REDIS_PORT' 6380
        SILVERPILOT_BACKEND_PORT = Get-ConfiguredPort $Values 'SILVERPILOT_BACKEND_PORT' 8083
        SILVERPILOT_FRONTEND_PORT = Get-ConfiguredPort $Values 'SILVERPILOT_FRONTEND_PORT' 8082
    }
    $duplicates = @($configuredPorts.GetEnumerator() | Group-Object Value | Where-Object Count -gt 1)
    if ($duplicates.Count -gt 0) {
        $details = @($duplicates | ForEach-Object { "port $($_.Name): $($_.Group.Name -join ', ')" }) -join '; '
        throw "Configured host ports must be unique: $details"
    }
}

function Get-PortListener([int]$Port) {
    return Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
}

function Get-ExcludedTcpPortRange([int]$Port) {
    if (-not $script:excludedTcpPortRangesLoaded) {
        $script:excludedTcpPortRangesLoaded = $true
        if ($null -ne (Get-Command netsh.exe -ErrorAction SilentlyContinue)) {
            $previousPreference = $ErrorActionPreference
            try {
                $ErrorActionPreference = 'Continue'
                $output = @(& netsh.exe interface ipv4 show excludedportrange protocol=tcp 2>&1)
                $exitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $previousPreference
            }
            if ($exitCode -eq 0) {
                foreach ($line in $output) {
                    if ([string]$line -match '^\s*(\d+)\s+(\d+)(?:\s+\*)?\s*$') {
                        $script:excludedTcpPortRanges += [pscustomobject]@{
                            Start = [int]$Matches[1]
                            End = [int]$Matches[2]
                        }
                    }
                }
            } else {
                Write-Warning 'Windows TCP excluded-port ranges could not be read; Docker will perform the final bind check.'
            }
        }
    }
    return $script:excludedTcpPortRanges |
        Where-Object { $Port -ge $_.Start -and $Port -le $_.End } |
        Select-Object -First 1
}

function Get-ListenerDescription([int]$Port) {
    $listener = Get-PortListener $Port
    if ($null -eq $listener) { return 'free' }
    $process = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
    $processName = if ($null -eq $process) { 'unknown process' } else { $process.ProcessName }
    return "$processName (PID $($listener.OwningProcess))"
}

function Get-RunningComposeService([string]$Service) {
    if ($null -ne $script:runningComposeServices) {
        return $script:runningComposeServices[$Service]
    }
    $compose = Invoke-DockerCapture @(
        'compose', '--project-directory', $projectRoot, '--file', $composeFile,
        '--env-file', $environmentFile, '--profile', 'full', 'ps',
        '--status', 'running', '--format', 'json'
    )
    if ($compose.ExitCode -ne 0) { return $null }
    $jsonLines = @($compose.Output)
    $script:runningComposeServices = @{}
    foreach ($jsonLine in $jsonLines) {
        if ([string]::IsNullOrWhiteSpace($jsonLine)) { continue }
        $row = $jsonLine | ConvertFrom-Json
        $script:runningComposeServices[$row.Service] = $row
    }
    return $script:runningComposeServices[$Service]
}

function Test-ComposeOwnsPort([string]$Service, [int]$ContainerPort, [int]$HostPort) {
    $serviceRow = Get-RunningComposeService $Service
    if ($null -eq $serviceRow) { return $false }
    foreach ($publisher in @($serviceRow.Publishers)) {
        if ([int]$publisher.TargetPort -eq $ContainerPort -and [int]$publisher.PublishedPort -eq $HostPort) {
            return $true
        }
    }
    return $false
}

function Assert-PortAvailableForCompose([string]$Service, [int]$ContainerPort, [int]$HostPort) {
    $listener = Get-PortListener $HostPort
    if ($null -ne $listener) {
        if (Test-ComposeOwnsPort $Service $ContainerPort $HostPort) { return }
        $description = Get-ListenerDescription $HostPort
        throw "Cannot start Docker service '$Service': host port $HostPort is occupied by $description. Stop that process or choose a different SILVERPILOT port."
    }
    $excludedRange = Get-ExcludedTcpPortRange $HostPort
    if ($null -ne $excludedRange) {
        throw "Cannot start Docker service '$Service': host port $HostPort is reserved by the Windows TCP exclusion range $($excludedRange.Start)-$($excludedRange.End). Choose a different SILVERPILOT port in .env.docker."
    }
}

function Start-RedisForHostMode {
    foreach ($relativePath in @(
        'compose.yaml',
        'redis\Dockerfile',
        'redis\docker-entrypoint.sh',
        'redis\.dockerignore',
        'scripts\verify-docker-redis.ps1'
    )) {
        $absolutePath = Join-Path $projectRoot $relativePath
        if (-not (Test-Path -LiteralPath $absolutePath)) {
            throw "Required Redis startup path is missing: $absolutePath"
        }
    }

    & $seedImageVerificationScript
    if ($LASTEXITCODE -ne 0) {
        throw "Repository seed-image verification failed with exit code $LASTEXITCODE."
    }
    Assert-NoEmbeddedMachineProfile
    Write-IdeaHostProfile
    Invoke-Compose @('--profile', 'full', 'config', '--quiet')
    $values = Read-EnvironmentFile
    $redisPort = Get-ConfiguredPort $values 'SILVERPILOT_REDIS_PORT' 6380
    Assert-PortAvailableForCompose 'redis' 6379 $redisPort
    $redisFingerprint = Get-RedisBuildFingerprint
    $redisBuildRequired = Test-RedisBuildRequired $redisFingerprint

    # This action is the infrastructure half of the Local run mode. Remove only
    # this Compose project's non-Redis containers and retain every named volume.
    Invoke-Compose @('--profile', 'full', 'rm', '--stop', '--force', 'frontend', 'backend', 'mysql')
    $redisWaitSeconds = [string]([math]::Min($WaitTimeoutSeconds, 300))
    $lastFailure = ''
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        try {
            $startArguments = @('up', '-d')
            if ($redisBuildRequired -or $attempt -gt 1) {
                $startArguments += '--build'
                Write-Host '[INFO] Redis image is missing, changed, explicitly requested, or recovering from a failed start; building it now.' -ForegroundColor Cyan
            } else {
                $startArguments += '--no-build'
                Write-Host '[PASS] Verified the Redis image matches its current build inputs; starting without a registry/build dependency.' -ForegroundColor Green
            }
            if ($attempt -gt 1) { $startArguments += '--force-recreate' }
            $startArguments += @('--wait', '--wait-timeout', $redisWaitSeconds, 'redis')
            Invoke-Compose $startArguments

            & $redisVerificationScript -EnvironmentFile $environmentFile -RequireOnlyRedis
            if ($LASTEXITCODE -ne 0) {
                throw "Docker Redis verification failed with exit code $LASTEXITCODE."
            }
            Save-RedisBuildState $redisFingerprint
            Write-Host "[PASS] Local-mode infrastructure is ready: only Redis 8.2.9 Extended runs in Docker on 127.0.0.1:$redisPort." -ForegroundColor Green
            return
        } catch {
            $lastFailure = $_.Exception.Message
            Write-Warning "Docker Redis startup attempt $attempt failed: $lastFailure"
            try {
                & docker compose --project-directory $projectRoot --file $composeFile --env-file $environmentFile ps redis
                & docker compose --project-directory $projectRoot --file $composeFile --env-file $environmentFile logs --tail 80 --no-color redis
            } catch {
                Write-Warning "Redis diagnostics could not be collected: $($_.Exception.Message)"
            }
            if ($attempt -lt 2) {
                Write-Host '[INFO] Removing only the failed Redis container and retrying with the existing named volume.' -ForegroundColor Cyan
                Invoke-Compose @('rm', '--stop', '--force', 'redis')
                $script:runningComposeServices = $null
                Start-Sleep -Seconds 1
            }
        }
    }
    throw "Docker Redis did not recover after two bounded attempts. Named data volumes were preserved. Last failure: $lastFailure"
}

. (Join-Path $PSScriptRoot 'project-lifecycle.ps1')
$lifecycleLock = Enter-ProjectLifecycleLock $projectRoot
try {
Ensure-DockerReady
if ($Action -in @('up', 'dev', 'redis', 'full', 'restart')) {
    $versionMode = if ($Action -in @('full', 'restart')) { 'Docker' } else { 'Local' }
    & $versionVerificationScript -Mode $versionMode -SkipLocalHostChecks:($versionMode -eq 'Local' -and -not $VerifyLocalHost) -RuntimeOnly
    if ($LASTEXITCODE -ne 0) {
        throw "$versionMode runtime compatibility policy returned exit code $LASTEXITCODE. No service was started."
    }
}
Ensure-EnvironmentFile

switch ($Action) {
    'up' { Start-RedisForHostMode }
    'dev' { Start-RedisForHostMode }
    'redis' { Start-RedisForHostMode }
    'full' {
        Assert-ProjectPreflight
        $values = Read-EnvironmentFile
        Assert-ConfiguredPortsUnique $values
        Assert-PortAvailableForCompose 'mysql' 3306 (Get-ConfiguredPort $values 'SILVERPILOT_MYSQL_PORT' 3307)
        Assert-PortAvailableForCompose 'redis' 6379 (Get-ConfiguredPort $values 'SILVERPILOT_REDIS_PORT' 6380)
        Assert-PortAvailableForCompose 'backend' 8083 (Get-ConfiguredPort $values 'SILVERPILOT_BACKEND_PORT' 8083)
        Assert-PortAvailableForCompose 'frontend' 8080 (Get-ConfiguredPort $values 'SILVERPILOT_FRONTEND_PORT' 8082)
        $buildFingerprint = Get-BuildFingerprint
        $buildRequired = Test-BuildRequired $buildFingerprint
        $startArguments = @('--profile', 'full', 'up', '-d')
        if ($buildRequired) {
            $startArguments += '--build'
            Write-Host '[INFO] Docker build inputs changed or an image is missing; rebuilding before startup.' -ForegroundColor Cyan
        } else {
            $startArguments += '--no-build'
            Write-Host '[PASS] Verified Docker images match the current build inputs; starting without a registry/build dependency.' -ForegroundColor Green
        }
        $startArguments += @('--wait', '--wait-timeout', [string]$WaitTimeoutSeconds)
        try {
            Invoke-Compose $startArguments
        } catch {
            Write-Warning 'The first full-stack start did not reach readiness. Capturing diagnostics and retrying once with safe container recreation; named volumes are retained.'
            Write-FullStackDiagnostics
            $retryArguments = @('--profile', 'full', 'up', '-d')
            $retryArguments += if ($buildRequired) { '--build' } else { '--no-build' }
            $retryArguments += @('--force-recreate', '--wait', '--wait-timeout', [string]$WaitTimeoutSeconds)
            try {
                Invoke-Compose $retryArguments
            } catch {
                Write-FullStackDiagnostics
                throw
            }
        }
        Invoke-Compose @('--profile', 'full', 'ps')
        try {
            Invoke-RunningStackVerification
        } catch {
            Write-FullStackDiagnostics
            throw
        }
        Save-BuildState $buildFingerprint
        $frontendPort = Get-ConfiguredPort $values 'SILVERPILOT_FRONTEND_PORT' 8082
        Write-Host "[PASS] Full stack started and passed runtime verification. Open http://127.0.0.1:$frontendPort/login" -ForegroundColor Green
    }
    'restart' {
        Assert-ProjectPreflight
        Invoke-Compose @('--profile', 'full', 'up', '-d', '--force-recreate', '--wait', '--wait-timeout', [string]$WaitTimeoutSeconds)
        Invoke-RunningStackVerification
        Write-Host '[PASS] Full stack was recreated in dependency order and passed runtime verification; named volumes were retained.' -ForegroundColor Green
    }
    'down' {
        Invoke-Compose @('--profile', 'full', 'down', '--remove-orphans')
        Write-Host '[PASS] SilverPilot containers stopped; named data volumes were retained.' -ForegroundColor Green
    }
    'status' { Invoke-Compose @('--profile', 'full', 'ps') }
    'logs' { Invoke-Compose @('--profile', 'full', 'logs', '--tail', '200') }
    'config' {
        Write-IdeaHostProfile
        Assert-NoEmbeddedMachineProfile
        Invoke-Compose @('--profile', 'full', 'config', '--quiet')
        Write-Host "[PASS] Compose configuration and ignored external IDEA host config are valid: $hostProperties" -ForegroundColor Green
    }
    'doctor' {
        Write-IdeaHostProfile
        Assert-ProjectPreflight
        Write-Host "[INFO] PowerShell $($PSVersionTable.PSVersion); $(docker compose version)" -ForegroundColor Cyan
        Invoke-Compose @('--profile', 'full', 'ps')
        Write-Host "[PASS] Startup configuration passed structural checks; local secrets remain outside src/main/resources and have a current-user encrypted backup." -ForegroundColor Green
    }
    'rotate-mcp' {
        $content = Get-Content -LiteralPath $environmentFile -Raw
        $content = Set-EnvironmentLine $content 'SILVERPILOT_MCP_API_KEY' (New-LocalSecret)
        [IO.File]::WriteAllText($environmentFile, $content, [Text.UTF8Encoding]::new($false))
        Save-EnvironmentBackup $content
        Write-IdeaHostProfile
        Write-Host '[PASS] Rotated the local MCP API key and refreshed its DPAPI backup and ignored host configuration.' -ForegroundColor Green
    }
    'reset' {
        if (-not $Force) {
            throw 'reset deletes the SilverPilot MySQL/Redis/upload volumes. Re-run with -Force only when this is intended.'
        }
        Invoke-Compose @('--profile', 'full', 'down', '--volumes', '--remove-orphans')
        Write-Host '[WARN] SilverPilot containers and named volumes were removed. This cannot be undone.' -ForegroundColor Yellow
    }
}
} finally {
    Exit-ProjectLifecycleLock $lifecycleLock
}
