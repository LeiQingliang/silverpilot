$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

Push-Location $projectRoot
try {
    $paths = @(& git ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate publishable files.' }

    $forbidden = @($paths | Where-Object {
        $normalized = $_ -replace '\\', '/'
        ($normalized -match '(^|/)\.env(?:\..+)?$' -and $normalized -notmatch '\.example$') -or
        $normalized -match '(^|/)(?:id_rsa|id_ed25519|credentials|secrets?)(?:\.|$)' -or
        $normalized -match '\.(?:pem|p12|pfx|jks|keystore)$' -or
        $normalized -match '(^|/)application-(?:local|host)\.properties$'
    })
    if ($forbidden.Count -gt 0) {
        throw "Forbidden local or secret-bearing files are publishable: $($forbidden -join ', ')"
    }

    $required = @('README.md', 'LICENSE', 'CONTRIBUTING.md', 'CODE_OF_CONDUCT.md', 'SECURITY.md')
    $missing = @($required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $projectRoot $_) -PathType Leaf) })
    if ($missing.Count -gt 0) { throw "Required community files are missing: $($missing -join ', ')" }

    $oversized = New-Object System.Collections.Generic.List[string]
    $textExtensions = @('.cff', '.cmd', '.css', '.env', '.example', '.html', '.java', '.js', '.json', '.jsonl', '.md', '.mjs', '.properties', '.ps1', '.scss', '.sh', '.sql', '.toml', '.txt', '.vue', '.xml', '.yaml', '.yml')
    $privateKeyPattern = '-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'
    $tokenPatterns = @(
        'gh[pousr]_[A-Za-z0-9_]{30,}',
        'AKIA[0-9A-Z]{16}',
        'AIza[0-9A-Za-z_-]{35}'
    )
    $mainlandPhonePattern = '(?<!\d)1[3-9]\d{9}(?!\d)'
    $prcIdPattern = '(?<!\d)\d{17}[\dXx](?!\d)'
    $findings = New-Object System.Collections.Generic.List[string]

    foreach ($relativePath in $paths) {
        $fullPath = Join-Path $projectRoot $relativePath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { continue }
        $item = Get-Item -LiteralPath $fullPath
        if ($item.Length -ge 90MB) { $oversized.Add($relativePath) }
        if ($item.Length -gt 5MB -or [IO.Path]::GetExtension($fullPath).ToLowerInvariant() -notin $textExtensions) { continue }

        $lineNumber = 0
        foreach ($line in Get-Content -LiteralPath $fullPath -ErrorAction Stop) {
            $lineNumber++
            if ($line -match $privateKeyPattern) { $findings.Add("private-key:$relativePath`:$lineNumber") }
            foreach ($pattern in $tokenPatterns) {
                if ($line -match $pattern) { $findings.Add("credential:$relativePath`:$lineNumber") }
            }
            if ($line -match $mainlandPhonePattern) { $findings.Add("phone-like:$relativePath`:$lineNumber") }
            if ($line -match $prcIdPattern) { $findings.Add("id-like:$relativePath`:$lineNumber") }
        }
    }

    if ($oversized.Count -gt 0) { throw "Files at or above the 90 MiB publication limit: $($oversized -join ', ')" }
    if ($findings.Count -gt 0) { throw "Potential secrets or personal identifiers detected (values suppressed): $($findings -join ', ')" }

    & git -c core.safecrlf=false diff --check
    if ($LASTEXITCODE -ne 0) { throw 'Working-tree whitespace validation failed.' }
    & git -c core.safecrlf=false diff --cached --check
    if ($LASTEXITCODE -ne 0) { throw 'Staged whitespace validation failed.' }

    Write-Host "[PASS] Publication safety: $($paths.Count) files checked; no forbidden files, oversized blobs, high-confidence secrets, mainland phone numbers or PRC ID patterns found." -ForegroundColor Green
} finally {
    Pop-Location
}
