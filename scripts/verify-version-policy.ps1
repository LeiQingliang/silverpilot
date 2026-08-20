param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Local', 'Docker')]
    [string]$Mode,
    [switch]$SkipLocalHostChecks,
    [switch]$RuntimeOnly
)

$ErrorActionPreference = 'Stop'
if ([Net.ServicePointManager]::SecurityProtocol -band [Net.SecurityProtocolType]::Tls12) {
    # TLS 1.2 is already enabled.
} else {
    [Net.ServicePointManager]::SecurityProtocol =
        [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
}

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$policyFile = Join-Path $projectRoot 'runtime-versions.json'
$backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
$frontendRoot = Join-Path $projectRoot 'SourceCode\cecsmsui-vue'
$requestHeaders = @{ 'User-Agent' = 'SilverPilot-Version-Audit/1.0' }

function Get-OfficialJson([string]$Name, [string]$Uri) {
    try {
        return (Invoke-RestMethod -Uri $Uri -Headers $requestHeaders -TimeoutSec 30)
    } catch {
        throw "Official version check '$Name' failed at $Uri. Check Internet/proxy/TLS access and retry. $($_.Exception.Message)"
    }
}

function Get-OfficialText([string]$Name, [string]$Uri) {
    try {
        return [string](Invoke-WebRequest -Uri $Uri -Headers $requestHeaders -UseBasicParsing -TimeoutSec 30).Content
    } catch {
        throw "Official version check '$Name' failed at $Uri. Check Internet/proxy/TLS access and retry. $($_.Exception.Message)"
    }
}

function Get-OfficialFileSha256([string]$Name, [string]$Uri) {
    $temporaryFile = Join-Path ([System.IO.Path]::GetTempPath()) "silverpilot-$([guid]::NewGuid().ToString('N')).download"
    try {
        Invoke-WebRequest -Uri $Uri -Headers $requestHeaders -OutFile $temporaryFile -UseBasicParsing -TimeoutSec 60
        return (Get-FileHash -LiteralPath $temporaryFile -Algorithm SHA256).Hash.ToLowerInvariant()
    } catch {
        throw "Official artifact check '$Name' failed at $Uri. Check Internet/proxy/TLS access and retry. $($_.Exception.Message)"
    } finally {
        if (Test-Path -LiteralPath $temporaryFile -PathType Leaf) {
            [System.IO.File]::Delete([System.IO.Path]::GetFullPath($temporaryFile))
        }
    }
}

function Assert-Equal([string]$Name, [string]$Expected, [string]$Actual, [string]$Remediation) {
    if ($Expected -cne $Actual) {
        throw "$Name is not the verified stable baseline. Expected '$Expected', found '$Actual'. $Remediation"
    }
}

function Assert-StableVersion([string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or
        $Value -match '(?i)(?:^|[-_.])(alpha|beta|preview|pre|rc|m\d+|milestone|snapshot|nightly)(?:[-_.]?\d*)') {
        throw "$Name contains a missing or pre-release version '$Value'. RC, Beta, Preview, milestone, snapshot and nightly versions are forbidden."
    }
}

function Get-LatestStableVersion([string[]]$Values, [string]$Pattern, [string]$Name) {
    $stable = @($Values | Where-Object { $_ -match $Pattern } | Sort-Object { [version]$_ } -Descending)
    if ($stable.Count -eq 0) { throw "Official source '$Name' returned no stable semantic version matching $Pattern." }
    return [string]$stable[0]
}

function Get-DockerHubTags([string]$Repository, [string]$Query) {
    $encodedQuery = [Uri]::EscapeDataString($Query)
    $uri = "https://hub.docker.com/v2/repositories/library/$Repository/tags?page_size=100&name=$encodedQuery"
    $response = Get-OfficialJson "Docker Official Image $Repository" $uri
    return @($response.results | ForEach-Object { [string]$_.name })
}

function Assert-DockerOfficialTag([string]$Repository, [string]$Tag) {
    $tags = @(Get-DockerHubTags $Repository $Tag)
    if ($Tag -notin $tags) {
        throw "Docker Official Image tag '$Repository`:$Tag' is not published. Refusing to substitute a floating, preview, or unrelated tag."
    }
}

function Get-LatestTemurinResoluteTag([int]$Major, [ValidateSet('jdk', 'jre')][string]$ImageType) {
    $tags = @(Get-DockerHubTags 'eclipse-temurin' "$ImageType-resolute")
    $candidates = @()
    foreach ($tag in $tags) {
        if ($tag -match "^$Major\.(\d+)\.(\d+)_(\d+)-$ImageType-resolute$") {
            $candidates += [pscustomobject]@{
                Name = $tag
                Version = [version]"$Major.$($Matches[1]).$($Matches[2])"
                Build = [int]$Matches[3]
            }
        }
    }
    $latest = $candidates | Sort-Object Version, Build -Descending | Select-Object -First 1
    if ($null -eq $latest) {
        throw "Docker Official Image did not publish a stable Temurin $Major $ImageType Resolute tag."
    }
    return [string]$latest.Name
}

function Get-ScopedEnvironmentValue([string]$Name) {
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $value = [Environment]::GetEnvironmentVariable($Name, $scope)
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value.Trim() }
    }
    return ''
}

function Get-CommandPath([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $command) { throw "Required command '$Name' is missing from PATH." }
    return [System.IO.Path]::GetFullPath($command.Source)
}

function Invoke-NativeCapture([string]$FilePath, [string[]]$Arguments) {
    # Windows PowerShell 5.1 turns a native program's stderr into a
    # NativeCommandError when ErrorActionPreference is Stop. Java deliberately
    # prints -version/-XshowSettings to stderr, so capture it under Continue and
    # decide success exclusively from the native exit code.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $FilePath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = @($output | ForEach-Object { [string]$_ })
    }
}

if (-not (Test-Path -LiteralPath $policyFile)) {
    throw "Runtime version policy is missing: $policyFile"
}
$policy = Get-Content -LiteralPath $policyFile -Raw | ConvertFrom-Json
if ([int]$policy.schemaVersion -ne 1) { throw "Unsupported runtime-versions.json schema: $($policy.schemaVersion)" }
if ([string]$policy.npmTarballSha256 -notmatch '^[0-9a-f]{64}$') {
    throw 'runtime-versions.json npmTarballSha256 must be a lowercase SHA-256 digest.'
}

$baselineVersions = @(
    [string]$policy.java.hostRelease,
    [string]$policy.java.dockerJdkTag,
    [string]$policy.java.dockerJreTag,
    [string]$policy.springBoot,
    [string]$policy.maven,
    [string]$policy.dependencyCheck,
    [string]$policy.node,
    [string]$policy.npm,
    [string]$policy.dockerEngine,
    [string]$policy.dockerCompose,
    [string]$policy.mysql.version,
    [string]$policy.redis.version,
    [string]$policy.nginx,
    [string]$policy.go.stable,
    [string]$policy.go.docker,
    [string]$policy.alpine
)
foreach ($value in $baselineVersions) { Assert-StableVersion 'runtime-versions.json' $value }
$alpineBranch = [version][string]$policy.alpine

if (-not $RuntimeOnly) {
Write-Host '[INFO] Checking official stable/LTS release channels. This explicit maintenance audit never installs or upgrades global software.' -ForegroundColor Cyan

$oracleJava = Get-OfficialText 'Oracle Java SE support roadmap' 'https://www.oracle.com/java/technologies/java-se-support-roadmap.html'
if ($oracleJava -notmatch 'Java SE 8, 11, 17, 21, and 25 are LTS releases') {
    throw 'Oracle support roadmap no longer confirms Java 25 as an LTS release. Review the version policy before starting.'
}
$adoptiumAssets = @(Get-OfficialJson 'Eclipse Temurin Java 25 GA' 'https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')
if ($adoptiumAssets.Count -eq 0) { throw 'Adoptium returned no GA Java 25 Windows x64 JDK.' }
$latestJavaRelease = [string]$adoptiumAssets[0].release_name
Assert-StableVersion 'Eclipse Temurin Java release' $latestJavaRelease
Assert-Equal 'Java 25 LTS release' "jdk-$($policy.java.hostRelease)" $latestJavaRelease 'Update the host JDK and runtime-versions.json only after compatibility tests pass.'

$nodeIndex = @(Get-OfficialJson 'Node.js release index' 'https://nodejs.org/dist/index.json')
$latestLtsNodeRow = $nodeIndex | Where-Object {
    $_.lts -and [string]$_.version -match '^v\d+\.\d+\.\d+$'
} | Select-Object -First 1
if ($null -eq $latestLtsNodeRow) { throw 'Node.js did not return a stable LTS release.' }
$latestNode = ([string]$latestLtsNodeRow.version).TrimStart('v')
Assert-Equal 'Node.js LTS' ([string]$policy.node) $latestNode 'Install the latest LTS, update the lock policy, and rerun frontend tests.'

$latestNpm = [string](Get-OfficialJson 'npm latest package' 'https://registry.npmjs.org/npm/latest').version
Assert-StableVersion 'npm latest package' $latestNpm
Assert-Equal 'npm stable' ([string]$policy.npm) $latestNpm 'Install the stable npm release and regenerate package-lock.json with review.'
$npmTarballUri = "https://registry.npmjs.org/npm/-/npm-$($policy.npm).tgz"
$npmTarballSha256 = Get-OfficialFileSha256 'npm official tarball' $npmTarballUri
Assert-Equal 'npm official tarball SHA-256' ([string]$policy.npmTarballSha256) $npmTarballSha256 'Review the official artifact before changing the pinned checksum.'

$mavenDownload = Get-OfficialText 'Apache Maven downloads' 'https://maven.apache.org/download.cgi'
$mavenMatch = [regex]::Match($mavenDownload, 'Apache Maven\s+([0-9]+\.[0-9]+\.[0-9]+)')
if (-not $mavenMatch.Success) { throw 'Apache Maven download page did not expose a stable 3.x release.' }
$latestMaven = $mavenMatch.Groups[1].Value
Assert-Equal 'Apache Maven recommended release' ([string]$policy.maven) $latestMaven 'Do not use Maven 4 RC; update the wrapper to the latest recommended GA release.'

$dependencyCheckRelease = Get-OfficialJson 'OWASP Dependency-Check latest stable release' 'https://api.github.com/repos/dependency-check/DependencyCheck/releases/latest'
if ([bool]$dependencyCheckRelease.draft -or [bool]$dependencyCheckRelease.prerelease -or
    [string]$dependencyCheckRelease.tag_name -notmatch '^v([0-9]+\.[0-9]+\.[0-9]+)$') {
    throw "OWASP Dependency-Check latest endpoint returned a draft, pre-release, or unexpected tag '$($dependencyCheckRelease.tag_name)'."
}
$latestDependencyCheck = $Matches[1]
Assert-Equal 'OWASP Dependency-Check stable' ([string]$policy.dependencyCheck) $latestDependencyCheck 'Update the scanner only after its official stable release and a successful cached offline scan.'

$dockerRelease = Get-OfficialJson 'Docker Engine latest release' 'https://api.github.com/repos/moby/moby/releases/latest'
if ([bool]$dockerRelease.prerelease -or [string]$dockerRelease.tag_name -notmatch '^docker-v([0-9]+\.[0-9]+\.[0-9]+)$') {
    throw "Docker Engine latest endpoint returned a pre-release or unexpected tag '$($dockerRelease.tag_name)'."
}
$latestDockerEngine = $Matches[1]
Assert-Equal 'Docker Engine stable' ([string]$policy.dockerEngine) $latestDockerEngine 'Upgrade Docker Desktop from its stable channel, then revalidate the project.'

$composeRelease = Get-OfficialJson 'Docker Compose latest release' 'https://api.github.com/repos/docker/compose/releases/latest'
if ([bool]$composeRelease.prerelease -or [string]$composeRelease.tag_name -notmatch '^v([0-9]+\.[0-9]+\.[0-9]+)$') {
    throw "Docker Compose latest endpoint returned a pre-release or unexpected tag '$($composeRelease.tag_name)'."
}
$latestCompose = $Matches[1]
Assert-Equal 'Docker Compose stable' ([string]$policy.dockerCompose) $latestCompose 'Upgrade Docker Desktop/Compose from its stable channel and rerun the gate.'

$springMetadataText = Get-OfficialText 'Spring Boot Maven Central metadata' 'https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml'
[xml]$springMetadata = $springMetadataText
$latestSpringBoot = Get-LatestStableVersion @($springMetadata.metadata.versioning.versions.version) '^\d+\.\d+\.\d+$' 'Spring Boot Maven Central metadata'
Assert-Equal 'Spring Boot stable' ([string]$policy.springBoot) $latestSpringBoot 'Update the parent only after migration, tests, and runtime verification.'

$mysqlPolicy = Get-OfficialText 'Oracle MySQL release model' 'https://docs.oracle.com/cd/E17952_01/mysql-9.7-en/mysql-releases.html'
if ($mysqlPolicy -notmatch '9\.7' -or $mysqlPolicy -notmatch '(?i)Long-Term Support|LTS') {
    throw 'Oracle documentation no longer confirms the selected MySQL 9.7 LTS line.'
}
$mysqlTags = @(Get-DockerHubTags 'mysql' "$($policy.mysql.ltsLine).")
$latestMysql = Get-LatestStableVersion $mysqlTags "^$([regex]::Escape([string]$policy.mysql.ltsLine))\.\d+$" 'MySQL Docker Official Image'
Assert-Equal 'MySQL LTS patch' ([string]$policy.mysql.version) $latestMysql 'Review the LTS patch upgrade and preserve existing named-volume data.'

$redisPolicy = Get-OfficialText 'Redis version management' 'https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/'
if ($redisPolicy -notmatch "Redis $([regex]::Escape([string]$policy.redis.extendedLine))" -or
    $redisPolicy -notmatch '(?i)Extended') {
    throw "Redis documentation no longer confirms the selected $($policy.redis.extendedLine) Extended line."
}
$redisReleaseIndex = Get-OfficialText 'Redis official source releases' 'https://download.redis.io/releases/'
$redisPattern = "redis-($([regex]::Escape([string]$policy.redis.extendedLine))\.\d+)\.tar\.gz"
$redisVersions = @([regex]::Matches($redisReleaseIndex, $redisPattern) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
$latestRedis = Get-LatestStableVersion $redisVersions "^$([regex]::Escape([string]$policy.redis.extendedLine))\.\d+$" 'Redis official source releases'
Assert-Equal 'Redis Extended patch' ([string]$policy.redis.version) $latestRedis 'Update the checksum-pinned Redis source build and validate persistence before switching.'

$nginxDownload = Get-OfficialText 'Nginx downloads' 'https://nginx.org/en/download.html'
$nginxMatch = [regex]::Match($nginxDownload, 'Stable version[\s\S]{0,1500}?nginx-([0-9]+\.[0-9]+\.[0-9]+)', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $nginxMatch.Success) { throw 'Nginx download page did not expose a stable release.' }
$latestNginx = $nginxMatch.Groups[1].Value
Assert-Equal 'Nginx stable' ([string]$policy.nginx) $latestNginx 'Update the runtime image only after proxy and security-header regression tests.'

$goReleases = @(Get-OfficialJson 'Go stable downloads' 'https://go.dev/dl/?mode=json')
if ($goReleases.Count -eq 0 -or [string]$goReleases[0].version -notmatch '^go([0-9]+\.[0-9]+\.[0-9]+)$') {
    throw 'Go downloads did not return a stable release.'
}
$latestGo = $Matches[1]
Assert-Equal 'Go upstream stable' ([string]$policy.go.stable) $latestGo 'Review the new stable toolchain and use it when the matching Docker Official Image is available.'
$goAlpine = [version][string]$policy.alpine
$goAlpineLine = "$($goAlpine.Major).$($goAlpine.Minor)"
$goDockerTags = @(Get-DockerHubTags 'golang' "alpine$goAlpineLine")
$goDockerCandidates = @()
foreach ($tag in $goDockerTags) {
    if ($tag -match "^(\d+\.\d+\.\d+)-alpine$([regex]::Escape($goAlpineLine))$") {
        $goDockerCandidates += [pscustomobject]@{ Name = $tag; Version = [version]$Matches[1] }
    }
}
$latestGoDocker = $goDockerCandidates | Sort-Object Version -Descending | Select-Object -First 1
if ($null -eq $latestGoDocker) {
    throw "Docker Official Image did not publish a stable Go Alpine $goAlpineLine tag."
}
Assert-Equal 'Go Docker Official Image' ([string]$policy.go.docker) ([string]$latestGoDocker.Version) 'Update the gosu builder only after the Docker Official Image is published and the database image is rebuilt.'

$alpineReleases = Get-OfficialText 'Alpine stable releases' 'https://alpinelinux.org/releases/'
$alpinePattern = "($($alpineBranch.Major)\.$($alpineBranch.Minor)\.\d+)"
$alpineVersions = @([regex]::Matches($alpineReleases, $alpinePattern) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
$latestAlpine = Get-LatestStableVersion $alpineVersions "^$($alpineBranch.Major)\.$($alpineBranch.Minor)\.\d+$" 'Alpine release branch'
Assert-Equal 'Alpine stable patch' ([string]$policy.alpine) $latestAlpine 'Update pinned Alpine images and rebuild after compatibility testing.'
} else {
    Write-Host '[INFO] Runtime startup uses the reviewed repository baseline without contacting upstream release channels. Run verify-version-policy.ps1 directly for the latest-version maintenance audit.' -ForegroundColor Cyan
}

$backendPom = Get-Content -LiteralPath (Join-Path $backendRoot 'pom.xml') -Raw
$mavenWrapper = Get-Content -LiteralPath (Join-Path $backendRoot '.mvn\wrapper\maven-wrapper.properties') -Raw
$backendDockerfile = Get-Content -LiteralPath (Join-Path $backendRoot 'Dockerfile') -Raw
$frontendPackage = Get-Content -LiteralPath (Join-Path $frontendRoot 'package.json') -Raw | ConvertFrom-Json
$frontendDockerfile = Get-Content -LiteralPath (Join-Path $frontendRoot 'Dockerfile') -Raw
$mysqlDockerfile = Get-Content -LiteralPath (Join-Path $projectRoot 'database\Dockerfile') -Raw
$redisDockerfile = Get-Content -LiteralPath (Join-Path $projectRoot 'redis\Dockerfile') -Raw
$compose = Get-Content -LiteralPath (Join-Path $projectRoot 'compose.yaml') -Raw

$springPin = [regex]::Match($backendPom, '<parent>[\s\S]*?<version>([^<]+)</version>').Groups[1].Value.Trim()
$javaPin = [regex]::Match($backendPom, '<java\.version>([^<]+)</java\.version>').Groups[1].Value.Trim()
$dependencyCheckPin = [regex]::Match($backendPom, '<dependency-check\.version>([^<]+)</dependency-check\.version>').Groups[1].Value.Trim()
$mavenPin = [regex]::Match($mavenWrapper, 'apache-maven-([0-9]+\.[0-9]+\.[0-9]+)-bin').Groups[1].Value
Assert-Equal 'pom.xml Spring Boot parent' ([string]$policy.springBoot) $springPin 'Align pom.xml with the reviewed stable baseline.'
Assert-Equal 'pom.xml Java release' ([string][int]$policy.java.ltsMajor) $javaPin 'Compile against the selected Java LTS line.'
Assert-Equal 'pom.xml Dependency-Check plugin' ([string]$policy.dependencyCheck) $dependencyCheckPin 'Align the scanner with the reviewed official stable release.'
Assert-Equal 'Maven Wrapper distribution' ([string]$policy.maven) $mavenPin 'Update maven-wrapper.properties from the official Maven distribution.'
Assert-Equal 'package.json package manager' "npm@$($policy.npm)" ([string]$frontendPackage.packageManager) 'Pin the exact stable npm version.'
if ([string]$frontendPackage.engines.node -notmatch [regex]::Escape([string]$policy.node) -or
    [string]$frontendPackage.engines.npm -notmatch [regex]::Escape([string]$policy.npm)) {
    throw 'package.json engines do not enforce the verified Node.js/npm baseline.'
}

$digests = $policy.dockerImageDigests
if ($backendDockerfile -notmatch "FROM eclipse-temurin:$([regex]::Escape([string]$policy.java.dockerJdkTag))@$([regex]::Escape([string]$digests.temurinJdk)) AS build" -or
    $backendDockerfile -notmatch "FROM eclipse-temurin:$([regex]::Escape([string]$policy.java.dockerJreTag))@$([regex]::Escape([string]$digests.temurinJre)) AS runtime-rootfs") {
    throw 'Backend Dockerfile does not pin the approved Temurin JDK/JRE tags and OCI digests.'
}
$alpineLine = "$($alpineBranch.Major).$($alpineBranch.Minor)"
if ($frontendDockerfile -notmatch "FROM node:$([regex]::Escape([string]$policy.node))-alpine$([regex]::Escape($alpineLine))@$([regex]::Escape([string]$digests.node)) AS build" -or
    $frontendDockerfile -notmatch "ADD --checksum=sha256:$([regex]::Escape([string]$policy.npmTarballSha256)) https://registry\.npmjs\.org/npm/-/npm-$([regex]::Escape([string]$policy.npm))\.tgz /tmp/npm\.tgz" -or
    $frontendDockerfile -notmatch 'tar -xzf /tmp/npm\.tgz -C /usr/local/lib/node_modules/npm --strip-components=1' -or
    $frontendDockerfile -notmatch 'ln -sf \.\./lib/node_modules/npm/bin/npm-cli\.js /usr/local/bin/npm' -or
    $frontendDockerfile -notmatch "FROM nginx:$([regex]::Escape([string]$policy.nginx))-alpine$([regex]::Escape($alpineLine))-slim@$([regex]::Escape([string]$digests.nginx)) AS runtime") {
    throw 'Frontend Dockerfile does not pin and extract the approved Node.js/npm tarball/Nginx/Alpine artifacts and digests.'
}
if ($mysqlDockerfile -notmatch "FROM golang:$([regex]::Escape([string]$policy.go.docker))-alpine$([regex]::Escape($alpineLine))@$([regex]::Escape([string]$digests.golang)) AS gosu-builder" -or
    $mysqlDockerfile -notmatch "FROM mysql:$([regex]::Escape([string]$policy.mysql.version))@$([regex]::Escape([string]$digests.mysql)) AS prepared" -or
    $mysqlDockerfile -notmatch 'microdnf upgrade -y curl-7\.76\.1-40\.el9_8\.5 libcurl-7\.76\.1-40\.el9_8\.5') {
    throw 'Database Dockerfile does not pin the approved Go/Alpine/MySQL LTS artifacts and reviewed Oracle security update.'
}
if ($redisDockerfile -notmatch "ARG REDIS_VERSION=$([regex]::Escape([string]$policy.redis.version))" -or
    $redisDockerfile -notmatch "ARG REDIS_DOWNLOAD_SHA=$([regex]::Escape([string]$policy.redis.sourceSha256))" -or
    $redisDockerfile -notmatch "FROM alpine:$([regex]::Escape([string]$policy.alpine))@$([regex]::Escape([string]$digests.alpine))") {
    throw 'Redis Dockerfile does not pin the approved source version, SHA-256, and Alpine OCI digest.'
}
if ($compose -notmatch "silverpilot-mysql:$([regex]::Escape([string]$policy.mysql.version))" -or
    $compose -notmatch "silverpilot-redis:$([regex]::Escape([string]$policy.redis.version))") {
    throw 'compose.yaml image tags drift from runtime-versions.json.'
}

$dockerVersions = @(& docker version --format '{{.Client.Version}}|{{.Server.Version}}' 2>$null)
if ($LASTEXITCODE -ne 0 -or $dockerVersions.Count -ne 1) {
    throw 'Docker client/server version could not be read even though the engine was expected to be ready.'
}
$dockerParts = $dockerVersions[0].Trim().Split('|')
$composeOutput = [string](& docker compose version 2>$null)
if ($LASTEXITCODE -ne 0 -or $composeOutput -notmatch 'v?([0-9]+\.[0-9]+\.[0-9]+)') {
    throw 'Docker Compose plugin is missing or returned an unrecognized version.'
}
$detectedComposeVersion = $Matches[1]
if ($RuntimeOnly) {
    Write-Host "[PASS] Docker CLI $($dockerParts[0]), Engine $($dockerParts[1]) and Compose $detectedComposeVersion are available; required commands are validated by the startup flow." -ForegroundColor Green
} else {
    Assert-Equal 'Docker CLI' ([string]$policy.dockerEngine) $dockerParts[0] 'Upgrade Docker Desktop from the stable channel.'
    Assert-Equal 'Docker Engine' ([string]$policy.dockerEngine) $dockerParts[1] 'Upgrade Docker Desktop from the stable channel.'
    Assert-Equal 'Docker Compose plugin' ([string]$policy.dockerCompose) $detectedComposeVersion 'Upgrade Docker Desktop/Compose from the stable channel.'
}

if ($Mode -eq 'Local' -and -not $SkipLocalHostChecks) {
    $javaPath = Get-CommandPath 'java.exe'
    $javacPath = Get-CommandPath 'javac.exe'
    $nodePath = Get-CommandPath 'node.exe'
    $npmPath = Get-CommandPath 'npm.cmd'
    $mysqlPath = Get-CommandPath 'mysql.exe'

    $javaProbe = Invoke-NativeCapture $javaPath @('-XshowSettings:properties', '-version')
    if ($javaProbe.ExitCode -ne 0) { throw 'The default Java executable exists but cannot start.' }
    $runtimeLine = $javaProbe.Output | Where-Object { $_ -match '^\s*java\.runtime\.version\s*=' } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace([string]$runtimeLine)) {
        throw 'The default Java runtime did not expose java.runtime.version.'
    }
    $javaRuntime = ([string]$runtimeLine -split '=', 2)[1].Trim() -replace '-LTS$', ''
    if ($RuntimeOnly) {
        $javaFeature = ([version]($javaRuntime.Split('+')[0])).Major
        Assert-Equal 'Default Java feature release' ([string][int]$policy.java.ltsMajor) ([string]$javaFeature) 'Use the supported Java LTS feature release and fix JAVA_HOME/PATH ordering.'
    } else {
        Assert-Equal 'Default Java runtime' ([string]$policy.java.hostRelease) $javaRuntime 'Make Java 25 LTS the global default and fix JAVA_HOME/PATH ordering.'
    }

    $javacProbe = Invoke-NativeCapture $javacPath @('-version')
    $javacOutput = $javacProbe.Output -join [Environment]::NewLine
    if ($javacProbe.ExitCode -ne 0 -or $javacOutput -notmatch 'javac\s+([0-9]+\.[0-9]+\.[0-9]+)') {
        throw 'The default javac executable is unavailable or returned an unrecognized version.'
    }
    $expectedJavaFeature = ([string]$policy.java.hostRelease).Split('+')[0]
    if ($RuntimeOnly) {
        Assert-Equal 'Default javac feature release' ([string][int]$policy.java.ltsMajor) ([string]([version]$Matches[1]).Major) 'Use the supported Java LTS compiler feature release.'
    } else {
        Assert-Equal 'Default javac' $expectedJavaFeature $Matches[1] 'Make the matching Java 25 JDK compiler the global default.'
    }

    $javaHome = Get-ScopedEnvironmentValue 'JAVA_HOME'
    if ([string]::IsNullOrWhiteSpace($javaHome)) { throw 'JAVA_HOME is not defined.' }
    $javaHomeExecutable = [System.IO.Path]::GetFullPath((Join-Path $javaHome 'bin\java.exe'))
    if (-not (Test-Path -LiteralPath $javaHomeExecutable) -or $javaHomeExecutable -cne $javaPath) {
        throw "JAVA_HOME resolves to '$javaHomeExecutable' while PATH resolves java.exe to '$javaPath'. Fix the global default before starting."
    }

    $nodeProbe = Invoke-NativeCapture $nodePath @('-p', 'process.versions.node')
    if ($nodeProbe.ExitCode -ne 0) { throw 'The default Node.js executable exists but cannot start.' }
    $nodeVersion = $nodeProbe.Output -join [Environment]::NewLine
    $npmProbe = Invoke-NativeCapture $npmPath @('--version')
    if ($npmProbe.ExitCode -ne 0) { throw 'The default npm executable exists but cannot start.' }
    $npmVersion = $npmProbe.Output -join [Environment]::NewLine
    if ($RuntimeOnly) {
        Assert-Equal 'Default Node.js major' ([string]([version][string]$policy.node).Major) ([string]([version]$nodeVersion.Trim()).Major) 'Use the supported Node.js LTS major release.'
        Assert-Equal 'Default npm major' ([string]([version][string]$policy.npm).Major) ([string]([version]$npmVersion.Trim()).Major) 'Use the supported npm major release.'
    } else {
        Assert-Equal 'Default Node.js' ([string]$policy.node) $nodeVersion.Trim() 'Install the current LTS and make it the PATH default.'
        Assert-Equal 'Default npm' ([string]$policy.npm) $npmVersion.Trim() 'Install the reviewed stable npm release.'
    }

    $wrapperPath = Join-Path $backendRoot 'mvnw.cmd'
    $wrapperProbe = Invoke-NativeCapture $wrapperPath @('-B', '-ntp', '--version')
    if ($wrapperProbe.ExitCode -ne 0) { throw 'Maven Wrapper is present but cannot run.' }
    $wrapperLine = $wrapperProbe.Output | Where-Object { $_ -match '^Apache Maven\s+' } | Select-Object -First 1
    if ([string]$wrapperLine -notmatch '^Apache Maven\s+([0-9]+\.[0-9]+\.[0-9]+)') {
        throw 'Maven Wrapper returned an unrecognized version.'
    }
    Assert-Equal 'Maven Wrapper runtime' ([string]$policy.maven) $Matches[1] 'Repair the wrapper distribution before building.'

    Push-Location $frontendRoot
    try {
        & $npmPath ls --depth=0 --silent *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend dependencies are missing or inconsistent. Startup made no repair; run 'cd SourceCode\cecsmsui-vue; npm ci', review the result, then retry."
        }
    } finally {
        Pop-Location
    }

    $databasePassword = Get-ScopedEnvironmentValue 'CECSMS_LOCAL_DB_PASSWORD'
    if ([string]::IsNullOrWhiteSpace($databasePassword)) { $databasePassword = Get-ScopedEnvironmentValue 'CECSMS_DB_PASSWORD' }
    if ([string]::IsNullOrWhiteSpace($databasePassword)) {
        throw 'CECSMS_LOCAL_DB_PASSWORD or CECSMS_DB_PASSWORD is required for the host MySQL preflight.'
    }
    $databaseUsername = Get-ScopedEnvironmentValue 'CECSMS_LOCAL_DB_USERNAME'
    if ([string]::IsNullOrWhiteSpace($databaseUsername)) { $databaseUsername = 'root' }
    $databaseUrl = Get-ScopedEnvironmentValue 'CECSMS_LOCAL_DB_URL'
    if ([string]::IsNullOrWhiteSpace($databaseUrl)) {
        $databaseUrl = 'jdbc:mysql://127.0.0.1:3306/a_old?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&sslMode=DISABLED&allowPublicKeyRetrieval=true'
    }
    if ($databaseUrl -notmatch '^jdbc:mysql://(?:127\.0\.0\.1|localhost|\[::1\]):3306/([^?]+)') {
        throw 'CECSMS_LOCAL_DB_URL must use loopback MySQL port 3306 and include a database name.'
    }
    $databaseName = $Matches[1]
    $previousMysqlPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $databasePassword
        $serverVersionOutput = @(& $mysqlPath --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$databaseUsername" --batch --skip-column-names '--execute=SELECT VERSION()' 2>$null)
        if ($LASTEXITCODE -ne 0 -or $serverVersionOutput.Count -eq 0) {
            throw "Host MySQL authentication/query failed for '$databaseUsername@$databaseName'. Verify CECSMS_LOCAL_DB_* without changing data."
        }
        $serverVersion = ([string]$serverVersionOutput[0]).Split('-')[0].Trim()
        if ($RuntimeOnly) {
            $expectedMysql = [version][string]$policy.mysql.version
            $actualMysql = [version]$serverVersion
            Assert-Equal 'Host MySQL LTS line' "$($expectedMysql.Major).$($expectedMysql.Minor)" "$($actualMysql.Major).$($actualMysql.Minor)" 'Use the supported MySQL LTS line; patch updates remain runtime-compatible after normal database backup checks.'
        } else {
            Assert-Equal 'Host MySQL server' ([string]$policy.mysql.version) $serverVersion 'Upgrade the host server only through a backed-up LTS migration.'
        }
        & $mysqlPath --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$databaseUsername" "--database=$databaseName" --batch --skip-column-names "--execute=SELECT 1 FROM ``user`` LIMIT 1" *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Host database '$databaseName' is missing the required user table or is not readable by '$databaseUsername'."
        }
    } finally {
        $env:MYSQL_PWD = $previousMysqlPassword
    }

    Write-Host "[PASS] Local tools and dependencies: Java $javaRuntime, Maven $($policy.maven), Node $nodeVersion, npm $npmVersion, MySQL $serverVersion." -ForegroundColor Green
} elseif ($Mode -eq 'Docker') {
    if ($RuntimeOnly) {
        Write-Host '[PASS] Pure Docker mode does not require host Java/Node/npm/MySQL; reviewed image tags and immutable digests are internally consistent.' -ForegroundColor Green
    } else {
        $latestTemurinJdk = Get-LatestTemurinResoluteTag ([int]$policy.java.ltsMajor) 'jdk'
        $latestTemurinJre = Get-LatestTemurinResoluteTag ([int]$policy.java.ltsMajor) 'jre'
        Assert-Equal 'Temurin Docker JDK tag' ([string]$policy.java.dockerJdkTag) $latestTemurinJdk 'Update the backend build image when the Docker Official Image publishes a newer Java 25 LTS patch.'
        Assert-Equal 'Temurin Docker JRE tag' ([string]$policy.java.dockerJreTag) $latestTemurinJre 'Update the backend runtime image when the Docker Official Image publishes a newer Java 25 LTS patch.'
        Assert-DockerOfficialTag 'node' "$($policy.node)-alpine$alpineLine"
        Assert-DockerOfficialTag 'nginx' "$($policy.nginx)-alpine$alpineLine-slim"
        Assert-DockerOfficialTag 'mysql' ([string]$policy.mysql.version)
        Assert-DockerOfficialTag 'golang' "$($policy.go.docker)-alpine$alpineLine"
        Assert-DockerOfficialTag 'alpine' ([string]$policy.alpine)
        Write-Host '[PASS] Pure Docker mode does not require host Java/Node/npm/MySQL; exact stable build/runtime pins and official image tags are verified.' -ForegroundColor Green
    }
} else {
    Write-Host '[PASS] Local infrastructure version gate passed; host Java/Node/npm/Maven/MySQL checks are reserved for the full one-click Local entry.' -ForegroundColor Green
}

if ($RuntimeOnly) {
    Write-Host "[PASS] Offline runtime compatibility policy passed for $Mode mode; upstream release freshness is intentionally decoupled from startup." -ForegroundColor Green
} else {
    Write-Host "[PASS] Explicit online version maintenance audit passed for $Mode mode; no RC/Beta/Preview component was accepted." -ForegroundColor Green
}
exit 0
