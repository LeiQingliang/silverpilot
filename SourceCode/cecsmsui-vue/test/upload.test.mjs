import test from 'node:test'
import assert from 'node:assert/strict'
import { validateImageUpload } from '../src/utils/upload.js'

test('image upload contract accepts supported bounded images', () => {
  assert.equal(validateImageUpload({ type: 'image/jpeg', size: 1024 }), '')
  assert.equal(validateImageUpload({ type: 'image/png', size: 2 * 1024 * 1024 }), '')
  assert.match(validateImageUpload({ type: 'image/webp', size: 1024 }), /仅支持/)
  assert.match(validateImageUpload({ type: 'image/gif', size: 2 * 1024 * 1024 + 1 }), /2MB/)
  assert.match(validateImageUpload({ type: 'image/gif', size: 0 }), /不能为空/)
})
