param([switch]$Full)

$ErrorActionPreference = 'Stop'
$skillRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $skillRoot '..\..\..'))
if ($Full) { & (Join-Path $projectRoot 'scripts\verify-project.ps1') }
else { & (Join-Path $projectRoot 'scripts\validate-knowledge-base.ps1') }
$result = $LASTEXITCODE
if ($result -ne 0) { exit $result }

$required = @(
    'SourceCode\cecsmsServe-springboot\src\main\java\com\cecsmsserve\controller\ChatController.java',
    'SourceCode\cecsmsServe-springboot\src\main\java\com\cecsmsserve\controller\McpController.java',
    'SourceCode\cecsmsui-vue\src\components\front\ai\AiChat.vue',
    'workbuddy\agent-manifest.json'
)
foreach ($relativePath in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $relativePath))) {
        Write-Error "Missing required SilverPilot surface: $relativePath"
    }
}
Write-Host '[PASS] SilverPilot structure and knowledge governance are valid.' -ForegroundColor Green
exit 0
