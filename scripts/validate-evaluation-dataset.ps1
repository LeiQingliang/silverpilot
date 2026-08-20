param([string]$Dataset = (Join-Path $PSScriptRoot '..\evaluation\agent-evaluation-dataset.jsonl'))

$ErrorActionPreference = 'Stop'
$lines = @(Get-Content -LiteralPath $Dataset -Encoding utf8 | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($lines.Count -lt 12) { Write-Error 'Evaluation dataset must contain at least 12 cases.' }
$ids = @{}
$required = @('id', 'category', 'userMessage', 'expectedBehavior', 'requiresConfirmation', 'risk')
$lineNumber = 0
foreach ($line in $lines) {
    $lineNumber++
    try { $case = $line | ConvertFrom-Json }
    catch { Write-Error "Invalid JSON on line $lineNumber" }
    foreach ($field in $required) {
        if (-not ($case.PSObject.Properties.Name -contains $field)) { Write-Error "Line $lineNumber is missing $field" }
    }
    if ($ids.ContainsKey($case.id)) { Write-Error "Duplicate evaluation id: $($case.id)" }
    $ids[$case.id] = $true
    if ($case.requiresConfirmation -and $case.expectedBehavior -ne 'pending-not-executed') {
        Write-Error "$($case.id): confirmation cases must expect pending-not-executed"
    }
}
Write-Host "[PASS] Evaluation dataset schema is valid: $($lines.Count) cases." -ForegroundColor Green
exit 0
