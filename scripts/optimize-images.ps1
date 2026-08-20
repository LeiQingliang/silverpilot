[CmdletBinding()]
param(
    [string]$ImageRoot = '',
    [ValidateRange(320, 4096)]
    [int]$MaxDimension = 1600,
    [ValidateRange(40, 95)]
    [int]$Quality = 78
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ImageRoot)) {
    $ImageRoot = Join-Path $PSScriptRoot '..\image'
}
$resolvedRoot = (Resolve-Path -LiteralPath $ImageRoot).Path
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (-not $resolvedRoot.StartsWith($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "ImageRoot must stay inside the repository: $resolvedRoot"
}

$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if (-not $ffmpeg) {
    throw 'ffmpeg is required. Install a current stable ffmpeg build and retry.'
}

$sources = Get-ChildItem -LiteralPath $resolvedRoot -File -Recurse |
    Where-Object { $_.Extension.ToLowerInvariant() -in '.jpg', '.jpeg', '.png' }

$created = 0
$skipped = 0
$sourceBytes = 0L
$optimizedBytes = 0L
$filter = "scale=w='min($MaxDimension,iw)':h='min($MaxDimension,ih)':force_original_aspect_ratio=decrease"

foreach ($source in $sources) {
    $destination = [System.IO.Path]::ChangeExtension($source.FullName, '.webp')
    if ((Test-Path -LiteralPath $destination) -and
        ((Get-Item -LiteralPath $destination).LastWriteTimeUtc -ge $source.LastWriteTimeUtc)) {
        $skipped++
        continue
    }

    & $ffmpeg.Source -hide_banner -loglevel error -y -i $source.FullName `
        -vf $filter -c:v libwebp -quality $Quality -compression_level 6 -preset picture $destination
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $destination)) {
        throw "Failed to optimize image: $($source.FullName)"
    }

    $created++
    $sourceBytes += $source.Length
    $optimizedBytes += (Get-Item -LiteralPath $destination).Length
}

$savedBytes = [Math]::Max(0, $sourceBytes - $optimizedBytes)
[pscustomobject]@{
    Root = $resolvedRoot
    CreatedOrUpdated = $created
    SkippedCurrent = $skipped
    SourceMiB = [Math]::Round($sourceBytes / 1MB, 2)
    OptimizedMiB = [Math]::Round($optimizedBytes / 1MB, 2)
    SavedMiB = [Math]::Round($savedBytes / 1MB, 2)
}
