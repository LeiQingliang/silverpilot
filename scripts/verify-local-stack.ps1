param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [ValidateRange(15, 300)]
    [int]$TimeoutSeconds = 90,
    [switch]$FullAudit
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$environmentFile = Join-Path $projectRoot '.env.docker'
$redisVerificationScript = Join-Path $PSScriptRoot 'verify-docker-redis.ps1'
$baseUrlValue = $BaseUrl.TrimEnd('/')
$backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
$frontendRoot = Join-Path $projectRoot 'SourceCode\cecsmsui-vue'
$backendIdentity = ([regex]::Replace($backendRoot, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
$frontendIdentity = ([regex]::Replace($frontendRoot, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
$backendMainClassIdentity = 'comcecsmsservececsmsserveapplication'

function Read-DockerEnvironmentValues {
    if (-not (Test-Path -LiteralPath $environmentFile)) {
        throw "Docker environment file is missing: $environmentFile"
    }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $parts = $line.Split('=', 2)
        $values[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $values
}

function Get-Listener([int]$Port) {
    return Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
}

function Get-ProcessRecord([int]$ProcessId) {
    return Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
}

function Assert-LocalTopology {
    $frontendListener = Get-Listener 8081
    $backendListener = Get-Listener 8083
    $mysqlListener = Get-Listener 3306
    if ($null -eq $frontendListener -or $null -eq $backendListener -or $null -eq $mysqlListener) {
        throw 'Local mode requires listeners on MySQL 3306, Vite 8081, and Java 8083.'
    }

    $frontendOwner = Get-ProcessRecord ([int]$frontendListener.OwningProcess)
    $backendOwner = Get-ProcessRecord ([int]$backendListener.OwningProcess)
    $mysqlOwner = Get-ProcessRecord ([int]$mysqlListener.OwningProcess)
    $frontendCommand = ([regex]::Replace([string]$frontendOwner.CommandLine, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
    $backendCommand = ([regex]::Replace([string]$backendOwner.CommandLine, '[^a-zA-Z0-9]', '')).ToLowerInvariant()
    $isProjectFrontend = $null -ne $frontendOwner -and $frontendOwner.Name -eq 'node.exe' -and
        $frontendCommand.Contains($frontendIdentity) -and $frontendCommand.Contains('vite')
    # IntelliJ and the Spring Boot Maven plugin can shorten a long Windows
    # classpath into a Java @argfile. In that mode the visible command line no
    # longer contains the repository path, but it still contains the exact
    # application main class. Requiring both values rejected a healthy IDEA
    # launch depending only on the IDE's "Shorten command line" setting.
    # The subsequent health, database, Redis, media and Agent probes verify
    # that the listener is the expected SilverPilot application.
    $isProjectBackend = $null -ne $backendOwner -and $backendOwner.Name -eq 'java.exe' -and
        $backendCommand.Contains($backendMainClassIdentity)
    if (-not $isProjectFrontend) {
        throw "Port 8081 is not owned by this repository's Vite process (PID $($frontendListener.OwningProcess))."
    }
    if (-not $isProjectBackend) {
        throw "Port 8083 is not owned by this repository's Java backend (PID $($backendListener.OwningProcess))."
    }
    if ($null -eq $mysqlOwner -or $mysqlOwner.Name -ne 'mysqld.exe') {
        $mysqlName = if ($null -eq $mysqlOwner) { 'unknown process' } else { [string]$mysqlOwner.Name }
        throw "Port 3306 is owned by $mysqlName (PID $($mysqlListener.OwningProcess)), not mysqld.exe."
    }
    foreach ($forbiddenPort in @(3307, 8082)) {
        $forbiddenListener = Get-Listener $forbiddenPort
        if ($null -ne $forbiddenListener) {
            $owner = Get-ProcessRecord ([int]$forbiddenListener.OwningProcess)
            $ownerName = if ($null -eq $owner) { 'unknown process' } else { [string]$owner.Name }
            throw "Local mode requires port $forbiddenPort to remain unused, but $ownerName (PID $($forbiddenListener.OwningProcess)) owns it."
        }
    }
    Write-Host '[PASS] Runtime ownership is exact: mysqld:3306, project Vite:8081, project Java:8083; Docker-only ports 3307/8082 are free.' -ForegroundColor Green
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-ProbeToken([string]$Secret, [int]$UserId) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = [ordered]@{ alg = 'HS256'; typ = 'JWT' } | ConvertTo-Json -Compress
    $payloadJson = [ordered]@{
        iss = 'cecsms-serve'
        sub = [string]$UserId
        aud = [string]$UserId
        iat = $now
        exp = $now + 300
    } | ConvertTo-Json -Compress
    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson))
    $unsigned = "$header.$payload"
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
    try {
        $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned)))
    } finally {
        $hmac.Dispose()
    }
    return "$unsigned.$signature"
}

function Get-ResponseText($Response) {
    if ($Response.Content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString($Response.Content)
    }
    return [string]$Response.Content
}

function Get-Sha256Hex([byte[]]$Bytes) {
    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($algorithm.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

function Wait-ForCheck([string]$Name, [scriptblock]$Check) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastError = $null
    do {
        try {
            & $Check
            Write-Host "[PASS] $Name" -ForegroundColor Green
            return
        } catch {
            $lastError = $_.Exception.Message
            Start-Sleep -Seconds 1
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Name failed after $TimeoutSeconds seconds. Last error: $lastError"
}

$dockerValues = Read-DockerEnvironmentValues
$jwtSecret = [string]$dockerValues.SILVERPILOT_JWT_SECRET
if ($jwtSecret.Length -lt 32) {
    throw 'Local verification requires SILVERPILOT_JWT_SECRET with at least 32 characters in .env.docker.'
}

& $redisVerificationScript -EnvironmentFile $environmentFile -RequireOnlyRedis
if ($LASTEXITCODE -ne 0) {
    throw "Local Docker Redis verification returned exit code $LASTEXITCODE."
}
Assert-LocalTopology

Wait-ForCheck 'Local backend health returned UP.' {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8083/actuator/health' -TimeoutSec 5
    if ($health.status -ne 'UP') { throw "Backend health is $($health.status)" }
}
Wait-ForCheck 'Local backend explicitly reported its required Docker Redis component as UP.' {
    $redisHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:8083/actuator/health/redis' -TimeoutSec 5
    if ($redisHealth.status -ne 'UP') { throw "Backend Redis health is $($redisHealth.status)" }
}
Wait-ForCheck 'Local Vite login page returned the Vue application shell.' {
    $response = Invoke-WebRequest -Uri "$baseUrlValue/login" -UseBasicParsing -TimeoutSec 5 -Headers @{ 'Cache-Control' = 'no-cache' }
    $content = Get-ResponseText $response
    if ($response.StatusCode -ne 200 -or $content -notmatch 'id=["'']app["'']') {
        throw "Login page is not a valid application shell: HTTP $($response.StatusCode)"
    }
}
Wait-ForCheck 'Local Vite-to-backend proxy returned UP.' {
    $health = Invoke-RestMethod -Uri "$baseUrlValue/api/actuator/health" -TimeoutSec 5
    if ($health.status -ne 'UP') { throw "Proxied backend health is $($health.status)" }
}

$headers = @{ Authorization = "Bearer $(New-ProbeToken $jwtSecret 1)" }
Wait-ForCheck 'Local backend queried the host MySQL database successfully.' {
    $identity = Invoke-RestMethod -Uri "$baseUrlValue/api/user/selectById/1" -Headers $headers -TimeoutSec 5
    if ([int]$identity.code -ne 200 -or [int]$identity.result.id -ne 1) {
        throw "Admin identity query returned application code $($identity.code)."
    }
}
Wait-ForCheck 'A database-backed real image is byte-identical through the Vite media proxy.' {
    $activityPage = Invoke-RestMethod -Uri "$baseUrlValue/api/activity/selectAllByPage/1/8" -Headers $headers -TimeoutSec 5
    if ([int]$activityPage.code -ne 200) {
        throw "Activity image query returned application code $($activityPage.code)."
    }
    # Activity pagination intentionally keeps the legacy frontend contract:
    # result is the row array and msg carries the total. Other paginated APIs
    # may use a records wrapper, so accept both shapes here instead of treating
    # a healthy legacy response as an empty page.
    $activityRows = if ($activityPage.result.PSObject.Properties.Name -contains 'records') {
        @($activityPage.result.records)
    } else {
        @($activityPage.result)
    }
    $activity = @($activityRows | Where-Object {
        [string]$_.image -match '^/image/.+\.(?:png|jpe?g)(?:[?#].*)?$' -and
        [string]$_.image -notmatch '^/image/demo/'
    } | Select-Object -First 1)
    if ($activity.Count -ne 1) {
        throw 'The active database returned no real raster activity image.'
    }

    $originalPath = [string]$activity[0].image
    $optimizedPath = [regex]::Replace($originalPath, '(?i)\.(?:png|jpe?g)(?=([?#]|$))', '.webp')
    $pathOnly = $optimizedPath.Split('?', 2)[0].Split('#', 2)[0]
    $relativePath = $pathOnly.TrimStart('/').Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $diskPath = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $relativePath))
    $projectPrefix = $projectRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $diskPath.StartsWith($projectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Database image path escaped the project root: $optimizedPath"
    }
    if (-not (Test-Path -LiteralPath $diskPath -PathType Leaf)) {
        throw "Database image has no optimized local file: $diskPath"
    }

    $client = [System.Net.Http.HttpClient]::new()
    try {
        $response = $client.GetAsync("$baseUrlValue$optimizedPath").GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Media proxy returned HTTP $([int]$response.StatusCode) for $optimizedPath."
        }
        $contentType = [string]$response.Content.Headers.ContentType.MediaType
        if ($contentType -ne 'image/webp') {
            throw "Media proxy returned '$contentType' instead of image/webp for $optimizedPath."
        }
        $httpBytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    } finally {
        $client.Dispose()
    }
    $diskBytes = [System.IO.File]::ReadAllBytes($diskPath)
    if ((Get-Sha256Hex $httpBytes) -ne (Get-Sha256Hex $diskBytes)) {
        throw "Media proxy bytes differ from the real repository image: $optimizedPath"
    }
}
Wait-ForCheck 'Local Agent status is readable.' {
    $status = Invoke-RestMethod -Uri "$baseUrlValue/api/chat/status" -Headers $headers -TimeoutSec 5
    if ([string]::IsNullOrWhiteSpace([string]$status.defaultProvider) -or $null -eq $status.providers) {
        throw 'Agent status response is incomplete.'
    }
}

if ($FullAudit) {
    if ($null -eq (Get-Command node -ErrorAction SilentlyContinue)) {
        throw 'Full local communication audit requires Node.js in PATH.'
    }
    $mcpApiKey = [string]$dockerValues.SILVERPILOT_MCP_API_KEY
    if ([string]::IsNullOrWhiteSpace($mcpApiKey)) {
        throw 'Full local communication audit requires SILVERPILOT_MCP_API_KEY in .env.docker.'
    }
    $previousBaseUrl = $env:CECSMS_QA_BASE_URL
    $previousJwtSecret = $env:CECSMS_QA_JWT_SECRET
    $previousMcpApiKey = $env:CECSMS_QA_MCP_API_KEY
    try {
        $env:CECSMS_QA_BASE_URL = $baseUrlValue
        $env:CECSMS_QA_JWT_SECRET = $jwtSecret
        $env:CECSMS_QA_MCP_API_KEY = $mcpApiKey
        & node (Join-Path $projectRoot 'scripts\audit-frontend-backend-communication.mjs')
        if ($LASTEXITCODE -ne 0) { throw "Full local communication audit failed with exit code $LASTEXITCODE." }
    } finally {
        $env:CECSMS_QA_BASE_URL = $previousBaseUrl
        $env:CECSMS_QA_JWT_SECRET = $previousJwtSecret
        $env:CECSMS_QA_MCP_API_KEY = $previousMcpApiKey
    }
}

Write-Host "[PASS] Hybrid local runtime verification completed: host MySQL/Java/Vite + required Docker Redis; $baseUrlValue/login" -ForegroundColor Green
exit 0
