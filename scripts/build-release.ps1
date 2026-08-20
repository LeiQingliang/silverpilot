param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^v[0-9]+\.[0-9]+\.[0-9]+$')]
    [string]$Tag,

    [string]$OutputDirectory,

    [switch]$SkipVerification,

    [switch]$IncludeRepositorySbom
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$version = $Tag.Substring(1)

function Write-Utf8NoBom {
    param(
        [string]$Path,
        [string]$Content
    )
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function New-DeterministicZip {
    param(
        [string]$SourceDirectory,
        [string]$DestinationPath,
        [DateTimeOffset]$Timestamp
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $sourceRoot = [System.IO.Path]::GetFullPath($SourceDirectory).TrimEnd('\', '/')
    $destination = [System.IO.Path]::GetFullPath($DestinationPath)
    $stream = [System.IO.File]::Open($destination, [System.IO.FileMode]::CreateNew)
    try {
        $archive = New-Object System.IO.Compression.ZipArchive(
            $stream,
            [System.IO.Compression.ZipArchiveMode]::Create,
            $false
        )
        try {
            $files = Get-ChildItem -LiteralPath $sourceRoot -Recurse -File | Sort-Object FullName
            foreach ($file in $files) {
                $relativePath = $file.FullName.Substring($sourceRoot.Length).TrimStart('\', '/') -replace '\\', '/'
                $entry = $archive.CreateEntry($relativePath, [System.IO.Compression.CompressionLevel]::Optimal)
                $entry.LastWriteTime = $Timestamp
                $input = [System.IO.File]::OpenRead($file.FullName)
                try {
                    $output = $entry.Open()
                    try { $input.CopyTo($output) } finally { $output.Dispose() }
                } finally { $input.Dispose() }
            }
        } finally { $archive.Dispose() }
    } finally { $stream.Dispose() }
}

Push-Location $projectRoot
try {
    $workingTreeChanges = @(git status --porcelain --untracked-files=all)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect the Git working tree.' }
    if ($workingTreeChanges.Count -gt 0) {
        throw 'Release assets must be built from a clean Git working tree.'
    }

    & (Join-Path $PSScriptRoot 'verify-release-contract.ps1') -ExpectedTag $Tag
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    if (-not $SkipVerification) {
        & (Join-Path $PSScriptRoot 'verify-publication-safety.ps1')
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & (Join-Path $PSScriptRoot 'verify-project.ps1')
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }

    if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
        $OutputDirectory = Join-Path $projectRoot "release-artifacts\$Tag"
    } elseif (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
        $OutputDirectory = Join-Path $projectRoot $OutputDirectory
    }
    $outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
    if (Test-Path -LiteralPath $outputRoot) {
        $existingFiles = @(Get-ChildItem -LiteralPath $outputRoot -Force)
        if ($existingFiles.Count -gt 0) {
            throw "Release output directory must be empty: $outputRoot"
        }
    } else {
        [void](New-Item -ItemType Directory -Path $outputRoot)
    }

    $commit = [string](git rev-parse HEAD)
    if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$') {
        throw 'Unable to resolve the release commit.'
    }
    $commitEpoch = [long]([string](git show -s --format=%ct HEAD))
    if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve the release commit timestamp.' }
    $commitTime = [DateTimeOffset]::FromUnixTimeSeconds($commitEpoch).ToUniversalTime()
    $mavenTimestamp = $commitTime.ToString('yyyy-MM-ddTHH:mm:ssZ')

    $backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
    $mavenWrapper = if ($env:OS -eq 'Windows_NT') {
        Join-Path $backendRoot 'mvnw.cmd'
    } else {
        Join-Path $backendRoot 'mvnw'
    }
    Push-Location $backendRoot
    try {
        & $mavenWrapper '-B' '-ntp' "-Dproject.build.outputTimestamp=$mavenTimestamp" '-DskipTests' 'clean' 'package'
        if ($LASTEXITCODE -ne 0) { throw 'Deterministic backend release build failed.' }
    } finally { Pop-Location }

    $backendJar = Join-Path $backendRoot "target\cecsms-serve-$version.jar"
    if (-not (Test-Path -LiteralPath $backendJar -PathType Leaf)) {
        throw "Expected backend release JAR was not produced: $backendJar"
    }
    $backendAsset = Join-Path $outputRoot "silverpilot-$version-backend.jar"
    Copy-Item -LiteralPath $backendJar -Destination $backendAsset

    $frontendDist = Join-Path $projectRoot 'SourceCode\cecsmsui-vue\dist'
    if (-not (Test-Path -LiteralPath $frontendDist -PathType Container)) {
        throw 'Frontend dist is missing after project verification.'
    }
    $frontendAsset = Join-Path $outputRoot "silverpilot-$version-frontend.zip"
    New-DeterministicZip -SourceDirectory $frontendDist -DestinationPath $frontendAsset -Timestamp $commitTime

    $sourceAsset = Join-Path $outputRoot "silverpilot-$version-source.zip"
    & git archive '--format=zip' "--prefix=silverpilot-$version/" "--output=$sourceAsset" HEAD
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $sourceAsset -PathType Leaf)) {
        throw 'Unable to create the exact Git source archive.'
    }

    $remoteUrl = ([string](git remote get-url origin)).Trim()
    if ($LASTEXITCODE -ne 0 -or $remoteUrl -notmatch 'github\.com[/:]([^/]+)/([^/]+?)(?:\.git)?$') {
        throw "Unable to derive the GitHub repository from origin '$remoteUrl'."
    }
    $repository = "$($Matches[1])/$($Matches[2])"

    $assetDescriptions = @(
        [ordered]@{ name = [System.IO.Path]::GetFileName($backendAsset); kind = 'spring-boot-executable-jar' },
        [ordered]@{ name = [System.IO.Path]::GetFileName($frontendAsset); kind = 'frontend-static-build' },
        [ordered]@{ name = [System.IO.Path]::GetFileName($sourceAsset); kind = 'exact-git-source' }
    )

    if ($IncludeRepositorySbom) {
        $ghCommand = Get-Command gh -ErrorAction SilentlyContinue
        if ($null -eq $ghCommand) { throw 'GitHub CLI is required to include the repository SBOM.' }
        $sbomResponse = @(& $ghCommand.Source api "repos/$repository/dependency-graph/sbom")
        if ($LASTEXITCODE -ne 0) { throw 'GitHub dependency graph SBOM export failed.' }
        $sbomWrapper = (($sbomResponse -join [Environment]::NewLine) | ConvertFrom-Json)
        if ($null -eq $sbomWrapper.sbom) { throw 'GitHub returned an empty repository SBOM.' }
        $sbomAsset = Join-Path $outputRoot "silverpilot-$version.spdx.json"
        $sbomJson = $sbomWrapper.sbom | ConvertTo-Json -Depth 100
        Write-Utf8NoBom -Path $sbomAsset -Content ($sbomJson + "`n")
        $assetDescriptions += [ordered]@{ name = [System.IO.Path]::GetFileName($sbomAsset); kind = 'spdx-2.3-repository-sbom' }
    }

    $manifestAsset = Join-Path $outputRoot "silverpilot-$version-release-manifest.json"
    $manifest = [ordered]@{
        schemaVersion = 1
        product = 'SilverPilot'
        version = $version
        tag = $Tag
        repository = $repository
        commit = $commit
        commitTimestampUtc = $commitTime.ToString('o')
        assets = $assetDescriptions
    }
    Write-Utf8NoBom -Path $manifestAsset -Content (($manifest | ConvertTo-Json -Depth 6) + "`n")

    $checksumAsset = Join-Path $outputRoot 'SHA256SUMS'
    $checksumLines = Get-ChildItem -LiteralPath $outputRoot -File |
        Where-Object { $_.Name -cne 'SHA256SUMS' } |
        Sort-Object Name |
        ForEach-Object {
            $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash  $($_.Name)"
        }
    Write-Utf8NoBom -Path $checksumAsset -Content (($checksumLines -join "`n") + "`n")

    Write-Host "[PASS] Release assets for $Tag were built from $commit in $outputRoot." -ForegroundColor Green
    Get-ChildItem -LiteralPath $outputRoot -File | Sort-Object Name | Select-Object Name, Length
} finally {
    Pop-Location
}
