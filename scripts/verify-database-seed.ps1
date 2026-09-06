[CmdletBinding()]
param(
    [string]$EnvironmentFile = '',
    [string]$SeedFile = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) {
    $EnvironmentFile = Join-Path $PSScriptRoot '..\.env.docker'
}
if ([string]::IsNullOrWhiteSpace($SeedFile)) {
    $SeedFile = Join-Path $PSScriptRoot '..\database\a_old.sql'
}
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$resolvedEnvironmentFile = [System.IO.Path]::GetFullPath($EnvironmentFile)
$resolvedSeedFile = [System.IO.Path]::GetFullPath($SeedFile)
$repositoryImageRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'image'))
$schemaName = 'qa_seed_' + [Guid]::NewGuid().ToString('N')
$remoteSeedFile = "/tmp/$schemaName.sql"
$requiredTables = @(
    'activity', 'activity_state', 'activity_type', 'agent_action', 'agent_run',
    'comment', 'emergency_help', 'message', 'message_receiver', 'news', 'order_state',
    'recipe', 'recipe_order', 'report', 'role', 'role_function', 'service_order',
    'service_type', 'sys_function', 'user', 'user_activity'
)

function Invoke-MySql([string]$Sql) {
    $output = $Sql | & docker compose --env-file $resolvedEnvironmentFile exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -uroot --default-character-set=utf8mb4 --batch --skip-column-names'
    if ($LASTEXITCODE -ne 0) { throw "MySQL command failed with exit code $LASTEXITCODE." }
    return @($output)
}

function Invoke-SchemaSql([string]$Sql) {
    return @(Invoke-MySql "USE ``$schemaName``; $Sql")
}

function Get-Scalar([string]$Sql) {
    $rows = @(Invoke-SchemaSql $Sql)
    if ($rows.Count -ne 1) { throw "Expected one result row, received $($rows.Count)." }
    return [string]$rows[0]
}

function Assert-Scalar([string]$Name, [string]$Sql, [string]$Expected) {
    $actual = Get-Scalar $Sql
    if ($actual -ne $Expected) { throw "$Name expected $Expected but received $actual." }
    Write-Host "[PASS] $Name ($actual)." -ForegroundColor Green
}

if (-not (Test-Path -LiteralPath $resolvedEnvironmentFile -PathType Leaf)) {
    throw "Docker environment file is missing: $resolvedEnvironmentFile"
}
if (-not (Test-Path -LiteralPath $resolvedSeedFile -PathType Leaf)) {
    throw "Database seed file is missing: $resolvedSeedFile"
}
& (Join-Path $PSScriptRoot 'validate-seed-assets.ps1') `
    -SeedFile $resolvedSeedFile -AssetRoot $repositoryImageRoot
if ($LASTEXITCODE -ne 0) {
    throw "Repository seed-image verification failed with exit code $LASTEXITCODE."
}
if ($schemaName -notmatch '^qa_seed_[0-9a-f]{32}$' -or $remoteSeedFile -notmatch '^/tmp/qa_seed_[0-9a-f]{32}\.sql$') {
    throw 'Generated disposable database targets failed the safety check.'
}

Push-Location $projectRoot
try {
    $containerId = [string](& docker compose --env-file $resolvedEnvironmentFile ps -q mysql)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
        throw 'The MySQL Compose container is not running.'
    }
    $containerId = $containerId.Trim()

    Invoke-MySql "CREATE DATABASE ``$schemaName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" | Out-Null
    & docker cp $resolvedSeedFile "${containerId}:$remoteSeedFile" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy the SQL seed into the MySQL container.' }

    & docker compose --env-file $resolvedEnvironmentFile exec -T mysql sh -lc `
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -uroot --default-character-set=utf8mb4 --database="$1" < "$2"' `
        seed-import $schemaName $remoteSeedFile
    if ($LASTEXITCODE -ne 0) { throw "Seed import failed with exit code $LASTEXITCODE." }
    Write-Host "[PASS] Imported the SQL seed into disposable schema $schemaName." -ForegroundColor Green

    $quotedTables = ($requiredTables | ForEach-Object { "'$_'" }) -join ','
    Assert-Scalar 'Required base-table contract' "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name IN ($quotedTables);" '21'
    $dateColumns = @(Invoke-SchemaSql @'
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND DATA_TYPE IN ('date','datetime','timestamp','year')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
'@)
    if ($dateColumns.Count -eq 0) { throw 'The seed has no date columns to verify.' }
    $dateYearChecks = foreach ($dateColumn in $dateColumns) {
        $parts = ([string]$dateColumn).Split("`t")
        if ($parts.Count -ne 3 -or $parts[0] -notmatch '^[A-Za-z0-9_]+$' -or $parts[1] -notmatch '^[A-Za-z0-9_]+$') {
            throw 'Unexpected date-column metadata in the disposable schema.'
        }
        $tableName = $parts[0]
        $columnName = $parts[1]
        $yearExpression = if ($parts[2] -eq 'year') { "``$columnName``" } else { "YEAR(``$columnName``)" }
        "SELECT COUNT(*) AS invalid_count FROM ``$tableName`` WHERE ``$columnName`` IS NOT NULL AND $yearExpression <> 2027"
    }
    Assert-Scalar 'All non-null seed dates use year 2027' `
        ("SELECT COALESCE(SUM(invalid_count),0) FROM (" + ($dateYearChecks -join ' UNION ALL ') + ") seed_date_years;") '0'
    Assert-Scalar 'Active foreign-key contract' @'
SELECT COUNT(*) FROM information_schema.table_constraints
WHERE constraint_schema=DATABASE() AND constraint_type='FOREIGN KEY'
  AND constraint_name IN (
    'fk_user_role','fk_activity_type','fk_activity_director',
    'fk_user_activity_user','fk_user_activity_activity','fk_service_type_parent',
    'fk_service_order_user','fk_service_order_worker','fk_service_order_doctor',
    'fk_service_order_parent_type','fk_service_order_child_type',
    'fk_report_user','fk_report_doctor','fk_comment_user','fk_comment_reply_user',
    'fk_comment_parent','fk_role_function_role','fk_role_function_function');
'@ '18'
    Assert-Scalar 'Critical index contract' @'
SELECT COUNT(DISTINCT CONCAT(table_name, ':', index_name))
FROM information_schema.statistics
WHERE table_schema=DATABASE()
  AND CONCAT(table_name, ':', index_name) IN (
    'activity:idx_activity_state_schedule',
    'user_activity:idx_user_activity_activity_state',
    'user_activity:idx_user_activity_user_state',
    'service_order:idx_service_order_user_state',
    'service_order:idx_service_order_type_state',
    'report:idx_report_user_time');
'@ '6'
    # Keep native-process input ASCII-only: Windows PowerShell 5.1 otherwise
    # transcodes non-ASCII pipeline text before Docker receives it.
    Assert-Scalar 'Registration-state contract' @'
SELECT COUNT(*) FROM user_activity
WHERE state IS NULL OR state NOT IN (
  _utf8mb4 0xE68AA5E5908DE68890E58A9F,
  _utf8mb4 0xE68AA5E5908DE5AEA1E6A0B8E4B8AD,
  _utf8mb4 0xE5B7B2E58F96E6B688E68AA5E5908D
);
'@ '0'
    Assert-Scalar 'Activity registration counters' @'
SELECT COUNT(*) FROM activity a
WHERE COALESCE(a.signNum,0) <> (
  SELECT COUNT(*) FROM user_activity ua
  WHERE ua.aId=a.id AND ua.state=_utf8mb4 0xE68AA5E5908DE68890E58A9F);
'@ '0'
    Assert-Scalar 'Service category hierarchy' @'
SELECT COUNT(*) FROM service_order o
JOIN service_type p ON p.id=o.typeBId
JOIN service_type c ON c.id=o.typeSId
WHERE p.leaderId IS NOT NULL OR c.leaderId<>p.id;
'@ '0'

    $seedImagePaths = @(Invoke-SchemaSql @'
SELECT `image` FROM `activity` WHERE `image` IS NOT NULL AND `image` <> ''
UNION
SELECT `avatar` FROM `news` WHERE `avatar` IS NOT NULL AND `avatar` <> ''
UNION
SELECT `image_url` FROM `recipe` WHERE `image_url` IS NOT NULL AND `image_url` <> ''
UNION
SELECT `image` FROM `service_type` WHERE `image` IS NOT NULL AND `image` <> '';
'@ | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ } | Sort-Object -Unique)
    if ($seedImagePaths.Count -eq 0) {
        throw 'The database seed does not expose any image paths to validate.'
    }
    $imageRootPrefix = $repositoryImageRoot.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar
    foreach ($imagePath in $seedImagePaths) {
        if ($imagePath -notmatch '^/image/[A-Za-z0-9._/-]+\.(?:png|jpe?g|gif|webp)$' -or $imagePath.Contains('..')) {
            throw "Seed image path is outside the supported repository-image URL contract: $imagePath"
        }
        $relativePath = $imagePath.Substring('/image/'.Length).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        $resolvedImagePath = [System.IO.Path]::GetFullPath((Join-Path $repositoryImageRoot $relativePath))
        if (-not $resolvedImagePath.StartsWith($imageRootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Seed image path escaped the repository image directory: $imagePath"
        }
        if (-not (Test-Path -LiteralPath $resolvedImagePath -PathType Leaf)) {
            throw "Seed image path has no real file under image/: $imagePath ($resolvedImagePath)"
        }
        if ((Get-Item -LiteralPath $resolvedImagePath).Length -le 0) {
            throw "Seed image file is empty: $imagePath"
        }
    }
    Write-Host "[PASS] All $($seedImagePaths.Count) unique imported image URLs resolve to real, non-empty files under image/." -ForegroundColor Green

    $checkTargets = ($requiredTables | ForEach-Object { "``$_``" }) -join ','
    $checkRows = @(Invoke-SchemaSql "CHECK TABLE $checkTargets;")
    if ($checkRows.Count -ne $requiredTables.Count) {
        throw "CHECK TABLE returned $($checkRows.Count) rows for $($requiredTables.Count) tables."
    }
    foreach ($row in $checkRows) {
        $columns = ([string]$row).Split("`t")
        if ($columns.Count -lt 4 -or $columns[$columns.Count - 2] -ne 'status' -or $columns[$columns.Count - 1] -ne 'OK') {
            throw "CHECK TABLE reported a failure: $row"
        }
    }
    Write-Host '[PASS] MySQL CHECK TABLE reported OK for all 21 required tables.' -ForegroundColor Green
} finally {
    try {
        Invoke-MySql "DROP DATABASE IF EXISTS ``$schemaName``;" | Out-Null
    } finally {
        & docker compose --env-file $resolvedEnvironmentFile exec -T mysql sh -lc 'rm -f -- "$1"' seed-cleanup $remoteSeedFile 2>$null
        Pop-Location
    }
}

Write-Host '[PASS] Database seed verification completed with no disposable schema left behind.' -ForegroundColor Green
exit 0
