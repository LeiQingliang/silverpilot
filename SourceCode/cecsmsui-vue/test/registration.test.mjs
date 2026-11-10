import test from 'node:test'
import assert from 'node:assert/strict'
import { createRegistrationPayload } from '../src/utils/registration.js'

test('registration sends only public DTO fields and the current one-time challenge', () => {
  const input = {
    username: '  test_member  ', name: ' Test Member ', password: '  independent-test-password  ',
    telephone: ' test-phone ', idNum: ' test-identity ', sex: '男', address: ' Test Address ',
    verificationCode: ' aB7Z ', captchaId: 'stale-challenge',
    id: 1, roleId: 1, role: 1, point: 999, department: 'private', token: 'untrusted', checkPass: 'confirmation'
  }
  const payload = createRegistrationPayload(input, 'current-challenge')
  assert.deepEqual(payload, {
    username: 'test_member', name: 'Test Member', password: '  independent-test-password  ',
    telephone: 'test-phone', idNum: 'test-identity', sex: '男', address: 'Test Address',
    captchaId: 'current-challenge', verificationCode: 'aB7Z'
  })
  assert.equal(input.username, '  test_member  ')
})

test('registration never fabricates an answer or retains unrelated fields', () => {
  const payload = createRegistrationPayload({ username: 'tester', roleId: 1 }, 'challenge')
  assert.equal(payload.verificationCode, undefined)
  assert.equal(Object.hasOwn(payload, 'roleId'), false)
  assert.equal(payload.captchaId, 'challenge')
})
