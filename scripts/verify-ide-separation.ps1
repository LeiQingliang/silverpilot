$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backendRoot = Join-Path $projectRoot 'SourceCode\cecsmsServe-springboot'
$frontendRoot = Join-Path $projectRoot 'SourceCode\cecsmsui-vue'

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Assert-TextMatches([string]$Text, [string]$Pattern, [string]$Message) {
    if ($Text -notmatch $Pattern) { throw $Message }
}

function Assert-TextDoesNotMatch([string]$Text, [string]$Pattern, [string]$Message) {
    if ($Text -match $Pattern) { throw $Message }
}

$backendPomPath = Join-Path $backendRoot 'pom.xml'
$backendPropertiesPath = Join-Path $backendRoot 'src\main\resources\application.properties'
$frontendPackagePath = Join-Path $frontendRoot 'package.json'
$frontendVitePath = Join-Path $frontendRoot 'vite.config.js'
$vscodeTasksPath = Join-Path $frontendRoot '.vscode\tasks.json'
$composePath = Join-Path $projectRoot 'compose.yaml'
$projectLauncherPath = Join-Path $PSScriptRoot 'start-project.ps1'
$dockerDevPath = Join-Path $PSScriptRoot 'docker-dev.ps1'
$projectStopPath = Join-Path $PSScriptRoot 'stop-project.ps1'
$localPreparationCmdPath = Join-Path $projectRoot 'start-local.cmd'
$backendCompatibilityPath = Join-Path $PSScriptRoot 'start-local-backend.ps1'
$frontendLauncherPath = Join-Path $PSScriptRoot 'start-local-frontend.ps1'

foreach ($requiredPath in @(
    $backendPomPath,
    $backendPropertiesPath,
    $frontendPackagePath,
    $frontendVitePath,
    $vscodeTasksPath,
    $composePath,
    $projectLauncherPath,
    $dockerDevPath,
    $projectStopPath,
    $localPreparationCmdPath,
    $backendCompatibilityPath,
    $frontendLauncherPath
)) {
    Assert-Condition (Test-Path -LiteralPath $requiredPath) "Separation gate input is missing: $requiredPath"
}

$backendPom = Get-Content -LiteralPath $backendPomPath -Raw
Assert-TextDoesNotMatch $backendPom '(?i)frontend-maven-plugin|cecsmsui-vue|<artifactId>.*(?:node|npm)' `
    'Backend pom.xml must not build, embed, or launch the Vue frontend.'
foreach ($embeddedFrontendPath in @(
    (Join-Path $backendRoot 'src\main\resources\static\index.html'),
    (Join-Path $backendRoot 'src\main\resources\public\index.html'),
    (Join-Path $backendRoot 'src\main\frontend')
)) {
    Assert-Condition (-not (Test-Path -LiteralPath $embeddedFrontendPath)) `
        "Frontend runtime content must not be embedded in the backend module: $embeddedFrontendPath"
}

$backendProperties = Get-Content -LiteralPath $backendPropertiesPath -Raw
Assert-TextMatches $backendProperties '(?m)^server\.port=\$\{CECSMS_SERVER_PORT:8083\}\r?$' `
    'Backend must keep its independent default port 8083.'
Assert-TextMatches $backendProperties 'http://127\.0\.0\.1:8081' `
    'Backend CORS defaults must explicitly allow the independent Vite origin on 8081.'

$frontendPackage = Get-Content -LiteralPath $frontendPackagePath -Raw | ConvertFrom-Json
$frontendScripts = @($frontendPackage.scripts.PSObject.Properties | ForEach-Object { [string]$_.Value }) -join "`n"
Assert-TextMatches ([string]$frontendPackage.scripts.predev) 'verify-local-backend-ready\.mjs' `
    'Frontend predev must verify, but never start, the IDEA backend.'
Assert-TextMatches ([string]$frontendPackage.scripts.dev) 'run-vite-dev\.mjs|(?:^|\s)vite(?:\s|$)' `
    'Frontend dev must launch only Vite.'
Assert-TextDoesNotMatch $frontendScripts '(?i)(?:^|[\\/\s])(?:java|java\.exe|mvn|mvnw|spring-boot)(?:[\\/\s.:]|$)|start-local-backend|start-project' `
    'Frontend npm scripts must not build, start, or stop the Java backend.'

$vscodeTasks = Get-Content -LiteralPath $vscodeTasksPath -Raw | ConvertFrom-Json
$taskText = @($vscodeTasks.tasks | ForEach-Object {
    ([string]$_.label) + "`n" + ([string]$_.command) + "`n" + (@($_.args) -join "`n")
}) -join "`n"
Assert-TextDoesNotMatch $taskText '(?i)start-local-backend|start-project|docker-dev|(?:^|[\\/\s])(?:java|java\.exe|mvn|mvnw)(?:[\\/\s.:]|$)' `
    'VSCode tasks must own only frontend actions and must not start backend or infrastructure processes.'
$devTask = @($vscodeTasks.tasks | Where-Object { $_.label -eq 'Frontend: dev server' })
Assert-Condition ($devTask.Count -eq 1) 'VSCode must expose exactly one Frontend: dev server task.'
Assert-Condition (([string]$devTask[0].command) -eq 'npm.cmd') 'VSCode frontend task must invoke npm directly.'
Assert-Condition ((@($devTask[0].args) -join ' ') -eq 'run dev') 'VSCode frontend task must execute npm run dev only.'
Assert-Condition ($null -eq $devTask[0].dependsOn) 'VSCode frontend task must not auto-start backend or Docker dependencies.'

$viteConfig = Get-Content -LiteralPath $frontendVitePath -Raw
Assert-TextMatches $viteConfig "backendTarget\s*=.*http://127\.0\.0\.1:8083" `
    'Vite must proxy to the independent backend on 8083 by default.'
Assert-TextMatches $viteConfig '/api' 'Vite must proxy the /api boundary.'
Assert-TextMatches $viteConfig 'server:\s*\{[\s\S]*?port:\s*8081' 'Vite must keep its independent development port 8081.'
Assert-TextMatches $viteConfig 'strictPort:\s*true' 'Vite must fail rather than silently changing its frontend port.'

$compose = Get-Content -LiteralPath $composePath -Raw
Assert-TextMatches $compose '(?m)^\s{2}backend:\s*$' 'Compose must retain a distinct backend service.'
Assert-TextMatches $compose '(?m)^\s{2}frontend:\s*$' 'Compose must retain a distinct frontend service.'
Assert-TextMatches $compose 'context:\s*\./SourceCode/cecsmsServe-springboot' 'Compose backend must build from the backend module only.'
Assert-TextMatches $compose 'context:\s*\./SourceCode/cecsmsui-vue' 'Compose frontend must build from the frontend module only.'

$projectLauncher = Get-Content -LiteralPath $projectLauncherPath -Raw
Assert-TextDoesNotMatch $projectLauncher '(?i)start-local-(?:backend|frontend)\.ps1' `
    'The root Local entry must prepare infrastructure only; it must not launch Java or Vite.'
Assert-TextDoesNotMatch $projectLauncher '(?i)Start-Process[^\r\n]*(?:java|npm)' `
    'The root launcher must not create local Java or npm processes.'

$dockerDev = Get-Content -LiteralPath $dockerDevPath -Raw
Assert-TextMatches $dockerDev 'file\.image-base-path=.*ConvertTo-JavaPropertyValue\s+\$projectRoot' `
    'Generated IDEA host configuration must pin the current project media root instead of inheriting another checkout''s CECSMS_UPLOAD_DIR.'

$projectStop = Get-Content -LiteralPath $projectStopPath -Raw
Assert-TextDoesNotMatch $projectStop '(?i)stop-local-(?:backend|frontend)\.ps1|Stop-Process|Get-CimInstance\s+Win32_Process' `
    'The root stop command must manage Docker only and must never inspect or stop IDEA/VSCode processes.'
$localPreparationCmd = Get-Content -LiteralPath $localPreparationCmdPath -Raw
Assert-TextMatches $localPreparationCmd 'start-project\.ps1"\s+-Mode\s+Local' `
    'start-local.cmd must delegate to the infrastructure-only Local preparation mode.'
Assert-TextDoesNotMatch $localPreparationCmd '(?i)WaitForStop|OpenBrowser|start-local-(?:backend|frontend)' `
    'start-local.cmd must not claim ownership of an IDE application session.'

$backendCompatibility = Get-Content -LiteralPath $backendCompatibilityPath -Raw
Assert-TextDoesNotMatch $backendCompatibility '(?i)Start-Process|&\s*(?:java|java\.exe)|-FilePath\s+java' `
    'The compatibility backend command must verify IDEA state and must never launch Java.'
$frontendLauncher = Get-Content -LiteralPath $frontendLauncherPath -Raw
Assert-TextDoesNotMatch $frontendLauncher '(?i)Start-Process|\[switch\]\$Detached' `
    'The frontend helper must remain foreground and VSCode-owned; detached Vite startup is forbidden.'

$managedPidFile = Join-Path $backendRoot 'target\local-backend.pid'
Assert-Condition (-not (Test-Path -LiteralPath $managedPidFile)) `
    "A legacy script-managed backend PID file still exists: $managedPidFile"

Write-Host '[PASS] Strict separation gate passed: IDEA owns Java/8083, VSCode owns Vite/8081, and local scripts own only infrastructure checks.' -ForegroundColor Green
exit 0
