import assert from 'node:assert/strict'
import { execFile } from 'node:child_process'
import { createServer } from 'node:http'
import { mkdtemp, writeFile, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const exec = promisify(execFile)
const runner = fileURLToPath(new URL('../run-agent-evaluation.ps1', import.meta.url))

async function evaluate(response, { trials = 1, cancelFails = false, vision = false,
  provider = 'longcat', evidenceType = 'synthetic', expectedTool = null } = {}) {
  const dir = await mkdtemp(path.join(tmpdir(), 'silverpilot-eval-'))
  let cancellations = 0
  const server = createServer(async (request, reply) => {
    for await (const _chunk of request) { /* consume body */ }
    reply.setHeader('Content-Type', 'application/json')
    if (request.url === '/chat/status') {
      reply.end(JSON.stringify({ providers: [{ id: 'longcat', vision: false }], prompts: { agent: { version: 'test' } } }))
    } else if (request.url.endsWith('/cancel')) {
      cancellations++
      reply.statusCode = cancelFails ? 500 : 200
      reply.end(JSON.stringify({ success: !cancelFails }))
    } else {
      reply.end(JSON.stringify(response))
    }
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  try {
    const env = path.join(dir, '.env')
    const dataset = path.join(dir, 'cases.jsonl')
    const report = path.join(dir, 'report.json')
    await writeFile(env, 'SILVERPILOT_JWT_SECRET=' + 'synthetic-only-'.repeat(4))
    await writeFile(dataset, JSON.stringify({
      id: vision ? 'multimodal-001' : 'test-001', category: 'synthetic', risk: 'low',
      userMessage: 'Synthetic test', requiresConfirmation: Boolean(response.pendingAction),
      expectedTool: response.pendingAction ? 'book_service' : expectedTool,
      expectedBehavior: vision ? 'route-only-to-vision-provider' : 'respect-eight-iteration-limit',
    }))
    let code = 0
    try {
      await exec('pwsh', ['-NoProfile', '-File', runner, '-BaseUrl',
        'http://127.0.0.1:' + server.address().port, '-Provider', provider,
        '-EnvFile', env, '-Dataset', dataset, '-ReportPath', report,
        '-Trials', String(trials), '-EvidenceType', evidenceType], { timeout: 30000 })
    } catch (error) {
      code = error.code
      if (typeof code !== 'number') throw error
    }
    return { code, report: JSON.parse((await readFile(report, 'utf8')).replace(/^\uFEFF/, '')), cancellations }
  } finally {
    await new Promise(resolve => server.close(resolve))
    await rm(dir, { recursive: true, force: true })
  }
}

const completed = () => ({
  success: true, reply: 'Synthetic answer', trace: [],
  run: { runId: 'synthetic-run', provider: 'longcat', model: 'stub-only',
    status: 'COMPLETED', latencyMs: 123, llmCalls: 1, toolCalls: 0, totalTokens: 20, promptVersion: 'test' },
})

test('records repeated trials and evidence context without saving raw reply', async () => {
  const result = await evaluate(completed(), { trials: 2 })
  assert.equal(result.code, 0)
  assert.equal(result.report.executed, 2)
  assert.equal(result.report.evidenceType, 'synthetic')
  assert.deepEqual(result.report.results.map(row => row.trial), [1, 2])
  assert.equal(result.report.results[0].model, 'stub-only')
  assert.equal(result.report.results[0].latencyMs, 123)
  assert.match(result.report.provenance.datasetSha256, /^[A-F0-9]{64}$/)
  assert.equal(JSON.stringify(result.report).includes('Synthetic answer'), false)
})

test('rejects degraded responses instead of counting fallback as provider success', async () => {
  const response = completed()
  response.run.status = 'DEGRADED'
  const result = await evaluate(response)
  assert.equal(result.code, 1)
  assert.equal(result.report.passed, 0)
})

test('rejects a silent provider substitution', async () => {
  const response = completed()
  response.run.provider = 'mock'
  assert.equal((await evaluate(response)).report.passed, 0)
})

test('does not label auto-routed mock evidence as live', async () => {
  const response = completed()
  response.run.provider = 'mock'
  const result = await evaluate(response, { provider: 'auto', evidenceType: 'live' })
  assert.equal(result.code, 0)
  assert.equal(result.report.evidenceType, 'mock')
})

test('requires the expected read tool to complete successfully', async () => {
  const response = completed()
  response.trace = [{ code: 'TOOL:list_services', status: 'failed' }]
  assert.equal((await evaluate(response, { expectedTool: 'list_services' })).report.passed, 0)
  response.trace[0].status = 'completed'
  assert.equal((await evaluate(response, { expectedTool: 'list_services' })).report.passed, 1)
})

test('accepts explicit loop guardrail but rejects exceeding the eight-call contract', async () => {
  const response = completed()
  response.success = false
  response.error = '对话处理超过安全迭代次数，请稍后重试'
  response.run.status = 'FAILED'
  response.run.llmCalls = 8
  assert.equal((await evaluate(response)).report.passed, 1)
  response.run.llmCalls = 9
  assert.equal((await evaluate(response)).report.passed, 0)
})

test('cancels every pending action and fails if cleanup fails', async () => {
  const response = completed()
  response.run.status = 'WAITING_CONFIRMATION'
  response.pendingAction = { status: 'PENDING', confirmationToken: 'synthetic-token', toolName: 'book_service' }
  response.trace = [{ code: 'TOOL:book_service', status: 'waiting' }]
  const good = await evaluate(response, { trials: 2 })
  assert.equal(good.report.passed, 2)
  assert.equal(good.cancellations, 2)
  const bad = await evaluate(response, { cancelFails: true })
  assert.equal(bad.report.passed, 0)
})

test('rejects non-vision provider on image case using advertised capabilities', async () => {
  assert.equal((await evaluate(completed(), { vision: true })).report.passed, 0)
})
