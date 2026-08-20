import test from 'node:test'
import assert from 'node:assert/strict'
import {
  actionStatusText,
  formatLatency,
  normalizeAnalytics,
  normalizeAction,
  normalizeCarePlan,
  normalizeProvider,
  normalizeRun,
  normalizeTrace,
  sanitizeMessages
} from '../src/utils/agent.js'

test('sanitizeMessages keeps only valid bounded conversation messages', () => {
  const input = [
    { role: 'system', content: 'hidden' },
    { role: 'user', content: '  你好  ' },
    { role: 'assistant', content: '好的' },
    { role: 'user', content: '' },
    null
  ]

  assert.deepEqual(sanitizeMessages(input), [
    { role: 'user', content: '你好' },
    { role: 'assistant', content: '好的' }
  ])
})

test('provider, run and trace responses are normalized without leaking extra fields', () => {
  assert.deepEqual(normalizeProvider({ id: 'doubao', name: '豆包', vision: 1, configured: true }), {
    id: 'doubao', name: '豆包', model: '', configured: true,
    text: false, vision: true, serverVoice: false, statusText: ''
  })
  assert.equal(normalizeProvider({}), null)
  assert.deepEqual(normalizeRun({ runId: 'abc', latencyMs: '1200', toolCalls: 2 }), {
    runId: 'abc', provider: 'unresolved', model: '', inputModality: 'TEXT', status: 'UNKNOWN',
    latencyMs: 1200, llmCalls: 0, toolCalls: 2, totalTokens: 0, promptVersion: '',
    confirmationRequired: false
  })
  assert.deepEqual(normalizeTrace([{ title: '调用工具', detail: '完成', hidden: 'drop' }]), [{
    code: 'STEP', title: '调用工具', status: 'completed', detail: '完成', timestamp: null
  }])
})

test('analytics and latency helpers provide bounded display values', () => {
  assert.deepEqual(normalizeAnalytics({ successRate: 120, totalRuns: '3', providers: { deepseek: 3 } }), {
    days: 7, totalRuns: 3, successRate: 100, averageLatencyMs: 0, toolCalls: 0,
    totalTokens: 0, degradedRuns: 0, toolSuccessRate: 0, waitingConfirmation: 0,
    providers: { deepseek: 3 }, modalities: {}, recentRuns: []
  })
  assert.equal(formatLatency(830), '830 ms')
  assert.equal(formatLatency(2450), '2.5 s')
  const hostileValue = Object.assign(Object.create(null), { toString: true })
  assert.equal(normalizeAnalytics({ totalRuns: hostileValue, successRate: Infinity }).totalRuns, 0)
  assert.equal(normalizeAnalytics({ totalRuns: hostileValue, successRate: Infinity }).successRate, 0)
  assert.equal(formatLatency(hostileValue), '0 ms')
})

test('normalizeCarePlan bounds model output and keeps verifiable service evidence', () => {
  const plan = normalizeCarePlan({
    runId: 'run-1', provider: 'deepseek', urgency: 'unexpected', totalTokens: '42',
    recommendations: [{ serviceId: '9', serviceName: '上门助浴', reason: '行动不便', nextStep: '人工核验', evidence: ['BUSINESS:9', 'KB:safe'] }]
  })
  assert.equal(plan.urgency, 'medium')
  assert.equal(plan.totalTokens, 42)
  assert.deepEqual(plan.recommendations[0].evidence, ['BUSINESS:9', 'KB:safe'])
  assert.equal(normalizeCarePlan({ summary: 'missing run' }), null)
})

test('normalizeAction rejects malformed input and strips unexpected fields', () => {
  assert.equal(normalizeAction(null), null)
  assert.equal(normalizeAction({ id: 'invalid' }), null)
  assert.deepEqual(normalizeAction({
    id: '7',
    confirmationToken: 'token',
    summary: '预约服务',
    status: 'PENDING',
    argumentsJson: 'must-not-leak'
  }), {
    id: 7,
    confirmationToken: 'token',
    toolName: '',
    summary: '预约服务',
    status: 'PENDING',
    resultMessage: null,
    expiresAt: null,
    createdAt: null,
    executedAt: null
  })
})

test('action status labels are readable and tolerate unknown values', () => {
  assert.equal(actionStatusText('SUCCEEDED'), '已完成')
  assert.equal(actionStatusText('something-new'), '未知状态')
})
