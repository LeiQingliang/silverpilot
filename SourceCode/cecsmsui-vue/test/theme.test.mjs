import test from 'node:test'
import assert from 'node:assert/strict'
import { applyThemeToDocument, resolveInitialTheme } from '../src/composables/useTheme.js'

test('theme resolution is intentionally fixed to light mode', () => {
  assert.equal(resolveInitialTheme('light', true), 'light')
  assert.equal(resolveInitialTheme('dark', false), 'light')
  assert.equal(resolveInitialTheme('invalid', true), 'light')
})

test('theme application rejects dark requests and updates the document to light', () => {
  const target = { documentElement: { dataset: {}, style: {} } }
  assert.equal(applyThemeToDocument('dark', target), 'light')
  assert.equal(target.documentElement.dataset.theme, 'light')
  assert.equal(target.documentElement.style.colorScheme, 'light')
})
