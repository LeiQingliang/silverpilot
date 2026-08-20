$ErrorActionPreference = 'Stop'

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$policyPath = Join-Path $projectRoot 'runtime-versions.json'
$policy = Get-Content -LiteralPath $policyPath -Raw | ConvertFrom-Json
$npmVersion = [string]$policy.npm
$expectedSha256 = [string]$policy.npmTarballSha256

if ($npmVersion -notmatch '^\d+\.\d+\.\d+$' -or $expectedSha256 -notmatch '^[0-9a-f]{64}$') {
    throw 'runtime-versions.json must contain a stable npm version and lowercase SHA-256 tarball digest.'
}

$temporaryRoot = if ([string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
    [System.IO.Path]::GetTempPath()
} else {
    [System.IO.Path]::GetFullPath($env:RUNNER_TEMP)
}
$archive = Join-Path $temporaryRoot "silverpilot-npm-$npmVersion-$([guid]::NewGuid().ToString('N')).tgz"
$uri = "https://registry.npmjs.org/npm/-/npm-$npmVersion.tgz"

try {
    Write-Host "[INFO] Downloading npm $npmVersion from the official registry." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $uri -OutFile $archive -UseBasicParsing -TimeoutSec 60
    $actualSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha256 -cne $expectedSha256) {
        throw "npm tarball checksum mismatch: expected '$expectedSha256', found '$actualSha256'."
    }

    & npm install --global $archive --ignore-scripts
    if ($LASTEXITCODE -ne 0) { throw 'The checksum-verified npm tarball could not be installed.' }
    $installedVersion = [string](& npm --version)
    if ($LASTEXITCODE -ne 0 -or $installedVersion.Trim() -cne $npmVersion) {
        throw "npm installation mismatch: expected '$npmVersion', found '$($installedVersion.Trim())'."
    }
    Write-Host "[PASS] npm $npmVersion installed from SHA-256 verified official tarball." -ForegroundColor Green
} finally {
    if (Test-Path -LiteralPath $archive -PathType Leaf) {
        [System.IO.File]::Delete([System.IO.Path]::GetFullPath($archive))
    }
}
