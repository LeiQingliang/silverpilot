import test from 'node:test'
import assert from 'node:assert/strict'
import {
  getBackendErrorMessage,
  isCanceledRequest,
  resolveGlobalHttpFeedback
} from '../src/utils/http-error.js'

test('canceled requests never produce a false network failure toast', () => {
  const error = { code: 'ERR_CANCELED', name: 'CanceledError' }
  assert.equal(isCanceledRequest(error), true)
  assert.equal(resolveGlobalHttpFeedback(error), null)
})

test('backend CommonResult message is preserved for server errors', () => {
  const error = { response: { status: 503, data: { msg: '模型通道暂时不可用' } } }
  assert.equal(getBackendErrorMessage(error), '模型通道暂时不可用')
  assert.equal(resolveGlobalHttpFeedback(error), '模型通道暂时不可用')
})

test('rate limit, timeout and offline failures have actionable Chinese messages', () => {
  assert.equal(
    resolveGlobalHttpFeedback({ response: { status: 429, data: {} } }),
    '操作过于频繁，请稍后重试'
  )
  assert.equal(resolveGlobalHttpFeedback({ code: 'ECONNABORTED' }), '请求超时，请稍后重试')
  assert.equal(resolveGlobalHttpFeedback({ code: 'ERR_NETWORK' }), '无法连接服务器')
})

test('validation responses stay with the calling form instead of creating duplicate toasts', () => {
  assert.equal(resolveGlobalHttpFeedback({ response: { status: 400, data: { msg: '参数无效' } } }), null)
})
