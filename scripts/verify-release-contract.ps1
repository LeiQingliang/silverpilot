param(
    [string]$ExpectedTag
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

function Assert-Equal {
    param(
        [string]$Name,
        [string]$Expected,
        [string]$Actual
    )
    if ($Expected -cne $Actual) {
        throw "$Name mismatch: expected '$Expected', found '$Actual'."
    }
}

$runtimePolicy = Get-Content -LiteralPath (Join-Path $projectRoot 'runtime-versions.json') -Raw | ConvertFrom-Json
$version = [string]$runtimePolicy.projectVersion
if ($version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
    throw "runtime-versions.json projectVersion must be stable SemVer, found '$version'."
}
if ([string]$runtimePolicy.npmTarballSha256 -notmatch '^[0-9a-f]{64}$') {
    throw 'runtime-versions.json npmTarballSha256 must be a lowercase SHA-256 digest.'
}
$dependencyCheckVersion = [string]$runtimePolicy.dependencyCheck
if ($dependencyCheckVersion -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
    throw "runtime-versions.json dependencyCheck must be a stable SemVer release, found '$dependencyCheckVersion'."
}

$releaseTag = "v$version"
if (-not [string]::IsNullOrWhiteSpace($ExpectedTag)) {
    Assert-Equal 'Release tag' $releaseTag $ExpectedTag
}

$backendPomPath = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot\pom.xml'
$backendPomText = Get-Content -LiteralPath $backendPomPath -Raw
[xml]$backendPom = $backendPomText
$frontendPackage = Get-Content -LiteralPath (Join-Path $projectRoot 'SourceCode\cecsmsui-vue\package.json') -Raw | ConvertFrom-Json
$citation = Get-Content -LiteralPath (Join-Path $projectRoot 'CITATION.cff') -Raw
$changelog = Get-Content -LiteralPath (Join-Path $projectRoot 'CHANGELOG.md') -Raw

Assert-Equal 'Backend Maven version' $version ([string]$backendPom.project.version)
Assert-Equal 'Frontend package version' $version ([string]$frontendPackage.version)
Assert-Equal 'Dependency-Check Maven plugin' $dependencyCheckVersion ([string]$backendPom.project.properties.'dependency-check.version')

$dependencyCheckContract = [ordered]@{
    'project-local ignored data directory' = '<dependency-check.data-directory>${project.basedir}/../../.dependency-check-data</dependency-check.data-directory>'
    'OWASP maintained NVD mirror' = '<dependency-check.nvd-datafeed-url>https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/nvdcve-{0}.json.gz</dependency-check.nvd-datafeed-url>'
    'network-disabled scan default' = '<autoUpdate>false</autoUpdate>'
    'high severity build gate' = '<failBuildOnCVSS>7.0</failBuildOnCVSS>'
    'expiring exact suppression file' = '<suppressionFile>${dependency-check.suppression-file}</suppressionFile>'
    'unused suppression build gate' = '<failBuildOnUnusedSuppressionRule>true</failBuildOnUnusedSuppressionRule>'
    'explicit opt-in Maven profile' = '<id>dependency-check</id>'
}

$dependencyCheckSuppressionPath = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot\config\dependency-check-suppressions.xml'
$dependencyCheckSuppression = Get-Content -LiteralPath $dependencyCheckSuppressionPath -Raw
if ($dependencyCheckSuppression -notmatch '<suppress until="2026-10-01Z">' -or
    $dependencyCheckSuppression -notmatch 'tomcat-embed-core@11\\\.0\\\.24' -or
    $dependencyCheckSuppression -notmatch '<cve>CVE-2026-66299</cve>') {
    throw 'The reviewed Tomcat examples-only suppression must remain exact, expiring, and limited to CVE-2026-66299.'
}
foreach ($entry in $dependencyCheckContract.GetEnumerator()) {
    if (-not $backendPomText.Contains([string]$entry.Value)) {
        throw "Dependency-Check contract is missing $($entry.Key)."
    }
}

$dependencyCheckScript = Join-Path $projectRoot 'scripts\invoke-dependency-check.ps1'
if (-not (Test-Path -LiteralPath $dependencyCheckScript -PathType Leaf)) {
    throw "The bounded Dependency-Check runner is missing: $dependencyCheckScript"
}
$dependencyCheckRunner = Get-Content -LiteralPath $dependencyCheckScript -Raw
foreach ($requiredRunnerToken in @('UpdateTimeoutMinutes', 'ScanTimeoutMinutes', 'MaximumDataAgeHours', "'-Pdependency-check'", "'-DautoUpdate=false'", 'DependencyCheck_Builder/nvd_cache')) {
    if (-not $dependencyCheckRunner.Contains($requiredRunnerToken)) {
        throw "The bounded Dependency-Check runner is missing '$requiredRunnerToken'."
    }
}

$citationVersion = [regex]::Match($citation, '(?m)^version:\s+"([^"]+)"\s*$')
$releaseDate = [regex]::Match($citation, '(?m)^date-released:\s+"([0-9]{4}-[0-9]{2}-[0-9]{2})"\s*$')
if (-not $citationVersion.Success -or -not $releaseDate.Success) {
    throw 'CITATION.cff must contain quoted version and date-released fields.'
}
Assert-Equal 'Citation version' $version $citationVersion.Groups[1].Value

$escapedVersion = [regex]::Escape($version)
$escapedDate = [regex]::Escape($releaseDate.Groups[1].Value)
if ($changelog -notmatch "(?m)^## \[$escapedVersion\] - $escapedDate\s*$") {
    throw "CHANGELOG.md does not contain the dated $version release entry."
}

$releaseNotes = Join-Path $projectRoot "docs\releases\$releaseTag.md"
if (-not (Test-Path -LiteralPath $releaseNotes -PathType Leaf)) {
    throw "Release notes are missing: $releaseNotes"
}
foreach ($requiredFile in @('.github\release.yml', '.github\workflows\release.yml', 'docs\RELEASE_PROCESS.md')) {
    $requiredPath = Join-Path $projectRoot $requiredFile
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Release governance file is missing: $requiredFile"
    }
}

$digestProperties = @(
    'temurinJdk',
    'temurinJre',
    'node',
    'nginx',
    'golang',
    'mysql',
    'alpine'
)
foreach ($property in $digestProperties) {
    $digest = [string]$runtimePolicy.dockerImageDigests.$property
    if ($digest -notmatch '^sha256:[0-9a-f]{64}$') {
        throw "runtime-versions.json has an invalid $property OCI digest: '$digest'."
    }
}

$dockerfiles = Get-ChildItem -LiteralPath $projectRoot -Filter 'Dockerfile' -File -Recurse
$unpinnedImages = New-Object System.Collections.Generic.List[string]
foreach ($dockerfile in $dockerfiles) {
    $content = Get-Content -LiteralPath $dockerfile.FullName
    foreach ($line in $content) {
        if ($line -match '^FROM\s+(\S+)' -and $Matches[1] -cne 'scratch' -and
            $Matches[1] -notmatch '@sha256:[0-9a-f]{64}$') {
            $relativePath = $dockerfile.FullName.Substring($projectRoot.Length).TrimStart('\', '/')
            $unpinnedImages.Add("${relativePath}: $line")
        }
    }
}
if ($unpinnedImages.Count -gt 0) {
    throw "Docker base images must use tag plus OCI digest: $($unpinnedImages -join '; ')"
}

$frontendDockerfile = Get-Content -LiteralPath (Join-Path $projectRoot 'SourceCode\cecsmsui-vue\Dockerfile') -Raw
$npmTarballPattern = "ADD --checksum=sha256:$([regex]::Escape([string]$runtimePolicy.npmTarballSha256)) https://registry\.npmjs\.org/npm/-/npm-$([regex]::Escape([string]$runtimePolicy.npm))\.tgz /tmp/npm\.tgz"
if ($frontendDockerfile -notmatch $npmTarballPattern -or
    $frontendDockerfile -notmatch 'tar -xzf /tmp/npm\.tgz -C /usr/local/lib/node_modules/npm --strip-components=1' -or
    $frontendDockerfile -notmatch 'ln -sf \.\./lib/node_modules/npm/bin/npm-cli\.js /usr/local/bin/npm') {
    throw 'The frontend Docker build must extract npm from the checksum-pinned official tarball.'
}

$workflowFiles = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.github\workflows') -File -Include '*.yml', '*.yaml'
if ($workflowFiles.Count -eq 0) {
    throw 'No GitHub Actions workflow files were found for supply-chain validation.'
}
foreach ($workflowFile in $workflowFiles) {
    $workflow = Get-Content -LiteralPath $workflowFile.FullName -Raw
    foreach ($line in ($workflow -split "`r?`n")) {
        if ($line -notmatch '^\s*uses:\s+(\S+)') { continue }
        $actionReference = $Matches[1]
        if ($actionReference.StartsWith('./')) { continue }
        if ($actionReference -notmatch '^[^@\s]+@[0-9a-f]{40}$') {
            throw "Workflow action references must use a full 40-character commit SHA: $($workflowFile.Name): $actionReference"
        }
    }

    $checkoutCount = [regex]::Matches($workflow, '(?m)^\s*uses:\s+actions/checkout@[0-9a-f]{40}(?:\s|$)').Count
    $credentialOptOutCount = [regex]::Matches($workflow, '(?m)^\s*persist-credentials:\s*false\s*$').Count
    if ($credentialOptOutCount -lt $checkoutCount) {
        throw "Every actions/checkout step must set persist-credentials: false: $($workflowFile.Name)"
    }
}

foreach ($scaWorkflowRelativePath in @('.github\workflows\ci.yml', '.github\workflows\release.yml')) {
    $scaWorkflowPath = Join-Path $projectRoot $scaWorkflowRelativePath
    $scaWorkflow = Get-Content -LiteralPath $scaWorkflowPath -Raw
    foreach ($requiredScaToken in @(
        "windows-odc-$dependencyCheckVersion-",
        'actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9',
        './scripts/invoke-dependency-check.ps1 -Mode All -UpdateTimeoutMinutes 15 -ScanTimeoutMinutes 10 -MaximumDataAgeHours 168'
    )) {
        if (-not $scaWorkflow.Contains($requiredScaToken)) {
            throw "$scaWorkflowRelativePath is missing the pinned persistent Java SCA contract '$requiredScaToken'."
        }
    }
}

$mysqlDockerfile = Get-Content -LiteralPath (Join-Path $projectRoot 'database\Dockerfile') -Raw
if ($mysqlDockerfile -notmatch 'microdnf upgrade -y curl-7\.76\.1-40\.el9_8\.5 libcurl-7\.76\.1-40\.el9_8\.5') {
    throw 'The MySQL derivative image must apply the reviewed Oracle curl/libcurl security update before flattening.'
}

Write-Host "[PASS] Release contract ${releaseTag}: version metadata, notes, workflows, changelog, citation and supply-chain digests are consistent." -ForegroundColor Green
exit 0
