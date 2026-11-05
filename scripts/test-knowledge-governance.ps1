# Isolated regression fixtures for the publication validator; no provider or database required.
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$fixtureParent = Join-Path $repoRoot '.qa-artifacts'
$fixtureRoot = Join-Path $fixtureParent ('knowledge-governance-' + [guid]::NewGuid().ToString('N'))
$validator = Join-Path $PSScriptRoot 'validate-knowledge-base.ps1'
$pwsh = (Get-Process -Id $PID).Path
try { $businessZone = [TimeZoneInfo]::FindSystemTimeZoneById('Asia/Shanghai') }
catch { $businessZone = [TimeZoneInfo]::FindSystemTimeZoneById('China Standard Time') }
$today = [TimeZoneInfo]::ConvertTimeFromUtc([datetime]::UtcNow, $businessZone).Date
$past = $today.AddDays(-10).ToString('yyyy-MM-dd')
$yesterday = $today.AddDays(-1).ToString('yyyy-MM-dd')
$current = $today.ToString('yyyy-MM-dd')
$future = $today.AddDays(10).ToString('yyyy-MM-dd')
$cases = @(
    @{ Name = 'valid-approved'; Reviewed = $past; Next = $future; Status = 'approved'; Exit = 0 },
    @{ Name = 'valid-through-today'; Reviewed = $current; Next = $current; Status = 'approved'; Exit = 0 },
    @{ Name = 'expired-approved'; Reviewed = $past; Next = $yesterday; Status = 'approved'; Exit = 1 },
    @{ Name = 'expired-governance'; Reviewed = $past; Next = $yesterday; Status = 'governance'; Exit = 1 },
    @{ Name = 'quarantined-draft'; Reviewed = $past; Next = $yesterday; Status = 'draft'; Exit = 0; Warning = $true },
    @{ Name = 'missing-reviewed'; Reviewed = $null; Next = $future; Status = 'approved'; Exit = 1 },
    @{ Name = 'missing-next'; Reviewed = $past; Next = $null; Status = 'approved'; Exit = 1 },
    @{ Name = 'invalid-date'; Reviewed = '2026-02-30'; Next = $future; Status = 'approved'; Exit = 1 },
    @{ Name = 'invalid-draft-date'; Reviewed = $past; Next = 'not-a-date'; Status = 'draft'; Exit = 1 },
    @{ Name = 'future-review'; Reviewed = $future; Next = $future; Status = 'approved'; Exit = 1 },
    @{ Name = 'inverted-window'; Reviewed = $current; Next = $yesterday; Status = 'draft'; Exit = 1 },
    @{ Name = 'body-cannot-supply-date'; Reviewed = $null; Next = $future; Status = 'approved'; Exit = 1; Body = "reviewed_at: $past" },
    @{ Name = 'duplicate-review-date'; Reviewed = $past; Next = $future; Status = 'approved'; Exit = 1; Extra = "next_review_at: $future" },
    @{ Name = 'duplicate-manifest'; Reviewed = $past; Next = $future; Status = 'approved'; Exit = 1; Manifest = @('ima-ready/doc.md', 'ima-ready/doc.md') },
    @{ Name = 'omitted-manifest'; Reviewed = $past; Next = $future; Status = 'approved'; Exit = 1; Manifest = @() }
)

try {
    foreach ($case in $cases) {
        $caseRoot = Join-Path $fixtureRoot $case.Name
        $knowledgeRoot = Join-Path $caseRoot 'ima-ready'
        [void](New-Item -ItemType Directory -Path $knowledgeRoot -Force)
        $metadata = @('---', 'title: Fixture', 'document_id: TEST-001', 'version: 1.0.0',
            'owner: fixture', "status: $($case.Status)")
        if ($null -ne $case.Reviewed) { $metadata += "reviewed_at: $($case.Reviewed)" }
        if ($null -ne $case.Next) { $metadata += "next_review_at: $($case.Next)" }
        $metadata += @('scope: test', 'source_type: test')
        if ($case.Extra) { $metadata += $case.Extra }
        $metadata += @('---', 'Fixture content.')
        if ($case.Body) { $metadata += $case.Body }
        [IO.File]::WriteAllText((Join-Path $knowledgeRoot 'doc.md'), ($metadata -join "`n"))
        $entries = if ($case.ContainsKey('Manifest')) { @($case.Manifest) } else { @('ima-ready/doc.md') }
        [IO.File]::WriteAllText((Join-Path $caseRoot 'manifest.json'),
            (@{ documents = @($entries) } | ConvertTo-Json -Depth 4))
        $output = & $pwsh -NoProfile -File $validator -KnowledgeRoot $knowledgeRoot 2>&1
        $actualExit = $LASTEXITCODE
        if ($actualExit -ne $case.Exit) {
            throw "$($case.Name): expected exit $($case.Exit), received $actualExit`n$($output -join "`n")"
        }
        if ($case.Warning -and ($output -join "`n") -notmatch '\[WARN\].*draft is excluded') {
            throw "$($case.Name): missing quarantine warning"
        }
        Write-Host "[PASS] $($case.Name)"
    }
    Write-Host "Knowledge governance validator regression: $($cases.Count) fixtures passed."
    $global:LASTEXITCODE = 0
} finally {
    $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
    $allowedPrefix = [IO.Path]::GetFullPath($fixtureParent).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if ($resolvedFixture.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase) -and
            (Test-Path -LiteralPath $resolvedFixture)) {
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}
