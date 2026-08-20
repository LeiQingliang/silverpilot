import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const publicHeader = await readFile(
  new URL('../src/components/front/home/FrontHeaderMenu.vue', import.meta.url),
  'utf8'
)
const loginView = await readFile(new URL('../src/views/loginView.vue', import.meta.url), 'utf8')
const registerView = await readFile(new URL('../src/views/registerView.vue', import.meta.url), 'utf8')

test('login and registration pages hide duplicate header authentication buttons', () => {
  assert.match(
    publicHeader,
    /const isAuthenticationPage = computed\(\(\) => route\.name === 'login' \|\| route\.name === 'register'\)/
  )
  assert.match(publicHeader, /v-if="!isLoggedIn && !isAuthenticationPage" class="auth-buttons"/)
  assert.match(publicHeader, /v-else-if="isLoggedIn" class="selectmenu"/)
})

test('authentication pages retain one primary action and one cross-page link', () => {
  assert.equal((loginView.match(/native-type="submit"/g) || []).length, 1)
  assert.equal(
    (loginView.match(
      /<el-button link type="primary" @click="register" class="register-link">\s*还没有账号？立即注册\s*<\/el-button>/g
    ) || []).length,
    1
  )
  assert.equal((registerView.match(/native-type="submit"/g) || []).length, 1)
  assert.equal(
    (registerView.match(
      /<button class="login-link" type="button" @click="toLogin">已有账号？返回登录<\/button>/g
    ) || []).length,
    1
  )
})
