param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\.env.docker'),
    [ValidateSet('Docker', 'Local')]
    [string]$RuntimeMode = 'Docker',
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$resolvedEnvironmentFile = [System.IO.Path]::GetFullPath($EnvironmentFile)
$backupRoot = Join-Path $projectRoot 'backups'
$script:localMySql = $null
$script:localMySqlDump = $null
$script:localDatabasePassword = $null
$script:localDatabaseUsername = 'root'
$script:localDatabaseName = 'a_old'

function Get-ScopedEnvironmentValue([string]$Name) {
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $value = [Environment]::GetEnvironmentVariable($Name, $scope)
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value.Trim() }
    }
    return ''
}

function Invoke-MySql([string]$Sql) {
    if ($RuntimeMode -eq 'Local') {
        $previousPassword = $env:MYSQL_PWD
        try {
            $env:MYSQL_PWD = $script:localDatabasePassword
            $output = $Sql | & $script:localMySql --protocol=TCP -h 127.0.0.1 -P 3306 `
                "-u$($script:localDatabaseUsername)" "--database=$($script:localDatabaseName)" `
                --batch --skip-column-names
        } finally {
            $env:MYSQL_PWD = $previousPassword
        }
    } else {
        $output = $Sql | & docker compose --env-file $resolvedEnvironmentFile exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -uroot --database="$MYSQL_DATABASE" --batch --skip-column-names'
    }
    if ($LASTEXITCODE -ne 0) { throw "MySQL command failed with exit code $LASTEXITCODE." }
    return @($output)
}

function Get-Scalar([string]$Sql) {
    $rows = @(Invoke-MySql $Sql)
    if ($rows.Count -ne 1) { throw "Expected one MySQL result row, received $($rows.Count)." }
    return [string]$rows[0]
}

function New-VerifiedBackup {
    if (-not (Test-Path -LiteralPath $backupRoot)) {
        New-Item -ItemType Directory -Path $backupRoot | Out-Null
    }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $name = "a_old-$($RuntimeMode.ToLowerInvariant())-before-integrity-$stamp.sql"
    $containerPath = "/tmp/$name"
    $localPath = Join-Path $backupRoot $name
    if ($RuntimeMode -eq 'Local') {
        $previousPassword = $env:MYSQL_PWD
        try {
            $env:MYSQL_PWD = $script:localDatabasePassword
            & $script:localMySqlDump --protocol=TCP -h 127.0.0.1 -P 3306 `
                "-u$($script:localDatabaseUsername)" --single-transaction --routines --triggers --events `
                --set-gtid-purged=OFF "--result-file=$localPath" $script:localDatabaseName
            if ($LASTEXITCODE -ne 0) { throw 'Local mysqldump failed; no repair was attempted.' }
        } finally {
            $env:MYSQL_PWD = $previousPassword
        }
    } else {
        $containerId = (& docker compose --env-file $resolvedEnvironmentFile ps -q mysql).Trim()
        if ([string]::IsNullOrWhiteSpace($containerId)) { throw 'The project MySQL container is not running.' }
        try {
            $dumpCommand = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --protocol=TCP -h 127.0.0.1 -uroot --single-transaction --routines --triggers --events --set-gtid-purged=OFF --result-file={0} "$MYSQL_DATABASE"' -f $containerPath
            & docker exec $containerId sh -lc $dumpCommand
            if ($LASTEXITCODE -ne 0) { throw 'mysqldump failed; no repair was attempted.' }
            & docker cp "${containerId}:$containerPath" $localPath | Out-Null
            if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $localPath)) {
                throw 'Unable to copy the database backup from the MySQL container.'
            }
        } finally {
            & docker exec $containerId rm -f -- $containerPath 2>$null | Out-Null
        }
    }
    $file = Get-Item -LiteralPath $localPath
    if ($file.Length -lt 1024) { throw "Database backup is unexpectedly small: $($file.Length) bytes." }
    $hash = (Get-FileHash -LiteralPath $localPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Host "[PASS] Verified backup: $localPath ($($file.Length) bytes, SHA-256 $hash)" -ForegroundColor Green
    return $localPath
}

if ($RuntimeMode -eq 'Local') {
    $service = Get-CimInstance Win32_Service -Filter "Name='MySQL97'" -ErrorAction SilentlyContinue
    if ($null -eq $service) { throw 'The local MySQL97 service was not found.' }
    $binaryMatch = [regex]::Match([string]$service.PathName, '^"([^"]+)"')
    $serverBinary = if ($binaryMatch.Success) { $binaryMatch.Groups[1].Value } else { ([string]$service.PathName -split '\s+')[0].Trim('"') }
    $binDirectory = Split-Path -Parent $serverBinary
    $script:localMySql = Join-Path $binDirectory 'mysql.exe'
    $script:localMySqlDump = Join-Path $binDirectory 'mysqldump.exe'
    if (-not (Test-Path -LiteralPath $script:localMySql) -or -not (Test-Path -LiteralPath $script:localMySqlDump)) {
        throw "Local MySQL client tools were not found under $binDirectory."
    }
    $script:localDatabaseUsername = Get-ScopedEnvironmentValue 'CECSMS_LOCAL_DB_USERNAME'
    if ([string]::IsNullOrWhiteSpace($script:localDatabaseUsername)) { $script:localDatabaseUsername = 'root' }
    $script:localDatabasePassword = Get-ScopedEnvironmentValue 'CECSMS_LOCAL_DB_PASSWORD'
    if ([string]::IsNullOrWhiteSpace($script:localDatabasePassword)) {
        $script:localDatabasePassword = Get-ScopedEnvironmentValue 'CECSMS_DB_PASSWORD'
    }
    if ([string]::IsNullOrWhiteSpace($script:localDatabasePassword)) {
        throw 'Local repair requires CECSMS_LOCAL_DB_PASSWORD or CECSMS_DB_PASSWORD in the current/user environment.'
    }
} else {
    if (-not (Test-Path -LiteralPath $resolvedEnvironmentFile)) {
        throw "Docker environment file is missing: $resolvedEnvironmentFile"
    }
    if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI is not available in PATH.'
    }
}

Push-Location $projectRoot
try {
    if ($RuntimeMode -eq 'Local') {
        $previousPassword = $env:MYSQL_PWD
        try {
            $env:MYSQL_PWD = $script:localDatabasePassword
            & (Join-Path (Split-Path -Parent $script:localMySql) 'mysqladmin.exe') --protocol=TCP -h 127.0.0.1 -P 3306 "-u$($script:localDatabaseUsername)" ping --silent
        } finally {
            $env:MYSQL_PWD = $previousPassword
        }
    } else {
        & docker compose --env-file $resolvedEnvironmentFile exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin --protocol=TCP -h 127.0.0.1 -uroot ping --silent'
    }
    if ($LASTEXITCODE -ne 0) { throw 'The project MySQL service is not ready.' }

    $orphanRegistrations = [int](Get-Scalar "SELECT COUNT(*) FROM user_activity ua LEFT JOIN user u ON u.id=ua.uId LEFT JOIN activity a ON a.id=ua.aId WHERE u.id IS NULL OR a.id IS NULL;")
    $orphanPermissions = [int](Get-Scalar "SELECT COUNT(*) FROM role_function rf LEFT JOIN role r ON r.id=rf.rId LEFT JOIN sys_function f ON f.id=rf.fId WHERE r.id IS NULL OR f.id IS NULL;")
    Write-Host "[INFO] Active-domain orphan rows: user_activity=$orphanRegistrations, role_function=$orphanPermissions"
    if (-not $Apply) {
        Write-Host '[INFO] Audit-only mode. Re-run with -Apply to back up, quarantine, repair, and add constraints.' -ForegroundColor Yellow
        exit 0
    }

    $backup = New-VerifiedBackup
    $repairSql = @'
CREATE TABLE IF NOT EXISTS data_integrity_quarantine (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_table VARCHAR(64) NOT NULL,
  source_pk BIGINT NOT NULL,
  payload_json JSON NOT NULL,
  reason VARCHAR(255) NOT NULL,
  quarantined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_integrity_quarantine (source_table, source_pk, reason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO data_integrity_quarantine(source_table,source_pk,payload_json,reason)
SELECT 'user_activity',ua.id,
       JSON_OBJECT('id',ua.id,'uId',ua.uId,'aId',ua.aId,'enterDate',ua.enterDate,'enterTime',ua.enterTime,'stateHex',HEX(ua.state)),
       'missing active user or activity'
FROM user_activity ua
LEFT JOIN user u ON u.id=ua.uId
LEFT JOIN activity a ON a.id=ua.aId
WHERE u.id IS NULL OR a.id IS NULL;

INSERT IGNORE INTO data_integrity_quarantine(source_table,source_pk,payload_json,reason)
SELECT 'role_function',rf.id,
       JSON_OBJECT('id',rf.id,'rId',rf.rId,'fId',rf.fId),
       'missing active role or function'
FROM role_function rf
LEFT JOIN role r ON r.id=rf.rId
LEFT JOIN sys_function f ON f.id=rf.fId
WHERE r.id IS NULL OR f.id IS NULL;

DELETE ua FROM user_activity ua
LEFT JOIN user u ON u.id=ua.uId
LEFT JOIN activity a ON a.id=ua.aId
WHERE u.id IS NULL OR a.id IS NULL;

DELETE rf FROM role_function rf
LEFT JOIN role r ON r.id=rf.rId
LEFT JOIN sys_function f ON f.id=rf.fId
WHERE r.id IS NULL OR f.id IS NULL;

UPDATE user_activity
SET state=CASE state
  WHEN '0' THEN _utf8mb4 0xE5B7B2E58F96E6B688E68AA5E5908D
  WHEN '1' THEN _utf8mb4 0xE68AA5E5908DE5AEA1E6A0B8E4B8AD
  WHEN '2' THEN _utf8mb4 0xE68AA5E5908DE68890E58A9F
  ELSE state END
WHERE state IN ('0','1','2');

UPDATE activity a
SET signNum=(SELECT COUNT(*) FROM user_activity ua
             WHERE ua.aId=a.id AND HEX(ua.state)='E68AA5E5908DE68890E58A9F');
'@
    Invoke-MySql $repairSql | Out-Null

    $constraints = [ordered]@{
        fk_user_role = 'ALTER TABLE `user` ADD CONSTRAINT `fk_user_role` FOREIGN KEY (`roleId`) REFERENCES `role` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_activity_type = 'ALTER TABLE `activity` ADD CONSTRAINT `fk_activity_type` FOREIGN KEY (`activityTypeId`) REFERENCES `activity_type` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_activity_director = 'ALTER TABLE `activity` ADD CONSTRAINT `fk_activity_director` FOREIGN KEY (`dId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_user_activity_user = 'ALTER TABLE `user_activity` ADD CONSTRAINT `fk_user_activity_user` FOREIGN KEY (`uId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_user_activity_activity = 'ALTER TABLE `user_activity` ADD CONSTRAINT `fk_user_activity_activity` FOREIGN KEY (`aId`) REFERENCES `activity` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_service_type_parent = 'ALTER TABLE `service_type` ADD CONSTRAINT `fk_service_type_parent` FOREIGN KEY (`leaderId`) REFERENCES `service_type` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_service_order_user = 'ALTER TABLE `service_order` ADD CONSTRAINT `fk_service_order_user` FOREIGN KEY (`uId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_service_order_worker = 'ALTER TABLE `service_order` ADD CONSTRAINT `fk_service_order_worker` FOREIGN KEY (`mId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_service_order_doctor = 'ALTER TABLE `service_order` ADD CONSTRAINT `fk_service_order_doctor` FOREIGN KEY (`dId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_service_order_parent_type = 'ALTER TABLE `service_order` ADD CONSTRAINT `fk_service_order_parent_type` FOREIGN KEY (`typeBId`) REFERENCES `service_type` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_service_order_child_type = 'ALTER TABLE `service_order` ADD CONSTRAINT `fk_service_order_child_type` FOREIGN KEY (`typeSId`) REFERENCES `service_type` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_report_user = 'ALTER TABLE `report` ADD CONSTRAINT `fk_report_user` FOREIGN KEY (`uId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_report_doctor = 'ALTER TABLE `report` ADD CONSTRAINT `fk_report_doctor` FOREIGN KEY (`dId`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_comment_user = 'ALTER TABLE `comment` ADD CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_comment_reply_user = 'ALTER TABLE `comment` ADD CONSTRAINT `fk_comment_reply_user` FOREIGN KEY (`reply_to`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_comment_parent = 'ALTER TABLE `comment` ADD CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_role_function_role = 'ALTER TABLE `role_function` ADD CONSTRAINT `fk_role_function_role` FOREIGN KEY (`rId`) REFERENCES `role` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
        fk_role_function_function = 'ALTER TABLE `role_function` ADD CONSTRAINT `fk_role_function_function` FOREIGN KEY (`fId`) REFERENCES `sys_function` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE;'
    }
    foreach ($name in $constraints.Keys) {
        $exists = [int](Get-Scalar "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_name='$name' AND constraint_type='FOREIGN KEY';")
        if ($exists -eq 0) { Invoke-MySql $constraints[$name] | Out-Null }
    }

    $indexes = [ordered]@{
        'activity:idx_activity_state_schedule' = 'CREATE INDEX `idx_activity_state_schedule` ON `activity` (`state`,`activityDate`,`startTime`,`endTime`);'
        'user_activity:idx_user_activity_activity_state' = 'CREATE INDEX `idx_user_activity_activity_state` ON `user_activity` (`aId`,`state`);'
        'user_activity:idx_user_activity_user_state' = 'CREATE INDEX `idx_user_activity_user_state` ON `user_activity` (`uId`,`state`);'
        'service_order:idx_service_order_user_state' = 'CREATE INDEX `idx_service_order_user_state` ON `service_order` (`uId`,`orderState`);'
        'service_order:idx_service_order_type_state' = 'CREATE INDEX `idx_service_order_type_state` ON `service_order` (`typeBId`,`orderState`);'
        'report:idx_report_user_time' = 'CREATE INDEX `idx_report_user_time` ON `report` (`uId`,`time`);'
    }
    foreach ($key in $indexes.Keys) {
        $parts = $key.Split(':', 2)
        $exists = [int](Get-Scalar "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='$($parts[0])' AND index_name='$($parts[1])';")
        if ($exists -eq 0) { Invoke-MySql $indexes[$key] | Out-Null }
    }

    $remaining = [int](Get-Scalar "SELECT (SELECT COUNT(*) FROM user_activity ua LEFT JOIN user u ON u.id=ua.uId LEFT JOIN activity a ON a.id=ua.aId WHERE u.id IS NULL OR a.id IS NULL)+(SELECT COUNT(*) FROM role_function rf LEFT JOIN role r ON r.id=rf.rId LEFT JOIN sys_function f ON f.id=rf.fId WHERE r.id IS NULL OR f.id IS NULL);")
    if ($remaining -ne 0) { throw "Database repair left $remaining active-domain orphan rows." }
    Write-Host "[PASS] Active database integrity repaired; constraints and indexes are present. Backup retained at $backup" -ForegroundColor Green
} finally {
    Pop-Location
}
