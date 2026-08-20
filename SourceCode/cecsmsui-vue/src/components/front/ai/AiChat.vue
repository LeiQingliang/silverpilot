<template>
  <FrontHeaderMenu />
  <main class="companion-page">
    <section class="companion-intro" aria-labelledby="companion-title">
      <div :class="['companion-portrait', { busy: loading, listening: recording }]" aria-hidden="true">
        <img :src="companionPortrait" alt="" />
        <span class="presence-dot"></span>
      </div>
      <div class="companion-copy">
        <span class="companion-label">小伴 · 生活服务助理</span>
        <h1 id="companion-title">告诉我想办什么，我来把步骤接起来</h1>
        <p>活动、上门服务、健康报告和膳食安排都可以用日常说法交代。需要提交或取消的操作，我会先整理好，再请你确认。</p>
      </div>
      <div class="availability" aria-label="助理服务状态">
        <span :class="agentStatus.configured ? 'ready' : 'waiting'"><i></i>{{ statusLabel }}</span>
        <span :class="visionReady ? 'ready' : 'waiting'"><i></i>{{ visionReady ? '看图可用' : '看图待配置' }}</span>
      </div>
    </section>

    <div class="companion-workspace">
      <section class="conversation-card" aria-labelledby="conversation-title">
        <header class="conversation-heading">
          <div>
            <span>{{ loading ? '正在办理' : '当前对话' }}</span>
            <h2 id="conversation-title">{{ loading ? thinkingStage : '今天需要我帮你做什么？' }}</h2>
          </div>
          <div class="conversation-actions">
            <button v-if="speechOutputSupported" type="button" :class="{ active: autoSpeak }" @click="autoSpeak = !autoSpeak">
              {{ autoSpeak ? '自动朗读：开' : '自动朗读：关' }}
            </button>
            <button type="button" :disabled="loading || messages.length === 0" @click="clearHistory">清空对话</button>
          </div>
        </header>

        <div class="quick-task-row" aria-label="常用任务">
          <button
            v-for="item in quickPrompts"
            :key="item.prompt"
            type="button"
            :disabled="loading || Boolean(pendingAction)"
            @click="sendQuickPrompt(item.prompt)"
          >
            <span aria-hidden="true">{{ item.icon }}</span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.caption }}</small>
          </button>
        </div>

        <div ref="messageList" class="message-list" aria-live="polite" :aria-busy="loading">
          <div v-if="messages.length === 0 && !pendingAction" class="welcome-state">
            <img :src="companionPortrait" alt="小伴生活服务助理" />
            <div>
              <h3>你好，我是小伴。</h3>
              <p>不用研究菜单，也不用把话说得很完整。比如“这周想出去活动一下”“帮我找个上门服务”，我会先补齐必要信息，再直接帮你推进。</p>
              <span>涉及费用、报名、预约或取消时，确认前不会改动任何业务数据。</span>
            </div>
          </div>

          <article v-for="(msg, index) in messages" :key="`${msg.role}-${index}`" :class="['message', msg.role]">
            <div class="message-avatar" aria-hidden="true">
              <img v-if="msg.role === 'assistant'" :src="companionPortrait" alt="" />
              <span v-else>{{ userInitial }}</span>
            </div>
            <div class="message-body">
              <div class="message-meta">
                <strong>{{ msg.role === 'user' ? '你' : '小伴' }}</strong>
                <time>{{ msg.time || '' }}</time>
              </div>
              <div class="message-content">{{ msg.content }}</div>
              <div v-if="Array.isArray(msg.attachments) && msg.attachments.length" class="message-attachments">
                <span v-for="name in msg.attachments" :key="name">图片：{{ name }}</span>
              </div>
              <div v-if="msg.role === 'assistant'" class="reply-actions">
                <button
                  v-if="speechOutputSupported"
                  type="button"
                  :aria-pressed="speechMessageIndex === index && speechState === 'speaking'"
                  @click="toggleSpeech(msg.content, index)"
                >
                  {{ speechButtonLabel(index) }}
                </button>
                <span v-if="msg.run">{{ providerName(msg.run.provider) }} · {{ formatLatency(msg.run.latencyMs) }}</span>
              </div>
            </div>
          </article>

          <article v-if="pendingAction" class="confirmation-card" aria-labelledby="confirmation-title">
            <div class="confirmation-heading">
              <span aria-hidden="true">✓</span>
              <div><small>最后一步</small><strong id="confirmation-title">请确认是否执行</strong></div>
            </div>
            <p>{{ pendingAction.summary }}</p>
            <small>有效期至 {{ formatActionTime(pendingAction.expiresAt) }}。确认前不会修改业务数据。</small>
            <div class="confirmation-actions">
              <el-button type="primary" :loading="actionLoading" @click="confirmPendingAction">确认并执行</el-button>
              <el-button :disabled="actionLoading" @click="cancelPendingAction">暂不执行</el-button>
            </div>
          </article>

          <article v-if="loading" class="message assistant active-run">
            <div class="message-avatar assistant-thinking" aria-hidden="true">
              <img :src="companionPortrait" alt="" />
            </div>
            <div class="message-body">
              <div class="message-meta"><strong>小伴</strong><time>正在处理</time></div>
              <div class="thinking-card">
                <span>{{ thinkingStage }}</span>
                <i></i><i></i><i></i>
              </div>
            </div>
          </article>
        </div>

        <div v-if="attachments.length" class="attachment-tray" aria-label="待发送图片">
          <article v-for="attachment in attachments" :key="attachment.id">
            <img :src="attachment.dataUrl" alt="待发送图片预览" />
            <div><strong>{{ attachment.name }}</strong><small>{{ fileSize(attachment.size) }}</small></div>
            <button type="button" @click="removeAttachment(attachment.id)" aria-label="移除图片">移除</button>
          </article>
        </div>

        <form class="composer" @submit.prevent="submitMessage">
          <input ref="fileInput" class="sr-only" type="file" accept="image/jpeg,image/png,image/webp" multiple @change="addFiles" />
          <label class="sr-only" for="companion-input">告诉小伴要办理的事情</label>
          <textarea
            id="companion-input"
            ref="inputTextarea"
            v-model="userInput"
            rows="2"
            maxlength="2000"
            :placeholder="recording ? '正在听，请继续说…' : '直接说要办的事，例如：帮我找一个适合腿脚不便老人的上门服务'"
            :disabled="loading"
            @keydown.enter.exact.prevent="submitMessage"
          ></textarea>
          <div class="composer-bottom">
            <div class="media-actions">
              <button type="button" :disabled="loading || attachments.length >= 3" @click="fileInput?.click()">图片</button>
              <button type="button" :disabled="loading || attachments.length >= 3 || !cameraSupported" @click="openCamera">拍照</button>
              <button
                type="button"
                :class="{ recording }"
                :disabled="loading || !speechInputSupported"
                @click="toggleRecording"
              >{{ recording ? '停止听写' : '语音输入' }}</button>
            </div>
            <span class="voice-status" role="status">{{ voiceStatus || providerHint }}</span>
            <span class="character-count">{{ userInput.length }}/2000</span>
            <el-button v-if="loading" type="danger" plain @click="stopRequest">停止</el-button>
            <el-button v-else native-type="submit" type="primary" :disabled="!userInput.trim() && attachments.length === 0">发送并办理</el-button>
          </div>
          <p v-if="attachments.length && !visionReady" class="vision-notice">图片已保留在本次对话中。管理员配置视觉模型后才能发送分析；DeepSeek 文本通道本身不读取图片。</p>
        </form>
      </section>

      <aside class="context-panel" aria-label="办理信息">
        <section class="context-section care-entry">
          <span>需要更完整的安排？</span>
          <h2>生成照护建议</h2>
          <p>结合当前服务目录和知识库，整理风险、可选服务与下一步，不会自动下单。</p>
          <el-button type="primary" plain :disabled="loading || Boolean(pendingAction)" @click="openCarePlan">开始整理</el-button>
        </section>

        <section class="context-section">
          <header><div><span>当前进度</span><h2>办理过程</h2></div></header>
          <div v-if="trace.length === 0" class="context-empty">发出需求后，这里会显示查找、核对和执行进度。</div>
          <ol v-else class="trace-list">
            <li v-for="(item, index) in trace" :key="`${item.code}-${index}`" :class="item.status">
              <span>{{ index + 1 }}</span>
              <div><strong>{{ item.title }}</strong><p>{{ item.detail }}</p></div>
            </li>
          </ol>
          <details v-if="lastRun" class="run-details">
            <summary>查看本次技术详情</summary>
            <dl>
              <div><dt>服务引擎</dt><dd>{{ providerName(lastRun.provider) }}</dd></div>
              <div><dt>处理用时</dt><dd>{{ formatLatency(lastRun.latencyMs) }}</dd></div>
              <div><dt>业务工具</dt><dd>{{ lastRun.toolCalls }} 次</dd></div>
              <div><dt>输入类型</dt><dd>{{ lastRun.inputModality === 'TEXT' ? '文字' : '图片与文字' }}</dd></div>
            </dl>
          </details>
        </section>

        <section class="context-section connection-panel">
          <header><div><span>服务连接</span><h2>能力状态</h2></div></header>
          <ul>
            <li><span :class="agentStatus.configured ? 'online' : 'offline'"></span><div><strong>文字办理</strong><small>{{ agentStatus.configured ? `${agentStatus.capabilityCount || 13} 项业务能力可用` : '模型尚未配置' }}</small></div></li>
            <li><span :class="knowledgeReady ? 'online' : 'offline'"></span><div><strong>IMA 知识库</strong><small>{{ knowledgeReady ? `${knowledgeCount} 篇受控资料` : '等待资料或审批' }}</small></div></li>
            <li><span :class="mcpConfigured ? 'online' : 'offline'"></span><div><strong>WorkBuddy</strong><small>{{ mcpConfigured ? '只读业务工具已连接' : '等待管理员配置密钥' }}</small></div></li>
            <li><span :class="visionReady ? 'online' : 'offline'"></span><div><strong>图片识别</strong><small>{{ visionReady ? '视觉通道可用' : '需要单独的视觉模型' }}</small></div></li>
          </ul>
          <label for="provider-select">服务引擎（高级设置）</label>
          <select id="provider-select" v-model="selectedProvider" :disabled="loading || carePlanLoading">
            <option value="auto">自动选择</option>
            <option v-for="provider in providers" :key="provider.id" :value="provider.id" :disabled="!provider.configured">
              {{ provider.name }}{{ provider.configured ? '' : '（未配置）' }}
            </option>
          </select>
        </section>

        <section class="context-section recent-actions">
          <header>
            <div><span>最近记录</span><h2>已办理事项</h2></div>
            <button type="button" :disabled="historyLoading" @click="loadActions(true)">刷新</button>
          </header>
          <div v-if="actionHistory.length === 0" class="context-empty">暂时没有办理记录。</div>
          <ol v-else>
            <li v-for="action in actionHistory.slice(0, 5)" :key="action.id">
              <span :class="['action-dot', actionStatusTone(action.status)]"></span>
              <div><strong>{{ action.summary }}</strong><small>{{ formatActionTime(action.createdAt) }}</small></div>
              <em :class="actionStatusTone(action.status)">{{ actionStatusText(action.status) }}</em>
            </li>
          </ol>
          <div class="weekly-summary">
            <span>近 {{ analytics.days }} 天</span>
            <strong>{{ analytics.totalRuns }} 次协助 · 成功率 {{ analytics.successRate.toFixed(1) }}%</strong>
          </div>
        </section>
      </aside>
    </div>

    <el-dialog
      v-model="cameraOpen"
      title="拍摄一张照片"
      width="min(720px, calc(100vw - 24px))"
      append-to-body
      destroy-on-close
      align-center
      @closed="stopCamera"
    >
      <div class="camera-workspace">
        <div class="camera-preview">
          <video v-show="!cameraError" ref="cameraVideo" autoplay muted playsinline></video>
          <div v-if="cameraLoading" class="camera-state">正在连接摄像头…</div>
          <div v-else-if="cameraError" class="camera-state error">{{ cameraError }}</div>
        </div>
        <canvas ref="cameraCanvas" class="sr-only"></canvas>
        <p>照片会在浏览器中压缩，只随本次请求发送。请勿拍摄身份证、银行卡或无关隐私信息。</p>
      </div>
      <template #footer>
        <el-button @click="cameraOpen = false">取消</el-button>
        <el-button type="primary" :disabled="cameraLoading || Boolean(cameraError)" @click="capturePhoto">使用这张照片</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="carePlanOpen"
      title="整理照护建议"
      width="min(760px, calc(100vw - 24px))"
      append-to-body
      destroy-on-close
      align-center
    >
      <div class="care-plan-workbench">
        <p class="care-plan-intro">请描述老人的当前需求、行动能力、照护偏好和需要注意的情况。建议只会引用系统现有服务和已审批知识，不会自动创建订单。</p>
        <label for="care-needs-input">需要解决的问题</label>
        <textarea
          id="care-needs-input"
          v-model="careNeeds"
          maxlength="1200"
          rows="5"
          placeholder="例如：老人 78 岁，行动不便，近期想找安全的上门助浴服务，请帮我梳理风险和下一步。"
        ></textarea>
        <div class="care-plan-actions">
          <span>{{ careNeeds.length }}/1200</span>
          <el-button type="primary" :loading="carePlanLoading" :disabled="careNeeds.trim().length < 5" @click="generateCarePlan">整理建议</el-button>
        </div>
        <el-skeleton v-if="carePlanLoading" :rows="5" animated />
        <section v-else-if="carePlan" class="care-plan-result" aria-live="polite">
          <header><div><small>需求摘要</small><h3>{{ carePlan.summary }}</h3></div><el-tag effect="plain">{{ urgencyLabel(carePlan.urgency) }}</el-tag></header>
          <div v-if="carePlan.recommendations.length" class="recommendation-list">
            <article v-for="item in carePlan.recommendations" :key="item.serviceId">
              <h4>{{ item.serviceName }} <small>#{{ item.serviceId }}</small></h4>
              <p>{{ item.reason }}</p>
              <dl><dt>下一步</dt><dd>{{ item.nextStep }}</dd></dl>
              <div class="evidence-tags"><span v-for="source in item.evidence" :key="source">来源：{{ source }}</span></div>
            </article>
          </div>
          <div v-else class="care-plan-empty"><strong>当前没有匹配到可核验服务</strong><p>{{ carePlan.safetyNotice }}</p></div>
          <div class="care-safety"><strong>安全提醒</strong><p>{{ carePlan.safetyNotice }}</p><small>{{ carePlan.disclaimer }}</small></div>
        </section>
      </div>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import FrontHeaderMenu from '@/components/front/home/FrontHeaderMenu.vue'
import companionPortrait from '@/assets/assistant/xiaoban-care-coordinator.webp'
import api from '@/utils/axios'
import {
  actionStatusText,
  actionStatusTone,
  formatActionTime,
  formatLatency,
  getApiError,
  normalizeAction,
  normalizeAnalytics,
  normalizeCarePlan,
  normalizeProvider,
  normalizeRun,
  normalizeTrace,
  sanitizeMessages
} from '@/utils/agent'

const router = useRouter()
const userInput = ref('')
const messages = ref([])
const attachments = ref([])
const providers = ref([])
const selectedProvider = ref('auto')
const loading = ref(false)
const actionLoading = ref(false)
const historyLoading = ref(false)
const analyticsLoading = ref(false)
const messageList = ref(null)
const inputTextarea = ref(null)
const fileInput = ref(null)
const pendingAction = ref(null)
const actionHistory = ref([])
const trace = ref([])
const lastRun = ref(null)
const thinkingStage = ref('正在理解你的需求')
const agentStatus = ref({ loaded: false, configured: false, capabilityCount: 0, knowledge: null, mcpConfigured: false })
const analytics = ref(normalizeAnalytics(null))
const speechInputSupported = ref(false)
const speechOutputSupported = ref(false)
const recording = ref(false)
const voiceStatus = ref('')
const autoSpeak = ref(false)
const speechState = ref('idle')
const speechMessageIndex = ref(null)
const cameraSupported = ref(false)
const cameraOpen = ref(false)
const cameraLoading = ref(false)
const cameraError = ref('')
const cameraVideo = ref(null)
const cameraCanvas = ref(null)
const carePlanOpen = ref(false)
const carePlanLoading = ref(false)
const careNeeds = ref('')
const carePlan = ref(null)
let activeController = null
let thinkingTimer = null
let recognition = null
let cameraStream = null
let speechBaseInput = ''
let attachmentSequence = 0
let activeUtterance = null
let speechToken = 0

const quickPrompts = [
  { icon: '活', label: '找活动', caption: '看看最近适合参加什么', prompt: '帮我看看最近有什么适合参加的活动，我还没想好具体类型' },
  { icon: '服', label: '找服务', caption: '按需求挑选上门服务', prompt: '我想找一个合适的养老服务，请先根据我的情况帮我选' },
  { icon: '餐', label: '安排膳食', caption: '查菜谱与可预订餐食', prompt: '最近不知道吃什么更合适，帮我看看系统里的健康菜谱' },
  { icon: '健', label: '看报告', caption: '读取已有健康记录', prompt: '帮我看看最近的健康报告，先说清楚已有数据，不要替医生诊断' }
]
const thinkingStages = ['正在理解你的需求', '正在查找相关服务', '正在核对可用信息', '正在整理下一步', '正在做安全检查']

const token = sessionStorage.getItem('token') || localStorage.getItem('token')
const userId = sessionStorage.getItem('id') || localStorage.getItem('id') || 'current-user'
const historyKey = `chat_history_${userId}`
const storedUser = (() => {
  try { return JSON.parse(sessionStorage.getItem('user') || localStorage.getItem('user') || '{}') }
  catch { return {} }
})()
const userInitial = computed(() => String(storedUser.name || storedUser.username || '你').trim().charAt(0) || '你')

const statusLabel = computed(() => {
  if (!agentStatus.value.loaded) return '正在连接'
  return agentStatus.value.configured ? '文字办理可用' : '文字服务待配置'
})
const knowledgeReady = computed(() => Boolean(agentStatus.value.knowledge?.ready))
const knowledgeCount = computed(() => Number(agentStatus.value.knowledge?.approvedDocuments) || 0)
const mcpConfigured = computed(() => Boolean(agentStatus.value.mcpConfigured))
const visionReady = computed(() => providers.value.some((provider) => provider.configured && provider.vision))
const selectedProviderInfo = computed(() => providers.value.find((item) => item.id === selectedProvider.value))
const providerHint = computed(() => {
  if (!speechInputSupported.value) return '语音输入需要最新版 Edge 或 Chrome，并允许麦克风权限'
  if (attachments.value.length) return visionReady.value ? '图片将在发送前压缩，并交给已配置的视觉服务处理' : '图片识别等待管理员配置视觉服务'
  if (selectedProvider.value === 'auto') return '会根据文字或图片自动选择可用服务'
  return selectedProviderInfo.value?.statusText || '服务引擎由后台安全托管'
})

const nowLabel = () => new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date())
const providerName = (id) => providers.value.find((item) => item.id === id)?.name || id || '服务引擎'
const fileSize = (bytes) => bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
const urgencyLabel = (urgency) => ({ low: '常规关注', medium: '建议关注', high: '优先处理', emergency: '立即求助' }[urgency] || '待核验')

const humanizeReply = (value) => String(value || '')
  .replace(/^#{1,6}\s+/gm, '')
  .replace(/\*\*/g, '')
  .replace(/^(?:作为(?:一个)?(?:AI|人工智能)[^，。]*[，,：:]?\s*)/i, '')
  .replace(/^[*-]\s+/gm, '')
  .trim()

const loadHistory = () => {
  try { messages.value = sanitizeMessages(JSON.parse(sessionStorage.getItem(historyKey) || '[]')) }
  catch { messages.value = []; sessionStorage.removeItem(historyKey) }
}
const saveHistory = () => sessionStorage.setItem(historyKey, JSON.stringify(sanitizeMessages(messages.value)))
const scrollToBottom = async () => {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

const loadStatus = async () => {
  try {
    const { data } = await api.get('/chat/status', { suppressErrorToast: true })
    providers.value = Array.isArray(data?.providers) ? data.providers.map(normalizeProvider).filter(Boolean) : []
    agentStatus.value = {
      loaded: true,
      configured: Boolean(data?.configured),
      capabilityCount: Number(data?.capabilityCount) || 0,
      knowledge: data?.knowledge || null,
      mcpConfigured: Boolean(data?.mcpConfigured)
    }
  } catch {
    agentStatus.value = { loaded: true, configured: false, capabilityCount: 0, knowledge: null, mcpConfigured: false }
  }
}

const loadAnalytics = async (showError = false) => {
  analyticsLoading.value = true
  try {
    const { data } = await api.get('/chat/analytics', { params: { days: 7 }, suppressErrorToast: true })
    analytics.value = normalizeAnalytics(data)
  } catch (error) {
    if (showError) ElMessage.error(getApiError(error, '近期协助情况暂时无法读取'))
  } finally {
    analyticsLoading.value = false
  }
}

const loadActions = async (showError = false) => {
  historyLoading.value = true
  try {
    const { data } = await api.get('/chat/actions', { params: { limit: 10 }, suppressErrorToast: true })
    if (!data?.success || !Array.isArray(data.actions)) throw new Error(data?.error || '办理记录格式无效')
    actionHistory.value = data.actions.map(normalizeAction).filter(Boolean)
    pendingAction.value = actionHistory.value.find((action) => action.status === 'PENDING' && action.confirmationToken) || null
  } catch (error) {
    if (showError) ElMessage.error(getApiError(error, '办理记录暂时无法读取'))
  } finally {
    historyLoading.value = false
  }
}

const startThinking = () => {
  let index = 0
  thinkingStage.value = thinkingStages[index]
  clearInterval(thinkingTimer)
  thinkingTimer = setInterval(() => {
    index = Math.min(index + 1, thinkingStages.length - 1)
    thinkingStage.value = thinkingStages[index]
  }, 1500)
}
const stopThinking = () => { clearInterval(thinkingTimer); thinkingTimer = null }
const submitMessage = () => sendMessage(userInput.value)
const sendQuickPrompt = (prompt) => sendMessage(prompt)
const openCarePlan = () => { carePlan.value = null; carePlanOpen.value = true }

const generateCarePlan = async () => {
  const needs = careNeeds.value.trim()
  if (needs.length < 5 || carePlanLoading.value) return
  if (!agentStatus.value.configured) {
    ElMessage.error('文字服务尚未配置，请联系管理员检查模型环境变量')
    return
  }
  carePlanLoading.value = true
  carePlan.value = null
  try {
    const { data } = await api.post('/chat/care-plan', { needs, provider: selectedProvider.value }, { suppressErrorToast: true })
    const normalized = normalizeCarePlan(data)
    if (!normalized) throw new Error('照护建议结构校验失败')
    carePlan.value = normalized
    await loadAnalytics(false)
  } catch (error) {
    ElMessage.error(getApiError(error, '照护建议暂时无法生成'))
  } finally {
    carePlanLoading.value = false
  }
}

const sendMessage = async (rawText) => {
  let text = typeof rawText === 'string' ? rawText.trim() : ''
  if (!text && attachments.value.length) text = '请说明图片里可以确认的内容、不确定的地方，以及下一步可以怎么处理。'
  if (!text || loading.value) return
  if (pendingAction.value) {
    ElMessage.warning('请先确认或取消当前待办事项')
    await scrollToBottom()
    return
  }
  if (!agentStatus.value.configured) {
    ElMessage.error('文字服务尚未配置，请联系管理员检查模型环境变量')
    return
  }
  if (attachments.value.length && !visionReady.value) {
    ElMessage.error('图片识别需要单独配置视觉模型；DeepSeek 当前文字通道不能读取图片')
    return
  }
  if (attachments.value.length && selectedProvider.value === 'deepseek') {
    ElMessage.warning('DeepSeek 当前文字通道不能读取图片，请选择自动或已配置的视觉服务')
    return
  }

  const outgoingAttachments = attachments.value.map(({ name, mimeType, dataUrl }) => ({ name, mimeType, dataUrl }))
  const attachmentNames = attachments.value.map((item) => item.name)
  userInput.value = ''
  attachments.value = []
  voiceStatus.value = ''
  messages.value.push({ role: 'user', content: text, attachments: attachmentNames, time: nowLabel() })
  saveHistory()
  await scrollToBottom()
  loading.value = true
  trace.value = []
  startThinking()
  const controller = new AbortController()
  activeController = controller

  try {
    const { data } = await api.post('/chat', {
      messages: sanitizeMessages(messages.value),
      provider: selectedProvider.value,
      attachments: outgoingAttachments
    }, { signal: controller.signal, suppressErrorToast: true })
    trace.value = normalizeTrace(data?.trace)
    lastRun.value = normalizeRun(data?.run)
    if (!data?.success) throw new Error(data?.error || '服务没有返回有效结果')
    if (typeof data.reply === 'string' && data.reply.trim()) {
      const reply = humanizeReply(data.reply)
      messages.value.push({ role: 'assistant', content: reply, time: nowLabel(), run: lastRun.value })
      if (autoSpeak.value) startSpeech(reply, messages.value.length - 1)
    }
    const action = normalizeAction(data.pendingAction)
    if (action?.status === 'PENDING' && action.confirmationToken) {
      pendingAction.value = action
      await loadActions(false)
    } else if (!data.reply) {
      throw new Error('服务没有返回有效结果')
    }
    await loadAnalytics(false)
  } catch (error) {
    if (error?.response?.data) {
      trace.value = normalizeTrace(error.response.data.trace)
      lastRun.value = normalizeRun(error.response.data.run)
    }
    if (error?.code === 'ERR_CANCELED') ElMessage.info('已停止本次办理')
    else ElMessage.error(getApiError(error, '生活助理暂时无法处理这件事'))
  } finally {
    if (activeController === controller) activeController = null
    loading.value = false
    stopThinking()
    saveHistory()
    await scrollToBottom()
    inputTextarea.value?.focus({ preventScroll: true })
  }
}

const stopRequest = () => activeController?.abort()

const confirmPendingAction = async () => {
  const action = pendingAction.value
  if (!action?.confirmationToken || actionLoading.value) return
  actionLoading.value = true
  try {
    const { data } = await api.post(`/chat/actions/${encodeURIComponent(action.confirmationToken)}/confirm`, null, { suppressErrorToast: true })
    const updated = normalizeAction(data?.action)
    if (!data?.success) throw new Error(data?.error || '操作执行失败')
    messages.value.push({ role: 'assistant', content: humanizeReply(data.message || '已经办好了。'), time: nowLabel(), run: null })
    pendingAction.value = updated?.status === 'PENDING' ? updated : null
    ElMessage.success('事项已办理完成')
  } catch (error) {
    ElMessage.error(getApiError(error, '操作执行失败'))
  } finally {
    actionLoading.value = false
    await Promise.allSettled([loadActions(false), loadAnalytics(false)])
    saveHistory()
    await scrollToBottom()
  }
}

const cancelPendingAction = async () => {
  const action = pendingAction.value
  if (!action?.confirmationToken || actionLoading.value) return
  actionLoading.value = true
  try {
    const { data } = await api.post(`/chat/actions/${encodeURIComponent(action.confirmationToken)}/cancel`, null, { suppressErrorToast: true })
    if (!data?.success) throw new Error(data?.error || '取消失败')
    messages.value.push({ role: 'assistant', content: humanizeReply(data.message || '好的，已经取消，没有改动业务数据。'), time: nowLabel(), run: null })
    pendingAction.value = null
    ElMessage.success('已取消，业务数据没有变化')
  } catch (error) {
    ElMessage.error(getApiError(error, '取消操作失败'))
  } finally {
    actionLoading.value = false
    await loadActions(false)
    saveHistory()
    await scrollToBottom()
  }
}

const readBlob = (blob) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => resolve(reader.result)
  reader.onerror = reject
  reader.readAsDataURL(blob)
})

const compressAttachment = async (file) => {
  if (file.size <= 900 * 1024) return file
  const bitmap = await createImageBitmap(file)
  const ratio = Math.min(1, 1440 / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(bitmap.width * ratio))
  canvas.height = Math.max(1, Math.round(bitmap.height * ratio))
  const context = canvas.getContext('2d')
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close?.()
  const blob = await new Promise((resolve, reject) => canvas.toBlob((value) => value ? resolve(value) : reject(new Error('图片压缩失败')), 'image/jpeg', 0.82))
  const name = file.name.replace(/\.[^.]+$/, '') + '.jpg'
  return new File([blob], name, { type: 'image/jpeg', lastModified: Date.now() })
}

const appendAttachment = async (sourceFile) => {
  if (attachments.value.length >= 3) throw new Error('一次最多发送 3 张图片')
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(sourceFile.type)) throw new Error(`${sourceFile.name} 不是支持的图片格式`)
  const file = await compressAttachment(sourceFile)
  if (file.size > 3 * 1024 * 1024) throw new Error(`${file.name} 压缩后仍超过 3MB`)
  const totalBytes = attachments.value.reduce((total, item) => total + item.size, 0) + file.size
  if (totalBytes > 6 * 1024 * 1024) throw new Error('本次图片总大小不能超过 6MB')
  const dataUrl = await readBlob(file)
  attachments.value.push({ id: ++attachmentSequence, name: file.name.slice(0, 120), mimeType: file.type, size: file.size, dataUrl })
}

const addFiles = async (event) => {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  for (const file of files) {
    try { await appendAttachment(file) }
    catch (error) { ElMessage.warning(error.message || `${file.name} 读取失败`) }
  }
}
const removeAttachment = (id) => { attachments.value = attachments.value.filter((item) => item.id !== id) }

const stopCamera = () => {
  cameraStream?.getTracks().forEach((track) => track.stop())
  cameraStream = null
  if (cameraVideo.value) cameraVideo.value.srcObject = null
}

const cameraErrorText = (error) => ({
  NotAllowedError: '没有摄像头权限。请在浏览器地址栏允许摄像头后重试。',
  NotFoundError: '没有检测到可用摄像头。',
  NotReadableError: '摄像头正被其他应用占用，请关闭占用程序后重试。',
  OverconstrainedError: '摄像头不支持当前拍摄参数。'
}[error?.name] || '摄像头连接失败，请检查浏览器权限和设备状态。')

const openCamera = async () => {
  cameraOpen.value = true
  cameraLoading.value = true
  cameraError.value = ''
  await nextTick()
  try {
    stopCamera()
    cameraStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false
    })
    cameraVideo.value.srcObject = cameraStream
    await cameraVideo.value.play()
  } catch (error) {
    cameraError.value = cameraErrorText(error)
  } finally {
    cameraLoading.value = false
  }
}

const capturePhoto = async () => {
  const video = cameraVideo.value
  const canvas = cameraCanvas.value
  if (!video?.videoWidth || !canvas) return
  const ratio = Math.min(1, 1440 / Math.max(video.videoWidth, video.videoHeight))
  canvas.width = Math.round(video.videoWidth * ratio)
  canvas.height = Math.round(video.videoHeight * ratio)
  canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise((resolve, reject) => canvas.toBlob((value) => value ? resolve(value) : reject(new Error('照片生成失败')), 'image/jpeg', 0.84))
  try {
    await appendAttachment(new File([blob], `现场照片-${Date.now()}.jpg`, { type: 'image/jpeg' }))
    cameraOpen.value = false
    ElMessage.success('照片已加入本次对话')
  } catch (error) {
    ElMessage.error(error.message || '照片处理失败')
  }
}

const voiceErrorText = (error) => ({
  'not-allowed': '麦克风权限被拒绝，请在地址栏允许后重试',
  'service-not-allowed': '浏览器语音服务不可用，请检查网络或浏览器设置',
  'audio-capture': '没有检测到可用麦克风',
  'no-speech': '没有听到清晰语音，请靠近麦克风再试',
  network: '语音识别网络连接失败，请稍后重试'
}[error] || '语音识别没有成功，请再说一次或直接输入')

const requestMicrophoneAccess = async () => {
  if (!navigator.mediaDevices?.getUserMedia) throw new Error('当前浏览器不能访问麦克风')
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
  stream.getTracks().forEach((track) => track.stop())
}

const toggleRecording = async () => {
  if (!recognition) {
    ElMessage.warning('请使用最新版 Edge 或 Chrome 开启语音输入')
    return
  }
  if (recording.value) {
    recognition.stop()
    return
  }
  voiceStatus.value = '正在申请麦克风权限…'
  try {
    await requestMicrophoneAccess()
    recognition.start()
  } catch (error) {
    voiceStatus.value = error?.name === 'NotAllowedError'
      ? '麦克风权限被拒绝，请在地址栏允许后重试'
      : (error.message || '麦克风无法启动')
    ElMessage.warning(voiceStatus.value)
  }
}

const resetSpeechState = () => {
  activeUtterance = null
  speechMessageIndex.value = null
  speechState.value = 'idle'
}

const stopSpeech = () => {
  speechToken += 1
  globalThis.speechSynthesis?.cancel()
  resetSpeechState()
}

const startSpeech = (text, messageIndex = null) => {
  const synthesizer = globalThis.speechSynthesis
  const Utterance = globalThis.SpeechSynthesisUtterance
  if (!speechOutputSupported.value || !synthesizer || !Utterance || !text?.trim()) return

  const token = ++speechToken
  synthesizer.cancel()
  if (synthesizer.paused) synthesizer.resume()

  const utterance = new Utterance(text)
  utterance.lang = 'zh-CN'
  utterance.rate = 0.95
  activeUtterance = utterance
  speechMessageIndex.value = messageIndex
  speechState.value = 'speaking'

  const finish = () => {
    if (token === speechToken) resetSpeechState()
  }
  utterance.onstart = () => {
    if (token === speechToken) speechState.value = 'speaking'
  }
  utterance.onpause = () => {
    if (token === speechToken) speechState.value = 'paused'
  }
  utterance.onresume = () => {
    if (token === speechToken) speechState.value = 'speaking'
  }
  utterance.onend = finish
  utterance.onerror = finish
  synthesizer.speak(utterance)
}

const toggleSpeech = (text, messageIndex) => {
  const synthesizer = globalThis.speechSynthesis
  if (!synthesizer) return

  const isCurrentMessage = activeUtterance && speechMessageIndex.value === messageIndex
  if (isCurrentMessage && speechState.value === 'speaking') {
    synthesizer.pause()
    speechState.value = 'paused'
    return
  }
  if (isCurrentMessage && speechState.value === 'paused') {
    synthesizer.resume()
    speechState.value = 'speaking'
    return
  }
  startSpeech(text, messageIndex)
}

const speechButtonLabel = (messageIndex) => {
  if (speechMessageIndex.value !== messageIndex) return '朗读回复'
  if (speechState.value === 'speaking') return '暂停朗读'
  if (speechState.value === 'paused') return '继续朗读'
  return '朗读回复'
}

const clearHistory = async () => {
  try {
    await ElMessageBox.confirm('只会清空本机显示的对话文字，业务订单和办理记录都会保留。', '清空当前对话', {
      type: 'warning', confirmButtonText: '清空', cancelButtonText: '保留'
    })
    stopSpeech()
    messages.value = []
    trace.value = []
    lastRun.value = null
    sessionStorage.removeItem(historyKey)
  } catch { /* 用户选择保留 */ }
}

onMounted(async () => {
  localStorage.removeItem('deepseek_api_key')
  sessionStorage.removeItem('apiKey')
  if (!token) { await router.replace('/login'); return }
  loadHistory()
  cameraSupported.value = Boolean(navigator.mediaDevices?.getUserMedia && window.isSecureContext)
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition
  speechInputSupported.value = Boolean(Recognition && navigator.mediaDevices?.getUserMedia && window.isSecureContext)
  speechOutputSupported.value = Boolean(window.speechSynthesis && window.SpeechSynthesisUtterance)
  if (Recognition) {
    recognition = new Recognition()
    recognition.lang = 'zh-CN'
    recognition.interimResults = true
    recognition.continuous = false
    recognition.onstart = () => {
      recording.value = true
      speechBaseInput = userInput.value
      voiceStatus.value = '正在听，请自然说话…'
    }
    recognition.onend = () => {
      recording.value = false
      voiceStatus.value = userInput.value === speechBaseInput ? '没有识别到内容，可以再试一次' : '语音已转成文字，可继续修改或发送'
    }
    recognition.onerror = (event) => {
      recording.value = false
      if (event.error === 'aborted') return
      voiceStatus.value = voiceErrorText(event.error)
      ElMessage.warning(voiceStatus.value)
    }
    recognition.onresult = (event) => {
      let transcript = ''
      for (let index = 0; index < event.results.length; index++) transcript += event.results[index][0].transcript
      userInput.value = `${speechBaseInput}${speechBaseInput && transcript ? ' ' : ''}${transcript}`.slice(0, 2000)
    }
  }
  await Promise.allSettled([loadStatus(), loadActions(false), loadAnalytics(false)])
  await scrollToBottom()
  if (window.matchMedia('(min-width: 841px)').matches) {
    inputTextarea.value?.focus({ preventScroll: true })
  }
})

onBeforeUnmount(() => {
  activeController?.abort()
  stopThinking()
  recognition?.abort()
  stopCamera()
  stopSpeech()
})
</script>

<style scoped>
.companion-page {
  min-height: 100vh;
  padding: calc(var(--sp-header-height) + 28px) 22px 44px;
  color: var(--sp-text);
  background: transparent;
}

.companion-intro,
.companion-workspace {
  width: min(1320px, 100%);
  margin-inline: auto;
}

.companion-intro {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: 124px minmax(0, 1fr) auto;
  align-items: center;
  gap: 26px;
  overflow: hidden;
  margin-bottom: 20px;
  padding: 24px 28px;
  color: var(--sp-text);
  background: linear-gradient(145deg, #ffffff 0%, #f5f9ff 54%, #e8f2ff 100%);
  border: 1px solid #d6e6f6;
  border-radius: var(--sp-radius-lg);
  box-shadow: var(--sp-shadow-lg);
}
.companion-intro::before { content: ''; position: absolute; z-index: -1; width: 320px; aspect-ratio: 1; top: -210px; right: 12%; background: rgb(41 151 255 / 10%); border-radius: 50%; box-shadow: 0 0 0 48px rgb(41 151 255 / 4%), 0 0 0 96px rgb(41 151 255 / 2%); }

.companion-portrait {
  position: relative;
  width: 112px;
  height: 112px;
  overflow: hidden;
  background: var(--sp-brand-soft);
  border: 4px solid #ffffff;
  border-radius: 50%;
  box-shadow: 0 16px 34px rgb(0 61 122 / 14%);
}

.companion-portrait img {
  width: 116%;
  height: 116%;
  margin: -2% 0 0 -8%;
  object-fit: cover;
  object-position: center 15%;
  transform-origin: center bottom;
  animation: companion-breathe 4.8s ease-in-out infinite;
}

.presence-dot {
  position: absolute;
  right: 7px;
  bottom: 10px;
  width: 16px;
  height: 16px;
  background: var(--sp-success);
  border: 3px solid #ffffff;
  border-radius: 50%;
}

.companion-portrait.busy .presence-dot,
.companion-portrait.listening .presence-dot { animation: status-pulse 1.25s ease-in-out infinite; }
.companion-portrait.listening .presence-dot { background: var(--sp-danger); }

.companion-copy { min-width: 0; }
.companion-label { color: var(--sp-brand); font-size: 13px; font-weight: 650; letter-spacing: 0; }
.companion-copy h1 { margin: 5px 0 7px; color: var(--sp-text); font-size: clamp(30px, 3.6vw, 46px); line-height: 1.08; letter-spacing: -.045em; }
.companion-copy p { max-width: 780px; margin: 0; color: var(--sp-text-secondary); font-size: 15px; line-height: 1.7; }

.availability { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; }
.availability span { min-height: 34px; display: inline-flex; align-items: center; gap: 8px; padding: 0 11px; color: var(--sp-text-secondary); font-size: 12px; font-weight: 600; background: rgb(255 255 255 / 76%); border: 1px solid rgb(0 0 0 / 8%); border-radius: var(--sp-radius-pill); backdrop-filter: blur(10px); }
.availability i { width: 8px; height: 8px; background: var(--sp-warning); border-radius: 50%; }
.availability .ready i { background: var(--sp-success); }

.companion-workspace { display: grid; grid-template-columns: minmax(0, 1fr) 330px; gap: 18px; align-items: start; }
.conversation-card,
.context-section { background: var(--sp-surface-glass); border: 1px solid rgb(255 255 255 / 72%); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-md); backdrop-filter: blur(22px); }

.conversation-card {
  min-width: 0;
  height: min(770px, calc(100vh - 214px));
  min-height: 620px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.conversation-heading { min-height: 76px; display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 15px 20px; background: linear-gradient(180deg, rgb(255 255 255 / 96%), rgb(247 247 243 / 82%)); border-bottom: 1px solid var(--sp-border); }
.conversation-heading span { color: var(--sp-text-muted); font-size: 11px; font-weight: 650; }
.conversation-heading h2 { margin: 2px 0 0; font-size: 18px; line-height: 1.35; }
.conversation-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 7px; }
.conversation-actions button,
.reply-actions button { min-height: 34px; padding: 0 10px; color: var(--sp-text-secondary); font-size: 12px; font-weight: 650; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-xs); cursor: pointer; }
.conversation-actions button.active { color: var(--sp-brand-strong); background: var(--sp-brand-soft); border-color: color-mix(in srgb, var(--sp-brand) 35%, var(--sp-border)); }

.quick-task-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; padding: 12px 14px; background: var(--sp-surface-muted); border-bottom: 1px solid var(--sp-border); }
.quick-task-row button { min-width: 0; min-height: 62px; display: grid; grid-template-columns: 30px minmax(0, 1fr); grid-template-rows: auto auto; align-items: center; column-gap: 8px; padding: 9px 10px; text-align: left; color: var(--sp-text); background: #fff; border: 1px solid var(--sp-border); border-radius: var(--sp-radius-sm); cursor: pointer; transition: transform var(--sp-duration-fast) var(--sp-ease-expressive), background-color var(--sp-duration-fast) ease; }
.quick-task-row button:hover { background: var(--sp-brand-soft); border-color: #b7d5f3; transform: translateY(-1px); box-shadow: var(--sp-shadow-sm); }
.quick-task-row button > span { grid-row: 1 / 3; width: 30px; height: 30px; display: grid; place-items: center; color: var(--sp-brand); font-size: 13px; font-weight: 700; background: var(--sp-brand-soft); border-radius: 50%; }
.quick-task-row strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.quick-task-row small { overflow: hidden; color: var(--sp-text-muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }

.message-list { flex: 1; min-height: 0; overflow: auto; padding: 22px 22px 14px; scrollbar-color: var(--sp-border-strong) transparent; }
.welcome-state { min-height: 100%; display: grid; grid-template-columns: 180px minmax(0, 520px); align-items: center; justify-content: center; gap: 28px; padding: 30px; }
.welcome-state > img { width: 180px; height: 180px; object-fit: cover; object-position: center 12%; background: var(--sp-brand-soft); border: 4px solid var(--sp-surface); border-radius: 50%; box-shadow: 0 20px 48px rgb(16 26 23 / 16%); animation: companion-breathe 4.8s ease-in-out infinite; }
.welcome-state h3 { margin: 0 0 8px; font-size: 26px; }
.welcome-state p { margin: 0 0 13px; color: var(--sp-text-secondary); font-size: 16px; line-height: 1.8; }
.welcome-state span { display: block; padding: 10px 12px; color: var(--sp-text-secondary); font-size: 13px; line-height: 1.6; background: var(--sp-success-soft); border-left: 3px solid var(--sp-success); border-radius: 0 var(--sp-radius-xs) var(--sp-radius-xs) 0; }

.message { display: flex; align-items: flex-start; gap: 11px; margin-bottom: 22px; }
.message.user { flex-direction: row-reverse; }
.message-avatar { width: 42px; height: 42px; flex: 0 0 auto; display: grid; place-items: center; overflow: hidden; color: var(--sp-on-brand); font-size: 13px; font-weight: 800; background: var(--sp-brand); border: 1px solid var(--sp-border); border-radius: 50%; }
.message-avatar img { width: 122%; height: 122%; object-fit: cover; object-position: center 12%; background: #edf3ef; }
.message-body { min-width: 0; max-width: min(82%, 760px); display: flex; flex-direction: column; align-items: flex-start; }
.message.user .message-body { align-items: flex-end; }
.message-meta { display: flex; align-items: center; gap: 8px; margin: 0 3px 5px; }
.message-meta strong { font-size: 13px; }
.message-meta time { color: var(--sp-text-muted); font-size: 11px; }
.message-content { padding: 13px 15px; color: var(--sp-text); font-size: 16px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: 4px 12px 12px 12px; }
.message.user .message-content { color: var(--sp-text); background: var(--sp-brand-soft); border-color: color-mix(in srgb, var(--sp-brand) 22%, var(--sp-border)); border-radius: 12px 4px 12px 12px; }
.message-attachments { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.message-attachments span { padding: 4px 7px; color: var(--sp-text-muted); font-size: 10px; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-xs); }
.reply-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 7px; }
.reply-actions button { min-height: 30px; }
.reply-actions span { color: var(--sp-text-muted); font-size: 10px; }

.thinking-card { min-width: 230px; display: flex; align-items: center; gap: 6px; padding: 13px 15px; color: var(--sp-text-secondary); font-size: 14px; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: 4px 12px 12px 12px; }
.thinking-card span { margin-right: 4px; }
.thinking-card i { width: 7px; height: 7px; background: var(--sp-brand); border-radius: 50%; animation: thinking-dot 1.2s ease-in-out infinite; }
.thinking-card i:nth-of-type(2) { animation-delay: .14s; }
.thinking-card i:nth-of-type(3) { animation-delay: .28s; }
.assistant-thinking { animation: status-pulse 1.4s ease-in-out infinite; }

.confirmation-card { margin: 8px 52px 22px; padding: 17px; background: var(--sp-warning-soft); border: 1px solid color-mix(in srgb, var(--sp-warning) 32%, var(--sp-border)); border-radius: var(--sp-radius-sm); }
.confirmation-heading { display: flex; align-items: center; gap: 10px; }
.confirmation-heading > span { width: 32px; height: 32px; display: grid; place-items: center; color: var(--sp-on-brand); font-weight: 800; background: var(--sp-brand); border-radius: 50%; }
.confirmation-heading div { display: grid; }
.confirmation-heading small { color: var(--sp-warning); font-size: 10px; font-weight: 750; }
.confirmation-heading strong { font-size: 16px; }
.confirmation-card > p { margin: 12px 0 6px; color: var(--sp-text); font-size: 16px; line-height: 1.7; }
.confirmation-card > small { color: var(--sp-text-secondary); }
.confirmation-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.confirmation-actions .el-button { margin-left: 0; }

.attachment-tray { display: flex; gap: 8px; overflow-x: auto; padding: 9px 14px 0; border-top: 1px solid var(--sp-border); }
.attachment-tray article { min-width: 190px; display: grid; grid-template-columns: 48px minmax(0, 1fr) auto; align-items: center; gap: 8px; padding: 7px; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-xs); }
.attachment-tray img { width: 48px; height: 42px; object-fit: cover; border-radius: 4px; }
.attachment-tray div { min-width: 0; display: grid; }
.attachment-tray strong { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.attachment-tray small { color: var(--sp-text-muted); font-size: 9px; }
.attachment-tray button { min-height: 30px; padding: 0 7px; color: var(--sp-danger); font-size: 10px; background: transparent; border: 0; cursor: pointer; }

.composer { margin: 10px 14px 14px; padding: 10px; background: rgb(255 255 255 / 92%); border: 1px solid var(--sp-border-strong); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-sm); }
.composer:focus-within { border-color: var(--sp-brand); box-shadow: var(--sp-focus); }
.composer textarea { width: 100%; min-height: 62px; max-height: 150px; padding: 5px 7px; resize: vertical; color: var(--sp-text); font-size: 16px; line-height: 1.65; background: transparent; border: 0; outline: 0; }
.composer textarea::placeholder { color: var(--sp-text-muted); }
.composer-bottom { display: grid; grid-template-columns: auto minmax(120px, 1fr) auto auto; align-items: center; gap: 8px; padding-top: 8px; border-top: 1px solid var(--sp-border); }
.media-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.media-actions button { min-height: 36px; padding: 0 11px; color: var(--sp-text-secondary); font-size: 12px; font-weight: 650; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-xs); cursor: pointer; }
.media-actions button:hover { color: var(--sp-brand-strong); border-color: var(--sp-brand); }
.media-actions button.recording { color: var(--sp-danger); background: var(--sp-danger-soft); border-color: var(--sp-danger); }
.voice-status { min-width: 0; overflow: hidden; color: var(--sp-text-muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.character-count { color: var(--sp-text-muted); font-size: 10px; }
.vision-notice { margin: 8px 5px 0; padding: 7px 9px; color: var(--sp-warning); font-size: 11px; line-height: 1.55; background: var(--sp-warning-soft); border-radius: var(--sp-radius-xs); }

.context-panel { display: grid; gap: 12px; max-height: min(770px, calc(100vh - 214px)); overflow-y: auto; padding-right: 2px; scrollbar-color: var(--sp-border-strong) transparent; }
.context-section { padding: 16px; box-shadow: var(--sp-shadow-xs); }
.context-section header { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.context-section header span,
.care-entry > span { color: var(--sp-text-muted); font-size: 10px; font-weight: 650; letter-spacing: .04em; }
.context-section h2 { margin: 1px 0 0; font-size: 16px; }
.care-entry { color: var(--sp-text); background: var(--sp-brand-soft); border-color: #c9def3; border-left: 4px solid var(--sp-brand); }
.care-entry h2,
.care-entry > span { color: var(--sp-brand-strong); }
.care-entry p { margin: 7px 0 13px; color: var(--sp-text-secondary); font-size: 12px; line-height: 1.65; }
.care-entry .el-button { width: 100%; }
.context-empty { padding: 12px; color: var(--sp-text-muted); font-size: 12px; line-height: 1.6; background: var(--sp-surface-muted); border-radius: var(--sp-radius-xs); }

.trace-list { display: grid; gap: 10px; list-style: none; }
.trace-list li { display: grid; grid-template-columns: 26px minmax(0, 1fr); gap: 8px; }
.trace-list li > span { width: 24px; height: 24px; display: grid; place-items: center; color: var(--sp-text-muted); font-size: 10px; font-weight: 750; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: 50%; }
.trace-list li.success > span { color: var(--sp-success); background: var(--sp-success-soft); border-color: color-mix(in srgb, var(--sp-success) 28%, var(--sp-border)); }
.trace-list strong { display: block; font-size: 12px; }
.trace-list p { margin: 2px 0 0; color: var(--sp-text-muted); font-size: 10px; line-height: 1.5; }
.run-details { margin-top: 12px; border-top: 1px solid var(--sp-border); }
.run-details summary { padding-top: 10px; color: var(--sp-text-secondary); font-size: 11px; font-weight: 650; cursor: pointer; }
.run-details dl { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 9px; }
.run-details dl div { padding: 7px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-xs); }
.run-details dt { color: var(--sp-text-muted); font-size: 9px; }
.run-details dd { margin: 2px 0 0; font-size: 11px; font-weight: 700; }

.connection-panel ul { display: grid; gap: 10px; list-style: none; }
.connection-panel li { display: grid; grid-template-columns: 9px minmax(0, 1fr); align-items: center; gap: 9px; }
.connection-panel li > span { width: 8px; height: 8px; background: var(--sp-warning); border-radius: 50%; }
.connection-panel li > span.online { background: var(--sp-success); }
.connection-panel li div { display: grid; }
.connection-panel li strong { font-size: 12px; }
.connection-panel li small { color: var(--sp-text-muted); font-size: 10px; }
.connection-panel label { display: block; margin: 14px 0 5px; color: var(--sp-text-secondary); font-size: 10px; font-weight: 650; }
.connection-panel select { width: 100%; min-height: 38px; padding: 0 10px; color: var(--sp-text); background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-xs); }

.recent-actions header button { min-height: 30px; padding: 0 8px; color: var(--sp-text-secondary); font-size: 10px; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-xs); cursor: pointer; }
.recent-actions ol { display: grid; gap: 10px; list-style: none; }
.recent-actions li { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; align-items: center; gap: 7px; }
.action-dot { width: 7px; height: 7px; background: var(--sp-text-muted); border-radius: 50%; }
.action-dot.success { background: var(--sp-success); }
.action-dot.warning { background: var(--sp-warning); }
.action-dot.danger { background: var(--sp-danger); }
.recent-actions li div { min-width: 0; display: grid; }
.recent-actions li strong { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.recent-actions li small { color: var(--sp-text-muted); font-size: 9px; }
.recent-actions em { font-size: 9px; font-style: normal; }
.recent-actions em.success { color: var(--sp-success); }
.recent-actions em.warning { color: var(--sp-warning); }
.recent-actions em.danger { color: var(--sp-danger); }
.weekly-summary { display: grid; gap: 2px; margin-top: 12px; padding: 9px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-xs); }
.weekly-summary span { color: var(--sp-text-muted); font-size: 9px; }
.weekly-summary strong { font-size: 11px; }

.camera-workspace { display: grid; gap: 10px; }
.camera-preview { position: relative; min-height: 320px; display: grid; place-items: center; overflow: hidden; background: #111; border-radius: var(--sp-radius-sm); }
.camera-preview video { width: 100%; max-height: 62vh; display: block; object-fit: contain; }
.camera-state { position: absolute; inset: 0; display: grid; place-items: center; padding: 24px; color: #fff; text-align: center; background: #18201b; }
.camera-state.error { color: #ffe2e5; }
.camera-workspace p { margin: 0; color: var(--sp-text-muted); font-size: 12px; line-height: 1.6; }

.care-plan-workbench { display: grid; gap: 12px; }
.care-plan-intro { margin: 0; padding: 11px 12px; color: var(--sp-text-secondary); font-size: 13px; line-height: 1.65; background: var(--sp-info-soft); border-left: 3px solid var(--sp-info); }
.care-plan-workbench > label { color: var(--sp-text); font-size: 13px; font-weight: 700; }
.care-plan-workbench > textarea { width: 100%; min-height: 130px; padding: 12px; resize: vertical; color: var(--sp-text); font: inherit; line-height: 1.65; background: var(--sp-surface); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-sm); }
.care-plan-workbench > textarea:focus { outline: none; border-color: var(--sp-brand); box-shadow: var(--sp-focus); }
.care-plan-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.care-plan-actions span { color: var(--sp-text-muted); font-size: 11px; }
.care-plan-result { display: grid; gap: 13px; margin-top: 4px; }
.care-plan-result > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.care-plan-result header small { color: var(--sp-text-muted); }
.care-plan-result h3 { margin: 3px 0 0; font-size: 18px; }
.recommendation-list { display: grid; gap: 10px; }
.recommendation-list article { padding: 13px; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-sm); }
.recommendation-list h4 { margin: 0; font-size: 15px; }
.recommendation-list h4 small { color: var(--sp-text-muted); font-weight: 500; }
.recommendation-list p { margin: 6px 0; color: var(--sp-text-secondary); line-height: 1.65; }
.recommendation-list dl { display: grid; grid-template-columns: 70px 1fr; margin: 0; font-size: 12px; }
.recommendation-list dt { color: var(--sp-text-muted); }
.recommendation-list dd { margin: 0; }
.evidence-tags { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 9px; }
.evidence-tags span { padding: 3px 6px; color: var(--sp-text-muted); font-size: 9px; background: var(--sp-surface); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-pill); }
.care-plan-empty,
.care-safety { padding: 12px; background: var(--sp-warning-soft); border-radius: var(--sp-radius-sm); }
.care-plan-empty p,
.care-safety p { margin: 4px 0; color: var(--sp-text-secondary); line-height: 1.6; }
.care-safety small { color: var(--sp-text-muted); }

/* Keep secondary Agent information readable for older users as well. */
.quick-task-row small,
.message-attachments span,
.reply-actions span,
.confirmation-heading small,
.voice-status,
.character-count,
.context-section header span,
.care-entry > span,
.trace-list p,
.run-details summary,
.run-details dt,
.connection-panel li small,
.connection-panel label,
.recent-actions header button,
.recent-actions li small,
.recent-actions em,
.weekly-summary span { font-size: 12px; }
.attachment-tray strong,
.care-entry p,
.trace-list strong,
.run-details dd,
.connection-panel li strong,
.recent-actions li strong,
.weekly-summary strong { font-size: 13px; }
.attachment-tray small { font-size: 12px; }
.attachment-tray button { min-height: 32px; font-size: 12px; }
.vision-notice { padding: 8px 10px; font-size: 13px; }
.evidence-tags span { font-size: 11px; }

@keyframes companion-breathe {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-3px) scale(1.012); }
}

@keyframes status-pulse {
  0%, 100% { box-shadow: 0 0 0 0 color-mix(in srgb, currentColor 0%, transparent); }
  50% { box-shadow: 0 0 0 5px color-mix(in srgb, var(--sp-brand) 16%, transparent); }
}

@keyframes thinking-dot {
  0%, 70%, 100% { opacity: .35; transform: translateY(0); }
  35% { opacity: 1; transform: translateY(-3px); }
}

@media (max-width: 1080px) {
  .companion-workspace { grid-template-columns: minmax(0, 1fr) 292px; }
  .quick-task-row { grid-template-columns: 1fr 1fr; }
  .conversation-card { height: min(800px, calc(100vh - 210px)); }
  .context-panel { max-height: min(800px, calc(100vh - 210px)); }
}

@media (max-width: 840px) {
  .companion-page { padding: calc(var(--sp-header-height) + 14px) 12px 26px; }
  .companion-intro { grid-template-columns: 74px minmax(0, 1fr); gap: 14px; padding: 13px; }
  .companion-portrait { width: 72px; height: 72px; }
  .availability { grid-column: 1 / -1; flex-direction: row; flex-wrap: wrap; }
  .companion-workspace { grid-template-columns: 1fr; }
  .conversation-card { height: auto; min-height: 700px; }
  .message-list { min-height: 390px; max-height: 58vh; }
  .context-panel { max-height: none; overflow: visible; grid-template-columns: 1fr 1fr; }
  .care-entry, .recent-actions { grid-column: 1 / -1; }
}

@media (max-width: 600px) {
  .companion-intro { align-items: start; }
  .companion-copy h1 { font-size: 21px; }
  .companion-copy p { font-size: 13px; }
  .availability span { min-height: 31px; font-size: 10px; }
  .conversation-heading { align-items: flex-start; flex-direction: column; padding: 13px; }
  .conversation-actions { width: 100%; justify-content: flex-start; }
  .quick-task-row { display: flex; overflow-x: auto; padding: 9px; }
  .quick-task-row button { min-width: 165px; }
  .message-list { padding: 16px 11px 10px; }
  .welcome-state { grid-template-columns: 1fr; gap: 14px; padding: 20px 10px; text-align: center; }
  .welcome-state > img { width: 126px; height: 126px; margin: 0 auto; }
  .welcome-state h3 { font-size: 22px; }
  .welcome-state p { font-size: 15px; }
  .welcome-state span { text-align: left; }
  .message { gap: 7px; }
  .message-avatar { width: 34px; height: 34px; }
  .message-body { max-width: calc(100% - 42px); }
  .message-content { padding: 11px 12px; font-size: 16px; line-height: 1.75; }
  .confirmation-card { margin-inline: 0; }
  .composer { margin: 8px; }
  .composer-bottom { grid-template-columns: 1fr auto; }
  .media-actions { grid-column: 1 / -1; }
  .voice-status { grid-column: 1 / -1; grid-row: 2; white-space: normal; }
  .character-count { display: none; }
  .composer-bottom > .el-button { grid-column: 1 / -1; grid-row: 3; width: 100%; margin-left: 0; }
  .context-panel { grid-template-columns: 1fr; }
  .care-entry, .recent-actions { grid-column: auto; }
  .camera-preview { min-height: 240px; }
}

@media (prefers-reduced-motion: reduce) {
  .companion-portrait img,
  .welcome-state > img,
  .presence-dot,
  .thinking-card i,
  .assistant-thinking { animation: none; }
}
</style>
