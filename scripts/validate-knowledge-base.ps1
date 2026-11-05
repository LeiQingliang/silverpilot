param([string]$KnowledgeRoot = (Join-Path $PSScriptRoot '..\knowledge-base\ima-ready'))

$ErrorActionPreference = 'Stop'
$resolvedRoot = [System.IO.Path]::GetFullPath($KnowledgeRoot)
$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()
try { $businessZone = [TimeZoneInfo]::FindSystemTimeZoneById('Asia/Shanghai') }
catch { $businessZone = [TimeZoneInfo]::FindSystemTimeZoneById('China Standard Time') }
$today = [TimeZoneInfo]::ConvertTimeFromUtc([datetime]::UtcNow, $businessZone).Date
$requiredFields = @('title', 'document_id', 'version', 'owner', 'status', 'reviewed_at', 'next_review_at', 'scope', 'source_type')
$allowedStatuses = @('approved', 'draft', 'governance')
$documents = @(Get-ChildItem -LiteralPath $resolvedRoot -Filter '*.md' -File | Sort-Object Name)
if ($documents.Count -eq 0) { Write-Error "No Markdown documents found in $resolvedRoot" }

$ids = @{}
$hashes = @{}
$approvedCount = 0
$draftCount = 0
foreach ($document in $documents) {
    $raw = Get-Content -LiteralPath $document.FullName -Raw -Encoding utf8
    $frontmatter = [regex]::Match($raw, '\A---\r?\n(?<metadata>[\s\S]*?)\r?\n---(?:\r?\n|$)')
    if (-not $frontmatter.Success) {
        $errors.Add("$($document.Name): missing or unclosed YAML frontmatter")
        continue
    }
    $values = @{}
    foreach ($field in $requiredFields) {
        $fieldMatches = [regex]::Matches($frontmatter.Groups['metadata'].Value,
            "(?m)^$([regex]::Escape($field)):[ \t]*([^\r\n]*)\r?$")
        if ($fieldMatches.Count -ne 1 -or [string]::IsNullOrWhiteSpace($fieldMatches[0].Groups[1].Value.Trim(' ', '"', "'"))) {
            $errors.Add("$($document.Name): missing $field")
            if ($fieldMatches.Count -gt 1) { $errors.Add("$($document.Name): duplicate $field") }
        } else {
            $values[$field] = $fieldMatches[0].Groups[1].Value.Trim(' ', '"', "'")
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
    if ($values['status'] -eq 'approved') { $approvedCount++ }
    if ($values['status'] -eq 'draft') { $draftCount++ }
    $dates = @{}
    foreach ($dateField in @('reviewed_at', 'next_review_at')) {
        if (-not $values.ContainsKey($dateField)) { continue }
        $parsedDate = [datetime]::MinValue
        if ($values[$dateField] -notmatch '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' -or
                -not [datetime]::TryParseExact($values[$dateField], 'yyyy-MM-dd',
                    [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$parsedDate)) {
            $errors.Add("$($document.Name): $dateField must be a valid yyyy-MM-dd date")
        } else {
            $dates[$dateField] = $parsedDate.Date
        }
    }
    if ($dates.ContainsKey('reviewed_at') -and $dates['reviewed_at'] -gt $today) {
        $errors.Add("$($document.Name): reviewed_at cannot be in the future")
    }
    if ($dates.ContainsKey('reviewed_at') -and $dates.ContainsKey('next_review_at') -and
            $dates['next_review_at'] -lt $dates['reviewed_at']) {
        $errors.Add("$($document.Name): next_review_at is before reviewed_at")
    }
    if ($dates.ContainsKey('next_review_at') -and $dates['next_review_at'] -lt $today) {
        if ($values['status'] -eq 'draft') {
            $warnings.Add("$($document.Name): draft is excluded from runtime and ineligible for ima import; review expired on $($values['next_review_at']); owner review and external copy removal verification remain required")
        } else {
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
        if (-not $target.StartsWith($resolvedRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar,
                [System.StringComparison]::OrdinalIgnoreCase)) {
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
    $manifestTargets = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $documentTargets = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($document in $documents) { [void]$documentTargets.Add($document.FullName) }
    foreach ($entry in $manifest.documents) {
        $manifestTarget = [IO.Path]::GetFullPath((Join-Path (Split-Path $resolvedRoot -Parent) $entry))
        if (-not $manifestTargets.Add($manifestTarget)) { $errors.Add("manifest references duplicate document: $entry") }
        if (-not $documentTargets.Contains($manifestTarget)) { $errors.Add("manifest references missing or out-of-scope document: $entry") }
    }
    foreach ($document in $documents) {
        if (-not $manifestTargets.Contains($document.FullName)) { $errors.Add("manifest omits document: $($document.Name)") }
    }
    if ($manifest.documents.Count -ne $documents.Count) {
        $errors.Add("manifest count $($manifest.documents.Count) does not match folder count $($documents.Count)")
    }
}

foreach ($item in $warnings) { Write-Host "[WARN] $item" -ForegroundColor Yellow }
if ($errors.Count -gt 0) {
    foreach ($item in $errors) { Write-Host "[FAIL] $item" -ForegroundColor Red }
    exit 1
}
Write-Host "[PASS] Knowledge base is clean: $($documents.Count) documents, $approvedCount approved, $draftCount draft (excluded); as of $($today.ToString('yyyy-MM-dd')) Asia/Shanghai." -ForegroundColor Green
exit 0
