const MAX_CHAT_MESSAGES = 30
const MAX_MESSAGE_LENGTH = 4000

const toFiniteNumber = (value) => {
  try {
    const numericValue = Number(value)
    return Number.isFinite(numericValue) ? numericValue : null
  } catch {
    return null
  }
}

const finiteNonNegative = (value, fallback = 0) => Math.max(0, toFiniteNumber(value) ?? fallback)

export const sanitizeMessages = (value) => Array.isArray(value)
  ? value
      .filter((item) => item && ['user', 'assistant'].includes(item.role) && typeof item.content === 'string')
      .map(({ role, content }) => ({ role, content: content.trim().slice(0, MAX_MESSAGE_LENGTH) }))
      .filter((item) => item.content)
      .slice(-MAX_CHAT_MESSAGES)
  : []

export const normalizeAction = (value) => {
  if (!value || typeof value !== 'object') return null
  const id = toFiniteNumber(value.id)
  if (id === null) return null
  return {
    id,
    confirmationToken: typeof value.confirmationToken === 'string' ? value.confirmationToken : null,
    toolName: typeof value.toolName === 'string' ? value.toolName : '',
    summary: typeof value.summary === 'string' ? value.summary : '待办理事项',
    status: typeof value.status === 'string' ? value.status : 'UNKNOWN',
    resultMessage: typeof value.resultMessage === 'string' ? value.resultMessage : null,
    expiresAt: value.expiresAt || null,
    createdAt: value.createdAt || null,
    executedAt: value.executedAt || null
  }
}

export const actionStatusText = (status) => ({
  PENDING: '等待确认',
  EXECUTING: '执行中',
  SUCCEEDED: '已完成',
  FAILED: '执行失败',
  CANCELLED: '已取消',
  EXPIRED: '已过期'
}[status] || '未知状态')

export const actionStatusTone = (status) => ({
  PENDING: 'warning',
  EXECUTING: 'info',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  CANCELLED: 'muted',
  EXPIRED: 'muted'
}[status] || 'muted')

export const formatActionTime = (value) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
}

export const getApiError = (error, fallback = '请求失败，请稍后重试') => (
  error?.response?.data?.error
  || error?.response?.data?.message
  || error?.response?.data?.msg
  || error?.message
  || fallback
)

export const normalizeProvider = (value) => {
  if (!value || typeof value !== 'object' || typeof value.id !== 'string') return null
  return {
    id: value.id,
    name: typeof value.name === 'string' ? value.name : value.id,
    model: typeof value.model === 'string' ? value.model : '',
    configured: Boolean(value.configured),
    text: Boolean(value.text),
    vision: Boolean(value.vision),
    serverVoice: Boolean(value.serverVoice),
    statusText: typeof value.statusText === 'string' ? value.statusText : ''
  }
}

export const normalizeRun = (value) => {
  if (!value || typeof value !== 'object' || typeof value.runId !== 'string') return null
  return {
    runId: value.runId,
    provider: typeof value.provider === 'string' ? value.provider : 'unresolved',
    model: typeof value.model === 'string' ? value.model : '',
    inputModality: typeof value.inputModality === 'string' ? value.inputModality : 'TEXT',
    status: typeof value.status === 'string' ? value.status : 'UNKNOWN',
    latencyMs: finiteNonNegative(value.latencyMs),
    llmCalls: finiteNonNegative(value.llmCalls),
    toolCalls: finiteNonNegative(value.toolCalls),
    totalTokens: finiteNonNegative(value.totalTokens),
    promptVersion: typeof value.promptVersion === 'string' ? value.promptVersion : '',
    confirmationRequired: Boolean(value.confirmationRequired)
  }
}

export const normalizeCarePlan = (value) => {
  if (!value || typeof value !== 'object' || typeof value.runId !== 'string') return null
  const allowedUrgency = new Set(['low', 'medium', 'high', 'emergency'])
  const recommendations = Array.isArray(value.recommendations)
    ? value.recommendations
        .map((item) => {
          const serviceId = item && typeof item === 'object' ? toFiniteNumber(item.serviceId) : null
          if (serviceId === null || typeof item.serviceName !== 'string') return null
          return {
            serviceId,
            serviceName: item.serviceName.slice(0, 120),
            reason: typeof item.reason === 'string' ? item.reason.slice(0, 300) : '',
            nextStep: typeof item.nextStep === 'string' ? item.nextStep.slice(0, 240) : '',
            evidence: Array.isArray(item.evidence)
              ? item.evidence
                  .filter((entry) => typeof entry === 'string')
                  .map((entry) => entry.slice(0, 300))
                  .slice(0, 8)
              : []
          }
        })
        .filter(Boolean)
        .slice(0, 3)
    : []
  return {
    runId: value.runId.slice(0, 120),
    provider: typeof value.provider === 'string' ? value.provider.slice(0, 80) : 'unresolved',
    model: typeof value.model === 'string' ? value.model.slice(0, 120) : '',
    promptVersion: typeof value.promptVersion === 'string' ? value.promptVersion.slice(0, 80) : '',
    totalTokens: finiteNonNegative(value.totalTokens),
    summary: typeof value.summary === 'string' ? value.summary.slice(0, 160) : '已完成需求整理',
    urgency: allowedUrgency.has(value.urgency) ? value.urgency : 'medium',
    recommendations,
    safetyNotice: typeof value.safetyNotice === 'string' ? value.safetyNotice.slice(0, 300) : '',
    disclaimer: typeof value.disclaimer === 'string' ? value.disclaimer.slice(0, 220) : '',
    generatedAt: value.generatedAt || null
  }
}

export const normalizeTrace = (value) => Array.isArray(value)
  ? value
      .filter((item) => item && typeof item.title === 'string')
      .map((item) => ({
        code: typeof item.code === 'string' ? item.code : 'STEP',
        title: item.title,
        status: typeof item.status === 'string' ? item.status : 'completed',
        detail: typeof item.detail === 'string' ? item.detail : '',
        timestamp: item.timestamp || null
      }))
      .slice(-16)
  : []

export const normalizeAnalytics = (value) => ({
  days: Math.max(1, finiteNonNegative(value?.days, 7)),
  totalRuns: finiteNonNegative(value?.totalRuns),
  successRate: Math.min(100, finiteNonNegative(value?.successRate)),
  averageLatencyMs: finiteNonNegative(value?.averageLatencyMs),
  toolCalls: finiteNonNegative(value?.toolCalls),
  totalTokens: finiteNonNegative(value?.totalTokens),
  degradedRuns: finiteNonNegative(value?.degradedRuns),
  toolSuccessRate: Math.min(100, finiteNonNegative(value?.toolSuccessRate)),
  waitingConfirmation: finiteNonNegative(value?.waitingConfirmation),
  providers: value?.providers && typeof value.providers === 'object' ? value.providers : {},
  modalities: value?.modalities && typeof value.modalities === 'object' ? value.modalities : {},
  recentRuns: Array.isArray(value?.recentRuns) ? value.recentRuns : []
})

export const formatLatency = (milliseconds) => {
  const value = finiteNonNegative(milliseconds)
  return value < 1000 ? `${Math.round(value)} ms` : `${(value / 1000).toFixed(1)} s`
}
