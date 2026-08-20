<template>
  <div class="operations-dashboard">
    <header class="dashboard-heading">
      <div>
        <span>运营数据总览</span>
        <h1>智慧养老运营中心</h1>
        <p>人员、活动、服务与智能助理数据均来自当前业务系统。</p>
      </div>
      <button type="button" :disabled="loading" @click="loadDashboard">
        {{ loading ? '同步中…' : '刷新数据' }}
      </button>
    </header>

    <el-alert v-if="loadError" :title="loadError" type="warning" :closable="false" show-icon />

    <section class="agent-health" aria-labelledby="agent-health-title">
      <div class="agent-identity"><img :src="companionPortrait" alt="" /><div><small>小伴生活助理</small><h2 id="agent-health-title">智能服务运行状态</h2></div></div>
      <div class="health-signal"><i :class="{ ready: agentStatus.configured }"></i>{{ agentStatus.configured ? '模型通道可用' : '模型通道待配置' }}</div>
      <dl>
        <div><dt>业务工具</dt><dd>{{ agentStatus.capabilityCount }}</dd></div>
        <div><dt>审批知识</dt><dd>{{ agentStatus.knowledgeCount }}</dd></div>
        <div><dt>近期开办</dt><dd>{{ agentAnalytics.totalRuns }}</dd></div>
        <div><dt>成功率</dt><dd>{{ agentAnalytics.successRate.toFixed(1) }}%</dd></div>
      </dl>
      <el-button type="primary" plain @click="toWhere('/AgentOperationsView')">查看智能运营</el-button>
    </section>

    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else>
      <section class="stat-grid" aria-label="人员概览">
        <button v-for="item in peopleStats" :key="item.route" type="button" class="stat-card" @click="toWhere(item.route)">
          <div><small>{{ item.eyebrow }}</small><strong>{{ item.value }}</strong><span>{{ item.label }}</span></div>
          <el-icon :size="34"><component :is="item.icon" /></el-icon>
        </button>
      </section>

      <section class="chart-grid" aria-label="运营图表">
        <article><CountRate /></article>
        <article><CountSignedup /></article>
        <article><ActivitySort /></article>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../../../utils/axios'
import { UserFilled, FirstAidKit, Avatar } from '@element-plus/icons-vue'
import CountRate from './CountRate.vue'
import CountSignedup from './CountSignedup.vue'
import ActivitySort from './ActivitySort.vue'
import companionPortrait from '@/assets/assistant/xiaoban-care-coordinator.webp'

const router = useRouter()
const loading = ref(true)
const loadError = ref('')
const counts = ref({ users: 0, doctors: 0, workers: 0 })
const agentStatus = ref({ configured: false, capabilityCount: 0, knowledgeCount: 0 })
const agentAnalytics = ref({ totalRuns: 0, successRate: 0 })

const peopleStats = computed(() => [
  { eyebrow: '服务对象', label: '普通用户', value: counts.value.users, route: '/UserMenageView', icon: UserFilled },
  { eyebrow: '专业照护', label: '医护人员', value: counts.value.doctors, route: '/DoctorMenageView', icon: FirstAidKit },
  { eyebrow: '社区运营', label: '社区工作者', value: counts.value.workers, route: '/WorkerMenageView', icon: Avatar }
])

const toWhere = (route) => router.push(route)
const getSum = async (roleId) => {
  const { data } = await api.get(`/count/getSum/${roleId}`)
  if (data?.code !== 200) throw new Error(data?.msg || '人员统计读取失败')
  return Math.max(0, Number(data.result) || 0)
}

const loadDashboard = async () => {
  loading.value = true
  loadError.value = ''
  const results = await Promise.allSettled([
    getSum(4), getSum(3), getSum(2), api.get('/chat/admin/overview', { params: { days: 7 }, suppressErrorToast: true })
  ])
  const [users, doctors, workers, operations] = results
  if (users.status === 'fulfilled') counts.value.users = users.value
  if (doctors.status === 'fulfilled') counts.value.doctors = doctors.value
  if (workers.status === 'fulfilled') counts.value.workers = workers.value
  if (operations.status === 'fulfilled') {
    const data = operations.value.data
    agentStatus.value = {
      configured: Array.isArray(data?.providers) && data.providers.some((item) => item.configured),
      capabilityCount: Math.max(0, Number(data?.businessToolCount) || 0),
      knowledgeCount: Math.max(0, Number(data?.knowledge?.approvedDocuments) || 0)
    }
    agentAnalytics.value = {
      totalRuns: Math.max(0, Number(data?.analytics?.totalRuns) || 0),
      successRate: Math.max(0, Number(data?.analytics?.successRate) || 0)
    }
  }
  if (results.some((result) => result.status === 'rejected')) loadError.value = '部分指标暂时不可用，已保留成功加载的数据。'
  loading.value = false
}

onMounted(loadDashboard)
</script>

<style scoped>
.operations-dashboard { min-height: 100%; display: grid; align-content: start; gap: 20px; padding: 6px; color: var(--sp-text); }
.dashboard-heading { position: relative; isolation: isolate; min-height: 205px; display: flex; align-items: flex-end; justify-content: space-between; gap: 22px; overflow: hidden; padding: 32px 34px; color: var(--sp-on-night); background: var(--sp-gradient-aurora); border: 1px solid rgb(255 255 255 / 10%); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-lg); }
.dashboard-heading::before { content: ''; position: absolute; z-index: -1; width: 340px; aspect-ratio: 1; top: -220px; right: -60px; border: 1px solid rgb(255 255 255 / 13%); border-radius: 50%; box-shadow: 0 0 0 48px rgb(255 255 255 / 3%), 0 0 0 96px rgb(255 255 255 / 2%); }
.dashboard-heading span { color: var(--sp-energy-lime); font-size: 12px; font-weight: 780; letter-spacing: .06em; }
.dashboard-heading h1 { margin: 9px 0 6px; color: var(--sp-on-night); font-size: clamp(34px, 4vw, 52px); line-height: 1.05; letter-spacing: -.05em; }
.dashboard-heading p { margin: 0; color: var(--sp-on-night-muted); font-size: 13px; }
.dashboard-heading button { min-height: 42px; padding: 0 16px; color: var(--sp-on-night); font-weight: 700; background: rgb(255 255 255 / 8%); border: 1px solid rgb(255 255 255 / 15%); border-radius: var(--sp-radius-pill); cursor: pointer; backdrop-filter: blur(10px); transition: transform var(--sp-duration-fast) ease, background-color var(--sp-duration-fast) ease; }
.dashboard-heading button:hover:not(:disabled) { background: rgb(255 255 255 / 14%); transform: translateY(-2px); }
.agent-health { display: grid; grid-template-columns: minmax(220px, 1fr) auto minmax(360px, 1.2fr) auto; align-items: center; gap: 18px; padding: 19px 22px; color: var(--sp-text); background: var(--sp-surface-glass); border: 1px solid rgb(255 255 255 / 72%); border-left: 4px solid var(--sp-energy-violet); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-md); backdrop-filter: blur(20px); }
.agent-identity { display: flex; align-items: center; gap: 12px; }.agent-identity > img { width: 48px; height: 48px; object-fit: cover; object-position: center 12%; background: #edf3ef; border: 1px solid var(--sp-border); border-radius: 50%; }
.agent-identity small { color: var(--sp-brand-strong); font-size: 10px; font-weight: 750; }.agent-identity h2 { margin: 3px 0 0; font-size: 17px; }
.health-signal { display: flex; align-items: center; gap: 7px; padding: 7px 10px; color: var(--sp-warning); font-size: 10px; background: var(--sp-warning-soft); border: 1px solid color-mix(in srgb, var(--sp-warning) 24%, var(--sp-border)); border-radius: var(--sp-radius-pill); }
.health-signal i { width: 7px; height: 7px; background: var(--sp-warning); border-radius: 50%; }.health-signal i.ready { background: var(--sp-success); }
.agent-health dl { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin: 0; }.agent-health dl div { padding: 9px; text-align: center; background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-xs); }.agent-health dt { color: var(--sp-text-muted); font-size: 9px; }.agent-health dd { margin: 4px 0 0; font-size: 17px; font-weight: 800; font-variant-numeric: tabular-nums; }
.stat-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 15px; }
.stat-card { position: relative; min-height: 154px; display: flex; align-items: center; justify-content: space-between; overflow: hidden; padding: 22px; color: var(--sp-text); text-align: left; background: var(--sp-surface-glass); border: 1px solid rgb(255 255 255 / 72%); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-md); cursor: pointer; backdrop-filter: blur(18px); transition: transform var(--sp-duration-base) var(--sp-ease-expressive), box-shadow var(--sp-duration-base) ease, border-color var(--sp-duration-base) ease; }
.stat-card::before { content: ''; position: absolute; width: 130px; aspect-ratio: 1; right: -48px; bottom: -68px; opacity: .13; background: var(--sp-energy-violet); filter: blur(14px); border-radius: 50%; }
.stat-card:nth-child(2)::before { background: var(--sp-energy-coral); }.stat-card:nth-child(3)::before { background: var(--sp-brand); }
.stat-card:hover { transform: translateY(-6px); border-color: color-mix(in srgb, var(--sp-energy-violet) 30%, var(--sp-border)); box-shadow: var(--sp-shadow-lg); }.stat-card div { position: relative; display: grid; }.stat-card small { color: var(--sp-text-muted); font-size: 10px; font-weight: 700; }.stat-card strong { margin: 10px 0 2px; color: var(--sp-text); font-size: 40px; line-height: 1; }.stat-card span { color: var(--sp-text-secondary); font-size: 13px; }.stat-card .el-icon { position: relative; color: var(--sp-on-brand); padding: 14px; background: var(--sp-gradient-action); border-radius: 16px; box-shadow: 0 12px 26px rgb(23 107 82 / 18%); }
.chart-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 15px; }.chart-grid article { min-width: 0; min-height: 340px; overflow: hidden; background: var(--sp-surface-glass); border: 1px solid rgb(255 255 255 / 72%); border-radius: var(--sp-radius-lg); box-shadow: var(--sp-shadow-md); backdrop-filter: blur(18px); }
@media (max-width: 1180px) { .agent-health { grid-template-columns: 1fr auto; }.agent-health dl, .agent-health > .el-button { grid-column: 1 / -1; }.agent-health > .el-button { justify-self: start; margin-left: 0; }.chart-grid { grid-template-columns: 1fr 1fr; }.chart-grid article:last-child { grid-column: 1 / -1; } }
@media (max-width: 760px) { .dashboard-heading { align-items: flex-start; flex-direction: column; }.dashboard-heading button { width: 100%; }.agent-health { grid-template-columns: 1fr; }.health-signal { justify-self: start; }.agent-health dl { grid-template-columns: 1fr 1fr; }.agent-health > .el-button { width: 100%; }.stat-grid, .chart-grid { grid-template-columns: 1fr; }.chart-grid article:last-child { grid-column: auto; } }
</style>
