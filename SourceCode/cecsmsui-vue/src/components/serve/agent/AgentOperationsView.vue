<template>
  <section class="agent-operations" aria-labelledby="agent-operations-title">
    <header class="page-heading">
      <div>
        <span>智能服务运营</span>
        <h1 id="agent-operations-title">小伴运行与知识协同</h1>
        <p>查看用户办理链路、WorkBuddy 只读调用、IMA 知识资料和多模态状态。这里不展示密钥、原始提示词或个人健康内容。</p>
      </div>
      <div class="heading-actions">
        <el-select v-model="days" aria-label="统计周期" @change="loadOverview()">
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 14 天" :value="14" />
          <el-option label="近 30 天" :value="30" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadOverview()">刷新数据</el-button>
      </div>
    </header>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false">
      <template #default>请确认后端已启动，并使用系统管理员账号重新加载。</template>
    </el-alert>

    <div class="metric-grid" aria-label="运行概览">
      <article><span>协助次数</span><strong>{{ analytics.totalRuns }}</strong><small>近 {{ analytics.days }} 天</small></article>
      <article><span>链路成功率</span><strong>{{ analytics.successRate.toFixed(1) }}%</strong><small>{{ analytics.degradedRuns }} 次降级处理</small></article>
      <article><span>平均响应</span><strong>{{ formatLatency(analytics.averageLatencyMs) }}</strong><small>{{ analytics.toolCalls }} 次业务工具调用</small></article>
      <article><span>待用户确认</span><strong>{{ analytics.waitingConfirmation }}</strong><small>写操作均需二次确认</small></article>
    </div>

    <div class="operations-grid">
      <el-card shadow="never" class="panel">
        <template #header>
          <div class="panel-heading"><div><span>服务引擎</span><h2>模型与多模态能力</h2></div><el-tag :type="visionConfigured ? 'success' : 'warning'" effect="plain">{{ visionConfigured ? '图文可用' : '仅文字可用' }}</el-tag></div>
        </template>
        <div v-if="providers.length" class="provider-list">
          <article v-for="provider in providers" :key="provider.id">
            <div><strong>{{ provider.name }}</strong><small>{{ provider.model || '模型名称未公开' }}</small></div>
            <el-tag :type="provider.configured ? 'success' : 'info'" effect="plain" size="small">{{ provider.configured ? '已连接' : '未配置' }}</el-tag>
            <ul><li :class="{ enabled: provider.text }">文字</li><li :class="{ enabled: provider.vision }">图片</li><li :class="{ enabled: provider.serverVoice }">服务端语音</li></ul>
          </article>
        </div>
        <el-empty v-else description="暂无服务引擎信息" :image-size="72" />
        <div class="info-note"><strong>多模态方式</strong><p>语音由浏览器实时转写，摄像头在浏览器内拍照并压缩；图片交给单独配置的视觉模型。DeepSeek 文字通道不读取图片。</p></div>
      </el-card>

      <el-card shadow="never" class="panel">
        <template #header>
          <div class="panel-heading"><div><span>外部协作</span><h2>WorkBuddy 连接</h2></div><el-tag :type="workBuddy.configured ? 'success' : 'warning'" effect="plain">{{ workBuddy.configured ? '已连接' : '待配置' }}</el-tag></div>
        </template>
        <dl class="connection-details">
          <div><dt>协议版本</dt><dd>{{ workBuddy.protocolVersion || '—' }}</dd></div>
          <div><dt>安全模式</dt><dd>{{ workBuddy.mode || '只读' }}</dd></div>
        </dl>
        <div class="chips"><span v-for="tool in workBuddy.tools" :key="tool">{{ readableTool(tool) }}</span></div>
        <h3>最近调用</h3>
        <div v-if="workBuddyInteractions.length" class="compact-list">
          <article v-for="item in workBuddyInteractions" :key="item.id">
            <span :class="['status-dot', statusTone(item.status)]"></span>
            <div><strong>{{ readableTool(item.toolName) }}</strong><small>{{ formatDate(item.createdAt) }}</small></div>
            <em :class="statusTone(item.status)">{{ statusLabel(item.status) }}</em>
          </article>
        </div>
        <div v-else class="empty-note">还没有 WorkBuddy 调用记录。</div>
      </el-card>

      <el-card shadow="never" class="panel">
        <template #header>
          <div class="panel-heading"><div><span>受控知识</span><h2>IMA 知识库</h2></div><el-tag :type="knowledge.ready ? 'success' : 'warning'" effect="plain">{{ knowledge.ready ? `${knowledge.approvedDocuments} 篇已就绪` : '未就绪' }}</el-tag></div>
        </template>
        <p class="panel-copy">只统计已审批并可检索的资料；文件路径和全文不会在运营页面暴露。</p>
        <div v-if="knowledge.sources.length" class="chips"><span v-for="source in knowledge.sources" :key="source">{{ source }}</span></div>
        <el-empty v-else description="暂无已审批知识资料" :image-size="64" />
        <h3>最近知识检索</h3>
        <div v-if="knowledgeInteractions.length" class="compact-list">
          <article v-for="item in knowledgeInteractions" :key="item.id">
            <span :class="['status-dot', statusTone(item.status)]"></span>
            <div><strong>用户 #{{ item.userId }} 发起知识检索</strong><small>{{ formatDate(item.createdAt) }}</small></div>
            <em :class="statusTone(item.status)">{{ statusLabel(item.status) }}</em>
          </article>
        </div>
        <div v-else class="empty-note">本周期内还没有知识检索记录。</div>
      </el-card>

      <el-card shadow="never" class="panel run-panel">
        <template #header>
          <div class="panel-heading"><div><span>用户办理</span><h2>最近运行记录</h2></div><small>提示词 {{ agentPromptVersion }}</small></div>
        </template>
        <div class="table-scroll">
          <el-table :data="analytics.recentRuns" empty-text="暂无运行记录" table-layout="fixed">
            <el-table-column prop="userId" label="用户" width="78"><template #default="scope">#{{ scope.row.userId }}</template></el-table-column>
            <el-table-column prop="provider" label="服务引擎" min-width="120" show-overflow-tooltip />
            <el-table-column prop="inputModality" label="输入" width="92"><template #default="scope">{{ scope.row.inputModality === 'TEXT' ? '文字' : '图文' }}</template></el-table-column>
            <el-table-column prop="status" label="状态" width="112"><template #default="scope"><el-tag :type="tagType(scope.row.status)" effect="plain" size="small">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="toolCalls" label="工具" width="68" />
            <el-table-column prop="latencyMs" label="耗时" width="92"><template #default="scope">{{ formatLatency(scope.row.latencyMs) }}</template></el-table-column>
            <el-table-column prop="createdAt" label="时间" min-width="150"><template #default="scope">{{ formatDate(scope.row.createdAt) }}</template></el-table-column>
          </el-table>
        </div>
      </el-card>
    </div>
    <footer class="update-note">数据生成于 {{ formatDate(generatedAt) }}；页面在可见时每 60 秒自动更新。</footer>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/utils/axios'
import { formatLatency, normalizeAnalytics } from '@/utils/agent'

const statusLabel = (status) => ({ COMPLETED: '已完成', WAITING_CONFIRMATION: '待确认', DEGRADED: '已降级', FAILED: '失败', SUCCEEDED: '成功', PENDING: '待确认', EXECUTING: '执行中', CANCELLED: '已取消', EXPIRED: '已过期' }[status] || status || '未知')
const statusTone = (status) => ({ SUCCEEDED: 'success', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'muted', EXPIRED: 'muted', PENDING: 'warning', WAITING_CONFIRMATION: 'warning', EXECUTING: 'info', DEGRADED: 'warning' }[status] || 'muted')
const tagType = (status) => ({ COMPLETED: 'success', SUCCEEDED: 'success', FAILED: 'danger', DEGRADED: 'warning', WAITING_CONFIRMATION: 'warning', PENDING: 'warning', EXECUTING: 'primary' }[status] || 'info')
const formatDate = (value) => {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}
const loading = ref(false)
const loadError = ref('')
const days = ref(7)
const generatedAt = ref(null)
const analytics = ref(normalizeAnalytics(null))
const providers = ref([])
const knowledge = ref({ approvedDocuments: 0, ready: false, sources: [] })
const knowledgeInteractions = ref([])
const workBuddy = ref({ configured: false, protocolVersion: '', mode: '', tools: [] })
const workBuddyInteractions = ref([])
const prompts = ref({})
let refreshTimer

const visionConfigured = computed(() => providers.value.some((item) => item.configured && item.vision))
const agentPromptVersion = computed(() => prompts.value?.agent?.version || '—')
const safeArray = (value) => Array.isArray(value) ? value : []
const loadOverview = async ({ silent = false } = {}) => {
  if (!silent) loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get('/chat/admin/overview', { params: { days: days.value }, suppressErrorToast: true })
    generatedAt.value = data?.generatedAt || new Date().toISOString()
    analytics.value = normalizeAnalytics(data?.analytics)
    providers.value = safeArray(data?.providers)
    knowledge.value = { approvedDocuments: Number(data?.knowledge?.approvedDocuments) || 0, ready: Boolean(data?.knowledge?.ready), sources: safeArray(data?.knowledge?.sources) }
    knowledgeInteractions.value = safeArray(data?.knowledgeInteractions)
    workBuddy.value = { configured: Boolean(data?.workBuddy?.configured), protocolVersion: data?.workBuddy?.protocolVersion || '', mode: data?.workBuddy?.mode || '', tools: safeArray(data?.workBuddy?.tools) }
    workBuddyInteractions.value = safeArray(data?.workBuddyInteractions)
    prompts.value = data?.prompts || {}
  } catch (error) {
    loadError.value = error.response?.data?.error || '智能运营数据暂时无法读取'
    if (!silent) ElMessage.error(loadError.value)
  } finally { loading.value = false }
}
const readableTool = (tool) => ({ cecsms_get_platform_overview: '平台概况', cecsms_list_activities: '活动目录', cecsms_list_services: '服务目录', cecsms_list_recipes: '健康菜谱', cecsms_search_knowledge: '知识检索', cecsms_get_agent_metrics: '运行指标' }[String(tool || '').replace(/^mcp:/, '')] || String(tool || '未知工具').replace(/^mcp:/, ''))

onMounted(async () => {
  await loadOverview()
  refreshTimer = window.setInterval(() => { if (document.visibilityState === 'visible') loadOverview({ silent: true }) }, 60_000)
})
onBeforeUnmount(() => window.clearInterval(refreshTimer))
</script>

<style scoped>
.agent-operations { min-width: 0; }
.page-heading { position: relative; isolation: isolate; min-height: 190px; display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; overflow: hidden; margin-bottom: 18px; padding: 30px 32px; color: var(--sp-on-night); background: var(--sp-gradient-aurora); border: 1px solid rgb(255 255 255 / 10%); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-lg); }
.page-heading::before { content: ''; position: absolute; z-index: -1; width: 320px; aspect-ratio: 1; top: -210px; right: -70px; border: 1px solid rgb(255 255 255 / 13%); border-radius: 50%; box-shadow: 0 0 0 46px rgb(255 255 255 / 3%), 0 0 0 92px rgb(255 255 255 / 2%); }
.page-heading > div:first-child { min-width: 0; }.page-heading span { color: var(--sp-energy-lime); font-size: 12px; font-weight: 780; }.page-heading h1 { margin: 6px 0 7px; color: var(--sp-on-night); font-size: clamp(32px, 4vw, 48px); line-height: 1.05; letter-spacing: -.045em; }.page-heading p { max-width: 780px; margin: 0; color: var(--sp-on-night-muted); font-size: 13px; line-height: 1.65; }
.heading-actions { flex: 0 0 auto; display: flex; gap: 8px; }.heading-actions .el-select { width: 120px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 16px 0; }.metric-grid article { position: relative; min-width: 0; display: grid; gap: 4px; overflow: hidden; padding: 18px; background: var(--sp-surface-glass); border: 1px solid rgb(255 255 255 / 72%); border-radius: var(--sp-radius-md); box-shadow: var(--sp-shadow-md); backdrop-filter: blur(18px); }.metric-grid article::after { content: ''; position: absolute; width: 80px; height: 80px; right: -30px; bottom: -40px; opacity: .13; background: var(--sp-energy-violet); filter: blur(10px); border-radius: 50%; }.metric-grid span { color: var(--sp-text-muted); font-size: 11px; }.metric-grid strong { font-size: clamp(24px, 3vw, 34px); }.metric-grid small { overflow: hidden; color: var(--sp-text-secondary); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.operations-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }.panel { min-width: 0; background: var(--sp-surface-glass); border-color: rgb(255 255 255 / 72%); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-md); backdrop-filter: blur(18px); }.panel-heading { min-height: 40px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }.panel-heading span { color: var(--sp-text-muted); font-size: 10px; }.panel-heading h2 { margin: 1px 0 0; font-size: 17px; }.panel-heading > small { color: var(--sp-text-muted); }.panel h3 { margin: 16px 0 9px; font-size: 13px; }.panel-copy { margin: 0 0 12px; color: var(--sp-text-secondary); font-size: 12px; line-height: 1.65; }
.provider-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }.provider-list article { min-width: 0; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 7px; padding: 11px; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-sm); }.provider-list article > div { min-width: 0; display: grid; }.provider-list strong, .compact-list strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.provider-list small, .compact-list small { color: var(--sp-text-muted); font-size: 9px; }.provider-list ul { grid-column: 1 / -1; display: flex; flex-wrap: wrap; gap: 5px; list-style: none; }.provider-list li, .chips span { padding: 4px 7px; color: var(--sp-text-muted); font-size: 9px; background: var(--sp-surface); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-pill); }.provider-list li { opacity: .6; }.provider-list li.enabled { color: var(--sp-success); opacity: 1; }
.info-note { margin-top: 11px; padding: 10px 11px; background: var(--sp-info-soft); border-left: 3px solid var(--sp-info); }.info-note strong { font-size: 11px; }.info-note p { margin: 3px 0 0; color: var(--sp-text-secondary); font-size: 10px; line-height: 1.6; }
.connection-details { display: grid; gap: 7px; margin: 0 0 11px; }.connection-details div { display: grid; grid-template-columns: 78px minmax(0, 1fr); gap: 8px; padding: 7px 8px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-xs); }.connection-details dt { color: var(--sp-text-muted); font-size: 10px; }.connection-details dd { margin: 0; font-size: 10px; font-weight: 650; }.chips { display: flex; flex-wrap: wrap; gap: 6px; }.chips span { background: var(--sp-surface-muted); }
.compact-list { display: grid; gap: 8px; }.compact-list article { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; align-items: center; gap: 8px; }.compact-list article > div { min-width: 0; display: grid; }.compact-list em { font-size: 9px; font-style: normal; }.status-dot { width: 7px; height: 7px; background: var(--sp-text-muted); border-radius: 50%; }.status-dot.success { background: var(--sp-success); }.status-dot.warning { background: var(--sp-warning); }.status-dot.danger { background: var(--sp-danger); }.compact-list em.success { color: var(--sp-success); }.compact-list em.warning { color: var(--sp-warning); }.compact-list em.danger { color: var(--sp-danger); }.compact-list em.muted { color: var(--sp-text-muted); }.empty-note { padding: 10px; color: var(--sp-text-muted); font-size: 11px; background: var(--sp-surface-muted); border-radius: var(--sp-radius-xs); }
.run-panel { grid-column: 1 / -1; }.table-scroll { overflow-x: auto; }.table-scroll .el-table { min-width: 720px; }.update-note { padding: 13px 2px 2px; color: var(--sp-text-muted); font-size: 10px; text-align: right; }
/* Operational metadata must remain legible on dense administrator screens. */
.page-heading p { font-size: 14px; }
.metric-grid span,
.panel-copy,
.provider-list strong,
.compact-list strong,
.info-note strong { font-size: 13px; }
.metric-grid small,
.panel-heading span,
.provider-list small,
.compact-list small,
.provider-list li,
.chips span,
.info-note p,
.connection-details dt,
.connection-details dd,
.compact-list em,
.empty-note,
.update-note { font-size: 12px; }
.panel-heading h2 { font-size: 18px; }
.panel h3 { font-size: 14px; }
@media (max-width: 1100px) { .metric-grid { grid-template-columns: 1fr 1fr; }.operations-grid { grid-template-columns: 1fr; }.run-panel { grid-column: auto; } }
@media (max-width: 700px) { .page-heading { align-items: stretch; flex-direction: column; padding: 16px; }.heading-actions { width: 100%; }.heading-actions .el-select { flex: 1; width: auto; }.provider-list { grid-template-columns: 1fr; }.panel :deep(.el-card__body) { padding: 14px; } }
@media (max-width: 440px) { .metric-grid { grid-template-columns: 1fr; }.heading-actions { flex-direction: column; }.heading-actions .el-button { width: 100%; } }
</style>
