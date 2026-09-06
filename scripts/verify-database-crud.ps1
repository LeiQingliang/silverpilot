[CmdletBinding()]
param(
    [string]$EnvironmentFile = '',
    [string]$BaseUrl = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) {
    $EnvironmentFile = Join-Path $PSScriptRoot '..\.env.docker'
}
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$resolvedEnvironmentFile = [System.IO.Path]::GetFullPath($EnvironmentFile)

function Read-EnvironmentValues {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $resolvedEnvironmentFile) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $parts = $line.Split('=', 2)
        $values[$parts[0].Trim()] = $parts[1].Trim()
    }
    return $values
}

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-ProbeToken([string]$Secret, [int]$UserId) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = [ordered]@{ alg = 'HS256'; typ = 'JWT' } | ConvertTo-Json -Compress
    $payloadJson = [ordered]@{
        iss = 'cecsms-serve'; sub = [string]$UserId; aud = [string]$UserId
        iat = $now; exp = $now + 300
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

function Assert-Success($Response, [string]$Name) {
    if ($null -eq $Response -or [int]$Response.code -ne 200) {
        $code = if ($null -eq $Response) { 'null' } else { [string]$Response.code }
        $message = if ($null -eq $Response) { '' } else { [string]$Response.msg }
        throw "$Name returned application code $code ($message)."
    }
    Write-Host "[PASS] $Name" -ForegroundColor Green
}

function Invoke-MySql([string]$Sql) {
    $output = $Sql | & docker compose --env-file $resolvedEnvironmentFile exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -uroot --database="$MYSQL_DATABASE" --batch --skip-column-names'
    if ($LASTEXITCODE -ne 0) { throw "MySQL verification command failed with exit code $LASTEXITCODE." }
    return @($output)
}

function Get-Scalar([string]$Sql) {
    $rows = @(Invoke-MySql $Sql)
    if ($rows.Count -ne 1) { throw "Expected one MySQL row, received $($rows.Count)." }
    return [string]$rows[0]
}

if (-not (Test-Path -LiteralPath $resolvedEnvironmentFile)) {
    throw "Docker environment file is missing: $resolvedEnvironmentFile"
}
$values = Read-EnvironmentValues
$jwtSecret = [string]$values.SILVERPILOT_JWT_SECRET
if ($jwtSecret.Length -lt 32) { throw 'SILVERPILOT_JWT_SECRET must contain at least 32 characters.' }
if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $port = if ($values.SILVERPILOT_FRONTEND_PORT) { [int]$values.SILVERPILOT_FRONTEND_PORT } else { 8082 }
    $BaseUrl = "http://127.0.0.1:$port"
}
$BaseUrl = $BaseUrl.TrimEnd('/')
$headers = @{ Authorization = "Bearer $(New-ProbeToken $jwtSecret 1)" }
$marker = 'QA-DB-' + [Guid]::NewGuid().ToString('N')
$updatedMarker = "$marker-U"
$createdId = $null

Push-Location $projectRoot
try {
    $types = Invoke-RestMethod -Uri "$BaseUrl/api/activityType/selectByState1" -Headers $headers -TimeoutSec 10
    Assert-Success $types 'Read active activity types through the frontend proxy'
    $type = @($types.result) | Select-Object -First 1
    if ($null -eq $type -or [int]$type.id -le 0) { throw 'No active activity type is available for the CRUD probe.' }

    $directors = Invoke-RestMethod -Uri "$BaseUrl/api/user/selectByRid/2" -Headers $headers -TimeoutSec 10
    Assert-Success $directors 'Read eligible activity directors through MyBatis'
    $director = @($directors.result) | Select-Object -First 1
    if ($null -eq $director -or [int]$director.id -le 0) { throw 'No eligible activity director is available for the CRUD probe.' }
    $contractSample = Invoke-RestMethod -Uri "$BaseUrl/api/activity/selectById/1" -Headers $headers -TimeoutSec 10
    Write-Verbose ("Activity JSON contract sample: " + ($contractSample.result | ConvertTo-Json -Depth 2 -Compress))

    $activity = [ordered]@{
        activityName = $marker
        activityTypeId = [int]$type.id
        activityDate = (Get-Date).Date.AddDays(30).ToString('yyyy-MM-dd')
        startTime = '09:00:00'
        endTime = '10:00:00'
        activityAddress = 'QA Room'
        dId = [int]$director.id
        activityDetail = 'create-through-frontend-backend-mybatis-mysql'
        activityPoint = 1
        limitNum = 2
    }
    $activityJson = $activity | ConvertTo-Json -Compress
    Write-Verbose "CRUD payload: $activityJson"
    $created = Invoke-RestMethod -Uri "$BaseUrl/api/activity/insert" -Method Put -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $activityJson -TimeoutSec 10
    Assert-Success $created 'Create a disposable activity through the complete application path'
    $createdId = [int]$created.result.id
    if ($createdId -le 0) { throw 'The create response did not include a generated database ID.' }
    if ((Get-Scalar "SELECT COUNT(*) FROM activity WHERE id=$createdId AND activityName='$marker';") -ne '1') {
        throw 'The created activity is not present in MySQL.'
    }
    Write-Host '[PASS] MySQL contains the API-created row.' -ForegroundColor Green

    $read = Invoke-RestMethod -Uri "$BaseUrl/api/activity/selectById/$createdId" -Headers $headers -TimeoutSec 10
    Assert-Success $read 'Read the created row back through Nginx, Spring and MyBatis'
    if ([string]$read.result.activityName -ne $marker) { throw 'The read-after-write value does not match.' }
    if ([int]$read.result.dId -ne [int]$director.id -or [int]$read.result.activityTypeId -ne [int]$type.id) {
        throw 'The MyBatis result map did not preserve the activity foreign-key fields.'
    }
    Write-Host '[PASS] JSON and MyBatis preserved the activity foreign-key contract.' -ForegroundColor Green

    $activity['id'] = $createdId
    $activity['activityName'] = $updatedMarker
    $activity['activityDetail'] = 'updated-through-optimistic-state-gates'
    $activity['activityDate'] = (Get-Date).Date.AddDays(45).ToString('yyyy-MM-dd')
    $updated = Invoke-RestMethod -Uri "$BaseUrl/api/activity/update" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ($activity | ConvertTo-Json -Compress) -TimeoutSec 10
    Assert-Success $updated 'Update the disposable row through the complete application path'
    if ((Get-Scalar "SELECT COUNT(*) FROM activity WHERE id=$createdId AND activityName='$updatedMarker' AND activityDetail='updated-through-optimistic-state-gates' AND activityDate='$($activity['activityDate'])' AND startTime='09:00:00' AND endTime='10:00:00';") -ne '1') {
        throw 'The MySQL row did not receive the expected update.'
    }
    Write-Host '[PASS] MySQL contains the API-updated values.' -ForegroundColor Green
    $readUpdated = Invoke-RestMethod -Uri "$BaseUrl/api/activity/selectById/$createdId" -Headers $headers -TimeoutSec 10
    Assert-Success $readUpdated 'Read the edited activity date back through the application'
    if ([string]$readUpdated.result.activityDate -ne [string]$activity['activityDate']) {
        throw 'The edited date was not preserved by the database-to-JSON round trip.'
    }
    Write-Host '[PASS] Edited activity date persists in MySQL and the fresh API response.' -ForegroundColor Green

    $deleted = Invoke-RestMethod -Uri "$BaseUrl/api/activity/delete/$createdId" -Method Post -Headers $headers -TimeoutSec 10
    Assert-Success $deleted 'Delete the disposable row through the complete application path'
    if ((Get-Scalar "SELECT COUNT(*) FROM activity WHERE id=$createdId;") -ne '0') {
        throw 'The disposable activity still exists after the delete request.'
    }
    Write-Host '[PASS] CRUD audit left no disposable business row.' -ForegroundColor Green
    $createdId = $null
} finally {
    $idClause = if ($null -eq $createdId) { '0' } else { [string][int]$createdId }
    Invoke-MySql "DELETE FROM activity WHERE (id=$idClause OR activityName IN ('$marker','$updatedMarker')) AND activityName LIKE 'QA-DB-%';" | Out-Null
    $remaining = Get-Scalar "SELECT COUNT(*) FROM activity WHERE activityName IN ('$marker','$updatedMarker');"
    if ($remaining -ne '0') { throw "CRUD audit cleanup failed; $remaining disposable rows remain." }
    Pop-Location
}

Write-Host "[PASS] End-to-end database CRUD verification completed through $BaseUrl." -ForegroundColor Green
exit 0
