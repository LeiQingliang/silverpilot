[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [switch]$SkipWebpCompanionCheck
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Join-Path $PSScriptRoot '..'
}
$resolvedProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
$ffprobe = Get-Command ffprobe -ErrorAction SilentlyContinue
if ($null -eq $ffprobe) {
    throw 'ffprobe is required to decode-check image and video resources.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-UnderProject([string]$Path) {
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    $prefix = $resolvedProjectRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolvedPath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Media validation target escaped the project root: $resolvedPath"
    }
    return $resolvedPath
}

function Assert-DecodableVisual([System.IO.FileInfo]$File) {
    $output = & $ffprobe.Source -v error -select_streams 'v:0' `
        -show_entries 'stream=codec_name,width,height' -of 'csv=p=0' $File.FullName 2>&1
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($output -join ''))) {
        throw "Visual resource cannot be decoded: $($File.FullName) $($output -join ' ')"
    }
    $parts = (($output | Select-Object -First 1) -split ',')
    if ($parts.Count -lt 3 -or [int]$parts[1] -le 0 -or [int]$parts[2] -le 0) {
        throw "Visual resource has invalid dimensions: $($File.FullName)"
    }
}

function Assert-SafeSvg([System.IO.FileInfo]$File) {
    $settings = New-Object System.Xml.XmlReaderSettings
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = $null
    try {
        $reader = [System.Xml.XmlReader]::Create($File.FullName, $settings)
        $document = New-Object System.Xml.XmlDocument
        $document.XmlResolver = $null
        $document.Load($reader)
    } finally {
        if ($null -ne $reader) { $reader.Dispose() }
    }
    if ($null -eq $document.DocumentElement -or $document.DocumentElement.LocalName -ne 'svg') {
        throw "SVG has an invalid root element: $($File.FullName)"
    }
    if (@($document.SelectNodes("//*[local-name()='script' or local-name()='object' or local-name()='iframe']")).Count -gt 0) {
        throw "SVG contains active content: $($File.FullName)"
    }
}

function Assert-Pdf([System.IO.FileInfo]$File) {
    $stream = [System.IO.File]::OpenRead($File.FullName)
    try {
        $header = New-Object byte[] 5
        if ($stream.Read($header, 0, $header.Length) -ne $header.Length -or
            [System.Text.Encoding]::ASCII.GetString($header) -ne '%PDF-') {
            throw "PDF signature is invalid: $($File.FullName)"
        }
    } finally {
        $stream.Dispose()
    }
}

function Assert-Docx([System.IO.FileInfo]$File) {
    $archive = $null
    try {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($File.FullName)
        $names = @($archive.Entries | ForEach-Object { $_.FullName })
        if ('[Content_Types].xml' -notin $names -or 'word/document.xml' -notin $names) {
            throw "DOCX package is missing required entries: $($File.FullName)"
        }
    } finally {
        if ($null -ne $archive) { $archive.Dispose() }
    }
}

$visualRoots = @(
    (Join-Path $resolvedProjectRoot 'image'),
    (Join-Path $resolvedProjectRoot 'SourceCode\cecsmsui-vue\src\assets')
)
$documentRoot = Join-Path $resolvedProjectRoot 'file'
$videoRoot = Join-Path $resolvedProjectRoot 'video'
foreach ($path in @($visualRoots + $documentRoot + $videoRoot)) {
    [void](Assert-UnderProject $path)
    if (-not (Test-Path -LiteralPath $path -PathType Container)) {
        throw "Required media directory is missing: $path"
    }
}

$visualCount = 0
$svgCount = 0
foreach ($root in $visualRoots) {
    foreach ($file in @(Get-ChildItem -LiteralPath $root -Recurse -File)) {
        if ($file.Length -le 0) { throw "Media file is empty: $($file.FullName)" }
        switch ($file.Extension.ToLowerInvariant()) {
            { $_ -in '.jpg', '.jpeg', '.png', '.webp', '.gif', '.ico' } {
                Assert-DecodableVisual $file
                $visualCount++
                break
            }
            '.svg' {
                Assert-SafeSvg $file
                $svgCount++
                break
            }
        }
    }
}

$runtimeImageRoot = Join-Path $resolvedProjectRoot 'image'
$sourceImages = @(Get-ChildItem -LiteralPath $runtimeImageRoot -Recurse -File | Where-Object { $_.Extension.ToLowerInvariant() -in '.jpg', '.jpeg', '.png' })
if (-not $SkipWebpCompanionCheck) {
    foreach ($source in $sourceImages) {
        $webp = [System.IO.Path]::ChangeExtension($source.FullName, '.webp')
        if (-not (Test-Path -LiteralPath $webp -PathType Leaf) -or (Get-Item -LiteralPath $webp).Length -le 0) {
            throw "Runtime image has no usable WebP companion: $($source.FullName)"
        }
    }
}

$documentCount = 0
foreach ($file in @(Get-ChildItem -LiteralPath $documentRoot -Recurse -File)) {
    if ($file.Length -le 0) { throw "Document file is empty: $($file.FullName)" }
    switch ($file.Extension.ToLowerInvariant()) {
        '.pdf' { Assert-Pdf $file; $documentCount++; break }
        '.docx' { Assert-Docx $file; $documentCount++; break }
        default { throw "Unvalidated document type found: $($file.FullName)" }
    }
}

$videoCount = 0
foreach ($file in @(Get-ChildItem -LiteralPath $videoRoot -Recurse -File)) {
    if ($file.Length -le 0) { throw "Video file is empty: $($file.FullName)" }
    $output = & $ffprobe.Source -v error -show_entries 'stream=codec_type,codec_name' -of 'csv=p=0' $file.FullName 2>&1
    if ($LASTEXITCODE -ne 0 -or -not (($output -join "`n") -match '(?m)^video,')) {
        throw "Video resource has no decodable video stream: $($file.FullName) $($output -join ' ')"
    }
    $videoCount++
}

Write-Host "[PASS] Decoded $visualCount raster images, parsed $svgCount SVG files, opened $documentCount documents, checked $videoCount videos, and verified $($sourceImages.Count) WebP companion pairs." -ForegroundColor Green
exit 0
