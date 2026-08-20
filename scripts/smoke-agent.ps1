param(
    [string]$BaseUrl = 'http://127.0.0.1:8083',
    [int]$UserId = 17,
    [string]$EnvFile = ''
)

$ErrorActionPreference = 'Stop'
function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
function New-LocalJwt([string]$Secret, [int]$SubjectId) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = '{"alg":"HS256","typ":"JWT"}'
    $payloadJson = @{ iss = 'cecsms-serve'; sub = [string]$SubjectId; aud = @([string]$SubjectId); iat = $now; exp = $now + 1800 } | ConvertTo-Json -Compress
    $unsigned = (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))) + '.' + (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson)))
    $hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
    try { $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($unsigned))) }
    finally { $hmac.Dispose() }
    return "$unsigned.$signature"
}

function Get-EnvFileValue([string]$Path, [string]$Name) {
    if (-not $Path -or -not (Test-Path -LiteralPath $Path)) { return $null }
    $line = Get-Content -LiteralPath $Path -Encoding utf8 | Where-Object { $_ -match "^$([regex]::Escape($Name))=" } | Select-Object -Last 1
    if (-not $line) { return $null }
    return $line.Substring($line.IndexOf('=') + 1)
}
$jwtSecret = if ($EnvFile) { Get-EnvFileValue $EnvFile 'SILVERPILOT_JWT_SECRET' } else { [Environment]::GetEnvironmentVariable('CECSMS_JWT_SECRET', 'User') }
$mcpKey = if ($EnvFile) { Get-EnvFileValue $EnvFile 'SILVERPILOT_MCP_API_KEY' } else { [Environment]::GetEnvironmentVariable('CECSMS_MCP_API_KEY', 'User') }
if ([string]::IsNullOrWhiteSpace($jwtSecret) -or $jwtSecret.Length -lt 32) { Write-Error 'CECSMS_JWT_SECRET is missing.' }
if ([string]::IsNullOrWhiteSpace($mcpKey)) { Write-Error 'CECSMS_MCP_API_KEY is missing.' }
$token = New-LocalJwt $jwtSecret $UserId
$authHeaders = @{ Authorization = "Bearer $token" }
$mcpHeaders = @{ 'X-CECSMS-MCP-Key' = $mcpKey; Accept = 'application/json, text/event-stream' }

$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
if ($health.status -ne 'UP') { Write-Error 'Backend health is not UP.' }
$status = Invoke-RestMethod -Uri "$BaseUrl/chat/status" -Headers $authHeaders -TimeoutSec 10
if (-not $status.configured -or $status.capabilityCount -lt 13 -or -not $status.knowledge.ready) {
    Write-Error 'Agent status, capabilities, or knowledge base is not ready.'
}
if (-not $status.prompts.agent.version -or -not $status.rateLimit.backend) {
    Write-Error 'Prompt version or rate-limit backend observability is missing.'
}
if ($status.knowledge.PSObject.Properties.Name -contains 'root') {
    Write-Error 'Agent status must not expose the server knowledge-base filesystem path.'
}
$doubao = @($status.providers | Where-Object { $_.id -eq 'doubao' }) | Select-Object -First 1
if ($doubao -and -not $doubao.configured) {
    $visionRequest = @{
        messages = @(@{ role = 'user'; content = '请描述这张测试图片' })
        provider = 'auto'
        attachments = @(@{
            name = 'one-pixel.png'; mimeType = 'image/png'
            dataUrl = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='
        })
    } | ConvertTo-Json -Depth 10
    $visionStatus = $null
    try {
        Invoke-RestMethod -Uri "$BaseUrl/chat" -Method Post -Headers $authHeaders `
            -ContentType 'application/json; charset=utf-8' `
            -Body ([Text.Encoding]::UTF8.GetBytes($visionRequest)) -TimeoutSec 15 | Out-Null
        Write-Error 'Unconfigured vision request unexpectedly succeeded.'
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $visionStatus = [int]$_.Exception.Response.StatusCode
        }
    }
    if ($visionStatus -ne 503) { Write-Error "Unconfigured vision boundary returned HTTP $visionStatus instead of 503." }
}

$knowledgeRequest = @{
    messages = @(@{ role = 'user'; content = '这是一次知识库检索验收。请必须先调用 search_knowledge_base，查询“写操作人工确认安全策略”，然后保留 [KB:...] 来源回答。' })
    provider = 'deepseek'; attachments = @()
} | ConvertTo-Json -Depth 10
$knowledgeResponse = Invoke-RestMethod -Uri "$BaseUrl/chat" -Method Post -Headers $authHeaders `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($knowledgeRequest)) -TimeoutSec 90
if (-not $knowledgeResponse.success -or $knowledgeResponse.run.provider -ne 'deepseek' -or $knowledgeResponse.run.toolCalls -lt 1) {
    Write-Error 'Grounded Agent knowledge run did not complete with a tool call.'
}
if ($knowledgeResponse.run.totalTokens -lt 1 -or -not $knowledgeResponse.run.promptVersion) {
    Write-Error 'Live Agent run did not report token usage and prompt version.'
}

$careRequest = @{
    needs = '老人行动不便，希望获得安全的上门助浴支持，请给出风险提示和可核验下一步。'
    provider = 'deepseek'
} | ConvertTo-Json
$carePlan = Invoke-RestMethod -Uri "$BaseUrl/chat/care-plan" -Method Post -Headers $authHeaders `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($careRequest)) -TimeoutSec 90
if (-not $carePlan.runId -or $carePlan.recommendations.Count -lt 1 -or $carePlan.totalTokens -lt 1) {
    Write-Error 'Structured care plan did not return a grounded real-service recommendation.'
}
if (@($carePlan.recommendations[0].evidence | Where-Object { $_ -like 'BUSINESS:*' }).Count -lt 1) {
    Write-Error 'Structured care plan is missing a validated business service evidence ID.'
}

$serviceCall = @{
    jsonrpc = '2.0'; id = 20; method = 'tools/call'
    params = @{ name = 'cecsms_list_services'; arguments = @{} }
} | ConvertTo-Json -Depth 8
$serviceResponse = Invoke-RestMethod -Uri "$BaseUrl/mcp" -Method Post -Headers $mcpHeaders `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($serviceCall)) -TimeoutSec 15
$serviceText = $serviceResponse.result.content[0].text
$serviceMatch = [regex]::Match($serviceText, 'ID\s+(\d+)')
if (-not $serviceMatch.Success) { Write-Error 'MCP did not return an active service ID.' }
$serviceId = [int]$serviceMatch.Groups[1].Value
$tomorrow = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$ordersBefore = Invoke-RestMethod -Uri "$BaseUrl/serviceOrder/selectByUId/$UserId" -Headers $authHeaders -TimeoutSec 15
if ($ordersBefore.code -ne 200) { Write-Error 'Could not snapshot service orders before the pending-action check.' }
$orderIdsBefore = @($ordersBefore.result | ForEach-Object { [string]$_.id } | Sort-Object)
$writePrompt = "请预约服务ID $serviceId，日期 $tomorrow，地址为测试小区1号楼101室。信息已完整，请创建待确认操作。"
$writeRequest = @{ messages = @(@{ role = 'user'; content = $writePrompt }); provider = 'deepseek'; attachments = @() } | ConvertTo-Json -Depth 10
$writeResponse = Invoke-RestMethod -Uri "$BaseUrl/chat" -Method Post -Headers $authHeaders `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($writeRequest)) -TimeoutSec 90
if (-not $writeResponse.success -or $writeResponse.pendingAction.status -ne 'PENDING') {
    Write-Error 'Agent did not create a pending write action.'
}
$confirmationToken = $writeResponse.pendingAction.confirmationToken
$cancelResponse = Invoke-RestMethod -Uri "$BaseUrl/chat/actions/$confirmationToken/cancel" -Method Post -Headers $authHeaders -ContentType 'application/json' -TimeoutSec 15
if (-not $cancelResponse.success -or $cancelResponse.action.status -ne 'CANCELLED') {
    Write-Error 'Pending action could not be safely cancelled.'
}
$ordersAfter = Invoke-RestMethod -Uri "$BaseUrl/serviceOrder/selectByUId/$UserId" -Headers $authHeaders -TimeoutSec 15
if ($ordersAfter.code -ne 200) { Write-Error 'Could not verify service orders after cancelling the pending action.' }
$orderIdsAfter = @($ordersAfter.result | ForEach-Object { [string]$_.id } | Sort-Object)
if (Compare-Object -ReferenceObject $orderIdsBefore -DifferenceObject $orderIdsAfter) {
    Write-Error 'Cancelling the pending action changed persisted service orders.'
}

$analytics = Invoke-RestMethod -Uri "$BaseUrl/chat/analytics?days=7" -Headers $authHeaders -TimeoutSec 10
if ($analytics.totalRuns -lt 3 -or $analytics.toolCalls -lt 2 -or $analytics.totalTokens -lt 1) { Write-Error 'Agent analytics did not include smoke runs and token usage.' }
Write-Host "[PASS] Live Agent: provider usage, versioned prompt, grounded knowledge, structured care plan, pending write cancellation with unchanged business orders, MCP, and analytics verified." -ForegroundColor Green
exit 0
