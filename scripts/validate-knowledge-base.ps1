param([string]$KnowledgeRoot = (Join-Path $PSScriptRoot '..\knowledge-base\ima-ready'))

$ErrorActionPreference = 'Stop'
$resolvedRoot = [System.IO.Path]::GetFullPath($KnowledgeRoot)
$errors = [System.Collections.Generic.List[string]]::new()
$requiredFields = @('title', 'document_id', 'version', 'owner', 'status', 'reviewed_at', 'next_review_at', 'scope', 'source_type')
$allowedStatuses = @('approved', 'draft', 'governance')
$documents = @(Get-ChildItem -LiteralPath $resolvedRoot -Filter '*.md' -File | Sort-Object Name)
if ($documents.Count -eq 0) { Write-Error "No Markdown documents found in $resolvedRoot" }

$ids = @{}
$hashes = @{}
foreach ($document in $documents) {
    $raw = Get-Content -LiteralPath $document.FullName -Raw -Encoding utf8
    if (-not $raw.StartsWith('---')) {
        $errors.Add("$($document.Name): missing YAML frontmatter")
        continue
    }
    $values = @{}
    foreach ($field in $requiredFields) {
        $match = [regex]::Match($raw, "(?m)^$([regex]::Escape($field)):\s*(.+?)\s*$")
        if (-not $match.Success -or [string]::IsNullOrWhiteSpace($match.Groups[1].Value)) {
            $errors.Add("$($document.Name): missing $field")
        } else {
            $values[$field] = $match.Groups[1].Value.Trim(' ', '"', "'")
        }
    }
    if ($values.ContainsKey('status') -and $allowedStatuses -notcontains $values['status']) {
        $errors.Add("$($document.Name): invalid status '$($values['status'])'")
    }
    if ($values.ContainsKey('document_id')) {
        $id = $values['document_id']
        if ($ids.ContainsKey($id)) { $errors.Add("$($document.Name): duplicate document_id $id") }
        else { $ids[$id] = $document.Name }
    }
    if ($values.ContainsKey('next_review_at')) {
        $reviewDate = [datetime]::MinValue
        if (-not [datetime]::TryParseExact($values['next_review_at'], 'yyyy-MM-dd', $null, 'None', [ref]$reviewDate)) {
            $errors.Add("$($document.Name): next_review_at must use yyyy-MM-dd")
        } elseif ($reviewDate.Date -lt (Get-Date).Date) {
            $errors.Add("$($document.Name): review expired on $($values['next_review_at'])")
        }
    }
    if ($raw -match '(?i)(sk-[a-z0-9]{16,}|Bearer\s+[a-z0-9._-]{20,}|api[_ -]?key\s*[:=]\s*[a-z0-9._-]{16,})') {
        $errors.Add("$($document.Name): possible plaintext secret")
    }
    $hash = (Get-FileHash -LiteralPath $document.FullName -Algorithm SHA256).Hash
    if ($hashes.ContainsKey($hash)) { $errors.Add("$($document.Name): duplicate content of $($hashes[$hash])") }
    else { $hashes[$hash] = $document.Name }

    foreach ($linkMatch in [regex]::Matches($raw, '\]\((?!https?://|#)([^)]+)\)')) {
        $link = $linkMatch.Groups[1].Value.Split('#')[0]
        if ([string]::IsNullOrWhiteSpace($link)) { continue }
        $target = [System.IO.Path]::GetFullPath((Join-Path $document.DirectoryName $link))
        if (-not $target.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            $errors.Add("$($document.Name): relative link escapes knowledge root: $link")
        } elseif (-not (Test-Path -LiteralPath $target)) {
            $errors.Add("$($document.Name): broken relative link: $link")
        }
    }
}

$manifestPath = Join-Path (Split-Path $resolvedRoot -Parent) 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath)) {
    $errors.Add('knowledge-base/manifest.json is missing')
} else {
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json
    foreach ($entry in $manifest.documents) {
        $manifestTarget = Join-Path (Split-Path $resolvedRoot -Parent) $entry
        if (-not (Test-Path -LiteralPath $manifestTarget)) { $errors.Add("manifest references missing document: $entry") }
    }
    if ($manifest.documents.Count -ne $documents.Count) {
        $errors.Add("manifest count $($manifest.documents.Count) does not match folder count $($documents.Count)")
    }
}

if ($errors.Count -gt 0) {
    foreach ($item in $errors) { Write-Host "[FAIL] $item" -ForegroundColor Red }
    exit 1
}
$approvedCount = @($documents | Where-Object {
    (Get-Content -LiteralPath $_.FullName -Raw -Encoding utf8) -match '(?m)^status:\s*approved\s*$'
}).Count
Write-Host "[PASS] Knowledge base is clean: $($documents.Count) documents, $approvedCount approved." -ForegroundColor Green
exit 0
