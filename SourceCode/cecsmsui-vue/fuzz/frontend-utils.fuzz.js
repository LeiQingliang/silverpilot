import assert from 'node:assert/strict'
import test from 'node:test'
import fc from 'fast-check'

import {
  normalizeAnalytics,
  normalizeCarePlan,
  sanitizeMessages
} from '../src/utils/agent.js'
import { optimizedImageUrl } from '../src/utils/media.js'
import { validateImageUpload } from '../src/utils/upload.js'

const configuredRuns = Number.parseInt(process.env.SILVERPILOT_FUZZ_RUNS || '2000', 10)
// A fixed seed keeps CI failures reproducible while fast-check still explores thousands of cases.
const propertyOptions = {
  numRuns: Number.isInteger(configuredRuns) && configuredRuns > 0
    ? Math.min(configuredRuns, 20_000)
    : 2000,
  seed: 20260820,
  endOnFailure: true
}

const hostileNumber = fc.oneof(
  fc.jsonValue(),
  fc.constant(Number.POSITIVE_INFINITY),
  fc.constant(Number.NEGATIVE_INFINITY),
  fc.constant(Number.NaN)
)

test('chat history sanitizer is bounded, canonical and idempotent', () => {
  fc.assert(fc.property(fc.jsonValue(), (input) => {
    const sanitized = sanitizeMessages(input)
    assert.ok(Array.isArray(sanitized))
    assert.ok(sanitized.length <= 30)
    for (const message of sanitized) {
      assert.ok(['user', 'assistant'].includes(message.role))
      assert.equal(typeof message.content, 'string')
      assert.equal(message.content, message.content.trim())
      assert.ok(message.content.length > 0)
      assert.ok(message.content.length <= 4000)
    }
    assert.deepEqual(sanitizeMessages(sanitized), sanitized)
  }), propertyOptions)
})

test('care-plan normalization bounds untrusted model output', () => {
  const recommendation = fc.record({
    serviceId: hostileNumber,
    serviceName: fc.string({ maxLength: 600 }),
    reason: fc.string({ maxLength: 900 }),
    nextStep: fc.string({ maxLength: 700 }),
    evidence: fc.array(fc.string({ maxLength: 700 }), { maxLength: 20 })
  })
  const payload = fc.record({
    runId: fc.string({ maxLength: 400 }),
    provider: fc.string({ maxLength: 300 }),
    model: fc.string({ maxLength: 300 }),
    promptVersion: fc.string({ maxLength: 300 }),
    totalTokens: hostileNumber,
    summary: fc.string({ maxLength: 600 }),
    urgency: fc.string({ maxLength: 40 }),
    recommendations: fc.array(fc.oneof(recommendation, fc.jsonValue()), { maxLength: 12 }),
    safetyNotice: fc.string({ maxLength: 900 }),
    disclaimer: fc.string({ maxLength: 700 })
  })

  fc.assert(fc.property(payload, (input) => {
    const plan = normalizeCarePlan(input)
    assert.notEqual(plan, null)
    assert.ok(plan.runId.length <= 120)
    assert.ok(plan.provider.length <= 80)
    assert.ok(plan.model.length <= 120)
    assert.ok(plan.promptVersion.length <= 80)
    assert.ok(Number.isFinite(plan.totalTokens) && plan.totalTokens >= 0)
    assert.ok(plan.summary.length <= 160)
    assert.ok(['low', 'medium', 'high', 'emergency'].includes(plan.urgency))
    assert.ok(plan.recommendations.length <= 3)
    assert.ok(plan.safetyNotice.length <= 300)
    assert.ok(plan.disclaimer.length <= 220)
    for (const item of plan.recommendations) {
      assert.ok(Number.isFinite(item.serviceId))
      assert.ok(item.serviceName.length <= 120)
      assert.ok(item.reason.length <= 300)
      assert.ok(item.nextStep.length <= 240)
      assert.ok(item.evidence.length <= 8)
      assert.ok(item.evidence.every((entry) => entry.length <= 300))
    }
  }), propertyOptions)
})

test('analytics normalization never emits invalid numeric boundaries', () => {
  const analytics = fc.record({
    days: hostileNumber,
    totalRuns: hostileNumber,
    successRate: hostileNumber,
    averageLatencyMs: hostileNumber,
    toolCalls: hostileNumber,
    totalTokens: hostileNumber,
    degradedRuns: hostileNumber,
    toolSuccessRate: hostileNumber,
    waitingConfirmation: hostileNumber
  })

  fc.assert(fc.property(analytics, (input) => {
    const normalized = normalizeAnalytics(input)
    for (const key of [
      'days',
      'totalRuns',
      'successRate',
      'averageLatencyMs',
      'toolCalls',
      'totalTokens',
      'degradedRuns',
      'toolSuccessRate',
      'waitingConfirmation'
    ]) {
      assert.ok(Number.isFinite(normalized[key]), `${key} must be finite`)
      assert.ok(normalized[key] >= 0, `${key} must be non-negative`)
    }
    assert.ok(normalized.days >= 1)
    assert.ok(normalized.successRate <= 100)
    assert.ok(normalized.toolSuccessRate <= 100)
  }), propertyOptions)
})

test('upload validation accepts only supported, finite and bounded images', () => {
  const supportedType = fc.constantFrom('image/jpeg', 'image/png', 'image/gif')
  const invalidType = fc.string()
    .filter((type) => !['image/jpeg', 'image/png', 'image/gif'].includes(type))
  const invalidSize = fc.oneof(
    fc.integer({ min: -1_000_000, max: 0 }),
    fc.integer({ min: 2 * 1024 * 1024 + 1, max: 3 * 1024 * 1024 }),
    fc.constant(Number.POSITIVE_INFINITY),
    fc.constant(Number.NaN)
  )
  const uploadCase = fc.oneof(
    fc.tuple(supportedType, fc.integer({ min: 1, max: 2 * 1024 * 1024 }))
      .map(([type, size]) => ({ file: { type, size }, valid: true })),
    fc.tuple(invalidType, hostileNumber)
      .map(([type, size]) => ({ file: { type, size }, valid: false })),
    fc.tuple(supportedType, invalidSize)
      .map(([type, size]) => ({ file: { type, size }, valid: false }))
  )

  fc.assert(fc.property(uploadCase, ({ file, valid }) => {
    assert.equal(validateImageUpload(file) === '', valid)
  }), propertyOptions)
})

test('image URL optimization is deterministic and idempotent', () => {
  fc.assert(fc.property(fc.string({ maxLength: 1000 }), (input) => {
    const optimized = optimizedImageUrl(input)
    assert.equal(typeof optimized, 'string')
    assert.equal(optimizedImageUrl(optimized), optimized)
    if (/^(?:data|blob):/i.test(input.trim())) assert.equal(optimized, input.trim())
  }), propertyOptions)
})
