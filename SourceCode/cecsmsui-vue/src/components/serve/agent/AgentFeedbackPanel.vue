<template>
  <el-card shadow="never" class="workflow-panel">
    <template #header><div class="workflow-heading"><div><h2>问题处理与办理结果</h2><p>记录问题、修复和人工复测，保留每次状态变化。</p></div><el-button :loading="exporting" @click="exportReport">导出匿名运营报告</el-button></div></template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="outcome-grid">
      <article><span>需确认动作总量</span><strong>{{ outcomes.total || 0 }}</strong><small>按动作创建时间统计</small></article>
      <article><span>办理成功</span><strong>{{ outcomes.statuses?.SUCCEEDED || 0 }}</strong><small>后端已完成执行</small></article>
      <article><span>取消 / 到期</span><strong>{{ outcomes.statuses?.CANCELLED || 0 }} / {{ outcomes.statuses?.EXPIRED || 0 }}</strong><small>与系统失败分别统计</small></article>
      <article><span>问题验证关闭率</span><strong>{{ closureRate }}</strong><small>{{ summary.verified || 0 }} / {{ summary.total || 0 }} 条本期创建的问题</small></article>
    </div>
    <p class="workflow-note">待确认不等于办理成功。运行、动作和问题采用各自的创建时间统计，不能直接拼接成转化漏斗。新问题的观察期可能尚未结束。</p>
    <p class="workflow-note">当前待确认 {{ outcomes.statuses?.PENDING || 0 }}，执行中 {{ outcomes.statuses?.EXECUTING || 0 }}，执行失败 {{ outcomes.statuses?.FAILED || 0 }}。</p>
    <el-form :inline="true" class="feedback-create" @submit.prevent="createFeedback">
      <el-form-item label="关联运行"><el-select v-model="draft.runId" filterable allow-create default-first-option placeholder="选择最近运行或输入运行编号" aria-label="关联运行"><el-option v-for="run in recentRuns" :key="run.runId" :label="run.provider + ' · ' + run.runId" :value="run.runId" /></el-select></el-form-item>
      <el-form-item label="问题类别"><el-select v-model="draft.category" aria-label="问题类别"><el-option v-for="(label, key) in categories" :key="key" :label="label" :value="key" /></el-select></el-form-item>
      <el-form-item label="优先级"><el-select v-model="draft.priority" aria-label="优先级"><el-option v-for="priority in ['P1', 'P2', 'P3']" :key="priority" :label="priority" :value="priority" /></el-select></el-form-item>
      <el-form-item><el-button native-type="submit" type="primary" :loading="saving" :disabled="!draft.runId">登记问题</el-button></el-form-item>
    </el-form>
    <p class="workflow-note">只登记分类和编号；请勿填写原始对话、个人健康信息或密钥。P1 为阻断办理，P2 为主要体验问题，P3 为一般优化。</p>
    <el-alert v-if="truncated" title="仅展示最近 100 条问题；汇总指标覆盖所选周期全部问题。" type="warning" :closable="false" />
    <div class="workflow-table"><el-table :data="items" empty-text="暂无问题记录" table-layout="fixed">
      <el-table-column prop="runId" label="关联运行" min-width="150" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="登记时间" min-width="160" show-overflow-tooltip />
      <el-table-column label="类别 / 优先级" min-width="155"><template #default="{ row }">{{ categories[row.category] || row.category }} · {{ row.priority }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }">{{ states[row.status] || row.status }}</template></el-table-column>
      <el-table-column label="负责人" width="90"><template #default="{ row }">#{{ row.ownerId }}</template></el-table-column>
      <el-table-column prop="changeRef" label="修复引用" min-width="150" show-overflow-tooltip />
      <el-table-column prop="evidenceRef" label="复测记录" min-width="145" show-overflow-tooltip />
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="openTransition(row)">处理</el-button></template></el-table-column>
    </el-table></div>
    <el-dialog v-model="dialogOpen" title="处理运营问题" width="min(540px, 94vw)" :close-on-click-modal="false">
      <p class="workflow-note">关联运行：{{ selected?.runId }}<br>问题编号：{{ selected?.id }}</p>
      <el-form label-position="top" @submit.prevent="saveTransition">
        <el-form-item label="下一步"><el-select v-model="transition.status" aria-label="下一步"><el-option v-for="status in nextStates" :key="status" :label="states[status]" :value="status" /></el-select></el-form-item>
        <el-form-item v-if="['FIXED', 'VERIFIED'].includes(transition.status)" label="修复引用"><el-input v-model="transition.changeRef" maxlength="80" placeholder="commit:提交哈希 或 issue:编号" /></el-form-item>
        <template v-if="transition.status === 'VERIFIED'">
          <el-form-item label="已保存的复测记录编号"><el-input v-model="transition.evidenceRef" maxlength="85" placeholder="eval:case-001" /></el-form-item>
          <el-checkbox v-model="transition.verificationPassed">我已检查复测记录，确认问题修复且复测通过</el-checkbox>
          <p class="workflow-note">这里保存人工复测结论及记录编号，不会自动验证外部报告内容。</p>
        </template>
        <p v-if="transition.status === 'TRIAGED'" class="workflow-note">进入分诊后由当前管理员负责。重新打开会清除当前修复及复测结论，历史变化仍保留。</p>
        <el-button native-type="submit" type="primary" :loading="saving" :disabled="transition.status === 'VERIFIED' && !transition.verificationPassed">保存处理结果</el-button>
      </el-form>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/utils/axios'
import { saveBlobResponse } from '@/utils/download'

const props = defineProps({ days: { type: Number, default: 7 }, recentRuns: { type: Array, default: () => [] }, refreshKey: { type: [String, Number], default: '' } })
const categories = { KNOWLEDGE_GAP: '知识缺失', CONFUSING_RESPONSE: '表达不清', TOOL_ERROR: '工具错误', CONFIRMATION_UX: '确认体验', MODEL_PLANNING: '规划问题' }
const states = { OPEN: '待分诊', TRIAGED: '处理中', FIXED: '待复测', VERIFIED: '已验证关闭' }
const transitions = { OPEN: ['TRIAGED'], TRIAGED: ['FIXED'], FIXED: ['VERIFIED', 'TRIAGED'], VERIFIED: ['TRIAGED'] }
const items = ref([])
const outcomes = ref({})
const summary = ref({})
const truncated = ref(false)
const error = ref('')
const saving = ref(false)
const exporting = ref(false)
const dialogOpen = ref(false)
const selected = ref(null)
const draft = ref({ runId: '', category: 'CONFUSING_RESPONSE', priority: 'P2' })
const transition = ref({ status: '', changeRef: '', evidenceRef: '', verificationPassed: false })
const nextStates = computed(() => transitions[selected.value?.status] || [])
const closureRate = computed(() => summary.value.verifiedClosureRate == null ? '暂无样本' : summary.value.verifiedClosureRate + '%')
let requestSequence = 0
const load = async () => {
  const sequence = ++requestSequence
  try {
    const { data } = await api.get('/chat/admin/operations', { params: { days: props.days }, suppressErrorToast: true })
    if (sequence !== requestSequence) return
    items.value = Array.isArray(data?.feedback?.items) ? data.feedback.items : []
    truncated.value = Boolean(data?.feedback?.truncated)
    summary.value = data?.feedbackSummary || {}
    outcomes.value = data?.actionOutcomes || {}
    error.value = ''
  } catch (failure) {
    if (sequence === requestSequence) error.value = failure.response?.data?.error || '运营问题暂时无法读取'
  }
}
const createFeedback = async () => {
  saving.value = true
  try {
    await api.post('/chat/admin/operations/feedback', draft.value, { suppressErrorToast: true })
    draft.value.runId = ''
    ElMessage.success('问题已登记')
    await load()
  } catch (failure) { ElMessage.error(failure.response?.data?.error || '问题登记失败') }
  finally { saving.value = false }
}
const openTransition = (row) => {
  selected.value = row
  transition.value = { status: transitions[row.status]?.[0] || '', changeRef: row.changeRef || '', evidenceRef: '', verificationPassed: false }
  dialogOpen.value = true
}
const saveTransition = async () => {
  saving.value = true
  try {
    await api.post('/chat/admin/operations/feedback/' + selected.value.id + '/transition', { ...transition.value, version: selected.value.version }, { suppressErrorToast: true })
    dialogOpen.value = false
    ElMessage.success('处理结果已保存')
    await load()
  } catch (failure) {
    ElMessage.error(failure.response?.data?.error || '保存失败，请重试')
    if (failure.response?.status === 409) { dialogOpen.value = false; await load() }
  } finally { saving.value = false }
}
const exportReport = async () => {
  exporting.value = true
  try {
    const { data } = await api.get('/chat/admin/operations/export', { params: { days: props.days }, suppressErrorToast: true })
    if (data?.schemaVersion !== 'silverpilot-operations-v1') throw new Error('报告格式不正确')
    await saveBlobResponse({ status: 200, data: JSON.stringify(data, null, 2), headers: { 'content-type': 'application/octet-stream' } }, 'silverpilot-operations-' + props.days + 'days.json')
  } catch (failure) { ElMessage.error(failure.response?.data?.error || failure.message || '报告导出失败') }
  finally { exporting.value = false }
}
watch(() => [props.days, props.refreshKey], load, { immediate: true })
</script>

<style scoped>
.workflow-panel { margin-top: 16px; border-radius: var(--sp-radius-lg); }
.workflow-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.workflow-heading h2 { margin: 0; font-size: 18px; }.workflow-heading p, .workflow-note { font-size: 12px; line-height: 1.7; color: var(--sp-text-secondary); }
.outcome-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 16px 0; }
.outcome-grid article { display: grid; gap: 6px; padding: 12px; background: var(--sp-surface-muted); border-radius: 8px; }
.outcome-grid strong { font-size: 24px; }.outcome-grid small, .outcome-grid span { font-size: 12px; }
.feedback-create { margin-top: 18px; }.feedback-create .el-select { width: 180px; }.feedback-create .el-form-item:first-child .el-select { width: 260px; }
.workflow-table { overflow-x: auto; }.workflow-table .el-table { min-width: 760px; }
@media (max-width: 900px) { .outcome-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.workflow-heading { align-items: flex-start; flex-direction: column; } }
@media (max-width: 500px) { .outcome-grid { grid-template-columns: 1fr; }.feedback-create .el-form-item { display: block; margin-right: 0; }.feedback-create .el-select, .feedback-create .el-form-item:first-child .el-select { width: 100%; } }
</style>
