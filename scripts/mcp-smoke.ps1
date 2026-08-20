param(
    [string]$Endpoint = 'http://127.0.0.1:8083/mcp',
    [string]$ApiKey = $env:CECSMS_MCP_API_KEY
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    $ApiKey = [Environment]::GetEnvironmentVariable('CECSMS_MCP_API_KEY', 'User')
}
if ([string]::IsNullOrWhiteSpace($ApiKey)) { Write-Error 'CECSMS_MCP_API_KEY is not configured.' }
$headers = @{ Authorization = "Bearer $ApiKey"; Accept = 'application/json, text/event-stream' }
$initialize = @{
    jsonrpc = '2.0'; id = 1; method = 'initialize'
    params = @{ protocolVersion = '2025-03-26'; capabilities = @{}; clientInfo = @{ name = 'silverpilot-smoke'; version = '1.0.0' } }
} | ConvertTo-Json -Depth 8
$initResponse = Invoke-RestMethod -Uri $Endpoint -Method Post -Headers $headers -ContentType 'application/json' -Body $initialize
if ($initResponse.result.serverInfo.name -ne 'silverpilot-cecsms') { Write-Error 'Unexpected MCP server.' }
if ($initResponse.result.protocolVersion -ne '2025-03-26') { Write-Error 'Unexpected MCP protocol version.' }
$listRequest = @{ jsonrpc = '2.0'; id = 2; method = 'tools/list'; params = @{} } | ConvertTo-Json -Depth 5
$listResponse = Invoke-RestMethod -Uri $Endpoint -Method Post -Headers $headers -ContentType 'application/json' -Body $listRequest
$toolCount = @($listResponse.result.tools).Count
if ($toolCount -ne 6) { Write-Error "Expected exactly 6 MCP tools, got $toolCount." }
if (@($listResponse.result.tools | Where-Object {
    -not $_.annotations.readOnlyHint -or $_.annotations.destructiveHint
}).Count -ne 0) { Write-Error 'Every exposed MCP tool must be explicitly read-only and non-destructive.' }
Write-Host "[PASS] MCP initialize and tools/list succeeded with $toolCount read-only tools." -ForegroundColor Green
exit 0
