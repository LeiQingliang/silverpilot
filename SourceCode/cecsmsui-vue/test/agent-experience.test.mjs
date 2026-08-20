import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const aiChat = await readFile(new URL('../src/components/front/ai/AiChat.vue', import.meta.url), 'utf8')
const operations = await readFile(new URL('../src/components/serve/agent/AgentOperationsView.vue', import.meta.url), 'utf8')
const router = await readFile(new URL('../src/router/index.js', import.meta.url), 'utf8')
const nginx = await readFile(new URL('../docker/nginx.conf', import.meta.url), 'utf8')
const prompt = await readFile(new URL('../../cecsmsServe-springboot/src/main/resources/prompts/silverpilot-agent-v3.md', import.meta.url), 'utf8')

test('assistant workspace supports explicit microphone permission and camera capture', () => {
  assert.match(aiChat, /getUserMedia\(\{ audio: true \}\)/)
  assert.match(aiChat, /facingMode: \{ ideal: 'environment' \}/)
  assert.match(aiChat, /capturePhoto/)
  assert.match(aiChat, /webkitSpeechRecognition/)
  assert.match(aiChat, /xiaoban-care-coordinator\.webp/)
})

test('assistant copy uses the human service identity without cockpit language', () => {
  assert.match(aiChat, /小伴生活服务助理/)
  assert.match(aiChat, /不用研究菜单/)
  assert.doesNotMatch(aiChat, /AGENTIC|LIVE SESSION|驾驶舱/)
  assert.doesNotMatch(router, /Agent 驾驶舱|运营驾驶舱/)
  assert.doesNotMatch(aiChat, />\s*Token\s*</)
})

test('administrator has a guarded operations route with WorkBuddy and IMA visibility', () => {
  assert.match(router, /name: 'AgentOperationsView'/)
  assert.match(router, /meta: \{ roles: \[ROLE_ADMIN\]/)
  assert.match(operations, /WorkBuddy 连接/)
  assert.match(operations, /IMA 知识库/)
  assert.match(operations, /suppressErrorToast: true/)
})

test('production policy allows same-origin camera and microphone only', () => {
  assert.match(nginx, /Permissions-Policy "camera=\(self\), geolocation=\(\), microphone=\(self\)"/)
  assert.doesNotMatch(nginx, /camera=\(\)/)
})

test('versioned prompt handles fuzzy requests and avoids canned AI phrasing', () => {
  assert.match(prompt, /version: 3\.0\.0/)
  assert.match(prompt, /模糊指令处理/)
  assert.match(prompt, /只读查询缺少筛选条件时/)
  assert.match(prompt, /不写“作为 AI”/)
})
