param(
    [string]$BaseUrl = 'http://127.0.0.1:8083',
    [string]$LoginName = 'admin',
    [string]$Password = $(if ($env:CECSMS_QA_LOGIN_PASSWORD) { $env:CECSMS_QA_LOGIN_PASSWORD } else { '123456' }),
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env.docker'),
    [ValidateRange(0, 4)]
    [int]$ExpectedRoleId = 0
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$composeFile = Join-Path $projectRoot 'compose.yaml'
$resolvedEnv = [System.IO.Path]::GetFullPath($EnvFile)
$normalizedBaseUrl = $BaseUrl.TrimEnd('/')

if (-not (Test-Path -LiteralPath $resolvedEnv)) {
    throw "Docker environment file is missing: $resolvedEnv"
}
if ([string]::IsNullOrWhiteSpace($LoginName) -or [string]::IsNullOrWhiteSpace($Password)) {
    throw 'A local QA login name and password are required.'
}

$redisContainer = (& docker compose --env-file $resolvedEnv -f $composeFile ps -q redis).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($redisContainer)) {
    throw 'The project Redis container is not running. Start infrastructure before this smoke test.'
}

$captchaKey = [guid]::NewGuid().ToString('D')
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $digest = $sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($captchaKey))
} finally {
    $sha256.Dispose()
}
$captchaDigestPrefix = ([BitConverter]::ToString($digest, 0, 16) -replace '-', '').ToLowerInvariant()
$redisKey = 'cecsms:security:captcha:' + $captchaDigestPrefix

function Invoke-Redis([string[]]$Arguments) {
    $result = & docker exec $redisContainer sh -c `
        'REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli --raw "$@"' _ @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Redis QA command failed with exit code $LASTEXITCODE"
    }
    return @($result)
}

try {
    $captchaResponse = Invoke-WebRequest -UseBasicParsing `
        -Uri "$normalizedBaseUrl/user/getVerificationCode/$captchaKey" -TimeoutSec 10
    if ($captchaResponse.StatusCode -ne 200 -or $captchaResponse.Headers.'Content-Type' -notmatch 'image/jpeg') {
        throw 'The CAPTCHA endpoint did not return a JPEG challenge.'
    }

    $captchaCode = ((Invoke-Redis @('GET', $redisKey)) -join '').Trim()
    if ($captchaCode -notmatch '^[2-9A-HJ-NP-Z]{4}$') {
        throw 'The issued CAPTCHA was not persisted in the project Redis instance.'
    }

    $login = Invoke-RestMethod -Method Post -TimeoutSec 15 `
        -Uri "$normalizedBaseUrl/user/login/$captchaKey" `
        -ContentType 'application/x-www-form-urlencoded; charset=UTF-8' `
        -Body @{ loginName = $LoginName; password = $Password; code = $captchaCode }
    if ($login.code -ne 200 -or [string]::IsNullOrWhiteSpace([string]$login.result.token)) {
        throw "Login failed with application code $($login.code): $($login.msg)"
    }
    if ($null -ne $login.result.password) {
        throw 'The login response exposed a password field value.'
    }
    $actualRoleId = [int]$login.result.roleId
    if ($ExpectedRoleId -gt 0 -and $actualRoleId -ne $ExpectedRoleId) {
        throw "Login succeeded, but the returned role ID was $actualRoleId instead of $ExpectedRoleId."
    }

    $protected = Invoke-RestMethod -Method Get -TimeoutSec 10 `
        -Uri "$normalizedBaseUrl/user/selectById/$($login.result.id)" `
        -Headers @{ Authorization = "Bearer $($login.result.token)" }
    if ($protected.code -ne 200 -or $protected.result.id -ne $login.result.id) {
        throw 'The JWT returned by login did not authorize the expected self-read endpoint.'
    }

    if ($ExpectedRoleId -eq 1) {
        $adminOverview = Invoke-RestMethod -Method Get -TimeoutSec 15 `
            -Uri "$normalizedBaseUrl/chat/admin/overview?days=1" `
            -Headers @{ Authorization = "Bearer $($login.result.token)" }
        if ($null -eq $adminOverview.analytics `
                -or $null -eq $adminOverview.workBuddy `
                -or [int]$adminOverview.businessToolCount -lt 1) {
            throw 'The administrator JWT did not return a valid administrator operations overview.'
        }
    }

    $remaining = [int](((Invoke-Redis @('EXISTS', $redisKey)) -join '').Trim())
    if ($remaining -ne 0) {
        throw 'The CAPTCHA still exists after login and could be replayed.'
    }

    $adminResult = if ($ExpectedRoleId -eq 1) { ', administrator role and administrator-only endpoint' } else { '' }
    Write-Host "[PASS] Real CAPTCHA, Redis one-time consumption, credential verification, JWT issuance, protected self-read$adminResult all passed." -ForegroundColor Green
} finally {
    try { Invoke-Redis @('DEL', $redisKey) *> $null } catch { }
}
