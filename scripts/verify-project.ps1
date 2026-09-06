param([switch]$SkipAudit)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
$frontendRoot = Join-Path $projectRoot 'SourceCode\cecsmsui-vue'

$scriptFiles = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File
foreach ($scriptFile in $scriptFiles) {
    $tokens = $null
    $parseErrors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile(
        $scriptFile.FullName,
        [ref]$tokens,
        [ref]$parseErrors
    )
    if (@($parseErrors).Count -gt 0) {
        $details = @($parseErrors | ForEach-Object { $_.Message }) -join '; '
        throw "PowerShell syntax validation failed for $($scriptFile.Name): $details"
    }
}
Write-Host "[PASS] PowerShell syntax validation passed for $($scriptFiles.Count) project scripts." -ForegroundColor Green

& (Join-Path $PSScriptRoot 'verify-ide-separation.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot 'verify-startup-lifecycle.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot 'verify-residue-cleanup.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot 'validate-seed-assets.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot 'validate-knowledge-base.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot 'validate-evaluation-dataset.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot 'verify-release-contract.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Push-Location $backendRoot
try {
    & .\mvnw.cmd clean verify
    $backendExit = $LASTEXITCODE
    if ($backendExit -eq 0) {
        $backendJar = Get-ChildItem -LiteralPath (Join-Path $backendRoot 'target') -Filter 'cecsms-serve-*.jar' |
            Where-Object { $_.Name -notlike '*.original' } |
            Select-Object -First 1
        if ($null -eq $backendJar) { throw 'Backend verification did not produce an executable JAR.' }
        $jarEntries = & jar tf $backendJar.FullName
        if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect the backend JAR.' }
        $forbiddenEntries = @($jarEntries | Where-Object {
            $_ -match '(^|/)application-(?:local|host)\.properties$' -or $_ -match '(^|/)\.env($|\.)'
        })
        if ($forbiddenEntries.Count -gt 0) {
            throw "Local configuration or secrets entered the backend JAR: $($forbiddenEntries -join ', ')"
        }
        & .\mvnw.cmd -q org.apache.maven.plugins:maven-dependency-plugin:3.10.0:build-classpath '-Dmdep.outputFile=target/jdeprscan-classpath.txt'
        if ($LASTEXITCODE -ne 0) { throw 'Unable to build the backend dependency classpath for jdeprscan.' }
        $dependencyClasspath = (Get-Content -Raw -LiteralPath (Join-Path $backendRoot 'target\jdeprscan-classpath.txt')).Trim()
        $scanClasspath = "$(Join-Path $backendRoot 'target\classes');$dependencyClasspath"
        & jdeprscan --class-path $scanClasspath --release 25 (Join-Path $backendRoot 'target\classes')
        if ($LASTEXITCODE -ne 0) { throw 'JDK deprecated API scan failed.' }
    }
} finally { Pop-Location }
if ($backendExit -ne 0) { exit $backendExit }

Push-Location $frontendRoot
try {
    if ($SkipAudit) {
        & npm run test
        $frontendExit = $LASTEXITCODE
        if ($frontendExit -eq 0) { & npm run test:fuzz; $frontendExit = $LASTEXITCODE }
        if ($frontendExit -eq 0) { & npm run build; $frontendExit = $LASTEXITCODE }
    } else {
        & npm run check
        $frontendExit = $LASTEXITCODE
    }
} finally { Pop-Location }
if ($frontendExit -ne 0) { exit $frontendExit }
Write-Host '[PASS] Backend tests/deprecation scan, frontend lint/dead-code/unit/property tests/build/audit, knowledge gate and evaluation schema all passed.' -ForegroundColor Green
exit 0
