import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const readSource = (relativePath) => readFileSync(resolve(projectRoot, relativePath), 'utf8')

test('login storage keeps only the minimum non-sensitive user profile', () => {
  const source = readSource('src/views/loginView.vue')

  assert.match(source, /const sessionUser = \{[\s\S]*?id: user\.id,[\s\S]*?roleId: user\.roleId,[\s\S]*?username: user\.username,[\s\S]*?name: user\.name[\s\S]*?\}/)
  assert.match(source, /sessionStorage\.setItem\('user', JSON\.stringify\(sessionUser\)\)/)
  assert.doesNotMatch(source, /sessionStorage\.setItem\('birthday'/)
  assert.doesNotMatch(source, /sessionStorage\.setItem\('user', JSON\.stringify\(user\)\)/)
})

test('profile updates do not copy sensitive form fields into browser storage', () => {
  const source = readSource('src/components/front/personal/PersonalCenter.vue')

  assert.doesNotMatch(source, /Object\.assign\(user, payload\)/)
  assert.match(source, /user\.name = payload\.name/)
})
