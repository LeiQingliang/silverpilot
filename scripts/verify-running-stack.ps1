param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\.env.docker'),
    [ValidateRange(15, 300)]
    [int]$TimeoutSeconds = 90,
    [switch]$FullAudit
)

$ErrorActionPreference = 'Stop'
$resolvedEnvironmentFile = [System.IO.Path]::GetFullPath($EnvironmentFile)
$projectRoot = Split-Path -Parent $resolvedEnvironmentFile
$composeFile = Join-Path $projectRoot 'compose.yaml'

function Read-EnvironmentValues {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $resolvedEnvironmentFile) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $parts = $line.Split('=', 2)
        $values[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $values
}

function Get-PortValue([hashtable]$Values, [string]$Name, [int]$DefaultValue) {
    $rawValue = $Values[$Name]
    if ([string]::IsNullOrWhiteSpace($rawValue)) { return $DefaultValue }
    $parsed = 0
    if (-not [int]::TryParse($rawValue, [ref]$parsed) -or $parsed -lt 1 -or $parsed -gt 65535) {
        throw "$Name must be an integer from 1 to 65535."
    }
    return $parsed
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

function Get-ComposeRows {
    $jsonLines = @(& docker compose --project-directory $projectRoot --file $composeFile --env-file $resolvedEnvironmentFile --profile full ps --format json 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read Docker Compose service state.' }
    $rows = @()
    foreach ($line in $jsonLines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parsed = $line | ConvertFrom-Json
        $rows += @($parsed)
    }
    return $rows
}

function Wait-ForHealthyContainers {
    $expectedServices = @('mysql', 'redis', 'backend', 'frontend')
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $rows = @(Get-ComposeRows)
        $byService = @{}
        foreach ($row in $rows) { $byService[[string]$row.Service] = $row }
        $unhealthy = @($expectedServices | Where-Object {
            $row = $byService[$_]
            $null -eq $row -or [string]$row.State -ne 'running' -or [string]$row.Health -ne 'healthy'
        })
        if ($unhealthy.Count -eq 0) {
            Write-Host '[PASS] Docker services are running and healthy: mysql, redis, backend, frontend.' -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Docker services did not all become healthy within $TimeoutSeconds seconds: $($unhealthy -join ', ')"
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
            Start-Sleep -Seconds 2
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Name failed after $TimeoutSeconds seconds. Last error: $lastError"
}

function Get-ResponseText($Response) {
    if ($Response.Content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString($Response.Content)
    }
    return [string]$Response.Content
}

function Get-PersistedImagePaths {
    $sql = @'
SELECT `image` FROM `activity` WHERE `image` IS NOT NULL AND `image` <> ''
UNION
SELECT `avatar` FROM `news` WHERE `avatar` IS NOT NULL AND `avatar` <> ''
UNION
SELECT `image_url` FROM `recipe` WHERE `image_url` IS NOT NULL AND `image_url` <> ''
UNION
SELECT `image` FROM `service_type` WHERE `image` IS NOT NULL AND `image` <> ''
ORDER BY 1;
'@
    $rows = @($sql | & docker compose --project-directory $projectRoot --file $composeFile `
        --env-file $resolvedEnvironmentFile exec -T mysql sh -lc `
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -uroot --database="$MYSQL_DATABASE" --batch --skip-column-names')
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read persisted image paths from the running database (exit $LASTEXITCODE)."
    }
    return @($rows | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ } | Sort-Object -Unique)
}

function Get-ResponseContentType($Response) {
    try {
        return [string]($Response.Headers.GetValues('Content-Type') | Select-Object -First 1)
    } catch {
        return [string]$Response.Headers['Content-Type']
    }
}

if (-not (Test-Path -LiteralPath $resolvedEnvironmentFile)) {
    throw "Docker environment file is missing: $resolvedEnvironmentFile"
}
if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker CLI is not available in PATH.'
}

$values = Read-EnvironmentValues
$frontendPort = Get-PortValue $values 'SILVERPILOT_FRONTEND_PORT' 8082
$baseUrl = "http://127.0.0.1:$frontendPort"
$jwtSecret = [string]$values.SILVERPILOT_JWT_SECRET
if ($jwtSecret.Length -lt 32) {
    throw 'SILVERPILOT_JWT_SECRET must contain at least 32 characters.'
}

Wait-ForHealthyContainers
Wait-ForCheck 'Frontend health endpoint returned ok.' {
    $response = Invoke-WebRequest -Uri "$baseUrl/healthz" -UseBasicParsing -TimeoutSec 5
    $content = Get-ResponseText $response
    if ($response.StatusCode -ne 200 -or $content.Trim() -ne 'ok') {
        throw "Unexpected frontend health response: HTTP $($response.StatusCode)"
    }
}
Wait-ForCheck 'Login page returned the Vue application shell.' {
    $response = Invoke-WebRequest -Uri "$baseUrl/login" -UseBasicParsing -TimeoutSec 5 -Headers @{ 'Cache-Control' = 'no-cache' }
    $content = Get-ResponseText $response
    if ($response.StatusCode -ne 200 -or $content -notmatch 'id=["'']app["'']') {
        throw "Login page is not a valid application shell: HTTP $($response.StatusCode)"
    }
}
Wait-ForCheck 'Frontend-to-backend health proxy returned UP.' {
    $health = Invoke-RestMethod -Uri "$baseUrl/api/actuator/health" -TimeoutSec 5
    if ($health.status -ne 'UP') { throw "Backend health is $($health.status)" }
}
Wait-ForCheck 'Backend explicitly reported its required Docker Redis component as UP.' {
    $redisHealth = Invoke-RestMethod -Uri "$baseUrl/api/actuator/health/redis" -TimeoutSec 5
    if ($redisHealth.status -ne 'UP') { throw "Backend Redis health is $($redisHealth.status)" }
}

$adminToken = New-ProbeToken $jwtSecret 1
$headers = @{ Authorization = "Bearer $adminToken" }
Wait-ForCheck 'Authenticated database identity query succeeded.' {
    $identity = Invoke-RestMethod -Uri "$baseUrl/api/user/selectById/1" -Headers $headers -TimeoutSec 5
    if ([int]$identity.code -ne 200 -or [int]$identity.result.id -ne 1) {
        throw "Admin identity query returned application code $($identity.code)."
    }
}
Wait-ForCheck 'Agent runtime status and knowledge wiring are readable.' {
    $status = Invoke-RestMethod -Uri "$baseUrl/api/chat/status" -Headers $headers -TimeoutSec 5
    if ([string]::IsNullOrWhiteSpace([string]$status.defaultProvider) -or $null -eq $status.providers -or $null -eq $status.knowledge) {
        throw 'Agent status response is incomplete.'
    }
}

$persistedImagePaths = @(Get-PersistedImagePaths)
if ($persistedImagePaths.Count -eq 0) {
    throw 'The running database exposes no image paths for the runtime media gate.'
}
Wait-ForCheck "All $($persistedImagePaths.Count) persisted image URLs resolve through the frontend proxy." {
    foreach ($imagePath in $persistedImagePaths) {
        if ($imagePath -notmatch '^/image/[A-Za-z0-9._/-]+\.(?:png|jpe?g|gif|webp)$' -or $imagePath.Contains('..')) {
            throw "Persisted image URL is outside the local raster-image contract: $imagePath"
        }
        $response = Invoke-WebRequest -Uri ($baseUrl + $imagePath) -UseBasicParsing -TimeoutSec 5
        $contentType = Get-ResponseContentType $response
        $contentLength = if ($response.Content -is [byte[]]) {
            $response.Content.Length
        } else {
            $response.RawContentLength
        }
        if ($response.StatusCode -ne 200 -or $contentLength -le 0 -or $contentType -notmatch '^image/') {
            throw "Persisted image did not return a non-empty image response: $imagePath (HTTP $($response.StatusCode), $contentType, $contentLength bytes)"
        }
    }
}

if ($FullAudit) {
    if ($null -eq (Get-Command node -ErrorAction SilentlyContinue)) {
        throw 'Full communication audit requires the Node.js version declared by the frontend package.'
    }
    $previousBaseUrl = $env:CECSMS_QA_BASE_URL
    try {
        $env:CECSMS_QA_BASE_URL = $baseUrl
        & node --env-file=$resolvedEnvironmentFile (Join-Path $projectRoot 'scripts\audit-frontend-backend-communication.mjs')
        if ($LASTEXITCODE -ne 0) { throw "Full communication audit failed with exit code $LASTEXITCODE." }
    } finally {
        $env:CECSMS_QA_BASE_URL = $previousBaseUrl
    }
    & (Join-Path $projectRoot 'scripts\verify-database-crud.ps1') `
        -EnvironmentFile $resolvedEnvironmentFile `
        -BaseUrl $baseUrl
    if ($LASTEXITCODE -ne 0) { throw "Database CRUD audit failed with exit code $LASTEXITCODE." }
}

Write-Host "[PASS] SilverPilot runtime verification completed successfully: $baseUrl/login" -ForegroundColor Green
exit 0
