[CmdletBinding()]
param(
    [string]$SeedFile = '',
    [string]$AssetRoot = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($SeedFile)) {
    $SeedFile = Join-Path $PSScriptRoot '..\database\a_old.sql'
}
if ([string]::IsNullOrWhiteSpace($AssetRoot)) {
    $AssetRoot = Join-Path $PSScriptRoot '..\image'
}
$resolvedSeedFile = [System.IO.Path]::GetFullPath($SeedFile)
$resolvedAssetRoot = [System.IO.Path]::GetFullPath($AssetRoot)

if (-not (Test-Path -LiteralPath $resolvedSeedFile -PathType Leaf)) {
    throw "Database seed file is missing: $resolvedSeedFile"
}
if (-not (Test-Path -LiteralPath $resolvedAssetRoot -PathType Container)) {
    throw "Repository image directory is missing: $resolvedAssetRoot"
}

function Get-HeaderBytes([string]$Path, [int]$Count = 24) {
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $buffer = New-Object byte[] $Count
        $read = $stream.Read($buffer, 0, $buffer.Length)
        if ($read -eq $buffer.Length) { return $buffer }
        if ($read -le 0) { return [byte[]]@() }
        return [byte[]]$buffer[0..($read - 1)]
    } finally {
        $stream.Dispose()
    }
}

function Assert-ImageSignature([System.IO.FileInfo]$File) {
    $header = @(Get-HeaderBytes $File.FullName)
    $extension = $File.Extension.ToLowerInvariant()
    $valid = switch ($extension) {
        '.png' {
            $header.Count -ge 24 -and
            $header[0] -eq 0x89 -and $header[1] -eq 0x50 -and
            $header[2] -eq 0x4e -and $header[3] -eq 0x47 -and
            $header[4] -eq 0x0d -and $header[5] -eq 0x0a -and
            $header[6] -eq 0x1a -and $header[7] -eq 0x0a
        }
        { $_ -in '.jpg', '.jpeg' } {
            $header.Count -ge 3 -and $header[0] -eq 0xff -and
            $header[1] -eq 0xd8 -and $header[2] -eq 0xff
        }
        '.gif' {
            if ($header.Count -lt 6) { $false } else {
                [Text.Encoding]::ASCII.GetString([byte[]]$header[0..5]) -in @('GIF87a', 'GIF89a')
            }
        }
        '.webp' {
            if ($header.Count -lt 12) { $false } else {
                [Text.Encoding]::ASCII.GetString([byte[]]$header[0..3]) -eq 'RIFF' -and
                [Text.Encoding]::ASCII.GetString([byte[]]$header[8..11]) -eq 'WEBP'
            }
        }
        default { $false }
    }
    if (-not $valid) {
        throw "Seed image extension and file signature do not match: $($File.FullName)"
    }
}

$sql = Get-Content -LiteralPath $resolvedSeedFile -Raw
$publicPaths = @(
    [regex]::Matches($sql, '/image/[A-Za-z0-9._/-]+') |
        ForEach-Object { $_.Value } |
        Sort-Object -Unique
)
if ($publicPaths.Count -eq 0) {
    throw 'Database seed contains no /image/... paths to validate.'
}

$assetRootPrefix = $resolvedAssetRoot.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
) + [System.IO.Path]::DirectorySeparatorChar
$referencedFiles = @()
$webpFiles = @()
foreach ($publicPath in $publicPaths) {
    if ($publicPath.Contains('..') -or
        $publicPath -notmatch '^/image/[A-Za-z0-9._/-]+\.(?:png|jpe?g|gif|webp)$') {
        throw "Seed image path is not a safe repository raster-image URL: $publicPath"
    }
    $relativePath = $publicPath.Substring('/image/'.Length).Replace(
        '/', [System.IO.Path]::DirectorySeparatorChar)
    $resolvedPath = [System.IO.Path]::GetFullPath((Join-Path $resolvedAssetRoot $relativePath))
    if (-not $resolvedPath.StartsWith($assetRootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Seed image path escaped the repository image directory: $publicPath"
    }
    if (-not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
        throw "Seed image path has no real file under image/: $publicPath"
    }
    $file = Get-Item -LiteralPath $resolvedPath
    if ($file.Length -le 0) { throw "Seed image file is empty: $publicPath" }
    Assert-ImageSignature $file
    $referencedFiles += $file

    if ($file.Extension.ToLowerInvariant() -in '.png', '.jpg', '.jpeg') {
        $webpPath = [System.IO.Path]::ChangeExtension($file.FullName, '.webp')
        if (-not (Test-Path -LiteralPath $webpPath -PathType Leaf)) {
            throw "Seed image has no WebP companion in image/: $publicPath"
        }
        $webp = Get-Item -LiteralPath $webpPath
        if ($webp.Length -le 0) { throw "Seed WebP companion is empty: $webpPath" }
        Assert-ImageSignature $webp
        $webpFiles += $webp
    }
}

Write-Host "[PASS] Database seed references $($referencedFiles.Count) real raster images under image/; all files have valid signatures and $($webpFiles.Count) optimized companions are present." -ForegroundColor Green
exit 0
